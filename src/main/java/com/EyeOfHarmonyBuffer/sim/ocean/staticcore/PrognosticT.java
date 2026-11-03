package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;

/**
 * ★ 2026-11-03（stage 4）：T 的季节内 prognostic flux-form 输运。
 * <pre>
 *   dT/dt = -div(uT) + KH ∇²_h T + KV ∂²T/∂z² + Q_sw
 * </pre>
 * 表面 k=0 作为 Dirichlet 边界（surfaceT，由 ML/表面通量步给出）。
 * 面通量用已验证的 divFlux（Σ=0）；30×1 天显式步。
 */
public final class PrognosticT {
    public static double LAST_MAX_DT = 0.0;
    /** 累计的冰点钳制温差（诊断，K·格点）。 */
    public static double LAST_DEFICIT = 0.0;

    private PrognosticT() {}

    public static double run(OceanGrid g, OceanState st, double[] surfaceT, double[] src,
                             double seasonSeconds, int substeps) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n3 = nx * ny * nz;
        int steps = Math.max(1, substeps);
        double dt = seasonSeconds / steps;
        double[] adv = new double[n3];
        double[] tn = new double[n3];
        double maxd = 0.0;
        for (int step = 0; step < steps; step++) {
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++)
                        adv[st.idx(i, j, k)] = AdvectionDiffusionSolver.divFlux(g, st, st.t, i, j, k);
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int js = j * nx + i;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        if (k == 0) { tn[q] = surfaceT[js]; continue; }
                        double t = st.t[q], dz = g.dz[k];
                        double tw = g.isWet(i - 1, j) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tnn = g.isWet(i, j + 1) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        double tdn = (k + 1 < nz) ? st.t[st.idx(i, j, k + 1)] : t;
                        double lapH = AdvectionDiffusionSolver.khAt(g, k)
                                * ((tw + te - 2.0 * t) / (g.dx * g.dx)
                                 + (ts + tnn - 2.0 * t) / (g.dy * g.dy));
                        double cv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                        double lapZ = cv * (tup + tdn - 2.0 * t);
                        double v = t + dt * (-adv[q] + lapH + lapZ + (src == null ? 0.0 : src[q]));
                        // ★ 2026-11-03（stage 3 接入 progn T）：T≥Tf(S)，子步内钳制。
                        // 先只做约束（潜热/盐析记账下一步并入），保证不出现 165 K 液态水。
                        double tf = FreezingPoint.kelvin(st.s[q]);
                        if (v < tf) { LAST_DEFICIT += (tf - v); v = tf; }
                        tn[q] = v;
                        maxd = Math.max(maxd, Math.abs(v - t));
                    }
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        st.t[q] = tn[q];
                    }
                }
        }
        LAST_MAX_DT = maxd;
        return maxd;
    }
}
