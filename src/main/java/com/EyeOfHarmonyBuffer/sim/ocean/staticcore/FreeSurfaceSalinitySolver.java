package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * 局地自由水面盐度：每个水柱有自己的 h0 和 hS。
 *
 * <p>无限 x 世界没有全球盐量守恒；只要求局地：
 *   d(hS)/dt + ... = 0,  h = h0,  dh/dt = (P-E+runoff)/rho。
 * 本类用伪时间步推进表面层；不修改 StaticOceanSolver 的 psi。
 */
public final class FreeSurfaceSalinitySolver {

    public static final double DT = 86400.0;
    public static final double RHO = 1027.8;
    public static final double H_MIN = 1.0;

    private FreeSurfaceSalinitySolver() {}

    public static AdvectionDiffusionSolver.Result solve(OceanGrid g, OceanState st,
                                                        SeaSurfaceForcing f, double theta,
                                                        int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        double dz0 = g.dz[0];
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                if (st.surfaceLayer[q] <= 0.0) st.surfaceLayer[q] = dz0;
            }
        }
        int iter = 0;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int qs = j * nx + i;
                    int q0 = st.idx(i, j, 0);
                    int q1 = st.idx(i, j, 1);
                    double h = Math.max(H_MIN, dz0 + st.eta[qs]);
                    double m = st.s[q0] * h;
                    double fw = f.freshwaterFluxKgM2S(
                            (int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, st.t[q0]);
                    if (Double.isNaN(fw)) fw = 0.0;
                    // hS 方程的表面散度源：+淡水使 S 降低，-蒸发使 S 升高。
                    m -= st.s[q0] * (fw / RHO) * DT;
                    int qW = g.idx(i - 1, j), qE = g.idx(i + 1, j);
                    int qS = g.idx(i, j - 1), qN = g.idx(i, j + 1);
                    double hW = Math.max(H_MIN, dz0 + st.eta[qW]);
                    double hE = Math.max(H_MIN, dz0 + st.eta[qE]);
                    double hS = Math.max(H_MIN, dz0 + st.eta[qS]);
                    double hN = Math.max(H_MIN, dz0 + st.eta[qN]);
                    double sW = st.s[st.idx(i - 1, j, 0)];
                    double sE = st.s[st.idx(i + 1, j, 0)];
                    double sS = st.s[st.idx(i, j - 1, 0)];
                    double sN = st.s[st.idx(i, j + 1, 0)];
                    double u0 = st.u[q0], v0 = st.v[q0];
                    double fE = u0 >= 0 ? u0 * 0.5 * (h + hE) * st.s[q0] : u0 * 0.5 * (h + hE) * sE;
                    double fW = u0 >= 0 ? u0 * 0.5 * (h + hW) * sW : u0 * 0.5 * (h + hW) * st.s[q0];
                    double fN = v0 >= 0 ? v0 * 0.5 * (h + hN) * st.s[q0] : v0 * 0.5 * (h + hN) * sN;
                    double fS = v0 >= 0 ? v0 * 0.5 * (h + hS) * sS : v0 * 0.5 * (h + hS) * st.s[q0];
                    m -= ((fE - fW) / dx + (fN - fS) / dy) * DT;
                    double flux = AdvectionDiffusionSolver.KV * (st.s[q1] - st.s[q0]) / dz0;
                    m += flux * DT;
                    double s0 = m / h;
                    double old = st.s[q0];
                    st.s[q0] = s0;
                    st.surfaceLayer[qs] = h;
                    res = Math.max(res, Math.abs(s0 - old));
                }
            }
            for (int k = 1; k < nz; k++) {
                double dz = g.dz[k];
                double cv = AdvectionDiffusionSolver.KV / (dz * dz);
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int q = st.idx(i, j, k);
                        double s = st.s[q];
                        double sw = g.isWet(i - 1, j, k) ? st.s[st.idx(i - 1, j, k)] : s;
                        double se = g.isWet(i + 1, j, k) ? st.s[st.idx(i + 1, j, k)] : s;
                        double ss = g.isWet(i, j - 1, k) ? st.s[st.idx(i, j - 1, k)] : s;
                        double sn = g.isWet(i, j + 1, k) ? st.s[st.idx(i, j + 1, k)] : s;
                        double sup = st.s[st.idx(i, j, k - 1)];
                        double sdn = (k + 1 < nz) ? st.s[st.idx(i, j, k + 1)] : s;
                        double u = st.u[q], v = st.v[q];
                        double advX = u >= 0 ? u * (s - sw) / dx : u * (se - s) / dx;
                        double advY = v >= 0 ? v * (s - ss) / dy : v * (sn - s) / dy;
                        double diff = AdvectionDiffusionSolver.KH * (sw + se - 2.0 * s) / (dx * dx)
                                    + AdvectionDiffusionSolver.KH * (ss + sn - 2.0 * s) / (dy * dy)
                                    + cv * (sup + sdn - 2.0 * s);
                        double diag = 4.0 * AdvectionDiffusionSolver.KH / (dx * dx)
                                    + 4.0 * AdvectionDiffusionSolver.KH / (dy * dy) + 2.0 * cv
                                    + Math.abs(u) / dx + Math.abs(v) / dy;
                        double target = s + (diff - advX - advY) / diag;
                        double nv = s + AdvectionDiffusionSolver.SOR * (target - s);
                        st.s[q] = nv;
                        res = Math.max(res, Math.abs(nv - s));
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        return new AdvectionDiffusionSolver.Result(iter, res);
    }
}
