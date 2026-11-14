package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.Thermohaline;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * Adiabatic sub-grid transport: Gent-McWilliams eddy-induced transport (GM).
 *
 * <p>Why: the local geostrophic velocity has no energy sink at 2.5deg x 5deg, so
 * frontogenesis runs away (evidence: R3 design doc sections 18-20). The physical
 * sink is mesoscale eddies transporting buoyancy ALONG isopycnals: adiabatic,
 * no work, no net heat input. v1 did this implicitly with its zonally-uniform
 * 2D overturning cell; here it is written explicitly as GM.
 *
 * <p>Formulas (Gent and McWilliams 1990; Gent et al. 1995; Griffies 1998):
 *   slope        s_x = -rho_x / rho_z ,  s_y = -rho_y / rho_z   (rho_z less than 0)
 *   streamfn     Psi_x = kappa * s_x  ,  Psi_y = kappa * s_y    [m2/s]
 *   eddy vel.    u* = -d(Psi_x)/dz , v* = -d(Psi_y)/dz
 *                w* =  d(Psi_x)/dx + d(Psi_y)/dy
 *
 * <p>The three components are exactly non-divergent and Psi vanishes at the
 * top/bottom interfaces, so depth-integral(u*) = 0: GM does NOT change the net
 * transport, it only re-arranges tracers along isopycnals. That is adiabatic.
 *
 * <p>kappa uses the existing AdvectionDiffusionSolver.KH = 1000 m2/s (standard
 * coarse-resolution value, Gent et al. 1995 / Griffies 1998), not a new constant.
 * Slope clipping |s| at most 0.1 is the standard numerical guard (Griffies 1998).
 */
public final class EddyTransport {
    private EddyTransport() {}

    /** On by default. Only used on the R3 path; v1 fallback stays bit-identical. */
    public static boolean ON = true;
    public static double KAPPA = AdvectionDiffusionSolver.KH;
    public static double SLOPE_MAX = 0.1;
    /**
     * Stratification floor for the GM taper [1/s]: kappa_eff = kappa * N^2/(N^2+N0^2).
     * N0 = 1e-4 s^-1 is a physical scale (buoyancy period ~ 17 h); where N^2 is much
     * smaller (mixed layer, convecting columns) the isopycnal slope is undefined and GM
     * must switch off - otherwise the slope blows up and the eddy velocity becomes noise.
     * Standard GM tapering (Danabasoglu and Marshall 1996 use the same form).
     */
    public static double N0 = 1.0e-4;

    public static double LAST_USTAR_MAX = 0.0;
    public static double LAST_VSTAR_MAX = 0.0;
    public static double LAST_WSTAR_MAX = 0.0;
    public static int LAST_U_J = -1, LAST_U_K = -1;
    public static double LAST_U_AT_MAX = 0.0;
    /** Domain integral of (u* . grad rho) dV; GM must make this <= 0 (down-gradient). */
    public static double LAST_APEFLUX = 0.0;
    /** Same integral but only over levels 2..wl-3 (away from the boundary taper). */
    public static double LAST_APEFLUX_INTERIOR = 0.0;

    /** Density with the same EOS as the momentum solver (UNESCO when EOS80_ON). */
    private static double rho(OceanState st, int q) {
        if (BaroclinicFlow.EOS80_ON) return Eos80.density0K(st.s[q], st.t[q]);
        return AdvectionDiffusionSolver.RHO0
            * (1.0 - Thermohaline.ALPHA * (st.t[q] - 280.0) + Thermohaline.BETA * (st.s[q] - 35.0));
    }

    /** Add the GM face-velocity increments to st.uF/vF/wF (call AFTER the projection). */
    public static void apply(OceanGrid g, OceanState st) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        int n3 = nx * ny * nz;
        double[] px = new double[n3], py = new double[n3];
        double[] rho = new double[n3];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) rho[st.idx(i, j, k)] = rho(st, st.idx(i, j, k));
            }
        // 斜率必须来自**大尺度**密度场：原始 rho 的格点噪声会让 s 在相邻层之间
        // 在 +0.1/-0.1 之间跳变，u* = -dPsi/dz 就会出现 2 m/s 的假速度（实测）。
        // 标准做法：先用 3x3x3 盒式平均粗粒化，再算斜率（不是调参，是斜率的分辨率要求）。
        double[] rhoS = new double[n3];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    double sum = 0.0;
                    int cnt = 0;
                    for (int jj = j - 2; jj <= j + 2; jj++)
                        for (int ii = i - 2; ii <= i + 2; ii++)
                            for (int kk = Math.max(0, k - 2); kk <= Math.min(wl - 1, k + 2); kk++) {
                                if (!g.isWet(ii, jj, kk)) continue;
                                sum += rho[st.idx(ii, jj, kk)];
                                cnt++;
                            }
                    rhoS[st.idx(i, j, k)] = cnt > 0 ? sum / cnt : rho[st.idx(i, j, k)];
                }
            }
        System.arraycopy(rhoS, 0, rho, 0, n3);
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
                    // ★ rho_z must be d(rho)/d(z_UP). The model k index increases DOWNWARD,
                    // so (rho[k+1]-rho[k-1]) is d(rho)/d(z_down) = -d(rho)/d(z_up).
                    // Without this minus the stable case (rho increasing downward) gives
                    // rz > 0, the clamp below fires everywhere, slopes saturate at +-0.1 and
                    // u* comes out as ~1 m/s of noise (round-24 bug, caught by GMTEST).
                    double rz;
                    if (k == 0) rz = -(rho[st.idx(i, j, 1)] - rho[q]) / (0.5 * (g.dz[0] + g.dz[1]));
                    else if (k == wl - 1) rz = -(rho[q] - rho[st.idx(i, j, k - 1)]) / (0.5 * (g.dz[k] + g.dz[k - 1]));
                    else rz = -(rho[st.idx(i, j, k + 1)] - rho[st.idx(i, j, k - 1)])
                              / (0.5 * (g.dz[k + 1] + g.dz[k]) + 0.5 * (g.dz[k] + g.dz[k - 1]));
                    if (rz > -1.0e-6) rz = -1.0e-6;
                    double sx = -rx / rz;
                    double sy = -ry / rz;
                    // Clip by ZEROING, not by pinning at the limit: pinning manufactures a
                    // constant artificial slope (and therefore a constant Psi) wherever the
                    // real slope is steeper, which is exactly what produced the fake 1 m/s.
                    if (Math.abs(sx) > SLOPE_MAX || Math.abs(sy) > SLOPE_MAX) { sx = 0.0; sy = 0.0; }
                    // 边界锥化：GM 流函数在上下边界必须为 0（标准边界条件）。
                    // 不做锥化时，表层 s 很大而 Psi(surface)=0，u*=(0-100)/50=2 m/s 的
                    // 假速度会出现在每个第一层（实测 U*=V*=2.0000 就是它）。
                    double taper = 1.0;
                    if (k == 0 || k == wl - 1) taper = 0.0;
                    else if (k == 1 || k == wl - 2) taper = 0.5;
                    // N^2 taper: GM must vanish where the column is not stratified.
                    double bVert = WorldContract.GRAVITY / AdvectionDiffusionSolver.RHO0;
                    double n2 = -bVert * rz;              // rz is already d(rho)/d(z_up)
                    double n2t = n2 > 0.0 ? n2 : 0.0;
                    // HARD gate: where N^2 is below the stratification floor the isopycnal
                    // slope is not defined at all (mixed layer / convecting columns), so
                    // switch GM off there instead of keeping a noisy slope with a small kappa.
                    if (n2t < N0 * N0) { sx = 0.0; sy = 0.0; }
                    double kap = KAPPA * n2t / (n2t + N0 * N0);
                    px[q] = taper * kap * sx;
                    py[q] = taper * kap * sy;
                }
            }
        // Vertically smooth Psi (3 passes of 1-2-1). u* = -dPsi/dz, so a slope that
        // changes by O(0.1) between two 50 m cells gives 2 m/s of fake eddy velocity.
        // GM streamfunction smoothing is standard practice (Gent et al. 1995).
        {
            double[] t1 = new double[n3], t2 = new double[n3];
            for (int pass = 0; pass < 3; pass++) {
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int wl = g.wetLayers[g.idx(i, j)];
                        for (int k = 0; k < wl; k++) {
                            int q = st.idx(i, j, k);
                            if (k == 0 || k == wl - 1) { t1[q] = 0.0; t2[q] = 0.0; continue; }
                            t1[q] = 0.25 * px[st.idx(i, j, k - 1)] + 0.5 * px[q] + 0.25 * px[st.idx(i, j, k + 1)];
                            t2[q] = 0.25 * py[st.idx(i, j, k - 1)] + 0.5 * py[q] + 0.25 * py[st.idx(i, j, k + 1)];
                        }
                    }
                System.arraycopy(t1, 0, px, 0, n3);
                System.arraycopy(t2, 0, py, 0, n3);
            }
        }
        double uMax = 0, vMax = 0, wMax = 0, ape = 0.0, apeInt = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                if (wl < 3) continue;
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    double pUp = (k > 0) ? 0.5 * (px[q] + px[st.idx(i, j, k - 1)]) : 0.0;
                    double pDn = (k + 1 < wl) ? 0.5 * (px[q] + px[st.idx(i, j, k + 1)]) : 0.0;
                    double uC = -(pUp - pDn) / g.dz[k];
                    double qUp = (k > 0) ? 0.5 * (py[q] + py[st.idx(i, j, k - 1)]) : 0.0;
                    double qDn = (k + 1 < wl) ? 0.5 * (py[q] + py[st.idx(i, j, k + 1)]) : 0.0;
                    double vC = -(qUp - qDn) / g.dz[k];
                    double wC = 0.0;
                    if (g.isWet(i + 1, j, k) && g.isWet(i - 1, j, k))
                        wC += (px[st.idx(i + 1, j, k)] - px[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
                    if (g.isWet(i, j + 1, k) && g.isWet(i, j - 1, k))
                        wC += (py[st.idx(i, j + 1, k)] - py[st.idx(i, j - 1, k)]) / (2.0 * g.dy);
                    double uF = g.isWet(i + 1, j, k) ? 0.5 * (uC + cellU(g, st, px, i + 1, j, k)) : 0.0;
                    double vF = g.isWet(i, j + 1, k) ? 0.5 * (vC + cellV(g, st, py, i, j + 1, k)) : 0.0;
                    double wF = (k + 1 < wl) ? 0.5 * (wC + cellW(g, st, px, py, i, j, k + 1)) : 0.0;
                    st.uF[q] += uF;
                    st.vF[q] += vF;
                    st.wF[q] += wF;
                    if (Math.abs(uC) > uMax) { uMax = Math.abs(uC); LAST_U_J = j; LAST_U_K = k; LAST_U_AT_MAX = uC; }
                    vMax = Math.max(vMax, Math.abs(vC));
                    wMax = Math.max(wMax, Math.abs(wC));
                    double rx = 0.0, ry = 0.0, rz = 0.0;
                    if (g.isWet(i + 1, j, k) && g.isWet(i - 1, j, k))
                        rx = (rho[st.idx(i + 1, j, k)] - rho[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
                    if (g.isWet(i, j + 1, k) && g.isWet(i, j - 1, k))
                        ry = (rho[st.idx(i, j + 1, k)] - rho[st.idx(i, j - 1, k)]) / (2.0 * g.dy);
                    // same z_UP convention as the slope didiagnostic: k+1 is BELOW
                    if (k > 0) rz = -(rho[q] - rho[st.idx(i, j, k - 1)]) / g.dz[k];
                    else if (k + 1 < wl) rz = -(rho[st.idx(i, j, k + 1)] - rho[q]) / g.dz[k];
                    double contrib = (uC * rx + vC * ry + wC * rz) * g.dz[k] * g.dx * g.dy * g.cosLat(j);
                    ape += contrib;
                    if (k >= 2 && k <= wl - 3) apeInt += contrib;
                }
            }
        LAST_USTAR_MAX = uMax;
        LAST_VSTAR_MAX = vMax;
        LAST_WSTAR_MAX = wMax;
        LAST_APEFLUX = ape;
        LAST_APEFLUX_INTERIOR = apeInt;
    }

    private static double cellU(OceanGrid g, OceanState st, double[] px, int i, int j, int k) {
        int wl = g.wetLayers[g.idx(i, j)];
        if (k >= wl) return 0.0;
        int q = st.idx(i, j, k);
        double pUp = (k > 0) ? 0.5 * (px[q] + px[st.idx(i, j, k - 1)]) : 0.0;
        double pDn = (k + 1 < wl) ? 0.5 * (px[q] + px[st.idx(i, j, k + 1)]) : 0.0;
        return -(pUp - pDn) / g.dz[Math.min(k, g.nz - 1)];
    }

    private static double cellV(OceanGrid g, OceanState st, double[] py, int i, int j, int k) {
        int wl = g.wetLayers[g.idx(i, j)];
        if (k >= wl) return 0.0;
        int q = st.idx(i, j, k);
        double qUp = (k > 0) ? 0.5 * (py[q] + py[st.idx(i, j, k - 1)]) : 0.0;
        double qDn = (k + 1 < wl) ? 0.5 * (py[q] + py[st.idx(i, j, k + 1)]) : 0.0;
        return -(qUp - qDn) / g.dz[Math.min(k, g.nz - 1)];
    }

    private static double cellW(OceanGrid g, OceanState st, double[] px, double[] py, int i, int j, int k) {
        int wl = g.wetLayers[g.idx(i, j)];
        if (k >= wl) return 0.0;
        double wC = 0.0;
        if (g.isWet(i + 1, j, k) && g.isWet(i - 1, j, k))
            wC += (px[st.idx(i + 1, j, k)] - px[st.idx(i - 1, j, k)]) / (2.0 * g.dx);
        if (g.isWet(i, j + 1, k) && g.isWet(i, j - 1, k))
            wC += (py[st.idx(i, j + 1, k)] - py[st.idx(i, j - 1, k)]) / (2.0 * g.dy);
        return wC;
    }
}