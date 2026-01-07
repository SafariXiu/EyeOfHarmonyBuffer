package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.util.MathHelper;

import java.util.Objects;

public final class MacroSiteManager {

    private final FieldManager fieldManager;
    private final MacroSelectorConfig config;
    private final long worldSeed;
    private final int macroGridSize;
    private final double macroSiteSpacing;
    private final long macroSiteSalt;
    private final int neighborRadius;
    private final int maxCacheEntries;

    private final Long2ObjectLinkedOpenHashMap<MacroSite> siteCache = new Long2ObjectLinkedOpenHashMap<>();

    public MacroSiteManager(FieldManager fieldManager,
                            MacroSelectorConfig config,
                            long worldSeed) {

        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.config = Objects.requireNonNull(config, "config");
        this.worldSeed = worldSeed;

        this.macroGridSize = config.macroGridSize();
        this.macroSiteSpacing = config.macroSiteSpacing();
        this.macroSiteSalt = config.macroSiteSalt();
        this.neighborRadius = Math.max(2, config.macroNeighborRadius());
        this.maxCacheEntries = Math.max(64, config.macroCacheMaxEntries());
    }

    public MacroSiteQueryResult query(int blockX, int blockZ) {
        int cellX = floorDiv(blockX, macroGridSize);
        int cellZ = floorDiv(blockZ, macroGridSize);

        MacroSite primary = null;
        MacroSite secondary = null;
        double bestDistSq = Double.POSITIVE_INFINITY;
        double secondDistSq = Double.POSITIVE_INFINITY;

        for (int dz = -neighborRadius; dz <= neighborRadius; dz++) {
            for (int dx = -neighborRadius; dx <= neighborRadius; dx++) {
                int nx = cellX + dx;
                int nz = cellZ + dz;
                MacroSite site = resolveSite(nx, nz);
                double distSq = distanceSq(blockX, blockZ, site.centerX(), site.centerZ());
                if (distSq < bestDistSq) {
                    secondary = primary;
                    secondDistSq = bestDistSq;
                    primary = site;
                    bestDistSq = distSq;
                } else if (distSq < secondDistSq) {
                    secondary = site;
                    secondDistSq = distSq;
                }
            }
        }

        double primaryDist = Math.sqrt(bestDistSq);
        double secondaryDist = secondary == null ? Double.POSITIVE_INFINITY : Math.sqrt(secondDistSq);

        return new MacroSiteQueryResult(primary, secondary, primaryDist, secondaryDist);
    }

    private MacroSite resolveSite(int cellX, int cellZ) {
        long key = pack(cellX, cellZ);
        MacroSite cached = siteCache.get(key);
        if (cached != null) {
            return cached;
        }

        MacroSite generated = generateSite(cellX, cellZ);
        siteCache.putAndMoveToFirst(key, generated);
        trimCache();
        return generated;
    }

    private MacroSite generateSite(int cellX, int cellZ) {
        int originX = cellX * macroGridSize;
        int originZ = cellZ * macroGridSize;

        long hashX = NoiseUtil.mix(worldSeed, cellX, cellZ, macroSiteSalt ^ 0x45CCAA11L);
        long hashZ = NoiseUtil.mix(worldSeed, cellX, cellZ, macroSiteSalt ^ 0xBC117A31L);

        double jitterRadius = macroGridSize * 0.5;
        double offsetX = (NoiseUtil.hashToUnit(hashX) - 0.5d) * 2.0d * jitterRadius;
        double offsetZ = (NoiseUtil.hashToUnit(hashZ) - 0.5d) * 2.0d * jitterRadius;

        int centerX = (int) Math.round(originX + jitterRadius + offsetX);
        int centerZ = (int) Math.round(originZ + jitterRadius + offsetZ);

        TerrainSample terrain = fieldManager.sampleTerrain(centerX, centerZ);
        ClimateSample climate = fieldManager.sampleClimate(centerX, centerZ);
        HydroSample hydro = fieldManager.sampleHydro(centerX, centerZ);

        double continentalScore = config.continentalSettings().compose(
            terrain.elevation(), hydro.coastDistance(), hydro.saturation()
        );

        double humidity = MathHelper.clamp_float((float) climate.humidity(), 0.0f, 1.0f);
        double temperature = MathHelper.clamp_float((float) climate.temperature(), 0.0f, 1.0f);

        MacroTag macroTag = MacroTag.pick(continentalScore, humidity, temperature);
        MacroBiome macroBiome = macroTag.toMacroBiome();

        long id = NoiseUtil.mix(worldSeed, cellX, cellZ, macroSiteSalt);

        return new MacroSite(
            id,
            cellX,
            cellZ,
            centerX,
            centerZ,
            macroTag,
            macroBiome,
            continentalScore,
            humidity,
            temperature
        );
    }

    private void trimCache() {
        while (siteCache.size() > maxCacheEntries) {
            long key = siteCache.lastLongKey();
            siteCache.removeLast();
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

    private static long pack(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xFFFFFFFFL);
    }
}
