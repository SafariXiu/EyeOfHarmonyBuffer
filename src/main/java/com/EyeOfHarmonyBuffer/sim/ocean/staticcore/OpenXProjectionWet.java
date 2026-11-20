package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * Wet-mask x-open 3D projection prototype. Active cells are wet; dry neighbours
 * impose zero normal flux. Not wired into production yet.
 */
public final class OpenXProjectionWet {
    private OpenXProjectionWet() {}


    /** Wet-mask face divergence at cell centres. */
    public static double[] divergence(int nx, int ny, int nz, boolean[] wet,
                                      double[] uF, double[] vF, double[] wF) {
        int n = nx * ny * nz, nxny = nx * ny;
        double[] div = new double[n];
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    if (!wet[q]) continue;
                    double ue = (i + 1 < nx && wet[q + 1]) ? uF[q] : 0.0;
                    double uw = (i > 0 && wet[q - 1]) ? uF[q - 1] : 0.0;
                    double vn = (j + 1 < ny && wet[q + nx]) ? vF[q] : 0.0;
                    double vs = (j > 0 && wet[q - nx]) ? vF[q - nx] : 0.0;
                    double wt = (k + 1 < nz && wet[q + nxny]) ? wF[q] : 0.0;
                    double wb = (k > 0 && wet[q - nxny]) ? wF[q - nxny] : 0.0;
                    div[q] = ue - uw + vn - vs + wt - wb;
                }
        return div;
    }

    public static double project(int nx, int ny, int nz, boolean[] wet,
                                 double[] uF, double[] vF, double[] wF,
                                 int maxIter, double tol) {
        int n = nx * ny * nz, nxny = nx * ny;
        double[] div = new double[n], phi = new double[n], r = new double[n], p = new double[n], ap = new double[n];
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    if (!wet[q]) continue;
                    double ue = (i + 1 < nx && wet[q + 1]) ? uF[q] : 0.0;
                    double uw = (i > 0 && wet[q - 1]) ? uF[q - 1] : 0.0;
                    double vn = (j + 1 < ny && wet[q + nx]) ? vF[q] : 0.0;
                    double vs = (j > 0 && wet[q - nx]) ? vF[q - nx] : 0.0;
                    double wt = (k + 1 < nz && wet[q + nxny]) ? wF[q] : 0.0;
                    double wb = (k > 0 && wet[q - nxny]) ? wF[q - nxny] : 0.0;
                    div[q] = ue - uw + vn - vs + wt - wb;
                    r[q] = div[q];
                    p[q] = r[q];
                }
        // Neumann/dry boundaries: L has one nullspace per connected wet component.
        // Remove the component mean of div before CG.
        int[] comp = components(nx, ny, nz, wet);
        int nComp = 0;
        for (int c : comp) if (c >= 0) nComp = Math.max(nComp, c + 1);
        double[] sum = new double[nComp];
        int[] cnt = new int[nComp];
        for (int q = 0; q < n; q++) {
            if (!wet[q] || comp[q] < 0) continue;
            sum[comp[q]] += div[q];
            cnt[comp[q]]++;
        }
        for (int c = 0; c < nComp; c++) if (cnt[c] > 0) sum[c] /= cnt[c];
        for (int q = 0; q < n; q++) {
            if (!wet[q] || comp[q] < 0) continue;
            div[q] -= sum[comp[q]];
            r[q] = div[q];
            p[q] = r[q];
        }
        double rr = dot(r, r);
        double target = tol * tol * Math.max(1.0, dot(div, div));
        for (int it = 0; it < maxIter && rr > target; it++) {
            applyL(nx, ny, nz, wet, p, ap);
            double pap = dot(p, ap);
            if (!(pap > 1e-300)) break;
            double alpha = rr / pap;
            for (int q = 0; q < n; q++) { phi[q] += alpha * p[q]; r[q] -= alpha * ap[q]; }
            double rrNew = dot(r, r);
            double beta = rrNew / Math.max(1e-300, rr);
            for (int q = 0; q < n; q++) p[q] = r[q] + beta * p[q];
            rr = rrNew;
        }
        // Correct all faces adjacent to wet cells; dry-side faces are zero.
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    if (!wet[q]) continue;
                    if (i + 1 < nx && wet[q + 1]) uF[q] += phi[q + 1] - phi[q]; else uF[q] = 0.0;
                    if (j + 1 < ny && wet[q + nx]) vF[q] += phi[q + nx] - phi[q]; else vF[q] = 0.0;
                    if (k + 1 < nz && wet[q + nxny]) wF[q] += phi[q + nxny] - phi[q]; else wF[q] = 0.0;
                    if (i > 0 && !wet[q - 1]) uF[q - 1] = 0.0;
                    if (j > 0 && !wet[q - nx]) vF[q - nx] = 0.0;
                    if (k > 0 && !wet[q - nxny]) wF[q - nxny] = 0.0;
                }
        double max = 0.0;
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    if (!wet[q]) continue;
                    double ue = (i + 1 < nx && wet[q + 1]) ? uF[q] : 0.0;
                    double uw = (i > 0 && wet[q - 1]) ? uF[q - 1] : 0.0;
                    double vn = (j + 1 < ny && wet[q + nx]) ? vF[q] : 0.0;
                    double vs = (j > 0 && wet[q - nx]) ? vF[q - nx] : 0.0;
                    double wt = (k + 1 < nz && wet[q + nxny]) ? wF[q] : 0.0;
                    double wb = (k > 0 && wet[q - nxny]) ? wF[q - nxny] : 0.0;
                    max = Math.max(max, Math.abs(ue - uw + vn - vs + wt - wb));
                }
        return max;
    }


    static int[] components(int nx, int ny, int nz, boolean[] wet) {
        int n = nx * ny * nz, nxny = nx * ny;
        int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        int[] stack = new int[n];
        int cid = 0;
        for (int s = 0; s < n; s++) {
            if (!wet[s] || comp[s] >= 0) continue;
            int top = 0;
            stack[top++] = s;
            comp[s] = cid;
            while (top > 0) {
                int q = stack[--top];
                int i = q % nx, j = (q / nx) % ny, k = q / nxny;
                if (i + 1 < nx && wet[q + 1] && comp[q + 1] < 0) { comp[q + 1] = cid; stack[top++] = q + 1; }
                if (i > 0 && wet[q - 1] && comp[q - 1] < 0) { comp[q - 1] = cid; stack[top++] = q - 1; }
                if (j + 1 < ny && wet[q + nx] && comp[q + nx] < 0) { comp[q + nx] = cid; stack[top++] = q + nx; }
                if (j > 0 && wet[q - nx] && comp[q - nx] < 0) { comp[q - nx] = cid; stack[top++] = q - nx; }
                if (k + 1 < nz && wet[q + nxny] && comp[q + nxny] < 0) { comp[q + nxny] = cid; stack[top++] = q + nxny; }
                if (k > 0 && wet[q - nxny] && comp[q - nxny] < 0) { comp[q - nxny] = cid; stack[top++] = q - nxny; }
            }
            cid++;
        }
        return comp;
    }

    static void applyL(int nx, int ny, int nz, boolean[] wet, double[] p, double[] out) {
        java.util.Arrays.fill(out, 0.0);
        int nxny = nx * ny;
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    if (!wet[q]) continue;
                    double s = 0.0;
                    if (i + 1 < nx && wet[q + 1]) s += p[q] - p[q + 1];
                    if (i > 0 && wet[q - 1]) s += p[q] - p[q - 1];
                    if (j + 1 < ny && wet[q + nx]) s += p[q] - p[q + nx];
                    if (j > 0 && wet[q - nx]) s += p[q] - p[q - nx];
                    if (k + 1 < nz && wet[q + nxny]) s += p[q] - p[q + nxny];
                    if (k > 0 && wet[q - nxny]) s += p[q] - p[q - nxny];
                    out[q] = s;
                }
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }
}
