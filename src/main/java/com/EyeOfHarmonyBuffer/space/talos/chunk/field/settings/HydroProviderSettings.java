package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.HydroConfigSection;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record HydroProviderSettings(
    double seaLevelBlocks,
    GroundwaterSettings groundwater,
    RiverSettings river,
    LakeSettings lake,
    CoastSettings coast,
    DiagnosticsSettings diagnostics,
    double waterTableBufferBlocks
) {
    private static final double DEFAULT_LACUNARITY = 2.0d;
    private static final double DEFAULT_PERSISTENCE = 0.5d;
    private static final int DEFAULT_OCTAVES = 4;
    private static final double DEFAULT_WATER_TABLE_BUFFER = 6.0d;

    @Desugar
    public record NoiseSettings(
        double frequency,
        double lacunarity,
        double persistence,
        int octaves
    ) {}

    @Desugar
    public record GroundwaterSettings(
        double baseSaturation,
        double saturationVariance,
        double baseAquifer,
        double aquiferVariance,
        double maxFlowRate,
        double heightFalloff,
        double heightWeight,
        NoiseSettings saturationNoise,
        NoiseSettings flowNoise
    ) {}

    @Desugar
    public record RiverSettings(
        double frequency,
        double detailFrequency,
        double strength,
        double threshold,
        int seedOffset,
        int smoothRadius
    ) {}

    @Desugar
    public record LakeSettings(
        boolean enabled,
        double threshold,
        int seedOffset
    ) {}

    @Desugar
    public record CoastSettings(
        boolean syncWithMacro,
        double falloff
    ) {}

    @Desugar
    public record DiagnosticsSettings(
        boolean logSamples,
        int probeInterval
    ) {}

    public static HydroProviderSettings defaults() {
        GroundwaterSettings groundwater = new GroundwaterSettings(
            0.25d,
            0.35d,
            0.45d,
            0.25d,
            0.8d,
            64.0d,
            0.5d,
            new NoiseSettings(1.0d / 256.0d, DEFAULT_LACUNARITY, DEFAULT_PERSISTENCE, DEFAULT_OCTAVES),
            new NoiseSettings(1.0d / 192.0d, DEFAULT_LACUNARITY, DEFAULT_PERSISTENCE, DEFAULT_OCTAVES)
        );

        return new HydroProviderSettings(
            63.0d,
            groundwater,
            new RiverSettings(0.0015d, 0.006d, 1.0d, 0.45d, 51, 2),
            new LakeSettings(false, 0.7d, 73),
            new CoastSettings(true, 16.0d),
            new DiagnosticsSettings(false, 500),
            DEFAULT_WATER_TABLE_BUFFER
        );
    }

    public static HydroProviderSettings fromConfig() {
        double seaLevelBlocks = HydroConfigSection.hydroSeaLevel;
        double normalizedAquiferBase = clamp01(HydroConfigSection.hydroBaseAquiferNormalized);
        double waterTableBuffer = Math.max(0.0d, HydroConfigSection.hydroWaterTableBufferBlocks);

        GroundwaterSettings groundwater = new GroundwaterSettings(
            clamp01(HydroConfigSection.hydroBaseSaturation),
            clamp01(HydroConfigSection.hydroSaturationVariance),
            normalizedAquiferBase,
            clamp01(HydroConfigSection.hydroAquiferVariance),
            clamp01(HydroConfigSection.hydroMaxFlowRate),
            Math.max(1.0d, HydroConfigSection.hydroHeightFalloffBlocks),
            clamp01(HydroConfigSection.hydroHeightWeight),
            new NoiseSettings(
                HydroConfigSection.hydroSaturationNoiseFrequency,
                HydroConfigSection.hydroSaturationNoiseLacunarity,
                HydroConfigSection.hydroSaturationNoisePersistence,
                HydroConfigSection.hydroSaturationNoiseOctaves
            ),
            new NoiseSettings(
                HydroConfigSection.hydroFlowNoiseFrequency,
                HydroConfigSection.hydroFlowNoiseLacunarity,
                HydroConfigSection.hydroFlowNoisePersistence,
                HydroConfigSection.hydroFlowNoiseOctaves
            )
        );

        return new HydroProviderSettings(
            seaLevelBlocks,
            groundwater,
            new RiverSettings(
                HydroConfigSection.hydroRiverFrequency,
                HydroConfigSection.hydroRiverDetailFrequency,
                HydroConfigSection.hydroRiverStrength,
                HydroConfigSection.hydroRiverThreshold,
                HydroConfigSection.hydroRiverSeedOffset,
                HydroConfigSection.hydroRiverSmoothRadius
            ),
            new LakeSettings(
                HydroConfigSection.hydroLakeEnabled,
                HydroConfigSection.hydroLakeThreshold,
                HydroConfigSection.hydroLakeSeedOffset
            ),
            new CoastSettings(
                HydroConfigSection.hydroCoastSyncWithMacro,
                HydroConfigSection.hydroCoastFalloff
            ),
            new DiagnosticsSettings(
                HydroConfigSection.hydroDiagLogSamples,
                HydroConfigSection.hydroDiagProbeInterval
            ),
            waterTableBuffer
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
