package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/** P272：**按观测锚点标定 CELL_GAIN**，并在真海岸上给东带。 */
public class P272 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p272_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        int GRAD = 15_000, NM = 64;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say("P272：按观测锚点标定 CELL_GAIN（近岸 tau_z 目标中位 0.09 Pa）");
        say(String.format(LF, "  TAU0=%.4f PLATE_CELL=%d Z_CYCLE=%d  NM=%d", TAU0, cell, ZC, NM));
        say("");
        // 先缓存每行的解与「真海岸」判定
        GyreRow.Row[] rows = new GyreRow.Row[NM];
        int[] zs = new int[NM];
        boolean[] real = new boolean[NM];
        for (int r = 0; r < NM; r++) {
            zs[r] = (int) ((r + 0.5) / NM * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
            rows[r] = GyreRow.solve((r * 311_000) % 9_000_000, zs[r], SD, cell, wind, p);
            real[r] = rows[r].valid && rows[r].eastX < p.maxRow - 20_000 && rows[r].westX > -p.maxRow + 20_000
                   && (rows[r].eastX - rows[r].westX) > 2 * Math.PI * p.deltaAt(zs[r]);
        }
        int nReal = 0; for (boolean b : real) if (b) nReal++;
        say(String.format(LF, "  真海岸行 %d/%d", nReal, NM));
        say("");
        say(String.format(LF, "  %-8s %14s %14s %14s %14s %14s", "CELL_GAIN", "近岸|tau_z|中位", "h_c 极值m",
            "东带@13.5", "东带@22.5", "东带@36"));
        double best = 1.4; double bestErr = 1e9;
        for (double g : new double[]{1.4, 2.1, 2.8, 3.5}) {
            Atmosphere.CELL_GAIN = g;
            double[] tau = new double[NM];
            double[] near = new double[NM * 3];
            int nc = 0;
            for (int r = 0; r < NM; r++) {
                if (!real[r]) { tau[r] = 0; continue; }
                double s = 0; int c = 0;
                for (int k = 1; k <= 4; k++) {
                    double[] a = Atmosphere.windStress(rows[r].eastX - k * 60_000, zs[r], SD, cell, th, GRAD);
                    s += a[1]; c++;
                    if (k <= 3) near[nc++] = Math.abs(a[1]);
                }
                tau[r] = s / c;
            }
            double[] n2 = Arrays.copyOf(near, nc);
            Arrays.sort(n2);
            double med = nc > 0 ? n2[nc / 2] : 0;
            double[] hc = CoastalLayer.steadySmoothed(tau, (double) ZC / NM);
            double e1 = east(hc, 0.15, wind), e2 = east(hc, 0.25, wind), e3 = east(hc, 0.40, wind);
            say(String.format(LF, "  %-8.1f %14.5f %14.1f %14.2f %14.2f %14.2f", g, med, ext(hc), e1, e2, e3));
            double err = Math.abs(med - 0.09);
            if (err < bestErr) { bestErr = err; best = g; }
        }
        Atmosphere.CELL_GAIN = best;
        say("");
        say(String.format(LF, "  ⇒ 最接近观测中位 0.09 Pa 的是 CELL_GAIN = %.1f（已采用）", best));
        say(String.format(LF, "  参考：观测的沿岸风应力 0.05~0.15 Pa；跨岸梯度 10 hPa/1600km = 6.25e-4 Pa/m"));
        rep.close();
    }

    static double east(double[] hc, double bandD, GyreRow.BandedWind wind) {
        int z = (int) (bandD * (ZC / 2));
        double lat = WorldContract.latOf(z, ZC);
        double f = WorldContract.coriolis(lat);
        double hcv = hc[Math.min(hc.length - 1, (int) ((double) z / ZC * hc.length))];
        double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
        double sv = wind.at(0, z) / (1025.0 * H_T * WorldContract.betaForLatitude(lat, ZC)) * 1000;
        return cb + sv;
    }

    static double ext(double[] v) { double m = 0; for (double x : v) if (Math.abs(x) > Math.abs(m)) m = x; return m; }

    static void say(String s) { System.out.println("[P272] " + s); rep.println("[P272] " + s); }
}
