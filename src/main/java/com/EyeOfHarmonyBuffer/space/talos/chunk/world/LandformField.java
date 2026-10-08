package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 地貌场（L1c-0，D43）：**「这里是什么地貌」的唯一权威**。
 *
 * 背景（D39/D42 遗留的严重问题）：地形层与群系层各自从 L1b 重新推导了一次
 * "这里是不是山"，用的还是不同的场（地形 = beltMask01×elevation01，群系 = relief01），
 * 于是热干气候下"地形是山、群系是沙漠"——沙漠表层是沙子，山区就变沙了。
 *
 * 根治：把五档档案权重（低地/丘陵/台地/山地/峰）抽成**唯一的权重场**，
 * 地形层（{@link V2TerrainGen}）与群系层（{@link V2BiomeSelect}）都只消费它，
 * 两边不可能再漂移。
 *
 * 每格输出：
 *   low / hill / plat / mtn / peak —— 五档档案权重（和为 1）
 *   mtnAmt —— 「山体强度」[0,1] = clamp01(rise / {@link #MTN_RISE_SCALE})
 *             其中 rise = (1−auth)·mtnComp0 + auth·uplift（**含山层仲裁与中尺度纹理**）
 *
 * **不要把它和 {@link V2TerrainGen#DETAIL_MTNCOMP_SCALE} 当成同一个标尺**：那两个数
 * （85 / 90）看着像重复，实际喂进去的是**两个不同的量**——
 *   · 这里 rise  = 已按山层权威权重仲裁过的**抬升量**（群系用它判"是不是山"）；
 *   · 那里 mtnComp = base − plain，是**仲裁前**的基础山地贡献（地形用它定块级细节强度）。
 * 前者是"这座山有多高"，后者是"基础地形自己有多少山味"，量纲相同但定义不同 ⇒ 各自一个常数。
 * 曾经本行的文档写的是 {@code mtnComp0/70}（一个早就不存在的式子），照文档改代码就会把地形改坏。
 *
 * 求解时机：山层之后、群系场之前（不需要气候场）。
 */
public final class LandformField {

    private LandformField() {}

    /** 网格分辨率（blocks，250m）。 */
    public static final int CELL = 250;

    /** 窗口宽度（blocks）：世界沿 X 无限 → 每窗口独立求解（绝对坐标，不换种子）。 */
    public static final int TILE_X = 100_000;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）（曾误缩到 2,500 ⟹ 网格 10x5 ⟹ 地形被抹平）

    public static final int TILE_Z = 50_000;   // ★ 2026-10-08 回退：地形层与行星尺度无关（TILE/CELL 服务 MC 分辨率与地形质量）

    // 必须跟着 TILE_X/TILE_Z 走：曾经硬编码 400_000/200_000，1/4 缩放后 TILE_X 变成 100_000
    // 而这里没跟着改，于是 solve() 算满 1600x800 而 sample() 只读前 400x200 —— 白算 16 倍。
    /** 暴露区格数（不含 halo）：400 × 200。声明放在 TILE_* 之后，直接派生，不用限定名绕前向引用。 */
    public static final int NX = TILE_X / CELL;

    public static final int NZ = TILE_Z / CELL;

    /** 含 1 格 halo 的数组宽高：solve 算 [-1, NX] × [-1, NZ]，索引 (j+1)*SX + (i+1)。 */
    public static final int SX = NX + 2;

    public static final int SZ = NZ + 2;

    /** 求解用的海平面（与 Provider 的 getWaterLevel 一致，仅用于档案带限下限）。 */
    public static final int SEA_LEVEL = 64;

    /**
     * 「山体抬升量 → 强度」标尺（blocks）。**唯一使用点：{@link #solve} 里的 {@code rise / MTN_RISE_SCALE}。**
     *
     * 抬升量 rise = (1−auth)·mtnComp0 + auth·uplift（含中尺度纹理）。
     * 这是"这里是不是山"的**实测口径**——不再用档案标签（MOUNTAIN 档在低底盘区只有 ~100 高）。
     *
     * 与 {@link V2TerrainGen#DETAIL_MTNCOMP_SCALE}（=90，喂的是仲裁前的 mtnComp）**不是同一个标尺**，
     * 见类注释的说明。
     */
    public static final double MTN_RISE_SCALE = 85.0;

    /** 瓦片缓存键。**唯一实现见 {@link WindowKey}** —— 旧写法把 int 种子截成 24 位，
     *  同一个式子当时在本层与 RelaxedClimate/MountainLayerV2/V2BiomeField 各有一份。 */
    private static long tileKey(int seed, int tileX, int tileZ) {
        return WindowKey.of(seed, tileX, tileZ);
    }

    private static final ConcurrentHashMap<Long, Field> CACHE =
        new ConcurrentHashMap<Long, Field>();

    /**
     * 解缓存上限（瓦片数）。每个 Field = 7 × SX×SZ float ≈ 2.27 MB → 20 个 ≈ **45 MB**。
     *
     * 20 的依据：
     *   - 本类自己的 halo 邻域：查询横跨 tileX ± 1、tileZ ± 1 = 3×3 = **9** 个瓦片；
     *   - 已知最宽的消费者：400km × 200km 的扫描（**历史**：V2BiomeField 旧口径的 1600×800 求解；
     *     或地图命令）= 4×4 = **16** 个瓦片；
     *   - 再往上留余量，是为了躲开 **LRU 的经典病理**：工作集略大于容量 + 循环访问时，
     *     LRU 每次淘汰的恰好是"再过几轮就要用"的那块。实测（探针 P173 的对抗测例：
     *     1 个热瓦片 + 17 个冷瓦片轮转 = 18 个工作集）：
     *       容量 12 → 97 次重解；容量 16 → 97 次；**容量 20 → 18 次（= 理想值）**。
     *     这个模式在地图命令/广域扫描里是可能出现的，45MB 换掉它是划算的。
     */
    private static final int CACHE_LIMIT = 20;

    /**
     * 诊断计数器（探针用，生产只加两个 long、无行为影响）：solve() 调用次数与累计耗时。
     * 用途：验证"任意淘汰"造成的重复求解（P169 的 88k 点扫描里实测 23,822 次）。
     */
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_COUNT =
        new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_NANOS =
        new java.util.concurrent.atomic.AtomicLong();

    /**
     * **求解硬上限（安全阀，不是优化）**。0 = 不限（生产默认，行为与以前逐位一致）。
     *
     * 为什么加：第 4 次"进程跑飞"（探针 P209 的 period 段）就发生在本层 ——
     * 6,000 次采样触发了 7,165 次 LandformField 求解（≈1.19 次/查询，等于每次查询都重建），
     * 1,110 s CPU 跑不出来。成因与气候层同源：**采样在瓦片上跳 → 工作集 > 缓存容量**。
     * 探针/命令做广域扫描时设一个上限，宁可快速失败也不要闷跑几小时。**生产不要设它。**
     */
    public static int SOLVE_HARD_CAP = 0;
    /** 每求解这么多次就打一行警告（0 = 不打；生产默认 0，不影响任何输出）。 */
    public static int SOLVE_WARN_EVERY = 0;


    /** 清零诊断计数器（探针用）。 */
    public static void resetStats() {
        SOLVE_COUNT.set(0L);
        SOLVE_NANOS.set(0L);
    }

    private static final class Field {
        final float[] low = new float[SX * SZ];
        final float[] hill = new float[SX * SZ];
        final float[] plat = new float[SX * SZ];
        final float[] mtn = new float[SX * SZ];
        final float[] peak = new float[SX * SZ];
        final float[] mtnAmt = new float[SX * SZ];
        /** 中性 bias 的静态骨架高度（供"雪线以上"判据，避免与地形高度循环依赖）。 */
        final float[] h0 = new float[SX * SZ];
        /**
         * 最近一次被命中的时间（**只参与淘汰排序，不参与任何计算**，与 MountainLayerV2 同款）。
         * volatile 只为多线程下的原子/可见；无竞争写，热点代价可忽略。
         */
        volatile long lastUse = System.nanoTime();
    }

    /** 查询结果（线程本地复用 → 热路径零分配）。 */
    public static final class Sample {
        public double low, hill, plat, mtn, peak, mtnAmt, h0;

        public double mtnPlusPeak() {
            return mtn + peak;
        }
    }

    private static final ThreadLocal<Sample> TL = new ThreadLocal<Sample>() {
        @Override
        protected Sample initialValue() {
            return new Sample();
        }
    };

    /** 后台预热。 */
    public static void ensure(int worldSeedInt) {
        field(worldSeedInt, 0, 0);
    }

    /**
     * 清空解缓存。**不是死 API**：探针工作区有 90+ 处调用它来扫参，其中 P168/P169 是入库的守卫。
     * 审计时看到"src 里没人调用"请不要删。
     */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * 淘汰"最久没被命中"的那块瓦片（近似 LRU）。
     *
     * 旧写法是删迭代器吐出的第一个 key（= 任意瓦片）。行/列扫描里每个瓦片会被反复复用，
     * 任意淘汰会把**当前正在用的热瓦片**踢掉 → 下一次访问重解 → 再踢 → 级联重解
     * （MountainLayerV2 实测同款缺陷：一个 LandformField 瓦片被重解 800+ 次 / 21.9s）。
     *
     * **重要澄清（免得后人再把锅算到淘汰策略头上）**：第一次 P169 跑出"88k 点扫描里
     * LandformField 重解 23,822 次"时，根因**不是**淘汰策略，而是 V2BiomeField 的旧宽口径
     * 求解：旧 NX/NZ = 1600×800，每块 biome 瓦片要跑 256 万次 LandformField.sample，
     * 工作集横跨 400km×200km = 16+ 个 LandformField 瓦片。V2BiomeField 修成 400×200 之后，
     * 同一条扫描的重解次数立刻从 23,822 掉到 482，LRU 落地后是 9（= 9 个瓦片的工作集，理想值）。
     * 也就是说：**先量工作集，再谈淘汰策略**。
     */
    private static void evictOldest() {
        Long worst = null;
        long worstUse = Long.MAX_VALUE;
        for (java.util.Map.Entry<Long, Field> e : CACHE.entrySet()) {
            long u = e.getValue().lastUse;
            if (u < worstUse) {
                worstUse = u;
                worst = e.getKey();
            }
        }
        if (worst != null) {
            CACHE.remove(worst);
        }
    }

    private static Field field(int worldSeedInt, int tileX, int tileZ) {
        long key = tileKey(worldSeedInt, tileX, tileZ);
        Field f = CACHE.get(key);
        if (f != null) {
            f.lastUse = System.nanoTime();
            return f;
        }
        if (CACHE.size() >= CACHE_LIMIT) {
            evictOldest();
        }
        final int tx = tileX, tz = tileZ;
        Field solved = CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tx, tz));
        solved.lastUse = System.nanoTime();
        return solved;
    }

    /** 双线性查询（无分配，结果对象线程本地复用）。 */
    public static int tileOfX(int x) {
        return Math.floorDiv(x, TILE_X);
    }

    public static int tileOfZ(int z) {
        return Math.floorDiv(z, TILE_Z);
    }

    // ★★★★★★★ 2026-10-08 性能修复：**加记忆化**（原来每次调用都算 7×4×4 Catmull-Rom）
    //
    // 【为什么】`V2BiomeSelect.accumulateWeights` 对**每一格**都调本方法，
    //   而 `V2BiomeField.solve` = 400×200 = **81,000 格/次**。
    //   更糟的是【同一格会被调用两次】：
    //     V2BiomeSelect:117  ClimateCoords.sample → SimClimate → ... → sample()
    //     V2BiomeSelect:155  lf = LandformField.sample(...)        ← 同一格
    //   实测日志：[BiomeField] land=5762ms，其中 landform=5672ms（★ 98%）。
    //
    // 【缓存】ThreadLocal + 坐标键（含 seed）。80,000 个工作集 ⟹ 上限 262,144。
    //   ⚠ 存【不可变数组】而不是 Sample（Sample 是复用的可变对象）。
    // ★ 2026-10-08 **回退**：曾试过「8 个 LongDblMap」，但每次命中要 8 次查找 ⟹ 不划算。
    //   保留原来的 `HashMap<Long,double[]>`（一次查找取 7 个值）。
    private static final ThreadLocal<java.util.HashMap<Long, double[]>> SAMPLE_MEMO =
        ThreadLocal.withInitial(java.util.HashMap::new);
    public static boolean SAMPLE_MEMO_ON = true;
    public static void clearSampleMemo() { SAMPLE_MEMO.get().clear(); }

    public static Sample sample(int x, int z, int worldSeedInt) {
        if (!SAMPLE_MEMO_ON) return sampleRaw(x, z, worldSeedInt);
        java.util.HashMap<Long, double[]> m = SAMPLE_MEMO.get();
        long key = ((((long) x) << 32) ^ (z & 0xFFFFFFFFL)) * 31L + worldSeedInt;
        double[] v = m.get(key);
        Sample s = TL.get();
        if (v != null) {
            s.low = v[0]; s.hill = v[1]; s.plat = v[2]; s.mtn = v[3];
            s.peak = v[4]; s.mtnAmt = v[5]; s.h0 = v[6];
            return s; }
        Sample r = sampleRaw(x, z, worldSeedInt);
        if (m.size() > 262144) m.clear();
        m.put(key, new double[]{ r.low, r.hill, r.plat, r.mtn, r.peak, r.mtnAmt, r.h0 });
        return r; }

    private static Sample sampleRaw(int x, int z, int worldSeedInt) {
        int tile = tileOfX(x), tileZ = tileOfZ(z);
        Field f = field(worldSeedInt, tile, tileZ);
        Sample s = TL.get();
        double fx = (x - tile * (double) TILE_X) / (double) CELL - 0.5;
        double fz = (z - tileZ * (double) TILE_Z) / (double) CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        if (i < -1) { i = -1; tx = 0; }
        if (i > NX - 1) { i = NX - 1; tx = 1; }
        if (j < -1) { j = -1; tz = 0; }
        if (j > NZ - 1) { j = NZ - 1; tz = 1; }
        // ★ §7728：用 4x4 Catmull-Rom（丙）替代 2x2 bilerp。
        //   用户放大图确认：格折痕是【直线段 + 折角】的多边形（200-400 格），
        //   正是 250 m 网格的插值折痕 —— smoothstep（甲）只降 4 倍不够。
        s.low = crSample(f.low, i, j, tx, tz);
        s.hill = crSample(f.hill, i, j, tx, tz);
        s.plat = crSample(f.plat, i, j, tx, tz);
        s.mtn = crSample(f.mtn, i, j, tx, tz);
        s.peak = crSample(f.peak, i, j, tx, tz);
        s.mtnAmt = crSample(f.mtnAmt, i, j, tx, tz);
        s.h0 = crSample(f.h0, i, j, tx, tz);
        return s;
    }

    /**
     * ★ §7728：<b>4x4 Catmull-Rom 张量积插值（丙）</b>。
     *
     * <p><b>为什么需要</b>：{@code §7725} 的 smoothstep 只保证一阶导连续（C1），
     * 二阶导在格边界仍跳变 ⟹ 图上【仍能看到细的对角折痕】。
     * Catmull-Rom 的插值核在【采样点上】是 C1 的（二阶导仅在节点处不连续），
     * 折痕强度显著低于 bilerp/smoothstep。
     *
     * <p>需要 4x4 邻域。{@link #solve} 算了 [-1, NX] 的 halo ⟹
     * 只要 {@code i-1 >= -1 && i+2 <= NX} 就够；越界时【clamp 端点】（等价于重复边界格）。
     *
     * @param g 场地数组（索引 (j+1)*SX + (i+1)）
     */
    private static double crSample(float[] g, int i, int j, double tx, double tz) {
        int i0 = i - 1, i3 = i + 2, j0 = j - 1, j3 = j + 2;
        if (i0 < -1) i0 = -1;
        if (i3 > NX) i3 = NX;
        if (j0 < -1) j0 = -1;
        if (j3 > NZ) j3 = NZ;
        int ia = Math.max(-1, i0), ib = Math.max(-1, i), ic = Math.min(NX, i + 1), id = Math.min(NX, i3);
        int ja = Math.max(-1, j0), jb = Math.max(-1, j), jc = Math.min(NZ, j + 1), jd = Math.min(NZ, j3);
        double r0 = cr1(g[(ja + 1) * SX + (ia + 1)], g[(ja + 1) * SX + (ib + 1)],
                        g[(ja + 1) * SX + (ic + 1)], g[(ja + 1) * SX + (id + 1)], tx);
        double r1 = cr1(g[(jb + 1) * SX + (ia + 1)], g[(jb + 1) * SX + (ib + 1)],
                        g[(jb + 1) * SX + (ic + 1)], g[(jb + 1) * SX + (id + 1)], tx);
        double r2 = cr1(g[(jc + 1) * SX + (ia + 1)], g[(jc + 1) * SX + (ib + 1)],
                        g[(jc + 1) * SX + (ic + 1)], g[(jc + 1) * SX + (id + 1)], tx);
        double r3 = cr1(g[(jd + 1) * SX + (ia + 1)], g[(jd + 1) * SX + (ib + 1)],
                        g[(jd + 1) * SX + (ic + 1)], g[(jd + 1) * SX + (id + 1)], tx);
        return cr1(r0, r1, r2, r3, tz);
    }

    /** 1D 均匀 Catmull-Rom。 */
    private static double cr1(double p0, double p1, double p2, double p3, double t) {
        double t2 = t * t, t3 = t2 * t;
        return 0.5 * ((2.0 * p1)
                + (-p0 + p2) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t2
                + (-p0 + 3.0 * p1 - 3.0 * p2 + p3) * t3);
    }

    private static double bilerp(float[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = g[k00], v10 = g[k10], v01 = g[k01], v11 = g[k11];
        // ★ §7725：smoothstep 加权 —— 裸双线性的【对角折痕】会在图上连成长直线。
        //   （本类的 mtnPlusPeak() 是侵蚀的门控，所以折痕会直接进地形。）
        double sx = tx * tx * (3.0 - 2.0 * tx);
        double sz = tz * tz * (3.0 - 2.0 * tz);
        return (v00 * (1 - sx) + v10 * sx) * (1 - sz) + (v01 * (1 - sx) + v11 * sx) * sz;
    }

    // ================= 权重公式（唯一来源） =================

    /**
     * 五档档案权重（连续、和为 1）。out = {low, hill, plat, mtn, peak}。
     *
     * 关键：山带走廊（relief/belt）覆盖面达陆地 5~6 成，若走廊整体抬到山地带，
     * 低海拔走廊也会变成 110+ 的大山 → 中纬大面积雪白。故"走廊×高海拔"才成山
     * （elevation01 只在此处作为"是否真山"的门槛连续量，不直接定高）；
     * 低海拔走廊并入丘陵档（起伏放大但不长高）。
     */
    public static void computeWeights(double belt, double relief, double elev, double[] out) {
        double rest = 1.0 - belt;
        double platP = smoothstep(0.52, 0.74, elev);
        double wPlat = rest * platP;
        double hillP = smoothstep(0.16, 0.46, relief);
        double m = smoothstep(0.22, 0.60, elev);
        double pk = smoothstep(0.72, 0.95, relief);
        out[0] = rest * (1.0 - platP) * (1.0 - hillP);              // low
        out[1] = belt * (1.0 - m) + rest * (1.0 - platP) * hillP;   // hill
        out[2] = wPlat;                                             // plat
        out[3] = belt * m * (1.0 - pk);                             // mtn
        out[4] = belt * m * pk;                                     // peak
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }

    // ================= 离线求解 =================

    /** 安全阀共用检查（0 = 不限，生产默认）。 */
    private static void checkSolveCap(long sn, int tx, int tz) {
        if (SOLVE_HARD_CAP > 0 && sn > SOLVE_HARD_CAP) {
            throw new IllegalStateException("LandformField 求解超过硬上限 SOLVE_HARD_CAP=" + SOLVE_HARD_CAP
                + "（已 " + sn + " 次，累计 " + (SOLVE_NANOS.get() / 1_000_000_000L) + "s，当前瓦片=("
                + tx + "," + tz + ")）。几乎一定是缓存抖动：工作集 > 容量。请改用 tile 主序采样。");
        }
        if (SOLVE_WARN_EVERY > 0 && sn % SOLVE_WARN_EVERY == 0) {
            System.out.println("[LandformField] 求解已达 " + sn + " 次，累计 "
                + (SOLVE_NANOS.get() / 1_000_000_000L) + "s，当前瓦片=(" + tx + "," + tz + ")");
        }
    }

    private static Field solve(int seed, int tileX, int tileZ) {
        long sn = SOLVE_COUNT.incrementAndGet();
        checkSolveCap(sn, tileX, tileZ);
        final int originX = tileX * TILE_X, originZ = tileZ * TILE_Z;
        long t0 = System.nanoTime();
        // ★★★★★★★ 分段计时（定位真热点）
        long tOro = 0, tW = 0, tBP = 0, tAuth = 0;
        int n = SX * SZ;
        Field f = new Field();
        double[] w = new double[5];
        double[] bp = new double[2];
        double[] au = new double[3];   // ★ authAll 的输出缓冲（复用）

        for (int j = -1; j <= NZ; j++) {
            int z = originZ + j * CELL + CELL / 2;
            for (int i = -1; i <= NX; i++) {
                int x = originX + i * CELL + CELL / 2;
                int k = (j + 1) * SX + (i + 1);
                long _a = System.nanoTime();
                OrographyField.OroSample o = OrographyField.sample(x, z, seed);
                long _b = System.nanoTime(); tOro += _b - _a;
                computeWeights(o.beltMask01, o.relief01, o.elevation01, w);
                long _c = System.nanoTime(); tW += _c - _b;

                // 中性 bias 的骨架高度（**含中尺度纹理**）→ 真实的"相对平原抬升量"
                V2TerrainGen.basePlainFromWeights(w, x, z, seed, SEA_LEVEL, 0.5, 0.5, bp);
                long _d = System.nanoTime(); tBP += _d - _c;
                // ★ 2026-10-08：一次取齐（原为两次独立调用，各含 HashMap 查 + 3 次 doubleToLongBits）
                double mtnComp0 = bp[0] > bp[1] ? bp[0] - bp[1] : 0.0;
                MountainLayerV2.authAll(x, z, seed, au);
                long _e = System.nanoTime(); tAuth += _e - _d;
                double auth = au[0];
                double uplift = au[1];
                double rise = (1.0 - auth) * mtnComp0 + auth * uplift;
                double amt = rise / MTN_RISE_SCALE;
                if (amt > 1.0) {
                    amt = 1.0;
                } else if (amt < 0.0) {
                    amt = 0.0;
                }
                f.h0[k] = (float) (bp[1] + rise);

                f.low[k] = (float) w[0];
                f.hill[k] = (float) w[1];
                f.plat[k] = (float) w[2];
                f.mtn[k] = (float) w[3];
                f.peak[k] = (float) w[4];
                f.mtnAmt[k] = (float) amt;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        SOLVE_NANOS.addAndGet(System.nanoTime() - t0);
        System.out.println(String.format(
            "[Landform] seed=%d solved in %dms  (%dx%d @ %dm)  [oro=%.0f w=%.0f bp=%.0f auth=%.0f]ms",
            seed, ms, NX, NZ, CELL,
            tOro / 1e6, tW / 1e6, tBP / 1e6, tAuth / 1e6));
        if (false) System.out.println("[Landform] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
