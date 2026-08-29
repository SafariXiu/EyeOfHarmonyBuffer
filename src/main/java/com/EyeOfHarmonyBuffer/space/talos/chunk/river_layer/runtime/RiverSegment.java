package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;

public final class RiverSegment {

    public final int edgeId;
    public final int segmentIndex;
    public final RiverType type;

    public final double ax, az;
    public final double bx, bz;

    public final double progressStart;
    public final double progressEnd;

    public final double edgeWidthStart;
    public final double edgeWidthEnd;

    public final double influenceRadius;

    /** 该线段起点的纵向深度倍率（来自 RiverNetworkProfile）。 */
    public final double depthScaleStart;
    /** 该线段终点的纵向深度倍率。 */
    public final double depthScaleEnd;

    public final boolean hasSource;
    public final boolean hasMouth;

    public final double sourceX;
    public final double sourceZ;
    public final double mouthX;
    public final double mouthZ;

    public RiverSegment(int edgeId,
                        int segmentIndex,
                        RiverType type,
                        double ax, double az,
                        double bx, double bz,
                        double progressStart,
                        double progressEnd,
                        double edgeWidthStart,
                        double edgeWidthEnd,
                        double influenceRadius,
                        double depthScaleStart,
                        double depthScaleEnd,
                        boolean hasSource,
                        boolean hasMouth,
                        double sourceX,
                        double sourceZ,
                        double mouthX,
                        double mouthZ) {
        this.edgeId = edgeId;
        this.segmentIndex = segmentIndex;
        this.type = type;
        this.ax = ax;
        this.az = az;
        this.bx = bx;
        this.bz = bz;
        this.progressStart = progressStart;
        this.progressEnd = progressEnd;
        this.edgeWidthStart = edgeWidthStart;
        this.edgeWidthEnd = edgeWidthEnd;
        this.influenceRadius = influenceRadius;
        this.depthScaleStart = depthScaleStart;
        this.depthScaleEnd = depthScaleEnd;
        this.hasSource = hasSource;
        this.hasMouth = hasMouth;
        this.sourceX = sourceX;
        this.sourceZ = sourceZ;
        this.mouthX = mouthX;
        this.mouthZ = mouthZ;
    }
}
