package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*; import java.util.*;

// P946 -- A6 口径体检：边界流速度 v 的纬度依赖 vs T-prime 的纬度依赖.
public class P946 {
    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final int DRIFT_MAX = 100_000;
    static final double TONGUE_MIN = 2.0;
    static final double M_PER_DEG = WorldContract.MAX_D / 90.0;
    static PrintStream rep;
    static void say(String s) { rep.println("[P946] " + s); rep.flush(); System.out.println("[P946] " + s); System.out.flush(); }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long,int[]> m = new LinkedHashMap<>();
        for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    // 与 OceanField.dTzmDz(DTDZ_MODE=3) 逐式相同: 极向为正, 5 度尺度中心差, |phi| 上做.
    static double dTds(int zRow) {
        double a = Math.abs(Math.toDegrees(WorldContract.latOf(zRow)));
        double hi = Math.min(a + 2.5, 90.0), lo = Math.max(a - 2.5, 0.0);
        double dy = (hi - lo) * M_PER_DEG;
        if (dy <= 0.0) return 0.0;
        return (Atmosphere.zmslkNew(Math.toRadians(hi)) - Atmosphere.zmslkNew(Math.toRadians(lo))) / dy;
    }

    static double atnh(double k) {
        double u = k / SeaSurfaceTemp.ANOM_MAX;
        if (u > 0.999999) u = 0.999999;
        if (u < -0.999999) u = -0.999999;
        return SeaSurfaceTemp.ANOM_MAX * 0.5 * Math.log((1.0 + u) / (1.0 - u));
    }

    static double pct(double[] v, int n, double p) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        double x = p * (n - 1); int i = (int) Math.floor(x); double f = x - i;
        return (i + 1 < n) ? a[i] * (1 - f) + a[i + 1] * f : a[n - 1];
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p946_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P946: A6 口径体检 -- 边界流速度 v 的纬度依赖 vs T-prime 的纬度依赖");
        say(String.format(LF, "  lambda=%.6e  ANOM_MAX=%.1f  DTDZ_MODE=%d  墙=wx+10km/ex-10km  |T-prime|门=%.1f K",
            SeaSurfaceTemp.LAMBDA, SeaSurfaceTemp.ANOM_MAX, OceanField.DTDZ_MODE, TONGUE_MIN));
        say("");
        // 每段墙: {zi, x, side, tAtWall, dTds, v, latAbs, degenerate}
        List<double[]> walls = new ArrayList<>();
        int nBasin = 0; long t0 = System.nanoTime();
        for (int zi = 0; zi < OceanField.ROWS; zi++) {
            int z = (int) ((zi + 0.5) / OceanField.ROWS * ZC);
            double d = dTds(z);
            double latAbs = Math.abs(Math.toDegrees(WorldContract.latOf(z)));
            List<int[]> bs = basinsAt(z); nBasin += bs.size();
            for (int[] b : bs) {
                int wx = b[0], ex = b[1];
                if (ex - wx < 200_000) continue;
                double tw = OceanField.anomalyAt(wx + 10_000, z, SEED);
                double te = OceanField.anomalyAt(ex - 10_000, z, SEED);
                for (int s = 0; s < 2; s++) {
                    double tt = (s == 0) ? tw : te;
                    double vv = Double.NaN; boolean deg = Math.abs(d) < 1e-12;
                    // t = ANOM_MAX*atanh(T-prime/ANOM_MAX) = atnh(T-prime);  t = -(v/lambda)*dTds
                    // => v = -lambda * atnh(T-prime) / dTds.   (S622: the earlier version multiplied
                    //    by ANOM_MAX a second time -- atnh already returns it. 8x too large.)
                    if (!deg) vv = -SeaSurfaceTemp.LAMBDA * atnh(tt) / d;
                    walls.add(new double[]{zi, (s == 0 ? wx : ex), (s == 0 ? -1 : 1), tt, d, vv, latAbs, deg ? 1 : 0});
                }
            }
        }
        int nw = walls.size();
        say(String.format(LF, "  墙数=%d  海盆=%d  耗时=%.1f s", nw, nBasin, (System.nanoTime() - t0) / 1e9));
        say("");
        say("  [A] 逐纬带分布 (|lat| 10 度一带)");
        say(String.format(LF, "  %-7s %6s %11s %12s %10s %10s %10s %10s %11s",
            "band", "nWall", "|T-prime|med", "|v|med(m/s)", "|v|p10", "|v|p90", "satT2K", "satV.02", "dTds(med)"));
        say("        (dTds 单位 K/m; |v| 单位 m/s; satT2K/satV.02 = 满足 |T-prime|>=2K / |v|>=0.02 的百分比)");
        say("        (dTds 单位 K/m; |v| 单位 m/s)");
        double[] vMed = new double[nw], tMed = new double[nw], dMed = new double[nw];
        for (int band = 0; band < 9; band++) {
            double lo = band * 10.0, hi = lo + 10.0;
            double[] tv = new double[nw], vv = new double[nw], dv = new double[nw];
            int n = 0, nT = 0, nV = 0;
            for (double[] w : walls) {
                double la = w[6]; if (la < lo || la >= hi) continue;
                tv[n] = Math.abs(w[3]);
                if (w[7] == 0) { vv[n] = Math.abs(w[5]); dv[n] = Math.abs(w[4]); } else { vv[n] = Double.NaN; dv[n] = 0; }
                if (Math.abs(w[3]) >= TONGUE_MIN) nT++;
                if (w[7] == 0 && Math.abs(w[5]) >= 0.02) nV++;
                n++;
            }
            if (n == 0) continue;
            // NaN 会被排序推到末尾; 退化行另计. 这里只对非退化统计 v.
            int nv2 = 0; double[] vv2 = new double[n];
            for (int k = 0; k < n; k++) if (!Double.isNaN(vv[k])) vv2[nv2++] = vv[k];
            say(String.format(LF, "  %-7s %6d %11.2f %12.4f %10.4f %10.4f %9d%% %9d%% %11.3e",
                ((int) lo) + "-" + ((int) hi), n, pct(tv, n, 0.5), pct(vv2, nv2, 0.5), pct(vv2, nv2, 0.10), pct(vv2, nv2, 0.90),
                (int) Math.round(100.0 * nT / n), (int) Math.round(100.0 * nV / n), pct(dv, n, 0.5)));
            vMed[band] = pct(vv2, nv2, 0.5); tMed[band] = pct(tv, n, 0.5); dMed[band] = pct(dv, n, 0.5);
        }
        say("");
        say("  [B] A6 原判据复现 (经向段, |T-prime| >= 2.0 K) -- 必须等于套件的读数");
        int nM = 0, nMT = 0, nW = 0, nWT = 0, nE = 0, nET = 0;
        for (double[] a : walls) for (double[] b : walls) {
            if ((int) b[0] != (int) a[0] + 1) continue;
            if (b[2] != a[2]) continue;
            if (Math.abs(b[1] - a[1]) > DRIFT_MAX) continue;
            nM++; if (Math.abs(a[3]) >= TONGUE_MIN) { nMT++; if (a[2] < 0) nWT++; else nET++; }
            if (a[2] < 0) nW++; else nE++;
        }
        say(String.format(LF, "  经向段=%d  带流(|T-prime|)=%d = %.1f%%   西 %d/%d  东 %d/%d",
            nM, nMT, 100.0 * nMT / Math.max(1, nM), nWT, nW, nET, nE));
        say(String.format(LF, "  GATE_A6_MERIDIONAL=%s", (100.0 * nMT / Math.max(1, nM) >= 50.0) ? "PASS" : "FAIL"));
        say("");
        say("  [C] 同一批经向段, 换成【速度】判据 (|v| >= vmin)");
        double[] vgrid = {0.002, 0.005, 0.01, 0.02, 0.05, 0.10};
        for (double vmin : vgrid) {
            int nV = 0, nDeg = 0;
            for (double[] a : walls) for (double[] b : walls) {
                if ((int) b[0] != (int) a[0] + 1) continue;
                if (b[2] != a[2]) continue;
                if (Math.abs(b[1] - a[1]) > DRIFT_MAX) continue;
                if (a[7] != 0) { nDeg++; continue; }
                if (Math.abs(a[5]) >= vmin) nV++;
            }
            say(String.format(LF, "    vmin=%.3f m/s -> %.1f%%   (退化/不可判 %d 段)",
                vmin, 100.0 * nV / Math.max(1, nM), nDeg));
        }
        say("");
        say("  [D] 口径缺陷直接量化: 在 |v| >= 0.02 m/s 的经向段里, 有多少 |T-prime| < 2.0 K (T-prime 判据看不见它们)");
        int nFast = 0, nFastBlind = 0;
        double[] blindLat = new double[nw]; int nbl = 0;
        for (double[] a : walls) {
            if (a[7] != 0) continue;
            if (Math.abs(a[5]) < 0.02) continue;
            nFast++;
            if (Math.abs(a[3]) < TONGUE_MIN) { nFastBlind++; if (nbl < nw) blindLat[nbl++] = a[6]; }
        }
        say(String.format(LF, "    |v|>=0.02 的墙 %d 段, 其中 |T-prime|<2.0K 的 %d 段 = %.1f%%",
            nFast, nFastBlind, 100.0 * nFastBlind / Math.max(1, nFast)));
        if (nbl > 0) say(String.format(LF, "    这些被漏掉的墙的 |lat| 中位 = %.1f 度 (p10=%.1f p90=%.1f)",
            pct(blindLat, nbl, 0.5), pct(blindLat, nbl, 0.10), pct(blindLat, nbl, 0.90)));
        say("");
        say(String.format(LF, "  reentryBlocked=%d", OceanField.reentryBlocked));
        say("DONE");
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }
}
