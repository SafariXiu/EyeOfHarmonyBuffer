package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** 3D MAC projection with a wet mask; every face is coupled only if both cells are wet. Not production. */
public final class OpenXMAC3DWet {
    private OpenXMAC3DWet() {}

    static int ci(int i, int j, int k, int nx, int ny) { return (k * ny + j) * nx + i; }
    static int ui(int i, int j, int k, int nx, int ny) { return (k * ny + j) * (nx + 1) + i; }
    static int vi(int i, int j, int k, int nx, int ny) { return (k * (ny + 1) + j) * nx + i; }
    static int wi(int i, int j, int k, int nx, int ny) { return k * nx * ny + j * nx + i; }

    public static double[] divergence(int nx, int ny, int nz, double dx, double dy, double dz,
                                      double[] u, double[] v, double[] w, boolean[] wet,
                                      double westFlux, double eastFlux) {
        double[] d = new double[nx * ny * nz];
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = ci(i, j, k, nx, ny);
                    if (!wet[q]) continue;
                    int jp = (j + 1) % ny, jm = (j - 1 + ny) % ny;
                    int kp = (k + 1) % nz, km = (k - 1 + nz) % nz;
                    double ue, uw, vn, vs, wt, wb;
                    if (i == nx - 1) ue = eastFlux;
                    else ue = wet[ci(i + 1, j, k, nx, ny)] ? u[ui(i + 1, j, k, nx, ny)] : 0.0;
                    if (i == 0) uw = westFlux;
                    else uw = wet[ci(i - 1, j, k, nx, ny)] ? u[ui(i, j, k, nx, ny)] : 0.0;
                    vn = wet[ci(i, jp, k, nx, ny)] ? v[vi(i, jp, k, nx, ny)] : 0.0;
                    vs = wet[ci(i, jm, k, nx, ny)] ? v[vi(i, j, k, nx, ny)] : 0.0;
                    wt = wet[ci(i, j, kp, nx, ny)] ? w[wi(i, j, kp, nx, ny)] : 0.0;
                    wb = wet[ci(i, j, km, nx, ny)] ? w[wi(i, j, k, nx, ny)] : 0.0;
                    d[q] = (ue - uw) / dx + (vn - vs) / dy + (wt - wb) / dz;
                }
        return d;
    }

    public static void applyL(int nx, int ny, int nz, double dx, double dy, double dz,
                              boolean[] wet, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = ci(i, j, k, nx, ny);
                    if (!wet[q]) continue;
                    int jp = (j + 1) % ny, jm = (j - 1 + ny) % ny;
                    int kp = (k + 1) % nz, km = (k - 1 + nz) % nz;
                    double s = 0.0;
                    if (i > 0 && wet[ci(i - 1, j, k, nx, ny)]) s += (p[q] - p[ci(i - 1, j, k, nx, ny)]) / (dx * dx);
                    if (i < nx - 1 && wet[ci(i + 1, j, k, nx, ny)]) s += (p[q] - p[ci(i + 1, j, k, nx, ny)]) / (dx * dx);
                    if (wet[ci(i, jp, k, nx, ny)]) s += (p[q] - p[ci(i, jp, k, nx, ny)]) / (dy * dy);
                    if (wet[ci(i, jm, k, nx, ny)]) s += (p[q] - p[ci(i, jm, k, nx, ny)]) / (dy * dy);
                    if (wet[ci(i, j, kp, nx, ny)]) s += (p[q] - p[ci(i, j, kp, nx, ny)]) / (dz * dz);
                    if (wet[ci(i, j, km, nx, ny)]) s += (p[q] - p[ci(i, j, km, nx, ny)]) / (dz * dz);
                    out[q] = s;
                }
    }

    public static double[] solve(int nx, int ny, int nz, double dx, double dy, double dz,
                                 boolean[] wet, double[] div, int maxIter, double tol) {
        int n = nx * ny * nz;
        double[] p = new double[n], r = new double[n], q = new double[n], ap = new double[n];
        int cnt = 0;
        double mean = 0.0;
        for (int k = 0; k < n; k++) if (wet[k]) { mean += div[k]; cnt++; }
        if (cnt > 0) mean /= cnt;
        for (int k = 0; k < n; k++) if (wet[k]) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(nx, ny, nz, dx, dy, dz, wet, q, ap);
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
                                 double dx, double dy, double dz, boolean[] wet,
                                 double westFlux, double eastFlux, int maxIter, double tol) {
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                u[ui(0, j, k, nx, ny)] = westFlux;
                u[ui(nx, j, k, nx, ny)] = eastFlux;
            }
        for (int k = 0; k < nz; k++)
            for (int i = 0; i < nx; i++) v[vi(i, ny, k, nx, ny)] = v[vi(i, 0, k, nx, ny)];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) w[wi(i, j, nz, nx, ny)] = w[wi(i, j, 0, nx, ny)];

        double[] div = divergence(nx, ny, nz, dx, dy, dz, u, v, w, wet, westFlux, eastFlux);
        double[] p = solve(nx, ny, nz, dx, dy, dz, wet, div, maxIter, tol);

        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 1; i < nx; i++) {
                    int a = ci(i, j, k, nx, ny), b = ci(i - 1, j, k, nx, ny);
                    if (wet[a] && wet[b]) u[ui(i, j, k, nx, ny)] -= (p[a] - p[b]) / dx;
                    else u[ui(i, j, k, nx, ny)] = 0.0;
                }
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                int jm = (j - 1 + ny) % ny;
                for (int i = 0; i < nx; i++) {
                    int a = ci(i, j, k, nx, ny), b = ci(i, jm, k, nx, ny);
                    if (wet[a] && wet[b]) v[vi(i, j, k, nx, ny)] -= (p[a] - p[b]) / dy;
                    else v[vi(i, j, k, nx, ny)] = 0.0;
                }
            }
        for (int k = 0; k < nz; k++) {
            int km = (k - 1 + nz) % nz;
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int a = ci(i, j, k, nx, ny), b = ci(i, j, km, nx, ny);
                    if (wet[a] && wet[b]) w[wi(i, j, k, nx, ny)] -= (p[a] - p[b]) / dz;
                    else w[wi(i, j, k, nx, ny)] = 0.0;
                }
        }
        for (int k = 0; k < nz; k++)
            for (int i = 0; i < nx; i++) v[vi(i, ny, k, nx, ny)] = v[vi(i, 0, k, nx, ny)];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) w[wi(i, j, nz, nx, ny)] = w[wi(i, j, 0, nx, ny)];

        double[] d2 = divergence(nx, ny, nz, dx, dy, dz, u, v, w, wet, westFlux, eastFlux);
        double max = 0.0;
        for (int k = 0; k < d2.length; k++) if (wet[k]) max = Math.max(max, Math.abs(d2[k]));
        return max;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
