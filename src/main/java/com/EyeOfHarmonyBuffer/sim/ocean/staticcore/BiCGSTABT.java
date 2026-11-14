package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;

/**
 * T 的矩阵-free BiCGSTAB（对角/无预条件）。
 * 离散与 AdvectionDiffusionSolver 完全一致：
 *   interior: adv - diff = 0
 *   surface: T = surfaceT
 *   dry: 0
 */
public final class BiCGSTABT {

    private BiCGSTABT() {}

    /** 最近一次冻结 active-set 固定的格点数（诊断）。 */
    public static int LAST_FROZEN_CELLS = 0;

    private static int idx(OceanGrid g, int i, int j, int k) {
        int ii = i % g.nx; if (ii < 0) ii += g.nx;
        return (k * g.ny + j) * g.nx + ii;
    }

    private static void apply(OceanGrid g, OceanState st, double[] x, double[] surfaceT, double[] out) {
        apply(g, st, x, surfaceT, null, out);
    }

    /** 同 {@link #apply}，但 k&gt;=1 的方程带稳态源项 src [K/s]（穿透短波）。 */
    private static void apply(OceanGrid g, OceanState st, double[] x, double[] surfaceT,
                              double[] src, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) {
                    for (int k = 0; k < nz; k++) out[idx(g, i, j, k)] = 0.0;
                    continue;
                }
                for (int k = 0; k < nz; k++) {
                    int q = idx(g, i, j, k);
                    double t = x[q];
                    if (k == 0) {
                        out[q] = t - surfaceT[j * nx + i];
                        continue;
                    }
                    double tw = g.isWet(i - 1, j, k) ? x[idx(g, i - 1, j, k)] : t;
                    double te = g.isWet(i + 1, j, k) ? x[idx(g, i + 1, j, k)] : t;
                    double ts = g.isWet(i, j - 1, k) ? x[idx(g, i, j - 1, k)] : t;
                    double tn = g.isWet(i, j + 1, k) ? x[idx(g, i, j + 1, k)] : t;
                    double tup = x[idx(g, i, j, k - 1)];
                    double tdn = (k + 1 < g.wetLayers[g.idx(i, j)]) ? x[idx(g, i, j, k + 1)] : t;
                    double u = st.u[q], v = st.v[q];
                    double advX = u >= 0 ? u * (t - tw) / dx : u * (te - t) / dx;
                    double advY = v >= 0 ? v * (t - ts) / dy : v * (tn - t) / dy;
                    double adv = advX + advY
                            + AdvectionDiffusionSolver.advZ(st, i, j, k, t, g.dz[k]);
                    double dz = g.dz[k];
                    double cv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                    double diff = AdvectionDiffusionSolver.khAt(g, k) * (tw + te - 2.0 * t) / (dx * dx)
                                + AdvectionDiffusionSolver.khAt(g, k) * (ts + tn - 2.0 * t) / (dy * dy)
                                + cv * (tup + tdn - 2.0 * t);
                    out[q] = adv - diff - (src == null ? 0.0 : src[q]);
                }
            }
        }
    }

    /** active-set：fixed 的格点行改为 x = tf（Dirichlet），其余正常。 */
    private static void applyMasked(OceanGrid g, OceanState st, double[] x, double[] surfaceT,
                                    double[] src, boolean[] fixed, double[] tf, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) {
                    for (int k = 0; k < nz; k++) out[idx(g, i, j, k)] = 0.0;
                    continue;
                }
                for (int k = 0; k < nz; k++) {
                    int q = idx(g, i, j, k);
                    double t = x[q];
                    if (fixed[q]) { out[q] = t - tf[q]; continue; }
                    if (k == 0) { out[q] = t - surfaceT[j * nx + i]; continue; }
                    double tw = g.isWet(i - 1, j, k) ? x[idx(g, i - 1, j, k)] : t;
                    double te = g.isWet(i + 1, j, k) ? x[idx(g, i + 1, j, k)] : t;
                    double ts = g.isWet(i, j - 1, k) ? x[idx(g, i, j - 1, k)] : t;
                    double tn = g.isWet(i, j + 1, k) ? x[idx(g, i, j + 1, k)] : t;
                    double tup = x[idx(g, i, j, k - 1)];
                    double tdn = (k + 1 < g.wetLayers[g.idx(i, j)]) ? x[idx(g, i, j, k + 1)] : t;
                    double u = st.u[q], v = st.v[q];
                    double advX = u >= 0 ? u * (t - tw) / dx : u * (te - t) / dx;
                    double advY = v >= 0 ? v * (t - ts) / dy : v * (tn - t) / dy;
                    double adv = advX + advY
                            + AdvectionDiffusionSolver.advZ(st, i, j, k, t, g.dz[k]);
                    double dz = g.dz[k];
                    double cv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                    double diff = AdvectionDiffusionSolver.khAt(g, k) * (tw + te - 2.0 * t) / (dx * dx)
                                + AdvectionDiffusionSolver.khAt(g, k) * (ts + tn - 2.0 * t) / (dy * dy)
                                + cv * (tup + tdn - 2.0 * t);
                    out[q] = adv - diff - (src == null ? 0.0 : src[q]);
                }
            }
        }
    }

    /** 带 mask 的 BiCGSTAB（与 {@link #solve} 同算法）。 */
    private static double solveMasked(OceanGrid g, OceanState st, double[] surfaceT,
                                      double[] src, int maxIter, double tol,
                                      boolean[] fixed, double[] tf) {
        int n = g.nx * g.ny * g.nz;
        double[] x = st.t.clone();
        double[] r = new double[n], rhat = new double[n];
        double[] p = new double[n], v = new double[n], s = new double[n], t = new double[n];
        double[] ax = new double[n];
        applyMasked(g, st, x, surfaceT, src, fixed, tf, ax);
        for (int q = 0; q < n; q++) r[q] = fixed[q] ? (tf[q] - x[q]) : -ax[q];
        System.arraycopy(r, 0, rhat, 0, n);
        double rho = 1.0, alpha = 1.0, omega = 1.0;
        double r0 = norm(g, r);
        if (r0 < tol) { System.arraycopy(x, 0, st.t, 0, n); return r0; }
        for (int it = 0; it < maxIter; it++) {
            double rho1 = dot(g, rhat, r);
            if (Math.abs(rho) < 1e-300) break;
            double beta = (rho1 / rho) * (alpha / omega);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * (p[q] - omega * v[q]);
            applyMasked(g, st, p, surfaceT, src, fixed, tf, v);
            double den = dot(g, rhat, v);
            if (Math.abs(den) < 1e-300) break;
            alpha = rho1 / den;
            for (int q = 0; q < n; q++) s[q] = r[q] - alpha * v[q];
            if (norm(g, s) < tol) {
                for (int q = 0; q < n; q++) { x[q] += alpha * p[q]; r[q] = 0.0; }
                break;
            }
            applyMasked(g, st, s, surfaceT, src, fixed, tf, t);
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
        System.arraycopy(x, 0, st.t, 0, n);
        return norm(g, r);
    }

    /**
     * ★ 2026-11-03（stage 3）：带内部冰点约束的 T 解。
     * 先正常解；把 T&lt;Tf(S) 的格点固定为 Tf 重解；重复直到无新超冷。
     * 潜热/盐析记账留给调用方（ML/冰），本方法只保证 T≥Tf 的约束。
     */
    public static double solveWithFreezing(OceanGrid g, OceanState st, double[] surfaceT,
                                           double[] src, int maxIter, double tol) {
        int n = g.nx * g.ny * g.nz;
        boolean[] fixed = new boolean[n];
        double[] tf = new double[n];
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz(g); k++) {
                    int q = st.idx(i, j, k);
                    tf[q] = FreezingPoint.kelvin(st.s[q]);
                }
            }
        double res = 0.0;
        int pass = 0;
        for (; pass < 6; pass++) {
            res = solveMasked(g, st, surfaceT, src, maxIter, tol, fixed, tf);
            boolean added = false;
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 1; k < nz(g); k++) {
                        int q = st.idx(i, j, k);
                        if (!fixed[q] && st.t[q] < tf[q] - 1.0e-9) {
                            fixed[q] = true;
                            added = true;
                        }
                    }
                }
            if (!added) break;
        }
        for (int q = 0; q < n; q++) if (fixed[q]) st.t[q] = tf[q];
        int cnt = 0;
        for (int q = 0; q < n; q++) if (fixed[q]) cnt++;
        LAST_FROZEN_CELLS = cnt;
        return res;
    }
    private static int nz(OceanGrid g) { return g.nz; }

    /** 右预条件 BiCGSTAB：M=diag(A)。 */
    public static double solvePreconditioned(OceanGrid g, OceanState st, double[] surfaceT,
                                             int maxIter, double tol) {
        int n = g.nx * g.ny * g.nz;
        double[] x = st.t.clone();
        double[] r = new double[n], rhat = new double[n], p = new double[n];
        double[] v = new double[n], s = new double[n], t = new double[n];
        double[] z = new double[n], z2 = new double[n], diag = new double[n], ax = new double[n];
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) { for (int k=0;k<nz;k++) diag[idx(g,i,j,k)] = 1.0; continue; }
                for (int k = 0; k < nz; k++) {
                    int q = idx(g, i, j, k);
                    if (k == 0) { diag[q] = 1.0; continue; }
                    double dz = g.dz[k];
                    double cv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                    diag[q] = 4.0 * AdvectionDiffusionSolver.khAt(g, k) / (dx * dx)
                            + 4.0 * AdvectionDiffusionSolver.khAt(g, k) / (dy * dy) + 2.0 * cv
                            + Math.abs(st.u[q]) / dx + Math.abs(st.v[q]) / dy
                            + AdvectionDiffusionSolver.advZDiag(st, i, j, k, dz);
                }
            }
        apply(g, st, x, surfaceT, ax);
        for (int q = 0; q < n; q++) r[q] = -ax[q];
        System.arraycopy(r, 0, rhat, 0, n);
        double rho = 1.0, alpha = 1.0, omega = 1.0;
        if (norm(g, r) < tol) return norm(g, r);
        for (int it = 0; it < maxIter; it++) {
            double rho1 = dot(g, rhat, r);
            if (Math.abs(rho) < 1e-300) break;
            double beta = (rho1 / rho) * (alpha / omega);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * (p[q] - omega * v[q]);
            for (int q = 0; q < n; q++) z[q] = p[q] / diag[q];
            apply(g, st, z, surfaceT, v);
            double den = dot(g, rhat, v);
            if (Math.abs(den) < 1e-300) break;
            alpha = rho1 / den;
            for (int q = 0; q < n; q++) s[q] = r[q] - alpha * v[q];
            if (norm(g, s) < tol) {
                for (int q = 0; q < n; q++) { x[q] += alpha * z[q]; r[q] = 0.0; }
                break;
            }
            for (int q = 0; q < n; q++) z2[q] = s[q] / diag[q];
            apply(g, st, z2, surfaceT, t);
            double tt = dot(g, t, t);
            if (tt < 1e-300) break;
            omega = dot(g, t, s) / tt;
            for (int q = 0; q < n; q++) {
                x[q] += alpha * z[q] + omega * z2[q];
                r[q] = s[q] - omega * t[q];
            }
            rho = rho1;
            if (norm(g, r) < tol) break;
        }
        System.arraycopy(x, 0, st.t, 0, n);
        return norm(g, r);
    }
    static double dot(OceanGrid g, double[] a, double[] b) {
        double s = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j))
                    for (int k = 0; k < g.nz; k++) {
                        int q = idx(g, i, j, k);
                        s += a[q] * b[q];
                    }
        return s;
    }

    static double norm(OceanGrid g, double[] a) { return Math.sqrt(dot(g, a, a)); }

    public static double solve(OceanGrid g, OceanState st, double[] surfaceT, int maxIter, double tol) {
        return solve(g, st, surfaceT, null, maxIter, tol);
    }

    /** 带稳态源项 src [K/s] 的版本（k&gt;=1；k=0 仍是 Dirichlet 面）。 */
    public static double solve(OceanGrid g, OceanState st, double[] surfaceT,
                               double[] src, int maxIter, double tol) {
        int n = g.nx * g.ny * g.nz;
        double[] x = st.t.clone();
        double[] r = new double[n], rhat = new double[n];
        double[] p = new double[n], v = new double[n], s = new double[n], t = new double[n];
        double[] ax = new double[n];
        apply(g, st, x, surfaceT, src, ax);
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
            apply(g, st, p, surfaceT, src, v);
            double den = dot(g, rhat, v);
            if (Math.abs(den) < 1e-300) break;
            alpha = rho1 / den;
            for (int q = 0; q < n; q++) s[q] = r[q] - alpha * v[q];
            double sNorm = norm(g, s);
            if (sNorm < tol) {
                for (int q = 0; q < n; q++) { x[q] += alpha * p[q]; r[q] = 0.0; }
                break;
            }
            apply(g, st, s, surfaceT, src, t);
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
        System.arraycopy(x, 0, st.t, 0, n);
        return norm(g, r);
    }
}
