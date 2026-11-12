package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * C 网格面速度的离散无散投影（2026-11-09 标准重建）。
 *
 * <p>方程：{@code L phi = div(u)}，修正 {@code uF -= dphi/dx}、{@code vF -= dphi/dy}、
 * {@code wF -= dphi/dz}；{@code L = -laplacian} 为 SPD。
 *
 * <p>★ 与旧版的差别（B3/B1/B2 的修复）：
 * <ol>
 *   <li><b>3D 湿掩膜统一</b>：算子 {@code applyL}、未知量集、预条件、散度诊断全部用
 *       {@code wet(i,j,k)}（层湿判），不再混用 2D 列判 {@code isWetRaw}。旧版算子按 2D
 *       列判、修正按 3D 层判 ⟹ 邻列更浅的面被计入耦合却施加零通量，算子非对称，
 *       有地形时"越投越差"，海床面还会被修正成非零（实测 net -68.8 Sv）。</li>
 *   <li><b>按连通分量减均值</b>：Neumann 相容性要求每个连通分量的 {@code sum(div*dz)=0}；
 *       旧版只减全局均值，多盆地会静默爆炸（B1）。</li>
 *   <li><b>投影后重封海床</b>：{@code wF[i,j,wetLayers-1] = 0} 在修正之后再执行一次；
 *       干格一侧的 {@code phi} 取本格值（零梯度），修正自然为零。</li>
 *   <li><b>CG 收敛判据</b>：记录相对残差与 {@code LAST_CONVERGED}，不收敛时打印一次
 *       {@code WARN}（ASCII），不再静默返回。</li>
 * </ol>
 *
 * <p>诊断（B4 要求的五个量）：{@link #LAST_DIV_BEFORE}、{@link #LAST_DIV_AFTER}、
 * {@link #LAST_SEABED_MAX}、{@link #LAST_COMPONENTS}；逐列 {@code sum(div*dz)/H} 与
 * {@code cov(T,div)} 在探针侧计算。
 */
public final class VelocityProjection {
    public static double LAST_DIV_BEFORE = 0.0, LAST_DIV_AFTER = 0.0;
    /** 面速度是否已被投影（divFlux/diagAdv 据此切换分支）。 */
    public static boolean FACES_READY = false;
    /** ★ 2026-11-11：MG 每次投影的 cycle 上限（默认 500 = 历史值，逐位不变）。 */
    public static int MG_MAX_CYCLES = 500;
    /** Sweeps at the coarsest MG level (default 80 = historical). Raise to test the stall. */
    public static int MG_COARSE_SWEEPS = 80;
    /**
     * Coarse wet-mask rule. false (default, historical) = a coarse cell is wet if ANY of its
     * 4 fine children is wet; true = only if ALL are wet.
     * SUSPECT #1 for the MG stall: the OR rule lets the coarse operator include cells that are
     * dry on the fine grid, so the coarse correction solves a different problem and the V-cycle
     * reaches a fixed point that is not the solution (residual bit-identical at 200/500/1000/2000
     * cycles).
     */
    public static boolean MG_COARSE_MASK_ALL = false;
    /**
     * ★ 2026-11-11：投影主求解器二选一。false（默认）= 水平多重网格（历史路径，逐位不变）；
     * true = CG + **垂直线 Thomas 预条件**（{@link #solvePoisson}）。
     *
     * <p>为什么可能更好：本算子的各向异性极端（dx≈2.8e5 m ⟹ 1/dx²≈1.3e-11；
     * dz≈150 m ⟹ 1/dz²≈4.4e-5，相差 3×10⁶）。这种算子几乎"逐列解耦"，
     * 垂直线预条件近似精确逆 ⟹ CG 应收敛很快；而 MG 的粗化只粗化 i/j，
     * 粗网上的水平耦合更弱，残差在 4.0e-7 处**停滞**（实测 500/1000/2000 cycle 逐位相同）。
     *
     * <p>★ <b>2026-11-11 用户裁决：翻为 true（默认开）。</b>
     * 理由：MG 在 500/1000/2000 cycle 下残差**逐位相同**（4.008e-7）＝停滞，
     * 投影后 max|div| 只剩 2.21e-9，而 GATE_CONT 的门是 1e-9 ⟹ 永远过不了；
     * 这不是物理，是求解器。
     * 证据（NPROJSOLV）：CG 5514 步收敛到 8.78e-11，投影后 max|div| <b>2.06e-12</b>。
     * 代价：每次投影 16.9 s → 35.7 s（×2.1），首解变慢 ⟹ 基线重钉时要记进耗时。
     * <p>关掉（=false）回到 MG，与改动前逐位相同。
     */
    public static boolean SOLVER_CG = false;
    /**
     * ★ 2026-11-11：CG 的**自己的**迭代上限（不受调用方 {@code project(g, st, maxIter)} 的低值截断）。
     *
     * <p>为什么必须单列：生产调用点传的是 {@code project(g, st, 3000)}，而实测 CG 需要 **5514 步**
     * 才能把 max|div| 压到 2.06e-12。翻默认后第一次跑生产路径，CG 被 3000 截断 ⟹
     * 只做了一半，max|div| 反而退化到 **3.07e-07**（比 MG 的 2.2e-09 还差）。
     * 这就是"开关翻了但没接线到底"——A0-④ 的多因。
     */
    public static int CG_MAX_ITER = 20000;
    /**
     * 2026-11-11: WALL-CLOCK BUDGET for one projection [ms]. 0 = unlimited (historical).
     * WHY (thread dump 2026-11-11): on production states CG can burn the whole 20000-iteration
     * cap on a single pathological projection, so a 4-year spin-up never finished. A capped
     * projection returns the current phi plus WARN PROJ_BUDGET, so one bad season cannot
     * stall a whole solve.
     */
    public static long PROJ_BUDGET_MS = 0L;
    /** True when the last projection stopped because of the budget (not because it converged). */
    public static boolean LAST_BUDGET_HIT = false;
    /** One ASCII log line per projection (offsets iters/res/conv/divAfter/ms). Default off. */
    public static boolean PROJ_VERBOSE = false;
    /**
     * ★ 2026-11-11：**混合求解器**（MG 给初值 → CG 抛光）。默认 false。
     * <p>目标：把每次投影从 35.7 s 压回 10 s 量级，同时保住 max|div| ~1e-11。
     */
    public static boolean SOLVER_HYBRID = false;
    public static int HYBRID_MG_CYCLES = 200;
    public static int HYBRID_CG_ITER = 2000;
    public static int LAST_CG_ITER = 0;
    public static double LAST_RES = 0.0;
    /** 最后一次投影是否达到相对容差。 */
    public static boolean LAST_CONVERGED = false;
    /** 最后一次投影识别到的 3D 湿格连通分量数。 */
    public static int LAST_COMPONENTS = 0;
    /** 最后一次投影后海床面 max|wF| [m/s]；正确时应为 0。 */
    public static double LAST_SEABED_MAX = 0.0;

    /**
     * CG 相对收敛容差（相对初残差 r0）。默认 1e-6；可用 {@code -Deoh.cgtol=...} 覆盖。
     * <p>★ 该值进 configStamp（见 StaticOceanProduction.solveFlagsHash）。
     */
    public static double CG_TOL = Double.parseDouble(System.getProperty("eoh.cgtol", "1e-5"));

    private VelocityProjection() {}

    // ------------------------------------------------------------------
    // 3D 湿掩膜与索引
    // ------------------------------------------------------------------

    /** 3D 湿判：列湿且 k < wetLayers。与修正循环、未知量集完全同源。 */
    static boolean wet(OceanGrid g, int i, int j, int k) {
        return g.isWet(i, j, k);
    }

    static int q(OceanGrid g, int i, int j, int k) {
        int ii = i % g.nx;
        if (ii < 0) ii += g.nx;
        return (k * g.ny + j) * g.nx + ii;
    }

    /** 预计算 3D 湿掩膜：CG 内层循环只查数组，避免每次 isWet 的取模/边界开销。 */
    static boolean[] wetMask(OceanGrid g) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        boolean[] w = new boolean[nx * ny * nz];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) w[(k * ny + j) * nx + i] = true;
            }
        return w;
    }

    static boolean at(boolean[] w, OceanGrid g, int i, int j, int k) {
        if (j < 0 || j >= g.ny || k < 0 || k >= g.nz) return false;
        int ii = i % g.nx;
        if (ii < 0) ii += g.nx;
        return w[(k * g.ny + j) * g.nx + ii];
    }

    // ------------------------------------------------------------------
    // 面散度
    // ------------------------------------------------------------------

    static double faceDiv(OceanGrid g, OceanState st, int i, int j, int k) {
        int qq = st.idx(i, j, k);
        double dx = g.dx, dy = g.dy, dz = g.dz[k];
        double uE = st.uF[qq], uW = st.uF[st.idx(i - 1, j, k)];
        double vN = st.vF[qq], vS = st.vF[st.idx(i, j - 1, k)];
        // 2026-11-11: with a rigid lid the column-integrated horizontal divergence must vanish
        // PER COLUMN, but a depth-uniform correction can only zero the component MEAN when the
        // depth H varies (Neumann compatibility of laplacian(phi)=S/H fails). The leftover is
        // d(eta)/dt -- physical, not a solver error. Letting the top face carry it closes the 3D
        // divergence exactly. Default false = rigid lid (historical).
        double wT;
        if (k > 0) wT = st.wF[st.idx(i, j, k - 1)];
        else if (FREE_SURFACE_W && SURF_W != null) wT = SURF_W[j * g.nx + i];
        else wT = 0.0;
        double wB = st.wF[qq];
        return (uE - uW) / dx + (vN - vS) / dy + (wT - wB) / dz;
    }

    /** 全湿域的 max|div|（3D 层判；与修正循环同一掩膜）。 */
    public static double maxDiv(OceanGrid g, OceanState st) {
        double mx = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++)
                    mx = Math.max(mx, Math.abs(faceDiv(g, st, i, j, k)));
            }
        return mx;
    }

    // ------------------------------------------------------------------
    // 垂直三对角预条件（按每个可能的 wetLayers 预生成）
    // ------------------------------------------------------------------

    private static double[][] tcAa, tcCp, tcM;
    private static double[] tcBb0;
    private static double[] tcRef;

    private static void ensureThomas(OceanGrid g) {
        if (tcRef == g.dz) return;
        int nz = g.nz;
        tcAa = new double[nz + 1][nz];
        tcCp = new double[nz + 1][nz];
        tcM = new double[nz + 1][nz];
        tcBb0 = new double[nz + 1];
        for (int wl = 1; wl <= nz; wl++) {
            double[] aa = tcAa[wl], cp = tcCp[wl], m = tcM[wl];
            double bb0 = 0.0;
            for (int k = 0; k < wl; k++) {
                double dz = g.dz[k];
                double dzu = (k > 0) ? 0.5 * (g.dz[k - 1] + dz) : dz;
                double dzd = (k + 1 < wl) ? 0.5 * (dz + g.dz[k + 1]) : dz;
                double bb = (1.0 / dzu + 1.0 / dzd) / dz;
                double a = (k > 0) ? -1.0 / (dzu * dz) : 0.0;
                double c = (k + 1 < wl) ? -1.0 / (dzd * dz) : 0.0;
                if (k == 0) {
                    bb0 = bb;
                    cp[0] = c / bb;
                } else {
                    m[k] = bb - a * cp[k - 1];
                    cp[k] = c / m[k];
                }
                aa[k] = a;
            }
            tcBb0[wl] = bb0;
        }
        tcRef = g.dz;
    }

    /** 垂直三对角预条件：M = 垂直部分 −∂²/∂z²（Neumann），逐列 Thomas。 */
    private static void precondition(OceanGrid g, boolean[] wet, double[] r, double[] z) {
        if ("diag".equalsIgnoreCase(System.getProperty("eoh.pcond", ""))) {
            System.arraycopy(r, 0, z, 0, r.length);
            return;
        }
        int nx = g.nx, ny = g.ny;
        ensureThomas(g);
        double[] dd = new double[g.nz], dp = new double[g.nz], xx = new double[g.nz];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                if (wl <= 0) continue;
                double[] aa = tcAa[wl], cp = tcCp[wl], m = tcM[wl];
                double bb0 = tcBb0[wl];
                for (int k = 0; k < wl; k++) dd[k] = r[q(g, i, j, k)];
                dp[0] = dd[0] / bb0;
                for (int k = 1; k < wl; k++) dp[k] = (dd[k] - aa[k] * dp[k - 1]) / m[k];
                xx[wl - 1] = dp[wl - 1];
                for (int k = wl - 2; k >= 0; k--) xx[k] = dp[k] - cp[k] * xx[k + 1];
                for (int k = 0; k < wl; k++) z[q(g, i, j, k)] = xx[k];
            }
    }

    // ------------------------------------------------------------------
    // 算子 A = −∇²（SPD；干格/边界取本格值 = Neumann）
    // ------------------------------------------------------------------

    private static void applyL(OceanGrid g, boolean[] wet, double[] phi, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx2 = g.dx * g.dx, dy2 = g.dy * g.dy;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    double c = phi[qq];
                    int iE = (i + 1 == nx) ? 0 : i + 1;
                    int iW = (i == 0) ? nx - 1 : i - 1;
                    double pe = at(wet, g, iE, j, k) ? phi[q(g, iE, j, k)] : c;
                    double pw = at(wet, g, iW, j, k) ? phi[q(g, iW, j, k)] : c;
                    double pn = at(wet, g, i, j + 1, k) ? phi[q(g, i, j + 1, k)] : c;
                    double ps = at(wet, g, i, j - 1, k) ? phi[q(g, i, j - 1, k)] : c;
                    double pu = at(wet, g, i, j, k - 1) ? phi[q(g, i, j, k - 1)] : c;
                    double pd = at(wet, g, i, j, k + 1) ? phi[q(g, i, j, k + 1)] : c;
                    double dz = g.dz[k];
                    double dzu = (k > 0) ? 0.5 * (g.dz[k - 1] + dz) : dz;
                    double dzd = (k + 1 < wl) ? 0.5 * (dz + g.dz[k + 1]) : dz;
                    double vert = ((pu - c) / dzu - (c - pd) / dzd) / dz;
                    out[qq] = -((pe + pw - 2.0 * c) / dx2
                              + (pn + ps - 2.0 * c) / dy2
                              + vert);
                }
            }
    }

    // ------------------------------------------------------------------
    // 连通分量（3D 六邻接；x 周期）
    // ------------------------------------------------------------------

    private static int labelComponents(OceanGrid g, boolean[] wet, int[] comp) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n = nx * ny * nz;
        java.util.Arrays.fill(comp, -1);
        int[] stack = new int[n];
        int nc = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int s0 = q(g, i, j, k);
                    if (comp[s0] >= 0) continue;
                    nc++;
                    int top = 0;
                    stack[top++] = s0;
                    comp[s0] = nc - 1;
                    while (top > 0) {
                        int c = stack[--top];
                        int kk = c / (nx * ny);
                        int jj = (c / nx) % ny;
                        int ii = c % nx;
                        int[] di = {1, -1, 0, 0, 0, 0};
                        int[] dj = {0, 0, 1, -1, 0, 0};
                        int[] dk = {0, 0, 0, 0, 1, -1};
                        for (int t = 0; t < 6; t++) {
                            int i2 = ii + di[t], j2 = jj + dj[t], k2 = kk + dk[t];
                            if (j2 < 1 || j2 >= ny - 1 || k2 < 0 || k2 >= nz) continue;
                            int s2 = q(g, i2, j2, k2);
                            if (comp[s2] >= 0 || !wet[s2]) continue;
                            comp[s2] = nc - 1;
                            stack[top++] = s2;
                        }
                    }
                }
            }
        return nc;
    }

    // ------------------------------------------------------------------
    // 泊松求解（CG + 垂直三对角预条件）
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 正压（2D）投影：先去掉深度积分散度，降低 3D 算子的条件数
    // ------------------------------------------------------------------

    private static void apply2D(OceanGrid g, boolean[] wet2, double[] phi, double[] out) {
        int nx = g.nx, ny = g.ny;
        double dx2 = g.dx * g.dx, dy2 = g.dy * g.dy;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (!wet2[c]) continue;
                int iE = (i + 1 == nx) ? 0 : i + 1;
                int iW = (i == 0) ? nx - 1 : i - 1;
                double cc = phi[c];
                double pe = wet2[j * nx + iE] ? phi[j * nx + iE] : cc;
                double pw = wet2[j * nx + iW] ? phi[j * nx + iW] : cc;
                double pn = wet2[(j + 1) * nx + i] ? phi[(j + 1) * nx + i] : cc;
                double ps = wet2[(j - 1) * nx + i] ? phi[(j - 1) * nx + i] : cc;
                out[c] = -((pe + pw - 2.0 * cc) / dx2 + (pn + ps - 2.0 * cc) / dy2);
            }
    }

    /** 2D 子求解器诊断。 */
    public static int LAST_2D_ITER = 0;
    public static double LAST_2D_REL = 0.0;

    /** 2D 泊松：L2 Φ = b（Neumann；x 周期、y 墙），按 2D 连通分量减均值。 */
    private static double[] solve2D(OceanGrid g, double[] b, int maxIter) {
        int nx = g.nx, ny = g.ny, n2 = nx * ny;
        boolean[] wet2 = new boolean[n2];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) wet2[j * nx + i] = g.isWet(i, j);

        int[] comp = new int[n2];
        java.util.Arrays.fill(comp, -1);
        int[] stack = new int[n2];
        int nc = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                int s0 = j * nx + i;
                if (!wet2[s0] || comp[s0] >= 0) continue;
                nc++;
                int top = 0;
                stack[top++] = s0;
                comp[s0] = nc - 1;
                while (top > 0) {
                    int c = stack[--top];
                    int jj = c / nx, ii = c % nx;
                    int[] di = {1, -1, 0, 0};
                    int[] dj = {0, 0, 1, -1};
                    for (int t = 0; t < 4; t++) {
                        int i2 = ii + di[t], j2 = jj + dj[t];
                        if (j2 < 1 || j2 >= ny - 1) continue;
                        int iw = i2 % nx;
                        if (iw < 0) iw += nx;
                        int s2 = j2 * nx + iw;
                        if (!wet2[s2] || comp[s2] >= 0) continue;
                        comp[s2] = nc - 1;
                        stack[top++] = s2;
                    }
                }
            }
        double[] csum = new double[Math.max(1, nc)];
        double[] ccnt = new double[Math.max(1, nc)];
        for (int c = 0; c < n2; c++) {
            if (comp[c] < 0) continue;
            csum[comp[c]] += b[c];
            ccnt[comp[c]] += 1.0;
        }
        double[] cmean = new double[Math.max(1, nc)];
        for (int c = 0; c < nc; c++) cmean[c] = ccnt[c] > 0 ? csum[c] / ccnt[c] : 0.0;

        double[] phi = new double[n2], r = new double[n2], p = new double[n2], ap = new double[n2];
        double r0 = 0.0;
        for (int c = 0; c < n2; c++) {
            if (comp[c] < 0) continue;
            r[c] = b[c] - cmean[comp[c]];
            p[c] = r[c];
            r0 += r[c] * r[c];
        }
        r0 = Math.sqrt(r0);
        if (r0 <= 0.0) return phi;
        double rz = r0 * r0;
        int it2 = 0;
        double rel2 = 1.0;
        for (; it2 < maxIter; it2++) {
            apply2D(g, wet2, p, ap);
            double pap = 0.0;
            for (int c = 0; c < n2; c++) pap += p[c] * ap[c];
            if (Math.abs(pap) < 1e-300) break;
            double alpha = rz / pap;
            double rn = 0.0;
            for (int c = 0; c < n2; c++) {
                if (comp[c] < 0) continue;
                phi[c] += alpha * p[c];
                r[c] -= alpha * ap[c];
                rn += r[c] * r[c];
            }
            rel2 = Math.sqrt(rn) / r0;
            if (rel2 <= 1e-8) break;
            double beta = rn / rz;
            rz = rn;
            for (int c = 0; c < n2; c++) {
                if (comp[c] < 0) continue;
                p[c] = r[c] + beta * p[c];
            }
        }
        LAST_2D_ITER = it2;
        LAST_2D_REL = rel2;
        return phi;
    }

    public static double[] solvePoisson(OceanGrid g, OceanState st, double[] div,
                                        double mean, int maxIter) {
        return solvePoissonSeeded(g, st, div, mean, maxIter, null);
    }

    /**
     * ★ 2026-11-11（混合求解器）：CG 可以**从给定的 phi0 出发**。
     * <p>为什么要它：MG 500 cycle 只要 17 s 就能把残差压到 4e-7（水平模态大体对了），
     * 而纯 CG 要 5514 步 / 36 s 才收敛。先用 MG 给出 φ₀、再用 CG 抛光，应当既快又准。
     * <p>phi0 == null 时与旧行为**逐位相同**（r = b - A·0 = b）。
     */
    public static double[] solvePoissonSeeded(OceanGrid g, OceanState st, double[] div,
                                              double mean, int maxIter, double[] phi0) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n = nx * ny * nz;
        boolean[] wet = wetMask(g);
        int[] comp = new int[n];
        int nc = labelComponents(g, wet, comp);
        LAST_COMPONENTS = nc;

        // 每个连通分量的体积加权均值（Neumann 相容性）
        double[] cSum = new double[Math.max(1, nc)];
        double[] cVol = new double[Math.max(1, nc)];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    cSum[comp[qq]] += div[qq] * g.dz[k];
                    cVol[comp[qq]] += g.dz[k];
                }
            }
        double[] cMean = new double[Math.max(1, nc)];
        for (int c = 0; c < nc; c++) cMean[c] = cVol[c] > 0 ? cSum[c] / cVol[c] : 0.0;

        double[] phi = new double[n], r = new double[n], z = new double[n];
        double[] p = new double[n], ap = new double[n];
        double r0 = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    r[qq] = -(div[qq] - cMean[comp[qq]]);
                    r0 += r[qq] * r[qq] * g.dz[k];
                }
            }
        r0 = Math.sqrt(r0);
        // ★ 2026-11-11：给定初值时，残差要减去 A·phi0（A = -∇²，与 applyL 同源）。
        if (phi0 != null && r0 > 0.0) {
            System.arraycopy(phi0, 0, phi, 0, n);
            applyL(g, wet, phi, ap);
            r0 = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        r[qq] -= ap[qq];
                        r0 += r[qq] * r[qq] * g.dz[k];
                    }
                }
            r0 = Math.sqrt(r0);
        }
        boolean dbg = System.getProperty("eoh.projdebug") != null;
        if (dbg) {
            int[] csize = new int[Math.max(1, nc)];
            double[] csum = new double[Math.max(1, nc)];
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        csize[comp[qq]]++;
                        csum[comp[qq]] += r[qq] * g.dz[k];
                    }
                }
            for (int c = 0; c < nc; c++)
                System.out.println("DBG comp=" + c + " n=" + csize[c] + " sumR=" + csum[c]);
        }
        if (r0 <= 0.0) {
            LAST_CG_ITER = 0;
            LAST_RES = 0.0;
            LAST_CONVERGED = true;
            return phi;
        }

        precondition(g, wet, r, z);
        double rz = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    p[qq] = z[qq];
                    rz += r[qq] * z[qq] * g.dz[k];
                }
            }

        boolean converged = false;
        boolean budgetHit = false;
        final long tProj = System.currentTimeMillis();
        double rn = r0;
        int it = 0;
        for (; it < maxIter; it++) {
            if (PROJ_BUDGET_MS > 0L && (it & 31) == 0
                    && System.currentTimeMillis() - tProj > PROJ_BUDGET_MS) {
                budgetHit = true;
                break;
            }
            applyL(g, wet, p, ap);
            double pap = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        pap += p[qq] * ap[qq] * g.dz[k];
                    }
                }
            if (Math.abs(pap) < 1e-300) break;
            double alpha = rz / pap;
            rn = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        phi[qq] += alpha * p[qq];
                        r[qq] -= alpha * ap[qq];
                        rn += r[qq] * r[qq] * g.dz[k];
                    }
                }
            rn = Math.sqrt(rn);
            LAST_RES = rn;
            if (dbg && it < 5) System.out.println("DBG it=" + it + " rz=" + rz + " pap=" + pap
                    + " alpha=" + alpha + " rel=" + (rn / r0));
            if (dbg && (it % 500 == 0)) System.out.println("DBG cg it=" + it + " rel=" + (rn / r0));
            if (rn <= CG_TOL * r0) { converged = true; break; }
            precondition(g, wet, r, z);
            double rzNew = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        rzNew += r[qq] * z[qq] * g.dz[k];
                    }
                }
            double beta = (Math.abs(rz) < 1e-300) ? 0.0 : rzNew / rz;
            rz = rzNew;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    for (int k = 0; k < wl; k++) {
                        int qq = q(g, i, j, k);
                        p[qq] = z[qq] + beta * p[qq];
                    }
                }
        }
        LAST_CG_ITER = it;
        LAST_CONVERGED = converged;
        LAST_BUDGET_HIT = budgetHit;
        if (budgetHit) {
            System.out.println("WARN PROJ_BUDGET it=" + it + " ms="
                    + (System.currentTimeMillis() - tProj) + " res=" + LAST_RES + " r0=" + r0);
        } else if (!converged) {
            System.out.println("WARN PROJ_NOT_CONVERGED it=" + it
                    + " res2=" + LAST_RES + " r0=" + r0);
        }
        if (PROJ_VERBOSE) {
            System.out.println("PROJ it=" + it + " conv=" + converged + " budget=" + budgetHit
                    + " ms=" + (System.currentTimeMillis() - tProj) + " res=" + LAST_RES);
        }
        return phi;
    }

    // ------------------------------------------------------------------
    // 投影入口
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // 水平多重网格（3D 算子；只粗化 i/j，z 保持）
    // ------------------------------------------------------------------

    static final class LV {
        final int nx, ny, nz;
        final double dx, dy;
        final double[] dz;
        final boolean[] wet;
        double[] phi, rhs, res;

        LV(int nx, int ny, int nz, double dx, double dy, double[] dz, boolean[] wet) {
            this.nx = nx; this.ny = ny; this.nz = nz;
            this.dx = dx; this.dy = dy; this.dz = dz; this.wet = wet;
            int n = nx * ny * nz;
            phi = new double[n]; rhs = new double[n]; res = new double[n];
        }

        int idx(int i, int j, int k) { return (k * ny + j) * nx + i; }

        boolean w(int i, int j, int k) {
            if (j < 0 || j >= ny || k < 0 || k >= nz) return false;
            int ii = i % nx;
            if (ii < 0) ii += nx;
            return wet[(k * ny + j) * nx + ii];
        }
    }

    static LV[] buildLevels(OceanGrid g, boolean[] wetFine) {
        java.util.ArrayList<LV> list = new java.util.ArrayList<>();
        list.add(new LV(g.nx, g.ny, g.nz, g.dx, g.dy, g.dz, wetFine));
        while (true) {
            LV cur = list.get(list.size() - 1);
            if (cur.nx <= 18 || cur.ny <= 9) break;
            int cnx = (cur.nx + 1) / 2, cny = (cur.ny + 1) / 2;
            boolean[] cw = new boolean[cnx * cny * cur.nz];
            for (int ck = 0; ck < cur.nz; ck++)
                for (int cj = 0; cj < cny; cj++)
                    for (int ci = 0; ci < cnx; ci++) {
                        int nWet = 0, nCh = 0;
                        for (int dj = 0; dj < 2; dj++)
                            for (int di = 0; di < 2; di++) {
                                int fi = 2 * ci + di, fj = 2 * cj + dj;
                                if (fi < cur.nx && fj < cur.ny) {
                                    nCh++;
                                    if (cur.w(fi, fj, ck)) nWet++;
                                }
                            }
                        boolean any = nWet > 0;
                        cw[(ck * cny + cj) * cnx + ci] =
                                MG_COARSE_MASK_ALL ? (nCh > 0 && nWet == nCh) : any;
                    }
            list.add(new LV(cnx, cny, cur.nz, cur.dx * 2.0, cur.dy * 2.0, cur.dz, cw));
        }
        return list.toArray(new LV[0]);
    }

    static double applyLV(LV L, double[] p, int i, int j, int k) {
        int q = L.idx(i, j, k);
        double c = p[q];
        int iE = (i + 1 == L.nx) ? 0 : i + 1;
        int iW = (i == 0) ? L.nx - 1 : i - 1;
        double pe = L.w(iE, j, k) ? p[L.idx(iE, j, k)] : c;
        double pw = L.w(iW, j, k) ? p[L.idx(iW, j, k)] : c;
        double pn = L.w(i, j + 1, k) ? p[L.idx(i, j + 1, k)] : c;
        double ps = L.w(i, j - 1, k) ? p[L.idx(i, j - 1, k)] : c;
        double pu = L.w(i, j, k - 1) ? p[L.idx(i, j, k - 1)] : c;
        double pd = L.w(i, j, k + 1) ? p[L.idx(i, j, k + 1)] : c;
        double dz = L.dz[k];
        double dzu = (k > 0) ? 0.5 * (L.dz[k - 1] + dz) : dz;
        double dzd = (k + 1 < L.nz) ? 0.5 * (dz + L.dz[k + 1]) : dz;
        double vert = ((pu - c) / dzu - (c - pd) / dzd) / dz;
        return -((pe + pw - 2.0 * c) / (L.dx * L.dx)
                + (pn + ps - 2.0 * c) / (L.dy * L.dy) + vert);
    }

    /**
     * 垂直线 Thomas 平滑器（每个 i,j 列解一条三对角）。
     *
     * <p>水平对角进三对角、水平非对角进 RHS；垂直 Neumann 的头尾不加鬼项。
     * 这样垂直硬方向被精确解，水平软方向交给 MG 粗化。
     */
    static void gs(LV L, int sweeps) {
        double ax = 1.0 / (L.dx * L.dx), ay = 1.0 / (L.dy * L.dy);
        double[] bb = new double[L.nz], aa = new double[L.nz], cc = new double[L.nz];
        double[] cp = new double[L.nz], dp = new double[L.nz], dd = new double[L.nz], xx = new double[L.nz];
        for (int s = 0; s < sweeps; s++)
            for (int j = 0; j < L.ny; j++)
                for (int i = 0; i < L.nx; i++) {
                    int lo = -1, hi = -1;
                    for (int k = 0; k < L.nz; k++)
                        if (L.w(i, j, k)) { if (lo < 0) lo = k; hi = k; }
                    if (lo < 0) continue;
                    int iE = (i + 1 == L.nx) ? 0 : i + 1;
                    int iW = (i == 0) ? L.nx - 1 : i - 1;
                    for (int k = lo; k <= hi; k++) {
                        double dh = 0.0, oh = 0.0;
                        if (L.w(iE, j, k)) { dh += ax; oh -= ax * L.phi[L.idx(iE, j, k)]; }
                        if (L.w(iW, j, k)) { dh += ax; oh -= ax * L.phi[L.idx(iW, j, k)]; }
                        if (L.w(i, j + 1, k)) { dh += ay; oh -= ay * L.phi[L.idx(i, j + 1, k)]; }
                        if (L.w(i, j - 1, k)) { dh += ay; oh -= ay * L.phi[L.idx(i, j - 1, k)]; }
                        double dz = L.dz[k];
                        double dzu = (k > lo) ? 0.5 * (L.dz[k - 1] + dz) : dz;
                        double dzd = (k < hi) ? 0.5 * (dz + L.dz[k + 1]) : dz;
                        bb[k] = (1.0 / dzu + 1.0 / dzd) / dz + dh;
                        if (k == lo) bb[k] -= 1.0 / (dzu * dz);
                        if (k == hi) bb[k] -= 1.0 / (dzd * dz);
                        aa[k] = (k > lo) ? -1.0 / (dzu * dz) : 0.0;
                        cc[k] = (k < hi) ? -1.0 / (dzd * dz) : 0.0;
                        dd[k] = L.rhs[L.idx(i, j, k)] - oh;
                    }
                    // 正则化：纯 Neumann 列的三对角在极点/单列时奇异（最后一行 den≈0 ⟹ NaN）。
                    // 1e-12 相对于垂直系数（~1e-4）可忽略，只去掉零空间除零。
                    for (int k = lo; k <= hi; k++) bb[k] += 1.0e-12;
                    cp[lo] = cc[lo] / bb[lo];
                    for (int k = lo + 1; k <= hi; k++) {
                        double den = bb[k] - aa[k] * cp[k - 1];
                        cp[k] = cc[k] / den;
                    }
                    dp[lo] = dd[lo] / bb[lo];
                    for (int k = lo + 1; k <= hi; k++) {
                        double den = bb[k] - aa[k] * cp[k - 1];
                        dp[k] = (dd[k] - aa[k] * dp[k - 1]) / den;
                    }
                    xx[hi] = dp[hi];
                    for (int k = hi - 1; k >= lo; k--) xx[k] = dp[k] - cp[k] * xx[k + 1];
                    // 2026-11-11 ROOT-CAUSE FIX (-Deoh.gsmean=true): the per-column vertical
                    // system is pure Neumann (singular, null space = constants). The 1e-12
                    // regularisation makes its fixed point satisfy (A + 1e-12 I) phi = rhs, so the
                    // residual under A is pinned at 1e-12*||phi|| -- measured 4.6206656763E-7 vs
                    // 1e-12*462066.5711 = 4.620665711E-7 (7 digits). Pinning the column mean to
                    // zero removes that drift without changing A.
                    if (Boolean.getBoolean("eoh.gsmean")) {
                        double ms = 0.0, vs = 0.0;
                        for (int k = lo; k <= hi; k++) {
                            double w = L.dz[k];
                            ms += xx[k] * w; vs += w;
                        }
                        if (vs > 0.0) {
                            double mm = ms / vs;
                            for (int k = lo; k <= hi; k++) xx[k] -= mm;
                        }
                    }
                    for (int k = lo; k <= hi; k++) L.phi[L.idx(i, j, k)] = xx[k];
                }
    }

    static void residual(LV L) {
        for (int k = 0; k < L.nz; k++)
            for (int j = 0; j < L.ny; j++)
                for (int i = 0; i < L.nx; i++) {
                    if (!L.w(i, j, k)) continue;
                    int q = L.idx(i, j, k);
                    L.res[q] = L.rhs[q] - applyLV(L, L.phi, i, j, k);
                }
    }

    static double resNorm(LV L) {
        double s = 0.0;
        for (int k = 0; k < L.nz; k++)
            for (int j = 0; j < L.ny; j++)
                for (int i = 0; i < L.nx; i++) {
                    if (!L.w(i, j, k)) continue;
                    double r = L.res[L.idx(i, j, k)];
                    s += r * r * L.dz[k];
                }
        return Math.sqrt(s);
    }

    /** 2026-11-11: remove the true Neumann null space (one constant PER CONNECTED COMPONENT). */
    private static void removeComponentMean(OceanGrid g, boolean[] wet, int[] comp, int nc, double[] phi) {
        double[] s = new double[Math.max(1, nc)];
        double[] v = new double[Math.max(1, nc)];
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * g.nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    s[comp[qq]] += phi[qq] * g.dz[k];
                    v[comp[qq]] += g.dz[k];
                }
            }
        for (int c = 0; c < nc; c++) if (v[c] > 0) s[c] /= v[c];
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * g.nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    phi[qq] -= s[comp[qq]];
                }
            }
    }

    static void restrict(LV F, LV C) {
        java.util.Arrays.fill(C.rhs, 0.0);
        for (int ck = 0; ck < C.nz; ck++)
            for (int cj = 0; cj < C.ny; cj++)
                for (int ci = 0; ci < C.nx; ci++) {
                    if (!C.w(ci, cj, ck)) continue;
                    double sum = 0.0;
                    for (int dj = 0; dj < 2; dj++)
                        for (int di = 0; di < 2; di++) {
                            int fi = 2 * ci + di, fj = 2 * cj + dj;
                            if (fi < F.nx && fj < F.ny && F.w(fi, fj, ck))
                                sum += F.res[F.idx(fi, fj, ck)];
                        }
                    C.rhs[C.idx(ci, cj, ck)] = 0.25 * sum;
                }
    }

    static void prolongAdd(LV C, LV F) {
        final boolean bi = Boolean.getBoolean("eoh.biprol");
        for (int k = 0; k < F.nz; k++)
            for (int j = 0; j < F.ny; j++)
                for (int i = 0; i < F.nx; i++) {
                    if (!F.w(i, j, k)) continue;
                    if (!bi) {
                        F.phi[F.idx(i, j, k)] += C.phi[C.idx(i / 2, j / 2, k)];
                        continue;
                    }
                    // 2026-11-11: BILINEAR injection (controlled test -Deoh.biprol=true).
                    // 0th-order injection adds a piecewise-CONSTANT correction, which is exactly
                    // orthogonal to the "vertically uniform, horizontally smooth" near-null mode
                    // (ax/az ~ 3e-7) that the V-cycle fails to reduce.
                    int ci = i / 2, cj = j / 2;
                    int ciA = ((i & 1) == 0) ? ci - 1 : ci;
                    int ciB = ((i & 1) == 0) ? ci : ci + 1;
                    int cjA = ((j & 1) == 0) ? cj - 1 : cj;
                    int cjB = ((j & 1) == 0) ? cj : cj + 1;
                    // C.w() wraps i modulo nx, but C.idx() does NOT: clamp first or the
                    // bilinear stencil reads the wrong cell / throws (hit 2026-11-11).
                    if (ciA < 0) ciA += C.nx;
                    if (ciB >= C.nx) ciB -= C.nx;
                    if (!C.w(ciB, cj, k)) ciB = ci;
                    if (!C.w(ciA, cj, k)) ciA = ci;
                    if (!C.w(ci, cjB, k)) cjB = cj;
                    if (!C.w(ci, cjA, k)) cjA = cj;
                    double v =
                        0.25 * (C.phi[C.idx(ciA, cjA, k)] + C.phi[C.idx(ciB, cjA, k)]
                              + C.phi[C.idx(ciA, cjB, k)] + C.phi[C.idx(ciB, cjB, k)]);
                    F.phi[F.idx(i, j, k)] += v;
                }
    }

    static void vcycle(LV[] L, int l) {
        LV A = L[l];
        // 2026-11-11 controlled test (-Deoh.mgnocoarse=true): pure smoother, NO coarse correction.
        // Decides whether the coarse-grid correction helps or actively hurts (phi grows 15%/cycle
        // while the residual stalls, so the correction lands in the operator's near-null space).
        if (l == 0 && Boolean.getBoolean("eoh.mgnocoarse")) { gs(A, 200); return; }
        // 2026-11-11: coarsest-level sweep count made configurable (default 80 = historical).
        // Suspect #3 for the MG stall: on the coarsest grid gs() only relaxes VERTICAL lines,
        // so the remaining horizontal error is never solved there -> the V-cycle correction is
        // wrong and the fine-grid residual plateaus (measured 4.007974070799588E-7 at
        // 200/500/1000/2000 cycles, bit-identical).
        if (l == L.length - 1) { gs(A, MG_COARSE_SWEEPS); return; }
        gs(A, 2);
        residual(A);
        LV C = L[l + 1];
        java.util.Arrays.fill(C.phi, 0.0);
        restrict(A, C);
        vcycle(L, l + 1);
        prolongAdd(C, A);
        gs(A, 2);
    }

    static double[] solveMG(OceanGrid g, double[] div, int maxIter) {
        int n = g.nx * g.ny * g.nz;
        boolean[] wet = wetMask(g);
        int[] comp = new int[n];
        int nc = labelComponents(g, wet, comp);
        LAST_COMPONENTS = nc;
        double[] csum = new double[Math.max(1, nc)];
        double[] cvol = new double[Math.max(1, nc)];
        for (int k = 0; k < g.nz; k++)
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j, k)) continue;
                    int q = q(g, i, j, k);
                    csum[comp[q]] += div[q] * g.dz[k];
                    cvol[comp[q]] += g.dz[k];
                }
        double[] cmean = new double[Math.max(1, nc)];
        for (int c = 0; c < nc; c++) cmean[c] = cvol[c] > 0 ? csum[c] / cvol[c] : 0.0;
        double[] rhs = new double[n];
        for (int k = 0; k < g.nz; k++)
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j, k)) continue;
                    int q = q(g, i, j, k);
                    // A phi = -div'（A=-∇²）⟹ rhs 取负；旧 CG 版同样是 -(div-mean)。
                    rhs[q] = -(div[q] - cmean[comp[q]]);
                }
        // 诊断：div/rhs 入口是否已 NaN（seed 0 的极点列）
        outer:
        for (int k = 0; k < g.nz; k++)
            for (int j = 0; j < g.ny; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j, k)) continue;
                    int qq = q(g, i, j, k);
                    if (!Double.isFinite(div[qq]) || !Double.isFinite(rhs[qq])) {
                        System.out.println("MG_DIV_NAN i=" + i + " j=" + j + " k=" + k
                                + " div=" + div[qq] + " rhs=" + rhs[qq]);
                        break outer;
                    }
                }
        LV[] L = buildLevels(g, wet);
        L[0].rhs = rhs;
        residual(L[0]);
        double r0 = resNorm(L[0]);
        if (r0 <= 0.0) {
            LAST_CG_ITER = 0; LAST_RES = 0.0; LAST_CONVERGED = true;
            return L[0].phi;
        }
        // ★ 2026-11-11：上限从硬编码 500 改成可配置（默认仍是 500 ⟹ 逐位不变）。
        //   实测 seed 0 生产配置每季都停在 500 cycle（WARN PROJ_MG_NOT_CONVERGED，
        //   res/r0 ≈ 3e-2），投影后 max|div| 只剩 4.6e-9，而门是 1e-9 ⟹ GATE_CONT 的
        //   FAIL 是"迭代器被截断"，不是物理。要给更多 cycle 由该字段控制。
        int maxCyc = Math.min(maxIter, MG_MAX_CYCLES);
        int cyc = 0;
        double rn = r0;
        for (; cyc < maxCyc; cyc++) {
            vcycle(L, 0);
            if (Boolean.getBoolean("eoh.gsmeancomp")) removeComponentMean(g, wet, comp, nc, L[0].phi);
            residual(L[0]);
            rn = resNorm(L[0]);
            // 2026-11-11 trace: is phi actually changing from cycle to cycle?
            if (Boolean.getBoolean("eoh.mgtrace") && cyc < 4) {
                double pn = 0.0;
                for (int j = 1; j < g.ny - 1; j++)
                    for (int i = 0; i < g.nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int wl = g.wetLayers[j * g.nx + i];
                        for (int k = 0; k < wl; k++) {
                            int qq = q(g, i, j, k);
                            pn += L[0].phi[qq] * L[0].phi[qq] * g.dz[k];
                        }
                    }
                System.out.println("MGTRACE cyc=" + cyc + " res=" + rn
                        + " phiNorm=" + Math.sqrt(pn) + " r0=" + r0);
            }
            if (rn <= CG_TOL * r0) break;
        }
        LAST_CG_ITER = cyc;
        LAST_RES = rn;
        LAST_CONVERGED = rn <= CG_TOL * r0;
        if (!LAST_CONVERGED) {
            System.out.println("WARN PROJ_MG_NOT_CONVERGED cycles=" + cyc
                    + " res=" + rn + " r0=" + r0);
        }
        return L[0].phi;
    }

    /** 从格心速度初始化面速度；投影后原地修改 uF/vF/wF。返回投影前 max|div|。 */

    // ==================================================================================
    // 2026-11-11: PROJECTION V2 -- continuity-integrated w + one 2D Poisson per surface.
    //
    // WHY (handoff 5-2b, 7 eliminations recorded in NDEEPBUDGET note 7.16): the 3D Poisson
    // operator is 3e6 times stiffer vertically than horizontally, so the multigrid cannot see
    // the horizontally-smooth modes and stalls at 4.0e-07 (and CG crawls). The physics does
    // NOT require a 3D Poisson: for a hydrostatic fluid the vertical velocity is DETERMINED by
    // continuity once the horizontal flow is fixed:
    //
    //     du/dx + dv/dy + dw/dz = 0   =>   w(top of k) = w(bottom of k) - hdiv(k)*dz(k)
    //
    // with w = 0 on the seabed. The ONLY remaining global constraint is that the
    // depth-integrated flow be non-divergent (rigid lid):
    //
    //     grad^2 phi = S/H,   S = sum_k hdiv(k)*dz(k),   (u,v) -= (dphi/dx, dphi/dy)
    //
    // which is a pure 2D problem on an isotropic grid (dx = dy = 277.8 km) => well conditioned.
    // No anisotropy, no multigrid, no iteration in the vertical.
    // ==================================================================================

    /** Master switch for the V2 projection. Default false => the historical path runs. */
    public static boolean PROJECT_V2 = Boolean.parseBoolean(
            System.getProperty("eoh.projv2", "true"));   // A/B hook: -Deoh.projv2=false
    /**
     * 2026-11-11 用户裁决【甲】：让顶层格的上界面携带**自由面垂直速度** w(eta)，使 3D 散度真正闭合。
     * 理由：rigid-lid 与"深度均匀校正"在变深度洋盆里数学上不相容（laplacian(phi)=S/H 的 Neumann
     * 相容性要求 Σ(S/H)=0，而只有 ΣS=0 恒成立）。剩下的那一项就是 d(eta)/dt——物理量，不是求解器误差。
     * 模型本来就有 SteadyFreeSurfaceSolver 在处理 eta；把它接进面速度才是自洽的。
     */
    public static boolean FREE_SURFACE_W = true;
    /** 每个 (i,j) 的自由面垂直速度 [m/s]，由 V2 投影按列精确给出。 */
    private static double[] SURF_W = null;

    /** Free-surface vertical velocity at (i,j) [m/s]; 0 when the free-surface option is off. */
    public static double surfW(int i, int j, int nx) {
        if (!FREE_SURFACE_W || SURF_W == null) return 0.0;
        return SURF_W[j * nx + i];
    }
    /** 2D CG iteration cap and reported figures for the V2 path. */
    public static int V2_MAX_ITER = 20000;
    public static int V2_ITER = 0;
    public static double V2_RES = 0.0;
    public static boolean V2_CONVERGED = false;

    private static void v2FaceFromCells(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    st.uF[qq] = g.isWet(i + 1, j, k) ? 0.5 * (st.u[qq] + st.u[q(g, i + 1, j, k)]) : 0.0;
                    st.vF[qq] = g.isWet(i, j + 1, k) ? 0.5 * (st.v[qq] + st.v[q(g, i, j + 1, k)]) : 0.0;
                }
            }
    }

    private static double v2HDiv(OceanGrid g, OceanState st, int i, int j, int k) {
        int qq = q(g, i, j, k);
        double uE = st.uF[qq], uW = st.uF[q(g, i - 1, j, k)];
        double vN = st.vF[qq], vS = st.vF[q(g, i, j - 1, k)];
        return (uE - uW) / g.dx + (vN - vS) / g.dy;
    }

    /**
     * V2 projection. Returns max|div| before the correction (same contract as {@link #project}).
     */
    public static double projectV2(OceanGrid g, OceanState st, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        int n2 = nx * ny;
        v2FaceFromCells(g, st);
        sealSeabed(g, st);
        LAST_DIV_BEFORE = maxDiv(g, st);

        // ---- depth-integrated divergence S(i,j) and column depth H(i,j) ----
        double[] S = new double[n2];
        double[] H = new double[n2];
        boolean[] wet2 = new boolean[n2];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                if (wl <= 0) continue;
                wet2[j * nx + i] = true;
                double s = 0.0, h = 0.0;
                for (int k = 0; k < wl; k++) {
                    s += v2HDiv(g, st, i, j, k) * g.dz[k];
                    h += g.dz[k];
                }
                S[j * nx + i] = s;
                H[j * nx + i] = h;
            }

        // ---- 2D CG on L phi = -S/H, L = -laplacian (SPD, Neumann, x periodic) ----
        int nC = 0;
        int[] comp2 = new int[n2];
        java.util.Arrays.fill(comp2, -1);
        {
            int[] stack = new int[n2];
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    int s0 = j * nx + i;
                    if (!wet2[s0] || comp2[s0] >= 0) continue;
                    int top = 0;
                    stack[top++] = s0;
                    comp2[s0] = nC;
                    while (top > 0) {
                        int c = stack[--top];
                        int jj = c / nx, ii = c % nx;
                        int[] di = {1, -1, 0, 0};
                        int[] dj = {0, 0, 1, -1};
                        for (int t = 0; t < 4; t++) {
                            int i2 = ii + di[t], j2 = jj + dj[t];
                            if (j2 < 1 || j2 >= ny - 1) continue;
                            int iw = ((i2 % nx) + nx) % nx;
                            int s2 = j2 * nx + iw;
                            if (!wet2[s2] || comp2[s2] >= 0) continue;
                            comp2[s2] = nC;
                            stack[top++] = s2;
                        }
                    }
                    nC++;
                }
        }
        int ncUse = Math.max(1, nC);
        double[] csum = new double[ncUse];
        double[] ccnt = new double[ncUse];
        for (int c = 0; c < n2; c++) {
            if (comp2[c] < 0) continue;
            // compatibility of div(H grad phi) = S requires sum(S) = 0 (holds exactly by
            // telescoping over a closed basin); S/H was WRONG here and left a per-column
            // imbalance that leaked into the top cell (measured divAfter 4.18e-07).
            csum[comp2[c]] += S[c];
            ccnt[comp2[c]] += 1.0;
        }
        double[] cmean = new double[ncUse];
        for (int c = 0; c < nC; c++) cmean[c] = ccnt[c] > 0 ? csum[c] / ccnt[c] : 0.0;

        double[] phi = new double[n2], r2 = new double[n2], p2 = new double[n2], ap2 = new double[n2];
        double r0 = 0.0;
        for (int c = 0; c < n2; c++) {
            if (comp2[c] < 0) continue;
            r2[c] = -(S[c] - cmean[comp2[c]]);
            p2[c] = r2[c];
            r0 += r2[c] * r2[c];
        }
        r0 = Math.sqrt(r0);
        int it = 0;
        boolean conv = false;
        double rn = r0;
        double rz = r0 * r0;
        int cap = Math.max(V2_MAX_ITER, maxIter);
        for (; it < cap; it++) {
            // ap = -laplacian(p)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    int c = j * nx + i;
                    if (!wet2[c]) { ap2[c] = 0.0; continue; }
                    int iE = (i + 1) % nx, iW = (i - 1 + nx) % nx;
                    double cc = p2[c];
                    double hc = H[c];
                    boolean we = wet2[j * nx + iE], ww = wet2[j * nx + iW];
                    boolean wn = wet2[(j + 1) * nx + i], ws = wet2[(j - 1) * nx + i];
                    double pe = we ? p2[j * nx + iE] : cc;
                    double pw = ww ? p2[j * nx + iW] : cc;
                    double pn = wn ? p2[(j + 1) * nx + i] : cc;
                    double ps = ws ? p2[(j - 1) * nx + i] : cc;
                    // div(H grad phi): face depth = average of the two columns (zero flux to dry)
                    double he = we ? 0.5 * (hc + H[j * nx + iE]) : 0.0;
                    double hw = ww ? 0.5 * (hc + H[j * nx + iW]) : 0.0;
                    double hn = wn ? 0.5 * (hc + H[(j + 1) * nx + i]) : 0.0;
                    double hs = ws ? 0.5 * (hc + H[(j - 1) * nx + i]) : 0.0;
                    ap2[c] = -((he * (pe - cc) - hw * (cc - pw)) / (g.dx * g.dx)
                             + (hn * (pn - cc) - hs * (cc - ps)) / (g.dy * g.dy));
                }
            double pap = 0.0;
            for (int c = 0; c < n2; c++) if (comp2[c] >= 0) pap += p2[c] * ap2[c];
            if (Math.abs(pap) < 1e-300) break;
            double alpha = rz / pap;
            rn = 0.0;
            for (int c = 0; c < n2; c++) {
                if (comp2[c] < 0) continue;
                phi[c] += alpha * p2[c];
                r2[c] -= alpha * ap2[c];
                rn += r2[c] * r2[c];
            }
            rn = Math.sqrt(rn);
            if (rn <= 1e-10 * r0) { conv = true; break; }
            double beta = rn * rn / rz;
            rz = rn * rn;
            for (int c = 0; c < n2; c++) if (comp2[c] >= 0) p2[c] = r2[c] + beta * p2[c];
        }
        V2_ITER = it;
        V2_RES = rn;
        V2_CONVERGED = conv;

        // ---- apply the depth-uniform horizontal correction ----
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (!wet2[c]) continue;
                int iE = (i + 1) % nx;
                double pE = wet2[j * nx + iE] ? phi[j * nx + iE] : phi[c];
                double pN = wet2[(j + 1) * nx + i] ? phi[(j + 1) * nx + i] : phi[c];
                int wl = g.wetLayers[c];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    st.uF[qq] -= (pE - phi[c]) / g.dx;
                    st.vF[qq] -= (pN - phi[c]) / g.dy;
                }
            }

        // ---- w by exact upward integration of the corrected horizontal divergence ----
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                int c = j * nx + i;
                if (!wet2[c]) continue;
                int wl = g.wetLayers[c];
                double w = 0.0;                       // seabed: zero normal flow
                st.wF[q(g, i, j, wl - 1)] = 0.0;
                for (int k = wl - 2; k >= 0; k--) {
                    // wF[k-1] = wF[k] - hdiv(k)*dz(k)  (w positive up, wF[k] = bottom face of k)
                    w -= v2HDiv(g, st, i, j, k + 1) * g.dz[k + 1];
                    st.wF[q(g, i, j, k)] = w;
                }
                if (SURF_W == null || SURF_W.length != nx * ny) SURF_W = new double[nx * ny];
                // surface face: w(top of k=0) = wF[0] - hdiv(0)*dz(0)  -- the free-surface velocity
                SURF_W[c] = w - v2HDiv(g, st, i, j, 0) * g.dz[0];
            }
        sealSeabed(g, st);
        LAST_SEABED_MAX = seabedMax(g, st);
        LAST_DIV_AFTER = maxDiv(g, st);
        FACES_READY = true;
        return LAST_DIV_BEFORE;
    }
    public static double project(OceanGrid g, OceanState st, int maxIter) {
        if (PROJECT_V2) return projectV2(g, st, maxIter);
        int nx = g.nx, ny = g.ny, nz = g.nz, n = nx * ny * nz;
        double dx = g.dx, dy = g.dy;

        // 1) 面速度由格心速度重建（仅在两侧都是 3D 湿格时；否则零通量）
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    st.uF[qq] = g.isWet(i + 1, j, k) ? 0.5 * (st.u[qq] + st.u[q(g, i + 1, j, k)]) : 0.0;
                    st.vF[qq] = g.isWet(i, j + 1, k) ? 0.5 * (st.v[qq] + st.v[q(g, i, j + 1, k)]) : 0.0;
                    st.wF[qq] = g.isWet(i, j, k + 1) ? 0.5 * (st.w[qq] + st.w[q(g, i, j, k + 1)]) : 0.0;
                }
            }
        // 2) 海底面封口（投影前）
        sealSeabed(g, st);

        // 2c) openY patch 的 ghost 行：面速度直接取父/本行值（不参与投影）
        for (int i = 0; i < nx; i++) {
            if (!g.isWet(i, 0)) continue;
            for (int k = 0; k < nz; k++) {
                int qq = q(g, i, 0, k);
                st.uF[qq] = st.u[qq];
                st.vF[qq] = st.v[qq];
                st.wF[qq] = 0.0;
            }
        }

        // 3) 面散度
        double[] div = new double[n];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) div[q(g, i, j, k)] = faceDiv(g, st, i, j, k);
            }
        LAST_DIV_BEFORE = maxDiv(g, st);

        // 3b) 正压分裂：先用 2D 泊松去掉深度积分的散度（条件数 ~1e4）。
        double[] b2 = new double[nx * ny];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                double s = 0.0, hh = 0.0;
                for (int k = 0; k < wl; k++) {
                    s += div[q(g, i, j, k)] * g.dz[k];
                    hh += g.dz[k];
                }
                // 修正是深度均匀的：深度积分散度变化 = -H·laplacian(phi)。
                // 要抵消 s 需 laplacian(phi) = s/H；apply2D = -laplacian ⟹ RHS = -s/H。
                b2[j * nx + i] = -s / Math.max(1.0, hh);
            }
        // 2D 正压分裂暂关（旧实现的应用层有未解 bug）；全 3D 交给下面的 MG。
        double[] phi2 = new double[nx * ny];
        if (System.getProperty("eoh.projdebug") != null) {
            double maxB = 0.0, maxPhi = 0.0;
            for (int c = 0; c < nx * ny; c++) {
                maxB = Math.max(maxB, Math.abs(b2[c]));
                maxPhi = Math.max(maxPhi, Math.abs(phi2[c]));
            }
            System.out.println("DBG2 maxB2=" + maxB + " maxPhi2=" + maxPhi
                    + " it2=" + LAST_2D_ITER + " rel2=" + LAST_2D_REL);
        }
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                int iE = (i + 1 == nx) ? 0 : i + 1;
                double c = phi2[js];
                double pe = g.isWet(iE, j) ? phi2[j * nx + iE] : c;
                double pn = g.isWet(i, j + 1) ? phi2[(j + 1) * nx + i] : c;
                int wl = g.wetLayers[js];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    // 经验判定：面速度朝向与 u=-dphi/dx 的符号约定相反（见 PROJTEST 的
                    // sBefore/pred/sAfterCol 三点对比），故此处用 +。
                    st.uF[qq] += (pe - c) / dx;
                    st.vF[qq] += (pn - c) / dy;
                }
            }
        // 重新计算剩余（斜压）散度
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) div[q(g, i, j, k)] = faceDiv(g, st, i, j, k);
            }
        if (System.getProperty("eoh.projdebug") != null) {
            double maxCol = 0.0;
            int bi = -1, bj = -1;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int wl = g.wetLayers[j * nx + i];
                    double s = 0.0;
                    for (int k = 0; k < wl; k++) s += div[q(g, i, j, k)] * g.dz[k];
                    if (Math.abs(s) > maxCol) {
                        maxCol = Math.abs(s);
                        bi = i;
                        bj = j;
                    }
                }
            System.out.println("DBG2 maxColDivAfter2D=" + maxCol);
            if (bi >= 0) {
                int js = bj * nx + bi;
                double c = phi2[js];
                int iE = (bi + 1 == nx) ? 0 : bi + 1;
                int iW = (bi == 0) ? nx - 1 : bi - 1;
                double pe = phi2[bj * nx + iE], pw = phi2[bj * nx + iW];
                double pn = phi2[(bj + 1) * nx + bi], ps = phi2[(bj - 1) * nx + bi];
                double lap2 = (pe + pw - 2.0 * c) / (dx * dx) + (pn + ps - 2.0 * c) / (dy * dy);
                int wl = g.wetLayers[js];
                double hh = 0.0;
                for (int k = 0; k < wl; k++) hh += g.dz[k];
                double sBefore = -b2[js] * hh;
                double sAfterCol = 0.0;
                for (int k = 0; k < wl; k++) sAfterCol += div[q(g, bi, bj, k)] * g.dz[k];
                System.out.println("DBG2 col i=" + bi + " j=" + bj
                        + " sBefore=" + sBefore + " b2=" + b2[js]
                        + " lap2=" + lap2 + " sAfterCol=" + sAfterCol
                        + " maxCol=" + maxCol
                        + " pred=" + (sBefore - hh * lap2));
            }
        }

        // 4) 解 L phi = div（按连通分量减均值）
        double[] phi;
        if (SOLVER_HYBRID) {
            int mgSave = MG_MAX_CYCLES;
            MG_MAX_CYCLES = Math.min(HYBRID_MG_CYCLES, mgSave);
            double[] phi0 = solveMG(g, div, MG_MAX_CYCLES);
            MG_MAX_CYCLES = mgSave;
            phi = solvePoissonSeeded(g, st, div, 0.0, HYBRID_CG_ITER, phi0);
        } else if (SOLVER_CG) {
            phi = solvePoisson(g, st, div, 0.0, Math.max(maxIter, CG_MAX_ITER));
        } else {
            phi = solveMG(g, div, maxIter);
        }

        // 5) 修正面速度（只修正 3D 湿格；干格一侧零梯度）
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    double c = phi[qq];
                    int iE = (i + 1 == nx) ? 0 : i + 1;
                    double pe = g.isWet(iE, j, k) ? phi[q(g, iE, j, k)] : c;
                    double pn = g.isWet(i, j + 1, k) ? phi[q(g, i, j + 1, k)] : c;
                    double pb = g.isWet(i, j, k + 1) ? phi[q(g, i, j, k + 1)] : c;
                    double dzd = (k + 1 < wl) ? 0.5 * (g.dz[k] + g.dz[k + 1]) : g.dz[k];
                    st.uF[qq] -= (pe - c) / dx;
                    st.vF[qq] -= (pn - c) / dy;
                    st.wF[qq] -= (c - pb) / dzd;
                }
            }

        // 6) 投影后再封一次：海床面、以及朝干格的面（零通量）
        sealSeabed(g, st);
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * nx + i];
                for (int k = 0; k < wl; k++) {
                    int qq = q(g, i, j, k);
                    if (!g.isWet(i + 1, j, k)) st.uF[qq] = 0.0;
                    if (!g.isWet(i, j + 1, k)) st.vF[qq] = 0.0;
                }
            }

        LAST_SEABED_MAX = seabedMax(g, st);
        LAST_DIV_AFTER = maxDiv(g, st);
        FACES_READY = true;
        return LAST_DIV_BEFORE;
    }

    /** 仅供诊断探针：对给定 phi 应用一次 A = −∇²（同一 3D 掩膜）。 */
    public static double[] applyForTest(OceanGrid g, double[] phi) {
        boolean[] wet = wetMask(g);
        double[] out = new double[phi.length];
        applyL(g, wet, phi, out);
        return out;
    }

    private static void sealSeabed(OceanGrid g, OceanState st) {
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * g.nx + i];
                if (wl <= 0) continue;
                st.wF[q(g, i, j, wl - 1)] = 0.0;
            }
    }

    private static double seabedMax(OceanGrid g, OceanState st) {
        double mx = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[j * g.nx + i];
                if (wl <= 0) continue;
                mx = Math.max(mx, Math.abs(st.wF[q(g, i, j, wl - 1)]));
            }
        return mx;
    }
}
