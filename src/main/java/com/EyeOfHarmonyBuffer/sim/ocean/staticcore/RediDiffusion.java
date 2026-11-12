package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;

/**
 * Redi isopycnal diffusion (the other half of the adiabatic closure).
 *
 * <p>Mature coarse-resolution models (MITgcm, MOM6, NEMO, POP) always pair GM with
 * Redi. GM only re-arranges along isopycnals; Redi actually diffuses tracers ALONG
 * them, which is what smooths the density field that GM reads. Without it the model
 * keeps its grid-scale isopycnal roughness.
 *
 * <p>Implementation: small-slope tensor (Griffies 1998), horizontal fluxes only:
 * <pre>
 *   F_x = -kappa * (dT/dx - s_x * dT/dz)
 *   F_y = -kappa * (dT/dy - s_y * dT/dz)
 *   tendency = -(dF_x/dx + dF_y/dy)
 * </pre>
 * The vertical part of the tensor is NOT included here: it needs an implicit solve
 * (kappa*s^2*dt/dz^2 is enormous), and that is the documented next step.
 *
 * <p>kappa = AdvectionDiffusionSolver.KH = 1000 m2/s (same source as GM).
 */
public final class RediDiffusion {
    private RediDiffusion() {}

    /**
     * Default OFF: this is an R3-path experiment. Turning it on changes the v1 production
     * path too (PrognosticT/Salinity call it unconditionally), so it must stay off until a
     * new baseline is pinned together with the R3 work.
     */
    public static boolean ON = false;
    public static double KAPPA = AdvectionDiffusionSolver.KH;
    public static double SLOPE_MAX = 0.1;
    public static double LAST_MAX_RATE = 0.0;

    /** Isopycnal slope at cell centres (same convention as EddyTransport: z up). */
    static void slopes(OceanGrid g, OceanState st, double[] sx, double[] sy) {
        int nx = g.nx, ny = g.ny;
        double[] rho = new double[st.t.length];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    rho[q] = BaroclinicFlow.EOS80_ON
                        ? Eos80.density0K(st.s[q], st.t[q])
                        : AdvectionDiffusionSolver.RHO0
                            * (1.0 - Thermohaline.ALPHA * (st.t[q] - 280.0) + Thermohaline.BETA * (st.s[q] - 35.0));
                }
            }
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl < 3) continue;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    boolean xe = g.isWet(i + 1, j, k), xw = g.isWet(i - 1, j, k);
                    boolean yn = g.isWet(i, j + 1, k), ys = g.isWet(i, j - 1, k);
                    double rx = 0.0, ry = 0.0;
                    if (xe && xw) rx = (rho[st.idx(i + 1, j, k)] - rho[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
                    if (yn && ys) ry = (rho[st.idx(i, j + 1, k)] - rho[st.idx(i, j - 1, k)]) / (2.0 * g.dy);
                    double rz;
                    if (k == 0) rz = -(rho[st.idx(i, j, 1)] - rho[q]) / (0.5 * (g.dz[0] + g.dz[1]));
                    else if (k == wl - 1) rz = -(rho[q] - rho[st.idx(i, j, k - 1)]) / (0.5 * (g.dz[k] + g.dz[k - 1]));
                    else rz = -(rho[st.idx(i, j, k + 1)] - rho[st.idx(i, j, k - 1)])
                              / (0.5 * (g.dz[k + 1] + g.dz[k]) + 0.5 * (g.dz[k] + g.dz[k - 1]));
                    if (rz > -1.0e-6) rz = -1.0e-6;
                    double a = -rx / rz, b = -ry / rz;
                    if (Math.abs(a) > SLOPE_MAX || Math.abs(b) > SLOPE_MAX) { a = 0.0; b = 0.0; }
                    sx[q] = a; sy[q] = b;
                }
            }
    }

    /** Add the Redi tendency (per second) to out[]. */
    public static void tendency(OceanGrid g, OceanState st, double[] fld, double[] out) {
        int nx = g.nx, ny = g.ny;
        int n3 = fld.length;
        double[] sx = new double[n3], sy = new double[n3];
        slopes(g, st, sx, sy);
        double maxRate = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double f = fld[q];
                    double fz = 0.0;
                    if (k > 0) fz = (fld[q] - fld[st.idx(i, j, k - 1)]) / g.dz[k];
                    else if (k + 1 < wl) fz = (fld[st.idx(i, j, k + 1)] - f) / g.dz[k];
                    // fluxes at east / west faces of this cell (centred in y for the slope)
                    double fxE = 0.0, fxW = 0.0;
                    if (g.isWet(i + 1, j, k)) {
                        int qe = st.idx(i + 1, j, k);
                        double fxe = (fld[qe] - f) / g.dx - 0.5 * (sx[q] + sx[qe]) * fz;
                        fxE = -KAPPA * fxe;
                    }
                    if (g.isWet(i - 1, j, k)) {
                        int qw = st.idx(i - 1, j, k);
                        double fxw = (f - fld[qw]) / g.dx - 0.5 * (sx[q] + sx[qw]) * fz;
                        fxW = -KAPPA * fxw;
                    }
                    double fyN = 0.0, fyS = 0.0;
                    if (g.isWet(i, j + 1, k)) {
                        int qn = st.idx(i, j + 1, k);
                        double fyn = (fld[qn] - f) / g.dy - 0.5 * (sy[q] + sy[qn]) * fz;
                        fyN = -KAPPA * fyn;
                    }
                    if (g.isWet(i, j - 1, k)) {
                        int qs = st.idx(i, j - 1, k);
                        double fys = (f - fld[qs]) / g.dy - 0.5 * (sy[q] + sy[qs]) * fz;
                        fyS = -KAPPA * fys;
                    }
                    double tend = -(fxE - fxW) / g.dx - (fyN - fyS) / g.dy;
                    out[q] += tend;
                    if (Math.abs(tend) > maxRate) maxRate = Math.abs(tend);
                }
            }
        LAST_MAX_RATE = maxRate;
    }
}
