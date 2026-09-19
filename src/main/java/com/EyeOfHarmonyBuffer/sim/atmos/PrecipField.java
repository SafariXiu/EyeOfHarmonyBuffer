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
 *   K      = sigma * L_d^2 / EADY_COEF     涡动扩散率（Green 1970 / Stone 1972，EDDY_MIX = 1/EADY_COEF）
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
    /**
     * 诊断读数（**只给探针用，生产不读**）。线程隔离，避免多线程下互相踩。
     * [0] 原始 divU（只来自 cellPressure）  [1] 叠加进来的 div(V)  [2] 叠加后的 divU  [3] wEff
     * [4] wBase（纬向平均上升支：默认取 ZonalTables.wZm；HadleyCell.ENABLED 时取求解值）
     * [5] q 比湿  [6] tSl 海平面等效温度  [7] kappa  [8] precip 主项(m/s)
     * [13] beta（土壤湿度桶给出的表面湿润度；未启用时恒 1.0）
     * [14] wDiag = -(H_EFF/pi)*(divU + div(V)) —— **真正的诊断垂直速度**（m/s），
     *      由散度直接算，不经 `EPS_C`/`W_LOC_MAX` 那一链 ⇒ 这才是能与 ω500 对比的量。
     * [9] 加完涡动后的 p(m/s)  [10] 浅对流地板(m/s)  [11] depl  [12] 地表温度(K)
     * —— 这一组是「为什么这个点这么湿/干」的分解，用于 §402 的撒哈拉恒湿归因。
     * 为什么要它：Q_SCALE 的标定目标是「div(V) 的 RMS 与模型的 divU 同量级」，
     * 而 divU 是 mmPerDay 的局部变量、外部拿不到 —— 没有它就只能靠猜量级。
     */
    public static final ThreadLocal<double[]> DIAG = ThreadLocal.withInitial(() -> new double[16]);   // §462: [15]=V

    /**
     * ★ S3 土壤湿度桶的注入点（§405）：非 null 时 {@link #moisture} 用
     * {@code RH_eff = RH_SEA*beta + RH_DRY*(1-beta)} 取代全球常数 {@code RH_SEA}。
     * <b>null（默认）⇒ 逐位不变。</b>
     * 用 ThreadLocal 而不是参数：{@code moisture()} 在模型里被多处调用，加参数会波及一大片；
     * 这与本仓已有的 {@code Atmosphere.SST_SUPPRESS} / {@code StationaryWave.IN_SOLVE} 是同一套机制。
     */
    public static final ThreadLocal<Double> BETA_OVERRIDE = new ThreadLocal<>();
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
    // ⚠⚠ §539（P2-19 续）：以下关于「重标 EDDY_MIX 的合格区间」的内容【已被 §429 取代】。
    //   EDDY_MIX 现为推导值（PrecipField.java:169 = 1/EADY_COEF = 3.2258），【零自由度】，
    //   由模型自己的 EADY_COEF 导出（:165-167）。本节保留作历史（P457 扫描读数仍有价值），
    //   但【不得据它认为 EDDY_MIX 可调】。
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
    //       ⚠⚠ §539（P2-19 续）：下面这条裁定【已被 §429 取代】—— EDDY_MIX 现为
    //          PrecipField.java:169 的 1/EADY_COEF = 3.2258，由模型自己的 EADY_COEF 导出、
    //          【零自由度】，【不是】对 GPCP 标定出来的乘子。保留原文仅供追溯。
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
    // ⚠ 本常量只在 EDDY_CLOSURE = 0 时使用（§429 起生产就是 closure 0）；保留 closure 1 是为了 A/B 可切换。
    // ⚠⚠ 2026-09-13 **第三次重标**：D47（deformRadius 换 beta 平面）让 45~55N 的 L_d 降 11.4%
    // ⇒ κ 降 1.238 倍 ⇒ 重标到同一目标（2.565）得 **3.00**（P463 实测 2.552，−0.50%）。
    // ⚠⚠ **§429 定案（用户裁决：按最标准的做，不妥协不捏造）**：
    //   本常量【不再是对 GPCP 标定出来的乘子】，而是**标准闭合式本身**。
    //   Green (1970) / Stone (1972)：D = L_d^2 * (f/N) * (dU/dz)，
    //   而 eadyGrowth 里 sigma_Eady = EADY_COEF * (f/N) * |dU/dz|
    //   ⇒ **D = sigma_Eady * L_d^2 / EADY_COEF** ⇒ (L_mix/L_d)^2 = 1/EADY_COEF = 3.2258。
    //   **完全由模型自己的 EADY_COEF 导出，零自由度。**
    //   §146~§150 的标定值 3.00 与它只差 7%，但来源不同（拟合 vs 推导）。见 §429。
    public static double EDDY_MIX = 1.0 / EADY_COEF;
    /** 算 d2W/dphi2 用的纬度步长（度）。 */
    public static final double EDDY_DPHI_DEG = 5.0;
    /**
     * ★★★★★ **§494：求导步长的【可扫描】覆盖**（`<= 0` 时用 `EDDY_DPHI_DEG`）。
     *
     * <p><b>为什么要它</b>：`EDDY_DPHI_DEG` 是 `final`，所以这个步长**从来没有被 A/B 过**。
     * 而 §485 量到 `curv(W) = Δ²W/dy²` 是**相消主导**（`Δ²W/W ~ 0.01%~1%`）⇒ 模板宽度应当决定符号。
     * 更早的 P632（见第 1343 行注释）已经指出：**`EDDY_DPHI_DEG = 5` 恰好落在表的节点上**
     * ⇒ `curv(W)` 拾取的全是**折角伪影**。这个旋钮就是为了把那一句变成可测的 A/B。
     */
    public static double EDDY_DPHI_DEG_V = -1.0;

    /** 实际使用的求导步长（度）。 */
    public static double dphiDeg() { return EDDY_DPHI_DEG_V > 0.0 ? EDDY_DPHI_DEG_V : EDDY_DPHI_DEG; }
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
        // ★ §405：beta 为 null 时 rh == RH_SEA，与接线前【逐位相同】（同一操作数顺序）。
        Double bo = BETA_OVERRIDE.get();
        double rh = (bo == null) ? RH_SEA : SoilMoisture.rhEff(bo);
        return rh * qSat(tSfcSl) * depletion(elev, kappa);
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

    // ==================== §444 水汽源：陆地水汽来自【上游海面】 ====================

    /**
     * ★★★ **§444：陆地水汽是否改用【上游海面】作为源**（默认 **false**）。
     *
     * <p>现状 `q = RH*qSat(tSfcSl)*depletion(hUp,kappa)`：**纯局地**，没有水汽源，
     * 唯一的经向因子是 `depletion`（上风地形）—— 而它让**季风区更干**（§438 实测）。
     *
     * <p>P656 的 go/no-go（沿风逆推到海、读上游海温）：
     * <pre>
     *   JJA 亚洲   fetch  975 km  上游 SST 297.48 K  => q源 0.015165
     *   JJA 撒哈拉 fetch 1906 km  上游 SST 293.86 K  => q源 0.012297
     *   => q源*exp(-fetch/L) 之比：L=1000km **3.1x**、2000km 1.96x、3000km 1.68x  【方向对】
     *   ⚠ DJF 反号（亚洲 fetch 3208 km，冬季风把来向推回内陆）—— 已记账。
     * </pre>
     *
     * <p><b>为什么必须与 {@code StationaryWave.WVLW_K} 配对</b>（§443）：
     * 水汽长波吸收是文献里的**放大器**（占降水变化的 65%），但单独打开它只会**放大错误**
     * （实测 Qnet 对比 −14.95 → −27.90），因为它的输入（水汽场）是反号的。
     * 水汽源负责把对比先摆正，辐射反馈负责放大。**两项必须同时开。**
     */
    public static boolean Q_FROM_SOURCE = false;

    /**
     * ★★★ **§472：边界层水汽改走【稳态收支口径】。** 默认 **false** ⇒ 逐位不变。
     *
     * <p>`E = P + V`（H_BL = 1000 m 的板），三项全部是模型已有的量：
     * <pre>
     *   E = 86400*chv*(qSat(Ts)*beta - q)        表面蒸发
     *   P = k_P*q                                (k_P = P 对 q 的线性系数)
     *   V = 86400*rho*|w_BL|*(q - q_FT)          与自由对流层的交换
     *   q_FT = q*exp(-(H_EFF/2)/H_MOIST)         模型自己的损耗律
     * </pre>
     * `q_FT ∝ q` ⇒ **闭式解**（见 {@code mmPerDay}）：`q* = 86400*chv*qSat(Ts)*beta / (86400*chv + k_P + C*(1-qftF))`。
     * **无新常数、零迭代、零递归。**
     *
     * <p><b>P678 实测（§471）</b>：现状下收支 q 与模型现在 q 同量级（0.01138 vs 0.01367、0.00918 vs 0.01300）；
     * 而 `M` 由「§448 下两盒都为负（−5.656e7 / −1.376e8）」变成**两盒都正（+9.994e7 / +2.452e7）**。
     */
    public static boolean Q_FROM_BLBUDGET = false;

    /** 水汽从海岸向内陆的 e 折输送尺度（m）。P656 扫的三个值：1e6 / 2e6 / 3e6。 */
    public static double SOURCE_FETCH_L = 1.5e6;
    /** 逆推的最大距离（m）；超过则回落到原式。 */
    public static double SOURCE_FETCH_MAX = 4.0e6;

    /**
     * ★★★ **§448：水汽源温度要不要带季节项。** 默认 **false**（逐位不变）。
     *
     * <p><b>这是一个口径不一致，不是口味问题。</b>本文件 {@code mmPerDay} 在算 `tSl` 时
     * 写得很清楚（§216.7）：
     * <pre>
     *   tSl = annualSeaLevelTemp(lat, k, sstAnom) + seasonalAnomaly(lat, k, theta)
     *   // 注释原文：「这里要的是『海平面等效年均温度 + 季节项 + SST'』，与 surfaceTemp 同源」
     * </pre>
     * 而 {@link #upwindSea} 返回的**海面**温度只取了前半截：
     * <pre>
     *   annualSeaLevelTemp(lat, 0.0, sstAnom(xi,zi,theta))   // ← 少了 + seasonalAnomaly
     * </pre>
     * ⇒ **内陆点**（`kappa > 0.5`）的水汽源温度是**年平**的，而**海洋点**走
     * `moisture(tQ,...)`，`tQ` 是带季节项的 `tSl` ⇒ 同一张图上海、陆两侧的季节口径不同。
     *
     * <p><b>实测代价</b>（P661 + COBE-SST2 1991-2020 月气候）：19.3N 处本世界自己的
     * 季节项 JJA = **+1.0234 K**，而观测孟加拉湾盒 JJA − 年 = **+1.13 K**、南海 **+1.30 K**
     * ⇒ 模型**已经有**正确的纬向季节循环，只是**没接到季风水汽源上**；
     * 而 §447 的逐相位 curl 只给出 +0.10 K 的**经向**结构 ⇒ 缺的那 1 K 主要在这里。
     *
     * <p>⚠ 只作用于 {@link #Q_FROM_SOURCE}=true 的那条支路 ⇒ 默认路径不可达。
     */
    public static boolean SOURCE_SEASONAL_T = false;

    /**
     * **沿风逆推到海面**：返回 `{fetch(m), SST(K)}`；找不到海返回 `{-1, NaN}`。
     * 与 {@link #upwindElev} 同一套做法，只是多走几步直到海面。
     */
    public static double[] upwindSea(int x0, int z0, long seed, int cell, double theta, int gradStep,
                                     double u0, double v0) {
        double x = x0, z = z0, u = u0, v = v0;
        for (double d = 0.0; d < SOURCE_FETCH_MAX; d += UPWIND_STEP) {
            double sp = Math.hypot(u, v);
            if (sp < 0.1) return new double[]{-1.0, Double.NaN};
            x -= u / sp * UPWIND_STEP;
            z -= v / sp * UPWIND_STEP;
            // ★ §527 修复（审计面 1）：原来是 clamp(z, 0, MAX_D) —— 那只保留【北半球】：
            //   南半球点（z 约 3*MAX_D）会被夹到 MAX_D = 【北极】，而不是回到它自己的纬度。
            //   契约下气候在 Z 上以 Z_CYCLE 周期重复 ⇒ 只能【回卷】不能【夹逼】。
            //   回卷对北半球点 z 不变 ⇒ 两个盒子（15~35N / 20~35N）的读数与 P293 均不受影响。
            z = WorldContract.wrapZ(z, WorldContract.Z_CYCLE);
            int xi = (int) Math.round(x), zi = (int) Math.round(z);
            if (!PlateField.isLandWithCell(xi, zi, seed, cell)) {
                double lat = WorldContract.latOf(zi);
                double tSea = Atmosphere.annualSeaLevelTemp(lat, 0.0, Atmosphere.sstAnom(xi, zi, theta));
                // §448：源温度的季节项。false 时**不执行加法**（不做 +0.0 的形式改写）。
                if (SOURCE_SEASONAL_T) tSea += Atmosphere.seasonalAnomaly(lat, 0.0, theta);
                return new double[]{d, tSea};
            }
            double[] uv = Atmosphere.windAt(xi, zi, seed, cell, theta, gradStep);
            u = uv[0]; v = uv[1];
        }
        return new double[]{-1.0, Double.NaN};
    }

    /**
     * ★★★★★★ **§480：`wEff` 的纬向平均项换成 `+H_bl·F_net/M`（Neelin 的 `∇·v₁ = −F_net/M`）。**
     * 默认 **false** ⇒ 逐位不变。
     *
     * <p><b>为什么必须换</b>（§479 的代码级体检）：`cellPressure = CELL_GAIN*carrier(φ−mig)*tropicGate*(KAPPA_MEAN−κ)`
     * 里的 `(KAPPA_MEAN−κ)` 是**静态**的（海正陆负）⇒ **符号全年由「海还是陆」决定**，
     * 季节只能把 `carrier` 剖面推 8 度 ⇒ **撒哈拉两季都拿到热低压 ⇒ 永远上升**
     * （§478 实测：JJA `divU = −2.50e−06`、`wEff = +1.31e−03`）。
     *
     * <p><b>驱动量必须同时满足三条</b>：① 能随季节翻号；② 能区分沙漠与季风区（同纬度同季节）；
     * ③ 除以毛湿稳定度 `M` 才有 `w` 的量纲。**唯一同时满足的是 `F_net/M`。**
     *
     * <p>⚠ **必须与 {@link #Q_FROM_BLBUDGET} 配对**：`M` 的符号由 `q_BL` 决定（§470），
     * 只有 §472 的收支口径能让它在两盒都为正（§471：+9.994e7 / +2.452e7）。
     * 与 §444/§456 同型：**一个开关的物理前提由另一个开关提供。**
     *
     * <p>⚠ **一次前向代入，不迭代**（§464/§466 纪律）：`F_net` 的 `qLat` 用【旧闭合】的 `w` 估出的 `P`。
     */
    public static boolean WZM_FROM_QNET = false;

    /** 诊断：`M <= 0` 而回落到旧闭合的次数（正常应为 0；非 0 说明没配对）。 */
    public static long qnetNegM = 0;
    /** 诊断：`M <= 0` 的【5 度纬度带 × 海陆】分布 —— 用来判「不成立」集中在哪。 */
    public static final long[] qnetNegMBandLand = new long[18];
    public static final long[] qnetNegMBandSea = new long[18];
    /** 诊断：最近一次的 M 与 F_net（单值，仅供探针；多线程下不可靠）。 */
    public static volatile double qnetLastM = 0.0, qnetLastFnet = 0.0;
    /** 诊断：本支路被调用的次数。 */
    public static long qnetCalls = 0;
    /**
     * ★★★ **§482：`M` 的两层口径里【自由对流层所在的高度】占 `H_EFF` 的比例。** 默认 0.5（= 6000 m）。
     *
     * <p>为什么它是一个旋钮而不是常数：两层口径下 `Δh = cp·(zFT·Γ) − g·zFT + L·Δq`，
     * 那个 `−19704 J/kg` 的干层结项**完全由 `zFT` 决定**（`zFT = 6000` 时 `g·zFT − cp·Γ·zFT = +19704`）。
     * 而 `M > 0` 的阈值 `q_BL > 0.00846`（§481）正是这个数除以 `L`。
     * **⇒ 两层口径下 `M` 的符号由一个建模选择控制 ⇒ 它不是一个良定义的物理量。**
     * 这个旋钮就是为了**量出**这件事（§482），不是为了调参。
     */
    public static double M_FT_FRAC = 0.5;

    /**
     * §480：`+H_bl*F_net/M`。`F_net` 与 `M` 全部用模型已有的量；**只做一次前向代入**。
     */
    public static double wEffQnet(double latRad, double theta, double divU,
                                  double q, double tQ, double depl, double k, double beta,
                                  double chv, double absSolar) {
        qnetCalls++;
        double wOld = wEff(latRad, theta, divU);
        // ---- F_net = qRad + qSens + qLat（qLat 用旧闭合的 w 估出的 P ⇒ 一次前向代入）----
        double cwv = q * RHO_AIR * H_MOIST;
        double ts = Radiation.skinTempLand(absSolar, tQ, RH_SEA * qSat(tQ) * depl, chv, beta);
        double qRad = absSolar - StationaryWave.olrClear(ts, cwv);
        double qSens = chv * Radiation.CP * (ts - tQ);
        double pEst = precip(q, wOld) * 86400.0 * 1000.0;          // mm/day
        double qLat = Radiation.LV * pEst / 86400.0;               // W/m^2
        double fNet = qRad + qSens + qLat;
        // ---- M = rho*H_EFF*(h_BL - h_FT)，h = cp*T + g*z + L*q ----
        double zFT = M_FT_FRAC * Atmosphere.H_EFF;
        double qftF = Math.exp(-zFT / H_MOIST);
        double dh = Radiation.CP * (Atmosphere.GAMMA * zFT)
                  - G_ACC * zFT
                  + Radiation.LV * q * (1.0 - qftF);
        double M = Atmosphere.RHO_AIR * Atmosphere.H_EFF * dh;
        qnetLastM = M; qnetLastFnet = fNet;
        if (M <= 1.0e-6) {
            qnetNegM++;
            int bd = (int) (Math.toDegrees(Math.abs(latRad)) / 5.0);
            if (bd < 0) bd = 0; if (bd > 17) bd = 17;
            if (k > 0.5) qnetNegMBandLand[bd]++; else qnetNegMBandSea[bd]++;
            return wOld;
        }
        return Atmosphere.H_BL * fNet / M;
    }
    /**
     * ★★★★★★ **§473：稳态【平流】水汽收支 —— §472 那个局地平衡的严格版。** 默认 false ⇒ 逐位不变。
     *
     * <pre>
     *   dq/ds = (E - P - V_vent)/|V_h|          沿流线积分，海洋作边界条件
     *   E      = 86400*chv*(qSat(Ts)*beta - q)      （线性于 q）
     *   P      = k_P*q                              （线性于 q）
     *   V_vent = C*(q - q_FT),  q_FT = q*qftF       （线性于 q）
     * </pre>
     * 三项都对 `q` 线性 ⇒ 令 `lambda = 86400*chv + k_P + C*(1-qftF)`、`E0 = 86400*chv*qSat(Ts)*beta`：
     * <pre>
     *   dq/ds = (E0 - lambda*q)/|V|
     *   ⇒ 逐步递推（精确解）：q <- q*exp(-a) + (E0/lambda)*(1-exp(-a)),   a = lambda*ds/|V|
     * </pre>
     * **⇒ 局地平衡 `E0/lambda` 就是吸引子（正是 §472 的闭式解），
     * 而平流把它以长度尺度 `L_eff = |V|/lambda` 拉过去 —— `L_eff` 是【导出的】，
     * 替换掉 §444 里那个拟合的 `SOURCE_FETCH_L`。**
     *
     * <p>⚠ 这是 §444 fetch 方案的严格版；也是 §438/§441-32/§472 三次指认的那一项
     * （「缺的是经度方向的信息」/「没有输送」）的正面实现。
     */
    public static boolean Q_ADVECT_BUDGET = false;

    /**
     * ★★★★★★★ **§496：边界层准平衡（BLQ）对流判据。** 默认 **false** ⇒ 逐位不变。
     *
     * <p><b>文献逐字</b>（Raymond《Convection and the Environment》Ch.6，`refs/raymond_ch6_env.pdf`）：
     * <pre>
     *   the updraft mass flux proportional to the difference between the boundary
     *   layer moist entropy s_bl and the saturated moist entropy of the throttling
     *   layer of the free troposphere s*_th ... M_u = gamma * (s_bl - s*_th)   (6.9)
     * </pre>
     * **`s_bl <= s*_th` ⇒ `M_u = 0` ⇒ 一滴雨都不下，无论 `wEff` 多大。**
     *
     * <p><b>为什么这是「缺的那一项」</b>（§493）：模型现在 `P = EPS_C*rho*q*wEff/rho_w` 是
     * **大尺度凝结式、没有任何对流触发** ⇒ 只要 `wEff > 0` 就下雨 ⇒
     * 「正确地实现辐合聚集水汽」反而把沙漠弄得更湿。**它【不依赖 divU 的符号】** ——
     * 这是六条路都撞在 `divU` 上之后，唯一还没被用过的自由度。
     *
     * <p><b>本实现（用 MSE 代替熵，与文献同源）</b>：
     * <pre>
     *   h_bl  = CP*T_sfc + LV*q                    （z = 0）
     *   T_th  = T_sfc - GAMMA*H_BL ,  z_th = H_BL
     *   h*_th = CP*T_th + G_ACC*z_th + LV*qSat(T_th)   （throttling 层【饱和】）
     *   dh    = h_bl - h*_th
     *   g     = smoothstep01( dh / (CP*BLQ_SMOOTH_K) )
     *   P    <- P * g
     * </pre>
     * ⚠ **`h*_th` 用【饱和】的 `qSat(T_th)`** —— 这是与 §482 的 `wEffQnet` 的关键差别
     * （那里用的是未饱和的 `q*exp(-z/H)`，导致 `M` 的符号随旋钮翻转）。
     * ⚠ `BLQ_SMOOTH_K` 是**数值光滑宽度**（不是物理常数），只为避免世界生成里的阶跃；已记账、可 A/B。
     */
    public static boolean BLQ_GATE = false;
    /** 光滑宽度（K 当量）。1 K 相对 ±20,000 J/kg 的信号 ⇒ 实质是阶跃但不产生空间不连续。 */
    public static double BLQ_SMOOTH_K = 1.0;
    /** 诊断：最近一次的 `dh = h_bl - h*_th`（J/kg）。 */
    public static volatile double blqLastDh = 0.0;
    /** 诊断：被 BLQ 判据压到接近 0 的次数（`g < 0.01`）。 */
    public static long blqBlocked = 0;
    /** 诊断：调用次数。 */
    public static long blqCalls = 0;

    /** smoothstep01(x)：x<=0 -> 0，x>=1 -> 1。 */
    public static double smoothstep01b(double x) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        return x * x * (3.0 - 2.0 * x);
    }

    /** 平流积分里【源汇项求值的最大点数】（性能守卫；路径更长时按等间隔抽样）。 */
    public static int ADVB_MAX_EVAL = 4;

    /** 平流积分的代价计数（探针读数）。 */
    public static long advWalkSteps = 0, advEvalPoints = 0, advCalls = 0;
    /** §493：被饱和上限截住的步数（诊断；大量截断说明 λ 太小/辐合过强）。 */
    public static long advCapped = 0;

    /**
     * 沿风逆推到海，再【从海向本点正向积分】稳态平流收支。返回 `{fetch, q}`；找不到海返回 `{-1, NaN}`。
     */
    public static double[] moistureAdvected(int x0, int z0, long seed, int cell, double theta, int gradStep,
                                            double u0, double v0) {
        advCalls++;
        final int CAP = 64;
        double[] px = new double[CAP], pz = new double[CAP], pu = new double[CAP], pv = new double[CAP];
        double[] pd = new double[CAP];
        int m = 0;
        double x = x0, z = z0, u = u0, v = v0;
        double sea = Double.NaN;
        for (double dd = 0.0; dd < SOURCE_FETCH_MAX; dd += UPWIND_STEP) {
            double sp = Math.hypot(u, v);
            if (sp < 0.1) return new double[]{-1.0, Double.NaN};
            x -= u / sp * UPWIND_STEP; z -= v / sp * UPWIND_STEP;
            // ★ §527 修复（审计面 1）：原来是 clamp(z, 0, MAX_D) —— 那只保留【北半球】：
            //   南半球点（z 约 3*MAX_D）会被夹到 MAX_D = 【北极】，而不是回到它自己的纬度。
            //   契约下气候在 Z 上以 Z_CYCLE 周期重复 ⇒ 只能【回卷】不能【夹逼】。
            //   回卷对北半球点 z 不变 ⇒ 两个盒子（15~35N / 20~35N）的读数与 P293 均不受影响。
            z = WorldContract.wrapZ(z, WorldContract.Z_CYCLE);
            int xi = (int) Math.round(x), zi = (int) Math.round(z);
            if (!PlateField.isLandWithCell(xi, zi, seed, cell)) {
                sea = qSat(Atmosphere.annualSeaLevelTemp(WorldContract.latOf(zi), 0.0,
                           Atmosphere.sstAnom(xi, zi, theta))) * RH_SEA;
                break;
            }
            if (m < CAP) { px[m] = x; pz[m] = z; pu[m] = u; pv[m] = v; pd[m] = dd; m++; }
            double[] uv = Atmosphere.windAt(xi, zi, seed, cell, theta, gradStep);
            u = uv[0]; v = uv[1];
            advWalkSteps++;
        }
        if (Double.isNaN(sea)) return new double[]{-1.0, Double.NaN};
        if (m == 0) return new double[]{0.0, sea};
        // 从海侧（下标 m-1）向本点（下标 0）正向积分
        int every = Math.max(1, m / Math.max(1, ADVB_MAX_EVAL));
        double q = sea;
        for (int i = m - 1; i >= 0; i--) {
            if (((m - 1 - i) % every) != 0 && i != 0) continue;   // 等间隔抽样，末点必算
            int xi = (int) Math.round(px[i]), zi = (int) Math.round(pz[i]);
            double sp = Math.max(0.1, Math.hypot(pu[i], pv[i]));
            double kk = Atmosphere.kappaMemo(xi, zi, seed, cell);
            double tS = Atmosphere.surfaceTemp(xi, zi, seed, cell, theta);
            double chv = Radiation.bulkCoeff(kk, sp);
            // ★ §474：**绝不在路径点上独立求 beta**。§464/§466 的教训：在自旋之外再调 `betaAt`
            //   会为每个路径点再造一次完整桶自旋（实测 4.378 ms/点/次 ⇒ D/E 一度是 50× 基线）。
            //   这里直接读【调用点已经算出的】BETA_OVERRIDE（mmPerDay 在调本方法前设好）。
            //   代价：路径上的 beta 用目标点的值（准静态近似，须记账），换来零额外自旋。
            Double bo = BETA_OVERRIDE.get();
            double beta = (kk > 0.5) ? (bo == null ? 1.0 : bo.doubleValue()) : 1.0;
            double[] e = Atmosphere.windAt(xi + gradStep, zi, seed, cell, theta, gradStep);
            double[] w = Atmosphere.windAt(xi - gradStep, zi, seed, cell, theta, gradStep);
            double[] n = Atmosphere.windAt(xi, zi + gradStep, seed, cell, theta, gradStep);
            double[] s2 = Atmosphere.windAt(xi, zi - gradStep, seed, cell, theta, gradStep);
            double divU = (e[0] - w[0]) / (2.0 * gradStep) + (n[1] - s2[1]) / (2.0 * gradStep);
            double wE = wEff(WorldContract.latOf(zi), theta, divU);
            double qftF = Math.exp(-(0.5 * Atmosphere.H_EFF) / H_MOIST);
            // ★★★ §492 单位修正：`lambda` 必须是【1/day】而不是「mm/day per unit q」。
            //   推导：dW/dt = E - P - W*divV,  W = q*rho_a*H  ⇒  dq/dt = (E - P - q*rho_a*H*divV)/(rho_a*H)
            //   三项的 q 依赖都是【通量】单位（mm/day per unit q）⇒ 必须整体除以 rho_a*H_MOIST 才变成 1/day。
            //   检查：86400*chv/(rho_a*H) = 3266/2450 = 1.33 /day ⇒ 时间尺度 18 h ✓ 物理
            //   ⚠ 修正前 lambda 少了这个除法 ⇒ a 大 2450 倍 ⇒ 每步撞上 a>40 的截断 ⇒ 退化成局地平衡（§491）。
            double HCO = RHO_AIR * H_MOIST;                       // = 2450 kg/m^2（气柱质量）
            // ★★★ §493（乙₁ 的核心）：【带符号】的散度项。
            //   dq/dt = (E - P)/(rho_a*H) - q*div(V)   ⇒ lambda 里加的是【带符号】的 divV（1/day）。
            //   辐合（divV<0）⇒ lambda 变小 ⇒ q* = E0/lambda 变大 ⇒ **周围的水汽被聚过来**。
            //   ⚠ 修前用的是 Math.abs(...)*(1-qftF)（对称通风）⇒ 把辐合与辐散当同一件事，
            //     那一项横向补给【结构上不存在】（§490/§492 四）。
            double lamFlux = (86400.0 * chv
                          + ((wE > 0.0) ? EPS_C * RHO_AIR * wE / RHO_WATER * 86400.0 * 1000.0 : 0.0)) / HCO;
            double lam = lamFlux + 86400.0 * divU;                // [1/day]，divU 带符号
            if (lam < 1.0e-3) lam = 1.0e-3;                        // 数值下限（不许为负/为 0）
            double e0 = 86400.0 * chv * qSat(tS) * beta / HCO;
            double ds = (i == m - 1) ? pd[i] : (pd[i + 1] - pd[i]);
            double a = lam * ds / sp / 86400.0;                   // lam[1/day] * 秒 / 86400 ⇒ 无量纲
            if (a > 40.0) a = 40.0;
            q = q * Math.exp(-a) + (e0 / lam) * (1.0 - Math.exp(-a));
            // 物理上限：比湿不能超过该点的饱和值（推导得出，不是拟合）
            double qCap = qSat(tS) * beta;
            if (q > qCap) { q = qCap; advCapped++; }
            advEvalPoints++;
        }
        return new double[]{pd[m - 1] + UPWIND_STEP, q};
    }
    /**
     * **§444：该点的近地面比湿。** `Q_FROM_SOURCE=false` 或该点是海洋（`kappa <= 0.5`）
     * 或逆推不到海时，**等于** {@link #moisture}（逐位不变）。
     */
    public static double moistureFromSource(int x, int z, long seed, int cell, double theta, int gradStep,
                                            double tSfcSl, double elev, double kappa, double u, double v) {
        if (kappa <= 0.5) return moisture(tSfcSl, elev, kappa);
        double[] sea = upwindSea(x, z, seed, cell, theta, gradStep, u, v);
        // ⚠ §444 修正：**逆推不到海时不再回落到原始（湿）q**，而是按【最大距离】处理 ——
        //   否则内陆深部的点保留原值，把盒均拉回去（实测撒哈拉盒子里 36% 的点落在这个分支）。
        //   物理上，空气在陆上走了那么远，水汽确实已经耗尽。
        double fetch = (sea[0] < 0.0) ? SOURCE_FETCH_MAX : sea[0];
        double tSrc  = (sea[0] < 0.0) ? tSfcSl : sea[1];
        Double bo = BETA_OVERRIDE.get();
        double rh = (bo == null) ? RH_SEA : SoilMoisture.rhEff(bo);
        return rh * qSat(tSrc) * Math.exp(-fetch / SOURCE_FETCH_L) * depletion(elev, kappa);
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
        // §264：纬向平均海平面温度直接取观测月表（ZONAL_SL_FROM_TABLE=false 时这一支不执行）
        if (ZONAL_SL_FROM_TABLE) return ZonalTables.tZmSlMonth(latRad, theta);
        // ⚠ D79 步骤 0（§231.4）：改走**纬向平均专用**的季节异常（表 A_ZM_K），
        //   不再借用逐点口径的 seasonalAnomaly(lat, <kappa>, theta) —— 两者口径不同，
        //   共用一张表时修逐点那一侧会把涡动链一起带偏。
        return Atmosphere.zonalMeanSeaLevelK(latRad)
             + Atmosphere.seasonalAnomalyZonal(latRad, theta);
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
     * **纬向平均海平面温度直接取观测月表**（设计冻结 §264，用户裁决 2026-09-17）。
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「{@code false}（默认）」，但**实际默认已是 {@code true}**
 * （见 :710 声明行与 2026-09-17 的用户裁决）。原「逐位相同」只对 {@code false} 支成立。
     * {@code true} ⇒ 在 {@code zonalSlTemp} 的第一支返回
     * {@link ZonalTables#tZmSlMonth}(lat, theta)，即**观测的纬向平均海平面温度**。
     *
     * <p><b>为什么这是「合理」而不是拐杖</b>：
     * <ol>
     *   <li>纬向平均态**必须外生给定** —— 这是本模型的第一条设计约束（X 无限 ⇒ 纬向平均无定义）。
     *       年基 {@code T_OCEAN_K}/{@code T_LAND_K}、风 {@code U_ZM}、气压 {@code P_REF} 早就是观测表，
     *       <b>温度的季节部分不该例外</b>；</li>
     *   <li>{@code tZmSlMonth} 是**纬向平均量**，<b>不可能编码降水</b> —— 降水依赖本世界自己生成的
     *       陆海分布（kappa 场），所以这条外生表没有把结论写进公式；</li>
     *   <li>它同时消掉三个互相矛盾的口径：年基（模型算的）vs 振幅（补丁表 {@code A_ZM_K}）vs 观测月表。
     *       实测代价（§256）：原来的 {@code zonalSlTemp} 在 60~90 度比观测月表冷 <b>4~21 K</b>，
     *       它使柱水汽在 60~70 度只有观测的 0.44~0.75、{@code |dW/dy|} 在 50~60 度是观测的 1.8~2.2 倍，
     *       进而让涡动水汽通量在 50~55 度塌到 45 度的 0.40。</li>
     * </ol>
     *
     * <p>⚠ <b>纪律修正（§264.1）</b>：这一支是「把已知错误换成已知正确」。
     * 若验收判据因此后退，<b>不构成回滚它的理由</b> —— 应接受该后退并记为下一处待修。
     * 进 {@link com.EyeOfHarmonyBuffer.sim.runtime.SimClimate#configStamp()}。
     */
    public static boolean ZONAL_SL_FROM_TABLE = true;   // 2026-09-17 用户裁决落地：中纬两条独立带 −40%→−3%（§265）

    /**
     * **柱水汽取观测月表**（设计冻结 §266/§267）。
     *
     * <p>{@code false} ⇒ 原式 {@code moisture(zonalSlTemp)*RHO_AIR*H_MOIST}（隐含**有效柱 RH 恒为 0.80**）。
     * {@code true} ⇒ {@link ZonalTables#wColMonth}，即 ERA5 月平均全球柱水汽（20 层 1000~300 hPa 积分/g）。
     *
     * <p><b>为什么</b>（§266.6 实测，全球口径）：把常数 0.80 换算成它本该代表的「有效柱 RH」，
     * 观测是 <b>20~30 度只有 0.57~0.62</b>、45~60 度 0.79~0.99、70~85 度 0.87~0.99。
     * 即模型恒用 0.80 ⇒ <b>副热带气柱湿了 30~40%</b>，这正是 §265 落地后
     * 「冬季副热带 +769.7%」的唯一来源。
     *
     * <p><b>为什么这不是把结论写进公式</b>：① 柱水汽是**热力学状态量**，不是降水型；
     * 降水仍由 {@code curv(W)}、{@code stormGate}、{@code K} 与**本世界自己的 kappa 场**算出来；
     * ② 模型里 W **本来就是纯纬向函数**（{@code curv} 只用纬向剖面、{@code stormGate} 用的是**全球表**
     * {@code uZm}）⇒ 它现在对世界的陆海分布**零依赖**，换成观测表**不减少任何地理内容**；
     * ③ 与年基、风、气压、以及 §265 刚落地的温度完全同类（纬向平均态外生给定）。
     *
     * <p>⚠ 该形状**非单调**（副热带干、中纬持平、高纬略湿），**不可能**写成 {@code f(w_zm)}
     * —— 70~85 度的 {@code w_zm} 也是负的（下沉），那里却需要 0.87~0.99。已试算否决。
     * 进 {@link com.EyeOfHarmonyBuffer.sim.runtime.SimClimate#configStamp()}。
     */
    public static boolean COL_WATER_FROM_TABLE = true;   // 2026-09-17 §268.3 四格 A/B 通过后落地

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
        // §267：COL_WATER_FROM_TABLE=false 时走下面原式，逐位不变。
        if (COL_WATER_FROM_TABLE) return ZonalTables.wColMonth(latRad, theta);
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
     * ★★★ **§432：纬向平均温度的经向梯度是否走【光滑重建】**。
     *
     * <pre>
     *   false（现状）= 节点的 ±EDDY_DPHI_DEG 中心差分
     *   true          = 对 -90..+90 的 5 度节点做 PCHIP（Fritsch-Carlson）保形三次重建，再取【解析导数】
     * </pre>
     *
     * <p><b>为什么必须改（P642 实测的硬证据）</b>：{@code ZonalTables.tZmSlMonth} 在纬度上是
     * <b>5 度节点线性插值</b> ⇒ <b>它的一阶导在每个节点上跳变</b>。DJF 实测：
     * <pre>
     *   60.0N T=262.69   61.5N 260.49   63.0N 258.29   64.5N 256.09
     *   => 连续三次恰好 -2.20 K / 1.5 度 = -1.4667 K/度 —— 这是【分段直线】的签名
     *   而 55~60N 的斜率只有 -0.90，65~70N 变成 +0.09（表的最低点在 65N，向极反而回升）
     * </pre>
     * 于是 {@code sigma ∝ |dT/dy|} 在每个节点上带一个折角，{@code K = sigma L_d^2} 继承它，
     * 而 {@code MFC = d(K X')/dy} 是**对分段线性表求的第二次导** ⇒ 57.5~60N 出现假尖峰
     * （sigma 6.73 -> 8.94 -> 11.17 -> 8.90），把 K 顶出一个鼓包，K' 在 55N 反号，
     * 56~58N 因此出现一个**不在物理里的负 MFC 洞**。
     *
     * <p>⚠ 这条与 §427 否证的那条【不是同一件事】：柱水汽 W 只被求一次导（PCHIP 中性），
     * 温度 T 经由 sigma 被求两次导。<b>§426 猜的「阶梯」猜对了机制、猜错了表。</b>
     *
     * <p><b>默认 false ⇒ 走原路 ⇒ 逐位不变。</b>
     */
    public static boolean EDDY_T_SMOOTH = false;

    /** PCHIP 节点：-90..+90 每 5 度，共 37 个。 */
    public static final int T_NN = 37;
    private static final class TSlopeCache {
        boolean valid; double theta;
        final double[] m = new double[T_NN];
    }
    private static final ThreadLocal<TSlopeCache> T_SLOPE = new ThreadLocal<TSlopeCache>();

    /** 37 个节点的 PCHIP 节点导数（dT/dy，K/m）。等距 ⇒ 权重 W1 = W2 = 3h。 */
    static double[] tNodeSlopes(double theta) {
        TSlopeCache c = T_SLOPE.get();
        if (c == null) { c = new TSlopeCache(); T_SLOPE.set(c); }
        if (c.valid && c.theta == theta) return c.m;
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] y = new double[T_NN];
        for (int k = 0; k < T_NN; k++) y[k] = zonalSlTemp(Math.toRadians((k - 18) * 5.0), theta);
        double[] dl = new double[T_NN - 1];
        for (int k = 0; k < T_NN - 1; k++) dl[k] = (y[k + 1] - y[k]) / h;
        c.m[0] = pchipEnd(dl[0], dl[1]);
        for (int k = 1; k < T_NN - 1; k++) {
            double d0 = dl[k - 1], d1 = dl[k];
            if (d0 * d1 <= 0.0) { c.m[k] = 0.0; continue; }
            c.m[k] = 2.0 / (1.0 / d0 + 1.0 / d1);
        }
        c.m[T_NN - 1] = pchipEnd(dl[T_NN - 2], dl[T_NN - 3]);
        c.valid = true; c.theta = theta;
        return c.m;
    }

    /** 纬向平均温度的经向梯度 dT/dy（K/m）。见 {@link #EDDY_T_SMOOTH}。 */
    public static double zonalSlTempSlope(double latRad, double theta) {
        if (!EDDY_T_SMOOTH || ZONAL_PROFILE_OVERRIDE != null) {
            double d = Math.toRadians(dphiDeg());
            return (zonalSlTemp(latRad + d, theta) - zonalSlTemp(latRad - d, theta))
                 / (2.0 * d * WorldContract.R_EFF);
        }
        double fr = latRad / Math.toRadians(5.0) + 18.0;
        if (fr <= 0.0) return tNodeSlopes(theta)[0];
        if (fr >= T_NN - 1) return tNodeSlopes(theta)[T_NN - 1];
        int i = (int) Math.floor(fr);
        double t = fr - i, h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] m = tNodeSlopes(theta);
        double y0 = zonalSlTemp(Math.toRadians((i - 18) * 5.0), theta);
        double y1 = zonalSlTemp(Math.toRadians((i + 1 - 18) * 5.0), theta);
        double dHdt = y0 * (6.0 * t * t - 6.0 * t) + h * m[i] * (3.0 * t * t - 4.0 * t + 1.0)
                    + y1 * (-6.0 * t * t + 6.0 * t) + h * m[i + 1] * (3.0 * t * t - 2.0 * t);
        return dHdt / h;
    }
    /**
     * ★★★ **§433：Eady 增长率的温度梯度是否改用 850 hPa（自由对流层）**。
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「{@code false}（现状）」，但**实际默认已是 {@code true}**
 * （见 :881；同段 :876 亦已写「默认已切到 true」⇒ 原文本段自相矛盾）。
     * {@code true} = {@link ZonalTables#t850Month}（NCEP/NCAR R1 月平均 850 hPa 温度）的 ±5 度差分，
     * 且热成风式里的 {@code 1/T} 也用 850 hPa 温度。
     *
     * <p><b>为什么必须改（§432/§433 实测）</b>：DJF 的段斜率（K/度）
     * <pre>
     *   段         50-55   55-60   60-65   65-70   70-75
     *   850 hPa   -0.722  -0.565  -0.509  -0.468  -0.328   平滑单调
     *   海平面还原  -0.322  -1.010  -1.466  +0.086  +0.294   3 倍悬崖 + 反号
     * </pre>
     * 海平面还原的悬崖与反号来自 {@code +ZF(phi)*GAMMA*z_bar(phi)}：格陵兰/海冰的地表不均匀性
     * 被还原成高温。它让 sigma 在 60N 出一个鼓包 ⇒ K 鼓包 ⇒ K' 在 55N 反号 ⇒
     * **55~57.5N 出现一个观测里没有的负 MFC 洞**（§431 暴露的那条缺陷）。
     *
     * <p><b>口径一致性</b>：{@link #stormGate} 用的 {@code ZonalTables.uZm} **本来就是 850 hPa 纬向风**；
     * 改用 850 hPa 温度后，整条涡动链（风、温度、扩散率）**同处一个层**。
     *
     * <p><b>§433 定案（用户裁决「按最标准的做，不妥协不捏造」）：默认已切到 true。</b>
     * P644 实测：DJF 的 sigma 由 6.73/8.94/11.17（非单调、60N 鼓包）变成 4.91/4.67/4.43（单调），
     * **55/57.5N 的假负瓣由 −1.595/−0.399 变成 +1.648/+1.421**（观测 +1.12/+1.04）。
     * 代价：JJA 56~59N 变干（海 JJA 平均 0.88 -> 0.74），已记账为**新暴露**的缺陷。
     */
    public static boolean EDDY_SIGMA_T850 = true;

    /**
     * **Eady 斜压增长率** <code>sigma = 0.31 * f * |dU/dz| / N</code>（1/s）。
     *
     * <p>dU/dz 由**热成风**从模型自己的纬向平均温度场给出：
     * <code>f * dU/dz = -(g/T) * dT/dy</code>。**零新参数、零硬编码纬度形状。**
     */
    public static double eadyGrowth(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
        // §433：改用 850 hPa（自由对流层）的经向梯度与温度。理由见 EDDY_SIGMA_T850。
        // 默认 false ⇒ t 与 dTdy 都是原式，逐位不变。
        double t = EDDY_SIGMA_T850 ? ZonalTables.t850Month(latRad, theta) : zonalSlTemp(latRad, theta);
        double tN = EDDY_SIGMA_T850 ? ZonalTables.t850Month(latRad + d, theta) : zonalSlTemp(latRad + d, theta);
        double tS = EDDY_SIGMA_T850 ? ZonalTables.t850Month(latRad - d, theta) : zonalSlTemp(latRad - d, theta);
        // §432：EDDY_T_SMOOTH 时改走 PCHIP 解析导数（默认 false ⇒ 下面这行一字不改）。
        double dTdy = (EDDY_T_SMOOTH && !EDDY_SIGMA_T850) ? zonalSlTempSlope(latRad, theta)
                                    : (tN - tS) / (2.0 * d * WorldContract.R_EFF);
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
    // ⚠⚠ **§429 定案**：切到 0。理由不是标定，是**标准式**：Green/Stone 的 D 是 sigma^1·L_d^2（差一个常数）。
    //   closure 1 的 K = GAIN*tau*sigma^2*L_d^2 在文献里也站得住（CH12 式 16/20，K = v*^2*tau，Taylor 1922），
    //   但它的 gain 是【标定出来的】（§146~§150 记录的 5 倍结构比，且那段标定早于 §268 换成 ERA5 柱水汽表）。
    //   在「不捏造」的判据下：能用推导就不用拟合 ⇒ closure 0。
    public static int EDDY_CLOSURE = 0;
    /** 拉格朗日去相关时间（s）：Caballero &amp; Hanley (2012) §6 实测 ≈ 0.9 天。 */
    public static double EDDY_TAU = 77_760.0;
    /**
     * 物理化闭合里唯一的 O(1) 无量纲系数（由 GPCP 标定；见设计冻结 §146）。
     *
     * <p><b>⚠⚠ 2026 重标（§320，「V8 海陆生成器接入」带来的第四次重标）</b>：
     * 地形换成 {@code TalosField}（当时靠 {@code PlateField.TALOS_TERRAIN = true} 选中该路；
     * 那个开关已随 §567 删除）后，
     * 同一个 gain = 5.45 的 45~55 夏从 2.562 掉到 <b>1.939（-24.4%）</b> ⇒ 旧值失效。
     * 在新地形上重扫（P463，closure 1，锚 = GPCP v2.3 LTM(1991-2020) 45~55N JJA = 2.565）：</p>
     * <pre>
     *   gain    45-55夏   对锚偏差   独立 err
     *   5.450    1.939    -24.4%     0.3582
     *   7.500    2.376     -7.4%     0.3507
     *   8.400    2.568     +0.12%    0.3649   <- 采用
     *   9.300    2.759     +7.6%     0.4083
     * </pre>
     * <p>⚠ <b>必须记账</b>：独立 err 在新地形上<b>无论 gain 取何值都比旧地形差</b>
     * （旧地形 0.2251~0.2656，新地形 0.3507~0.4083）⇒
     * <b>这是一次真实的地形代价，gain 无法弥补。</b>它把「风暴轴水汽输送」的模拟质量降低了一档；
     * 归因与后续处理见设计冻结 §320（独立 err 的绝对值随地形变化 ⇒ **只做 A/B 相对比较**）。</p>
     */
    // ⚠⚠ **第五次重标（§323，V8 地形 + E123/E124 修复后）**：
    //   8.40 是在「E124 容差路径 bug」尚未修好的地形上标定的；E124 修完后地形再变
    //   （LEVEL 0.6816 -> 0.7054）⇒ P296 实测 2.33（-9.0%）⇒ 已失效。
    //   在修正地形上重扫（P463，closure 1，锚 = GPCP 2.565）：
    //     gain    45-55夏   偏差     独立 err
    //     8.400    2.345   -8.6%    0.4150
    //     9.500    2.547   -0.70%   0.4152
    //     10.600   2.750   +7.2%    0.4600
    //   ⇒ 线性反解命中 2.565 得 **9.60**（M8：地形纯函数性还在被修时，对地形标定的常数必须在其后重标）。
    //
    // ⚠ 独立 err 在新地形上无论 gain 取何值都在 0.415~0.46，**显著劣于旧地形的 0.225~0.266** ⇒
    //   **这是一笔地形代价，gain 补不回来**（待用户裁决，见设计冻结 §320）。
    public static double EDDY_PHYS_GAIN = 9.60;

    /**
     * **A-ii 开关**（用户裁决 A，设计冻结 §234~§236）：涡动项的**纬度放置**改由观测给。
     *
     * <p>{@code false}（默认）⇒ 与接线前**逐位相同**（走 {@link #modelShape}）。
     * {@code true} ⇒
     * <pre>
     *   eddyMfc = EDDY_MFC_REF * eddyMfcObs(|phi|) * [ modelShape(phi,theta) / A(|phi|) ]
     * </pre>
     * <b>放置与符号</b>来自观测（NCEP 日资料实测的瞬变涡动水汽通量辐合，30~40 度是**负**的
     * ⇒ {@code max(0,·)} 之后那里不再有涡动降水 —— 这正是 §235.2 证实的病）；
     * <b>季节调制</b>仍由模型自己的 {@code modelShape} 给（除以 {@code A} 只是把它无量纲化、有界化），
     * 所以 B2.b 仍由模型自己的温度场决定，§217 的原则不被绕过。
     */
    public static boolean EDDY_PLACEMENT_FROM_OBS = false;   // A′：①② 达标但③不可能达标（§239.2/§241），收尾关闭

    /** A-ii 的基准幅值（kg/(m^2 s)）—— 按既定口径标定（45~55 夏 = GPCP 2.565）。 */
    public static double EDDY_MFC_REF = 1.0e-4;   // [A 标定点 2]

    /**
     * **把涡动闭合补成完整的通量散度**（设计冻结 §268，工作项 13）。
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「{@code false}（默认）」，但**实际默认已是 {@code true}**
 * （见 :1040，2026-09-17 §268.3 四格 A/B 通过后落地）。
     * {@code true} ⇒ {@code MFC = d(gate*K*W')/dy = (gate*K)'*W' + gate*K*W''}。
     *
     * <p><b>这不是新物理、也不是新参数</b>：它只是把**同一个下坡扩散闭合**写成它本来的形式
     * （「通量的散度」），而原式是它在 {@code (gate*K)' = 0} 假设下的特例。
     *
     * <p><b>为什么现在必须补</b>（§267.2 实测）：只把柱水汽换成观测表（水平只差 7%），
     * 中纬降水就掉了 55~66% —— 因为 {@code curv(W)} 换了符号。
     * 生产式漏掉的 {@code (gate*K)'*W'} 用**旧 W** 量时是保留项的 37%（§262.2），
     * **用正确的 W 量时就成了决定性的一项**。
     */
    public static boolean EDDY_FULL_DIVERGENCE = true;   // 2026-09-17 §268.3 四格 A/B 通过后落地

    /** {@code F(y) = gate(y)*K(y)*W'(y)}（涡动水汽通量，poleward 为正）。 */
    static double fluxAt(double y, double theta, double d) {
        double w0 = wOf(y, theta);
        double wp = wOf(y + d, theta);
        double wm = wOf(y - d, theta);
        if (w0 <= 0.0) return 0.0;
        double dWdy = (wp - wm) / (2.0 * d * WorldContract.R_EFF);
        double ld = deformRadius(y, theta);
        double sg = eadyGrowth(y, theta);
        double K = (EDDY_CLOSURE == 0)
            ? EDDY_MIX * sg * ld * ld
            : EDDY_PHYS_GAIN * EDDY_TAU * sg * sg * ld * ld;
        return stormGate(y, theta) * K * dWdy;
    }

    /**
     * **掩码放在散度外面**（设计冻结 §269.5，工作项 14）。
     *
     * <p>{@code false} ⇒ 掩码在微分里面（§268 的写法）；{@code true} ⇒
     * <pre>
     *   MFC = gate * d(K*W')/dy = gate * ( K'*W' + K*W'' )
     * </pre>
     *
     * <p><b>为什么必须这样</b>（§269.4 实测）：{@code gate} 是「Rossby 波不能传入自由对流层东风带」
     * 的**掩码**，它回答「这里有没有涡动」，不该参与「通量的空间结构」。
     * 把掩码放进微分之后 {@code G = gate*K} 在 45 度取极大 ⇒ {@code G'} 在那里反号 ⇒
     * {@code W'G'} 在 45 度下方为负、上方为正，与保留项 {@code GW''} **几乎相消**
     * ⇒ 45 度出现一个**不在物理里的洞**。量级上 {@code W'G'} 在 35~60 度比 {@code GW''} 大
     * <b>1~48 倍</b>，即形状几乎完全由掩码的梯度决定。
     */
    public static boolean EDDY_MASK_OUTSIDE = false;

    /** 扩散率 K（**不含掩码**）。 */
    static double kAt(double y, double theta) {
        double ld = deformRadius(y, theta);
        double sg = eadyGrowth(y, theta);
        return (EDDY_CLOSURE == 0) ? EDDY_MIX * sg * ld * ld
             : EDDY_PHYS_GAIN * EDDY_TAU * sg * sg * ld * ld;
    }

    // ==================== §427 目标第 3 项：涡动扩散的【变量】与【梯度构造】 ====================

    /**
     * **被涡动扩散的标量**（设计冻结 §427；用户裁决 A「换被扩散的变量」）。
     *
     * <pre>
     *   0 = 柱水汽 W        （现状；ERA5 月表 ZonalTables.wColMonth，单位 kg/m^2）
     *   1 = 近地面比湿 q    （rho*H_MOIST*q，单位 kg/m^2，与 W 同量纲）
     *   2 = 湿静能 h        （rho*H_MOIST*(cp*T/L_v + q)，单位 kg/m^2，与 W 同量纲）
     * </pre>
     *
     * <p><b>为什么 h 这样写</b>：{@code h = cp*T + L_v*q} 是单位质量的湿静能（J/kg）。
     * 除以 {@code L_v} 只是把它换成「等效比湿」（kg/kg），再乘 {@code rho*H_MOIST} 换成
     * 「等效水汽柱」（kg/m^2）—— **纯量纲换算，不含任何新常数、不改形状**。
     * 这样三个变量的量级可比（赤道处 W≈47、q 柱≈46、h 柱≈341 kg/m^2），
     * 剩下的倍数交给 {@code EDDY_PHYS_GAIN} 重标（与 §146 引入 closure 1 时同一套纪律）。
     *
     * <p><b>物理依据</b>：涡动（斜压）混合的对象是**湿静能**而不是柱水汽 —— 湿静能的经向梯度
     * 在深热带近乎为零（热带湿中性）、在中纬斜压带达峰，因此降梯度通量 {@code v'h' = -D dh/dy}
     * 必然在中纬单峰、两端趋零，其辐合 {@code -d(v'h')/dy} 自动给出「低纬辐散、中纬辐合」的
     * **单极型**剖面（§426 实测观测就是单极型）。柱水汽的梯度峰在副热带（因为 q_sat 对 T 是指数），
     * 位置比观测的峰偏赤道 15~20 度 ⇒ 形状对不上。
     *
     * <p><b>⚠ 口径</b>：这是**纬向平均**量，用一个显式 RH_SEA 的 {@link #zonalQ}，
     * <b>不走</b> {@link #moisture}（后者会被 {@code BETA_OVERRIDE} 这个逐点 ThreadLocal 改），
     * 否则涡动链会随「本地土壤湿度」逐点变化 —— 那不是纬向平均量该有的行为。
     *
     * <p><b>默认 0 ⇒ 走 {@link #modelShapeFull} 原路 ⇒ 逐位不变。</b>
     */
    public static int EDDY_VAR = 0;

    /**
     * **经向梯度的构造方式**（设计冻结 §427）。
     *
     * <pre>
     *   0 = 节点中心差分（现状）：(X(y+d) - X(y-d)) / (2*d*R_eff)，d = EDDY_DPHI_DEG
     *   1 = 保形三次样条（PCHIP）解析导数：在 19 个 5 度节点上重建 C1 曲线再解析求导
     * </pre>
     *
     * <p><b>为什么需要 1</b>：{@code columnWater}/{@code zonalSlTemp} 都是**双线性表**
     * （5 度节点 + 月线性）⇒ 一阶差分成阶梯、二阶差分是节点上的折角。
     * 生产式 {@code MFC = d(gate*K*X')/dy} 里含**两次**差分 ⇒ 实质是观测表的二阶差分，
     * 被表本身的节点噪声主导（P632 实测 W'' 在节点上 ±2000~5400e-14、节点之间恒为 0）。
     * PCHIP 的节点导数取**相邻割线的加权调和平均**（Fritsch-Carlson），是保形的一阶估计，
     * 不含二阶差分的放大。
     *
     * <p><b>零新常数</b>：等距节点 ⇒ 权重只依赖 h；端点用标准三点公式 + 保形限幅。
     * <b>默认 0 ⇒ 走原路 ⇒ 逐位不变。</b>
     */
    public static int EDDY_GRAD = 0;

    /** 涡动链的 5 度节点数（0..90 度，共 19 个）。 */
    public static final int EDDY_NN = 19;

    /** **纬向平均**近地面比湿（kg/kg，RH = RH_SEA）。涡动链专用，不读 BETA_OVERRIDE。 */
    public static double zonalQ(double latRad, double theta) {
        return RH_SEA * qSat(zonalSlTemp(latRad, theta));
    }

    /** 涡动链的标量 X(lat,theta)，单位统一为 kg/m^2。见 {@link #EDDY_VAR}。 */
    public static double eddyScalar(double latRad, double theta) {
        if (EDDY_VAR == 1) return RHO_AIR * H_MOIST * zonalQ(latRad, theta);
        if (EDDY_VAR == 2) {
            double t = zonalSlTemp(latRad, theta);
            return RHO_AIR * H_MOIST * (CP_AIR * t / Radiation.LV + zonalQ(latRad, theta));
        }
        return columnWater(latRad, theta);
    }

    /** PCHIP 节点导数缓存的钥匙：theta + 变量号（两个都变了才重算）。 */
    private static final class SlopeCache {
        boolean valid; double theta; int var;
        final double[] m = new double[EDDY_NN];
    }
    private static final ThreadLocal<SlopeCache> SLOPE_CACHE = new ThreadLocal<SlopeCache>();

    /** 端点导数：标准三点公式 + Fritsch-Carlson 保形限幅。 */
    static double pchipEnd(double d0, double d1) {
        double m = (4.0 * d0 - d1) / 3.0;
        if (m * d0 <= 0.0) return 0.0;
        if (d0 * d1 < 0.0 && Math.abs(m) > Math.abs(3.0 * d0)) return 3.0 * d0;
        return m;
    }

    /**
     * **19 个 5 度节点上的 PCHIP 节点导数**（dX/dy，单位 m）。等距节点 ⇒ 权重 W1 = W2 = 3h。
     *
     * <p>内部节点用 Fritsch-Carlson 加权调和平均：割线异号或为零 ⇒ 导数取 0（保形、无过冲）。
     * 结果按 (theta, EDDY_VAR) 记忆在 ThreadLocal 里 —— 与 {@code Atmosphere.KE_MEMO} 同一套纪律，
     * 纯记忆化、不改任何读数。
     */
    static double[] eddyNodeSlopes(double theta) {
        SlopeCache c = SLOPE_CACHE.get();
        if (c == null) { c = new SlopeCache(); SLOPE_CACHE.set(c); }
        if (c.valid && c.theta == theta && c.var == EDDY_VAR) return c.m;
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] y = new double[EDDY_NN];
        for (int k = 0; k < EDDY_NN; k++) y[k] = eddyScalar(Math.toRadians(k * 5.0), theta);
        double[] dl = new double[EDDY_NN - 1];
        for (int k = 0; k < EDDY_NN - 1; k++) dl[k] = (y[k + 1] - y[k]) / h;
        c.m[0] = pchipEnd(dl[0], dl[1]);
        for (int k = 1; k < EDDY_NN - 1; k++) {
            double d0 = dl[k - 1], d1 = dl[k];
            if (d0 * d1 <= 0.0) { c.m[k] = 0.0; continue; }
            c.m[k] = 2.0 / (1.0 / d0 + 1.0 / d1);   // 等距 ⇒ (3h+3h) / (3h/d0 + 3h/d1)
        }
        c.m[EDDY_NN - 1] = pchipEnd(dl[EDDY_NN - 2], dl[EDDY_NN - 3]);
        c.valid = true; c.theta = theta; c.var = EDDY_VAR;
        return c.m;
    }

    /** PCHIP 重建的解析导数 dX/dy（单位 m）。见 {@link #EDDY_GRAD}。 */
    static double eddySlopePchip(double latRad, double theta) {
        double a = Math.abs(Math.toDegrees(latRad));
        if (a >= 90.0) return 0.0;
        double fr = a / 5.0;
        int i = (int) Math.floor(fr);
        if (i > EDDY_NN - 2) i = EDDY_NN - 2;
        double t = fr - i;
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double y0 = eddyScalar(Math.toRadians(i * 5.0), theta);
        double y1 = eddyScalar(Math.toRadians((i + 1) * 5.0), theta);
        double[] m = eddyNodeSlopes(theta);
        double dHdt = y0 * (6.0 * t * t - 6.0 * t) + h * m[i] * (3.0 * t * t - 4.0 * t + 1.0)
                    + y1 * (-6.0 * t * t + 6.0 * t) + h * m[i + 1] * (3.0 * t * t - 2.0 * t);
        return dHdt / h;
    }

    /** X 的经向梯度（按 {@link #EDDY_GRAD} 选构造）。探针可读 —— 涡动链的形状诊断入口。 */
    public static double eddyDXdy(double y, double theta, double d) {
        if (EDDY_GRAD == 1) return eddySlopePchip(y, theta);
        return (eddyScalar(y + d, theta) - eddyScalar(y - d, theta)) / (2.0 * d * WorldContract.R_EFF);
    }

    /** {@code F(y) = gate*K*X'}，X 由 {@link #EDDY_VAR} 选。与 {@link #fluxAt} 同构。 */
    static double fluxAtVar(double y, double theta, double d) {
        if (eddyScalar(y, theta) <= 0.0) return 0.0;
        return gateOf(y, theta) * kAt(y, theta) * eddyDXdy(y, theta, d);
    }

    /** {@code MFC = d(gate*K*X')/dy}（掩码在外，§269.5；X 由 §427 的开关选）。 */
    static double modelShapeVarFull(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        return (fluxAtVar(c + d, theta, d) - fluxAtVar(c - d, theta, d)) / (2.0 * d * WorldContract.R_EFF);
    }

    // ==================== §427.3 涡动门（gate）的【过渡尺度】 ====================

    /**
     * **涡动门的构造**（设计冻结 §427.3；文献核实：Randel & Held 1991 / Lu et al. 2022 / Caballero & Hanley 2012）。
     *
     * <pre>
     *   0 = 现状：smoothstep(clamp01(u_zm / U0_STORM))  —— 阈值式
     *   1 = 混合长宽度：0.5 交点位置不变（仍在 u_zm = U0_STORM/2），
     *       但过渡的【速度宽度】u_w = |du_zm/dy| * l_L，l_L = v* * EDDY_TAU（拉格朗日混合长）
     *   2 = ★临界纬度 + Rhines 宽度（§428）：下边界锁在 u_zm = 0 的【临界纬度】phi_c，
     *       过渡的【纬度宽度】W = L_R / R_eff，L_R = sqrt(2 v* / beta)（Rhines 尺度）
     * </pre>
     *
     * <p><b>为什么必须改</b>：gate 是扩散率的一部分（守恒要求它在导数内），
     * 于是 {@code MFC = (dgate/dy)KX' + gate(dK/dy)X' + gate*K*(dX'/dy)}。
     * 第一项【没有物理意义】：它完全由「阈值取得多陡」决定。P636 实测（sig1，JJA，U0=5）：
     * 它在 30~45 度就是全部信号（35N −3.29 / 总 −3.48 mm/day；40N −3.79 / 总 −5.27），
     * 在 50~60 度占 22~97%。⇒ 模型的涡动 MFC 形状【由掩码的梯度决定】，不是由涡动物理决定。
     *
     * <p><b>为什么是 ℓ_L = v*·τ_L</b>：Randel & Held 1991 —— 涡动在临界线（u = 相速）
     * <b>10~20 度外</b>才破碎；Lu et al. 2022 —— 混合长 = Rhines 尺度，实测 ℓ_L ≈ 700 km；
     * Caballero & Hanley 2012 式(20) —— ℓ_L = τ_L·v*，τ_L ≈ 0.9 天【就是本类已有的 EDDY_TAU】。
     * ⇒ 过渡的纬度宽度必须 ≥ 混合长，**零新常数**：v* = σ_Eady·L_d 已经在模型里。
     *
     * <p>量的核对（45N）：σ=5.78e-6、L_d=1.081e6 ⇒ v*=6.25 m/s、ℓ_L=486 km ≈ 4.4 度；
     * tanh 的 0.1→0.9 跨度 = 2.196·ℓ_L ≈ 9.6 度（文献要求 ≥6~9 度 ✓）。
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「默认 0」，但**实际默认已是 {@code 2}**
 * （见 :1263 与紧邻的「⚠⚠ §429 定案：切到 2」）。
     */
    // ⚠⚠ **§429 定案**：切到 2。理由：mode 0 的阈值 U0_STORM=5 m/s 在 JJA 的 u_zm 里**永远达不到**
    //   （JJA 峰值约 3.6 m/s）⇒ 0.5 交点被推到 41N，而 DJF 是 27.5N：**季节迁移里有一大半是假的**。
    //   mode 2 把下边界锁在 u_zm = 0 的**临界纬度**（模型自己的表给出迁移），过渡宽度取 **Rhines 混合长**，
    //   极侧边缘取 u_zm 回到 0 的交点 —— 全部由模型自己的场导出，零新常数。
    public static int EDDY_GATE_MODE = 2;

    /** 纬向风经向切变 |du_zm/dy|（1/s），中心差分 ±EDDY_DPHI_DEG。 */
    public static double uShearAbs(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
        double uN = ZonalTables.uZm(Math.toDegrees(latRad + d), theta);
        double uS = ZonalTables.uZm(Math.toDegrees(latRad - d), theta);
        return Math.abs(uN - uS) / (2.0 * d * WorldContract.R_EFF);
    }

    /** **拉格朗日混合长** ℓ_L = v*·τ_L（m），v* = σ_Eady·L_d。见 {@link #EDDY_GATE_MODE}。 */
    public static double mixingLength(double latRad, double theta) {
        return eadyGrowth(latRad, theta) * deformRadius(latRad, theta) * EDDY_TAU;
    }

    /**
     * **混合长宽度的涡动门**：0.5 交点与 {@link #stormGate} 严格同位置（都在 u_zm = U0_STORM/2，
     * 因为 smoothstep(0.5) = 0.5），只把过渡尺度从「阈值」换成「混合长的纬度宽度」。
     */
    public static double stormGateWide(double latRad, double theta) {
        double u = ZonalTables.uZm(Math.toDegrees(latRad), theta);
        double uHalf = 0.5 * U0_STORM;
        double uw = uShearAbs(latRad, theta) * mixingLength(latRad, theta);
        if (!(uw > 1.0e-3)) uw = 1.0e-3;
        return 0.5 * (1.0 + Math.tanh((u - uHalf) / uw));
    }

    /** 涡动链用的门：按 {@link #EDDY_GATE_MODE} 选。 */
    public static double gateOf(double latRad, double theta) {
        if (EDDY_GATE_MODE == 2) return stormGateCrit(latRad, theta);
        return (EDDY_GATE_MODE == 0) ? stormGate(latRad, theta) : stormGateWide(latRad, theta);
    }

    /**
     * **Rhines 尺度** L_R = sqrt(2 v* / beta)（m），v* = sigma_Eady * L_d。
     *
     * <p>Lu et al. 2022 逐字结论：the evidence strongly supports that the Rhines scale is the
     * energy-containing scale and hence behaves as the **mixing length** for the heat and moisture
     * transport in the storm track。45N 实测 L_R = 738 km = 6.6 度，正落在文献要求的 6~9 度。
     * beta = 2*Omega/R_eff 已经是 WorldContract 里的量 ⇒ **零新常数**。
     */
    public static double rhinesWidthRad(double latRad, double theta) {
        double vs = eadyGrowth(latRad, theta) * deformRadius(latRad, theta);
        double beta = 2.0 * WorldContract.OMEGA / WorldContract.R_EFF;
        return Math.sqrt(Math.max(1.0e-30, 2.0 * Math.abs(vs) / beta)) / WorldContract.R_EFF;
    }

    /**
     * **临界纬度**（rad，带符号）：从赤道向极扫描，u_zm 由【东风】转【西风】的第一个交点。
     * 找不到（整条剖面都是东风/西风）返回 {@code NaN}。
     */
    public static double criticalLatRad(double theta, boolean north) {
        double sgn = north ? 1.0 : -1.0;
        // ① 先在 |lat| 上找【急流纬度】（u_zm 最大处）—— 风暴轴的下边界是它的赤道侧边缘。
        double best = -1.0e9, latMax = 0.0;
        for (int i = 0; i <= 720; i++) {
            double d = i * 90.0 / 720.0;
            double u = ZonalTables.uZm(sgn * d, theta);
            if (u > best) { best = u; latMax = d; }
        }
        if (best <= 0.0) return Double.NaN;
        // ② 从急流向【赤道】扫，取第一个 u 由正变非正的交点。
        //    ⚠ 不能从赤道往极扫：JJA 在 8.5N 有一个【西风道】(u = +0.6 m/s)，
        //      那是热带现象，不是风暴轴边界（P638 第一版就栽在这里）。
        int n = (int) Math.ceil(latMax / 0.25);
        for (int i = 1; i <= n; i++) {
            double d = latMax - i * 0.25;
            if (d < 0.0) break;
            if (ZonalTables.uZm(sgn * d, theta) <= 0.0) {
                double lo = d, hi = d + 0.25;
                for (int k = 0; k < 40; k++) {
                    double mid = 0.5 * (lo + hi);
                    if (ZonalTables.uZm(sgn * mid, theta) > 0.0) hi = mid; else lo = mid;
                }
                return Math.toRadians(hi) * sgn;
            }
        }
        return Double.NaN;
    }

    /**
     * **临界纬度 + Rhines 宽度的涡动门**（`EDDY_GATE_MODE = 2`，设计冻结 §428）。
     *
     * <pre>
     *   gate = S( (|phi| - phi_c) / W ) * ( 1 - S( (|phi| - phi_p) / W ) ) ,  S = smoothstep
     *   phi_c = 赤道侧临界纬度；phi_p = 极侧边缘（都由 u_zm = 0 的交点给出，从急流向两侧扫）
     *   W     = L_R / R_eff（Rhines 混合长），在 phi_c + W/2 处自洽迭代两次
     * </pre>
     *
     * <p><b>为什么下边界必须是 phi_c 而不是「速度阈值」</b>（P636 实测）：mode 0 的 U0_STORM = 5 m/s
     * 在 **JJA 的 u_zm 里永远达不到**（JJA 峰值约 3.6 m/s）⇒ gate 在 JJA 全程 < 0.85，
     * 0.5 交点被推到约 41 度，而 DJF 是约 27.5 度：**季节迁移有 13.5 度，其中大部分是假的**
     * （真迁移由 u = 0 的临界纬度给出）。把下边界锁在 u = 0 之后，迁移完全由模型自己的 u_zm 表决定。
     */
    /**
     * **极侧边缘**（rad，带符号）：从急流向【极】扫，u_zm 由正变非正的交点。
     * 找不到（一直西风到 90 度）返回 {@code NaN} ⇒ 门在极侧不闭合。
     */
    public static double polarEdgeLatRad(double theta, boolean north) {
        double sgn = north ? 1.0 : -1.0;
        double best = -1.0e9, latMax = 0.0;
        for (int i = 0; i <= 720; i++) {
            double d = i * 90.0 / 720.0;
            double u = ZonalTables.uZm(sgn * d, theta);
            if (u > best) { best = u; latMax = d; }
        }
        if (best <= 0.0) return Double.NaN;
        int n = (int) Math.ceil((90.0 - latMax) / 0.25);
        for (int i = 1; i <= n; i++) {
            double d = latMax + i * 0.25;
            if (d > 90.0) break;
            if (ZonalTables.uZm(sgn * d, theta) <= 0.0) {
                double lo = d - 0.25, hi = d;
                for (int k = 0; k < 40; k++) {
                    double mid = 0.5 * (lo + hi);
                    if (ZonalTables.uZm(sgn * mid, theta) > 0.0) lo = mid; else hi = mid;
                }
                return Math.toRadians(hi) * sgn;
            }
        }
        return Double.NaN;
    }

    static double smoothstep01(double x) {
        x = x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
        return x * x * (3.0 - 2.0 * x);
    }

    public static double stormGateCrit(double latRad, double theta) {
        boolean north = latRad >= 0.0;
        double pc = criticalLatRad(theta, north);
        if (Double.isNaN(pc)) return 0.0;
        double ac = Math.abs(pc), a = Math.abs(latRad);
        if (a <= ac) return 0.0;
        double w = rhinesWidthRad(pc, theta);
        for (int k = 0; k < 2; k++) w = rhinesWidthRad(pc + 0.5 * w * (north ? 1.0 : -1.0), theta);
        w = Math.max(Math.toRadians(2.0), Math.min(Math.toRadians(20.0), w));
        double lo = smoothstep01((a - ac) / w);
        double pp = polarEdgeLatRad(theta, north);
        if (Double.isNaN(pp)) return lo;
        return lo * (1.0 - smoothstep01((a - Math.abs(pp)) / w));
    }

    /**
     * ★★★ §425：`columnWater` 的纬度依赖是【分段线性】的（表 5 度节点 + 线性插值）
     * ⇒ `curv(W)` = 二阶差分在**每个节点上是 ±2000~5400e-14、节点之间精确为 0**
     * （P632 实测）。而 `EDDY_DPHI_DEG = 5` 恰好落在节点上 ⇒ **`curv(W)` 拾取的全是折角伪影。**
     *
     * <p>后果：`modelShape`/`fluxAt` 的符号完全由「哪个节点恰好是折角」决定 ——
     * 这解释了 §235 记录的「`curv(W)` 在 30~45 度给正、峰在 44~52 度」、
     * §413 的 corr 只有 0.38/0.59、以及 P617「加大步长反而更差」。
     *
     * <p>修法：在 5 度节点上取 `columnWater`，再用 **Catmull-Rom** 做 C1 光滑重建。
     * 零新常数、局部、只影响涡动链。**默认 false ⇒ 走原路 ⇒ 逐位不变。**
     */
    public static boolean CW_SMOOTH = false;

    static double cwNode(int k, double theta) {
        int kk = k < 0 ? 0 : (k > 18 ? 18 : k);
        return columnWater(Math.toRadians(kk * 5.0), theta);
    }

    /** C1 光滑的柱水汽（Catmull-Rom 重建）。见 {@link #CW_SMOOTH}。 */
    static double columnWaterSmooth(double latRad, double theta) {
        double a = Math.abs(Math.toDegrees(latRad));
        double f = a / 5.0;
        int i = (int) Math.floor(f);
        double t = f - i;
        double p0 = cwNode(i - 1, theta), p1 = cwNode(i, theta);
        double p2 = cwNode(i + 1, theta), p3 = cwNode(i + 2, theta);
        return 0.5 * (2.0 * p1 + (-p0 + p2) * t
                    + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                    + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t * t * t);
    }

    /** 涡动链用的 W（`CW_SMOOTH` 时走光滑重建）。 */
    static double wOf(double latRad, double theta) {
        return CW_SMOOTH ? columnWaterSmooth(latRad, theta) : columnWater(latRad, theta);
    }

    /** W 的经向梯度。 */
    static double dWdy(double y, double theta, double d) {
        return (columnWater(y + d, theta) - columnWater(y - d, theta)) / (2.0 * d * WorldContract.R_EFF);
    }

    /** {@code MFC = gate * d(K*W')/dy}（掩码在外，§269.5）。 */
    static double modelShapeMasked(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double dy = d * WorldContract.R_EFF;
        double kp = kAt(c + d, theta), km = kAt(c - d, theta);
        double wp = dWdy(c + d, theta, d), wm = dWdy(c - d, theta, d);
        return stormGate(latRad, theta) * (kp * wp - km * wm) / (2.0 * dy);
    }

    /** {@code MFC = -dF/dy}（中心差分，步长 d = EDDY_DPHI_DEG）。 */
    static double modelShapeFull(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        return (fluxAt(c + d, theta, d) - fluxAt(c - d, theta, d)) / (2.0 * d * WorldContract.R_EFF);
    }

    public static double eddyMfc(double latRad, double theta) {
        if (!EDDY_PLACEMENT_FROM_OBS) {
            // §427：EDDY_VAR / EDDY_GRAD 非默认时才换路 ⇒ 默认路径一字不动（逐位不变）。
            if (EDDY_VAR != 0 || EDDY_GRAD != 0 || EDDY_GATE_MODE != 0) return modelShapeVarFull(latRad, theta);
            if (!EDDY_FULL_DIVERGENCE) return modelShape(latRad, theta);
            return EDDY_MASK_OUTSIDE ? modelShapeMasked(latRad, theta) : modelShapeFull(latRad, theta);
        }
        double a = Math.abs(Math.toDegrees(latRad));
        int k = (int) (a / 5.0);
        if (k > 17) k = 17;
        double tl = a / 5.0 - k;
        double[] ann = shapeAnn();
        // ---- A（§238）：放置、符号与**季节迁移**全部取自**逐月**观测剖面 ----
        //  为什么不再乘 modelShape/A：逐月剖面**已经含季节**，再乘一次就是重复计数。
        //  ⚠ 记账：gate * sigma^2 * L_d^2 * curv(W) 那一整套物理形式由此**降级为诊断量**
        //  （modelShape/shapeAnn 保留，供对照与回滚）。
        return EDDY_MFC_REF * ZonalTables.eddyMfcObsMonth(latRad, theta);
    }

    /**
     * **模型形状**（原 {@code eddyMfc} 的完整原式，一字未改）—— A-ii 只换它的**纬度放置**。
     *
     * <p>{@code A(|phi|) = mean_theta |modelShape|} 用它算：**按幅值归一**而不是按带符号的年均值，
     * 因为年均值在副热带会穿零（正负相消）⇒ 比值无界（§236.2）。
     */
    static double modelShape(double latRad, double theta) {
        double d = Math.toRadians(dphiDeg());
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

    /** 19 纬（0..90，每 5 度）的 {@code A(|phi|) = mean_theta |modelShape|}，**惰性算一次**（76 次求值）。 */
    private static volatile double[] SHAPE_ANN = null;
    private static double[] shapeAnn() {
        double[] t = SHAPE_ANN;
        if (t != null) return t;
        t = new double[19];
        for (int k = 0; k < 19; k++) {
            double lat = Math.toRadians(k * 5.0), s = 0.0;
            for (int q = 0; q < 4; q++) s += Math.abs(modelShape(lat, q * Math.PI / 2.0));
            t[k] = s / 4.0;
        }
        return SHAPE_ANN = t;
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

    /**
     * **候选 A（设计冻结 §244.5）：不让局地边界层散度抵消纬向平均上升支。**
     *
     * <p>{@code false}（默认）⇒ {@code wEff = w_zm + w_loc}，与接线前**逐位相同**（那一行一字未改）。
     * {@code true} ⇒ {@code wEff = max(0,w_zm) + max(0,w_loc)}：两项**分别取正再相加**。
     *
     * <p><b>为什么要试它</b>（§244.4 实测）：25° 海夏 {@code w_zm = +1.500e-3}、{@code w_loc = -1.836e-3}
     * ⇒ 相加为负 ⇒ {@code precip()} 直接返回 0 ⇒ 副热带海洋夏季在 98~100% 的格点上是**精确的 0**，
     * 而观测（GPCP+ETOPO1，25~40°N 海 JJA）= <b>2.356 mm/day</b>。
     * 这是全降水场里最大的单项误差。
     *
     * <p><b>已知局限</b>：它只在 {@code w_zm > 0} 的地方起作用（本世界约 5~27°），
     * 30~40° 那里 {@code w_zm < 0} ⇒ **A 治不了那一段**。它是一次「便宜的对照」，
     * 用来把「硬零」拆成「被抵消的」与「本来就下沉的」两部分。进 {@code configStamp()}。
     */
    public static boolean SPLIT_ASCENT = false;

    /**
     * **候选 R-2（设计冻结 §248.6 / §249）：q 改用局地真实地表温度。**
     *
     * <p>{@code false}（默认）⇒ 与接线前**逐位相同**（下面那个三元表达式的 else 支就是原变量）。
     * {@code true} ⇒ 在算 q 之前先减掉 {@code GAMMA*max(0,elev)*kappa}。
     *
     * <p><b>为什么</b>（§248.2 实测）：原式喂给 {@link #moisture} 的是**海平面等效温度**，
     * 而陆地上那个 {@code +GAMMA*z_bar_land} 修正在 30 度是 <b>+5.4 K</b>，把 qSat 抬了约 24%
     * ⇒ 陆地 q 反而比海洋高（16.0 vs 13.3 g/kg），与真实地球**反号**。
     * 水汽是由**有高度的真实地表**提供的，不是由「海平面上的温度」提供的。
     *
     * <p><b>海洋中性（逐位证明）</b>：P501 自检 [4] 断言
     * {@code surfaceTemp == tSl - GAMMA*max(0,elev)*kappa} 逐点逐位成立（残差 0.00e+00）
     * ⇒ {@code kappa = 0} 处两支完全相同 ⇒ **本开关不可能改变任何纯海洋点**。
     *
     * <p><b>实测标定（P501 v2，15~45 度）</b>：陆地 q 15.96 → <b>12.55 g/kg（-21.4%）</b>、
     * 陆地 RH 0.7843 → <b>0.5683</b>；海洋**逐位不变**。
     * ⚠ 它同时把陆地 E 从 10.23 推到 15.81 ⇒ **R-2 要与陆面湿润度因子 β 配对使用**
     * （实测 β = 0.60 把陆地 E 压回 2.10 mm/day）。**本切片不接线 E/β。**
     * 进 {@link com.EyeOfHarmonyBuffer.sim.runtime.SimClimate#configStamp()}。
     */
    public static boolean Q_AT_SURFACE_TEMP = false;

    /**
     * **候选 S-1（设计冻结 §251）：浅对流地板。**
     *
     * <p>⚠ <b>§533 修正（P1-10）</b>：本条曾写「{@code false}（默认）」，但**实际默认已是 {@code true}**
 * （见 :1611，2026-09-17 落地：① 0.0%→99.5%、⑤ 海洋地板 0.00→1.12，§252）。
     *
     * <p>为什么需要它：{@code precip()} 里那行 {@code if (wEff <= 0) return 0.0} 把
     * 「深对流触发条件」误当成了「降水存在条件」。真实副热带海洋**从不停止降水**
     * （GPCP+ETOPO1 实测 20~62.5N 海洋 JJA 最小值 = <b>1.84 mm/day</b>），
     * 而模型在 25~40 度海洋夏季 **98~100% 的格点是精确的 0**。
     * §245~§250 已逐条排除：调涡动参数（§247.4）、防空转（§245.5）、
     * {@code max(0,E+MFC)}（§244.5）、水汽松弛（§248.3）—— **保留 max(0,上升) 的任何形式都无法填这条地板**。
     *
     * <pre>
     *   P_sh = ALPHA_SH * E_sh / rho_w
     *   E_sh = rho_a * cdOf(kappa) * |V|_eff * max(0, (1-kappa)*qSat(T_sfc) - q)
     *   |V|_eff = sqrt(|V|^2 + V_GUST^2)
     *   P    = max(P_原有, P_sh)      ← **地板，不是加法**
     * </pre>
     *
     * <p>四个选择：① {@code (1-kappa)} = 湿表面分数（浅对流是湿表面现象，纯海洋 1 / 纯内陆 0，零阈值连续）；
     * ② {@code max(0, ...)} 自动把陆地上的地板关掉（陆地 q 本来就高），**不需要分支**；
     * ③ {@code max} 而不是加法 —— 加法会把**已经太湿**的中纬 45 度海洋推得更湿；
     * ④ {@code V_GUST} 取 L-3 的 QTCM 值 4 m/s。
     */
    public static boolean SHALLOW_FLOOR = true;   // 2026-09-17 落地：① 0.0%->99.5%、⑤ 海洋地板 0.00->1.12（§252）
    /** 浅对流效率（由判据①的观测锚反解；见 §251.2）。 */
    public static double ALPHA_SH = 0.40;
    /** 地表通量的风速下限（m/s）—— L-3（QTCM `VVsmin = 4.0`）。没有下限，季风反转点通量归零。 */
    public static double V_GUST = 4.0;

    /** 有效上升速度（m/s）：纬向平均上升支 + 局地辐合。 */
    /**
     * ★ A/B 开关（§406）：{@code wZm} 的【升降边界】是否也跟着 ITCZ 平移。
     *
     * 物理疑问：ITCZ 迁移是真的，但 **Hadley 胞边界**迁移得远比 ITCZ 小；
     * 现在两者共用同一个 {@code precipSubsolarLat(theta)} 平移，于是 JJA 的升降边界被从
     * 18.75 度推到 25.7 度 —— **撒哈拉（20~30N）因此整片落进上升区**。
     * 默认 true ⇒ 逐位不变。
     */
    public static boolean WZM_ITCZ_SHIFT = true;

    /**
     * ★★★ **§436：纬向平均上升支是否改用【观测月表】**（用户裁决 A'）。
     *
     * <p>⚠⚠ <b>§436 实测后【默认撤回 false】</b>：单独打开它是**净伤害** ——
     * JJA 亚洲 2.972 → <b>0.222</b>、撒哈拉 3.054 → 0.168，年比 0.81 → <b>0.38</b>（更差）。
     * **原因是一个更根本的事实**：观测的**纬向平均** omega 里**根本没有季风** ——
     * 季风是 70~120E 的**区域**现象，纬向平均把它与太平洋高压一起平均掉了（15~35N 的 JJA 纬向平均是下沉）。
     * 旧的**手定表 + ITCZ 平移恰好在替这个缺失的区域机制顶班**（这也是它注释里自称「只能自己定的第一名」的真实含义）。
     * ⇒ 本表**在物理上是更正确的纬向平均**，但它替换掉了一个**补偿性错误**，而补偿的对象还没有被实现。
     * ⇒ **必须先有区域（2-D）机制（S2 定常波，或季风指数），才能采纳它。** 表与开关保留供 A/B。
     *
     * <p>{@code true} = {@link ZonalTables#wZmMonth}（NCEP/NCAR R1 日均 omega500 的月平均）。
     * {@code false} = 旧路：手定 10 节点无季节的 {@link ZonalTables#W_ZM}，再按 {@link #WZM_ITCZ_SHIFT} 叠加人工平移。
     *
     * <p><b>为什么必须改（§435 实测）</b>：手定表与观测月表逐点差（1e-3 m/s）
     * <pre>
     *   lat      0      10     20     30     40      50     60
     *   旧表   +5.000 +3.500 -0.500 -1.200 +0.200 +1.500 +1.000
     *   DJF    +2.053 +1.805 -1.343 -2.177 -1.525 +0.710 +0.941
     *   JJA    +1.140 +1.807 -1.415 -2.040 +0.052 -0.129 +1.100
     * </pre>
     * ① **40N 在 DJF 符号错**；② **副热带下沉低估约 2 倍**（20/30N，正是撒哈拉的水汽侧驱动量）；
     * ③ **50N 在 JJA 也反号**；④ 旧表 50~60 度的正值自述是「涡动 MFC 的替身」，
     * 而涡动项现在已显式计算 ⇒ **重复计数**（观测表里那段的正值是真实的平均上升，不是替身）。
     *
     * <p><b>⚠ 不再叠加人工 ITCZ 平移</b>：观测月表自带真实的季节迁移，再乘一次就是重复计数。
     * （同时记账：观测的副热带下沉最小纬度**全年都在 25N** ⇒ 观测的下沉带几乎不迁移，
     *  所以本开关修的是**量级与 40N 的符号**，不是迁移。见 §435。）
     */
    public static boolean WZM_FROM_TABLE = false;   // §436：默认撤回，理由见上

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
        // ★ S1 接线（设计冻结 395）：把查表的 W_ZM 换成 Held-Hou 【求解】出来的形状。
        //   为什么：原表的升降边界固定在 18.75°，再被 +10° 平移推到 28.75°N，
        //   于是 20~30N 的撒哈拉带被判成上升。理论本身给出 phi_0 = phi_H/sqrt5，
        //   用模型自己的 T_E 算得 phi_H=18.64°、phi_0=8.34° => 20~30N 是下沉。
        //   默认 ENABLED=false => 走原路 => 逐位不变。
        double wLat = WZM_ITCZ_SHIFT ? shifted : Math.toDegrees(latRad);
        // §436：改用观测月表时【不叠加】人工平移（表自带迁移）；默认 true ⇒ 走上一支。
        double wBase = WZM_FROM_TABLE ? ZonalTables.wZmMonth(Math.toDegrees(latRad), theta)
                     : (HadleyCell.ENABLED ? HadleyCell.wZmSolved(wLat) : ZonalTables.wZm(wLat));
        DIAG.get()[4] = wBase;
        if (!SPLIT_ASCENT) return wBase + wLoc;
        return Math.max(0.0, wBase) + Math.max(0.0, wLoc);
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
        double k = Atmosphere.kappaMemo(x, z, seed, cell);   // ★ §373：memo 关时等价回落，逐位相同
        DIAG.get()[7] = k;
        double elev = PlateField.elevationWithCell(x, z, seed, cell);
        // ⚠ §216.7 口径统一：这里要的是「海平面等效年均温度 + 季节项 + SST'」，
        //   与 Atmosphere.surfaceTemp **同源**（surfaceTemp 只多一个 -Γ*h*k）。
        double tSl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z, theta))
                   + Atmosphere.seasonalAnomaly(lat, k, theta);
        double[] u0 = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double hUp = upwindElev(x, z, seed, cell, u0[0], u0[1]);
        double depl = depletion(hUp, k);
        DIAG.get()[11] = depl;
        // R-2：Q_AT_SURFACE_TEMP=false 时 tQ == tSl ⇒ 下面一行与接线前**逐位相同**。
        double tQ = Q_AT_SURFACE_TEMP
                  ? tSl - Atmosphere.GAMMA * Math.max(0.0, elev) * k
                  : tSl;
        // ★ §405 S3 接线：只在这一次 moisture() 调用周围设置 beta，之后立刻还原
        //   （自旋中不设 —— 那时 beta 由 SoilMoisture 自己显式给出，设了会覆盖它、导致递归）。
        Double savedBeta = BETA_OVERRIDE.get();
        double betaUsed = 1.0;
        if (SoilMoisture.ENABLED && !SoilMoisture.isSpinningUp()) {
            betaUsed = SoilMoisture.betaAt(x, z, seed, cell, theta, gradStep);
            BETA_OVERRIDE.set(betaUsed);
        } else if (savedBeta != null) {
            betaUsed = savedBeta;
        }
        double q;
        if (Q_ADVECT_BUDGET && k > 0.5) {
            // §473：平流口径（只在陆地；海洋走原式）
            double[] adv = moistureAdvected(x, z, seed, cell, theta, gradStep, u0[0], u0[1]);
            if (adv[0] >= 0.0) {
                q = adv[1] * depl;
            } else {
                // 找不到海：按【最大距离】处理（§444 第 40 条：回落必须走同一个物理，不许退回旧口径）
                q = moistureFromSource(x, z, seed, cell, theta, gradStep, tQ, k > 0.0 ? hUp : 0.0, k, u0[0], u0[1]);
            }
        } else {
            q = Q_FROM_SOURCE
              ? moistureFromSource(x, z, seed, cell, theta, gradStep, tQ, k > 0.0 ? hUp : 0.0, k, u0[0], u0[1])
              : moisture(tQ, k > 0.0 ? hUp : 0.0, k);
        }
        if (savedBeta == null) BETA_OVERRIDE.remove(); else BETA_OVERRIDE.set(savedBeta);
        DIAG.get()[5] = q; DIAG.get()[6] = tSl; DIAG.get()[13] = betaUsed;
        double[] ux = Atmosphere.windAt(x + gradStep, z, seed, cell, theta, gradStep);
        double[] uw = Atmosphere.windAt(x - gradStep, z, seed, cell, theta, gradStep);
        double[] un = Atmosphere.windAt(x, z + gradStep, seed, cell, theta, gradStep);
        double[] us = Atmosphere.windAt(x, z - gradStep, seed, cell, theta, gradStep);
        double divU = (ux[0] - uw[0]) / (2.0 * gradStep) + (un[1] - us[1]) / (2.0 * gradStep);
        DIAG.get()[0] = divU;                       // 诊断：原始 divU（只来自 cellPressure）
        // ★ S2 接线（设计冻结 393）：把【定常波】的辐合场 div(V) 叠进来。
        //   为什么：现在 divU 完全来自 cellPressure，而 352/356 已实测那是个【陆海开关】
        //   （所有陆地拿到辐合、所有海洋拿到辐散），不是季风/沙漠判别器。
        //   393 实测：Q 取【降水型】时，求解器给出的正是【亚洲辐合 / 撒哈拉辐散】。
        //   默认 ENABLED=false => 整段跳过 => 逐位不变。
        //   isSolving() 守卫切断「求解器建强迫场 -> mmPerDay -> 再求解」的递归。
        double dWave = 0.0;
        if (StationaryWave.ENABLED) {
            // 递归守卫：求解自身建强迫时会再进 mmPerDay，这里不能再调 ensureSolved。
            // 但 divAt 必须【照常调用】—— 闭环正是靠"求解期间读上一轮的场"来闭合反馈的。
            if (!StationaryWave.isSolving()) StationaryWave.ensureSolved(seed, cell, theta, gradStep);
            dWave = StationaryWave.divAt(x, z);
            divU += dWave;
        }
        DIAG.get()[1] = dWave;                      // 诊断：叠加进来的 div(V)
        DIAG.get()[2] = divU;                       // 诊断：叠加后的 divU
        // ★ 真正的诊断 w：由【总散度】直接算，不经过任何降水参数化。
        //   单位与量级：w_mid = -(H_EFF/pi)*D，D<0（辐合）=> w>0（上升）。
        //   存在的理由：`wEff` 是【降水驱动量】（含 EPS_C/W_LOC_MAX 等调出来的系数），
        //   拿它对 ω500 是口径错误（§418 实测赤道差 2.7~4.8 倍，但那不能判模型错）。
        DIAG.get()[14] = -(Atmosphere.H_EFF / Math.PI) * divU;
        // 平均经向环流 + 局地辐合（原式，未改动）
        // §480：配对（WZM_FROM_QNET + Q_FROM_BLBUDGET）时，纬向平均项换成 +H_bl*F_net/M。
        double wE;
        if (WZM_FROM_QNET) {
            double chvQ = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
            double decQ = Atmosphere.subsolarLat(theta);
            double asrQ = Radiation.insolation(lat, decQ) * (1.0 - Radiation.albedo(k > 0.5, tQ));
            wE = wEffQnet(lat, theta, divU, q, tQ, depl, k,
                          SoilMoisture.ENABLED ? betaUsed : 1.0, chvQ, asrQ);
        } else {
            wE = wEff(lat, theta, divU);
        }
        DIAG.get()[3] = wE;                         // 诊断：wEff
        // ★★★ §472：边界层水汽【收支口径】。E = P + V ⇒ q 的闭式解（无新常数、零迭代）。
        //   为什么必须：§470 量到 q_BL 是【自由参数】（RH_SEA*qSat），换个水汽方案就换个数；
        //   而 §471 实测收支口径让毛湿稳定度 M 在两盒都变正 ⇒ 甲（F_net/M）才可用。
        //   q* = 86400*chv*qSat(Ts)*beta / (86400*chv + k_P + C*(1-qftF))
        //     C = 86400*rho*|w_BL|,  w_BL = -H_BL*divU,  qftF = exp(-(H_EFF/2)/H_MOIST)
        //   Ts 用 tQ（= tSl，与现口径一致；皮温那一步是 60 次二分，这里不引入）。
        if (Q_FROM_BLBUDGET) {
            double qsTsB = qSat(tQ);
            double chvB = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
            double Cb = 86400.0 * RHO_AIR * Math.abs(-Atmosphere.H_BL * divU);
            double kPb = (wE > 0.0) ? EPS_C * RHO_AIR * wE / RHO_WATER * 86400.0 * 1000.0 : 0.0;
            double qftF = Math.exp(-(0.5 * Atmosphere.H_EFF) / H_MOIST);
            double denB = 86400.0 * chvB + kPb + Cb * (1.0 - qftF);
            if (denB > 1.0e-12) q = 86400.0 * chvB * qsTsB * betaUsed * depl / denB;
        }
        double p = precip(q, wE);
        // ★★★ §496：BLQ 对流判据（默认关 ⇒ 逐位不变）。
        if (BLQ_GATE) {
            double tSfc = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
            double tTh = tSfc - Atmosphere.GAMMA * Atmosphere.H_BL;
            double hBl = Radiation.CP * tSfc + Radiation.LV * q;
            double hTh = Radiation.CP * tTh + G_ACC * Atmosphere.H_BL + Radiation.LV * qSat(tTh);
            double dh = hBl - hTh;
            blqLastDh = dh; blqCalls++;
            double g = smoothstep01b(dh / (Radiation.CP * BLQ_SMOOTH_K));
            if (g < 0.01) blqBlocked++;
            p *= g;
        }
        DIAG.get()[8] = p;
        // 风暴轴：瞬变斜压涡动的水汽通量辐合。
        // 只取辐合侧（mfc>0）：辐散侧（副热带）的「变干」已经由 w_zm 的下沉支表达，
        // 再减一次就是重复计数。
        if (withEddy) {
            double mfc = eddyMfc(lat, theta);
            if (mfc > 0.0) p += mfc * depl * depletionCol(hUp, k) / RHO_WATER;
        }
        DIAG.get()[9] = p;
        // ---- 候选 S-1：浅对流地板（§251）。SHALLOW_FLOOR=false 时整段被跳过 ⇒ 逐位不变。----
        if (SHALLOW_FLOOR) {
            double tSfcF = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
            DIAG.get()[12] = tSfcF;
            double qsSfcF = qSat(tSfcF);
            double spF = Math.hypot(u0[0], u0[1]);
            double vEff = Math.sqrt(spF * spF + V_GUST * V_GUST);
            double betaF = 1.0 - Atmosphere.clamp01(k);
            double eSh = RHO_AIR * Atmosphere.cdOf(k) * vEff * Math.max(0.0, betaF * qsSfcF - q);
            double pSh = ALPHA_SH * eSh / RHO_WATER;
            DIAG.get()[10] = pSh;
            if (pSh > p) p = pSh;
        }
        double pMm = p * 86400.0 * 1000.0;
        // ★ §462：植被的【降水反馈通道】 P_eff = P_d + D_B*V（文献：Liu et al. 2006a; Claussen et al. 2013）。
        //   ABI 是非对称的：若本线程正在解 V（V_OVERRIDE 非 null），**直接读覆盖值** ⇒ 绝不重入 vegAt。
        if (Vegetation.ENABLED) {
            double[] ov = Vegetation.V_OVERRIDE.get();
            double vv;
            if (ov != null) {
                vv = ov[0];
            } else {
                final double pdHere = pMm;
                vv = Vegetation.vegAt(x, z, seed, cell, v2 -> pdHere);
            }
            pMm += Vegetation.D_B * vv;
            DIAG.get()[15] = vv;
        }
        return pMm;
    }
}
