package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.coastline.CoastlineSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.HydroProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.HydroProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.Objects;

import static com.cleanroommc.modularui.ModularUI.LOGGER;

public final class NoiseHydroProvider implements HydroProvider {

    private static final double WORLD_HEIGHT = 256.0d;
    private static final double DEFAULT_LACUNARITY = 2.0d;
    private static final double DEFAULT_PERSISTENCE = 0.5d;
    private static final int DEFAULT_OCTAVES = 4;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final HydroProviderSettings settings;
    private final CoastlineProvider coastlineProvider;

    private final FractalNoise2D saturationNoise;
    private final FractalNoise2D flowNoise;
    private final FractalNoise2D riverNoise;
    private final FractalNoise2D riverDetailNoise;
    private final FractalNoise2D lakeNoise;
    private final Long2ObjectLinkedOpenHashMap<HydroChunkCache> chunkCache = new Long2ObjectLinkedOpenHashMap<>();

    public NoiseHydroProvider(long seed,
                              HydroProviderSettings settings,
                              CoastlineProvider coastlineProvider) {

        this.settings = Objects.requireNonNull(settings, "settings");
        this.coastlineProvider = Objects.requireNonNull(coastlineProvider, "coastlineProvider");

        HydroProviderSettings.GroundwaterSettings gw = settings.groundwater();
        this.saturationNoise = createNoise(
            seed ^ 0x4E39D6B17C1EL,
            gw.saturationNoise()
        );
        this.flowNoise = createNoise(
            seed ^ 0x62D9C279A4FBL,
            gw.flowNoise()
        );

        HydroProviderSettings.RiverSettings river = settings.river();
        this.riverNoise = new FractalNoise2D(
            seed ^ 0x9E3779B97F4A7C15L ^ river.seedOffset(),
            river.frequency(),
            DEFAULT_LACUNARITY,
            DEFAULT_PERSISTENCE,
            DEFAULT_OCTAVES
        );
        this.riverDetailNoise = new FractalNoise2D(
            seed ^ 0xB5297A4D9F4A7C15L ^ river.seedOffset(),
            river.detailFrequency(),
            DEFAULT_LACUNARITY,
            DEFAULT_PERSISTENCE,
            DEFAULT_OCTAVES
        );

        double lakeFrequency = Math.max(0.0008d, river.detailFrequency() * 0.5d);
        HydroProviderSettings.LakeSettings lake = settings.lake();
        this.lakeNoise = new FractalNoise2D(
            seed ^ 0x1F123BB59E3779B9L ^ lake.seedOffset(),
            lakeFrequency,
            DEFAULT_LACUNARITY,
            DEFAULT_PERSISTENCE,
            DEFAULT_OCTAVES
        );
    }

    @Override
    public HydroSample sample(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        long key = chunkKey(chunkX, chunkZ);

        HydroChunkCache cache = chunkCache.get(key);
        if (cache == null) {
            cache = new HydroChunkCache();
            chunkCache.put(key, cache);
            ensureCapacity();
        }

        int index = (localZ << 4) | localX;
        HydroSample cached = cache.samples[index];
        if (cached != null) {
            return cached;
        }

        HydroSample computed = computeSample(blockX, blockZ);
        cache.samples[index] = computed;
        return computed;
    }

    private HydroSample computeSample(int blockX, int blockZ) {
        GroundwaterResult groundwater = sampleGroundwater(blockX, blockZ);
        RiverMetrics river = sampleRiver(blockX, blockZ);
        double lakeFactor = sampleLake(blockX, blockZ);

        CoastlineContext coastline = settings.coast().syncWithMacro()
            ? sampleCoastline(blockX, blockZ)
            : CoastlineContext.inlandFallback();

        double coastInfluence = coastline.influence(settings.coast().falloff());
        double waterLevel = lerp(groundwater.aquiferLevelBlocks(), settings.seaLevelBlocks(), coastInfluence);

        boolean inlandSea = lakeFactor > 0.75d
            && coastline.distance() > (coastline.beachWidth() + coastline.shelfWidth());

        return new HydroSample(
            groundwater.saturation(),
            groundwater.flowRate(),
            groundwater.aquiferLevelBlocks(),
            river.strength(),
            lakeFactor,
            waterLevel,
            coastline.distance(),
            river.distance(),
            inlandSea
        );
    }

    @Override
    public void invalidateCaches() {
        int before = chunkCache.size();
        chunkCache.clear();
        LOGGER.info("[NoiseHydroProvider] invalidateCaches cleared {} entries", before);
    }

    @Override
    public void dispose() {
        chunkCache.clear();
    }

    private GroundwaterResult sampleGroundwater(int blockX, int blockZ) {
        HydroProviderSettings.GroundwaterSettings gw = settings.groundwater();

        double saturationNoiseValue = saturationNoise.sample(blockX, blockZ);
        double saturation = clamp01(gw.waterTableLevel() +
            gw.saturationVariance() * saturationNoiseValue);

        double aquiferLevel = clamp01(gw.waterTableLevel() +
            gw.aquiferVariance() * saturationNoiseValue);
        double aquiferLevelBlocks = aquiferLevel * WORLD_HEIGHT;

        double flowNoiseValue = flowNoise.sample(blockX, blockZ);
        double flowRate = clamp01(
            gw.maxFlowRate() * (0.5d + 0.5d * flowNoiseValue)
        );

        return new GroundwaterResult(saturation, flowRate, aquiferLevelBlocks);
    }

    private RiverMetrics sampleRiver(int blockX, int blockZ) {
        HydroProviderSettings.RiverSettings river = settings.river();

        double base = 1.0d - Math.abs(riverNoise.sample(blockX, blockZ));
        double detail = 1.0d - Math.abs(riverDetailNoise.sample(blockX, blockZ));
        double composite = clamp01(base * 0.8d + detail * 0.2d);

        double threshold = clamp01(river.threshold());
        double raw = smoothstep(threshold, 1.0d, composite);
        double strength = clamp01(raw * river.strength());

        if (river.smoothRadius() > 0) {
            double exponent = 1.0d / (1.0d + river.smoothRadius());
            strength = Math.pow(strength, exponent);
        }

        double riverDistance = 1.0d - strength;
        return new RiverMetrics(strength, riverDistance);
    }

    private double sampleLake(int blockX, int blockZ) {
        if (!settings.lake().enabled()) {
            return 0.0d;
        }
        double noise = 0.5d * (lakeNoise.sample(blockX, blockZ) + 1.0d);
        return smoothstep(settings.lake().threshold(), 1.0d, noise);
    }

    private CoastlineContext sampleCoastline(int blockX, int blockZ) {
        CoastlineSample sample = coastlineProvider.sample(blockX, blockZ, MacroTag.PLAINS);
        return new CoastlineContext(
            sample.isLand(),
            sample.distanceToCoast(),
            sample.beachWidth(),
            sample.shelfWidth()
        );
    }

    private void ensureCapacity() {
        if (chunkCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        Long2ObjectMap.Entry<HydroChunkCache> eldest = chunkCache.long2ObjectEntrySet().iterator().next();
        chunkCache.remove(eldest.getLongKey());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static FractalNoise2D createNoise(long seed,
                                              HydroProviderSettings.NoiseSettings settings) {
        return new FractalNoise2D(
            seed,
            settings.frequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0d - 2.0d * t);
    }

    @Desugar
    private record GroundwaterResult(double saturation,
                                     double flowRate,
                                     double aquiferLevelBlocks) {}

    @Desugar
    private record RiverMetrics(double strength, double distance) {}

    private static final class CoastlineContext {
        private final boolean land;
        private final int distance;
        private final int beachWidth;
        private final int shelfWidth;

        CoastlineContext(boolean land, int distance, int beachWidth, int shelfWidth) {
            this.land = land;
            this.distance = distance;
            this.beachWidth = beachWidth;
            this.shelfWidth = shelfWidth;
        }

        double distance() {
            return distance;
        }

        int beachWidth() {
            return beachWidth;
        }

        int shelfWidth() {
            return shelfWidth;
        }

        double influence(double falloff) {
            if (!land) {
                return 1.0d;
            }
            double normalized = 1.0d - (distance / Math.max(1.0d, falloff));
            return clamp01(normalized);
        }

        static CoastlineContext inlandFallback() {
            return new CoastlineContext(true, 10_000, 0, 0);
        }
    }

    private static final class HydroChunkCache {
        private final HydroSample[] samples = new HydroSample[16 * 16];
    }
}
