package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * 最小 h0 预报盐度：表面层厚度随 P-E+runoff 变，盐质量沿 h0 守恒。
 * 仅用于短测，不接生产。
 */
public final class PrognosticSurfaceSalinitySolver {

    public static final double DT = 86400.0;
    public static final double RHO = 1027.8;
    public static final double H_MIN = 1.0;
    public static final double H_MAX = 500.0;

    public static final class Result {
        public final int iterations;
        public final double residual;
        public final double budgetMax, budgetMean;
        public final double sMin, sMax, hMin, hMax;
        public final double saltDrift;
        Result(int it, double res, double bmax, double bmean,
               double smin, double smax, double hmin, double hmax, double drift) {
            iterations = it; residual = res; budgetMax = bmax; budgetMean = bmean;
            sMin = smin; sMax = smax; hMin = hmin; hMax = hmax; saltDrift = drift;
        }
    }

    private PrognosticSurfaceSalinitySolver() {}

    public static double solve(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta, int maxIter) {
        return solveDetailed(g, st, f, theta, maxIter).residual;
    }

    private static double bilinear(OceanGrid g, double[] s, int i, int j,
                                    double xb, double yb) {
        int nx = g.nx, ny = g.ny;
        int i0 = (int) Math.floor(xb), j0 = (int) Math.floor(yb);
        double tx = xb - i0, ty = yb - j0;
        int i1 = (i0 + 1) % nx, j1 = j0 + 1;
        i0 = (i0 % nx + nx) % nx;
        if (j0 < 1) j0 = 1;
        if (j1 > ny - 2) j1 = ny - 2;
        double s00 = g.isWet(i0, j0) ? s[j0 * nx + i0] : s[j * nx + i];
        double s10 = g.isWet(i1, j0) ? s[j0 * nx + i1] : s[j * nx + i];
        double s01 = g.isWet(i0, j1) ? s[j1 * nx + i0] : s[j * nx + i];
        double s11 = g.isWet(i1, j1) ? s[j1 * nx + i1] : s[j * nx + i];
        return (1 - tx) * (1 - ty) * s00 + tx * (1 - ty) * s10
             + (1 - tx) * ty * s01 + tx * ty * s11;
    }

    public static Result solveDetailed(OceanGrid g, OceanState st, SeaSurfaceForcing f,
                                       double theta, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dz0 = g.dz[0], dx = g.dx, dy = g.dy;
        double[] fw = new double[nx * ny];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++)
                if (g.isWet(i, j)) {
                    double v = f.freshwaterFluxKgM2S((int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, st.t[st.idx(i, j, 0)]);
                    // ★ 2026-11-02：把结冰/融冰的盐通量并入表层淡水收支（负 = 析盐浓缩）。
                    double ice = st.iceSaltFlux[j * nx + i];
                    fw[j * nx + i] = (Double.isNaN(v) ? 0.0 : v) + ice;
                    st.iceSaltFlux[j * nx + i] = 0.0;
                }
        double salt0 = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++)
                if (g.isWet(i, j)) {
                    int qs = j * nx + i;
                    salt0 += st.s[st.idx(i, j, 0)] * Math.max(H_MIN, st.surfaceLayer[qs]);
                }
        double res = 0.0;
        int it = 0;
        double[] sOld = new double[nx * ny];
        double[] hOld = new double[nx * ny];
        for (; it < maxIter; it++) {
            res = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    int qs = j * nx + i;
                    sOld[qs] = st.s[st.idx(i, j, 0)];
                    hOld[qs] = Math.max(H_MIN, st.surfaceLayer[qs]);
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int qs = j * nx + i, q0 = st.idx(i, j, 0), q1 = st.idx(i, j, 1);
                    double u = st.u[q0], v = st.v[q0];
                    double xb = i - u * DT / dx;
                    double yb = j - v * DT / dy;
                    double sAdv = bilinear(g, sOld, i, j, xb, yb);
                    double h0 = Math.max(H_MIN, hOld[qs]);
                    double h = Math.min(H_MAX, Math.max(H_MIN, h0 + fw[qs] * DT / RHO));
                    double m = sAdv * h0;
                    double flux = AdvectionDiffusionSolver.KV * (st.s[q1] - st.s[q0]) / dz0;
                    m += flux * DT;
                    double s0 = Math.max(0.0, Math.min(50.0, m / h));
                    double old = st.s[q0];
                    st.s[q0] = s0;
                    st.surfaceLayer[qs] = h;
                    st.eta[qs] = h - dz0;
                    res = Math.max(res, Math.abs(s0 - old));
                }
            if (res < 1.0e-8) break;
        }

        double salt1 = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++)
                if (g.isWet(i, j)) {
                    int qs = j * nx + i;
                    salt1 += st.s[st.idx(i, j, 0)] * Math.max(H_MIN, st.surfaceLayer[qs]);
                }
        double saltDrift = salt0 > 0 ? (salt1 - salt0) / salt0 : 0.0;
        double bmax = 0.0, bsum = 0.0;
        double smin = 1e30, smax = -1e30, hmin = 1e30, hmax = -1e30;
        int n = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int qs = j * nx + i, q0 = st.idx(i, j, 0), q1 = st.idx(i, j, 1);
                double s = st.s[q0], h = st.surfaceLayer[qs];
                smin = Math.min(smin, s); smax = Math.max(smax, s);
                hmin = Math.min(hmin, h); hmax = Math.max(hmax, h);
                double u = st.u[q0], v = st.v[q0];
                double uE = g.isWet(i + 1, j) ? st.u[st.idx(i + 1, j, 0)] : u;
                double uW = g.isWet(i - 1, j) ? st.u[st.idx(i - 1, j, 0)] : u;
                double vN = g.isWet(i, j + 1) ? st.v[st.idx(i, j + 1, 0)] : v;
                double vS = g.isWet(i, j - 1) ? st.v[st.idx(i, j - 1, 0)] : v;
                double divU = (uE - uW) / (2.0 * dx) + (vN - vS) / (2.0 * dy);
                double sW = g.isWet(i - 1, j) ? st.s[st.idx(i - 1, j, 0)] : s;
                double sS = g.isWet(i, j - 1) ? st.s[st.idx(i, j - 1, 0)] : s;
                double adv = u * (s - sW) / (2.0 * dx) + v * (s - sS) / (2.0 * dy);
                double flux = AdvectionDiffusionSolver.KV * (st.s[q1] - s) / dz0;
                double budget = Math.abs(adv + s * divU + s * (fw[qs] / RHO) - flux);
                bmax = Math.max(bmax, budget); bsum += budget; n++;
            }
        double bmean = n > 0 ? bsum / n : 0.0;
        return new Result(it, res, bmax, bmean, smin, smax, hmin, hmax, saltDrift);
    }
}
