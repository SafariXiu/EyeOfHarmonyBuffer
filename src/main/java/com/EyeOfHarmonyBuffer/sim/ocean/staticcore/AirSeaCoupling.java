package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-04（A4 起步）：海气不动点环。
 *
 * <p>把「大气看 SST'/冰 → 算通量 → 海洋求解 → 新 SST'/冰」写成一个可复用的环，
 * 而不是每个探针各写一遍（NFIXPOINT / NFIXPOINT2 是探针版）。
 *
 * <pre>
 *   relT, relF  ← 首解（无 provider）
 *   repeat:
 *      install providers from (relT, relF)
 *      solve ocean  → next
 *      measure dSST and ice-state flips
 *      relT = (1−α)·relT + α·next.T ;  relF = (1−α)·relF + α·next.F   (α = {@link #ALPHA})
 * </pre>
 *
 * <p><b>{@link #ICE_LAG_ON}</b>：冰态改用**上一轮**的值（滞后一格，不做阻尼）。
 * 这是"冰滞后"的一个**无参数**实现（只换状态时序、不引入时间尺度）；
 * 冰-反照率双稳态是否因此消失，由 {@code NCOUPLE} 实测判定。
 *
 * <p>α 是**数值阻尼**，不是物理常数；默认 0.5（与 NFIXPOINT2 相同）。
 * 环本身不改任何方程，只决定"大气看到什么"。
 */
public final class AirSeaCoupling {
    private AirSeaCoupling() {}

    /** 数值阻尼系数（对耦合变量，不是物理参数）。 */
    public static double ALPHA = 0.5;
    /** true：大气读到的是【上一轮】的冰态（滞后一格）；false：读阻尼后的当前值。 */
    public static boolean ICE_LAG_ON = false;

    /** 最近一次 {@link #run} 的结果（探针/门的入口）。 */
    public static Result LAST = null;

    /**
     * ★ A4 门：海气不动点是否收敛。判据全部是**不等式**（浮点里"相等"不可测）。
     * <ol>
     *   <li>{@code dSST 均值 < tolK}（默认 0.1 K，与交接文档的耦合残差判据一致；这是**容差**不是物理常数）；</li>
     *   <li>冰翻转格数**逐轮不增**（单调）；</li>
     *   <li>dSST 最大值有限。</li>
     * </ol>
     * <p>★ 为了证明这个门"有牙"：把 {@code tolK} 收紧到 1e-3 时，本机实测的 0.0036 K 会 FAIL。
     */
    public static boolean gatePass(double tolK) {
        if (LAST == null || LAST.iterations < 1) return false;
        if (!(LAST.dSstMean < tolK)) return false;
        for (int i = 1; i < LAST.iterations; i++)
            if (LAST.flipsIt[i] > LAST.flipsIt[i - 1]) return false;
        return Double.isFinite(LAST.dSstMax);
    }

    /** 门的判据文字（写进探针输出，避免"门是黑箱"）。 */
    public static String gateText(double tolK) {
        if (LAST == null) return "no run";
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(java.util.Locale.ROOT, "mean=%.4f<%.4f=%s",
                LAST.dSstMean, tolK, LAST.dSstMean < tolK));
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < LAST.iterations; i++) {
            if (i > 0) f.append("->");
            f.append(LAST.flipsIt[i]);
        }
        sb.append(" flips=").append(f);
        return sb.toString();
    }

    public static final class Result {
        public OceanSolution solution;
        /** 与上一轮相比的 SST 变化 [K]。 */
        public double dSstMean, dSstMax;
        /** 与上一轮相比，冰覆盖跨过 0.5 的格点数（"整格翻转"的计数）。 */
        public int iceFlips;
        /** 有效湿格数。 */
        public int wetCells;
        /** 最坏格（dSST 最大）的位置与两个状态。 */
        public int worstI, worstJ;
        public double worstTs0, worstTs1, worstF0, worstF1;
        public int iterations;
        /** 逐轮统计（长度 = iterations）。 */
        public double[] dSstMeanIt, dSstMaxIt;
        public int[] flipsIt;
    }

    static int indexX(OceanGrid g, int x) {
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        return Math.max(0, Math.min(g.nx - 1, (int) Math.floor(xx / g.dx)));
    }

    static int indexZ(OceanGrid g, int z) {
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        return Math.max(1, Math.min(g.ny - 2, (int) Math.floor(zz / g.dy)));
    }

    /**
     * 跑不动点环。{@code iterations} 次重解（首解不计）。
     * <p>结束时**卸下 provider**（与 {@code StaticOceanProduction} 的纪律一致：解中不装）。
     */
    public static Result run(int seed, int cell, SeaSurfaceForcing forcing,
                             double theta, int season, int advMaxIter, int iterations) {
        Atmosphere.SST_PROVIDER = null;
        Atmosphere.ICE_PROVIDER = null;
        OceanSolution cur = StaticOceanSolver.solve(seed, cell, 0, 0, forcing, theta, 1, season, advMaxIter);
        OceanGrid g = cur.state.grid;
        final int n2 = g.nx * g.ny;
        final double[] relT = new double[n2];
        final double[] relF = new double[n2];
        final boolean[] wet = new boolean[n2];
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                int js = j * g.nx + i;
                wet[js] = g.isWet(i, j);
                relT[js] = wet[js] ? cur.surfaceT[js] : 0.0;
                relF[js] = wet[js] ? Math.max(0.0, cur.state.iceFraction[js]) : 0.0;
            }

        final Result r = new Result();
        r.solution = cur;
        r.dSstMeanIt = new double[iterations];
        r.dSstMaxIt = new double[iterations];
        r.flipsIt = new int[iterations];
        r.wetCells = 0;
        for (int js = 0; js < n2; js++) if (wet[js]) r.wetCells++;

        for (int it = 1; it <= iterations; it++) {
            final double[] fUsed = new double[n2];
            for (int js = 0; js < n2; js++) fUsed[js] = relF[js];
            Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() {
                @Override public double anomalyAt(int x, int z) {
                    int i = indexX(g, x), j = indexZ(g, z);
                    if (!g.isWet(i, j)) return 0.0;
                    int js = j * g.nx + i;
                    return relT[js] - Atmosphere.seaSurfaceTemp(
                            WorldContract.latOf((int) Math.round(g.zOf(j))), 0.0);
                }
                @Override public double anomalyAt(int x, int z, double th) { return anomalyAt(x, z); }
            };
            Atmosphere.ICE_PROVIDER = new Atmosphere.IceProvider() {
                @Override public double iceFraction(int x, int z) {
                    int i = indexX(g, x), j = indexZ(g, z);
                    return g.isWet(i, j) ? fUsed[j * g.nx + i] : 0.0;
                }
                @Override public double iceThickness(int x, int z) { return 1.0; }
            };
            OceanSolution next = StaticOceanSolver.solve(seed, cell, 0, 0, forcing, theta, 1, season, advMaxIter);

            double sum = 0.0, mx = 0.0;
            int n = 0, flips = 0, mi = -1, mj = -1;
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int js = j * g.nx + i;
                    double d = Math.abs(next.surfaceT[js] - cur.surfaceT[js]);
                    sum += d; n++;
                    if (d > mx) { mx = d; mi = i; mj = j; }
                    double f0 = cur.state.iceFraction[js];
                    double f1 = next.state.iceFraction[js];
                    if ((f0 > 0.5 && f1 <= 0.5) || (f0 <= 0.5 && f1 > 0.5)) flips++;
                }
            r.iterations = it;
            r.dSstMean = n > 0 ? sum / n : 0.0;
            r.dSstMax = mx;
            r.iceFlips = flips;
            r.dSstMeanIt[it - 1] = r.dSstMean;
            r.dSstMaxIt[it - 1] = mx;
            r.flipsIt[it - 1] = flips;
            if (mi >= 0) {
                int js = mj * g.nx + mi;
                r.worstI = mi; r.worstJ = mj;
                r.worstTs0 = cur.surfaceT[js];
                r.worstTs1 = next.surfaceT[js];
                r.worstF0 = cur.state.iceFraction[js];
                r.worstF1 = next.state.iceFraction[js];
            }
            for (int js = 0; js < n2; js++) {
                if (!wet[js]) continue;
                double fNew = Math.max(0.0, next.state.iceFraction[js]);
                relT[js] = (1.0 - ALPHA) * relT[js] + ALPHA * next.surfaceT[js];
                if (ICE_LAG_ON) {
                    // 真滞后一格：下一轮大气看到的是【本轮开始时那个解】的冰态（不追新、不阻尼）。
                    relF[js] = Math.max(0.0, cur.state.iceFraction[js]);
                } else {
                    relF[js] = (1.0 - ALPHA) * relF[js] + ALPHA * fNew;
                }
            }
            cur = next;
        }
        Atmosphere.SST_PROVIDER = null;
        Atmosphere.ICE_PROVIDER = null;
        r.solution = cur;
        LAST = r;
        return r;
    }
}
