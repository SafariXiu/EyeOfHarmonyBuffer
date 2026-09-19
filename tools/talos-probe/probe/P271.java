package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P271：**在真海岸上重建沿岸测量**（先验特征，再测量 —— §44.4 的教训）。 */
public class P271 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p271_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        int GRAD = 15_000;
        say("P271：真海岸上的沿岸风（CELL_GAIN=%.2f，maxRow=%d）", Atmosphere.CELL_GAIN, 12000000);
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say("");
        int found = 0;
        for (int k = 0; k < 24 && found < 5; k++) {
            int z = (int) ((k + 0.5) / 24.0 * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            GyreRow.Row r = GyreRow.solve((k * 977_000) % 9_000_000, z, SD, cell, wind, p);
            if (!r.valid) continue;
            // **关键断言：东端必须是真海岸，不是 maxRow 截断**
            if (r.eastX >= p.maxRow - 20_000) continue;
            if (r.westX <= -p.maxRow + 20_000) continue;
            double gate = Math.PI * p.deltaAt(z);
            if ((r.eastX - r.westX) < 2 * gate) continue;
            found++;
            say(String.format(LF, "===== 第 %d 个真海岸：z=%d（%.1f 度）盆宽 %.0f km  westX=%d eastX=%d =====",
                found, z, Math.toDegrees(WorldContract.latOf(z, ZC)), (r.eastX - r.westX) / 1000.0, r.westX, r.eastX));
            say(String.format(LF, "   %-9s %8s %11s %11s %11s", "离岸km", "kappa", "dp/dx", "|U| m/s", "tau_z Pa"));
            double prev = Double.NaN;
            int mono = 0, n = 0;
            for (int j = 1; j <= 8; j++) {
                int x = r.eastX - j * 50_000;
                double kx = Atmosphere.kappaAt(x, z, SD, cell);
                double gx = (Atmosphere.pressureAnomaly(x + GRAD, z, SD, cell, th)
                           - Atmosphere.pressureAnomaly(x - GRAD, z, SD, cell, th)) / (2.0 * GRAD);
                double[] uv = Atmosphere.windAt(x, z, SD, cell, th, GRAD);
                double[] ts = Atmosphere.windStress(x, z, SD, cell, th, GRAD);
                if (!Double.isNaN(prev)) { n++; if (Math.abs(kx) > Math.abs(prev)) mono++; }
                prev = kx;
                say(String.format(LF, "   %-9d %8.3f %11.3e %11.2f %11.5f",
                    j * 50, kx, gx, Math.hypot(uv[0], uv[1]), ts[1]));
            }
            say(String.format(LF, "   ⇒ |kappa| 随离岸单调上升的比例 %d/%d（应当接近满值）", mono, n));
            say("");
        }
        if (found == 0) say("   ⚠ 24 条行里没有一条两端都是真海岸 —— 需要放宽取样。");
        rep.close();
    }

    static void say(String fmt, Object... a) {
        String s = String.format(LF, fmt, a);
        System.out.println("[P271] " + s); rep.println("[P271] " + s);
    }
}
