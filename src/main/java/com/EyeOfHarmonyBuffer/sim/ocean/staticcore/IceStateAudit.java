package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** P0: centralized ice-state invariants and budgets (audit only). */
public final class IceStateAudit {
    private IceStateAudit() {}

    public static double areaTotal(OceanState st, OceanGrid g) {
        double sum = 0.0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j)) sum += st.iceFraction[j * g.nx + i];
        return sum;
    }

    public static double volumeTotal(OceanState st, OceanGrid g) {
        double sum = 0.0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j)) sum += st.iceThickness[j * g.nx + i];
        return sum;
    }

    public static double snowTotal(OceanState st, OceanGrid g) {
        double sum = 0.0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++)
                if (g.isWet(i, j)) sum += st.snowThickness[j * g.nx + i];
        return sum;
    }

    public static int violations(OceanState st, OceanGrid g) {
        int n = 0;
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = st.iceFraction[js], v = st.iceThickness[js], s = st.snowThickness[js];
                if (!Double.isFinite(a) || a < 0.0 || a > 1.0) n++;
                else if (!Double.isFinite(v) || v < 0.0) n++;
                else if (!Double.isFinite(s) || s < 0.0) n++;
                else if (v > 1.0e-12 && a <= 1.0e-12) n++;
            }
        return n;
    }

    /** Enforce local bounds without inventing area; not called on the default path yet. */
    public static void enforceLocal(OceanState st, OceanGrid g) {
        for (int j = 0; j < g.ny; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * g.nx + i;
                double a = Math.max(0.0, Math.min(1.0, st.iceFraction[js]));
                double v = Math.max(0.0, st.iceThickness[js]);
                double s = Math.max(0.0, st.snowThickness[js]);
                if (a <= 1.0e-12) { a = 0.0; v = 0.0; s = 0.0; }
                if (v <= 1.0e-12) { v = 0.0; a = 0.0; s = 0.0; }
                st.iceFraction[js] = a;
                st.iceThickness[js] = v;
                st.snowThickness[js] = s;
            }
    }

    public static void printBudget(OceanState st, OceanGrid g, String tag) {
        System.out.println(String.format(java.util.Locale.ROOT,
                "ICEAUDIT %s area=%.6f vol=%.6f snow=%.6f violations=%d",
                tag, areaTotal(st, g), volumeTotal(st, g), snowTotal(st, g), violations(st, g)));
    }
}