package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

/**
 * 按 worldSeedInt 管理一个世界尺度的河流区域层。
 *
 * 新版要点（局部 / 骨架式）：
 *   - 不再构建 PlateFlowField、也不再做整板块 BFS 距离场；
 *   - 仅为每个 plateId：
 *       * 通过随机采样估计一个大致的板块中心 + 近似半径（PlateInfo）；
 *       * 从该中心向外投射若干射线，找到陆地→非陆地的边界作为 mouth；
 *       * 从每个 mouth 出发，朝板块中心方向 + 噪声步进生长主河折线；
 *   - 对外接口（距离场、宽度、掩码、最近河查询等）保持不变。
 */

public final class RiverRegionLayer {

    /** riverCell 的物理边长（blocks），用于与旧实现兼容（例如某些常量换算） */
    public static final int RIVER_CELL_TO_BLOCK = 256;

    /** 主河最小长度（blocks） */
    private static final int MAIN_RIVER_MIN_LENGTH_BLOCKS = 10_000;
    /** 主河建议最大长度（blocks） */
    private static final int MAIN_RIVER_MAX_LENGTH_BLOCKS = 40_000;

    /** 主河生长时的最大步数（逻辑步，而非 cell） */
    private static final int GROW_STEP_LIMIT = 2_000;

    /** 河口之间的最小间距（blocks） */
    private static final int MIN_MOUTH_SPACING_BLOCKS = 20_000;
    private static final double MIN_MOUTH_SPACING_BLOCKS_SQ =
        MIN_MOUTH_SPACING_BLOCKS * (double) MIN_MOUTH_SPACING_BLOCKS;

    private final int worldSeedInt;

    /** 每个 plateId 的主河折线 */
    private final java.util.Map<Integer, java.util.List<RiverPolyline>> mainRiversByPlateId = new java.util.concurrent.ConcurrentHashMap<>();
    /** 每个 plateId 的支流（当前还未生成，保留结构） */
    private final java.util.Map<Integer, java.util.List<RiverPolyline>> tributariesByPlateId = new java.util.concurrent.ConcurrentHashMap<>();
    /** 每条河的源头描述（按 riverId 索引） */
    private final java.util.Map<Integer, RiverSourceDescriptor> sourceByRiverId =
        new java.util.concurrent.ConcurrentHashMap<>();

    /** 每个 plateId 的简化板块信息（中心 + 近似半径），懒加载 */
    private final java.util.Map<Integer, PlateInfo> plateInfoById = new java.util.concurrent.ConcurrentHashMap<>();

    private static final int CACHE_SHIFT = 0;
    private final java.util.Map<Long, CachedSample> sampleCache = new java.util.concurrent.ConcurrentHashMap<>();

    public RiverRegionLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    public double getRiverDistance(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.distance;
    }

    public double getWidthCore(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.widthCore;
    }

    public double getWidthValley(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.widthValley;
    }

    public double getWidthAvoid(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.widthAvoid;
    }

    public double getRiverMask(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.mask;
    }

    public int getNearestRiverId(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.riverId;
    }

    public int getRiverLevel(int worldX, int worldZ) {
        CachedSample s = sampleCached(worldX, worldZ);
        return s.riverLevel;
    }

    public RiverRegionLayer.RiverSourceDescriptor getSourceByRiverId(int riverId) {
        return sourceByRiverId.get(riverId);
    }

    public RiverSourceDescriptor findNearestSource(int worldX, int worldZ, double maxRadiusBlocks) {
        int plateId = TalosLandMask.getPlateId(worldX, worldZ, worldSeedInt);
        if (plateId == 0) return null;

        ensureRiversForPlate(plateId);

        java.util.List<RiverPolyline> mainList = mainRiversByPlateId.get(plateId);
        if (mainList == null || mainList.isEmpty()) return null;

        RiverSourceDescriptor best = null;
        double bestDistSq = maxRadiusBlocks > 0.0 ? maxRadiusBlocks * maxRadiusBlocks : Double.MAX_VALUE;

        for (RiverPolyline rp : mainList) {
            RiverSourceDescriptor src = sourceByRiverId.get(rp.id);
            if (src == null) continue;

            double dx = worldX - src.centerX;
            double dz = worldZ - src.centerZ;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                best = src;
            }
        }

        return best;
    }

    /**
     * 调试 / 可视化用途：拿到某个 plateId 的主河。
     */
    public java.util.List<RiverPolyline> getMainRivers(int plateId) {
        ensureRiversForPlate(plateId);
        java.util.List<RiverPolyline> list = mainRiversByPlateId.get(plateId);
        return list != null ? java.util.Collections.unmodifiableList(list) : java.util.Collections.emptyList();
    }

    /**
     * 调试 / 可视化用途：拿到某个 plateId 的支流。
     */
    public java.util.List<RiverPolyline> getTributaries(int plateId) {
        ensureRiversForPlate(plateId);
        java.util.List<RiverPolyline> list = tributariesByPlateId.get(plateId);
        return list != null ? java.util.Collections.unmodifiableList(list) : java.util.Collections.emptyList();
    }

    public static final class NearestRiverSample {
        public final boolean hasRiver;
        public final double distance;
        public final int riverId;
        public final int riverLevel;
        public final int plateId;
        public final double nearestX;
        public final double nearestZ;

        public NearestRiverSample(boolean hasRiver,
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

    /** 单条河的源头描述（湖 + 中央裂隙） */
    public static final class RiverSourceDescriptor {
        public final int riverId;
        public final int plateId;
        public final int level;

        /** 源头湖中心（世界坐标，blocks） */
        public final double centerX;
        public final double centerZ;

        /** 源头湖半径（水平，大致圆形半径） */
        public final double lakeRadiusBlocks;

        /** 湖盆最大下挖深度（相对周围地表） */
        public final double lakeDepthBlocks;

        /** 中央裂隙半径（水平半径） */
        public final double fissureRadiusBlocks;

        /** 裂隙最大深度（相对周围地表，可以比 lakeDepth 更深） */
        public final double fissureDepthBlocks;

        /** 源头出水方向（从湖流向下游的单位向量） */
        public final double outflowDirX;
        public final double outflowDirZ;

        public RiverSourceDescriptor(int riverId,
                                     int plateId,
                                     int level,
                                     double centerX,
                                     double centerZ,
                                     double lakeRadiusBlocks,
                                     double lakeDepthBlocks,
                                     double fissureRadiusBlocks,
                                     double fissureDepthBlocks,
                                     double outflowDirX,
                                     double outflowDirZ) {
            this.riverId = riverId;
            this.plateId = plateId;
            this.level = level;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.lakeRadiusBlocks = lakeRadiusBlocks;
            this.lakeDepthBlocks = lakeDepthBlocks;
            this.fissureRadiusBlocks = fissureRadiusBlocks;
            this.fissureDepthBlocks = fissureDepthBlocks;
            this.outflowDirX = outflowDirX;
            this.outflowDirZ = outflowDirZ;
        }
    }

    /**
     * Debug：返回当前点在本板块下最近的河段信息 + 最近点坐标。
     * 不做宽度/掩码计算，也不做 isLand 过滤，只是几何上的最近。
     */
    public NearestRiverSample debugGetNearestRiverSample(int worldX, int worldZ) {

        int plateId = TalosLandMask.getPlateId(worldX, worldZ, worldSeedInt);
        if (plateId == 0) {
            return new NearestRiverSample(false, Double.MAX_VALUE, 0, 0, 0, 0.0, 0.0);
        }

        ensureRiversForPlate(plateId);

        java.util.List<RiverPolyline> mainList = mainRiversByPlateId.get(plateId);
        java.util.List<RiverPolyline> tribList = tributariesByPlateId.get(plateId);

        if ((mainList == null || mainList.isEmpty()) &&
            (tribList == null || tribList.isEmpty())) {
            return new NearestRiverSample(false, Double.MAX_VALUE, 0, 0, plateId, 0.0, 0.0);
        }

        double bestDist = Double.MAX_VALUE;
        int   bestRiverId = 0;
        int   bestRiverLevel = 0;
        double bestNearestX = 0.0;
        double bestNearestZ = 0.0;

        if (mainList != null) {
            for (RiverPolyline rp : mainList) {
                ClosestResult r = findClosestOnPolylineWithPoint(rp, worldX, worldZ);
                if (r.distance < bestDist) {
                    bestDist = r.distance;
                    bestRiverId = rp.id;
                    bestRiverLevel = rp.level;
                    bestNearestX = r.nearestX;
                    bestNearestZ = r.nearestZ;
                }
            }
        }

        if (tribList != null) {
            for (RiverPolyline rp : tribList) {
                ClosestResult r = findClosestOnPolylineWithPoint(rp, worldX, worldZ);
                if (r.distance < bestDist) {
                    bestDist = r.distance;
                    bestRiverId = rp.id;
                    bestRiverLevel = rp.level;
                    bestNearestX = r.nearestX;
                    bestNearestZ = r.nearestZ;
                }
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            return new NearestRiverSample(false, Double.MAX_VALUE, 0, 0, plateId, 0.0, 0.0);
        }

        return new NearestRiverSample(true, bestDist, bestRiverId, bestRiverLevel,
            plateId, bestNearestX, bestNearestZ);
    }

    private static final class CachedSample {
        final double distance;
        final double widthCore;
        final double widthValley;
        final double widthAvoid;
        final double mask;
        final int riverId;
        final int riverLevel;

        CachedSample(double distance, double widthCore, double widthValley,
                     double widthAvoid, double mask, int riverId, int riverLevel) {
            this.distance = distance;
            this.widthCore = widthCore;
            this.widthValley = widthValley;
            this.widthAvoid = widthAvoid;
            this.mask = mask;
            this.riverId = riverId;
            this.riverLevel = riverLevel;
        }
    }

    private CachedSample sampleCached(int worldX, int worldZ) {
        int keyX = worldX >> CACHE_SHIFT;
        int keyZ = worldZ >> CACHE_SHIFT;
        long key = (((long) keyX) << 32) ^ (keyZ & 0xFFFFFFFFL);

        CachedSample s = sampleCache.get(key);
        if (s != null) {
            return s;
        }

        s = sampleRaw(worldX, worldZ);
        sampleCache.put(key, s);
        return s;
    }

    /**
     * 真正的河场采样逻辑。
     */
    private CachedSample sampleRaw(int worldX, int worldZ) {

        int plateId = TalosLandMask.getPlateId(worldX, worldZ, worldSeedInt);
        if (plateId == 0) {
            return new CachedSample(Double.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        }

        ensureRiversForPlate(plateId);

        java.util.List<RiverPolyline> mainList = mainRiversByPlateId.get(plateId);
        java.util.List<RiverPolyline> tribList = tributariesByPlateId.get(plateId);

        if ((mainList == null || mainList.isEmpty()) &&
            (tribList == null || tribList.isEmpty())) {
            return new CachedSample(Double.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        }

        double bestDist = Double.MAX_VALUE;
        int   bestRiverId = 0;
        int   bestRiverLevel = 0;
        double bestTAlong = 0.0; // [0,1]，河上参数

        if (mainList != null) {
            for (RiverPolyline rp : mainList) {
                ClosestResult r = findClosestOnPolyline(rp, worldX, worldZ);
                if (r.distance < bestDist) {
                    bestDist = r.distance;
                    bestRiverId = rp.id;
                    bestRiverLevel = rp.level;
                    bestTAlong = r.tAlong;
                }
            }
        }

        if (tribList != null) {
            for (RiverPolyline rp : tribList) {
                ClosestResult r = findClosestOnPolyline(rp, worldX, worldZ);
                if (r.distance < bestDist) {
                    bestDist = r.distance;
                    bestRiverId = rp.id;
                    bestRiverLevel = rp.level;
                    bestTAlong = r.tAlong;
                }
            }
        }

        if (bestDist == Double.MAX_VALUE) {
            return new CachedSample(Double.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        }

        WidthTriplet w = computeWidthsForSample(plateId, bestRiverLevel, bestTAlong);

        if (!TalosLandMask.isLand(worldX, worldZ, worldSeedInt)) {
            return new CachedSample(Double.MAX_VALUE, 0, 0, 0, 0, 0, 0);
        }

        double mask = 0.0;
        if (w.widthValley > 0.0) {
            double t = bestDist / w.widthValley;
            t = clamp01(t);
            mask = 1.0 - smoothstep01(t);
        }

        return new CachedSample(bestDist, w.widthCore, w.widthValley, w.widthAvoid,
            mask, bestRiverId, bestRiverLevel);
    }

    private static final class ClosestResult {
        final double distance;
        final double tAlong;   // 0..1
        final double nearestX;
        final double nearestZ;

        ClosestResult(double distance, double tAlong, double nearestX, double nearestZ) {
            this.distance = distance;
            this.tAlong = tAlong;
            this.nearestX = nearestX;
            this.nearestZ = nearestZ;
        }
    }

    /**
     * 求一点对一条 polyline 的最近距离及沿程参数 t。
     * t = 0 表示在首点附近，t = 1 表示在尾点附近。
     */
    private static ClosestResult findClosestOnPolyline(RiverPolyline rp, double x, double z) {
        java.util.List<RiverPolyline.Node> nodes = rp.nodes;
        int size = nodes.size();
        if (size == 0) {
            return new ClosestResult(Double.MAX_VALUE, 0.0, x, z);
        }
        if (size == 1) {
            RiverPolyline.Node n = nodes.get(0);
            double dx = x - n.x;
            double dz = z - n.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            return new ClosestResult(dist, 0.0, n.x, n.z);
        }

        double bestDistSq = Double.MAX_VALUE;
        double bestS = 0.0;
        double bestX = 0.0;
        double bestZ = 0.0;

        double totalLength = 0.0;
        double sAccum = 0.0;

        for (int i = 0; i < size - 1; i++) {
            RiverPolyline.Node a = nodes.get(i);
            RiverPolyline.Node b = nodes.get(i + 1);

            double vx = b.x - a.x;
            double vz = b.z - a.z;

            double segLenSq = vx * vx + vz * vz;
            if (segLenSq <= 0.0) segLenSq = 1e-12;
            double segLen = Math.sqrt(segLenSq);

            totalLength += segLen;

            double wx = x - a.x;
            double wz = z - a.z;

            double t = (wx * vx + wz * vz) / segLenSq;
            if (t < 0.0) t = 0.0;
            else if (t > 1.0) t = 1.0;

            double projX = a.x + vx * t;
            double projZ = a.z + vz * t;

            double dx = x - projX;
            double dz = z - projZ;
            double distSq = dx * dx + dz * dz;

            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                bestX = projX;
                bestZ = projZ;
                bestS = sAccum + segLen * t;
            }

            sAccum += segLen;
        }

        double bestDist = Math.sqrt(bestDistSq);
        double tAlong = (totalLength > 0.0) ? (bestS / totalLength) : 0.0;
        if (tAlong < 0.0) tAlong = 0.0;
        else if (tAlong > 1.0) tAlong = 1.0;

        return new ClosestResult(bestDist, tAlong, bestX, bestZ);
    }

    /**
     * 求一点对一条 polyline 的最近距离、沿程参数 t 以及最近点坐标。
     * t = 0 表示在首点附近，t = 1 表示在尾点附近。
     *
     * 当前实现与 findClosestOnPolyline 完全一致，只是保留独立方法名方便调试调用。
     */
    private static ClosestResult findClosestOnPolylineWithPoint(RiverPolyline rp, double x, double z) {
        return findClosestOnPolyline(rp, x, z);
    }

    private static final class WidthTriplet {
        final double widthCore;
        final double widthValley;
        final double widthAvoid;

        WidthTriplet(double widthCore, double widthValley, double widthAvoid) {
            this.widthCore = widthCore;
            this.widthValley = widthValley;
            this.widthAvoid = widthAvoid;
        }
    }

    /**
     * 当前策略：
     *   - 主河(level=0)：基准宽度较大；
     *   - 支流(level>=1)：宽度按级别缩小；
     *   - 不同 plateId 宽度有少量随机差异；
     *   - 沿河长度方向加一点 sin 噪声；
     *   - 保证 widthCore <= widthValley <= widthAvoid。
     *
     * 后续可以在这里接入真正的 riverStyleParams(x,z) 混合逻辑。
     */
    private WidthTriplet computeWidthsForSample(int plateId, int riverLevel, double tAlong) {
        double baseCore = 18.0; // 主河核心直径 ~36
        double baseValley = 72.0; // 主河谷地直径 ~144
        double baseAvoid = 120.0; // 主河避让直径 ~240

        if (riverLevel > 0) {
            double factor = 1.0 / (riverLevel + 1.5); // 1 -> ~0.4, 2 -> ~0.29...
            baseCore *= factor;
            baseValley *= factor;
            baseAvoid *= factor;
        }

        long key = (((long) worldSeedInt) << 32) ^ (plateId & 0xFFFFFFFFL);
        java.util.Random rand = new java.util.Random(hash64(key, 0xBEEFCAFEL));

        double jitterCore = 0.75 + rand.nextDouble() * 0.5; // 0.75..1.25
        double jitterValley = 0.75 + rand.nextDouble() * 0.5; // 0.75..1.25
        double jitterAvoid = 0.75 + rand.nextDouble() * 0.5; // 0.75..1.25

        double phase = tAlong * Math.PI * 2.0;
        double sinFactor = 1.0 + 0.25 * Math.sin(phase * 2.0 + plateId * 0.37);

        double widthCore = baseCore * jitterCore * sinFactor;
        double widthValley = baseValley * jitterValley * sinFactor;
        double widthAvoid = baseAvoid * jitterAvoid * sinFactor;

        if (widthValley < widthCore) {
            widthValley = widthCore;
        }
        if (widthAvoid < widthValley) {
            widthAvoid = widthValley;
        }

        return new WidthTriplet(widthCore, widthValley, widthAvoid);
    }

    /** 板块简化信息：中心 + 近似半径 */
    private static final class PlateInfo {
        final double centerX;
        final double centerZ;
        final double approxRadius;

        PlateInfo(double centerX, double centerZ, double approxRadius) {
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.approxRadius = approxRadius;
        }
    }

    /**
     * 获取（或构建）给定 plateId 的 PlateInfo。
     */
    private PlateInfo getPlateInfo(int plateId) {
        if (plateId == 0) return null;
        PlateInfo info = plateInfoById.get(plateId);
        if (info != null) return info;

        synchronized (this) {
            info = plateInfoById.get(plateId);
            if (info != null) return info;
            PlateInfo built = computePlateInfo(plateId);
            plateInfoById.put(plateId, built);
            return built;
        }
    }

    /**
     * 通过随机采样估计板块中心和近似半径。
     * 只做有限次数 TalosLandMask 采样，完全不 BFS。
     */
    private PlateInfo computePlateInfo(int plateId) {
        final int MAX_SAMPLES = 256;
        final int SEARCH_RADIUS_BLOCKS = 200_000;

        java.util.Random rand = new java.util.Random(
            hash64(worldSeedInt, plateId, 0xC0DEC0DEL)
        );

        double sumX = 0.0;
        double sumZ = 0.0;
        int count = 0;

        for (int i = 0; i < MAX_SAMPLES; i++) {
            double r = rand.nextDouble() * SEARCH_RADIUS_BLOCKS;
            double a = rand.nextDouble() * Math.PI * 2.0;
            int wx = (int) Math.round(Math.cos(a) * r);
            int wz = (int) Math.round(Math.sin(a) * r);

            int pid = TalosLandMask.getPlateId(wx, wz, worldSeedInt);
            if (pid != plateId) continue;
            if (!TalosLandMask.isLand(wx, wz, worldSeedInt)) continue;

            sumX += wx;
            sumZ += wz;
            count++;
        }

        if (count == 0) {
            return new PlateInfo(0.0, 0.0, 0.0);
        }

        double cx = sumX / count;
        double cz = sumZ / count;

        double maxR = 0.0;
        for (int i = 0; i < MAX_SAMPLES; i++) {
            double r = rand.nextDouble() * SEARCH_RADIUS_BLOCKS;
            double a = rand.nextDouble() * Math.PI * 2.0;
            int wx = (int) Math.round(Math.cos(a) * r);
            int wz = (int) Math.round(Math.sin(a) * r);

            int pid = TalosLandMask.getPlateId(wx, wz, worldSeedInt);
            if (pid != plateId) continue;
            if (!TalosLandMask.isLand(wx, wz, worldSeedInt)) continue;

            double dx = wx - cx;
            double dz = wz - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > maxR) maxR = dist;
        }

        if (maxR < 10_000.0) {
            maxR = 10_000.0;
        }

        return new PlateInfo(cx, cz, maxR);
    }

    /**
     * 保证某个 plateId 的河流数据已生成。
     */
    private void ensureRiversForPlate(int plateId) {
        if (plateId == 0) return;
        if (mainRiversByPlateId.containsKey(plateId)) {
            return;
        }

        synchronized (this) {
            if (mainRiversByPlateId.containsKey(plateId)) {
                return;
            }
            buildMainRiversForPlate(plateId);
        }
    }

    /**
     * 为给定 plateId 生成主河与支流（当前实现只生成主河骨架）。
     *
     * 新实现要点：
     *   - 基于 PlateInfo 估计的板块中心 + 半径；
     *   - 从中心向外投射射线，找到陆地→非陆地的边界作为 mouth；
     *   - 从 mouth 出发，朝中心 + 噪声步进生长主河折线。
     */
    private void buildMainRiversForPlate(int plateId) {
        java.util.List<RiverPolyline> mainList = new java.util.ArrayList<>();
        java.util.List<RiverPolyline> tribList = new java.util.ArrayList<>();

        PlateInfo plateInfo = getPlateInfo(plateId);
        if (plateInfo == null || plateInfo.approxRadius <= 0.0) {
            mainRiversByPlateId.put(plateId, mainList);
            tributariesByPlateId.put(plateId, tribList);
            return;
        }

        int mainCount = computeMainRiverCount(plateId);

        java.util.List<MouthSample> mouths = selectMouthsForPlate(plateId, plateInfo, mainCount);

        int indexWithinPlate = 0;
        for (MouthSample mouth : mouths) {
            RiverPolyline rp = buildMainRiverForPlate(plateId, indexWithinPlate, plateInfo, mouth);
            indexWithinPlate++;
            if (rp != null) {
                mainList.add(rp);

                RiverSourceDescriptor src = buildSourceForRiver(plateId, rp, plateInfo);
                if (src != null) {
                    sourceByRiverId.put(rp.id, src);
                }
            }
        }

        mainRiversByPlateId.put(plateId, mainList);
        tributariesByPlateId.put(plateId, tribList);
    }

    private RiverSourceDescriptor buildSourceForRiver(int plateId,
                                                      RiverPolyline rp,
                                                      PlateInfo plateInfo) {
        if (rp == null || rp.nodes == null || rp.nodes.size() < 2) return null;

        java.util.List<RiverPolyline.Node> nodes = rp.nodes;

        RiverPolyline.Node mouthNode = nodes.get(0);
        RiverPolyline.Node srcNode   = nodes.get(nodes.size() - 1);

        double centerX = srcNode.x;
        double centerZ = srcNode.z;

        RiverPolyline.Node nearSrc = nodes.get(nodes.size() - 2);
        double outDirX = nearSrc.x - srcNode.x;
        double outDirZ = nearSrc.z - srcNode.z;
        double len = Math.sqrt(outDirX * outDirX + outDirZ * outDirZ);
        if (len <= 1e-6) {
            double toCenterX = plateInfo.centerX - centerX;
            double toCenterZ = plateInfo.centerZ - centerZ;
            double len2 = Math.sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ);
            if (len2 <= 1e-6) {
                outDirX = 0.0;
                outDirZ = 1.0;
            } else {
                outDirX = toCenterX / len2;
                outDirZ = toCenterZ / len2;
            }
        } else {
            outDirX /= len;
            outDirZ /= len;
        }

        int level = rp.level;

        double scale = 1.0 / (level + 1.0);

        double baseRiverValley = 72.0 * scale;

        double lakeRadius = baseRiverValley * 2.5;
        if (lakeRadius < 80.0 * scale) {
            lakeRadius = 80.0 * scale;
        }
        if (lakeRadius > 260.0 * scale) {
            lakeRadius = 260.0 * scale;
        }

        double lakeDepth = 18.0 * scale; // 最大下挖 18 格左右，看起来像一个明显湖盆

        double fissureRadius = lakeRadius * 0.18;
        if (fissureRadius < 6.0 * scale) fissureRadius = 6.0 * scale;
        if (fissureRadius > 20.0 * scale) fissureRadius = 20.0 * scale;

        double fissureDepth = lakeDepth * 2.5;
        if (fissureDepth < 32.0 * scale) fissureDepth = 32.0 * scale;

        return new RiverSourceDescriptor(
            rp.id,
            plateId,
            level,
            centerX,
            centerZ,
            lakeRadius,
            lakeDepth,
            fissureRadius,
            fissureDepth,
            outDirX,
            outDirZ
        );
    }

    private int computeMainRiverCount(int plateId) {
        long key = hash64(worldSeedInt, plateId, 0xCAFEBABEL);
        java.util.Random rand = new java.util.Random(key);
        double r = rand.nextDouble();
        return 1 + (r < 0.37 ? 1 : 0);
    }

    private static final class MouthSample {
        final double x;
        final double z;

        MouthSample(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * 射线找到的“潜在河口命中”结果。
     */
    private static final class MouthHit {
        final double x;
        final double z;
        MouthHit(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * 基于 PlateInfo 选取若干入海口 mouth：
     *   - 从板块中心向外随机选若干射线方向；
     *   - 沿每条射线从中心向外步进，找到“本板陆地 → 非本板陆地或海洋”的交界；
     *   - 在交界处使用 refineCoastCrossing 精细化交点；
     *   - 用 estimateCoastNormal 求海岸法线；
     *   - 只有“射线方向与海岸法线夹角在扇形内”的正面入海点才接受；
     *   - 若是“非正面触碰”的交界，则直接丢弃这一交界点，沿射线继续扫描；
     *   - 同时用 isBigWaterBodyAlongRay 过滤小湖；
     *   - 按 mouth 之间的最小间距和尝试次数选出 targetCount 个 mouth。
     */
    private java.util.List<MouthSample> selectMouthsForPlate(int plateId,
                                                             PlateInfo plateInfo,
                                                             int targetCount) {
        if (plateInfo == null || plateInfo.approxRadius <= 0.0) {
            return java.util.Collections.emptyList();
        }

        java.util.List<MouthSample> result = new java.util.ArrayList<>();
        java.util.Random rand = new java.util.Random(
            hash64(worldSeedInt, plateId, 0xFACEB00CL)
        );

        final double centerX = plateInfo.centerX;
        final double centerZ = plateInfo.centerZ;
        final double radius  = plateInfo.approxRadius;

        final int    MAX_TRIES       = targetCount * 16;
        final double MIN_SPACING     = MIN_MOUTH_SPACING_BLOCKS;
        final double MIN_SPACING_SQ  = MIN_SPACING * MIN_SPACING;

        final double RAY_STEP                = 512.0;
        final double MIN_BIG_WATER_THICKNESS = 1500.0;
        final double WATER_CHECK_MAX         = 4000.0;

        final double MAX_SECTOR_ANGLE_DEG    = 45.0;

        int tries = 0;
        while (result.size() < targetCount && tries < MAX_TRIES) {
            tries++;

            double angle = rand.nextDouble() * Math.PI * 2.0;
            double dirX  = Math.cos(angle);
            double dirZ  = Math.sin(angle);

            double maxDist = radius * 1.5;

            MouthHit hit = findMouthAlongRayWithSector(
                plateId,
                centerX, centerZ,
                dirX, dirZ,
                maxDist,
                RAY_STEP,
                MIN_BIG_WATER_THICKNESS,
                WATER_CHECK_MAX,
                MAX_SECTOR_ANGLE_DEG
            );

            if (hit == null) {
                continue;
            }

            double mouthX = hit.x;
            double mouthZ = hit.z;

            boolean tooClose = false;
            for (MouthSample exist : result) {
                double dx = mouthX - exist.x;
                double dz = mouthZ - exist.z;
                double d2 = dx * dx + dz * dz;
                if (d2 < MIN_SPACING_SQ) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                continue;
            }

            result.add(new MouthSample(mouthX, mouthZ));
        }

        return result;
    }

    /**
     * 沿着 (centerX,centerZ) + t * dirX,dirZ 这一条射线，
     * 查找第一个“满足扇形条件的河口（陆->海交界点）”。
     *
     * 逻辑：
     *   - 从中心开始，每步 step 一直走到 maxDist；
     *   - 发现“本板陆地 -> 非本板陆地/水体”的交界时：
     *       * 用 refineCoastCrossing 精细化交界点；
     *       * 用 isBigWaterBodyAlongRay 检查是不是大水体（不是就继续往前）；
     *       * 用 estimateCoastNormal 求海岸法线；
     *       * 若 n 与射线方向 d 的夹角在给定扇形内，则返回这个 mouth；
     *       * 否则视作“非正面触碰”，丢弃这个交界点，继续沿射线扫描。
     */
    private MouthHit findMouthAlongRayWithSector(int plateId,
                                                 double centerX, double centerZ,
                                                 double dirX,   double dirZ,
                                                 double maxDistBlocks,
                                                 double step,
                                                 double minBigWaterThickness,
                                                 double waterCheckMax,
                                                 double maxSectorAngleDeg) {

        double lenDir = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (lenDir < 1e-6) return null;
        dirX /= lenDir;
        dirZ /= lenDir;

        double t = 0.0;

        double prevX = centerX;
        double prevZ = centerZ;
        boolean prevIsPlateLand = isPlateLand(plateId, prevX, prevZ);

        double cosMax = Math.cos(Math.toRadians(maxSectorAngleDeg));

        while (t <= maxDistBlocks) {
            t += step;
            double curX = centerX + dirX * t;
            double curZ = centerZ + dirZ * t;

            boolean curIsPlateLand = isPlateLand(plateId, curX, curZ);

            if (prevIsPlateLand && !curIsPlateLand) {

                CoastCrossing cross = refineCoastCrossing(plateId, prevX, prevZ, curX, curZ);
                if (cross == null) {
                    prevX = curX;
                    prevZ = curZ;
                    prevIsPlateLand = curIsPlateLand;
                    continue;
                }

                double mouthX = cross.x;
                double mouthZ = cross.z;

                double hitDistFromCenter = Math.sqrt(
                    (mouthX - centerX) * (mouthX - centerX) +
                        (mouthZ - centerZ) * (mouthZ - centerZ)
                );

                if (!isBigWaterBodyAlongRay(centerX, centerZ,
                    dirX, dirZ,
                    hitDistFromCenter,
                    step,
                    minBigWaterThickness,
                    waterCheckMax)) {
                    prevX = curX;
                    prevZ = curZ;
                    prevIsPlateLand = curIsPlateLand;
                    continue;
                }

                CoastNormalInfo coastInfo = estimateCoastNormal(mouthX, mouthZ);
                double nx = coastInfo.nx;
                double nz = coastInfo.nz;

                double dotDN = dirX * nx + dirZ * nz;
                if (dotDN < 0.0) {
                    nx = -nx;
                    nz = -nz;
                    dotDN = -dotDN;
                }

                double dot = dirX * nx + dirZ * nz;

                if (dot >= cosMax) {
                    return new MouthHit(mouthX, mouthZ);
                }

                prevX = curX;
                prevZ = curZ;
                prevIsPlateLand = curIsPlateLand;
                continue;
            }

            prevX = curX;
            prevZ = curZ;
            prevIsPlateLand = curIsPlateLand;
        }

        return null;
    }

    /**
     * 检测从“入水起点”开始，沿同一射线向外走，是否碰到“大水体”。
     *
     * 逻辑：
     *   - 从 startDist 开始，沿 dirX,dirZ 每步 step 一直往外走；
     *   - 若连续水段长度 >= minThickness，则认为是“大水体”（海/大湖）；
     *   - 若在水段长度还没到 minThickness 就重新上岸，则认为只是小湖/窄水体。
     */
    private boolean isBigWaterBodyAlongRay(double centerX, double centerZ,
                                           double dirX, double dirZ,
                                           double startDist,
                                           double step,
                                           double minThickness,
                                           double maxCheckDist) {

        double waterRun = 0.0;
        double endDist  = startDist + maxCheckDist;

        for (double d = startDist; d <= endDist; d += step) {
            double x = centerX + dirX * d;
            double z = centerZ + dirZ * d;

            int ix = (int) Math.round(x);
            int iz = (int) Math.round(z);

            boolean isLand = TalosLandMask.isLand(ix, iz, worldSeedInt);

            if (!isLand) {
                waterRun += step;
                if (waterRun >= minThickness) {
                    return true;
                }
            } else {
                return false;
            }
        }

        return waterRun >= minThickness;
    }

    /**
     * 从 mouth 出发，朝板块中心方向 + 噪声步进生长主河骨架。
     * 成功则返回一条 RiverPolyline（坐标为世界坐标，单位 blocks）。
     *
     * 这里保留原始“撞出板块就终止”的简单逻辑，不再做末端重塑。
     */
    private RiverPolyline buildMainRiverForPlate(int plateId,
                                                 int indexWithinPlate,
                                                 PlateInfo plateInfo,
                                                 MouthSample mouth) {

        if (plateInfo == null || plateInfo.approxRadius <= 0.0 || mouth == null) {
            return null;
        }

        long baseSeed = hash64(worldSeedInt, plateId, indexWithinPlate, 0x9E3779B97F4A7C15L);

        double centerX = plateInfo.centerX;
        double centerZ = plateInfo.centerZ;
        double radius = plateInfo.approxRadius;

        double curX = mouth.x;
        double curZ = mouth.z;

        double dirX = centerX - curX;
        double dirZ = centerZ - curZ;
        double lenDir = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (lenDir <= 0.0) {
            dirX = 0.0;
            dirZ = 1.0;
        } else {
            dirX /= lenDir;
            dirZ /= lenDir;
        }

        java.util.List<RiverPolyline.Node> nodes = new java.util.ArrayList<>();
        nodes.add(new RiverPolyline.Node(curX, curZ));

        double totalLengthBlocks = 0.0;

        final double STEP = 256.0;
        final int MAX_STEPS = GROW_STEP_LIMIT;
        final double MIN_TOTAL = MAIN_RIVER_MIN_LENGTH_BLOCKS;
        final double MAX_TOTAL = MAIN_RIVER_MAX_LENGTH_BLOCKS;

        for (int step = 0; step < MAX_STEPS; step++) {
            if (totalLengthBlocks >= MAX_TOTAL) {
                break;
            }

            double toCenterX = centerX - curX;
            double toCenterZ = centerZ - curZ;
            double distToCenter = Math.sqrt(toCenterX * toCenterX + toCenterZ * toCenterZ);
            if (distToCenter > 0.0) {
                toCenterX /= distToCenter;
                toCenterZ /= distToCenter;
            }

            double inlandT = clamp01(1.0 - distToCenter / (radius + 1.0));
            double maxAngle = Math.toRadians(35.0) * (1.0 - 0.5 * inlandT);

            double noise = hash01(worldSeedInt, plateId, indexWithinPlate, step, 0xDEADBEEFL) * 2.0 - 1.0;
            double angleOffset = noise * maxAngle;

            double cosA = Math.cos(angleOffset);
            double sinA = Math.sin(angleOffset);
            double ndx = toCenterX * cosA - toCenterZ * sinA;
            double ndz = toCenterX * sinA + toCenterZ * cosA;

            double nextX = curX + ndx * STEP;
            double nextZ = curZ + ndz * STEP;

            if (!isPlateLand(plateId, nextX, nextZ)) {

                boolean found = false;
                double tryNextX = nextX;
                double tryNextZ = nextZ;
                double tryNdx   = ndx;
                double tryNdz   = ndz;

                for (int retry = 0; retry < 3; retry++) {
                    angleOffset *= 0.5;
                    cosA = Math.cos(angleOffset);
                    sinA = Math.sin(angleOffset);
                    tryNdx = toCenterX * cosA - toCenterZ * sinA;
                    tryNdz = toCenterX * sinA + toCenterZ * cosA;
                    tryNextX = curX + tryNdx * STEP;
                    tryNextZ = curZ + tryNdz * STEP;
                    if (isPlateLand(plateId, tryNextX, tryNextZ)) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    break;
                }

                nextX = tryNextX;
                nextZ = tryNextZ;
                ndx   = tryNdx;
                ndz   = tryNdz;
            }

            curX = nextX;
            curZ = nextZ;
            nodes.add(new RiverPolyline.Node(curX, curZ));
            totalLengthBlocks += STEP;

            dirX = ndx;
            dirZ = ndz;

            if (totalLengthBlocks >= MIN_TOTAL && distToCenter <= radius * 0.25) {
                break;
            }
        }

        if (nodes.size() < 2 || totalLengthBlocks < MAIN_RIVER_MIN_LENGTH_BLOCKS * 0.5) {
            return null;
        }

        int riverId = computeRiverId(plateId, indexWithinPlate, 0);
        return new RiverPolyline(riverId, plateId, 0, nodes);
    }

    private static final class CoastCrossing {
        final double x;
        final double z;
        CoastCrossing(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }

    /**
     * 给定一段从陆地点 (x0,z0) 到非陆地点 (x1,z1) 的线段，
     * 用二分搜索在中间找到一个精确的“岸线交点”（陆/非陆边界，精度 ~1 block）。
     */
    private CoastCrossing refineCoastCrossing(int plateId,
                                              double x0, double z0,
                                              double x1, double z1) {
        if (!isPlateLand(plateId, x0, z0)) return null;
        if (isPlateLand(plateId, x1, z1))  return null;

        double ax = x0;
        double az = z0;
        double bx = x1;
        double bz = z1;

        for (int iter = 0; iter < 20; iter++) {
            double mx = 0.5 * (ax + bx);
            double mz = 0.5 * (az + bz);
            if (isPlateLand(plateId, mx, mz)) {
                ax = mx;
                az = mz;
            } else {
                bx = mx;
                bz = mz;
            }
        }

        double mx = 0.5 * (ax + bx);
        double mz = 0.5 * (az + bz);

        return new CoastCrossing(mx, mz);
    }

    private static final class CoastNormalInfo {
        final double nx;
        final double nz;
        CoastNormalInfo(double nx, double nz) {
            this.nx = nx;
            this.nz = nz;
        }
    }

    /**
     * 在 (x,z) 附近用小十字采样估计一个“海岸法线”，指向海那边。
     */
    private CoastNormalInfo estimateCoastNormal(double x, double z) {
        int ix = (int) Math.round(x);
        int iz = (int) Math.round(z);

        final int SAMPLE_OFFSET = 64;

        boolean posXLand = TalosLandMask.isLand(ix + SAMPLE_OFFSET, iz, worldSeedInt);
        boolean negXLand = TalosLandMask.isLand(ix - SAMPLE_OFFSET, iz, worldSeedInt);
        boolean posZLand = TalosLandMask.isLand(ix, iz + SAMPLE_OFFSET, worldSeedInt);
        boolean negZLand = TalosLandMask.isLand(ix, iz - SAMPLE_OFFSET, worldSeedInt);

        double wPosX = posXLand ? 0.0 : 1.0;
        double wNegX = negXLand ? 0.0 : 1.0;
        double wPosZ = posZLand ? 0.0 : 1.0;
        double wNegZ = negZLand ? 0.0 : 1.0;

        double nx = wPosX - wNegX;
        double nz = wPosZ - wNegZ;

        double len = Math.sqrt(nx * nx + nz * nz);
        if (len < 1e-3) {
            return new CoastNormalInfo(0.0, 1.0);
        }

        nx /= len;
        nz /= len;

        return new CoastNormalInfo(nx, nz);
    }

    private boolean isPlateLand(int plateId, double wx, double wz) {
        int x = (int) Math.round(wx);
        int z = (int) Math.round(wz);
        int pid = TalosLandMask.getPlateId(x, z, worldSeedInt);
        if (pid != plateId) return false;
        return TalosLandMask.isLand(x, z, worldSeedInt);
    }

    private int computeRiverId(int plateId, int indexWithinPlate, int level) {
        long v = (((long) worldSeedInt) << 32)
            ^ (plateId & 0xFFFFL)
            ^ (((long) indexWithinPlate) << 16)
            ^ (((long) level) << 8);
        return (int) (v ^ (v >>> 32));
    }

    private static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    /**
     * smoothstep(0,1,x) = 3x^2 - 2x^3
     */
    private static double smoothstep01(double x) {
        x = clamp01(x);
        return x * x * (3.0 - 2.0 * x);
    }

    private static long hash64(long... keys) {
        long h = 0x9E3779B97F4A7C15L;
        for (long k : keys) {
            h ^= k + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
        }
        // final avalanching
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdl;
        h ^= (h >>> 33);
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= (h >>> 33);
        return h;
    }

    private static double hash01(long... keys) {
        long h = hash64(keys);
        // 转成 [0,1)
        return (h >>> 11) * (1.0 / (1L << 53));
    }
}
