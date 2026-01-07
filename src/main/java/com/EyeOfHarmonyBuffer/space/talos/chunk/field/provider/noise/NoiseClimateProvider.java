package com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.noise;

import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.ClimateProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.settings.ClimateProviderSettings;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.FractalNoise2D;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;

import java.util.Objects;
import java.util.function.LongSupplier;

import static com.cleanroommc.modularui.ModularUI.LOGGER;

public final class NoiseClimateProvider implements ClimateProvider {

    private static final double TWO_PI = Math.PI * 2.0d;
    private static final long CACHE_TTL_TICKS = 1200L;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final ClimateProviderSettings settings;
    private final FractalNoise2D temperatureNoise;
    private final FractalNoise2D humidityNoise;
    private final FractalNoise2D rainfallNoise;
    private final LongSupplier timeSupplier;
    private final Long2ObjectLinkedOpenHashMap<ClimateChunkCache> chunkCache = new Long2ObjectLinkedOpenHashMap<>();

    private long lastCacheTick = Long.MIN_VALUE;

    public NoiseClimateProvider(long seed,
                                ClimateProviderSettings settings,
                                LongSupplier timeSupplier) {

        this.settings = Objects.requireNonNull(settings, "settings");
        this.timeSupplier = timeSupplier != null ? timeSupplier : () -> 0L;

        this.temperatureNoise = new FractalNoise2D(
            seed ^ 0x9E3779B97F4A7C15L ^ settings.temperature().seedOffset(),
            settings.temperature().frequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.temperature().octaves()
        );

        this.humidityNoise = new FractalNoise2D(
            seed ^ 0xB5297A4DL ^ settings.humidity().seedOffset(),
            settings.humidity().frequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.humidity().octaves()
        );

        this.rainfallNoise = new FractalNoise2D(
            seed ^ 0x1F123BB5L ^ settings.rainfall().seedOffset(),
            settings.rainfall().frequency(),
            settings.lacunarity(),
            settings.persistence(),
            settings.rainfall().octaves()
        );
    }

    @Override
    public ClimateSample sample(int blockX, int blockZ) {
        long now = timeSupplier.getAsLong();
        if (shouldExpireCache(now)) {
            chunkCache.clear();
            lastCacheTick = now;
        }

        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int localX = blockX & 15;
        int localZ = blockZ & 15;
        long key = chunkKey(chunkX, chunkZ);

        ClimateChunkCache cache = chunkCache.get(key);
        if (cache == null) {
            cache = new ClimateChunkCache();
            chunkCache.put(key, cache);
            ensureCapacity();
        }

        int index = (localZ << 4) | localX;
        ClimateSample cached = cache.samples[index];
        if (cached != null) {
            return cached;
        }

        ClimateSample computed = computeSample(blockX, blockZ, now);
        cache.samples[index] = computed;
        return computed;
    }

    private ClimateSample computeSample(int blockX, int blockZ, long nowTicks) {
        double temperatureRaw = temperatureNoise.sample(blockX, blockZ);
        double humidityRaw = humidityNoise.sample(blockX, blockZ);
        double rainfallRaw = rainfallNoise.sample(blockX, blockZ);

        double seasonPhase = computeSeasonPhase(nowTicks);
        double seasonFactor = computeSeasonFactor(seasonPhase);

        double temperature = clamp01(
            settings.temperature().base()
                + settings.temperature().variance() * temperatureRaw
                + seasonFactor * settings.season().temperatureAmplitude()
        );

        double humidity = clamp01(
            settings.humidity().base()
                + settings.humidity().variance() * humidityRaw
                + seasonFactor * settings.season().humidityAmplitude()
        );

        double rainfall = clamp01(
            settings.rainfall().base()
                + settings.rainfall().variance() * rainfallRaw
                + seasonFactor * settings.season().rainfallAmplitude()
        );

        double windDirection = 0.0d;
        double windSpeed = 0.0d;

        if (settings.wind().enabled()) {
            double directionNorm = wrap01(0.5d + temperatureRaw * 0.5d * settings.wind().directionVariance());
            windDirection = directionNorm * TWO_PI;

            double speedRaw = settings.wind().speedBase()
                + settings.wind().speedVariance() * humidityRaw;
            windSpeed = clamp01(speedRaw);
        }

        return new ClimateSample(
            temperature,
            humidity,
            rainfall,
            windDirection,
            windSpeed,
            seasonPhase
        );
    }

    private double computeSeasonPhase(long nowTicks) {
        if (!settings.season().enabled()) {
            return 0.0d;
        }
        double cycleTicks = settings.season().cycleTicks();
        if (cycleTicks <= 0.0d) {
            return settings.season().phaseOffset();
        }
        double time = Math.floorMod(nowTicks, (long) Math.ceil(cycleTicks));
        double phase = (time / cycleTicks) + settings.season().phaseOffset();
        phase -= Math.floor(phase);
        return phase;
    }

    private double computeSeasonFactor(double seasonPhase) {
        return settings.season().enabled()
            ? Math.sin(seasonPhase * TWO_PI)
            : 0.0d;
    }

    private boolean shouldExpireCache(long now) {
        if (lastCacheTick == Long.MIN_VALUE) {
            return true;
        }
        if (now < lastCacheTick) {
            return true;
        }
        return now - lastCacheTick >= CACHE_TTL_TICKS;
    }

    private void ensureCapacity() {
        if (chunkCache.size() <= MAX_CACHE_ENTRIES) {
            return;
        }
        Long2ObjectMap.Entry<ClimateChunkCache> eldest = chunkCache.long2ObjectEntrySet().iterator().next();
        chunkCache.remove(eldest.getLongKey());
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static double clamp01(double value) {
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static double wrap01(double value) {
        double wrapped = value % 1.0d;
        return wrapped < 0.0d ? wrapped + 1.0d : wrapped;
    }

    @Override
    public void invalidateCaches() {
        int before = chunkCache.size();
        chunkCache.clear();
        lastCacheTick = Long.MIN_VALUE;

        LOGGER.info("[NoiseClimateProvider] invalidateCaches cleared {} entries", before);
    }

    @Override
    public void dispose() {
        chunkCache.clear();
        lastCacheTick = Long.MIN_VALUE;
    }

    private static final class ClimateChunkCache {
        private final ClimateSample[] samples = new ClimateSample[16 * 16];
    }
}
