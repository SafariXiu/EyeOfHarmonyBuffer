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
     * 面向上层的完整采样接口，返回 TalosLandMask.Sample，
     * 内部通过 WorldgenAPI.samplePointTiled 实现。
     */
    public static Sample sampleFull(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = WorldgenAPI.samplePointTiled(worldX, worldZ, worldSeedInt);
        return (r != null) ? new Sample(r) : null;
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
     * 以当前世界坐标为锚点，获取所在超级大陆中心的整数 block 坐标。
     *
     * @param worldX       世界方块 X
     * @param worldZ       世界方块 Z
     * @param worldSeedInt 由 getWorldSeedInt(world) 得到
     * @return int[2] = {centerX, centerZ}；若当前位置不在任何超级大陆（superId=0），返回 null。
     */
    public static int[] getSuperCenterXZAt(int worldX, int worldZ, int worldSeedInt) {
        int superId = getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return null;
        }
        return WorldgenAPI.getSuperCenterXZ(superId, worldSeedInt);
    }

    /**
     * 根据 superId 和 worldSeedInt，返回该超级大陆的中心坐标。
     */
    public static int[] getSuperCenterXZById(int superId, int worldSeedInt) {
        if (superId == 0) {
            return null;
        }
        return WorldgenAPI.getSuperCenterXZ(superId, worldSeedInt);
    }

    /**
     * 根据 superId 和 worldSeedInt，返回该超级大陆的 baseRadius。
     */
    public static double getSuperBaseRadius(int superId, int worldSeedInt) {
        if (superId == 0) {
            return 0.0;
        }
        return WorldgenAPI.getSuperBaseRadius(superId, worldSeedInt);
    }

    /**
     * 3.2: TalosLandMask 包装 getLandMaskForChunk，供 ChunkProvider 使用。
     *
     * @param chunkX      区块坐标 X
     * @param chunkZ      区块坐标 Z
     * @param worldSeedInt 由 getWorldSeedInt(world) 得到
     */
    public static LandMask16 getLandMaskForChunk(int chunkX, int chunkZ, int worldSeedInt) {
        return WorldgenAPI.getLandMaskForChunk(chunkX, chunkZ, worldSeedInt);
    }

    /**
     * 3.4: cheap 的 isLand(x,z,seed) 包装。
     *
     * 和上面的 isLand(worldX,worldZ,seed) 的区别：
     *   - isLand(...) 用的是带 tile 缓存 + 完整权重的 SampleResult；
     *   - isLandCheap(...) 只做超级大陆多边形内外判定，不算权重，也不走 tile 层。
     *
     * 建议：
     *   - 如果你已经有 chunk 的 LandMask16，就优先查 LandMask16；
     *   - 只有在完全脱离 chunk 语境的零散点查询时，才直接用这个 cheap 版。
     */
    public static boolean isLandCheap(int worldX, int worldZ, int worldSeedInt) {
        return WorldgenAPI.isLandCheap(worldX, worldZ, worldSeedInt);
    }

    /**
     * 面向 Minecraft 侧的采样结果封装，避免直接依赖 WorldgenAPI.SampleResult。
     */
    public static final class Sample {
        public final boolean isLand;
        public final int plateId;
        public final int superId;

        public final double landWeight;
        public final double coastWeight;
        public final double edgeWeight;
        public final double shelfWeight;

        private Sample(WorldgenAPI.SampleResult r) {
            this.isLand = r.isLand;
            this.plateId = r.plateId;
            this.superId = r.superId;
            this.landWeight = r.landWeight;
            this.coastWeight = r.coastWeight;
            this.edgeWeight = r.edgeWeight;
            this.shelfWeight = r.shelfWeight;
        }
    }
}
