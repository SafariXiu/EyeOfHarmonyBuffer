package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Debug 用：根据 TalosRiverSystem 的河场参数，在一个“平坦、海拔统一”的 chunk 上
 * 挖出一个大致的河道形状，方便可视化宽度和走向。
 *
 * 正式地形不要依赖这里的剖面逻辑，只用于开发调试。
 *
 * 当前版本要点：
 *   - 使用 TalosRiverSystem.sampleHydroField，在陆地 + 海洋上都能拿到河场；
 *   - 不再做海洋缓坡；
 *   - 在河谷影响范围内，从 seaLevel 往下挖出一个简单的 U 型剖面：
 *       * 如果挖到的方块不是石头，就先强制改成石头（方便调试看到“被改过”的区域）；
 *       * 原本是石头的位置则改成水方块；
 *   - 在水面以上挖掉一段空气，方便观察剖面。
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

        final int CHUNK_SIZE   = 16;
        final int WORLD_HEIGHT = 256;

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = worldX0 + localX;
                int worldZ = worldZ0 + localZ;

                TalosRiverSystem.HydroSample h =
                    TalosRiverSystem.sampleHydroField(worldX, worldZ, worldSeedInt);

                double dist        = h.distance;
                double coreWidth   = h.widthCore;
                double valleyWidth = h.widthValley;
                double mask        = h.mask;

                if (valleyWidth > 0.0 && dist <= valleyWidth) {
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

                    if (depthFactor > 0.0) {
                        int depthBlocks = (int) Math.round(depthMax * depthFactor);
                        if (depthBlocks > 0) {

                            int bottomY = seaLevel - depthBlocks + 1;
                            if (bottomY < 1) bottomY = 1;

                            for (int y = bottomY; y <= seaLevel; y++) {
                                if (y < 0 || y >= WORLD_HEIGHT) break;
                                int idx = getIndex(localX, y, localZ);
                                Block b = blocks[idx];

                                if (b != Blocks.stone) {
                                    blocks[idx] = Blocks.stone;
                                    meta[idx]   = 0;
                                    continue;
                                }

                                blocks[idx] = Blocks.water;
                                meta[idx]   = 0;
                            }

                            {
                                int y = bottomY - 1;
                                while (y >= 0 && y < WORLD_HEIGHT) {
                                    int idx = getIndex(localX, y, localZ);
                                    Block b = blocks[idx];

                                    if (b != Blocks.air && b != Blocks.water && b != null) {
                                        break;
                                    }

                                    blocks[idx] = Blocks.stone;
                                    meta[idx]   = 0;

                                    y--;
                                }
                            }

                            for (int y = seaLevel + 1; y <= seaLevel + 32 && y < WORLD_HEIGHT; y++) {
                                if (y < 0) continue;
                                int idx = getIndex(localX, y, localZ);
                                blocks[idx] = Blocks.air;
                                meta[idx]   = 0;
                            }
                        }
                    }
                }
                for (int y = 0; y <= seaLevel && y < WORLD_HEIGHT; y++) {
                    int idx = getIndex(localX, y, localZ);
                    blocks[idx] = Blocks.glowstone;
                    meta[idx]   = 0;
                }
            }
        }
    }

    private static int getIndex(int x, int y, int z) {
        return (x * 16 + z) * 256 + y;
    }
}
