package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** 生产 2D 温度区域缓存（B）。 */
public final class SurfaceTemperature2DProduction {
    private SurfaceTemperature2DProduction() {}

    public static boolean ON = true;
    private static SurfaceTemperature2D.Result region;
    private static int seed = Integer.MIN_VALUE;

    public static synchronized SurfaceTemperature2D.Result region(int worldSeed) {
        if (region == null || seed != worldSeed) {
            region = SurfaceTemperature2D.solveRegion(worldSeed, 160000, 0, 0, 0.0, 20);
            seed = worldSeed;
        }
        return region;
    }

    public static synchronized double at(int x, int z, int worldSeed) {
        SurfaceTemperature2D.Result r = region(worldSeed);
        OceanGrid g = r.grid;
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        int i = (int) Math.floor(xx / g.dx);
        int j = (int) Math.floor(zz / g.dy);
        i = Math.max(0, Math.min(g.nx - 1, i));
        j = Math.max(1, Math.min(g.ny - 2, j));
        if (!g.isWet(i, j)) return Double.NaN;
        return r.state.t[r.state.idx(i, j, 0)];
    }
}
