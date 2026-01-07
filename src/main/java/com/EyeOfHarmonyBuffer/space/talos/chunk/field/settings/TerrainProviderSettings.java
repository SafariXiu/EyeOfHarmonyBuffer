package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.EyeOfHarmonyBuffer.Config.FieldManagerConfigSpec;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record TerrainProviderSettings(
    double baseHeight,
    double verticalScale,
    double primaryFrequency,
    double lacunarity,
    double persistence,
    int octaves,
    double slopeSampleOffsetBlocks,
    int noiseSeedOffset,
    boolean cacheEnabled,
    int cacheSize
) {

    public static TerrainProviderSettings defaults() {
        return new TerrainProviderSettings(
            64.0d,
            32.0d,
            0.003d,
            2.0d,
            0.5d,
            4,
            4.0d,
            0,
            false,
            256
        );
    }

    public static TerrainProviderSettings fromConfig() {
        return new TerrainProviderSettings(
            FieldManagerConfigSpec.terrainBaseHeight,
            FieldManagerConfigSpec.terrainAmplitude,
            FieldManagerConfigSpec.terrainFrequency,
            FieldManagerConfigSpec.terrainLacunarity,
            FieldManagerConfigSpec.terrainPersistence,
            FieldManagerConfigSpec.terrainOctaves,
            Math.max(1.0d, FieldManagerConfigSpec.terrainSlopeSampleStep),
            FieldManagerConfigSpec.terrainNoiseSeedOffset,
            FieldManagerConfigSpec.terrainCacheEnabled,
            FieldManagerConfigSpec.terrainCacheSize
        );
    }
}
