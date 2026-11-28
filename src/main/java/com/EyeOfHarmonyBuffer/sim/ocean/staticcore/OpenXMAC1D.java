package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 1D MAC projection: pressure at cells, velocity at faces; x boundary face
 * velocities are fixed parent flux, pressure uses a gauge (no boundary ghost).
 * Not wired into production.
 */
public final class OpenXMAC1D {
    private OpenXMAC1D() {}

    public static double[] divergence(int n, double dx, double[] u) {
        double[] d = new double[n];
        for (int i = 0; i < n; i++) d[i] = (u[i + 1] - u[i]) / dx;
        return d;
    }

    public static void applyL(int n, double dx, double[] p, double[] out) {
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                out[i] = (p[0] - p[1]) / (dx * dx);
            } else if (i == n - 1) {
                out[i] = (p[n - 1] - p[n - 2]) / (dx * dx);
            } else {
                out[i] = (2.0 * p[i] - p[i - 1] - p[i + 1]) / (dx * dx);
            }
        }
    }

    public static double[] solve(int n, double dx, double[] div, int maxIter, double tol) {
        double[] p = new double[n], r = new double[n], q = new double[n], ap = new double[n];
        double mean = 0.0;
        for (double x : div) mean += x;
        mean /= n;
        for (int i = 0; i < n; i++) { r[i] = -(div[i] - mean); q[i] = r[i]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(n, dx, q, ap);
            double pap = dot(q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int i = 0; i < n; i++) { p[i] += alpha * q[i]; r[i] -= alpha * ap[i]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int i = 0; i < n; i++) q[i] = r[i] + beta * q[i];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int n, double[] u, double dx, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        u[0] = westFlux;
        u[n] = eastFlux;
        double[] div = divergence(n, dx, u);
        double[] p = solve(n, dx, div, maxIter, tol);
        for (int f = 1; f < n; f++) u[f] -= (p[f] - p[f - 1]) / dx;
        double[] d2 = divergence(n, dx, u);
        double max = 0.0;
        for (double x : d2) max = Math.max(max, Math.abs(x));
        return max;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
