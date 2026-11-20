package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 3D fixed-x (Neumann) Poisson prototype: x boundaries are fixed faces, y/z are
 * closed. It is the pressure operator needed by the 3D fixed-face projection.
 * Not wired into production.
 */
public final class OpenXPoissonFixedX {
    private OpenXPoissonFixedX() {}

    public static final class Result {
        public final double[] phi;
        public final int iterations;
        public final double residual;
        Result(double[] phi, int iterations, double residual) {
            this.phi = phi; this.iterations = iterations; this.residual = residual;
        }
    }

    public static Result solve(int nx, int ny, int nz, double[] rhs, int maxIter, double tol) {
        int n = nx * ny * nz, nxny = nx * ny;
        double[] phi = new double[n], r = new double[n], p = new double[n], ap = new double[n];
        for (int k = 1; k < nz - 1; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    r[q] = rhs[q];
                    p[q] = r[q];
                }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(rhs, rhs));
        int it = 0;
        for (; it < maxIter && rr > target; it++) {
            applyL(nx, ny, nz, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int q = 0; q < n; q++) { phi[q] += alpha * p[q]; r[q] -= alpha * ap[q]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * p[q];
            rr = rrNew;
        }
        return new Result(phi, it, Math.sqrt(rr));
    }

    static void applyL(int nx, int ny, int nz, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        int nxny = nx * ny;
        for (int k = 1; k < nz - 1; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    double w = (i == 1) ? p[q] : p[q - 1];
                    double e = (i == nx - 2) ? p[q] : p[q + 1];
                    out[q] = 6.0 * p[q] - w - e
                            - p[q - nx] - p[q + nx] - p[q - nxny] - p[q + nxny];
                }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
