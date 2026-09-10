package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

/**
 * V2 群系 LUT（L1c 离线求解 + 空间平滑，T2.2）。
 *
 * **为什么做成 LUT**：
 *   1. 性能：群系选择原本每列都要采样气候场（4 次 LUT + 风 + 4 次 elevation01 + SST），
 *      一个区块 256 列就是几百微秒。改为按种子离线求解 1km 网格（400×200 = 8 万格，
 *      约 0.3s，跑在预热线程），运行时每列只做一次查表 + 双线性插值（无分配，~20ns）。
 *   2. 平滑：对 **16 通道权重场**做可分离盒滤波（去掉孤立小斑块），并让高度倾向
 *      （bias/scale）在边界连续；解决"雪山紧邻沙漠"的硬边问题。
 *   3. 连续边界：LUT 存的是**每格 16 通道权重**（不是每格 argmax 的 kind），查询时对
 *      4 个格子的权重做双线性插值再 argmax → 群系边界是连续场的等值线。
 *      旧实现存 kind + 取最近格，边界被量化到 1km 网格（实测 100% 的陆地边界切换点
 *      落在 x/z%1000==0 上），呈现 1000 格长的直线段与 90° 直角。
 *   4. 小尺度抖动：查询群系种类前把坐标按**环面周期分形噪声**位移（JITTER_AMP/LEN/OCT），
 *      让边界在 chunk 尺度上呈现自然起伏（实测边界周长 +17%、群系占比漂移 <0.01pp）。
 *      只抖种类、不抖 bias/scale → 高度场不受影响。
 *
 * 与 {@link V2BiomeSelect} 的关系：求解时逐格调用 {@code accumulateWeights}，
 * 口径完全一致；运行时只查表。
 */
public final class V2BiomeField {

    /** 网格分辨率（blocks）：1km → 400×200 格。 */
    public static final int CELL = 1000;
    public static final int NX = 400_000 / CELL;
    public static final int NZ = 200_000 / CELL;

    /** 平滑半径（格）：2 → 5×5 邻域 ≈ 5km。 */
    public static int BLUR_R = 1;
    /** 权重场平滑遍数（半径 1 + 2 遍 ≈ 2km 有效半径，边界不被拉出长裙边）。 */
    public static int BLUR_PASSES = 2;

    private static final int KINDS = V2BiomeSelect.KINDS;

    /** 窗口宽度（blocks）：与 LandformField/气候一致，按绝对坐标窗口求解。 */
    public static final int TILE_X = 400_000;

    public static final int TILE_Z = 200_000;

    private static long tileKey(int seed, int tileX, int tileZ) {
        return ((long) seed << 40) ^ ((long) (tileX & 0xFFFFF) << 20) ^ (tileZ & 0xFFFFFL);
    }

    private static final ConcurrentHashMap<Long, Field> CACHE =
        new ConcurrentHashMap<Long, Field>();

    /**
     * 一个种子的解（各 8 万格）。
     *
     * bias/scale 在**全定义域**都有值（海格按"假如是陆地"算），保证双线性插值在海岸处平滑；
     * 群系种类分陆/海两套：查询时用**精确海陆判定**取用，于是海岸线不会被 1km 网格量化。
     */
    private static final class Field {
        final float[] bias = new float[NX * NZ];
        final float[] scale = new float[NX * NZ];
        /** 16 通道陆地权重（×255 字节）：查询时双线性插值再 argmax → 边界连续。 */
        final byte[] chan = new byte[NX * NZ * KINDS];
        /** 海洋候选：SHELF 权重（×255）。OCEAN/SHELF 按双线性插值后的 0.5 阈值判定。 */
        final byte[] shelfW = new byte[NX * NZ];
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
    public static double JITTER_AMP = 200.0;
    /**
     * 主波长（blocks）。**必须整除 400000 与 200000**（…/250/320/400/500…），
     * 八度波长逐级减半；否则噪声在环面接缝处不连续。
     */
    public static final double JITTER_LEN = 400.0;
    /** 八度数（波长 400 / 200 / 100 blocks）。 */
    public static final int JITTER_OCT = 3;
    /** 各八度的 lattice 尺寸与归一化幅度（类加载时算好 → 热路径无除法）。 */
    /** 抖动噪声 X 方向是否无界（C1 世界沿 X 无限 → true）。 */
    private static final boolean JT_X_UNBOUNDED = true;
    private static final int[] JT_PX = new int[JITTER_OCT];
    private static final int[] JT_PZ = new int[JITTER_OCT];
    private static final double[] JT_INV = new double[JITTER_OCT];
    private static final double[] JT_AMP = new double[JITTER_OCT];
    static {
        double len = JITTER_LEN, amp = 1.0, norm = 0.0;
        for (int o = 0; o < JITTER_OCT; o++) {
            JT_INV[o] = 1.0 / len;
            JT_PX[o] = (int) Math.round(400_000.0 / len);
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
        i = ((i % NX) + NX) % NX;
        j = ((j % NZ) + NZ) % NZ;
        int i1 = (i + 1) % NX, j1 = (j + 1) % NZ;
        int k00 = j * NX + i, k10 = j * NX + i1, k01 = j1 * NX + i, k11 = j1 * NX + i1;
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
                i = ((ji % NX) + NX) % NX;
                j = ((jj % NZ) + NZ) % NZ;
                i1 = (i + 1) % NX;
                j1 = (j + 1) % NZ;
                k00 = j * NX + i;
                k10 = j * NX + i1;
                k01 = j1 * NX + i;
                k11 = j1 * NX + i1;
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
        return sample(x, z, worldSeedInt,
            NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0);
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
     * 环面周期分形值噪声（[-1,1]）：lattice 索引按世界周期取模 → 噪声本身以
     * 400k(x)/200k(z) 为周期、无重复图案；channel 用不同种子避免 x/z 位移同向。
     */
    private static double jitter(int x, int z, int seed, int channel) {
        double v = 0.0;
        for (int o = 0; o < JITTER_OCT; o++) {
            v += JT_AMP[o] * valueNoise(x, z, JT_INV[o], JT_X_UNBOUNDED ? 0 : JT_PX[o],
                JT_PZ[o], seed + channel * 977 + o * 31);
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
        int z0 = ((zi % pz) + pz) % pz, z1 = (z0 + 1) % pz;
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
        int n = NX * NZ;
        V2BiomeSelect.Kind[] kinds = KIND_ARR;
        byte[] shelfW = new byte[n];
        double[] w = new double[KINDS];

        // 1) 逐格算 16 通道候选权重（陆地口径，**全定义域**）
        float[] chan = new float[n * KINDS];
        for (int j = 0; j < NZ; j++) {
            int z = originZ + j * CELL + CELL / 2;
            for (int i = 0; i < NX; i++) {
                int x = originX + i * CELL + CELL / 2;
                int k = j * NX + i;
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
            // 横向
            for (int j = 0; j < NZ; j++) {
                for (int i = 0; i < NX; i++) {
                    int dst = (j * NX + i) * KINDS;
                    for (int q = 0; q < KINDS; q++) {
                        float s = 0;
                        for (int d = -BLUR_R; d <= BLUR_R; d++) {
                            int ii = ((i + d) % NX + NX) % NX;
                            s += chan[(j * NX + ii) * KINDS + q];
                        }
                        tmp[dst + q] = s / win;
                    }
                }
            }
            // 纵向
            for (int j = 0; j < NZ; j++) {
                for (int i = 0; i < NX; i++) {
                    int dst = (j * NX + i) * KINDS;
                    for (int q = 0; q < KINDS; q++) {
                        float s = 0;
                        for (int d = -BLUR_R; d <= BLUR_R; d++) {
                            int jj = ((j + d) % NZ + NZ) % NZ;
                            s += tmp[(jj * NX + i) * KINDS + q];
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
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("[BiomeField] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
