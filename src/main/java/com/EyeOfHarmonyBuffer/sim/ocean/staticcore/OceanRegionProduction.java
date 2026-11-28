package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing;

/** On-demand regional ocean solutions for the production accessors. */
public final class OceanRegionProduction {
    private OceanRegionProduction() {}

    private static final OceanRegionCache CACHE = new OceanRegionCache();
    static {
        CACHE.setLoader(new OceanRegionCache.Loader() {
            @Override public OceanRegionSolution load(OceanRegionKey key) {
                return compute(key);
            }
        });
    }

    private static OceanRegionSolution compute(OceanRegionKey key) {
        int nx = 24, ny = 24;
        double minX = OceanRegionGrid.patchMinX(key.regionX);
        double maxX = OceanRegionGrid.patchMaxX(key.regionX);
        double minZ = OceanRegionGrid.patchMinZ(key.regionZ);
        double maxZ = OceanRegionGrid.patchMaxZ(key.regionZ);
        OceanGrid g = OceanGrid.patch(key.worldSeed, StaticOceanProduction.CELL,
                (int) Math.round(minX), (int) Math.round(minZ), nx, ny,
                maxX - minX, maxZ - minZ, true);
        // ★ 2026-11-24（S0.3）：**不再翻转全局 REGION_ON**。原来整段求解（分钟级）里
        //   全局 REGION_ON=false ⟹ 其它线程（瓦片生成）读到的海洋采样静默退回粗父解，
        //   同坐标不同结果。现在用「只对本线程生效」的旁路：求解线程读父解，别的线程
        //   照常走 region（未命中就在缓存锁上等到解完）。
        StaticOceanProduction.beginRegionBypass();
        try {
            OceanSolution coarse = StaticOceanProduction.solution(key.worldSeed);
            GlobalBoxParent parent = new GlobalBoxParent(coarse);
            DiagnosticSeaSurfaceForcing f = new DiagnosticSeaSurfaceForcing(0, StaticOceanProduction.CELL, 12500);
            VelocityProjection.OPEN_X = true;
            OceanSponge.OPEN_X = true;
            OceanSolution ps = StaticOceanSolver.solve(g, key.worldSeed, f, 0.0, 1, 0, 5000, parent, 3);
            return new OceanRegionSolution(key, ps);
        } finally {
            StaticOceanProduction.endRegionBypass();
        }
    }

    public static OceanSolution solutionAt(int x, int z, int worldSeed) {
        OceanRegionKey key = OceanRegionGrid.keyAt(worldSeed, x, z, 1, 1, OceanRegionKey.QUALITY_FAST);
        OceanRegionSolution rs = CACHE.get(key);
        return rs.solution;
    }

    public static boolean wetAt(int x, int z, int worldSeed) {
        return cell(x, z, worldSeed) != null;
    }

    private static int[] cell(int x, int z, int worldSeed) {
        OceanSolution s = solutionAt(x, z, worldSeed);
        OceanGrid g = s.state.grid;
        int i = (int) Math.floor((x - g.x0) / g.dx);
        int j = (int) Math.floor((z - g.z0) / g.dy);
        if (i < 0 || i >= g.nx || j < 0 || j >= g.ny || !g.isWet(i, j)) return null;
        return new int[]{i, j};
    }

    public static double atT(int x, int z, int worldSeed) {
        int[] c = cell(x, z, worldSeed);
        if (c == null) return Double.NaN;
        OceanSolution s = solutionAt(x, z, worldSeed);
        return s.surfaceT[c[1] * s.state.grid.nx + c[0]];
    }

    public static double atS(int x, int z, int worldSeed) {
        int[] c = cell(x, z, worldSeed);
        if (c == null) return Double.NaN;
        OceanSolution s = solutionAt(x, z, worldSeed);
        return s.state.s[s.state.idx(c[0], c[1], 0)];
    }

    public static double atU(int x, int z, int worldSeed) {
        int[] c = cell(x, z, worldSeed);
        if (c == null) return Double.NaN;
        OceanSolution s = solutionAt(x, z, worldSeed);
        return s.state.u[s.state.idx(c[0], c[1], 0)];
    }

    public static double atV(int x, int z, int worldSeed) {
        int[] c = cell(x, z, worldSeed);
        if (c == null) return Double.NaN;
        OceanSolution s = solutionAt(x, z, worldSeed);
        return s.state.v[s.state.idx(c[0], c[1], 0)];
    }

    public static double iceAt(int x, int z, int worldSeed, boolean thickness) {
        int[] c = cell(x, z, worldSeed);
        if (c == null) return 0.0;
        OceanSolution s = solutionAt(x, z, worldSeed);
        int js = c[1] * s.state.grid.nx + c[0];
        return thickness ? s.state.iceThickness[js] : s.state.iceFraction[js];
    }

    public static int size() { return CACHE.size(); }
}