package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * Standard 5-category ice thickness distribution (ITD) skeleton.
 *
 * <p>This is the Phase 2c structure: area/volume/snow per thickness category, instead
 * of forcing frazil into one bulk ice state. It is deliberately not wired into the
 * production path yet. The category bounds are a numerical discretization (CICE-like
 * 0.1/0.3/0.7/1.5 m); they are not physical constants and can be revisited.
 *
 * <p>Units: area is grid-cell fraction; volume is grid-cell mean ice volume (m);
 * snow is grid-cell mean snow volume (m snow). Ice thickness in a category is
 * h = volume / max(area, eps).
 */
public final class IceThicknessDistribution {

    public static final int NCAT = 5;
    public static final double[] BOUNDS = {0.0, 0.1, 0.3, 0.7, 1.5, Double.POSITIVE_INFINITY};
    public static final double EPS = 1.0e-12;

    public final double[][] area;    // [cat][js]
    public final double[][] volume;  // [cat][js]
    public final double[][] snow;    // [cat][js]
    private final int n2;
    /** True once categories have been populated from the bulk state. */
    public boolean initialized = false;

    public IceThicknessDistribution(OceanGrid g) {
        n2 = g.nx * g.ny;
        area = new double[NCAT][n2];
        volume = new double[NCAT][n2];
        snow = new double[NCAT][n2];
    }

    /** Rebuild categories from the existing bulk state. */
    public void initFromState(OceanState st) {
        clear();
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = Math.max(0.0, Math.min(1.0, st.iceFraction[js]));
                double v = Math.max(0.0, st.iceThickness[js]);
                if (a <= EPS || v <= EPS) continue;
                double h = v / a;
                int c = category(h);
                area[c][js] += a;
                volume[c][js] += v;
                snow[c][js] += Math.max(0.0, st.snowThickness[js]);
            }
        }
        initialized = true;
    }

    public void clear() {
        initialized = false;
        for (int c = 0; c < NCAT; c++) {
            java.util.Arrays.fill(area[c], 0.0);
            java.util.Arrays.fill(volume[c], 0.0);
            java.util.Arrays.fill(snow[c], 0.0);
        }
    }

    public int category(double h) {
        if (!(h > 0.0)) return 0;
        for (int c = 0; c < NCAT; c++) {
            if (h < BOUNDS[c + 1]) return c;
        }
        return NCAT - 1;
    }

    public double totalArea(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += area[c][js];
        return s;
    }

    public double totalVolume(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += volume[c][js];
        return s;
    }

    public double totalSnow(int js) {
        double s = 0.0;
        for (int c = 0; c < NCAT; c++) s += snow[c][js];
        return s;
    }

    /** Write totals back to the bulk state (single-category coupling). */
    public void syncToState(OceanState st) {
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = totalArea(js);
                if (a > 1.0) {
                    double inv = 1.0 / a;
                    for (int c = 0; c < NCAT; c++) {
                        area[c][js] *= inv;
                        volume[c][js] *= inv;
                        snow[c][js] *= inv;
                    }
                    a = 1.0;
                }
                st.iceFraction[js] = Math.max(0.0, Math.min(1.0, a));
                st.iceThickness[js] = Math.max(0.0, totalVolume(js));
                st.snowThickness[js] = Math.max(0.0, totalSnow(js));
            }
        }
    }

    /**
     * Add frazil volume to the thinnest category. The new area is limited by the
     * remaining open-water fraction and the reference thickness. Returns the area
     * actually created.
     */
    public double addFrazil(int js, double dm, double hRef) {
        if (!(dm > 0.0)) return 0.0;
        double open = Math.max(0.0, 1.0 - totalArea(js));
        double da = Math.min(dm / Math.max(EPS, hRef), open);
        area[0][js] += da;
        volume[0][js] += dm;
        return da;
    }

    /** Largest absolute difference between category totals and the bulk state. */
    public double conservationError(OceanState st) {
        double err = 0.0;
        OceanGrid g = st.grid;
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                err = Math.max(err, Math.abs(totalArea(js) - st.iceFraction[js]));
                err = Math.max(err, Math.abs(totalVolume(js) - st.iceThickness[js]));
                err = Math.max(err, Math.abs(totalSnow(js) - st.snowThickness[js]));
            }
        }
        return err;
    }

    /**
     * Standard per-category zero-layer thermodynamics for one grid cell.
     * The caller owns the open-water/ML part and the salt/freshwater totals.
     */
    public void thermoStep(OceanState st, SeaSurfaceForcing f, double theta,
                           double dt, double tf, int i, int j, int js) {
        OceanGrid g = st.grid;
        int x = (int) Math.round(g.xOf(i));
        int z = (int) Math.round(g.zOf(j));
        for (int c = 0; c < NCAT; c++) {
            double a = area[c][js];
            if (a <= EPS) continue;
            double v = volume[c][js];
            double h = v / a;
            double hs = snow[c][js] / a;
            double qAtTf = f.netHeatFluxWm2(x, z, theta, tf, 1.0, h);
            double dV;
            if (qAtTf > 0.0) {
                dV = -qAtTf * dt
                        / (OceanConstants.RHO_ICE * OceanConstants.L_FUSION) * a;
            } else {
                double qs = AdvectionDiffusionSolver.iceSurfaceFlux(f, theta, x, z,
                        tf, h, 1.0, hs);
                dV = qs * dt
                        / (OceanConstants.RHO_ICE * OceanConstants.L_FUSION) * a;
            }
            v += dV;
            if (v <= 0.0 || a <= EPS) {
                area[c][js] = 0.0;
                volume[c][js] = 0.0;
                snow[c][js] = 0.0;
            } else {
                volume[c][js] = v;
            }
        }
    }

    /**
     * Transport each category with the existing single-category upwind transport.
     * This is a transition implementation: it reuses transportIce per category and
     * then syncs totals; it is not a full ridging/remapping scheme.
     */
    public void transport(OceanState st, SeaSurfaceForcing f, double theta, double dt) {
        int n = st.iceFraction.length;
        for (int c = 0; c < NCAT; c++) {
            for (int js = 0; js < n; js++) {
                st.iceFraction[js] = area[c][js];
                st.iceThickness[js] = volume[c][js];
                st.snowThickness[js] = snow[c][js];
            }
            AdvectionDiffusionSolver.transportIce(st.grid, st, f, theta, dt);
            for (int js = 0; js < n; js++) {
                area[c][js] = st.iceFraction[js];
                volume[c][js] = st.iceThickness[js];
                snow[c][js] = st.snowThickness[js];
            }
        }
        syncToState(st);
    }

    /** Remove up to dm of ice volume, starting at the thinnest category. */
    public double meltVolume(int js, double dm) {
        double left = Math.max(0.0, dm);
        double removed = 0.0;
        for (int c = 0; c < NCAT && left > 0.0; c++) {
            double v = volume[c][js];
            if (v <= 0.0) continue;
            double take = Math.min(v, left);
            volume[c][js] = v - take;
            left -= take;
            removed += take;
            if (volume[c][js] <= EPS) {
                area[c][js] = 0.0;
                volume[c][js] = 0.0;
                snow[c][js] = 0.0;
            }
        }
        return removed;
    }
}
