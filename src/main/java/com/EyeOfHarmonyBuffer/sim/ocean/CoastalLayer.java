package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * 沿岸上升流层（eastern coastal layer）—— **东边界流的真身**。
 *
 * <h3>为什么需要它（设计冻结 §29）</h3>
 * 一维 Munk 行模型在东边界**没有边界层**（衰减模态是西边界那一支），
 * 所以「东带均速 = curl/(rho0*H*beta)」是**局部量**，只有 0.5~2.9 mm/s。
 * 真实海洋的加州流/加那利流不是这个 —— 它是**沿岸上升流急流**：
 *
 * <pre>
 *   沿岸风 tau_along
 *     -> 离岸埃克曼输送 M_x = |tau_along| / (rho*f)
 *     -> 近岸水被抽走，温跃层抬升 h_c
 *     -> 抬升面是倾斜的，地转流沿岸向
 *   沿岸一路累积：  h_c(z) = ∫ tau_along / (rho*g'*H1) ds
 *   地转急流：      v(x)   = (g'/f) * (h_c/R_d) * exp(-(x_e-x)/R_d)
 *   内 Rossby 半径：R_d    = sqrt(g'*H1) / |f|
 * </pre>
 *
 * <h3>⚠ 已被否决的闭合：沿岸空间累积（§77 实测）</h3>
 * 早先的写法是 <code>h_c(z) = ∫ tau_along/(rho*g'*H1) ds</code>，用长度尺度
 * <code>L_RELAX</code> 的指数核在**整个气候周期**上累积。P290/P320 实测否决：
 * <pre>
 *   L_RELAX = MAX_D = 10,000 km（旧值）  方向正确 44%（32 样本）  |东带|>=20mm/s 44%
 *   L_RELAX = 3,000 km                   方向正确 25%            |东带|>=20mm/s  0%
 * </pre>
 * 两个硬伤：
 * <ol>
 *   <li><b>量级来自一条 10,000 km 的记忆，而它与模块自己的尺度核对不一致</b> ——
 *       旧注释写的是 <code>tau=0.1 Pa, L_along=10,000 km -> h_c=97.6 m</code>，
 *       但 97.6 m 反推的 L 其实是 <b>3,000 km</b>（§77.2）。10,000 km 把 h_c 抬到 250~640 m，
 *       比独立物理估计（62 m）大 4~10 倍；</li>
 *   <li><b>方向不可能稳健</b>：沿岸累积把 55~82 度冬季的强向极 tau（+0.90 Pa @55.5 度）
 *       一路带到副热带，使 h_c 在冬季翻号。</li>
 * </ol>
 * ⇒ <b>已改用下面的「局地表征」(<code>hcLocal</code>)，它没有记忆长度这个自由度。</b>
 *
 * <h3>✅ 现在的闭合：离岸埃克曼输送 x 上升流季节 / 离岸宽度（局地）</h3>
 * <pre>
 *   M_off = -tau_s / (rho*f)        离岸埃克曼输送（m^2/s，正 = 离岸 = 上升流）
 *   h_c   = -M_off * T_up / L_x = tau_s * T_up / (rho*f*L_x)
 * </pre>
 * 尺度核对（本世界，tau_s = 0.06 Pa、f@30 度、T_up = 90 d、L_x = W = 100 km）：
 * <pre>
 *   h_c   = 62.4 m        真实沿岸上升流的温跃层抬升 50~150 m ✓
 *   v_max = 0.72 m/s      ⚠ 高于真实东边界流表层 0.2~0.5 m/s
 *   T_E   = 2.6 Sv        加州流沿岸急流 ~1~2 Sv ✓
 *   全深均 @100km = 6.3 mm/s
 * </pre>
 * ⇒ <b>A3 的 20 mm/s 达不到，而且差的不是 1.5 倍而是 ~3 倍</b>：
 * 20 mm/s @100 km x 4000 m 等价于**带内 8 Sv** 的输运，沿岸上升流急流只给 2~3 Sv。
 * 20 mm/s 实际描述的是**整个涡旋尺度的东边界流系统**（真实加州流 10 Sv/(100km x 4000m) = 25 mm/s），
 * 不是这条急流。**用户已裁决按物理重锚（2026 会话）。**
 *
 * <p><b>结构性限制</b>：<code>jetPeak</code> 用 R_d 当急流宽度，30 度处 R_d = 23.8 km
 * ⇒ 全深均 = 8.8e-3 * v_max。要 20 mm/s 需要 v_max = 2.3 m/s（观测 0.2~0.5）。
 * <b>v_max、h_c、全深均三个量里只能对上两个。</b>
 *
 * <h3>方向判据的状态（§86 复测后：<b>已达标，挂起解除</b>）</h3>
 * 大气侧的 u_zm 真实表 + cell 项赤道门控 + p_ref 经向梯度落地后（§86），
 * 同一批 32 个样本给 <b>26/32 = 81%</b>（判据 >=80%），拆解：
 * <ul>
 *   <li><b>|lat| 20~30 度 = 16/16 = 100%</b> —— 海洋侧能管的部分全对；</li>
 *   <li><b>±45 度不计入</b>：那里 NH 冬季的向极风在真实地球上是对的（东北太平洋冬季南风、
 *       阿拉斯加流向极、加州流只到 ~48 度）；</li>
 *   <li><b>±10 度：8/8 = 100%</b> —— §86 之前是 3/8（38%），根因在<b>大气侧</b>：
 *       cell 项在赤道带符号错（假高压）+ p_ref 经向梯度从未进 v。两处都在大气侧修好了，
 *       <b>沿海岸风参数化仍然关闭</b>（COAST_WIND_ON = false，§52 的否决不变）。</li>
 * </ul>
 * ⇒ 待大气侧修好 10 度的沿岸风之后再重测这一条。
 *
 * <h3>旧闭合（保留只为历史探针可复现，A3 不再使用）</h3>
 * 纯积分会在净输入不为零时跨周期线性发散，所以旧版用长度尺度 L_relax 的指数核，
 * 并在**周期边界**下迭代到收敛。
 */
public final class CoastalLayer {

    private CoastalLayer() {}

    /** 约化重力（m/s^2）。地球上层海洋典型值 0.02。 */
    public static double G_PRIME = 0.02;
    /**
     * **沿岸上升流层厚度（m）** —— 沿岸层占的厚度。**不是**全水深，
     * 也**不是** {@link SurfaceLayer} 里那个输运层（审计 D51）。
     *
     * <p>⚠ 这个名字历史上被两个类共用过（本类 150 / SurfaceLayer 800），
     * 而它们是**两个不同的物理量**：本值是「大陆架上升流把温跃层顶到近表层」的厚度，
     * SurfaceLayer 的那个是「大洋内区风生环流的垂向衰减尺度」。
     * 现在 SurfaceLayer 那边已改名 {@code H_TRANSPORT}。
     * <b>看到 150 与 800 并存不是漂移，是两件事。</b>
     */
    public static double H_THERMOCLINE = 150.0;
    /**
     * 全水深（m），只用于把沿岸层速度折算成全深平均。
     *
     * <p>⚠ 与 {@code OceanField.H_TOTAL} 是**两个运行时旋钮、初值同源**，
     * 二者**必须相等**（否则「沿岸层折算」与「Sverdrup 积分」用的深度不一致）。
     * OceanField 那边的初值已改成引用本字段（审计 D51）。
     */
    public static double H_TOTAL = 4000.0;
    /** 海水密度（kg/m^3）。**ocean/ 包内的单一来源**；SurfaceLayer.RHO 是同一物理量的独立副本。 */
    public static double RHO = 1025.0;
    /**
     * 累积的松弛长度（m）—— **只被旧版累积闭合使用，A3 已不用**。
     *
     * <p>⚠ 旧值 = MAX_D = 10,000 km，与模块自己的尺度核对（97.6 m 反推 3,000 km）矛盾，
     * 且实测把 h_c 抬高 4~10 倍（§77.2）。**已按尺度核对改成 3,000 km**（用户裁决）。
     * 注意：改成 3,000 km 会让旧累积的方向正确率**下降**（44% -> 25%）——
     * 这正是「缩短记忆不是修法」的证据，见 §77.1。
     */
    public static double L_RELAX = 3_000_000.0;
    /**
     * |f| 下限（s^-1），避免赤道发散。锚点 = **3.93 度纬度**
     * （见 {@code hcLocal} 的 javadoc：这条下限只在 |lat| &lt; asin(F_MIN/(2*Omega)) = 3.93 度 起作用）。
     *
     * <p>⚠ <b>与 {@link SurfaceLayer#F_MIN}（1.27e-5，锚点 5 度）不是同一个量</b>（审计 D51）：
     * 本值服务于**沿岸层**。**不要「统一」成一个值。**
     */
    public static double F_MIN = 1.0e-5;

    /**
     * 本类旋钮的**配置指纹**（纯函数，只读自己的 public static）。
     *
     * <p>⚠ 审计 D58：本类的常量**全部参与** OceanField.solveRow 的解
     * （rossbyRadius / hcLocal / coastTangent / jetPeak），所以改任何一个都会改 SST' ⇒ 必须进指纹。
     * 这里**不求精细**（把不在解里的 L_RELAX / UPWELL_* 也算进去了）——
     * 宁可多失效一次缓存，也不要漏掉一个真旋钮。
     */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + Double.doubleToLongBits(G_PRIME);
        h = h * 31 + Double.doubleToLongBits(H_THERMOCLINE);
        h = h * 31 + Double.doubleToLongBits(H_TOTAL);
        h = h * 31 + Double.doubleToLongBits(RHO);
        h = h * 31 + Double.doubleToLongBits(L_RELAX);
        h = h * 31 + Double.doubleToLongBits(F_MIN);
        h = h * 31 + Double.doubleToLongBits(UPWELL_SEASON_DAYS);
        h = h * 31 + Double.doubleToLongBits(UPWELL_WIDTH);
        h = h * 31 + COAST_TAN_DZ;
        h = h * 31 + COAST_TAN_N;
        h = h * 31 + COAST_TAN_WIN;
        h = h * 31 + COAST_TAN_STEP;
        h = h * 31 + Double.doubleToLongBits(SMOOTH_KM);
        return h;
    }

    /** 内 Rossby 半径 R_d = sqrt(g'*H1)/|f|（m）。 */
    public static double rossbyRadius(double f) {
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        return Math.sqrt(G_PRIME * H_THERMOCLINE) / fa;
    }

    // ==================== A3 的闭合（现行） ====================

    /**
     * 上升流季节长度（天）。
     *
     * <p>物理锚：主要东边界上升流系统（加州/加那利/秘鲁/本吉拉）的**上升流季**是
     * 3~6 个月，本式取 90 天（一个季节）作为「风把温跃层抬起来、又被回流抹平」的
     * 有效积分时间。**它是观测到的季节长度，不是拟合出来的**。
     */
    public static double UPWELL_SEASON_DAYS = 90.0;

    /**
     * 离岸宽度 L_x（m）：上升流把水从多宽的带里抽走。
     *
     * <p><b>取 A3 的验收带宽 W = 100 km，不引入新参数</b> —— 沿岸层就是这个 100 km 带。
     */
    public static double UPWELL_WIDTH = 100_000.0;

    /**
     * **沿岸层的唯一入口**：由局地沿岸风应力给出温跃层位移 h_c（m，负 = 抬升 = 上升流）。
     *
     * <pre>
     *   M_off = -tau_s/(rho*f)                     离岸埃克曼输送（正 = 离岸）
     *   raw   = tau_s*T_up/(rho*f*L_x) = -M_off*T_up/L_x
     *   h_c   = H1*tanh(raw/H1)                    ⚠ H1 只是**有界化**
     * </pre>
     *
     * <p>性质：连续、无阈值、无分支、纯函数、与 |f| 单调同号（⇒ 方向不受有界化影响）。
     *
     * <p><b>为什么要有 tanh 这一层</b>：<code>raw</code> 在 |f|-&gt;0 时发散
     * （10 度处 tau_s=0.32 Pa 给 raw = 970 m —— 温跃层只有 150 m 厚，物理上不可能）。
     * 温跃层被抽干就不可能再抬 ⇒ 位移以层厚 H1 为界。
     * tanh **单调、光滑、零新参数**；代价是物理区间（50~150 m）被压缩 5~25%，
     * 所以同时提供有界化之前的 {@link #hcLocalRaw}。
     *
     * @param tauAlong 沿岸风应力（Pa）。**必须投影到局地海岸切向上**（见 {@link #coastTangent}）
     * @param f        科氏参数（带符号）
     */
    public static double hcLocal(double tauAlong, double f) {
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        double fs = f < 0.0 ? -fa : fa;
        double raw = tauAlong * (UPWELL_SEASON_DAYS * 86400.0) / (RHO * fs * UPWELL_WIDTH);
        return H_THERMOCLINE * Math.tanh(raw / H_THERMOCLINE);
    }

    /** {@link #hcLocal} 的**无界**原始值（m），只为诊断/尺度核对用。 */
    public static double hcLocalRaw(double tauAlong, double f) {
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        double fs = f < 0.0 ? -fa : fa;
        return tauAlong * (UPWELL_SEASON_DAYS * 86400.0) / (RHO * fs * UPWELL_WIDTH);
    }

    // ---- 局地海岸切向（用户裁决：开） ----

    /** 求切向时的纬向步长（m）。 */
    public static int COAST_TAN_DZ = 125_000;
    /** 求切向时向 ±z 取的步数（共 2*N+1 个海岸位置做最小二乘）。 */
    public static int COAST_TAN_N = 2;
    /** 每个 z 上找海岸的扫描半窗（m）。 */
    public static int COAST_TAN_WIN = 300_000;
    /** 扫描步长（m）。 */
    public static int COAST_TAN_STEP = 5_000;

    /**
     * **局地海岸切向**（沿岸单位向量，指向 +z）。
     *
     * <p>返回 <code>{t_x, t_y}</code> 满足 <code>(t_x,t_y) = (-n_y, n_x)</code>，
     * 其中 <code>n</code> 是**离岸**外法向（东岸上 n_x &lt; 0）。
     * 于是 <code>M_off = -tau.t/(rho*f)</code> 在南北两个半球都成立
     * （<b>不要</b>用 tau_y 代替 tau.t：本世界实测海岸切向偏离子午向 0~67 度）。
     *
     * <p>海岸位置由 ±{@link #COAST_TAN_DZ} 上的最小二乘斜率给出；
     * 任一 z 上找不到海岸（海盆过宽/狭）就**回退成子午向 (0,1)** —— 这是「没有海岸信息」，
     * 不是阈值判据。纯函数：只读 (seed,x,z,cell) 的地形。
     */
    public static double[] coastTangent(int xEast, int z, long seed, int cell) {
        int n = 2 * COAST_TAN_N + 1;
        double[] xs = new double[n];
        double sx = 0, sz = 0;
        int zc = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
        for (int m = -COAST_TAN_N; m <= COAST_TAN_N; m++) {
            int zn = (int) (((z + (long) m * COAST_TAN_DZ) % zc + zc) % zc);
            int xb = coastNear(xEast, zn, seed, cell);
            if (xb == Integer.MIN_VALUE) return new double[]{0.0, 1.0};
            xs[m + COAST_TAN_N] = xb;
            sx += xb; sz += m * (double) COAST_TAN_DZ;
        }
        sx /= n; sz /= n;
        double sxy = 0, szz = 0;
        for (int k = 0; k < n; k++) {
            // ⚠ 2026-09-13 修正（审计 D33）：原来写的是 k * COAST_TAN_DZ - sz，少了 - COAST_TAN_N。
            // 第一循环的 m 是**已居中**的（sz = mean(m*DZ) == 0），第二循环的 k 却从 0 起。
            // 因 Σ(xs[k]-sx) = 0，分子 Σ u_k·k·DZ 与正确值 Σ u_k(k-N)·DZ **恰好相等**，
            // 而分母 Σ(k·DZ)^2 = 30·DZ^2、正确值 Σ((k-N)·DZ)^2 = 10·DZ^2（N=2, n=5）
            // ⇒ slope_code = slope_true / 3，**对任意海岸形状成立**（最大 30 度切向误差）。
            double dz = (k - COAST_TAN_N) * (double) COAST_TAN_DZ - sz;
            sxy += (xs[k] - sx) * dz;
            szz += dz * dz;
        }
        if (szz <= 0.0) return new double[]{0.0, 1.0};
        double slope = sxy / szz;                       // dx/dz
        double norm = Math.hypot(slope * COAST_TAN_DZ, COAST_TAN_DZ);
        return new double[]{slope * COAST_TAN_DZ / norm, COAST_TAN_DZ / norm};
    }

    /** 在 [x0-win, x0+win] 内找 x0 附近那条海岸（最东的海洋块）。找不到返回 MIN_VALUE。 */
    static int coastNear(int x0, int z, long seed, int cell) {
        int last = Integer.MIN_VALUE;
        for (int x = x0 - COAST_TAN_WIN; x <= x0 + COAST_TAN_WIN; x += COAST_TAN_STEP) {
            if (com.EyeOfHarmonyBuffer.sim.litho.PlateField.isLandWithCell(x, z, seed, cell)) {
                if (last != Integer.MIN_VALUE) return last;
            } else last = x;
        }
        return Integer.MIN_VALUE;
    }

    /**
     * 沿岸累积（周期边界下迭代到收敛）。**纯函数**。
     *
     * @param tauAlong 沿岸风应力（Pa，正 = 沿 +s 方向）沿路径的采样
     * @param ds       采样间距（m）
     * @return h_c（m，正 = 温跃层下沉 / 负 = 上升）
     */
    public static double[] steadyPeriodic(double[] tauAlong, double ds) {
        int n = tauAlong.length;
        double[] h = new double[n];
        double a = Math.exp(-ds / L_RELAX);
        // ⚠ 2026-09-13 修正（审计 D34）：ODE dh/ds = -h/L + tau/(rho*g'*H1) 的**精确一步**递推是
        //   h_i = a*h_{i-1} + (1-a)*L*tau/(rho*g'*H1)
        // 原来用 ds 代替了 (1-a)*L。常值 tau 的定点因此是
        //   h_inf = [tau*L/(rho*g'*H1)] * x/(1-e^-x),  x = ds/L_RELAX  —— 而 x/(1-e^-x) >= 1 恒成立
        // ⇒ 收敛值随采样步长变：ds=20 km ×1.003、100 km ×1.017、1000 km ×1.176、2000 km ×1.370，
        //   而 §77.2 用来反推 L_RELAX 的 97.6 m 锚点在**任何 ds 下都取不到**。
        // 改成 (1-a)*L 之后定点 = tau*L/(rho*g'*H1)，**与 ds 严格无关**。
        double k = (1.0 - a) * L_RELAX / (RHO * G_PRIME * H_THERMOCLINE);
        double acc = 0;
        int passes = 4;                      // e^{-2*4} ≈ 0.03% ⇒ 收敛
        for (int p = 0; p < passes; p++) {
            for (int i = 0; i < n; i++) {
                acc = acc * a + tauAlong[i] * k;
                h[i] = acc;
            }
        }
        return h;
    }

    /**
     * **按涡旋分段**的沿岸累积（周期边界下迭代到收敛）。
     *
     * <p>为什么必须分段：副热带是上升流（tau_along < 0），副极地是下涌（tau_along > 0），
     * 两者在整周期积分里会**互相抵消**，导致副热带的 h_c 被极区那一段盖掉
     * （§35.3：实测 h_c 符号是正的，而副热带应当是负的）。
     *
     * <p>物理上两者本来就是**两个独立的涡旋系统**（副热带涡与副极地涡），
     * 沿岸波导在风应力反向处断开。所以在这里**把累积重置为 0**，
     * 每个涡旋各算各的。
     */
    public static double[] steadySegmented(double[] tauAlong, double ds) {
        int n = tauAlong.length;
        double[] h = new double[n];
        double a = Math.exp(-ds / L_RELAX);
        double k = (1.0 - a) * L_RELAX / (RHO * G_PRIME * H_THERMOCLINE);   // 同 steadyPeriodic（审计 D34）
        for (int pass = 0; pass < 4; pass++) {
            double acc = 0;
            int prev = 0;
            for (int i = 0; i < n; i++) {
                int sgn = tauAlong[i] > 0 ? 1 : (tauAlong[i] < 0 ? -1 : 0);
                if (sgn != 0 && prev != 0 && sgn != prev) acc = 0;   // 涡旋边界：重置
                if (sgn != 0) prev = sgn;
                acc = acc * a + tauAlong[i] * k;
                h[i] = acc;
            }
        }
        return h;
    }

    /**
     * **按涡旋尺度平滑后再累积** —— 取代「按符号重置」（那个版本太激进：
     * 逐点 tau_z 的噪声让累积不断被重置，h_c 根本长不起来）。
     *
     * <p>物理依据：沿岸波导响应的是**涡旋尺度**的沿岸风，不是逐点噪声。
     * 所以先在 SMOOTH_KM 的窗口上平滑 tau_along，再做周期累积。
     */
    public static double SMOOTH_KM = 1500.0;

    /**
     * 实际生效的平滑窗口宽度（m）。
     *
     * <p>⚠ 审计 D35-b：steadySmoothed 里的 Math.max(1, …) 是一个**未声明的下限** ——
     * 当 ds ≥ SMOOTH_KM/2（= 750 km）时 half 被抬到 1，实际窗口变成 3·ds，
     * 即标称 1500 km 的 1.5~4 倍（ds=1000 km ⇒ 3000 km、ds=2000 km ⇒ 6000 km）。
     * 本方法把它**暴露出来**，让探针能断言「我这一跑的窗口确实是标称值」。
     * （**不改公式** —— 粗采样下怎么定义窗口是裁决问题，不是 bug 判定。）
     */
    public static double smoothedWindowM(double ds) {
        int half = Math.max(1, (int) (SMOOTH_KM * 1000.0 / ds / 2.0));
        return (2 * half + 1) * ds;
    }

    public static double[] steadySmoothed(double[] tauAlong, double ds) {
        int n = tauAlong.length;
        int half = Math.max(1, (int) (SMOOTH_KM * 1000.0 / ds / 2.0));
        double[] sm = new double[n];
        for (int i = 0; i < n; i++) {
            double s = 0;
            for (int j = -half; j <= half; j++) s += tauAlong[((i + j) % n + n) % n];
            sm[i] = s / (2 * half + 1);
        }
        return steadyPeriodic(sm, ds);
    }

    /**
     * 沿岸急流峰值速度 v_max = (g'/f)*(h_c/R_d)（m/s，带符号）。
     *
     * <p>⚠ <b>P286 实测的赤道奇异点</b>：`rossbyRadius` 把 |f| 夹在 F_MIN，而这里原来用**真实 f**
     * 除以它 ⇒ 1/f 的抵消被打破 ⇒ **lat=0 处 v_max = ±Infinity、lat=2 处 −252.8 mm/s**。
     * 现在**两边用同一个有效 f**（只夹量值、保留符号），于是
     * <code>v_max = sign(f)*g'*h_c/sqrt(g'*H1)</code>，与 |f| 无关、处处有限。
     * <p>注意：这**只**改变 |lat| &lt; asin(F_MIN/(2Ω)) = 3.93 度 的区间，其余逐位不变。
     */
    public static double jetPeak(double hc, double f) {
        double fa = Math.abs(f);
        if (fa < F_MIN) fa = F_MIN;
        double fs = f < 0.0 ? -fa : fa;
        return (G_PRIME / fs) * (hc / rossbyRadius(fs));
    }

    /**
     * 沿岸层对「东带 W 宽、全深 H 平均」的贡献（m/s）。
     *
     * <pre>
     *   psi_c = v_max * R_d * (1 - exp(-W/R_d))      沿岸层内的流函数（m^2/s）
     *   贡献  = (H1/H) * psi_c / W                    折算成全深平均
     * </pre>
     */
    public static double eastBandContribution(double hc, double f, double W) {
        double rd = rossbyRadius(f);
        double vmax = jetPeak(hc, f);
        double psi = vmax * rd * (1.0 - Math.exp(-W / rd));
        return (H_THERMOCLINE / H_TOTAL) * psi / W;
    }

    /**
     * **沿岸层急流的横截面体积输运（Sv）**，诊断用。
     *
     * <pre>
     *   psi = v_max * R_d            （m^2/s，「每米岸线」的流函数幅值）
     *   Q   = psi * H_THERMOCLINE    （m^3/s ；乘的是**层厚**，把「每米岸线」变成「一个横截面」）
     *   Sv  = Q / 1e6
     * </pre>
     *
     * <p>量级核对：v_max = 0.682 m/s、R_d(30 度) = 23.76 km、H1 = 150 m
     * ⇒ 0.682 x 23760 x 150 = 2.43e6 m^3/s = **2.43 Sv**，与真实沿岸上升流（1~3 Sv）同量级。
     *
     * <p>⚠⚠ <b>审计 D37 的正确处置（2026-09-13，含我自己的 E36 记账）</b>：
     * 这个函数原来有第三个入参 {@code alongshoreKm}，却**从来没有用到它**。
     * 我第一版的「修法」是**给它编一个用途**（乘上岸线长度），结果 T_E 从 **2.43 Sv 变成 16195.90 Sv** ——
     * 后者比南极绕极流（~130 Sv）还大两个数量级，**物理上荒谬**。
     * <b>真正错的是签名，不是公式</b>：体积输运是**横截面**积分，本来就不该依赖沿程长度。
     * ⇒ 正确修法是**删掉那个参数**（而不是给它找个用处）。
     * <b>退回原来的公式后 T_E 逐位回到 2.43 Sv（P292 的读数证明）。</b>
     */
    public static double transportSv(double hc, double f) {
        double rd = rossbyRadius(f);
        double vmax = jetPeak(hc, f);
        double psi = vmax * rd;                        // m^2/s（每米岸线）
        return psi * H_THERMOCLINE / 1e6;              // m^3/s -> Sv
    }
}
