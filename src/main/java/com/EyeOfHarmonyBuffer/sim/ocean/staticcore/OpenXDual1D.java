package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 1D dual MAC (B): velocity at cells, pressure at faces. Divergence at faces,
 * correction to cell velocities from face-pressure differences. Compact Poisson.
 * Not wired into production yet.
 */
public final class OpenXDual1D {
    private OpenXDual1D() {}

    public static final class Result {
        public final double[] pressure;
        public final int iterations;
        public final double residual;
        Result(double[] pressure, int iterations, double residual) {
            this.pressure = pressure; this.iterations = iterations; this.residual = residual;
        }
    }

    /** Solve L p = div on faces 1..n-1, p[0]=p[n]=0. */
    public static Result solve(int n, double dx, double[] div, int maxIter, double tol) {
        double[] p = new double[n + 1];
        double[] r = new double[n + 1], q = new double[n + 1], ap = new double[n + 1];
        for (int f = 1; f < n; f++) { r[f] = div[f]; q[f] = r[f]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        int it = 0;
        for (; it < maxIter && rr > target; it++) {
            applyL(n, dx, q, ap);
            double pap = dot(q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int f = 1; f < n; f++) { p[f] += alpha * q[f]; r[f] -= alpha * ap[f]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int f = 1; f < n; f++) q[f] = r[f] + beta * q[f];
            rr = rrNew;
        }
        return new Result(p, it, Math.sqrt(rr));
    }

    static void applyL(int n, double dx, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int f = 1; f < n; f++) {
            out[f] = (2.0 * p[f] - p[f + 1] - p[f - 1]) / (dx * dx);
        }
    }

    /** Project an all-wet 1D cell-centred velocity field. */
    public static double project(int n, double[] u, double dx, int maxIter, double tol) {
        double[] div = new double[n + 1];
        for (int f = 1; f < n; f++) div[f] = (u[f] - u[f - 1]) / dx;
        Result r = solve(n, dx, div, maxIter, tol);
        for (int i = 0; i < n; i++) u[i] += (r.pressure[i + 1] - r.pressure[i]) / dx;
        double max = 0.0;
        for (int f = 1; f < n; f++) max = Math.max(max, Math.abs((u[f] - u[f - 1]) / dx));
        return max;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
