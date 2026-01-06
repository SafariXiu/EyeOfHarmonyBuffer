package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record HydroProviderSettings(
    double waterTableLevel,
    double saturationVariance,
    double aquiferVariance,
    double maxFlowRate,
    double saturationFrequency,
    double flowFrequency,
    double lacunarity,
    double persistence,
    int octaves
) {
    public static HydroProviderSettings defaults() {
        return new HydroProviderSettings(
            0.4,
            0.4,
            0.3,
            0.8,
            1.0 / 256.0,
            1.0 / 192.0,
            2.0,
            0.5,
            4
        );
    }
}
