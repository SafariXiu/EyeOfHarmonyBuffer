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

/** P516：陆地占比扫描 —— N-5（47.53%）与 N-6（陆块 3~7）是否真的互斥。 */
public class P526 {
    static int[] FILL_VISITS = { 0 };
    static double CA = 0.15;
    static double CW = 600_000.0;
    static double FS = 1.0;
    static boolean NOFEAT = false;
    static double RM = 0, RG = 800;
    static double THR = 0;
    static java.util.List<double[]> ISLES = new java.util.ArrayList<>();
    static int OCT = 1;
    static int WOC = 2;

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P526] " + s); rep.flush(); System.out.println("[P526] " + s); System.out.flush(); }

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
        rep = new PrintStream(new File(dir, "P526_floodfill.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        S("=== P526 选项3：洼地填平（填掉所有内海）+ 成本实测 ===");
        offAll();
        double target = coarseProd();
        S(String.format(LF, "N-5 目标 = 生产默认陆地占比 %.4f%%", 100 * target));
        S("");
        WOC = 2; PlateField.WARP_W = 1_800_000.0;
        CA = 0.0; CW = 600_000.0; RM = 2500.0; RG = 1200.0;
        S("##### A11 ROUGH_M=2500 ROUGH_G=1200（形状冠军：D 1.106, N-1 129）#####");
        run("flood", 6_000_000, target, false, true);
        offAll();
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static void offAll() {
        PlateField.A1_CONTINUOUS_CONT = false; PlateField.A2_DOMAIN_WARP = false; PlateField.A3_GATED_RELIEF = false;
        PlateField.A4_RIFT_GUARD = false; PlateField.A5_NOISE_FROM_SKEL = false; PlateField.A6_NO_OCEAN_ARC = false;
        PlateField.L2_NOISE_SIGN_SAFE = false; PlateField.SKEL_MARGIN = 0.90;
        PlateField.A7_SITE_SHELL_FROM_LIVE = false;
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06; PlateField.CONT_WAV = 8_000_000.0; PlateField.WARP_OCT = 2;
        PlateField.COAST_AMP = 0.0; PlateField.COAST_W = 400_000.0; PlateField.COAST_OCT = 2;
    }

    static void run(String tag, double wav, double target, boolean detail, boolean a7) throws Exception {
        offAll();
        PlateField.A1_CONTINUOUS_CONT = true; PlateField.A2_DOMAIN_WARP = true; PlateField.A3_GATED_RELIEF = true;
        PlateField.A4_RIFT_GUARD = true; PlateField.A6_NO_OCEAN_ARC = true; PlateField.L2_NOISE_SIGN_SAFE = true;
        PlateField.CONT_WAV = wav; PlateField.A7_SITE_SHELL_FROM_LIVE = a7; PlateField.CONT_OCT = OCT; PlateField.WARP_OCT = WOC;
        // ⚠ E100：必须在 offAll() 【之后】设 —— 否则被 offAll() 复位。
        PlateField.COAST_AMP = CA; PlateField.COAST_W = CW;
        PlateField.ARC_H = 2600.0 * FS; PlateField.COLLIDE_H = 5600.0 * FS; PlateField.A10_NO_FEAT = NOFEAT;
        PlateField.A11_COAST_ONLY_ROUGH = (RM > 0); PlateField.ROUGH_M = RM; PlateField.ROUGH_G = RG;
        S("########## " + tag + "  CONT_WAV = " + (int) (wav / 1000) + " km  A7 = " + a7 + " ##########");
        double thr = tune(target); THR = thr;
        byte[] land = new byte[W * W];
        long nl = 0; double wsum = 0, lw = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
            for (int cc = 0; cc < W; cc++) {
                boolean l = PlateField.isLandFullWithArc(xOf(cc), z, seed, PlateField.PLATE_CELL, thr);
                land[r * W + cc] = (byte) (l ? 1 : 0); wsum += cw; if (l) { nl++; lw += cw; }
            } }
        S(String.format(LF, "  thr=%.5f  陆地 均匀 %.2f%%  面积加权 %.2f%%", thr, 100.0 * nl / (W * W), 100.0 * lw / wsum));
        double[] v = new double[W * W];
        for (int i = 0; i < W * W; i++) v[i] = land[i];
        MapWriter.writePng(new File(MAPDIR, "sealandE_" + tag + ".png"), W, W, v, 0.0, 1.0, SEALAND);

        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        ISLES.clear();
        int[] K = counts(land, lab, stk, detail);
        S(String.format(LF, "  N-1 内海 = %d   N-2 非最大陆块 = %d（点状 %d）   N-6 >=2%%陆块 = %d", K[0], K[3], K[4], K[5]));
        if (detail) dumpIsles();
        // ================= 选项 3：洼地填平 =================  
        S("  --- 选项 3：填掉所有【不连通开放大洋】的海（一次 BFS） ---");
        long t0 = System.nanoTime();
        int[] vi = { 0 };
        byte[] f = fillEnclosed(land, vi);
        long tFull = System.nanoTime() - t0;
        long changed = 0; for (int i2 = 0; i2 < W * W; i2++) if (f[i2] != land[i2]) changed++;
        S(String.format(LF, "  全图 BFS：改动 %d 格（%.3f%% 图幅），访问 %d 次，耗时 %.0f ms  ⇒ %.1f ns/格",
            changed, 100.0 * changed / (W * W), vi[0], tFull / 1e6, tFull / (double) (W * W)));
        ISLES.clear();
        int[] K2 = counts(f, lab, stk, false);
        S(String.format(LF, "  填平后：N-1 = %d   N-2 = %d   >=2%%陆块 = %d", K2[0], K2[3], K2[5]));
        List<Double> e2 = new ArrayList<>();
        byte[] sm2 = majority(f, MB);
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c2 = 0; c2 < W; c2++) {
                if (sm2[r * W + c2] == 0) continue;
                if (sm2[r * W + wr(c2 + 1)] == 1 && sm2[r * W + wr(c2 - 1)] == 1 && sm2[wr(r + 1) * W + c2] == 1 && sm2[wr(r - 1) * W + c2] == 1) continue;
                e2.add(PlateField.edgeDistance(xOf(c2), z, seed) / 1000.0);
            } }
        java.util.Collections.sort(e2);
        S(String.format(LF, "  填平后 N-7 中位 = %.0f km", e2.isEmpty() ? 0 : e2.get(e2.size() / 2)));
        // 窗口化填平（游戏里唯一可行的形式）：窗口边长 200 / 400 / 800 px = 1000 / 2000 / 4000 km
        for (int ws : new int[]{ 200, 400, 800 }) {
            int[] vi2 = { 0 };
            long t1 = System.nanoTime();
            byte[] g = fillWindowed(land, ws, vi2);
            long tw = System.nanoTime() - t1;
            long ch2 = 0; for (int i2 = 0; i2 < W * W; i2++) if (g[i2] != land[i2]) ch2++;
            ISLES.clear();
            int[] K3 = counts(g, lab, stk, false);
            S(String.format(LF, "  窗口 %d px (%.0f km)：改动 %d 格，残留 N-1 = %d，访问 %d 次，耗时 %.0f ms ⇒ 每列额外访问 %.2f 次",
                ws, ws * step / 1000.0, ch2, K3[0], vi2[0], tw / 1e6, vi2[0] / (double) (W * W)));
        }
        long ulN = 0, ulL = 0;
        for (int r = UL_R; r < W; r++) for (int cc = 0; cc < UL_C; cc++) { ulN++; if (land[r * W + cc] == 1) ulL++; }
        S(String.format(LF, "  N-3 左上角 陆地 %.2f%%", 100.0 * ulL / ulN));
        byte[] sm = majority(land, MB);
        List<Double> es = new ArrayList<>();
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int cc = 0; cc < W; cc++) {
                if (sm[r * W + cc] == 0) continue;
                if (sm[r * W + wr(cc + 1)] == 1 && sm[r * W + wr(cc - 1)] == 1 && sm[wr(r + 1) * W + cc] == 1 && sm[wr(r - 1) * W + cc] == 1) continue;
                es.add(PlateField.edgeDistance(xOf(cc), z, seed) / 1000.0);
            } }
        java.util.Collections.sort(es);
        S(String.format(LF, "  N-7 平滑海岸线 %d px  edgeDist 中位 %.0f km", es.size(), es.isEmpty() ? 0 : es.get(es.size() / 2)));
        // 【N-6 仪器修正】原定义数的是【原始像素】的 4 连通分量 —— 一条 1 px（5 km）宽的地峡就能把两块大陆焊成一块。
        // 这里改成在【405 km 多数滤波后】的掩膜上数，即「细颈窄于 405 km 的连接不算连接」。
        int[] labS = new int[W * W]; int nBigS = 0, nS = 0; long biggest = 0;
        for (int i = 0; i < W * W; i++) {
            if (sm[i] == 0 || labS[i] != 0) continue;
            int sp = 0; stk[sp++] = i; labS[i] = 1; long a = 0;
            while (sp > 0) { int p = stk[--sp]; a++; int r = p / W, c2 = p - r * W;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) if (sm[t] == 1 && labS[t] == 0) { labS[t] = 1; stk[sp++] = t; } }
            nS++; if (a > biggest) biggest = a; if (a > 0.02 * W * W) nBigS++;
        }
        S(String.format(LF, "  【N-6b】405 km 平滑后的陆块：总 %d 块，>=2%% 图幅 %d 块，最大 %.2f%% 图幅", nS, nBigS, 100.0 * biggest / (W * W)));
        // 【归因 A】原始海岸线的 edgeDistance 直方图 —— 低尾 = 被 feats（造山带）驱动出来的海岸/突刺
        int[] hb = new int[6]; long nbp = 0; double dsum = 0;
        byte[] bnd = new byte[W * W];
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c2 = 0; c2 < W; c2++) {
                if (land[r * W + c2] == 0) continue;
                if (land[r * W + wr(c2 + 1)] == 1 && land[r * W + wr(c2 - 1)] == 1 && land[wr(r + 1) * W + c2] == 1 && land[wr(r - 1) * W + c2] == 1) continue;
                bnd[r * W + c2] = 1; nbp++;
                double e = PlateField.edgeDistance(xOf(c2), z, seed) / 1000.0; dsum += e;
                int k2 = e < 25 ? 0 : e < 50 ? 1 : e < 100 ? 2 : e < 200 ? 3 : e < 400 ? 4 : 5; hb[k2]++;
            } }
        S(String.format(LF, "  【归因A】原始海岸线 %d px，edgeDist 直方图 <25:%d  25-50:%d  50-100:%d  100-200:%d  200-400:%d  >400:%d  （平均 %.0f km）",
            nbp, hb[0], hb[1], hb[2], hb[3], hb[4], hb[5], dsum / Math.max(1, nbp)));
        // 【归因 B】原始海岸线的盒计数分形维数 —— 越接近 1.0 越「规则/光滑」
        int[] boxes = { 5, 10, 20, 40, 80, 160 };
        double sx2 = 0, sy2 = 0, sxx2 = 0, sxy2 = 0;
        for (int bi = 0; bi < boxes.length; bi++) {
            int s2 = boxes[bi]; long cnt = 0; int g2 = W / s2;
            for (int br = 0; br < g2; br++) for (int bc = 0; bc < g2; bc++) {
                boolean hit = false;
                for (int rr = br * s2; rr < (br + 1) * s2 && !hit; rr++) for (int cc = bc * s2; cc < (bc + 1) * s2; cc++) if (bnd[rr * W + cc] == 1) { hit = true; break; }
                if (hit) cnt++; }
            double X = Math.log(1.0 / s2), Y = Math.log(Math.max(1, cnt));
            sx2 += X; sy2 += Y; sxx2 += X * X; sxy2 += X * Y;
        }
        int nb2 = boxes.length;
        S(String.format(LF, "  【归因B】原始海岸线分形维数 D = %.3f  （1.0=光滑/规则；地球海岸 1.2~1.4）", (nb2 * sxy2 - sx2 * sy2) / (nb2 * sxx2 - sx2 * sx2)));
        p243(land);
        S("");
    }

    static int[] counts(byte[] land, int[] lab, int[] stk, boolean detail) {
        int nS = 0, sDot = 0, sLin = 0, nL = 0, lDot = 0, nBig = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
            double sx = 0, sz = 0;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; a++;
                sx += c2 * step; sz += r * step;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
            nL++; if (a > 0.02 * W * W) nBig++;
            else ISLES.add(new double[]{ a, 2 * Math.sqrt(a * step * step / Math.PI) / 1000.0, sx / a, sz / a });
            if (a < 100) lDot++;
        }
        java.util.Arrays.fill(lab, 0);
        List<double[]> seas = new ArrayList<>();
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 2; long a = 0; boolean lr = false;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c2 = p - r * W; a++; if (c2 == 0 || c2 == W - 1) lr = true;
                double dx = c2 * step, dz = r * step; sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) if (land[t] == 0 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
            if (lr) continue;
            nS++;
            double[] m = shape(a, sx, sz, sxx, szz, sxz);
            double[] q = { a, m[0], m[1], sx / a, sz / a };
            seas.add(q);
            if (m[0] < 30) sDot++; else if (m[1] >= 3.0) sLin++;
        }
        if (detail && !seas.isEmpty()) {
            seas.sort((p, q) -> Double.compare(q[0], p[0]));
            S("  【残余内海明细】（按面积降序，面积 px / 等效直径 km / 长宽比 / 中心 lat / 中心 x km / edgeDist km）");
            for (int k = 0; k < Math.min(10, seas.size()); k++) {
                double[] d = seas.get(k);
                int cx = (int) d[3], cz = (int) d[4];
                S(String.format(LF, "    #%d  %8.0f px  %8.1f km  比值 %5.2f  lat %+7.2f  x %8.0f km  edgeDist %5.0f km",
                    k + 1, d[0], d[1], d[2], Math.toDegrees(WorldContract.latOf(cz)), d[3] / 1000.0,
                    PlateField.edgeDistance(cx, cz, seed) / 1000.0));
            }
        }
        java.util.Arrays.fill(lab, 0);
        return new int[]{ nS, sDot, sLin, nL, lDot, nBig };
    }

    /** skel 代理：elevationFull 在 +-125 km 窗口上的均值（L2 下噪声在 skel~0 处被压扁 ⇒ 均值 ~ skel）。 */
    static double skelProxy(int x, int z) {
        double s = 0; int n = 0;
        for (int dz = -125_000; dz <= 125_000; dz += 125_000)
            for (int dx = -125_000; dx <= 125_000; dx += 125_000) { s += PlateField.elevationFull(x + dx, z + dz, seed, PlateField.PLATE_CELL, THR); n++; }
        return s / n;
    }

    static void dumpIsles() {
        if (ISLES.isEmpty()) { S("  (无孤岛)"); return; }
        ISLES.sort((p, q) -> Double.compare(q[0], p[0]));
        S("  【孤岛解剖】面积px / 等效直径km / lat / edgeDist km / skel代理 m");
        for (int k = 0; k < Math.min(12, ISLES.size()); k++) {
            double[] d = ISLES.get(k);
            int cx = (int) d[2], cz = (int) d[3];
            S(String.format(LF, "    #%d %7.0f px %7.1f km  lat %+7.2f  edgeDist %5.0f km  skel %+7.0f m",
                k + 1, d[0], d[1], Math.toDegrees(WorldContract.latOf(cz)),
                PlateField.edgeDistance(cx, cz, seed) / 1000.0, skelProxy(cx, cz)));
        }
    }

    /** 从图幅边界 BFS 出海：填掉所有【不与开放大洋连通】的海（= 洼地填平）。 */
    static byte[] fillEnclosed(byte[] land, int[] visits) {
        byte[] out = land.clone();
        byte[] seen = new byte[W * W];
        int[] q = new int[W * W];
        int head = 0, tail = 0;
        // ⚠ E103：开放大洋的定义必须与 counts() 一致 —— **只有左右两列是边界**；
        //   z 是周期轴，上下两行是相邻的，把它们当边界会让「与上下边连通的内海」被误判为开放。
        for (int r = 0; r < W; r++) { int i2 = r * W; if (out[i2] == 0 && seen[i2] == 0) { seen[i2] = 1; q[tail++] = i2; }
            int i3 = r * W + W - 1; if (out[i3] == 0 && seen[i3] == 0) { seen[i3] = 1; q[tail++] = i3; } }
        while (head < tail) {
            int p = q[head++]; visits[0]++;
            int r = p / W, c2 = p - r * W;
            int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
            for (int t : nn) if (out[t] == 0 && seen[t] == 0) { seen[t] = 1; q[tail++] = t; }
        }
        for (int i2 = 0; i2 < W * W; i2++) if (out[i2] == 0 && seen[i2] == 0) out[i2] = 1;   // 填平成陆
        return out;
    }

    /** 窗口化填平：每个 ws x ws 窗口内部独立做一次 BFS（窗口边界视为"开放"）。 */
    static byte[] fillWindowed(byte[] land, int ws, int[] visits) {
        byte[] out = land.clone();
        byte[] seen = new byte[W * W];
        int[] q = new int[ws * ws];
        for (int r0 = 0; r0 < W; r0 += ws) for (int c0 = 0; c0 < W; c0 += ws) {
            int head = 0, tail = 0;
            for (int r = r0; r < r0 + ws; r++) for (int c2 = c0; c2 < c0 + ws; c2++) {
                boolean edge = (c2 == c0 || c2 == c0 + ws - 1);   // ⚠ E103：z 方向是周期轴，窗口上下不是边界
                int i2 = r * W + c2;
                if (edge && out[i2] == 0 && seen[i2] == 0) { seen[i2] = 1; q[tail++] = i2; }
            }
            while (head < tail) {
                int p = q[head++]; visits[0]++;
                int r = p / W, c2 = p - r * W;
                int[] nn = { r * W + wr(c2 + 1), r * W + wr(c2 - 1), wr(r + 1) * W + c2, wr(r - 1) * W + c2 };
                for (int t : nn) { int tr = t / W, tc = t - tr * W;
                    if (tr < r0 || tr >= r0 + ws || tc < c0 || tc >= c0 + ws) continue;
                    if (out[t] == 0 && seen[t] == 0) { seen[t] = 1; q[tail++] = t; } }
            }
            for (int r = r0; r < r0 + ws; r++) for (int c2 = c0; c2 < c0 + ws; c2++) { int i2 = r * W + c2; if (out[i2] == 0 && seen[i2] == 0) out[i2] = 1; }
            for (int r = r0; r < r0 + ws; r++) for (int c2 = c0; c2 < c0 + ws; c2++) seen[r * W + c2] = 0;
        }
        return out;
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
        for (int r = 0; r < G; r++) for (int c2 = 0; c2 < G; c2++) {
            int x = (int) Math.round(X0 + (c2 + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (PlateField.isLandWithCell(x, z, seed, PlateField.PLATE_CELL)) n++;
        }
        return n / (double) (G * G);
    }
    static double coarse(double thr) {
        int G = 400; long n = 0;
        for (int r = 0; r < G; r++) for (int c2 = 0; c2 < G; c2++) {
            int x = (int) Math.round(X0 + (c2 + 0.5) * SPAN / G), z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
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
            for (int c2 = 0; c2 < m; c2++) {
                int best = -1, bestOv = 0;
                for (int p = 0; p < pm; p++) { int lo = Math.max(xw[c2], pxw[p]), hi = Math.min(xe[c2], pxe[p]); if (hi - lo > bestOv) { bestOv = hi - lo; best = p; } }
                if (best >= 0 && bestOv > 0) {
                    double dd = Math.abs(xw[c2] - pxw[best]) * step;
                    coast[c2] = dd <= WALL_TOL ? pCoast[best] + (int) step : (int) step;
                    area[c2] = pArea[best] + (long) wt[c2] * (long) step; matched[best] = true;
                } else { coast[c2] = (int) step; area[c2] = (long) wt[c2] * (long) step; }
            }
            if (pm > 0) for (int p = 0; p < pm; p++) if (!matched[p]) { wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
            System.arraycopy(xw, 0, pxw, 0, m); System.arraycopy(xe, 0, pxe, 0, m);
            System.arraycopy(coast, 0, pCoast, 0, m); System.arraycopy(area, 0, pArea, 0, m); pm = m;
        }
        for (int p = 0; p < pm; p++) { wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
        S(String.format(LF, "  N-4 G1b = %.4f    G2 = %.4f", wTot > 0 ? wLong / (double) wTot : 0, seaTot > 0 ? seaInBasin / (double) seaTot : 0));
    }

    static byte[] majority(byte[] m, int K) {
        int half = K / 2;
        int[] t1 = new int[W * W], t2 = new int[W * W];
        for (int r = 0; r < W; r++) { int base = r * W, s = 0;
            for (int k = -half; k <= half; k++) s += m[base + wr(k)];
            t1[base] = s;
            for (int c2 = 1; c2 < W; c2++) { s += m[base + wr(c2 + half)] - m[base + wr(c2 - half - 1)]; t1[base + c2] = s; } }
        for (int c2 = 0; c2 < W; c2++) { int s = 0;
            for (int k = -half; k <= half; k++) s += t1[wr(k) * W + c2];
            t2[c2] = s;
            for (int r = 1; r < W; r++) { s += t1[wr(r + half) * W + c2] - t1[wr(r - half - 1) * W + c2]; t2[r * W + c2] = s; } }
        byte[] out = new byte[W * W]; int thr = K * K / 2;
        for (int i = 0; i < W * W; i++) out[i] = (byte) (t2[i] > thr ? 1 : 0);
        return out;
    }
}
