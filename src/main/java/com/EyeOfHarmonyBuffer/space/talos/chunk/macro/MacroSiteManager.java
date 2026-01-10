package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.util.MathHelper;
import net.minecraft.world.gen.NoiseGeneratorSimplex;

import java.util.*;

public final class MacroSiteManager {

    private static final LatitudeBand[] LATITUDE_BANDS = new LatitudeBand[]{
        new LatitudeBand(
            0,
            0.00d,
            0.12d,
            new MacroBiome[]{MacroBiome.SUBPOLAR, MacroBiome.MOUNTAINOUS},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        new LatitudeBand(
            1,
            0.12d,
            0.28d,
            new MacroBiome[]{MacroBiome.COOL_FORESTED, MacroBiome.SUBPOLAR},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        new LatitudeBand(
            2,
            0.28d,
            0.52d,
            new MacroBiome[]{MacroBiome.PLAINS_TEMPERATE, MacroBiome.COOL_FORESTED},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        new LatitudeBand(
            3,
            0.52d,
            0.72d,
            new MacroBiome[]{MacroBiome.WARM_DRY, MacroBiome.LOWLAND_WET},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        new LatitudeBand(
            4,
            0.72d,
            0.88d,
            new MacroBiome[]{MacroBiome.TROPICAL_HUMID, MacroBiome.LOWLAND_WET},
            new MacroBiome[]{MacroBiome.OCEANIC}
        ),
        new LatitudeBand(
            5,
            0.88d,
            1.00d,
            new MacroBiome[]{MacroBiome.TROPICAL_HUMID, MacroBiome.WARM_DRY},
            new MacroBiome[]{MacroBiome.OCEANIC}
        )
    };

    private static final int LAND_DIVERSITY_NEIGHBOR_RADIUS = 1;
    private static final int LAND_DIVERSITY_DUP_THRESHOLD = 4;
    private static final double CLIMATE_TEMP_WEIGHT = 1.15d;
    private static final double CLIMATE_HUMIDITY_WEIGHT = 0.85d;
    private static final double COASTAL_WEIGHT = 0.25d;

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
    private final MacroSelectorConfig.HeightProfile heightProfile;
    private final PlateMaskSampler plateMaskSampler;

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
        double configuredSpacing = this.macroSiteSpacing > 0.0d
            ? this.macroSiteSpacing
            : this.macroGridSize;
        int plateSampleSpacing = (int) Math.max(1.0d, Math.round(configuredSpacing));
        this.plateMaskSampler = new PlateMaskSampler(worldSeed, plateSampleSpacing);
        this.neighborRadius = Math.max(2, config.macroNeighborRadius());
        this.maxCacheEntries = Math.max(64, config.macroCacheMaxEntries());
        this.latitudeSettings = config.latitudeSettings();
        this.continentalLandThreshold = config.continentalLandThreshold();
        this.coastSoftBandWidth = config.coastSoftBandWidth();
        long latitudeSeed = config.baseSalt() ^ latitudeSettings.warpSalt();
        this.latitudeWarpNoise = new NoiseGeneratorSimplex(new Random(latitudeSeed));
        this.heightProfile = config.heightProfile();
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

        PlateMaskSample plateSample = plateMaskSampler.sample(centerX, centerZ);
        double plateHeight = plateSample.height();

        TerrainSample terrain = fieldManager.sampleTerrain(centerX, centerZ);
        ClimateSample climate = fieldManager.sampleClimate(centerX, centerZ);
        HydroSample hydro = fieldManager.sampleHydro(centerX, centerZ);

        double continentalScore = config.continentalSettings().compose(
            terrain.elevation(), hydro.coastDistance(), hydro.saturation()
        );

        double humidity = MathHelper.clamp_float((float) climate.humidity(), 0.0f, 1.0f);
        double temperature = MathHelper.clamp_float((float) climate.temperature(), 0.0f, 1.0f);

        MacroDomain domain = plateSample.isLand() ? MacroDomain.LAND : MacroDomain.OCEAN;
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

        List<BiomeScore> scored = scoreBiomes(pool, siteSeed, humidity, temperature, coastSoftness);
        MacroBiome macroBiome = scored.isEmpty() ? pool[0] : scored.get(0).biome();
        macroBiome = enforceLandDiversity(cellX, cellZ, domain, macroBiome, scored);

        MacroTag macroTag = MacroTag.fromBiome(macroBiome);

        double heightVariation = computeHeightVariation(macroBiome);

        double baseHeight = computeBaseHeight(macroBiome, continentalScore);
        double macroVariance = computeMacroVariance(macroBiome, humidity, heightVariation);
        double microVariance = computeMicroVariance(macroBiome, humidity, heightVariation);

        if (config.debugLogging()) {
            System.out.println(
                "[MacroSiteManager] cell=(" + cellX + "," + cellZ + ")"
                    + " center=(" + centerX + "," + centerZ + ")"
                    + " lat01=" + String.format(Locale.ROOT, "%.3f", latitude01)
                    + " band=" + latitudeBandIndex
                    + " domain=" + domain
                    + " plate=" + String.format(Locale.ROOT, "%.3f", plateHeight)
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
            coastSoftness,
            baseHeight,
            macroVariance,
            microVariance,
            heightVariation
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

    private List<BiomeScore> scoreBiomes(MacroBiome[] pool,
                                         long siteSeed,
                                         double humidity,
                                         double temperature,
                                         double coastSoftness) {
        List<BiomeScore> scored = new ArrayList<>(pool.length);
        for (MacroBiome candidate : pool) {
            double dh = candidate.climate.humidity - humidity;
            double dt = candidate.climate.temperature - temperature;

            double score = (dh * dh * CLIMATE_HUMIDITY_WEIGHT) + (dt * dt * CLIMATE_TEMP_WEIGHT);

            double coastalBias = candidate.isCoastal()
                ? Math.abs(1.0d - coastSoftness)
                : coastSoftness;
            score += coastalBias * COASTAL_WEIGHT;

            long mixSeed = NoiseUtil.mix(siteSeed, candidate.id, 0, macroSiteSalt ^ 0x6F4D38A1L);
            double jitter = (NoiseUtil.hashToUnit(mixSeed) - 0.5d) * 0.01d;
            score += jitter;

            scored.add(new BiomeScore(candidate, score));
        }

        scored.sort(Comparator.comparingDouble(BiomeScore::score));
        return scored;
    }

    private MacroBiome enforceLandDiversity(int cellX,
                                            int cellZ,
                                            MacroDomain domain,
                                            MacroBiome proposed,
                                            List<BiomeScore> scored) {
        if (domain == MacroDomain.OCEAN || scored.size() <= 1) {
            return proposed;
        }

        int duplicates = countNeighborMatches(cellX, cellZ, proposed);
        if (duplicates < LAND_DIVERSITY_DUP_THRESHOLD) {
            return proposed;
        }

        for (BiomeScore candidate : scored) {
            MacroBiome biome = candidate.biome();
            if (biome == proposed || biome.isOceanic()) {
                continue;
            }
            if (countNeighborMatches(cellX, cellZ, biome) == 0) {
                return biome;
            }
        }

        return proposed;
    }

    private int countNeighborMatches(int cellX, int cellZ, MacroBiome biome) {
        int matches = 0;
        for (int dz = -LAND_DIVERSITY_NEIGHBOR_RADIUS; dz <= LAND_DIVERSITY_NEIGHBOR_RADIUS; dz++) {
            for (int dx = -LAND_DIVERSITY_NEIGHBOR_RADIUS; dx <= LAND_DIVERSITY_NEIGHBOR_RADIUS; dx++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                MacroSite neighbor = siteCache.get(pack(cellX + dx, cellZ + dz));
                if (neighbor == null) {
                    continue;
                }
                if (neighbor.domain() != MacroDomain.LAND) {
                    continue;
                }
                if (neighbor.macroBiome() == biome) {
                    matches++;
                }
            }
        }
        return matches;
    }

    private double computeHeightVariation(MacroBiome macroBiome) {
        if (macroBiome == null) {
            return 0.0d;
        }
        double macroRange = Math.max(1.0d, macroBiome.height.absoluteMax - macroBiome.height.absoluteMin);
        double worldRange = heightProfile != null
            ? Math.max(1.0d, heightProfile.terrainRange())
            : macroRange;
        double normalized = macroRange / worldRange;
        return MathHelper.clamp_double(normalized, 0.05d, 1.50d);
    }

    private double computeBaseHeight(MacroBiome macroBiome,
                                     double continentalScore) {
        MacroBiome.MacroHeightProfile macroHeight = macroBiome.height;
        double continental01 = clamp01(0.5d * (continentalScore + 1.0d));

        double worldHeight = lerp(
            macroHeight.absoluteMin,
            macroHeight.absoluteMax,
            continental01
        ) + macroHeight.baseHeightOffset;

        double clampedWorld = MathHelper.clamp_double(
            worldHeight,
            macroHeight.absoluteMin,
            macroHeight.absoluteMax
        );

        if (heightProfile != null) {
            double floor = heightProfile.terrainFloorY();
            double range = heightProfile.terrainRange();
            double normalized = (clampedWorld - floor) / range;
            return MathHelper.clamp_double(normalized * 2.0d - 1.0d, -1.0d, 1.0d);
        }

        double macroRange = Math.max(1.0d, macroHeight.absoluteMax - macroHeight.absoluteMin);
        double normalized = (clampedWorld - macroHeight.absoluteMin) / macroRange;
        return MathHelper.clamp_double(normalized * 2.0d - 1.0d, -1.0d, 1.0d);
    }

    private double computeMacroVariance(MacroBiome macroBiome,
                                        double humidity,
                                        double heightVariation) {
        double humidityClamped = MathHelper.clamp_double(humidity, 0.0d, 1.0d);
        double base = lerp(0.60d, -0.10d, humidityClamped);
        double scaled = base * (0.65d + 0.35d * heightVariation);

        if (macroBiome.isHardEdge()) {
            scaled += 0.12d;
        }
        if (macroBiome.isOceanic()) {
            scaled -= 0.25d;
        }
        return MathHelper.clamp_double(scaled, -1.0d, 1.0d);
    }

    private double computeMicroVariance(MacroBiome macroBiome,
                                        double humidity,
                                        double heightVariation) {
        double humidityClamped = MathHelper.clamp_double(humidity, 0.0d, 1.0d);
        double base = 0.28d - 0.16d * humidityClamped;
        double scaled = base * (0.50d + 0.50d * heightVariation);

        if (macroBiome.isHardEdge()) {
            scaled += 0.05d;
        }
        if (macroBiome.isOceanic()) {
            scaled *= 0.5d;
        }
        return MathHelper.clamp_double(scaled, 0.02d, 0.80d);
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

    private static double clamp01(double value) {
        if (value < 0.0d) {
            return 0.0d;
        }
        if (value > 1.0d) {
            return 1.0d;
        }
        return value;
    }

    @Desugar
    private record BiomeScore(MacroBiome biome, double score) {}

    private static final class PlateMaskSampler {

        private static final long PLATE_SALT = 0xE531A2D7B4C3F19EL;
        private static final double PLATE_FREQUENCY = 1.0 / 4096.0;
        private static final int PLATE_OCTAVES = 4;
        private static final double PLATE_LACUNARITY = 2.05d;
        private static final double PLATE_GAIN = 0.48d;
        private static final double SEA_LEVEL_THRESHOLD = 0.02d;

        private static final int SMOOTH_RADIUS = 1;
        private static final int LAND_VOTE_THRESHOLD = 5;

        private static final int CONNECTIVITY_RADIUS = 2;
        private static final int MIN_LAND_COMPONENT = 5;
        private static final int MIN_OCEAN_COMPONENT = 5;

        private static final int[][] FLOOD_NEIGHBORS = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1,  0},          {1,  0},
            {-1,  1}, {0,  1}, {1,  1}
        };

        private final long worldSeed;
        private final int sampleSpacing;
        private final Long2DoubleOpenHashMap rawHeightCache = new Long2DoubleOpenHashMap();

        private PlateMaskSampler(long worldSeed, int sampleSpacing) {
            this.worldSeed = worldSeed;
            this.sampleSpacing = sampleSpacing;
            this.rawHeightCache.defaultReturnValue(Double.NaN);
        }

        PlateMaskSample sample(int blockX, int blockZ) {
            int diameter = CONNECTIVITY_RADIUS * 2 + 1;
            boolean[][] mask = new boolean[diameter][diameter];
            double centerHeight = Double.NaN;

            for (int dz = -CONNECTIVITY_RADIUS; dz <= CONNECTIVITY_RADIUS; dz++) {
                for (int dx = -CONNECTIVITY_RADIUS; dx <= CONNECTIVITY_RADIUS; dx++) {
                    int sampleX = blockX + dx * sampleSpacing;
                    int sampleZ = blockZ + dz * sampleSpacing;
                    double height = sampleRawHeight(sampleX, sampleZ);
                    if (dx == 0 && dz == 0) {
                        centerHeight = height;
                    }
                    mask[dx + CONNECTIVITY_RADIUS][dz + CONNECTIVITY_RADIUS] = height > SEA_LEVEL_THRESHOLD;
                }
            }

            boolean smoothed = applyMajority(mask);
            boolean finalMask = enforceConnectivity(mask, smoothed);

            return new PlateMaskSample(centerHeight, finalMask);
        }

        private boolean applyMajority(boolean[][] mask) {
            int center = CONNECTIVITY_RADIUS;
            int landVotes = 0;

            for (int dz = -SMOOTH_RADIUS; dz <= SMOOTH_RADIUS; dz++) {
                for (int dx = -SMOOTH_RADIUS; dx <= SMOOTH_RADIUS; dx++) {
                    if (mask[center + dx][center + dz]) {
                        landVotes++;
                    }
                }
            }

            boolean majority = landVotes >= LAND_VOTE_THRESHOLD;
            mask[center][center] = majority;
            return majority;
        }

        private boolean enforceConnectivity(boolean[][] mask, boolean currentValue) {
            int center = CONNECTIVITY_RADIUS;
            int componentSize = floodFill(mask, center, center, currentValue);
            int threshold = currentValue ? MIN_LAND_COMPONENT : MIN_OCEAN_COMPONENT;

            if (componentSize >= threshold) {
                return currentValue;
            }
            return !currentValue;
        }

        private int floodFill(boolean[][] mask, int startX, int startZ, boolean targetValue) {
            int diameter = mask.length;
            boolean[][] visited = new boolean[diameter][diameter];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[]{startX, startZ});
            visited[startX][startZ] = true;
            int size = 0;

            while (!queue.isEmpty()) {
                int[] pos = queue.removeFirst();
                size++;

                for (int[] dir : FLOOD_NEIGHBORS) {
                    int nx = pos[0] + dir[0];
                    int nz = pos[1] + dir[1];
                    if (nx < 0 || nz < 0 || nx >= diameter || nz >= diameter) {
                        continue;
                    }
                    if (visited[nx][nz]) {
                        continue;
                    }
                    if (mask[nx][nz] != targetValue) {
                        continue;
                    }
                    visited[nx][nz] = true;
                    queue.addLast(new int[]{nx, nz});
                }
            }

            return size;
        }

        private double sampleRawHeight(int blockX, int blockZ) {
            long key = (((long) blockX) << 32) ^ (blockZ & 0xFFFFFFFFL);
            double cached = rawHeightCache.get(key);
            if (!Double.isNaN(cached)) {
                return cached;
            }

            double value = NoiseUtil.fractal(
                worldSeed,
                PLATE_SALT,
                blockX,
                blockZ,
                PLATE_FREQUENCY,
                PLATE_OCTAVES,
                PLATE_LACUNARITY,
                PLATE_GAIN
            );
            rawHeightCache.put(key, value);
            return value;
        }
    }

    private static final class PlateMaskSample {
        private final double height;
        private final boolean land;

        private PlateMaskSample(double height, boolean land) {
            this.height = height;
            this.land = land;
        }

        double height() {
            return height;
        }

        boolean isLand() {
            return land;
        }
    }
}
