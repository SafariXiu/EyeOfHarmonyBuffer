package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 2D MAC projection: u at x-faces, v at y-faces, pressure at cells.
 * x boundaries are fixed parent flux; y is periodic. Not wired into production.
 */
public final class OpenXMAC2D {
    private OpenXMAC2D() {}

    public static double[] divergence(int nx, int ny, double dx, double dy, double[] u, double[] v) {
        double[] d = new double[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int jp = (j + 1) % ny;
                d[j * nx + i] = (u[(j * (nx + 1)) + i + 1] - u[(j * (nx + 1)) + i]) / dx
                        + (v[jp * nx + i] - v[j * nx + i]) / dy;
            }
        return d;
    }

    public static void applyL(int nx, int ny, double dx, double dy, double[] p, double[] out) {
        for (int j = 0; j < ny; j++) {
            int jm = (j - 1 + ny) % ny, jp = (j + 1) % ny;
            for (int i = 0; i < nx; i++) {
                double x;
                if (i == 0) x = (p[j * nx] - p[j * nx + 1]) / (dx * dx);
                else if (i == nx - 1) x = (p[j * nx + nx - 1] - p[j * nx + nx - 2]) / (dx * dx);
                else x = (2.0 * p[j * nx + i] - p[j * nx + i - 1] - p[j * nx + i + 1]) / (dx * dx);
                double y = (2.0 * p[j * nx + i] - p[jm * nx + i] - p[jp * nx + i]) / (dy * dy);
                out[j * nx + i] = x + y;
            }
        }
    }

    public static double[] solve(int nx, int ny, double dx, double dy, double[] div,
                                 int maxIter, double tol) {
        int n = nx * ny;
        double[] p = new double[n], r = new double[n], q = new double[n], ap = new double[n];
        double mean = 0.0;
        for (double x : div) mean += x;
        mean /= n;
        for (int k = 0; k < n; k++) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(nx, ny, dx, dy, q, ap);
            double pap = dot(q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int k = 0; k < n; k++) { p[k] += alpha * q[k]; r[k] -= alpha * ap[k]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int k = 0; k < n; k++) q[k] = r[k] + beta * q[k];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int nx, int ny, double[] u, double[] v,
                                 double dx, double dy, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        for (int j = 0; j < ny; j++) {
            u[j * (nx + 1)] = westFlux;
            u[j * (nx + 1) + nx] = eastFlux;
            v[ny * nx + j] = v[j * nx]; // periodic duplicate
        }
        double[] div = divergence(nx, ny, dx, dy, u, v);
        double[] p = solve(nx, ny, dx, dy, div, maxIter, tol);
        for (int j = 0; j < ny; j++) {
            for (int i = 1; i < nx; i++)
                u[j * (nx + 1) + i] -= (p[j * nx + i] - p[j * nx + i - 1]) / dx;
            int jm = (j - 1 + ny) % ny;
            for (int i = 0; i < nx; i++)
                v[j * nx + i] -= (p[j * nx + i] - p[jm * nx + i]) / dy;
        }
        for (int j = 0; j < ny; j++) v[ny * nx + j] = v[j * nx];
        double[] d2 = divergence(nx, ny, dx, dy, u, v);
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
