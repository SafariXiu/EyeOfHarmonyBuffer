package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import net.minecraft.world.World;

/**
 * Talos 海陆 / 板块 掩码统一入口。
 *
 * 所有 Minecraft 侧代码（chunk 生成、群系、结构、装饰等）应只通过这里访问，
 * 以保证和 ChunkProviderTalos2 / WorldChunkManagerTalos2 完全一致。
 */

public final class TalosLandMask {

    private TalosLandMask() {}

    /**
     * 将 World.getSeed() 压成 int，用于 WorldgenAPI。
     * 所有地方必须以同一种方式计算 worldSeedInt，否则掩码会不一致。
     */
    public static int getWorldSeedInt(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }

    /**
     * 使用 tile 缓存进行单点采样（推荐的统一接口）：
     *   - worldX, worldZ: 世界方块坐标
     *   - worldSeedInt: 由 getWorldSeedInt(world) 得到
     */
    public static WorldgenAPI.SampleResult sample(int worldX, int worldZ, int worldSeedInt) {
        return WorldgenAPI.samplePointTiled(worldX, worldZ, worldSeedInt);
    }

    /**
     * 方便直接拿 bool 的 isLand。
     */
    public static boolean isLand(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null && r.isLand;
    }

    /**
     * 直接拿板块 ID。
     */
    public static int getPlateId(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.plateId : 0;
    }

    /**
     * 直接拿超级大陆 ID。
     */
    public static int getSuperId(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.superId : 0;
    }

    /**
     * 连续陆地权重 [0,1]
     */
    public static double getLandWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.landWeight : 0.0;
    }

    /**
     * 海岸带权重 [0,1]
     */
    public static double getCoastWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.coastWeight : 0.0;
    }

    /**
     * 宏观边缘权重 [0,1]，0=超级大陆中心，1=外缘
     */
    public static double getEdgeWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.edgeWeight : 0.0;
    }

    /**
     * 如果你在 chunk 内需要频繁访问，可以先拿到 tile 再做本地遍历。
     * 注意：LandTile 内部数组顺序为 [z][x]。
     */
    public static WorldgenAPI.LandTile getTileForChunk(int chunkX, int chunkZ, int worldSeedInt) {
        return WorldgenAPI.getTileForChunk(chunkX, chunkZ, worldSeedInt);
    }
}
