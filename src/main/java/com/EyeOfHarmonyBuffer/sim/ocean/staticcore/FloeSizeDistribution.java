package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** Minimal numerical floe-size distribution: area weights only, no Earth floe diameter. */
public final class FloeSizeDistribution {
    public static final int N = 8;
    public final double[] diameter = new double[N];
    public final double[][] area;
    public boolean initialized = false;

    public FloeSizeDistribution(OceanGrid g) {
        double cell = Math.sqrt(Math.max(1.0, g.dx * g.dy));
        for (int k = 0; k < N; k++) diameter[k] = cell * Math.pow(0.5, k);
        area = new double[N][g.nx * g.ny];
    }

    public void initFromState(OceanState st) {
        clear();
        // Maximum-entropy numerical initial condition: equal area across size bins.
        for (int js = 0; js < area[0].length; js++) {
            double a = Math.max(0.0, st.iceFraction[js]);
            for (int k = 0; k < N; k++) area[k][js] = a / N;
        }
        initialized = true;
    }

    public void clear() {
        for (int k = 0; k < N; k++) java.util.Arrays.fill(area[k], 0.0);
        initialized = false;
    }

    public double total(int js) {
        double s = 0.0;
        for (int k = 0; k < N; k++) s += area[k][js];
        return s;
    }

    /** Scale the distribution to the bulk area (new ice/area changes). */
    public void reconcile(OceanState st, int js) {
        double a = Math.max(0.0, st.iceFraction[js]);
        double s = total(js);
        if (a <= 1.0e-12 || s <= 1.0e-12) {
            for (int k = 0; k < N; k++) area[k][js] = 0.0;
            if (a > 0.0) area[0][js] = a;
            return;
        }
        double f = a / s;
        for (int k = 0; k < N; k++) area[k][js] *= f;
    }

    /** Breakup: move a fraction of each bin to the next smaller bin; no area change. */
    public void breakup(int js, double lostArea) {
        double a = total(js);
        if (a <= 1.0e-12 || !(lostArea > 0.0)) return;
        double f = Math.min(1.0, lostArea / a);
        for (int k = 0; k < N - 1; k++) {
            double move = area[k][js] * f;
            area[k][js] -= move;
            area[k + 1][js] += move;
        }
    }

    /** Lateral melt: return the bulk area fraction removed; updates the distribution. */
    public double lateralMelt(int js, double qOpen, double dt) {
        if (!(qOpen > 0.0) || !(dt > 0.0)) return 0.0;
        double w = qOpen / (OceanConstants.RHO_ICE * OceanConstants.L_FUSION);
        double loss = 0.0;
        for (int k = 0; k < N; k++) {
            double a = area[k][js];
            if (a <= 1.0e-12) continue;
            double r = Math.min(1.0, (4.0 / diameter[k]) * w * dt);
            double d = a * r;
            area[k][js] -= d;
            loss += d;
        }
        return loss;
    }
}