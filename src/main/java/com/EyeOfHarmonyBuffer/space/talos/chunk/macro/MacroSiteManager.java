package com.EyeOfHarmonyBuffer.space.talos.chunk.macro;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.manager.FieldManager;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.provider.MacroFieldProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.ClimateSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.HydroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.MacroSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.sample.TerrainSample;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
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
    private final MacroFieldProvider macroFieldProvider;
    private final MacroSelectorConfig.HeightContinuitySettings continuitySettings;


    private final Long2ObjectLinkedOpenHashMap<MacroSite> siteCache = new Long2ObjectLinkedOpenHashMap<>();

    private final Long2ObjectLinkedOpenHashMap<SeedSite> seedCache = new Long2ObjectLinkedOpenHashMap<>();

    private final LongOpenHashSet seeding = new LongOpenHashSet();
    private final LongOpenHashSet finalizing = new LongOpenHashSet();

    private static final class SeedSite {
        final long id;
        final int cellX, cellZ;
        final int centerX, centerZ;

        final MacroDomain domain;
        final double latitude01;
        final int latitudeBandIndex;

        final double continentalScore;
        final double humidity;
        final double temperature;
        final double coastSoftness;

        final List<BiomeScore> scored;
        final MacroBiome initialPick;

        final double heightVariation;
        final double baseHeight;
        final double macroVariance;
        final double microVariance;

        final double[] baseGrid;
        final double[] macroGrid;
        final double[] microGrid;
        final int gridRes;
        final double gridStep;

        SeedSite(long id,
                 int cellX, int cellZ,
                 int centerX, int centerZ,
                 MacroDomain domain,
                 double latitude01,
                 int latitudeBandIndex,
                 double continentalScore,
                 double humidity,
                 double temperature,
                 double coastSoftness,
                 List<BiomeScore> scored,
                 MacroBiome initialPick,
                 double heightVariation,
                 double baseHeight,
                 double macroVariance,
                 double microVariance,
                 double[] baseGrid,
                 double[] macroGrid,
                 double[] microGrid,
                 int gridRes,
                 double gridStep) {

            this.id = id;
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.domain = Objects.requireNonNull(domain, "domain");
            this.latitude01 = latitude01;
            this.latitudeBandIndex = latitudeBandIndex;
            this.continentalScore = continentalScore;
            this.humidity = humidity;
            this.temperature = temperature;
            this.coastSoftness = coastSoftness;
            this.scored = Objects.requireNonNull(scored, "scored");
            this.initialPick = Objects.requireNonNull(initialPick, "initialPick");
            this.heightVariation = Double.isNaN(heightVariation) ? 0.0d : Math.max(0.0d, heightVariation);
            this.baseHeight = baseHeight;
            this.macroVariance = macroVariance;
            this.microVariance = microVariance;
            this.baseGrid = baseGrid;
            this.macroGrid = macroGrid;
            this.microGrid = microGrid;
            this.gridRes = gridRes;
            this.gridStep = gridStep;
        }
    }

    public MacroSiteManager(FieldManager fieldManager,
                            MacroFieldProvider macroFieldProvider,
                            MacroSelectorConfig config,
                            long worldSeed) {

        this.fieldManager = Objects.requireNonNull(fieldManager, "fieldManager");
        this.macroFieldProvider = Objects.requireNonNull(macroFieldProvider, "macroFieldProvider");
        this.continuitySettings = Objects.requireNonNull(config.heightContinuity(), "heightContinuity");
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
        final int K = 3;
        int cellX = floorDiv(blockX, macroGridSize);
        int cellZ = floorDiv(blockZ, macroGridSize);

        MacroSite[] bestSite = new MacroSite[K];
        double[] bestDistSq = new double[K];
        for (int i = 0; i < K; i++) bestDistSq[i] = Double.POSITIVE_INFINITY;

        for (int dz = -neighborRadius; dz <= neighborRadius; dz++) {
            for (int dx = -neighborRadius; dx <= neighborRadius; dx++) {
                int nx = cellX + dx;
                int nz = cellZ + dz;
                MacroSite site = resolveSite(nx, nz);
                double distSq = distanceSq(blockX, blockZ, site.centerX(), site.centerZ());

                for (int i = 0; i < K; i++) {
                    if (distSq < bestDistSq[i]) {
                        for (int j = K - 1; j > i; j--) {
                            bestDistSq[j] = bestDistSq[j - 1];
                            bestSite[j] = bestSite[j - 1];
                        }
                        bestDistSq[i] = distSq;
                        bestSite[i] = site;
                        break;
                    }
                }
            }
        }

        ArrayList<SiteHit> hits = new ArrayList<>(K);
        for (int i = 0; i < K; i++) {
            if (bestSite[i] != null) {
                hits.add(new SiteHit(bestSite[i], bestDistSq[i]));
            }
        }

        if (hits.isEmpty()) {
            throw new IllegalStateException("MacroSiteManager.query produced no hits");
        }
        return new MacroSiteQueryResult(hits);
    }

    private MacroSite resolveSite(int cellX, int cellZ) {
        long key = pack(cellX, cellZ);

        MacroSite cached = siteCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (finalizing.contains(key)) {
            throw new IllegalStateException("Recursive finalize detected for cell=(" + cellX + "," + cellZ + ")");
        }

        finalizing.add(key);
        try {
            SeedSite seed = resolveSeedSite(cellX, cellZ);
            MacroBiome chosen = pickWithLandDiversity(seed);

            MacroTag tag = MacroTag.fromBiome(chosen);

            MacroSite site = new MacroSite(
                seed.id,
                seed.cellX,
                seed.cellZ,
                seed.centerX,
                seed.centerZ,
                tag,
                chosen,
                seed.continentalScore,
                seed.humidity,
                seed.temperature,
                seed.domain,
                seed.latitude01,
                seed.latitudeBandIndex,
                seed.coastSoftness,
                seed.baseHeight,
                seed.macroVariance,
                seed.microVariance,
                seed.heightVariation,
                seed.baseGrid,
                seed.macroGrid,
                seed.microGrid,
                seed.gridRes,
                seed.gridStep
            );

            siteCache.putAndMoveToFirst(key, site);
            trimCache();
            return site;
        } finally {
            finalizing.remove(key);
        }
    }

    private SeedSite seedSite(int cellX, int cellZ) {
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

        // IMPORTANT: seed pick is neighbor-free; diversity is applied later in finalize().
        MacroBiome initialPick = scored.isEmpty() ? pool[0] : scored.get(0).biome();

        double heightVariation = computeHeightVariation(initialPick);

        int gridRes = config.continuousHeightField()
            ? Math.max(2, config.heightControlResolution())
            : 1;

        double[] baseGrid = null;
        double[] macroGrid = null;
        double[] microGrid = null;
        double gridStep = 0.0d;

        if (gridRes > 1) {
            baseGrid = new double[gridRes * gridRes];
            macroGrid = new double[gridRes * gridRes];
            microGrid = new double[gridRes * gridRes];
            gridStep = (double) macroGridSize / (gridRes - 1);
            double halfSpan = (gridRes - 1) * 0.5d;

            for (int gz = 0; gz < gridRes; gz++) {
                double offsetZ1 = (gz - halfSpan) * gridStep;
                int sampleZ = (int) Math.round(centerZ + offsetZ1);

                for (int gx = 0; gx < gridRes; gx++) {
                    double offsetX1 = (gx - halfSpan) * gridStep;
                    int sampleX = (int) Math.round(centerX + offsetX1);

                    TerrainSample gridTerrain = fieldManager.sampleTerrain(sampleX, sampleZ);
                    ClimateSample gridClimate = fieldManager.sampleClimate(sampleX, sampleZ);
                    HydroSample gridHydro = fieldManager.sampleHydro(sampleX, sampleZ);

                    double gridContinental = config.continentalSettings().compose(
                        gridTerrain.elevation(),
                        gridHydro.coastDistance(),
                        gridHydro.saturation()
                    );

                    double gridHumidity = MathHelper.clamp_double(gridClimate.humidity(), 0.0d, 1.0d);

                    double sampleBase = computeBaseHeight(initialPick, gridContinental);
                    sampleBase = anchorBaseHeight(sampleX, sampleZ, sampleBase, heightVariation);

                    double sampleMacro = computeMacroVariance(initialPick, gridHumidity, heightVariation);
                    double sampleMicro = computeMicroVariance(initialPick, gridHumidity, heightVariation);

                    int idx = gz * gridRes + gx;
                    baseGrid[idx] = sampleBase;
                    macroGrid[idx] = sampleMacro;
                    microGrid[idx] = sampleMicro;
                }
            }

            applyContinuityFilters(baseGrid, gridRes);
            applyContinuityFilters(macroGrid, gridRes);
            applyContinuityFilters(microGrid, gridRes);
        }

        double baseHeight = computeBaseHeight(initialPick, continentalScore);
        baseHeight = anchorBaseHeight(centerX, centerZ, baseHeight, heightVariation);
        double macroVariance = computeMacroVariance(initialPick, humidity, heightVariation);
        double microVariance = computeMicroVariance(initialPick, humidity, heightVariation);

        if (config.debugLogging()) {
            System.out.println(
                "[MacroSiteManager] seed cell=(" + cellX + "," + cellZ + ")"
                    + " center=(" + centerX + "," + centerZ + ")"
                    + " lat01=" + String.format(Locale.ROOT, "%.3f", latitude01)
                    + " band=" + latitudeBandIndex
                    + " domain=" + domain
                    + " plate=" + String.format(Locale.ROOT, "%.3f", plateHeight)
                    + " coastSoft=" + String.format(Locale.ROOT, "%.2f", coastSoftness)
                    + " score=" + String.format(Locale.ROOT, "%.3f", continentalScore)
                    + " coastDist=" + String.format(Locale.ROOT, "%.1f", hydro.coastDistance())
                    + " initialBiome=" + initialPick
            );
        }

        return new SeedSite(
            siteSeed,
            cellX, cellZ,
            centerX, centerZ,
            domain,
            latitude01,
            latitudeBandIndex,
            continentalScore,
            humidity,
            temperature,
            coastSoftness,
            scored,
            initialPick,
            heightVariation,
            baseHeight,
            macroVariance,
            microVariance,
            baseGrid,
            macroGrid,
            microGrid,
            gridRes,
            gridStep
        );
    }

    private void trimCache() {
        while (siteCache.size() > maxCacheEntries) {
            siteCache.removeLast();
        }
        while (seedCache.size() > maxCacheEntries) {
            seedCache.removeLast();
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

    private double computeBaseHeight(MacroBiome macroBiome, double continentalScore) {
        double continental01 = clamp01(0.5d * (continentalScore + 1.0d));

        double t = smoothStep(continental01);

        if (heightProfile != null) {
            double floor = heightProfile.terrainFloorY();
            double range = heightProfile.terrainRange();

            double worldY = floor + t * range;

            double normalized01 = (worldY - floor) / range;
            return MathHelper.clamp_double(normalized01 * 2.0d - 1.0d, -1.0d, 1.0d);
        }

        return MathHelper.clamp_double(t * 2.0d - 1.0d, -1.0d, 1.0d);
    }

    private static double smoothStep(double t) {
        double c = clamp01(t);
        return c * c * (3.0d - 2.0d * c);
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

    private double anchorBaseHeight(int blockX,
                                    int blockZ,
                                    double localBase,
                                    double variation) {
        if (continuitySettings == null || continuitySettings.disabled()) {
            return localBase;
        }

        MacroSample macroSample = macroFieldProvider.sample(blockX, blockZ);
        if (macroSample == null) {
            return localBase;
        }

        double anchor = normalizeMacroSampleHeight(macroSample.macroBaseHeight());
        double anchorWeight = continuitySettings.globalFieldWeight();
        double sampleWeight = MathHelper.clamp_double(macroSample.anchorWeight(), 0.0d, 1.0d);
        double hardEdge = MathHelper.clamp_double(macroSample.hardEdge(), 0.0d, 1.0d);

        double weight = anchorWeight * (0.5d + 0.5d * sampleWeight) * (0.75d + 0.25d * hardEdge);
        if (weight <= 0.0d) {
            return localBase;
        }

        double blended = lerp(weight, localBase, anchor);
        return MathHelper.clamp_double(blended, -1.0d, 1.0d);
    }

    private static double normalizeMacroSampleHeight(short macroBaseHeight) {
        return MathHelper.clamp_double(macroBaseHeight / 32767.0d, -1.0d, 1.0d);
    }

    private void applyContinuityFilters(double[] grid, int resolution) {
        if (continuitySettings == null
            || continuitySettings.disabled()
            || grid == null
            || resolution <= 1) {
            return;
        }

        if (continuitySettings.maxNeighborDelta() > 0.0d) {
            clampNeighborDelta(grid, resolution, continuitySettings.maxNeighborDelta());
        }

        int iterations = Math.max(0, continuitySettings.relaxIterations());
        double strength = MathHelper.clamp_double(continuitySettings.gridBlurStrength(), 0.0d, 1.0d);
        if (iterations > 0 && strength > 0.0d) {
            int radius = Math.max(1, continuitySettings.smoothingRadius());
            for (int i = 0; i < iterations; i++) {
                boxBlurGrid(grid, resolution, radius, strength);
            }
        }
    }

    private void clampNeighborDelta(double[] grid,
                                    int resolution,
                                    double maxDelta) {
        double allowed = MathHelper.clamp_double(maxDelta, 0.0d, 1.0d);
        if (allowed <= 0.0d) {
            return;
        }

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                int idx = z * resolution + x;
                double value = grid[idx];

                if (x + 1 < resolution) {
                    int right = idx + 1;
                    double delta = grid[right] - value;
                    if (Math.abs(delta) > allowed) {
                        grid[right] = value + Math.copySign(allowed, delta);
                    }
                }
                if (z + 1 < resolution) {
                    int down = idx + resolution;
                    double delta = grid[down] - value;
                    if (Math.abs(delta) > allowed) {
                        grid[down] = value + Math.copySign(allowed, delta);
                    }
                }
            }
        }
    }

    private void boxBlurGrid(double[] grid,
                             int resolution,
                             int radius,
                             double strength) {
        double[] scratch = Arrays.copyOf(grid, grid.length);
        double invArea = 1.0d / ((radius * 2 + 1) * (radius * 2 + 1));
        double wSelf = 1.0d - strength;

        for (int z = 0; z < resolution; z++) {
            for (int x = 0; x < resolution; x++) {
                double sum = 0.0d;
                for (int dz = -radius; dz <= radius; dz++) {
                    int nz = MathHelper.clamp_int(z + dz, 0, resolution - 1);
                    for (int dx = -radius; dx <= radius; dx++) {
                        int nx = MathHelper.clamp_int(x + dx, 0, resolution - 1);
                        sum += scratch[nz * resolution + nx];
                    }
                }
                double blurred = sum * invArea;
                int idx = z * resolution + x;
                grid[idx] = grid[idx] * wSelf + blurred * strength;
            }
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

    private SeedSite resolveSeedSite(int cellX, int cellZ) {
        long key = pack(cellX, cellZ);

        SeedSite cached = seedCache.get(key);
        if (cached != null) {
            return cached;
        }

        if (seeding.contains(key)) {
            // Should never happen because seeding must be neighbor-free.
            throw new IllegalStateException("Recursive seeding detected for cell=(" + cellX + "," + cellZ + ")");
        }

        seeding.add(key);
        try {
            SeedSite seeded = seedSite(cellX, cellZ);
            seedCache.putAndMoveToFirst(key, seeded);
            trimCache();
            return seeded;
        } finally {
            seeding.remove(key);
        }
    }

    private MacroBiome pickWithLandDiversity(SeedSite self) {
        if (self.domain == MacroDomain.OCEAN || self.scored.size() <= 1) {
            return self.initialPick;
        }

        MacroBiome proposed = self.initialPick;

        int duplicates = countNeighborMatchesSeed(self.cellX, self.cellZ, proposed);
        if (duplicates < LAND_DIVERSITY_DUP_THRESHOLD) {
            return proposed;
        }

        for (BiomeScore candidate : self.scored) {
            MacroBiome biome = candidate.biome();
            if (biome == proposed || biome.isOceanic()) continue;
            if (countNeighborMatchesSeed(self.cellX, self.cellZ, biome) == 0) {
                return biome;
            }
        }
        return proposed;
    }

    private int countNeighborMatchesSeed(int cellX, int cellZ, MacroBiome biome) {
        int matches = 0;
        for (int dz = -LAND_DIVERSITY_NEIGHBOR_RADIUS; dz <= LAND_DIVERSITY_NEIGHBOR_RADIUS; dz++) {
            for (int dx = -LAND_DIVERSITY_NEIGHBOR_RADIUS; dx <= LAND_DIVERSITY_NEIGHBOR_RADIUS; dx++) {
                if (dx == 0 && dz == 0) continue;

                SeedSite neighbor = resolveSeedSite(cellX + dx, cellZ + dz);
                if (neighbor.domain != MacroDomain.LAND) continue;

                if (neighbor.initialPick == biome) {
                    matches++;
                }
            }
        }
        return matches;
    }

    public static final class SiteHit {
        public final MacroSite site;
        public final double distSq;
        public final double dist;

        public SiteHit(MacroSite site, double distSq) {
            this.site = site;
            this.distSq = distSq;
            this.dist = Math.sqrt(distSq);
        }
    }
}
