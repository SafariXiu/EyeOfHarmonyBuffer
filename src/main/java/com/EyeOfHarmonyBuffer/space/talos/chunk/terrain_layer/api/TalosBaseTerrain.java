package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainEngine;
import net.minecraft.world.World;

/**
 * 层4：基础地形（Base Height）统一入口。
 *
 * 对 Minecraft 侧只暴露简单 API，不包含任何噪声/宏群系细节。
 */

public final class TalosBaseTerrain {

    private TalosBaseTerrain() {}

    /**
     * 与 TalosLandMask / TalosMacroClimate 一致的 worldSeedInt 计算。
     */
    public static int getWorldSeedInt(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }

    /**
     * 采样单点基础高度。
     *
     * @param worldX       世界 X（blocks）
     * @param worldZ       世界 Z（blocks）
     * @param worldSeedInt 来自 getWorldSeedInt(world)
     * @param seaLevel     全局海平面（例如 64）
     */
    public static double sampleBaseHeight(int worldX, int worldZ,
                                          int worldSeedInt,
                                          int seaLevel) {
        return TerrainEngine.sampleBaseHeight(worldX, worldZ, worldSeedInt, seaLevel);
    }

    /**
     * 预留 chunk 级上下文接口（目前内部仍然是 stateless 的，可以后扩展做缓存）。
     */
    public static final class SampleContext {
        public final int worldSeedInt;
        public final int seaLevel;
        public final int chunkX;
        public final int chunkZ;

        public SampleContext(int worldSeedInt, int seaLevel,
                             int chunkX, int chunkZ) {
            this.worldSeedInt = worldSeedInt;
            this.seaLevel = seaLevel;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }
    }

    public static double sampleBaseHeightWithContext(int worldX, int worldZ,
                                                     SampleContext ctx) {
        return TerrainEngine.sampleBaseHeight(worldX, worldZ,
            ctx.worldSeedInt,
            ctx.seaLevel);
    }
}
