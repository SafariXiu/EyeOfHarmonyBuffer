package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyType;

import java.util.Collections;
import java.util.List;

/**
 * 模板空间（u,v ∈ [0,1]，对应模板世界 100000×100000）中的水体。
 * 与 RiverBodyData 一一对应，只是坐标换成了 UV。
 */
public final class TemplateBody {

    public final int id;
    public final RiverBodyType type;

    /** 挂载的河（through/oxbow）；独立水体为 -1。 */
    public final int parentEdgeId;
    public final float tStart;
    public final float tEnd;

    public final double centerU;
    public final double centerV;
    public final double radiusU;
    public final double radiusV;

    public final float rotation;
    /** 水体最大深度（blocks，不随缩放变化）。 */
    public final float maxDepthBlocks;
    /** 水面相对海平面的偏移（blocks，不随缩放变化）。 */
    public final float waterLevelOffset;

    public final List<TemplatePoint> outlineUV;

    public TemplateBody(int id,
                        RiverBodyType type,
                        int parentEdgeId,
                        float tStart,
                        float tEnd,
                        double centerU,
                        double centerV,
                        double radiusU,
                        double radiusV,
                        float rotation,
                        float maxDepthBlocks,
                        float waterLevelOffset,
                        List<TemplatePoint> outlineUV) {
        this.id = id;
        this.type = type;
        this.parentEdgeId = parentEdgeId;
        this.tStart = tStart;
        this.tEnd = tEnd;
        this.centerU = centerU;
        this.centerV = centerV;
        this.radiusU = radiusU;
        this.radiusV = radiusV;
        this.rotation = rotation;
        this.maxDepthBlocks = maxDepthBlocks;
        this.waterLevelOffset = waterLevelOffset;
        this.outlineUV = Collections.unmodifiableList(outlineUV);
    }
}
