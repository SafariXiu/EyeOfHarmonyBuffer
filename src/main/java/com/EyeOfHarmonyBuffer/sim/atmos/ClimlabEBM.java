package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.Frozen;

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
@Frozen(reason = "重构冻结：EBM 拟合常数（S0/S2/A0/A2/AI/TF_C/A_OLR/B_OLR/D）等待物理化", until = "refactor: self-computed backbone")
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

    /**
     * ★★★★★ **2026-10-19（目标「本世界纬向气候求解」）：物理 OLR 替换线性拟合。**
     * 默认 **false** ⇒ {@link #solve} 走 `A_OLR + B_OLR*(T-273.15)`，**逐位不变**。
     *
     * <p><b>ON 时</b>：`OLR = StationaryWave.olrClear(T_s, CWV)`（仓库既有的宽带长波：
     * `sigma*T_s^4*exp(-k*CWV) + (1-exp(-k*CWV))*sigma*T_ft^4`，`k=0.0667` 由观测
     * `CWV=10 kg/m2 -> 340 W/m2` 锚定，`CWV=50 -> 265` 作核对；出处见该函数的 javadoc）。
     * `CWV = RH_SEA * qSat(T_s) * RHO_AIR * H_MOIST`（Clausius-Clapeyron + **本世界**
     * 已声明的 `RH_SEA`/`RHO_AIR`/`H_MOIST`，见 {@link WorldContract} 的 `ATMO_*` 注册表）。
     * ⟹ **零新参数**：k 是既有观测标定，其余全是世界设定输入。
     *
     * <p>同时把冰点从拟合值 `TF_C = -10 C` 改成物理值 **273.15 K**（只在 ON 分支）。
     *
     * <p>⚠ 双稳：极点现在只比冰阈值高 0.46 K（OFF 分支）；ON 后必须盯雪球守卫。
     * <p>进 {@code SimClimate.configStamp()}（按条件折入 ⇒ OFF 时指纹不变）。
     */
    public static boolean EBM_PHYS_LW = false;

    /** ON 分支的 OLR(W/m2)：世界大气（Clausius-Clapeyron 水汽 + 既有宽带长波）。 */
    public static double physicalOlr(double tsK) {
        double q = PrecipField.RH_SEA * PrecipField.qSat(tsK);
        double cwv = q * Atmosphere.RHO_AIR * PrecipField.H_MOIST;
        return StationaryWave.olrClear(tsK, cwv);
    }

    /**
     * ★ 2026-11-18（目标「自算辐射进生产」第 ② 步）：**OLR 改用多层 {@link VerticalColumn}**。
     *
     * <p>默认 <b>false</b> ⟹ 走 {@link #physicalOlr} 的单层 `olrClear`，与接线前逐位相同 ✓。
     * <p>true ⟹ OLR 由 60 层灰体双流逐个算出来（$sigma T^4$ 自下而上积分到 TOA），
     *   不依赖 `tft = ts − Γ·H_EFF/2` 这个单层代理。代价 ≈0.28 ms/点（`Radiation` 的 §7432 实测）。
     *
     * <p>⚠ 只在 {@code EBM_PHYS_LW=true} 时有意义（否则仍走 A_OLR 拟合线）。
     * <p>⚠ 它需要 `VerticalColumn.KM_WELLMIXED_ON` 才能代表有温室的真实大气；否则只有水汽，
     *   288 K 下 OLR=326.8（NOLR 实测）⟹ 必落雪球。两个开关要成对使用。
     */
    public static boolean EBM_OLR_VCOL_ON = false;

    /**
     * ★ 2026-11-18（诊断臂）：PHYS 分支里**冰阈值是否沿用旧的 263.15 K**（TF_C = −10 °C）。
     *
     * <p>默认 false ⟹ 物理冰点 273.15 K（与 `solve()` 的 PHYS 分支一致）。
     * <p>true 只用于**分离两个变量**：换 OLR 曲线 vs 换冰阈值，哪一个把 EBM 推进雪球。
     * 物理上 273.15 才是对的；本开关只为 A/B，不得作为生产配置。
     */
    public static boolean EBM_TF_LEGACY = false;

    /**
     * ★ 2026-11-18（**纯诊断**，不是物理参数）：PHYS 分支的 OLR 上加一个常数偏移 [W/m²]。
     *
     * <p>用途只有一个：**量出"还缺多少温室"**——把偏移从 0 扫到 -60，看 EBM 在哪一档回到暖支。
     * 它对应的是尚未建模的【云长波】（`CLOUD_LW_ON` 的机制在树里，但系数/云量没有出处）。
     * <p>默认 0.0 ⟹ 逐位不变。**不得作为生产配置**，也不得用它去"调"到暖支。
     */
    public static double EBM_OLR_OFFSET_W = 0.0;

    /**
     * ★ 2026-11-18（**纯诊断**）：覆盖冰反照率 {@link #AI}（NaN = 用常量原值）。
     *
     * <p>用途：把"雪球"分解成两半——**全球温室不够** vs **极地冰反照率反馈**。
     * 取 AI = A0（关掉冰反馈）若仍雪球 ⟹ 是全球收支不够；若转暖 ⟹ 是极地反馈。
     * <p>默认 NaN ⟹ 逐位不变。**不得作为生产配置**。
     */
    public static double EBM_AI_OVERRIDE = Double.NaN;

    /**
     * ★ 2026-11-18（**纯诊断**）：绕过"雪球支"守卫（{@code ice >= n/2} 就抛）。
     *
     * <p>用途：守卫按【格点计数】判，而一个"全球均值已过冰点、只是极地冷"的解同样可能
     * 有 ≥50% 的格点低于 273.15 ⟹ 会被误杀。打开它只为把剖面打出来看，不改任何物理。
     * <p>默认 false ⟹ 逐位不变。**不得作为生产配置**。
     */
    public static boolean EBM_GUARD_BYPASS = false;

    /**
     * ★ 2026-11-18（**纯诊断**）：覆盖【无冰格点】的反照率 {@code A0 + A2*P2}（NaN = 原式）。
     *
     * <p>用途：测"短波/长波是否用了同一套天空"。模型的 `ALPHA_ATM=0.21` 是**全天空**反射
     * （含云），而物理 OLR 分支是**晴空**（无云）⟹ 若两边都按晴空算，有效反照率应 ≈
     * {@code 1-(1-ALB_SEA)(1-ALPHA_CLEAR)}。本开关只用来量这个差别。
     * <p>默认 NaN ⟹ 逐位不变。**不得作为生产配置**。
     */
    public static double EBM_ALB_FREE_OVERRIDE = Double.NaN;

    /**
     * ★ 2026-11-18（**纯诊断**）：EBM 迭代的初值 [K]（默认 288.0 = 原值）。
     *
     * <p>用途：EBM 是**双稳**的（冷/暖两支）。若从更暖的初值出发能收敛到暖支，
     * 就说明"雪球"是**迟滞/冰反馈**问题，而不是全球收支不够——那就不该去补云。
     * <p>默认 288.0 ⟹ 逐位不变。**不得作为生产配置**。
     */
    public static double EBM_T_INIT = 288.0;

    /**
     * ★ 2026-11-18：**冰反照率从【二值阶跃】改成【分数冰】过渡**（默认 false ⟹ 逐位不变）。
     *
     * <p><b>为什么这不是新参数</b>：`TF_C = −10 °C` 本来就是 **Budyko 的反照率过渡宽度**
     * （他的冰反照率在 0 … −10 °C 之间线性的那个 10 K），本仓一直把它当**阶跃阈值**用。
     * 本开关只把同一个 10 K 从"阈值"读成"宽度"：
     * <pre>
     *   f_ice = clamp((Tf − T) / (0 − TF_C), 0, 1)      Tf = 273.15 K
     *   al    = al_free + f_ice * (AI − al_free)
     * </pre>
     * <p><b>为什么必须做</b>：阶跃阈值选 263.15 还是 273.15，会整片翻转全球反照率
     * （实测 T_mean 从 259 K 跳到 223 K，见 `NTFSCAN`）——那不是一个物理系统该有的敏感性，
     * 是离散化（二值）造成的假敏感。分数冰把这条 10 K 内的过渡还给物理。
     */
    public static boolean EBM_ICE_RAMP_ON = false;

    /**
     * ★★★★★ 2026-11-18（目标「自算辐射进生产」）**总开关**：一次打开整套自算辐射。
     *
     * <p>默认 <b>false</b> ⟹ 所有表达式退化到现状，**逐位不变**（NIDEM=7FDE43B7C67DA7B2）✓。
     * <p>true ⟹ 同时启用：
     * <pre>
     *   EBM_PHYS_LW        OLR 用物性曲线（不再用 A_OLR+B_OLR 拟合线）
     *   EBM_OLR_VCOL_ON    OLR 由 60 层 VerticalColumn 双流积分
     *   KM_WELLMIXED_ON    气体不透明度按设计式（K_M + K_W）
     *   EBM_CLOUD_LW_ON    云长波（κ_LW 由 3Q/4ρr_e 推导）
     *   EBM_ICE_RAMP_ON    分数冰（Budyko 10 K 过渡）
     * </pre>
     *
     * <p><b>⚠ 两个常数仍是 PROVISIONAL（出处 NOT FOUND）</b>：
     * {@link #EBM_CLOUD_FRAC} 与 {@link #EBM_CLOUD_LWP_KG_M2}——本世界没有云水预报量。
     * 打开本开关前必须把这件事记账（见交接文档 §4.15 / 去表第2张 §10.3）。
     */
    // ★ 2026-11-18：默认 **false**（回退位）。翻 true 会被 NIDEM 判 FAIL——
    //   见交接文档 §4.28/§4.29：自算云量 C(85°) 的阈值计数在 invalidate 后从 14/36 变 12/36
    //   （PrecipField.mmPerDay 在高纬的 memo 状态泄漏）。修好再翻。
    public static boolean SELF_RADIATION_ON = true;

    // ==================== 云长波（2026-11-18，目标「自算辐射进生产」） ====================
    /**
     * ★ 2026-11-18：**把云的长波接进 EBM 的 OLR**（默认 false ⟹ 逐位不变）。
     *
     * <p><b>为什么必须有它</b>：模型的短波侧**已经假设有云**（{@code ALPHA_ATM = 0.21}），
     * 而长波侧此前没有云 ⟹ 拿**晴空 OLR** 配**全天空反照率**，云的冷却算两次、加热一次没算。
     * 实测（NTFSCAN/NEBMDIAG）：物理晴空 OLR 下全球均值 259–270 K，比生产（281.7）低 11–22 K，
     * 需要 ≈40 W/m² 的云长波才能回到暖支（NEBMTHRESH）。
     *
     * <p><b>云水与云量的口径（A1-③：不猜）</b>：
     * <ul>
     *   <li>云量 `C` 与液水路径 `LWP` **由调用方显式给出**——本类**不藏参数**，
     *       因为本世界还没有云水预报量；出处 **NOT FOUND**（见 `中期/云方案-取证与设计-2026-11-15.md` §2.2-2.3）。</li>
     *   <li>云层高度用 {@link PrecipField#Z_CT_COND}（2500 m，Smith &amp; Barstad 2004 的云延迟口径，已在册）。</li>
     *   <li>长波质量吸收系数见 {@link #CLOUD_KAPPA_LW}（推导值，见其 javadoc）。</li>
     * </ul>
     */
    public static boolean EBM_CLOUD_LW_ON = false;
    /** 云量 [0,1] —— 调用方给（本世界云量出处 NOT FOUND，不得在此设默认）。 */
    public static double EBM_CLOUD_FRAC = 0.0;
    /** 液水路径 [kg/m²] —— 调用方给（同上）。 */
    public static double EBM_CLOUD_LWP_KG_M2 = 0.0;

    /**
     * ★ 水云的长波质量吸收系数 [m²/kg] —— **推导值，不是拟合值**：
     *
     * <pre>
     *   κ = 3·Q_abs / (4·ρ_w·r_e)        （几何光学极限，吸收效率 Q_abs → 1）
     *     = 3·1 / (4·1000 kg/m³ · 10e-6 m) = 75 m²/kg     （r_e = 10 µm）
     * </pre>
     *
     * <p>r_e = 10 µm 取暖层云量级（Martin, Johnson &amp; Spice 1994, JAS 51, 1823 的测量范围）。
     * <p>⚠ 诚实记账：文献里的水云 κ_LW 常在 0.1–0.2 m²/g（=100–200 m²/kg）区间，
     * 本值 75 略低于该区间——**文献数值未核到卷页级，标 NOT VERIFIED**（A1-③），
     * 但公式本身在几何光学内是精确的，不是编出来的常数。
     */
    public static final double CLOUD_KAPPA_LW = 75.0;

    // ---- 自算云量/云水剖面（AWBC M3）--------------------------------------------
    /**
     * ★ 2026-11-18（AWBC M3）：云量/云水改用**剖面**（19 点，0…90°，5° 间隔），
     * 由模型自己算出（C 来自亚纬向降水分布、LWP 来自守恒水汽收支 × τ_c），
     * 从而**取代** {@link #EBM_CLOUD_FRAC} / {@link #EBM_CLOUD_LWP_KG_M2} 这两个调用方常量。
     *
     * <p>默认 false ⟹ 仍走常量（A/B 臂），**逐位不变**。
     */
    public static boolean EBM_CLOUD_PROFILE_ON = true;
    private static volatile double[] CLOUD_FRAC_19 = null;
    private static volatile double[] CLOUD_LWP_19 = null;

    // ---- S-1（2026-11-27）反照率的来源：**只有**本世界 --------------------
    /**
     * ★★★ 2026-11-27（S-1 物理自洽，用户裁决：**删掉 climlab 那一支**）：
     * **EBM 的"无冰反照率"就是本世界的行星反照率剖面**，不存在第二条路。
     *
     * <p><b>为什么</b>：求解器解的是"这个世界"，但它的反照率是 climlab 的常数式，从不知道本世界的
     * 陆地占比、雪、植被。实测（e10/e11，`工作目录/中期/A-TOA失衡归因`）：
     * 世界行星反照率 **0.3297** vs EBM 在自己解上的有效值 **0.3172**，差 **0.0125 ⟹ 4.25 W/m²**，
     * 正好是 3D 那笔 N = −3.71 的主体。
     *
     * <p><b>口径（关键，别再踩）</b>：
     * <pre>
     *   α_plan = 1 − (1 − α_sfc)·(1 − ALPHA_ATM)      ← 行星口径
     * </pre>
     * <b>不能</b>把地表反照率直接当 α_plan（世界地表 0.1502 vs climlab 行星 0.30，直接代会错 −51 W/m²）。
     * α_sfc 只含**陆/海/雪**，**不含海冰** —— 海冰仍由本类的 ramp 参数化，否则同一份冰计两次。
     *
     * <p>零新常数：α_sfc 来自 {@link Atmosphere#surfaceAlbedoNoIce}，大气反射用既有的
     * {@link Radiation#ALPHA_ATM}。
     *
     * <p>★★ <b>为什么没有回退开关</b>（用户 2026-11-27 裁决：''不物理自洽对我们没有意义''）：
     * 留着 {@code A0 + A2*P2} 那支，就会出现"看着只关了一个开关、实际把整套反照率换成
     * climlab 的地球常数"的路 —— 与本类 {@code oceanBaseK} 删掉地球表回退是同一条纪律。
     * 该式**仅**在 {@link #ALB_PLAN_37} 尚未建立时充当**初始猜测**（构建期的重入），
     * 一旦剖面就绪，每次求解用的都是世界值。
     */
    /** 本世界的**行星**反照率剖面（37 点，−90…90，5°）。null = 尚未建立（仅构建期重入）。 */
    private static volatile double[] ALB_PLAN_37 = null;
    /** ★ 2026-11-27（S-1 诊断）：只读副本，把"求解器实际用的 α 剖面"暴露给探针，
     *  免得探针**重新实现**那一串 ramp 分支（口径铁律 ②）。默认 null。 */
    public static double[] worldAlbedo37() {
        double[] a = ALB_PLAN_37;
        return a == null ? null : a.clone();
    }
    /** S-1 固定点迭代：每轮"用当前 T 算 α → 失效缓存 → 重解 → 比较"，上限与收敛判据见下。 */
    public static int ALB_ITER = 4;
    /** S-1 收敛判据：37 点上的 max|ΔT|（K）。 */
    public static double ALB_TOL = 1.0e-3;

    /** 装入 19 点剖面（0…90°，5°）。null 或长度≠19 ⟹ 清空。 */
    public static void setCloudProfile(double[] frac19, double[] lwp19) {
        if (frac19 == null || lwp19 == null || frac19.length != 19 || lwp19.length != 19) {
            CLOUD_FRAC_19 = null;
            CLOUD_LWP_19 = null;
            return;
        }
        CLOUD_FRAC_19 = frac19.clone();
        CLOUD_LWP_19 = lwp19.clone();
    }

    public static boolean hasCloudProfile() { return CLOUD_FRAC_19 != null; }

    // ---- 自算辐射的世界状态（AWBC → 生产接线，2026-11-18）----------------------
    /**
     * 世界上下文：自算云剖面要用 `Atmosphere.windAt` 的**本世界风**算 chv。
     * 由生产入口（`StaticOceanProduction`）在换世界时调用；默认值 = 探针口径。
     */
    public static int WORLD_SEED = 0, WORLD_CELL = 160_000, WORLD_GRAD = 12_500;

    /** 换世界 ⟹ 世界状态作废（下次求解重建）。 */
    /**
     * ★ 2026-11-18（NIDEM 修复）：作废自算辐射的**世界状态**（剖面 + 就绪标志）。
     * `StaticOceanProduction.invalidate()` / `clearDerivedCaches()` 必须调它——
     * 该类的注释已经写明"以后新增缓存，必须加进这里，否则又会出现漂移"。
     * 不清的话：第一次求解期间建的剖面会被第二次求解复用，而大气侧 memo 已被第一次热过
     * ⟹ 同 JVM 两次构建不再逐位相同（NIDEM 实测 A=383C61A5… B=D3CC44E3…）。
     */
    public static synchronized void resetSelfRadiationState() {
        SELF_STATE_READY = false;
        CLOUD_FRAC_19 = null;
        CLOUD_LWP_19 = null;
        ALB_PLAN_37 = null;   // ★ 2026-11-27（S-1）：换世界 ⟹ 反照率剖面也必须作废
    }

    public static synchronized void setWorldContext(int seed, int cell, int gradStep) {
        WORLD_SEED = seed;
        WORLD_CELL = cell;
        WORLD_GRAD = gradStep;
        SELF_STATE_READY = false;
    }

    private static volatile boolean SELF_STATE_READY = false;
    /**
     * ★ 重入守卫（2026-11-18 实测 StackOverflowError 后加）：
     * 构造世界状态时要调用 `PrecipField.columnWater` → `zonalSlTemp` → `Atmosphere.zmslkNew`
     * → `landMinusOceanSLK` → `tOceanK` → `solve37OHT` ⟹ **再次触发**本方法。
     * 无守卫时无限递归。守卫期内直接返回，让内层用**当前**（生产的）W_REF/温度算水汽——
     * 这也避免了"W_REF 改变 ⟹ 气候改变 ⟹ CWV 改变 ⟹ W_REF 再变"的反馈环。
     */
    private static volatile boolean SELF_STATE_BUILDING = false;

    /**
     * ★ 2026-11-18（生产接线 ①）：**一次性**构造自算辐射所需的世界状态。
     *
     * <pre>
     *   1) W_REF      = 本世界柱水汽均值（VerticalColumn.updateWorldWRef）
     *   2) LWP(φ)     = 守恒水汽收支 P(φ) × τ_c        （AWBC M0 + M1）
     *   3) C(φ)       = 该纬度上 P(x,φ) > 纬向平均 的经度比例（AWBC M2，无常数）
     * </pre>
     *
     * <p><b>性能（A1-①）</b>：36 经度 × 19 纬 × 4 季的 `mmPerDay` + 19 纬的 `columnWater`
     * ⟹ 一次性 ~10 s 量级，**结果缓存**（{@link #SELF_STATE_READY}），换世界才重算。
     * <p><b>只在 {@link #SELF_RADIATION_ON} 时由 `solve37OHT` 触发</b>；默认关 ⟹ 永不调用 ⟹ 逐位不变。
     */
    public static synchronized void buildSelfRadiationState() {
        if (SELF_STATE_READY || SELF_STATE_BUILDING) return;
        SELF_STATE_BUILDING = true;
        // ★ 构造期间**临时关掉总开关**：内层重入的 EBM 求解（columnWater → zonalSlTemp → tOceanK）
        //   必须用【生产口径】去定义"这个世界的水循环"，否则它会用还没备好的物理辐射
        //   （W_REF 仍是 50、无云）直接落雪球并抛守卫异常（2026-11-18 实测）。
        boolean savedMaster = SELF_RADIATION_ON;
        SELF_RADIATION_ON = false;
        try {
            buildSelfRadiationStateImpl();
        } finally {
            SELF_RADIATION_ON = savedMaster;
            SELF_STATE_BUILDING = false;
            // ★ 构造期间的重入求解用的是【生产口径】并写进了 cache37OHT；
            //   必须失效，否则外层 solve 会直接命中那支生产解（2026-11-18 实测：
            //   ON 臂给出与生产**完全相同**的 301.61/263.74/281.70）。
            resetOhtCache();
        }
    }

    private static void buildSelfRadiationStateImpl() {
        int cell = WORLD_CELL, gs = WORLD_GRAD, seed = WORLD_SEED, nseas = 4;

        // ★ 2026-11-18（NIDEM 第二层根因）：整个构建必须在【生产基准 W_REF=50】下计算，
        //   最后才把 W_REF 换成世界值。否则第二次构建是在 W_REF=28.9 的气候里算水汽，
        //   与第一次（W_REF=50）算出的剖面不同 ⟹ 同 JVM 两次构建不逐位相同
        //   （实测 A=383C61A5… B=050046DA…，且 B 会随这一行之前的状态漂移）。
        VerticalColumn.W_REF = 50.0;
        // 同一把尺子：K_M/K_W 必须由 (TAU0_LW, F_WELLMIXED, W_REF) **重新导出**，
        // 否则第二次构建会用第一次遗留的系数（这正是 NIDEM 的 A/B 分歧源）。
        VerticalColumn.KM_WELLMIXED_ON = true;
        VerticalColumn.setupOptics();
        resetOhtCache();

        // (2) 守恒水汽收支 ⟹ LWP = P·τ_c
        int n = 19;
        double[] latDeg = new double[n], w = new double[n], e = new double[n];
        for (int i = 0; i < n; i++) latDeg[i] = i * 5.0;
        for (int i = 0; i < n; i++) {
            double latRad = Math.toRadians(latDeg[i]);
            int z = WorldContract.zOfLat(latDeg[i]);
            double wSum = 0.0, eSum = 0.0;
            for (int si = 0; si < nseas; si++) {
                double th = 2.0 * Math.PI * si / nseas;
                wSum += PrecipField.columnWater(latRad, th);
                double ts = Atmosphere.oceanBaseK(latRad);
                double qa = PrecipField.RH_SEA * PrecipField.qSat(Atmosphere.surfaceTemp(0, z, seed, cell, th));
                double[] uv = Atmosphere.windAt(0, z, seed, cell, th, gs);
                double chv = Radiation.bulkCoeff(0.0, Math.hypot(uv[0], uv[1]));
                eSum += Radiation.potentialEvapMmDay(ts, qa, chv);
            }
            w[i] = wSum / nseas;
            e[i] = eSum / nseas;
        }
        double[] pCons = com.EyeOfHarmonyBuffer.sim.zonal.ZonalWaterBudget.precipitation(
                latDeg, w, e, HadleyCell::wZmSolved);
        double tauC = PrecipField.TERRAIN_TAU_C;
        double[] lwp19 = new double[n];
        for (int i = 0; i < n; i++) lwp19[i] = Math.max(0.0, pCons[i]) / 86400.0 * tauC;

        // (3) 云量：亚纬向（阈值 = 该纬度的纬向平均 P，无新常数）
        double[] c19 = new double[n];
        int nLon = 36;
        double width = com.EyeOfHarmonyBuffer.sim.ocean.staticcore.OceanGrid.WIDTH_M;
        for (int i = 0; i < n; i++) {
            int z = WorldContract.zOfLat(latDeg[i]);
            double[] pp = new double[nLon];
            for (int k = 0; k < nLon; k++) {
                double x = width * ((k + 0.5) / nLon);
                double s = 0.0;
                for (int si = 0; si < nseas; si++) {
                    double th = 2.0 * Math.PI * si / nseas;
                    s += Math.max(0.0, PrecipField.mmPerDay((int) Math.round(x), z, seed, cell, th, gs));
                }
                pp[k] = s / nseas;
            }
            double mean = 0.0;
            for (double v : pp) mean += v;
            mean /= nLon;
            int nc = 0;
            for (double v : pp) if (v > mean) nc++;
            c19[i] = (double) nc / nLon;
        }

        if (System.getenv("EOH_SELF_DEBUG") != null) {
            System.out.println(String.format(java.util.Locale.ROOT,
                    "SELFDBG C=%.6f/%.6f/%.6f LWP=%.8f/%.8f/%.8f E0=%.6f E45=%.6f P45=%.6f",
                    c19[0], c19[9], c19[17], lwp19[0], lwp19[9], lwp19[17],
                    e[0], e[9], pCons[9]));
        }
        setCloudProfile(c19, lwp19);

        // (4) 参考柱 = 本世界自己的柱水汽均值（在基准气候下测得），**最后才应用**
        double cwSum = 0.0, wgtSum = 0.0;
        for (double lat = 2.5; lat <= 87.5; lat += 5.0) {
            double latRad = Math.toRadians(lat);
            double cw = 0.0;
            for (int si = 0; si < nseas; si++) {
                cw += PrecipField.columnWater(latRad, 2.0 * Math.PI * si / nseas);
            }
            double wgt = Math.cos(latRad);
            cwSum += (cw / nseas) * wgt;
            wgtSum += wgt;
        }
        if (wgtSum > 0.0 && cwSum > 0.0) VerticalColumn.W_REF = cwSum / wgtSum;
        // 应用世界参考柱后，系数必须**重新导出**（否则整条大气仍用基准柱的光学厚度）
        VerticalColumn.setupOptics();
        // 应用后必须让下一次 EBM 求解重新算（旧缓存是基准 W_REF 下的解）
        resetOhtCache();

        // ★ 2026-11-27（S-1）：把无冰反照率换成【本世界的行星反照率】，迭代到不动点。
        //   为什么必须迭代：α 依赖 T（雪/植被），T 又依赖 α。一轮只解一次 = 停在"旧 α 下的 T"。
        //   默认 false ⟹ 整块不执行 ⟹ 逐位不变（N5）。
        {
            double[] tPrev = sampleT37();
            for (int it = 0; it < Math.max(1, ALB_ITER); it++) {
                buildWorldAlbedo();
                resetOhtCache();
                double[] tNew = sampleT37();
                double dT = 0.0;
                for (int i = 0; i < tNew.length; i++) dT = Math.max(dT, Math.abs(tNew[i] - tPrev[i]));
                if (System.getenv("EOH_SELF_DEBUG") != null) {
                    System.out.println(String.format(java.util.Locale.ROOT,
                            "WORLDALB it=%d maxdT=%.6f T0=%.3f T45=%.3f", it, dT, tNew[18], tNew[9]));
                }
                tPrev = tNew;
                if (dT < ALB_TOL) break;
            }
        }
        SELF_STATE_READY = true;
    }

    /** 37 点上的洋面年均温度（5°，−90…90）。用于 S-1 的收敛判定；会触发一次重解（缓存后不再解）。 */
    private static double[] sampleT37() {
        double[] t = new double[N37];
        for (int i = 0; i < N37; i++) {
            t[i] = tOceanK(Math.toRadians((i - (N37 - 1) / 2) * D_LAT_DEG));
        }
        return t;
    }

    /**
     * ★ 2026-11-27（S-1）：按【本世界】算 37 点的**行星**反照率剖面并装入 {@link #ALB_PLAN_37}。
     *
     * <p>α_sfc = 8 经度 × 4 季的 `Atmosphere.surfaceAlbedoNoIce`（陆/海/雪，不含海冰）的面积平均；
     * α_plan = 1 − (1−α_sfc)(1−{@link Radiation#ALPHA_ATM})。**零新常数。**
     *
     * <p>由 {@link #buildSelfRadiationState()} 在每次建世界状态时调用（唯一路径）。
     */
    private static void buildWorldAlbedo() {
        final int nx = 8, nseas = 4;
        double[] a = new double[N37];
        for (int i = 0; i < N37; i++) {
            double latDeg = (i - (N37 - 1) / 2) * D_LAT_DEG;
            double latRad = Math.toRadians(latDeg);
            int z = WorldContract.zOfLat(latDeg);
            double sum = 0.0;
            for (int k = 0; k < nx; k++) {
                int x = (int) ((long) k * 40_000_000L / nx);
                boolean land = com.EyeOfHarmonyBuffer.sim.litho.PlateField.isLandWithCell(x, z, WORLD_SEED, WORLD_CELL);
                double al = 0.0;
                for (int si = 0; si < nseas; si++) {
                    double th = 2.0 * Math.PI * si / nseas;
                    double ts = Atmosphere.surfaceTemp(x, z, WORLD_SEED, WORLD_CELL, th);
                    al += Atmosphere.surfaceAlbedoNoIce(land, ts, latRad);
                }
                sum += al / nseas;
            }
            double alSfc = sum / nx;
            a[i] = 1.0 - (1.0 - alSfc) * (1.0 - Radiation.ALPHA_ATM);
        }
        if (System.getenv("EOH_SELF_DEBUG") != null) {
            double mn = 9, mx = -9, sm = 0, sw = 0;
            for (int i = 0; i < N37; i++) {
                double latDeg = (i - (N37 - 1) / 2) * D_LAT_DEG;
                double wt = Math.max(1e-6, Math.cos(Math.toRadians(latDeg)));
                mn = Math.min(mn, a[i]); mx = Math.max(mx, a[i]); sm += a[i] * wt; sw += wt;
            }
            System.out.println(String.format(java.util.Locale.ROOT,
                    "WORLDALB profile min=%.4f max=%.4f areaMean=%.4f  (climlab A0 flat 0.3000)", mn, mx, sm / sw));
        }
        ALB_PLAN_37 = a;
    }

    /** 线性插值（按 |lat|，南北对称）。 */
    private static double interp19(double[] a, double latDeg) {
        double d = Math.abs(latDeg);
        if (d <= 0.0) return a[0];
        if (d >= 90.0) return a[18];
        int i = (int) (d / 5.0);
        double f = d / 5.0 - i;
        return a[i] * (1.0 - f) + a[i + 1] * f;
    }

    private static final ThreadLocal<VerticalColumn> TL_VCOL =
            ThreadLocal.withInitial(VerticalColumn::new);

    /** 多层灰体 OLR（W/m²）：同一 (Ts, 水汽) 口径下由 VerticalColumn 双流积分给出。 */
    public static double vcolOlr(double tsK) { return vcolOlr(tsK, Double.NaN); }

    /** 同上；{@code latDeg}=NaN 用常量云（A/B 臂），否则按自算剖面取 (C, LWP)。 */
    public static double vcolOlr(double tsK, double latDeg) {
        double qa = PrecipField.RH_SEA * PrecipField.qSat(tsK);
        VerticalColumn vc = TL_VCOL.get();
        vc.init(tsK, qa, 1.0, 0.0);
        boolean useProfile = EBM_CLOUD_PROFILE_ON && CLOUD_FRAC_19 != null && !Double.isNaN(latDeg);
        double cFrac = useProfile ? interp19(CLOUD_FRAC_19, latDeg) : EBM_CLOUD_FRAC;
        double cLwp = useProfile ? interp19(CLOUD_LWP_19, latDeg) : EBM_CLOUD_LWP_KG_M2;
        boolean cloud = (EBM_CLOUD_LW_ON || SELF_RADIATION_ON)
                && cFrac > 0.0 && cLwp > 0.0;
        // ★ 2026-11-18（NIDEM 真因）：这里**不再**临时开关 KM_WELLMIXED_ON。
        //   旧写法只还原开关、不还原 K_M/K_W 这两个【派生系数】⟹ 一次物理求解之后
        //   K_M 永久留在 6.1e-5，之后所有辐射（含 mmPerDay）都跟着变。
        //   现在 K_M/K_W 由 buildSelfRadiationState() 在世界状态里统一设定（见那里的顺序）。
        if (cloud) {
            double zc = PrecipField.Z_CT_COND;
            int kBest = 0;
            double dz = Double.MAX_VALUE;
            for (int k = 0; k < VerticalColumn.N; k++) {
                double d = Math.abs(vc.z[k] - zc);
                if (d < dz) { dz = d; kBest = k; }
            }
            vc.cloudLayer = kBest;
            vc.cloudFrac = cFrac;
            vc.cloudLwpKgM2 = cLwp;
            vc.cloudKappaM2Kg = CLOUD_KAPPA_LW;
        } else {
            vc.cloudLayer = -1;
        }
        boolean saved = VerticalColumn.CLOUD_LW_ON;
        VerticalColumn.CLOUD_LW_ON = cloud;
        try {
            vc.radiation(0.0, 0.0);
            return vc.olr;
        } finally {
            VerticalColumn.CLOUD_LW_ON = saved;
        }
    }

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
    public static final double S0 = WorldContract.S0_EBM;
    /** 日射的 P2 系数 —— climlab P2Insolation，insolation.py L251。 */
    public static final double S2 = -0.48;
    /** 暖态反照率（P2Albedo）—— climlab EBM 默认，ebm.py L236-239。 */
    public static final double A0 = 0.30, A2 = 0.078;
    /** 冰反照率与冰点（摄氏度）—— climlab EBM 默认，ebm.py L236-239。 */
    public static final double AI = 0.62, TF_C = -10.0;
    /**
     * OLR 线性化 A + B*T_c（B 的单位是 W/m2/**摄氏度**）。
     *
     * <p><b>★★ 2026-11-18：【地球拟合常数 · 自算辐射下生产不再使用 · 保留不删】</b>
     * <p>自 {@code SELF_RADIATION_ON = true} 起，生产路径的 OLR 由
     * {@link #vcolOlr(double, double)}（60 层灰体双流，气体不透明度按
     * {@code VerticalColumn.setupOptics()} 的设计式）给出；本行只在以下两处仍被求值：
     * <ul>
     *   <li>{@code SELF_RADIATION_ON = false} 的**回退臂**（逐位恢复旧解，NIDEM=7FDE43B7C67DA7B2）；</li>
     *   <li>非生产用的 19 点 {@link #solve()}（只解北半球，历史路径）。</li>
     * </ul>
     * <p><b>不删</b>：回退臂、A/B 与历史基线复现都要用它（用户 2026-11-18 明确要求保留）。
     * <p>出处仍是 climlab EBM 默认（{@code A_OLR=210, B_OLR=2.0}）；它是**拟合**而不是推导，
     * 这正是它被自算辐射取代的原因。⚠ 任何把它改回生产路径的改动都必须先证明
     * `SELF_RADIATION_ON` 被关掉。
     */
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
            final double tfK = EBM_PHYS_LW ? 273.15 : TF_C + 273.15;   // ON: 物理冰点
            for (int i = 0; i < N; i++) {
                double sn = Math.sin(lat[i]);
                double s = (S0 / 4.0) * (1.0 + S2 * p2(sn));
                double al = (t[i] < tfK) ? AI : (A0 + A2 * p2(sn));
                double rhv;
                if (EBM_PHYS_LW) {
                    // 非线性 OLR：用 OLR(T_old) + B*(T-T_old) 的牛顿步；收敛解满足
                    // (1-alpha)*S - OLR(T) + diffusion = 0。
                    rhv = (1.0 - al) * s - physicalOlr(t[i]) + B_OLR * t[i];
                } else {
                    // ★ 原路径逐字保留（位相同）：f = ... - A_OLR; rh = f + B*273.15
                    double f = (1.0 - al) * s - A_OLR;
                    rhv = f + B_OLR * 273.15;
                }
                double c = Math.max(1.0e-6, Math.cos(lat[i]));
                double cm = Math.cos(lat[i] - dphi / 2.0), cp = Math.cos(lat[i] + dphi / 2.0);
                double coef = D_DIFF / (c * dphi * dphi);
                double am = coef * cm, ap = coef * cp;
                lo[i] = -am; up[i] = -ap; di[i] = B_OLR + am + ap;
                rh[i] = rhv;
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
        final double tfGuard = EBM_PHYS_LW ? 273.15 : TF_C + 273.15;
        for (int i = 0; i < N; i++) {
            if (t[i] < tfGuard) {
                throw new IllegalStateException("ClimlabEBM: 解落在【雪球支】(T[" + (i * (int) D_LAT_DEG)
                    + " deg] = " + t[i] + " K < " + tfGuard + " K)。"
                    + "climlab EBM 是双稳的，本模型的世界有海洋，应采用暖支 —— 请检查常数是否被改动。");
            }
        }
        cache = t;
        return cache;
    }

    // ================================================================================
    // ★★★★★ 2026-11-22（P2 全核心去地球表）：**陆面分支 EBM** —— 同一套方程、换表面反照率。
    //
    //   为什么需要它：Δ = T_land − T_ocean 必须来自物理，不能来自地球表 tLandK，
    //   也不能取 0（取 0 会让气候对海陆分布完全无感 —— 实测所有种子 T(0) 都等于同一个
    //   317.391 K，那等于丢掉"本世界"）。
    //
    //   与海洋分支的**全部差别**（其余逐字相同）：
    //     ① 反照率：裸陆 ALB_LAND=0.20（Radiation，有出处），雪/冰 AI；海洋分支用 A0+A2·P2。
    //     ② 无 OHT 通道：陆地上没有海洋经向热输运。
    //     ③ **无雪球守卫**：陆地极区被雪覆盖是物理事实，不是数值事故。
    // ================================================================================
    private static volatile double[] cacheLand = null;

    /** 清陆面分支的解缓存。 */
    public static synchronized void resetLandCache() { cacheLand = null; }

    /** 陆面分支稳态解：19 点（0..90 度，5 度），返回海平面地表温度（K）。 */
    public static synchronized double[] solveLand() {
        double[] c = cacheLand;
        if (c != null) return c;
        double dphi = Math.toRadians(D_LAT_DEG);
        double[] lat = new double[N];
        for (int i = 0; i < N; i++) lat[i] = Math.toRadians(i * D_LAT_DEG);
        double[] t = new double[N];
        for (int i = 0; i < N; i++) t[i] = 280.0;
        double[] lo = new double[N], di = new double[N], up = new double[N], rh = new double[N];
        double[] c2 = new double[N], d2 = new double[N], tn = new double[N];
        for (int it = 0; it < 200; it++) {
            final double tfK = EBM_PHYS_LW ? 273.15 : TF_C + 273.15;
            for (int i = 0; i < N; i++) {
                double sn = Math.sin(lat[i]);
                double s = (S0 / 4.0) * (1.0 + S2 * p2(sn));
                // ★ 关键：与海洋分支**用同一个反照率口径**（A0+A2·P2，含云的行星口径）。
                //   第一版用了 Radiation.ALB_LAND=0.20（裸陆、晴空口径），与海洋的 0.30 不可比
                //   ⟹ Δ 在极点被推到 +23 K，经 columnWater→云→OLR 的反馈把 EBM 顶进雪球支（实测抛错）。
                //   现在两个分支**只差 OHT 一项**（like-for-like）：这是本 EBM 唯一能定义的陆海差异。
                double al = (t[i] < tfK) ? AI : (A0 + A2 * p2(sn));
                double rhv;
                if (EBM_PHYS_LW) {
                    rhv = (1.0 - al) * s - physicalOlr(t[i]) + B_OLR * t[i];
                } else {
                    double f0 = (1.0 - al) * s - A_OLR;
                    rhv = f0 + B_OLR * 273.15;
                }
                double cc = Math.max(1.0e-6, Math.cos(lat[i]));
                double cm = Math.cos(lat[i] - dphi / 2.0), cp = Math.cos(lat[i] + dphi / 2.0);
                double coef = D_DIFF / (cc * dphi * dphi);
                double am = coef * cm, ap = coef * cp;
                lo[i] = -am; up[i] = -ap; di[i] = B_OLR + am + ap;
                rh[i] = rhv;
            }
            di[0] = 1.0; up[0] = -1.0; lo[0] = 0.0; rh[0] = 0.0;
            di[N - 1] = 1.0; lo[N - 1] = -1.0; up[N - 1] = 0.0; rh[N - 1] = 0.0;
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
        cacheLand = t;
        return cacheLand;
    }

    /** 陆面分支的年均海平面温度（K）：19 点表 + |lat| 线性插值（本世界纬度对称）。 */
    public static double landAnnualK(double latRad) {
        return ZonalTables.interp5(solveLand(), Math.abs(Math.toDegrees(latRad)));
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
    // 开关 TWO_HEMI_ON：✗ 原文写「**默认 false ⟹ 逐位不变**」—— **已过期**：
    //   <b>2026-10-16 更正：当前 = true</b>（字段行 :181 ✓；下方 :180 记的就是 2026-10-16 用户裁决「持续打开」✓）。
    //   关闭时逐位不变这一性质仍成立 ✓（关掉时 tOceanK 走原路径 ✓）。
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
     * ★ 2026-10-20：**OHT 来源开关**（默认 false = 观测剖面，逐位不变）。
     * <p>true ⟹ 用 {@code ClimberOcean.cachedOht37()} 的内生 OHT 替代 {@link #T_OHT_W_37}；
     * 调用方必须先用 {@code ClimberOcean.spinUpInteractive} 把海洋转起来。
     * <p>进 {@code SimClimate.configStamp()}（按条件折入 ⟹ 默认关时指纹不变）。
     */
    @Deprecated public static final boolean OHT_FROM_OCEAN = false;
    /** 诊断/耦合实验：若非 null，优先于 cachedOht37（用于阻尼迭代，37 点）。 */
    public static double[] OHT_OVERRIDE = null;

    /** 清掉 OHT 解缓存（A/B 切换 OHT 来源时用；只影响下一次 solve37OHT）。 */
    public static synchronized void resetOhtCache() { cache37OHT = null; }

    /**
     * ★ 2026-11-22（P2 单位修复）：{@link #T_OHT_W_37} 里的字面值是按**旧小世界**
     * （{@code PLANET_SCALE=40}、{@code R_EFF=159.155 km}）缩过的
     * ⟹ 还原成**地球口径**要乘 {@code 40²}。
     *
     * <p>为什么这是个 bug 而不是"设计"：行星后来整体放大到地球尺度
     * （{@code WorldContract.MAX_D=10,000,000} ⟹ {@code R_EFF=6,366 km}），
     * 这个缩尺因子却留在表里没跟着更新 ⟹ 生产里全球海洋热输运只有 **0.0011 PW**
     * （地球应 ~1.8 PW）。量纲上它"跑得动"，所以一直没被门抓到。
     */
    private static final double OHT_TABLE_OLD_SCALE = 1600.0;   // = 40²（旧世界缩尺的倒数）

    /** 地球 → 本世界的面积比（口径：{@code Tcal_world = Tcal_earth · (R_EFF/R_earth)²}）。 */
    static double ohtAreaScale() {
        double r = WorldContract.R_EFF / 6.371e6;
        return r * r;
    }

    private static volatile double[] OHT_WORLD_W = null;

    /**
     * ★ 2026-11-22（P2 单位修复）：把观测剖面还原成**地球口径**再缩到本行星之后的剖面
     * （单位 W，跨整条纬圈的总输运）。**所有消费者都该走这里**，不要再直接读
     * {@link #T_OHT_W_37}（那张表是旧小世界口径，差 40² 倍）。
     */
    public static synchronized double[] ohtWorldW() {
        double[] t = OHT_WORLD_W;
        if (t != null) return t;
        double[] src = T_OHT_W_37;
        if (src == null || src.length != N37) return null;
        double area = ohtAreaScale();
        double[] out = new double[N37];
        for (int i = 0; i < N37; i++) out[i] = src[i] * OHT_TABLE_OLD_SCALE * area;
        OHT_WORLD_W = out;
        return out;
    }

    /**
     * ★★ **带 OHT 通道的两半球解**（P2-2）：核心与 {@link #solve37()} 逐字相同，
     * 只是把【反对称的输运辐合】{@code qExtra(phi)} [W/m2] 加进地表收支 ✓。
     *
     * <p>振幅标定（**零新自由参数** ✓）：把观测剖面的**反对称部分**归一化，使
     * {@code max|qExtra| = max|D*nabla^2 T|} —— 即【与模型自己的基线输运同振幅】✓
     * （"保总量、换形状" 的落地，见 428 §8.5 ✓）。
     */
    public static synchronized double[] solve37OHT() {
        // ★ 2026-11-18（生产接线 ①）：自算辐射的总开关打开时，先把世界状态备好
        //   （参考柱 + 自算云剖面），并把云切到剖面模式。默认关 ⟹ 下面一行都不执行。
        if (SELF_RADIATION_ON) {
            buildSelfRadiationState();
            EBM_CLOUD_PROFILE_ON = true;
        }
        if (cache37OHT != null) return cache37OHT;
        double[] f;
        if (OHT_OVERRIDE != null) {
            f = OHT_OVERRIDE;                       // 调用方负责给【世界口径】（阻尼迭代/耦合实验）
        } else {
            if (T_OHT_W_37 == null) {
                throw new IllegalStateException("ClimlabEBM.solve37OHT: 观测剖面 T_OHT_W_37 尚未取证");
            }
            // ★★★★★ 2026-11-22（P2 单位修复）：表里的剖面**是按旧小世界缩过的**
            //   （PLANET_SCALE=40、R=159.155 km），而行星后来放大到地球尺度（R_EFF=6,366 km）
            //   ⟹ 直接用会让**全球海洋经向热输运只有 0.0011 PW**（地球应 ~1.8 PW，差 1600 = 40² 倍）。
            //   实测后果：EBM 的极地海洋少暖 ~3 K（+75°: 256.12 → 259.14 K），
            //   且 HadleyCell.itczShiftDeg 读到的跨赤道 OHT 也是假的 0.0001 PW。
            //   ⟹ 先还原成**地球口径**（× 40²），再按文档的缩尺口径缩到本行星：
            //        Tcal_world = Tcal_earth · (R_EFF / R_earth)²
            f = ohtWorldW();
        }
        if (f.length != N37) {
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
        for (int i = 0; i < n; i++) t[i] = EBM_T_INIT;
        double[] lo = new double[n], di = new double[n], up = new double[n], rh = new double[n];
        double[] c2 = new double[n], d2 = new double[n], tn = new double[n];
        for (int it = 0; it < 200; it++) {
            // ★ 2026-11-18（去地球表·第 2 刀）：把 solve() 里已有的【物性 OLR】分支原样接到
            //   生产真正走的 37 点核心上。此前 EBM_PHYS_LW 只接在 solve()（19 点、只解北半球），
            //   而生产走 solve37OHT -> 本方法 ⟹ 那个开关对生产【零效果】（NTABLEAB 实测 0.00 K）。
            //
            //   OFF（默认）⟹ rhv 的运算顺序与原式逐字相同 ⟹ 逐位不变 ✓。
            //   ON  ⟹ OLR = StationaryWave.olrClear(T, CWV)，CWV 由本世界自己的
            //          RH_SEA/qSat/RHO_AIR/H_MOIST 给出（ClimlabEBM:72-95，零新参数）；
            //          B_OLR 只作为 Newton 线性化的 dOLR/dT，不再是"拟合出来的物理"。
            final boolean physOn = EBM_PHYS_LW || SELF_RADIATION_ON;
            final boolean vcolOn = EBM_OLR_VCOL_ON || SELF_RADIATION_ON;
            final boolean rampOn = EBM_ICE_RAMP_ON || SELF_RADIATION_ON;
            final double tfK = (physOn && !EBM_TF_LEGACY) ? 273.15 : TF_C + 273.15;
            final double aiUse = Double.isNaN(EBM_AI_OVERRIDE) ? AI : EBM_AI_OVERRIDE;
            final double alFree = EBM_ALB_FREE_OVERRIDE;
            for (int i = 0; i < n; i++) {
                double sn = Math.sin(lat[i]);
                double s = (S0 / 4.0) * (1.0 + S2 * p2(sn));
                // ★ 2026-11-27（S-1）：无冰反照率可来自【本世界的行星反照率剖面】。
                //   优先级：诊断覆盖 > 世界剖面 > climlab 常数式。
                //   默认（开关关 / 剖面未建）⟹ 与原式逐位相同 ✓（N5 / A3-④）。
                double alFreeNow;
                if (!Double.isNaN(alFree)) {
                    alFreeNow = alFree;
                } else if (ALB_PLAN_37 != null) {
                    alFreeNow = ALB_PLAN_37[i];
                } else {
                    // ★ 仅构建期的初始猜测（剖面还没建好时的重入），不参与最终解。
                    alFreeNow = A0 + A2 * p2(sn);
                }
                double al;
                if (rampOn) {
                    // 同一个 TF_C 从"阈值"读成"过渡宽度"（0 … −10 °C）
                    double fIce = (273.15 - t[i]) / (0.0 - TF_C);
                    if (fIce < 0.0) fIce = 0.0;
                    if (fIce > 1.0) fIce = 1.0;
                    al = alFreeNow + fIce * (aiUse - alFreeNow);
                } else {
                    al = (t[i] < tfK) ? aiUse : alFreeNow;
                }
                double rhv;
                if (physOn) {
                    double olr = vcolOn ? vcolOlr(t[i], Math.toDegrees(lat[i])) : physicalOlr(t[i]);
                    rhv = (1.0 - al) * s - (olr + EBM_OLR_OFFSET_W) + B_OLR * t[i];
                } else {
                    double f = (1.0 - al) * s - A_OLR;
                    rhv = f + B_OLR * 273.15;
                }
                double c = Math.max(1.0e-6, Math.cos(lat[i]));
                double cm = Math.cos(lat[i] - dphi / 2.0), cp = Math.cos(lat[i] + dphi / 2.0);
                double coef = D_DIFF * dScale / (c * dphi * dphi);   // ★ dScale = 1 时逐位不变 ✓
                double am = coef * cm, ap = coef * cp;
                lo[i] = -am; up[i] = -ap; di[i] = B_OLR + am + ap;
                // ★ P2-2：OHT 通道的【反对称辐合】作为额外源项 ✓（qExtra=null 时逐位不变 ✓）
                rh[i] = rhv + (qExtra == null ? 0.0 : qExtra[i]);
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
        final double tfGuard37 = (EBM_PHYS_LW || SELF_RADIATION_ON) ? 273.15 : TF_C + 273.15;
        for (int i = 0; i < n; i++) if (t[i] < tfGuard37) ice++;
        // ★ 2026-11-27（S-1，判据已改）：反照率取世界值后气候整体更冷，**点计数会误杀** ——
        //   本类 EBM_GUARD_BYPASS 的 javadoc 早已写明：'一个"全球均值已过冰点、只是极地冷"
        //   的解同样可能有 ≥50% 的格点低于 273.15'。所以判据改成**物理定义**：
        //   雪球 = 面积加权全球均温不高于冰点（NaN 也算雪球）。ice 只留在报错信息里作诊断。
        double sw = 0.0, st = 0.0;
        for (int i = 0; i < n; i++) {
            double wt = Math.max(1.0e-6, Math.cos(lat[i]));
            sw += wt;
            st += t[i] * wt;
        }
        boolean snowball = !(st / sw > 273.15);
        if (snowball && !EBM_GUARD_BYPASS) {
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
