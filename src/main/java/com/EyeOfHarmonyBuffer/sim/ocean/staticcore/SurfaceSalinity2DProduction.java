package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;

/**
 * 生产查询适配：把 80M×40M 的 SurfaceSalinity2D 区域解按点插值。
 * 默认 off；只有显式打开且 A/B 通过后，才允许 OceanField 使用。
 */
public final class SurfaceSalinity2DProduction {

    public static boolean ON = true;
    private static Region result;

    private SurfaceSalinity2DProduction() {}

    private static synchronized Region region(int worldSeed) {
        if (result == null || result.seed != worldSeed) {
            Atmosphere.suppressSst(true);
            try {
                SurfaceSalinity2D.RegionResult r =
                    SurfaceSalinity2D.solveRegion(worldSeed, 160000, 0, 0, 0.0, 80);
                result = new Region(worldSeed, r);
            } finally {
                Atmosphere.suppressSst(false);
            }
        }
        return result;
    }

    private static OceanGrid wetGrid;
    private static int wetSeed = Integer.MIN_VALUE;

    public static synchronized boolean isWetAt(int x, int z, int worldSeed) {
        if (wetGrid == null || wetSeed != worldSeed) {
            wetGrid = OceanGrid.landV3(worldSeed, 160000, 0, 0);
            wetSeed = worldSeed;
        }
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        int i = (int) Math.floor(xx / wetGrid.dx);
        int j = (int) Math.floor(zz / wetGrid.dy);
        return wetGrid.isWet(i, j);
    }

    public static double at(int x, int z, int worldSeed) {
        if (!ON) return -1.0;
        Region r = region(worldSeed);
        OceanGrid g = r.r.grid;
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        int i = (int) Math.floor(xx / g.dx);
        int j = (int) Math.floor(zz / g.dy);
        i = Math.max(0, Math.min(g.nx - 1, i));
        j = Math.max(1, Math.min(g.ny - 2, j));
        return r.r.state.s[r.r.state.idx(i, j, 0)];
    }

    public static synchronized double[] row(int zIdx, int worldSeed) {
        Region r = region(worldSeed);
        OceanGrid g = r.r.grid;
        int j = Math.max(1, Math.min(g.ny - 2, zIdx));
        double[] out = new double[g.nx];
        for (int i = 0; i < g.nx; i++) out[i] = r.r.state.s[r.r.state.idx(i, j, 0)];
        return out;
    }

    public static synchronized double budgetMax(int worldSeed) { return region(worldSeed).r.budgetMax; }
    public static synchronized double budgetMean(int worldSeed) { return region(worldSeed).r.budgetMean; }
    public static synchronized double saltDrift(int worldSeed) { return region(worldSeed).r.saltDrift; }

    private static final class Region {
        final int seed;
        final SurfaceSalinity2D.RegionResult r;
        Region(int s, SurfaceSalinity2D.RegionResult r) { seed = s; this.r = r; }
    }
}
