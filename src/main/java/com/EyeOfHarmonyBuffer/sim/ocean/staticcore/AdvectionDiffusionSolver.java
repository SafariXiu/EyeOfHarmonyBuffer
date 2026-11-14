package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.ClimberOcean;
import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;
import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 稳态平流-扩散 T/S 组件。
 *
 * <p>u*dT/dx + v*dT/dy = KH*del2_h(T) + KV*d2T/dz2；
 * 表面 Dirichlet；底部/陆地零通量；x 周期；上风格式。
 * u/v 来自 BarotropicSolver（w=0）。
 */
public final class AdvectionDiffusionSolver {

    public static final double KH = 1000.0;

    /**
     * ★ 2026-11-03：KH 的 Bryan & Lewis (1979) 剖面（ClimberOcean.blKh：1000 + 4000 exp(-z/500)）。
     * 常数 KH=1000 是论文的深层值；表层应为 5000。默认关，A/B 用 NKHBL=1。
     */
    public static boolean KH_BL_ON = false;
    public static double khAt(OceanGrid g, int k) {
        return KH_BL_ON ? ClimberOcean.blKh(g.zCenter[k]) : KH;
    }

    /**
     * 混合层有效垂向扩散：H^2/tau，H=50m, tau=30d ⟹ 9.65e-4 m²/s。
     *
     * <p>★★ 2026-11-02 判定性实验（已完成，结论：原先是 KV 误用）：
     * 把混合层值 9.65e-4 用在**所有层**时，温跃层被抹平（hDiag=968.6 m、60% 格点 &gt;500 m、
     * 亚冰点 274、冰盖 835）。改成【分层】——混合层内 {@link #KV}、层下 {@link #KV_DEEP}
     * ——之后：hDiag=390.8 m、&gt;500 m 降到 19.9%、亚冰点 67、冰盖 220、OHT 峰值 0.406→1.231 PW。
     * 全局替换 1e-4（连表层也用）会崩（T 217–2452 K、99.7% 冰盖），所以必须分层。
     */
    public static final double KV = 50.0 * 50.0 / (30.0 * 86400.0);   // 9.65e-4（混合层值；全局替换已实测失败）

    /**
     * 深层垂向扩散：Munk (1966), "Abyssal recipes", Deep-Sea Research 13:707–730，
     * 由深层热收支反演得 K ≈ 1e-4 m²/s。用于【混合层以下】；混合层内仍用 {@link #KV}。
     */
    public static final double KV_DEEP = 1.0e-4;

    /**
     * 分层垂向扩散率：格心在混合层内 → {@link #KV}；以下 → {@link #KV_DEEP}。
     * <p>★ 2026-11-02 判定性实验：混合层用 H²/τ（9.65e-4），深层用 Munk 的 1e-4。
     * 若弱层结是本世界的值，这一刀不应改变 hDiag/亚冰点；若改变，则原先是 KV 误用。
     */
    static double kvAt(OceanGrid g, OceanState st, int i, int j, int k) {
        return g.zCenter[k] < st.mixedLayerDepth[j * g.nx + i] ? KV : KV_DEEP;
    }
    public static final double SW_R = 0.58, SW_Z1 = 0.35, SW_Z2 = 23.0;
    public static double qswLayer(double qsw0, double[] dz, int k) {
        double zt = 0.0;
        for (int i = 0; i < k; i++) zt += dz[i];
        double zb = zt + dz[k];
        double ft = SW_R * Math.exp(-zt / SW_Z1) + (1 - SW_R) * Math.exp(-zt / SW_Z2);
        double fb = SW_R * Math.exp(-zb / SW_Z1) + (1 - SW_R) * Math.exp(-zb / SW_Z2);
        return qsw0 * (ft - fb);
    }
    /**
     * 穿透短波的稳态源项 [K/s]：k&gt;=1 的每层吸收 qswLayer/(rho*Cp*dz)。
     * k=0 的那一份留在表面通量里（Q_net - qsw0 + qswLayer(0)），不重复计入。
     * <p>★ 2026-11-02：季节循环下必须用源项而不是"每季 += dt*Qsw"——后者每季只加不减，
     * 12 个月会把次表层热成 367 K（实测）。
     */
    static double[] shortwaveSource(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] src = new double[nx * ny * nz];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                double qsw0 = f.shortwaveWm2((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), theta, st.t[st.idx(i, j, 0)]);
                for (int k = 1; k < nz; k++)
                    src[st.idx(i, j, k)] = qswLayer(qsw0, g.dz, k) / (RHO0 * CP_SEA * g.dz[k]);
            }
        return src;
    }

    /**
     * Semtner (1976) 0 层冰的【冰面热通量】：解
     * <pre>  q_surf(T_s) + K_ICE*(T_f − T_s)/h = 0 </pre>
     * 返回进入冰面的净通量 [W/m2]（正 = 融化，负 = 结冰）。
     * <p>★ 2026-11-02：静态核心此前**没有这一项**——把大气净通量直接当结冰能量，
     * 冰厚不提供绝热 ⟹ 单季可结 100+ m 冰 ⟹ 盐析反馈 ⟹ 雪球。
     * 常数：K_ICE=2.0 W/m/K、rho_i=917、L_f=3.34e5（Semtner 1976，ClimberOcean 已登记）。
     */
    static double iceSurfaceFlux(SeaSurfaceForcing f, double theta, int x, int z,
                                 double tf, double hIce) {
        double qf = f.netHeatFluxWm2(x, z, theta, tf, 1.0, hIce);
        if (!Double.isFinite(qf)) return 0.0;
        if (qf >= 0.0) return qf;                 // 冰面在冰点以上 ⟹ 融化
        double h = Math.max(0.05, hIce);
        double lo = tf - 40.0, hi = tf;
        for (int it = 0; it < 24; it++) {
            double mid = 0.5 * (lo + hi);
            double q = f.netHeatFluxWm2(x, z, theta, mid, 1.0, h);
            double fv = q + ClimberOcean.K_ICE * (tf - mid) / h;
            if (fv > 0.0) lo = mid; else hi = mid;
        }
        double ts = 0.5 * (lo + hi);
        return ClimberOcean.K_ICE * (tf - ts) / h;   // 负 = 海洋向冰面失热
    }

    /**
     * ★ 2026-11-09（阶段 2 配套）：平流形式开关。
     *
     * <p>false = 散度形式 {@code div(u f)}；true = 平流形式 {@code div(u f) - f div(u)}。
     * 当投影后仍有散度残差时，散度形式会注入 {@code -f div(u)} 的虚假源。实测（2.5°、
     * 去掉全柱重置后、一季）散度形式把 T 推到 632 K、S 撞 50；平流形式给出
     * T 271-306 K、S 33.6-35.7、深层 4 km 271-272 K。**默认 true**。
     *
     * <p>这不是参数化，而是同一方程在离散层面的另一种写法：速度场不是严格无散时，
     * 平流形式对该残差免疫。等 3D 投影把 max|div| 压到 <1e-9 后，可再 A/B 是否恢复
     * 散度形式以取得严格守恒。
     */
    public static boolean ADVECTIVE_FORM = true;

    /** 面速度的散度 div(u)（与 {@link #divFlux} 同一套面/迎风口径）。 */
    public static double divU(OceanGrid g, OceanState st, int i, int j, int k) {
        int q = st.idx(i, j, k);
        double dx = g.dx, dy = g.dy, dz = g.dz[k];
        if (VelocityProjection.FACES_READY) {
            double uE = st.uF[q], uW = st.uF[st.idx(i - 1, j, k)];
            double vN = st.vF[q], vS = st.vF[st.idx(i, j - 1, k)];
            // 2026-11-11 (ruling 甲): the surface face must carry the free-surface velocity,
            // exactly as VelocityProjection.faceDiv does. Otherwise the advection scheme sees a
            // divergence that the projection has already removed (measured: divFlux reported
            // 1.16e-06 while faceDiv reported 5.5e-23 for the same state).
            double wT = (k > 0) ? st.wF[st.idx(i, j, k - 1)] : VelocityProjection.surfW(i, j, g.nx);
            double wB = st.wF[q];
            return (uE - uW) / dx + (vN - vS) / dy + (wT - wB) / dz;
        }
        double u = st.u[q], v = st.v[q], w = st.w[q];
        double fe = g.isWet(i + 1, j, k) ? u : 0.0;
        double fw = g.isWet(i - 1, j, k) ? st.u[st.idx(i - 1, j, k)] : 0.0;
        double fn = g.isWet(i, j + 1, k) ? v : 0.0;
        double fs = g.isWet(i, j - 1, k) ? st.v[st.idx(i, j - 1, k)] : 0.0;
        double ft = (k > 0) ? w : 0.0;
        double fb = (k + 1 < g.nz && g.isWet(i, j, k + 1)) ? st.w[st.idx(i, j, k + 1)] : 0.0;
        return (fe - fw) / dx + (fn - fs) / dy + (ft - fb) / dz;
    }

    /**
     * ★ 2026-11-02（守恒 flux-form，面速度=本格速度）：div(u T)。
     * <p>东/上界面用【本格】u/w，西/下界面用【邻格】u/w ⟹ 相邻两格对同一面算出同一通量，
     * Σ divFlux·dV 严格等于边界通量（墙/底为 0 ⟹ 0）。
     * <p>为什么不用"两侧平均面速度"：平均速度会让面速度大于格心速度，而 SOR 的对角
     * 仍按格心速度构造，实测对角被低估 ⟹ 盐 SOR 发散（salt -12.6%、clamp 1.4e5）。
     * 本格速度的面通量则与 |u|/dx 对角严格匹配。
     */
    public static double divFlux(OceanGrid g, OceanState st, double[] fld, int i, int j, int k) {
        int q = st.idx(i, j, k);
        double dx = g.dx, dy = g.dy, dz = g.dz[k];
        if (VelocityProjection.FACES_READY) {
            // C 网格面速度分支（投影后）；迎风取面两侧的供体格
            double uE = st.uF[q];
            double fe = uE * (uE >= 0.0 ? fld[q] : fld[st.idx(i + 1, j, k)]);
            int qw = st.idx(i - 1, j, k);
            double uW = st.uF[qw];
            double fw = uW * (uW >= 0.0 ? fld[qw] : fld[q]);
            double vN = st.vF[q];
            double fn = vN * (vN >= 0.0 ? fld[q] : fld[st.idx(i, j + 1, k)]);
            int qs = st.idx(i, j - 1, k);
            double vS = st.vF[qs];
            double fs = vS * (vS >= 0.0 ? fld[qs] : fld[q]);
            double wT = (k > 0) ? st.wF[st.idx(i, j, k - 1)] : VelocityProjection.surfW(i, j, g.nx);
            // At k = 0 there is no cell above: a DOWNWARD surface flow (wT < 0) must not read
            // fld[k-1] (index -1 -> AIOOBE). Zero-gradient is the consistent choice.
            double ft = (k > 0) ? wT * (wT >= 0.0 ? fld[q] : fld[st.idx(i, j, k - 1)]) : wT * fld[q];
            double fb = 0.0;
            if (k + 1 < g.nz) {
                double wB = st.wF[q];
                fb = wB * (wB >= 0.0 ? fld[st.idx(i, j, k + 1)] : fld[q]);
            }
            return (fe - fw) / dx + (fn - fs) / dy + (ft - fb) / dz;
        }
        double u = st.u[q], v = st.v[q], w = st.w[q];
        double fe, fw, fn, fs, ft, fb;
        if (g.isWet(i + 1, j, k)) {
            fe = u >= 0.0 ? u * fld[q] : u * fld[st.idx(i + 1, j, k)];
        } else fe = 0.0;
        if (g.isWet(i - 1, j, k)) {
            int qw = st.idx(i - 1, j, k);
            double uw = st.u[qw];
            fw = uw >= 0.0 ? uw * fld[qw] : uw * fld[q];
        } else fw = 0.0;
        if (g.isWet(i, j + 1, k)) {
            fn = v >= 0.0 ? v * fld[q] : v * fld[st.idx(i, j + 1, k)];
        } else fn = 0.0;
        if (g.isWet(i, j - 1, k)) {
            int qs = st.idx(i, j - 1, k);
            double vs = st.v[qs];
            fs = vs >= 0.0 ? vs * fld[qs] : vs * fld[q];
        } else fs = 0.0;
        if (k > 0) {
            ft = w >= 0.0 ? w * fld[q] : w * fld[st.idx(i, j, k - 1)];
        } else ft = 0.0;
        if (k + 1 < g.nz) {
            int qb = st.idx(i, j, k + 1);
            double wb = st.w[qb];
            fb = wb >= 0.0 ? wb * fld[qb] : wb * fld[q];
        } else fb = 0.0;
        return (fe - fw) / dx + (fn - fs) / dy + (ft - fb) / dz;
    }

    /**
     * ★ 2026-11-11（深层收支仪器）：{@link #divFlux} 里【只有垂直】的那一部分，
     * 含 ADVECTIVE_FORM 的 {@code -f·divU} 修正项（与 {@link #divFlux} 同口径、同符号）。
     *
     * <p>只给 {@link DeepBudget} 读，用于把"垂直平流"从三维平流里拆出来。
     * 默认路径不调用 ⟹ 与改动前逐位相同。
     */
    public static double advVerticalPart(OceanGrid g, OceanState st, double[] fld, int i, int j, int k) {
        int q = st.idx(i, j, k);
        double dz = g.dz[k];
        double ft, fb, divV;
        if (VelocityProjection.FACES_READY) {
            double wT = (k > 0) ? st.wF[st.idx(i, j, k - 1)] : VelocityProjection.surfW(i, j, g.nx);
            ft = (k > 0) ? wT * (wT >= 0.0 ? fld[q] : fld[st.idx(i, j, k - 1)]) : wT * fld[q];
            fb = 0.0;
            double wB = st.wF[q];
            if (k + 1 < g.nz) fb = wB * (wB >= 0.0 ? fld[st.idx(i, j, k + 1)] : fld[q]);
            divV = (wT - wB) / dz;
        } else {
            double w = st.w[k];
            ft = (k > 0) ? (w >= 0.0 ? w * fld[q] : w * fld[st.idx(i, j, k - 1)]) : 0.0;
            fb = 0.0;
            double wb = 0.0;
            if (k + 1 < g.nz) {
                int qb = st.idx(i, j, k + 1);
                wb = st.w[qb];
                fb = wb >= 0.0 ? wb * fld[qb] : wb * fld[q];
            }
            divV = ((k > 0 ? w : 0.0) - wb) / dz;
        }
        double out = (ft - fb) / dz;
        if (ADVECTIVE_FORM) out -= fld[q] * divV;
        return out;
    }

    /**
     * flux-form 的对角线：本格/邻格面速度的"流出"系数之和（与 {@link #divFlux} 严格对应）。
     */
    public static double diagAdv(OceanGrid g, OceanState st, int i, int j, int k) {
        int q = st.idx(i, j, k);
        if (VelocityProjection.FACES_READY) {
            double uE = st.uF[q], uW = st.uF[st.idx(i - 1, j, k)];
            double vN = st.vF[q], vS = st.vF[st.idx(i, j - 1, k)];
            double wT = (k > 0) ? st.wF[st.idx(i, j, k - 1)] : VelocityProjection.surfW(i, j, g.nx);
            double wB = st.wF[q];
            double d = 0.0;
            d += (uE > 0.0 ? uE : 0.0) / g.dx + (uW < 0.0 ? -uW : 0.0) / g.dx;
            d += (vN > 0.0 ? vN : 0.0) / g.dy + (vS < 0.0 ? -vS : 0.0) / g.dy;
            d += (wT > 0.0 ? wT : 0.0) / g.dz[k] + (wB < 0.0 ? -wB : 0.0) / g.dz[k];
            return d;
        }
        double u = st.u[q], v = st.v[q], w = st.w[q];
        double d = 0.0;
        d += (u >= 0.0 ? u : 0.0) / g.dx;
        if (g.isWet(i - 1, j, k)) { double uw = st.u[st.idx(i - 1, j, k)]; d += (uw < 0.0 ? -uw : 0.0) / g.dx; }
        d += (v >= 0.0 ? v : 0.0) / g.dy;
        if (g.isWet(i, j - 1, k)) { double vs = st.v[st.idx(i, j - 1, k)]; d += (vs < 0.0 ? -vs : 0.0) / g.dy; }
        if (k > 0) d += (w >= 0.0 ? w : 0.0) / g.dz[k];
        if (k + 1 < g.nz) { double wb = st.w[st.idx(i, j, k + 1)]; d += (wb < 0.0 ? -wb : 0.0) / g.dz[k]; }
        return d;
    }

    public static final double SOR = 0.3;
    public static final double RHO0 = 1027.8;
    /** 盐收支诊断（每次 solveSalinity 刷新）。 */
    public static double LAST_SALT0 = 0.0, LAST_SALT1 = 0.0, LAST_CORR = 0.0, LAST_ICE = 0.0;
    public static int LAST_CLAMP = 0;
    /** 内部冰点约束累计生成的冰厚 [m]（诊断）。 */
    public static double LAST_INTERIOR_ICE = 0.0;
    public static final double CP_SEA = 3985.0;

    /**
     * ★ 2026-11-02 修复：T/S 平流原来**只有 u、v 两项**，{@code OceanState.w} 被
     * {@link BaroclinicFlow#apply} 写入后全仓无人读取（全仓 grep：只有写入点）。
     * 后果：翻转环流的垂直支路缺失，而 {@code ∂v/∂y + ∂w/∂z = 0} 要求两者成对出现；
     * 只平流 v 会把「上升/下沉携带的热量」整项漏掉，热收支因此无法闭合。
     * <p>关闭本开关 = 回到 2026-11-02 之前的行为（逐位相同），仅用于 A/B 与回溯。
     */
    public static boolean VERTICAL_ADVECTION_ON = true;

    /**
     * ★ 2026-11-07：局地埃克曼输送/抽吸 → 混合层卷吸（默认关）。
     * <p>w_E = ∂M_E/∂x + ∂M_N/∂y，M=(τ_N,−τ_E)/(ρf)；陆面法向通量=0；f→0 用 {@link #EKMAN_F_MIN} 线性衰减。
     * <p>关闭时整段跳过，不新增任何数组/调用 ⇒ 与改动前逐位相同；开关条件进 solveFlagsHash。
     */
    public static boolean EKMAN_UPWELL_ON = false;
    /** f 的数值正则化下限（s^-1；锚点 |lat|=3.93°）。不是物理常数。 */
    public static final double EKMAN_F_MIN = 1.0e-5;
    public static double LAST_W_EK_MAX = 0.0;
    public static double LAST_W_EK_MEAN = 0.0;
    public static int LAST_W_EK_UPWELL_CELLS = 0;
    public static int LAST_W_EK_CELLS = 0;
    /** A 方案诊断：投影后的总 wF 在 ML 底的最大/平均（斜压+埃克曼）。 */
    public static double LAST_W_TOTAL_MAX = 0.0;
    public static double LAST_W_TOTAL_MEAN = 0.0;
    /** A 方案诊断：ML 水平平流项 |uML∇T| 的最大值 [K/s]、wBase 最大值 [m/s]。 */
    public static double LAST_ML_ADV_MAX = 0.0;
    public static double LAST_ML_WBASE_MAX = 0.0;
    /** A-ekman：解析埃克曼 w / 输送；ML 只用埃克曼分量，不混入正压/斜压。 */
    public static double[] EK_W = null;
    public static double[] EK_MX = null, EK_MY = null;

    /**
     * ★ 2026-11-03（stage 1）：表面混合层盐收支，替换 dz0 的 a0 扩散平衡。
     * 方程 rho h dS/dt = -S Fw - rho h adv + rho KV (Sb-S)/delta（设计稿 §3.1）。
     * 默认关；A/B 用 NMLS=1。总盐仍由 solveSalinity 的 corr 收口。
     */
    public static boolean ML_SALT_ON = true;

    /**
     * ★ 2026-11-03（stage 3）：T 求解内冰点 active-set（T≥Tf(S)，全水柱）。
     * 默认关；A/B 用 NFREEZE=1。潜热/盐析记账暂未并入（先验证约束本身）。
     */
    public static boolean FREEZE_ACTIVE_ON = true;

    /**
     * ★ 2026-11-04（A6 质量档评估）：两个"迭代深度"旋钮。
     *
     * <p>架构 §8 的 FAST 档写的是 "outer 1、substeps 15"，对应这里的两个数。
     * <b>默认保持现行值（3 / 30）⟹ 逐位不变</b>；把它们调小是**换解**，不是优化，
     * 所以必须先实测影响面（`NQUALITY`）再谈是否用于生产。
     *
     * <ul>
     *   <li>{@link #OUTER_MAX}：每季内"表面 bulk 根 ↔ 内部 T"的外层次数。A1 实测残差
     *       outer=1 → 4.4e-3、outer=2 → 1.0e-4、最后一遍 → 4.0e-6（K）。</li>
     *   <li>{@link #SUBSTEPS}：prognostic T/S 每季的显式子步数（30 ⟹ Δt=1 天；15 ⟹ Δt=2 天）。</li>
     * </ul>
     */
    /**
     * ★ 2026-11-04（A6 实测）：**3 → 1**。`NQUALITY` 两步测量：
     * <ul>
     *   <li>单次求解：(3,30) 63 s / (2,30) 44 s / (1,30) 44 s，三者**逐位相同**；</li>
     *   <li>生产耦合路径（3 次求解）：(3,30) 151 s vs (1,30) 134 s，同样**逐位相同**
     *       （max|dT|=0、max|dSST|=0、max|dh|=0、dMOC=0）。</li>
     * </ul>
     * ⟹ 第 2/3 遍外层是**冗余工作**（从收敛态再解一次得到同一个解），去掉它不改变任何数值。
     * 这是"实现层面优化、数值逐位不变"（2026-10-24 用户裁决）允许的那一类。
     */
    public static int OUTER_MAX = 1;
    /**
     * 每季 prognostic 子步数。**保持 30**：实测 30 → 15 会改解
     * （max|dT| 1.204 K、max|dSST| 0.403 K、max|dh| 170 m），不是优化，是换模型。
     * 架构 §8 的 "FAST 档（substeps 15）" 因此**不能用于生产**。
     */
    public static int SUBSTEPS = 30;

    /**
     * ★ 2026-11-09（阶段 3 / A5）：一季的物理秒数。由 {@link StaticOceanSolver} 按
     * {@code DAYS_PER_YEAR*86400/nseas} 设置。
     *
     * <p>旧版把每季硬编码成 30 天 ⟹ 4 季“一年”只有 120 天，热/盐/冰的年积分少 3.04×。
     * 这是时间轴口径错误，不是调参。
     */
    public static double SEASON_SECONDS = 30.0 * 86400.0;

    /**
     * ★ 2026-11-11：**一季对应的子步数**（纯函数，不再依赖可变字段 {@link #SUBSTEPS}）。
     *
     * <p>为什么必须有：{@code SUBSTEPS} 是在 {@code StaticOceanSolver.solve()} 里按季长改写的
     * （默认 30 ⟶ 4 季时变成 92）。而 {@code StaticOceanProduction.solveFlagsHash()} 读的是这个字段
     * ⟹ **求解前与求解后算出的指纹不同** ⟹ 第二次调用必然重建，磁盘缓存永远命不中
     * （实测 NCACHEDISK：PASS2 仍走 compute，274 s vs 266 s，加速 1.0×）。
     * <p>这不是缓存本身的错，是"指纹读了一个会被求解改写的量"——A6 的"缓存键漏了输入"同类。
     */
    public static int substepsFor(int nseas) {
        double sec = WorldContract.DAYS_PER_YEAR * 86400.0 / Math.max(1, nseas);
        return Math.max(30, (int) Math.ceil(sec / 86400.0));
    }

    /** ★ 2026-11-07（R3）：显式通量平流允许的最大库朗数。 */
    public static double CFL_MAX = 0.5;
    /** ★ 自适应子步上限（防止病态场把一次求解拖死）。 */
    public static int CFL_MAX_STEPS = 800;

    /**
     * ★ 2026-11-07（R3）：显式通量平流的 CFL 速率 [1/s]：
     * max(|uF|/dx + |vF|/dy + |wF|/dz)；要求 dt &lt;= CFL_MAX/rate。
     *
     * <p>为什么需要：SUBSTEPS=30 对应 dt=1 天。v1 的 w 被 W_MAX(1e-4) 限幅，CFL_z ~ 0.17；
     * R3 的 w 来自连续方程（耦合实测 6e-3 m/s），CFL_z = 6e-3*86400/50 ~ 10
     * ⟹ 显式垂直平流直接不稳定。这是**数值稳定性**要求，不是调参。
     */
    public static double cflRate(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny;
        double rate = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double rr = Math.abs(st.uF[q]) / g.dx
                              + Math.abs(st.vF[q]) / g.dy
                              + Math.abs(st.wF[q]) / g.dz[k];
                    if (rr > rate) rate = rr;
                }
            }
        return rate;
    }

    /** 按 CFL 把子步数抬到够用（不降低调用方给的下限）。 */
    public static int cflSteps(OceanGrid g, OceanState st, double seconds, int minSteps) {
        double rate = cflRate(g, st);
        int steps = Math.max(1, minSteps);
        if (rate > 0.0) {
            int need = (int) Math.ceil(seconds * rate / CFL_MAX);
            if (need > steps) steps = Math.min(need, Math.max(steps, CFL_MAX_STEPS));
        }
        return steps;
    }

    /**
     * ★ 2026-11-03（stage 4）：T 用季节内 prognostic flux-form，替代稳态 BiCGSTAB。
     * 默认关；A/B 用 NPROGT=1。
     */
    public static boolean PROGNOSTIC_T_ON = true;

    /**
     * ★ 2026-11-03（stage 3 步序）：盐度求解后重钳【表层】T 到最终的 Tf(S)。
     * 默认关；A/B 用 NSURF=1。修的是"ML/冰步用旧 sbar 的 Tf、盐度后又变"的步序缺口。
     */
    public static boolean FREEZE_SURFACE_ON = true;

    /**
     * 混合层底判据：密度跳变 0.03 kg/m³（de Boyer Montégut et al. 2004, JGR 109 C12003；
     * Holte &amp; Talley 2009）。
     * <p>★ 2026-11-02：h 改为 Kraus–Turner 卷吸方程的**时间积分**（见 updateMixedLayer），
     * 不再用"B&lt;0 就 h=hLimit"的瞬时整柱对流。多年循环由 StaticOceanSolver 负责。
     */
    public static final double MLD_JUMP_KGM3 = 0.03;

    /**
     * 垂直平流的迎风离散（k 向下增大；w>0 = 上升，上游在下方 k+1）。
     * <p>与水平两项同口径：{@code w * dT/dz} 的迎风形式，不是通量形式。
     */
    public static double advZ(OceanState st, int i, int j, int k, double t, double dz) {
        if (!VERTICAL_ADVECTION_ON || k <= 0) return 0.0;
        double w = st.w[st.idx(i, j, k)];
        if (w == 0.0) return 0.0;
        double tUp = st.t[st.idx(i, j, k - 1)];                       // 上方（k-1）
        // ★ 2026-11-04：海底以下（k+1 ≥ wetLayers）是干格，数组里是 0；
        //   旧式只看 k+1 < nz ⟹ 在海底读 0 ⟹ 稳态/时间步都会把 T/S 抽干（实测 S→0.84）。
        double tDn = (k + 1 < st.grid.nz && k + 1 < st.grid.wetLayers[st.grid.idx(i, j)])
                   ? st.t[st.idx(i, j, k + 1)] : t;  // 下方（k+1；海底以下零梯度）
        return w >= 0.0 ? w * (t - tDn) / dz : w * (tUp - t) / dz;
    }

    /** 对角线里对应 |w|/dz 的那一项（与 {@link #advZ} 成对使用）。 */
    public static double advZDiag(OceanState st, int i, int j, int k, double dz) {
        if (!VERTICAL_ADVECTION_ON || k <= 0) return 0.0;
        return Math.abs(st.w[st.idx(i, j, k)]) / dz;
    }

    private AdvectionDiffusionSolver() {}

    public static final class Result {
        public final int iterations;
        public final double residual;
        Result(int iterations, double residual) {
            this.iterations = iterations; this.residual = residual;
        }
    }

    /**
     * ★ 2026-11-09（阶段 2）：只做冷启动的 S 兜底，**不再把整列 T 重置成表层温度**。
     *
     * <p>旧版对 {@code k=0..nz-1} 执行 {@code st.t[q]=ts}——这是 P0-1/P0-3 的“全柱重置”
     * 语义，任何调用方都会把深层结构抹掉。T 的初值由 {@link StaticOceanSolver} 的冷启动
     * 剖面给出；表层 Dirichlet 只作用在 k=0（见 {@link #solve(OceanGrid,OceanState,double[],int)}）。
     */
    public static void init(OceanGrid g, OceanState st, double[] surfaceT) {
        int nx = g.nx, ny = g.ny;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    if (st.s[q] == 0.0) st.s[q] = 35.0;
                }
            }
    }

    /**
     * 局地埃克曼抽吸/沿岸辐散 w_E [m/s]；正=上升。只读风应力与网格，纯函数（除诊断静态）。
     * <p>M_E=taper*τ_N/(ρ f_eff)，M_N=−taper*τ_E/(ρ f_eff)；到陆的面取 0（法向无通量）。
     * <p>方向：windStressZ 是 world +z；northSign=sign(latOf(zW+dz)−latOf(zW−dz)) 转北向。
     */
    static double[] ekmanUpwelling(OceanGrid g, SeaSurfaceForcing f, double theta) {
        int nx = g.nx, ny = g.ny, n2 = nx * ny;
        double[] tx = new double[n2], tz = new double[n2];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                tx[q] = f.windStressX((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
                tz[q] = f.windStressZ((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
            }
        double[] mx = new double[n2], my = new double[n2];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                double lat = g.latAt(j);
                double f0 = WorldContract.coriolis(lat);
                double af = Math.abs(f0);
                double taper = af < EKMAN_F_MIN ? af / EKMAN_F_MIN : 1.0;
                double fs = af < EKMAN_F_MIN ? (f0 < 0.0 ? -EKMAN_F_MIN : EKMAN_F_MIN) : f0;
                int zW = WorldContract.zOfLat(Math.toDegrees(lat));
                double latP = Math.toDegrees(WorldContract.latOf(zW + 1000));
                double latM = Math.toDegrees(WorldContract.latOf(zW - 1000));
                double northSign = Math.signum(latP - latM);
                if (northSign == 0.0) northSign = 1.0;
                double tauN = northSign * tz[q];
                double tauE = tx[q];
                mx[q] = taper * tauN / (RHO0 * fs);
                my[q] = -taper * tauE / (RHO0 * fs);
            }
        double[] w = new double[n2];
        double max = 0.0, sum = 0.0;
        int up = 0, cnt = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                double mxE = g.isWet(i + 1, j) ? 0.5 * (mx[q] + mx[g.idx(i + 1, j)]) : 0.0;
                double mxW = g.isWet(i - 1, j) ? 0.5 * (mx[q] + mx[g.idx(i - 1, j)]) : 0.0;
                double myN = g.isWet(i, j - 1) ? 0.5 * (my[q] + my[g.idx(i, j - 1)]) : 0.0;
                double myS = g.isWet(i, j + 1) ? 0.5 * (my[q] + my[g.idx(i, j + 1)]) : 0.0;
                double we = (mxE - mxW) / g.dx + (myN - myS) / g.dy;
                w[q] = we;
                if (Math.abs(we) > max) max = Math.abs(we);
                sum += we; cnt++;
                if (we > 0.0) up++;
            }
        LAST_W_EK_MAX = max;
        LAST_W_EK_MEAN = cnt > 0 ? sum / cnt : 0.0;
        LAST_W_EK_UPWELL_CELLS = up;
        LAST_W_EK_CELLS = cnt;
        return w;
    }

    /** 局地埃克曼输送 M_E/M_N [m²/s]（A 方案用；与 v1 的 ekmanUpwelling 同一公式，只是返回输送而不是 w）。 */
    static double[][] ekmanTransport(OceanGrid g, SeaSurfaceForcing f, double theta) {
        int nx = g.nx, ny = g.ny, n2 = nx * ny;
        double[] tx = new double[n2], tz = new double[n2];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                tx[q] = f.windStressX((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
                tz[q] = f.windStressZ((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
            }
        double[] mx = new double[n2], my = new double[n2];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                double lat = g.latAt(j);
                double f0 = WorldContract.coriolis(lat);
                double af = Math.abs(f0);
                double taper = af < EKMAN_F_MIN ? af / EKMAN_F_MIN : 1.0;
                double fs = af < EKMAN_F_MIN ? (f0 < 0.0 ? -EKMAN_F_MIN : EKMAN_F_MIN) : f0;
                int zW = WorldContract.zOfLat(Math.toDegrees(lat));
                double latP = Math.toDegrees(WorldContract.latOf(zW + 1000));
                double latM = Math.toDegrees(WorldContract.latOf(zW - 1000));
                double northSign = Math.signum(latP - latM);
                if (northSign == 0.0) northSign = 1.0;
                double tauN = northSign * tz[q];
                double tauE = tx[q];
                mx[q] = taper * tauN / (RHO0 * fs);
                my[q] = -taper * tauE / (RHO0 * fs);
            }
        return new double[][]{mx, my};
    }

    /**
     * A 方案：把埃克曼输送作为三维速度加到 u/v（ML 深度 h 内 M/h，h 以下回流 -M/(H-h)），
     * 再做无散投影；ML 底的 wF 就是埃克曼抽吸/沿岸上升流。无源项、无调参、无 NH/SH。
     */
    static void applyEkmanFlow(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        // 诊断：先算解析埃克曼 w（v1 的 LAST_W_EK_*），再算投影后的总 wF（LAST_W_TOTAL_*）。
        EK_W = ekmanUpwelling(g, f, theta);
        double[][] m = ekmanTransport(g, f, theta);
        double[] mx = m[0], my = m[1];
        EK_MX = mx;
        EK_MY = my;
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double H = g.columnDepthM(i, j);
                double h = Math.max(g.dz[0], Math.min(st.mixedLayerDepth[js], H));
                double below = Math.max(0.0, H - h);
                // 几何边界：内部薄于埃克曼层（H−h<h）时，-M/(H−h) 会快于表层 M/h（非物理）；
                // 浅水/ML 触底不做埃克曼层+回流。这是模型边界，不是可调参数。
                if (below < h) continue;
                double uE = mx[js] / h, vE = my[js] / h;
                double uR = below > 0.0 ? -mx[js] / below : 0.0;
                double vR = below > 0.0 ? -my[js] / below : 0.0;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    if (g.zCenter[k] < h) { st.u[q] += uE; st.v[q] += vE; }
                    else { st.u[q] += uR; st.v[q] += vR; }
                }
            }
        VelocityProjection.project(g, st, 3000);
        double max = 0.0, sum = 0.0;
        int up = 0, cnt = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                double H = g.columnDepthM(i, j);
                double h = Math.max(g.dz[0], Math.min(st.mixedLayerDepth[j * nx + i], H));
                int wl = g.wetLayers[g.idx(i, j)];
                int kb = 0;
                double zc = 0.0;
                while (kb < wl - 1 && zc + 0.5 * g.dz[kb] <= h) { zc += g.dz[kb]; kb++; }
                if (kb <= 0) continue;
                double wb = st.wF[st.idx(i, j, kb - 1)];
                if (Math.abs(wb) > max) max = Math.abs(wb);
                sum += wb; cnt++;
                if (wb > 0.0) up++;
            }
        LAST_W_TOTAL_MAX = max;
        LAST_W_TOTAL_MEAN = cnt > 0 ? sum / cnt : 0.0;
    }

    /**
     * 通量边界：表面不再固定 T，而由 Qnet(Ts) = -Kv dT/dz 决定。
     * 外层固定点：解一次 Dirichlet T -> 用 T1 与 Qnet 更新 Ts -> 再解。
     */
    public static Result solveFluxBoundary(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta,
                                           int maxIter, int outerMax) {
        int nx = g.nx, ny = g.ny;
        // ★ 2026-11-02（甲）：surfaceT 从【当前海温】续起，而不是每次从大气气候值重置。
        // 否则季节循环没有海洋热记忆：每季都从同一个 oceanBaseK 冷启动。
        double[] surfaceT = new double[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                surfaceT[j * nx + i] = g.isWet(i, j)
                        ? st.t[st.idx(i, j, 0)]
                        : Atmosphere.oceanBaseK(g.latAt(j));
        // ★ 2026-11-03（stage 2）：冰覆盖的单一来源 = state.iceFraction。
        // 从当前表层 T 初始化（仍是 ClimberOcean.iceFraction 解析式，Petoukhov 2000）；
        // 下游净热/反照率/诊断统一读这个状态，不再各自重算。
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                // ★ 2026-11-09（阶段 3）：冰分数按冰厚连续化，而不是 0/1 二值。
                //   二值在冰缘造成反照率跳变 ⟹ 年际翻转（实测 58.8°N 表层年际 ΔT=5.28 K）。
                //   参考厚度用 Radiation.H_ICE_REF（UCAR 484 的 0.5 m，不是新常数）。
                st.iceFraction[j * nx + i] = Math.min(1.0,
                        st.iceThickness[j * nx + i] / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
            }
        double res = 0.0;
        int outer = 0;
        for (; outer < outerMax; outer++) {
            // ★ 2026-11-07（R3）：动量来源二选一（R3 = 稳态 3D 动量平衡；关 = v1 热成风）。
            if (R3Momentum.ON) R3Momentum.applyToState(g, st);
            else BaroclinicFlow.apply(g, st);
            // ★ 2026-11-07 A 方案：三维埃克曼速度 + 投影（只在 on 时；off 逐位不变）。
            if (EKMAN_UPWELL_ON) applyEkmanFlow(g, st, f, theta);
            // ★ 2026-11-02：不再 init(g, st, surfaceT)！那会把整列 T 重置成表层值，
            // 每季把深层结构抹掉一次——3D 解不能用"均匀表层温度"当初始猜测。
            // BiCGSTAB 直接以当前 T 为初值。
            // ★ 2026-11-02：穿透短波作为【稳态源项】进入 T 方程（不再是每季 += dt 的阶梯）。
            double[] src = shortwaveSource(g, st, f, theta);
            double br;
            if (PROGNOSTIC_T_ON) {
                br = PrognosticT.run(g, st, surfaceT, src, SEASON_SECONDS, SUBSTEPS);
            } else if (FREEZE_ACTIVE_ON) {
                br = BiCGSTABT.solveWithFreezing(g, st, surfaceT, src, Math.min(maxIter, 2000), 1.0e-6);
            } else {
                br = BiCGSTABT.solve(g, st, surfaceT, src, Math.min(maxIter, 2000), 1.0e-6);
            }
            // ★ 2026-11-09（阶段 2）：删掉"全柱重置"兜底。br>1 K 在冻结钳制下是正常量级，
            //   不能当数值失败；非有限才记 WARN，绝不重置状态。
            if (!Double.isFinite(br)) {
                System.out.println("WARN PROGNOSTIC_T_NONFINITE br=" + br);
            }
            double dt = SEASON_SECONDS;
            res = 0.0;   // ★ 2026-11-02：原来这一行在表面更新【之后】，且循环里从未再赋值。
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0), js = j * nx + i;
                    double h = Math.max(1.0, st.mixedLayerDepth[js]);
                    double ts = st.t[q0];
                    double q = f.netHeatFluxWm2((int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, ts, st.iceFraction[js]);
                    double qsw0 = f.shortwaveWm2((int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, ts);
                    q = q - qsw0 + qswLayer(qsw0, g.dz, 0);
                    // ★ 2026-11-02：表层更新加入【稳态通量残差】的内部扩散项。
                    // 旧式只加 Q，没有任何回拉 ⟹ 不是收缩映射（把外层开 3 次会爆）。
                    // 这里用混合层底以下第一个格心 (T_b, delta) 计算 KV 通量；
                    // 步长按扩散稳定限 0.5*h*delta/KV 收紧，避免 h 很小时过冲。
                    double zc = 0.0;
                    int kb = g.nz - 1;
                    for (int k = 0; k < g.nz; k++) {
                        double zk = zc + 0.5 * g.dz[k];
                        if (zk > h) { kb = k; break; }
                        zc += g.dz[k];
                    }
                    double zb = zc + 0.5 * g.dz[kb];
                    double delta = Math.max(0.5 * g.dz[kb], zb - h);
                    double tb = st.t[st.idx(i, j, kb)];
                    // 混合层底的扩散通量用【深层】扩散率（层下就是 KV_DEEP 的 regime）。
                    double qEff = q - RHO0 * CP_SEA * KV_DEEP * (ts - tb) / delta;
                    double dtS = Math.min(dt, 0.5 * h * delta / KV_DEEP);
                    st.hTOld[js] = h * ts;
                    // ★ 2026-11-09（阶段 3 / A2）：表面通量加到【顶层格】，再由混合层平均。
                    //   旧式分母用 h（MLD），随后 k=0 的异常又被 dz0/h 摊薄一次 ⟹ 实际加热
                    //   只有 q·dt/(rho cp h) 的 dz0/h 倍（h=390 m 时 7.8× 欠加热）。
                    //   分母改用 dz0 后，ML 平均恰好给出 q·dt/(rho cp h)。
                    double t0f = ts + dtS * qEff / (RHO0 * CP_SEA * Math.max(1.0, g.dz[0]));
                    if (!Double.isFinite(t0f)) t0f = ts;
                    // ★★ 2026-11-12（#2 残余的根因）：这一支**在 ML 冰步之后**又按净通量加热表层，
                    //   而它完全不看冰 —— 于是季末快照里出现「冰 1.6 m、表层 275.2 K」（NICEDYN 实测）。
                    //   修法同 ICE_BASAL_MELT：多余热量先融冰，化光才留给水（零新参数、能量守恒）。
                    if (ICE_BASAL_MELT && t0f > 0.0) {
                        double tfHere = FreezingPoint.kelvin(st.s[q0]);
                        double hi = st.iceThickness[js];
                        double dzEff = Math.max(1.0, g.dz[0]);
                        if (hi > 1.0e-6 && t0f > tfHere) {
                            double capM = RHO0 * CP_SEA * dzEff * (t0f - tfHere)
                                    / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                            double melted = Math.min(hi, capM);
                            st.iceThickness[js] = hi - melted;
                            t0f -= melted * (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION)
                                    / (RHO0 * CP_SEA * dzEff);
                            LAST_BASAL_MELT_M += melted;
                        }
                    }
                    // 注意：这里**不能**简单钳到冰点。2026-11-02 实测：把 200.0 换成 T_FREEZE 后，
                    // 混合层把被钳住的表层温度再平均回整柱，热无法排出 ⟹ T 跑到 334..468 K。
                    // 冰点约束必须与海冰的潜热/盐通量一起做，不能只钳温度。
                    t0f = Math.max(200.0, Math.min(400.0, t0f));
                    surfaceT[js] = t0f;
                    st.t[q0] = t0f;
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0), q1 = st.idx(i, j, 1);
                    double ts = st.t[q0];
                    int js = j * nx + i;
                    double ice = st.iceFraction[js];
                    double q = f.netHeatFluxWm2((int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, ts, ice);
                    double h = Math.max(1.0, st.mixedLayerDepth[js]);
                    int im = (i - 1 + nx) % nx, ip = (i + 1) % nx;
                    int jm = Math.max(1, j - 1), jp = Math.min(ny - 2, j + 1);
                    double hW = st.mixedLayerDepth[j * nx + im], hE = st.mixedLayerDepth[j * nx + ip];
                    double hS = st.mixedLayerDepth[jm * nx + i], hN = st.mixedLayerDepth[jp * nx + i];
                    double du = (st.u[st.idx(ip, j, 0)] - st.u[st.idx(im, j, 0)]) / (2 * g.dx);
                    double dv = (st.v[st.idx(i, jp, 0)] - st.v[st.idx(i, jm, 0)]) / (2 * g.dy);
                    double dhdx = (hE - hW) / (2 * g.dx), dhdy = (hN - hS) / (2 * g.dy);
                    double divHu = h * (du + dv) + st.u[q0] * dhdx + st.v[q0] * dhdy;
                    double tx = f.windStressX((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
                    double tz = f.windStressZ((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta);
                    double ustar = Math.sqrt(Math.hypot(tx, tz) / 1025.0);
                    // 混合层底以下第一个格心：KT 的 ΔT 与表层通量都用它（不是 k=1——
                    // h 深时 k=1 还在混合层内部，ΔT=0 会给出虚假的巨大卷吸）。
                    double zc = 0.0;
                    int kb = g.nz - 1;
                    for (int k = 0; k < g.nz; k++) {
                        double zk = zc + 0.5 * g.dz[k];
                        if (zk > h) { kb = k; break; }
                        zc += g.dz[k];
                    }
                    // ★ 2026-11-09（阶段 3 / P0-2+A3）：KT 的分母改用【浮力跳变】，
                    //   而不是温差 + 0.05 K 下限。冰区 ts≈tb=tf 时 dTml 被 0.05 兜住会
                    //   把 K 放大 ~20×、h 一步跳到 hLimit（实测 MLD p99=3,650 m）。
                    //   浮力跳变在无层结/不稳定时 ≤0 ⟹ K=0，不再虚增卷吸。
                    double tbMl = st.t[st.idx(i, j, kb)];
                    double sbMl = st.s[st.idx(i, j, kb)];
                    double ssMl = st.s[q0];
                    double db = WorldContract.GRAVITY
                            * (Thermohaline.ALPHA * (ts - tbMl)
                             - Thermohaline.BETA * (ssMl - sbMl));
                    double rho0 = 1027.8 * (1.0 - Thermohaline.ALPHA * (st.t[q0] - 285.0)
                            + Thermohaline.BETA * (st.s[q0] - 35.0));
                    double hLimit = 1.0, hAcc = 0.0;
                    for (int k = 1; k < g.nz; k++) {
                        int qk = st.idx(i, j, k);
                        double rk = 1027.8 * (1.0 - Thermohaline.ALPHA * (st.t[qk] - 285.0)
                                + Thermohaline.BETA * (st.s[qk] - 35.0));
                        hAcc += g.dz[k];
                        hLimit = Math.max(1.0, hAcc);
                        if (rk - rho0 > MLD_JUMP_KGM3) break;
                    }
                    double qsw0h = f.shortwaveWm2((int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)), theta, ts);
                    double qeff = q - qsw0h + qswLayer(qsw0h, g.dz, 0);
                    double B = WorldContract.GRAVITY * Thermohaline.ALPHA * qeff / (RHO0 * CP_SEA);
                    // ★ 2026-11-02（KT）：h dh/dt = 2.5u*^3 - 0.5 B h  ⟹  dh/dt = K/h，
                    //   K = (2.5u*^3 - 0.5Bh) / (g α ΔT)。
                    //   K>0 卷吸（受层结因子 1-h/hLimit 限制）；K<0 去卷吸（不受该因子限制）。
                    //   不再有"B<0 ⟹ h=hLimit"的瞬时整柱对流。
                    double K;
                    if (db <= 1.0e-9) {
                        K = 0.0;   // 无层结/不稳定：不做卷吸（对流由独立判据处理）
                    } else {
                        K = (2.5 * ustar * ustar * ustar - 0.5 * B * h) / db;
                    }
                    if (K > 0.0) K *= Math.max(0.0, 1.0 - h / hLimit);
                    double h2 = h * h + 2.0 * K * dt;
                    double hEnt = Math.sqrt(Math.max(g.dz[0] * g.dz[0], h2));
                    double hNew = Math.max(g.dz[0], Math.min(hEnt, hLimit));
                    // 阶段 3：混合层深度不能超过该列洋底深度（浅海/陆架）
                    double colDepth = g.columnDepthM(i, j);
                    if (hNew > colDepth) hNew = colDepth;
                    // ★ 2026-11-02：res 仍不累计——实测把外层从 1 次改成 3 次会让 v 爆到 2e3 m/s
                    // （表面时间步不是收缩映射）。见交接：这里需要重写表面通量边界，而不是简单开迭代。
                    double dep = 0.0, tsum = 0.0, ssum = 0.0, wsum = 0.0;
                    for (int k = 0; k < g.nz && dep < hNew; k++) {
                        double w = Math.min(g.dz[k], hNew - dep);
                        int qk = st.idx(i, j, k);
                        tsum += st.t[qk] * w; ssum += st.s[qk] * w; wsum += w; dep += g.dz[k];
                    }
                    double tbar = wsum > 0 ? tsum / wsum : st.t[q0];
                    double sbar = wsum > 0 ? ssum / wsum : st.s[q0];
                    // ★ 2026-11-07 A 方案：三维埃克曼速度 + ML 底卷吸 + 表层水平平流（默认关）。
                    //   体积/热量/盐由 3D 平流闭合；不是源项；无 NH/SH、无调参。
                    if (EKMAN_UPWELL_ON) {
                        int wl = g.wetLayers[g.idx(i, j)];
                        int kb2 = 0;
                        double zc2 = 0.0;
                        while (kb2 < wl - 1 && zc2 + 0.5 * g.dz[kb2] <= hNew) {
                            zc2 += g.dz[kb2];
                            kb2++;
                        }
                        if (kb2 > 0) {
                            double wBase = EK_W != null ? EK_W[js] : 0.0;
                            if (wBase > LAST_ML_WBASE_MAX) LAST_ML_WBASE_MAX = wBase;
                            if (wBase > 0.0) {
                                double tbUp = st.t[st.idx(i, j, kb2)];
                                double sbUp = st.s[st.idx(i, j, kb2)];
                                double frac = Math.min(1.0, wBase * dt / Math.max(1.0, hNew));
                                tbar += frac * (tbUp - tbar);
                                sbar += frac * (sbUp - sbar);
                            }
                        }
                        double tW = g.isWet(im, j) ? st.t[st.idx(im, j, 0)] : tbar;
                        double tE = g.isWet(ip, j) ? st.t[st.idx(ip, j, 0)] : tbar;
                        double tS = g.isWet(i, jm) ? st.t[st.idx(i, jm, 0)] : tbar;
                        double tN = g.isWet(i, jp) ? st.t[st.idx(i, jp, 0)] : tbar;
                        double sW = g.isWet(im, j) ? st.s[st.idx(im, j, 0)] : sbar;
                        double sE = g.isWet(ip, j) ? st.s[st.idx(ip, j, 0)] : sbar;
                        double sS = g.isWet(i, jm) ? st.s[st.idx(i, jm, 0)] : sbar;
                        double sN = g.isWet(i, jp) ? st.s[st.idx(i, jp, 0)] : sbar;
                        // ML 平均速度用【投影后的面速度】（与 3D divFlux 同一套 uF/vF；预算才闭合）。
                        double uML = 0.0, vML = 0.0, dzSum = 0.0;
                        for (int k = 0; k < kb2; k++) {
                            int qml = st.idx(i, j, k);
                            int qmlW = st.idx((i - 1 + nx) % nx, j, k);
                            int qmlS = st.idx(i, Math.max(1, j - 1), k);
                            double uC = 0.5 * (st.uF[qml] + st.uF[qmlW]);
                            double vC = 0.5 * (st.vF[qml] + st.vF[qmlS]);
                            double dz = g.dz[k];
                            uML += uC * dz;
                            vML += vC * dz;
                            dzSum += dz;
                        }
                        uML /= Math.max(1.0, dzSum);
                        vML /= Math.max(1.0, dzSum);
                        double advT = uML * (tE - tW) / (2.0 * g.dx) + vML * (tN - tS) / (2.0 * g.dy);
                        double advS = uML * (sE - sW) / (2.0 * g.dx) + vML * (sN - sS) / (2.0 * g.dy);
                        if (Math.abs(advT) > LAST_ML_ADV_MAX) LAST_ML_ADV_MAX = Math.abs(advT);
                        tbar -= dt * advT;
                        sbar -= dt * advS;
                    }
                    // ★ 2026-11-02（冰点/海冰耦合）：0 层热力冰（Semtner 1976 的热力部分）。
                    // 混合层热量低于冰点 ⟹ 差额【转为结冰潜热】并从海洋移除，tbar 钉在冰点；
                    // 高于冰点且有冰 ⟹ 先融冰，剩下的才加热海水。
                    // ⚠ 上一版只把温度钳到 T_FREEZE、没有把热量转成冰 ⟹ 每步"凭空加热"⟹ 热失控。
                    // 冰/盐调整必须放在【写回混合层之前】，否则只有 k=0 被改、层内其余格点仍是旧值。
                    double tf = FreezingPoint.kelvin(sbar);
                    double tbarBeforeIce = tbar;
                    double iceStart = Math.max(0.0, st.iceThickness[js]);
                    // ★★ 2026-11-12（交接 §11.3 项 2）：冰-海热交换（底部融化）。见 ICE_BASAL_MELT。
                    if (ICE_BASAL_MELT && st.iceThickness[js] > 1.0e-6 && tbar > tf) {
                        double capM = RHO0 * CP_SEA * hNew * (tbar - tf)
                                / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                        double h0 = st.iceThickness[js];
                        double melted = Math.min(h0, capM);
                        st.iceThickness[js] = h0 - melted;
                        tbar -= melted * ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION
                                / (RHO0 * CP_SEA * hNew);
                        LAST_BASAL_MELT_M += melted;
                    }
                    // ★ 2026-11-09（阶段 3）：冰热力学按【日】子步。
                    //   旧式一个 91 天季一步：核化用无冰开放水面通量，−500 W/m² 一步造 ~13 m 冰；
                    //   子步后第一步只核化 ~0.14 m，之后由冰热阻的 Stefan 生长主导。
                    int iceSteps = Math.min(30, Math.max(1, (int) Math.round(dt / 86400.0)));
                    double dtSub = dt / iceSteps;
                    for (int sub = 0; sub < iceSteps; sub++) {
                        double iceNow = Math.max(0.0, st.iceThickness[js]);
                        if (iceNow > 1.0e-6) {
                            // Tf 处净表面通量 >0 ⟹ 融冰；<0 ⟹ 结冰（受冰热阻限制）
                            double qAtTf = f.netHeatFluxWm2((int) Math.round(g.xOf(i)),
                                    (int) Math.round(g.zOf(j)), theta, tf, 1.0, iceNow);
                            double dIce;
                            if (qAtTf > 0.0) {
                                dIce = -qAtTf * dtSub
                                        / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                            } else {
                                double qs = iceSurfaceFlux(f, theta, (int) Math.round(g.xOf(i)),
                                        (int) Math.round(g.zOf(j)), tf, iceNow);
                                dIce = qs * dtSub
                                        / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                            }
                            if (!Double.isFinite(dIce)) dIce = 0.0;
                            st.iceThickness[js] = Math.max(0.0, iceNow + dIce);
                            tbar = tf;
                        } else if (tbar < tf) {
                            // 无冰且过冷：开放水面通量形成初始冰；子步保证只核化薄冰。
                            double qs = f.netHeatFluxWm2((int) Math.round(g.xOf(i)),
                                    (int) Math.round(g.zOf(j)), theta, tf, 0.0);
                            double dIce = Math.max(0.0, -qs) * dtSub
                                    / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                            if (!Double.isFinite(dIce)) dIce = 0.0;
                            st.iceThickness[js] = dIce;
                            tbar = tf;
                        }
                    }
                    if (ICE_TRACE && (iceStart > 0.0 || st.iceThickness[js] > 0.0)
                            && Math.abs(Math.toDegrees(g.latAt(j)) - ICE_TRACE_LAT) < 1.0) {
                        System.out.println(String.format(java.util.Locale.ROOT,
                            "ICETRACE i=%d j=%d lat=%.2f theta=%.3f tbar0=%.3f tf=%.3f tbar1=%.3f ice0=%.4f ice1=%.4f hML=%.1f",
                            i, j, Math.toDegrees(g.latAt(j)), theta, tbarBeforeIce, tf, tbar,
                            iceStart, st.iceThickness[js], hNew));
                    }
                    // 整季（dt）的盐通量由冰厚变化总量给出
                    st.iceSaltFlux[js] = -ClimberOcean.RHO_ICE
                            * (st.iceThickness[js] - iceStart) / dt;
                    dep = 0.0;
                    // ★ 2026-11-11：把"混合层重写"这一项的净热量也记账（默认关）。
                    final int dBand = DeepBudget.ON ? DeepBudget.band(g, j) : 0;
                    final double dCos = DeepBudget.ON ? Math.max(0.05, g.cosLat(j)) : 1.0;
                    for (int k = 0; k < g.nz && dep < hNew; k++) {
                        int qk = st.idx(i, j, k);
                        if (DeepBudget.ON)
                            DeepBudget.add(dBand, DeepBudget.T_ML, k,
                                    (tbar - st.t[qk]) * g.dz[k] * dCos);
                        st.t[qk] = tbar; st.s[qk] = sbar; dep += g.dz[k];
                    }
                    st.t[q0] = tbar; surfaceT[js] = tbar;
                    st.mixedLayerDepth[js] = hNew;
                    st.hTNew[js] = hNew * tbar;
                    // stage 2：冰覆盖状态由本步表层 T 更新（单一来源）
                    st.iceFraction[js] = Math.min(1.0,
                            st.iceThickness[js] / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
                    // ★ 2026-11-02 实测否决：全水柱 T 钳到 Tf + 按差额结冰 + 冰盐通量，
                    // 会形成 冰盐→S→密度→T→更多冰 的正反馈：T 冲到 467.9 K、内部冰 3.8e5 m、
                    // 盐 corr 2.26、clamp 3.5e4。已回退；正确做法需要与表面边界/brine 一起设计。
                }
            // ★ 2026-11-11：每季混合层/冰步之后做一次对流调整（默认关 ⟹ 空操作）。
            convectiveAdjust(g, st);
            // ★ 2026-11-12：对流掺上来的热量先融冰（见 meltIceFromSurfaceHeat 的说明）。
            meltIceFromSurfaceHeat(g, st, dt);
            if (EKMAN_UPWELL_ON) {
                // ★ 2026-11-07 A：ML 水平平流按 SUBSTEPS×1 天推进（30 天一步显式 CFL 会爆）。
                //   用 k=0 的投影面速度，避免把 h 以下的回流算进 ML 平均；无调参。
                double[] sSurf = new double[nx * ny];
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++)
                        if (g.isWet(i, j)) sSurf[j * nx + i] = st.s[st.idx(i, j, 0)];
                double[] tA = surfaceT.clone(), sA = sSurf.clone();
                double[] tB = new double[nx * ny], sB = new double[nx * ny];
                double dts = dt / Math.max(1, SUBSTEPS);
                for (int sub = 0; sub < SUBSTEPS; sub++) {
                    for (int j = 1; j < ny - 1; j++)
                        for (int i = 0; i < nx; i++) {
                            if (!g.isWet(i, j)) continue;
                            int js = j * nx + i;
                            int q0 = st.idx(i, j, 0);
                            int im = (i - 1 + nx) % nx, ip = (i + 1) % nx;
                            int jm = Math.max(1, j - 1), jp = Math.min(ny - 2, j + 1);
                            double hC = Math.max(g.dz[0], Math.min(st.mixedLayerDepth[js], g.columnDepthM(i, j)));
                            double hE = g.isWet(ip, j) ? Math.max(g.dz[0], Math.min(st.mixedLayerDepth[j * nx + ip], g.columnDepthM(ip, j))) : hC;
                            double hW = g.isWet(im, j) ? Math.max(g.dz[0], Math.min(st.mixedLayerDepth[j * nx + im], g.columnDepthM(im, j))) : hC;
                            double hN = g.isWet(i, jp) ? Math.max(g.dz[0], Math.min(st.mixedLayerDepth[jp * nx + i], g.columnDepthM(i, jp))) : hC;
                            double hS = g.isWet(i, jm) ? Math.max(g.dz[0], Math.min(st.mixedLayerDepth[jm * nx + i], g.columnDepthM(i, jm))) : hC;
                            double uE = 0.5 * (EK_MX[js] / hC + EK_MX[j * nx + ip] / hE);
                            double uW = 0.5 * (EK_MX[j * nx + im] / hW + EK_MX[js] / hC);
                            double vN = 0.5 * (EK_MY[js] / hC + EK_MY[jp * nx + i] / hN);
                            double vS = 0.5 * (EK_MY[jm * nx + i] / hS + EK_MY[js] / hC);
                            double tC = tA[js], sC = sA[js];
                            double tE = g.isWet(ip, j) ? tA[j * nx + ip] : tC;
                            double tW = g.isWet(im, j) ? tA[j * nx + im] : tC;
                            double tN = g.isWet(i, jp) ? tA[jp * nx + i] : tC;
                            double tS = g.isWet(i, jm) ? tA[jm * nx + i] : tC;
                            double sE = g.isWet(ip, j) ? sA[j * nx + ip] : sC;
                            double sW = g.isWet(im, j) ? sA[j * nx + im] : sC;
                            double sN = g.isWet(i, jp) ? sA[jp * nx + i] : sC;
                            double sS = g.isWet(i, jm) ? sA[jm * nx + i] : sC;
                            double fTE = uE * (uE >= 0.0 ? tC : tE);
                            double fTW = uW * (uW >= 0.0 ? tW : tC);
                            double fTN = vN * (vN >= 0.0 ? tC : tN);
                            double fTS = vS * (vS >= 0.0 ? tS : tC);
                            double fSE = uE * (uE >= 0.0 ? sC : sE);
                            double fSW = uW * (uW >= 0.0 ? sW : sC);
                            double fSN = vN * (vN >= 0.0 ? sC : sN);
                            double fSS = vS * (vS >= 0.0 ? sS : sC);
                            double dT = (fTE - fTW) / g.dx + (fTN - fTS) / g.dy;
                            double dS = (fSE - fSW) / g.dx + (fSN - fSS) / g.dy;
                            tB[js] = tC - dts * dT;
                            sB[js] = sC - dts * dS;
                            if (Math.abs(dT) > LAST_ML_ADV_MAX) LAST_ML_ADV_MAX = Math.abs(dT);
                        }
                    double[] sw = tA; tA = tB; tB = sw;
                    sw = sA; sA = sB; sB = sw;
                    java.util.Arrays.fill(tB, 0.0);
                    java.util.Arrays.fill(sB, 0.0);
                }
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int js = j * nx + i;
                        int q0 = st.idx(i, j, 0);
                        int wl = g.wetLayers[g.idx(i, j)];
                        surfaceT[js] = tA[js];
                        st.t[q0] = tA[js];
                        st.s[q0] = sA[js];
                        double h = st.mixedLayerDepth[js];
                        for (int k = 1; k < wl; k++) {
                            if (g.zCenter[k] >= h) break;
                            st.t[st.idx(i, j, k)] = tA[js];
                            st.s[st.idx(i, j, k)] = sA[js];
                        }
                        st.iceFraction[js] = Math.min(1.0,
                            st.iceThickness[js] / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
                    }
            }
            if (res < 1.0e-3) break;
        }
        return new Result(outer, res);
    }

    /**
     * ★★★ 2026-11-11：**对流调整**（静力不稳定 → 混合到中性面）。**默认关**，无参数。
     *
     * <p>为什么必须有它（交接文档 §5-1 的根因）：本类与 {@link PrognosticT} 里，
     * 静力不稳定（上重下轻）时的处理是
     * <ul>
     *   <li>Kraus–Turner 的卷吸率 {@code db <= 1e-9 ⟹ K = 0}（"对流由独立判据处理"）；</li>
     *   <li>而<b>全库grep没有任何"独立判据"</b>：{@code MixedLayerSolver} 是死代码（无调用者）。</li>
     * </ul>
     * 于是"冷而淡的表层压在暖而咸的次表层上"这种真实海洋**必须立刻混合**的状态，
     * 在本模型里被永久冻结住：表层被冰/大气钉在 Tf，深层保留较暖的水，
     * 两者之间的密度差（实测最坏 0.649 kg/m³）永远消不掉。
     *
     * <p>算法：自上而下扫一遍，若上层密度 &gt; 下层则按厚度加权合并（T、S 均守恒），
     * 退一步复查；直到整列单调稳定。**不引入任何常数**（只用 EOS 的 alpha/beta），
     * 因此符合铁律②。标准做法见 Bryan (1969, J. Comput. Phys. 4:347) 的对流调整；
     * ⚠ 具体卷页 **NOT FOUND**（本条只声明方法出处，未查到页码）。
     *
     * <p>★ <b>2026-11-11 用户裁决：翻为 true（默认开）。</b>
     * 理由：静力不稳定在真实海洋里是**立即**混合的；没有它，冰下冷表层压在暖次表层上的
     * 密度差（实测最坏 0.642 kg/m³、4.69% 的深层格点）永远不会消失。
     * 证据：A/B（seed 0、1 年、其余默认）反转 4.69% → <b>0.00%</b>，深层热量收支几乎不动。
     * 代价：改解 ⟹ **整套验收基线必须重钉**。
     * <p>关掉（=false）仍与改动前逐位相同。
     */
    public static boolean CONVECT_ADJUST_ON = Boolean.parseBoolean(
            System.getProperty("eoh.convect", "true"));   // A/B hook: -Deoh.convect=false
    /** 诊断：上一次调用合并过的格点数 / 温度调整量之和 [K]。 */
    public static int LAST_CONV_CELLS = 0;
    public static double LAST_CONV_DT = 0.0;
    /** Cumulative across every call since JVM start (tells "per-season" from "only the finale"). */
    public static long TOTAL_CONV_CELLS = 0L;
    public static int CONV_CALLS = 0;

    /** 线性 EOS 的局地位密度（与 BaroclinicFlow / 混合层判据同源、同常数）。 */
    static double rhoOf(OceanState st, int q) {
        return 1027.8 * (1.0 - Thermohaline.ALPHA * (st.t[q] - 285.0)
                + Thermohaline.BETA * (st.s[q] - 35.0));
    }

    /**
     * ★★ 2026-11-12（#2 残余的第三个缺口）：**对流调整之后**再用混合层多余热量融冰。
     *
     * <p>为什么需要：季内顺序是「ML 冰步（T 钉到 Tf）→ convectiveAdjust」。后者把下面较暖的水
     * 掺上来 ⟹ 季末快照出现「冰 1.6 m、表层 275.2 K」（NICEDYN 的 ICESNAP 实测）。
     * 物理上掺上来的热量应当先融冰，所以这里用一个能量守恒的后处理把它补上：
     * {@code Q = rho0*cp*h*(T0-Tf)}，融冰 {@code Q/(rho_ice*L_f)}，冷却量按混合层厚度摊匀
     * （保持层结形状，不再造新的不稳定）。零新参数。
     */
    public static void meltIceFromSurfaceHeat(OceanGrid g, OceanState st, double dtSeconds) {
        if (!ICE_BASAL_MELT) return;
        int nx = g.nx, ny = g.ny;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double hi = st.iceThickness[js];
                if (hi <= 1.0e-6) continue;
                int q0 = st.idx(i, j, 0);
                double tf = FreezingPoint.kelvin(st.s[q0]);
                if (!(st.t[q0] > tf)) continue;
                double h = Math.max(g.dz[0], Math.min(st.mixedLayerDepth[js], g.columnDepthM(i, j)));
                double capM = RHO0 * CP_SEA * h * (st.t[q0] - tf)
                        / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
                double melted = Math.min(hi, capM);
                st.iceThickness[js] = hi - melted;
                // ★ 2026-11-14（C1）：融冰淡水必须回到海洋（旧版只改冰厚、不记淡水 = 质量汇）。
                if (melted > 0.0 && dtSeconds > 0.0)
                    st.iceSaltFlux[js] += ClimberOcean.RHO_ICE * melted / dtSeconds;
                double dT = melted * (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION)
                        / (RHO0 * CP_SEA * h);
                double acc = 0.0;
                for (int k = 0; k < g.nz && acc < h; k++) {
                    st.t[st.idx(i, j, k)] -= dT;
                    acc += g.dz[k];
                }
                LAST_BASAL_MELT_M += melted;
            }
        }
    }

    public static void convectiveAdjust(OceanGrid g, OceanState st) {
        if (!CONVECT_ADJUST_ON) return;
        int nx = g.nx, ny = g.ny, nz = g.nz;
        // ★ 单调栈（块合并）：每次合并让"块数"减 1 ⟹ 每列最多 nz-1 次合并，必然终止。
        //   初版写成"成对混合 + 回退一步"，在等值/近似等值时会在两个 k 之间打转
        //   （实测一次收尾调用合并了 1.02e9 格次，把 1 年跑成 18 min）——这是自造死循环，已废弃。
        double[] bT = new double[nz], bS = new double[nz], bD = new double[nz], bR = new double[nz];
        int[] bN = new int[nz];
        int cells = 0;
        double sumdT = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            int bandB = DeepBudget.ON ? DeepBudget.band(g, j) : 0;
            double cosB = DeepBudget.ON ? Math.max(0.05, g.cosLat(j)) : 1.0;
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 1) continue;
                int top = 0;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    bT[top] = st.t[q]; bS[top] = st.s[q]; bD[top] = g.dz[k];
                    bR[top] = rhoOf(st, q); bN[top] = 1; top++;
                    while (top >= 2 && bR[top - 2] > bR[top - 1] + 1.0e-12) {
                        int a = top - 2, b = top - 1;
                        double d = bD[a] + bD[b];
                        double T = (bT[a] * bD[a] + bT[b] * bD[b]) / d;
                        double S = (bS[a] * bD[a] + bS[b] * bD[b]) / d;
                        bT[a] = T; bS[a] = S; bD[a] = d; bN[a] = bN[a] + bN[b];
                        bR[a] = 1027.8 * (1.0 - Thermohaline.ALPHA * (T - 285.0)
                                + Thermohaline.BETA * (S - 35.0));
                        top--;
                        cells++;
                    }
                }
                if (top == wl) continue;   // nothing merged: leave the column untouched
                // write back block values (each block is a contiguous run of layers)
                int k = 0;
                for (int t2 = 0; t2 < top; t2++) {
                    for (int n2 = 0; n2 < bN[t2]; n2++, k++) {
                        int q = st.idx(i, j, k);
                        sumdT += Math.abs(bT[t2] - st.t[q]);
                        if (DeepBudget.ON) {
                            DeepBudget.add(bandB, DeepBudget.T_CONV, k,
                                    (bT[t2] - st.t[q]) * g.dz[k] * cosB);
                        }
                        st.t[q] = bT[t2];
                        st.s[q] = bS[t2];
                    }
                }
            }
        }
        LAST_CONV_CELLS = cells;
        LAST_CONV_DT = sumdT;
        TOTAL_CONV_CELLS += cells;
        CONV_CALLS++;
    }

    /**
     * 从当前 T/S 诊断混合层深度（密度跳变 0.03 kg/m³）。
     * <p>★ 2026-11-02：h 不是状态量。逐季 h 的算术平均与最终 T/S 不自洽
     * （实测 91% 格点差 &gt;100 m）。输出前用最终年平均 T/S 重诊一次，
     * 保证"报出来的 h"与"报出来的 T/S"是同一个状态。
     */
    static void diagnoseMixedLayer(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q0 = st.idx(i, j, 0);
                double r0 = 1027.8 * (1.0 - Thermohaline.ALPHA * (st.t[q0] - 285.0)
                        + Thermohaline.BETA * (st.s[q0] - 35.0));
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl <= 0) continue;
                double hAcc = 0.0, h = g.dz[0];
                for (int k = 1; k < wl; k++) {
                    int qk = st.idx(i, j, k);
                    double rk = 1027.8 * (1.0 - Thermohaline.ALPHA * (st.t[qk] - 285.0)
                            + Thermohaline.BETA * (st.s[qk] - 35.0));
                    hAcc += g.dz[k];
                    h = hAcc;
                    if (rk - r0 > MLD_JUMP_KGM3) break;
                }
                st.mixedLayerDepth[j * nx + i] = Math.min(Math.max(g.dz[0], h), g.columnDepthM(i, j));
            }
    }

    public static Result solve(OceanGrid g, OceanState st, double[] surfaceT) {
        return solve(g, st, surfaceT, 50000);
    }

    public static Result solve(OceanGrid g, OceanState st, double[] surfaceT, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        // ★ 2026-11-09（阶段 2）：只钉 k=0 的表层 Dirichlet，绝不重置整柱。
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                st.t[st.idx(i, j, 0)] = surfaceT[j * nx + i];
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    if (st.s[q] == 0.0) st.s[q] = 35.0;
                }
            }
        }
        int iter = 0;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            for (int k = 1; k < nz; k++) {
                double dz = g.dz[k];
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int q = st.idx(i, j, k);
                        double cv = kvAt(g, st, i, j, k) / (dz * dz);
                        double t = st.t[q];
                        double tw = g.isWet(i - 1, j, k) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j, k) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1, k) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tn = g.isWet(i, j + 1, k) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        // A3: below the seabed (k+1 >= wetLayers) is dry -> zero gradient.
                        double tdn = (k + 1 < g.wetLayers[g.idx(i, j)]) ? st.t[st.idx(i, j, k + 1)] : t;

                        double u = st.u[q], v = st.v[q];
                        double advX = u >= 0 ? u * (t - tw) / dx : u * (te - t) / dx;
                        double advY = v >= 0 ? v * (t - ts) / dy : v * (tn - t) / dy;
                        double adv = advX + advY + advZ(st, i, j, k, t, dz);

                        double diff = khAt(g, k) * (tw + te - 2.0 * t) / (dx * dx)
                                    + KH * (ts + tn - 2.0 * t) / (dy * dy)
                                    + cv * (tup + tdn - 2.0 * t);
                        double diag = 4.0 * khAt(g, k) / (dx * dx) + 4.0 * khAt(g, k) / (dy * dy) + 2.0 * cv
                                    + Math.abs(u) / dx + Math.abs(v) / dy
                                    + advZDiag(st, i, j, k, dz);
                        double target = t + (diff - adv) / diag;
                        double nv = t + SOR * (target - t);
                        st.t[q] = nv;
                        double d = Math.abs(nv - t);
                        if (d > res) res = d;
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        return new Result(iter, res);
    }

    /**
     * ★ 2026-11-04（A1）：带【矩阵级深层 Dirichlet】的稳态 T 求解。
     *
     * <p>与 {@link #solve(OceanGrid,OceanState,double[],int)} 的区别：
     * <ul>
     *   <li><b>不重置</b>列（保留调用方给的初值——通常是扩散-only 的收敛解）；</li>
     *   <li>k ≥ {@code kDeep} 的格点【不参与迭代】，只作已知邻点（真正的 Dirichlet）；</li>
     *   <li>{@code cosLat=true} 时纬向项用 {@code dx*cos(lat)}（球面度量）。</li>
     * </ul>
     * 算子与已验证的稳态 SOR 完全同源，只改了这三处。
     */
    public static Result solveSteady(OceanGrid g, OceanState st, double[] surfaceT,
                                     int kDeep, boolean cosLat, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        int kMax = Math.max(1, Math.min(kDeep, nz));
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (g.isWet(i, j)) st.t[st.idx(i, j, 0)] = surfaceT[j * nx + i];
        int iter = 0;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            for (int k = 1; k < kMax; k++) {
                double dz = g.dz[k];
                for (int j = 1; j < ny - 1; j++) {
                    double dxc = cosLat ? Math.max(1.0, g.dx * g.cosLat(j)) : g.dx;
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int wl = g.wetLayers[g.idx(i, j)];
                        int q = st.idx(i, j, k);
                        double cv = kvAt(g, st, i, j, k) / (dz * dz);
                        double t = st.t[q];
                        double tw = g.isWet(i - 1, j, k) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j, k) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1, k) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tn = g.isWet(i, j + 1, k) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        // ★ A1：k+1 ≥ wl 才是干格（海底以下），用零梯度；
                        //   k+1 = kMax 是【深层 Dirichlet 格点】——它是湿的、值是固定的，必须读进来，
                        //   否则深层锚点与上层断开（上一版就是这样把 S 抽到 0.84 的）。
                        double tdn = (k + 1 < wl) ? st.t[st.idx(i, j, k + 1)] : t;
                        double u = st.u[q], v = st.v[q];
                        double advX = u >= 0 ? u * (t - tw) / dxc : u * (te - t) / dxc;
                        double advY = v >= 0 ? v * (t - ts) / g.dy : v * (tn - t) / g.dy;
                        double adv = advX + advY + advZ(st, i, j, k, t, dz);
                        double diff = khAt(g, k) * (tw + te - 2.0 * t) / (dxc * dxc)
                                    + KH * (ts + tn - 2.0 * t) / (g.dy * g.dy)
                                    + cv * (tup + tdn - 2.0 * t);
                        double diag = 4.0 * khAt(g, k) / (dxc * dxc) + 4.0 * khAt(g, k) / (g.dy * g.dy) + 2.0 * cv
                                    + Math.abs(u) / dxc + Math.abs(v) / g.dy
                                    + advZDiag(st, i, j, k, dz);
                        double target = t + (diff - adv) / diag;
                        double nv = t + SOR * (target - t);
                        st.t[q] = nv;
                        double dd = Math.abs(nv - t);
                        if (dd > res) res = dd;
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        return new Result(iter, res);
    }

    /**
     * ⚠ 2026-11-12 更正（约定 4：历史段落必须自证是历史）：下面这句的「默认 false」已过期 —— **当前字段行 = true**。
     * ★ 2026-11-04（A3）：内部冰点 active-set 的【收尾投影】。（历史）默认 false ⟹ 逐位不变。
     *
     * <p>稳态 T 方程里没有相变项，所以冰下 k=1–2 可以稳定地停在 Tf 以下
     * （A1 收口实测：±86.3° 各 2 格，T=271.228 K、Tf=271.279 K，差 0.051 K）。
     * 真实的海洋不会留下过冷水：多出来的冷量由**结冰释放的潜热**补上。
     *
     * <pre>  dm = ρ0 cp (Tf − T) / L_f  [kg/m³]  ⟹  dhi = dm·dz/ρ_ice  [m]（按 1 m²）</pre>
     *
     * <p>常数与边界与 {@link PrognosticT#FREEZE_LEDGER_ON} 完全一致（L_f / ρ_ice 的出处写在那边）；
     * <b>不做盐析</b>（冰视为留住自己的盐），所以 S 不动、总盐不变。
     * <p>投影把格点钉在 Tf(S)，是 active-set 的"固定"一侧；再解一次方程即可让邻点适应。
     *
     * <p>⚠ <b>只能在【解 T】之后调用</b>。`solveSteady` 也被用来解 S（把 S 临时放进 t），
     * 把投影放进 solveSteady 会把"盐度"当温度钉到 Tf ⟹ 实测 S 冲到 271.3 psu。
     * 所以它是**显式调用**（调用方明确知道这是 T），不是求解器的收尾。
     *
     * <p>★ <b>实测否决（2026-11-04，NFREEZE2）</b>：把这份亏损真的记成**表面冰**
     * （{@code INTERIOR_FREEZE_ICE_ON=true}）跑 1 年，会经冰-反照率/冰热阻反馈失控：
     * 冰均值 <b>58.6 m</b>、最大 <b>12,670 m</b>、923/3356 格有冰，T 上限 307.3 K。
     * 与 2026-11-02 的"全柱钳制 + 结冰 + brine"是同一机制（那次数值更差：冰 3.8e5 m）。
     * ⟹ 记账本身留着（有量、可审计），但**默认不落到冰上**；只钉温度并报告那笔热量。
     */
    public static boolean INTERIOR_FREEZE_ON = true;

    /**
     * 诊断（默认关，零影响）：对指定世界坐标 (x,z) 打印**逐季**的混合层/冰收支。
     * <p>为什么需要：P484 的 SEASON 门报出「整年有冰，而四季平均都在冰点之上」的格（12% 湿格），
     * 而 ML 冰块里 {@code tbar} 一有冰就被钉到 Tf —— 两者按代码不该同时成立 ⟹ 必须逐季看，
     * 不能靠读代码推断（A6「用探针检验假设」）。
     */
    /**
     * ★★ 2026-11-12（交接 §11.3 项 2 的根因修复）：**冰-海热交换（底部融化）**。
     *
     * <p>旧代码在有冰时无条件 {@code tbar = tf}，把混合层高出的那部分热量**直接丢弃** ⟹
     * 「水体比冰点高 2.3 K」与「冰照样生长」可以同时成立。NICEDYN 实测（54N, seed 0）：
     * <pre>
     *   theta=3.142  tbar0=271.133  tf=271.267  ice 0.000 -> 1.631 m   （过冷核化，正常）
     *   theta=4.712  tbar0=273.566  tf=271.250  ice 1.631 -> 2.274 m   （水体暖 2.3 K，冰还在长）
     * </pre>
     *
     * <p>修法：先把混合层相对冰点的**多余热量**折成融冰量（{@code Q = rho0*cp*hNew*(Tbar-Tf)}，
     * 融冰 {@code Q/(rho_ice*L_f)}），冰化光时余热留在水里；**零新参数、能量守恒**。
     * 之后照旧走表面通量。
     *
     * <p>默认 <b>true</b>（2026-11-12 用户批准「解决冰过盛」后翻默认）；
     * <b>-Deoh.icebasal=false 逐位回到旧口径</b>（旧的错误行为：丢热 + 冰照长）。
     */
    public static boolean ICE_BASAL_MELT = Boolean.parseBoolean(
            System.getProperty("eoh.icebasal", "true"));
    /** 累计底部融化量（m，诊断）。 */
    public static double LAST_BASAL_MELT_M = 0.0;
    public static boolean ICE_TRACE = false;
    /** 只打印 |lat - ICE_TRACE_LAT| < 1 度**且有冰**的格（每条一行/季）。 */
    public static double ICE_TRACE_LAT = -999.0;
    /**
     * ★ A3 实测否决：投影时是否把这笔潜热记成表面冰。默认 false；
     * 打开会失控（见上），只作为证据保留。
     */
    public static boolean INTERIOR_FREEZE_ICE_ON = false;
    /** 投影统计：被钉住的格点数、生成的等效冰厚 [m]、释放的潜热 [J/m²·格点] 与总亏损 [K]。 */
    public static int LAST_IF_CELLS = 0;
    public static double LAST_IF_ICE = 0.0;
    public static double LAST_IF_J = 0.0;
    public static double LAST_IF_DEFICIT = 0.0;

    public static void applyInteriorFreezing(OceanGrid g, OceanState st) {
        LAST_IF_CELLS = 0; LAST_IF_ICE = 0.0; LAST_IF_J = 0.0; LAST_IF_DEFICIT = 0.0;
        int nx = g.nx, ny = g.ny, nz = g.nz;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                int wl = Math.min(nz, g.wetLayers[g.idx(i, j)]);
                for (int k = 1; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double tf = FreezingPoint.kelvin(st.s[q]);
                    double t = st.t[q];
                    if (!(t < tf)) continue;
                    double deficit = tf - t;
                    // ★ 2026-11-11：冰点投影是"凭空加热"，单独记账（默认关）。
                    if (DeepBudget.ON)
                        DeepBudget.add(DeepBudget.band(g, j), DeepBudget.T_FRZ_FINAL, k,
                                deficit * g.dz[k] * Math.max(0.05, g.cosLat(j)));
                    st.t[q] = tf;
                    double massIce = RHO0 * CP_SEA * deficit / PrognosticT.L_FUSION;
                    double dhi = massIce * g.dz[k] / PrognosticT.RHO_ICE;
                    if (INTERIOR_FREEZE_ICE_ON) st.iceThickness[js] += dhi;
                    LAST_IF_CELLS++;
                    LAST_IF_ICE += dhi;
                    LAST_IF_J += RHO0 * CP_SEA * deficit * g.dz[k];
                    LAST_IF_DEFICIT += deficit;
                }
            }
    }

    /**
     * 最小盐度输运：与 T 同一 u/v、KH、KV；表面只由 E-P 淡水通量约束。
     *
     * <p>这是 provisional：尚未加入海冰结冰/融冰盐通量，也未把 S 反馈到 EOS。
     * 表面盐通量 rho*KV*(S1-S0)/dz0 = S0*Fw，Fw 正 = 淡水进入海洋。
     */
    public static Result solveSalinity(OceanGrid g, OceanState st, SeaSurfaceForcing f,
                                       double theta, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        double dz0 = g.dz[0];
        double a0 = RHO0 * KV / dz0;
        // 盐度不由本方法重设为 35；初值/总盐量由调用方或世界盐库存给定。
        double salt0 = 0.0, vol = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    salt0 += st.s[st.idx(i, j, k)] * g.dz[k];
                    vol += g.dz[k];
                }
            }
        }
        // 固定体积：去掉 E-P 的面积平均；只让空间异常驱动盐度。
        // forcing 只算一次，避免在迭代里反复调用昂贵的 PrecipField/Radiation。
        double[] fwSurf = new double[nx * ny];
        double fwSum = 0.0;
        double iceSum = 0.0;
        int clamp = 0;
        int fwN = 0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                double v = f.freshwaterFluxKgM2S((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), theta, st.t[st.idx(i, j, 0)]);
                if (Double.isNaN(v)) v = 0.0;
                // ★ 2026-11-02：结冰/融冰的盐通量并入 3D 盐度求解器的表层淡水收支。
                iceSum += st.iceSaltFlux[g.idx(i, j)];
                v += st.iceSaltFlux[g.idx(i, j)];
                st.iceSaltFlux[g.idx(i, j)] = 0.0;
                fwSurf[g.idx(i, j)] = v;
                fwSum += v;
                fwN++;
            }
        }
        double fwMean = fwN > 0 ? fwSum / fwN : 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (g.isWet(i, j)) fwSurf[g.idx(i, j)] -= fwMean;
            }
        }
        int iter = 0;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0);
                    double fw = fwSurf[g.idx(i, j)];
                    // ★ 2026-11-02：这里保留【稳定版】表面盐度（dz0 扩散平衡）。
                    // 混合层盐收支 rho h dS0/dt = rho KV (Sb-S0)/delta - S0 Fw - rho h adv
                    // 的公式是对的，但它的平流项是 advective form：实测非守恒
                    // （每季 corr 到 0.064 psu、clamp 1.1e4 次、h 从 631 恶化到 1231 m、
                    //  亚冰点 134 -> 982）。必须先有【守恒的 flux-form 平流】再启用，
                    // 否则是把一个正确的方程用错误的离散在跑。
                    double s0;
                    if (ML_SALT_ON) {
                        // stage 1：混合层盐收支（h 参与，E−P/brine 不再被 a0 吞掉）
                        int js2 = j * nx + i;
                        double hm = Math.max(dz0, st.mixedLayerDepth[js2]);
                        double zc = 0.0;
                        int kb = nz - 1;
                        for (int k = 0; k < nz; k++) {
                            double zk = zc + 0.5 * g.dz[k];
                            if (zk > hm) { kb = k; break; }
                            zc += g.dz[k];
                        }
                        double sb = st.s[st.idx(i, j, kb)];
                        double delta = Math.max(0.5 * g.dz[kb], (zc + 0.5 * g.dz[kb]) - hm);
                        double baseC = KV_DEEP / delta;
                        double fwC = fw / RHO0;
                        double s = st.s[q0];
                        double sw = g.isWet(i - 1, j) ? st.s[st.idx(i - 1, j, 0)] : s;
                        double se = g.isWet(i + 1, j) ? st.s[st.idx(i + 1, j, 0)] : s;
                        double ss = g.isWet(i, j - 1) ? st.s[st.idx(i, j - 1, 0)] : s;
                        double sn = g.isWet(i, j + 1) ? st.s[st.idx(i, j + 1, 0)] : s;
                        double u0 = st.u[q0], v0 = st.v[q0];
                        double aS = (u0 >= 0 ? u0 : -u0) / dx + (v0 >= 0 ? v0 : -v0) / dy;
                        double b = (u0 >= 0 ? -u0 * sw / dx : u0 * se / dx)
                                 + (v0 >= 0 ? -v0 * ss / dy : v0 * sn / dy);
                        s0 = (baseC * sb - hm * b) / (fwC + baseC + hm * aS);
                    } else {
                        double den = a0 + fw;
                        s0 = den > 1.0e-12 ? a0 * st.s[st.idx(i, j, 1)] / den : 50.0;
                    }
                    if (s0 > 50.0) { s0 = 50.0; clamp++; }
                    if (s0 < 0.0) { s0 = 0.0; clamp++; }
                    double old = st.s[q0];
                    double nv = old + SOR * (s0 - old);
                    if (nv < 0.0) { nv = 0.0; clamp++; }
                    if (nv > 50.0) { nv = 50.0; clamp++; }
                    st.s[q0] = nv;
                    res = Math.max(res, Math.abs(nv - old));
                }
            }
            for (int k = 1; k < nz; k++) {
                double dz = g.dz[k];
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int q = st.idx(i, j, k);
                        double cv = kvAt(g, st, i, j, k) / (dz * dz);
                        double s = st.s[q];
                        double sw = g.isWet(i - 1, j, k) ? st.s[st.idx(i - 1, j, k)] : s;
                        double se = g.isWet(i + 1, j, k) ? st.s[st.idx(i + 1, j, k)] : s;
                        double ss = g.isWet(i, j - 1, k) ? st.s[st.idx(i, j - 1, k)] : s;
                        double sn = g.isWet(i, j + 1, k) ? st.s[st.idx(i, j + 1, k)] : s;
                        double sup = st.s[st.idx(i, j, k - 1)];
                        double sdn = (k + 1 < nz) ? st.s[st.idx(i, j, k + 1)] : s;
                        double u = st.u[q], v = st.v[q];
                        double advX = u >= 0 ? u * (s - sw) / dx : u * (se - s) / dx;
                        double advY = v >= 0 ? v * (s - ss) / dy : v * (sn - s) / dy;
                        double diff = khAt(g, k) * (sw + se - 2.0 * s) / (dx * dx)
                                    + KH * (ss + sn - 2.0 * s) / (dy * dy)
                                    + cv * (sup + sdn - 2.0 * s);
                        double diag = 4.0 * khAt(g, k) / (dx * dx) + 4.0 * khAt(g, k) / (dy * dy) + 2.0 * cv
                                    + Math.abs(u) / dx + Math.abs(v) / dy;
                        double target = s + (diff - advX - advY) / diag;
                        double nv = s + SOR * (target - s);
                        if (nv < 0.0) { nv = 0.0; clamp++; }
                        if (nv > 50.0) { nv = 50.0; clamp++; }
                        st.s[q] = nv;
                        double d = Math.abs(nv - s);
                        if (d > res) res = d;
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        double salt1 = 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) salt1 += st.s[st.idx(i, j, k)] * g.dz[k];
            }
        }
        double corrOut = 0.0;
        if (vol > 0.0) {
            corrOut = (salt0 - salt1) / vol;
            if (corrOut != 0.0) {
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        for (int k = 0; k < nz; k++) {
                            int q = st.idx(i, j, k);
                            st.s[q] = Math.max(0.0, Math.min(50.0, st.s[q] + corrOut));
                        }
                    }
                }
            }
        }
        LAST_SALT0 = salt0; LAST_SALT1 = salt1; LAST_CORR = corrOut;
        LAST_ICE = iceSum; LAST_CLAMP = clamp;
        System.out.println("SALT salt0=" + salt0 + " salt1=" + salt1
            + " corr=" + corrOut + " ice=" + iceSum + " clamp=" + clamp);
        return new Result(iter, res);
    }

    /**
     * 通量边界版：k=0 不再固定，而由
     *   Q_net(T0) = rho*Cp*KV*(T0-T1)/dz0
     * 解出；正 Q = 进入海洋。若 Q(Tf)<0 则结冰，T0=Tf。
     */
    public static Result solve(OceanGrid g, OceanState st, double[] surfaceT,
                               SeaSurfaceForcing f, double theta, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        double dz0 = g.dz[0];
        double rhoCpKv = RHO0 * CP_SEA * KV;
        // ★ 2026-11-09（阶段 2）：只钉 k=0 的表层 Dirichlet，绝不重置整柱。
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                st.t[st.idx(i, j, 0)] = surfaceT[j * nx + i];
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    if (st.s[q] == 0.0) st.s[q] = 35.0;
                }
            }
        }
        int iter = 0;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            // surface flux boundary
            for (int j = 1; j < ny - 1; j++) {
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0);
                    double t1 = st.t[st.idx(i, j, 1)];
                    int x = (int) Math.round(g.xOf(i));
                    int z = (int) Math.round(g.zOf(j));
                    double qf = f.netHeatFluxWm2(x, z, theta, SurfaceTemperatureSolver.T_FREEZE);
                    double t0;
                    if (Double.isNaN(qf)) {
                        t0 = st.t[q0];
                    } else if (qf < 0.0) {
                        t0 = SurfaceTemperatureSolver.T_FREEZE;
                    } else {
                        double lo = SurfaceTemperatureSolver.T_FREEZE, hi = 400.0;
                        double flo = qf;
                        double fhi = f.netHeatFluxWm2(x, z, theta, hi);
                        if (Double.isNaN(fhi) || flo * fhi > 0.0) {
                            t0 = st.t[q0];
                        } else {
                            for (int it = 0; it < 20; it++) {
                                double mid = 0.5 * (lo + hi);
                                double fm = f.netHeatFluxWm2(x, z, theta, mid) - rhoCpKv * (mid - t1) / dz0;
                                double floR = flo - rhoCpKv * (lo - t1) / dz0;
                                if (floR * fm <= 0.0) hi = mid;
                                else { lo = mid; flo = f.netHeatFluxWm2(x, z, theta, mid); }
                            }
                            t0 = 0.5 * (lo + hi);
                        }
                    }
                    double old = st.t[q0];
                    double nv = old + SOR * (t0 - old);
                    st.t[q0] = nv;
                    res = Math.max(res, Math.abs(nv - old));
                }
            }
            for (int k = 1; k < nz; k++) {
                double dz = g.dz[k];
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int q = st.idx(i, j, k);
                        double cv = kvAt(g, st, i, j, k) / (dz * dz);
                        double t = st.t[q];
                        double tw = g.isWet(i - 1, j, k) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j, k) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1, k) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tn = g.isWet(i, j + 1, k) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        // A3: below the seabed (k+1 >= wetLayers) is dry -> zero gradient.
                        double tdn = (k + 1 < g.wetLayers[g.idx(i, j)]) ? st.t[st.idx(i, j, k + 1)] : t;

                        double u = st.u[q], v = st.v[q];
                        double advX = u >= 0 ? u * (t - tw) / dx : u * (te - t) / dx;
                        double advY = v >= 0 ? v * (t - ts) / dy : v * (tn - t) / dy;
                        double adv = advX + advY + advZ(st, i, j, k, t, dz);

                        double diff = khAt(g, k) * (tw + te - 2.0 * t) / (dx * dx)
                                    + KH * (ts + tn - 2.0 * t) / (dy * dy)
                                    + cv * (tup + tdn - 2.0 * t);
                        double diag = 4.0 * khAt(g, k) / (dx * dx) + 4.0 * khAt(g, k) / (dy * dy) + 2.0 * cv
                                    + Math.abs(u) / dx + Math.abs(v) / dy
                                    + advZDiag(st, i, j, k, dz);
                        double target = t + (diff - adv) / diag;
                        double nv = t + SOR * (target - t);
                        st.t[q] = nv;
                        double d = Math.abs(nv - t);
                        if (d > res) res = d;
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        return new Result(iter, res);
    }
}
