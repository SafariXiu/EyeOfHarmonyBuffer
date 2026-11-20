package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** 2D MAC projection with a wet mask; dry faces carry zero flux. Not production. */
public final class OpenXMAC2DWet {
    private OpenXMAC2DWet() {}

    public static double[] divergence(int nx, int ny, double dx, double dy,
                                      double[] u, double[] v, boolean[] wet,
                                      double westFlux, double eastFlux) {
        double[] d = new double[nx * ny];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int q = j * nx + i;
                if (!wet[q]) continue;
                int jp = (j + 1) % ny, jm = (j - 1 + ny) % ny;
                double ue, uw, vn, vs;
                if (i == nx - 1) ue = eastFlux;
                else ue = wet[q + 1] ? u[j * (nx + 1) + i + 1] : 0.0;
                if (i == 0) uw = westFlux;
                else uw = wet[q - 1] ? u[j * (nx + 1) + i] : 0.0;
                vn = wet[jp * nx + i] ? v[jp * nx + i] : 0.0;
                vs = wet[jm * nx + i] ? v[j * nx + i] : 0.0;
                d[q] = (ue - uw) / dx + (vn - vs) / dy;
            }
        return d;
    }

    public static void applyL(int nx, int ny, double dx, double dy, boolean[] wet, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int q = j * nx + i;
                if (!wet[q]) continue;
                int jp = (j + 1) % ny, jm = (j - 1 + ny) % ny;
                double x = 0.0;
                if (i > 0 && wet[q - 1]) x += (p[q] - p[q - 1]) / (dx * dx);
                if (i < nx - 1 && wet[q + 1]) x += (p[q] - p[q + 1]) / (dx * dx);
                double y = 0.0;
                if (wet[jp * nx + i]) y += (p[q] - p[jp * nx + i]) / (dy * dy);
                if (wet[jm * nx + i]) y += (p[q] - p[jm * nx + i]) / (dy * dy);
                out[q] = x + y;
            }
    }

    public static double[] solve(int nx, int ny, double dx, double dy, boolean[] wet, double[] div,
                                 int maxIter, double tol) {
        int n = nx * ny;
        double[] p = new double[n], r = new double[n], q = new double[n], ap = new double[n];
        int cnt = 0;
        double mean = 0.0;
        for (int k = 0; k < n; k++) if (wet[k]) { mean += div[k]; cnt++; }
        if (cnt > 0) mean /= cnt;
        for (int k = 0; k < n; k++) if (wet[k]) { r[k] = -(div[k] - mean); q[k] = r[k]; }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(nx, ny, dx, dy, wet, q, ap);
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

    public static double project(int nx, int ny, double[] u, double[] v, double dx, double dy,
                                 boolean[] wet, double westFlux, double eastFlux,
                                 int maxIter, double tol) {
        for (int j = 0; j < ny; j++) {
            u[j * (nx + 1)] = westFlux;
            u[j * (nx + 1) + nx] = eastFlux;
        }
        for (int i = 0; i < nx; i++) v[ny * nx + i] = v[i];
        double[] div = divergence(nx, ny, dx, dy, u, v, wet, westFlux, eastFlux);
        double[] p = solve(nx, ny, dx, dy, wet, div, maxIter, tol);
        for (int j = 0; j < ny; j++)
            for (int i = 1; i < nx; i++) {
                int q = j * nx + i;
                if (wet[q] && wet[q - 1]) u[j * (nx + 1) + i] -= (p[q] - p[q - 1]) / dx;
                else u[j * (nx + 1) + i] = 0.0;
            }
        for (int j = 0; j < ny; j++) {
            int jm = (j - 1 + ny) % ny;
            for (int i = 0; i < nx; i++) {
                int q = j * nx + i;
                if (wet[q] && wet[jm * nx + i]) v[j * nx + i] -= (p[q] - p[jm * nx + i]) / dy;
                else v[j * nx + i] = 0.0;
            }
        }
        for (int i = 0; i < nx; i++) v[ny * nx + i] = v[i];
        double[] d2 = divergence(nx, ny, dx, dy, u, v, wet, westFlux, eastFlux);
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
