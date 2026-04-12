package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.RiverPolyline;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.RiverRegionLayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.World;

/**
 * Talos 河流 / 水文骨架统一入口（Layer 3）。
 *
 * 所有 Minecraft 侧代码（chunk 生成、地形合成、高山、洞穴、装饰等）
 * 应只通过这里访问河流相关信息，以保证和 RiverRegionLayer 完全一致。
 *
 * 功能：
 *   - worldSeedInt 统一计算（与 TalosLandMask 保持一致）；
 *   - 按 worldSeedInt 缓存 RiverRegionLayer 实例；
 *   - 提供：
 *       * riverDistance(x,z)：到最近河中心线的平面距离（blocks）；
 *       * widthCore/widthValley/widthAvoid：河道/河谷/避让半宽（blocks）；
 *       * riverMask：0..1 掩码（可选）；
 *       * 最近河 ID / 河级别（可选）；
 *       * 主河/支流 polyline（仅调试用）。
 */

public final class TalosRiverSystem {

    private TalosRiverSystem() {}

    /**
     * 将 World.getSeed() 压成 int，用于河流系统。
     * 必须与 TalosLandMask.getWorldSeedInt 保持一致写法。
     */
    public static int getWorldSeedInt(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }

    private static final Int2ObjectOpenHashMap<RiverRegionLayer> LAYERS =
        new Int2ObjectOpenHashMap<>();

    private static RiverRegionLayer getLayer(int worldSeedInt) {
        RiverRegionLayer layer = LAYERS.get(worldSeedInt);
        if (layer == null) {
            layer = new RiverRegionLayer(worldSeedInt);
            LAYERS.put(worldSeedInt, layer);
        }
        return layer;
    }

    /**
     * 到最近河中心线的平面距离（blocks）。
     * 若该点不在任何河影响区附近，返回一个较大的值（例如 > widthAvoid 上界）。
     */
    public static double getRiverDistance(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverDistance(worldX, worldZ);
    }

    /**
     * 河道核心半宽（blocks）。
     * 层 6 在该范围内必须保证水体/河床，不得露干。
     */
    public static double getWidthCore(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthCore(worldX, worldZ);
    }

    /**
     * 河谷半宽（blocks）。
     * 层 6 使用该范围对地形进行 U/V/W 型谷地雕刻。
     */
    public static double getWidthValley(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthValley(worldX, worldZ);
    }

    /**
     * 避让半宽（blocks）。
     * 层 5 高山与层 7 洞穴在该半径内必须衰减/禁洞，给河流让出走廊。
     */
    public static double getWidthAvoid(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthAvoid(worldX, worldZ);
    }

    /**
     * 河影响掩码（0..1）。
     * 0 表示不受河影响，1 表示处于河核心/谷底，主要供层 6 做强度插值使用。
     */
    public static double getRiverMask(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverMask(worldX, worldZ);
    }

    /**
     * 最近河段的 ID（主河/支流），主要用于 debug 或离线分析。
     */
    public static int getNearestRiverId(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getNearestRiverId(worldX, worldZ);
    }

    /**
     * 最近河的级别：0 = 主河，1 = 一级支流，2 = 二级支流 ...
     */
    public static int getRiverLevel(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverLevel(worldX, worldZ);
    }

    /**
     * 返回指定超级大陆（superId）的主河 polyline 列表。
     * 仅用于调试可视化或离线工具。
     */
    public static java.util.List<RiverPolyline> getMainRivers(int superId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getMainRivers(superId);
    }

    /**
     * 返回所有支流 polyline（可选）。
     */
    public static java.util.List<RiverPolyline> getTributaries(int superId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getTributaries(superId);
    }

    public static RiverRegionLayer.RiverSourceDescriptor getRiverSourceById(int riverId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getSourceByRiverId(riverId);
    }

    /**
     * 在给定世界坐标附近查找最近的河源头（只看同一板块的主河）。
     * maxRadiusBlocks <= 0 表示不限制半径。
     */
    public static RiverRegionLayer.RiverSourceDescriptor findNearestSource(int worldX,
                                                                           int worldZ,
                                                                           double maxRadiusBlocks,
                                                                           int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.findNearestSource(worldX, worldZ, maxRadiusBlocks);
    }

    public static final class DebugNearestRiverInfo {
        public final boolean hasRiver;
        public final double distance;
        public final int riverId;
        public final int riverLevel;
        public final int plateId;
        public final double nearestX;
        public final double nearestZ;

        public DebugNearestRiverInfo(boolean hasRiver,
                                     double distance,
                                     int riverId,
                                     int riverLevel,
                                     int plateId,
                                     double nearestX,
                                     double nearestZ) {
            this.hasRiver = hasRiver;
            this.distance = distance;
            this.riverId = riverId;
            this.riverLevel = riverLevel;
            this.plateId = plateId;
            this.nearestX = nearestX;
            this.nearestZ = nearestZ;
        }
    }

    public static DebugNearestRiverInfo debugFindNearestRiver(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        RiverRegionLayer.NearestRiverSample s = layer.debugGetNearestRiverSample(worldX, worldZ);
        return new DebugNearestRiverInfo(
            s.hasRiver, s.distance, s.riverId, s.riverLevel, s.plateId, s.nearestX, s.nearestZ
        );
    }
}
