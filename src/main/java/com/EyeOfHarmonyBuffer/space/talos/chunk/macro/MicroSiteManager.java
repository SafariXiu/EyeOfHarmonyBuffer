package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.util.MathHelper;

import java.util.List;
import java.util.Objects;

public final class MicroSiteManager {

    private final FieldManager fieldManager;
    private final MacroSelectorConfig config;
    private final long worldSeed;

    private final int microGridSize;
    private final long microSiteSalt;
    private final int neighborRadius = 1;
    private final int maxCacheEntries;

    private final Long2ObjectLinkedOpenHashMap<MicroSite> cache = new Long2ObjectLinkedOpenHashMap<>();

    public MicroSiteManager(FieldManager fieldManager,
                            MacroSelectorConfig config,
                            long worldSeed) {
        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.config = Objects.requireNonNull(config, "config");
        this.worldSeed = worldSeed;

        this.microGridSize = config.microGridSize();
        this.microSiteSalt = config.microSiteSalt();
        this.maxCacheEntries = Math.max(64, config.macroCacheMaxEntries());
    }

    public MicroSite resolve(MacroSite macroSite,
                             MacroBiome macroBiome,
                             int blockX,
                             int blockZ) {

        if (macroSite == null || macroBiome == null) {
            return null;
        }

        int cellX = floorDiv(blockX, microGridSize);
        int cellZ = floorDiv(blockZ, microGridSize);

        MicroSite closest = null;
        double bestDistSq = Double.POSITIVE_INFINITY;

        for (int dz = -neighborRadius; dz <= neighborRadius; dz++) {
            for (int dx = -neighborRadius; dx <= neighborRadius; dx++) {
                int nx = cellX + dx;
                int nz = cellZ + dz;
                MicroSite site = resolveSite(macroSite, macroBiome, nx, nz);
                double distSq = distanceSq(blockX, blockZ, site.centerX(), site.centerZ());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    closest = site;
                }
            }
        }

        return closest;
    }

    private MicroSite resolveSite(MacroSite macroSite,
                                  MacroBiome macroBiome,
                                  int cellX,
                                  int cellZ) {

        long key = pack(macroSite.id(), cellX, cellZ);
        MicroSite cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        MicroSite generated = generateSite(macroSite, macroBiome, cellX, cellZ);
        cache.putAndMoveToFirst(key, generated);
        trimCache();
        return generated;
    }

    private MicroSite generateSite(MacroSite macroSite,
                                   MacroBiome macroBiome,
                                   int cellX,
                                   int cellZ) {

        int originX = cellX * microGridSize;
        int originZ = cellZ * microGridSize;
        double halfGrid = microGridSize * 0.5d;

        long macroLow = macroSite.id() & 0xFFFFFFFFL;
        long macroHigh = (macroSite.id() >>> 32) & 0xFFFFFFFFL;

        long hashX = NoiseUtil.mix(
            worldSeed ^ macroLow,
            cellX,
            cellZ,
            microSiteSalt ^ 0x1234BADEL
        );
        long hashZ = NoiseUtil.mix(
            worldSeed ^ macroHigh,
            cellX,
            cellZ,
            microSiteSalt ^ 0x9F13577DL
        );

        double offsetX = (NoiseUtil.hashToUnit(hashX) - 0.5d) * 2.0d * halfGrid;
        double offsetZ = (NoiseUtil.hashToUnit(hashZ) - 0.5d) * 2.0d * halfGrid;

        int centerX = (int) Math.round(originX + halfGrid + offsetX);
        int centerZ = (int) Math.round(originZ + halfGrid + offsetZ);

        ClimateSample climate = Objects.requireNonNull(
            fieldManager.sampleClimate(centerX, centerZ),
            "ClimateSample"
        );

        double humidity = MathHelper.clamp_float((float) climate.humidity(), 0.0f, 1.0f);
        double temperature = MathHelper.clamp_float((float) climate.temperature(), 0.0f, 1.0f);

        MacroBiome.MacroBiomeVariant variant = pickVariant(
            macroBiome,
            macroSite.id(),
            centerX,
            centerZ
        );
        int variantIndex = MicroSite.safeVariantIndex(macroBiome, variant);

        long id = NoiseUtil.mix(
            worldSeed ^ macroLow ^ macroHigh,
            cellX,
            cellZ,
            microSiteSalt
        );

        return new MicroSite(
            id,
            macroSite.id(),
            cellX,
            cellZ,
            centerX,
            centerZ,
            variantIndex,
            variant,
            humidity,
            temperature
        );
    }

    private MacroBiome.MacroBiomeVariant pickVariant(MacroBiome macroBiome,
                                                     long macroSiteId,
                                                     int centerX,
                                                     int centerZ) {

        List<MacroBiome.MacroBiomeVariant> variants = macroBiome.variants;
        if (variants == null || variants.isEmpty()) {
            return null;
        }
        long salt = config.baseSalt()
            ^ microSiteSalt
            ^ macroBiome.id
            ^ (macroSiteId & 0xFFFFFFFFL);

        int index = NoiseUtil.weightedIndex(worldSeed, salt, centerX, centerZ, variants);
        return index >= 0 ? variants.get(index) : null;
    }

    private void trimCache() {
        while (cache.size() > maxCacheEntries) {
            cache.removeLast();
        }
    }

    private static double distanceSq(int x1, int z1, int x2, int z2) {
        long dx = (long) x1 - x2;
        long dz = (long) z1 - z2;
        return (double) dx * dx + (double) dz * dz;
    }

    private static int floorDiv(int value, int divisor) {
        if (value >= 0) {
            return value / divisor;
        }
        return -((divisor - 1 - value) / divisor);
    }

    private static long pack(long macroSiteId, int cellX, int cellZ) {
        long mix = Long.rotateLeft(macroSiteId, 21) ^ Long.rotateLeft(macroSiteId, 7);
        long cell = (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
        return mix ^ cell;
    }
}
