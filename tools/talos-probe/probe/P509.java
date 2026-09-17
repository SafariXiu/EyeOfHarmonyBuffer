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
 * P509：把用户看出来的三类缺陷**归因**（路线 A 第二轮）。
 *
 *   用户原话：「大陆内部会出现类似河流结构的海洋状态」/「零星的点状海洋」/
 *             「海洋上会有…一个细长条这种」的大陆。
 *
 * 仪器：对 A1A2A3 配置（含岛弧，走 isLandFullWithArc —— 与生产同一条链）出图，
 * 然后把【内海分量】与【非最大陆块】按 (等效直径, 伸长率, 到板块边界的距离) 分类。
 *
 * 判读逻辑（可证伪）：
 *   若「河流状」内海的 edgeDistance 中位 << 50 km ⇒ 它们坐在板块边界上 ⇒ 凶手是 feat（RIFT_D 裂谷槽）；
 *   若「点状」内海的 edgeDistance 中位很大 ⇒ 它们不在边界上 ⇒ 凶手是陆架带上的噪声。
 *   若海洋上的细长条陆块 edgeDistance 中位 << 50 km ⇒ 凶手是洋中脊（RIDGE_H）。
 */
public class P509 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P509] " + s); rep.flush(); System.out.println("[P509] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static double THR = -0.12274;                 // P508 给 A1A2A3 标定出来的阈值
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    static final MapWriter.ColorMap SEALAND = new MapWriter.ColorMap() {
        @Override public int rgb(double t) { return t < 0.5 ? 0x1B3B6F : 0xE0D5B0; }
    };

    public static void main(String[] args) throws Exception {
        if (args.length > 0) THR = Double.parseDouble(args[0]);
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P509_routeA2.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        PlateField.A1_CONTINUOUS_CONT = true; PlateField.A2_DOMAIN_WARP = true; PlateField.A3_GATED_RELIEF = true;
        S("=== P509 路线 A 三类缺陷归因（A1=A2=A3=true, thr=" + THR + "）===");
        S(String.format(LF, "口径：%d px @ %.2f km/px，窗 %.0f km；**走 isLandFullWithArc ⇒ 与生产同一条链（含超宽大洋岛弧）**",
            W, step / 1000, SPAN / 1000.0));

        byte[] land = new byte[W * W];
        long nl = 0; double wsum = 0, lw = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
            for (int c = 0; c < W; c++) {
                boolean l = PlateField.isLandFullWithArc(xOf(c), z, seed, PlateField.PLATE_CELL, THR);
                land[r * W + c] = (byte) (l ? 1 : 0);
                wsum += cw; if (l) { nl++; lw += cw; }
            } }
        S(String.format(LF, "陆地占比：均匀 %.4f%%  面积加权 %.4f%%", 100.0 * nl / (W * W), 100.0 * lw / wsum));
        double[] v = new double[W * W];
        for (int i = 0; i < W * W; i++) v[i] = land[i];
        File png = new File(MAPDIR, "sealandA_A1A2A3_arc.png");
        MapWriter.writePng(png, W, W, v, 0.0, 1.0, SEALAND);
        S("PNG（含岛弧，与生产同链）= " + png.getAbsolutePath());
        S("");

        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        // ---- 海分量（内海）----
        List<double[]> seas = new ArrayList<>();
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0; boolean lr = false;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
            while (sp > 0) {
                int p = stk[--sp]; int r = p / W, c = p - r * W;
                a++; if (c == 0 || c == W - 1) lr = true;
                double dx = c * step, dz = r * step;
                sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 0 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; }
            }
            if (lr) continue;
            seas.add(moments(a, sx, sz, sxx, szz, sxz));
        }
        // ---- 陆块（非最大）----
        List<double[]> isles = new ArrayList<>();
        long biggest = 0;
        for (int i = 0; i < W * W; i++) { if (land[i] == 0 || lab[i] != 0) continue; int sp = 0; stk[sp++] = i; lab[i] = 2; long a = 0;
            while (sp > 0) { int p = stk[--sp]; a++; int r = p / W, c = p - r * W;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
            if (a > biggest) biggest = a; }
        java.util.Arrays.fill(lab, 0);
        long nIsle = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 3; long a = 0;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0; int rep0 = -1;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W;
                a++; if (rep0 < 0) rep0 = p;
                double dx = c * step, dz = r * step;
                sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 3; stk[sp++] = t; } }
            if (a == biggest) continue;
            nIsle++;
            isles.add(moments(a, sx, sz, sxx, szz, sxz));
        }
        S(String.format(LF, "内海（不碰左右边界的海分量）= %d 块；非最大陆块 = %d 块（最大陆块 %d px = %.2f%%）",
            seas.size(), nIsle, biggest, 100.0 * biggest / (W * W)));
        S("");

        classify("内海", seas);
        classify("海洋陆块", isles);

        // ---- A-9 修正：在【原始】海岸线上做盒计数 ----
        byte[] bnd = new byte[W * W];
        long nb = 0; double esum = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c = 0; c < W; c++) {
                if (land[r * W + c] == 0) continue;
                if (land[r * W + wr(c + 1)] == 1 && land[r * W + wr(c - 1)] == 1 && land[wr(r + 1) * W + c] == 1 && land[wr(r - 1) * W + c] == 1) continue;
                bnd[r * W + c] = 1; nb++; esum += PlateField.edgeDistance(xOf(c), z, seed) / 1000.0;
            } }
        S(String.format(LF, "原始海岸线 %d px（平均 edgeDistance %.0f km）", nb, esum / Math.max(1, nb)));
        S(String.format(LF, "A-9 修正：原始海岸线分形维数 D = %.3f   （P508 的 0.983 是在 405 km 平滑后量的 ⇒ 必然约 1.0，作废）", boxD(bnd)));
        byte[] sm9 = majority(land, 9, W);
        byte[] b9 = new byte[W * W];
        for (int r = 0; r < W; r++) for (int c = 0; c < W; c++) {
            if (sm9[r * W + c] == 0) continue;
            if (sm9[r * W + wr(c + 1)] == 1 && sm9[r * W + wr(c - 1)] == 1 && sm9[wr(r + 1) * W + c] == 1 && sm9[wr(r - 1) * W + c] == 1) continue;
            b9[r * W + c] = 1; }
        S(String.format(LF, "        45 km 轻度平滑后 D = %.3f（作参照）", boxD(b9)));
        rep.flush(); rep.close();
        PlateField.A1_CONTINUOUS_CONT = false; PlateField.A2_DOMAIN_WARP = false; PlateField.A3_GATED_RELIEF = false;
        System.out.println("JAVA_EXIT=0");
    }

    static double[] moments(long a, double sx, double sz, double sxx, double szz, double sxz) {
        double mx = sx / a, mz = sz / a;
        double cxx = sxx / a - mx * mx, czz = szz / a - mz * mz, cxz = sxz / a - mx * mz;
        double tr = cxx + czz, det = cxx * czz - cxz * cxz;
        double disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
        double l1 = tr / 2 + disc, l2 = Math.max(1e-9, tr / 2 - disc);
        return new double[]{ a, 2 * Math.sqrt(a * step * step / Math.PI) / 1000.0, Math.sqrt(l1 / l2), mx / 1000.0, mz / 1000.0 };
    }

    static void classify(String name, List<double[]> list) {
        S("--- " + name + " 分类 ---");
        if (list.isEmpty()) { S("  （空）"); return; }
        List<double[]> dot = new ArrayList<>(), riv = new ArrayList<>(), oth = new ArrayList<>();
        for (double[] d : list) {
            if (d[1] < 30) dot.add(d); else if (d[2] >= 3.0) riv.add(d); else oth.add(d);
        }
        bucket("点状 (等效直径<30km)", dot);
        bucket("河流状 (直径>=30km 且 长宽比>=3)", riv);
        bucket("其它", oth);
        // 与随机点的 edgeDistance 对照
        double s = 0; int n = 0;
        for (int k = 0; k < 3000; k++) {
            int c = hashPick(k * 2654435761L + 1, W), r = hashPick(k * 40503L + 7, W);
            s += PlateField.edgeDistance(xOf(c), zOf(r), seed) / 1000.0; n++;
        }
        S(String.format(LF, "  【对照】全图随机点 edgeDistance 平均 = %.0f km", s / n));
        S("");
    }

    static void bucket(String nm, List<double[]> l) {
        if (l.isEmpty()) { S(String.format(LF, "  %-32s 0 块", nm)); return; }
        double[] diam = new double[l.size()], el = new double[l.size()], ed = new double[l.size()], len = new double[l.size()];
        double sum = 0;
        for (int i = 0; i < l.size(); i++) { diam[i] = l.get(i)[1]; el[i] = l.get(i)[2]; len[i] = l.get(i)[1] * l.get(i)[2]; sum += l.get(i)[0]; }
        // 逐块算到板块边界的平均距离（用中心点近似 + 形状因子）
        for (int i = 0; i < l.size(); i++) {
            double[] d = l.get(i);
            ed[i] = PlateField.edgeDistance((int) (d[3] * 1000), (int) (d[4] * 1000), seed) / 1000.0;
        }
        java.util.Arrays.sort(diam); java.util.Arrays.sort(el); java.util.Arrays.sort(ed); java.util.Arrays.sort(len);
        S(String.format(LF, "  %-32s %5d 块  面积占比 %5.2f%%  等效直径 中位 %6.1f km  P90 %6.1f  长宽比 中位 %5.2f  P90 %5.2f  中心 edgeDist 中位 %6.0f km",
            nm, l.size(), 100.0 * sum / (W * (double) W), diam[diam.length / 2], diam[(int) (diam.length * 0.9)],
            el[el.length / 2], el[(int) (el.length * 0.9)], ed[ed.length / 2]));
    }

    static double boxD(byte[] bnd) {
        int[] boxes = { 5, 10, 20, 40, 80, 160 };
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int bi = 0; bi < boxes.length; bi++) {
            int s = boxes[bi]; long cnt = 0; int g = W / s;
            for (int br = 0; br < g; br++) for (int bc = 0; bc < g; bc++) {
                boolean hit = false;
                for (int rr = br * s; rr < (br + 1) * s && !hit; rr++) for (int cc = bc * s; cc < (bc + 1) * s; cc++) if (bnd[rr * W + cc] == 1) { hit = true; break; }
                if (hit) cnt++;
            }
            double X = Math.log(1.0 / s), Y = Math.log(cnt);
            sx += X; sy += Y; sxx += X * X; sxy += X * Y;
        }
        int n = boxes.length;
        return (n * sxy - sx * sy) / (n * sxx - sx * sx);
    }

    static byte[] majority(byte[] m, int K, int w) {
        int half = K / 2;
        int[] t1 = new int[w * w], t2 = new int[w * w];
        for (int r = 0; r < w; r++) { int base = r * w, s = 0;
            for (int k = -half; k <= half; k++) s += m[base + wr(k)];
            t1[base] = s;
            for (int c = 1; c < w; c++) { s += m[base + wr(c + half)] - m[base + wr(c - half - 1)]; t1[base + c] = s; } }
        for (int c = 0; c < w; c++) { int s = 0;
            for (int k = -half; k <= half; k++) s += t1[wr(k) * w + c];
            t2[c] = s;
            for (int r = 1; r < w; r++) { s += t1[wr(r + half) * w + c] - t1[wr(r - half - 1) * w + c]; t2[r * w + c] = s; } }
        byte[] out = new byte[w * w]; int thr = K * K / 2;
        for (int i = 0; i < w * w; i++) out[i] = (byte) (t2[i] > thr ? 1 : 0);
        return out;
    }

    static int hashPick(long h, int n) {
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L; h ^= (h >>> 33);
        return (int) (((h >>> 1) % n + n) % n);
    }
}
