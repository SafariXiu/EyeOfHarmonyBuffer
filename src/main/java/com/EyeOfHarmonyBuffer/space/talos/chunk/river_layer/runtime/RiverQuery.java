package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public final class RiverQuery {

    public static final class RiverQueryResult {
        public final boolean affected;
        public final double distanceToCenter;
        public final double riverWidth; // 完整宽度
        public final double channelRadius; // 半径
        public final double channelInfluence;
        public final double terrainInfluence;
        /** 沿河纵向深度倍率（0..1，来自 RiverNetworkProfile）。 */
        public final double depthScale;
        public final double riverProgress; // 0..1
        public final RiverType riverType;
        public final int edgeId;
        public final boolean hasSource;
        public final boolean hasMouth;
        public final double closestX;
        public final double closestZ;
        public final double sourceX;
        public final double sourceZ;
        public final double mouthX;
        public final double mouthZ;

        /** 命中的水体（独立湖 / 湿地 / 穿河湖 / 牛轭湖），无则 null。 */
        public final RiverBodyData body;
        /** 到水体轮廓的距离：内部为 0，外部为到椭圆轮廓的近似距离。 */
        public final double bodyDistance;
        /** 水体影响 0..1：内部 1，向外随距离衰减。 */
        public final double bodyMask;

        private RiverQueryResult(boolean affected,
                                 double distanceToCenter,
                                 double riverWidth,
                                 double channelRadius,
                                 double channelInfluence,
                                 double terrainInfluence,
                                 double depthScale,
                                 double riverProgress,
                                 RiverType riverType,
                                 int edgeId,
                                 boolean hasSource,
                                 boolean hasMouth,
                                 double closestX,
                                 double closestZ,
                                 double sourceX,
                                 double sourceZ,
                                 double mouthX,
                                 double mouthZ,
                                 RiverBodyData body,
                                 double bodyDistance,
                                 double bodyMask) {
            this.affected = affected;
            this.distanceToCenter = distanceToCenter;
            this.riverWidth = riverWidth;
            this.channelRadius = channelRadius;
            this.channelInfluence = channelInfluence;
            this.terrainInfluence = terrainInfluence;
            this.depthScale = depthScale;
            this.riverProgress = riverProgress;
            this.riverType = riverType;
            this.edgeId = edgeId;
            this.hasSource = hasSource;
            this.hasMouth = hasMouth;
            this.closestX = closestX;
            this.closestZ = closestZ;
            this.sourceX = sourceX;
            this.sourceZ = sourceZ;
            this.mouthX = mouthX;
            this.mouthZ = mouthZ;
            this.body = body;
            this.bodyDistance = bodyDistance;
            this.bodyMask = bodyMask;
        }

        public static RiverQueryResult none() {
            return new RiverQueryResult(
                false,
                Double.POSITIVE_INFINITY,
                0.0, 0.0,
                0.0, 0.0,
                1.0,
                0.0,
                null,
                -1,
                false,
                false,
                0.0,
                0.0,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                null,
                Double.POSITIVE_INFINITY,
                0.0
            );
        }
    }

    public static final class SegmentProjection {
        public final double distanceSquared;
        public final double segmentT;
        public final double closestX;
        public final double closestZ;

        public SegmentProjection(double distanceSquared,
                                 double segmentT,
                                 double closestX,
                                 double closestZ) {
            this.distanceSquared = distanceSquared;
            this.segmentT = segmentT;
            this.closestX = closestX;
            this.closestZ = closestZ;
        }
    }

    private static final RiverBodyIndex EMPTY_BODY_INDEX =
        RiverBodyIndex.build(Collections.<RiverBodyData>emptyList(), RiverBodyIndex.CELL_SIZE);

    private RiverQuery() {}

    public static SegmentProjection projectToSegment(
        double px, double pz,
        double ax, double az,
        double bx, double bz
    ) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;

        double lenSq = abx * abx + abz * abz;
        if (lenSq <= 1.0e-12) {
            double dx = px - ax;
            double dz = pz - az;
            return new SegmentProjection(dx * dx + dz * dz, 0.0, ax, az);
        }

        double t = (apx * abx + apz * abz) / lenSq;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;

        double cx = ax + abx * t;
        double cz = az + abz * t;

        double dx = px - cx;
        double dz = pz - cz;

        return new SegmentProjection(dx * dx + dz * dz, t, cx, cz);
    }

    private static double smoothstep(double v) {
        double t = Math.max(0.0, Math.min(1.0, v));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double typeWeight(@Nullable RiverType type) {
        if (type == null) {
            return 1.0;
        }
        switch (type) {
            case MAIN:
                return 2.0;
            case BRANCH1:
                return 1.2;
            case BRANCH2:
            default:
                return 1.0;
        }
    }

    /** 兼容入口：不含水体索引（只查河道）。 */
    public static RiverQueryResult query(
        RiverSpatialIndex index,
        double worldX, double worldZ
    ) {
        return query(index, EMPTY_BODY_INDEX, worldX, worldZ);
    }

    public static RiverQueryResult query(
        RiverSpatialIndex index,
        RiverBodyIndex bodyIndex,
        double worldX, double worldZ
    ) {
        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;

        var candidates = index.queryCell(sampleX, sampleZ);
        if (candidates.isEmpty() && bodyIndex == null) {
            return RiverQueryResult.none();
        }

        RiverSegment dominantSeg = null;
        double dominantT = 0.0;
        double dominantDistSq = Double.POSITIVE_INFINITY;
        double dominantClosestX = 0.0;
        double dominantClosestZ = 0.0;

        double dominantScore = -1.0;

        double bestChannelInfluence = 0.0;
        double bestTerrainInfluence = 0.0;

        for (RiverSegment s : candidates) {
            SegmentProjection p = projectToSegment(
                sampleX, sampleZ,
                s.ax, s.az,
                s.bx, s.bz
            );

            double distSq = p.distanceSquared;
            double dist   = Math.sqrt(distSq);

            double riverProgress =
                s.progressStart + (s.progressEnd - s.progressStart) * p.segmentT;

            double widthT = smoothstep(riverProgress);
            double riverWidth =
                s.edgeWidthStart + (s.edgeWidthEnd - s.edgeWidthStart) * widthT;
            double channelRadius = riverWidth * 0.5;

            if (channelRadius <= 0.0 || s.influenceRadius <= 0.0) {
                continue;
            }

            double channelValue =
                Math.max(0.0, 1.0 - dist / channelRadius);
            channelValue = smoothstep(channelValue);

            double terrainValue =
                Math.max(0.0, 1.0 - dist / s.influenceRadius);
            terrainValue = smoothstep(terrainValue);

            if (channelValue > bestChannelInfluence) {
                bestChannelInfluence = channelValue;
            }
            if (terrainValue > bestTerrainInfluence) {
                bestTerrainInfluence = terrainValue;
            }

            if (channelValue <= 0.0 && terrainValue <= 0.0) {
                continue;
            }

            double score = channelValue * typeWeight(s.type);

            if (score > dominantScore ||
                (score == dominantScore && distSq < dominantDistSq)) {

                dominantScore   = score;
                dominantSeg     = s;
                dominantT       = p.segmentT;
                dominantDistSq  = distSq;
                dominantClosestX = p.closestX;
                dominantClosestZ = p.closestZ;
            }
        }

        RiverBodyData bestBody = null;
        double bestBodyDist = Double.POSITIVE_INFINITY;
        double bestBodyMask = 0.0;

        if (bodyIndex != null) {
            List<RiverBodyData> bodyCandidates = bodyIndex.queryCell(sampleX, sampleZ);
            for (RiverBodyData b : bodyCandidates) {
                BodyHit hit = bodyHit(b, sampleX, sampleZ);
                if (hit.mask > bestBodyMask
                    || (hit.mask == bestBodyMask && hit.distance < bestBodyDist)) {
                    bestBody = b;
                    bestBodyDist = hit.distance;
                    bestBodyMask = hit.mask;
                }
            }
        }

        boolean hasBody = bestBody != null;

        if (dominantSeg == null && !hasBody) {
            return RiverQueryResult.none();
        }

        // 水体不参与河岸压平：河岸塑形只跟河道走，
        // 湖/湿地自己的岸滩和外坡由水体雕刻接管（避免把湖周围压出硬切）。
        double terrainInfluence = bestTerrainInfluence;

        if (dominantSeg == null) {
            // 只有水体、没有河道：返回水体专属结果
            return new RiverQueryResult(
                true,
                bestBodyDist,
                0.0,
                0.0,
                0.0,
                terrainInfluence,
                1.0,
                0.0,
                null,
                -1,
                false,
                false,
                bestBody.getCenterX(),
                bestBody.getCenterZ(),
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                bestBody,
                bestBodyDist,
                bestBodyMask
            );
        }

        double bestDist = Math.sqrt(dominantDistSq);

        double bestProgress =
            dominantSeg.progressStart
                + (dominantSeg.progressEnd - dominantSeg.progressStart) * dominantT;

        double widthT = smoothstep(bestProgress);
        double bestWidth =
            dominantSeg.edgeWidthStart
                + (dominantSeg.edgeWidthEnd - dominantSeg.edgeWidthStart) * widthT;
        double bestRadius = bestWidth * 0.5;

        double bestDepthScale =
            dominantSeg.depthScaleStart
                + (dominantSeg.depthScaleEnd - dominantSeg.depthScaleStart) * dominantT;

        return new RiverQueryResult(
            true,
            bestDist,
            bestWidth,
            bestRadius,
            bestChannelInfluence,
            terrainInfluence,
            bestDepthScale,
            bestProgress,
            dominantSeg.type,
            dominantSeg.edgeId,
            dominantSeg.hasSource,
            dominantSeg.hasMouth,
            dominantClosestX,
            dominantClosestZ,
            dominantSeg.sourceX,
            dominantSeg.sourceZ,
            dominantSeg.mouthX,
            dominantSeg.mouthZ,
            bestBody,
            bestBodyDist,
            bestBodyMask
        );
    }

    private static final class BodyHit {
        final double distance;
        final double mask;

        BodyHit(double distance, double mask) {
            this.distance = distance;
            this.mask = mask;
        }
    }

    private static BodyHit bodyHit(RiverBodyData body, double sampleX, double sampleZ) {
        if (pointInPolygon(sampleX, sampleZ, body.getOutline())) {
            return new BodyHit(0.0, 1.0);
        }

        // 用「到真实轮廓的最短距离」做衰减，而不是椭圆近似：
        // 不规则轮廓的岸滩列如果按椭圆算，掩码可能在部分方向提前归零，
        // 水体直接不被命中，沙圈/砂砾圈就会断成一段一段。
        double dist = distanceToPolygonOutline(
            sampleX, sampleZ, body.getOutline()
        );
        double mask = 1.0 - dist / RiverBodyIndex.SHORE_MARGIN;
        if (mask < 0.0) mask = 0.0;
        if (mask > 1.0) mask = 1.0;
        return new BodyHit(dist, mask);
    }

    private static double distanceToPolygonOutline(
        double px, double pz, List<RiverPoint> polygon
    ) {
        int n = polygon.size();
        double best = Double.POSITIVE_INFINITY;

        for (int i = 0, j = n - 1; i < n; j = i++) {
            RiverPoint a = polygon.get(j);
            RiverPoint b = polygon.get(i);
            double abx = b.getX() - a.getX();
            double abz = b.getZ() - a.getZ();
            double lenSq = abx * abx + abz * abz;

            double t;
            if (lenSq <= 1.0e-12) {
                t = 0.0;
            } else {
                t = ((px - a.getX()) * abx + (pz - a.getZ()) * abz) / lenSq;
                if (t < 0.0) t = 0.0;
                else if (t > 1.0) t = 1.0;
            }

            double cx = a.getX() + abx * t;
            double cz = a.getZ() + abz * t;
            double dx = px - cx;
            double dz = pz - cz;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
            }
        }

        return Math.sqrt(best);
    }

    public static boolean pointInPolygon(double px, double pz, List<RiverPoint> polygon) {
        boolean inside = false;
        int n = polygon.size();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getX();
            double yi = polygon.get(i).getZ();
            double xj = polygon.get(j).getX();
            double yj = polygon.get(j).getZ();

            if ((yi > pz) != (yj > pz)) {
                double xIntersect = (xj - xi) * (pz - yi) / (yj - yi) + xi;
                if (px < xIntersect) {
                    inside = !inside;
                }
            }
        }

        return inside;
    }
}
