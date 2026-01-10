package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.Config.FieldManagerConfigSpec;
import com.github.bsideup.jabel.Desugar;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Desugar
public record MacroSelectorConfig(
    long baseSalt,
    NoiseSettings patchNoise,
    RareSettings rareSettings,
    ContinentalSettings continentalSettings,
    double continentalLandThreshold,
    double coastSoftBandWidth,
    OverrideSettings overrideSettings,
    LatitudeSettings latitudeSettings,
    boolean debugLogging,
    int macroGridSize,
    double macroSiteSpacing,
    long macroSiteSalt,
    int macroNeighborRadius,
    int macroCacheMaxEntries,
    double macroBlendWidth,
    double edgeNoiseFrequency,
    double edgeNoiseAmplitude,
    long edgeNoiseSalt,
    int microGridSize,
    double microSiteSpacing,
    long microSiteSalt,
    HeightSettings heightSettings,
    TransitionSettings transitionSettings,
    HeightProfile heightProfile
) {

    public MacroSelectorConfig {
        Objects.requireNonNull(patchNoise, "patchNoise");
        Objects.requireNonNull(rareSettings, "rareSettings");
        Objects.requireNonNull(continentalSettings, "continentalSettings");
        Objects.requireNonNull(heightSettings, "heightSettings");
        Objects.requireNonNull(heightProfile, "heightProfile");
        Objects.requireNonNull(latitudeSettings, "latitudeSettings");
        Objects.requireNonNull(overrideSettings, "overrideSettings");

        if (macroGridSize < 512) {
            throw new IllegalArgumentException("macroGridSize must be >= 512");
        }
        if (macroSiteSpacing <= 0.0d) {
            throw new IllegalArgumentException("macroSiteSpacing must be > 0");
        }
        if (macroNeighborRadius < 1) {
            throw new IllegalArgumentException("macroNeighborRadius must be >= 1");
        }
        if (macroCacheMaxEntries < 64) {
            throw new IllegalArgumentException("macroCacheMaxEntries must be >= 64");
        }
        if (macroBlendWidth <= 0.0d) {
            throw new IllegalArgumentException("macroBlendWidth must be > 0");
        }
        if (edgeNoiseFrequency <= 0.0d) {
            throw new IllegalArgumentException("edgeNoiseFrequency must be > 0");
        }
        if (edgeNoiseAmplitude < 0.0d) {
            throw new IllegalArgumentException("edgeNoiseAmplitude must be >= 0");
        }
        if (microGridSize < 256) {
            throw new IllegalArgumentException("microGridSize must be >= 256");
        }
        if (microSiteSpacing <= 0.0d) {
            throw new IllegalArgumentException("microSiteSpacing must be > 0");
        }
        if (continentalLandThreshold <= 0.0d || continentalLandThreshold >= 1.0d) {
            throw new IllegalArgumentException("continentalLandThreshold must be within (0,1)");
        }
        if (coastSoftBandWidth < 0.0d || coastSoftBandWidth > 0.25d) {
            throw new IllegalArgumentException("coastSoftBandWidth must be within [0,0.25]");
        }
    }

    public static MacroSelectorConfig fromSpec() {
        NoiseSettings heightNoise = null;
        if (FieldManagerConfigSpec.selectorHeightNoiseEnabled) {
            heightNoise = new NoiseSettings(
                FieldManagerConfigSpec.selectorHeightNoiseSalt,
                FieldManagerConfigSpec.selectorHeightNoiseFrequency,
                FieldManagerConfigSpec.selectorHeightNoiseOctaves,
                FieldManagerConfigSpec.selectorHeightNoiseLacunarity,
                FieldManagerConfigSpec.selectorHeightNoiseGain,
                1.0d
            );
        }

        HeightSettings heightSettings = new HeightSettings(
            heightNoise,
            FieldManagerConfigSpec.selectorMacroHeightNoiseStrength,
            FieldManagerConfigSpec.selectorMicroHeightNoiseStrength
        );

        TransitionSettings transitionSettings = new TransitionSettings(
            FieldManagerConfigSpec.selectorTransitionEnabled,
            FieldManagerConfigSpec.selectorTransitionDefaultCoastWidth,
            Arrays.asList(FieldManagerConfigSpec.selectorTransitionRules)
        );

        HeightProfile heightProfile = new HeightProfile(
            FieldManagerConfigSpec.terrainWorldFloor,
            FieldManagerConfigSpec.terrainWorldCeiling,
            FieldManagerConfigSpec.terrainFloorY,
            FieldManagerConfigSpec.terrainCeilingY,
            FieldManagerConfigSpec.terrainSeaLevel
        );

        return new MacroSelectorConfig(
            FieldManagerConfigSpec.selectorSeedSalt,
            new NoiseSettings(
                FieldManagerConfigSpec.selectorPatchSalt,
                FieldManagerConfigSpec.selectorPatchFrequency,
                FieldManagerConfigSpec.selectorPatchOctaves,
                FieldManagerConfigSpec.selectorPatchLacunarity,
                FieldManagerConfigSpec.selectorPatchGain,
                FieldManagerConfigSpec.selectorPatchScale
            ),
            new RareSettings(
                FieldManagerConfigSpec.selectorRareEnabled,
                FieldManagerConfigSpec.selectorRareSalt,
                FieldManagerConfigSpec.selectorRareFrequency,
                FieldManagerConfigSpec.selectorRareThreshold
            ),
            new ContinentalSettings(
                FieldManagerConfigSpec.selectorElevationMin,
                FieldManagerConfigSpec.selectorElevationMax,
                FieldManagerConfigSpec.selectorElevationWeight,
                FieldManagerConfigSpec.selectorCoastScale,
                FieldManagerConfigSpec.selectorCoastWeight,
                FieldManagerConfigSpec.selectorHydroWeight,
                FieldManagerConfigSpec.selectorContinentalPivot,
                FieldManagerConfigSpec.selectorContinentalScale,
                FieldManagerConfigSpec.selectorCoastBeachWidth,
                FieldManagerConfigSpec.selectorCoastShelfWidth
            ),
            FieldManagerConfigSpec.selectorContinentalLandThreshold,
            FieldManagerConfigSpec.selectorCoastSoftBandWidth,
            new OverrideSettings(
                FieldManagerConfigSpec.selectorOverrideLandScoreThreshold,
                FieldManagerConfigSpec.selectorOverrideMinShelfWidth
            ),
            new LatitudeSettings(
                FieldManagerConfigSpec.selectorLatitudePeriod,
                FieldManagerConfigSpec.selectorLatitudeBlendWidth,
                FieldManagerConfigSpec.selectorLatitudeBaseBias,
                FieldManagerConfigSpec.selectorLatitudeMixWeight,
                FieldManagerConfigSpec.selectorLatitudeWarpScale,
                FieldManagerConfigSpec.selectorLatitudeWarpAmplitude,
                FieldManagerConfigSpec.selectorLatitudeWarpSalt
            ),
            FieldManagerConfigSpec.selectorDebugLogging,
            FieldManagerConfigSpec.selectorMacroGridSize,
            FieldManagerConfigSpec.selectorMacroSiteSpacing,
            FieldManagerConfigSpec.selectorMacroSiteSalt,
            2,
            FieldManagerConfigSpec.macroCacheMaxEntries,
            FieldManagerConfigSpec.selectorMacroBlendWidth,
            FieldManagerConfigSpec.selectorEdgeNoiseFrequency,
            FieldManagerConfigSpec.selectorEdgeNoiseAmplitude,
            FieldManagerConfigSpec.selectorEdgeNoiseSalt,
            FieldManagerConfigSpec.selectorMicroGridSize,
            FieldManagerConfigSpec.selectorMicroSiteSpacing,
            FieldManagerConfigSpec.selectorMicroSiteSalt,
            heightSettings,
            transitionSettings,
            heightProfile
        );
    }

    @Desugar
    public record NoiseSettings(
        long salt,
        double frequency,
        int octaves,
        double lacunarity,
        double gain,
        double scale
    ) {
        public boolean enabled() {
            return frequency > 0.0d && octaves > 0 && scale > 0.0d;
        }
    }

    @Desugar
    public record RareSettings(
        boolean enabled,
        long salt,
        double frequency,
        double threshold
    ) {
        public double clampThreshold() {
            return Math.max(0.0d, Math.min(1.0d, threshold));
        }
    }

    @Desugar
    public record HeightSettings(
        @Nullable NoiseSettings noise,
        double macroNoiseStrength,
        double microNoiseStrength
    ) {
        public boolean enabled() {
            return noise != null && macroNoiseStrength != 0.0d;
        }
    }

    @Desugar
    public record TransitionSettings(
        boolean enabled,
        double defaultCoastWidth,
        List<String> ruleStrings
    ) {
        public List<String> ruleStrings() {
            return ruleStrings == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(ruleStrings);
        }
    }

    @Desugar
    public record ContinentalSettings(
        double elevationMin,
        double elevationMax,
        double elevationWeight,
        double coastScale,
        double coastWeight,
        double hydroWeight,
        double pivot,
        double scale,
        double beachWidth,
        double shelfWidth
    ) {
        private static final double EPS = 1.0e-6;

        public double normalizeElevation(double elevation) {
            double range = Math.max(EPS, elevationMax - elevationMin);
            double normalized = (elevation - elevationMin) / range;
            return clamp01(normalized);
        }

        public double normalizeCoast(double coastDistance) {
            double normalized = coastDistance / Math.max(EPS, coastScale);
            return clamp01(normalized);
        }

        public double normalizeHydro(double saturation) {
            return clamp01(1.0d - saturation);
        }

        public double combine(double elevation, double coast, double hydro) {
            double total = Math.max(EPS, elevationWeight + coastWeight + hydroWeight);
            double weighted = elevation * elevationWeight
                + coast * coastWeight
                + hydro * hydroWeight;
            return clamp01(weighted / total);
        }

        public double remapToMacroRange(double combined) {
            double centered = (combined - pivot) * scale;
            if (centered < -1.0d) {
                return -1.0d;
            }
            if (centered > 1.0d) {
                return 1.0d;
            }
            return centered;
        }

        public double compose(double elevation, double coastDistance, double saturation) {
            double nElevation = normalizeElevation(elevation);
            double nCoast = normalizeCoast(coastDistance);
            double nHydro = normalizeHydro(saturation);
            double combined = combine(nElevation, nCoast, nHydro);
            return remapToMacroRange(combined);
        }

        private static double clamp01(double value) {
            if (value < 0.0d) {
                return 0.0d;
            }
            if (value > 1.0d) {
                return 1.0d;
            }
            return value;
        }

        public double beachWidthBlocks() {
            return beachWidth;
        }

        public double shelfWidthBlocks() {
            return shelfWidth;
        }
    }

    @Desugar
    public record HeightProfile(
        int worldFloorY,
        int worldCeilingY,
        double terrainFloorY,
        double terrainCeilingY,
        double seaLevelY
    ) {
        public double terrainRange() {
            return Math.max(1.0d, terrainCeilingY - terrainFloorY);
        }
    }

    @Desugar
    public record LatitudeSettings(
        double periodBlocks,
        double blendWidth,
        double baseBias,
        double mixWeight,
        double warpScale,
        double warpAmplitude,
        long warpSalt
    ) {
        public LatitudeSettings {
            if (periodBlocks <= 0.0d) {
                throw new IllegalArgumentException("Latitude period must be > 0");
            }
            if (blendWidth < 0.0d) {
                throw new IllegalArgumentException("Latitude blendWidth must be >= 0");
            }
            if (warpScale < 0.0d) {
                throw new IllegalArgumentException("Latitude warpScale must be >= 0");
            }
            if (warpAmplitude < 0.0d) {
                throw new IllegalArgumentException("Latitude warpAmplitude must be >= 0");
            }
        }

        public double normalizedBlendWidth() {
            return Math.max(0.0d, Math.min(0.5d, blendWidth));
        }
    }

    @Desugar
    public record OverrideSettings(
        double landScoreThreshold,
        double minShelfWidthBlocks
    ) {
        public OverrideSettings {
            if (landScoreThreshold <= 0.0d || landScoreThreshold >= 1.0d) {
                throw new IllegalArgumentException("landScoreThreshold must be within (0,1)");
            }
            if (minShelfWidthBlocks < 0.0d) {
                throw new IllegalArgumentException("minShelfWidthBlocks must be >= 0");
            }
        }
    }
}
