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

    /**
     * 核心：按 chunk 坐标生成地形方块数组。
     * 流程：
     *   1. 清空方块数组
     *   2. 计算 (x,z) 的大致地貌类型 OCEAN/BEACH/PLAINS
     *   3. 计算平原基准高度图 plainsBase（仅与 terrainNoise 相关）
     *   4. 根据 continentNoise + terrainNoise + 各种规则生成基础高度图 heightMap
     *   5. 对高度图做 3x3 平滑
     *   6. 确保沙滩高度不低于水位
     *   7. 根据高度图和地貌类型填充方块（石头/沙/土/草/水）
     *   8. 对海底和岸边做一系列后处理：清理海底、生成宽大陆架、向下延伸岸壁、平滑沙滩与草地边缘
     */
    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {

        System.out.println("[Talos2] onChunkProvider at " + chunkX + ", " + chunkZ);

        clearChunkBlocks(blocks, meta);

        double[][] plainsBase = computePlainsBase(chunkX, chunkZ);

        int[][] heightMap = computeBaseHeightMap(chunkX, chunkZ, plainsBase);

        smoothHeightMap(heightMap);

        ensureMinBeachHeight(heightMap, chunkX, chunkZ);

        fillBlocksFromHeightMap(blocks, meta, heightMap, chunkX, chunkZ);

        strongCleanOceanFloor(blocks, meta);
        buildWideShelf(blocks, meta, chunkX, chunkZ);
        extendLandEdgesDown(blocks, meta);
        smoothBeachPlainEdges(blocks, meta);
    }

    /**
     * 步骤 1：将整个 chunk 的方块数组清空（置为 null / 0）。
     * 这样后续生成过程可以假设初始都是“空气”。
     */
    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i]   = 0;
        }
    }

    /**
     * 步骤 3：计算“平原基准高度图” plainsBase。
     * 这里只使用 terrainNoise（中尺度噪声）来生成基础的平原高度，范围大致在 [PLAIN_MIN, PLAIN_MAX]。
     *
     * 之后在实际高度计算时，海洋/沙滩/陆架都会参考这个基准平原高度进行过渡或约束。
     */
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

                double dRaw = this.terrainNoise.noise(gx * detailScale, gz * detailScale);
                double d = (dRaw + 1.0D) * 0.5D; // [0,1]

                double hPlains = plainMin + d * (plainMax - plainMin);
                plainsBase[localX][localZ] = hPlains;
            }
        }

        return plainsBase;
    }

    /**
     * 步骤 4：综合 continentNoise 和 terrainNoise 计算基础高度图：
     * - c 控制从深海 → 陆架 → 海滩 → 平原的过渡
     * - d 控制局部起伏（深海凹凸、陆架高度变化、平原起伏等）
     *
     * 这里会分为 4 段逻辑：
     *   1) c < cShelfStart     → 深海区：高度 ~ [DEEP_MIN, DEEP_MAX]
     *   2) cShelfStart..cShelfEnd → 深海到陆架的坡面
     *   3) cShelfEnd..cBeachEnd  → 陆架/沙滩过渡区，高度围绕水位
     *   4) c >= cBeachEnd     → 内陆平原及其向海岸的高度回拉
     *
     * 返回的 heightMap 是 int 高度（已做了基础的 min/max 裁剪）。
     */
    private int[][] computeBaseHeightMap(
        int chunkX,
        int chunkZ,
        double[][] plainsBase) {

        final int SIZE = 17;
        final int worldHeight = 256;
        final int waterLevel  = this.getWaterLevel();

        BiomeGenTalos2Ocean oceanBiome  = TalosBiomes.TALOS_OCEAN;
        BiomeGenTalos2Beach beachBiome  = TalosBiomes.TALOS_BEACH;
        BiomeGenTalos2Plains plainsBiome = TalosBiomes.TALOS_PLAINS;

        double DEEP_MIN      = oceanBiome.deepMin;
        double DEEP_MAX      = oceanBiome.deepMax;
        double SHELF_TOP_MIN = oceanBiome.shelfTopMin;
        double SHELF_TOP_MAX = oceanBiome.shelfTopMax;

        double BEACH_MIN = beachBiome.beachMin;
        double BEACH_MAX = beachBiome.beachMax;

        double PLAIN_MIN = plainsBiome.plainMin;
        double PLAIN_MAX = plainsBiome.plainMax;

        final double cShelfStart = 0.30D;
        final double cShelfEnd   = 0.45D;
        final double cBeachEnd   = 0.55D;

        final double continentScale = 0.0007D;
        final double detailScale    = 0.0025D;

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                double cRaw = this.continentNoise.noise(gx * continentScale, gz * continentScale);
                double c = (cRaw + 1.0D) * 0.5D;
                c = c * c * (3.0D - 2.0D * c);

                double dRaw = this.terrainNoise.noise(gx * detailScale, gz * detailScale);
                double d = (dRaw + 1.0D) * 0.5D; // [0,1]

                double hDeep   = DEEP_MIN + d * (DEEP_MAX - DEEP_MIN);
                double hPlains = PLAIN_MIN + d * (PLAIN_MAX - PLAIN_MIN);

                double h;

                if (c < cShelfStart) {
                    h = hDeep;

                } else if (c < cShelfEnd) {
                    double t = (c - cShelfStart) / (cShelfEnd - cShelfStart);
                    t = clamp01(t);

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
                    h = computeBeachHeight(c, d, hPlains, BEACH_MIN, BEACH_MAX, cShelfEnd, cBeachEnd);

                } else {
                    h = computePlainsHeightNearCoast(c, d, hPlains, BEACH_MAX, cBeachEnd);
                }

                int ih = (int) Math.round(h);
                if (ih < 4) ih = 4;
                if (ih > worldHeight - 4) ih = worldHeight - 4;

                heightMap[localX][localZ] = ih;
            }
        }

        return heightMap;
    }

    /**
     * 海滩/近岸区域的高度计算逻辑。
     *
     * 效果：
     * - 高度围绕水位附近(BEACH_MIN..BEACH_MAX)
     * - 少量由 d 控制的细微信息
     * - 接向内陆平原高度时，差值被限制在 ±2 格之内，避免“沙滩旁边突然大高坡”
     */
    private double computeBeachHeight(
        double c,
        double d,
        double hPlainsRef,
        double beachMin,
        double beachMax,
        double cShelfEnd,
        double cBeachEnd) {

        double beachMid = (beachMin + beachMax) * 0.5D;
        double smallVar = (d - 0.5D) * 4.0D;
        double baseBeach = beachMid + smallVar;

        if (baseBeach < beachMin) baseBeach = beachMin;
        if (baseBeach > beachMax) baseBeach = beachMax;

        double edgeBandFrac = 0.15D;
        double bandStartC = cBeachEnd - edgeBandFrac * (cBeachEnd - cShelfEnd);
        double tEdge = (c - bandStartC) / (cBeachEnd - bandStartC);
        tEdge = clamp01(tEdge);

        double maxDelta = 2.0D;
        double targetPlain = hPlainsRef;
        if (targetPlain > baseBeach + maxDelta) {
            targetPlain = baseBeach + maxDelta;
        }
        if (targetPlain < baseBeach - maxDelta) {
            targetPlain = baseBeach - maxDelta;
        }

        return baseBeach * (1.0D - tEdge) + targetPlain * tEdge;
    }

    /**
     * 内陆平原的高度计算，同时对靠近海岸线的区域进行“回拉”：
     * - d 控制整体平原起伏（可产生高地或低洼）
     * - 靠近 c ≈ cBeachEnd 的岸边区域，如果高度过高，会被强制压制到一个合理范围，
     *   避免沙滩旁边直接竖起高墙。
     */
    private double computePlainsHeightNearCoast(
        double c,
        double d,
        double hPlains,
        double beachMax,
        double cBeachEnd) {

        double basePlain = hPlains;

        double td = d - 0.5D;
        double adjust = td * td * 16.0D * (td >= 0 ? 1 : -1);
        basePlain += adjust;

        double shorelineBand = 0.05D;
        double tShore = (c - cBeachEnd) / shorelineBand;

        if (tShore <= 0.0D) {
            return basePlain;
        }

        if (tShore > 1.0D) tShore = 1.0D;

        double targetBeach = beachMax - 1.0D;

        double maxDelta = 4.0D;
        if (basePlain > targetBeach + maxDelta) {
            basePlain = targetBeach + maxDelta;
        }

        return targetBeach * (1.0D - tShore) + basePlain * tShore;
    }

    /**
     * 步骤 5：对高度图进行 3×3 邻域平均，平滑高差。
     * 目的：
     * - 去掉独立的\n      小山包或小坑
     * - 减少地形的“噪点”，使海岸线、平原边缘更柔和
     */
    private void smoothHeightMap(int[][] heightMap) {
        final int SIZE = 17;
        int[][] smoothMap = new int[SIZE][SIZE];

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

        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) {
                heightMap[x][z] = smoothMap[x][z];
            }
        }
    }

    /**
     * 步骤 6：确保被标记为 BEACH 的格子的高度不低于水位。
     * 防止出现“沙滩生物群系在水下”的情况。
     */
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

    /**
     * 步骤 7：根据高度图和地貌类型填充实际方块数据：
     *
     * - OCEAN：地面以下全石头，直至 groundHeight；若低于水位则填水柱至水位
     * - BEACH：顶层 1~2 格为沙，下 2~3 格为泥土，再下是石头；若高度低于水位-2，则补水到水位
     * - PLAINS：顶层草，下 3 格泥土，再下石头；若高度低于水位则形成“内陆湖”
     *
     * 填充时按列 (localX, localZ) 纵向遍历 y。
     */
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

                } else { // PLAINS
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

    /**
     * strongCleanOceanFloor:
     * - 清理水下的地表层，把草/泥土/砾石替换为石头。
     * - 判断标准：该方块位于一个“水柱”下方（上方直到水位范围内存在水）。
     * 目的是让海底外观更干净统一，不会出现草块在水下面。
     */
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

    /**
     * buildWideShelf:
     * - 在靠近大陆（continentNoise 介于一定范围）且水下有实心地形的区域，
     *   检测到附近一定范围内存在“露出水面的陆地”时，
     *   会在这片区域内构造一段“大陆架”和“海底陡坡”。
     *
     * 实现方式：
     *   1) 标记 isNearContinent: c ∈ [0.30, 0.52]
     *   2) 标记 hasSolidBelowWater: 该 (x,z) 列在 waterLevel 以下存在实心方块
     *   3) 在曼哈顿半径 50 范围内查找最近的“露出水面的陆地块”
     *   4) 根据距离 nearest 计算一个 shelfTopY 和 cliffBottomY，高度区间内填充石头
     *
     * 效果：在大陆周围生成一圈较宽的石质大陆架，海底从陆地向外有较自然的坡度和平台。
     */
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

    /**
     * extendLandEdgesDown:
     * - 对“靠岸列”（水面之上某几格高度内有草/土/石/沙）进行处理，
     *   如果它们在水下有实心块，则从那个块往下再填充一段石头，使岸壁向下延伸更深。
     *
     * 目的是让海岸线在水下有更厚实的结构，看起来像断崖或较陡的海底坡。
     */
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

    /**
     * smoothBeachPlainEdges:
     * - 用于在沙滩 (sand) 和草地 (grass) 的交界处平滑高度差。
     *
     * 逻辑：
     *   1) 对每一列，找到顶层非空气方块的 yTop；
     *   2) 如果顶层是沙子，检查四个方向相邻列顶部是否有草方块；
     *   3) 若存在草且高度差在 1~2 格之间，则将当前顶层沙子（以及其下一层）改为泥土；
     *
     * 效果：
     * - 避免“沙子高出草地一格或两格”的小悬崖；
     * - 让沙滩逐渐向草地过渡，看上去更自然。
     */
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

    /**
     * 将一个 double 值限制在 [0,1] 范围内。
     */
    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
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
