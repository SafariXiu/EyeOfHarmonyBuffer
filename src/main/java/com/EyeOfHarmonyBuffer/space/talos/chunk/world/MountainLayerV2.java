package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 山层（过程驱动，替换 DLA）：抬升场 + 河道下切（流水侵蚀）+ 权威权重。
 *
 * 架构与 RelaxedClimate 同款：按种子离线求解一次（后台线程，~1-3s），缓存粗网格，
 * 运行时双线性查询。**每格域 = BELT_CELL_X(50km) × CARVE_DOMAIN_Z(200km)**；X 无限、查询不折叠坐标。
 *
 * 输出：
 *   uplift(x,z) —— 山带抬升量（blocks，已侵蚀；叠加在 plain 之上）
 *   auth(x,z)   —— 山层权威权重 w [0,1]（山带核心 1 → 边界 0）
 *   slope01     —— |∇uplift| / 0.10，供块级细节与雪-岩判定
 *
 * 合成（由 ChunkProviderTalos2 完成）：
 *   h = plain + (1-w)*mtnComp + w*uplift        // plain/mtnComp 来自 V2TerrainGen 分解
 *
 * 侵蚀模型：D8 汇水面积 + 河道下切 h -= K·A^0.55·S。
 *   关键：下切按汇水面积开闸（aGate0..aGate1）——山脊/峰顶（A≈1）几乎不削，
 *   所以峰高 ≈ 抬升场设计值，可被 axialAmp 直接控制；谷地则被下切压低 → 底盘更低。
 */
public final class MountainLayerV2 {

    private MountainLayerV2() {}

    /** 粗网格分辨率（blocks，250m）。 */
    public static final int CELL = 250;
    /**
     * **山带格**（X 方向步长，blocks）。
     *
     * C1 世界 = 无限平面：X 无限；Z 也分格（山带按 Z 格子布点，见下面的 BELT_CELL_Z）。
     * 山带按 X 格子确定性布点、并**完全落在格内**（见 {@link #BELT_X_FIT}），
     * 于是"每格独立求解"与"全域一起求解"结果一致（实验 P92 验证下切是严格局地的）。
     */
    public static final int BELT_CELL_X = 50_000;
    /** Z 方向山带格（无限平面 → Z 必须分格，否则远离原点的 z 上根本没有山带）。 */
    public static final int BELT_CELL_Z = 50_000;
    /**
     * 每格网格尺寸（格）：X = BELT_CELL_X / CELL = 200 格；Z = CARVE_DOMAIN_Z / CELL = 800 行。
     *
     * **Z 不能按 BELT_CELL_Z 派生（= 200）。** 看上去 bilinear 只读 j ≤ NZ、其余 600 行"白算"，
     * 但那 600 行是 {@link #carve}（Priority-Flood 填洼 + D8 汇水面积累积）的**计算域**：
     * 平地上的水顺 ε 梯度全部汇入山带，域一大一小，汇水面积 acc 就不同 → 下切量不同 → 山高不同。
     * 实测（探针 P170，belt 格 (0,5)，seed=1022228679）：把 NZ 砍成 200 + Z halo 后，
     *   auth 逐位一致（主循环是逐点纯函数），
     *   但 uplift 在深内部仍有 0.51% 的格子变化（最大 0.55 块）、格边界行最大差 22.57 块；
     *   把 Tune.carveEnabled 关掉再比，uplift 差异**全部归零** → 差异 100% 来自下切域变小。
     * 即"4 倍白算"的判断不成立，这里保留 800 行。
     */
    public static final int NX = BELT_CELL_X / CELL;
    /**
     * 下切计算域的高度（blocks）。**这不是"纬度周长"，也不是从 BELT_CELL_Z 派生的量**：
     * {@link #carve}（Priority-Flood 填洼 + D8 汇水面积累积）是**全局算法**，域的大小会改变 acc，
     * 从而改变下切量。上面 49~56 行给了实测证据（P170）。
     *
     * 提成具名常量只是为了让"这个 200_000 从哪来"可检索；**不要**把它改成 BELT_CELL_Z。
     */
    public static final int CARVE_DOMAIN_Z = 200_000;
    public static final int NZ = CARVE_DOMAIN_Z / CELL;
    /** 山带在 X/Z 方向的落位上限：距格角不超过此值（留出 ≥25km 的下切 margin）。 */
    private static final double BELT_X_FIT = 18_750.0;

    /** 每格山带数量上限（历史：旧版在"全域 400k×200k"上放 5 条；现值按每格 50km×200km 折算）。 */
    private static final int BELTS_PER_CELL = 3;

    /**
     * 调参入口：默认值即生产参数。
     * 探针可改写后调用 {@link #clearCache()} 重新求解，用于快速扫参。
     */
    public static final class Tune {
        /** 下切预算（blocks）：河道满额时的下切量。 */
        public static double carve = 50.0;
        /** 下切闸门（汇水面积，单位 250m 格）：A ≤ aGateLo 完全不切，A ≥ aGateHi 满额。 */
        public static double aGateLo = 100.0, aGateHi = 4000.0;
        /** 下切不得超过本格抬升的比例（谷底不穿底盘）。 */
        public static double carveMaxFrac = 0.60;
        /** 下切后的热力平滑轮数 / 系数。 */
        public static int smoothPass = 2;
        public static double thermal = 0.35;
        /** 峰化曲线指数：u' = umax·(u/umax)^γ，γ&gt;1 压中段、保顶部。 */
        public static double peakifyGamma = 1.35;
        /** 山带包络基础抬升范围（底盘）。 */
        public static double ampLo = 4.0, ampHi = 10.0;
        /** 中尺度脊线纹理振幅（blocks）：0.5~3km ridged 噪声，山带内部起伏的主来源。 */
        public static double midAmp = 450.0;
        /** 纹理锐化指数：tex^p，p&gt;1 → 大部分区域压低、山脊变窄变尖。 */
        public static double texPow = 2.5;
        /** 主脊轴额外抬升范围（峰顶高度）。 */
        public static double axialLo = 60.0, axialHi = 85.0;
        /** 下切用的包络下限：env ≥ envFloor 时满额生效（env 在坡面上衰减很快）。 */
        public static double envFloor = 0.25;
        /** 底盘下压量（× belt 包络，可为负值以压低山带底盘）。 */
        public static double floorDrop = 18.0;
        /** 打印求解诊断。 */
        public static boolean debug = false;
        /** 下切总开关（诊断用）。 */
        public static boolean carveEnabled = true;
    }

    /** 解缓存：key = (seed, cellX, cellZ)。每个非空解 = 3×NX×NZ float ≈ 1.92 MB。 */
    private static final ConcurrentHashMap<Long, Layer> CACHE =
        new ConcurrentHashMap<Long, Layer>();
    /**
     * 缓存上限（格数）。
     *
     * **必须 ≥ "解一个 LandformField 瓦片" 的邻域大小**：LandformField.TILE_X = 100k
     * = 2 个 belt 格，TILE_Z = 50k = 1 个 belt 格，但它要取 halo（i=-1..NX、j=-1..NZ），
     * 于是横跨 **4 个 belt 列 × 3 个 belt 行 = 12 格**，外加邻瓦片共享的 2 列。
     * 旧值 6 < 工作集 → 反复重解（探针 P168 实测：2.4k 次/4 瓦片、单瓦片 800+ 次）。
     * 16 = 一个瓦片（12）+ 邻瓦片共享列（4）余量；无山带的格共享 {@link #ZERO} 不占内存，
     * 故最坏 ~16×1.92 MB ≈ 31 MB。
     */
    private static final int CACHE_LIMIT = 16;

    /**
     * 诊断计数器（探针 P168 用，生产恒定只加两个 long，无行为影响）：
     * solve() 的调用次数与累计耗时。用于验证"解一个 LandformField 瓦片
     * （100km×50km）到底触发了多少次造山带求解"——理论上只需数十次，
     * 若出现上万次即为缓存反复失效（thrashing）。
     */
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_COUNT =
        new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_NANOS =
        new java.util.concurrent.atomic.AtomicLong();

    /**
     * **求解硬上限（安全阀，不是优化）**。0 = 不限（生产默认，行为与以前逐位一致）。
     *
     * 为什么加：第 4 次"进程跑飞"（探针 P209 的 period 段）就发生在本层 ——
     * 6,000 次采样触发了 7,165 次 MountainLayerV2 求解（≈1.19 次/查询，等于每次查询都重建），
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

    /** 当前求解的种子（供纹理与世界种子对齐；仅在 solve() 期间使用）。 */
    private static int SOLVE_SEED = 0;

    /** 山带格缓存键。**唯一实现见 {@link WindowKey}** —— 旧写法把 int 种子截成 24 位，
     *  同一个式子当时在本层与 RelaxedClimate/LandformField/V2BiomeField 各有一份。
     *  注意 {@code EMPTY_CELLS}（"该格无山带"）用的是同一个键空间，改键时两者必须同步。 */
    private static long cellKey(int seed, int cellX, int cellZ) {
        return WindowKey.of(seed, cellX, cellZ);
    }

    /** 一条山带。 */
    private static final class Belt {
        double cx, cz;          // 中心（域内绝对坐标）
        double ca, sa;          // 走向单位向量
        double halfL, halfW;    // 半长 / 半宽
        double amp;             // 山带包络基础抬升
        double axialAmp;        // 主脊轴额外抬升
        double meanderAmp;      // 主脊蜿蜒幅度
        double meanderFreq;
        double peakPhase1, peakPhase2;
    }

    /** 一个种子的解。 */
    private static final class Layer {
        final float[] uplift = new float[NX * NZ];
        final float[] auth = new float[NX * NZ];
        /** 坡度场（|∇uplift| / 0.10，0..1），供块级细节与雪-岩判定。 */
        final float[] slope = new float[NX * NZ];
        /**
         * 最近一次被命中的时间（**仅供淘汰排序，不参与任何计算**）。
         * volatile 只为多线程下的原子/可见；无竞争写，热点代价可忽略。
         */
        volatile long lastUse = System.nanoTime();
    }

    /**
     * 无山带格的共享解（全零）。
     *
     * 若某格 {@link #layout} 一条山带都布不下（belts.length == 0），solve() 的结果**恒为全零**：
     * 主循环里 sumW = 0 → auth = 0、uplift = 0、env = 0；carve 中 h 全 0 → 每格走
     * `v <= 0 → tmp[k] = v` 分支原样写回、平滑因 env = 0 全部跳过；peakify（umax = 0）与
     * slope 同理得 0。故直接返回这一个共享实例是**逐位等价**的，同时省掉
     * 1.92 MB/格 的内存与填洼+排序+D8 的 ~10ms。
     *
     * lastUse 恒为最小值 → 缓存满时第一个被淘汰（重解它只需一次 layout，~0.1ms）。
     */
    private static final Layer ZERO = new Layer();

    static {
        ZERO.lastUse = Long.MIN_VALUE;
    }

    /**
     * "该格没有山带" 的备忘（key = 同 {@link #cellKey}）。
     *
     * 无山带格**不进 CACHE**：CACHE 的槽位要留给真正占 1.92 MB 的解。
     * 但也不能每次都重跑 layout()（400 次尝试 + OrographyField 采样 ≈0.1~0.2 ms，
     * 实测 P168：不备忘时单瓦片会被动重解 400+ 次无山带格）。
     * 备忘满了整体清空（重建代价 = 最坏重跑一次 layout，可忽略）。
     */
    private static final ConcurrentHashMap<Long, Boolean> EMPTY_CELLS =
        new ConcurrentHashMap<Long, Boolean>();
    private static final int EMPTY_LIMIT = 512;

    public static boolean isEnabled() {
        return V2TerrainConfigSection.mountainV2Enabled;
    }

    /** 清空解缓存（探针扫参用；含"无山带格"备忘 —— Tune 改动会改变 layout 结果）。 */
    public static void clearCache() {
        CACHE.clear();
        EMPTY_CELLS.clear();
    }

    /** 后台预热：预解原点所在的山带格（其余格按需惰性求解）。 */
    public static void ensure(int worldSeedInt) {
        if (!isEnabled()) {
            return;
        }
        layer(worldSeedInt, 0, 0);
    }

    /**
     * 淘汰"最久没被用过"的那格（近似 LRU）。
     *
     * 旧写法是删迭代器吐出的第一个 key（= 任意格），这在 LandformField 的行扫描下**必然出错**：
     * 一个 belt 列的"边缘列"（i=-1 与 i=NX 那两列）每行只被访问 1 次，而主体列每行被访问
     * 200 次；任意淘汰会先把主体格踢出去 → 下一行再来 200 次未命中 → 每行重解 4 格。
     */
    private static void evictOldest() {
        Long worst = null;
        long worstUse = Long.MAX_VALUE;
        for (java.util.Map.Entry<Long, Layer> e : CACHE.entrySet()) {
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

    /** 取某格的解（不存在则求解，带容量保护）。 */
    private static Layer layer(int worldSeedInt, int cellX, int cellZ) {
        if (!isEnabled()) {
            return null;
        }
        long key = cellKey(worldSeedInt, cellX, cellZ);
        Layer l = CACHE.get(key);
        if (l != null) {
            l.lastUse = System.nanoTime();
            return l;
        }
        if (EMPTY_CELLS.containsKey(key)) {
            return ZERO;   // 已知该格无山带：解恒为全零，不必再 layout
        }
        if (CACHE.size() >= CACHE_LIMIT) {
            evictOldest();
        }
        final int cx = cellX, cz = cellZ;
        Layer solved = CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, cx, cz));
        if (solved == ZERO) {
            // 全零解不占 CACHE 槽位（否则 16 个槽会被 16 MB 的零数组占满，真解反而被淘汰）
            CACHE.remove(key);
            if (EMPTY_CELLS.size() >= EMPTY_LIMIT) {
                EMPTY_CELLS.clear();
            }
            EMPTY_CELLS.put(key, Boolean.TRUE);
            return ZERO;
        }
        solved.lastUse = System.nanoTime();
        return solved;
    }

    /** 点所在的山带格索引。 */
    public static int cellOfX(int x) {
        return Math.floorDiv(x, BELT_CELL_X);
    }

    public static int cellOfZ(int z) {
        return Math.floorDiv(z, BELT_CELL_Z);
    }

    // ==================== 查询 ====================

    /** 山带抬升（blocks，已侵蚀；山带外 0）。 */
    public static double uplift(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt, cellOfX(x), cellOfZ(z));
        return l == null ? 0.0 : bilinear(l.uplift, x, z);
    }

    /** 山层权威权重 w [0,1]。 */
    public static double auth(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt, cellOfX(x), cellOfZ(z));
        return l == null ? 0.0 : bilinear(l.auth, x, z);
    }

    /** 坡度 [0,1]（0=平，1≈陡坡）。 */
    public static double slope01(int x, int z, int worldSeedInt) {
        Layer l = layer(worldSeedInt, cellOfX(x), cellOfZ(z));
        return l == null ? 0.0 : bilinear(l.slope, x, z);
    }

    /**
     * 一次取齐 auth/uplift/slope（热路径用：避免三次查格 + 三次插值）。
     * out[0]=auth, out[1]=uplift, out[2]=slope。
     */
    public static void sample(int x, int z, int worldSeedInt, double[] out) {
        Layer l = layer(worldSeedInt, cellOfX(x), cellOfZ(z));
        if (l == null) {
            out[0] = out[1] = out[2] = 0.0;
            return;
        }
        out[0] = bilinear(l.auth, x, z);
        out[1] = bilinear(l.uplift, x, z);
        out[2] = bilinear(l.slope, x, z);
    }

    /**
     * 格内双线性插值：X 用格内局部坐标（clamp 到 [0, NX-1]）；Z 夹到 [0, NZ-1]。
     *
     * 注：fz < 0（每格最靠下的半个格）时 j 会被夹到 0，而 tz 仍是原值 → 取的是 (0,1) 行
     * 而不是 (-1,0) 行。这是**既有行为**，修它需要加 Z halo，而 halo 会改变 carve 域
     * （见 NZ 的说明），所以保持原样。
     */
    private static double bilinear(float[] g, int wx, int wz) {
        int cellX = cellOfX(wx), cellZ = cellOfZ(wz);
        double fx = (wx - cellX * (double) BELT_CELL_X) / CELL - 0.5;
        double fz = (wz - cellZ * (double) BELT_CELL_Z) / CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        // 越界只可能出现在**格首半格**（fx ∈ [-0.5, 0) → i = -1，fz 同理）。
        // 旧写法把 i 夹到 0 却**保留 tx = 0.5**，于是算成 (列0, 列1) 的 50/50 平均 ——
        // 与 fx = +0.5 的取值**逐位相同**，等于每 50km 造一条 250 格宽的"重复带"
        // （违反"X 无周期"契约；实测：x 与 x+250 逐位相同的非零点占 0.46% ≈ 条带面积 0.5%）。
        // 夹取时把比例一起归位（取边界那一格的值），才是"到边界停住"的正确语义。
        // 注意：这只改**查询插值**，不动 solve/carve 的网格与 D8 汇水域 → 不属于 C 那类必须批准的改动。
        if (i < 0) {
            i = 0;
            tx = 0.0;
        } else if (i > NX - 1) {
            i = NX - 1;
            tx = 1.0;
        }
        int i1 = Math.min(NX - 1, i + 1);
        if (j < 0) {
            j = 0;
            tz = 0.0;
        } else if (j > NZ - 1) {
            j = NZ - 1;
            tz = 1.0;
        }
        int j1 = Math.min(NZ - 1, j + 1);
        int r0 = j * NX, r1 = j1 * NX;
        double v00 = g[r0 + i], v10 = g[r0 + i1];
        double v01 = g[r1 + i], v11 = g[r1 + i1];
        return bilerpSmooth(v00, v10, v01, v11, tx, tz);
    }

    /**
     * ★ §7725：<b>smoothstep 加权的双线性插值</b>（替换裸 bilerp）。
     *
     * <p><b>为什么</b>：裸双线性在<b>每个格的【对角线】上二阶导不连续</b>
     * （{@code d2/dxdz} 跳变），相邻格的折痕连成<b>长直线</b> —— 在
     * {@code /talosmap} 图上表现为「很平滑但明显是解析式的斜线」，
     * 而它【会】影响高度（本类的 auth/uplift 直接进地形）。
     *
     * <p>改用 {@code smoothstep} 权重后 {@code d/dx} 与 {@code d/dz} 在格边界连续
     * （C1），折痕消失。代价：每格 +6 次乘法，<b>零新增查表</b>。
     *
     * <p>注意：smoothstep 只在 {@code t ∈ [0,1]} 上有定义，调用方已经保证
     * {@code tx/tz} 落在 [0,1]（越界分支会把比例归位到 0 或 1）。
     */
    static double bilerpSmooth(double v00, double v10, double v01, double v11,
                               double tx, double tz) {
        double sx = tx * tx * (3.0 - 2.0 * tx);
        double sz = tz * tz * (3.0 - 2.0 * tz);
        return (v00 * (1 - sx) + v10 * sx) * (1 - sz) + (v01 * (1 - sx) + v11 * sx) * sz;
    }

    // ==================== 求解 ====================

    /** 安全阀共用检查（0 = 不限，生产默认）。 */
    private static void checkSolveCap(long sn, int cx, int cz) {
        if (SOLVE_HARD_CAP > 0 && sn > SOLVE_HARD_CAP) {
            throw new IllegalStateException("MountainLayerV2 求解超过硬上限 SOLVE_HARD_CAP=" + SOLVE_HARD_CAP
                + "（已 " + sn + " 次，累计 " + (SOLVE_NANOS.get() / 1_000_000_000L) + "s，当前格=("
                + cx + "," + cz + ")）。几乎一定是缓存抖动：工作集 > 容量。请改用 tile 主序采样。");
        }
        if (SOLVE_WARN_EVERY > 0 && sn % SOLVE_WARN_EVERY == 0) {
            System.out.println("[MountainLayerV2] 求解已达 " + sn + " 次，累计 "
                + (SOLVE_NANOS.get() / 1_000_000_000L) + "s，当前格=(" + cx + "," + cz + ")");
        }
    }

    private static Layer solve(int seed, int cellX, int cellZ) {
        long t0 = System.nanoTime();
        long sn = SOLVE_COUNT.incrementAndGet();
        checkSolveCap(sn, cellX, cellZ);
        SOLVE_SEED = seed;
        Belt[] belts = layout(seed, cellX, cellZ);
        if (belts.length == 0) {
            // 该格一条山带都没有 → 解恒为全零（见 ZERO 的说明），跳过整片 160k 格的计算
            SOLVE_NANOS.addAndGet(System.nanoTime() - t0);
            System.out.println("[MountainV2] seed=" + seed + " solved in "
                + ((System.nanoTime() - t0) / 1_000_000) + "ms  belts=0 (empty)");
            return ZERO;
        }
        double originX = cellX * (double) BELT_CELL_X;
        double originZ = cellZ * (double) BELT_CELL_Z;

        Layer layer = new Layer();
        float[] env = new float[NX * NZ];       // 山带包络（0..1），下切只在包络内生效
        // 1) 抬升场 + 权威场
        for (int j = 0; j < NZ; j++) {
            double z = originZ + j * CELL + CELL * 0.5;
            for (int i = 0; i < NX; i++) {
                double x = originX + i * CELL + CELL * 0.5;
                double sumW = 0.0, sumUp = 0.0, sumEnv = 0.0, maxW = 0.0;
                for (Belt b : belts) {
                    double w = beltWeight(b, x, z);
                    if (w <= 0.0) {
                        continue;
                    }
                    double up = beltUplift(b, x, z, w);
                    sumW += w;
                    sumUp += w * up;
                    sumEnv += w * beltEnvelope(b, x, z);
                    if (w > maxW) {
                        maxW = w;
                    }
                }
                int k = j * NX + i;
                layer.auth[k] = (float) maxW;
                layer.uplift[k] = (float) (sumW > 0 ? sumUp / Math.max(1.0, sumW) : 0.0);
                env[k] = (float) (sumW > 0 ? sumEnv / Math.max(1.0, sumW) : 0.0);
            }
        }
        if (Tune.debug) {
            double mn = 1e9, mx = -1e9, s1 = 0, s2 = 0;
            int c = 0;
            for (int j = 0; j < NZ; j += 2) {
                for (int i = 0; i < NX; i += 2) {
                    double x = originX + i * CELL + CELL * 0.5, z = originZ + j * CELL + CELL * 0.5;
                    for (Belt b : belts) {
                        if (beltEnvelope(b, x, z) < 0.5) continue;
                        double t = ridgeTexture(b, x, z);
                        if (t < mn) mn = t;
                        if (t > mx) mx = t;
                        s1 += t; s2 += t * t; c++;
                    }
                }
            }
            double mm = s1 / c;
            System.out.printf("[tex] n=%d min=%.3f max=%.3f mean=%.3f sd=%.3f  midAmp=%.0f%n",
                c, mn, mx, mm, Math.sqrt(Math.max(0, s2 / c - mm * mm)), Tune.midAmp);
            double vmn = 1e9, vmx = -1e9, v1 = 0, v2 = 0;
            int vc = 0;
            for (int k = 0; k < layer.uplift.length; k++) {
                if (layer.auth[k] < 0.6f) continue;
                double v = layer.uplift[k];
                if (v < vmn) vmn = v;
                if (v > vmx) vmx = v;
                v1 += v; v2 += v * v; vc++;
            }
            double vm = v1 / vc;
            System.out.printf("[pre-carve uplift] n=%d min=%.0f max=%.0f mean=%.0f sd=%.1f%n",
                vc, vmn, vmx, vm, Math.sqrt(Math.max(0, v2 / vc - vm * vm)));
        }
        // 2) 河道下切（汇水面积分档；山脊/峰顶 A≈1 不切 → 峰高 = 抬升场设计值）
        if (Tune.carveEnabled) {
            carve(layer.uplift, env);
        }
        // 2b) 峰化曲线：u' = uMax·(u/uMax)^γ —— 压中段底盘、保顶部，峰更突出
        float umax = 0f;
        for (float v : layer.uplift) {
            if (v > umax) umax = v;
        }
        if (umax > 1f) {
            for (int k = 0; k < layer.uplift.length; k++) {
                float v = layer.uplift[k];
                if (v <= 0f) continue;
                layer.uplift[k] = (float) (umax * Math.pow(v / umax, Tune.peakifyGamma));
            }
        }
        // 3) 坡度场（|∇uplift| / 0.10，0..1）
        //
        // 【双轴环面是有意的，别当缺陷删】（本文件 3 处：这里、{@link #fillSinks}、{@link #lowest}）
        //   · Z 环面：本域 800 行 = CARVE_DOMAIN_Z(200km) 是 belt 间距 BELT_CELL_Z(50km) 的 4 倍，
        //     这是**故意**给 Priority-Flood 填洼 + D8 汇水面积留的计算域：平地上的水顺 ε 梯度
        //     全部汇入山带，域一大一小汇水面积就不同、下切量就不同（P170 实测：把域砍到 200 行
        //     后 uplift 深内部仍有 0.51% 的格子变化、格边界行最大差 22.57 块，关掉下切则差异全归零）。
        //   · X 环面：山带按 X 格子确定性布点且**完全落在格内**（BELT_X_FIT），下切是**严格局地**的
        //     （P92 验证），所以边缘那一圈绕回对边时那里根本没有山带，接了也影响不到任何被画出来的地形。
        // 换句话说：这里的取模不是"把世界接成环"，而是"给一个局部雕刻域选边界条件"。
        for (int j = 0; j < NZ; j++) {
            for (int i = 0; i < NX; i++) {
                int im = ((i - 1) % NX + NX) % NX, ip = (i + 1) % NX;
                int jm = ((j - 1) % NZ + NZ) % NZ, jp = (j + 1) % NZ;
                double gx = (layer.uplift[j * NX + ip] - layer.uplift[j * NX + im]) / (2.0 * CELL);
                double gz = (layer.uplift[jp * NX + i] - layer.uplift[jm * NX + i]) / (2.0 * CELL);
                double s = Math.sqrt(gx * gx + gz * gz) / 0.10;   // 10% 坡度 → 1
                layer.slope[j * NX + i] = (float) (s > 1.0 ? 1.0 : s);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        SOLVE_NANOS.addAndGet(System.nanoTime() - t0);
        System.out.println("[MountainV2] seed=" + seed + " solved in " + ms + "ms  belts=" + belts.length
            + "  upMax=" + String.format("%.0f", umax));
        return layer;
    }

    /** 山带布局：中心尽量落在内陆，沿轴裁剪保证主要在山地上。 */
    /**
     * 一格（{@link #BELT_CELL_X} 宽 × 200k 周长）内的山带布点。
     *
     * 与旧版的区别：旧版把 5 条山带撒在"整个世界域 400k×200k"里（世界必须是有限环面）；
     * 新版按 **X 格子** 确定性布点（格索引进哈希），并强制**整条山带落在格内**
     * （X 方向距格边 ≥25km），于是每格可以独立求解、结果与全域求解一致。
     */
    private static Belt[] layout(int seed, int cellX, int cellZ) {
        Random rng = new Random(seed * 6364136223846793005L + 1442695040888963407L
            + cellX * 0x9E3779B97F4A7C15L + cellZ * 0xC2B2AE3D27D4EB4FL);
        double cellCX = cellX * (double) BELT_CELL_X;
        double cellCZ = cellZ * (double) BELT_CELL_Z;
        Belt[] out = new Belt[BELTS_PER_CELL];
        int made = 0;
        for (int attempt = 0; attempt < 400 && made < BELTS_PER_CELL; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2.0;
            double ca = Math.cos(ang), sa = Math.sin(ang);
            double halfL = 45_000.0 + rng.nextDouble() * 55_000.0;
            double halfW = 8_000.0 + rng.nextDouble() * 7_000.0;      // 略窄 → 更陡
            // 约束：包围盒必须落在格内（X、Z 都要，留 ≥25km 下切 margin）
            double ex = Math.abs(ca) * halfL + Math.abs(sa) * halfW;
            double ez = Math.abs(sa) * halfL + Math.abs(ca) * halfW;
            double fit = BELT_X_FIT;
            if (ex > fit || ez > fit) {
                double s = Math.min(fit / Math.max(1e-6, ex), fit / Math.max(1e-6, ez));
                halfL *= s;
                ex *= s;
                ez *= s;
            }
            double cx = cellCX + (rng.nextDouble() * 2.0 - 1.0) * Math.max(0.0, fit - ex);
            double cz = cellCZ + (rng.nextDouble() * 2.0 - 1.0) * Math.max(0.0, fit - ez);
            OrographyField.OroSample o = OrographyField.sample((int) cx, (int) cz, seed);
            if (!o.isLand || o.coastDist > -20_000.0) {
                continue;
            }
            Belt b = new Belt();
            b.cx = cx;
            b.cz = cz;
            b.ca = ca;
            b.sa = sa;
            b.halfL = halfL;
            b.halfW = halfW;
            b.amp = Tune.ampLo + rng.nextDouble() * (Tune.ampHi - Tune.ampLo);
            b.axialAmp = Tune.axialLo + rng.nextDouble() * (Tune.axialHi - Tune.axialLo);
            b.meanderAmp = 5_000.0 + rng.nextDouble() * 4_000.0;
            b.meanderFreq = 1.0 / (45_000.0 + rng.nextDouble() * 30_000.0);
            b.peakPhase1 = rng.nextDouble() * Math.PI * 2.0;
            b.peakPhase2 = rng.nextDouble() * Math.PI * 2.0;
            // 与已有山带保持距离，避免重叠成一片
            boolean ok = true;
            for (int i = 0; i < made; i++) {
                double dx = out[i].cx - b.cx, dz = out[i].cz - b.cz;
                if (dx * dx + dz * dz < 60_000.0 * 60_000.0) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            out[made++] = b;
        }
        if (made < BELTS_PER_CELL) {
            Belt[] trimmed = new Belt[made];
            System.arraycopy(out, 0, trimmed, 0, made);
            return trimmed;
        }
        return out;
    }

    /** 山带权威权重（含端部 taper、边界过渡）。 */
    private static double beltWeight(Belt b, double x, double z) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = 1.0 - Math.abs(along) / b.halfL;
        if (taper <= 0.0) {
            return 0.0;
        }
        taper = Math.pow(taper, 0.30);
        double belt = Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * taper;
        double meander = b.meanderAmp * TerrainNoise.fbm2DS(seedSalt(b, 0xE31L), along * b.meanderFreq, 0.0, 1.0, 1.0, 2);
        double latA = lat - meander;
        double axial = Math.exp(-Math.pow(latA / (0.10 * b.halfW), 2.0)) * taper;   // 主脊更窄
        double w = belt * 1.6 + axial * 0.8;
        return w > 1.0 ? 1.0 : w;
    }

    /**
     * 中尺度脊线纹理：与世界种子同一套 ridged 场（V2TerrainGen.mountainTexture），
     * 使山层与基础山地的脊线对齐、过渡自然。返回 0..1（谷=0，脊=1）。
     * 这是山带内部 1~4km 尺度起伏的唯一来源——只有它才能造出支脊/次级峰。
     */
    private static double ridgeTexture(Belt b, double x, double z) {
        // 种子必须走 V2TerrainGen.textureSeed()：本方法的**全部意义**就是"和基础山地同一套脊线"，
        // 而裸 SOLVE_SEED 会让这里拿到另一套噪声（见 V2TerrainGen.textureSeed 的注释）。
        return V2TerrainGen.mountainTexture(V2TerrainGen.textureSeed(SOLVE_SEED), x, z);
    }

    /** 山带包络（横向高斯 × 端部 taper，0..1）。 */
    private static double beltEnvelope(Belt b, double x, double z) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = 1.0 - Math.abs(along) / b.halfL;
        if (taper <= 0.0) {
            return 0.0;
        }
        return Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * Math.pow(taper, 0.30);
    }

    /** 山带抬升（含沿脊峰/鞍 + 多尺度噪声）。 */
    private static double beltUplift(Belt b, double x, double z, double w) {
        double dx = x - b.cx, dz = z - b.cz;
        double along = dx * b.ca + dz * b.sa;
        double lat = -dx * b.sa + dz * b.ca;
        double taper = Math.pow(Math.max(0.0, 1.0 - Math.abs(along) / b.halfL), 0.30);
        double belt = Math.exp(-Math.pow(lat / (0.40 * b.halfW), 2.0)) * taper;
        double meander = b.meanderAmp * TerrainNoise.fbm2DS(seedSalt(b, 0xE31L), along * b.meanderFreq, 0.0, 1.0, 1.0, 2);
        double latA = lat - meander;
        double axial = Math.exp(-Math.pow(latA / (0.10 * b.halfW), 2.0)) * taper;
        double tex = ridgeTexture(b, x, z);
        if (Tune.texPow != 1.0) {
            tex = Math.pow(tex, Tune.texPow);
        }
        double mid = Tune.midAmp * tex;
        double n1 = TerrainNoise.fbm2DS(seedSalt(b, 0xE11L), x, z, 1.0 / 45000.0, 1.0, 3) * 8.0;
        double n2 = TerrainNoise.fbm2DS(seedSalt(b, 0xE12L), x, z, 1.0 / 13000.0, 1.0, 3) * 5.0;
        double n3 = TerrainNoise.fbm2DS(seedSalt(b, 0xE13L), x, z, 1.0 / 4200.0, 1.0, 2) * 3.0;
        // 沿脊改为"离散峰列"：周期 ≈8k 的尖峰，每峰高度不同，峰间鞍部接近 0
        double s = along / 4000.0 + b.peakPhase1;
        double bump = Math.pow(Math.max(0.0, Math.cos(s * Math.PI)), 2.0);
        double hvar = 0.55 + 0.45 * Math.sin(along / 19000.0 + b.peakPhase2);
        double mod = 0.10 + 1.70 * bump * hvar;
        double axialAmp = b.axialAmp * mod;
        double beltMod = 1.0 + 0.28 * Math.sin(along / 31000.0 + b.peakPhase2);
        double up = belt * beltMod * (b.amp + n1 * 0.7 + mid)
            + (n1 + n2 + n3) * belt * beltMod
            + axial * axialAmp
            - Tune.floorDrop * belt * beltMod;
        return Math.max(-30.0, up);
    }

    private static long seedSalt(Belt b, long salt) {
        return (long) Double.doubleToLongBits(b.cx * 31.0 + b.cz * 17.0) ^ salt;
    }

    // ==================== 河道下切 ====================

    /**
     * 汇水面积 → 静态下切。
     * 山脊/峰顶 A≈1 → 不切（峰高 = 抬升场设计值）；河道 A 大 → 切到 carve 预算。
     * 下切量再受 belt 包络与本地抬升比例约束，谷底不会穿到 plain 之下。
     */
    private static void carve(float[] h, float[] env) {
        int n = h.length;
        float[] acc = new float[n];
        int[] order = new int[n];
        float[] tmp = new float[n];
        // 1) 填洼（Priority-Flood）：噪声会造出大量小洼地，不填则汇水面积碎片化
        float[] fill = fillSinks(h);
        // 2) 高度降序排序（在填洼面上排，保证排水自高向低单向流动）
        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : fill) { if (v < lo) lo = v; if (v > hi) hi = v; }
        float scale = (hi - lo) < 1e-3f ? 1f : 4095f / (hi - lo);
        int[] cnt = new int[4097];
        for (int k = 0; k < n; k++) cnt[(int) ((fill[k] - lo) * scale)]++;
        int a = 0;
        for (int b = 4095; b >= 0; b--) { int c = cnt[b]; cnt[b] = a; a += c; }
        for (int k = 0; k < n; k++) order[cnt[(int) ((fill[k] - lo) * scale)]++] = k;
        // 3) D8 汇水面积（自高向低累积）
        Arrays.fill(acc, 0f);
        for (int oi = 0; oi < n; oi++) {
            int k = order[oi];
            int lo2 = lowest(k, fill);
            acc[k] += 1f;
            if (lo2 >= 0) acc[lo2] += acc[k];
        }
        // 4) 分档下切：A 小（山脊/峰顶）不切，A 大（河道）切满 carve 预算
        double accMax = 0, cutMax = 0, cutSum = 0;
        int pos = 0;
        for (int k = 0; k < n; k++) {
            double v = h[k];
            if (v <= 0.0) { tmp[k] = (float) v; continue; }
            double g = (acc[k] - Tune.aGateLo) / Math.max(1e-6, Tune.aGateHi - Tune.aGateLo);
            if (g < 0.0) g = 0.0;
            else if (g > 1.0) g = 1.0;
            g = g * g * (3.0 - 2.0 * g);          // smoothstep：只有真河道才切
            double envC = env[k] / Tune.envFloor;
            if (envC > 1.0) envC = 1.0;
            double cut = Tune.carve * g * envC;
            double lim = v * Tune.carveMaxFrac;
            if (cut > lim) cut = lim;
            tmp[k] = (float) (v - cut);
            pos++;
            if (acc[k] > accMax) accMax = acc[k];
            if (cut > cutMax) cutMax = cut;
            cutSum += cut;
        }
        if (Tune.debug) {
            System.out.println("[carve] pos=" + pos + " accMax=" + String.format("%.0f", accMax)
                + " cutMax=" + String.format("%.1f", cutMax)
                + " cutMean=" + String.format("%.1f", cutSum / Math.max(1, pos)));
        }
        // 5) 热力平滑（圆化脊线/坡面，只在包络内）
        for (int p = 0; p < Tune.smoothPass; p++) {
            for (int j = 1; j < NZ - 1; j++) {
                for (int i = 1; i < NX - 1; i++) {
                    int k = j * NX + i;
                    if (env[k] <= 0f) continue;
                    float s = 0.25f * (tmp[k - 1] + tmp[k + 1] + tmp[k - NX] + tmp[k + NX]);
                    tmp[k] = tmp[k] + (float) Tune.thermal * (s - tmp[k]);
                }
            }
        }
        System.arraycopy(tmp, 0, h, 0, n);
    }

    /** 填洼 ε（blocks）：足够小不影响高度，但能消除平地排水死区。 */
    private static final float FILL_EPS = 1e-3f;

    /**
     * Priority-Flood+ε 填洼（Barnes 2014）：从全局最低格起，按高度优先队列外扩，
     * 每格水位 = max(自身高度, 上游水位) → 结果无洼地，D8 排水全程连通。
     */
    private static float[] fillSinks(float[] h) {
        int n = h.length;
        float[] fill = new float[n];
        boolean[] done = new boolean[n];
        int[] heap = new int[n];
        int size = 0;
        int seed = 0;
        float lo = Float.MAX_VALUE;
        for (int k = 0; k < n; k++) {
            if (h[k] < lo) { lo = h[k]; seed = k; }
        }
        fill[seed] = lo;
        done[seed] = true;
        heapPush(heap, size++, seed, fill);
        while (size > 0) {
            int c = heapPop(heap, size--, fill);
            int j = c / NX, i = c % NX;
            for (int dj = -1; dj <= 1; dj++) {
                for (int di = -1; di <= 1; di++) {
                    if (di == 0 && dj == 0) continue;
                    int jj = ((j + dj) % NZ + NZ) % NZ;   // 环面见坡度场上方说明（有意）
                    int ii = ((i + di) % NX + NX) % NX;
                    int kk = jj * NX + ii;
                    if (done[kk]) continue;
                    done[kk] = true;
                    // +ε：填洼区每远离溢流点升高 ε，保证 D8 排水无平地死区
                    float lvl = fill[c] + FILL_EPS;
                    fill[kk] = h[kk] > lvl ? h[kk] : lvl;
                    heapPush(heap, size++, kk, fill);
                }
            }
        }
        return fill;
    }

    private static void heapPush(int[] heap, int size, int v, float[] key) {
        heap[size] = v;
        int i = size;
        while (i > 0) {
            int p = (i - 1) >> 1;
            if (key[heap[p]] <= key[heap[i]]) {
                break;
            }
            int t = heap[p]; heap[p] = heap[i]; heap[i] = t;
            i = p;
        }
    }

    private static int heapPop(int[] heap, int size, float[] key) {
        int top = heap[0];
        heap[0] = heap[size - 1];
        int i = 0, m = size - 1;
        while (true) {
            int l = 2 * i + 1, r = l + 1, s = i;
            if (l < m && key[heap[l]] < key[heap[s]]) s = l;
            if (r < m && key[heap[r]] < key[heap[s]]) s = r;
            if (s == i) {
                break;
            }
            int t = heap[i]; heap[i] = heap[s]; heap[s] = t;
            i = s;
        }
        return top;
    }

    private static int lowest(int k, float[] h) {
        int j = k / NX, i = k % NX;
        int best = -1;
        float bh = h[k];
        for (int dj = -1; dj <= 1; dj++) {
            for (int di = -1; di <= 1; di++) {
                if (di == 0 && dj == 0) continue;
                int jj = ((j + dj) % NZ + NZ) % NZ;   // 环面见坡度场上方说明（有意）
                int ii = ((i + di) % NX + NX) % NX;
                int kk = jj * NX + ii;
                if (h[kk] < bh) { bh = h[kk]; best = kk; }
            }
        }
        return best;
    }
}
