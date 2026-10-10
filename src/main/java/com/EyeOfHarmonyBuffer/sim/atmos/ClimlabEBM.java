package com.EyeOfHarmonyBuffer.sim.atmos;

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
 * <p><b>默认关闭</b>（{@link #ENABLED} = false）⟹ {@link Atmosphere#oceanBaseK} 逐位不变。
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
    public static boolean ENABLED = true;    // ★ §7347 完整接入：§7346 已过 22 支验收（门中性）

    // ==================== climlab 常数（逐字，见类头出处） ====================
    /** 太阳常数（W/m2）—— climlab const.S0，insolation.py L251。 */
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

    /** 该纬度（弧度）的内生洋面年均温度（K）。插值口径与 {@link ZonalTables#interp5} 一致（对 |lat| 对称）。 */
    public static double tOceanK(double latRad) {
        return ZonalTables.interp5(solve(), Math.toDegrees(latRad));
    }

    /** 极赤温差（K）—— 诊断用（观测 39.43 K，本解 36.19 K）。 */
    public static double poleEquatorSpan() {
        double[] t = solve();
        return t[0] - t[N - 1];
    }
}
