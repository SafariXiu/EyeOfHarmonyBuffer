package com.EyeOfHarmonyBuffer.Config.TalosConfig;

import net.minecraftforge.common.config.Configuration;

public final class ClimateConfigSection {

    private static final String CAT_CLIMATE_TEMPERATURE = "fieldmanager.climate.temperature";
    private static final String CAT_CLIMATE_HUMIDITY = "fieldmanager.climate.humidity";
    private static final String CAT_CLIMATE_RAINFALL = "fieldmanager.climate.rainfall";
    private static final String CAT_CLIMATE_WIND = "fieldmanager.climate.wind";
    private static final String CAT_CLIMATE_SEASON = "fieldmanager.climate.season";

    // temperature
    public static double climateTempBase = 0.6d;
    public static double climateTempVariance = 0.25d;
    public static double climateTempFrequency = 0.0025d;
    public static int climateTempOctaves = 3;
    public static int climateTempSeedOffset = 17;

    // humidity
    public static double climateHumidityBase = 0.7d;
    public static double climateHumidityVariance = 0.2d;
    public static double climateHumidityFrequency = 0.002d;
    public static int climateHumidityOctaves = 3;
    public static int climateHumiditySeedOffset = 29;

    // rainfall
    public static double climateRainfallBase = 0.45d;
    public static double climateRainfallVariance = 0.45d;
    public static double climateRainfallFrequency = 0.0013d;
    public static int climateRainfallOctaves = 3;
    public static int climateRainfallSeedOffset = 37;

    // wind
    public static boolean climateWindEnabled = false;
    public static double climateWindDirectionVariance = 0.2d;
    public static double climateWindSpeedBase = 0.3d;
    public static double climateWindSpeedVariance = 0.15d;

    // season
    public static boolean climateSeasonEnabled = false;
    public static double climateSeasonLengthDays = 96.0d;
    public static double climateSeasonPhaseOffset = 0.0d;
    public static double climateSeasonTemperatureAmplitude = 0.05d;
    public static double climateSeasonHumidityAmplitude = 0.03d;
    public static double climateSeasonRainfallAmplitude = 0.04d;

    public ClimateConfigSection() {}

    public static void load(Configuration config) {
        loadTemperature(config);
        loadHumidity(config);
        loadRainfall(config);
        loadWind(config);
        loadSeason(config);
    }

    private static void loadTemperature(Configuration config) {
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

        climateTempOctaves = Math.max(1,
            config.get(CAT_CLIMATE_TEMPERATURE, "octaves", climateTempOctaves,
                    "温度噪声层数（>=1）。")
                .getInt(climateTempOctaves)
        );

        climateTempSeedOffset = config
            .get(CAT_CLIMATE_TEMPERATURE, "seedOffset", climateTempSeedOffset,
                "温度噪声 seed 偏移。")
            .getInt(climateTempSeedOffset);
    }

    private static void loadHumidity(Configuration config) {
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

        climateHumidityOctaves = Math.max(1,
            config.get(CAT_CLIMATE_HUMIDITY, "octaves", climateHumidityOctaves,
                    "湿度噪声层数（>=1）。")
                .getInt(climateHumidityOctaves)
        );

        climateHumiditySeedOffset = config
            .get(CAT_CLIMATE_HUMIDITY, "seedOffset", climateHumiditySeedOffset,
                "湿度噪声 seed 偏移。")
            .getInt(climateHumiditySeedOffset);
    }

    private static void loadRainfall(Configuration config) {
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

        climateRainfallOctaves = Math.max(1,
            config.get(CAT_CLIMATE_RAINFALL, "octaves", climateRainfallOctaves,
                    "降水噪声层数（>=1）。")
                .getInt(climateRainfallOctaves)
        );

        climateRainfallSeedOffset = config
            .get(CAT_CLIMATE_RAINFALL, "seedOffset", climateRainfallSeedOffset,
                "降水噪声 seed 偏移。")
            .getInt(climateRainfallSeedOffset);
    }

    private static void loadWind(Configuration config) {
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
    }

    private static void loadSeason(Configuration config) {
        climateSeasonEnabled = config
            .get(CAT_CLIMATE_SEASON, "enabled", climateSeasonEnabled,
                "是否启用季节调制。")
            .getBoolean(climateSeasonEnabled);

        climateSeasonLengthDays = Math.max(1.0d,
            config.get(CAT_CLIMATE_SEASON, "lengthDays", climateSeasonLengthDays,
                    "完整季节周期长度（天，>=1）。")
                .getDouble(climateSeasonLengthDays)
        );

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
}
