package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 无限世界的区域格子（3D海洋-无限世界架构.md §3）。
 *
 * <p>原点对齐的固定 10,000 km 格子；halo 1,500 km 用于 sponge 与相邻区域混合。
 * 格子与【访问顺序】无关，因此同一种子的区域划分是确定的。
 * <p>这不是"全球箱"：x/z 上无限延伸，每个区域只看自己位置的真实 LandV3。
 */
public final class OceanRegionGrid {

    /** 区域边长（m）：10,000 km。覆盖盆地环流的记忆尺度。 */
    public static final double REGION_M = 10_000_000.0;
    /** halo 宽度（m）：1,500 km。sponge 目标尺度；相邻区域的重叠区。 */
    public static final double HALO_M = 1_500_000.0;

    private OceanRegionGrid() {}

    /** 点 x 所在的区域下标（向下取整，负坐标同样正确）。 */
    public static int regionX(double x) { return (int) Math.floor(x / REGION_M); }

    /** 点 z 所在的区域下标。 */
    public static int regionZ(double z) { return (int) Math.floor(z / REGION_M); }

    public static double originX(int ri) { return ri * REGION_M; }
    public static double originZ(int rj) { return rj * REGION_M; }

    /** 区域 + halo 的 patch 边界（含）。 */
    public static double patchMinX(int ri) { return originX(ri) - HALO_M; }
    public static double patchMaxX(int ri) { return originX(ri) + REGION_M + HALO_M; }
    public static double patchMinZ(int rj) { return originZ(rj) - HALO_M; }
    public static double patchMaxZ(int rj) { return originZ(rj) + REGION_M + HALO_M; }

    /** 点是否落在区域 ri 的 patch（含 halo）内。 */
    public static boolean inPatchX(double x, int ri) {
        return x >= patchMinX(ri) && x < patchMaxX(ri);
    }

    public static boolean inPatchZ(double z, int rj) {
        return z >= patchMinZ(rj) && z < patchMaxZ(rj);
    }

    /** 覆盖点 (x,z) 的核心区域 key。 */
    public static OceanRegionKey keyAt(int worldSeed, double x, double z,
                                       int landVersion, int solverVersion, int quality) {
        return new OceanRegionKey(worldSeed, regionX(x), regionZ(z),
            landVersion, solverVersion, quality);
    }

    /**
     * 点 (x,z) 在 halo 内还覆盖到哪些区域下标（含核心区域）。
     * <p>混合权重（架构 §2 的 w_r）在阶段 2 用这些下标；此处只给几何归属。
     */
    public static int[] coveringX(double x) {
        int lo = (int) Math.floor((x - HALO_M) / REGION_M) - 1;
        int hi = (int) Math.floor((x + HALO_M) / REGION_M) + 1;
        int[] tmp = new int[4];
        int n = 0;
        for (int ri = lo; ri <= hi && n < tmp.length; ri++) {
            if (inPatchX(x, ri)) tmp[n++] = ri;
        }
        int[] out = new int[n];
        System.arraycopy(tmp, 0, out, 0, n);
        return out;
    }
}
