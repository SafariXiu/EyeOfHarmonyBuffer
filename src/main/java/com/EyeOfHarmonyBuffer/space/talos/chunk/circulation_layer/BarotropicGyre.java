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
     * 宏观时间步上限：DT×MACRO 需 ≥ 环流建立时间（≈8 天 = 7e5 s）。
     *
     * ===== 2026-09 实测（P222，生产窗口 tileX=0，输入冻结）=====
     * <pre>
     *   MACRO | 宏观步 |      ζ残差 | 收敛阈值   | 残差/阈值 | RMS v   |  u/v
     *    5000 |   5000 |  8.767e-10 | 4.301e-10  |   203.8%  | 0.14153 | 1.96   ← 顶到上限，没收敛
     *   10000 |   7112 |  4.300e-10 | 4.301e-10  |   100.0%  | 0.15161 | 2.04   ← 靠 CONV_TOL 提前退出
     *   20000 |   7112 |  4.300e-10 | —          |   100.0%  | 0.15161 | 2.04
     *   40000 |   7112 |  4.300e-10 | —          |   100.0%  | 0.15161 | 2.04   ← 与 10000 逐位相同
     * </pre>
     * ⇒ 旧的 5000 **把自旋截断在未收敛处**（残差是阈值的 2 倍），海洋流速因此系统性偏弱：
     * 生产 RMS v 从 0.14153（截断）到 0.15161（收敛）= **+7.1%**。真正的收敛点是 7112 步，
     * 而 7112 是**窗口的确定函数**，不是可以调的旋钮。
     *
     * 取 10000 而不是 7112：留 ~40% 余量，让"收敛所需步数"随窗口/种子波动时仍能靠
     * CONV_TOL 自己退出（顶到上限就等于又把它变成一个任意截断）。20000/40000 与 10000
     * 逐位相同 ⇒ 大于收敛点之后加步数**不改变结果**，只花时间。
     *
     * 代价（P222 [3]，每个档位重建整个气候窗口）：
     * 单窗构建 7231 ms → 10310 ms（**+43%**，+3.1 s / 窗，100 km 瓦片跨一次触发一次，
     * 走后台预热路径）。这是"让海洋真的收敛"必须付的钱。
     */
    public static int MACRO = 10000;
    /**
     * 每个宏观步的 V-cycle 次数。**生产取 1**（最省，且实测不是杠杆）。
     *
     * ===== 2026-09 实测（P222 [1]，生产窗口 tileX=0，输入冻结，只改这一个变量）=====
     * <pre>
     *  V_CYCLES |  耗时ms | 泊松残差 | RMS u   | RMS v   |  u/v  | 西/东 | 峰值|x|占比
     *      1    |    1289 |  1.85e-04 | 0.27766 | 0.14153 |  1.96 |  0.99 |     0.017
     *      2    |    2472 |  9.47e-05 | 0.27759 | 0.14149 |  1.96 |  0.99 |     0.017
     *      4    |    4849 |  4.97e-05 | 0.27755 | 0.14147 |  1.96 |  0.99 |     0.017
     * </pre>
     * ⇒ 场差异只有 **0.048%**，成本是严格线性（×3.76）⇒ **V_CYCLES 不是杠杆**。
     *
     * <h3>为什么与本文件旧注释的结论相反（那段话已被删除，理由记在这里）</h3>
     * 旧注释的依据是**理想闭合海盆 128×128** 的实验：那里 V_CYCLES=1 得到西/东=0.80、
     * |v| 峰值在海盆中央，V_CYCLES>=4 才出现 Munk 解。但那是**另一种几何**：
     * 两侧有连续的经向墙。生产窗口是"散布大陆 + X 环绕"，两条结论不能互相搬运。
     * 实测把两者都放在了同一张表上：**生产窗口的西/东 = 0.99，且与 V_CYCLES 无关** ——
     * 也就是说生产窗口里**根本没有西边界流**，这跟 V_CYCLES 无关，是**几何**问题
     * （见下一条待办），把 V_CYCLES 从 1 提到 4 只会多花 3.76 倍时间买 0.048%。
     *
     * <h3>机制（为什么 1 次 V-cycle 就够）</h3>
     * 宏观循环本身就是"对缓慢变化的 rhs 反复解泊松"：{@link #MACRO} 步里 ψ 被迭代精炼了
     * 上万次，单步做几个 V-cycle 并不重要。泊松残差那一列的差异只是**最后一次**解的好坏。
     *
     * <h3>待办（已不是"调参"问题）</h3>
     * 西/东 = 0.99 ⇒ 生产窗口里没有西边界强化。下一步要查的是**几何**：
     * β 平面上的西边界流需要一条连续经向墙来闭合 Sverdrup 输运；生产窗口的
     * X 是**窗口内环绕**（halo padding）且大陆是散布的，"每行最靠西的海格"并不构成一条墙。
     * 归档记录里"0/419024 个连通分量缺少经向墙"这条也要按同一把尺子重新核一遍。
     */
    public static int V_CYCLES = 1;
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
     * Y（= Z 轴）索引：**越界 clamp 到 [0, ny-1]**，与 {@code ClimateGridData.idx()} 的 Z 规则一致。
     *
     * ===== 定案记录（2026-09；这里曾经是开关 `public static boolean WRAP_Y`）=====
     * 原先默认 {@code true}，模板用 {@code (y±1+ncz)%ncz} 把 Z 接回环。那与世界的 Z 契约
     * （Z 的周期只控制气候，地形/海陆/洋流在 Z 上不重复）直接冲突，也与
     * {@code ClimateGridData} 已经改好的 clamp + 真实 halo 行冲突 —— 等于只修了一半。
     *
     * 受控实验（P210，生产窗口 tileX=0，同一批格点，**只改这一个变量**）：
     * <pre>
     *   true  → RMS u=0.22087  v=0.05545   u/v = 3.98
     *   false → RMS u=0.25279  v=0.12310   u/v = 2.05      ← 经向流翻一倍多
     * </pre>
     * 负对照（海区上下被陆封死、碰不到 Z 接缝）：开关两侧**所有统计量逐位相同**
     * ⇒ 上表的差异不是噪声。
     *
     * ⇒ **取 clamp，并把开关本身删掉**：不留"两种世界都能跑"的手柄。
     * 契约行 **T4a**（本类不得再出现 Z 周期开关）与 **T4b**（本方法必须 clamp）守着这条不再复发。
     *
     * ⚠ 这是一次**会改变世界海洋**的改动：经向流翻倍、u/v 由 3.98 降到 2.05，
     * 海洋热输运与沿岸海温随之变化（P195 signstats 指纹在本次改动后应当改变）。
     */
    static int yIdx(int y, int ny) {
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
        /**
         * **虚拟墙**专用掩码（只含墙，不含天然陆地）；{@code null} = 本层没有墙。
         *
         * 它只被 {@link #buildLevels} 用来做**保守粗化**：天然陆地仍走"4 个子格里 ≥2 个是陆"
         * 的历史判据（保证 wall=null 的调用点逐位不变），而墙只要**命中任意一个子格**就整格算墙。
         * 原因：极地墙只有 2 个求解器格厚，按 ≥2 粗化会在第 2 层就整条消失，粗层校正于是能穿过墙 ——
         * 细层每轮 smooth 都会把墙上的 ψ 压回 0，但粗层注入的光滑误差会在 MACRO 步里
         * 累积成真实的穿墙通量。保守粗化让墙在每一层都保持 ≥1 格厚（实测 128→64→32 全程不消失）。
         */
        final boolean[] wall;
        final double[] psi, rhs, res;
        Lv(int nx, int ny, boolean[] land) {
            this(nx, ny, land, null);
        }
        Lv(int nx, int ny, boolean[] land, boolean[] wall) {
            this.nx = nx; this.ny = ny; this.n = nx * ny; this.land = land; this.wall = wall;
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
            boolean[] wall = cur.wall == null ? null : new boolean[nx * ny];
            for (int y = 0; y < ny; y++) {
                for (int x = 0; x < nx; x++) {
                    int c = 0, wc = 0;
                    if (cur.land[cur.id(2 * x, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y)]) c++;
                    if (cur.land[cur.id(2 * x, 2 * y + 1)]) c++;
                    if (cur.land[cur.id(2 * x + 1, 2 * y + 1)]) c++;
                    if (wall != null) {
                        if (cur.wall[cur.id(2 * x, 2 * y)]) wc++;
                        if (cur.wall[cur.id(2 * x + 1, 2 * y)]) wc++;
                        if (cur.wall[cur.id(2 * x, 2 * y + 1)]) wc++;
                        if (cur.wall[cur.id(2 * x + 1, 2 * y + 1)]) wc++;
                        wall[y * nx + x] = wc > 0;
                    }
                    land[y * nx + x] = c >= 2 || wc > 0;
                }
            }
            cur = new Lv(nx, ny, land, wall);
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
        solve(nxF, nyF, dxF, dzF, landF, null, uW, vW, fRowF, betaRowF, uOutF, vOutF, stepCap);
    }

    /** 陆或墙 —— 求解器里的"不可流格"。{@code wallF} 为 null 时退化成纯陆判据。 */
    private static boolean blocked(boolean[] landF, boolean[] wallF, int fi) {
        return landF[fi] || (wallF != null && wallF[fi]);
    }

    /**
     * 带**虚拟墙**的求解（极地硬墙 C1；墙的几何由 {@code PolarZone} 单一口径给出）。
     *
     * @param wallF 与 {@code landF} 同形的墙掩码；{@code null} = 无墙。
     *              墙格与陆格在求解器里**完全同权**：ψ=0、ζ 不更新、风应力旋度不注入、
     *              输出流速为 0 —— 数学上就是一条岸线。唯一的区别是它**不写回** {@code landF}，
     *              所以地形、群系、渲染、玩家碰撞都看不到它（玩家不会撞到一堵空气墙）。
     *              粗化时的区别见 {@link Lv#wall}。
     */
    public static void solve(int nxF, int nyF, double dxF, double dzF, boolean[] landF, boolean[] wallF,
                             double[] uW, double[] vW, double[] fRowF, double[] betaRowF,
                             double[] uOutF, double[] vOutF, int stepCap) {
        long t0 = System.nanoTime();
        SOLVE_CALLS.incrementAndGet();
        int ncx = Math.max(4, Math.min(NCX, nxF)), ncz = Math.max(4, Math.min(NCZ, nyF));
        double hx = nxF * dxF / ncx, hz = nyF * dzF / ncz;
        int nc = ncx * ncz;

        // ---- 粗网格采样（最近邻；粗细网格用采样解耦，不需要整除） ----
        boolean[] land = new boolean[nc];
        boolean[] wall = wallF == null ? null : new boolean[nc];
        double[] uC = new double[nc], vC = new double[nc];
        double[] fC = new double[ncz], bC = new double[ncz];
        for (int y = 0; y < ncz; y++) {
            int fy = (int) Math.min(nyF - 1, Math.floor((y + 0.5) * hz / dzF));
            fC[y] = fRowF[fy];
            bC[y] = betaRowF[fy];
            for (int x = 0; x < ncx; x++) {
                int fx = (int) Math.min(nxF - 1, Math.floor((x + 0.5) * hx / dxF));
                int fi = fy * nxF + fx, i = y * ncx + x;
                boolean w = blocked(landF, wallF, fi);
                if (wall != null) wall[i] = w && !landF[fi];
                land[i] = w;
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

        Lv fine = new Lv(ncx, ncz, land, wall);
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
                if (blocked(landF, wallF, fi)) { uOutF[fi] = 0; vOutF[fi] = 0; continue; }
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
