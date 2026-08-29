package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;

import java.util.ArrayList;
import java.util.List;

public final class RiverTemplateFactory {

    private RiverTemplateFactory() {}

    public static RiverTemplate fromNetwork(RiverNetwork network) {
        List<TemplateEdge> tplEdges = new ArrayList<>();

        for (RiverEdgeData e : network.getEdges()) {
            List<TemplatePoint> ptsUV = new ArrayList<>(e.getPoints().size());
            for (RiverPoint p : e.getPoints()) {
                double u = p.getX() / RiverTemplate.WORLD_SIZE;
                double v = p.getZ() / RiverTemplate.WORLD_SIZE;
                ptsUV.add(new TemplatePoint(u, v));
            }

            tplEdges.add(new TemplateEdge(
                e.getId(),
                e.getParentId(),
                e.getParentSegment(),
                e.getType(),
                e.getRelation(),
                e.hasSource(),
                e.hasMouth(),
                e.getVisualWidthScale(),
                e.getWidthStart(),
                e.getWidthEnd(),
                e.getInfluenceRadius(),
                e.getParentT(),
                ptsUV
            ));
        }

        List<TemplateBody> tplBodies = new ArrayList<>();

        for (RiverBodyData b : network.getBodies()) {
            List<TemplatePoint> outlineUV = new ArrayList<>(b.getOutline().size());
            for (RiverPoint p : b.getOutline()) {
                outlineUV.add(new TemplatePoint(
                    p.getX() / RiverTemplate.WORLD_SIZE,
                    p.getZ() / RiverTemplate.WORLD_SIZE
                ));
            }

            tplBodies.add(new TemplateBody(
                b.getId(),
                b.getType(),
                b.getParentEdgeId(),
                b.getTStart(),
                b.getTEnd(),
                b.getCenterX() / RiverTemplate.WORLD_SIZE,
                b.getCenterZ() / RiverTemplate.WORLD_SIZE,
                b.getRadiusX() / RiverTemplate.WORLD_SIZE,
                b.getRadiusZ() / RiverTemplate.WORLD_SIZE,
                b.getRotation(),
                b.getMaxDepthBlocks(),
                b.getWaterLevelOffset(),
                outlineUV
            ));
        }

        return new RiverTemplate(network.getSeed(), tplEdges, tplBodies);
    }
}
