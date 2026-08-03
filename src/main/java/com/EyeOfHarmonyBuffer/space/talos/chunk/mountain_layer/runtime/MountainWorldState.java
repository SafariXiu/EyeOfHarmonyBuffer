package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单个世界的山地状态：条带索引 + 山带缓存 + 单元格扫描状态。
 *
 * 线程安全：
 *   - beltIndex / belts / scannedTiles 全部用 ConcurrentHashMap；
 *   - 底层 WorldgenAPI / TectonicWorld 缓存已改为并发容器（见 WorldgenAPI、
 *     TectonicWorld），因此后台预构建线程可以安全调用 sampleFull；
 *   - 重复构建由 putIfAbsent 去重（同 beltId 结果确定，谁赢都一样）。
 */
public final class MountainWorldState {

    /** 构造风格采样单元边长（blocks），与 TectonicStyleLayer 一致。 */
    public static final int CELL_BLOCKS = 64;

    /** 扫描 tile 边长（blocks），与 TectonicStyleLayer.TILE_SIZE 一致。 */
    public static final int TILE_BLOCKS = 1024;

    /** 网格单轴上限（防极端条带撑爆内存）。 */
    private static final int MAX_GRID = 2048;

    /** DLA 聚集体占条带网格比例：0.25 保证山带内脊线足够密。 */
    private static final double DLA_TARGET_FILL = 0.25;

    /** 多锚点播种数量（沿走向的并行脊系）。 */
    private static final int DLA_ANCHORS = 3;

    /** 山带蒙版模糊半径（格）：拉宽边缘过渡带（约 5~6 格 ≈ 350~450 blocks）。 */
    private static final double MASK_BLUR_RADIUS = 3.5;

    public final int worldSeedInt;

    /** 世界 64 格 -> beltId。 */
    private final ConcurrentHashMap<Long, Long> beltIndex =
        new ConcurrentHashMap<Long, Long>();

    /** beltId -> 不可变山带。 */
    private final ConcurrentHashMap<Long, MountainBelt> belts =
        new ConcurrentHashMap<Long, MountainBelt>();

    /** 已扫描的 1024 tile（后台预构建去重用）。 */
    private final ConcurrentHashMap<Long, Boolean> scannedTiles =
        new ConcurrentHashMap<Long, Boolean>();

    /**
     * 64 格样式层级缓存（0=非山地，1/2/3=山地层级）。
     * 避免区块生成每列重复 sampleFull；超过上限整体清空重采。
     */
    private final ConcurrentHashMap<Long, Integer> styleCache =
        new ConcurrentHashMap<Long, Integer>();

    /** 正在发现中的起始格（防主线程/后台线程重复 BFS 同一条带）。 */
    private final ConcurrentHashMap<Long, Boolean> discoveryPending =
        new ConcurrentHashMap<Long, Boolean>();

    private static final int STYLE_CACHE_LIMIT = 1_000_000;

    public MountainWorldState(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    // ------------------------------------------------------------
    // 对外查询
    // ------------------------------------------------------------

    /** 查询 (worldX, worldZ) 的山地结构高度 01（0 = 无山）。 */
    public double sampleMountainHeight01(int worldX, int worldZ) {
        MountainBelt belt = beltAt(worldX, worldZ);
        return belt != null ? belt.sample01(worldX, worldZ) : 0.0;
    }

    /** 查询所在山带；未索引时同步兜底发现（首个区块可能小卡，后台预构建消除）。 */
    public MountainBelt beltAt(int worldX, int worldZ) {
        int cellX = Math.floorDiv(worldX, CELL_BLOCKS);
        int cellZ = Math.floorDiv(worldZ, CELL_BLOCKS);
        Long id = beltIndex.get(cellKey(cellX, cellZ));
        if (id != null) {
            MountainBelt b = belts.get(id);
            if (b != null) {
                return b;
            }
        }

        // 过渡带：该点不在任何分量 64 格内，但可能落在某条山带的
        // 模糊蒙版里（蒙版过渡带约 350~450 block）。
        // 不能在这里返回 null，否则 mask 会在 64 格边界（=区块边界）
        // 从 ~0.6 直接跳 0，造成严格区块对齐的硬切。
        for (MountainBelt b : belts.values()) {
            if (worldX >= b.minX && worldX <= b.maxX
                && worldZ >= b.minZ && worldZ <= b.maxZ) {
                if (b.sampleMask01(worldX, worldZ) > 0.001) {
                    return b;
                }
            }
        }
        return ensureBeltAtCell(cellX, cellZ);
    }

    /** 仅取山带蒙版（群系归属用；带外 0）。 */
    public double sampleMask01(int worldX, int worldZ) {
        MountainBelt belt = beltAt(worldX, worldZ);
        return belt != null ? belt.sampleMask01(worldX, worldZ) : 0.0;
    }

    // ------------------------------------------------------------
    // 调试信息（/talmountain 用）
    // ------------------------------------------------------------

    public int debugBeltCount() {
        return belts.size();
    }

    public int debugIndexedCellCount() {
        return beltIndex.size();
    }

    public int debugStyleCacheSize() {
        return styleCache.size();
    }

    public int debugScannedTileCount() {
        return scannedTiles.size();
    }

    public int debugStyleTier(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, CELL_BLOCKS);
        int cz = Math.floorDiv(worldZ, CELL_BLOCKS);
        return styleTierAt(cx, cz);
    }

    public java.util.List<MountainBelt> debugBelts() {
        return new java.util.ArrayList<MountainBelt>(belts.values());
    }

    // ------------------------------------------------------------
    // 条带发现 / 构建
    // ------------------------------------------------------------

    /**
     * 同步兜底：若该格属于山地构造风格，则完整发现其所在条带并构建。
     * 非山地格返回 null（不索引）。
     */
    public MountainBelt ensureBeltAtCell(int cellX, int cellZ) {
        if (styleTierAt(cellX, cellZ) == 0) {
            return null;
        }

        long startKey = cellKey(cellX, cellZ);
        Long indexed = beltIndex.get(startKey);
        if (indexed != null) {
            return belts.get(indexed);
        }

        // 已有线程正在发现：直接返回（该线程完成后索引会立即可见）
        if (discoveryPending.putIfAbsent(startKey, Boolean.TRUE) != null) {
            return null;
        }

        try {
            indexed = beltIndex.get(startKey);
            if (indexed != null) {
                return belts.get(indexed);
            }

            ComponentResult comp = floodComponent(cellX, cellZ);
            if (comp.cells.length == 0) {
                return null;
            }

            long beltId = canonicalBeltId(comp.cells);
            MountainBelt existing = belts.get(beltId);
            if (existing != null) {
                indexCells(comp.cells, beltId);
                return existing;
            }

            BeltMeta meta = beltMeta(comp.cells);
            MountainBelt built = buildBelt(
                beltId, meta, comp.cells, comp.maxTier
            );
            MountainBelt winner = belts.putIfAbsent(beltId, built);
            if (winner != null) {
                built = winner;
            }
            indexCells(comp.cells, beltId);
            return built;
        } finally {
            discoveryPending.remove(startKey);
        }
    }

    /** 后台预构建：扫描一个 1024 tile 内所有 64 格。 */
    public void scanTile(int tileX, int tileZ) {
        long tkey = tileKey(tileX, tileZ);
        if (scannedTiles.putIfAbsent(tkey, Boolean.TRUE) != null) {
            return;
        }

        int baseX = tileX * TILE_BLOCKS;
        int baseZ = tileZ * TILE_BLOCKS;
        for (int gz = 0; gz < TILE_BLOCKS / CELL_BLOCKS; gz++) {
            int worldZ = baseZ + gz * CELL_BLOCKS + CELL_BLOCKS / 2;
            int cz = Math.floorDiv(worldZ, CELL_BLOCKS);
            for (int gx = 0; gx < TILE_BLOCKS / CELL_BLOCKS; gx++) {
                int worldX = baseX + gx * CELL_BLOCKS + CELL_BLOCKS / 2;
                int cx = Math.floorDiv(worldX, CELL_BLOCKS);
                if (beltIndex.containsKey(cellKey(cx, cz))) {
                    continue;
                }
                if (styleTierAt(cx, cz) != 0) {
                    ensureBeltAtCell(cx, cz);
                }
            }
        }
    }

    /**
     * 世界加载时同步预构建出生点附近的山带（小范围、快），
     * 保证生成出生区块时不会在主线程触发整条带的 BFS 发现。
     */
    public void prebuildAroundSpawn(int worldX, int worldZ) {
        int centerTileX = Math.floorDiv(worldX, TILE_BLOCKS);
        int centerTileZ = Math.floorDiv(worldZ, TILE_BLOCKS);
        for (int r = 0; r <= 2; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    scanTile(centerTileX + dx, centerTileZ + dz);
                }
            }
        }
    }

    // ------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------

    /**
     * 构造风格判定（与 TectonicStyleLayer.sampleGrid 同规则）：
     * 返回 0=非山地，1=HIGHLAND，2=MOUNTAINS，3=PEAK。
     */
    private int styleTierAt(int cellX, int cellZ) {
        long key = cellKey(cellX, cellZ);
        Integer cached = styleCache.get(key);
        if (cached != null) {
            return cached;
        }
        int tier = computeStyleTier(cellX, cellZ);
        if (styleCache.size() > STYLE_CACHE_LIMIT) {
            styleCache.clear();
        }
        styleCache.put(key, tier);
        return tier;
    }

    private int computeStyleTier(int cellX, int cellZ) {
        int worldX = cellX * CELL_BLOCKS + CELL_BLOCKS / 2;
        int worldZ = cellZ * CELL_BLOCKS + CELL_BLOCKS / 2;
        TalosLandMask.Sample s = TalosLandMask.sampleFull(
            worldX, worldZ, worldSeedInt
        );
        if (s == null || !s.isLand) {
            return 0;
        }
        double div = TalosLandMask.maxBoundaryStrength(
            PlateBoundaryState.DIVERGENT, s
        );
        if (div >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return 0; // 裂谷
        }
        if (s.plateBoundaryState != PlateBoundaryState.CONVERGENT) {
            return 0;
        }
        double w = s.plateBoundaryWeight;
        if (w >= 0.7) {
            return 3; // PEAK
        }
        if (w > 0.5) {
            return 2; // MOUNTAINS
        }
        if (w >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return 1; // HIGHLAND
        }
        return 0;
    }

    private static final class ComponentResult {
        final long[] cells;
        final int maxTier;

        ComponentResult(long[] cells, int maxTier) {
            this.cells = cells;
            this.maxTier = maxTier;
        }
    }

    /** BFS 全连通分量（跨 tile），返回分量内全部 64 格（打包 key）+ 最高层级。 */
    private ComponentResult floodComponent(int startCellX, int startCellZ) {
        HashMap<Long, Integer> styleCache = new HashMap<Long, Integer>();
        HashSet<Long> visited = new HashSet<Long>();
        ArrayList<Long> cells = new ArrayList<Long>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();

        long startKey = cellKey(startCellX, startCellZ);
        int startTier = styleTierAt(startCellX, startCellZ);
        styleCache.put(startKey, startTier);
        visited.add(startKey);
        queue.add(startKey);
        cells.add(startKey);
        int maxTier = startTier;

        while (!queue.isEmpty()) {
            long key = queue.poll();
            int cx = (int) (key >> 32);
            int cz = (int) (key & 0xffffffffL);

            int[][] dirs = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1}
            };
            for (int[] d : dirs) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long nk = cellKey(nx, nz);
                if (visited.contains(nk)) {
                    continue;
                }
                Integer st = styleCache.get(nk);
                if (st == null) {
                    st = styleTierAt(nx, nz);
                    styleCache.put(nk, st);
                }
                if (st == null || st == 0) {
                    continue;
                }
                if (st > maxTier) {
                    maxTier = st;
                }
                visited.add(nk);
                queue.add(nk);
                cells.add(nk);
            }
        }

        long[] out = new long[cells.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cells.get(i);
        }
        return new ComponentResult(out, maxTier);
    }

    /** 条带 ID：分量内字典序最小格 + 世界种子混合（与发现顺序无关）。 */
    private long canonicalBeltId(long[] cells) {
        long min = Long.MAX_VALUE;
        for (long c : cells) {
            if (c < min) {
                min = c;
            }
        }
        long h = min * 0x9E3779B97F4A7C15L ^ worldSeedInt;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    private static final class BeltMeta {
        double centerX;
        double centerZ;
        double angleRad;
        double halfLength;
        double halfWidth;
        double minX;
        double minZ;
        double maxX;
        double maxZ;
    }

    /** 分量 -> 几何：中心 / PCA 主轴 / 半长半宽 / 世界包围盒。 */
    private BeltMeta beltMeta(long[] cells) {
        int n = cells.length;
        double sx = 0.0;
        double sz = 0.0;
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (long c : cells) {
            int cx = (int) (c >> 32);
            int cz = (int) (c & 0xffffffffL);
            double wx = cx * CELL_BLOCKS + CELL_BLOCKS / 2.0;
            double wz = cz * CELL_BLOCKS + CELL_BLOCKS / 2.0;
            sx += wx;
            sz += wz;
            minX = Math.min(minX, cx * CELL_BLOCKS);
            minZ = Math.min(minZ, cz * CELL_BLOCKS);
            maxX = Math.max(maxX, cx * CELL_BLOCKS + CELL_BLOCKS);
            maxZ = Math.max(maxZ, cz * CELL_BLOCKS + CELL_BLOCKS);
        }

        double cx0 = sx / n;
        double cz0 = sz / n;

        double sxx = 0.0;
        double szz = 0.0;
        double sxz = 0.0;
        for (long c : cells) {
            int cx = (int) (c >> 32);
            int cz = (int) (c & 0xffffffffL);
            double wx = cx * CELL_BLOCKS + CELL_BLOCKS / 2.0 - cx0;
            double wz = cz * CELL_BLOCKS + CELL_BLOCKS / 2.0 - cz0;
            sxx += wx * wx;
            szz += wz * wz;
            sxz += wx * wz;
        }

        double angle = 0.5 * Math.atan2(2.0 * sxz, sxx - szz);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);

        double minAlong = Double.POSITIVE_INFINITY;
        double maxAlong = Double.NEGATIVE_INFINITY;
        double minCross = Double.POSITIVE_INFINITY;
        double maxCross = Double.NEGATIVE_INFINITY;
        for (long c : cells) {
            int cx = (int) (c >> 32);
            int cz = (int) (c & 0xffffffffL);
            double wx = cx * CELL_BLOCKS + CELL_BLOCKS / 2.0 - cx0;
            double wz = cz * CELL_BLOCKS + CELL_BLOCKS / 2.0 - cz0;
            double along = wx * cos + wz * sin;
            double cross = -wx * sin + wz * cos;
            minAlong = Math.min(minAlong, along);
            maxAlong = Math.max(maxAlong, along);
            minCross = Math.min(minCross, cross);
            maxCross = Math.max(maxCross, cross);
        }

        BeltMeta meta = new BeltMeta();
        meta.centerX = cx0;
        meta.centerZ = cz0;
        meta.angleRad = angle;
        meta.halfLength = (maxAlong - minAlong) / 2.0 + CELL_BLOCKS;
        meta.halfWidth = (maxCross - minCross) / 2.0 + CELL_BLOCKS;
        meta.minX = minX;
        meta.minZ = minZ;
        meta.maxX = maxX;
        meta.maxZ = maxZ;
        return meta;
    }

    private MountainBelt buildBelt(long beltId, BeltMeta meta, long[] cells,
                                   int kind) {
        int gridW = clamp(
            (int) Math.ceil(2.0 * meta.halfWidth / CELL_BLOCKS), 8, MAX_GRID
        );
        int gridH = clamp(
            (int) Math.ceil(2.0 * meta.halfLength / CELL_BLOCKS), 8, MAX_GRID
        );

        long seed = beltId ^ (worldSeedInt * 0x9E3779B97F4A7C15L);
        long buildT0 = System.nanoTime();
        DlaMountainGenerator.Result r = DlaMountainGenerator.generate(
            gridW, gridH, seed,
            DLA_TARGET_FILL,
            DlaMountainGenerator.DEFAULT_DRIFT,
            DlaMountainGenerator.DEFAULT_LATERAL,
            DlaMountainGenerator.DEFAULT_MAX_STEPS,
            DlaMountainGenerator.DEFAULT_BLUR,
            DlaMountainGenerator.DEFAULT_PROFILE_AMP,
            DLA_ANCHORS
        );

        // 单元格蒙版：分量格 -> 网格位置（1），其余 0
        float[] mask = new float[gridW * gridH];
        for (int i = 0; i < mask.length; i++) {
            mask[i] = 0f;
        }
        double cos = Math.cos(meta.angleRad);
        double sin = Math.sin(meta.angleRad);
        for (long c : cells) {
            int cx = (int) (c >> 32);
            int cz = (int) (c & 0xffffffffL);
            double wx = cx * CELL_BLOCKS + CELL_BLOCKS / 2.0 - meta.centerX;
            double wz = cz * CELL_BLOCKS + CELL_BLOCKS / 2.0 - meta.centerZ;
            double along = wx * cos + wz * sin;
            double cross = -wx * sin + wz * cos;
            double u = (cross + meta.halfWidth) / (2.0 * meta.halfWidth);
            double v = (along + meta.halfLength) / (2.0 * meta.halfLength);
            if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
                continue;
            }
            int gx = (int) Math.round(u * (gridW - 1));
            int gy = (int) Math.round(v * (gridH - 1));
            mask[gy * gridW + gx] = 1f;
        }

        // 拉宽边缘过渡带：蒙版模糊（约 200~256 blocks）
        float[] blurredMask = DlaMountainGenerator.blur01(
            mask, gridW, gridH, MASK_BLUR_RADIUS
        );
        System.out.printf(
            "[MOUNTAIN] belt %d grid=%dx%d cells=%d built in %.1f ms%n",
            beltId, gridW, gridH, cells.length,
            (System.nanoTime() - buildT0) / 1e6
        );

        return new MountainBelt(
            beltId,
            CELL_BLOCKS,
            gridW,
            gridH,
            r.elevation01,
            blurredMask,
            meta.centerX,
            meta.centerZ,
            meta.angleRad,
            meta.halfLength,
            meta.halfWidth,
            meta.minX,
            meta.minZ,
            meta.maxX,
            meta.maxZ,
            kind
        );
    }

    private void indexCells(long[] cells, long beltId) {
        for (long c : cells) {
            beltIndex.put(c, beltId);
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xffffffffL);
    }

    private static long tileKey(int tileX, int tileZ) {
        return (((long) tileX) << 32) ^ (tileZ & 0xffffffffL);
    }
}
