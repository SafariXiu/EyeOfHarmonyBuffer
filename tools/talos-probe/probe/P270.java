package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P270：**归因拆解** —— 沿岸风到底是谁给的？（cell 项 vs 热力项），以及为什么会饱和。 */
public class P270 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p270_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        int GRAD = 15_000;
        say(String.format(LF, "P270：沿岸风归因拆解（gradStep=%d km）", GRAD / 1000));
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        int z = (int) (0.25 * (ZC / 2));
        GyreRow.Params p = new GyreRow.Params();
        p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
        GyreRow.Row row = GyreRow.solve(5_500_000, z, SD, cell, wind, p);
        say(String.format(LF, "  纬线 z=%d（%.1f 度）盆宽 %.0f km，东岸 x=%d", z,
            Math.toDegrees(WorldContract.latOf(z, ZC)), (row.eastX - row.westX) / 1000.0, row.eastX));
        say("");
        for (double g : new double[]{0.0, 2.8, 4.0, 8.0}) {
            Atmosphere.CELL_GAIN = g;
            say(String.format(LF, "===== CELL_GAIN = %.1f =====", g));
            say(String.format(LF, "   %-9s %7s %11s %11s %11s %10s %9s %9s",
                "离岸km", "kappa", "gradP总", "gradP_热力", "gradP_cell", "|U| m/s", "tau_x", "tau_z"));
            for (int k = 1; k <= 4; k++) {
                int x = row.eastX - k * 120_000;
                double lat = WorldContract.latOf(z, ZC);
                double kx = Atmosphere.kappaAt(x, z, SD, cell);
                double gTot = gradP(x, z, cell, th, GRAD, true);
                double gCell = gradP(x, z, cell, th, GRAD, false);
                double[] uv = Atmosphere.windAt(x, z, SD, cell, th, GRAD);
                double[] ts = Atmosphere.windStress(x, z, SD, cell, th, GRAD);
                say(String.format(LF, "   %-9d %7.3f %11.3e %11.3e %11.3e %10.2f %9.5f %9.5f",
                    k * 120, kx, gTot, gTot - gCell, gCell, Math.hypot(uv[0], uv[1]), ts[0], ts[1]));
            }
            say("");
        }
        Atmosphere.CELL_GAIN = 4.0;
        say("说明：");
        say("  gradP 是**经向**（z 方向）分量的绝对值，因为沿岸风主要由 p_x 经地转给出；");
        say("  这里给出的是 |dp/dz| 作为对照 —— 真正的沿岸驱动是 p_x，见下一节。");
        say("");
        say("===== 关键：跨岸（x 方向）气压梯度 =====");
        say(String.format(LF, "   %-12s %9s %13s %13s %13s", "CELL_GAIN", "离岸km", "dp/dx_总", "dp/dx_热力", "dp/dx_cell"));
        for (double g : new double[]{0.0, 2.8, 4.0, 8.0}) {
            Atmosphere.CELL_GAIN = g;
            for (int k = 1; k <= 3; k++) {
                int x = row.eastX - k * 120_000;
                double gxTot = gradX(x, z, cell, th, GRAD, true);
                double gxCell = gradX(x, z, cell, th, GRAD, false);
                say(String.format(LF, "   %-12.1f %9d %13.3e %13.3e %13.3e", g, k * 120, gxTot, gxTot - gxCell, gxCell));
            }
        }
        Atmosphere.CELL_GAIN = 4.0;
        rep.close();
    }

    static double gradP(int x, int z, int cell, double th, int g, boolean total) {
        return (pv(x, z + g, cell, th, total) - pv(x, z - g, cell, th, total)) / (2.0 * g);
    }

    static double gradX(int x, int z, int cell, double th, int g, boolean total) {
        return (pv(x + g, z, cell, th, total) - pv(x - g, z, cell, th, total)) / (2.0 * g);
    }

    static double pv(int x, int z, int cell, double th, boolean total) {
        double p = Atmosphere.pressureAnomaly(x, z, SD, cell, th);
        if (total) return p;
        return p - Atmosphere.cellPressure(WorldContract.latOf(z, ZC), Atmosphere.kappaAt(x, z, SD, cell), th);
    }

    static void say(String s) { System.out.println("[P270] " + s); rep.println("[P270] " + s); }
}
