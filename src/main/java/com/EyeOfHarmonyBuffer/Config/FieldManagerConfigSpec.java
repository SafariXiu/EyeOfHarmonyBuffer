package com.EyeOfHarmonyBuffer.Config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

public final class FieldManagerConfigSpec {

    private static Configuration config;

    private static final String CAT_MACRO_CACHE = "fieldManager.macroCache";
    private static final String CAT_SELECTOR = "fieldManager.macroSelector";
    private static final String CAT_SELECTOR_PATCH = "fieldManager.macroSelector.patch";
    private static final String CAT_SELECTOR_RARE = "fieldManager.macroSelector.rare";
    private static final String CAT_SELECTOR_CONTINENTAL = "fieldManager.macroSelector.continental";
    private static final String CAT_SELECTOR_DEBUG = "fieldManager.macroSelector.debug";

    private static final String CAT_TERRAIN = "fieldManager.terrain";
    private static final String CAT_CLIMATE_TEMPERATURE = "fieldManager.climate.temperature";
    private static final String CAT_CLIMATE_HUMIDITY = "fieldManager.climate.humidity";
    private static final String CAT_CLIMATE_RAINFALL = "fieldManager.climate.rainfall";
    private static final String CAT_CLIMATE_WIND = "fieldManager.climate.wind";
    private static final String CAT_CLIMATE_SEASON = "fieldManager.climate.season";
    private static final String CAT_HYDRO = "fieldManager.hydro";
    private static final String CAT_HYDRO_RIVER = "fieldManager.hydro.river";
    private static final String CAT_HYDRO_LAKE = "fieldManager.hydro.lake";
    private static final String CAT_HYDRO_COAST = "fieldManager.hydro.coastline";
    private static final String CAT_HYDRO_DIAG = "fieldManager.hydro.diagnostics";
    private static final String CAT_DIAGNOSTICS = "fieldManager.diagnostics";

    private FieldManagerConfigSpec() {}

    /* ===================== 对外字段 ===================== */

    // macro cache
    public static boolean macroCacheEnabled = true;
    public static int macroCacheMaxEntries = 512;
    public static boolean macroCacheDiagnostics = true;

    // selector - shared
    public static long selectorSeedSalt = 0x5EEDL;
    public static boolean selectorDebugLogging = false;

    // selector - voronoi macro/micro
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

    // selector - patch noise
    public static double selectorPatchFrequency = 0.0018d;
    public static int selectorPatchOctaves = 3;
    public static double selectorPatchLacunarity = 2.0d;
    public static double selectorPatchGain = 0.5d;
    public static double selectorPatchScale = 4096.0d;
    public static long selectorPatchSalt = 0x71A7105L;

    // selector - rare marker
    public static boolean selectorRareEnabled = true;
    public static double selectorRareFrequency = 0.0045d;
    public static double selectorRareThreshold = 0.83d;
    public static long selectorRareSalt = 0xBADBEEFL;

    // selector - continental blend
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

    // diagnostics
    public static boolean diagnosticsSampleUsePlayer = false;
    public static boolean diagnosticsSampleUseSpawn = true;
    public static int diagnosticsSampleX = 0;
    public static int diagnosticsSampleZ = 0;

    // terrain
    public static int terrainNoiseSeedOffset = 0;
    public static double terrainFrequency = 0.003d;
    public static double terrainAmplitude = 32.0d;
    public static double terrainBaseHeight = 64.0d;
    public static int terrainOctaves = 4;
    public static double terrainPersistence = 0.5d;
    public static double terrainLacunarity = 2.0d;
    public static int terrainSlopeSampleStep = 4;
    public static boolean terrainCacheEnabled = false;
    public static int terrainCacheSize = 256;

    // climate - temperature
    public static double climateTempBase = 0.6d;
    public static double climateTempVariance = 0.25d;
    public static double climateTempFrequency = 0.0025d;
    public static int climateTempOctaves = 3;
    public static int climateTempSeedOffset = 17;

    // climate - humidity
    public static double climateHumidityBase = 0.7d;
    public static double climateHumidityVariance = 0.2d;
    public static double climateHumidityFrequency = 0.002d;
    public static int climateHumidityOctaves = 3;
    public static int climateHumiditySeedOffset = 29;

    // climate - rainfall
    public static double climateRainfallBase = 0.45d;
    public static double climateRainfallVariance = 0.45d;
    public static double climateRainfallFrequency = 0.0013d;
    public static int climateRainfallOctaves = 3;
    public static int climateRainfallSeedOffset = 37;

    // climate - wind
    public static boolean climateWindEnabled = false;
    public static double climateWindDirectionVariance = 0.2d;
    public static double climateWindSpeedBase = 0.3d;
    public static double climateWindSpeedVariance = 0.15d;

    // climate - season
    public static boolean climateSeasonEnabled = false;
    public static double climateSeasonLengthDays = 96.0d;
    public static double climateSeasonPhaseOffset = 0.0d;
    public static double climateSeasonTemperatureAmplitude = 0.05d;
    public static double climateSeasonHumidityAmplitude = 0.03d;
    public static double climateSeasonRainfallAmplitude = 0.04d;

    // hydro
    public static double hydroSeaLevel = 63.0d;

    // hydro - river
    public static double hydroRiverFrequency = 0.0015d;
    public static double hydroRiverDetailFrequency = 0.006d;
    public static double hydroRiverStrength = 1.0d;
    public static double hydroRiverThreshold = 0.45d;
    public static int hydroRiverSeedOffset = 51;
    public static int hydroRiverSmoothRadius = 2;

    // hydro - lake
    public static boolean hydroLakeEnabled = false;
    public static double hydroLakeThreshold = 0.7d;
    public static int hydroLakeSeedOffset = 73;

    // hydro - coastline
    public static boolean hydroCoastSyncWithMacro = true;
    public static double hydroCoastFalloff = 16.0d;

    // hydro - diagnostics
    public static boolean hydroDiagLogSamples = false;
    public static int hydroDiagProbeInterval = 500;

    public static void init(File configFile) {
        if (config == null) {
            config = new Configuration(configFile);
            loadConfig();
        }
    }

    public static void reloadConfig() {
        if (config != null) {
            config.load();
            loadConfig();
        }
    }

    private static void loadConfig() {

        loadMacroCache();
        loadMacroSelector();
        loadDiagnostics();
        loadTerrain();
        loadClimate();
        loadHydro();

        if (config.hasChanged()) {
            config.save();
        }
    }

    private static void loadMacroCache() {
        macroCacheEnabled = config
            .get(CAT_MACRO_CACHE, "enabled", macroCacheEnabled,
                "是否启用宏格缓存（true=开启，false=关闭）。")
            .getBoolean(macroCacheEnabled);

        macroCacheMaxEntries = config
            .get(CAT_MACRO_CACHE, "maxEntries", macroCacheMaxEntries,
                "缓存最大条目数（>=32）。")
            .getInt(macroCacheMaxEntries);
        if (macroCacheMaxEntries < 32) {
            macroCacheMaxEntries = 32;
        }

        macroCacheDiagnostics = config
            .get(CAT_MACRO_CACHE, "diagnosticsEnabled", macroCacheDiagnostics,
                "是否启用缓存诊断统计。")
            .getBoolean(macroCacheDiagnostics);
    }

    private static void loadMacroSelector() {
        selectorSeedSalt = getLongProperty(
            CAT_SELECTOR,
            "noiseSeedSalt",
            selectorSeedSalt,
            "宏群系选择器内部噪声的 seed salt。"
        );

        selectorDebugLogging = config
            .get(CAT_SELECTOR_DEBUG, "enableDebugLogging", selectorDebugLogging,
                "选择器是否输出调试日志。")
            .getBoolean(selectorDebugLogging);

        selectorPatchFrequency = config
            .get(CAT_SELECTOR_PATCH, "frequency", selectorPatchFrequency,
                "Patch 噪声频率。")
            .getDouble(selectorPatchFrequency);

        selectorPatchOctaves = config
            .get(CAT_SELECTOR_PATCH, "octaves", selectorPatchOctaves,
                "Patch 噪声音阶数（>=1）。")
            .getInt(selectorPatchOctaves);
        if (selectorPatchOctaves < 1) {
            selectorPatchOctaves = 1;
        }

        selectorPatchLacunarity = config
            .get(CAT_SELECTOR_PATCH, "lacunarity", selectorPatchLacunarity,
                "Patch 噪声频率倍增系数。")
            .getDouble(selectorPatchLacunarity);

        selectorPatchGain = config
            .get(CAT_SELECTOR_PATCH, "gain", selectorPatchGain,
                "Patch 噪声振幅衰减系数。")
            .getDouble(selectorPatchGain);

        selectorPatchScale = config
            .get(CAT_SELECTOR_PATCH, "idScale", selectorPatchScale,
                "Patch ID 的缩放上限（>0）。")
            .getDouble(selectorPatchScale);
        if (selectorPatchScale < 1.0d) {
            selectorPatchScale = 1.0d;
        }

        selectorPatchSalt = getLongProperty(
            CAT_SELECTOR_PATCH,
            "salt",
            selectorPatchSalt,
            "Patch 噪声 salt。"
        );

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

        selectorRareSalt = getLongProperty(
            CAT_SELECTOR_RARE,
            "salt",
            selectorRareSalt,
            "稀有噪声 salt。"
        );

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

        selectorCoastScale = config
            .get(CAT_SELECTOR_CONTINENTAL, "coastScale", selectorCoastScale,
                "海岸距离归一化尺度（越大远海越慢接近 1）。")
            .getDouble(selectorCoastScale);
        if (selectorCoastScale < 1.0d) {
            selectorCoastScale = 1.0d;
        }

        selectorCoastBeachWidth = config
            .get(CAT_SELECTOR_CONTINENTAL, "beachWidth", selectorCoastBeachWidth,
                "海滩判定宽度（方块数，>=1）。")
            .getDouble(selectorCoastBeachWidth);
        if (selectorCoastBeachWidth < 1.0d) {
            selectorCoastBeachWidth = 1.0d;
        }

        selectorCoastShelfWidth = config
            .get(CAT_SELECTOR_CONTINENTAL, "shelfWidth", selectorCoastShelfWidth,
                "陆架（浅海）判定宽度（方块数，>=1）。")
            .getDouble(selectorCoastShelfWidth);
        if (selectorCoastShelfWidth < 1.0d) {
            selectorCoastShelfWidth = 1.0d;
        }

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

        selectorContinentalScale = config
            .get(CAT_SELECTOR_CONTINENTAL, "scale", selectorContinentalScale,
                "权重合成后映射到 [-1,1] 的缩放因子。")
            .getDouble(selectorContinentalScale);
        if (selectorContinentalScale <= 0.0d) {
            selectorContinentalScale = 1.0d;
        }

        selectorMacroGridSize = config
            .get(CAT_SELECTOR, "macroGridSize", selectorMacroGridSize,
                "Voronoi 宏站点索引网格尺寸（block 单位，建议 8192~12288）。")
            .getInt(selectorMacroGridSize);
        if (selectorMacroGridSize < 1024) {
            selectorMacroGridSize = 1024;
        }

        selectorMacroSiteSpacing = config
            .get(CAT_SELECTOR, "macroSiteSpacing", selectorMacroSiteSpacing,
                "宏站点平均间距（block 单位，建议 9000~11000）。")
            .getDouble(selectorMacroSiteSpacing);
        if (selectorMacroSiteSpacing < 2048.0d) {
            selectorMacroSiteSpacing = 2048.0d;
        }

        selectorMacroBlendWidth = config
            .get(CAT_SELECTOR, "macroBlendWidth", selectorMacroBlendWidth,
                "宏边界过渡带宽度 W（block 单位，建议 400~900）。")
            .getDouble(selectorMacroBlendWidth);
        if (selectorMacroBlendWidth < 64.0d) {
            selectorMacroBlendWidth = 64.0d;
        }

        selectorMacroSiteSalt = getLongProperty(
            CAT_SELECTOR,
            "macroSiteSalt",
            selectorMacroSiteSalt,
            "宏站点 jitter/ID 生成 salt。"
        );

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

        selectorEdgeNoiseSalt = getLongProperty(
            CAT_SELECTOR,
            "edgeNoiseSalt",
            selectorEdgeNoiseSalt,
            "宏边界破碎噪声 salt。"
        );

        selectorMicroGridSize = config
            .get(CAT_SELECTOR, "microGridSize", selectorMicroGridSize,
                "微站点索引网格尺寸（block 单位，建议 4096~6144）。")
            .getInt(selectorMicroGridSize);
        if (selectorMicroGridSize < 512) {
            selectorMicroGridSize = 512;
        }

        selectorMicroSiteSpacing = config
            .get(CAT_SELECTOR, "microSiteSpacing", selectorMicroSiteSpacing,
                "微站点平均间距（block 单位，建议 4000~6000）。")
            .getDouble(selectorMicroSiteSpacing);
        if (selectorMicroSiteSpacing < 1024.0d) {
            selectorMicroSiteSpacing = 1024.0d;
        }

        selectorMicroSiteSalt = getLongProperty(
            CAT_SELECTOR,
            "microSiteSalt",
            selectorMicroSiteSalt,
            "微站点 jitter/ID 生成 salt（请与宏 salt 区分）。"
        );
    }

    private static void loadTerrain() {
        terrainNoiseSeedOffset = config
            .get(CAT_TERRAIN, "noiseSeedOffset", terrainNoiseSeedOffset,
                "噪声 seed 偏移，避免与其他字段冲突。")
            .getInt(terrainNoiseSeedOffset);

        terrainFrequency = config
            .get(CAT_TERRAIN, "frequency", terrainFrequency,
                "基础噪声频率。")
            .getDouble(terrainFrequency);

        terrainAmplitude = config
            .get(CAT_TERRAIN, "amplitude", terrainAmplitude,
                "高度振幅。")
            .getDouble(terrainAmplitude);

        terrainBaseHeight = config
            .get(CAT_TERRAIN, "baseHeight", terrainBaseHeight,
                "平均地面高度。")
            .getDouble(terrainBaseHeight);

        terrainOctaves = config
            .get(CAT_TERRAIN, "octaves", terrainOctaves,
                "噪声叠加层数。")
            .getInt(terrainOctaves);

        terrainPersistence = config
            .get(CAT_TERRAIN, "persistence", terrainPersistence,
                "每层振幅衰减系数。")
            .getDouble(terrainPersistence);

        terrainLacunarity = config
            .get(CAT_TERRAIN, "lacunarity", terrainLacunarity,
                "每层频率放大倍数。")
            .getDouble(terrainLacunarity);

        terrainSlopeSampleStep = config
            .get(CAT_TERRAIN, "slopeSampleStep", terrainSlopeSampleStep,
                "坡度采样间隔（方块数，>=1）。")
            .getInt(terrainSlopeSampleStep);
        if (terrainSlopeSampleStep < 1) {
            terrainSlopeSampleStep = 1;
        }

        terrainCacheEnabled = config
            .get(CAT_TERRAIN, "cacheEnabled", terrainCacheEnabled,
                "是否启用 Terrain 局部缓存。")
            .getBoolean(terrainCacheEnabled);

        terrainCacheSize = config
            .get(CAT_TERRAIN, "cacheSize", terrainCacheSize,
                "Terrain 缓存条目数（>=32，仅 cacheEnabled=true 时有效）。")
            .getInt(terrainCacheSize);
        if (terrainCacheSize < 32) {
            terrainCacheSize = 32;
        }
    }

    private static void loadClimate() {
        // temperature
        climateTempBase = config
            .get(CAT_CLIMATE_TEMPERATURE, "base", climateTempBase,
                "基础温度（0~1）。")
            .getDouble(climateTempBase);

        climateTempVariance = config
            .get(CAT_CLIMATE_TEMPERATURE, "variance", climateTempVariance,
                "温度波动幅度。")
            .getDouble(climateTempVariance);

        climateTempFrequency = config
            .get(CAT_CLIMATE_TEMPERATURE, "frequency", climateTempFrequency,
                "温度噪声频率。")
            .getDouble(climateTempFrequency);

        climateTempOctaves = config
            .get(CAT_CLIMATE_TEMPERATURE, "octaves", climateTempOctaves,
                "温度噪声层数。")
            .getInt(climateTempOctaves);

        climateTempSeedOffset = config
            .get(CAT_CLIMATE_TEMPERATURE, "seedOffset", climateTempSeedOffset,
                "温度噪声 seed 偏移。")
            .getInt(climateTempSeedOffset);

        // humidity
        climateHumidityBase = config
            .get(CAT_CLIMATE_HUMIDITY, "base", climateHumidityBase,
                "基础湿度（0~1）。")
            .getDouble(climateHumidityBase);

        climateHumidityVariance = config
            .get(CAT_CLIMATE_HUMIDITY, "variance", climateHumidityVariance,
                "湿度波动幅度。")
            .getDouble(climateHumidityVariance);

        climateHumidityFrequency = config
            .get(CAT_CLIMATE_HUMIDITY, "frequency", climateHumidityFrequency,
                "湿度噪声频率。")
            .getDouble(climateHumidityFrequency);

        climateHumidityOctaves = config
            .get(CAT_CLIMATE_HUMIDITY, "octaves", climateHumidityOctaves,
                "湿度噪声层数。")
            .getInt(climateHumidityOctaves);

        climateHumiditySeedOffset = config
            .get(CAT_CLIMATE_HUMIDITY, "seedOffset", climateHumiditySeedOffset,
                "湿度噪声 seed 偏移。")
            .getInt(climateHumiditySeedOffset);

        // rainfall
        climateRainfallBase = config
            .get(CAT_CLIMATE_RAINFALL, "base", climateRainfallBase,
                "基础降水强度（0~1）。")
            .getDouble(climateRainfallBase);

        climateRainfallVariance = config
            .get(CAT_CLIMATE_RAINFALL, "variance", climateRainfallVariance,
                "降水波动幅度。")
            .getDouble(climateRainfallVariance);

        climateRainfallFrequency = config
            .get(CAT_CLIMATE_RAINFALL, "frequency", climateRainfallFrequency,
                "降水噪声频率。")
            .getDouble(climateRainfallFrequency);

        climateRainfallOctaves = config
            .get(CAT_CLIMATE_RAINFALL, "octaves", climateRainfallOctaves,
                "降水噪声层数。")
            .getInt(climateRainfallOctaves);

        climateRainfallSeedOffset = config
            .get(CAT_CLIMATE_RAINFALL, "seedOffset", climateRainfallSeedOffset,
                "降水噪声 seed 偏移。")
            .getInt(climateRainfallSeedOffset);

        // wind
        climateWindEnabled = config
            .get(CAT_CLIMATE_WIND, "enabled", climateWindEnabled,
                "是否计算风场。")
            .getBoolean(climateWindEnabled);

        climateWindDirectionVariance = config
            .get(CAT_CLIMATE_WIND, "directionVariance", climateWindDirectionVariance,
                "风向波动幅度。")
            .getDouble(climateWindDirectionVariance);

        climateWindSpeedBase = config
            .get(CAT_CLIMATE_WIND, "speedBase", climateWindSpeedBase,
                "基础风速。")
            .getDouble(climateWindSpeedBase);

        climateWindSpeedVariance = config
            .get(CAT_CLIMATE_WIND, "speedVariance", climateWindSpeedVariance,
                "风速波动幅度。")
            .getDouble(climateWindSpeedVariance);

        // season
        climateSeasonEnabled = config
            .get(CAT_CLIMATE_SEASON, "enabled", climateSeasonEnabled,
                "是否启用季节调制。")
            .getBoolean(climateSeasonEnabled);

        climateSeasonLengthDays = config
            .get(CAT_CLIMATE_SEASON, "lengthDays", climateSeasonLengthDays,
                "完整季节周期长度（天）。")
            .getDouble(climateSeasonLengthDays);
        if (climateSeasonLengthDays < 1.0d) {
            climateSeasonLengthDays = 1.0d;
        }

        climateSeasonPhaseOffset = config
            .get(CAT_CLIMATE_SEASON, "phaseOffset", climateSeasonPhaseOffset,
                "季节相位偏移（0~1）。")
            .getDouble(climateSeasonPhaseOffset);

        climateSeasonTemperatureAmplitude = config
            .get(CAT_CLIMATE_SEASON, "temperatureAmplitude", climateSeasonTemperatureAmplitude,
                "季节温度振幅。")
            .getDouble(climateSeasonTemperatureAmplitude);

        climateSeasonHumidityAmplitude = config
            .get(CAT_CLIMATE_SEASON, "humidityAmplitude", climateSeasonHumidityAmplitude,
                "季节湿度振幅。")
            .getDouble(climateSeasonHumidityAmplitude);

        climateSeasonRainfallAmplitude = config
            .get(CAT_CLIMATE_SEASON, "rainfallAmplitude", climateSeasonRainfallAmplitude,
                "季节降水振幅。")
            .getDouble(climateSeasonRainfallAmplitude);
    }

    private static void loadHydro() {
        hydroSeaLevel = config
            .get(CAT_HYDRO, "seaLevel", hydroSeaLevel,
                "海平面高度。")
            .getDouble(hydroSeaLevel);

        // river
        hydroRiverFrequency = config
            .get(CAT_HYDRO_RIVER, "frequency", hydroRiverFrequency,
                "主河网噪声频率。")
            .getDouble(hydroRiverFrequency);

        hydroRiverDetailFrequency = config
            .get(CAT_HYDRO_RIVER, "detailFrequency", hydroRiverDetailFrequency,
                "细节噪声频率。")
            .getDouble(hydroRiverDetailFrequency);

        hydroRiverStrength = config
            .get(CAT_HYDRO_RIVER, "strength", hydroRiverStrength,
                "河流强度系数。")
            .getDouble(hydroRiverStrength);

        hydroRiverThreshold = config
            .get(CAT_HYDRO_RIVER, "threshold", hydroRiverThreshold,
                "河流判定阈值（0~1，越低河越多）。")
            .getDouble(hydroRiverThreshold);

        hydroRiverSeedOffset = config
            .get(CAT_HYDRO_RIVER, "seedOffset", hydroRiverSeedOffset,
                "河流噪声 seed 偏移。")
            .getInt(hydroRiverSeedOffset);

        hydroRiverSmoothRadius = config
            .get(CAT_HYDRO_RIVER, "smoothRadius", hydroRiverSmoothRadius,
                "河流平滑半径（>=0）。")
            .getInt(hydroRiverSmoothRadius);
        if (hydroRiverSmoothRadius < 0) {
            hydroRiverSmoothRadius = 0;
        }

        // lake
        hydroLakeEnabled = config
            .get(CAT_HYDRO_LAKE, "enabled", hydroLakeEnabled,
                "是否生成湖泊/内陆海字段。")
            .getBoolean(hydroLakeEnabled);

        hydroLakeThreshold = config
            .get(CAT_HYDRO_LAKE, "threshold", hydroLakeThreshold,
                "湖泊判定阈值。")
            .getDouble(hydroLakeThreshold);

        hydroLakeSeedOffset = config
            .get(CAT_HYDRO_LAKE, "seedOffset", hydroLakeSeedOffset,
                "湖泊噪声 seed 偏移。")
            .getInt(hydroLakeSeedOffset);

        // coastline
        hydroCoastSyncWithMacro = config
            .get(CAT_HYDRO_COAST, "syncWithMacro", hydroCoastSyncWithMacro,
                "是否与宏格海岸线保持一致。")
            .getBoolean(hydroCoastSyncWithMacro);

        hydroCoastFalloff = config
            .get(CAT_HYDRO_COAST, "falloff", hydroCoastFalloff,
                "海岸平滑过渡宽度。")
            .getDouble(hydroCoastFalloff);

        // diagnostics
        hydroDiagLogSamples = config
            .get(CAT_HYDRO_DIAG, "logSamples", hydroDiagLogSamples,
                "是否额外输出 Hydro 样本日志。")
            .getBoolean(hydroDiagLogSamples);

        hydroDiagProbeInterval = config
            .get(CAT_HYDRO_DIAG, "probeInterval", hydroDiagProbeInterval,
                "诊断日志间隔（采样次数）。")
            .getInt(hydroDiagProbeInterval);
        if (hydroDiagProbeInterval < 1) {
            hydroDiagProbeInterval = 1;
        }
    }

    private static void loadDiagnostics() {
        diagnosticsSampleUsePlayer = config
            .get(CAT_DIAGNOSTICS, "usePlayerPosition", diagnosticsSampleUsePlayer,
                "诊断采样是否使用在线玩家位置（true=使用玩家坐标，false=根据 useSpawn/sampleX/Z）。")
            .getBoolean(diagnosticsSampleUsePlayer);

        diagnosticsSampleUseSpawn = config
            .get(CAT_DIAGNOSTICS, "useSpawn", diagnosticsSampleUseSpawn,
                "诊断采样是否使用世界重生点（true=使用 spawn，false=使用 sampleX/Z）。")
            .getBoolean(diagnosticsSampleUseSpawn);

        diagnosticsSampleX = config
            .get(CAT_DIAGNOSTICS, "sampleX", diagnosticsSampleX,
                "diagnosticsUseSpawn=false 时的采样 X 坐标。")
            .getInt(diagnosticsSampleX);

        diagnosticsSampleZ = config
            .get(CAT_DIAGNOSTICS, "sampleZ", diagnosticsSampleZ,
                "diagnosticsUseSpawn=false 时的采样 Z 坐标。")
            .getInt(diagnosticsSampleZ);
    }

    private static long getLongProperty(String category, String key, long defaultValue, String comment) {
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
}
