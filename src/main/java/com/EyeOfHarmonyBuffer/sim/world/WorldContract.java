package com.EyeOfHarmonyBuffer.sim.world;

/**
 * 世界契约（决定 1 = B）的**唯一真相来源**。
 *
 * <pre>
 *   X      无限、无周期
 *   Z      无限
 *   气候   在 Z 上以 Z_CYCLE 周期重复
 *   地形   在 Z 上不重复
 * </pre>
 *
 * <p>纬度用**带符号的 4 段线性三角波**（裁决项 D1 已落地）。z=0 赤道 → MAX_D 北极 → 2·MAX_D 赤道
 * → 3·MAX_D 南极 → 4·MAX_D 赤道；两个极点已真正分开，见 {@link #latOf(int)} 与下方 Z_CYCLE 条目。
 */
public final class WorldContract {

    private WorldContract() {}

    /**
     * ★★★★★★★ **2026-10-21（用户裁决）：全行星缩放因子 —— 单一来源。**
     * <pre>
     *   PLANET_SCALE = 1  ：地球量级（当前；R=6366 km、Ω=地球、MAX_D=10,000,000）
     *   PLANET_SCALE = 40 ：旧的小世界（R=159 km、Ω×40、MAX_D=250,000）
     * </pre>
     * <p>所有尺度常量都由它派生（MAX_D/Z_CYCLE/OMEGA/R_EFF/COAST_WIND_W/SOIL_GRAD_STEP/
     * UPWIND_STEP/SOURCE_FETCH_MAX）；**禁止再出现“半套缩放”**（R 去了 Ω 没去）。
     * <p>⚠ 地形特征尺度（{@code TalosLandField.SC}）也要跟它走，见该字段注释。
     */
    /**
     * ★★★★★★★ 永久定为 1.0（用户裁决 2026-10-23）：世界就是地球尺度。
     *
     * <p>下面的「×40 / 小世界」文字全部是**历史记录**（旧 Talos 是小行星）。
     * 现在 40 只作为**论文的消融实验开关**保留：改这一个常数即可复现旧小世界
     * （MAX_D=250,000、Ω×40、R_EFF=159 km），用来对照「行星缩 40 倍时物理怎么崩」。
     */
    public static final double PLANET_SCALE = 1.0;

    /**
     * <b>极到赤道的弧长</b>（block；1 格 = 1 米锁死）—— **本文件的基本尺度**。
     *
     * <p>R_EFF、beta、风带宽度、沿岸上升流层全部由它派生，**与气候在 z 上的周期无关**。
     * <p>2026-09-18 口径解耦（D1 第一步）：原来是 {@code Z_CYCLE / 2} 的派生量，
     * 现在独立成常量，**值不变（10,000,000）⇒ 行为逐位不变**。
     */
    // ★★★★★★★★ 2026-10-08：全体缩小 40×（与地形层 SC=1/40 一致）
    //   裁决：地形缩了 40 倍（SC=0.00625），行星契约同步缩 40 倍 ⟹ **所有尺度比保持**。
    //   配套：OMEGA ×40（必须！否则大气变形半径 > 行星半径 ⟹ 气候层塌掉）
    public static final int MAX_D = (int) Math.round(10_000_000 / PLANET_SCALE);   // ★ 地球量级 = 10,000,000；旧小世界 = 250,000

    /**
     * 气候在 z 上的周期（block）。
     *
     * <p>⚠⚠ <b>2026-10-14 审查补注（阶段 4 · 过期数字猎捕）：以下整段是【历史记录】</b> ✗ ——
     * 它记录的是 Z_CYCLE 扩到 20,000,000 那一次裁决（设计冻结 §29 ✓）的论证，
     * 而世界后来<b>整体缩放 40 倍</b>（见 {@link #MAX_D} 的「原 10,000,000 ÷ 40」✓）。
     * <b>当前值以下面的字段声明行为准</b>：
     * <pre>
     *   MAX_D   = **250,000**      （= 250 km 极到赤道，不是下面论证里的 10,000 km ✗）
     *   Z_CYCLE = **4 * MAX_D** = **1,000,000**  （★ D1 极点分离【已经落地】✓，
     *                                              不再是下面 "届时改成" 的语气 ✗）
     * </pre>
     *
     * <p><b>（历史）裁决：1,000,000 -> 20,000,000</b>（设计冻结 §29）。理由：
     * <ul>
     *   <li>极到赤道 = MAX_D = 10,000 km ⇒ 行星半径 6,366 km，**与地球 6,371 km 差 0.1%**；</li>
     *   <li>⚠ **本条论证已随 §77 失效**：沿岸累积闭合已退役，现行 T_E = g'H1*tau*T_up/(rho*f^2*L_x) ∝ 1/f²，与 L_along 无关；</li>
     *   <li>风带宽度/L_R 从 0.20 升到 4.07 ⇒ **斜压不稳定（风暴轴）从「不存在」变成「与地球同级」**；</li>
     *   <li>**西边界流完全不受影响**：curl ∝ 1/MAX_D 且 beta ∝ 1/MAX_D ⇒ curl/beta 不变。</li>
     * </ul>
     * 唯一代价是玩家从赤道走到极点要 <b>250 km</b>（1 格 ≈ 1 米是锁死的；⚠ 论证当时写的是
     * "10,000 km" ✗，那是缩放 40 倍【之前】的值）。
     *
     * <p>⚠ <b>2026-09-18（D1 第一步）</b>：本常量当时改成 {@code 2 * MAX_D}
     * （在当时的尺度下值仍是 20,000,000 ✓）。
     * <b>★ D1（极点分离）现已落地</b> ✓ —— 见下面字段声明行的 {@code 4 * MAX_D}：
     * 极点在 {@code Z_CYCLE / 4}、赤道在 {@code 0} 与 {@code 2 * MAX_D} ✓。
     * （⚠ 原文用"落地时改成…届时…"的<b>将来时</b> ✗，已与代码不符 ✓。）
     */
    public static final int Z_CYCLE = 4 * MAX_D;   // ★ 2026-10-08：= 1,000,000（原 40,000,000 ÷ 40）
    // ★★★★★★★★ 2026-10-08：Ω ×40（**必须**）
    //   依据：大气/海洋的变形半径 ∝ √(gH)/Ω。地形与 R_EFF 缩了 40 倍，
    //   若 Ω 不变，则 R_d/R_EFF 会 ×40（实测 0.337 -> 13.494）⟹ 罗斯贝波/哈得来环流全塌。
    //   Ω ×40 后 R_d/R_EFF 精确保持（实测 0.337 -> 0.337），Rhines 比也保持（0.104）。
    //   代价：日长 23.93 h -> 35.9 分钟（MC 的昼夜由游戏自己管，不受影响）。
    public static final double OMEGA = 7.2921e-5 * PLANET_SCALE;   // 地球 = 7.2921e-5；旧小世界 = ×40
    /** 有效行星半径（m）：R_eff = MAX_D/(pi/2) = 6366 km —— 与地球 6371 km 差 0.1%。 */
    public static final double R_EFF = MAX_D / (Math.PI / 2.0);   // ★ 自动 = 159,154.9 m（原 6,366,197.7 ÷ 40）
    /** 一年多少「天」（季节相位的时间单位，纯约定）。 */
    // ★ 2026-10-08：一年 = 365.25「新日」；因日长缩了 40 倍，一「年」也缩 40 倍。
    //   物理时间尺度：一年 = 365.25 × 35.9 min = 9.09 天（原 365.25 天）
    public static final double DAYS_PER_YEAR = 365.25;   // ★ 保持（它是「天」的计数，不是物理时间）

    // ================================================================================
    // ★★★★★ 2026-10-19（用户裁决）：**本世界大气 = 地球型**。
    //   Talos2 本质上是一颗类地行星（有自己的气候生态链、与地球相差不大）⟹
    //   以下数值是【世界设定输入】，不是"物理公式"，也不再是隐藏的地球锚。
    //   ★ 单一来源：消费者必须引用这里，不得再复制新副本。
    //   ⚠ 审计（2026-10-19）：这些量此前散落在 PrecipField / Atmosphere / VerticalColumn，
    //     值都等于下面这组；本块是它们的正式登记。与"地球拟合值"的区别：
    //     · 气体物性（R_d / CP / L_v / ε=0.622）是**空气的属性**，随成分变；
    //     · A_OLR/B_OLR、Radiation.EPS=238.5/(σ·288.15⁴)、D_DIFF、TF_C=-10°C 仍是
    //       **待替换的地球拟合值**（不属于本块；见目标"本世界纬向气候求解"）。
    // ================================================================================
    /** 地面气压（Pa）—— 世界设定。 */
    public static final double ATMO_P_SURF = 101300.0;
    /** 海面相对湿度 —— 世界设定。 */
    public static final double ATMO_RH_SEA = 0.80;
    /** 海平面空气密度（kg/m^3）—— 世界设定。 */
    public static final double ATMO_RHO_AIR = 1.225;
    /** 对流层等效厚度（m）—— 世界设定（气压异常的垂直尺度）。 */
    public static final double ATMO_H_EFF = 12_000.0;
    /** 边界层厚度（m）—— 世界设定（湍流/浅对流尺度）。 */
    public static final double ATMO_H_BL = 1000.0;
    /** 海面拖曳系数 —— 世界设定。 */
    public static final double ATMO_CD_OCN = 1.3e-3;
    /** 陆面拖曳系数 —— 世界设定。 */
    public static final double ATMO_CD_LND = 3.0e-3;
    /** 参考温度（K）—— 世界设定（大气密度/气压的参考态）。 */
    public static final double ATMO_T0 = 288.15;
    /** 长波光学厚度 —— 世界设定（地球型大气的温室强度）。 */
    public static final double ATMO_TAU_LW = 6.0;

    // ==================== 2026-10-25 Phase 0：世界参数单一登记 ====================
    // 说明：这里登记的是【世界定义输入】或【明确命名的模型口径】。Phase 1 会决定
    // S0/GRAVITY 的双口径如何统一；Phase 0 只保证不再有散落在各文件的字面量副本。

    /** 辐射/地表能量平衡使用的太阳常数（W/m^2）：现代 TSI 标准 1361.0。 */
    public static final double S0_TSI = 1361.0;
    /** EBM 使用的 climlab 太阳常数（W/m^2）：1365.2；与 S0_TSI 的差异待 Phase 1 裁决。 */
    public static final double S0_EBM = 1365.2;

    /** 标准重力（m/s^2）：9.80665。 */
    public static final double GRAVITY = 9.80665;
    /** 降水链旧口径重力（m/s^2）：9.807；Phase 1 统一前保留，避免静默改数。 */
    public static final double GRAVITY_ACCEL = 9.807;

    /** 黄赤交角（弧度）：23.44°；本世界轴向倾角。 */
    public static final double OBLIQUITY = Math.toRadians(23.44);

    /** 干空气定压比热（J/(kg K)）。 */
    public static final double ATMO_CP = 1004.0;
    /** 水汽化潜热（J/kg）。 */
    public static final double ATMO_LV = 2.45e6;
    /** 干空气气体常数（J/(kg K)）。 */
    public static final double ATMO_R_D = 287.05;
    /** 水汽气体常数（J/(kg K)）。 */
    public static final double ATMO_R_V = 461.5;
    /** 水汽/干空气分子量比。 */
    public static final double ATMO_EPS = 0.622;
    /** 水的三相点（K）。 */
    public static final double WATER_T_TRIPLE = 273.16;

    /**
     * ★ D1（2026-09-18）：**带符号**纬度（弧度）—— 沿子午圈匀速行走的**线性三角波**。
     * <p>z=0 赤道 → MAX_D 北极(+90°) → 2·MAX_D 赤道 → 3·MAX_D 南极(-90°) → 4·MAX_D 赤道。
     * <p>1 格 = 1 米不变（每 MAX_D 格走 90 度）。**全程连续且周期** ⇒ sin(lat) 连续 ⇒ f 不再跳号。
     */
    /**
     * ★ §527 新增：**z 的周期回卷 —— 单一来源**。
     *
     * <p>为什么要有它：契约下气候在 Z 上以 {@code zCycle} 周期重复，所以「把 z 弄回一个周期内」
     * 只能【回卷】，不能【夹逼】。历史上有两处用了 {@code clamp(z, 0, MAX_D)}，
     * 那只保留北半球：南半球点（z 约 3*MAX_D）会被夹到 **MAX_D = 北极**，
     * 而不是回到它自己的纬度。见 {@code PrecipField.upwindSea} / {@code moistureAdvected}。
     *
     * <p>整数版与 {@link #latOf} 内部【逐字一致】；连续版供沿风逆推这类浮点游走使用。
     */
    public static int wrapZ(int z, int zCycle) {
        return ((z % zCycle) + zCycle) % zCycle;
    }

    /** 连续 z 的周期回卷（与 {@link #wrapZ(int,int)} 同口径）。 */
    public static double wrapZ(double z, int zCycle) {
        double w = z % zCycle;
        return w < 0.0 ? w + zCycle : w;
    }

    public static double latOf(int z, int zCycle) {
        int quarter = zCycle / 4;
        int u = wrapZ(z, zCycle);
        double q = (double) u / quarter;
        double t;
        if (q < 1.0) t = q;
        else if (q < 3.0) t = 2.0 - q;
        else t = q - 4.0;
        return t * Math.PI / 2.0;
    }

    /** bandD = |纬度| / 90°：0 在**两条赤道**（z=0 与 2·MAX_D）、1 在**两个极点**。 */
    public static double bandD(int z, int zCycle) {
        return Math.abs(latOf(z, zCycle)) / (Math.PI / 2.0);
    }

    /** 半球符号（D1 后由纬度决定；两条赤道上取 +1）。 */
    public static double hemisphereSign(int z, int zCycle) {
        return latOf(z, zCycle) >= 0 ? 1.0 : -1.0;
    }

    /**
     * beta(phi) = 2*Omega*cos(phi)/R。
     * <p>★ D1：整条子午圈 = 4·MAX_D = 2πR ⇒ **R = zCycle/(2π)** ⇒ 系数是 {@code 2π/zCycle}。
     * <p>⚠ 改 Z_CYCLE 时必须同时改这一行；漏改会把 beta 砍半，直接歪掉西边界流。
     * **绝不要再用常数 3.24e-10（那是 45 度的值）。**
     */
    public static double betaForLatitude(double latRad, int zCycle) {
        return 2.0 * OMEGA * (2.0 * Math.PI) / zCycle * Math.cos(latRad);
    }

    /**
     * ★★★★★★★★ <b>2026-10-15 审计：海洋专用的自转速率 = **地球真值**</b>（不做 x40 ✓）。
     *
     * <h3>为什么需要它（本文是那串调查的落点 ✓）</h3>
     * <p>起因：P292 的 A3 方向门 FAIL（38% vs 判据 80%）。追查链条（待裁决/399~404）：
     * <ol>
     *   <li>东带速度量级 = 1e-5 ~ 1e-4 m/s，比观测（0.2~0.5 m/s）**弱 4 个数量级** ✗</li>
     *   <li>不是探针 artifact：直接调 {@code GyreRow.solve}（生产口径）量到同一量级 ✓</li>
     *   <li>参数自洽（aH/rhoH/h 与世界尺度同量级 ✓，delta_M/L 与地球相当 ✓）</li>
     *   <li>★ 真因：{@code beta = 2*Omega*(2*pi/zCycle)*cos(phi)} 的**两个因子同向叠加**：
     *       {@code OMEGA} 被 x40（:67，大气必需 ✓），而几何 {@code zCycle = 4*MAX_D} 缩到 1/40
     *       ⟹ **beta 被放大 40x40 = 1600 倍** ✗
     *       ⟹ Sverdrup {@code V = curl(tau)/(rho0*beta)} ⟹ 风生环流被系统性压弱 ✗</li>
     *   <li>定量（待裁决/404 的扫描 ✓，玩具三带风 + 手动 beta）：
     *       <pre>
     *         beta x1     + tau0 x1  ⟹ vPeak 4.5e-4（观测的 0.09%）
     *         beta /40    + tau0 x1  ⟹ vPeak 5.0e-3（1.0%）
     *         beta /40    + tau0 x40 ⟹ vPeak **0.199**（**39.9%**）✓ 落在观测 0.2~0.5 内 ✓
     *         beta /1600  + tau0 x40 ⟹ vPeak 1.18（236%，过头 ✗）
     *       </pre>
     *       ⟹ 需要 **beta /40** 那一档 ✓（tau0 那半由风的经向尺度自动给出，见下 ✓）</li>
     * </ol>
     *
     * <h3>⚠ 为什么不直接改 OMEGA</h3>
     * <p>因为 {@code WorldContract:29} 有明文：「OMEGA x40（**必须！**否则大气变形半径 > 行星半径
     * ⟹ **气候层塌掉**）」✗ ⟹ 动 OMEGA 会**打塌大气** ✗。
     * <p>⟹ 本常量是**工程妥协**：<b>大气继续感受 x40，海洋单独感受地球自转</b> ✓。
     * 它**物理上不自洽**（同一个世界两套自转）✗ —— 但它是**唯一同时让两边都活**的做法 ✓
     * （待裁决/404 列了四个方案，(i) 解耦是唯一可行的 ✓）。
     *
     * <p><b>用它的地方</b>：{@link #betaForLatitudeOcean} ⟹ 被 {@code GyreRow.betaForLatitude} 引用
     * ⟹ 只影响**风生环流**（Munk 行求解）✓；大气、辐射、Hadley 等**一律仍用 {@link #OMEGA}** ✓。
     */
    public static final double OMEGA_OCEAN = OMEGA;   // 单一来源：海洋与大气同自转（旧实现曾用 7.2921e-5 硬编码）

    /**
     * 海洋口径的 beta(phi) = 2*Omega_ocean*cos(phi)/R（R = zCycle/(2*pi) ✓）。
     *
     * <p>★ 与 {@link #betaForLatitude} 的唯一区别是**用 {@link #OMEGA_OCEAN}** 而不是 {@link #OMEGA} ✓。
     * <p>⟹ 相对旧值恰好缩小 40 倍 ✓（那正是 404 扫描里 vPeak 从 4.5e-4 升到 5.0e-3 的那一档 ✓）。
     * <p>⚠ 其余一切（{@code zCycle} 的几何、cos 的纬度依赖）**完全不变** ✓。
     */
    public static double betaForLatitudeOcean(double latRad, int zCycle) {
        return 2.0 * OMEGA_OCEAN * (2.0 * Math.PI) / zCycle * Math.cos(latRad);
    }

    /**
     * 纬度（度）→ z（block）。**探针不许再手写 {@code lat/90 * Z_CYCLE/2}**（D1 后会静默错位）。
     * <p>北纬走第一支 [0, MAX_D]，南纬走第三支 [2·MAX_D, 3·MAX_D]。
     */
    public static int zOfLat(double latDeg) {
        double b = Math.abs(latDeg) / 90.0 * MAX_D;
        return latDeg >= 0 ? (int) Math.round(b) : (int) Math.round(2.0 * MAX_D + b);
    }

    /** 科氏参数 f = 2*Omega*sin(phi)。 */
    public static double coriolis(double latRad) {
        return 2.0 * OMEGA * Math.sin(latRad);
    }

    // ==================================================================================
    // ★★ D1（极点分离）—— **已于 2026-09-18 落地**；下面只剩命名别名与自检
    //
    // 用户裁决（2026-09-18）：「分离，并且继续保持行星尺度」。
    //
    // 几何：球面上一条子午圈本身就**是一个圆**，周长 40,000 km（北极→赤道→南极→赤道→北极）。
    //   所以要让 z 忠实等于一条子午圈，周期必须是 4 * MAX_D。
    //   现行的 2 * MAX_D 只够走「赤道→北极」+「南极→赤道」各 10,000 km，
    //   中间那 20,000 km 的「北极→南极」被压成了 0 ⇒ 极点在 z = MAX_D 处**焊死**，
    //   于是 sin(纬度) 在那里从 +1 硬跳到 -1 ⇒ 科氏参数 f 从 +2Ω 瞬间翻到 -2Ω
    //   （OceanField 逐行取 f 做环流，那里就是非物理的）。
    //
    // 为什么「相位平移」不算：把不连续点从 z=MAX_D 搬到 z=0 只是换位置。
    //   圆柱的 z 是一个圆，纬度要覆盖 -90…+90 ⇒ **必然有一对纬度被等同**。
    //   要让它落在「两条赤道」上，就需要 40,000 km。
    //   ⇒ **只有 4 * MAX_D 能让纬度全程连续且周期，从而让 sin(lat) 连续。**
    //
    // 落地清单（D1 第二步）：
    //   1) Z_CYCLE = 4 * MAX_D
    //   2) latOf -> latOfD1，bandD -> bandDD1，hemisphereSign 退役
    //   3) betaForLatitude 的 (pi / zCycle) -> (2 * pi / zCycle)   ← 两行必须一起改
    //   4) 审计所有把 Z_CYCLE / 2 当「极点」用的地方（会静默错位）
    //   R_EFF 与全部 MAX_D 派生式**一个字都不用改**（这正是先解耦 MAX_D 的目的）。
    // ==================================================================================

    // D1 的周期：一条完整子午圈 = 4 ×（极到赤道）。
    // 原别名 Z_CYCLE_D1 已于 §7199 合并入 Z_CYCLE（用户裁决 D-5；零外部读者，本类 3 处自显示改用正名）。

    /**
     * D1 的纬度（弧度）：沿子午圈**匀速**行走的线性三角波。
     * <p>z=0 赤道 → MAX_D 北极(+90°) → 2·MAX_D 赤道 → 3·MAX_D 南极(-90°) → 4·MAX_D 赤道。
     * <p>1 格 = 1 米不变（每 MAX_D 格走 90 度）。
     */
    public static double latOfD1(int z, int zCycle) { return latOf(z, zCycle); }

    /** D1 的 bandD = |纬度| / 90°：0 在两条赤道、1 在两个极点。 */
    public static double bandDD1(int z, int zCycle) { return bandD(z, zCycle); }

    /** D1 的科氏参数 f = 2Ω·sin(纬度) —— 公式不变，但纬度连续 ⇒ f 连续。 */
    public static double coriolisD1(int z, int zCycle) { return coriolis(latOf(z, zCycle)); }

    /**
     * D1 的 beta：整条子午圈 = 2πR ⇒ R = zCycle / (2π)，故 beta = 2Ω·cos(φ)/R。
     * <p>⚠ 现行版本是 {@code 2Ω·π/zCycle}（因为现行 R = zCycle/π）。
     * **这两行必须与 Z_CYCLE 一起改；改错直接歪掉西边界流。**
     */
    public static double betaForLatitudeD1(double latRad, int zCycle) { return betaForLatitude(latRad, zCycle); }

    /** D1 自检：逐条打印结论。**任何一条 FAIL 都不许落地。** */
    public static String d1SelfCheck() {
        java.util.Locale L = java.util.Locale.ROOT;
        StringBuilder b = new StringBuilder();
        int zc = Z_CYCLE;
        b.append(String.format(L, "  Z_CYCLE_D1 = %d  ( = 4 x MAX_D )%n", zc));
        double rD1 = zc / (2.0 * Math.PI);
        b.append(String.format(L, "  R_EFF(现行) = %.3f km    R = Z_CYCLE_D1/(2*pi) = %.3f km    差 = %.3f km%n",
            R_EFF / 1000.0, rD1 / 1000.0, Math.abs(R_EFF - rD1) / 1000.0));
        boolean ok = true;
        int[] zs = {0, MAX_D, 2 * MAX_D, 3 * MAX_D, 4 * MAX_D};
        int[] ex = {0, 90, 0, -90, 0};
        for (int i = 0; i < zs.length; i++) {
            double got = Math.toDegrees(latOfD1(zs[i], zc));
            boolean e = Math.abs(got - ex[i]) < 1e-9;
            ok = ok && e;
            b.append(String.format(L, "  lat(z=%9d) = %+9.5f deg    期望 %+4d    %s%n", zs[i], got, ex[i], e ? "OK" : "**FAIL**"));
        }
        double mx = 0; int at = 0;
        for (int z = -zc; z <= 2 * zc; z += 997) {
            double d = Math.abs(Math.sin(latOfD1(z + 1, zc)) - Math.sin(latOfD1(z, zc)));
            if (d > mx) { mx = d; at = z; }
        }
        b.append(String.format(L, "  D1   |delta sin(lat)| 最大单格步进 = %.8f (z=%d)   %s%n", mx, at, mx < 1e-3 ? "连续 OK" : "**有跳变 FAIL**"));
        ok = ok && mx < 1e-3;
        double jump = Math.abs(Math.sin(latOf(MAX_D - 1)) - Math.sin(latOf(MAX_D + 1)));
        b.append(String.format(L, "  D1 落地后 |delta sin(lat)| 跨 z=MAX_D(+-1 格) = %.6f   (应 ~0)%n", jump));
        ok = ok && jump < 1e-3;
        b.append(String.format(L, "  总判定 = %s%n", ok ? "PASS" : "**FAIL**"));
        return b.toString();
    }

    // ---- 默认契约的便捷重载 ----
    public static double bandD(int z) { return bandD(z, Z_CYCLE); }
    public static double latOf(int z) { return latOf(z, Z_CYCLE); }
    public static double betaForLatitude(double latRad) { return betaForLatitude(latRad, Z_CYCLE); }
}
