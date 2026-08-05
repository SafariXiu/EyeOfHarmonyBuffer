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
    private static final int SALT_AQUIFER = 0xB3;
    private static final int SALT_AQUIFER_DRY = 0xC4;
    private static final int SALT_LAKE_PIPE = 0xD5;

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
    /** 洞厅专属区外扩（blocks）：区域内干洞全深度、含水网络不生成。 */
    public static final double HALL_ZONE_MARGIN = 2.0 * CELL_BLOCKS;

    /** 含水网络参数。 */
    private static final double AQUIFER_BASIN_CHANCE = 0.40;
    private static final int AQUIFER_FULL_COUNT = 4;
    private static final int AQUIFER_HALF_COUNT = 3;
    private static final int AQUIFER_DEAD_COUNT = 2;
    private static final double AQUIFER_WATER_MIN = 20.0;
    private static final double AQUIFER_WATER_MAX = 40.0;
    private static final int AQUIFER_CROSS_RADIUS = 2;
    private static final int AQUIFER_LOCAL_RADIUS = 1;
    private static final int AQUIFER_DRY_RADIUS = 3;
    /** 半水节点单独接干洞的概率（单元内另有兜底保证至少一个）。 */
    private static final double AQUIFER_HALF_DRY_CHANCE = 0.5;
    /** 同单元三个半水节点的最小水平间距（blocks）。 */
    private static final double AQUIFER_HALF_MIN_DIST = 48.0;
    /** 半水节点找位次数的上限，超过后使用固定分散布局兜底。 */
    private static final int AQUIFER_HALF_POS_ATTEMPTS = 24;
    /** 含水盆地步长（单元）：每 2 个 256 单元一个盆地锚点。 */
    private static final int AQUIFER_BACKBONE_STEP = 2;
    /** 含水-湖泊连接管半径（blocks）。 */
    private static final double AQUIFER_LAKE_PIPE_RADIUS = 5.0;
    /** 找全水节点接湖的搜索半径（单元）。 */
    private static final int AQUIFER_LAKE_RADIUS = 4;
    /** 洞厅湖找全水节点的半径（单元）：洞厅专属区清空水网，需扩大到区外。 */
    private static final int HALL_LAKE_FULL_RADIUS = 7;
    /** 洞厅湖接入列要求的地面高度上限：低于该值才保证接入点在水下。 */
    private static final int MEGA_HALL_LAKE_MAX_FLOOR = 12;
    /** 洞厅湖接入点 Y：水面以下 2 格。 */
    private static final int MEGA_HALL_LAKE_PIPE_Y = 13;
    /** 洞厅湖接入点扫描步长（blocks）。 */
    private static final int MEGA_HALL_LAKE_SCAN_STEP = 32;
    /** 大厅湖连接管扫描半径（单元）：覆盖最远全水节点到大厅的管道长度。 */
    private static final int LAKE_PIPE_SCAN_RADIUS = 5;
    /** 干洞上层带下限：盆地 / 禁干带内只允许该高度以上的干洞。 */
    public static final int DRY_UPPER_MIN_Y = 46;

    private static final Object NO_MEGA_HALL = new Object();
    private static final ConcurrentHashMap<Long, Object> MEGA_HALL_CACHE =
        new ConcurrentHashMap<Long, Object>();
    /** 洞厅湖接入点缓存（洞厅极稀有，扫描一次后复用）。 */
    private static final Object NO_LAKE_CELL = new Object();
    private static final ConcurrentHashMap<Long, Object> MEGA_HALL_LAKE_CACHE =
        new ConcurrentHashMap<Long, Object>();
    /** 大厅湖 / 洞厅湖连接管缓存（同一管只构建一次）。 */
    private static final Object NO_LAKE_PIPE = new Object();
    private static final ConcurrentHashMap<Long, Object> LAKE_PIPE_CACHE =
        new ConcurrentHashMap<Long, Object>();
    /** 浅层限制单元缓存：线段检查会按采样点反复查询，避免重复哈希。 */
    private static final ConcurrentHashMap<Long, Boolean> SHALLOW_CELL_CACHE =
        new ConcurrentHashMap<Long, Boolean>();
    /** 洞厅专属区单元缓存。 */
    private static final ConcurrentHashMap<Long, Boolean> HALL_ZONE_CELL_CACHE =
        new ConcurrentHashMap<Long, Boolean>();
    private static final int MEGA_HALL_CACHE_LIMIT = 8192;
    private static final int SHALLOW_CELL_CACHE_LIMIT = 200_000;
    private static final int HALL_ZONE_CELL_CACHE_LIMIT = 200_000;

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
        // 浅层限制单元：含水盆地 + 盆地外圈 1 格禁干带，只允许上层干洞。
        // 洞厅专属区内不受此限制，干洞恢复全深度。
        boolean hallZone = isHallZoneCell(cellX, cellZ, seed);
        boolean shallowOnly = !hallZone
            && isShallowOnlyCell(cellX, cellZ, seed);
        boolean backbone = isBackboneCell(cellX, cellZ);

        if (backbone) {
            long id = nodeId(seed, cellX, cellZ, 0);
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 0, seed, SALT_NODE_POS, 96.0, 160.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, 1, seed, SALT_NODE_POS, 96.0, 160.0);
            float y = shallowOnly
                ? (float) CaveMath.hashRange(
                    cellX, cellZ, 2, seed, SALT_NODE_POS, 50.0, 57.0)
                : (float) CaveMath.hashRange(
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
            // 浅层限制单元内干洞只留上层，深层/中层让给暗河和禁干带。
            if (shallowOnly && band != CaveNode.BAND_UPPER) {
                continue;
            }
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

            if (shallowOnly) {
                // 浅层限制单元内不生成大厅，避免巨大干室探进暗河/禁干带。
                chamber = false;
                kind = CaveNode.KIND_NORMAL;
                crx = 0;
                cry = 0;
                crz = 0;
            } else if (chamber && chamberCrossesShallow(
                    x, z, crx, crz, seed)) {
                // 大厅空腔会横向探进盆地/禁干带：降级为普通分支。
                chamber = false;
                kind = CaveNode.KIND_NORMAL;
                crx = 0;
                cry = 0;
                crz = 0;
            }
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
        // 浅层限制单元不生成竖井；竖井列贴近浅层限制单元时也跳过。
        if (!shallowOnly
            && mid != null
            && !columnNearShallow(mid.x, mid.z, 6.0, seed)
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
        MEGA_HALL_LAKE_CACHE.clear();
        LAKE_PIPE_CACHE.clear();
        SHALLOW_CELL_CACHE.clear();
        HALL_ZONE_CELL_CACHE.clear();
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

    // ------------------------------------------------------------
    // ------------------------------------------------------------
    // 含水网络（暗河）：第二阶段，干洞网络完成后独立生成
    // ------------------------------------------------------------

    private static boolean isAquiferKind(int kind) {
        return kind == CaveNode.KIND_AQUIFER_FULL
            || kind == CaveNode.KIND_AQUIFER_HALF
            || kind == CaveNode.KIND_AQUIFER_DEAD;
    }

    private static boolean isAquiferBackboneCell(int cellX, int cellZ) {
        return Math.floorMod(cellX, AQUIFER_BACKBONE_STEP) == 0
            && Math.floorMod(cellZ, AQUIFER_BACKBONE_STEP) == 0;
    }

    /** 某 256 格单元的水面高度（20~45）。 */
    public static int aquiferWaterLevel(int cellX, int cellZ, long seed) {
        int bx = Math.floorDiv(cellX, AQUIFER_BACKBONE_STEP);
        int bz = Math.floorDiv(cellZ, AQUIFER_BACKBONE_STEP);
        return (int) Math.round(AQUIFER_WATER_MIN + CaveMath.hash01(
            bx, bz, 0, seed, SALT_AQUIFER)
            * (AQUIFER_WATER_MAX - AQUIFER_WATER_MIN));
    }

    /** 某 256 单元是否属于含水盆地（雕刻器硬隔离兜底也会用到）。 */
    public static boolean basinHasAquifer(int cellX, int cellZ, long seed) {
        int bx = Math.floorDiv(cellX, AQUIFER_BACKBONE_STEP);
        int bz = Math.floorDiv(cellZ, AQUIFER_BACKBONE_STEP);
        return CaveMath.hash01(bx, bz, 1, seed, SALT_AQUIFER)
            < AQUIFER_BASIN_CHANCE;
    }

    /**
     * 浅层限制单元：含水盆地本身 + 盆地外圈 1 格禁干带。
     * 这些单元内只允许上层干洞（DRY_UPPER_MIN_Y 以上）。
     */
    public static boolean isShallowOnlyCell(int cellX, int cellZ, long seed) {
        long key = cellKey(cellX, cellZ) ^ CaveMath.mix64(seed);
        Boolean cached = SHALLOW_CELL_CACHE.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        if (SHALLOW_CELL_CACHE.size() > SHALLOW_CELL_CACHE_LIMIT) {
            SHALLOW_CELL_CACHE.clear();
        }
        boolean result = false;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (basinHasAquifer(cellX + dx, cellZ + dz, seed)) {
                    result = true;
                    break;
                }
            }
            if (result) {
                break;
            }
        }
        SHALLOW_CELL_CACHE.put(key, Boolean.valueOf(result));
        return result;
    }

    /** 某 256 单元是否落在洞厅专属区内（包围盒外扩 HALL_ZONE_MARGIN）。 */
    public static boolean isHallZoneCell(int cellX, int cellZ, long seed) {
        long key = cellKey(cellX, cellZ) ^ CaveMath.mix64(seed);
        Boolean cached = HALL_ZONE_CELL_CACHE.get(key);
        if (cached != null) {
            return cached.booleanValue();
        }
        if (HALL_ZONE_CELL_CACHE.size() > HALL_ZONE_CELL_CACHE_LIMIT) {
            HALL_ZONE_CELL_CACHE.clear();
        }
        boolean result = false;
        int superX = Math.floorDiv(
            cellX, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        int superZ = Math.floorDiv(
            cellZ, MEGA_HALL_CELL_BLOCKS / CELL_BLOCKS);
        double x0 = cellX * CELL_BLOCKS;
        double x1 = x0 + CELL_BLOCKS;
        double z0 = cellZ * CELL_BLOCKS;
        double z1 = z0 + CELL_BLOCKS;
        for (int sz = -1; sz <= 1 && !result; sz++) {
            for (int sx = -1; sx <= 1 && !result; sx++) {
                CaveMegaHall hall = megaHallForSupercell(
                    superX + sx, superZ + sz, seed);
                if (hall == null) {
                    continue;
                }
                if (x1 >= hall.minX - HALL_ZONE_MARGIN
                    && x0 <= hall.maxX + HALL_ZONE_MARGIN
                    && z1 >= hall.minZ - HALL_ZONE_MARGIN
                    && z0 <= hall.maxZ + HALL_ZONE_MARGIN) {
                    result = true;
                }
            }
        }
        HALL_ZONE_CELL_CACHE.put(key, Boolean.valueOf(result));
        return result;
    }

    /** 大厅水平包围盒是否碰到任何浅层限制单元（盆地 / 禁干带）。 */
    private static boolean chamberCrossesShallow(
        double x, double z, double rx, double rz, long seed
    ) {
        int minCX = Math.floorDiv((int) Math.floor(x - rx), CELL_BLOCKS);
        int maxCX = Math.floorDiv((int) Math.floor(x + rx), CELL_BLOCKS);
        int minCZ = Math.floorDiv((int) Math.floor(z - rz), CELL_BLOCKS);
        int maxCZ = Math.floorDiv((int) Math.floor(z + rz), CELL_BLOCKS);
        for (int cz = minCZ; cz <= maxCZ; cz++) {
            for (int cx = minCX; cx <= maxCX; cx++) {
                if (isShallowOnlyCell(cx, cz, seed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 列（x,z）是否在 margin 范围内贴近某个浅层限制单元。 */
    private static boolean columnNearShallow(double x, double z,
                                             double margin, long seed) {
        int cx = Math.floorDiv((int) Math.floor(x), CELL_BLOCKS);
        int cz = Math.floorDiv((int) Math.floor(z), CELL_BLOCKS);
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = cx + dx;
                int nz = cz + dz;
                if (!isShallowOnlyCell(nx, nz, seed)) {
                    continue;
                }
                double cellMinX = nx * CELL_BLOCKS;
                double cellMaxX = cellMinX + CELL_BLOCKS;
                double cellMinZ = nz * CELL_BLOCKS;
                double cellMaxZ = cellMinZ + CELL_BLOCKS;
                double closestX = Math.max(cellMinX, Math.min(x, cellMaxX));
                double closestZ = Math.max(cellMinZ, Math.min(z, cellMaxZ));
                double d = Math.sqrt(
                    (x - closestX) * (x - closestX)
                        + (z - closestZ) * (z - closestZ));
                if (d <= margin) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 生成某单元的含水节点（带缓存），并对干洞节点 / 线段做避让。 */
    private static List<CaveNode> aquiferNodesForCell(
        int cellX, int cellZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache
    ) {
        long key = cellKey(cellX, cellZ);
        List<CaveNode> cached = aquiferNodeCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<CaveNode> out = computeAquiferNodes(
            cellX, cellZ, seed, nodeCache, edgeCache);
        List<CaveNode> prev = aquiferNodeCache.putIfAbsent(key, out);
        return prev != null ? prev : out;
    }

    private static List<CaveNode> computeAquiferNodes(
        int cellX, int cellZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache
    ) {
        List<CaveNode> out = new ArrayList<CaveNode>();
        // 只有盆地锚点单元（每 2 个 256 单元）才生成含水节点
        if (!isAquiferBackboneCell(cellX, cellZ)) {
            return out;
        }
        // 洞厅专属区内不生成含水网络，把空间让给干洞与洞厅本体。
        if (isHallZoneCell(cellX, cellZ, seed)) {
            return out;
        }
        int wl = aquiferWaterLevel(cellX, cellZ, seed);
        int idx = 0;
        // 骨干锚点：每个盆地固定一个，放在深层底部（y=6），
        // 低于干洞 DEEP 带，保证水网全局连通且不与干洞抢层。
        out.add(new CaveNode(
            nodeId(seed, cellX, cellZ, 0x2FFF),
            cellX, cellZ,
            cellX * CELL_BLOCKS + 128.0f,
            6.0f,
            cellZ * CELL_BLOCKS + 128.0f,
            CaveNode.KIND_AQUIFER_FULL, CaveNode.BAND_DEEP,
            0, 0, 0, 0, 0));
        if (!basinHasAquifer(cellX, cellZ, seed)) {
            return out;
        }
        for (int i = 0; i < AQUIFER_FULL_COUNT; i++) {
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 0, seed, SALT_AQUIFER, 24.0, 232.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 1, seed, SALT_AQUIFER, 24.0, 232.0);
            float y = (float) (wl - 3 - CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 2, seed, SALT_AQUIFER, 1.0, 8.0));
            out.add(new CaveNode(
                nodeId(seed, cellX, cellZ, 0x2000 + idx),
                cellX, cellZ, x, y, z,
                CaveNode.KIND_AQUIFER_FULL, CaveNode.BAND_DEEP,
                0, 0, 0, 0, 0));
            idx++;
        }
        List<double[]> halfPositions =
            new ArrayList<double[]>(AQUIFER_HALF_COUNT);
        for (int i = 0; i < AQUIFER_HALF_COUNT; i++) {
            double hx = 0;
            double hz = 0;
            boolean placed = false;
            for (int attempt = 0;
                 attempt < AQUIFER_HALF_POS_ATTEMPTS; attempt++) {
                double cx = cellX * CELL_BLOCKS + CaveMath.hashRange(
                    cellX, cellZ, idx * 3 + 0 + attempt * 64,
                    seed, SALT_AQUIFER, 24.0, 232.0);
                double cz = cellZ * CELL_BLOCKS + CaveMath.hashRange(
                    cellX, cellZ, idx * 3 + 1 + attempt * 64,
                    seed, SALT_AQUIFER, 24.0, 232.0);
                boolean ok = true;
                for (double[] p : halfPositions) {
                    double dx = cx - p[0];
                    double dz = cz - p[1];
                    if (dx * dx + dz * dz
                        < AQUIFER_HALF_MIN_DIST * AQUIFER_HALF_MIN_DIST) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    hx = cx;
                    hz = cz;
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                hx = cellX * CELL_BLOCKS + 40.0 + i * 72.0;
                hz = cellZ * CELL_BLOCKS + 40.0 + (i % 2) * 140.0;
            }
            halfPositions.add(new double[] {hx, hz});
            float y = (float) (wl + 2 + CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 2, seed, SALT_AQUIFER, 1.0, 6.0));
            out.add(new CaveNode(
                nodeId(seed, cellX, cellZ, 0x2000 + idx),
                cellX, cellZ, (float) hx, y, (float) hz,
                CaveNode.KIND_AQUIFER_HALF, CaveNode.BAND_MID,
                0, 0, 0, 0, 0));
            idx++;
        }
        for (int i = 0; i < AQUIFER_DEAD_COUNT; i++) {
            float x = cellX * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 0, seed, SALT_AQUIFER, 24.0, 232.0);
            float z = cellZ * CELL_BLOCKS + (float) CaveMath.hashRange(
                cellX, cellZ, idx * 3 + 1, seed, SALT_AQUIFER, 24.0, 232.0);
            boolean below = CaveMath.hash01(
                cellX, cellZ, idx * 3 + 2, seed, SALT_AQUIFER) < 0.5;
            float y = below
                ? (float) (wl - 2 - CaveMath.hashRange(
                    cellX, cellZ, idx * 3 + 2, seed, SALT_AQUIFER, 1.0, 6.0))
                : (float) (wl + 2 + CaveMath.hashRange(
                    cellX, cellZ, idx * 3 + 2, seed, SALT_AQUIFER, 1.0, 6.0));
            out.add(new CaveNode(
                nodeId(seed, cellX, cellZ, 0x2000 + idx),
                cellX, cellZ, x, y, z,
                CaveNode.KIND_AQUIFER_DEAD,
                below ? CaveNode.BAND_DEEP : CaveNode.BAND_MID,
                0, 0, 0, 0, 0));
            idx++;
        }
        return out;
    }

    /** 调试/传送用：列出附近单元的全部含水节点（不做避让过滤）。 */
    public static List<CaveNode> debugAquiferNodesNear(
        int worldX, int worldZ, long seed, int radiusCells
    ) {
        List<CaveNode> out = new ArrayList<CaveNode>();
        int ccx = Math.floorDiv(worldX, 256);
        int ccz = Math.floorDiv(worldZ, 256);
        for (int r = 0; r <= radiusCells; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    out.addAll(computeAquiferNodes(
                        ccx + dx, ccz + dz, seed, null, null));
                }
            }
        }
        return out;
    }

    /** 某单元含水节点的连接（局部），并接干洞。 */
    private static List<CaveSegment> aquiferSegmentsForCell(
        int cellX, int cellZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        Map<Long, List<CaveSegment>> aquiferEdgeCache
    ) {
        long key = cellKey(cellX, cellZ);
        List<CaveSegment> cached = aquiferEdgeCache.get(key);
        if (cached != null) {
            return cached;
        }
        List<CaveSegment> out = new ArrayList<CaveSegment>();
        List<CaveNode> nodes = aquiferNodesForCell(
            cellX, cellZ, seed, nodeCache, edgeCache, aquiferNodeCache);
        Set<Long> dryLinkedHalfIds = new HashSet<Long>();
        for (CaveNode node : nodes) {
            connectAquiferNode(
                node, seed, nodeCache, edgeCache, aquiferNodeCache,
                out, dryLinkedHalfIds);
        }
        // 单元兜底：至少一个半水节点接干洞，避免出现没有干湿接口的盆地。
        boolean anyHalfLinked = false;
        for (CaveNode node : nodes) {
            if (node.kind == CaveNode.KIND_AQUIFER_HALF
                && dryLinkedHalfIds.contains(node.id)) {
                anyHalfLinked = true;
                break;
            }
        }
        if (!anyHalfLinked) {
            int halfCount = 0;
            for (CaveNode node : nodes) {
                if (node.kind == CaveNode.KIND_AQUIFER_HALF) {
                    halfCount++;
                }
            }
            if (halfCount > 0) {
                int pick = (int) (CaveMath.hash01(
                    cellX, cellZ, 11, seed, SALT_AQUIFER_DRY) * halfCount);
                int cur = 0;
                CaveNode guaranteed = null;
                for (CaveNode node : nodes) {
                    if (node.kind == CaveNode.KIND_AQUIFER_HALF) {
                        if (cur == pick) {
                            guaranteed = node;
                            break;
                        }
                        cur++;
                    }
                }
                if (guaranteed != null) {
                    CaveNode dry = nearestDryNode(
                        guaranteed, seed, nodeCache, AQUIFER_DRY_RADIUS);
                    if (dry != null) {
                        CaveSegment seg = buildSegment(
                            guaranteed, dry, seed);
                        if (seg != null) {
                            out.add(seg);
                        }
                    }
                }
            }
        }
        List<CaveSegment> prev = aquiferEdgeCache.putIfAbsent(key, out);
        return prev != null ? prev : out;
    }

    /** 收集与某区块相交的含水-湖泊连接管（大厅湖 / 洞厅湖）。 */
    private static void collectLakePipesForChunk(
        int chunkX, int chunkZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        List<CaveSegment> out, Set<Long> seenEdges
    ) {
        int cellX = chunkX >> 4;
        int cellZ = chunkZ >> 4;
        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;
        // 带湖大厅：连接管最长约 4 单元，扫描 ±5 单元保证覆盖。
        for (int dz = -LAKE_PIPE_SCAN_RADIUS;
             dz <= LAKE_PIPE_SCAN_RADIUS; dz++) {
            for (int dx = -LAKE_PIPE_SCAN_RADIUS;
                 dx <= LAKE_PIPE_SCAN_RADIUS; dx++) {
                for (CaveNode node : nodesOf(
                    cellX + dx, cellZ + dz, seed, nodeCache)) {
                    if (node.kind != CaveNode.KIND_CHAMBER) {
                        continue;
                    }
                    CaveChamber ch = new CaveChamber(
                        node.x, node.y, node.z,
                        node.chamberRx, node.chamberRy, node.chamberRz,
                        node.id);
                    if (!ch.hasLake) {
                        continue;
                    }
                    CaveSegment pipe = lakePipeForChamber(
                        node, ch, seed,
                        nodeCache, edgeCache, aquiferNodeCache);
                    if (pipe == null) {
                        continue;
                    }
                    if (pipe.maxX >= x0 && pipe.minX <= x0 + 16
                        && pipe.maxZ >= z0 && pipe.minZ <= z0 + 16
                        && seenEdges.add(pipe.edgeId)) {
                        out.add(pipe);
                    }
                }
            }
        }
        // 洞厅湖：管道可能伸出洞厅超级格，查周围 3×3 超级格。
        int superX = Math.floorDiv(chunkX * 16, MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(chunkZ * 16, MEGA_HALL_CELL_BLOCKS);
        for (int sz = -1; sz <= 1; sz++) {
            for (int sx = -1; sx <= 1; sx++) {
                CaveMegaHall hall = megaHallForSupercell(
                    superX + sx, superZ + sz, seed);
                if (hall == null) {
                    continue;
                }
                CaveSegment pipe = lakePipeForHall(
                    hall, seed, nodeCache, edgeCache, aquiferNodeCache);
                if (pipe == null) {
                    continue;
                }
                if (pipe.maxX >= x0 && pipe.minX <= x0 + 16
                    && pipe.maxZ >= z0 && pipe.minZ <= z0 + 16
                    && seenEdges.add(pipe.edgeId)) {
                    out.add(pipe);
                }
            }
        }
    }

    /** 带湖大厅的连接管（缓存：同一大厅只构建一次）。 */
    private static CaveSegment lakePipeForChamber(
        CaveNode node, CaveChamber ch, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache
    ) {
        long key = CaveMath.mix64(node.id ^ seed);
        Object cached = LAKE_PIPE_CACHE.get(key);
        if (cached != null) {
            return cached == NO_LAKE_PIPE ? null : (CaveSegment) cached;
        }
        CaveSegment pipe = null;
        double[] target = chamberLakeTarget(ch);
        if (target != null) {
            CaveNode full = nearestFullNodeForLake(
                target[0], target[1], target[2], seed,
                nodeCache, edgeCache, aquiferNodeCache,
                AQUIFER_LAKE_RADIUS, ch, null);
            if (full != null) {
                pipe = buildLakePipe(
                    full, target[0], target[1], target[2], seed,
                    SALT_LAKE_PIPE, AQUIFER_LAKE_PIPE_RADIUS,
                    (int) ch.lakeSurfaceY, true);
            }
        }
        LAKE_PIPE_CACHE.put(key, pipe != null ? pipe : NO_LAKE_PIPE);
        return pipe;
    }

    /** 洞厅湖的连接管（缓存：每个洞厅只构建一次）。 */
    private static CaveSegment lakePipeForHall(
        CaveMegaHall hall, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache
    ) {
        int superX = Math.floorDiv(
            (int) Math.floor(hall.cx), MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(
            (int) Math.floor(hall.cz), MEGA_HALL_CELL_BLOCKS);
        long key = megaHallKey(seed, superX, superZ);
        Object cached = LAKE_PIPE_CACHE.get(key);
        if (cached != null) {
            return cached == NO_LAKE_PIPE ? null : (CaveSegment) cached;
        }
        CaveSegment pipe = null;
        double[] lake = megaHallLakeCell(hall, seed);
        if (lake != null) {
            CaveNode full = nearestFullNodeForLake(
                lake[0], MEGA_HALL_LAKE_PIPE_Y, lake[1], seed,
                nodeCache, edgeCache, aquiferNodeCache,
                HALL_LAKE_FULL_RADIUS, null, hall);
            if (full != null) {
                pipe = buildLakePipe(
                    full, lake[0], MEGA_HALL_LAKE_PIPE_Y, lake[1], seed,
                    SALT_LAKE_PIPE + 1, AQUIFER_LAKE_PIPE_RADIUS,
                    CaveMegaHall.LAKE_WATER_LEVEL, true);
            }
        }
        LAKE_PIPE_CACHE.put(key, pipe != null ? pipe : NO_LAKE_PIPE);
        return pipe;
    }

    /** 大厅湖内的接入点：湖床上方 1 格，避开石柱。 */
    private static double[] chamberLakeTarget(CaveChamber ch) {
        int y = ch.lakeBedY + 1;
        double[][] candidates = new double[][] {
            {ch.cx, y, ch.cz},
            {ch.cx + ch.rx * 0.35, y, ch.cz},
            {ch.cx - ch.rx * 0.35, y, ch.cz},
            {ch.cx, y, ch.cz + ch.rz * 0.35},
            {ch.cx, y, ch.cz - ch.rz * 0.35},
            {ch.cx + ch.rx * 0.25, y, ch.cz + ch.rz * 0.25},
        };
        for (double[] c : candidates) {
            if (ch.inside(c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, 0.0)) {
                return c;
            }
        }
        return null;
    }

    /** 找离目标最近的全水节点（可排除位于大厅 / 洞厅内部的节点）。 */
    private static CaveNode nearestFullNodeForLake(
        double x, double y, double z, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        int radius, CaveChamber rejectChamber, CaveMegaHall rejectHall
    ) {
        int ccx = Math.floorDiv((int) Math.floor(x), CELL_BLOCKS);
        int ccz = Math.floorDiv((int) Math.floor(z), CELL_BLOCKS);
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : aquiferNodesForCell(
                    ccx + dx, ccz + dz, seed,
                    nodeCache, edgeCache, aquiferNodeCache)) {
                    if (n.kind != CaveNode.KIND_AQUIFER_FULL) {
                        continue;
                    }
                    if (rejectChamber != null
                        && rejectChamber.inside(
                            n.x + 0.5, n.y + 0.5, n.z + 0.5, 0.0)) {
                        continue;
                    }
                    if (rejectHall != null
                        && nodeInsideMegaHall(n, rejectHall)) {
                        continue;
                    }
                    double dxp = n.x - x;
                    double dyp = n.y - y;
                    double dzp = n.z - z;
                    double d = dxp * dxp + dyp * dyp + dzp * dzp;
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

    /** 洞厅湖接入点：洞厅内地面最深的非石柱列（带缓存）。 */
    private static double[] megaHallLakeCell(CaveMegaHall hall, long seed) {
        int superX = Math.floorDiv(
            (int) Math.floor(hall.cx), MEGA_HALL_CELL_BLOCKS);
        int superZ = Math.floorDiv(
            (int) Math.floor(hall.cz), MEGA_HALL_CELL_BLOCKS);
        long key = megaHallKey(seed, superX, superZ);
        Object cached = MEGA_HALL_LAKE_CACHE.get(key);
        if (cached != null) {
            return cached == NO_LAKE_CELL ? null : (double[]) cached;
        }
        double[] found = scanMegaHallLakeCell(hall, seed);
        MEGA_HALL_LAKE_CACHE.put(key, found != null ? found : NO_LAKE_CELL);
        return found;
    }

    private static double[] scanMegaHallLakeCell(CaveMegaHall hall,
                                                 long seed) {
        int minX = (int) Math.floor(hall.minX);
        int maxX = (int) Math.ceil(hall.maxX);
        int minZ = (int) Math.floor(hall.minZ);
        int maxZ = (int) Math.ceil(hall.maxZ);
        int step = MEGA_HALL_LAKE_SCAN_STEP;
        double bestX = Double.NaN;
        double bestZ = Double.NaN;
        double bestFloor = Double.POSITIVE_INFINITY;
        int[] span = new int[2];
        for (int z = minZ; z <= maxZ; z += step) {
            for (int x = minX; x <= maxX; x += step) {
                if (!hall.insideHorizontal(x + 0.5, z + 0.5)) {
                    continue;
                }
                if (hall.isPillarColumn(x, z)) {
                    continue;
                }
                if (!hall.verticalSpan(
                        x, z, (int) Math.ceil(hall.maxY), span)) {
                    continue;
                }
                if (span[0] > MEGA_HALL_LAKE_PIPE_Y
                    || span[1] < MEGA_HALL_LAKE_PIPE_Y + 6) {
                    continue;
                }
                int fy = hall.floorY(x, z);
                if (fy <= MEGA_HALL_LAKE_MAX_FLOOR && fy < bestFloor) {
                    bestX = x;
                    bestZ = z;
                    bestFloor = fy;
                }
            }
        }
        if (Double.isNaN(bestX)) {
            return null;
        }
        return new double[] {bestX, bestZ};
    }

    /** U 形含水连接管：下潜 → 水平接近 → 抬升进入湖体。 */
    private static CaveSegment buildLakePipe(
        CaveNode from, double tx, double ty, double tz,
        long seed, int salt, double radius,
        int waterLevelY, boolean pierceShell
    ) {
        double lowY = Math.max(3.0, Math.min(from.y, ty) - 7.0);
        long targetHash = CaveMath.mix64(
            Double.doubleToLongBits(tx)
                ^ Double.doubleToLongBits(ty * 1.0e6)
                ^ Double.doubleToLongBits(tz)
                ^ salt);
        long pipeEdge = edgeId(from.id, targetHash);
        int n = 7;
        float[] xs = new float[n];
        float[] ys = new float[n];
        float[] zs = new float[n];
        float[] rs = new float[n];
        float maxR = 0;
        xs[0] = (float) from.x;
        ys[0] = (float) from.y;
        zs[0] = (float) from.z;
        for (int i = 1; i < n - 1; i++) {
            double t = (double) i / (n - 1);
            double px = from.x + (tx - from.x) * t;
            double pz = from.z + (tz - from.z) * t;
            double jx = (CaveMath.hash01(
                pipeEdge, i, 0, seed, salt) - 0.5) * 10.0;
            double jz = (CaveMath.hash01(
                pipeEdge, i, 1, seed, salt) - 0.5) * 10.0;
            double py = lowY;
            double rise = (t - 0.5) / 0.5;
            if (rise > 0) {
                py = lowY + (ty - lowY) * rise;
            }
            py += (CaveMath.hash01(
                pipeEdge, i, 2, seed, salt) - 0.5) * 1.5;
            if (py < 2.0) {
                py = 2.0;
            }
            xs[i] = (float) (px + jx);
            ys[i] = (float) py;
            zs[i] = (float) (pz + jz);
            double r = radius * (0.85 + 0.3 * CaveMath.hash01(
                pipeEdge, i, 3, seed, salt));
            rs[i] = (float) r;
            if (r > maxR) {
                maxR = (float) r;
            }
        }
        xs[n - 1] = (float) tx;
        ys[n - 1] = (float) ty;
        zs[n - 1] = (float) tz;
        rs[0] = (float) (radius * (0.85 + 0.3 * CaveMath.hash01(
            pipeEdge, 0, 3, seed, salt)));
        rs[n - 1] = (float) (radius * (0.85 + 0.3 * CaveMath.hash01(
            pipeEdge, n - 1, 3, seed, salt)));
        maxR = Math.max(maxR, Math.max(rs[0], rs[n - 1]));

        float margin = maxR + 3.0f;
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, xs[i]);
            maxX = Math.max(maxX, xs[i]);
            minY = Math.min(minY, ys[i]);
            maxY = Math.max(maxY, ys[i]);
            minZ = Math.min(minZ, zs[i]);
            maxZ = Math.max(maxZ, zs[i]);
        }
        return new CaveSegment(
            pipeEdge, false,
            xs, ys, zs, rs,
            minX - margin, minY - margin, minZ - margin,
            maxX + margin, maxY + margin, maxZ + margin,
            true, false, waterLevelY, pierceShell
        );
    }

    /** 收集与某区块相交的含水线段（含跨单元长管）。 */
    private static void collectAquiferSegmentsForChunk(
        int chunkX, int chunkZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        Map<Long, List<CaveSegment>> aquiferEdgeCache,
        List<CaveSegment> out, Set<Long> seenEdges
    ) {
        int cellX = chunkX >> 4;
        int cellZ = chunkZ >> 4;
        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;
        for (int dz = -AQUIFER_CROSS_RADIUS; dz <= AQUIFER_CROSS_RADIUS; dz++) {
            for (int dx = -AQUIFER_CROSS_RADIUS; dx <= AQUIFER_CROSS_RADIUS; dx++) {
                for (CaveSegment seg : aquiferSegmentsForCell(
                    cellX + dx, cellZ + dz, seed,
                    nodeCache, edgeCache, aquiferNodeCache,
                    aquiferEdgeCache)) {
                    if (seenEdges.add(seg.edgeId)
                        && seg.maxX >= x0 && seg.minX <= x0 + 16
                        && seg.maxZ >= z0 && seg.minZ <= z0 + 16) {
                        out.add(seg);
                    }
                }
            }
        }
    }

    private static void connectAquiferNode(
        CaveNode node, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        List<CaveSegment> out,
        Set<Long> dryLinkedHalfIds
    ) {
        if (node.kind == CaveNode.KIND_AQUIFER_DEAD) {
            CaveNode target = nearestAquiferNode(
                node, seed, nodeCache, edgeCache, aquiferNodeCache,
                AQUIFER_LOCAL_RADIUS, -1, true, false);
            if (target == null) {
                target = nearestAquiferNode(
                    node, seed, nodeCache, edgeCache, aquiferNodeCache,
                    AQUIFER_CROSS_RADIUS, -1, false, false);
            }
            if (target == null) {
                target = nearestAquiferNode(
                    node, seed, nodeCache, edgeCache, aquiferNodeCache,
                    AQUIFER_CROSS_RADIUS, -1, true, false);
            }
            if (target == null) {
                target = nearestDryNode(
                    node, seed, nodeCache, AQUIFER_DRY_RADIUS);
            }
            if (target != null) {
                addSegment(out, buildSegment(node, target, seed));
            }
            return;
        }
        if (node.kind == CaveNode.KIND_AQUIFER_FULL) {
            CaveNode t1 = nearestAquiferNode(
                node, seed, nodeCache, edgeCache, aquiferNodeCache,
                AQUIFER_CROSS_RADIUS, -1, false, false);
            if (t1 == null) {
                t1 = nearestAquiferNode(
                    node, seed, nodeCache, edgeCache, aquiferNodeCache,
                    AQUIFER_CROSS_RADIUS, -1, true, false);
            }
            if (t1 == null) {
                // 实在没有含水邻居时接干洞，避免孤立封闭
                t1 = nearestDryNode(
                    node, seed, nodeCache, AQUIFER_DRY_RADIUS);
            }
            if (t1 != null) {
                addSegment(out, buildSegment(node, t1, seed));
            }
            CaveNode t2 = nearestAquiferNode(
                node, seed, nodeCache, edgeCache, aquiferNodeCache,
                AQUIFER_CROSS_RADIUS,
                t1 != null ? t1.id : -1, false, false);
            if (t2 != null) {
                addSegment(out, buildSegment(node, t2, seed));
            }
            // 骨干锚点：向右 / 向下连相邻锚点，保证全局连通
            if (node.id == nodeId(
                    seed, node.cellX, node.cellZ, 0x2FFF)) {
                for (int d = 0; d < 2; d++) {
                    int nx = node.cellX
                        + (d == 0 ? AQUIFER_BACKBONE_STEP : 0);
                    int nz = node.cellZ
                        + (d == 0 ? 0 : AQUIFER_BACKBONE_STEP);
                    if (!isAquiferBackboneCell(nx, nz)) {
                        continue;
                    }
                    CaveNode target = aquiferBackboneNodeOf(
                        nx, nz, seed, nodeCache, edgeCache,
                        aquiferNodeCache);
                    if (target != null && target.id != node.id) {
                        addSegment(out, buildSegment(node, target, seed));
                    }
                }
            }
            return;
        }
        // 半水：只在同单元内连水位以下的全水节点，让通道下半段真正见水
        CaveNode target = nearestAquiferNode(
            node, seed, nodeCache, edgeCache, aquiferNodeCache,
            AQUIFER_LOCAL_RADIUS, -1, false, true);
        if (target == null) {
            target = nearestAquiferNode(
                node, seed, nodeCache, edgeCache, aquiferNodeCache,
                AQUIFER_LOCAL_RADIUS, -1, true, true);
        }
        boolean linkedDry = false;
        if (target != null) {
            addSegment(out, buildSegment(node, target, seed));
        } else {
            // 实在没有含水邻居时也保证接上干洞，避免孤立封闭节点
            CaveNode dry = nearestDryNode(
                node, seed, nodeCache, AQUIFER_DRY_RADIUS);
            if (dry != null) {
                addSegment(out, buildSegment(node, dry, seed));
                dryLinkedHalfIds.add(node.id);
                linkedDry = true;
            }
        }
        // 干湿接口：按概率额外接一个干洞节点（单元兜底在调用方处理）
        if (!linkedDry
            && CaveMath.hash01(
                node.cellX, node.cellZ, node.id, seed, SALT_AQUIFER_DRY)
                < AQUIFER_HALF_DRY_CHANCE) {
            CaveNode dry = nearestDryNode(
                node, seed, nodeCache, AQUIFER_DRY_RADIUS);
            if (dry != null) {
                addSegment(out, buildSegment(node, dry, seed));
                dryLinkedHalfIds.add(node.id);
            }
        }
    }

    /** 只有非 null 的线段才加入列表（buildSegment 可能因避让检查返回 null）。 */
    private static void addSegment(List<CaveSegment> out, CaveSegment seg) {
        if (seg != null) {
            out.add(seg);
        }
    }

    private static CaveNode nearestAquiferNode(
        CaveNode node, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache,
        int radius, long excludeId, boolean allowAnyKind,
        boolean sameCellOnly
    ) {
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : aquiferNodesForCell(
                    node.cellX + dx, node.cellZ + dz, seed,
                    nodeCache, edgeCache, aquiferNodeCache)) {
                    if (n.id == node.id || n.id == excludeId) {
                        continue;
                    }
                    if (sameCellOnly
                        && (n.cellX != node.cellX || n.cellZ != node.cellZ)) {
                        continue;
                    }
                    if (!allowAnyKind
                        && n.kind != CaveNode.KIND_AQUIFER_FULL) {
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

    private static CaveNode aquiferBackboneNodeOf(
        int cellX, int cellZ, long seed,
        Map<Long, List<CaveNode>> nodeCache,
        Map<Long, List<CaveSegment>> edgeCache,
        Map<Long, List<CaveNode>> aquiferNodeCache
    ) {
        long id = nodeId(seed, cellX, cellZ, 0x2FFF);
        for (CaveNode n : aquiferNodesForCell(
            cellX, cellZ, seed, nodeCache, edgeCache, aquiferNodeCache)) {
            if (n.id == id) {
                return n;
            }
        }
        return null;
    }

    private static CaveNode nearestDryNode(
        CaveNode node, long seed,
        Map<Long, List<CaveNode>> nodeCache, int radius
    ) {
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : nodesOf(
                    node.cellX + dx, node.cellZ + dz, seed, nodeCache)) {
                    if (n.id == node.id || isAquiferKind(n.kind)) {
                        continue;
                    }
                    if (n.kind == CaveNode.KIND_MEGA_HALL) {
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
    private static boolean isBackboneCell(int cellX, int cellZ) {
        return Math.floorMod(cellX, BACKBONE_STEP) == 0
            && Math.floorMod(cellZ, BACKBONE_STEP) == 0;
    }

    private static int detailCount(int cellX, int cellZ, long seed) {
        double r = CaveMath.hash01(cellX, cellZ, 0, seed, SALT_DETAIL_COUNT);
        if (r < 0.05) {
            return 0;
        }
        if (r < 0.25) {
            return 1;
        }
        if (r < 0.55) {
            return 2;
        }
        if (r < 0.85) {
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
            && !isAquiferKind(a.kind)
            && !isAquiferKind(b.kind)
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
        boolean aquifer = isAquiferKind(a.kind) || isAquiferKind(b.kind);
        boolean fullySubmerged = false;
        int waterLevelY = 0;
        if (aquifer) {
            int wa = aquiferWaterLevel(a.cellX, a.cellZ, seed);
            int wb = aquiferWaterLevel(b.cellX, b.cellZ, seed);
            waterLevelY = Math.min(wa, wb);
            boolean fa = a.kind == CaveNode.KIND_AQUIFER_FULL
                || (a.kind == CaveNode.KIND_AQUIFER_DEAD && a.y < wa - 1);
            boolean fb = b.kind == CaveNode.KIND_AQUIFER_FULL
                || (b.kind == CaveNode.KIND_AQUIFER_DEAD && b.y < wb - 1);
            // 只有两端都是全水节点才全淹没；
            // 半水节点不跨单元，避免两侧水面高度不一致穿帮。
            fullySubmerged = fa && fb;
        }
        // 全避让：普通线段（干洞 / 含水）不得穿过洞厅体积，洞厅连接除外。
        if (a.kind != CaveNode.KIND_MEGA_HALL
            && b.kind != CaveNode.KIND_MEGA_HALL
            && crossesMegaHall(xs, ys, zs, rs, seed)) {
            return null;
        }
        // 路径级检查：干洞线段在盆地 / 禁干带内下探到上层带以下时拒绝。
        // 洞厅连接除外（洞厅本身贯通深层）。
        if (!aquifer
            && a.kind != CaveNode.KIND_MEGA_HALL
            && b.kind != CaveNode.KIND_MEGA_HALL
            && dipsIntoShallowZone(xs, ys, zs, rs, seed)) {
            return null;
        }
        return new CaveSegment(
            edgeId,
            collapsed,
            xs, ys, zs, rs,
            minX - margin, minY - margin, minZ - margin,
            maxX + margin, maxY + margin, maxZ + margin,
            aquifer, fullySubmerged, waterLevelY, false
        );
    }

    /** 线段（含半径）是否穿过任何洞厅体积（洞厅连接除外）。 */
    private static boolean crossesMegaHall(float[] xs, float[] ys,
                                           float[] zs, float[] rs,
                                           long seed) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < xs.length; i++) {
            minX = Math.min(minX, (int) Math.floor(xs[i]));
            maxX = Math.max(maxX, (int) Math.floor(xs[i]));
            minZ = Math.min(minZ, (int) Math.floor(zs[i]));
            maxZ = Math.max(maxZ, (int) Math.floor(zs[i]));
        }
        int minSX = Math.floorDiv(minX, MEGA_HALL_CELL_BLOCKS);
        int maxSX = Math.floorDiv(maxX, MEGA_HALL_CELL_BLOCKS);
        int minSZ = Math.floorDiv(minZ, MEGA_HALL_CELL_BLOCKS);
        int maxSZ = Math.floorDiv(maxZ, MEGA_HALL_CELL_BLOCKS);
        for (int sz = minSZ; sz <= maxSZ; sz++) {
            for (int sx = minSX; sx <= maxSX; sx++) {
                CaveMegaHall hall = megaHallForSupercell(sx, sz, seed);
                if (hall == null) {
                    continue;
                }
                if (maxX < hall.minX - 16 || minX > hall.maxX + 16
                    || maxZ < hall.minZ - 16 || minZ > hall.maxZ + 16) {
                    continue;
                }
                if (segmentIntersectsHall(xs, ys, zs, rs, hall, seed)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 圆盘判定：采样点中心或边界点落入洞厅形状即视为穿过（不漏光）。 */
    private static boolean segmentIntersectsHall(float[] xs, float[] ys,
                                                 float[] zs, float[] rs,
                                                 CaveMegaHall hall,
                                                 long seed) {
        for (int i = 0; i < xs.length - 1; i++) {
            double len = Math.sqrt(
                (xs[i + 1] - xs[i]) * (xs[i + 1] - xs[i])
                    + (ys[i + 1] - ys[i]) * (ys[i + 1] - ys[i])
                    + (zs[i + 1] - zs[i]) * (zs[i + 1] - zs[i]));
            int samples = Math.max(2, (int) (len / 8.0) + 1);
            for (int k = 0; k <= samples; k++) {
                double t = (double) k / samples;
                double x = xs[i] + (xs[i + 1] - xs[i]) * t;
                double y = ys[i] + (ys[i + 1] - ys[i]) * t;
                double z = zs[i] + (zs[i + 1] - zs[i]) * t;
                if (y < hall.minY - 4.0 || y > hall.maxY + 4.0) {
                    continue;
                }
                double r = rs[i] + (rs[i + 1] - rs[i]) * t;
                if (hall.insideHorizontal(x, z)) {
                    return true;
                }
                double reach = r + 3.0;
                for (int dir = 0; dir < 12; dir++) {
                    double a = dir * (2.0 * Math.PI / 12);
                    if (hall.insideHorizontal(
                        x + Math.cos(a) * reach, z + Math.sin(a) * reach)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 路径级检查：干洞折线（含隧道半径）是否在盆地 / 禁干带内
     * 下探到上层带（DRY_UPPER_MIN_Y）以下。
     */
    private static boolean dipsIntoShallowZone(float[] xs, float[] ys,
                                               float[] zs, float[] rs,
                                               long seed) {
        for (int i = 0; i < xs.length - 1; i++) {
            double len = Math.sqrt(
                (xs[i + 1] - xs[i]) * (xs[i + 1] - xs[i])
                    + (ys[i + 1] - ys[i]) * (ys[i + 1] - ys[i])
                    + (zs[i + 1] - zs[i]) * (zs[i + 1] - zs[i]));
            int samples = Math.max(2, (int) (len / 8.0) + 1);
            for (int k = 0; k <= samples; k++) {
                double t = (double) k / samples;
                double x = xs[i] + (xs[i + 1] - xs[i]) * t;
                double y = ys[i] + (ys[i + 1] - ys[i]) * t;
                double z = zs[i] + (zs[i + 1] - zs[i]) * t;
                int cellX = Math.floorDiv((int) Math.floor(x), CELL_BLOCKS);
                int cellZ = Math.floorDiv((int) Math.floor(z), CELL_BLOCKS);
                double r = rs[i] + (rs[i + 1] - rs[i]) * t;
                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = cellX + dx;
                        int nz = cellZ + dz;
                        if (isHallZoneCell(nx, nz, seed)) {
                            continue;
                        }
                        if (!isShallowOnlyCell(nx, nz, seed)) {
                            continue;
                        }
                        double cellMinX = nx * CELL_BLOCKS;
                        double cellMaxX = cellMinX + CELL_BLOCKS;
                        double cellMinZ = nz * CELL_BLOCKS;
                        double cellMaxZ = cellMinZ + CELL_BLOCKS;
                        double closestX = Math.max(
                            cellMinX, Math.min(x, cellMaxX));
                        double closestZ = Math.max(
                            cellMinZ, Math.min(z, cellMaxZ));
                        double d = Math.sqrt(
                            (x - closestX) * (x - closestX)
                                + (z - closestZ) * (z - closestZ));
                        if (d <= r + 1.5
                            && y - r - 1.5 < DRY_UPPER_MIN_Y) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static double baseRadius(int ka, int kb) {
        if (isAquiferKind(ka) || isAquiferKind(kb)) {
            return 6.0;
        }
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
                                               Map<Long, List<CaveSegment>> edgeCache,
                                               Map<Long, List<CaveNode>> aquiferNodeCache,
                                               Map<Long, List<CaveSegment>> aquiferEdgeCache) {
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

        // 含水网络（第二阶段）：干洞已完整，生成暗河并避让干洞
        collectAquiferSegmentsForChunk(
            chunkX, chunkZ, seed,
            nodeCache, edgeCache, aquiferNodeCache, aquiferEdgeCache,
            segments, seenEdges);

        // 含水-湖泊连接管：大厅湖 / 洞厅湖（独立收集，保证长管全覆盖）
        collectLakePipesForChunk(
            chunkX, chunkZ, seed,
            nodeCache, edgeCache, aquiferNodeCache,
            segments, seenEdges);

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
                    CaveSegment seg = buildSegment(node, target, seed);
                    if (seg != null) {
                        out.add(seg);
                    }
                }
            }
            return;
        }

        if (node.kind == CaveNode.KIND_SHAFT) {
            CaveNode twin = findNodeById(node.twinId, node.cellX, node.cellZ,
                seed, nodeCache);
            if (twin != null) {
                CaveSegment seg = buildSegment(node, twin, seed);
                if (seg != null) {
                    out.add(seg);
                }
            }
            return;
        }

        // 普通 / 大厅 / 入口 / 天坑 / 洞厅：连接邻域最近节点（优先跨单元）。
        // 被盆地/禁干带/洞厅避让检查拒绝的候选直接跳过并继续找下一个。
        // 洞厅是超大节点，搜索半径放宽到 4 单元；其余节点保持 2 单元，成本低。
        int radius = node.kind == CaveNode.KIND_MEGA_HALL ? 4 : 2;
        int maxLinks = node.isEntranceLike() ? 2 : 3;
        int maxTries = node.isEntranceLike() ? 8 : 6;
        long exclude = -1;
        int added = 0;
        for (int i = 0; i < maxTries && added < maxLinks; i++) {
            CaveNode target = nearestNode(
                node, seed, nodeCache, exclude, radius,
                node.kind == CaveNode.KIND_MEGA_HALL);
            if (target == null) {
                break;
            }
            exclude = target.id;
            CaveSegment seg = buildSegment(node, target, seed);
            if (seg != null) {
                out.add(seg);
                added++;
            }
        }
    }

    private static CaveNode nearestNode(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        long excludeId) {
        return nearestNode(node, seed, nodeCache, excludeId, 2, false);
    }

    private static CaveNode nearestNode(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        long excludeId, int radius) {
        return nearestNode(node, seed, nodeCache, excludeId, radius, false);
    }

    private static CaveNode nearestNode(CaveNode node, long seed,
                                        Map<Long, List<CaveNode>> nodeCache,
                                        long excludeId, int radius,
                                        boolean preferLowY) {
        CaveNode best = null;
        double bestD = Double.POSITIVE_INFINITY;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (CaveNode n : nodesOf(
                    node.cellX + dx, node.cellZ + dz, seed, nodeCache)) {
                    if (n.id == node.id || n.id == excludeId) {
                        continue;
                    }
                    if (isAquiferKind(n.kind)) {
                        continue;
                    }
                    double d = distSq(node, n);
                    if (preferLowY && n.y > DRY_UPPER_MIN_Y) {
                        d += 90000.0;
                    }
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
                        if (isAquiferKind(n.kind)) {
                            continue;
                        }
                        double d = distSq(node, n);
                        if (preferLowY && n.y > DRY_UPPER_MIN_Y) {
                            d += 90000.0;
                        }
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
