package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.ocean.staticcore.KppBoundaryLayer;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;

/**
 * 2026-11-22 (#4): 1D ice-atmosphere surface-layer column core.
 *
 * <p>This is the first, source-backed building block for the ABL module:
 * neutral log-profile surface exchange + K-theory diffusion in a 1D column.
 * It is NOT yet a full KPP/stable-boundary-layer model and is NOT wired into
 * production. Constants are sourced:
 *   von Karman 0.4, zref 10 m, ice roughness 0.0005 m
 * from Icepack icepack_parameters.F90 (vonkar, zref, iceruf).
 *
 * <p>Signs: H > 0 upward (surface to air), E > 0 upward, tau > 0.
 */
public final class AtmosphereBoundaryLayer1D {
    /** Source: Icepack icepack_parameters.F90. */
    public static final double VONK = 0.4;
    public static final double ZREF = 10.0;
    public static final double ICERUF = 0.0005;

    public final double[] z;       // heights [m], ascending
    public final double[] theta;   // potential temperature [K]
    public final double[] q;       // specific humidity [kg/kg]
    public final double[] u;       // [m/s]
    public final double[] v;       // [m/s]
    public static boolean DEBUG = false;
    public static int DEBUG_COUNT = 0;
    // MOM5 entrain.F90: beta_surf=0.23, Ashear=25.0, wentrmax=0.05 m/s.
    private static final double BETA_SURF = 0.23;
    private static final double ASHEAR = 25.0;
    private static final double WENTRMAX = 0.05;

    public AtmosphereBoundaryLayer1D(double[] heights) {
        if (heights == null || heights.length < 2) throw new IllegalArgumentException("need >=2 levels");
        for (int k = 1; k < heights.length; k++) {
            if (!(heights[k] > heights[k - 1])) throw new IllegalArgumentException("z must ascend");
        }
        this.z = heights.clone();
        int n = heights.length;
        theta = new double[n];
        q = new double[n];
        u = new double[n];
        v = new double[n];
    }

    /** KPP ABL state {obl [m], surface buoyancy flux [m2/s3], ustar [m/s]}. */
    public double[] ablState(double tsK, double qs, double rho, double cp) {
        double[] f = surfaceFlux(tsK, qs, rho, cp);
        double ustar = f[3];
        double bf = -9.80616 * (f[0] / (rho * cp)) / Math.max(200.0, theta[0]);
        double g = 9.80616;
        double obl = z[z.length - 1];
        double thS = tsK;
        double uS = u[0], vS = v[0];
        for (int k = 1; k < z.length; k++) {
            double du = u[k] - uS, dv = v[k] - vS;
            double den = du * du + dv * dv + KppBoundaryLayer.MIN_VT2;
            double ri = (g / Math.max(200.0, theta[0])) * z[k] * (theta[k] - thS) / den;
            if (ri > KppBoundaryLayer.RI_CRIT) {
                obl = Math.max(1.0, z[k]);
                break;
            }
        }
        return new double[]{Math.max(1.0, obl), bf, ustar};
    }

    /**
     * Net surface energy [W/m2], positive into the surface: absorbed solar +
     * emissivity*lwDown - emissivity*sigma*Ts^4 - H - E*Lv.
     */
    public double netSurfaceEnergy(double solarAbsorbed, double lwDown, double emissivity,
                                   double tsK, double qs, double rho, double cp, double lv) {
        double[] f = surfaceFlux(tsK, qs, rho, cp);
        double e = Math.max(0.0, Math.min(1.0, emissivity));
        return solarAbsorbed + e * lwDown - e * Radiation.SIGMA * Math.pow(tsK, 4)
                - f[0] - f[1] * lv;
    }

    /** Bisection surface temperature for netSurfaceEnergy=0 in [lo,hi]. */
    public double equilibriumSurfaceTemp(double solarAbsorbed, double lwDown, double emissivity,
                                         double qs, double rho, double cp, double lv,
                                         double lo, double hi) {
        double flo = netSurfaceEnergy(solarAbsorbed, lwDown, emissivity, lo, qs, rho, cp, lv);
        double fhi = netSurfaceEnergy(solarAbsorbed, lwDown, emissivity, hi, qs, rho, cp, lv);
        if (flo * fhi > 0.0) return 0.5 * (lo + hi);
        for (int it = 0; it < 40; it++) {
            double mid = 0.5 * (lo + hi);
            double fm = netSurfaceEnergy(solarAbsorbed, lwDown, emissivity, mid, qs, rho, cp, lv);
            if (fm * flo <= 0.0) { hi = mid; fhi = fm; } else { lo = mid; flo = fm; }
        }
        return 0.5 * (lo + hi);
    }

    /**
     * Coupled ice-surface step: advance the column and return the net surface
     * energy [W/m2] used for the ice/atmosphere exchange. No new constants.
     */
    public double coupledSurfaceStep(double dt, double iceSurfaceTemp, double iceQs,
                                     double freeTheta, double freeQ, double freeU, double freeV,
                                     double rho, double cp, double lv,
                                     double solarAbsorbed, double lwDown, double emissivity) {
        double net = netSurfaceEnergy(solarAbsorbed, lwDown, emissivity,
                iceSurfaceTemp, iceQs, rho, cp, lv);
        step(dt, iceSurfaceTemp, iceQs, freeTheta, freeQ, freeU, freeV, rho, cp);
        return net;
    }

    /** Neutral transfer coefficient at the lowest level. */
    public double transferCoefficient() {
        double ch = VONK / Math.log(Math.max(z[0], 1.01 * ICERUF) / ICERUF);
        return ch * ch;
    }

    /** {H [W/m2], E [kg/m2/s], tau [Pa], ustar [m/s]}, positive upward. */
    public double[] surfaceFlux(double tsK, double qs, double rho, double cp) {
        double ch = transferCoefficient();
        double wind = Math.hypot(u[0], v[0]);
        double w = Math.max(wind, 1.0e-3);
        double h = rho * cp * ch * w * (tsK - theta[0]);
        double e = rho * ch * w * (qs - q[0]);
        double tau = rho * ch * w * w;
        return new double[]{h, e, tau, Math.sqrt(ch) * w};
    }

    /**
     * One explicit neutral surface-layer step. The top level is relaxed to the
     * free-atmosphere state through the same K profile; no entrainment yet.
     */
    public void step(double dt, double tsK, double qs, double freeTheta, double freeQ,
                     double freeU, double freeV, double rho, double cp) {
        implicitStep(dt, tsK, qs, freeTheta, freeQ, freeU, freeV, rho, cp);
    }

    private void implicitStep(double dt, double tsK, double qs, double freeTheta, double freeQ,
                              double freeU, double freeV, double rho, double cp) {
        int n = z.length;
        double[] f = surfaceFlux(tsK, qs, rho, cp);
        if (!Double.isFinite(f[0]) || !Double.isFinite(f[3])) return;
        double[] ab = ablState(tsK, qs, rho, cp);
        double obl = ab[0], bf = ab[1], ustar = f[3];
        double ch = transferCoefficient();
        double wind = Math.max(1.0e-3, Math.hypot(u[0], v[0]));
        double A = ch * wind;

        double[] Kf = new double[n];
        for (int k = 1; k < n; k++) {
            double zf = 0.5 * (z[k - 1] + z[k]);
            double K = zf < obl
                    ? KppBoundaryLayer.scalarDiffusivity(zf / obl, obl, bf, ustar) : 0.0;
            Kf[k] = Double.isFinite(K) ? Math.max(0.0, K) : 0.0;
        }
        double ztop = z[n - 1] + 0.5 * (z[n - 1] - z[n - 2]);
        double Ktop = ztop < obl
                ? KppBoundaryLayer.scalarDiffusivity(Math.min(1.0, ztop / obl), obl, bf, ustar)
                : VONK * ustar * ztop;
        if (!Double.isFinite(Ktop)) Ktop = 0.0;
        Ktop = Math.max(0.0, Ktop);

        // MOM5 entrain.F90 bulk entrainment (surface-driven).
        double hML = Math.max(1.0, obl);
        double b0 = Math.max(0.0, -bf);
        double vsurf3 = b0 * hML;
        double vshear3 = ASHEAR * ustar * ustar * ustar;
        double jump = 9.80616 * Math.max(0.0, freeTheta - theta[n - 1]) / Math.max(1.0, theta[n - 1]);
        double src3 = vsurf3 + vshear3;
        double num = BETA_SURF * src3 / hML;
        double den = jump + Math.pow(Math.max(0.0, src3), 2.0 / 3.0) / hML;
        double we = den > 0.0 ? Math.min(WENTRMAX, Math.max(0.0, num / den)) : 0.0;
        double entCoef = we / hML;


        double[] a = new double[n], b = new double[n], c = new double[n], r = new double[n];
        // theta: diffusion + surface H + grey LW
        for (int k = 0; k < n; k++) {
            double dzc = (k == 0) ? z[0]
                    : (k == n - 1 ? z[n - 1] - z[n - 2] : 0.5 * (z[k + 1] - z[k - 1]));
            a[k] = 0.0; b[k] = 1.0; c[k] = 0.0; r[k] = theta[k];
            if (k > 0) {
                double coef = dt / dzc * Kf[k] / (z[k] - z[k - 1]);
                a[k] = -coef; b[k] += coef;
            }
            if (k < n - 1) {
                double coef = dt / dzc * Kf[k + 1] / (z[k + 1] - z[k]);
                c[k] = -coef; b[k] += coef;
            } else {
                double coef = dt / dzc * Ktop / Math.max(1.0, ztop - z[n - 1]);
                b[k] += coef; r[k] += coef * freeTheta;
            }
            if (k == 0) {
                double coef = dt / dzc * A;
                b[k] += coef; r[k] += coef * tsK;
            }
            double radCoef = dt * Radiation.SIGMA * 4.0 * Math.pow(theta[k], 3)
                    / (rho * cp * Math.max(1.0, z[k]));
            b[k] += radCoef;
            r[k] += dt * Radiation.SIGMA * (3.0 * Math.pow(theta[k], 4) + Math.pow(freeTheta, 4))
                    / (rho * cp * Math.max(1.0, z[k]));
            if (z[k] <= hML) {
                double rel = dt * entCoef;
                b[k] += rel; r[k] += rel * freeTheta;
            }
        }
        solveTri(a, b, c, r);
        System.arraycopy(r, 0, theta, 0, n);

        // q: diffusion + surface E
        for (int k = 0; k < n; k++) {
            double dzc = (k == 0) ? z[0]
                    : (k == n - 1 ? z[n - 1] - z[n - 2] : 0.5 * (z[k + 1] - z[k - 1]));
            a[k] = 0.0; b[k] = 1.0; c[k] = 0.0; r[k] = q[k];
            if (k > 0) {
                double coef = dt / dzc * Kf[k] / (z[k] - z[k - 1]);
                a[k] = -coef; b[k] += coef;
            }
            if (k < n - 1) {
                double coef = dt / dzc * Kf[k + 1] / (z[k + 1] - z[k]);
                c[k] = -coef; b[k] += coef;
            } else {
                double coef = dt / dzc * Ktop / Math.max(1.0, ztop - z[n - 1]);
                b[k] += coef; r[k] += coef * freeQ;
            }
            if (k == 0) {
                double coef = dt / dzc * A;
                b[k] += coef; r[k] += coef * qs;
            }
            if (z[k] <= hML) {
                double rel = dt * entCoef;
                b[k] += rel; r[k] += rel * freeQ;
            }
        }
        solveTri(a, b, c, r);
        for (int k = 0; k < n; k++) q[k] = Math.max(0.0, r[k]);

        // nonlocal flux already added to the theta/q implicit RHS above

        // u and v: implicit drag + diffusion
        implicitMomentum(dt, A, Kf, Ktop, ztop, freeU, rho, u);
        implicitMomentum(dt, A, Kf, Ktop, ztop, freeV, rho, v);
    }

    private void implicitMomentum(double dt, double A, double[] Kf, double Ktop,
                                  double ztop, double freeV, double rho, double[] vel) {
        int n = z.length;
        double[] a = new double[n], b = new double[n], c = new double[n], r = new double[n];
        for (int k = 0; k < n; k++) {
            double dzc = (k == 0) ? z[0]
                    : (k == n - 1 ? z[n - 1] - z[n - 2] : 0.5 * (z[k + 1] - z[k - 1]));
            a[k] = 0.0; b[k] = 1.0; c[k] = 0.0; r[k] = vel[k];
            if (k > 0) {
                double coef = dt / dzc * Kf[k] / (z[k] - z[k - 1]);
                a[k] = -coef; b[k] += coef;
            }
            if (k < n - 1) {
                double coef = dt / dzc * Kf[k + 1] / (z[k + 1] - z[k]);
                c[k] = -coef; b[k] += coef;
            } else {
                double coef = dt / dzc * Ktop / Math.max(1.0, ztop - z[n - 1]);
                b[k] += coef; r[k] += coef * freeV;
            }
            if (k == 0) b[k] += dt / dzc * A;
        }
        solveTri(a, b, c, r);
        System.arraycopy(r, 0, vel, 0, n);
    }

    private static void solveTri(double[] a, double[] b, double[] c, double[] r) {
        int n = b.length;
        for (int i = 1; i < n; i++) {
            double m = a[i] / (Math.abs(b[i - 1]) < 1.0e-30 ? 1.0e-30 : b[i - 1]);
            b[i] -= m * c[i - 1];
            r[i] -= m * r[i - 1];
        }
        r[n - 1] /= (Math.abs(b[n - 1]) < 1.0e-30 ? 1.0e-30 : b[n - 1]);
        for (int i = n - 2; i >= 0; i--) {
            r[i] = (r[i] - c[i] * r[i + 1])
                    / (Math.abs(b[i]) < 1.0e-30 ? 1.0e-30 : b[i]);
        }
    }

    private void explicitStep(double dt, double tsK, double qs, double freeTheta, double freeQ,
                              double freeU, double freeV, double rho, double cp) {
        double[] f0 = surfaceFlux(tsK, qs, rho, cp);
        if (!Double.isFinite(f0[0]) || !Double.isFinite(f0[1]) || !Double.isFinite(f0[3])) return;
        double[] ab0 = ablState(tsK, qs, rho, cp);
        double obl0 = ab0[0], bf0 = ab0[1], us0 = f0[3];
        double kMax = 0.0, dzMin = Double.POSITIVE_INFINITY;
        for (int k = 0; k < z.length - 1; k++) {
            double zf = 0.5 * (z[k] + z[k + 1]);
            double K = zf < obl0
                    ? KppBoundaryLayer.scalarDiffusivity(zf / obl0, obl0, bf0, us0)
                    : VONK * us0 * zf;
            if (!Double.isFinite(K)) K = VONK * us0 * zf;
            kMax = Math.max(kMax, K);
            dzMin = Math.min(dzMin, z[k + 1] - z[k]);
        }
        if (!(dzMin < Double.POSITIVE_INFINITY)) dzMin = z[0];
        double dtMax = 0.2 * dzMin * dzMin / Math.max(1.0e-12, kMax);
        // explicit surface-flux stability (numerical tolerances, not physics)
        double dtMaxT = 1.0 * dzMin * rho * cp / Math.max(1.0e-9, Math.abs(f0[0]));
        double dtMaxQ = 1.0e-5 * dzMin * rho / Math.max(1.0e-12, Math.abs(f0[1]));
        dtMax = Math.min(dtMax, Math.min(dtMaxT, dtMaxQ));
        int nSub = Math.max(1, (int) Math.ceil(dt / Math.max(1.0e-6, dtMax)));
        nSub = Math.min(nSub, 10000);
        double dts = dt / nSub;
        if (DEBUG && DEBUG_COUNT++ < 2) {
            System.out.println("ABLDBG dt=" + dt + " nSub=" + nSub + " dts=" + dts
                    + " kMax=" + kMax + " dtMax=" + dtMax + " th0=" + theta[0]
                    + " q0=" + q[0] + " H=" + f0[0] + " E=" + f0[1] + " obl=" + obl0 + " bf=" + bf0);
        }
        for (int s = 0; s < nSub; s++) {
            explicitStepOnce(dts, tsK, qs, freeTheta, freeQ, freeU, freeV, rho, cp, kMax);
            if (DEBUG && s < 5) {
                System.out.println("ABLSUB s=" + s + " th0=" + theta[0] + " q0=" + q[0]
                        + " th1=" + theta[1] + " q1=" + q[1] + " u0=" + u[0]);
            }
            if (DEBUG && !Double.isFinite(theta[0])) {
                System.out.println("ABLNaN s=" + s + " th=" + java.util.Arrays.toString(theta)
                        + " q=" + java.util.Arrays.toString(q) + " u=" + java.util.Arrays.toString(u));
                break;
            }
        }
        if (DEBUG && DEBUG_COUNT <= 2) {
            System.out.println("ABLDBG after th0=" + theta[0] + " q0=" + q[0]);
        }
    }

    private void explicitStepOnce(double dt, double tsK, double qs, double freeTheta, double freeQ,
                                  double freeU, double freeV, double rho, double cp, double kLimit) {
        int n = z.length;
        double[] f = surfaceFlux(tsK, qs, rho, cp);
        double ustar = f[3];
        double[] abl = ablState(tsK, qs, rho, cp);
        double obl = abl[0], bf = abl[1];
        double[] th = theta.clone();
        double[] qq = q.clone();
        double[] uu = u.clone();
        double[] vv = v.clone();
        double bottomT = f[0] / (rho * cp);
        double bottomQ = f[1] / rho;

        for (int k = 0; k < n; k++) {
            double dz = (k == 0) ? z[0]
                    : (k == n - 1 ? z[n - 1] - z[n - 2] : 0.5 * (z[k + 1] - z[k - 1]));
            double fBotT, fTopT, fBotQ, fTopQ, fBotU, fTopU, fBotV, fTopV;
            if (k == 0) {
                fBotT = bottomT; fBotQ = bottomQ;
                fBotU = 0.0; fBotV = 0.0;
            } else {
                double zf = 0.5 * (z[k - 1] + z[k]);
                double K = zf < obl
                        ? KppBoundaryLayer.scalarDiffusivity(zf / obl, obl, bf, ustar) : 0.0;
                if (!Double.isFinite(K)) K = 0.0;
                K = Math.min(K, kLimit);
                double dzf = z[k] - z[k - 1];
                fBotT = K * (theta[k] - theta[k - 1]) / dzf;
                fBotQ = K * (q[k] - q[k - 1]) / dzf;
                fBotU = K * (u[k] - u[k - 1]) / dzf;
                fBotV = K * (v[k] - v[k - 1]) / dzf;
            }
            if (k == n - 1) {
                double zf = z[n - 1] + 0.5 * (z[n - 1] - z[n - 2]);
                double K = zf < obl
                        ? KppBoundaryLayer.scalarDiffusivity(Math.min(1.0, zf / obl), obl, bf, ustar)
                        : VONK * ustar * zf;   // top always couples to free climate
                if (!Double.isFinite(K)) K = 0.0;
                K = Math.min(K, kLimit);
                double dzf = Math.max(1.0, zf - z[n - 1]);
                fTopT = K * (freeTheta - theta[n - 1]) / dzf;
                fTopQ = K * (freeQ - q[n - 1]) / dzf;
                fTopU = K * (freeU - u[n - 1]) / dzf;
                fTopV = K * (freeV - v[n - 1]) / dzf;
            } else {
                double zf = 0.5 * (z[k] + z[k + 1]);
                double K = zf < obl
                        ? KppBoundaryLayer.scalarDiffusivity(zf / obl, obl, bf, ustar) : 0.0;
                if (!Double.isFinite(K)) K = 0.0;
                K = Math.min(K, kLimit);
                double dzf = z[k + 1] - z[k];
                fTopT = K * (theta[k + 1] - theta[k]) / dzf;
                fTopQ = K * (q[k + 1] - q[k]) / dzf;
                fTopU = K * (u[k + 1] - u[k]) / dzf;
                fTopV = K * (v[k + 1] - v[k]) / dzf;
            }
            th[k] += dt * (fBotT - fTopT) / dz;
            // first-order grey longwave damping to the free atmosphere
            th[k] -= dt * Radiation.SIGMA
                    * (Math.pow(theta[k], 4) - Math.pow(freeTheta, 4))
                    / (rho * cp * Math.max(1.0, z[k]));
            qq[k] += dt * (fBotQ - fTopQ) / dz;
            if (k == 0) {
                // semi-implicit surface drag: C = tau/(rho*|V|) = ch*|V|
                double spd = Math.max(1.0e-3, Math.hypot(u[0], v[0]));
                double C = f[2] / (rho * spd);
                uu[0] = (u[0] - dt * fTopU / dz) / (1.0 + dt * C / dz);
                vv[0] = (v[0] - dt * fTopV / dz) / (1.0 + dt * C / dz);
            } else {
                uu[k] += dt * (fBotU - fTopU) / dz;
                vv[k] += dt * (fBotV - fTopV) / dz;
            }
        }
        System.arraycopy(th, 0, theta, 0, n);
        System.arraycopy(qq, 0, q, 0, n);
        System.arraycopy(uu, 0, u, 0, n);
        System.arraycopy(vv, 0, v, 0, n);
    }
}
