package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

import java.util.Collections;
import java.util.List;

public final class RiverBodyData {

    private final int id;
    private final RiverBodyType type;

    /** 挂载的河（through/oxbow）；独立水体为 -1。 */
    private final int parentEdgeId;
    private final float tStart;
    private final float tEnd;

    private final double centerX;
    private final double centerZ;
    private final double radiusX;
    private final double radiusZ;

    private final float rotation;
    private final float maxDepthBlocks;
    private final float waterLevelOffset;

    private final List<RiverPoint> outline;

    public RiverBodyData(
        int id,
        RiverBodyType type,
        int parentEdgeId,
        float tStart,
        float tEnd,
        double centerX,
        double centerZ,
        double radiusX,
        double radiusZ,
        float rotation,
        float maxDepthBlocks,
        float waterLevelOffset,
        List<RiverPoint> outline
    ) {
        this.id = id;
        this.type = type;
        this.parentEdgeId = parentEdgeId;
        this.tStart = tStart;
        this.tEnd = tEnd;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radiusX = radiusX;
        this.radiusZ = radiusZ;
        this.rotation = rotation;
        this.maxDepthBlocks = maxDepthBlocks;
        this.waterLevelOffset = waterLevelOffset;
        this.outline = Collections.unmodifiableList(outline);
    }

    public int getId() { return id; }
    public RiverBodyType getType() { return type; }
    public int getParentEdgeId() { return parentEdgeId; }
    public float getTStart() { return tStart; }
    public float getTEnd() { return tEnd; }
    public double getCenterX() { return centerX; }
    public double getCenterZ() { return centerZ; }
    public double getRadiusX() { return radiusX; }
    public double getRadiusZ() { return radiusZ; }
    public float getRotation() { return rotation; }
    public float getMaxDepthBlocks() { return maxDepthBlocks; }
    public float getWaterLevelOffset() { return waterLevelOffset; }
    public List<RiverPoint> getOutline() { return outline; }
}
