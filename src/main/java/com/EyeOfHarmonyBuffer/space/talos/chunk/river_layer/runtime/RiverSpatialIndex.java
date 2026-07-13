package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import java.util.*;

public final class RiverSpatialIndex {

    public static final int CELL_SIZE = 64;

    private final Map<Long, List<RiverSegment>> segmentsByCell;
    private final int cellSize;

    private RiverSpatialIndex(Map<Long, List<RiverSegment>> segmentsByCell,
                              int cellSize) {
        this.segmentsByCell = segmentsByCell;
        this.cellSize = cellSize;
    }

    public static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    public static RiverSpatialIndex build(List<RiverSegment> segments, int cellSize) {
        Map<Long, List<RiverSegment>> index = new HashMap<>();

        for (RiverSegment s : segments) {
            double expansion = Math.max(
                s.influenceRadius,
                Math.max(s.edgeWidthStart, s.edgeWidthEnd) * 0.5
            );

            double minX = Math.min(s.ax, s.bx) - expansion;
            double maxX = Math.max(s.ax, s.bx) + expansion;
            double minZ = Math.min(s.az, s.bz) - expansion;
            double maxZ = Math.max(s.az, s.bz) + expansion;

            int minCellX = (int) Math.floor(minX / cellSize);
            int maxCellX = (int) Math.floor(maxX / cellSize);
            int minCellZ = (int) Math.floor(minZ / cellSize);
            int maxCellZ = (int) Math.floor(maxZ / cellSize);

            for (int cx = minCellX; cx <= maxCellX; cx++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    long key = cellKey(cx, cz);
                    index.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
                }
            }
        }

        Map<Long, List<RiverSegment>> frozen = new HashMap<>();
        for (Map.Entry<Long, List<RiverSegment>> e : index.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }

        return new RiverSpatialIndex(Collections.unmodifiableMap(frozen), cellSize);
    }

    public List<RiverSegment> queryCell(double worldX, double worldZ) {
        int cellX = (int) Math.floor(worldX / cellSize);
        int cellZ = (int) Math.floor(worldZ / cellSize);
        long key = cellKey(cellX, cellZ);
        List<RiverSegment> list = segmentsByCell.get(key);
        return list != null ? list : Collections.emptyList();
    }
}
