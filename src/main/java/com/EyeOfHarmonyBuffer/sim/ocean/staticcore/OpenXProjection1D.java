package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 1D staggered face-centred x-open projection test. Faces u[0..n], cells 0..n-1;
 * cell i divergence = (u[i+1]-u[i])/dx; correction u[i] -= (phi[i]-phi[i-1])/dx.
 * Zero Dirichlet phi at the two ends. Not wired into production.
 */
public final class OpenXProjection1D {
    private OpenXProjection1D() {}

    public static double project(int n, double[] u, double dx, int maxIter, double tol) {
        double[] div = new double[n];
        for (int i = 1; i < n - 1; i++) div[i] = (u[i + 1] - u[i]) / dx;
        double[] phi = new double[n];
        double[] r = new double[n];
        double[] p = new double[n];
        double[] ap = new double[n];
        for (int i = 1; i < n - 1; i++) { r[i] = div[i]; p[i] = r[i]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
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
        for (int i = 1; i < n; i++) u[i] += (phi[i] - phi[i - 1]) / dx;
        double max = 0.0;
        for (int i = 1; i < n - 1; i++) max = Math.max(max, Math.abs((u[i + 1] - u[i]) / dx));
        return max;
    }

    /**
     * Fixed-boundary-face variant: faces u[1] and u[n-1] are parent-provided and
     * not corrected; only faces 2..n-2 are corrected. Active cells 1..n-2 use
     * Neumann pressure at the active-domain ends.
     */
    public static double projectFixed(int n, double[] u, double dx, int maxIter, double tol) {
        double[] div = new double[n];
        for (int i = 1; i < n - 1; i++) div[i] = (u[i + 1] - u[i]) / dx;
        double mean = 0.0;
        int cnt = 0;
        for (int i = 1; i < n - 1; i++) { mean += div[i]; cnt++; }
        if (cnt > 0) mean /= cnt;
        for (int i = 1; i < n - 1; i++) div[i] -= mean;
        double[] phi = new double[n];
        double[] r = new double[n], p = new double[n], ap = new double[n];
        for (int i = 1; i < n - 1; i++) { r[i] = div[i]; p[i] = r[i]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyLFixed(n, dx, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int i = 0; i < n; i++) { phi[i] += alpha * p[i]; r[i] -= alpha * ap[i]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int i = 0; i < n; i++) p[i] = r[i] + beta * p[i];
            rr = rrNew;
        }
        for (int i = 2; i < n - 1; i++) u[i] += (phi[i] - phi[i - 1]) / dx;
        double max = 0.0;
        for (int i = 1; i < n - 1; i++) max = Math.max(max, Math.abs((u[i + 1] - u[i]) / dx));
        return max;
    }


    /** Apply the fixed-face 1D projection independently to every (j,k) x-row of a 3D face array. */
    public static double projectXRows(int nx, int ny, int nz, double[] uF, int maxIter, double tol) {
        double[] u = new double[nx + 1];
        double max = 0.0;
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                int q0 = (k * ny + j) * nx;
                for (int i = 0; i < nx; i++) u[i] = uF[q0 + i];
                u[nx] = 0.0;
                double m = projectFixed(nx, u, 1.0, maxIter, tol);
                max = Math.max(max, m);
                for (int i = 0; i < nx; i++) uF[q0 + i] = u[i];
            }
        return max;
    }

    static void applyLFixed(int n, double dx, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int i = 1; i < n - 1; i++) {
            double w = (i == 1) ? p[i] : p[i - 1];
            double e = (i == n - 2) ? p[i] : p[i + 1];
            out[i] = (2.0 * p[i] - w - e) / (dx * dx);
        }
    }

    static void applyL(int n, double dx, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int i = 1; i < n - 1; i++)
            out[i] = (2.0 * p[i] - p[i - 1] - p[i + 1]) / (dx * dx);
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
