package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.ContinentalField;
import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import net.minecraft.util.MathHelper;

import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome.*;

public final class MacroBiomeField implements ContinentalField {

    public static final long MACRO_SALT = 0x9E3779B97F4A7C15L;

    private static final int MACRO_CELL_SIZE = 2048;
    private static final double MACRO_PLATE_LOCK = 0.65D;
    private static final double MACRO_SELECTOR_SOFTNESS = 0.18D;
    private static final double SECONDARY_OFFSET = 0.07D;

    private final ThreadLocal<NoiseSample> scratchSample =
        ThreadLocal.withInitial(NoiseSample::new);

    @Override
    public double continentalBase(int x, int z) {
        return sampleNoise(x, z).base;
    }

    @Override
    public double continentalLatitude01(int x, int z) {
        return sampleNoise(x, z).latitude01;
    }

    public static final class LatitudeBand {
        public final double min;
        public final double max;
        public final MacroBiome[] pool;

        public LatitudeBand(double min, double max, MacroBiome[] pool) {
            this.min = min;
            this.max = max;
            this.pool = pool;
        }
    }

    private final MacroBiomeConfig config;
    private final long worldSeed;

    private final SimplexNoiseOctave detailNoise;

    private final SimplexNoiseOctave baseNoiseCoarse;
    private final SimplexNoiseOctave baseNoiseFine;

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

    private static final int TILE_SIZE = 64;
    private static final int TILE_MASK = TILE_SIZE - 1;
    private static final int MAX_TILES = 256;

    private final TileCache tileCache = new TileCache();

    public MacroBiomeField(long worldSeed, MacroBiomeConfig config) {
        this.worldSeed = worldSeed;
        this.config = (config == null) ? MacroBiomeConfig.defaultPreset() : config;

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

        this.baseNoiseCoarse = new SimplexNoiseOctave(worldSeed ^ MACRO_SALT, this.config.baseOctaves);
        this.baseNoiseFine   = new SimplexNoiseOctave((worldSeed + 0x5DEECE66DL) ^ MACRO_SALT, this.config.baseOctaves);
        this.detailNoise     = new SimplexNoiseOctave(worldSeed ^ (MACRO_SALT >>> 1), this.config.detailOctaves);
    }

    private static final LatitudeBand[] LATITUDE_BANDS = {
        new LatitudeBand(0.00, 0.18, new MacroBiome[]{SUBPOLAR}),
        new LatitudeBand(0.18, 0.32, new MacroBiome[]{SUBPOLAR, COOL_FORESTED}),
        new LatitudeBand(0.32, 0.52, new MacroBiome[]{COOL_FORESTED, PLAINS_TEMPERATE, MOUNTAINOUS}),
        new LatitudeBand(0.52, 0.72, new MacroBiome[]{PLAINS_TEMPERATE, WARM_DRY, MOUNTAINOUS}),
        new LatitudeBand(0.72, 0.88, new MacroBiome[]{WARM_DRY, LOWLAND_WET, TROPICAL_HUMID}),
        new LatitudeBand(0.88, 1.00, new MacroBiome[]{TROPICAL_HUMID, LOWLAND_WET})
    };

    private static LatitudeBand findBand(double latitude01) {
        for (LatitudeBand band : LATITUDE_BANDS) {
            if (latitude01 >= band.min && latitude01 < band.max) {
                return band;
            }
        }
        return LATITUDE_BANDS[LATITUDE_BANDS.length - 1];
    }

    public void sample(int chunkX, int chunkZ, ChunkProviderTalos2.ChunkShoreCache cache) {
        for (int lx = 0; lx <= 16; lx++) {
            for (int lz = 0; lz <= 16; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                NoiseSample ns = sampleNoise(gx, gz);
                MacroBiome primary = pickPrimary(gx, gz, ns.base, ns.latitude01);
                MacroBiome secondary = pickSecondary(gx, gz, ns.base, ns.latitude01);
                double blend = computeBlend(ns.base, ns.detail);

                byte primaryId = (byte) primary.ordinal();
                byte secondaryId = (byte) secondary.ordinal();
                byte blendByte = (byte) MathHelper.clamp_int((int) Math.round(blend * 255.0), 0, 255);

                cache.macroPrimary[lx][lz] = primaryId;
                cache.macroSecondary[lx][lz] = secondaryId;
                cache.macroBlend[lx][lz] = blendByte;
                cache.macro[lx][lz] = primaryId;
            }
        }
    }

    public SampleDual sampleDual(int x, int z) {
        NoiseSample ns = sampleNoise(x, z);
        MacroBiome primary = pickPrimary(x, z, ns.base, ns.latitude01);
        MacroBiome secondary = pickSecondary(x, z, ns.base, ns.latitude01);
        double blend = computeBlend(ns.base, ns.detail);
        return new SampleDual(primary, secondary, blend);
    }

    public static final class SampleDual {
        public final MacroBiome primary;
        public final MacroBiome secondary;
        public final double primaryWeight;

        private SampleDual(MacroBiome primary, MacroBiome secondary, double primaryWeight) {
            this.primary = primary;
            this.secondary = secondary;
            this.primaryWeight = primaryWeight;
        }
    }

    public double sampleContinentalness(int x, int z) {
        NoiseSample ns = sampleNoise(x, z);
        return clamp01(0.5D + ns.base * 0.5D);
    }

    private NoiseSample sampleNoise(int gx, int gz) {
        TileCache.Tile tile = tileCache.getOrBuild(gx, gz, this);
        int idx = tile.index(gx, gz);

        NoiseSample ns = scratchSample.get();
        ns.base = tile.base[idx];
        ns.detail = tile.detail[idx];
        ns.latitude01 = tile.latitude01[idx];
        return ns;
    }

    private MacroBiome pickPrimary(int gx, int gz, double baseNoise, double latitude01) {
        LatitudeBand band = findBand(latitude01);
        MacroBiome[] pool = band.pool;

        double selector = clamp01(0.5D + baseNoise * 0.5D);
        selector = softenSelector(selector);
        selector = applyMacroPlateBias(selector, gx, gz, pool.length, 0L);

        int idx = selectIndex(selector, pool.length);
        return pool[idx];
    }

    private MacroBiome pickSecondary(int gx, int gz, double baseNoise, double latitude01) {
        LatitudeBand band = findBand(latitude01);
        MacroBiome[] pool = band.pool;

        double selector = clamp01(0.5D + baseNoise * 0.5D + SECONDARY_OFFSET);
        selector = softenSelector(selector);
        selector = applyMacroPlateBias(selector, gx, gz, pool.length, 0x5F3564959E3779B9L);

        int idx = selectIndex(selector, pool.length);
        return pool[idx];
    }

    private double applyMacroPlateBias(double selector,
                                       int gx,
                                       int gz,
                                       int poolLength,
                                       long salt) {

        int cellX = Math.floorDiv(gx, MACRO_CELL_SIZE);
        int cellZ = Math.floorDiv(gz, MACRO_CELL_SIZE);

        long hash = mix64(worldSeed ^ salt ^ (cellX * 0x632BE5ABDCB5A641L) ^ (cellZ * 0x9E3779B185EBCA87L));
        double h01 = doubleFromHash(hash);

        double targetIdx = h01 * poolLength;
        double snappedCenter = (Math.floor(targetIdx) + 0.5D) / poolLength;

        return lerp(selector, snappedCenter, MACRO_PLATE_LOCK);
    }

    private static double softenSelector(double selector) {
        double width = MACRO_SELECTOR_SOFTNESS;
        if (width <= 0.0D) {
            return selector;
        }
        return selector * selector * (3.0D - 2.0D * selector);
    }

    private static int selectIndex(double selector, int poolLength) {
        int idx = (int) Math.floor(selector * poolLength);
        if (idx < 0) {
            idx = 0;
        } else if (idx >= poolLength) {
            idx = poolLength - 1;
        }
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

    private static double clampSigned(double v) {
        if (v < -1.0D) return -1.0D;
        if (v >  1.0D) return  1.0D;
        return v;
    }

    public static double clamp01(double x) {
        if (x < 0.0D) return 0.0D;
        if (x > 1.0D) return 1.0D;
        return x;
    }

    private static final class NoiseSample {
        double base;
        double detail;
        double latitude01;
    }

    public MacroBiome getMacroBiome(int x, int z) {
        SampleDual sample = sampleDual(x, z);
        return (sample != null && sample.primary != null)
            ? sample.primary
            : MacroBiome.PLAINS_TEMPERATE;
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

            Tile(int originX, int originZ) {
                this.originX = originX;
                this.originZ = originZ;
                int size = TILE_SIZE * TILE_SIZE;
                this.base = new double[size];
                this.detail = new double[size];
                this.latitude01 = new double[size];
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
            Tile result = existing == null ? fresh : existing;

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
                if (tail == null) break;
                tiles.remove(tail);
            }
        }
    }

    private void populateTile(TileCache.Tile tile) {
        for (int dz = 0; dz < TILE_SIZE; dz++) {
            int gz = tile.originZ + dz;
            for (int dx = 0; dx < TILE_SIZE; dx++) {
                int gx = tile.originX + dx;

                double coarse = baseNoiseCoarse.noise(gx * baseCoarseScale, gz * baseCoarseScale);
                double fine   = baseNoiseFine.noise(gx * macroScale, gz * macroScale);
                double blended = coarse * baseCoarseWeight + fine * baseFineWeight;

                double detail = detailNoise.noise(gx * detailScale, gz * detailScale);

                double latitudeTerm = (latitudePeriod != 0.0)
                    ? Math.cos(Math.PI * (gz / latitudePeriod))
                    : 0.0;
                latitudeTerm = clampSigned(latitudeTerm * latitudeBiasStrength + latitudeBaseBias);

                double base = blended * (1.0 - latitudeMixWeight)
                    + latitudeTerm * latitudeMixWeight;
                base = clampSigned(base);

                double latitude01 = clamp01(0.5 + latitudeTerm * 0.5);

                int idx = dz * TILE_SIZE + dx;
                tile.base[idx] = base;
                tile.detail[idx] = detail;
                tile.latitude01[idx] = latitude01;
            }
        }
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
                                 int detailOctaves) {

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
        }

        public static MacroBiomeConfig defaultPreset() {
            return PRESET_BASE;
        }

        public static MacroBiomeConfig preset(String name) {
            if (name == null) return PRESET_BASE;
            return switch (name.toLowerCase(Locale.ROOT)) {
                case "lean-cool" -> PRESET_LEAN_COOL;
                case "chunky-warm" -> PRESET_CHUNKY_WARM;
                case "lat-strong" -> PRESET_LAT_STRONG;
                case "lat-very-strong" -> PRESET_LAT_VERY_STRONG;
                default -> PRESET_BASE;
            };
        }

        private static final MacroBiomeConfig PRESET_BASE = new MacroBiomeConfig(
            1.0D / 6000.0D,
            0.45D,
            3.5D,
            0.65D,
            50000.0D,
            0.55D,
            0.25D,
            0.00D,
            0.35D,
            0.20D,
            2,
            2
        );

        private static final MacroBiomeConfig PRESET_LEAN_COOL = new MacroBiomeConfig(
            1.0D / 6500.0D,
            0.40D,
            3.2D,
            0.70D,
            50000.0D,
            0.60D,
            0.30D,
            -0.02D,
            0.30D,
            0.22D,
            2,
            2
        );

        private static final MacroBiomeConfig PRESET_CHUNKY_WARM = new MacroBiomeConfig(
            1.0D / 5500.0D,
            0.50D,
            4.0D,
            0.60D,
            50000.0D,
            0.50D,
            0.20D,
            +0.02D,
            0.40D,
            0.18D,
            2,
            2
        );

        private static final MacroBiomeConfig PRESET_LAT_STRONG = new MacroBiomeConfig(
            1.0D / 6000.0D,
            0.45D,
            3.5D,
            0.65D,
            50000.0D,
            0.90D,
            0.60D,
            0.00D,
            0.35D,
            0.22D,
            2,
            2
        );

        private static final MacroBiomeConfig PRESET_LAT_VERY_STRONG = new MacroBiomeConfig(
            1.0D / 6000.0D,
            0.45D,
            3.5D,
            0.65D,
            50000.0D,
            1.10D,
            0.70D,
            -0.02D,
            0.35D,
            0.25D,
            2,
            2
        );
    }

}
