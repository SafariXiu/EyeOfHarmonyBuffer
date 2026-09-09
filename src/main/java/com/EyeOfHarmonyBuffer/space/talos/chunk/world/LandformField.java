package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

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
 *   mtnAmt —— 「山体强度」[0,1] = max(山层权威 auth, mtnComp0/70, mtn+peak 权重)
 *             （与地形里 mountainDetail 用的 amt 同口径，且包含山层）
 *
 * 求解时机：山层之后、群系场之前（不需要气候场）。
 */
public final class LandformField {

    private LandformField() {}

    /** 网格分辨率（blocks）：1km → 400×200 格。 */
    public static final int CELL = 1000;
    public static final int NX = 400_000 / CELL;
    public static final int NZ = 200_000 / CELL;

    /** 求解用的海平面（与 Provider 的 getWaterLevel 一致，仅用于档案带限下限）。 */
    public static final int SEA_LEVEL = 64;

    /**
     * 「山体抬升量 → 强度」标尺（blocks）。
     * 抬升量 = 地形相对周围平原的高度 = (1−auth)·mtnComp0 + auth·uplift（含中尺度纹理）。
     * 这是"这里是不是山"的**实测口径**——不再用档案标签（MOUNTAIN 档在低底盘区只有 ~100 高）。
     */
    public static double MTN_RISE_SCALE = 85.0;

    private static final ConcurrentHashMap<Integer, Field> CACHE =
        new ConcurrentHashMap<Integer, Field>();

    private static final class Field {
        final float[] low = new float[NX * NZ];
        final float[] hill = new float[NX * NZ];
        final float[] plat = new float[NX * NZ];
        final float[] mtn = new float[NX * NZ];
        final float[] peak = new float[NX * NZ];
        final float[] mtnAmt = new float[NX * NZ];
        /** 中性 bias 的静态骨架高度（供"雪线以上"判据，避免与地形高度循环依赖）。 */
        final float[] h0 = new float[NX * NZ];
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
        if (!CACHE.containsKey(worldSeedInt)) {
            CACHE.computeIfAbsent(worldSeedInt, LandformField::solve);
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
        return CACHE.computeIfAbsent(worldSeedInt, LandformField::solve);
    }

    /** 双线性查询（无分配，结果对象线程本地复用）。 */
    public static Sample sample(int x, int z, int worldSeedInt) {
        Field f = field(worldSeedInt);
        Sample s = TL.get();
        double fx = GlobalCirculation.foldX(x) / (double) CELL - 0.5;
        double fz = GlobalCirculation.foldZ(z) / (double) CELL - 0.5;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double tx = fx - i, tz = fz - j;
        i = ((i % NX) + NX) % NX;
        j = ((j % NZ) + NZ) % NZ;
        int i1 = (i + 1) % NX, j1 = (j + 1) % NZ;
        int k00 = j * NX + i, k10 = j * NX + i1, k01 = j1 * NX + i, k11 = j1 * NX + i1;
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

    private static Field solve(int seed) {
        long t0 = System.nanoTime();
        int n = NX * NZ;
        Field f = new Field();
        double[] w = new double[5];
        double[] bp = new double[2];

        for (int j = 0; j < NZ; j++) {
            int z = j * CELL + CELL / 2;
            for (int i = 0; i < NX; i++) {
                int x = i * CELL + CELL / 2;
                int k = j * NX + i;
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
        System.out.println("[Landform] seed=" + seed + " solved in " + ms + "ms  ("
            + NX + "x" + NZ + " @ " + CELL + "m)");
        return f;
    }
}
