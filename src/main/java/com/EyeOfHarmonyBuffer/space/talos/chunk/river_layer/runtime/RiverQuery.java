package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;
import org.jetbrains.annotations.Nullable;

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
                                 double mouthZ) {
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
                Double.NaN
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

    public static RiverQueryResult query(
        RiverSpatialIndex index,
        double worldX, double worldZ
    ) {
        double sampleX = worldX + 0.5;
        double sampleZ = worldZ + 0.5;

        var candidates = index.queryCell(sampleX, sampleZ);
        if (candidates.isEmpty()) {
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

        if (dominantSeg == null) {
            return RiverQueryResult.none();
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
            bestTerrainInfluence,
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
            dominantSeg.mouthZ
        );
    }
}
