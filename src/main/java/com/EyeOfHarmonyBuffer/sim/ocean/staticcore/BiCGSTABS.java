package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * S 的矩阵-free BiCGSTAB。
 * 表面：div(hSu) + S*(fw/rho) - KV*(S1-S0)/dz0 = 0
 * 内部：adv - diff = 0
 * dry: 0
 */
public final class BiCGSTABS {

    private BiCGSTABS() {}

    private static int idx(OceanGrid g, int i, int j, int k) {
        int ii = i % g.nx; if (ii < 0) ii += g.nx;
        return (k * g.ny + j) * g.nx + ii;
    }

    private static void apply(OceanGrid g, OceanState st, double[] fwSurf,
                              double[] x, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy, dz0 = g.dz[0];
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) {
                    for (int k = 0; k < nz; k++) out[idx(g, i, j, k)] = 0.0;
                    continue;
                }
                for (int k = 0; k < nz; k++) {
                    int q = idx(g, i, j, k);
                    double s = x[q];
                    if (k == 0) {
                        int qs = j * nx + i;
                        double h = Math.max(1.0, st.surfaceLayer[qs]);
                        boolean wW = g.isWet(i - 1, j), wE = g.isWet(i + 1, j);
                        boolean wS = g.isWet(i, j - 1), wN = g.isWet(i, j + 1);
                        double hW = wW ? Math.max(1.0, st.surfaceLayer[g.idx(i - 1, j)]) : h;
                        double hE = wE ? Math.max(1.0, st.surfaceLayer[g.idx(i + 1, j)]) : h;
                        double hS = wS ? Math.max(1.0, st.surfaceLayer[g.idx(i, j - 1)]) : h;
                        double hN = wN ? Math.max(1.0, st.surfaceLayer[g.idx(i, j + 1)]) : h;
                        double sW = wW ? x[idx(g, i - 1, j, 0)] : s;
                        double sE = wE ? x[idx(g, i + 1, j, 0)] : s;
                        double sS = wS ? x[idx(g, i, j - 1, 0)] : s;
                        double sN = wN ? x[idx(g, i, j + 1, 0)] : s;
                        double u = st.u[q], v = st.v[q];
                        double uE = wE ? st.u[idx(g, i + 1, j, 0)] : u;
                        double uW = wW ? st.u[idx(g, i - 1, j, 0)] : u;
                        double vN = wN ? st.v[idx(g, i, j + 1, 0)] : v;
                        double vS = wS ? st.v[idx(g, i, j - 1, 0)] : v;
                        double divU = (uE - uW) / (2.0 * dx) + (vN - vS) / (2.0 * dy);
                        double adv = u * (s - sW) / (2.0 * dx) + v * (s - sS) / (2.0 * dy);
                        double flux = AdvectionDiffusionSolver.KV * (x[idx(g, i, j, 1)] - s) / dz0;
                        double fw = fwSurf[j * nx + i];
                        out[q] = adv + s * divU + s * (fw / 1027.8) - flux;
                        continue;
                    }
                    double sw = g.isWet(i - 1, j) ? x[idx(g, i - 1, j, k)] : s;
                    double se = g.isWet(i + 1, j) ? x[idx(g, i + 1, j, k)] : s;
                    double ss = g.isWet(i, j - 1) ? x[idx(g, i, j - 1, k)] : s;
                    double sn = g.isWet(i, j + 1) ? x[idx(g, i, j + 1, k)] : s;
                    double sup = x[idx(g, i, j, k - 1)];
                    double sdn = (k + 1 < nz) ? x[idx(g, i, j, k + 1)] : s;
                    double u = st.u[q], v = st.v[q];
                    double advX = u >= 0 ? u * (s - sw) / dx : u * (se - s) / dx;
                    double advY = v >= 0 ? v * (s - ss) / dy : v * (sn - s) / dy;
                    double dz = g.dz[k];
                    double cv = AdvectionDiffusionSolver.KV / (dz * dz);
                    double diff = AdvectionDiffusionSolver.KH * (sw + se - 2.0 * s) / (dx * dx)
                                + AdvectionDiffusionSolver.KH * (ss + sn - 2.0 * s) / (dy * dy)
                                + cv * (sup + sdn - 2.0 * s);
                    out[q] = advX + advY - diff;
                }
            }
        }
    }

    static double dot(OceanGrid g, double[] a, double[] b) {
        double sum = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j))
                    for (int k = 0; k < g.nz; k++) {
                        int q = idx(g, i, j, k);
                        sum += a[q] * b[q];
                    }
        return sum;
    }

    static double norm(OceanGrid g, double[] a) { return Math.sqrt(dot(g, a, a)); }

    public static double solve(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta,
                               int maxIter, double tol) {
        int n = g.nx * g.ny * g.nz;
        double[] x = st.s.clone();
        double[] r = new double[n], rhat = new double[n], p = new double[n];
        double[] v = new double[n], s = new double[n], t = new double[n], ax = new double[n];
        double[] fwSurf = new double[g.nx * g.ny];
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j)) {
                    double fw = f.freshwaterFluxKgM2S((int) Math.round(g.xOf(i)),
                            (int) Math.round(g.zOf(j)), theta, st.t[st.idx(i, j, 0)]);
                    fwSurf[j * g.nx + i] = Double.isNaN(fw) ? 0.0 : fw;
                }
        apply(g, st, fwSurf, x, ax);
        for (int q = 0; q < n; q++) r[q] = -ax[q];
        System.arraycopy(r, 0, rhat, 0, n);
        double rho = 1.0, alpha = 1.0, omega = 1.0;
        double r0 = norm(g, r);
        if (r0 < tol) return r0;
        for (int it = 0; it < maxIter; it++) {
            double rho1 = dot(g, rhat, r);
            if (Math.abs(rho) < 1e-300) break;
            double beta = (rho1 / rho) * (alpha / omega);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * (p[q] - omega * v[q]);
            apply(g, st, fwSurf, p, v);
            double den = dot(g, rhat, v);
            if (Math.abs(den) < 1e-300) break;
            alpha = rho1 / den;
            for (int q = 0; q < n; q++) s[q] = r[q] - alpha * v[q];
            double sNorm = norm(g, s);
            if (sNorm < tol) {
                for (int q = 0; q < n; q++) { x[q] += alpha * p[q]; r[q] = 0.0; }
                break;
            }
            apply(g, st, fwSurf, s, t);
            double tt = dot(g, t, t);
            if (tt < 1e-300) break;
            omega = dot(g, t, s) / tt;
            for (int q = 0; q < n; q++) {
                x[q] += alpha * p[q] + omega * s[q];
                r[q] = s[q] - omega * t[q];
            }
            rho = rho1;
            if (norm(g, r) < tol) break;
        }
        System.arraycopy(x, 0, st.s, 0, n);
        return norm(g, r);
    }
}
