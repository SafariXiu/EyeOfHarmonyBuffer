package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
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

    private final World world;
    private final SimplexNoiseOctave continentNoise;
    private final SimplexNoiseOctave terrainNoise;
    private final MacroBiomeField macroBiomeField;
    private final CoastWidthField coastWidthField;

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

        this.macroBiomeField = new MacroBiomeField(seed);
        this.coastWidthField = new CoastWidthField(seed);
        this.terrainNoise = new SimplexNoiseOctave(seed ^ 0x1234ABCDL, 4);
        this.continentNoise = new SimplexNoiseOctave(
            seed ^ Talos2Continent.CONTINENT_SALT,
            Talos2Continent.CONTINENT_OCTAVES
        );
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

        BiomeGenTalos2Ocean oceanBiome   = TalosBiomes.TALOS_OCEAN;
        BiomeGenTalos2Beach beachBiome   = TalosBiomes.TALOS_BEACH;
        BiomeGenTalos2Plains plainsBiome = TalosBiomes.TALOS_PLAINS;
        BiomeGenTalos2Shelf shelfBiome   = TalosBiomes.TALOS_SHELF;

        final double DEEP_MIN      = oceanBiome.deepMin;
        final double DEEP_MAX      = oceanBiome.deepMax;
        final double SHELF_TOP_MIN = shelfBiome.shelfTopMin;
        final double SHELF_TOP_MAX = shelfBiome.shelfTopMax;

        final double BEACH_MIN = beachBiome.beachMin;
        final double BEACH_MAX = beachBiome.beachMax;

        final double PLAIN_MIN = plainsBiome.plainMin;
        final double PLAIN_MAX = plainsBiome.plainMax;

        final double detailScale = 0.0025D;

        final int BLEND_BLOCKS = 8;
        final int INLAND_RAMP_BLOCKS = 24;

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                boolean isLand = shore.isLand[localX][localZ];
                int dist = shore.dist[localX][localZ] & 0xFFFF;

                int beachW = shore.beachW[localX][localZ] & 0xFFFF;
                int shelfW = shore.shelfW[localX][localZ] & 0xFFFF;

                double d = sampleTerrain01(gx, gz, detailScale);
                d = clamp01(d);

                double hDeep = DEEP_MIN + d * (DEEP_MAX - DEEP_MIN);
                double shelfTop = SHELF_TOP_MIN + d * (SHELF_TOP_MAX - SHELF_TOP_MIN);

                double tShelfNear = 1.0D - clamp01(dist / (double) shelfW);
                tShelfNear = smooth01(tShelfNear);
                double hShelfOnly = lerp(hDeep, shelfTop, tShelfNear);

                double hPlainsBase = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);
                double hPlainsOnly = computePlainsHeightNearCoast_DIST(
                    gx, gz, dist, beachW, d, hPlainsBase, BEACH_MAX, INLAND_RAMP_BLOCKS
                );

                double beach01 = clamp01(dist / (double) beachW);
                double hBeachOnly = computeBeachHeight01(beach01, d, hPlainsOnly, BEACH_MIN, BEACH_MAX);

                if (hBeachOnly < BEACH_MIN) hBeachOnly = BEACH_MIN;
                if (hBeachOnly > BEACH_MAX) hBeachOnly = BEACH_MAX;

                double h;
                if (!isLand) {
                    double t = smoothstep(shelfW - BLEND_BLOCKS, shelfW + BLEND_BLOCKS, dist);
                    h = lerp(hShelfOnly, hDeep, t);
                } else {
                    double t = smoothstep(beachW - BLEND_BLOCKS, beachW + BLEND_BLOCKS, dist);
                    h = lerp(hBeachOnly, hPlainsOnly, t);
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

        BiomeGenTalos2Beach beachBiome = TalosBiomes.TALOS_BEACH;
        int beachMinY = (int) Math.round(Math.max(waterLevel, beachBiome.beachMin));
        int beachMaxY = (int) Math.round(beachBiome.beachMax) + 1; // tolerance

        final int PLAIN_MIN_Y = 67;

        for (int lx = 0; lx < SIZE; lx++) {
            for (int lz = 0; lz < SIZE; lz++) {

                if (!shore.isLand[lx][lz]) continue;

                int dist = shore.dist[lx][lz] & 0xFFFF;
                int beachW = shore.beachW[lx][lz] & 0xFFFF;

                int y = hm[lx][lz];

                if (dist <= beachW) {
                    if (y < beachMinY) y = beachMinY;
                    if (y > beachMaxY) y = beachMaxY;
                } else {
                    if (y < PLAIN_MIN_Y) y = PLAIN_MIN_Y;
                }

                hm[lx][lz] = y;
            }
        }
    }

    private void fillBlocksFromHeightMap(
        Block[] blocks,
        byte[] meta,
        int[][] heightMap,      // 17x17
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

                int groundHeight = heightMap[localX][localZ];
                int columnBase = (localX * 16 + localZ) * worldHeight;

                boolean isLand = shore.isLand[localX][localZ];
                int dist = shore.dist[localX][localZ] & 0xFFFF;

                int beachW = shore.beachW[localX][localZ] & 0xFFFF;
                int shelfW = shore.shelfW[localX][localZ] & 0xFFFF;

                BiomeGenBase baseBiome;
                if (!isLand) {
                    baseBiome = (dist <= shelfW) ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
                } else {
                    baseBiome = (dist <= beachW) ? TalosBiomes.TALOS_BEACH : TalosBiomes.TALOS_PLAINS;
                }

                if (baseBiome == TalosBiomes.TALOS_OCEAN) {
                    BiomeGenTalos2Ocean biome = (BiomeGenTalos2Ocean) baseBiome;
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

                } else if (baseBiome == TalosBiomes.TALOS_SHELF) {
                    BiomeGenTalos2Shelf biome = (BiomeGenTalos2Shelf) baseBiome;

                    BlockMetaPair top = biome.surfaceBlock;
                    BlockMetaPair stone = biome.shelfBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    int topY = groundHeight;

                    for (int y = 0; y <= topY; y++) {
                        int idx = columnBase + y;
                        BlockMetaPair pair = (y == topY) ? top : stone;
                        blocks[idx] = pair.getBlock();
                        meta[idx] = pair.getMetadata();
                    }

                    if (this.canGenerateWaterBlock() && topY < waterLevel) {
                        for (int y = topY + 1; y <= waterLevel; y++) {
                            int idx = columnBase + y;
                            blocks[idx] = water.getBlock();
                            meta[idx] = water.getMetadata();
                        }
                    }

                } else if (baseBiome == TalosBiomes.TALOS_BEACH) {
                    BiomeGenTalos2Beach biome = (BiomeGenTalos2Beach) baseBiome;
                    BlockMetaPair sand = biome.surfaceBlock;
                    BlockMetaPair dirt = biome.fillerBlock;
                    BlockMetaPair stone = biome.stoneBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    int top = groundHeight;

                    for (int y = 0; y <= top; y++) {
                        int idx = columnBase + y;

                        BlockMetaPair pair;
                        if (y == top || y == top - 1) pair = sand;
                        else if (y >= top - 4) pair = dirt;
                        else pair = stone;

                        blocks[idx] = pair.getBlock();
                        meta[idx] = pair.getMetadata();
                    }

                    if (this.canGenerateWaterBlock() && top < waterLevel - 2) {
                        for (int y = top + 1; y <= waterLevel; y++) {
                            int idx = columnBase + y;
                            blocks[idx] = water.getBlock();
                            meta[idx] = water.getMetadata();
                        }
                    }

                } else {
                    BiomeGenTalos2Plains biome = (BiomeGenTalos2Plains) baseBiome;
                    BlockMetaPair grass = biome.surfaceBlock;
                    BlockMetaPair dirt = biome.fillerBlock;
                    BlockMetaPair stone = biome.stoneBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    int top = groundHeight;

                    for (int y = 0; y <= top; y++) {
                        int idx = columnBase + y;

                        BlockMetaPair pair;
                        if (y == top) pair = grass;
                        else if (y >= top - 3) pair = dirt;
                        else pair = stone;

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

        final int COAST_RADIUS_BLOCKS = 192;

        ChunkCoastField cf = ChunkCoastField.build(this.continentNoise, chunkX, chunkZ, COAST_RADIUS_BLOCKS);

        for (int lx = 0; lx < SIZE; lx++) {
            for (int lz = 0; lz < SIZE; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                boolean isLand = cf.isLandAt(this.continentNoise, gx, gz);
                int dist = cf.distToCoastAt(this.continentNoise, gx, gz);

                MacroBiome m = this.macroBiomeField.pick(gx, gz);
                CoastProfile p = CoastProfiles.forMacro(m);

                int beachW = this.coastWidthField.beachWidthBlocks(gx, gz, p);
                int shelfW = this.coastWidthField.shelfWidthBlocks(gx, gz, p);

                out.isLand[lx][lz] = isLand;

                if (dist < 0) dist = 0;
                if (dist > 65535) dist = 65535;
                out.dist[lx][lz] = (short) dist;

                out.beachW[lx][lz] = (short) beachW;
                out.shelfW[lx][lz] = (short) shelfW;
            }
        }
    }

    static final class ChunkShoreCache {
        final boolean[][] isLand = new boolean[17][17];
        final short[][] dist = new short[17][17];
        final short[][] beachW = new short[17][17];
        final short[][] shelfW = new short[17][17];
    }


    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
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
