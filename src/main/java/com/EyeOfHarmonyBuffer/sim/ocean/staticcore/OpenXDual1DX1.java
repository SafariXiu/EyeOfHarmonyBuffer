package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 1D X1 dual projection: velocity at cells, pressure at faces, half dual cells at
 * the two x boundaries. Boundary normal flux is parent-provided and fixed;
 * pressure uses a gauge (mean removed). Not wired into production.
 */
public final class OpenXDual1DX1 {
    private OpenXDual1DX1() {}

    /** Dual divergence at faces; boundary dual cells are half-width. */
    public static double[] divergence(int n, double[] u, double dx, double westFlux, double eastFlux) {
        double[] d = new double[n + 1];
        d[0] = (u[0] - westFlux) / (0.5 * dx);
        for (int f = 1; f < n; f++) d[f] = (u[f] - u[f - 1]) / dx;
        d[n] = (eastFlux - u[n - 1]) / (0.5 * dx);
        return d;
    }

    public static void gradient(int n, double[] p, double dx, double[] g) {
        for (int i = 0; i < n; i++) g[i] = (p[i + 1] - p[i]) / dx;
    }

    /** B = -D(Gp), positive for the X1 geometry. */
    static void applyB(int n, double dx, double[] p, double[] out) {
        double[] g = new double[n];
        gradient(n, p, dx, g);
        double[] d = divergence(n, g, dx, 0.0, 0.0);
        for (int f = 0; f <= n; f++) out[f] = -d[f];
    }

    public static double[] solve(int n, double dx, double[] div, int maxIter, double tol) {
        double[] p = new double[n + 1], r = new double[n + 1], q = new double[n + 1], ap = new double[n + 1];
        double[] v = new double[n + 1];
        for (int f = 0; f <= n; f++) v[f] = (f == 0 || f == n) ? 0.5 * dx : dx;
        double total = 0.0, mean = 0.0;
        for (int f = 0; f <= n; f++) { total += v[f]; mean += v[f] * div[f]; }
        if (total > 0) mean /= total;
        for (int f = 0; f <= n; f++) { r[f] = -(div[f] - mean); q[f] = r[f]; }
        double rr = dotV(v, r, r);
        double target = tol * tol * Math.max(1.0, dotV(v, div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyB(n, dx, q, ap);
            double pap = dotV(v, q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int f = 0; f <= n; f++) { p[f] += alpha * q[f]; r[f] -= alpha * ap[f]; }
            double rrNew = dotV(v, r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int f = 0; f <= n; f++) q[f] = r[f] + beta * q[f];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int n, double[] u, double dx, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        double[] div = divergence(n, u, dx, westFlux, eastFlux);
        double[] p = solve(n, dx, div, maxIter, tol);
        double[] g = new double[n];
        gradient(n, p, dx, g);
        for (int i = 0; i < n; i++) u[i] -= g[i];
        double[] d2 = divergence(n, u, dx, westFlux, eastFlux);
        double max = 0.0;
        for (double x : d2) max = Math.max(max, Math.abs(x));
        return max;
    }

    private static double dotV(double[] v, double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += v[i] * a[i] * b[i];
        return s;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
