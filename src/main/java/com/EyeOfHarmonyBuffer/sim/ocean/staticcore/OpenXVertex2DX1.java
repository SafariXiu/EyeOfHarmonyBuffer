package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 2D X1 vertex-centered dual pressure prototype: x-open half dual cells, y periodic.
 * Boundary normal flux is parent-provided and fixed; pressure gauge with V weights.
 * Not wired into production.
 */
public final class OpenXVertex2DX1 {
    private OpenXVertex2DX1() {}

    static int vp(int i, int j, int nx, int ny) {
        int jj = j % ny; if (jj < 0) jj += ny;
        return i * ny + jj;
    }

    static int cp(int i, int j, int nx, int ny) {
        int jj = j % ny; if (jj < 0) jj += ny;
        return jj * nx + i;
    }

    public static double[] divergence(int nx, int ny, double[] u, double[] v,
                                      double dx, double dy, double westFlux, double eastFlux) {
        double[] d = new double[(nx + 1) * ny];
        for (int i = 0; i <= nx; i++)
            for (int j = 0; j < ny; j++) {
                double fe, fw, fn, fs, vol;
                if (i == 0) {
                    fe = 0.5 * (u[cp(0, j, nx, ny)] + u[cp(0, j - 1, nx, ny)]) * dy;
                    fw = westFlux * dy;
                    fn = v[cp(0, j, nx, ny)] * (0.5 * dx);
                    fs = v[cp(0, j - 1, nx, ny)] * (0.5 * dx);
                    vol = 0.5 * dx * dy;
                } else if (i == nx) {
                    fe = eastFlux * dy;
                    fw = 0.5 * (u[cp(nx - 1, j, nx, ny)] + u[cp(nx - 1, j - 1, nx, ny)]) * dy;
                    fn = v[cp(nx - 1, j, nx, ny)] * (0.5 * dx);
                    fs = v[cp(nx - 1, j - 1, nx, ny)] * (0.5 * dx);
                    vol = 0.5 * dx * dy;
                } else {
                    fe = 0.5 * (u[cp(i, j, nx, ny)] + u[cp(i, j - 1, nx, ny)]) * dy;
                    fw = 0.5 * (u[cp(i - 1, j, nx, ny)] + u[cp(i - 1, j - 1, nx, ny)]) * dy;
                    fn = 0.5 * (v[cp(i, j, nx, ny)] + v[cp(i - 1, j, nx, ny)]) * dx;
                    fs = 0.5 * (v[cp(i, j - 1, nx, ny)] + v[cp(i - 1, j - 1, nx, ny)]) * dx;
                    vol = dx * dy;
                }
                d[i * ny + j] = (fe - fw + fn - fs) / vol;
            }
        return d;
    }

    public static void gradient(int nx, int ny, double[] p, double dx, double dy,
                                double[] gx, double[] gy) {
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                double gxi = 0.5 * ((p[vp(i + 1, j, nx, ny)] - p[vp(i, j, nx, ny)])
                        + (p[vp(i + 1, j + 1, nx, ny)] - p[vp(i, j + 1, nx, ny)])) / dx;
                double gyi = 0.5 * ((p[vp(i, j + 1, nx, ny)] - p[vp(i, j, nx, ny)])
                        + (p[vp(i + 1, j + 1, nx, ny)] - p[vp(i + 1, j, nx, ny)])) / dy;
                gx[cp(i, j, nx, ny)] = gxi;
                gy[cp(i, j, nx, ny)] = gyi;
            }
    }

    static void applyB(int nx, int ny, double[] p, double dx, double dy, double[] out) {
        double[] gx = new double[nx * ny], gy = new double[nx * ny];
        gradient(nx, ny, p, dx, dy, gx, gy);
        double[] d = divergence(nx, ny, gx, gy, dx, dy, 0.0, 0.0);
        for (int q = 0; q < out.length; q++) out[q] = -d[q];
    }

    public static double[] solve(int nx, int ny, double dx, double dy, double[] div,
                                 int maxIter, double tol) {
        int m = (nx + 1) * ny;
        double[] p = new double[m], r = new double[m], q = new double[m], ap = new double[m];
        double[] v = new double[m];
        for (int i = 0; i <= nx; i++)
            for (int j = 0; j < ny; j++)
                v[i * ny + j] = (i == 0 || i == nx) ? 0.5 * dx * dy : dx * dy;
        double total = 0.0, mean = 0.0;
        for (int k = 0; k < m; k++) { total += v[k]; mean += v[k] * div[k]; }
        if (total > 0) mean /= total;
        for (int k = 0; k < m; k++) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dotV(v, r, r);
        double target = tol * tol * Math.max(1.0, dotV(v, div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyB(nx, ny, q, dx, dy, ap);
            double pap = dotV(v, q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int k = 0; k < m; k++) { p[k] += alpha * q[k]; r[k] -= alpha * ap[k]; }
            double rrNew = dotV(v, r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int k = 0; k < m; k++) q[k] = r[k] + beta * q[k];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int nx, int ny, double[] u, double[] v,
                                 double dx, double dy, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        double[] div = divergence(nx, ny, u, v, dx, dy, westFlux, eastFlux);
        double[] p = solve(nx, ny, dx, dy, div, maxIter, tol);
        double[] gx = new double[nx * ny], gy = new double[nx * ny];
        gradient(nx, ny, p, dx, dy, gx, gy);
        for (int q = 0; q < u.length; q++) { u[q] -= gx[q]; v[q] -= gy[q]; }
        double[] d2 = divergence(nx, ny, u, v, dx, dy, westFlux, eastFlux);
        double max = 0.0;
        for (double x : d2) max = Math.max(max, Math.abs(x));
        return max;
    }

    private static double dotV(double[] w, double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += w[i] * a[i] * b[i];
        return s;
    }
}
