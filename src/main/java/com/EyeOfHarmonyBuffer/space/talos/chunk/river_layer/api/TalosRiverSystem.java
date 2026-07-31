package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;
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
 */

public final class TalosRiverSystem {

    private static final double SOURCE_PROGRESS_MAX = 0.04; // 上游 4% 视为源头段
    private static final double SOURCE_RADIUS_MULT  = 2.0;  // 源头影响范围 = 2 × channelRadius
    public static double SUPERCONTINENT_RIVER_SCALE = 1.5;

    private TalosRiverSystem() {}

    /**
     * 将 World.getSeed() 压成 int，用于河流系统。
     * 必须与 TalosLandMask.getWorldSeedInt 保持一致写法。
     */
    public static int getWorldSeedInt(World world) {
        return TalosLandMask.getWorldSeedInt(world);
    }

    /**
     * 统一水文视角：不做 isLand 过滤，只看 RVR2 河网（现改为：按超级大陆 + 模板实例化的河网）。
     */
    private static RiverQueryResult queryHydro(int worldX, int worldZ, int worldSeedInt) {
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        return queryHydroForSuperId(worldX, worldZ, worldSeedInt, superId);
    }

    /**
     * 已知 superId 的水文查询：跳过内部重复的 getSuperId 全量采样。
     * 供 chunk 级批量采样使用（superId 直接读 LandSample 表）。
     */
    private static RiverQueryResult queryHydroForSuperId(
        int worldX, int worldZ, int worldSeedInt, int superId
    ) {
        if (superId == 0) {
            return RiverQuery.RiverQueryResult.none();
        }

        SupercontinentInfo info =
            SupercontinentAdapter.getInfoAt(superId, worldX, worldZ, worldSeedInt);
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

        if (!TalosLandMask.isLandCheap(worldX, worldZ, worldSeedInt)) {
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
            this.hasRiver   = hasRiver;
            this.distance   = distance;
            this.riverId    = riverId;
            this.riverLevel = riverLevel;
            this.plateId    = plateId;
            this.nearestX   = nearestX;
            this.nearestZ   = nearestZ;
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
     * 这里是**改造点之一**：在原有 distance / width / mask / riverId / riverLevel 之外，
     * 正式把 RVR2 / RiverQuery 里的语义字段也带出来：
     *
     *   - riverType    : MAIN / BRANCH1 / BRANCH2
     *   - hasSource    : 是否标记有源头
     *   - hasMouth     : 是否标记有河口
     *   - riverProgress: 0..1，从源头（上游）到河口（下游）的归一化进度
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

        /** RVR2 中的河型：主干 / 一级支流 / 二级支流 */
        public final RiverType riverType;
        /** RVR2 标记：该 edge 是否有源头 */
        public final boolean hasSource;
        /** RVR2 标记：该 edge 是否有河口 */
        public final boolean hasMouth;
        /**
         * 沿当前 edge 的进度（0..1），约定：
         *   - 0   ≈ 上游/源头端（当 hasSource==true 时靠近源头）
         *   - 1   ≈ 下游/河口端（当 hasMouth==true 时靠近河口）
         */
        public final double riverProgress;

        /** 该 edge 源头点在世界坐标中的 X/Z（当 hasSource==true 时有意义） */
        public final double sourceX;
        public final double sourceZ;
        /** 该 edge 河口点在世界坐标中的 X/Z（当 hasMouth==true 时有意义） */
        public final double mouthX;
        public final double mouthZ;

        public HydroSample(double distance,
                           double widthCore,
                           double widthValley,
                           double widthAvoid,
                           double mask,
                           int riverId,
                           int riverLevel,
                           RiverType riverType,
                           boolean hasSource,
                           boolean hasMouth,
                           double riverProgress,
                           double sourceX,
                           double sourceZ,
                           double mouthX,
                           double mouthZ) {
            this.distance      = distance;
            this.widthCore     = widthCore;
            this.widthValley   = widthValley;
            this.widthAvoid    = widthAvoid;
            this.mask          = mask;
            this.riverId       = riverId;
            this.riverLevel    = riverLevel;
            this.riverType     = riverType;
            this.hasSource     = hasSource;
            this.hasMouth      = hasMouth;
            this.riverProgress = riverProgress;
            this.sourceX       = sourceX;
            this.sourceZ       = sourceZ;
            this.mouthX        = mouthX;
            this.mouthZ        = mouthZ;
        }
    }

    /**
     * 正式水文 API：在“陆地 + 海洋”上采样河场（Hydro Field）。
     *
     * 改造点：
     *   - 保持方法签名不变；
     *   - 在 HydroSample 里多带了 riverType / hasSource / hasMouth / riverProgress。
     */
    public static HydroSample sampleHydroField(int worldX, int worldZ, int worldSeedInt) {
        RiverQueryResult r = queryHydro(worldX, worldZ, worldSeedInt);
        return toHydroSample(r);
    }

    /**
     * 为某个 chunk 一次性采样 16x16 水文场。
     *
     * 数组索引约定：idx = localX * 16 + localZ（0..255）。
     * 与逐点调用 sampleHydroField 完全一致，只是 superId 直接复用
     * 已算好的 LandSample 表，跳过每列重复的全量海陆采样。
     */
    public static HydroSample[] sampleHydroFieldChunk(
        int chunkX, int chunkZ, int worldSeedInt,
        TalosLandMask.Sample[] landSamples
    ) {
        HydroSample[] out = new HydroSample[16 * 16];

        for (int localZ = 0; localZ < 16; localZ++) {
            int worldZ = chunkZ * 16 + localZ;
            for (int localX = 0; localX < 16; localX++) {
                int idx = localX * 16 + localZ;
                int worldX = chunkX * 16 + localX;

                int superId = 0;
                TalosLandMask.Sample s =
                    (landSamples != null) ? landSamples[idx] : null;
                if (s != null) {
                    superId = s.superId;
                }

                out[idx] = toHydroSample(
                    queryHydroForSuperId(worldX, worldZ, worldSeedInt, superId)
                );
            }
        }

        return out;
    }

    private static HydroSample noneHydroSample() {
        return new HydroSample(
            Double.MAX_VALUE,
            0.0, 0.0, 0.0,
            0.0,
            0,
            0,
            null,
            false,
            false,
            0.0,
            Double.NaN,
            Double.NaN,
            Double.NaN,
            Double.NaN
        );
    }

    /** 把 RiverQueryResult 转成对外 HydroSample，与 sampleHydroField 的语义一致。 */
    private static HydroSample toHydroSample(RiverQueryResult r) {
        if (!r.affected) {
            return noneHydroSample();
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
            riverLevel,
            r.riverType,
            r.hasSource,
            r.hasMouth,
            r.riverProgress,
            r.sourceX,
            r.sourceZ,
            r.mouthX,
            r.mouthZ
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

    public enum EndpointKind {
        SOURCE,
        MOUTH
    }

    /** 对外暴露的、与实现无关的“河流端点”结构。 */
    public static final class RiverEndpoint {
        /** 当前超级大陆 ID */
        public final int superId;
        /** 对应 RVR2 edgeId（可以视作 riverId） */
        public final int riverId;
        /**
         * 河级：0 = 主河，1 = 一级支流，2 = 二级支流
         * （用来 debug 或者以后排序）
         */
        public final int riverLevel;
        /** 端点类型：true=源头, false=河口 */
        public final boolean isSource;
        /** 端点世界坐标 */
        public final double x;
        public final double z;

        public RiverEndpoint(int superId,
                             int riverId,
                             int riverLevel,
                             boolean isSource,
                             double x,
                             double z) {
            this.superId = superId;
            this.riverId = riverId;
            this.riverLevel = riverLevel;
            this.isSource = isSource;
            this.x = x;
            this.z = z;
        }
    }

    public static java.util.List<RiverEndpoint> listEndpointsOnCurrentSupercontinent(
        int worldX, int worldZ,
        int worldSeedInt,
        java.util.EnumSet<EndpointKind> kinds
    ) {
        java.util.List<RiverEndpoint> result = new java.util.ArrayList<RiverEndpoint>();

        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return result;
        }

        SupercontinentInfo info = SupercontinentAdapter.getInfoAt(worldX, worldZ, worldSeedInt);
        if (info == null) {
            return result;
        }

        String templateId = RiverTemplatePicker.pickTemplateIdForSupercontinent(worldSeedInt, superId);
        if (templateId == null) {
            return result;
        }

        double scaleFactor = SUPERCONTINENT_RIVER_SCALE;
        RiverSystem sys = SupercontinentRiverSystemRegistry.getOrCreate(
            worldSeedInt,
            info,
            templateId,
            scaleFactor
        );
        if (sys == null || sys.network == null) {
            return result;
        }

        java.util.List<RiverEdgeData> edges = sys.network.getEdges();
        if (edges == null || edges.isEmpty()) {
            return result;
        }

        for (RiverEdgeData e : edges) {
            java.util.List<RiverPoint> pts = e.getPoints();
            if (pts == null || pts.size() < 2) continue;

            int riverLevel = 0;
            if (e.getType() != null) {
                switch (e.getType()) {
                    case MAIN:    riverLevel = 0; break;
                    case BRANCH1: riverLevel = 1; break;
                    case BRANCH2: riverLevel = 2; break;
                }
            }

            if (e.hasSource() && kinds.contains(EndpointKind.SOURCE)) {
                RiverPoint p0 = pts.get(0);
                result.add(new RiverEndpoint(
                    superId,
                    e.getId(),
                    riverLevel,
                    true,
                    p0.getX(),
                    p0.getZ()
                ));
            }

            if (e.hasMouth() && kinds.contains(EndpointKind.MOUTH)) {
                RiverPoint pn = pts.get(pts.size() - 1);
                result.add(new RiverEndpoint(
                    superId,
                    e.getId(),
                    riverLevel,
                    false,
                    pn.getX(),
                    pn.getZ()
                ));
            }
        }

        return java.util.Collections.unmodifiableList(result);
    }
}
