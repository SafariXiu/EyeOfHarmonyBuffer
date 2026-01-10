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
import net.minecraft.world.gen.NoiseGeneratorSimplex;

import java.util.Random;
import java.util.Locale;
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
    private final MacroSelectorConfig.LatitudeSettings latitudeSettings;
    private final double continentalLandThreshold;
    private final double coastSoftBandWidth;
    private final NoiseGeneratorSimplex latitudeWarpNoise;

    private static final LatitudeBand[] LATITUDE_BANDS = new LatitudeBand[]{
        new LatitudeBand(
            0,
            0.00d,
            0.12d,
            new MacroBiome[]{MacroBiome.SUBPOLAR, MacroBiome.MOUNTAINOUS},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        // 冷温带
        new LatitudeBand(
            1,
            0.12d,
            0.28d,
            new MacroBiome[]{MacroBiome.COOL_FORESTED, MacroBiome.SUBPOLAR},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        // 温带
        new LatitudeBand(
            2,
            0.28d,
            0.52d,
            new MacroBiome[]{MacroBiome.PLAINS_TEMPERATE, MacroBiome.COOL_FORESTED},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        // 亚热带
        new LatitudeBand(
            3,
            0.52d,
            0.72d,
            new MacroBiome[]{MacroBiome.WARM_DRY, MacroBiome.LOWLAND_WET},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        // 热带
        new LatitudeBand(
            4,
            0.72d,
            0.88d,
            new MacroBiome[]{MacroBiome.TROPICAL_HUMID, MacroBiome.LOWLAND_WET},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        // 赤道带
        new LatitudeBand(
            5,
            0.88d,
            1.00d,
            new MacroBiome[]{MacroBiome.TROPICAL_HUMID, MacroBiome.WARM_DRY},
            new MacroBiome[]{MacroBiome.OCEANIC}
        )
    };

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
        this.latitudeSettings = config.latitudeSettings();
        this.continentalLandThreshold = config.continentalLandThreshold();
        this.coastSoftBandWidth = config.coastSoftBandWidth();
        long latitudeSeed = config.baseSalt() ^ latitudeSettings.warpSalt();
        this.latitudeWarpNoise = new NoiseGeneratorSimplex(new Random(latitudeSeed));
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

        double jitterRadius = macroGridSize * 0.5d;
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

        MacroDomain domain = resolveDomain(continentalScore);
        double coastSoftness = computeCoastSoftness(continentalScore);

        MacroSelectorConfig.OverrideSettings override = config.overrideSettings();
        if (domain == MacroDomain.OCEAN
            && continentalScore >= override.landScoreThreshold()
            && hydro.coastDistance() > override.minShelfWidthBlocks()) {
            if (config.debugLogging()) {
                System.out.println(
                    "[MacroSiteManager] override -> LAND cell=(" + cellX + "," + cellZ + ")"
                        + " center=(" + centerX + "," + centerZ + ")"
                        + " score=" + String.format(Locale.ROOT, "%.3f", continentalScore)
                        + " coastDist=" + String.format(Locale.ROOT, "%.1f", hydro.coastDistance())
                );
            }
            domain = MacroDomain.LAND;
        }

        double latitude01 = sampleLatitude01(centerX, centerZ);
        LatitudeBand latitudeBand = findLatitudeBand(latitude01);
        int latitudeBandIndex = latitudeBand.index;

        MacroBiome[] pool = poolForDomain(latitudeBand, domain);

        long siteSeed = NoiseUtil.mix(worldSeed, cellX, cellZ, macroSiteSalt);
        MacroBiome macroBiome = selectMacroBiome(pool, siteSeed, humidity, temperature, coastSoftness);
        MacroTag macroTag = MacroTag.fromBiome(macroBiome);

        if (config.debugLogging()) {
            System.out.println(
                "[MacroSiteManager] cell=(" + cellX + "," + cellZ + ")"
                    + " center=(" + centerX + "," + centerZ + ")"
                    + " lat01=" + String.format(Locale.ROOT, "%.3f", latitude01)
                    + " band=" + latitudeBandIndex
                    + " domain=" + domain
                    + " coastSoft=" + String.format(Locale.ROOT, "%.2f", coastSoftness)
                    + " score=" + String.format(Locale.ROOT, "%.3f", continentalScore)
                    + " coastDist=" + String.format(Locale.ROOT, "%.1f", hydro.coastDistance())
                    + " biome=" + macroBiome
            );
        }

        return new MacroSite(
            siteSeed,
            cellX,
            cellZ,
            centerX,
            centerZ,
            macroTag,
            macroBiome,
            continentalScore,
            humidity,
            temperature,
            domain,
            latitude01,
            latitudeBandIndex,
            coastSoftness
        );
    }

    private void trimCache() {
        while (siteCache.size() > maxCacheEntries) {
            long key = siteCache.lastLongKey();
            siteCache.removeLast();
        }
    }

    private static final class LatitudeBand {
        final int index;
        final double min;
        final double max;
        final MacroBiome[] landPool;
        final MacroBiome[] seaPool;

        LatitudeBand(int index,
                     double min,
                     double max,
                     MacroBiome[] landPool,
                     MacroBiome[] seaPool) {
            this.index = index;
            this.min = min;
            this.max = max;
            this.landPool = landPool;
            this.seaPool = seaPool;
        }

        boolean contains(double latitude01) {
            return latitude01 >= min && latitude01 <= max;
        }
    }

    private static LatitudeBand findLatitudeBand(double latitude01) {
        for (LatitudeBand band : LATITUDE_BANDS) {
            if (band.contains(latitude01)) {
                return band;
            }
        }
        return LATITUDE_BANDS[LATITUDE_BANDS.length - 1];
    }

    private double sampleLatitude01(int blockX, int blockZ) {
        double period = latitudeSettings.periodBlocks();
        double basePhase = (blockZ / period) + latitudeSettings.baseBias();
        double wrapped = basePhase - Math.floor(basePhase);
        if (wrapped < 0.0d) {
            wrapped += 1.0d;
        }

        if (latitudeSettings.warpScale() > 0.0d && latitudeSettings.warpAmplitude() > 0.0d) {
            double nx = blockX * latitudeSettings.warpScale();
            double nz = blockZ * latitudeSettings.warpScale();
            double warp = latitudeWarpNoise.func_151605_a(nx, nz);
            wrapped += warp * latitudeSettings.warpAmplitude();
        }

        wrapped = MathHelper.clamp_double(wrapped, 0.0d, 1.0d);

        if (latitudeSettings.mixWeight() > 0.0d) {
            double mirror = 1.0d - wrapped;
            wrapped = lerp(latitudeSettings.mixWeight(), wrapped, mirror);
        }

        return wrapped;
    }

    private MacroDomain resolveDomain(double continentalValue) {
        return continentalValue >= continentalLandThreshold ? MacroDomain.LAND : MacroDomain.OCEAN;
    }

    private double computeCoastSoftness(double continentalValue) {
        if (coastSoftBandWidth <= 0.0d) {
            return 0.0d;
        }
        double delta = Math.abs(continentalValue - continentalLandThreshold);
        double softness = 1.0d - (delta / coastSoftBandWidth);
        return MathHelper.clamp_double(softness, 0.0d, 1.0d);
    }

    private static MacroBiome[] poolForDomain(LatitudeBand band, MacroDomain domain) {
        return domain == MacroDomain.OCEAN ? band.seaPool : band.landPool;
    }

    private MacroBiome selectMacroBiome(MacroBiome[] pool,
                                        long siteSeed,
                                        double humidity,
                                        double temperature,
                                        double coastSoftness) {
        if (pool.length == 1) {
            return pool[0];
        }
        Random rng = new Random(siteSeed);
        return pool[rng.nextInt(pool.length)];
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

    private static double lerp(double alpha, double from, double to) {
        return from + alpha * (to - from);
    }
}
