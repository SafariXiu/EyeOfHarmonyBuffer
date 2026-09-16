package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * 水汽与降水（M3 切片 3）—— 目标 B2（三条降水带）与 B4（雨影）。
 *
 * <h3>设计（M3 调研报告的结论）</h3>
 * <pre>
 *   q_sat(T)  = 0.622*e_s/(p - 0.378*e_s),  e_s = 611.2*exp(17.67*Tc/(Tc+243.5))   Bolton
 *   海面水汽  q = 0.80 * q_sat(SST)                       闭式，不依赖任何跨瓦片状态
 *   陆地水汽  q = 0.80 * q_sat(海平面温度) * exp(-h/H_MOIST)   过山损耗（雨影的来源）
 *   w_eff     = w_zm(phi-delta) + clamp(-H_bl*div(U), +-2e-3)   纬向平均上升支 + 局地辐合
 *   P_mean    = eps_c * rho_a * q * max(0, w_eff) / rho_w        [m/s]
 *   P_eddy    = max(0, MFC) * depl * depl_col / rho_w             [m/s]  ← 风暴轴（B2 中纬带）
 *   P         = P_mean + P_eddy                                   [m/s]
 *   depl      = exp(-h_up/H_MOIST*kappa)      边界层过山损耗（H_MOIST = 2000 m）
 *   depl_col  = exp(-h_up/EDDY_DEPL_H*kappa)  气柱过山损耗（EDDY_DEPL_H = 1000 m = H_BL）
 * </pre>
 *
 * <h3>中纬雨带的来源：瞬变斜压涡动的水汽通量辐合（不再靠 w_zm 的替身）</h3>
 * <pre>
 *   gate   = smoothstep(u_zm/U0)           风暴轴西风门（临界层吸收）
 *   sigma  = 0.31 * f * |dU/dz| / N        Eady 增长率（dU/dz 由热成风从 T_zm 给出）
 *   L_d    = N * H_EFF / f                 Rossby 变形半径
 *   K      = EDDY_MIX * sigma * L_d^2      涡动扩散率
 *   MFC    = gate * K * d2W/dphi2 / R_eff^2   涡动水汽通量辐合（W = 气柱水汽）
 * </pre>
 * 纬度结构与季节迁移**全部**由模型自己的温度场推出，零硬编码形状。
 * 副热带水汽廓线是凹的（d2W/dy2<0）⇒ 涡动辐散 ⇒ 干带自动保住；
 * 中纬是凸的 ⇒ 辐合 ⇒ 雨带。西风门在 |lat| 小于约 30 度恒为 0（零阈值、无分支）。
 * <p>w_zm 因此**回归它的本义**：只代表平均经向环流（Hadley/Ferrel），
 * 不再兼任涡动替身。全工程里 w_zm 的唯一消费者就是本文件，改动面收敛。
 */
public final class PrecipField {

    private PrecipField() {}

    public static final double P_SURF = 101300.0;
    // ⚠ 2026-09-13（审计 D5 同族）：这里原来是**第二份** 1.225 —— 与 Atmosphere.RHO_AIR 是两个独立常量，
    // 改一处不会改另一处。现改为引用**单一来源**（qualified 名，且两者都是 final ⇒ 无漂移可能）。
    public static final double RHO_AIR = Atmosphere.RHO_AIR;
    public static final double RHO_WATER = 1000.0;
    /** 对流效率。 */
    public static double EPS_C = 0.75;
    /** 海面相对湿度（水汽 = 饱和值 x 这个系数）。 */
    public static double RH_SEA = 0.80;
    /** 过山损耗尺度（m）。 */
    public static double H_MOIST = 2000.0;
    /**
     * **ITCZ / 气压带的迁移幅度**（弧度）。真实 ITCZ 只迁移 5~10 度，不是太阳直射点的 23.44 度
     * （第一跑直接用直射点纬度 ⇒ 降水最大值被推到 22 度，错了）。
     */
    public static double ITCZ_MIGRATION = Math.toRadians(10.0);
    /** 取「上风方向」地形的步长（m）：雨影必须用上风地形，不能用本地地形。 */
    public static double UPWIND_STEP = 150_000.0;

    /** 用于降水的「有效直射点纬度」：按 ITCZ_MIGRATION 缩放。 */
    public static double precipSubsolarLat(double theta) {
        double f = Atmosphere.OBLIQUITY == 0 ? 0 : ITCZ_MIGRATION / Atmosphere.OBLIQUITY;
        return Atmosphere.subsolarLat(theta) * f;
    }
    /** 边界层厚度（m），w_loc = -H_bl*div(U) 用。 */
    /**
     * 边界层厚度（m）。**单源**：直接引用 {@link Atmosphere#H_BL}。
     *
     * <p>⚠ <b>2026-09-13 审计 D50</b>：这里原来是**第二个独立定义** {@code = 1000.0}（而且**可变**），
     * 与 {@code Atmosphere.H_BL}（final）是两个常量。今天值相同所以行为一致，但：
     * ① 一个是 final 一个是可变 ⇒ 可以漂移；② **探针全部读 {@code Atmosphere.H_BL}**，
     * 而生产用本字段 ⇒ 只改一个，探针读数就与生产脱钩（D5 家族的典型形态）。
     * 改成引用后**值不变 ⇒ 逐位不变**，同时消掉一个「可变但不在 configStamp 里」的旋钮。
     */
    public static final double H_BL = Atmosphere.H_BL;
    /** w_loc 的截断（m/s）。 */
    public static double W_LOC_MAX = 2.0e-3;

    // ================= 风暴轴：瞬变斜压涡动的**水汽通量辐合** =================

    /** 重力（m/s^2）。 */
    public static final double G_ACC = 9.807;
    /** 定压比热（J/kg/K）。 */
    public static final double CP_AIR = 1004.0;
    /** 干绝热递减率（K/m）= g/cp。 */
    public static final double GAMMA_D = G_ACC / CP_AIR;
    /** Eady 增长率系数（标准值 0.31）。 */
    public static final double EADY_COEF = 0.31;
    /**
     * **涡动混合长与变形半径的平方比** <code>(L_mix/L_d)^2</code>：
     * 涡动扩散率 <code>K = EDDY_MIX * sigma * L_d^2</code>。
     *
     * <p>物理含义：中纬风暴轴里等压面涡动的经向摆幅 L_mix 相对变形半径 L_d 的平方比。
     *
     * <p>⚠⚠ <b>标定锚点（2026-09-13 重标，审计 D43）</b>：
     * 原文这里写的是「观测的 45~55 度纬向平均降水 2.0~2.6 mm/day（<b>P291 实测</b>）」——
     * <b>那个引用是假的</b>：P291 是「目标① 的海盆几何」探针，与降水毫无关系（§135.1）。
     * 现在唯一的锚是**真取来的观测**：GPCP 长期月平均气候态 45~55°N **JJA** 纬向平均
     * = <b>2.565 mm/day</b>（等纬距算术平均，与验收仪器 P296 的 band() 同口径；cos 加权 2.562）。
     * 逐字复现见 `build\eoh_probe\refs\verify_gpcp_anchors.py`（12 个锚全部 ±0.0004 内）。
     */
    // ⚠ 2026-09-13 **重标**（用户裁决 1A「重标 EDDY_MIX 的合格区间」；依据 P457 扫描）：
    // 唯一的标定锚 = **GPCP 长期月平均气候态（气候期 1991-2020）** 45~55°N **JJA** 纬向平均降水
    // ⚠ 版本更正（审计 D59，2026-09-14）：本地文件的 netCDF 全局属性写着
    //   title = "GPCP Version 2.3 Combined Precipitation Dataset (Final)"、version = "V2.3"，
    //   而本注释与设计文档一直写的是 **v2.2**。**数据是 v2.3**，周期 1991-2020 由
    //   climatology_bounds（69761..80688 days since 1800-01-01）证实无误。
    //   12 个锚已从该文件独立复算通过（verify_gpcp_anchors.py）⇒ 数字没错，标签错了。
    //   = **2.565 mm/day**（等纬距算术平均，与验收仪器 P296 的 band() 同口径；cos 加权 2.562）
    //   数据：https://downloads.psl.noaa.gov/Datasets/gpcp/precip.mon.ltm.1991-2020.nc
    // P457 扫描（**已与 P296 的网格逐点对齐** —— 修掉 E12：原来 P457 用 ±6000 km 的 200 点，
    // 与 P296 的 0..15,960 km 400 点不是同一批 ⇒ 同一 EDDY_MIX 下 2.007 vs 1.99）：
    //   1.37 -> 1.507 (-41.3%)   1.70 -> 1.724 (-32.8%)   2.00 -> 1.921 (-25.1%)
    //   **2.11 -> 1.993 (-22.3%)**  2.60 -> 2.316 (-9.7%)   2.80 -> 2.447 (-4.6%)
    //   2.90 -> 2.513 (-2.0%)   **2.95 -> 2.546 (-0.7%)**   3.00 -> 2.579 (+0.5%)
    // ⇒ 取 **2.95**（= 反解 GPCP 观测锚的值）。
    // ⚠ 记账三条（详见设计冻结 §135）：
    //   (1) **D41**：旧的「合格区间 1.37~2.11」在全工程与设计冻结里**找不到任何推导或引文**，
    //       只有反复断言（PrecipField:83 引的「P291 实测」也是错的 —— P291 是海盆几何探针）⇒ 整条作废。
    //   (2) **物理含义无文献支持 —— 已查证（2026-09-13）**：本量写作 (L_mix/L_d)^2，2.95 ⇒ L_mix/L_d = 1.72。
    //       **Caballero & Hanley (2012)**, J. Atmos. Sci. 69, 3422-3435, doi:10.1175/JAS-D-12-035.1（全文已存 refs/）：
    //         低层混合长 **远小于**外部涡动尺度（实测 L ≈ 2500 km、ℓ ≈ 700 km），且 **ℓ ∝ v\***；
    //         随气候变暖混合长减小而涡动尺度增大
    //       ⇒ **不存在可引用的普适 L_mix/L_d 常数。**
    //       （Caballero 2011 AMS 摘要同结论："the mixing length is always much smaller than the size
    //         of the dominant eddies"。）
    //       ⇒ **裁定（用户裁决）：EDDY_MIX 是「对 GPCP 标定出来的乘子」，不是「预测出来的常数」。**
    //       ⇒ 若将来做物理化的涡动闭合：正确形式是 κ = v*·ℓ_e（Caballero & Hanley 式 16），
    //         且因 ℓ_e ∝ v* ⇒ **κ = τ_e·σ²·L_d²**，τ_e = 拉格朗日去相关时间（他们实测 ≈ **0.9 天**）。
    //         注意现行式是 σ¹·L_d²（少一阶 σ、且乘子无量纲），两式**不同构**。
    //   (3) **B2.c 因此从「验收项」降级为「标定检查」**（被拟合的目标不能再当独立验收）；
    //       独立验收改用同一份 GPCP 数据里**没有被拟合**的量（45~55 年均、20~40 干带、60~70 带、JJA/DJF）。
    // ⚠ EDDY_MIX 是**纯乘子**，同时缩放冬夏 ⇒ B2.a 的冬/夏比值与 B2.b 的峰值**位置**严格不变。
    // ⚠⚠ 2026-09-13 **第二次重标**：D48（columnWater 少乘 RHO_AIR）修完之后，
    // 旧值 2.95 会把 45~55 夏推到 **2.983（对 GPCP +16.3%）** ⇒ 2.95 已失效。
    // 重标到同一目标（2.565）得 **2.40**（P463 实测 2.540，−0.99%）。
    // ⚠ 本常量只在 EDDY_CLOSURE = 0 时使用（生产已切到 closure 1）；保留它是为了 A/B 可切换。
    // ⚠⚠ 2026-09-13 **第三次重标**：D47（deformRadius 换 beta 平面）让 45~55N 的 L_d 降 11.4%
    // ⇒ κ 降 1.238 倍 ⇒ 重标到同一目标（2.565）得 **3.00**（P463 实测 2.552，−0.50%）。
    public static double EDDY_MIX = 3.00;
    /** 算 d2W/dphi2 用的纬度步长（度）。 */
    public static final double EDDY_DPHI_DEG = 5.0;
    /**
     * **风暴轴的赤道侧边界：临界层吸收。**
     *
     * <p>Rossby 波不能传入东风带 —— 纬向风 U_zm 的零线（临界纬度）就是风暴轴的赤道侧边界。
     * 门用 **smoothstep** <code>x^2*(3-2x)</code>，<code>x = clamp(u/U0_STORM, 0, 1)</code>：
     * 零阈值、处处连续（x=0 与 x=1 处一阶导都为 0）、**无 if 分支**。
     *
     * <p>U0_STORM = **涡动相速度谱的上界**（观测的中纬涡动相速度 3~5 m/s）：
     * 纬向风超过它以后不再有任何相速度进入临界层 ⇒ 门饱和到 1。
     *
     * <p>这条门还带来一个必要的**鲁棒性**：d2W/dphi2 是二阶导，会放大任何低纬的温度不连续。
     * u_zm 在 |lat| 小于约 25 度为东风 ⇒ 门恒为 0，赤道带与副热带不被污染。
     */
    public static double U0_STORM = 5.0;

    /** 风暴轴的西风门（0~1）。无分支、处处连续。 */
    public static double stormGate(double latRad, double theta) {
        // ⚠ 2026-09-13 修正（审计 D4）：原来写的是
        //     ZonalTables.uZm(deg(latRad) - deg(Atmosphere.DELTA_PHI0) * cos(theta))
        // —— 它用了一个**已被 §83 废除**的季节机制（「整体平移 Delta_phi」），
        // 而 Atmosphere.java:82-88 的 javadoc 还写着「模型已不再使用」⇒ **文档与代码直接矛盾**。
        // §83 之后全模型的季节约定是「U_ZM_JAN/JUL 两表 + 按 cos(theta) 插值」，
        // 本方法现在改用同一个入口 uZm(latDeg, theta)。
        // 保留 uZm（全球含陆 850 hPa）是**物理上正确的**：Rossby 波不能传入**自由对流层**东风带，
        // 门槛该用 850 hPa 的纬向风，而不是洋面 10 m 风（那不是本类其他地方用的 uZmBlend 的用途）。
        double u = ZonalTables.uZm(Math.toDegrees(latRad), theta);
        double x = u / U0_STORM;
        x = x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
        return x * x * (3.0 - 2.0 * x);
    }

    /** 饱和比湿（kg/kg）。T 单位 K。 */
    public static double qSat(double tK) {
        double tc = tK - 273.15;
        double es = 611.2 * Math.exp(17.67 * tc / (tc + 243.5));
        return 0.622 * es / (P_SURF - 0.378 * es);
    }

    /**
     * 比湿（kg/kg）。
     *
     * @param tSfcSl 该点的**海平面**温度（K）—— 陆地要先加回递减率
     * @param elev   海拔（m）
     * @param kappa  大陆度 [0,1]
     */
    public static double moisture(double tSfcSl, double elev, double kappa) {
        return RH_SEA * qSat(tSfcSl) * depletion(elev, kappa);
    }

    /**
     * 过山损耗因子 <code>exp(-h/H_MOIST*kappa)</code> —— **雨影的唯一来源**。
     *
     * <p>h 必须是**上风方向**的地形高度（见 {@link #upwindElev}），不是本地海拔。
     * 抽出来是为了让风暴轴涡动项也用同一个因子（涡动水汽同样被上风的山抽干）。
     */
    public static double depletion(double hUp, double kappa) {
        if (kappa <= 0.0) return 1.0;
        return Math.exp(-Math.max(0.0, hUp) / H_MOIST * kappa);
    }

    /**
     * **上风方向**的地形高度（m）—— 雨影必须用它，不能用本地地形。
     *
     * <p>第一跑直接用本地海拔做损耗 ⇒ 迎风坡也被判成干的（测出来只是「高处雨少」，
     * 不是雨影）。正确的物理是：**空气在来路上已经被沿途的山抽干了**，
     * 所以迎风坡用低处的湿度（雨多），背风坡用山脊的湿度（雨少）。
     */
    public static double upwindElev(int x, int z, long seed, int cell, double u, double v) {
        double sp = Math.hypot(u, v);
        if (sp < 0.1) return 0.0;
        int sx = x - (int) Math.round(u / sp * UPWIND_STEP);
        int sz = z - (int) Math.round(v / sp * UPWIND_STEP);
        return Math.max(0.0, PlateField.elevationWithCell(sx, sz, seed, cell));
    }

    // ---------- 风暴轴的物理量（全部是 Atmosphere / ZonalTables 已有场的纯函数） ----------

    /**
     * 纬向平均斜压性/水汽廓线用的**大陆度**。
     *
     * <p>取自 <code>Atmosphere.KAPPA_MEAN</code>（**= 0.328**，本世界实测的面积加权全球 ⟨κ⟩）⇒ **零新常数**。
     *
     * <p>✅ <b>0.15 -&gt; 0.328 之后（§94.4 实测）</b>：σ(50 度) 冬/夏 从 <b>0.9969 升到 1.0838</b>，
     * 45~65 度平均从 0.9552 升到 1.0248；中纬带 2.26/0.73 -&gt; 2.20/0.77（夏仍在 2.0~2.6）；
     * 赤道带与副热带干带<b>逐位未变</b>；B1／赤道连续性逐位不变。
     * （⚠ 本条原文还写着「`EDDY_MIX` 不需要重标（合格区间 1.37~2.11）」——
     * 那条区间已在 2026-09-13 被**整条作废**（无推导来源，D41），见上方 EDDY_MIX 的记账。）
     *
     * <p>❌ <b>本段原来的「已知局限 / 正解应该是加一张按纬度的大陆度表」整段作废</b>（§90 实测推翻）：
     * <code>PlateField</code> 全链<b>没有任何一处引用纬度</b>，陆海场按构造就是统计均匀且各向同性的；
     * ⟨κ⟩(φ) 实测是<b>平的</b>（19 个纬度全在 0.304~0.341，无一超过 2σ）。
     * 原来那句「那些纬度的陆地占比 62~79%」来自<b>单条 z 线 × 6.7 个板块格</b>的抽样噪声
     * （同纬度换窗口给 0.054）—— <b>不要把抽样噪声当成纬度结构</b>。
     *
     * <p>⚠ 仍<b>未修好</b>的一条（已证与 κ 无关）：B2 的「<b>定纬度带平均</b> 冬 &gt; 夏」。
     * <code>EDDY_MIX</code> 是纯乘子 ⇒ 该比值对它<b>严格不变</b>；真根因是<b>风暴轴的季节迁移</b>
     * （冬季湿辐合极大在 41.2 度、夏季在 51.2~56.3 度），固定带在冬季被掏空。
     * 它<b>与「带峰值 冬&gt;夏」和「带位置随季节移动」两条互斥</b>，真实地球 50N 的 DJF/JJA 也只有 1.06
     * ⇒ 判据已按 §95 改为<b>峰值口径 / 随带迁移口径</b>。
     */
    // ⚠ 2026-09-13 修正（审计 D6）：这里原本是
    //     public static double EDDY_KAPPA = Atmosphere.KAPPA_MEAN;
    // —— 一个**类加载时的静态拷贝**。Atmosphere.KAPPA_MEAN 是**可变** public static，
    // 于是运行时改它（P420 重测 <kappa> 时就会）不会传到降水这一侧，两处 <kappa> 静默漂移。
    // P447 G 段实测：KAPPA_MEAN 0.328 -> 0.500，EDDY_KAPPA 全程 0.328。
    // ⇒ **直接删掉这个拷贝**，用点上直接读 Atmosphere.KAPPA_MEAN（由构造保证一致，不再需要断言）。

    /**
     * **纬向平均的海平面温度**（K）—— 涡动水汽通量辐合用它，不用局地温度。
     *
     * <p>理由：中纬降水的水汽来自风暴轴尺度（数千 km）内的**海面蒸发**，
     * 不是本地地面。用局地温度会把「冬季大陆上空又冷又干」错当成「没有风暴轴」。
     */
    public static double zonalSlTemp(double latRad, double theta) {
        // ⚠ §216.7：改用**本世界自己的**纬向平均海平面温度（κ = ⟨κ⟩ 处的海陆混合），
        //   不再用地球的 T_zm —— 否则风暴轴的水汽源会按「地球在那个纬度有多少陆地」算。
        //
        // ⚠⚠ D79 判读钩子：非 null 时**整条涡动链**（eadyGrowth / deformRadius / staticN /
        //   columnWater / eddyMfc）都改用外部给的纬向剖面。**生产恒为 null ⇒ 逐位不变。**
        //   它存在的唯一理由：回答「把真实 ERA5 月平均剖面直接喂进替身，峰位会不会迁移」——
        //   这是 D79 唯一还没做过的判别实验（§226.4）。进 configStamp（非 null 会改结果，D58）。
        ZonalProfile ov = ZONAL_PROFILE_OVERRIDE;
        if (ov != null) return ov.tempAt(latRad, theta);
        return Atmosphere.zonalMeanSeaLevelK(latRad)
             + Atmosphere.seasonalAnomaly(latRad, Atmosphere.KAPPA_MEAN, theta);
    }

    /** D79 判读用的纬向剖面提供者（**生产恒为 null**）。 */
    public interface ZonalProfile { double tempAt(double latRad, double theta); }

    /**
     * **诊断钩子**：非 null 时 {@link #zonalSlTemp} 直接用它。默认 {@code null}。
     *
     * <p>⚠ 这是一个 {@code public static} **可变**字段（与 {@code Atmosphere.SST_PROVIDER} 同族）——
     * 审计曾把 PlateField 的同类字段记为「契约可被调用方打破」。这里接受它，因为：
     * ① 只有一个消费者（{@link #zonalSlTemp}）；② 默认 null ⇒ 生产路径逐位不变；
     * ③ 它进 {@code SimClimate.configStamp()}，非 null 会让瓦片缓存失效。
     * **不许**在生产接线里给它赋值。
     */
    public static ZonalProfile ZONAL_PROFILE_OVERRIDE = null;

    /**
     * **气柱水汽**（kg/m^2）：纬向平均近地比湿 x 空气密度 x 水汽标高 H_MOIST。
     *
     * <p>⚠ <b>2026-09-13 修正（审计 D48）</b>：原来写的是 <code>moisture(...) * H_MOIST</code>，
     * 但 {@link #moisture} 返回的是**比湿（kg/kg）**，比湿乘长度得到的是 **kg*m/kg**，**不是 kg/m²**
     * —— 少乘了空气密度。量级核对（45N）：修正前 W = 12.9、修正后 **15.75 kg/m²**，
     * 而 ERA5 观测（20 层 1000~300 hPa，梯形积分/g，海洋纬向平均）是 **16.40 kg/m²**
     * ⇒ 修正后吻合到 4%。
     *
     * <p>⚠ 这个因子**线性**传进 {@code eddyMfc} 的 {@code curv}（二阶导），与 {@code EDDY_PHYS_GAIN}
     * 退化（可以互相吸收）⇒ 它不改变任何验收读数的形状，只把标定 gain 从 5.4 降到约 4.4。
     */
    public static double columnWater(double latRad, double theta) {
        return moisture(zonalSlTemp(latRad, theta), 0.0, 0.0) * RHO_AIR * H_MOIST;
    }

    /** **纬向平均静力稳定度** N（1/s）：由标准大气递减率 GAMMA 与干绝热递减率给出。 */
    public static double staticN(double latRad, double theta) {
        double t = zonalSlTemp(latRad, theta);
        return Math.sqrt(Math.max(1e-8, (G_ACC / t) * (GAMMA_D - Atmosphere.GAMMA)));
    }

    /**
     * **Rossby 变形半径** <code>L_d = N*H/f</code>（m）。
     *
     * <p>H 用 Atmosphere 已有的对流层有效厚度 H_EFF（12,000 m）。
     * f -> 0 时封顶（赤道附近本来就没有斜压涡动，且那里 gate = 0）。
     */
    public static double deformRadius(double latRad, double theta) {
        // ⚠ 2026-09-13 修正（审计 D47）：原来写的是 N*H/f 并把 f 地板在 1.0e-5，
        // 赤道处给出 **L_d = 12,421 km** —— 真实赤道变形半径 1,500~2,500 km，大了 5~8 倍。
        // （1e-5 对应纬度 5.5 度，不是「赤道附近」的合理截断。）
        // 它一直没暴露是因为 stormGate 在 |lat| < 25 度恒为 0；但任何「放宽 gate 去修副热带/赤道」
        // 的尝试第一步就会踩到它。
        // 现在换成**赤道 beta 平面**的标准形式（Gill 1982 §11），零新参数、无阈值、C-infinity：
        //   c = N*H_EFF（第一斜压模重力波速）; beta = 2*Omega/R_EFF; L_d = c / sqrt(f^2 + beta*c)
        // 两个端点自动正确：f->0 时 L_d -> sqrt(c/beta)（赤道 Rossby 半径 ≈ 2329 km）；
        // |f| 大时 L_d -> c/|f|（与旧式同）。
        double c = staticN(latRad, theta) * Atmosphere.H_EFF;
        double f = WorldContract.coriolis(latRad);
        double beta = 2.0 * WorldContract.OMEGA / WorldContract.R_EFF;
        return c / Math.sqrt(f * f + beta * c);
    }

    /**
     * **Eady 斜压增长率** <code>sigma = 0.31 * f * |dU/dz| / N</code>（1/s）。
     *
     * <p>dU/dz 由**热成风**从模型自己的纬向平均温度场给出：
     * <code>f * dU/dz = -(g/T) * dT/dy</code>。**零新参数、零硬编码纬度形状。**
     */
    public static double eadyGrowth(double latRad, double theta) {
        double d = Math.toRadians(EDDY_DPHI_DEG);
        double t = zonalSlTemp(latRad, theta);
        double tN = zonalSlTemp(latRad + d, theta);
        double tS = zonalSlTemp(latRad - d, theta);
        double dTdy = (tN - tS) / (2.0 * d * WorldContract.R_EFF);
        double f = WorldContract.coriolis(latRad);
        if (Math.abs(f) < 1.0e-8) return 0.0;
        double dudz = -(G_ACC / (f * t)) * dTdy;
        return EADY_COEF * Math.abs(f) * Math.abs(dudz) / staticN(latRad, theta);
    }

    /**
     * **瞬变斜压涡动的水汽通量辐合**（kg/(m^2 s)，正 = 辐合/降水）。
     *
     * <pre>
     *   MFC = gate * K * d2W/dphi2 / R_eff^2 ,  K = EDDY_MIX * sigma * L_d^2
     * </pre>
     *
     * <p>这是本参数化的**全部形状来源**：纬度结构与季节迁移完全由模型自己的
     * 温度场（tZonalMean + seasonalAnomaly）经「热成风 -> sigma」和「水汽经向曲率」
     * 两步推出。**没有任何高斯峰、没有阈值切换。**
     *
     * <p>两重保护让副热带保持干：① u_zm 在 |lat| 小于约 25 度为东风 ⇒ stormGate = 0；
     * ② 副热带（20~40 度）水汽廓线是**凹**的（d2W/dy2 &lt; 0）⇒ 涡动辐散。
     * 中纬（45~75 度）是**凸**的 ⇒ 辐合（湿）。零交叉纬度随季节南北移动。
     */
    /**
     * **涡动扩散率的闭合版本。**
     *
     * <pre>
     *   0 = 现行：K = EDDY_MIX * sigma^1 * L_d^2                 （无量纲乘子，**没有物理依据**）
     *   1 = 物理化：K = EDDY_PHYS_GAIN * EDDY_TAU * sigma^2 * L_d^2
     * </pre>
     *
     * <p><b>为什么 1 才是对的</b>（Caballero &amp; Hanley 2012, JAS 69, 3422-3435,
     * doi:10.1175/JAS-D-12-035.1，全文见 build/eoh_probe/refs/CaballeroHanley2012.pdf）：
     * 式 (16) 把扩散率写成 <code>kappa = v* * l_e</code>（涡动速度 x 有效混合长），
     * 式 (20) 给出 <code>l_L = v* * tau_L</code>（tau_L = 拉格朗日速度自相关积分时间），
     * 核心发现是 <b><code>l_e ∝ v*</code></b>（§5 末）。
     * ⇒ <code>kappa = tau_e * v*^2</code>；再用 Eady 速度尺度 <code>v* ~ sigma * L_d</code>
     * ⇒ <b><code>kappa = tau_e * sigma^2 * L_d^2</code></b>。
     *
     * <p>现行式是 <code>sigma^1 * L_d^2</code> 乘一个**无量纲**的数 —— **两式不同构**：
     * sigma 的幂次不同 ⇒ 纬度结构与季节迁移都会变 ⇒ 这不是「换个常数」而是**换标度**。
     *
     * <p><b>参数</b>：<code>EDDY_TAU = 0.9 天</code>（CH12 §6 实测的拉格朗日去相关时间，
     * 与 Swanson &amp; Pierrehumbert 1997 的太平洋风暴轴同量级）；
     * <code>EDDY_PHYS_GAIN</code> 是唯一的 O(1) 无量纲系数，由 GPCP 观测锚标定。
     *
     * <p>⚠ CH12 同时证明「混合长远小于外部涡动尺度」（L ≈ 2500 km、l ≈ 700 km）
     * ⇒ **把 L_d 直接当混合长是错的**；本式通过 <code>l_e = tau_e * v*</code> 回避了这一点。
     *
     * <p><b>2026-09-13 落地（用户裁决「做涡动闭合的大改」）</b>：<code>EDDY_CLOSURE = 1</code>、
     * <code>EDDY_PHYS_GAIN = 4.40</code>（D48 修完后的重标值）。标定与判定见设计冻结 §146~§150（P463）：</p>
     * <pre>
     *   两个闭合**各自标定到同一目标**（45~55 夏 = GPCP 2.565）后的对照
     *   （D47 修复之后的最新一次标定）：
     *   closure  参数            45-55夏        47-62夏   47-62冬   独立 err
     *   0 现行   EDDY_MIX=3.00   2.552 (-0.50%)  2.081     1.953     0.3530
     *   1 物理化 gain=5.45       2.562 (-0.11%)  2.162     2.158     0.3387
     *   ⇒ 物理化**胜 4.1%**（独立 err 0.3530 -> 0.3387）
     * </pre>
     * <p>⚠⚠ <b>必须记账的两条</b>：
     * <ol>
     *   <li><b>赤道带与副热带逐位不变</b>（6.563 / 1.913 / 0.668）—— 那里 <code>stormGate = 0</code>，
     *       涡动项根本不作用。⇒ 这次大改**修不了**副热带 3.4 倍过干，也修不了赤道冬季。
     *       （这与 Schneider et al. 2006 的结论一致：副热带的涡动辐散是小项。）</li>
     *   <li><b>gain = 4.40 不是 O(1)</b>：取「纯物理」的 gain = 1.0 时 45~55 夏只有 0.969（对 GPCP 偏干 62%）。
     *       ⇒ **模型需要约 5 倍的涡动扩散率才能对上观测**（P464 逐纬度量出所需比值 = 5.213 夏 / 4.848 冬）。
     *       **§149 已经把 σ、L_d、curv、τ_e 四项逐一排除**（前三项实测都在真实范围内，第四项是文献值），
     *       ⇒ **剩下的 5 倍归因于「扩散型闭合本身低估了风暴轴的水汽输送」，记为一条未解释的结构比。**
     *       本式的**形式**现在有物理依据了（σ² 而非 σ¹），**量级仍然是标定出来的**。</li>
     * </ol>
     */
    public static int EDDY_CLOSURE = 1;
    /** 拉格朗日去相关时间（s）：Caballero &amp; Hanley (2012) §6 实测 ≈ 0.9 天。 */
    public static double EDDY_TAU = 77_760.0;
    /** 物理化闭合里唯一的 O(1) 无量纲系数（由 GPCP 标定；见设计冻结 §146）。 */
    public static double EDDY_PHYS_GAIN = 5.45;

    public static double eddyMfc(double latRad, double theta) {
        double d = Math.toRadians(EDDY_DPHI_DEG);
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double w0 = columnWater(c, theta);
        if (w0 <= 0.0) return 0.0;
        double wp = columnWater(c + d, theta);
        double wm = columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
        double ld = deformRadius(c, theta);
        double sg = eadyGrowth(c, theta);
        double K = (EDDY_CLOSURE == 0)
            ? EDDY_MIX * sg * ld * ld
            : EDDY_PHYS_GAIN * EDDY_TAU * sg * sg * ld * ld;
        return K * curv * stormGate(latRad, theta);
    }

    /**
     * **气柱**过山损耗的尺度（m）—— 涡动水汽通量辐合用的那一层。
     *
     * <p>涡动项作用在**整个气柱**的水汽上。地形降水对气柱的抽取比对近地面层更彻底
     * （近地面那个 exp(-h/H_MOIST) 只描述边界层的损耗），所以气柱的损耗尺度更短。
     * 取 1000 m = 模型已有的边界层厚度 H_BL。
     * <p>EDDY_DEPL_H = H_MOIST（2000 m）即退回「涡动项与平均项同样损耗」的对照。
     */
    public static double EDDY_DEPL_H = 1000.0;

    /** 气柱的过山损耗因子（额外的、只作用于涡动项的那一层）。 */
    public static double depletionCol(double hUp, double kappa) {
        if (kappa <= 0.0) return 1.0;
        return Math.exp(-Math.max(0.0, hUp) / EDDY_DEPL_H * kappa);
    }

    /** 把涡动水汽通量辐合折成**与 w_zm 可比**的等效上升速度（m/s），只为诊断/对照用。 */
    public static double eddyWEquivalent(double latRad, double theta) {
        double q = moisture(zonalSlTemp(latRad, theta), 0.0, 0.0);
        double m = eddyMfc(latRad, theta);
        return m > 0.0 && q > 1e-9 ? m / (RHO_AIR * q) : 0.0;
    }

    /** 有效上升速度（m/s）：纬向平均上升支 + 局地辐合。 */
    public static double wEff(double latRad, double theta, double divU) {
        double shifted = Math.toDegrees(latRad - precipSubsolarLat(theta));
        // ⚠ 2026-09-13 修正（审计 D39，用户裁决）：原来是**硬截断**
        //     double wLoc = -H_BL * divU;  if (wLoc > W_LOC_MAX) ...;  if (wLoc < -W_LOC_MAX) ...;
        // 后果（P446 实测）：|divU| 的阈值 2.0e-6 而模型自己的 divU 赤道 ~1e-5、中纬 3~4e-6
        // ⇒ wLoc 在全世界 **60~89%** 的采样点上被钉死，那一项**不携带空间信息**，
        // wEff 退化成「纬度函数 + 常数」—— 而那正是这项设计想避免的。
        // ⇒ 换成**有界归一化**：W_LOC_MAX 从「会被打满的截断」变成「渐近幅值」。
        // 恒等关系让配方无需新参数：DIV0*H_BL = 2e-6 * 1000 = 2e-3 m/s **恰好等于 W_LOC_MAX**。
        // 性质：|tanh| < 1 ⇒ 构造上不可能饱和；处处 C∞；|divU| < DIV0 时近似线性（保留结构）。
        // ⚠ 代价：不是纯粹解封 —— 全值域都被轻微压缩（wLoc = W_LOC_MAX/2 处 −7.6%）
        //   ⇒ B2/B3/B4 会变，需全量重跑。
        double wLoc = W_LOC_MAX * Math.tanh((-H_BL * divU) / W_LOC_MAX);
        return ZonalTables.wZm(shifted) + wLoc;
    }

    /** 降水率（m/s）。 */
    public static double precip(double q, double wEff) {
        if (wEff <= 0) return 0.0;
        return EPS_C * RHO_AIR * q * wEff / RHO_WATER;
    }

    /** 世界坐标上的降水（mm/day）。O(1)（需要 4 次风场取样算散度）。 */
    public static double mmPerDay(int x, int z, long seed, int cell, double theta, int gradStep) {
        return mmPerDay(x, z, seed, cell, theta, gradStep, true);
    }

    /**
     * 世界坐标上的降水（mm/day）。
     *
     * @param withEddy false = 关掉风暴轴涡动项（**只用于受控 A/B 对照**，正常路径恒为 true）
     */
    public static double mmPerDay(int x, int z, long seed, int cell, double theta, int gradStep, boolean withEddy) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, seed, cell);
        double elev = PlateField.elevationWithCell(x, z, seed, cell);
        // ⚠ §216.7 口径统一：这里要的是「海平面等效年均温度 + 季节项 + SST'」，
        //   与 Atmosphere.surfaceTemp **同源**（surfaceTemp 只多一个 -Γ*h*k）。
        double tSl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z))
                   + Atmosphere.seasonalAnomaly(lat, k, theta);
        double[] u0 = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double hUp = upwindElev(x, z, seed, cell, u0[0], u0[1]);
        double depl = depletion(hUp, k);
        double q = moisture(tSl, k > 0.0 ? hUp : 0.0, k);
        double[] ux = Atmosphere.windAt(x + gradStep, z, seed, cell, theta, gradStep);
        double[] uw = Atmosphere.windAt(x - gradStep, z, seed, cell, theta, gradStep);
        double[] un = Atmosphere.windAt(x, z + gradStep, seed, cell, theta, gradStep);
        double[] us = Atmosphere.windAt(x, z - gradStep, seed, cell, theta, gradStep);
        double divU = (ux[0] - uw[0]) / (2.0 * gradStep) + (un[1] - us[1]) / (2.0 * gradStep);
        // 平均经向环流 + 局地辐合（原式，未改动）
        double p = precip(q, wEff(lat, theta, divU));
        // 风暴轴：瞬变斜压涡动的水汽通量辐合。
        // 只取辐合侧（mfc>0）：辐散侧（副热带）的「变干」已经由 w_zm 的下沉支表达，
        // 再减一次就是重复计数。
        if (withEddy) {
            double mfc = eddyMfc(lat, theta);
            if (mfc > 0.0) p += mfc * depl * depletionCol(hUp, k) / RHO_WATER;
        }
        return p * 86400.0 * 1000.0;
    }
}
