package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

import java.util.Collections;
import java.util.List;

public final class RiverNetwork {

    private final int version;
    private final int coordinateScale;

    private final double minX;
    private final double minZ;
    private final double maxX;
    private final double maxZ;

    private final long seed;

    private final List<RiverEdgeData> edges;
    private final List<RiverBodyData> bodies;

    public RiverNetwork(
        int version,
        int coordinateScale,
        double minX,
        double minZ,
        double maxX,
        double maxZ,
        long seed,
        List<RiverEdgeData> edges
    ) {
        this(
            version,
            coordinateScale,
            minX,
            minZ,
            maxX,
            maxZ,
            seed,
            edges,
            Collections.<RiverBodyData>emptyList()
        );
    }

    public RiverNetwork(
        int version,
        int coordinateScale,
        double minX,
        double minZ,
        double maxX,
        double maxZ,
        long seed,
        List<RiverEdgeData> edges,
        List<RiverBodyData> bodies
    ) {
        this.version = version;
        this.coordinateScale = coordinateScale;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.seed = seed;
        this.edges = Collections.unmodifiableList(edges);
        this.bodies = Collections.unmodifiableList(bodies);
    }

    public int getVersion() { return version; }
    public int getCoordinateScale() { return coordinateScale; }
    public double getMinX() { return minX; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxZ() { return maxZ; }
    public long getSeed() { return seed; }
    public List<RiverEdgeData> getEdges() { return edges; }
    public List<RiverBodyData> getBodies() { return bodies; }
}
