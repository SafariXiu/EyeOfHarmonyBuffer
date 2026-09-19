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
 * P266：**受控 2x2 A/B**（按 §34.7 的教训）。
 *
 * <p>固定同一批海盆、同一批采样点、同一个差分步长，只切两个开关：
 * 副高 cell（CELL_GAIN）与 海温异常（SST_PROVIDER）。
 */
public class P266 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double A_H = 1.9e4;
    static final double GRID = 5000.0;
    static final double W = 100_000.0;
    static final int NZg = 64;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static GyreRow.Row[] rows = new GyreRow.Row[NZg];
    static int[] zs = new int[NZg];
    static double[] tauSm = new double[NZg];      // 固定采样点
    static int[] smIdx = new int[NZg];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p266_report.txt"), "UTF-8");
        say("P266：受控 2x2 A/B —— 副高 cell x 海温异常");
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind wind = new GyreRow.BandedWind(TAU0, ZC);
        double thS = Atmosphere.theta(0.0);

        // ---- 固定同一批行 + 同一批采样点（4 个格共用）----
        for (int r = 0; r < NZg; r++) {
            zs[r] = (int) ((r + 0.5) / NZg * ZC);
            GyreRow.Params p = new GyreRow.Params();
            p.h = GRID; p.rhoH = 1025.0 * H_TOTAL; p.aH = A_H; p.zCycle = ZC;
            rows[r] = GyreRow.solve(5_500_000, zs[r], SD, cell, wind, p);
            double gate = Math.PI * p.deltaAt(zs[r]);
            smIdx[r] = rows[r].valid ? (int) (rows[r].eastX - 200_000) : Integer.MIN_VALUE;
        }
        say(String.format(LF, "  固定 %d 条行，每条行取唯一样本点 eastX-200km，差分步长 15 km", NZg));
        say("");

        // ---- 预先算好 SST 网格（4 个格共用同一份）----
        double[] hcPrev = new double[NZg];
        double[][] anom = new double[NZg][8];
        double[] xs8 = new double[8];
        for (int i = 0; i < 8; i++) xs8[i] = 500_000 + i * 1_400_000;
        for (int it = 0; it < 3; it++) {
            for (int r = 0; r < NZg; r++) {
                int z = zs[r];
                double lat = WorldContract.latOf(z, ZC);
                double f = WorldContract.coriolis(lat);
                double dTdz = (Atmosphere.tZonalMean(WorldContract.latOf(z + 50_000, ZC))
                             - Atmosphere.tZonalMean(WorldContract.latOf(z - 50_000, ZC))) / 100_000.0;
                double rd = CoastalLayer.rossbyRadius(f);
                double vJet = CoastalLayer.jetPeak(hcPrev[r], f);
                for (int i = 0; i < 8; i++) {
                    double vs = 0;
                    if (rows[r].valid) {
                        vs = samp(rows[r], xs8[i]) * SeaSurfaceTemp.SURF_FACTOR;
                        double d = rows[r].eastX - xs8[i];
                        if (d > 0 && d < 4 * rd) vs += vJet * Math.exp(-d / rd);
                    }
                    anom[r][i] = SeaSurfaceTemp.anomaly(vs, dTdz);
                }
            }
            double[] tz = measure(cell, wind, thS, true, 1.4);
            hcPrev = CoastalLayer.steadySmoothed(tz, (double) ZC / NZg);
        }

        // ---- 受控 2x2 ----
        say("A. 2x2 受控 A/B（正 = 开）");
        say(String.format(LF, "   %-10s %-10s %16s %12s %18s %16s",
            "副高cell", "海温异常", "沿岸|tau_z|均值Pa", "h_c极值m", "东带@36度mm/s", "东带@22.5度mm/s"));
        double[][] res = new double[4][4];
        int k = 0;
        for (int cOn = 0; cOn < 2; cOn++) {
            for (int sOn = 0; sOn < 2; sOn++) {
                Atmosphere.CELL_GAIN = cOn == 1 ? 1.4 : 0.0;
                if (sOn == 1) {
                    Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() {
                        @Override public double anomalyAt(int x, int z) { return lookup(x, z, anom, xs8); }
                    };
                } else Atmosphere.SST_PROVIDER = null;
                double[] tz = measure(cell, wind, thS, true, 1.4);
                double ma = 0; for (double v : tz) ma += Math.abs(v);
                double[] hc = CoastalLayer.steadySmoothed(tz, (double) ZC / NZg);
                double e36 = eastBand(hc, 0.40, wind);
                double e22 = eastBand(hc, 0.25, wind);
                res[k][0] = ma / NZg; res[k][1] = ext(hc); res[k][2] = e36; res[k][3] = e22;
                say(String.format(LF, "   %-10s %-10s %16.5f %12.2f %18.2f %16.2f",
                    cOn == 1 ? "on" : "off", sOn == 1 ? "on" : "off", ma / NZg, ext(hc), e36, e22));
                k++;
            }
        }
        say("");
        say(String.format(LF, "   只开 cell ：tau x%.2f，东带@36 %.2f mm/s", res[1][0] / res[0][0], res[1][2]));
        say(String.format(LF, "   只开 SST  ：tau x%.2f，东带@36 %.2f mm/s", res[2][0] / res[0][0], res[2][2]));
        say(String.format(LF, "   两个都开  ：tau x%.2f，东带@36 %.2f mm/s", res[3][0] / res[0][0], res[3][2]));
        say("   （目标：东带 30 mm/s）");
        rep.close();
    }

    static double[] measure(int cell, GyreRow.BandedWind wind, double thS, boolean dummy, double g) {
        double[] tz = new double[NZg];
        for (int r = 0; r < NZg; r++) {
            if (smIdx[r] == Integer.MIN_VALUE) { tz[r] = 0; continue; }
            double[] a = Atmosphere.windStress(smIdx[r], zs[r], SD, cell, thS, 15_000);
            tz[r] = a[1];
        }
        return tz;
    }

    static double eastBand(double[] hc, double bandD, GyreRow.BandedWind wind) {
        int z = (int) (bandD * (ZC / 2));
        double lat = WorldContract.latOf(z, ZC);
        double f = WorldContract.coriolis(lat);
        double hcv = hc[Math.min(NZg - 1, (int) ((double) z / ZC * NZg))];
        double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
        double sv = wind.at(0, z) / (1025.0 * H_TOTAL * WorldContract.betaForLatitude(lat, ZC)) * 1000;
        return cb + sv;
    }

    static double samp(GyreRow.Row r, double x) {
        double fi = (x - r.westX) / r.h;
        int i0 = (int) Math.floor(fi);
        if (i0 < 0) return r.v[0];
        if (i0 >= r.n - 1) return r.v[r.n - 1];
        double t = fi - i0;
        return r.v[i0] * (1 - t) + r.v[i0 + 1] * t;
    }

    static double lookup(int x, int z, double[][] anom, double[] xs8) {
        int zz = ((z % ZC) + ZC) % ZC;
        double fz = zz / (double) ZC * NZg - 0.5;
        int i0 = (int) Math.floor(fz); double tz = fz - i0;
        int i1 = ((i0 + 1) % NZg + NZg) % NZg; i0 = ((i0 % NZg) + NZg) % NZg;
        double fx = (x - xs8[0]) / (xs8[7] - xs8[0]) * 7.0;
        int j0 = (int) Math.floor(fx);
        if (j0 < 0) j0 = 0; if (j0 > 6) j0 = 6;
        double tx = fx - j0; if (tx < 0) tx = 0; if (tx > 1) tx = 1;
        double a = anom[i0][j0] * (1 - tx) + anom[i0][j0 + 1] * tx;
        double b = anom[i1][j0] * (1 - tx) + anom[i1][j0 + 1] * tx;
        return a * (1 - tz) + b * tz;
    }

    static double ext(double[] v) { double m = 0; for (double x : v) if (Math.abs(x) > Math.abs(m)) m = x; return m; }

    static void say(String s) { System.out.println("[P266] " + s); rep.println("[P266] " + s); }
}
