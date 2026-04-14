package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.RiverPolyline;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.RiverRegionLayer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.World;

/**
 * Talos 河流 / 水文骨架统一入口（Layer 3 顶层 API）。
 *
 * 使用约定（非常重要）：
 *
 * 1. 本类提供两大类“河场视图”：
 *
 *    A) 【陆地地形场（Terrain Field）】——只在陆地上有河影响
 *       - getRiverDistance(...)
 *       - getWidthCore(...), getWidthValley(...), getWidthAvoid(...)
 *       - getRiverMask(...)
 *
 *       语义：
 *         * 仅在 TalosLandMask.isLand(x,z) == true 的格子返回有效值；
 *         * 在海洋 / 湖面 / 非陆地区块上，distance 为 Double.MAX_VALUE，
 *           各种 width / mask 为 0，等价于“无河影响”。
 *
 *       适用层：
 *         * 地形雕刻层（层 5/6/7 等）：
 *             - 山体 / 高山对河道的避让（使用 widthAvoid）
 *             - 陆地河谷雕刻（使用 widthValley + mask）
 *             - 洞穴避让 / 减少河床下大洞
 *         * 一切“只想在陆地考虑河流”的系统，都应该使用这一组 API。
 *
 *    B) 【水文场（Hydro Field）】——在陆地 + 海洋上统一采样河影响
 *       - sampleHydroField(...)
 *       - getHydroRiverDistance(...), getHydroWidthValley(...), getHydroMask(...)
 *
 *       语义：
 *         * 在同一板块 (plateId != 0) 内的陆地 + 海洋格子上，
 *           都会返回“到最近河骨架的距离 + 样条宽度 + mask”；
 *         * 不做 TalosLandMask.isLand 过滤；
 *         * 在非常远离任何河的格子，distance 为 Double.MAX_VALUE，
 *           各种 width / mask 为 0。
 *
 *       适用层：
 *         * 河口 / 三角洲 / 海岸侵蚀（需要看到“河流延续入海”的效果）；
 *         * 海底河谷 / 入海沟槽 / 水体模拟；
 *         * Debug / 可视化工具（例如 RiverDebugCarver）。
 *
 * 2. 所有 Minecraft 侧代码（chunk 生成、地形、高山、洞穴、装饰等）：
 *    - 访问“陆地河谷 / 避让”时，应优先使用【陆地地形场】API；
 *    - 只有在 **明确需要海洋上的河场信息** 时，才应使用【水文场】API。
 *
 * 3. 内部实现细节（简要）：
 *    - worldSeedInt 统一从 World.getSeed() 压缩而来（与 TalosLandMask 保持一致）；
 *    - 按 worldSeedInt 缓存 RiverRegionLayer 实例；
 *    - RiverRegionLayer 负责：
 *         * 河骨架 polyline 的生成与缓存；
 *         * 最近河距离 / 宽度 / 掩码的计算；
 *         * 源头湖 / 裂隙等源头信息。
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

    /**
     * 按 worldSeedInt 获取（或创建）对应的 RiverRegionLayer。
     * 对外隐藏具体实现，保证所有调用者共享同一套河骨架与距离场。
     */
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
     *
     * 语义（陆地视角）：
     *   - 若当前位置在某条河的陆地影响区附近，返回到最近河骨架的距离；
     *   - 若该点不在任何“陆地河影响区”附近，返回一个较大的值（例如 > widthAvoid 上界），
     *     可视作“无河影响”。
     *
     * 注意：
     *   - 在 TalosLandMask.isLand(...) == false 的格子（海洋 / 湖面），
     *     内部会直接返回 Double.MAX_VALUE。
     */
    public static double getRiverDistance(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverDistance(worldX, worldZ);
    }

    /**
     * 河道核心半宽（blocks，陆地视角）。
     *
     * 用途：
     *   - 层 6 在该范围内应保证水体/河床，不得露干；
     *   - 也可用于区分“河床核心”与“河谷侧翼”。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；海洋格子上将返回 0。
     */
    public static double getWidthCore(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthCore(worldX, worldZ);
    }

    /**
     * 河谷半宽（blocks，陆地视角）。
     *
     * 用途：
     *   - 层 6 使用该范围对地形进行 U/V/W 型谷地雕刻；
     *   - 可配合 getRiverMask(...) 决定挖谷深度与形状。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；海洋格子上将返回 0。
     */
    public static double getWidthValley(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthValley(worldX, worldZ);
    }

    /**
     * 避让半宽（blocks，陆地视角）。
     *
     * 用途：
     *   - 层 5 高山与层 7 洞穴在该半径内应当衰减/禁洞，给河流让出走廊；
     *   - 通常 >= widthValley，用于“更软”的避让范围。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；海洋格子上将返回 0。
     */
    public static double getWidthAvoid(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getWidthAvoid(worldX, worldZ);
    }

    /**
     * 河影响掩码（0..1，陆地视角）。
     *
     * 语义：
     *   - 0 表示不受河影响（远离河谷或在海上）；
     *   - 1 表示处于河核心/谷底；
     *   - 在 [0,1] 之间平滑衰减，主要供地形层做强度插值使用。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；
     *   - 海洋 / 非陆地格子统一返回 0。
     */
    public static double getRiverMask(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverMask(worldX, worldZ);
    }

    /**
     * 最近河段的 ID（主河/支流），主要用于 debug 或离线分析。
     *
     * 注意：
     *   - 若当前位置远离任何河骨架，返回 0。
     *   - 不区分是否在陆地或海洋，仅基于骨架几何。
     */
    public static int getNearestRiverId(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getNearestRiverId(worldX, worldZ);
    }

    /**
     * 最近河的级别：0 = 主河，1 = 一级支流，2 = 二级支流 ...
     *
     * 注意：
     *   - 若当前位置远离任何河骨架，返回 0；
     *   - 不区分陆地/海洋，仅基于骨架几何。
     */
    public static int getRiverLevel(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getRiverLevel(worldX, worldZ);
    }

    /**
     * 返回指定超级大陆（superId）的主河 polyline 列表。
     *
     * 用途：
     *   - 调试可视化或离线分析；
     *   - 不直接用于地形 / 水文场采样。
     */
    public static java.util.List<RiverPolyline> getMainRivers(int superId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getMainRivers(superId);
    }

    /**
     * 返回所有支流 polyline（可选）。
     *
     * 用途：
     *   - 调试可视化或离线分析；
     *   - 不直接用于地形 / 水文场采样。
     */
    public static java.util.List<RiverPolyline> getTributaries(int superId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getTributaries(superId);
    }

    /**
     * 根据 riverId 获取该河的源头描述（源头湖 + 裂隙等），主要用于
     * 源头地形雕刻或 debug。
     */
    public static RiverRegionLayer.RiverSourceDescriptor getRiverSourceById(int riverId, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.getSourceByRiverId(riverId);
    }

    /**
     * 在给定世界坐标附近查找最近的河源头（只看同一板块的主河）。
     *
     * @param maxRadiusBlocks 最大搜索半径（blocks）；<=0 表示不限制。
     *
     * 用途：
     *   - 源头附近定制地形（源头湖、裂隙等）；
     *   - Debug / 可视化源头分布。
     */
    public static RiverRegionLayer.RiverSourceDescriptor findNearestSource(int worldX,
                                                                           int worldZ,
                                                                           double maxRadiusBlocks,
                                                                           int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        return layer.findNearestSource(worldX, worldZ, maxRadiusBlocks);
    }

    public static final class DebugNearestRiverInfo {
        /** 是否在当前板块下找到了任意河段 */
        public final boolean hasRiver;
        /** 到最近河段的几何距离（blocks） */
        public final double distance;
        /** 最近河段的 ID */
        public final int riverId;
        /** 最近河段的级别（0 主河，1.. 支流） */
        public final int riverLevel;
        /** 当前所在板块 ID */
        public final int plateId;
        /** 最近点在河骨架上的世界坐标 X/Z */
        public final double nearestX;
        public final double nearestZ;

        public DebugNearestRiverInfo(boolean hasRiver,
                                     double distance,
                                     int riverId,
                                     int riverLevel,
                                     int plateId,
                                     double nearestX,
                                     double nearestZ) {
            this.hasRiver  = hasRiver;
            this.distance  = distance;
            this.riverId   = riverId;
            this.riverLevel= riverLevel;
            this.plateId   = plateId;
            this.nearestX  = nearestX;
            this.nearestZ  = nearestZ;
        }
    }

    /**
     * Debug 几何查询：在“当前板块”下找到最近河段以及最近点坐标。
     *
     * 特点：
     *   - 不做宽度 / 掩码计算；
     *   - 不做 isLand 过滤：即使在海洋上，也会基于 polyline 几何给出最近距离；
     *   - 主要用于调试 / 可视化，不建议直接驱动地形逻辑。
     */
    public static DebugNearestRiverInfo debugFindNearestRiver(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        RiverRegionLayer.NearestRiverSample s = layer.debugGetNearestRiverSample(worldX, worldZ);
        return new DebugNearestRiverInfo(
            s.hasRiver, s.distance, s.riverId, s.riverLevel, s.plateId, s.nearestX, s.nearestZ
        );
    }

    /**
     * 水文场采样结构：在“陆地 + 海洋”上统一表示河流影响。
     *
     * 字段语义等价于 RiverRegionLayer.CachedSample，但这是对外暴露的稳定结构。
     *
     * 注意：
     *   - 仅在 plateId != 0 的区域才有可能返回非无限 distance；
     *   - 若 distance == Double.MAX_VALUE 或 widthValley == 0，可认为“无河影响”。
     */
    public static final class HydroSample {
        /** 到最近河中心线的平面距离（blocks） */
        public final double distance;
        /** 河道核心半宽（blocks） */
        public final double widthCore;
        /** 河谷半宽（blocks） */
        public final double widthValley;
        /** 避让半宽（blocks） */
        public final double widthAvoid;
        /** 河影响掩码（0..1，从 valley 边缘到谷底） */
        public final double mask;
        /** 最近河 ID（主河/支流） */
        public final int riverId;
        /** 最近河级别：0 = 主河，1.. 支流等级 */
        public final int riverLevel;

        public HydroSample(double distance,
                           double widthCore,
                           double widthValley,
                           double widthAvoid,
                           double mask,
                           int riverId,
                           int riverLevel) {
            this.distance    = distance;
            this.widthCore   = widthCore;
            this.widthValley = widthValley;
            this.widthAvoid  = widthAvoid;
            this.mask        = mask;
            this.riverId     = riverId;
            this.riverLevel  = riverLevel;
        }
    }

    /**
     * 正式水文 API：在“陆地 + 海洋”上采样河场（Hydro Field）。
     *
     * 行为：
     *   - 在同一板块 (plateId != 0) 内，无论是陆地还是海洋，
     *     都会基于最近河骨架 + 宽度参数返回 distance/width/mask；
     *   - 在远离任何河骨架或不在任何板块中的区域：
     *       distance = Double.MAX_VALUE
     *       widthCore/Valley/Avoid = 0
     *       mask = 0
     *
     * 典型用途：
     *   - 河口 / 三角洲 / 海岸侵蚀；
     *   - 海底河谷 / 入海沟槽；
     *   - 水体模拟 / Debug 可视化（如 RiverDebugCarver）。
     *
     * 注意：
     *   - 若只是做“陆地河谷雕刻 / 山体 / 洞穴避让”，请使用上面的陆地地形场 API，
     *     而不是本方法。
     */
    public static HydroSample sampleHydroField(int worldX, int worldZ, int worldSeedInt) {
        RiverRegionLayer layer = getLayer(worldSeedInt);
        RiverRegionLayer.CachedSample s = layer.sampleHydroField(worldX, worldZ);
        return new HydroSample(
            s.distance, s.widthCore, s.widthValley, s.widthAvoid, s.mask,
            s.riverId, s.riverLevel
        );
    }

    /** 水文场：到最近河中心线的距离（陆地 + 海洋统一视角）。 */
    public static double getHydroRiverDistance(int worldX, int worldZ, int worldSeedInt) {
        return sampleHydroField(worldX, worldZ, worldSeedInt).distance;
    }

    /** 水文场：河谷半宽（陆地 + 海洋统一视角）。 */
    public static double getHydroWidthValley(int worldX, int worldZ, int worldSeedInt) {
        return sampleHydroField(worldX, worldZ, worldSeedInt).widthValley;
    }

    /** 水文场：河影响掩码（陆地 + 海洋统一视角）。 */
    public static double getHydroMask(int worldX, int worldZ, int worldSeedInt) {
        return sampleHydroField(worldX, worldZ, worldSeedInt).mask;
    }
}
