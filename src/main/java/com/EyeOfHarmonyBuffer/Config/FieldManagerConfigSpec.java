package com.EyeOfHarmonyBuffer.Config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

public final class FieldManagerConfigSpec {

    private static Configuration config;

    private static final String CAT_MACRO_CACHE = "fieldManager.macroCache";
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
}
