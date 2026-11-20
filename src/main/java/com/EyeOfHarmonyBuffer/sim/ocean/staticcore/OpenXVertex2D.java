package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 2D periodic vertex-centered dual pressure prototype (B3D).
 * Velocity at primal cell centres; pressure at primal vertices; dual divergence
 * uses 4-cell face averages; cell gradient uses 4-vertex averages.
 * Not wired into production.
 */
public final class OpenXVertex2D {
    private OpenXVertex2D() {}

    static int at(int i, int j, int n) {
        int ii = i % n; if (ii < 0) ii += n;
        int jj = j % n; if (jj < 0) jj += n;
        return jj * n + ii;
    }

    /** Dual divergence at vertices. */
    public static double[] divergence(int n, double[] u, double[] v) {
        double[] d = new double[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double fe = 0.5 * (u[at(i, j, n)] + u[at(i, j - 1, n)]);
                double fw = 0.5 * (u[at(i - 1, j, n)] + u[at(i - 1, j - 1, n)]);
                double fn = 0.5 * (v[at(i, j, n)] + v[at(i - 1, j, n)]);
                double fs = 0.5 * (v[at(i, j - 1, n)] + v[at(i - 1, j - 1, n)]);
                d[j * n + i] = fe - fw + fn - fs;
            }
        return d;
    }

    /** Cell gradient from vertex pressure. */
    public static void gradient(int n, double[] p, double[] gx, double[] gy) {
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double gxi = 0.5 * ((p[at(i + 1, j, n)] - p[at(i, j, n)])
                        + (p[at(i + 1, j + 1, n)] - p[at(i, j + 1, n)]));
                double gyi = 0.5 * ((p[at(i, j + 1, n)] - p[at(i, j, n)])
                        + (p[at(i + 1, j + 1, n)] - p[at(i + 1, j, n)]));
                gx[j * n + i] = gxi;
                gy[j * n + i] = gyi;
            }
    }

    static void applyA(int n, double[] p, double[] out) {
        double[] gx = new double[n * n], gy = new double[n * n];
        gradient(n, p, gx, gy);
        double[] d = divergence(n, gx, gy);
        for (int k = 0; k < out.length; k++) out[k] = -d[k];
    }

    /** Solve A p = div with periodic p, mean removed. */
    public static double[] solve(int n, double[] div, int maxIter, double tol) {
        double[] p = new double[n * n], r = new double[n * n], q = new double[n * n], ap = new double[n * n];
        double mean = 0.0;
        for (double x : div) mean += x;
        mean /= div.length;
        for (int k = 0; k < div.length; k++) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyA(n, q, ap);
            double pap = dot(q, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int k = 0; k < p.length; k++) { p[k] += alpha * q[k]; r[k] -= alpha * ap[k]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int k = 0; k < q.length; k++) q[k] = r[k] + beta * q[k];
            rr = rrNew;
        }
        return p;
    }

    public static double project(int n, double[] u, double[] v, int maxIter, double tol) {
        double[] div = divergence(n, u, v);
        double[] p = solve(n, div, maxIter, tol);
        double[] gx = new double[n * n], gy = new double[n * n];
        gradient(n, p, gx, gy);
        for (int k = 0; k < u.length; k++) { u[k] -= gx[k]; v[k] -= gy[k]; }
        double[] d2 = divergence(n, u, v);
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
