package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import net.minecraft.world.World;

/**
 * Talos 海陆 / 板块 掩码的 Minecraft 侧统一入口。
 *
 * 所有 Minecraft 端代码（chunk 生成、群系、结构、装饰等）应只通过这里访问，
 * 以保证和 ChunkProviderTalos2 / WorldChunkManagerTalos2 的行为完全一致。
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
     * 单点采样的推荐入口（使用 WorldgenAPI.samplePointTiled，目前等价于 samplePointRaw）。
     *
     * @param worldX       世界方块坐标 X
     * @param worldZ       世界方块坐标 Z
     * @param worldSeedInt 由 getWorldSeedInt(world) 得到的 int 种子
     */
    public static WorldgenAPI.SampleResult sample(int worldX, int worldZ, int worldSeedInt) {
        return WorldgenAPI.samplePointTiled(worldX, worldZ, worldSeedInt);
    }

    /**
     * 面向上层的完整采样封装，避免直接依赖 WorldgenAPI.SampleResult。
     */
    public static Sample sampleFull(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = WorldgenAPI.samplePointTiled(worldX, worldZ, worldSeedInt);
        return (r != null) ? new Sample(r) : null;
    }

    /**
     * 为某个 chunk 一次性采样 16x16 的完整海陆结果。
     *
     * 数组索引约定：idx = localX * 16 + localZ（0..255）。
     * 所有值与逐点调用 sampleFull 完全一致，只是收敛成一次遍历，
     * 供 ChunkProviderTalos2 / TalosChunkContext 复用以避免重复采样。
     */
    public static Sample[] sampleChunk(int chunkX, int chunkZ, int worldSeedInt) {
        Sample[] out = new Sample[16 * 16];
        int worldX0 = chunkX * 16;
        int worldZ0 = chunkZ * 16;

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = worldZ0 + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int idx = localX * 16 + localZ;
                out[idx] = sampleFull(worldX0 + localX, worldZ, worldSeedInt);
            }
        }

        return out;
    }

    /** 直接拿某点的板块 ID。 */
    public static int getPlateId(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.plateId : 0;
    }

    /** 直接拿某点的超级大陆 ID。 */
    public static int getSuperId(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.superId : 0;
    }

    /** 连续陆地权重 [0,1]。 */
    public static double getLandWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.landWeight : 0.0;
    }

    /** 海岸带权重 [0,1]。 */
    public static double getCoastWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.coastWeight : 0.0;
    }

    /** 宏观边缘权重 [0,1]，0 = 超级大陆中心，1 = 外缘。 */
    public static double getEdgeWeight(int worldX, int worldZ, int worldSeedInt) {
        WorldgenAPI.SampleResult r = sample(worldX, worldZ, worldSeedInt);
        return r != null ? r.edgeWeight : 0.0;
    }

    /**
     * 以当前世界坐标为锚点，获取所在超级大陆中心的整数 block 坐标。
     *
     * @return int[2] = {centerX, centerZ}；若当前位置不在任何超级大陆（superId=0），返回 null
     */
    public static int[] getSuperCenterXZAt(int worldX, int worldZ, int worldSeedInt) {
        int superId = getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return null;
        }
        return WorldgenAPI.getSuperCenterXZ(superId, worldSeedInt);
    }

    /** 根据 superId 和 worldSeedInt，返回该超级大陆的中心坐标。 */
    public static int[] getSuperCenterXZById(int superId, int worldSeedInt) {
        if (superId == 0) {
            return null;
        }
        return WorldgenAPI.getSuperCenterXZ(superId, worldSeedInt);
    }

    /** 根据 superId 和 worldSeedInt，返回该超级大陆的 baseRadius。 */
    public static double getSuperBaseRadius(int superId, int worldSeedInt) {
        if (superId == 0) {
            return 0.0;
        }
        return WorldgenAPI.getSuperBaseRadius(superId, worldSeedInt);
    }

    /**
     * 根据 superId 获取该超大陆「从中心指向最近海岸」的流出方向（弧度）。
     * 只依赖超大陆几何、与查询位置无关，河流系统用它作为固定的向海朝向。
     */
    public static double getSuperOutflowAngle(int superId, int worldSeedInt) {
        if (superId == 0) {
            return 0.0;
        }
        return WorldgenAPI.getSuperNearestCoastAngle(superId, worldSeedInt);
    }

    /**
     * 为某个 chunk 获取 16×16 的 LandMask16，给 ChunkProvider 使用。
     *
     * @param chunkX       区块坐标 X
     * @param chunkZ       区块坐标 Z
     * @param worldSeedInt 由 getWorldSeedInt(world) 得到
     */
    public static LandMask16 getLandMaskForChunk(int chunkX, int chunkZ, int worldSeedInt) {
        return WorldgenAPI.getLandMaskForChunk(chunkX, chunkZ, worldSeedInt);
    }

    /**
     * 3.4: cheap 的 isLand(x,z,seed) 包装。
     *
     * 区别：
     *   - 普通 sample(...)：返回完整 SampleResult，带连续权重；
     *   - isLandCheap(...)：只看是否在超级大陆多边形内，不算权重，也不走 tile 层。
     *
     * 建议：
     *   - 如果你已经有 chunk 的 LandMask16，就优先查 LandMask16；
     *   - 完全脱离 chunk 语境的零散点查询，再用这个 cheap 版。
     */
    public static boolean isLandCheap(int worldX, int worldZ, int worldSeedInt) {
        return WorldgenAPI.isLandCheap(worldX, worldZ, worldSeedInt);
    }

    /**
     * Minecraft 侧使用的采样结果封装，避免直接依赖 WorldgenAPI.SampleResult。
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
