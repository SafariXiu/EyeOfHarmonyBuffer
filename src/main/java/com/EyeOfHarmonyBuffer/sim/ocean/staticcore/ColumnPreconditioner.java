package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * ★ 2026-11-23（项目 B / 里程碑 2）：**逐列块预条件子 v1**。
 *
 * <h3>它要近似什么</h3>
 * Newton 解 {@code J·δ = −R}，{@code R = M(x) − x}，{@code J = ∂M/∂x}。
 * 慢（深层）模态满足 {@code J ≈ I − dt·L}，{@code dt·L} 很小 ⟹ {@code I − J} 近似奇异，
 * 无预条件 Krylov 必然停滞（实测：4 次迭代误差几乎不降）。
 *
 * <pre>
 *   慢模态：  J ≈ I − dt·L      ⟹      I − J ≈ dt·L
 *   预条件：  P(r) = 解 L·x = r  （**用 L，不是 I + dt·L**）
 * </pre>
 *
 * <h3>为什么不是平滑算子（CO 报告的教训）</h3>
 * 上一版写成 {@code (I + dt·Dzz)^{-1}}（平滑）⟹ 慢模态被**压小**而不是**放大** ⟹
 * 线搜索找不到下降方向，23 次 map 状态没动。本类的工作点恰好相反：
 * 对深层一个平缓的残差，解出来的修正可以有几十~几千倍的幅度。
 *
 * <h3>算符 L 从哪来</h3>
 * 与 {@link StaticOceanSolver#applyDeepSteady} **同一套面通量公式**
 * （{@code F = w·T_up − K·(T_lo − T_hi)/dzd}，迎风按 w 的符号），
 * 区别只有两点：① 覆盖**整列**（不只 z ≥ 1000 m）；② 表层边界条件：
 * <ul>
 *   <li><b>T</b>：表层按 Dirichlet（修正 δ = 0，即锚在**当前**表层温度）；</li>
 *   <li><b>S</b>：表层 Neumann（本算符里没有表面盐通量项）。
 *       若整列矩阵奇异（w ≈ 0 ⟹ 两端 Neumann 的纯扩散），该列**退回恒等**并计入
 *       {@link #LAST_SKIP}（A3-⑦：不允许为了"能解"而塞正则化常数）。</li>
 * </ul>
 *
 * <h3>接线状态（A3-④）</h3>
 * ★ **未接线**：生产路径不调用本类，默认行为逐位不变，因此**不进 configStamp**。
 * M4（生产接线）把 {@code AirSeaCoupling} 换成平衡解求解时必须折进 {@code StaticOceanProduction.solveFlagsHash}。
 *
 * <h3>验收判据（里程碑 2 的三个单测，见 probe/EQPRECON）</h3>
 * <ol>
 *   <li>放大性：纯深层残差 {@code ‖P(r)‖/‖r‖ ≫ 1}（≈1 或 &lt;1 ⟹ 方向仍错）；</li>
 *   <li>一致性：对真实残差，{@code (I−J)·P(r) ≈ r}（有限差分；相对误差 &lt; 50%）；</li>
 *   <li>成本：整场一次 {@code apply} &lt; 0.2 s。</li>
 * </ol>
 */
public final class ColumnPreconditioner {

    private ColumnPreconditioner() {}

    /** 最近一次 apply 里因为矩阵奇异而**退回恒等**的列数（诊断）。 */
    public static long LAST_SKIP = 0;
    /** 最近一次 apply 实际处理的湿列数（诊断）。 */
    public static long LAST_COLS = 0;

    /**
     * 对整场残差做一次预条件：{@code dT ← P(rT)}，{@code dS ← P(rS)}。
     *
     * @param g  网格
     * @param st 当前状态（只读：只取 w / S 用于算子系数与 Dirichlet 值）
     * @param rT T 残差（n3，K/年 之类的量纲无关；Krylov 只关心方向）
     * @param rS S 残差（n3）
     * @param dT 输出：T 的修正（n3）
     * @param dS 输出：S 的修正（n3）
     */
    public static void apply(OceanGrid g, OceanState st,
                             double[] rT, double[] rS, double[] dT, double[] dS) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] A = new double[nz * nz];
        double[] b = new double[nz];
        double[] x = new double[nz];
        double[] w = new double[nz];
        double[] kv = new double[nz];
        LAST_SKIP = 0;
        LAST_COLS = 0;
        java.util.Arrays.fill(dT, 0.0);
        java.util.Arrays.fill(dS, 0.0);
        for (int i = 0; i < nx; i++) {
            for (int j = 1; j < ny - 1; j++) {
                if (!g.isWet(i, j)) continue;
                int m = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                if (m <= 0) continue;
                for (int k = 0; k < m; k++) {
                    int q = st.idx(i, j, k);
                    w[k] = st.w[q];
                    kv[k] = AdvectionDiffusionSolver.kvAt(g, st, i, j, k);
                }
                LAST_COLS++;
                boolean okT = solveColumn(g, i, j, m, w, kv, rT, dT, A, b, x, true);
                boolean okS = solveColumn(g, i, j, m, w, kv, rS, dS, A, b, x, false);
                if (!okT || !okS) LAST_SKIP++;
            }
        }
    }

    /**
     * ★ 2026-11-23（M3 决策点 D）：**分块（掩膜）预条件** —— 快模态保持恒等、慢模态用深层 L⁻¹。
     *
     * <p>动机（CT 报告的根因）：{@code I−J} 的谱横跨快（表层通量/冰/对流，{@code I−J ≈ 1}）
     * 与慢（深层，{@code I−J ≈ dt·L}）两类模态；v1 对**所有**模态都乘 {@code L⁻¹}，
     * 于是步长被深层支配、改不动由表层主导的 rmsT。本方法把两类模态分开：
     * <ul>
     *   <li>深度 {@code z < DEEP_STEADY_Z0}：直接取 δ = r（快模态的正确 Newton 步）；</li>
     *   <li>深度 {@code z ≥ DEEP_STEADY_Z0}：解 {@code L_deep·δ = dz·r/dt}（dt = 一年），
     *       即 {@code δ = (I−J)⁻¹ r} 的物理量纲版本（A 是 [m/s]，乘 dz/dt 才是 [1/s]）。</li>
     * </ul>
     * <p>阈值复用生产常量 {@link StaticOceanSolver#DEEP_STEADY_Z0}（**不新建常数**）。
     * <p>只作用于 T；S 保持恒等（S 的表层 Neumann 算子可能奇异，见 {@link #LAST_SKIP}）。
     *
     * @param dtSec 一年 = {@code WorldContract.DAYS_PER_YEAR * 86400}（把 K/年 变成 K/s 的通量尺度）
     */
    public static void applyMasked(OceanGrid g, OceanState st,
                                   double[] rT, double[] rS, double[] dT, double[] dS,
                                   double dtSec) {
        System.arraycopy(rT, 0, dT, 0, rT.length);
        System.arraycopy(rS, 0, dS, 0, rS.length);
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] A = new double[nz * nz];
        double[] b = new double[nz];
        double[] x = new double[nz];
        double[] w = new double[nz];
        double[] kv = new double[nz];
        LAST_SKIP = 0;
        LAST_COLS = 0;
        double z0 = StaticOceanSolver.DEEP_STEADY_Z0;
        for (int i = 0; i < nx; i++) {
            for (int j = 1; j < ny - 1; j++) {
                if (!g.isWet(i, j)) continue;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                int k0 = -1;
                for (int k = 0; k < wl; k++) if (g.zCenter[k] >= z0) { k0 = k; break; }
                if (k0 <= 0 || wl - k0 < 2) continue;
                int m = wl - k0;
                for (int k = 0; k < wl; k++) {
                    w[k] = st.w[st_idx(g, i, j, k)];
                    kv[k] = AdvectionDiffusionSolver.kvAt(g, st, i, j, k);
                }
                LAST_COLS++;
                java.util.Arrays.fill(A, 0.0);
                for (int a = 0; a < m; a++) {
                    int k = k0 + a;
                    double wf = 0.5 * (w[k - 1] + w[k]);
                    double dzd = 0.5 * (g.dz[k - 1] + g.dz[k]);
                    double kk = 0.5 * (kv[k - 1] + kv[k]);
                    double cLo, cHi;
                    if (wf > 0.0) { cLo = -kk / dzd; cHi = wf + kk / dzd; }
                    else          { cLo = wf - kk / dzd; cHi = kk / dzd; }
                    if (k - 1 >= k0) A[a * m + (k - 1 - k0)] += cLo;   // 表层及以上：δ=0（Dirichlet）
                    A[a * m + (k - k0)] += cHi;
                    if (k + 1 < wl) {
                        double wf2 = 0.5 * (w[k] + w[k + 1]);
                        double dzd2 = 0.5 * (g.dz[k] + g.dz[k + 1]);
                        double kk2 = 0.5 * (kv[k] + kv[k + 1]);
                        double cLo2, cHi2;
                        if (wf2 > 0.0) { cLo2 = -kk2 / dzd2; cHi2 = wf2 + kk2 / dzd2; }
                        else           { cLo2 = wf2 - kk2 / dzd2; cHi2 = kk2 / dzd2; }
                        A[a * m + (k - k0)] -= cLo2;
                        A[a * m + (k + 1 - k0)] -= cHi2;
                    }
                    b[a] = g.dz[k] * rT[st_idx(g, i, j, k)] / dtSec;
                }
                if (!solveDense(A, b, x, m)) { LAST_SKIP++; continue; }
                for (int a = 0; a < m; a++) dT[st_idx(g, i, j, k0 + a)] = x[a];
            }
        }
    }

    /**
     * 单列：装配 {@code L·δ = r} 并解出 δ 写回 {@code out}。
     * Dirichlet 边界（T 的表层）在修正里取 0 ⟹ 只贡献对角项。
     */
    private static boolean solveColumn(OceanGrid g, int i, int j, int m,
                                       double[] w, double[] kv,
                                       double[] r, double[] out,
                                       double[] A, double[] b, double[] x,
                                       boolean surfaceDirichlet) {
        java.util.Arrays.fill(A, 0.0);
        for (int k = 0; k < m; k++) b[k] = r[st_idx(g, i, j, k)];
        // 内部面：k-1 与 k 之间
        for (int k = 1; k < m; k++) {
            double wf = 0.5 * (w[k - 1] + w[k]);
            double dzd = 0.5 * (g.dz[k - 1] + g.dz[k]);
            double kk = 0.5 * (kv[k - 1] + kv[k]);
            double cLo, cHi;
            if (wf > 0.0) { cLo = -kk / dzd; cHi = wf + kk / dzd; }
            else          { cLo = wf - kk / dzd; cHi = kk / dzd; }
            // 上行：+F 进 k 行
            A[k * m + (k - 1)] += cLo;
            A[k * m + k] += cHi;
            // 下行：−F 进 k−1 行
            A[(k - 1) * m + (k - 1)] -= cLo;
            A[(k - 1) * m + k] -= cHi;
        }
        // 表层（k=0 的上方）：T = Dirichlet（δ=0），S = Neumann（无项）
        if (surfaceDirichlet) {
            double wf = w[0];
            double dzd = 0.5 * g.dz[0];
            double kk = kv[0];
            double cHi = (wf > 0.0) ? (wf + kk / dzd) : (kk / dzd);
            A[0] += cHi;                       // 行 0 的对角
        }
        if (!solveDense(A, b, x, m)) return false;
        for (int k = 0; k < m; k++) out[st_idx(g, i, j, k)] = x[k];
        return true;
    }

    /** 状态数组的线性下标：与 {@link OceanState#idx(int, int, int)} 同序。 */
    private static int st_idx(OceanGrid g, int i, int j, int k) {
        return (k * g.ny + j) * g.nx + i;
    }

    /** 小规模稠密解（部分主元）；奇异返回 false（调用方保持原值）。 */
    private static boolean solveDense(double[] A, double[] b, double[] x, int m) {
        for (int c = 0; c < m; c++) {
            int piv = c;
            double best = Math.abs(A[c * m + c]);
            for (int r = c + 1; r < m; r++) {
                double v = Math.abs(A[r * m + c]);
                if (v > best) { best = v; piv = r; }
            }
            if (!(best > 1.0e-30)) return false;
            if (piv != c) {
                for (int k2 = 0; k2 < m; k2++) {
                    double tmp = A[c * m + k2]; A[c * m + k2] = A[piv * m + k2]; A[piv * m + k2] = tmp;
                }
                double tb = b[c]; b[c] = b[piv]; b[piv] = tb;
            }
            for (int r = c + 1; r < m; r++) {
                double f = A[r * m + c] / A[c * m + c];
                if (f == 0.0) continue;
                for (int k2 = c; k2 < m; k2++) A[r * m + k2] -= f * A[c * m + k2];
                b[r] -= f * b[c];
            }
        }
        for (int r = m - 1; r >= 0; r--) {
            double s = b[r];
            for (int c = r + 1; c < m; c++) s -= A[r * m + c] * x[c];
            x[r] = s / A[r * m + r];
        }
        return true;
    }
}
