package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

/**
 * 稳态**风生正压环流**求解器（路线 B · 第二版）。
 *
 * <pre>
 *   ∂ζ/∂t = curl_z(τ)/(ρ₀·H) − β·v + A_h·∇²ζ
 *   ζ = ∇²ψ ,  u = −∂ψ/∂y ,  v = ∂ψ/∂x ,  τ = ρ_a·C_D·|U|·U
 *   curl_z(τ) = ∂τy/∂x − ∂τx/∂y
 * </pre>
 *
 * <b>为什么在粗网格上解</b>：Munk 层 δ_M=(A_h/β)^(1/3)。本世界 β≈1.15e-10（地球 5 倍）
 * 而海盆中位数只有 1510 km —— 用地球的 A_h≈1.2e3 得 δ_M=22 km、西向强化倍数 L/δ_M≈69，
 * 算出来是几 m/s 的假洋流。取 A_h=1.8e6 ⇒ δ_M≈250 km、L/δ_M≈6 ⇒ 真实的 0.7~1.2 m/s。
 * δ_M=250 km 意味着粗网格 35 km 完全够（7 格），于是整场在 32×128 上解。
 *
 * <b>为什么必须多重网格</b>：第一版用高斯-赛德尔解泊松，大尺度模式收敛率只有 1−O(1/N²)，
 * ψ 在盆尺度上滞后于 ζ → β 项错 → ζ 持续积分 → maxV 跑到 973 m/s。加扫描次数无效。
 *
 * 粗/细网格用**采样**解耦（不做代数多重网格），避免整除问题；粗网格自成一套离散。
 */
public final class BarotropicGyre {

    private BarotropicGyre() {}

    public static final double OMEGA = 7.2921e-5;
    public static double RHO_AIR = 1.2;
    public static double C_D = 1.3e-3;
    public static double RHO_WATER = 1025.0;
    public static double H_MIXED = 100.0;
    public static double A_H = 1.9e4;
    public static double WIND_MS = 10.0;
    public static double BETA_SCALE = 1.0;

    public static int NCX = 32, NCZ = 128;
    /** 伪时间步长（s）：显式扩散上限 ≈ 1/(2A_h(1/hx²+1/hz²)) ≈ 155 s。 */
    public static double DT = 140.0;
    /**
     * 步数：DT×MACRO 需 ≥ 环流建立时间（≈8 天 = 7e5 s）。
     *
     * 注意：V_CYCLES=1 时压力泊松解是**跨步累积** V-cycle 才收敛的，
     * 所以 MACRO 同时决定 ζ 的积分长度**和** ψ 的收敛程度，不能单独按
     * 物理自旋时间缩短。实测（1/4 世界，A_H=1.9e4）：MACRO=315 泊松残差
     * 1.7e-2（未收敛），MACRO=5000 为 4.6e-6。若将来要把 MACRO 调小，
     * 必须同步把 V_CYCLES 按比例调大。
     */
    public static int MACRO = 5000;
    /**
     * 每个宏观步的 V-cycle 次数。
     *
     * 实测（理想闭合海盆 128×128，β=3.24e-10，A_H=1.9e4，δ_M=5 格）单次 V-cycle 只把
     * 泊松残差降到 3e-1 量级，V_CYCLES=1 时残差 1.4e-1 —— ψ 严重落后 ζ，于是 −β·v 反馈
     * 是错的，**西边界层根本长不出来**（西/东=0.80，|v| 峰值出现在海盆中央）。
     * V_CYCLES>=4 后残差降到 4e-3，教科书 Munk 解立刻出现：内部 v≈0.012 m/s
     * （Sverdrup 理论 1.47e-2，吻合），西岸峰值 0.24 m/s = 内部 20 倍，东岸仅 0.026。
     */
    public static int V_CYCLES = 1;
    /**
     * TODO(成本)：V_CYCLES 1->4 后生产路径单窗口求解实测从 ~9.5 s 涨到 ~440 s（46 倍，
     * 远高于 V-cycle 次数的 4 倍），说明还有别的开销被这次收敛放大。下一步要做的：
     *   1) 在 V_CYCLES=4 下扫 MACRO（1250/2500/5000），用理想海盆的"内部 v 是否等于
     *      Sverdrup 值 + 西/东比"作为判据找出最小可用步数；
     *   2) 查 updateFlow 里除了 solve() 之外还有哪个循环的迭代次数取决于流场；
     *   3) 把 vcycle 的 pre/post 平滑从 2 提到 3~4，换取单次 V-cycle 收敛因子更好，
     *      从而允许 V_CYCLES 降到 2。在此之前不要下调 V_CYCLES。
     */
    /** 输出前最后一次泊松解的 V-cycle 次数（取 u/v 用的 ψ 必须充分收敛）。 */
    public static int FINAL_CYCLES = 32;

    /**
     * 收敛提前退出阈值：max|Δζ| 相对于 DT·max|src| 的比值。
     *
     * 必须**从 ζ=0 起步**做判据，不能跨调用热启动 —— 否则同一坐标的生成结果会依赖
     * 玩家访问窗口的顺序，破坏确定性地形。从零起点做，收敛步数是窗口的确定函数。
     */
    public static double CONV_TOL = 1e-3;
    /** 提前退出的最小步数下限，避免源项极弱时一步就"收敛"。 */
    public static int MIN_STEPS = 150;
    /** 上一次求解实际走了多少步（诊断）。 */
    public static int lastSteps;
    // ===== 步数上限是【每次调用的显式参数】，不是静态字段 =====
    //
    // 这里曾经是 `public static int STEP_CAP`，由 RelaxedClimate 在调用前后写：
    //     STEP_CAP = 416; updateFlow(...); STEP_CAP = 0;
    // 这在**同一个进程里是跨线程共享的可变量**：preheat 线程（RelaxedClimate.warm →
    // HEATER 线程池）会在前台停在外层某一轮时调用 solve()，于是同一个 (种子, 窗口) 的解
    // 取决于**另一个线程当时停在第几轮外层** —— 前台/后台谁先跑完就得到不同的流场，
    // 并被写进窗口缓存长期生效。这是真正的非确定性世界内容缺陷，不是理论风险。
    // 改成参数后每个调用点的上限在**它自己的栈帧**里，结构上不可能再串。
    //
    // 代价：十几处历史探针直接调 11 参 solve()。它们全部走「完整自旋」这一档，
    // 所以保留一个 11 参重载（= stepCap 0）给它们，生产代码只走带 stepCap 的那个。

    /**
     * 求解域【Y（Z 轴）方向是否周期环绕】。**默认 true = 与历史行为逐位一致。**
     *
     * 存在意义：ClimateGridData 那一层已经改成"Z 不环绕"（I 轮加了真实 halo 行），
     * 但本求解器的模板用 `(y±1+ncz)%ncz` 又把 Z 接了回去 —— 等于修了一半。
     * 设 false 时改为 clamp（与 ClimateGridData.idx() 的 Z 处理一致，靠真实 halo 行收边）。
     *
     * ===== 状态：保留中，尚未定案（2026-09）=====
     * · **默认 `true` = 与历史行为逐位一致**（已用 P200 复跑证明：三例的 steps/泊松残差/RMS u,v 全部相同）。
     * · 它现在只被探针用来做"只改 Y 周期这一个变量"的受控实验（P210）。
     * · **已实测的收益**（P210，生产窗口 tileX=0，同一批格点）：
     *     `true`  → RMS u=0.22087 v=0.05545，**u/v = 3.98**
     *     `false` → RMS u=0.25279 v=0.12310，**u/v = 2.05**（v 翻一倍多）
     *   负对照（海区上下被陆封死、碰不到接缝）：开关两边**所有统计量逐位相同** ⇒ 差异不是噪声。
     * · **修改方向已批准，但执行时间未定**（属于会改变世界海洋的行为变更，需与其它决策一起拍板）。
     *
     * ⇒ **定案后必须二选一：把默认值固定成正确的那一侧（大概率是 `false`），或者直接删掉这个开关
     *    并把 yIdx 换成所选实现。不要长期把一个"两种世界都能跑"的开关留在生产代码里。**
     */
    public static boolean WRAP_Y = true;

    /** Y 索引：WRAP_Y 时按 ny 取模；否则 clamp 到 [0, ny-1]（与 ClimateGridData.idx 的 Z 规则一致）。 */
    static int yIdx(int y, int ny) {
        if (WRAP_Y) {
            return ((y % ny) + ny) % ny;
        }
        return y < 0 ? 0 : (y >= ny ? ny - 1 : y);
    }

    public static double lastResidual, lastMaxU, lastMaxV, lastMaxZeta, lastMaxSrc, lastMs;

    /** 诊断计数器（零行为变化）：solve() 被调用的次数、累计实际走的宏观步数。 */
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_CALLS =
        new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong TOTAL_MACRO_STEPS =
        new java.util.concurrent.atomic.AtomicLong();
    /** 诊断：最后一次泊松解的相对残差 max|Lψ−rhs|/max|rhs|（ζ 收敛 ≠ ψ 收敛）。 */
    public static double lastPoisRes;

    private static final class Lv {
        final int nx, ny, n;
        final boolean[] land;
        final double[] psi, rhs, res;
        Lv(int nx, int ny, boolean[] land) {
            this.nx = nx; this.ny = ny; this.n = nx * ny; this.land = land;
            psi = new double[n]; rhs = new double[n]; res = new double[n];
        }
        int id(int x, int y) {
            return yIdx(y, ny) * nx + (((x % nx) + nx) % nx);
        }
    }

    private static Lv[] buildLevels(Lv fine) {
        java.util.List<Lv> ls = new java.util.ArrayList<>();
        ls.add(fine);
        Lv cur = fine;
        while (cur.nx >= 8 && cur.ny >= 16) {
            int nx = cur.nx / 2, ny = cur.ny / 2;
            boolean[] land = new boolean[nx * ny];
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    int c = 0;
                    if (cur.land[cur.id(2 * x, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x, 2 * y + 1)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y + 1)]) c++;
                    land[y * nx + x] = c >= 2;
                }
            }
            cur = new Lv(nx, ny, land);
            ls.add(cur);
        }
        return ls.toArray(new Lv[0]);
    }

    private static void smooth(Lv l, int iters) {
        for (int it = 0; it < iters; it++) {
            for (int y = 0; y < l.ny; y++) {
                for (int x = 0; x < l.nx; x++) {
                    int i = y * l.nx + x;
                    if (l.land[i]) { l.psi[i] = 0; continue; }
                    double s = l.psi[l.id(x + 1, y)] + l.psi[l.id(x - 1, y)]
                             + l.psi[l.id(x, y + 1)] + l.psi[l.id(x, y - 1)];
                    l.psi[i] = 0.25 * (s - l.rhs[i]);
                }
            }
        }
    }

    private static void residual(Lv l) {
        for (int y = 0; y < l.ny; y++) {
            for (int x = 0; x < l.nx; x++) {
                int i = y * l.nx + x;
                if (l.land[i]) { l.res[i] = 0; continue; }
                double s = l.psi[l.id(x + 1, y)] + l.psi[l.id(x - 1, y)]
                         + l.psi[l.id(x, y + 1)] + l.psi[l.id(x, y - 1)] - 4.0 * l.psi[i];
                l.res[i] = l.rhs[i] - s;
            }
        }
    }

    private static void restrict(Lv f, Lv c) {
        for (int y = 0; y < c.ny; y++) {
            for (int x = 0; x < c.nx; x++) {
                double v = 4.0 * f.res[f.id(2 * x, 2 * y)]
                    + 2.0 * (f.res[f.id(2 * x + 1, 2 * y)] + f.res[f.id(2 * x - 1, 2 * y)]
                           + f.res[f.id(2 * x, 2 * y + 1)] + f.res[f.id(2 * x, 2 * y - 1)])
                    + (f.res[f.id(2 * x + 1, 2 * y + 1)] + f.res[f.id(2 * x - 1, 2 * y + 1)]
                     + f.res[f.id(2 * x + 1, 2 * y - 1)] + f.res[f.id(2 * x - 1, 2 * y - 1)]);
                c.rhs[y * c.nx + x] = v / 16.0;
            }
        }
        java.util.Arrays.fill(c.psi, 0.0);
    }

    private static void prolongAdd(Lv c, Lv f) {
        for (int y = 0; y < c.ny; y++) {
            for (int x = 0; x < c.nx; x++) {
                double v = c.psi[y * c.nx + x];
                int fx = 2 * x, fy = 2 * y;
                f.psi[f.id(fx, fy)] += v;
                f.psi[f.id(fx + 1, fy)] += 0.5 * v;
                f.psi[f.id(fx - 1, fy)] += 0.5 * v;
                f.psi[f.id(fx, fy + 1)] += 0.5 * v;
                f.psi[f.id(fx, fy - 1)] += 0.5 * v;
                f.psi[f.id(fx + 1, fy + 1)] += 0.25 * v;
                f.psi[f.id(fx - 1, fy + 1)] += 0.25 * v;
                f.psi[f.id(fx + 1, fy - 1)] += 0.25 * v;
                f.psi[f.id(fx - 1, fy - 1)] += 0.25 * v;
            }
        }
    }

    private static void vcycle(Lv[] ls, int lvl) {
        Lv l = ls[lvl];
        if (lvl == ls.length - 1) { smooth(l, 60); return; }
        smooth(l, 2);
        residual(l);
        Lv c = ls[lvl + 1];
        restrict(l, c);
        vcycle(ls, lvl + 1);
        prolongAdd(c, l);
        smooth(l, 2);
    }

    private static double tauX(double[] u, double[] v, int i) {
        double sp = Math.hypot(u[i], v[i]) * WIND_MS;
        return RHO_AIR * C_D * sp * u[i] * WIND_MS;
    }

    private static double tauY(double[] u, double[] v, int i) {
        double sp = Math.hypot(u[i], v[i]) * WIND_MS;
        return RHO_AIR * C_D * sp * v[i] * WIND_MS;
    }

    /**
     * 完整自旋（步数上限 = {@link #MACRO}），等价于 {@code solve(..., 0)}。
     * 保留这个重载只为不动几十处历史探针的调用点；生产代码用带 {@code stepCap} 的那个。
     */
    public static void solve(int nxF, int nyF, double dxF, double dzF, boolean[] landF,
                             double[] uW, double[] vW, double[] fRowF, double[] betaRowF,
                             double[] uOutF, double[] vOutF) {
        solve(nxF, nyF, dxF, dzF, landF, uW, vW, fRowF, betaRowF, uOutF, vOutF, 0);
    }

    /**
     * @param stepCap 本次求解的步数上限；{@code <=0} 表示用 {@link #MACRO}。
     *                调用方（RelaxedClimate 的外层耦合循环）用它在前 15 轮只做粗略自旋、
     *                只在最后一轮做完整自旋 —— 仍然是 ζ=0 起步，确定性不变。
     *                **必须走参数，不得退回静态字段**（原因见上方注释；TalosContract T6b 守着这条）。
     */
    public static void solve(int nxF, int nyF, double dxF, double dzF, boolean[] landF,
                             double[] uW, double[] vW, double[] fRowF, double[] betaRowF,
                             double[] uOutF, double[] vOutF, int stepCap) {
        long t0 = System.nanoTime();
        SOLVE_CALLS.incrementAndGet();
        int ncx = Math.max(4, Math.min(NCX, nxF)), ncz = Math.max(4, Math.min(NCZ, nyF));
        double hx = nxF * dxF / ncx, hz = nyF * dzF / ncz;
        int nc = ncx * ncz;

        // ---- 粗网格采样（最近邻；粗细网格用采样解耦，不需要整除） ----
        boolean[] land = new boolean[nc];
        double[] uC = new double[nc], vC = new double[nc];
        double[] fC = new double[ncz], bC = new double[ncz];
        for (int y = 0; y < ncz; y++) {
            int fy = (int) Math.min(nyF - 1, Math.floor((y + 0.5) * hz / dzF));
            fC[y] = fRowF[fy];
            bC[y] = betaRowF[fy];
            for (int x = 0; x < ncx; x++) {
                int fx = (int) Math.min(nxF - 1, Math.floor((x + 0.5) * hx / dxF));
                int fi = fy * nxF + fx, i = y * ncx + x;
                land[i] = landF[fi];
                uC[i] = land[i] ? 0 : uW[fi];
                vC[i] = land[i] ? 0 : vW[fi];
            }
        }

        // ---- 风应力平方律 + 旋度 → 源项 ----
        double[] src = new double[nc];
        double maxSrc = 0;
        for (int y = 0; y < ncz; y++) {
            for (int x = 0; x < ncx; x++) {
                int i = y * ncx + x;
                if (land[i]) continue;
                int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                int jp = yIdx(y + 1, ncz) * ncx + x, jm = yIdx(y - 1, ncz) * ncx + x;
                double dTauYdx = (tauY(uC, vC, ip) - tauY(uC, vC, im)) / (2.0 * hx);
                double dTauXdy = (tauX(uC, vC, jp) - tauX(uC, vC, jm)) / (2.0 * hz);
                src[i] = (dTauYdx - dTauXdy) / (RHO_WATER * H_MIXED);
                if (Math.abs(src[i]) > maxSrc) maxSrc = Math.abs(src[i]);
            }
        }
        lastMaxSrc = maxSrc;

        Lv fine = new Lv(ncx, ncz, land);
        Lv[] ls = buildLevels(fine);
        double[] zeta = new double[nc];
        double hbar2 = hx * hz;

        // ---- 伪时间步进到稳态（收敛即提前退出；从 ζ=0 起步 → 确定性）----
        double maxRes = 0;
        int steps = 0;
        double convTol = CONV_TOL * DT * maxSrc + 1e-30;
        int maxIters = stepCap > 0 ? Math.min(stepCap, MACRO) : MACRO;
        for (int it = 0; it < maxIters; it++) {
            steps = it + 1;
            for (int i = 0; i < nc; i++) fine.rhs[i] = land[i] ? 0 : hbar2 * zeta[i];
            for (int v = 0; v < V_CYCLES; v++) vcycle(ls, 0);
            maxRes = 0;
            for (int y = 0; y < ncz; y++) {
                double beta = bC[y] * BETA_SCALE;
                for (int x = 0; x < ncx; x++) {
                    int i = y * ncx + x;
                    if (land[i]) continue;
                    int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                    int jp = yIdx(y + 1, ncz) * ncx + x, jm = yIdx(y - 1, ncz) * ncx + x;
                    double v = (fine.psi[ip] - fine.psi[im]) / (2.0 * hx);
                    double lap = (zeta[ip] - 2 * zeta[i] + zeta[im]) / (hx * hx)
                               + (zeta[jp] - 2 * zeta[i] + zeta[jm]) / (hz * hz);
                    double d = DT * (src[i] - beta * v + A_H * lap);
                    zeta[i] += d;
                    double a = Math.abs(d);
                    if (a > maxRes) maxRes = a;
                    if (a != a) { lastResidual = Double.NaN; return; }   // NaN 早退
                }
            }
            if (steps >= MIN_STEPS && maxRes < convTol) break;
        }
        lastSteps = steps;
        TOTAL_MACRO_STEPS.addAndGet(steps);
        lastResidual = maxRes;
        double maxZ = 0;
        for (int i = 0; i < nc; i++) if (!land[i] && Math.abs(zeta[i]) > maxZ) maxZ = Math.abs(zeta[i]);
        lastMaxZeta = maxZ;

        // ---- 最后一次泊松 → 粗格中心梯度 → 双线性插值回细网格 ----
        for (int i = 0; i < nc; i++) fine.rhs[i] = land[i] ? 0 : hbar2 * zeta[i];
        for (int v = 0; v < Math.max(FINAL_CYCLES, V_CYCLES + 1); v++) vcycle(ls, 0);
        // 泊松残差：max|Lψ − rhs| / max|rhs|
        double pr = 0, prs = 1e-30;
        for (int y = 0; y < ncz; y++) {
            for (int x = 0; x < ncx; x++) {
                int i = y * ncx + x;
                if (land[i]) continue;
                int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                int jp = yIdx(y + 1, ncz) * ncx + x, jm = yIdx(y - 1, ncz) * ncx + x;
                double s = fine.psi[ip] + fine.psi[im] + fine.psi[jp] + fine.psi[jm] - 4.0 * fine.psi[i];
                double r = Math.abs(s - fine.rhs[i]);
                if (r > pr) pr = r;
                if (Math.abs(fine.rhs[i]) > prs) prs = Math.abs(fine.rhs[i]);
            }
        }
        lastPoisRes = pr / prs;
        double[] uG = new double[nc], vG = new double[nc];
        for (int y = 0; y < ncz; y++) {
            for (int x = 0; x < ncx; x++) {
                int i = y * ncx + x;
                if (land[i]) continue;
                int ip = y * ncx + (x + 1) % ncx, im = y * ncx + (x - 1 + ncx) % ncx;
                int jp = yIdx(y + 1, ncz) * ncx + x, jm = yIdx(y - 1, ncz) * ncx + x;
                uG[i] = -(fine.psi[jp] - fine.psi[jm]) / (2.0 * hz);
                vG[i] = (fine.psi[ip] - fine.psi[im]) / (2.0 * hx);
            }
        }
        double mu = 0, mv = 0;
        for (int y = 0; y < nyF; y++) {
            double gy = (y + 0.5) * dzF / hz - 0.5;
            int y0 = (int) Math.floor(gy);
            double ty = gy - y0;
            for (int x = 0; x < nxF; x++) {
                int fi = y * nxF + x;
                if (landF[fi]) { uOutF[fi] = 0; vOutF[fi] = 0; continue; }
                double gx = (x + 0.5) * dxF / hx - 0.5;
                int x0 = (int) Math.floor(gx);
                double tx = gx - x0;
                double u = bl(uG, ncx, ncz, x0, y0, tx, ty);
                double v = bl(vG, ncx, ncz, x0, y0, tx, ty);
                uOutF[fi] = u;
                vOutF[fi] = v;
                if (Math.abs(u) > mu) mu = Math.abs(u);
                if (Math.abs(v) > mv) mv = Math.abs(v);
            }
        }
        lastMaxU = mu;
        lastMaxV = mv;
        lastMs = (System.nanoTime() - t0) / 1e6;
    }

    private static int idw(int y, int x, int nx, int ny) {
        return yIdx(y, ny) * nx + (((x % nx) + nx) % nx);
    }

    private static double bl(double[] f, int nx, int ny, int x0, int y0, double tx, double ty) {
        double a = f[idw(y0, x0, nx, ny)] * (1 - tx) + f[idw(y0, x0 + 1, nx, ny)] * tx;
        double b = f[idw(y0 + 1, x0, nx, ny)] * (1 - tx) + f[idw(y0 + 1, x0 + 1, nx, ny)] * tx;
        return a * (1 - ty) + b * ty;
    }
}
