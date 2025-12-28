package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver.Talos2BiomeResolver;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.Collections;
import java.util.List;

public class ChunkProviderTalos2 extends ChunkProviderSpaceLakes {

    private static final MacroBiome[] MACRO_VALUES = MacroBiome.values();

    private final CoastlineAtlas coastlineAtlas;
    private final World world;

    private final SimplexNoiseOctave terrainNoise;
    private final MacroBiomeField macroBiomeField;

    private final MacroBiomeSelector macroSelector;

    private final Talos2ClimateSampler surfaceClimateSampler;
    private final Talos2BiomeResolver biomeResolver;

    private static final BlockMetaPair SNOW_SURFACE = new BlockMetaPair(Blocks.snow, (byte) 0);
    private static final BlockMetaPair PACKED_ICE = new BlockMetaPair(Blocks.packed_ice, (byte) 0);
    private static final BlockMetaPair SANDSTONE_FILL = new BlockMetaPair(Blocks.sandstone, (byte) 0);
    private static final BlockMetaPair MYCELIUM_TOP = new BlockMetaPair(Blocks.mycelium, (byte) 0);

    private static final boolean TALOS_TIMING = false;
    private static final int SLOW_CHUNK_MS = 80;
    private static final int VERY_SLOW_CHUNK_MS = 300;

    private static long now() { return System.nanoTime(); }
    private static long ms(long nanos) { return nanos / 1_000_000L; }

    private static void logChunkTiming(int chunkX, int chunkZ,
                                       long tTotal,
                                       long tClear, long tCache,
                                       long tBase, long tSmooth, long tClamp,
                                       long tFill, long tClean, long tBedrock) {
        long totalMs = ms(tTotal);
        if (!TALOS_TIMING || totalMs < SLOW_CHUNK_MS) return;

        String msg = "[Talos2] chunk=(" + chunkX + "," + chunkZ + ") total=" + totalMs + "ms"
            + " clear=" + ms(tClear)
            + " shoreCache=" + ms(tCache)
            + " baseHM=" + ms(tBase)
            + " smooth=" + ms(tSmooth)
            + " clamp=" + ms(tClamp)
            + " fill=" + ms(tFill)
            + " clean=" + ms(tClean)
            + " bedrock=" + ms(tBedrock);

        System.out.println(msg);

        if (totalMs >= VERY_SLOW_CHUNK_MS) {
            System.out.println("[Talos2] VERY SLOW chunk stacktrace:");
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                System.out.println("    at " + e);
            }
        }
    }

    public ChunkProviderTalos2(World world, long seed, boolean mapFeaturesEnabled) {
        super(world, seed, mapFeaturesEnabled);
        this.world = world;

        Talos2Hooks.HookData hook = Talos2Hooks.resolve(world);

        MacroBiomeField.MacroBiomeConfig macroConfig =
            (hook != null && hook.macroConfig != null)
                ? hook.macroConfig
                : Talos2NoiseConfig.currentMacroConfig();

        this.macroSelector = new MacroBiomeSelector(seed);

        if (hook != null && hook.macroField != null && hook.coastlineAtlas != null) {
            this.macroBiomeField = hook.macroField;
            this.coastlineAtlas = hook.coastlineAtlas;
        } else {
            this.macroBiomeField = new MacroBiomeField(seed, macroConfig);
            this.coastlineAtlas = new DefaultCoastlineAtlas(this.macroBiomeField, seed);
            Talos2Hooks.register(
                world.provider.dimensionId,
                world,
                seed,
                (DefaultCoastlineAtlas) this.coastlineAtlas,
                this.macroBiomeField,
                macroConfig
            );
        }

        this.terrainNoise = new SimplexNoiseOctave(seed ^ 0x1234ABCDL, 4);
        this.surfaceClimateSampler = new Talos2ClimateSampler(world, this.macroBiomeField);
        this.biomeResolver = new Talos2BiomeResolver(world, this.macroBiomeField);
    }

    @Override
    public String makeString() {
        return "Talos2Source_New";
    }

    @Override
    protected BiomeDecoratorSpace getBiomeGenerator() {
        return new BiomeDecoratorTalos2();
    }

    @Override
    protected BiomeGenBase[] getBiomesForGeneration() {
        return new BiomeGenBase[]{
            TalosBiomes.TALOS_OCEAN,
            TalosBiomes.TALOS_BEACH,
            TalosBiomes.TALOS_PLAINS,
            TalosBiomes.TALOS_SHELF,
            TalosBiomes.TALOS_DESERT,
        };
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        final long t0 = now();

        long tClear = 0, tCache = 0, tBase = 0, tSmooth = 0, tClamp = 0, tFill = 0, tClean = 0, tBed = 0;

        long a = now();
        clearChunkBlocks(blocks, meta);
        tClear = now() - a;

        a = now();
        ChunkShoreCache shore = new ChunkShoreCache();
        buildShoreCache(chunkX, chunkZ, shore);
        tCache = now() - a;

        a = now();
        int[][] hm = computeBaseHeightMap(chunkX, chunkZ, shore);
        tBase = now() - a;

        a = now();
        smoothHeightMap(hm);
        tSmooth = now() - a;

        a = now();
        applyDistHeightConstraints(hm, shore);
        tClamp = now() - a;

        a = now();
        fillBlocksFromHeightMap(blocks, meta, hm, chunkX, chunkZ, shore);
        tFill = now() - a;

        a = now();
        strongCleanOceanFloor(blocks, meta);
        extendLandEdgesDown(blocks, meta);
        tClean = now() - a;

        a = now();
        applyBedrockFloor(blocks, meta, chunkX, chunkZ);
        tBed = now() - a;

        long tTotal = now() - t0;
        logChunkTiming(chunkX, chunkZ, tTotal, tClear, tCache, tBase, tSmooth, tClamp, tFill, tClean, tBed);
    }

    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i] = 0;
        }
    }

    private int[][] computeBaseHeightMap(int chunkX, int chunkZ, ChunkShoreCache shore) {
        final int SIZE = 17;
        final int worldHeight = 256;

        BiomeGenTalos2Ocean oceanBiome = TalosBiomes.TALOS_OCEAN;
        BiomeGenTalos2Beach beachBiome = TalosBiomes.TALOS_BEACH;
        BiomeGenTalos2Shelf shelfBiome = TalosBiomes.TALOS_SHELF;

        final double DEEP_MIN      = oceanBiome.deepMin;
        final double DEEP_MAX      = oceanBiome.deepMax;
        final double SHELF_TOP_MIN = shelfBiome.shelfTopMin;
        final double SHELF_TOP_MAX = shelfBiome.shelfTopMax;

        final double BEACH_MIN = beachBiome.beachMin;
        final double BEACH_MAX = beachBiome.beachMax;

        final double detailScale = 0.0025D;

        final int BLEND_BLOCKS = 8;
        final int INLAND_RAMP_BLOCKS = 24;

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int macroPrimaryId = shore.macroPrimary[localX][localZ] & 0xFF;
                int macroSecondaryId = shore.macroSecondary[localX][localZ] & 0xFF;
                double macroBlend = (shore.macroBlend[localX][localZ] & 0xFF) / 255.0;

                MacroBiome macroPrimary = MACRO_VALUES[macroPrimaryId];
                MacroBiome macroSecondary = MACRO_VALUES[macroSecondaryId];

                if (macroPrimary == null) macroPrimary = MacroBiome.PLAINS_TEMPERATE;
                if (macroSecondary == null) macroSecondary = macroPrimary;

                MacroHeightSample heightSample = blendHeightProfiles(macroPrimary, macroSecondary, macroBlend);

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                boolean isLand = shore.isLand[localX][localZ];
                int dist = shore.dist[localX][localZ] & 0xFFFF;

                int beachW = shore.beachW[localX][localZ] & 0xFFFF;
                int shelfW = shore.shelfW[localX][localZ] & 0xFFFF;

                boolean isDesert = isLand && dist > beachW &&
                    (macroPrimary == MacroBiome.WARM_DRY ||
                        (macroSecondary == MacroBiome.WARM_DRY && macroBlend < 0.45));

                double d = clamp01(sampleTerrain01(gx, gz, detailScale));

                double hDeep = DEEP_MIN + d * (DEEP_MAX - DEEP_MIN);
                double shelfTop = SHELF_TOP_MIN + d * (SHELF_TOP_MAX - SHELF_TOP_MIN);

                double tShelfNear = 1.0D - clamp01(dist / (double) Math.max(1, shelfW));
                tShelfNear = smooth01(tShelfNear);
                double hShelfOnly = lerp(hDeep, shelfTop, tShelfNear);

                double hMacroBase = sampleBlendedMacroHeight(gx, gz, d, heightSample);
                double hPlainsOnly = computePlainsHeightNearCoast_DIST(
                    gx, gz, dist, beachW, d, hMacroBase, BEACH_MAX, INLAND_RAMP_BLOCKS
                );

                double hDesertOnly = hPlainsOnly;
                if (isDesert) {
                    hDesertOnly = computeDesertHeight(
                        gx, gz, d,
                        heightSample.min,
                        heightSample.max
                    );
                }

                double beach01 = clamp01(dist / (double) Math.max(1, beachW));
                double hBeachOnly = computeBeachHeight01(beach01, d, hPlainsOnly, BEACH_MIN, BEACH_MAX);
                if (hBeachOnly < BEACH_MIN) hBeachOnly = BEACH_MIN;
                if (hBeachOnly > BEACH_MAX) hBeachOnly = BEACH_MAX;

                double h;
                if (!isLand) {
                    double t = smoothstep(shelfW - BLEND_BLOCKS, shelfW + BLEND_BLOCKS, dist);
                    h = lerp(hShelfOnly, hDeep, t);
                } else {
                    double inlandTarget = isDesert ? hDesertOnly : hPlainsOnly;
                    double t = smoothstep(beachW - BLEND_BLOCKS, beachW + BLEND_BLOCKS, dist);
                    h = lerp(hBeachOnly, inlandTarget, t);
                }

                int ih = (int) Math.floor(h);
                if (ih < 4) ih = 4;
                if (ih > worldHeight - 4) ih = worldHeight - 4;
                heightMap[localX][localZ] = ih;
            }
        }

        return heightMap;
    }

    private void smoothHeightMap(int[][] heightMap) {
        final int SIZE = 17;
        int[][] out = new int[SIZE][SIZE];

        int[] buf = new int[9];

        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                int n = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx < 0 || nx > 16 || nz < 0 || nz > 16) continue;
                        buf[n++] = heightMap[nx][nz];
                    }
                }
                java.util.Arrays.sort(buf, 0, n);
                out[x][z] = buf[n / 2];
            }
        }

        for (int x = 0; x <= 16; x++) {
            System.arraycopy(out[x], 0, heightMap[x], 0, SIZE);
        }
    }

    private void applyDistHeightConstraints(int[][] hm, ChunkShoreCache shore) {
        final int SIZE = 17;

        int waterLevel = this.getWaterLevel();

        MacroBiome.MacroHeightProfile beachProfile = MacroBiome.COASTAL.height;
        int beachMinY = (int) Math.round(Math.max(waterLevel, beachProfile.absoluteMin));
        int beachMaxY = (int) Math.round(beachProfile.absoluteMax) + 1;

        for (int lx = 0; lx < SIZE; lx++) {
            for (int lz = 0; lz < SIZE; lz++) {

                int macroPrimaryId  = shore.macroPrimary[lx][lz] & 0xFF;
                int macroSecondaryId = shore.macroSecondary[lx][lz] & 0xFF;
                double macroBlend = (shore.macroBlend[lx][lz] & 0xFF) / 255.0;

                MacroBiome macroPrimary = MACRO_VALUES[macroPrimaryId];
                MacroBiome macroSecondary = MACRO_VALUES[macroSecondaryId];

                if (macroPrimary == null) macroPrimary = MacroBiome.PLAINS_TEMPERATE;
                if (macroSecondary == null) macroSecondary = macroPrimary;

                MacroHeightSample heightSample = blendHeightProfiles(macroPrimary, macroSecondary, macroBlend);

                if (!shore.isLand[lx][lz]) continue;

                int dist = shore.dist[lx][lz] & 0xFFFF;
                int beachW = shore.beachW[lx][lz] & 0xFFFF;

                boolean isDesert = dist > beachW &&
                    (macroPrimary == MacroBiome.WARM_DRY ||
                        (macroSecondary == MacroBiome.WARM_DRY && macroBlend < 0.45));

                int y = hm[lx][lz];

                if (dist <= beachW) {
                    if (y < beachMinY) y = beachMinY;
                    if (y > beachMaxY) y = beachMaxY;
                } else if (isDesert) {
                    int desertMinY = (int) Math.floor(heightSample.min);
                    int desertMaxY = (int) Math.ceil(heightSample.max);
                    if (y < desertMinY) y = desertMinY;
                    if (y > desertMaxY) y = desertMaxY;
                } else {
                    int inlandMinY = (int) Math.floor(heightSample.min);
                    if (y < inlandMinY) y = inlandMinY;
                }

                hm[lx][lz] = y;
            }
        }
    }

    private void fillBlocksFromHeightMap(
        Block[] blocks,
        byte[] meta,
        int[][] heightMap,
        int chunkX,
        int chunkZ,
        ChunkShoreCache shore) {

        final int worldHeight = 256;
        final int CHUNK_SIZE = 16;

        int waterLevel = this.getWaterLevel();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                int columnBase = (localX * 16 + localZ) * worldHeight;
                int groundHeight = heightMap[localX][localZ];

                boolean isLand = shore.isLand[localX][localZ];
                int dist = shore.dist[localX][localZ] & 0xFFFF;

                int beachW = shore.beachW[localX][localZ] & 0xFFFF;
                int shelfW = shore.shelfW[localX][localZ] & 0xFFFF;

                BiomeGenBase baseBiome;
                Talos2ClimateSampler.ClimateSample climateSample = null;

                if (!isLand) {
                    baseBiome = (dist <= shelfW) ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
                } else if (dist <= beachW) {
                    baseBiome = TalosBiomes.TALOS_BEACH;
                } else {
                    baseBiome = biomeResolver.resolve(gx, gz);
                    climateSample = surfaceClimateSampler.sample(gx, gz);

                    if (baseBiome == null) {
                        int macroPrimaryId = shore.macroPrimary[localX][localZ] & 0xFF;
                        int macroSecondaryId = shore.macroSecondary[localX][localZ] & 0xFF;
                        double macroBlend = (shore.macroBlend[localX][localZ] & 0xFF) / 255.0;
                        MacroBiome macroPrimary = MACRO_VALUES[macroPrimaryId];
                        MacroBiome macroSecondary = MACRO_VALUES[macroSecondaryId];
                        baseBiome = macroSelector.pick(gx, gz, macroPrimary, macroSecondary, macroBlend);
                    }
                }

                if (baseBiome == TalosBiomes.TALOS_OCEAN) {
                    fillOceanColumn(blocks, meta, columnBase, groundHeight, waterLevel);
                } else if (baseBiome == TalosBiomes.TALOS_SHELF) {
                    fillShelfColumn(blocks, meta, columnBase, groundHeight, waterLevel,
                        (BiomeGenTalos2Shelf) baseBiome);
                } else if (baseBiome == TalosBiomes.TALOS_BEACH) {
                    fillBeachColumn(blocks, meta, columnBase, groundHeight, waterLevel,
                        (BiomeGenTalos2Beach) baseBiome);
                } else {
                    buildColumnForBiome(blocks, meta, columnBase, groundHeight, waterLevel,
                        baseBiome, climateSample);
                }
            }
        }
    }

    private void strongCleanOceanFloor(Block[] blocks, byte[] meta) {
        final int H = 256;
        final int SEA = this.getWaterLevel();
        BlockMetaPair stone = this.getStoneBlock();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int base = (lx * 16 + lz) * H;

                boolean seenWaterAbove = false;

                for (int y = SEA; y >= 0; y--) {
                    int idx = base + y;
                    Block b = blocks[idx];

                    if (b == null || b == Blocks.air) continue;

                    if (b == Blocks.water) {
                        seenWaterAbove = true;
                        continue;
                    }

                    if (!seenWaterAbove) continue;

                    if (b == Blocks.grass || b == Blocks.dirt || b == Blocks.gravel) {
                        blocks[idx] = stone.getBlock();
                        meta[idx] = stone.getMetadata();
                    }
                }
            }
        }
    }

    private void extendLandEdgesDown(Block[] blocks, byte[] meta) {
        final int worldHeight = 256;
        final int waterLevel = this.getWaterLevel();

        BlockMetaPair stone = this.getStoneBlock();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

                int columnBase = (localX * 16 + localZ) * worldHeight;

                int topSolidBelowWater = -1;
                for (int y = waterLevel; y >= 1; y--) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b != null && b != Blocks.air && b != Blocks.water) {
                        topSolidBelowWater = y;
                        break;
                    }
                }
                if (topSolidBelowWater < 0) continue;

                boolean isCoastColumn = false;
                for (int y = waterLevel + 1; y <= waterLevel + 6 && y < worldHeight; y++) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b == Blocks.grass || b == Blocks.dirt || b == Blocks.stone || b == Blocks.sand) {
                        isCoastColumn = true;
                        break;
                    }
                }
                if (!isCoastColumn) continue;

                int targetDepth = Math.max(4, topSolidBelowWater - 10);
                for (int y = topSolidBelowWater - 1; y >= targetDepth; y--) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b == null || b == Blocks.air || b == Blocks.water) {
                        blocks[idx] = stone.getBlock();
                        meta[idx] = stone.getMetadata();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private void applyBedrockFloor(Block[] blocks, byte[] meta, int chunkX, int chunkZ) {
        final int H = 256;
        final int LAYERS = 5;

        java.util.Random r = new java.util.Random(
            (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + this.world.getSeed()
        );

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int base = (lx * 16 + lz) * H;

                for (int y = 0; y < LAYERS; y++) {
                    if (y == 0 || r.nextInt(LAYERS) > y) {
                        blocks[base + y] = Blocks.bedrock;
                        meta[base + y] = 0;
                    }
                }
            }
        }
    }

    private void buildShoreCache(int chunkX, int chunkZ, ChunkShoreCache out) {
        final int SIZE = 17;

        this.macroBiomeField.sample(chunkX, chunkZ, out);

        for (int lx = 0; lx < SIZE; lx++) {
            for (int lz = 0; lz < SIZE; lz++) {

                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                int macroId = out.macroPrimary[lx][lz] & 0xFF;
                MacroBiome macro = MACRO_VALUES[macroId];
                if (macro == null) {
                    macro = MacroBiome.PLAINS_TEMPERATE;
                }

                boolean isLand = this.coastlineAtlas.isLand(gx, gz);
                int distRaw = this.coastlineAtlas.distanceToCoast(gx, gz);
                int beachWidthRaw = this.coastlineAtlas.beachWidth(gx, gz, macro);
                int shelfWidthRaw = this.coastlineAtlas.shelfWidth(gx, gz, macro);

                int dist = distRaw;
                if (dist < 0) dist = 0;
                if (dist > 65535) dist = 65535;

                int beachW = beachWidthRaw;
                if (beachW < 0) beachW = 0;
                if (beachW > 65535) beachW = 65535;

                int shelfW = shelfWidthRaw;
                if (shelfW < 0) shelfW = 0;
                if (shelfW > 65535) shelfW = 65535;

                out.isLand[lx][lz] = isLand;
                out.dist[lx][lz] = (short) dist;
                out.beachW[lx][lz] = (short) beachW;
                out.shelfW[lx][lz] = (short) shelfW;
            }
        }
    }

    private double computeDesertHeight(int gx, int gz, double baseNoise01, double min, double max) {
        double dunes = sampleTerrain01(gx, gz, 0.0065D);
        double mesas = sampleTerrain01(gx, gz, 0.0012D);
        double blend = clamp01(0.55D * baseNoise01 + 0.30D * dunes + 0.15D * mesas);

        double height = min + blend * (max - min);

        double ripples = sampleTerrain01(gx + 4000, gz - 4000, 0.0120D);
        height += (ripples - 0.5D) * 2.5D;

        return clamp(height, min, max);
    }

    public static final class ChunkShoreCache {
        public final boolean[][] isLand = new boolean[17][17];
        public final short[][] dist = new short[17][17];
        public final short[][] beachW = new short[17][17];
        public final short[][] shelfW = new short[17][17];
        public final byte[][] macro = new byte[17][17];

        public final byte[][] macroPrimary = new byte[17][17];
        public final byte[][] macroSecondary = new byte[17][17];
        public final byte[][] macroBlend = new byte[17][17];
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    private static double clamp(double v, double min, double max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double smooth01(double x) {
        x = clamp01(x);
        return x * x * (3.0D - 2.0D * x);
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = (x - e0) / (e1 - e0);
        t = clamp01(t);
        return t * t * (3.0D - 2.0D * t);
    }

    private double sampleTerrain01(int gx, int gz, double scale) {
        double sx = gx * scale;
        double sz = gz * scale;

        final double COS = 0.8660254037844386;
        final double SIN = 0.5;
        double rx = sx * COS - sz * SIN;
        double rz = sx * SIN + sz * COS;

        rx += 1000.0;
        rz -= 1000.0;

        double raw = this.terrainNoise.noise(rx, rz);
        return (raw + 1.0D) * 0.5D;
    }

    private double computeBeachHeight01(
        double beach01,
        double d,
        double hPlainsRef,
        double beachMin, double beachMax) {

        double t = clamp01(beach01);
        t = t * t * (3.0D - 2.0D * t);

        double base = beachMin * (1.0D - t) + beachMax * t;

        double micro = (d - 0.5D) * 1.2D;
        double h = base + micro;

        double min = beachMin - 0.3D;
        double max = beachMax + 0.3D;
        if (h < min) h = min;
        if (h > max) h = max;

        double edgeBandFrac = 0.15D;
        double bandStart = 1.0D - edgeBandFrac;
        double te = (t - bandStart) / (1.0D - bandStart);
        te = clamp01(te);

        double maxDelta = 2.0D;
        double targetPlain = hPlainsRef;
        if (targetPlain > h + maxDelta) targetPlain = h + maxDelta;
        if (targetPlain < h - maxDelta) targetPlain = h - maxDelta;

        return h * (1.0D - te) + targetPlain * te;
    }

    private double computePlainsHeightNearCoast_DIST(
        int gx,
        int gz,
        int distToCoast,
        int beachWidthBlocks,
        double d,
        double hPlains,
        double beachMax,
        int inlandRampBlocks
    ) {
        double x = distToCoast - beachWidthBlocks;
        double tInland = smoothstep(0.0D, (double) inlandRampBlocks, x);

        double targetBeach = beachMax - (1.0D - tInland) * 0.3D;

        double nLow = sampleTerrain01(gx, gz, 0.00055D);
        double nMid = sampleTerrain01(gx, gz, 0.00180D);
        double nHi  = d;

        double hill =
            (nLow - 0.5D) * 16.0D +
                (nMid - 0.5D) * 6.0D +
                (nHi  - 0.5D) * 1.2D;

        hill *= tInland;

        double basePlain = hPlains + hill;

        double maxDeltaNear = 4.0D;
        double maxDeltaFar  = 30.0D;

        double tt = tInland * tInland;
        double maxDelta = lerp(maxDeltaNear, maxDeltaFar, tt);

        double cap = targetBeach + maxDelta;
        if (basePlain > cap) basePlain = cap;

        return targetBeach * (1.0D - tInland) + basePlain * tInland;
    }

    private double sampleBlendedMacroHeight(int gx, int gz,
                                            double noise01,
                                            MacroHeightSample sample) {

        double absMin = sample.min;
        double absMax = sample.max;

        double base = absMin + noise01 * (absMax - absMin);
        double extra = (noise01 - 0.5D) * sample.variation * 32.0D;
        double offset = sample.offset * 16.0D;

        double value = base + extra + offset;
        return clamp(value, Math.min(absMin, absMax), Math.max(absMin, absMax));
    }

    private void buildColumnForBiome(
        Block[] blocks, byte[] meta,
        int columnBase, int groundHeight, int waterLevel,
        BiomeGenBase biome,
        Talos2ClimateSampler.ClimateSample climateSample
    ) {
        BlockMetaPair surface = getSurfaceBlock(biome);
        BlockMetaPair filler = getFillerBlock(biome);
        BlockMetaPair stone = getStoneBlockForBiome(biome);
        BlockMetaPair water = this.getWaterBlock();

        if (climateSample != null) {
            double temp = climateSample.temperature;
            double humid = climateSample.humidity;

            if (temp < 0.16D) {
                surface = SNOW_SURFACE;
                filler = PACKED_ICE;
            } else if (temp > 0.82D && humid < 0.35D) {
                surface = getSandBlock();
                filler = SANDSTONE_FILL;
            } else if (humid > 0.78D && temp > 0.55D) {
                surface = MYCELIUM_TOP;
                filler = this.getDirtBlock();
            }
        }

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            BlockMetaPair pair;

            if (y == groundHeight) {
                pair = surface;
            } else if (y >= groundHeight - 3) {
                pair = (filler != null) ? filler : surface;
            } else {
                pair = (stone != null) ? stone : this.getStoneBlock();
            }

            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private static final class MacroHeightSample {
        final double min;
        final double max;
        final double offset;
        final double variation;

        MacroHeightSample(double min, double max, double offset, double variation) {
            this.min = min;
            this.max = max;
            this.offset = offset;
            this.variation = variation;
        }
    }

    private MacroHeightSample blendHeightProfiles(MacroBiome primary,
                                                  MacroBiome secondary,
                                                  double blendPrimary) {

        MacroBiome.MacroHeightProfile hpA = primary.height;
        MacroBiome.MacroHeightProfile hpB = secondary.height;

        double t = clamp01(blendPrimary);
        double invT = 1.0 - t;

        double min = hpA.absoluteMin * t + hpB.absoluteMin * invT;
        double max = hpA.absoluteMax * t + hpB.absoluteMax * invT;
        double offset = hpA.baseHeightOffset * t + hpB.baseHeightOffset * invT;
        double variation = hpA.heightVariation * t + hpB.heightVariation * invT;

        return new MacroHeightSample(min, max, offset, variation);
    }

    private BlockMetaPair getSurfaceBlock(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs) {
            Block top = gs.topBlock != null ? gs.topBlock : Blocks.grass;
            byte meta = gs.topMeta;
            return new BlockMetaPair(top, meta);
        }
        return this.getGrassBlock();
    }

    private BlockMetaPair getFillerBlock(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs) {
            Block filler = gs.fillerBlock != null ? gs.fillerBlock : Blocks.dirt;
            byte meta = gs.fillerMeta;
            return new BlockMetaPair(filler, meta);
        }
        return this.getDirtBlock();
    }

    private BlockMetaPair getStoneBlockForBiome(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs && gs.stoneBlock != null) {
            return new BlockMetaPair(gs.stoneBlock, gs.stoneMeta);
        }
        return this.getStoneBlock();
    }

    private void fillOceanColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel) {

        BiomeGenTalos2Ocean biome = TalosBiomes.TALOS_OCEAN;
        BlockMetaPair stone = biome.bottomBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            blocks[idx] = stone.getBlock();
            meta[idx] = stone.getMetadata();
        }
        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private void fillShelfColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel,
                                 BiomeGenTalos2Shelf biome) {

        BlockMetaPair top = biome.surfaceBlock;
        BlockMetaPair shelf = biome.shelfBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            BlockMetaPair pair = (y == groundHeight) ? top : shelf;
            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private void fillBeachColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel,
                                 BiomeGenTalos2Beach biome) {

        BlockMetaPair sand = biome.surfaceBlock;
        BlockMetaPair dirt = biome.fillerBlock;
        BlockMetaPair stone = biome.stoneBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;

            BlockMetaPair pair;
            if (y == groundHeight || y == groundHeight - 1) {
                pair = sand;
            } else if (y >= groundHeight - 4) {
                pair = dirt;
            } else {
                pair = stone;
            }

            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel - 2) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    @Override
    public void onPopulate(IChunkProvider provider, int x, int z) {

    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getMonsters() {
        return new BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getCreatures() {
        return new BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getWaterCreatures() {
        return new BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected List<MapGenBaseMeta> getWorldGenerators() {
        return Collections.emptyList();
    }

    @Override
    public double getHeightModifier() {
        return 15.0;
    }

    @Override
    public int getWaterLevel() {
        return 64;
    }

    @Override
    public boolean canGenerateWaterBlock() {
        return true;
    }

    @Override
    public boolean canGenerateIceBlock() {
        return false;
    }

    @Override
    protected BlockMetaPair getWaterBlock() {
        return new BlockMetaPair(Blocks.water, (byte) 0);
    }

    @Override
    protected BlockMetaPair getGrassBlock() {
        return new BlockMetaPair(Blocks.grass, (byte) 0);
    }

    @Override
    protected BlockMetaPair getDirtBlock() {
        return new BlockMetaPair(Blocks.dirt, (byte) 0);
    }

    @Override
    protected BlockMetaPair getStoneBlock() {
        return new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    protected BlockMetaPair getSandBlock() {
        return new BlockMetaPair(Blocks.sand, (byte) 0);
    }

    @Override
    protected boolean enableBiomeGenBaseBlock() {
        return false;
    }
}
