package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * ★ 2026-11-02（方案 B 第一步）：S 的季节内 prognostic flux-form 输运。
 * <p>放弃稳态奇异系统（S=const 零空间 + 全局 corr），改为显式时间步：
 * <pre>
 *   dS/dt = -div(uS) + KH ∇²_h S + KV ∂²S/∂z² + 表面 E−P/冰盐
 * </pre>
 * 面通量用 {@link AdvectionDiffusionSolver#divFlux}（已验证 Σ=0），
 * 表面淡水通量按混合层厚度 h 稀释（不是 dz0）。
 * <p>守恒性：内部面通量严格抵消；总量只随 E−P/冰盐变化（物理上就该变）。
 */
public final class PrognosticSalinity {

    public static double LAST_TOTAL0 = 0.0, LAST_TOTAL1 = 0.0;
    /** 表面淡水只作用在表层 dz0（造强盐度盖）。默认关。 */
    public static boolean THIN_SURF_FW_ON = true;

    private PrognosticSalinity() {}

    public static double run(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta,
                             double seasonSeconds, int substeps) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n3 = nx * ny * nz;
        int steps = Math.max(1, substeps);
        double dt = seasonSeconds / steps;
        double[] fw = new double[nx * ny];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int js = j * nx + i;
                double v = f.freshwaterFluxKgM2S((int) Math.round(g.xOf(i)),
                        (int) Math.round(g.zOf(j)), theta, st.t[st.idx(i, j, 0)]);
                if (Double.isNaN(v)) v = 0.0;
                v += st.iceSaltFlux[js];
                st.iceSaltFlux[js] = 0.0;
                fw[js] = v;
            }
        LAST_TOTAL0 = total(g, st);
        double[] adv = new double[n3];
        double[] ns = new double[n3];
        for (int step = 0; step < steps; step++) {
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++)
                        adv[st.idx(i, j, k)] = AdvectionDiffusionSolver.divFlux(g, st, st.s, i, j, k);
                }
            double maxd = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int js = j * nx + i;
                    double hMl = Math.max(g.dz[0], st.mixedLayerDepth[js]);
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double s = st.s[q], dz = g.dz[k];
                        double sw = g.isWet(i - 1, j) ? st.s[st.idx(i - 1, j, k)] : s;
                        double se = g.isWet(i + 1, j) ? st.s[st.idx(i + 1, j, k)] : s;
                        double ss = g.isWet(i, j - 1) ? st.s[st.idx(i, j - 1, k)] : s;
                        double sn = g.isWet(i, j + 1) ? st.s[st.idx(i, j + 1, k)] : s;
                        double sup = (k > 0) ? st.s[st.idx(i, j, k - 1)] : s;
                        double sdn = (k + 1 < nz) ? st.s[st.idx(i, j, k + 1)] : s;
                        double lapH = AdvectionDiffusionSolver.khAt(g, k)
                                * ((sw + se - 2.0 * s) / (g.dx * g.dx)
                                 + (ss + sn - 2.0 * s) / (g.dy * g.dy));
                        double kv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                        double lapZ = kv * (sup + sdn - 2.0 * s);
                        // ★ 表面 E−P/冰盐按混合层厚度均摊（所有 z<h 的层同源），
                        // 不再只堆在 k=0 —— 后者在 30 天里把表层 S 打到 20/49（实测）。
                        double src = 0.0;
                        if (THIN_SURF_FW_ON) {
                            if (k == 0) src = -s * fw[js] / (AdvectionDiffusionSolver.RHO0 * g.dz[0]);
                        } else if (g.zCenter[k] < hMl) {
                            src = -s * fw[js] / (AdvectionDiffusionSolver.RHO0 * hMl);
                        }
                        double v = s + dt * (-adv[q] + lapH + lapZ + src);
                        if (v < 0.0) v = 0.0;
                        if (v > 50.0) v = 50.0;
                        maxd = Math.max(maxd, Math.abs(v - s));
                        ns[q] = v;
                    }
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        st.s[q] = ns[q];
                    }
                }
            if (!Double.isFinite(maxd)) break;
        }
        LAST_TOTAL1 = total(g, st);
        return LAST_TOTAL1 - LAST_TOTAL0;
    }

    private static double total(OceanGrid g, OceanState st) {
        double sum = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++) sum += st.s[st.idx(i, j, k)] * g.dz[k];
            }
        return sum;
    }
}
