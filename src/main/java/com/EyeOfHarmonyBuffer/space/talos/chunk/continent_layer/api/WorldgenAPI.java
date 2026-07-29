package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.geom.TectonicWorld;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.PlateId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.SupercontinentId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.LandType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.TectonicLandSample;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * =====================================================
 * 类名：WorldgenAPI
 * 来源：原 Python worldgen_api.py 精简版（仅核心采样接口） + Java 高性能扩展
 * 功能：
 *   - 对内暴露可供 Minecraft 世界生成器调用的核心接口；
 *   - 第一层输出：
 *       * 海陆布尔值 isLand
 *       * 板块ID plateId
 *       * 超级大陆ID superId
 *       * 一些连续权重：landWeight / coastWeight / edgeWeight / shelfWeight
 *   - 性能优化：
 *       * Tile 级缓存（按固定 TILE_SIZE 预计算一整块海陆布尔 + ID）；
 *   - 注意：
 *       * 这里已经完全切换到 tectonic_v1，不再调用旧的 WorldgenCore。
 * =====================================================
 */

public class WorldgenAPI {

    /**
     * 对 Minecraft 端暴露的简化结果类型
     *   - isLand    : 是否为陆地
     *   - plateId   : 板块 / 子大陆 ID
     *   - superId   : 超级大陆 ID
     *   - landWeight: 连续陆地权重 [0,1]（大陆中心=1，海洋=0）
     *   - coastWeight: 海岸带权重 [0,1]（陆地一侧，靠海=1，内陆=0）
     *   - edgeWeight : 大陆宏观边缘权重 [0,1]（中心=0，外缘=1）
     *   - shelfWeight: 海洋侧“近海 / 大陆架”权重 [0,1]
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

        @Override
        public String toString() {
            return String.format(
                "Land=%s, Plate=%d, Super=%d, landW=%.3f, coastW=%.3f, edgeW=%.3f, shelfW=%.3f",
                isLand, plateId, superId, landWeight, coastWeight, edgeWeight, shelfWeight
            );
        }
    }

    private static final Long2ObjectOpenHashMap<TectonicWorld> TECTONIC_CACHE =
        new Long2ObjectOpenHashMap<TectonicWorld>();

    /**
     * 获取或创建一个 TectonicWorld。
     * 这里 worldSeed 用 int，内部转为 long 即可。
     */
    private static TectonicWorld getTectonicWorld(int worldSeed) {
        long key = worldSeed & 0xFFFFFFFFL;
        TectonicWorld tw = TECTONIC_CACHE.get(key);
        if (tw != null) return tw;

        if (TECTONIC_CACHE.size() > 16) {
            TECTONIC_CACHE.clear();
        }

        tw = new TectonicWorld(worldSeed);
        TECTONIC_CACHE.put(key, tw);
        return tw;
    }

    /**
     * 第一层原始地形采样（不带 tile 缓存）：
     *   - 替代原来的 WorldgenCore.isLandRaw()；
     *   - 直接调用 tectonic_v1 的 TectonicWorld + TectonicLandSample。
     *
     * @param x         世界坐标 X（方块）
     * @param z         世界坐标 Z（方块）
     * @param worldSeed 全局种子（从 World.getSeed() 推导）
     * @return SampleResult：海陆布尔、板块ID、超级大陆ID以及连续权重
     */
    public static SampleResult samplePointRaw(int x, int z, int worldSeed) {
        TectonicWorld world = getTectonicWorld(worldSeed);
        TectonicLandSample s = world.sampleBlock(x, z);

        boolean isLand = (s.landType == LandType.SUPERCONTINENT);

        int superId = 0;
        if (s.supercontinentId != null) {
            SupercontinentId sid = s.supercontinentId;
            superId = sid.toInt();
        }

        int plateId = 0;
        if (s.plateId != null) {
            PlateId pid = s.plateId;
            plateId = pid.toInt();
        }

        double landWeight = isLand ? s.radialCenterward : 0.0;

        double coastWeight = isLand ? s.coastBand : 0.0;

        double shelfWeight = !isLand ? s.shelfBand : 0.0;

        double edgeWeight = isLand ? (1.0 - s.radialCenterward) : 0.0;

        return new SampleResult(
            isLand,
            plateId,
            superId,
            landWeight,
            coastWeight,
            edgeWeight,
            shelfWeight
        );
    }

    // ---------- Tile 级缓存结构 ----------

    /**
     * 每个 Tile 的边长（以方块为单位）
     * 可根据具体情况调整：256 / 512 均可。
     *   - 值越大：预计算开销越大，但缓存命中率越高；
     *   - 值越小：内存利用更精细，但 tile 数量增加。
     *
     * 目前保持 256，不动。
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
        new Long2ObjectOpenHashMap<LandTile>();

    /**
     * Tile 结构：预先存好一整块 TILE_SIZE x TILE_SIZE 范围的海陆布尔 + ID。
     * 注意：数组顺序为 [z][x]。
     *
     * 目前只缓存 isLand / plateId / superId：
     *   - 权重（landWeight / coastWeight / edgeWeight / shelfWeight）
     *     仍然在 samplePointTiled 里按需单点计算；
     *   - 这样先保证行为正确，再以后视需要把权重也缓存进 Tile。
     */
    public static class LandTile {
        public final int tileX;
        public final int tileZ;
        public final int size;

        public final boolean[][] isLand;
        public final int[][] plateId;
        public final int[][] superId;

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
    @Deprecated
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
     *   - 直接调用 samplePointRaw 扫描所有点；
     *   - 只缓存 isLand + plateId + superId 三个字段。
     *
     * 后续如果想把 landWeight / coastWeight / edgeWeight / shelfWeight 也缓存，
     * 可以在这里多存几组 double[][] 或 float[][]。
     */
    private static LandTile buildTile(int tileX, int tileZ, int worldSeed) {
        int size = TILE_SIZE;
        int x0 = tileX * size;
        int z0 = tileZ * size;

        LandTile tile = new LandTile(tileX, tileZ, size);

        for (int dz = 0; dz < size; dz++) {
            int worldZ = z0 + dz;
            for (int dx = 0; dx < size; dx++) {
                int worldX = x0 + dx;

                SampleResult raw = samplePointRaw(worldX, worldZ, worldSeed);
                tile.isLand[dz][dx] = raw.isLand;
                tile.plateId[dz][dx] = raw.plateId;
                tile.superId[dz][dx] = raw.superId;
            }
        }

        return tile;
    }

    /**
     * 使用“轻量封装”的单点采样。
     *
     * 注意：短期内为了性能，已经不再使用 256x256 的 LandTile 预计算，
     * 直接退回到 samplePointRaw。
     *
     * 如果以后需要重新启用 tile 缓存，请先评估对 TectonicWorld.sampleBlock
     * 的调用量。
     */
    public static SampleResult samplePointTiled(int x, int z, int worldSeed) {
        return samplePointRaw(x, z, worldSeed);
    }

    /**
     * 方便在 Chunk 生成器中使用的辅助方法：
     *   - 直接获取覆盖某个 chunk 的 LandTile；
     *   - 然后你可以在 chunk 内 16x16 列上按需要做下采样或直接读取。
     *
     * @param chunkX    区块坐标 X（以 chunk 为单位）
     * @param chunkZ    区块坐标 Z（以 chunk 为单位）
     * @param worldSeed 世界种子
     * @return 覆盖该 chunk 左上角的 LandTile（通常 1 个 tile 足够，
     *         如果 chunk 跨 tile 边界，可以自行根据 worldX/worldZ 再调用 getOrBuildTileForPoint）
     */
    @Deprecated
    public static LandTile getTileForChunk(int chunkX, int chunkZ, int worldSeed) {
        int worldX0 = chunkX * 16;
        int worldZ0 = chunkZ * 16;
        return getOrBuildTileForPoint(worldX0, worldZ0, worldSeed);
    }

    /**
     * 根据 superId 和 worldSeed，返回该超级大陆的中心 block 坐标 [x,z]。
     *
     * @param superId   TalosLandMask / sampleResult 返回的超级大陆ID
     * @param worldSeed 世界种子（int）
     * @return int[2]，元素为 {centerX, centerZ}；如果 superId==0 或异常则返回 null。
     */
    public static int[] getSuperCenterXZ(int superId, int worldSeed) {
        if (superId == 0) {
            return null;
        }

        SupercontinentId sid = SupercontinentId.fromInt(superId);
        TectonicWorld world = getTectonicWorld(worldSeed);

        return world.getSuperCenterBlockPos(sid);
    }

    /**
     * 根据 superId 和 worldSeed，返回该超级大陆的 baseRadius。
     *
     * @param superId   TalosLandMask / SampleResult 返回的超级大陆ID
     * @param worldSeed 世界种子（int）
     * @return 该超级大陆的 baseRadius；若 superId==0 或异常则返回 0.0。
     */
    public static double getSuperBaseRadius(int superId, int worldSeed) {
        if (superId == 0) {
            return 0.0;
        }

        SupercontinentId sid = SupercontinentId.fromInt(superId);
        TectonicWorld world = getTectonicWorld(worldSeed);

        return world.getSuperBaseRadius(sid);
    }

    /**
     * 3.1: 在 WorldgenAPI 暴露 getLandMaskForChunk(chunkX,chunkZ,seed)
     *
     * 注意：
     *   - chunkX / chunkZ 是 Minecraft 的区块坐标（每块 16×16 方块）；
     *   - worldSeed 仍然用你这边的 int 版本（getWorldSeedInt）。
     */
    public static LandMask16 getLandMaskForChunk(int chunkX, int chunkZ, int worldSeed) {
        TectonicWorld world = getTectonicWorld(worldSeed);
        return world.buildLandMaskForChunk(chunkX, chunkZ);
    }

    /**
     * 3.4: cheap 的 isLand(x,z,seed)
     *
     * 特点：
     *   - 只做“是否在最近超级大陆多边形内”的判断；
     *   - 不计算 landWeight / coastWeight / edgeWeight / shelfWeight；
     *   - 不走 tile 缓存（但会走 TectonicWorld 自己内部的 Supercontinent 缓存）。
     *
     * 用途：
     *   - 需要高频 0/1 判定，但不需要连续权重的场景；
     *   - 例如：结构黑名单、随机点筛选等。
     */
    public static boolean isLandCheap(int x, int z, int worldSeed) {
        TectonicWorld world = getTectonicWorld(worldSeed);
        return world.isLandFast(x, z);
    }
}
