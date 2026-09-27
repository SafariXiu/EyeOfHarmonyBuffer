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
    public static final int STEP = 10_000;

    /** 每 tile 的格数（一维）。tile = {@code TILE x TILE} 格。 */
    public static int TILE = 16;

    /**
     * 窗口向外扩张的 halo（格）。窗口边长 = {@code TILE + 2*HALO}。
     *
     * <p>P1162 实测：halo=0 有 5/256 格不一致（最大 103.8 m），<b>halo&gt;=4 起完全收敛</b>；
     * 取 8 = 2 倍余量。
     */
    public static int HALO = 8;

    /**
     * 平地抬升增量（米）。出处：调研 03 §6.2 逐字「Priority-Flood + ε … 用 <b>1/256 格的高度</b>」，
     * 而 1 格 = 1 米由 {@code TalosField} 类头逐字锁死。**不是可调旋钮**。
     */
    public static final double EPS = 1.0 / 256.0;

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
    // ==================== 内部 ====================

    private static int floorDiv(int a, int b) { int q = a / b; return ((a % b) != 0 && ((a ^ b) < 0)) ? q - 1 : q; }

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
        return new double[]{ t[0][li * TILE + lj], t[1][li * TILE + lj], t[2][li * TILE + lj] };
    }

    /**
     * 解一个 tile：在「tile + halo」窗口里跑 Priority-Flood + ε（8 邻域）与 D8 汇流累积，
     * 只取中心 {@code TILE x TILE}。
     */
    private static double[][] solveTile(int ti, int tj, long seed) {
        final int W = TILE + 2 * HALO;
        final int i0 = ti * TILE - HALO, j0 = tj * TILE - HALO;
        final int[] DI = { -1, -1, -1, 0, 0, 1, 1, 1 }, DJ = { -1, 0, 1, -1, 1, -1, 0, 1 };

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

        // ---- ② D8 流向（最陡下降；无邻居则不指派）----
        int[] dir = new int[W * W];
        java.util.Arrays.fill(dir, -1);
        for (int i = 0; i < W; i++) {
            for (int j = 0; j < W; j++) {
                int k = i * W + j;
                if (f[k] < PlateField.SEA_LEVEL) continue;      // ③ 海岸线排水：只从陆地出发
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

        // ---- ③ 汇流累积（按高程降序 = 上游先算）----
        double[] acc = new double[W * W];
        java.util.Arrays.fill(acc, 1.0);
        Integer[] order = new Integer[W * W];
        for (int k = 0; k < W * W; k++) order[k] = k;
        final double[] fRef = f;
        java.util.Arrays.sort(order, new java.util.Comparator<Integer>() {
            @Override public int compare(Integer a, Integer b) { return Double.compare(fRef[b], fRef[a]); }
        });
        for (int oi = 0; oi < W * W; oi++) {
            int k = order[oi], d = dir[k];
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
        // 沿 D8 树累积（按高程降序 = 上游先算；与 acc 同一遍历顺序）
        final double CELL_AREA = (double) STEP * (double) STEP;
        double[] qacc = new double[W * W];
        for (int oi = 0; oi < W * W; oi++) {
            int k = order[oi];
            if (rr[k] == 0.0 && f[k] < PlateField.SEA_LEVEL) continue;
            qacc[k] += rr[k] * CELL_AREA;
            int d = dir[k];
            if (d < 0) continue;
            int ni = k / W + DI[d], nj = k % W + DJ[d];
            if (ni < 0 || nj < 0 || ni >= W || nj >= W) continue;
            qacc[ni * W + nj] += qacc[k];
        }

        // ---- 只取中心 TILE x TILE ----
        double[] fill = new double[TILE * TILE], facc = new double[TILE * TILE], fwid = new double[TILE * TILE];
        for (int i = 0; i < TILE; i++) {
            for (int j = 0; j < TILE; j++) {
                int src = (HALO + i) * W + (HALO + j);
                fill[i * TILE + j] = f[src] - e[src];
                facc[i * TILE + j] = acc[src];
                // ⑤ 河宽 W = 6.289 * Q^0.46（Table 5.3 downstream/sand-bed + 锚点 Q1=8000cfs,W1=250ft）
                //    Q = Qacc / SEC  （m^3/s）
                double Q = qacc[src] / 3.155693e7;
                fwid[i * TILE + j] = 6.289 * Math.pow(Math.max(0.0, Q), 0.46);
            }
        }
        return new double[][]{ fill, facc, fwid };
    }
}
