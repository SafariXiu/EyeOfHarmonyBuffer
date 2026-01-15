package com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector;

import com.EyeOfHarmonyBuffer.command.TalosClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition.TransitionResolver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.transition.TransitionRule;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import net.minecraft.util.MathHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * MacroBiomeSelector
 *
 * 职责：
 * - 给定世界坐标 (blockX, blockZ)，选择当前点的宏站点（primary/secondary）与 MacroBiome/MacroTag/variant 等信息
 * - 计算该点用于地形生成的宏高度参数，并输出不可变结果 MacroSelectionResult
 *
 * 主要输入：
 * - MacroSiteManager 的 query 结果：primary/secondary + hits（候选站点距离列表）
 * - FieldManager 的场采样：HydroSample（海岸/河流/湿度等）、TerrainSample（坡度/粗糙度/海拔等）
 * - ContinuousHeightField：生成连续的 worldY 基准高度（减少大尺度断裂）
 * - TransitionResolver：海岸带/过渡规则，可能强制更换 variant
 *
 * 核心流程（简化）：
 * - 站点选择：primary/secondary 与 edgeMetric/edgeFactor 评估
 * - 高度融合：对 hits 使用 gaussian 权重混合 base/variance/noise 等高度参数
 * - 边界连续性：applyEdgeContinuity 限制边界处基准高度差并衰减方差
 * - signed->worldY：将 signed 高度映射到世界 Y（floor/range），并与 continuous worldY 融合
 *
 * 本次改动（接入 MacroBiome Height Profile 三参数）：
 * - 在 hits gaussian 融合阶段，同时对以下参数做同权重混合（避免边界跳变）：
 *   - baseHeightOffset（宏形状偏置）
 *   - absoluteMin / absoluteMax（MacroBiome 允许的世界高度硬下限/硬上限）
 * - 在 world-space 高度计算后，对 worldBaseHeight 与 continuousWorldBaseHeight 按 blendedAbsMin/Max 做 clamp
 * - 将 blendedBaseHeightOffset / blendedAbsMinY / blendedAbsMaxY 写入 MacroSelectionResult，供 ChunkProvider 使用
 *
 * 设计要点：
 * - absoluteMin/absoluteMax 的语义是“世界 Y 空间的硬约束”，适合在 worldY 计算后 clamp
 * - baseHeightOffset 的语义是“风格偏置”，最终如何影响噪声/振幅由 ChunkProvider 实现
 */
public final class MacroBiomeSelector {

    private static final Logger LOGGER = LogManager.getLogger(MacroBiomeSelector.class);

    private static final boolean DEBUG_EDGE = true;
    private static final boolean DEBUG_DISABLE_EDGE_CLAMP = false;
    private static final int DEBUG_PRINT_EVERY_N_BLOCKS = 16;
    private static final double DEBUG_EDGE_PRINT_THRESHOLD = 0.85;

    private final FieldManager fieldManager;
    private final MacroSelectorConfig config;
    private final long worldSeed;
    private final MacroSiteManager macroSiteManager;
    private final MicroSiteManager microSiteManager;
    private final TransitionResolver transitionResolver;
    private final MacroSelectorConfig.HeightContinuitySettings continuitySettings;
    private final ContinuousHeightField continuousHeightField;

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
        this.continuousHeightField = new ContinuousHeightField(config.heightProfile());
    }

    public MacroSelectionResult select(int blockX, int blockZ) {
        MacroSiteQueryResult query = macroSiteManager.query(blockX, blockZ);
        MacroSite primarySite = Objects.requireNonNull(query.primary(), "MacroSiteManager primarySite");
        MacroSite secondarySite = query.secondary();

        double primaryDistance = query.primaryDistance();
        double secondaryDistance = query.secondaryDistance();

        final boolean edgeFlipped = false;
        final MacroSite activeSite = primarySite;

        final MacroTag macroTag = primarySite.macroTag();
        final MacroBiome macroBiome = primarySite.macroBiome();
        final double continentalScore = primarySite.continentalScore();
        final double humidity = clamp01(primarySite.humidity());
        final double temperature = clamp01(primarySite.temperature());

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

        TerrainSample terrainXZ = Objects.requireNonNull(
            fieldManager.sampleTerrain(blockX, blockZ),
            "TerrainSample"
        );

        double continentalScoreXZ = config.continentalSettings().compose(
            terrainXZ.elevation(),
            hydro.coastDistance(),
            hydro.saturation()
        );

        double riverStrength = clamp01(hydro.riverStrength());

        final double RIVER_DISTANCE_SCALE_BLOCKS = 1024.0d;
        double riverDistanceBlocks = clamp01(hydro.riverDistance()) * RIVER_DISTANCE_SCALE_BLOCKS;

        final double SLOPE_NORM = 0.08d;
        double slope01 = clamp01(terrainXZ.slope() / SLOPE_NORM);

        double roughness01 = clamp01(terrainXZ.roughness());

        ContinuousHeightField.HeightContext continuous = continuousHeightField.sample(
            continentalScoreXZ,
            clamp01(hydro.saturation()),
            riverStrength,
            riverDistanceBlocks,
            slope01,
            roughness01
        );

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
        HeightComputation secondaryHeight = null;
        if (secondarySite != null) {
            secondaryHeight = computeHeight(secondarySite, blockX, blockZ);
        }

        List<MacroSiteManager.SiteHit> hits = Objects.requireNonNull(query.hits(), "hits");

        double edgeMetricStable = Double.POSITIVE_INFINITY;

        if (hits != null && hits.size() >= 2) {
            double d1 = hits.get(0).dist;
            double d2 = hits.get(1).dist;

            edgeMetricStable = d2 - d1;

            if (hits.size() >= 3) {
                double d3 = hits.get(2).dist;
                edgeMetricStable = Math.min(edgeMetricStable, d3 - d1);
            }
        }

        if (secondarySite == null) {
            edgeMetricStable = Double.POSITIVE_INFINITY;
        } else if (!isBlendCompatible(primarySite, secondarySite)) {
            secondarySite = null;
            secondaryDistance = Double.POSITIVE_INFINITY;
            secondaryHeight = null;
            edgeMetricStable = Double.POSITIVE_INFINITY;
        }

        double sigma = Math.max(1.0d, config.macroBlendWidth());

        double wSum = 0.0d;

        double base = 0, macroVar = 0, microVar = 0;
        double macroNoise = 0, microNoise = 0;
        double finalBase = 0, finalMicro = 0;
        double noiseSample= 0, variation = 0;
        double baseHeightOffsetAcc = 0.0d;
        double absMinAcc = 0.0d;
        double absMaxAcc = 0.0d;

        for (int i = 0; i < hits.size(); i++) {
            MacroSite s = hits.get(i).site;
            double d = hits.get(i).dist;

            if (i > 0 && !isBlendCompatible(primarySite, s)) continue;

            double w = gaussianW(d, sigma);
            if (w <= 0.0d) continue;

            HeightComputation h = computeHeight(s, blockX, blockZ);

            wSum += w;

            MacroBiome sb = (s != null) ? s.macroBiome() : null;
            if (sb != null && sb.height != null) {
                baseHeightOffsetAcc += w * (double) sb.height.baseHeightOffset;
                absMinAcc += w * (double) sb.height.absoluteMin;
                absMaxAcc += w * (double) sb.height.absoluteMax;
            } else {
                MacroBiome pb = macroBiome;
                if (pb != null && pb.height != null) {
                    baseHeightOffsetAcc += w * (double) pb.height.baseHeightOffset;
                    absMinAcc += w * (double) pb.height.absoluteMin;
                    absMaxAcc += w * (double) pb.height.absoluteMax;
                }
            }

            base += w * h.baseHeight();
            macroVar += w * h.macroVariance();
            microVar += w * h.microVariance();
            macroNoise += w * h.macroNoise();
            microNoise += w * h.microNoise();
            finalBase += w * h.finalBaseHeight();
            finalMicro += w * h.finalMicroVariance();
            noiseSample += w * h.noiseSample();
            variation += w * h.heightVariation();
        }

        HeightComputation height;
        if (wSum > 0.0d) {
            double inv = 1.0d / wSum;
            height = new HeightComputation(
                base * inv,
                macroVar * inv,
                microVar * inv,
                macroNoise * inv,
                microNoise * inv,
                finalBase * inv,
                finalMicro * inv,
                noiseSample * inv,
                variation * inv
            );
        } else {
            height = primaryHeight;
        }

        boolean hasSecondary = (secondarySite != null);
        double edgeFactor = computeEdgeFactor(hasSecondary ? edgeMetricStable : Double.POSITIVE_INFINITY);
        double blend = computeBlendWeight(edgeFactor);

        if (DEBUG_EDGE
            && secondarySite != null
            && (Math.floorMod(blockX, DEBUG_PRINT_EVERY_N_BLOCKS) == 0)
            && (Math.floorMod(blockZ, DEBUG_PRINT_EVERY_N_BLOCKS) == 0)) {

            if (edgeFactor < DEBUG_EDGE_PRINT_THRESHOLD) {
                LOGGER.info(
                    "[EDGE-DIAG] pos=({}, {}) edgeMetric={} edgeFactor={} blend={} " +
                        "P(site={}, biome={}, base={}, finalBase={}) " +
                        "S(site={}, biome={}, base={}, finalBase={}) " +
                        "L(finalBase={}, macroVar={}, microVar={})",
                    blockX, blockZ,
                    fmt(edgeMetricStable), fmt(edgeFactor), fmt(blend),

                    primarySite.id(), primarySite.macroBiome(),
                    fmt(primaryHeight.baseHeight()), fmt(primaryHeight.finalBaseHeight()),

                    secondarySite.id(), secondarySite.macroBiome(),
                    fmt(secondaryHeight.baseHeight()), fmt(secondaryHeight.finalBaseHeight()),

                    fmt(height.finalBaseHeight()), fmt(height.macroVariance()), fmt(height.finalMicroVariance())
                );
            }
        }

        height = applyEdgeContinuity(height, primaryHeight, secondaryHeight, edgeFactor, hasSecondary);

        double blendedBaseHeightOffset;
        double blendedAbsMinY;
        double blendedAbsMaxY;

        if (wSum > 0.0d) {
            blendedBaseHeightOffset = baseHeightOffsetAcc / wSum;
            blendedAbsMinY = absMinAcc / wSum;
            blendedAbsMaxY = absMaxAcc / wSum;
        } else {
            if (macroBiome != null && macroBiome.height != null) {
                blendedBaseHeightOffset = (double) macroBiome.height.baseHeightOffset;
                blendedAbsMinY = (double) macroBiome.height.absoluteMin;
                blendedAbsMaxY = (double) macroBiome.height.absoluteMax;
            } else {
                blendedBaseHeightOffset = 0.0d;
                blendedAbsMinY = Double.NEGATIVE_INFINITY;
                blendedAbsMaxY = Double.POSITIVE_INFINITY;
            }
        }

        if (blendedAbsMinY > blendedAbsMaxY) {
            double t0 = blendedAbsMinY;
            blendedAbsMinY = blendedAbsMaxY;
            blendedAbsMaxY = t0;
        }

        MacroSelectorConfig.HeightProfile profile = config.heightProfile();
        double floor = profile != null ? profile.terrainFloorY() : 0.0d;
        double ceiling = profile != null ? profile.terrainCeilingY() : 256.0d;
        double range = Math.max(1.0d, ceiling - floor);

        double seaLevelY = profile != null ? profile.seaLevelY() : 64.0d;

        if (macroTag != null && macroTag.isOceanic()) {
            height = clampOceanicHeights(height, seaLevelY, floor, range);

            double clampedWorldY = Math.min(continuous.worldY(), seaLevelY);
            if (clampedWorldY != continuous.worldY()) {
                continuous = new ContinuousHeightField.HeightContext(
                    continuous.continentalScore(),
                    continuous.continental01(),
                    continuous.ruggedness01(),
                    continuous.baseY(),
                    continuous.upliftY(),
                    continuous.carveY(),
                    continuous.wetDepressY(),
                    clampedWorldY,
                    continuous.detailAmpY()
                );
            }
        }

        double normalizedBase = clamp01(0.5d * (height.finalBaseHeight() + 1.0d));
        double worldBaseHeight = floor + normalizedBase * range;
        double worldMacroVariance = clamp01(0.5d * (height.macroVariance() + 1.0d)) * range;
        double worldMicroVariance = Math.max(0.0d, height.finalMicroVariance()) * range;

// 3) continuous world base Y（你原逻辑保留）
        double contY = continuous.worldY();
        if (macroTag != null && macroTag.isOceanic()) {
            contY = Math.min(contY, seaLevelY);
        }

        final double MAX_DELTA_Y = 48.0d;
        double minY = worldBaseHeight - MAX_DELTA_Y;
        double maxY = worldBaseHeight + MAX_DELTA_Y;
        if (contY < minY) contY = minY;
        else if (contY > maxY) contY = maxY;

        double edge = clamp01(edgeFactor);
        double nearEdge = 1.0d - edge;
        double t = nearEdge * nearEdge * (3.0d - 2.0d * nearEdge);

        double continuousWorldBaseHeight = contY + (worldBaseHeight - contY) * t;

        if (Double.isFinite(blendedAbsMinY) || Double.isFinite(blendedAbsMaxY)) {
            worldBaseHeight = MathHelper.clamp_double(worldBaseHeight, blendedAbsMinY, blendedAbsMaxY);
            continuousWorldBaseHeight = MathHelper.clamp_double(continuousWorldBaseHeight, blendedAbsMinY, blendedAbsMaxY);
        }

        double continuous01 = clamp01((continuousWorldBaseHeight - floor) / Math.max(1.0d, range));
        double continuousFinalBaseHeight = continuous01 * 2.0d - 1.0d;

        double continuousDetailAmpY = continuous.detailAmpY();

        MacroSelectionResult result = new MacroSelectionResult(
            activeSite.id(),
            primarySite,
            secondarySite,
            primaryDistance,
            secondarySite != null ? secondaryDistance : Double.POSITIVE_INFINITY,
            hasSecondary ? edgeMetricStable : Double.POSITIVE_INFINITY,
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
            height.heightVariation(),
            continuousFinalBaseHeight,
            continuousWorldBaseHeight,
            continuousDetailAmpY,
            blendedBaseHeightOffset,
            blendedAbsMinY,
            blendedAbsMaxY
        );

        if (DEBUG_EDGE
            && (Math.floorMod(blockX, DEBUG_PRINT_EVERY_N_BLOCKS) == 0)
            && (Math.floorMod(blockZ, DEBUG_PRINT_EVERY_N_BLOCKS) == 0)) {

            if (edgeFactor < DEBUG_EDGE_PRINT_THRESHOLD) {
                String pCell = primarySite != null ? (primarySite.cellX() + "," + primarySite.cellZ()) : "null";
                String sCell = (secondarySite != null) ? (secondarySite.cellX() + "," + secondarySite.cellZ()) : "none";

                double d1 = (hits != null && hits.size() > 0) ? hits.get(0).dist : Double.NaN;
                double d2 = (hits != null && hits.size() > 1) ? hits.get(1).dist : Double.NaN;

                LOGGER.info(
                    "[TERR-DIAG] pos=({}, {}) biome={} " +
                        "finalBase={} worldBaseY={} contWorldY={} contSigned={} " +
                        "macroVar={} microVar={} var={} " +
                        "P(site={} cell={} d={} pFinalBase={}) " +
                        "S(site={} cell={} d={} sFinalBase={})",
                    blockX, blockZ,
                    macroBiome != null ? macroBiome.name() : "null",
                    fmt(height.finalBaseHeight()),
                    fmt(worldBaseHeight),
                    fmt(continuousWorldBaseHeight),
                    fmt(continuousFinalBaseHeight),
                    fmt(height.macroVariance()),
                    fmt(height.finalMicroVariance()),
                    fmt(height.heightVariation()),
                    primarySite != null ? primarySite.id() : -1,
                    pCell, fmt(d1), fmt(primaryHeight != null ? primaryHeight.finalBaseHeight() : Double.NaN),
                    secondarySite != null ? secondarySite.id() : -1,
                    sCell, fmt(d2), fmt(secondaryHeight != null ? secondaryHeight.finalBaseHeight() : Double.NaN)
                );
            }
        }

        if (config.debugLogging() && LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                "[MacroSelector] pos=({}, {}) macroSite={} microSite={} tag={} rare={} edgeMetric={} edgeFactor={} flipped={} transitionOverride={}",
                blockX,
                blockZ,
                activeSite.id(),
                microSiteId,
                macroTag,
                rare,
                edgeMetricStable,
                edgeFactor,
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

    private double computeEdgeFactor(double edgeMetric) {
        if (!Double.isFinite(edgeMetric)) {
            return 1.0d;
        }
        double w = Math.max(1e-6, config.macroBlendWidth());
        double normalized = edgeMetric / w;
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
                "macroSite=%d secondary=%s transitionOverride=%s rule=%s contWorldY=%.2f contSigned=%.3f oldWorldY=%.2f absMin=%.1f absMax=%.1f off=%.3f",
                result.macroSiteId(),
                result.secondarySite() != null ? result.secondarySite().id() : "none",
                result.transitionOverride(),
                result.transitionRuleId() != null ? result.transitionRuleId() : "none",
                result.continuousWorldBaseHeight(),
                result.continuousFinalBaseHeight(),
                result.worldBaseHeight(),
                result.absoluteMinY(),
                result.absoluteMaxY(),
                result.baseHeightOffset()
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
        double t = clamp01(edgeFactor);

        double s = smoothStep(t);
        double base = 1.0d - s;

        double p = 1.35d;// 1.0线性，>1更柔，<1更硬
        return Math.pow(base, p);
    }

    private HeightComputation applyEdgeContinuity(HeightComputation height,
                                                  HeightComputation primaryHeight,
                                                  HeightComputation secondaryHeight,
                                                  double edgeFactor,
                                                  boolean hasSecondary) {
        if (continuitySettings == null || continuitySettings.disabled() || height == null) {
            return height;
        }

        HeightComputation adjusted = height;

        if (!DEBUG_DISABLE_EDGE_CLAMP
            && hasSecondary
            && primaryHeight != null
            && secondaryHeight != null
            && continuitySettings.maxEdgeDelta() > 0.0d) {
            double delta = continuitySettings.maxEdgeDelta();
            double minBase = Math.min(primaryHeight.finalBaseHeight(), secondaryHeight.finalBaseHeight()) - delta;
            double maxBase = Math.max(primaryHeight.finalBaseHeight(), secondaryHeight.finalBaseHeight()) + delta;
            adjusted = adjusted.clampFinalBase(minBase, maxBase);
        }

        double edge = clamp01(edgeFactor);
        double k = Math.max(0.0d, continuitySettings.varianceFalloff());

        double nearEdge = 1.0d - smoothStep(edge);

        double minScale = 0.55d;

        double varianceScale = (1.0d - nearEdge) * 1.0d + nearEdge * minScale;
        varianceScale = Math.pow(MathHelper.clamp_double(varianceScale, 0.0d, 1.0d), Math.max(1.0d, k));

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

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static String fmt(double v) {
        if (!Double.isFinite(v)) return "NaN/Inf";
        return String.format(java.util.Locale.ROOT, "%.4f", v);
    }

    private static double gaussianW(double d, double sigma) {
        double x = d / Math.max(1e-6, sigma);
        return Math.exp(-0.5 * x * x);
    }
}
