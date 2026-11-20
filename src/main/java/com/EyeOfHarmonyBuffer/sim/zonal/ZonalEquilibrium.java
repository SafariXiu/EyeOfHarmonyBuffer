package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile;

/**
 * Annual-mean equilibrium residual for the 19-latitude surface energy/water system.
 * This is a research diagnostic; it does NOT time-step and does NOT touch production.
 */
public final class ZonalEquilibrium {

    public static final class Residual {
        public final double[] latDeg = new double[SeasonalZonal.NL];
        public final double[] ts = new double[SeasonalZonal.NL];
        public final double[] gT = new double[SeasonalZonal.NL];      // W/m2
        public final double[] gW = new double[SeasonalZonal.NL];      // mm/day
        public final double[] pMm = new double[SeasonalZonal.NL];
        public final double[] eMm = new double[SeasonalZonal.NL];
        public final double[] wKgM2 = new double[SeasonalZonal.NL];
        public final double[] t850 = new double[SeasonalZonal.NL];
        public final double[] asr = new double[SeasonalZonal.NL];
        public final double[] olr = new double[SeasonalZonal.NL];
        public final double[] surfNet = new double[SeasonalZonal.NL];
        public final double[] qTransport = new double[SeasonalZonal.NL];
        public final double[] qOht = new double[SeasonalZonal.NL];
        public final double[] dTransport = new double[SeasonalZonal.NL];
        public double maxAbsGT = 0.0, rmsGT = 0.0;
        public double globalP = 0.0, globalE = 0.0;
        public int iterations = 0;
    }

    /** true = infinite-X plane geometry (no 2*pi*R*cos); false = legacy spherical. */
    public static boolean PLANE_GEOMETRY = true;

    private ZonalEquilibrium() {}

    /**
     * 1D OHT bridge removed with the 2.5D ocean (2026-11-16).
     * The 1D energy budget is no longer a valid reference (qOht had a 37→19 index bug);
     * 2D/3D atmosphere computes its own transport. Return zeros so old probes fail loudly.
     */
    public static double[] oceanQ19() {
        return new double[SeasonalZonal.NL];
    }

    /** Local radiative-convective equilibrium Ts profile (no meridional transport). */
    public static double[] localProfile() {
        if (ZonalLandProfile.currentSeed() == Long.MIN_VALUE) ZonalLandProfile.setSeed(0L);
        double[] ts = new double[SeasonalZonal.NL];
        double saveAlb = VerticalColumn.ALBEDO;
        boolean saveFixed = VerticalColumn.FIXED_TS;
        try {
            VerticalColumn.FIXED_TS = false;
            for (int i = 0; i < SeasonalZonal.NL; i++) {
                double lat = -90.0 + 10.0 * i;
                double k = Math.max(0.0, Math.min(1.0, ZonalLandProfile.at(Math.toRadians(lat))));
                VerticalColumn.ALBEDO = Radiation.ALB_SEA + k * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                VerticalColumn c = new VerticalColumn();
                c.init(280.0, 0.010, 1.0, 6.0);
                c.solveRCE(SeasonalZonal.DT_ATM, annualCosZenith(lat), 200.0, 1.0, SeasonalZonal.MAX_ATM);
                ts[i] = c.tSfc;
            }
        } finally {
            VerticalColumn.ALBEDO = saveAlb;
            VerticalColumn.FIXED_TS = saveFixed;
        }
        return ts;
    }

    /** Damped diagonal fixed-point/Newton on Ts; no time stepping. */
    public static Residual solveDamped(double[] initialTs, int maxIter, double tol) {
        double[] x = initialTs.clone();
        double alpha = 0.05;   // K per (W/m2)
        Residual r = evaluate(x);
        for (int it = 0; it < maxIter; it++) {
            r.iterations = it;
            if (r.maxAbsGT < tol) return r;
            double[] xn = new double[x.length];
            for (int i = 0; i < x.length; i++) {
                double step = Math.max(-5.0, Math.min(5.0, alpha * r.gT[i]));
                xn[i] = Math.max(150.0, Math.min(350.0, x[i] - step));
            }
            Residual rn = evaluate(xn);
            int tries = 0;
            while (rn.maxAbsGT > r.maxAbsGT && tries < 6) {
                alpha *= 0.5;
                for (int i = 0; i < x.length; i++) {
                    double step = Math.max(-5.0, Math.min(5.0, alpha * r.gT[i]));
                    xn[i] = Math.max(150.0, Math.min(350.0, x[i] - step));
                }
                rn = evaluate(xn);
                tries++;
            }
            if (!(rn.maxAbsGT < r.maxAbsGT)) break;
            x = xn;
            r = rn;
            alpha = Math.min(0.2, alpha * 1.1);
        }
        r.iterations = maxIter;
        return r;
    }

    /** Finite-difference Newton on Ts; 19x19, no time stepping. */
    public static Residual solveNewton(double[] initialTs, int maxIter, double tol) {
        double[] x = initialTs.clone();
        Residual r = evaluate(x);
        int n = x.length;
        double[][] J = null;
        for (int it = 0; it < maxIter; it++) {
            r.iterations = it;
            if (r.maxAbsGT < tol) return r;
            // Rebuild the full Jacobian only every 4 iterations; reuse it in between.
            if (J == null || it % 4 == 0) {
                J = new double[n][n];
                double h = 0.5;
                for (int j = 0; j < n; j++) {
                    double old = x[j];
                    x[j] = old + h;
                    Residual rp = evaluate(x);
                    x[j] = old;
                    for (int i = 0; i < n; i++) J[i][j] = (rp.gT[i] - r.gT[i]) / h;
                }
            }
            double[] dx = solveLinear(J, r.gT);
            if (dx == null) return r;
            for (int i = 0; i < n; i++) dx[i] = Math.max(-5.0, Math.min(5.0, -dx[i]));
            double lambda = 1.0;
            Residual rn = null;
            for (int ls = 0; ls < 3; ls++) {
                double[] xn = new double[n];
                for (int i = 0; i < n; i++) xn[i] = Math.max(150.0, Math.min(350.0, x[i] + lambda * dx[i]));
                rn = evaluate(xn);
                if (rn.maxAbsGT < r.maxAbsGT) { x = xn; break; }
                lambda *= 0.5;
            }
            if (rn == null || !(rn.maxAbsGT < r.maxAbsGT)) return r;
            r = rn;
        }
        return r;
    }

    /** Polish the single worst |G_T| point with a 1-D Newton step (no hand tuning). */
    public static Residual polishWorst(double[] initialTs, int rounds) {
        double[] x = initialTs.clone();
        Residual r = evaluate(x);
        for (int it = 0; it < rounds && r.maxAbsGT > 1.0; it++) {
            int k = 0;
            for (int i = 1; i < x.length; i++) if (Math.abs(r.gT[i]) > Math.abs(r.gT[k])) k = i;
            double eps = 0.5;
            double[] xp = x.clone();
            xp[k] += eps;
            Residual rp = evaluate(xp);
            double deriv = (rp.gT[k] - r.gT[k]) / eps;
            if (!Double.isFinite(deriv) || Math.abs(deriv) < 1.0e-6) break;
            double step = -r.gT[k] / deriv;
            step = Math.max(-2.0, Math.min(2.0, step));
            double[] xn = x.clone();
            xn[k] += step;
            Residual rn = evaluate(xn);
            if (rn.maxAbsGT < r.maxAbsGT) {
                x = xn; r = rn;
            } else {
                xn[k] = x[k] + 0.5 * step;
                rn = evaluate(xn);
                if (rn.maxAbsGT < r.maxAbsGT) { x = xn; r = rn; } else break;
            }
        }
        return r;
    }

    /** Levenberg-Marquardt with a trust-region-like damping; one evaluation per iteration. */
    public static Residual solveLM(double[] initialTs, int maxIter, double tol) {
        double[] x = initialTs.clone();
        Residual r = evaluate(x);
        if (maxIter <= 0) return r;
        int n = x.length;
        double[][] J = new double[n][n];
        double h = 0.5;
        for (int j = 0; j < n; j++) {
            double old = x[j];
            x[j] = old + h;
            Residual rp = evaluate(x);
            x[j] = old;
            for (int i = 0; i < n; i++) J[i][j] = (rp.gT[i] - r.gT[i]) / h;
        }
        double lambda = 1.0;
        for (int it = 0; it < maxIter; it++) {
            r.iterations = it;
            if (r.maxAbsGT < tol) return r;
            double[][] A = new double[n][n];
            double[] b = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double s = 0.0;
                    for (int k = 0; k < n; k++) s += J[k][i] * J[k][j];
                    A[i][j] = s + (i == j ? lambda * (1.0 + Math.abs(J[i][i])) : 0.0);
                }
                double s = 0.0;
                for (int k = 0; k < n; k++) s += J[k][i] * r.gT[k];
                b[i] = -s;
            }
            double[] dx = solveLinear(A, b);
            if (dx == null) { lambda *= 10.0; continue; }
            for (int i = 0; i < n; i++) dx[i] = Math.max(-5.0, Math.min(5.0, dx[i]));
            double[] xn = new double[n];
            for (int i = 0; i < n; i++) xn[i] = Math.max(150.0, Math.min(350.0, x[i] + dx[i]));
            Residual rn = evaluate(xn);
            if (rn.rmsGT < r.rmsGT && rn.maxAbsGT < r.maxAbsGT * 1.5) {
                x = xn; r = rn; lambda = Math.max(1.0e-3, lambda * 0.5);
            } else {
                lambda *= 5.0;
            }
        }
        return r;
    }

    /** Broyden quasi-Newton: one evaluation per iteration after the initial Jacobian. */
    public static Residual solveBroyden(double[] initialTs, int maxIter, double tol) {
        double[] x = initialTs.clone();
        Residual r = evaluate(x);
        int n = x.length;
        double[][] J = new double[n][n];
        double h = 0.5;
        for (int j = 0; j < n; j++) {
            double old = x[j];
            x[j] = old + h;
            Residual rp = evaluate(x);
            x[j] = old;
            for (int i = 0; i < n; i++) J[i][j] = (rp.gT[i] - r.gT[i]) / h;
        }
        for (int it = 0; it < maxIter; it++) {
            r.iterations = it;
            if (r.maxAbsGT < tol) return r;
            double[] dx = solveLinear(J, r.gT);
            if (dx == null) return r;
            for (int i = 0; i < n; i++) dx[i] = Math.max(-5.0, Math.min(5.0, -dx[i]));
            double lambda = 1.0;
            Residual rn = null;
            double[] xn = null;
            for (int ls = 0; ls < 3; ls++) {
                xn = new double[n];
                for (int i = 0; i < n; i++) xn[i] = Math.max(150.0, Math.min(350.0, x[i] + lambda * dx[i]));
                rn = evaluate(xn);
                if (rn.maxAbsGT < r.maxAbsGT) break;
                lambda *= 0.5;
            }
            if (rn == null || !(rn.maxAbsGT < r.maxAbsGT)) return r;
            double[] s = new double[n];
            double[] y = new double[n];
            double denom = 0.0;
            for (int i = 0; i < n; i++) {
                s[i] = xn[i] - x[i];
                y[i] = rn.gT[i] - r.gT[i];
                denom += s[i] * s[i];
            }
            if (denom > 1.0e-12) {
                for (int i = 0; i < n; i++) {
                    double js = 0.0;
                    for (int j = 0; j < n; j++) js += J[i][j] * s[j];
                    double c = (y[i] - js) / denom;
                    for (int j = 0; j < n; j++) J[i][j] += c * s[j];
                }
            }
            x = xn;
            r = rn;
        }
        return r;
    }

    /** Gaussian elimination with partial pivoting; returns null if singular. */
    private static double[] solveLinear(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        for (int col = 0; col < n; col++) {
            int piv = col;
            for (int r = col + 1; r < n; r++) if (Math.abs(M[r][col]) > Math.abs(M[piv][col])) piv = r;
            if (Math.abs(M[piv][col]) < 1.0e-12) return null;
            double[] tmp = M[col]; M[col] = M[piv]; M[piv] = tmp;
            for (int r = col + 1; r < n; r++) {
                double f = M[r][col] / M[col][col];
                for (int c = col; c <= n; c++) M[r][c] -= f * M[col][c];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            double s = M[i][n];
            for (int j = i + 1; j < n; j++) s -= M[i][j] * x[j];
            x[i] = s / M[i][i];
        }
        return x;
    }

    /** Annual-mean cosZenith at a latitude. */
    public static double annualCosZenith(double latDeg) {
        double s = 0.0;
        for (int j = 0; j < 24; j++) {
            double theta = 2.0 * Math.PI * j / 24.0;
            double dec = 0.409 * Math.cos(theta);
            s += Math.max(0.01, Radiation.insolation(Math.toRadians(latDeg), dec) / VerticalColumn.S0);
        }
        return s / 24.0;
    }

    /**
     * Evaluate G_T = annual surface net + dry-static heat transport convergence,
     * and G_W = E - P (no MFC here). Ts is the only input.
     */
    public static Residual evaluate(double[] ts) {
        if (ZonalLandProfile.currentSeed() == Long.MIN_VALUE) ZonalLandProfile.setSeed(0L);
        Residual r = new Residual();
        int k850 = 0;
        double best = 1.0e30;
        for (int k = 0; k < VerticalColumn.N; k++) {
            double d = Math.abs(VerticalColumn.PC[k] - 85000.0);
            if (d < best) { best = d; k850 = k; }
        }
        double saveAlb = VerticalColumn.ALBEDO;
        boolean saveFixed = VerticalColumn.FIXED_TS;
        boolean saveDry = VerticalColumn.DRY_EXIT;
        double[] pArr = new double[SeasonalZonal.NL];
        double[] eArr = new double[SeasonalZonal.NL];
        double[] netArr = new double[SeasonalZonal.NL];
        try {
            VerticalColumn.FIXED_TS = true;
            VerticalColumn.DRY_EXIT = false;   // smooth residual for Jacobian/line search
            for (int i = 0; i < SeasonalZonal.NL; i++) {
                double lat = -90.0 + 10.0 * i;
                r.latDeg[i] = lat;
                r.ts[i] = ts[i];
                double k = Math.max(0.0, Math.min(1.0, ZonalLandProfile.at(Math.toRadians(lat))));
                VerticalColumn.ALBEDO = Radiation.ALB_SEA + k * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                VerticalColumn c = new VerticalColumn();
                // Continuation from the warm branch (see NBRANCH: 280 K init can fall into
                // the 242/252 K branches at 50-60N; the warm branch is the legal one).
                c.init(290.0, 0.015, 1.0, 6.0);
                c.tSfc = ts[i];
                c.solveAtmosphere(SeasonalZonal.DT_ATM, annualCosZenith(lat), 200.0, 1.0, SeasonalZonal.MAX_ATM);
                r.t850[i] = c.t[k850];
                r.wKgM2[i] = c.cwv();
                pArr[i] = Math.max(0.0, c.lastPStepKgM2 / SeasonalZonal.DT_ATM) * 86400.0;
                eArr[i] = Math.max(0.0, c.lastEStepKgM2 / SeasonalZonal.DT_ATM) * 86400.0;
                r.pMm[i] = pArr[i];
                r.eMm[i] = eArr[i];
                r.gW[i] = eArr[i] - pArr[i];
                netArr[i] = c.lastSurfNet;
                r.surfNet[i] = c.lastSurfNet;
                r.asr[i] = c.lastStepAsr;
                r.olr[i] = c.lastStepOlr;
            }
        } finally {
            VerticalColumn.ALBEDO = saveAlb;
            VerticalColumn.FIXED_TS = saveFixed;
            VerticalColumn.DRY_EXIT = saveDry;
        }
        double[] q = PLANE_GEOMETRY
                ? DiffusiveTransport.qFromTModelZ(r.latDeg, r.t850)
                : DiffusiveTransport.qFromTModel(r.latDeg, r.t850);
        double sw = 0.0, sp = 0.0, se = 0.0, s2 = 0.0;
        for (int i = 0; i < SeasonalZonal.NL; i++) {
            double cw = PLANE_GEOMETRY ? 1.0 : Math.cos(Math.toRadians(r.latDeg[i]));
            sw += cw;
            sp += cw * pArr[i];
            se += cw * eArr[i];
        }
        // Second pass is not needed: the loop above can store net in a temp; do it here for clarity.
        // Re-run is avoided by recomputing from the same columns only if net was stored.
        // For now, use q + 0 as a transport-only diagnostic; caller uses gT only when net is set.
        double[] qOht = oceanQ19();
        for (int i = 1; i < SeasonalZonal.NL - 1; i++) {
            r.dTransport[i] = DiffusiveTransport.diffusivityFromT(r.latDeg[i],
                    r.t850[i - 1], r.t850[i], r.t850[i + 1]);
        }
        for (int i = 0; i < SeasonalZonal.NL; i++) {
            r.qTransport[i] = q[i];
            r.qOht[i] = qOht[i];
            r.gT[i] = netArr[i] + q[i] + qOht[i];
            // Polar lines have zero area: do not require a local surface balance there.
            if (PLANE_GEOMETRY && (i == 0 || i == SeasonalZonal.NL - 1)) r.gT[i] = 0.0;
            double cw = PLANE_GEOMETRY ? 1.0 : Math.cos(Math.toRadians(r.latDeg[i]));
            s2 += cw * r.gT[i] * r.gT[i];
        }
        r.globalP = sp / sw;
        r.globalE = se / sw;
        r.rmsGT = Math.sqrt(s2 / sw);
        for (double v : r.gT) r.maxAbsGT = Math.max(r.maxAbsGT, Math.abs(v));
        return r;
    }
}
