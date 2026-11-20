package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * Standalone 2D x-open Poisson prototype for the x-open projection task.
 *
 * <p>Unknowns are interior i=1..nx-2, j=1..ny-2. Columns i=0/nx-1 are x ghost
 * (Dirichlet 0 in this manufactured test), rows j=0/ny-1 are y boundaries.
 * This class is not wired into production; it validates the x-ghost operator.
 */
public final class OpenXPoisson {
    private OpenXPoisson() {}

    public static final class Result {
        public final double[] phi;
        public final int iterations;
        public final double residual;
        Result(double[] phi, int iterations, double residual) {
            this.phi = phi; this.iterations = iterations; this.residual = residual;
        }
    }

    /** Solve -laplacian(phi)=rhs with zero Dirichlet boundary on all four sides. */
    public static Result solve(int nx, int ny, double[] rhs, int maxIter, double tol) {
        int n = nx * ny;
        double[] phi = new double[n];
        double[] r = new double[n];
        double[] p = new double[n];
        double[] ap = new double[n];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 1; i < nx - 1; i++) {
                int q = j * nx + i;
                r[q] = rhs[q];
                p[q] = r[q];
            }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(rhs, rhs));
        int it = 0;
        for (; it < maxIter && rr > target; it++) {
            applyL(nx, ny, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int q = 0; q < n; q++) {
                phi[q] += alpha * p[q];
                r[q] -= alpha * ap[q];
            }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * p[q];
            rr = rrNew;
        }
        return new Result(phi, it, Math.sqrt(rr));
    }

    /** Solve the 3D analogue -laplacian(phi)=rhs with zero boundary (x ghost included). */
    public static Result solve3D(int nx, int ny, int nz, double[] rhs, int maxIter, double tol) {
        int n = nx * ny * nz;
        double[] phi = new double[n];
        double[] r = new double[n];
        double[] p = new double[n];
        double[] ap = new double[n];
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
            applyL3D(nx, ny, nz, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int q = 0; q < n; q++) {
                phi[q] += alpha * p[q];
                r[q] -= alpha * ap[q];
            }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * p[q];
            rr = rrNew;
        }
        return new Result(phi, it, Math.sqrt(rr));
    }

    static void applyL3D(int nx, int ny, int nz, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        int nxny = nx * ny;
        for (int k = 1; k < nz - 1; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    out[q] = 6.0 * p[q] - p[q - 1] - p[q + 1]
                            - p[q - nx] - p[q + nx] - p[q - nxny] - p[q + nxny];
                }
    }

    static void applyL(int nx, int ny, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int j = 1; j < ny - 1; j++)
            for (int i = 1; i < nx - 1; i++) {
                int q = j * nx + i;
                out[q] = 4.0 * p[q] - p[q - 1] - p[q + 1] - p[q - nx] - p[q + nx];
            }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
