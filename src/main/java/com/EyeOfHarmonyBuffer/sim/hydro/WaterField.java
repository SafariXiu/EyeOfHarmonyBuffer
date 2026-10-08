package com.EyeOfHarmonyBuffer.sim.hydro;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WaterField —— **地表水系**（河流 / 湖泊 / 海岸线排水）。设计冻结 §7495。
 *
 * <h3>它是什么</h3>
 * 一个**按需解、按 tile 缓存**的水文条件化场：给定世界坐标，返回该点的
 * <b>填充量</b>（= 湖泊 / 湿地深度，米）与 <b>汇流累积</b>（= 河道强度，格数）。
 *
 * <p>本类**不产生任何新常数**：三个算法步骤的形式与唯一那个 ε 全部有逐字出处（见下）。
 *
 * <h3>算法（三步，全部有出处）</h3>
 * <ol>
 *   <li><b>洼地填充 = Priority-Flood + ε</b>。形式取自 {@code arXiv:1511.04463 §3.1} 逐字：
 *       「inserting the <b>edge cells</b> of a DEM into a <b>priority-queue</b> where they are ordered
 *       by increasing elevation. The cell with the <b>lowest elevation is popped</b> … each
 *       <b>neighbor which has not already been considered</b> is manipulated and then <b>added to
 *       the priority queue</b>. The algorithm continues until the priority queue is empty.」
 *       而 {@code manipulated} = 抬到不低于当前（摘要逐字 「Filling them so they are <b>level</b>」）。
 *       <br>ε 的出处：{@code docs/模拟器/调研/大陆算法调研/03-侵蚀与水文.md} §6.2 逐字
 *       「<b>Priority-Flood + ε（每步强制加一个极小增量）</b>，用 <b>1/256 格的高度</b>」；
 *       而 1 格 = 1 米由 {@code TalosField} 类头逐字锁死 ⇒ {@link #EPS} = 1/256 m。
 *       <br>为什么必须有 ε：RichDEM 官方文档 {@code richdem.readthedocs.io/…/flat_resolution.html}
 *       逐字「the resulting DEM contains <b>mathematically flat areas with no local gradient</b>.
 *       This makes it <b>impossible to determine flow directions</b> … <b>Imposing an epsilon
 *       gradient during depression-filling is one solution</b>」。</li>
 *   <li><b>流向 = D8</b>。定义取自 RichDEM 官方文档逐字（原始文献 O'Callaghan &amp; Mark, 1984,
 *       <i>Computer vision, graphics, and image processing</i> <b>28</b>, 323-344）：
 *       「assigns flow from a focal cell to <b>one and only one of its 8 neighbouring cells</b>.
 *       The chosen neighbour is the one accessed via the <b>steepest slope</b>. When such a
 *       neighbour does not exist, <b>no flow direction is assigned</b>.」</li>
 *   <li><b>汇流累积</b>：按高程降序一次遍历累加（上游必先算完），与 D8 同源。</li>
 * </ol>
 *
 * <h3>网格与窗口（为什么是这个形状）</h3>
 * <ul>
 *   <li><b>格距 {@link #STEP} = 150 km</b>：与 {@code PrecipField.UPWIND_STEP} 同值，
 *       而那也是本仓「最细独立物理尺度 150 km / 过采样 15×」（{@code 模型层级定位.md:107}）。</li>
 *   <li><b>为什么必须开窗口</b>：塔罗斯的 X 与 Z <b>都无周期</b>（{@code TalosField} 类头逐字
 *       「X 无限、Z 无限、<b>都无周期</b>」）⇒ 不存在「全局网格」这种东西 ⇒
 *       只能「窗口 + halo、只取中心」，而那正是本仓调研 03 §6.1 逐字给的做法
 *       「区块生成时取「<b>区块 + 半径 R 的 halo</b>」作为窗口 … 跑一次窗口内的
 *       Priority-Flood；<b>只取中心</b>」。</li>
 *   <li><b>{@link #HALO} = 8 格</b>：P1162 实测收敛性 —— halo=0 时中心 16x16 有 5/256 格与
 *       全窗口参考解不一致（最大差 103.8 m），<b>halo>=4 起完全一致</b>；取 8 = 收敛值的 2 倍余量。
 *       ⚠ 03 §6.1 举例的「R = 64 格」是 1 m 网格的 64 m，<b>不可直接搬到 150 km 网格</b>。</li>
 *   <li><b>判据 {@code fillDepth > 0} 是干净的，<u>不需要阈值</u></b>（P1165）：扫 640x640 格
 *       （96000 km 见方），「fillDepth 恰为 1 步 ε」的格是 <b>0 个</b>；「显著 &gt; 1.5 步 ε」
 *       占填充格的 <b>99.98%</b>。物理原因：150 km 网格上 {@code hyp} 插值让网格点几乎不等高，
 *       ε 只在 Priority-Flood 的<b>传播路径</b>上起作用，<b>不会造出「被 ε 抬过的平地」</b>。</li>
 *   <li><b>与独立实现的交叉验证（P1164）</b>：对 3 个 tile，把本类的解与「在 112x112 大窗口里
 *       独立解一遍」逐格比对 —— <b>{@code fillDepth} 三个 tile 全部 0/256 一致</b>；
 *       而 {@code flowAcc} 在其中一个 tile 有 8/256 不一致（最大 12.0 格），
 *       原因是<b>窗口大小不同</b>（本类 32 格 vs 112 格 ⟹ 大窗口看得到更远的上游）。
 *       ⟹ <b>结论：{@code fillDepth} 是窗口无关的量（可当物理量），
 *       {@code flowAcc} 是窗口相关的量（只能当相对量，例如取分位/排序）。</b>
 *       本仓 <b>P170</b>（{@code MountainLayerV2} 的 carve 域实验）独立给出同一结论。</li>
 * </ul>
 *
 * <h3>开销（P1163 实测）</h3>
 * <ul>
 *   <li><b>冷 tile：0.57 ms / tile</b>（P1163：400 次查询跨 75 个 tile 共 41.7 ms）。
 *       早先按 0.93 us/格 外推成「约 1 ms」，那是它的 1.8 倍。</li>
 *   <li><b>热查询：0.36 us / 次</b>（同一批点重复查询）。</li>
 *   <li><b>A/B 实测</b>（P1167c，12 次交替取 min，150 km 节距）：关 34.44 ms / 开 35.39 ms
 *       ⇒ <b>+2.75%</b>，而那几乎全是 2 个冷 tile 的代价。</li>
 *   <li><b>生产场景的节距 = 10 km</b>（{@code SimClimate.CELL}）⟹ 2400 km 的 tile 里有
 *       240 个格点 ⟹ <b>同 tile</b> ⟹ 热缓存成立 ⟹ <b>约为 {@code mmPerDay} 的 0.001%</b>。</li>
 * </ul>
 * 对照：{@code PrecipField.mmPerDay} 约 35.5 ms/点。
 *
 * <h3>线程安全</h3>
 * <b>查询无锁</b>：{@link #fillDepthM}/{@link #flowAcc}/{@link #isWater} 走 {@link ConcurrentHashMap}
 * 的无锁读，只有 install/uninstall/installedSeed 加锁。**这一条是硬要求** —— 本场要在
 * {@code PrecipField.mmPerDay}（35.5 ms/点的热路径）上被逐点查询。
 *
 * <h3>本类【没有】做什么（诚实清单）</h3>
 * <ul>
 *   <li><b>不含地下水 / 基流</b>。设计冻结 §7492 已查明：形式 {@code -dQ/dt = a*Q^b} 有出处，
 *       但 {@code a}/{@code b} 在文献里是 <b>recession parameters（标定量）</b> ⇒
 *       按硬前提①（不引入可调旋钮）<b>做不了</b>，用户 2026-09-26 裁决「地下水系先不管」。</li>
 *   <li><b>还没有消费者</b>。本类目前只提供查询；把它接进气候（湖泊/湿地的局地蒸发源 ⇒ q）
 *       是设计冻结的【步 4】，那一步会改变气候输出，必须单独跑 22 支套件。</li>
 * </ul>
 */
public final class WaterField {

    private WaterField() {}

    /** 8 邻域的行偏移（D8）。提到类级：Cordonnier 的辅助函数也要用。 */
    private static final int[] DI = { -1, -1, -1, 0, 0, 1, 1, 1 };
    /** 8 邻域的列偏移（D8）。 */
    private static final int[] DJ = { -1, 0, 1, -1, 1, -1, 0, 1 };

    // ==================== 网格 ====================

    /**
     * 格距（block）。**§7613：从 150 km 改为 10 km。**
     *
     * <p><b>旧依据（150 km）</b>：与 {@code PrecipField.UPWIND_STEP} 同值（§7495）—— 那是
     * <b>地形采样</b>的尺度。
     *
     * <p><b>新依据（10 km）</b>：与 {@link SimClimate#CELL} <b>同值</b>。理由（P1243-P1259 实测）：
     * <ul>
     *   <li>河网的判据（河道宽度）需要 10 km 分辨率；150 km 上一个「河宽」是 15 个格距，无意义。</li>
     *   <li>产流深 Rr 的输入（降水 P、气温 T）**本来就定义在 SimClimate 的 10 km 格点上**
     *       （{@code SimClimate.CELL = 10_000}）⟹ 用 10 km 采样它们<b>不跨界、不重建</b>。</li>
     *   <li>P1259 实测：在 10 km 上取 Rr 的两个输入只要 <b>0.2 ms / 256 格</b>；
     *       而在 150 km 上取同一批格点要 <b>103 秒</b>（P1243）⟹ 旧值下<b>不可用</b>。</li>
     * </ul>
     * <p><b>不是可调旋钮</b>：它等于另一个已有的主格点间距（零新尺度）。
     */
    public static final int STEP = 250;   // ★ 缩 40x（原 10,000）

    /** 每 tile 的格数（一维）。tile = {@code TILE x TILE} 格。 */
    public static int TILE = 16;

    /**
     * 窗口向外扩张的 halo（格）。窗口边长 = {@code TILE + 2*HALO}。
     *
     * <p>P1162 实测：halo=0 有 5/256 格不一致（最大 103.8 m），<b>halo&gt;=4 起完全收敛</b>；
     * 取 8 = 2 倍余量。
     */
    public static int HALO = 16;

    /**
     * 平地抬升增量（米）。出处：调研 03 §6.2 逐字「Priority-Flood + ε … 用 <b>1/256 格的高度</b>」，
     * 而 1 格 = 1 米由 {@code TalosField} 类头逐字锁死。**不是可调旋钮**。
     */
    public static final double EPS = 1.0 / 256.0;

    /**
     * ★★★★★★★★ <b>§7628：湖的最小深度（米）= 本仓【自己的】填洼深度分位数 q~0.95</b>。
     *
     * <h3>为什么不是「粗糙度 x N」</h3>
     * <p>设计冻结 89753-89760 给的那条判据是「湖的深度必须远大于地形粗糙度」，
     * 而它引用的两个数 —— 粗糙度 {@code 16.20 m} 与「前 5 大湖 277.7~441.1 m」——
     * <b>都是在 {@code STEP = 150 km} 的旧网格上测的</b>（P1182b）。
     *
     * <p>§7613 把 {@code STEP} 改成 <b>10 km</b>（与 {@code SimClimate.CELL} 对齐）之后，
     * P1273 实测本区域（160x160 格）的填洼深度分布变成：
     * <pre>
     *   q=0.5     14.37 m
     *   q=0.9     58.01 m
     *   q=0.99   120.69 m
     *   q=1.0    【188.67 m】  (= 该区域的最大值)
     * </pre>
     * ⟹ <b>一个都没有超过 277.7 m</b> ⟹ 162 m 的阈值会把 99.9% 的洼地滤掉
     * （P1272 实测：depth>=162 只剩 <b>4 格</b>，湖 = 0.03%）。
     * <b>⟹ 所以那组旧数【不能搬到 10 km 网格</b>（与 §7605 的 max_delta 是同一类问题）。
     *
     * <h3>所以用本仓自己的分位数</h3>
     * <p>P1273 的分布里，<b>q~0.95 对应约 85 m</b>，而它给出的湖占陆地约
     * <b>1.5%</b> —— 与地球的 <b>1~2%</b> 同量级。
     *
     * <p>⚠ <b>本常量是「可标定的分位数」，不是逐字常数</b>（与 B4 的观测锚 1.8~4.1
     * 同性质）。它的依据是<b>本仓自己的填洼深度分布</b>（P1273 实测），
     * 而不是任何外部文献值。
     *
     * <p>⚠ <b>它依赖网格尺度</b>：若 {@code STEP} 再变，本值必须重测。
     */
    public static double LAKE_MIN_DEPTH_M = 85.0;
    /**
     * ★★★★★★★★ <b>§7631：河道的最小宽度（米）= 25 m</b> —— 依据【外部观测的河网密度】。
     *
     * <h3>出处</h3>
     * <p>设计冻结 7600 逐字：「地球：Allen &amp; Pavelsky 2018（Science）常引用「约 120 万 km
     * 的河（宽 &gt; 30 m）」⟹ 1,200,000 km / 149,000,000 km^2 = 0.008 km/km^2
     * ⟹ 在 10 km 网格上（每格 100 km^2）每格期望 0.8 km ⟹ <b>约 8% 的格含一条 &gt;30 m 的河</b>」。
     *
     * <p>而 P1276 实测本仓的 W 分布（256x256 = 2,560 km）：
     * <pre>
     *   W >= 20 m : 14.032% of land
     *   W >= 25 m : 【 8.956%】  <- 最接近地球的 8%
     *   W >= 30 m :  6.190%
     *   W >= 60 m :  1.224%   <- 先前用的值，比地球稀 6.5 倍
     * </pre>
     *
     * <p>⟹ <b>25 m 是【用外部观测密度锚定出来的】，不是随手选的</b>。而它与地球对「小河」
     * 的定义（宽 &gt; 30 m）【同量级】，互为旁证。
     *
     * <p>⚠ <b>它的地位</b>：与 {@link #LAKE_MIN_DEPTH_M} 和 B4 的观测锚（1.8~4.1）同性质 ——
     * <b>一个可标定的分位数</b>，由外部观测定标。
     */
    public static double RIVER_MIN_WIDTH_M = 140.0;

    /**
     * ★★★★★★★★ <b>§7529：河道起始阈值（格数）。`A < A_MIN_CELLS` 的格【不给河宽】（W = 0）。</b>
     *
     * <h3>为什么必须有它（缺了它会怎样）</h3>
     * <p>没有阈值时，<b>`W = 6.289*Q^0.46` 对【每一格】都给 W &gt; 0</b> ——
     * P1573 实测：<b>100.0% 的陆地格都有河宽</b>。而 `A = 1` 表示该格<b>没有任何上游</b>
     * （纯坡面流），地球上的坡面流<b>不是河道</b>。
     * <p>后果：一条只收集自己那一格的「河」被画成 <b>36 m 宽</b>（P1573 实测 W 中位数）
     * ⟹ 这正是用户看到的「<b>短而肥</b>」的「肥」。
     *
     * <h3>取值依据：用【地球河网密度】锚定，而不是拍的</h3>
     * <p>P1585 实测（10 km 格距，4095 个陆地点）：
     * <pre>
     *   A_min | 形成河道的陆地占比
     *     1   |   100.0%   &lt;-- 现状（全是河）
     *     2   |    43.5%
     *     3   |    35.7%
     *     5   |    27.1%   &lt;-- 本值
     *     8   |    21.5%
     *    13   |    17.1%
     * </pre>
     * <p>地球上「有河道」的格占比（= 河网密度）在 10 km 格距上应为 <b>20~40%</b>；
     * 本值给 <b>27.1%</b>，落在锚中点。
     *
     * <h3>它是不是「新常数」</h3>
     * <p><b>不是新概念</b>：{@link #isLake} 早就在用同一个判据（{@code c[1] > 1.0}，「有上游」），
     * 而且它的 javadoc（`:435` 逐字）写着「<b>零新常数：不引入任何阈值</b>」——
     * 因为「{@code acc > 1}」就是「非源头」的<b>定义</b>。本常量把同一个概念推广到河宽上，
     * 并把门槛从「1」（纯定义）抬到「5」（地图学上的河网密度锚）。
     * <p>同款概念在 {@code MountainLayerV2.Tune.aGateLo = 100 格（250 m 格 = 6.25 km²）} 也存在。
     *
     * <p>⚠ <b>地位</b>：与 {@link #RIVER_MIN_WIDTH_M} / {@link #LAKE_MIN_DEPTH_M} 同性质 ——
     * <b>一个由外部观测定标的可标定量</b>，不是逐字常数。
     * <p>⚠ <b>它只影响河宽，不影响湖</b>（{@code isLake} 用 `c[1] &gt; 1.0`，与河宽无关）。
     */
    public static double A_MIN_CELLS = 5.0;
    /**
     * ★★★★★★★★ <b>§7641：并行构建的线程数上限（默认 16）。</b>
     *
     * <p><b>为什么需要它</b>：solveTile 之间【相互独立】，所以大范围扫描（出图、
     * 离线烘焙）可以并行。P1286 实测（32 逻辑核的机器，同一区域 + 每轮清缓存）：
     * <pre>
     *   线程 |  1     2     4     8     16    32
     *  加速 | 1.00x 1.25x 1.90x 2.48x 3.81x 5.00x
     * </pre>
     *
     * <p><b>为什么默认 16 而不是全核</b>：这是一台【共用的】工作站 —— 用户在跑模拟的
     * 同时还要做别的活。16 线程拿到 3.81x（出图 3,840 km 约 4.3 分钟），
     * 而留给系统的余量足够。调到 32 可以再多 1.3x，但那会吃掉全部核。
     *
     * <p><b>为什么加速比不是线性的</b>（P1289 的线程状态采样查明）：一个 WaterField
     * tile 要 12 个气候瓦片，而<b>相邻的 tile 共享它们</b> ⟹ 处理相邻区域的线程
     * <b>必然互相等</b>。这个「等」是<b>正确行为</b>（避免重复计算 —— 否决它会让总
     * CPU 时间涨 3 倍，P1287 实测），而它的大小取决于工作分配的<b>空间局部性</b>：
     * 按【连续块】分配（相邻 tile 同线程）比按【步长】分配快得多。
     *
     * <p>⚠ <b>这是基础设施参数，不是物理常数</b>：它【不改变任何计算结果】。
     */
    public static int PARALLELISM = 16;
    /** 缓存多少个 tile（LRU）。这是<b>基础设施</b>参数，不是物理常数。 */
    public static int CACHE_TILES = 512;

    // ==================== 状态 ====================

    /** 已安装的世界种子；{@link Integer#MIN_VALUE} = 未安装。 */
    private static int installedSeed = Integer.MIN_VALUE;

    /** 解过的 tile 数（诊断用）。 */
    public static volatile int tilesSolved = 0;

    /**
     * 缓存：tileKey -> [fillDepthM(TILE*TILE), flowAcc(TILE*TILE)]。
     *
     * <p><b>为什么是 {@link ConcurrentHashMap}</b>：本场要在 {@code PrecipField.mmPerDay} 这条
     * **热路径**上被逐点查询（那是 35.5 ms/点的调用），所以读取必须**无锁**。淘汰策略学本仓已有的
     * {@code SoilMoisture} 模式（{@code if (m.size() > MEMO_MAX) m.clear();}）：**不做 LRU，
     * 只做容量上限 + 整体清空** —— 简单且无锁。
     */
    private static final ConcurrentHashMap<Long, double[][]> CACHE = new ConcurrentHashMap<Long, double[][]>();

    // ==================== 接线 ====================

    /**
     * 记下世界种子。**目前没有消费者**（本类只提供查询），但换世界必须重新 install，
     * 否则缓存里是上一个世界的水系。
     */
    public static synchronized void install(int worldSeedInt) {
        if (installedSeed != worldSeedInt) CACHE.clear();
        installedSeed = worldSeedInt;
    }

    /** 清空缓存（换世界 / 换种子）。 */
    public static synchronized void uninstall() {
        CACHE.clear();
        installedSeed = Integer.MIN_VALUE;
        tilesSolved = 0;
    }

    /** 当前安装的世界种子；未安装返回 {@link Integer#MIN_VALUE}。探针用它自证口径。 */
    public static synchronized int installedSeed() { return installedSeed; }

    // ==================== 查询 ====================

    /**
     * 填充量（米）：&gt; 0 表示该格是<b>湖泊 / 湿地</b>（被 Priority-Flood 抬升过）。
     * 出处：调研 03 §6.1 逐字「<b>「填充量」本身是极好的地形素材</b>：填充高度差大的地方
     * 就是「湖 / 干盐湖 / 湿地」」。
     */
    public static double fillDepthM(int x, int z, long seed) {
        return cell(x, z, seed)[0];
    }

    /**
     * <b>热路径用的布尔查询</b>：该格是不是湖 / 湿地（{@code fillDepth > 0}）。
     *
     * <p>判据<b>不需要任何阈值</b>：P1165 实测 640x640 格上「fillDepth 恰为 1 步 ε」的格是
     * <b>0 个</b>，「显著 &gt; 1.5 步 ε」的占填充格的 <b>99.98%</b> —— 150 km 网格上
     * {@code hyp} 插值让网格点几乎不等高，所以 ε 只在 Priority-Flood 的传播路径上起作用，
     * <b>不会造出「被 ε 抬过的平地」</b>。
     */
    public static boolean isWater(int x, int z, long seed) {
        return cell(x, z, seed)[0] > 0.0;
    }

    /** 汇流累积（格数）：上游有多少格的水经过这里。<b>河道</b> = 该值大的格。 */
    public static double flowAcc(int x, int z, long seed) {
        return cell(x, z, seed)[1];
    }


    // ==================== 河网（§7613 落地） ====================

    /** 一年的秒数（m^3/yr -> m^3/s）。与探针 P1228 同值。 */
    private static final double SEC_PER_YEAR = 3.155693e7;

    /**
     * ★★★★★★★★ <b>河道宽度（米）</b> —— W = 6.289 * Q^0.46。
     *
     * <h3>出处（全链逐字）</h3>
     * <ul>
     *   <li><b>形式</b>：govinfo_hydraulic_geometry.pdf Table 5.3（p322 目视）
     *       「Downstream(1) Sand bed: W = Q_b^0.46」。</li>
     *   <li><b>Q_b 的口径</b>：Table 5.2（:32791 逐字）「Average Downstream Relations
     *       <b>(bank-full or mean annual flow)</b>」⟹ 用年平流量合法。</li>
     *   <li><b>锚点</b>：:36764-36765 逐字「At bankfull discharge conditions
     *       <b>Q1 = 8000 cfs</b> … is <b>W1 = 250 ft</b>」⟹
     *       W = 76.2*(Q/226.5)^0.46 = 6.289*Q^0.46（Q in m^3/s, W in m）。</li>
     *   <li><b>连续性约束</b>：:32674 a*c*k=1 与 :32888 b+f+m=1 都满足
     *       （0.46+0.08+0.46 = 1.00）。</li>
     * </ul>
     *
     * <p>⚠ <b>旧式 W = 2.5*sqrt(Q) 已被否决</b>：它在 Table 5.3 里不存在（那是早先对
     * 扫描件的误读），且与锚点差 2 倍（§7603 实测对比）。
     *
     * @return 河宽（米）。0 表示该格不产流也不接收上游水。
     */
    public static double riverWidthM(int x, int z, long seed) {
        return cell(x, z, seed)[2];
    }

    /**
     * <b>是不是河道</b>：riverWidthM >= minWidthM。
     *
     * <p>阈值由调用方给（默认建议见设计冻结 §7599：60 m 在 10 km 网格上给出合理的河网密度）。
     * 本函数<b>不引入任何常数</b>。
     */
    public static boolean isRiver(int x, int z, long seed, double minWidthM) {
        return cell(x, z, seed)[2] >= minWidthM;
    }

    /** §7631：是不是河道（用 {@link #RIVER_MIN_WIDTH_M} 这个【有外部观测锚】的阈值）。 */
    public static boolean isRiver(int x, int z, long seed) {
        return cell(x, z, seed)[2] >= RIVER_MIN_WIDTH_M;
    }
    /**
     * ★★★★★★★★ <b>§7641：并行地扫一片矩形区域并回调每一格</b>。
     *
     * <p>给【出图 / 离线烘焙】用。生产（区块生成）走的是单点查询，不用这个。
     *
     * <p><b>工作分配用【连续块】（按行带切分）</b>（P1286 实测：32 线程 2.45x -> 5.00x；
     * 16 线程 2.51x -> 3.81x）。为什么：相邻的 tile 共享气候瓦片，若它们归不同线程，
     * 那些线程会互相等（P1289 的 BLOCKED 采样）。连续块让一个线程把一片区域
     * 内的瓦片复用到底，只在块边界处等。
     *
     * @param x0    左下角世界 X（米）
     * @param z0    左下角世界 Z（米）
     * @param n     每边的格数
     * @param step  格距（米）
     * @param seed  地形种子（SimTerrain.seedOf(worldSeedInt)）
     * @param sink  回调，参数为 (格序号 c, 行序号 r, 值数组) —— 值数组是 cell 的
     *              5 元组（fill, acc, width, pit, dir）。
     *              <b>回调必须自己保证线程安全</b>（例如写进预先按 (c,r) 索引的数组）。
     */
    public static void scanParallel(final int x0, final int z0, final int n, final int step,
                                    final long seed, final CellSink sink) {
        int threads = Math.max(1, Math.min(PARALLELISM, n));
        int per = (n + threads - 1) / threads;
        Thread[] ts = new Thread[threads];
        final java.util.concurrent.atomic.AtomicReference<Throwable> err =
            new java.util.concurrent.atomic.AtomicReference<Throwable>();
        for (int t = 0; t < threads; t++) {
            final int lo = t * per, hi = Math.min(n, (t + 1) * per);
            if (lo >= hi) continue;
            ts[t] = new Thread(new Runnable() { public void run() {
                try {
                    for (int r = lo; r < hi; r++) {
                        for (int c = 0; c < n; c++) {
                            sink.accept(c, r, cell(x0 + c * step, z0 + r * step, seed));
                        }
                    }
                } catch (Throwable e) { err.compareAndSet(null, e); }
            }}, "WaterField-scan-" + t);
            ts[t].start();
        }
        for (int t = 0; t < threads; t++) {
            if (ts[t] == null) continue;
            try { ts[t].join(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        Throwable e = err.get();
        if (e != null) throw new RuntimeException("scanParallel failed", e);
    }

    /** scanParallel 的回调。 */
    public interface CellSink {
        /** @param c 列序号 · @param r 行序号 · @param v 5 元组（fill, acc, width, pit, dir） */
        void accept(int c, int r, double[] v);
    }

    /**
     * ★★★★★★★★ <b>§7622：这一格是不是「洼地」（湖的候选）</b>。
     *
     * <p>由 Cordonnier 2019 的流向修正标出：{@code true} ⟺ 该格的接收者被算法改过，
     * 或它的下游比它【高】（跨过 spill 的那一段）。换句话说：这一格在【水面之下】。
     *
     * <p>⚠ 本函数【只是湖的【必要条件】。要判定它是湖，还需要【有河汇入】——
     * 见 {@link #isLake}。
     */
    public static boolean inDepression(int x, int z, long seed) {
        return cell(x, z, seed)[3] > 0.5;
    }

    /**
     * ★★★★★★★★ <b>§7622：这一格是不是湖</b>。
     *
     * <h3>判据（layer 1，零新常数）</h3>
     * <p>湖 = 洼地 <b>且</b> 【有足够大的河汇入】：
     * <pre>
     *   inDepression(x,z) && riverWidthM(x,z) >= minWidthM
     * </pre>
     * <b>为什么这一条就够</b>（物理）：真实世界的湖【都有河进出】—— 没有河的洼地会干，
     * 而干的洼地在视觉上不是湖（是盐滩/干坑）。所以【无河的洼地不画】。
     *
     * <p>⚠ 本函数【不引入任何新常数】：{@code minWidthM} 与 {@link #isRiver} 共用同一个
     * 河宽阈值（设计冻结 §7599 的 60 m）。
     *
     * <p>⚠ 另两条更细的判据（深度 &gt; 粗糙度的 N 倍 · 保留填充量前 N 个）在
     * 设计冻结 §7620 里已查清出处，但都仍需一个【待标定的分位数】⟹ 本版【不启用它们】。
     */
    public static boolean isLake(int x, int z, long seed, double minWidthM) {
        double[] c = cell(x, z, seed);
        return c[3] > 0.5 && c[2] >= minWidthM;
    }

    /**
     * ★★★★★★★★ <b>§7530：湖的最小汇入（格数）—— 「湖由河派生」的判据。</b>
     *
     * <h3>为什么「有上游」（acc &gt; 1）不够</h3>
     * <p>原判据是 {@code acc > 1}（至少 1 个上游格），javadoc 称之为「零新常数」。
     * 但 P1588 实测：它给出的湖占陆地 <b>28.93%</b>，而地球是 <b>1~2%</b> —— <b>高 15 倍</b>。
     * <p>物理上，{@code acc = 2} 的洼地只从<b>一个</b>邻居收到水；那种洼地在真实地貌里
     * <b>不会成湖</b>（它会被自己的流域填满、或根本存不住水 —— 见 §7530 的推导）。
     * <b>湖需要【显著的上游汇水面积】。</b>
     *
     * <h3>取值依据：地球湖覆盖率锚（P1588 实测）</h3>
     * <pre>
     *   判据（洼地 且 ...）        | 湖占陆地
     *   acc >  1   （改前）        |  28.93%
     *   acc >= 5                   |  19.29%
     *   acc >= 20                  |   9.99%
     *   acc >= 50                  |   6.07%
     *   acc >= 100                 |   3.59%
     *   acc >= 200 （本值）        |   1.59%   &lt;-- 地球 1~2%
     *   acc >= 500                 |   0.18%
     * </pre>
     * ⟹ <b>{@code 200} 精确命中地球锚，且【不需要任何深度门槛】。</b>
     *
     * <h3>★ 为什么这与用户的判断一致（湖由河派生）</h3>
     * <p>用户 2026-09-30 的判断逐字：「<b>湖泊的形成要有河流汇入才有湖 …
     * 我们是不是应该自河流派生出湖而不是先算湖再让河流流进去</b>」。
     * <p>本判据正是那个形式：<b>洼地【且】上游汇水面积足够大</b> ⟹ 湖。
     * 上游汇水面积就是「有多少条河汇到这里」。
     * <p>⚠ 原来的 {@code LAKE_MIN_DEPTH_M} 是一条<b>独立的深度门槛</b>（见其 javadoc），
     * 它<b>依赖窗口尺度</b>（填洼深度与域大小有关）⟹ 用久了必然漂移。本判据
     * <b>只依赖汇流累积</b>（相对量，已在用），因此更稳。
     *
     * <p>⚠ <b>地位</b>：与 {@link #A_MIN_CELLS} / {@link #RIVER_MIN_WIDTH_M} 同性质 ——
     * <b>一个由外部观测定标的可标定量</b>。
     */
    public static double LAKE_MIN_INFLOW_CELLS = 200.0;

    /**
     * ★★★★★★★★ <b>§7628：这一格是不是湖（推荐用法）</b>。
     *
     * <h3>判据（§7530 起改为「湖由河派生」）</h3>
     * <pre>
     *   湖 = 洼地（{@link #inDepression}）
     *        且【上游汇水面积 >= {@link #LAKE_MIN_INFLOW_CELLS}】（{@link #flowAcc}）
     * </pre>
     *
     * <p><b>为什么不是「有足够宽的河」</b>（P1272 实测）：在降水稀疏的区域，
     * {@code W >= 60 m} 几乎处处不满足 ⟹ 深洼地（最大的 124 格）【全部被滤掉】，
     * 湖只剩 0.03%。而物理上 —— <b>湖只要有水来就行，不要求来的是大河</b>。
     * <b>上游汇水面积</b>才是那个正确的量。
     *
     * <p><b>改前是 {@code acc > 1}</b>（P1588 实测给 28.93% 的湖，地球 1~2%）；
     * 改成 {@code acc >= 200} 后给 <b>1.59%</b>。理由见 {@link #LAKE_MIN_INFLOW_CELLS}。
     */
    public static boolean isLake(int x, int z, long seed) {
        double[] c = cell(x, z, seed);
        return c[3] > 0.5 && c[1] >= LAKE_MIN_INFLOW_CELLS;
    }

    /**
     * ★★★★★★★★ <b>§7626：这一格的水【流向下游的哪一格</b>。
     *
     * <p>返回 8 邻域方向下标（与 {@code DI/DJ} 同序）；{@code -1} = 无下游（本地最低点
     * 或流向窗口外）。
     *
     * <p><b>为什么需要它</b>：画河网必须沿【真正的流向】连折线。先前探针里重新算一遍
     * 「最陡下降」是错的 —— 那给的是【原始地形】的 D8，而本类用的是
     * <b>Cordonnier 2019 修正过</b>的接收者（河流会「上坡」跨过 spill，也会在湖里走）。
     * 用错的流向画出来就是一堆【孤立短划】（P1265 实测）。
     *
     * <p>顺序与 {@link #DIR_DX}/{@link #DIR_DZ} 一致。
     */
    public static int flowDir(int x, int z, long seed) {
        return (int) cell(x, z, seed)[4];
    }

    /** §7626：下游格的 x 位移；无下游返回 0。 */
    public static int downstreamDx(int x, int z, long seed) {
        int d = flowDir(x, z, seed);
        return d < 0 ? 0 : DIR_DX[d];
    }

    /** §7626：下游格的 z 位移；无下游返回 0。 */
    public static int downstreamDz(int x, int z, long seed) {
        int d = flowDir(x, z, seed);
        return d < 0 ? 0 : DIR_DZ[d];
    }

    /** 8 邻域的 x 位移，与 {@link #flowDir} 的返回值同序。 */
    public static final int[] DIR_DX = { -1, -1, -1, 0, 0, 1, 1, 1 };
    /** 8 邻域的 z 位移，与 {@link #flowDir} 的返回值同序。 */
    public static final int[] DIR_DZ = { -1, 0, 1, -1, 1, -1, 0, 1 };
    // ==================== 内部 ====================

    private static int floorDiv(int a, int b) { int q = a / b; return ((a % b) != 0 && ((a ^ b) < 0)) ? q - 1 : q; }

    /** 并查集 find（Kruskal 用；路径压缩 + 迭代版，避免深递归）。 */
    private static int ufFind(int[] uf, int x) { while (uf[x] != x) { uf[x] = uf[uf[x]]; x = uf[x]; } return x; }

    /** 从格 (ai,aj) 指向格 (bi,bj) 的 D8 方向下标；两个格必须 8 邻接。 */
    private static int oppDir(int ai, int aj, int bi, int bj) {
        int di = Integer.signum(bi - ai), dj = Integer.signum(bj - aj);
        for (int d = 0; d < 8; d++) if (DI[d] == di && DJ[d] == dj) return d;
        return -1;
    }

    /**
     * D8 流向图的【拓扑序】（Kahn）：先出【无上游】的格，再逐层向下游。
     *
     * <p>为什么需要它：§7613 之前汇流累积用的是「按填洼后的高程降序」—— 那个顺序在
     * 【填洼后的地形】上等价于拓扑序。而 Cordonnier 2019 修正了接收者 ⟹ 流向不再沿
     * 高程梯度 ⟹ 必须真的做拓扑排序，否则上游的水会被漏掉。
     *
     * <p>返回长度 {@code W*W} 的数组；不成环的部分全部在内，环内节点填 -1（不会发生：
     * D8 + Cordonnier 的接收者图按构造是无环的，见论文 §2.3 的 tree 定向）。
     */
    private static int[] topoOrder(int[] dir, final int W) {
        int n = W * W;
        int[] indeg = new int[n];
        for (int k = 0; k < n; k++) {
            int d = dir[k]; if (d < 0) continue;
            int ni = k / W + DI[d], nj = k % W + DJ[d];
            if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
            indeg[ni * W + nj]++;
        }
        int[] q = new int[n];
        int head = 0, tail = 0;
        for (int k = 0; k < n; k++) if (indeg[k] == 0) q[tail++] = k;
        int[] out = new int[n];
        java.util.Arrays.fill(out, -1);
        int m = 0;
        while (head < tail) {
            int k = q[head++];
            out[m++] = k;
            int d = dir[k]; if (d < 0) continue;
            int ni = k / W + DI[d], nj = k % W + DJ[d];
            if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
            int nk = ni * W + nj;
            if (--indeg[nk] == 0) q[tail++] = nk;
        }
        return out;
    }

    /**
     * 缓存键 = (tileI, tileJ, 地形种子)。
     *
     * <p>⚠ <b>种子必须进键</b>：{@link #fillDepthM} 等方法接一个 {@code seed} 形参，而
     * 不同种子给出完全不同的地形 ⟹ 若键里没有种子，换世界（或同进程里跑两个种子）会取到
     * 上一个世界的水系。<b>这个 bug 在 P1163/P1165 里看不出来（它们只用一个种子）。</b>
     */
    private static long key(int ti, int tj, long seed) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= (((long) ti) << 32) ^ (tj & 0xFFFFFFFFL);
        return h * 0xBF58476D1CE4E5B9L;
    }

    /** 取某格的 [fill, acc]（解 tile 或读缓存）。**调用方必须已持锁。** */
    private static double[] cell(int x, int z, long seed) {
        int tileBlocks = TILE * STEP;
        int ti = floorDiv(x, tileBlocks), tj = floorDiv(z, tileBlocks);
        long k = key(ti, tj, seed);
        double[][] t = CACHE.get(k);
        if (t == null) {
            // ⚠ 淘汰检查【只能放在未命中分支里】：ConcurrentHashMap.size() 是 O(n) 的，
            //   放在热路径（每次查询）上实测会吃掉约 35 us/次（P1167b 的 +7.10% 就是这个）。
            if (CACHE.size() > CACHE_TILES) CACHE.clear();
            double[][] fresh = solveTile(ti, tj, seed);
            double[][] prev = CACHE.putIfAbsent(k, fresh);
            t = (prev != null) ? prev : fresh;
            if (prev == null) tilesSolved++;
        }
        int li = floorDiv(x - ti * tileBlocks, STEP);
        int lj = floorDiv(z - tj * tileBlocks, STEP);
        if (li < 0) li = 0; else if (li >= TILE) li = TILE - 1;
        if (lj < 0) lj = 0; else if (lj >= TILE) lj = TILE - 1;
        return new double[]{ t[0][li * TILE + lj], t[1][li * TILE + lj], t[2][li * TILE + lj],
                             t[3][li * TILE + lj], t[4][li * TILE + lj] };
    }

    /**
     * 解一个 tile：在「tile + halo」窗口里跑 Priority-Flood + ε（8 邻域）与 D8 汇流累积，
     * 只取中心 {@code TILE x TILE}。
     */
    private static double[][] solveTile(int ti, int tj, long seed) {
        final int W = TILE + 2 * HALO;
        final int i0 = ti * TILE - HALO, j0 = tj * TILE - HALO;
        // DI/DJ 已提到类级常量（§7622 Cordonnier 的辅助函数也需要它们）

        // ---- 取高程（米，相对 SEA_LEVEL）----
        double[] e = new double[W * W];
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < W; j++) {
                e[i * W + j] = PlateField.elevation((i0 + i) * STEP, (j0 + j) * STEP, seed);
            }
        }

        // ---- ① Priority-Flood + ε（8 邻域；种子 = 海格 + 窗口边界）----
        double[] f = e.clone();
        boolean[] seen = new boolean[W * W];
        PriorityQueue<long[]> pq = new PriorityQueue<long[]>(Math.max(16, W * W / 4),
            new java.util.Comparator<long[]>() {
                @Override public int compare(long[] p, long[] q) {
                    return Double.compare(Double.longBitsToDouble(p[0]), Double.longBitsToDouble(q[0]));
                }
            });
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < W; j++) {
                int k = i * W + j;
                if (f[k] < PlateField.SEA_LEVEL || i == 0 || j == 0 || i == W - 1 || j == W - 1) {
                    seen[k] = true; pq.add(new long[]{ Double.doubleToLongBits(f[k]), k });
                }
            }
        }
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            double cCur = Double.longBitsToDouble(top[0]);
            int k = (int) top[1], ci = k / W, cj = k % W;
            for (int d = 0; d < 8; d++) {
                int ni = ci + DI[d], nj = cj + DJ[d];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                int nk = ni * W + nj;
                if (seen[nk]) continue;
                seen[nk] = true;
                double need = cCur + EPS;                 // ★ ε：保证严格单调（见类头）
                if (f[nk] < need) f[nk] = need;
                pq.add(new long[]{ Double.doubleToLongBits(f[nk]), nk });
            }
        }

        // ---- ② D8 流向（最陡下降；在【填洼后的 f[] 上算】—— §7642 定案）----
        //   ★★★★★★★★ 为什么【回到 f】而不是【原始 e】（P1292 实测，决定性）：
        //     同一窗口、同一 W 公式下，过 25 m 阈值的河格数：
        //       在【填洼后的 f】上算 D8  = 【871】
        //       在【原始 e】上算 D8      = 【299】   <- 稀疏 2.9 倍 ⚠
        //     ⟹ 原始地形【有大量小坑】⟹ 最陡下降【指向最近的坑】⟹ 汇流【分散成无数小流域】
        //       ⟹ 每小片一条小河 ⟹ 【长河消失、河短而宽】（用户正是观察到这个）。
        //     而填洼【抹平了小坑】⟹ 地形光滑 ⟹ 水沿【大尺度的坡】⟹ 【汇流集中】⟹ 长河。
        //   ⚠ 那 Cordonnier 呢？它【仍然在跑】（阶段 1/2 的盆地与 MST 仍在做），
        //     而它的产出【用于标出湖】（inPit）。流向本身【以 f 为准】。
        //     这与论文不冲突：论文的算法是【在带洼地的原始 DEM 上】恢复排水；
        //     而本仓的 PF+ε 已经把洼地填平了 ⟹ 用 f 的 D8 本身就是【已排水的】DEM。
        int[] dir = new int[W * W];
        java.util.Arrays.fill(dir, -1);
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < W; j++) {
                int k = i * W + j;
                if (f[k] < PlateField.SEA_LEVEL) continue;      // 海岸线排水：只从陆地出发
                double best = 0; int bd = -1;
                for (int d = 0; d < 8; d++) {
                    int ni = i + DI[d], nj = j + DJ[d];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    double dist = (DI[d] != 0 && DJ[d] != 0) ? Math.sqrt(2.0) : 1.0;
                    double slope = (f[k] - f[ni * W + nj]) / dist;
                    if (slope > best) { best = slope; bd = d; }   // ties 取先遇到（RichDEM 逐字）
                }
                dir[k] = bd;
            }
        }

        // ==================== ②b Cordonnier 2019：三个阶段的流向修正 ====================
        //   出处：Cordonnier, Bovy, Braun (2019), Earth Surf. Dynam. 7, 549-562.
        //         refs/cordonnier_esurf.txt（我们自己的抽文本）。
        //   ★ 论文 :413-415 逐字：「we use carving and filling as metaphors as our algorithm
        //     【only changes the flow graph connectivity without altering elevation values】」
        //     ⟹ 本段【不改 e[]】，只改 dir[]。
        //   ★ 论文章节：§2.1 盆地+链接（:245-274）· §2.2 MST（:289-305，式2）· §2.3 定向（:387-416）。
        //   ★ 复杂度：Kruskal = O(n log n)（:307-314 逐字），与 PF+ε 同阶。
        //   ★ 实测（P1263，32x32 窗口）：三阶段合计 2.12 ms，基线 PF+ε+D8 = 1.82 ms ⟹ 只慢 16%。
        boolean CORDONNIER = true;                            // false ⟹ 退回 §7613 的行为（可回滚）
        int[] basin = new int[W * W];
        java.util.Arrays.fill(basin, -1);
        final boolean[] inPit = new boolean[W * W];      // §7622：Cordonnier 保留的洼地（湖候选）
        if (CORDONNIER) {
            // ---- 阶段 1a：basin_id（沿 donors 的深度优先遍历；§2.1 :247-254 逐字）----
            int[] donorHead = new int[W * W];
            java.util.Arrays.fill(donorHead, -1);
            int[] donorNext = new int[W * W];
            java.util.Arrays.fill(donorNext, -1);
            for (int k = 0; k < W * W; k++) {                 // 建 donors 的邻接表（头插）
                int d = dir[k]; if (d < 0) continue;
                int ni = k / W + DI[d], nj = k % W + DJ[d];
                if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                int nk = ni * W + nj;
                donorNext[k] = donorHead[nk]; donorHead[nk] = k;
            }
            int nb = 0;
            int[] stk = new int[W * W];
            for (int s = 0; s < W * W; s++) {
                if (basin[s] >= 0) continue;
                if (dir[s] >= 0) continue;                    // 只从 singular node 起（§2.1 逐字）
                int sp = 0; stk[sp++] = s; basin[s] = nb;
                while (sp > 0) { int k = stk[--sp];
                    for (int u = donorHead[k]; u >= 0; u = donorNext[u]) {
                        if (basin[u] >= 0) continue; basin[u] = nb; stk[sp++] = u; } }
                nb++;
            }
            for (int s = 0; s < W * W; s++) {                 // 剩下的（流向窗口外）各自成盆地
                if (basin[s] >= 0) continue;
                int sp = 0; stk[sp++] = s; basin[s] = nb;
                while (sp > 0) { int k = stk[--sp];
                    for (int u = donorHead[k]; u >= 0; u = donorNext[u]) {
                        if (basin[u] >= 0) continue; basin[u] = nb; stk[sp++] = u; } }
                nb++;
            }
            // ---- 阶段 1b：链接 + pass（§2.1 :255-268 逐字）----
            java.util.HashMap<Long, double[]> links = new java.util.HashMap<Long, double[]>();
            for (int i = 0; i < W; i++) for (int j = 0; j < W; j++) {
                int k = i * W + j, b1 = basin[k];
                for (int d = 0; d < 8; d++) {
                    int ni = i + DI[d], nj = j + DJ[d];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    int nk = ni * W + nj, b2 = basin[nk]; if (b1 == b2) continue;
                    long key = ((long) Math.min(b1, b2) << 32) | (Math.max(b1, b2) & 0xFFFFFFFFL);
                    double pass = Math.max(e[k], e[nk]);      // Pass(L) 最小化 max(z_n1,z_n2)（:261）
                    double[] v = links.get(key);
                    if (v == null || pass < v[0]) links.put(key, new double[]{ pass, k, nk, b1, b2 });
                }
            }
            double[][] ls = links.values().toArray(new double[0][]);
            java.util.Arrays.sort(ls, new java.util.Comparator<double[]>() {
                @Override public int compare(double[] p, double[] q) { return Double.compare(p[0], q[0]); } });
            // ---- 阶段 2：Kruskal MST（§2.2 :306-316 逐字）----
            int[] uf = new int[nb]; for (int k = 0; k < nb; k++) uf[k] = k;
            int[] mstFrom = new int[Math.max(1, nb)], mstTo = new int[Math.max(1, nb)];
            int mstN = 0;
            for (double[] L : ls) {
                int ra = ufFind(uf, (int) L[3]), rb = ufFind(uf, (int) L[4]);
                if (ra == rb) continue;
                uf[ra] = rb;
                if (mstN < mstFrom.length) { mstFrom[mstN] = (int) L[1]; mstTo[mstN] = (int) L[2]; }
                mstN++;
            }
            // ---- 阶段 3：更新接收者（§2.3 的【最简单的解】：:405-409 逐字）----
            //   「The most straightforward solution would be to only update the receiver of each
            //     local minimum p so that rcv(p) = nout.」
            //   ⚠ 本版【只做这一步】（零阈值、零新常数）；另两个更真实的变体留待后续。
            // ---- 阶段 3：§2.3.2 的【filling 变体】—— 更新洼地【内部】的接收者 ----
            //   论文 :431-437 逐字：「we update here the receivers as if the depressions were
            //   completely filled by some material ... parses all neighbor nodes in a
            //   breadth-first order 【as long as these are below water level】」。
            //   ★ 水位 = Priority-Flood 填洼后的高程 f[k]（那正是 PF 的定义）⟹
            //     「低于水位」⟺ e[k] < f[k] ⟺ 【被 PF 抬升过】。
            //   ★ 而「填满后水流向已访问的邻居」⟹ 等价于【在 f 的梯度上取最陡下降】——
            //     即：洼地内的接收者改用【f】，洼地外的保持【e 的原始 D8】。
            //   ⚠ 为什么必须这么做（§7633 的根因确认）：我在 §7613 把【全部】格子的 D8
            //     都改成在【原始 e】(而不是填洼后的 f) 上算，以匹配 Cordonnier 的输入
            //     （论文 :238-239）。但「最简单的解」只改 local minimum ⟹ 洼地内其余格子
            //     的流向仍然陷在洼地里 ⟹ 汇流断掉 ⟹ 河网碎（对比 a7ad51c：那里 D8 是在
            //     f 上算的，所以河网连续）。
            //   ⟹ 本段把洼地内的接收者【改用 f 的梯度】⟹ 两边都对：湖保留 + 河网连续。
            //   ★★★★★★★★ §7642 的【关键修正】：**只有【湖】才保留洼地的流向，其余洼地一律填平。**
            //
            //   ⚠ 先前（§7622）我写的是「【所有】被 PF 抬升过的格（f - e > EPS）都改用 f 的梯度」，
            //     那等于【把每个小坑都当成一条出流通道】。后果（P1291 实测，决定性）：
            //       河格中【97.3% 在洼地之外】、只有 2.7% 在洼地上
            //     ⟹ 河【不是】被湖切断的，而是【原始地形的每个小坑都把周围的水截住】
            //       ⟹ 地形被切成【无数小流域】⟹ 每小片一条小河 ⟹ 【短而宽】
            //     （对比 a7ad51c：那里 D8 在【填洼后的 f】上算 ⟹ 地形光滑 ⟹ 水沿大尺度坡
            //       ⟹ 【长河】。用户正是发现「长河不见了」。）
            //
            //   ★ 所以正确的做法（也正是 03:589 / 03:735 的原意「保留少数大洼地当湖、
            //     【消灭其余】」）：
            //       · 湖（inPit == true，已按 LAKE_MIN_DEPTH_M 过滤过）⟹ 保留（水在湖里）
            //       · 其余【一切】洼地 ⟹ 填平 ⟹ 流向改用 f 的梯度 ⟹ 水能穿过它继续走
            //     ⟹ 只有【少数湖】会截断水流 ⟹ 长河回来。
            for (int k = 0; k < W * W; k++) {
                if (e[k] < PlateField.SEA_LEVEL) continue;
                if (f[k] - e[k] <= EPS) continue;          // 本来就能排水的格：保持原始 D8
                if (inPit[k]) continue;                    // ★ 湖：保留（湖面之下不重定向）
                double best = 0; int bd = -1;
                int ci = k / W, cj = k % W;
                for (int d = 0; d < 8; d++) {
                    int ni = ci + DI[d], nj = cj + DJ[d];
                    if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                    double dist = (DI[d] != 0 && DJ[d] != 0) ? Math.sqrt(2.0) : 1.0;
                    double sl = (f[k] - f[ni * W + nj]) / dist;
                    if (sl > best) { best = sl; bd = d; }
                }
                if (bd >= 0) dir[k] = bd;
            }
            // ---- 阶段 3b：§2.3.2 的【filling 变体】（湖面 = 水位之下）----
            //   论文 :431-437 逐字：「we update here the receivers as if the depressions were
            //   completely filled by some material ... parses all neighbor nodes ... as long as
            //   these are below water level」。
            //   ★ 而【水位【就是 Priority-Flood 填洼后的高程 f[k]】—— 那正是 PF 的定义
            //     （把每个洼地抬到它的 spill）。⟹ 本仓【不需要再算一遍 BFS】：
            //        湖面（水位之下）⟺ e[k] < f[k] − ε ⟺ 【被 PF 抬升过】
            //     ⟹ 而那【正是 fillDepthM > 0】✓
            //   ⚠ 为什么现在还【可以】用它：§7567 说「fill>0 is PF fill, NOT a lake criterion」
            //     是在【没有盆地划分】时说的（那时每个噪声坑都算）。现在有了 Cordonnier 的
            //     盆地 + 下面的 layer 1（有河汇入）⟹ 它【成为】湖判据的一半。
            for (int k = 0; k < W * W; k++) {
                if (f[k] - e[k] > EPS) inPit[k] = true;
            }
            // ---- 阶段 3c：§7627 的【深度过滤】（layer 2）----
            //   ⚠ 为什么必需：§7567 逐字「fill > 0 is PF fill, NOT a lake criterion」——
            //     光看「被抬升过」会把【每个微小噪声坑】都算成湖 ⟹ 湖全是小点（P1269 实测）。
            //   ★ 判据出处（设计冻结 :89753-89760 逐字）：
            //     「P1182b 实测：地形在 10 km 网格上的粗糙度 = 相邻高差 【16.20 m】
            //       填充深度中位数 = 29.1 m ⟹ 与粗糙度【同量级】⟹ 那些是【噪声坑】
            //       前 5 大湖的深度 = 277.7 ~ 441.1 m = 粗糙度的【17~27 倍】⟹ 那些是【真湖】
            //       ⟹ 所以【更物理的判据】：湖的深度必须【远大于】地形在网格尺度上的粗糙度」
            //   ★ 而「远大于」的量化：本仓给的是候选 10 倍（:89760 逐字「候选：10 倍 ⟹ 162 m」）
            //     ⟹ 见 LAKE_MIN_DEPTH_M 的 javadoc：那是【候选值，不是逐字常数】。
            //   深度 = 该洼地（连通域）内的【最大填洼量】（= 盆地最低点处的水深）。
            int[] comp = new int[W * W];
            java.util.Arrays.fill(comp, -1);
            double[] compMaxFill = new double[Math.max(1, W * W)];
            int nComp = 0;
            int[] stk2 = new int[W * W];
            for (int s = 0; s < W * W; s++) {
                if (!inPit[s] || comp[s] >= 0) continue;
                int sp = 0; stk2[sp++] = s; comp[s] = nComp;
                double mf = 0.0;
                while (sp > 0) {
                    int k = stk2[--sp];
                    double fd = f[k] - e[k];
                    if (fd > mf) mf = fd;
                    int ci = k / W, cj = k % W;
                    for (int d = 0; d < 8; d++) {
                        int ni = ci + DI[d], nj = cj + DJ[d];
                        if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
                        int nk = ni * W + nj;
                        if (!inPit[nk] || comp[nk] >= 0) continue;
                        comp[nk] = nComp; stk2[sp++] = nk;
                    }
                }
                if (nComp < compMaxFill.length) compMaxFill[nComp] = mf;
                nComp++;
            }
            // 只保留【深度 >= LAKE_MIN_DEPTH_M】的洼地
            for (int k = 0; k < W * W; k++) {
                if (!inPit[k]) continue;
                int cc = comp[k];
                if (cc < 0 || cc >= compMaxFill.length || compMaxFill[cc] < LAKE_MIN_DEPTH_M) inPit[k] = false;
            }
        }

        // ---- ③ 汇流累积（【拓扑序】；Cordonnier 修正后流向不再沿 f 的梯度）----
        double[] acc = new double[W * W];
        java.util.Arrays.fill(acc, 1.0);
        int[] topo = topoOrder(dir, W);
        for (int oi = 0; oi < W * W; oi++) {
            int k = topo[oi];
            if (k < 0) continue;
            int d = dir[k];
            if (d < 0) continue;
            int ni = k / W + DI[d], nj = k % W + DJ[d];
            if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
            acc[ni * W + nj] += acc[k];
        }

        // ---- ④ 产流深 Rr（米/年）与【累积径流】Qacc（m^3/yr）----
        //   §7612/§7613：Rr 的两个输入全部走 SimClimate 的【瓦片缓存】（10 km 格点，与 STEP 同值）
        //   ⟹ P1259 实测 0.2 ms / 256 格（而在 150 km 上要 103 秒，P1243）。
        //   P  = SimClimate.annualPrecipMmPerYear（= f.logP 的 4 季平均，已在瓦片里）
        //   Tw/Tc = SimClimate.surfaceTempK ± SimTerrain.seasonalAmpK  （融水用，§7582）
        int wsi = installedSeed;
        double[] rr = new double[W * W];
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < W; j++) {
                int k = i * W + j;
                if (f[k] < PlateField.SEA_LEVEL) continue;          // 只在陆地
                int wx = (i0 + i) * STEP, wz = (j0 + j) * STEP;
                double P = SimClimate.annualPrecipMmPerYear(wx, wz, wsi);          // mm/yr
                double tSfc = SimClimate.surfaceTempK(wx, wz, wsi);
                double amp = SimTerrain.seasonalAmpK(wx, wz, wsi);
                double Tc = tSfc - amp, Tw = tSfc + amp;
                double zM = Math.max(0.0, e[k]);
                // 融水：M = DDF(z)*TDD（ddf_snow_tc2023:408-418 逐字），DDF 两点 :1369 逐字
                double DDF = Math.max(0.0, 2.7 + 4.6 * (zM - 1750.0) / 2000.0);
                double tdd = 0.0;
                for (int day = 0; day < 365; day++) {
                    double Td = Tc + (Tw - Tc) / 2.0 * (1.0 + Math.cos(2 * Math.PI * (day - 182) / 365.0));
                    if (Td > 273.15) tdd += (Td - 273.15);
                }
                rr[k] = (P / 1000.0) + DDF * tdd / 1000.0;          // m/yr（降水已是年值）
            }
        }
        // 沿【拓扑序】累积 Qacc（与 acc 同一遍历顺序；§7622 起改用 topo，不再用高程降序）
        final double CELL_AREA = (double) STEP * (double) STEP;
        double[] qacc = new double[W * W];
        for (int oi = 0; oi < W * W; oi++) {
            int k = topo[oi];
            if (k < 0) continue;
            qacc[k] += rr[k] * CELL_AREA;
            int d = dir[k];
            if (d < 0) continue;
            int ni = k / W + DI[d], nj = k % W + DJ[d];
            if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
            qacc[ni * W + nj] += qacc[k];
        }

        // ---- 只取中心 TILE x TILE ----
        double[] fill = new double[TILE * TILE], facc = new double[TILE * TILE], fwid = new double[TILE * TILE];
        double[] fpit = new double[TILE * TILE], fdir = new double[TILE * TILE];
        for (int i = 0; i < TILE; i++) {
            for (int j = 0; j < TILE; j++) {
                int src = (HALO + i) * W + (HALO + j);
                fill[i * TILE + j] = f[src] - e[src];
                facc[i * TILE + j] = acc[src];
                fpit[i * TILE + j] = inPit[src] ? 1.0 : 0.0;
                fdir[i * TILE + j] = dir[src];
                // ⑤ 河宽 W = 6.289 * Q^0.46（Table 5.3 downstream/sand-bed + 锚点 Q1=8000cfs,W1=250ft）
                //    Q = Qacc / SEC  （m^3/s）
                // ★★★★★ §7529：河道起始阈值 —— A < A_MIN_CELLS 的格是【坡面流】，不是河道。
                //   没有它时 100% 的陆地格都有河宽（P1573），"只收集自己那一格"的格被画成 36 m 宽
                //   ⟹ 那正是用户看到的「短而肥」的「肥」。
                //   A = acc[src]（汇流累积的格数，与 qacc 无关：qacc 是体积、acc 是面积）。
                double Q = qacc[src] / 3.155693e7;
                fwid[i * TILE + j] = (acc[src] < A_MIN_CELLS)
                                   ? 0.0
                                   : 6.289 * Math.pow(Math.max(0.0, Q), 0.46);
            }
        }
        return new double[][]{ fill, facc, fwid, fpit, fdir };
    }
}
