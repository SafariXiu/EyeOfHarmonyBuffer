package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
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
    public static final int TILE_X = 100_000;

    public static final int TILE_Z = 50_000;

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

    public static Sample sample(int x, int z, int worldSeedInt) {
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
        int i1 = i + 1, j1 = j + 1;
        int k00 = (j + 1) * SX + (i + 1), k10 = (j + 1) * SX + (i1 + 1), k01 = (j1 + 1) * SX + (i + 1), k11 = (j1 + 1) * SX + (i1 + 1);
        s.low = bilerp(f.low, k00, k10, k01, k11, tx, tz);
        s.hill = bilerp(f.hill, k00, k10, k01, k11, tx, tz);
        s.plat = bilerp(f.plat, k00, k10, k01, k11, tx, tz);
        s.mtn = bilerp(f.mtn, k00, k10, k01, k11, tx, tz);
        s.peak = bilerp(f.peak, k00, k10, k01, k11, tx, tz);
        s.mtnAmt = bilerp(f.mtnAmt, k00, k10, k01, k11, tx, tz);
        s.h0 = bilerp(f.h0, k00, k10, k01, k11, tx, tz);
        return s;
    }

    private static double bilerp(float[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = g[k00], v10 = g[k10], v01 = g[k01], v11 = g[k11];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
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
        int n = SX * SZ;
        Field f = new Field();
        double[] w = new double[5];
        double[] bp = new double[2];

        for (int j = -1; j <= NZ; j++) {
            int z = originZ + j * CELL + CELL / 2;
            for (int i = -1; i <= NX; i++) {
                int x = originX + i * CELL + CELL / 2;
                int k = (j + 1) * SX + (i + 1);
                OrographyField.OroSample o = OrographyField.sample(x, z, seed);
                computeWeights(o.beltMask01, o.relief01, o.elevation01, w);

                // 中性 bias 的骨架高度（**含中尺度纹理**）→ 真实的"相对平原抬升量"
                V2TerrainGen.basePlainFromWeights(w, x, z, seed, SEA_LEVEL, 0.5, 0.5, bp);
                double mtnComp0 = bp[0] > bp[1] ? bp[0] - bp[1] : 0.0;
                double auth = MountainLayerV2.auth(x, z, seed);
                double uplift = MountainLayerV2.uplift(x, z, seed);
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
        System.out.println("[Landform] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
