package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 1D finite-volume fixed-face projection (A: cell-centered + diagonal mass matrix).
 * Boundary cells have half volume; faces 0/n are fixed; mass-weighted CG keeps M*L symmetric.
 */
public final class OpenXFV1D {
    private OpenXFV1D() {}

    public static final class Result {
        public final double[] phi;
        public final int iterations;
        public final double residual;
        Result(double[] phi, int iterations, double residual) {
            this.phi = phi; this.iterations = iterations; this.residual = residual;
        }
    }

    public static Result solve(int n, double dx, double[] rhs, int maxIter, double tol) {
        double[] m = new double[n];
        for (int i = 0; i < n; i++) m[i] = (i == 0 || i == n - 1) ? 0.5 * dx : dx;
        double total = 0.0, mean = 0.0;
        for (int i = 0; i < n; i++) { total += m[i]; mean += m[i] * rhs[i]; }
        if (total > 0) mean /= total;
        double[] div = new double[n];
        for (int i = 0; i < n; i++) div[i] = rhs[i] - mean;
        double[] phi = new double[n], r = new double[n], p = new double[n], ap = new double[n];
        for (int i = 0; i < n; i++) { r[i] = div[i]; p[i] = r[i]; }
        double rr = dotM(m, r, r);
        double target = tol * tol * Math.max(1.0, dotM(m, div, div));
        int it = 0;
        for (; it < maxIter && rr > target; it++) {
            applyL(n, dx, p, ap);
            double pap = dotM(m, p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int i = 0; i < n; i++) { phi[i] += alpha * p[i]; r[i] -= alpha * ap[i]; }
            double rrNew = dotM(m, r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int i = 0; i < n; i++) p[i] = r[i] + beta * p[i];
            rr = rrNew;
        }
        return new Result(phi, it, Math.sqrt(rr));
    }

    public static void applyL(int n, double dx, double[] p, double[] out) {
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                out[i] = 2.0 * (p[i] - p[i + 1]) / (dx * dx);
            } else if (i == n - 1) {
                out[i] = 2.0 * (p[i] - p[i - 1]) / (dx * dx);
            } else {
                out[i] = (2.0 * p[i] - p[i - 1] - p[i + 1]) / (dx * dx);
            }
        }
    }

    /** Project an all-wet 1D face array; fixed faces 0/n are unchanged. */
    public static double project(int n, double[] u, double dx, int maxIter, double tol) {
        double[] div = new double[n];
        for (int i = 0; i < n; i++) div[i] = (u[i + 1] - u[i]) / dx;
        Result r = solve(n, dx, div, maxIter, tol);
        for (int i = 1; i < n; i++) u[i] += (r.phi[i] - r.phi[i - 1]) / dx;
        double max = 0.0;
        for (int i = 0; i < n; i++) max = Math.max(max, Math.abs((u[i + 1] - u[i]) / dx));
        return max;
    }

    private static double dotM(double[] m, double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += m[i] * a[i] * b[i];
        return s;
    }
}
