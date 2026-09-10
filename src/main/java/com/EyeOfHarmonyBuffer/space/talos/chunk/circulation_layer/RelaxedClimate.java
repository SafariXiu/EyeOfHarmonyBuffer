package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.util.concurrent.ConcurrentHashMap;

/**
 * M5+M6 核心：环面离线松弛求解的气候场（按世界种子缓存，运行时查表插值）。
 *
 * 物理：在 400k×200k 环面的粗网格（2k/格）上做**嵌套定点迭代**：
 *   内层1：固定流场下把海温收敛到"逆流输运 + 向 seaTeq 弛豫"的不动点；
 *   内层2：同流场下把**空气温度/湿度/海洋性**收敛（海上目标=已解海温，陆上目标=landTeq/干平衡）；
 *   外层：用新海温更新气压（暖池低压/冷舌高压，增益 2.2）→ 地转风 + 摩擦
 *         （风速欠弛豫 0.5 抑制追逐）→ 埃克曼转向 + 岸墙折射的洋流。
 * 产出：副热带环流圈、西岸暖/东岸冷、寒舌暖池、迎风岸湿舌等由耦合自身涌现。
 *
 * 查询（全部双线性 O(1)≈100ns）：samplePressure/sampleWind/sampleCurrent/sampleSst/
 * sampleHumidity/sampleAirTemp/sampleMaritime。首次访问某种子执行求解（约 5s，一次性）。
 * 确定性：固定迭代上限 + 阈值早停。
 */
public final class RelaxedClimate {

    private RelaxedClimate() {}

    /** 网格分辨率（block/格）。 */
    public static final int CELL_X = 5000;
    /** Z 方向网格（block/格）：纬度循环 4,000,000 / 20,000 = 200 行。 */
    public static final int CELL_Z = 20_000;
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
    /** 诊断：Sverdrup 西边界流的自归一化尺度——maxShear 与逐行 strength（暴露问题用）。 */
    public static double lastMaxShear, lastMeanUMin, lastMeanUMax;
    public static final double[] lastStrength = new double[512];
    public static int lastStrengthN;
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
    public static double SYNOPTIC_KM = 300_000.0;
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
     * 配套：BC_STRENGTH 必须同比放大（它也是绝对速度），COAST_WBC_GAIN 反向补偿以保持
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
    public static double ADVECT_SPEED_REF = 0.2;
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

    private static final java.util.concurrent.ExecutorService HEATER =
        java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "TalosClimatePreheat");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });

    private static final java.util.Set<Long> PENDING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 诊断：累计预热完成的窗口数 / 最近一次预热耗时(ms)。 */
    public static volatile int preheated;
    public static volatile long lastPreheatMs;

    private static void warm(int worldSeedInt, int tileX) {
        long key = cacheKey(worldSeedInt, tileX);
        if (CACHE.containsKey(key) || !PENDING.add(key)) {
            return;
        }
        HEATER.execute(() -> {
            try {
                long t0 = System.nanoTime();
                CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tileX));
                lastPreheatMs = (System.nanoTime() - t0) / 1_000_000L;
                preheated++;
            } catch (Throwable ignored) {
                // 预热失败不影响前台：下次真正访问时会重解
            } finally {
                PENDING.remove(key);
            }
        });
    }

    /** 窗口缓存上限（每个 ≈1.3 MB）。 */
    public static int CACHE_LIMIT = 5;

    private static long cacheKey(int seed, int tileX) {
        return ((long) seed << 24) ^ (tileX & 0xFFFFFFL);
    }

    /** 网格场数据。 */
    static final class ClimateGridData {
        /** 本窗口在**绝对世界坐标**里的 X 起点（窗口只是"解哪一段"，不改世界）。 */
        final int originX;
        /** 含 halo 的 X 格数（暴露区 400k + 两侧各 HALO_X）。 */
        final int nx;
        final int ny = GlobalCirculation.Z_CYCLE / CELL_Z;

        ClimateGridData(int originX, int nxPadded) {
            this.originX = originX;
            this.nx = nxPadded;
            // 数组必须在构造器里分配：nx 是构造参数，字段初始化器阶段还没赋值
            int n = nx * ny;
            land = new boolean[n];
            sst = new double[n];
            teqSea = new double[n];
            teqLand = new double[n];
            qLandEq = new double[n];
            p = new double[n];
            u = new double[n];
            v = new double[n];
            fu = new double[n];
            fv = new double[n];
            tAir = new double[n];
            q = new double[n];
            mar = new double[n];
            pSm = new double[n];
            pf = new double[n];
            pDir = new double[n];
            anom = new double[n];
            anomT = new double[n];
            wave = new double[n];
            pu = new double[n];
            pv = new double[n];
            sstNew = new double[n];
            lap = new double[n];
            fade = new double[n];
            coastD = new double[n];
            sstOld = new double[n];
            sstP = new double[n];
            tNew = new double[n];
            qNew = new double[n];
            marNew = new double[n];
            p0z = new double[ny];
            fRow = new double[ny];
            dampRow = new double[ny];
            rowSq = new double[ny];
            rowN = new int[ny];
            rowMx = new double[ny];
            rowBig = new int[ny];
        }

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

        int idx(int ix, int iy) {
            int wy = ((iy % ny) + ny) % ny;
            return wy * nx + ((ix % nx + nx) % nx);
        }
    }

    // ================= 查询 API =================

    /** 预热原点窗口。 */
    public static ClimateGridData ensure(int worldSeedInt) {
        return tileGrid(worldSeedInt, 0);
    }

    /** 清空窗口缓存（世界卸载 / 探针扫参）。 */
    public static void clearCache() {
        CACHE.clear();
    }

    // ================= C2：气候按 X 瓦片求解（世界沿 X 无限） =================

    /** 瓦片宽度（blocks）：沿用原域宽。 */
    public static final int TILE_X = 400_000;
    /** 交叉淡入带宽度（blocks），近邻窗口解差异的衰减长度就是它。 */
    public static int TILE_BLEND = 100_000;
    /**
     * 求解 halo（blocks）：每侧在暴露窗口外**多解**这么宽。
     *
     * 这是"窗口解不唯一"的根治办法。原来窗口 X 方向是**环面**（bilinear/idx 都按 400k 取模），
     * 而内层平流的有效记忆长度 ~300 km（14 步 × 6 km，按 0.715^k 展开），几乎必然绕回原点；
     * 再叠加 sverdrupWbc/applyCoastalSst 用"本窗口内的洋盆起点"当西边界，
     * 于是相邻窗口解出来的场**整体**差 0.4（P113 实测：跨边界 4 km 处 Δp=0.36，
     * 而窗口内部 4 km 处只有 0.0055 —— 不是"缝"而是两套完全不同的气候）。
     * 加一圈 ≥ 记忆长度的 halo 后，暴露区内的所有回溯路径都落在 halo 内部，
     * 不再碰到"窗口边缘"这个人为边界。
     */
    public static int HALO_X = 400_000;

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
     * 取某窗口的解：**同一个世界种子 + 绝对坐标窗口**（不是"每瓦片换种子"）。
     * 换种子的写法会让每个窗口针对一套虚构海陆松弛，与真实地形对不上（已修正）。
     */
    private static ClimateGridData tileGrid(int worldSeedInt, int tileX) {
        long key = cacheKey(worldSeedInt, tileX);
        ClimateGridData d = CACHE.get(key);
        if (d != null) {
            return d;
        }
        // 逐出"离当前窗口最远"的那个：sampleBlended 一次要用 tile-1/tile/tile+1，
        // 任意逐出（迭代器顺序）会把刚用到的邻居踢掉，造成反复重解（旧写法实测会抖）。
        if (CACHE.size() >= CACHE_LIMIT) {
            long worst = 0;
            int worstDist = -1;
            for (Long k : CACHE.keySet()) {
                int tx = (int) (k & 0xFFFFFFL);
                if (tx > 0x7FFFFF) {
                    tx -= 0x1000000;
                }
                int dist = Math.abs(tx - tileX);
                if (dist > worstDist) {
                    worstDist = dist;
                    worst = k;
                }
            }
            if (worstDist > 0) {
                CACHE.remove(worst);
            }
        }
        final int tile = tileX;
        ClimateGridData solved = CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tile));
        if (PREHEAT) {
            warm(worldSeedInt, tileX - 1);
            warm(worldSeedInt, tileX + 1);
        }
        return solved;
    }

    /**
     * 取场值：瓦片查表，可选跨瓦片交叉淡入（{@link #TILE_BLEND}）。
     *
     * 有了 {@link #HALO_X} 之后相邻窗口在重叠区已经解出同一个场（不再是两套气候），
     * 所以默认 {@code TILE_BLEND = 0}（直接取本瓦片，连淡入都不需要）。
     * 保留淡入逻辑是为了在 halo 预算被调小时还能退化成"缝被抹平"的老方案。
     */
    private static double sampleBlended(int worldSeedInt, int x, int z, int f) {
        int tile = tileOfX(x);
        double localX = x - (double) tile * TILE_X;
        ClimateGridData d = tileGrid(worldSeedInt, tile);
        // 网格坐标 = 暴露区局部坐标 + halo 偏移
        double gx = localX + HALO_X;
        double v = bilinear(fieldOf(d, f), gx, z, d.nx, d.ny);
        if (TILE_BLEND > 0) {
            if (localX < TILE_BLEND) {
                double k = 0.5 - localX / (2.0 * TILE_BLEND);      // 边界 0.5 → 带内 0
                double vn = bilinear(fieldOf(tileGrid(worldSeedInt, tile - 1), f),
                    gx + TILE_X, z, d.nx, d.ny);
                v = v * (1.0 - k) + vn * k;
            } else if (localX > TILE_X - TILE_BLEND) {
                double k = 0.5 - (TILE_X - localX) / (2.0 * TILE_BLEND);
                double vn = bilinear(fieldOf(tileGrid(worldSeedInt, tile + 1), f),
                    gx - TILE_X, z, d.nx, d.ny);
                v = v * (1.0 - k) + vn * k;
            }
        }
        return v;
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
        int tile = tileOfX(x);
        ClimateGridData d = tileGrid(worldSeedInt, tile);
        if (isLandCell(d, x - tile * TILE_X, z)) {
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

    private static boolean isLandCell(ClimateGridData d, int x, int z) {
        return d.land[d.idx(Math.floorDiv(x - d.originX, CELL_X), Math.floorDiv(z, CELL_Z))];
    }

    private static double bilinear(double[] fld, double wx, double wz, int nx, int ny) {
        int XC = nx * CELL_X, ZC = ny * CELL_Z;
        int fx = (int) (((wx % XC) + XC) % XC);
        int fz = (int) (((wz % ZC) + ZC) % ZC);
        double gx = fx / (double) CELL_X, gz = fz / (double) CELL_Z;
        int ix0 = (int) Math.floor(gx), iz0 = (int) Math.floor(gz);
        double tx = gx - ix0, tz = gz - iz0;
        int ix1 = (ix0 + 1) % nx, iz1 = (iz0 + 1) % ny;
        double s00 = fld[iz0 * nx + ix0], s10 = fld[iz0 * nx + ix1];
        double s01 = fld[iz1 * nx + ix0], s11 = fld[iz1 * nx + ix1];
        return s00 * (1 - tx) * (1 - tz) + s10 * tx * (1 - tz)
             + s01 * (1 - tx) * tz + s11 * tx * tz;
    }

    // ================= 离线求解 =================

    private static ClimateGridData solve(int worldSeedInt, int tileX) {
        ClimateGridData d = new ClimateGridData(tileX * TILE_X - HALO_X, (TILE_X + 2 * HALO_X) / CELL_X);
        for (int iy = 0; iy < d.ny; iy++) {
            int z = iy * CELL_Z;
            int zm = GlobalCirculation.foldZ(z);
            double b = GlobalCirculation.bandD(z);
            double latRad = (zm <= 100_000 ? b : -b) * Math.PI / 2.0;
            double f = Math.sin(latRad);
            double fa = Math.abs(f);
            d.fRow[iy] = fa < 1.0e-4 ? 0.0 : f;
            d.dampRow[iy] = fa / (fa + 0.30);
            d.p0z[iy] = profileP0(b);
        }
        for (int iy = 0; iy < d.ny; iy++) {
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int x = d.originX + ix * CELL_X, z = iy * CELL_Z;   // 绝对坐标：窗口只决定解哪一段
                double b = GlobalCirculation.bandD(z);
                d.land[i] = NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0;
                d.teqSea[i] = ThermalForcing.seaTeq(x, z, worldSeedInt);
                d.teqLand[i] = ThermalForcing.landTeq(x, z, worldSeedInt);
                d.qLandEq[i] = 0.08 + 0.30 * ThermalForcing.insolation01(b);   // 湿润热带陆平衡升（雨林水汽）
                // 定常行星波（**固定场**，不随 SST 反馈）：风向的二维结构全靠它。
                // 只有 λ=140 km 一层时风向几乎是纯纬向的 → 洋流退化成水平条带（视觉验收发现）；
                // 补一层 λ=45 km 让风向有真实的弯曲/涡旋，而因为是固定场，不引入任何反馈增益。
                double n = NoiseContinentGrid.bandNoise(x, z, worldSeedInt, 0xABC_1234L, 1.0 / 140_000.0, 2);
                d.wave[i] = (n * 2.0 - 1.0) * WAVE_AMP * Math.sin(Math.PI * b);
                d.pf[i] = d.p0z[iy] * PF_P0_SCALE + d.wave[i]
                    + (d.land[i] ? -1.5 * (d.teqLand[i] - ThermalForcing.zonalMeanSeaTeq(b)) : 0.0);
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
        updateFlow(d, worldSeedInt);
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
            updateFlow(d, worldSeedInt);
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
            int z = iy * CELL_Z;
            double b = GlobalCirculation.bandD(z);
            double sstRef = ThermalForcing.zonalMeanSeaTeq(b);
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                int x = d.originX + ix * CELL_X;
                double pv = d.p0z[iy] + d.wave[i];
                if (d.land[i]) {
                    double teq = ThermalForcing.landTeq(x, z, worldSeedInt);
                    pv += -1.5 * (teq - sstRef);
                } else {
                    pv -= SST_P_GAIN * (d.sstP[i] - sstRef);
                }
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

    /** 同上·Z 方向（行方向，沿纬度环绕）。 */
    private static void boxBlurY(double[] src, double[] dst, int nx, int ny, int r) {
        if (r <= 0) {
            System.arraycopy(src, 0, dst, 0, nx * ny);
            return;
        }
        int w = 2 * r + 1;
        for (int ix = 0; ix < nx; ix++) {
            double s = 0;
            for (int k = -r; k <= r; k++) {
                s += src[wrapIdx(k, ny) * nx + ix];
            }
            for (int iy = 0; iy < ny; iy++) {
                dst[iy * nx + ix] = s / w;
                s += src[wrapIdx(iy + r + 1, ny) * nx + ix] - src[wrapIdx(iy - r, ny) * nx + ix];
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
        for (int i = 0; i < n; i++) {
            d.anom[i] = d.p[i] - d.pf[i];
        }
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
        double scale = 14_000.0 * WIND_SCALE;
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
     * 视觉上就是横贯全图的水平带。现在放大到 200 km，并在带内把**法向分量**压掉
     * （向岸的全消、离岸的消一半，保证仍有离岸流），于是近岸流被迫**与海岸平行**，
     * 也就是"贴着陆地流"。这是参数化，不是从涡度平衡涌现的——但不做它就没有沿岸流。
     */
    public static double COAST_ALIGN = 200_000.0;
    /** 沿岸海温参数化：作用格数（1 格 = CELL blocks）与增益。 */
    /** 东边界上升流的离岸作用宽度（blocks）。 */
    private static final double COAST_UPWELL_BLOCKS = 100_000.0;
    /** 沿岸暖舌的绝对幅度 = COAST_WBC_GAIN × BC_STRENGTH，绑定成常量以免两者漂开。 */
    private static final double COAST_WBC_TONGUE = 0.32;
    private static final double COAST_WBC_GAIN = COAST_WBC_TONGUE / RelaxedClimate.BC_STRENGTH;
    private static final double COAST_UPWELL_GAIN = 0.22;

    /**
     * Sverdrup 西边界回流速度场（每格，仅海格非零）。
     * β·V = curl(τ) → 洋盆内净经向输运由西边界回流抵消，强度 ∝ −∫_west^east curl dx。
     */
    private static double[] sverdrupWbc(ClimateGridData d) {
        // 行平均纬向风：**解析廓线**（由 p0z 地转风给出），不用"全域海格均值"
        // —— 后者是全域耦合，X 无限时无定义（C1 改动）。
        double[] meanU = new double[d.ny];
        for (int iy = 0; iy < d.ny; iy++) {
            double f = d.fRow[iy];
            if (f == 0.0) {
                meanU[iy] = 0;
                continue;
            }
            double dpdz0 = (d.p0z[(iy + 1) % d.ny] - d.p0z[((iy - 1) % d.ny + d.ny) % d.ny])
                / (2.0 * CELL_Z);
            meanU[iy] = -(dpdz0 / f) * 14_000.0 * d.dampRow[iy];
        }
        // 积分形式 Sverdrup：∫_west^east curl dx = [v] − ∂/∂z(∫u dx)
        // 洋盆内主项 = −∂(纬向风积分)/∂z；用行平均 u 的南北差近似（稳健，不受 ∂v/∂x 噪声影响）
        double[] shear = new double[d.ny];
        double maxShear = 1e-12;
        for (int iy = 0; iy < d.ny; iy++) {
            shear[iy] = (meanU[(iy + 1) % d.ny] - meanU[((iy - 1) % d.ny + d.ny) % d.ny])
                / (2.0 * CELL_Z);
            double a = Math.abs(shear[iy]);
            if (a > maxShear) {
                maxShear = a;
            }
        }
        lastMeanUMin = Double.MAX_VALUE;
        lastMeanUMax = -Double.MAX_VALUE;
        for (int iy = 0; iy < d.ny; iy++) {
            if (meanU[iy] < lastMeanUMin) lastMeanUMin = meanU[iy];
            if (meanU[iy] > lastMeanUMax) lastMeanUMax = meanU[iy];
        }
        lastMaxShear = maxShear;
        lastStrengthN = d.ny;
        double[] wbc = new double[d.nx * d.ny];
        for (int iy = 0; iy < d.ny; iy++) {
            double strength = BC_STRENGTH * shear[iy] / maxShear;   // 自归一：峰值 ±BC_STRENGTH
            if (iy < lastStrength.length) {
                lastStrength[iy] = strength;
            }
            if (strength == 0.0) {
                continue;
            }
            // 局部判定："西边 BC_WIDTH 内有陆、东边没有" = 这里是洋盆的**西边界**。
            // 旧写法靠"本行连续海段的起点"，而海段起点常落在 400 km 宽的 halo 边上
            // （人为边缘不是海岸）→ 西边界流被放到可见区之外；大陆放大后海盆超过
            // 整个填充窗宽，那个写法会更彻底失效。
            int maxCells = (int) Math.max(1, Math.round(BC_WIDTH / CELL_X));
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i] || d.coastD[i] > BC_WIDTH) {
                    continue;
                }
                int westCells = 0;
                boolean westLand = false;
                for (int k = 1; k <= maxCells; k++) {
                    if (d.land[d.idx(ix - k, iy)]) {
                        westLand = true;
                        westCells = k;
                        break;
                    }
                }
                if (!westLand) {
                    continue;
                }
                boolean eastLand = false;
                for (int k = 1; k <= maxCells; k++) {
                    if (d.land[d.idx(ix + k, iy)]) {
                        eastLand = true;
                        break;
                    }
                }
                if (eastLand) {
                    continue;   // 东边也有陆 → 是海峡，不是洋盆西边界
                }
                double decay = 1.0 - (westCells - 1) / (double) maxCells;
                wbc[i] = strength * decay * decay;
            }
        }
        return wbc;
    }

    /**
     * 沿岸海温参数化（未解析的边界层）：
     *   · 西边界：把边界回流携带的暖/冷水叠加到海温（回流向极 → 暖舌，向赤道 → 冷舌）
     *   · 东边界：上升流（Ekman 辐散）→ 冷舌（加州/秘鲁/本格拉型）
     * 西边界用已局部化的 wbc，东边界用"向东找真陆地"的距离，都不再依赖窗口内的海段起止。
     */
    private static void applyCoastalSst(ClimateGridData d) {
        double[] wbc = sverdrupWbc(d);
        for (int iy = 0; iy < d.ny; iy++) {
            // 边界回流的**向极分量**决定暖舌（两个半球的副热带西边界都是向极暖流）
            double sPole = (iy * CELL_Z <= GlobalCirculation.Z_CYCLE / 2) ? 1.0 : -1.0;
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
                adj += COAST_WBC_GAIN * sPole * wbc[i];
                // 东边界上升流：向东找**真陆地**，而不是海段终点。
                if (d.coastD[i] < COAST_UPWELL_BLOCKS) {
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
    public static double BC_WIDTH = 150_000.0;
    /**
     * 西边界回流峰值速度（按行平均纬向风切变自归一）。
     *
     * **必须与风速量级同步**：它是直接加到 fz 上的绝对速度。纬度循环 ×20 后 p0z 的经向
     * 梯度小了 20 倍、风速从 ~1.9 掉到 ~0.14，而 2.5 这个老常量不变 → 西边界流变成了
     * 风本身的 18 倍，洋流各向异性 RMS(fx):RMS(fz) 从 6.5 掉到 0.97（P118 实测）。
     */
    public static double BC_STRENGTH = 2.0;

    private static void updateFlow(ClimateGridData d, int worldSeedInt) {
        double ca = Math.cos(0.35), sa = Math.sin(0.35);
        // ---- 1) Sverdrup 输运 → 西边界层经向速度 ----
        // β·V = curl(τ)；洋盆内净经向输运必须由**西边界回流**抵消，
        // 于是西边界流速 ∝ −∫_west^east curl dx（沿纬度行从西岸积到东岸）。
        // 这给出真实的**环流圈方向**：副热带（NH）风应力旋度为负 → 西边界向极；
        // 副极地旋度为正 → 西边界向赤道；南半球随风场自动镜像，无需手写半球因子。
        double[] wbc = sverdrupWbc(d);

        rows(d.ny, iy -> {
            double s = (iy * CELL_Z <= GlobalCirculation.Z_CYCLE / 2) ? 1.0 : -1.0;
            for (int ix = 0; ix < d.nx; ix++) {
                int i = d.idx(ix, iy);
                if (d.land[i]) {
                    d.fu[i] = 0;
                    d.fv[i] = 0;
                    continue;
                }
                double sp = Math.sqrt(d.u[i] * d.u[i] + d.v[i] * d.v[i]);
                if (sp < 1e-6) {
                    d.fu[i] = 0;
                    d.fv[i] = 0;
                    continue;
                }
                double ux = d.u[i] / sp, uz = d.v[i] / sp;
                // 埃克曼转向：旋转**整个风矢量**（保留风速量级 → 流速有强弱）
                double fx = (ux * ca + s * uz * sa) * sp;
                double fz = (-s * ux * sa + uz * ca) * sp;
                // 西边界层回流（Sverdrup 补偿）
                fz += wbc[i];
                // 近岸流向约束：把**法向分量**压掉（向岸全消、离岸消一半），
                // 流量被迫与海岸平行。coastD 的梯度就是"指向海"的法向，处处可用。
                double cd = d.coastD[i];
                if (cd > 0 && cd < COAST_ALIGN) {
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
        double dt = 6000.0;
        System.arraycopy(d.sst, 0, d.sstOld, 0, d.nx * d.ny);
        // 海温弛豫长度：水体保留自身温度的 e 折距离。真实海洋 SST 弛豫时间 ~30-60 天，
        // 流速 ~0.1 m/s → 250~500km。原值 55km 会让异常在 84km 路径上只剩 22% → 洋流对海温几乎无影响。
        double relaxL = 250_000.0;
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
        double dt = 6000.0;
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
