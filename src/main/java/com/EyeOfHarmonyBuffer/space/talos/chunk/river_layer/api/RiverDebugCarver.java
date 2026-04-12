package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Debug 用：根据 TalosRiverSystem 的河场参数，在一个“平坦、海拔统一”的 chunk 上
 * 挖出一个大致的河道形状，方便可视化宽度和走向。
 *
 * 正式地形不要依赖这里的剖面逻辑，只用于开发调试。
 */

public final class RiverDebugCarver {

    private RiverDebugCarver() {}

    /**
     * 在给定 chunk 上，根据当前河场参数挖 debug 河道：
     *
     * 前置：
     *   - chunk 中 seaLevel 以下已经填好石头/草/水等基础方块；
     *   - 这里仅负责在河谷范围内挖坑 + 填水 + 挖掉水面上几层空气。
     */
    public static void carveFlatChunk(int chunkX, int chunkZ,
                                      int worldSeedInt,
                                      Block[] blocks, byte[] meta,
                                      int seaLevel) {

        final int CHUNK_SIZE = 16;

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = worldX0 + localX;
                int worldZ = worldZ0 + localZ;

                double dist        = TalosRiverSystem.getRiverDistance(worldX, worldZ, worldSeedInt);
                double coreWidth   = TalosRiverSystem.getWidthCore(worldX, worldZ, worldSeedInt);
                double valleyWidth = TalosRiverSystem.getWidthValley(worldX, worldZ, worldSeedInt);
                double mask        = TalosRiverSystem.getRiverMask(worldX, worldZ, worldSeedInt);

                if (valleyWidth <= 0.0 || dist > valleyWidth) {
                    continue;
                }

                double depthMax = 10.0;

                double depthFactor;
                if (dist <= coreWidth) {
                    depthFactor = 1.0;
                } else {
                    double t = (dist - coreWidth) / Math.max(1.0, (valleyWidth - coreWidth));
                    if (t < 0.0) t = 0.0;
                    if (t > 1.0) t = 1.0;
                    depthFactor = 1.0 - t;
                }

                depthFactor *= mask;

                if (depthFactor <= 0.0) {
                    continue;
                }

                int depthBlocks = (int) Math.round(depthMax * depthFactor);
                if (depthBlocks <= 0) {
                    continue;
                }

                int bottomY = seaLevel - depthBlocks + 1;
                if (bottomY < 1) bottomY = 1;

                for (int y = bottomY; y <= seaLevel; y++) {
                    int idx = getIndex(localX, y, localZ);
                    blocks[idx] = Blocks.water;
                    meta[idx] = 0;
                }

                for (int y = seaLevel + 1; y <= seaLevel + 3; y++) {
                    if (y < 0 || y >= 256) break;
                    int idx = getIndex(localX, y, localZ);
                    blocks[idx] = Blocks.air;
                    meta[idx] = 0;
                }
            }
        }
    }

    /** 与 ChunkProviderTalos2 的索引逻辑保持一致。 */
    private static int getIndex(int x, int y, int z) {
        return (x * 16 + z) * 256 + y;
    }
}
