package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;

public final class MacroBiomeSelector {

    private static final Logger LOGGER = LogManager.getLogger(MacroBiomeSelector.class);

    private final FieldManager fieldManager;
    private final MacroSelectorConfig config;
    private final long worldSeed;
    private final MacroSiteManager macroSiteManager;
    private final MicroSiteManager microSiteManager;

    public MacroBiomeSelector(FieldManager fieldManager,
                              long worldSeed,
                              MacroSelectorConfig config) {
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.worldSeed = worldSeed;
        this.config = Objects.requireNonNull(config, "config");
        this.macroSiteManager = new MacroSiteManager(fieldManager, config, worldSeed);
        this.microSiteManager = new MicroSiteManager(fieldManager, config, worldSeed);
    }

    public MacroSelectionResult select(int blockX, int blockZ) {
        MacroSiteQueryResult query = macroSiteManager.query(blockX, blockZ);
        MacroSite primarySite = Objects.requireNonNull(query.primary(), "MacroSiteManager primarySite");
        MacroSite secondarySite = query.secondary();

        if (secondarySite != null && !isBlendCompatible(primarySite, secondarySite)) {
            secondarySite = null;
        }

        double edgeMetric = query.edgeMetric();
        boolean edgeFlipped = false;
        MacroSite activeSite = primarySite;

        if (secondarySite != null && shouldFlipToSecondary(edgeMetric, blockX, blockZ)) {
            edgeFlipped = true;
            activeSite = secondarySite;
        }

        MacroTag macroTag = activeSite.macroTag();
        MacroBiome macroBiome = activeSite.macroBiome();
        double continentalScore = activeSite.continentalScore();
        double humidity = clamp01(activeSite.humidity());
        double temperature = clamp01(activeSite.temperature());

        HydroSample hydro = Objects.requireNonNull(
            fieldManager.sampleHydro(blockX, blockZ),
            "HydroSample"
        );

        double coastDistance = hydro.coastDistance();
        MacroSelectorConfig.ContinentalSettings continental = config.continentalSettings();
        double normalizedCoast = continental.normalizeCoast(coastDistance);
        double normalizedHydro = continental.normalizeHydro(hydro.saturation());
        double coastWidth = continental.beachWidthBlocks();
        double shelfWidth = continental.shelfWidthBlocks();

        boolean rare = config.rareSettings().enabled()
            && computeRareNoise(blockX, blockZ) >= config.rareSettings().clampThreshold();
        int patchId = config.patchNoise().enabled() ? computePatchId(blockX, blockZ) : 0;

        MicroSite microSite = microSiteManager.resolve(activeSite, macroBiome, blockX, blockZ);
        long microSiteId = microSite != null ? microSite.id() : -1L;
        int microVariantId = microSite != null ? microSite.variantIndex() : -1;

        MacroBiome.MacroBiomeVariant variant = null;
        if (microSite != null && microSite.variant() != null) {
            variant = microSite.variant();
        } else {
            variant = pickVariant(macroBiome, blockX, blockZ, patchId, activeSite.id());
        }

        double edgeFactor = computeEdgeFactor(edgeMetric);

        MacroSelectionResult result = new MacroSelectionResult(
            activeSite.id(),
            primarySite,
            secondarySite,
            query.primaryDistance(),
            query.secondaryDistance(),
            edgeMetric,
            edgeFactor,
            edgeFlipped,
            macroTag,
            macroBiome,
            rare,
            coastDistance,
            coastWidth,
            shelfWidth,
            patchId,
            continentalScore,
            humidity,
            temperature,
            normalizedCoast,
            normalizedHydro,
            variant,
            microSiteId,
            microVariantId
        );

        if (config.debugLogging() && LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "[MacroSelector] pos=({}, {}) macroSite={} microSite={} tag={} rare={} edgeMetric={} edgeFactor={} flipped={}",
                blockX,
                blockZ,
                activeSite.id(),
                microSiteId,
                macroTag,
                rare,
                edgeMetric,
                edgeFactor,
                edgeFlipped
            );
        }

        return result;
    }

    private double computeRareNoise(int blockX, int blockZ) {
        MacroSelectorConfig.RareSettings rare = config.rareSettings();
        double nx = blockX * rare.frequency();
        double nz = blockZ * rare.frequency();
        return NoiseUtil.valueNoise(worldSeed, config.baseSalt() ^ rare.salt(), nx, nz);
    }

    private int computePatchId(int blockX, int blockZ) {
        MacroSelectorConfig.NoiseSettings patch = config.patchNoise();
        double noise = NoiseUtil.fractal(
            worldSeed,
            config.baseSalt() ^ patch.salt(),
            blockX,
            blockZ,
            patch.frequency(),
            patch.octaves(),
            patch.lacunarity(),
            patch.gain()
        );
        noise = clamp01(noise);
        int id = (int) Math.floor(noise * patch.scale());
        return Math.max(0, id);
    }

    private boolean shouldFlipToSecondary(double edgeMetric, int blockX, int blockZ) {
        if (!Double.isFinite(edgeMetric)) {
            return false;
        }
        double blendWidth = config.macroBlendWidth();
        if (edgeMetric >= blendWidth) {
            return false;
        }
        double intensity = 1.0d - clamp01(edgeMetric / blendWidth);
        if (intensity <= 0.0d) {
            return false;
        }

        double flipProbability = intensity * 0.5d * config.edgeNoiseAmplitude();
        flipProbability = clamp01(flipProbability);
        if (flipProbability <= 0.0d) {
            return false;
        }

        double nx = blockX * config.edgeNoiseFrequency();
        double nz = blockZ * config.edgeNoiseFrequency();
        double raw = NoiseUtil.valueNoise(worldSeed, config.edgeNoiseSalt() ^ config.baseSalt(), nx, nz);
        double noise01 = 0.5d + 0.5d * raw;
        return noise01 < flipProbability;
    }

    private double computeEdgeFactor(double edgeMetric) {
        if (!Double.isFinite(edgeMetric)) {
            return 1.0d;
        }
        double normalized = edgeMetric / config.macroBlendWidth();
        return clamp01(normalized);
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

    private MacroBiome.MacroBiomeVariant pickVariant(MacroBiome macroBiome,
                                                     int blockX,
                                                     int blockZ,
                                                     int patchId,
                                                     long macroSiteId) {
        List<MacroBiome.MacroBiomeVariant> variants = macroBiome.variants;
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        long salt = config.baseSalt() ^ macroBiome.id ^ patchId ^ macroSiteId;
        int index = NoiseUtil.weightedIndex(worldSeed, salt, blockX, blockZ, variants);
        return variants.get(index);
    }

    private static boolean isBlendCompatible(MacroSite a, MacroSite b) {
        if (a == null || b == null) {
            return false;
        }
        MacroTag ta = a.macroTag();
        MacroTag tb = b.macroTag();
        if (ta == null || tb == null) {
            return false;
        }
        if (ta.isOceanic() != tb.isOceanic()) {
            return false;
        }
        if (ta.isCoastal() != tb.isCoastal()) {
            return false;
        }
        return true;
    }
}
