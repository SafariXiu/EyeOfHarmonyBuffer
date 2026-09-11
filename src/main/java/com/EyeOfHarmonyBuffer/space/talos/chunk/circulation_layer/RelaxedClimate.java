package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * M5+M6 核心：**按绝对坐标窗口**离线松弛求解的气候场（按 世界种子 + 瓦片 缓存，运行时查表插值）。
 *
 * 物理：在 240×240 的粗网格上做**嵌套定点迭代**。一个窗口 = 暴露区 TILE_X(100km) × Z_CYCLE(1M)
 * 两侧再各加 HALO_X(100km) / HALO_Z_ROWS(20 行) 的 halo ⇒ 数组域 **300km × 1.2M**，
 * 格距 CELL_X=1250 / CELL_Z=5000（不再是"2k/格"）。世界不是环面：X 无限、Z 只有气候按 1M 重复。
 *   内层1：固定流场下把海温收敛到"逆流输运 + 向 seaTeq 弛豫"的不动点；
 *   内层2：同流场下把**空气温度/湿度/海洋性**收敛（海上目标=已解海温，陆上目标=landTeq/干平衡）；
 *   外层：用新海温更新气压（暖池低压/冷舌高压，增益 2.2）→ 地转风 + 摩擦
 *         （风速欠弛豫 0.5 抑制追逐）→ 埃克曼转向 + 岸墙折射的洋流。
 * 产出：副热带环流圈、西岸暖/东岸冷、寒舌暖池、迎风岸湿舌等由耦合自身涌现。
 *
 * 查询（全部双线性 O(1)≈100ns）：samplePressure/sampleWind/sampleCurrent/sampleSst/
 * sampleHumidity/sampleAirTemp/sampleMaritime。**首次访问某个 (种子, tileX, tileZ) 会触发一次求解，
 * 实测 5.5~7.6 s/窗口（P203），且每跨一个 100km 瓦片就要解一次** —— 不是"每种子一次性 5s"。
 * 确定性：固定迭代上限 + 阈值早停。
 */
public final class RelaxedClimate {

    private RelaxedClimate() {}

    /** 网格分辨率（block/格）。 */
    public static final int CELL_X = 1250;
    /** Z 方向网格（block/格）：Z_CYCLE / CELL_Z = 1,000,000 / 5,000 = 200 行（纬度周期行数）。 */
    public static final int CELL_Z = 5000;
    /** 外层次数（可调：收敛诊断/预热预算用）。 */
    public static int OUTER = 16;
    /** 诊断：最近一次求解的外层次数 / 末次 SST 残差（收敛判据 5e-4）。 */
    public static int lastOuterIters;
    public static double lastSstResidual;
    /** 风场欠松弛系数（耦合环增益≈1 → 需要欠松弛；探针 P104 扫参定值）。 */
    public static double WIND_BLEND = 1.0;
    /** 海洋热惯性：每轮外层 sstP 向 sst 靠近的比例（1=无惯性；探针 P105 扫参定值）。 */
    public static double SST_INERTIA = 1.0;
    /** 诊断：最近一次外层迭代后 SST 场相对上一轮外层的 RMS/最大变化。 */
    public static double lastOuterDelta, lastOuterDeltaMax;
    /** 诊断：同一次外层里流场 fu/fv 的 RMS/最大变化，以及 ΔSST 的按行直方图（10 桶）。 */
    public static double lastFlowDelta, lastFlowDeltaMax;
    public static final int[] outerRowHist = new int[10];
    /** 诊断：最近一次 advectSst 的最大 |Δ|、>1e-3 的格数、海格总数、3 个采样值。 */
    public static double DIAG_MAX, DIAG_A, DIAG_B, DIAG_C;
    public static int DIAG_BIG, DIAG_N;
    /** 内层海温/空气迭代上限（可调：收敛诊断用，见 P102/P103）。 */
    public static int INNER = 30;
    public static int AIR_INNER = 14;
    /** 诊断：最后一次外层迭代内层残差序列（长度 = 实际执行的内层次数）。 */
    public static final double[] innerTrace = new double[4096];
    /** 诊断：同一步的最大 |Δ|、超阈值格数、以及 3 个采样格的 sst 值。 */
    public static final double[] traceMax = new double[4096];
    public static final int[] traceCnt = new int[4096];
    public static final double[] traceCell = new double[4096 * 3];
    public static int innerTraceLen;
    /** 海温→气压的耦合增益（外层耦合环增益；P106 扫参找收敛域）。 */
    public static double SST_P_GAIN = 0.5;
    /**
     * 气压场平滑遍数（5 点滤波）。
     *
     * 地转风取的是 ∇p，而 SST 平流会在 2 格（4 km）尺度上积累噪声 → 气压出现格点噪声 →
     * 风向在相邻格点间乱翻 → 下一轮 SST 又变。实测流场相邻两轮外层的 Δrms 高达 2.5、
     * Δmax 10.8（P110），远超 SST 本身的信号。真实的地转平衡取的是天气尺度梯度，
     * 所以对 p 做轻微平滑是物理上正确的做法，不是数值遮丑。
     */
    public static int P_SMOOTH_PASSES = 4;
    /**
     * 定常行星波振幅（λ=140 km，随 sin(πb) 在赤道/极地归零）。
     *
     * 它是**固定场**，决定风向的二维结构，不引入任何反馈增益。取值要与纬向廓线 p0z 的
     * 经向梯度相称：p0z 的 |dp/dz| ≈ 3.6e-6/格，本项 ≈ 2·A/140000，取 A=0.09 得 1.3e-6，
     * 比值 2.8 → 风向可偏离纯纬向 ~20°，既不是条带也不是乱流。
     * （纬度循环从 200k 提到 4M 后 p0z 的梯度小了 20 倍，原来的 0.30 反而反超 3 倍，
     * 实测洋流 RMS(fx):RMS(fz) 从 6.5 掉到 1.5、风向变成以经向为主——所以必须一起重标定。）
     */
    public static double WAVE_AMP = 0.03;
    /** 保留占位：旧的各向同性近岸阻尼已删除（它把西边界流一起掐掉）。 */
    /**
     * 风向是否只由**解析热力强迫气压场**决定（SST 距平只调风速、不调风向）。
     *
     * 耦合环不收敛的根源是"风向 ← 瞬时 ∇p(含 SST 距平)"这条反馈：SST 距平 0.2 就能翻动
     * 气压梯度方向，于是下一轮平流路径整个换向、SST 再变 0.2，周而复始（P104~P111 实测：
     * 卸掉任何单一阻尼都压不住，Δflow 高达 2.5）。真实大气里，行星波/信风/西风带由**气候态
     * 热力强迫**（海陆热力差 + 纬向廓线）设定，SST 距平主要调**强度**与低层辐合，不重定风向。
     * 打开后风向取自解析场（常数），反馈只剩"强度 → 平流量 → SST"，环增益大幅下降。
     */
    public static boolean FIXED_WIND_DIR = true;
    /**
     * 定风向用的气压里，SST 距平占的权重：{@code pDir = pf + W·(p − pf)}。
     *
     * W=0 时风向完全由解析热力场定（收敛最好，但洋流退化成纯纬向条带、没有涡旋与西边界流）；
     * W=1 时等价于旧的完全耦合（结构丰富但外层不收敛、窗口之间对不上）。
     * 取中间值是为了同时保住"有结构"和"可收敛"。
     */
    public static double DIR_ANOM_WEIGHT = 0.6;
    /**
     * 距平进入定风向场之前的**天气尺度平滑半径**（blocks）。
     *
     * 当初风向完全耦合时环增益 > 1（外层 Δrms 0.2 不收敛），根因是 SST 在 **2~4 km** 尺度上
     * 有格点噪声，乘 2.2 的增益后梯度反超纬向廓线，风向在相邻格点间乱翻（实测相邻 2 km
     * 风向转角 mean 55°、p99 177°）。真实大气响应的是**天气尺度以上**的海温型，
     * 所以这里先把距平按本半径平滑掉小尺度，再参与定方向：大尺度暖池/冷池仍在，
     * 格点噪声被挡在门外。取 0 等价于旧的"完全耦合"（不收敛），取正值才可用。
     */
    public static double SYNOPTIC_KM = 75_000.0;
    /**
     * 定风向场里纬向廓线 p0z 的放大系数。
     *
     * 纬度循环 ×20 后 p0z 的经向梯度也随之小了 20 倍，而定常波与陆地热力差两项的梯度
     * 与纬度尺度无关 → 风从"纬向占优"翻成"各向同性"（实测 RMS(u)/RMS(v)=1.36，洋流 1.09）。
     * 只放大 p0z 就够：目标比值 3~4（风向可偏离纯纬向 15~20°）。
     * 注意这是**定风向专用场**，不影响对外输出的 samplePressure。
     */
    public static double PF_P0_SCALE = 3.0;
    /**
     * 风矢量朝"下坡方向"（−∇p）偏转的角度（弧度）。
     *
     * 这是**各向异性的天花板**：把单位地转风矢量朝与之垂直的下坡方向转 a，纯纬向的风
     * 就变成 tan(a) 的南北分量 → 比值 = 1/tan(a)。原来的 0.42 rad 直接把它锁死在 2.25，
     * 无论 pf 的梯度比做到多高都没用（P127 实测：pf 纯海格的 |dp/dz|/|dp/dx| 已经是 32:1，
     * 但风速比只有 2.13 —— 全部损失在这一步）。取 0.25 rad → 天花板 4.0。
     */
    public static double WIND_TURN = 0.25;
    /**
     * 风速整体倍率。纬度循环 ×20 后 p0z 的经向梯度被摊薄 20 倍，风速从 ~1.9 掉到 ~0.06，
     * 洋流图（速度→明度）因此几乎没有明暗对比。这里只放大**量级**，方向由单位矢量决定、
     * 完全不受影响；ClimateCoords 只用单位化的风向（wind/|wind|），所以群系/气候不变。
     * 配套：绝对速度量级的常量必须与风速量级同步（COAST_WBC_GAIN 直接是绝对幅度）；
     * 沿岸暖舌的绝对幅度不变。
     */
    public static double WIND_SCALE = 17.0;
    /**
     * 定风向场 pf 的平滑遍数（5 点滤波）。
     *
     * pf 里的"陆地热力差"项在海岸处是**硬跳变**（陆格上有 −1.5·(landTeq−sstRef)，海格上是 0），
     * 一格之内跳 0.6 → 梯度 6e-5，乘上 scale 后局部风速可达平均值的 11 倍（实测 max|fz|=10.95）。
     * 地转平衡本来只在天气尺度成立，在 5 km 的海岸线上不该出现这种急流，所以先平滑再取方向。
     */
    public static int PF_SMOOTH_PASSES = 2;
    /** 海温平流的回溯步数（每步 6 km；决定"洋流能把多远的海温搬过来"= 耦合强度）。 */
    public static int SST_TRACE_STEPS = 14;
    /**
     * 回溯速度参考值：每步位移 = dt · |F|/(|F|+REF)。
     *
     * 旧写法是 dt · (F/|F|)（把流场归一化成单位方向），于是**在流速趋零处（赤道无科氏、
     * 气压极值处 ∇p≈0）方向完全由噪声决定**，风场一丝变化就让回溯点跳 180°、SST 跳 ~0.1。
     * 外层耦合环因此永远不收敛（实测 Δrms 恒在 0.13~0.19，与欠松弛系数几乎无关）。
     * 换成饱和式后位移对 F 处处光滑，且弱流自然少搬运（更物理）。
     */
    public static double ADVECT_SPEED_REF = 0.05;
    private static final double INNER_TOL = 5.0e-4;

    private static final ConcurrentHashMap<Long, ClimateGridData> CACHE =
        new ConcurrentHashMap<Long, ClimateGridData>();

    /** 求解并行开关（probe 可关掉做串/并行一致性对比）。 */
    public static volatile boolean PARALLEL = true;
    /** 专用求解池：不占用 ForkJoin commonPool（MC 主线程/其他 mod 也在用）。 */
    private static final java.util.concurrent.ForkJoinPool POOL =
        new java.util.concurrent.ForkJoinPool(Math.max(1, Runtime.getRuntime().availableProcessors() - 1));

    /**
     * 按行并行执行：本类所有内层通量循环都以"行"为独立单元——
     * 写目标行互不重叠、跨行读只读旧场，故切分到多核**不改变任何数值**（确定性）。
     * 行数太少时不值得付并行开销，直接串行。
     *
     * 在 POOL 的任务里跑 parallel stream，stream 会复用当前 FJP（而不是 commonPool），
     * 于是后台预热线程与前台生成线程共享同一个有界池，不会互相打爆 CPU。
     */
    private static void rows(int ny, java.util.function.IntConsumer body) {
        if (!PARALLEL || POOL.getParallelism() <= 1 || ny < 16) {
            for (int iy = 0; iy < ny; iy++) {
                body.accept(iy);
            }
            return;
        }
        POOL.submit(() -> java.util.stream.IntStream.range(0, ny).parallel().forEach(body)).join();
    }

    // ================= 后台预热 =================

    /** 预热开关：解完一个窗口后，在后台把左右邻居也解掉，玩家跨窗口时不再卡顿。 */
    public static volatile boolean PREHEAT = true;

    /**
     * 预热队列上限（背压）。预热是**尽力而为**：多世界/多维度同时请求时宁可不预热，
     * 也不能让任务无限堆积（旧写法是 newSingleThreadExecutor 的无界队列）。
     */
    private static final int PREHEAT_QUEUE_MAX = 24;

    private static final java.util.concurrent.ThreadPoolExecutor HEATER =
        new java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(PREHEAT_QUEUE_MAX),
            r -> {
                Thread t = new Thread(r, "TalosClimatePreheat");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

    /** 最近一次前台访问的世界种子：预热的"过期世界"守卫（换世界/维度时丢弃旧任务）。 */
    private static volatile int ACTIVE_SEED;

    /** 诊断：因背压/过期被丢弃的预热请求数。 */
    public static volatile int preheatDropped;

    /** 诊断：预热队列是否已排空（探针用；生产不读）。 */
    public static boolean preheatIdle() {
        return PENDING.isEmpty() && HEATER.getActiveCount() == 0 && HEATER.getQueue().isEmpty();
    }

    private static final java.util.Set<Long> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 诊断：累计预热完成的窗口数 / 最近一次预热耗时(ms)。 */
    public static volatile int preheated;
    public static volatile long lastPreheatMs;

    /**
     * 预热半径（窗口数）。1 只够玩家正常步行；快速移动、传送、或斜向跨越时来不及，
     * 前台就会撞上未就绪的窗口而卡一次完整构建。半径 2 时同时活跃 5 个窗口，
     * 因此 CACHE_LIMIT 必须 >= 2*半径+1，否则逐出策略会把刚预热好的邻居踢掉。
     */
    public static int PREHEAT_RADIUS = 2;

    /**
     * Z 方向预热半径（Z 瓦片）—— **现行值是 1**（I 轮调过；下面论证写的是 0 的取舍，保留作为记录）：
     * Z 瓦片高 1M（= 10 个 X 瓦片），跨一次很罕见，
     * 每次预热都要多解几个 12s 的窗口，不划算；真正需要 Z 邻居时（每瓦片最后一格取 halo 行）
     * 按需求解即可，代价与"没预热到的 X 邻居"完全一样。
     */
    public static int PREHEAT_RADIUS_Z = 1;

    private static void warm(int worldSeedInt, int tileX, int tileZ) {
        long key = cacheKey(worldSeedInt, tileX, tileZ);
        if (CACHE.containsKey(key) || !PENDING.add(key)) {
            return;
        }
        try {
            HEATER.execute(() -> {
                try {
                    if (worldSeedInt != ACTIVE_SEED) {
                        return;                     // 已换世界/维度：这次预热没意义，直接丢
                    }
                    long t0 = System.nanoTime();
                    CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tileX, tileZ));
                    lastPreheatMs = (System.nanoTime() - t0) / 1_000_000L;
                    preheated++;
                } catch (Throwable ignored) {
                    // 预热失败不影响前台：下次真正访问时会重解
                } finally {
                    PENDING.remove(key);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException queueFull) {
            // 背压：队列满 → 丢弃本次预热（前台访问时按需构建，行为与"没预热到"一致）
            PENDING.remove(key);
            preheatDropped++;
        }
    }

    /**
     * 窗口缓存上限（个）。**9 → 16 是为了让工作集装得下**（零行为变化的容量修复）。
     *
     * 体积不再手写：见 {@link #LAST_WINDOW_BYTES} —— 构造器按**真实分配**累加得出
     * （旧注释写的 "每个 ≈1.3 MB" 与实际差 10 倍，就是这么漂掉的）。
     * 240×240 的窗口约 **14.8 MB** ⇒ 9 个 ≈133 MB、16 个 ≈237 MB。
     *
     * 16 的依据（实测 P203/P207/P208）：
     *   · 一个 LandformField/气候窗口在 X 上要被 TILE_BLEND 拉进 ±1 个瓦片 ⇒ 单行工作集 5 个；
     *   · Z 方向有 3 个瓦片（halo 行落在相邻瓦片上）⇒ 最坏 5×3 = **15**；
     *   · 16 = 15 + 1 余量。
     * 实测：工作集 10 个窗时重扫一遍要重解 ~2.2 次、14 个窗要重解 ~10.2 次（旧 CACHE_LIMIT=9）。
     */
    public static int CACHE_LIMIT = 16;

    /**
     * **诊断计数器（只加两个 long，不参与任何计算，零行为变化）**。
     *
     * 为什么要有：曾经三次"进程跑飞"（P140 8325s/27min、P190 ~100min、P191 >50min）全部出在
     * 「窗口构建 + 采样」这条链路上，而现场**没有任何计数**，只能事后猜。
     * 实测（P203）：单次窗口构建 5.5~7.6 s；工作集 8 个窗（< CACHE_LIMIT=9）重扫一遍 0.00 s、
     * 10 个窗要重解 ~2.2 次、14 个窗要重解 ~10.2 次。所以 BUILD_COUNT 一旦远超扫描规模，
     * 就说明缓存抖动在打转。
     */
    public static final java.util.concurrent.atomic.AtomicLong BUILD_COUNT =
        new java.util.concurrent.atomic.AtomicLong();
    /** 累计窗口构建耗时（ns）。 */
    public static final java.util.concurrent.atomic.AtomicLong BUILD_NANOS =
        new java.util.concurrent.atomic.AtomicLong();
    /** 每构建这么多次就打一行警告（0 = 不打）。生产默认 0，不影响任何输出。 */
    public static int BUILD_WARN_EVERY = 0;

    /**
     * **窗口构建硬上限**（安全阀，不是优化）。
     *
     * 0 = 不限（生产默认，行为与以前逐位一致）。>0 时构建次数超过它就直接抛
     * {@link IllegalStateException}，把计数、缓存占用、当前 tile 一起报出来。
     * 用途：探针/命令行工具做广域扫描时，宁可**快速失败**也不要像 P140/P190/P191 那样
     * 闷跑几十分钟。生产不要设它。
     */
    public static int BUILD_HARD_CAP = 0;

    /** 诊断计数器（零行为变化）：窗口缓存的命中/未命中次数 —— 用于算真实命中率。 */
    public static final java.util.concurrent.atomic.AtomicLong CACHE_HIT =
        new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong CACHE_MISS =
        new java.util.concurrent.atomic.AtomicLong();

    /** 命中率（0..1）；无访问时返回 NaN。 */
    public static double cacheHitRate() {
        long h = CACHE_HIT.get(), m = CACHE_MISS.get();
        return (h + m) == 0 ? Double.NaN : h / (double) (h + m);
    }

    /** 诊断：最近创建的一个窗口实际占用的堆字节数与格数（按真实分配自动累加，见 ClimateGridData 构造器）。 */
    public static volatile long LAST_WINDOW_BYTES;
    public static volatile int LAST_WINDOW_N;

    /** 清零诊断计数器（探针用）。 */
    public static void resetBuildStats() {
        BUILD_COUNT.set(0L);
        BUILD_NANOS.set(0L);
        CACHE_HIT.set(0L);
        CACHE_MISS.set(0L);
    }

    /**
     * 窗口缓存键。**唯一实现是 {@link WindowKey#of}**，本层不再自己拼位段。
     *
     * 旧写法 {@code ((long) seed << 40) ^ ((long)(tileX & 0xFFFFF) << 20) ^ ...} 把 int 种子
     * 左移 40 位，只有低 24 位留在 long 里 —— 于是 seed 与 seed+2^24 的**两个不同世界**
     * 共用同一批已解窗口（换种子后地形/气候不变）。同一个式子当时在
     * LandformField / MountainLayerV2 / V2BiomeField 里各抄了一份，是 4 处同源缺陷。
     */
    private static long cacheKey(int seed, int tileX, int tileZ) {
        return WindowKey.of(seed, tileX, tileZ);
    }

    /** 网格场数据。 */
    static final class ClimateGridData {
        /** 本窗口在**绝对世界坐标**里的 X 起点（窗口只是"解哪一段"，不改世界）。 */
        final int originX;
        /**
         * 本窗口在**绝对世界坐标**里的 Z 起点 = tileZ · Z_CYCLE。
         *
         * Z 方向也必须分瓦片：Z_CYCLE=1M 只控制**纬度**（bandD/latRad 都是 z mod 1M 的函数），
         * 而海陆/洋流在 Z 上**不重复**（契约：跨极点后是新大陆、新海盆）。
         * 旧实现把查询 z 折回 [0,1M)，于是 |z| ≥ 500k 处的 SST/风/洋流是用**另一套海陆**解出来的，
         * sampleSst 的 isLandCell 还会把真海面判成陆 → NaN → ClimateCoords 丢掉整条 SST 项。
         * 因为瓦片原点取 1M 的整数倍，纬度场在每个 Z 瓦片里完全相同，所以加 Z 瓦片几乎零代价。
         */
        final int originZ;
        /** 本窗口的 Z 瓦片索引（跨瓦片取 halo 行时要用）。 */
        final int tileZ;
        /** 含 halo 的 X 格数（暴露区 400k + 两侧各 HALO_X）。 */
        final int nx;
        /** 纬度周期行数（200）：一行的纬度 = (该行绝对 z) mod Z_CYCLE。 */
        final int nyLat = GlobalCirculation.Z_CYCLE / CELL_Z;
        /** Z halo 行数（上下各一份，构造时快照，避免求解中途被改）。 */
        final int haloZ = HALO_Z_ROWS;
        /** 数组行数 = nyLat + 2·haloZ。数组行 iy 的绝对 z = originZ + (iy - haloZ)·CELL_Z。 */
        final int ny = nyLat + 2 * haloZ;

        /** 数组行 → 纬度行（0..nyLat-1）：行 iy 与行 iy±nyLat 的纬度相同。 */
        int latOf(int iy) {
            int v = (iy - haloZ) % nyLat;
            return v < 0 ? v + nyLat : v;
        }

        ClimateGridData(int originX, int originZ, int tileZ, int nxPadded) {
            this.originX = originX;
            this.originZ = originZ;
            this.tileZ = tileZ;
            this.nx = nxPadded;
            // 数组必须在构造器里分配：nx 是构造参数，字段初始化器阶段还没赋值
            int n = nx * ny;
            // 字节数**按真实分配累加**（acc[0]），不再在手写注释里估算体积 ——
            // 旧注释写的 "每个 ≈1.3 MB" 与实际差了 10 倍，就是这么漂掉的。
            long[] acc = {0L};
            land = allocB(n, acc);
            sst = allocD(n, acc);
            teqSea = allocD(n, acc);
            teqLand = allocD(n, acc);
            qLandEq = allocD(n, acc);
            p = allocD(n, acc);
            u = allocD(n, acc);
            v = allocD(n, acc);
            fu = allocD(n, acc);
            fv = allocD(n, acc);
            tAir = allocD(n, acc);
            q = allocD(n, acc);
            mar = allocD(n, acc);
            pSm = allocD(n, acc);
            pf = allocD(n, acc);
            pDir = allocD(n, acc);
            anom = allocD(n, acc);
            anomT = allocD(n, acc);
            gPsi = allocD(n, acc);
            gZeta = allocD(n, acc);
            gSrc = allocD(n, acc);
            fPhys = allocD(ny, acc);
            betaRow = allocD(ny, acc);
            sPoleRow = allocD(ny, acc);
            wave = allocD(n, acc);
            pu = allocD(n, acc);
            pv = allocD(n, acc);
            sstNew = allocD(n, acc);
            lap = allocD(n, acc);
            fade = allocD(n, acc);
            coastD = allocD(n, acc);
            sstOld = allocD(n, acc);
            sstP = allocD(n, acc);
            tNew = allocD(n, acc);
            qNew = allocD(n, acc);
            marNew = allocD(n, acc);
            p0z = allocD(ny, acc);
            fRow = allocD(ny, acc);
            dampRow = allocD(ny, acc);
            rowSq = allocD(ny, acc);
            rowN = allocI(ny, acc);
            rowMx = allocD(ny, acc);
            rowBig = allocI(ny, acc);
            this.bytes = acc[0];
            LAST_WINDOW_BYTES = acc[0];
            LAST_WINDOW_N = n;
        }

        /** 按真实分配累加字节数的三个小助手（8B double / 4B int / 1B boolean）。 */
        private static double[] allocD(int len, long[] acc) { acc[0] += (long) len * 8L; return new double[len]; }
        private static int[] allocI(int len, long[] acc) { acc[0] += (long) len * 4L; return new int[len]; }
        private static boolean[] allocB(int len, long[] acc) { acc[0] += (long) len; return new boolean[len]; }

        /**
         * 最近一次被命中的时间（**只参与淘汰排序，不参与任何计算**）。
         * volatile 只为多线程下的可见性；无竞争写，热点代价可忽略。与 LandformField/MountainLayerV2 同款。
         */
        volatile long lastUse = System.nanoTime();

        /** 本窗口实际占用的堆字节数（构造时按真实分配累加，自动派生、不会漂）。 */
        final long bytes;

        final boolean[] land;
        final double[] sst;
        final double[] teqSea;
        final double[] teqLand;
        final double[] qLandEq;
        final double[] p;
        final double[] u;
        final double[] v;
        final double[] fu;
        final double[] fv;
        // 空气场（随耦合流场收敛）
        final double[] tAir;
        final double[] q;
        final double[] mar;
        final double[] p0z;
        final double[] pSm;
        /** 只由**解析热力强迫**构成的气压场（不含 SST 距平）：定风向的底。 */
        final double[] pf;
        /** 实际用来定风向的场：pf + DIR_ANOM_WEIGHT · smooth(距平)。 */
        final double[] pDir;
        final double[] anom;
        final double[] anomT;
        /** 风生环流求解器的工作数组（跨外层热启动，别每次清零）。 */
        final double[] gPsi;
        final double[] gZeta;
        final double[] gSrc;
        /** 逐行物理量：f = 2Ω·sin(纬)，β = df/dy。 */
        final double[] fPhys;
        final double[] betaRow;
        /** 半球符号（+1 北 / -1 南）：按**绝对 z** 的纬度带算，不再假设行号 0..ny-1 是一个周期。 */
        final double[] sPoleRow;
        final double[] wave;
        final double[] fRow;
        final double[] dampRow;
        // 中间态
        final double[] pu;
        final double[] pv;
        final double[] sstNew;
        /** 平滑(Jacobi)增量：并行时不能就地改 sstNew，先算增量再统一加回。 */
        final double[] lap;
        /** 海岸渐隐（1=开阔海，0=岸）：只给回溯用，让回溯缓缓停住而不是硬撞陆地。 */
        final double[] fade;
        /** 每格到最近陆地的距离（block，陆地为 0）：近岸约束与西边界流都用它，求解前算一次。 */
        final double[] coastD;
        final double[] rowSq;
        final int[] rowN;
        final double[] rowMx;
        final int[] rowBig;
        /** 上一轮迭代的 sst 快照：残差必须量"整步更新量"（含平滑），不能只量平流子步。 */
        final double[] sstOld;
        /** 喂给气压场的海温（海洋热惯性用；默认等同 sst）。 */
        final double[] sstP;
        final double[] tNew;
        final double[] qNew;
        final double[] marNew;

        /**
         * 数组索引。X 仍按瓦片环绕（HALO_X 的设计依赖它）；
         * **Z 不再环绕**（环就是接缝本身）：越界只 clamp 到 halo 两端。
         */
        int idx(int ix, int iy) {
            int wy = iy < 0 ? 0 : (iy >= ny ? ny - 1 : iy);
            return wy * nx + ((ix % nx + nx) % nx);
        }
    }

    // ================= 查询 API =================

    /** 预热原点窗口（X 瓦片 0 / Z 瓦片 0）。 */
    public static ClimateGridData ensure(int worldSeedInt) {
        return tileGrid(worldSeedInt, 0, 0);
    }

    /** 清空窗口缓存（世界卸载 / 探针扫参）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    // ================= C2：气候按 X 瓦片求解（世界沿 X 无限） =================

    /** 瓦片宽度（blocks）：沿用原域宽。 */
    public static final int TILE_X = 100_000;
    /** 交叉淡入带宽度（blocks），近邻窗口解差异的衰减长度就是它。 */
    public static int TILE_BLEND = 25_000;
    /**
     * 求解 halo（blocks）：每侧在暴露窗口外**多解**这么宽。
     *
     * 这是"窗口解不唯一"的根治办法。**历史**：早期窗口 X 方向是环面（bilinear/idx 都按当时的
     * 世界周期取模；那个周期常量现在是 PERIOD_X=100k），
     * 而内层平流的有效记忆长度 ~300 km（14 步 × 6 km，按 0.715^k 展开），几乎必然绕回原点；
     * 再叠加 applyCoastalSst 用"本窗口内的洋盆起点"当西边界，
     * 于是相邻窗口解出来的场**整体**差 0.4（P113 实测：跨边界 4 km 处 Δp=0.36，
     * 而窗口内部 4 km 处只有 0.0055 —— 不是"缝"而是两套完全不同的气候）。
     * 加一圈 ≥ 记忆长度的 halo 后，暴露区内的所有回溯路径都落在 halo 内部，
     * 不再碰到"窗口边缘"这个人为边界。
     */
    public static int HALO_X = 100_000;

    /**
     * **Z 方向 halo 行数（上下各一份）**。域 = nyLat + 2·HALO_Z_ROWS 行。
     *
     * 解决"环接缝"：原来求解域是 1M 纬度环（row ny-1 与 row 0 相连），而 E 之后这两行
     * 承载的是不同海陆（绝对 z 相差 ~995,000）→ 瓦片 Z 边缘十几到几十行被伪连接扰动。
     *
     * 关键洞察（本设计的依据）：纬度只是 z mod 1M 的函数，所以瓦片上方第 k 行的绝对 z
     * 落在 [originZ+1M, originZ+1M+H·CELL_Z)，其纬度**恰好等于瓦片内第 k 行的纬度**；
     * 下方同理。于是"加 Z halo"不需要改纬度表的语义：只要让域变成 nyLat+2H 行、
     * 把 halo 行的海陆/温度/定常波按**绝对 z** 采样（自然落到相邻 Z 瓦片的坐标上），
     * row ny-1 与 row 0 之间就不再是伪连接，而是真实相邻行。
     *
     * 取值由探针 P174 扫出来（判据：跨 z=n·1M 的相邻阶跃 < 场内基线 max|Δ|）。
     */
    public static int HALO_Z_ROWS = 20;

    // 场选择码（供 sampleBlended 复用同一套"瓦片 + 淡入"逻辑）
    private static final int F_P = 0, F_U = 1, F_V = 2, F_FU = 3, F_FV = 4,
        F_SST = 5, F_TAIR = 6, F_Q = 7, F_MAR = 8;

    private static double[] fieldOf(ClimateGridData d, int f) {
        switch (f) {
            case F_P: return d.p;
            case F_U: return d.u;
            case F_V: return d.v;
            case F_FU: return d.fu;
            case F_FV: return d.fv;
            case F_SST: return d.sst;
            case F_TAIR: return d.tAir;
            case F_Q: return d.q;
            default: return d.mar;
        }
    }

    /** 点所在瓦片索引。 */
    public static int tileOfX(int x) {
        return Math.floorDiv(x, TILE_X);
    }

    /**
     * 点所在的 **Z 瓦片**索引：瓦片高 = {@link GlobalCirculation#Z_CYCLE}（1M，只控制纬度）。
     * 与 X 瓦片不同，Z 瓦片只管"用哪一套海陆"，纬度场在每个 Z 瓦片里完全相同。
     */
    public static int tileOfZ(int z) {
        return Math.floorDiv(z, GlobalCirculation.Z_CYCLE);
    }

    /**
     * 取某窗口的解：**同一个世界种子 + 绝对坐标窗口**（不是"每瓦片换种子"）。
     * 换种子的写法会让每个窗口针对一套虚构海陆松弛，与真实地形对不上（已修正）。
     */
    private static ClimateGridData tileGrid(int worldSeedInt, int tileX, int tileZ) {
        ACTIVE_SEED = worldSeedInt;
        long key = cacheKey(worldSeedInt, tileX, tileZ);
        ClimateGridData d = CACHE.get(key);
        if (d != null) {
            CACHE_HIT.incrementAndGet();
            d.lastUse = System.nanoTime();       // 真 LRU：命中即刷新
            return d;
        }
        CACHE_MISS.incrementAndGet();
        // **真 LRU 逐出**（淘汰 lastUse 最小的那个），与 LandformField/MountainLayerV2 同款。
        //
        // 旧写法淘汰"离当前窗口最远"的，距离 = |Δtx|*(Z_CYCLE/TILE_X) + |Δtz| = |Δtx|*10 + |Δtz|。
        // 那个度量假定"Z 邻居比 X 邻居更该留"，可实际的扫描是**每一行都要来回用 X 邻居**，
        // 于是缓存一满就优先逐出每行都要用的 X 邻居、把过期的 Z 瓦片留着 ——
        // 实测 14 窗工作集时重扫一遍要重解 10.2 次，而理论下限只有 5 次，那 2 倍偏差就来自这里。
        // LRU 不需要任何关于访问模式的先验，自然解决。
        if (CACHE.size() >= CACHE_LIMIT) {
            Long worst = null;
            long oldest = Long.MAX_VALUE;
            for (java.util.Map.Entry<Long, ClimateGridData> e : CACHE.entrySet()) {
                if (e.getKey() == key) {
                    continue;
                }
                long t = e.getValue().lastUse;
                if (t < oldest) {
                    oldest = t;
                    worst = e.getKey();
                }
            }
            if (worst != null) {
                CACHE.remove(worst);
            }
        }
        final int tile = tileX, tile2 = tileZ;
        ClimateGridData solved = CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tile, tile2));
        if (PREHEAT) {
            for (int r = 1; r <= Math.max(1, PREHEAT_RADIUS); r++) {
                warm(worldSeedInt, tileX - r, tileZ);
                warm(worldSeedInt, tileX + r, tileZ);
            }
            // Z 方向：瓦片高 1M（= 10 个 X 瓦片），但传送/传送门会**瞬时**跨越 → 必须预热，
            // 否则主线程要等一次完整构建。代价权衡：跨一次 = 一次完整构建（~10s 前台卡顿），
            // 频率极低但代价极高 → 取半径 1（上下各一个），每次预热的额外成本 = 2 次后台构建。
            // 注意**只预热同 tileX 的 Z 邻居**（不带上它们的 X 邻居）：Z 邻居的 X 邻居等玩家真过去时
            // 再按 X 规则预热即可，这样常驻窗口数保持在 CACHE_LIMIT 以内、不会互相逐出。
            if (PREHEAT_RADIUS_Z > 0) {
                for (int r = 1; r <= PREHEAT_RADIUS_Z; r++) {
                    warm(worldSeedInt, tileX, tileZ - r);
                    warm(worldSeedInt, tileX, tileZ + r);
                }
            }
        }
        return solved;
    }

    /**
     * 取场值：瓦片查表，可选跨瓦片交叉淡入（{@link #TILE_BLEND}）。
     *
     * **现行值 25_000（25 km），淡入是开着的**（旧注释写"默认 TILE_BLEND = 0"，与常量不符，已改正）。
     *
     * 代价（实测 P203/P207/P208）：开了淡入，靠近瓦片 X 边界的采样要**再拉进 ±1 个瓦片**，
     * 单行工作集从 3 个窗涨到 5 个 —— 这是缓存抖动的一个放大因子。
     * 有了 {@link #HALO_X} 之后相邻窗口在重叠区其实已经解出同一个场（不再是两套气候），
     * 所以"是否真的需要这层淡入"值得单独评估；**改它属于行为变更（会改变瓦片边界附近的值），
     * 本轮只把注释改成与常量一致，没有动值。**
     */
    private static double sampleBlended(int worldSeedInt, int x, int z, int f) {
        int tile = tileOfX(x), tileZ = tileOfZ(z);
        double localX = x - (double) tile * TILE_X;
        double localZ = z - (double) tileZ * GlobalCirculation.Z_CYCLE;   // ∈ [0, 1M)：本瓦片的纬度周期
        ClimateGridData d = tileGrid(worldSeedInt, tile, tileZ);
        // 网格坐标 = 暴露区局部坐标 + halo 偏移
        double gx = localX + HALO_X;
        double v = bilinearSample(worldSeedInt, tile, tileZ, d, f, gx, localZ);
        if (TILE_BLEND > 0) {
            if (localX < TILE_BLEND) {
                double k = 0.5 - localX / (2.0 * TILE_BLEND);      // 边界 0.5 → 带内 0
                ClimateGridData dn = tileGrid(worldSeedInt, tile - 1, tileZ);
                double vn = bilinearSample(worldSeedInt, tile - 1, tileZ, dn, f, gx + TILE_X, localZ);
                v = v * (1.0 - k) + vn * k;
            } else if (localX > TILE_X - TILE_BLEND) {
                double k = 0.5 - (TILE_X - localX) / (2.0 * TILE_BLEND);
                ClimateGridData dn = tileGrid(worldSeedInt, tile + 1, tileZ);
                double vn = bilinearSample(worldSeedInt, tile + 1, tileZ, dn, f, gx - TILE_X, localZ);
                v = v * (1.0 - k) + vn * k;
            }
        }
        return v;
    }

    /**
     * 采样专用双线性：X 用网格内坐标（含 HALO_X，保持瓦片内环绕）；
     * **Z 用本瓦片内的 z ∈ [0, Z_CYCLE)，不取模**，行号 = z/CELL_Z + haloZ。
     *
     * 因为域上下各解了 HALO_Z_ROWS 行真实数据（halo 行按绝对 z 采样，纬度同瓦片内对应行），
     * 边界附近插值用的是**真实相邻行**，跨 z = n·1M 连续且域边缘不再有伪连接扰动。
     */
    private static double bilinearSample(int worldSeedInt, int tileX, int tileZ, ClimateGridData d,
                                         int f, double gx, double localZ) {
        double[] fld = fieldOf(d, f);
        if (localZ < 0) {
            localZ += GlobalCirculation.Z_CYCLE;
        }
        double gxi = gx / (double) CELL_X, gzi = localZ / (double) CELL_Z + d.haloZ;   // blocks → 格（含 halo 偏移）
        int ix0 = (int) Math.floor(gxi), iz0 = (int) Math.floor(gzi);
        double tx = gxi - ix0, tz = gzi - iz0;
        int ix1 = (ix0 + 1) % d.nx;
        if (iz0 < 0) {
            iz0 = 0;
            tz = 0.0;
        }
        int iz1 = iz0 + 1 >= d.ny ? d.ny - 1 : iz0 + 1;
        int base0 = iz0 * d.nx, base1 = iz1 * d.nx;
        double s00 = fld[base0 + ix0], s10 = fld[base0 + ix1];
        double s01 = fld[base1 + ix0], s11 = fld[base1 + ix1];
        return s00 * (1 - tx) * (1 - tz) + s10 * tx * (1 - tz)
             + s01 * (1 - tx) * tz + s11 * tx * tz;
    }



    public static double samplePressure(int x, int z, int worldSeedInt) {
        return sampleBlended(worldSeedInt, x, z, F_P);
    }

    public static double[] sampleWind(int x, int z, int worldSeedInt) {
        return new double[] {
            sampleBlended(worldSeedInt, x, z, F_U),
            sampleBlended(worldSeedInt, x, z, F_V)
        };
    }

    /** 洋流方向 [fx, fz]（临海陆格已幽灵填充；纯内陆≈0，调用方先判 isLand）。 */
    public static double[] sampleCurrent(int x, int z, int worldSeedInt) {
        return new double[] {
            sampleBlended(worldSeedInt, x, z, F_FU),
            sampleBlended(worldSeedInt, x, z, F_FV)
        };
    }

    /** 耦合海温（海上；陆上 NaN）。 */
    public static double sampleSst(int x, int z, int worldSeedInt) {
        int tile = tileOfX(x), tileZ = tileOfZ(z);
        ClimateGridData d = tileGrid(worldSeedInt, tile, tileZ);
        if (isLandCell(d, x - tile * TILE_X, z - tileZ * GlobalCirculation.Z_CYCLE)) {
            return Double.NaN;
        }
        return sampleBlended(worldSeedInt, x, z, F_SST);
    }

    /** 空气温度（全定义域）。 */
    public static double sampleAirTemp(int x, int z, int worldSeedInt) {
        return sampleBlended(worldSeedInt, x, z, F_TAIR);
    }

    /** 空气湿度 [0,1]（全定义域）。 */
    public static double sampleHumidity(int x, int z, int worldSeedInt) {
        double q = sampleBlended(worldSeedInt, x, z, F_Q);
        return q < 0 ? 0 : (q > 1 ? 1 : q);
    }

    /** 海洋性记忆 [0,1]（全定义域）。 */
    public static double sampleMaritime(int x, int z, int worldSeedInt) {
        return sampleBlended(worldSeedInt, x, z, F_MAR);
    }

    /**
     * 入参是**本瓦片内**的局部坐标：localX ∈ [0, TILE_X)、localZ ∈ [0, Z_CYCLE)。
     *
     * 掩码是按**绝对坐标** originX + ix·CELL_X 建的，所以下标必须自己补偏移，不能再减 originX：
     *   · 列号：localX + HALO_X（x 下标这一项从 Halo 引入起就是错的 —— 只有 tile=0 时
     *     localX − originX 恰好等于 localX + HALO_X，其余瓦片整块错位 100km）；
     *   · 行号：localZ/CELL_Z + haloZ（I 轮加 Z halo 时漏加，造成 100km 整行错位）。
     * 实测（P191：z=502km 一行真值 100% 陆、旧代码全判成海；z=402km 一行真值 0% 陆、
     * 旧代码判出 337/400 陆）→ 修复后同一张表 42.52% → 2.95%。
     *
     * 残留的 2.95% **不是缺陷**，是掩码自身的分辨率：掩码采样在 1250×5000 的格心上，
     * 而查询点任意，floorDiv 只能取到那一格。P206 的哨兵对照：
     *   双轴对齐（查询点落在格心）0.0000%（0/172800）；只偏 z +2500 → 3.1123%；
     *   只偏 x +625 → 0.1545%；偏 x+1000/z+2500（= P191 口径）→ 3.1128%。
     * 即残留几乎全部来自 **z 方向 5km 量化**（P191 的采样点正好落在两行正中间）。
     */
    private static boolean isLandCell(ClimateGridData d, int localX, int localZ) {
        return d.land[d.idx(Math.floorDiv(localX + HALO_X, CELL_X),
            Math.floorDiv(localZ, CELL_Z) + d.haloZ)];
    }

    /**
     * 网格内双线性（求解器内部用）：wx/wz 是**数组格坐标**（wx = 列·CELL_X，wz = 行·CELL_Z）。
     * X 仍按瓦片环绕；**Z 改为 clamp**——域现在上下各带 HALO_Z_ROWS 行真实数据，
     * 再取模就会把 domain 两端接起来（那正是要消除的环接缝）。
     */
    private static double bilinear(double[] fld, double wx, double wz, int nx, int ny) {
        double gx = (((wx % (nx * CELL_X)) + nx * CELL_X) % (nx * CELL_X)) / (double) CELL_X;
        double gz = wz / (double) CELL_Z;
        if (gz < 0.0) gz = 0.0;
        if (gz > ny - 1.0) gz = ny - 1.0;
        int ix0 = (int) Math.floor(gx), iz0 = (int) Math.floor(gz);
        double tx = gx - ix0, tz = gz - iz0;
        int ix1 = (ix0 + 1) % nx, iz1 = iz0 + 1 >= ny ? ny - 1 : iz0 + 1;
        double s00 = fld[iz0 * nx + ix0], s10 = fld[iz0 * nx + ix1];
        double s01 = fld[iz1 * nx + ix0], s11 = fld[iz1 * nx + ix1];
        return s00 * (1 - tx) * (1 - tz) + s10 * tx * (1 - tz)
             + s01 * (1 - tx) * tz + s11 * tx * tz;
    }

    // ================= 离线求解 =================

    private static ClimateGridData solve(int worldSeedInt, int tileX, int tileZ) {
        long bt0 = System.nanoTime();
        long bn = BUILD_COUNT.incrementAndGet();
        if (BUILD_HARD_CAP > 0 && bn > BUILD_HARD_CAP) {
            throw new IllegalStateException("RelaxedClimate 窗口构建超过硬上限 BUILD_HARD_CAP="
                + BUILD_HARD_CAP + "（已构建 " + bn + " 次，累计 "
                + (BUILD_NANOS.get() / 1_000_000_000L) + "s，缓存占用 " + CACHE.size() + "/" + CACHE_LIMIT
                + "，当前 tile=(" + tileX + "," + tileZ + ")）。"
                + "这几乎一定是缓存抖动：工作集 > CACHE_LIMIT。请改用 tile 主序采样，或调大 CACHE_LIMIT。");
        }
        if (BUILD_WARN_EVERY > 0 && bn % BUILD_WARN_EVERY == 0) {
            System.out.println("[RelaxedClimate] 窗口构建已达 " + bn + " 次，累计 "
                + (BUILD_NANOS.get() / 1_000_000_000L) + "s，缓存 " + CACHE.size() + "/" + CACHE_LIMIT
                + "，当前 tile=(" + tileX + "," + tileZ + ")");
        }
        final int originZ = tileZ * GlobalCirculation.Z_CYCLE;
        ClimateGridData d = new ClimateGridData(tileX * TILE_X - HALO_X,
            originZ, tileZ, (TILE_X + 2 * HALO_X) / CELL_X);
        for (int iy = 0; iy < d.ny; iy++) {
            // 逐行表按**该行的纬度**填：行 iy 的绝对 z = originZ + (iy-haloZ)·CELL_Z，
            // 纬度 = 绝对 z mod Z_CYCLE（由 latOf 给出）。halo 行因此自动拿到正确的纬度，
            // 不需要假设"行号 0..ny-1 恰好是一个完整周期"。
            int z = d.latOf(iy) * CELL_Z;
            double b = GlobalCirculation.bandD(z);
            d.sPoleRow[iy] = z <= GlobalCirculation.Z_CYCLE / 2 ? 1.0 : -1.0;
            // 符号翻转点在**半个纬度周期**处（bandD 的折返点 = 极点），不是 100k。
            // 旧代码写死 100_000（那是 200k 周期的 Z_CYCLE/2 残留），LAT_CYCLE 提到 4M 后
            // 它让 f>0 只覆盖前 100k（周期的 2.5%）、其余 97.5% 符号全反，
            // 且与 sPole 用的 (iy*CELL_Z <= Z_CYCLE/2) 互相矛盾。科氏力符号错了，
            // 地转风方向与埃克曼转向就都错。
            double latRad = GlobalCirculation.latRad(z);
            double f = Math.sin(latRad);
            double fa = Math.abs(f);
            d.fRow[iy] = fa < 1.0e-4 ? 0.0 : f;
            // 物理量：本模型的 f 是归一化的 sin(纬度)，乘 2Ω 才是真科氏参数。
            // β = df/dy = 2Ω·cos(纬)·(π/2)/MAX_D（MAX_D = 半周期 = Z_CYCLE/2）。
            d.fPhys[iy] = 2.0 * BarotropicGyre.OMEGA * d.fRow[iy];
            d.betaRow[iy] = 2.0 * BarotropicGyre.OMEGA * Math.cos(b * Math.PI / 2.0)
                * (Math.PI / 2.0) * 2.0 / GlobalCirculation.Z_CYCLE;
            d.dampRow[iy] = fa / (fa + 0.30);
            d.p0z[iy] = profileP0(b);
        }
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                // **绝对坐标**：X 取窗口起点 + 局部偏移，Z 取本 Z 瓦片起点 + 局部偏移。
                // 海陆/温度/定常波都必须用绝对 z —— 用局部 z 等于把 1M 外那套海陆搬过来（契约违反）。
                // 数组行 iy → 绝对 z = originZ + (iy - haloZ)·CELL_Z（halo 行落在相邻 Z 瓦片上）
                int x = d.originX + ix * CELL_X, z = d.originZ + (iy - d.haloZ) * CELL_Z;
                double b = GlobalCirculation.bandD(d.latOf(iy) * CELL_Z);   // 纬度 = 绝对 z mod Z_CYCLE
                d.land[i] = NoiseContinentGrid.isLand(x, z, worldSeedInt);
                d.teqSea[i] = ThermalForcing.seaTeq(x, z, worldSeedInt);
                d.teqLand[i] = ThermalForcing.landTeq(x, z, worldSeedInt);
                d.qLandEq[i] = 0.08 + 0.30 * ThermalForcing.insolation01(b);   // 湿润热带陆平衡升（雨林水汽）
                // 定常行星波（**固定场**，不随 SST 反馈）：风向的二维结构全靠它。
                // 只有 λ=140 km 一层时风向几乎是纯纬向的 → 洋流退化成水平条带（视觉验收发现）；
                // 补一层 λ=45 km 让风向有真实的弯曲/涡旋，而因为是固定场，不引入任何反馈增益。
                double n = NoiseContinentGrid.bandNoise(x, z, worldSeedInt, 0xABC_1234L, 1.0 / 140_000.0, 2);
                d.wave[i] = (n * 2.0 - 1.0) * WAVE_AMP * Math.sin(Math.PI * b);
                // 固定底：只含纬向廓线与定常波。**陆地热力差移出去了** ——
                // 它在海岸线上是硬跳变（一格 0.6），而大气的响应尺度是天气尺度不是海岸线尺度；
                // 实测（P150）它贡献的风应力旋度比定常波大 40 倍、且在 40 km 尺度上，
                // 把海盆尺度的旋度结构完全淹没了 —— 这正是"长不出环流圈"的根因。
                d.pf[i] = d.p0z[iy] * PF_P0_SCALE + d.wave[i];
                if (d.land[i]) {
                    d.tAir[i] = d.teqLand[i];
                    d.q[i] = d.qLandEq[i];
                    d.mar[i] = 0.0;
                } else {
                    d.sst[i] = d.teqSea[i];
                    d.tAir[i] = d.teqSea[i];
                    d.q[i] = 0.60 + 0.35 * d.teqSea[i];
                    d.mar[i] = 1.0;
                }
            }
        }
        smoothPf(d, PF_SMOOTH_PASSES);
        buildCoastFade(d);
        buildCoastDist(d);
        System.arraycopy(d.sst, 0, d.sstP, 0, d.nx * d.ny);
        updateP(d, worldSeedInt);
        buildDirField(d);
        computeWind(d, 1.0, worldSeedInt);
        updateFlow(d, worldSeedInt, 0);   // 初始化这一轮就是完整自旋（与历史行为逐位一致）
        double[] prevOuterSst = new double[d.nx * d.ny];
        double[] prevOuterFu = new double[d.nx * d.ny];
        double[] prevOuterFv = new double[d.nx * d.ny];
        for (int it = 0; it < OUTER; it++) {
            lastOuterIters = it + 1;
            System.arraycopy(d.sst, 0, prevOuterSst, 0, d.nx * d.ny);
            System.arraycopy(d.fu, 0, prevOuterFu, 0, d.nx * d.ny);
            System.arraycopy(d.fv, 0, prevOuterFv, 0, d.nx * d.ny);
            for (int i = 0; i < d.nx * d.ny; i++) {
                if (!d.land[i]) {
                    d.sstP[i] += SST_INERTIA * (d.sst[i] - d.sstP[i]);
                }
            }
            updateP(d, worldSeedInt);
            smoothP(d, P_SMOOTH_PASSES);
            buildDirField(d);
            computeWind(d, WIND_BLEND, worldSeedInt);
            // 外层耦合循环每轮都重解正压涡旋代价极高（实测 16 次 × ~1.1 s）。
            // 前 15 轮只做粗略自旋（ζ 仍从 0 起步 → 结果与访问顺序无关，确定性不变），
            // 最后一轮才做完整自旋，保证参与 SSC 平流与输出的流场是收敛解。
            //
            // 上限**按参数传进去**：这里曾经写的是 BarotropicGyre.STEP_CAP 静态字段
            // （赋值 → 调用 → 清零），而 preheat 线程会在同一时间窗里调用 solve()，
            // 于是解取决于另一个线程当时停在第几轮 → 非确定性世界内容。
            // 详见 BarotropicGyre 顶部的说明；TalosContract T6b 守着这条不再复发。
            boolean lastOuter = (it == OUTER - 1);
            int stepCap = lastOuter ? 0 : Math.max(150, BarotropicGyre.MACRO / 12);
            updateFlow(d, worldSeedInt, stepCap);
            boolean trace = (it == OUTER - 1);
            int tk = 0;
            for (int k = 0; k < INNER; k++) {
                double res = advectSst(d);
                lastSstResidual = res;
                if (trace && tk < innerTrace.length) {
                    innerTrace[tk] = res;
                    traceMax[tk] = DIAG_MAX;
                    traceCnt[tk] = DIAG_BIG;
                    traceCell[tk * 3] = DIAG_A;
                    traceCell[tk * 3 + 1] = DIAG_B;
                    traceCell[tk * 3 + 2] = DIAG_C;
                    tk++;
                }
                if (res < INNER_TOL) {
                    break;
                }
            }
            if (trace) {
                innerTraceLen = tk;
            }
            for (int k = 0; k < AIR_INNER; k++) {
                advectAir(d, worldSeedInt);
            }
            double os = 0, om = 0, fs = 0, fm = 0;
            int on = 0;
            java.util.Arrays.fill(outerRowHist, 0);
            for (int i = 0; i < d.nx * d.ny; i++) {
                double du = d.fu[i] - prevOuterFu[i], dv = d.fv[i] - prevOuterFv[i];
                double fd = Math.sqrt(du * du + dv * dv);
                fs += fd * fd;
                if (fd > fm) fm = fd;
                if (!d.land[i]) {
                    double dd = d.sst[i] - prevOuterSst[i];
                    os += dd * dd;
                    on++;
                    double ad = dd < 0 ? -dd : dd;
                    if (ad > om) om = ad;
                    if (ad > 0.2) {
                        int row = i / d.nx;
                        int b = row * 10 / d.ny;
                        if (b > 9) b = 9;
                        outerRowHist[b]++;
                    }
                }
            }
            lastOuterDelta = Math.sqrt(os / Math.max(1, on));
            lastOuterDeltaMax = om;
            lastFlowDelta = Math.sqrt(fs / Math.max(1, d.nx * d.ny));
            lastFlowDeltaMax = fm;
        }
        updateP(d, worldSeedInt);
        applyCoastalSst(d);   // 沿岸暖舌/冷舌参数化（未解析的边界层）
        ghostFillSea(d);      // 临海陆格用海值填充，消除跨岸插值拽入 0 值的方块伪影
        BUILD_NANOS.addAndGet(System.nanoTime() - bt0);
        return d;
    }

    /**
     * 临海幽灵填充：把紧邻海的陆格（8 邻域）用海邻值平均填上（sst 与流场），
     * 使沿岸像素的双线性插值窗跨岸时不再拽入 0 值，消除海岸 1~2 格的方块伪影。
     */
    private static void ghostFillSea(ClimateGridData d) {
        for (int pass = 0; pass < 2; pass++) {
            double[] s = new double[d.nx * d.ny];
            double[] fu = new double[d.nx * d.ny];
            double[] fv = new double[d.nx * d.ny];
            for (int iy = 0; iy < d.ny; iy++) {
                for (int ix = 0; ix < d.nx; ix++) {
                    int i = d.idx(ix, iy);
                    if (!d.land[i]) {
                        s[i] = d.sst[i];
                        fu[i] = d.fu[i];
                        fv[i] = d.fv[i];
                        continue;
                    }
                    double ss = 0, us = 0, vs = 0;
                    int n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dy == 0) continue;
                            int j = d.idx(ix + dx, iy + dy);
                            if (!d.land[j]) {
                                ss += d.sst[j];
                                us += d.fu[j];
                                vs += d.fv[j];
                                n++;
                            }
                        }
                    }
                    if (n > 0) {
                        s[i] = ss / n;
                        fu[i] = us / n;
                        fv[i] = vs / n;
                    } else {
                        s[i] = d.sst[i];
                        fu[i] = d.fu[i];
                        fv[i] = d.fv[i];
                    }
                }
            }
            for (int i = 0; i < d.nx * d.ny; i++) {
                if (d.land[i]) {
                    d.sst[i] = s[i];
                    d.fu[i] = fu[i];
                    d.fv[i] = fv[i];
                }
            }
        }
    }

    private static double profileP0(double b) {
        return -0.95 * gauss(b, 0.00, 0.17) + 1.00 * gauss(b, 0.30, 0.085)
             - 0.80 * gauss(b, 0.62, 0.075) + 0.55 * gauss(b, 0.95, 0.09);
    }

    private static double gauss(double b, double c, double s) {
        double x = (b - c) / s;
        return Math.exp(-0.5 * x * x);
    }

    /**
     * 压力场 = 解析纬向廓线 + 局地波 + 距平。
     *
     * **C1 关键改动**：原版用"该纬度**全域**所有海格 SST 的均值"作参考态 —— 那是**全域耦合**，
     * 世界一旦沿 X 无限就无法定义（换域即换解）。改为**解析热力强迫的纬向平均**
     * {@link ThermalForcing#zonalMeanSeaTeq}，于是本函数只依赖本格与其邻域的输入，
     * 求解变成**局地**的（可以按格求解、跨格无缝）。
     */
    private static void updateP(ClimateGridData d, int worldSeedInt) {
        rows(d.ny, iy -> {
            int z = d.latOf(iy) * CELL_Z;          // halo 行也要拿到自己那行的纬度
            double b = GlobalCirculation.bandD(z);
            double sstRef = ThermalForcing.zonalMeanSeaTeq(b);
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int x = d.originX + ix * CELL_X;
                double pv = d.p0z[iy] + d.wave[i];
                double term;
                if (d.land[i]) {
                    term = -1.5 * (d.teqLand[i] - sstRef);
                } else {
                    term = -SST_P_GAIN * (d.sstP[i] - sstRef);
                }
                pv += term;
                // 真实的热力距平（陆地热力差 + SST 距平），供定风向场做天气尺度平滑。
                // 注意不能再用 p − pf 反推：两者差一个 p0z·(1−PF_P0_SCALE) 的虚假纬向项。
                d.anom[i] = term;
                d.p[i] = pv;
            }
        });
    }

    /**
     * 海岸距离场（chamfer 两遍扫描）。网格在 X/Z 两个方向都是环绕的（与 idx 语义一致），
     * 单位是 block。用来做"近岸流向约束"和西边界流的近岸判定——比 coastDistBlocks 的
     * 梯度估计可靠得多（后者在远场会给出不可信的小值，实测约 19% 的海格被误报在 20~50 km）。
     */
    private static void buildCoastDist(ClimateGridData d) {
        final double INF = 1e12;
        final double dx = CELL_X, dz = CELL_Z;
        final double dg = Math.sqrt(dx * dx + dz * dz);
        for (int i = 0; i < d.nx * d.ny; i++) {
            d.coastD[i] = d.land[i] ? 0.0 : INF;
        }
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                double v = d.coastD[i];
                v = Math.min(v, d.coastD[d.idx(ix - 1, iy)] + dx);
                v = Math.min(v, d.coastD[d.idx(ix, iy - 1)] + dz);
                v = Math.min(v, d.coastD[d.idx(ix - 1, iy - 1)] + dg);
                v = Math.min(v, d.coastD[d.idx(ix + 1, iy - 1)] + dg);
                d.coastD[i] = v;
            }
        }
        for (int iy = d.ny - 1; iy >= 0; iy--) {
            for (int ix = d.nx - 1; ix >= 0; ix--) {
                int i = d.idx(ix, iy);
                double v = d.coastD[i];
                v = Math.min(v, d.coastD[d.idx(ix + 1, iy)] + dx);
                v = Math.min(v, d.coastD[d.idx(ix, iy + 1)] + dz);
                v = Math.min(v, d.coastD[d.idx(ix + 1, iy + 1)] + dg);
                v = Math.min(v, d.coastD[d.idx(ix - 1, iy + 1)] + dg);
                d.coastD[i] = v;
            }
        }
    }

    /** Z 方向（行）越界 → clamp 到 halo 边缘。 */
    private static int clampIdx(int v, int m) {
        return v < 0 ? 0 : (v >= m ? m - 1 : v);
    }

    private static int wrapIdx(int v, int m) {
        int r = v % m;
        return r < 0 ? r + m : r;
    }

    /** 可分离盒式模糊·X 方向（前缀和推进，O(n)，与半径无关；沿 X 环绕）。 */
    private static void boxBlurX(double[] src, double[] dst, int nx, int ny, int r) {
        if (r <= 0) {
            System.arraycopy(src, 0, dst, 0, nx * ny);
            return;
        }
        int w = 2 * r + 1;
        for (int iy = 0; iy < ny; iy++) {
            int b = iy * nx;
            double s = 0;
            for (int k = -r; k <= r; k++) {
                s += src[b + wrapIdx(k, nx)];
            }
            for (int ix = 0; ix < nx; ix++) {
                dst[b + ix] = s / w;
                s += src[b + wrapIdx(ix + r + 1, nx)] - src[b + wrapIdx(ix - r, nx)];
            }
        }
    }

    /**
     * 同上·Z 方向（行方向）。**不再环绕**：域上下各带 HALO_Z_ROWS 行真实数据，
     * 环绕会把 domain 两端接起来（就是环接缝）；越界 clamp 到 halo 边缘即可。
     */
    private static void boxBlurY(double[] src, double[] dst, int nx, int ny, int r) {
        if (r <= 0) {
            System.arraycopy(src, 0, dst, 0, nx * ny);
            return;
        }
        int w = 2 * r + 1;
        for (int ix = 0; ix < nx; ix++) {
            double s = 0;
            for (int k = -r; k <= r; k++) {
                s += src[clampIdx(k, ny) * nx + ix];
            }
            for (int iy = 0; iy < ny; iy++) {
                dst[iy * nx + ix] = s / w;
                s += src[clampIdx(iy + r + 1, ny) * nx + ix] - src[clampIdx(iy - r, ny) * nx + ix];
            }
        }
    }

    /**
     * 组装定风向场：pDir = pf + DIR_ANOM_WEIGHT · smooth_SYNOPTIC(p − pf)。
     * DIR_ANOM_WEIGHT = 0 时退化成纯 pf（即上一版的"定向风"）。
     */
    private static void buildDirField(ClimateGridData d) {
        if (DIR_ANOM_WEIGHT <= 0.0) {
            System.arraycopy(d.pf, 0, d.pDir, 0, d.nx * d.ny);
            return;
        }
        int n = d.nx * d.ny;
        // anom 已由 updateP 填好（陆地热力差 + SST 距平），直接平滑
        boxBlurX(d.anom, d.anomT, d.nx, d.ny, (int) Math.max(0, Math.round(SYNOPTIC_KM / CELL_X)));
        boxBlurY(d.anomT, d.anom, d.nx, d.ny, (int) Math.max(0, Math.round(SYNOPTIC_KM / CELL_Z)));
        for (int i = 0; i < n; i++) {
            d.pDir[i] = d.pf[i] + DIR_ANOM_WEIGHT * d.anom[i];
        }
    }

    /** 对定风向场 pf 做 5 点平滑（0.4·自 + 0.15·四邻）。 */
    private static void smoothPf(ClimateGridData d, int passes) {
        for (int k = 0; k < passes; k++) {
            rows(d.ny, iy -> {
                for (int ix = 0; ix < d.nx; ix++) {
                    int i = d.idx(ix, iy);
                    double s = d.pf[d.idx(ix + 1, iy)] + d.pf[d.idx(ix - 1, iy)]
                             + d.pf[d.idx(ix, iy + 1)] + d.pf[d.idx(ix, iy - 1)];
                    d.pSm[i] = d.pf[i] * 0.4 + s * 0.15;
                }
            });
            System.arraycopy(d.pSm, 0, d.pf, 0, d.nx * d.ny);
        }
    }

    /** 气压场 5 点平滑（0.4·自 + 0.15·四邻，和为 1）。 */
    /**
     * 海岸渐隐场：对"海掩码"做 2 遍盒式模糊，得到 1（开阔海）→0（岸）的连续权重。
     *
     * 用途只有一个：让 advectSst 的回溯在接近陆地时**平滑减速**，而不是撞到陆格就 break。
     * 硬 break 下，初始方向上一点点变化就会让回溯"2 步就停"或"14 步跑到完全不同的地方"，
     * s*(流场) 因此不连续，外层耦合环没法收敛。放在这里而不是去缩放 d.fu/d.fv，是因为实测
     * "把整个流矢量按离岸距离缩放"会把西边界流一起掐掉——0~20 km 沿岸流速被压到远场的
     * 40%（0.512 vs 1.304），而 20~50 km 反而是峰值。物理流场该在岸边多强就多强。
     */
    private static void buildCoastFade(ClimateGridData d) {
        for (int i = 0; i < d.nx * d.ny; i++) {
            d.fade[i] = d.land[i] ? 0.0 : 1.0;
        }
        double[] tmp = d.lap;   // 复用平滑缓冲（此处在求解前调用，lap 还没被用到）
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < d.nx * d.ny; i++) {
                int ix = i % d.nx, iy = i / d.nx;
                double s = d.fade[i] * 0.4;
                s += d.fade[d.idx(ix + 1, iy)] * 0.15;
                s += d.fade[d.idx(ix - 1, iy)] * 0.15;
                s += d.fade[d.idx(ix, iy + 1)] * 0.15;
                s += d.fade[d.idx(ix, iy - 1)] * 0.15;
                tmp[i] = s;
            }
            System.arraycopy(tmp, 0, d.fade, 0, d.nx * d.ny);
        }
    }

    private static void smoothP(ClimateGridData d, int passes) {
        for (int k = 0; k < passes; k++) {
            rows(d.ny, iy -> {
                for (int ix = 0; ix < d.nx; ix++) {
                    int i = d.idx(ix, iy);
                    double s = d.p[d.idx(ix + 1, iy)] + d.p[d.idx(ix - 1, iy)]
                             + d.p[d.idx(ix, iy + 1)] + d.p[d.idx(ix, iy - 1)];
                    d.pSm[i] = d.p[i] * 0.4 + s * 0.15;
                }
            });
            System.arraycopy(d.pSm, 0, d.p, 0, d.nx * d.ny);
        }
    }

    private static void computeWind(ClimateGridData d, double blendNew, int worldSeedInt) {
        double scale = 3_500.0 * WIND_SCALE;
        double ca = Math.cos(WIND_TURN), sa = Math.sin(WIND_TURN);
        double[] nu = new double[d.nx * d.ny];
        double[] nv = new double[d.nx * d.ny];
        rows(d.ny, iy -> {
            double f = d.fRow[iy];
            if (f == 0.0) {
                for (int ix = 0; ix < d.nx; ix++) {
                    nu[d.idx(ix, iy)] = 0;
                    nv[d.idx(ix, iy)] = 0;
                }
                return;
            }
            double damp = d.dampRow[iy];
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int ip = d.idx(ix + 1, iy), im = d.idx(ix - 1, iy);
                int jp = d.idx(ix, iy + 1), jm = d.idx(ix, iy - 1);
                double dpdx = (d.p[ip] - d.p[im]) / (2.0 * CELL_X);
                double dpdz = (d.p[jp] - d.p[jm]) / (2.0 * CELL_Z);
                double ug = -(dpdz / f) * scale * damp;
                double vg = (dpdx / f) * scale * damp;
                double sp = Math.sqrt(ug * ug + vg * vg);
                if (sp < 1e-9) {
                    nu[i] = 0;
                    nv[i] = 0;
                    continue;
                }
                double gl = Math.sqrt(dpdx * dpdx + dpdz * dpdz) + 1e-12;
                double gxu, gzu, uxu = ug / sp, uzu = vg / sp;
                if (FIXED_WIND_DIR) {
                    double fxp = (d.pDir[ip] - d.pDir[im]) / (2.0 * CELL_X);
                    double fzp = (d.pDir[jp] - d.pDir[jm]) / (2.0 * CELL_Z);
                    double glp = Math.sqrt(fxp * fxp + fzp * fzp) + 1e-12;
                    gxu = -fxp / glp;
                    gzu = -fzp / glp;
                    double ugp = -(fzp / f) * scale * damp, vgp = (fxp / f) * scale * damp;
                    double spp = Math.sqrt(ugp * ugp + vgp * vgp);
                    if (spp > 1e-12) {
                        uxu = ugp / spp;
                        uzu = vgp / spp;
                    }
                } else {
                    gxu = -dpdx / gl;
                    gzu = -dpdz / gl;
                }
                double sx = uxu * ca + gxu * sa;
                double sz = uzu * ca + gzu * sa;
                double sl = Math.sqrt(sx * sx + sz * sz) + 1e-12;
                nu[i] = (sx / sl) * sp * 0.88;
                nv[i] = (sz / sl) * sp * 0.88;
            }
        });
        double w1 = blendNew, w2 = 1.0 - blendNew;
        rows(d.ny, iy -> {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                d.u[i] = nu[i] * w1 + d.pu[i] * w2;
                d.v[i] = nv[i] * w1 + d.pv[i] * w2;
                d.pu[i] = d.u[i];
                d.pv[i] = d.v[i];
            }
        });
    }

    /**
     * 近岸流向约束半径（blocks）。
     *
     * 原来的"岸墙折射"只有 26 km 且只消掉**撞岸**的分量，26 km 之外流向完全不管海岸 →
     * 视觉上就是横贯全图的水平带。缩放前的世界尺寸下这个宽度取 200 km；本轮 1/4 等比缩放时
     * 一并按比例缩到 **50 km**（注释里曾写"放大到 200 km"，那是缩放前的值，已改正）。
     * 带内把**法向分量**压掉
     * （向岸的全消、离岸的消一半，保证仍有离岸流），于是近岸流被迫**与海岸平行**，
     * 也就是"贴着陆地流"。这是参数化，不是从涡度平衡涌现的——但不做它就没有沿岸流。
     */
    public static double COAST_ALIGN = 50_000.0;
    /** 沿岸海温参数化：作用格数（1 格 = CELL blocks）与增益。 */
    /** 东边界上升流的离岸作用宽度（blocks）。 */
    private static final double COAST_UPWELL_BLOCKS = 25_000.0;
    /** 沿岸暖舌的绝对幅度（**独立常量**；历史上曾与已删除的 BC_STRENGTH 绑定，已解耦）。 */
    private static final double COAST_WBC_TONGUE = 0.32;
    // **不要把它写成"另一个后置常量"的函数**：静态初始化器按文本顺序执行，后声明的非编译期常量
    // 用限定名会绕过前向引用检查、实际读到默认值 0.0，使 COAST_WBC_GAIN 变成 +Infinity
    // （曾把整片海 SST 推成 ±1/NaN）。历史上这个坑来自已删除的 BC_STRENGTH。
    // 现行做法：暖舌幅度只由 COAST_WBC_TONGUE 决定（=0.16），适用位置由 applyCoastalSst 的
    // "真西岸 + (1−dW/BC_WIDTH)² 向海衰减"决定（P191/P192/P196 实测）。
    private static final double COAST_WBC_GAIN = COAST_WBC_TONGUE / 2.0;
    private static final double COAST_UPWELL_GAIN = 0.22;


    /**
     * 沿岸海温参数化（未解析的边界层）：
     *   · 西边界：把边界回流携带的暖/冷水叠加到海温（回流向极 → 暖舌，向赤道 → 冷舌）
     *   · 东边界：上升流（Ekman 辐散）→ 冷舌（加州/秘鲁/本格拉型）
     * 西边界用已局部化的 wbc，东边界用"向东找真陆地"的距离，都不再依赖窗口内的海段起止。
     */
    private static void applyCoastalSst(ClimateGridData d) {
        for (int iy = 0; iy < d.ny; iy++) {
            // 边界回流的**向极分量**决定暖舌（两个半球的副热带西边界都是向极暖流）
            double sPole = d.sPoleRow[iy];   // 按该行绝对 z 的纬度带（halo 行也对）
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    continue;
                }
                double adj = 0.0;
                // 西边界暖/冷舌：直接用已经**局部化**的 wbc —— 它只在本行"真西岸"
                // （西边 BC_WIDTH 内有陆、东边没有）非零，并自带向海衰减。
                // 原写法用 dW = k − start（本行在**填充窗口**里第一个海格）：海段横跨整个
                // 填充窗时 start 落在 400 km 外的 halo 里 → 暖舌被放到可见区之外，真海岸上
                // 什么都没有。这与西边界流是同一类 bug，一并修掉。
                // 直接用求解出的经向流速当西边界流强度（归一化到典型 WBC 速度 0.5 m/s）。
                //
                // **作用域修复（探针 P191/P184 定位）**：这一项曾经对**每个海格无条件生效**
                // （实测离岸 400km 仍 100% 被施加），把沿岸暖舌参数化撒到了整片海洋上。
                // 现在恢复成"只在真西岸 + 向海衰减"：
                //   西岸判据 = 西侧 BC_WIDTH 内有陆、且东侧 BC_WIDTH 内无陆（洋盆西边界，非海峡）；
                //   衰减 = (1 - dW/BC_WIDTH)^2，dW 为向西到陆的距离。
                // 判据用"向西找真陆"实现（与下方东岸上升流的"向东找真陆"镜像），
                // 不依赖任何"窗口内连续海段起点"的写法（那些已作为死代码删除）。
                if (!ABLATE_T1_COAST_WBC) {
                    int maxW = (int) Math.max(1, Math.round(BC_WIDTH / CELL_X));
                    int dW = -1;
                    for (int q = 1; q <= maxW; q++) {
                        if (d.land[d.idx(ix - q, iy)]) {
                            dW = (q - 1) * (int) CELL_X;
                            break;
                        }
                    }
                    if (dW >= 0) {
                        boolean eastOpen = true;
                        for (int q = 1; q <= maxW; q++) {
                            if (d.land[d.idx(ix + q, iy)]) {
                                eastOpen = false;
                                break;
                            }
                        }
                        if (eastOpen) {
                            double decay = 1.0 - dW / BC_WIDTH;
                            adj += COAST_WBC_GAIN * sPole
                                * Math.max(-1.0, Math.min(1.0, d.fv[i] / 0.5)) * decay * decay;
                        }
                    }
                }
                // 东边界上升流：向东找**真陆地**，而不是海段终点。
                if (!ABLATE_T2_UPWELL && d.coastD[i] < COAST_UPWELL_BLOCKS) {
                    int maxCells = (int) Math.max(1, Math.round(COAST_UPWELL_BLOCKS / CELL_X));
                    for (int q = 1; q <= maxCells; q++) {
                        if (d.land[d.idx(ix + q, iy)]) {
                            double dE = (q - 1) * (double) CELL_X;
                            double decay = 1.0 - dE / COAST_UPWELL_BLOCKS;
                            adj -= COAST_UPWELL_GAIN * decay * decay;
                            break;
                        }
                    }
                }
                if (adj != 0.0) {
                    double v = d.sst[i] + adj;
                    d.sst[i] = v > 1 ? 1 : (v < -1 ? -1 : v);
                }
            }
        }
    }
    /**
     * 西边界层宽度（blocks）。
     *
     * 原来是固定 80 km，而实测海盆中位数只有 690 km → 边界层占盆宽 12%，读起来是宽带不是急流。
     * 现在改成"离西岸 BC_WIDTH 内"，既与海盆宽度无关（局部判定），也不会在大陆放大后失效。
     */
    public static double BC_WIDTH = 37_500.0;

    /**
     * **诊断消融开关（生产必须全为 false；默认 false = 行为完全不变）**。
     * 用于定位"u/v 偏置 6.95 vs 求解器单独 1.15"与"南北差异"来自哪一项耦合（探针 P184）：
     *   T1 = applyCoastalSst 的西岸暖/冷舌项（**已局部化**：只在本行"真西岸"——西侧 BC_WIDTH 内有陆、
     *        东侧没有——非零，并带 (1−dW/BC_WIDTH)² 向海衰减；作用域实测 100% → 9.26% 海格，P196）
     *   T2 = applyCoastalSst 的东岸上升流项（coastD + 向东找陆，本来就是局部化的）
     *   T3 = updateFlow 的近岸法向压缩（coastD < COAST_ALIGN，也是局部化的）
     *
     * 这三条是**诊断用**的消融开关：默认全 false = 生产行为逐位不变。定位完 u/v 偏置的来源
     * （P184/P189 已做）且 PSI 图（批次 2）出完之后，这三个开关就该删掉，不要长期留在生产代码里。
     */
    public static boolean ABLATE_T1_COAST_WBC = false;
    public static boolean ABLATE_T2_UPWELL = false;
    public static boolean ABLATE_T3_ALIGN = false;

    /**
     * @param stepCap 传给 {@link BarotropicGyre#solve} 的步数上限。**必须是参数**：
     *                它曾经是跨线程共享的静态字段，preheat 线程会串改前台的值。
     */
    private static void updateFlow(ClimateGridData d, int worldSeedInt, int stepCap) {
        double ca = Math.cos(0.35), sa = Math.sin(0.35);
        // ---- 1) Sverdrup 输运 → 西边界层经向速度 ----
        // β·V = curl(τ)；洋盆内净经向输运必须由**西边界回流**抵消，
        // 于是西边界流速 ∝ −∫_west^east curl dx（沿纬度行从西岸积到东岸）。
        // 这给出真实的**环流圈方向**：副热带（NH）风应力旋度为负 → 西边界向极；
        // 副极地旋度为正 → 西边界向赤道；南半球随风场自动镜像，无需手写半球因子。
        // ===== 路线 B：稳态风生正压环流求解器 =====
        // 用涡度方程 β·v = curl(τ)/(ρ₀H) − rζ + A_h∇²ζ 解出 u/v（m/s），
        // 取代原来的"埃克曼旋转 + Sverdrup 自归一化参数化"。
        // 地块 ψ=0 的 Dirichlet 条件天然给出"无穿岸流"，β 效应自带西向强化。
        BarotropicGyre.solve(d.nx, d.ny, CELL_X, CELL_Z, d.land,
            d.u, d.v, d.fPhys, d.betaRow, d.fu, d.fv, stepCap);

        rows(d.ny, iy -> {
            double s = d.sPoleRow[iy];
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    d.fu[i] = 0;
                    d.fv[i] = 0;
                    continue;
                }
                // 流速已由 BarotropicGyre 写好（单位 m/s）；这里只叠加近岸约束
                double fx = d.fu[i], fz = d.fv[i];
                // 近岸流向约束：把**法向分量**压掉（向岸全消、离岸消一半），
                // 流量被迫与海岸平行。coastD 的梯度就是"指向海"的法向，处处可用。
                double cd = d.coastD[i];
                if (!ABLATE_T3_ALIGN && cd > 0 && cd < COAST_ALIGN) {
                    double gx2 = d.coastD[d.idx(ix + 1, iy)] - d.coastD[d.idx(ix - 1, iy)];
                    double gz2 = d.coastD[d.idx(ix, iy + 1)] - d.coastD[d.idx(ix, iy - 1)];
                    double gl2 = Math.sqrt(gx2 * gx2 + gz2 * gz2);
                    if (gl2 > 1e-6) {
                        double nx = gx2 / gl2, nz = gz2 / gl2;
                        double vn = fx * nx + fz * nz;
                        double k = 1.0 - cd / COAST_ALIGN;
                        double kill = vn < 0 ? k : k * 0.5;
                        fx -= vn * nx * kill;
                        fz -= vn * nz * kill;
                    }
                }
                d.fu[i] = fx;
                d.fv[i] = fz;
            }
        });
    }

    /** 海温输运步（仅海上）；返回 RMS 变化。 */
    private static double advectSst(ClimateGridData d) {
        int steps = SST_TRACE_STEPS;   // 回溯步数：决定海温异常能被流场搬运多远（西边界暖舌/东边界冷舌）
        double dt = 1500.0;
        System.arraycopy(d.sst, 0, d.sstOld, 0, d.nx * d.ny);
        // 海温弛豫长度：水体保留自身温度的 e 折距离。真实海洋 SST 弛豫时间 ~30-60 天，
        // 流速 ~0.1 m/s → 250~500km。原值 55km 会让异常在 84km 路径上只剩 22% → 洋流对海温几乎无影响。
        double relaxL = 62_500.0;
        rows(d.ny, iy -> {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    d.sstNew[i] = d.sst[i];
                    continue;
                }
                double px = ix * CELL_X, pz = iy * CELL_Z;
                double travelled = 0;
                for (int k = 0; k < steps; k++) {
                    double fx = bilinear(d.fu, px, pz, d.nx, d.ny), fz = bilinear(d.fv, px, pz, d.nx, d.ny);
                    double sp = Math.sqrt(fx * fx + fz * fz);
                    if (sp < 1e-9) {
                        break;
                    }
                    // 饱和式而非归一化：px -= F/(|F|+REF)·dt（弱流位移趋零，方向处处光滑）
                    // 再乘海岸渐隐：接近陆地时位移平滑趋零，回溯自然停住（不必硬 break）
                    double rate = dt / (sp + ADVECT_SPEED_REF) * bilinear(d.fade, px, pz, d.nx, d.ny);
                    double qx = px - fx * rate, qz = pz - fz * rate;
                    int lx = (int) Math.floor(qx / CELL_X);
                    int lz = (int) Math.floor(qz / CELL_Z);
                    if (d.land[d.idx(lx, lz)]) {
                        break;          // 不把回溯点推进陆格：陆上 sst=0 会当"0 度水"污染沿海
                    }
                    travelled += Math.sqrt((qx - px) * (qx - px) + (qz - pz) * (qz - pz));
                    px = qx;
                    pz = qz;
                }
                double tAd = bilinear(d.sst, px, pz, d.nx, d.ny);
                // 弛豫分数按**实际走过的路程**算（含一步下限）：路程对流场连续，
                // 而原来按"步数"算会随 break 位置整数跳变，是另一处不连续源。
                double f = 1.0 - Math.exp(-(travelled + dt) / relaxL);
                d.sstNew[i] = tAd + (d.teqSea[i] - tAd) * f;
            }
        });
        rows(d.ny, iy -> {
            boolean smooth = iy > 0 && iy < d.ny - 1;   // 极区两行边界条件特殊，不做平滑
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (!smooth || d.land[i]) {
                    d.lap[i] = 0;
                    continue;
                }
                // 只对**海邻居**做拉普拉斯：陆格 sst=0 不是"零度水"，把它算进来会让沿海格
                // 每轮被拽向 0、再被平流推回，形成残差 2.7e-2 的永动极限环（P102 实测）。
                int j1 = d.idx(ix + 1, iy), j2 = d.idx(ix - 1, iy);
                int j3 = d.idx(ix, iy + 1), j4 = d.idx(ix, iy - 1);
                double nsum = 0;
                int cnt = 0;
                if (!d.land[j1]) { nsum += d.sstNew[j1]; cnt++; }
                if (!d.land[j2]) { nsum += d.sstNew[j2]; cnt++; }
                if (!d.land[j3]) { nsum += d.sstNew[j3]; cnt++; }
                if (!d.land[j4]) { nsum += d.sstNew[j4]; cnt++; }
                d.lap[i] = cnt > 0 ? (nsum - cnt * d.sstNew[i]) * 0.04 : 0;
            }
        });
        for (int i = 0; i < d.nx * d.ny; i++) {
            d.sstNew[i] += d.lap[i];
        }
        rows(d.ny, iy -> {
            double rsum = 0, rmx = 0;
            int rn = 0, rbig = 0;
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    continue;
                }
                double v = d.sstNew[i];
                if (v > 1) v = 1;
                if (v < -1) v = -1;
                d.sst[i] = v;
                // 残差量的必须是**整步**（平流+平滑+限幅）之后相对上一轮的变化。
                // 旧写法只量平流子步 (sstNew - sst)，而 sstNew 随后还要被平滑，
                // 于是即使在真不动点上残差也停在"平滑增量"量级（实测 1.37e-2），
                // 看起来像极限环，实际是判据量错了对象（P103 诊断）。
                double dd = v - d.sstOld[i];
                rsum += dd * dd;
                rn++;
                double ad = dd < 0 ? -dd : dd;
                if (ad > rmx) rmx = ad;
                if (ad > 1.0e-3) rbig++;
            }
            d.rowSq[iy] = rsum;
            d.rowN[iy] = rn;
            d.rowMx[iy] = rmx;
            d.rowBig[iy] = rbig;
        });
        double sum = 0, mx = 0;
        int n = 0, big = 0;
        for (int iy = 0; iy < d.ny; iy++) {
            sum += d.rowSq[iy];
            n += d.rowN[iy];
            if (d.rowMx[iy] > mx) mx = d.rowMx[iy];
            big += d.rowBig[iy];
        }
        DIAG_MAX = mx;
        DIAG_BIG = big;
        DIAG_N = n;
        DIAG_A = n > 1000 ? d.sst[1000] : 0;
        DIAG_B = n > 5000 ? d.sst[5000] : 0;
        DIAG_C = n > 15000 ? d.sst[15000] : 0;
        return Math.sqrt(sum / Math.max(1, n));
    }

    /** 空气三场输运步（全定义域；海上目标=已解海温、陆地目标=landTeq/干平衡）。 */
    private static void advectAir(ClimateGridData d, int worldSeedInt) {
        int steps = 5;
        double dt = 1500.0;
        double lt = 40_000.0, lqSea = 20_000.0, lqLand = 55_000.0, lm = 30_000.0;
        rows(d.ny, iy -> {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                double px = ix * CELL_X, pz = iy * CELL_Z;
                boolean stopped = false;
                // 先取路径最远点做"源地初始化"（用当前场近似），再从远到近弛豫
                double[][] path = new double[steps + 1][2];
                path[0][0] = px;
                path[0][1] = pz;
                for (int k = 1; k <= steps && !stopped; k++) {
                    double fx = bilinear(d.u, px, pz, d.nx, d.ny), fz = bilinear(d.v, px, pz, d.nx, d.ny);
                    double sp = Math.sqrt(fx * fx + fz * fz);
                    if (sp < 1e-9) {
                        stopped = true;
                        for (int j = k; j <= steps; j++) {
                            path[j][0] = px;
                            path[j][1] = pz;
                        }
                        break;
                    }
                    double rate = dt / (sp + ADVECT_SPEED_REF);
                    px -= fx * rate;
                    pz -= fz * rate;
                    path[k][0] = px;
                    path[k][1] = pz;
                }
                int sxi = (int) Math.floor(path[steps][0] / CELL_X);
                int szi = (int) Math.floor(path[steps][1] / CELL_Z);
                int si = d.idx(sxi, szi);
                double t = d.land[si] ? d.teqLand[si] : d.sst[si];
                double q = d.land[si] ? d.qLandEq[si] : 0.60 + 0.35 * d.sst[si];
                double m = d.land[si] ? 0.0 : 1.0;
                for (int k = steps - 1; k >= 0; k--) {
                    int cxi = (int) Math.floor(path[k][0] / CELL_X);
                    int czi = (int) Math.floor(path[k][1] / CELL_Z);
                    int ci = d.idx(cxi, czi);
                    boolean cl = d.land[ci];
                    double eqT = cl ? d.teqLand[ci] : d.sst[ci];
                    double eqQ = cl ? d.qLandEq[ci] : 0.60 + 0.35 * d.sst[ci];
                    double ft = 1.0 - Math.exp(-dt / lt);
                    double fq = 1.0 - Math.exp(-dt / (cl ? lqLand : lqSea));
                    double fm = 1.0 - Math.exp(-dt / lm);
                    t += (eqT - t) * ft;
                    q += (eqQ - q) * fq;
                    m += ((cl ? 0.0 : 1.0) - m) * fm;
                }
                d.tNew[i] = t;
                d.qNew[i] = q;
                d.marNew[i] = m;
            }
        });
        // 拷回（轻微限幅）
        rows(d.ny, iy -> {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                double t = d.tNew[i];
                double q = d.qNew[i];
                double m = d.marNew[i];
                d.tAir[i] = t < -1 ? -1 : (t > 1 ? 1 : t);
                d.q[i] = q < 0 ? 0 : (q > 1 ? 1 : q);
                d.mar[i] = m < 0 ? 0 : (m > 1 ? 1 : m);
            }
        });
    }
}
