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
 * P507: P506 的两条判据被**证伪/失效**之后的重做。
 *
 * <p>P506 的教训（必须记账）：
 *  C) 我用「海岸线的 edgeDistance」检验"海岸线=板块边界"，实测中位 285 km ⇒ 看起来证伪。
 *     **但这个统计是按周长加权的，而图上有 10,110 个中位直径 11 km 的碎海 ⇒ 周长统计被碎屑完全支配。**
 *     ⇒ 它答不了"大尺度轮廓是不是多边形"这个问题。**必须先在 ~200 km 尺度上平滑再量。**
 *  D) 我用「edgeDistance > 500 km 的点」当"大陆内区"，实测 46.76% 低于海平面。
 *     **但那些点里大部分是深海内区（base = -4100 m）** ⇒ 这个数什么也没说明。
 *     ⇒ 必须**先按壳类型分类**再统计。
 *
 * <p>本探针只读 PlateField 公开 API，用「elevationWithCellRaw 的局部均值」当壳类型代理
 * （raw 的均值收敛到 base：陆壳 +620 m、洋壳 -4100 m，两者相差 4,720 m，不可能混）。
 */
public class P507 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P507] " + s); rep.flush(); System.out.println("[P507] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step;
    static long seed;
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wrap(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    /** 壳类型代理：raw 高程在局部窗口上的均值。> -1700 m ⇒ 陆壳。 */
    static double shellProxy(int x, int z, int rad) {
        double s = 0; int n = 0;
        for (int dz = -rad; dz <= rad; dz += rad)
            for (int dx = -rad; dx <= rad; dx += rad) { s += PlateField.elevationWithCellRaw(x + dx, z + dz, seed, PlateField.PLATE_CELL); n++; }
        return s / n;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) W = Integer.parseInt(args[0]);
        step = SPAN / (double) W;
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P507_sealand_defects2.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        say("=== P507 海陆图缺陷：平滑之后再看 ===");
        say(String.format(LF, "PLATE_CELL=%.0f km；图幅 %.0f km ⇒ 横向 %.2f 个板块格；%d px @ %.2f km/px",
            PlateField.PLATE_CELL / 1000.0, SPAN / 1000.0, (double) SPAN / PlateField.PLATE_CELL, W, step / 1000.0));

        byte[] land = new byte[W * W];
        long nL = 0;
        for (int r = 0; r < W; r++) {
            int z = zOf(r);
            for (int c = 0; c < W; c++) { boolean l = PlateField.isLandWithCell(xOf(c), z, seed, PlateField.PLATE_CELL); land[r * W + c] = (byte) (l ? 1 : 0); if (l) nL++; }
        }
        say(String.format(LF, "原始陆地 %.2f%%", 100.0 * nL / (W * W)));
        say("");

        // ---------- 0) 板块格尺度：陆壳格连成几块 ----------
        say("--- 0) 板块格尺度（250 km 网格 x 5x5 局部均值分类壳类型）---");
        int G = 80, gs = (int) (SPAN / G);
        byte[] cell = new byte[G * G];
        long cont = 0;
        for (int i = 0; i < G; i++) for (int j = 0; j < G; j++) {
            double v = shellProxy((int) (X0 + (j + 0.5) * gs), (int) (Z0 + (i + 0.5) * gs), 125_000);
            cell[i * G + j] = (byte) (v > -1700 ? 1 : 0);
            if (v > -1700) cont++;
        }
        say(String.format(LF, "  250 km 网格：陆壳格 %d/%d = %.1f%%", cont, G * G, 100.0 * cont / (G * G)));
        int[] cl = new int[G * G]; int nc = 0; int[] q = new int[G * G];
        for (int i = 0; i < G * G; i++) {
            if (cell[i] == 0 || cl[i] != 0) continue;
            nc++; int sp = 0; q[sp++] = i; cl[i] = nc; int area = 0;
            while (sp > 0) {
                int p = q[--sp]; area++;
                int r = p / G, c = p - r * G;
                int[] nn = { r * G + (c + 1 == G ? 0 : c + 1), r * G + (c == 0 ? G - 1 : c - 1),
                             (r + 1 == G ? 0 : r + 1) * G + c, (r == 0 ? G - 1 : r - 1) * G + c };
                for (int t : nn) if (cell[t] == 1 && cl[t] == 0) { cl[t] = nc; q[sp++] = t; }
            }
            if (area > 0) { }
        }
        say(String.format(LF, "  **板块格尺度的陆壳连通块数 = %d**  （地球约 6~7 块大陆）", nc));
        int[] sizes = new int[nc + 1];
        for (int i = 0; i < G * G; i++) if (cl[i] > 0) sizes[cl[i]]++;
        java.util.Arrays.sort(sizes);
        StringBuilder sb = new StringBuilder();
        for (int k = sizes.length - 1; k >= Math.max(1, sizes.length - 8); k--) sb.append(sizes[k]).append(" ");
        say("  各块面积（250km 格数，降序前 7）：" + sb);
        say("");

        // ---------- 1) 平滑之后：陆地/内海还剩几块 ----------
        for (int K : new int[]{41, 81, 161}) {
            say(String.format(LF, "--- 1) 多数滤波 %d px = %.0f km 之后 ---", K, K * step / 1000.0));
            byte[] sm = majority(land, K);
            long sl = 0; for (byte b : sm) if (b == 1) sl++;
            say(String.format(LF, "  陆地 %.2f%%", 100.0 * sl / (W * W)));
            int[] lab = new int[W * W]; int[] stk = new int[W * W];
            int nland = 0, nseaEnc = 0, nseaOpen = 0;
            List<Integer> landSizes = new ArrayList<>(), seaSizes = new ArrayList<>();
            for (int i = 0; i < W * W; i++) {
                if (sm[i] == 0 || lab[i] != 0) continue;
                nland++; int sp = 0; stk[sp++] = i; lab[i] = 1; int a = 0;
                while (sp > 0) { int p = stk[--sp]; a++; int r = p / W, c = p - r * W;
                    int[] nn = { r * W + wrap(c + 1), r * W + wrap(c - 1), wrap(r + 1) * W + c, wrap(r - 1) * W + c };
                    for (int t : nn) if (sm[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
                landSizes.add(a);
            }
            for (int i = 0; i < W * W; i++) {
                if (sm[i] == 1 || lab[i] != 0) continue;
                int sp = 0; stk[sp++] = i; lab[i] = 2; int a = 0; boolean lr = false;
                while (sp > 0) { int p = stk[--sp]; a++; int r = p / W, c = p - r * W; if (c == 0 || c == W - 1) lr = true;
                    int[] nn = { r * W + wrap(c + 1), r * W + wrap(c - 1), wrap(r + 1) * W + c, wrap(r - 1) * W + c };
                    for (int t : nn) if (sm[t] == 0 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
                if (lr) nseaOpen++; else { nseaEnc++; seaSizes.add(a); }
            }
            say(String.format(LF, "  **陆地连通块 = %d**（含碎屑）   **内海 = %d 块**   开放海 = %d 块", nland, nseaEnc, nseaOpen));
            landSizes.sort((p, q2) -> q2 - p);
            say(String.format(LF, "  最大陆地块 %d px = %.2f%% 图幅；第二大 %d px = %.3f%%", landSizes.get(0), 100.0 * landSizes.get(0) / (W * W),
                landSizes.size() > 1 ? landSizes.get(1) : 0, landSizes.size() > 1 ? 100.0 * landSizes.get(1) / (W * W) : 0.0));
            if (!seaSizes.isEmpty()) {
                seaSizes.sort((p, q2) -> q2 - p);
                say(String.format(LF, "  最大内海 %d px（等效直径 %.1f km）", seaSizes.get(0), 2 * Math.sqrt(seaSizes.get(0) * step * step / Math.PI) / 1000.0));
            }
            // 平滑后的海岸线 edgeDistance
            int nbp = 0; double sum = 0; List<Double> es = new ArrayList<>();
            for (int r = 0; r < W; r++) { int z = zOf(r);
                for (int c = 0; c < W; c++) {
                    if (sm[r * W + c] == 0) continue;
                    if (sm[r * W + wrap(c + 1)] == 1 && sm[r * W + wrap(c - 1)] == 1 && sm[wrap(r + 1) * W + c] == 1 && sm[wrap(r - 1) * W + c] == 1) continue;
                    nbp++; double e = PlateField.edgeDistance(xOf(c), z, seed) / 1000.0; sum += e; es.add(e);
                } }
            java.util.Collections.sort(es);
            if (nbp > 0) say(String.format(LF, "  平滑后海岸线 %d px，edgeDistance 中位 %.0f km，25%%=%.0f，75%%=%.0f，<=50 km 占 %.1f%%",
                nbp, es.get(nbp / 2), es.get(nbp / 4), es.get(nbp * 3 / 4), 100.0 * es.stream().filter(v -> v <= 50).count() / nbp));
            else say("  （没有海岸线）");
            say("");
        }

        // ---------- 2) 按壳类型分开统计内区高程 ----------
        say("--- 2) 内区（edgeDistance > 500 km）按壳类型分开的 elevationWithCell 分布 ---");
        long nc2 = 0, nBelowC = 0, no2 = 0, nBelowO = 0;
        double sc = 0, sc2 = 0, so = 0, so2 = 0;
        double minC = 1e9, maxC = -1e9;
        for (int k = 0; k < 40000; k++) {
            int c = hashPick(k * 2654435761L, W), r = hashPick(k * 40503L + 12345L, W);
            int x = xOf(c), z = zOf(r);
            if (PlateField.edgeDistance(x, z, seed) < 500_000) continue;
            double e = PlateField.elevationWithCell(x, z, seed, PlateField.PLATE_CELL);
            boolean isCont = shellProxy(x, z, 125_000) > -1700;
            if (isCont) { nc2++; sc += e; sc2 += e * e; if (e < 0) nBelowC++; if (e < minC) minC = e; if (e > maxC) maxC = e; }
            else { no2++; so += e; so2 += e * e; if (e < 0) nBelowO++; }
        }
        double mc = sc / Math.max(1, nc2), vc = sc2 / Math.max(1, nc2) - mc * mc;
        double mo = so / Math.max(1, no2), vo = so2 / Math.max(1, no2) - mo * mo;
        say(String.format(LF, "  **陆壳内区**: n=%d  均值 %.1f m  sigma %.1f m  min %.0f max %.0f  **低于海平面 %.2f%%**",
            nc2, mc, Math.sqrt(Math.max(0, vc)), minC, maxC, 100.0 * nBelowC / Math.max(1, nc2)));
        say(String.format(LF, "  洋壳内区: n=%d  均值 %.1f m  sigma %.1f m  低于海平面 %.2f%%", no2, mo, Math.sqrt(Math.max(0, vo)), 100.0 * nBelowO / Math.max(1, no2)));
        say(String.format(LF, "  解析预测（ELEV_CONT=620, sigma=478）: P(e<0) = %.1f%%", 100.0 * normCdf(-620.0 / 478.0)));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    /** K x K 盒均值 > K*K/2 的多数滤波，行列都按周期 wrap。 */
    static byte[] majority(byte[] m, int K) {
        int half = K / 2;
        int[] tmp = new int[W * W];
        for (int r = 0; r < W; r++) {
            int base = r * W, s = 0;
            for (int k = -half; k <= half; k++) s += m[base + wrap(k)];
            tmp[base] = s;
            for (int c = 1; c < W; c++) { s += m[base + wrap(c + half)] - m[base + wrap(c - half - 1)]; tmp[base + c] = s; }
        }
        int[] tmp2 = new int[W * W];
        for (int c = 0; c < W; c++) {
            int s = 0;
            for (int k = -half; k <= half; k++) s += tmp[wrap(k) * W + c];
            tmp2[c] = s;
            for (int r = 1; r < W; r++) { s += tmp[wrap(r + half) * W + c] - tmp[wrap(r - half - 1) * W + c]; tmp2[r * W + c] = s; }
        }
        byte[] out = new byte[W * W];
        int thr = K * K / 2;
        for (int i = 0; i < W * W; i++) out[i] = (byte) (tmp2[i] > thr ? 1 : 0);
        return out;
    }

    static int hashPick(long h, int n) {
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L; h ^= (h >>> 33);
        return (int) (((h >>> 1) % n + n) % n);
    }
    static double normCdf(double z) { return 0.5 * (1.0 + erf(z / Math.sqrt(2.0))); }
    static double erf(double x) {
        double t = 1.0 / (1.0 + 0.3275911 * Math.abs(x));
        double y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return x >= 0 ? y : -y;
    }
}
