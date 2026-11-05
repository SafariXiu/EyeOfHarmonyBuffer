package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * 2D 表层盐度输运的可复用入口。
 *
 * <p>当前复用 {@link BiCGSTABS} 的 hS 稳态离散（平流 + 扩散 + E-P + 冰反照率/盐析由
 * SurfaceTemperatureSolver/Prognostic 提供）；不复制公式，避免两套口径。
 * 仅作为“OceanField 之外”的 2D 求解入口，尚未接生产反馈。
 */
public final class SurfaceSalinity2D {

    private SurfaceSalinity2D() {}

    public static double solve(OceanGrid g, OceanState st, SeaSurfaceForcing f,
                               double theta, int maxIter) {
        return BiCGSTABS.solve(g, st, f, theta, maxIter, 1.0e-6);
    }

    public static final class RegionResult {
        public final OceanGrid grid;
        public final OceanState state;
        public final double residual;
        public final double sMin, sMax, sCenter;
        public final double budgetMax, budgetMean, saltDrift;
        RegionResult(OceanGrid g, OceanState st, double res, double mn, double mx, double sc,
                     double bmax, double bmean, double drift) {
            grid = g; state = st; residual = res; sMin = mn; sMax = mx; sCenter = sc;
            budgetMax = bmax; budgetMean = bmean; saltDrift = drift;
        }
    }

    public static RegionResult solveRegion(int worldSeed, int cell, int x0, int z0,
                                           double theta, int maxIter) {
        OceanGrid g = OceanGrid.landV3(worldSeed, cell, x0, z0);
        OceanState st = new OceanState(g);
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++) {
                    int q = st.idx(i, j, k);
                    st.t[q] = 290.0;
                    st.s[q] = 35.0;
                }
                st.eta[j * g.nx + i] = 0.0;
            }
        com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing f =
            new com.EyeOfHarmonyBuffer.sim.ocean.DiagnosticSeaSurfaceForcing(worldSeed, cell, 1000);
        BarotropicSolver.Result baro = BarotropicSolver.solve(g, f, theta);
        for (int q = 0; q < st.u.length; q++) { st.u[q] = baro.state.u[q]; st.v[q] = baro.state.v[q]; }
        System.arraycopy(baro.state.eta, 0, st.eta, 0, st.eta.length);
        PrognosticSurfaceSalinitySolver.Result pr =
            PrognosticSurfaceSalinitySolver.solveDetailed(g, st, f, theta, maxIter);
        double res = pr.residual;
        double mn = 1e9, mx = -1e9;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j)) {
                    double s = st.s[st.idx(i, j, 0)];
                    mn = Math.min(mn, s); mx = Math.max(mx, s);
                }
        double sc = st.s[st.idx(g.nx / 2, g.ny / 2, 0)];
        return new RegionResult(g, st, res, mn, mx, sc, pr.budgetMax, pr.budgetMean, pr.saltDrift);
    }

    public static double at(OceanState st, int i, int j) {
        return st.s[st.idx(i, j, 0)];
    }
}
