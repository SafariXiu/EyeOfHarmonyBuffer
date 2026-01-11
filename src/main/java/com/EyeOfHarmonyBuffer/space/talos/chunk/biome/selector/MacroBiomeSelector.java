package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.command.TalosClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition.TransitionResolver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition.TransitionRule;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MacroBiomeSelector {

    private static final Logger LOGGER = LogManager.getLogger(MacroBiomeSelector.class);

    private final FieldManager fieldManager;
    private final MacroSelectorConfig config;
    private final long worldSeed;
    private final MacroSiteManager macroSiteManager;
    private final MicroSiteManager microSiteManager;
    private final TransitionResolver transitionResolver;
    private final MacroSelectorConfig.HeightContinuitySettings continuitySettings;

    public MacroBiomeSelector(FieldManager fieldManager,
                              long worldSeed,
                              MacroFieldProvider macroFieldProvider,
                              MacroSelectorConfig config) {
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.worldSeed = worldSeed;
        this.config = Objects.requireNonNull(config, "config");
        this.macroSiteManager = new MacroSiteManager(fieldManager, macroFieldProvider, config, worldSeed);
        this.microSiteManager = new MicroSiteManager(fieldManager, config, worldSeed);
        this.transitionResolver = new TransitionResolver(config.transitionSettings());
        this.continuitySettings = config.heightContinuity();
    }

    public MacroSelectionResult select(int blockX, int blockZ) {
        MacroSiteQueryResult query = macroSiteManager.query(blockX, blockZ);
        MacroSite primarySite = Objects.requireNonNull(query.primary(), "MacroSiteManager primarySite");
        MacroSite secondarySite = query.secondary();

        double primaryDistance = query.primaryDistance();
        double secondaryDistance = query.secondaryDistance();
        double edgeMetric = query.edgeMetric();

        if (secondarySite != null && !isBlendCompatible(primarySite, secondarySite)) {
            secondarySite = null;
            secondaryDistance = Double.POSITIVE_INFINITY;
            edgeMetric = Double.POSITIVE_INFINITY;
        }

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

        MacroBiome.MacroBiomeVariant variant;
        if (microSite != null && microSite.variant() != null) {
            variant = microSite.variant();
        } else {
            variant = pickVariant(macroBiome, blockX, blockZ, patchId, activeSite.id());
        }

        TransitionResolver.Result transitionResult = transitionResolver.evaluate(
            macroBiome,
            variant,
            coastDistance
        );

        boolean transitionOverride = false;
        double transitionCoastWidth = transitionResult.appliedCoastWidth();
        String transitionRuleId = null;

        if (transitionResult.requiresOverride()) {
            MacroBiome.MacroBiomeVariant constrained = pickVariantWithWhitelist(
                macroBiome,
                blockX,
                blockZ,
                patchId,
                activeSite.id(),
                transitionResult.rule()
            );
            if (constrained != null) {
                variant = constrained;
                transitionOverride = true;
                transitionRuleId = transitionResult.rule().descriptor();
            } else {
                LOGGER.warn(
                    "[MacroSelector] Transition override requested but no allowed variant present for macroBiome={} rule={}",
                    macroBiome.id,
                    transitionResult.rule() != null ? transitionResult.rule().descriptor() : "none"
                );
            }
        }

        HeightComputation primaryHeight = computeHeight(primarySite, blockX, blockZ);
        HeightComputation secondaryHeight = (secondarySite != null)
            ? computeHeight(secondarySite, blockX, blockZ)
            : primaryHeight;

        double edgeFactorInput = secondarySite != null ? edgeMetric : Double.POSITIVE_INFINITY;
        double edgeFactor = computeEdgeFactor(edgeFactorInput);
        double blend = computeBlendWeight(edgeFactor);

        HeightComputation height = (secondarySite != null)
            ? HeightComputation.lerp(primaryHeight, secondaryHeight, blend)
            : primaryHeight;

        height = applyEdgeContinuity(height, primaryHeight, edgeFactor, secondarySite != null);

        MacroSelectorConfig.HeightProfile profile = config.heightProfile();
        double floor = profile != null ? profile.terrainFloorY() : 0.0d;
        double ceiling = profile != null ? profile.terrainCeilingY() : 256.0d;
        double range = Math.max(1.0d, ceiling - floor);

        double seaLevelY = profile != null ? profile.seaLevelY() : 64.0d;

        if (macroTag != null && macroTag.isOceanic()) {
            height = clampOceanicHeights(height, seaLevelY, floor, range);
        }

        double normalizedBase = clamp01(0.5d * (height.finalBaseHeight() + 1.0d));
        double worldBaseHeight = floor + normalizedBase * range;
        double worldMacroVariance = clamp01(0.5d * (height.macroVariance() + 1.0d)) * range;
        double worldMicroVariance = Math.max(0.0d, height.finalMicroVariance()) * range;

        MacroSelectionResult result = new MacroSelectionResult(
            activeSite.id(),
            primarySite,
            secondarySite,
            primaryDistance,
            secondarySite != null ? secondaryDistance : Double.POSITIVE_INFINITY,
            secondarySite != null ? edgeMetric : Double.POSITIVE_INFINITY,
            computeEdgeFactor(secondarySite != null ? edgeMetric : Double.POSITIVE_INFINITY),
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
            microVariantId,
            height.baseHeight(),
            height.macroVariance(),
            height.microVariance(),
            height.macroNoise(),
            height.microNoise(),
            height.finalBaseHeight(),
            height.finalMicroVariance(),
            height.heightNoiseSample(),
            transitionOverride,
            transitionCoastWidth,
            transitionRuleId,
            worldBaseHeight,
            worldMacroVariance,
            worldMicroVariance,
            height.heightVariation()
        );

        if (config.debugLogging() && LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "[MacroSelector] pos=({}, {}) macroSite={} microSite={} tag={} rare={} edgeMetric={} edgeFactor={} flipped={} transitionOverride={}",
                blockX,
                blockZ,
                activeSite.id(),
                microSiteId,
                macroTag,
                rare,
                edgeMetric,
                computeEdgeFactor(edgeMetric),
                edgeFlipped,
                transitionOverride
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

    private HeightComputation computeHeight(MacroSite site, int blockX, int blockZ) {
        MacroSite.HeightSample sample;
        if (config.continuousHeightField()) {
            sample = site.sampleHeightField(blockX, blockZ, config.macroGridSize());
        } else {
            sample = new MacroSite.HeightSample(
                site.baseHeight(),
                site.macroVariance(),
                site.microVariance(),
                site.heightVariation()
            );
        }

        double variation = Math.max(0.0d, sample.variation());

        MacroSelectorConfig.HeightSettings settings = config.heightSettings();
        if (settings == null || !settings.enabled() || settings.noise() == null) {
            return new HeightComputation(
                sample.base(),
                sample.macroVariance(),
                sample.microVariance(),
                0.0d,
                0.0d,
                sample.base(),
                sample.microVariance(),
                0.0d,
                variation
            );
        }

        MacroSelectorConfig.NoiseSettings noiseSettings = settings.noise();
        double raw = NoiseUtil.fractal(
            worldSeed,
            config.baseSalt() ^ noiseSettings.salt(),
            blockX,
            blockZ,
            noiseSettings.frequency(),
            noiseSettings.octaves(),
            noiseSettings.lacunarity(),
            noiseSettings.gain()
        );
        double normalized = raw * 2.0d - 1.0d;

        double macroHeightNoise = normalized * settings.macroNoiseStrength() * Math.max(0.25d, variation);
        double microHeightNoise = normalized * settings.microNoiseStrength() * Math.max(0.25d, variation);

        double finalBaseHeight = sample.base() + macroHeightNoise;
        double finalMicroVariance = Math.max(0.0d, sample.microVariance() + microHeightNoise);

        return new HeightComputation(
            sample.base(),
            sample.macroVariance(),
            sample.microVariance(),
            macroHeightNoise,
            microHeightNoise,
            finalBaseHeight,
            finalMicroVariance,
            normalized,
            variation
        );
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
        if (index < 0) {
            index = 0;
        }
        return variants.get(index);
    }

    private MacroBiome.MacroBiomeVariant pickVariantWithWhitelist(MacroBiome macroBiome,
                                                                  int blockX,
                                                                  int blockZ,
                                                                  int patchId,
                                                                  long macroSiteId,
                                                                  TransitionRule rule) {
        if (macroBiome == null || rule == null) {
            return null;
        }
        List<MacroBiome.MacroBiomeVariant> variants = macroBiome.variants;
        if (variants == null || variants.isEmpty()) {
            return null;
        }

        List<MacroBiome.MacroBiomeVariant> allowed = new ArrayList<>();
        for (MacroBiome.MacroBiomeVariant candidate : variants) {
            if (rule.allowsVariant(candidate)) {
                allowed.add(candidate);
            }
        }

        if (allowed.isEmpty()) {
            return null;
        }

        long salt = config.baseSalt() ^ macroBiome.id ^ patchId ^ macroSiteId ^ 0x75F1A3E5B4C2D19EL;
        int index = NoiseUtil.weightedIndex(worldSeed, salt, blockX, blockZ, allowed);
        if (index < 0) {
            index = 0;
        }
        return allowed.get(index);
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

    public TalosClimateSample buildDiagnosticSample(int blockX, int blockZ) {
        MacroSelectionResult result = select(blockX, blockZ);

        MacroBiome biome = result.macroBiome();
        MacroTag tag = result.macroTag();

        float temperature = (float) result.temperature();
        float humidity = (float) result.humidity();
        float macroVariance = (float) result.macroVariance();

        return new TalosClimateSample.Builder(blockX, blockZ)
            .macroBiome(biome.name(), biome.id)
            .climate(temperature, humidity, macroVariance)
            .heights(
                result.finalBaseHeight(),
                result.macroVariance(),
                result.finalMicroVariance()
            )
            .hardEdge(biome.isHardEdge())
            .plateauAnchorWeight(biome.getPlateauAnchorWeight())
            .oceanicCandidate(tag != null && tag.isOceanic())
            .hydroLevel(result.normalizedHydro())
            .distanceToCoast(result.coastDistance())
            .message(String.format(
                "macroSite=%d secondary=%s transitionOverride=%s rule=%s",
                result.macroSiteId(),
                result.secondarySite() != null ? result.secondarySite().id() : "none",
                result.transitionOverride(),
                result.transitionRuleId() != null ? result.transitionRuleId() : "none"
            ))
            .build();
    }

    private static final class HeightComputation {

        private final double macroBaseHeight;
        private final double macroVariance;
        private final double microVariance;
        private final double macroHeightNoise;
        private final double microHeightNoise;
        private final double finalBaseHeight;
        private final double finalMicroVariance;
        private final double noiseSample;
        private final double heightVariation;

        private HeightComputation(double macroBaseHeight,
                                  double macroVariance,
                                  double microVariance,
                                  double macroHeightNoise,
                                  double microHeightNoise,
                                  double finalBaseHeight,
                                  double finalMicroVariance,
                                  double noiseSample,
                                  double heightVariation) {
            this.macroBaseHeight = macroBaseHeight;
            this.macroVariance = macroVariance;
            this.microVariance = microVariance;
            this.macroHeightNoise = macroHeightNoise;
            this.microHeightNoise = microHeightNoise;
            this.finalBaseHeight = finalBaseHeight;
            this.finalMicroVariance = finalMicroVariance;
            this.noiseSample = noiseSample;
            this.heightVariation = heightVariation;
        }

        double macroVariance() {
            return macroVariance;
        }

        double microVariance() {
            return microVariance;
        }

        double macroHeightNoise() {
            return macroHeightNoise;
        }

        double microHeightNoise() {
            return microHeightNoise;
        }

        double finalBaseHeight() {
            return finalBaseHeight;
        }

        double finalMicroVariance() {
            return finalMicroVariance;
        }

        double noiseSample() {
            return noiseSample;
        }

        double heightVariation() {
            return heightVariation;
        }

        double baseHeight() {
            return macroBaseHeight;
        }

        double macroNoise() {
            return macroHeightNoise;
        }

        double microNoise() {
            return microHeightNoise;
        }

        double heightNoiseSample() {
            return noiseSample;
        }

        static HeightComputation lerp(HeightComputation a, HeightComputation b, double t) {
            double inv = 1.0d - t;
            return new HeightComputation(
                lerp(a.macroBaseHeight, b.macroBaseHeight, inv, t),
                lerp(a.macroVariance, b.macroVariance, inv, t),
                lerp(a.microVariance, b.microVariance, inv, t),
                lerp(a.macroHeightNoise, b.macroHeightNoise, inv, t),
                lerp(a.microHeightNoise, b.microHeightNoise, inv, t),
                lerp(a.finalBaseHeight, b.finalBaseHeight, inv, t),
                lerp(a.finalMicroVariance, b.finalMicroVariance, inv, t),
                lerp(a.noiseSample, b.noiseSample, inv, t),
                lerp(a.heightVariation, b.heightVariation, inv, t)
            );
        }

        HeightComputation clampFinalBase(double min, double max) {
            double clamped = MathHelper.clamp_double(finalBaseHeight, min, max);
            if (clamped == finalBaseHeight) {
                return this;
            }
            return new HeightComputation(
                macroBaseHeight,
                macroVariance,
                microVariance,
                macroHeightNoise,
                microHeightNoise,
                clamped,
                finalMicroVariance,
                noiseSample,
                heightVariation
            );
        }

        HeightComputation scaleVariance(double scale) {
            double s = MathHelper.clamp_double(scale, 0.0d, 1.0d);
            if (Math.abs(s - 1.0d) < 1.0e-6d) {
                return this;
            }
            return new HeightComputation(
                macroBaseHeight,
                macroVariance * s,
                microVariance * s,
                macroHeightNoise * s,
                microHeightNoise * s,
                finalBaseHeight,
                finalMicroVariance * s,
                noiseSample,
                heightVariation
            );
        }

        private static double lerp(double a, double b, double inv, double t) {
            return a * inv + b * t;
        }
    }

    private static HeightComputation clampOceanicHeights(HeightComputation original,
                                                         double seaLevelY,
                                                         double floor,
                                                         double range) {
        if (original == null) {
            return null;
        }

        double seaLevel01 = clamp01((seaLevelY - floor) / range);

        double base01 = clamp01(0.5d * (original.finalBaseHeight() + 1.0d));
        double clampedBase01 = Math.min(base01, seaLevel01);
        double clampedFinalBase = clampedBase01 * 2.0d - 1.0d;
        double baseWorld = floor + clampedBase01 * range;

        double macroVar01 = clamp01(0.5d * (original.macroVariance() + 1.0d));
        double macroWorld = macroVar01 * range;
        double microWorld = Math.max(0.0d, original.finalMicroVariance()) * range;

        double headroom = Math.max(0.0d, seaLevelY - baseWorld);
        double totalVariance = macroWorld + microWorld;

        double varianceScale = (totalVariance <= headroom || totalVariance <= 0.0d)
            ? 1.0d
            : headroom / totalVariance;

        double clampedMacroWorld = macroWorld * varianceScale;
        double clampedMicroWorld = microWorld * varianceScale;

        double clampedMacro01 = clamp01(clampedMacroWorld / range);
        double clampedMacroVariance = clampedMacro01 * 2.0d - 1.0d;
        double clampedFinalMicroVariance = clampedMicroWorld / range;

        double adjustedMacroHeightNoise = clampedFinalBase - original.baseHeight();
        double adjustedMicroHeightNoise = clampedFinalMicroVariance - original.microVariance();

        return new HeightComputation(
            original.baseHeight(),
            clampedMacroVariance,
            original.microVariance(),
            adjustedMacroHeightNoise,
            adjustedMicroHeightNoise,
            clampedFinalBase,
            clampedFinalMicroVariance,
            original.heightNoiseSample(),
            original.heightVariation()
        );
    }

    private double computeBlendWeight(double edgeFactor) {
        double base = 1.0d - edgeFactor;
        if (continuitySettings == null || continuitySettings.disabled()) {
            return base;
        }
        return 1.0d - smoothStep(edgeFactor);
    }

    private HeightComputation applyEdgeContinuity(HeightComputation height,
                                                  HeightComputation primaryHeight,
                                                  double edgeFactor,
                                                  boolean hasSecondary) {
        if (continuitySettings == null || continuitySettings.disabled() || height == null) {
            return height;
        }

        HeightComputation adjusted = height;

        if (hasSecondary && primaryHeight != null && continuitySettings.maxEdgeDelta() > 0.0d) {
            double delta = continuitySettings.maxEdgeDelta();
            double min = primaryHeight.finalBaseHeight() - delta;
            double max = primaryHeight.finalBaseHeight() + delta;
            adjusted = adjusted.clampFinalBase(min, max);
        }

        double varianceScale = Math.pow(Math.max(0.0d, 1.0d - edgeFactor),
            Math.max(0.0d, continuitySettings.varianceFalloff()));
        adjusted = adjusted.scaleVariance(varianceScale);

        return adjusted;
    }

    private static double smoothStep(double t) {
        double clamped = clamp01(t);
        return clamped * clamped * (3.0d - 2.0d * clamped);
    }

    public MacroSelectorConfig config() {
        return this.config;
    }

    public MacroSelectorConfig.HeightProfile heightProfile() {
        return this.config.heightProfile();
    }
}
