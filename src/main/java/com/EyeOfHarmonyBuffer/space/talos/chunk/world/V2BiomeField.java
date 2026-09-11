package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.WindowKey;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 群系 LUT（L1c 离线求解 + 空间平滑，T2.2）。
 *
 * **为什么做成 LUT**：
 *   1. 性能：群系选择原本每列都要采样气候场（4 次 LUT + 风 + 4 次 elevation01 + SST），
 *      一个区块 256 列就是几百微秒。改为按种子离线求解 **250m** 网格（一个窗口 400×200 = 8 万格，
 *      约 0.3s，跑在预热线程），运行时每列只做一次查表 + 双线性插值（无分配，~20ns）。
 *   2. 平滑：对 **16 通道权重场**做可分离盒滤波（去掉孤立小斑块），并让高度倾向
 *      （bias/scale）在边界连续；解决"雪山紧邻沙漠"的硬边问题。
 *   3. 连续边界：LUT 存的是**每格 16 通道权重**（不是每格 argmax 的 kind），查询时对
 *      4 个格子的权重做双线性插值再 argmax → 群系边界是连续场的等值线。
 *      旧实现存 kind + 取最近格，边界被量化到当时的 1km 网格上，呈现 1000 格长的直线段与 90° 直角
 *      （现行 CELL = 250m，且改成插值权重后边界是连续场的等值线）。
 *   4. 小尺度抖动：查询群系种类前把坐标按**分形值噪声**位移（JITTER_AMP/LEN/OCT；
 *      两个轴都可无界，见 JT_* 常量与 F-1 修复）；
 *      让边界在 chunk 尺度上呈现自然起伏（实测边界周长 +17%、群系占比漂移 <0.01pp）。
 *      只抖种类、不抖 bias/scale → 高度场不受影响。
 *
 * 与 {@link V2BiomeSelect} 的关系：求解时逐格调用 {@code accumulateWeights}，
 * 口径完全一致；运行时只查表。
 */
public final class V2BiomeField {

    /** 网格分辨率（blocks，250m）。 */
    public static final int CELL = 250;

    /** 窗口宽度（blocks）：与 LandformField/气候一致，按绝对坐标窗口求解。 */
    public static final int TILE_X = 100_000;

    public static final int TILE_Z = 50_000;

    /**
     * 暴露区格数（不含 halo）= 400 × 200。
     * **必须从 TILE_* 派生**：旧写法硬编码 400_000 / 200_000 = 1600×800，而 sample() 只读
     * i ≤ 399 / j ≤ 199 —— 白算 16 倍（单次 solve 峰值分配 ~246MB，CACHE 打满 ~416MB）。
     */
    public static final int NX = TILE_X / CELL;

    public static final int NZ = TILE_Z / CELL;

    /**
     * 含 1 格 halo 的数组宽高：solve() 计算 i ∈ [-1, NX]、j ∈ [-1, NZ]，索引 (j+1)*SX + (i+1)。
     *
     * halo 是给 sample() 用的：旧写法对索引取模，i = -1 绕到 NX-1（100km 外那一列）→
     * 每 100km 一条群系接缝。所以"砍 NX"必须连 halo 一起做，否则接缝更糟。
     */
    public static final int SX = NX + 2;

    public static final int SZ = NZ + 2;

    /** 平滑半径（格）：2 → 5×5 邻域 ≈ 5km。 */
    public static int BLUR_R = 1;
    /** 权重场平滑遍数（半径 1 + 2 遍 ≈ 2km 有效半径，边界不被拉出长裙边）。 */
    public static int BLUR_PASSES = 2;

    private static final int KINDS = V2BiomeSelect.KINDS;

    /** 瓦片缓存键。**唯一实现见 {@link WindowKey}** —— 旧写法把 int 种子截成 24 位，
     *  同一个式子当时在本层与 RelaxedClimate/LandformField/MountainLayerV2 各有一份。 */
    private static long tileKey(int seed, int tileX, int tileZ) {
        return WindowKey.of(seed, tileX, tileZ);
    }

    private static final ConcurrentHashMap<Long, Field> CACHE =
        new ConcurrentHashMap<Long, Field>();

    /**
     * 一个种子的解（各 8 万格）。
     *
     * bias/scale 在**全定义域**都有值（海格按"假如是陆地"算），保证双线性插值在海岸处平滑；
     * 群系种类分陆/海两套：查询时用**精确海陆判定**取用，于是海岸线不会被 LUT 网格量化。
     */
    private static final class Field {
        final float[] bias = new float[SX * SZ];
        final float[] scale = new float[SX * SZ];
        /** 16 通道陆地权重（×255 字节）：查询时双线性插值再 argmax → 边界连续。 */
        final byte[] chan = new byte[SX * SZ * KINDS];
        /** 海洋候选：SHELF 权重（×255）。OCEAN/SHELF 按双线性插值后的 0.5 阈值判定。 */
        final byte[] shelfW = new byte[SX * SZ];
    }

    /** 查询结果（线程本地复用 → 热路径零分配）。 */
    public static final class Sample {
        public V2BiomeSelect.Kind kind;
        public double bias, scale;
        public boolean land;
    }

    private static final ThreadLocal<Sample> TL = new ThreadLocal<Sample>() {
        @Override
        protected Sample initialValue() {
            return new Sample();
        }
    };

    // ==== 小尺度边界抖动（chunk 级不规则度）====
    /** 边界位移幅度（blocks，峰值；实际 rms ≈ 0.22×）。0 = 关闭。 */
    public static double JITTER_AMP = 50.0;
    /**
     * 主波长（blocks）。**现行 100**（八度波长 100 / 50 / 25）。
     * 历史：当时要求它整除世界周期（400000 与 200000）以免环面接缝不连续；
     * 现在两轴都不折叠（X 与 Z 都走"无界"），这条约束已不存在。
     */
    public static final double JITTER_LEN = 100.0;
    /** 八度数（波长 JITTER_LEN、/2、/4 = 100 / 50 / 25 blocks）。 */
    public static final int JITTER_OCT = 3;
    /** 各八度的 lattice 尺寸与归一化幅度（类加载时算好 → 热路径无除法）。 */
    /**
     * 抖动噪声 **Z 方向**是否无界。
     *
     * **F-1 修复**：这里以前没有这个开关 —— `valueNoise` 对 `pz` 无条件取模
     * （`z0 = ((zi % pz) + pz) % pz`，而 px 是有 `px > 0` 保护的），于是
     * `JT_PZ[o] = round(200_000/len)` 让**群系抖动在 Z 上每 200,000 格逐位复读一次**，
     * 违反「Z_CYCLE 只重复气候、地形/群系不得复读」的契约。
     * 现在与 X 同款：传 0 ⇒ 不折叠。
     *
     * 注：X 方向的同类开关 `JT_X_UNBOUNDED` 与数组 `JT_PX` 已删除 —— 它们恒为 true/恒不被读，
     * 调用处现在直接传 0（见 jitter()）。
     */
    private static final boolean JT_Z_UNBOUNDED = true;
    private static final int[] JT_PZ = new int[JITTER_OCT];
    private static final double[] JT_INV = new double[JITTER_OCT];
    private static final double[] JT_AMP = new double[JITTER_OCT];
    static {
        double len = JITTER_LEN, amp = 1.0, norm = 0.0;
        for (int o = 0; o < JITTER_OCT; o++) {
            JT_INV[o] = 1.0 / len;
            JT_PZ[o] = (int) Math.round(200_000.0 / len);
            JT_AMP[o] = amp;
            norm += amp;
            amp *= 0.5;
            len *= 0.5;
        }
        for (int o = 0; o < JITTER_OCT; o++) {
            JT_AMP[o] /= norm;
        }
    }

    /** 缓存的 Kind 数组（避免热路径每次 Kind.values() 分配）。 */
    private static final V2BiomeSelect.Kind[] KIND_ARR = V2BiomeSelect.Kind.values();

    private V2BiomeField() {}

    /** 后台预热（WorldEvent.Load → ClimatePreheat）。 */
    public static void ensure(int worldSeedInt) {
        field(worldSeedInt, 0, 0);
    }

    /** 诊断计数器（零行为变化）：solve() 调用次数与累计耗时。 */
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_COUNT =
        new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong SOLVE_NANOS =
        new java.util.concurrent.atomic.AtomicLong();

    /** 清零诊断计数器。 */
    public static void resetStats() {
        SOLVE_COUNT.set(0L);
        SOLVE_NANOS.set(0L);
    }

    /**
     * **求解硬上限（安全阀，不是优化）**。0 = 不限（生产默认，行为与以前逐位一致）。
     *
     * 第 4 次"进程跑飞"（探针 P209 period 段）在本层也有份：6,000 次采样触发了
     * 1,853 次 V2BiomeField 求解。成因与气候层同源：采样在瓦片上跳 → 工作集 > 缓存容量。
     */
    public static int SOLVE_HARD_CAP = 0;
    /** 每求解这么多次就打一行警告（0 = 不打）。 */
    public static int SOLVE_WARN_EVERY = 0;

    public static void clearCache() {
        CACHE.clear();
    }

    private static Field field(int worldSeedInt, int tileX, int tileZ) {
        long key = tileKey(worldSeedInt, tileX, tileZ);
        Field f = CACHE.get(key);
        if (f != null) {
            return f;
        }
        if (CACHE.size() > 12) {
            java.util.Iterator<Long> it = CACHE.keySet().iterator();
            if (it.hasNext()) {
                CACHE.remove(it.next());
            }
        }
        final int tx = tileX, tz = tileZ;
        return CACHE.computeIfAbsent(key, k -> solve(worldSeedInt, tx, tz));
    }

    /** 点所在的窗口索引。 */
    public static int tileOfX(int x) {
        return Math.floorDiv(x, TILE_X);
    }

    public static int tileOfZ(int z) {
        return Math.floorDiv(z, TILE_Z);
    }

    /** 热路径查询（调用方已知精确海陆状态时用，省一次噪声采样）。 */
    public static Sample sample(int x, int z, int worldSeedInt, boolean isLand) {
        int tile = tileOfX(x), tileZ = tileOfZ(z);
        Field f = field(worldSeedInt, tile, tileZ);
        Sample s = TL.get();
        s.land = isLand;
        double fx = (x - tile * (double) TILE_X) / (double) CELL - 0.5;
        double fz = (z - tileZ * (double) TILE_Z) / (double) CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        // clamp 到 halo 范围（fx ∈ [-0.5, NX-0.5] → i ∈ [-1, NX-1]），**不再取模**
        i = i < -1 ? -1 : (i > NX ? NX : i);
        j = j < -1 ? -1 : (j > NZ ? NZ : j);
        int k00 = (j + 1) * SX + (i + 1);
        int k10 = k00 + 1, k01 = k00 + SX, k11 = k01 + 1;
        s.bias = bilerp(f.bias, k00, k10, k01, k11, tx, tz);
        s.scale = bilerp(f.scale, k00, k10, k01, k11, tx, tz);
        if (isLand) {
            // 陆地：16 通道双线性插值 + argmax（连续边界，无 1km 台阶）
            if (JITTER_AMP > 0.0) {
                // 小尺度域扭曲：只作用于**群系种类**（bias/scale 保持平滑 → 高度场不受影响）
                double dx = JITTER_AMP * jitter(x, z, worldSeedInt, 0) / (double) CELL;
                double dz = JITTER_AMP * jitter(x, z, worldSeedInt, 1) / (double) CELL;
                // 注：jitter 用绝对 x、X 方向不折叠（px<=0）→ 边界抖动不随窗口重复
                double jfx = fx + dx, jfz = fz + dz;
                int ji = (int) Math.floor(jfx), jj = (int) Math.floor(jfz);
                tx = jfx - ji;
                tz = jfz - jj;
                // |jitter 位移| ≤ JITTER_AMP/CELL = 0.2 格 → ji/jj 仍落在 [-1, NX-1] / [-1, NZ-1]
                i = ji < -1 ? -1 : (ji > NX ? NX : ji);
                j = jj < -1 ? -1 : (jj > NZ ? NZ : jj);
                k00 = (j + 1) * SX + (i + 1);
                k10 = k00 + 1;
                k01 = k00 + SX;
                k11 = k01 + 1;
            }
            int b00 = k00 * KINDS, b10 = k10 * KINDS, b01 = k01 * KINDS, b11 = k11 * KINDS;
            double wtx = 1.0 - tx, wtz = 1.0 - tz;
            int best = 0;
            double bw = -1.0;
            for (int q = 0; q < KINDS; q++) {
                double v = (u(f.chan[b00 + q]) * wtx + u(f.chan[b10 + q]) * tx) * wtz
                    + (u(f.chan[b01 + q]) * wtx + u(f.chan[b11 + q]) * tx) * tz;
                if (v > bw) {
                    bw = v;
                    best = q;
                }
            }
            s.kind = KIND_ARR[best];
        } else {
            // 海洋：SHELF 权重双线性插值后按 0.5 判定（OCEAN ↔ SHELF 边界同样连续）
            s.kind = bilerpByte(f.shelfW, k00, k10, k01, k11, tx, tz) >= 127.5
                ? V2BiomeSelect.Kind.SHELF : V2BiomeSelect.Kind.OCEAN;
        }
        return s;
    }

    /** 热路径查询（自行做精确海陆判定；WorldChunkManager 用）。 */
    public static Sample sample(int x, int z, int worldSeedInt) {
        return sample(x, z, worldSeedInt, NoiseContinentGrid.isLand(x, z, worldSeedInt));
    }

    /** 只取群系种类。 */
    public static V2BiomeSelect.Kind kind(int x, int z, int worldSeedInt) {
        return sample(x, z, worldSeedInt).kind;
    }

    private static int u(byte v) {
        return v & 0xFF;
    }

    /** [0,1] 权重 → 字节（×255）。 */
    private static byte toByte(double w) {
        int v = (int) Math.round(w * 255.0);
        return (byte) (v < 0 ? 0 : (v > 255 ? 255 : v));
    }

    private static double bilerpByte(byte[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = u(g[k00]), v10 = u(g[k10]), v01 = u(g[k01]), v11 = u(g[k11]);
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
    }

    /**
     * 分形值噪声（[-1,1]）**在两轴上都不折叠**（px/pz 传 0 = 无界）——
     * 契约：Z_CYCLE 只重复气候，地形/群系抖动不得在 Z 上复读。
     * 历史：曾经 pz = round(200_000/len)，于是抖动在 Z 上每 200km 复读一次（F-1，已修）；
     * channel 用不同种子避免 x/z 位移同向。
     */
    private static double jitter(int x, int z, int seed, int channel) {
        double v = 0.0;
        for (int o = 0; o < JITTER_OCT; o++) {
            // X 传 0 = 不折叠（世界沿 X 无限）；Z 由 JT_Z_UNBOUNDED 决定（F-1 修复后也不折叠）。
            v += JT_AMP[o] * valueNoise(x, z, JT_INV[o], 0,
                JT_Z_UNBOUNDED ? 0 : JT_PZ[o], seed + channel * 977 + o * 31);
        }
        return v;
    }

    private static double valueNoise(int x, int z, double inv, int px, int pz, int seed) {
        double fx = x * inv, fz = z * inv;
        int xi = (int) Math.floor(fx), zi = (int) Math.floor(fz);
        double tx = fx - xi, tz = fz - zi;
        double sx = tx * tx * (3 - 2 * tx), sz = tz * tz * (3 - 2 * tz);
        int x0, x1;
        if (px > 0) {
            x0 = ((xi % px) + px) % px;
            x1 = (x0 + 1) % px;
        } else {
            x0 = xi;
            x1 = xi + 1;
        }
        int z0, z1;
        if (pz > 0) {                      // 与 px 同款保护：pz<=0 = 不折叠（且避免 pz=0 除零）
            z0 = ((zi % pz) + pz) % pz;
            z1 = (z0 + 1) % pz;
        } else {
            z0 = zi;
            z1 = zi + 1;
        }
        double v00 = hash01(x0, z0, seed), v10 = hash01(x1, z0, seed);
        double v01 = hash01(x0, z1, seed), v11 = hash01(x1, z1, seed);
        return ((v00 * (1 - sx) + v10 * sx) * (1 - sz) + (v01 * (1 - sx) + v11 * sx) * sz) * 2 - 1;
    }

    private static double hash01(int x, int z, int s) {
        int v = x * 374761393 + z * 668265263 + s * 1274126177;
        v = (v ^ (v >>> 13)) * 1274126177;
        return ((v ^ (v >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    private static double bilerp(float[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = g[k00], v10 = g[k10], v01 = g[k01], v11 = g[k11];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
    }

    // ================= 离线求解 =================

    private static Field solve(int seed, int tileX, int tileZ) {
        final int originX = tileX * TILE_X, originZ = tileZ * TILE_Z;
        long t0 = System.nanoTime();
        long sn = SOLVE_COUNT.incrementAndGet();
        if (SOLVE_HARD_CAP > 0 && sn > SOLVE_HARD_CAP) {
            throw new IllegalStateException("V2BiomeField 求解超过硬上限 SOLVE_HARD_CAP=" + SOLVE_HARD_CAP
                + "（已 " + sn + " 次，累计 " + (SOLVE_NANOS.get() / 1_000_000_000L)
                + "s，当前 tile=(" + tileX + "," + tileZ + ")）。几乎一定是缓存抖动：工作集 > 容量。"
                + "请改用 tile 主序采样。");
        }
        if (SOLVE_WARN_EVERY > 0 && sn % SOLVE_WARN_EVERY == 0) {
            System.out.println("[V2BiomeField] 求解已达 " + sn + " 次，累计 "
                + (SOLVE_NANOS.get() / 1_000_000_000L) + "s，当前 tile=(" + tileX + "," + tileZ + ")");
        }
        int n = SX * SZ;
        V2BiomeSelect.Kind[] kinds = KIND_ARR;
        byte[] shelfW = new byte[n];
        double[] w = new double[KINDS];

        // 1) 逐格算 16 通道候选权重（陆地口径，**全定义域，含 1 格 halo**）
        float[] chan = new float[n * KINDS];
        for (int j = -1; j <= NZ; j++) {
            int z = originZ + j * CELL + CELL / 2;
            for (int i = -1; i <= NX; i++) {
                int x = originX + i * CELL + CELL / 2;
                int k = (j + 1) * SX + (i + 1);
                OrographyField.OroSample o = OrographyField.sample(x, z, seed);
                Arrays.fill(w, 0.0);
                V2BiomeSelect.accumulateWeights(x, z, seed, o, true, w);
                int base = k * KINDS;
                for (int q = 0; q < KINDS; q++) {
                    chan[base + q] = (float) w[q];
                }
                // 海候选（OCEAN / SHELF）：只留 SHELF 权重，查询时插值后按 0.5 判定
                Arrays.fill(w, 0.0);
                V2BiomeSelect.accumulateWeights(x, z, seed, o, false, w);
                shelfW[k] = toByte(w[V2BiomeSelect.Kind.SHELF.ordinal()]);
            }
        }

        // 2) 平滑：对 **16 通道权重场**做可分离盒滤波（半径 BLUR_R）。
        //    比"对 argmax 做众数滤波"更正确：边界落在权重交叉处，强地貌不会被
        //    少量高置信邻居的多数票搬走（那正是"低山被标成 MOUNTAINS"的原因）。
        float[] tmp = new float[n * KINDS];
        float[] out = new float[n * KINDS];
        int win = 2 * BLUR_R + 1;
        for (int pass = 0; pass < BLUR_PASSES; pass++) {
            // 横向（含 halo 列；越界用边界复制，**不再取模** —— 取模会把 100km 外的列拉进来）
            for (int j = -1; j <= NZ; j++) {
                int rowBase = (j + 1) * SX;
                for (int i = -1; i <= NX; i++) {
                    int dst = (rowBase + i + 1) * KINDS;
                    for (int q = 0; q < KINDS; q++) {
                        float s = 0;
                        for (int d = -BLUR_R; d <= BLUR_R; d++) {
                            int ii = i + d;
                            if (ii < -1) ii = -1;
                            else if (ii > NX) ii = NX;
                            s += chan[(rowBase + ii + 1) * KINDS + q];
                        }
                        tmp[dst + q] = s / win;
                    }
                }
            }
            // 纵向（同样含 halo 行）
            for (int j = -1; j <= NZ; j++) {
                for (int i = -1; i <= NX; i++) {
                    int dst = ((j + 1) * SX + i + 1) * KINDS;
                    for (int q = 0; q < KINDS; q++) {
                        float s = 0;
                        for (int d = -BLUR_R; d <= BLUR_R; d++) {
                            int jj = j + d;
                            if (jj < -1) jj = -1;
                            else if (jj > NZ) jj = NZ;
                            s += tmp[((jj + 1) * SX + i + 1) * KINDS + q];
                        }
                        out[dst + q] = s / win;
                    }
                }
            }
            System.arraycopy(out, 0, chan, 0, n * KINDS);
        }

        // 3) argmax + 加权高度倾向
        Field f = new Field();
        for (int k = 0; k < n; k++) {
            int base = k * KINDS;
            double sum = 0, bs = 0, ss = 0;
            for (int q = 0; q < KINDS; q++) {
                double wq = chan[base + q];
                if (wq <= 0.0) {
                    continue;
                }
                sum += wq;
                bs += wq * kinds[q].heightBias;
                ss += wq * kinds[q].heightScale;
            }
            f.bias[k] = sum > 1e-9 ? (float) (bs / sum) : 0.5f;
            f.scale[k] = sum > 1e-9 ? (float) (ss / sum) : 0.5f;
            for (int q = 0; q < KINDS; q++) {
                f.chan[base + q] = toByte(chan[base + q]);
            }
        }
        System.arraycopy(shelfW, 0, f.shelfW, 0, n);
        SOLVE_NANOS.addAndGet(System.nanoTime() - t0);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("[BiomeField] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
