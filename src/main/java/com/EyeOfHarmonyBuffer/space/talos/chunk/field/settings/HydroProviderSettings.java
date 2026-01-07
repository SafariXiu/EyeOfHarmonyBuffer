package com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings;

import com.EyeOfHarmonyBuffer.Config.FieldManagerConfigSpec;
import com.github.bsideup.jabel.Desugar;

@Desugar
public record HydroProviderSettings(
    double seaLevelBlocks,
    GroundwaterSettings groundwater,
    RiverSettings river,
    LakeSettings lake,
    CoastSettings coast,
    DiagnosticsSettings diagnostics
) {
    private static final double DEFAULT_LACUNARITY = 2.0d;
    private static final double DEFAULT_PERSISTENCE = 0.5d;
    private static final int DEFAULT_OCTAVES = 4;
    private static final double WORLD_HEIGHT = 256.0d;

    @Desugar
    public record NoiseSettings(
        double frequency,
        double lacunarity,
        double persistence,
        int octaves
    ) {}

    @Desugar
    public record GroundwaterSettings(
        double waterTableLevel,
        double saturationVariance,
        double aquiferVariance,
        double maxFlowRate,
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
            0.4d,
            0.4d,
            0.3d,
            0.8d,
            new NoiseSettings(1.0d / 256.0d, DEFAULT_LACUNARITY, DEFAULT_PERSISTENCE, DEFAULT_OCTAVES),
            new NoiseSettings(1.0d / 192.0d, DEFAULT_LACUNARITY, DEFAULT_PERSISTENCE, DEFAULT_OCTAVES)
        );

        return new HydroProviderSettings(
            63.0d,
            groundwater,
            new RiverSettings(0.0015d, 0.006d, 1.0d, 0.45d, 51, 2),
            new LakeSettings(false, 0.7d, 73),
            new CoastSettings(true, 16.0d),
            new DiagnosticsSettings(false, 500)
        );
    }

    public static HydroProviderSettings fromConfig() {
        double waterTableLevel = clamp01(FieldManagerConfigSpec.hydroSeaLevel / WORLD_HEIGHT);

        GroundwaterSettings groundwater = new GroundwaterSettings(
            waterTableLevel,
            FieldManagerConfigSpec.hydroRiverStrength,
            clamp01(FieldManagerConfigSpec.hydroRiverThreshold),
            Math.max(0.0d, FieldManagerConfigSpec.hydroRiverStrength),
            new NoiseSettings(
                FieldManagerConfigSpec.hydroRiverFrequency,
                DEFAULT_LACUNARITY,
                DEFAULT_PERSISTENCE,
                DEFAULT_OCTAVES
            ),
            new NoiseSettings(
                FieldManagerConfigSpec.hydroRiverDetailFrequency,
                DEFAULT_LACUNARITY,
                DEFAULT_PERSISTENCE,
                DEFAULT_OCTAVES
            )
        );

        return new HydroProviderSettings(
            FieldManagerConfigSpec.hydroSeaLevel,
            groundwater,
            new RiverSettings(
                FieldManagerConfigSpec.hydroRiverFrequency,
                FieldManagerConfigSpec.hydroRiverDetailFrequency,
                FieldManagerConfigSpec.hydroRiverStrength,
                FieldManagerConfigSpec.hydroRiverThreshold,
                FieldManagerConfigSpec.hydroRiverSeedOffset,
                FieldManagerConfigSpec.hydroRiverSmoothRadius
            ),
            new LakeSettings(
                FieldManagerConfigSpec.hydroLakeEnabled,
                FieldManagerConfigSpec.hydroLakeThreshold,
                FieldManagerConfigSpec.hydroLakeSeedOffset
            ),
            new CoastSettings(
                FieldManagerConfigSpec.hydroCoastSyncWithMacro,
                FieldManagerConfigSpec.hydroCoastFalloff
            ),
            new DiagnosticsSettings(
                FieldManagerConfigSpec.hydroDiagLogSamples,
                FieldManagerConfigSpec.hydroDiagProbeInterval
            )
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }
}
