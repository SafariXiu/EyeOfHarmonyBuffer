package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TalosLandErosion —— **地貌演化层（LEM / stream power）**。
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
 * {@link #ENABLED} 默认 <b>false</b>。关掉后 {@link TalosLandField#height0} 里
 * 那一行短路判断不执行，<b>逐位回到现状</b>。
 *
 * <h3>许可证</h3>
 * ★ 只借鉴<b>公式</b>（公式不受版权保护），未抄任何 GPL 代码。
 */
public final class TalosLandErosion {

    private TalosLandErosion() {}

    // ==================== 开关 ====================

    /** ★ 总开关。关掉后 {@link TalosLandField#height0} 里那一行短路判断不执行，逐位回到现状。 */
    public static volatile boolean ENABLED = true;

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

    public static volatile int ITER = 200;
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
    public static volatile double WIDTH_MAX = 60.0;
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
    /** 平滑轮数（每轮一次 λ 步 + 一次 μ 步）。 */
    public static volatile int SMOOTH_ROUNDS = 4;
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
     * <p>用 smoothstep 在 [DRY_ACCW_MIN, DRY_ACCW_MIN x DRY_TRANS] 上平滑过渡，
     * 所以不会出现突兀的水头。非河道格返回 0。
     */
    /** ★ 单格的湿润度（未做单调化）：由 pRel 相对阈值决定。 */
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
    public static volatile int BOWL_SPACING = 24;
    public static volatile double BOWL_R = 8.0;
    public static volatile double BOWL_DEPTH = 16.0;
    /** ★ 环形抬升（相对碗深）。没有它时碗在斜坡上装不住水（湖面 0.44% 到 1.52%）。 */
    public static volatile double BOWL_RIM = 0.90;
    public static volatile double BOWL_MIN_RIFT = 0.40;
    public static volatile double LAKE_MIN_FILL = 0.3;

    // ==================== 缓存 ====================

    public static volatile int CACHE_TILES = 16;

    static final int[] DX = { -1, -1, -1, 0, 0, 1, 1, 1 };
    static final int[] DZ = { -1, 0, 1, -1, 1, -1, 0, 1 };
    static final double[] DD = { 1.41421356, 1, 1.41421356, 1, 1, 1.41421356, 1, 1.41421356 };
    static final double EPS = 1.0 / 256.0;

    // ==================== 内部状态 ====================

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
        if (!ENABLED) return;
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
        final double v = sample(t.z, t, px, pz);           // LEM 高度（格）
        final double baseBlk = SEA_BLK + baseM * ELEV_TO_BLK;
        final double d = v - baseBlk;                      // ★ LEM 相对基础剖面的变化量（格）
        // ★ 海岸淡化：靠海处权重到 0 -> 与基础剖面连续 -> 无海岸墙
        final double taper = TalosLandField.smoothstep(0.0, COAST_TAPER_M, baseM);
        // ★★ 不许沉到海平面以下 —— 但必须用【连续】的方式限制，不能用 if 硬钳。
        //    硬钳（out 小于等于 SEA 就设成 SEA+0.5）会在海岸带制造 27 格的悬崖
        //    （实测：相邻格高差 max 从 5.63 米跳到 27.03 米）。
        //    这里改成限制 d 的【负向幅度】，再乘 taper：
        //      taper = 1 时最深正好到 SEA_BLK + 0.5；
        //      taper = 0 时完全不限制；
        //      对 taper 连续，对空间也连续（min 只产生拐点，不产生跳变）。
        final double dMin = -(baseBlk - SEA_BLK - 0.5);
        final double dUse = (d < dMin) ? dMin : d;
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
            double de = segBest - wK * 0.5;
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
    private static Tile tileAt(long ws, double px, double pz) {
        if (!ENABLED) return null;
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

    /** 抬升场（米）= 海岸剖面 + 造山 + 中尺度扰动（海岸淡化）。 */
    public static double baseHeight(long ws, double px, double pz) {
        double sig = TalosLandField.signedCoastDistCF(ws, px, pz);
        double b = TalosLandField.coastProfileCF(sig);
        if (b <= 0.0) return b;
        // ★ 必须与 TalosLandField.height0 【逐位同口径】，否则 LEM 开/关之间会出现台阶。
        //   造山的海岸淡化也要一样（[131]）。
        double ot = TalosLandField.OROG_COAST_TAPER_M > 0.0
                ? TalosLandField.smoothstep(0.0, TalosLandField.OROG_COAST_TAPER_M, b) : 1.0;
        double h = b + (ot > 0.0
                ? TalosLandField.OROG_MAX_M * TalosLandField.orogeny01(ws, px, pz) * ot : 0.0);
        if (NA_AMP != 0.0 && NA_WL > 0.0 && NA_OCT > 0) {
            double taper = TalosLandField.smoothstep(0.0, NA_TAPER, b);
            if (taper > 0.0) h += NA_AMP * taper * fbm(ws ^ 0x3CL, px, pz, NA_WL, NA_OCT);
        }
        return h;
    }

    /** fBm，值域 [-1,1]。 */
    static double fbm(long seed, double x, double y, double wl, int oct) {
        double v = 0.0, amp = 1.0, sum = 0.0, f = 1.0 / wl;
        for (int k = 0; k < oct; k++) {
            double n = TalosLandField.cnoise(x * f, y * f, 1.0, seed + k * 0x9E3779B9L);
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
        int W = SOLVE_CELLS;
        if (W <= 0 || OUT_CELLS <= 0 || BUFFER_CELLS < 0) return null;
        if (W < OUT_CELLS + 2 * BUFFER_CELLS) return null;
        Tile t = new Tile(tx, tz, W);
        int N = W * W;
        double outBlk = OUT_CELLS * STEP_BLK;
        double ox = tx * outBlk - BUFFER_CELLS * STEP_BLK;
        double oz = tz * outBlk - BUFFER_CELLS * STEP_BLK;

        for (int j = 0; j < W; j++) {
            double wz = oz + j * STEP_BLK;
            for (int i = 0; i < W; i++) {
                int k = j * W + i;
                double m = baseHeight(ws, ox + i * STEP_BLK, wz);
                // ★ U 必须存【格】，与更新式同一量纲（米会把 uu 放大 55.6 倍 -> 地形被抬飞）
                t.U[k] = SEA_BLK + m * ELEV_TO_BLK;
                t.z[k] = SEA_BLK + m * ELEV_TO_BLK;
                t.sea[k] = (m <= 0.0);
            }
        }

        int fe = Math.max(1, FLOW_EVERY);
        long[] heap = new long[N];
        int[] ord = new int[N];
        int[] tmp = new int[N];
        int[] cnt = new int[256];
        double[] uBuf = new double[N];

        for (int it = 0; it < ITER; it++) {
            if (it % fe == 0) { fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt); }
            orderAsc(t.z, N, ord, tmp, cnt);
            for (int oi = 0; oi < N; oi++) {
                int k = ord[oi];
                if (t.sea[k]) continue;
                int d = t.dir[k]; if (d < 0) continue;
                int ni = k % W + DX[d], nj = k / W + DZ[d];
                if (ni < 0 || ni >= W || nj < 0 || nj >= W) continue;
                double al = DK * Math.pow(t.acc[k], M);
                double uu = (t.U[k] - SEA_BLK) / UREF;
                double up = uu > 0.0 ? DTU * UREF * Math.pow(uu, GAMMA) : 0.0;
                double nb = t.z[nj * W + ni];
                double zd = nb > SEA_BLK ? nb : SEA_BLK;
                t.z[k] = (t.z[k] + up + al * zd) / (1.0 + al);
            }
        }
        fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        if (LAKES_ENABLED) {
            carveLakes(ws, t, ox, oz);
            fill(t, heap); dirs(t); accOf(t, ord, tmp, cnt);
        }
        precipField(ws, t, ox, oz);   // ★ 有效汇流面积 accW（只读，不改地形；用 baseHeight 不递归）
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
                com.EyeOfHarmonyBuffer.sim.runtime.SimClimate.CELL / STEP_BLK));
        final int ncg = (W + CG - 1) / CG + 1;
        final double[] pg = new double[ncg * ncg];
        for (int cj = 0; cj < ncg; cj++) {
            for (int ci = 0; ci < ncg; ci++) {
                final int wx = (int) (ox + (ci * CG + CG * 0.5) * STEP_BLK);
                final int wz = (int) (oz + (cj * CG + CG * 0.5) * STEP_BLK);
                double pv;
                try { pv = com.EyeOfHarmonyBuffer.sim.runtime.SimClimate
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
     * {@code NoiseContinentGrid.height -> TalosLandField.height -> TalosLandErosion.height}
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
                    com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(zi));
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
                    kappa = com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.KAPPA_MEAN;
                } catch (Throwable ex) { precipFails++; }
                double depl = 0.0;
                try { depl = com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.depletion(hUp, kappa); }
                catch (Throwable ex) { depl = 1.0; }
                final double moist = MOIST_FLOOR + (1.0 - MOIST_FLOOR) * depl;
                // ★★★ 只对【陆地】应用降水修正，并除以 P_REF_MM。
                //   实测踩过的坑：
                //     ① 最初漏了 (P_REF_BASE/P_REF_MM) —— P_REF_MM 根本没被用，河宽全线崩到 1.0
                //     ② 对海洋也乘了 pRel —— 海洋 pRel 很小，把【陆地】的均值从 0.844 拉到 0.432
                //   P_REF_BASE = 1000 是代理公式的隐含参考（探针在只统计陆地时测出 mean(pRel)=0.844）。
                // ★ 纬向降水带（零成本）：赤道 ITCZ 湿、副热带干、中纬风暴带次湿。
                //   它【不与河道位置反相关】—— 这是它能纠正地形代理系统性偏差的原因。
                final double latR = com.EyeOfHarmonyBuffer.sim.world.WorldContract.latOf(zi);
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
        if (!MEANDER_ENABLED) { t.chOrd = null; t.nCh = 0; return; }
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

    /** 湖泊层：裂谷选位，挖碗 + 环形 rim。 */
    private static void carveLakes(long ws, Tile t, double ox, double oz) {
        int W = t.W;
        int sp = Math.max(2, BOWL_SPACING);
        double R = BOWL_R, D = BOWL_DEPTH, rim = BOWL_RIM;
        int Ri = (int) Math.ceil(R);
        if (Ri < 1 || D <= 0.0 || R <= 0.0) return;
        for (int cj = sp / 2; cj < W; cj += sp) {
            for (int ci = sp / 2; ci < W; ci += sp) {
                int ck = cj * W + ci;
                if (t.sea[ck]) continue;
                double pb = TalosLandField.plateBoundary01(ws, ox + ci * STEP_BLK, oz + cj * STEP_BLK);
                double cv = TalosLandField.convergence01(ws, ox + ci * STEP_BLK, oz + cj * STEP_BLK);
                if (pb * (1.0 - cv) < BOWL_MIN_RIFT) continue;
                for (int dj = -Ri; dj <= Ri; dj++) {
                    for (int di = -Ri; di <= Ri; di++) {
                        int i = ci + di, j = cj + dj;
                        if (i < 0 || i >= W || j < 0 || j >= W) continue;
                        int k = j * W + i;
                        if (t.sea[k]) continue;
                        double r2 = (di * di + dj * dj) / (R * R);
                        if (r2 >= 1.0) continue;
                        double w = (1.0 - r2) * (1.0 - r2);
                        double rimp = 4.0 * r2 * (1.0 - r2);
                        t.z[k] += D * (rim * rimp - w);
                    }
                }
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
                t.dir[k] = (byte) best;
            }
        }
    }

    private static void accOf(Tile t, int[] ord, int[] tmp, int[] cnt) {
        int W = t.W, N = W * W;
        Arrays.fill(t.acc, 0.0);
        orderDesc(t.filled, N, ord, tmp, cnt);
        for (int q = 0; q < N; q++) {
            int k = ord[q];
            t.acc[k] += 1.0;
            int d = t.dir[k];
            if (d < 0) continue;
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
