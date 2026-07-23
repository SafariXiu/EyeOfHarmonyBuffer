package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

import java.util.Collections;
import java.util.List;

public final class RiverEdgeData {

    private final int id;
    private final int parentId;
    private final int parentSegment;

    private final RiverType type;
    private final RiverRelation relation;

    private final boolean hasSource;
    private final boolean hasMouth;

    private final float visualWidthScale;

    private final float widthStart;
    private final float widthEnd;

    private final float influenceRadius;

    private final float parentT;

    private final List<RiverPoint> points;

    public RiverEdgeData(
        int id,
        int parentId,
        int parentSegment,
        RiverType type,
        RiverRelation relation,
        boolean hasSource,
        boolean hasMouth,
        float visualWidthScale,
        float widthStart,
        float widthEnd,
        float influenceRadius,
        float parentT,
        List<RiverPoint> points
    ) {
        this.id = id;
        this.parentId = parentId;
        this.parentSegment = parentSegment;
        this.type = type;
        this.relation = relation;
        this.hasSource = hasSource;
        this.hasMouth = hasMouth;
        this.visualWidthScale = visualWidthScale;
        this.widthStart = widthStart;
        this.widthEnd = widthEnd;
        this.influenceRadius = influenceRadius;
        this.parentT = parentT;
        this.points = Collections.unmodifiableList(points);
    }

    public int getId() { return id; }
    public int getParentId() { return parentId; }
    public int getParentSegment() { return parentSegment; }
    public RiverType getType() { return type; }
    public RiverRelation getRelation() { return relation; }
    public boolean hasSource() { return hasSource; }
    public boolean hasMouth() { return hasMouth; }
    public float getVisualWidthScale() { return visualWidthScale; }
    public float getWidthStart() { return widthStart; }
    public float getWidthEnd() { return widthEnd; }
    public float getInfluenceRadius() { return influenceRadius; }
    public float getParentT() { return parentT; }
    public List<RiverPoint> getPoints() { return points; }
}
