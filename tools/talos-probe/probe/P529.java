package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P529：**鲁棒性扫描** —— 跨 6 个种子 x CONT_WAV {2000, 3000, 4000, 6000} km，
 * 量每一档的【种间极差】。
 *
 * 判据（用户裁决 §286）：不看某一张图好不好看，看**种间极差是否收窄**。
 * 根因假设：CONT_WAV/图幅 之比太小 ⇒ 有效独立样本只有个位数~几十 ⇒ 统计量方差巨大。
 */
public class P529 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P529] " + s); rep.flush(); System.out.println("[P529] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static int SEED = 1022228679;
    static final int UL_C = 800, UL_R = 3200;
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    static void offAll() {
        PlateField.A1_CONTINUOUS_CONT = false; PlateField.A2_DOMAIN_WARP = false; PlateField.A3_GATED_RELIEF = false;
        PlateField.A4_RIFT_GUARD = false; PlateField.A6_NO_OCEAN_ARC = false; PlateField.L2_NOISE_SIGN_SAFE = false;
        PlateField.A7_SITE_SHELL_FROM_LIVE = false; PlateField.A10_NO_FEAT = false; PlateField.A11_COAST_ONLY_ROUGH = false;
        PlateField.COAST_AMP = 0.0; PlateField.COAST_W = 600_000.0; PlateField.COAST_OCT = 2;
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06; PlateField.CONT_WAV = 8_000_000.0; PlateField.WARP_OCT = 2;
        PlateField.SKEL_MARGIN = 0.90; PlateField.ROUGH_M = 0.0; PlateField.ROUGH_G = 800.0;
        PlateField.ARC_H = 2600.0; PlateField.COLLIDE_H = 5600.0;
    }
    static void on(double wav) {
        offAll();
        PlateField.A1_CONTINUOUS_CONT = true; PlateField.A2_DOMAIN_WARP = true; PlateField.A3_GATED_RELIEF = true;
        PlateField.A4_RIFT_GUARD = true; PlateField.A6_NO_OCEAN_ARC = true; PlateField.L2_NOISE_SIGN_SAFE = true;
        PlateField.A7_SITE_SHELL_FROM_LIVE = true;
        PlateField.COAST_AMP = 0.15; PlateField.COAST_W = 600_000.0;
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06; PlateField.CONT_WAV = wav;
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

    /** 返回 {陆地均匀%, 陆地面积加权%, N-1, N-2, 左上角%, thr}。 */
    static double[] measure(double wav, double tgt, int[] lab, int[] stk) {
        on(wav);
        double thr = tune(tgt);
        byte[] land = new byte[W * W];
        long nl = 0; double wsum = 0, lw = 0; long ulN = 0, ulL = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
            for (int c = 0; c < W; c++) {
                boolean l = PlateField.isLandFullWithArc(xOf(c), z, seed, PlateField.PLATE_CELL, thr);
                land[r * W + c] = (byte) (l ? 1 : 0); wsum += cw; if (l) { nl++; lw += cw; }
                if (r >= UL_R && c < UL_C) { ulN++; if (l) ulL++; }
            } }
        int n1 = 0, n2 = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
            n2++;
        }
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 2; boolean lr = false;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; if (c2 == 0 || c2 == W - 1) lr = true;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) if (land[t] == 0 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
            if (!lr) n1++;
        }
        java.util.Arrays.fill(lab, 0);
        return new double[]{ 100.0 * nl / (W * W), 100.0 * lw / wsum, n1, n2, 100.0 * ulL / Math.max(1, ulN), thr };
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P529_robust_wav.txt"), "UTF-8");
        int[] seeds = { 1022228679, 1, 42, 2026, 987654321, 7 };
        double[] wavs = { 2_000_000, 3_000_000, 4_000_000, 6_000_000 };
        S("=== P529 鲁棒性扫描：6 种子 x CONT_WAV {2000,3000,4000,6000} km ===");
        S("工作点：A1+A2+A3+A4+A6+L2+A7 + A9(0.15/600)；陆地占比各自标定到该种子的 A0 基准");
        S("");
        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        String[] nm = { "陆地均匀%", "陆地面积加权%", "N-1 内海", "N-2 孤岛", "左上角陆地%" };
        for (int wi = 0; wi < wavs.length; wi++) {
            double wav = wavs[wi];
            double[][] v = new double[seeds.length][6];
            S("########## CONT_WAV = " + (int) (wav / 1000) + " km ##########");
            S(String.format(LF, "%-11s %10s %12s %7s %7s %10s %10s", "seed", "A0陆地%", "陆地均匀%", "N-1", "N-2", "左上角%", "thr"));
            for (int k = 0; k < seeds.length; k++) {
                SEED = seeds[k]; seed = SimTerrain.seedOf(SEED);
                offAll(); double tgt = coarseProd();
                double[] m = measure(wav, tgt, lab, stk);
                v[k] = m;
                S(String.format(LF, "%-11d %9.2f%% %11.2f%% %7.0f %7.0f %9.2f%% %10.5f",
                    SEED, 100 * tgt, m[0], m[2], m[3], m[4], m[5]));
            }
            S(String.format(LF, "  --- CONT_WAV = %d km 的种间统计（6 个种子）---", (int) (wav / 1000)));
            S(String.format(LF, "  %-16s %9s %9s %9s %9s", "量", "均值", "最小", "最大", "极差"));
            int[] col = { 0, 1, 2, 3, 4 };
            for (int j = 0; j < 5; j++) {
                int cj = col[j];
                double mn = 1e9, mx = -1e9, sum = 0;
                for (int k = 0; k < seeds.length; k++) { double x = v[k][cj]; sum += x; if (x < mn) mn = x; if (x > mx) mx = x; }
                S(String.format(LF, "  %-16s %9.2f %9.2f %9.2f %9.2f", nm[j], sum / seeds.length, mn, mx, mx - mn));
            }
            S("");
        }
        offAll();
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
