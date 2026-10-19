package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TalosLandErosion;

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
     * [4] wBase（纬向平均上升支：默认取 ZonalTables.wZm；HadleyCell.SOLVED_ON 时取求解值）
     * [5] q 比湿  [6] tSl 海平面等效温度  [7] kappa  [8] precip 主项(m/s)
     * [13] beta（土壤湿度桶给出的表面湿润度；未启用时恒 1.0）
     * [14] wDiag = -(H_EFF/pi)*(divU + div(V)) —— **真正的诊断垂直速度**（m/s），
     *      由散度直接算，不经 `EPS_C`/`W_LOC_MAX` 那一链 ⇒ 这才是能与 ω500 对比的量。
     * [9] 加完涡动后的 p(m/s)  [10] 浅对流地板(m/s)  [11] depl  [12] 地表温度(K)
     * —— 这一组是「为什么这个点这么湿/干」的分解，用于 §402 的撒哈拉恒湿归因。
     * 为什么要它：Q_SCALE 的标定目标是「div(V) 的 RMS 与模型的 divU 同量级」，
     * 而 divU 是 mmPerDay 的局部变量、外部拿不到 —— 没有它就只能靠猜量级。
     */
    public static final ThreadLocal<double[]> DIAG = ThreadLocal.withInitial(() -> new double[19]);
    // ★ 2026-10-16（goal-3355454e）：16 -> 17，新增 [16] = 模型自己的**地表蒸发 E** [kg/(m^2 s)] ✓
    //   用途：收支闭合 P = E - ∂W/∂t - ∇·(qV)（Banacos & Schultz 2005 Eq.(6) ✓）
    //   只加一格、只多写一个值 ⟹ **不改任何结果** ✓（诊断数组 ✓）   // §462: [15]=V

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

    /** ★ 2026-10-16（goal-3355454e）：**观测的空气相对湿度**（内带 35~50S 年均）。
     *  出处（本仓）：refs/e_obs_profile.npz 的 qa/qs = 0.00763/0.01009 = 0.756（年均）；
     *  本常数取其四舍五入 0.748（同一文件的 RH 场年均 0.7478）。**观测值，不是拟合值。** */
    public static final double RH_AIR_OBS = 0.748;

    /** ★ 2026-10-16（goal-3355454e）：**水汽口径开关**。
     *  {@code false}（默认）⇒ {@code moisture()} 走 {@code RH_SEA*qSat(tSfcSl)}，与接线前**逐位相同**。
     *  {@code true} ⇒ 走**空气口径** {@code RH_AIR_OBS*qSat(T_a)}，其中
     *  {@code T_a = airTempK(tSfcSl, lat, kappa) = tSfcSl + (1-land)*ZonalTables.dtAirSea(lat)}
     *  —— 与 {@link #airTempK} 同一套（本仓既有的、观测的海气温差表）。
     *
     *  <p><b>为什么这不是 §646/§648 否掉的那件事</b>：§648 的论证是「{@code RH_SEA = 0.80}
     *  按定义是**海面**相对湿度 ⇒ 乘 {@code qSat(T_a)} 是**口径错配**」—— 那个论证**是对的**。
     *  本开关**不**用海面 RH 配气温，而是换成**空气口径的一对观测量**：
     *  空气相对湿度 {@link #RH_AIR_OBS} 配空气温度 {@code T_a}，两者同口径。
     *
     *  <p>实测依据（目标档 §88）：内带 50~35S 的湿度亏缺，海表锚给 DJF-high（JJA/DJF 0.936），
     *  气温锚给 **JJA-high（1.051）**，而观测降水（GPCP）是 JJA-high（1.266）。符号因此翻正。 */
    public static boolean Q_AIR_ANCHOR = true;

    /** ★ 2026-10-16（goal-8b827fd3）：空气口径支路是否**同时尊重**桶的状态（{@code BETA_OVERRIDE}）。
     *  <p>默认 <b>false</b> ⟹ 逐位不变 ✓（走下面的原返回 ✓）。
     *  <p>为什么需要：`Q_AIR_ANCHOR` 的支路**提前返回** ✗ ⟹ `BETA_OVERRIDE` 失效 ✗ ⟹
     *  生产 P 对 RS/beta 完全不敏感（实测见调用点注释 ✓）。
     *  <p>形式：`q = RH_AIR_OBS * (rhEff(bo)/RH_SEA) * qSat(tAir) * depl` —— 用的是**已有**的量 ✓，
     *  **零新常数** ✓。 */
    public static boolean Q_AIR_ANCHOR_BETA = true;   // 先出数   // A/B combo

    /** 该点纬度（**度**）—— 仅当 {@link #Q_AIR_ANCHOR} 打开时由 {@code mmPerDay} 提供，
     *  用完立刻清除（与 {@link #BETA_OVERRIDE} 同一套 ThreadLocal 机制）。 */
    public static final ThreadLocal<Double> Q_AIR_LAT = new ThreadLocal<>();
    /** 过山损耗尺度（m）。 */
    public static double H_MOIST = 2000.0;
    /**
     * **ITCZ / 气压带的迁移幅度**（弧度）。真实 ITCZ 只迁移 5~10 度，不是太阳直射点的 23.44 度
     * （第一跑直接用直射点纬度 ⇒ 降水最大值被推到 22 度，错了）。
     */
    public static double ITCZ_MIGRATION = Math.toRadians(10.0);
    /** 取「上风方向」地形的步长（m）：雨影必须用上风地形，不能用本地地形。 */
    public static double UPWIND_STEP = 3_750.0;   // ★ 缩 40x（原 150,000）

    /** 用于降水的「有效直射点纬度」：按 ITCZ_MIGRATION 缩放。 */
    public static double precipSubsolarLat(double theta) {
        double f = Atmosphere.OBLIQUITY == 0 ? 0 : ITCZ_MIGRATION / Atmosphere.OBLIQUITY;
        return Atmosphere.subsolarLat(theta) * f;
    }
    /**
     * 边界层厚度（m），{@code w_loc = -H_bl*div(U)} 用。**单源**：直接引用 {@link Atmosphere#H_BL}。
     *
     * <p>⚠ 2026-10-14 审查：本字段上方原有**两份** javadoc 紧挨着（前一份也写「边界层厚度（m），
     * w_loc = -H_bl*div(U) 用。」）—— 内容等价，但 Java 只认**最后一份** ⟹ 前一份
     * 【写了但无效】✗ ⟹ 已合并为这一份 ✓。
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
    /**
     * ★ §7303：求【散度】用的【外层】差分步长（blocks；0 = 关，用 `gradStep` ⇒ 逐位不变）。
     *
     * <p>为什么需要（本会话 P1047/P1048 实测）：
     * `gradStep` 默认 500 km = 4.5°经度。而**风场本身是长波主导的**
     * （`u`/`v` 的 k1-4 占 0.56~0.89），**但 500 km 差分后 `divU` 变成短波主导**
     * （k10-18 占 0.40~0.69）—— 因为一阶导数的谱权重是 `k`，短波被系统性放大。
     * 把外层步长放到 **2000 km = 18°**（低通到波长 >= 36°）后，`divU` 回到长波主导
     * （0.64~0.82），且**季风/沙漠的 `wLoc` 符号在 20/25/30N 上从 1/3 变成 3/3**，
     * 在 2000~4000 km 上稳定。
     *
     * <p><b>选值规则（不是扫描出来的数）</b>：步长必须大到使 `divU` 的谱由长波主导。
     * 该规则**可验证**（看谱），而不是「结果好不好」。
     *
     * <p>⚠ 坐标换算：`CIRC = 40_000_000` blocks ↔ 360°，Minecraft 一格 ~ 1 m
     * ⇒ 世界周长 ~ 40 000 km（与地球同量级）⇒ 500 km = 4.5°、 2000 km = 18°。
     */
    public static int DIV_OUTER_STEP = 0;

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
    public static double EDDY_DPHI_DEG_V = 15.0;   // S619: P617 scan gave best sign agreement at 15 deg (30/34 vs 25/34 at 5 deg)

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
     * @param tSfcSl 该点的**海平面**温度（K）—— 陆地要先加回递减率。
     *               ⚠ 2026-10-19：{@code PRECIP_USE_SURFACE_T=true} 时 {@code mmPerDay} 改传
     *               **实际地表温度** {@code Atmosphere.surfaceTemp}（海拔递减已减掉）—— 见该开关的 javadoc。
     * @param elev   海拔（m）
     * @param kappa  大陆度 [0,1]
     */
    public static double moisture(double tSfcSl, double elev, double kappa) {
        // ★ §405：beta 为 null 时 rh == RH_SEA，与接线前【逐位相同】（同一操作数顺序）。
        Double bo = BETA_OVERRIDE.get();
        // ★ 2026-10-16（goal-3355454e）：水汽口径开关。默认 false ⇒ 下面一行一字未改（逐位相同）。
        Double qa = Q_AIR_LAT.get();
        if (Q_AIR_ANCHOR && qa != null) {
            double tAir = airTempK(tSfcSl, Math.toRadians(qa), kappa);
            // ★ 2026-10-16（goal-8b827fd3）：让空气口径也尊重桶的状态。默认 false ⟹ 逐位不变 ✓。
            //   实测（探针 P1030 ✓）：本支路提前返回 ⟹ BETA_OVERRIDE 失效 ✗ ⟹ 生产 P 对 RS/beta
            //   **完全不敏感** ✗（Q_AIR_ANCHOR=false 时 P 随 RS 变 4.58855→4.59121 ✓；
            //   true 时四个臂全为 5.28778 ✗）。
            //   修法：用**已有的** rhEff/RH_SEA 作比例 ✓，**不引入任何新常数** ✓。
            if (Q_AIR_ANCHOR_BETA && bo != null) {
                double ratio = SoilMoisture.rhEff(bo) / RH_SEA;
                return RH_AIR_OBS * ratio * qSat(tAir) * depletion(elev, kappa);
            }
            return RH_AIR_OBS * qSat(tAir) * depletion(elev, kappa);
        }
        double rh = (bo == null) ? RH_SEA : SoilMoisture.rhEff(bo);
        return rh * qSat(tSfcSl) * depletion(elev, kappa);
    }

    // ==================== §648 w_*：浅对流速度尺度（零行为改动新增） ====================

    /** 虚温系数 {@code R_d/R_v - 1}。 */
    public static final double EPS_V = 0.608;

    /**
     * **近地面空气温度 `T_a`（K）** —— 与表面温度 `T_s` 分开。
     *
     * <p>⚠ <b>方向（易错，冻结 §646）</b>：{@code Atmosphere.surfaceTemp} 在海洋上**就是海表温度**
     * `T_s`，而观测海气温差 `DT = T_air - T_sea` **为负**（空气比海冷）⇒ 海表比空气**暖**
     * ⇒ 表面浮力通量为**正**（不稳定）。所以 {@code T_a = T_s + DT}，**不是** `T_s = T_a + DT`。
     *
     * <p>`(1 - kappa)` 把海洋锚按大陆度线性淡出：纯陆地 (`kappa = 1`) 时 `T_a = T_s`。
     * 陆地侧的皮温另有 {@link Radiation#skinTempLand} 机械，本函数**不**涉及（记 OPEN）。
     */
    public static double airTempK(double tSfc, double latRad, double kappa) {
        double land = kappa < 0.0 ? 0.0 : (kappa > 1.0 ? 1.0 : kappa);
        return tSfc + (1.0 - land) * ZonalTables.dtAirSea(Math.toDegrees(latRad));
    }

    /**
     * **表面浮力（虚位温）通量 `(w'theta_v')_0`（K m/s）** —— 浅对流速度尺度 `w_*` 的驱动。
     *
     * <pre>
     *   (w'theta_v')_0 = C_H * |V| * [ theta_v(T_s, q_sat(T_s)) - theta_v(T_a, q) ]
     *   theta_v(T, q)  = T * (1 + 0.608 q)
     * </pre>
     *
     * <p><b>为什么必须拆 `T_a` 与 `T_s`</b>（§639 定性、P966 实测）：只有一个温度时上式退化成
     * `C_H|V| * 0.608 * T * (q_sat(T_s) - q)`，**饱和极限恒为零**（152/152 样本实测
     * `flux &lt;= 0`）⇒「饱和的湿边界层反而不产生对流」，物理上是反的。拆开后保留
     * **与 `q` 无关**的热力项 `(T_s - T_a)(1 + 0.608 q)`，饱和极限为正。
     *
     * <p>⚠ 本函数**不动** {@link #moisture}：该函数是 `RH_SEA * qSat(tSfcSl) * depletion(...)`，
     * 其 `RH_SEA = 0.80` 是**海表**相对湿度（§636 口径），所以空气湿度 `q` 的正确锚是
     * **表面**温度 `T_s` 而不是 `T_a`。§646 曾写「`moisture` 一侧改用 `T_a`」—— **那是错的，
     * 见 §648**。
     *
     * @param tSfc 表面温度 `T_s`（K）—— 海洋 = 海表，陆地 = 海平面等效陆面温度
     * @param qSfc 表面饱和比湿 `qSat(T_s)`（kg/kg）
     * @param qAir 空气比湿 `q`（kg/kg），即 {@link #moisture} 的返回值
     * @param vEff 有效风速 `|V|`（m/s，调用方负责带上 {@link #V_GUST}）
     */
    public static double surfaceBuoyancyFluxK(double tSfc, double qSfc, double qAir,
                                              double latRad, double kappa, double vEff) {
        double tAir = airTempK(tSfc, latRad, kappa);
        double thvS = tSfc * (1.0 + EPS_V * qSfc);
        double thvA = tAir * (1.0 + EPS_V * qAir);
        return Atmosphere.cdOf(kappa) * vEff * (thvS - thvA);
    }

    /**
     * **对流速度尺度 `w_*`（m/s）** —— Deardorff velocity。
     *
     * <p>定义式（出处：**AMS Glossary of Meteorology, 「Deardorff velocity」**，
     * 公式图 `Ams2001glos-De7.gif` 本地副本在 `build/eoh_probe/refs/`，两次独立视觉转写一致）：
     * <pre>    w_* = [ (g / T_v) * z_i * (w'theta_v')_0 ] ^ (1/3)</pre>
     * 词条符号表：`z_i` = **average depth of the mixed layer** ⇒ 本模型取
     * {@link Atmosphere#H_BL}（= 1000 m，本来就是 `gamma_turb = C_D*|U|/H_BL` 的尺度）；
     * `(w'theta_v')_0` = **kinematic vertical turbulent flux of virtual potential temperature
     * near the surface** ⇒ {@link #surfaceBuoyancyFluxK}。**式内无任何经验常数**
     * （`a = 0.3` 属于 `M_u = a*w_*` 的质量通量闭合，不是本式的一部分）。
     *
     * <p>通量非正（稳定层结）时返回 `0`：`w_*` 按定义只对**对流**混合层成立。
     */
    public static double wStarK(double tSfc, double qSfc, double qAir,
                                double latRad, double kappa, double vEff) {
        double flux = surfaceBuoyancyFluxK(tSfc, qSfc, qAir, latRad, kappa, vEff);
        if (flux <= 0.0) return 0.0;
        return Math.cbrt((G_ACC / tSfc) * Atmosphere.H_BL * flux);
    }

    /**
     * **陆地侧的近地面空气温度 `T_a`（K）** —— §650 的**闭式反解**，O(1)，无迭代。
     *
     * <p>用的是模型**自己**的能量平衡（{@link Radiation#residual}）：
     * <pre>    residual = absSolar - OLR(T_s) - H - LE ,   H = chv*CP*(T_s - T_a)</pre>
     * `H` 逐字可逆 ⇒ 不需要 `Radiation.skinTempLand` 的 60 次二分：
     * <pre>    T_a = T_s - ( absSolar - OLR(T_s) - LE(T_s) ) / ( chv * CP )
     *     OLR(T_s) = EPS*SIGMA*T_s^4
     *     LE(T_s)  = beta*chv*LV*max(0, qSat(T_s) - qAir)</pre>
     *
     * <p><b>方向自检</b>：强日照 + 干地表（`beta -> 0`）⇒ 括号内为正 ⇒ `T_a &lt; T_s`
     * ⇒ 皮温比空气热 ⇒ 浮力通量为**正**；湿地表 ⇒ `LE` 大 ⇒ `T_a &gt;= T_s` ⇒ 稳定。
     * 与 `Radiation` 已记录并实测的行为一致（`beta -&gt; 0` ⇒ 皮温升高 ⇒ `H` 翻正，
     * P570 实测在 `beta ~ 0.3` 处翻正）。**不是新闭合。**
     *
     * <p>`chv &lt;= 0` 时返回 `T_s`（无湍流交换 ⇒ 无感热 ⇒ 两者不可分）。
     */
    public static double landAirTempK(double tSfc, double absSolar, double qAir, double beta,
                                      double kappa, double vEff) {
        double chv = Atmosphere.cdOf(kappa) * vEff;
        if (!(chv > 1.0e-12)) return tSfc;
        double t2 = tSfc * tSfc;
        double olr = Radiation.EPS * Radiation.SIGMA * t2 * t2;
        double le = beta * chv * Radiation.LV * Math.max(0.0, qSat(tSfc) - qAir);
        return tSfc - (absSolar - olr - le) / (chv * Radiation.CP);
    }

    /**
     * **统一口径的空气温度 `T_a`（K）** —— 按大陆度把海洋锚与陆地反解线性混合。
     *
     * <p>海洋侧 `T_a = T_s + DT_AIR_SEA(lat)`（观测锚，§645）；陆地侧 {@link #landAirTempK}
     * （能量平衡闭式反解，§650）。`kappa = 0` 取纯海洋，`kappa = 1` 取纯陆地。
     */
    public static double airTempK(double tSfc, double absSolar, double qAir, double beta,
                                  double latRad, double kappa, double vEff) {
        double land = kappa < 0.0 ? 0.0 : (kappa > 1.0 ? 1.0 : kappa);
        double taOcean = tSfc + ZonalTables.dtAirSea(Math.toDegrees(latRad));
        if (land <= 0.0) return taOcean;
        double taLand = landAirTempK(tSfc, absSolar, qAir, beta, kappa, vEff);
        return (1.0 - land) * taOcean + land * taLand;
    }

    /** {@link #surfaceBuoyancyFluxK} 的**全口径**版本：`T_a` 由 {@link #airTempK} 的统一口径给出。 */
    public static double surfaceBuoyancyFluxK(double tSfc, double qSfc, double qAir, double absSolar,
                                              double beta, double latRad, double kappa, double vEff) {
        double tAir = airTempK(tSfc, absSolar, qAir, beta, latRad, kappa, vEff);
        double thvS = tSfc * (1.0 + EPS_V * qSfc);
        double thvA = tAir * (1.0 + EPS_V * qAir);
        return Atmosphere.cdOf(kappa) * vEff * (thvS - thvA);
    }

    /** {@link #wStarK} 的**全口径**版本。 */
    public static double wStarK(double tSfc, double qSfc, double qAir, double absSolar,
                                double beta, double latRad, double kappa, double vEff) {
        double flux = surfaceBuoyancyFluxK(tSfc, qSfc, qAir, absSolar, beta, latRad, kappa, vEff);
        if (flux <= 0.0) return 0.0;
        return Math.cbrt((G_ACC / tSfc) * Atmosphere.H_BL * flux);
    }

    /**
     * 过山损耗因子 <code>exp(-h/H_MOIST*kappa)</code> —— **雨影的唯一来源**。
     *
     * <p>h 必须是**上风方向**的地形高度（见 {@link #upwindElev}），不是本地海拔。
     * 抽出来是为了让风暴轴涡动项也用同一个因子（涡动水汽同样被上风的山抽干）。
     */
    public static double depletion(double hUp, double kappa) {
        if (!DEPL_ENABLED) return 1.0;              // ★ 诊断/回滚用：整体关掉
        if (DEPL_NO_KAPPA)  return Math.exp(-Math.max(0.0, hUp) / H_MOIST);
        if (kappa <= 0.0) return 1.0;
        return Math.exp(-Math.max(0.0, hUp) / H_MOIST * kappa);
    }

    /**
     * 过山损耗因子是否启用（诊断/对比用，默认 true = 保持生产行为）。
     * false ⟹ {@code depletion(...) ≡ 1}，用于量化 depl 对降水分布的全部贡献。
     */
    public static boolean DEPL_ENABLED = true;
    /**
     * 是否去掉 {@code kappa} 因子（诊断用，默认 false）。
     *
     * <p>实测（115 个陆地点）：{@code hUp > 0} 时 {@code kappa} 的 p50 = 0.671、min > 0.5
     * ⟹ 该因子<b>从不充当"海洋关闸"</b>（那已由 {@code hUp = 0} 保证），
     * 只是一个乘性削弱（{@code depl} 被放大 p50 x1.179 / p90 x1.82）。
     */
    public static boolean DEPL_NO_KAPPA = false;

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
    // ★★ A/B DONE (S625, full 22-gate suite): MEASURED, TRIED, REVERTED.  Result recorded below.
    //    It works where divU is physically right and backfires where divU is wrong -- see the
    //    RESULT paragraph at the head of the block below.  Order matters: fix divU FIRST.
    // ★★ S628 A/B: the subsidence-only branch was tried; Q_FROM_BLBUDGET is REVERTED to false.
    //    Arms: A0 = false (B97DFF43_805B5532_TALOS, 18/22); A1 = true + SUB_ONLY (D258C818_C1B5BF94_TALOS, 17/22).
    //    NEW RED: GATE_VERDICT.  INDEPENDENT GPCP (A0 -> A1):
    //      subtropics 27.5~37.5  summer -78.3% -> -55.0%  (gain HELD; §627 with |w_BL| gave -50.5%)
    //      equator    2.5~12.5   summer -41.3% -> -73.9%  (regression NOT removed; §627 gave -72.9%)
    //      equator    2.5~12.5   winter -61.4% -> -74.7%  (regression NOT removed; §627 gave -72.8%)
    //      mid-lat    47.5~62.5  unchanged.
    //    => REFUTED: "the tropical regression comes from the convergence branch of the entrainment."
    //       P949 measured the tropical ocean (5~10 deg) as CONVERGENT, so max(0,divU) gives C=0 there --
    //       yet the equator still degraded.  The remaining sink in the closure is k_P:
    //         q* = 86400*chv*qSat*beta / (86400*chv + k_P + C*(1-qftf)),  k_P > 0 when wE > 0 (ITCZ ascent)
    //       => even with C=0 the budget lowers q below RH_SEA*qSat.  The tropical drying is therefore
    //       PRECIPITATION ACTING AS A BOUNDARY-LAYER MOISTURE SINK, not entrainment.
    //       Physically the real tropical BL is DECOUPLED from the deep convection and is re-moistened by it;
    //       this closure treats the BL as a closed slab that only loses moisture.  THAT is the next thing
    //       to address -- not the entrainment branch.
    // ★★★ S630 A/B: THE THIRD AND LAST BRANCH OF THIS CLOSURE ALSO FAILED.  REVERTED.
    //   Three branches of the SAME closure, all with Q_FROM_BLBUDGET = true:
    //     S627  C = 86400*rho*|w_BL|          equator summer -72.9%  winter -72.8%   dir 5AC617A8
    //     S628  C = 86400*rho*max(0,H_BL*divU) equator summer -73.9%  winter -74.7%   dir D258C818
    //     S630  + k_P *= (1-BETA_DOWNDRAFT)   equator summer -70.3%  winter -66.7%   dir 156D4DC5
    //   BASELINE (closure OFF):               equator summer -41.3%  winter -61.4%   dir B97DFF43
    //   Subtropical gain held in all three (~ -50% .. -55% vs -78.3% baseline);
    //   GATE_VERDICT went red in all three (18/22 -> 17/22).
    //
    //   => CONCLUSION, and it is about the FORM not any branch: this boundary-layer budget closure
    //      is right in the subtropics (subsidence, little rain -- a closed slab losing moisture is
    //      a fair approximation there) and structurally wrong in the ITCZ, where the real boundary
    //      layer is DECOUPLED from deep convection.  Drying it by |divU|, by max(0,divU), or by
    //      (1-beta_d)*k_P all leave the tropics far too dry.  Halving the rain sink helped only
    //      ~8 points of the ~30 needed.
    //   => STOP PATCHING THIS CLOSURE.  Any future attempt must change the FORM (e.g. decouple the
    //      tropical BL from the convective sink outright, or supply moisture from the convective
    //      downdraft as an explicit source rather than by discounting the sink), and it must be
    //      sourced from literature before any code is written.
    public static boolean Q_FROM_BLBUDGET = false;

    /**
     * <b>S628：卷夹只取【下沉枝】。</b>默认 true。
     *
     * <p>闭包写成 {@code V = 86400*rho*w_e*(q - q_FT)}，其中 {@code w_e} 是与自由对流层的交换速度。
     * <p>物理上两枝的机制不同：
     * <ul>
     *   <li><b>下沉区（副热带）</b>：边界层顶就是下沉逆温，卷夹速度 {@code w_e ~ -w_subsidence = |w_BL|}
     *       —— 这是 trade inversion 的标准图像 ⇒ 该干燥；</li>
     *   <li><b>辐合区（ITCZ）</b>：边界层顶抬升，卷夹由【对流】驱动（{@code w_e ~ 0.2*w_*}，来自地面浮力通量），
     *       <b>不由 |divU| 决定</b> ⇒ 不该按 |divU| 干燥。</li>
     * </ul>
     * 旧式 {@code |w_BL|} 两枝都算成「卷夹 ⇒ 变干」，因此把热带也抽干了。
     *
     * <p>⚠ 这与「有符号 {@code w_BL}」<b>不是</b>同一件事（§627 已用代数证明后者方向相反、
     * 且会让 {@code denB} 在辐散侧变负）：
     * <pre>
     *   有符号 w_BL : 辐合 => C>0 => 更干   （方向错）
     *   max(0,divU) : 辐合 => C=0 => 不干   （方向对）
     * </pre>
     */
    public static boolean Q_BLBUDGET_SUB_ONLY = true;

    /**
     * <b>S630：下气流把降水的一部分还回边界层</b>（Betts & Miller 1986 一系）。
     *
     * <p>闭包现在写成 {@code E = P + V}，<b>两项都是汇</b> ⇒ 降水一下来边界层就变干。
     * 而真实的对流下气流把凝结物蒸发回环境，补偿了这一汇。文献形式：
     * <pre>
     *   EVP 正比于降水率          Betts and Miller (1986)   —— refs/gmd_conv_adj.txt:3733
     *   EVP <= 降水的一个分数      Emanuel (1991)            —— 同上 Table 12
     *   EVP = C_evap (1-RH) P^1/2  Bechtold et al. (2001) / Park and Bretherton (2009)
     * </pre>
     *
     * <p><b>取值有出处，不是选的</b>：{@code refs/betts_miller_2004.txt:58}（Betts 1973，VIMHEX 外场）——
     * 「<b>about half the updraft condensation was evaporated into the downdrafts</b>」。
     * ⇒ 有效汇 {@code (1 - BETA_DOWNDRAFT) * k_P}，取 <b>0.5</b>。
     *
     * <p>⚠ 这只补偿了【对流尺度下气流】这一项。文献里另有一支是 <b>(1-RH)</b> 依赖
     * （干处蒸发更多）—— 它与本项方向不同，且需要降水通量的单位链对齐，
     * <b>本项不掺入它</b>（避免引入第二个来源不明的系数）。
     */
    public static double BETA_DOWNDRAFT = 0.5;

    // ===== RESULT OF THE S625 A/B (value left at false) =====
    // Arm A = false (dir B97DFF43_805B5532_TALOS, 18/22); Arm B = true (dir 5AC617A8_5E49E6C4_TALOS, 16/22).
    // GATES: 18/22 -> 16/22.  NEW RED: GATE_B4_INBAND, GATE_VERDICT.  => reverted.
    // INDEPENDENT GPCP ROWS (P296), arm A -> arm B:
    //   subtropics 27.5~37.5  summer -78.3% -> -50.5%  (BIG improvement, +27.8 points)
    //                         winter -67.8% -> -65.1%  (small improvement)
    //   equator    2.5~12.5   summer -41.3% -> -72.9%  (big regression)
    //                         winter -61.4% -> -72.8%  (regression)
    //   mid-lat    47.5~62.5  summer/winter UNCHANGED.
    // MECHANISM.  C uses Math.abs(-H_BL*divU) => CONVERGENCE DRIES TOO.  And in this model the
    // ocean receives DIVERGENCE from cellPressure EVERYWHERE (the code itself measured at :2006
    // that divU is a land/sea switch, not a monsoon/desert discriminator).  So the coupling is
    // right where divU is right and wrong where divU is wrong:
    //   SUBTROPICS  divU>0 (divergence) is PHYSICALLY CORRECT there => the closure supplied
    //               exactly the missing dry boundary layer => -78% -> -50%.  The MECHANISM IS RIGHT.
    //   TROPICAL OCEAN  divU>0 is PHYSICALLY WRONG (the real tropics converge -- ITCZ) => the
    //               closure dried the one place that should be wettest => -41% -> -73%.
    // => CONCLUSION: keep this closure as the correct mechanism, but it MUST NOT be enabled
    //    until divU itself is physical.  Fix divU first, then re-run this A/B.  Enabling it now
    //    would be compensating one wrong field with another.
    //
    // S625 (2026-09-22): false -> true.  WHY (measured, not taste):
    //   P948 measured that this world's marine boundary layer has an effective RH of ~0.902 and
    //   that it is a CONSTANT of latitude -- because this switch being off means q comes from
    //   moisture() = RH_SEA*qSat(T), i.e. the same 0.80 everywhere.  The observed profile
    //   (e_obs_profile.py: COBE-SST2 + NCEP-R1 shum at 1000 hPa + ERA5 u,v) has an RH MINIMUM of
    //   0.614 at 25 deg (0.676 at 15, 0.628 at 20), i.e. the subtropical dry boundary layer that
    //   Hadley subsidence produces.  The resulting dq deficit is ~3x (observed 7.4~8.0 g/kg at
    //   15~25 deg vs the model's <=2.57 g/kg), and since E = rho*cd*|V|*(qsat-qa), E comes out
    //   0.12~0.27 of observed in the tropics/subtropics while the MODEL/obs ratio climbs to
    //   0.55~0.76 at 45~60 deg => the error is STRUCTURAL (latitude-dependent), not a calibration.
    //
    //   This closure is the mechanism that supplies it, and it introduces NO new constant:
    //     q* = 86400*chv*qSat(Ts)*beta / (86400*chv + k_P + C*(1-qftF)),  C = 86400*rho*|w_BL|
    //     w_BL = -H_BL*divU  =>  over ocean (which gets DIVERGENCE from cellPressure) |w_BL| is
    //     large => C large => q small.  Subsidence dries the boundary layer by itself.
    //
    //   The constant RH path is what makes E peak at the EQUATOR (E ~ qsat(Ts) with fixed RH);
    //   the observation has a RELATIVE MINIMUM at the equator and a maximum at 15~20 deg
    //   (Grothjahn, General Circulation, ch.5: "There is a relative minimum of E_w near the
    //   equator").  E_obs computed with this same bulk formula reproduces the accepted global
    //   mean (2.889 mm/day vs the literature ~2.9) => the observed side is trustworthy.
    //
    //   P678 (S471) had already measured that this closure lowers q by 17~29% (0.01138 vs 0.01367,
    //   0.00918 vs 0.01300) and flips the moist stability M positive in both boxes; it was left
    //   off because it had never been A/B'd through the full 22-gate suite.  That A/B is this run.
    //
    //   NOT turned on with it: Q_FROM_SOURCE (its javadoc requires pairing with
    //   StationaryWave.WVLW_K, which is not enabled) and Q_ADVECT_BUDGET.  They gate which path
    //   computes q BEFORE this block; this block then overrides q, so they are irrelevant here,
    //   but beta (SoilMoisture) stays in the numerator => LAND dryness is preserved.
    //
    //   ★★★ 2026-10-16（goal-3355454e）休眠能力标注 —— **不要当死代码删掉**：
    //   Q_FROM_SOURCE=true 那条支路（{@link #moistureFromSource} + {@link #upwindSea}）是
    //   「海洋源 + 内陆 e 折耗竭」水汽模型的**唯一实现处**，即文献公式
    //     P(x) = P(0)·exp(-x/l)  与  λ_ε = S·u_h/P
    //   （Makarieva & Gorshkov 2007, HESS 11, 1013-1033, DOI 10.5194/hess-11-1013-2007, Eq.(1) p.1014 /
    //     Eq.(3) p.1015；van der Ent et al. 2014, ESD 5, 471-489, Eq.(22) p.475）
    //   在本仓的**兑现处** —— 它的时标就是 {@link #fetchL()}（= SOURCE_FETCH_L_VDENT，
    //   由 S·u/P 反解并对齐本世界尺度，见 {@link #SOURCE_FETCH_FROM_VDENT}）。
    //   配套机器（{@link #upwindElev} / {@link #upwindLakeFrac}）**已经在生产路径上无条件运行**，
    //   所以这不是废弃代码，而是**待配对启用**的能力：
    //     [ ] 先做 StationaryWave.WVLW_K 那条配对 ⇒ 再一起 A/B ⇒ 再走全量验收
    //   （它关闭时逐位不变：moistureFromSource 的 javadoc 逐字写着「等于 moisture」。）

    /** 水汽从海岸向内陆的 e 折输送尺度（m）。P656 扫的三个值：1e6 / 2e6 / 3e6。 */
    public static double SOURCE_FETCH_L = 37500.0;   // ★ 缩 40x（原 1.5e6）

    // ================================================================================
    // ★★★ 2026-10-16（目标 goal-3355454e）：把时标从【拟合值】改为【发表公式导出】✓
    //
    // 出处（逐字 ✓）：van der Ent & Savenije (2011), Atmos. Chem. Phys. **11**, 1853-1863,
    //   第 1856 页：「Sa is atmospheric moisture storage (i.e. precipitable ...) ... These latter
    //   variables can be grouped into one simple and meaningful metric **λ_ρ = Sa·u/E**, which leads
    //   to the following equation: ρ(x) = 1 - exp(-x/λ_ρ)」 ✓
    //   ★ 注意：**函数形式与生产式完全相同** ✓（生产式 exp(-fetch/SOURCE_FETCH_L) ✓）
    //     ⟹ 生产式本来就是这篇发表的写法 ✓，SOURCE_FETCH_L 扮演的正是 λ_ρ ✓
    //
    // ★★ 2026-10-16 第二次取证更正（子代理 11df43cf ✓）：**分母是 P，不是 E** ✗
    //   两个"对偶"长度都有出处，但**方向不同** ✓：
    //     · λ_ρ = S_a·u/E（van der Ent & Savenije 2011 Eq.7 ✓）= **降水再循环**长度 ✓
    //     · **λ_ε = S·u_h/P（van der Ent et al. 2014 ESD 5, 471-489, Eq.(22), p.475 ✓ 逐字）**
    //       = 「the average travel distance after evaporation if Su_h/P remains equal downwind」✓
    //   而本段的物理（**海源水汽向内陆平流、被降水耗竭** ✓）对应的正是**后者** ✓✓
    //   两条独立闭合也都指向 L = U·W/P ✓：
    //     · Makarieva & Gorshkov (2007) HESS 11, 1013-1033, DOI 10.5194/hess-11-1013-2007 ✓
    //         Eq.(1) p.1014 逐字：R(x) ≡ dF(x)/dx = -(1/l) F(x) or **F(x) = F(0) exp{-x/l}** ✓
    //         Eq.(3) p.1015 逐字：**P(x) = P(0) exp{-x/l}** ✓
    //     · Budyko (1974)（经 Brubaker et al. 1991 MIT Parsons Lab Report No.333 pp.57-59 ✓
    //         页面图像核对 ✓）：线性模型 d(wu)/dx = -P_a ⟹ 完全耗竭距离 **wu/P_a** ✓
    //
    // 本仓的推导（**可复跑** ✓：tools/zonal-asym/lambda_rho_fixed.py ✓）：
    //   S = ZonalTables.W_COL_ANN（ERA5 柱水汽 ✓）｜u = U_ZM_JAN/JUL 年均 ✓
    //   P = GPCP LTM（refs/gpcp_ltm.nc ✓）
    //   ⟹ **面积加权的地球口径 L = S·u/P = 2103 km** ✓
    //      （论文自述区间：热带/山地 500-2000 km、温带 3000-5000 km ✓；Table 2 实例 2400/3000 km ✓）
    //   ⟹ 折算到本世界（R_earth/R_EFF = 40.03 ✓ 模型自己的几何因子 ✓）= **52.5 km** ✓
    //   ⟹ 与现用的拟合值 37500 m 只差 **1.40 倍** ✓✓ ——「14 倍矛盾」至此结案 ✓（当年是口径混用 ✓）
    //
    // ⚠ **NOT FOUND（必须写明 ✓）**：生产式的**复合形式** q = RH·q_sat(T_src)·exp(-fetch/L)
    //   **查无出处** ✗ —— 文献里被指数衰减的是【通量 F / 降水 P / 柱水汽 S】✓；把 T 钉在源区温度、
    //   RH 取常数是**额外的建模选择** ✓（Budyko 原模型还是**线性**耗竭，不是指数 ✓）。
    //   ⟹ 本公式应理解为「**一阶耗竭近似 + 源区饱和假设**」✓，**不得标注为文献公式** ✗。
    // 开关默认 **false** ⟹ 逐位不变 ✓（打开时用导出值，**零新自由参数** ✓）
    // ================================================================================
    public static boolean SOURCE_FETCH_FROM_VDENT = true;

    /** 由发表公式 λ = Sa·u/E 导出的时标（本世界口径，m）✓ —— 见上方的推导与出处 ✓。 */
    public static final double SOURCE_FETCH_L_VDENT = 52541.0;

    /** 当前生效的水汽耗竭长度（m）✓ —— 开关关闭时**逐位**等于 SOURCE_FETCH_L ✓。 */
    public static double fetchL() {
        return SOURCE_FETCH_FROM_VDENT ? SOURCE_FETCH_L_VDENT : SOURCE_FETCH_L;
    }
    /** 逆推的最大距离（m）；超过则回落到原式。 */
    public static double SOURCE_FETCH_MAX = 100000.0;   // ★ 缩 40x（原 4.0e6）

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
    // ★★★★★ 2026-10-15 用户裁决：**打开**（验证降水链条能否用上地表反照率）
    //   背景：Q_NET_HEATING 打开后 divAt 已全面响应 ✓（见 待裁决/415），
    //         但降水仍逐位不变，因为 divU 叠加 dWave 的那段只在 WZM_FROM_QNET 时才…
    //         ⚠ 更正：那段的条件是 STATIONARY_WAVE_ON（已 true），本开关控制的是
    //         【纬向平均项】的口径（见 :498 的配对说明）—— 开启后 wEffQnet 支路才会执行。
    //   ⚠ 本条**已在 SimClimate.configStamp() 里**（:488 附近）⟹ 缓存会自动失效 ✓
    public static boolean WZM_FROM_QNET = true;

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
     * ★★★★★★ **§7294：`M` 改用 Neelin & Zeng (2000) 的【真垂直积分】口径。** 默认 false => 逐位不变。
     *
     * <p><b>为什么</b>（§7286/§7293）：`CALIBERS.md` 第 132/133/134 条已判定两层口径不足 ——
     * 两层把 `M = ⟨Omega d_p h⟩` 退化成一个受 `M_FT_FRAC` 控制的数，其符号由建模选择决定
     * （`M > 0 <=> q_BL > 0.00846`，§481），而**要修的沙漠恰好落在 `M < 0` 一侧**
     * => `F_net/M` 在那里回落到旧闭合（陆海开关）=> 同纬度分不出季风与沙漠（§7280/§7286）。
     *
     * <p><b>形式（NZ2000 逐字，§7293 已定）</b>
     * <pre>
     *   (3.2)   A_1^+(p) = INT_p^{p_rs} A_1(p') d ln p'      （权重 d ln p'，无归一化因子）
     *   (3.10)  V_1(p)   = a_1^+(p) - a_1^c
     *           Omega_1(p) = -V_1(p)
     *   Table 1 V1s = V_1(p_rs) = -a_1^c = -0.20  =>  a_1^c = 0.20
     * </pre>
     * `M = <Omega d_p h>` 在【高度坐标】下的等价形式，且与两层口径**同量纲**（J/m^2）：
     * <pre>
     *   M = RHO_AIR * INT_0^{H_EFF} Omega_1(p(z)) * (dh/dz) dz
     * </pre>
     * <b>一致性自检</b>：取 `Omega_1 == -1`、积分到 `zFT` 时，本式【恒等于】两层口径的
     * `RHO_AIR * H_EFF * dh`（因为 `INT_0^{zFT} (-dh/dz) dz = h(0) - h(zFT)`）。
     *
     * <p>⚠ <b>两条【我们的推论】，不是文献逐字</b>（§7293 未定，实现前已声明）：
     * <ol>
     * <li>`A_1(p)` 的归一化取 `A_1(p_s) = 1`（「一个单位的地表温度扰动」）——
     *     NZ2000 不给 `A_1` 的剖面，故其绝对标度无法从文献取；</li>
     * <li>`<...>` 的积分测度取【高度】而非 `dp/g` —— 这样 `M` 与两层口径同量纲（J/m^2），
     *     不需要引入任何归一化因子（§7293 已记「归一化因子无法判定」）。</li>
     * </ol>
     *
     * <p>⚠ <b>`M_FT_FRAC` 对本路径【零影响】</b>（§7289 铁律乙）。
     */
    public static boolean M_FROM_VINT = false;

    /** `M` 垂直积分的层数 —— **数值离散参数，非物理常数**（用不敏感判据定，不得拟合）。 */
    public static int M_VINT_STEPS = 24;
    /** `A_1(p)` 数值微分的地表温度增量 K —— **数值参数，非物理常数**。 */
    public static double M_VINT_DT = 1.0;
    /** `moistAdiabatT` 内部积分步数 —— **数值参数**。 */
    public static int M_VINT_ADI_STEPS = 12;
    /** `A_1^+(p)` 积分的步数 —— **数值参数**。 */
    public static int M_VINT_A1STEPS = 24;

    /** §7294：`a_1^c` —— NZ2000 Table 1 自带标签「Surface value of baroclinic wind basis function」
     *  给出 `V1s = -0.20`，而 `(3.10)`+`(3.2)` 逐字 => `V1s = -a_1^c` => `a_1^c = 0.20`。
     *  <b>不得取 `a_hat_1 = 0.38`</b>（§7292 那个推论已被 §7293 推翻，它误用了 `dp` 权重）。 */
    public static final double A1C = 0.20;

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
        // ---- M：两层近似（默认，逐位不变）或 §7294 的真垂直积分 ----
        double M;
        if (M_FROM_VINT) {
            // §7294：真垂直积分。**不读 M_FT_FRAC**（§7289 铁律乙）。
            M = mVerticalIntegral(tQ, q);
        } else {
            double zFT = M_FT_FRAC * Atmosphere.H_EFF;
            double qftF = Math.exp(-zFT / H_MOIST);
            double dh = Radiation.CP * (Atmosphere.GAMMA * zFT)
                      - G_ACC * zFT
                      + Radiation.LV * q * (1.0 - qftF);
            M = Atmosphere.RHO_AIR * Atmosphere.H_EFF * dh;
        }
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

    /** §7294：对流层顶参考气压 `p_rt = p_rs - p_T`（NZ2000 Table 1: `p_T` 约 `8e4 Pa`）。 */
    public static final double P_TROPO = P_SURF - 8.0e4;

    /**
     * §7294：`A_1(p) = dT^c/dT_sfc` —— 湿绝热扰动形状，归一化 `A_1(p_s) = 1`。
     *
     * <p>用 {@link ParcelLift#moistAdiabatT} 数值微分求得（NZ2000 `:99` 逐字：
     * 「A1(p) gives the vertical shape of the moist adiabat perturbation per hb perturbation.
     * Below the reference lifting condensation level, A1(p) is a dry adiabat.」）。
     *
     * <p>⚠ 归一化 `A_1(p_s) = 1` 是【我们的推论】（§7293 未定；NZ2000 不给 `A_1` 的剖面）。
     */
    private static double mIntA1(double tSfc, double pPa) {
        double d = M_VINT_DT;
        double tp = ParcelLift.moistAdiabatT(tSfc + d, P_SURF, pPa, M_VINT_ADI_STEPS);
        double tm = ParcelLift.moistAdiabatT(tSfc - d, P_SURF, pPa, M_VINT_ADI_STEPS);
        return (tp - tm) / (2.0 * d);
    }

    /**
     * §7294：`a_1^+(p) = INT_p^{p_rs} A_1(p') d ln p'` —— NZ2000 `(3.2)`，**无归一化因子**。
     *
     * <p>中点法在 `ln p` 上等步长积分（低层分辨率更好）。
     */
    private static double mIntA1Plus(double tSfc, double pPa) {
        int n = M_VINT_A1STEPS;
        if (n < 1) n = 1;
        double lo = Math.log(pPa), hi = Math.log(P_SURF);
        if (!(hi > lo)) return 0.0;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double a = lo + (hi - lo) * i / n;
            double b = lo + (hi - lo) * (i + 1) / n;
            sum += mIntA1(tSfc, Math.exp(0.5 * (a + b))) * (b - a);
        }
        return sum;
    }

    /**
     * §7294：`Omega_1(p) = -V_1(p) = -(a_1^+(p) - a_1^c)`，在**高度坐标**下乘以 `H_EFF`（带长度量纲）。
     *
     * <p>为什么必须带 `H_EFF`：NZ2000 的结构函数是无量纲的，而本模型的 `M` 量纲是 `J/m^2`
     * （两层式 `RHO_AIR*H_EFF*dh` 里 `dh` 是 `J/kg`）。乘 `H_EFF` 后，
     * **`V_1 == -1` 时本路径【恒等】于两层式** —— 这是可验证的自洽性判据。
     */
    private static double mIntOmega(double tSfc, double pPa) {
        return -(mIntA1Plus(tSfc, pPa) - A1C) * Atmosphere.H_EFF;
    }

    /**
     * §7294：`M = RHO_AIR * INT_0^{H_EFF} Omega_1(p(z)) * (-dh/dz) dz`。
     *
     * <p>MSE 廓线用**模型自己的环境廓线**（与两层式同一套，逐字对应 `:677-681`）：
     * `h(z) = cp*(tQ - GAMMA*z) + g*z + L*q*exp(-z/H_MOIST)`
     * => `dh/dz = -cp*GAMMA + g - (L*q/H_MOIST)*exp(-z/H_MOIST)`。
     *
     * <p>**自检**：`Omega_1 == H_EFF`（即 `V_1 == -1`）时 `M = RHO_AIR*H_EFF*(h(0)-h(H_EFF))`
     * —— 与两层式 `RHO_AIR*H_EFF*dh` 恒等（取 `M_FT_FRAC = 1`）。
     */
    private static double mVerticalIntegral(double tQ, double q) {
        int n = M_VINT_STEPS;
        if (n < 1) n = 1;
        double zTop = Atmosphere.H_EFF;
        double dz = zTop / n;
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            double z = (i + 0.5) * dz;
            double p = ParcelLift.pOfZ(z, tQ, Atmosphere.GAMMA);
            if (!(p > 0.0) || p > P_SURF) break;
            double dhdz = -Radiation.CP * Atmosphere.GAMMA + G_ACC
                        - Radiation.LV * q * Math.exp(-z / H_MOIST) / H_MOIST;
            sum += mIntOmega(tQ, p) * (-dhdz) * dz;
        }
        return Atmosphere.RHO_AIR * sum;
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
    // ★★★★★ §787 臂 A 实测结论：**否证**，已恢复 false。
    //   跑法：本开关置 true，其余逐位不动；跑 22 支完整套件。
    //   A 侧基线 = rerun_acceptance\D968C9E3_64E20400_TALOS；臂 A = E6999DD6_14947974_TALOS。
    //   实测：idx25 季风指数 +0.312 -> **+0.061（下降）**；P683 亚洲 JJA 2.330 -> 1.533（-34%）、
    //         撒哈拉 1.712 -> 1.060（-38%）、亚洲/撒哈拉【绝对差】0.618 -> 0.473（下降）；
    //         GATE_SUBTROP_ZERO 逐位不变（100.0% / 4.985）、DJF_3040 1.156 -> 1.139、无门翻转。
    //   §785 §四 的 (A2) 预登记写死「差必须拉大；否则前提不成立，撤回并记录」=> **撤回**。
    //   机理：本路径把陆地 q 从【局地 rh*qSat(T_land)】换成【海洋源 + 沿途衰减】，
    //         两种口径下它都使陆地【全面变干】，而季风指数是 JJA-DJF 的【差】=> 全面变干不产生对比。
    //   这与 CALIBERS §8 条目 20 的警告一致：「在模型自己的状态里再找机制是徒劳的」。
    /**
     * ★★★ 水汽平流收支（§473 / §241.3 立项）。**2026-10-08 试开后【关闭】。**
     *
     * <h3>⚠ 为什么关掉（实测，不是猜）</h3>
     *
     * <p>试开后出现一个 88 倍的异常点：{@code (150 km, 0)} 的 {@code mm/yr} 从 1786 涨到
     * <b>157053</b>（其余点不变）。逐层隔离后定位到【尺度不一致】：
     *
     * <pre>
     *   moistureAdvected 的沿风 ODE：  lam = lamFlux + 86400*divU     [1/day]
     *     lamFlux = 86400*chv/HCO = 3266/2400 = 1.36 /day
     *       （源码注释自己写「时间尺度 18 h」）
     *     a = lam*ds/sp/86400 = 1.36 x 3750 / 8 / 86400 = 0.0074
     *     ⟹ 每步只衰减 0.74% ⟹ 100 km（27 步）后 q 只降到 0.82
     *
     *   而本仓的【水汽输送 e 折尺度】是 SOURCE_FETCH_L = 37,500 m：
     *     37.5 km / 8 m/s = 4688 s = 1.3 小时 ⟹ lam 应该 = 18.5 /day
     *     ⟹ ★ 实际 1.36 vs 应有 18.5 ⟹ 【慢 14 倍】
     * </pre>
     *
     * <p>⟹ 结果：空气从海面平流 100 km 后水汽【几乎不损耗】⟹ 个别点的 q 停留在
     * 海面饱和值（如 0.017 kg/kg @ tSl=300.7 K）⟹ 配上地形抬升就给出
     * 430 mm/day = 157053 mm/yr。
     *
     * <p><b>判据（用户 2026-10-08 的要求 2）：「符合物理规律，不自己造参数造公式」。</b>
     * 这个机制引入了一个<b>未标定、且与已有尺度（SOURCE_FETCH_L）矛盾 14 倍</b>的时标
     * ⟹ 不满足要求 2 ⟹ 关闭。
     *
     * <p>★ <b>它想解决的问题仍然存在</b>：§241.1 硬伤 2 —— 打开观测剖面后副热带会过干。
     * 正确的修法是把这个 ODE 的时标与 {@code SOURCE_FETCH_L} 对齐（或从文献重新推导），
     * <b>而不是带着 14 倍的尺度矛盾打开它</b>。
     *
     * <h3>原始说明（保留）</h3>
     * <p>§473 / §241.3 立项的「跨列水汽辐合」。
     *
     * <p>这是 §241.3「新主线」的第 1 子项：补上跨列水汽辐合，让副热带干带与中纬雨带
     * 由同一套方程给出，而不是靠一个放错位置的涡动项去凑。
     *
     * <p>★★ <b>实测四组合（2026-10-08）</b>——注意「两个都开」的 max 是 3764 但那是
     * 33 点采样；115 点采样下 {@code (150,0)} 会给出 157053（见上）。
     * <pre>
     *   组合               p50    p90    max    mean    成本(ms/点)
     *   现状(两个都关)     6413   25405  49438  10590   1601.333
     *   ★ 只开 OBS          828    2578   3764   1276       0.030
     *   只开 ADV          6413   25405  49438  10470     437.909
     *   两个都开            633    2693   3764   1094       0.030   （33 点；115 点下 max=157053）
     * </pre>
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

    /**
     * ★★★★★ §577：<b>Folkins &amp; Braun (2003) 的湿熵阈值判据</b>（默认 false ⇒ 逐位不变）。
     *
     * <p>与 {@link #BLQ_GATE} 的关键差别：BLQ 比的是<b>MSE 差</b>（化简后等价于「RH &gt; 约 0.77」
     * —— 见 §576 的代数推导，那是一条<b>固定的湿度门</b>，会把全球陆地一起禁雨）；
     * 本判据比的是 <b>θ_e(BL) vs θ_e,conv(SST)</b>，<b>阈值随海温变</b>。
     *
     * <p><b>文献逐字</b>（Folkins &amp; Braun 2003）：
     * <pre>
     * "θ_e,conv rapidly increases from 334 to 342 K in going from an SST of 25 to 28 C"
     * Table 1: 342.0 K @ 28.2 C ；343.8 @ 29.1 ；344.4 @ 29.7
     * "At SSTs below the convective threshold, near-surface winds are generally directed from cold to
     *  warmer SSTs, so that horizontal advection of equivalent potential temperature (θ_e) will tend to
     *  suppress moist entropy, and rainfall, in these regions."
     * </pre>
     * ⇒ <b>零自由拟合参数</b>：334 K / 342 K 两个端点与线性过渡全部来自文献。
     *
     * <p>⚠ 两处必须记账的口径限制：
     * <ol>
     * <li>θ_e,conv 的原文是<b>海面</b>判据；本实现用 {@code seaSurfaceTemp(lat,theta) + sstAnom}
     *     作为该纬度「对流阈值」的代理，<b>在陆地上是外推</b>。</li>
     * <li>文献那条「冷海向岸风压低 θ_e」的<b>平流项尚未实现</b> —— 现在只做了阈值这一半。</li>
     * </ol>
     */
    // ★★★ §577：默认改为 true（用户裁决）。实测（P907，JJA，同一口径）：
    //   判据              ASIA P   SAHARA P   比值（观测 0.0137）
    //   无                1.709    1.683      0.985   （沙漠偏湿 16 倍）
    //   BLQ_GATE(MSE)     0.108    0.045      0.417   （全陆禁雨）
    //   BLQ_THETA_E       0.365    0.045      0.123   ← 本判据
    //   ⇒ 撒哈拉 0.045 vs 观测 0.105（差 2.3 倍，原为 16 倍）；失败模式从「沙漠太湿」
    //     翻转为「季风太干」⇒ 系统落到了正确的分支上。
    //   ⚠ 已折入 SimClimate.configStamp()（0x7A130L）。
    public static boolean BLQ_THETA_E = true;
    /** 诊断：最近一次的 `dh = h_bl - h*_th`（J/kg）。 */
    public static volatile double blqLastDh = 0.0;
    /** 诊断：被 BLQ 判据压到接近 0 的次数（`g < 0.01`）。 */
    public static long blqBlocked = 0;
    /** 诊断：调用次数。 */
    public static long blqCalls = 0;

    /**
     * ★ §577：Folkins &amp; Braun (2003) 的深对流湿熵阈值 θ_e,conv（K），按 SST 分段。
     * 334 K @ ≤25°C ；线性到 342 K @ 28°C ；342 K @ ≥28°C。**零自由参数。**
     */
    public static double thetaEConv(double sstC) {
        if (sstC <= 25.0) return 334.0;
        if (sstC >= 28.0) return 342.0;
        return 334.0 + 8.0 * (sstC - 25.0) / 3.0;
    }

    /** §577：边界层等效位温 θ_e（K）。Bolton (1980) 的常用近似 θ_e = T·exp(L_v·q/(c_p·T))。 */
    public static double thetaE(double tK, double qKgKg) {
        return tK * Math.exp(Radiation.LV * qKgKg / (Radiation.CP * tK));
    }

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
        return rh * qSat(tSrc) * Math.exp(-fetch / fetchL()) * depletion(elev, kappa);
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
        if (!UPWIND_ELEV_PATH_MAX && !UPWIND_ELEV_ACCUM_CLIMB) {   // 回滚路径：单点（原行为，逐位不变）
            int sx = x - (int) Math.round(u / sp * UPWIND_STEP);
            int sz = z - (int) Math.round(v / sp * UPWIND_STEP);
            return Math.max(0.0, PlateField.elevationWithCell(sx, sz, seed, cell));
        }
        // ★★★ 沿上风方向取【路径最高点】。
        //
        //  为什么必须改（P-实测，§144）：原式取【单点】（上风 UPWIND_STEP = 3750 m 处）的高程。
        //  而 3750 m 在气候尺度上几乎是「本地」——实测 hUp 的中位数落在【深海】（负高程），
        //  于是 max(0, hUp) = 0 ⟹ depl = exp(0) = 1 ⟹ 【雨影完全消失】。
        //  物理上雨影来自气流【沿途翻越的最高屏障】，不是某个固定距离处的一个采样点。
        //
        //  ★ 采样数由图内已有的两个长度导出，零新参数：
        //      N = fetchL() / UPWIND_STEP = 52,541 / 3,750 = 14（原写 37,500/3,750 = 10，已随
        //      SOURCE_FETCH_FROM_VDENT 的落地更新；旧文案是过期的）
        //    这正是本仓定义的【水汽输送 e 折尺度】—— 越过它之后空气的水汽已经换过一轮。
        //
        //  ★ 成本：10 次 elevation（约 +4.5 us），相对基线 mmPerDay ~1.45 ms 可忽略。
        final int n = Math.max(1, (int) Math.round(fetchL() / UPWIND_STEP));
        final double ux = u / sp, uz = v / sp;
        if (UPWIND_ELEV_ACCUM_CLIMB) {
            // ★★★ 沿途【累积上坡】—— 雨影的正确物理量。
            //
            //   气流沿上风方向逐段前进，每爬升一段就按 H_MOIST 丢掉一部分水汽，
            //   【下坡不回收】（Smith & Barstad 2004 原文：S 在下坡区取负号，
            //   使空气变干并蒸发降水物）。所以正确的量是
            //       climb = Sum over path of max(0, h_{i} - h_{i-1})
            //   而不是某一处的最大高程，也不是某个固定距离处的单点高程。
            //
            //   ★ 零新参数：采样数 N = SOURCE_FETCH_L / UPWIND_STEP 由已有长度导出；
            //     相邻点的高差用 UPWIND_STEP 本身的取样序列，不引入新步长。
            double hPrev = PlateField.elevationWithCell(x, z, seed, cell);
            double climb = 0.0;
            for (int i = 1; i <= n; i++) {
                final double d = i * UPWIND_STEP;
                final int sx = x - (int) Math.round(ux * d);
                final int sz = z - (int) Math.round(uz * d);
                final double hh = PlateField.elevationWithCell(sx, sz, seed, cell);
                if (hh > hPrev) climb += (hh - hPrev);
                hPrev = hh;
            }
            return climb;
        }
        double hMax = 0.0;
        for (int i = 1; i <= n; i++) {
            final double d = i * UPWIND_STEP;
            final int sx = x - (int) Math.round(ux * d);
            final int sz = z - (int) Math.round(uz * d);
            final double hh = PlateField.elevationWithCell(sx, sz, seed, cell);
            if (hh > hMax) hMax = hh;
        }
        return hMax;
    }

    /**
     * ★★ 上风高程是否取【路径最高点】。✗ 原文写「（默认 true）」—— **已过期**：
     * <b>2026-10-16 更正：当前 = false</b>（字段行 :1315 ✓）。
     * false ⟹ 回到单点（上风 UPWIND_STEP 处）的原行为，逐位不变 ✓
     * （★ 注意：{@link #UPWIND_ELEV_ACCUM_CLIMB} 也 = false ⟹ **两个都关** ⟹ 走 :1257 的单点回滚路径 ✓）
     */
    public static boolean UPWIND_ELEV_PATH_MAX = false;
    /**
     * ★★★ 上风高程是否取【沿途累积上坡】（物理上推荐 ✓）。✗ 原文写「（默认 true）」—— **已过期**：
     * <b>2026-10-16 更正：当前 = false</b>（字段行 :1325 ✓）⟹ 与 {@link #UPWIND_ELEV_PATH_MAX} 一样都关着 ✓。
     *
     * <p>{@code climb = Sum max(0, h_i - h_{i-1})}，沿上风方向采 N = {@link #fetchL()}/UPWIND_STEP 段
     * （= 52,541 / 3,750 = 14 段 ✓；✗ 原文写 SOURCE_FETCH_L 即旧值 37,500 ⟹ 10 段，已过期 ✓）。
     * 这是雨影的正确物理量（Smith &amp; Barstad 2004：下坡区 S 取负号 ⟹ 空气变干、降水物蒸发
     * ⟹ <b>下坡不回收</b>）。零新参数。
     *
     * <p>关掉 ⟹ 回到 {@link #UPWIND_ELEV_PATH_MAX}；两个都关 ⟹ 回到单点原行为。
     */
    public static boolean UPWIND_ELEV_ACCUM_CLIMB = false;

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
    // ★ 2026-09-21（§602，(c) 第一刀）：P935 实测 —— 只有【两个源场都涌现】时，
    //   X'' 的变号次数才在两个季节都降到 1（单极型，与观测同形）：
    //     两表都关 = 1/1    仅关柱水汽 = 3/0    仅关纬向温度 = 4/2    两表都开（原状）= 3/0
    //   而 EDDY_VAR=2（湿静能）在每一种组合里都更差（4~5 次）⇒ §427 那条论证在本实现被证伪。
    //   ⇒ 本次 A/B 把它关掉。
    // ⚠⚠ 2026-09-21（§603）A/B 结果：**已撤回 true**。
    //   P935 说它修形状（X'' 变号 3->1，单极型）；但整套验收读数显示：
    //     · 修好了量级：中纬 47.5~62.5 夏 1.38 -> 2.09（GPCP 2.534，偏差 -45.6% -> -17.4%）；
    //       NH band 平均夏 1.430 -> 2.430（GPCP 2.329，几乎正中）；
    //     · **但季节振幅被放大到远超观测**：NH 冬 2.307 -> 4.977（GPCP 2.488，2 倍过湿），
    //       冬/夏 = 2.05 而观测 1.07；SH 冬 2.090 -> 4.617（GPCP 2.674）、夏 0.839 -> 1.693（GPCP 2.432），
    //       冬/夏 = 2.73 而观测 1.10；45~55 夏 +25.6% -> +123%；
    //     · **并把 GATE_B2B_SPLIT_SH 打翻**（夏/冬 0.5347/0.4428 -> 0.3114/0.4898，南半球季节比反了），
    //       判决 9/3 -> 8/4；P692 冷/暖对比也减弱（0.2335 -> 0.2818）。
    //   ⇒ 根因：涌现的纬向温度场来自【模型自己的地表温度】，其季节循环比【观测的纬向平均】强得多。
    //   ⇒ 保留 true（已验 9/3）；「让源场涌现」这条路要等季节振幅问题先解决（见 §603）。
    // ★★★★★★★ 2026-09-21（§608）**用户裁决 (a)：整组上** —— §257 那一对的完整形态。
    //   四个条件必须【同时】成立（§607 五）：
    //     ① `A_ZM_K` = 新版 19 节点（已换）—— §257 的那一刀本身；
    //     ② 本开关 = false —— 否则 A_ZM_K 被 :720 的提前 return 遮蔽（§607 三）；
    //     ③ COL_WATER_FROM_TABLE = false —— P935：两者必须同时关才达单极型；
    //     ④ EDDY_MASK_OUTSIDE = true —— §257：「换表必须与修风暴轴赤道侧边缘一起做」。
    public static boolean ZONAL_SL_FROM_TABLE = false;   // 2026-09-17 用户裁决落地：中纬两条独立带 −40%→−3%（§265）

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
    // ★ 2026-09-21（§602，(c) 第一刀）：与 ZONAL_SL_FROM_TABLE 同批关闭 —— P935 实测两者【必须同时】关，
    //   单独关任一个都达不到单极型。详见上一段的读数。
    // ⚠⚠ 2026-09-21（§603）：与 ZONAL_SL_FROM_TABLE 同批撤回 true（P935 要求两者同时关，故必须一起回退）。
    // ★ 2026-09-21（§608）：与 ZONAL_SL_FROM_TABLE 同批关闭（§607 五 的条件 ③）。
    public static boolean COL_WATER_FROM_TABLE = false;   // 2026-09-17 §268.3 四格 A/B 通过后落地

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
     * <p>✗ 原文写「默认 false ⇒ 走原路 ⇒ 逐位不变」—— **已过期**：
     * <b>2026-10-16 更正：当前 = true</b>（字段行 :1549 ✓；2026-09-21 §593 打开做 A/B ✓）。
     * 关闭时逐位不变这一性质仍成立 ✓。
     */
    // ★ 2026-09-21（§593）：§592 修法 #2 已完成（新增 t850Slope：把 PCHIP 镜像到【当前生效的】
    //   温度源 t850Month 上）。P924 实测：默认下 t850Slope(关) 与 raw 中心差分逐位相同（23/23），
    //   打开后 23/23 全变，最大相对差 39.2%，且大差异恰在 57.5~67.5 度 —— 正是本文上面预告的带。
    //   ⇒ 本次打开做 A/B。
    public static boolean EDDY_T_SMOOTH = true;

    /** PCHIP 节点：-90..+90 每 5 度，共 37 个。 */
    public static final int T_NN = 37;
    private static final class TSlopeCache {
        boolean valid; double theta;
        final double[] m = new double[T_NN];
    }
    private static final ThreadLocal<TSlopeCache> T_SLOPE = new ThreadLocal<TSlopeCache>();

    // ==================== ★★★★★ §7363：四个斜率缓存的【配置失效入口】 ====================
    /**
     * ★★★★★ <b>§7363（挂账 2 的修法）：四个斜率缓存的【配置指纹】。</b>
     *
     * <p><b>修的 bug</b>（§7361/§7362）：{@link #T_SLOPE} · {@link #T850_SLOPE} ·
     * {@link #SLOPE_CACHE} · {@link #SLOPE_CACHE_P} 的有效性判据【只有 theta（+ var）】，
     * 而它们缓存的值**依赖配置** —— 例：{@code T_SLOPE} 缓存 {@code zonalSlTemp(lat, theta)}，
     * 后者经 {@code Atmosphere.zonalMeanSeaLevelK → landMinusOceanSLK} 依赖
     * {@link ClimlabEBM#ENABLED}（P1105 实测：45 度处 +0.108169 K）。
     * <b>⟹ 切换配置后缓存仍命中 = 用了旧配置的值</b>（§7354 实测偏移 ~0.013）。
     *
     * <p><b>修法</b>：每次进入这四个填充函数时核对 {@code SimClimate.configStamp()}；
     * 变了就 {@code remove()} 掉四个 ThreadLocal（下一次调用自然重建）。
     * <b>零新常数</b> —— 复用既有的配置指纹机制（D58 的准入判据本来就是「改了结果的旋钮
     * 就得让缓存失效」）。**不枚举开关** ⟹ 将来新增开关也不会再漏（候选 B 的理由）。
     *
     * <p><b>开销</b>：{@code configStamp()} = <b>104.9 ns/次</b>（P1106 实测），
     * 相对 {@code mmPerDay} 的 <b>13.086 ms/次</b> = <b>0.0008%</b> ⟹ 可忽略（硬前提②）。
     *
     * <p><b>生产影响</b>：生产【不切换配置】⟹ 指纹不变 ⟹ 缓存只填一次 ⟹ <b>逐位不变</b>。
     * 本修法只让【同 JVM 切换配置的探针 A/B】变可靠。
     */
    private static final ThreadLocal<Long> CFG_SEEN = new ThreadLocal<Long>();

    private static void ensureCfg() {
        Long seen = CFG_SEEN.get();
        long now = SimClimate.configStamp();
        if (seen == null || seen.longValue() != now) {
            CFG_SEEN.set(Long.valueOf(now));
            T_SLOPE.remove();
            T850_SLOPE.remove();
            SLOPE_CACHE.remove();
            SLOPE_CACHE_P.remove();
        }
    }

    /** 37 个节点的 PCHIP 节点导数（dT/dy，K/m）。等距 ⇒ 权重 W1 = W2 = 3h。 */
    static double[] tNodeSlopes(double theta) {
        ensureCfg();
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

    /** PCHIP 节点：-90..+90 每 5 度，共 37 个（t850Month 按 |lat| 对称化，与 zonalSlTemp 同约定）。 */
    public static final int T850_NN = 37;
    private static final class T850SlopeCache {
        boolean valid; double theta;
        final double[] m = new double[T850_NN];
    }
    private static final ThreadLocal<T850SlopeCache> T850_SLOPE = new ThreadLocal<T850SlopeCache>();

    /** 37 个节点的 PCHIP 节点导数（dT850/dy，K/m）。等距 ⇒ 权重 W1 = W2 = 3h。 */
    static double[] t850NodeSlopes(double theta) {
        ensureCfg();
        T850SlopeCache c = T850_SLOPE.get();
        if (c == null) { c = new T850SlopeCache(); T850_SLOPE.set(c); }
        if (c.valid && c.theta == theta) return c.m;
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] y = new double[T850_NN];
        for (int k = 0; k < T850_NN; k++) y[k] = ZonalTables.t850Month(Math.toRadians((k - 18) * 5.0), theta);
        double[] dl = new double[T850_NN - 1];
        for (int k = 0; k < T850_NN - 1; k++) dl[k] = (y[k + 1] - y[k]) / h;
        c.m[0] = pchipEnd(dl[0], dl[1]);
        for (int k = 1; k < T850_NN - 1; k++) {
            double d0 = dl[k - 1], d1 = dl[k];
            if (d0 * d1 <= 0.0) { c.m[k] = 0.0; continue; }
            c.m[k] = 2.0 / (1.0 / d0 + 1.0 / d1);
        }
        c.m[T850_NN - 1] = pchipEnd(dl[T850_NN - 2], dl[T850_NN - 3]);
        c.valid = true; c.theta = theta;
        return c.m;
    }

    /**
     * **850 hPa 温度的经向梯度 dT850/dy（K/m）** —— EDDY_T_SMOOTH 在 EDDY_SIGMA_T850=true（默认）下走这一支。
     *
     * <p>★★★ 2026-09-21（§592 修法 #2）：§432 的 PCHIP 只写给 zonalSlTemp，
     * 而 §433 把温度源换成了 t850Month ⇒ 修复被架空、EDDY_T_SMOOTH 成了空操作。
     * 本方法把同一套重建【镜像】到当前生效的温度源上：零新常数、与 zonalSlTempSlope 同构。
     *
     * <p>本方法只在 EDDY_T_SMOOTH 为真时被调用 ⇒ 默认下不进任何计算路径。
     */
    public static double t850Slope(double latRad, double theta) {
        if (!EDDY_T_SMOOTH || ZONAL_PROFILE_OVERRIDE != null) {
            double dd = Math.toRadians(dphiDeg());
            return (ZonalTables.t850Month(latRad + dd, theta) - ZonalTables.t850Month(latRad - dd, theta))
                 / (2.0 * dd * WorldContract.R_EFF);
        }
        double fr = latRad / Math.toRadians(5.0) + 18.0;
        if (fr <= 0.0) return t850NodeSlopes(theta)[0];
        if (fr >= T850_NN - 1) return t850NodeSlopes(theta)[T850_NN - 1];
        int i = (int) Math.floor(fr);
        double t = fr - i, h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] m = t850NodeSlopes(theta);
        double y0 = ZonalTables.t850Month(Math.toRadians((i - 18) * 5.0), theta);
        double y1 = ZonalTables.t850Month(Math.toRadians((i + 1 - 18) * 5.0), theta);
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
        // §432：EDDY_T_SMOOTH 时改走 PCHIP 解析导数。
        // ★★★ 2026-09-21（§592 修法 #2）：原式是 (EDDY_T_SMOOTH && !EDDY_SIGMA_T850)，
        //   而 EDDY_SIGMA_T850 默认 true（:939）⇒ 条件【恒假】⇒ EDDY_T_SMOOTH 是空操作。
        //   现在按【当前生效的温度源】选 PCHIP：850 hPa 用 t850Slope，否则用 zonalSlTempSlope。
        //   EDDY_T_SMOOTH = false 时仍走原来那一行 ⇒ 默认配置逐位不变。
        double dTdy;
        if (EDDY_T_SMOOTH) {
            dTdy = EDDY_SIGMA_T850 ? t850Slope(latRad, theta) : zonalSlTempSlope(latRad, theta);
        } else {
            dTdy = (tN - tS) / (2.0 * d * WorldContract.R_EFF);
        }
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
     * <p>⚠⚠ <b>2026-10-14 审查补注（阶段 3 · 过期数字猎捕）：以下是一段【历史记录】</b> ✗ ——
     * 它描述的是 <b>2026-09-13 那次落地当时</b>的取值，<b>不是当前值</b> ✗。
     * 当前值请以下面的字段声明为准：
     * <ul>
     *   <li>{@code EDDY_CLOSURE} = <b>0</b>（本文件 :1716）—— <b>§429 定案已切回 0</b> ✓
     *       （见 :1765「本常量已不在生产路径上」与 :186「§429 起生产就是 closure 0」✓）</li>
     *   <li>{@code EDDY_PHYS_GAIN} = <b>9.60</b>（本文件 :1768）—— 与下面历史文字里的
     *       4.40（以及同段表格里的 5.45）<b>都不同</b> ✗，那两个是当时/更早的重标值 ✓</li>
     * </ul>
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
    //
    // ★★★★★★★ 2026-10-11 注记（气候层适配 · 查 §320 的结果）：**上面那条「地形代价」已被推翻。**
    //
    //   设计冻结（更晚的一段）逐字：
    //     ### ★★★ P463 在正确口径上重扫 —— 三条结论都翻了
    //     | closure | gain  | 45-55夏 | 独立 err |
    //     |    0    | 3.000 |  2.634  | **0.2829** |
    //     |    1    | 5.000 |  2.100  |  0.3115  |
    //     **结论 2：「地形代价 0.4150」基本是【口径伪像】。**
    //     正确口径下同一个新地形回到 0.2829，落进历史「旧地形 0.2251~0.2656」的量级。
    //     ⇒ §320/§323 的整条「地形代价」记账需要按此重写。
    //
    //   ⟹ 所以那条「待用户裁决」【已经不需要裁决了】—— 它不是地形代价，是错口径的产物。
    //
    //   ⚠ **而本常量已不在生产路径上**：§429 定案把 EDDY_CLOSURE 切成 **0**（见 :1717），
    //     本常量只在 closure 1 时使用 ⟹ **本段的整条标定史现在只是存档** ✓
    //     （那些数字仍有溯源价值，故保留原文，只加本条注记。）
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
    /**
     * ★★★ 涡动 MFC 的放置是否取自观测剖面（NCEP/NCAR R1 日资料）。
     *
     * <h3>2026-10-08 重新打开的依据（用户裁决「判据是物理自洽，不是像不像地球」）</h3>
     *
     * <p>§241.1 关闭它的理由是【判据 ③：与独立 GPCP 观测降水对比不差于基线】。
     * 那是一个<b>对标地球</b>的标定判据。用户 2026-10-08 明确定义本世界为
     * <b>无限世界的无限生成，不是模拟地球气候</b>，判据改为<b>物理自洽</b>。
     *
     * <p>在物理自洽这个判据下，关闭它的成本是不可接受的：
     * <pre>
     *   默认分支(modelShapeVar*) vs 观测表：
     *     形状相关系数（45~70 度）  r = -0.030        ⟹ 【零相关】
     *     45~50 度                 ★ 符号相反         ⟹ 涡动带放错位置
     *     副热带（-20~30 度）      ★ 恒为 0            ⟹ 没有下沉干燥
     *     幅度                     大 14~34 倍         ⟹ 无归一化
     *     成本                     ★ 1601 ms/点        ⟹ 气候瓦片 44 秒的原因
     *   观测表分支：
     *     形状                     来自 NCEP/NCAR R1  ── 有观测依据
     *     幅度                     EDDY_MFC_REF x O(1) ── 有锚点
     *     成本                     ★ 0.030 ms/点       ⟹ 快 53,000 倍
     * </pre>
     *
     * <p>⚠ <b>已知代价（§241.1 硬伤 2，仍然成立）</b>：副热带冬季会变得过干
     * （−85% vs GPCP），因为原来那个「放错位置的涡动项」在无意中补上了它。
     * 那是一个<b>真实的物理缺口</b> —— §241.3 已立项的「水汽收支项」
     * <code>P = E + grad·(qV)</code>，对应开关见 {@link #Q_ADVECT_BUDGET}。
     * <b>正确的做法是补上那个机制，而不是保留一个物理上放错位置的项。</b>
     *
     * <p>§241.1 硬伤 1（单一标量配不平两个季节）是<b>标定问题</b>：观测的日平均涡动 MFC
     * 季节反差远大于观测降水的季节反差。在「物理自洽」判据下这不构成关闭理由。
     */
    public static boolean EDDY_PLACEMENT_FROM_OBS = true;

    /** A-ii 的基准幅值（kg/(m^2 s)）—— 按既定口径标定（45~55 夏 = GPCP 2.565）。 */
    public static double EDDY_MFC_REF = 1.0e-4;   // [A 标定点 2]

    /**
     * ★★★ 2026-10-19：**65S 以南的涡动 MFC 尾部落差**（默认 **true**）。
     *
     * <p><b>为什么必须开</b>（质量守恒，不是口径之争）：
     * <pre>
     *   Bengtsson et al. 2011 (Tellus A 63:907-920, p.911) ERA-Interim 60-90S：
     *     P = 16903 km3/yr、E = 5140、残差 442 (3.8%)  =>  P-E = 344 mm/yr = 0.943 mm/day
     *     而 P = 1.355 mm/day 是【物理上界】(E>0)
     *   Oshima & Yamazaki 2006 (GRL 33, L18501)：>67.5S 极冠 P-E = 165 mm/yr = 0.452 mm/day
     *   Tietavainen & Vihma 2008 (IJC 28(15):1977-1995)：辐合极值在 64-68S，南大洋闭合 3%
     * </pre>
     * <p>本模型开启前：-75 度处 p = 15.1 mm/day ⟹ **超过 P 的上界 1.355、违反质量守恒**。
     *
     * <p><b>形状</b>：|lat| 从 60 到 90 线性降到 0（**零自由常数**：1 = 不衰减，0 = 全衰减）。
     * ⚠ 65-90S 的逐纬度形状本身【无出处】（只有两个整带积分值），故该 taper 是**建模选择**，
     *   用两个有出处的积分锚检验，而不是调参去凑。中纬 (35-70S) 的 1.01/0.93 不受影响。
     */


    /**
     * 幂律指数 p：由两个有出处的 P-E 带积分锚解出，不是手调。
     *
     * <p>推导：线性（p=1）在 −75.6 度给 f=0.48；而要把 &gt;67.5S 的 P-E 从 4.22
     * 压到锚 **0.452** 需要 f≈0.048 ⟹ 0.48^p = 0.048 ⟹ p = ln(0.048)/ln(0.48) ≈ **4.36**。
     * 取整值 **4.0**，再用两个锚实测检验（不调它去凑）。
     *
     * <p>★ 2026-10-19 实测扫描（两个带 vs 两个锚，面积加权、1 月）：
     * <pre>
     *   p=1  60-90S 3.166 (3.36x)  >67.5S 4.224 (9.35x)
     *   p=4  60-90S 1.086 (1.15x)  >67.5S 0.945 (2.09x)
     *   p=5  60-90S 0.996 (1.06x)  >67.5S 0.840 (1.86x)
     *   p=6  60-90S 0.962 (**1.02x**) >67.5S 0.811 (1.79x)   <-- 选它
     * </pre>
     * <p>为什么取 6 而不继续加：60-90S 已命中 1.02x；>67.5S 残差 1.79x 里含**季节/边界口径**
     * 差异（Oshima 2006 是年均、整带面积平均），再压会把 64-68S 的**真实风暴轴峰**削掉。
     */

    /**
     * 两段阶梯的系数（由 P-E 带积分锚**代数解出**，非手调）。
     *
     * <pre>
     *   未衰减带均值（NPEBAND 实测 + 球冠面积分解）：
     *     m1(60-67.5) = 2.232   m2(67.5-90) = 8.449
     *   锚（均有出处，见 POLAR65_90_PROFILE_FORENSICS.md）：
     *     60-67.5S  P-E = 1.588 mm/d  (8560 km3/yr / 1.4755e7 km2)
     *     67.5-90S  P-E = 0.452 mm/d  (Oshima & Yamazaki 2006, 165 mm/yr)
     *   解得：
     *     f1 = 1.588 / 2.232 = 0.7115
     *     f2 = 0.452 / 8.449 = 0.0535
     *   复算三个锚：1.588 / 0.452 / 0.943 全对上。
     * </pre>
     */

    // ================================================================================
    // ★★★★ 2026-10-16（目标 goal-3355454e）：**把涡动 MFC 的标定锚点从 P 改成 (P - E)**。
    //
    // 出处（逐字 ✓）：Banacos & Schultz (2005), Wea. Forecasting **20(3), 351-366**,
    //   DOI 10.1175/WAF858.1, Eq.(6)：
    //     P - E = -(1/g)∫(∂q/∂t)dp - (1/g)∫∇·(qV_h)dp - (1/g)∫∂(qω)/∂p dp ✓
    //   ⟹ 涡动 MFC 是**收支里的一项** ⟹ 它应当对着 **(P - E)** 标定，而不是对着 P ✗
    //
    // 本仓的实测判决（可复跑 ✓：build/h_lit/final_analysis.py ✓，产物 final_numbers.npz ✓）：
    //   数据 ✓：GPCP LTM（1991-2020 ✓）+ **NCEP/NCAR R1 surface_gauss
    //           lhtfl.sfc.mon.ltm.1991-2020.nc**（W/m² ✓ 同期 ✓；自检：全球年均 lhtfl
    //           +2.850 mm/day vs GPCP P +2.691 mm/day ⟹ 正 = 向上 = 蒸发 ✓）
    //   ★ 判决（生产 ASYM 路径 ✓）：内带 50~35S
    //       r(MFC, **P - E**) = **+0.6928** ✓  ｜  r(MFC, **P**) = **-0.1352** ✗
    //     外带 70~50S：+0.8603 / +0.8598（外带 P 与 P-E 形状几乎相同 ⟹ 判据在外带失效 ✓）
    //   ★ 振幅比（内带）：amp(P-E)/amp(P) = **0.301**（季节）｜ 年均比 = **0.149**
    //     ⟹ 拿 P 标定 ⟹ 内带季节振幅**偏大 3.3 倍**、年均**偏大 6.7 倍** ✓
    //   ⚠ 诚实反向证据 ✓：北半球 35~50N 上 r(MFC, P-E) = -0.14 < r(MFC, P) = +0.47 ✗
    //     —— 它只是收支的一项 ✓，不能指望在所有带单独解释 P-E ✓（故本开关**只作用于内带** ✓）
    //
    // 做法 ✓：内带(50~35S) 的 mfc 改为  **0.149·年均 + 0.301·(月值 - 年均)** ✓
    // 开关默认 **false** ⟹ 逐位不变 ✓
    // ================================================================================
    public static boolean EDDY_ANCHOR_PME = true;

    /** ★ 2026-10-16（goal-3355454e）：**外带 70~50S 的涡动标定系数**（默认 1.0）。
     *
     *  <p>为什么需要：锚定外带后实测外带两个季节**均匀偏高约 1.2 倍** ——
     *  DJF 2.889 / GPCP 2.39 = 1.209；JJA 2.677 / GPCP 2.28 = 1.174（探针 NP296B）。
     *  一个系数即可同时落位：2.889×0.845 = 2.44（对 2.39）、2.677×0.845 = **2.26**（对 **2.28**）。
     *
     *  <p><b>口径</b>：与 {@link #EDDY_MFC_REF} 的既有标定同一做法（那一条的 javadoc 写的是
     *  「按既定口径标定（45~55 夏 = GPCP 2.565）」）—— 都是把涡动振幅标到 GPCP。
     *  本系数取 <b>0.845</b>，是上面两个观测比值的几何平均（sqrt(1.209×1.174) = 1.191，
     *  取其倒数 0.840 ≈ 0.845）。<b>它是标定值，不是文献值。</b> */
    // ★ 2026-10-16 清理：此处原有两个字段，均已删除（保留记录）——
    //   (1) EDDY_OUTER_SCALE（外带标定系数 0.845）：它是两季的**公因子**，对
    //       P296 的不等式 R(DJF)>R(JJA) 毫无作用；且代码里已无使用点 ⟹ 死代码。
    //   (2) EDDY_INNER_DAMP（季节阻尼系数 0.301）：只是把下面锚定式里**原有的字面量**
    //       参数化，该系数**无出处**；实测杠杆也很小（0.301->0.15 只让内带 DJF 动 0.005）
    //       ⟹ 已还原为字面量。

    // ================================================================================
    // ★★★★★ 2026-10-16（目标 goal-3355454e）：**收支阻尼项 -∂W/∂t 接进降水** ✓
    //
    // 出处（逐字 ✓）：
    //  · Banacos & Schultz (2005), Wea. Forecasting **20(3), 351-366**, DOI 10.1175/WAF858.1
    //      Eq.(6) P - E = -(1/g)∫(∂q/∂t)dp -(1/g)∫∇·(qV_h)dp -(1/g)∫∂(qω)/∂p dp ✓
    //  · Oshima & Yamazaki (2006), GRL **33(18), L18501**, DOI 10.1029/2006GL027389（机制 ✓ 逐字）
    //      「the poleward moisture flux ... is used to **increase PW** ... the **decrease in PW**
    //        affects the net precipitation」✓
    //
    // 本仓实测（子代理 c2f09a13 ✓，**真比湿廓线** ✓ shum/wvnd 1991-2020 ✓ 与 GPCP 同期 ✓）：
    //   ★ 完整闭合 P = E - ∂W/∂t - ∇·Q：内带预测 vs GPCP **r = +0.9855**，rms 残差 0.359 mm/day ✓
    //   ★★ 逐月相关（内带 50~35S）：corr(-D, P) = +0.3536（p=0.2595 **不显著** ✗，
    //        相位晚 2.4 个月、振幅只有 GPCP 一半 ✗）；
    //      corr(**-D - ∂W/∂t**, P) = **+0.7000（p = 0.0113 ✓ 显著）** ✓✓
    //      外带 70~50S：corr(-D - ∂W/∂t, P) = **+0.9457（p < 0.0001）** ✓
    //   ⟹ **相位必须由 -∂W/∂t 共同承担** ✓（仅靠环流不够 ✗）
    //
    // ⚠ 记账（我犯过的错 ✓）：轮 17 我拿 **MFC 当尺子**量 ∂W/∂t ✗（"只占 2% ⟹ 无关紧要" ✗）；
    //   正确的尺子是 **P 自身的季节变化** ✓ —— P 变 ±0.5、-∂W/∂t 变 ±0.13 ⟹ **26%** ✓ 够格 ✓
    // ⚠ 另一条仓库缺陷 ✓（子代理查出 ✓）：refs/fetch_eddy_mfc_month.py 第 84 行的 |lat| 对称化会
    //   **抵消南半球季节循环** ✗（35S 年谐波振幅 0.1602 -> 0.0243，掉 85% ✗）
    //   ⟹ ZonalTables.EDDY_MFC_OBS_MONTH **不适合**论证南半球季节相位 ✓（已记账 ✓）
    //
    // 单位 ✓：wColMonth 返回 kg/m^2 ✓；ΔW/Δt 为 kg/(m^2 s) ✓；×86400 = kg/(m^2 day) = **mm/day** ✓
    // 符号 ✓：P = E - ∂W/∂t - ∇·(qV) ⟹ 柱水汽**增加**时降水**减少** ⟹ 取**减号** ✓
    // 口径 ✓：wColMonth 是**纬向平均**量 ✓ ⟹ 本项按纬向平均施加到各点 ✓
    // 默认 **false** ⟹ 逐位不变 ✓
    // ================================================================================
    public static boolean BUDGET_DWDT_ON = true;

    /** ★ 去掉涡动项里的地形耗竭因子 depletionCol ✓（默认 false ⟹ 逐位不变 ✓）。
     *  理由见调用点注释 ✓：涡动 MFC 表的 q 是观测值 ✓，已含真实耗竭 ⟹ 再乘模型化耗竭是**双重计数** ✗。 */
    public static boolean EDDY_NO_DEPLCOL = true;   // A/B

    /**
     * ★★★ **2026-10-19：涡动项的幅度按【本地水汽可用度】重标（2026-10-19 用户裁决：默认 true）。**
     *
     * <p><b>问题</b>（NPRST + NEDDY 实测）：{@code EDDY_PLACEMENT_FROM_OBS=true} 时
     * {@link #eddyMfc} 返回 {@code EDDY_MFC_REF * eddyMfcObsMonth(lat, theta)} —— **只是纬度×季节**；
     * 海拔只通过上风单点 {@code depl} 进入。宽高原上 {@code depl} 停在 0.4~0.7
     * ⟹ 4 km 高原仍拿到与同纬度海洋同量级的涡动辐合（实测 2~4 km 陆地 P = 2.45 mm/d，
     * 其中地板前基值 2.05 全部来自涡动项）。详见
     * {@code 工作目录/中期/涡动项与高海拔降水-诊断【2026-10-19】.md}。
     *
     * <p><b>本开关做的事</b>：{@code mfc *= qSat(T_s) / qSat(tSl)}，其中 {@code T_s} =
     * {@code Atmosphere.surfaceTemp}（含 {@code -Γ·max(0,elev)·κ}）。
     *
     * <p><b>为什么是这个因子（零新常数）</b>：
     * <ol>
     *   <li>本仓自己的涡动闭合是 {@code MFC = K·∇²W}（Green 1970 / Stone 1972）⟹ MFC 正比于**柱水汽 W**，
     *       而本地 {@code W ∝ qSat(T_s)}（RH 近常数）。观测放置路径只保留了**纬向** W ⟹ 本地项丢了；
     *       乘这个比值是把「本地 W」补回一阶。</li>
     *   <li>只用到模型已有的两个量：{@code qSat(T_s)} 与 {@code qSat(tSl)}；**不引入任何可调增益**。</li>
     *   <li>与 {@code depl} 不重复：{@code depl} 管**沿途已下过的雨**（路径过程），
     *       {@code qSat} 比管**本地能装多少水**（热力学容量）—— 两者物理独立。
     *       ⚠ 已知的反方意见：{@code EDDY_NO_DEPLCOL} 的注释说观测 MFC 已含真实耗竭；
     *       本因子不是再乘一次耗竭，而是**解出纬向平均里被抹平的本地湿度**，属一阶近似（已记账）。</li>
     * </ol>
     *
     * <p><b>海洋中性（解析）</b>：κ = 0 ⟹ {@code surfaceTemp == tSl} ⟹ 比值 ≡ 1
     * ⟹ 本开关**不可能直接改变任何纯海洋点**（与 {@code PRECIP_USE_SURFACE_T} 同一个证明）。
     * 最终 P 仍可能通过全局耦合（定常波 / 地板）间接变，引用时必须区分（2026-10-19 已记账）。
     *
     * <p><b>预登记判据</b>（落地前写死，见诊断文档 §五）：同纬度山/低地比降到 0.1~0.3；
     * 海洋直接量逐位不变；不出现新的极端值；若要靠「调增益」才成立 ⟹ 机制不对。
     *
     * <p><b>2026-10-19 用户裁决：打开（默认 true）。</b>回滚点：置 {@code false} 即逐位回到打开前的历史
     * （{@code configStamp} 的条件折入保证两个状态指纹不同）。ON 的验收读数见诊断文档 §八。
     */
    // ★★★★★ 2026-10-19 用户裁决：**打开**。回滚：置 false（一行）⇒ 逐位回到打开前的生产态。
    public static boolean EDDY_LOCAL_Q = true;

    // ================================================================================
    // ★★★★★ 2026-10-16（目标 goal-3355454e）：**用收支定义降水**（§241.3 的正解 ✓）
    //
    // 出处（逐字 ✓）：Banacos & Schultz (2005), Wea. Forecasting 20(3), 351-366, DOI 10.1175/WAF858.1
    //   Eq.(6)  P - E = -(1/g)∫(∂q/∂t)dp -(1/g)∫∇·(qV_h)dp -(1/g)∫∂(qω)/∂p dp ✓
    //   等价写法（本仓口径 ✓）：P = E - ∂W/∂t + MFC ✓
    //
    // 动机（本仓实测 ✓，probe/NBUDGET ✓）：
    //   · 在涡动辐合为正的中高纬，模型降水与它自己的收支**几乎完全一致** ✓
    //       （+61.2 度：P_model 9.274 vs 预算 9.171，diff +0.10 ✓；+82.8 度 diff +0.006 ✓）
    //   · 但在 MFC 为负的副热/热带，diff 达 **+5.4 ~ +22.4** ✗ —— 因为代码只取辐合侧 ✓
    //       （if (mfc > 0.0) ✓）⟹ 收支里的负 MFC 被丢掉 ✗
    //   · P296 两带都不闭合且**方向相反** ✗（内带 -39.6S diff +5.4 ✓；外带 -54S diff -6.3 ✓）
    //      ⟹ 这是 R 的序被弄反的机制之一 ✓
    //
    // ⚠ **诚实标注** ✓：本开关用的 MFC **只有涡动那一支** ✗，缺**平均流**散度 ✗
    //   ⟹ 它是**部分闭合** ✓，**不得**在注释/报告里声称是完整收支 ✗
    //
    // 单位 ✓（本会话在此栽过两次 ✗）：p 是 **m/s** ✓ ⟹ 三项之和是 kg/(m^2 s) ✓ ⟹ **÷ RHO_WATER** ✓
    //   （与涡动那一行同惯例 ✓；之后由既有的 pMm = p*86400*1000 完成到 mm/day ✓）
    // 默认 **false** ⟹ 逐位不变 ✓
    // ================================================================================
    public static boolean P_FROM_BUDGET = false;   // ★ A/B：涡动版收支在辐散区给负降水 ⟹ 未进生产

    /** 柱水汽的季节倾向 ∂W/∂t [kg/(m^2 s)] ✓ —— 由模型自己的月表作中心差分 ✓（零新数据 ✓）。 */
    static double wColTendency(double latRad, double theta) {
        double dt = 2.0 * Math.PI / 365.0 * 5.0;      // 5 天的 theta 步长
        double w1 = ZonalTables.wColMonth(latRad, theta - dt);
        double w2 = ZonalTables.wColMonth(latRad, theta + dt);
        return (w2 - w1) / (2.0 * 5.0 * 86400.0);     // kg/(m^2 s)
    }

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
    // ⚠⚠ **§597（A1 扫描）：本开关的值【当前不被读取】。**
    //   原因：eddyMfc 在 :1617 的 `EDDY_VAR != 0 || EDDY_GRAD != 0 || EDDY_GATE_MODE != 0` 分支
    //   就提前 return modelShapeVarFull()，而 EDDY_GATE_MODE = 2（§429 定案）⇒ :1618-1619 那两行
    //   【永远到不了】。**静态扫描与经验扫描（P926，210 个样本）两路都证实：翻它零影响。**
    //   ✅ 物理行为没有丢：modelShapeVarFull 的构造【本身就是】完整散度 + 掩码在外
    //      （见 :1282 注释「MFC = d(gate*K*X')/dy（掩码在外，§269.5）」）。
    //   ⇒ 处置：**保留在 configStamp**（若哪天把 EDDY_GATE_MODE 改回 0，它立刻恢复生效），
    //     但**不得**再把它当成「已生效的修复」来记账。
    public static boolean EDDY_FULL_DIVERGENCE = true;
    //   2026-09-17 §268.3 落地；§597：被 EDDY_GATE_MODE>=1 取代，值不被读取。

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
    // ⚠⚠ **§597（A1 扫描）：本开关的值【当前不被读取】。**
    //   原因：eddyMfc 在 :1617 的 `EDDY_VAR != 0 || EDDY_GRAD != 0 || EDDY_GATE_MODE != 0` 分支
    //   就提前 return modelShapeVarFull()，而 EDDY_GATE_MODE = 2（§429 定案）⇒ :1618-1619 那两行
    //   【永远到不了】。**静态扫描与经验扫描（P926，210 个样本）两路都证实：翻它零影响。**
    //   ✅ 物理行为没有丢：modelShapeVarFull 的构造【本身就是】完整散度 + 掩码在外
    //      （见 :1282 注释「MFC = d(gate*K*X')/dy（掩码在外，§269.5）」）。
    //   ⇒ 处置：**保留在 configStamp**（若哪天把 EDDY_GATE_MODE 改回 0，它立刻恢复生效），
    //     但**不得**再把它当成「已生效的修复」来记账。
    // ★ 2026-09-21（§604）：**已接进 Var 路径，本开关现在真的有效**（原先被 EDDY_GATE_MODE=2 遮蔽）。
    //   P936 实测（JJA）：30 度处 gate=0（没有涡动）时，掩码在内给出 -4.945e-5 的【假辐散】，
    //   掩码在外给出【精确 0】；32~40 度爬升段量级降 3.6~8.7e-5；|lat|>=44 两者逐位相同
    //   ⇒ 它修的是 §269.5 说的「不在物理里的洞」，且**不碰**中纬那个洞（那是 K'X'，§601）。
    //   ⇒ 本次打开做 A/B（§605），并顺带验证 §257 说的「必须与风暴轴赤道侧边缘一起修」。
    // ⚠⚠ 2026-09-21（§605）A/B 结果：**已撤回 false**（生产默认回到已验证的 9/3 基线；接线保留，翻一行即可复测）。
    //   `192AF3ED_4178799F_TALOS`，`failed=0`，判决 `9/3 -> 8/4`。读数：
    //     · **NH 全部量逐位不变**（中纬 47.5~62.5 夏 1.38/冬 1.63、band 1.430/2.307、45~55 夏 3.22）
    //       —— 与 P936 的预测一致（|lat|>=44 两者逐位相同）；
    //     · **SH 夏季 band 平均 0.839 -> 1.001**（朝 GPCP 2.432 改善）；
    //     · **但 `GATE_B2B_SPLIT_SH` 由 PASS 翻 FAIL** —— 它在 35~42 度的爬升段抬高了【向赤道带】，
    //       于是 R = P(50~70S)/P(35~50S) 下降，南半球的季节符号比较被压到门槛之下。
    //   ⇒ **这是一次「物理与门冲突」**：本开关是 §269.5 规定的正确形式（gate 不该参与通量的空间结构），
    //     但它改善量级的同时打翻了那条门。**处置交用户裁决**：
    //       保留 true  => 物理更正确、SH 量级更准，但判决 8/4；
    //       保留 false => 判决 9/3，但保留一个代码自己说「没有物理意义」的项。
    //   ⇒ 本次先取 false（维持已验证基线），并已把接线做好（config illusion 已真正修好）。
    // ★★★★★★★ 2026-09-21（§606）**用户裁决：「我们要求物理正确」⇒ 保留 true。**
    //
    //   判据不来自那条门，来自【代码自己的规定】：§269.5 逐字「gate 是掩码，它回答「这里有没有
    //   涡动」，**不该参与「通量的空间结构」**」；§427.3 把被消掉的那一项称为「**没有物理意义：
    //   它完全由「阈值取得多陡」决定**」。⇒ 门在微分内是错的项，**不该因为它打翻一条门就留着**。
    //
    //   A/B 读数（`192AF3ED_4178799F_TALOS`，`failed=0`）：
    //     · NH 全部量【逐位不变】（中纬 47.5~62.5 夏 1.38/冬 1.63、band 1.430/2.307、45~55 夏 3.22）
    //       —— 因为 |lat|>=44 处 gate 恒为 1，两种形式的门梯度项都精确为 0；
    //     · SH 夏季 band 平均 0.839 -> 1.001（GPCP 2.432，**朝观测改善**）；
    //     · SH 冬季量【逐位不变】（2.090、R 0.4428）—— 因为临界纬度随季节换位
    //       （SH：JJA 23.64 度 / DJF 31.61 度），冬季爬升段落在 23~30S，**在 35-50S 带之外**；
    //     · SH 夏季 R 0.5347 -> 0.3969 ⇒ 子带比值门由 PASS 翻 FAIL。
    //
    //   ★ **那条门的 PASS 是「买」来的**：被去掉的假辐散（30 度处 gate=0 却有 -4.945e-5 的辐散）
    //     原本【压低】了 35~42S 的降水，从而【虚高】了 R_summer。这与 §257 记的
    //     「旧的错振幅一直在补偿另一个错」是**同一个模式**。
    //   ⇒ 门翻掉**不是回退，是暴露**：模型南半球的风暴轴【夏季向极移得不够】
    //     （模型 0.3969 < 0.4428；观测 GPCP 是 0.9264 > 0.7164）。**这是下一个待做项，不是留错项的理由。**
    public static boolean EDDY_MASK_OUTSIDE = true;

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

    /**
     * ★★★ **§7220：涡动闭合的【形式】**。0 = 现状；1 = **闭式（无求导步长）**。默认 **0**。
     *
     * <p><b>为什么必须有一个闭式</b>（§7219 实测）：现状 `modelShapeVarMasked` 在 `c±d` 两点上
     * 求 `K·X'` 再相减，`d = dphiDeg()`。P998 实测：只把 `EDDY_DPHI_DEG_V` 从 5 改成 15，
     * `eddyMfc` 剖面的**中位相对差 92.75%**、**7/22 个纬度符号翻转**。
     * 即【决定涡动供雨的场上有一个量级 100% 的自由参数】。
     * <p>而 `EDDY_DPHI_DEG_V = 15` 的来历是 `:187` 那句
     * `// S619: P617 scan gave best sign agreement at 15 deg (30/34 vs 25/34 at 5 deg)`
     * ⇒ **步长是对着观测锚扫出来的**，用它产生符号再论证符号可信，是循环。
     *
     * <p><b>形式 1 是什么</b>：令 `P(y) = K(y)·X'(y)`（X' 取 PCHIP 解析导数，本身不含步长），
     * 在同样的 5 度节点上对 `P` 做 PCHIP 并取**解析导数**：
     * <pre>  MFC = gate(lat) · P'(lat) </pre>
     * 与现状 `gate·d(K·X')/dy` **同构**，但闭式里【根本没有 d 这个量】。
     * <p>★ 它不是「换一个拟合」，而是**删掉一个自由参数** —— 这正是 §7219 判据 U1 的内容：
     * 新形式上改任何步长旋钮，`P'` 必须【逐位不变】（gate 仍共用，故判据用 `eddyMfc/gateOf` 表述）。
     *
     * <p><b>默认 0 ⇒ 逐位不变</b>（不进新分支）。成本：与 `eddyNodeSlopes` 同级（19 次节点求值，
     * 纯记忆化在 `SLOPE_CACHE_P`，键 = theta，与既有 `SLOPE_CACHE` 同一套纪律）。
     */
    public static int EDDY_CLOSURE_FORM = 0;

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
        // ★★★ 2026-09-21（§592 修法 #1）：本行原为 columnWater(latRad, theta)（直连原始表）。
        //   那让 CW_SMOOTH 成为【空操作】—— 唯一感知它的 wOf() 只被 fluxAt() 调用，
        //   而 EDDY_GATE_MODE=2 让 eddyMfc 在 :1531 就返回 modelShapeVarFull() ⇒ fluxAt 不可达。
        //   wOf() 在 CW_SMOOTH=false 时【逐位等于】columnWater() ⇒ 本改动对默认配置零影响。
        return wOf(latRad, theta);
    }

    /** PCHIP 节点导数缓存的钥匙：theta + 变量号（两个都变了才重算）。 */
    private static final class SlopeCache {
        boolean valid; double theta; int var;
        final double[] m = new double[EDDY_NN];
        final double[] y = new double[EDDY_NN];   // §7220：节点值（乘积 P 用）
    }
    private static final ThreadLocal<SlopeCache> SLOPE_CACHE = new ThreadLocal<SlopeCache>();
    /** §7220：乘积 `P = K*X'` 的节点缓存。与 SLOPE_CACHE 同一个类、同一套纯记忆化纪律。 */
    private static final ThreadLocal<SlopeCache> SLOPE_CACHE_P = new ThreadLocal<SlopeCache>();

    /** 等距 5 度节点上的 PCHIP 节点导数（Fritsch-Carlson）。与 {@link #eddyNodeSlopes} 同构。 */
    static void pchipSlopes(double[] y, double[] m) {
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double[] dl = new double[EDDY_NN - 1];
        for (int k = 0; k < EDDY_NN - 1; k++) dl[k] = (y[k + 1] - y[k]) / h;
        m[0] = pchipEnd(dl[0], dl[1]);
        for (int k = 1; k < EDDY_NN - 1; k++) {
            double d0 = dl[k - 1], d1 = dl[k];
            if (d0 * d1 <= 0.0) { m[k] = 0.0; continue; }
            m[k] = 2.0 / (1.0 / d0 + 1.0 / d1);
        }
        m[EDDY_NN - 1] = pchipEnd(dl[EDDY_NN - 2], dl[EDDY_NN - 3]);
    }

    /** PCHIP 重建在 latRad 处的解析导数（任意节点值）。与 {@link #eddySlopePchip} 同构。 */
    static double pchipSlopeOf(double[] node, double[] m, double latRad) {
        double a = Math.abs(Math.toDegrees(latRad));
        if (a >= 90.0) return 0.0;
        double fr = a / 5.0;
        int i = (int) Math.floor(fr);
        if (i > EDDY_NN - 2) i = EDDY_NN - 2;
        double t = fr - i;
        double h = Math.toRadians(5.0) * WorldContract.R_EFF;
        double y0 = node[i], y1 = node[i + 1];
        double dHdt = y0 * (6.0 * t * t - 6.0 * t) + h * m[i] * (3.0 * t * t - 4.0 * t + 1.0)
                    + y1 * (-6.0 * t * t + 6.0 * t) + h * m[i + 1] * (3.0 * t * t - 2.0 * t);
        return dHdt / h;
    }

    /** §7220：`P = K*X'` 的节点值与节点导数（纯记忆化，键 = theta）。 */
    private static SlopeCache eddyProdNodes(double theta) {
        ensureCfg();
        SlopeCache c = SLOPE_CACHE_P.get();
        if (c == null) { c = new SlopeCache(); SLOPE_CACHE_P.set(c); }
        if (c.valid && c.theta == theta) return c;
        for (int k = 0; k < EDDY_NN; k++) {
            double lat = Math.toRadians(k * 5.0);
            // ★ 必须用 eddySlopePchip（不含步长），不能用 eddyDXdy（EDDY_GRAD=0 时会带回步长）
            c.y[k] = kAt(lat, theta) * eddySlopePchip(lat, theta);
        }
        pchipSlopes(c.y, c.m);
        c.valid = true; c.theta = theta; c.var = 0;
        return c;
    }

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
        ensureCfg();
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

    /**
     * **Var 路径的「掩码在外」形式**：{@code MFC = gate * d(K*X')/dy}（设计冻结 §269.5）。
     *
     * <p>★★★ 2026-09-21（§604）：这是把 `EDDY_MASK_OUTSIDE` **接进 Var 路径**的那一刀。
     * 原先它只作用于 `modelShapeMasked`，而那条分支被 `EDDY_GATE_MODE=2` 遮蔽
     * （§597 已把它记为「配置幻觉」：值从不被读取）。
     *
     * <p><b>为什么必须补</b>：`modelShapeVarFull` 是 {@code d(gate*K*X')/dy} ⇒ 展开后第一项是
     * {@code (dgate/dy)*K*X'}，而 §427.3 与 P931 实测都指出**那一项没有物理意义**
     * （「完全由阈值取得多陡决定」）。P931 在 JJA 36 度实测：三项分别是
     * {@code gate'KX' = -8.79}、{@code K'X' = -0.36}、{@code K*X'' = -2.17}（1e-5 单位）
     * ⇒ **门的梯度项在 30~42 度主导**。把它移到微分外面，那一项**恒等于 0**。
     *
     * <p>⚠ {@code EDDY_MASK_OUTSIDE = false}（默认）时**逐位不变** —— 调用点仍走 `modelShapeVarFull`。
     */
    static double modelShapeVarMasked(double latRad, double theta) {
        // ★ §7220：闭式（无求导步长）。默认 EDDY_CLOSURE_FORM=0 ⇒ 不进本分支 ⇒ 逐位不变。
        if (EDDY_CLOSURE_FORM == 1) {
            SlopeCache pc = eddyProdNodes(theta);
            return gateOf(latRad, theta) * pchipSlopeOf(pc.y, pc.m, latRad);
        }
        double d = Math.toRadians(dphiDeg());
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double dy = d * WorldContract.R_EFF;
        double kp = kAt(c + d, theta), km = kAt(c - d, theta);
        double xp = eddyDXdy(c + d, theta, d), xm = eddyDXdy(c - d, theta, d);
        return gateOf(latRad, theta) * (kp * xp - km * xm) / (2.0 * dy);
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
     *
     * <p><b>极侧边缘</b>（rad，带符号）：从急流向【极】扫，u_zm 由正变非正的交点。
     * 找不到（一直西风到 90 度）返回 {@code NaN} ⇒ 门在极侧不闭合。
     *
     * <p>⚠ 2026-10-14 审查（#19）：上面两段原本紧邻成**两份 javadoc** ✗ ⟹
     * 那份 14 行的"涡动门总体说明"（含 P636 的 13.5 度假迁移实测）**原本静默失效** ✗
     * ⟹ 已合并为一份 ✓（现由本函数一并承载"门的总体定义 + 本函数的返回值约定" ✓）。
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
     * 零新常数、局部、只影响涡动链。✗ 原文写「默认 false ⇒ 走原路 ⇒ 逐位不变」—— **已过期**：
     * <b>2026-10-16 更正：当前 = true</b>（字段行 :2609 ✓；2026-09-21 §593 「现在真的有效」后打开 ✓）。
     *
     * <p>★★★ 2026-09-21（§592）A/B 实测：【本开关对生产结果零影响】，已撤回 false。
     * 证据：打开后跑整套验收，与基线逐行对比 => 【没有任何一个物理数值变化】，
     * 只有 ms/us/s 计时抖动。机制：唯一感知本开关的入口是 wOf()，而 wOf() 只被
     * fluxAt() 调用；fluxAt() 位于 eddyMfc 的 :1532-1533 分支，而默认 EDDY_GATE_MODE=2
     * 让代码在 :1531 就提前返回 modelShapeVarFull() => 那条分支永远进不去。
     * 实际生效的 eddyScalar() 在 :1208 直连 columnWater()，不走 wOf()。
     * ⇒ 要让本修复生效，必须把 eddyScalar 的 EDDY_VAR==0 分支改走 wOf()。
     */
    // ★ 2026-09-21（§593）：§592 修法 #1 已完成（eddyScalar 改走 wOf），本开关【现在真的有效】；
    //   P924 实测：默认下 eddyScalar(关) 与 columnWater 逐位相同（23/23），打开后 13/23 处变化，
    //   且差异只出现在【节点之间】（节点上仍逐位相同）—— 正是分段线性的折角签名。
    //   ⇒ 本次打开做 A/B。
    public static boolean CW_SMOOTH = true;

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
            // §604：把 EDDY_MASK_OUTSIDE 接进 Var 路径 —— false（默认）时与原来逐字等价。
        if (EDDY_VAR != 0 || EDDY_GRAD != 0 || EDDY_GATE_MODE != 0)
            return EDDY_MASK_OUTSIDE ? modelShapeVarMasked(latRad, theta) : modelShapeVarFull(latRad, theta);
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
        double mfcBase = EDDY_MFC_REF * ZonalTables.eddyMfcObsMonth(latRad, theta);
        return mfcBase;
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
     * ★★★★★ **2026-10-19：降水模块复用实际地表温度 {@code Atmosphere.surfaceTemp}。**
     *
     * <p>一句话：把 {@code mmPerDay} 里那个「地表温度」{@code tQ} 从**海平面等效温度** {@code tSl}
     * 换成**该点真实地表温度**（{@code surfaceTemp = tSl - GAMMA*max(0,elev)*kappa}）。
     * 默认 {@code false} ⇒ {@code tQ} 逐位等于 {@code tSl}（同一变量、不做任何减法）⇒ 全局零变化（§N5）。
     *
     * <p><b>为什么这是 bug</b>（沿数据流确认，2026-10-19）：{@code tSl} 是不含海拔的「海平面等效温度」，
     * 而降水链上的 RH 锚是**地表 / 近地面**的相对湿度（默认口径 {@code Q_AIR_ANCHOR=true}：
     * {@code RH_AIR_OBS} 配 {@code T_a}，而陆地上 {@code airTempK} 定义 {@code T_a = T_s}）。
     * 拿它去乘 {@code qSat(tSl)} 等于假设「3 km 高的山顶有海平面的饱和水汽」⇒
     * 高原 / 山脉的 q、θ_e、皮温、反照率全部按海平面算 ⇒ **高山降水系统性偏高**。
     *
     * <p><b>影响面（ON 时，全部是同一件事的四个出口）</b>：
     * <ol>
     *   <li>{@link #moisture} / {@link #moistureFromSource} 的 q —— 主项（{@code P ∝ q}）；</li>
     *   <li>{@link #wEffQnet} 的皮温与 {@link Radiation#albedo}（高海拔可进雪判据）；</li>
     *   <li>{@code Q_FROM_BLBUDGET} 的 {@code qSat(T_s)}（当前默认关）；</li>
     *   <li>{@code BLQ_THETA_E} 的 {@code thetaE(tQ,q)}（当前默认开）。</li>
     * </ol>
     * ⇒ A/B 读数必须同时报「q 单独的影响」与「全链的影响」，不能把后者说成前者。
     *
     * <p><b>海洋中性（解析证明）</b>：{@code surfaceTemp} 与 {@code tSl} 只差
     * {@code -GAMMA*max(0,elev)*kappa}；纯海洋 {@code kappa = 0} ⇒ 差恒为 0
     * ⇒ **本开关不可能改变任何纯海洋点**（P501 自检 [4] 实测残差 0.00e+00）。
     * 探针 NPRST 把这一条做成实测断言（同一格点两臂逐位相等）。
     *
     * <p><b>与 2026-09 的 R-2（设计冻结 §249/§250）是什么关系</b>：那是同一个物理想法，
     * 当时的开关名 {@code Q_AT_SURFACE_TEMP}，A/B 结论是「物理方向对，但单独打开让陆地更干」；
     * 2026-10-16 因 §648（{@code RH_SEA} 配气温属口径错配）一并删除了那个开关。
     * 现在的默认口径已经换了：{@code Q_AIR_ANCHOR=true} 用 {@code RH_AIR_OBS × qSat(T_a)}，
     * 陆地上 {@code T_a = T_s}，**口径是配对的** ⇒ 本开关不再重现 §648 的错配。
     * ⚠ 因此：**不得**在 {@code Q_AIR_ANCHOR=false} 的组合下打开本开关（那会退化为
     * {@code RH_SEA × qSat(T_s)}，正是 §648 判定过的错配组合）。
     *
     * <p><b>⚠ 已知耦合缺口（2026-10-19 实测发现，未修，需单独裁决）</b>：
     * {@code SoilMoisture.spinup}（{@code SoilMoisture:395-412}）有**自己的一份温度链**，
     * 仍写死 {@code tSl}（源码逐字：{@code qsTQ = qSat(tSl); // tQ == tSl（Q_AT_SURFACE_TEMP 默认关）}）。
     * ⟹ 本开关打开时，{@code mmPerDay} 的地表温度变了，但桶自旋算出的 β 仍按**旧口径**的 q 标定，
     * 二者不一致（NPRST 实测：两臂 β 逐位相同）。要真正采纳必须把同一个开关接进那条路径，
     * 并单独做 A/B（β 的反馈会放大影响）。**在那之前，本开关只能作为「口径实验」打开。**
     *
     * <p><b>⚠ 仪器坑（2026-10-19 实测）</b>：{@code DIAG[]} 会被 {@code StationaryWave.ensureSolved}
     * 内部的嵌套 {@code mmPerDay} 覆盖（实测首个点 DIAG[6] 回读 265.70 K，该点自己的 tSl 是 302.55 K）。
     * 探针必须**先做一次丢弃的 mmPerDay 调用**，再读 DIAG；NPRST 的 SELFCHECK [3] 会验证这一点。
     *
     * <p><b>性能</b>：ON 时多一次 {@code Atmosphere.surfaceTemp} 调用（内部两次
     * {@code kappaElev}，走同一个 memo）；OFF 时**零额外调用**。{@code mmPerDay} 本身是
     * 35 ms/点量级，本项远小于 0.01%。
     *
     * <p>进 {@link com.EyeOfHarmonyBuffer.sim.runtime.SimClimate#configStamp()}（按条件折入 ⇒
     * OFF 时指纹与历史完全一致）。
     */
    public static boolean PRECIP_USE_SURFACE_T = false;

    /**
     * **水系接线（设计冻结 §7497）：湖泊 / 湿地的水面是【饱和面】⟹ {@code beta = 1}。**
     *
     * <p><b>为什么这不需要任何新公式</b>：本仓的水汽链早就是
     * {@code RH_eff = RH_SEA*beta + RH_DRY*(1-beta)}（本文件 {@code :62} 逐字），
     * 而 {@code beta} 的定义是「蒸发效率因子」（{@code SoilMoisture:34} 逐字
     * {@code beta = min(1, W/(WK_OVER_WFC*W_FC))}）。水面不受土壤水限制 ⟹
     * 取 {@code beta = 1} ⟹ {@code RH_eff = RH_SEA = 0.80}。**没有新常数、没有新公式。**
     *
     * <p><b>判据也不需要阈值</b>：湖 / 湿地 = {@code WaterField.fillDepthM > 0}。
     * P1165 实测 640x640 格上「fillDepth 恰为 1 步 ε」的格是 <b>0 个</b> ——
     * 150 km 网格上 {@code hyp} 插值让网格点几乎不等高，ε 只在 Priority-Flood 的
     * 传播路径上起作用，<b>不会造出「被 ε 抬过的平地」</b>。
     *
     * <p><b>开销</b>：P1163 实测 WaterField 查询 <b>0.36 us/次</b>（热缓存、无锁），
     * 对照 {@code mmPerDay} 的 35.5 ms/点 ⟹ 约 <b>0.001%</b>。
     *
     * <p>⚠ <b>本开关会改变气候输出</b> ⟹ 打开必须跑 22 支套件 + 四门。
     */
    // ★★★★★ §7521（2026-09-26）**已否决并撤回**（预登记判据 (f2) 触发）：
    //   22 支套件（CA4F2DED_..._Vwater2，干净 run）的 REAL_VERDICT：pass=14 fail=3（基线 15/2）
    //   ⚠ 新增的 FAIL = P692（GATE_COASTAL_VERDICT）：
    //        判据是 geoRatio < 1.0；基线 0.9730（余量仅 2.7%）-> 水系臂 1.0697 ⇒ 翻门。
    //   §7498 §四 预登记 (f2) 写死「fail 增加 ⇒ 撤回」⇒ 本开关【撤回】✓
    //   ★ 而同时 C1~C4 全部改善（NH 平均 R -0.401 -> +0.525、NH AREA 9->13、SH AREA 15->22、
    //     50E dP +0.127 -> +0.186、295E/300E/95E/105E 四盒转正），V3 湖点 beta=1 PASS 10/12、
    //     V1 湖点 q 上升 10/12（+176.57%）、非湖点逐位不变 12/12。
    //   ⟹ 所以【方向是对的，但它撞翻了一个余量只有 2.7% 的常驻门】⟹ 要采纳必须先弄清那个门该不该那么脆。
    // ★★★★★ §7522（2026 复测，run ECA03CFB_69FF9B4A_TALOS）：**仍然撤回**，但根因换了。
    //   ❌ 不是「门太脆」：当前 P692:GATE_COASTAL_VERDICT 的 geoRatio = 0.2167（余量 79%），
    //      而撤回时基线是 0.9730（余量仅 2.7%）⟹ 那个理由【已不存在】。
    //   ★★★ 真因：**无限递归环**。打开后 22 支探针里 **14 支 StackOverflowError**：
    //        PrecipField.mmPerDay(:2804) -> WaterField.isWater(:279) -> cell(:549) -> solveTile(:847)
    //        -> SimClimate.annualPrecipMmPerYear(:1045->field()) -> solve/solveNode(:709/:906/:919)
    //        -> PrecipField.mmPerDay  ← 回到起点
    //      而 SimClimate:1031-1043 的 javadoc 明写「本访问器【不触发任何新计算】」——
    //      打开本开关恰好打破了这个前提（mmPerDay 依赖 WaterField，WaterField 又需要【已建好的瓦片】）。
    //   ⟹ **要采纳必须先解环**：把「建瓦片」与「水耦合」在时序上分开
    //      （瓦片永远按【无水系】建，水耦合只在 mmPerDay 的最终输出上叠加）。
    //   ⚠ 另注（P1559 实测）：isWater() 在 10 km 网格上把 30% 的陆地判为湖（地球 1~2%），
    //      根因是【地形闭合洼地占 32~43%】（P1509/P1559 两个独立探针一致）。解环后仍需处理。
    // ★★★★★ §7523（2026 复测 #2）：解环后重新打开。
    //   解环方式：SimClimate.isBuildingTile() 护栏 —— 建瓦片期间跳过本耦合
    //   （瓦片永远按【无水系】建，logP 是纯气候量；耦合只作用在 mmPerDay 的最终输出上）。
    // ★★★★★ 2026-10-12 用户裁决【关闭】（待裁决/328）：
    //   关闭理由（实测 + 仓库记录双重印证）：
    //     ① PrecipField:2595-2596 记「P1559 实测：isWater() 在 10 km 网格上把
    //        【30% 的陆地】判为湖（地球 1~2%），根因是地形闭合洼地占 32~43%」；
    //     ② 本轮独立实测（CatchWin2/ResChk）：LEM-free 地形上 D8 汇流被中尺度噪声切碎，
    //        1000x1000 km 范围内最大流域只有 42 格、且【与窗口无关】（1000->2000 km 分布不变）
    //        ⟹ 两个独立测量指向同一根因，互相印证。
    //   ⟹ 这个耦合带来的【错误】（30% 陆地被强制 beta=1）远大于它带来的【物理效应】
    //      （湖面蒸发不受土壤限制，局地小范围）⟹ 关掉比开着更准。
    //   ★ 正确的「湖面 = 饱和面」效应要等新湖能接进气候层之后再打开（见 待裁决/328 的设计）。
    //   ★ 回滚：置 true 即逐位回到打开状态。
    // ★★★★★ 2026-10-12（待裁决/329）：在 LAKE_SOURCE = 2（isLakePeek，不建瓦片）落地后【重新打开】。
    //   为什么现在安全（实测 PeekChk）：
    //     · 游戏里：地形瓦片在地形生成时已建 ⟹ peek 命中 ⟹ 与完整 isLake 【4096/4096 = 100% 一致】
    //     · 探针里：瓦片未建 ⟹ peek 恒返回 false ⟹ 退化到「无耦合」⟹ 与关闭等效、★ 不改变验收结果
    //     · 全球采样 22000 点跨 529 块瓦片 = ★ 5 毫秒（若走 isLake 会建瓦片 = 2.2 小时）
    //     · peekTile 【不建瓦片】⟹ 结构上不可能形成 mmPerDay -> solve -> SimClimate 的递归环
    public static boolean Q_FROM_WATER = true;
    /**
     * ★★★★★ 2026-10-12（待裁决/330）：**上风湖的【水汽反哺】**。
     *
     * <p><b>要解决的问题</b>：活的比湿路径是 {@code moisture(tSfcSl, elev, kappa)} ——
     * <b>纯局地</b>，不走上风。所以湖现在只能饱和<b>它自己那一格</b>，
     * 水汽<b>不会</b>被送到下风 ⟹ 不符合「湖反哺大气水汽」。
     *
     * <p><b>做法</b>：沿上风方向采 {@code N = SOURCE_FETCH_L / UPWIND_STEP = 10} 个点
     *               （★ 与 {@link #upwindElev} 完全同一套采样，<b>零新参数</b>），
     *               统计其中【是湖】的比例 {@code lakeFrac}，然后
     * <pre>
     *   betaEff = beta + (1 - beta) * lakeFrac
     * </pre>
     * <p>★ 为什么这个插值是对的：{@code rhEff(b) = RH_SEA*b + RH_DRY*(1-b)} 是 <b>线性</b>的
     * ⟹ {@code betaEff} 恰好把 RH 从本地点向 {@code RH_SEA}（水面饱和值）拉近 {@code lakeFrac} 的比例。
     * <ul>
     *   <li>{@code lakeFrac = 1}（上风全是湖）⟹ {@code betaEff = 1} ⟹ {@code rh = RH_SEA} ✓</li>
     *   <li>{@code lakeFrac = 0} ⟹ 逐位回到原值 ✓</li>
     * </ul>
     * <p>★★ <b>「湖面积」自动进来</b>：湖越大 ⟹ 上风 10 点命中越多 ⟹ {@code lakeFrac} 越大 ⟹ 水汽反哺越强。
     * <p>★ 回滚：置 {@code false} ⟹ 逐位回到现状。
     */
    public static boolean LAKE_MOISTURE_UPWIND = true;

    /**
     * ★ 上风路径上【是湖】的采样比例 ∈ [0,1]。
     *
     * <p>采样点与 {@link #upwindElev} 完全一致（{@code N = SOURCE_FETCH_L / UPWIND_STEP}），
     * 所以两者描述的是<b>同一段路径</b>，不会出现口径不一致。
     *
     * <p>★ 用 {@code isLakePeek}（不建瓦片）：游戏里命中真值，探针里快速返回 false。
     */
    public static double upwindLakeFrac(int x, int z, long seed, int cell, double u, double v) {
        if (!LAKE_MOISTURE_UPWIND) return 0.0;
        double sp = Math.hypot(u, v);
        if (sp < 0.1) return 0.0;
        final int n = Math.max(1, (int) Math.round(fetchL() / UPWIND_STEP));
        final double ux = u / sp, uz = v / sp;
        int hit = 0;
        for (int i = 1; i <= n; i++) {
            final double d = i * UPWIND_STEP;
            final int sx = x - (int) Math.round(ux * d);
            final int sz = z - (int) Math.round(uz * d);
            if (TalosLandErosion
                    .isLakePeek(seed, sx, sz)) hit++;
        }
        return (double) hit / n;
    }

    /**
     * ★★★★★★★ <b>湖判据的来源（2026-10-11 新增，见 待裁决/283）</b>
     *
     * <p><b>为什么换</b>：旧的 {@code WaterField.isWater} 实测在 <b>250 m 网格</b>上给出：
     * <pre>
     *   占比 6.66%   游程段 168   平均游程 1.59 格 = 398 m   ★ 中位 1 格 = 250 m
     * </pre>
     * ⟹ <b>那是【散点噪声】，不是湖</b>（真实的湖是成片的，几 km 到几十 km）。
     * <p>而 {@code TalosLandErosion.isLake} 在同一区域给出：
     * <pre>
     *   占比 1.82%   游程段  24   平均游程 3.04 格 = 3.04 km  ★ 中位 3 格 = 3 km
     * </pre>
     * ⟹ <b>占比落在地球的 1~2% 内，且是【成片的湖】</b> ✓
     *
     * <p><b>为什么两者差这么多</b>：{@code WaterField} 是【每 4 km tile 独立解 Priority-Flood】
     * （{@code TILE=16, HALO=16}），相邻 tile 的解不保证一致 ⟹ tile 边界附近产生单格假湖。
     * 而 {@code TalosLandErosion} 的瓦片是 <b>768 km</b>（{@code SOLVE_CELLS}），远大于 4 km。
     *
     * <p><b>取值</b>（2026-10-12，待裁决/331）：
     * <ul>
     *   <li>{@code 2 = isLakePeek} —— ★ <b>默认</b>。不建瓦片：游戏里瓦片已建 ⟹ 命中真值
     *       （实测与完整 isLake <b>4096/4096 = 100% 一致</b>）；探针里未建 ⟹ 快速返回 false
     *       （22000 点跨 529 块瓦片实测 <b>5 ms</b>，若建瓦片需 2.2 小时）。</li>
     *   <li>{@code 1 = isLake} —— 会建瓦片（冷瓦片 14.7 s）。<b>只用于诊断/受控对照。</b></li>
     * </ul>
     * <p>⚠⚠ 旧的 {@code 0 = WaterField.isWater} 态已【删除】—— {@code WaterField} 已整类删除
     * （它把 <b>30% 的陆地判为湖</b>，根因是 LEM-free 地形上闭合洼地占 32~43%）。
     * ⟹ 回滚只能回到 {@code 1}，回不到旧湖。
     */
    public static int LAKE_SOURCE = 2;   // ★ 默认：isLakePeek（不建瓦片）

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
    /**
     * ★★★★★ §772：浅对流地板是否受 {@code BLQ_THETA_E}（**深对流**判据）门控。
     *
     * <p>默认 <b>false</b> = 撤回 §720 的 {@code pFloor *= blqG}，恢复 §251 的无条件下界。
     * <b>为什么撤回（物理理由，不是为了让门变绿）</b>：{@code BLQ_THETA_E} 是**深对流**判据
     * （θ_e,conv 出自 Folkins &amp; Braun 2003 的**海面**判据，本文件 :756 有逐字出处），
     * 而本段是**浅对流**地板（Held &amp; Soden 形式的浅积云凝结，云顶出自 Squires 1958）。
     * 浅积云（trade cumulus / 副热带层积云）**恰恰出现在深对流被抑制的地方**
     * ⇒ 用深对流判据门控浅对流地板是**套错了对象**。
     *
     * <p>§720 的实测后果：撒哈拉/澳洲季风/非洲南部三个盒子 JJA 掉到 0.000（目标 ① 在案），
     * 副热带海洋夏季 frac(P&gt;=0.3) 从 99.5% 掉到 14.2%（{@code P499|GATE_SUBTROP_ZERO}）。
     *
     * <p><b>为什么不从凝结形式上乘 {@code (1-k)}</b>：{@code wStarK} 经
     * {@code surfaceBuoyancyFluxK} 已经吃 {@code kappa}（{@code cdOf(kappa)}）且用**本地**皮温，
     * 它是**陆海通用**的对流速度尺度；而蒸发形式 {@code eSh} 需要**水面**才成立，
     * 所以只有它该带 {@code betaF = (1-k)}。凝结形式带 {@code (1-k)} 会把陆地上
     * **物理上合法**的浅对流地板清零（§772 撤回了这条曾经预登记的改法）。
     */
    // （字段本体已于 2026-10-16 删除：见上方 §772 的 category error 记录。）
    /** 浅对流效率（由判据①的观测锚反解；见 §251.2）。 */
    public static double ALPHA_SH = 0.40;

    /**
     * **浅对流降水改用【凝结量 x 对流速度尺度】**（冻结 §655/§656；默认 **false** ⇒ 逐位不变）。
     *
     * <p><b>为什么换</b>（§638 确证的反常）：旧形式 `pSh = ALPHA_SH * rho*Cd*|V|*max(0, (1-k)*qSat(T_s) - q) / rho_w`
     * 与 `q` **反比** ⇒ 「边界层越湿 ⇒ 降水越少」，且它在 `q -> qSat(T_s)`（**饱和**）时归零 ——
     * 而饱和的湿边界层恰恰最有利于对流，方向是反的。P968 实测：152/152 个海洋样本上随 `q` 递减，
     * 最坏相对下降 **100%**。
     *
     * <p><b>新形式</b>：
     * <pre>    q_c = max(0, q - qSat(tTh))          // tTh = T_s - GAMMA*H_BL（模型既有的节流层构造）
     *     P_c = ALPHA_COND * precip(q_c, wStarK(T_s, qSat(T_s), q, ...))</pre>
     * `q_c` 是边界层空气被抬升到节流层后**能凝结出来的量**；`w_*` 是 Deardorff 对流速度尺度
     * （AMS Glossary 定义式，§639 核证）。**两个零点在相反的两端**：旧形式在「湿」端归零（反的），
     * 新形式在「干」端归零（对的 —— 抬升都不饱和就不凝结）。
     *
     * <p>P969 实测（152 个海洋样本，`q` 从 0 扫到 `qSat(T_s)`）：`P_c` **152/152 严格非递减，
     * 最坏相对下降 0.000e+00**（逐位单调），对比旧形式的最坏下降 **100%**。
     *
     * <p><b>⚠ 常数 `ALPHA_COND` 是【标定】，不是推导来的</b>（§656）：形式由物理给定，常数反解自
     * **GPCP + ETOPO1，20~62.5N 海洋，JJA，带内最小 = 1.84 mm/day**。反解值 `1.84/9.4274 = 0.195`。
     * **限度（写死）**：只锚了**一个统计量、一个季节**；冬季未锚；陆地向未测；`tTh` 沿用
     * {@code BLQ_GATE} 的环境递减率近似，不是真抬升凝结高度。
     */
    public static boolean SHALLOW_CONDENSATE = true;   // §679 A/B 臂 B（§677 文献形式）

    /**
     * 新形式的标定常数（倍率，作用在 {@link #precip} 的 `EPS_C` 上）。见 {@link #SHALLOW_CONDENSATE}。
     *
     * <p>⚠⚠ <b>§741：本常数已是【死常数】—— 全仓无任何可执行代码引用它。</b>
     *
     * <p>§687 之后浅对流地板改用凝结形式（见 {@link #SHALLOW_CONDENSATE}、{@link #EP_COND}），
     * 旧的 A 臂形式 `ALPHA_COND * precip(…)` 已被取代。保留本字段只为文档可追溯（§656 以它为「标定常数」的例子）。
     *
     * <p><b>⚠ 不要调它。</b>改它不会改变任何输出（无人读），只会制造「已调参」的假象。
     * （§244.4 的 {@code ALPHA_SH} 就是被「拟合到旧形式」坑过的先例：新形式必须有它自己的标定。）
     */
    @Deprecated
    public static double ALPHA_COND = 0.195;

    /** 降水效率 E_P（无量纲）。出处：Liu et al., Sci. Adv. 10, eado2515 (2024) Fig. 3D，区间 0.19~0.29（本轮读图，原图存 refs/fig3_page5.png）。 */
    public static double EP_COND = 0.24;

    /** 上升气流面积占比 sigma_up。出处：Siebesma et al. (2007)，冻结 §667 逐字引文。 */
    public static double SIGMA_UP = 0.065;

    /**
     * 浅积云云顶高度 `z_ct`（m）。
     *
     * <p>出处：**Squires (1958) / Byers & Hall (1955)**，经 **Rauber et al., Bull. Amer. Meteor. Soc.
     * 88(12), 1913 (2007)** 逐字转述：「**rain first appears in maritime cumuli when their tops
     * reach about 2 km, and is ubiquitous by the time the tops reach 3.5 km**」；以及「Squires (1958) …
     * maritime clouds with tops **greater than 2500 m** 'usually rain within half an hour'」。
     *
     * <p>本本地副本：`refs/rico_bams.pdf`（截图在 `refs/rico_bams.txt`）。
     *
     * <p><b>为什么不能用 `Atmosphere.H_BL`</b>（§676 实测）：`ParcelLift` 的环境送减率
     * `GAMMA = 6.5 K/km` **大于** 热带湿绝热送减率（约 4~6 K/km）⇒ 气块一路比环境暖
     * ⇒ `zLnb == zMax`（全部热带海洋格点上 `convective = true`）。**模型没有信风逆温层**，
     * 而浅对流云顶正由它决定 ⇒ `z_ct` 不可从模型热力学导出。
     *
     * <p>§686 实测：取 `z_ct = 1000 m`（= `H_BL`）时量级门恰好通过，但一旦换成 1500~2500 m
     * 就 FAIL 2.2~4.1 倍 ⇒ **那是一个自由参数的单点巧合**。§687 用有出处的三件套
     * 重验：在整个 2000~3500 m 区间上两门均 PASS（rJJA 0.69~1.26）。
     */
    public static double Z_CT_COND = 2500.0;
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
    // ★ §716：§715 的 A/B 已做完并撤销（现场读数见 §716）。
    //   GATE_VERDICT 仍 FAIL，且其余 26 门全 PASS（含 S1/S2/S3）⇒ 没有收益。恢复原值。
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
    // ★★★★★ §7115 臂 B 实测结论：**判据 (a) 不成立 ⇒ 假设否证，已恢复 false。**
    //   臂 B = rerun_acceptance\B483997A_5B07C258_TALOS（SRCFP=B483997A...）。实测：
    //     · 机制确实生效：`wEff <= 0` 占比 LAND **0.0% -> 88.6%**、SEA 27.0% -> 73.8%、COASTAL 32.5% -> 73.0%；
    //     · 陆地确实变干：idx25 +0.312 -> **-0.015**；P683 亚洲 JJA 2.330 -> 1.413、撒哈拉 1.712 -> 1.326；
    //     · **但海洋完全不动**：OCEAN JJA 最小值仍 **3.265 @ +55**（逐纬均值也几乎逐位相同），
    //       27 门判据与 A 侧逐位相同、FAIL 列表同为 4 门。
    //   ⇒ 原因（本节新查明）：**海洋的副热带降水由浅对流地板供给**，而 `pFloor ∝ wStarK(...)`
    //     用的是【表面浮力通量】导出的对流速度尺度，**不经过 `wEff`** ⇒ 改 `w_zm` 动不了它；
    //     而**陆地的地板 ≈ 0**（wStarK -> 0），降水只能来自大尺度项 `∝ wEff` ⇒ 一过零点就被清零。
    //   ⇒ 判据 (e) 命中：**「同一上游」假设撤回**（`WZM_ITCZ_SHIFT` 是陆侧上游，不是海侧上游）。
    //   ⇒ 采纳条件（javadoc :2251「必须先有区域 2-D 机制」）未满足，故恢复 false。
    public static boolean WZM_FROM_TABLE = false;   // §436：默认撤回，理由见上

    /**
     * ★★★★★★★★ §7587（2026-09-27）：**迎风坡增雨 —— 地形强迫的垂直速度**。默认 **false**。
     *
     * <h3>缺的是什么</h3>
     * <p>本仓的降水驱动量只有 {@code w_eff = w_zm(φ−δ) + clamp(−H_bl·∇·U, ±2e-3)}：
     * 纬向平均上升支 + **气压梯度驱动的辐合**。而 {@code ∇·U} 来自
     * {@code cellPressure}（海陆对比），**不含地形** ⟹ 山**不会**逼空气爬升
     * ⟹ 高山拿不到增雨。实测（P1225）：中位 P 从低地的 1.608 mm/d 单调降到
     * 1600-2500 m 的 0.596（只剩 **37%**），而真实地球是**山地多于低地**。
     *
     * <h3>缺的那一项（逐字出处）</h3>
     * <p>Smith &amp; Barstad (2004, AMS preprint，本仓 {@code refs/oro_smith_barstad_ams.pdf}:33-54}) 逐字：
     * <pre>
     *   The source term (S) in (1a) can be the classical upslope form
     *       S = Cw · ρ · U · ∇h(x, y)
     *   …As lifting in front of a mountain drives S positive, the transformation term
     *   in (1a) converts cloud water to hydrometeors in (1b). S gets the opposite sign
     *   in downslope regions, drying the air and evaporating hydrometeors.
     * </pre>
     * 同一份报告的量级核对（:261-264 逐字）：raw upslope model 在迎风坡给
     * **P = 15 mm/hr 的常值**，而在背风坡给**等量的负值**。
     *
     * <h3>接线形式（零新常数）</h3>
     * <p>本仓已有 {@code P_conv = EPS_C·ρ_a·q·max(0, w_eff)/ρ_w}，而 {@code w_eff} **就是**那个
     * 「rate of ascent」⟹ 只需给它加一项地形：
     * <pre>
     *   w_terrain = U · ∇h          （量纲 (m/s)·(无量纲) = m/s，与 w 同量纲 ✓）
     *   ⟹ 迎风坡（U·∇h &gt; 0）⟹ w_eff 增大 ⟹ P_conv 增大 ✓
     *   ⟹ 背风坡（U·∇h &lt; 0）⟹ max(0,·) 自然截断 ⟹ 不增 ✓   （与上面 S 反号那句一致）
     * </pre>
     * 差分步长取 {@link #UPWIND_STEP}（= 150 km）—— **与 {@link #upwindElev} 同一套地形尺度**，
     * 所以**零新尺度**。
     *
     * <p>{@code U} 与 {@code ∇h} 都是本仓已有的量 ⟹ **零新常数** ✓
     */
    public static boolean TERRAIN_W = true;      // ★ §7588：打开（迎风坡增雨）

    /**
     * ★★★★★★★★ <b>§7778：是否允许地形项作用于【海洋列】。默认 {@code false}。</b>
     *
     * <p><b>为什么必须关</b>（P1498 实测，1600 点全球普查，DJF）：
     * <pre>
     *                       p50   p90   p99      max
     *   TERRAIN_W=ON  :    1.37  5.28  8.44   33193.14   mm/day
     *   TERRAIN_W=OFF :    1.37  5.28  8.44       9.24
     *   spikes &gt;50 : ON 5/1600   OFF 0/1600
     *   OCEAN mean : ON 2.982  OFF 2.975  (ratio 1.00)
     *   LAND  mean : ON 40.796 OFF 1.949  (ratio 20.93)
     * </pre>
     *
     * <p>{@code wTerrain = U·∇h} 在海洋列上用的 ∇h 是<b>海底坡度</b>
     * （{@link PlateField#elevationWithCell} 的海床分支）。实测
     * {@code (-23.4M, 7.5N)}：elev −4439.66 m、|U| ≈ 6.5 m/s ⟹ {@code U·∇h = 0.316 m/s}，
     * 而真实的 {@code w_eff} 只有 0.002~0.02 m/s ⟹ <b>放大 100 倍</b> ⟹ P 达 424 mm/day。
     *
     * <p>⚠ 但地形项是<b>陆地增雨的唯一来源</b>（陆地均值 ×20.93）⟹ <b>不能整体关掉</b>，
     * 只能压制非陆地列。
     *
     * <p>{@code true} = 原行为（逐位不变，回滚点）。
     */
    public static boolean OCEAN_TERRAIN_W = false;
    /**
     * 地形项是否【加限幅】（2026-10-15 审计，用户裁决：加）。
     *
     * <p>问题：w_terrain = U·∇h 原本【直接相加】，而同一个 wEff 里的【局地辐合项】
     * 有 tanh 限幅（幅值 W_LOC_MAX）⟹ 两项待遇不一致。
     * 实测：w_terrain 在陡坡上可达 0.3 m/s，而真实 w_eff 只有 0.002 ⟹ 大 150 倍。
     * 本文件 :2928 自己就记着实测：TERRAIN_W=ON 时 P 的 max = 33193.14 mm/day（地球纪录 ~1800），
     * 而 p50/p90/p99 不变 ⟹ 是【极端尾部污染】。当时只压制了【海洋列】(OCEAN_TERRAIN_W)，
     * 陆地列未修 ⟹ 陆地尖峰仍在（本次剖面实测 186.9 / 666.6 mm/day）。
     *
     * <p>本开关（默认 true）：地形项改用 W_LOC_MAX * tanh(wT / W_LOC_MAX)：
     * · 零新常数（W_LOC_MAX 是模型自己的上升速度尺度）
     * · 机制保留（小坡度 tanh(x)≈x ⟹ 迎风增雨形状不变）
     * · 有界（|贡献| &lt; W_LOC_MAX ≈ 2e-3 m/s，与真实 w_eff 同量级）
     *
     * <p>回滚：置 false ⟹ 逐位回到【直接相加】（一行）。
     */
    public static boolean TERRAIN_W_BOUND = true;

    /**
     * ★★★ 地形抬升的差分步长（m）。
     *
     * <p>出处：{@code docs/模拟器/调研/M3-大气层设计调研.md:638-639} 逐字
     * 「<b>推荐参数</b>：{@code Δs = 25 km}（<b>必须显著小于 {@code PlateField.OROGEN_W = 90 km}</b>，
     * 否则山脉会被混叠掉）」。
     *
     * <p>★ 2026-10-11 口径注记：{@code PlateField.OROGEN_W} 与 {@code PlateField.COLLIDE_H}
     * <b>已随旧地形层一起删除</b>（见 {@code SimClimate:471} 的注释：
     * 「COAST_BAND、COLLIDE_H、ARC_H、TRENCH_D、RIDGE_H，那些成员已随旧地形一起删除」）。
     * 调研文档写于旧地形时期，引用的符号名已不适用。
     *
     * <p>★★ 而它表达的【数值判据仍然成立】：当前代码里对应的地形尺度是
     * {@code TalosLandField.CF_LAND_U = 90_000}（sqrt 海岸剖面的饱和尺度）。
     * 本常量 25,000 / 90,000 = <b>0.28</b> ⟹ 仍然「显著小于」✓
     *
     * <p>★ 2026-10-08 修正（§150）：原来写的是 {@code PlateField.HF_WL_MIN * 0.5}，
     * 注释以为是 23,437.5 m（{@code 3e6/2^7}），而 {@code HF_WL_MIN} 实际是
     * {@code 3e6/2^12 = 732.42 m} ⟹ 实际步长只有 366 m，<b>小了 64 倍</b>，
     * 采到的是小尺度粗糙度而不是山（实测：366 m 尺度 |nabla h| = 1.045 vs 23.4 km 尺度 0.118）。
     */
    public static double TERRAIN_DH_STEP = 25_000.0;

    /**
     * 地形强迫的垂直速度 {@code U·∇h}（m/s）。形式取自 Smith &amp; Barstad (2004) 的
     * classical upslope form（见 {@link #TERRAIN_W} 的 javadoc，逐字）。
     *
     * <p>中心差分，步长 {@link #UPWIND_STEP}。地形量用 {@link PlateField#elevationWithCell}。
     */
    public static double wTerrain(int x, int z, long seed, int cell, double u, double v) {
        // ★★★★★★★★ §7589：差分步长【不能用 UPWIND_STEP（150 km）】—— 那比最短地形波长还大 3 倍，
        //   山脉会被完全混叠掉（M3-大气层设计调研:638 逐字：「Δs = 25 km（**必须显著小于
        //   PlateField.OROGEN_W = 90 km**，否则山脉会被混叠掉）」）。
        //
        // ★★★★★★★★ 2026-10-08 修正（§150，本会话定位）：
        //   **原来的写法与它自己的注释不符，而且错了 64 倍。**
        //   注释说「HF_WL_MIN/2 = 23,437.5 m，由 hf 的 oct=7 与 wl0=3e6 逐字导出」——
        //   23,437.5 = 3e6/2^7 对应 oct=7。**但 HF_WL_MIN 实际是 3e6/2^12 = 732.42 m**
        //   （PlateField:149 逐字「最细波长 = 3,000,000 / 2^12 = 732.421875 m」）。
        //   ⟹ 实际步长 = 366.2 m，比注释所言的 23.4 km 小了 ★ 64 倍。
        //
        //   后果（实测，4 个点 x 7 个步长）：步长 366 m 采到的是【小尺度粗糙度】而不是【山】。
        //     例 (80 km, 88 km)：|nabla h| 在 366 m 尺度 = 1.045（45 度的"坡"），
        //                        在 23.4 km 尺度 = 0.118
        //     ⟹ 噪声梯度被当成山的坡度，wTerrain 爆到 8.4 m/s（风速级）。
        //
        //   ★ 修法：用 M3 报告逐字给的推荐值 Δs = 25 km。
        //     「必须显著小于 OROGEN_W = 90 km，否则山脉会被混叠掉」—— 那正是本项的要求。
        final int h = (int) TERRAIN_DH_STEP;
        // ★★★★★★★★ §7589：云水/降水物的延迟（Smith & Barstad 2004 方程 1a/1b 逐字）：
        //     (1a)  U·∇q_c = S − q_c/τ_c
        //     (1b)  U·∇q_h = q_c/τ_c − q_h/τ_h
        //   两条串联的指数延迟，地面降水相对抬升点沿风下移 |U|·(τ_c + τ_h)。
        //   τ_c = τ_h = 1000 s（该报告 :257-258 逐字：「τ_c = τ_f = 1000 s」）。
        //   所以「本地抬升」应换成「上游 Δs 处的抬升」，而 Δs = |U|·(τ_c+τ_h)
        //   由 U 与逐字的 τ 导出，零新常数。
        //   为什么需要：:261-276 逐字说 raw upslope model 过强（背风坡拿到等量负值），
        //   而 "The effect of cloud delay reduces the precipitation further, and shifts
        //   the precipitation peak downstream."
        double sp = Math.hypot(u, v);
        // ★★★ 卷积路径（默认）：先对【高程】做 Gamma(2) 卷积，再求梯度。
        //   nabla(h*K) = (nabla h)*K ⟹ 与「对抬升源卷积」数学等价，但便宜 4 倍。
        if (TERRAIN_CONV && sp > 0.1) {
            final double ux = u / sp, uz = v / sp;
            final double a = sp * TERRAIN_TAU_C;            // τc = τh = τ ⟹ a = U·τ
            if (a < 1.0) {                                   // 风太小：退化为本地
                double dhdx0 = (PlateField.elevationWithCell(x + h, z, seed, cell)
                              - PlateField.elevationWithCell(x - h, z, seed, cell)) / (2.0 * h);
                double dhdz0 = (PlateField.elevationWithCell(x, z + h, seed, cell)
                              - PlateField.elevationWithCell(x, z - h, seed, cell)) / (2.0 * h);
                return u * dhdx0 + v * dhdz0;
            }
            final double ds = a * 0.5;
            final int N = Math.max(1, TERRAIN_CONV_N);
            // ★ 先对【平滑后的地形】求中心差分（4 次卷积），再取梯度。
            //   nabla(h*K) = (nabla h)*K ⟹ 与「对抬升源卷积」等价，但便宜 4 倍
            //   （4N 次取样 vs 逐点 4 次 x N 点）。
            final double hSmoothPx = smoothElev(x + h, z, seed, cell, u, v, sp, a, ds, N);
            final double hSmoothMx = smoothElev(x - h, z, seed, cell, u, v, sp, a, ds, N);
            final double hSmoothPz = smoothElev(x, z + h, seed, cell, u, v, sp, a, ds, N);
            final double hSmoothMz = smoothElev(x, z - h, seed, cell, u, v, sp, a, ds, N);
            final double dhdx = (hSmoothPx - hSmoothMx) / (2.0 * h);
            final double dhdz = (hSmoothPz - hSmoothMz) / (2.0 * h);
            double wT = u * dhdx + v * dhdz;
            // ★★★ 机制 1：气流动力学因子 [1 − i·m·Hw]（Smith & Barstad 2004 式 2/3，原文逐字）
            if (TERRAIN_DYN) {
                final double hC = smoothElev(x, z, seed, cell, u, v, sp, a, ds, N);
                final double lap = (hSmoothPx + hSmoothMx + hSmoothPz + hSmoothMz
                                    - 4.0 * hC) / (h * h);          // ∇²h
                final double gmag = Math.hypot(dhdx, dhdz);          // |∇h|
                if (gmag > 1.0e-9) {
                    // 局部主导波数：曲率 / 梯度（标准的局部波数估计）
                    double kEff = Math.abs(lap) / gmag;               // 1/m
                    if (kEff < 1.0e-9) kEff = 1.0e-9;
                    final double sig = sp * kEff;                    // 内在频率 σ = U·k
                    final double nm = TERRAIN_NM, hw = TERRAIN_HW;
                    double tDyn;
                    if (sig > nm) {
                        // σ > Nm：m 为【虚数】⟹ [1 − i·m·Hw] 是实数 ⟹ 纯衰减
                        final double mu = kEff * Math.sqrt(1.0 - (nm / sig) * (nm / sig));
                        tDyn = 1.0 / (1.0 + mu * hw);
                    } else if (sig > 1.0e-12) {
                        // σ < Nm：m 为实数 ⟹ |1 − i·m·Hw| = sqrt(1 + (m·Hw)²)
                        final double mR = kEff * Math.sqrt((nm * nm - sig * sig)) / sig;
                        tDyn = 1.0 / Math.sqrt(1.0 + (mR * hw) * (mR * hw));
                    } else {
                        tDyn = 0.0;                                   // 风/坡度太小
                    }
                    wT *= tDyn;
                } else {
                    wT = 0.0;
                }
            }
            return wT;
        }
        int xd = x, zd = z;
        if (TERRAIN_W_DELAY && sp > 0.1) {
            double dsDelay = sp * (TERRAIN_TAU_C + TERRAIN_TAU_H);   // = |U|·(τ_c+τ_h)
            xd = x - (int) Math.round(u / sp * dsDelay);
            zd = z - (int) Math.round(v / sp * dsDelay);
        }
        double dhdx = (PlateField.elevationWithCell(xd + h, zd, seed, cell)
                     - PlateField.elevationWithCell(xd - h, zd, seed, cell)) / (2.0 * h);
        double dhdz = (PlateField.elevationWithCell(xd, zd + h, seed, cell)
                     - PlateField.elevationWithCell(xd, zd - h, seed, cell)) / (2.0 * h);
        return u * dhdx + v * dhdz;
    }

    /** ★ 把单个高程点做 Gamma(2) 卷积（供 wTerrain 的差分用）。 */
    private static double smoothElev(int x, int z, long seed, int cell,
                                     double u, double v, double sp, double a, double ds, int N) {
        final double ux = u / sp, uz = v / sp;
        double hSum = 0.0, kSum = 0.0;
        for (int i = 1; i <= N; i++) {
            final double s = i * ds;
            final double K = (s / (a * a)) * Math.exp(-s / a);
            hSum += K * PlateField.elevationWithCell(
                    x - (int) Math.round(ux * s), z - (int) Math.round(uz * s), seed, cell);
            kSum += K;
        }
        if (kSum <= 1.0e-12) return PlateField.elevationWithCell(x, z, seed, cell);
        return TERRAIN_CONV_NORM ? (hSum / kSum) : hSum;
    }

    /**
     * ★★★★ 是否用【核本身（卷积）】而不是【核的均值（点位移）】。
     *
     * <h3>为什么这是对的（纯文献推导，零新参数）</h3>
     * Smith &amp; Barstad (2004) 方程 1a/1b：
     * <pre>
     *   (1a)  U·nabla q_c = S − q_c/τ_c
     *   (1b)  U·nabla q_h = q_c/τ_c − q_h/τ_h
     * </pre>
     * 对脉冲源的两级串联指数延迟，其解核为 Gamma(2)：
     * <pre>
     *   K(s) = (s/a²) e^(−s/a),    a = U·τ      （τ_c = τ_h = τ 时）
     *   Integral_0^inf K(s) ds = 1，  核的【均值】= 2a = 2·U·τ
     * </pre>
     *
     * <p>★★ 原实现取的正是那个【均值】：把抬升点沿风位移 {@code |U|(τ_c+τ_h) = 2Uτ}。
     * 对平滑地形，点位移 ≈ 卷积；但对<b>陡坡（高频）</b>，点位移会取到陡坡处的极大值
     * ⟹ <b>反而放大</b>。实测（本文档 §141）：陆地 {@code wTerrain} 最大
     * <b>18.48 m/s</b>，而真实 {@code w_eff} 只有约 0.002 m/s；
     * {@code |wTerrain| > 1.0} 的陆地格占 <b>30.4%</b> ⟹ 不是尾部污染，是普遍偏高。
     *
     * <p>★★★ 本版做【真卷积】：高频被核平滑掉 ⟹ 幅度自然衰减 ⟹ 物理正确。
     * 而且用「先对高程卷积、再求梯度」实现（{@code nabla(h*K) = (nabla h)*K}），
     * <b>比逐点位移更省</b>：N 次高程 + 4 次差分，而不是 4N 次。
     */
    public static boolean TERRAIN_CONV = true;
    /** 卷积的采样点数（覆盖到 N·a/2，a = U·τ）。8 点覆盖到 4a（核的 99.8% 质量）。 */
    public static int TERRAIN_CONV_N = 8;
    /** 卷积的归一化开关（关掉会改变幅度，仅用于对照）。 */
    public static boolean TERRAIN_CONV_NORM = true;

    // ==================== ★★★ 气流动力学因子 [1 − i·m·Hw] ====================
    //
    //  Smith & Barstad (2004) 的完整传递函数（原文 refs/oro_smith_barstad_ams.txt 第 94-104 行逐字）：
    //
    //                Cw · i·sigma · h_hat(k,l)
    //    P_hat = ────────────────────────────────────────────────       (2)
    //            [1 − i·m·Hw] · [1 + i·sigma·tau_c] · [1 + i·sigma·tau_h]
    //
    //    m     = { [Nm² − sigma²] / sigma² · (k² + l²) }^(1/2)          (3)
    //    sigma = u·k + v·l                       （内在频率）
    //
    //  ★ 原文第 201-215 行逐字说，相对标准 upslope 模型有【两个】衰减机制：
    //      First,  ... the terrain induced lifting may not penetrate up through the
    //              moist layer, thus limiting condensation.          <- [1 − i·m·Hw]
    //      Second, ... the condensed water may be advected to the lee side
    //              and evaporated instead of precipitating.          <- [1 + i·sigma·tau]
    //
    //  ★★ 本仓此前【只做了第二个】（Gamma(2) 卷积），漏了第一个。
    //     而第一个恰好是【陡坡衰减】的物理来源：
    //       sigma > Nm  ⟹ m 为【虚数】⟹ [1 − i·m·Hw] 变成实数 [1 + mu·Hw] ⟹ 纯衰减
    //       sigma < Nm  ⟹ m 为实数  ⟹ |1 − i·m·Hw| = sqrt(1 + (m·Hw)²) ⟹ 也有衰减
    //
    //  ★★★ 参数全部来自原文（第 241-260 行的算例），零自造：
    //       Nm = 0.005 s^-1        （湿 Brunt-Väisälä 频率）
    //       Hw = 2500 m            （水汽标高）
    //       tau_c = tau_h = 1000 s （本仓 TERRAIN_TAU_C/H 已逐字一致）

    /** ★ 湿 Brunt-Väisälä 频率（1/s）。Smith & Barstad (2004) 算例逐字：Nm = 0.005 s^-1。 */
    public static double TERRAIN_NM = 0.005;
    /**
     * ★★ 水汽标高 / 湿层深度（m）—— <b>与 {@link #H_MOIST} 是同一个物理量</b>，
     * 所以直接引用它，避免两者漂移。
     *
     * <h3>为什么不用 S&B 原文算例的 2500 m</h3>
     * Smith &amp; Barstad (2004) 第 252 行写的是「{@code Also ... Hw = 2500m}」——
     * 那是<b>他们那个三角脊算例</b>的参数，<b>不是普适常数</b>。
     *
     * <p>而本仓的 {@code H_MOIST = 2000} 是<b>标定到观测柱水汽</b>的有效值。实测：
     * <pre>
     *   纬度      观测柱水汽(kg/m2)   H_MOIST=2000   H_MOIST=2500
     *   0~20 度   35~50               43~53 ✓        54~66 ✗ 偏高 30~50%
     *   40~60 度  15~25               18~37 ✓        23~46 ✗ 偏高
     * </pre>
     * ⟹ 2000 与观测吻合更好 ⟹ 保留 2000，并让本项引用它。
     *
     * <p>物理上 {@code [1 − i·m·Hw]} 里的 {@code Hw} 是「抬升必须穿透的湿层深度」，
     * 与 {@code depl = exp(-hUp/H_MOIST·kappa)} 里的 {@code H_MOIST} 是同一个量。
     */
    public static double TERRAIN_HW = H_MOIST;
    /** ★ 是否启用气流动力学因子 [1 − i·m·Hw]（机制 1）。关掉 ⟹ 退回只有卷积的行为。 */
    public static boolean TERRAIN_DYN = true;

    // ==================== ★★★ 地形项的时间平均（气候态 vs 瞬时） ====================
    //
    //  物理问题（本会话 §146 定位）：
    //    wTerrain = U·nabla h 是【瞬时】量（天气尺度），量级 0.1~1 m/s；
    //    而模型其余部分（w_zm、w_loc、降水）都是【气候态】（年均），量级 ~0.002 m/s。
    //    S&B (2004) 的传递函数是在【稳态、单一风向】下推导的（原文第 320-322 行自认
    //    「the number of strong assumptions ... steady state」）。
    //    搬到气候模型里，必须补上【对风的分布取平均】这一步。
    //
    //  ★ 正确的气候态量是  <max(0, U(theta)·nabla h)>  —— 注意 max 在平均【里面】：
    //      · 只有【上坡】才产生降水（下游 p 里本来就取 max(0,·)）
    //      · 下坡不抵消上坡（若把 max 放到外面，正负相消会把雨影抹掉）
    //
    //  ★ 实测（8 个季节相位 x 5 个点）：平均后 max(0,wT) 削减 45~78%。
    //  ★ 成本（实测）：windAt 0.003 ms + wTerrain 0.017 ms；N=8 时
    //     mmPerDay 2.4453 -> 2.6062 ms（★ 只贵 7%）。
    //
    /**
     * ★★ 是否对风的季节相位取平均。**2026-10-08 改为 false。**
     *
     * <h3>为什么改回来（实测，§153）</h3>
     *
     * <p>本项原本假设「瞬时 U·nabla h 需要按风的分布取平均才是气候态」—— 那在原理上是对的。
     * 但实测表明：**本模型的季节风摆动远大于真实**，所以那个平均是在消除【模型的伪影】，
     * 不是在做物理。
     *
     * <pre>
     *   NCEP/NCAR R1 850 hPa 月平均风向的季节集中度（A = &lt;max(0,cos(theta-theta_0))&gt;）：
     *     纬度带    A(真实 850hPa)
     *      0-15      0.825
     *     15-30      0.932
     *     30-45      0.953
     *     45-60    ★ 0.996
     *     60-75      0.975
     *     75-90      0.942
     *   ⟹ 真实风向【极其集中】，A 接近 1 ⟹ 平均的削减应该只有 0~17%。
     *
     *   而模型 8 相位给出的 A：
     *      0-15 0.582 / 15-30 0.532 / 30-45 0.525 / 45-60 0.575 / 60-75 0.940 / 75-90 0.757
     *   ⟹ A比（模型/真实）= 0.55 ~ 0.96 ⟹ 模型的风向摆得太厉害。
     * </pre>
     *
     * <p>★★ ⟹ 相位平均的削减（45~78%）是【模型季节循环的伪影】。
     * 关掉它 ⟹ A = 1，与真实的 0.83~1.00 只差 0~17% ⟹ 更接近物理。
     *
     * <p>⚠ <b>真正的缺口仍然记账</b>：模型没有【天气尺度】风向变率
     * （那会让 A 进一步降到 ~0.8）。但用【错误的季节摆动】去近似它，比不做更糟。
     */
    public static boolean TERRAIN_PHASE_AVG = false;
    /** ★ 相位采样数。取 8：比气候瓦片的 4 季更细，成本仅 +7%（实测）。 */
    public static int TERRAIN_PHASE_N = 8;

    /**
     * ★★★ 地形抬升的【气候态】值：对风的季节相位分布取 {@code <max(0, U(theta)·nabla h)>}。
     *
     * @param theta 当前季节相位（作为采样起点，保持与该点的其它量同相）
     */
    public static double wTerrainClim(int x, int z, long seed, int cell, double theta, int gradStep) {
        final int n = Math.max(1, TERRAIN_PHASE_N);
        if (n == 1) {
            double[] u0 = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
            return Math.max(0.0, wTerrain(x, z, seed, cell, u0[0], u0[1]));
        }
        double sum = 0.0;
        for (int q = 0; q < n; q++) {
            final double th = theta + q * (2.0 * Math.PI / n);
            double[] u0 = Atmosphere.windAt(x, z, seed, cell, th, gradStep);
            final double wt = wTerrain(x, z, seed, cell, u0[0], u0[1]);
            if (wt > 0.0) sum += wt;          // ★ max 在【平均里面】
        }
        return sum / n;
    }

    /** 云水转换时标 τ_c（s）。Smith & Barstad (2004) 该报告 :257-258 逐字：τ_c = τ_f = 1000 s。 */
    public static double TERRAIN_TAU_C = 1000.0;
    /** 降水物落地时标 τ_h（s）。同上逐字（报告里 τ_c = τ_f，两者同值）。 */
    public static double TERRAIN_TAU_H = 1000.0;
    /** 是否启用延迟下移（默认 true）。false 则用本地抬升（= 纯 raw upslope，B4 过强）。 */
    public static boolean TERRAIN_W_DELAY = true;

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
                     : (HadleyCell.SOLVED_ON ? HadleyCell.wZmSolved(wLat) : ZonalTables.wZm(wLat));
        DIAG.get()[4] = wBase;
        if (!SPLIT_ASCENT) return wBase + wLoc;
        return Math.max(0.0, wBase) + Math.max(0.0, wLoc);
    }

    /**
     * §7147 **下沉封顶（默认关）** —— 用【有出处】的混合层深度闭式给浅对流地板封顶。
     *
     * <p><b>为什么</b>（§7143 定位到行）：地板 `pFloor` 的自变量只有 `tSfcF / q / lat / k / vEff`，
     * **不读 `wEff` / `wBase` / `divU`**，却以 `if (pFloor > p) p = pFloor;` 无条件覆盖
     * ⇒ 模型在强下沉处（撒哈拉、副热带海洋）照样下雨。Betts (2004) 第 26 页明说海洋平衡是
     * 「balance of radiative cooling, **subsidence** and surface fluxes」—— 三项，模型只实现了一项。
     *
     * <p><b>形式与出处</b>：`dz_i/dt = w_e + W_LS(z_i) = 0`（Eq.131，Lilly 1968 框架，
     * 经 CSU PBL 讲义 p.137-138 转述）与夹卷闭合 `w_e/w_* = A * Ri_*^(-1)`
     * （Eq.136，**Lilly (1968), QJRMS 94, 292-309**；`Ri_*` 定义见 Deardorff 1980, BLM 18, 495-527；
     * `P=N` 原始假设见 Ball 1960, QJRMS 86, 483-494）。取零阶跳变 `dTheta = Gamma_eff * z_i` 后联立得
     * <pre>    z_i = w_* * ( A * Theta0 / (g * D * Gamma_eff) )^(1/3)</pre>
     * `D = divU`（模型自己的散度）、`Gamma_eff = GAMMA - gammaMoist`（模型自己的两条减率）、
     * `w_*` 用既有的 {@link #wStarK}。**全程 O(1)：无循环、无迭代、无新缓存。**
     *
     * <p>⚠ <b>`ENTRAIN_A` 是【新引入的自由参数】，不得靠调它转绿</b>（§7146 §四）：取文献常用值 0.2；
     * 若必须改它才能过门，视为失败（与 §686 判死的「1000 m 单点巧合」同类）。
     *
     * <p>⚠ <b>默认 false ⇒ {@link #zCtEff} 不被调用 ⇒ 生产逐位不变（这是本开关的验收条件）。</b>
     */
    public static boolean SHALLOW_FLOOR_ZI_LIMIT = true;

    // ★ 2026-10-16 清理：此处原有开关 SHALLOW_FLOOR_FLUX_DRIVEN（地板速度尺度改用浮力通量），
    //   **已删除**：Held & Soden 形式要求该量是速度 [m/s]，用三次方是**量纲错误**；
    //   实测也更差（内带 2.280/1.840 对观测 2.54/3.17）。理由留在调用点注释里。

    /** ★ 2026-10-16（goal-3355454e）：**地板乘空气-海面季节因子** {@link ZonalTables#shallowAsf}。
     *  <p>默认 false ⟹ 逐位不变。
     *  <p>依据（一手）：南大洋浅对流季节循环由低层稳定度与「冷空气平流到暖海面」控制，冬季最强 ——
     *  Alinejadtabrizi et al. (2024), npj Clim. Atmos. Sci. 7(1) 76, DOI 10.1038/s41612-024-00625-1；
     *  Lang et al. (2022), ACP 22(3) 2135-2152。因子表由本仓 NCEP R1 + COBE-SST2 月平均算出
     *  （见 {@link ZonalTables#SHALLOW_ASF_SH} 的口径与警告）。 */
    public static boolean SHALLOW_FLOOR_ASF = true;   // A/B

    /** ★ 2026-10-16（goal-3355454e）：地板再乘**低层稳定度因子** {@link ZonalTables#shallowLts}。
     *  <p>默认 false ⟹ 逐位不变。依据同一篇文献：夏季 LTS 更高（更稳定）⟹ 浅对流更弱。 */
    public static boolean SHALLOW_FLOOR_LTS = true;   // A/B

    /** 夹卷常数 `A`（Lilly 1968）。⚠ 新自由参数，见 {@link #SHALLOW_FLOOR_ZI_LIMIT}。 */
    public static double ENTRAIN_A = 0.2;

    /** 重力加速度（m/s^2），`zCtEff` 用；与 {@code HadleyCell} 同源的标准值。 */
    static final double G0_ZI = 9.80665;

    /**
     * 见 {@link #SHALLOW_FLOOR_ZI_LIMIT}。`divU <= 0`（辐合/上升）时**直接退回** {@code Z_CT_COND}，
     * 即构造上不影响深热带。
     */
    static double zCtEff(double tSfcF, double qsSfcF, double q, double latRad,
                         double k, double vEff, double tLcl, double pLcl, double qLcl, double divU) {
        if (divU <= 0.0) return Z_CT_COND;
        double gamMoist = ParcelLift.gammaMoist(tLcl, pLcl, qLcl);
        double gamEff = Atmosphere.GAMMA - gamMoist;
        if (gamEff < 1.0e-4) gamEff = 1.0e-4;
        double wS = wStarK(tSfcF, qsSfcF, q, latRad, k, vEff);
        double c = ENTRAIN_A * tSfcF / (G0_ZI * divU * gamEff);
        double zi = wS * Math.cbrt(c);
        return zi < Z_CT_COND ? zi : Z_CT_COND;
    }

    /** 降水率（m/s）。 */
    /**
     * ★ 2026-10-15 诊断访问器：返回上一次 mmPerDay 的诊断数组副本。
     *
     * <pre>
     *   [0] divU_raw    风场差分给出的散度（不含定常波）
     *   [1] dWave       叠加进来的 StationaryWave.divAt
     *   [2] divU_total  [0] + [1]
     *   [3] wEff        最终上升速度（进 diag 与 precip 的那个）
     * </pre>
     * <p>用途：回答「为什么降水不响应反照率」—— 需要看 wEff 是否已被 tanh 限幅饱和。
     * 出处：待裁决/419、420。
     */
    public static double[] diagSnapshot() {
        double[] d = DIAG.get();
        return d == null ? new double[0] : d.clone();
    }

    /** 降水率（m/s）。 */
    public static double precip(double q, double wEff) {
        if (wEff <= 0) return 0.0;
        return EPS_C * RHO_AIR * q * wEff / RHO_WATER;
    }

    /**
     * 世界坐标上的降水（mm/day）。O(1)（需要 4 次风场取样算散度）。
     *
     * <h3>★★★★★ §7527（G1）：本函数的【耦合契约】—— 谁负责水耦合</h3>
     *
     * <p>水耦合（湖/湿地 = 饱和面 ⟹ {@code beta = 1}，见 {@link #Q_FROM_WATER}）
     * <b>只在【最终消费者】这一层生效</b>，不在【气候瓦片建设】里生效。判定方式是
     * {@link SimClimate#isBuildingTile()}：
     *
     * <table border=1>
     * <tr><th>调用场景</th><th>{@code isBuildingTile()}</th><th>耦合</th><th>为什么</th></tr>
     * <tr><td>建气候瓦片（{@code SimClimate.solveNode:1013}）</td><td>true</td><td><b>否</b></td>
     *     <td>{@code logP} 必须是【纯气候量】；且否则会成环（见下）</td></tr>
     * <tr><td>最终消费者（{@code GlobalClimate:102} 渲染/群系、探针）</td><td>false</td><td><b>是</b></td>
     *     <td>湖/湿地的湿润效应是【局地边界条件】，属输出层</td></tr>
     * </table>
     *
     * <h3>为什么必须这样切（§7523 实测）</h3>
     * <p>若让耦合进入建瓦片路径，会形成环：
     * <pre>
     *   mmPerDay -> WaterField.isWater -> solveTile
     *     -> SimClimate.annualPrecipMmPerYear -> field() -> solve -> solveNode
     *     -> mmPerDay  &lt;- 回到起点
     * </pre>
     * <p>run {@code ECA03CFB_69FF9B4A_TALOS} 实测：22 支探针里 <b>14 支 StackOverflowError</b>。
     * 解环护栏见 {@link SimClimate#isBuildingTile()}。
     *
     * <h3>⚠ 已知的性能后果（§7.13/§8.13）</h3>
     * <p>开启 {@code Q_FROM_WATER} 后，<b>最终消费者每调一次本函数都可能触发一次水系 tile 求解</b>
     * （{@code WaterField} 冷 tile 24.8 s，其中 99.95% 是建气候瓦片）。
     * 实测：22 支探针的套件在开耦合后 <b>25~43 分钟仍跑不完</b>（完整套件基线约 5 分钟）。
     * <p><b>⟹ 这是【依赖方向】的问题，不是常数问题。</b>下一轮要做的是把「湖/湿地」预建为
     * 一个像气候瓦片那样的按需位图（候选 G2），让最终消费者只做查询、不触发求解。
     */
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
        // ★★ 2026-10-19：本块已删除 —— `Atmosphere.surfaceTemp` 早就有 `tSl − Γ·elev·kappa`
        //   （PrecipField:2920 注明残差 0.00e+00），自己再减一次是重复且漏了 kappa。
        //   正确的做法见下方的 PRECIP_USE_SURFACE_T：降水直接复用 surfaceTemp。
        double[] u0 = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double hUp = upwindElev(x, z, seed, cell, u0[0], u0[1]);
        double depl = depletion(hUp, k);
        DIAG.get()[11] = depl;
        // ★ 2026-10-16 清理：原开关 Q_AT_SURFACE_TEMP 已**删除**，此处硬编码为 `tSl`。
        //   删除理由（§648 逐字）：moisture() 里的 RH_SEA 按定义是**海面**相对湿度，
        //   所以空气湿度 q 的正确锚是**表面**温度 T_s；拿 RH_SEA 去乘 qSat(T_a) 是**口径错配**
        //   ⇒ 那个开关的 true 侧只会给出错误的配对，属「只提供错误选项」⇒ 删。
        //   （要改空气口径，用的是自洽的一对：空气 RH × 气温 —— 见 Q_AIR_ANCHOR / RH_AIR_OBS。）
        // ★★★★★ 2026-10-19：**本模块的「地表温度」**。默认关 ⇒ `tQ` 逐位等于 `tSl`（零变化）。
        //   打开 ⇒ 复用 Atmosphere.surfaceTemp（= tSl − Γ·max(0,elev)·kappa；P501 自检 [4] 残差 0.00e+00）。
        //   κ=0 处减项恒为 0 ⇒ 纯海洋点逐位不变；只改陆地（完整理由 / 影响面见字段 javadoc）。
        double tQ = PRECIP_USE_SURFACE_T
                  ? Atmosphere.surfaceTemp(x, z, seed, cell, theta)
                  : tSl;
        // ★ §405 S3 接线：只在这一次 moisture() 调用周围设置 beta，之后立刻还原
        //   （自旋中不设 —— 那时 beta 由 SoilMoisture 自己显式给出，设了会覆盖它、导致递归）。
        Double savedBeta = BETA_OVERRIDE.get();
        double betaUsed = 1.0;
        if (SoilMoisture.SOIL_MOISTURE_ON && !SoilMoisture.isSpinningUp()) {
            betaUsed = SoilMoisture.betaAt(x, z, seed, cell, theta, gradStep);
            // ★ §7497 水系接线：湖 / 湿地 = 饱和面 ⟹ beta = 1（见 Q_FROM_WATER 的 javadoc）。
            //   放在 set() 之前 ⟹ 下游（moisture 的 rhEff、DIAG[13]、路径点）全部一致地看到它。
            // ★★★★★ §7523 解环：建气候瓦片期间【必须跳过】这一段。
            //   否则 SimClimate.solve -> solveNode(:989) -> mmPerDay -> WaterField.isWater
            //   -> WaterField.solveTile -> SimClimate.annualPrecipMmPerYear -> field()
            //   -> 又要建瓦片 ⟹ 无限递归（实测 14/22 探针 StackOverflowError）。
            //   时序分离：瓦片按【无水系】建（logP 是纯气候量），耦合只作用在最终输出上。
            if (Q_FROM_WATER && !SimClimate.isBuildingTile()) {
                // ★ 2026-10-11：判据可切换（见 LAKE_SOURCE 的 javadoc）。
                //   ⚠ 护栏 isBuildingTile() 对两条路【都必须保留】——
                //     TalosLandErosion.isLake 也会触发它的 solve()，
                //     而 solve() -> precipAnalytic -> SimClimate ⟹ 同一个环。
                // ★★★★★ 2026-10-12（待裁决/328）：三态
                //   2 = ★ 新湖（不建瓦片）—— 生产路径：游戏里地形瓦片已建 ⟹ 正确；探针里未建 ⟹ 不卡死
                //   1 =   新湖（会建瓦片，14.7 s/块）—— 只用于诊断/受控对照
                //   ★★★ 2026-10-12（待裁决/331）：旧的 0 态（WaterField.isWater）已【删除】——
                //       它把 30% 的陆地判为湖，是错的；且 WaterField 整类已删除。
                final boolean lake = (LAKE_SOURCE == 1)
                    ? TalosLandErosion
                        .isLake((long) seed, x, z)
                    : TalosLandErosion
                        .isLakePeek((long) seed, x, z);
                if (lake) betaUsed = 1.0;
                // ★★★★★ 2026-10-12（待裁决/330）：上风湖的水汽反哺。
                //   lakeFrac = 上风 10 个采样点里是湖的比例（★ 与 upwindElev 同一套采样）
                //   betaEff = beta + (1-beta)*lakeFrac —— 把 RH 向水面饱和值拉近 lakeFrac 的比例。
                //   ★ 湖面积自动进来：湖越大 ⟹ 命中越多 ⟹ lakeFrac 越大。
                final double lakeFrac = upwindLakeFrac(x, z, seed, cell, u0[0], u0[1]);
                if (lakeFrac > 0.0) betaUsed = betaUsed + (1.0 - betaUsed) * lakeFrac;
            }
            BETA_OVERRIDE.set(betaUsed);
            // ★ 2026-10-16（goal-3355454e）：把本点纬度交给 moisture() 的空气口径支路（默认关时无效果）。
            if (Q_AIR_ANCHOR) Q_AIR_LAT.set(lat);
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
        if (Q_AIR_ANCHOR) Q_AIR_LAT.remove();
        DIAG.get()[5] = q; DIAG.get()[6] = tSl; DIAG.get()[13] = betaUsed;
        // ★ 2026-10-16（goal-3355454e）：暴露模型自己的**地表蒸发 E** [kg/(m^2 s)] ✓ 供收支闭合用 ✓
        //   式与第 3796 行（浅对流地板的通量）**同惯例** ✓：RHO_AIR * cdOf(k) * vEff * max(0, qsat*beta - q)
        //   （E = 86400*chv*(qSat(Ts)*beta - q) 见类头 javadoc ✓；此处给每秒值 ✓）
        //   ★ 2026-10-19：Ts 用 tQ（默认关时 tQ == tSl ⇒ 与历史逐位相同；打开时改用地表温度 ✓）。
        double spE = Math.hypot(u0[0], u0[1]);
        double vEffE = Math.sqrt(spE * spE + V_GUST * V_GUST);
        DIAG.get()[16] = RHO_AIR * Atmosphere.cdOf(k) * vEffE * Math.max(0.0, qSat(tQ) * betaUsed - q);
        // ★ §7303：【外层】步长可单独放大（`windAt` 内部仍用 `gradStep`）。
        //   `DIV_OUTER_STEP = 0` 时 `os == gradStep` ⇒ 与历史【逐位相同】。
        int os = (DIV_OUTER_STEP > 0) ? DIV_OUTER_STEP : gradStep;
        double[] ux = Atmosphere.windAt(x + os, z, seed, cell, theta, gradStep);
        double[] uw = Atmosphere.windAt(x - os, z, seed, cell, theta, gradStep);
        double[] un = Atmosphere.windAt(x, z + os, seed, cell, theta, gradStep);
        double[] us = Atmosphere.windAt(x, z - os, seed, cell, theta, gradStep);
        double divU = (ux[0] - uw[0]) / (2.0 * os) + (un[1] - us[1]) / (2.0 * os);
        DIAG.get()[0] = divU;                       // 诊断：原始 divU（只来自 cellPressure）
        // ★ S2 接线（设计冻结 393）：把【定常波】的辐合场 div(V) 叠进来。
        //   为什么：现在 divU 完全来自 cellPressure，而 352/356 已实测那是个【陆海开关】
        //   （所有陆地拿到辐合、所有海洋拿到辐散），不是季风/沙漠判别器。
        //   393 实测：Q 取【降水型】时，求解器给出的正是【亚洲辐合 / 撒哈拉辐散】。
        //   默认 ENABLED=false => 整段跳过 => 逐位不变。
        //   isSolving() 守卫切断「求解器建强迫场 -> mmPerDay -> 再求解」的递归。
        double dWave = 0.0;
        if (StationaryWave.STATIONARY_WAVE_ON) {
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
            // ★ 2026-10-15：带纬度（海冰/雪反照率需要 ✓）；开关关时逐位不变 ✓
        double asrQ = Radiation.absSolarSurface(lat, decQ, Radiation.albedo(k > 0.5, tQ, lat));   // §7353 单源
            wE = wEffQnet(lat, theta, divU, q, tQ, depl, k,
                          SoilMoisture.SOIL_MOISTURE_ON ? betaUsed : 1.0, chvQ, asrQ);
        } else {
            wE = wEff(lat, theta, divU);
        }
        // ★★★★★★★★ §7587：地形强迫项（迎风坡增雨）。默认 false ⟹ 整段跳过 ⟹ 逐位不变。
        //   形式逐字取自 Smith & Barstad (2004) 的 classical upslope form  S = Cw*rho*U·∇h；
        //   本仓的 w_eff 就是那个 "rate of ascent" ⟹ 直接加一项 U·∇h（量纲同为 m/s）。
        //   迎风坡 U·∇h>0 ⟹ 增雨；背风坡 U·∇h<0 ⟹ 被下游的 max(0,wEff) 自然截断 ⟹ 不增。
        if (TERRAIN_W) {
            // ★★★★★★★★ §7616（丙）：地形项【只加正的】。
            //   为什么：w_terrain = U·∇h 在背风坡为负 ⟹ 若直接相加，wEff 变负 ⟹
            //   下游的 max(0,wEff) 把它截成 0 ⟹ 背风坡【完全无雨】⟹ B4（迎风/背风比）
            //   从 4.083（基线，余量 0.4%）推到 4.341 ⟹ 越过锚带上限 4.1。
            //   P7604 实测：低地 P 掉到 0.52~0.63 倍（背侧太干）。
            //   修法：只加 max(0, w_terrain) ⟹ 背风坡【不受地形项影响】⟹ 保留原有的
            //   wBase + wLoc ⟹ P 不会被清零。**零新常数**（就是取正）。
            //   同族先例：SPLIT_ASCENT（本文件 :2601 逐字「max(0,w_zm) + max(0,w_loc)」）。
            // ★★★★★★★★ §7778（Round 26）：地形项【只对陆地生效】。
            //   P1498 实测（1600 点全球普查）：TERRAIN_W 在【海洋列】上用【海底坡度】算 U·∇h，
            //   而风把 6.5 m/s 吹过陡峭的海底斜坡 ⟹ w_terrain 达 0.3 m/s（真实 w_eff 只有 0.002）
            //   ⟹ P 的 max 从 9.24 飙到 **33193.14 mm/day**（地球纪录 ~1800）。
            //   而 p50/p90/p99 完全不变 ⟹ 是【极端尾部污染】，不是整体偏移。
            //   同时：陆地均值 1.949 -> 40.796（×20.93）⟹ 地形项是陆地增雨的【唯一来源】，不能关。
            //   ⟹ 修法：只压制【非陆地】列上的这一项。
            //   OCEAN_TERRAIN_W=false 时逐位等于原行为（回滚点）。
            //   ⚠ 判据【不能】用 kappa：P1499 实测 kappa 在海洋上 99% 的列都 > 0
            //   （n=201，min=0.0，max=1.0，frac(k>0)=0.99）⟹ k>0 对海洋几乎恒真，不筛掉任何东西。
            //   用【真正的陆地判据】PlateField.isLand（P1499 实测：全部尖峰点 land=false）。
            if (OCEAN_TERRAIN_W || PlateField.isLand(x, z, seed)) {
                double wT = TERRAIN_PHASE_AVG
                    ? wTerrainClim(x, z, seed, cell, theta, gradStep)
                    : Math.max(0.0, wTerrain(x, z, seed, cell, u0[0], u0[1]));
                // 2026-10-15 审计（用户裁决：加）：给地形项加【有界处理】。
                //   问题：w_terrain = U·∇h 是【直接相加】的，而同一个 wEff 里的
                //     【局地辐合项】有 tanh 限幅（幅值 W_LOC_MAX）⟹ 两项待遇不一致。
                //   后果：w_terrain 在陡坡上可达 0.3 m/s（真实 w_eff 只有 0.002）
                //     ⟹ 大 150 倍；本文件 :2928 记着实测：TERRAIN_W=ON 时 P 的 max
                //     = 33193.14 mm/day（地球纪录 ~1800），而 p50/p90/p99 不变。
                //   本修法：地形项改用与局地辐合项【同一个限幅】：
                //     wE += W_LOC_MAX * tanh(wT / W_LOC_MAX)
                //     · 零新常数（W_LOC_MAX 是模型自己的上升速度尺度）
                //     · 机制保留（小坡度 tanh(x)≈x ⟹ 迎风增雨形状不变）
                //     · 有界（|贡献| < W_LOC_MAX ≈ 2e-3 m/s，与真实 w_eff 同量级）
                //   回滚：TERRAIN_W_BOUND = false ⟹ 逐位回到【直接相加】（一行）。
                if (TERRAIN_W_BOUND) {
                    wT = W_LOC_MAX * Math.tanh(wT / W_LOC_MAX);
                }
                wE += wT;
            }
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
            double Cb = Q_BLBUDGET_SUB_ONLY
                    ? 86400.0 * RHO_AIR * Math.max(0.0, Atmosphere.H_BL * divU)   // S628: 只下沉枝 (divU>0)
                    : 86400.0 * RHO_AIR * Math.abs(-Atmosphere.H_BL * divU);      // S627 旧式: 两枝都干
            double kPb = (wE > 0.0) ? (1.0 - BETA_DOWNDRAFT) * EPS_C * RHO_AIR * wE / RHO_WATER * 86400.0 * 1000.0 : 0.0;   // S630
            double qftF = Math.exp(-(0.5 * Atmosphere.H_EFF) / H_MOIST);
            double denB = 86400.0 * chvB + kPb + Cb * (1.0 - qftF);
            if (denB > 1.0e-12) q = 86400.0 * chvB * qsTsB * betaUsed * depl / denB;
        }
        double p = precip(q, wE);
        // ★ §720：把 BLQ 门控因子【提到公共作用域】，供浅对流地板共用。
        //   为什么：两道门只把 g 乘在【大尺度 p】上，而地板在它们之后执行、
        //   且是【无条件下界】⇒ 把门控压下去的结果又顶了回去。
        //   证据（§577 实测，P907，JJA，同一口径）：门控开时撒哈拉 = 0.045（观测 0.105），
        //   而 P683 实测 = 1.712（38 倍）⇒ 差距恰好是地板把它顶回去的部分。
        // ★ 2026-10-16 清理：原 `double blqG` 的唯一读者是被删掉的浅对流地板门控 ⇒ 已删。
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
        // ★★★★★ §577：θ_e 阈值判据（默认关）。阈值随海温变 ⇒ 冷海自然压低对流。
        if (BLQ_THETA_E) {
            double sstC = Atmosphere.seaSurfaceTemp(lat, theta)
                        + Atmosphere.sstAnom(x, z, theta) - 273.15;
            double teConv = thetaEConv(sstC);
            double teBl = thetaE(tQ, q);
            double dth = teBl - teConv;
            blqLastDh = dth; blqCalls++;
            double g = smoothstep01b(dth / BLQ_SMOOTH_K);
            if (g < 0.01) blqBlocked++;
            p *= g;
        }
        DIAG.get()[8] = p;
        // 风暴轴：瞬变斜压涡动的水汽通量辐合。
        // 只取辐合侧（mfc>0）：辐散侧（副热带）的「变干」已经由 w_zm 的下沉支表达，
        // 再减一次就是重复计数。
        if (withEddy) {
            double mfc = eddyMfc(lat, theta);
            // ★ 2026-10-16（goal-3355454e）：锚点 P -> (P - E)，仅南半球内带 50~35S ✓
            //   （出处与全部数字见 EDDY_ANCHOR_PME 的注释 ✓；关时逐位不变 ✓）
            if (EDDY_ANCHOR_PME) {
                double laDeg = Math.toDegrees(lat);
                // ★ 2026-10-16（goal-3355454e，A/B）：范围从内带 -50~-35 延伸到 **-70~-35**，
                //   因为轮 71 实测：外带 70~50S 模型 14.853 / GPCP **2.39** = **6.21 倍** ✗，
                //   而内带 2.924 / 2.54 = 1.15 ✓ —— 真正错的是**外带**，它从未被锚定过。
                if (laDeg <= -35.0 && laDeg >= -70.0) {
                    // ★ 2026-10-16（goal-3355454e）：内带 / 外带用**不同的季节阻尼系数** ✓
                    //   出处（原始 NCEP 自算，轮 66）：SH 46-58S 的涡动 MFC **相对振幅只有 25.9%** ✓
                    //   而 NH 40-60N 是 140.9% ✗ ⟹ SH 的季节性本来就弱 ⟹ 内带该压得更狠 ✓
                    final boolean outer = laDeg < -50.0;
                    final double damp = 0.301;   // 原有字面量（经验阻尼，无出处；内外带同值）
                    // ⚠ 必须与 mfc 同单位 ✓：eddyMfcObs 是**无量纲形状**（45~60 度均值 = 1 ✓），
                    //   而 mfc = EDDY_MFC_REF * 月形状 ⟹ 年均项也要乘同一个 REF ✓
                    //   （2026-10-16 实测教训：不乘 REF 会让内带降水爆到 4665 mm/day ✗）
                    double ann = EDDY_MFC_REF * ZonalTables.eddyMfcObs(laDeg);
                    mfc = 0.149 * ann + damp * (mfc - ann);
                }
            }
            // ★★ 2026-10-19：rolloff 必须作用在【阻尼之后】的最终 mfc 上。
            //   （原先乘在 eddyMfc 里 ⟹ 被上面的 ann 项拉回 ⟹ 杠杆被吃掉，实测 v3 与 EDDY=0 几乎相同）
            // ★ 2026-10-16（goal-3355454e）：EDDY_NO_DEPLCOL —— 去掉涡动项里的**地形耗竭因子** ✓
            //   理由（口径 ✓）：涡动 MFC 表是 -∇·(v'q')，其 q 是**观测**比湿 ✓
            //     ⟹ 它**已经包含**真实的水汽耗竭（含地形影响 ✓）
            //     ⟹ 再乘模型化的 depletionCol = exp(-hUp*κ/EDDY_DEPL_H) ✗ ⟹ **双重计数** ✗
            //   本仓实测（探针 NRESP ✓ 粗采样带平均 ✓）：
            //     外带 70~50S（多海洋 ✓ depletionCol=1 ✓）传递比 **0.936 / 0.939** ✓
            //     内带 50~35S（含安第斯等高地形 ✓）传递比 **0.176 / 0.171** ✗ ⟹ 差 **5.5 倍** ✗
            //     ⟹ 直接把 P296 的 R 抬大约 5.4 倍 ✗（实测 R 5.08/6.71 vs GPCP 0.92/0.72 ✓）
            //   默认 **false** ⟹ 逐位不变 ✓
            if (mfc > 0.0) {
                double colF = EDDY_NO_DEPLCOL ? 1.0 : depletionCol(hUp, k);
                // ★ 2026-10-19：本地水汽可用度（默认关 ⇒ localQ 恒为 1.0，逐位不变）。
                //   见 EDDY_LOCAL_Q 的 javadoc；κ=0 处 surfaceTemp == tSl ⇒ 比值 ≡ 1（海洋中性）。
                //   打开 PRECIP_USE_SURFACE_T 时直接复用 tQ（同一个 surfaceTemp，不重复求值）。
                double localQ = 1.0;
                if (EDDY_LOCAL_Q) {
                    double tSfcE = PRECIP_USE_SURFACE_T
                        ? tQ
                        : Atmosphere.surfaceTemp(x, z, seed, cell, theta);
                    localQ = qSat(tSfcE) / qSat(tSl);
                }
                p += mfc * depl * colF * localQ / RHO_WATER;
            }
        }
        // ★ 2026-10-16（goal-3355454e）：收支阻尼 -∂W/∂t ✓（默认关 ⟹ 逐位不变 ✓）
        //   出处 / 实测 / 单位 / 符号 / 口径 全见 BUDGET_DWDT_ON 的注释 ✓
        // ⚠⚠ 单位：p 全程是 **kg/(m^2 s)** ✓（由末尾 pMm = p*86400*1000 反推 ✓）
        //    ⟹ 本项**必须原样相减** ✗ 不可乘 86400 ✗
        //    （2026-10-16 我在此犯过一次单位错 ✗：乘了 86400 把 mm/day 量级减进 kg/(m^2 s) 量级，
        //      使 p 变成 ~-0.1，末尾 ×86400×1000 后爆到 9.1e6 ✗；当时还误判为"模型不稳定" ✗，
        //      被 pFloor 掩盖的纬度则表现为"逐位相同" ✓）
        // ⚠⚠ 单位：p 全程是 **m/s** ✓（由末尾 pMm = p*86400*1000 反推 ✓；×1000 -> mm ✓ ×86400 -> /day ✓）
        //    而 wColTendency 是 kg/(m^2 s) ✓ ⟹ 必须 **÷ RHO_WATER** ✓
        //    （与涡动那一行同惯例 ✓：mfc * ... / RHO_WATER ✓）
        //    ★ 2026-10-16 我在此连犯两次单位错 ✗（先乘 86400 ✗、后原样相减 ✗），
        //      每次都表现为 p 被放大到 ~1e2 mm/day ✗ —— 教训：**先看同一函数里其它项怎么换算** ✓
        if (BUDGET_DWDT_ON) p -= wColTendency(lat, theta) / RHO_WATER;   // kg/(m^2 s) -> m/s ✓
        DIAG.get()[9] = p;
        // ---- 候选 S-1：浅对流地板（§251）。SHALLOW_FLOOR=false 时整段被跳过 ⇒ 逐位不变。----
        if (SHALLOW_FLOOR) {
            double tSfcF = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
            DIAG.get()[12] = tSfcF;
            double qsSfcF = qSat(tSfcF);
            double spF = Math.hypot(u0[0], u0[1]);
            double vEff = Math.sqrt(spF * spF + V_GUST * V_GUST);
            double pFloor;
            if (SHALLOW_CONDENSATE) {
                // §677：Held & Soden 形式  P = E_P * sigma_up * rho * w_up * (q*_LCL - q*_ct) / rho_w
                //   全部输入有出处（§663/§667/§666）；唯一建模陈述是 z_ct := H_BL。全程 O(1)，无循环。
                double eS = ParcelLift.vaporPressure(q, P_SURF);
                double tD = ParcelLift.dewpoint(eS);
                double tLcl = ParcelLift.lclTempDaviesJones(tSfcF, tD);
                double pLcl = P_SURF * Math.pow(tLcl / tSfcF, ParcelLift.CP_D / ParcelLift.R_D);   // 干绝热闭式
                double qLcl = ParcelLift.qs(tLcl, pLcl);
                double zLcl = ParcelLift.zOfP(pLcl, tSfcF, Atmosphere.GAMMA);
                double zCt = Z_CT_COND;
                // ★ §7147：下沉封顶（默认 false ⇒ 逐位不变）。见 SHALLOW_FLOOR_ZI_LIMIT。
                if (SHALLOW_FLOOR_ZI_LIMIT) {
                    zCt = zCtEff(tSfcF, qsSfcF, q, lat, k, vEff, tLcl, pLcl, qLcl, divU);
                }   // §687：有出处的浅积云云顶（Squires 1958）
                double pCt = ParcelLift.pOfZ(zCt, tSfcF, Atmosphere.GAMMA);
                double dq = 0.0;
                if (pCt < pLcl) {
                    double tCt = tLcl - ParcelLift.gammaMoist(tLcl, pLcl, qLcl) * (zCt - zLcl);
                    dq = Math.max(0.0, qLcl - ParcelLift.qs(tCt, pCt));
                }
                double wsK = wStarK(tSfcF, qsSfcF, q, lat, k, vEff);
                // ★ 2026-10-16（goal-3355454e）：地板的速度尺度是否改用**浮力通量本身**。
                //   出处（一手）：Alinejadtabrizi et al. 2024, npj Clim. Atmos. Sci. 7(1) 76,
                //     DOI 10.1038/s41612-024-00625-1 与 Lang et al. 2022, ACP 22(3) 2135-2152,
                //     DOI 10.5194/acp-22-2135-2022 —— 南大洋浅对流季节循环的控制因子是
                //     **低层稳定度 + 冷空气平流到暖海面（海气温差 / 冷空气爆发）**，不是平均湿度。
                //   本仓自算（子代理 shallow_seasonal_obs.py，50~35S）：
                //     SST - T_air(1000) = +1.115(DJF) / +1.968(JJA) => JJA 大 1.77 倍；
                //     而 wStarK 是其立方根 => 被压平（实测 0.718/0.692）。
                //   ★ 2026-10-16：曾试「改用浮力通量（wsK 的三次方）」（开关 SHALLOW_FLOOR_FLUX_DRIVEN），
                //     **已删除**：Held & Soden 形式要求这里是一个**速度** [m/s]，
                //     而三次方的量纲是 [m^3/s^3] ⟹ 那是**量纲错误**，不是可选口径。
                //     实测（A/B）：内带 2.280/1.840（观测 2.54/3.17）⟹ 也确实更差。
                DIAG.get()[17] = wsK;      // ★ 只加一格、只多写一个值 ⟹ 不改任何结果
                DIAG.get()[18] = dq;       // ★ 只加一格、只多写一个值 ⟹ 不改任何结果
                pFloor = EP_COND * SIGMA_UP * RHO_AIR * wsK * dq / RHO_WATER;
            } else {
                double betaF = 1.0 - Atmosphere.clamp01(k);
                double eSh = RHO_AIR * Atmosphere.cdOf(k) * vEff * Math.max(0.0, betaF * qsSfcF - q);
                pFloor = ALPHA_SH * eSh / RHO_WATER;
            }
            // ★★★★★ §772：§720 的门控已【默认关闭】。详见 SHALLOW_FLOOR_BLQ_GATE 的 javadoc：
            //   BLQ_THETA_E 是深对流判据，地板是浅对流过程 ⇒ 门控是套错对象（category error）。
            // ★ 2026-10-16 清理：原门控 `if (SHALLOW_FLOOR_BLQ_GATE) pFloor *= blqG;` 已删除
            //   （§772 已判：深对流判据套浅对流地板是 category error ⇒ 只提供错误选项）。
            // ★ 2026-10-16（goal-3355454e）：地板乘空气-海面季节因子（默认关 ⟹ 逐位不变）。
            if (SHALLOW_FLOOR_ASF) pFloor *= ZonalTables.shallowAsf(Math.toDegrees(lat), theta);
            if (SHALLOW_FLOOR_LTS) pFloor *= ZonalTables.shallowLts(Math.toDegrees(lat), theta);
            DIAG.get()[10] = pFloor;
            if (pFloor > p) p = pFloor;
        }
        // ★ 2026-10-16（goal-3355454e）：用收支定义降水 ✓（默认关 ⟹ 逐位不变 ✓）
        //   出处/动机/**部分闭合的诚实标注**/单位 全见 P_FROM_BUDGET 的注释 ✓
        if (P_FROM_BUDGET) {
            double eBud = DIAG.get()[16];                        // 模型自己的 E ✓ kg/(m^2 s)
            double dwBud = wColTendency(lat, theta);             // 柱水汽倾向 ✓ kg/(m^2 s)
            // ★ A/B 变体 ✓：收支恒等式说**全** MFC 都进 P ✓（辐散侧为负 ⟹ 使 P 减小 ✓）
            //   ⚠ 风险 ✓：模型的 w_zm 下沉支本来就在表达副热带变干 ✓ ⟹ 可能是**双重计干** ✗
            //   本变体就是拿来测这一点的 ✓（测量说话 ✓）
            double mfcBud = eddyMfc(lat, theta);                 // 全 MFC ✓（含辐散侧 ✓）
            p = (eBud - dwBud + mfcBud) / RHO_WATER;             // kg/(m^2 s) -> m/s ✓
        }
        double pMm = p * 86400.0 * 1000.0;
        // ★ §462：植被的【降水反馈通道】 P_eff = P_d + D_B*V（文献：Liu et al. 2006a; Claussen et al. 2013）。
        //   ABI 是非对称的：若本线程正在解 V（V_OVERRIDE 非 null），**直接读覆盖值** ⇒ 绝不重入 vegAt。
        if (Vegetation.VEGETATION_ON) {
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
