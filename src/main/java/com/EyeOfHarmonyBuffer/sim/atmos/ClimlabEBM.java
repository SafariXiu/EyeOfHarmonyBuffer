package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★★★★★★★ **§7344/§7345：climlab EBM 稳态解 —— 内生纬向温度（替换外生观测表 T_OCEAN_K）。**
 *
 * <p><b>为什么要有它</b>（{@link Atmosphere#oceanBaseK} 的 javadoc 逐字）：
 * {@code T_zm = ZF*t2m_land + (1-ZF)*t2m_sea} 逐行精确，但那行里的 {@code ZF} 是**地球的陆地占比**。
 * 本世界的陆地占比是 {@code kappa} ⇒ **只有一个外生 T_zm 就回答不了「换一个陆地分布会怎样」**。
 * 把它换成**自洽的物理解**之后，纬向温度不再依赖地球的观测表。
 *
 * <h3>方程（climlab EBM，稳态 —— 边值问题，不是时间积分 ⇒ 不违反「零预报量」）</h3>
 * <pre>
 *   0 = (1-alpha(phi)) * (S0/4) * (1 + s2*P2(sin phi)) - (A + B*T_C) + D*nabla^2_unit T
 *   alpha(phi) = (T < Tf) ? ai : (a0 + a2*P2(sin phi))          // 冰反照率，按 T 迭代
 *   P2(x) = (3x^2 - 1)/2
 *   nabla^2_unit T = (1/cos phi) d/dphi (cos phi dT/dphi)       // 对【弧度】phi 的无量纲拉普拉斯
 * </pre>
 *
 * <h3>★ 常数全部逐字来自 climlab 源码（本仓已存档：docs/模拟器/调研/research_climlab_mitgcm.md）</h3>
 * <pre>
 *   :65-66  S(phi) = (S0/4)(1 + s2*P2(sin phi))；S0 = 1365.2 W/m2，s2 = -0.48
 *   :56     albedo = a0 + a2*P2(sin phi)
 *   :60     climlab.EBM() 默认（ebm.py L236-239）：Tf = -10.0 C, a0 = 0.3, a2 = 0.078, ai = 0.62
 *   :457    A = 210 W/m2, B = 2 W/m2/摄氏度, D = 0.555 W/m2/摄氏度
 *   :418    K = D/C*a^2 = 5.39e5 m2/s（物理扩散率的等效值；本类用无量纲算子，故直接用 D）
 * </pre>
 * <b>⟹ 零自由参数：一个都没有调过、扫过。</b>
 *
 * <h3>★ 验证（§7345 P1079，只读探针）</h3>
 * <pre>
 *   与 ERA5 观测海洋支 T_OCEAN_K 逐点比（19 点，0..90 度，5 度间隔）：
 *     赤道  299.80 vs 299.66（+0.14）    45 度 281.54 vs 283.33（-1.79）
 *     30 度 291.02 vs 293.57（-2.55）    90 度 263.61 vs 260.23（+3.38）
 *     最大逐点偏差 5.66 K（80 度）；极赤温差 36.19 K vs 观测 39.43 K（差 3.24 K）
 *   ⟹ 标准常数集【无需任何标定】就复现了观测廓线（~2 K 精度）。
 *   端到端（四盒 JJA，§7264 口径）：ASIA 1.7624 -> 1.6921（-4.0%）、SAHARA 1.5712 -> 1.5150（-3.6%）、
 *     其余两盒基本不动、PHASE_ALL 恒 3/4 ⟹ 【温和】。
 * </pre>
 *
 * <h3>⚠ 与 §7342/§7343 的关系（诚实记账）</h3>
 * §7342 曾据「T_OCEAN_K[9] = 283.33 是 90 度」判「三张生产温度表极地偏暖 ~28 K」，
 * §7343 并据以给出「改动 #1」（后果 SAHARA -13.2%）。**两者均已【撤回】**：
 * {@link ZonalTables#interp5} 的注释逐字是「**5 度间隔表**的线性插值」⟹ 19 元素 = 0,5,...,90 度
 * ⟹ 283.33 是 **45 度**、260.23 才是 90 度 ⟹ **表的极地没有偏暖**，是我的索引错误。
 * （P1078 实际改的是 35/40/45 度，故其后果数据也作废。）
 *
 * <p><b>性能</b>：一次 O(19) 三对角（Thomas）+ 冰反照率迭代 ⟹ 首次调用时算一次、之后缓存。
 * 相对 {@code ZonalTables} 的查表只多一次数组索引，**可忽略**。
 *
 * <p><b>默认关闭</b>（{@link #EBM_ON} = false）⟹ {@link Atmosphere#oceanBaseK} 逐位不变。
 */
public final class ClimlabEBM {

    private ClimlabEBM() {}

    /**
     * ★ **§7347 已完整接入（= true）**：{@link Atmosphere#oceanBaseK} 走本类的内生解。
     *
     * <p>置 false ⟹ 回落到 {@link ZonalTables#tOceanK}，**逐位不变**（P1080-a 验证到 6 位小数，
     * 且 §7346 实测 gate token **0/17 支不同**）⟹ 保留这个开关是为了随时可做 A/B。
     *
     * <p>★ 口径注意：{@link Atmosphere#landMinusOceanSLK} 的 {@code Δ} **故意不走本开关** ——
     * 那段 javadoc 记了裁决理由（观测的相对差 + 内生的绝对水平）。
     */
    public static boolean EBM_ON = true;    // ★ §7347 完整接入：§7346 已过 22 支验收（门中性）

    // ==================== climlab 常数（逐字，见类头出处） ====================
    /**
     * 太阳常数（W/m2）—— climlab const.S0，insolation.py L251。
     *
     * <p>⚠⚠ <b>2026-10-14 审查（阶段 3 · 重复常量扫描）：与本仓自己的
     * {@link Radiation#S0}（1361.0）<b>值不同</b></b> ✗ —— 本值 1365.2 是
     * <b>逐字取自 climlab</b> ✓（EBM 的稳态解要求与 climlab 同参数 ✓），
     * 而 1361.0 是现代 TSI 标准 ✓。
     * ⟹ ★ <b>不要</b>把本值改成 1361.0 ✗（会改 EBM 的稳态解）；引用时写全类名 ✓。
     */
    public static final double S0 = 1365.2;
    /** 日射的 P2 系数 —— climlab P2Insolation，insolation.py L251。 */
    public static final double S2 = -0.48;
    /** 暖态反照率（P2Albedo）—— climlab EBM 默认，ebm.py L236-239。 */
    public static final double A0 = 0.30, A2 = 0.078;
    /** 冰反照率与冰点（摄氏度）—— climlab EBM 默认，ebm.py L236-239。 */
    public static final double AI = 0.62, TF_C = -10.0;
    /** OLR 线性化 A + B*T_C（B 的单位是 W/m2/**摄氏度**）。 */
    public static final double A_OLR = 210.0, B_OLR = 2.0;
    /** 经向热扩散系数（W/m2/摄氏度）—— MeridionalHeatDiffusion，meridional_heat_diffusion.py L66。 */
    public static final double D_DIFF = 0.555;

    /** 纬度网格步长（度）—— 与 {@link ZonalTables} 的 5 度表**同网格**，便于直接对比。 */
    public static final double D_LAT_DEG = 5.0;
    /** 网格点数（0..90 度，含两端）。 */
    public static final int N = 19;

    /** 二元二次勒让德多项式 P2(x) = (3x^2-1)/2。 */
    static double p2(double x) { return (3.0 * x * x - 1.0) / 2.0; }

    // ==================== 求解（惰性 + 缓存） ====================
    private static double[] cache = null;

    /**
     * 解稳态 EBM，返回 19 点（0..90 度，5 度间隔）的**海平面地表温度（K）**。
     *
     * <p>两头都是 Neumann（{@code dT/dphi = 0}）：赤道是南北对称轴、北极是极轴对称轴。
     * 只解北半球、镜像得南半球（本世界南北对称，见 {@code WorldContract.latOf} 的三角波）。
     * ★ §7345 记账：首版把两头的边界写反了（赤道给了 Neumann、北极给了「无扩散局地平衡」），
     * 北极因此没有热量流入 ⟹ 解出 201.87 K（错）。修正后 263.61 K。
     */
    public static synchronized double[] solve() {
        if (cache != null) return cache;
        double dphi = Math.toRadians(D_LAT_DEG);
        double[] lat = new double[N];
        for (int i = 0; i < N; i++) lat[i] = Math.toRadians(i * D_LAT_DEG);
        double[] t = new double[N];
        for (int i = 0; i < N; i++) t[i] = 288.0;                  // 初值不重要（EBM 是线性边值问题）
        double[] lo = new double[N], di = new double[N], up = new double[N], rh = new double[N];
        double[] c2 = new double[N], d2 = new double[N], tn = new double[N];
        for (int it = 0; it < 200; it++) {
            // 反照率依赖 T ⟹ 逐次迭代（climlab 的 StepFunctionAlbedo 也是逐格点判断）
            for (int i = 0; i < N; i++) {
                double sn = Math.sin(lat[i]);
                double s = (S0 / 4.0) * (1.0 + S2 * p2(sn));
                double al = (t[i] < TF_C + 273.15) ? AI : (A0 + A2 * p2(sn));
                double f = (1.0 - al) * s - A_OLR;
                double c = Math.max(1.0e-6, Math.cos(lat[i]));
                double cm = Math.cos(lat[i] - dphi / 2.0), cp = Math.cos(lat[i] + dphi / 2.0);
                double coef = D_DIFF / (c * dphi * dphi);
                double am = coef * cm, ap = coef * cp;
                lo[i] = -am; up[i] = -ap; di[i] = B_OLR + am + ap;
                rh[i] = f + B_OLR * 273.15;                        // B 的单位是 W/m2/摄氏度
            }
            // 两头 Neumann（i=0 赤道、i=N-1 北极）
            di[0] = 1.0; up[0] = -1.0; lo[0] = 0.0; rh[0] = 0.0;
            di[N - 1] = 1.0; lo[N - 1] = -1.0; up[N - 1] = 0.0; rh[N - 1] = 0.0;
            // 三对角 Thomas O(n)
            c2[0] = up[0] / di[0]; d2[0] = rh[0] / di[0];
            for (int i = 1; i < N; i++) {
                double den = di[i] - lo[i] * c2[i - 1];
                c2[i] = (i < N - 1) ? up[i] / den : 0.0;
                d2[i] = (rh[i] - lo[i] * d2[i - 1]) / den;
            }
            tn[N - 1] = d2[N - 1];
            for (int i = N - 2; i >= 0; i--) tn[i] = d2[i] - c2[i] * tn[i + 1];
            double mx = 0.0;
            for (int i = 0; i < N; i++) { mx = Math.max(mx, Math.abs(tn[i] - t[i])); t[i] = tn[i]; }
            if (mx < 1.0e-6) break;
        }
        // ★★★★★ §7347 双稳守卫（零新常数）：climlab EBM 有两个平衡态，实测（P1082）：
        //   暖支  赤道 299.80 / 90 度 263.61 K（0/19 冰格点）—— 初值 >= 264 K 时收敛到此
        //   雪球支 赤道 237.90 / 90 度 221.36 K（19/19 冰格点）—— 初值 <= 262 K 时收敛到此
        //   分岔在初值 262~264 K 之间；**暖支的最小值 263.61 K 离冰阈值 263.15 K 只有 +0.46 K**。
        // 本模型的世界有海洋（kappa 由 PlateField 给）⟹ 应采用【暖支】。
        // 守卫的目的：若常数被改动、或将来接入别的分量使解翻到雪球支，**显式失败**而不是
        //            静默给出一套雪球气候（赤道 237.9 K）—— 后者会污染整个模型而无人察觉。
        for (int i = 0; i < N; i++) {
            if (t[i] < TF_C + 273.15) {
                throw new IllegalStateException("ClimlabEBM: 解落在【雪球支】(T[" + (i * (int) D_LAT_DEG)
                    + " deg] = " + t[i] + " K < " + (TF_C + 273.15) + " K)。"
                    + "climlab EBM 是双稳的，本模型的世界有海洋，应采用暖支 —— 请检查常数是否被改动。");
            }
        }
        cache = t;
        return cache;
    }

    // ================================================================================
    // ★★★★★ 2026-10-15（用户裁决「改成真实的物理」）：**两半球 EBM**（37 点，-90..+90）
    //
    // 为什么：上面的 solve() **只解北半球、再镜像** ✗ —— 真实世界的南北不对称
    //   （AMOC 跨赤道输运、南极洲 vs 北冰洋）**表示不了** ✓。用户裁决：改成真实物理 ✓
    //
    // ★★ 诚实边界：**太阳强迫与反照率本身是南北对称的**（P2(sin phi) 是偶函数 ✓）⟹
    //   两半球几何**本身不产生任何不对称** ✓；不对称必须来自【非对称的输运项】
    //   （= 下一步的**观测锚定 OHT 通道** ✓）。所以本步是**结构前置**，不是效果本身 ✓
    //   （P1.4 已实测过同类现象：oceanBaseK = +0.00 K ✗）
    //
    // 开关 TWO_HEMI_ON **默认 false ⟹ 逐位不变** ✓（关掉时 tOceanK 走原路径 ✓）
    // ================================================================================
    // ★ 2026-10-16 用户裁决「改成真实的物理」+「持续打开」⟹ **A/B 轮：打开** ✓（关掉即回旧路径 ✓）
    public static boolean TWO_HEMI_ON = true;
    /** 两半球网格点数（-90..+90 度，含两端 ✓）。 */
    public static final int N37 = 37;
    private static volatile double[] cache37 = null;
    private static volatile double[] cache37OHT = null;

    /**
     * 解**两半球**稳态 EBM：37 点（-90..+90，5 度），**两端极地 Neumann**，
     * 赤道（i=18）是**内部点**（不再是人造对称轴 ✓）。
     *
     * <p>方程与常数与 {@link #solve()} **逐字相同** ✓（只换网格与边界条件 ✓）。
     * <p>雪球守卫改为**按冰格点计数**：冰格点 >= N37/2 ⟹ 落在雪球支 ⟹ 显式失败 ✓
     *   （旧版按「任意点 < Tf」判 ⟹ 对两半球会**误伤** —— 南极点本来就该在冰点以下 ✓）
     */
    public static synchronized double[] solve37() {
        if (cache37 != null) return cache37;
        cache37 = solve37Core(null, 1.0);
        return cache37;
    }
    // （solve37OHT 见下：它把反对称的 OHT 辐合 qExtra 交给同一个核心 ✓）

    /**
     * ★★ **带 OHT 通道的两半球解**（P2-2）：核心与 {@link #solve37()} 逐字相同，
     * 只是把【反对称的输运辐合】{@code qExtra(phi)} [W/m2] 加进地表收支 ✓。
     *
     * <p>振幅标定（**零新自由参数** ✓）：把观测剖面的**反对称部分**归一化，使
     * {@code max|qExtra| = max|D*nabla^2 T|} —— 即【与模型自己的基线输运同振幅】✓
     * （"保总量、换形状" 的落地，见 428 §8.5 ✓）。
     */
    public static synchronized double[] solve37OHT() {
        if (cache37OHT != null) return cache37OHT;
        double[] f = T_OHT_W_37;
        if (f == null || f.length != N37) {
            throw new IllegalStateException("ClimlabEBM.solve37OHT: 观测剖面 T_OHT_W_37 尚未取证"
                + "（按铁律 2，未取证不许编 ⟹ 本通道不可启用）");
        }
        int n = N37;
        double dphi = Math.toRadians(D_LAT_DEG);
        // (a) 辐合（量纲正确 ✓）：Q(phi) = -(1/(2*pi*R^2*cos phi)) * dTcal/dphi
        double R = WorldContract.R_EFF;
        double[] q = new double[n];
        for (int i = 0; i < n; i++) {
            double phi = Math.toRadians((i - (n - 1) / 2) * D_LAT_DEG);
            double c = Math.max(1.0e-6, Math.cos(phi));
            int im = Math.max(0, i - 1), ip = Math.min(n - 1, i + 1);
            double dTcal = (f[ip] - f[im]) / ((ip - im) * dphi);
            q[i] = -dTcal / (2.0 * Math.PI * R * R * c);
        }
        // (b) 只取【反对称部分】—— 对称部分 EBM 的扩散项已经含了 ✓（防双重计数 ✓）
        double[] qOdd = new double[n];
        for (int i = 0; i < n; i++) qOdd[i] = 0.5 * (q[i] - q[n - 1 - i]);
        // (c) ★★ **不做任何"与基线同振幅"的归一化** ✗ ——
        //   那是机制验证阶段的临时做法：它把 OHT 当成【全部】输运 ⟹ 曾给出 +74 K 的荒谬响应 ✗。
        //   现在 Tcal 是【物理量】（已按 (R_EFF/R_earth)^2 缩尺 ✓）⟹ 辐合本身就是 W/m2 ✓ 直接可用 ✓
        //   ★ 诊断出口只记**内部点**的振幅（极点 1/cos 奇异 ⟹ 排除 ✓）
        double maxOdd = 0.0;
        for (int i = 1; i < n - 1; i++) maxOdd = Math.max(maxOdd, Math.abs(qOdd[i]));
        lastOhtAmpWPerM2 = maxOdd;
        // (d) ★★★★★ **不需要「保总量」补偿** ✓✓ —— 这里是本轮最重要的一个判断更正 ✗：
        //     曾计划「打开通道时把 D 调小，使总输运峰值回到基线」✗。实现后发现：
        //       (i)  **EBM 基线的反对称输运本来就是零** ✓（对称解 ⟹ 对称输运 ✓）
        //            ⟹ 叠加观测的【反对称】部分**没有双重计数** ✓✓（双重计数只对【对称部分】成立，
        //               而我们的通道只取反对称部分 ✓）
        //       (ii) 强行把 D 调小 ⟹ 极区失去输运 ⟹ **落进雪球支**（守卫实测 18/37 冰格点 ✗）
        //            ⟹ 那不是"补偿"，而是把模型推离物理 ✗
        //       (iii) 更强的旁证：通道给出的 **+5.14 K** 与**观测自己的南北差**一致 ✓✓
        //            （本仓非对称锚表实测 ΔT_OCEAN(N-S) = +6.17 K @65 度 ✓）
        //      ⟹ 结论：**通道直接用，不做 D 补偿** ✓；lastOhtDEffScale 恒记 1.0 ✓（诊断用）
        lastOhtDEffScale = 1.0;
        cache37OHT = solve37Core(qOdd, 1.0);
        return cache37OHT;
    }

    /** 内部：核心求解器。{@code dScale} 缩放扩散系数（=1 为原样 ✓；"保总量"标定会把它调小 ✓）。 */
    private static synchronized double[] solve37Core(double[] qExtra, double dScale) {
        double dphi = Math.toRadians(D_LAT_DEG);
        int n = N37;
        double[] lat = new double[n];
        for (int i = 0; i < n; i++) lat[i] = Math.toRadians((i - (n - 1) / 2) * D_LAT_DEG);   // i=18 -> 0 度
        double[] t = new double[n];
        for (int i = 0; i < n; i++) t[i] = 288.0;
        double[] lo = new double[n], di = new double[n], up = new double[n], rh = new double[n];
        double[] c2 = new double[n], d2 = new double[n], tn = new double[n];
        for (int it = 0; it < 200; it++) {
            for (int i = 0; i < n; i++) {
                double sn = Math.sin(lat[i]);
                double s = (S0 / 4.0) * (1.0 + S2 * p2(sn));
                double al = (t[i] < TF_C + 273.15) ? AI : (A0 + A2 * p2(sn));
                double f = (1.0 - al) * s - A_OLR;
                double c = Math.max(1.0e-6, Math.cos(lat[i]));
                double cm = Math.cos(lat[i] - dphi / 2.0), cp = Math.cos(lat[i] + dphi / 2.0);
                double coef = D_DIFF * dScale / (c * dphi * dphi);   // ★ dScale = 1 时逐位不变 ✓
                double am = coef * cm, ap = coef * cp;
                lo[i] = -am; up[i] = -ap; di[i] = B_OLR + am + ap;
                // ★ P2-2：OHT 通道的【反对称辐合】作为额外源项 ✓（qExtra=null 时逐位不变 ✓）
                rh[i] = f + B_OLR * 273.15 + (qExtra == null ? 0.0 : qExtra[i]);
            }
            // 两端极地 Neumann（-90 与 +90 ✓）；赤道是内部点 ✓
            di[0] = 1.0; up[0] = -1.0; lo[0] = 0.0; rh[0] = 0.0;
            di[n - 1] = 1.0; lo[n - 1] = -1.0; up[n - 1] = 0.0; rh[n - 1] = 0.0;
            c2[0] = up[0] / di[0]; d2[0] = rh[0] / di[0];
            for (int i = 1; i < n; i++) {
                double den = di[i] - lo[i] * c2[i - 1];
                c2[i] = (i < n - 1) ? up[i] / den : 0.0;
                d2[i] = (rh[i] - lo[i] * d2[i - 1]) / den;
            }
            tn[n - 1] = d2[n - 1];
            for (int i = n - 2; i >= 0; i--) tn[i] = d2[i] - c2[i] * tn[i + 1];
            double mx = 0.0;
            for (int i = 0; i < n; i++) { mx = Math.max(mx, Math.abs(tn[i] - t[i])); t[i] = tn[i]; }
            if (mx < 1.0e-6) break;
        }
        int ice = 0;
        for (int i = 0; i < n; i++) if (t[i] < TF_C + 273.15) ice++;
        if (ice >= n / 2) {
            throw new IllegalStateException("ClimlabEBM.solve37: 解落在【雪球支】(" + ice + "/" + n
                + " 个冰格点)。两半球 EBM 仍应是暖支 —— 请检查常数与非对称项是否被改动。");
        }
        cache37 = t;
        return cache37;
    }

    // ================================================================================
    // ★★★★★ 2026-10-15/16（P2-2 机制）：**显式 OHT 通道**（观测锚定的经向海洋热输运）
    //
    // 物理：把观测的经向热输运 F(phi)[W/m] 的【辐合】加进地表收支 ✓
    //         Q_OHT(phi) = -(1/(a cos phi)) * d(F cos phi)/d phi        [W/m2]
    //       出处（430 报告 Q2 ✓）：Aylmer et al. (2024) Comm.Earth Environ. 5:406（式(1)）
    //                              + Frajka-Williams et al. (2019) Front.Mar.Sci. 6:260
    //
    // ★★ 防双重计数（本设计的关键）：**只加【反对称部分】** ✓
    //      理由：EBM 的扩散项 D*nabla^2T **已经**含了【对称】的经向输运 ✗；
    //            它结构上**表示不了**的，正是 OHT 的【反对称（跨赤道）】部分 ✓✓
    //            —— 而那恰好就是 AMOC 的招牌效应 ✓（P2-1 实测：几何本身给不出不对称 ✓）
    //      做法：Q_channel = A * odd(qhat)，qhat 由观测剖面归一化而来 ✓
    //
    // ★ 零新自由参数：振幅 A 由【模型自己的基线输运】标定（"同振幅、换形状" ✓，
    //   见 428 §8.5 ✓）—— 不需要任何新的可调常数 ✓
    //
    // ★ 观测锚：15N 峰值 1.18 PW、26.5N 1.33 PW、RAPID 1.2 PW（430 Q1 ✓）；
    //   **全球海洋峰值 = NOT FOUND，严禁外推** ✓；**南半球值待取证** ✓
    //   ⟹ 未取证前 T_OHT_W_37 保持 **null**，通道无法启用（诚实边界 ✓ 铁律 2 ✓）
    // ================================================================================
    // ★ 2026-10-16 用户裁决「改成真实的物理」+「持续打开」⟹ **A/B 轮：打开** ✓
    //   （剖面 T_OHT_W_37 已按 T2019 官方表填好 ✓；关掉即回纯扩散 EBM ✓）
    public static boolean OHT_CHANNEL_ON = true;
    /** 观测锚定的经向热输运剖面：**跨整条纬圈的总输运 Tcal(phi)**（W；37 节点 -90..+90，5 度）。
     *
     *  <p>★ 为什么用总量而不是「每单位经度」：辐合的**量纲正确**形式是
     *  <pre>  Q(phi) = -(1/(2*pi*R^2*cos phi)) * dTcal/dphi        [W/m2]  </pre>
     *  （面积 = 2*pi*R^2*cos phi*dphi ✓）。用「每单位经度」的写法还要额外带 1/R，
     *  容易漏（本实现第一版就漏了 ✗，靠振幅标定掩盖了 ✓）。
     *
     *  <p>★★ **缩尺口径**（写死，避免错 1600 倍 ✗）：本世界 R_EFF = 159.15 km（地球的 1/40 ✓），
     *  而观测值（1.18 / 1.33 PW）是**地球口径** ⟹ 必须按面积比缩放：
     *  <pre>  Tcal_world = Tcal_earth * (R_EFF / R_earth)^2  </pre>
     *  这样【辐合 W/m2 与地球相同】✓（与模型自身"气温/辐射取地球量级"的设计一致 ✓）。
     *
     *  <p>★ **未取证前保持 null** ⟹ {@link #OHT_CHANNEL_ON} 无法生效 ✓（不许编 ✗）。 */
    public static volatile double[] T_OHT_W_37 = {
        // ★★ 观测锚定的全球海洋经向热输运剖面（**跨整条纬圈的总输运 Tcal**，W；37 节点 -90..+90，5 度）
        // 出处（一手，CC-BY 4.0）：Trenberth, Zhang, Fasullo & Cheng (2019),
        //   J. Climate 32(14):4567-4583, DOI 10.1175/JCLI-D-18-0872.1
        //   官方附表数据 DOI 10.5065/9v3y-fn61 —— Fig. 4a：**全球海洋** MHT (PW)，逐 1 度，
        //   2000-2016 年均循环，**向北为正** ✓
        // 口径：节点值 = ±2.5 度带平均（与锚表同约定 ✓）；本世界按 (R_EFF/R_earth)^2 缩尺 ✓
        // 核验（生成脚本 tools/zonal-asym/emit_oht_profile.py ✓）：
        //   赤道 +0.230 PW（T2019 原表 0.5S/0.5N = +0.13/+0.34 ✓ 自洽）
        //   NH 峰值 **+1.758 PW @ +20 度**；SH 极小 **-1.035 PW @ -15 度**
        //     （独立复核：取证测得 -1.04 @13.5~15.5S ✓✓ 完全吻合）
        //   南极侧 < -76.5 度无数据 ⟹ **按边界条件收敛到 0**（极地输运必须为零 ✓，不是外推观测 ✓）
        //   ★ 分盆记账：Atlantic-Arctic 产品止于 33.5S ⟹ **不做南大西洋分盆**（用全球段 ✓，
        //     并在此写明该段是【全球】口径而非南大西洋口径 ✓）
        // ⚠ 北半球局地锚（26.5N RAPID 1.33±0.40 PW、15N 大西洋峰 1.18 PW）是【大西洋】口径，
        //   本表是【全球】口径 ⟹ 数值天然更大（~1.7 vs ~1.2 PW ✓），**不可直接互比** ✓
-0.000000e+00, -2.311326e+09, -4.622653e+09, -1.248116e+10, -5.512514e+10, -1.445735e+11, -2.569039e+11, -3.245102e+11, -3.151494e+11, -2.766658e+11, -3.484325e+11, -4.836451e+11, -5.481311e+11, -5.897349e+11, -6.230180e+11, -6.459002e+11, -5.959755e+11, -3.702745e+11, 1.435334e+11, 6.386195e+11, 9.048843e+11, 1.069220e+12, 1.097302e+12, 1.052578e+12, 9.693703e+11, 8.362379e+11, 6.250982e+11, 5.013267e+11, 4.826050e+11, 4.358006e+11, 3.421919e+11, 2.517035e+11, 1.944981e+11, 1.154508e+11, 4.680436e+10, 1.144107e+10, 0.000000e+00};
    /** 诊断出口：上一次 {@link #solve37OHT()} 用到的辐合振幅 [W/m2]（= 基线 max|D*nabla^2T| ✓）。 */
    public static volatile double lastOhtAmpWPerM2 = Double.NaN;
    /** 诊断出口：「保总量」标定解出的 D 缩放系数（1 = 未减小 ✓）。 */
    public static volatile double lastOhtDEffScale = Double.NaN;
    private static volatile double dEffScaleCached = Double.NaN;

    /**
     * ★★ **「保总量」标定**：解出 {@code k}，使【总输运（扩散 + 通道）的峰值 = 基线峰值】✓。
     *
     * <p>总输运（跨整条纬圈，W）：由辐合反解 —— 扩散项 {@code D*nabla^2T} 与通道项 {@code qOdd}
     * 用的是**同一个算子**，所以 {@code T = -2*pi*R^2*cos(phi) * (D_eff*nabla^2T + qOdd)} ✓。
     * <p>零新自由参数 ✓：标定条件是**自洽**的（对模型自己的基线 ✓），结果缓存 ✓。
     * <p>若通道本身就比基线弱（k=1 时峰值已不超），直接取 1 ✓（不做无谓的减小 ✓）。
     */
    // ★★ 这里原本有一个「保总量」bisection（解出 k 使总输运峰值回基线）——
    //    **已删除** ✗：实测证明 (a) 基线反对称输运为零 ⟹ 无双重计数 ✓；
    //    (b) 调小 D 会把模型推入雪球支（18/37 冰格点 ✗）；
    //    (c) 通道的 +5.14 K 与观测自己的南北差（+6.17 K @65 度）一致 ✓✓ ⟹ 不需要补偿 ✓。
    //    完整论证见 solve37OHT 的 (d) 段 ✓ 与 待裁决/428。

    /** 总输运（跨整条纬圈，W）的峰值：扩散项 + 通道项 ✓（只用内部点，极点 1/cos 奇异 ⟹ 排除 ✓）。 */
    private static double peakTransport(double[] t, double[] qOddExtra, double dScale) {
        double dphi = Math.toRadians(D_LAT_DEG);
        int n = N37;
        double R = WorldContract.R_EFF;
        double mx = 0.0;
        for (int i = 1; i < n - 1; i++) {
            double phi = Math.toRadians((i - (n - 1) / 2) * D_LAT_DEG);
            double c = Math.max(1.0e-6, Math.cos(phi));
            double cm = Math.cos(phi - dphi / 2.0), cp = Math.cos(phi + dphi / 2.0);
            double coefk = D_DIFF * dScale / (c * dphi * dphi);
            double lap = (coefk * cm) * (t[i - 1] - t[i]) + (coefk * cp) * (t[i + 1] - t[i]);
            double conv = lap + (qOddExtra == null ? 0.0 : qOddExtra[i]);
            double tot = -2.0 * Math.PI * R * R * c * conv;
            mx = Math.max(mx, Math.abs(tot));
        }
        return mx;
    }

    /**
     * 该纬度（弧度）的内生洋面年均温度（K）。
     * <p>插值口径：默认与 {@link ZonalTables#interp5} 一致（**对 |lat| 对称** ✗）；
     * 打开 {@link #TWO_HEMI_ON} 后改走 {@link ZonalTables#interp37}（**带符号** ✓）。
     */
    public static double tOceanK(double latRad) {
        double deg = Math.toDegrees(latRad);
        if (OHT_CHANNEL_ON && T_OHT_W_37 != null) return ZonalTables.interp37(solve37OHT(), deg);
        if (TWO_HEMI_ON) return ZonalTables.interp37(solve37(), deg);
        return ZonalTables.interp5(solve(), deg);
    }

    /** 极赤温差（K）—— 诊断用（观测 39.43 K，旧解 36.19 K）。打开两半球后取【赤道 - 北极】✓。 */
    public static double poleEquatorSpan() {
        if (OHT_CHANNEL_ON && T_OHT_W_37 != null) {
            double[] t = solve37OHT();
            return t[(N37 - 1) / 2] - t[N37 - 1];
        }
        if (TWO_HEMI_ON) {
            double[] t = solve37();
            return t[(N37 - 1) / 2] - t[N37 - 1];
        }
        double[] t = solve();
        return t[0] - t[N - 1];
    }
}
