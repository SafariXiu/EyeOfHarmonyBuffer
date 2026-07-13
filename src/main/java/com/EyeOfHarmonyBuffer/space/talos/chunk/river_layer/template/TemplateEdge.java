package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverRelation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverType;

import java.util.List;

public final class TemplateEdge {
    public final int id;
    public final int parentId;
    public final int parentSegment;
    public final RiverType type;
    public final RiverRelation relation;
    public final boolean hasSource;
    public final boolean hasMouth;
    public final float visualWidthScale;
    public final float widthStart;
    public final float widthEnd;
    public final float influenceRadius;
    public final float parentT;
    public final List<TemplatePoint> pointsUV;

    public TemplateEdge(int id,
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
                        List<TemplatePoint> pointsUV) {
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
        this.pointsUV = java.util.Collections.unmodifiableList(pointsUV);
    }
}
