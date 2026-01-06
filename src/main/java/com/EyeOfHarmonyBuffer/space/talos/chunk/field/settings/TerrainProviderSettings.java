package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.github.bsideup.jabel.Desugar;

@Desugar
public record TerrainProviderSettings(
    double baseHeight,
    double verticalScale,
    double primaryFrequency,
    double lacunarity,
    double persistence,
    int octaves,
    double slopeSampleOffsetBlocks
) {
    public static TerrainProviderSettings defaults() {
        return new TerrainProviderSettings(
            64.0,
            48.0,
            1.0 / 256.0,
            2.0,
            0.5,
            5,
            2.0
        );
    }
}
