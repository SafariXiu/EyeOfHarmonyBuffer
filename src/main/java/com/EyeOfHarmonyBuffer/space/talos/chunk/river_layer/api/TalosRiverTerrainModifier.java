package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

public final class TalosRiverTerrainModifier {

    private TalosRiverTerrainModifier() {}

    /**
     * 基于河流 mask 对基础高度做“河岸压低 + 平滑回老地形”。
     *
     * 规则：
     *   - 只在 isLand == true 时生效；
     *   - 只在 baseHeight > riverSurfaceY 时改，保护河道和洼地；
     *   - mask <= 0.7：不改；
     *   - 0.7 < mask < 0.8：在 baseHeight 和 riverSurfaceY 之间平滑插值；
     *   - mask >= 0.8：直接压到 riverSurfaceY。
     *
     * @param worldX        世界 X
     * @param worldZ        世界 Z
     * @param worldSeedInt  Talos 世界种子 int
     * @param baseHeightD   原始基础高度（double）
     * @param riverSurfaceY 河面高度（当前就是 seaLevel，即 64）
     */
    public static double applyRiverBankShaping(
        int worldX, int worldZ,
        int worldSeedInt,
        double baseHeightD,
        double riverSurfaceY
    ) {
        if (!TalosLandMask.isLand(worldX, worldZ, worldSeedInt)) {
            return baseHeightD;
        }

        if (baseHeightD <= riverSurfaceY) {
            return baseHeightD;
        }

        double mask = TalosRiverSystem.getRiverMask(worldX, worldZ, worldSeedInt);

        if (mask <= 0.7) {
            return baseHeightD;
        }

        double smoothThreshold = 0.7;
        double flatThreshold   = 0.8;

        double targetY = riverSurfaceY;

        if (mask >= flatThreshold) {
            return targetY;
        }

        double t = (mask - smoothThreshold) / (flatThreshold - smoothThreshold);
        t = t * t * (3.0 - 2.0 * t);

        return baseHeightD + (targetY - baseHeightD) * t;
    }
}
