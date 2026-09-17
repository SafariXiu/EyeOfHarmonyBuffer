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

/** P510：路线 A 第三轮 —— 八度 x 过渡带 x 岛弧 扫描 + 左上角「饼干屑」归因。 */
public class P510 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "run" + File.separator + "talos_maps");
    static PrintStream rep;
    static void S(String s) { rep.println("[P510] " + s); rep.flush(); System.out.println("[P510] " + s); System.out.flush(); }

    static int W = 4000;
    static long X0 = 0L, Z0 = -(long) WorldContract.MAX_D, SPAN = WorldContract.Z_CYCLE;
    static double step = SPAN / (double) W;
    static long seed;
    static final int MB = 81;
    static final double MW = 300_000.0, G1_TARGET = 400_000.0, WALL_TOL = 60_000.0;
    static final MapWriter.ColorMap SEALAND = new MapWriter.ColorMap() {
        @Override public int rgb(double t) { return t < 0.5 ? 0x1B3B6F : 0xE0D5B0; }
    };
    static int xOf(int c) { return (int) Math.round(X0 + c * step); }
    static int zOf(int r) { return (int) Math.round(Z0 + r * step); }
    static int wr(int v) { return v < 0 ? v + W : (v >= W ? v - W : v); }
    static final int UL_C = 800, UL_R = 3200;      // 左上角：x<4000 km 且 z>+6e6（lat>54 度北）

    public static void main(String[] args) throws Exception {
        File dir = new File(ROOT, "build" + File.separator + "eoh_probe" + File.separator + "refs");
        dir.mkdirs();
        rep = new PrintStream(new File(dir, "P510_routeA3.txt"), "UTF-8");
        seed = SimTerrain.seedOf(SEED);
        S("=== P510 路线 A 第三轮 ===");

        // ---- 极点环绕演示：wideOceanWeight 的 8 条 7000 km 臂在极点附近会【穿过极点】 ----
        S("");
        S("--- 归因 0：超宽大洋判据的 8 条臂在极区穿过了极点（z 是周期轴，臂长 7000 km）---");
        for (int zz : new int[]{ 9_500_000, 9_900_000 }) {
            StringBuilder sb = new StringBuilder(String.format(LF, "  z=%9d (lat %+7.2f): 八向落点纬度 =", zz, Math.toDegrees(WorldContract.latOf(zz))));
            for (int k = 0; k < 8; k++) {
                double a = 2.0 * Math.PI * k / 8.0 + 0.39;
                int sz = (int) Math.round(zz + 7_000_000.0 * Math.sin(a));
                sb.append(String.format(LF, " %+6.1f", Math.toDegrees(WorldContract.latOf(sz))));
            }
            S(sb.toString());
        }
        S("  ⇒ 从北半球高纬出发，+z 方向的臂【翻过极点落到南半球】⇒ 判据在那里量的是另一个半球，不是邻域。");
        S("");

        int[][] cfgs = {
            {3, 6, 7000, 900}, {1, 6, 7000, 900}, {2, 6, 7000, 900},
            {1, 18, 7000, 900}, {2, 18, 7000, 900}, {1, 18, 7000, 400}, {1, 18, 0, 900}
        };
        String[] tags = { "oct3cs06", "oct1cs06", "oct2cs06", "oct1cs18", "oct2cs18", "oct1cs18arc400", "oct1cs18arcOFF" };
        PlateField.A1_CONTINUOUS_CONT = false; PlateField.A2_DOMAIN_WARP = false; PlateField.A3_GATED_RELIEF = false;
        double baseFrac = coarseProd();   // 生产默认口径
        S(String.format(LF, "A-5 标定目标 = 生产默认陆地占比 %.4f%%（400x400 粗网格）", 100 * baseFrac));
        S("");
        for (int k = 0; k < cfgs.length; k++) {
            run(tags[k], cfgs[k][0], cfgs[k][1] / 100.0, cfgs[k][2], cfgs[k][3], baseFrac);
        }
        PlateField.A1_CONTINUOUS_CONT = false; PlateField.A2_DOMAIN_WARP = false; PlateField.A3_GATED_RELIEF = false;
        PlateField.CONT_OCT = 1; PlateField.CS_W = 0.06;
        PlateField.MAX_OCEAN_HALF = 7_000_000; PlateField.OCEAN_BREAK_H = 900.0;
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }

    /** 返回该配置标定后的陆地占比（供下一个配置当目标）。 */
    static double run(String tag, int oct, double cs, int arcHalf, double arcH, double targetIn) throws Exception {
        PlateField.A1_CONTINUOUS_CONT = true; PlateField.A2_DOMAIN_WARP = true; PlateField.A3_GATED_RELIEF = true;
        PlateField.CONT_OCT = oct; PlateField.CS_W = cs;
        PlateField.MAX_OCEAN_HALF = arcHalf; PlateField.OCEAN_BREAK_H = arcH;
        S("########## " + tag + "  (oct=" + oct + " CS_W=" + cs + " arcHalf=" + arcHalf / 1000 + "km arcH=" + arcH + ") ##########");
        double target = targetIn < 0 ? coarseProd() : targetIn;
        double thr = tune(target);
        byte[] land = new byte[W * W];
        long nl = 0; double wsum = 0, lw = 0;
        for (int r = 0; r < W; r++) { int z = zOf(r); double cw = Math.cos(WorldContract.latOf(z));
            for (int c = 0; c < W; c++) {
                boolean l = PlateField.isLandFullWithArc(xOf(c), z, seed, PlateField.PLATE_CELL, thr);
                land[r * W + c] = (byte) (l ? 1 : 0);
                wsum += cw; if (l) { nl++; lw += cw; }
            } }
        double frac = nl / (double) (W * W);
        S(String.format(LF, "  标定 thr=%.5f  陆地 均匀 %.2f%%  面积加权 %.2f%%  (目标 %.2f%%)", thr, 100 * frac, 100 * lw / wsum, 100 * target));
        double[] v = new double[W * W];
        for (int i = 0; i < W * W; i++) v[i] = land[i];
        File png = new File(MAPDIR, "sealandB_" + tag + ".png");
        MapWriter.writePng(png, W, W, v, 0.0, 1.0, SEALAND);

        int[] lab = new int[W * W]; int[] stk = new int[W * W];
        int[] res = comps(land, lab, stk, true);
        int nSeaTot = res[0], nSeaDot = res[1], nSeaLin = res[2], nLandTot = res[3], nLandDot = res[4], nLandLin = res[5], nBig = res[6];
        S(String.format(LF, "  内海 %d（点状 %d / 河流状 %d）  非最大陆块 %d（点状 %d / 河流状 %d）  >=2%%陆块 %d",
            nSeaTot, nSeaDot, nSeaLin, nLandTot, nLandDot, nLandLin, nBig));

        // 左上角
        long ulN = 0, ulL = 0;
        for (int r = UL_R; r < W; r++) for (int c = 0; c < UL_C; c++) { ulN++; if (land[r * W + c] == 1) ulL++; }
        int ulLand = 0, ulDot = 0;
        int[] labU = new int[W * W];
        for (int r = UL_R; r < W; r++) for (int c = 0; c < UL_C; c++) {
            int i = r * W + c; if (land[i] == 0 || labU[i] != 0) continue;
            int sp = 0; stk[sp++] = i; labU[i] = 1; long a = 0;
            while (sp > 0) { int p = stk[--sp]; a++; int rr = p / W, cc = p - rr * W;
                int[] nn = { rr * W + wr(cc + 1), rr * W + wr(cc - 1), wr(rr + 1) * W + cc, wr(rr - 1) * W + cc };
                for (int t : nn) if (land[t] == 1 && labU[t] == 0) { labU[t] = 1; stk[sp++] = t; } }
            ulLand++; if (a < 100) ulDot++;
        }
        S(String.format(LF, "  【左上角 x<4000km & lat>54N】陆地占比 %.2f%%  陆块 %d 个（其中 <%d px 的碎屑 %d 个）",
            100.0 * ulL / ulN, ulLand, 100, ulDot));

        // A-1
        byte[] sm = majority(land, MB);
        List<Double> es = new ArrayList<>();
        for (int r = 0; r < W; r++) { int z = zOf(r);
            for (int c = 0; c < W; c++) {
                if (sm[r * W + c] == 0) continue;
                if (sm[r * W + wr(c + 1)] == 1 && sm[r * W + wr(c - 1)] == 1 && sm[wr(r + 1) * W + c] == 1 && sm[wr(r - 1) * W + c] == 1) continue;
                es.add(PlateField.edgeDistance(xOf(c), z, seed) / 1000.0);
            } }
        java.util.Collections.sort(es);
        S(String.format(LF, "  A-1 平滑海岸线 %d px  edgeDist 中位 %.0f km", es.size(), es.isEmpty() ? 0 : es.get(es.size() / 2)));
        p243(land);
        S("  PNG = " + png.getName());
        S("");
        return frac;
    }

    /** 返回 {内海总数,内海点状,内海河流状,陆块总数,陆块点状,陆块河流状,>=2%陆块数}。 */
    static int[] comps(byte[] land, int[] lab, int[] stk, boolean withStats) {
        int nS = 0, sDot = 0, sLin = 0, nL = 0, lDot = 0, lLin = 0, nBig = 0;
        for (int i = 0; i < W * W; i++) {
            if (land[i] == 0 || lab[i] != 0) continue;
            int sp = 0; stk[sp++] = i; lab[i] = 1; long a = 0;
            double sx = 0, sz = 0, sxx = 0, szz = 0, sxz = 0;
            while (sp > 0) { int p = stk[--sp]; int r = p / W, c = p - r * W; a++;
                double dx = c * step, dz = r * step; sx += dx; sz += dz; sxx += dx * dx; szz += dz * dz; sxz += dx * dz;
                int[] nn = { r * W + wr(c + 1), r * W + wr(c - 1), wr(r + 1) * W + c, wr(r - 1) * W + c };
                for (int t : nn) if (land[t] == 1 && lab[t] == 0) { lab[t] = 1; stk[sp++] = t; } }
            if (a > 0.02 * W * W) nBig++;
            nL++; double[] m2 = shape(a, sx, sz, sxx, szz, sxz);
            if (m2[0] < 30) lDot++; else if (m2[1] >= 3.0) lLin++;
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
        return new int[]{ nS, sDot, sLin, nL, lDot, lLin, nBig };
    }

    static double[] shape(long a, double sx, double sz, double sxx, double szz, double sxz) {
        double mx = sx / a, mz = sz / a;
        double cxx = sxx / a - mx * mx, czz = szz / a - mz * mz, cxz = sxz / a - mx * mz;
        double tr = cxx + czz, det = cxx * czz - cxz * cxz;
        double disc = Math.sqrt(Math.max(0, tr * tr / 4 - det));
        double l1 = tr / 2 + disc, l2 = Math.max(1e-9, tr / 2 - disc);
        return new double[]{ 2 * Math.sqrt(a * step * step / Math.PI) / 1000.0, Math.sqrt(l1 / l2) };
    }

    /**
     * A-5 的目标：**生产默认**（三开关全关）的陆地占比。
     *
     * <p>⚠ E98（本轮自己的口径错误）：第一版这里写的是
     * {@code isLandFullWithArc(..., 0.10)} —— 那是把**写过死的 0.10 阈值套在 A1 的连续场上**，
     * 而 A1 的标定阈值根本不是 0.10（P508 实测是 -0.12）⇒ 目标被抬到 **59.4%**（真值 47.5%），
     * 整轮扫描都跑在一个比现状「陆得多」的世界上，读数不可比。**必须用 isLandWithCell 直接量。**
     */
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
        S(String.format(LF, "  G1b = %.4f    G2 = %.4f", wTot > 0 ? wLong / (double) wTot : 0, seaTot > 0 ? seaInBasin / (double) seaTot : 0));
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
