package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;

public final class TalosRiverProfile {

    private TalosRiverProfile() {}

    // 源头湖 / 暗河井参数（与旧 TalosRiverCarver 保持一致）
    private static final double BASE_LAKE_RADIUS = 48.0;
    private static final double LAKE_RADIUS_PREFACTOR = 1.5;
    private static final double LAKE_CENTER_MIN_DEPTH = 16.0;
    private static final double SHAFT_RADIUS_FACTOR = 0.18;
    private static final double UNDERGROUND_EXTRA_DEPTH = 32.0;

    private static final double LAKE_AMP1 = 0.18;
    private static final double LAKE_AMP2 = 0.10;
    private static final int LAKE_K1 = 3;
    private static final int LAKE_K2 = 5;

    /**
     * 高度场雕刻用的完整河床目标高度（含源头湖 / 暗河井）。
     *
     * 与 computeRiverBedY 的区别：
     *   - 在 computeRiverBedY 基础上叠加源头湖的湖盆与暗河井深度；
     *   - 带与旧 TalosRiverCarver 一致的雕刻门槛：只在河谷内或源头湖
     *     范围内下挖，其它位置返回 baseHeightD（原高度）；
     *   - 返回值为"河床目标 Y"，调用方用 min(height, bedY) 落地，
     *     从而让河谷两侧自然成坡，而不是事后垂直切方块。
     */
    public static double computeChannelBedY(int worldX, int worldZ,
                                            double baseHeightD,
                                            int seaLevel,
                                            TalosRiverSystem.HydroSample hydro,
                                            MacroPackageId macroId) {
        double dist = hydro.distance;
        double valleyWidth = hydro.widthValley;
        double mask = hydro.mask;

        if (mask <= 0.0 || dist == Double.MAX_VALUE || valleyWidth <= 0.0) {
            return baseHeightD;
        }

        boolean inSourceLake = false;
        double sx = hydro.sourceX;
        double sz = hydro.sourceZ;
        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;
        double dxs = 0.0;
        double dzs = 0.0;
        double radialFromSource = 0.0;

        if (hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {
            dxs = sampleX - sx;
            dzs = sampleZ - sz;
            radialFromSource = Math.sqrt(dxs * dxs + dzs * dzs);

            if (radialFromSource <= BASE_LAKE_RADIUS * LAKE_RADIUS_PREFACTOR) {
                inSourceLake = true;
            }
        }

        if (!inSourceLake && dist > valleyWidth) {
            return baseHeightD;
        }

        if (macroId == null || macroId == MacroPackageId.OCEANIC) {
            return baseHeightD;
        }

        double riverBedYd = computeRiverBedY(baseHeightD, seaLevel, hydro, macroId);

        if (inSourceLake && hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {
            if (radialFromSource > 0.0001) {
                double theta = Math.atan2(dzs, dxs); // [-pi, pi]

                double radiusFactor =
                    1.0
                        + LAKE_AMP1 * Math.sin(LAKE_K1 * theta)
                        + LAKE_AMP2 * Math.cos(LAKE_K2 * theta);

                double minFactor = 0.75;
                double maxFactor = 1.30;
                if (radiusFactor < minFactor) radiusFactor = minFactor;
                if (radiusFactor > maxFactor) radiusFactor = maxFactor;

                double effectiveRadius = BASE_LAKE_RADIUS * radiusFactor;

                if (radialFromSource <= effectiveRadius) {
                    double rNorm = radialFromSource / effectiveRadius;
                    if (rNorm > 1.0) rNorm = 1.0;

                    double u = 1.0 - rNorm;
                    if (u < 0.0) u = 0.0;

                    double lakeShape = u * u;

                    double baseDepth = seaLevel - riverBedYd;
                    if (baseDepth < 0.0) baseDepth = 0.0;

                    double lakeDepth = LAKE_CENTER_MIN_DEPTH * lakeShape;
                    double finalDepth = Math.max(baseDepth, lakeDepth);

                    double shaftRadius = BASE_LAKE_RADIUS * SHAFT_RADIUS_FACTOR;

                    if (radialFromSource <= shaftRadius) {
                        double rCoreNorm = radialFromSource / shaftRadius;
                        if (rCoreNorm > 1.0) rCoreNorm = 1.0;

                        double shaftShape = 1.0 - rCoreNorm;
                        if (shaftShape < 0.0) shaftShape = 0.0;

                        double targetShaftCenterDepth =
                            LAKE_CENTER_MIN_DEPTH + UNDERGROUND_EXTRA_DEPTH;

                        double shaftDepthSmooth = targetShaftCenterDepth * shaftShape;

                        int shaftSteps = 4;
                        double stepSize = targetShaftCenterDepth / shaftSteps;

                        int stepIndex = (int) Math.floor(shaftDepthSmooth / stepSize);
                        if (stepIndex < 0) stepIndex = 0;
                        if (stepIndex >= shaftSteps) stepIndex = shaftSteps - 1;

                        double shaftDepth = (stepIndex + 1) * stepSize;
                        finalDepth = Math.max(finalDepth, shaftDepth);
                    }

                    riverBedYd = seaLevel - finalDepth;
                }
            }
        }

        int riverBedY = (int) Math.floor(riverBedYd);
        if (riverBedY < 1) {
            riverBedY = 1;
        }
        if (riverBedY >= seaLevel) {
            return baseHeightD;
        }
        return riverBedY;
    }

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
