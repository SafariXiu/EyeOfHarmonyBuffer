package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;

public final class TalosRiverProfile {

    private TalosRiverProfile() {}

    public static double computeRiverBedY(double baseHeight,
                                          int seaLevel,
                                          TalosRiverSystem.HydroSample hydro,
                                          MacroPackageId macroId) {

        double dist        = hydro.distance;
        double coreWidth   = hydro.widthCore;
        double valleyWidth = hydro.widthValley;
        double mask        = hydro.mask;
        int    riverLevel  = hydro.riverLevel;

        if (valleyWidth <= 0.0 || mask <= 0.0 ||
            dist == Double.MAX_VALUE) {
            return baseHeight;
        }

        MacroPackageRegistry.RiverStylePreset style =
            MacroPackageRegistry.get(macroId).riverStyle();

        double depthMain = style.baseDepthBlocks;

        double scale = 1.0;
        if (riverLevel > 0) {
            scale = Math.pow(
                clamp01(style.tributaryDepthScale),
                riverLevel
            );
        }
        double depthMax = depthMain * scale;

        double depthFactor = computeDepthFactorByValleyType(
            dist, coreWidth, valleyWidth,
            style.riverValleyType
        );

        depthFactor *= mask;

        if (depthFactor <= 0.0) {
            return baseHeight;
        }

        double depthBlocks = depthMax * depthFactor;

        double target = seaLevel - depthBlocks;
        if (target < 1.0) target = 1.0;
        return target;
    }

    /**
     * 根据横向距离 + 河谷半宽 + 类型，计算 0..1 的深度因子：
     *   - 1 ≈ 谷底（最深）
     *   - 0 ≈ 谷缘（无挖掘）
     */
    private static double computeDepthFactorByValleyType(double dist,
                                                         double coreWidth,
                                                         double valleyWidth,
                                                         MacroPackageRegistry.RiverValleyType type) {
        if (dist <= coreWidth) {
            return 1.0;
        }

        double denom = Math.max(1.0, valleyWidth - coreWidth);
        double t = (dist - coreWidth) / denom;
        t = clamp01(t);

        double u = 1.0 - t;

        switch (type) {
            case V_SHAPED:
                return Math.sqrt(u);

            case U_SHAPED:
            default:
                return u * u;
        }
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }
}
