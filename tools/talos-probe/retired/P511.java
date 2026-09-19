package probe;

import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P511：新目标（用户裁决 2026-09-17）—— **大陆内部完全干净、没有任何海洋**。
 *
 * 阶梯：A123 -> A1234（裂谷保底）-> A12345（噪声按骨架门控）-> A123456（再关岛弧）。
 * 新判据 N-1 = 内海（被陆地完全包围的海分量）数 == 0。
 */
public class P511 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P511] " + s); rep.flush(); System.out.println("[P511] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static final int MB = 81;
    static final double MW = 300_000.0, G1_TARGET = 400_000.0, WALL_TOL = 60_000.0;
    static final int UL_C = 800, UL_R = 3200;
    static final MapWriter.ColorMap SEALAND = new MapWriter.ColorMap() {
        @Override public int rgb(double t) { return t < 0.5 ? 0x1B3B6F : 0xE0D5B0; }
    };
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P511_cleanland.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        S("=== P511 大陆内部零海洋（新目标）===");
        sw(false, false, false, false, false, false);
        double target = coarseProd();
        S(String.format(LF, "N-5 标定目标 = 生产默认陆地占比 %.4f%%", 100 * target));
        S("");
        String[] tags = { "A123", "A1234", "A12345", "A123456" };
        boolean[][] cfg = {
            { false, false, false }, { true, false, false }, { true, true, false }, { true, true, true }
        };
        for (int k = 0; k < 4; k++) run(tags[k], cfg[k][0], cfg[k][1], cfg[k][2], target);
        sw(false, false, false, false, false, false);
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06; PlateField.RIFT_FRAC = 0.55; PlateField.NOISE_FADE = 800.0;
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static void sw(boolean a1, boolean a2, boolean a3, boolean a4, boolean a5, boolean a6) {
        PlateField.A1_CONTINUOUS_CONT = a1; PlateField.A2_DOMAIN_WARP = a2; PlateField.A3_GATED_RELIEF = a3;
        PlateField.A4_RIFT_GUARD = a4; PlateField.A5_NOISE_FROM_SKEL = a5; PlateField.A6_NO_OCEAN_ARC = a6;
    }

    static void run(String tag, boolean a4, boolean a5, boolean a6, double target) throws Exception {
        sw(true, true, true, a4, a5, a6);
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06;
        S("########## " + tag + "  (A4=" + a4 + " A5=" + a5 + " A6=" + a6 + ") ##########");
        double thr = tune(target);
        byte[] land = new byte[W * W];
        long nl = 0; double wsum = 0, lw = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
            for (int c = 0; c < W; c++) {
                boolean l = PlateField.isLandFullWithArc(xOf(c), z, seed, PlateField.PLATE_CELL, thr);
                land[r * W + c] = (byte) (l ? 1 : 0); wsum += cw; if (l) { nl++; lw += cw; }
            } }
        S(String.format(LF, "  thr=%.5f  陆地 均匀 %.2f%%  面积加权 %.2f%%", thr, 100.0 * nl / (W * W), 100.0 * lw / wsum));
        double[] v = new double[W * W];
        for (int i = 0; i < W * W; i++) v[i] = land[i];
        MapWriter.writePng(new File(MAPDIR, "sealandC_" + tag + ".png"), W, W, v, 0.0, 1.0, SEALAND);

        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        int[] K = counts(land, lab, stk);
        S(String.format(LF, "  【N-1】内海 = %d  （点状 %d / 河流状 %d）   非最大陆块 = %d（点状 %d）",
            K[0], K[1], K[2], K[3], K[4]));
        S(String.format(LF, "  【N-6】>=2%% 陆块 = %d", K[5]));
        long ulN = 0, ulL = 0;
        for (int r = UL_R; r < W; r++) for (int c = 0; c < UL_C; c++) { ulN++; if (land[r * W + c] == 1) ulL++; }
        int[] labU = new int[W * W]; int ulLand = 0, ulDot = 0;
        for (int r = UL_R; r < W; r++) for (int c = 0; c < UL_C; c++) {
            int i = r * W + c; if (land[i] == 0 || labU[i] != 0) continue;
            int sp = 0; stk[sp++] = i; labU[i] = 1; long a = 0;
            while (sp > 0) { int p = stk[--sp]; a++; int rr = p / W, cc = p - rr * W;
                int[] nn = { rr * W + wr(cc + 1), rr * W + wr(cc - 1), wr(rr + 1) * W + cc, wr(rr - 1) * W + cc };
                for (int t : nn) if (land[t] == 1 && labU[t] == 0) { labU[t] = 1; stk[sp++] = t; } }
            ulLand++; if (a < 100) ulDot++;
        }
        S(String.format(LF, "  【N-3】左上角 陆地 %.2f%%  陆块 %d  碎屑(<100px) %d", 100.0 * ulL / ulN, ulLand, ulDot));
        byte[] sm = majority(land, MB);
        List<Double> es = new ArrayList<>();
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c = 0; c < W; c++) {
                if (sm[r * W + c] == 0) continue;
                if (sm[r * W + wr(c + 1)] == 1 && sm[r * W + wr(c - 1)] == 1 && sm[wr(r + 1) * W + c] == 1 && sm[wr(r - 1) * W + c] == 1) continue;
                es.add(PlateField.edgeDistance(xOf(c), z, seed) / 1000.0);
            } }
        java.util.Collections.sort(es);
        S(String.format(LF, "  【N-7】平滑海岸线 %d px  edgeDist 中位 %.0f km", es.size(), es.isEmpty() ? 0 : es.get(es.size() / 2)));
        p243(land);
        S("");
    }

    static int[] counts(byte[] land, int[] lab, int[] stk) {
        int nS = 0, sDot = 0, sLin = 0, nL = 0, lDot = 0, nBig = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W; a++;
                double dx = c * step, dz = r * step; sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
            nL++; if (a > 0.02 * W * W) nBig++;
            double[] m = shape(a, sx, sz, sxx, szz, sxz);
            if (m[0] < 30) lDot++;
        }
        java.util.Arrays.fill(lab, 0);
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 2; long a = 0; boolean lr = false;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W; a++; if (c == 0 || c == W - 1) lr = true;
                double dx = c * step, dz = r * step; sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 0 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
            if (lr) continue;
            nS++; double[] m = shape(a, sx, sz, sxx, szz, sxz);
            if (m[0] < 30) sDot++; else if (m[1] >= 3.0) sLin++;
        }
        java.util.Arrays.fill(lab, 0);
        return new int[]{ nS, sDot, sLin, nL, lDot, nBig };
    }

    static double[] shape(long a, double sx, double sz, double sxx, double szz, double sxz) {
        double mx = sx / a, mz = sz / a;
        double cxx = sxx / a - mx * mx, czz = szz / a - mz * mz, cxz = sxz / a - mx * mz;
        double tr = cxx + czz, det = cxx * czz - cxz * cxz;
        double disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
        double l1 = tr / 2 + disc, l2 = Math.max(1e-9, tr / 2 - disc);
        return new double[]{ 2 * Math.sqrt(a * step * step / Math.PI) / 1000.0, Math.sqrt(l1 / l2) };
    }

    static double coarseProd() {
        int G = 400; long n = 0;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (PlateField.isLandWithCell(x, z, seed, PlateField.PLATE_CELL)) n++;
        }
        return n / (double) (G * G);
    }
    static double coarse(double thr) {
        int G = 400; long n = 0;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (PlateField.isLandFullWithArc(x, z, seed, PlateField.PLATE_CELL, thr)) n++;
        }
        return n / (double) (G * G);
    }
    static double tune(double target) {
        double lo = -1.20, hi = 1.20;
        for (int it = 0; it < 22; it++) { double mid = 0.5 * (lo + hi); if (coarse(mid) > target) lo = mid; else hi = mid; }
        return 0.5 * (lo + hi);
    }

    static void p243(byte[] a) {
        final int MAXI = 512;
        int[] pxw = new int[MAXI], pxe = new int[MAXI], pCoast = new int[MAXI];
        long[] pArea = new long[MAXI];
        long wTot = 0, wLong = 0, seaTot = 0, seaInBasin = 0;
        int pm = 0;
        for (int iz = 0; iz < W; iz++) {
            int[] xw = new int[MAXI], xe = new int[MAXI], wt = new int[MAXI];
            int m = 0, ix = 0;
            while (ix < W) {
                if (a[iz * W + ix] == 1) { ix++; continue; }
                int j = ix; while (j + 1 < W && a[iz * W + j + 1] == 0) j++;
                seaTot += (j - ix + 1);
                if ((j - ix + 1) * step >= MW) { seaInBasin += (j - ix + 1); if (m < MAXI) { xw[m] = ix; xe[m] = j; wt[m] = j - ix + 1; m++; } }
                ix = j + 1;
            }
            int[] coast = new int[m]; long[] area = new long[m];
            boolean[] matched = new boolean[pm];
            for (int c = 0; c < m; c++) {
                int best = -1, bestOv = 0;
                for (int p = 0; p < pm; p++) { int lo = Math.max(xw[c], pxw[p]), hi = Math.min(xe[c], pxe[p]); if (hi - lo > bestOv) { bestOv = hi - lo; best = p; } }
                if (best >= 0 && bestOv > 0) {
                    double dd = Math.abs(xw[c] - pxw[best]) * step;
                    coast[c] = dd <= WALL_TOL ? pCoast[best] + (int) step : (int) step;
                    area[c] = pArea[best] + (long) wt[c] * (long) step; matched[best] = true;
                } else { coast[c] = (int) step; area[c] = (long) wt[c] * (long) step; }
            }
            if (pm > 0) for (int p = 0; p < pm; p++) if (!matched[p]) { wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
            System.arraycopy(xw, 0, pxw, 0, m); System.arraycopy(xe, 0, pxe, 0, m);
            System.arraycopy(coast, 0, pCoast, 0, m); System.arraycopy(area, 0, pArea, 0, m); pm = m;
        }
        for (int p = 0; p < pm; p++) { wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
        S(String.format(LF, "  【N-4】G1b = %.4f    G2 = %.4f", wTot > 0 ? wLong / (double) wTot : 0, seaTot > 0 ? seaInBasin / (double) seaTot : 0));
    }

    static byte[] majority(byte[] m, int K) {
        int half = K / 2;
        int[] t1 = new int[W * W], t2 = new int[W * W];
        for (int r = 0; r < W; r++) { int base = r * W, s = 0;
            for (int k = -half; k <= half; k++) s += m[base + wr(k)];
            t1[base] = s;
            for (int c = 1; c < W; c++) { s += m[base + wr(c + half)] - m[base + wr(c - half - 1)]; t1[base + c] = s; } }
        for (int c = 0; c < W; c++) { int s = 0;
            for (int k = -half; k <= half; k++) s += t1[wr(k) * W + c];
            t2[c] = s;
            for (int r = 1; r < W; r++) { s += t1[wr(r + half) * W + c] - t1[wr(r - half - 1) * W + c]; t2[r * W + c] = s; } }
        byte[] out = new byte[W * W]; int thr = K * K / 2;
        for (int i = 0; i < W * W; i++) out[i] = (byte) (t2[i] > thr ? 1 : 0);
        return out;
    }
}
