package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 3D X1 vertex-centered dual pressure prototype: x-open half dual cells, y/z periodic.
 * Boundary normal flux is parent-provided and fixed; pressure gauge with V weights.
 * Not wired into production.
 */
public final class OpenXVertex3DX1 {
    private OpenXVertex3DX1() {}

    static int vp(int i, int j, int k, int nx, int ny, int nz) {
        int jj = j % ny; if (jj < 0) jj += ny;
        int kk = k % nz; if (kk < 0) kk += nz;
        return (i * ny + jj) * nz + kk;
    }

    static int cp(int i, int j, int k, int nx, int ny, int nz) {
        int jj = j % ny; if (jj < 0) jj += ny;
        int kk = k % nz; if (kk < 0) kk += nz;
        return (kk * ny + jj) * nx + i;
    }

    public static double[] divergence(int nx, int ny, int nz, double[] u, double[] v, double[] w,
                                      double dx, double dy, double dz, double westFlux, double eastFlux) {
        double[] wf = new double[ny * nz], ef = new double[ny * nz];
        java.util.Arrays.fill(wf, westFlux);
        java.util.Arrays.fill(ef, eastFlux);
        return divergenceArr(nx, ny, nz, u, v, w, dx, dy, dz, wf, ef);
    }

    public static double[] divergenceArr(int nx, int ny, int nz, double[] u, double[] v, double[] w,
                                         double dx, double dy, double dz, double[] westFlux, double[] eastFlux) {
        double[] d = new double[(nx + 1) * ny * nz];
        for (int i = 0; i <= nx; i++)
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nz; k++) {
                    double fe, fw, fn, fs, ft, fb, vol;
                    if (i == 0) {
                        fe = 0.25 * (u[cp(0, j, k, nx, ny, nz)] + u[cp(0, j - 1, k, nx, ny, nz)]
                                + u[cp(0, j, k - 1, nx, ny, nz)] + u[cp(0, j - 1, k - 1, nx, ny, nz)]) * dy * dz;
                        fw = westFlux[j * nz + k] * dy * dz;
                        fn = 0.5 * (v[cp(0, j, k, nx, ny, nz)] + v[cp(0, j, k - 1, nx, ny, nz)]) * (0.5 * dx * dz);
                        fs = 0.5 * (v[cp(0, j - 1, k, nx, ny, nz)] + v[cp(0, j - 1, k - 1, nx, ny, nz)]) * (0.5 * dx * dz);
                        ft = 0.5 * (w[cp(0, j, k, nx, ny, nz)] + w[cp(0, j - 1, k, nx, ny, nz)]) * (0.5 * dx * dy);
                        fb = 0.5 * (w[cp(0, j, k - 1, nx, ny, nz)] + w[cp(0, j - 1, k - 1, nx, ny, nz)]) * (0.5 * dx * dy);
                        vol = 0.5 * dx * dy * dz;
                    } else if (i == nx) {
                        fe = eastFlux[j * nz + k] * dy * dz;
                        fw = 0.25 * (u[cp(nx - 1, j, k, nx, ny, nz)] + u[cp(nx - 1, j - 1, k, nx, ny, nz)]
                                + u[cp(nx - 1, j, k - 1, nx, ny, nz)] + u[cp(nx - 1, j - 1, k - 1, nx, ny, nz)]) * dy * dz;
                        fn = 0.5 * (v[cp(nx - 1, j, k, nx, ny, nz)] + v[cp(nx - 1, j, k - 1, nx, ny, nz)]) * (0.5 * dx * dz);
                        fs = 0.5 * (v[cp(nx - 1, j - 1, k, nx, ny, nz)] + v[cp(nx - 1, j - 1, k - 1, nx, ny, nz)]) * (0.5 * dx * dz);
                        ft = 0.5 * (w[cp(nx - 1, j, k, nx, ny, nz)] + w[cp(nx - 1, j - 1, k, nx, ny, nz)]) * (0.5 * dx * dy);
                        fb = 0.5 * (w[cp(nx - 1, j, k - 1, nx, ny, nz)] + w[cp(nx - 1, j - 1, k - 1, nx, ny, nz)]) * (0.5 * dx * dy);
                        vol = 0.5 * dx * dy * dz;
                    } else {
                        fe = 0.25 * (u[cp(i, j, k, nx, ny, nz)] + u[cp(i, j - 1, k, nx, ny, nz)]
                                + u[cp(i, j, k - 1, nx, ny, nz)] + u[cp(i, j - 1, k - 1, nx, ny, nz)]) * dy * dz;
                        fw = 0.25 * (u[cp(i - 1, j, k, nx, ny, nz)] + u[cp(i - 1, j - 1, k, nx, ny, nz)]
                                + u[cp(i - 1, j, k - 1, nx, ny, nz)] + u[cp(i - 1, j - 1, k - 1, nx, ny, nz)]) * dy * dz;
                        fn = 0.25 * (v[cp(i, j, k, nx, ny, nz)] + v[cp(i - 1, j, k, nx, ny, nz)]
                                + v[cp(i, j, k - 1, nx, ny, nz)] + v[cp(i - 1, j, k - 1, nx, ny, nz)]) * dx * dz;
                        fs = 0.25 * (v[cp(i, j - 1, k, nx, ny, nz)] + v[cp(i - 1, j - 1, k, nx, ny, nz)]
                                + v[cp(i, j - 1, k - 1, nx, ny, nz)] + v[cp(i - 1, j - 1, k - 1, nx, ny, nz)]) * dx * dz;
                        ft = 0.25 * (w[cp(i, j, k, nx, ny, nz)] + w[cp(i - 1, j, k, nx, ny, nz)]
                                + w[cp(i, j - 1, k, nx, ny, nz)] + w[cp(i - 1, j - 1, k, nx, ny, nz)]) * dx * dy;
                        fb = 0.25 * (w[cp(i, j, k - 1, nx, ny, nz)] + w[cp(i - 1, j, k - 1, nx, ny, nz)]
                                + w[cp(i, j - 1, k - 1, nx, ny, nz)] + w[cp(i - 1, j - 1, k - 1, nx, ny, nz)]) * dx * dy;
                        vol = dx * dy * dz;
                    }
                    d[(i * ny + j) * nz + k] = (fe - fw + fn - fs + ft - fb) / vol;
                }
        return d;
    }

    public static void gradient(int nx, int ny, int nz, double[] p, double dx, double dy, double dz,
                                double[] gx, double[] gy, double[] gz) {
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    double gxi = 0.25 * ((p[vp(i + 1, j, k, nx, ny, nz)] - p[vp(i, j, k, nx, ny, nz)])
                            + (p[vp(i + 1, j + 1, k, nx, ny, nz)] - p[vp(i, j + 1, k, nx, ny, nz)])
                            + (p[vp(i + 1, j, k + 1, nx, ny, nz)] - p[vp(i, j, k + 1, nx, ny, nz)])
                            + (p[vp(i + 1, j + 1, k + 1, nx, ny, nz)] - p[vp(i, j + 1, k + 1, nx, ny, nz)])) / dx;
                    double gyi = 0.25 * ((p[vp(i, j + 1, k, nx, ny, nz)] - p[vp(i, j, k, nx, ny, nz)])
                            + (p[vp(i + 1, j + 1, k, nx, ny, nz)] - p[vp(i + 1, j, k, nx, ny, nz)])
                            + (p[vp(i, j + 1, k + 1, nx, ny, nz)] - p[vp(i, j, k + 1, nx, ny, nz)])
                            + (p[vp(i + 1, j + 1, k + 1, nx, ny, nz)] - p[vp(i + 1, j, k + 1, nx, ny, nz)])) / dy;
                    double gzi = 0.25 * ((p[vp(i, j, k + 1, nx, ny, nz)] - p[vp(i, j, k, nx, ny, nz)])
                            + (p[vp(i + 1, j, k + 1, nx, ny, nz)] - p[vp(i + 1, j, k, nx, ny, nz)])
                            + (p[vp(i, j + 1, k + 1, nx, ny, nz)] - p[vp(i, j + 1, k, nx, ny, nz)])
                            + (p[vp(i + 1, j + 1, k + 1, nx, ny, nz)] - p[vp(i + 1, j + 1, k, nx, ny, nz)])) / dz;
                    int q = cp(i, j, k, nx, ny, nz);
                    gx[q] = gxi; gy[q] = gyi; gz[q] = gzi;
                }
    }

    public static double[] applyBPublic(int nx, int ny, int nz, double[] p, double dx, double dy, double dz) {
        double[] out = new double[(nx + 1) * ny * nz];
        applyB(nx, ny, nz, p, dx, dy, dz, out);
        return out;
    }

    static void applyB(int nx, int ny, int nz, double[] p, double dx, double dy, double dz, double[] out) {
        int m = nx * ny * nz;
        double[] gx = new double[m], gy = new double[m], gz = new double[m];
        gradient(nx, ny, nz, p, dx, dy, dz, gx, gy, gz);
        double[] d = divergence(nx, ny, nz, gx, gy, gz, dx, dy, dz, 0.0, 0.0);
        for (int q = 0; q < out.length; q++) out[q] = -d[q];
    }

    public static double[] solve(int nx, int ny, int nz, double dx, double dy, double dz, double[] div,
                                 int maxIter, double tol) {
        int m = (nx + 1) * ny * nz;
        double[] p = new double[m], r = new double[m], q = new double[m], ap = new double[m];
        double[] v = new double[m];
        for (int i = 0; i <= nx; i++)
            for (int j = 0; j < ny; j++)
                for (int k = 0; k < nz; k++)
                    v[(i * ny + j) * nz + k] = (i == 0 || i == nx) ? 0.5 * dx * dy * dz : dx * dy * dz;
        double total = 0.0, mean = 0.0;
        for (int ii = 0; ii < m; ii++) { total += v[ii]; mean += v[ii] * div[ii]; }
        if (total > 0) mean /= total;
        for (int ii = 0; ii < m; ii++) { r[ii] = -(div[ii] - mean); q[ii] = r[ii]; }
        double rr = dotV(v, r, r);
        double target = tol * tol * Math.max(1.0, dotV(v, div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyB(nx, ny, nz, q, dx, dy, dz, ap);
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

    public static double project(int nx, int ny, int nz, double[] u, double[] v, double[] w,
                                 double dx, double dy, double dz, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        double[] div = divergence(nx, ny, nz, u, v, w, dx, dy, dz, westFlux, eastFlux);
        double[] p = solve(nx, ny, nz, dx, dy, dz, div, maxIter, tol);
        int m = nx * ny * nz;
        double[] gx = new double[m], gy = new double[m], gz = new double[m];
        gradient(nx, ny, nz, p, dx, dy, dz, gx, gy, gz);
        for (int q = 0; q < u.length; q++) { u[q] -= gx[q]; v[q] -= gy[q]; w[q] -= gz[q]; }
        double[] d2 = divergence(nx, ny, nz, u, v, w, dx, dy, dz, westFlux, eastFlux);
        double max = 0.0;
        for (double x : d2) max = Math.max(max, Math.abs(x));
        return max;
    }


    public static double projectArr(int nx, int ny, int nz, double[] u, double[] v, double[] w,
                                    double dx, double dy, double dz, double[] westFlux, double[] eastFlux,
                                    int maxIter, double tol) {
        double[] div = divergenceArr(nx, ny, nz, u, v, w, dx, dy, dz, westFlux, eastFlux);
        double[] p = solve(nx, ny, nz, dx, dy, dz, div, maxIter, tol);
        int m = nx * ny * nz;
        double[] gx = new double[m], gy = new double[m], gz = new double[m];
        gradient(nx, ny, nz, p, dx, dy, dz, gx, gy, gz);
        for (int q = 0; q < u.length; q++) { u[q] -= gx[q]; v[q] -= gy[q]; w[q] -= gz[q]; }
        double[] d2 = divergenceArr(nx, ny, nz, u, v, w, dx, dy, dz, westFlux, eastFlux);
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
