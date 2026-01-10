package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.MacroSite;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;

import java.util.Objects;

public final class MacroSelectionResult {

    private final long macroSiteId;
    private final MacroSite primarySite;
    private final MacroSite secondarySite;
    private final double primaryDistance;
    private final double secondaryDistance;
    private final double edgeMetric;
    private final double edgeFactor;
    private final boolean edgeFlipped;
    private final MacroTag macroTag;
    private final MacroBiome macroBiome;
    private final boolean rare;
    private final double coastDistance;
    private final double coastWidth;
    private final double shelfWidth;
    private final int patchId;
    private final double continentalScore;
    private final double humidity;
    private final double temperature;
    private final double normalizedCoast;
    private final double normalizedHydro;
    private final MacroBiome.MacroBiomeVariant variant;
    private final long microSiteId;
    private final int microVariantId;
    private final double macroBaseHeight;
    private final double macroVariance;
    private final double microVariance;
    private final double macroHeightNoise;
    private final double microHeightNoise;
    private final double finalBaseHeight;
    private final double finalMicroVariance;
    private final double heightNoiseSample;
    private final boolean transitionOverride;
    private final double transitionCoastWidth;
    private final String transitionRuleId;

    private final double worldBaseHeight;
    private final double worldMacroVariance;
    private final double worldMicroVariance;
    private final double heightVariation;

    public MacroSelectionResult(long macroSiteId,
                                MacroSite primarySite,
                                MacroSite secondarySite,
                                double primaryDistance,
                                double secondaryDistance,
                                double edgeMetric,
                                double edgeFactor,
                                boolean edgeFlipped,
                                MacroTag macroTag,
                                MacroBiome macroBiome,
                                boolean rare,
                                double coastDistance,
                                double coastWidth,
                                double shelfWidth,
                                int patchId,
                                double continentalScore,
                                double humidity,
                                double temperature,
                                double normalizedCoast,
                                double normalizedHydro,
                                MacroBiome.MacroBiomeVariant variant,
                                long microSiteId,
                                int microVariantId,
                                double macroBaseHeight,
                                double macroVariance,
                                double microVariance,
                                double macroHeightNoise,
                                double microHeightNoise,
                                double finalBaseHeight,
                                double finalMicroVariance,
                                double heightNoiseSample,
                                boolean transitionOverride,
                                double transitionCoastWidth,
                                String transitionRuleId,
                                double worldBaseHeight,
                                double worldMacroVariance,
                                double worldMicroVariance,
                                double heightVariation) {

        this.macroSiteId = macroSiteId;
        this.primarySite = Objects.requireNonNull(primarySite, "primarySite");
        this.secondarySite = secondarySite;
        this.primaryDistance = Double.isNaN(primaryDistance) ? 0.0d : primaryDistance;
        this.secondaryDistance = Double.isNaN(secondaryDistance) ? Double.POSITIVE_INFINITY : secondaryDistance;
        this.edgeMetric = edgeMetric;
        this.edgeFactor = Double.isNaN(edgeFactor) ? 1.0d : edgeFactor;
        this.edgeFlipped = edgeFlipped;
        this.macroTag = Objects.requireNonNull(macroTag, "macroTag");
        this.macroBiome = Objects.requireNonNull(macroBiome, "macroBiome");
        this.rare = rare;
        this.coastDistance = coastDistance;
        this.coastWidth = coastWidth;
        this.shelfWidth = shelfWidth;
        this.patchId = patchId;
        this.continentalScore = continentalScore;
        this.humidity = humidity;
        this.temperature = temperature;
        this.normalizedCoast = normalizedCoast;
        this.normalizedHydro = normalizedHydro;
        this.variant = variant;
        this.microSiteId = microSiteId;
        this.microVariantId = microVariantId;
        this.macroBaseHeight = Double.isNaN(macroBaseHeight) ? 0.0d : macroBaseHeight;
        this.macroVariance = Double.isNaN(macroVariance) ? 0.0d : macroVariance;
        this.microVariance = Double.isNaN(microVariance) ? 0.0d : microVariance;
        this.macroHeightNoise = Double.isNaN(macroHeightNoise) ? 0.0d : macroHeightNoise;
        this.microHeightNoise = Double.isNaN(microHeightNoise) ? 0.0d : microHeightNoise;
        this.finalBaseHeight = Double.isNaN(finalBaseHeight) ? this.macroBaseHeight : finalBaseHeight;
        this.finalMicroVariance = Double.isNaN(finalMicroVariance) ? this.microVariance : Math.max(0.0d, finalMicroVariance);
        this.heightNoiseSample = Double.isNaN(heightNoiseSample) ? 0.0d : Math.max(-1.0d, Math.min(1.0d, heightNoiseSample));
        this.transitionOverride = transitionOverride;
        this.transitionCoastWidth = transitionCoastWidth;
        this.transitionRuleId = transitionRuleId;
        this.worldBaseHeight = worldBaseHeight;
        this.worldMacroVariance = Math.max(0.0d, worldMacroVariance);
        this.worldMicroVariance = Math.max(0.0d, worldMicroVariance);
        this.heightVariation = Double.isNaN(heightVariation) ? 0.0d : Math.max(0.0d, heightVariation);
    }

    public long macroSiteId() {
        return macroSiteId;
    }

    public MacroSite primarySite() {
        return primarySite;
    }

    public MacroSite secondarySite() {
        return secondarySite;
    }

    public double primaryDistance() {
        return primaryDistance;
    }

    public double secondaryDistance() {
        return secondaryDistance;
    }

    public double edgeMetric() {
        return edgeMetric;
    }

    public double edgeFactor() {
        return edgeFactor;
    }

    public boolean edgeFlipped() {
        return edgeFlipped;
    }

    public MacroTag macroTag() {
        return macroTag;
    }

    public MacroBiome macroBiome() {
        return macroBiome;
    }

    public boolean rare() {
        return rare;
    }

    public double coastDistance() {
        return coastDistance;
    }

    public double coastWidth() {
        return coastWidth;
    }

    public double shelfWidth() {
        return shelfWidth;
    }

    public int patchId() {
        return patchId;
    }

    public double continentalScore() {
        return continentalScore;
    }

    public double humidity() {
        return humidity;
    }

    public double temperature() {
        return temperature;
    }

    public double normalizedCoast() {
        return normalizedCoast;
    }

    public double normalizedHydro() {
        return normalizedHydro;
    }

    public MacroBiome.MacroBiomeVariant variant() {
        return variant;
    }

    public long microSiteId() {
        return microSiteId;
    }

    public int microVariantId() {
        return microVariantId;
    }

    public double macroBaseHeight() {
        return macroBaseHeight;
    }

    public double macroVariance() {
        return macroVariance;
    }

    public double microVariance() {
        return microVariance;
    }

    public double macroHeightNoise() {
        return macroHeightNoise;
    }

    public double microHeightNoise() {
        return microHeightNoise;
    }

    public double finalBaseHeight() {
        return finalBaseHeight;
    }

    public double finalMicroVariance() {
        return finalMicroVariance;
    }

    public double heightNoiseSample() {
        return heightNoiseSample;
    }

    public boolean transitionOverride() {
        return transitionOverride;
    }

    public double transitionCoastWidth() {
        return transitionCoastWidth;
    }

    public String transitionRuleId() {
        return transitionRuleId;
    }

    public boolean isOceanic() {
        return macroTag.isOceanic();
    }

    public boolean isCoastal() {
        return macroTag.isCoastal();
    }

    public boolean isFrozen() {
        return macroTag.isFrozen();
    }

    public double distanceToEdge() {
        return edgeMetric;
    }

    public boolean isInTransitionBand() {
        return Double.isFinite(edgeMetric) && edgeMetric <= 0.0d;
    }

    public boolean hasMicroSite() {
        return microSiteId != -1L;
    }

    public boolean heightNoiseApplied() {
        return Math.abs(heightNoiseSample) > 1.0e-6;
    }

    public boolean transitionOverrideApplied() {
        return transitionOverride;
    }

    public double worldBaseHeight() {
        return worldBaseHeight;
    }

    public double worldMacroVariance() {
        return worldMacroVariance;
    }

    public double worldMicroVariance() {
        return worldMicroVariance;
    }

    public double heightVariation() {
        return heightVariation;
    }
}
