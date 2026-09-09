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
 *   2. 平滑：在网格上做邻域众数滤波（去掉孤立小斑块）+ 高度倾向的邻域平均
 *      （地形在群系边界连续），解决"雪山紧邻沙漠"的硬边问题。
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

    private static final ConcurrentHashMap<Integer, Field> CACHE =
        new ConcurrentHashMap<Integer, Field>();

    /**
     * 一个种子的解（各 8 万格）。
     *
     * bias/scale 在**全定义域**都有值（海格按"假如是陆地"算），保证双线性插值在海岸处平滑；
     * 群系种类分陆/海两套：查询时用**精确海陆判定**取用，于是海岸线不会被 1km 网格量化。
     */
    private static final class Field {
        final float[] bias = new float[NX * NZ];
        final float[] scale = new float[NX * NZ];
        final byte[] kindLand = new byte[NX * NZ];
        final byte[] kindSea = new byte[NX * NZ];
        /** 地貌置信度 [0,1]：平滑投票时按它加权，避免强地貌被周围气候群系吞掉。 */
        final float[] conf = new float[NX * NZ];
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

    private V2BiomeField() {}

    /** 后台预热（WorldEvent.Load → ClimatePreheat）。 */
    public static void ensure(int worldSeedInt) {
        if (!CACHE.containsKey(worldSeedInt)) {
            CACHE.computeIfAbsent(worldSeedInt, V2BiomeField::solve);
        }
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static Field field(int worldSeedInt) {
        Field f = CACHE.get(worldSeedInt);
        if (f != null) {
            return f;
        }
        if (CACHE.size() > 2) {
            CACHE.clear();
        }
        return CACHE.computeIfAbsent(worldSeedInt, V2BiomeField::solve);
    }

    /** 热路径查询（调用方已知精确海陆状态时用，省一次噪声采样）。 */
    public static Sample sample(int x, int z, int worldSeedInt, boolean isLand) {
        Field f = field(worldSeedInt);
        Sample s = TL.get();
        s.land = isLand;
        double fx = GlobalCirculation.foldX(x) / (double) CELL - 0.5;
        double fz = GlobalCirculation.foldZ(z) / (double) CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = ((i % NX) + NX) % NX;
        j = ((j % NZ) + NZ) % NZ;
        int i1 = (i + 1) % NX, j1 = (j + 1) % NZ;
        int k00 = j * NX + i, k10 = j * NX + i1, k01 = j1 * NX + i, k11 = j1 * NX + i1;
        s.bias = bilerp(f.bias, k00, k10, k01, k11, tx, tz);
        s.scale = bilerp(f.scale, k00, k10, k01, k11, tx, tz);
        int ni = tx < 0.5 ? i : i1;
        int nj = tz < 0.5 ? j : j1;
        byte[] kk = isLand ? f.kindLand : f.kindSea;
        s.kind = V2BiomeSelect.Kind.values()[kk[nj * NX + ni] & 0xFF];
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

    private static double bilerp(float[] g, int k00, int k10, int k01, int k11, double tx, double tz) {
        double v00 = g[k00], v10 = g[k10], v01 = g[k01], v11 = g[k11];
        return (v00 * (1 - tx) + v10 * tx) * (1 - tz) + (v01 * (1 - tx) + v11 * tx) * tz;
    }

    // ================= 离线求解 =================

    private static Field solve(int seed) {
        long t0 = System.nanoTime();
        int n = NX * NZ;
        V2BiomeSelect.Kind[] kinds = V2BiomeSelect.Kind.values();
        byte[] kindSea = new byte[n];
        float[] conf = new float[n];
        double[] w = new double[KINDS];

        // 1) 逐格算 16 通道候选权重（陆地口径，**全定义域**）
        float[] chan = new float[n * KINDS];
        for (int j = 0; j < NZ; j++) {
            int z = j * CELL + CELL / 2;
            for (int i = 0; i < NX; i++) {
                int x = i * CELL + CELL / 2;
                int k = j * NX + i;
                OrographyField.OroSample o = OrographyField.sample(x, z, seed);
                Arrays.fill(w, 0.0);
                conf[k] = (float) V2BiomeSelect.accumulateWeights(x, z, seed, o, true, w);
                int base = k * KINDS;
                for (int q = 0; q < KINDS; q++) {
                    chan[base + q] = (float) w[q];
                }
                // 海候选（OCEAN / SHELF）
                Arrays.fill(w, 0.0);
                V2BiomeSelect.accumulateWeights(x, z, seed, o, false, w);
                kindSea[k] = (byte) (w[V2BiomeSelect.Kind.SHELF.ordinal()] > 0.5
                    ? V2BiomeSelect.Kind.SHELF.ordinal() : V2BiomeSelect.Kind.OCEAN.ordinal());
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
            int best = 0;
            double bestW = -1, sum = 0, bs = 0, ss = 0;
            for (int q = 0; q < KINDS; q++) {
                double wq = chan[base + q];
                if (wq <= 0.0) {
                    continue;
                }
                sum += wq;
                bs += wq * kinds[q].heightBias;
                ss += wq * kinds[q].heightScale;
                if (wq > bestW) {
                    bestW = wq;
                    best = q;
                }
            }
            f.kindLand[k] = (byte) best;
            f.bias[k] = sum > 1e-9 ? (float) (bs / sum) : 0.5f;
            f.scale[k] = sum > 1e-9 ? (float) (ss / sum) : 0.5f;
        }
        System.arraycopy(kindSea, 0, f.kindSea, 0, n);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("[BiomeField] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
