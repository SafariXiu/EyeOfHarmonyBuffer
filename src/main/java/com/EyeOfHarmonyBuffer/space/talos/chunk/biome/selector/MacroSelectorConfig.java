package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.*;
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
    HeightContinuitySettings heightContinuity,
    TransitionSettings transitionSettings,
    HeightProfile heightProfile,
    boolean continuousHeightField,
    int heightControlResolution
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
        if (heightControlResolution < 1) {
            throw new IllegalArgumentException("heightControlResolution must be >= 1");
        }
    }

    public static MacroSelectorConfig fromSpec() {
        NoiseSettings heightNoise = null;
        if (MacroSelectorHeightConfigSection.selectorHeightNoiseEnabled) {
            heightNoise = new NoiseSettings(
                MacroSelectorHeightConfigSection.selectorHeightNoiseSalt,
                MacroSelectorHeightConfigSection.selectorHeightNoiseFrequency,
                MacroSelectorHeightConfigSection.selectorHeightNoiseOctaves,
                MacroSelectorHeightConfigSection.selectorHeightNoiseLacunarity,
                MacroSelectorHeightConfigSection.selectorHeightNoiseGain,
                1.0d
            );
        }

        HeightSettings heightSettings = new HeightSettings(
            heightNoise,
            MacroSelectorHeightConfigSection.selectorMacroHeightNoiseStrength,
            MacroSelectorHeightConfigSection.selectorMicroHeightNoiseStrength
        );

        HeightContinuitySettings continuitySettings = new HeightContinuitySettings(
            MacroSelectorContinuityConfigSection.continuityEnabled,
            MacroSelectorContinuityConfigSection.globalFieldWeight,
            MacroSelectorContinuityConfigSection.maxNeighborDelta,
            MacroSelectorContinuityConfigSection.smoothingRadius,
            MacroSelectorContinuityConfigSection.relaxIterations,
            MacroSelectorContinuityConfigSection.maxEdgeDelta,
            MacroSelectorContinuityConfigSection.varianceFalloff,
            MacroSelectorContinuityConfigSection.gridBlurStrength,
            MacroSelectorContinuityConfigSection.finalPassEnabled,
            MacroSelectorContinuityConfigSection.finalBlendStrength,
            MacroSelectorContinuityConfigSection.finalPointSampleRadius,
            MacroSelectorContinuityConfigSection.finalMaxDelta
        );

        TransitionSettings transitionSettings = new TransitionSettings(
            MacroSelectorTransitionConfigSection.selectorTransitionEnabled,
            MacroSelectorTransitionConfigSection.selectorTransitionDefaultCoastWidth,
            Arrays.asList(MacroSelectorTransitionConfigSection.selectorTransitionRules)
        );

        HeightProfile heightProfile = new HeightProfile(
            TerrainConfigSection.terrainWorldFloor,
            TerrainConfigSection.terrainWorldCeiling,
            TerrainConfigSection.terrainFloorY,
            TerrainConfigSection.terrainCeilingY,
            TerrainConfigSection.terrainSeaLevel
        );

        return new MacroSelectorConfig(
            MacroSelectorConfigSection.selectorSeedSalt,
            new NoiseSettings(
                MacroSelectorConfigSection.selectorPatchSalt,
                MacroSelectorConfigSection.selectorPatchFrequency,
                MacroSelectorConfigSection.selectorPatchOctaves,
                MacroSelectorConfigSection.selectorPatchLacunarity,
                MacroSelectorConfigSection.selectorPatchGain,
                MacroSelectorConfigSection.selectorPatchScale
            ),
            new RareSettings(
                MacroSelectorConfigSection.selectorRareEnabled,
                MacroSelectorConfigSection.selectorRareSalt,
                MacroSelectorConfigSection.selectorRareFrequency,
                MacroSelectorConfigSection.selectorRareThreshold
            ),
            new ContinentalSettings(
                MacroSelectorConfigSection.selectorElevationMin,
                MacroSelectorConfigSection.selectorElevationMax,
                MacroSelectorConfigSection.selectorElevationWeight,
                MacroSelectorConfigSection.selectorCoastScale,
                MacroSelectorConfigSection.selectorCoastWeight,
                MacroSelectorConfigSection.selectorHydroWeight,
                MacroSelectorConfigSection.selectorContinentalPivot,
                MacroSelectorConfigSection.selectorContinentalScale,
                MacroSelectorConfigSection.selectorCoastBeachWidth,
                MacroSelectorConfigSection.selectorCoastShelfWidth
            ),
            MacroSelectorConfigSection.selectorContinentalLandThreshold,
            MacroSelectorConfigSection.selectorCoastSoftBandWidth,
            new OverrideSettings(
                MacroSelectorConfigSection.selectorOverrideLandScoreThreshold,
                MacroSelectorConfigSection.selectorOverrideMinShelfWidth
            ),
            new LatitudeSettings(
                MacroSelectorConfigSection.selectorLatitudePeriod,
                MacroSelectorConfigSection.selectorLatitudeBlendWidth,
                MacroSelectorConfigSection.selectorLatitudeBaseBias,
                MacroSelectorConfigSection.selectorLatitudeMixWeight,
                MacroSelectorConfigSection.selectorLatitudeWarpScale,
                MacroSelectorConfigSection.selectorLatitudeWarpAmplitude,
                MacroSelectorConfigSection.selectorLatitudeWarpSalt
            ),
            MacroSelectorConfigSection.selectorDebugLogging,
            MacroSelectorConfigSection.selectorMacroGridSize,
            MacroSelectorConfigSection.selectorMacroSiteSpacing,
            MacroSelectorConfigSection.selectorMacroSiteSalt,
            2,
            MacroCacheConfigSection.macroCacheMaxEntries,
            MacroSelectorConfigSection.selectorMacroBlendWidth,
            MacroSelectorConfigSection.selectorEdgeNoiseFrequency,
            MacroSelectorConfigSection.selectorEdgeNoiseAmplitude,
            MacroSelectorConfigSection.selectorEdgeNoiseSalt,
            MacroSelectorConfigSection.selectorMicroGridSize,
            MacroSelectorConfigSection.selectorMicroSiteSpacing,
            MacroSelectorConfigSection.selectorMicroSiteSalt,
            heightSettings,
            continuitySettings,
            transitionSettings,
            heightProfile,
            MacroSelectorHeightConfigSection.selectorContinuousHeightField,
            MacroSelectorHeightConfigSection.selectorHeightControlResolution
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

    @Desugar
    public record HeightContinuitySettings(
        boolean enabled,
        double globalFieldWeight,
        double maxNeighborDelta,
        int smoothingRadius,
        int relaxIterations,
        double maxEdgeDelta,
        double varianceFalloff,
        double gridBlurStrength,
        boolean finalPassEnabled,
        double finalBlendStrength,
        int finalPointSampleRadius,
        double finalMaxDelta
    ) {
        public HeightContinuitySettings {
            if (globalFieldWeight < 0.0d || globalFieldWeight > 1.0d) {
                throw new IllegalArgumentException("globalFieldWeight must be within [0,1]");
            }
            if (maxNeighborDelta < 0.0d || maxNeighborDelta > 1.0d) {
                throw new IllegalArgumentException("maxNeighborDelta must be within [0,1]");
            }
            if (smoothingRadius < 0) {
                throw new IllegalArgumentException("smoothingRadius must be >= 0");
            }
            if (relaxIterations < 0) {
                throw new IllegalArgumentException("relaxIterations must be >= 0");
            }
            if (maxEdgeDelta < 0.0d || maxEdgeDelta > 1.0d) {
                throw new IllegalArgumentException("maxEdgeDelta must be within [0,1]");
            }
            if (varianceFalloff < 0.5d) {
                throw new IllegalArgumentException("varianceFalloff must be >= 0.5");
            }
            if (gridBlurStrength < 0.0d || gridBlurStrength > 1.0d) {
                throw new IllegalArgumentException("gridBlurStrength must be within [0,1]");
            }
            if (finalBlendStrength < 0.0d || finalBlendStrength > 1.0d) {
                throw new IllegalArgumentException("finalBlendStrength must be within [0,1]");
            }
            if (finalPointSampleRadius < 1) {
                throw new IllegalArgumentException("finalPointSampleRadius must be >= 1");
            }
            if (finalMaxDelta < 0.0d || finalMaxDelta > 1.0d) {
                throw new IllegalArgumentException("finalMaxDelta must be within [0,1]");
            }
        }

        public boolean disabled() {
            return !enabled;
        }
    }
}
