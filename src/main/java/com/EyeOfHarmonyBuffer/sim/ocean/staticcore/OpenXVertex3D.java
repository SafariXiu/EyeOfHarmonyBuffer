package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 3D periodic vertex-centered dual pressure prototype (B3D).
 * Velocity at primal cell centres; pressure at primal vertices; dual divergence
 * averages 4 cells per dual face; cell gradient averages 4 vertices per component.
 * Not wired into production.
 */
public final class OpenXVertex3D {
    private OpenXVertex3D() {}

    static int at(int i, int j, int k, int n) {
        int ii = i % n; if (ii < 0) ii += n;
        int jj = j % n; if (jj < 0) jj += n;
        int kk = k % n; if (kk < 0) kk += n;
        return (kk * n + jj) * n + ii;
    }

    public static double[] divergence(int n, double[] u, double[] v, double[] w) {
        double[] d = new double[n * n * n];
        for (int k = 0; k < n; k++)
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double fe = 0.25 * (u[at(i, j, k, n)] + u[at(i, j - 1, k, n)]
                            + u[at(i, j, k - 1, n)] + u[at(i, j - 1, k - 1, n)]);
                    double fw = 0.25 * (u[at(i - 1, j, k, n)] + u[at(i - 1, j - 1, k, n)]
                            + u[at(i - 1, j, k - 1, n)] + u[at(i - 1, j - 1, k - 1, n)]);
                    double fn = 0.25 * (v[at(i, j, k, n)] + v[at(i - 1, j, k, n)]
                            + v[at(i, j, k - 1, n)] + v[at(i - 1, j, k - 1, n)]);
                    double fs = 0.25 * (v[at(i, j - 1, k, n)] + v[at(i - 1, j - 1, k, n)]
                            + v[at(i, j - 1, k - 1, n)] + v[at(i - 1, j - 1, k - 1, n)]);
                    double ft = 0.25 * (w[at(i, j, k, n)] + w[at(i - 1, j, k, n)]
                            + w[at(i, j - 1, k, n)] + w[at(i - 1, j - 1, k, n)]);
                    double fb = 0.25 * (w[at(i, j, k - 1, n)] + w[at(i - 1, j, k - 1, n)]
                            + w[at(i, j - 1, k - 1, n)] + w[at(i - 1, j - 1, k - 1, n)]);
                    d[(k * n + j) * n + i] = fe - fw + fn - fs + ft - fb;
                }
        return d;
    }

    public static void gradient(int n, double[] p, double[] gx, double[] gy, double[] gz) {
        for (int k = 0; k < n; k++)
            for (int j = 0; j < n; j++)
                for (int i = 0; i < n; i++) {
                    double gxi = 0.25 * ((p[at(i + 1, j, k, n)] - p[at(i, j, k, n)])
                            + (p[at(i + 1, j + 1, k, n)] - p[at(i, j + 1, k, n)])
                            + (p[at(i + 1, j, k + 1, n)] - p[at(i, j, k + 1, n)])
                            + (p[at(i + 1, j + 1, k + 1, n)] - p[at(i, j + 1, k + 1, n)]));
                    double gyi = 0.25 * ((p[at(i, j + 1, k, n)] - p[at(i, j, k, n)])
                            + (p[at(i + 1, j + 1, k, n)] - p[at(i + 1, j, k, n)])
                            + (p[at(i, j + 1, k + 1, n)] - p[at(i, j, k + 1, n)])
                            + (p[at(i + 1, j + 1, k + 1, n)] - p[at(i + 1, j, k + 1, n)]));
                    double gzi = 0.25 * ((p[at(i, j, k + 1, n)] - p[at(i, j, k, n)])
                            + (p[at(i + 1, j, k + 1, n)] - p[at(i + 1, j, k, n)])
                            + (p[at(i, j + 1, k + 1, n)] - p[at(i, j + 1, k, n)])
                            + (p[at(i + 1, j + 1, k + 1, n)] - p[at(i + 1, j + 1, k, n)]));
                    int q = (k * n + j) * n + i;
                    gx[q] = gxi; gy[q] = gyi; gz[q] = gzi;
                }
    }

    static void applyA(int n, double[] p, double[] out) {
        double[] gx = new double[n * n * n], gy = new double[n * n * n], gz = new double[n * n * n];
        gradient(n, p, gx, gy, gz);
        double[] d = divergence(n, gx, gy, gz);
        for (int q = 0; q < out.length; q++) out[q] = -d[q];
    }

    public static double[] solve(int n, double[] div, int maxIter, double tol) {
        int m = n * n * n;
        double[] p = new double[m], r = new double[m], q = new double[m], ap = new double[m];
        double mean = 0.0;
        for (double x : div) mean += x;
        mean /= m;
        for (int k = 0; k < m; k++) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyA(n, q, ap);
            double pap = dot(q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int k = 0; k < m; k++) { p[k] += alpha * q[k]; r[k] -= alpha * ap[k]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int k = 0; k < m; k++) q[k] = r[k] + beta * q[k];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int n, double[] u, double[] v, double[] w, int maxIter, double tol) {
        double[] div = divergence(n, u, v, w);
        double[] p = solve(n, div, maxIter, tol);
        double[] gx = new double[n * n * n], gy = new double[n * n * n], gz = new double[n * n * n];
        gradient(n, p, gx, gy, gz);
        for (int q = 0; q < u.length; q++) { u[q] -= gx[q]; v[q] -= gy[q]; w[q] -= gz[q]; }
        double[] d2 = divergence(n, u, v, w);
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
