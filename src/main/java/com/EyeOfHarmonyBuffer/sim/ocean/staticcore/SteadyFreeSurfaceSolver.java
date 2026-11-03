package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;

/**
 * B2-steady 的第一块：由稳态连续性解自由水面 eta。
 *
 * <p>当前 u/v 来自风生 psi，先算其散度 D0；再解势流修正 phi：
 *   ∇²phi = Fw/(rho H) - D0
 * 使总散度满足稳态连续性。然后由 r*u' = -g*grad(eta) 得
 *   eta = -r*phi/g
 * 这是稳态线性自由水面近似，不含时间步/重力波 CFL。
 */
public final class SteadyFreeSurfaceSolver {

    public static final double RHO = 1027.8;
    public static final double G = 9.81;
    public static final double R_FRICTION = 1.0 / (4.0 * 86400.0);
    public static final double ALPHA = 1.7e-4;
    public static final double BETA = 7.6e-4;
    public static final double T_REF = 288.0;
    public static final double S_REF = 35.0;
    public static final double SOR = 0.2;

    public static final class Result {
        public final int iterations;
        public final double residual;
        public final double etaMin, etaMax;
        Result(int it, double res, double mn, double mx) {
            iterations = it; residual = res; etaMin = mn; etaMax = mx;
        }
    }

    private SteadyFreeSurfaceSolver() {}

    public static Result solve(OceanGrid g, OceanState st, SeaSurfaceForcing f, double theta) {
        int nx = g.nx, ny = g.ny;
        double dx = g.dx, dy = g.dy;
        double hTotal = 0.0;
        for (double z : g.dz) hTotal += z;
        double[] src = new double[nx * ny];
        double mean = 0.0;
        int n = 0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int uE = st.idx(i + 1, j, 0), uW = st.idx(i - 1, j, 0);
                int vN = st.idx(i, j + 1, 0), vS = st.idx(i, j - 1, 0);
                double div = (st.u[uE] - st.u[uW]) / (2.0 * dx)
                           + (st.v[vN] - st.v[vS]) / (2.0 * dy);
                double fw = f.freshwaterFluxKgM2S(
                        (int) Math.round(g.xOf(i)), (int) Math.round(g.zOf(j)),
                        theta, st.t[st.idx(i, j, 0)]);
                if (Double.isNaN(fw)) fw = 0.0;
                src[j * nx + i] = fw / (RHO * hTotal) - div;
                mean += src[j * nx + i];
                n++;
            }
        }
        if (n > 0) mean /= n;
        // 周期 x 的泊松可解性：去掉源的平均。
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++)
                if (g.isWet(i, j)) src[j * nx + i] -= mean;

        double[] phi = new double[nx * ny];
        double res = 0.0;
        int it = 0;
        for (; it < 50000; it++) {
            res = 0.0;
            for (int j = 1; j < ny - 1; j++) {
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) { phi[j * nx + i] = 0.0; continue; }
                    double w = phi[j * nx + ((i - 1 + nx) % nx)];
                    double e = phi[j * nx + ((i + 1) % nx)];
                    double s = phi[(j - 1) * nx + i];
                    double nn = phi[(j + 1) * nx + i];
                    double target = 0.25 * (w + e + s + nn - dx * dx * src[j * nx + i]);
                    double old = phi[j * nx + i];
                    double nv = old + SOR * (target - old);
                    phi[j * nx + i] = nv;
                    double d = Math.abs(nv - old);
                    if (d > res) res = d;
                }
            }
            if (res < 1.0e-10) break;
        }
        double etaMin = 1e30, etaMax = -1e30;
        double etaSum = 0.0; int etaN = 0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                double dT = st.t[st.idx(i, j, 0)] - T_REF;
                double dS = st.s[st.idx(i, j, 0)] - S_REF;
                double etaSteric = hTotal * (ALPHA * dT - BETA * dS);
                double eta = -R_FRICTION * phi[q] / G + etaSteric;
                st.eta[q] = eta;
                etaSum += eta; etaN++;
                int uE = st.idx(i + 1, j, 0), uW = st.idx(i - 1, j, 0);
                int vN = st.idx(i, j + 1, 0), vS = st.idx(i, j - 1, 0);
                double du = (phi[g.idx(i + 1, j)] - phi[g.idx(i - 1, j)]) / (2.0 * dx);
                double dv = (phi[g.idx(i, j + 1)] - phi[g.idx(i, j - 1)]) / (2.0 * dy);
                for (int k = 0; k < g.nz; k++) {
                    int qq = st.idx(i, j, k);
                    st.u[qq] += du;
                    st.v[qq] += dv;
                }
            }
        }
        double etaMean = etaN > 0 ? etaSum / etaN : 0.0;
        for (int j = 1; j < ny - 1; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                int q = j * nx + i;
                st.eta[q] -= etaMean;
                etaMin = Math.min(etaMin, st.eta[q]);
                etaMax = Math.max(etaMax, st.eta[q]);
            }
        }
        return new Result(it, res, etaMin, etaMax);
    }
}
