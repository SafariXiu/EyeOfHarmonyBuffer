package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.*;

/**
 * 第四层内部核心：根据 profile + worldSeedInt 计算 H_base(x,z)。
 */

public final class TerrainBaseHeight {

    private TerrainBaseHeight() {}

    /**
     * 共享大陆骨架（仅陆地宏群系）。
     *
     * 所有陆地 preset 都叠加这同一个低频场（同种子 / 同频率 / 同振幅），
     * 因此宏群系边界两侧的 h1 / h2 包含完全相同的分量，
     * 做 lerp 混合时这一部分原样穿过边界，最大尺度的起伏天然衔接，
     * 从根上消除"两侧大陆骨架不同导致的对不上"的接缝。
     *
     * 海洋 preset（oceanDepthMax > 0）不叠加，海底维持原样。
     */
    public static final double CONTINENTAL_FREQ = 1.0 / 8000.0;
    public static final double CONTINENTAL_AMP = 12.0;
    public static final int CONTINENTAL_OCTAVES = 2;

    /** 群系高度倾向增益（V2 口径）：baseT 位移 = (bias − 0.5) × 本值。 */
    public static double BIOME_BIAS_GAIN = 1.6;
    /** 群系散布映射：factor = LO + SPAN × scale（scale=0.5 → 1.0 中性）。 */
    public static double BIOME_SCALE_LO = 0.4, BIOME_SCALE_SPAN = 1.2;

    /** 默认无群系偏移（bias=0.5）。 */
    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile) {
        return computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile, 0.5);
    }

    /**
     * 三层分解核心。
     *
     * @param biomeBias 群系级高度偏移 [0,1]，0.5=无偏移。作为 baseT 的
     *                  线性偏移整合进带内塑形（在 bandShape 之前），
     *                  取代旧系统在 TerrainEngine 里的事后 smoothstep 重映射
     *                  （后者会把平滑 bias 非线性放大成区域性抬升 / 硬切）。
     */
    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile,
                                               double biomeBias) {
        // 旧轨口径：位移增益 0.25、不做散布调制（保持 V1 行为不变）
        return computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile, biomeBias, 0.5, 0.25);
    }

    /** V2 口径：群系高度倾向（bias 位移 + scale 散布）。 */
    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile,
                                               double biomeBias, double biomeScale) {
        return computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile,
            biomeBias, biomeScale, BIOME_BIAS_GAIN);
    }

    /**
     * 三层噪声的**归一化**值（与档案参数无关）。
     *
     * warpedFbm2D 对 amp 线性，且归一化除子 octaveSum(amp, oct) 也含 amp，
     * 所以 "raw/octaveSum(1, oct)" 与档案无关 → base 与 plain（同一频率/八度数）
     * 可以**共享同一份噪声采样**，省掉一半的列耗时。
     */
    public static final class Noise {
        public double low;
        public double cont;
        public double mid;
        public double hi;
    }

    /**
     * 采样共享噪声（含域扭曲；频率/八度数取自档案，调用方保证两个档案一致）。
     *
     * 全部走 {@link PeriodicNoise}（格数 = round(周期/波长)；是否对索引取模由 INFINITE_X/INFINITE_Z 决定，
     * 两者当前都为 true ⇒ **不折叠**，因此上面的"按周期重复"不再成立）。
     */
    public static void sampleNoise(int worldX, int worldZ, int worldSeedInt,
                                   BaseTerrainProfile p, Noise out) {
        long seed = (long) worldSeedInt;
        out.low = PeriodicNoise.warpedFbm2(seed ^ 0x1234ABCDL, worldX, worldZ,
            cellsX(p.lowNX, p.lowFreq), cellsZ(p.lowNZ, p.lowFreq), p.lowOctaves,
            cellsX(p.lowWNX, p.lowFreq * 0.5), cellsZ(p.lowWNZ, p.lowFreq * 0.5),
            (1.0 / p.lowFreq) * 0.5)
            / octaveSum(1.0, p.lowOctaves);
        out.cont = PeriodicNoise.gradientFbm2(seed ^ 0xABCDEF01L, worldX, worldZ,
            cellsX(p.contNX, CONTINENTAL_FREQ), cellsZ(p.contNZ, CONTINENTAL_FREQ),
            CONTINENTAL_OCTAVES) * (CONTINENTAL_AMP / octaveSum(1.0, CONTINENTAL_OCTAVES));
        out.mid = PeriodicNoise.warpedFbm2(seed ^ 0x5678EF01L, worldX, worldZ,
            cellsX(p.midNX, p.midFreq), cellsZ(p.midNZ, p.midFreq), p.midOctaves,
            cellsX(p.midWNX, p.midFreq * 0.5), cellsZ(p.midWNZ, p.midFreq * 0.5),
            (1.0 / p.midFreq) * 0.35)
            / octaveSum(1.0, p.midOctaves);
        out.hi = PeriodicNoise.warpedFbm2(seed ^ 0x9ABCDEFFL, worldX, worldZ,
            cellsX(p.hiNX, p.highFreq), cellsZ(p.hiNZ, p.highFreq), p.highOctaves,
            cellsX(p.hiWNX, p.highFreq * 0.5), cellsZ(p.hiWNZ, p.highFreq * 0.5),
            (1.0 / p.highFreq) * 0.25)
            / octaveSum(1.0, p.highOctaves);
    }

    /**
     * 档案里缓存的格数（**可能为负 = 该轴不折叠**，见 {@link PeriodicNoise#cellsX}）；
     * 0 表示未缓存 → 现算。
     *
     * 两个轴**必须用同一个判据**。原先 Z 写的是 {@code cached > 0}，而 INFINITE_Z=true 时
     * {@code cellsZFromFreq} 一律返回**负数** ⇒ 判据恒假 ⇒ 每次都白算一遍
     * （数值相同，所以没有任何测试能发现；这正是"同一件事两套写法"的典型样本）。
     */
    private static int cellsX(int cached, double freq) {
        return cached != 0 ? cached : PeriodicNoise.cellsX(1.0 / freq);
    }

    private static int cellsZ(int cached, double freq) {
        return cached != 0 ? cached : PeriodicNoise.cellsZ(1.0 / freq);
    }

    /** 由共享噪声 + 档案参数求高度（land 分支）。 */
    public static double fromNoise(BaseTerrainProfile profile, Noise n,
                                   double biomeBias, double biomeScale, double biasGain) {
        double loAmp = octaveSum(profile.lowAmp, profile.lowOctaves);
        double baseT;
        if (profile.oceanDepthMax <= 0.0) {
            baseT = (loAmp * n.low + n.cont) / (loAmp + CONTINENTAL_AMP) * 0.5 + 0.5;
        } else {
            baseT = n.low * 0.5 + 0.5;
        }
        baseT = clamp(baseT, 0.0, 1.0);

        double s = profile.plateauStrength;
        if (s > 0.0) {
            double p = smoothstep(0.25, 0.75, baseT);
            baseT = clamp(lerp(baseT, 0.5, p * s * 0.5), 0.0, 1.0);
        }

        double factor = BIOME_SCALE_LO + BIOME_SCALE_SPAN * biomeScale;
        if (factor < 0.0) {
            factor = 0.0;
        }
        if (Math.abs(factor - 1.0) > 1e-6) {
            baseT = clamp(0.5 + (baseT - 0.5) * factor, 0.0, 1.0);
        }
        if (biomeBias != 0.5) {
            baseT = clamp(baseT + (biomeBias - 0.5) * biasGain, 0.0, 1.0);
        }

        double lo = profile.minHeight;
        double hi = profile.maxHeight;
        if (hi <= lo) {
            hi = lo + 1.0;
        }
        double base = lo + (hi - lo) * bandShape(baseT);

        if (profile.midAmp > 0.0 && profile.midOctaves > 0) {
            base += n.mid * Math.min(18.0, profile.midAmp * 0.9);
        }
        if (profile.highAmp > 0.0 && profile.highOctaves > 0) {
            base += n.hi * Math.min(4.0, 1.0 + profile.highAmp * 0.6);
        }
        return base;
    }

    private static double computeBaseHeightCore(int worldX, int worldZ,
                                                int worldSeedInt,
                                                BaseTerrainProfile profile,
                                                double biomeBias, double biomeScale,
                                                double biasGain) {
        Noise n = new Noise();
        sampleNoise(worldX, worldZ, worldSeedInt, profile, n);
        return fromNoise(profile, n, biomeBias, biomeScale, biasGain);
    }

    /**
     * 分段高度带曲线：把归一化 t 映射到带内位置。
     * 低地段（t 小）平缓、丘陵段（t 中）起伏、山地带（t 大）陡峭，
     * 各段占比固定，避免旧的单 smoothstep 把带内噪声放大。
     */
    static double bandShape(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        double lo = smoothstep(0.0, 0.45, t);
        double mid = smoothstep(0.35, 0.80, t);
        double hi = smoothstep(0.72, 1.0, t);
        return clamp(0.26 * lo + 0.48 * mid + 0.26 * hi, 0.0, 1.0);
    }

    /** FBM 各层振幅的等比和（层间振幅折半）：amp * (2 - 0.5^(oct-1))。 */
    private static double octaveSum(double amp, int octaves) {
        if (octaves <= 0) {
            return 0.0;
        }
        return amp * (2.0 - Math.pow(0.5, octaves - 1));
    }

    /**
     * 仅对海洋 preset 生效的“深度下限收紧”。
     */
    public static double applyOceanDepthLimit(double h,
                                              BaseTerrainProfile profile,
                                              int seaLevel) {
        if (profile.oceanDepthMax <= 0.0) {
            return h;
        }
        double minY = seaLevel - profile.oceanDepthMax;
        if (h < minY) {
            double t = saturate((minY - h) / 16.0);
            h = lerp(h, minY, t);
        }
        return h;
    }
}
