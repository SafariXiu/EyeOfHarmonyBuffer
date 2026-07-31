package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;

import java.util.ArrayList;
import java.util.List;

public final class RiverSystem {

    public final RiverNetwork network;
    public final List<RiverSegment> segments;
    public final RiverSpatialIndex index;

    public RiverSystem(RiverNetwork network,
                       List<RiverSegment> segments,
                       RiverSpatialIndex index) {
        this.network = network;
        this.segments = java.util.Collections.unmodifiableList(new ArrayList<>(segments));
        this.index = index;
    }

    public static RiverSystem buildFromNetwork(RiverNetwork network) {
        return buildFromNetwork(
            network,
            RiverNetworkProfile.build(network)
        );
    }

    private static RiverSystem buildFromNetwork(RiverNetwork network,
                                                RiverNetworkProfile profile) {
        List<RiverSegment> segs = new ArrayList<>();

        for (RiverEdgeData edge : network.getEdges()) {
            List<RiverPoint> pts = edge.getPoints();
            int n = pts.size();
            if (n < 2) continue;

            double[] cumulative = new double[n];
            cumulative[0] = 0.0;
            for (int i = 1; i < n; i++) {
                RiverPoint a = pts.get(i - 1);
                RiverPoint b = pts.get(i);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                cumulative[i] = cumulative[i - 1] + Math.hypot(dx, dz);
            }
            double totalLength = cumulative[n - 1];
            if (totalLength <= 1.0e-6) {
                continue;
            }

            boolean hasSource = edge.hasSource();
            boolean hasMouth  = edge.hasMouth();

            RiverPoint src   = pts.get(0);
            double sourceX   = src.getX();
            double sourceZ   = src.getZ();

            RiverPoint mouth = pts.get(n - 1);
            double mouthX    = mouth.getX();
            double mouthZ    = mouth.getZ();

            for (int i = 0; i < n - 1; i++) {
                RiverPoint a = pts.get(i);
                RiverPoint b = pts.get(i + 1);

                double progressStart = cumulative[i]     / totalLength;
                double progressEnd   = cumulative[i + 1] / totalLength;

                double depthScaleStart = 1.0;
                double depthScaleEnd   = 1.0;
                double widthStart      = edge.getWidthStart();
                double widthEnd        = edge.getWidthEnd();
                double influenceRadius = edge.getInfluenceRadius();
                if (profile != null) {
                    depthScaleStart = profile.scaleAt(edge.getId(), progressStart);
                    depthScaleEnd   = profile.scaleAt(edge.getId(), progressEnd);
                    widthStart      = profile.widthStartAt(edge.getId());
                    widthEnd        = profile.widthEndAt(edge.getId());
                    influenceRadius = profile.influenceAt(
                        edge.getId(), progressStart, progressEnd
                    );
                }

                RiverSegment seg = new RiverSegment(
                    edge.getId(),
                    i,
                    edge.getType(),
                    a.getX(), a.getZ(),
                    b.getX(), b.getZ(),
                    progressStart,
                    progressEnd,
                    widthStart,
                    widthEnd,
                    influenceRadius,
                    depthScaleStart,
                    depthScaleEnd,
                    hasSource,
                    hasMouth,
                    sourceX,
                    sourceZ,
                    mouthX,
                    mouthZ
                );
                segs.add(seg);
            }
        }

        RiverSpatialIndex idx = RiverSpatialIndex.build(segs, RiverSpatialIndex.CELL_SIZE);
        return new RiverSystem(network, segs, idx);
    }
}
