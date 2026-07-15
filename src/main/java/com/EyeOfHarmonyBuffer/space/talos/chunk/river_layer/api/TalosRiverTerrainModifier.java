package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.MacroPackageRegistry;

public final class TalosRiverTerrainModifier {

    private TalosRiverTerrainModifier() {}

    /**
     * 基于河流 mask 对基础高度做「河岸压低 + 平滑回老地形」。
     *
     * 新版规则（riverMask ∈ [0, 1]，越靠近河道中心值越大）：
     *   - 仅在 isLand == true 时生效；
     *   - 仅在 baseHeight > riverSurfaceY 时改，保护河道和洼地；
     *   - 使用 MacroPackageRegistry.RiverBankPreset 中的单参数：
     *       bankIntensity ∈ [0,1]，表示当前宏群系的「河岸压低强度」：
     *         0 = 几乎不做河岸压低；
     *         1 = 很强的洪泛平原 + 较宽的坡地过渡。
     *
     *     在本函数内部，将 bankIntensity 映射为：
     *       L_flat  : 河心附近「贴河面的大平面」在 mask 轴上的长度；
     *       L_slope : 大平面外侧「从河面缓坡过渡回原地形」在 mask 轴上的长度。
     *
     *     然后再反推出两个内部使用的阈值：
     *       flatThreshold   = 1 - L_flat；
     *       smoothThreshold = flatThreshold - L_slope；
     *
     *     最终 piecewise 规则保持不变：
     *       - mask <= smoothThreshold：不改高度（保持原始地形）；
     *       - smoothThreshold < mask < flatThreshold：
     *           在 baseHeight 和 riverSurfaceY 之间做平滑插值（缓坡河岸 / 河谷）；
     *       - mask >= flatThreshold：
     *           直接压到 riverSurfaceY（紧贴河道的平坦河岸地带）。
     *
     *   - 若 bankPreset 为 null，则使用一个中性的强度（0.5 左右）。
     *
     * @param worldX        世界 X
     * @param worldZ        世界 Z
     * @param worldSeedInt  Talos 世界种子 int
     * @param baseHeightD   原始基础高度（double）
     * @param riverSurfaceY 河面高度（当前就是 seaLevel，即 64）
     * @param bankPreset    当前宏群系的河岸预设（可为 null）
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
        if (mask <= 0.0) {
            return baseHeightD;
        }

        double k;
        if (bankPreset == null) {
            k = 0.5;
        } else {
            k = bankPreset.bankIntensity();
        }
        if (k < 0.0) k = 0.0;
        if (k > 1.0) k = 1.0;

        if (k < 0.0) k = 0.0;
        if (k > 1.0) k = 1.0;

        double center = 0.55;
        double strength = 0.6;

        k = center + (k - center) * (1.0 - strength);

        final double L_flat_min  = 0.03;
        final double L_flat_max  = 0.18;

        final double L_slope_min = 0.04;
        final double L_slope_max = 0.28;

        double L_flat  = L_flat_min  + (L_flat_max  - L_flat_min)  * k;
        double L_slope = L_slope_min + (L_slope_max - L_slope_min) * k;

        double total = L_flat + L_slope;
        if (total > 0.95) {
            double scale = 0.95 / total;
            L_flat  *= scale;
            L_slope *= scale;
        }

        double flatThreshold = 1.0 - L_flat;
        double smoothThreshold = flatThreshold - L_slope;

        if (smoothThreshold < 0.0) {
            smoothThreshold = 0.0;
        }
        if (smoothThreshold >= flatThreshold) {
            smoothThreshold = flatThreshold - 0.01;
            if (smoothThreshold < 0.0) {
                smoothThreshold = 0.0;
            }
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
