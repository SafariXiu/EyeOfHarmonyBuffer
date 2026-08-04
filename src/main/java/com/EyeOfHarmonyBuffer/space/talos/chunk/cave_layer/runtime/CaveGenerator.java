package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 洞穴网络生成器（纯函数，无状态、无随机流）。
 *
 * 结构：
 *   - 骨干晶格：每 3×3 个 256 格单元（约 768 blocks）保证一个骨干节点，
 *     骨干之间沿晶格边连接 → 全局连通的主通道网；
 *   - 分支：普通单元按概率生成 0~2 个节点，连接到 3×3 邻域最近节点；
 *   - 大厅 / 竖井 / 入口：节点类型决定附加结构。
 *
 * 一切由 (seed, cellX, cellZ, index) 哈希决定，跨区块可复现、无接缝。
 */
public final class CaveGenerator {

    /** 水平单元边长（blocks）。 */
    public static final int CELL_BLOCKS = 256;

    /** 骨干晶格步长（单元）：每 1 个单元一条主干，约 256 blocks。 */
    public static final int BACKBONE_STEP = 1;

    /** 线段搜索半径（单元）：最近节点搜索 ±2，边最长跨 4 个单元，取 ±4。 */
    public static final int SEGMENT_REACH_CELLS = 4;

    // 参数盐（保持确定性、互不串扰）
    private static final int SALT_DETAIL_COUNT = 0x1A;
    private static final int SALT_NODE_POS = 0x2B;
    private static final int SALT_BAND = 0x3C;
    private static final int SALT_CHAMBER = 0x4D;
    private static final int SALT_ENTRANCE = 0x5E;
    private static final int SALT_SHAFT = 0x6F;
    private static final int SALT_EDGE_JITTER = 0x7A;
    private static final int SALT_EDGE_RADIUS = 0x8B;

    private CaveGenerator() {}

    // ------------------------------------------------------------
    // 节点
    // ------------------------------------------------------------

    /** 生成某个 256 格单元的全部节点（确定性）。 */
    public static List<CaveNode> nodesForCell(int cellX, int cellZ, long seed) {
        List<CaveNode> nodes = new ArrayList<CaveNode>();
        boolean backbone = isBackboneCell(cellX, cellZ);

        if (backbone) {
            long id = nodeId(seed, cellX, cellZ, 0);
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 0, seed, SALT_NODE_POS, 96.0, 160.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 1, seed, SALT_NODE_POS, 96.0, 160.0);
            float y = (float) CaveMath.hashRange(
                cellX, cellZ, 2, seed, SALT_NODE_POS, 26.0, 46.0);
            nodes.add(new CaveNode(
                id, cellX, cellZ, x, y, z,
                CaveNode.KIND_BACKBONE, CaveNode.BAND_MID,
                0, 0, 0, 0, 0
            ));
        }

        // 分支节点
        int detailCount = detailCount(cellX, cellZ, seed);
        for (int i = 0; i < detailCount; i++) {
            int band = pickBand(cellX, cellZ, i, seed);
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, i * 3 + 0, seed, SALT_NODE_POS, 24.0, 232.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, i * 3 + 1, seed, SALT_NODE_POS, 24.0, 232.0);
            float y = (float) bandY(band, cellX, cellZ, i, seed);

            boolean chamber = CaveMath.hash01(
                cellX, cellZ, i, seed, SALT_CHAMBER) < 0.22;
            int kind = chamber ? CaveNode.KIND_CHAMBER : CaveNode.KIND_NORMAL;
            float crx = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i, seed, SALT_CHAMBER, 8.0, 18.0) : 0;
            float cry = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i + 100, seed, SALT_CHAMBER, 6.0, 13.0) : 0;
            float crz = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i + 200, seed, SALT_CHAMBER, 8.0, 18.0) : 0;

            nodes.add(new CaveNode(
                nodeId(seed, cellX, cellZ, i + 1),
                cellX, cellZ, x, y, z,
                kind, band, crx, cry, crz, 0, 0
            ));
        }

        // 竖井：为单元内第一个 MID 节点补一个深井孪生
        CaveNode mid = null;
        for (CaveNode n : nodes) {
            if (n.band == CaveNode.BAND_MID) {
                mid = n;
                break;
            }
        }
        if (mid != null
            && CaveMath.hash01(cellX, cellZ, 77, seed, SALT_SHAFT) < 0.10) {
            int idx = nodes.size();
            float y = (float) CaveMath.hashRange(
                cellX, cellZ, 78, seed, SALT_SHAFT, 10.0, 20.0);
            nodes.add(new CaveNode(
                nodeId(seed, cellX, cellZ, idx),
                cellX, cellZ, mid.x, y, mid.z,
                CaveNode.KIND_SHAFT, CaveNode.BAND_DEEP,
                0, 0, 0, 0, mid.id
            ));
        }

        // 入口 / 天坑
        double entRoll = CaveMath.hash01(cellX, cellZ, 99, seed, SALT_ENTRANCE);
        if (entRoll < 0.30) {
            int idx = nodes.size();
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 100, seed, SALT_ENTRANCE, 48.0, 208.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 101, seed, SALT_ENTRANCE, 48.0, 208.0);
            float y = (float) CaveMath.hashRange(
                cellX, cellZ, 102, seed, SALT_ENTRANCE, 50.0, 58.0);
            nodes.add(new CaveNode(
                nodeId(seed, cellX, cellZ, idx),
                cellX, cellZ, x, y, z,
                CaveNode.KIND_ENTRANCE, CaveNode.BAND_UPPER,
                0, 0, 0, 2, 0
            ));
        } else if (entRoll < 0.36) {
            int idx = nodes.size();
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 110, seed, SALT_ENTRANCE, 48.0, 208.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 111, seed, SALT_ENTRANCE, 48.0, 208.0);
            float y = (float) CaveMath.hashRange(
                cellX, cellZ, 112, seed, SALT_ENTRANCE, 52.0, 60.0);
            nodes.add(new CaveNode(
                nodeId(seed, cellX, cellZ, idx),
                cellX, cellZ, x, y, z,
                CaveNode.KIND_SINKHOLE, CaveNode.BAND_UPPER,
                0, 0, 0, 1, 0
            ));
        }

        return nodes;
    }

    private static boolean isBackboneCell(int cellX, int cellZ) {
        return Math.floorMod(cellX, BACKBONE_STEP) == 0
            && Math.floorMod(cellZ, BACKBONE_STEP) == 0;
    }

    private static int detailCount(int cellX, int cellZ, long seed) {
        double r = CaveMath.hash01(cellX, cellZ, 0, seed, SALT_DETAIL_COUNT);
        if (r < 0.02) {
            return 0;
        }
        if (r < 0.12) {
            return 1;
        }
        if (r < 0.40) {
            return 2;
        }
        if (r < 0.75) {
            return 3;
        }
        return 4;
    }

    private static int pickBand(int cellX, int cellZ, int i, long seed) {
        double r = CaveMath.hash01(cellX, cellZ, i + 1, seed, SALT_BAND);
        if (r < 0.55) {
            return CaveNode.BAND_MID;
        }
        if (r < 0.80) {
            return CaveNode.BAND_UPPER;
        }
        return CaveNode.BAND_DEEP;
    }

    private static double bandY(int band, int cellX, int cellZ, int i,
                                long seed) {
        switch (band) {
            case CaveNode.BAND_UPPER:
                return CaveMath.hashRange(
                    cellX, cellZ, i + 10, seed, SALT_BAND, 46.0, 58.0);
            case CaveNode.BAND_DEEP:
                return CaveMath.hashRange(
                    cellX, cellZ, i + 20, seed, SALT_BAND, 10.0, 24.0);
            case CaveNode.BAND_MID:
            default:
                return CaveMath.hashRange(
                    cellX, cellZ, i + 30, seed, SALT_BAND, 24.0, 46.0);
        }
    }

    private static long nodeId(long seed, int cellX, int cellZ, int index) {
        long h = seed;
        h = CaveMath.mix64(h ^ (cellX * 0x9E3779B97F4A7C15L));
        h = CaveMath.mix64(h ^ (cellZ * 0xBF58476D1CE4E5B9L));
        h = CaveMath.mix64(h ^ (index * 0x94D049BB133111EBL));
        return h;
    }

    // ------------------------------------------------------------
    // 边 → 通道线段
    // ------------------------------------------------------------

    /** 连接两个节点生成 3D 折线通道（确定性）。 */
    public static CaveSegment buildSegment(CaveNode a, CaveNode b, long seed) {
        double ax = a.x, ay = a.y, az = a.z;
        double bx = b.x, by = b.y, bz = b.z;
        double dist = Math.sqrt(
            (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
                + (bz - az) * (bz - az)
        );
        int n = (int) Math.round(dist / 24.0) + 1;
        if (n < 2) {
            n = 2;
        }
        if (n > 48) {
            n = 48;
        }

        double baseR = baseRadius(a.kind, b.kind);
        long edgeId = edgeId(a.id, b.id);

        // 垂直抖动基向量（与 AB 垂直）
        double abx = bx - ax, aby = by - ay, abz = bz - az;
        double[] p = perpendicular(abx, aby, abz);
        double[] q0 = cross(abx, aby, abz, p[0], p[1], p[2]);
        double qLen = Math.sqrt(q0[0] * q0[0] + q0[1] * q0[1] + q0[2] * q0[2]);
        double[] q = (qLen > 1.0e-9)
            ? new double[] {q0[0] / qLen, q0[1] / qLen, q0[2] / qLen}
            : new double[] {1.0, 0.0, 0.0};
        double amp = Math.min(7.0, dist * 0.07);

        float[] xs = new float[n];
        float[] ys = new float[n];
        float[] zs = new float[n];
        float[] rs = new float[n];
        float maxR = 0;

        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            double px = ax + (bx - ax) * t;
            double py = ay + (by - ay) * t;
            double pz = az + (bz - az) * t;

            // 只有中间点做抖动：端点必须锚定在节点精确坐标上，
            // 否则入口竖井（垂直开在节点正上方）会与通道起点错开十几格，
            // 出现"入口挖了但连不上洞穴"的死井。
            if (i > 0 && i < n - 1) {
                double jx = CaveMath.hash01(
                    edgeId, i, 0, seed, SALT_EDGE_JITTER) - 0.5;
                double jy = CaveMath.hash01(
                    edgeId, i, 1, seed, SALT_EDGE_JITTER) - 0.5;
                px += p[0] * jx * 2.0 * amp + q[0] * jy * 2.0 * amp;
                py += p[1] * jx * 2.0 * amp + q[1] * jy * 2.0 * amp;
                pz += p[2] * jx * 2.0 * amp + q[2] * jy * 2.0 * amp;
            }

            double r = baseR * noise1D(edgeId, t * 4.0, seed);
            xs[i] = (float) px;
            ys[i] = (float) py;
            zs[i] = (float) pz;
            rs[i] = (float) r;
            if (r > maxR) {
                maxR = (float) r;
            }
        }

        float margin = maxR + 3.0f;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        return new CaveSegment(
            edgeId,
            xs, ys, zs, rs,
            minX - margin, minY - margin, minZ - margin,
            maxX + margin, maxY + margin, maxZ + margin
        );
    }

    private static double baseRadius(int ka, int kb) {
        if (ka == CaveNode.KIND_SHAFT || kb == CaveNode.KIND_SHAFT) {
            return 3.4;
        }
        if (ka == CaveNode.KIND_BACKBONE || kb == CaveNode.KIND_BACKBONE) {
            return 6.8;
        }
        if (ka == CaveNode.KIND_CHAMBER || kb == CaveNode.KIND_CHAMBER) {
            return 5.0;
        }
        if (ka == CaveNode.KIND_ENTRANCE || kb == CaveNode.KIND_ENTRANCE) {
            return 2.8;
        }
        if (ka == CaveNode.KIND_SINKHOLE || kb == CaveNode.KIND_SINKHOLE) {
            return 2.2;
        }
        return 5.7;
    }

    /** 沿路径的确定性 1D 噪声（0.62~1.38 倍半径）。 */
    private static double noise1D(long id, double x, long seed) {
        int i0 = (int) Math.floor(x);
        double f = x - i0;
        double u = f * f * (3.0 - 2.0 * f);
        double a = CaveMath.hash01(id, i0, 0, seed, SALT_EDGE_RADIUS);
        double b = CaveMath.hash01(id, i0 + 1, 0, seed, SALT_EDGE_RADIUS);
        return 0.62 + 0.76 * (a + (b - a) * u);
    }

    private static double[] perpendicular(double ax, double ay, double az) {
        double[] up = {0.0, 1.0, 0.0};
        double[] c = cross(ax, ay, az, up[0], up[1], up[2]);
        double len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        if (len < 1.0e-6) {
            // AB 与 Y 平行：改用 X 轴
            c = cross(ax, ay, az, 1.0, 0.0, 0.0);
            len = Math.sqrt(c[0] * c[0] + c[1] * c[1] + c[2] * c[2]);
        }
        return new double[] {c[0] / len, c[1] / len, c[2] / len};
    }

    private static double[] cross(double ax, double ay, double az,
                                  double bx, double by, double bz) {
        return new double[] {
            ay * bz - az * by,
            az * bx - ax * bz,
            ax * by - ay * bx
        };
    }

    public static long edgeId(long idA, long idB) {
        long lo = Math.min(idA, idB);
        long hi = Math.max(idA, idB);
        return lo ^ CaveMath.mix64(hi);
    }

    // ------------------------------------------------------------
    // 区块数据
    // ------------------------------------------------------------

    /**
     * 构建某个区块相关的全部洞穴数据。
     *
     * @param nodeCache 单元节点缓存（可并发；缺失时现算）
     * @param edgeCache 单元线段缓存（可并发；缺失时现算）
     */
    public static CaveChunkData buildChunkData(int chunkX, int chunkZ,
                                               long seed,
                                               Map<Long, List<CaveNode>> nodeCache,
                                               Map<Long, List<CaveSegment>> edgeCache) {
        int cellX = chunkX >> 4;
        int cellZ = chunkZ >> 4;
        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;

        List<CaveSegment> segments = new ArrayList<CaveSegment>();
        List<CaveChamber> chambers = new ArrayList<CaveChamber>();
        List<CaveEntrance> entrances = new ArrayList<CaveEntrance>();
        Set<Long> seenEdges = new HashSet<Long>();

        for (int dz = -SEGMENT_REACH_CELLS; dz <= SEGMENT_REACH_CELLS; dz++) {
            for (int dx = -SEGMENT_REACH_CELLS; dx <= SEGMENT_REACH_CELLS; dx++) {
                int cx = cellX + dx;
                int cz = cellZ + dz;
                long ckey = cellKey(cx, cz);
                List<CaveSegment> cellSegs = edgeCache.get(ckey);
                if (cellSegs == null) {
                    cellSegs = segmentsForCell(cx, cz, seed, nodeCache);
                    List<CaveSegment> prev = edgeCache.putIfAbsent(ckey, cellSegs);
                    if (prev != null) {
                        cellSegs = prev;
                    }
                }
                for (CaveSegment seg : cellSegs) {
                    if (seenEdges.add(seg.edgeId)
                        && seg.maxX >= x0 && seg.minX <= x0 + 16
                        && seg.maxZ >= z0 && seg.minZ <= z0 + 16) {
                        segments.add(seg);
                    }
                }

                // 大厅：±1 单元内的大厅可能伸入本区块
                if (dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1) {
                    List<CaveNode> nodes = nodesOf(cx, cz, seed, nodeCache);
                    for (CaveNode node : nodes) {
                        if (node.kind == CaveNode.KIND_CHAMBER) {
                            CaveChamber ch = new CaveChamber(
                                node.x, node.y, node.z,
                                node.chamberRx, node.chamberRy, node.chamberRz
                            );
                            if (ch.maxX >= x0 && ch.minX <= x0 + 16
                                && ch.maxZ >= z0 && ch.minZ <= z0 + 16) {
                                chambers.add(ch);
                            }
                        }
                    }
                }
            }
        }

        // 入口：只取本区块所在单元的入口（竖井列必须在区块内）
        List<CaveNode> ownNodes = nodesOf(cellX, cellZ, seed, nodeCache);
        for (CaveNode node : ownNodes) {
            if (!node.isEntranceLike()) {
                continue;
            }
            int ex = (int) Math.floor(node.x);
            int ez = (int) Math.floor(node.z);
            if (ex >= x0 && ex < x0 + 16 && ez >= z0 && ez < z0 + 16) {
                entrances.add(new CaveEntrance(
                    ex, ez, (int) Math.floor(node.y),
                    node.shaftRadius, node.kind == CaveNode.KIND_SINKHOLE
                ));
            }
        }

        return new CaveChunkData(segments, chambers, entrances);
    }

    /** 某单元出发的全部线段（不跨单元去重；缓存用）。 */
    public static List<CaveSegment> segmentsForCell(
        int cellX, int cellZ, long seed,
        Map<Long, List<CaveNode>> nodeCache) {
        List<CaveSegment> out = new ArrayList<CaveSegment>();
        for (CaveNode node : nodesOf(cellX, cellZ, seed, nodeCache)) {
            collectEdges(node, seed, nodeCache, out);
        }
        return out;
    }

    private static void collectEdges(CaveNode node, long seed,
                                     Map<Long, List<CaveNode>> nodeCache,
                                     List<CaveSegment> out) {
        if (node.kind == CaveNode.KIND_BACKBONE) {
            // 晶格边：向右 / 向下 BACKBONE_STEP 格
            for (int d = 0; d < 2; d++) {
                int nx = node.cellX + (d == 0 ? BACKBONE_STEP : 0);
                int nz = node.cellZ + (d == 0 ? 0 : BACKBONE_STEP);
                if (!isBackboneCell(nx, nz)) {
                    continue;
                }
                CaveNode target = backboneNodeOf(nx, nz, seed, nodeCache);
                if (target != null) {
                    out.add(buildSegment(node, target, seed));
                }
            }
            return;
        }

        if (node.kind == CaveNode.KIND_SHAFT) {
            CaveNode twin = findNodeById(node.twinId, node.cellX, node.cellZ,
                seed, nodeCache);
            if (twin != null) {
                out.add(buildSegment(node, twin, seed));
            }
            return;
        }

        // 普通 / 大厅 / 入口 / 天坑：连接 5×5 邻域最近节点（优先跨单元）
        CaveNode first = nearestNode(node, seed, nodeCache, -1);
        if (first != null) {
            out.add(buildSegment(node, first, seed));
        }
        // 第二条支路：连接次近节点，形成分支网络
        CaveNode second = nearestNode(node, seed, nodeCache,
            first != null ? first.id : -1);
        if (second != null) {
            out.add(buildSegment(node, second, seed));
        }
        // 第三条支路：进一步增加通道覆盖
        CaveNode third = nearestNode(node, seed, nodeCache,
            second != null ? second.id : (first != null ? first.id : -1));
        if (third != null) {
            out.add(buildSegment(node, third, seed));
        }
    }

    private static CaveNode nearestNode(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        long excludeId) {
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (CaveNode n : nodesOf(
                    node.cellX + dx, node.cellZ + dz, seed, nodeCache)) {
                    if (n.id == node.id || n.id == excludeId) {
                        continue;
                    }
                    double d = distSq(node, n);
                    if (d < bestD - 1.0e-9
                        || (Math.abs(d - bestD) <= 1.0e-9 && n.id < best.id)) {
                        best = n;
                        bestD = d;
                    }
                }
            }
        }
        if (best == null) {
            return null;
        }
        // 避免同单元互连成孤岛：若最近是同单元，且存在异单元节点，取异单元最近
        if (best.cellX == node.cellX && best.cellZ == node.cellZ) {
            CaveNode other = null;
            double od = Double.POSITIVE_INFINITY;
            for (int dz = -2; dz <= 2; dz++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = node.cellX + dx;
                    int nz = node.cellZ + dz;
                    if (nx == node.cellX && nz == node.cellZ) {
                        continue;
                    }
                    for (CaveNode n : nodesOf(nx, nz, seed, nodeCache)) {
                        if (n.id == node.id || n.id == excludeId) {
                            continue;
                        }
                        double d = distSq(node, n);
                        if (d < od - 1.0e-9
                            || (Math.abs(d - od) <= 1.0e-9 && n.id < other.id)) {
                            other = n;
                            od = d;
                        }
                    }
                }
            }
            if (other != null) {
                return other;
            }
        }
        return best;
    }

    private static double distSq(CaveNode a, CaveNode b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static CaveNode backboneNodeOf(int cx, int cz, long seed,
                                           Map<Long, List<CaveNode>> nodeCache) {
        for (CaveNode n : nodesOf(cx, cz, seed, nodeCache)) {
            if (n.kind == CaveNode.KIND_BACKBONE) {
                return n;
            }
        }
        return null;
    }

    private static CaveNode findNodeById(long id, int cx, int cz, long seed,
                                         Map<Long, List<CaveNode>> nodeCache) {
        for (CaveNode n : nodesOf(cx, cz, seed, nodeCache)) {
            if (n.id == id) {
                return n;
            }
        }
        return null;
    }

    private static List<CaveNode> nodesOf(int cx, int cz, long seed,
                                          Map<Long, List<CaveNode>> nodeCache) {
        long key = cellKey(cx, cz);
        List<CaveNode> nodes = nodeCache.get(key);
        if (nodes == null) {
            nodes = nodesForCell(cx, cz, seed);
            List<CaveNode> prev = nodeCache.putIfAbsent(key, nodes);
            if (prev != null) {
                nodes = prev;
            }
        }
        return nodes;
    }

    public static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }
}
