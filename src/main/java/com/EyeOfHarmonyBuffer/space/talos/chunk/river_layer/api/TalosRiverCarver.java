package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.TalosRiverProfile;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * 正式用河道 Carver：根据 TalosRiverSystem 的河场 + MacroPackageRegistry 的河谷风格，
 * 在真实地形上挖出类似原版 MC 那种“河面=海平面”的河道。
 *
 * 使用约定：
 *   - 需要在基础地形（噪声 + 大陆层 + 高山等）已经填好方块之后调用；
 *   - 本类只负责在「河谷范围内」把 seaLevel 以下挖成河槽 + 水体，
 *     并清理水面以上的一段空气，方便植物 / 结构生成；
 *   - 不做海底河谷：只使用 TalosRiverSystem 的「陆地视角」API，
 *     即只在 isLand==true 的格子上才有宽度 / mask，有利于做“河流截止到海岸线”的效果。
 */

public final class TalosRiverCarver {

    private TalosRiverCarver() {}

    /**
     * 在给定 chunk 上挖出河道。
     *
     * 前置：
     *   - chunk 中 seaLevel 以下已经填好石头/泥土/沙等基础方块；
     *   - 在调用本方法前，不需要专门预挖平地，本方法会直接切穿山体。
     *
     * @param chunkX   区块 X 坐标（按 16 格）
     * @param chunkZ   区块 Z 坐标（按 16 格）
     * @param worldSeedInt 与 TalosLandMask/TalosRiverSystem 一致的世界种子 int
     * @param blocks   ChunkProviderTalos2 使用的方块数组
     * @param meta     方块 metadata 数组
     * @param seaLevel 海平面高度（河面高度即为此值）
     * @param macroResolver 用于从气候层/宏群系层获取 MacroPackageId
     */
    public static void carveChunkRivers(int chunkX, int chunkZ,
                                        int worldSeedInt,
                                        Block[] blocks, byte[] meta,
                                        int seaLevel,
                                        int worldHeight,
                                        MacroPackageResolver macroResolver) {

        final int CHUNK_SIZE   = 16;

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = worldX0 + localX;
                int worldZ = worldZ0 + localZ;

                double dist        = TalosRiverSystem.getRiverDistance(worldX, worldZ, worldSeedInt);
                double coreWidth   = TalosRiverSystem.getWidthCore(worldX, worldZ, worldSeedInt);
                double valleyWidth = TalosRiverSystem.getWidthValley(worldX, worldZ, worldSeedInt);
                double avoidWidth  = TalosRiverSystem.getWidthAvoid(worldX, worldZ, worldSeedInt);
                double mask        = TalosRiverSystem.getRiverMask(worldX, worldZ, worldSeedInt);

                if (valleyWidth <= 0.0 || dist == Double.MAX_VALUE || mask <= 0.0) {
                    continue;
                }
                if (dist > valleyWidth) {
                    continue;
                }

                int riverId    = TalosRiverSystem.getNearestRiverId(worldX, worldZ, worldSeedInt);
                int riverLevel = TalosRiverSystem.getRiverLevel(worldX, worldZ, worldSeedInt);

                TalosRiverSystem.HydroSample hydro = new TalosRiverSystem.HydroSample(
                    dist,
                    coreWidth,
                    valleyWidth,
                    avoidWidth,
                    mask,
                    riverId,
                    riverLevel
                );

                MacroPackageId macroId = macroResolver.resolveMacroPackageId(worldX, worldZ);
                if (macroId == null) {
                    continue;
                }

                double riverBedYd = TalosRiverProfile.computeRiverBedY(
                    seaLevel,
                    seaLevel,
                    hydro,
                    macroId
                );

                int riverBedY = (int) Math.floor(riverBedYd);
                if (riverBedY < 1) {
                    riverBedY = 1;
                }
                if (riverBedY >= seaLevel) {
                    continue;
                }

                for (int y = riverBedY; y <= seaLevel && y < worldHeight; y++) {
                    if (y < 0) continue;

                    int idx = getIndex(localX, y, localZ);
                    Block b = blocks[idx];

                    blocks[idx] = Blocks.water;
                    meta[idx]   = 0;
                }

                for (int y = seaLevel + 1; y <= seaLevel + 32 && y < worldHeight; y++) {
                    if (y < 0) continue;
                    int idx = getIndex(localX, y, localZ);
                    blocks[idx] = Blocks.air;
                    meta[idx]   = 0;
                }
            }
        }
    }

    /** 与 ChunkProviderTalos2 的索引逻辑保持一致。 */
    private static int getIndex(int x, int y, int z) {
        return (x * 16 + z) * 256 + y;
    }

    /**
     * 从气候 / 宏群系层获取 MacroPackageId 的回调接口。
     * 由调用方在 ChunkProviderTalos2 里实现，通常会内部调用
     * 诸如 ClimateLayer / MacroPackageLayer 之类的采样函数。
     */
    public interface MacroPackageResolver {
        MacroPackageId resolveMacroPackageId(int worldX, int worldZ);
    }
}
