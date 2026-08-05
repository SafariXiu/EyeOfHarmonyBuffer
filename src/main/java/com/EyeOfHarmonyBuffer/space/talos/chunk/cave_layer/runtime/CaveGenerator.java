package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format.CaveTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private static final int SALT_COLLAPSE = 0x9C;
    private static final int SALT_MEGA_HALL = 0xA1;

    /** 大厅出现概率（原 22%，巨型大厅更稀有）。 */
    private static final double CHAMBER_CHANCE = 0.10;
    /** 大厅水平半轴范围（blocks）：直径约 52~88，覆盖约 3~5 个区块。 */
    private static final double CHAMBER_RADIUS_XZ_MIN = 26.0;
    private static final double CHAMBER_RADIUS_XZ_MAX = 44.0;
    /** 大厅垂直半轴范围（blocks）：总高约 28~34 格。 */
    private static final double CHAMBER_RADIUS_Y_MIN = 14.0;
    private static final double CHAMBER_RADIUS_Y_MAX = 17.0;
    /** 大厅中心 Y 上限：避免巨型空腔顶到地表。 */
    private static final float CHAMBER_MAX_CENTER_Y = 78.0f;

    /** 洞厅超级格边长（blocks）：每个洞厅独占一个区域，避免重叠。 */
    public static final int MEGA_HALL_CELL_BLOCKS = 4096;
    /** 洞厅生成概率：每个 4096×4096 超级格约 0.5%。 */
    public static final double MEGA_HALL_CHANCE = 0.005;
    /** 洞厅水平半径范围：直径约 1000~1800 格。 */
    public static final double MEGA_HALL_RADIUS_XZ_MIN = 500.0;
    public static final double MEGA_HALL_RADIUS_XZ_MAX = 900.0;
    /** 洞厅垂直半径范围：总高约 48~58 格，配合中心保证底部≥5、顶部≤64。 */
    public static final double MEGA_HALL_RADIUS_Y_MIN = 24.0;
    public static final double MEGA_HALL_RADIUS_Y_MAX = 29.0;
    /** 洞厅中心 Y 范围（配合半径钳制在 5~64 内）。 */
    public static final double MEGA_HALL_CENTER_Y_MIN = 34.0;
    public static final double MEGA_HALL_CENTER_Y_MAX = 35.0;
    /** 洞厅垂直硬边界：底不低于 5，顶不高于 64。 */
    public static final int MEGA_HALL_MIN_Y = 5;
    public static final int MEGA_HALL_MAX_Y = 64;
    /** 洞厅中心距超级格边缘的最小距离（保证整厅落在本格内）。 */
    private static final double MEGA_HALL_MARGIN = 1000.0;

    private static final Object NO_MEGA_HALL = new Object();
    private static final ConcurrentHashMap<Long, Object> MEGA_HALL_CACHE =
        new ConcurrentHashMap<Long, Object>();
    private static final int MEGA_HALL_CACHE_LIMIT = 8192;

    private CaveGenerator() {}

    // ------------------------------------------------------------
    // 节点
    // ------------------------------------------------------------

    /** 生成某个 256 格单元的全部节点（确定性）。 */
    public static List<CaveNode> nodesForCell(int cellX, int cellZ, long seed) {
        List<CaveNode> nodes = new ArrayList<CaveNode>();
        int hallSuperX = Math.floorDiv(
            cellX, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        int hallSuperZ = Math.floorDiv(
            cellZ, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        CaveMegaHall megaHall = megaHallForSupercell(
            hallSuperX, hallSuperZ, seed);
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
            boolean chamber = CaveMath.hash01(
                cellX, cellZ, i, seed, SALT_CHAMBER) < CHAMBER_CHANCE;
            int kind = chamber ? CaveNode.KIND_CHAMBER : CaveNode.KIND_NORMAL;
            float crx = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i, seed, SALT_CHAMBER,
                CHAMBER_RADIUS_XZ_MIN, CHAMBER_RADIUS_XZ_MAX) : 0;
            float cry = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i + 100, seed, SALT_CHAMBER,
                CHAMBER_RADIUS_Y_MIN, CHAMBER_RADIUS_Y_MAX) : 0;
            float crz = chamber ? (float) CaveMath.hashRange(
                cellX, cellZ, i + 200, seed, SALT_CHAMBER,
                CHAMBER_RADIUS_XZ_MIN, CHAMBER_RADIUS_XZ_MAX) : 0;

            float y = (float) bandY(band, cellX, cellZ, i, seed);
            if (chamber) {
                // 巨型大厅必须整体落在可用高度内，避免挖穿基岩或顶到地表。
                float minCy = cry + 4.0f;
                float maxCy = CHAMBER_MAX_CENTER_Y - cry;
                if (y < minCy) {
                    y = minCy;
                } else if (y > maxCy) {
                    y = maxCy;
                }
            }
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

        // 洞厅优先：清掉洞厅范围内的其他所有节点，只留洞厅自己的超级节点
        if (megaHall != null) {
            List<CaveNode> kept = new ArrayList<CaveNode>(nodes.size());
            for (CaveNode n : nodes) {
                if (!nodeInsideMegaHall(n, megaHall)) {
                    kept.add(n);
                }
            }
            nodes = kept;
        }

        // 洞厅：中心所在单元挂一个超级节点，用于连接主干网
        CaveNode mega = megaHallNodeForCell(cellX, cellZ, seed);
        if (mega != null) {
            nodes.add(mega);
        }

        return nodes;
    }

    /** 查询某个 4096 超级格是否有洞厅（带缓存，确定性）。 */
    public static CaveMegaHall megaHallForSupercell(int superX, int superZ,
                                                    long seed) {
        long key = megaHallKey(seed, superX, superZ);
        Object cached = MEGA_HALL_CACHE.get(key);
        if (cached != null) {
            return cached == NO_MEGA_HALL ? null : (CaveMegaHall) cached;
        }
        if (MEGA_HALL_CACHE.size() > MEGA_HALL_CACHE_LIMIT) {
            MEGA_HALL_CACHE.clear();
        }

        CaveMegaHall hall = computeMegaHall(superX, superZ, seed);
        MEGA_HALL_CACHE.put(key, hall != null ? hall : NO_MEGA_HALL);
        return hall;
    }

    /** 坐标是否落在某个洞厅内（用于标签查询）。 */
    public static CaveMegaHall megaHallAt(int worldX, int worldZ, long seed) {
        int superX = Math.floorDiv(worldX, MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(worldZ, MEGA_HALL_CELL_BLOCKS);
        CaveMegaHall hall = megaHallForSupercell(superX, superZ, seed);
        if (hall != null
            && hall.insideHorizontal(worldX + 0.5, worldZ + 0.5)) {
            return hall;
        }
        return null;
    }

    /** 收集与某区块相交的洞厅（洞厅被限制在超级格内部，只需查本格）。 */
    private static void collectMegaHallsForChunk(int chunkX, int chunkZ,
                                                 long seed,
                                                 List<CaveMegaHall> out) {
        int superX = Math.floorDiv(
            chunkX * 16, MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(
            chunkZ * 16, MEGA_HALL_CELL_BLOCKS);
        CaveMegaHall hall = megaHallForSupercell(superX, superZ, seed);
        if (hall != null && hall.intersectsChunk(chunkX, chunkZ)) {
            out.add(hall);
        }
    }

    /** 释放洞厅缓存（世界卸载时调用）。 */
    public static void clearMegaHallCache() {
        MEGA_HALL_CACHE.clear();
    }

    private static CaveMegaHall computeMegaHall(int superX, int superZ,
                                                long seed) {
        if (CaveMath.hash01(
                superX, superZ, 0, seed, SALT_MEGA_HALL
            ) >= MEGA_HALL_CHANCE) {
            return null;
        }
        double cx = superX * MEGA_HALL_CELL_BLOCKS + CaveMath.hashRange(
            superX, superZ, 1, seed, SALT_MEGA_HALL,
            MEGA_HALL_MARGIN,
            MEGA_HALL_CELL_BLOCKS - MEGA_HALL_MARGIN);
        double cz = superZ * MEGA_HALL_CELL_BLOCKS + CaveMath.hashRange(
            superX, superZ, 2, seed, SALT_MEGA_HALL,
            MEGA_HALL_MARGIN,
            MEGA_HALL_CELL_BLOCKS - MEGA_HALL_MARGIN);
        double rx = CaveMath.hashRange(
            superX, superZ, 3, seed, SALT_MEGA_HALL,
            MEGA_HALL_RADIUS_XZ_MIN, MEGA_HALL_RADIUS_XZ_MAX);
        double rz = CaveMath.hashRange(
            superX, superZ, 4, seed, SALT_MEGA_HALL,
            MEGA_HALL_RADIUS_XZ_MIN, MEGA_HALL_RADIUS_XZ_MAX);
        double ry = CaveMath.hashRange(
            superX, superZ, 5, seed, SALT_MEGA_HALL,
            MEGA_HALL_RADIUS_Y_MIN, MEGA_HALL_RADIUS_Y_MAX);
        double cy = CaveMath.hashRange(
            superX, superZ, 6, seed, SALT_MEGA_HALL,
            MEGA_HALL_CENTER_Y_MIN, MEGA_HALL_CENTER_Y_MAX);
        // 硬性钳制：洞厅整体落在 5~64 之间
        if (cy - ry < MEGA_HALL_MIN_Y) {
            cy = MEGA_HALL_MIN_Y + ry;
        }
        if (cy + ry > MEGA_HALL_MAX_Y) {
            cy = MEGA_HALL_MAX_Y - ry;
        }

        // 群系限制：中心 + 四角都必须是 Alpine / Polar Desert
        if (!allowedMegaHallBiome((int) cx, (int) cz, (int) seed)
            || !allowedMegaHallBiome(
                (int) (cx - rx * 0.8), (int) (cz - rz * 0.8), (int) seed)
            || !allowedMegaHallBiome(
                (int) (cx + rx * 0.8), (int) (cz - rz * 0.8), (int) seed)
            || !allowedMegaHallBiome(
                (int) (cx - rx * 0.8), (int) (cz + rz * 0.8), (int) seed)
            || !allowedMegaHallBiome(
                (int) (cx + rx * 0.8), (int) (cz + rz * 0.8), (int) seed)) {
            return null;
        }
        return new CaveMegaHall(cx, cy, cz, rx, ry, rz, seed);
    }

    private static boolean allowedMegaHallBiome(int wx, int wz, int seed) {
        BiomeGenBase biome = TalosMacroClimate.getBiome(wx, wz, seed);
        return biome == TalosBiomes.TALOS_ALPINE
            || biome == TalosBiomes.TALOS_POLAR_DESERT;
    }

    /** 洞厅中心所在单元对应的网络节点；其他单元返回 null。 */
    private static CaveNode megaHallNodeForCell(int cellX, int cellZ,
                                                long seed) {
        int superX = Math.floorDiv(
            cellX, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        int superZ = Math.floorDiv(
            cellZ, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        CaveMegaHall hall = megaHallForSupercell(superX, superZ, seed);
        if (hall == null) {
            return null;
        }
        int hcX = Math.floorDiv((int) Math.floor(hall.cx), CELL_BLOCKS);
        int hcZ = Math.floorDiv((int) Math.floor(hall.cz), CELL_BLOCKS);
        if (hcX != cellX || hcZ != cellZ) {
            return null;
        }
        return new CaveNode(
            nodeId(seed, cellX, cellZ, 0xFFFF),
            cellX, cellZ,
            (float) hall.cx, (float) hall.cy, (float) hall.cz,
            CaveNode.KIND_MEGA_HALL, CaveNode.BAND_MID,
            (float) hall.rx, (float) hall.ry, (float) hall.rz,
            0, 0
        );
    }

    /** 节点是否落在洞厅范围内（含边缘 4 格禁装饰带）。 */
    private static boolean nodeInsideMegaHall(CaveNode node,
                                              CaveMegaHall hall) {
        return hall.nearHorizontal(node.x, node.z, 4.0)
            && node.y >= hall.minY - 4.0
            && node.y <= hall.maxY + 4.0;
    }

    private static long megaHallKey(long seed, int superX, int superZ) {
        long h = seed;
        h = CaveMath.mix64(h ^ (superX * 0x9E3779B97F4A7C15L));
        h = CaveMath.mix64(h ^ (superZ * 0xBF58476D1CE4E5B9L));
        return h;
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
        // 连接大厅的通道不允许塌方，避免巨型空腔被碎石填掉。
        boolean collapsed = a.kind != CaveNode.KIND_CHAMBER
            && b.kind != CaveNode.KIND_CHAMBER
            && CaveMath.hash01(
                edgeId, 0, 0, seed, SALT_COLLAPSE) < 0.10;

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
            collapsed,
            xs, ys, zs, rs,
            minX - margin, minY - margin, minZ - margin,
            maxX + margin, maxY + margin, maxZ + margin
        );
    }

    private static double baseRadius(int ka, int kb) {
        if (ka == CaveNode.KIND_MEGA_HALL || kb == CaveNode.KIND_MEGA_HALL) {
            return 10.0;
        }
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
        List<CaveMegaHall> megaHalls = new ArrayList<CaveMegaHall>(1);
        List<CaveTag> tags = CaveFlavorRegistry.tagsForCell(
            cellX, cellZ, seed
        );
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
                                node.chamberRx, node.chamberRy, node.chamberRz,
                                node.id
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

        // 洞厅：只查本区块所在超级格（洞厅被限制在超级格内部）
        collectMegaHallsForChunk(chunkX, chunkZ, seed, megaHalls);
        if (!megaHalls.isEmpty() && !tags.contains(CaveTag.MEGA_HALL)) {
            tags.add(CaveTag.MEGA_HALL);
        }

        return new CaveChunkData(
            segments, chambers, entrances, megaHalls, tags
        );
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

        if (node.kind == CaveNode.KIND_MEGA_HALL) {
            connectMegaHall(node, seed, nodeCache, out);
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
        return nearestNode(node, seed, nodeCache, excludeId, 2);
    }

    private static CaveNode nearestNode(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        long excludeId, int radius) {
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : nodesOf(
                    node.cellX + dx, node.cellZ + dz, seed, nodeCache)) {
                    if (n.id == node.id || n.id == excludeId) {
                        continue;
                    }
                    // 洞厅不互相连接，避免两个洞厅之间拉一条超长通道
                    if (node.kind == CaveNode.KIND_MEGA_HALL
                        && n.kind == CaveNode.KIND_MEGA_HALL) {
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
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    int nx = node.cellX + dx;
                    int nz = node.cellZ + dz;
                    if (nx == node.cellX && nz == node.cellZ) {
                        continue;
                    }
                    for (CaveNode n : nodesOf(nx, nz, seed, nodeCache)) {
                        if (n.id == node.id || n.id == excludeId) {
                            continue;
                        }
                        if (node.kind == CaveNode.KIND_MEGA_HALL
                            && n.kind == CaveNode.KIND_MEGA_HALL) {
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

    /** 洞厅连主干网：在 ±4 单元内找最近的 3 个节点开通道。 */
    private static void connectMegaHall(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        List<CaveSegment> out) {
        int superX = Math.floorDiv(
            node.cellX, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        int superZ = Math.floorDiv(
            node.cellZ, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        CaveMegaHall hall = megaHallForSupercell(superX, superZ, seed);
        if (hall == null) {
            return;
        }
        long exclude = -1;
        for (int i = 0; i < 3; i++) {
            CaveNode target = nearestNodeInDirection(
                node, seed, nodeCache, hall.mouthAngle[i], 4, exclude);
            if (target != null) {
                out.add(buildSegment(node, target, seed));
                exclude = target.id;
            }
        }
    }

    /** 在指定方向（60° 锥形）内找最近的节点，让洞厅通道对准预设口。 */
    private static CaveNode nearestNodeInDirection(
        CaveNode node, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        double angle, int radius, long excludeId
    ) {
        double dirX = Math.cos(angle);
        double dirZ = Math.sin(angle);
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : nodesOf(
                    node.cellX + dx, node.cellZ + dz, seed, nodeCache)) {
                    if (n.id == node.id || n.id == excludeId) {
                        continue;
                    }
                    if (n.kind == CaveNode.KIND_MEGA_HALL) {
                        continue;
                    }
                    double vx = n.x - node.x;
                    double vz = n.z - node.z;
                    double len = Math.sqrt(vx * vx + vz * vz);
                    if (len < 1.0e-6) {
                        continue;
                    }
                    double dot = (vx * dirX + vz * dirZ) / len;
                    if (dot < 0.5) {
                        continue;
                    }
                    double d = distSq(node, n);
                    if (d < bestD - 1.0e-9
                        || (Math.abs(d - bestD) <= 1.0e-9
                            && n.id < best.id)) {
                        best = n;
                        bestD = d;
                    }
                }
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
