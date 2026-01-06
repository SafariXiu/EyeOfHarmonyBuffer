package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.ClimateProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;

public final class NoiseClimateProvider implements ClimateProvider {

    private final ClimateProviderSettings settings;
    private final FractalNoise2D temperatureNoise;
    private final FractalNoise2D humidityNoise;
    private final FractalNoise2D rainfallNoise;

    public NoiseClimateProvider(long seed, ClimateProviderSettings settings) {
        this.settings = settings;
        this.temperatureNoise = new FractalNoise2D(
            seed ^ 0x9E3779B97F4A7C15L,
            settings.temperatureFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
        this.humidityNoise = new FractalNoise2D(
            seed ^ 0xB5297A4DL,
            settings.humidityFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
        this.rainfallNoise = new FractalNoise2D(
            seed ^ 0x1F123BB5L,
            settings.rainfallFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
    }

    @Override
    public ClimateSample sample(int blockX, int blockZ) {
        double temperature = clamp01(settings.temperatureBase()
            + settings.temperatureVariance() * temperatureNoise.sample(blockX, blockZ));
        double humidity = clamp01(settings.humidityBase()
            + settings.humidityVariance() * humidityNoise.sample(blockX, blockZ));
        double rainfall = clamp01(settings.rainfallBase()
            + settings.rainfallVariance() * rainfallNoise.sample(blockX, blockZ));

        return new ClimateSample(temperature, humidity, rainfall);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
