package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverEdgeData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverNetwork;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 在超级大陆实例化后的 RiverNetwork 上，
 * 根据 TalosLandMask 把「出海后的河段」截短为
 * “第一次进海 + bufferLenBlocks 弧长”。
 *
 * 语义：
 *   - 先找到每条 edge 第一次从陆地进入海洋的线段；
 *   - 在线段上收敛出真实海岸线交点（折线顶点可能离真实海岸几十格）；
 *   - 再从该交点沿折线继续往下游走 bufferLenBlocks；
 *   - 在这个位置插入一个截断点，丢弃之后所有点；
 *   - 未进入海的 edge 原样保留。
 */

public final class CoastClipper {

    private CoastClipper() {}

    /** 截断时在「第一次入海点」之后继续保留的弧长（blocks）。 */
    public static final double DEFAULT_BUFFER_BLOCKS = 20.0;

    public static RiverNetwork clipNetworkAtCoast(
        RiverNetwork original,
        int worldSeedInt,
        double bufferLenBlocks
    ) {
        List<RiverEdgeData> originalEdges = original.getEdges();
        if (originalEdges.isEmpty()) {
            return original;
        }

        List<RiverEdgeData> clippedEdges = new ArrayList<RiverEdgeData>(originalEdges.size());

        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (RiverEdgeData e : originalEdges) {
            List<RiverPoint> pts = e.getPoints();
            int n = pts.size();
            if (n < 2) {
                // 没有有效折线，直接跳过
                continue;
            }

            int firstSeaIdx = findFirstSeaIndex(pts, worldSeedInt);

            final List<RiverPoint> newPts;
            if (firstSeaIdx < 0) {
                newPts = pts;
            } else {
                newPts = clipPolylineFromIndex(
                    pts, firstSeaIdx, bufferLenBlocks, worldSeedInt
                );
                if (newPts.size() < 2) {
                    continue;
                }
            }

            for (RiverPoint p : newPts) {
                double x = p.getX();
                double z = p.getZ();
                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }

            RiverEdgeData clipped = new RiverEdgeData(
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
                Collections.unmodifiableList(newPts)
            );

            clippedEdges.add(clipped);
        }

        if (clippedEdges.isEmpty()) {
            return new RiverNetwork(
                original.getVersion(),
                original.getCoordinateScale(),
                original.getMinX(),
                original.getMinZ(),
                original.getMaxX(),
                original.getMaxZ(),
                original.getSeed(),
                Collections.emptyList()
            );
        }

        if (Double.isInfinite(minX)) {
            minX = original.getMinX();
            minZ = original.getMinZ();
            maxX = original.getMaxX();
            maxZ = original.getMaxZ();
        }

        return new RiverNetwork(
            original.getVersion(),
            original.getCoordinateScale(),
            minX,
            minZ,
            maxX,
            maxZ,
            original.getSeed(),
            Collections.unmodifiableList(clippedEdges)
        );
    }

    /**
     * 找到折线第一次从 “陆地 → 海洋” 的点索引。
     * 返回的索引 i 满足：
     *   - pts[i-1] 在陆地
     *   - pts[i]   在海洋
     * 若整条折线未发生这样的过渡，则返回 -1。
     */
    private static int findFirstSeaIndex(List<RiverPoint> pts, int worldSeedInt) {
        int n = pts.size();
        boolean prevLand = isLand(pts.get(0).getX(), pts.get(0).getZ(), worldSeedInt);

        for (int i = 1; i < n; i++) {
            RiverPoint cur = pts.get(i);
            boolean curLand = isLand(cur.getX(), cur.getZ(), worldSeedInt);

            if (prevLand && !curLand) {
                return i;
            }

            prevLand = curLand;
        }

        return -1;
    }

    private static boolean isLand(double x, double z, int worldSeedInt) {
        return TalosLandMask.isLandCheap(
            (int) Math.floor(x), (int) Math.floor(z), worldSeedInt
        );
    }

    /**
     * 已知 startSeaIdx 是第一次落在海洋上的顶点索引：
     *   - 保留 [0 .. startSeaIdx-1] 的点，并把「最后陆地点 → 第一个海点」
     *     的线段收敛成真实海岸线交点（稀疏折线时海顶点可能离海岸几十格，
     *     直接用顶点会把河口锚进深海，入海口 ramp 会在到达海岸前提前结束）；
     *   - 再从该交点开始，沿折线继续往下游走 bufferLen；
     *   - 在对应位置插一个截断点。
     */
    private static List<RiverPoint> clipPolylineFromIndex(
        List<RiverPoint> pts,
        int startSeaIdx,
        double bufferLen,
        int worldSeedInt
    ) {
        List<RiverPoint> out = new ArrayList<RiverPoint>();

        final RiverPoint start;
        if (startSeaIdx > 0) {
            for (int i = 0; i < startSeaIdx - 1; i++) {
                out.add(pts.get(i));
            }
            start = findCoastCrossing(
                pts.get(startSeaIdx - 1), pts.get(startSeaIdx), worldSeedInt
            );
            out.add(start);
        } else {
            for (int i = 0; i < startSeaIdx; i++) {
                out.add(pts.get(i));
            }
            start = pts.get(startSeaIdx);
            out.add(start);
        }

        double acc = 0.0;
        RiverPoint last = start;

        for (int i = startSeaIdx; i < pts.size(); i++) {
            RiverPoint cur = pts.get(i);
            double dx = cur.getX() - last.getX();
            double dz = cur.getZ() - last.getZ();
            double segLen = Math.hypot(dx, dz);

            if (acc + segLen <= bufferLen) {
                out.add(cur);
                acc += segLen;
                last = cur;
            } else {
                double remain = bufferLen - acc;
                if (remain < 0.0) remain = 0.0;

                double t = (segLen > 1.0e-6) ? (remain / segLen) : 0.0;
                double clipX = last.getX() + dx * t;
                double clipZ = last.getZ() + dz * t;

                out.add(new RiverPoint(clipX, clipZ));
                break;
            }
        }

        return out;
    }

    /**
     * 在「最后一个陆地点 → 第一个海点」线段上求真实海岸线交点：
     * 先按约 2 格步长找到第一个非陆地采样，再二分收敛到边界。
     */
    private static RiverPoint findCoastCrossing(
        RiverPoint land, RiverPoint sea, int worldSeedInt
    ) {
        double dx = sea.getX() - land.getX();
        double dz = sea.getZ() - land.getZ();
        double len = Math.hypot(dx, dz);
        int steps = Math.max(1, (int) Math.ceil(len / 2.0));

        for (int i = 1; i <= steps; i++) {
            double t = (double) i / steps;
            double px = land.getX() + dx * t;
            double pz = land.getZ() + dz * t;
            if (isLand(px, pz, worldSeedInt)) {
                continue;
            }

            double a = (double) (i - 1) / steps;
            double b = t;
            for (int iter = 0; iter < 12; iter++) {
                double mid = (a + b) / 2.0;
                double mx = land.getX() + dx * mid;
                double mz = land.getZ() + dz * mid;
                if (isLand(mx, mz, worldSeedInt)) {
                    a = mid;
                } else {
                    b = mid;
                }
            }

            double fx = land.getX() + dx * b;
            double fz = land.getZ() + dz * b;
            return new RiverPoint(fx, fz);
        }

        // 理论上不会走到这里（sea 端必然是非陆地）
        return sea;
    }
}
