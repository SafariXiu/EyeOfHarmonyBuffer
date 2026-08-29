package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

public final class MacroSelectorConfigSection {

    private static final String CAT_SELECTOR = "fieldmanager.macroselector";
    private static final String CAT_SELECTOR_PATCH = "fieldmanager.macroselector.patch";
    private static final String CAT_SELECTOR_RARE = "fieldmanager.macroselector.rare";
    private static final String CAT_SELECTOR_CONTINENTAL = "fieldmanager.macroselector.continental";
    private static final String CAT_SELECTOR_DEBUG = "fieldmanager.macroselector.debug";
    private static final String CAT_SELECTOR_LATITUDE = "fieldmanager.macroselector.latitude";

    public static long selectorSeedSalt = 0x5EEDL;
    public static boolean selectorDebugLogging = false;

    public static double selectorLatitudePeriod = 48000.0d;
    public static double selectorLatitudeBlendWidth = 0.12d;
    public static double selectorLatitudeBaseBias = 0.0d;
    public static double selectorLatitudeMixWeight = 0.25d;
    public static double selectorLatitudeWarpScale = 0.0006d;
    public static double selectorLatitudeWarpAmplitude = 0.08d;
    public static long selectorLatitudeWarpSalt = 0x71A7105EL;

    public static double selectorContinentalLandThreshold = 0.45d;
    public static double selectorCoastSoftBandWidth = 0.08d;
    public static double selectorOverrideLandScoreThreshold = 0.28d;
    public static double selectorOverrideMinShelfWidth = 48.0d;

    public static int selectorMacroGridSize = 8192;
    public static double selectorMacroSiteSpacing = 10000.0d;
    public static double selectorMacroBlendWidth = 700.0d;
    public static long selectorMacroSiteSalt = 0xC0FFEE11L;
    public static double selectorEdgeNoiseFrequency = 1.0d / 512.0d;
    public static double selectorEdgeNoiseAmplitude = 1.0d;
    public static long selectorEdgeNoiseSalt = 0xED9E5101L;

    public static int selectorMicroGridSize = 4096;
    public static double selectorMicroSiteSpacing = 5000.0d;
    public static long selectorMicroSiteSalt = 0x1CE0BEEFCL;

    public static double selectorPatchFrequency = 0.0018d;
    public static int selectorPatchOctaves = 3;
    public static double selectorPatchLacunarity = 2.0d;
    public static double selectorPatchGain = 0.5d;
    public static double selectorPatchScale = 4096.0d;
    public static long selectorPatchSalt = 0x71A7105L;

    public static boolean selectorRareEnabled = true;
    public static double selectorRareFrequency = 0.0045d;
    public static double selectorRareThreshold = 0.83d;
    public static long selectorRareSalt = 0xBADBEEFL;

    public static double selectorElevationMin = 40.0d;
    public static double selectorElevationMax = 160.0d;
    public static double selectorElevationWeight = 0.5d;
    public static double selectorCoastScale = 96.0d;
    public static double selectorCoastWeight = 0.3d;
    public static double selectorHydroWeight = 0.2d;
    public static double selectorContinentalPivot = 0.45d;
    public static double selectorContinentalScale = 2.2d;
    public static double selectorCoastBeachWidth = 24.0d;
    public static double selectorCoastShelfWidth = 48.0d;

    public MacroSelectorConfigSection() {}

    public static void load(Configuration config) {
        selectorSeedSalt = getLongProperty(config, CAT_SELECTOR, "noiseSeedSalt",
            selectorSeedSalt, "宏群系选择器内部噪声的 seed salt。");

        selectorDebugLogging = config
            .get(CAT_SELECTOR_DEBUG, "enableDebugLogging", selectorDebugLogging,
                "选择器是否输出调试日志。")
            .getBoolean(selectorDebugLogging);

        selectorPatchFrequency = config
            .get(CAT_SELECTOR_PATCH, "frequency", selectorPatchFrequency,
                "Patch 噪声频率。")
            .getDouble(selectorPatchFrequency);

        selectorPatchOctaves = Math.max(1,
            config.get(CAT_SELECTOR_PATCH, "octaves", selectorPatchOctaves,
                    "Patch 噪声音阶数（>=1）。")
                .getInt(selectorPatchOctaves)
        );

        selectorPatchLacunarity = config
            .get(CAT_SELECTOR_PATCH, "lacunarity", selectorPatchLacunarity,
                "Patch 噪声频率倍增系数。")
            .getDouble(selectorPatchLacunarity);

        selectorPatchGain = config
            .get(CAT_SELECTOR_PATCH, "gain", selectorPatchGain,
                "Patch 噪声振幅衰减系数。")
            .getDouble(selectorPatchGain);

        selectorPatchScale = Math.max(1.0d,
            config.get(CAT_SELECTOR_PATCH, "idScale", selectorPatchScale,
                    "Patch ID 的缩放上限（>0）。")
                .getDouble(selectorPatchScale)
        );

        selectorPatchSalt = getLongProperty(config, CAT_SELECTOR_PATCH, "salt",
            selectorPatchSalt, "Patch 噪声 salt。");

        selectorRareEnabled = config
            .get(CAT_SELECTOR_RARE, "enabled", selectorRareEnabled,
                "是否开启稀有标记。")
            .getBoolean(selectorRareEnabled);

        selectorRareFrequency = config
            .get(CAT_SELECTOR_RARE, "frequency", selectorRareFrequency,
                "稀有噪声频率。")
            .getDouble(selectorRareFrequency);

        selectorRareThreshold = config
            .get(CAT_SELECTOR_RARE, "threshold", selectorRareThreshold,
                "稀有判定阈值（0~1，越低越容易触发）。")
            .getDouble(selectorRareThreshold);

        selectorRareSalt = getLongProperty(config, CAT_SELECTOR_RARE, "salt",
            selectorRareSalt, "稀有噪声 salt。");

        selectorElevationMin = config
            .get(CAT_SELECTOR_CONTINENTAL, "elevationMin", selectorElevationMin,
                "海陆判定参考的最低海拔。")
            .getDouble(selectorElevationMin);

        selectorElevationMax = config
            .get(CAT_SELECTOR_CONTINENTAL, "elevationMax", selectorElevationMax,
                "海陆判定参考的最高海拔。")
            .getDouble(selectorElevationMax);
        if (selectorElevationMax <= selectorElevationMin) {
            selectorElevationMax = selectorElevationMin + 1.0d;
        }

        selectorElevationWeight = config
            .get(CAT_SELECTOR_CONTINENTAL, "elevationWeight", selectorElevationWeight,
                "海拔对大陆度的权重。")
            .getDouble(selectorElevationWeight);

        selectorCoastScale = Math.max(1.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "coastScale", selectorCoastScale,
                    "海岸距离归一化尺度（越大远海越慢接近 1）。")
                .getDouble(selectorCoastScale)
        );

        selectorCoastBeachWidth = Math.max(1.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "beachWidth", selectorCoastBeachWidth,
                    "海滩判定宽度（方块数，>=1）。")
                .getDouble(selectorCoastBeachWidth)
        );

        selectorCoastShelfWidth = Math.max(1.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "shelfWidth", selectorCoastShelfWidth,
                    "陆架（浅海）判定宽度（方块数，>=1）。")
                .getDouble(selectorCoastShelfWidth)
        );

        selectorCoastWeight = config
            .get(CAT_SELECTOR_CONTINENTAL, "coastWeight", selectorCoastWeight,
                "海岸距离对大陆度的权重。")
            .getDouble(selectorCoastWeight);

        selectorHydroWeight = config
            .get(CAT_SELECTOR_CONTINENTAL, "hydroWeight", selectorHydroWeight,
                "水文（湿度/饱和度）对大陆度的权重。")
            .getDouble(selectorHydroWeight);

        selectorContinentalPivot = config
            .get(CAT_SELECTOR_CONTINENTAL, "pivot", selectorContinentalPivot,
                "权重合成后映射到 [-1,1] 的枢轴点（0~1）。")
            .getDouble(selectorContinentalPivot);

        selectorContinentalScale = Math.max(1.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "scale", selectorContinentalScale,
                    "权重合成后映射到 [-1,1] 的缩放因子。")
                .getDouble(selectorContinentalScale)
        );

        selectorContinentalLandThreshold = clamp01(
            config.get(CAT_SELECTOR_CONTINENTAL, "landThreshold", selectorContinentalLandThreshold,
                    "大陆度 >= 该值判定为陆地（0 按旧行为，1 全部视为海洋）。")
                .getDouble(selectorContinentalLandThreshold)
        );

        selectorCoastSoftBandWidth = Math.max(0.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "coastSoftBandWidth", selectorCoastSoftBandWidth,
                    "围绕 landThreshold 计算海岸柔化的窗口宽度（越大海岸过渡越宽，0 关闭 soft band）。")
                .getDouble(selectorCoastSoftBandWidth)
        );

        selectorOverrideLandScoreThreshold = clamp01(
            config.get(CAT_SELECTOR_CONTINENTAL, "overrideLandScoreThreshold",
                    selectorOverrideLandScoreThreshold,
                    "当大陆度 >= 该值且远离陆架时，强制把宏域视为陆地。")
                .getDouble(selectorOverrideLandScoreThreshold)
        );
        if (selectorOverrideLandScoreThreshold <= 0.0d) {
            selectorOverrideLandScoreThreshold = 0.01d;
        } else if (selectorOverrideLandScoreThreshold >= 1.0d) {
            selectorOverrideLandScoreThreshold = 0.99d;
        }

        selectorOverrideMinShelfWidth = Math.max(0.0d,
            config.get(CAT_SELECTOR_CONTINENTAL, "overrideMinShelfWidth",
                    selectorOverrideMinShelfWidth,
                    "Override 生效所需的最小海岸距离（方块数，>陆架宽度即视为真实陆地）。")
                .getDouble(selectorOverrideMinShelfWidth)
        );

        selectorMacroGridSize = Math.max(1024,
            config.get(CAT_SELECTOR, "macroGridSize", selectorMacroGridSize,
                    "Voronoi 宏站点索引网格尺寸（block 单位，建议 8192~12288）。")
                .getInt(selectorMacroGridSize)
        );

        selectorMacroSiteSpacing = Math.max(2048.0d,
            config.get(CAT_SELECTOR, "macroSiteSpacing", selectorMacroSiteSpacing,
                    "宏站点平均间距（block 单位，建议 9000~11000）。")
                .getDouble(selectorMacroSiteSpacing)
        );

        selectorMacroBlendWidth = Math.max(64.0d,
            config.get(CAT_SELECTOR, "macroBlendWidth", selectorMacroBlendWidth,
                    "宏边界过渡带宽度 W（block 单位，建议 400~900）。")
                .getDouble(selectorMacroBlendWidth)
        );

        selectorMacroSiteSalt = getLongProperty(config, CAT_SELECTOR, "macroSiteSalt",
            selectorMacroSiteSalt, "宏站点 jitter/ID 生成 salt。");

        selectorEdgeNoiseFrequency = config
            .get(CAT_SELECTOR, "edgeNoiseFrequency", selectorEdgeNoiseFrequency,
                "宏边界破碎噪声频率（1/波长，建议约 1/512）。")
            .getDouble(selectorEdgeNoiseFrequency);
        if (selectorEdgeNoiseFrequency <= 0.0d) {
            selectorEdgeNoiseFrequency = 1.0d / 512.0d;
        }

        selectorEdgeNoiseAmplitude = config
            .get(CAT_SELECTOR, "edgeNoiseAmplitude", selectorEdgeNoiseAmplitude,
                "宏边界破碎噪声振幅（0~1，控制翻转概率曲线）。")
            .getDouble(selectorEdgeNoiseAmplitude);

        selectorEdgeNoiseSalt = getLongProperty(config, CAT_SELECTOR, "edgeNoiseSalt",
            selectorEdgeNoiseSalt, "宏边界破碎噪声 salt。");

        selectorMicroGridSize = Math.max(512,
            config.get(CAT_SELECTOR, "microGridSize", selectorMicroGridSize,
                    "微站点索引网格尺寸（block 单位，建议 4096~6144）。")
                .getInt(selectorMicroGridSize)
        );

        selectorMicroSiteSpacing = Math.max(1024.0d,
            config.get(CAT_SELECTOR, "microSiteSpacing", selectorMicroSiteSpacing,
                    "微站点平均间距（block 单位，建议 4000~6000）。")
                .getDouble(selectorMicroSiteSpacing)
        );

        selectorMicroSiteSalt = getLongProperty(config, CAT_SELECTOR, "microSiteSalt",
            selectorMicroSiteSalt, "微站点 jitter/ID 生成 salt（请与宏 salt 区分）。");

        selectorLatitudePeriod = Math.max(1.0d,
            config.get(CAT_SELECTOR_LATITUDE, "periodBlocks", selectorLatitudePeriod,
                    "Z 轴上从极地到极地的完整周期长度（方块数）。")
                .getDouble(selectorLatitudePeriod)
        );

        selectorLatitudeBlendWidth = clamp01(
            config.get(CAT_SELECTOR_LATITUDE, "blendWidth", selectorLatitudeBlendWidth,
                    "纬度带之间的重叠宽度（0~1，影响 band 筛选柔化程度）。")
                .getDouble(selectorLatitudeBlendWidth)
        );

        selectorLatitudeBaseBias = clamp01(
            config.get(CAT_SELECTOR_LATITUDE, "baseBias", selectorLatitudeBaseBias,
                    "纬度相位基础偏移（0~1，可整体北移/南移带状分布）。")
                .getDouble(selectorLatitudeBaseBias)
        );

        selectorLatitudeMixWeight = clamp01(
            config.get(CAT_SELECTOR_LATITUDE, "mixWeight", selectorLatitudeMixWeight,
                    "南北镜像混合权重（0=不混合，1=完全对称）。")
                .getDouble(selectorLatitudeMixWeight)
        );

        selectorLatitudeWarpScale = Math.max(0.0d,
            config.get(CAT_SELECTOR_LATITUDE, "warpScale", selectorLatitudeWarpScale,
                    "纬度扰动噪声的输入缩放（越大曲度越频繁，0=禁用扰动）。")
                .getDouble(selectorLatitudeWarpScale)
        );

        selectorLatitudeWarpAmplitude = Math.max(0.0d,
            config.get(CAT_SELECTOR_LATITUDE, "warpAmplitude", selectorLatitudeWarpAmplitude,
                    "纬度扰动幅度（0=禁用扰动）。")
                .getDouble(selectorLatitudeWarpAmplitude)
        );

        selectorLatitudeWarpSalt = getLongProperty(config, CAT_SELECTOR_LATITUDE, "warpSalt",
            selectorLatitudeWarpSalt, "纬度扰动噪声的 salt。");
    }

    private static long getLongProperty(Configuration config, String category, String key,
                                        long defaultValue, String comment) {
        Property property = config.get(category, key, Long.toString(defaultValue), comment);
        String raw = property.getString();

        if (raw == null || raw.trim().isEmpty()) {
            property.set(Long.toString(defaultValue));
            return defaultValue;
        }

        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            property.set(Long.toString(defaultValue));
            return defaultValue;
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
