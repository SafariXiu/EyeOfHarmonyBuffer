package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 2026-11-21 (#4 步骤③): 1D conservative incremental remap core.
 *
 * <p>Piecewise-linear reconstruction (minmod) + departure interval integration.
 * This is the 1D building block for an operator-split horizontal remap; it is NOT
 * the full 2D CICE departure-region scheme yet. No physical constants are introduced.
 *
 * <p>Coordinates are cell units: cell i spans [i*dx,(i+1)*dx].
 */
public final class IceTransportRemap {
    private IceTransportRemap() {}

    /** Remap q in place. faceVel has n+1 faces; periodic wraps face n to face 0. */
    public static void remap1D(double[] q, double[] faceVel, double dx, double dt, boolean periodic) {
        int n = q.length;
        if (n == 0) return;
        double[] slope = new double[n];
        for (int i = 0; i < n; i++) {
            double dl = (i > 0) ? (q[i] - q[i - 1]) / dx
                    : (periodic ? (q[0] - q[n - 1]) / dx : 0.0);
            double dr = (i < n - 1) ? (q[i + 1] - q[i]) / dx
                    : (periodic ? (q[0] - q[n - 1]) / dx : 0.0);
            if (dl * dr <= 0.0) slope[i] = 0.0;
            else slope[i] = Math.abs(dl) < Math.abs(dr) ? dl : dr;
        }
        double[] qNew = new double[n];
        for (int i = 0; i < n; i++) {
            double l = i * dx - faceVel[i] * dt;
            double r = (i + 1) * dx - faceVel[i + 1] * dt;
            if (r < l) { double t = l; l = r; r = t; }
            double sum;
            if (periodic) {
                while (l < 0.0) { l += n * dx; r += n * dx; }
                while (l >= n * dx) { l -= n * dx; r -= n * dx; }
                if (r <= n * dx) {
                    sum = integrate(q, slope, dx, l, r);
                } else {
                    sum = integrate(q, slope, dx, l, n * dx)
                            + integrate(q, slope, dx, 0.0, r - n * dx);
                }
            } else {
                l = Math.max(0.0, l);
                r = Math.min(n * dx, r);
                sum = r > l ? integrate(q, slope, dx, l, r) : 0.0;
            }
            qNew[i] = sum / dx;
        }
        System.arraycopy(qNew, 0, q, 0, n);
    }

    private static double integrate(double[] q, double[] slope, double dx,
                                    double l, double r) {
        int n = q.length;
        int j0 = (int) Math.floor(l / dx);
        int j1 = (int) Math.ceil(r / dx);
        double sum = 0.0;
        for (int j = j0; j < j1; j++) {
            if (j < 0 || j >= n) continue;
            double a = Math.max(l, j * dx);
            double b = Math.min(r, (j + 1) * dx);
            if (b <= a) continue;
            double mid = 0.5 * (a + b);
            double qm = q[j] + slope[j] * (mid - (j + 0.5) * dx);
            sum += qm * (b - a);
        }
        return sum;
    }
}
