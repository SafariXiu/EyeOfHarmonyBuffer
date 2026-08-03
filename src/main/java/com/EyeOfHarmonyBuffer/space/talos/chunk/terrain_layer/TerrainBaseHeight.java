package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.*;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise.fbm2D;

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
    private static final double CONTINENTAL_FREQ = 1.0 / 8000.0;
    private static final double CONTINENTAL_AMP = 12.0;
    private static final int CONTINENTAL_OCTAVES = 2;

    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile) {

        double x = worldX;
        double z = worldZ;
        long seed = (long) worldSeedInt;

        double raw = 0.0;
        double totalAmp = 0.0;

        // 共享大陆骨架：所有陆地宏群系使用同一低频场
        if (profile.oceanDepthMax <= 0.0) {
            raw += fbm2D(seed ^ 0xABCDEF01L,
                x, z,
                CONTINENTAL_FREQ,
                CONTINENTAL_AMP,
                CONTINENTAL_OCTAVES);
            totalAmp += octaveSum(CONTINENTAL_AMP, CONTINENTAL_OCTAVES);
        }

        // 低频：大陆级盆地 / 高原
        raw += fbm2D(seed ^ 0x1234ABCDL,
            x, z,
            profile.lowFreq,
            profile.lowAmp,
            profile.lowOctaves);
        totalAmp += octaveSum(profile.lowAmp, profile.lowOctaves);

        // 中频：丘陵 / 台地
        raw += fbm2D(seed ^ 0x5678EF01L,
            x, z,
            profile.midFreq,
            profile.midAmp,
            profile.midOctaves);
        totalAmp += octaveSum(profile.midAmp, profile.midOctaves);

        // 高频：小起伏 / 岩面
        raw += fbm2D(seed ^ 0x9ABCDEFFL,
            x, z,
            profile.highFreq,
            profile.highAmp,
            profile.highOctaves);
        totalAmp += octaveSum(profile.highAmp, profile.highOctaves);

        // 归一化到 [0,1]：噪声只负责"形状"，绝对高度由 minHeight/maxHeight 决定
        double t = 0.5;
        if (totalAmp > 0.0) {
            t = raw / totalAmp * 0.5 + 0.5;
        }
        t = clamp(t, 0.0, 1.0);

        // 台地 / 高原修饰（带内操作：把偏高部分向带中心拉拢）
        double s = profile.plateauStrength;
        if (s > 0.0) {
            double p = smoothstep(0.2, 0.8, t);
            t = lerp(t, 0.5, p * s);
            t = clamp(t, 0.0, 1.0);
        }

        double lo = profile.minHeight;
        double hi = profile.maxHeight;
        if (hi <= lo) {
            hi = lo + 1.0;
        }

        // smoothstep 让分布向带中部集中，两端可达但不常驻
        return lo + (hi - lo) * smoothstep(0.0, 1.0, t);
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
