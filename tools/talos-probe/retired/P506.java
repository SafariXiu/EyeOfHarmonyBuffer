package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * P506: 为什么海陆图看起来像"多边形拼在一起 + 大陆内部一堆碎片化海洋"？
 *
 * <p>只读 PlateField，不改任何生产代码。四项度量：
 *  A) 陆地连通分量（4 邻接，行方向按 Z 周期 wrap）⇒ 有几块大陆、多大、多长。
 *  B) 被陆地完全包围的海域（不碰左右边界的海分量）⇒ 内海数量 / 大小 / 长宽比 / 离板块边界的距离。
 *  C) 海岸线上的 edgeDistance 直方图 ⇒ **"海岸线就是板块边界"这个假设的直接检验**。
 *  D) 内区（edgeDistance > 500 km）的 elevationWithCell 分布 ⇒ 检验
 *     "ELEV_CONT=620 m 顶不住 n1 的 700 m 振幅"这个解析预测。
 */
public class P506 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P506] " + s); rep.flush(); System.out.println("[P506] " + s); System.out.flush(); }

    static int W = 4000;          // 5000 block/px
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D;
    static long SPAN = WorldContract.Z_CYCLE;
    static double step;
    static byte[] land;
    static long seed;

    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) W = Integer.parseInt(args[0]);
        step = SPAN / (double) W;
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P506_sealand_defects.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);

        say("=== P506 海陆图缺陷解剖 ===");
        say(String.format(LF, "PLATE_CELL = %d block = %.0f km ；本图幅 %.0f km 宽 ⇒ 横向只有 %.2f 个板块格",
            PlateField.PLATE_CELL, PlateField.PLATE_CELL / 1000.0, SPAN / 1000.0, (double) SPAN / PlateField.PLATE_CELL));
        say(String.format(LF, "图幅 %d x %d px，%.1f 格/px (%.2f km/px)；x0=%d z0=%d span=%d", W, W, step, step / 1000.0, X0, Z0, SPAN));
        say("");

        land = new byte[W * W];
        long t0 = System.nanoTime();
        long nLand = 0;
        for (int r = 0; r < W; r++) {
            int z = zOf(r);
            for (int c = 0; c < W; c++) {
                boolean l = PlateField.isLandWithCell(xOf(c), z, seed, PlateField.PLATE_CELL);
                land[r * W + c] = (byte) (l ? 1 : 0);
                if (l) nLand++;
            }
        }
        say(String.format(LF, "采样 %.1f s；陆地 %d/%d = %.2f%%", (System.nanoTime() - t0) / 1e9, nLand, W * W, 100.0 * nLand / (W * W)));
        say("");

        // ---------- A) 陆地连通分量 ----------
        say("--- A) 陆地连通分量（4 邻接，Z 方向周期 wrap）---");
        int[] lab = new int[W * W];
        int[] stk = new int[W * W];
        List<long[]> comps = new ArrayList<>();   // area,minR,maxR,minC,maxC,sumX,sumZ
        int nComp = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            nComp++;
            int sp = 0; stk[sp++] = i; lab[i] = nComp;
            long area = 0; int mnR = W, mxR = -1, mnC = W, mxC = -1;
            while (sp > 0) {
                int p = stk[--sp];
                int r = p / W, c = p - r * W;
                area++;
                if (r < mnR) mnR = r; if (r > mxR) mxR = r;
                if (c < mnC) mnC = c; if (c > mxC) mxC = c;
                int rup = r + 1 == W ? 0 : r + 1, rdn = r - 1 < 0 ? W - 1 : r - 1;
                int[] cand = { r * W + ((c + 1) % W), r * W + ((c + W - 1) % W), rup * W + c, rdn * W + c };
                for (int q : cand) if (land[q] == 1 && lab[q] == 0) { lab[q] = nComp; stk[sp++] = q; }
            }
            comps.add(new long[]{ area, mnR, mxR, mnC, mxC });
        }
        comps.sort((p, q) -> Long.compare(q[0], p[0]));
        say(String.format(LF, "陆地分量总数 = %d", nComp));
        say(String.format(LF, "  %-5s %10s %9s %9s %9s %9s %9s", "rank", "面积px", "%全球", "宽km", "高km", "minLat", "maxLat"));
        for (int k = 0; k < Math.min(12, comps.size()); k++) {
            long[] a = comps.get(k);
            double latMin = Math.toDegrees(WorldContract.latOf(zOf((int) a[1])));
            double latMax = Math.toDegrees(WorldContract.latOf(zOf((int) a[2])));
            say(String.format(LF, "  %-5d %10d %8.2f%% %9.0f %9.0f %9.2f %9.2f", k + 1, a[0], 100.0 * a[0] / (W * W),
                a[4] * step / 1000.0, a[3] * 0 + (a[2] - a[1]) * step / 1000.0, latMin, latMax));
        }
        int big = 0; for (long[] a : comps) if (a[0] > 0.02 * W * W) big++;
        say(String.format(LF, "  ≥2%% 图幅的大陆 = %d 块；<0.1%% 的小碎块 = %d 块", big,
            comps.stream().filter(a -> a[0] < 0.001 * W * W).count()));
        say("");

        // ---------- B) 内海 ----------
        say("--- B) 被陆地包围的海域（海分量，且不碰左右边界；上下按 Z 周期 wrap）---");
        int[] slab = new int[W * W];
        int ns = 0;
        List<double[]> seas = new ArrayList<>();  // area, eqDiamKm, elong, meanEdgeKm, minLat, maxLat
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || slab[i] != 0) continue;
            ns++;
            int sp = 0; stk[sp++] = i; slab[i] = ns;
            long area = 0; boolean touchLR = false;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0, sedge = 0;
            while (sp > 0) {
                int p = stk[--sp];
                int r = p / W, c = p - r * W;
                area++;
                if (c == 0 || c == W - 1) touchLR = true;
                double dx = (xOf(c) - X0) / 1000.0, dz = (zOf(r) - Z0) / 1000.0;
                sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int rup = r + 1 == W ? 0 : r + 1, rdn = r - 1 < 0 ? W - 1 : r - 1;
                int[] cand = { r * W + ((c + 1) % W), r * W + ((c + W - 1) % W), rup * W + c, rdn * W + c };
                for (int q : cand) if (land[q] == 0 && slab[q] == 0) { slab[q] = ns; stk[sp++] = q; }
            }
            if (touchLR) continue;
            double mx = sx / area, mz = sz / area;
            double cxx = sxx / area - mx * mx, czz = szz / area - mz * mz, cxz = sxz / area - mx * mz;
            double tr = cxx + czz, det = cxx * czz - cxz * cxz;
            double disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
            double l1 = tr / 2 + disc, l2 = Math.max(1e-9, tr / 2 - disc);
            double eqD = 2 * Math.sqrt(area * step * step / Math.PI) / 1000.0;
            seas.add(new double[]{ area, eqD, Math.sqrt(l1 / l2), 0, mx, mz });
        }
        seas.sort((p, q) -> Double.compare(q[0], p[0]));
        say(String.format(LF, "海分量总数 = %d；其中**不碰左右边界的内海** = %d", ns, seas.size()));
        say(String.format(LF, "  %-5s %10s %9s %9s %9s %10s", "rank", "面积px", "等效直径km", "长宽比", "中心x km", "中心z km"));
        for (int k = 0; k < Math.min(12, seas.size()); k++) {
            double[] a = seas.get(k);
            say(String.format(LF, "  %-5d %10.0f %11.1f %11.2f %10.0f %10.0f", k + 1, a[0], a[1], a[2], a[4], a[5]));
        }
        double[] eqd = seas.stream().mapToDouble(a -> a[1]).toArray();
        java.util.Arrays.sort(eqd);
        if (eqd.length > 0) {
            say(String.format(LF, "  内海等效直径: 中位 %.1f km，10%%分位 %.1f，90%%分位 %.1f，最大 %.1f",
                eqd[eqd.length / 2], eqd[eqd.length / 10], eqd[eqd.length * 9 / 10], eqd[eqd.length - 1]));
        }
        say("");

        // ---------- C) 海岸线是不是板块边界 ----------
        say("--- C) 海岸线上的 edgeDistance（=0 就是板块边界）---");
        int[] hist = new int[12];
        long nb = 0; double sumEdge = 0;
        List<Double> edges = new ArrayList<>();
        for (int r = 0; r < W; r++) {
            int rup = r + 1 == W ? 0 : r + 1, rdn = r - 1 < 0 ? W - 1 : r - 1;
            int z = zOf(r);
            for (int c = 0; c < W; c++) {
                if (land[r * W + c] == 0) continue;
                boolean b = land[r * W + (c + 1) % W] == 0 || land[r * W + (c + W - 1) % W] == 0
                         || land[rup * W + c] == 0 || land[rdn * W + c] == 0;
                if (!b) continue;
                nb++;
                double e = PlateField.edgeDistance(xOf(c), z, seed) / 1000.0;
                sumEdge += e; edges.add(e);
                int k = (int) Math.min(11.0, e / 25.0);
                hist[k]++;
            }
        }
        say(String.format(LF, "海岸像素数 = %d；平均 edgeDistance = %.1f km", nb, sumEdge / Math.max(1, nb)));
        String[] names = { "0-25", "25-50", "50-75", "75-100", "100-125", "125-150", "150-175", "175-200", "200-225", "225-250", "250-275", ">=275" };
        long cum = 0;
        for (int k = 0; k < 12; k++) {
            cum += hist[k];
            say(String.format(LF, "  %-8s km %8d %7.2f%%  累计 %6.2f%%", names[k], hist[k], 100.0 * hist[k] / Math.max(1, nb), 100.0 * cum / Math.max(1, nb)));
        }
        java.util.Collections.sort(edges);
        if (!edges.isEmpty()) {
            say(String.format(LF, "  edgeDistance 分位: 10%%=%.0f  25%%=%.0f  50%%=%.0f  75%%=%.0f  90%%=%.0f km",
                edges.get(edges.size() / 10), edges.get(edges.size() / 4), edges.get(edges.size() / 2),
                edges.get(edges.size() * 3 / 4), edges.get(edges.size() * 9 / 10)));
        }
        say("");

        // ---------- D) 内区高程分布 ----------
        say("--- D) 内区采样（edgeDistance > 500 km）的 elevationWithCell 分布 ---");
        long ns2 = 0, below = 0; double sum = 0, sum2 = 0; double mn = 1e9, mx = -1e9;
        for (int k = 0; k < 40000; k++) {
            int c = (int) (Math.abs(PlateField.edgeDistance(xOf(0), zOf(0), seed)) * 0 + 1) * 0;
            int cc = (int) (Math.random() * 0) + 0;
            int c2 = hashPick(k * 2654435761L, W), r2 = hashPick(k * 40503L + 12345L, W);
            int x = xOf(c2), z = zOf(r2);
            if (PlateField.edgeDistance(x, z, seed) < 500_000) continue;
            double e = PlateField.elevationWithCell(x, z, seed, PlateField.PLATE_CELL);
            ns2++; sum += e; sum2 += e * e;
            if (e < 0) below++;
            if (e < mn) mn = e; if (e > mx) mx = e;
        }
        double mean = sum / Math.max(1, ns2), var = sum2 / Math.max(1, ns2) - mean * mean;
        say(String.format(LF, "  样本 %d；均值 %.1f m，标准差 %.1f m，min %.1f，max %.1f", ns2, mean, Math.sqrt(Math.max(0, var)), mn, mx));
        say(String.format(LF, "  **低于海平面的比例 = %d/%d = %.2f%%**  <== 大陆内区被噪声打到海面以下的比例", below, ns2, 100.0 * below / Math.max(1, ns2)));
        say(String.format(LF, "  解析预测：ELEV_CONT=620 m，n1(700m x5 oct)+n2(160m x3 oct) 的 sigma ≈ %.0f m ⇒ P(e<0) = %.1f%%",
            Math.sqrt(700 * 700 * (1 + 0.25 + 0.0625 + 0.015625 + 0.00390625) / 3 + 160 * 160 * (1 + 0.25 + 0.0625) / 3) * 0 + 478.0,
            100.0 * normCdf(-620.0 / 478.0)));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static int hashPick(long h, int n) {
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L; h ^= (h >>> 33);
        return (int) (((h >>> 1) % n + n) % n);
    }

    static double normCdf(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }
    static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }
}
