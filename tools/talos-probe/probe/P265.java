package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P265：**海温场（西暖东冷）-> 沿岸风 -> 沿岸层** 的闭环测量。
 *
 * <p>做法：网格化「表层经向速度 -> 海温异常」，装上 Atmosphere 的 SST 提供者，
 * 然后重测东边界沿岸风、h_c 与东带速度。沿岸急流对海温的反馈用**不动点迭代**（2 轮）。
 */
public class P265 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double GRID = 5000.0;
    static final double W = 100_000.0;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;

    static final int NZg = 64, NXg = 32;
    static double[] zs = new double[NZg], xs = new double[NXg];
    static double[][] anom = new double[NZg][NXg];
    static double[] hcPrev = new double[NZg];
    static GyreRow.Row[] rows = new GyreRow.Row[NZg];

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p265_report.txt"), "UTF-8");
        say("P265：海温场 -> 沿岸风 -> 沿岸层的闭环");
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        say(String.format(LF, "  PLATE_CELL=%d  LAMBDA=1/(%.0f 天)  SURF_FACTOR=%.0f  ANOM_MAX=%.1f K",
            cell, 1.0 / (SeaSurfaceTemp.LAMBDA * 86400), SeaSurfaceTemp.SURF_FACTOR, SeaSurfaceTemp.ANOM_MAX));
        say("");

        for (int i = 0; i < NXg; i++) xs[i] = 150_000 + i * (11_400_000.0 / (NXg - 1));
        // 先拿到每行的解
        for (int r = 0; r < NZg; r++) {
            int z = (int) ((r + 0.5) / NZg * ZC);
            zs[r] = z;
            GyreRow.Params p = new GyreRow.Params();
            p.h = GRID; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
            rows[r] = GyreRow.solve(5_500_000, z, SEED, cell, wind, p);
        }

        for (int iter = 0; iter < 3; iter++) {
            for (int r = 0; r < NZg; r++) {
                int z = (int) zs[r];
                double lat = WorldContract.latOf(z, ZC);
                double f = WorldContract.coriolis(lat);
                double dTdz = (Atmosphere.tZonalMean(WorldContract.latOf(z + 50_000, ZC))
                             - Atmosphere.tZonalMean(WorldContract.latOf(z - 50_000, ZC))) / 100_000.0;
                GyreRow.Row row = rows[r];
                double rd = CoastalLayer.rossbyRadius(f);
                double vJet = CoastalLayer.jetPeak(hcPrev[r], f);
                for (int i = 0; i < NXg; i++) {
                    double vs = 0;
                    if (row.valid) {
                        vs = sample(row, xs[i]) * SeaSurfaceTemp.SURF_FACTOR;
                        double d = row.eastX - xs[i];
                        if (d > 0 && d < 4 * rd) vs += vJet * Math.exp(-d / rd);
                    }
                    anom[r][i] = SeaSurfaceTemp.anomaly(vs, dTdz);
                }
            }
            Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() {
                @Override public double anomalyAt(int x, int z) { return lookup(x, z); }
            };
            double[] tauZ = new double[NZg];
            for (int m = 0; m < NZg; m++) {
                int z = (int) zs[m];
                double s1 = 0; int c = 0;
                GyreRow.Row row = rows[m];
                if (row.valid) {
                    for (int k = 1; k <= 4; k++) {
                        double[] a = Atmosphere.windStress(row.eastX - k * 120_000, z, SEED, cell, thS, 15_000);
                        s1 += a[1]; c++;
                    }
                }
                tauZ[m] = c > 0 ? s1 / c : 0;
            }
            double ds = (double) ZC / NZg;
            hcPrev = CoastalLayer.steadySmoothed(tauZ, ds);
            double mA = 0, mx = 0;
            for (double v : tauZ) { mA += Math.abs(v); if (Math.abs(v) > Math.abs(mx)) mx = v; }
            double amn = 9, amx = -9;
            for (double[] row2 : anom) for (double v : row2) { if (v < amn) amn = v; if (v > amx) amx = v; }
            say(String.format(LF, "  [迭代 %d] SST 异常范围 %+.2f ~ %+.2f K；沿岸 |tau_z| 平均 %.5f Pa（峰值 %+.4f）；h_c 极值 %+.2f m",
                iter, amn, amx, mA / NZg, mx, ext(hcPrev)));
        }
        say("");

        // ---- 东带结果 ----
        say("A. 东带（斯维尔德鲁普 + 沿岸层）");
        say(String.format(LF, "   %-8s %12s %14s %16s %14s", "纬度", "h_c m", "沿岸层mm/s", "斯维尔德鲁普mm/s", "合计mm/s"));
        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double lat = WorldContract.latOf(z, ZC);
            double f = WorldContract.coriolis(lat);
            double hcv = hcPrev[Math.min(NZg - 1, (int) ((double) z / ZC * NZg))];
            double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
            double sv = wind.at(0, z) / (1025.0 * H_TOTAL * WorldContract.betaForLatitude(lat, ZC)) * 1000;
            say(String.format(LF, "   %-8.1f %12.2f %14.3f %16.4f %14.4f",
                Math.toDegrees(lat), hcv, cb, sv, cb + sv));
        }
        say("");
        say("B. 西暖舌 / 东冷舌抽样（z 取 30 度纬线）");
        int zz = (int) (0.333 * (ZC / 2));
        int rr = (int) ((double) zz / ZC * NZg);
        say(String.format(LF, "   纬度 %.1f 度：", Math.toDegrees(WorldContract.latOf(zz, ZC))));
        for (int i = 0; i < NXg; i += 3) {
            say(String.format(LF, "     x=%.2fM  SST异常 %+.2f K", xs[i] / 1e6, anom[Math.min(NZg - 1, rr)][i]));
        }
        say("");
        say("C. 说明");
        say("  - T' = -(v_surf/lambda)*dT_zm/dz：西边界 v>0 ⇒ 暖舌；东边界 v<0 ⇒ 冷舌。无开关。");
        say("  - 沿岸急流对海温的反馈已用 3 轮不动点迭代计入。");
        rep.close();
    }

    static double sample(GyreRow.Row r, double x) {
        double fi = (x - r.westX) / r.h;
        int i0 = (int) Math.floor(fi);
        if (i0 < 0) return r.v[0];
        if (i0 >= r.n - 1) return r.v[r.n - 1];
        double t = fi - i0;
        return r.v[i0] * (1 - t) + r.v[i0 + 1] * t;
    }

    static double lookup(int x, int z) {
        int zz = ((z % ZC) + ZC) % ZC;
        double fz = zz / (double) ZC * NZg - 0.5;
        int i0 = (int) Math.floor(fz);
        double tz = fz - i0;
        int i1 = ((i0 + 1) % NZg + NZg) % NZg;
        i0 = ((i0 % NZg) + NZg) % NZg;
        double fx = (x - xs[0]) / (xs[NXg - 1] - xs[0]) * (NXg - 1);
        int j0 = (int) Math.floor(fx);
        if (j0 < 0) j0 = 0;
        if (j0 > NXg - 2) j0 = NXg - 2;
        double tx = fx - j0 < 0 ? 0 : (fx - j0 > 1 ? 1 : fx - j0);
        double a = anom[i0][j0] * (1 - tx) + anom[i0][j0 + 1] * tx;
        double b = anom[i1][j0] * (1 - tx) + anom[i1][j0 + 1] * tx;
        return a * (1 - tz) + b * tz;
    }

    static double ext(double[] v) { double m = 0; for (double x : v) if (Math.abs(x) > Math.abs(m)) m = x; return m; }

    static void say(String s) { System.out.println("[P265] " + s); rep.println("[P265] " + s); }
}
