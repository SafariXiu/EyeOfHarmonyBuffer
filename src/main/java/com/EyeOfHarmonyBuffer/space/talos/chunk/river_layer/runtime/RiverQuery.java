package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;

public final class RiverQuery {

    public static final class RiverQueryResult {
        public final boolean affected;
        public final double distanceToCenter;
        public final double riverWidth; // 完整宽度
        public final double channelRadius; // 半径
        public final double channelInfluence;
        public final double terrainInfluence;
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

        double bestDistSq = Double.POSITIVE_INFINITY;
        RiverSegment bestSeg = null;
        double bestSegmentT = 0.0;

        double bestChannelInfluence = 0.0;
        double bestTerrainInfluence = 0.0;

        double bestClosestX = 0.0;
        double bestClosestZ = 0.0;

        for (RiverSegment s : candidates) {
            SegmentProjection p = projectToSegment(
                sampleX, sampleZ,
                s.ax, s.az,
                s.bx, s.bz
            );

            double dist = Math.sqrt(p.distanceSquared);

            double riverProgress =
                s.progressStart + (s.progressEnd - s.progressStart) * p.segmentT;

            double widthT = smoothstep(riverProgress);
            double riverWidth =
                s.edgeWidthStart + (s.edgeWidthEnd - s.edgeWidthStart) * widthT;
            double channelRadius = riverWidth * 0.5;

            double channelValue =
                Math.max(0.0, 1.0 - dist / channelRadius);
            channelValue = smoothstep(channelValue);

            double terrainValue =
                Math.max(0.0, 1.0 - dist / s.influenceRadius);
            terrainValue = smoothstep(terrainValue);

            if (terrainValue > bestTerrainInfluence) {
                bestTerrainInfluence = terrainValue;
            }
            if (channelValue > bestChannelInfluence) {
                bestChannelInfluence = channelValue;
            }

            if (p.distanceSquared < bestDistSq) {
                bestDistSq = p.distanceSquared;
                bestSeg = s;
                bestSegmentT = p.segmentT;
                bestClosestX = p.closestX;
                bestClosestZ = p.closestZ;
            }
        }

        if (bestSeg == null) {
            return RiverQueryResult.none();
        }

        double bestDist = Math.sqrt(bestDistSq);
        double bestProgress =
            bestSeg.progressStart + (bestSeg.progressEnd - bestSeg.progressStart) * bestSegmentT;
        double widthT = smoothstep(bestProgress);
        double bestWidth =
            bestSeg.edgeWidthStart + (bestSeg.edgeWidthEnd - bestSeg.edgeWidthStart) * widthT;
        double bestRadius = bestWidth * 0.5;

        return new RiverQueryResult(
            true,
            bestDist,
            bestWidth,
            bestRadius,
            bestChannelInfluence,
            bestTerrainInfluence,
            bestProgress,
            bestSeg.type,
            bestSeg.edgeId,
            bestSeg.hasSource,
            bestSeg.hasMouth,
            bestClosestX,
            bestClosestZ,
            bestSeg.sourceX,
            bestSeg.sourceZ,
            bestSeg.mouthX,
            bestSeg.mouthZ
        );
    }
}
