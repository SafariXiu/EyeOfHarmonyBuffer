package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.MacroSite;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.github.bsideup.jabel.Desugar;

import java.util.Objects;

@Desugar
public record MacroSelectionResult(
    long macroSiteId,
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
    int microVariantId
) {

    public MacroSelectionResult {
        Objects.requireNonNull(macroTag, "macroTag");
        Objects.requireNonNull(macroBiome, "macroBiome");
        if (primarySite == null) {
            throw new IllegalArgumentException("primarySite cannot be null");
        }
        if (Double.isNaN(edgeFactor)) {
            edgeFactor = 1.0d;
        }
        if (Double.isNaN(primaryDistance)) {
            primaryDistance = 0.0d;
        }
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
}
