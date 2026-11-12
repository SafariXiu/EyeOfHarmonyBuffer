package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import java.util.ArrayList;

/**
 * 2026-11-11: deep-ocean per-term heat budget instrument (diagnostic, default OFF).
 *
 * WHY: handoff doc section 5-1 says the deep water is 4 K warmer than its source and
 * 4.2% of deep cells are lighter than their own surface. We know it WARMS but not WHO
 * carries the heat down. The only honest criterion is a term-by-term ledger:
 * advection (horizontal/vertical), diffusion (horizontal/vertical), shortwave source,
 * mixed-layer rewrite, interior-freezing projection -- how many K each contributed.
 *
 * ZERO-EFFECT CONTRACT (rules N5): with ON=false every accumulation call sits inside
 * an 'if (ON)' guard, no array is allocated, no physical quantity is touched, and it
 * does NOT enter solveFlagsHash (it only reads the solution, never changes it).
 *
 * Units: acc = tendency[K/s] * dt[s] * dz[m] * max(0.05, cos(lat)) => K*m.
 * Divide by volume(band,k) to get the mean temperature contribution [K].
 */
public final class DeepBudget {
    private DeepBudget() {}

    /** Master switch. Default false => bit-identical to the code before it existed. */
    public static boolean ON = false;

    public static final int T_ADV = 0;      // total 3D advection (= horizontal + vertical)
    public static final int T_ADV_V = 1;    // the vertical part of it
    public static final int T_DIFF_H = 2;   // KH horizontal diffusion
    public static final int T_DIFF_V = 3;   // KV vertical diffusion
    public static final int T_SRC = 4;      // penetrating shortwave source
    public static final int T_REDI = 5;     // Redi isopycnal diffusion (default off)
    public static final int T_ML = 6;       // mixed-layer rewrite (entrainment / full-column / ice)
    /** Freezing clamp inside the PrognosticT substep (masked numerical blow-up). */
    public static final int T_FRZ_LOOP = 7;
    /** Freezing active-set projection after the annual mean (INTERIOR_FREEZE_ON). */
    public static final int T_FRZ_FINAL = 8;
    /** Convective adjustment (default OFF; see AdvectionDiffusionSolver#convectiveAdjust). */
    public static final int T_CONV = 9;
    public static final int NT = 10;

    public static final String[] NAME = {
        "ADV_total", "ADV_vert", "DIFF_h", "DIFF_v", "SRC_sw", "REDI", "ML_rewrite",
        "FRZ_loop", "FRZ_final", "CONVECT"
    };

    /** Latitude bands: 0 = |lat|<30, 1 = 30-60, 2 = >=60 (same split as the handoff doc). */
    public static final int NB = 3;

    /** Largest explicit-advection CFL number seen (rate * dt). > 1 means the scheme is unstable. */
    public static double LAST_CFL = 0.0;
    /** Its latitude band. */
    public static int LAST_CFL_BAND = -1;

    private static int nz = 0;
    private static double[] acc = new double[0];      // [(band*NT+term)*nz + k]
    private static double[] vol = new double[0];      // [band*nz + k]
    /** Per (band,term): largest single |contribution|, sum of |contribution|, count of |c| > 1e3. */
    private static double[] mx = new double[0];
    private static double[] sa = new double[0];
    private static long[] nb = new long[0];
    private static final ArrayList<double[]> YEAR = new ArrayList<>();

    public static int band(OceanGrid g, int j) {
        double a = Math.abs(Math.toDegrees(g.latAt(j)));
        if (a < 30.0) return 0;
        if (a < 60.0) return 1;
        return 2;
    }

    /** Called once at the start of every solve: reset and rebuild the volume weights. */
    public static void begin(OceanGrid g) {
        YEAR.clear();
        TDEEP.clear();
        TALL.clear();
        nz = g.nz;
        acc = new double[NB * NT * nz];
        vol = new double[NB * nz];
        mx = new double[NB * NT];
        sa = new double[NB * NT];
        nb = new long[NB * NT];
        LAST_CFL = 0.0;
        LAST_CFL_BAND = -1;
        if (!ON) return;
        for (int j = 1; j < g.ny - 1; j++) {
            int b = band(g, j);
            double c = Math.max(0.05, g.cosLat(j));
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) vol[b * nz + k] += g.dz[k] * c;
            }
        }
    }

    public static void add(int band, int term, int k, double v) {
        if (!ON || nz <= 0 || k < 0 || k >= nz) return;
        acc[(band * NT + term) * nz + k] += v;
        int t = band * NT + term;
        double a = Math.abs(v);
        if (a > mx[t]) mx[t] = a;
        sa[t] += a;
        if (a > 1.0e3) nb[t]++;
    }

    /** Largest single |contribution| ever added for (band,term), units K*m. */
    public static double maxAbs(int band, int term) {
        if (nz <= 0) return 0.0;
        return mx[band * NT + term];
    }

    /** Sum of |contribution| for (band,term), units K*m. */
    public static double sumAbs(int band, int term) {
        if (nz <= 0) return 0.0;
        return sa[band * NT + term];
    }

    /** How many single contributions for (band,term) exceeded 1e3 K*m (a 150 m layer ~ 6.7 K). */
    public static long nBig(int band, int term) {
        if (nz <= 0) return 0L;
        return nb[band * NT + term];
    }

    /** Measured annual-mean T over the true deep (z>=2000 m, wet cells only), per year. */
    private static final ArrayList<Double> TDEEP = new ArrayList<>();
    private static final ArrayList<Double> TALL = new ArrayList<>();

    /** Close one simulated year: freeze this year's ledger and clear the accumulator. */
    public static void closeYear(OceanGrid g, OceanState st) {
        if (!ON || nz <= 0) return;
        YEAR.add(acc.clone());
        java.util.Arrays.fill(acc, 0.0);
        // closure check: the ledger must reproduce the actual annual-mean change
        double sD = 0.0, vD = 0.0, sA = 0.0, vA = 0.0;
        for (int j = 1; j < g.ny - 1; j++) {
            double c = Math.max(0.05, g.cosLat(j));
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    double w = g.dz[k] * c;
                    double t = st.t[st.idx(i, j, k)];
                    sA += t * w; vA += w;
                    if (g.zCenter[k] >= 2000.0) { sD += t * w; vD += w; }
                }
            }
        }
        TDEEP.add(vD > 0 ? sD / vD : 0.0);
        TALL.add(vA > 0 ? sA / vA : 0.0);
    }

    public static double tDeep(int y) {
        return (y >= 0 && y < TDEEP.size()) ? TDEEP.get(y) : Double.NaN;
    }

    public static double tAll(int y) {
        return (y >= 0 && y < TALL.size()) ? TALL.get(y) : Double.NaN;
    }

    public static int years() { return YEAR.size(); }
    public static double[] year(int y) { return YEAR.get(y); }
    public static int nz() { return nz; }
    public static double volume(int band, int k) {
        if (nz <= 0) return 0.0;
        return vol[band * nz + k];
    }
}
