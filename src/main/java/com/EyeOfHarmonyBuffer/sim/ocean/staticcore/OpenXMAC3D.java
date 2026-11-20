package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 3D MAC projection: u/v/w at faces, pressure at cells. x boundaries are fixed
 * parent flux; y/z are periodic. Not wired into production.
 */
public final class OpenXMAC3D {
    private OpenXMAC3D() {}

    public static double[] divergence(int nx, int ny, int nz, double dx, double dy, double dz,
                                      double[] u, double[] v, double[] w) {
        double[] d = new double[nx * ny * nz];
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int jp = (j + 1) % ny, kp = (k + 1) % nz;
                    double du = (u[(k * ny + j) * (nx + 1) + i + 1] - u[(k * ny + j) * (nx + 1) + i]) / dx;
                    double dv = (v[(k * (ny + 1) + jp) * nx + i] - v[(k * (ny + 1) + j) * nx + i]) / dy;
                    double dw = (w[kp * nx * ny + j * nx + i] - w[k * nx * ny + j * nx + i]) / dz;
                    d[(k * ny + j) * nx + i] = du + dv + dw;
                }
        return d;
    }

    public static void applyL(int nx, int ny, int nz, double dx, double dy, double dz, double[] p, double[] out) {
        for (int k = 0; k < nz; k++) {
            int km = (k - 1 + nz) % nz, kp = (k + 1) % nz;
            for (int j = 0; j < ny; j++) {
                int jm = (j - 1 + ny) % ny, jp = (j + 1) % ny;
                for (int i = 0; i < nx; i++) {
                    double x;
                    if (i == 0) x = (p[(k * ny + j) * nx] - p[(k * ny + j) * nx + 1]) / (dx * dx);
                    else if (i == nx - 1) x = (p[(k * ny + j) * nx + nx - 1] - p[(k * ny + j) * nx + nx - 2]) / (dx * dx);
                    else x = (2.0 * p[(k * ny + j) * nx + i] - p[(k * ny + j) * nx + i - 1] - p[(k * ny + j) * nx + i + 1]) / (dx * dx);
                    double y = (2.0 * p[(k * ny + j) * nx + i] - p[(k * ny + jm) * nx + i] - p[(k * ny + jp) * nx + i]) / (dy * dy);
                    double z = (2.0 * p[(k * ny + j) * nx + i] - p[(km * ny + j) * nx + i] - p[(kp * ny + j) * nx + i]) / (dz * dz);
                    out[(k * ny + j) * nx + i] = x + y + z;
                }
            }
        }
    }

    public static double[] solve(int nx, int ny, int nz, double dx, double dy, double dz,
                                 double[] div, int maxIter, double tol) {
        int n = nx * ny * nz;
        double[] p = new double[n], r = new double[n], q = new double[n], ap = new double[n];
        double mean = 0.0;
        for (double x : div) mean += x;
        mean /= n;
        for (int k = 0; k < n; k++) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(nx, ny, nz, dx, dy, dz, q, ap);
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

    public static double project(int nx, int ny, int nz, double[] u, double[] v, double[] w,
                                 double dx, double dy, double dz, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                u[(k * ny + j) * (nx + 1)] = westFlux;
                u[(k * ny + j) * (nx + 1) + nx] = eastFlux;
            }
        for (int k = 0; k < nz; k++)
            for (int i = 0; i < nx; i++) {
                v[(k * (ny + 1) + ny) * nx + i] = v[k * (ny + 1) * nx + i];
                w[((nz) * nx * ny) + 0 * nx + i] = w[0 * nx * ny + 0 * nx + i]; // fixed below per j
            }
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                w[nz * nx * ny + j * nx + i] = w[j * nx + i];

        double[] div = divergence(nx, ny, nz, dx, dy, dz, u, v, w);
        double[] p = solve(nx, ny, nz, dx, dy, dz, div, maxIter, tol);

        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 1; i < nx; i++)
                    u[(k * ny + j) * (nx + 1) + i] -= (p[(k * ny + j) * nx + i] - p[(k * ny + j) * nx + i - 1]) / dx;
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                int jm = (j - 1 + ny) % ny;
                for (int i = 0; i < nx; i++)
                    v[(k * (ny + 1) + j) * nx + i] -= (p[(k * ny + j) * nx + i] - p[(k * ny + jm) * nx + i]) / dy;
            }
        for (int k = 0; k < nz; k++) {
            int km = (k - 1 + nz) % nz;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++)
                    w[k * nx * ny + j * nx + i] -= (p[(k * ny + j) * nx + i] - p[(km * ny + j) * nx + i]) / dz;
        }
        for (int k = 0; k < nz; k++)
            for (int i = 0; i < nx; i++)
                v[(k * (ny + 1) + ny) * nx + i] = v[k * (ny + 1) * nx + i];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                w[nz * nx * ny + j * nx + i] = w[j * nx + i];

        double[] d2 = divergence(nx, ny, nz, dx, dy, dz, u, v, w);
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
