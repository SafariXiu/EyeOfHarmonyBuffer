package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.ClimateConfigSection;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record ClimateProviderSettings(
    NoiseChannel temperature,
    NoiseChannel humidity,
    NoiseChannel rainfall,
    WindSettings wind,
    SeasonSettings season,
    double lacunarity,
    double persistence
) {
    private static final double DEFAULT_LACUNARITY = 2.0d;
    private static final double DEFAULT_PERSISTENCE = 0.55d;
    private static final double TICKS_PER_DAY = 24000.0d;

    @Desugar
    public record NoiseChannel(
        double base,
        double variance,
        double frequency,
        int octaves,
        int seedOffset
    ) {}

    @Desugar
    public record WindSettings(
        boolean enabled,
        double directionVariance,
        double speedBase,
        double speedVariance
    ) {}

    @Desugar
    public record SeasonSettings(
        boolean enabled,
        double cycleTicks,
        double phaseOffset,
        double temperatureAmplitude,
        double humidityAmplitude,
        double rainfallAmplitude
    ) {}

    public static ClimateProviderSettings defaults() {
        return new ClimateProviderSettings(
            new NoiseChannel(0.6d, 0.25d, 0.0025d, 3, 17),
            new NoiseChannel(0.7d, 0.20d, 0.0020d, 3, 29),
            new NoiseChannel(0.45d, 0.45d, 0.0013d, 3, 37),
            new WindSettings(false, 0.2d, 0.3d, 0.15d),
            new SeasonSettings(false, 96.0d * TICKS_PER_DAY, 0.0d, 0.05d, 0.03d, 0.04d),
            DEFAULT_LACUNARITY,
            DEFAULT_PERSISTENCE
        );
    }

    public static ClimateProviderSettings fromConfig() {
        NoiseChannel temperatureChannel = new NoiseChannel(
            ClimateConfigSection.climateTempBase,
            ClimateConfigSection.climateTempVariance,
            ClimateConfigSection.climateTempFrequency,
            ClimateConfigSection.climateTempOctaves,
            ClimateConfigSection.climateTempSeedOffset
        );

        NoiseChannel humidityChannel = new NoiseChannel(
            ClimateConfigSection.climateHumidityBase,
            ClimateConfigSection.climateHumidityVariance,
            ClimateConfigSection.climateHumidityFrequency,
            ClimateConfigSection.climateHumidityOctaves,
            ClimateConfigSection.climateHumiditySeedOffset
        );

        NoiseChannel rainfallChannel = new NoiseChannel(
            ClimateConfigSection.climateRainfallBase,
            ClimateConfigSection.climateRainfallVariance,
            ClimateConfigSection.climateRainfallFrequency,
            ClimateConfigSection.climateRainfallOctaves,
            ClimateConfigSection.climateRainfallSeedOffset
        );

        WindSettings windSettings = new WindSettings(
            ClimateConfigSection.climateWindEnabled,
            ClimateConfigSection.climateWindDirectionVariance,
            ClimateConfigSection.climateWindSpeedBase,
            ClimateConfigSection.climateWindSpeedVariance
        );

        SeasonSettings seasonSettings = new SeasonSettings(
            ClimateConfigSection.climateSeasonEnabled,
            Math.max(1.0d, ClimateConfigSection.climateSeasonLengthDays * TICKS_PER_DAY),
            normalizePhase(ClimateConfigSection.climateSeasonPhaseOffset),
            ClimateConfigSection.climateSeasonTemperatureAmplitude,
            ClimateConfigSection.climateSeasonHumidityAmplitude,
            ClimateConfigSection.climateSeasonRainfallAmplitude
        );

        return new ClimateProviderSettings(
            temperatureChannel,
            humidityChannel,
            rainfallChannel,
            windSettings,
            seasonSettings,
            DEFAULT_LACUNARITY,
            DEFAULT_PERSISTENCE
        );
    }

    private static double normalizePhase(double value) {
        double phase = value % 1.0d;
        return phase < 0.0d ? phase + 1.0d : phase;
    }
}
