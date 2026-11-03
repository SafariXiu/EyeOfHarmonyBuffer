package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * F5 最小 3D 稳态扩散 solver（J1 的 T/S 组件）。
 *
 * <p>只解稳态扩散：
 *   KH*(T_w+T_e-2T)/dx^2 + KH*(T_s+T_n-2T)/dy^2
 * + KV*(T_up+T_dn-2T)/dz^2 = 0
 * 表面 Dirichlet，底部零通量，陆地零通量，x 周期。
 *
 * <p>KH=1000 m2/s, KV=1e-5 m2/s（MITgcm tutorial_baroclinic_gyre 的
 * diffKhT/diffKrT；只作为数值参考，不是本世界拟合）。
 */
public final class DiffusionSolver {

    public static final double KH = 1000.0;
    public static final double KV = 1.0e-5;
    public static final double SOR = 1.4;

    private DiffusionSolver() {}

    public static final class Result {
        public final int iterations;
        public final double residual;
        Result(int iterations, double residual) {
            this.iterations = iterations; this.residual = residual;
        }
    }

    public static Result solve(OceanGrid g, OceanState st, double[] surfaceT) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                double ts = surfaceT[j * nx + i];
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    st.t[q] = k == 0 ? ts : 2.0;
                    st.s[q] = 35.0;
                }
            }
        }
        int iter = 0, maxIter = 3000;
        double res = 0.0;
        for (; iter < maxIter; iter++) {
            res = 0.0;
            for (int k = 1; k < nz; k++) {
                double dz = g.dz[k];
                double cv = KV / (dz * dz);
                for (int j = 1; j < ny - 1; j++) {
                    for (int i = 0; i < nx; i++) {
                        if (!g.isWet(i, j)) continue;
                        int q = st.idx(i, j, k);
                        double t = st.t[q];
                        double tw = g.isWet(i - 1, j) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tn = g.isWet(i, j + 1) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        double tdn = (k + 1 < nz) ? st.t[st.idx(i, j, k + 1)] : t;
                        double num = KH * (tw + te - 2.0 * t) / (dx * dx)
                                   + KH * (ts + tn - 2.0 * t) / (dy * dy)
                                   + cv * (tup + tdn - 2.0 * t);
                        double target = t + num / (4.0 * KH / (dx * dx) + 4.0 * KH / (dy * dy) + 2.0 * cv);
                        double nv = t + SOR * (target - t);
                        st.t[q] = nv;
                        double d = Math.abs(nv - t);
                        if (d > res) res = d;
                    }
                }
            }
            if (res < 1.0e-8) break;
        }
        return new Result(iter, res);
    }
}
