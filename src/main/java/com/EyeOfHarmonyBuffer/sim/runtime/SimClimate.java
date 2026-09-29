package com.EyeOfHarmonyBuffer.sim.runtime;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;   // ★ S1a（§385）
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * **新模拟器 → 世界的运行时桥**（纵向切片第 2 步：气候 → 群系）。
 *
 * <h3>接线点与爆炸半径</h3>
 * 群系管线是 WorldChunkManagerTalos2.pickBiomeFor → V2BiomePicker.biomeAt
 * → V2BiomeField.kind（250 m LUT）→ **V2BiomeSelect.accumulateWeights**
 * → **ClimateCoords.sample**。LUT 求解时逐格调用 accumulateWeights，
 * 运行时只查表 —— 所以「气候 → 群系」的**唯一入口**是 ClimateCoords.sample。
 * 本类就是气候的唯一实现（第 3 段之后不再有回退分支），
 * Coords 的每一个字段都由新模拟器给出，**下游（20 格气候带表、地形变体、高度倾向）
 * 一行不改**：群系只通过「气候坐标」改变。
 *
 * <h3>① 成本：为什么必须在粗格点上算一次再双线性上采样</h3>
 * 各量的代价（P381 实测，见 build/eoh_scratch_clim2/REPORT.md）：
 * <pre>
 *   PlateField.elevationWithCell（1 次高程）             ~0.4-0.6 us
 *   Atmosphere.kappaAt（193 次 isLand 环采样）           ~56-74 us
 *   Atmosphere.windAt（4 次 pressureAnomaly + kappa）    ~280 us
 *   PrecipField.mmPerDay（5 次 windAt + 风暴轴涡动项）   ~1.45 ms
 *   ⇒ 一整列气候（kappa + T + 年平风 + 4 季降水）        ~6 ms
 * </pre>
 * 而群系 LUT 是 **250 m** 网格（一个 100x50 km 瓦片 = 81,204 格）⇒ 直接算需要 **~8 分钟/瓦片**。
 * 实测（P382/P386）：**旧气候**下同一个瓦片的求解要 **46~49 s**（250 m 网格让 RelaxedClimate 的
 * 窗口缓存不断抖动，逐格代价 ~0.57 ms），完全不可能。旧实现早有现成范本
 * （RelaxedClimate 的 TILE_X/CELL_X + halo、V2BiomeField 的 CELL/TILE + 双线性 + 瓦片缓存），
 * 本类照同一模式做：**在粗格点上算一次、缓存、双线性上采样到 250 m**。
 * 实测同一个瓦片的求解降到 **~2 s**。
 *
 * <h3>格点间距的选取理由（不是拍的）</h3>
 * 气候场里**最细的独立物理尺度**：
 * <pre>
 *   COAST_BLEND（kappa 的海陆混合尺度）          800 km
 *   风场的差分步长 gradStep（生产 500 km）       ⇒ 风在 1000 km 以下没有独立信息
 *   ITCZ / w_zm 的纬度带宽                       1000~2000 km
 *   PrecipField.UPWIND_STEP（上风地形取样）      150 km
 *   ⇒ 除「原始高程」外，最细的独立尺度 ≈ 150 km。
 * </pre>
 * 取 CELL = 10 km ⇒ 对最细尺度 15 倍过采样、对 kappa 80 倍。
 * 保真度实测（P383 §4）：CELL = 10 km 与 5 km 的**群系不一致率 0.105%**、与 2.5 km 差 0.080%
 * ⇒ 10 km 已经收敛。
 *
 * <p><b>唯一被粗格点牺牲的量是原始高程</b>（Voronoi 棱上有最高 7,970 m 的跳变，§7/§84.5），
 * 它进温度的方式是**海拔直减** ⇒ 本类**不把直减算进格点**：格点存的是
 * **海平面等效温度**，每列再用**该列自己的精确高程**减一次直减率。
 * 于是温度在格点上与生产 surfaceTemp 逐位相等，格点之间只平滑掉 10 km 以下的细节。
 *
 * <h3>② 季节：为什么是**4 个等间距相位求平均**</h3>
 * 群系必须是**静态**的（不能随 Theta 闪）。取 theta = 0, pi/2, pi, 3pi/2（至日 + 分点）：
 * <pre>
 *   Sum_q cos(theta_q - psi) = 0   （对任意 psi、任意半球相位）  ⇒ 季节项的**年平恒为 0**
 * </pre>
 * ⇒ 对温度这一类「季节项线性进入」的量，4 季平均与**解析年平逐位相等**：
 * <pre>
 *   T_年平 = T_zm + (1-kappa)*SST'
 *          = surfaceTemp(theta_0) - seasonalAnomaly(theta_0)
 *            + GAMMA*max(0,elev)*kappa + (1-kappa)*SST'
 * </pre>
 * ⚠ <b>这里踩过一个坑（P383 抓到）</b>：第一版只写了
 * <code>surfaceTemp(theta_0) + GAMMA*h*kappa</code>，**忘了减 seasonalAnomaly(theta_0)**，
 * 于是 tSea 里混进了一个 **±A(phi)（中纬陆地最大 18.7 K）的假季节偏移** ——
 * 北半球整体偏暖、南半球整体偏冷（theta_0 是北半球夏至）。探针实测：4 季平均与它的差
 * 最大 **42.1 K**。修好后两者差 &lt; 1e-12 K（P383 §1）。⇒ **温度可以只算一季，省 3/4 调用**。
 *
 * <p>对**非线性**的量（wEff 里迁移的 ITCZ、precip 的 max(0,.) 截断、风暴轴涡动项、风矢量）
 * 不能只取一季：单季取样会把 ITCZ 挪到 10 度以外（theta=0 是北半球夏至），
 * 赤道群系会整条错位。所以**降水与风都真的取 4 季平均**。
 *
 * <h3>③ 归一化：物理量 → [0,1] 的锚（都能被真实观测核对）</h3>
 * <pre>
 *   temp  = 0.10 + (T_sfc(K) - 255.15) / 55        T ∈ [255.15, 299.15] ⇒ [0.10, 0.90]
 *   moist = 0.4974*log10(P_mm/yr) - 0.8525         P ∈ {200,400,900,2000} ⇒ {0.29,0.44,0.62,0.79}
 *   continent = kappa（已经是 [0,1]，不做映射）
 * </pre>
 * 锚点表（V2BiomeSelect 的带心 ↔ 观测）：
 * <pre>
 *   带心  坐标   本式反推的物理量        真实观测锚
 *   0.10  极地   255.2 K (-18.0 C)      北极年平 -18 C；也正是 Atmosphere.T_zm 的极点值
 *   0.30  苔原   266.2 K (-7.0 C)       苔原带年平 -5~-8 C
 *   0.52  温带   278.3 K (+5.1 C)       寒温带针叶林/温带年平 3~8 C
 *   0.72  亚热带 289.3 K (+16.1 C)      亚热带年平 16~18 C
 *   0.90  热带   299.2 K (+26.0 C)      热带雨林年平 26~27 C；也正是 T_zm 的赤道值
 *   0.30  干旱   ~200 mm/yr            真沙漠 < 250 mm/yr
 *   0.44  半干旱 ~400 mm/yr            半干旱草原 250~500 mm/yr
 *   0.60  湿润   ~880 mm/yr            湿润森林 600~1500 mm/yr
 *   0.80  过湿   ~2000 mm/yr           雨林/过湿 > 2000 mm/yr
 * </pre>
 * 温度的两个端点锚**就是 Atmosphere.T_zm 拟合地球观测用的那两个点**（赤道 299 K / 极 255 K）
 * ⇒ 归一化与温度场用的是同一套观测，不是两套。降水用 4 个观测降水级做 log 线性回归，
 * 残差 &lt;= 0.017 坐标（= 降水的 8%），四个带心全部落在观测级里。
 *
 * <h3>湿度用的是**生产降水场本身**，不是另写一套</h3>
 * logP = log10( 4 季平均的 PrecipField.mmPerDay(x,z,seed,cell,theta,500_000) x 365.25 )
 * ⇒ 群系的「湿」与 /talosmap 的降水图层是**同一个函数**，不存在口径漂移。
 * 代价是每格点 4 x 1.45 ms（占整个瓦片求解的 ~25%）；换掉的是「自己用 50 km 格点重算散度」
 * 带来的 **30~77% 的降水偏差**（P383 第一版实测，那版已废弃）。
 *
 * <h3>为什么不读 {@code oro}（重要）</h3>
 * 第 1 步之后世界的海陆由 PlateField 决定，而 OrographyField.OroSample 来自**旧陆海场**
 * NoiseContinentGrid（两者实测不一致 ~10%，见 P385）。群系 LUT 对**每一格**都用 asLand=true
 * 求解（海格的陆地通道留给海岸线取用），于是若读 oro.elevation01，会在「旧场说是海、
 * 新场说是陆」的地方拿到 elevation01 = 0（没有直减）⇒ 引入一个与地形无关的假平地。
 * **本类一个字段都不读 oro**，参数只为与旧签名逐位兼容而保留。
 *
 * <h3>与 SimTerrain 的关系</h3>
 * 种子映射共用 {@link SimTerrain#seedOf(int)} —— 气候与地形必须落在**同一个** PlateField
 * 世界上，否则 kappa 的海岸线与方块海岸线会错位。
 *
 * <p>纯函数：给定 (seed, x, z) 结果逐位可复现；唯一的静态可变状态是
 * **瓦片缓存**（{@link #clearCache()}）与开关注入点。
 */
public final class SimClimate {

    private SimClimate() {}

    // ★ 2026-09-18 顶死一套（第 3 段）：原 `public static boolean ENABLED = true` 已退役。
    //   它是「回退到旧栈」的开关，但 ClimateCoords 的旧实现已删除 ⇒ 无路可回退；
    //   留着这个 false 分支只会再长出一套口径（D17/D72 就是这么来的）。
    //   现在气候只有一条路：ClimateCoords.sample -> SimClimate.sample。

    // ==================== 网格几何 ====================

    /**
     * 主格点间距（m）。**必须整除 TILE_X 与 TILE_Z**（否则相邻瓦片的格点不在同一个绝对格上
     * ⇒ 双线性插值在瓦片边界不连续）。
     *
     * <p>10 km 的依据见类注释：气候场最细的独立尺度 ≈ 150 km（UPWIND_STEP），15 倍过采样；
     * 与 5 km / 2.5 km 的群系不一致率 0.105% / 0.080%（P383 §4）。
     */
    public static int CELL = 10_000;

    /**
     * **到海岸的距离量的搜索半窗（m）—— 群系 continent 坐标专用**（审计 D18-(c2)）。
     *
     * <p>⚠ <b>2026-09-13 更正</b>：本字段的语义在 D18 的两次尝试之间**变过**，本段原来描述的是
     * **已被放弃的那一版**（「40 km 半径的陆地占比」）。现行实现是
     * {@code continent = clamp01(-PlateField.coastDistanceNew(x,z,seed,cell,COAST_FINE) / COAST_FINE)}，
     * 即**与旧 ClimateCoords:94 逐字同式的「距离」量**，只是换了场（新场 PlateField）。
     *
     * <p>为什么放弃「占比」型：**任何陆地占比在离岸 d 处都是 0.5 + d/(pi*R/2)** ——
     * 想让 24 km 处达到旧语义的 0.60 就需要 R≈76 km，**而那时岸线处仍是 0.5** ⇒ 端点对不上。
     * P454 实测：40 km 半径下 kapFine(24 km) 只有 0.038，而旧语义要 0.60 —— **量程差一个数量级**。
     *
     * <p>⚠ 也**不能**退回旧的 {@code OrographyField.coastDist}：那个来自**旧场** NoiseContinentGrid，
     * 而 D16-a 刚把方块/群系的海陆统一到新场 ⇒ 用它等于把旧场请回来。
     * ⇒ 做法是：**在新场 PlateField 上算一个距离量**（{@code PlateField.coastDistanceNew}）。
     *
     * <p>⚠⚠ 本段曾有一版写「用 40 km 半径的陆地**占比**」，并推导「带宽 0.16 = 15 个量化台阶」。
     * **那一版已被放弃**（P454 实测证伪：占比型在离岸 d 处恒为 0.5 + d/(pi*R/2)，
     * 24 km 处只到 0.038 而旧语义要 0.60 ⇒ **端点对不上、量程差一个数量级**）。
     * 引这段旧推导请先读 §118.9。
     */
    public static int COAST_FINE = 40_000;

    /**
     * 瓦片尺寸（m）。
     *
     * <p>为什么不能取 1:1（第一版就是 1:1，被 P388 §7 抓到）：群系 LUT 的求解范围含
     * **±250 m halo**，而缓存是按「包含该点的瓦片」索引的 ⇒ 瓦片边界上的 halo 列会落进
     * **邻居瓦片**，一次 V2BiomeField 求解要连带解 **9 个**气候瓦片（3x3），首个瓦片 8.9 s
     * 里 7.5 s（84%）花在气候上。
     *
     * <p>⚠⚠ <b>2026-09-13 更正（审计 D20）</b>：本段原来写「取 2x2 之后，每个群系瓦片（含 halo）
     * **恰好落在一个气候瓦片内**」，并声称 TILE_X/TILE_Z 是 V2BiomeField 的 **2x2 倍**。
     * <b>两句都不成立</b>：代码里 TILE_X=100_000 / TILE_Z=50_000，与
     * {@code V2BiomeField.TILE_X/TILE_Z} <b>完全相同</b>（那是 <b>1:1</b>，不是 2x2）；
     * 而且 V2BiomeField 的 halo 采样点在 {@code originX ± CELL/2 = ±125 m}，
     * <b>已经跨出</b>气候瓦片 ⇒ 一次冷启动的群系瓦片仍会连带解 <b>3x3 = 9 个</b>气候瓦片。
     * （数值正确性不受影响 —— 每个瓦片自带 halo 副本；受影响的是**冷启动成本**。）
     *
     * <p>原句的后续：
     * 瓦片内** ⇒ 一次求解一个气候瓦片。2:1 是对齐关系里最小的可行值。
     */
    public static final int TILE_X = 100_000;
    public static final int TILE_Z = 50_000;

    /** 风/气压的差分步长（m）。**必须与生产 Atmosphere.windAt 的实参一致**（500 km）。 */
    public static int GRAD_STEP = 500_000;

    /** 上风取样距离（m）—— 只用于 onshore 这个诊断位。 */
    public static int UPWIND_OFFSET = 15_000;

    /** 坡度诊断的差分步长（m）与饱和尺度（无量纲坡度）。2% = 20 m/km = 典型山前坡度。 */
    public static int SLOPE_STEP = 2_000;
    public static double SLOPE_SCALE = 0.02;

    /** 瓦片缓存容量（个）。每个瓦片 ≈ 10 KB（84 个格点 x 9 条通道）。 */
    /**
     * 缓存的瓦片数上限。**§7624：64 -> 4,096。**
     *
     * <p><b>为什么改</b>（P1266/P1267 实测）：气候瓦片是 100 km x 50 km，而本仓的
     * 水系/出图要扫【数千 km】⟹ 工作集远超 64：
     * <pre>
     *   单个 WaterField tile (160 km)  : 约   12 个瓦片  -> 64 够
     *   20x20 tiles 出图      (3200 km) :   2,048 个瓦片  -> 64 【thrash】
     *   全图                  (6400 km) :   8,192 个瓦片  -> 64 【严重 thrash】
     * </pre>
     * P1266 的直接对照：{@code climate miss = 0} 时 {@code 1.32 ms/tile}，
     * 而 {@code miss = 311} 时 {@code 9,561 ms/tile} —— <b>差 7,200 倍</b>。
     *
     * <p><b>内存</b>：一个 {@link Field} = 11 个 {@code double[]} x (12x7=84) x 8 B
     * = <b>7.4 KB</b> ⟹ 4,096 个 = <b>约 30 MB</b>（可接受）。
     *
     * <p><b>⚠ 它【不改任何计算结果】</b>：{@code solve()} 是纯函数，本参数【只是容量】
     * ⟹ 输出<b>逐位不变</b>⟹ <b>不进 {@link #configStamp()}</b>（D58 的准入判据是
     * 「改了结果的旋钮」，而它不改结果）。
     *
     * <p><b>为什么只提容量、不改淘汰策略</b>：见 {@link #evictAny()} 的 javadoc ——
     * 本仓早已判断「策略不是杠杆，容量才是」（与 V2BiomeField.evictAny 同款）。
     */
    public static int CACHE_LIMIT = 8_192;

    /** 4 个等间距季节相位（至日 + 分点）。theta=0 是北半球夏至（Atmosphere 的约定）。 */
    public static final double[] SEASON = {0.0, Math.PI / 2, Math.PI, 3 * Math.PI / 2};

    // ==================== 归一化锚 ====================
    /** 温度锚下：模型的极点年平（= 地球观测北极年平 -18 C）。 */
    public static final double T_LO_K = 255.15;
    /** 温度锚上：模型的赤道年平（= 地球观测赤道年平 +26 C）。 */
    public static final double T_HI_K = 299.15;
    /** 坐标锚：极地带心 / 热带带心（= V2BiomeSelect.TEMP_CENTER 的首末项）。 */
    public static final double TEMP_LO = 0.10, TEMP_HI = 0.90;
    /** temp 的斜率 = (0.90-0.10)/(299.15-255.15) = 1/55。 */
    public static final double TEMP_SLOPE = (TEMP_HI - TEMP_LO) / (T_HI_K - T_LO_K);

    /** moist = MOIST_A*log10(P_mm/yr) - MOIST_B。4 个观测降水级 {200,400,900,2000} mm/yr 的 log 线性拟合。 */
    public static final double MOIST_A = 0.4974, MOIST_B = 0.8525;
    /** 降水下限（mm/yr）—— 纯数值保护（log10(0)），远低于任何真实沙漠。 */
    public static final double P_MIN_MM_YR = 1.0e-3;

    /** airT 诊断的归一尺度（K）：相对纬向平均的距平 / 15 K。 */
    public static final double AIRT_SCALE = 15.0;
    /**
     * airT 的分子用哪个温度（**审计 D8-b 的 A/B 开关**，用户裁决 4-C）。
     *
     * <p>{@code false}（默认）{@code = tSfc - tzm}，{@code true} {@code = tSea - tzm}。
     * 两者是**同一个海平面等效层**的差别问题：{@code tSfc} 含 {@code -GAMMA*max(0,elev)*kappa}，
     * 而 {@code tzm} 是海平面值 ⇒ 相减会把大陆点系统性压低。
     *
     * <p>⚠ 它**进 {@link #configStamp()}**：改了结果就必须让瓦片失效（D58 的教训）。
     */
    public static boolean AIRT_SEALEVEL = false;

    /**
     * 海温距平的注入点（第 3 步接 A2/A3）。null（默认）= 无海温距平 —— 与生产一致：
     * Atmosphere.SST_PROVIDER 在 src 里**没有任何调用方**（P380 源码扫描）。
     *
     * <p>⚠⚠ <b>2026-09-13 修正（审计 D3）</b>：原文写的是「它只进温度这一路（本类显式加
     * (1-kappa)*SST'）… 第 3 步把洋流接进来时要同时设置两处」——<b>这条指导是错的</b>。
     * 因为 {@code Atmosphere.surfaceTemp} 的返回式里已经含 (1-k)*sstAnom，
     * 「两处都设」会得到 2(1-kappa)*SST'（P447 F 段实测差 +5.000 K）。
     * ⇒ 现在<b>只有一个注入点：Atmosphere.SST_PROVIDER</b>；
     * {@link #SST_PROVIDER} 保留字段只为兼容探针签名（P447 的反事实分支）。
     *
     * <p>⚠ <b>2026-09-13 补正（审计 D56）</b>：上一句原文只说「不再参与 <b>tSea</b> 的计算」——
     * 措辞太窄，结果<b>另外两个读者被留在原地</b>（{@code tSl -> f.q} 与 {@code f.sst}），
     * 它们一直读到恒 null 的字段、静静贡献 0。接线之后 tSea 有 SST'、那两个没有 ⇒ 口径分裂。
     * 现在本类<b>没有任何一处</b>读这个字段；{@code sstAnomAt} 已删除。
     */
    public interface SstProvider { double anomalyAt(int x, int z); }
    public static SstProvider SST_PROVIDER = null;

    // ==================== 诊断计数器 ====================
    public static final AtomicLong SOLVE_COUNT = new AtomicLong();
    public static final AtomicLong SOLVE_NANOS = new AtomicLong();
    public static final AtomicLong NODE_COUNT = new AtomicLong();
    public static final AtomicLong CACHE_HIT = new AtomicLong();
    public static final AtomicLong CACHE_MISS = new AtomicLong();
    public static final AtomicLong SAMPLE_COUNT = new AtomicLong();

    public static void resetStats() {
        SOLVE_COUNT.set(0); SOLVE_NANOS.set(0); NODE_COUNT.set(0);
        CACHE_HIT.set(0); CACHE_MISS.set(0); SAMPLE_COUNT.set(0);
    }

    /**
     * ★★★★★★★★ 瓦片缓存。**§7639：初始容量必须【足够大】。**
     *
     * <p>为什么：{@code ConcurrentHashMap} 的锁粒度是 <b>bin</b>，而 bin 数 = 容量
     * （向上取整到 2 的幂）。默认构造器只给 <b>16 个槽 ⇒ 2 个 bin</b> ⟹ 32 个线程
     * 挤在 2 个 bin 上 ⟹ 构建【完全串行】。
     *
     * <p>P1287 实测（该问题存在时）：8 线程 wall 58,417 ms 而 <b>总 CPU 时间 426,828 ms</b>
     * —— 是单线程 140,891 ms 的 <b>3 倍</b>。也就是说并行【没省时间，反而让总工作量翻了 3 倍】。
     *
     * <p>P1286 实测：1 → 2 → 4 → 8 线程的加速比 = 1.00x → 1.06x → 1.63x → <b>2.54x（饱和）</b>。
     * 用 Amdahl 反推串行占比 = <b>60.6%</b>。
     *
     * <p>⟹ 给 {@code CACHE_LIMIT * 4} 个槽（下限 16,384）⟹ 至少 16,384 个 bin，
     * 远多于任何机器的核数 ⟹ 不同瓦片的构建【互不阻塞】，同时同一瓦片【只算一次】。
     */
    private static final ConcurrentHashMap<Long, Field> CACHE =
        new ConcurrentHashMap<Long, Field>(Math.max(16_384, CACHE_LIMIT * 4));

    /**
     * ★★★★★★★★ **§7639：按 key 分片的构建锁（64 路）。**
     *
     * <p><b>为什么需要它</b>（P1289 的线程状态采样，决定性）：8 线程扫描时
     * <b>3~6 个 worker 处于 BLOCKED</b>，栈顶是
     * {@code ConcurrentHashMap.computeIfAbsent(ConcurrentHashMap.java:1742)}。
     * 原因是：一个 WaterField tile 要 12 个气候瓦片，而<b>相邻的 WaterField tile 共享
     * 同一个气候瓦片</b> ⟹ 多个线程同时要【同一个 key】⟹ {@code computeIfAbsent}
     * 的 <b>同一个 bin 锁被持有 263 ms</b>（= 一次 {@code solve}）。
     *
     * <p><b>为什么不是「锁外求解 + putIfAbsent」</b>：那样【不阻塞】但会【重复计算】——
     * P1287 实测 8 线程的<b>总 CPU 时间 426,828 ms</b>，是单线程 140,891 ms 的 <b>3 倍</b>。
     *
     * <p><b>为什么不是「加大 ConcurrentHashMap 容量」</b>：容量只减少【不同 key 撞同一个
     * bin】；而这里的阻塞来自<b>同一个 key</b>（共享瓦片）⟹ 加大容量【无效】——
     * P1286 实测（容量 16 -> 16,384）仍为 2.48x。
     *
     * <p>⟹ 所以用 64 个独立的锁：<b>不同 key 最多 64 路并行</b>，而<b>同一个 key 只算一次</b>。
     * 为什么 64 足够：{@code CACHE} 的 key 是 (seed, tx, tz)，热点只是【当前扫描带】上的
     * 那几十个瓦片；64 路 ≫ 32 核。
     */
    private static final Object[] BUILD_LOCKS = new Object[64];
    static { for (int i = 0; i < BUILD_LOCKS.length; i++) BUILD_LOCKS[i] = new Object(); }

    /** 清空瓦片缓存。改 CELL 之后**必须**调用（几何变了）。 */
    public static void clearCache() { CACHE.clear(); }

    /** 改几何的唯一推荐入口：校验整除关系并清缓存。（探针扫参用。） */
    public static void configure(int cell) {
        if (cell <= 0 || TILE_X % cell != 0 || TILE_Z % cell != 0) {
            throw new IllegalArgumentException("SimClimate 几何不合法：CELL 必须整除 TILE_X/TILE_Z（"
                + TILE_X + "," + TILE_Z + "），得到 " + cell);
        }
        CELL = cell; clearCache();
    }

    // ==================== 一个瓦片 ====================

    /**
     * 一个瓦片的粗格点解。数组约定与 V2BiomeField 完全一致：含 1 格 halo
     * （i ∈ [-1, NX]、j ∈ [-1, NZ]），索引 (j+1)*SX + (i+1)，
     * 格点绝对坐标 = origin + i*cell + cell/2。
     *
     * <p><b>为什么这个约定是「无缝」的</b>：相邻瓦片的格点落在**同一个绝对格**上
     * （要求 cell | TILE），而每个格点的值是 (seed, x, z) 的纯函数 ⇒ 两个瓦片在共享边上
     * 插值出**逐位相同**的值，不需要任何跨瓦片通信。
     */
    private static final class Field {
        final int cell, nx, nz, sx, sz;
        final int originX, originZ;
        /** 海平面等效年平温度（K）—— 直减率**没有**含在里面，由每列按自己的高程减。 */
        final double[] tSea;
        /** 大陆度 kappa ∈ [0,1]（= Atmosphere.kappaAt）。 */
        final double[] kap;
        /** **到海岸的距离**（block，陆为负/海为正）—— 群系 continent 坐标用它（审计 D18-(c2)）。
         *  ⚠ 搜索半窗只有 {@link #COAST_FINE}（40 km）⇒ **>32 km 的点返回哨兵 ±80 km**，
         *  只够 0~40 km 的语义用。要几百 km 的量程必须用 {@link #coastFar}。 */
        final double[] coastD;
        /** **到海岸的距离（大量程）**（block）—— 海洋影响穿透权重用它（D46）。
         *  搜索半窗 {@link #COAST_FAR}（1600 km）⇒ 哨兵 ±3200 km，量程足够。 */
        final double[] coastFar;
        /** 年平风（m/s，4 季平均）。 */
        final double[] wX, wZ;
        /** 年平降水的 log10(mm/yr) —— 就是 PrecipField.mmPerDay 的 4 季平均。 */
        final double[] logP;
        /** 近地比湿（kg/kg，4 季平均）与海温距平（K）—— 诊断用。 */
        final double[] q, sst;
        /** 迎风抬升 / 背风下沉（[0,1]，tanh 饱和）。 */
        final double[] up, lee;

        Field(int cell, int nx, int nz, int ox, int oz) {
            this.cell = cell; this.nx = nx; this.nz = nz; this.sx = nx + 2; this.sz = nz + 2;
            this.originX = ox; this.originZ = oz;
            int n = sx * sz;
            tSea = new double[n]; kap = new double[n]; coastD = new double[n]; coastFar = new double[n];
            wX = new double[n]; wZ = new double[n];
            logP = new double[n]; q = new double[n]; sst = new double[n]; up = new double[n]; lee = new double[n];
        }
    }

    /**
     * **配置指纹**（审计 D2）：把这一跑所依赖的**全部可变旋钮**压成一个 long，异或进缓存键。
     *
     * <p>为什么必须有：瓦片缓存原来只按 (seed, tx, tz) 索引，于是改任何一个旋钮
     * （CELL / GRAD_STEP / SST_PROVIDER / Atmosphere 的一堆 public static / …）都**不会让旧瓦片失效**
     * —— P447 E 段实测：把 ZonalTables.SEA_ONLY_UZM 翻一下而**不清缓存**，windX 仍是旧值 −2.447146；
     * 清缓存后才变成 −3.489431（**差 1.042 m/s = 43%**）。
     * 更坏的是**部分淘汰**之后，同一条纬线上的相邻瓦片会来自不同配置 ⇒ 瓦片边界出现真实跳变。
     *
     * <p>做法：**每次 field() 现算**（几十次算术，相对 sample 的 668 ns 可忽略），
     * 异或进键 ⇒ 旋钮一变、键就变 ⇒ 自动失效，不需要调用方守纪律。
     */
    /** **public 是为了让海洋场复用同一个配置指纹**（第三步接线；避免两套 stamp 漂移）。 */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + CELL; h = h * 31 + GRAD_STEP; h = h * 31 + UPWIND_OFFSET; h = h * 31 + SLOPE_STEP;
        h = h * 31 + Double.doubleToLongBits(SLOPE_SCALE);
        h = h * 31 + Double.doubleToLongBits(Atmosphere.KAPPA_MEAN);
        h = h * 31 + Double.doubleToLongBits(Atmosphere.CELL_GAIN);
        h = h * 31 + Double.doubleToLongBits(Atmosphere.CELL_MIGRATION);
        h = h * 31 + Double.doubleToLongBits(Atmosphere.CELL_TROPIC_GATE_DEG);
        h = h * 31 + Double.doubleToLongBits(Atmosphere.PLATEAU_AMP);
        h = h * 31 + (Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE ? 1 : 0);   // ★ S1a（§385）
        h = h * 31 + (Radiation.BUCKET_BETA ? 1 : 0);                      // ★ S3（§387）
        h = h * 31 + (Radiation.ATM_REFLECT ? 1 : 0);                      // ★ §7353 大气/云反射（改了结果 ⇒ 必须进指纹）
        h = h * 31 + (com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.ENABLED ? 1 : 0);   // ★ §7363 补漏：§7347 加该开关时忘了折入（D58 准入判据）
        h = h * 31 + (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.ENABLED ? 1 : 0);   // ★ S2（§393）
        h = h * 31 + (AIRT_SEALEVEL ? 1 : 0);   // D8-b 的 A/B 开关（改了结果 ⇒ 必须进指纹）
        // §216.7：海陆年均对比进不进 p'。默认 false 时 p' 解析不变，但**打开时会变** ⇒ 必须进指纹。
        h = h * 31 + (Atmosphere.LANDS_ANNUAL_IN_PRESSURE ? 1 : 0);
        // D79：季节项的形状（单一谐波 vs 观测年循环形状）。改了结果 ⇒ 必须进指纹。
        h = h * 31 + (Atmosphere.SEASON_SHAPE_FROM_OBS ? 1 : 0);
        // D79 判读钩子：非 null 会换掉整条涡动链的纬向剖面 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.ZONAL_PROFILE_OVERRIDE == null ? 0 : 1);
        // A-ii：涡动项的纬度放置（观测 vs 模型）。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.EDDY_PLACEMENT_FROM_OBS ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(PrecipField.EDDY_MFC_REF);
        // 候选 A（§244.5）：w_eff 的两项分别取正再相加。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.SPLIT_ASCENT ? 1 : 0);
        // 候选 R-2（§249）：q 用局地真实地表温度。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.Q_AT_SURFACE_TEMP ? 1 : 0);
        h = h * 31 + (PrecipField.Q_FROM_WATER ? 1 : 0);   // ★ §7497 水系接线（湖/湿地 beta=1）
        // 候选 S-1（§251）：浅对流地板。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.SHALLOW_FLOOR ? 1 : 0);
        if (PrecipField.SHALLOW_CONDENSATE) h = h * 31 + 0x7A139L;   // §655 浅对流凝结形式
        // §264：纬向平均海平面温度取观测月表。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.ZONAL_SL_FROM_TABLE ? 1 : 0);
        // §267：柱水汽取观测月表。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.COL_WATER_FROM_TABLE ? 1 : 0);
        // §268：涡动闭合补成完整通量散度。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.EDDY_FULL_DIVERGENCE ? 1 : 0);
        // §269.5：掩码放在散度外面。改了结果 ⇒ 必须进指纹（D58）。
        h = h * 31 + (PrecipField.EDDY_MASK_OUTSIDE ? 1 : 0);
        // §7215：把涡动 MFC 并入土壤桶的强迫 A。改了结果 ⇒ 必须进指纹（D58）。
        // ★ 用【条件折入】而不是 `h*31 + (X?1:0)`：默认 false 时指纹与历史【完全一致】。
        if (com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.MFC_IN_BUCKET) h = h * 31 + 0x7A135L;
        // §567：地形已唯一（TalosField）。原先这里折入【全部旧 PlateField 旋钮】
        //   （A1/A2/A3、CONT_WAV、CS_W、WARP_W/AMP、A_PLAIN/A_MTN/FEAT_GATE、CONT_OCT、
        //    MAX_OCEAN_HALF、OCEAN_BREAK_H/FRAC、A4/A5/A6、RIFT_FRAC、NOISE_FADE、L2、
        //    SKEL_MARGIN、A7、A10、A11、ROUGH_M/G、WARP_OCT、COAST_AMP/W/OCT、A12×2、
        //    COAST_BAND、COLLIDE_H、ARC_H、TRENCH_D、RIDGE_H）；那些成员已随旧地形一起删除。
        //   ⇒ 同一个世界的「地形指纹」值会变一次（气候瓦片一次性失效，数值不变）。
        // §315/§566 的地形身份项现在是**无条件**折入：生产态（TalosField）这一段的贡献
        //   与翻转默认之后的取值**逐位相同**，所以本刀没有额外改动指纹。
        h = h * 31 + 0x7A105L;
        h = h * 31 + com.EyeOfHarmonyBuffer.sim.litho.TalosField.configStamp();
        // §401 地球验证层：换掩膜/地形 = 换配置 ⇒ 瓦片必须失效（D58 的教训）。
        // ★ 与 §315/§566 的地形身份同一套纪律：**按条件**折入 ⇒ 两者都为 null 时
        //   指纹与历史【完全一致】（严格 bit-neutral，生产不受任何影响）。
        //   用 identityHashCode 而不是 boolean：换一份掩膜也必须失效，不能只认"有没有装"。
        // §425 柱水汽 C1 光滑：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.CW_SMOOTH) {
            h = h * 31 + 0x7A114L;
        }
        // §427 涡动扩散的变量/梯度构造（用户裁决 A）：按条件折入 ⇒ 默认（0/0）指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_VAR != 0
                || com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_GRAD != 0
                || com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_GATE_MODE != 0) {
            h = h * 31 + 0x7A115L;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_VAR;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_GRAD;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_GATE_MODE;
        }
        // §7220 涡动闭合的【形式】（0 = 现状 / 1 = 闭式）。改了结果 ⇒ 必须进指纹（D58）。
        // ★ 与上面那块同一个模式：按条件折入 ⇒ 默认 0 时指纹与历史【完全一致】。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_CLOSURE_FORM != 0) {
            h = h * 31 + 0x7A136L;
        }
        // §496 BLQ 对流判据：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.BLQ_GATE) {
            h = h * 31 + 0x7A125L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.BLQ_SMOOTH_K);
        }
        // ★ §577 θ_e 阈值判据（Folkins & Braun 2003）：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        //   ⚠ 必须折入：它改变降水 ⇒ 不折入会让气候瓦片命中旧缓存（本项目已有的缺陷类）。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.BLQ_THETA_E) {
            h = h * 31 + 0x7A130L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.BLQ_SMOOTH_K);
        }
        // §480 wEff 改由 F_net/M 驱动：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.WZM_FROM_QNET) {
            h = h * 31 + 0x7A124L;
        }
        // §7303 散度的【外层】差分步长（0 = 用 gradStep ⇒ 关闭时指纹与历史完全一致，D58）。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.DIV_OUTER_STEP != 0) {
            h = h * 31 + 0x7A13BL;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.DIV_OUTER_STEP;
        }
        // §7294 M 改由真垂直积分（Neelin & Zeng 2000）：按条件折入 ⇒ 关闭时指纹与历史完全一致（D58）。
        //   数值离散参数也折入：它们会改变结果，不折入会让气候瓦片命中旧缓存。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.M_FROM_VINT) {
            h = h * 31 + 0x7A13AL;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.M_VINT_STEPS;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.M_VINT_DT);
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.M_VINT_ADI_STEPS;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.M_VINT_A1STEPS;
        }
        // §473 平流口径：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.Q_ADVECT_BUDGET) {
            h = h * 31 + 0x7A123L;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.ADVB_MAX_EVAL;
        }
        // §472 边界层水汽收支口径：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.Q_FROM_BLBUDGET) {
            h = h * 31 + 0x7A122L;
            // S628: the entrainment branch changes q => must invalidate climate tiles.
            if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.Q_BLBUDGET_SUB_ONLY) {
                h = h * 31 + 0x7A137L;
            }
            // S630: the downdraft return fraction changes q => must invalidate climate tiles.
            h = h * 31 + 0x7A138L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.BETA_DOWNDRAFT);
        }
        // §459 植被/干旱度状态：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Vegetation.ENABLED) {
            h = h * 31 + 0x7A121L;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.Vegetation.configStamp();
        }
        // §455 柱净辐射改用 ASR-OLR：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.QRAD_ASR_MINUS_OLR) {
            h = h * 31 + 0x7A120L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.OLR_K);
        }
        // §451 pzRef 进 v 的替代（§422 选项 D）：非 0 时折入 ⇒ 默认 0 时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PZREF_VZ_MODE != 0) {
            h = h * 31 + 0x7A11FL;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PZREF_VZ_MODE;
        }
        // §447 逐相位海洋 SST：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        // 为什么必须有这一位：打开后解出来的 SST' 真的变了，而 OceanField 自己的
        // configStamp 只清它自己的 ANOM/SPAN/BAND 缓存，**清不掉 SimClimate 的瓦片**
        // ⇒ 瓦片会拿旧 SST' 继续算（这正是 D58 抓到的那个缺陷的形状）。
        if (com.EyeOfHarmonyBuffer.sim.ocean.OceanField.PHASE_SEASONAL) {
            h = h * 31 + 0x7A11DL;
        }
        // §444 水汽源：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.Q_FROM_SOURCE) {
            h = h * 31 + 0x7A11CL;
            // §448 水汽源温度的季节项：非 false 时折入 ⇒ 关闭时指纹与 §444 完全一致。
            if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.SOURCE_SEASONAL_T) {
                h = h * 31 + 0x7A11EL;
            }
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.SOURCE_FETCH_L);
        }
        // §443 柱长波吸收的水汽系数：非 0 时折入 ⇒ 默认 0 时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.WVLW_K != 0.0) {
            h = h * 31 + 0x7A11BL;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.WVLW_K);
        }
        // §441 陆地反照率偏移：非 0 时折入 ⇒ 默认 0 时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Radiation.ALB_LAND_ADD != 0.0) {
            h = h * 31 + 0x7A11AL;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.Radiation.ALB_LAND_ADD);
        }
        // §440 表面阻力（Monteith）：非 0 时折入 ⇒ 默认 0 时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.RS_SURF != 0.0) {
            h = h * 31 + 0x7A119L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.RS_SURF);
        }
        // §436 纬向平均上升支是否改用观测月表：按条件折入（默认 true ⇒ 折入一次，行为已改变）。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.WZM_FROM_TABLE) {
            h = h * 31 + 0x7A118L;
        }
        // §433 Eady 增长率的温度梯度是否改用 850 hPa：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_SIGMA_T850) {
            h = h * 31 + 0x7A117L;
        }
        // §432 纬向平均温度的经向梯度是否走 PCHIP 解析导数：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_T_SMOOTH) {
            h = h * 31 + 0x7A116L;
        }
        // §422 p_ref 经向斜率是否进 v：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (!com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PZREF_IN_V) {
            h = h * 31 + 0x7A113L;
        }
        // §409 地表干暖项：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PA_DRY_WARMTH) {
            h = h * 31 + 0x7A112L;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.SOIL_GRAD_STEP;
        }
        // §407 陆海热容对比：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.SEASON_FROM_HEAT_CAPACITY) {
            h = h * 31 + 0x7A111L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.C_SEA);
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.C_LAND);
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.BETA_LAND_REF);
        }
        // §405 环流侧：cellPressure 的位相是否由陆海温差驱动。按条件折入。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.CELL_PHASE_FROM_TEMP) {
            h = h * 31 + 0x7A110L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.CELL_MIG_SENS);
        }
        // §405 S3 土壤湿度桶：按条件折入 ⇒ 关闭时指纹与历史完全一致。
        if (com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.ENABLED) {
            h = h * 31 + 0x7A10FL;
            h = h * 31 + com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.NTHETA;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.W_FC);
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture.RH_DRY);
        }
        if (PlateField.MASK != null || PlateField.ELEV != null) {
            h = h * 31 + 0x7A10EL;
            h = h * 31 + System.identityHashCode(PlateField.MASK);
            h = h * 31 + System.identityHashCode(PlateField.ELEV);
        }
        // ★★★★★★★ §527 补漏（审计面 3 的 B4）：以下 10 个开关【会改变结果】却【从未进指纹】
        //   ⇒ 翻它们瓦片缓存不失效、继续吃旧结果 ⇒ 【静默给错结果】。
        //   项目自己的纪律（Atmosphere.java:230）：「改了结果就必须让瓦片失效（D58 的教训）」。
        //   同族的 PZREF_IN_V / PA_DRY_WARMTH / SEASON_FROM_HEAT_CAPACITY / CELL_PHASE_FROM_TEMP
        //   都进了指纹 ⇒ 这 10 个是【漏网，不是设计豁免】。
        //
        //   折入约定（沿用本函数既有做法）：
        //     · 默认 false 的开关用 if (FLAG)      ⇒ 关闭时指纹与历史完全一致
        //     · 默认 true  的开关用 if (!FLAG)     ⇒ 照 PZREF_IN_V 的先例，同样保住当前指纹
        //   ⇒ 本次补漏【不改变当前指纹】，只让「翻开关」这件事被指纹看见。
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PA_NO_CELL) {
            h = h * 31 + 0x7A126L;
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.PA_NO_THERMAL) {
            h = h * 31 + 0x7A127L;
        }
        if (!com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.KEEP_EDDY_OUTSIDE) {
            h = h * 31 + 0x7A128L;
        }
        if (!com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.WZM_ITCZ_SHIFT) {
            h = h * 31 + 0x7A129L;
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.Q_NET_HEATING) {
            h = h * 31 + 0x7A12AL;
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.ZERO_F) {
            h = h * 31 + 0x7A12BL;
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave.CLOSED_LOOP) {
            h = h * 31 + 0x7A12CL;
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn.FIXED_TS) {
            h = h * 31 + 0x7A12DL;
        }
        if (com.EyeOfHarmonyBuffer.sim.ocean.SurfaceLayer.EKMAN_ENABLED) {
            h = h * 31 + 0x7A12EL;
        }
        if (!com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.SNOW_FROM_TEMP) {
            h = h * 31 + 0x7A12FL;
        }
        // ★★★★★★★ §589 复查补漏：以下 2 个开关【会改变结果】却【不在指纹里】——
        //   与 §527 补的那 10 个是【同一个缺陷类】（漏网，不是设计豁免）。
        //   · HadleyCell.ENABLED：换掉 wEff 里的 wBase（PrecipField:1742）⇒ 改降水 ⇒ 改气候瓦片。
        //   · COAST_WIND_ON：Atmosphere:265 会给风场叠加沿岸风项 ⇒ 改风应力 ⇒ 改海洋。
        //   折入约定沿用 §527（:521-524）：默认 false ⇒ 用 if (FLAG) ⇒ 关闭时指纹与历史完全一致。
        //   ⇒ 本次补漏【不改变当前指纹】，只让「翻这两个开关」这件事被指纹看见。
        if (com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.ENABLED) {
            h = h * 31 + 0x7A131L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.H_TROP);
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.T0);
        }
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.COAST_WIND_ON) {
            h = h * 31 + 0x7A132L;
        }
        // S619 (A1 scan, third configStamp gap): EDDY_DPHI_DEG_V changes the differencing step used
        // by the whole eddy chain (X-prime and MFC), so it MUST invalidate climate tiles.
        // Conditional fold => the default -1 keeps the fingerprint bit-identical to history.
        if (com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_DPHI_DEG_V > 0.0) {
            h = h * 31 + 0x7A133L;
            h = h * 31 + Double.doubleToLongBits(com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.EDDY_DPHI_DEG_V);
        }
        // S620 (P944 attribution): ZMSLK_LEGACY swaps zonalMeanSeaLevelK between the observed
        // sea-level annual table and the old reconstruction. It feeds airT = (tSea - tzm)/AIRT_SCALE
        // (SimClimate:634/:775) AND the OceanField thermal-wind gradient, so it MUST invalidate
        // climate tiles. Conditional fold => the default (false) keeps production bit-identical.
        if (com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.ZMSLK_LEGACY) {
            h = h * 31 + 0x7A134L;
        }
        return h;
    }

    private static Field field(int worldSeedInt, int tx, int tz) {
        long key = WindowKey.of(worldSeedInt, tx, tz) ^ configStamp();
        Field f = CACHE.get(key);
        if (f != null) { CACHE_HIT.incrementAndGet(); return f; }
        CACHE_MISS.incrementAndGet();
        if (CACHE.size() >= CACHE_LIMIT) evictAny();
        // ★★★★★★★★ §7638（性能卡点）：**solve() 必须在锁外**。
        //
        // 旧式：return CACHE.computeIfAbsent(key, k -> solve(...));
        //   ⚠ ConcurrentHashMap.computeIfAbsent 【会持有该 key 所在 bin 的锁】直到回调返回。
        //     而 solve() 要 ~263 ms（84 节点 x 4 季 mmPerDay）⟹ 锁被持有 263 ms。
        //     更要命的是：CACHE 有 8,192 个 entry 但【初始容量只有 16】⟹ 只有 16 个 bin
        //     ⟹ 多个不同瓦片【共享同一个 bin】⟹ 它们的构建【被迫串行】。
        //   P1285 实测：2 线程 1.14x、4 线程 2.35x（理想 2x/4x）⟹ 串行瓶颈确认。
        //
        // 新式（与 WaterField.cell() 同款）：
        //   get（无锁）-> miss 则【在锁外】solve -> putIfAbsent（只锁一瞬）-> 用先到的那个。
        //   代价：两个线程同时构建【同一个】瓦片时白算一次（正确性不受影响）。
        // ★★★★★★★★ §7639：double-checked + 【per-key 分片锁】。
        //   P1289 实测：用 computeIfAbsent 时 8 线程里有 3~6 个 BLOCKED 在它的 bin 锁上
        //   （因为相邻 WaterField tile 共享气候瓦片 ⟹ 多线程要【同一个 key】）。
        //   ⟹ 这里按 key 分片到 64 个锁：不同的 key 最多 64 路并行，同一个 key 只算一次。
        Object lock = BUILD_LOCKS[(int) (key & 63L)];
        synchronized (lock) {
            Field f2 = CACHE.get(key);                 // 双检：可能已被别的线程填好
            if (f2 != null) { CACHE_HIT.incrementAndGet(); return f2; }
            Field fresh = solve(worldSeedInt, tx, tz); // ★ 只对【同一个 key】串行
            CACHE.put(key, fresh);
            return fresh;
        }
    }

    /** 淘汰一个**任意**瓦片 —— 与 V2BiomeField.evictAny 同款（策略不是杠杆，容量才是）。 */
    private static void evictAny() {
        java.util.Iterator<Long> it = CACHE.keySet().iterator();
        if (it.hasNext()) { CACHE.remove(it.next()); }
    }

    // ==================== 单点查询（热路径） ====================

    /**
     * 与旧 ClimateCoords.sample **逐字段同签名**的替代实现。
     *
     * <p>⚠ 参数 oro 故意不读（理由见类注释「为什么不读 oro」）：它来自旧陆海场，
     * 而第 1 步之后世界的海陆由 PlateField 决定。保留参数只为调用点零改动。
     */
    public static ClimateCoords.Coords sample(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        SAMPLE_COUNT.incrementAndGet();
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        long seed = SimTerrain.seedOf(worldSeedInt);

        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1), k10 = k00 + 1, k01 = k00 + f.sx, k11 = k01 + 1;

        double kap = bl(f.kap, k00, k10, k01, k11, tx, tz);
        double coastD = bl(f.coastD, k00, k10, k01, k11, tx, tz);
        double tSea = bl(f.tSea, k00, k10, k01, k11, tx, tz);
        double logP = bl(f.logP, k00, k10, k01, k11, tx, tz);
        double wX = bl(f.wX, k00, k10, k01, k11, tx, tz);
        double wZ = bl(f.wZ, k00, k10, k01, k11, tx, tz);
        double qq = bl(f.q, k00, k10, k01, k11, tx, tz);
        double sst = bl(f.sst, k00, k10, k01, k11, tx, tz);
        double up = bl(f.up, k00, k10, k01, k11, tx, tz);
        double lee = bl(f.lee, k00, k10, k01, k11, tx, tz);

        // 地表温度 = 插值出来的海平面温度 - 该列**自己的**精确高程 x 直减 x kappa
        double elev = PlateField.elevationWithCell(x, z, seed, PlateField.PLATE_CELL);
        double tSfc = tSea - Atmosphere.GAMMA * Math.max(0.0, elev) * kap;

        double lat = WorldContract.latOf(z);
        // ⚠ 必须用**本世界自己的**纬向平均（κ = ⟨κ⟩ 处的海陆混合），不是地球的 T_zm ——
        //   否则 airT = (tSea - tzm)/AIRT_SCALE 会混进「地球在那个纬度的陆地占比」这一层。
        double tzm = Atmosphere.zonalMeanSeaLevelK(lat);

        ClimateCoords.Coords c = new ClimateCoords.Coords();
        c.temp = clamp01(TEMP_LO + (tSfc - T_LO_K) * TEMP_SLOPE);
        c.moist = clamp01(MOIST_A * logP - MOIST_B);
        // 审计 D18（用户裁决 (c)）：continent 用**小半径**陆地占比（岸线 = 0，恢复旧语义），
        // 不再用大尺度 κ —— 它在岸线上恒为 0.5，且旧门的带宽（0.008）比一个量化台阶（0.0104）还窄。
        c.continent = clamp01(-coastD / COAST_FINE);
        c.windX = wX; c.windZ = wZ;
        c.up = up; c.lee = lee; c.sstAnom = sst;
        c.q = qq;
        c.mar = clamp01(1.0 - kap);
        // ⚠⚠ 审计 D8 的根因分析与一次**失败的修复**（2026-09-13，已撤回，记账）：
        // 现状 (tSfc - tzm)/AIRT_SCALE 把**含海拔直减的地表温度**与**海平面**纬向平均相减 ——
        // 两个不同高度层在相减 ⇒ 大陆点被 -GAMMA*elev*kappa 系统性压低 ⇒ airT 恒负。
        // P458 实测后果：airMass 四类里「大陆性+暖」只占 **1.3%**（几乎为空），
        // 而两个阈值 mar>=0.5 与 airT>=0 都落在中位数附近 ⇒ 世界被两个刀切面分成四类。
        //
        // 我改成 (tSea - tzm)/AIRT_SCALE（同层相比），P458 复测四类变成 21.0/19.0/35.4/24.6 —— 看起来修好了。
        // **但那是假象**：tSea = T_zm + (1-kappa)*SST'，而 **SST_PROVIDER = null** ⇒ tSea == T_zm
        // ⇒ airT 只剩「双线性插值 vs 精确纬度」的残差（~1e-5）⇒ **符号是浮点噪声**，
        // 而 airMass 的类别 2/3 正是由这个符号决定 ⇒ 比改之前更糟。**已撤回。**
        //
        // ⇒ **真正的结论**：airMass 的「暖/冷」轴**结构上没有物理内容** ——
        // 年平温度场 tSea = T_zm + (1-kappa)*SST'，在没有 SST 时**完全没有海陆热力对比**；
        // 对比全都活在**季节项 A(phi, kappa)** 里，而 tSea 恰恰把季节项减掉了。
        // **⇒ 这属于第 3 步（接洋流/SST）的接线缺口，不是今天能修的 bug。**
        //
        // ⚠⚠ **2026-09-13：第 3 步已经接上了（§162）⇒ D8 的前提变了，必须重新裁决。**
        //   - 上面那段的前提是「SST_PROVIDER = null ⇒ tSea == T_zm」；现在
        //     Atmosphere.SST_PROVIDER 是**活的**（OceanField），tSea = T_zm + (1-k)*SST'，
        //     **有海陆/洋盆热力对比了**（副热带西边界流 ±4~7 K）。
        //   - 所以「airT 只是浮点噪声」这个结论**不再成立**；但 D8 的**原始**批评仍然成立：
        //     tSfc 含 -GAMMA*max(0,elev)*kap，与**海平面**的 tzm 相减 ⇒ 大陆点被系统性压低。
        //   - ⇒ 现在是一个**有物理内容但也有系统性偏差**的轴。要不要改成同层相比、
        //     改了之后四类占比是否合理 —— **必须先用探针量出来再裁决**（P473），不许拍脑门。
        // ⚠ D8-b 的 **A/B 开关**（用户裁决 4-C：「重新量四类再决定」）：
        //   false（默认，现状）= tSfc - tzm —— 含海拔直减的地表温度 与 海平面纬向平均 相减；
        //   true             = tSea - tzm —— **同层相比**（两个都是海平面等效）。
        // 它**进 configStamp()**（改了结果就必须让瓦片失效 —— D58 的教训）。
        c.airT = clamp(((AIRT_SEALEVEL ? tSea : tSfc) - tzm) / AIRT_SCALE, -1.0, 1.0);
        double qs = PrecipField.qSat(tSfc);
        c.dry = qs > 0.0 ? clamp01(1.0 - qq / qs) : 1.0;
        c.bandD = WorldContract.bandD(z);
        c.airMass = c.mar >= 0.5 ? (c.airT >= 0.0 ? 0 : 2) : (c.airT >= 0.0 ? 1 : 3);
        double sp = Math.hypot(wX, wZ);
        c.onshore = sp > 1.0e-6
            && !PlateField.isLandWithCell(x + (int) (wX / sp * UPWIND_OFFSET),
                                          z + (int) (wZ / sp * UPWIND_OFFSET), seed, PlateField.PLATE_CELL);
        return c;
    }

    /**
     * 该点的**大陆度 κ**（= {@code Atmosphere.kappaAt}，走同一个瓦片缓存 + 双线性插值）。
     *
     * <p>⚠ <b>D46</b>：**不要把 {@link #coords} 的 {@code out3[2]} 当成 κ 用** ——
     * 那是 {@code continent}（= {@code clamp01(-coastD / COAST_FINE)}，一个 **40 km 就饱和**的
     * **到岸距离**坡）。两者在**岸线上**分别是 **0.5**（κ：岸线 landFraction = 0.5）与 **0**。
     *
     * <p>成本：一次瓦片查表 + 4 次双线性 ⇒ **纳秒级**（对比 {@code Atmosphere.kappaAt} 的 ~60 us，
     * 后者会让 {@code composeColumn} 的 347 ns/列 慢 170 倍）。
     */
    public static double kappaAt(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1);
        return bl(f.kap, k00, k00 + 1, k00 + f.sx, k00 + f.sx + 1, tx, tz);
    }

    /**
     * **海洋影响向内陆的穿透尺度（m）** —— 雪线的海陆季节振幅过渡用它（D46 修复）。
     *
     * <p><b>观测依据（我自己取的，可复现）</b>：ERA5 月平均 2 m 气温（2015 年 12 个月，0.25°），
     * 经 NOAA/UH APDRC OPeNDAP：
     * <code>https://apdrc.soest.hawaii.edu/dods/public_data/Reanalysis_Data/ERA5/monthly_2d/Surface.ascii</code>
     * 变量 <code>t2m</code> / <code>sst</code>（sst 缺测 = 陆地，做掩膜）。
     * 取北美大西洋/墨西哥湾一侧（排除太平洋岸）的年较差随离岸距离：
     * <pre>
     *   30~35N:  0-50 km 19.2 K | 50-100 19.9 | 100-200 21.0 | 200-300 22.0 | 300-500 22.8
     *   ⇒ 拟合 R(d) = Rinf - (Rinf-R0)exp(-d/L) 得 **L ≈ 200 km**
     * </pre>
     * 一条更南/更北的断面被山脉与哈德逊湾混淆，只取这一条干净的。
     * ⚠ 局限：1 年、0.25°、距离用「最近海洋」、山脉未剔除 ⇒ 只当**量级**用，
     * 用户批准的取值带是 **200~400 km**，取观测值 **200 km**。
     */
    public static double MARITIME_SCALE = 200_000.0;

    /** **大量程海岸距离的搜索半窗（m）** —— 必须 >= 3 x {@link #MARITIME_SCALE}（D46 / E20）。 */
    public static int COAST_FAR = 1_600_000;

    /**
     * **海洋影响权重**：海岸 = 0（完全海洋性），内陆 ≳3 个 {@link #MARITIME_SCALE} 后 → 1（完全大陆性）。
     * 纯距离函数、C∞、无阈值 if。**这是 D46 的修复量** —— 它与 {@code continent}
     * （40 km 就饱和的到岸距离坡）和 κ（{@code COAST_BLEND} = 800 km 的陆海混合）**都不是同一个量**。
     */
    public static double maritimeInland(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1);
        // ⚠ 必须用 coastFar（大量程），不能用 coastD —— 后者 >32 km 就是哨兵（E20）。
        double d = bl(f.coastFar, k00, k00 + 1, k00 + f.sx, k00 + f.sx + 1, tx, tz);
        return 1.0 - Math.exp(-Math.max(0.0, -d) / MARITIME_SCALE);
    }

    /** 只取气候坐标（省一个 Coords 分配；探针/出图用）。 */
    public static void coords(int x, int z, int worldSeedInt, double[] out3) {
        ClimateCoords.Coords c = sample(x, z, worldSeedInt, null);
        out3[0] = c.temp; out3[1] = c.moist; out3[2] = c.continent;
    }

    // ==================== 离线求解 ====================

    private static Field solve(int worldSeedInt, int tx, int tz) {
        long t0 = System.nanoTime();
        SOLVE_COUNT.incrementAndGet();
        long seed = SimTerrain.seedOf(worldSeedInt);
        int cell = CELL;
        int nx = TILE_X / cell, nz = TILE_Z / cell;
        Field f = new Field(cell, nx, nz, tx * TILE_X, tz * TILE_Z);
        int pc = PlateField.PLATE_CELL;
        // ★ §362/§373 刀 1（用户裁决 B）：本瓦片的 84 个节点会把同一个 (x,z) 的 {kappa,elev}
        //   反复要 50+ 次 —— P555 实测 54 次/节点、kappaAt = 95.77 us ⇒ 5.17 ms/节点，
        //   占 13.63 ms/节点的 38%。windAt / pressureAnomaly / surfaceTemp **本来就走**
        //   kappaElev，只是记忆化没开（它原本只在 OceanField.solveRow 里开）。
        //   ★ §373：memo 的逐位不变已由 P566 定案（全部模板点预热后 0/750）。
        //   thread-local ⇒ 对其它线程零影响；beginMemo 已开着时返回 false，嵌套安全。
        boolean memoCreated = Atmosphere.beginMemo();
        try {
            for (int j = -1; j <= nz; j++) {
                int z = f.originZ + j * cell + cell / 2;
                double lat = WorldContract.latOf(z);
                double tzm = Atmosphere.zonalMeanSeaLevelK(lat);
                for (int i = -1; i <= nx; i++) {
                    int x = f.originX + i * cell + cell / 2;
                    int k = (j + 1) * f.sx + (i + 1);
                    NODE_COUNT.incrementAndGet();
                    solveNode(f, k, x, z, lat, tzm, seed, pc);
                }
            }
        } finally {
            Atmosphere.endMemo(memoCreated);
        }
        SOLVE_NANOS.addAndGet(System.nanoTime() - t0);
        return f;
    }

    /** 单个粗格点。抽成方法只为让循环体可读（内联由 JIT 负责）。 */
    private static void solveNode(Field f, int k, int x, int z, double lat, double tzm, long seed, int pc) {
        double kap = Atmosphere.kappaMemo(x, z, seed, pc);   // ★ §373：与 kappaAt 逐位相同（P566 已证）
        double elev = PlateField.elevationWithCell(x, z, seed, pc);
        // ⚠⚠ 审计 D56（2026-09-13，接线当天抓到的口径分裂）：
        // 这里原来是 sstAnomAt(x, z)，读的是**已废弃**的 SimClimate.SST_PROVIDER（恒 null ⇒ 恒 0）。
        // 而同一个函数的 tSea（下面几行）走的是 Atmosphere.surfaceTemp ⇒ 含**活的**
        // Atmosphere.SST_PROVIDER。于是**接线之后**，同一个瓦片节点里：
        //   tSea  = T_zm + (1-k)*SST'      （有 SST'）
        //   tSl   = T_zm + 季节项 + 0       （没有 SST'）→ 进 PrecipField.moisture → f.q
        //   f.sst = 0                       → 进 ClimateCoords.sstAnom → 群系/湿润
        // 接线之前两者都是 0，所以「一致」；接线之后**必然分裂**。这是接线自己打开的口径口子。
        // ⇒ 现在本类所有 SST' 读取都走 Atmosphere.sstAnom 这**唯一**注入点。
        double sstA = Atmosphere.sstAnom(x, z);

        // 海平面等效**年平**温度：
        //   surfaceTemp(th) = T_zm + seasonalAnomaly(th) - GAMMA*max(0,elev)*kappa + (1-kappa)*SST'
        //   Sum_q seasonalAnomaly(th_q)/4 == 0  ⇒  年平 = T_zm + (1-kappa)*SST'
        // 所以取一季的 surfaceTemp 后要**减掉**该季的季节项、加回节点自己的直减项。
        // ⚠ 漏掉 seasonalAnomaly 这一项会让 tSea 混进 ±A(phi)（中纬陆地最大 18.7 K）的
        //    假季节偏移（P383 实测 4 季平均与它差 42.1 K）。
        // 海平面等效年平温度 = T_zm + (1-kappa)*SST'。
        // ⚠⚠ 2026-09-13 修正（审计 D3）：这里原来还有一项「+ (1.0 - kap) * sstA」，
        // 但 Atmosphere.surfaceTemp 的返回式里**已经含** (1-k)*sstAnom(x,z)（Atmosphere.java:364），
        // 他没有被下面的减法减掉 ⇒ 两个 provider 都设时得到 **2(1-kappa)*SST'**。
        // P447 F 段实测：kappa=0 处两个都设 +5 K ⇒ SimClimate = 309.145996 K，
        // 而生产 surfaceTemp 年平 = 304.145996 K，**差恰好 +5.000 K**。
        // 触发条件正是类注释（见 SST_PROVIDER 那一段）推荐的第 3 步接法「同时设置两处」。
        // ⇒ 现在 SST 只从 Atmosphere.SST_PROVIDER **单点**注入；SimClimate.SST_PROVIDER 已废弃。
        double tSea = Atmosphere.surfaceTemp(x, z, seed, pc, SEASON[0])
                    - Atmosphere.seasonalAnomaly(lat, kap, SEASON[0])
                    + Atmosphere.GAMMA * Math.max(0.0, elev) * kap;
        f.kap[k] = kap;
        f.coastD[k] = PlateField.coastDistanceNew(x, z, seed, pc, COAST_FINE);
        // ⚠ E20（我自己的错误，P461 抓到）：coastD 的半窗只有 40 km ⇒ 它**根本量不到** 200 km 的
        // 海洋影响尺度（>32 km 全是哨兵 ±80 km）⇒ 我第一版 maritimeInland 会把整个内陆压到 0.33。
        // ⇒ 另算一个大量程的距离场（每个瓦片节点多一次 coastDistanceNew，约 75 us x 66 节点
        //   = 5 ms/瓦片，相对瓦片求解的 0.3~3 s 可忽略）。
        f.coastFar[k] = PlateField.coastDistanceNew(x, z, seed, pc, COAST_FAR);
        f.tSea[k] = tSea;
        f.sst[k] = sstA;

        // 年平风（必须真取 4 季平均：p' 的季节项与 U_zm 的 1/7 月表插值都随 theta 变）
        double ux = 0.0, uz = 0.0;
        for (int s = 0; s < SEASON.length; s++) {
            double[] w = Atmosphere.windAt(x, z, seed, pc, SEASON[s], GRAD_STEP);
            ux += w[0] * 0.25;
            uz += w[1] * 0.25;
        }
        f.wX[k] = ux; f.wZ[k] = uz;

        // 迎风抬升 / 背风下沉：tanh 饱和（零阈值；SLOPE_SCALE = 2% 坡度 -> 0.76）
        int st = SLOPE_STEP;
        double gx = (PlateField.elevationWithCell(x + st, z, seed, pc)
                   - PlateField.elevationWithCell(x - st, z, seed, pc)) / (2.0 * st);
        double gz = (PlateField.elevationWithCell(x, z + st, seed, pc)
                   - PlateField.elevationWithCell(x, z - st, seed, pc)) / (2.0 * st);
        double sp = Math.hypot(ux, uz);
        double dot = sp > 1.0e-9 ? (ux * gx + uz * gz) / sp : 0.0;
        f.up[k] = sat(dot);
        f.lee[k] = sat(-dot);

        // 年平降水 = **生产降水场本身**的 4 季平均（mm/yr）；顺带记 4 季平均比湿。
        double hUp = PrecipField.upwindElev(x, z, seed, pc, ux, uz);
        double pMmDay = 0.0, qSum = 0.0;
        for (int s = 0; s < SEASON.length; s++) {
            double th = SEASON[s];
            pMmDay += PrecipField.mmPerDay(x, z, seed, pc, th, GRAD_STEP) * 0.25;
            double tSl = tzm + Atmosphere.seasonalAnomaly(lat, kap, th) + (1.0 - kap) * sstA;
            qSum += PrecipField.moisture(tSl, kap > 0.0 ? hUp : 0.0, kap) * 0.25;
        }
        f.q[k] = qSum;
        double mmYr = pMmDay * 365.25;
        f.logP[k] = Math.log10(Math.max(mmYr, P_MIN_MM_YR));

        // ★★ S1a + S3（§385/§387）：把【诊断】皮温换成【表面能量平衡 + 地表湿润度 beta】解出的皮温。
        //   为什么放在最后：本块的 beta = min(1, P/E_p)（Radiation 稳态互补桶）需要 pMmDay（上面刚算完）。
        //   为什么用年平风：T_s -> p' -> 风 -> T_s 本来会成环；ux/uz 已经算好 ⇒
        //   这里【不新增任何 windAt 调用】，也不引入新的迭代。
        //   §387 定案：沙漠的「干」必须由 beta 表达（不是低 q_a）—— P569 实测降 q_a 反而增大蒸发。
        //   两个开关默认 false ⇒ 整段跳过 ⇒ 逐位不变。
        if (Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE) {
            double chv0 = Radiation.bulkCoeff(kap, Math.hypot(ux, uz));
            double alb0 = Radiation.ALB_SEA
                        + Atmosphere.clamp01(kap) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
            double absS0 = Radiation.absSolarSurface(lat, Atmosphere.subsolarLat(SEASON[0]), alb0);   // §7353 单源
            double qa0 = PrecipField.moisture(tSea, 0.0, kap);
            double beta0 = 1.0;
            if (Radiation.BUCKET_BETA) {
                // E_p = beta=1 时的潜在蒸发（Manabe eq.19），与 pMmDay 同为 mm/day。
                double tsPot = Radiation.skinTempLand(absS0, tSea, qa0, chv0, 1.0);
                double epMmDay = Radiation.potentialEvapMmDay(tsPot, qa0, chv0);
                beta0 = Radiation.bucketBeta(pMmDay, epMmDay);
            }
            double tsE = Radiation.skinTempLand(absS0, tSea, qa0, chv0, beta0);
            tSea = tSea + Atmosphere.clamp01(kap) * (tsE - tSea);
            f.tSea[k] = tSea;
        }
    }

    // sstAnomAt(x,z) 已删除（审计 D56）：它读的是废弃的 SimClimate.SST_PROVIDER（恒 0），
    // 是「两个注入点」这个错误设计的最后一处残留。全部改走 Atmosphere.sstAnom。

    // ==================== 原始物理量的只读访问器 ====================

    /**
     * **该点的地表温度（K）** —— 与生产 Atmosphere.surfaceTemp(x,z,seed,cell,theta) 的
     * **年平**在格点上**逐位相等**（P383 §1 端到端实测 max|d| = 1.7e-05 K）。
     *
     * <p>⚠ **含每列自己的海拔直减**，不是格点的海平面值 —— 否则雪线会错到山顶上。
     * 可以直接替代「按高度」的雪线判据：
     * <pre>c.snow = SimClimate.surfaceTempK(x, z, worldSeedInt) &lt; 273.15;</pre>
     *
     * <p>与 {@link #sample} **共用同一份瓦片缓存**（不会算第二遍）；无分配。
     */
    /**
     * ★★★★★★★★ §7612：**年平降水（mm/yr）** —— f.logP 的双线性采样。
     *
     * <p>f.logP 就是「PrecipField.mmPerDay 的 4 季平均」的 log10（见 Field#logP 的 javadoc
     * 与 solveNode 里的赋值）。而它【已经在瓦片里】⟹ 本访问器**不触发任何新计算**，只做双线性插值。
     *
     * <p>这是【水系层】需要的量：产流深 Rr 的降水项就是它。走这里而不是再调
     * {@code PrecipField.mmPerDay}，是因为后者**每次新坐标要 13-17 ms**
     * （PrecipField:1343 逐字：P1106 实测 mmPerDay = 13.086 ms/次），
     * 而本访问器是 **warm 0.16 ms/次**（P1245 实测）。
     *
     * <p>与 {@link #sample} **共用同一份瓦片缓存**；无分配。
     */
    public static double annualPrecipMmPerYear(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1), k10 = k00 + 1, k01 = k00 + f.sx, k11 = k01 + 1;
        return Math.pow(10.0, bl(f.logP, k00, k10, k01, k11, tx, tz));
    }

    /** 大陆度 kappa 的双线性采样（与 kappaAt 同源，但走瓦片缓存）。 */
    public static double kappaFromTile(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1), k10 = k00 + 1, k01 = k00 + f.sx, k11 = k01 + 1;
        return bl(f.kap, k00, k10, k01, k11, tx, tz);
    }

    public static double surfaceTempK(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1), k10 = k00 + 1, k01 = k00 + f.sx, k11 = k01 + 1;
        double kap = bl(f.kap, k00, k10, k01, k11, tx, tz);
        double tSea = bl(f.tSea, k00, k10, k01, k11, tx, tz);
        double elev = PlateField.elevationWithCell(x, z, SimTerrain.seedOf(worldSeedInt),
                                                  PlateField.PLATE_CELL);
        return tSea - Atmosphere.GAMMA * Math.max(0.0, elev) * kap;
    }

    /**
     * **该点的风（m/s）**：out[0] = u（纬向，正 = 东）、out[1] = v（经向，正 = 北）。
     *
     * <p>⚠ 是**年平风**（4 个等间距季节相位的算术平均），不是某一季的风 ——
     * 群系必须静态，云/粒子的"斜度"用年平才有意义；要季节风请直接调
     * Atmosphere.windAt(...,theta,500_000)（那会绕开缓存，贵 ~280 us）。
     *
     * <p>与 {@link #sample} **共用同一份瓦片缓存**；不分配（调用方给 out2）。
     */
    public static void windAt(int x, int z, int worldSeedInt, double[] out2) {
        Field f = field(worldSeedInt, Math.floorDiv(x, TILE_X), Math.floorDiv(z, TILE_Z));
        double fx = (x - f.originX) / (double) f.cell - 0.5;
        double fz = (z - f.originZ) / (double) f.cell - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = i < -1 ? -1 : (i > f.nx ? f.nx : i);
        j = j < -1 ? -1 : (j > f.nz ? f.nz : j);
        int k00 = (j + 1) * f.sx + (i + 1), k10 = k00 + 1, k01 = k00 + f.sx, k11 = k01 + 1;
        out2[0] = bl(f.wX, k00, k10, k01, k11, tx, tz);
        out2[1] = bl(f.wZ, k00, k10, k01, k11, tx, tz);
    }

    // ==================== 小工具 ====================

    private static double bl(double[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = g[k00], v10 = g[k10], v01 = g[k01], v11 = g[k11];
        return (v00 * (1.0 - tx) + v10 * tx) * (1.0 - tz) + (v01 * (1.0 - tx) + v11 * tx) * tz;
    }

    /** 单侧饱和：v<=0 给 0，正的一侧 tanh 饱和到 1（与旧实现 clamp01(max(0,dot)/S) 同类，但无硬拐点）。 */
    static double sat(double v) { return v <= 0.0 ? 0.0 : Math.tanh(v / SLOPE_SCALE); }

    static double clamp01(double v) { return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v); }

    static double clamp(double v, double lo, double hi) { return v < lo ? lo : (v > hi ? hi : v); }
}
