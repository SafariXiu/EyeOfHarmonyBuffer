package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import java.util.Arrays;

/**
 * Matrix-free Newton-Krylov skeleton for an annual map F(x)=M(x)-x.
 *
 * <p>This class is deliberately not wired into the production solver. It solves a
 * nonlinear algebraic system using Newton outer iterations and a matrix-free
 * BiCGSTAB Krylov inner solve (Jv by finite differences). It introduces no physical
 * constants: all tolerances are numerical solver controls.
 */
public final class SteadyNewtonKrylov {
    private SteadyNewtonKrylov() {}

    /** Annual map: returns M(x). It must not mutate x. */
    public interface AnnualMap {
        double[] apply(double[] x);
    }

    public static final class Result {
        public final double[] x;
        public final double residual;
        public final int newtonIterations;
        public final int krylovIterations;
        public final boolean converged;
        Result(double[] x, double residual, int newtonIterations, int krylovIterations, boolean converged) {
            this.x = x;
            this.residual = residual;
            this.newtonIterations = newtonIterations;
            this.krylovIterations = krylovIterations;
            this.converged = converged;
        }
    }

    public static Result solve(AnnualMap map, double[] x0, int maxNewton, int maxKrylov, double tol) {
        double[] x = x0.clone();
        double[] f = residual(map, x);
        double fn = norm2(f);
        int newton = 0;
        int krylovTotal = 0;
        for (newton = 0; newton < maxNewton; newton++) {
            if (!Double.isFinite(fn) || fn <= tol) break;
            double[] dx = new double[x.length];
            int used = bicgstab(map, x, f, dx, maxKrylov, 1.0e-3 * fn);
            krylovTotal += used;
            if (!finite(dx)) break;
            double step = 1.0;
            double[] candidate = null;
            double[] fc = null;
            double fcn = Double.POSITIVE_INFINITY;
            for (int ls = 0; ls < 20; ls++) {
                candidate = axpy(x, step, dx);
                fc = residual(map, candidate);
                fcn = norm2(fc);
                if (Double.isFinite(fcn) && fcn < fn) break;
                step *= 0.5;
            }
            if (candidate == null || !Double.isFinite(fcn) || fcn >= fn) break;
            x = candidate;
            f = fc;
            fn = fcn;
        }
        return new Result(x, fn, newton, krylovTotal, fn <= tol);
    }

    /** Solve J dx = -f by matrix-free BiCGSTAB. */
    private static int bicgstab(AnnualMap map, double[] x, double[] f, double[] dx, int maxIter, double tol) {
        int n = x.length;
        double[] b = new double[n];
        for (int i = 0; i < n; i++) b[i] = -f[i];
        double[] r = b.clone();
        double[] rhat = b.clone();
        double[] p = new double[n];
        double[] v = new double[n];
        double[] s = new double[n];
        double[] t = new double[n];
        double[] jv = new double[n];
        double rho = 1.0, alpha = 1.0, omega = 1.0;
        double[] pv = new double[n];
        double[] sv = new double[n];
        Arrays.fill(p, 0.0);
        Arrays.fill(v, 0.0);
        Arrays.fill(dx, 0.0);
        double bn = norm2(b);
        double target = tol * Math.max(1.0, bn);
        int used = 0;
        for (int k = 0; k < maxIter; k++) {
            used = k + 1;
            double rhoNew = dot(rhat, r);
            if (Math.abs(rhoNew) < 1e-300) break;
            double beta = (rhoNew / rho) * (alpha / omega);
            for (int i = 0; i < n; i++) p[i] = r[i] + beta * (p[i] - omega * v[i]);
            jvp(map, x, p, f, jv);
            double denom = dot(rhat, jv);
            if (Math.abs(denom) < 1e-300) break;
            alpha = rhoNew / denom;
            for (int i = 0; i < n; i++) s[i] = r[i] - alpha * jv[i];
            if (norm2(s) <= target) {
                for (int i = 0; i < n; i++) dx[i] += alpha * p[i];
                break;
            }
            jvp(map, x, s, f, jv);
            double tt = dot(jv, jv);
            if (tt < 1e-300) break;
            omega = dot(jv, s) / tt;
            for (int i = 0; i < n; i++) dx[i] += alpha * p[i] + omega * s[i];
            for (int i = 0; i < n; i++) r[i] = s[i] - omega * jv[i];
            if (norm2(r) <= target) break;
            rho = rhoNew;
        }
        return used;
    }

    private static void jvp(AnnualMap map, double[] x, double[] v, double[] f0, double[] out) {
        double eps = 1e-6 * Math.max(1.0, norm2(x));
        double[] xp = new double[x.length];
        for (int i = 0; i < x.length; i++) xp[i] = x[i] + eps * v[i];
        double[] fp = map.apply(xp);
        for (int i = 0; i < x.length; i++) out[i] = (fp[i] - f0[i]) / eps;
    }

    private static double[] residual(AnnualMap map, double[] x) {
        double[] m = map.apply(x);
        double[] f = new double[x.length];
        for (int i = 0; i < x.length; i++) f[i] = m[i] - x[i];
        return f;
    }

    private static double[] axpy(double[] x, double a, double[] y) {
        double[] z = new double[x.length];
        for (int i = 0; i < x.length; i++) z[i] = x[i] + a * y[i];
        return z;
    }

    private static double dot(double[] a, double[] b) {
        double s = 0.0;
        for (int i = 0; i < a.length; i++) s += a[i] * b[i];
        return s;
    }

    private static double norm2(double[] a) {
        double s = 0.0;
        for (double v : a) s += v * v;
        return Math.sqrt(s);
    }

    private static boolean finite(double[] a) {
        for (double v : a) if (!Double.isFinite(v)) return false;
        return true;
    }
}
