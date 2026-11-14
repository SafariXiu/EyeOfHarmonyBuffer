package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.ClimberOcean;
import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * 由 bulk 净热通量=0 求每个水柱的海表温度。
 * netHeatFluxWm2 正 = 进入海洋；根用二分。
 */
public final class SurfaceTemperatureSolver {

    /** 参考盐度下的海水冰点；由 FreezingPoint 计算，不是硬编码。 */
    public static final double T_FREEZE = FreezingPoint.referenceKelvin();

    private SurfaceTemperatureSolver() {}

    public static double[] solve(OceanGrid g, SeaSurfaceForcing f, double theta,
                                 double[] fallback) {
        return solve(g, f, theta, fallback, null);
    }

    public static double[] solve(OceanGrid g, SeaSurfaceForcing f, double theta,
                                 double[] fallback, OceanState st) {
        double[] out = new double[g.nx * g.ny];
        for (int j = 0; j < g.ny; j++) {
            for (int i = 0; i < g.nx; i++) {
                int q = j * g.nx + i;
                if (!g.isWet(i, j)) { out[q] = fallback[q]; continue; }
                int x = (int) Math.round(g.xOf(i));
                int z = (int) Math.round(g.zOf(j));
                // 物理根选择：若在冰点海洋仍在失热，则结冰，SST 钉在冰点；
                // 否则在 [Tf, 400] 找无冰根。
                double qf = f.netHeatFluxWm2(x, z, theta, T_FREEZE);
                if (Double.isNaN(qf)) { out[q] = fallback[q]; continue; }
                if (qf < 0.0) {
                    out[q] = T_FREEZE;
                    if (st != null) {
                        st.iceFraction[q] = 1.0;
                        updateIceAndBrine(g, st, f, i, j, q, x, z, theta, qf);
                    }
                    continue;
                }
                double qIce = f.netHeatFluxWm2(x, z, theta, T_FREEZE, 1.0);
                if (Double.isFinite(qIce) && qIce < 0.0) {
                    double lo = 0.0, hi = 1.0;
                    for (int it = 0; it < 40; it++) {
                        double mid = 0.5 * (lo + hi);
                        double qm = f.netHeatFluxWm2(x, z, theta, T_FREEZE, mid);
                        if (qm > 0.0) lo = mid; else hi = mid;
                    }
                    double frac = 0.5 * (lo + hi);
                    double tOpen = rootNoIce(f, x, z, theta, fallback[q]);
                    out[q] = frac * T_FREEZE + (1.0 - frac) * tOpen;
                    if (st != null) {
                        st.iceFraction[q] = frac;
                        updateIceAndBrine(g, st, f, i, j, q, x, z, theta, qIce);
                    }
                    continue;
                }
                if (st != null) { st.iceFraction[q] = 0.0; st.iceThickness[q] = 0.0; }
                out[q] = rootNoIce(f, x, z, theta, fallback[q]);
            }
        }
        if (st != null) {
            smoothIceEdge(g, out, st);
            // ★ 2026-11-04：平滑是数值手段，不能把 T 推到局地冰点以下。
            //   实测：冰缘平滑让 T 比 Tf(S) 低 0.051 K（157 格，恰好在 0.05 容差上）。
            for (int j = 1; j < g.ny - 1; j++)
                for (int i = 0; i < g.nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int q = j * g.nx + i;
                    double tf = FreezingPoint.kelvin(st.s[st.idx(i, j, 0)]);
                    if (out[q] < tf) out[q] = tf;
                }
        }
        return out;
    }

    private static void smoothIceEdge(OceanGrid g, double[] out, OceanState st) {
        double[] t2 = out.clone();
        double[] f2 = st.iceFraction.clone();
        for (int j = 1; j < g.ny - 1; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * g.nx + i;
                boolean edge = st.iceFraction[q] > 0.0;
                for (int dj = -1; dj <= 1 && !edge; dj++)
                    for (int di = -1; di <= 1 && !edge; di++)
                        if (g.isWet(i + di, j + dj) && st.iceFraction[(j + dj) * g.nx + ((i + di + g.nx) % g.nx)] > 0.0)
                            edge = true;
                if (!edge) continue;
                double sumT = 0.0, sumF = 0.0; int n = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++) {
                        if (!g.isWet(i + di, j + dj)) continue;
                        int qq = (j + dj) * g.nx + ((i + di + g.nx) % g.nx);
                        sumT += out[qq]; sumF += st.iceFraction[qq]; n++;
                    }
                if (n > 0) { t2[q] = sumT / n; f2[q] = sumF / n; }
            }
        }
        System.arraycopy(t2, 0, out, 0, out.length);
        System.arraycopy(f2, 0, st.iceFraction, 0, f2.length);
    }

    private static void updateIceAndBrine(OceanGrid g, OceanState st, SeaSurfaceForcing f,
                                          int i, int j, int q, int x, int z, double theta, double qIce) {
        double h = Math.max(0.05, st.iceThickness[q]);
        double ts = iceSurfaceT(f, x, z, theta, h);
        double qh = f.netHeatFluxWm2(x, z, theta, ts, 1.0);
        double dh = -qh * 86400.0 / (ClimberOcean.RHO_ICE * ClimberOcean.L_FUSION);
        if (dh > 0.0) {
            int qs = st.idx(i, j, 0);
            double s0 = st.s[qs];
            double dS = s0 * (ClimberOcean.RHO_ICE / ClimberOcean.RHO_REF) * dh / Math.max(1.0, h);
            st.s[qs] = Math.max(0.0, Math.min(50.0, s0 + dS));
        }
        st.iceThickness[q] = Math.max(0.0, Math.min(ClimberOcean.ICE_MAX, h + dh));
    }

    private static double iceSurfaceT(SeaSurfaceForcing f, int x, int z, double theta, double h) {
        double lo = T_FREEZE - 40.0, hi = T_FREEZE;
        for (int it = 0; it < 30; it++) {
            double mid = 0.5 * (lo + hi);
            double q = f.netHeatFluxWm2(x, z, theta, mid, 1.0);
            double flux = q + ClimberOcean.K_ICE * (T_FREEZE - mid) / Math.max(0.05, h);
            if (flux > 0.0) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    private static double rootNoIce(SeaSurfaceForcing f, int x, int z, double theta, double fallback) {
        double qf = f.netHeatFluxWm2(x, z, theta, T_FREEZE);
        if (Double.isNaN(qf)) return fallback;
        double[] gridT = {T_FREEZE, 275, 280, 285, 290, 295, 300, 305, 310, 320, 330, 340, 360, 400};
        double best = fallback, bestDist = Double.MAX_VALUE;
        double tPrev = gridT[0], fPrev = qf;
        for (int gi = 1; gi < gridT.length; gi++) {
            double tCur = gridT[gi];
            double fCur = f.netHeatFluxWm2(x, z, theta, tCur);
            if (Double.isFinite(fCur) && fPrev * fCur <= 0.0) {
                double lo = tPrev, hi = tCur, flo = fPrev;
                for (int it = 0; it < 60; it++) {
                    double mid = 0.5 * (lo + hi);
                    double fm = f.netHeatFluxWm2(x, z, theta, mid);
                    if (flo * fm <= 0.0) hi = mid; else { lo = mid; flo = fm; }
                }
                double root = 0.5 * (lo + hi);
                double d = Math.abs(root - fallback);
                if (d < bestDist) { bestDist = d; best = root; }
            }
            tPrev = tCur; fPrev = fCur;
        }
        return best;
    }
}
