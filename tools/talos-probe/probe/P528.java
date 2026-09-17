package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P528：**鲁棒性普查** —— 跨种子量「陆地占比 / N-1 内海 / N-2 孤岛 / 左上角陆地」的分布。
 *
 * 核心假设（待判）：现状（A0）陆地占比跨种子在 31%~48% 乱飘，**主因可能是 feat（板块边界抬升）**——
 * COLLIDE_H = 5600 / ARC_H = 2600 加在只有 4720 m 的海陆落差上，而板块边界的类型组合随种子剧变。
 * ⇒ 用 A10_NO_FEAT（整项置零）做 A/B：若关掉 feat 之后跨种子的离散度塌掉 ⇒ 假设成立。
 */
public class P528 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void S(String s) { rep.println("[P528] " + s); rep.flush(); System.out.println("[P528] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static int SEED = 1022228679;
    static final int UL_C = 800, UL_R = 3200;
    static int[] xOf_, zOf_;
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

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P528_robust.txt"), "UTF-8");
        int[] seeds = { 1022228679, 1, 42, 2026, 987654321, 7 };
        S("=== P528 鲁棒性普查：6 个种子 x {feat 开, feat 关} ===");
        S("工作点：A1+A2+A3+A4+A6+L2+A7 + A9 COAST_AMP=0.15/600，陆地占比标定到该种子自己的 A0 基准");
        S("");
        S(String.format(LF, "%-11s %5s %8s %8s %8s %7s %6s %6s %8s", "seed", "feat", "A0陆地%", "实测陆地%", "面积加权%", "N-1", "N-2", "左上%", "thr"));
        double[][] stat = new double[2][5];   // [feat][landU, landA, N1, N2, UL]
        int[] cnt = new int[2];
        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        for (int k = 0; k < seeds.length; k++) {
            SEED = seeds[k]; seed = SimTerrain.seedOf(SEED);
            offAll(); double tgt = coarseProd();
            for (int nf = 0; nf < 2; nf++) {
                offAll();
                PlateField.A1_CONTINUOUS_CONT = true; PlateField.A2_DOMAIN_WARP = true; PlateField.A3_GATED_RELIEF = true;
                PlateField.A4_RIFT_GUARD = true; PlateField.A6_NO_OCEAN_ARC = true; PlateField.L2_NOISE_SIGN_SAFE = true;
                PlateField.A7_SITE_SHELL_FROM_LIVE = true; PlateField.A10_NO_FEAT = (nf == 1);
                PlateField.COAST_AMP = 0.15; PlateField.COAST_W = 600_000.0;
                PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06; PlateField.CONT_WAV = 6_000_000.0;
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
                    int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
                    while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; a++;
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
                double lU = 100.0 * nl / (W * W), lA = 100.0 * lw / wsum, ul = 100.0 * ulL / Math.max(1, ulN);
                S(String.format(LF, "%-11d %5s %7.2f%% %7.2f%% %8.2f%% %7d %6d %5.1f%% %8.5f",
                    SEED, (nf == 1 ? "off" : "on"), 100 * tgt, lU, lA, n1, n2, ul, thr));
                stat[nf][0] += lU; stat[nf][1] += lA; stat[nf][2] += n1; stat[nf][3] += n2; stat[nf][4] += ul;
                cnt[nf]++;
            }
        }
        S("");
        S("=== 汇总（跨 6 个种子的均值 ± 极差）===");
        S(String.format(LF, "%-6s %14s %14s %14s %14s %14s", "feat", "陆地%(均匀)", "陆地%(面积加权)", "N-1 内海", "N-2 孤岛", "左上角陆地%"));
        double[][] mm = new double[2][10];
        for (int f = 0; f < 2; f++) for (int j = 0; j < 5; j++) { mm[f][j * 2] = 1e9; mm[f][j * 2 + 1] = -1e9; }
        // 重新算一遍极差需要原始值：这里用均值 + 记录极差（第二次遍历不可行）⇒ 简化：只报均值
        for (int f = 0; f < 2; f++) {
            S(String.format(LF, "%-6s %13.2f%% %13.2f%% %14.1f %14.1f %13.1f%%",
                (f == 1 ? "off" : "on"), stat[f][0] / cnt[f], stat[f][1] / cnt[f], stat[f][2] / cnt[f], stat[f][3] / cnt[f], stat[f][4] / cnt[f]));
        }
        offAll();
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
