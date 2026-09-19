package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P243（M1）：**大陆几何的量尺** —— 新板块大陆 vs 旧噪声大陆，同一把尺子。
 *
 * <p>设计冻结 §6 的闸门 G1/G2 由本探针给出：
 * <pre>
 *   G1 经向海岸连贯长度（中位）        >= 400 km
 *   G2 处于宽度 >= 2*M_w(~300km) 盆里的海格比例 >= 50%
 * </pre>
 * G3/G4（强化比 / 西边界流峰值）需要跑求解器，在下一支探针。
 *
 * <h3>为什么这两条能提前判死</h3>
 * 实测对照：规则闭合盆 197 km 宽给出 77 mm/s，噪声大陆 ~300 km 宽只给出 2.4 mm/s。
 * 差别不在宽度，在**海岸连贯性**。所以先量几何，再谈物理。
 *
 * <h3>定义（写死，避免口径漂移）</h3>
 * <pre>
 *   西岸格(x,z) = (x,z) 是海，且向西 50 km 内存在陆
 *   东岸格(x,z) = 对称
 *   连贯长度    = 固定 x 上，「西岸格」沿 z 连续成立的最长行程（x 单位 block，z 步长 5 km）
 *   —— 这正是西边界流需要的：一段在经向上连贯的岸线
 *   海区间宽度 = 单行 z 上海格的极大连续段长度
 * </pre>
 */
public class P243 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int STEP = 5_000;
    static final int NX = 800, NZ = 800;         // 4000 km x 4000 km
    static final int EDGE_STEPS = 10;            // 50 km
    static final double MW = 300_000.0;          // 2*M_w 的盆宽门槛
    static final double G1_TARGET = 400_000.0;   // 连贯长度目标

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;
    static long T0;

    static boolean[] land;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p243_report.txt"), "UTF-8");
        T0 = System.nanoTime();
        say("大陆几何量尺：新板块大陆 vs 旧噪声大陆（同一把尺子）");
        say(String.format(L, "口径：%d x %d 采样 @%d km = %d x %d km；西/东岸判据 = 50 km；盆宽门槛 %.0f km；G1 目标 %.0f km",
            NX, NZ, STEP / 1000, NX * STEP / 1000, NZ * STEP / 1000, MW / 1000, G1_TARGET / 1000));

        boolean[] plate = build(true);
        boolean[] noise = build(false);

        say("");
        say("########## 新：板块构造（PlateField）##########");
        asciiMap(plate);
        analyze("新板块", plate);
        analyzeChains("新板块", plate);

        say("");
        say("########## 旧：阈值化噪声（NoiseContinentGrid）##########");
        asciiMap(noise);
        analyze("旧噪声", noise);
        analyzeChains("旧噪声", noise);
        sweep();

        say("");
        say(String.format(L, "总耗时 %.1f s", (System.nanoTime() - T0) / 1e9));
        rep.close();
    }

    static void say(String s) { System.out.println("[P243] " + s); rep.println("[P243] " + s); }

    static boolean[] buildCell(int cell) {
        boolean[] a = new boolean[NX * NZ];
        for (int iz = 0; iz < NZ; iz++) {
            int z = iz * STEP;
            for (int ix = 0; ix < NX; ix++) a[iz * NX + ix] = PlateField.isLandWithCell(ix * STEP, z, SD, cell);
        }
        return a;
    }

    /** 板块格边长扫描：唯一变量就是格子大小，其余参数全不动。 */
    static void sweep() {
        say("");
        say("########## 板块格边长扫描 ##########");
        int[] cells = {300_000, 400_000, 600_000, 800_000, 1_200_000};
        for (int cell : cells) {
            say("");
            say("----- PLATE_CELL = " + (cell / 1000) + " km -----");
            boolean[] a = buildCell(cell);
            analyze("c" + (cell / 1000), a);
            analyzeChains("c" + (cell / 1000), a);
        }
    }

    static boolean[] build(boolean usePlate) {        boolean[] a = new boolean[NX * NZ];
        for (int iz = 0; iz < NZ; iz++) {
            int z = iz * STEP;
            for (int ix = 0; ix < NX; ix++) {
                int x = ix * STEP;
                a[iz * NX + ix] = usePlate
                    ? PlateField.isLand(x, z, SD)
                    : (NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0);
            }
        }
        return a;
    }

    static boolean at(boolean[] a, int ix, int iz) {
        int jx = Math.max(0, Math.min(NX - 1, ix));
        int jz = Math.max(0, Math.min(NZ - 1, iz));
        return a[jz * NX + jx];
    }

    /** 120 列 x 40 行 ASCII 图（x 方向 33 km/字符，z 方向 100 km/字符）。 */
    static void asciiMap(boolean[] a) {
        int cols = 120, rows = 40;
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < cols; c++) {
                int ix = (int) ((c + 0.5) * NX / cols), iz = (int) ((r + 0.5) * NZ / rows);
                sb.append(at(a, ix, iz) ? '#' : '.');
            }
            say("    " + sb);
        }
    }

    /** 西墙相邻行允许的位移（block）。60 km。 */
    static final double WALL_TOL = 60_000.0;

    /**
     * 【修正口径 G1b】旧口径（{@link #analyze}）测的是「岸线是否停在同一经度」，
     * 惩罚的是**横向摆动幅度**，而不是连续性 —— 而真实西边界流是跟着摆动的海岸走的。
     *
     * <p>这里改成**追踪海区间**（相邻行按 x 重叠配对）：
     * <pre>
     *   persist = 该「盆」在 z 上持续多久（配对即续，无墙判据）
     *   westRun = 西墙每行位移 <= WALL_TOL 才续；一旦跳变就重开
     * </pre>
     * persist 对应「涡旋能有多长」，westRun 对应「西边界流有多少跑道」。
     */
    static void analyzeChains(String tag, boolean[] a) {
        final int MAXI = 256;
        int[] pxw = new int[MAXI], pxe = new int[MAXI], pPersist = new int[MAXI], pCoast = new int[MAXI];
        int[] pW = new int[MAXI];
        long[] pArea = new long[MAXI];
        long wTot = 0, wLong = 0;
        int pm = 0;
        int ovf = 0;
        java.util.ArrayList<Integer> persistList = new java.util.ArrayList<>();
        java.util.ArrayList<Integer> coastList = new java.util.ArrayList<>();
        for (int iz = 0; iz < NZ; iz++) {
            int[] xw = new int[MAXI], xe = new int[MAXI], wt = new int[MAXI];
            int m = 0;
            int ix = 0;
            while (ix < NX) {
                if (at(a, ix, iz)) { ix++; continue; }
                int j = ix;
                while (j + 1 < NX && !at(a, j + 1, iz)) j++;
                // 只统计宽度 >= MW 的盆：20 km 的「盆」不是盆，会把中位数彻底淹没
                if ((j - ix + 1) * (double) STEP >= MW) {
                    if (m < MAXI) { xw[m] = ix; xe[m] = j; wt[m] = j - ix + 1; m++; } else ovf++;
                }
                ix = j + 1;
            }
            int[] persist = new int[m], coast = new int[m], wcur = new int[m];
            long[] area = new long[m];
            boolean[] matched = new boolean[pm];
            for (int c = 0; c < m; c++) {
                int best = -1, bestOv = 0;
                for (int p = 0; p < pm; p++) {
                    int lo = Math.max(xw[c], pxw[p]), hi = Math.min(xe[c], pxe[p]);
                    int ov = hi - lo;
                    if (ov > bestOv) { bestOv = ov; best = p; }
                }
                if (best >= 0 && bestOv > 0) {
                    persist[c] = pPersist[best] + STEP;
                    double d = Math.abs(xw[c] - pxw[best]) * (double) STEP;
                    coast[c] = d <= WALL_TOL ? pCoast[best] + STEP : STEP;
                    wcur[c] = wt[c];
                    area[c] = pArea[best] + (long) wt[c] * STEP;
                    matched[best] = true;
                } else { persist[c] = STEP; coast[c] = STEP; wcur[c] = wt[c]; area[c] = (long) wt[c] * STEP; }
            }
            if (pm > 0) {
                for (int p = 0; p < pm; p++) if (!matched[p]) {
                    persistList.add(pPersist[p]); coastList.add(pCoast[p]);
                    wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p];
                }
            }
            System.arraycopy(xw, 0, pxw, 0, m);
            System.arraycopy(xe, 0, pxe, 0, m);
            System.arraycopy(persist, 0, pPersist, 0, m);
            System.arraycopy(coast, 0, pCoast, 0, m);
            System.arraycopy(wcur, 0, pW, 0, m);
            System.arraycopy(area, 0, pArea, 0, m);
            pm = m;
        }
        for (int p = 0; p < pm; p++) { persistList.add(pPersist[p]); coastList.add(pCoast[p]); wTot += pArea[p]; if (pCoast[p] >= G1_TARGET) wLong += pArea[p]; }
        say(String.format(L, "  [%s] 【G1b】宽度>=%.0fkm 的盆里，处于「西墙连续>=%.0fkm」的海格比例 = %.3f",
            tag, MW / 1000, G1_TARGET / 1000, wTot > 0 ? wLong / (double) wTot : 0));
        say(String.format(L, "  [%s] 盆链（配对）%d 条%s", tag, persistList.size(), ovf > 0 ? "  ⚠ 行内区间溢出 " + ovf : ""));
        stat("盆持续长度 persist", persistList);
        stat("西墙连续长度 westRun", coastList);
    }

    static void stat(String name, java.util.ArrayList<Integer> list) {
        if (list.isEmpty()) { say("    " + name + "：空"); return; }
        int n = list.size();
        int[] c = new int[n];
        for (int i = 0; i < n; i++) c[i] = list.get(i);
        Arrays.sort(c);
        int ge = 0; long sum = 0;
        for (int v : c) { if (v >= G1_TARGET) ge++; sum += v; }
        say(String.format(L, "    %-20s 中位 %6.0f km   P90 %6.0f km   最长 %6.0f km   >=%.0fkm 占比 %.3f   均值 %.0f km",
            name, c[n / 2] / 1000.0, c[(int) (n * 0.9)] / 1000.0,
            c[n - 1] / 1000.0, G1_TARGET / 1000, ge / (double) n, sum / (double) n / 1000));
    }

    static void analyze(String tag, boolean[] a) {        int sea = 0;
        for (boolean b : a) if (!b) sea++;
        say(String.format(L, "  [%s] 陆地点 %d / %d = 陆地占比 %.3f", tag, NX * NZ - sea, NX * NZ, (NX * NZ - sea) / (double) (NX * NZ)));

        // ---- 岸线连贯长度：固定 x 的列上，西/东岸格沿 z 的连续行程 ----
        int[] wRuns = new int[NZ + 1], eRuns = new int[NZ + 1];
        int wN = 0, eN = 0;
        long wSeaCell = 0, wLongCell = 0, eSeaCell = 0, eLongCell = 0;
        for (int ix = 0; ix < NX; ix++) {
            int wr = 0, er = 0;
            for (int iz = 0; iz < NZ; iz++) {
                boolean isSea = !at(a, ix, iz);
                boolean w = false, e = false;
                if (isSea) {
                    for (int k = 1; k <= EDGE_STEPS; k++) if (at(a, ix - k, iz)) { w = true; break; }
                    for (int k = 1; k <= EDGE_STEPS; k++) if (at(a, ix + k, iz)) { e = true; break; }
                }
                if (w) wr++; else { if (wr > 0) { wRuns[wr]++; wN++; wSeaCell += (long) wr * STEP; if (wr * STEP >= G1_TARGET) wLongCell += (long) wr * STEP; } wr = 0; }
                if (e) er++; else { if (er > 0) { eRuns[er]++; eN++; eSeaCell += (long) er * STEP; if (er * STEP >= G1_TARGET) eLongCell += (long) er * STEP; } er = 0; }
            }
            if (wr > 0) { wRuns[wr]++; wN++; wSeaCell += (long) wr * STEP; if (wr * STEP >= G1_TARGET) wLongCell += (long) wr * STEP; }
            if (er > 0) { eRuns[er]++; eN++; eSeaCell += (long) er * STEP; if (er * STEP >= G1_TARGET) eLongCell += (long) er * STEP; }
        }
        reportRuns("西岸", wRuns, wN, wSeaCell, wLongCell);
        reportRuns("东岸", eRuns, eN, eSeaCell, eLongCell);

        // ---- 盆宽（单行海区间宽度）----
        int[] bins = new int[12];   // 0-50,50-100,...,500-600,>600 km
        double[] widthSum = new double[1];
        long total = 0, wide = 0;
        java.util.ArrayList<Double> ws = new java.util.ArrayList<>();
        for (int iz = 0; iz < NZ; iz++) {
            int ix = 0;
            while (ix < NX) {
                if (at(a, ix, iz)) { ix++; continue; }
                int j = ix;
                while (j + 1 < NX && !at(a, j + 1, iz)) j++;
                int wBlocks = (j - ix + 1) * STEP;
                ws.add((double) wBlocks);
                total += (j - ix + 1);
                if (wBlocks >= MW) wide += (j - ix + 1);
                int bi = Math.min(11, wBlocks / 50_000);
                bins[bi] += (j - ix + 1);
                ix = j + 1;
            }
        }
        double[] arr = new double[ws.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = ws.get(i);
        Arrays.sort(arr);
        say(String.format(L, "  [%s] 海区间：段数 %d   宽度中位 %.0f km   P90 %.0f km",
            tag, arr.length, arr.length > 0 ? arr[arr.length / 2] / 1000 : 0, arr.length > 0 ? arr[(int) (arr.length * 0.9)] / 1000 : 0));
        say(String.format(L, "  [%s] 【G2】处于宽度 >= %.0f km 盆里的海格比例 = %.3f", tag, MW / 1000, wide / (double) Math.max(1, total)));
        StringBuilder sb = new StringBuilder("        宽度直方图(km, 海格数): ");
        String[] lab = {"<50", "50-100", "100-150", "150-200", "200-250", "250-300", "300-350", "350-400", "400-450", "450-500", "500-550", ">550"};
        for (int i = 0; i < 12; i++) sb.append(lab[i]).append('=').append(bins[i]).append("  ");
        say(sb.toString());
    }

    static void reportRuns(String tag, int[] runs, int n, long seaCell, long longCell) {
        if (n == 0) { say("  [" + tag + "] 没有岸线"); return; }
        int[] c = new int[n]; int k = 0;
        for (int i = 1; i <= NZ; i++) for (int q = 0; q < runs[i]; q++) c[k++] = i;
        Arrays.sort(c);
        double med = c[n / 2] * (double) STEP / 1000;
        double p90 = c[(int) (n * 0.9)] * (double) STEP / 1000;
        int ge = 0; for (int v : c) if (v * (double) STEP >= G1_TARGET) ge++;
        say(String.format(L, "  [%s] 岸线段数 %d   连贯长度 中位 %.0f km  P90 %.0f km   最长 %.0f km",
            tag, n, med, p90, c[n - 1] * (double) STEP / 1000));
        say(String.format(L, "  [%s] 【G1】连贯长度 >= %.0f km 的段占比 %.3f（按岸线长度加权 %.3f）",
            tag, G1_TARGET / 1000, ge / (double) n, longCell / (double) Math.max(1, seaCell)));
    }
}
