package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

/**
 * F2 盆地几何提取器（新，默认未被生产调用）。
 *
 * <p>与旧的 {@link BasinFinder} 正方形掩膜不同：本类按行海岸区间扩展，
 * 不做 O(R^2) 全方阵，因此可以覆盖数万 km 的盆地。
 *
 * <p>ADR-001：不假设 z 周期；所有坐标用原始 z。
 */
public final class BasinGeometry {

    public final boolean valid;
    /** 搜索窗口被撑满；不等于“闭合/开边界”的最终判定。 */
    public final boolean openBoundary;
    /** bbox 四周粗采样全是陆地：可作为闭合盆地的必要条件。 */
    public final boolean closed;
    /** 开边 bitmask：W=1,E=2,S=4,N=8；0=闭合。 */
    public final int openEdges;
    public static final int EDGE_W = 1, EDGE_E = 2, EDGE_S = 4, EDGE_N = 8;
    public final int step;
    public final int minX, maxX, minZ, maxZ;
    public final int rows;
    public final double widthKm, heightKm;

    private BasinGeometry(boolean valid, boolean openBoundary, boolean closed, int openEdges, int step,
                          int minX, int maxX, int minZ, int maxZ, int rows) {
        this.valid = valid;
        this.openBoundary = openBoundary;
        this.closed = closed;
        this.openEdges = openEdges;
        this.step = step;
        this.minX = minX; this.maxX = maxX;
        this.minZ = minZ; this.maxZ = maxZ;
        this.rows = rows;
        this.widthKm = (maxX - minX) / 1000.0;
        this.heightKm = (maxZ - minZ) / 1000.0;
    }

    /**
     * @param maxSpan 单方向最大搜索距离（m），例如 40_000_000
     */
    public static BasinGeometry extract(int x, int z, long seed, int cell, int step, int maxSpan) {
        if (step <= 0 || maxSpan < step) throw new IllegalArgumentException("bad step/maxSpan");
        final int radius = Math.max(1, maxSpan / step);
        final int W = 2 * radius + 1;
        final int cx = radius, cz = radius;
        final int wallTol = Math.max(1, 60_000 / step);

        if (PlateField.isLandWithCell(x, z, seed, cell)) {
            return new BasinGeometry(false, false, false, 0, step, x, x, z, z, 0);
        }

        int w = cx, e = cx;
        boolean open = false;
        while (w > 0 && !PlateField.isLandWithCell(x + (w - 1 - cx) * step, z, seed, cell)) w--;
        while (e < W - 1 && !PlateField.isLandWithCell(x + (e + 1 - cx) * step, z, seed, cell)) e++;
        if (w == 0 || e == W - 1) open = true;

        int uW = w, uE = e, minRow = cz, maxRow = cz, rows = 1;
        for (int dir = -1; dir <= 1; dir += 2) {
            int pw = w, pe = e;
            for (int s = 1; s <= radius; s++) {
                int r = cz + dir * s;
                if (r < 0 || r >= W) { open = true; break; }
                int zz = z + (r - cz) * step;
                int hit = -1;
                for (int q = pw; q <= pe; q++) {
                    if (!PlateField.isLandWithCell(x + (q - cx) * step, zz, seed, cell)) { hit = q; break; }
                }
                if (hit < 0) break;
                int nw = hit, ne = hit;
                while (nw > 0 && !PlateField.isLandWithCell(x + (nw - 1 - cx) * step, zz, seed, cell)) nw--;
                while (ne < W - 1 && !PlateField.isLandWithCell(x + (ne + 1 - cx) * step, zz, seed, cell)) ne++;
                if (Math.abs(nw - pw) > wallTol) break;
                if (nw == 0 || ne == W - 1) open = true;
                pw = nw; pe = ne;
                uW = Math.min(uW, nw); uE = Math.max(uE, ne);
                minRow = Math.min(minRow, r); maxRow = Math.max(maxRow, r);
                rows++;
            }
        }

        int minX = x + (uW - cx) * step;
        int maxX = x + (uE - cx) * step;
        int minZ = z + (minRow - cz) * step;
        int maxZ = z + (maxRow - cz) * step;
        int openEdges = perimeterOpenEdges(minX, maxX, minZ, maxZ, step, seed, cell);
        boolean closed = openEdges == 0;
        return new BasinGeometry(true, open, closed, openEdges, step, minX, maxX, minZ, maxZ, rows);
    }

    private static int perimeterOpenEdges(int minX, int maxX, int minZ, int maxZ,
                                           int step, long seed, int cell) {
        int e = 0;
        for (int x = minX; x <= maxX; x += step) {
            if (!PlateField.isLandWithCell(x, minZ, seed, cell)) e |= EDGE_S;
            if (!PlateField.isLandWithCell(x, maxZ, seed, cell)) e |= EDGE_N;
        }
        for (int z = minZ; z <= maxZ; z += step) {
            if (!PlateField.isLandWithCell(minX, z, seed, cell)) e |= EDGE_W;
            if (!PlateField.isLandWithCell(maxX, z, seed, cell)) e |= EDGE_E;
        }
        return e;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.ROOT,
            "BasinGeometry(valid=%b open=%b closed=%b edges=%d %.0f x %.0f km rows=%d step=%d)",
            valid, openBoundary, closed, openEdges, widthKm, heightKm, rows, step);
    }
}
