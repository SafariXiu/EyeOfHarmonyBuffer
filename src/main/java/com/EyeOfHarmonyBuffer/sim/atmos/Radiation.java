package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * S1a —— **辐射闭合**（设计冻结 §358 / §360，实测见 P554）。
 *
 * <p>存在理由：{@code src} 里原本**没有**太阳辐射、没有反照率、没有 OLR
 * （§348 结论四：grep 0 命中）。后果是地表温度是**诊断**给的、没有蒸发冷却，
 * 于是 Manabe eq.19 的潜在蒸发 {@code rho*C_D*|V|*(q_sat(T_s)-q)} 虚高
 * （P554 实测亚洲 **17.03 mm/day**，而能量限制值应为 ~7），
 * 使陆地桶在季风区也会把地榨干（§357）。
 *
 * <p>闭合（陆地 {@code G = 0}，Manabe 1969 eq.16 的先例）：
 * <pre>
 *   (1 - alpha_s) * S  =  eps*sigma*T_s^4 + H + LE
 *   H  = rho_a * c_p * C_H * |V| * (T_s - T_a)
 *   LE = rho_a * L_v * C_E * |V| * max(0, q_sat(T_s) - q_a)
 * </pre>
 *
 * <p><b>系数取法（声明，不新造数）</b>：{@code C_H = C_E = Atmosphere.cdOf(kappa)}
 * —— §354 把 {@code C_H}/{@code C_E} 的具体值列为**第③层未核验**，
 * 所以复用模型自己的拖曳系数，而不是从文献里抄一组无法核验的数。
 *
 * <p><b>OLR 取 {@code eps*sigma*T^4} 而不是 {@code A + B*T}</b>：§354 已证各来源的 A/B
 * 不一致且依赖参考态；{@code eps*sigma*T^4} 只需**一个**有效发射率，
 * 且直接由 Trenberth et al. (2009) 的观测 OLR 238.5 W/m^2 @ 288.15 K **定标**。
 *
 * <p><b>⚠ §7432 修订（2026-09-26）</b>：上面那条「不取 {@code A + B*T}」的决定**仍然有效**，
 * 但 {@link #residual} 的**长波项**已从单点的 {@code EPS*SIGMA*Ts^4} 改为
 * **逐层灰体**（{@link VerticalColumn}）给出的**净**地表长波 {@code SIGMA*Ts^4 - L_down}。
 * 理由：{@code EPS} 是 **TOA 有效发射率**（238.5/σ288.15⁴），拿它当地表净长波会**高估 1.8 倍**，
 * 于是 {@code Ts} 被解偏低、{@code qSens} 偏负。逐层方案与 Isca Frierson 同参数
 * （{@code TAU0_LW = 6.0} ⇔ {@code ir_tau_eq = 6.0}），且本仓的光学厚度用**质量路径**
 * （{@code tau = OLR_K*W(p)}）⇒ 有真实水汽反馈。详见 {@link #TL_VC}。
 */
public final class Radiation {

    private Radiation() {}

    /**
     * 太阳常数（W/m^2）= 1361.0 —— ★ <b>现代标准值</b>（TSI 观测；本仓自选，非 climlab）✓。
     *
     * <p>⚠⚠ <b>2026-10-14 审查（阶段 3 · 重复常量扫描）：全仓共有三处 S0，且值不同</b> ✗ ——
     * <ul>
     *   <li><b>本处</b> = 1361.0（现代 TSI 标准）✓</li>
     *   <li>{@link VerticalColumn#S0} = 1361.0（与本处一致 ✓）</li>
     *   <li>{@code ClimlabEBM.S0} = <b>1365.2</b> ✗ —— 那是<b>逐字取自 climlab 源码</b>
     *       （const.S0, insolation.py L251）✓，属<b>另一套模型的口径</b> ✓</li>
     * </ul>
     * ⟹ ★ 两者服务不同模型，**都不算错** ✓；但引用时必须写全类名 ✓，
     * <b>不要</b>把 climlab 的 1365.2 与本处混用 ✗（那会改 EBM 的稳态解 ✗）。
     */
    public static final double S0 = 1361.0;
    public static final double SIGMA = 5.670374419e-8;
    public static final double CP = 1004.0;
    public static final double LV = 2.45e6;
    /** 有效发射率：238.5 / (sigma * 288.15^4)。 */
    public static final double EPS = 238.5 / (SIGMA * Math.pow(288.15, 4));
    public static final double ALB_SEA = 0.06;
    public static final double ALB_LAND = 0.20;
    public static final double ALB_SNOW = 0.65;

    // ==================== ★★★★★ 海冰/雪反照率（2026-10-15 接线 ✓）====================
    //
    // 出处（全部逐字核对过 ✓，见 待裁决/410、411、412 ✓）：
    //   · UCAR/NCAR 技术说明 484（CCSM3 的 solar parameterization ✓）——
    //     这是本节的**主要来源** ✓；CICE 文档（CSM 的海冰模式 ✓）提供热力学参数 ✓。
    //   · 反照率的**原始观测**：SHEBA 现场（Sturm et al. 2002 / Perovich & Grenfell 2002 ✓）
    //     与 Grenfell & Perovich (1984), JGR 89, 3573-3580 ✓
    //
    // ⚠⚠ 来源**自己声明**的三处不确定性（照录，勿删 ✓）：
    //   ① 「The **-1 degC criterion is purely arbitrary**」（UCAR 484 逐字 ✓）
    //   ② 「The .075 decline for ponded ice is simply an **educated guess**,
    //        constrained by SHEBA measurements（Perovich et al. 2002）」（逐字 ✓）
    //   ③ 「this relatively simple method … **may not include enough physics** to
    //        accurately describe the feedback」
    //        （引 Maykut & Perovich 1987 / Curry et al. 1995 / Holland & Bitz 2003 ✓）
    //
    /** 裸冰反照率（可见 < 700 nm ✓）。出处：CICE 文档 p28（CCSM3 的 ccsm3 方案 ✓）。 */
    public static final double ALB_ICE_VIS = 0.78;
    /** 裸冰反照率（近红外 > 700 nm ✓）。出处：CICE 文档 p28 ✓。 */
    public static final double ALB_ICE_NIR = 0.36;
    /**
     * **冷雪（Tsf &lt; -1 degC）的单带（broadband）反照率** ✓ —— 0.86 ✓。
     *
     * <p>出处：UCAR 484 逐字「If we take the **65%/35%** vs and ni partition,
     * the **broadband albedos are 0.86(x1)** and 0.81(x3)」✓（取 x1 = 单层 ✓）。
     */
    public static final double ALB_SNOW_BB_COLD = 0.86;
    /**
     * **近融化雪（Tsf 到 0 degC）的单带反照率** ✓ —— 0.745 ✓。
     *
     * <p>出处：UCAR 484 逐字「at 0 degC the near-melting albedos are .86, .53 vs and ni
     * respectively (x1) … with a **broadband 65%/35% value of 0.745(x1)**」✓。
     */
    public static final double ALB_SNOW_BB_MELT = 0.745;
    /**
     * 可见/近红外的**能量分配比**（可见占比 ✓）—— 默认 **0.65** = UCAR 484 的
     * 「A **mean overcast sky partition would be 65%/35%**」✓。
     *
     * <p>⚠ UCAR 484 逐字：该分配「depends mainly on **solar zenith angle, cloudiness and
     * surface albedo**」✓ —— 本常数是它的**均值** ✓；
     * 文档另给 clear sky：雪 52/48、**海冰 51/49**、深池 50/50 ✓（留作后续可切换 ✓）。
     */
    public static double PART_VIS = 0.65;
    /** 冰反照率的厚度因子常数（m^-1 ✓）。出处：UCAR 484 **方程 (6)** 逐字 `c_fh = 4 m^-1` ✓。 */
    public static final double C_FH = 4.0;
    /** 方程 (6) 里的参考冰厚（m ✓）—— UCAR 484 逐字「The **0.5 m** sea ice thickness
     *  between thin and thick albedos is somewhat arbitrary, being loosely based on
     *  measurements」✓。 */
    public static final double H_ICE_REF = 0.5;
    /** 融化判据温度（K ✓）= **-1 degC**。出处：UCAR 484 逐字（⚠ 它自称 purely arbitrary ✓）。 */
    public static final double T_MELT_K = 272.15;
    /** 冰反照率随温度（-1 -> 0 degC）的下降量（每个带 ✓）。出处：CICE p28 / UCAR 484 ✓（⚠ 自称 educated guess ✓）。 */
    public static final double ALB_ICE_DECLINE = 0.075;
    /** 淡水冰的**热导率**（W/m/K ✓）—— Stefan 解用。出处：CICE 文档 **方程 62** `K_0 = 2.03` ✓。 */
    public static final double K_ICE = 2.03;
    /** 海冰**密度**（kg/m3 ✓）。出处：CICE 文档 p68 `rhoi = 917. kg/m3` ✓。 */
    public static final double RHO_ICE = 917.0;
    /** 淡水冰的**熔化潜热**（J/kg ✓）。出处：CICE 文档 **方程 61** `L_0 = 3.34e5` ✓。 */
    public static final double L_FUSION = 3.34e5;
    /**
     * ★★★★★ **海冰/雪反照率开关**（2026-10-15 ✓）—— 默认 **false** ⟹ **逐位不变** ✓。
     *
     * <p><b>它补的是什么</b>：原实现里海洋分支**直接返回 {@link #ALB_SEA}** ✗ ——
     * 即「结冰了也不改反照率」✗。这正是 {@code SimTerrain} 那句自述所指的缺口 ✓：
     * 「陆地的最冷月还要考虑**雪/冰反照率**，**本模型没有那套反馈**」✓。
     *
     * <p><b>回路（现成的 ✓，不必新建）</b>：反照率 -&gt; {@code netColumnHeating}
     * 的感热与辐射两项 -&gt; S2 定常波的强迫 ✓（{@link #ALB_LAND_ADD} 的注释已论证过 ✓）。
     *
     * <p>⚠ 本开关改变气候输出 ⟹ **必须折进 {@code SimClimate.configStamp()}**（已折 ✓）。
     */
    // ★★★★★ 2026-10-15：**用户裁决打开** ✓ —— 用于实测这个反馈的效果 ✓
    //   （关闭态的全量验收已通过 ✓：pass=8 / fail=0 ✓，见 待裁决/413 ✓）
    public static boolean SEA_ICE_ALBEDO_ON = true;
    /**
     * 冰反照率的**温度过渡偏移**（K ✓）：`DTs = max(0, tSurfK - T_MELT_K)` ✓。
     * 出处：UCAR 484 **方程 (3)** 逐字「DTs = 0, Ts &lt; -1 degC ｜ = Ts + 1, Ts &gt; -1 degC」✓。
     */
    static double meltDeltaK(double tSurfK) {
        double d = tSurfK - T_MELT_K;
        return d > 0.0 ? d : 0.0;
    }

    // ==================== 大气/云反射（§7353：行星反照率层） ====================
    /**
     * ★★★★★ **行星（系统）反照率**；权威值，Goosse《Climate System Dynamics》§2.1.6 逐字：
     * <pre>
     *   the incoming solar radiation on a horizontal surface at the top of the atmosphere is about 342 Wm-2,
     *   with roughly 30% of this being reflected back into space.
     * </pre>
     */
    public static final double ALPHA_PLANET = 0.30;
    /**
     * ★★★★★ **反射中发生在【大气】里的比例**；权威值，同段逐字：
     * <pre>
     *   An analysis of the Earth's global heat balance shows that more that 70% of the reflection
     *   takes place in the atmosphere, mainly because of the presence of clouds and aerosols.
     *   The remaining 30% is reflected by the surface.
     * </pre>
     */
    public static final double ATM_FRACTION = 0.70;

    /**
     * ★★★★★ **§7353 大气/云反射率（由上面两个权威数字【导出】，零自由参数）**：
     * {@code ALPHA_ATM = ATM_FRACTION * ALPHA_PLANET = 0.70 * 0.30 = 0.21}。
     *
     * <p><b>为什么必须有它</b>：本文件原来只提供【地表】反照率（{@link #ALB_SEA}/{@link #ALB_LAND}），
     * 而四处 {@code absSolar} 都写成 {@code S * (1 - alpha_sfc)} —— 那等于假设**大气不反射任何短波**
     * ⟹ 隐含的行星反照率**只有 0.106**（P1088 实测，按 {@code KAPPA_MEAN = 0.328} 加权），
     * 而观测是 **0.30** ⟹ **偏低 64.7%**。
     *
     * <p><b>正确形式</b>（大气先反射，剩下的到地表再反射一次）：
     * <pre>
     *   ASR_planet  = S * (1 - alpha_planet)
     *   ASR_surface = S * (1 - alpha_atm) * (1 - alpha_sfc)
     *   => 隐含行星反照率 = alpha_atm + (1 - alpha_atm) * alpha_sfc
     * </pre>
     * **⟹ 用本常数后，全球平均行星反照率 = 0.2937（观测 0.30，差 −0.0063 = −2.1%）** ✓（P1088 实测）
     *
     * <p>★ <b>与 {@code ClimlabEBM} 的关系</b>：那个内核用的是 climlab 的 {@code a0 = 0.30}，
     * **本来就是行星反照率（含云）** ⟹ **无缺口、不要重复加**。本层只补【地表能量平衡那一支】。
     */
    public static final double ALPHA_ATM = ATM_FRACTION * ALPHA_PLANET;

    /**
     * ★★★★★ **§7353 接线开关：把大气/云反射加进四处 {@code absSolar}。**
     *
     * <p>关闭时（{@code false}）逐位回到接线前的行为 —— 但那是【已确证的缺陷】：
     * 四处 {@code absSolar} 都写成 {@code S * (1 - alpha_sfc)}，等于假设大气不反射任何短波，
     * 隐含的行星反照率只有 **0.106**（按 {@code KAPPA_MEAN = 0.328} 加权，P1088 实测），
     * 而观测是 **0.30** ⟹ **偏低 64.7%** ✗
     *
     * <p>打开时每处的 {@code absSolar} 乘 {@code (1 - ALPHA_ATM) = 0.79} ⟹ 到达地表的短波降 21%，
     * 隐含行星反照率变成 **0.2937**（观测 0.30，差 −2.1%）✓
     *
     * <p>**⚠ 这是大改动**（地表温度、蒸发、降水、定常波强迫全受影响）⟹ **必须跑 22 支套件。**
     *
     * <p>★ <b>2026-10-11 口径更正（同 §785 的处置方式：保留正确的值，更正文档）</b>：
     * 原文写「默认 false ⟹ 逐位不变」，那是 **§7355 臂之前的接线前状态**。
     * <b>现值是 {@code true}</b> —— 与 {@code SoilMoisture.SOIL_MOISTURE_ON} 完全同一模式
     * （默认值被翻成 true 而注释未同步）。
     * <h3>★★★ 用户裁决（2026-10-11）</h3>
     * <p><b>裁决：保持 {@code true}</b>。
     * <p>依据：{@code false} 是【已确证的缺陷】（隐含行星反照率 0.106 vs 观测 0.30，
     * 偏低 64.7%，P1088 实测），而 {@code true} 给出 0.2937（差 −2.1%）✓。
     * <p>⟹ 本常量【不再是「临时的臂」】，而是【生效的生产配置】。
     * 行内注释里「临时」二字已随之失效（保留原文只为溯源）。
     */
    public static boolean ATM_REFLECT = true;    // ★ §7353 生产配置（2026-10-11 用户裁决：保持 true，依据见上）

    /**
     * ★★★★★ **§7353：到达【地表】的短波（W/m²）—— 四处 {@code absSolar} 的【单源】。**
     *
     * <pre>
     *   ATM_REFLECT = false :  S * (1 - alpha_sfc)                    // 与接线前【逐位相同】
     *   ATM_REFLECT = true  :  S * (1 - alpha_sfc) * (1 - ALPHA_ATM)  // 补上大气/云反射
     * </pre>
     *
     * <p>四个调用点：{@code PrecipField:2675} · {@code SoilMoisture:269} ·
     * {@code StationaryWave:314} · {@code SimClimate:912}。集中在这里 ⟹ 将来不会再漏掉某一处。
     */
    public static double absSolarSurface(double latRad, double dec, double albSfc) {
        double s = insolation(latRad, dec) * (1.0 - albSfc);
        return ATM_REFLECT ? s * (1.0 - ALPHA_ATM) : s;
    }
    /** 雪面判据（与 SimTerrain 的雪线同值）。 */
    public static final double T_SNOW_K = 273.15;

    /**
     * ★ S1a 接线开关（§358/§360/§368/§385）：把 {@code SimClimate} 里【诊断】的陆地皮温
     * 换成【表面能量平衡解出】的皮温。**默认 false ⇒ 逐位不变。**
     *
     * <p>为什么需要它：{@code P568}（§385）实测陆地上感热 {@code H} 是【负】的
     * （亚洲 −74.8、撒哈拉 −29.7 W/m²）—— 根因是诊断温度 {@code T_a} 没有蒸发冷却、偏高。
     * 而 {@code H} 的符号是 S2 的强迫场 {@code Q} 的前提。
     *
     * <p>⚠ 本开关改变气候输出 ⇒ **必须折进 {@code SimClimate.configStamp()}**（已折）。
     */
    // ★★★★★ 2026-10-15 用户裁决：「让它起到应该起的作用」⟹ **打开** ✓
    //   为什么这是关键：本模型的温度链原来是【EBM(TOA) + SST'】✗，
    //   不含【地表能量平衡】✗ ⟹ 地表反照率（含海冰/雪）**进不了温度** ✗。
    //   打开它之后，SimClimate.buildTile 会把 tSea 换成由地表能量平衡解出的皮温
    //   （SimClimate:1203-1218），而那里的 alb0 已接到 Radiation.albedo ✓
    //   ⟹ **地表反照率（含海冰/雪）第一次真正进入生产温度** ✓✓
    //   ⚠ 这是架构级改动（每个点的温度都会变）⟹ 必须全量重跑验收 ✓
    public static boolean SKIN_TEMP_FROM_ENERGY_BALANCE = true;

    /** 日平均 TOA 日照（W/m^2）。{@code phi} 与 {@code dec} 为弧度。 */
    public static double insolation(double phi, double dec) {
        double x = -Math.tan(phi) * Math.tan(dec);
        double h0 = (x <= -1.0) ? Math.PI : (x >= 1.0 ? 0.0 : Math.acos(x));
        return (S0 / Math.PI) * (h0 * Math.sin(phi) * Math.sin(dec)
               + Math.cos(phi) * Math.cos(dec) * Math.sin(h0));
    }

    /**
     * ★★★ **§441：陆地反照率的加性偏移**（默认 **0.0** ⇒ 逐位不变）。
     *
     * <p>这是**植被状态量将来要驱动的那个量**：裸地反照率高、植被低。
     * 先把它做成一个可扫描的旋钮，用来回答 V′ 的 go/no-go ——
     * **Charney (1975) 型「植被 ⇒ 反照率 ⇒ 加热 ⇒ 环流 ⇒ 降水」的正反馈，在模型里到底闭不闭得上。**
     *
     * <p>为什么必须走反照率这条路（而不是水分侧）：§440 已实测，任何**全局均匀**的
     * 水汽侧干预（表面阻力）都只放大「撒哈拉更湿」这个错误排序。
     * 而反照率影响的是 `netColumnHeating` 的**感热与辐射**两项 ⇒ 改变 **S2 定常波的强迫** ⇒
     * 而 §396 已经证明：**强迫取对时，求解器给出的正是「亚洲辐合 / 撒哈拉辐散」。**
     */
    public static double ALB_LAND_ADD = 0.0;

    /** 地表反照率（**旧签名，保留** ✓ —— 需要纬度时用下面那个重载 ✓）。 */
    public static double albedo(boolean isLand, double tSurfK) {
        return albedo(isLand, tSurfK, Double.NaN);
    }

    /**
     * 地表反照率（**带纬度** ✓）—— 海冰/雪的反馈需要纬度才算得出冰厚 ✓。
     *
     * <p>★ {@code latRad} 为 NaN 或开关关闭时，**与旧实现逐位相同** ✓
     * （海洋 → {@link #ALB_SEA} ✓；陆地 → 雪判据 ✓）。
     */
    public static double albedo(boolean isLand, double tSurfK, double latRad) {
        if (!isLand) {
            if (!SEA_ICE_ALBEDO_ON || Double.isNaN(latRad)) return ALB_SEA;
            return seaIceAlbedo(latRad, tSurfK);
        }
        if (!SEA_ICE_ALBEDO_ON || Double.isNaN(latRad)) {
            return tSurfK < T_SNOW_K ? ALB_SNOW : ALB_LAND + ALB_LAND_ADD;
        }
        // ★ 陆地：雪反照率升级为【温度连续】版 ✓（有出处：UCAR 484 方程 3 ✓）
        if (tSurfK >= T_SNOW_K) return ALB_LAND + ALB_LAND_ADD;
        return snowBroadband(tSurfK);
    }

    /**
     * ★★★★★ **冻结度日**（FDD ✓，K·s ✓）—— 用本仓自己的 **ERA5 月温度表**精确积分 ✓。
     *
     * <pre>
     *   FDD(lat) = SUM_{m=0..11} max( 0, T_f - tZmSlMonth(lat, theta_m) ) * dt
     * </pre>
     *
     * <p><b>为什么可以这么做</b> ✓：
     * <ul>
     *   <li>{@link ZonalTables#T_ZM_SL_MONTH} 是 **ERA5 月平均海平面温度**（12 个月 ✓），
     *       有出处 ✓（生成脚本见 ZonalTables 的头注释 ✓）。</li>
     *   <li>{@code dt} 用本仓已有的 {@code WorldContract.DAYS_PER_YEAR} 与 86400 s ✓
     *       ⟹ **零新常数** ✓。</li>
     *   <li>它是**逐点纯函数** ✓ ⟹ 不破坏本模型「无状态、纯函数」的架构 ✓。</li>
     *   <li>先例：{@code Atmosphere:641} 已在做同类的「月表 - 年表」运算 ✓。</li>
     * </ul>
     *
     * <p>⚠ 这是 **Stefan 解**要的驱动量 ✓ —— 见 {@link #iceThicknessM} ✓。
     */
    public static double fddK(double latRad) {
        double tf = SimTerrain.SEA_ICE_T;                       // 海水冰点 271.35 K ✓（本仓已有 ✓）
        double dt = WorldContract.DAYS_PER_YEAR * 86400.0 / 12.0;
        double sum = 0.0;
        for (int m = 0; m < 12; m++) {
            double theta = 2.0 * Math.PI * (m + 0.5) / 12.0;    // 月中点 ✓
            double t = ZonalTables.tZmSlMonth(latRad, theta);
            double d = tf - t;
            if (d > 0.0) sum += d * dt;
        }
        return sum;
    }

    /**
     * ★★★★ **冰厚**（m ✓）—— **Stefan 解** ✓，由 {@link #fddK} 驱动 ✓。
     *
     * <pre>
     *   dH/dt = -( k / (H * rho * L) ) * (T_s - T_f)      （Stefan 定律 ✓ 微分形式）
     *   =&gt;  H = sqrt( 2 * k * FDD / ( rho * L ) )
     * </pre>
     *
     * <p>出处 ✓：JGR 2008（DOI 10.1029/2008JC005223）给出微分形式 ✓；
     * 度日—冰厚关系见 **Anderson (1961)**, J. Glaciology **3**, 1170-1172 ✓
     * （DOI 10.3189/S0022143000017676 ✓）与 **Maykut (1986)**, The Geophysics of Sea Ice,
     * p 395-463 ✓（DOI 10.1007/978-1-4899-5352-0_6 ✓）。
     *
     * <p>⚠ Stefan 解的假设（Taylor &amp; Francis 1993 逐字 ✓）：「no thermal inertia ｜
     * no internal heat source ｜ a known temperature」⟹ 本模型的**逐点诊断**正合适 ✓。
     */
    public static double iceThicknessM(double latRad) {
        double fdd = fddK(latRad);
        if (fdd <= 0.0) return 0.0;
        return Math.sqrt(2.0 * K_ICE * fdd / (RHO_ICE * L_FUSION));
    }

    /**
     * ★★★★★ **海冰反照率**（单带 / broadband ✓）—— CCSM3 的结构 ✓。
     *
     * <pre>
     *   f_h   = min( atan(C_FH * h_i) / atan(C_FH * H_ICE_REF), 1 )    （UCAR 484 方程 6 ✓）
     *   a_ice = ALB_SEA + ( a_ice_cold - ALB_SEA ) * f_h              （薄冰趋海洋值 ✓）
     * </pre>
     *
     * <p>{@code a_ice_cold} 由**带值**按 {@link #PART_VIS} 合成 ✓：
     * {@code PART_VIS*ALB_ICE_VIS + (1-PART_VIS)*ALB_ICE_NIR} ✓；
     * 融化时两个带**同时**降 {@link #ALB_ICE_DECLINE}·DTs ✓（UCAR 484 方程 7 ✓）。
     *
     * <p>⚠ UCAR 484 逐字：「**no explicit pond fraction or depth, and no explicit pond albedo**」
     * ⟹ 融化池在本方案里是**隐式**的 ✓（与 CICE p28 一致 ✓）—— 本实现照此 ✓。
     *
     * <p>★ 验证点 ✓：DTs = 1 K 时，文档给的 broadband 冰反照率 = **0.515** ✓
     * （UCAR 484 逐字：「The spectral albedos at 0 degC are 0.655, 0.255(x1) …
     * with the broadband albedo at 65%/35% partition of 0.515(x1)」✓）。
     */
    public static double seaIceAlbedo(double latRad, double tSurfK) {
        double hi = iceThicknessM(latRad);
        if (hi <= 0.0) return ALB_SEA;                    // 无冰 ⟹ 海洋值 ✓（= 旧行为 ✓）
        double x = C_FH * hi, xr = C_FH * H_ICE_REF;
        double fh = Math.atan(x) / Math.atan(xr);
        if (fh > 1.0) fh = 1.0;
        double ds = meltDeltaK(tSurfK);
        double vis = ALB_ICE_VIS - ALB_ICE_DECLINE * ds;
        double nir = ALB_ICE_NIR - ALB_ICE_DECLINE * ds;
        if (vis < ALB_SEA) vis = ALB_SEA;
        if (nir < ALB_SEA) nir = ALB_SEA;
        double cold = PART_VIS * vis + (1.0 - PART_VIS) * nir;
        return ALB_SEA + (cold - ALB_SEA) * fh;
    }

    /**
     * **雪的单带反照率**（✓ 温度连续 ✓）—— UCAR 484 方程 (3) 的 broadband 版 ✓。
     *
     * <p>UCAR 484 逐字：「DTs = 0, Ts &lt; -1 degC ｜ = Ts + 1, Ts &gt; -1 degC」✓；
     * 可见带每 K 降 **0.10**、近红外降 **0.15** ✓ ⟹ 按 {@link #PART_VIS} 合成后，
     * 单带端点即文档给的 **0.86（冷）/ 0.745（0 degC）** ✓。
     */
    public static double snowBroadband(double tSurfK) {
        double ds = meltDeltaK(tSurfK);
        if (ds <= 0.0) return ALB_SNOW_BB_COLD;
        if (ds >= 1.0) return ALB_SNOW_BB_MELT;
        return ALB_SNOW_BB_COLD + (ALB_SNOW_BB_MELT - ALB_SNOW_BB_COLD) * ds;
    }

    /** 体块通量系数 {@code rho_a * C_D(kappa) * |V|}（W/(m^2 K) 或 kg/(m^2 s) 视用途）。 */
    public static double bulkCoeff(double kappa, double windSpeed) {
        return Atmosphere.RHO_AIR * Atmosphere.cdOf(kappa) * windSpeed;
    }

    /**
     * ★★★★★★★ §7432：**逐层灰体长波的工作列**（ThreadLocal 复用，避免每次 `new` 的 60 层分配）。
     *
     * <p><b>为什么加它</b>：{@link #residual} 原来用 `EPS*sigma*Ts^4`（{@link #EPS}），
     * 那是【TOA 有效发射率】，用它当【地表净长波损失】会**高估 1.8 倍**
     * （实测：`EPS*sigma*300.2^4 = 280.9` 而逐层的净地表长波 = `sigma*Ts^4 - lwSfcDown` = **156.4** W/m^2）。
     * 后果：`Ts` 被解**偏低** ⇒ `qSens = chv*CP*(Ts-Ta)` 偏负。
     *
     * <p><b>用的形式</b>：{@link VerticalColumn} 的逐层灰体双流（与 Isca Frierson 同方案，
     * `TAU0_LW = 6.0` ⇔ Isca 的 `ir_tau_eq = 6.0`；且本仓的光学厚度用**质量路径**
     * `tau = OLR_K*W(p)` 而非 Isca 的气压四次式 ⇒ **有真实水汽反馈**）。
     *
     * <p><b>已核对（§7431/§7432）</b>：
     * <ol>
     *   <li>`wind` **不进长波**（只用在地表通量）⇒ 传 0.0；</li>
     *   <li>`cosZenith` **只影响短波** ⇒ 传 0.0；</li>
     *   <li>{@link VerticalColumn#THETA_SB} 与 {@link #SIGMA} **逐位相同**；</li>
     *   <li>**单调性保持**（P1142：4 个 case 全部 18/18 无违例）⇒ `skinTempLand` 的二分前提不破；</li>
     *   <li>开销 **+280.9 us/点 = `mmPerDay` 的 0.8%**（P1141）。</li>
     * </ol>
     */
    private static final ThreadLocal<VerticalColumn> TL_VC =
        ThreadLocal.withInitial(VerticalColumn::new);

    /** 表面能量平衡残差（W/m^2），`beta = 1`（饱和表面）。**对 T_s 单调递减** ⇒ 二分法唯一根。 */
    public static double residual(double ts, double absSolar, double ta, double qa, double chv) {
        return residual(ts, absSolar, ta, qa, chv, 1.0);
    }

    /**
     * 表面能量平衡残差（W/m^2），带**地表湿润度 `beta`**（§387 定案）。
     *
     * <pre>
     *   E = beta * rho*C_D*|V| * (q_sat(Ts) - q_a)      <- Manabe：beta 乘在【通量】上
     * </pre>
     * **`beta` 才是「沙漠的干」的正确表达** —— 不是低 `q_a`。
     * （§387 的 `P569` 实测：降低 `q_a` 反而【增大】蒸发 ⇒ `H` 更负。因为干燥空气吹过湿表面蒸发更多。）
     * `beta -> 0` ⇒ 没有潜热冷却 ⇒ 皮温升高 ⇒ **`H` 翻正**（`P570` 实测在 `beta ~ 0.3` 处翻正）。
     */
    public static double residual(double ts, double absSolar, double ta, double qa, double chv, double beta) {
        // ★★★★★★★ §7432：长波改用【逐层灰体】（VerticalColumn），替代原来的单点 EPS*SIGMA*ts^4。
        //   物理：地表能量平衡里的长波项是【净】损失 = sigma*Ts^4 - L_down，
        //        而 L_down（大气向下长波）必须由【大气廓线的辐射传输】给出。
        //   原式 EPS*SIGMA*ts^4 用的是 EPS = 238.5/(sigma*288.15^4)，那是【TOA 有效发射率】，
        //   拿它当地表净长波会【高估 1.8 倍】（实测 280.9 vs 逐层的 156.4 W/m^2 @ ts=300.2）
        //   ⇒ Ts 被解偏低 ⇒ qSens = chv*CP*(Ts-Ta) 偏负。见 §7405/§7432。
        //   已核对：wind / cosZenith 都不进长波（故传 0.0）；单调性保持（P1142）；
        //          开销 +280.9 us/点 = mmPerDay 的 0.8%（P1141）。
        VerticalColumn vc = TL_VC.get();
        vc.init(ts, qa, beta, 0.0);
        vc.radiation(0.0, 0.0);
        double lwNet = SIGMA * ts * ts * ts * ts - vc.lwSfcDown;
        double h = chv * CP * (ts - ta);
        double le = beta * chv * LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
        return absSolar - lwNet - h - le;
    }

    /** 解陆地皮温（K），`beta = 1`。二分，60 次足够 double 收敛。 */
    public static double skinTempLand(double absSolar, double ta, double qa, double chv) {
        return skinTempLand(absSolar, ta, qa, chv, 1.0);
    }

    /** 解陆地皮温（K），带地表湿润度 `beta`。上界抬到 360 K（干沙漠皮温会接近 320 K）。 */
    public static double skinTempLand(double absSolar, double ta, double qa, double chv, double beta) {
        double lo = 180.0, hi = 360.0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (residual(mid, absSolar, ta, qa, chv, beta) > 0.0) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * ★ 桶开关（§387/§354）：用【稳态互补解】给出 `beta`，而不是全场 `beta = 1`。
     *
     * <pre>
     *   E_p  = beta=1 时的潜在蒸发（Manabe eq.19）
     *   E    = min(E_p, P)                <- 稳态互补（dW/dt = 0）
     *   W    = W_K * min(1, P/E_p)        <- W_K = 0.75 * W_FC  （§354 对教科书的更正）
     *   beta = E/E_p = min(1, P/E_p)
     * </pre>
     * **默认 false ⇒ 逐位不变。**
     */
    public static boolean BUCKET_BETA = false;

    /** Manabe eq.20 的 `W_K / W_FC`。教科书写 1.0 是**错的**（§354 从原文 PDF 核出）。 */
    public static final double WK_OVER_WFC = 0.75;

    /** 稳态互补解给出的地表湿润度 `beta = min(1, P/E_p)`（`E_p` 与 `P` 同单位即可）。 */
    public static double bucketBeta(double p, double ep) {
        if (ep <= 1.0e-12) return 1.0;
        double b = p / ep;
        return b < 0.0 ? 0.0 : (b > 1.0 ? 1.0 : b);
    }

    /** 潜在蒸发（mm/day）：Manabe eq.19 在【解出的皮温】上求值 —— 这就是「能量限制」。 */
    public static double potentialEvapMmDay(double ts, double qa, double chv) {
        return chv * Math.max(0.0, PrecipField.qSat(ts) - qa) / PrecipField.RHO_WATER * 86400.0 * 1000.0;
    }
}
