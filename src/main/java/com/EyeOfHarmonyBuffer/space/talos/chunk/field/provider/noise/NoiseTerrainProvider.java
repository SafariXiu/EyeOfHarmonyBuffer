package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.TerrainProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;

public final class NoiseTerrainProvider implements TerrainProvider {

    private final TerrainProviderSettings settings;
    private final FractalNoise2D elevationNoise;
    private final FractalNoise2D roughnessNoise;

    public NoiseTerrainProvider(long seed, TerrainProviderSettings settings) {
        this.settings = settings;
        this.elevationNoise = new FractalNoise2D(
            seed,
            settings.primaryFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
        this.roughnessNoise = new FractalNoise2D(
            seed ^ 0x5DEECE66DL,
            settings.primaryFrequency() * 2.0,
            settings.lacunarity(),
            settings.persistence(),
            Math.max(1, settings.octaves() - 1)
        );
    }

    @Override
    public TerrainSample sample(int blockX, int blockZ) {
        double elevationNorm = elevationNoise.sample(blockX, blockZ);
        double elevation = settings.baseHeight() + elevationNorm * settings.verticalScale();

        double offset = settings.slopeSampleOffsetBlocks();
        double east = elevationNoise.sample(blockX + offset, blockZ);
        double north = elevationNoise.sample(blockX, blockZ + offset);

        double slope = (Math.abs(east - elevationNorm) + Math.abs(north - elevationNorm)) * 0.5;
        double roughness = (roughnessNoise.sample(blockX, blockZ) * 0.5) + 0.5;

        return new TerrainSample(elevation, slope, clamp01(roughness));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
