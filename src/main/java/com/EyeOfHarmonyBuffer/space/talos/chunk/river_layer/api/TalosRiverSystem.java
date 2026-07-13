package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverSystemRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.SupercontinentRiverSystemRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverQuery;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverQuery.RiverQueryResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSpatialIndex;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplatePicker;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentAdapter;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentInfo;
import net.minecraft.world.World;

import java.util.List;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverQuery.query;

/**
 * Talos 河流 / 水文骨架统一入口（Layer 3 顶层 API）。
 *
 * 新版实现要点（基于 RVR2）：
 *
 * 1. 不再使用 RiverRegionLayer 的程序生成骨架；
 *    而是通过 RiverRegistry + Rvr2Loader 读取预计算的矢量河网（RiverNetwork）。
 *
 * 2. 运行时结构：
 *    - RiverSystem    : 持有 RiverNetwork + RiverSegment 列表 + RiverSpatialIndex
 *    - RiverQuery     : 对给定 worldX/Z 进行「最近河段 + 影响强度」查询
 *    - RiverSystemRegistry : 按 worldSeedInt 缓存/管理 RiverSystem
 *
 * 3. 对外 API 语义**保持不变**：
 *    - 陆地地形场（只在 isLand 上有值）：
 *        getRiverDistance / getWidthCore / getWidthValley / getWidthAvoid / getRiverMask
 *    - 水文场（陆地 + 海洋统一）：
 *        sampleHydroField / getHydroRiverDistance / getHydroWidthValley / getHydroMask
 *
 * 4. 目前 RVR2 尚未包含「源头湖 / 裂隙」数据，因此：
 *    - getRiverSourceById / findNearestSource 暂时返回 null；
 *    - getMainRivers / getTributaries 暂时返回空列表；
 *    - debugFindNearestRiver 使用 RiverSegment 做几何最近点查询（不再依赖板块 polyline）。
 */

public final class TalosRiverSystem {

    private static final double SOURCE_PROGRESS_MAX = 0.04; // 上游 4% 视为源头段
    private static final double SOURCE_RADIUS_MULT  = 2.0; // 源头影响范围 = 2 × channelRadius
    public static double SUPERCONTINENT_RIVER_SCALE = 6.5;

    private TalosRiverSystem() {}

    /**
     * 将 World.getSeed() 压成 int，用于河流系统。
     * 必须与 TalosLandMask.getWorldSeedInt 保持一致写法。
     */
    public static int getWorldSeedInt(World world) {
        return (int) (world.getSeed() & 0x7FFFFFFFL);
    }

    /**
     * 统一水文视角：不做 isLand 过滤，只看 RVR2 河网（现改为：按超级大陆 + 模板实例化的河网）。
     */
    private static RiverQueryResult queryHydro(int worldX, int worldZ, int worldSeedInt) {
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return RiverQuery.RiverQueryResult.none();
        }

        SupercontinentInfo info =
            SupercontinentAdapter.getInfoAt(worldX, worldZ, worldSeedInt);
        if (info == null) {
            return RiverQuery.RiverQueryResult.none();
        }

        String templateId = RiverTemplatePicker.pickTemplateIdForSupercontinent(
            worldSeedInt, superId
        );
        if (templateId == null) {
            return RiverQuery.RiverQueryResult.none();
        }

        double scaleFactor = SUPERCONTINENT_RIVER_SCALE;

        RiverSystem sys = SupercontinentRiverSystemRegistry.getOrCreate(
            worldSeedInt,
            info,
            templateId,
            scaleFactor
        );

        return query(sys.index, worldX, worldZ);
    }

    /**
     * 陆地地形视角：在非陆地（海洋/湖面）上视为无河影响。
     */
    private static RiverQueryResult queryLand(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        if (!r.affected) {
            return RiverQueryResult.none();
        }

        if (!TalosLandMask.isLand(worldX, worldZ, worldSeedInt)) {
            return RiverQueryResult.none();
        }

        return r;
    }

    /**
     * 到最近河中心线的平面距离（blocks）。
     *
     * 语义（陆地视角）：
     *   - 若当前位置在某条河的陆地影响区附近，返回到最近河骨架的距离；
     *   - 若该点不在任何“陆地河影响区”附近，返回一个较大的值（Double.MAX_VALUE），
     *     可视作“无河影响”。
     *
     * 注意：
     *   - 在 TalosLandMask.isLand(...) == false 的格子（海洋 / 湖面），
     *     内部会直接返回 Double.MAX_VALUE。
     */
    public static double getRiverDistance(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryLand(worldX, worldZ, worldSeedInt);
        return r.affected ? r.distanceToCenter : Double.MAX_VALUE;
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
        RiverQueryResult r = queryLand(worldX, worldZ, worldSeedInt);
        return r.affected ? r.channelRadius : 0.0;
    }

    /**
     * 河谷半宽（blocks，陆地视角）。
     *
     * 当前策略（可以后续微调）：
     *   - valleyRadius ≈ 3 × coreRadius
     *   - 即在核心河道外，再预留约 2 倍核心宽度的“谷地缓冲区”。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；海洋格子上将返回 0。
     */
    public static double getWidthValley(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryLand(worldX, worldZ, worldSeedInt);
        if (!r.affected) {
            return 0.0;
        }
        double core = r.channelRadius;
        return core * 3.0;
    }

    /**
     * 避让半宽（blocks，陆地视角）。
     *
     * 当前策略（可以后续微调）：
     *   - avoidRadius ≈ 1.5 × valleyRadius
     *   - 用于山体/洞穴更温和的“外圈避让”。
     *
     * 注意：
     *   - 仅在陆地格子上保证有意义；海洋格子上将返回 0。
     */
    public static double getWidthAvoid(int worldX, int worldZ, int worldSeedInt) {
        double valley = getWidthValley(worldX, worldZ, worldSeedInt);
        return valley > 0.0 ? valley * 1.5 : 0.0;
    }

    /**
     * 河影响掩码（0..1，陆地视角）。
     *
     * 新实现：
     *   - 直接使用 RiverQuery 中的 terrainInfluence（基于 influenceRadius + smoothstep）；
     *
     * 语义：
     *   - 0 表示不受河影响（远离河谷或在海上）；
     *   - 1 表示处于河核心/谷底；
     *   - 在 [0,1] 之间平滑衰减，主要供地形层做强度插值使用。
     */
    public static double getRiverMask(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryLand(worldX, worldZ, worldSeedInt);
        return r.affected ? r.terrainInfluence : 0.0;
    }

    /**
     * 最近河段的 ID（主河/支流），主要用于 debug 或离线分析。
     *
     * 注意：
     *   - 若当前位置远离任何河骨架，返回 0。
     *   - 不区分是否在陆地或海洋，仅基于骨架几何。
     */
    public static int getNearestRiverId(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        return r.affected ? r.edgeId : 0;
    }

    /**
     * 最近河的级别：0 = 主河，1 = 一级支流，2 = 二级支流 ...
     *
     * 注意：
     *   - 若当前位置远离任何河骨架，返回 0；
     *   - 不区分陆地/海洋，仅基于骨架几何。
     */
    public static int getRiverLevel(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        if (!r.affected || r.riverType == null) {
            return 0;
        }
        return switch (r.riverType) {
            case MAIN    -> 0;
            case BRANCH1 -> 1;
            case BRANCH2 -> 2;
        };
    }

    public static final class DebugNearestRiverInfo {
        /** 是否找到了任意河段 */
        public final boolean hasRiver;
        /** 到最近河段的几何距离（blocks） */
        public final double distance;
        /** 最近河段的 ID */
        public final int riverId;
        /** 最近河段的级别（0 主河，1.. 支流） */
        public final int riverLevel;
        /** 当前所在板块 ID（RVR2 版本暂时恒为 0） */
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
     * Debug 几何查询：在全局 RVR2 河网上找到最近河段以及最近点坐标。
     *
     * 特点：
     *   - 不做宽度 / 掩码计算；
     *   - 不做 isLand / plateId 过滤；
     *   - 主要用于调试 / 可视化。
     */
    public static DebugNearestRiverInfo debugFindNearestRiver(int worldX, int worldZ, int worldSeedInt) {
        RiverSystem sys = RiverSystemRegistry.getOrCreate(worldSeedInt);
        RiverSpatialIndex index = sys.index;

        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;

        List<RiverSegment> candidates = index.queryCell(sampleX, sampleZ);
        if (candidates.isEmpty()) {
            return new DebugNearestRiverInfo(false, Double.MAX_VALUE, 0, 0, 0, 0.0, 0.0);
        }

        double bestDistSq = Double.MAX_VALUE;
        RiverSegment bestSeg = null;
        RiverQuery.SegmentProjection bestProj = null;

        for (RiverSegment s : candidates) {
            RiverQuery.SegmentProjection p = RiverQuery.projectToSegment(
                sampleX, sampleZ,
                s.ax, s.az,
                s.bx, s.bz
            );
            if (p.distanceSquared < bestDistSq) {
                bestDistSq = p.distanceSquared;
                bestSeg = s;
                bestProj = p;
            }
        }

        if (bestSeg == null || bestProj == null) {
            return new DebugNearestRiverInfo(false, Double.MAX_VALUE, 0, 0, 0, 0.0, 0.0);
        }

        double dist = Math.sqrt(bestProj.distanceSquared);
        int riverId = bestSeg.edgeId;
        int riverLevel = switch (bestSeg.type) {
            case MAIN    -> 0;
            case BRANCH1 -> 1;
            case BRANCH2 -> 2;
        };

        int plateId = 0;

        return new DebugNearestRiverInfo(
            true,
            dist,
            riverId,
            riverLevel,
            plateId,
            bestProj.closestX,
            bestProj.closestZ
        );
    }

    /**
     * 水文场采样结构：在“陆地 + 海洋”上统一表示河流影响。
     *
     * 字段语义与旧版保持一致。
     *
     * 注意：
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
     * 新实现：
     *   - 直接使用 RVR2 + RiverQuery 的结果；
     *   - 不做 isLand 过滤，海洋上也能看到河谷延伸（方便河口/海底峡谷等逻辑）。
     */
    public static HydroSample sampleHydroField(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);

        if (!r.affected) {
            return new HydroSample(
                Double.MAX_VALUE,
                0.0, 0.0, 0.0,
                0.0,
                0,
                0
            );
        }

        double core   = r.channelRadius;
        double valley = core * 3.0;
        double avoid  = valley * 1.5;

        int riverLevel = 0;
        if (r.riverType != null) {
            riverLevel = switch (r.riverType) {
                case MAIN    -> 0;
                case BRANCH1 -> 1;
                case BRANCH2 -> 2;
            };
        }

        return new HydroSample(
            r.distanceToCenter,
            core,
            valley,
            avoid,
            r.terrainInfluence,
            r.edgeId,
            riverLevel
        );
    }

    public static boolean isNearRiverSource(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        if (!r.affected || !r.hasSource) {
            return false;
        }
        if (r.riverProgress > SOURCE_PROGRESS_MAX) {
            return false;
        }
        if (r.distanceToCenter > r.channelRadius * SOURCE_RADIUS_MULT) {
            return false;
        }
        return true;
    }

    public static boolean isNearRiverMouth(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        if (!r.affected || !r.hasMouth) {
            return false;
        }
        if (r.riverProgress < 1.0 - SOURCE_PROGRESS_MAX) {
            return false;
        }
        if (r.distanceToCenter > r.channelRadius * SOURCE_RADIUS_MULT) {
            return false;
        }
        return true;
    }

    /**
     * 在「当前超级大陆 + 其实例化河网」上，做一次“全局最近河段”的几何查询。
     *
     * 特点：
     *   - 不再使用空间索引的影响半径裁剪；
     *   - 直接在该超级大陆实例化后的所有 RiverSegment 上做最近点搜索；
     *   - 只要这个超级大陆选到了合法模板，就一定能返回一条最近河。
     *
     * 用途：
     *   - Debug 命令：无论你站在这个超级大陆的哪里，都能跳到“这块大陆上的某条最近河”。
     */
    public static DebugNearestRiverInfo debugFindNearestRiverOnSuper(int worldX, int worldZ, int worldSeedInt) {
        System.out.println("[RiverDebug] debugFindNearestRiverOnSuper at pos=("
            + worldX + "," + worldZ + "), seedInt=" + worldSeedInt);

        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        System.out.println("[RiverDebug]  superId = " + superId);

        if (superId == 0) {
            System.out.println("[RiverDebug]  superId=0, return no river.");
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        }

        SupercontinentInfo info = SupercontinentAdapter.getInfoAt(worldX, worldZ, worldSeedInt);
        if (info == null) {
            System.out.println("[RiverDebug]  SupercontinentInfo is null for superId=" + superId);
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        } else {
            System.out.println("[RiverDebug]  SupercontinentInfo: center=("
                + info.centerX + "," + info.centerZ + "), radius="
                + info.radius + ", angleRad=" + info.angleRad);
        }

        String templateId = RiverTemplatePicker.pickTemplateIdForSupercontinent(worldSeedInt, superId);
        System.out.println("[RiverDebug]  picked templateId=" + templateId);

        if (templateId == null) {
            System.out.println("[RiverDebug]  templateId is null, no river template for this superId.");
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        }

        double scaleFactor = SUPERCONTINENT_RIVER_SCALE;
        RiverSystem sys = SupercontinentRiverSystemRegistry.getOrCreate(
            worldSeedInt,
            info,
            templateId,
            scaleFactor
        );

        if (sys == null) {
            System.out.println("[RiverDebug]  RiverSystem is null for templateId=" + templateId);
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        }

        System.out.println("[RiverDebug]  RiverSystem: segments.size="
            + (sys.segments == null ? -1 : sys.segments.size())
            + ", bbox=(" + sys.network.getMinX() + "," + sys.network.getMinZ()
            + ")->(" + sys.network.getMaxX() + "," + sys.network.getMaxZ() + ")");

        if (sys.segments == null || sys.segments.isEmpty()) {
            System.out.println("[RiverDebug]  segments is empty, treat as no river.");
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        }

        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;

        double bestDistSq = Double.MAX_VALUE;
        RiverSegment bestSeg = null;
        RiverQuery.SegmentProjection bestProj = null;

        // === 全局最近段搜索 ===
        for (RiverSegment s : sys.segments) {
            RiverQuery.SegmentProjection p = RiverQuery.projectToSegment(
                sampleX, sampleZ,
                s.ax, s.az,
                s.bx, s.bz
            );
            if (p.distanceSquared < bestDistSq) {
                bestDistSq = p.distanceSquared;
                bestSeg = s;
                bestProj = p;
            }
        }

        if (bestSeg == null || bestProj == null) {
            System.out.println("[RiverDebug]  bestSeg/bestProj is null after scanning segments.");
            return new DebugNearestRiverInfo(
                false,
                Double.MAX_VALUE,
                0,
                0,
                0,
                0.0,
                0.0
            );
        }

        double dist = Math.sqrt(bestProj.distanceSquared);
        int riverId = bestSeg.edgeId;
        int riverLevel = switch (bestSeg.type) {
            case MAIN    -> 0;
            case BRANCH1 -> 1;
            case BRANCH2 -> 2;
        };

        int plateId = 0;

        System.out.println("[RiverDebug]  FOUND nearest river: riverId=" + riverId
            + ", level=" + riverLevel
            + ", dist=" + dist
            + ", closest=(" + bestProj.closestX + "," + bestProj.closestZ + ")");

        return new DebugNearestRiverInfo(
            true,
            dist,
            riverId,
            riverLevel,
            plateId,
            bestProj.closestX,
            bestProj.closestZ
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
