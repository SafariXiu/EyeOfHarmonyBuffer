package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;

/**
 * P286：A2 的**季节稳健性**（真实大气风）+ A3 的**赤道奇异点**与纬度口径。
 */
public class P286 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int GRAD = 500_000, CLAT = 100_000;
    static final int NLAT = 2 * (24_000_000 / CLAT) + 1;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final HashMap<String, double[]> CACHE = new HashMap<String, double[]>();
    static int cell;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p286_report.txt"), "UTF-8");
        cell = PlateField.PLATE_CELL;
        final GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say("P286：A2 季节稳健性（真实大气风） + A3 赤道奇异点");
        say(String.format(LF, "  旋度格点步长 %d km（%d 点）；GRAD=%d km；PLATE_CELL=%d", CLAT/1000, NLAT, GRAD/1000, cell));
        say("");
        say("A. A2 西边界流 @100 km 全深均（真实大气风，纬度 ±5..±45 步长 5，每纬线 6 个 x 起点）");
        say(String.format(LF, "   %-10s %6s %9s %9s %9s %9s %9s %9s %8s", "季节", "n", "p10", "p25", "p50", "p75", "p90", "max", ">=75%"));
        double[] allSeasons = new double[4000];
        int na = 0;
        for (int si = 0; si < 4; si++) {
            double day = si * WorldContract.DAYS_PER_YEAR / 4.0;
            double th = Atmosphere.theta(day);
            long t0 = System.nanoTime();
            double[] v = new double[400];
            int n = 0;
            for (int li = 0; li < 17; li++) {
                int latSign = li < 9 ? 1 : -1;
                int latDeg = 5 + (li % 9) * 5;
                int z = (int) (latSign * latDeg / 90.0 * (ZC / 2));
                for (int q = 0; q < 6; q++) {
                    int x = (q * 2_300_000 + li * 97_000) % 9_000_000;
                    GyreRow.Params p = params();
                    GyreRow.Row r = GyreRow.solve(x, z, SD, cell, realWind(th), p);
                    if (!r.valid) continue;
                    if (r.eastX >= p.maxRow - 20_000 || r.westX <= -p.maxRow + 20_000) continue;
                    if ((r.eastX - r.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                    int qq = Math.min(r.n, (int) (W / r.h));
                    double s = 0;
                    for (int i = 0; i < qq; i++) s += r.v[i];
                    v[n++] = Math.abs(s / qq * 1000.0);
                }
            }
            double[] s2 = Arrays.copyOf(v, n);
            Arrays.sort(s2);
            int p75 = 0; for (double x : s2) if (x >= 75) p75++;
            for (int i = 0; i < n; i++) allSeasons[na++] = s2[i];
            say(String.format(LF, "   %-10s %6d %9.1f %9.1f %9.1f %9.1f %9.1f %9.1f %7.0f%%",
                "day=" + (int) day, n, s2[(int)(0.10*n)], s2[(int)(0.25*n)], s2[n/2], s2[(int)(0.75*n)], s2[(int)(0.90*n)], s2[n-1], p75*100.0/n));
            say(String.format(LF, "   （本季耗时 %.0f s）", (System.nanoTime()-t0)/1e9));
        }
        double[] aa = Arrays.copyOf(allSeasons, na);
        Arrays.sort(aa);
        int a75 = 0; for (double x : aa) if (x >= 75) a75++;
        say(String.format(LF, "   %-10s %6d %9.1f %9.1f %9.1f %9.1f %9.1f %9.1f %7.0f%%",
            "四季合计", na, aa[(int)(0.10*na)], aa[(int)(0.25*na)], aa[na/2], aa[(int)(0.75*na)], aa[(int)(0.90*na)], aa[na-1], a75*100.0/na));
        say("");

        say("B. A3 沿岸层的赤道奇异点（四个季节）");
        int NM = 120, NQ = 6;
        say(String.format(LF, "   %-10s %10s %10s %10s %10s %10s %10s", "季节", "lat=0", "2", "5", "10", "20", "30"));
        for (int si = 0; si < 4; si++) {
            double day = si * WorldContract.DAYS_PER_YEAR / 4.0;
            double th = Atmosphere.theta(day);
            double[] tauRow = new double[NM];
            for (int r = 0; r < NM; r++) {
                int z = (int) ((r + 0.5) / NM * ZC);
                double s = 0; int c = 0;
                for (int q = 0; q < NQ; q++) {
                    GyreRow.Params p = params();
                    GyreRow.Row row = GyreRow.solve((q * 2_300_000 + r * 97_000) % 9_000_000, z, SD, cell, band, p);
                    if (!row.valid) continue;
                    if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) continue;
                    if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) continue;
                    for (int k = 1; k <= 3; k++) {
                        double tz = Atmosphere.windStress(row.eastX - k * 25_000, z, SD, cell, th, GRAD)[1];
                        s += tz; c++;
                    }
                }
                tauRow[r] = c > 0 ? s / c : 0;
            }
            double[] hc = CoastalLayer.steadySmoothed(tauRow, (double) ZC / NM);
            StringBuilder sb = new StringBuilder();
            for (int latDeg : new int[]{0, 2, 5, 10, 20, 30}) {
                int z = (int) (latDeg / 90.0 * (ZC / 2));
                double lat = WorldContract.latOf(z, ZC);
                double f = WorldContract.coriolis(lat);
                double hcv = hc[Math.min(NM - 1, (int) ((double) z / ZC * NM))];
                double cb = CoastalLayer.eastBandContribution(hcv, f, W) * 1000;
                sb.append(String.format(LF, " %10.1f", cb));
            }
            say(String.format(LF, "   %-10s%s", "day=" + (int) day, sb.toString()));
        }
        say("");
        say("C. 诊断：rossbyRadius 用了 |f| 下限 F_MIN，而 jetPeak 用真实 f ⇒ v_max ∝ 1/f 发散");
        say(String.format(LF, "   F_MIN=%.1e  R_d(F_MIN)=%.0f km  sqrt(g'H1)=%.3f m/s",
            CoastalLayer.F_MIN, CoastalLayer.rossbyRadius(CoastalLayer.F_MIN)/1000.0,
            Math.sqrt(CoastalLayer.G_PRIME * CoastalLayer.H_THERMOCLINE)));
        for (int latDeg : new int[]{0, 1, 2, 5, 10, 20, 30, 45}) {
            double f = WorldContract.coriolis(Math.toRadians(latDeg));
            double hcv = -300.0;
            say(String.format(LF, "   lat=%2d  f=%.3e  R_d=%6.0f km  jetPeak(hc=-300)=%8.3f m/s", latDeg, f,
                CoastalLayer.rossbyRadius(f)/1000.0, CoastalLayer.jetPeak(hcv, f)));
        }
        rep.close();
    }

    static GyreRow.Params params() {
        GyreRow.Params p = new GyreRow.Params();
        p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
        return p;
    }

    static GyreRow.WindCurl realWind(final double th) {
        return new GyreRow.WindCurl() {
            @Override public double at(int x, int z) { return realCurl(x, z, th); }
        };
    }

    static double realCurl(int x, int z, double th) {
        double[] lat = lattice(z, th);
        double f = (x + 24_000_000.0) / CLAT;
        int i = (int) Math.floor(f);
        if (i < 0) return lat[0];
        if (i >= NLAT - 1) return lat[NLAT - 1];
        double t = f - i;
        return lat[i] * (1 - t) + lat[i + 1] * t;
    }

    static synchronized double[] lattice(int z, double th) {
        String key = z + "_" + (long) (th * 1e6);
        double[] a = CACHE.get(key);
        if (a != null) return a;
        a = new double[NLAT];
        int g = GRAD;
        for (int i = 0; i < NLAT; i++) {
            int x = (int) ((long) (i - NLAT / 2) * CLAT);
            double tyE = Atmosphere.windStress(x + g, z, SD, cell, th, g)[1];
            double tyW = Atmosphere.windStress(x - g, z, SD, cell, th, g)[1];
            double txN = Atmosphere.windStress(x, z + g, SD, cell, th, g)[0];
            double txS = Atmosphere.windStress(x, z - g, SD, cell, th, g)[0];
            a[i] = (tyE - tyW) / (2.0 * g) - (txN - txS) / (2.0 * g);
        }
        CACHE.put(key, a);
        return a;
    }

    static void say(String s) { System.out.println("[P286] " + s); rep.println("[P286] " + s); }
}
