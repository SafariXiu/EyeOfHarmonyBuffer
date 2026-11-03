package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/** 生产 3D 静态海洋缓存（B）。 */
public final class StaticOceanProduction {
    private StaticOceanProduction() {}

    public static boolean ON = true;
    private static OceanSolution sol;
    private static int seed = Integer.MIN_VALUE;

    public static synchronized void invalidate() { sol = null; seed = Integer.MIN_VALUE; }

    public static synchronized OceanSolution solution(int worldSeed) {
        if (sol == null || seed != worldSeed) {
            DiagnosticSeaSurfaceForcing f = new DiagnosticSeaSurfaceForcing(worldSeed, 160000, 12500);
            sol = StaticOceanSolver.solve(worldSeed, 160000, 0, 0, f, 0.0, 1, 0, 5000);
            seed = worldSeed;
        }
        return sol;
    }

    public static synchronized double atT(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.surfaceT[j * g.nx + i];
    }

    public static synchronized double atS(int x, int z, int worldSeed) {
        OceanSolution s = solution(worldSeed);
        OceanGrid g = s.state.grid;
        int i = indexX(g, x), j = indexZ(g, z);
        if (!g.isWet(i, j)) return Double.NaN;
        return s.state.s[s.state.idx(i, j, 0)];
    }

    public static double atAnomaly(int x, int z, int worldSeed) {
        double t = atT(x, z, worldSeed);
        if (!Double.isFinite(t)) return 0.0;
        int zz = z;
        return t - Atmosphere.seaSurfaceTemp(WorldContract.latOf(zz), 0.0);
    }

    private static int indexX(OceanGrid g, int x) {
        double xx = ((x % (long) OceanGrid.WIDTH_M) + OceanGrid.WIDTH_M) % OceanGrid.WIDTH_M;
        return Math.max(0, Math.min(g.nx - 1, (int) Math.floor(xx / g.dx)));
    }

    private static int indexZ(OceanGrid g, int z) {
        double zz = ((z % (long) OceanGrid.HEIGHT_M) + OceanGrid.HEIGHT_M) % OceanGrid.HEIGHT_M;
        return Math.max(1, Math.min(g.ny - 2, (int) Math.floor(zz / g.dy)));
    }
}
