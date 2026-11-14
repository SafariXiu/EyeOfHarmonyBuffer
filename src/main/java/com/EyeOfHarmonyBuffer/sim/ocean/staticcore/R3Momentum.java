package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * R3 3D 动量海洋。
 *
 * <p><b>原型一</b>（{@link #solve}）：T/S 固定，只算正压风生 psi + Ekman 输送/抽吸诊断 + 投影，
 * 用来验量级/符号/单位/守恒。它不改变 3D 速度场。
 *
 * <p><b>原型一之二</b>（{@link #balance}）：把"热成风诊断"换成**稳态 3D 动量平衡**本身：
 * <pre>
 *   i f w - A_V d/dz(d w/dz) = N,   w = u + i v,   N = -(1/rho0)(dp/dx + i dp/dy)
 * </pre>
 * 这是原始方程（Coriolis + 压力梯度 + 垂向粘性）在定常、忽略平流与非线性的极限。
 * 它与 {@code BaroclinicFlow} 的热成风在"去掉深度平均"后**应当逐点一致**（这就是验证判据），
 * 但它同时给出 v1 完全没有的**经向压力梯度驱动的 u**。
 *
 * <p>坐标系：+j 是否指北由 {@link #northSign} 逐行判定（球面网格 j 从北极到南极 ⟹ -1）。
 * 速度分量 (u, v) 一律是**地理**分量（东, 北），f 用真实纬度。这样换网格不需要改动力学，
 * 也不会把"y 轴朝南"的网格算成反号。
 *
 * <p>刚性盖：本模块只解**斜压模态**（每列去掉深度平均），正压输运由 psi 承担。
 */
public final class R3Momentum {
    private R3Momentum() {}

    /**
     * ★ 2026-11-07（实测裁决）：**暂置 false**。
     *
     * <p>用户裁决是"R3 默认开"，但 2026-11-07 首次耦合生产运行（R3COUP）证明
     * 当前形态还不具备默认开的资格：U_MAX=2.88 / V_MAX=3.21 m/s、W_MAX=6.1e-3 m/s、
     * ETA_MAX=16.1 m（v1 同期 0.027 / 1e-4 / ~1 m），MOC 377.7 Sv。
     * 数值上没炸（NAN=0），但量级不物理：局地地转场在网格尺度没有能量汇，
     * 强 v -> 锋生 -> 更强 v 的正反馈。
     *
     * <p>要重新打开，必须先补**网格尺度的动量耗散/次网格闭合**（GM/Redi 或
     * 由分辨率导出的 A_H），并重跑 D1-D6 + 基线。v1 fallback 保持逐位不变。
     */
    public static boolean ON = Boolean.parseBoolean(
            System.getProperty("eoh.r3", "false"));   // A/B hook: -Deoh.r3=true
    public static final double A_V = AdvectionDiffusionSolver.KV_DEEP;
    public static final double A_H = AdvectionDiffusionSolver.KH;

    /**
     * ★ 2026-11-07（第 22 轮）：网格尺度涡粘（Smagorinsky 型），给 R3 补**能量汇**。
     *
     * <p>实测（§15）：不补闭合时耦合 R3 三年内发散到 V=5.5 m/s / MOC 2145 Sv / S 撞 50 psu。
     * 根因是局地地转速度对 T/S **瞬时**响应，网格尺度密度噪声立刻变成强流。
     *
     * <p>闭合：A_H = (C_s*Delta)^2 * |S|，Delta = sqrt(dx*dy)，|S| = sqrt(2S_ijS_ij)。
     * C_s 取 **1**（不是调参）：Smagorinsky 的 C_s 定义在 [0,1]，C_s=1 表示
     * "未分辨涡的尺度 = 滤波宽度"，是**物理上限**；取小值只会更弱。
     *
     * <p>实现：一次显式 Laplacian 松弛（步长取 0.25*Delta^2/A_Hmax 保证稳定），
     * 之后重新扣列深度平均（刚性盖约束不能破）。
     */
    public static boolean SGS_ON = true;    // 坐标系修正已生效，现在加闭合
    /**
     * ★ 2026-11-07（第 22 轮诊断）：是否把斜压 u 写进状态。
     * <p>v1 完全没有斜压 u；R3 有。耦合实测 U 峰值 1.4–2.3 m/s（v1 同期 0.027）。
     * 关掉 u 只跑 v，可以判定"发散是不是新加的 u 带来的"——这是诊断开关，不是候选方案。
     */
    public static boolean U_ON = true;

    /**
     * Fixed-point under-relaxation for the advective velocity:
     * u_adv = (1-lambda) * u_adv_prev + lambda * u_momentum.
     *
     * <p>Why: every Picard iteration re-solves the momentum from the INSTANTANEOUS T/S.
     * That closed loop can diverge even when the continuous steady state exists (the
     * iteration itself is unstable). Under-relaxation is the standard numerical
     * continuation for such fixed points and does not change the converged solution.
     *
     * <p>1.0 = no relaxation (previous behaviour).
     */
    public static double VEL_RELAX = 0.3;
    private static double[] Uprev, Vprev;

    // ★ 2026-11-12（A→B 整理：退役）：`W_CAP` / `W_CAP_ON` / `capW()` 已删除。
    //   理由（保留在台账，见 Talos2「开关退役名录」）：2026-11-08 第 23 轮实测否决 ——
    //   3 年耦合（限幅 1e-4）= S 从 0 崩到 50、U=7.4、V=11.6、MOC=2508 Sv；
    //   限幅破坏连续性（质量不守恒），盐度直接失控。留代码只会被误开。
    public static double SGS_CS = 1.0;
    /** 上一次 SGS 的 A_H 峰值 [m2/s]（诊断）。 */
    public static double LAST_SGS_AH_MAX = 0.0;

    /**
     * ★★ 2026-11-12（交接文档 §5.2 / NDEEPBUDGET §7.41）：把**水平粘性 A_H 放进动量平衡本身**。
     *
     * <p><b>为什么</b>：本类原先把定常动量方程解成<b>逐列独立</b>的三对角系统，
     * 水平粘性被整项省略（见 {@link #balance} 的 javadoc），A_H 只在解完之后用
     * {@link #applySgs} 做**一次显式松弛**。后果是：稳态解满足的方程里根本没有水平动量通量，
     * 相邻列之间没有耦合，网格尺度的密度噪声直接变成网格尺度的强流
     * （实测 <code>RESID=1.2e-4</code>、<code>U_MAX=1.94 m/s</code>、MOC 在 6 年内 202 -> 489 Sv）。
     * 显式松弛只是事后把场抹平，不改变"解"本身，因此不构成闭合。
     *
     * <p><b>方程</b>（定常、线性、Boussinesq、忽略平流与非线性的极限）：
     * <pre>
     *   i f w - d/dz(A_V dw/dz) - A_H lap_H w = N,     w = u + i v
     * </pre>
     * {@code A_H lap_H w} 一列之外的耦合项：用 Picard（滞后系数）迭代，
     * 把 {@code A_H lap_H w} 从**上一轮**的解取显式，列内的三对角仍然照旧解。
     * 收敛判据是<b>解的改变量</b>（相对 max|w|，{@link #AH_TOL}），迭代上限 {@link #AH_ITER_MAX}。
     *
     * <p><b>A_H 取</b>：与 {@link #applySgs} 同一套 Smagorinsky 型闭合
     * A_H = (C_s·Δ)^2·|S|，Δ = sqrt(dx·dy)，|S| = sqrt(2 S_ij S_ij)，
     * C_s 见 {@link #SGS_CS}。C_s 的定义与出处见那里的说明；本开关只改**位置**（进算子 vs 事后松弛），
     * 不改系数，所以两种读数是同一套闭合的两个用法，可比。
     *
     * <p><b>默认 false</b>：不开时逐位不变（不迭代、不加项、不进 configStamp，符合 N5）。
     * 打开时 {@link #applySgs} 自动关掉（否则同一份 A_H 会被算两遍）。
     */
    public static boolean AH_IN_BALANCE = Boolean.parseBoolean(
            System.getProperty("eoh.ahbal", "false"));   // A/B hook: -Deoh.ahbal=true
    /** Picard 迭代上限（A3-③ 要求收敛判据；上限只是兜底）。 */
    public static int AH_ITER_MAX = 30;
    /** 收敛判据：max|w_new - w_old| / max|w|。 */
    public static double AH_TOL = 1.0e-3;
    /** 上一次 balance() 实际迭代次数（诊断）。 */
    public static int LAST_AH_ITER = 0;
    /** 上一次 balance() 的 A_H 最大值 [m2/s]（诊断）。 */
    public static double LAST_AH_MAX = 0.0;

    // ---- 原型一诊断 ----
    public static double LAST_EK_M_MAX = 0.0;
    public static double LAST_M_MEAN = 0.0;
    public static double LAST_W_E_MAX = 0.0;
    public static double LAST_W_E_MEAN = 0.0;
    public static double LAST_DIV_AFTER = 0.0;
    public static double LAST_PSI_MAX = 0.0;

    // ---- 原型一之二诊断 ----
    public static double LAST_U_MAX = 0.0;
    public static double LAST_V_MAX = 0.0;
    public static double LAST_RESID_MAX = 0.0;
    public static double LAST_COL_INT_MAX = 0.0;
    public static double LAST_VS_THERMAL_MAX = 0.0;
    public static double LAST_VS_THERMAL_RMS = 0.0;
    public static int BALANCE_COUNT = 0;
    public static int STEPS = 0;

    private static OceanGrid ref;
    /** 斜压经向/纬向速度（地理分量：u=东，v=北），m/s。 */
    public static double[] U, V;
    /** 静力异常压 p'（Pa，相对常数 T0/S0）。 */
    public static double[] P;

    public static final double T_REF = 280.0;
    public static final double S_REF = 35.0;

    /** 上一轮 Picard 迭代的速度场（仅 {@link #AH_IN_BALANCE} 用；复用，避免每次分配 2 MB）。 */
    private static double[] Uo, Vo;
    /** 逐列 scratch：A_H、水平 Laplacian 的 u/v 分量（仅 {@link #AH_IN_BALANCE} 用）。 */
    private static double[] AHC, LU, LV;

    private static void ensure(OceanGrid g) {
        if (ref == g && U != null) return;
        ref = g;
        int n = g.nx * g.ny * g.nz;
        U = new double[n];
        V = new double[n];
        P = new double[n];
        Uo = new double[n];
        Vo = new double[n];
        AHC = new double[g.nz];
        LU = new double[g.nz];
        LV = new double[g.nz];
        STEPS = 0;
        BALANCE_COUNT = 0;
    }

    /**
     * +j 是否指向北：球面网格 lat 随 j 单调下降（z=0 北极）⟹ -1；
     * 笛卡尔网格北支 +1、南支 -1（与 {@code WorldContract.latOf} 的三角波一致）。
     * 这是**几何**判定，不是物理假设。
     */
    public static double northSign(OceanGrid g, int j) {
        int jp = Math.min(g.ny - 1, j + 1);
        int jm = Math.max(0, j - 1);
        double d = g.latAt(jp) - g.latAt(jm);
        return d >= 0.0 ? 1.0 : -1.0;
    }

    // ==================================================================================
    // 原型一：正压风生 + Ekman 输送/抽吸诊断 + 投影（T/S 固定）
    // ==================================================================================
    public static void solve(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        BarotropicSolver.update(g, st, f, theta);

        int nx = g.nx, ny = g.ny;
        double[] mx = new double[nx * ny], my = new double[nx * ny];
        double maxM = 0.0, sumM = 0.0;
        int cntM = 0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                int xi = (int) Math.round(g.xOf(i));
                int zj = (int) Math.round(g.zOf(j));
                double tx = f.windStressX(xi, zj, theta);
                double tz = f.windStressZ(xi, zj, theta);
                double lat = g.latAt(j);
                double f0 = WorldContract.coriolis(lat);
                double af = Math.abs(f0);
                double fs = af < 1.0e-5 ? (f0 < 0.0 ? -1.0e-5 : 1.0e-5) : f0;
                int zW = WorldContract.zOfLat(Math.toDegrees(lat));
                double latP = Math.toDegrees(WorldContract.latOf(zW + 1000));
                double latM = Math.toDegrees(WorldContract.latOf(zW - 1000));
                double ns = Math.signum(latP - latM);
                if (ns == 0.0) ns = 1.0;
                double tauN = ns * tz;
                mx[q] = tauN / (AdvectionDiffusionSolver.RHO0 * fs);
                my[q] = -tx / (AdvectionDiffusionSolver.RHO0 * fs);
                double m = Math.hypot(mx[q], my[q]);
                if (m > maxM) maxM = m;
                sumM += m;
                cntM++;
            }
        }
        LAST_EK_M_MAX = maxM;
        LAST_M_MEAN = cntM > 0 ? sumM / cntM : 0.0;

        double maxW = 0.0, sumW = 0.0;
        int cntW = 0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int im = (i - 1 + nx) % nx;
                int ip = (i + 1) % nx;
                double mxE = g.isWet(ip, j) ? 0.5 * (mx[j * nx + i] + mx[j * nx + ip]) : 0.0;
                double mxW = g.isWet(im, j) ? 0.5 * (mx[j * nx + im] + mx[j * nx + i]) : 0.0;
                double myN = g.isWet(i, j + 1) ? 0.5 * (my[j * nx + i] + my[(j + 1) * nx + i]) : 0.0;
                double myS = g.isWet(i, j - 1) ? 0.5 * (my[(j - 1) * nx + i] + my[j * nx + i]) : 0.0;
                double wE = (mxE - mxW) / g.dx + (myN - myS) / g.dy;
                double aw = Math.abs(wE);
                if (aw > maxW) maxW = aw;
                sumW += wE;
                cntW++;
            }
        }
        LAST_W_E_MAX = maxW;
        LAST_W_E_MEAN = cntW > 0 ? sumW / cntW : 0.0;

        VelocityProjection.project(g, st, 3000);
        LAST_DIV_AFTER = VelocityProjection.LAST_DIV_AFTER;

        double pmax = 0.0;
        if (st.psi != null) {
            for (double p : st.psi) if (Math.abs(p) > pmax) pmax = Math.abs(p);
        }
        LAST_PSI_MAX = pmax;
    }

    // ==================================================================================
    // 原型一之二：静力异常压
    // ==================================================================================
    /**
     * p'(z) = ∫_z^0 rho' g dz'（格心取半层 + 上半层累加）。
     * <p>rho' 用与 {@code BaroclinicFlow} 同一套线性 EOS（1027.8 * (-alpha dT + beta dS)），
     * 常数参考值在水平梯度里会消掉，所以 T_REF/S_REF 只影响 p' 的绝对零点，不影响动量。
     */
    public static void computePressure(OceanGrid g, OceanState st) {
        ensure(g);
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 0) continue;
                double acc = 0.0;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double rhoA = BaroclinicFlow.EOS80_ON
                        ? (Eos80.density0K(st.s[q], st.t[q]) - AdvectionDiffusionSolver.RHO0)
                        : AdvectionDiffusionSolver.RHO0
                            * (-Thermohaline.ALPHA * (st.t[q] - T_REF) + Thermohaline.BETA * (st.s[q] - S_REF));
                    double gdz = WorldContract.GRAVITY * g.dz[k];
                    P[q] = acc + 0.5 * rhoA * gdz;
                    acc += rhoA * gdz;
                }
            }
        }
    }

    /**
     * 压力梯度只在**两侧都湿**的格点计算；海岸格（单侧差分会把"陆地=无定义"当成
     * 一个格距内的密度悬崖）返回 0。
     * <p>为什么必须这样：实测生产解局地 |v| 的海岸极值 1.74 m/s、内区 RMS 只有 2.7 cm/s；
     * 1.7 m/s 不是流，是单侧差分把陆海边界当成了锋面。地转平衡在岸格本来也不成立。
     */
    private static double gradX(OceanGrid g, OceanState st, int i, int j, int k) {
        if (!g.isWet(i - 1, j, k) || !g.isWet(i + 1, j, k)) return 0.0;
        return (P[st.idx(i + 1, j, k)] - P[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
    }

    /** 沿**地理北**的导数：模型 +j 方向可能指南，乘 northSign 换成地理分量。 */
    private static double gradN(OceanGrid g, OceanState st, int i, int j, int k, double ns) {
        if (!g.isWet(i, j + 1, k) || !g.isWet(i, j - 1, k)) return 0.0;
        return ns * (P[st.idx(i, j + 1, k)] - P[st.idx(i, j - 1, k)]) / (2.0 * g.dy);
    }

    // ==================================================================================
    // 原型一之二：稳态 3D 动量平衡
    // ==================================================================================
    /**
     * 逐列解三对角复数方程得到斜压 (u, v)。
     *
     * <p>为什么逐列可解：定常动量方程里唯一把相邻列耦合起来的是水平粘性 A_H。
     * （历史，2026-11-07 当时的口径：A_H=1000 m^2/s、dx=278 km 下 O(A_H/(f dx^2)) ~ 1e-3，
     * 比压力梯度和 Coriolis 小三个量级，故<b>整项省略</b>、只留作事后的显式松弛。
     * ⟹ 这条省略在 2026-11-11 被证伪：稳态解里没有任何水平动量汇，R3 的深层环流自我增强，
     * 6 年 MOC 202 -> 489 Sv、深层 +0.1196 K/年。修法见 {@link #AH_IN_BALANCE}：
     * 打开时本函数用 Picard 把 {@code A_H lap_H w} 迭代进方程，关闭时仍走原路径（逐位不变）。）
     * 垂向粘性 A_V 必须保留：它决定海底/海表的无应力条件，并让矩阵可逆。
     *
     * <p>深度平均约束：每列减去 dz 加权的平均，等价于把"正压压力梯度"交给 psi；
     * 剩下的是真正的斜压模态（零净输运）。
     */
    public static void balance(OceanGrid g, OceanState st) {
        ensure(g);
        computePressure(g, st);
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] a = new double[nz], c = new double[nz];
        double[] br = new double[nz], bi = new double[nz];
        double[] dr = new double[nz], di = new double[nz];
        double[] cpR = new double[nz], cpI = new double[nz];
        double[] dpR = new double[nz], dpI = new double[nz];
        double[] wR = new double[nz], wI = new double[nz];
        // 压力梯度项 ε = (N - mean_z N)*dz 的副本：残差要用它，不能读已叠加 A_H 项的 dr/di。
        double[] nR = new double[nz], nI = new double[nz];

        final boolean ahOn = AH_IN_BALANCE;
        int aIters = ahOn ? Math.max(1, AH_ITER_MAX) : 1;
        int aIter = 0;
        double maxU = 0.0, maxV = 0.0, maxRes = 0.0, maxInt = 0.0;
        for (int it = 0; it < aIters; it++) {
        aIter = it + 1;
        maxU = 0.0; maxV = 0.0; maxRes = 0.0; maxInt = 0.0;
        LAST_AH_MAX = 0.0;
        if (ahOn) {
            System.arraycopy(U, 0, Uo, 0, U.length);
            System.arraycopy(V, 0, Vo, 0, V.length);
        }
        for (int j = 1; j < ny - 1; j++) {
            double ns = northSign(g, j);
            double lat = g.latAt(j);
            double f = WorldContract.coriolis(lat);
            // 赤道附近 f->0：只影响 |lat|<0.03°（那里 gate=0，结果被清零），防止除零产生 NaN。
            double fs = Math.abs(f) < 1.0e-6 ? (f < 0.0 ? -1.0e-6 : 1.0e-6) : f;
            double alatDeg = Math.abs(Math.toDegrees(lat));
            double gate = 1.0;
            if (alatDeg < 25.0) gate = 0.0;
            else if (alatDeg < 30.0) gate = (alatDeg - 25.0) / 5.0;

            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 0) continue;

                double sumNU = 0.0, sumNV = 0.0, H = 0.0;
                for (int k = 0; k < wl; k++) {
                    double px = gradX(g, st, i, j, k);
                    double pn = gradN(g, st, i, j, k, ns);
                    double nu = -px / AdvectionDiffusionSolver.RHO0;
                    double nv = -pn / AdvectionDiffusionSolver.RHO0;
                    dr[k] = nu; di[k] = nv;
                    sumNU += nu * g.dz[k]; sumNV += nv * g.dz[k];
                    H += g.dz[k];
                }
                double mNU = sumNU / H, mNV = sumNV / H;
                for (int k = 0; k < wl; k++) {
                    nR[k] = (dr[k] - mNU) * g.dz[k];
                    nI[k] = (di[k] - mNV) * g.dz[k];
                    dr[k] = nR[k];
                    di[k] = nI[k];
                }
                // ★ AH_IN_BALANCE：把 A_H lap_H w 从**上一轮**的解取出来当源项（Picard）。
                //   方程 i f w - d_z(A_V d_z w) - A_H lap_H w = N  ⇒  右端 += A_H lap_H w。
                //   每个方程乘 dz；同样去掉列深度均值（刚性盖约束）。
                if (ahOn) {
                    double meanAR = 0.0, meanAI = 0.0;
                    for (int k = 0; k < wl; k++) {
                        double ahk = smagAhAt(g, st, Uo, Vo, i, j, k);
                        double lu = lapH(g, st, Uo, i, j, k);
                        double lv = lapH(g, st, Vo, i, j, k);
                        AHC[k] = ahk; LU[k] = lu; LV[k] = lv;
                        if (ahk > LAST_AH_MAX) LAST_AH_MAX = ahk;
                        double addR = ahk * lu * g.dz[k];
                        double addI = ahk * lv * g.dz[k];
                        dr[k] += addR; di[k] += addI;
                        meanAR += addR; meanAI += addI;
                    }
                    meanAR /= H; meanAI /= H;
                    for (int k = 0; k < wl; k++) {
                        dr[k] -= meanAR * g.dz[k];
                        di[k] -= meanAI * g.dz[k];
                    }
                }

                for (int k = 0; k < wl; k++) {
                    double dz = g.dz[k];
                    double dzu = (k + 1 < wl) ? 0.5 * (dz + g.dz[k + 1]) : dz;
                    double dzd = (k > 0) ? 0.5 * (g.dz[k - 1] + dz) : dz;
                    double bR = 0.0;
                    double bI = fs * dz;
                    a[k] = 0.0;
                    c[k] = 0.0;
                    if (k > 0) { a[k] = -A_V / dzd; bR += A_V / dzd; }
                    if (k + 1 < wl) { c[k] = -A_V / dzu; bR += A_V / dzu; }
                    br[k] = bR; bi[k] = bI;
                }

                // 复数 Thomas
                for (int k = 0; k < wl; k++) {
                    double dR = br[k], dI = bi[k];
                    if (k > 0 && a[k] != 0.0) {
                        dR -= a[k] * cpR[k - 1];
                        dI -= a[k] * cpI[k - 1];
                    }
                    double den = dR * dR + dI * dI;
                    if (den < 1e-300) den = 1e-300;
                    double dRr = dr[k], dIr = di[k];
                    if (k > 0 && a[k] != 0.0) {
                        dRr -= a[k] * dpR[k - 1];
                        dIr -= a[k] * dpI[k - 1];
                    }
                    dpR[k] = (dRr * dR + dIr * dI) / den;
                    dpI[k] = (dIr * dR - dRr * dI) / den;
                    cpR[k] = (c[k] * dR) / den;
                    cpI[k] = (-c[k] * dI) / den;
                }
                wR[wl - 1] = dpR[wl - 1];
                wI[wl - 1] = dpI[wl - 1];
                for (int k = wl - 2; k >= 0; k--) {
                    wR[k] = dpR[k] - (cpR[k] * wR[k + 1] - cpI[k] * wI[k + 1]);
                    wI[k] = dpI[k] - (cpR[k] * wI[k + 1] + cpI[k] * wR[k + 1]);
                }

                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    U[q] = wR[k] * gate;
                    V[q] = wI[k] * gate;
                }
                for (int k = wl; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    U[q] = 0.0; V[q] = 0.0;
                }
                // 数值兜底：再扣一次深度平均（gate 与舍入可能留下残差）
                double mu = 0.0, mv = 0.0;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    mu += U[q] * g.dz[k];
                    mv += V[q] * g.dz[k];
                }
                mu /= H; mv /= H;
                double intU = 0.0, intV = 0.0;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    U[q] -= mu; V[q] -= mv;
                    intU += U[q] * g.dz[k];
                    intV += V[q] * g.dz[k];
                }
                maxInt = Math.max(maxInt, Math.max(Math.abs(intU), Math.abs(intV)));
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    if (Math.abs(U[q]) > maxU) maxU = Math.abs(U[q]);
                    if (Math.abs(V[q]) > maxV) maxV = Math.abs(V[q]);
                }
                // 动量残差：|i f w - A_V d2w/dz2 - N|（去平均后）
                for (int k = 0; k < wl; k++) {
                    double dz = g.dz[k];
                    double dzu = (k + 1 < wl) ? 0.5 * (dz + g.dz[k + 1]) : dz;
                    double dzd = (k > 0) ? 0.5 * (g.dz[k - 1] + dz) : dz;
                    double lapR = 0.0, lapI = 0.0;
                    if (k + 1 < wl) {
                        lapR += A_V * (U[st.idx(i, j, k + 1)] - U[st.idx(i, j, k)]) / dzu;
                        lapI += A_V * (V[st.idx(i, j, k + 1)] - V[st.idx(i, j, k)]) / dzu;
                    }
                    if (k > 0) {
                        lapR -= A_V * (U[st.idx(i, j, k)] - U[st.idx(i, j, k - 1)]) / dzd;
                        lapI -= A_V * (V[st.idx(i, j, k)] - V[st.idx(i, j, k - 1)]) / dzd;
                    }
                    lapR /= dz; lapI /= dz;
                    int q = st.idx(i, j, k);
                    // 注意：解的是 N' = N - mean_z(N)，所以残差里只能用 N'（dr[k]/dz），
                    // 不能再加回 mNU/mNV —— 否则残差恒等于 -mean_z(N)（那是正压压力梯度，不是误差）。
                    // 残差里必须包含 A_H 项，否则 A_H_IN_BALANCE 开时它会把 A_H 的贡献当成误差。
                    double ahR = ahOn ? AHC[k] * LU[k] : 0.0;
                    double ahI = ahOn ? AHC[k] * LV[k] : 0.0;
                    double resU = -fs * V[q] - lapR - ahR - (nR[k] / g.dz[k]);
                    double resV = fs * U[q] - lapI - ahI - (nI[k] / g.dz[k]);
                    double rr = Math.hypot(resU, resV);
                    if (rr > maxRes) maxRes = rr;
                }
            }
        }
        if (!ahOn) break;
        // ★ 收敛判据（A3-③ 要求的那一条）：**解的改变量** / max|w|，
        //   不用残差为零做判据（A6：浮点里 a-b≈0 不可测）。
        double dU = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double du = Math.abs(U[q] - Uo[q]);
                    double dv = Math.abs(V[q] - Vo[q]);
                    if (du > dU) dU = du;
                    if (dv > dU) dU = dv;
                }
            }
        }
        double wScale = Math.max(maxU, maxV);
        if (wScale <= 0.0) break;
        if (dU <= AH_TOL * wScale) break;
        }

        LAST_U_MAX = maxU;
        LAST_V_MAX = maxV;
        LAST_RESID_MAX = maxRes;
        LAST_COL_INT_MAX = maxInt;
        LAST_AH_ITER = ahOn ? aIter : 0;
        BALANCE_COUNT++;
    }

    /**
     * 水平 Laplacian（x 周期、y 墙；干格用**镜像**：邻格不湿就取本格值 ⇙ 零法向通量）。
     * 与 {@link #applySgs} 的定义口径一致，只是这里显式算而不做时间推进。
     */
    private static double lapH(OceanGrid g, OceanState st, double[] f, int i, int j, int k) {
        int q = st.idx(i, j, k);
        double f0 = f[q];
        double fe = g.isWet(i + 1, j, k) ? f[st.idx(i + 1, j, k)] : f0;
        double fw = g.isWet(i - 1, j, k) ? f[st.idx(i - 1, j, k)] : f0;
        double fn = g.isWet(i, j + 1, k) ? f[st.idx(i, j + 1, k)] : f0;
        double fs = g.isWet(i, j - 1, k) ? f[st.idx(i, j - 1, k)] : f0;
        return (fe + fw - 2.0 * f0) / (g.dx * g.dx) + (fn + fs - 2.0 * f0) / (g.dy * g.dy);
    }

    /**
     * Smagorinsky 型涡粘 A_H = (C_s·Δ)^2 |S|，Δ^2 = dx·dy，|S| = sqrt(2 S_ij S_ij)。
     * 与 {@link #applySgs} 的离散**逐字一致**（同一个 s2 表达式、同一个 Δ），
     * 这样“事后松弛”与“进算子”两个读数是同一套闭合的两种用法，可比。
     */
    private static double smagAhAt(OceanGrid g, OceanState st, double[] u, double[] v, int i, int j, int k) {
        double dx = g.dx, dy = g.dy;
        boolean xe = g.isWet(i + 1, j, k), xw = g.isWet(i - 1, j, k);
        boolean yn = g.isWet(i, j + 1, k), ys = g.isWet(i, j - 1, k);
        double dudx = (xe && xw) ? (u[st.idx(i + 1, j, k)] - u[st.idx(i - 1, j, k)]) / (2.0 * dx) : 0.0;
        double dvdx = (xe && xw) ? (v[st.idx(i + 1, j, k)] - v[st.idx(i - 1, j, k)]) / (2.0 * dx) : 0.0;
        double dudy = (yn && ys) ? (u[st.idx(i, j + 1, k)] - u[st.idx(i, j - 1, k)]) / (2.0 * dy) : 0.0;
        double dvdy = (yn && ys) ? (v[st.idx(i, j + 1, k)] - v[st.idx(i, j - 1, k)]) / (2.0 * dy) : 0.0;
        double s2 = 2.0 * (dudx * dudx + dvdy * dvdy) + (dudy + dvdx) * (dudy + dvdx);
        return SGS_CS * SGS_CS * (dx * dy) * Math.sqrt(Math.max(0.0, s2));
    }

    /**
     * 与 v1 热成风（{@link BaroclinicFlow#vBaroclinic}）比较：|lat| 在 [30, 60] 且两侧都湿的内区。
     * 只比 v（v1 没有 u）。
     */
    public static void compareThermalWind(OceanGrid g, OceanState st) {
        double mx = 0.0, ss = 0.0;
        int n = 0;
        for (int j = 1; j < g.ny - 1; j++) {
            double alat = Math.abs(Math.toDegrees(g.latAt(j)));
            if (alat < 30.0 || alat > 60.0) continue;
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                if (!g.isWet(i - 1, j) || !g.isWet(i + 1, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                double ns = northSign(g, j);
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double d = ns * V[q] - st.vBaroclinic[q];
                    mx = Math.max(mx, Math.abs(d));
                    ss += d * d;
                    n++;
                }
            }
        }
        LAST_VS_THERMAL_MAX = mx;
        LAST_VS_THERMAL_RMS = n > 0 ? Math.sqrt(ss / n) : 0.0;
    }

    // ==================================================================================
    // 耦合入口：把 R3 的斜压速度写回状态，并诊断 w 与 MOC
    // ==================================================================================

    /** R3 口径的翻转（最宽盆地、cos 加权、ψ 峰值 × 宽度）。 */
    public static double LAST_MOC_SV = 0.0;

    /**
     * 幂等：先撤掉旧斜压分量，再算新的。正压基态由 {@code BarotropicSolver} 提供
     * （调用前 st.u/st.v 里是正压风生流）。
     */
    public static void applyToState(OceanGrid g, OceanState st) {
        ensure(g);
        int n = st.u.length;
        for (int q = 0; q < n; q++) {
            st.u[q] -= st.uBaroclinic[q];
            st.v[q] -= st.vBaroclinic[q];
        }
        balance(g, st);
        // ★ 必须在写回状态**之前**做 SGS 平滑，否则平滑的是副本、状态没变（第 22 轮踩过）。
        // ★ AH_IN_BALANCE 开时，同一份 A_H 已进动量方程；再做一次显式松弛就是双重计、
        //   而且会把已满足平衡的解再抹一遍（那正是旧路径的毛病）。
        if (SGS_ON && !AH_IN_BALANCE) applySgs(g, st);
        if (VEL_RELAX < 1.0) {
            if (Uprev == null || Uprev.length != U.length) {
                Uprev = U.clone();
                Vprev = V.clone();
            } else {
                for (int q = 0; q < U.length; q++) {
                    U[q] = (1.0 - VEL_RELAX) * Uprev[q] + VEL_RELAX * U[q];
                    V[q] = (1.0 - VEL_RELAX) * Vprev[q] + VEL_RELAX * V[q];
                }
            }
            System.arraycopy(U, 0, Uprev, 0, U.length);
            System.arraycopy(V, 0, Vprev, 0, V.length);
        }
        // ★★ 2026-11-07（第 22 轮，关键修正）：R3 的 V 是**地理北向**分量，
        //   而 OceanState.v/vF 是**网格 +j 分量**。球面网格 +j 指南（lat 随 j 下降，
        //   northSign=-1），所以写回状态前必须乘 northSign，否则经向平流整体反号
        //   —— 这正是耦合"发散"的根因：T/S 被反号的斜压 v 平流，与原有状态对撞出锋面。
        for (int j = 1; j < g.ny - 1; j++) {
            double ns = northSign(g, j);
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double uu = U_ON ? U[q] : 0.0;
                    st.uBaroclinic[q] = uu;
                    st.vBaroclinic[q] = ns * V[q];
                    st.u[q] += uu;
                    st.v[q] += ns * V[q];
                }
            }
        }
        st.fillDryGhosts();
        diagnoseW(g, st);
        VelocityProjection.project(g, st, 3000);
        // ★ 2026-11-08：绝热次网格输运（GM）。必须在投影之后加到面速度上。
        if (EddyTransport.ON) EddyTransport.apply(g, st);
        // 2026-11-11: EddyTransport adds a bolus transport directly to the face velocities, i.e.
        // AFTER the projection, so the total field is no longer divergence-free (measured:
        // CONT 5.9e-23 -> 2.509e-07). Project again so the TOTAL transport is non-divergent;
        // a physically constructed GM (from a streamfunction) is already non-divergent, so this
        // only removes the part the current implementation adds spuriously.
        if (EddyTransport.ON) VelocityProjection.project(g, st, 3000);
        diagnoseMoc(g, st);
    }

    /** 网格尺度涡粘：一次显式 Laplacian 松弛，再重扣深度平均。 */
    static void applySgs(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny;
        double dx = g.dx, dy = g.dy;
        double delta2 = dx * dy;
        double[] ah = new double[U.length];
        double aMax = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    boolean xe = g.isWet(i + 1, j, k), xw = g.isWet(i - 1, j, k);
                    boolean yn = g.isWet(i, j + 1, k), ys = g.isWet(i, j - 1, k);
                    double dudx = (xe && xw) ? (U[st.idx(i + 1, j, k)] - U[st.idx(i - 1, j, k)]) / (2.0 * dx) : 0.0;
                    double dvdx = (xe && xw) ? (V[st.idx(i + 1, j, k)] - V[st.idx(i - 1, j, k)]) / (2.0 * dx) : 0.0;
                    double dudy = (yn && ys) ? (U[st.idx(i, j + 1, k)] - U[st.idx(i, j - 1, k)]) / (2.0 * dy) : 0.0;
                    double dvdy = (yn && ys) ? (V[st.idx(i, j + 1, k)] - V[st.idx(i, j - 1, k)]) / (2.0 * dy) : 0.0;
                    double s2 = 2.0 * (dudx * dudx + dvdy * dvdy) + (dudy + dvdx) * (dudy + dvdx);
                    double smag = Math.sqrt(Math.max(0.0, s2));
                    double a = SGS_CS * SGS_CS * delta2 * smag;
                    ah[q] = a;
                    if (a > aMax) aMax = a;
                }
            }
        }
        LAST_SGS_AH_MAX = aMax;
        if (aMax <= 0.0) return;
        double dtRelax = 0.25 * delta2 / aMax;
        double[] un = new double[U.length], vn = new double[V.length];
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double u0 = U[q], v0 = V[q];
                    int ie = g.isWet(i + 1, j, k) ? st.idx(i + 1, j, k) : q;
                    int iw = g.isWet(i - 1, j, k) ? st.idx(i - 1, j, k) : q;
                    int inn = g.isWet(i, j + 1, k) ? st.idx(i, j + 1, k) : q;
                    int is = g.isWet(i, j - 1, k) ? st.idx(i, j - 1, k) : q;
                    double lu = (U[ie] + U[iw] - 2.0 * u0) / (dx * dx) + (U[inn] + U[is] - 2.0 * u0) / (dy * dy);
                    double lv = (V[ie] + V[iw] - 2.0 * v0) / (dx * dx) + (V[inn] + V[is] - 2.0 * v0) / (dy * dy);
                    un[q] = u0 + dtRelax * ah[q] * lu;
                    vn[q] = v0 + dtRelax * ah[q] * lv;
                }
            }
        }
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                double mu = 0.0, mv = 0.0, H = 0.0;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    U[q] = un[q]; V[q] = vn[q];
                    mu += U[q] * g.dz[k]; mv += V[q] * g.dz[k]; H += g.dz[k];
                }
                if (H <= 0.0) continue;
                mu /= H; mv /= H;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    U[q] -= mu; V[q] -= mv;
                }
            }
        }
    }


    /** 连续方程从海底向上积分（底 w=0）；与 BaroclinicFlow.LOCAL_W_ON 同一口径。 */
    static void diagnoseW(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 0) continue;
                double wAcc = 0.0;
                for (int k = wl - 1; k >= 0; k--) {
                    int q = st.idx(i, j, k);
                    double uE = g.isWet(i + 1, j, k) ? st.u[st.idx(i + 1, j, k)] : st.u[q];
                    double uW = g.isWet(i - 1, j, k) ? st.u[st.idx(i - 1, j, k)] : st.u[q];
                    double vN = g.isWet(i, j + 1, k) ? st.v[st.idx(i, j + 1, k)] : st.v[q];
                    double vS = g.isWet(i, j - 1, k) ? st.v[st.idx(i, j - 1, k)] : st.v[q];
                    double divH = (uE - uW) / (2.0 * g.dx) + (vN - vS) / (2.0 * g.dy);
                    double wTop = wAcc - g.dz[k] * divH;
                    st.w[q] = 0.5 * (wAcc + wTop);
                    wAcc = wTop;
                }
                for (int k = wl; k < g.nz; k++) st.w[st.idx(i, j, k)] = 0.0;
            }
        }
    }

    /** 简版翻转诊断：最宽连续湿段、cos 加权、ψ 由斜压 v 自底向上积分。 */
    static void diagnoseMoc(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny;
        double best = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            int run = 0, bestRun = 0, bestStart = -1;
            for (int i = 0; i < nx; i++) {
                if (g.isWet(i, j)) {
                    if (run == 0) { }
                    run++;
                    if (run > bestRun) { bestRun = run; bestStart = i - run + 1; }
                } else run = 0;
            }
            if (g.isWet(0, j) && g.isWet(nx - 1, j)) {
                int a = 0; while (a < nx && g.isWet(a, j)) a++;
                int b = nx - 1; while (b >= 0 && g.isWet(b, j)) b--;
                int w = a + (nx - 1 - b);
                if (w > bestRun) { bestRun = w; bestStart = b + 1; }
            }
            if (bestRun <= 0) continue;
            int wl = 0;
            for (int m = 0; m < bestRun; m++) {
                int i = ((bestStart + m) % nx + nx) % nx;
                wl = Math.max(wl, g.wetLayers[g.idx(i, j)]);
            }
            if (wl <= 0) continue;
            double[] vbar = new double[wl];
            int c = 0;
            for (int m = 0; m < bestRun; m++) {
                int i = ((bestStart + m) % nx + nx) % nx;
                int lw = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < lw; k++) vbar[k] += st.vBaroclinic[st.idx(i, j, k)];
                c++;
            }
            if (c == 0) continue;
            for (int k = 0; k < wl; k++) vbar[k] /= c;
            double psi = 0.0, mx = 0.0;
            for (int k = wl - 1; k >= 0; k--) { psi += vbar[k] * g.dz[k]; mx = Math.max(mx, Math.abs(psi)); }
            best = Math.max(best, mx * (bestRun * g.dx * g.cosLat(j)) / 1.0e6);
        }
        LAST_MOC_SV = best;
    }
}