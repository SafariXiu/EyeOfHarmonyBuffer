package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.ocean.ClimberOcean;

/** 混合层 hT 隐式迎风平流：aP hT = aW hT_W + ... + rhs，SOR 求解。 */
public final class MixedLayerSolver {
    private MixedLayerSolver() {}
    public static void solve(OceanGrid g, OceanState st, double[] surfaceT,
                             SeaSurfaceForcing f, double dt) {
        int nx = g.nx, ny = g.ny;
        double[] hT = new double[nx * ny];
        double[] rhs = new double[nx * ny];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double h = Math.max(1.0, st.mixedLayerDepth[js]);
                double ts = surfaceT[js];
                double q = f.netHeatFluxWm2((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), 0.0, ts, ClimberOcean.iceFraction(ts));
                double fw = f.freshwaterFluxKgM2S((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), 0.0, ts);
                if (!Double.isFinite(q)) q = 0.0;
                hT[js] = h * ts;
                rhs[js] = h * ts + dt * (q / (1027.8 * 3985.0)
                        + fw / 1027.8 * st.t[st.idx(i, j, 1)]);
            }
        for (int iter = 0; iter < 400; iter++) {
            double maxd = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int js = j * nx + i;
                    int iw = (i - 1 + nx) % nx, ie = (i + 1) % nx;
                    int jw = Math.max(1, j - 1), je = Math.min(ny - 2, j + 1);
                    double u = st.u[st.idx(i, j, 0)], v = st.v[st.idx(i, j, 0)];
                    double aW = dt * Math.max(u, 0.0) / g.dx;
                    double aE = dt * Math.max(-u, 0.0) / g.dx;
                    double aS = dt * Math.max(v, 0.0) / g.dy;
                    double aN = dt * Math.max(-v, 0.0) / g.dy;
                    double aP = 1.0 + aW + aE + aS + aN;
                    double num = rhs[js] + aW * hT[j * nx + iw] + aE * hT[j * nx + ie]
                               + aS * hT[jw * nx + i] + aN * hT[je * nx + i];
                    double nv = num / aP;
                    double hh = Math.max(1.0, st.mixedLayerDepth[js]);
                    nv = Math.max(250.0 * hh, Math.min(330.0 * hh, nv));
                    double d = Math.abs(nv - hT[js]);
                    if (d > maxd) maxd = d;
                    hT[js] = hT[js] + 1.2 * (nv - hT[js]);
                }
            if (maxd < 1.0e-4) break;
        }
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double h = Math.max(1.0, st.mixedLayerDepth[js]);
                double tt = hT[js] / h;
                surfaceT[js] = Double.isFinite(tt) ? Math.max(250.0, Math.min(330.0, tt)) : surfaceT[js];
            }
    }
}
