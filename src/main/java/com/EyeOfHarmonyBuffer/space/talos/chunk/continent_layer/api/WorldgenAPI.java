package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.WorldgenCore;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * =====================================================
 * 类名：WorldgenAPI
 * 来源：Python worldgen_api.py 精简版（仅核心采样接口）+ Java 高性能扩展
 * 功能：
 *   - 对外暴露可供 Minecraft 世界生成器调用的核心接口；
 *   - 第一层只输出：
 *       * 海陆布尔值 isLand
 *       * 板块ID plateId
 *       * 超级大陆ID superId
 *   - 性能优化：
 *       * SampleContext 支持 chunk / 区域级多点采样；
 *       * 新增 Tile 级缓存（按固定 TILE_SIZE 预计算一整块海陆布尔 + ID）；
 * =====================================================
 */

public class WorldgenAPI {

    /**
     * 第一层原始地形：
     *   - 对应 WorldgenCore.isLandRaw()
     *   - 无删岛或填湖
     *
     * @param x         世界坐标 X
     * @param z         世界坐标 Z
     * @param worldSeed 全局种子（从 World.getSeed() 推导）
     * @return 海陆布尔、板块ID、超级大陆ID
     */
    public static WorldgenCore.LandResult samplePointRaw(int x, int z, int worldSeed) {
        return WorldgenCore.isLandRaw(x, z, worldSeed);
    }

    /**
     * 对 Minecraft 端暴露的简化结果类型
     *   - isLand : 是否为陆地
     *   - plateId: 板块 / 子大陆 ID
     *   - superId: 超级大陆 ID
     */
    public static class SampleResult {
        public final boolean isLand;
        public final int plateId;
        public final int superId;

        /** 连续陆地权重 [0,1] */
        public final double landWeight;

        /** 海岸带权重 [0,1]（陆地一侧） */
        public final double coastWeight;

        /** 宏观边缘权重 [0,1]，0=超级大陆中心，1=外缘（仅陆地有意义） */
        public final double edgeWeight;

        /** 海洋侧“近海 / 大陆架”权重 [0,1]，0=远洋，1=靠近海岸/大陆架 */
        public final double shelfWeight;

        public SampleResult(boolean isLand, int plateId, int superId,
                            double landWeight, double coastWeight,
                            double edgeWeight, double shelfWeight) {
            this.isLand = isLand;
            this.plateId = plateId;
            this.superId = superId;
            this.landWeight = landWeight;
            this.coastWeight = coastWeight;
            this.edgeWeight = edgeWeight;
            this.shelfWeight = shelfWeight;
        }

        /** 兼容旧调用：默认陆地=1.0，海洋=0.0，海岸/边缘/大陆架=0.0 */
        public SampleResult(boolean isLand, int plateId, int superId) {
            this(isLand, plateId, superId,
                isLand ? 1.0 : 0.0,
                0.0,
                0.0,
                0.0);
        }

        @Override
        public String toString() {
            return String.format(
                "Land=%s, Plate=%d, Super=%d, landW=%.3f, coastW=%.3f, edgeW=%.3f, shelfW=%.3f",
                isLand, plateId, superId, landWeight, coastWeight, edgeWeight, shelfWeight
            );
        }
    }

    /**
     * Chunk / 区域级的采样上下文：
     *   - 预先算好给定矩形范围内的 SuperContinentCenters；
     *   - 持有共享 NoiseFamily 引用；
     *   在这个范围内多次调用，可以避免每个点都重复做重计算。
     */
    public static class SampleContext {
        final WorldgenCore.LandContext landContext;

        public SampleContext(WorldgenCore.LandContext landContext) {
            this.landContext = landContext;
        }
    }

    /**
     * 为一个矩形区域准备采样上下文
     *
     * @param xMin      矩形最小 X（含）
     * @param zMin      矩形最小 Z（含）
     * @param xMax      矩形最大 X（含）
     * @param zMax      矩形最大 Z（含）
     * @param worldSeed 世界种子
     * @return SampleContext，可在该范围内重复使用
     */
    public static SampleContext prepareSampleContextForRect(
        int xMin, int zMin, int xMax, int zMax, int worldSeed) {

        WorldgenCore.LandContext ctx =
            WorldgenCore.prepareLandContextForRect(xMin, zMin, xMax, zMax, worldSeed);
        return new SampleContext(ctx);
    }

    /**
     * 每个 Tile 的边长（以方块为单位）
     * 可根据具体情况调整：256 / 512 均可。
     *   - 值越大：预计算开销越大，但缓存命中率越高；
     *   - 值越小：内存利用更精细，但 tile 数量增加。
     */
    private static final int TILE_SIZE = 256;

    /**
     * Tile 缓存的最大数量。
     * 超过该值后将执行简单的整体清理（可按需改成 LRU）。
     */
    private static final int MAX_TILE_CACHE_SIZE = 4096;

    /**
     * Tile 级别的缓存：
     * key: (tileX, tileZ, worldSeed) 打包成 long
     * val: LandTile（包含当前 tile 的 isLand + plateId + superId）
     */
    private static final Long2ObjectOpenHashMap<LandTile> TILE_CACHE =
        new Long2ObjectOpenHashMap<>();

    /**
     * Tile 结构：预先存好一整块 TILE_SIZE x TILE_SIZE 范围的海陆布尔 + ID。
     * 注意：数组顺序为 [z][x]，与其他局部处理代码约定一致。
     */
    public static class LandTile {
        public final int tileX;
        public final int tileZ;
        public final int size;

        public final boolean[][] isLand; // [size][size]
        public final int[][] plateId;    // [size][size]
        public final int[][] superId;    // [size][size]

        public LandTile(int tileX, int tileZ, int size) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.size = size;
            this.isLand = new boolean[size][size];
            this.plateId = new int[size][size];
            this.superId = new int[size][size];
        }
    }

    /**
     * 将 (tileX, tileZ, worldSeed) 打包成 long 作为 Tile 缓存的 key。
     */
    private static long packTileKey(int tileX, int tileZ, int worldSeed) {
        long k = (((long) tileX) & 0xffffffffL) << 32;
        k |= (((long) tileZ) & 0xffffL) << 16;
        k |= (worldSeed & 0xffff);
        return k;
    }

    /**
     * 根据一个世界坐标点 (x,z) 和 seed，获取或构建对应的 LandTile。
     */
    private static LandTile getOrBuildTileForPoint(int x, int z, int worldSeed) {
        int tileX = Math.floorDiv(x, TILE_SIZE);
        int tileZ = Math.floorDiv(z, TILE_SIZE);
        long key = packTileKey(tileX, tileZ, worldSeed);

        LandTile tile = TILE_CACHE.get(key);
        if (tile != null) {
            return tile;
        }

        // 简易容量控制：超出最大缓存数量时，直接清空
        if (TILE_CACHE.size() >= MAX_TILE_CACHE_SIZE) {
            TILE_CACHE.clear();
        }

        LandTile built = buildTile(tileX, tileZ, worldSeed);
        TILE_CACHE.put(key, built);
        return built;
    }

    /**
     * 构建一个 LandTile：
     *   - 计算该 tile 覆盖的世界坐标矩形；
     *   - 使用 WorldgenCore.prepareLandContextForRect() 预计算 LandContext；
     *   - 扫描该矩形内所有点，填充 isLand + plateId + superId。
     */
    private static LandTile buildTile(int tileX, int tileZ, int worldSeed) {
        int size = TILE_SIZE;
        int x0 = tileX * size;
        int z0 = tileZ * size;
        int x1 = x0 + size - 1;
        int z1 = z0 + size - 1;

        WorldgenCore.LandContext ctx = WorldgenCore.prepareLandContextForRect(x0, z0, x1, z1, worldSeed);
        LandTile tile = new LandTile(tileX, tileZ, size);

        for (int dz = 0; dz < size; dz++) {
            int worldZ = z0 + dz;
            for (int dx = 0; dx < size; dx++) {
                int worldX = x0 + dx;

                WorldgenCore.LandResult raw = WorldgenCore.isLandWithContext(worldX, worldZ, ctx);
                tile.isLand[dz][dx] = raw.isLand;
                tile.plateId[dz][dx] = raw.plateId;
                tile.superId[dz][dx] = raw.superId;
            }
        }

        return tile;
    }

    /**
     * 使用 Tile 缓存进行单点采样：
     *   - 若对应 tile 尚未生成，则会先构建整块 TILE_SIZE x TILE_SIZE；
     *   - 后续相同 tile 内的查询皆为 O(1) 数组访问。
     */
    public static SampleResult samplePointTiled(int x, int z, int worldSeed) {
        LandTile tile = getOrBuildTileForPoint(x, z, worldSeed);

        int localX = Math.floorMod(x, TILE_SIZE);
        int localZ = Math.floorMod(z, TILE_SIZE);

        boolean isLand = tile.isLand[localZ][localX];
        int plateId = tile.plateId[localZ][localX];
        int superId = tile.superId[localZ][localX];

        WorldgenCore.LandResult raw =
            WorldgenCore.isLandRaw(x, z, worldSeed);

        return new SampleResult(
            isLand,
            plateId,
            superId,
            raw.landWeight,
            raw.coastWeight,
            raw.edgeWeight,
            raw.shelfWeight
        );
    }

    /**
     * 方便在 Chunk 生成器中使用的辅助方法：
     *   - 直接获取覆盖某个 chunk 的 LandTile；
     *   - 然后你可以在 chunk 内 16x16 列上按需要做下采样或直接读取。
     *
     * @param chunkX    区块坐标 X（以 chunk 为单位）
     * @param chunkZ    区块坐标 Z（以 chunk 为单位）
     * @param worldSeed 世界种子
     * @return 覆盖该 chunk 的 LandTile（通常是 1 个 tile，但在边界附近 chunk 可能跨多个 tile）
     */
    public static LandTile getTileForChunk(int chunkX, int chunkZ, int worldSeed) {
        int worldX0 = chunkX * 16;
        int worldZ0 = chunkZ * 16;
        return getOrBuildTileForPoint(worldX0, worldZ0, worldSeed);
    }
}
