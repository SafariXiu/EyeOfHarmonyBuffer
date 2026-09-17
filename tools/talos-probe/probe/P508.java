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
 * P508：路线 A 的预览 + 闸门（设计冻结 §271）。
 *
 * <p>只【读】PlateField：换开关、换 contThreshold（走公开标定入口 isLandFull / elevationFull），
 * 出图 + 量 §271.3 的十条判据。**不改任何生产读数**（开关测完就复位）。
 *
 * <p>口径固定（与 P507 逐字一致，便于与已记录基线对比）：
 *   W = 4000 px @ 5 km/px，x0 = 0，z0 = -MAX_D，span = Z_CYCLE
 *   P243 的 STEP/EDGE_STEPS/MW/G1_TARGET 原封不动（5 km / 50 km / 300 km / 400 km）
 *   A-5 把每个配置的 contThreshold 二分标定到 A0 的陆地占比（±0.15 pt）⇒ 只比形状不比面积
 */
public class P508 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P508] " + s); rep.flush(); System.out.println("[P508] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;             // 5000 block = 5 km
    static long seed;
    static final int MB = 81;                            // 405 km 多数滤波
    static final double MW = 300_000.0, G1_TARGET = 400_000.0, WALL_TOL = 60_000.0;
    static final int EDGE_STEPS = 10;                    // 50 km

    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }

    static final MapWriter.ColorMap SEALAND = new MapWriter.ColorMap() {
        @Override public int rgb(double t) { return t < 0.5 ? 0x1B3B6F : 0xE0D5B0; }
    };

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P508_routeA.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        S("=== P508 路线 A 预览与闸门（§271.3）===");
        S(String.format(LF, "W=%d @ %.2f km/px；窗 %.0f km x %.0f km；PLATE_CELL=%.0f km；CONT_WAV(默认)=%.0f km",
            W, step / 1000, SPAN / 1000.0, SPAN / 1000.0, PlateField.PLATE_CELL / 1000.0, PlateField.CONT_WAV / 1000.0));
        S("");

        double target = coarseFracProd();   // 目标 = 生产口径 isLandWithCell 的陆地占比
        S(String.format(LF, "A0 基准陆地占比（400x400 粗网格 @40 km）= %.4f%%  <== A-5 的标定目标", 100 * target));
        S("");

        int[][] cfgs = { {0,0,0}, {1,0,0}, {0,1,0}, {0,0,1}, {1,1,0}, {1,1,1} };
        String[] tags = { "A0", "A1", "A2", "A3", "A1A2", "A1A2A3" };
        for (int k = 0; k < cfgs.length; k++) {
            run(tags[k], cfgs[k][0] != 0, cfgs[k][1] != 0, cfgs[k][2] != 0, target);
        }
        S("");
        S("判读线（§271.3）：A-1 edgeDist中位 >300 km；A-2 内海 <100；A-3 陆壳内区低于海面 <0.01%；");
        S("                 A-5 陆地占比 ±1 pt；A-6 G1b 不后退；A-7 G2 >=50%；A-8 连通陆块 3~7；A-9 分形维数 1.2~1.4");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    static void set(double a1, double a2, double a3) { }
    static void sw(boolean a1, boolean a2, boolean a3) {
        PlateField.A1_CONTINUOUS_CONT = a1; PlateField.A2_DOMAIN_WARP = a2; PlateField.A3_GATED_RELIEF = a3;
    }
    static boolean isLand(int c, int r, double thr) { return PlateField.isLandFull(xOf(c), zOf(r), seed, PlateField.PLATE_CELL, thr); }

    /** 400x400 粗网格上【生产口径】的陆地占比（isLandWithCell，不走标定入口）。 */
    static double coarseFracProd() {
        int G = 400; long n = 0;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G);
            int z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (PlateField.isLandWithCell(x, z, seed, PlateField.PLATE_CELL)) n++;
        }
        return n / (double) (G * G);
    }

    /** 400x400 粗网格上的陆地占比（标定入口 isLandFull，阈值可调）。 */
    static double coarseFrac(double thr) {
        int G = 400; long n = 0;
        for (int r = 0; r < G; r++) for (int c = 0; c < G; c++) {
            int x = (int) Math.round(X0 + (c + 0.5) * SPAN / G);
            int z = (int) Math.round(Z0 + (r + 0.5) * SPAN / G);
            if (PlateField.isLandFull(x, z, seed, PlateField.PLATE_CELL, thr)) n++;
        }
        return n / (double) (G * G);
    }

    /** 二分 contThreshold 命中目标陆地占比。 */
    static double tune(double target) {
        double lo = -0.80, hi = 0.90;
        for (int it = 0; it < 18; it++) { double mid = 0.5 * (lo + hi); if (coarseFrac(mid) > target) lo = mid; else hi = mid; }
        return 0.5 * (lo + hi);
    }

    static void run(String tag, boolean a1, boolean a2, boolean a3, double target) throws Exception {
        S("########## " + tag + "  (A1=" + a1 + " A2=" + a2 + " A3=" + a3 + ") ##########");
        sw(a1, a2, a3);
        double thr = tune(target);
        double frac = coarseFrac(thr);
        S(String.format(LF, "  A-5 标定：contThreshold=%.5f  ⇒ 粗网格陆地占比 %.4f%%（目标 %.4f%%，差 %+.3f pt）",
            thr, 100 * frac, 100 * target, 100 * (frac - target)));

        byte[] land = new byte[W * W];
        long nl = 0;
        double wsum = 0, lw = 0;
        for (int r = 0; r < W; r++) {
            int z = zOf(r);
            double cw = Math.cos(WorldContract.latOf(z));
            for (int c = 0; c < W; c++) {
                boolean l = isLand(c, r, thr);
                land[r * W + c] = (byte) (l ? 1 : 0);
                wsum += cw; if (l) { nl++; lw += cw; }
            }
        }
        S(String.format(LF, "  陆地占比：均匀 %.4f%%   面积加权 %.4f%%", 100.0 * nl / (W * W), 100.0 * lw / wsum));

        // PNG
        double[] v = new double[W * W];
        for (int i = 0; i < W * W; i++) v[i] = land[i];
        File png = new File(MAPDIR, "sealandA_" + tag + ".png");
        MapWriter.writePng(png, W, W, v, 0.0, 1.0, SEALAND);
        S("  PNG = " + png.getAbsolutePath());

        // A-2 内海（原始）
        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        int nEnc = 0, nLargeLand = 0; long bigLand = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
            while (sp > 0) { int p = stk[--sp]; a++; int r = p / W, c = p - r * W;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
            if (a > 0.02 * W * W) nLargeLand++;
        }
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 1 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 2; boolean lr = false;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W; if (c == 0 || c == W - 1) lr = true;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 0 && lab[t] == 0) { lab[t] = 2; stk[sp++] = t; } }
            if (!lr) nEnc++;
        }
        S(String.format(LF, "  A-2 内海（原始）= %d    A-8 >=2%% 图幅的连通陆块 = %d", nEnc, nLargeLand));

        // 405 km 多数滤波后的几何
        byte[] sm = majority(land, MB);
        int nEncSm = 0, nLandSm = 0;
        int[] lab2 = new int[W * W];
        for (int i = 0; i < W * W; i++) {
            if (sm[i] == 0 || lab2[i] != 0) continue;
            nLandSm++; int sp = 0; stk[sp++] = i; lab2[i] = 1;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (sm[t] == 1 && lab2[t] == 0) { lab2[t] = 1; stk[sp++] = t; } }
        }
        for (int i = 0; i < W * W; i++) {
            if (sm[i] == 1 || lab2[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab2[i] = 2; boolean lr = false;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W; if (c == 0 || c == W - 1) lr = true;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (sm[t] == 0 && lab2[t] == 0) { lab2[t] = 2; stk[sp++] = t; } }
            if (!lr) nEncSm++;
        }
        S(String.format(LF, "  A-2 内海（405 km 平滑后）= %d    陆块 = %d", nEncSm, nLandSm));

        // A-1 平滑海岸线的 edgeDistance + A-9 分形维数
        List<Double> es = new ArrayList<>();
        byte[] bnd = new byte[W * W];
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c = 0; c < W; c++) {
                if (sm[r * W + c] == 0) continue;
                if (sm[r * W + wr(c + 1)] == 1 && sm[r * W + wr(c - 1)] == 1 && sm[wr(r + 1) * W + c] == 1 && sm[wr(r - 1) * W + c] == 1) continue;
                bnd[r * W + c] = 1;
                es.add(PlateField.edgeDistance(xOf(c), z, seed) / 1000.0);
            } }
        java.util.Collections.sort(es);
        if (!es.isEmpty()) S(String.format(LF, "  A-1 平滑海岸线 %d px，edgeDistance 中位 %.0f km  (25%%=%.0f 75%%=%.0f)",
            es.size(), es.get(es.size() / 2), es.get(es.size() / 4), es.get(es.size() * 3 / 4)));
        StringBuilder d = new StringBuilder();
        int[] boxes = { 5, 10, 20, 40, 80, 160 };
        double[] lr2 = new double[boxes.length];
        for (int bi = 0; bi < boxes.length; bi++) {
            int s = boxes[bi]; long cnt = 0;
            int g = W / s;
            for (int br = 0; br < g; br++) for (int bc = 0; bc < g; bc++) {
                boolean hit = false;
                for (int rr = br * s; rr < (br + 1) * s && !hit; rr++) for (int cc = bc * s; cc < (bc + 1) * s; cc++) if (bnd[rr * W + cc] == 1) { hit = true; break; }
                if (hit) cnt++;
            }
            lr2[bi] = Math.log(cnt);
        }
        double sx = 0, sy = 0, sxx = 0, sxy = 0;
        for (int bi = 0; bi < boxes.length; bi++) { double X = Math.log(1.0 / boxes[bi]); sx += X; sy += lr2[bi]; sxx += X * X; sxy += X * lr2[bi]; }
        int n = boxes.length;
        double D = (n * sxy - sx * sy) / (n * sxx - sx * sx);
        S(String.format(LF, "  A-9 分形维数 D = %.3f  (盒 %d..%d px)", D, boxes[0], boxes[boxes.length - 1]));

        // A-3 / A-4 陆壳内区
        long nc = 0, below = 0; double su = 0, su2 = 0; double mn = 1e9, mx = -1e9;
        for (int k = 0; k < 40000; k++) {
            int c = hashPick(k * 2654435761L, W), r = hashPick(k * 40503L + 12345L, W);
            int x = xOf(c), z = zOf(r);
            if (PlateField.edgeDistance(x, z, seed) < 500_000) continue;
            if (shellProxy(x, z, thr) <= -1700) continue;             // 只要陆壳
            double e = PlateField.elevationFull(x, z, seed, PlateField.PLATE_CELL, thr);
            nc++; su += e; su2 += e * e; if (e < 0) below++;
            if (e < mn) mn = e; if (e > mx) mx = e;
        }
        double mu = su / Math.max(1, nc), va = su2 / Math.max(1, nc) - mu * mu;
        S(String.format(LF, "  A-3/A-4 陆壳内区 n=%d  均值 %.1f m  sigma %.1f m  min %.0f max %.0f  低于海面 %.4f%%",
            nc, mu, Math.sqrt(Math.max(0, va)), mn, mx, 100.0 * below / Math.max(1, nc)));

        // A-6 / A-7  P243 口径（逐字复用，只把窗口放大到整幅）
        p243(land);
        S("");
        sw(false, false, false);
    }

    /** raw 高程的局部均值 ⇒ 壳类型代理（与 P507 同一把尺子）。 */
    static double shellProxy(int x, int z, double thr) {
        double s = 0; int n = 0;
        for (int dz = -125_000; dz <= 125_000; dz += 125_000)
            for (int dx = -125_000; dx <= 125_000; dx += 125_000) { s += PlateField.elevationFull(x + dx, z + dz, seed, PlateField.PLATE_CELL, thr); n++; }
        return s / n;
    }

    /** P243 的 analyzeChains（G1b + persist/westRun 统计）+ G2（MW 过滤的盆面积占比）。 */
    static void p243(byte[] a) {
        final int MAXI = 512;
        int[] pxw = new int[MAXI], pxe = new int[MAXI], pPersist = new int[MAXI], pCoast = new int[MAXI];
        long[] pArea = new long[MAXI];
        long wTot = 0, wLong = 0, seaTot = 0, seaInBasin = 0;
        List<Integer> persistList = new ArrayList<>(), coastList = new ArrayList<>();
        int pm = 0;
        for (int iz = 0; iz < W; iz++) {
            int[] xw = new int[MAXI], xe = new int[MAXI], wt = new int[MAXI];
            int m = 0, ix = 0;
            while (ix < W) {
                if (a[iz * W + ix] == 1) { ix++; continue; }
                int j = ix;
                while (j + 1 < W && a[iz * W + j + 1] == 0) j++;
                seaTot += (j - ix + 1);
                if ((j - ix + 1) * step >= MW) {
                    seaInBasin += (j - ix + 1);
                    if (m < MAXI) { xw[m] = ix; xe[m] = j; wt[m] = j - ix + 1; m++; }
                }
                ix = j + 1;
            }
            int[] persist = new int[m], coast = new int[m], wcur = new int[m];
            long[] area = new long[m];
            boolean[] matched = new boolean[pm];
            for (int c = 0; c < m; c++) {
                int best = -1, bestOv = 0;
                for (int p = 0; p < pm; p++) { int lo = Math.max(xw[c], pxw[p]), hi = Math.min(xe[c], pxe[p]); if (hi - lo > bestOv) { bestOv = hi - lo; best = p; } }
                if (best >= 0 && bestOv > 0) {
                    persist[c] = pPersist[best] + (int) step;
                    double dd = Math.abs(xw[c] - pxw[best]) * step;
                    coast[c] = dd <= WALL_TOL ? pCoast[best] + (int) step : (int) step;
                    wcur[c] = wt[c]; area[c] = pArea[best] + (long) wt[c] * (long) step;
                    matched[best] = true;
                } else { persist[c] = (int) step; coast[c] = (int) step; wcur[c] = wt[c]; area[c] = (long) wt[c] * (long) step; }
            }
            if (pm > 0) for (int p = 0; p < pm; p++) if (!matched[p]) {
                persistList.add(pPersist[p]); coastList.add(pCoast[p]);
                wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p];
            }
            System.arraycopy(xw, 0, pxw, 0, m); System.arraycopy(xe, 0, pxe, 0, m);
            System.arraycopy(persist, 0, pPersist, 0, m); System.arraycopy(coast, 0, pCoast, 0, m);
            System.arraycopy(area, 0, pArea, 0, m); pm = m;
        }
        for (int p = 0; p < pm; p++) { persistList.add(pPersist[p]); coastList.add(pCoast[p]); wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
        java.util.Collections.sort(coastList);
        java.util.Collections.sort(persistList);
        S(String.format(LF, "  A-6 【G1b】宽度>=%.0fkm 的盆里，westRun>=%.0fkm 的面积比 = %.4f",
            MW / 1000, G1_TARGET / 1000, wTot > 0 ? wLong / (double) wTot : 0));
        S(String.format(LF, "      westRun  中位 %.0f km   P90 %.0f km   最长 %.0f km   （盆链 %d 条）",
            coastList.isEmpty() ? 0 : coastList.get(coastList.size() / 2) / 1000.0,
            coastList.isEmpty() ? 0 : coastList.get((int) (coastList.size() * 0.9)) / 1000.0,
            coastList.isEmpty() ? 0 : coastList.get(coastList.size() - 1) / 1000.0, coastList.size()));
        S(String.format(LF, "  A-7 【G2】宽度>=%.0fkm 的盆里的海格比例 = %.4f   （persist 中位 %.0f km）",
            MW / 1000, seaTot > 0 ? seaInBasin / (double) seaTot : 0,
            persistList.isEmpty() ? 0 : persistList.get(persistList.size() / 2) / 1000.0));
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

    static int hashPick(long h, int n) {
        h ^= (h >>> 33); h *= 0xff51afd7ed558ccdL; h ^= (h >>> 33); h *= 0xc4ceb9fe1a85ec53L; h ^= (h >>> 33);
        return (int) (((h >>> 1) % n + n) % n);
    }
}
