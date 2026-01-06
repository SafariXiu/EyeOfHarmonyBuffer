package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.ContinentalField;
import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.util.MathHelper;

import java.util.Arrays;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome.*;
import static com.cleanroommc.modularui.ModularUI.LOGGER;

public final class MacroBiomeField implements ContinentalField {

    public final class TalosDebugFlags {
        public static final boolean DEBUG_PLATE_ID = false;
    }

    public static final long MACRO_SALT = 0x9E3779B97F4A7C15L;

    private final double selectorSoftness;
    private final double secondaryOffset;

    private static final int TILE_SIZE = 64;
    private static final int MAX_TILES = 256;
    private static final int WORLD_CELL_SIZE = 32;

    private static final MacroBiome[] BIOME_TABLE = MacroBiome.values();

    private static final long PATCH_SALT = 0xC6BC279692B5C323L;

    private static final LatitudeBand[] LATITUDE_BANDS = {
        new LatitudeBand(0, 0.00, 0.18, new MacroBiome[]{SUBPOLAR}),
        new LatitudeBand(1, 0.18, 0.32, new MacroBiome[]{SUBPOLAR, COOL_FORESTED}),
        new LatitudeBand(2, 0.32, 0.52, new MacroBiome[]{COOL_FORESTED, PLAINS_TEMPERATE, MOUNTAINOUS}),
        new LatitudeBand(3, 0.52, 0.72, new MacroBiome[]{PLAINS_TEMPERATE, WARM_DRY, MOUNTAINOUS}),
        new LatitudeBand(4, 0.72, 0.88, new MacroBiome[]{PLAINS_TEMPERATE, WARM_DRY, LOWLAND_WET, TROPICAL_HUMID}),
        new LatitudeBand(5, 0.88, 1.00, new MacroBiome[]{TROPICAL_HUMID, LOWLAND_WET, WARM_DRY})
    };

    private final MacroBiomeConfig config;
    private final long worldSeed;

    private final SimplexNoiseOctave baseNoiseCoarse;
    private final SimplexNoiseOctave baseNoiseFine;
    private final SimplexNoiseOctave detailNoise;
    private final SimplexNoiseOctave latitudeWarpNoise;

    private final SimplexNoiseOctave plateNoise;
    private final SimplexNoiseOctave plateWarpNoise;

    private final Long2ObjectMap<MacroSample> macroCache = new Long2ObjectOpenHashMap<>();
    private final Object macroCacheLock = new Object();
    private final SimplexNoiseOctave macroBaseNoise;
    private final SimplexNoiseOctave macroBaseShapeNoise;
    private final SimplexNoiseOctave macroOffsetNoise;

    private final double plateNoiseScale;
    private final double plateWarpScale;
    private final double plateWarpStrength;

    private final double macroScale;
    private final double baseCoarseScale;
    private final double detailScale;
    private final double baseCoarseWeight;
    private final double baseFineWeight;

    private final double latitudePeriod;
    private final double latitudeBiasStrength;
    private final double latitudeMixWeight;
    private final double latitudeBaseBias;

    private final double detailBlendWeight;
    private final double baseBlendWeight;

    private final int macroCellSize;
    private final double macroPlateLock;

    private final double latitudeWarpScale;
    private final double latitudeWarpAmplitude;
    private final double latitudeBlendWidth;

    private final int patchGridSize;
    private final int patchesPerCell;
    private final double patchJitter;
    private final double patchSelectorRange;
    private final double patchBlendRadius;
    private final double patchSingleBiomeChance;

    private final SimplexNoiseOctave patchWarpNoiseX;
    private final SimplexNoiseOctave patchWarpNoiseZ;
    private final double patchWarpScale;
    private final double patchWarpStrength;

    private final TileCache tileCache = new TileCache();
    private final ThreadLocal<NoiseSample> scratchSample = ThreadLocal.withInitial(NoiseSample::new);
    private static final ThreadLocal<double[]> scratchWarpVec =
        ThreadLocal.withInitial(() -> new double[2]);
    private static final ThreadLocal<double[]> scratchRotVec =
        ThreadLocal.withInitial(() -> new double[2]);
    private final BandStats[] bandStats = Arrays.stream(LATITUDE_BANDS)
        .map(b -> new BandStats())
        .toArray(BandStats[]::new);

    public MacroBiomeField(long worldSeed, MacroBiomeConfig config) {
        this.worldSeed = worldSeed;
        this.config = (config == null) ? MacroBiomeConfig.bruteForcePreset() : config;

        this.macroScale = this.config.macroScale;
        this.baseCoarseScale = this.macroScale * this.config.coarseScaleFactor;
        this.detailScale = this.macroScale * this.config.detailScaleFactor;

        this.baseCoarseWeight = this.config.coarseWeight;
        this.baseFineWeight = 1.0D - this.baseCoarseWeight;

        this.latitudePeriod = this.config.latitudePeriod;
        this.latitudeBiasStrength = this.config.latitudeBiasStrength;
        this.latitudeMixWeight = this.config.latitudeMixWeight;
        this.latitudeBaseBias = this.config.latitudeBaseBias;

        this.detailBlendWeight = this.config.detailBlendWeight;
        this.baseBlendWeight = this.config.baseBlendWeight;

        this.macroCellSize = this.config.macroCellSize;
        this.macroPlateLock = this.config.macroPlateLock;

        this.latitudeWarpScale = this.config.latitudeWarpScale;
        this.latitudeWarpAmplitude = this.config.latitudeWarpAmplitude;
        this.latitudeBlendWidth = this.config.latitudeBlendWidth;

        this.baseNoiseCoarse = new SimplexNoiseOctave(worldSeed ^ MACRO_SALT, this.config.baseOctaves);
        this.baseNoiseFine = new SimplexNoiseOctave((worldSeed + 0x5DEECE66DL) ^ MACRO_SALT, this.config.baseOctaves);
        this.detailNoise = new SimplexNoiseOctave(worldSeed ^ (MACRO_SALT >>> 1), this.config.detailOctaves);
        this.latitudeWarpNoise = new SimplexNoiseOctave(
            (worldSeed ^ 0x6A09E667F3BCC909L) + 0x9E3779B97F4A7C15L, 1
        );

        this.patchGridSize = this.config.patchGridSize;
        this.patchesPerCell = this.config.patchesPerCell;
        this.patchJitter = this.config.patchJitter;
        this.patchSelectorRange = this.config.patchSelectorRange;
        this.patchBlendRadius = this.config.patchBlendRadius;
        this.patchSingleBiomeChance = this.config.patchSingleBiomeChance;

        this.patchWarpScale = (this.config.patchWarpScale <= 0.0D)
            ? this.macroScale * 0.35D
            : this.config.patchWarpScale;
        this.patchWarpStrength = (this.config.patchWarpStrength <= 0.0D)
            ? this.config.patchGridSize * 0.45D
            : this.config.patchWarpStrength;

        this.patchWarpNoiseX = new SimplexNoiseOctave(worldSeed ^ (PATCH_SALT >>> 1), 2);
        this.patchWarpNoiseZ = new SimplexNoiseOctave((worldSeed + 0xD1342543L) ^ (PATCH_SALT >>> 2), 2);

        this.selectorSoftness = this.config.selectorSoftness;
        this.secondaryOffset = this.config.secondaryOffset;

        this.plateNoiseScale = (this.config.macroPlateLock > 0.0D)
            ? this.macroScale * 0.35D
            : this.macroScale * 0.25D;
        this.plateWarpScale = this.plateNoiseScale * 2.0D;
        this.plateWarpStrength = Math.max(48.0D, this.macroCellSize * 0.02D);

        this.plateNoise = new SimplexNoiseOctave(
            (worldSeed ^ 0xD1B54A32D192ED03L) + MACRO_SALT,
            3
        );
        this.plateWarpNoise = new SimplexNoiseOctave(
            worldSeed ^ 0x94D049BB133111EBL,
            2
        );

        this.macroBaseNoise = new SimplexNoiseOctave(worldSeed ^ 0xA24BAED4963EE407L, 2);
        this.macroBaseShapeNoise = new SimplexNoiseOctave(worldSeed ^ 0x0000A9BCL, 2);
        this.macroOffsetNoise    = new SimplexNoiseOctave(worldSeed ^ 0x0000C37FL, 2);

        System.out.println("[EOHBMacro]:" + "1");
    }

    @Override
    public double continentalBase(int x, int z) {
        return sampleNoise(x, z).base;
    }

    @Override
    public double continentalLatitude01(int x, int z) {
        return sampleNoise(x, z).latitude01;
    }

    public SampleDual sampleDual(int x, int z) {
        NoiseSample ns = sampleNoise(x, z);
        MacroBiome primary = pickPrimary(x, z, ns);
        MacroBiome secondary = pickSecondary(x, z, ns, primary);
        double blend = computeBlend(ns.base, ns.detail);

        return new SampleDual(
            primary,
            secondary,
            blend,
            ns.patchVariant,
            ns.patchSingleBiome,
            ns.patchEdgeBlend
        );
    }

    public MacroSample sampleMacro(int x, int z) {
        return sampleWorldCell(x, z);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0D - 2.0D * t);
    }

    public static final class MacroSample {
        public final MacroBiome primary;
        public final MacroBiome secondary;
        public final double blendPrimary;
        public final byte tier;
        public final byte plateId;
        public final short plateauHeight;
        public final float anchorWeight;
        public final boolean hardEdge;
        public final short macroBaseHeight;

        public final byte patchVariant;
        public final boolean patchSingleBiome;
        public final double patchEdgeBlend;

        public final double wetWeight;
        public final double coldWeight;
        public final double coastWeight;

        private MacroSample(MacroBiome primary,
                            MacroBiome secondary,
                            double blendPrimary,
                            byte tier,
                            byte plateId,
                            short plateauHeight,
                            float anchorWeight,
                            boolean hardEdge,
                            short macroBaseHeight,
                            byte patchVariant,
                            boolean patchSingleBiome,
                            double patchEdgeBlend,
                            double wetWeight,
                            double coldWeight,
                            double coastWeight) {
            this.primary = primary;
            this.secondary = secondary;
            this.blendPrimary = blendPrimary;
            this.tier = tier;
            this.plateId = plateId;
            this.plateauHeight = plateauHeight;
            this.anchorWeight = anchorWeight;
            this.hardEdge = hardEdge;
            this.macroBaseHeight = macroBaseHeight;
            this.patchVariant = patchVariant;
            this.patchSingleBiome = patchSingleBiome;
            this.patchEdgeBlend = patchEdgeBlend;
            this.wetWeight = wetWeight;
            this.coldWeight = coldWeight;
            this.coastWeight = coastWeight;
        }
    }

    private NoiseSample sampleNoise(int gx, int gz) {
        TileCache.Tile tile = tileCache.getOrBuild(gx, gz, this);
        int idx = tile.index(gx, gz);

        NoiseSample ns = scratchSample.get();
        ns.base = tile.base[idx];
        ns.detail = tile.detail[idx];
        ns.latitude01 = tile.latitude01[idx];
        ns.patchSelectorOffset = tile.patchSelectorOffset[idx];
        ns.patchVariant = tile.patchVariant[idx];
        ns.patchEdgeBlend = tile.patchEdgeBlend[idx];
        ns.patchSingleBiome = tile.patchSingleBiome[idx] != 0;
        return ns;
    }

    private MacroBiome pickPrimary(int gx, int gz, NoiseSample ns) {
        LatitudeBlend blend = sampleLatitudeBlend(ns.latitude01);
        int bandIndex = blend.representativeIndex();
        bandStats[bandIndex].observe(ns.base);
        double selector = buildPrimarySelector(gx, gz, ns, blend, bandIndex, true);
        MacroBiome preferred = selectDominantPool(selector, gx, gz, blend, 0L);
        return enforceDominance(gx, gz, ns, preferred);
    }

    private MacroBiome pickSecondary(int gx, int gz, NoiseSample ns, MacroBiome primary) {
        if (ns.patchSingleBiome) {
            return primary;
        }

        LatitudeBlend blend = sampleLatitudeBlend(ns.latitude01);
        int bandIndex = blend.representativeIndex();
        double selector = clamp01(bandStats[bandIndex].normalize(ns.base) + this.secondaryOffset);
        double coast = clamp01(1.0D - smoothstep(0.35D, 0.65D, clamp01(0.5D + ns.base * 0.5D)));
        selector -= coast * 0.06D;
        selector = clamp01(selector + ns.patchSelectorOffset * 0.8D);
        selector = clamp01(selector + selectorJitter(gx * 31, gz * 17, bandIndex) * 0.5D);
        selector = softenSelector(selector);
        double softnessBoost = lerp(0.0D, 0.12D, ns.patchEdgeBlend);
        selector = lerp(selector, 0.5D, softnessBoost);

        MacroBiome candidate = selectDominantPool(selector, gx, gz, blend, 0x5F3564959E3779B9L);
        return (candidate == primary) ? primary : candidate;
    }

    private MacroBiome selectDominantPool(double selector, int gx, int gz, LatitudeBlend blend, long salt) {
        if (!blend.hasUpper()) {
            return selectFromPool(selector, gx, gz, blend.lower, salt);
        }
        double choice = cellHash01(gx, gz, salt ^ 0xB6EAF4B39C54A1B5L);
        if (choice < blend.upperWeight) {
            return selectFromPool(selector, gx, gz, blend.upper, salt);
        }
        return selectFromPool(selector, gx, gz, blend.lower, salt);
    }

    private MacroBiome selectFromPool(double selector, int gx, int gz, LatitudeBand band, long salt) {
        MacroBiome[] pool = band.pool;
        double biased = applyMacroPlateBias(selector, gx, gz, pool.length, salt);
        int idx = selectIndex(biased, pool.length);
        int rotation = poolRotation(gx, gz, band.index, pool.length);
        idx = (idx + rotation) % pool.length;
        return pool[idx];
    }

    private MacroBiome enforceDominance(int gx, int gz, NoiseSample center, MacroBiome candidate) {
        LatitudeBand band = findBand(center.latitude01);
        int latitudeSpan = Math.max(64, (int) Math.round((band.max - band.min) * latitudePeriod * 0.5D));
        int baseStep = Math.max(64, macroCellSize / 4);
        final int step = MathHelper.clamp_int(baseStep, 64, latitudeSpan);
        int agree = 0;
        int total = 0;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                NoiseSample neighborSample = sampleNoise(gx + dx * step, gz + dz * step);
                LatitudeBlend neighborBlend = sampleLatitudeBlend(neighborSample.latitude01);
                int neighborBandIndex = neighborBlend.representativeIndex();
                double neighborSelector = buildPrimarySelector(gx + dx * step, gz + dz * step,
                    neighborSample, neighborBlend, neighborBandIndex, true);
                MacroBiome neighborBiome = selectDominantPool(
                    neighborSelector,
                    gx + dx * step,
                    gz + dz * step,
                    neighborBlend,
                    0L
                );
                total++;
                if (neighborBiome == candidate) {
                    agree++;
                }
            }
        }

        if (agree >= (total * 3) / 4) {
            return candidate;
        }
        return dominantNeighbor(gx, gz, step, candidate);
    }

    private MacroBiome dominantNeighbor(int gx, int gz, int step, MacroBiome fallback) {
        int[] counts = new int[MacroBiome.values().length];
        counts[fallback.ordinal()]++;

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                NoiseSample neighborSample = sampleNoise(gx + dx * step, gz + dz * step);
                LatitudeBlend neighborBlend = sampleLatitudeBlend(neighborSample.latitude01);
                int bandIndex = neighborBlend.representativeIndex();
                double selector = buildPrimarySelector(
                    gx + dx * step,
                    gz + dz * step,
                    neighborSample,
                    neighborBlend,
                    bandIndex,
                    true
                );
                MacroBiome neighborBiome = selectDominantPool(
                    selector,
                    gx + dx * step,
                    gz + dz * step,
                    neighborBlend,
                    0L
                );
                counts[neighborBiome.ordinal()]++;
            }
        }

        int bestIdx = fallback.ordinal();
        int bestCount = counts[bestIdx];
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > bestCount) {
                bestCount = counts[i];
                bestIdx = i;
            }
        }
        return MacroBiome.values()[bestIdx];
    }

    private double applyMacroPlateBias(double selector, int gx, int gz,
                                       int poolLength, long salt) {
        if (macroPlateLock <= 0.0D || poolLength <= 1) {
            return selector;
        }

        double plate = plateHash01(gx, gz, salt ^ 0xA5A5A5A5A5A5A5A5L);
        double bias = (plate - 0.5D) * macroPlateLock;

        double scale = Math.min(1.0D, poolLength / 4.0D);
        selector = clamp01(selector + bias * scale);
        return selector;
    }

    private static LatitudeBand findBand(double latitude01) {
        for (LatitudeBand band : LATITUDE_BANDS) {
            if (latitude01 >= band.min && latitude01 < band.max) {
                return band;
            }
        }
        return LATITUDE_BANDS[LATITUDE_BANDS.length - 1];
    }

    private LatitudeBlend sampleLatitudeBlend(double latitude01) {
        if (latitudeBlendWidth <= 0.0D) {
            return new LatitudeBlend(findBand(latitude01));
        }

        double half = latitudeBlendWidth * 0.5D;
        double lowerLat = clamp01(latitude01 - half);
        double upperLat = clamp01(latitude01 + half);

        LatitudeBand lower = findBand(lowerLat);
        LatitudeBand upper = findBand(upperLat);

        if (lower == upper) {
            return new LatitudeBlend(lower);
        }

        double transitionStart = Math.max(lower.max - half, lowerLat);
        double transitionEnd = Math.min(upper.min + half, upperLat);
        double denom = Math.max(transitionEnd - transitionStart, 1.0e-6D);
        double mix = clamp01((latitude01 - transitionStart) / denom);

        return new LatitudeBlend(lower, upper, mix);
    }

    private void populateTile(TileCache.Tile tile) {
        for (int dz = 0; dz < TILE_SIZE; dz++) {
            int gz = tile.originZ + dz;
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int gx = tile.originX + dx;

                double coarse = baseNoiseCoarse.noise(gx * baseCoarseScale, gz * baseCoarseScale);
                double fine = baseNoiseFine.noise(gx * macroScale, gz * macroScale);
                double blended = coarse * baseCoarseWeight + fine * baseFineWeight;

                double detail = detailNoise.noise(gx * detailScale, gz * detailScale);

                double latitudeTerm = (latitudePeriod != 0.0)
                    ? Math.cos(Math.PI * (gz / latitudePeriod))
                    : 0.0;
                latitudeTerm = clampSigned(latitudeTerm * latitudeBiasStrength + latitudeBaseBias);

                double base = blended * (1.0 - latitudeMixWeight) + latitudeTerm * latitudeMixWeight;
                base = clampSigned(base);

                double latitudeWarp = latitudeWarpNoise.noise(
                    gx * latitudeWarpScale,
                    gz * latitudeWarpScale
                ) * latitudeWarpAmplitude;

                double warpedLatitudeTerm = clampSigned(latitudeTerm + latitudeWarp);
                double warpedLatitude01 = clamp01(0.5 + warpedLatitudeTerm * 0.5);

                PatchSample sample = computePatchSample(gx, gz);

                int idx = dz * TILE_SIZE + dx;
                tile.base[idx] = base;
                tile.detail[idx] = detail;
                tile.latitude01[idx] = warpedLatitude01;
                tile.patchSelectorOffset[idx] = sample.selectorOffset;
                tile.patchVariant[idx] = sample.variant;
                tile.patchEdgeBlend[idx] = sample.edgeBlend;
                tile.patchSingleBiome[idx] = (byte) (sample.singleBiome ? 1 : 0);
            }
        }
    }

    public static final class LatitudeBand {
        public final int index;
        public final double min;
        public final double max;
        public final MacroBiome[] pool;

        public LatitudeBand(int index, double min, double max, MacroBiome[] pool) {
            this.index = index;
            this.min = min;
            this.max = max;
            this.pool = pool;
        }
    }

    private static final class LatitudeBlend {
        final LatitudeBand lower;
        final LatitudeBand upper;
        final double upperWeight;

        LatitudeBlend(LatitudeBand single) {
            this.lower = single;
            this.upper = single;
            this.upperWeight = 0.0D;
        }

        LatitudeBlend(LatitudeBand lower, LatitudeBand upper, double upperWeight) {
            this.lower = lower;
            this.upper = upper;
            this.upperWeight = upperWeight;
        }

        boolean hasUpper() {
            return upperWeight > 1.0e-4D && upper != lower;
        }

        int representativeIndex() {
            if (hasUpper() && upperWeight >= 0.5D) {
                return upper.index;
            }
            return lower.index;
        }
    }

    private static final class NoiseSample {
        double base;
        double detail;
        double latitude01;
        double patchSelectorOffset;
        byte patchVariant;
        double patchEdgeBlend;
        boolean patchSingleBiome;
    }

    private static final class PatchSample {
        static final PatchSample NONE = new PatchSample(0.0D, (byte) 0, 0.0D, false);

        final double selectorOffset;
        final byte variant;
        final double edgeBlend;
        final boolean singleBiome;

        PatchSample(double selectorOffset, byte variant, double edgeBlend, boolean singleBiome) {
            this.selectorOffset = selectorOffset;
            this.variant = variant;
            this.edgeBlend = edgeBlend;
            this.singleBiome = singleBiome;
        }
    }

    private PatchSample computePatchSample(int gx, int gz) {
        if (patchesPerCell <= 0
            || patchGridSize <= 0
            || patchBlendRadius <= 0.0D
            || patchSelectorRange <= 0.0D) {
            return PatchSample.NONE;
        }

        double bestWeight = 0.0D;
        double bestOffset = 0.0D;
        byte bestVariant = 0;
        boolean singleBiome = false;
        double bestEdgeBlend = 0.0D;

        double[] warped = warpPatchCoords(gx, gz);
        double wx = warped[0];
        double wz = warped[1];

        int gridX = Math.floorDiv((int)Math.floor(wx), patchGridSize);
        int gridZ = Math.floorDiv((int)Math.floor(wz), patchGridSize);

        double[] rotated = scratchRotVec.get();

        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cellX = gridX + dx;
                int cellZ = gridZ + dz;

                long cellHashBase = mix64(worldSeed ^ PATCH_SALT
                    ^ (cellX * 0x632BE5ABDCB5A641L)
                    ^ (cellZ * 0x9E3779B185EBCA87L));

                for (int i = 0; i < patchesPerCell; i++) {
                    long hash = mix64(cellHashBase + i * 0x9E3779B97F4A7C15L);

                    double jitterX = (doubleFromHash(hash) * 2.0D - 1.0D) * patchJitter;
                    double jitterZ = (doubleFromHash(hash >>> 1) * 2.0D - 1.0D) * patchJitter;

                    double seedX = (cellX + 0.5D + jitterX) * patchGridSize;
                    double seedZ = (cellZ + 0.5D + jitterZ) * patchGridSize;

                    double dxWorld = (wx - seedX) / patchGridSize;
                    double dzWorld = (wz - seedZ) / patchGridSize;

                    double angle = (doubleFromHash(hash >>> 5) * 2.0D - 1.0D) * Math.PI;
                    rotateLocal(angle, dxWorld, dzWorld, rotated);
                    double dist = Math.hypot(rotated[0], rotated[1]);

                    if (dist > patchBlendRadius) continue;

                    double t = clamp01(dist / patchBlendRadius);
                    double weight = 1.0D - t * t;
                    if (weight <= bestWeight) continue;

                    double offset = (doubleFromHash(hash >>> 2) * 2.0D - 1.0D) * patchSelectorRange;
                    boolean single = doubleFromHash(hash >>> 3) < patchSingleBiomeChance;
                    byte variant = (byte) ((hash >>> 4) & 0x3);

                    bestWeight = weight;
                    bestOffset = offset * weight;
                    bestVariant = variant;
                    singleBiome = single;
                    bestEdgeBlend = clamp01(1.0D - dist / patchBlendRadius);
                }
            }
        }

        if (bestWeight <= 0.0D) {
            return PatchSample.NONE;
        }

        return new PatchSample(bestOffset, bestVariant, bestEdgeBlend, singleBiome);
    }

    private static final class TileCache {
        private final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();
        private final Deque<Long> lru = new ConcurrentLinkedDeque<>();

        private static final class Tile {
            final int originX;
            final int originZ;
            final double[] base;
            final double[] detail;
            final double[] latitude01;
            final double[] patchSelectorOffset;
            final byte[] patchVariant;
            final double[] patchEdgeBlend;
            final byte[] patchSingleBiome;

            Tile(int originX, int originZ) {
                this.originX = originX;
                this.originZ = originZ;
                int size = TILE_SIZE * TILE_SIZE;
                this.base = new double[size];
                this.detail = new double[size];
                this.latitude01 = new double[size];
                this.patchSelectorOffset = new double[size];
                this.patchVariant = new byte[size];
                this.patchEdgeBlend = new double[size];
                this.patchSingleBiome = new byte[size];
            }

            int index(int gx, int gz) {
                int localX = gx - originX;
                int localZ = gz - originZ;
                return (localZ * TILE_SIZE) + localX;
            }
        }

        Tile getOrBuild(int gx, int gz, MacroBiomeField owner) {
            int tileX = Math.floorDiv(gx, TILE_SIZE);
            int tileZ = Math.floorDiv(gz, TILE_SIZE);
            long key = (((long) tileX) << 32) ^ (tileZ & 0xFFFFFFFFL);

            Tile tile = tiles.get(key);
            if (tile != null) {
                touch(key);
                return tile;
            }

            Tile fresh = new Tile(tileX * TILE_SIZE, tileZ * TILE_SIZE);
            owner.populateTile(fresh);
            Tile existing = tiles.putIfAbsent(key, fresh);
            Tile result = (existing == null) ? fresh : existing;

            if (existing == null) {
                touch(key);
                evictIfNeeded();
            }
            return result;
        }

        private void touch(long key) {
            lru.remove(key);
            lru.addFirst(key);
        }

        private void evictIfNeeded() {
            while (tiles.size() > MAX_TILES) {
                Long tail = lru.pollLast();
                if (tail == null) {
                    break;
                }
                tiles.remove(tail);
            }
        }
    }

    private double[] warpPatchCoords(int gx, int gz) {
        double[] out = scratchWarpVec.get();
        double warpX = patchWarpNoiseX.noise(gx * patchWarpScale, gz * patchWarpScale);
        double warpZ = patchWarpNoiseZ.noise(gx * patchWarpScale, gz * patchWarpScale);
        out[0] = gx + warpX * patchWarpStrength;
        out[1] = gz + warpZ * patchWarpStrength;
        return out;
    }

    private static void rotateLocal(double angle, double dx, double dz, double[] out) {
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        out[0] = dx * cos - dz * sin;
        out[1] = dx * sin + dz * cos;
    }

    private static double clamp01(double x) {
        if (x < 0.0D) return 0.0D;
        if (x > 1.0D) return 1.0D;
        return x;
    }

    private static double clampSigned(double v) {
        if (v < -1.0D) return -1.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }

    private double softenSelector(double selector) {
        double width = this.selectorSoftness;
        if (width <= 0.0D) {
            return selector;
        }
        return selector * selector * (3.0D - 2.0D * selector);
    }

    private static int selectIndex(double selector, int poolLength) {
        int idx = (int) Math.floor(selector * poolLength);
        if (idx < 0) return 0;
        if (idx >= poolLength) return poolLength - 1;
        return idx;
    }

    private double computeBlend(double base, double detail) {
        double blend = 0.5D + detail * detailBlendWeight;
        blend += base * baseBlendWeight;
        return clamp01(blend);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static double doubleFromHash(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    private double cellHash01(int gx, int gz, long salt) {
        int cellSize = macroCellSize * 2;
        int cellX = Math.floorDiv(gx, cellSize);
        int cellZ = Math.floorDiv(gz, cellSize);
        long hash = mix64(worldSeed ^ salt
            ^ (cellX * 0x632BE5ABDCB5A641L)
            ^ (cellZ * 0x9E3779B185EBCA87L));
        return doubleFromHash(hash);
    }

    private double plateHash01(int gx, int gz, long salt) {
        double plateNoise01 = samplePlateField(gx, gz);

        final int coarseSize = Math.max(512, macroCellSize);
        int cellX = Math.floorDiv(gx + 37, coarseSize);
        int cellZ = Math.floorDiv(gz - 91, coarseSize);

        long hash = mix64(worldSeed ^ salt
            ^ (cellX * 0x632BE5ABDCB5A641L)
            ^ (cellZ * 0x9E3779B185EBCA87L));

        double coarseJitter = (doubleFromHash(hash) * 2.0D - 1.0D) * 0.08D;

        return clamp01(plateNoise01 + coarseJitter);
    }

    private double samplePlateField(int gx, int gz) {
        double warp = plateWarpNoise.noise(gx * plateWarpScale, gz * plateWarpScale) * plateWarpStrength;
        double px = gx + warp;
        double pz = gz - warp * 0.37D;

        double primary = plateNoise.noise(px * plateNoiseScale, pz * plateNoiseScale);
        double secondary = baseNoiseFine.noise(px * plateNoiseScale * 1.9D, pz * plateNoiseScale * 1.9D) * 0.2D;
        double combined = primary * 0.8D + secondary;

        return clamp01(0.5D + combined * 0.5D);
    }

    public static final class SampleDual {
        public final MacroBiome primary;
        public final MacroBiome secondary;
        public final double primaryWeight;
        public final byte patchVariant;
        public final boolean singleBiome;
        public final double patchEdgeBlend;

        private SampleDual(MacroBiome primary,
                           MacroBiome secondary,
                           double primaryWeight,
                           byte patchVariant,
                           boolean singleBiome,
                           double patchEdgeBlend) {
            this.primary = primary;
            this.secondary = secondary;
            this.primaryWeight = primaryWeight;
            this.patchVariant = patchVariant;
            this.singleBiome = singleBiome;
            this.patchEdgeBlend = patchEdgeBlend;
        }
    }

    private static byte computeMacroTier(MacroBiome biome) {
        double mid = (biome.height.absoluteMin + biome.height.absoluteMax) * 0.5;
        if (mid < 70.0) return 0;
        if (mid < 85.0) return 1;
        if (mid < 105.0) return 2;
        return 3;
    }

    private short computePlateauHeight(int gx, int gz, MacroBiome biome, NoiseSample ns) {
        MacroBiome.MacroHeightProfile hp = biome.height;
        double span = hp.absoluteMax - hp.absoluteMin;

        double basis = hp.absoluteMin + span * clamp01(0.5D + ns.base * 0.5D);

        double micro = ns.detail * hp.heightVariation * span;

        double offset = hp.baseHeightOffset * 48.0D;

        double plateau = basis + micro + offset;
        plateau = MathHelper.clamp_double(plateau, hp.absoluteMin, hp.absoluteMax);

        return (short) Math.round(plateau);
    }

    private static final class BandStats {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;

        void observe(double value) {
            if (value < min) min = value;
            if (value > max) max = value;
        }

        double normalize(double value) {
            if (max <= min) return 0.5D;
            double n = (value - min) / (max - min);
            return clamp01(n);
        }
    }

    private double buildPrimarySelector(int gx, int gz,
                                        NoiseSample ns,
                                        LatitudeBlend blend,
                                        int bandIndex,
                                        boolean applyJitter) {

        double warp = detailNoise.noise(gx * (macroScale * 0.35D), gz * (macroScale * 0.35D)) * 25.0D;
        double wx = gx + warp;
        double wz = gz + warp * 0.5D;

        double coarse = baseNoiseCoarse.noise(wx * (macroScale * config.coarseScaleFactor),
            wz * (macroScale * config.coarseScaleFactor));
        double fine   = baseNoiseFine.noise(wx * (macroScale * 1.6D),
            wz * (macroScale * 1.6D)) * 0.35D;

        double selector = coarse * 0.75D + fine * 0.25D;
        selector += ns.base * 0.20D;

        if (applyJitter) {
            selector += selectorJitter(gx, gz, bandIndex);
        }

        selector = clamp01(0.5D + selector * 0.5D);
        selector = softenSelector(selector);
        return selector;
    }

    private double selectorJitter(int gx, int gz, int bandIndex) {
        long hash = mix64(worldSeed
            ^ 0x4CF5AD432745937FL
            ^ (((long) gx) << 32)
            ^ (gz & 0xFFFFFFFFL)
            ^ ((long) bandIndex * 0x9E3779B97F4A7C15L));
        return (doubleFromHash(hash) * 2.0D - 1.0D) * 0.02D;
    }

    private int poolRotation(int gx, int gz, int bandIndex, int length) {
        if (length <= 1) {
            return 0;
        }
        long hash = mix64(worldSeed
            ^ 0xBB67AE8584CAA73BL
            ^ ((long) bandIndex * 0x94D049BB133111EBL)
            ^ (((long) (gx >> 10)) << 32)
            ^ (gz >> 10));
        int rotation = (int) (hash & 0x7FFFFFFFL);
        return rotation % length;
    }

    private byte encodePlateId(int gx, int gz) {
        double plate = plateHash01(gx, gz, 0x3FBCF9A97E65L);
        return (byte) MathHelper.clamp_int((int) Math.round(plate * 255.0), 0, 255);
    }

    public MacroSample sampleWorldCell(int gx, int gz) {
        long key = (((long)gx) << 32) ^ (gz & 0xFFFFFFFFL);
        MacroSample cached = macroCache.get(key);
        if (cached != null) return cached;

        MacroSample generated;
        synchronized (macroCacheLock) {
            cached = macroCache.get(key);
            if (cached != null) return cached;
            generated = buildWorldSampleAt(gx, gz);
            macroCache.put(key, generated);
        }
        return generated;
    }

    private MacroSample buildWorldSampleAt(int gx, int gz) {
        NoiseSample ns = sampleNoise(gx, gz);

        MacroBiome primary = pickPrimary(gx, gz, ns);
        MacroBiome secondary = pickSecondary(gx, gz, ns, primary);
        double blend = computeBlend(ns.base, ns.detail);

        short plateauHeight = computePlateauHeight(gx, gz, primary, ns);
        short macroBaseHeight = computeMacroBaseHeight(gx, gz, primary, secondary, blend);

        byte tier = computeMacroTier(primary);
        byte plateId = encodePlateId(gx, gz);
        float anchorWeight = primary.getPlateauAnchorWeight();
        boolean hardEdge = primary.isHardEdge();

        double wetWeight = clamp01(0.5 + ns.base * 0.35 + ns.detail * 0.15);
        double coldWeight = clamp01(1.0 - ns.latitude01);
        double coastWeight = computeCoastWeight(ns.base);

        if ((gx & 31) == 31 && (gz & 31) == 0) {
            LOGGER.debug("[MacroSample] gx={},gz={} baseHeight={} plateau={} offset={} variation={} prim={} sec={} blend={}",
                gx, gz,
                macroBaseHeight,
                plateauHeight,
                primary.height.baseHeightOffset,
                primary.height.heightVariation,
                primary.id,
                secondary.id,
                blend
            );
        }

        return new MacroSample(
            primary,
            secondary,
            blend,
            tier,
            plateId,
            plateauHeight,
            anchorWeight,
            hardEdge,
            macroBaseHeight,
            ns.patchVariant,
            ns.patchSingleBiome,
            ns.patchEdgeBlend,
            wetWeight,
            coldWeight,
            coastWeight
        );
    }

    private short computeMacroBaseHeight(int gx, int gz,
                                         MacroBiome primary,
                                         MacroBiome secondary,
                                         double blend) {
        MacroBiome.MacroHeightProfile hpA = primary.height;
        MacroBiome.MacroHeightProfile hpB = secondary.height;

        double t = clamp01(blend);

        // ① 独立的宏高度噪声，避免直接用 t
        double macroNoise = clamp01(0.5 + macroBaseShapeNoise.noise(gx * 0.0006, gz * 0.0006) * 0.5);
        double macroA = lerp(hpA.absoluteMin, hpA.absoluteMax, macroNoise);
        double macroB = lerp(hpB.absoluteMin, hpB.absoluteMax, macroNoise);
        double macro = lerp(macroA, macroB, t);

        // ② 平滑 plateau 与 offset
        double offset = lerp(hpA.baseHeightOffset, hpB.baseHeightOffset, t);
        double offsetNoise = clamp01(0.5 + macroOffsetNoise.noise(gx * 0.0004, gz * 0.0004) * 0.5);
        double plateau = offset * 32.0 * offsetNoise;

        double variation = lerp(hpA.heightVariation, hpB.heightVariation, t);
        double micro = macroBaseNoise.noise(gx * 0.0008, gz * 0.0008) * variation * 3.0;

        double minH = Math.min(hpA.absoluteMin, hpB.absoluteMin) - 8.0;
        double maxH = Math.max(hpA.absoluteMax, hpB.absoluteMax) + 8.0;
        double height = clamp(macro + plateau + micro, minH, maxH);
        return (short) Math.round(height);
    }

    private static double computeCoastWeight(double base) {
        double continental = clamp01(0.5D + base * 0.5D);
        return 1.0D - smoothstep(0.35D, 0.65D, continental);
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public MacroBiomeConfig getConfig() {
        return this.config;
    }

    public static final class MacroBiomeConfig {
        public final double macroScale;
        public final double coarseScaleFactor;
        public final double detailScaleFactor;
        public final double coarseWeight;

        public final double latitudePeriod;
        public final double latitudeBiasStrength;
        public final double latitudeMixWeight;
        public final double latitudeBaseBias;

        public final double detailBlendWeight;
        public final double baseBlendWeight;

        public final int baseOctaves;
        public final int detailOctaves;

        public final int macroCellSize;
        public final double macroPlateLock;

        public final double latitudeWarpScale;
        public final double latitudeWarpAmplitude;
        public final double latitudeBlendWidth;

        public final int patchGridSize;
        public final int patchesPerCell;
        public final double patchJitter;
        public final double patchSelectorRange;
        public final double patchBlendRadius;
        public final double patchSingleBiomeChance;

        public final double patchWarpScale;
        public final double patchWarpStrength;

        public final double selectorSoftness;
        public final double secondaryOffset;

        public final double baselineBlurRadius;
        public final int baselineIterations;
        public final double baselineClampMax;
        public final double clampSlopeFactor;
        public final double rampThreshold;
        public final int rampWidth;

        private MacroBiomeConfig(double macroScale,
                                 double coarseScaleFactor,
                                 double detailScaleFactor,
                                 double coarseWeight,
                                 double latitudePeriod,
                                 double latitudeBiasStrength,
                                 double latitudeMixWeight,
                                 double latitudeBaseBias,
                                 double detailBlendWeight,
                                 double baseBlendWeight,
                                 int baseOctaves,
                                 int detailOctaves,
                                 int macroCellSize,
                                 double macroPlateLock,
                                 double latitudeWarpScale,
                                 double latitudeWarpAmplitude,
                                 double latitudeBlendWidth,
                                 int patchGridSize,
                                 int patchesPerCell,
                                 double patchJitter,
                                 double patchSelectorRange,
                                 double patchBlendRadius,
                                 double patchSingleBiomeChance,
                                 double patchWarpScale,
                                 double patchWarpStrength,
                                 double selectorSoftness,
                                 double secondaryOffset,
                                 double baselineBlurRadius,
                                 int baselineIterations,
                                 double baselineClampMax,
                                 double clampSlopeFactor,
                                 double rampThreshold,
                                 int rampWidth) {
            this.macroScale = macroScale;
            this.coarseScaleFactor = coarseScaleFactor;
            this.detailScaleFactor = detailScaleFactor;
            this.coarseWeight = coarseWeight;
            this.latitudePeriod = latitudePeriod;
            this.latitudeBiasStrength = latitudeBiasStrength;
            this.latitudeMixWeight = latitudeMixWeight;
            this.latitudeBaseBias = latitudeBaseBias;
            this.detailBlendWeight = detailBlendWeight;
            this.baseBlendWeight = baseBlendWeight;
            this.baseOctaves = baseOctaves;
            this.detailOctaves = detailOctaves;
            this.macroCellSize = macroCellSize;
            this.macroPlateLock = macroPlateLock;
            this.latitudeWarpScale = latitudeWarpScale;
            this.latitudeWarpAmplitude = latitudeWarpAmplitude;
            this.latitudeBlendWidth = latitudeBlendWidth;
            this.patchGridSize = patchGridSize;
            this.patchesPerCell = patchesPerCell;
            this.patchJitter = patchJitter;
            this.patchSelectorRange = patchSelectorRange;
            this.patchBlendRadius = patchBlendRadius;
            this.patchSingleBiomeChance = patchSingleBiomeChance;
            this.patchWarpScale = patchWarpScale;
            this.patchWarpStrength = patchWarpStrength;
            this.selectorSoftness = selectorSoftness;
            this.secondaryOffset = secondaryOffset;
            this.baselineBlurRadius = baselineBlurRadius;
            this.baselineIterations = baselineIterations;
            this.baselineClampMax = baselineClampMax;
            this.clampSlopeFactor = clampSlopeFactor;
            this.rampThreshold = rampThreshold;
            this.rampWidth = rampWidth;
        }

        public static MacroBiomeConfig bruteForcePreset() {
            return PRESET_BRUTE;
        }

        public static MacroBiomeConfig preset(String name) {
            return PRESET_BRUTE;
        }

        private static final MacroBiomeConfig PRESET_BRUTE = new MacroBiomeConfig(
            1.0D / 7000.0D,
            0.45D,
            2.8D,
            0.65D,
            48000.0D,
            0.40D,
            0.20D,
            0.00D,
            0.12D,
            0.16D,
            1,
            1,
            3072,
            0.0D,
            1.0D / 90000.0D,
            0.12D,
            0.20D,
            2048,
            2,
            0.35D,
            0.08D,
            0.30D,
            0.15D,
            0.0D,
            0.0D,
            1.0D,
            0.05D,
            9.5D,
            4,
            24.0D,
            0.45D,
            12.0D,
            14
        );
    }
}
