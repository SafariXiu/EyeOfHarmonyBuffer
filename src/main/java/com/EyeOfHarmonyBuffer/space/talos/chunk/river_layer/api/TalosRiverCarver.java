package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.TalosRiverProfile;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

/**
 * Talos 河道 Carver：
 *
 * 职责：
 *   - 根据 TalosRiverSystem 的河场（距离、宽度、源头位置等）
 *     以及 MacroPackageRegistry 的河谷风格配置，
 *     在已经生成好的基础地形上“切”出类似原版 MC 那种
 *     「河面≈海平面」的河道和源头湖。
 *   - 在河道/湖泊区域内，把 seaLevel 以下挖成河槽 + 水体，
 *     并在水面以上清理一段空气空间，方便植被 / 结构生成。
 *   - 支持“源头湖”效果：在河流源头附近，
 *     生成一个不规则封闭轮廓的大湖面和更深的湖底，
 *     并在湖中心向下挖出一个倒锥形/台阶形“暗河井”，
 *     用于视觉上连接到更深的地下暗河。
 *
 * 使用约定：
 *   - 需要在基础地形（噪声 + 大陆层 + 高山等）已经填好方块之后调用；
 *   - 本类只负责在「河谷/湖泊范围内」替换方块，不处理海底河谷：
 *       仅在 TalosLandMask.isLand(...) == true 的格子上进行挖掘，
 *       这样河流会在接近海岸线时自然终止，不延伸到海底。
 */

public final class TalosRiverCarver {

    private TalosRiverCarver() {}

    /**
     * 在给定 chunk 上挖出河道/源头湖。
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
     * @param seaLevel 海平面高度（河面/湖面高度即为此值）
     * @param worldHeight 世界最大高度（例如 256）
     * @param macroResolver 用于从气候层/宏群系层获取 MacroPackageId
     */
    public static void carveChunkRivers(int chunkX, int chunkZ,
                                        int worldSeedInt,
                                        Block[] blocks, byte[] meta,
                                        int seaLevel,
                                        int worldHeight,
                                        MacroPackageResolver macroResolver) {
        TalosLandMask.Sample[] land =
            TalosLandMask.sampleChunk(chunkX, chunkZ, worldSeedInt);

        TalosRiverSystem.HydroSample[] hydro =
            TalosRiverSystem.sampleHydroFieldChunk(
                chunkX, chunkZ, worldSeedInt, land
            );

        MacroPackageId[] macro = new MacroPackageId[16 * 16];
        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkZ * 16 + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int idx = localX * 16 + localZ;
                macro[idx] = macroResolver.resolveMacroPackageId(
                    chunkX * 16 + localX, worldZ
                );
            }
        }

        carveChunkRivers(
            chunkX, chunkZ, worldSeedInt,
            blocks, meta, seaLevel, worldHeight,
            hydro, macro
        );
    }

    /**
     * chunk 级上下文版本：水文场与宏群系表由调用方预先算好
     * （来自 TalosChunkContext），挖掘时只读表，不再逐列重复采样。
     * 与旧入口对同一坐标产生的结果完全一致。
     */
    public static void carveChunkRivers(int chunkX, int chunkZ,
                                        int worldSeedInt,
                                        Block[] blocks, byte[] meta,
                                        int seaLevel,
                                        int worldHeight,
                                        TalosRiverSystem.HydroSample[] hydroGrid,
                                        MacroPackageId[] macroGrid) {

        final int CHUNK_SIZE = 16;

        final double BASE_LAKE_RADIUS = 48.0; // 源头湖的大致半径
        final double LAKE_RADIUS_PREFACTOR = 1.5; // 预筛范围 = BASE_LAKE_RADIUS * 该系数
        final double LAKE_CENTER_MIN_DEPTH = 16.0; // 湖中心至少比海平面低多少格
        final double SHAFT_RADIUS_FACTOR = 0.18; // 暗河井半径 ≈ 湖半径 * 该系数
        final double UNDERGROUND_EXTRA_DEPTH = 32.0; // 暗河井在湖中心基础上再向下的深度

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = worldX0 + localX;
                int worldZ = worldZ0 + localZ;
                int colIndex = localX * CHUNK_SIZE + localZ;

                TalosRiverSystem.HydroSample hydro =
                    (hydroGrid != null) ? hydroGrid[colIndex] : null;
                if (hydro == null) {
                    continue;
                }

                double dist = hydro.distance;
                double coreWidth = hydro.widthCore;
                double valleyWidth = hydro.widthValley;
                double avoidWidth = hydro.widthAvoid;
                double mask = hydro.mask;

                if (mask <= 0.0 || dist == Double.MAX_VALUE) {
                    continue;
                }
                if (valleyWidth <= 0.0) {
                    continue;
                }

                boolean inSourceLake = false;
                double sx = hydro.sourceX;
                double sz = hydro.sourceZ;
                double sampleX = worldX + 0.5;
                double sampleZ = worldZ + 0.5;

                double dxs = 0.0;
                double dzs = 0.0;
                double radialFromSource = 0.0;

                if (hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {
                    dxs = sampleX - sx;
                    dzs = sampleZ - sz;
                    radialFromSource = Math.sqrt(dxs * dxs + dzs * dzs);

                    if (radialFromSource <= BASE_LAKE_RADIUS * LAKE_RADIUS_PREFACTOR) {
                        inSourceLake = true;
                    }
                }

                if (!inSourceLake && dist > valleyWidth) {
                    continue;
                }

                MacroPackageId macroId =
                    (macroGrid != null) ? macroGrid[colIndex] : null;
                if (macroId == null || macroId == MacroPackageId.OCEANIC) {
                    continue;
                }

                double riverBedYd = TalosRiverProfile.computeRiverBedY(
                    seaLevel,
                    seaLevel,
                    hydro,
                    macroId
                );

                if (inSourceLake && hydro.hasSource && !Double.isNaN(sx) && !Double.isNaN(sz)) {

                    if (radialFromSource > 0.0001) {
                        double theta = Math.atan2(dzs, dxs); // [-π, π]

                        double amp1 = 0.18;
                        double amp2 = 0.10;
                        int k1 = 3;
                        int k2 = 5;

                        double radiusFactor =
                            1.0
                                + amp1 * Math.sin(k1 * theta)
                                + amp2 * Math.cos(k2 * theta);

                        double minFactor = 0.75;
                        double maxFactor = 1.30;
                        if (radiusFactor < minFactor) radiusFactor = minFactor;
                        if (radiusFactor > maxFactor) radiusFactor = maxFactor;

                        double effectiveRadius = BASE_LAKE_RADIUS * radiusFactor;

                        if (radialFromSource <= effectiveRadius) {

                            double rNorm = radialFromSource / effectiveRadius;
                            if (rNorm > 1.0) rNorm = 1.0;

                            double u = 1.0 - rNorm;
                            if (u < 0.0) u = 0.0;

                            double lakeShape = u * u;

                            double baseDepth = seaLevel - riverBedYd;
                            if (baseDepth < 0.0) baseDepth = 0.0;

                            double lakeDepth = LAKE_CENTER_MIN_DEPTH * lakeShape;

                            double finalDepth = Math.max(baseDepth, lakeDepth);

                            double shaftRadius = BASE_LAKE_RADIUS * SHAFT_RADIUS_FACTOR;

                            if (radialFromSource <= shaftRadius) {
                                double rCoreNorm = radialFromSource / shaftRadius;
                                if (rCoreNorm > 1.0) rCoreNorm = 1.0;

                                double shaftShape = 1.0 - rCoreNorm;
                                if (shaftShape < 0.0) shaftShape = 0.0;

                                double targetShaftCenterDepth =
                                    LAKE_CENTER_MIN_DEPTH + UNDERGROUND_EXTRA_DEPTH;

                                double shaftDepthSmooth = targetShaftCenterDepth * shaftShape;

                                int shaftSteps = 4;
                                double stepSize = targetShaftCenterDepth / shaftSteps;

                                int stepIndex = (int) Math.floor(shaftDepthSmooth / stepSize);
                                if (stepIndex < 0) stepIndex = 0;
                                if (stepIndex >= shaftSteps) stepIndex = shaftSteps - 1;

                                double shaftDepth = (stepIndex + 1) * stepSize;

                                finalDepth = Math.max(finalDepth, shaftDepth);
                            }

                            riverBedYd = seaLevel - finalDepth;
                        }
                    }
                }

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
                    blocks[idx] = Blocks.water;
                    meta[idx]   = 0;
                }

                for (int y = seaLevel + 1; y <= seaLevel + 64 && y < worldHeight; y++) {
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
