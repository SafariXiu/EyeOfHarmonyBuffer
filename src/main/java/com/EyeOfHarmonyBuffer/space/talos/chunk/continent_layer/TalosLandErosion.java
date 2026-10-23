package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.sim.Frozen;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * TalosLandErosion —— **地貌演化层（LEM / stream power）**。
 *
 * <h2>★★★★★ 2026-10-13 用户裁决：沉积项【采用 A · 保持关闭】—— 世界 = 7d85262（逐位等价，已实测）</h2>
 *
 * <p><b>现状一句话</b>：沉积项 {@code D = G*Qs/A}（Davy &amp; Lague 2009，出处见 {@link #SED_G} 的 javadoc）
 * 已<b>完整实装且有文献出处</b>，但默认 {@link #SED_G} {@code = 0.0} ⟹ 那段代码<b>一行都不执行</b>。
 *
 * <p><b>等价性是实测的，不是推理的</b>：把 7d85262 版的本文件单独编成一棵影子类树，
 * 用同一个基线探针（不引用任何新开关）跑同一种子，两个种子的 zSum 与 7d85262
 * <b>吻合到小数点后 4 位</b>：
 * <pre>
 *   种子 1022228679：湖 2.60 / 湿地 11.00 / 洼地 13.60 / 河 26.0 / &lt;300m 82.9 / zMax 5198.5 / zSum 9121178.1450
 *   种子 42        ：湖 3.50 / 湿地 10.31 / 洼地 13.82 / 河 26.3 / &lt;300m 80.1 / zMax 4813.4 / zSum 8598354.5582
 *   ⟹ 新旧两版逐项相同
 * </pre>
 * <p>⚠ 唯一的运行时差异：{@code configStamp()} 多了 7 个键（3 个 P1 + 4 个既存漏键修复）
 * ⟹ 部署后第一次查询会重算瓦片（一次性），<b>数值不变</b>。
 *
 * <h3>★★★ 为什么它在【本仓】不好用 —— 下一轮调研的出发点（逐条都有实测）</h3>
 * <ol>
 *   <li><b>和「填洼」没有耦合</b>：{@code D = G*Qs/A} 是<b>面积平均</b>量，对局部洼地几何毫无感知。
 *       真实世界的填洼靠「输运长度在静水中趋近 0 ⟹ 泥沙就地落淤」，本仓<b>没有这个机制</b>。
 *       实测（口径 1：沉积全境、侵蚀仍门控）：湖 2.60 → 2.47、湿地 11.00 → 10.99
 *       —— <b>几乎零作用</b>（洼地与周围同涨，相对深度不变）。</li>
 *   <li><b>不动点方向与直觉相反</b>：本仓隐式解的平衡态是 {@code z - zd = up/al + D(1+al)/al}
 *       ⟹ 沉积<b>抬高</b>平衡态的相对高差，而不是抹平它。</li>
 *   <li><b>门控架构让它没有干净的作用面</b>（本仓特有）：
 *       口径 0 的沉积量被 {@code act=0} 整格丢弃（零作用）；
 *       口径 1 只沉积不侵蚀 ⟹ <b>质量不守恒</b>，实测 {@code G >= 1} 时 zMax 失控到 57.8 万 ~ 123 万米；
 *       口径 2 必须解冻侵蚀，而解冻后的主导效应变成<b>平原被整体削平</b>
 *       （坡度 p50 27.6 → 4.1 m/km），起作用的不是沉积。</li>
 *   <li><b>「湖」是事后分类，不是状态量</b>：本仓没有水体状态，湖 = {@code filled - z > }{@link #LAKE_MIN_FILL}
 *       的阈值判定 ⟹ 平原一旦被削到<b>没有任何盆地深过 0.6 格</b>，湖就归零。
 *       实测：{@code G = 1e-300}（沉积严格为零）时湖已经是 0.02%，<b>与 G 几乎无关</b>。</li>
 *   <li><b>缺少坡面扩散</b>（下一轮首选假设，<b>尚未验证</b>）：真实平原的微洼地主要靠
 *       <b>坡面蠕移 / 漫流</b>填平，本仓只有河流项。</li>
 *   <li><b>{@code G} 的三个物理量在本仓都不存在</b>：{@code G = d* * vs / p0}（跃移长度 x 沉速 / 降水）
 *       ⟹ G 只能当裸旋钮扫，无法从本仓其他参数导出或交叉验证。</li>
 *   <li><b>没有物理时间步</b>：{@code ITER = 200} 是「步」，{@code DTU}/{@code UREF} 是标定值
 *       ⟹ 沉积速率没有时间标定，G 的最优值只能扫描定，不能从物理推。</li>
 * </ol>
 * <p>★ 完整实测（5 种子 x 2 配置 + 单种子 10 档 + 判别实验 + 出图）：
 * {@code K:\moder\Talos2\待裁决\345-P1沉积项-落地与实测-真杠杆是门控.md}
 * <p>★ 回滚语义：{@code SED_G = 0.0} 永远逐位回到 7d85262；口径 2 分支<b>只在 SED_G &gt; 0 时</b>生效
 * （否则逐位等于 §340 那个已证伪的写法）。
 *
 * <h2>★★★★★ 2026-10-11：本类【就是】生产环境的河道与水系分类系统（定位声明）</h2>
 *
 * <p>⚠ 2026-10-12（待裁决/331）：旧的 {@code sim.hydro.WaterField} 已<b>整类删除</b>
 * （它把 30% 的陆地判为湖，是错的）。湖 / 湿地的判据现在<b>只在本类</b>：
 * <pre>
 *   湖   ：filled - z &gt; {@link #LAKE_MIN_FILL}
 *   湿地 ：0 &lt; filled - z &lt;= {@link #LAKE_MIN_FILL}
 *   入口 ：{@link #isLake}（建瓦片，诊断用）/ {@link #isLakePeek}（不建瓦片，生产用）
 * </pre>
 * 量化河宽 / 流域也用本类（{@link #riverWidthBlocks} / {@link #accAt}）。
 *
 * <h3>它是什么</h3>
 * 在 {@link TalosLandField} 的「抬升场」上跑 <b>Braun &amp; Willett (2013) 的 O(n) 隐式
 * stream power</b>，演化到稳态，得到<b>自带树枝状河谷网络</b>的地形。
 *
 * <h3>为什么需要它（实测依据）</h3>
 * 旧的 {@code WaterField} 在 12 km 窗口里算 D8 汇流，实测：
 * <ul>
 *   <li>河长上限 = 窗口 = <b>12 km</b></li>
 *   <li>{@code isRiver} = <b>0%</b>、{@code isLake} = <b>0%</b>（门槛是 10 km 网格时代的，全部失效）</li>
 *   <li>流向脆弱：正负 0.1 格扰动改 10.5% 的流向</li>
 * </ul>
 * LEM 把地形演化到「完全排水 + 坡度由汇流面积决定」的不动点，
 * <b>河网成为地形的副产品</b>。
 *
 * <h3>数学</h3>
 * <pre>
 *   dz/dt = U - K * A^m * S^n                        （stream power，n = 1）
 *   z_i = (z_i + dtU_i + alpha * z_d) / (1 + alpha)  （Braun &amp; Willett 隐式）
 *     alpha = DK * A_i^m / L_i
 *     dtU_i = DTU * UREF * ((U_i - SEA) / UREF)^GAMMA
 * </pre>
 * ★ <b>遍历顺序必须是【高程升序】（出海口先）</b>，因为 z_i 依赖【下游的新值】。
 * ★ <b>zd = max(z_downstream, SEA)</b>：海平面是侵蚀基准面。
 *   不加这个 max 会把海岸带陆地一路拉到深海（实测 18.65% 的陆地被削穿）。
 * ★ 隐式格式<b>不产生坑</b>（新值是正权重加权平均，下坡关系被保持；
 *   实测洼地 3165 到 0 并保持）。
 *
 * <h3>窗口与瓦片</h3>
 * <ul>
 *   <li>求解窗口 {@link #SOLVE_CELLS} = 768 格（768 km）—— 远大于大陆特征尺度 125 km。</li>
 *   <li>有效输出 {@link #OUT_CELLS} = 512 格 —— 去掉两侧 buffer 后的中心区。</li>
 *   <li>buffer {@link #BUFFER_CELLS} = 128 格 —— 实测 buffer 64 km 时中心区 39.63% 的格差
 *       大于 1 格，<b>buffer 128 km 时差 = 0.0000 格（逐位相同）</b>，瓦片解<b>窗口无关</b>。</li>
 * </ul>
 * 因此每瓦片是 {@code (seed, tileX, tileZ)} 的<b>纯函数</b>，可无限外推。
 *
 * <h3>开关与回滚</h3>
 * {@link #EROSION_ON} 默认 <b>false</b>。关掉后 {@link TalosLandField#height0} 里
 * 那一行短路判断不执行，<b>逐位回到现状</b>。
 *
 * <h3>许可证</h3>
 * ★ 只借鉴<b>公式</b>（公式不受版权保护），未抄任何 GPL 代码。
 */
@Frozen(reason = "重构冻结：地形侵蚀等待海陆分布 v2", until = "refactor: landsea v2")
public final class TalosLandErosion {

    private TalosLandErosion() {}

    // ==================== 开关 ====================

    /** ★ 总开关。关掉后 {@link TalosLandField#height0} 里那一行短路判断不执行，逐位回到现状。 */
    public static volatile boolean EROSION_ON = true;

    // ==================== 网格与窗口 ====================

    /** 格距（格）。1 格 = 1 米。 */
    public static volatile double STEP_BLK = 1000.0;
    /** 求解窗口边长（格）。 */
    public static volatile int SOLVE_CELLS = 768;
    /** 有效输出边长（格）。 */
    public static volatile int OUT_CELLS = 512;
    /** 缓冲区（格）。★ 必须 &gt;= 大陆半径 125 km。 */
    public static volatile int BUFFER_CELLS = 128;

    /** 米 -&gt; 格（与 SimTerrain.ELEV_TO_BLK 同值）。 */
    public static final double ELEV_TO_BLK = 0.018;
    /** 海平面（格）。 */
    public static volatile double SEA_BLK = 64.0;

    // ==================== LEM 参数 ====================

    public static volatile int ITER = 140;
    public static volatile int FLOW_EVERY = 10;
    public static volatile double DK = 0.05;
    public static volatile double M = 0.5;
    public static volatile double DTU = 0.01351;
    public static volatile double GAMMA = 0.7;
    public static volatile double UREF = 100.0;

    // ==================== 抬升场中尺度扰动 ====================

    public static volatile double NA_AMP = 120.0;
    public static volatile double NA_WL = 3000.0;
    public static volatile int NA_OCT = 4;
    /** ★ 海岸淡化尺度（米）。没有它时 NA_AMP=120 会削穿 164 个陆地格。 */
    public static volatile double NA_TAPER = 150.0;

    // ==================== 河道层 ====================

    /**
     * ★ 河道阈值（临界流域面积，平方公里 = 格）。
     *
     * <p><b>为什么从 20 降到 3</b>：实测（[134]）阈值 20 时支流格只有 7612，
     * 而且支流在 1 km 网格上只有 1 到 2 格长 —— 看起来像「断头溪」，完全没有水系感。
     * 降到 3 后支流格 25704（<b>3.4 倍</b>），而总水面只从 0.077% 涨到约 0.11%
     * （因为支流只有 1 到 2 格宽）—— <b>几乎免费</b>。
     *
     * <table>
     *   <tr><th>ACC_MIN</th><th>河道格</th><th>占陆地</th><th>水面</th><th>支流格</th></tr>
     *   <tr><td>20</td><td>12036</td><td>10.8%</td><td>0.077%</td><td>7612</td></tr>
     *   <tr><td>5</td><td>24204</td><td>21.7%</td><td>0.100%</td><td>12168</td></tr>
     *   <tr><td><b>3</b></td><td>约 30000</td><td>约 27%</td><td>约 0.11%</td><td>约 20000</td></tr>
     *   <tr><td>2</td><td>44948</td><td>40.3%</td><td>0.121%</td><td>25704</td></tr>
     * </table>
     */
    public static volatile double ACC_MIN = 3.0;
    // ==================== ★ 降水耦合（水力几何）====================
    //
    //   河宽公式的真实形式是【水力几何】：W = a x Q^b，b 约 0.5，Q = 流量。
    //   而 Q ∝ P x A（降水 x 面积）—— 原来我们只用 A，把降水整个漏掉了。
    //
    //   ★ 本层的做法：定义【有效汇流面积】
    //       accW = (P / P_REF_MM) x acc
    //     只用于【河宽】。河道判定（acc >= ACC_MIN）仍然用真实 acc ——
    //     因为「是不是河道」由地形/汇流决定，不该被降水改变。
    //
    //   ★ 为什么不是直接改 widthOf 的签名：accW 是【逐格】的量，
    //     折进 acc 数组后，所有消费者（channelDistance / meanderField / riverWidthBlocks）
    //     一行都不用改，口径天然统一。

    /**
     * ★ 降水耦合总开关。关掉 ⟹ accW = acc ⟹ 逐位回到现状。
     *
     * <p>★★★ <b>当前默认 false，而且【不能】直接开</b> —— 实测发现根本性的规模错配：
     * <pre>
     *   LEM 瓦片    = 512 km（OUT_CELLS=512 x 1 km）
     *   气候瓦片    = SimClimate.TILE_X=100 km, TILE_Z=50 km
     *   气候格      = 10 km
     *   ⟹ 一个 LEM 瓦片横跨 5.1 x 10.2 = 【52 个气候瓦片】
     *   ⟹ 每个气候瓦片 44 秒 ⟹ 52 x 44 = 【38 分钟 / LEM 瓦片】
     * </pre>
     * 即使按 10 km 气候格取样（5929 次查询），实测单次 167 ms ⟹ 16 分钟。
     *
     * <p>可行的替代是【解析降水代理】（从地形直接算迎风坡增雨，O(N)），见待办 W4。
     */
    public static volatile boolean PRECIP_ENABLED = true;
    /** Coarse lattice step (in LEM cells = km) for the lake P/E lookup. */
    public static volatile int LAKE_CLIM_STEP_CELLS = 50;
    /** cross-fade width at LEM tile edges (km; must stay below BUFFER_CELLS). */
    public static volatile double SEAM_BLEND_BLK = 0.0;
    /**
     * Dirichlet ring width (cells). The outermost RING cells are pinned to the BASE field
     * every iteration, so two neighbouring tiles share identical boundary conditions.
     * Cross-fading was tried first and made things worse (two different drainage networks
     * blended into vertical streaks); pinning the boundary is the correct fix.
     */
    public static volatile int SEAM_RING = 0;
    /**
     * Edge taper (km). The erosion DELTA is faded to zero at the tile's own boundary.
     *
     * <p>Why this works where blending failed: the base field {@code baseHeight} is a GLOBAL
     * function, so two neighbouring tiles evaluate the SAME base at a shared edge. If the
     * delta goes to zero there, both sides converge to that same base -> the seam is exactly
     * zero BY CONSTRUCTION (no averaging of two different drainage networks).
     *
     * <p>Cost: a few arithmetic ops per query. Trade-off: erosion is damped inside a band of
     * this width along every tile edge (ET/512 of the area per side).
     */
    public static volatile double EDGE_TAPER_BLK = 0.0;
    /**
     * Cap on the erosion delta, in METRES of relief.
     *
     * <p>This is the "route 1" fix for the tile seam. The whole elevation is
     * {@code base + delta}; the base is a GLOBAL smooth field, identical for every tile, while
     * the delta is what each tile's erosion independently produces (up to +-1 km, i.e. 25% of
     * the total). Because the delta is a chaotic outcome of each tile's own drainage network,
     * neighbouring tiles settle at different heights (observed: 5 km vs 3 km either side of a
     * boundary) and no boundary blend can reconcile that.
     *
     * <p>Capping the delta makes the GROSS elevation come from the shared base field and
     * leaves erosion to shape detail only -- the same split the reference paper uses between
     * crust and amplification. Set to 0 to disable.
     */
    public static volatile double D_CLIP_M = 0.0;
    /**
     * Open-boundary experiment: cells on the ARRAY border drain OUT instead of into the tile.
     *
     * <p>Root cause of the 512 km seam: {@code dirs()} skipped out-of-bounds neighbours, so no
     * border cell could route water outward. Every tile therefore behaved as a CLOSED basin
     * whose wall sits at a different absolute position, and {@code acc} piled up differently
     * on each side. {@code accOf()} already DISCARDED water that left the array -- the wall was
     * only ever in {@code dirs()}.
     */
    public static volatile boolean OPEN_BOUNDARY = true;
    /**
     * EXPERIMENT: external inflow seeded on the array border (cells).
     *
     * <p>Hypothesis under test: the seam is not caused by the closed wall (that only bought
     * 3.4%) but by the ABSENCE of upstream area from outside the tile -- {@code accOf} starts
     * from zero, while erosion uses {@code al = DK * acc^0.5}, so two tiles see different
     * accumulation and therefore different erosion strength.
     *
     * <p>Seeding the border with a constant inflow tests this directly: if the seam collapses,
     * the missing external inflow is the real cause and the fix is a GLOBAL, tile-independent
     * A0(x) rather than a constant.
     */
    public static volatile double A0_SEED = 0.0;
    /**
     * EXPERIMENT: use the GLOBAL base field as the downstream reference instead of the
     * evolved neighbour.
     *
     * <p>The implicit update {@code z = (z + up + al*zd)/(1+al)} couples every cell to its
     * whole downstream chain, and the chain terminates at the tile boundary -- that is the
     * real cause of the seam (both the closed wall and missing inflow were measured and
     * refuted: 3.4% and 0.2%). Taking {@code zd} from {@code U} (the base field, a global
     * function) removes the chain entirely, so two tiles can no longer disagree.
     */
    public static volatile boolean BASE_DOWN = true;

    // --- 旧场 shim（V3 接入后旧 V8 造山/海岸剖面已停用）-----------------------------
    // 这些常量与工具原本来自 TalosLandField。V3 自带抬升场与海岸剖面，
    // 旧叠加层一律关闭（OROG_ON=false），保留同名符号只为不删掉历史代码路径。
    private static final boolean OROG_ON = false;
    private static final double OROG_MAX_M = 0.0;
    private static final double OROG_COAST_TAPER_M = 0.0;
    private static final double CF_LAND_H = 4_000.0;
    private static final double CF_LAND_U = 0.0;
    private static final double OROG_PLATE_BAND = 0.0;
    private static final double OROG_BAND_MIN = 0.0;
    private static final double SUPER_ABS = 0.0;

    private static double smoothstep(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        if (t < 0.0) t = 0.0; else if (t > 1.0) t = 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    private static double orogenyM(long ws, double x, double z) { return 0.0; }
    private static double orogeny01(long ws, double x, double z) { return 0.0; }
    private static double riftProfile(long ws, double x, double z, double w) { return 0.0; }
    private static double signedCoastDistCF(long ws, double x, double z) {
        return com.EyeOfHarmonyBuffer.sim2.land.LandV3.coastDist(ws, x, z);
    }
    private static double coastProfileCF(double sig) { return Math.max(0.0, 1.0 + sig / 400_000.0); }
    private static double cnoise(double x, double y, double z, long seed) {
        return com.EyeOfHarmonyBuffer.sim2.land.LandNoise.value(x, z, seed) - 0.5;
    }


    /**
     * Weight on the EVOLVED neighbour when BASE_DOWN is on:
     *   0.0 = pure global base  -> seam-free, but the drainage follows the regional slope
     *                             (parallel herringbone pattern observed)
     *   1.0 = pure evolved z    -> natural dendritic drainage, but the seam returns (247 m/km)
     * In between interpolates the downstream reference used by the implicit update.
     */
    public static volatile double BASE_BLEND = 0.5;
    private static final byte DIR_OUT = 8;
    /** ceiling of the LandV3 activity gate (1.0 lets the LEM run away to ~15 km). */
    public static volatile double ACT_MAX = 0.28;

    /**
     * 参考降水（mm/yr）。accW = (P/P_REF) x acc。
     *
     * <p>★ 取值 600（不是 1000）：pRel 的【中位数】是 0.809 —— 直接取让它均值=1 的 844
     * 会让【大多数河变窄】（与目标相反）。取 600 让中位河宽 +16%，同时保留
     * 湿区变宽、干区变窄的物理分化。
     * <pre>
     *   实测 pRel 分位（陆地）: p0.10=0.073  p0.25=0.542  p0.50=0.809
     *                          p0.75=0.985  p0.90=1.161  p0.99=5.557
     *   对应中位河宽倍数: P_REF=800 -> 1.01  700 -> 1.08  600 -> 1.16  500 -> 1.27
     * </pre>
     */
    public static volatile double P_REF_MM = 400.0;
    /** ★ 代理公式的隐含参考降水（mm/yr）。实测：只统计陆地时 mean(pRel)=0.844。 */
    public static volatile double P_REF_BASE_MM = 1000.0;
    /** 降水取样失败时的回退值（mm/yr）。 */
    public static volatile double P_FALLBACK_MM = 1000.0;
    /** 降水网格取样的诊断计数。 */
    public static volatile long precipSamples = 0L, precipFails = 0L;

    // ---- ★ 解析降水代理（方案 A）----
    //
    //   ★ 为什么不用完整气候链：规模错配无法调和。
    //       LEM 瓦片    = 512 km（OUT_CELLS=512 x 1 km；BUFFER_CELLS=128 是窗口无关性的硬要求）
    //       气候瓦片    = SimClimate.TILE_X=100 km, TILE_Z=50 km（与 LandformField / V2BiomeField 一致）
    //       ⟹ 一个 LEM 瓦片横跨 5.1 x 10.2 = 52 个气候瓦片 x 44 秒 = 38 分钟 ✗
    //       即使按 10 km 气候格取样（5929 次），实测单次 167 ms ⟹ 16 分钟 ✗
    //
    //   ★ 降水的主要空间结构只有两项，都能 O(N) 解析算：
    //       ① 大尺度水汽可用量   <- 大陆度 kappa + 地形耗尽 depletion(hUp, kappa)
    //       ② 小尺度迎风坡增雨   <- 纬向风 x 地形梯度（Smith & Barstad 的抬升项）
    //
    //   成本：2 次 elevationWithCell（约 1 us）⟹ 59 万格约 0.6 秒 ✓

    /** ★ 解析降水代理总开关。 */
    public static volatile boolean PRECIP_ANALYTIC = true;
    /** 迎风坡增雨的增益（无量纲）：pRel 里 exp(GAIN x climb)。 */
    public static volatile double TERRAIN_GAIN = 0.4;
    /**
     * ★ 爬升高度的归一化尺度（米）。
     *
     * <p><b>为什么不用局部坡度（实测踩过的坑）</b>：最初用 w = u x dh/dx 配 W_REF=0.002，
     * 那对应的坡度只有 0.25 m/km，而真实地形坡度是 1~100 m/km ⟹ <b>25% 的陆地撞上 clamp 上限</b>，
     * pRel 分布两端都饱和（p0.75 = p0.90 = p1.00 = 4.482）。
     *
     * <p>改用【上风累积爬升高度】h - hUp 后：量级 0~3000 m，分布自然，且物理上更对
     * （降水由气团【爬升的总高度】决定，不是局部坡度）。零额外成本（hUp 本来就要算）。
     */
    public static volatile double TERRAIN_H_REF = 2000.0;
    /** clamp 的半宽（无量纲）；超出即饱和。 */
    public static volatile double TERRAIN_CLIMB_CLAMP = 2.0;
    /** ★ 上风路径的采样点数（取路径最高点作为 hUp）。10 点 x 10 km = 100 km。 */
    public static volatile int UPWIND_SAMPLES = 10;
    /**
     * ★★★ 降水的区域平均窗口（LEM 格数）。
     *
     * <p><b>为什么必须有（实测踩过的坑）</b>：河宽应该由【流域平均降水】驱动
     * （Q = ∬P dA，而 acc 已经是流域面积），不是【河道所在点的降水】。
     * 直接乘点降水时，因为<b>河道总在低地、而低地相对上风山脊是背风</b>，
     * pRel 在河道格上系统性小于 1 ⟹ 降水耦合<b>系统性地让河变窄</b>
     * （实测区域 (0,0) 中位 3.32 → 2.21，尽管陆地 mean(pRel)=1.11）。
     *
     * <p>本参数做一次粗网格箱平均：保留大尺度湿/干差异，抹掉局部雨影。
     * 10 格 = 10 km。
     */
    public static volatile int PRECIP_SMOOTH = 10;
    /**
     * ★★ 纬向降水带增益（无量纲）。
     *
     * <p><b>为什么必须有</b>：地形代理（h - hUp）让【所有低地都干】，而河道就在低地
     * ⟹ 降水与河道位置【反相关】⟹ 耦合会系统性让河变窄（实测区域 (0,0) 中位 3.32 → 2.53）。
     * 真实世界的低地不一定干（亚马逊盆地就是低地且极湿）。
     *
     * <p>本项给出纬向带结构：赤道 ITCZ 湿、副热带（约 +-25 度）干、中纬风暴带（约 +-50 度）湿。
     * <pre>
     *   f(lat) = 0.6 cos(2 lat) + 0.4 cos(4 lat)      峰值在 0 度，谷值在约 +-30 度
     *   pLat   = exp(LAT_GAIN x f(lat))
     * </pre>
     * 零成本（只依赖纬度）。
     */
    public static volatile double LAT_GAIN = 0.55;
    /** 水汽耗尽的下限（内陆最干时的相对湿度因子）。 */
    public static volatile double MOIST_FLOOR = 0.25;
    /** 差分步长取地形最短波长的一半（与 PrecipField.wTerrain 同款），m。 */
    public static volatile double TERRAIN_DH_STEP = 23437.5;
    /** 是否打印解析降水的统计（每 64 个瓦片一次）。 */
    public static volatile boolean PRECIP_DIAG = false;
    public static volatile double precipLastMin = 1e18, precipLastMax = -1e18, precipLastMean = 0.0;
    public static volatile long precipTiles = 0L;

    /** 河宽系数：width = WIDTH_W0 * sqrt(accW)。从 0.7 提到 1.0 让干流更宽。 */
    public static volatile double WIDTH_W0 = 1.0;
    public static volatile double WIDTH_MIN = 1.0;
    /** 最大河宽（格）。从 45 提到 60，让主干有更大的宽度跨度。 */
    // ★★★ 2026-10-11：60 -> 200（见 待裁决/277 的查证）。
    //
    //   依据链（全部是外部观测，零自造）：
    //     · 形式 W = WIDTH_W0·sqrt(A) 是 Leopold & Maddock (1953) 的下游水力几何
    //       （W = a·Q^b, b≈0.5）配上 Q ∝ A ⟹ W ∝ A^0.5。
    //     · 系数用地球 7 条大河反算 W/sqrt(A)：亚马逊 3.02、刚果 1.56、叶尼塞 1.25、
    //       长江 1.12、莱茵 0.93、密西西比 0.89、泰晤士 0.63 ⟹ 典型 ~1.0
    //       ⟹ WIDTH_W0 = 1.0 正好落在典型值上 ✓
    //     · 缩放不变性：W_e = c·sqrt(A_e)，W_e = 40·W_mc、A_e = 1600·A_mc
    //       ⟹ W_mc = c·sqrt(A_mc) —— 【同一个公式、同一个系数】✓
    //     · 所以本世界的物理上界 = 地球最大河（亚马逊 8,000 m）÷ 40 = 200 blocks。
    //
    //   ⚠ 为什么旧的 60 是错的：实测 max W = 65.5（acc = 4,287 km²，正好对应亚马逊的流域），
    //     p99.9 = 59.5 已经贴在 60 上 ⟹ 【它在触顶，把真实的大河人工削平了】，
    //     而 javadoc 从未说明 60 从哪来。
    //
    //   ★ 改 60 -> 200 【不改变任何现有结果】（实测 max 只有 65.5）——
    //     它只是【去掉一个没有依据的天花板】。
    public static volatile double WIDTH_MAX = 200.0;
    /** ★ 河道段内横向鼓出振幅（格）—— 让 MC 尺度的 1 km 直线段有蜿蜒感。
     *  必须是【段内鼓出】形状（端点处为 0），否则「到折线的距离场」在格边界断裂。 */
    // ==================== ★ 河道蜿蜒（谱合成 / 相位积分）====================
    //
    // off(s) = A(s) * sin(Phi(s))
    //     lambda(s) = MEANDER_LAMK * W(s)                  （谱峰波长，Leopold & Wolman: 11 W）
    //     Phi(s)    = 2 pi * Integral ds / lambda(s)       （★ 相位必须积分：lambda 随河宽变）
    //     A(s)      = MEANDER_AMP * W(s) * (1 + MOD*sin(2 pi s/(MODW*lambda) + psi))
    //
    // ★ 为什么不用单一正弦：真实河流的蜿蜒【非周期、幅度变化、波长变化】。
    //   因为 lambda 正比于【局部河宽】，而河宽沿程从 2 格变到 60 格（30 倍），
    //   相位累积极不均匀 ⟹ 波形天然非周期 ✓ 再叠加慢变幅度调制给出"有的弯大有的弯小"。
    //
    // ★ 为什么是【弧长的函数】：跨格天然连续（顶点不需要固定）⟹ 单一中心线口径。
    //
    // ★ 与旧 MEANDER_AMP 的区别：旧的是【常数 20 格 + 段长周期的 bulge + 逐格翻符号】，
    //   那会让窄河消失、并造成每 1000 格一次的"呼吸"。新版三个毛病都没有。

    /** ★ 总开关。关掉 ⟹ off 恒为 0 ⟹ 逐位回到直线中心线。 */
    public static volatile boolean MEANDER_ENABLED = true;
    /** 蜿蜒幅度 = MEANDER_AMP × 河宽（真实蜿蜒带宽度是河宽的 2 到 5 倍）。 */
    public static volatile double MEANDER_AMP = 2.0;
    /** 谱峰波长 = MEANDER_LAMK × 河宽（Leopold & Wolman 实测 10 到 14）。 */
    public static volatile double MEANDER_LAMK = 11.0;
    /** 波长下限（格）。防止小河的 lambda 落到亚格尺度（W=2 ⟹ 22 格太密）。 */
    public static volatile double MEANDER_LAM_MIN = 40.0;
    /** 幅度调制深度（0 = 不调制）。 */
    public static volatile double MEANDER_MOD = 0.45;
    /** 调制波长 = MEANDER_MODW × lambda。 */
    public static volatile double MEANDER_MODW = 3.7;
    /** ★ 每段折线的采样数（精测阶段用）。越大越准，成本线性。 */
    public static volatile int MEANDER_SAMPLES = 12;

    // ---- ★ 顶点平滑（Taubin，去掉 D8 的 45 度折角）----
    /** 平滑轮数（每轮一次 λ 步 + 一次 μ 步）。
     *
     * <h3>★ 2026-10-11：4 -> 0（Taubin 与【亚格尺度的河】不可调和）</h3>
     * <p><b>实测</b>：Taubin 的顶点位移【平均 102.8 格、最大 725.8 格】（blocks），
     * 而河宽【平均只有 5.2 格】（blocks）⟹ ★ <b>位移是河宽的 96 倍</b>。
     * <p><b>后果</b>：格心落在河道外 ⟹ <b>60% 的河道格被判为非河道</b>
     * （修法 A2 之后实测 52.8%；关掉 Taubin 后 99.7%）。
     *
     * <p><b>为什么不可调和</b>：Taubin 的位移量级 = 【D8 阶梯的固有幅度】
     * （45 度折角处 `(up+dn)/2` 与格心差 1 个格距 = 1000 blocks，λ=0.5 ⟹ 500 blocks）。
     * 而本模型的河宽只有 5 blocks = <b>格距的 1/200</b> ⟹ 任何【按格距尺度】的平滑
     * 都会把河道线搬到 100 倍河宽之外。
     *
     * <p><b>而 Taubin 的存在理由不成立</b>：它的目的是「消除 D8 的 45 度折角」，
     * 但在 1 km 网格上，折角是<b>离散化的必然</b>且<b>视觉不可见</b>（河宽/格距 = 1/200）；
     * 而【蜿蜒】已由 {@code meanderOff} 独立提供（依据 Leopold &amp; Wolman，见 :243-257）。
     *
     * <p><b>回滚</b>：改回 {@code 4} 即可（但那样河道会重新碎片化）。
     */
    public static volatile int SMOOTH_ROUNDS = 0;
    /** Taubin λ（正向平滑系数）。 */
    public static volatile double SMOOTH_LAMBDA = 0.5;
    /** Taubin μ（反向收缩补偿，必须为负且 |mu| 略大于 lambda）。 */
    public static volatile double SMOOTH_MU = -0.53;

    private static final ThreadLocal<int[]>    CAND_K = new ThreadLocal<int[]>() {
        protected int[] initialValue() { return new int[3]; } };
    private static final ThreadLocal<double[]> CAND_D = new ThreadLocal<double[]>() {
        protected double[] initialValue() { return new double[3]; } };
    /** ★ channelDistance 顺手记下的【中心线上最近点】：{x, z, acc}。 */
    private static final ThreadLocal<double[]> TL_PT = new ThreadLocal<double[]>() {
        protected double[] initialValue() { return new double[3]; } };

    /**
     * ★★★ 找【中心线上】离 (px,pz) 最近的点。返回 {x, z, acc}（世界坐标），找不到返回 null。
     *
     * <h3>为什么需要它</h3>
     * 接入蜿蜒之后，中心线会横移最多 MEANDER_AMP x 河宽（主干可达 120 格）。
     * 于是<b>河道格的中心不再在河上</b> —— 在格中心调 {@link #isRiver} 会返回 false。
     * {@code /talos_tp} 正是按格中心判定的，会「定位到河道格却没有水」。
     *
     * <p>语义区分：
     * <ul>
     *   <li>{@code isRiver(x,z)} = 「这个点在不在水上」 —— <b>语义没变，是对的</b></li>
     *   <li>{@code nearestChannelPoint(x,z)} = 「最近的中心线点在哪」 —— <b>TP 用这个</b></li>
     * </ul>
     */
    /**
     * ★ (px,pz) 所在【格】是否属于河道（acc >= ACC_MIN 且非海非湖）。
     *
     * <p>与 {@link #isRiver} 的区别：
     * <ul>
     *   <li>{@code isRiver(x,z)} = <b>这个点在不在水上</b>（接入蜿蜒后，格中心常常不在河上）</li>
     *   <li>{@code isChannelCell(x,z)} = <b>这个格是不是河道格</b>（与蜿蜒无关）</li>
     * </ul>
     * 统计河网、判定归属时应该用后者，否则会被蜿蜒误导。
     */
    public static boolean isChannelCell(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null || t.chOrd == null) return false;
        int W = t.W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        int i = (int) Math.floor((px - ox) / STEP_BLK);
        int j = (int) Math.floor((pz - oz) / STEP_BLK);
        if (i < 0 || i >= W || j < 0 || j >= W) return false;
        return t.chOrd[j * W + i] >= 0;
    }

    /**
     * ★★ 有效汇流面积（含降水）= pRel x acc = 【年产流量的代理】。非河道格返回 0。
     *
     * <p><b>干河道的首选判据</b>：它同时表达「流域小」与「降水少」两个因素，
     * 而且零成本（accW 已在 Tile 里）。河道格满足 acc >= ACC_MIN，但若 accW 低于阈值，
     * 说明该流域的产流不足以维持常流 ⟹ 干河道。
     */
    public static double effectiveAccOfCell(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null || t.chOrd == null) return 0.0;
        int W = t.W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        int i = (int) Math.floor((px - ox) / STEP_BLK);
        int j = (int) Math.floor((pz - oz) / STEP_BLK);
        if (i < 0 || i >= W || j < 0 || j >= W) return 0.0;
        int k = j * W + i;
        return t.chOrd[k] >= 0 ? accWOf(t, k) : 0.0;
    }

    // ==================== ★★ 干河道（wadi / arroyo）====================
    //
    //   ★ 判据：accW = pRel x acc（年产流量代理）低于阈值 ⟹ 产流不足以维持常流。
    //   ★ 过渡带：不在阈值处硬切换，而是用平滑因子 t 同时控制【河床深度】与【水面高度】：
    //
    //       t        = smoothstep(DRY_ACCW_MIN, DRY_ACCW_MIN x DRY_TRANS, accW)
    //       bedFrac  = DRY_DEPTH_FRAC + (1 - DRY_DEPTH_FRAC) x t
    //       河床      = 水面 - riverDepth x bedFrac
    //       水位      = 水面 - (1 - t) x riverDepth
    //
    //   t = 1（常流）：bedFrac = 1            ⟹ 河床 = 水面 - 深度，水位 = 水面  ⟹ 满水
    //   t = 0（全干）：bedFrac = DRY_DEPTH_FRAC ⟹ 河床 = 水面 - 0.5 深度，
    //                                            水位 = 水面 - 深度 < 河床 ⟹ 【不铺水】
    //
    //   ★ 这样水头不会突兀：t 在过渡带内连续变化，水从满到浅到无。

    /** ★ 干河道阈值（相对降水 pRel）。低于它 ⟹ 降水不足以维持常流。 */
    public static volatile double DRY_PREL_MIN = 0.55;
    /** 已废弃：曾用 accW 作判据（但 accW 把流域面积也混进来了）。 */
    public static volatile double DRY_ACCW_MIN_UNUSED = 8.0;
    /** 过渡带宽度 = DRY_ACCW_MIN x DRY_TRANS。 */
    public static volatile double DRY_TRANS = 2.5;
    /** 全干时河床相对满水深度的比例（0.5 = 挖一半）。 */
    public static volatile double DRY_DEPTH_FRAC = 0.5;
    /** 干河道总开关。false ⟹ wetness 恒为 1 ⟹ 逐位回到现状。 */
    public static volatile boolean DRY_ENABLED = true;

    /**
     * ★★ 河道的【湿润度】t ∈ [0,1]：0 = 完全干（只留河床），1 = 常流（满水）。
     *
     * <p>⚠⚠ <b>2026-10-14 审查（#19）：本段是【静默失效的实质文档】</b> ✗ ——
     * 它与下一段（生效的 ✓「单格的湿润度…由 pRel 相对阈值决定」）紧邻成**两份 javadoc** ✗，
     * Java 只认最后一份 ⟹ 本段内容**原本无效** ✗。
     * ★ 且它里面的**参数名已过期** ✗：原文写 {@code DRY_ACCW_MIN}，
     * 而 {@code wetnessLocal} 实际用的是 {@link #DRY_PREL_MIN} ✓（见函数体 ✓）。
     * ⟹ 已把有用信息并入下一段 ✓（本段其余保留为史料 ✓）。
     *
     * <p><b>（历史）</b>用 smoothstep 在 [DRY_ACCW_MIN, DRY_ACCW_MIN x DRY_TRANS] 上平滑过渡，
     * 所以不会出现突兀的水头。非河道格返回 0。
     */
    /**
     * ★ 单格的湿润度（未做单调化）：由 pRel 相对阈值决定。
     *
     * <p>取值 t ∈ [0,1]：0 = 完全干（只留河床），1 = 常流（满水）✓。
     * 用 smoothstep 在 [DRY_PREL_MIN, DRY_PREL_MIN * max(1, DRY_TRANS)] 上平滑过渡 ✓
     * ⟹ 不会出现突兀的水头 ✓；非河道格返回 0 ✓。
     *
     * <p>⚠ 2026-10-14 审查（#19）：上面那段（写 DRY_ACCW_MIN 的历史说明 ✗）
     * 原本因"两份紧邻 javadoc"而**静默失效** ✗ ⟹ 其有效内容已并入本段 ✓，
     * 且参数名已按函数体更正为 {@link #DRY_PREL_MIN} ✓。
     */
    static double wetnessLocal(double pr) {
        if (!DRY_ENABLED) return 1.0;
        double lo = DRY_PREL_MIN, hi = DRY_PREL_MIN * Math.max(1.0, DRY_TRANS);
        if (hi <= lo) return pr >= lo ? 1.0 : 0.0;
        double t = (pr - lo) / (hi - lo);
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    /**
     * ★★ 河道的【湿润度】t ∈ [0,1]：0 = 完全干（只留河床），1 = 常流（满水）。
     *
     * <p>取自 {@link Tile#mWet}（由 meanderField 沿流向取 max(本格, 上游) 传播），
     * <b>所以保证「上游有水 ⟹ 下游有水」</b>—— 这是物理硬约束（水往低处流）。
     *
     * <p>★ 判据用的是 <b>pRel（相对降水）</b>而不是 accW：因为 accW = pRel x acc 已经含了
     * 流域面积，而干湿是【降水】的事，不是流域大小的事。
     */
    public static double wetness01(long ws, double px, double pz) {
        if (!DRY_ENABLED) return 1.0;
        Tile t = tileAt(ws, px, pz);
        if (t == null || t.chOrd == null || t.mWet == null) return 1.0;
        final int W = t.W;
        final double outBlk = OUT_CELLS * STEP_BLK;
        final double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        final double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        final int i = (int) Math.floor((px - ox) / STEP_BLK);
        final int j = (int) Math.floor((pz - oz) / STEP_BLK);
        if (i < 0 || i >= W || j < 0 || j >= W) return 1.0;
        final int o = t.chOrd[j * W + i];
        return (o >= 0 && o < t.mWet.length) ? t.mWet[o] : 1.0;
    }

    /** ★ 干河道时的河床比例 = DRY_DEPTH_FRAC + (1-DRY_DEPTH_FRAC) x t。 */
    public static double bedFrac01(double t) {
        double f = DRY_DEPTH_FRAC;
        if (f < 0.0) f = 0.0; if (f > 1.0) f = 1.0;
        return f + (1.0 - f) * t;
    }

    /** ★ 河道格自己的河宽（格）。非河道格返回 0。 */
    public static double channelWidthOfCell(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null || t.chOrd == null) return 0.0;
        int W = t.W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        int i = (int) Math.floor((px - ox) / STEP_BLK);
        int j = (int) Math.floor((pz - oz) / STEP_BLK);
        if (i < 0 || i >= W || j < 0 || j >= W) return 0.0;
        int o = t.chOrd[j * W + i];
        return o >= 0 ? t.mW[o] : 0.0;
    }

    public static double[] nearestChannelPoint(long ws, double px, double pz) {
        double[] pt = TL_PT.get();
        pt[2] = 0.0;
        double d = channelDistance(ws, px, pz);
        if (d == Double.MAX_VALUE || pt[2] <= 0.0) return null;
        return new double[]{ pt[0], pt[1], pt[2] };
    }

    /** 已废弃：旧常数幅度会撕裂窄河，保留仅为兼容。 */
    public static volatile double MEANDER_AMP_OLD = 20.0;
    /**
     * ★ 段内横向蜿蜒幅度【相对河宽】的比例（0 到 0.5）。
     *
     * <p>实测（[134]）：用常数幅度（旧 {@link #MEANDER_AMP} = 20 格）时，
     * acc=3 的支流只有 1.7 格宽，一旦偏移为负就会让 <b>de 恒为正 ⟹ 河道消失</b>，
     * 而偏移为正时又胀到 40 格。因为符号逐格翻转、鼓出逐段起伏，
     * 河道每 1000 格就消失又出现一次 —— 正是用户报的现象。
     *
     * <p>改成比例后：河宽 1.7 格 ⟹ 偏移 ±0.4 格；河宽 60 格 ⟹ 偏移 ±15 格。
     * <b>任何河宽下都不会消失，也不会胀。</b>
     */
    public static volatile double MEANDER_FRAC_UNUSED = 0.25;   // 已被 MEANDER_AMP 取代

    // ==================== 河深 ====================

    /** 河深系数：riverDepth = clamp(DEPTH_MIN, DEPTH_MAX, DEPTH_D0 * acc^0.3)。 */
    public static volatile double DEPTH_D0 = 0.6;
    public static volatile double DEPTH_MIN = 1.0;
    public static volatile double DEPTH_MAX = 6.0;

    // ==================== 湖泊层 ====================

    public static volatile boolean LAKES_ENABLED = true;
    // ★★★★★ 2026-10-11 曾调成 {SPACING=48, R=16, DEPTH=64}（见 待裁决/290、291）—— ★ 已回滚。
    //
    //   收益（协议测量，128 km / 1 km / 4 区块）：
    //     覆盖率 4.40% -> 1.88%（落进地球 1~2%）、游程段 148 -> 57、p90 3.50 -> 4.50 km
    //
    //   ✗ 回滚原因（待裁决/292 实测）：
    //     DEPTH 16 -> 64 把【碗的地形改造放大了 4 倍】：
    //       最大抬升 11.3 -> ★ 45.07 格（环形土墙）
    //       最大挖深 16   -> ★ 64.00 格
    //     （理论 rim = BOWL_RIM*BOWL_DEPTH = 57.6，但实测 45.07 ——
    //      因为 dz = D*(rim*rimp - w) 的最大值在 r2≈0.61 处，那里 w 不为 0；
    //      手算 f(x) = 0.9*4x(1-x) - (1-x)^2 的最大值 = 0.705 => 64*0.705 = 45.1 ✓）
    //     ⟹ ★ 用户裁决：「不能接受」（45 格高的环墙）
    //
    //   ⚠ 而验收【零回归】—— 那 16 个门覆盖不到这个尺度的地形变化，
    //     所以「零回归」不能证明该改动无害。
    public static volatile int BOWL_SPACING = 24;
    public static volatile double BOWL_R = 8.0;
    public static volatile double BOWL_DEPTH = 16.0;
    /** ★ 环形抬升（相对碗深）。没有它时碗在斜坡上装不住水（湖面 0.44% 到 1.52%）。 */
    public static volatile double BOWL_RIM = 0.90;
    public static volatile double BOWL_MIN_RIFT = 0.40;
    /**
     * ★★★★★ 2026-10-12（待裁决/341）：湖泊的最小水深门槛（格）。0.3 -> 0.6。
     *
     * <p><b>语义</b>：{@code filled − z > LAKE_MIN_FILL} 才算「湖」；否则是
     * <b>湿地 / 沼泽</b>（有水但不计为湖）。
     *
     * <p><b>为什么必须提高</b>（实测，tile(-3,2)，见 待裁决/341）：
     * <pre>
     *   湖深分位: p10=0.03  p25=0.09  ★ p50=0.23  p75=0.49  p90=0.79  max=3.16 格
     *   ⟹ ★ 湖深中位数只有 0.23 格 = 12.8 米 —— 这些是「湿地/水塘」，不是湖
     * </pre>
     * 三块瓦片合计的湖占比扫描（1 格 = 55.56 m）：
     * <pre>
     *   0.30 格 (16.7 m) ⟹ 3.79%   ← 旧值：远高于地球锚 1~2%
     *   0.50 格 (27.8 m) ⟹ 1.98%
     *   ★ 0.60 格 (33.3 m) ⟹ 1.51%   ← ★ 新值：落在地球锚中点
     *   0.70 格 (38.9 m) ⟹ 1.08%
     *   0.80 格 (44.4 m) ⟹ 0.73%
     * </pre>
     *
     * <p>⚠ <b>不只是显示阈值</b>：本常量还用在 {@code solveLakeLevels} 的 BFS 种子判定里
     * ⟹ 改它会<b>同时改水位解</b>（不只是改计数口径）。
     *
     * <p>⚠ <b>出处</b>：旧值 0.3 <b>没有任何注释/出处</b>（旁边几个常量都有）；
     * 新值 0.6 是<b>由「落在地球锚 1~2%」反推</b>的，<b>不是</b>从湖的物理定义导出。
     *
     * <p>★★★★ <b>2026-10-14 更正：那条锚【用错了口径】（research/E_lake_area.md）</b>。
     * 文献里的"湖"<b>全部是面积 / 淹没频率判据</b>，<b>没有任何一份全球湖泊清单用深度定义湖</b>：
     * <pre>
     *   自然湖 &gt;= 0.1 km²（Messager 2016, Nat Commun 7:13603, OA 原文）  = 1.8% of global land
     *   上式 + 大型水库                                                  = 2.0%
     *   同数据集外推 &gt;= 0.01 km²                                        = 2.2%
     *   淡水+咸水湖 &gt;= 10 ha（Lehner 2025, ESSD 17:2277, OA）           = 2.0%（含水库 2.24%）
     *   &gt; 0.002 km² 含水库（Verpoorter 2014, GRL）                       = 3.7% of nonglaciated land
     *   任意水面含季节+河流（Pekel 2016）                                = 3.0%
     *   ★ "1.8%" 的老祖宗：Thienemann 1925 的 2.5e6 km²（Downing 2010, Limnetica, OA 逐字）
     * </pre>
     * ⟹ ★ <b>本常量是【深度】判据（0.6 格 = 33 米），与上述【面积】判据不是同一个量</b>，
     * 直接拿"1~2%"当硬门是<b>范畴错误</b>。
     * <p>★ <b>处置（2026-10-14）</b>：该锚<b>降级为"量级监测 + 口径说明"</b>——只用于否证量级错误
     * （例如湖占比 &gt; 5%，那无论什么口径都不对）；<b>不再用它反推本常量的值</b>。
     * 本常量现在的正当性来自<b>物理判据</b>（"填洼深度 &gt; 33 米才叫湖，否则是湿地"）
     * 与 §341/§342 的分布实测，<b>不是</b>来自那个锚。
     *
     * <p>★ <b>2026-10-14 更正（重要）</b>：这里原先写着"给 LEM 加沉积项才是更物理的解"——
     * 那条<b>已被实测否证</b>（待裁决/345、347）：
     * <pre>
     *   沉积 D = G*Qs/A 是【面积平均】量，对局部洼地几何【零感知】
     *   口径 1（沉积全境、侵蚀仍门控）：湖 2.60 -> 2.47、湿地 11.00 -> 10.99  ⟹ 几乎零作用
     *   口径 2（解冻侵蚀）：主导效应是【平原被削平】，不是沉积在起作用
     * </pre>
     * ⟹ 真正解决"平原积水"的是<b>侵蚀阈值</b>（见 {@link #THETA_ON} / {@link #THETA_M}，
     * Theodoratos &amp; Kirchner 2020）：它让缓坡停止下切，平原不再被削到海平面。
     * <b>本常量现在只是一个【分类口径】（湖 vs 湿地），不再是给某个缺陷打的补丁。</b>
     *
     * <p>★ 回滚：改回 0.3。
     */
    public static volatile double LAKE_MIN_FILL = 0.6;

    /** ★★★★★ N1（2026-10-11）：裂谷 = 造山的镜像（同一张板块接缝网的【张裂侧】）。总开关。 */
    public static volatile boolean RIFT_ENABLED = true;
    /**
     * ★ N1a / N1b 的分配比例：**进抬升场（LEM 能平衡）的那一份**占多少；
     * 剩下 {@code 1 - RIFT_SPLIT} 由 LEM 之后叠加 —— 那一份才是**把水留下**的。
     *
     * <p><b>为什么必须分两份</b>：LEM 的不动点满足 {@code z_k - zd = up/al >= 0}
     * （待裁决/302 §一 的推导）⟹ 平衡态沿流向**单调不增** ⟹ 放进抬升场的裂谷会被【解掉】：
     * 【谷地形态留下，闭合盆地不会】。
     *
     * <p>用户裁决 2026-10-11：**1:1 先试**。⚠ <b>此值无外部出处，是试验起点</b>。
     */
    public static volatile double RIFT_SPLIT = 0.5;
    /**
     * ★ 2026-10-11（待裁决/304，V3b）：**按水量收支解湖的水位**，覆盖 {@code t.filled}。
     * <p>关掉 ⟹ 逐位回到「填满到溢出口」。
     */
    public static volatile boolean LAKE_SOLVE_ENABLED = true;
    /**
     * ★ 裂谷【肩 / 轴】比（无量纲）。
     *
     * <p><b>为什么不是沿用 {@code BOWL_RIM = 0.90}</b>：那是当年【环形土墙】的高度比，
     * 语义完全不同 —— 搬过来会让翼部抬升只有轴部沉降的 0.4 倍。
     *
     * <p><b>出处的锚</b>（待裁决/301 §7.1 锚 1 逐字）：
     * 「It is generally assumed that rift (or fault block) subsidence is
     * <b>approximately balanced by uplift of the flanks</b>」
     * —— Phil. Trans. R. Soc. Lond. A 294(1409), 125 (1980), doi 10.1098/rsta.1980.0020
     * ⟹ 肩/轴 ≈ <b>1.0</b>。
     */
    public static volatile double RIFT_SHOULDER = 1.0;

    // ==================== 缓存 ====================

    public static volatile int CACHE_TILES = 16;

    static final int[] DX = { -1, -1, -1, 0, 0, 1, 1, 1, 0 };
    static final int[] DZ = { -1, 0, 1, -1, 1, -1, 0, 1, 0 };
    static final double[] DD = { 1.41421356, 1, 1.41421356, 1, 1, 1.41421356, 1, 1.41421356, 1 };
    static final double EPS = 1.0 / 256.0;

    // ==================== 内部状态 ====================

    /** ★ 诊断计时（待裁决/304 §11.4）：湖水位解的三段耗时（ns，累积）。 */
    public static volatile long LAKE_SOLVE_NANOS = 0L, LAKE_CLIM_NANOS = 0L, LAKE_AFTER_NANOS = 0L;

    private static final ConcurrentHashMap<Long, Tile> CACHE = new ConcurrentHashMap<Long, Tile>();
    private static volatile int installedSeed = Integer.MIN_VALUE;
    private static volatile long tilesSolved = 0L;
    private static volatile long solveNanos = 0L;

    public static synchronized void install(int worldSeedInt) {
        if (installedSeed == worldSeedInt) return;
        installedSeed = worldSeedInt;
        CACHE.clear();
        tilesSolved = 0L; solveNanos = 0L;
    }

    public static synchronized void uninstall() {
        installedSeed = Integer.MIN_VALUE;
        CACHE.clear();
    }

    // ==================== 后台预解 ====================

    /** 预解线程（daemon + 最低优先级，与 OceanWiring 的 SST 预热同款）。 */
    private static volatile Thread worker;
    private static volatile boolean warmStop = false;

    /**
     * 后台预解 (px,pz) 附近的 5x5 瓦片（由近及远，螺旋）。
     * ★ 未解出来时查询会【同步解一次】（1.4 到 1.7 s 卡顿），
     *   所以正式接入时应先 warmAround 再让玩家靠近。
     */
    public static synchronized void warmAround(double px, double pz) {
        if (!EROSION_ON) return;
        int ob = outBlocks();
        if (ob <= 0) return;
        Thread old = worker;
        if (old != null && old.isAlive()) {
            warmStop = true;
            try { old.join(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        warmStop = false;
        final long ws = installedSeed;
        final int ctx = (int) Math.floorDiv((long) Math.floor(px), (long) ob);
        final int ctz = (int) Math.floorDiv((long) Math.floor(pz), (long) ob);
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                for (int ring = 0; ring <= 2 && !warmStop; ring++) {
                    for (int dz = -ring; dz <= ring && !warmStop; dz++) {
                        for (int dx = -ring; dx <= ring && !warmStop; dx++) {
                            if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                            long k = tileKey(ws, ctx + dx, ctz + dz);
                            if (CACHE.containsKey(k)) continue;
                            Tile tt = solve(ws, ctx + dx, ctz + dz);
                            if (tt != null) { CACHE.put(k, tt); evictIfNeeded(); }
                        }
                    }
                }
            }
        }, "Talos-LEM-Warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** 预热线程是否还在跑（诊断用）。 */
    public static boolean isWarming() { return worker != null && worker.isAlive(); }

    /** 停掉预热线程。 */
    public static synchronized void stopWarm() {
        warmStop = true;
        Thread w = worker;
        if (w != null) w.interrupt();
        worker = null;
    }

    public static int installedSeed() { return installedSeed; }
    public static long tilesSolved() { return tilesSolved; }
    public static long solveNanos() { return solveNanos; }
    public static int cachedTiles() { return CACHE.size(); }

    // ==================== 对外查询 ====================

    /**
     * ★ LEM 影响在海岸附近的淡化尺度（米）。
     *
     * <p><b>为什么必须有</b>：LEM 会把海岸带的陆地侵蚀到海平面以下。若那时【硬切回】基础剖面，
     * 就会在海岸带出现「两个高度场硬切换」⟹ <b>海岸墙</b>（实测踩过）。
     * 这里改成：把 LEM 的效果表示成【相对基础剖面的变化量 d】，再乘一个随离岸高度渐弱的权重。
     * 于是靠海处 d 到 0 ⟹ 与基础剖面严丝合缝 ⟹ 无墙。
     */
    public static volatile double COAST_TAPER_M = 100.0;

    /**
     * (px,pz) 处经 LEM 改造后的高度（米）。
     *
     * <p>★ <b>陆海归属完全由 {@link #baseHeight}（= 海岸剖面）决定，LEM 不改变它。</b>
     * 这一点是硬约束：{@code isLand} 用的是 {@code landScore 大于等于 0.5}，而
     * {@code coastProfileCF(signedCoastDistCF) 大于 0} 与它等价（{@code sig = (landScore-0.5)/|grad landScore|}）。
     * 任何让 LEM 改变符号的写法都会让【两套口径】分叉（[084] 的教训）。
     */
    public static double height(long ws, double px, double pz) {
        final double baseM = baseHeight(ws, px, pz);
        if (baseM <= 0.0) return baseM;                    // ★ 海：完全不动（不改陆海）
        Tile t = tileAt(ws, px, pz);
        if (t == null) return baseM;
        double v = seamBlend(ws, t, px, pz, sample(t.z, t, px, pz));           // LEM 高度（格）
        final double baseBlk = SEA_BLK + baseM * ELEV_TO_BLK;
        final double d = v - baseBlk;                      // ★ LEM 相对基础剖面的变化量（格）
        // ★ 海岸淡化：靠海处权重到 0 -> 与基础剖面连续 -> 无海岸墙
        final double taper = smoothstep(0.0, COAST_TAPER_M, baseM);
        // ★★ 不许沉到海平面以下 —— 但必须用【连续】的方式限制，不能用 if 硬钳。
        //    硬钳（out 小于等于 SEA 就设成 SEA+0.5）会在海岸带制造 27 格的悬崖
        //    （实测：相邻格高差 max 从 5.63 米跳到 27.03 米）。
        //    这里改成限制 d 的【负向幅度】，再乘 taper：
        //      taper = 1 时最深正好到 SEA_BLK + 0.5；
        //      taper = 0 时完全不限制；
        //      对 taper 连续，对空间也连续（min 只产生拐点，不产生跳变）。
        final double dMin = -(baseBlk - SEA_BLK - 0.5);
        double dUse = (d < dMin) ? dMin : d;
        if (D_CLIP_M > 0.0) {
            final double c = D_CLIP_M * ELEV_TO_BLK;
            if (dUse > c) dUse = c; else if (dUse < -c) dUse = -c;
        }
        dUse *= edgeTaper(t, px, pz);
        final double out = baseBlk + dUse * taper;
        return (out - SEA_BLK) / ELEV_TO_BLK;
    }

    /** 河宽公式：clamp(WIDTH_MIN, WIDTH_MAX, WIDTH_W0 * sqrt(acc))。 */
    public static double widthOf(double acc) {
        if (!(acc > 0.0)) return 0.0;
        double w = WIDTH_W0 * Math.sqrt(acc);
        return w < WIDTH_MIN ? WIDTH_MIN : (w > WIDTH_MAX ? WIDTH_MAX : w);
    }

    /** ★ 河深（格）：clamp(DEPTH_MIN, DEPTH_MAX, DEPTH_D0 * acc^0.3)。 */
    public static double riverDepth(double acc) {
        if (!(acc > 0.0)) return 0.0;
        double d = DEPTH_D0 * Math.pow(acc, 0.3);
        return d < DEPTH_MIN ? DEPTH_MIN : (d > DEPTH_MAX ? DEPTH_MAX : d);
    }

    /**
     * ★ 到【河道中心线】的有符号距离（格）。小于等于 0 表示在河道内。
     *
     * <p>做法：取查询点所在格周围 5x5 邻域里所有满足「非海、acc 大于等于 ACC_MIN、非湖」的格，
     * 每格构成一条线段（本格中心 -&gt; 下游格中心），取点到线段距离减去半宽与段内鼓出后的最小值。
     *
     * <p>★ 段内鼓出 off = MEANDER_AMP * 4t(1-t) * 正负1：
     * 在段两端（格中心）恒为 0，所以跨格连续；只在段中间鼓出，让 1 km 的直线段有蜿蜒感。
     * 实测（[128]）：自由 2D 噪声偏移会把河道撕成碎片，段内鼓出不会。
     */
    public static double channelDistance(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return Double.MAX_VALUE;
        int W = t.W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        int gi = (int) Math.floor((px - ox) / STEP_BLK);
        int gj = (int) Math.floor((pz - oz) / STEP_BLK);
        // ==================== 两阶段：粗筛 + 精测 ====================
        //
        // ★★★ 为什么必须这么做（实测踩过的坑）：
        //   曾经写成  de = dist − w/2 − off  ——  那是把 off 当成【宽度修正】，
        //   而 off 的真实语义是【中心线横移】。后果：
        //       off > 0 时 de = dist − w/2 − off  变得很负 ⟹ 河道胀到 (w + 2*off) 宽
        //       off < 0 时 de 恒为正               ⟹ 河道【整段消失】
        //   主干 A = 2*60 = 120 格 ⟹ 出图是一节一节的团块 + 扇形碎片。
        //
        //   正确做法：off 移动【线本身】，然后测到【移动后的折线】的距离。
        //   为了不把 25 段 x NS 次采样全算一遍，分两阶段：
        //       ① 对 5x5 邻域的 25 段做【直线】粗筛（便宜），保留最好的 CANDS 段
        //       ② 只对候选段做【密集采样折线】精测
        final int CANDS = 3;
        int[] candK = CAND_K.get();
        double[] candD = CAND_D.get();
        int nCand = 0;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                int i = gi + di, j = gj + dj;
                if (i < 0 || i >= W || j < 0 || j >= W) continue;
                int k = j * W + i;
                if (t.sea[k]) continue;
                if (!(t.acc[k] >= ACC_MIN)) continue;
                if (LAKES_ENABLED && t.filled[k] - t.z[k] > LAKE_MIN_FILL) continue;   // ★ 湖优先
                int d = t.dir[k];
                if (d < 0) continue;
                int ni = i + DX[d], nj = j + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                // ★ 段两端用【Taubin 平滑后的顶点】⟹ 消除 D8 的 45 度折角
                double ax, az, bx, bz;
                final int oa2 = (t.chOrd != null) ? t.chOrd[k] : -1;
                final int ob2 = (t.chOrd != null) ? t.chOrd[nj * W + ni] : -1;
                if (oa2 >= 0 && ob2 >= 0) {
                    ax = t.mVX[oa2]; az = t.mVZ[oa2];
                    bx = t.mVX[ob2]; bz = t.mVZ[ob2];
                } else {
                    ax = ox + (i + 0.5) * STEP_BLK; az = oz + (j + 0.5) * STEP_BLK;
                    bx = ox + (ni + 0.5) * STEP_BLK; bz = oz + (nj + 0.5) * STEP_BLK;
                }
                double vx = bx - ax, vz = bz - az;
                double vl = vx * vx + vz * vz;
                if (vl <= 0.0) continue;
                double tt = ((px - ax) * vx + (pz - az) * vz) / vl;
                tt = tt < 0.0 ? 0.0 : (tt > 1.0 ? 1.0 : tt);
                double qx = ax + vx * tt - px, qz = az + vz * tt - pz;
                double dist = Math.sqrt(qx * qx + qz * qz);
                double rough = dist - widthOf(accWOf(t, k)) * 0.5;
                // 插入候选（按 rough 升序，最多 CANDS 个）
                int ins = nCand;
                for (int c = 0; c < nCand; c++) { if (rough < candD[c]) { ins = c; break; } }
                if (ins < CANDS) {
                    int lim = Math.min(nCand, CANDS - 1);
                    for (int c = lim; c > ins; c--) { candD[c] = candD[c-1]; candK[c] = candK[c-1]; }
                    candK[ins] = k; candD[ins] = rough;
                    if (nCand < CANDS) nCand++;
                }
            }
        }
        double best = Double.MAX_VALUE;
        double bestAcc = 0.0;
        final int NS = MEANDER_SAMPLES;
        for (int c = 0; c < nCand; c++) {
            final int k = candK[c];
            final int i = k % W, j = k / W;
            final int d = t.dir[k];
            final int ni = i + DX[d], nj = j + DZ[d];
            double ax2, az2, bx2, bz2;
            final int oa3 = (t.chOrd != null) ? t.chOrd[k] : -1;
            final int ob3 = (t.chOrd != null) ? t.chOrd[nj * W + ni] : -1;
            if (oa3 >= 0 && ob3 >= 0) {
                ax2 = t.mVX[oa3]; az2 = t.mVZ[oa3]; bx2 = t.mVX[ob3]; bz2 = t.mVZ[ob3];
            } else {
                ax2 = ox + (i + 0.5) * STEP_BLK; az2 = oz + (j + 0.5) * STEP_BLK;
                bx2 = ox + (ni + 0.5) * STEP_BLK; bz2 = oz + (nj + 0.5) * STEP_BLK;
            }
            final double ax = ax2, az = az2, bx = bx2, bz = bz2;
            final double vx = bx - ax, vz = bz - az;
            final double vl = vx * vx + vz * vz;
            final double wK = widthOf(accWOf(t, k));
            final double ux = vx / Math.sqrt(vl), uz = vz / Math.sqrt(vl);
            final double pnx = -uz, pnz = ux;               // 段的法向
            // ★ 对【移动后的折线】做密集采样，取点到折线的最小距离
            double prevQx = 0, prevQz = 0;
            boolean has = false;
            double segBest = Double.MAX_VALUE;
            double nearX = 0, nearZ = 0;
            for (int s = 0; s <= NS; s++) {
                final double tt = (double) s / NS;
                final double off = meanderOff(t, k, tt);
                final double qx = ax + vx * tt + pnx * off;
                final double qz = az + vz * tt + pnz * off;
                if (has) {
                    double ex = qx - prevQx, ez = qz - prevQz;
                    double el = ex * ex + ez * ez;
                    double u = el > 0 ? ((px - prevQx) * ex + (pz - prevQz) * ez) / el : 0.0;
                    u = u < 0 ? 0 : (u > 1 ? 1 : u);
                    double cxq = prevQx + ex * u, czq = prevQz + ez * u;
                    double dx = cxq - px, dz = czq - pz;
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd < segBest) { segBest = dd; nearX = cxq; nearZ = czq; }
                } else {
                    double dd = Math.hypot(qx - px, qz - pz);
                    if (dd < segBest) { segBest = dd; nearX = qx; nearZ = qz; }
                }
                prevQx = qx; prevQz = qz; has = true;
            }
            // ★★★ 2026-10-11 修法 A2：判据必须把【蜿蜒包络】算进去（见 待裁决/269）。
            //
            //   【为什么旧判据是错的】
            //     meanderOff 把段横移 off = A(tt) * sin(phi)，而 A 在【段两端之间线性插值】
            //     （:1452 `A += (t.mA[ob] - A) * tt`），mA = MEANDER_AMP * W（Leopold & Wolman）。
            //     ⟹ |off| <= max(A_self, A_dn) = MEANDER_AMP * max(W_self, W_dn)。
            //     而旧判据只允许 W_self/2 ⟹ 横移量是阈值的 4 倍
            //     ⟹ 84% 的河道格被判为【非河道】（实测 84.0%，与理论 84.1% 吻合）。
            //
            //   【实测】（SMOOTH_ROUNDS=0，tile (0,-1)，288 个河道格，用真实 meanderOff）：
            //     旧判据 segBest - W/2                       : 73/288  = 25.3%
            //     只用【本格】A                              : 255/288 = 88.5%
            //     ★ 用【max(本格, 下游)】                     : 287/288 = 99.7%
            //   ⟹ 必须用两端的 max —— 因为段内插值让 |off| 可以【超过本格的 A】
            //     （实测 max|off| = 153.15 > maxA = 120.00）。
            //
            //   【为什么这不是自造公式】包络 = 两端极值 + 半宽，全部由几何与已有常量导出；
            //     零新增常量（MEANDER_AMP / widthOf / accWOf 都是既有的）。
            //
            //   【回滚】恢复 `double de = segBest - wK * 0.5;` 即可。
            // ★★★ 2026-10-11 修正（第二版）：必须用【预计算的 mA】，不要重算 MEANDER_AMP*W。
            //   为什么：mA 里还含【幅度调制】——`:1429` 的
            //     `if (MEANDER_MOD != 0.0) A *= (1.0 + MEANDER_MOD * Math.sin(mod));`
            //   实测：mA = 7.460 而 MEANDER_AMP*W = 5.305（差 1.41 倍 = 1 + MEANDER_MOD）。
            //   全瓦片抽查：mA != MEANDER_AMP*W 的占【100%】，最大差 53.99。
            //   ⟹ 用 mA 直接取两端极值，既准确又不会与调制公式脱节。
            final double wKDn = (ob3 >= 0) ? widthOf(accWOf(t, nj * W + ni)) : wK;
            final double aSelf = (oa3 >= 0) ? t.mA[oa3] : MEANDER_AMP * wK;
            final double aDn   = (ob3 >= 0) ? t.mA[ob3] : aSelf;
            final double env  = Math.max(aSelf, aDn) + Math.max(wK, wKDn) * 0.5;
            double de = segBest - env;
            if (de < best) {
                best = de; bestAcc = t.acc[k];
                double[] pt = TL_PT.get();
                pt[0] = nearX; pt[1] = nearZ; pt[2] = t.acc[k];
            }
        }
        // ★ 把最近河道格【自己的】 acc 存进 ThreadLocal，供 riverWidthBlocks / channelAccAt 用。
        //   为什么不能用双线性插值的 acc：插值会把【非河道格】的低值混进来，
        //   于是河道线在格内游走时河宽会【忽宽忽窄】（实测：用户报的"一段宽一段窄"）。
        //   用河道格自己的 acc ⟹ 沿一条河段宽度恒定，且因为格 acc 沿流向单调 ⟹ 河宽单调。
        double[] ch = TL_CH.get();
        if (ch == null) { ch = new double[1]; TL_CH.set(ch); }
        ch[0] = bestAcc;
        return best;
    }

    private static final ThreadLocal<double[]> TL_CH = new ThreadLocal<double[]>();

    /** ★ 最近河道格【自己的】汇流面积（不是插值）。非河道返回 0。 */
    public static double channelAccAt(long ws, double px, double pz) {
        if (!(channelDistance(ws, px, pz) <= 0.0)) return 0.0;
        double[] ch = TL_CH.get();
        return ch != null ? ch[0] : 0.0;
    }

    /** 汇流面积（格 = 平方公里）。非陆返回 0。 */
    public static double accAt(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double a = sample(t.acc, t, px, pz);
        return a > 0.0 ? a : 0.0;
    }

    /** 是否在河道内（湖不算）。 */
    public static boolean isRiver(long ws, double px, double pz) {
        return channelDistance(ws, px, pz) <= 0.0;
    }

    /** ★ 水面高度（格，取整到 MC 的整数 y）。非河非湖返回 NaN。
     *  = round(filled)：Priority-Flood 保证 filled 沿流向单调不增，取整后仍单调，
     *    因此下游水面永不高于上游，水不会倒流且必然连到海。 */
    public static double riverSurfaceBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return Double.NaN;
        double f = sample(t.filled, t, px, pz);
        double zz = sample(t.z, t, px, pz);
        boolean lake = LAKES_ENABLED && (f - zz) > LAKE_MIN_FILL;
        if (!lake && !isRiver(ws, px, pz)) return Double.NaN;
        return Math.round(f);
    }

    /** 河道宽度（格）。非河道或湖面返回 0。 */
    public static double riverWidthBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double d = sample(t.filled, t, px, pz) - sample(t.z, t, px, pz);
        if (LAKES_ENABLED && d > LAKE_MIN_FILL) return 0.0;     // ★ 湖优先
        // ★ 用【最近河道格自己的 acc】，不用双线性插值 —— 否则河宽会忽宽忽窄。
        double a = channelAccAt(ws, px, pz);
        if (!(a >= ACC_MIN)) return 0.0;
        return widthOf(a);
    }

    /** 是否湖面。 */
    public static boolean isLake(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null || !LAKES_ENABLED) return false;
        return sample(t.filled, t, px, pz) - sample(t.z, t, px, pz) > LAKE_MIN_FILL;
    }

    /** 湖面高度（格）；非湖返回 NaN。 */
    public static double lakeSurfaceBlocks(long ws, double px, double pz) {
        if (!isLake(ws, px, pz)) return Double.NaN;
        Tile t = tileAt(ws, px, pz);
        return sample(t.filled, t, px, pz);
    }

    /** 湖深（格）；非湖返回 0。 */
    public static double lakeDepthBlocks(long ws, double px, double pz) {
        Tile t = tileAt(ws, px, pz);
        if (t == null) return 0.0;
        double d = sample(t.filled, t, px, pz) - sample(t.z, t, px, pz);
        return d > 0.0 ? d : 0.0;
    }

    // ==================== 瓦片与采样 ====================

    /**
     * ★★★★★★★★★ 2026-10-11：本类的【配置指纹】—— 折进 {@link #tileKey}。
     *
     * <h3>为什么必须有（实测缺口）</h3>
     * <p>原先 {@code tileKey} 只含 {@code (seed, tx, tz)} ⟹ <b>改任何常量都不会让缓存失效</b>。
     * 而本类的瓦片缓存是【长驻进程】里的（{@code CACHE} 只在 {@code install/uninstall} 时清），
     * 所以实机里改了 {@code SMOOTH_ROUNDS} / {@code WIDTH_MAX} / {@code MEANDER_*} 之后，
     * <b>旧瓦片会被继续复用</b> ⟹ 「改了代码但游戏里没变」。</p>
     *
     * <p>★ 对照：{@code SimClimate.configStamp()} / {@code WaterField}（经 SimClimate）
     * 都有指纹，本类原先【没有】—— 那是一处真实的架构缺口。</p>
     *
     * <h3>折叠什么</h3>
     * <p>只折叠【影响结果】的量。<b>不折叠</b>：
     * {@code CACHE_TILES}（缓存大小）、{@code PRECIP_DIAG}（诊断开关）、
     * 以及三个已废弃的 {@code *_UNUSED} / {@code _OLD} 常量。</p>
     */
    public static long configStamp() {
        long h = 0x7A14L;   // 版本位
        // ---- 网格与求解窗口 ----
        h = h * 31 + (EROSION_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(STEP_BLK);
        h = h * 31 + SOLVE_CELLS;
        h = h * 31 + OUT_CELLS;
        h = h * 31 + BUFFER_CELLS;
        h = h * 31 + Double.doubleToLongBits(SEA_BLK);
        h = h * 31 + Double.doubleToLongBits(ELEV_TO_BLK);
        // ---- LEM 演化 ----
        h = h * 31 + ITER;
        h = h * 31 + FLOW_EVERY;
        h = h * 31 + Double.doubleToLongBits(DK);
        h = h * 31 + Double.doubleToLongBits(M);
        h = h * 31 + Double.doubleToLongBits(DTU);
        h = h * 31 + Double.doubleToLongBits(GAMMA);
        h = h * 31 + Double.doubleToLongBits(UREF);
        h = h * 31 + Double.doubleToLongBits(COAST_TAPER_M);
        // ---- ⚠ 2026-10-14（待裁决/353）补的【既存】漏键 ----
        //   这些都是【决定地形高度】的常量，却一直不在本键里 ⟹ 改它们不会让瓦片缓存失效
        //   （同 TECTONIC_GATE 那一类；§338 改 CF_LAND_H 时也踩过）。
        h = h * 31 + Double.doubleToLongBits(OROG_PLATE_BAND);
        h = h * 31 + Double.doubleToLongBits(OROG_BAND_MIN);
        h = h * 31 + Double.doubleToLongBits(SUPER_ABS);
        // ---- 抬升噪声 ----
        h = h * 31 + Double.doubleToLongBits(NA_AMP);
        h = h * 31 + Double.doubleToLongBits(NA_WL);
        h = h * 31 + NA_OCT;
        h = h * 31 + Double.doubleToLongBits(NA_TAPER);
        // ---- 降水（影响流量 -> 河宽）----
        h = h * 31 + (PRECIP_ENABLED ? 1 : 0);
        h = h * 31 + (PRECIP_ANALYTIC ? 1 : 0);
        h = h * 31 + PRECIP_SMOOTH;
        h = h * 31 + Double.doubleToLongBits(P_REF_MM);
        h = h * 31 + Double.doubleToLongBits(P_REF_BASE_MM);
        h = h * 31 + Double.doubleToLongBits(P_FALLBACK_MM);
        h = h * 31 + Double.doubleToLongBits(TERRAIN_GAIN);
        h = h * 31 + Double.doubleToLongBits(TERRAIN_H_REF);
        h = h * 31 + Double.doubleToLongBits(TERRAIN_DH_STEP);
        h = h * 31 + Double.doubleToLongBits(TERRAIN_CLIMB_CLAMP);
        h = h * 31 + Double.doubleToLongBits(LAT_GAIN);
        h = h * 31 + Double.doubleToLongBits(MOIST_FLOOR);
        h = h * 31 + UPWIND_SAMPLES;
        // ---- 河道判定与河宽 ----
        h = h * 31 + Double.doubleToLongBits(ACC_MIN);
        h = h * 31 + Double.doubleToLongBits(WIDTH_W0);
        h = h * 31 + Double.doubleToLongBits(WIDTH_MIN);
        h = h * 31 + Double.doubleToLongBits(WIDTH_MAX);
        h = h * 31 + Double.doubleToLongBits(DEPTH_D0);
        h = h * 31 + Double.doubleToLongBits(DEPTH_MIN);
        h = h * 31 + Double.doubleToLongBits(DEPTH_MAX);
        // ---- 蜿蜒（含 Taubin 顶点平滑）----
        h = h * 31 + (MEANDER_ENABLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(MEANDER_AMP);
        h = h * 31 + Double.doubleToLongBits(MEANDER_LAMK);
        h = h * 31 + Double.doubleToLongBits(MEANDER_LAM_MIN);
        h = h * 31 + Double.doubleToLongBits(MEANDER_MOD);
        h = h * 31 + Double.doubleToLongBits(MEANDER_MODW);
        h = h * 31 + MEANDER_SAMPLES;
        h = h * 31 + SMOOTH_ROUNDS;
        h = h * 31 + Double.doubleToLongBits(SMOOTH_LAMBDA);
        h = h * 31 + Double.doubleToLongBits(SMOOTH_MU);
        // ---- 干河道 ----
        h = h * 31 + (DRY_ENABLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(DRY_DEPTH_FRAC);
        h = h * 31 + Double.doubleToLongBits(DRY_PREL_MIN);
        h = h * 31 + Double.doubleToLongBits(DRY_TRANS);
        // ---- 湖泊 ----
        h = h * 31 + (LAKES_ENABLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(LAKE_MIN_FILL);
        h = h * 31 + BOWL_SPACING;
        h = h * 31 + Double.doubleToLongBits(BOWL_R);
        h = h * 31 + Double.doubleToLongBits(BOWL_DEPTH);
        h = h * 31 + Double.doubleToLongBits(BOWL_RIM);
        h = h * 31 + Double.doubleToLongBits(BOWL_MIN_RIFT);
        // ---- ★ N1 裂谷（2026-10-11）----
        h = h * 31 + (RIFT_ENABLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(RIFT_SPLIT);
        h = h * 31 + Double.doubleToLongBits(RIFT_SHOULDER);
        h = h * 31 + (LAKE_SOLVE_ENABLED ? 1 : 0);   // ★ 湖水位解（待裁决/304）
        // ---- ★★★★★ P1 沉积（2026-10-13）----
        //   ⚠ 本键是【正确性】要求（D58 的准入判据：改了结果的旋钮就得让缓存失效）：
        //     tileKey() = ... ^ configStamp()，漏一个键 ⟹ 换配置仍命中旧瓦片 ⟹ 探针看到「零变化」。
        //     本仓已因这个漏键栽过（交接文档 §3 第 10 条：LAKE_WATER_SURFACE 漏键）。
        h = h * 31 + Double.doubleToLongBits(SED_G);
        h = h * 31 + SED_GATE_MODE;
        // ---- ★★★★★ 2026-10-14 侵蚀阈值（347 §七）----
        h = h * 31 + Double.doubleToLongBits(THETA_M);
        h = h * 31 + (THETA_ON ? 1 : 0);
        h = h * 31 + (UNGATE_SURFACE ? 1 : 0);
        h = h * 31 + (NO_DEPRESSION_FILL ? 1 : 0);   // ★ 判别实验开关（进键 = 换它必须重建瓦片）
        // ---- ⚠ 顺手补的四个【既存】漏键（不是 P1 引入的）----
        //   TECTONIC_GATE / RIFT_SINK / RIFT_SEA_DECOUPLED / RIFT_FLOOR_M 都【改结果】却没进本键
        //   ⟹ 同一个 JVM 里改它们再查询会命中旧瓦片。按 D58 的规则补上（不改任何物理）。
        h = h * 31 + (TECTONIC_GATE ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(RIFT_SINK);
        h = h * 31 + (RIFT_SEA_DECOUPLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(RIFT_FLOOR_M);
        return h;
    }

    static int outBlocks() { return (int) Math.round(OUT_CELLS * STEP_BLK); }

    /**
     * ★ 瓦片缓存键。【必须含 seed】。
     *
     * <p><b>为什么</b>：原实现只用 (tx, tz)，于是同一个 JVM 里换世界（换种子）时，
     * 旧种子的瓦片会被新世界命中 ⟹ <b>地形看起来"种子不稳定"</b>。
     * <p>虽然 {@link #install} 会清缓存，但只要有<b>任何一条路径</b>在 install 之前
     * 或之后用别的 ws 查询，就会出现跨种子的脏命中。把 seed 编进键里是【零成本】的根治。
     */
    private static long tileKey(long ws, int tx, int tz) {
        long h = ws * 0x9E3779B97F4A7C15L;
        h ^= (((long) tx) << 32) ^ (tz & 0xFFFFFFFFL);
        // ★★★ 2026-10-11：并入配置指纹（原先【没有】—— 那是真实的缺口）。
        //   没有它时，改 SMOOTH_ROUNDS / WIDTH_MAX / MEANDER_* 等都不会让缓存失效，
        //   实机（长驻进程）里会继续复用旧瓦片 ⟹ 「改了代码但游戏里没变」。
        h ^= configStamp() * 0xC2B2AE3D27D4EB4FL;
        return h;
    }

    /**
     * ★ 取瓦片。**不依赖 installedSeed**。
     *
     * <h3>为什么去掉 installedSeed 门控（实测踩过的坑）</h3>
     * 原实现要求 {@code installedSeed == ws}，否则返回 null。实测诊断输出：
     * <pre>
     *   尝试 641601 点  命中 0  installedSeed=0  worldSeed=179054954  seedMismatch=641601
     * </pre>
     * ⟹ <b>有人用 0 调了 install()</b>，把真实种子覆盖掉了。
     * 推断时序：真实世界 onWorld(179054954) 之后，某个占位世界/别的维度又 onWorld(0)，
     * 于是 installedSeed 从 179054954 变成 0，之后所有水系查询全部返回 null。
     *
     * <p>★ 正确做法：<b>缓存的键里已经含 seed</b>（{@link #tileKey}），
     * 所以「不同种子互相污染」在结构上已经不可能。
     * 与其再加一道会误伤的门控，不如直接用查询传进来的 ws。
     * installedSeed 只保留作【诊断显示】。
     */
    /**
     * ★★★★★ 2026-10-12（待裁决/328）：**不触发建瓦片**的湖查询。
     *
     * <p><b>为什么需要</b>：{@link #isLake} 会触发 LEM 瓦片求解（实测冷瓦片 <b>14.7 秒</b>），
     * 而 {@code mmPerDay} 在气候层被高频调用 ⟹ 全球采样（如 P452 的 110x200 网格跨 <b>529 块</b>瓦片）
     * 需要 <b>2.16 小时</b>。
     *
     * <p><b>语义</b>：瓦片【已建】⟹ 给出真实答案；瓦片【未建】⟹ 返回 {@code false}（保守当作无湖）。
     * <ul>
     *   <li>游戏里：地形瓦片在地形生成时已经建好 ⟹ 查询命中 ⟹ <b>正确</b></li>
     *   <li>探针里：大多数瓦片未建 ⟹ 快速返回 false ⟹ <b>不卡死</b>（代价是低估湖）</li>
     *   <li>★ {@code SimClimate.isBuildingTile()} 期间也走这条路 ⟹ <b>结构上不可能成环</b></li>
     * </ul>
     *
     * <p>⚠ 与 {@link #isLake} 的区别：那条会建瓦片（诊断用），这条不会（生产用）。
     */
    public static boolean isLakePeek(long ws, double px, double pz) {
        if (!LAKES_ENABLED) return false;
        Tile t = peekTile(ws, px, pz);
        if (t == null) return false;
        return sample(t.filled, t, px, pz) - sample(t.z, t, px, pz) > LAKE_MIN_FILL;
    }

    /** 只查缓存，<b>不建瓦片</b>。未命中返回 {@code null}。 */
    private static Tile peekTile(long ws, double px, double pz) {
        if (!EROSION_ON) return null;
        int ob = outBlocks();
        if (ob <= 0 || SOLVE_CELLS < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        int tx = (int) Math.floorDiv((long) Math.floor(px), (long) ob);
        int tz = (int) Math.floorDiv((long) Math.floor(pz), (long) ob);
        return CACHE.get(tileKey(ws, tx, tz));
    }

    private static Tile tileAt(long ws, double px, double pz) {
        if (!EROSION_ON) return null;
        int ob = outBlocks();
        if (ob <= 0 || SOLVE_CELLS < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        // 只为诊断记录：谁最近被查询过（不参与正确性）
        if (installedSeed != (int) ws) installedSeed = (int) ws;
        int tx = (int) Math.floorDiv((long) Math.floor(px), (long) ob);
        int tz = (int) Math.floorDiv((long) Math.floor(pz), (long) ob);
        long k = tileKey(ws, tx, tz);
        Tile t = CACHE.get(k);
        if (t != null) return t;
        t = solve(ws, tx, tz);
        if (t == null) return null;
        CACHE.put(k, t);
        evictIfNeeded();
        return t;
    }

    private static void evictIfNeeded() {
        int cap = CACHE_TILES;
        if (cap <= 0) { if (CACHE.size() > 1) CACHE.clear(); return; }
        int toDrop = CACHE.size() - cap;
        if (toDrop <= 0) return;
        Iterator<Map.Entry<Long, Tile>> it = CACHE.entrySet().iterator();
        while (toDrop-- > 0 && it.hasNext()) { it.next(); it.remove(); }
    }

    /**
     * Cross-fade across LEM tile edges.
     *
     * <p>Neighbouring 512 km tiles are solved independently and each keeps a 128 km buffer,
     * so a point near an edge is covered by both. Without this blend the tiles meet in a
     * hard rectangle every 512 km (visible as straight lines in the terrain).
     */
    /** 0 at the tile's owned boundary, 1 at EDGE_TAPER_BLK inside it. */
    private static double edgeTaper(Tile t, double px, double pz) {
        final double ET = EDGE_TAPER_BLK;
        if (ET <= 0.0) return 1.0;
        final double ob = OUT_CELLS * STEP_BLK;
        final double x0 = t.tx * ob, z0 = t.tz * ob;
        final double dx = Math.min(px - x0, (x0 + ob) - px);
        final double dz = Math.min(pz - z0, (z0 + ob) - pz);
        double dist = Math.min(dx, dz);
        if (dist <= 0.0) return 0.0;
        if (dist >= ET) return 1.0;
        final double u = dist / ET;
        return u * u * (3.0 - 2.0 * u);
    }

    private static double seamBlend(long ws, Tile t, double px, double pz, double v) {
        final double B = SEAM_BLEND_BLK;
        if (B <= 0.0) return v;
        final double ob = OUT_CELLS * STEP_BLK;
        final double gx = (px - t.tx * ob) / STEP_BLK;
        final double gz = (pz - t.tz * ob) / STEP_BLK;
        final double edgeHi = OUT_CELLS - B;
        double wx = 0.0, wz = 0.0, sx = 0.0, sz = 0.0;
        if (gx < B) { wx = 0.5 * (1.0 - gx / B); sx = -ob; }
        else if (gx > edgeHi) { wx = 0.5 * (gx - edgeHi) / B; sx = ob; }
        if (gz < B) { wz = 0.5 * (1.0 - gz / B); sz = -ob; }
        else if (gz > edgeHi) { wz = 0.5 * (gz - edgeHi) / B; sz = ob; }
        if (wx > 0.0) {
            Tile n = tileAt(ws, px + sx, pz);
            if (n != null) v = v * (1.0 - wx) + sample(n.z, n, px, pz) * wx;
        }
        if (wz > 0.0) {
            Tile n = tileAt(ws, px, pz + sz);
            if (n != null) v = v * (1.0 - wz) + sample(n.z, n, px, pz) * wz;
        }
        return v;
    }

    private static double sample(double[] f, Tile t, double px, double pz) {
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = t.tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = t.tz * outBlk - BUFFER_CELLS * STEP_BLK;
        // ★ 半格对齐：f[j*W+i] 存的是【格心】(ox + (i+0.5)*STEP) 的值，
        //   所以先减 0.5 再取整，否则会与 channelDistance 的格心约定差半格
        //   （实测症状：格心处的 channelDistance 报 665 而不是 0）。
        double gx = (px - ox) / STEP_BLK - 0.5, gz = (pz - oz) / STEP_BLK - 0.5;
        int i0 = (int) Math.floor(gx), j0 = (int) Math.floor(gz);
        double fx = gx - i0, fz = gz - j0;
        int i1 = i0 + 1, j1 = j0 + 1;
        if (i0 < 0) { i0 = 0; i1 = 0; fx = 0.0; }
        if (j0 < 0) { j0 = 0; j1 = 0; fz = 0.0; }
        if (i1 >= t.W) { i0 = t.W - 1; i1 = i0; fx = 0.0; }
        if (j1 >= t.W) { j0 = t.W - 1; j1 = j0; fz = 0.0; }
        double a = f[j0 * t.W + i0], b = f[j0 * t.W + i1];
        double c = f[j1 * t.W + i0], d = f[j1 * t.W + i1];
        double u = a + (b - a) * fx, v = c + (d - c) * fx;
        return u + (v - u) * fz;
    }

    // ==================== 抬升场 ====================

    /**
     * ★★★ 实验开关（待裁决/307）：让 LEM 能产生【天然闭合洼地】（= 天然湖）的前置条件。
     * <ul>
     *   <li>{@code RIFT_SEA_DECOUPLED} —— 陆海判定用【不含裂谷】的场 ⟹ 允许「海平面以下的陆地」</li>
     *   <li>{@code RIFT_NEGATIVE_UPLIFT} —— 允许【负抬升】= 真正的构造沉降（原实现 clamp 在 0）</li>
     * </ul>
     * <p><b>为什么必须两个一起</b>：不动点 {@code z_k - zd = up / al}，只要 {@code up >= 0}
     * 就【永不产生坑】；而陆地上 {@code uu = (U - SEA_BLK)/UREF > 0} 恒成立
     * ⟹ 原来的 clamp 从不生效 ⟹ 定理锁死。要松开它，必须让【非海格】也能有 {@code uu < 0}。
     */
    public static volatile boolean RIFT_SEA_DECOUPLED = false;
    public static volatile boolean RIFT_NEGATIVE_UPLIFT = false;
    /**
     * ★★★★★ 2026-10-11（待裁决/308）：**裂谷沉降因子**（乘在抬升上，与海拔无关）。
     *
     * <p><b>为什么必须这样</b>：原式 {@code uu = (U - SEA_BLK)/UREF} 把「抬升速率」与「海拔」
     * 混成了一个量 ⟹ {@code up < 0} 只在 {@code U < 海平面} 时发生
     * ⟹ ★ 只有沿海低地能成坑（实测：天然湖格 **100% 在离岸 20 km 内**、中位 **1.4 km**）。
     *
     * <p>现在：{@code up = upBase * (1 + RIFT_SINK * riftProfile)}。
     * 轴部 {@code riftProfile = -1} ⟹ {@code up = upBase*(1 - RIFT_SINK)}，
     * {@code RIFT_SINK > 1} 时轴部【真的下沉】，**与海拔无关** ⟹ 湖可以出现在任何高度。
     *
     * <p><b>取值出处</b>：真实裂谷沉降 **150~800 m/Ma**（Lake Albert 实测，见 待裁决/302 §二），
     * 与造山带抬升速率【同量级】⟹ {@code RIFT_SINK ~ 1~2}。0 = 关。
     */
    // ★★★ 2026-10-11 裁决：本路线【已否决】—— 实测内陆永远形不成坑（`309`）：
    //   乘因子路线：SINK=1.5 完全无效；扫到 100 仍 100% 沿海、高程 p50 = 57 格。
    //   绝对沉降路线：SINK 扫到 2.0，全图 417 个坑【无一在离岸 20 km 之外】。
    //   ⟹ 在 LEM 的平衡态里「闭合洼地」与「河流下切」互斥，非调参可解。
    //   ⟹ 湖必须由 LEM 之外的机制提供（N1b）。本开关保留 = false/0，供回滚对照。
    public static volatile double RIFT_SINK = 0.0;
    /**
     * ★★★★★ 2026-10-12（待裁决/340）：**构造活动门控** —— 让 LEM 只在构造活跃带演化。
     *
     * <p><b>根因</b>（§339 实测 + 本轮读码）：LEM 的抬升项
     * <pre>
     *   up = DTU * UREF * ((U - SEA_BLK) / UREF)^GAMMA
     * </pre>
     * 是<b>海拔的函数</b>，而<b>不是构造活动的函数</b> ⟹ 任何高出海平面的格子都<b>持续抬升</b>
     * ⟹ LEM 必须靠下切来平衡（定点 z_k − zd = up/al）⟹ <b>处处起伏</b>。
     * <p>实测（ScaleRelief，深内陆 80x80 km，1 km 网格）：
     * <pre>
     *   窗  2 km: 不含 LEM 起伏 17 m（<100m 占 91.3%） →  含 LEM 150 m（<100m 23.9%）
     *   窗 10 km: 不含 LEM 起伏 92 m（<100m 占 57.1%） →  含 LEM 379 m（<100m  0.0%）
     *   ⟹ ★★ 底场本来就是大平原，是 LEM 把它切碎的
     * </pre>
     * <p><b>物理依据</b>：真实世界的抬升来自<b>构造</b>（板块运动）。稳定大陆内部
     * （如华北平原、北美大平原）抬升 ≈ 0 ⟹ <b>没有持续下切</b> ⟹ 河流达到均衡 ⟹ 平原。
     *
     * <p><b>修法</b>：up_eff = upBase * activity，activity ∈ [0,1] 由<b>构造活动</b>给出：
     * <pre>
     *   activity = clamp01( orogenyM / OROG_MAX_M ) + clamp01( |riftProfile| )
     * </pre>
     * ★ <b>零新参数</b>：orogenyM 的尺度用现成的 OROG_MAX_M（7500 m）归一化；
     * riftProfile 本身就在 [-1, 1]。
     * ★ 处处连续（不产生掩码边界的不连续）。
     *
     * <p>★ 回滚：置 false ⟹ 逐位回到现状。
     */
    public static volatile boolean TECTONIC_GATE = true;
    /**
     * ★★★★★ P1（2026-10-13）：**沉积系数 G**（无量纲）—— LEM 的沉积项。
     *
     * <p><b>方程</b>（Davy &amp; Lague 2009, JGR 114, F03007；Guerit et al. 2019, Geology 47, 853-856）：
     * <pre>
     *   dh/dt = U - E + D
     *     E = K * A^m * S^n         （= 本仓的 al = DK*acc^M，坡度隐式；见 solve 的隐式迭代式）
     *     D = G * Qs / A            （Qs = 上游净侵蚀通量）
     * </pre>
     *
     * <p><b>落地形式</b>（与 solve 的隐式迭代同量纲，格/步）：
     * <pre>
     *   z_k <- zEro_k + G * Qs_k / acc_k        Qs_k = Sum_{上游含自身} max(0, E_j)
     * </pre>
     * ★ Qs 用<b>本步</b>的 E 累积（不是滞后一步）；累积沿 z 的<b>反序</b>（= 上游→下游）一次 O(N) 完成。
     * ★ E 只取正部 ⟹ 洼地的「自填」（al*(z-zd) &lt; 0）<b>不计入</b>产沙。
     *
     * <p><b>出处（逐条可查）</b>：
     * <ul>
     *   <li><b>D = G*Qs/A</b>：Yuan, Braun, Guerit, Rouby &amp; Cordonnier 2019（JGR-ES 124, 1346-1365）
     *       式(10) 逐字：{@code + G/(p~A) * Integral_A (U - dh/dt) dA}；式(32) 逐字
     *       {@code K'_f = K_f/(1 + G/p~)} ⟹ 沉积把<b>有效侵蚀系数</b>降为 1/(1+G)。
     *       ★ 全文提取件：{@code K:\moder\Talos2\build\eoh_probe\refs\gfz_erosion_deposition.txt}</li>
     *   <li><b>G 的物理定义</b>：同文式(9) {@code G = d* * vs / p0}（跃移长度 x 沉速 / 降水）⟹ 量纲为一。</li>
     *   <li><b>G 的取值范围</b>：Guerit et al. 2019（Geology 47(9) 853-856，由 <b>29 组实验</b>流域-冲积扇
     *       + <b>68 个自然样本</b>估计）摘要逐字：continuum from detachment-limited
     *       (<b>G = 0</b>) to transport-limited behavior (<b>G &gt; 0.4</b>)；
     *       Yuan et al. 2019：典型参数（p = 1e-7 m/s, vs in [1e-6, 1e-1] m/s）下 G/p~ 为
     *       <b>order 1 or greater</b>。</li>
     * </ul>
     *
     * <p>★ <b>回滚</b>：置 {@code 0.0} ⟹ <b>逐位</b>回到纯侵蚀 LEM（沉积代码路径整体不执行，零开销）。
     *
     * <h3>★★★★★ 2026-10-14 用户【二次裁决】：0.0 -> <b>0.6</b>（开着沉积）</h3>
     *
     * <p><b>第一次裁决（A：保持 0.0）为什么被推翻</b>：当时反对开沉积的<b>唯一硬理由</b>是
     * 「它会把湖清光（1.53% -&gt; 0.10%）」——而那次扫描是<b>「解冻 + 沉积，尚无阈值」</b>。
     * {@link #THETA_ON} 落地之后，那个理由<b>反转</b>了。
     *
     * <p><b>阈值之上重扫（三种子，其余全用生产默认）</b>：
     * <pre>
     *   G        湖%            湿地%          积水合计%      zMax m
     *   0.0      1.07/0.98/1.99 7.31/7.67/6.41  8.37/8.65/8.41  3785
     *   0.6      1.39/1.71/3.07 6.17/6.40/5.24  7.56/8.12/8.30  5306
     *   ⟹ 单调：积水↓、湿地↓、湖↑、河↑（河谷切得更深）
     * </pre>
     *
     * <p>⚠ <b>2026-10-14 用户裁决：完全回退【海陆/造山】改动，只保留【水系】改动</b> ⟹
     * 造山那一线（OROG_MAX_M / OROG_PLATE_BAND / SUPER_ABS / 淡化修复）<b>已全部撤销</b>。
     * 因此本常量现在的正当性<b>只来自水系本身</b>（下表的积水/湿地/湖），
     * <b>不再引用任何"山高"论证</b>（那段论证对应的配置已经不存在了）。
     *
     * <p><b>取值出处</b>：{@code G = 0.6} 落在 Guerit et al. 2019 的连续谱内
     * （detachment-limited G=0 → transport-limited G&gt;0.4），且高于分界值 —— 与
     * 「本世界要有宽河谷 + 高山」的设计意图一致。★ <b>不是拟合出来的数</b>。
     *
     * <p>⚠ <b>代价</b>：沉积两趟 O(N) 每步都跑（热态 solve 约 +20%，实测 10.0 -&gt; 12.3 s/tile）；
     * 山更高（zMax 3785 -&gt; 5306 @UMAX=7500）；抬高地形会同时动<b>气候侧</b>看到的高程
     * ⟹ <b>改本值必须重跑完整验收并逐门归因</b>。
     */
    public static volatile double SED_G = 0.6;
    /**
     * ★ P1：沉积项的作用范围（与 {@link #TECTONIC_GATE} 的交互口径）。
     * <pre>
     *   0 = 沉积只在构造活跃区（dep *= activity）—— 与现状门控同口径（最保守）
     *   1 = 沉积全境，抬升与侵蚀仍按现状【整体】门控（= 交接文档 P1 说的「只加 D 项」第一版）
     *   2 = 只门控【抬升】：侵蚀与沉积全境生效（物理最自洽：稳定大陆内部无构造抬升，
     *       但河流的侵蚀 / 沉积照常发生）
     * </pre>
     * <p><b>三个口径各自的问题（必须先说清楚）</b>：
     * <ul>
     *   <li>口径 0：act=0 的平原上沉积量恒为 0 ⟹ <b>达不到 P1 的目的</b>（平原洼地不动）。</li>
     *   <li>口径 1：act=0 的平原上【只有沉积、没有侵蚀】⟹ <b>无平衡项</b>，平原单调抬升
     *       （每步 +G*Qs/acc，200 步累积）⟹ 质量不守恒。</li>
     *   <li>口径 2：物理最自洽，但它是 §340 那个<b>失败写法</b>的推广 —— SED_G = 0 且口径 2
     *       就逐位等于那个失败版（地形数学上全平 ⟹ D8 退化 ⟹ 放射状河网）。
     *       ⟹ 代码里口径 2 <b>只在 SED_G &gt; 0 时生效</b>，保证 SED_G = 0 永远逐位回滚。</li>
     * </ul>
     * ⟹ 用扫描决定（见 P1 报告）。
     */
    public static volatile int SED_GATE_MODE = 2;
    /**
     * ★★★★★ 2026-10-14（待裁决/347 §七）：**侵蚀阈值 theta** —— 修复"稳定内陆被削到海平面"的根因。
     *
     * <p><b>为什么需要它</b>（实测）：本仓隐式式在 {@code up_eff = 0} 的平原上不动点<b>精确等于</b>
     * {@code z = zd}，而 {@code zd} 有海平面地板 ⟹ 整个平原【代数上必然】收敛到正好等于海平面
     * （实测：湿地高程 p10/p50/p90 = 0/0/3 米，三个种子一致；44% 陆地坡度 &lt; 2 m/km，
     * 其中 89% 是 1 米级浅坑）。文献侧同一结论：无阈值时地形衰减 tau ~ 2~3 Myr（Braun &amp; Deal 2023）。
     *
     * <p><b>形式</b>（Theodoratos &amp; Kirchner 2020, ESurf 8:505 <b>Eq.1</b>，开放获取）：
     * <pre>
     *   sqrt(A)*|grad z| &lt;= theta ⟹ dz/dt = D*lap(z) + U          （坡面：零下切）
     *   sqrt(A)*|grad z| &gt;  theta ⟹ dz/dt = -K*(sqrt(A)*|grad z| - theta) + D*lap(z) + U
     * </pre>
     * ★ {@code sqrt(A)*|grad z|} 就是<b>归一化陡峭指数 k_sn = S * A^(m/n)</b>，本仓 m/n = 0.5（M = 0.5）
     * ⟹ theta 的物理含义 = <b>阈值陡峭指数</b>，量纲 = <b>长度（米）</b>（T&amp;K 原文逐字：
     * "theta must have the same dimensions as sqrt(A)|grad z|, i.e., dimensions of H"）。
     *
     * <p><b>落地形式（我推导，含自洽性验算）</b>：阈值在驱动量上等价于<b>减一个常数</b>
     * <pre>
     *   c = DK * THETA_M * ELEV_TO_BLK                 [格/步]
     *   zNew = (z + upEff + c + al*zd)/(1+al)          // 超阈值：正常下切
     *   若 al*(z - zd + upEff) &lt;= c ⟹ zNew = z + upEff  // ★ 零侵蚀区
     * </pre>
     * ★ 不动点从 {@code z - zd = up/al} 变成 {@code z - zd = (up + c)/al}
     * ⟹ <b>平原（up = 0）也获得有限正坡度</b>：{@code Delta_z = c/al = theta*ELEV_TO_BLK/sqrt(acc)}
     * ⟹ 换成物理量就是 <b>S = theta / sqrt(A)</b> —— 与 T&amp;K 判据在等号处完全一致（自洽性检验）。
     * 数值例：theta = 25 m 时，acc = 100 km² ⟹ 平衡坡度 2.5 m/km；acc = 1 km² ⟹ 25 m/km。
     *
     * <p><b>取值与出处</b>：T&amp;K 2020 <b>Table 2</b>（共同参数 K = 2e-6 /a、D = 0.005 m²/a、
     * U = 5e-5 m/a，原文注明为 "typical in the literature"）：
     * <pre>
     *   N_theta = K*theta/U :  0   0.1  0.2  0.4  0.8  1    1.6  2    4
     *   theta (m)           :  0   2.5  5    10   20   25   40   50   100
     * </pre>
     * ★ 同文逐字："the incision threshold values theta of some of the nine landscapes are
     * <b>significantly higher than natural values reported in the literature</b>
     * (e.g., Prosser &amp; Dietrich, 1995)" ⟹ <b>自然值 &lt; 25 m</b>。
     *
     * <p>⚠ <b>两个必须承认的坑</b>：
     * <ol>
     *   <li>★★ <b>分辨率依赖（已实测，2026-10-14）</b>：theta 的判据在 T&amp;K 的 <b>50 m 尺度</b> TIN 上
     *       标定，而我们的 {@code |grad z|} 是 <b>1 km 格尺度</b>。
     *       实测（同一块 256 km 的地、四档格距、陆地面积四档相同）：
     *       <pre>
     *         STEP_BLK   2000 m   1000 m   500 m   250 m
     *         坡度 p50    10.6     18.8    34.6    63.6   [m/km]
     *         拟合        |grad z| ∝ dx^(-0.86)      （2000→250 预测 5.98x，实测 6.00x）
     *       </pre>
     *       ⟹ <b>theta 只对【当前 STEP_BLK = 1000 m】成立</b>；若改格距，必须按
     *       {@code theta_new = theta_old * (dx_new/dx_old)^(-0.86)} 换算，否则零侵蚀区的面积会变
     *       （格变细 ⟹ 判据更难过 ⟹ 零侵蚀区缩小）。<b>这不是从文献换来的，是本仓实测的。</b>
     *       <p>★ 同一实测还有一个副产品：<b>本仓地形的粗糙度指数 H ≈ 0.14</b>（由 |grad z| ∝ dx^(H-1)），
     *       而真实地形 H ≈ 0.7~0.8 ⟹ 我们的地表在格尺度上"过糙"，这正是<b>缺坡面扩散</b>的表现
     *       （扩散会把 H 抬向真实值，也会让判据对分辨率更稳健）。</li>
     *   <li><b>{@code N_theta = K*theta/U} 对本仓退化</b>：内陆 {@code U = 0} ⟹ 按它定义 theta 得 0
     *       ⟹ 平原不受保护。⟹ <b>必须用绝对 theta（米）</b>。</li>
     * </ol>
     * <p>★ 尺度自洽旁证（不是出处）：本仓平原坡度 p50 = 26.5 m/km、1 km 格 = 10^6 m²
     * ⟹ k_sn = 26.5 m ⟹ <b>theta = 25 m 恰好卡在这个数上</b>。
     *
     * <p>★ 回滚：{@link #THETA_ON} = false ⟹ 逐位回到改动前。
     */
    public static volatile double THETA_M = 25.0;
    /**
     * ★ 阈值总开关。默认 <b>false = 逐位回滚位</b>。
     * <p>⚠ 关闭时【连"零侵蚀区分支"都不走】—— 因为无阈值时那条分支会改掉
     * "自填/淤积"分支的行为（z_new &gt; z + up 的那一支），破坏逐位等价。
     */
    public static volatile boolean THETA_ON = true;   // ★ 2026-10-14 实测定案（347 §八）：默认打开
    /**
     * ★★ 2026-10-14：**把"解除门控"从 SED_G 里解耦出来**（接口修复，347 §七）。
     *
     * <p><b>为什么要解耦</b>：P1 把"口径 2（只门控抬升）"写成 {@code sedOn && sedMode == 2}
     * ⟹ 打开沉积会【顺带】解除平原的侵蚀门控，两者绑死。后果：① 无法单独测"门控 vs 阈值"；
     * ② 外部看像是"沉积把平原变成湿地"（实测：G=1e-300 时湿地 46%，与沉积无关）。
     *
     * <p><b>语义</b>：
     * <pre>
     *   false（默认）：现状 —— up 不乘 activity，整个更新最后按 activity 插值（平原冻结）
     *   true        ：只门控【抬升】—— up 乘 activity，侵蚀 / 沉积 / 阈值全境生效
     * </pre>
     * ★ 默认 false ⟹ 逐位回到改动前。★ 旧行为（SED_G &gt; 0 且口径 2）现在等价于
     * {@code SED_G &gt; 0 且 UNGATE_SURFACE = true}（**有意的行为变更**）。
     */
    public static volatile boolean UNGATE_SURFACE = true;   // ★ 2026-10-14 实测定案：默认打开（只门控抬升）
    /**
     * ★★★ 判别实验（2026-10-13，P1 的最后一个缺口）：**禁止"洼地自填"**。
     *
     * <p><b>要判别什么</b>：口径 2（只门控抬升）会把湖【全部】清掉（湖 1.53% -> 0.10%），
     * 但"清掉"有两个互相竞争的候选原因，只看结果分不开：
     * <pre>
     *   候选 ① 洼地自填：洼地格的 dir 指向溢出口（t.par）⟹ zd = 溢出口高度 > 自身
     *          ⟹ al*(z-zd) &lt; 0 ⟹ 该格每步被【抬高】⟹ 200 步内填平。
     *          ⟹ 本开关【关掉它】。
     *   候选 ② 平原被整体侵蚀削平：解冻后平原朝下游下切、起伏被抹平，盆地被河流切穿。
     *          ⟹ 本开关【不影响它】（上游来水方向的下切照旧）。
     * </pre>
     *
     * <p><b>实现</b>：{@code if (zd &gt; z[k]) zd = z[k]} ⟹ 下游比本格高的格子（= 洼地/湖格）
     * 只保留抬升项，不再被抬高；正常下坡格的 {@code zd &lt; z[k]} ⟹ 钳位不生效 ⟹ <b>侵蚀逐位不变</b>。
     *
     * <p><b>判读</b>（口径 2 + 极小 G 使沉积严格为零，即 §340 的失败写法作底）：
     * <pre>
     *   NO_DEPRESSION_FILL=false ⟹ 湖 ~0.02%（已知）
     *   NO_DEPRESSION_FILL=true  ⟹ 若湖【回来】（&gt;1%）⟹ 候选 ① 成立，"湖 = 自填被门控冻结的产物"
     *                              若湖仍然 ~0   ⟹ 候选 ② 才是主因，P1 的因果判断要改写
     * </pre>
     * ★ 默认 {@code false} ⟹ 逐位不改动现状（回滚位）。
     * ⚠ 本开关**不是**一个"物理选项"，是一次性的判别工具；判读完应删或永久置 false。
     */
    public static volatile boolean NO_DEPRESSION_FILL = false;
    /**
     * ★★ 修法 B2（待裁决/310）：裂谷**不得把陆地压成海** —— 抬升场的最低高度（米）。
     * <p>为什么必须钳：{@code t.sea[k] = (baseHeight <= 0)} ⟹ 裂谷把陆地压到 0 以下
     * ⟹ 那一格【变成海】⟹ ★ 把大陆劈成两块（用户实测指认）。
     * <p>钳在 1 米 ⟹ 永远是陆地；且接近海平面时是【平地】⟹ {@code fill()} 不会给它积水
     * ⟹ 「被切了但没成湖的那块地」还回来了。
     */
    public static volatile double RIFT_FLOOR_M = 1.0;

    /** 抬升场（米）= 海岸剖面 + 造山 + 中尺度扰动（海岸淡化）+ ★ 裂谷。 */
    public static double baseHeight(long ws, double px, double pz) {
        // ★ 2026-10-22：可插拔 uplift 来源（默认关，关掉时逐位不变）。
        //   LandV3 把构造抬升建在【连续标量场】上（9 邻域速度方差 + 大陆度），
        //   不依赖 Voronoi 距离；本方法就是 LEM 的抬升输入 U，所以接在这里
        //   ⟹ LEM 会把这块抬升演化成排水平衡态（分叉脊 + 河谷 + 水系）。
        if (com.EyeOfHarmonyBuffer.sim2.land.LandV3.ENABLED) {
            return com.EyeOfHarmonyBuffer.sim2.land.LandV3.height(ws, px, pz);
        }
        double h = baseHeightRaw(ws, px, pz);
        // ★★★★★ N1a（2026-10-11）：裂谷下沉【进抬升场】。
        //   本方法就是 LEM 的抬升场 U（见 solve 里的 t.U[k] = SEA_BLK + m*ELEV_TO_BLK），
        //   所以加在这里 ⟹ LEM 会自己把裂谷谷地【演化】出来（不是事后挖）。
        //   ★ 幅值沿用 BOWL_DEPTH（16 格 = 889 m）、肩/轴比沿用 RIFT_SHOULDER ⟹ 零新参数。
        if (RIFT_ENABLED && RIFT_SPLIT != 0.0 && h > 0.0) {
            // ★★★ 修法 D（待裁决/310）：**海岸淡化**。照抄造山当年为【同一个问题】做的处置
            //   （TalosLandField:2249-2252 逐字：「否则造山会在海岸线上跳变式全量加入，
            //     实测在造山带里 1 格水平距离内高度跳 1389 米 = 海岸墙」）⟹ 零新参数。
            //   ★ 为什么裂谷也必须淡化：裂谷贴着海岸时会把陆地压到海平面以下
            //     ⟹ sea[] 翻面 ⟹ 【把大陆劈成两块】。
            double ta = OROG_COAST_TAPER_M > 0.0
                    ? smoothstep(0.0, OROG_COAST_TAPER_M, h) : 1.0;
            if (ta > 0.0) {
                h += ta * RIFT_SPLIT * (BOWL_DEPTH / ELEV_TO_BLK)
                   * riftProfile(ws, px, pz, RIFT_SHOULDER);
                // ★ 修法 B2：裂谷不得把陆地压成海（钳在 1 米 ⟹ 永远是陆地）。
                if (h < RIFT_FLOOR_M) h = RIFT_FLOOR_M;
            }
        }
        return h;
    }

    /** ★ 不含裂谷项的抬升场（米）—— 用于【陆海判定】（待裁决/307 实验）。 */
    public static double baseHeightRaw(long ws, double px, double pz) {
        double sig = signedCoastDistCF(ws, px, pz);
        double b = coastProfileCF(sig);
        if (b <= 0.0) return b;
        // ★ 必须与 height0 【逐位同口径】，否则 LEM 开/关之间会出现台阶。
        //   造山的海岸淡化也要一样（[131]）。
        double ot = OROG_COAST_TAPER_M > 0.0
                ? smoothstep(0.0, OROG_COAST_TAPER_M, b) : 1.0;
        double h = b + (ot > 0.0
                ? OROG_MAX_M * orogeny01(ws, px, pz) * ot : 0.0);
        if (NA_AMP != 0.0 && NA_WL > 0.0 && NA_OCT > 0) {
            double taper = smoothstep(0.0, NA_TAPER, b);
            if (taper > 0.0) h += NA_AMP * taper * fbm(ws ^ 0x3CL, px, pz, NA_WL, NA_OCT);
        }
        return h;
    }

    /** fBm，值域 [-1,1]。 */
    static double fbm(long seed, double x, double y, double wl, int oct) {
        double v = 0.0, amp = 1.0, sum = 0.0, f = 1.0 / wl;
        for (int k = 0; k < oct; k++) {
            double n = cnoise(x * f, y * f, 1.0, seed + k * 0x9E3779B9L);
            v += amp * (2.0 * n - 1.0);
            sum += amp; amp *= 0.5; f *= 2.0;
        }
        return sum > 0.0 ? v / sum : 0.0;
    }

    // ==================== 瓦片 ====================

    static final class Tile {
        final int tx, tz, W;
        final double[] z, acc, filled, U;
        /**
         * ★★★ 有效汇流面积（河宽 + 干河道判据都用）= 流域内【逐格降水的累积】。
         *
         * <pre>
         *   accW[k] = accW[上游] + pRel[k]        （单流向 D8，按 acc 升序传播）
         * </pre>
         *
         * <p><b>为什么不是 pRel x acc（实测踩过的坑）</b>：acc 沿流向单调不减，但 pRel 不是
         * （由局部地形/纬度决定）⟹ pRel x acc 可能【下游小于上游】⟹ 出现
         * <b>「上游有水、下游无水」</b>这种物理上不可能的情况（用户指出）。
         * 累加形式天然单调不减 ✓
         *
         * <p>★ 而且在 pRel 均匀时 Sum pRel = pRel x acc，与旧式【等价】⟹ 河宽标定不用重做。
         * <p>★ 顺带解决另一个坑：accW/acc = 流域【平均】降水，不再是「河道所在点」的降水
         * （河道在低地，点降水系统性偏低）。
         *
         * <p>PRECIP_ENABLED=false 时【就是 acc 本身】。
         */
        double[] accW;
        /** ★ 临时：逐格的相对降水 pRel（precipField 填，meanderField 累加后不再需要）。 */
        double[] pRelTmp;
        final byte[] dir;
        final boolean[] sea, done;
        final int[] par;
        /** ★ 蜿蜒场（只对河道格分配，见 meanderField）。chOrd[k] = 河道序号，-1 = 非河道。 */
        int[] chOrd;
        int nCh;
        /** 按河道序号索引：弧长 / 累积相位 / 幅度 / 河宽。 */
        double[] mS, mPhi, mA, mW, mMod;
        /** ★ Taubin 平滑后的顶点位置（世界坐标）。段两端用它 ⟹ 消除 D8 的 45 度折角。 */
        double[] mVX, mVZ;
        /** ★★ 沿流向【单调不减】的湿润度（干河道判据用）。见 meanderField 的传播。 */
        double[] mWet;
        Tile(int tx, int tz, int w) {
            this.tx = tx; this.tz = tz; this.W = w;
            this.z = new double[w * w]; this.acc = new double[w * w];
            this.filled = new double[w * w]; this.U = new double[w * w];
            this.dir = new byte[w * w]; this.sea = new boolean[w * w];
            this.done = new boolean[w * w]; this.par = new int[w * w];
        }
    }

    /** 解一个瓦片。线程安全（只用局部缓冲）。 */
    static Tile solve(long ws, int tx, int tz) {
        long t0 = System.nanoTime();
        phT = t0;
        int W = SOLVE_CELLS;
        if (W <= 0 || OUT_CELLS <= 0 || BUFFER_CELLS < 0) return null;
        if (W < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        Tile t = new Tile(tx, tz, W);
        int N = W * W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = tz * outBlk - BUFFER_CELLS * STEP_BLK;
        // ---- hoisted hot-loop terms (bit-identical) ----
        // al depends only on acc (recomputed every FLOW_EVERY), up depends only on the
        // static U field, nk on the D8 direction. Recomputing them per cell per iteration
        // cost two Math.pow + two integer divisions on 200 x 590k = 118M updates.
        final double[] alBuf = new double[N];
        final double[] upBuf = new double[N];
        final int[] nkBuf = new int[N];


        for (int j = 0; j < W; j++) {
            double wz = oz + j * STEP_BLK;
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                final double gx = ox + i * STEP_BLK;
                double m = baseHeight(ws, gx, wz);
                // ★ U 必须存【格】，与更新式同一量纲（米会把 uu 放大 55.6 倍 -> 地形被抬飞）
                t.U[k] = SEA_BLK + m * ELEV_TO_BLK;
                t.z[k] = SEA_BLK + m * ELEV_TO_BLK;
                // ★ 实验（待裁决/307）：陆海判定改用【不含裂谷】的场
                //   ⟹ 允许「海平面以下的陆地」⟹ U 可以 < SEA_BLK ⟹ up 允许负值
                //   ⟹ LEM 真正【下沉】⟹ 才可能演化出天然闭合洼地（天然湖）。
                t.sea[k] = (RIFT_SEA_DECOUPLED ? baseHeightRaw(ws, gx, wz) <= 0.0 : m <= 0.0);
                final double uu0 = (t.U[k] - SEA_BLK) / UREF;
                upBuf[k] = uu0 > 0.0 ? DTU * UREF * Math.pow(uu0, GAMMA) : 0.0;
            }
        }

        ph(PH_INIT);
        int fe = Math.max(1, FLOW_EVERY);
        long[] heap = new long[N];
        int[] ord = new int[N];
        int[] tmp = new int[N];
        int[] cnt = new int[256];
        double[] uBuf = new double[N];

        // ★★★★★ P1（2026-10-13）：沉积项（Davy & Lague 2009）的局部配置与缓冲。
        //   ★ SED_G = 0 时两个缓冲都是 null ⟹ 热循环里只多一次 null 判断，且【逐位】等于改动前。
        //   ★ 配置在这里【读一次】存成局部量：热循环 200 x 590k 次，volatile 读不能进循环（铁律①）。
        final double sedG = SED_G;
        final int sedMode = SED_GATE_MODE;
        final boolean sedOn = sedG > 0.0;
        final boolean noSelfFill = NO_DEPRESSION_FILL;   // ★ 判别实验（见该常量的 javadoc）
        // ★★★★★ 2026-10-14（347 §七）：侵蚀阈值的局部配置。
        //   cThr = DK * THETA_M * ELEV_TO_BLK（推导见 THETA_M 的 javadoc；格/步）。
        final boolean thetaOn = THETA_ON;
        final boolean ungateSurface = UNGATE_SURFACE;
        final double cThr = thetaOn ? DK * THETA_M * ELEV_TO_BLK : 0.0;
        final double[] eBuf = sedOn ? new double[N] : null;    // 本步逐格净侵蚀量（格/步，只取正部）
        final double[] qsBuf = sedOn ? new double[N] : null;   // 上游累积产沙 Qs（格/步）

        // ★ 2026-10-11（待裁决/308）：裂谷沉降因子【逐格预计算一次】。
        //   ⚠ 绝不能放进 ITER 循环：riftProfile 约 0.4 us/次，200 次迭代 x 590k 格 = 47 秒。
        //   这里只算一次：590k x 0.4 us = 0.24 s（相对瓦片求解 6~10 s 约 3%）。
        // ★★★★★ 2026-10-12（待裁决/340）：构造活动权重【逐格预计算一次】。
        //   ⚠ 绝不能放进 ITER 循环：orogenyM ~0.18us + riftProfile ~0.21us，
        //     200 次迭代 x 590k 格 = 46 秒。这里只算一次：590k x 0.39us = 0.23 s（约 3%）。
        double[] actBuf = null;
        if (TECTONIC_GATE) {
            actBuf = new double[N];
            for (int j = 0; j < W; j++) {
                final double gz = oz + j * STEP_BLK;
                for (int i = 0; i < W; i++) {
                    final int k = j * W + i;
                    final double gx = ox + i * STEP_BLK;
                    // LandV3 carries its own tectonic field, so the activity gate must be
                    // derived from the NEW uplift. The legacy orogenyM /
                    // riftProfile path brought its own Voronoi rift network along, which was
                    // then etched into the LandV3 terrain as a polygon mesh (observed).
                    if (com.EyeOfHarmonyBuffer.sim2.land.LandV3.ENABLED) {
                        // The uplift field is BROAD (f(d) spans 1800 km), so a linear gate is
                        // ~1 almost everywhere and the relief ran to 13 km. Smoothstep it so
                        // only the belt cores are fully active (bare field stays put).
                        // Calibrated from the measured uplift distribution over land:
                        //   p50=184  p80=2938 (0.07 U0)  p95=15801 (0.38 U0)  max=42825
                        // Gate = 0 below p80, 1 above p95: the same "top ~20% of land
                        // is active" extent the legacy gate had, but data-derived.
                        final double a = (com.EyeOfHarmonyBuffer.sim2.land.LandV3.uplift(ws, gx, gz)
                                - com.EyeOfHarmonyBuffer.sim2.land.LandV3.U0 * 0.07)
                                / (com.EyeOfHarmonyBuffer.sim2.land.LandV3.U0 * 0.31);
                        final double t01 = a > 1.0 ? 1.0 : (a < 0.0 ? 0.0 : a);
                        actBuf[k] = ACT_MAX * t01 * t01 * (3.0 - 2.0 * t01);
                    } else {
                        double aTmp = 0.0;
                        if (OROG_ON && OROG_MAX_M > 0.0) {
                            double om = orogenyM(ws, gx, gz);
                            if (om > 0.0) aTmp += om / OROG_MAX_M;
                        }
                        aTmp += Math.abs(riftProfile(ws, gx, gz, RIFT_SHOULDER));
                        actBuf[k] = aTmp > 1.0 ? 1.0 : aTmp;
                    }
                }
            }
        }
        double[] sinkBuf = null;
        if (RIFT_SINK > 0.0) {
            sinkBuf = new double[N];
            for (int j = 0; j < W; j++) {
                final double gz = oz + j * STEP_BLK;
                for (int i = 0; i < W; i++) {
                    final int k = j * W + i;
                    sinkBuf[k] = RIFT_SINK
                            * riftProfile(ws, ox + i * STEP_BLK, gz, RIFT_SHOULDER);
                }
            }
        }

        if (sinkBuf != null) {
            for (int k = 0; k < N; k++) upBuf[k] += DTU * UREF * sinkBuf[k];
        }

        ph(PH_GATE);
        prefetchClimate(ws, tx, tz, ox, oz);

        for (int it = 0; it < ITER; it++) {
            if (it % fe == 0) {
                fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
                for (int kk = 0; kk < N; kk++) {
                    alBuf[kk] = DK * Math.pow(t.acc[kk], M);
                    int d0 = t.dir[kk];
                    if (d0 < 0 || d0 >= 8) { nkBuf[kk] = -1; continue; }
                    int ni0 = kk % W + DX[d0], nj0 = kk / W + DZ[d0];
                    nkBuf[kk] = (ni0 < 0 || ni0 >= W || nj0 < 0 || nj0 >= W) ? -1 : nj0 * W + ni0;
                }
            }
            orderAsc(t.z, N, ord, tmp, cnt);
            if (SEAM_RING > 0) {
                final int R = SEAM_RING;
                for (int j = 0; j < W; j++) {
                    for (int i = 0; i < W; i++) {
                        if (i < R || j < R || i >= W - R || j >= W - R) {
                            final int kk = j * W + i;
                            t.z[kk] = t.U[kk];
                        }
                    }
                }
            }
            for (int oi = 0; oi < N; oi++) {
                int k = ord[oi];
                if (eBuf != null) eBuf[k] = 0.0;   // ★ P1：默认本步无产沙（下面的 continue 会跳过赋值）
                if (t.sea[k]) continue;
                final int nk = nkBuf[k]; if (nk < 0) continue;
                double al = alBuf[k];
                double up = upBuf[k];
                // ★★★★★ 2026-10-11（待裁决/308）：裂谷沉降 = 乘在抬升上的因子，与海拔无关。
                //   轴部 riftProfile = -1 ⟹ up = upBase*(1 - RIFT_SINK)。RIFT_SINK > 1 ⟹ 真的下沉。
                // ★ 沉降是【绝对量】：与海拔无关 ⟹ 任何高度都能压成负。
                //   ⚠ 乘因子路线已实测失败：upBase ∝ uu^GAMMA ∝ 离海平面高度 ⟹ 越高越压不动
                //     （实测湖格 100% 仍在离岸 20 km 内、高程 p50 = 57~63 格）。
                //   DTU*UREF = uu=1 时的抬升量，是现成的量 ⟹ 除 RIFT_SINK 外零新参数。
                double nb = BASE_DOWN
                        ? (BASE_BLEND * t.z[nk] + (1.0 - BASE_BLEND) * t.U[nk])
                        : t.z[nk];
                double zd = nb > SEA_BLK ? nb : SEA_BLK;
                // ★★★ 判别实验：禁止"洼地自填" ⟹ 下游高于本格时钳到本格。
                //   正常下坡格 zd < z[k] ⟹ 本行不生效 ⟹ 侵蚀逐位不变。
                if (noSelfFill && zd > t.z[k]) zd = t.z[k];
                // ★★★★★ 2026-10-12（待裁决/340）：构造活动门控。
                //   ⚠⚠ 第一版写成「up *= actBuf」是【错的】（实测 gate_on.png）：
                //      up=0 时定点 z_k=(z_k+al*zd)/(1+al) 仍向 zd 塌陷 ⟹ 地形【数学上完全平】
                //      ⟹ D8 流向退化 ⟹ 河网变成【人工放射状扇形】、湖从 3600 掉到 158。
                //   ★ 正确做法：非构造区【完全不演化】= 保持底场高度 t.U[k]。
                //     用【按活动度插值】实现，处处连续（不产生掩码边界的不连续）：
                //        activity=1 ⟹ 完整 LEM 演化（山地）
                //        activity=0 ⟹ z 保持底场（平原 ✓ 底场自身有 17~92 m 的微起伏，D8 正常）
                // ★★★★★ P1（2026-10-13）：口径 2 = 只门控【抬升】——
                //   up 乘 activity，而侵蚀（al 项）与沉积全境生效。
                //   ★ 只在 sedOn 时走这条：SED_G = 0 且口径 2 会逐位等于 §340 的失败写法
                //     （up=0 时定点塌向 zd ⟹ 地形数学上全平 ⟹ D8 退化 ⟹ 放射状河网）。
                // ★★★★★ 2026-10-14：门控口径与侵蚀阈值（解耦后各自独立）。
                //   upEff = 真正进入更新的抬升：解除门控时 up 乘 activity，否则 up 原样
                //   （整体门控在下面用【按活动度插值】统一施加）。
                final double upEff = (ungateSurface && actBuf != null) ? up * actBuf[k] : up;
                double zNew;
                if (thetaOn) {
                    // ★ T&K 2020 Eq.1：分子加阈值常数 c；驱动量不超过 c 的格子进入【零侵蚀区】
                    zNew = (t.z[k] + upEff + cThr + al * zd) / (1.0 + al);
                    if (al * (t.z[k] - zd + upEff) <= cThr) zNew = t.z[k] + upEff;
                } else {
                    zNew = (t.z[k] + upEff + al * zd) / (1.0 + al);
                }
                if (!ungateSurface && actBuf != null) zNew = t.z[k] + actBuf[k] * (zNew - t.z[k]);
                if (eBuf != null) {
                    final double e = t.z[k] - zNew;      // >0 = 净侵蚀
                    eBuf[k] = e > 0.0 ? e : 0.0;         // ★ 只取正部：洼地自填不计入产沙
                }
                t.z[k] = zNew;
            }
            // ★★★★★ P1：沉积项 D = G * Qs / A（Davy & Lague 2009；见 SED_G 的 javadoc）
            //   ① 累积：沿 z 反序（= 上游→下游）一次 O(N)。Qs[k] = Sum_{上游含自身} max(0, E_j)
            //   ② 沉积：z += G * Qs / acc            （口径 0 时再乘 activity）
            //   ⚠ 用【本步】的 E，不是滞后一步（累积就在本步的更新之后马上做）。
            if (eBuf != null) {
                Arrays.fill(qsBuf, 0.0);
                for (int oi = N - 1; oi >= 0; oi--) {
                    final int k = ord[oi];
                    final double q = qsBuf[k] + eBuf[k];
                    qsBuf[k] = q;
                    final int d = t.dir[k];
                    if (d < 0 || d >= 8) continue;
                    final int ni = k % W + DX[d], nj = k / W + DZ[d];
                    if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                    qsBuf[nj * W + ni] += q;
                }
                for (int oi = 0; oi < N; oi++) {
                    final int k = ord[oi];
                    if (t.sea[k]) continue;
                    final double a = t.acc[k] > 1.0 ? t.acc[k] : 1.0;
                    double dep = sedG * qsBuf[k] / a;
                    if (sedMode == 0 && actBuf != null) dep *= actBuf[k];
                    t.z[k] += dep;
                }
            }
        }
        fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        if (LAKES_ENABLED) {
            carveLakes(ws, t, ox, oz);
            fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        }
        precipField(ws, t, ox, oz);   // ★ 有效汇流面积 accW（只读，不改地形；用 baseHeight 不递归）
        // ★★★★★ 2026-10-11（待裁决/304，V3b）：按水量收支解每个盆地的水位，覆盖 t.filled。
        //   ★★ 必须放在 precipField 【之后】：precipField 会把本瓦片用到的【气候瓦片全部建好】，
        //      而湖解每个盆地要查一次 P 与一次 E。放在它之前 ⟹ 那几十块气候瓦片会在这里【冷建】
        //      ⟹ 实测（待裁决/304 §11.4）solveLake = 6769 ms，其中 6760 ms 全花在气候查询上。
        //   ★ 也必须在 fill() 之后（盆地是按 filled - z 认的，而 filled 要先由新 z 算出来）。
        //   ★ precipField 与 meanderField 都【只读】filled ⟹ 夹在两者之间是安全的。
        ph(PH_ITER);
        if (LAKES_ENABLED && LAKE_SOLVE_ENABLED) {
            final long ls0 = System.nanoTime();
            solveLakeLevels(ws, t, ox, oz);
            final long ls1 = System.nanoTime();
            dirs(t); accOf(t, ord, tmp, cnt);
            LAKE_SOLVE_NANOS += ls1 - ls0;
            LAKE_AFTER_NANOS += System.nanoTime() - ls1;
        }
        meanderField(ws, t);          // ★ 蜿蜒场（只读 acc/dir/sea/filled，不改变任何地形）
        tilesSolved++;
        solveNanos += (System.nanoTime() - t0);
        return t;
    }

    /**
     * ★★ 有效汇流面积：accW = (P / P_REF_MM) x acc —— 水力几何 W = a Q^b, Q ∝ P x A。
     *
     * <h3>为什么这么接</h3>
     * 河宽的真实驱动量是【流量 Q】而不是【面积 A】。Q ∝ P x A，所以把 P/P_REF 折进 acc 后，
     * 所有消费者（channelDistance / meanderField / riverWidthBlocks / 河深）自动同口径。
     *
     * <h3>性能</h3>
     * LEM 格 1 km，气候格 10 km ⟹ 每 10x10 个 LEM 格只查一次降水。
     * 768 km 瓦片 = 77x77 = 5929 次气候查询（约 1 秒）。逐格查要 59 万次（约 94 秒）⟹ 不可行。
     *
     * <h3>回退</h3>
     * 取样失败（探针无气候链 / 未初始化）⟹ 用 P_FALLBACK_MM ⟹ accW = acc ⟹ 逐位回到现状。
     */
    private static void precipField(long ws, Tile t, double ox, double oz) {
        final int W = t.W, N = W * W;
        if (!PRECIP_ENABLED) { t.accW = t.acc; return; }
        if (PRECIP_ANALYTIC) { precipAnalytic(ws, t, ox, oz); return; }
        final int CG = Math.max(1, (int) Math.round(
                SimClimate.CELL / STEP_BLK));
        final int ncg = (W + CG - 1) / CG + 1;
        final double[] pg = new double[ncg * ncg];
        for (int cj = 0; cj < ncg; cj++) {
            for (int ci = 0; ci < ncg; ci++) {
                final int wx = (int) (ox + (ci * CG + CG * 0.5) * STEP_BLK);
                final int wz = (int) (oz + (cj * CG + CG * 0.5) * STEP_BLK);
                double pv;
                try { pv = SimClimate
                        .annualPrecipMmPerYear(wx, wz, (int) ws); }
                catch (Throwable ex) { pv = P_FALLBACK_MM; precipFails++; }
                if (!(pv > 0.0) || Double.isNaN(pv)) { pv = P_FALLBACK_MM; precipFails++; }
                pg[cj * ncg + ci] = pv;
                precipSamples++;
            }
        }
        final double inv = 1.0 / Math.max(1.0, P_REF_MM);
        final double[] aw = new double[N];
        for (int j = 0; j < W; j++) {
            final int cj = j / CG;
            for (int i = 0; i < W; i++) {
                final int k = j * W + i;
                final double pv = pg[cj * ncg + (i / CG)];
                aw[k] = t.acc[k] * (pv * inv);
            }
        }
        t.accW = aw;
    }

    /**
     * ★★ 解析降水代理：pRel = 水汽耗尽 x 迎风坡增雨。
     *
     * <pre>
     *   ① 大尺度水汽可用量  moist = MOIST_FLOOR + (1-MOIST_FLOOR) x depletion(hUp, kappa)
     *        depletion(h, k) = exp(-max(0,h)/H_MOIST x k)      <- PrecipField 的雨影唯一来源
     *        hUp = 上风 100 km 处的地形高度（空气在来路上被山抽干）
     *   ② 小尺度迎风坡增雨  texp = exp(TERRAIN_GAIN x clamp(w/W_REF, -1.5, 1.5))
     *        w = u(lat) x dh/dx          <- Smith & Barstad 的抬升项（只用纬向风）
     *        u(lat): |lat|<30 东风 -6，30~60 西风 +8，>60 极地东风 -4（m/s）
     *   pRel = moist x texp
     * </pre>
     *
     * <p>成本：3 次 elevationWithCell + 1 次 kappaMemo 约 2 us/格 ⟹ 59 万格约 1.2 秒。
     */
    /**
     * ★★★ 重入保护：solve() 期间禁止再查 LEM。
     *
     * <p><b>为什么必须有（实测踩过的坑）</b>：溶解析降水代理最初用
     * {@code PlateField.elevationWithCell} 取高程 —— 而它现在是
     * {@code NoiseContinentGrid.height -> height -> TalosLandErosion.height}
     * 的转发，<b>包含 LEM 本身</b>。于是：
     * <pre>
     *   solve() -> precipField() -> elevationWithCell() -> ... -> TalosLandErosion.height()
     *           -> tileAt()（缓存里没有，因为正在解）-> solve() -> ...  ⟹ 无限递归
     * </pre>
     * 实测表现为「第一次调用跑了 3.5 分钟仍未返回」。
     *
     * <p>修法有两层：① 高程改用 {@link #baseHeight}（基础剖面，不含 LEM，零递归、零成本）；
     * ② 本标志作为兜底，一旦重入就跳过降水采样。
     */
    private static final ThreadLocal<Boolean> IN_SOLVE = new ThreadLocal<Boolean>() {
        protected Boolean initialValue() { return Boolean.FALSE; } };

    private static void precipAnalytic(long ws, Tile t, double ox, double oz) {
        final int W = t.W, N = W * W;
        // ★ 注意：这里【不能】用 IN_SOLVE 做早退 —— 因为 solve() 会在调用本函数【之前】
        //   就把它设成 TRUE，那样每次都会被自己挡掉（实测症状：precipFails 每瓦片 +1、
        //   pRel 恒为初值）。既然高程已改用 baseHeight（不含 LEM，不递归），本函数无需保护。
        final double upDist = 100_000.0;
        final double[] aw = new double[N];
        double mn = 1e18, mx = -1e18, sum = 0.0;
        int cnt = 0;
        for (int j = 0; j < W; j++) {
            final double wz = oz + (j + 0.5) * STEP_BLK;
            final int zi = (int) Math.floor(wz);
            // 纬向风（简化）：信风东风 / 西风带西风 / 极地东风
            final double latDeg = Math.toDegrees(
                    WorldContract.latOf(zi));
            final double alat = Math.abs(latDeg);
            final double uz = (alat < 30.0) ? -6.0 : (alat < 60.0 ? 8.0 : -4.0);
            for (int i = 0; i < W; i++) {
                final int k = j * W + i;
                final double wx = ox + (i + 0.5) * STEP_BLK;
                final int xi = (int) Math.floor(wx);
                // ① 迎风坡：u x dh/dx
                // ★ 用 baseHeight（基础剖面，不含 LEM）—— 零递归、零成本。
                //   语义上也更对：降水由【山脉】决定，而 LEM 的河谷是结果不是原因。
                //
                // ★★ 迎风坡增雨 = exp(GAIN x 上风累积爬升 / H_REF)。
                //    用【累积爬升】而不是局部坡度 —— 后者会让 25% 的陆地撞上 clamp（实测）。
                // ★★★ hUp = 【上风路径上已经爬过的最高点】，不是上风 100 km 处的单点高程。
                //   实测踩过的坑：单点高程的中位数是 -3599 m（那是深海）—— 因为我们的陆地
                //   只占 43%，上风 100 km 处多半是海，于是 h - hUp 恒为 +4000 左右（常数，无用）。
                //   物理上雨影来自「空气在来路上已经被山抽干」= 路径最高点，不是路径终点。
                double hLocal = 0.0, hUpTmp = 0.0;
                try {
                    hLocal = baseHeight(ws, wx, wz);
                    final double sgn = (uz > 0) ? 1.0 : -1.0;
                    for (int kk = 1; kk <= UPWIND_SAMPLES; kk++) {
                        final double hk = baseHeight(ws,
                                wx - upDist * (kk / (double) UPWIND_SAMPLES) * sgn, wz);
                        if (hk > hUpTmp) hUpTmp = hk;
                    }
                } catch (Throwable ex) { precipFails++; }
                double climb = (hLocal - hUpTmp) / TERRAIN_H_REF;
                final double cc = TERRAIN_CLIMB_CLAMP;
                if (climb > cc) climb = cc; else if (climb < -cc) climb = -cc;
                final double texp = Math.exp(TERRAIN_GAIN * climb);
                // ② 水汽耗尽：上风 100 km 处的地形高度 + 大陆度
                double hUp = hUpTmp, kappa = 0.0;
                try {
                    // ★ 用常数 KAPPA_MEAN，不用 kappaMemo —— 后者内部也走 PlateField，
                    //   在 solve() 期间调会递归。物理上 depletion = exp(-hUp/H_MOIST x kappa)，
                    //   主导项是 hUp（上风地形），kappa 只是调制。
                    kappa = Atmosphere.KAPPA_MEAN;
                } catch (Throwable ex) { precipFails++; }
                double depl = 0.0;
                try { depl = PrecipField.depletion(hUp, kappa); }
                catch (Throwable ex) { depl = 1.0; }
                final double moist = MOIST_FLOOR + (1.0 - MOIST_FLOOR) * depl;
                // ★★★ 只对【陆地】应用降水修正，并除以 P_REF_MM。
                //   实测踩过的坑：
                //     ① 最初漏了 (P_REF_BASE/P_REF_MM) —— P_REF_MM 根本没被用，河宽全线崩到 1.0
                //     ② 对海洋也乘了 pRel —— 海洋 pRel 很小，把【陆地】的均值从 0.844 拉到 0.432
                //   P_REF_BASE = 1000 是代理公式的隐含参考（探针在只统计陆地时测出 mean(pRel)=0.844）。
                // ★ 纬向降水带（零成本）：赤道 ITCZ 湿、副热带干、中纬风暴带次湿。
                //   它【不与河道位置反相关】—— 这是它能纠正地形代理系统性偏差的原因。
                final double latR = WorldContract.latOf(zi);
                final double fLat = 0.6 * Math.cos(2.0 * latR) + 0.4 * Math.cos(4.0 * latR);
                final double pLat = Math.exp(LAT_GAIN * fLat);
                final double pRel = (hLocal > 0.0)
                        ? moist * texp * pLat * (P_REF_BASE_MM / Math.max(1.0, P_REF_MM))
                        : 1.0;
                aw[k] = pRel;      // ★ 先存 pRel 本身；accW 由 meanderField 按流向累加
                if (pRel < mn) mn = pRel;
                if (pRel > mx) mx = pRel;
                sum += pRel; cnt++;
                precipSamples++;
            }
        }
        // ★★★ 区域平均：pRel 在【河道格】上系统性偏低（河道在低地 = 相对上风山脊是背风），
        //   而河宽应该由【流域平均降水】驱动。做一次箱平均即可抹掉这个偏差。
        final int SB = Math.max(1, PRECIP_SMOOTH);
        if (SB > 1) {
            final int nb = (W + SB - 1) / SB;
            final double[] avg = new double[nb * nb];
            final int[] bc = new int[nb * nb];
            for (int j = 0; j < W; j++) {
                final int bj = j / SB;
                for (int i = 0; i < W; i++) {
                    final int k = j * W + i;
                    final int bi = bj * nb + (i / SB);
                    avg[bi] += aw[k];                  // aw 现在存的就是 pRel
                    bc[bi]++;
                }
            }
            for (int b = 0; b < nb * nb; b++) if (bc[b] > 0) avg[b] /= bc[b]; else avg[b] = 1.0;
            // 邻域 3x3 再平滑一次，去掉块状边界
            final double[] sm = new double[nb * nb];
            for (int bj = 0; bj < nb; bj++) for (int bi = 0; bi < nb; bi++) {
                double s = 0; int c = 0;
                for (int dj = -1; dj <= 1; dj++) for (int di = -1; di <= 1; di++) {
                    final int x2 = bi + di, y2 = bj + dj;
                    if (x2 < 0 || x2 >= nb || y2 < 0 || y2 >= nb) continue;
                    s += avg[y2 * nb + x2]; c++; }
                sm[bj * nb + bi] = c > 0 ? s / c : 1.0;
            }
            for (int j = 0; j < W; j++) {
                final int bj = j / SB;
                for (int i = 0; i < W; i++) aw[j * W + i] = sm[bj * nb + (i / SB)];
            }
        }
        t.pRelTmp = aw;         // ★ accW 由 meanderField 按流向累加（保证单调不减）
        precipTiles++;
        // 注：pRelTmp 在 meanderField 用完即弃（不主动置 null，避免与并发读竞争）
        precipLastMin = mn; precipLastMax = mx;
        precipLastMean = cnt > 0 ? sum / cnt : 0.0;
        if (PRECIP_DIAG && (precipTiles & 63L) == 0L) {
            System.out.println("[LEM/降水] 瓦片 " + precipTiles + "  pRel min=" + String.format("%.3f", mn)
                + " mean=" + String.format("%.3f", precipLastMean) + " max=" + String.format("%.3f", mx));
        }
    }

    /** ★ 河宽用的汇流面积（含降水）。accW 未建时回落到真实 acc。 */
    static double accWOf(Tile t, int k) {
        final double[] aw = t.accW;
        final int[] co = t.chOrd;
        if (aw == null || co == null) return t.acc[k];
        final int o = co[k];
        // ★ accW[o] = 该流域到本格为止的 Sum pRel（已含本格）⟹ 直接返回，不要再加 acc。
        return (o >= 0 && o < aw.length) ? aw[o] : t.acc[k];
    }

    /** ★ 确定性的单格随机相位（用【全局格坐标】，保证跨瓦片一致）。 */
    private static double headPhase(long ws, Tile t, int k) {
        int gx = t.tx * OUT_CELLS + (k % t.W) - BUFFER_CELLS;
        int gz = t.tz * OUT_CELLS + (k / t.W) - BUFFER_CELLS;
        long h = ws * 0x9E3779B97F4A7C15L
               + gx * 0xC2B2AE3D27D4EB4FL
               + gz * 0x165667B19E3779F9L;
        h ^= (h >>> 30); h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27); h *= 0x94D049BB133111EBL; h ^= (h >>> 31);
        return (h >>> 11) * 0x1.0p-53 * 2.0 * Math.PI;
    }

    /**
     * ★★★ 蜿蜒场：给每个河道格算【弧长 s、累积相位 Phi、幅度 A、调制相位】。
     *
     * <pre>
     *   lambda = clamp(MEANDER_LAM_MIN, inf, MEANDER_LAMK * W)
     *   Phi    = 上游 Phi + 2 pi * 段长 / lambda(上游)      <- 相位【积分】
     *   A      = MEANDER_AMP * W * (1 + MOD * sin(Mod))
     *   Mod    = 上游 Mod + 2 pi * 段长 / (MODW * lambda)   <- 调制相位也【积分】
     *   off(t) = A(t) * sin(Phi(t))                          <- 段内线性插值
     * </pre>
     *
     * <p>★ 传播顺序 = <b>acc 升序</b>（上游到下游）。用 4096 桶的计数排序，O(N)。
     *
     * <p>★ <b>只读</b>：acc / dir / sea / filled / z 都不改动 ⟹ 地形逐位不变。
     */
    private static void meanderField(long ws, Tile t) {
        final int W = t.W, N = W * W;
        // ★★★ 2026-10-11 解耦（原为 `if (!MEANDER_ENABLED) { t.chOrd = null; ... return; }`）
        //   为什么改：`chOrd`【不只是】蜿蜒用的 —— 它同时是
        //     · `accWOf`（河宽）的索引
        //     · `mWet`（干河道）的索引 —— `wetness01` 用 `chOrd[...]` 取湿润度
        //     · `channelDistance` 判定「k 是否河道格」的依据
        //   ⟹ 旧写法让 `MEANDER_ENABLED = false` 变成【水系整体失效】（实测 NPE），
        //     而那个开关的语义只是「off 恒为 0 ⟹ 逐位回到直线中心线」（见 :259）。
        //   现在：`chOrd`/`accW`/`mWet` 始终建立；只有蜿蜒量（mS/mPhi/mA/mMod）在
        //   开关关闭时不被消费（`meanderOff` 自己 return 0）⟹ 语义正确。
        final int[] ch = new int[N];
        Arrays.fill(ch, -1);
        int n = 0; double maxAcc = 1.0;
        for (int k = 0; k < N; k++) {
            if (t.sea[k]) continue;
            if (!(t.acc[k] >= ACC_MIN)) continue;
            if (LAKES_ENABLED && t.filled[k] - t.z[k] > LAKE_MIN_FILL) continue;
            ch[k] = 0; n++;
            if (t.acc[k] > maxAcc) maxAcc = t.acc[k];
        }
        t.chOrd = ch; t.nCh = n;
        if (n == 0) { t.mS = new double[0]; t.mPhi = new double[0]; t.mA = new double[0];
                      t.mW = new double[0]; t.mMod = new double[0]; return; }
        final int NB = 4096;
        int[] head = new int[NB + 1];
        for (int k = 0; k < N; k++) {
            if (ch[k] != 0) continue;
            int b = (int) (t.acc[k] / maxAcc * (NB - 1));
            if (b < 0) b = 0; if (b >= NB) b = NB - 1;
            head[b + 1]++;
        }
        for (int b = 0; b < NB; b++) head[b + 1] += head[b];
        int[] pos = head.clone();
        int[] bucket = new int[n];
        for (int k = 0; k < N; k++) {
            if (ch[k] != 0) continue;
            int b = (int) (t.acc[k] / maxAcc * (NB - 1));
            if (b < 0) b = 0; if (b >= NB) b = NB - 1;
            bucket[pos[b]++] = k;
        }
        for (int i = 0; i < n; i++) ch[bucket[i]] = i;
        t.mS = new double[n]; t.mPhi = new double[n]; t.mA = new double[n];
        t.mW = new double[n]; t.mMod = new double[n];
        t.mVX = new double[n]; t.mVZ = new double[n];
        // ★ accW / mWet 都按【河道序号 o】索引（长度 n）；非河道格在访问器里回落到真实 acc。
        t.accW = new double[n];
        t.mWet = new double[n];
        // ---- 顶点初值 = 格中心；并建上下游链（供 Taubin 用）----
        final double ox = t.tx * (double) OUT_CELLS * STEP_BLK - BUFFER_CELLS * STEP_BLK;
        final double oz = t.tz * (double) OUT_CELLS * STEP_BLK - BUFFER_CELLS * STEP_BLK;
        final int[] dnOf = new int[n];
        final int[] upOf = new int[n];
        Arrays.fill(dnOf, -1); Arrays.fill(upOf, -1);
        for (int i = 0; i < n; i++) {
            final int k = bucket[i];
            t.mVX[i] = ox + (k % W + 0.5) * STEP_BLK;
            t.mVZ[i] = oz + (k / W + 0.5) * STEP_BLK;
        }
        for (int i = 0; i < n; i++) {
            final int k = bucket[i];
            final int d = t.dir[k]; if (d < 0) continue;
            final int ni = k % W + DX[d], nj = k / W + DZ[d];
            if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
            final int ob = ch[nj * W + ni];
            if (ob < 0) continue;
            dnOf[i] = ob;
            // upOf 取 acc 最大的上游（bucket 已按 acc 升序 ⟹ 后写覆盖前写即为最大）
            upOf[ob] = i;
        }
        // ---- Taubin 平滑（λ 步 + μ 步），保持体积、去除阶梯 ----
        if (SMOOTH_ROUNDS > 0) {
            final double[] tx = new double[n], tz = new double[n];
            for (int rd = 0; rd < SMOOTH_ROUNDS; rd++) {
                for (int pass = 0; pass < 2; pass++) {
                    final double coef = (pass == 0) ? SMOOTH_LAMBDA : SMOOTH_MU;
                    for (int i = 0; i < n; i++) tx[i] = t.mVX[i];
                    for (int i = 0; i < n; i++) tz[i] = t.mVZ[i];
                    for (int i = 0; i < n; i++) {
                        final int u = upOf[i], v = dnOf[i];
                        if (u < 0 || v < 0) continue;          // 端点/汇流口不动
                        final double lx = (tx[u] + tx[v]) * 0.5 - tx[i];
                        final double lz = (tz[u] + tz[v]) * 0.5 - tz[i];
                        t.mVX[i] = tx[i] + coef * lx;
                        t.mVZ[i] = tz[i] + coef * lz;
                    }
                }
            }
        }
        for (int i = 0; i < n; i++) {
            final int k = bucket[i];
            // ★★★ 顺序很重要（实测踩过的坑）：必须【先】找上游并累加 accW，【再】算宽度。
            //   最初把累加写在 wk 之后 ⟹ widthOf 读到还是 0 的 accW ⟹ 全瓦片宽度为 0。
            //
            //   上游 = dir 指向 k 的河道格中 acc 最大者
            int up = -1; double ua = -1;
            final int ki = k % W, kj = k / W;
            for (int q = 0; q < 8; q++) {
                int mi = ki + DX[q], mj = kj + DZ[q];
                if (mi < 0 || mi >= W || mj < 0 || mj >= W) continue;
                int mk = mj * W + mi;
                if (ch[mk] < 0) continue;
                int d = t.dir[mk]; if (d < 0) continue;
                if (mk % W + DX[d] != ki || mk / W + DZ[d] != kj) continue;
                if (t.acc[mk] > ua) { ua = t.acc[mk]; up = mk; }
            }
            // ★★★ 宽度：accW = pRel x acc —— 【乘法】形式。
            //
            //   为什么不沿流向累加（实测踩过的坑）：
            //     河道格在 1 km 网格上不一定【相邻】（上游可能在 2 格外），
            //     而 meanderField 找上游只看 8 邻域 ⟹ 累加链经常断裂
            //     ⟹ accW 系统性偏小（中位 2.78，而 acc 中位约 5~20）⟹ 宽度基线从 3.32 崩到 1.73。
            //   乘法形式不需要链：pRel 是【本格】的量，pRel=1 时 accW = acc 精确成立 ✓
            //   ★ 它不保证沿流向单调 —— 但那没关系，因为【宽度不需要单调】。
            //     单调性是【干河道】的需求，由下面独立的 wetness 传播保证。
            final double pr = (t.pRelTmp != null) ? t.pRelTmp[k] : 1.0;
            t.accW[ch[k]] = t.acc[k] * pr;
            // ★★★ 干河道：wetness 沿流向取 max(本格, 上游) ⟹ 保证【上游有水 => 下游有水】。
            double wet = wetnessLocal(pr);
            if (up >= 0) { final double wu = t.mWet[ch[up]]; if (wu > wet) wet = wu; }
            t.mWet[ch[k]] = wet;
            // ---- 现在 accW 已就位，再算宽度 ----
            final double wk = widthOf(accWOf(t, k));
            double lam = MEANDER_LAMK * wk;
            if (lam < MEANDER_LAM_MIN) lam = MEANDER_LAM_MIN;
            double s, phi, mod;
            if (up < 0) { s = 0.0; phi = headPhase(ws, t, k); mod = headPhase(ws, t, k) * 1.7; }
            else {
                final int ou = ch[up];
                // ★ 段长用【平滑后的顶点】—— 与 channelDistance 完全同口径
                final double ddx = t.mVX[ch[k]] - t.mVX[ou];
                final double ddz = t.mVZ[ch[k]] - t.mVZ[ou];
                final double dl = Math.sqrt(ddx * ddx + ddz * ddz);
                double lamUp = MEANDER_LAMK * widthOf(accWOf(t, up));
                if (lamUp < MEANDER_LAM_MIN) lamUp = MEANDER_LAM_MIN;
                s   = t.mS[ou]   + dl;
                phi = t.mPhi[ou] + 2.0 * Math.PI * dl / lamUp;
                mod = t.mMod[ou] + 2.0 * Math.PI * dl / (MEANDER_MODW * lamUp);
            }
            double A = MEANDER_AMP * wk;
            if (MEANDER_MOD != 0.0) A *= (1.0 + MEANDER_MOD * Math.sin(mod));
            final int ok = ch[k];
            t.mS[ok] = s; t.mPhi[ok] = phi; t.mA[ok] = A; t.mW[ok] = wk; t.mMod[ok] = mod;
            if (i == n - 1) t.pRelTmp = null;   // 最后一格 ⟹ 用完即弃（省内存）
        }
    }

    /**
     * ★ 某个河道格沿其下游段的横向偏移（格）。tt = 段内参数 [0,1]。
     * 段两端的 A 与 Phi 都已知 ⟹ 线性插值 ⟹ 只需【1 次 sin】。
     */
    static double meanderOff(Tile t, int k, double tt) {
        if (!MEANDER_ENABLED || t.chOrd == null) return 0.0;
        final int oa = t.chOrd[k];
        if (oa < 0) return 0.0;
        double A = t.mA[oa], phi = t.mPhi[oa];
        final int d = t.dir[k];
        if (d >= 0) {
            final int W = t.W;
            final int ni = k % W + DX[d], nj = k / W + DZ[d];
            if (ni >= 0 && ni < W && nj >= 0 && nj < W) {
                final int ob = t.chOrd[nj * W + ni];
                if (ob >= 0) {
                    A   += (t.mA[ob]   - A)   * tt;
                    phi += (t.mPhi[ob] - phi) * tt;
                }
            }
        }
        return A * Math.sin(phi);
    }

    /**
     * 湖泊层（★ N1b，2026-10-11 重写）：沿【裂谷场】叠加连续剖面。
     *
     * <p><b>改前</b>：按 {@code BOWL_SPACING = 24} 格的**规则网格**撒圆盘 + 环形 rim
     * ⟹ 用户不接受的两件事：24 km 网格的规律性、圆环形土墙。
     *
     * <p><b>改后</b>：在**每一格**上取 {@code r = plateBoundary01 x (1 - convergence01)}，
     * 叠加 {@code (1 - RIFT_SPLIT) x A x (k*4r(1-r) - r)} ⟹ **位置与宽度全由场决定**。
     * ★ {@code BOWL_SPACING} / {@code BOWL_R} / {@code BOWL_MIN_RIFT} 在本路径上【不再使用】。
     *
     * <p>★ <b>为什么这一份不能并进 N1a</b>：LEM 的不动点 {@code z_k - zd = up/al >= 0}
     * ⟹ 平衡态沿流向单调不增 ⟹ 进抬升场的那一份会被【解掉】（待裁决/302 §一）。
     * 这一份是在 LEM **之后**叠加的，LEM 看不见它 ⟹ 它才能留下一个闭合盆地。
     *
     * <p>⚠ 成本实测（待裁决/303）：场调用 0.21 us/格 ⟹ **0.12 s/瓦片**，
     * 对照瓦片求解 6~8 s ≈ **1.6%**。
     */
    private static void carveLakes(long ws, Tile t, double ox, double oz) {
        if (!RIFT_ENABLED) return;
        final double share = 1.0 - RIFT_SPLIT;
        if (share == 0.0) return;
        // ★★★★★ 2026-10-12 修 BUG：这里【曾经写成 BOWL_DEPTH / ELEV_TO_BLK】——
        //   那是【格 -> 米】的换算，而 t.z 的单位是【格】⟹ 叠加被放大了 1/ELEV_TO_BLK = 55.6 倍！
        //   实测后果（待裁决/311）：carveLakes 阶段平均 |dz| = 1277 m、max = 482 格（26,797 m），
        //   而它本来只该叠加 <= 16 格 ⟹ 表现为「大陆被切开」「地形被削平 6286 格」。
        //   ★ 根因：baseHeight 的单位是【米】（那里用 /ELEV_TO_BLK 是对的），carveLakes 改的是
        //     【格】的 t.z，我照抄了同一个表达式却没核对目标量纲。
        final double A = BOWL_DEPTH;                    // 幅值：格（与 t.z 同量纲）
        if (A <= 0.0) return;
        final int W = t.W;
        for (int j = 0; j < W; j++) {
            final double wz = oz + j * STEP_BLK;
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                if (t.sea[k]) continue;
                double dz = share * A * riftProfile(ws, ox + i * STEP_BLK, wz, RIFT_SHOULDER);
                // ★ 修法 D（待裁决/310）：N1b 也做海岸淡化（理由同 baseHeight：不得劈开大陆）
                if (dz < 0.0) {
                    double hM = baseHeightRaw(ws, ox + i * STEP_BLK, wz);
                    if (hM > 0.0) {
                        double ta = OROG_COAST_TAPER_M > 0.0
                                ? smoothstep(0.0, OROG_COAST_TAPER_M, hM) : 1.0;
                        dz *= ta;
                    } else dz = 0.0;
                }
                if (dz != 0.0) {
                    double nz = t.z[k] + dz;
                    // ★★★★★ 修法 B（待裁决/309）：裂谷【不得把陆地压到海平面以下】。
                    //   为什么必须钳：sea[] 是在瓦片 init 时按 baseHeight 定死的，而 N1b 只改 z 不改 sea
                    //   ⟹ 会出现「非海，但低于海平面」的格 ⟹ fill() 从【海格种子】把它们填到 SEA_BLK
                    //   ⟹ 伪湖（实测占湖格 35%，且 100% 挤在离岸 20 km 内 —— 见 待裁决/306）。
                    //   ★ 钳在 SEA_BLK 之后：盆地若【高于海平面】，fill() 给出的 filled 就是【溢出口高度】
                    //     ⟹ 湖出现在任何海拔、水位由溢出口定 —— 这正是「湖不必低于海平面」。
                    //   本钳只在沿海低地生效（裂谷幅值 share*A = 444 m，内陆高地根本碰不到）。
                    if (nz < SEA_BLK) nz = SEA_BLK;
                    t.z[k] = nz;
                }
            }
        }
    }

    /**
     * ★★★★★ 2026-10-11（待裁决/304，V3b）：**按水量收支解每个盆地的水位**，覆盖 {@code t.filled}。
     *
     * <pre>
     *   一个盆地： A_lake(L) x E  =  P x A_catch
     *     · 若 A_lake(溢出口) x E &lt;= P x A_catch  =&gt; 溢流湖：水位不动（= 溢出口）
     *     · 否则解出 L &lt; 溢出口             =&gt; 终端湖：把 filled 压到 L
     *     · 若连最深处都装不下               =&gt; 干涸：filled = z（自动不再是湖）
     * </pre>
     *
     * <p>★ 为什么覆盖 {@code filled} 就够了：{@code riverSurfaceBlocks = round(filled)}、
     * {@code isLake = filled - z > 阈值}、{@code ChunkProviderTalos2} 全部读 {@code filled}
     * ⟹ 一处改、全线生效。
     *
     * <p>⚠ 本遍【不做级联】（各自独立）—— 级联的意思是「减掉上游湖的蒸发损失」，
     * 实测影响 &lt; 2%（见 待裁决/304 §十）。
     *
     * <p>★ 成本：每盆地一次 {@code annualPrecipMmPerYear} + 一次 {@code lakeEvapMmPerYear}，
     * 两者都是**瓦片内双线性插值**（E 在建气候瓦片时已顺手算好）⟹ 边际成本≈0。
     */
    /** Async climate prefetch: off -> bit-identical behaviour. */
    public static volatile boolean PREFETCH_ON = true;
    /** Phase timers (diagnostics only): init / gate / ITER / post. */
    public static final java.util.concurrent.atomic.AtomicLong PH_INIT =
            new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong PH_GATE =
            new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong PH_ITER =
            new java.util.concurrent.atomic.AtomicLong();
    private static long phT = 0L;
    private static void ph(java.util.concurrent.atomic.AtomicLong acc) {
        long now = System.nanoTime();
        if (phT != 0L) acc.addAndGet(now - phT);
        phT = now;
    }

    private static java.util.concurrent.ExecutorService PREFETCH;

    private static synchronized java.util.concurrent.ExecutorService prefetchExec() {
        if (PREFETCH == null) {
            PREFETCH = java.util.concurrent.Executors.newSingleThreadExecutor(new java.util.concurrent.ThreadFactory() {
                @Override public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Talos-LEM-ClimPrefetch");
                    t.setDaemon(true);
                    return t;
                }
            });
        }
        return PREFETCH;
    }

    /**
     * Warm the climate tiles that {@link #solveLakeLevels} is going to ask for.
     *
     * <p>Each lake basin used to call {@code SimClimate} synchronously; a 512 km tile touches
     * ~67 climate tiles (100 km x 50 km) at ~0.37 s each = ~25 s. That work is independent of
     * the erosion iterations, so it is submitted here and overlaps the ITER loop (~26 s).
     *
     * <p>The lattice MUST match solveLakeLevels exactly (same CG / ncg / lo / +CG/2 offsets),
     * otherwise the cache misses and the whole point is lost.
     */
    private static void prefetchClimate(final long ws, int tx, int tz, final double ox, final double oz) {
        if (!PREFETCH_ON || !LAKE_SOLVE_ENABLED || !LAKES_ENABLED) return;
        final int CG = Math.max(1, LAKE_CLIM_STEP_CELLS);
        final int NS = OUT_CELLS;
        final int ncg = (NS + CG - 1) / CG + 2;
        final int lo = BUFFER_CELLS;
        final int seed = (int) ws;
        final double step = STEP_BLK;
        try {
            prefetchExec().submit(new Runnable() {
                @Override public void run() {
                    for (int cj = 0; cj < ncg; cj++) {
                        for (int ci = 0; ci < ncg; ci++) {
                            final int gi = lo + Math.min(NS - 1, ci * CG + CG / 2);
                            final int gj = lo + Math.min(NS - 1, cj * CG + CG / 2);
                            final double wx = ox + gi * step, wz = oz + gj * step;
                            try { SimClimate.annualPrecipMmPerYear((int) wx, (int) wz, seed); } catch (Throwable ex) { }
                            try { SimClimate.lakeEvapMmPerYear((int) wx, (int) wz, seed); } catch (Throwable ex) { }
                        }
                    }
                }
            });
        } catch (Throwable ex) { /* prefetch is best-effort */ }
    }

    private static void solveLakeLevels(long ws, Tile t, double ox, double oz) {
        if (!LAKE_SOLVE_ENABLED) return;
        final int W = t.W;
        final int lo = BUFFER_CELLS, hi = lo + OUT_CELLS;
        final int NS = hi - lo;
        if (NS <= 0) return;
        final int[] st = new int[NS * NS];
        final int[] cells = new int[NS * NS];
        final double[] zs = new double[NS * NS];
        final boolean[] vis = new boolean[W * W];
        // ---- coarse climate lattice for the lake P/E inputs ----
        // Each basin used to call SimClimate.annualPrecipMmPerYear / lakeEvapMmPerYear,
        // and ONE such call runs the whole planetary chain (stationary wave solve +
        // soil-moisture spin-up + radiative skin temperature). With thousands of basins
        // per 512 km tile this cost 85 s of a 105 s solve (JFR: Radiation.residual 55%).
        // Sampling the same fields on a coarse lattice and interpolating keeps the lake
        // physics but drops the query count by ~CG^2. Same pattern as precipField().
        final int CG = Math.max(1, LAKE_CLIM_STEP_CELLS);
        final int ncg = (NS + CG - 1) / CG + 2;
        final double[] pg = new double[ncg * ncg];
        final double[] eg = new double[ncg * ncg];
        {
            final long c0 = System.nanoTime();
            for (int cj = 0; cj < ncg; cj++) {
                for (int ci = 0; ci < ncg; ci++) {
                    final int gi = lo + Math.min(NS - 1, ci * CG + CG / 2);
                    final int gj = lo + Math.min(NS - 1, cj * CG + CG / 2);
                    final double wx = ox + gi * STEP_BLK, wz = oz + gj * STEP_BLK;
                    double pv = 0.0, ev = Double.NaN;
                    try { pv = SimClimate.annualPrecipMmPerYear((int) wx, (int) wz, (int) ws); } catch (Throwable ex) { }
                    try { ev = SimClimate.lakeEvapMmPerYear((int) wx, (int) wz, (int) ws); } catch (Throwable ex) { }
                    pg[cj * ncg + ci] = pv;
                    eg[cj * ncg + ci] = ev;
                }
            }
            LAKE_CLIM_NANOS += System.nanoTime() - c0;
        }

        for (int j = lo; j < hi; j++) {
            for (int i = lo; i < hi; i++) {
                final int q0 = j * W + i;
                if (vis[q0] || t.sea[q0]) continue;
                if (!(t.filled[q0] - t.z[q0] > LAKE_MIN_FILL)) continue;
                int sp = 0; st[sp++] = q0; vis[q0] = true;
                int n = 0; double accMax = 0.0, spill = 0.0; int outlet = q0;
                while (sp > 0) {
                    final int c = st[--sp];
                    cells[n] = c; zs[n] = t.z[c]; n++;
                    if (t.filled[c] > spill) spill = t.filled[c];
                    if (t.acc[c] > accMax) { accMax = t.acc[c]; outlet = c; }
                    final int ci = c % W, cj = c / W;
                    if (ci > lo)     { final int x = c - 1; if (!vis[x] && !t.sea[x] && t.filled[x] - t.z[x] > LAKE_MIN_FILL) { vis[x] = true; st[sp++] = x; } }
                    if (ci < hi - 1) { final int x = c + 1; if (!vis[x] && !t.sea[x] && t.filled[x] - t.z[x] > LAKE_MIN_FILL) { vis[x] = true; st[sp++] = x; } }
                    if (cj > lo)     { final int x = c - W; if (!vis[x] && !t.sea[x] && t.filled[x] - t.z[x] > LAKE_MIN_FILL) { vis[x] = true; st[sp++] = x; } }
                    if (cj < hi - 1) { final int x = c + W; if (!vis[x] && !t.sea[x] && t.filled[x] - t.z[x] > LAKE_MIN_FILL) { vis[x] = true; st[sp++] = x; } }
                }
                final double aSpill = n;
                // bilinear lookup on the coarse lattice instead of a per-basin solve
                final double gx = ((outlet % W) - lo) / (double) CG;
                final double gz = ((outlet / W) - lo) / (double) CG;
                int i0 = (int) Math.floor(gx), j0 = (int) Math.floor(gz);
                double ax = gx - i0, az = gz - j0;
                if (i0 < 0) { i0 = 0; ax = 0.0; } else if (i0 > ncg - 2) { i0 = ncg - 2; ax = 1.0; }
                if (j0 < 0) { j0 = 0; az = 0.0; } else if (j0 > ncg - 2) { j0 = ncg - 2; az = 1.0; }
                final int i1 = i0 + 1, j1 = j0 + 1;
                final double P = (pg[j0 * ncg + i0] * (1 - ax) + pg[j0 * ncg + i1] * ax) * (1 - az)
                        + (pg[j1 * ncg + i0] * (1 - ax) + pg[j1 * ncg + i1] * ax) * az;
                final double E = (eg[j0 * ncg + i0] * (1 - ax) + eg[j0 * ncg + i1] * ax) * (1 - az)
                        + (eg[j1 * ncg + i0] * (1 - ax) + eg[j1 * ncg + i1] * ax) * az;
                final double inflow = P * accMax;
                double aFin;
                if (!(E > 0.0) || aSpill * E <= inflow) aFin = aSpill;
                else {
                    final double kf = inflow / E;
                    aFin = (kf >= aSpill) ? aSpill : (kf <= 1.0 ? 0.0 : Math.floor(kf)); }
                if (aFin >= aSpill) continue;                       // 溢流：水位不动
                if (aFin <= 0.0) { for (int q = 0; q < n; q++) t.filled[cells[q]] = t.z[cells[q]]; continue; }
                Arrays.sort(zs, 0, n);
                final double level = zs[(int) aFin - 1];
                for (int q = 0; q < n; q++) { final int c = cells[q];
                    if (t.z[c] <= level) t.filled[c] = level; else t.filled[c] = t.z[c]; }
            }
        }
    }

    // ==================== 填洼 / 流向 / 汇流 ====================

    private static void fill(Tile t, long[] heap) {
        int W = t.W, N = W * W;
        double[] f = t.filled;
        Arrays.fill(t.done, false);
        Arrays.fill(t.par, -1);
        int hs = 0;
        for (int k = 0; k < N; k++) {
            boolean edge = (k % W == 0) || (k % W == W - 1) || (k / W == 0) || (k / W == W - 1);
            if (t.z[k] <= SEA_BLK) {
                // ★ 海格的基准面是【海平面】，不是海底深度。
                //   若用海底深度（很低的负值），sample() 的双线性插值会把邻近【河道格】
                //   的水面污染到海平面以下（实测：297 个入海口里 144 个水面低于 64，最低 22）。
                //   这也与更新式的 zd = max(z_downstream, SEA_BLK) 一致。
                f[k] = SEA_BLK; t.done[k] = true;
                heap[hs++] = key(f[k], k);
            } else if (edge) {
                f[k] = t.z[k]; t.done[k] = true;
                heap[hs++] = key(f[k], k);
            }
        }
        for (int i = hs / 2 - 1; i >= 0; i--) siftDown(heap, i, hs);
        while (hs > 0) {
            long top = heap[0]; heap[0] = heap[--hs]; siftDown(heap, 0, hs);
            int k = (int) (top & 0xFFFFFFFFL), ki = k % W, kj = k / W;
            for (int d = 0; d < 8; d++) {
                int ni = ki + DX[d], nj = kj + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                int nb = nj * W + ni;
                if (t.done[nb]) continue;
                t.done[nb] = true;
                double v = f[k] + EPS;
                f[nb] = t.z[nb] > v ? t.z[nb] : v;
                t.par[nb] = k;
                heap[hs++] = key(f[nb], nb);
                siftUp(heap, hs - 1);
            }
        }
    }

    private static void dirs(Tile t) {
        int W = t.W;
        for (int j = 0; j < W; j++) {
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                if (t.z[k] <= SEA_BLK) { t.dir[k] = -1; continue; }
                if (LAKES_ENABLED && t.filled[k] - t.z[k] > LAKE_MIN_FILL && t.par[k] >= 0) {
                    int pk = t.par[k], pi = pk % W, pj = pk / W;
                    int dx = Integer.signum(pi - i), dz = Integer.signum(pj - j);
                    int best = -1;
                    for (int q = 0; q < 8; q++) if (DX[q] == dx && DZ[q] == dz) { best = q; break; }
                    if (best >= 0) { t.dir[k] = (byte) best; continue; }
                }
                int best = -1; double bs = 0.0;
                for (int q = 0; q < 8; q++) {
                    int ni = i + DX[q], nj = j + DZ[q];
                    if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                    double sl = (t.filled[k] - t.filled[nj * W + ni]) / DD[q];
                    if (sl > bs) { bs = sl; best = q; }
                }
                if (OPEN_BOUNDARY && (i == 0 || j == 0 || i == W - 1 || j == W - 1)) {
                    t.dir[k] = DIR_OUT;   // free outlet: leaves the tile, dropped by accOf
                    continue;
                }
                t.dir[k] = (byte) best;
            }
        }
    }

    private static void accOf(Tile t, int[] ord, int[] tmp, int[] cnt) {
        int W = t.W, N = W * W;
        Arrays.fill(t.acc, 0.0);
        if (A0_SEED > 0.0) {
            for (int j = 0; j < W; j++) {
                for (int i = 0; i < W; i++) {
                    if (i == 0 || j == 0 || i == W - 1 || j == W - 1) t.acc[j * W + i] = A0_SEED;
                }
            }
        }
        orderDesc(t.filled, N, ord, tmp, cnt);
        for (int q = 0; q < N; q++) {
            int k = ord[q];
            t.acc[k] += 1.0;
            int d = t.dir[k];
            if (d < 0 || d >= 8) continue;   // sink, or drains out of the tile
            int ni = k % W + DX[d], nj = k / W + DZ[d];
            if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
            t.acc[nj * W + ni] += t.acc[k];
        }
    }

    // ==================== 基数排序 / 堆 ====================

    private static int sk(double v) {
        int s = Float.floatToIntBits((float) v);
        return s ^ ((s >> 31) & 0x7FFFFFFF);
    }

    private static long key(double v, int i) {
        return (((long) sk(v)) << 32) | (i & 0xFFFFFFFFL);
    }

    private static void orderAsc(double[] key, int N, int[] idx, int[] tmp, int[] cnt) {
        for (int i = 0; i < N; i++) idx[i] = i;
        int[] src = idx, dst = tmp;
        for (int pass = 0; pass < 4; pass++) {
            Arrays.fill(cnt, 0);
            int sh = pass * 8;
            for (int i = 0; i < N; i++) cnt[(sk(key[src[i]]) >>> sh) & 0xFF]++;
            int sum = 0;
            for (int b = 0; b < 256; b++) { int c = cnt[b]; cnt[b] = sum; sum += c; }
            for (int i = 0; i < N; i++) { int k = src[i]; dst[cnt[(sk(key[k]) >>> sh) & 0xFF]++] = k; }
            int[] sw = src; src = dst; dst = sw;
        }
        if (src != idx) System.arraycopy(src, 0, idx, 0, N);
    }

    private static void orderDesc(double[] key, int N, int[] idx, int[] tmp, int[] cnt) {
        for (int i = 0; i < N; i++) idx[i] = i;
        int[] src = idx, dst = tmp;
        for (int pass = 0; pass < 4; pass++) {
            Arrays.fill(cnt, 0);
            int sh = pass * 8;
            for (int i = 0; i < N; i++) cnt[(~sk(key[src[i]]) >>> sh) & 0xFF]++;
            int sum = 0;
            for (int b = 0; b < 256; b++) { int c = cnt[b]; cnt[b] = sum; sum += c; }
            for (int i = 0; i < N; i++) { int k = src[i]; dst[cnt[(~sk(key[k]) >>> sh) & 0xFF]++] = k; }
            int[] sw = src; src = dst; dst = sw;
        }
        if (src != idx) System.arraycopy(src, 0, idx, 0, N);
    }

    private static void siftUp(long[] a, int i) {
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (a[p] <= a[i]) break;
            long t = a[p]; a[p] = a[i]; a[i] = t;
            i = p;
        }
    }

    private static void siftDown(long[] a, int i, int n) {
        while (true) {
            int l = 2 * i + 1, r = l + 1, m = i;
            if (l < n && a[l] < a[m]) m = l;
            if (r < n && a[r] < a[m]) m = r;
            if (m == i) break;
            long t = a[m]; a[m] = a[i]; a[i] = t;
            i = m;
        }
    }
}
