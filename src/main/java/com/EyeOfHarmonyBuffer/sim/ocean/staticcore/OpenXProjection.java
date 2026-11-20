package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * Standalone x-open 3D projection prototype (all-wet). It uses the validated
 * x-ghost 3D Poisson operator and a simple face correction; it is NOT wired into
 * VelocityProjection yet. Purpose: verify that the x-open pressure correction
 * removes synthetic 3D divergence before adding wet masks.
 */
public final class OpenXProjection {
    private OpenXProjection() {}

    /** Face divergence at cell centres for an all-wet nx*ny*nz box. */
    public static double[] divergence(int nx, int ny, int nz, double[] uF, double[] vF, double[] wF) {
        double[] d = new double[nx * ny * nz];
        int nxny = nx * ny;
        for (int k = 1; k < nz - 1; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    d[q] = (uF[q] - uF[q - 1])
                            + (vF[q] - vF[q - nx])
                            + (wF[q] - wF[q - nxny]);
                }
        return d;
    }

    /** Project an all-wet velocity field; returns max |div| after projection. */
    public static double projectAllWet(int nx, int ny, int nz,
                                       double[] uF, double[] vF, double[] wF,
                                       int maxIter, double tol) {
        double[] d = divergence(nx, ny, nz, uF, vF, wF);
        OpenXPoisson.Result r = OpenXPoisson.solve3D(nx, ny, nz, d, maxIter, tol);
        double[] phi = r.phi;
        int nxny = nx * ny;
        // Correct all faces adjacent to interior cells (faces at the domain edge use phi=0 ghost).
        for (int k = 1; k < nz - 1; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    double phiE = (i + 1 < nx) ? phi[q + 1] : 0.0;
                    uF[q] += phiE - phi[q];
                }
        for (int k = 1; k < nz - 1; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    double phiN = (j + 1 < ny) ? phi[q + nx] : 0.0;
                    vF[q] += phiN - phi[q];
                }
        for (int k = 0; k < nz; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 1; i < nx - 1; i++) {
                    int q = (k * ny + j) * nx + i;
                    double phiT = (k + 1 < nz) ? phi[q + nxny] : 0.0;
                    wF[q] += phiT - phi[q];
                }
        double[] d2 = divergence(nx, ny, nz, uF, vF, wF);
        double max = 0.0;
        for (double v : d2) max = Math.max(max, Math.abs(v));
        return max;
    }
}
