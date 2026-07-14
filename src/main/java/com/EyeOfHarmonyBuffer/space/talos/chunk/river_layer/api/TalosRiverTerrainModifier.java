package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.MacroPackageRegistry;

public final class TalosRiverTerrainModifier {

    private TalosRiverTerrainModifier() {}

    /**
     * 基于河流 mask 对基础高度做「河岸压低 + 平滑回老地形」。
     *
     * 规则（约定 riverMask ∈ [0, 1]，越靠近河道中心值越大）：
     *   - 仅在 isLand == true 时生效；
     *   - 仅在 baseHeight > riverSurfaceY 时改，保护河道和洼地；
     *   - 使用 MacroPackageRegistry.RiverBankPreset 中的两个阈值：
     *       smoothThreshold, flatThreshold，要求 smoothThreshold < flatThreshold；
     *       - mask <= smoothThreshold：不改高度（保持原始地形）；
     *       - smoothThreshold < mask < flatThreshold：
     *           在 baseHeight 和 riverSurfaceY 之间做平滑插值（缓坡河岸 / 河谷）；
     *       - mask >= flatThreshold：
     *           直接压到 riverSurfaceY（紧贴河道的平坦河岸地带）；
     *   - 若 bankPreset 为 null 或数据非法（flat <= smooth），则退回到默认阈值 0.7 / 0.8。
     *
     * @param worldX        世界 X
     * @param worldZ        世界 Z
     * @param worldSeedInt  Talos 世界种子 int
     * @param baseHeightD   原始基础高度（double）
     * @param riverSurfaceY 河面高度（当前就是 seaLevel，即 64）
     * @param bankPreset    当前宏群系的河岸预设（可为 null，null 时使用默认 0.7 / 0.8）
     */
    public static double applyRiverBankShaping(
        int worldX, int worldZ,
        int worldSeedInt,
        double baseHeightD,
        double riverSurfaceY,
        MacroPackageRegistry.RiverBankPreset bankPreset
    ) {
        if (!TalosLandMask.isLand(worldX, worldZ, worldSeedInt)) {
            return baseHeightD;
        }

        if (baseHeightD <= riverSurfaceY) {
            return baseHeightD;
        }

        double mask = TalosRiverSystem.getRiverMask(worldX, worldZ, worldSeedInt);

        // 从预设中取阈值，并做一点保护
        double smoothThreshold = bankPreset.smoothThreshold();
        double flatThreshold   = bankPreset.flatThreshold();

        // 简单确保 smooth < flat，防止数据错误
        if (flatThreshold <= smoothThreshold) {
            // fallback 到一个安全的默认值
            smoothThreshold = 0.7;
            flatThreshold   = 0.8;
        }

        if (mask <= smoothThreshold) {
            return baseHeightD;
        }

        double targetY = riverSurfaceY;

        if (mask >= flatThreshold) {
            return targetY;
        }

        double t = (mask - smoothThreshold) / (flatThreshold - smoothThreshold);
        t = t * t * (3.0 - 2.0 * t);

        return baseHeightD + (targetY - baseHeightD) * t;
    }
}
