package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.*;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise.fbm2D;

/**
 * 第四层内部核心：根据 profile + worldSeedInt 计算 H_base(x,z)。
 */

public final class TerrainBaseHeight {

    private TerrainBaseHeight() {}

    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile) {

        double x = worldX;
        double z = worldZ;
        long seed = (long) worldSeedInt;

        double h = profile.baseHeight;

        // 低频：大陆级盆地 / 高原
        h += fbm2D(seed ^ 0x1234ABCDL,
            x, z,
            profile.lowFreq,
            profile.lowAmp,
            profile.lowOctaves);

        // 中频：丘陵 / 台地
        h += fbm2D(seed ^ 0x5678EF01L,
            x, z,
            profile.midFreq,
            profile.midAmp,
            profile.midOctaves);

        // 高频：小起伏 / 岩面
        h += fbm2D(seed ^ 0x9ABCDEFFL,
            x, z,
            profile.highFreq,
            profile.highAmp,
            profile.highOctaves);

        // 台地 / 高原修饰
        h = applyPlateau(h, profile);

        return h;
    }

    private static double applyPlateau(double h, BaseTerrainProfile profile) {
        double s = profile.plateauStrength;
        if (s <= 0.0) {
            return h;
        }

        double hNorm = h / 256.0;
        hNorm = clamp(hNorm, 0.0, 1.0);

        double t = smoothstep(0.2, 0.8, hNorm);
        double plateauNorm = lerp(hNorm, 0.5, t * s);

        return plateauNorm * 256.0;
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
