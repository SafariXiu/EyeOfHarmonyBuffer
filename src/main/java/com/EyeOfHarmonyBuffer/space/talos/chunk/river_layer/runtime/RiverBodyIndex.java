package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 水体（湖 / 湿地 / 穿河湖 / 牛轭湖）的空间索引。
 *
 * 每个水体按「轮廓 + 中心」的包围盒（再外扩 SHORE_MARGIN 格，
 * 覆盖岸滩和外坡的雕刻范围）插入经过的格子；
 * 查询时返回候选水体，由 RiverQuery 做精确的内外判断。
 */
public final class RiverBodyIndex {

    public static final int CELL_SIZE = 256;

    /** 包围盒外扩量：覆盖岸滩 + 外坡雕刻范围；查询衰减也用它。 */
    public static final double SHORE_MARGIN = 256.0;

    private final Map<Long, List<RiverBodyData>> bodiesByCell;
    private final int cellSize;

    private RiverBodyIndex(Map<Long, List<RiverBodyData>> bodiesByCell,
                           int cellSize) {
        this.bodiesByCell = bodiesByCell;
        this.cellSize = cellSize;
    }

    public static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    public static RiverBodyIndex build(List<RiverBodyData> bodies, int cellSize) {
        Map<Long, List<RiverBodyData>> index = new HashMap<>();

        for (RiverBodyData b : bodies) {
            double minX = Double.POSITIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxZ = Double.NEGATIVE_INFINITY;

            for (RiverPoint p : b.getOutline()) {
                if (p.getX() < minX) minX = p.getX();
                if (p.getX() > maxX) maxX = p.getX();
                if (p.getZ() < minZ) minZ = p.getZ();
                if (p.getZ() > maxZ) maxZ = p.getZ();
            }
            if (b.getCenterX() < minX) minX = b.getCenterX();
            if (b.getCenterX() > maxX) maxX = b.getCenterX();
            if (b.getCenterZ() < minZ) minZ = b.getCenterZ();
            if (b.getCenterZ() > maxZ) maxZ = b.getCenterZ();

            minX -= SHORE_MARGIN;
            minZ -= SHORE_MARGIN;
            maxX += SHORE_MARGIN;
            maxZ += SHORE_MARGIN;

            int minCellX = (int) Math.floor(minX / cellSize);
            int maxCellX = (int) Math.floor(maxX / cellSize);
            int minCellZ = (int) Math.floor(minZ / cellSize);
            int maxCellZ = (int) Math.floor(maxZ / cellSize);

            for (int cx = minCellX; cx <= maxCellX; cx++) {
                for (int cz = minCellZ; cz <= maxCellZ; cz++) {
                    long key = cellKey(cx, cz);
                    index.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
                }
            }
        }

        Map<Long, List<RiverBodyData>> frozen = new HashMap<>();
        for (Map.Entry<Long, List<RiverBodyData>> e : index.entrySet()) {
            frozen.put(e.getKey(), Collections.unmodifiableList(
                new ArrayList<>(e.getValue())));
        }

        return new RiverBodyIndex(Collections.unmodifiableMap(frozen), cellSize);
    }

    public List<RiverBodyData> queryCell(double worldX, double worldZ) {
        int cellX = (int) Math.floor(worldX / cellSize);
        int cellZ = (int) Math.floor(worldZ / cellSize);
        long key = cellKey(cellX, cellZ);
        List<RiverBodyData> list = bodiesByCell.get(key);
        return list != null ? list : Collections.emptyList();
    }
}
