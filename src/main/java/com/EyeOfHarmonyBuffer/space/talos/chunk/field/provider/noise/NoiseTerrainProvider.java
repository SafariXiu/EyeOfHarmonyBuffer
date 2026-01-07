package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.TerrainProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.TerrainProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import static com.cleanroommc.modularui.ModularUI.LOGGER;

public final class NoiseTerrainProvider implements TerrainProvider {

    private final TerrainProviderSettings settings;
    private final FractalNoise2D elevationNoise;
    private final FractalNoise2D roughnessNoise;
    private final Long2ObjectLinkedOpenHashMap<TerrainChunkCache> chunkCache = new Long2ObjectLinkedOpenHashMap<>();
    private final int maxCacheEntries = 256;

    public NoiseTerrainProvider(long seed, TerrainProviderSettings settings) {
        this.settings = settings;
        long baseSeed = seed + settings.noiseSeedOffset();
        this.elevationNoise = new FractalNoise2D(
            baseSeed,
            settings.primaryFrequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.octaves()
        );

        this.roughnessNoise = new FractalNoise2D(
            baseSeed ^ 0x5DEECE66DL,
            settings.primaryFrequency() * 2.0,
            settings.lacunarity(),
            settings.persistence(),
            Math.max(1, settings.octaves() - 1)
        );
    }

    @Override
    public TerrainSample sample(int blockX, int blockZ) {
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        long key = chunkKey(chunkX, chunkZ);

        TerrainChunkCache cache = chunkCache.get(key);
        if (cache == null) {
            cache = new TerrainChunkCache();
            chunkCache.put(key, cache);
            ensureCapacity();
        }

        int index = (localZ << 4) | localX;
        TerrainSample cached = cache.samples[index];
        if (cached != null) {
            return cached;
        }

        TerrainSample computed = computeSample(blockX, blockZ);
        cache.samples[index] = computed;
        return computed;
    }

    private TerrainSample computeSample(int blockX, int blockZ) {
        double elevationNorm = elevationNoise.sample(blockX, blockZ);
        double elevation = settings.baseHeight() + elevationNorm * settings.verticalScale();

        double offset = settings.slopeSampleOffsetBlocks();
        double east = elevationNoise.sample(blockX + offset, blockZ);
        double north = elevationNoise.sample(blockX, blockZ + offset);

        double slope = (Math.abs(east - elevationNorm) + Math.abs(north - elevationNorm)) * 0.5;
        double roughness = (roughnessNoise.sample(blockX, blockZ) * 0.5) + 0.5;

        return new TerrainSample(elevation, slope, clamp01(roughness));
    }

    @Override
    public void invalidateCaches() {
        int before = chunkCache.size();
        chunkCache.clear();
        LOGGER.info("[NoiseTerrainProvider] invalidateCaches cleared {} entries", before);
    }

    @Override
    public void dispose() {
        chunkCache.clear();
    }

    private void ensureCapacity() {
        if (chunkCache.size() <= maxCacheEntries) {
            return;
        }
        Long2ObjectMap.Entry<TerrainChunkCache> eldest = chunkCache.long2ObjectEntrySet().iterator().next();
        chunkCache.remove(eldest.getLongKey());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static final class TerrainChunkCache {
        private final TerrainSample[] samples = new TerrainSample[16 * 16];
    }
}
