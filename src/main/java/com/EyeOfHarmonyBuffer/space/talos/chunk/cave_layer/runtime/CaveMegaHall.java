package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 洞厅：宽长上千格的巨型空腔（不可变、确定性生成）。
 *
 * 形状用水平椭圆 + 垂直半高缩放；底部用中频噪声区分干地与湖泊。
 * 巨型石柱在雕刻时整列保留。
 *
 * 内部布局（2025-09 重构）：
 *   - 对柱子做 Delaunay 三角剖分，按面积贪心合并成 K 个房间（环）；
 *   - 墙 = 跨房间边界边 + 凸包外边界边（瘦三角形过滤 + 共线合并）；
 *   - 湖心岛 = 房间 1 内离所有墙最远的点（供外部建筑代码读取）。
 */
public final class CaveMegaHall {

    /** 洞厅形状指数：2=椭球，4=圆角方厅（更接近长方体、不会中间鼓包）。 */
    public static final double SHAPE_P = 4.0;
    /** 水平边界噪声：尺度 / 幅度（半径 ±12%）/ 盐。 */
    private static final double SHAPE_NOISE_SCALE = 140.0;
    private static final double SHAPE_NOISE_AMP = 0.12;
    private static final int SHAPE_NOISE_SALT = 0xC1;
    /** 天花板噪声：尺度 / 幅度（±8 格）/ 盐。 */
    private static final double CEIL_NOISE_SCALE = 42.0;
    private static final double CEIL_NOISE_AMP = 8.0;
    private static final int CEIL_NOISE_SALT = 0xC2;
    /** 洞厅湖水面基准：低处灌水到该高度。 */
    public static final int LAKE_WATER_LEVEL = 15;
    /** 洞厅底部地形噪声：单层大尺度、大幅度，靠幅度自然拔高高区。 */
    private static final double FLOOR_NOISE_SCALE = 140.0;
    private static final double FLOOR_NOISE_AMP = 30.0;
    private static final int FLOOR_NOISE_SALT = 0xC3;
    /** 域扭曲：把噪声坐标揉弯，消除直线切线。 */
    private static final double FLOOR_WARP_AMP = 60.0;
    private static final double FLOOR_WARP_SCALE = 300.0;
    private static final int FLOOR_WARP_SALT = 0xD1;
    /** 平台阈值：噪声超过该值直接切高台（约覆盖 1/4 区域）。 */
    private static final double PLATEAU_THRESHOLD = 0.15;
    /** 平台过渡带宽：阈值前这段做陡坡，而不是硬切。 */
    private static final double PLATEAU_BLEND = 0.12;
    /** 噪声梯度超过该值时保留硬崖，不铺陡坡。 */
    private static final double PLATEAU_HARD_GRADIENT = 0.06;
    private static final int PLATEAU_GRADIENT_STEP = 4;
    /** 高平台基准高度与起伏。 */
    private static final int PLATEAU_BASE = 32;
    private static final double PLATEAU_AMP = 6.0;
    private static final double PLATEAU_SCALE = 600.0;
    private static final int PLATEAU_SALT = 0xC6;
    /** 底部高频风格化噪声：小幅、较缓起伏，只留自然粗糙感。 */
    private static final double FLOOR_DETAIL_SCALE = 32.0;
    private static final double FLOOR_DETAIL_AMP = 1.5;
    private static final int FLOOR_DETAIL_SALT = 0xC7;

    // ---------- 房间分割（Delaunay + 合并） ----------
    /** 目标房间数。 */
    private static final int K_ROOMS = 3;
    /** 瘦三角形最小角阈值（< 15° 视为 sliver，其跨房间边不作墙）。 */
    private static final double SLIVER_ANGLE = 15.0;
    /** 共线墙合并夹角阈值（> 160° 合并成一条直墙）。 */
    private static final double MERGE_ANGLE = 160.0;
    private static final int DELAUNAY_SALT = 0xD8;

    // ---------- 湖心岛（房间 1 内远墙点 + 显式湖环，供外部建筑代码读取） ----------
    /** 岛外圈半径（更大更显眼）。 */
    private static final int ISLAND_RADIUS = 30;
    private static final int ISLAND_FLAT_RADIUS = 6;
    /** 岛顶高度 = 水位 15 + 8，保证高于水面。 */
    private static final int ISLAND_TOP = 23;
    /** 显式湖环：外径（约 60，含过渡带），内圈强制水位 → 岛必然在湖中央。 */
    private static final int ISLAND_LAKE_OUTER = 60;
    /** 湖环实心湖面外径（岛外圈到该半径全灌水）。 */
    private static final int ISLAND_LAKE_INNER = 45;
    /** 湖环过渡带宽度（向周围真实地形 smoothstep 融合）。 */
    private static final int ISLAND_TRANSITION = 15;
    /** 湖底深度（低于水位 LAKE_WATER_LEVEL，保证灌水）。 */
    private static final int ISLAND_LAKE_BED = 11;
    /** 湖心岛中心距墙的安全距离（岛+湖环外径 + 余量）。 */
    private static final int ISLAND_SAFE = 75;
    private static final int ISLAND_SALT = 0xD9;

    // ---------- 门洞（拱门形） ----------
    /** 门洞半宽（沿墙方向，格）：约 3 格，保证玩家可通行。 */
    private static final double DOOR_HALF_W = 3.0;
    /** 门洞边缘过渡带（格）：smoothstep + Perlin，避免硬切。 */
    private static final double DOOR_EDGE = 2.0;
    /** 拱门高度 = 墙高的该比例（圆弧顶部收拢）。 */
    private static final double DOOR_ARCH_FRAC = 0.60;
    private static final int DOOR_SALT = 0xDA;

    public final long seed;
    public final double cx;
    public final double cy;
    public final double cz;
    public final double rx;
    public final double ry;
    public final double rz;

    public final double minX;
    public final double maxX;
    public final double minY;
    public final double maxY;
    public final double minZ;
    public final double maxZ;
    public final int pillarCount;
    public final double[] pillarX;
    public final double[] pillarZ;
    public final int pillarHalf;

    // ================= 房间（Delaunay 三角形合并的环） =================
    /** 房间数（通常 = K_ROOMS）。 */
    public final int roomCount;
    /** 每个房间的多边形顶点（柱子索引，绕房间质心按角度排序）。 */
    public final int[][] roomVerts;
    /** 每个房间的多边形顶点坐标（与 roomVerts 对应）。 */
    public final double[][] roomPolyX;
    public final double[][] roomPolyZ;

    // ================= 墙（柱间厚带，全高到顶，厚度 = pillarHalf/2） =================
    public final int wallCount;
    /** 墙的端点柱子索引。 */
    public final int[] wallA;
    public final int[] wallB;
    /** 墙半厚 = 大柱子半径的一半。 */
    public final int wallHalf;

    // ================= 门洞（墙上缺口，保证房间可通行 / 连外） =================
    public final int doorCount;
    /** 门洞所在的墙索引。 */
    public final int[] doorWall;
    /** 门洞沿墙的起点 / 终点（沿 A→B 距离，单位格）。 */
    public final double[] doorU0;
    public final double[] doorU1;

    // ================= 湖心岛（房间 1 内离所有墙最远的点） =================
    public final double islandX;      // 岛心 x
    public final double islandZ;      // 岛心 z
    public final int islandRadius;    // 岛外圈半径（噪声起伏）
    public final int islandFlatRadius;// 中心平地半径（保证水平可放建筑）
    public final int islandTopY;      // 岛顶高度（高于水位）

    public CaveMegaHall(double cx, double cy, double cz,
                        double rx, double ry, double rz,
                        long seed) {
        this.seed = seed;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.rx = Math.max(1.0, rx);
        this.ry = Math.max(1.0, ry);
        this.rz = Math.max(1.0, rz);
        this.minX = cx - this.rx;
        this.maxX = cx + this.rx;
        this.minY = cy - this.ry;
        this.maxY = cy + this.ry;
        this.minZ = cz - this.rz;
        this.maxZ = cz + this.rz;

        // 基础半径 30~38（直径约 60~76）。
        this.pillarHalf = 30 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x91) * 9.0);
        int n = 12 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x92) * 5.0);
        this.pillarCount = n;
        this.pillarX = new double[n];
        this.pillarZ = new double[n];
        double minDist = pillarHalf * 3.0;
        double minDistSq = minDist * minDist;
        int placed = 0;
        for (int i = 0; i < n; i++) {
            double dx;
            double dz;
            boolean ok = false;
            for (int attempt = 0; attempt < 24 && !ok; attempt++) {
                dx = (CaveMath.hash01(
                    (long) cx, (long) cy, i * 2 + attempt,
                    seed, 0x93) - 0.5) * 2.0 * rx * 0.65;
                dz = (CaveMath.hash01(
                    (long) cx, (long) cz, i * 2 + attempt,
                    seed, 0x94) - 0.5) * 2.0 * rz * 0.65;
                if (dx * dx / (rx * rx) + dz * dz / (rz * rz) < 0.81) {
                    double px = cx + dx;
                    double pz = cz + dz;
                    boolean far = true;
                    for (int j = 0; j < placed; j++) {
                        double ddx = px - pillarX[j];
                        double ddz = pz - pillarZ[j];
                        if (ddx * ddx + ddz * ddz < minDistSq) {
                            far = false;
                            break;
                        }
                    }
                    if (far) {
                        this.pillarX[i] = px;
                        this.pillarZ[i] = pz;
                        placed++;
                        ok = true;
                    }
                }
            }
            if (!ok) {
                double a = i * (2.0 * Math.PI / n)
                    + CaveMath.hash01(
                        (long) cx, (long) cz, i, seed, 0x95) * 0.5;
                this.pillarX[i] = cx + Math.cos(a) * rx * 0.55;
                this.pillarZ[i] = cz + Math.sin(a) * rz * 0.55;
                placed++;
            }
        }

        // ================= Delaunay 剖分 + 房间合并 + 墙提取 =================
        int[][] triangles = delaunay(pillarX, pillarZ, n);
        int[][] regions = mergeRooms(pillarX, pillarZ, triangles, K_ROOMS);
        this.roomCount = regions.length;
        this.roomVerts = new int[roomCount][];
        this.roomPolyX = new double[roomCount][];
        this.roomPolyZ = new double[roomCount][];
        for (int ri = 0; ri < roomCount; ri++) {
            Set<Integer> verts = new HashSet<Integer>();
            for (int t : regions[ri]) {
                verts.add(triangles[t][0]);
                verts.add(triangles[t][1]);
                verts.add(triangles[t][2]);
            }
            double mx = 0.0, mz = 0.0;
            for (int v : verts) {
                mx += pillarX[v];
                mz += pillarZ[v];
            }
            mx /= verts.size();
            mz /= verts.size();
            List<Integer> sorted = new ArrayList<Integer>(verts);
            final double fcx = mx, fcz = mz;
            java.util.Collections.sort(sorted, (u, v) ->
                Double.compare(
                    Math.atan2(pillarZ[u] - fcz, pillarX[u] - fcx),
                    Math.atan2(pillarZ[v] - fcz, pillarX[v] - fcx)));
            roomVerts[ri] = new int[sorted.size()];
            roomPolyX[ri] = new double[sorted.size()];
            roomPolyZ[ri] = new double[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                int v = sorted.get(i);
                roomVerts[ri][i] = v;
                roomPolyX[ri][i] = pillarX[v];
                roomPolyZ[ri][i] = pillarZ[v];
            }
        }

        // 墙提取：跨房间边 + 凸包外边界边，sliver 过滤 + 共线合并
        List<int[]> walls = extractWalls(pillarX, pillarZ, triangles,
            regions, edgeToTriangles(triangles, n));
        this.wallHalf = Math.max(4, pillarHalf / 2);
        this.wallCount = walls.size();
        this.wallA = new int[wallCount];
        this.wallB = new int[wallCount];
        for (int i = 0; i < wallCount; i++) {
            wallA[i] = walls.get(i)[0];
            wallB[i] = walls.get(i)[1];
        }

        // ================= 门洞生成 =================
        // 需求：每个房间保底可达（房邻接生成树上的墙各开一个门），
        //       且至少 1 个凸包外边界墙开门（洞厅连外部网络 / 大厅开放区）。
        // 门洞沿墙 u 居中，避开柱子端点，边缘用 Perlin + smoothstep 渐变。
        java.util.List<Integer> doorWallL = new java.util.ArrayList<Integer>();
        java.util.List<Double> doorU0L = new java.util.ArrayList<Double>();
        java.util.List<Double> doorU1L = new java.util.ArrayList<Double>();
        // 每个房间的边界墙：墙的两端点都在该房间 roomVerts 里
        List<List<Integer>> roomWalls = new ArrayList<List<Integer>>();
        for (int ri = 0; ri < roomCount; ri++) {
            List<Integer> rw = new ArrayList<Integer>();
            for (int w = 0; w < wallCount; w++) {
                if (roomContainsPillar(ri, wallA[w])
                    && roomContainsPillar(ri, wallB[w])) {
                    rw.add(w);
                }
            }
            roomWalls.add(rw);
        }
        // 房邻接生成树（BFS），树边墙开门 → 所有房间互相可达
        boolean[] roomInTree = new boolean[roomCount];
        boolean[] wallHasDoor = new boolean[wallCount];
        java.util.List<Integer> tree = new java.util.ArrayList<Integer>();
        roomInTree[0] = true;
        tree.add(0);
        for (int idx = 0; idx < tree.size(); idx++) {
            int ri = tree.get(idx);
            for (int w : roomWalls.get(ri)) {
                for (int rj = 0; rj < roomCount; rj++) {
                    if (rj != ri && !roomInTree[rj]
                        && roomWalls.get(rj).contains(w)) {
                        roomInTree[rj] = true;
                        tree.add(rj);
                        if (!wallHasDoor[w]) {
                            addDoorToList(w, doorWallL, doorU0L, doorU1L, seed);
                            wallHasDoor[w] = true;
                        }
                    }
                }
            }
        }
        // 外层出口：至少一个凸包外边界墙（只属于 1 个房间）开门
        boolean outerDone = false;
        for (int w = 0; w < wallCount && !outerDone; w++) {
            int cnt = 0;
            for (int ri = 0; ri < roomCount; ri++) {
                if (roomWalls.get(ri).contains(w)) {
                    cnt++;
                }
            }
            if (cnt == 1 && !wallHasDoor[w]) {
                addDoorToList(w, doorWallL, doorU0L, doorU1L, seed);
                wallHasDoor[w] = true;
                outerDone = true;
            }
        }
        // 兜底：如果某些房间没有门（理论上不应发生），给其第一条边界墙补门
        for (int ri = 0; ri < roomCount; ri++) {
            boolean has = false;
            for (int w : roomWalls.get(ri)) {
                if (wallHasDoor[w]) {
                    has = true;
                    break;
                }
            }
            if (!has && !roomWalls.get(ri).isEmpty()) {
                int w = roomWalls.get(ri).get(0);
                if (!wallHasDoor[w]) {
                    addDoorToList(w, doorWallL, doorU0L, doorU1L, seed);
                    wallHasDoor[w] = true;
                }
            }
        }
        this.doorCount = doorWallL.size();
        this.doorWall = new int[doorCount];
        this.doorU0 = new double[doorCount];
        this.doorU1 = new double[doorCount];
        for (int d = 0; d < doorCount; d++) {
            doorWall[d] = doorWallL.get(d);
            doorU0[d] = doorU0L.get(d);
            doorU1[d] = doorU1L.get(d);
        }

        // ================= 湖心岛（A+B 选址） =================
        this.islandRadius = ISLAND_RADIUS;
        this.islandFlatRadius = ISLAND_FLAT_RADIUS;
        this.islandTopY = ISLAND_TOP;
        double[] far = pickIslandCenter();
        this.islandX = far[0];
        this.islandZ = far[1];
    }

    // ================= Delaunay（空圆法 O(n^4)，n≈14 极快） =================
    private static int[][] delaunay(double[] xs, double[] zs, int n) {
        List<int[]> tris = new ArrayList<int[]>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    if (isDelaunay(xs, zs, i, j, k, n)) {
                        tris.add(new int[] {i, j, k});
                    }
                }
            }
        }
        return tris.toArray(new int[0][]);
    }

    private static boolean isDelaunay(double[] xs, double[] zs,
                                      int i, int j, int k, int n) {
        double ax = xs[i], ay = zs[i];
        double bx = xs[j], by = zs[j];
        double cxx = xs[k], cyy = zs[k];
        double d = 2 * (ax * (by - cyy) + bx * (cyy - ay) + cxx * (ay - by));
        if (Math.abs(d) < 1e-9) {
            return false; // 共线
        }
        double ux = ((ax * ax + ay * ay) * (by - cyy)
            + (bx * bx + by * by) * (cyy - ay)
            + (cxx * cxx + cyy * cyy) * (ay - by)) / d;
        double uy = ((ax * ax + ay * ay) * (cxx - bx)
            + (bx * bx + by * by) * (ax - cxx)
            + (cxx * cxx + cyy * cyy) * (bx - ax)) / d;
        double r2 = (ax - ux) * (ax - ux) + (ay - uy) * (ay - uy);
        for (int m = 0; m < n; m++) {
            if (m == i || m == j || m == k) {
                continue;
            }
            double dd = (xs[m] - ux) * (xs[m] - ux) + (zs[m] - uy) * (zs[m] - uy);
            if (dd < r2 - 1e-9) {
                return false;
            }
        }
        return true;
    }

    // ================= 边 -> 三角形邻接 =================
    private static Map<Long, List<Integer>> edgeToTriangles(int[][] tris, int n) {
        Map<Long, List<Integer>> map = new HashMap<Long, List<Integer>>();
        for (int ti = 0; ti < tris.length; ti++) {
            int[] t = tris[ti];
            addEdge(map, t[0], t[1], ti);
            addEdge(map, t[1], t[2], ti);
            addEdge(map, t[0], t[2], ti);
        }
        return map;
    }

    private static void addEdge(Map<Long, List<Integer>> map,
                                int a, int b, int ti) {
        long key = edgeKey(a, b);
        List<Integer> l = map.get(key);
        if (l == null) {
            l = new ArrayList<Integer>(2);
            map.put(key, l);
        }
        l.add(ti);
    }

    private static long edgeKey(int a, int b) {
        if (a < b) {
            return (((long) a) << 32) ^ (b & 0xffffffffL);
        }
        return (((long) b) << 32) ^ (a & 0xffffffffL);
    }

    private static double triArea(double[] xs, double[] zs, int[] t) {
        double ax = xs[t[0]], ay = zs[t[0]];
        double bx = xs[t[1]], by = zs[t[1]];
        double cxx = xs[t[2]], cyy = zs[t[2]];
        return Math.abs((bx - ax) * (cyy - ay) - (by - ay) * (cxx - ax)) / 2.0;
    }

    private static double triCentroidX(double[] xs, int[] t) {
        return (xs[t[0]] + xs[t[1]] + xs[t[2]]) / 3.0;
    }

    private static double triCentroidZ(double[] zs, int[] t) {
        return (zs[t[0]] + zs[t[1]] + zs[t[2]]) / 3.0;
    }

    private static double minAngleOfTri(double[] xs, double[] zs, int[] t) {
        double a = angleAt(xs, zs, t[0], t[1], t[2]);
        double b = angleAt(xs, zs, t[1], t[0], t[2]);
        double c = angleAt(xs, zs, t[0], t[2], t[1]);
        return Math.min(a, Math.min(b, c));
    }

    private static double angleAt(double[] xs, double[] zs,
                                  int v1, int shared, int v2) {
        double ax = xs[v1] - xs[shared], ay = zs[v1] - zs[shared];
        double bx = xs[v2] - xs[shared], by = zs[v2] - zs[shared];
        double l1 = Math.hypot(ax, ay);
        double l2 = Math.hypot(bx, by);
        if (l1 < 1e-9 || l2 < 1e-9) {
            return 180.0;
        }
        double cosv = (ax * bx + ay * by) / (l1 * l2);
        if (cosv > 1.0) cosv = 1.0;
        if (cosv < -1.0) cosv = -1.0;
        return Math.toDegrees(Math.acos(cosv));
    }

    // ================= 三角形按面积 BFS 贪心合并成房间 =================
    private static int[][] mergeRooms(double[] xs, double[] zs,
                                      int[][] tris, int k) {
        int n = tris.length;
        double[] area = new double[n];
        double total = 0.0;
        for (int i = 0; i < n; i++) {
            area[i] = triArea(xs, zs, tris[i]);
            total += area[i];
        }
        double target = total / k;
        Map<Long, List<Integer>> edgeTri = edgeToTriangles(tris, n);
        // 邻接表
        Map<Integer, List<Integer>> adj = new HashMap<Integer, List<Integer>>();
        for (int i = 0; i < n; i++) {
            adj.put(i, new ArrayList<Integer>());
        }
        for (Map.Entry<Long, List<Integer>> e : edgeTri.entrySet()) {
            List<Integer> l = e.getValue();
            if (l.size() == 2) {
                adj.get(l.get(0)).add(l.get(1));
                adj.get(l.get(1)).add(l.get(0));
            }
        }
        Set<Integer> unassigned = new HashSet<Integer>();
        for (int i = 0; i < n; i++) {
            unassigned.add(i);
        }
        List<List<Integer>> regions = new ArrayList<List<Integer>>();
        for (int r = 0; r < k; r++) {
            if (unassigned.isEmpty()) {
                break;
            }
            // 种子 = 未分配中面积最大的三角形
            int seed = -1;
            double bestA = -1.0;
            for (int i : unassigned) {
                if (area[i] > bestA) {
                    bestA = area[i];
                    seed = i;
                }
            }
            List<Integer> comp = new ArrayList<Integer>();
            comp.add(seed);
            double acc = area[seed];
            List<Integer> frontier = new ArrayList<Integer>();
            frontier.add(seed);
            Set<Integer> visited = new HashSet<Integer>();
            visited.add(seed);
            int fi = 0;
            while (fi < frontier.size() && acc < target) {
                int cur = frontier.get(fi++);
                for (int nb : adj.get(cur)) {
                    if (unassigned.contains(nb) && !visited.contains(nb)) {
                        visited.add(nb);
                        frontier.add(nb);
                        comp.add(nb);
                        acc += area[nb];
                        if (acc >= target) {
                            break;
                        }
                    }
                }
            }
            regions.add(comp);
            unassigned.removeAll(comp);
        }
        // 剩余并入质心最近区域
        while (!unassigned.isEmpty()) {
            int bestT = -1, bestR = -1;
            double bestD = Double.POSITIVE_INFINITY;
            for (int t : unassigned) {
                double tcX = triCentroidX(xs, tris[t]);
                double tcZ = triCentroidZ(zs, tris[t]);
                for (int ri = 0; ri < regions.size(); ri++) {
                    for (int u : regions.get(ri)) {
                        double ux = triCentroidX(xs, tris[u]);
                        double uz = triCentroidZ(zs, tris[u]);
                        double dd = (tcX - ux) * (tcX - ux) + (tcZ - uz) * (tcZ - uz);
                        if (dd < bestD) {
                            bestD = dd;
                            bestT = t;
                            bestR = ri;
                        }
                    }
                }
            }
            if (bestT < 0) {
                break;
            }
            regions.get(bestR).add(bestT);
            unassigned.remove(bestT);
        }
        int[][] out = new int[regions.size()][];
        for (int i = 0; i < regions.size(); i++) {
            out[i] = new int[regions.get(i).size()];
            for (int j = 0; j < regions.get(i).size(); j++) {
                out[i][j] = regions.get(i).get(j);
            }
        }
        return out;
    }

    // ================= 墙提取（sliver 过滤 + 共线合并） =================
    private static List<int[]> extractWalls(double[] xs, double[] zs,
                                            int[][] tris, int[][] regions,
                                            Map<Long, List<Integer>> edgeTri) {
        int[] regionOf = new int[tris.length];
        for (int ri = 0; ri < regions.length; ri++) {
            for (int t : regions[ri]) {
                regionOf[t] = ri;
            }
        }
        Set<Integer> sliver = new HashSet<Integer>();
        for (int i = 0; i < tris.length; i++) {
            if (minAngleOfTri(xs, zs, tris[i]) < SLIVER_ANGLE) {
                sliver.add(i);
            }
        }
        List<int[]> walls = new ArrayList<int[]>();
        for (Map.Entry<Long, List<Integer>> e : edgeTri.entrySet()) {
            List<Integer> l = e.getValue();
            if (l.size() == 1) {
                // 凸包外边界边
                walls.add(edgeEnds(e.getKey()));
            } else if (l.size() == 2
                && regionOf[l.get(0)] != regionOf[l.get(1)]
                && !sliver.contains(l.get(0))
                && !sliver.contains(l.get(1))) {
                // 跨房间边，瘦三角形跳过
                walls.add(edgeEnds(e.getKey()));
            }
        }
        // 共线合并：共享顶点夹角 > MERGE_ANGLE 的墙对，并入较长的一条
        return mergeCollinearWalls(walls, xs, zs);
    }

    private static int[] edgeEnds(long key) {
        int a = (int) (key >>> 32);
        int b = (int) (key & 0xffffffffL);
        return new int[] {a, b};
    }

    private static List<int[]> mergeCollinearWalls(List<int[]> walls,
                                                   double[] xs, double[] zs) {
        List<int[]> result = new ArrayList<int[]>(walls);
        boolean merged = true;
        int iters = 0;
        while (merged && iters < 50) {
            merged = false;
            iters++;
            // 顶点 -> 墙列表
            Map<Integer, List<int[]>> byVertex =
                new HashMap<Integer, List<int[]>>();
            for (int[] w : result) {
                putEdge(byVertex, w[0], w);
                putEdge(byVertex, w[1], w);
            }
            List<Object[]> pairs = new ArrayList<Object[]>();
            for (Map.Entry<Integer, List<int[]>> e : byVertex.entrySet()) {
                int v = e.getKey();
                List<int[]> es = e.getValue();
                for (int i = 0; i < es.size(); i++) {
                    for (int j = i + 1; j < es.size(); j++) {
                        int[] e1 = es.get(i);
                        int[] e2 = es.get(j);
                        int a1 = e1[0] == v ? e1[1] : e1[0];
                        int a2 = e2[0] == v ? e2[1] : e2[0];
                        double ang = angleAt(xs, zs, a1, v, a2);
                        if (ang > MERGE_ANGLE) {
                            pairs.add(new Object[] {e1, e2, v});
                        }
                    }
                }
            }
            for (Object[] p : pairs) {
                int[] e1 = (int[]) p[0];
                int[] e2 = (int[]) p[1];
                int v = (Integer) p[2];
                if (!result.contains(e1) || !result.contains(e2)) {
                    continue;
                }
                int a = e1[0] == v ? e1[1] : e1[0];
                int b = e2[0] == v ? e2[1] : e2[0];
                result.remove(e1);
                result.remove(e2);
                if (a != b) {
                    boolean dup = false;
                    for (int[] w : result) {
                        if ((w[0] == a && w[1] == b) || (w[0] == b && w[1] == a)) {
                            dup = true;
                            break;
                        }
                    }
                    if (!dup) {
                        result.add(new int[] {a, b});
                    }
                }
                merged = true;
            }
        }
        return result;
    }

    private static void putEdge(Map<Integer, List<int[]>> byVertex,
                                int v, int[] w) {
        List<int[]> l = byVertex.get(v);
        if (l == null) {
            l = new ArrayList<int[]>();
            byVertex.put(v, l);
        }
        l.add(w);
    }


    // ================= 门洞辅助 =================

    /** 房间 ri 的边界是否包含柱子 p。 */
    private boolean roomContainsPillar(int ri, int p) {
        for (int v : roomVerts[ri]) {
            if (v == p) {
                return true;
            }
        }
        return false;
    }

    /** 在墙 w 上开一个门洞（沿墙居中，避开柱子端点，宽度 DOOR_HALF_W*2 + 过渡带）。 */
    private void addDoorToList(int w,
                               java.util.List<Integer> wallL,
                               java.util.List<Double> u0L,
                               java.util.List<Double> u1L,
                               long seed) {
        double ax = pillarX[wallA[w]], az = pillarZ[wallA[w]];
        double bx = pillarX[wallB[w]], bz = pillarZ[wallB[w]];
        double len = Math.hypot(bx - ax, bz - az);
        if (len < DOOR_HALF_W * 2.0 + 1.0) {
            return; // 墙太短，不开口
        }
        // 门洞中心：沿墙 40%~60% 处（确定性），避开柱子端点
        double t = 0.40 + CaveMath.hash01(w, 0, 0, seed, DOOR_SALT) * 0.20;
        double centerU = t * len;
        double half = DOOR_HALF_W + DOOR_EDGE; // 全宽（含渐变带）
        double u0 = centerU - half;
        double u1 = centerU + half;
        if (u0 < pillarHalf * 0.5) {
            u0 = pillarHalf * 0.5;
        }
        if (u1 > len - pillarHalf * 0.5) {
            u1 = len - pillarHalf * 0.5;
        }
        if (u1 - u0 < DOOR_HALF_W * 2.0) {
            return;
        }
        wallL.add(w);
        u0L.add(u0);
        u1L.add(u1);
    }

    /**
     * 墙在 (u, y) 处的门洞开度因子：0=全开（拱门内），1=全实心。
     *
     * 拱门形（方案 A）：在「沿墙 u × 高度 y」平面上，门洞是半椭圆轮廓——
     *   地面（y=floorY）全开、顶部（y=floorY+拱高）圆弧收拢到尖顶；
     *   边缘用 smoothstep(过渡带) + Perlin 抖动，不硬切、不"抽掉一块"。
     *
     * @return [0,1]
     */
    public double wallDoorFactor(int wallIdx, double u,
                                 int y, int floorY, int yMax, long seed) {
        for (int d = 0; d < doorCount; d++) {
            if (doorWall[d] != wallIdx) {
                continue;
            }
            double u0 = doorU0[d];
            double u1 = doorU1[d];
            double half = Math.max(1.0, (u1 - u0) / 2.0); // 门洞沿墙半宽
            double center = (u0 + u1) / 2.0;
            // 拱高：墙高的 DOOR_ARCH_FRAC 比例，至少 4 格
            double archH = Math.max(4.0,
                (yMax - floorY) * DOOR_ARCH_FRAC);
            double du = (u - center) / half;
            double dy = (double) (y - floorY) / archH;
            if (dy > 1.0) {
                return 1.0; // 高于拱顶：全实心
            }
            // 半椭圆：在该高度允许的最大沿墙归一化距离 r = sqrt(1 - dy^2)
            double r = Math.sqrt(Math.max(0.0, 1.0 - dy * dy));
            // 过渡带宽度（沿墙归一化）
            double band = DOOR_EDGE / half;
            double edge = (Math.abs(du) - r) / band;
            if (edge <= -1.0) {
                return 0.0; // 拱门内部：全开
            }
            if (edge >= 1.0) {
                return 1.0; // 拱门外部：全实心
            }
            // Perlin 抖动让拱门边缘不规则
            double n = (CaveMath.perlin3D(
                u / 12.0, y / 8.0, wallIdx * 0.11, seed,
                DOOR_SALT + 2) - 0.5) * 2.0;
            double t = (edge + 1.0) / 2.0 + n * 0.35;
            t = Math.max(0.0, Math.min(1.0, t));
            double s = t * t * (3.0 - 2.0 * t); // smoothstep
            return s;
        }
        return 1.0;
    }

    // ================= 湖心岛选址（A+B）：离墙远 + 周围低洼 =================
    /**
     * 在房间 1 多边形内选湖心岛中心：
     *   - 候选点必须离所有墙足够远（ISLAND_SAFE，保证岛+湖环不嵌进墙）；
     *   - 打分 = 周围若干格 floorY 均值（越低越好，选天然低洼=天然湖盆，
     *     湖环挖下去深度浅、过渡带融合自然）。
     */
    private double[] pickIslandCenter() {
        double[] polyX = roomPolyX[0];
        double[] polyZ = roomPolyZ[0];
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE;
        double z0 = Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
        for (int i = 0; i < polyX.length; i++) {
            x0 = Math.min(x0, polyX[i]);
            x1 = Math.max(x1, polyX[i]);
            z0 = Math.min(z0, polyZ[i]);
            z1 = Math.max(z1, polyZ[i]);
        }
        double step = 24.0;
        int nx = Math.max(2, (int) ((x1 - x0) / step));
        int nz = Math.max(2, (int) ((z1 - z0) / step));
        double bestX = 0, bestZ = 0;
        double bestScore = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (int i = 0; i <= nx; i++) {
            for (int j = 0; j <= nz; j++) {
                double px = x0 + (x1 - x0) * i / nx;
                double pz = z0 + (z1 - z0) * j / nz;
                if (!pointInPoly(px, pz, polyX, polyZ)) {
                    continue;
                }
                // 离所有墙的距离
                double dmin = Double.MAX_VALUE;
                for (int w = 0; w < wallCount; w++) {
                    dmin = Math.min(dmin, ptSegDist(px, pz,
                        pillarX[wallA[w]], pillarZ[wallA[w]],
                        pillarX[wallB[w]], pillarZ[wallB[w]]));
                }
                // 岛+湖环外径不能嵌进墙
                if (dmin < ISLAND_SAFE) {
                    continue;
                }
                // 周围 floorY 均值（选天然低洼 → 湖盆）
                double avg = 0.0;
                int nv = 0;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        avg += floorY((int) (px + dx * 20.0),
                                      (int) (pz + dz * 20.0));
                        nv++;
                    }
                }
                if (nv > 0) {
                    avg /= nv;
                }
                // 分数：低洼优先（低 floorY 更好）；离墙远做次权
                double score = avg - dmin * 0.02;
                if (score < bestScore) {
                    bestScore = score;
                    bestX = px;
                    bestZ = pz;
                    found = true;
                }
            }
        }
        if (!found) {
            // 兜底：房间质心
            double mx = 0, mz = 0;
            for (int i = 0; i < polyX.length; i++) {
                mx += polyX[i];
                mz += polyZ[i];
            }
            bestX = mx / polyX.length;
            bestZ = mz / polyX.length;
        }
        return new double[] {bestX, bestZ};
    }

    private static boolean pointInPoly(double x, double z,
                                       double[] px, double[] pz) {
        boolean inside = false;
        int n = px.length;
        int j = n - 1;
        for (int i = 0; i < n; i++) {
            if (((pz[i] > z) != (pz[j] > z))
                && (x < (px[j] - px[i]) * (z - pz[i]) / (pz[j] - pz[i]) + px[i])) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }

    private static double ptSegDist(double px, double pz,
                                    double ax, double az,
                                    double bx, double bz) {
        double vx = bx - ax, vz = bz - az;
        double l2 = vx * vx + vz * vz;
        if (l2 < 1e-9) {
            return Math.hypot(px - ax, pz - az);
        }
        double t = Math.max(0.0, Math.min(1.0,
            ((px - ax) * vx + (pz - az) * vz) / l2));
        return Math.hypot(px - (ax + t * vx), pz - (az + t * vz));
    }

    // ================= 查询 =================

    /** 水平投影是否在该洞厅内。 */
    public boolean insideHorizontal(double wx, double wz) {
        return noisyShapeH((int) Math.floor(wx), (int) Math.floor(wz)) < 1.0;
    }

    /** 是否在洞厅水平范围附近（含 margin 格的外扩带）。 */
    public boolean nearHorizontal(double wx, double wz, double margin) {
        int ix = (int) Math.floor(wx);
        int iz = (int) Math.floor(wz);
        double fx = shapeFactorX(ix, iz);
        double fz = shapeFactorZ(ix, iz);
        double ex = rx * fx + margin;
        double ez = rz * fz + margin;
        double dx = (wx - cx) / ex;
        double dz = (wz - cz) / ez;
        return shapeH(dx, dz) < 1.0;
    }

    /** 该列是否属于巨型石柱（整列不挖）。 */
    public boolean isPillarColumn(int wx, int wz) {
        return pillarIndex(wx, wz) >= 0;
    }

    /** 返回该列所属石柱的索引；不在任何柱内返回 -1。 */
    public int pillarIndex(int wx, int wz) {
        double reach = pillarHalf * 2.2;
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < pillarCount; i++) {
            double dx = wx + 0.5 - pillarX[i];
            double dz = wz + 0.5 - pillarZ[i];
            double d = dx * dx + dz * dz;
            if (d <= reach * reach && d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * 该列是否落在某条墙带内（柱间厚带，全高到顶）。返回墙索引，-1 无。
     * 判定半径取 wallHalf * 2：墙雕刻的半径含噪声（×1.34）与顶/脚外扩
     * （×1.5），最大约 wallHalf × 2——判定必须覆盖最大雕刻半径，
     * 否则墙边 / 墙顶脚超出的列进不了墙分支而被挖掉。
     */
    public int wallIndex(int wx, int wz) {
        double reach = wallHalf * 2.0;
        double reachSq = reach * reach;
        for (int i = 0; i < wallCount; i++) {
            double d = ptSegDist(wx + 0.5, wz + 0.5,
                pillarX[wallA[i]], pillarZ[wallA[i]],
                pillarX[wallB[i]], pillarZ[wallB[i]]);
            if (d <= reach) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 计算该列在洞厅内的整数 Y 范围并写入 out[0..1]。
     * @return 是否有非空范围
     */
    public boolean verticalSpan(int wx, int wz, int maxY, int[] out) {
        double h = noisyShapeH(wx, wz);
        if (h >= 1.0) {
            return false;
        }
        double ryLocal = ry * Math.pow(
            Math.max(0.0, 1.0 - h), 1.0 / SHAPE_P);
        double a = cy - ryLocal;
        double b = cy + ryLocal;
        double ceilNoise = CEIL_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / CEIL_NOISE_SCALE, 0.1, wz / CEIL_NOISE_SCALE,
                seed, CEIL_NOISE_SALT
            ));
        b += ceilNoise;
        int yMin = (int) Math.floor(a - 0.5) + 1;
        int yMax = (int) Math.ceil(b - 0.5) - 1;
        if (yMin < 1) {
            yMin = 1;
        }
        if (yMax > maxY) {
            yMax = maxY;
        }
        if (yMin > yMax) {
            return false;
        }
        out[0] = yMin;
        out[1] = yMax;
        return true;
    }

    /**
     * 洞厅该列的地面高度（不含石柱、不做顶部钳制）。
     * 雕刻器用它生成底部地形；暗河接入点也用它在洞厅内找水下位置。
     */
    public int floorY(int worldX, int worldZ) {
        double wx2 = worldX + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.4, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT);
        double wz2 = worldZ + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.5, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT + 1);
        double n = CaveMath.fbm3D(
            wx2 / FLOOR_NOISE_SCALE, 0.1, wz2 / FLOOR_NOISE_SCALE,
            seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
        int offset = (int) Math.round(n * FLOOR_NOISE_AMP);
        int lowY = LAKE_WATER_LEVEL + offset;
        int floorY = lowY;
        if (n >= PLATEAU_THRESHOLD - PLATEAU_BLEND) {
            double pn = CaveMath.perlin3D(
                worldX / PLATEAU_SCALE, 0.2, worldZ / PLATEAU_SCALE,
                seed, PLATEAU_SALT) * 2.0;
            int plateauY = PLATEAU_BASE + (int) Math.round(pn * PLATEAU_AMP);
            if (n >= PLATEAU_THRESHOLD) {
                floorY = plateauY;
            } else {
                double nRight = CaveMath.fbm3D(
                    (wx2 + PLATEAU_GRADIENT_STEP) / FLOOR_NOISE_SCALE,
                    0.1, wz2 / FLOOR_NOISE_SCALE,
                    seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
                if (Math.abs(nRight - n) > PLATEAU_HARD_GRADIENT) {
                    floorY = lowY;
                } else {
                    double t = (n - (PLATEAU_THRESHOLD - PLATEAU_BLEND))
                        / PLATEAU_BLEND;
                    double s = t * t * (3.0 - 2.0 * t);
                    floorY = (int) Math.round(lowY + (plateauY - lowY) * s);
                }
            }
        }
        double dn = CaveMath.perlin3D(
            worldX / FLOOR_DETAIL_SCALE, 0.3, worldZ / FLOOR_DETAIL_SCALE,
            seed, FLOOR_DETAIL_SALT) * 2.0;
        floorY += (int) Math.round(dn * FLOOR_DETAIL_AMP);
        if (floorY < 2) {
            floorY = 2;
        }
        return floorY;
    }

    // ============================================================
    // 湖心岛接口（外部建筑代码用）
    // ============================================================

    /** 湖心岛：是否落在岛区（噪声起伏的外圈）。外部建筑代码用。 */
    public boolean isOnIsland(double wx, double wz) {
        double dx = wx + 0.5 - islandX;
        double dz = wz + 0.5 - islandZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        double n = CaveMath.perlin3D(
            wx / 22.0, 0.3, wz / 22.0, seed, ISLAND_SALT + 1);
        double eff = islandRadius * (0.85 + 0.35 * n);
        return d <= eff;
    }

    /** 湖心岛中心平地：保证水平、可放建筑。 */
    public boolean isIslandFlat(double wx, double wz) {
        double dx = wx + 0.5 - islandX;
        double dz = wz + 0.5 - islandZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d <= islandFlatRadius;
    }

    /** 湖心岛岛顶高度（平地基准，外部建筑代码用）。 */
    public int islandTop() {
        return islandTopY;
    }

    /**
     * 湖心岛该列的地板高度（覆盖整个显式湖环）：
     *   - 中心平地 = islandTopY（建筑用地）；
     *   - 岛体（d ≤ islandRadius）缓坡下降到湖床；
     *   - 实心湖面（d ≤ ISLAND_LAKE_INNER）强制湖床（低于水位 → 灌水）；
     *   - 过渡带（到 ISLAND_LAKE_OUTER）向周围真实 floorY smoothstep 融合。
     * 湖环外不在此方法处理（调用方只在 isInIslandLake 内调用）。
     */
    public int islandFloorY(double wx, double wz) {
        double dx = wx + 0.5 - islandX;
        double dz = wz + 0.5 - islandZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        double n = CaveMath.perlin3D(
            wx / 22.0, 0.3, wz / 22.0, seed, ISLAND_SALT + 1);
        double eff = islandRadius * (0.85 + 0.35 * n); // 岛体外圈（噪声起伏）
        double bed = ISLAND_LAKE_BED;                  // 湖床约 11
        double floor;
        if (isIslandFlat(wx, wz)) {
            return islandTopY;
        }
        if (d <= eff) {
            // 岛体：岛顶 → 湖床 缓坡（smoothstep 圆滑）
            double t = (d - islandFlatRadius)
                / Math.max(1.0, eff - islandFlatRadius);
            t = Math.max(0.0, Math.min(1.0, t));
            double s = t * t * (3.0 - 2.0 * t);
            floor = islandTopY + (bed - islandTopY) * s;
        } else if (d <= ISLAND_LAKE_INNER) {
            // 实心湖面：强制湖床（低于水位 → 灌水）
            floor = bed;
        } else {
            // 过渡带：湖床 → 周围真实地形 平滑融合
            double tt = (d - ISLAND_LAKE_INNER)
                / Math.max(1.0, ISLAND_LAKE_OUTER - ISLAND_LAKE_INNER);
            tt = Math.max(0.0, Math.min(1.0, tt));
            double s = tt * tt * (3.0 - 2.0 * tt);
            double outer = floorY((int) wx, (int) wz);
            floor = bed + (outer - bed) * s;
        }
        // 高频起伏让海岸线自然
        double dn = CaveMath.perlin3D(
            wx / 18.0, 0.3, wz / 18.0, seed, ISLAND_SALT + 7) * 2.0;
        floor += dn;
        if (floor < 2.0) {
            floor = 2.0;
        }
        return (int) Math.round(floor);
    }

    /** 是否落在显式湖环内（岛体 + 实心湖面 + 过渡带）。 */
    public boolean isInIslandLake(double wx, double wz) {
        double dx = wx + 0.5 - islandX;
        double dz = wz + 0.5 - islandZ;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d <= ISLAND_LAKE_OUTER;
    }

    /** 湖心岛中心 X（外部建筑代码用）。 */
    public double islandCenterX() {
        return islandX;
    }

    /** 湖心岛中心 Z（外部建筑代码用）。 */
    public double islandCenterZ() {
        return islandZ;
    }

    /** 湖心岛外圈半径（外部建筑代码用）。 */
    public int islandRadius() {
        return islandRadius;
    }

    /** 湖心岛平地半径（外部建筑代码用）。 */
    public int islandFlatRadius() {
        return islandFlatRadius;
    }

    // ============================================================

    /** 带噪声扰动的水平形状值（<1 表示在该洞厅内）。 */
    private double noisyShapeH(int wx, int wz) {
        double fx = shapeFactorX(wx, wz);
        double fz = shapeFactorZ(wx, wz);
        double dx = (wx + 0.5 - cx) / (rx * fx);
        double dz = (wz + 0.5 - cz) / (rz * fz);
        return shapeH(dx, dz);
    }

    private double shapeFactorX(int wx, int wz) {
        return 1.0 + SHAPE_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / SHAPE_NOISE_SCALE, 0.05, wz / SHAPE_NOISE_SCALE,
                seed, SHAPE_NOISE_SALT
            ));
    }

    private double shapeFactorZ(int wx, int wz) {
        return 1.0 + SHAPE_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / SHAPE_NOISE_SCALE, 0.07, wz / SHAPE_NOISE_SCALE,
                seed, SHAPE_NOISE_SALT + 1
            ));
    }

    private static double shapeH(double dx, double dz) {
        return Math.pow(Math.abs(dx), SHAPE_P)
            + Math.pow(Math.abs(dz), SHAPE_P);
    }

    /** 是否与某个区块相交（粗略水平包围盒判断）。 */
    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;
        return maxX >= x0 && minX <= x0 + 16
            && maxZ >= z0 && minZ <= z0 + 16;
    }
}
