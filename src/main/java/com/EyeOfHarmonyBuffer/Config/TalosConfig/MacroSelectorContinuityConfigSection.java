package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class MacroSelectorContinuityConfigSection {

    private static final String CATEGORY = "fieldmanager.macroselector.continuity";

    public static boolean continuityEnabled = true;
    public static double globalFieldWeight = 0.6d;
    public static double maxNeighborDelta = 0.35d;
    public static int smoothingRadius = 2;
    public static int relaxIterations = 2;
    public static double maxEdgeDelta = 0.45d;
    public static double varianceFalloff = 2.0d;
    public static double gridBlurStrength = 0.4d;
    public static boolean finalPassEnabled = true;
    public static double finalBlendStrength = 0.85d;
    public static int finalPointSampleRadius = 24;
    public static double finalMaxDelta = 0.08d;

    private MacroSelectorContinuityConfigSection() {}

    public static void load(Configuration config) {

        continuityEnabled = config
            .get(CATEGORY, "enabled", continuityEnabled,
                "是否启用宏群系高度连续性系统。")
            .getBoolean(continuityEnabled);

        globalFieldWeight = clamp01(config
            .get(CATEGORY, "globalFieldWeight", globalFieldWeight,
                "全局高度场占比（0=完全依赖群系，1=完全跟随全局高度）。")
            .getDouble(globalFieldWeight));

        maxNeighborDelta = clamp01(config
            .get(CATEGORY, "maxNeighborDelta", maxNeighborDelta,
                "相邻站点允许的最大归一化高度差（0~1）。")
            .getDouble(maxNeighborDelta));

        smoothingRadius = Math.max(1,
            config.get(CATEGORY, "smoothingRadius", smoothingRadius,
                    "邻域平滑时的半径（>=1）。")
                .getInt(smoothingRadius));

        relaxIterations = Math.max(0,
            config.get(CATEGORY, "relaxIterations", relaxIterations,
                    "邻域平滑迭代次数。")
                .getInt(relaxIterations));

        maxEdgeDelta = clamp01(config
            .get(CATEGORY, "maxEdgeDelta", maxEdgeDelta,
                "边界混合时允许的最大高度差（归一化）。")
            .getDouble(maxEdgeDelta));

        varianceFalloff = Math.max(0.5d,
            config.get(CATEGORY, "varianceFalloff", varianceFalloff,
                    "宏/微方差在边缘的衰减指数。")
                .getDouble(varianceFalloff));

        gridBlurStrength = clamp01(config
            .get(CATEGORY, "gridBlurStrength", gridBlurStrength,
                "控制网模糊强度（0=不模糊，1=完全取平均）。")
            .getDouble(gridBlurStrength));

        finalPassEnabled = config
            .get(CATEGORY, "finalPassEnabled", finalPassEnabled,
                "是否启用最终高度平滑层。")
            .getBoolean(finalPassEnabled);

        finalBlendStrength = clamp01(config
            .get(CATEGORY, "finalBlendStrength", finalBlendStrength,
                "最终平滑插值强度，1=完全取平均。")
            .getDouble(finalBlendStrength));

        finalPointSampleRadius = Math.max(1,
            config.get(CATEGORY, "finalPointSampleRadius", finalPointSampleRadius,
                    "单点站点最终平滑时采样半径（方块数，>=1）。")
                .getInt(finalPointSampleRadius));

        finalMaxDelta = clamp01(config
            .get(CATEGORY, "finalMaxDelta", finalMaxDelta,
                "最终平滑后允许的最大归一化高度偏差。")
            .getDouble(finalMaxDelta));
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
