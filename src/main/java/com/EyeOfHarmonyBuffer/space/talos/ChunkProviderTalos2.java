package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.BiomeGenTalos2Beach;
import com.EyeOfHarmonyBuffer.space.talos.biome.BiomeGenTalos2Ocean;
import com.EyeOfHarmonyBuffer.space.talos.biome.BiomeGenTalos2Plains;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
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

    private static final boolean DEBUG_BEACH_HIGHS = false;
    private static final int DEBUG_PRINT_LIMIT_PER_CHUNK = 8;

    private static final boolean CLAMP_BEACH_MAX_HEIGHT = false;

    private static final int BEACH_MAX_TOLERANCE = 1;

    private static final boolean FIX_PLAINS_PULL_TO_SHORE = true;

    public ChunkProviderTalos2(World world, long seed, boolean mapFeaturesEnabled) {
        super(world, seed, mapFeaturesEnabled);
        this.world = world;

        this.continentNoise = new SimplexNoiseOctave(4);
        this.terrainNoise = new SimplexNoiseOctave(5);
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
            TalosBiomes.TALOS_PLAINS
        };
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {

        //System.out.println("[Talos2] onChunkProvider chunk=(" + chunkX + "," + chunkZ + ")");

        clearChunkBlocks(blocks, meta);

        double[][] plainsBase = computePlainsBase(chunkX, chunkZ);

        NoiseDebugInfo[][] noiseInfo = DEBUG_BEACH_HIGHS ? new NoiseDebugInfo[17][17] : null;

        int[][] heightMapRaw = computeBaseHeightMap(chunkX, chunkZ, plainsBase, noiseInfo);

        int[][] heightMap = copyHeightMap(heightMapRaw);

        smoothHeightMap(heightMap);

        if (CLAMP_BEACH_MAX_HEIGHT) {
            clampBeachMaxHeight(heightMap, chunkX, chunkZ);
        }

        ensureMinBeachHeight(heightMap, chunkX, chunkZ);

        fillBlocksFromHeightMap(blocks, meta, heightMap, chunkX, chunkZ);

        strongCleanOceanFloor(blocks, meta);
        buildWideShelf(blocks, meta, chunkX, chunkZ);
        extendLandEdgesDown(blocks, meta);

        //smoothBeachPlainEdges(blocks, meta);
        //removeMoatRing(blocks, meta);
        softenBeachBoundaryNoMoat(chunkX, chunkZ, blocks, meta);

        applyBedrockFloor(blocks, meta, chunkX, chunkZ);

        if (DEBUG_BEACH_HIGHS) {
            debugPrintHighBeachColumns(chunkX, chunkZ, blocks, heightMapRaw, heightMap, noiseInfo);
        }
    }

    private static int[][] copyHeightMap(int[][] src) {
        int[][] dst = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            dst[i] = new int[src[i].length];
            System.arraycopy(src[i], 0, dst[i], 0, src[i].length);
        }
        return dst;
    }

    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i]   = 0;
        }
    }

    private double[][] computePlainsBase(int chunkX, int chunkZ) {
        final int SIZE = 17;
        final double detailScale = 0.0025D;

        BiomeGenTalos2Plains plainsBiome = TalosBiomes.TALOS_PLAINS;

        double plainMin = plainsBiome.plainMin;
        double plainMax = plainsBiome.plainMax;

        double[][] plainsBase = new double[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {
                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double d = sampleTerrain01(gx, gz, detailScale);

                double hPlains = plainMin + d * (plainMax - plainMin);
                plainsBase[localX][localZ] = hPlains;
            }
        }

        return plainsBase;
    }

    private static class NoiseDebugInfo {
        double c;
        double d;
        int segment;
    }

    private int[][] computeBaseHeightMap(
        int chunkX,
        int chunkZ,
        double[][] plainsBase,
        NoiseDebugInfo[][] dbgOut) {

        final int SIZE = 17;
        final int worldHeight = 256;

        BiomeGenTalos2Ocean oceanBiome  = TalosBiomes.TALOS_OCEAN;
        BiomeGenTalos2Beach beachBiome  = TalosBiomes.TALOS_BEACH;
        BiomeGenTalos2Plains plainsBiome = TalosBiomes.TALOS_PLAINS;

        double DEEP_MIN = oceanBiome.deepMin;
        double DEEP_MAX  = oceanBiome.deepMax;
        double SHELF_TOP_MIN = oceanBiome.shelfTopMin;
        double SHELF_TOP_MAX = oceanBiome.shelfTopMax;

        double BEACH_MIN = beachBiome.beachMin;
        double BEACH_MAX = beachBiome.beachMax;

        double PLAIN_MIN = plainsBiome.plainMin;
        double PLAIN_MAX = plainsBiome.plainMax;

        final double cShelfStart = 0.30D;
        final double cShelfEnd = 0.45D;
        final double cBeachEnd = 0.55D;

        final double continentScale = 0.0007D;
        final double detailScale = 0.0025D;

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double cRaw = this.continentNoise.noise(gx * continentScale, gz * continentScale);
                double c = (cRaw + 1.0D) * 0.5D;
                c = c * c * (3.0D - 2.0D * c);

                double d = sampleTerrain01(gx, gz, detailScale);

                double hDeep   = DEEP_MIN + d * (DEEP_MAX - DEEP_MIN);
                double hPlains = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);

                final double BLEND = 0.03D;

                double hDeepOnly = hDeep;

                double tShelf = (c - cShelfStart) / (cShelfEnd - cShelfStart);
                tShelf = clamp01(tShelf);
                double shelfTop = SHELF_TOP_MIN + d * (SHELF_TOP_MAX - SHELF_TOP_MIN);

                double cliffZone = 0.20D;
                double tCliff;
                if (tShelf < cliffZone) {
                    double nt = tShelf / cliffZone;
                    tCliff = nt * nt * nt;
                } else {
                    tCliff = 1.0D;
                }
                double hShelfOnly = hDeep * (1.0D - tCliff) + shelfTop * tCliff;

                double hBeachOnly  = computeBeachHeight(c, d, hPlains, BEACH_MIN, BEACH_MAX, cShelfEnd, cBeachEnd);
                double hPlainsOnly = computePlainsHeightNearCoast(c, d, hPlains, BEACH_MAX, cBeachEnd);

                double w01 = smoothstep(cShelfStart - BLEND, cShelfStart + BLEND, c);
                double w12 = smoothstep(cShelfEnd - BLEND, cShelfEnd + BLEND, c);
                double w23 = smoothstep(cBeachEnd - BLEND, cBeachEnd + BLEND, c);

                double h01 = lerp(hDeepOnly, hShelfOnly, w01);
                double h12 = lerp(h01, hBeachOnly, w12);
                double h = lerp(h12, hPlainsOnly, w23);

                int seg = (c < cShelfStart) ? 0 : (c < cShelfEnd ? 1 : (c < cBeachEnd ? 2 : 3));

                int ih = (int) Math.floor(h);
                if (ih < 4) ih = 4;
                if (ih > worldHeight - 4) ih = worldHeight - 4;

                heightMap[localX][localZ] = ih;

                if (dbgOut != null) {
                    NoiseDebugInfo inf = new NoiseDebugInfo();
                    inf.c = c;
                    inf.d = d;
                    inf.segment = seg;
                    dbgOut[localX][localZ] = inf;
                }
            }
        }

        return heightMap;
    }

    private double computeBeachHeight(
        double c, double d, double hPlainsRef,
        double beachMin, double beachMax,
        double cShelfEnd, double cBeachEnd) {

        double t = (c - cShelfEnd) / (cBeachEnd - cShelfEnd);
        t = clamp01(t);
        t = t * t * (3.0D - 2.0D * t);

        double base = beachMin * (1.0D - t) + beachMax * t;

        double micro = (d - 0.5D) * 1.2D;

        double h = base + micro;

        double min = beachMin - 0.3D;
        double max = beachMax + 0.3D;
        if (h < min) h = min;
        if (h > max) h = max;

        double edgeBandFrac = 0.15D;
        double bandStartC = cBeachEnd - edgeBandFrac * (cBeachEnd - cShelfEnd);
        double te = (c - bandStartC) / (cBeachEnd - bandStartC);
        te = clamp01(te);

        double maxDelta = 2.0D;
        double targetPlain = hPlainsRef;
        if (targetPlain > h + maxDelta) targetPlain = h + maxDelta;
        if (targetPlain < h - maxDelta) targetPlain = h - maxDelta;

        return h * (1.0D - te) + targetPlain * te;
    }

    private double computePlainsHeightNearCoast(
        double c,
        double d,
        double hPlains,
        double beachMax,
        double cBeachEnd) {

        double basePlain = hPlains;

        double td = d - 0.5D;
        double adjust = td * td * 6.0D * (td >= 0 ? 1 : -1);
        basePlain += adjust;

        double shorelineBand = 0.05D;
        double t = (c - cBeachEnd) / shorelineBand;
        t = clamp01(t);

        double targetBeach = beachMax - 1.0D;

        double maxDelta = 4.0D;
        if (basePlain > targetBeach + maxDelta) {
            basePlain = targetBeach + maxDelta;
        }

        if (!FIX_PLAINS_PULL_TO_SHORE) {
            if (t <= 0.0D) return basePlain;
            return targetBeach * (1.0D - t) + basePlain * t;
        }

        return targetBeach * (1.0D - t) + basePlain * t;
    }

    private void smoothHeightMap(int[][] heightMap) {
        final int SIZE = 17;
        int[][] out = new int[SIZE][SIZE];

        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                int sum = 0, cnt = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx < 0 || nx > 16 || nz < 0 || nz > 16) continue;
                        sum += heightMap[nx][nz];
                        cnt++;
                    }
                }
                double avg = sum / (double) cnt;
                out[x][z] = (int) Math.floor(avg + 1e-6);
            }
        }

        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                heightMap[x][z] = out[x][z];
            }
        }
    }

    private void ensureMinBeachHeight(int[][] heightMap, int chunkX, int chunkZ) {
        final int CHUNK_SIZE = 16;
        int waterLevel = this.getWaterLevel();

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int gx = chunkX * 16 + x;
                int gz = chunkZ * 16 + z;

                BiomeGenBase biome = this.world.getBiomeGenForCoords(gx, gz);
                if (biome != TalosBiomes.TALOS_BEACH) continue;

                if (heightMap[x][z] < waterLevel) {
                    heightMap[x][z] = waterLevel;
                }
            }
        }
    }

    private void clampBeachMaxHeight(int[][] heightMap, int chunkX, int chunkZ) {
        final int CHUNK_SIZE = 16;

        BiomeGenTalos2Beach beachBiome = TalosBiomes.TALOS_BEACH;
        int maxY = (int)Math.round(beachBiome.beachMax) + BEACH_MAX_TOLERANCE;

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int gx = chunkX * 16 + x;
                int gz = chunkZ * 16 + z;

                BiomeGenBase biome = this.world.getBiomeGenForCoords(gx, gz);
                if (biome != TalosBiomes.TALOS_BEACH) continue;

                if (heightMap[x][z] > maxY) {
                    heightMap[x][z] = maxY;
                }
            }
        }
    }

    private void fillBlocksFromHeightMap(
        Block[] blocks,
        byte[] meta,
        int[][] heightMap,
        int chunkX,
        int chunkZ) {

        final int worldHeight = 256;
        final int CHUNK_SIZE  = 16;

        int waterLevel = this.getWaterLevel();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                int groundHeight = heightMap[localX][localZ];
                int columnBase   = (localX * 16 + localZ) * worldHeight;

                BiomeGenBase baseBiome = this.world.getBiomeGenForCoords(gx, gz);

                if (baseBiome == TalosBiomes.TALOS_OCEAN) {
                    BiomeGenTalos2Ocean biome = (BiomeGenTalos2Ocean) baseBiome;
                    BlockMetaPair stone = biome.bottomBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    for (int y = 0; y <= groundHeight; y++) {
                        int idx = columnBase + y;
                        blocks[idx] = stone.getBlock();
                        meta[idx]   = stone.getMetadata();
                    }
                    if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
                        for (int y = groundHeight + 1; y <= waterLevel; y++) {
                            int idx = columnBase + y;
                            blocks[idx] = water.getBlock();
                            meta[idx]   = water.getMetadata();
                        }
                    }

                } else if (baseBiome == TalosBiomes.TALOS_BEACH) {
                    BiomeGenTalos2Beach biome = (BiomeGenTalos2Beach) baseBiome;
                    BlockMetaPair sand  = biome.surfaceBlock;
                    BlockMetaPair dirt  = biome.fillerBlock;
                    BlockMetaPair stone = biome.stoneBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    int top = groundHeight;

                    for (int y = 0; y <= top; y++) {
                        int idx = columnBase + y;

                        BlockMetaPair pair;
                        if (y == top || y == top - 1) {
                            pair = sand;
                        } else if (y >= top - 4) {
                            pair = dirt;
                        } else {
                            pair = stone;
                        }

                        blocks[idx] = pair.getBlock();
                        meta[idx]   = pair.getMetadata();
                    }

                    if (this.canGenerateWaterBlock() && top < waterLevel - 2) {
                        for (int y = top + 1; y <= waterLevel; y++) {
                            int idx = columnBase + y;
                            blocks[idx] = water.getBlock();
                            meta[idx]   = water.getMetadata();
                        }
                    }

                } else {
                    BiomeGenTalos2Plains biome = (BiomeGenTalos2Plains) baseBiome;
                    BlockMetaPair grass = biome.surfaceBlock;
                    BlockMetaPair dirt  = biome.fillerBlock;
                    BlockMetaPair stone = biome.stoneBlock;
                    BlockMetaPair water = this.getWaterBlock();

                    int top = groundHeight;

                    for (int y = 0; y <= top; y++) {
                        int idx = columnBase + y;

                        BlockMetaPair pair;
                        if (y == top) {
                            pair = grass;
                        } else if (y >= top - 3) {
                            pair = dirt;
                        } else {
                            pair = stone;
                        }

                        blocks[idx] = pair.getBlock();
                        meta[idx]   = pair.getMetadata();
                    }

                    if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
                        for (int y = groundHeight + 1; y <= waterLevel; y++) {
                            int idx = columnBase + y;
                            blocks[idx] = water.getBlock();
                            meta[idx]   = water.getMetadata();
                        }
                    }
                }
            }
        }
    }

    private void strongCleanOceanFloor(Block[] blocks, byte[] meta) {
        final int worldHeight = 256;
        int waterLevel = this.getWaterLevel();

        BlockMetaPair stone = this.getStoneBlock();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

                int columnBase = (localX * 16 + localZ) * worldHeight;

                for (int y = 0; y <= waterLevel; y++) {
                    int index = columnBase + y;
                    Block b = blocks[index];

                    if (b == null || b == Blocks.air) continue;

                    boolean inWaterColumn = false;

                    if (b == Blocks.water) {
                        inWaterColumn = true;
                    } else {
                        for (int yy = y + 1; yy <= waterLevel; yy++) {
                            int aboveIdx = columnBase + yy;
                            if (blocks[aboveIdx] == Blocks.water) {
                                inWaterColumn = true;
                                break;
                            }
                        }
                    }

                    if (!inWaterColumn) continue;

                    if (b == Blocks.grass || b == Blocks.dirt || b == Blocks.gravel) {
                        blocks[index] = stone.getBlock();
                        meta[index]   = stone.getMetadata();
                    }
                }
            }
        }
    }

    private void buildWideShelf(Block[] blocks, byte[] meta, int chunkX, int chunkZ) {
        final int worldHeight = 256;
        final int waterLevel  = this.getWaterLevel();
        final int CHUNK_SIZE  = 16;

        BlockMetaPair stone = this.getStoneBlock();

        final double continentScale = 0.0007D;

        boolean[][] isNearContinent = new boolean[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int gx = chunkX * 16 + x;
                int gz = chunkZ * 16 + z;
                double cRaw = this.continentNoise.noise(gx * continentScale, gz * continentScale);
                double c = (cRaw + 1.0D) * 0.5D;
                c = c * c * (3.0D - 2.0D * c);
                isNearContinent[x][z] = (c >= 0.30D && c <= 0.52D);
            }
        }

        boolean[][] hasSolidBelowWater = new boolean[CHUNK_SIZE][CHUNK_SIZE];
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int columnBase = (x * 16 + z) * worldHeight;
                boolean solid = false;
                for (int y = waterLevel; y >= 1; y--) {
                    Block b = blocks[columnBase + y];
                    if (b != null && b != Blocks.air && b != Blocks.water) {
                        solid = true;
                        break;
                    }
                }
                hasSolidBelowWater[x][z] = solid;
            }
        }

        int MAX_SHELF_RADIUS = 50;

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {

                if (!isNearContinent[x][z]) continue;
                if (!hasSolidBelowWater[x][z]) continue;

                int columnBase = (x * 16 + z) * worldHeight;

                int nearest = MAX_SHELF_RADIUS + 1;

                for (int dx = -MAX_SHELF_RADIUS; dx <= MAX_SHELF_RADIUS; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= CHUNK_SIZE) continue;
                    for (int dz = -MAX_SHELF_RADIUS; dz <= MAX_SHELF_RADIUS; dz++) {
                        int zz = z + dz;
                        if (zz < 0 || zz >= CHUNK_SIZE) continue;

                        int base2 = (xx * 16 + zz) * worldHeight;
                        boolean land = false;
                        for (int y = waterLevel + 1; y <= waterLevel + 6 && y < worldHeight; y++) {
                            Block b2 = blocks[base2 + y];
                            if (b2 == Blocks.grass || b2 == Blocks.dirt ||
                                b2 == Blocks.stone || b2 == Blocks.sand) {
                                land = true;
                                break;
                            }
                        }
                        if (!land) continue;

                        int dist = Math.abs(dx) + Math.abs(dz);
                        if (dist < nearest) nearest = dist;
                    }
                }

                if (nearest > MAX_SHELF_RADIUS) continue;

                double t = nearest / (double) MAX_SHELF_RADIUS;

                int shelfTopY = (int)Math.round(
                    (waterLevel - 6) * (1.0D - t) + (waterLevel - 14) * t
                );
                if (shelfTopY < 8) shelfTopY = 8;

                int shelfThickness = 4;
                int cliffHeight = 18;
                int shelfBottomY = shelfTopY - shelfThickness;
                int cliffBottomY = Math.max(4, shelfBottomY - cliffHeight);

                for (int y = cliffBottomY; y <= shelfTopY; y++) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b == null || b == Blocks.air || b == Blocks.water) {
                        blocks[idx] = stone.getBlock();
                        meta[idx]   = stone.getMetadata();
                    }
                }
            }
        }
    }

    private void extendLandEdgesDown(Block[] blocks, byte[] meta) {
        final int worldHeight = 256;
        final int waterLevel  = this.getWaterLevel();

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
                        meta[idx]   = stone.getMetadata();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private void smoothBeachPlainEdges(Block[] blocks, byte[] meta) {
        final int worldHeight = 256;
        final int CHUNK_SIZE  = 16;

        final int BAND = 4;

        int[][] topY = new int[CHUNK_SIZE][CHUNK_SIZE];
        Block[][] topB = new Block[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int base = (x * CHUNK_SIZE + z) * worldHeight;
                int yTop = -1;
                Block bTop = null;

                for (int y = worldHeight - 1; y >= 0; y--) {
                    Block b = blocks[base + y];
                    if (b != null && b != Blocks.air) {
                        yTop = y;
                        bTop = b;
                        break;
                    }
                }

                topY[x][z] = yTop;
                topB[x][z] = bTop;
            }
        }

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {

                int y = topY[x][z];
                Block b = topB[x][z];
                if (y < 0 || b != Blocks.sand) continue;

                int nearest = BAND + 1;

                for (int dx = -BAND; dx <= BAND; dx++) {
                    int xx = x + dx;
                    if (xx < 0 || xx >= CHUNK_SIZE) continue;

                    for (int dz = -BAND; dz <= BAND; dz++) {
                        int zz = z + dz;
                        if (zz < 0 || zz >= CHUNK_SIZE) continue;

                        Block nb = topB[xx][zz];
                        if (nb == Blocks.grass || nb == Blocks.dirt) {
                            int dist = Math.abs(dx) + Math.abs(dz);
                            if (dist < nearest) nearest = dist;
                        }
                    }
                }

                if (nearest > BAND) continue;

                int base = (x * CHUNK_SIZE + z) * worldHeight;
                int idxTop = base + y;

                if (nearest <= 1) {
                    blocks[idxTop] = Blocks.grass;
                    meta[idxTop] = 0;

                    if (y - 1 >= 0 && blocks[base + y - 1] == Blocks.sand) {
                        blocks[base + y - 1] = Blocks.dirt;
                        meta[base + y - 1] = 0;
                    }
                } else {
                    blocks[idxTop] = Blocks.dirt;
                    meta[idxTop] = 0;
                }
            }
        }
    }

    private void debugPrintHighBeachColumns(
        int chunkX,
        int chunkZ,
        Block[] blocks,
        int[][] heightRaw,
        int[][] heightSmooth,
        NoiseDebugInfo[][] info) {

        final int SAMPLE_LIMIT = 4;

        BiomeGenTalos2Beach beachBiome = TalosBiomes.TALOS_BEACH;
        int beachMax = (int) Math.round(beachBiome.beachMax);
        int threshold = beachMax + BEACH_MAX_TOLERANCE;

        int beachCount = 0;
        int highCount = 0;

        int minH = Integer.MAX_VALUE;
        int maxH = Integer.MIN_VALUE;

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                BiomeGenBase biome = this.world.getBiomeGenForCoords(gx, gz);
                if (biome != TalosBiomes.TALOS_BEACH) continue;

                beachCount++;

                int hs = heightSmooth[lx][lz];
                if (hs < minH) minH = hs;
                if (hs > maxH) maxH = hs;

                if (hs > threshold) highCount++;
            }
        }

        System.out.println(
            "[Talos2][DBG] chunk=(" + chunkX + "," + chunkZ + ") " +
                "BEACH columns=" + beachCount + " " +
                (beachCount > 0 ? ("hSmooth[min,max]=[" + minH + "," + maxH + "] ") : "") +
                "beachMax=" + beachMax + " threshold=" + threshold + " " +
                "highBeachCount=" + highCount
        );

        if (beachCount == 0) return;

        int samplePrinted = 0;
        for (int lx = 0; lx < 16 && samplePrinted < SAMPLE_LIMIT; lx++) {
            for (int lz = 0; lz < 16 && samplePrinted < SAMPLE_LIMIT; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                BiomeGenBase biome = this.world.getBiomeGenForCoords(gx, gz);
                if (biome != TalosBiomes.TALOS_BEACH) continue;

                NoiseDebugInfo inf = (info != null) ? info[lx][lz] : null;
                double c = (inf != null) ? inf.c : -1;
                double d = (inf != null) ? inf.d : -1;
                int seg = (inf != null) ? inf.segment : -1;

                int yTop = findTopSolidY(blocks, lx, lz);
                Block top = (yTop >= 0) ? blocks[(lx * 16 + lz) * 256 + yTop] : null;

                System.out.println(
                    "[Talos2][DBG] BEACH sample " +
                        "local=(" + lx + "," + lz + ") world=(" + gx + "," + gz + ") " +
                        "c=" + fmt3(c) + " d=" + fmt3(d) + " seg=" + seg + " " +
                        "hRaw=" + heightRaw[lx][lz] + " hSmooth=" + heightSmooth[lx][lz] + " " +
                        "yTop=" + yTop + " top=" + (top == null ? "null" : top.getUnlocalizedName())
                );

                samplePrinted++;
            }
        }

        if (highCount == 0) return;

        int printed = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                BiomeGenBase biome = this.world.getBiomeGenForCoords(gx, gz);
                if (biome != TalosBiomes.TALOS_BEACH) continue;

                int hs = heightSmooth[lx][lz];
                if (hs <= threshold) continue;

                NoiseDebugInfo inf = (info != null) ? info[lx][lz] : null;
                double c = (inf != null) ? inf.c : -1;
                double d = (inf != null) ? inf.d : -1;
                int seg = (inf != null) ? inf.segment : -1;

                int yTop = findTopSolidY(blocks, lx, lz);
                Block top = (yTop >= 0) ? blocks[(lx * 16 + lz) * 256 + yTop] : null;

                System.out.println(
                    "[Talos2][DBG] High BEACH column " +
                        "local=(" + lx + "," + lz + ") world=(" + gx + "," + gz + ") " +
                        "c=" + fmt3(c) + " d=" + fmt3(d) + " seg=" + seg + " " +
                        "hRaw=" + heightRaw[lx][lz] + " hSmooth=" + hs +
                        " yTop=" + yTop + " top=" + (top == null ? "null" : top.getUnlocalizedName())
                );

                printed++;
                if (printed >= DEBUG_PRINT_LIMIT_PER_CHUNK) return;
            }
        }
    }

    private static String fmt3(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    private static int findTopSolidY(Block[] blocks, int lx, int lz) {
        int base = (lx * 16 + lz) * 256;
        for (int y = 255; y >= 0; y--) {
            Block b = blocks[base + y];
            if (b != null && b != Blocks.air) return y;
        }
        return -1;
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }

    private void removeMoatRing(Block[] blocks, byte[] meta) {
        final int H = 256, S = 16;
        final int SEA = 64;

        final int MIN_Y = SEA - 2;
        final int MAX_Y = SEA + 8;

        int[][] topY = new int[S][S];
        Block[][] topB = new Block[S][S];

        for (int x = 0; x < S; x++) {
            for (int z = 0; z < S; z++) {
                int base = (x * S + z) * H;
                int yTop = -1;
                Block bTop = null;
                for (int y = H - 1; y >= 0; y--) {
                    Block b = blocks[base + y];
                    if (b != null && b != Blocks.air) { yTop = y; bTop = b; break; }
                }
                topY[x][z] = yTop;
                topB[x][z] = bTop;
            }
        }

        for (int x = 1; x < S - 1; x++) {
            for (int z = 1; z < S - 1; z++) {
                int y = topY[x][z];
                if (y < MIN_Y || y > MAX_Y) continue;

                if (topB[x][z] != Blocks.dirt) continue;

                boolean adjSand  = false;
                boolean adjGrass = false;

                Block b1 = topB[x - 1][z];
                Block b2 = topB[x + 1][z];
                Block b3 = topB[x][z - 1];
                Block b4 = topB[x][z + 1];

                if (b1 == Blocks.sand || b2 == Blocks.sand || b3 == Blocks.sand || b4 == Blocks.sand) adjSand = true;
                if (b1 == Blocks.grass || b2 == Blocks.grass || b3 == Blocks.grass || b4 == Blocks.grass) adjGrass = true;

                if (!(adjSand && adjGrass)) continue;

                int base = (x * S + z) * H;

                blocks[base + y] = Blocks.grass; meta[base + y] = 0;

                for (int dy = 1; dy <= 2; dy++) {
                    int yy = y - dy;
                    if (yy < 0) break;
                    Block bb = blocks[base + yy];
                    if (bb == Blocks.sand || bb == Blocks.dirt || bb == Blocks.grass) {
                        blocks[base + yy] = Blocks.dirt;
                        meta[base + yy] = 0;
                    }
                }
            }
        }
    }

    private void softenBeachBoundaryNoMoat(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        final int H = 256, S = 16;
        final int SEA = getWaterLevel();

        final int MAX_Y = SEA + 6;
        final int R = 5;
        final float NEAR = 0.60f;
        final float FAR  = 0.10f;

        for (int lx = 0; lx < S; lx++) {
            for (int lz = 0; lz < S; lz++) {
                int gx = chunkX * 16 + lx;
                int gz = chunkZ * 16 + lz;

                if (world.getBiomeGenForCoords(gx, gz) != TalosBiomes.TALOS_PLAINS) continue;

                int base = (lx * S + lz) * H;
                int yTop = -1;
                for (int y = H - 1; y >= 0; y--) {
                    Block b = blocks[base + y];
                    if (b != null && b != Blocks.air) { yTop = y; break; }
                }
                if (yTop < 0 || yTop > MAX_Y) continue;

                if (blocks[base + yTop] != Blocks.grass) continue;

                int best = R + 1;
                for (int dx = -R; dx <= R; dx++) {
                    for (int dz = -R; dz <= R; dz++) {
                        int wx = gx + dx;
                        int wz = gz + dz;
                        if (world.getBiomeGenForCoords(wx, wz) == TalosBiomes.TALOS_BEACH) {
                            int d = Math.abs(dx) + Math.abs(dz);
                            if (d < best) best = d;
                        }
                    }
                }
                if (best > R) continue;

                float t = best / (float) R;
                float chance = NEAR * (1f - t) + FAR * t;

                if (pseudoNoise01(gx, gz) < chance) {
                    blocks[base + yTop] = Blocks.sand;
                    meta[base + yTop] = 0;

                    if (yTop - 1 >= 0 && blocks[base + yTop - 1] == Blocks.dirt) {
                        blocks[base + yTop - 1] = Blocks.sand;
                        meta[base + yTop - 1] = 0;
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

    private float pseudoNoise01(int x, int z) {
        int h = x * 374761393 + z * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7fffffff) / (float)0x80000000;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
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

    @Override
    public void onPopulate(IChunkProvider iChunkProvider, int i, int i1) {
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
