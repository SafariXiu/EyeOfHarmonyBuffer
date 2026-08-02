package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverRelation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.SupercontinentRiverSystemRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverQuery;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverQuery.RiverQueryResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSegment;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime.RiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.RiverTemplatePicker;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentAdapter;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template.SupercontinentInfo;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
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
 *
 * 3. 对外 API：
 *    - 陆地地形场（只在 isLand 上有值）：getRiverMask
 *    - 水文场（陆地 + 海洋统一）：sampleHydroField / sampleHydroFieldChunk
 *
 */

public final class TalosRiverSystem {

    private static final double SOURCE_PROGRESS_MAX = 0.04; // 上游 4% 视为源头段
    private static final double SOURCE_RADIUS_MULT  = 2.0;  // 源头影响范围 = 2 × channelRadius
    public static double SUPERCONTINENT_RIVER_SCALE = 1.5;

    private TalosRiverSystem() {}

    /**
     * 预初始化：从本模组 jar 加载内置河网数据（RVR2）。
     * Minecraft 侧统一通过本 API 调用，不要直接使用内部 RiverRegistry。
     */
    public static void onPreInit(FMLPreInitializationEvent event) {
        RiverRegistry.onPreInit(event);
    }

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
        /**
         * 沿河纵向深度倍率（0..1，来自 RiverNetworkProfile）。
         * 1 = 标准深度；入海口 / 源头会低于 1。
         */
        public final double depthScale;
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
                           double depthScale,
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
            this.depthScale    = depthScale;
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
            1.0,
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
            r.depthScale,
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
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);

        if (superId == 0) {
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

        String templateId = RiverTemplatePicker.pickTemplateIdForSupercontinent(worldSeedInt, superId);

        if (templateId == null) {
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

        if (sys.segments == null || sys.segments.isEmpty()) {
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

        RiverSystem sys = resolveSupercontinentRiverSystem(worldX, worldZ, worldSeedInt);
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

    /**
     * 解析当前超级大陆的运行时河网（superId → 超大陆信息 → 模板 → RiverSystem）。
     * 任何一步失败返回 null，供端点 / 汇入点等列表类 API 共用。
     */
    private static RiverSystem resolveSupercontinentRiverSystem(
        int worldX, int worldZ, int worldSeedInt
    ) {
        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return null;
        }

        SupercontinentInfo info = SupercontinentAdapter.getInfoAt(worldX, worldZ, worldSeedInt);
        if (info == null) {
            return null;
        }

        String templateId = RiverTemplatePicker.pickTemplateIdForSupercontinent(worldSeedInt, superId);
        if (templateId == null) {
            return null;
        }

        return SupercontinentRiverSystemRegistry.getOrCreate(
            worldSeedInt,
            info,
            templateId,
            SUPERCONTINENT_RIVER_SCALE
        );
    }

    /** 河流汇入点：某条边与其父河（或主河与海）的接点。 */
    public static final class RiverConfluence {
        /** 当前超级大陆 ID */
        public final int superId;
        /** 汇入点的边 ID（主河为 0，一级支流 / 二级支流为其 edgeId） */
        public final int riverId;
        /** 父河边 ID（主河与海的接点时为 -1） */
        public final int parentRiverId;
        /** 级别：0 = 主河（入海口），1 = 一级支流，2 = 二级支流 */
        public final int riverLevel;
        /** true = 下游分流点（接点在支流起点）；false = 汇入点 / 入海口（接点在支流终点） */
        public final boolean fromParent;
        /** 接点世界坐标 X/Z */
        public final double x;
        public final double z;

        public RiverConfluence(int superId,
                               int riverId,
                               int parentRiverId,
                               int riverLevel,
                               boolean fromParent,
                               double x,
                               double z) {
            this.superId = superId;
            this.riverId = riverId;
            this.parentRiverId = parentRiverId;
            this.riverLevel = riverLevel;
            this.fromParent = fromParent;
            this.x = x;
            this.z = z;
        }
    }

    /**
     * 列出当前超级大陆上指定级别河流的所有汇入点。
     *
     * 语义：
     *   - level = 0：主河与海的接点（入海口，即主河折线末端）；
     *   - level = 1：一级支流与主河的接点；
     *   - level = 2：二级支流与一级支流的接点。
     *   - level < 0 表示不过滤。
     *
     * 汇入 / 分流都算接点：INTO_PARENT 的接点在支流终点，
     * FROM_PARENT（下游分流）的接点在支流起点。
     * 数据来自运行时（截断后）的河网，位置与地图实际一致。
     */
    public static java.util.List<RiverConfluence> listConfluencesOnCurrentSupercontinent(
        int worldX, int worldZ,
        int worldSeedInt,
        int level
    ) {
        java.util.List<RiverConfluence> result = new java.util.ArrayList<RiverConfluence>();

        int superId = TalosLandMask.getSuperId(worldX, worldZ, worldSeedInt);
        if (superId == 0) {
            return result;
        }

        RiverSystem sys = resolveSupercontinentRiverSystem(worldX, worldZ, worldSeedInt);
        if (sys == null || sys.network == null) {
            return result;
        }

        java.util.List<RiverEdgeData> edges = sys.network.getEdges();
        if (edges == null || edges.isEmpty()) {
            return result;
        }

        for (RiverEdgeData e : edges) {
            java.util.List<RiverPoint> pts = e.getPoints();
            if (pts == null || pts.size() < 2) {
                continue;
            }

            int riverLevel = 0;
            if (e.getType() != null) {
                switch (e.getType()) {
                    case MAIN:    riverLevel = 0; break;
                    case BRANCH1: riverLevel = 1; break;
                    case BRANCH2: riverLevel = 2; break;
                }
            }

            if (level >= 0 && riverLevel != level) {
                continue;
            }

            RiverRelation rel = e.getRelation();
            double jx;
            double jz;
            int parentId;
            boolean fromParent;

            if (rel == RiverRelation.ROOT) {
                // 主河：接点 = 入海口（折线末端）
                RiverPoint pn = pts.get(pts.size() - 1);
                jx = pn.getX();
                jz = pn.getZ();
                parentId = -1;
                fromParent = false;
            } else if (rel == RiverRelation.INTO_PARENT) {
                // 汇入支流：接点 = 支流终点
                RiverPoint pn = pts.get(pts.size() - 1);
                jx = pn.getX();
                jz = pn.getZ();
                parentId = e.getParentId();
                fromParent = false;
            } else if (rel == RiverRelation.FROM_PARENT) {
                // 下游分流：接点 = 支流起点（从父河分出）
                RiverPoint p0 = pts.get(0);
                jx = p0.getX();
                jz = p0.getZ();
                parentId = e.getParentId();
                fromParent = true;
            } else {
                continue;
            }

            result.add(new RiverConfluence(
                superId,
                e.getId(),
                parentId,
                riverLevel,
                fromParent,
                jx,
                jz
            ));
        }

        return java.util.Collections.unmodifiableList(result);
    }
}
