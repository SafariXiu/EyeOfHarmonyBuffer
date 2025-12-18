package com.EyeOfHarmonyBuffer.space.talos;

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

    private enum TalosBiomeType {
        OCEAN,
        BEACH,
        PLAINS
    }

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
        return new BiomeGenBase[]{BiomeGenTalos2.talos2};
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {

        System.out.println("[Talos2] onChunkProvider at " + chunkX + ", " + chunkZ);

        final int worldHeight = 256;
        final int waterLevel  = this.getWaterLevel();
        final int CHUNK_SIZE  = 16;

        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i]   = 0;
        }

        TalosBiomeType[][] talosBiomes = new TalosBiomeType[17][17];

        final double continentScale = 0.0007D;

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double cRaw = this.continentNoise.noise(gx * continentScale, gz * continentScale);
                double c = (cRaw + 1.0D) * 0.5D;
                c = c * c * (3.0D - 2.0D * c);

                if (c < 0.45D) {
                    talosBiomes[localX][localZ] = TalosBiomeType.OCEAN;
                } else if (c < 0.55D) {
                    talosBiomes[localX][localZ] = TalosBiomeType.BEACH;
                } else {
                    talosBiomes[localX][localZ] = TalosBiomeType.PLAINS;
                }
            }
        }

        final double detailScale = 0.0025D;

        int[][] heightMap = new int[17][17];

        final double DEEP_MIN = 16.0D;
        final double DEEP_MAX = waterLevel - 18;

        final double SHELF_TOP_MIN = waterLevel - 12;
        final double SHELF_TOP_MAX = waterLevel - 6;

        final double BEACH_MIN = waterLevel - 2;
        final double BEACH_MAX = waterLevel + 3;

        final double PLAIN_MIN = waterLevel + 6;
        final double PLAIN_MAX = 96.0D;

        final double cShelfStart = 0.30D;
        final double cShelfEnd = 0.45D;
        final double cBeachEnd = 0.55D;

        double[][] plainsBase = new double[17][17];
        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double dRaw = this.terrainNoise.noise(gx * detailScale, gz * detailScale);
                double d = (dRaw + 1.0D) * 0.5D;

                double hPlains = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);
                plainsBase[localX][localZ] = hPlains;
            }
        }

        double minPlain = Double.POSITIVE_INFINITY;
        double maxPlain = Double.NEGATIVE_INFINITY;
        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {
                double h = plainsBase[localX][localZ];
                if (h < minPlain) minPlain = h;
                if (h > maxPlain) maxPlain = h;
            }
        }
        double relief = maxPlain - minPlain;

        double reliefClamped = Math.max(0.0D, Math.min(40.0D, relief));
        double tRelief = reliefClamped / 40.0D;
        @SuppressWarnings("unused")
        double shelfWidthMax = 50.0D * (1.0D - tRelief) + 10.0D * tRelief;

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double cRaw = this.continentNoise.noise(gx * continentScale, gz * continentScale);
                double c = (cRaw + 1.0D) * 0.5D;
                c = c * c * (3.0D - 2.0D * c);

                // 中尺度起伏 d
                double dRaw = this.terrainNoise.noise(gx * detailScale, gz * detailScale);
                double d = (dRaw + 1.0D) * 0.5D;

                double hDeep = DEEP_MIN + d * (DEEP_MAX - DEEP_MIN);
                double hPlains = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);

                double h;

                if (c < cShelfStart) {
                    h = hDeep;

                } else if (c < cShelfEnd) {
                    double t = (c - cShelfStart) / (cShelfEnd - cShelfStart);
                    if (t < 0.0D) t = 0.0D;
                    if (t > 1.0D) t = 1.0D;

                    double shelfTop = SHELF_TOP_MIN + d * (SHELF_TOP_MAX - SHELF_TOP_MIN);

                    double cliffZone = 0.20D;
                    double tCliff;
                    if (t < cliffZone) {
                        double nt = t / cliffZone;
                        tCliff = nt * nt * nt;
                    } else {
                        tCliff = 1.0D;
                    }

                    h = hDeep * (1.0D - tCliff) + shelfTop * tCliff;

                } else if (c < cBeachEnd) {

                    double hPlainsRef = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);

                    double beachMid = (BEACH_MIN + BEACH_MAX) * 0.5D;
                    double smallVar = (d - 0.5D) * 4.0D;
                    double baseBeach = beachMid + smallVar;
                    if (baseBeach < BEACH_MIN) baseBeach = BEACH_MIN;
                    if (baseBeach > BEACH_MAX) baseBeach = BEACH_MAX;

                    double edgeBandFrac = 0.15D;
                    double bandStartC = cBeachEnd - edgeBandFrac * (cBeachEnd - cShelfEnd);
                    double tEdge = (c - bandStartC) / (cBeachEnd - bandStartC);
                    if (tEdge < 0.0D) tEdge = 0.0D;
                    if (tEdge > 1.0D) tEdge = 1.0D;

                    double maxDelta = 2.0D;

                    double targetPlain = hPlainsRef;
                    if (targetPlain > baseBeach + maxDelta) {
                        targetPlain = baseBeach + maxDelta;
                    }
                    if (targetPlain < baseBeach - maxDelta) {
                        targetPlain = baseBeach - maxDelta;
                    }

                    h = baseBeach * (1.0D - tEdge) + targetPlain * tEdge;

                } else {
                    double basePlain = hPlains;

                    double td = d - 0.5D;
                    double adjust = td * td * 16.0D * (td >= 0 ? 1 : -1);
                    basePlain += adjust;

                    double shorelineBand = 0.05D;
                    double tShore = (c - cBeachEnd) / shorelineBand;

                    if (tShore <= 0.0D) {
                        h = basePlain;
                    } else {
                        if (tShore > 1.0D) tShore = 1.0D;

                        double targetBeach = BEACH_MAX - 1.0D;

                        double maxDelta = 4.0D;
                        if (basePlain > targetBeach + maxDelta) {
                            basePlain = targetBeach + maxDelta;
                        }

                        h = targetBeach * (1.0D - tShore) + basePlain * tShore;
                    }
                }

                int ih = (int)Math.round(h);
                if (ih < 4) ih = 4;
                if (ih > worldHeight - 4) ih = worldHeight - 4;

                heightMap[localX][localZ] = ih;
            }
        }

        int[][] smoothMap = new int[17][17];
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                int sum = 0;
                int cnt = 0;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;
                        if (nx < 0 || nx > 16 || nz < 0 || nz > 16) continue;
                        sum += heightMap[nx][nz];
                        cnt++;
                    }
                }
                smoothMap[x][z] = sum / cnt;
            }
        }
        heightMap = smoothMap;

        int minBeachHeight = waterLevel;
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                if (talosBiomes[x][z] != TalosBiomeType.BEACH) {
                    continue;
                }
                if (heightMap[x][z] < minBeachHeight) {
                    heightMap[x][z] = minBeachHeight;
                }
            }
        }

        BlockMetaPair grass = this.getGrassBlock();
        BlockMetaPair dirt  = this.getDirtBlock();
        BlockMetaPair stone = this.getStoneBlock();
        BlockMetaPair sand  = this.getSandBlock();
        BlockMetaPair water = this.getWaterBlock();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {

                int groundHeight = heightMap[localX][localZ];
                int columnBase   = (localX * 16 + localZ) * worldHeight;
                TalosBiomeType tBiome = talosBiomes[localX][localZ];

                switch (tBiome) {
                    case OCEAN: {
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
                        break;
                    }
                    case BEACH: {
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

                        break;
                    }
                    case PLAINS:
                    default: {
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
                        break;
                    }
                }
            }
        }

        strongCleanOceanFloor(blocks, meta);
        buildWideShelf(blocks, meta, chunkX, chunkZ);
        extendLandEdgesDown(blocks, meta);

        smoothBeachPlainEdges(blocks, meta);
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

                    if (!inWaterColumn) {
                        continue;
                    }

                    if (b == Blocks.grass ||
                        b == Blocks.dirt  ||
                        b == Blocks.gravel) {
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
                if (c >= 0.30D && c <= 0.52D) {
                    isNearContinent[x][z] = true;
                } else {
                    isNearContinent[x][z] = false;
                }
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

                if (nearest > MAX_SHELF_RADIUS) {
                    continue;
                }

                double t = nearest / (double) MAX_SHELF_RADIUS;

                int shelfTopY = (int)Math.round(
                    (waterLevel - 6) * (1.0D - t) +
                        (waterLevel - 14) * t
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

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {

                int columnBase = (localX * CHUNK_SIZE + localZ) * worldHeight;

                int yTop = -1;
                for (int y = worldHeight - 1; y >= 0; y--) {
                    Block b = blocks[columnBase + y];
                    if (b != null && b != Blocks.air) {
                        yTop = y;
                        break;
                    }
                }
                if (yTop < 0) continue;

                Block topBlock = blocks[columnBase + yTop];

                if (topBlock != Blocks.sand) continue;

                boolean nearGrass = false;
                int maxGrassY = -1;

                int[][] dirs = { {1,0}, {-1,0}, {0,1}, {0,-1} };
                for (int i = 0; i < 4; i++) {
                    int nx = localX + dirs[i][0];
                    int nz = localZ + dirs[i][1];
                    if (nx < 0 || nx >= CHUNK_SIZE || nz < 0 || nz >= CHUNK_SIZE) continue;

                    int nBase = (nx * CHUNK_SIZE + nz) * worldHeight;

                    for (int ny = worldHeight - 1; ny >= 0; ny--) {
                        Block nb = blocks[nBase + ny];
                        if (nb != null && nb != Blocks.air) {
                            if (nb == Blocks.grass) {
                                nearGrass = true;
                                if (ny > maxGrassY) maxGrassY = ny;
                            }
                            break;
                        }
                    }
                }

                if (!nearGrass || maxGrassY < 0) continue;

                int heightDiff = yTop - maxGrassY;

                if (heightDiff >= 1 && heightDiff <= 2) {
                    blocks[columnBase + yTop] = Blocks.dirt;
                    meta[columnBase + yTop]   = 0;

                    if (yTop - 1 >= 0) {
                        Block below = blocks[columnBase + yTop - 1];
                        if (below == Blocks.sand) {
                            blocks[columnBase + yTop - 1] = Blocks.dirt;
                            meta[columnBase + yTop - 1]   = 0;
                        }
                    }
                }
            }
        }
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
