package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.Config.FieldManagerConfigSpec;
import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

@Desugar
public record MacroSelectorConfig(
    long baseSalt,
    NoiseSettings patchNoise,
    RareSettings rareSettings,
    ContinentalSettings continentalSettings,
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
    long microSiteSalt
) {

    public MacroSelectorConfig {
        Objects.requireNonNull(patchNoise, "patchNoise");
        Objects.requireNonNull(rareSettings, "rareSettings");
        Objects.requireNonNull(continentalSettings, "continentalSettings");

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
    }

    public static MacroSelectorConfig fromSpec() {
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
            FieldManagerConfigSpec.selectorDebugLogging,
            FieldManagerConfigSpec.selectorMacroGridSize,
            FieldManagerConfigSpec.selectorMacroSiteSpacing,
            FieldManagerConfigSpec.selectorMacroSiteSalt,
            /* neighbor radius → 可暂写死 2，如需配置可在 spec 中新增 */
            2,
            FieldManagerConfigSpec.macroCacheMaxEntries,
            FieldManagerConfigSpec.selectorMacroBlendWidth,
            FieldManagerConfigSpec.selectorEdgeNoiseFrequency,
            FieldManagerConfigSpec.selectorEdgeNoiseAmplitude,
            FieldManagerConfigSpec.selectorEdgeNoiseSalt,
            FieldManagerConfigSpec.selectorMicroGridSize,
            FieldManagerConfigSpec.selectorMicroSiteSpacing,
            FieldManagerConfigSpec.selectorMicroSiteSalt
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
}
