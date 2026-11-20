package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** 1D Neumann fixed-x Poisson prototype for diagnosing the 3D fixed-face operator. */
public final class OpenXPoisson1DFixed {
    private OpenXPoisson1DFixed() {}

    public static final class Result {
        public final double[] phi;
        public final int iterations;
        public final double residual;
        Result(double[] phi, int iterations, double residual) {
            this.phi = phi; this.iterations = iterations; this.residual = residual;
        }
    }

    public static Result solve(int n, double dx, double[] rhs, int maxIter, double tol) {
        double[] div = rhs.clone();
        double mean = 0.0;
        int cnt = 0;
        for (int i = 1; i < n - 1; i++) { mean += div[i]; cnt++; }
        if (cnt > 0) mean /= cnt;
        for (int i = 1; i < n - 1; i++) div[i] -= mean;
        double[] phi = new double[n], r = new double[n], p = new double[n], ap = new double[n];
        for (int i = 1; i < n - 1; i++) { r[i] = div[i]; p[i] = r[i]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        int it = 0;
        for (; it < maxIter && rr > target; it++) {
            applyL(n, dx, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int i = 0; i < n; i++) { phi[i] += alpha * p[i]; r[i] -= alpha * ap[i]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int i = 0; i < n; i++) p[i] = r[i] + beta * p[i];
            rr = rrNew;
        }
        return new Result(phi, it, Math.sqrt(rr));
    }

    static void applyL(int n, double dx, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int i = 1; i < n - 1; i++) {
            if (i == 1) {
                out[i] = 2.0 * (p[i] - p[i + 1]) / (dx * dx);
            } else if (i == n - 2) {
                out[i] = 2.0 * (p[i] - p[i - 1]) / (dx * dx);
            } else {
                out[i] = (2.0 * p[i] - p[i - 1] - p[i + 1]) / (dx * dx);
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
