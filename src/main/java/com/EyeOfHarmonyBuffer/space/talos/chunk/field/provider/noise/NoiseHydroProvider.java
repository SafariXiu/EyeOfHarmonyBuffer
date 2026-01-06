package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.HydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.HydroProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;

public final class NoiseHydroProvider implements HydroProvider {

    private final HydroProviderSettings settings;
    private final FractalNoise2D saturationNoise;
    private final FractalNoise2D flowNoise;

    public NoiseHydroProvider(long seed, HydroProviderSettings settings) {
        this.settings = settings;
        this.saturationNoise = new FractalNoise2D(
            seed ^ 0xD1B54A32D192ED03L,
            settings.saturationFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
        this.flowNoise = new FractalNoise2D(
            seed ^ 0x94D049BB133111EBL,
            settings.flowFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
    }

    @Override
    public HydroSample sample(int blockX, int blockZ) {
        double saturationNorm = (saturationNoise.sample(blockX, blockZ) * 0.5) + 0.5;
        double saturation = clamp01(
            settings.waterTableLevel()
                + settings.saturationVariance() * (saturationNorm - 0.5)
        );

        double flowNorm = (flowNoise.sample(blockX, blockZ) * 0.5) + 0.5;
        double flowRate = clamp01(flowNorm) * settings.maxFlowRate();

        double aquifer = settings.waterTableLevel()
            + (saturationNorm - 0.5) * settings.aquiferVariance();

        return new HydroSample(saturation, flowRate, aquifer);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
