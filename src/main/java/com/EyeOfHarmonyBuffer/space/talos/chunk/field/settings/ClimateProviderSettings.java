package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record ClimateProviderSettings(
    double temperatureBase,
    double temperatureVariance,
    double temperatureFrequency,
    double humidityBase,
    double humidityVariance,
    double humidityFrequency,
    double rainfallBase,
    double rainfallVariance,
    double rainfallFrequency,
    double lacunarity,
    double persistence,
    int octaves
) {
    public static ClimateProviderSettings defaults() {
        return new ClimateProviderSettings(
            0.65,
            0.35,
            1.0 / 512.0,
            0.55,
            0.35,
            1.0 / 384.0,
            0.45,
            0.45,
            1.0 / 768.0,
            2.0,
            0.55,
            4
        );
    }
}
