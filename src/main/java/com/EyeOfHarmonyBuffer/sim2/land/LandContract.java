package com.EyeOfHarmonyBuffer.sim2.land;

/**
 * 新海陆分布 v3 —— 高度尺度契约（唯一来源）。
 *
 * <p>设计文档：{@code K:\moder\Talos2\新海陆分布系统-设计v3.md}
 *
 * <p><b>为什么要有这个类</b>：旧海陆层把 {@code 90,000 / 60,000 / 22,000} 这类
 * 绝对长度写死在十几个地方，缩放到 PLANET_SCALE=1 时只改了一半，导致
 * 「同一套海岸影响」在代码里同时存着 5 km 到 400 km 两个尺度。
 * 本类把所有陆地长度收进 {@link #L}（板块格距）的倍数，禁止再写绝对值。
 *
 * <p><b>尺度</b>：PLANET_SCALE = 1，1 格 = 1 m，R = 6,366 km。
 * 极到赤道 10,000 km，赤道周长 40,000 km。
 *
 * <p><b>物理骨架</b>：地壳厚度 → Airy 均衡高度 → 判陆 / 高度 / 距离 / kappa / 山地门。
 * 一个标量场同时定五件事，构造上消灭「三条读法」。
 *
 * <p>本包是**全新一套**：不 import 任何旧海陆类
 * （{@code TalosLandField / NoiseContinentGrid / OrographyField / LandformField}）。
 */
public final class LandContract {

    private LandContract() {}

    /**
     * 契约版本：任何会改变输出的改动都必须 +1（进 configStamp）。
     * <p>v2（2026-10-21）：造山带改为「远场传递 + 褶皱列车」。
     * <p>v3（2026-10-21）：接缝连续性——τ 成对化、接缝混合、深海热沉降连续化。
     * <p>v4（2026-10-21）：速度场按地球相对速度分布标定（中位 50 mm/yr），夹矢量不夹分量。
     * <p>v5（2026-10-21）：大陆场改「克拉通核 + 脊化活动带」；大陆场在板块参考系取样；
     * 海岸线域扭曲；俯冲极性（单侧海沟 + 上覆侧岛弧）。
     * <p>v8（2026-10-21）：陆/洋分支连续化（COAST_GATE）——海岸线上 2,673 m 的崖降到 300 m/km 级。
     * <p>v9（2026-10-24）：岛弧改按【挤压 + 速度】生成（PTP 2019 g(v)=v/v0），并加沿走向分段；
     *     VERSION 现在真的折进 {@code LandV3.configStamp()}（之前没有任何地方读它）。
     * <p>v10（2026-10-24）：其余构造项加板块状态门（洋脊/海沟/碰撞）；洋底年龄与洋脊隆起
     *     改由“到离散边界的距离 / 半扩张速率”驱动（PTP 2019 §4.3 + Parsons &amp; Sclater 1977）。
     */
    public static final int VERSION = 10;

    // ================= 水平尺度 =================
    /** 板块格距基准（m）：2,500 km。{@code WARP_*}、{@code plateL()} 都按它的倍数给。 */
    public static final double L = 2_500_000.0;
    /** 种子抖动幅度（×L，±0.8L）。 */
    public static final double SEED_JITTER = 1.60;   // ±0.8L：种子不再规整，cell 才不规则
    /** 域扭曲：第一级。 */
    public static final double WARP_A1 = 0.35 * L;
    public static final double WARP_WL1 = 1.20 * L;
    /** 域扭曲：第二级。 */
    public static final double WARP_A2 = 0.25 * L;
    public static final double WARP_WL2 = 0.50 * L;
    /** 域扭曲：第三级（~450 km）：让海岸在几百 km 尺度上弯曲。 */
    public static final double WARP_A3 = 0.200 * L;
    public static final double WARP_WL3 = 0.30 * L;
    /** 域扭曲：第四级（~175 km）：海岸的细碎弯曲。 */
    public static final double WARP_A4 = 0.100 * L;
    public static final double WARP_WL4 = 0.12 * L;

    // ================= 地壳厚度（P1 世界参数） =================
    /** 陆壳厚度（m）：35 km（CRUST1.0 典型值；卷页出处待补）。 */
    public static final double T_CONT = 35_000.0;
    /** 洋壳厚度（m）：7 km。 */
    public static final double T_OCEAN = 7_000.0;
    /** 海平面基准（m）：30.7 km，陆占 33% 的定标量，不是物理常数。 */
    public static final double T_REF = 30_700.0;
    /** 均衡系数 (1 − ρc/ρm)，ρc=2800、ρm=3300（Turcotte &amp; Schubert；卷页待补）。 */
    public static final double K_ISO = 1.0 - 2800.0 / 3300.0;

    /**
     * 全球最高峰上限（米）。**用户 2026-10-24 裁决**：最高峰不超过 10,240 m 都可以存在，
     * 不再要求把碰撞峰压回 4.2 km 档。
     *
     * <p>这个数是**表现层约束**而不是物理量：MC 1.7.10 的世界高度是 256 格，
     * 生产换算 {@code hCF = 64 + ELEV_TO_BLK(0.018) · hM} ⟹ 10,240 m 对应
     * {@code 64 + 184.3 = 248.3 格}，刚好在世界顶之下；再高就会被截断。
     *
     * <p>实现为**硬夹**（{@code min(h, MAX_PEAK_M)}）：低于它的地貌逐位不变，
     * 只有超过的峰被削平——当前 B′ 实测最高 5,008 m，所以对现有世界零影响。
     */
    public static final double MAX_PEAK_M = 10_240.0;

    // ================= 海岸 / 构造 =================
    /** soft-min 温度（m）：海岸过渡 ≈ 2σ = 140 km。 */
    public static final double SIGMA = 70_000.0;
    /** 造山：最大增厚（m）与带宽（m）。 */
    public static final double A_OROG = 40_000.0;
    public static final double S_OROG = 400_000.0;
    /** 海沟：最大减薄与带宽。 */
    public static final double A_TRENCH = 8_000.0;
    public static final double S_TRENCH = 150_000.0;
    /**
     * 裂谷：地壳减薄量（m 厚度）与高斯半宽（m）。
     *
     * <p><b>出处</b>：Achauer, U., Maguire, P.K.H., Mechie, J., Green, W.V. &amp; the KRISP
     * Working Group (1992), "Some remarks on the structure and geodynamics of the Kenya Rift",
     * in Ziegler (Ed.) Geodynamics of Rifting Vol. II, <i>Tectonophysics</i> <b>213</b>: 257-268：
     * <ul>
     *   <li>纬度 0.5° 剖面：地壳从西翼 <b>40 km</b> 减薄到地堑下 <b>30 km</b>，东翼回到 35 km；
     *       北端 Turkana 湖下进一步到 <b>20 km</b>（35 → 20）。</li>
     *   <li>地堑是 <b>50-70 km 宽</b>的半地堑；到端点处构造<b>撒开到 200 km 以上</b>。</li>
     *   <li>地堑底到肩部的<b>地形落差可达 1,600 m</b>。</li>
     * </ul>
     *
     * <p>取值：<b>A_RIFT = 10,000 m</b>（40→30 km 的 10 km 减薄；K_ISO·10 km = 1,515 m，
     * 与论文 1,600 m 的肩部落差同量级）；<b>S_RIFT = 100,000 m</b>（高斯半宽，全宽约 200 km，
     * 取论文"撒开到 200 km 以上"的尺度，而非 50-70 km 的单条半地堑——世界图 22 km/px 下后者只有 3 px）。
     * <p>⚠ 这仍是纯剪切（McKenzie 1978 式）的**几何近似**：没有沉积充填、没有挠曲回弹。
     */
    public static final double A_RIFT = 10_000.0;
    public static final double S_RIFT = 100_000.0;

    // ================= 大陆性场 =================
    /** 大陆性：超大陆尺度（m），让陆块有「一大带几中」的分布。 */
    public static final double C_WL0 = 10_000_000.0;
    /** 大陆性波长（m）：6,000 km，控制大陆成簇。 */
    public static final double C_WL = 6_000_000.0;
    /** 大陆性：次级（m）。 */
    public static final double C_WL2 = 2_700_000.0;
    /** 大陆性阈值：由每种子二分定标（目标陆占 33%），默认值只作初值。 */
    public static final double CUT_INIT = 0.644;
    /** 目标陆占比。 */
    public static final double LAND_TARGET = 0.33;

    /**
     * 大陆场的【域扭曲】：直接位移取 c 的坐标，让等高线/海岸线变成折线而不是圆弧。
     *
     * <p>为什么不能用现有的"海岸带噪声"代替：`CN_*` 是加在【厚度】上的（±2,500 m ⟹ ±380 m 高程），
     * 而海岸坡度约 65 m/km ⟹ 岸线只挪 6 km。要让海岸长出 50–100 km 的锯齿，
     * 必须位移【坐标】而不是叠高度。幅度取 180 km（≈ 3% 波长），不破坏大陆整体形状。
     */
    public static final double CW_A1 = 180_000.0, CW_WL1 = 600_000.0;
    public static final double CW_A2 =  60_000.0, CW_WL2 = 200_000.0;

    /**
     * 大陆场是否走「脊化活动带」版（默认<b>开</b>）。
     *
     * <p>脊化版（`1−|2b−1|`，权重 0.55 大尺度 / 0.27 / 0.18）把等高线拉成长条，
     * 海岸线因此有自然延伸的半岛与海湾，并带着宽缓的陆架坡；
     * 采用者判定这一版的**海岸线与陆架**明显优于各向同性紧凑陆块版 ⟹ 定为默认。
     * 关掉可回到紧凑陆块（A/B 对比用）。
     */
    public static boolean CELLC_RIDGE = true;

    // ================= 细节（带限，只加在 F 上） =================
    public static final double DET1 = 3_000.0, DWL1 = 12_000_000.0;
    public static final double DET2 = 800.0, DWL2 = 800_000.0;
    public static final double DET3 = 1_200.0, DWL3 = 200_000.0;
    public static final double DET4 = 400.0, DWL4 = 80_000.0;

    // ================= 海岸方位扰动（实验，默认关） =================
    /**
     * 以「最近板块种子」为极点，按方位角 θ 扰动海岸：
     * {@code Δt = A · exp(−(F/F0)²) · Σ_{k=2..6} cos(kθ+φ_k)/k}。
     *
     * <p>这是「大陆中心点 + 每角度不同强度」的可实现版本：中心 = 板块种子（O(1) 已有），
     * 角度的 1/k 给出大尺度海湾/岬角，闸门 exp(−(F/F0)²) 保证只改海岸、不动内陆山带。
     */
    public static final double COAST_AZ_AMP = 8_000.0;
    public static final double COAST_AZ_F0  = 3_000.0;
    public static final int    COAST_AZ_KMIN = 2;
    public static final int    COAST_AZ_KMAX = 6;

    /** 实验开关：默认关，与不开时逐位相同。 */
    public static boolean COAST_AZIMUTH = false;

    // ================= 板块权重（MWV，M2a） =================
    /**
     * 乘性加权 Voronoi：{@code φ = d / w}，边界是 Apollonius 圆（天然弯曲）。
     *
     * <p>权重取重叠 {@code w = 0.5 + 2.5·u³}：中板 ~0.8、最大 3.0。
     * 面积比可达 ~36×，能同时出现太平洋级 giant 与 1,500 km 微板块。
     * 关掉时 {@code w ≡ 1} ⟹ 与均匀 Voronoi 逐位相同。
     */
    public static boolean PLATE_WEIGHTED = false;
    // 幂图（power diagram）：φ = d² − R²，边界是直线。**每个 cell 都是凸的**
    // ⟹ 从构造上杜绝「一个板块被另一个板块完全包住」的圆环。
    public static final double POWER_MIN  = 0.60;   // 小板块 R/L 下限
    public static final double POWER_SPAN = 0.40;   // 小板块 R/L 跨度（0.60–1.00）
    /** 巨型板块比例（×R/L）。 */
    public static final double GIANT_FRAC = 0.020;
    public static final double GIANT_R    = 2.10;

    /** 判定"大板块"的 R/L 阈值（构造项只在两侧都够大时生效）。 */
    public static final double POWER_LARGE = 0.95;
    /** 判陆允许的最小 R/L（太小的一般是洋壳微板块）。 */
    public static final double POWER_CONT  = 0.55;

    // ================= 海岸带噪声（幂图的边界是直线，靠这一层把海岸弄自然） =================
    public static boolean COAST_NOISE = true;
    public static final double CN_AMP1 = 2_500.0, CN_WL1 = 500_000.0;
    public static final double CN_AMP2 = 1_000.0, CN_WL2 = 180_000.0;
    public static final double CN_AMP3 =   400.0, CN_WL3 =  70_000.0;
    public static final double CN_GATE = 2_500.0;
    /** MWV 的基准格距（m）：加权模式下用 1,750 km，让微板块能到 1,000 km 级。 */
    public static final double L_FINE = 2_500_000.0;
    /** 造山：大板块之间 vs 小板块之间的幅度（m）。 */
    public static final double A_MINOR = 4_000.0;
    /** 洋脊：离散洋界的隆起（m）与带宽（m）。 */
    public static final double A_RIDGE = 3_000.0;
    public static final double S_RIDGE = 500_000.0;
    /**
     * 厚度混合的幂次：p=1 = 算术平均；p=4 = "软最大"。
     * 三叉点 (35,35,7) 在 p=1 下平均成 25.7（低于海平面基准 ⟹ 内海），
     * 在 p=4 下是 31.6（⟹ 陆）。海岸 (35,7) 两种都低于基准 ⟹ 岸线仍在。
     */
    public static final double T_POWER = 4.0;

    // ================= 造山累积（M3，默认关） =================
    /**
     * 造山累积开关：false ⟹ 走旧的 {@code A_OROG·conv·exp(...)}，逐位不变。
     *
     * <p>公式来源：{@code K:\moder\Talos2\造山累积-公式推导.md}
     * <pre>
     *   S_eff = f · v_c · τ
     *   W     = W0 + S_eff
     *   σ     = W/2
     *   A     = H0 · S_eff / (σ·√(2π))     // 质量守恒
     *   ΔH    = A · exp(−d²/(2σ²))
     * </pre>
     */
    public static boolean OROG_BUDGET = false;
    /**
     * 归一化速度 → m/yr 的换算（"每个板块自己的运动方向"要落到真实量级才成立）。
     *
     * <p><b>标定依据（`LandPlatesVel` 实测）</b>：边界相对速度的归一化分布是
     * p10 0.41 / p50 1.00 / p90 1.43 / max 2.35。取 0.05 后：
     * <pre>
     *   中位 50 mm/yr，p90 71 mm/yr，最大 117 mm/yr
     * </pre>
     * 与地球板块相对速度（典型 20–80、上限 150–200；Bird 2003 量级）一致。
     * 原来的 0.13 给出中位 131 / p90 185 / max 305 —— 四分之一的边界比地球最快的还快，
     * 而且把造山带的 S_eff 抬到 2,400–3,900 km，才需要 {@link #OROG_SIG_MAX} 去封顶。
     */
    public static final double V_SCALE = 0.05;
    /**
     * 相邻板块相对速度的【矢量】上限（归一化单位；×{@link #V_SCALE} = 125 mm/yr）。
     * <p>⚠ 必须夹矢量、不能像原来那样只夹 {@code conv} 这一个分量：
     * 分量各夹各的之后，{@code conv} 与 {@code shear} 不再来自同一个速度差，
     * 边界形态就不再是"由相对运动算出来的"。
     */
    public static final double VEL_REL_MAX = 2.5;
    /**
     * 平滑速度场的核宽（m）：{@code wⱼ = exp(−dⱼ²/(2·STRAIN_L²))}。
     * <p>1,200 km 的取法：板块中心到邻居种子通常 4,000+ km ⟹ 权重 e^{−5.6} = 0.4%，
     * 板块内部仍然"几乎是刚体"；而边界两侧的权重都在同一量级，过渡自然。
     */
    public static final double STRAIN_L = 2_000_000.0;
    /**
     * 用【应变场】代替"选边界对"作为构造项入口（默认关，A/B 用）。
     *
     * <p>背景：速度场是分段常数 ⟹ 应变是边界上的 delta ⟹ 靠"选最近细胞对"取 conv
     * 必然在换人处跳。把速度平滑成 C¹ 后解析求 ε，残差从 2,120 处降到 46 处。
     */
    public static boolean OROG_EPS = true;
    /**
     * 应变的归一化基准（1/m）：把 {@code comp} 除以它得到"1 ≈ 强挤压边界"。
     * <p>实测该区域应变 \|ε\| 的 p97 = 7.45e-7 /m，取 7.5e-7。
     */
    public static final double EPS_REF = 7.5e-7;
    /** 边界汇聚年龄 τ 的范围（yr）与空间波长（m）。 */
    public static final double TAU_MIN = 5_000_000.0;
    public static final double TAU_MAX = 50_000_000.0;
    public static final double TAU_WL  = 20_000_000.0;
    /** 缩短量中变成地壳增厚的比例 f（按边缘类型）。 */
    public static final double F_COLLISION  = 0.60;
    public static final double F_SUBDUCTION = 0.25;
    public static final double F_ACCRETION  = 0.50;
    /** 初始变形带宽度 W0（m）。 */
    public static final double W0_COLLISION  = 500_000.0;
    public static final double W0_SUBDUCTION = 300_000.0;
    public static final double W0_ACCRETION  = 200_000.0;
    public static final double SQRT_2PI = 2.5066282746310002;
    /** 峰值项：窄高斯，模拟逆冲叠覆的局部尖峰（珠峰级），**不是**高原面。 */
    public static final double SPIKE_AMP = 0.0;   // 暂关：窄峰会变成孤立白点
    public static final double SPIKE_SIG = 120_000.0;
    /** 山带位置微扰：沿法向位移（m）与位移场波长（m）。 */
    public static final double BELT_SHIFT = 250_000.0;
    public static final double BELT_WL    = 3_500_000.0;
    /** 保留：旧掩膜参数，已由褶皱列车取代（当前未使用）。 */
    public static final double BELT_SIG_VAR = 0.45;
    public static final double BELT_ACT_MIN = 0.45;
    /** 山脊纹理：垂直山带 250 km、沿山带 800 km（各向异性）。 */
    public static final double TEX_ACROSS = 250_000.0;
    public static final double TEX_ALONG  = 800_000.0;
    /** 克拉通/内陆起伏：厚度的长波起伏（m）。 */
    public static final double CRATON1_AMP = 1_500.0, CRATON1_WL = 3_000_000.0;
    public static final double CRATON2_AMP =   600.0, CRATON2_WL = 1_200_000.0;
    // ================= 低地 / 盆地（2026-10-21） =================
    /**
     * 陆上盆地的最大减薄（m）。
     *
     * <p><b>为什么需要它</b>：陆壳 {@code T_CONT=35 km}、基准 {@code T_REF=30.7 km}，
     * 于是陆上基准高程恒为 {@code 0.1515×(35−30.7)=651 m}——**构造上不可能出现低地**。
     * 实测 98.9% 的陆地 &gt;200 m、77% &gt;1000 m（中位 1,262 m），所以只有"一片中等高度的台地"。
     * 真实低地来自地壳<b>减薄</b>（沉积盆地/裂谷后沉降），这里用长波场模拟。
     *
     * <p>量级：要压到 300–500 m，需要 {@code dt ≈ −5,000~−6,000 m}（也就是陆壳 35→29 km）。
     */
    public static final double LOW_AMP = 6_000.0;
    /** 低地场的波长（m）：4,000 km ⟹ 每块大陆 2–3 个盆地/低地。 */
    public static final double LOW_WL = 4_000_000.0;
    /** 低地场的阈值：c 低于 {@link #LOW_HI} 开始沉降，低于 {@link #LOW_LO} 达到满幅。 */
    public static final double LOW_HI = 0.62;
    public static final double LOW_LO = 0.32;
    /**
     * 造山带宽增长的封顶（m）：{@code W = W0 + min(S_eff, OROG_SE_CAP)}。
     *
     * <p>临界楔说"带随缩短量增宽"，但那是<b>有上限</b>的（真实造山带 300–1,500 km）。
     * 不封顶时 {@code S_eff≈800 km} 会把带宽推到 2,000 km，峰值被摊薄到 ~2.8 km（实测 p99）。
     * 封在 400 km ⟹ σ 从 650 降到 450 ⟹ 峰值抬 1.4 倍。
     */
    public static final double OROG_SE_CAP = 1_500_000.0;
    /**
     * ε 路径的**局部尖峰**（m，厚度）。
     *
     * <p>质量守恒公式的理论上限是 {@code T_CONT/1.2533 → h≈4.2 km}，
     * 所以"高原面"到 2.9–4 km 就到顶了。地球上 8.8 km 的峰来自**局部逆冲叠覆**，
     * 不是整体增厚——这一项就是它。
     *
     * <p>只挂在 {@code comp > EPS_SPIKE_LO} 的最强挤压处，且用噪声切成断续块，
     * 所以不会像旧实现那样变成"孤立白点"。
     */
    public static final double EPS_SPIKE_AMP = 14_000.0;
    public static final double EPS_SPIKE_LO = 0.45;
    public static final double EPS_SPIKE_WL = 500_000.0;
    /**
     * 高原（青藏式）：峰高撞到质量守恒上限后，**多出来的挤压量横向铺开**。
     *
     * <p>物理：喜马拉雅到 8 km 就上不去了，印度板块继续北推的缩短量只能靠
     * <b>加宽</b>消化 ⟹ 旁边出现 4.5–5 km、上千公里宽的高原。
     * 我们之前只有"峰"没有"台"，所以大碰撞和中等碰撞长得一样。
     *
     * <p>触发条件：{@code se > OROG_SE_CAP}（挤压量超过峰高能消化掉的部分）；
     * 幅度按超出比例饱和：{@code 1−exp(−(se/SE_CAP−1))}。
     */
    public static final double EPS_PLATEAU_AMP = 13_000.0;
    /**
     * 造山用的【初始地壳厚度】（m），与 {@link #T_CONT} 分开。
     *
     * <p>{@code T_CONT=35 km} 是"平均陆壳"，抬高它会连带抬升全部陆地基准
     * （+5 km 厚 ⟹ +758 m 高程），所以不能共用。但碰撞带的初始地壳本来就厚：
     * 印度板块 40+ km。质量守恒的峰值上限 {@code ΔH→0.798·H0} 直接由它决定 ⟹ 提到 40 km。
     */
    public static final double EPS_T_COLL = 40_000.0;

    // ================= 造山带形态：远场传递 + 褶皱列车（M4） =================
    /**
     * 挤压向内陆传递的距离（m）：造山带不是边界上的一条线，而是宽 2,000–4,000 km 的带。
     *
     * <p><b>依据</b>：陆-陆碰撞的分布式变形可延伸到前缘 1,000–2,000 km —— 天山距喜马拉雅前缘
     * ~1,500 km 仍在强烈变形（Molnar &amp; Tapponnier 1975；England &amp; McKenzie 1982 的
     * 粘性薄板解给出同样量级的衰减长度）。⟹ 主带半宽 σ 不再由独立的 {@code BELT_*} 掩膜决定，
     * 而是取质量守恒的 {@code σ = W/2}，上限设在这里。
     */
    public static final double OROG_SIG_MAX = 700_000.0;
    /**
     * 褶皱波长 λ（m）：薄板屈曲 {@code λ ≈ 2πH(η/6η_m)^{1/3}}（Biot 1961；Fletcher 1974）。
     * H=35 km、η/η_m≈30–100 ⟹ 300–560 km；观测（青藏内部山脉、天山）150–300 km ⟹ 取 280 km。
     */
    public static final double OROG_LAMBDA = 280_000.0;
    /** 挤压最强处的波长收缩系数（脊更密）。 */
    public static final double OROG_LAMBDA_MIN = 0.65;
    /** 褶皱脊-谷的相对幅度：0 = 光滑高原面，0.5 = 明显脊谷。 */
    public static final double OROG_FOLD = 0.50;
    /**
     * 次级褶皱（"纸推到顶、前面又起小的"）：主带外缘之外再起一波，
     * 幅度 {@code OROG_SEC}、波长 {@code ×OROG_SEC_MULT}、按 {@code OROG_SEC_DECAY·σ} 衰减。
     *
     * <p>物理对应：主带进入临界楔后不再增厚，后续缩短量转移到前陆的次级逆冲带
     * （喜马拉雅前缘的 Sub-Himalaya、天山的再生造山）。额外物质来自未建模的侧向挤出。
     */
    public static final double OROG_SEC        = 0.30;
    public static final double OROG_SEC_MULT   = 1.70;
    public static final double OROG_SEC_START  = 1.15;
    public static final double OROG_SEC_DECAY  = 0.95;
    /** 褶皱相位沿走向抖动（避免笔直）；单位是波长。 */
    public static final double OROG_PHASE_JIT  = 0.90;
    /** 原各向异性纹理的权重（褶皱列车接管后降到次要）。 */
    public static final double OROG_TEX_W      = 0.15;
    /**
     * 接缝混合带（m）：第二近细胞换人处（d₁≈d₂）把「最近两细胞」的构造贡献按连续权重混合。
     *
     * <p><b>为什么必须有</b>：构造项只由最近两个细胞的板块算，第二近细胞一换人，
     * {@code conv} 就从 T(A,B) 跳到 T(A,C)——多板块交界处（三叉点）必然出现台阶/折痕。
     * 混合后 {@code w₂ = ½·(1 − (d₂−d₁)/BLEND)}：接缝上 50/50，离接缝 Δd 后归零。
     */
    public static final double OROG_BLEND = 500_000.0;
    /**
     * 构造项多边界混合的核宽（m）：{@code k_j = exp(−db_j²/(2·K²))}。
     *
     * <p><b>为什么要换成核加权求和</b>：只要用"选最近的细胞对"，换人处就必然跳。
     * 改成"把三近细胞的边界按到各自中垂面的距离做高斯加权、再归一化"之后：
     * <ul>
     *   <li>普通边界上 db₁≈0、其余极远 ⟹ 权重集中在主对，与旧行为一致；</li>
     *   <li>三叉点处三个 db 都≈0 ⟹ 自动变成三对平均（这是唯一该平均的地方）；</li>
     *   <li>第 3/4 近细胞换人发生在 db≈1,500 km 以外，那里 k ≈ e^{−2.3} = 0.1 ⟹ 跳变被压到 ~10%。</li>
     * </ul>
     * 700 km 对应"相邻两边界的影响范围重叠区"，与 σ≤700 km 的主带宽度同量级。
     */
    public static final double OROG_KERNEL = 700_000.0;
    /**
     * 三叉点修正的作用半径（m）：第三近细胞进入这个距离以内时，
     * 三对边界同时在场，按 {@code q=(d₂−d₀)/OROG_VERTEX} 连续淡入三对平均。
     * <p>实测跳变点（cellId 与 plateId 同时变）要求它不小于 1,500 km 才够得着。
     */
    public static final double OROG_VERTEX = 0.0;
    /**
     * 陆/洋分支的过渡宽度（m，按厚度异常 base 计）：{@code g = smoothstep((base+GW/2)/GW)}。
     *
     * <p>⚠ 以前是 {@code if (base > 0) 陆上剖面 else 洋上剖面}。实测在一条海岸线上：
     * 洋侧 `dt=+4,697`（洋脊）、陆侧 `dt=−12,500`（裂谷），**差 17,197 m 厚度 = 2,673 m 高程**，
     * 就是放大图里那些"沿一条线突然掉下去"的崖。GW=4,000 m 让过渡落在 ±300 m 高程内。
     */
    public static final double COAST_GATE = 4_000.0;
    /**
     * 单条边界构造影响的【截断半径】（m）：超过它这条边界的贡献严格为 0。
     *
     * <p>1,800→2,600 km 用 smoothstep 收到 0，与主带（σ≤700 km）+ 次级褶皱（到 ~2,100 km）
     * 的实际影响范围一致；同时保证「换用哪一对边界」这种集合变化发生时，作用已衰减到 0。
     */
    public static final double OROG_REACH = 2_600_000.0;
    /**
     * 大陆场参考系混合带（m）：板块边界两侧各在自己的运动参考系里取大陆场，
     * 在 ±CRUST_BAND 内按 smoothstep 混合（避免边界上出现 c 的台阶）。
     */
    public static final double CRUST_BAND = 600_000.0;
    /**
     * 大陆场"随板块漂移"的强度（0 = 关，1 = 位移 v·τ）。
     *
     * <p>⚠ 默认 **0**：这个机制在板块参考系切换处会让取样坐标跳 1,000+ km，
     * 于是 {@code c0}（进而地壳厚度、海陆）出现 20,000+ m 的硬台阶——
     * 实测最差处 tc 从 37,041 m 跳到 12,576 m，地形直接断层。
     * 在找到"参考系本身连续"的写法之前，必须关着。
     */
    public static final double CRUST_DRIFT = 0.0;

    // ================= 边界分类（shear / 四类边界） =================
    /** |conv| 低于它视为"无正/负交"（走滑候选）。 */
    public static final double CONV_THRESH = 0.10;
    /** shear 高于它视为走滑为主。 */
    public static final double SHEAR_TRANS = 0.15;
    /** 转换边界的小幅起伏（m）与带宽（m）。 */
    public static final double A_TRANS = 800.0;
    public static final double S_TRANS = 150_000.0;

    // ================= 俯冲极性（2026-10-21） =================
    /**
     * 谁俯冲：<b>更洋的一侧</b>（{@code c} 更低 = 洋壳更厚/更老）。陆-陆不算俯冲。
     *
     * <p><b>为什么必须有</b>：碰撞两侧同时抬升是对的；但"至少一侧是洋壳"时，
     * 现实里只有<b>一侧</b>俯冲——海沟在俯冲侧，火山弧在上覆侧，两者相距 200–300 km。
     * 之前按"该点是不是陆"分支，于是洋-洋汇聚会在两侧各画一条海沟（对称双沟），
     * 而且永远长不出岛弧。
     *
     * <p>海沟质心略偏向俯冲侧 {@link #TRENCH_OFF}；火山弧在上覆侧 {@link #ARC_OFF} 处，
     * 幅度取 {@link #A_ARC}——量级按"岛弧地壳 25–30 km"反推：
     * 洋壳 7 km 要抬到海平面附近，厚度异常需要 ~2.4×10⁴ m。
     */
    public static final double TRENCH_OFF = 80_000.0;
    public static final double ARC_OFF = 260_000.0;
    public static final double S_ARC = 220_000.0;
    /**
     * 岛弧的"成链"门：沿走向用 380 km 噪声把它切成断续的段。
     * <p>不做这个的话，弧就是一条 1,000+ km 的连续金线——看着像划痕而不是岛链。
     */
    public static final double ARC_SEG_WL = 380_000.0;
    public static final double ARC_SEG_LO = 0.36;
    public static final double ARC_SEG_HI = 0.62;
    /** 岛弧幅度：按"弧壳 20–25 km、只有脊部露出水面"取，比之前低一档。 */
    public static final double A_ARC = 16_000.0;
    /**
     * 俯冲极性的平滑尺度（c 的差）：{@code pol = tanh((c₁−c₀)/POL_SMOOTH)}。
     * <p>⚠ 不能直接比较 `c0 < c1` —— 两侧 c 接近时判定会沿边界来回翻转，
     * 海沟/岛弧瞬间换到另一侧，就是最刺眼的那种台阶。
     */
    public static final double POL_SMOOTH = 0.04;
    /** 斜向边界相对纯汇聚的幅度系数。 */
    public static final double OBLIQUE_SCALE = 0.60;

    // ================= 板块驱动力：向"俯冲汇"的板片拉力 =================
    /** 汇（subduction sink）的格距、抖动与拉力强度。 */
    public static final double SINK_L = 28_000_000.0;
    public static final double SINK_JIT = 0.80;
    public static final double VEL_SINK = 0.70;

    // ================= 速度场（M3a，默认关） =================
    /**
     * 速度场开关：false ⟹ 旧的「两份噪声差 + 逐板块 hash」，逐位不变。
     *
     * <p>真实板块速度是**大尺度地幔流**的离散化（USGS：0–150 mm/yr，相邻板块相关），
     * 不是每块一个独立随机向量。这里用**无散流函数** ψ 的背景流：
     * <pre>
     *   ψ(x,z) = valueNoise(x/λ, z/λ)
     *   v_bg = ( ∂ψ/∂z , −∂ψ/∂x )        // 散度恒为 0
     * </pre>
     * 板块速度 = 在该板块种子处采样 v_bg（刚体板块 ⟹ 速度在板块内为常数），
     * 再按面积缩放 {@code v ∝ w^(−1/2)}（小板块更快，观测规律）。
     */
    public static boolean VEL_FIELD = false;
    /** 背景流场波长（m）与有限差分步长（m）。 */
    public static final double VEL_WL = 8_000_000.0;
    public static final double VEL_FD_STEP = 2_000_000.0;
    /** 面积缩放指数：v ∝ w^(−VEL_AREA_EXP)。 */
    public static final double VEL_AREA_EXP = 0.5;
    /** 逐板块扰动幅度（相对背景流）。 */
    public static final double VEL_JITTER = 0.35;

    // ================= 板块层（Worley + 核投票 + 两层扭曲） =================
    public static final double PLATE_CELL = 6_000_000.0;

    // ================= 两层板块 B′（2026-10-24） =================
    /**
     * 粗晶格间距（m）：巨型板块的种子。40,000 km 窗口 ⟹ 约 4–9 个巨型。
     * <p>取值 20,000 km 来自 400×400 参数扫描：L1=20,000 / L2=8,000 / BAND=400 km 得到
     * **12 个板块**（4 巨型 15–19% + 8 中小），最接近用户要求的“~10 块”。
     */
    public static double TT_L1 = 20_000_000.0;
    /** 细晶格间距（m）：中小板块的候选种子。 */
    public static double TT_L2 = 8_000_000.0;
    /**
     * 细层种子的“出生带”半宽（m）：只有到【粗 Voronoi 边界】的距离小于它的细种子才存在。
     * <p>关键：带只决定**哪些种子存在**，不决定边界——板块边界仍是两个最近种子的中垂线，
     * 所以剖分唯一、不嵌套、无空单元。
     */
    public static double TT_BAND = 400_000.0;
    public static final double PLATE_GIANT_L = 16_000_000.0;
    public static final double PLATE_R = 9_700_000.0;
    /**
     * 巨型核投票的【软化带宽度】（m）：归属度在核边 ±GIANT_RIM/2 内从 1 连续降到 0。
     *
     * <p>以前是布尔判定（种子在核内 → 巨型板块，否则 → 独立小板块）。种子一跨过核边，
     * 板块 id / 速度 / 俯冲极性全部硬切换——这是"相邻细胞落在不同板块"那类台阶的根。
     * 1,200 km 与核半径（9,700 km）之比约 12%，足以把切换摊成缓变，又不会把巨型板块的
     * 刚性速度抹掉（核内仍然是严格同一个速度）。
     */
    public static final double GIANT_RIM = 400_000.0;
    public static final double PLATE_JIT = 0.75;
    /** 板块层两层扭曲：大尺度塑形 + 小尺度磨边。 */
    public static final double PW_A1 = 3_000_000.0, PW_WL1 = 30_000_000.0;
    public static final double PW_A2 = 1_500_000.0, PW_WL2 = 14_000_000.0;
    public static final double PW_A3 = 1_200_000.0, PW_WL3 =  6_000_000.0;
    public static final double PW_A4 =   900_000.0, PW_WL4 =  4_200_000.0;
    /** giant / 小板块的速度缩放（giant 慢、小块快）与背景流增益。 */
    public static final double VEL_GIANT = 0.60;
    public static final double VEL_SMALL = 1.25;
    public static final double VEL_GAIN = 4.0;

    /** 当前生效的板块格距。 */
    public static double plateL() { return PLATE_WEIGHTED ? L_FINE : L; }

    /** 深海热沉降（m）：洋壳年龄 0→1 对应 0→−2,000 m（GDH1 量级，出处待补）。 */
    public static final double SEA_AGE_SUB = 2_000.0;

    /** 定标采样域（m）与步长（m）：14 步二分 × 160×160 ≈ 70 ms/种子。 */
    public static final double CAL_SPAN = 40_000_000.0;
    public static final double CAL_STEP = 250_000.0;
    public static final int CAL_ITERS = 12;

    /**
     * 配置戳：任何会改变输出的量都必须折进来（瓦片缓存失效靠它）。
     * <p>⚠ 只折**常量**；worldSeed 由调用方单独给出。
     */
    public static long configStamp() {
        long h = 0x9E3779B97F4A7C15L;
        h = h * 31 + VERSION;
        h = h * 31 + Double.doubleToLongBits(L);
        h = h * 31 + Double.doubleToLongBits(SEED_JITTER);
        h = h * 31 + Double.doubleToLongBits(WARP_A1);
        h = h * 31 + Double.doubleToLongBits(WARP_WL1);
        h = h * 31 + Double.doubleToLongBits(WARP_A2);
        h = h * 31 + Double.doubleToLongBits(WARP_WL2);
        h = h * 31 + Double.doubleToLongBits(WARP_A3);
        h = h * 31 + Double.doubleToLongBits(WARP_WL3);
        h = h * 31 + Double.doubleToLongBits(WARP_A4);
        h = h * 31 + Double.doubleToLongBits(WARP_WL4);
        h = h * 31 + Double.doubleToLongBits(T_CONT);
        h = h * 31 + Double.doubleToLongBits(T_OCEAN);
        h = h * 31 + Double.doubleToLongBits(T_REF);
        h = h * 31 + Double.doubleToLongBits(SIGMA);
        h = h * 31 + Double.doubleToLongBits(A_OROG);
        h = h * 31 + Double.doubleToLongBits(S_OROG);
        h = h * 31 + Double.doubleToLongBits(A_TRENCH);
        h = h * 31 + Double.doubleToLongBits(S_TRENCH);
        h = h * 31 + Double.doubleToLongBits(A_RIFT);
        h = h * 31 + Double.doubleToLongBits(S_RIFT);
        h = h * 31 + Double.doubleToLongBits(A_TRANS);
        h = h * 31 + Double.doubleToLongBits(S_TRANS);
        h = h * 31 + Double.doubleToLongBits(TRENCH_OFF);
        h = h * 31 + Double.doubleToLongBits(ARC_OFF);
        h = h * 31 + Double.doubleToLongBits(S_ARC);
        h = h * 31 + Double.doubleToLongBits(A_ARC);
        h = h * 31 + Double.doubleToLongBits(ARC_SEG_WL);
        h = h * 31 + Double.doubleToLongBits(C_WL0);
        h = h * 31 + Double.doubleToLongBits(C_WL);
        h = h * 31 + Double.doubleToLongBits(C_WL2);
        h = h * 31 + Double.doubleToLongBits(DET1);
        h = h * 31 + Double.doubleToLongBits(DET2);
        h = h * 31 + Double.doubleToLongBits(DET3);
        h = h * 31 + Double.doubleToLongBits(DWL1);
        h = h * 31 + Double.doubleToLongBits(DWL2);
        h = h * 31 + Double.doubleToLongBits(DWL3);
        h = h * 31 + Double.doubleToLongBits(DET4);
        h = h * 31 + Double.doubleToLongBits(DWL4);
        h = h * 31 + Double.doubleToLongBits(SEA_AGE_SUB);
        if (COAST_AZIMUTH) h = h * 31 + 1;      // ★ 只在开时折入 ⟹ 关时逐位不变
        if (VEL_FIELD) {
            h = h * 31 + 4;
            h = h * 31 + Double.doubleToLongBits(VEL_WL);
            h = h * 31 + Double.doubleToLongBits(VEL_FD_STEP);
            h = h * 31 + Double.doubleToLongBits(VEL_AREA_EXP);
            h = h * 31 + Double.doubleToLongBits(VEL_JITTER);
            h = h * 31 + Double.doubleToLongBits(VEL_GAIN);
        }
        if (OROG_BUDGET) {
            h = h * 31 + 3;
            h = h * 31 + Double.doubleToLongBits(V_SCALE);
            h = h * 31 + Double.doubleToLongBits(VEL_REL_MAX);
            h = h * 31 + Double.doubleToLongBits(TAU_MIN);
            h = h * 31 + Double.doubleToLongBits(TAU_MAX);
            h = h * 31 + Double.doubleToLongBits(TAU_WL);
            h = h * 31 + Double.doubleToLongBits(F_COLLISION);
            h = h * 31 + Double.doubleToLongBits(F_SUBDUCTION);
            h = h * 31 + Double.doubleToLongBits(F_ACCRETION);
            h = h * 31 + Double.doubleToLongBits(W0_COLLISION);
            h = h * 31 + Double.doubleToLongBits(W0_SUBDUCTION);
            h = h * 31 + Double.doubleToLongBits(W0_ACCRETION);
            h = h * 31 + Double.doubleToLongBits(SPIKE_AMP);
            h = h * 31 + Double.doubleToLongBits(SPIKE_SIG);
            h = h * 31 + Double.doubleToLongBits(OROG_SIG_MAX);
            h = h * 31 + Double.doubleToLongBits(OROG_LAMBDA);
            h = h * 31 + Double.doubleToLongBits(OROG_LAMBDA_MIN);
            h = h * 31 + Double.doubleToLongBits(OROG_FOLD);
            h = h * 31 + Double.doubleToLongBits(OROG_SEC);
            h = h * 31 + Double.doubleToLongBits(OROG_SEC_MULT);
            h = h * 31 + Double.doubleToLongBits(OROG_SEC_START);
            h = h * 31 + Double.doubleToLongBits(OROG_SEC_DECAY);
            h = h * 31 + Double.doubleToLongBits(OROG_PHASE_JIT);
            h = h * 31 + Double.doubleToLongBits(OROG_TEX_W);
            h = h * 31 + Double.doubleToLongBits(OROG_BLEND);
            h = h * 31 + Double.doubleToLongBits(OROG_KERNEL);
            h = h * 31 + Double.doubleToLongBits(OROG_REACH);
            h = h * 31 + Double.doubleToLongBits(CRUST_BAND);
            h = h * 31 + Double.doubleToLongBits(BELT_SHIFT);
            h = h * 31 + Double.doubleToLongBits(BELT_WL);
        }
        if (PLATE_WEIGHTED) {
            h = h * 31 + 2;
            h = h * 31 + Double.doubleToLongBits(POWER_MIN);
            h = h * 31 + Double.doubleToLongBits(POWER_SPAN);
            h = h * 31 + Double.doubleToLongBits(GIANT_FRAC);
            h = h * 31 + Double.doubleToLongBits(GIANT_R);
            h = h * 31 + Double.doubleToLongBits(POWER_LARGE);
            h = h * 31 + Double.doubleToLongBits(POWER_CONT);
            if (COAST_NOISE) {
                h = h * 31 + 5;
                h = h * 31 + Double.doubleToLongBits(CN_AMP1);
                h = h * 31 + Double.doubleToLongBits(CN_WL1);
                h = h * 31 + Double.doubleToLongBits(CN_AMP2);
                h = h * 31 + Double.doubleToLongBits(CN_WL2);
                h = h * 31 + Double.doubleToLongBits(CN_AMP3);
                h = h * 31 + Double.doubleToLongBits(CN_WL3);
                h = h * 31 + Double.doubleToLongBits(CN_GATE);
            }
            h = h * 31 + Double.doubleToLongBits(L_FINE);
            h = h * 31 + Double.doubleToLongBits(A_MINOR);
            h = h * 31 + Double.doubleToLongBits(A_RIDGE);
            h = h * 31 + Double.doubleToLongBits(S_RIDGE);
        }
        return h;
    }
}
