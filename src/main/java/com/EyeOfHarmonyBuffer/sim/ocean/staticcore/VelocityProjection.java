package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * ★ 2026-11-03（C 网格投影）：面速度的离散无散投影。
 * <p>散度/梯度都用【面量】：div =（uF_E−uF_W)/dx +（vF_N−vF_S)/dy +（wF_T−wF_B)/dz；
 * 泊松用紧模板 ∇²φ，修正 uF←uF−∂φ/∂x 等。这样散度算子与梯度算子严格互为伴随，
 * 投影能把面散度压到机器精度（collocated 格心版本做不到——棋盘模式）。
 */
public final class VelocityProjection {
    public static double LAST_DIV_BEFORE = 0.0, LAST_DIV_AFTER = 0.0;
    /** 面速度是否已被投影（divFlux/diagAdv 据此切换分支）。 */
    public static boolean FACES_READY = false;
    public static int LAST_CG_ITER = 0;
    public static double LAST_RES = 0.0;

    private VelocityProjection() {}

    static double faceDiv(OceanGrid g, OceanState st, int i, int j, int k) {
        int q = st.idx(i, j, k);
        double dx = g.dx, dy = g.dy, dz = g.dz[k];
        double uE = st.uF[q], uW = st.uF[st.idx(i - 1, j, k)];
        double vN = st.vF[q], vS = st.vF[st.idx(i, j - 1, k)];
        double wT = (k > 0) ? st.wF[st.idx(i, j, k - 1)] : 0.0;
        double wB = st.wF[q];
        return (uE - uW) / dx + (vN - vS) / dy + (wT - wB) / dz;
    }

    public static double maxDiv(OceanGrid g, OceanState st) {
        double mx = 0.0;
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < g.nz; k++)
                    mx = Math.max(mx, Math.abs(faceDiv(g, st, i, j, k)));
            }
        return mx;
    }

    /** 垂直三对角预条件：M = 垂直部分 −∂²/∂z²（Neumann），逐列 Thomas 求解。 */
    private static void precondition(OceanGrid g, double[] r, double[] z) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double[] aa = new double[nz], bb = new double[nz], cc = new double[nz];
        double[] dd = new double[nz], cp = new double[nz], dp = new double[nz], xx = new double[nz];
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = (k * ny + j) * nx + i;
                    double dz = g.dz[k];
                    double dzu = (k > 0) ? 0.5 * (g.dz[k - 1] + dz) : dz;
                    double dzd = (k + 1 < nz) ? 0.5 * (dz + g.dz[k + 1]) : dz;
                    bb[k] = (1.0 / dzu + 1.0 / dzd) / dz;
                    aa[k] = (k > 0) ? -1.0 / (dzu * dz) : 0.0;
                    cc[k] = (k + 1 < nz) ? -1.0 / (dzd * dz) : 0.0;
                    dd[k] = r[q];
                }
                cp[0] = cc[0] / bb[0];
                dp[0] = dd[0] / bb[0];
                for (int k = 1; k < nz; k++) {
                    double m = bb[k] - aa[k] * cp[k - 1];
                    cp[k] = cc[k] / m;
                    dp[k] = (dd[k] - aa[k] * dp[k - 1]) / m;
                }
                xx[nz - 1] = dp[nz - 1];
                for (int k = nz - 2; k >= 0; k--) xx[k] = dp[k] - cp[k] * xx[k + 1];
                for (int k = 0; k < nz; k++) z[(k * ny + j) * nx + i] = xx[k];
            }
    }

    /** out = −∇²φ（SPD，紧模板；Neumann：干格/边界取自身）。 */
    private static void applyL(OceanGrid g, double[] phi, double[] out) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = (k * ny + j) * nx + i;
                    double c = phi[q];
                    double pe = g.isWet(i + 1, j) ? phi[(k * ny + j) * nx + ((i + 1) % nx)] : c;
                    double pw = g.isWet(i - 1, j) ? phi[(k * ny + j) * nx + ((i - 1 + nx) % nx)] : c;
                    double pn = g.isWet(i, j + 1) ? phi[(k * ny + (j + 1)) * nx + i] : c;
                    double ps = g.isWet(i, j - 1) ? phi[(k * ny + (j - 1)) * nx + i] : c;
                    double pd = (k + 1 < nz) ? phi[((k + 1) * ny + j) * nx + i] : c;
                    double pu = (k > 0) ? phi[((k - 1) * ny + j) * nx + i] : c;
                    double dz = g.dz[k];
                    // ★ 非均匀 dz：面梯度用相邻层平均的 face dz
                    double dzu = (k > 0) ? 0.5 * (g.dz[k - 1] + g.dz[k]) : dz;
                    double dzd = (k + 1 < nz) ? 0.5 * (g.dz[k] + g.dz[k + 1]) : dz;
                    double vert = ((pu - c) / dzu - (c - pd) / dzd) / dz;
                    out[q] = -((pe + pw - 2.0 * c) / (dx * dx)
                             + (pn + ps - 2.0 * c) / (dy * dy)
                             + vert);
                }
            }
    }

    public static double[] solvePoisson(OceanGrid g, OceanState st, double[] div, double mean, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n = nx * ny * nz;
        double[] phi = new double[n], r = new double[n], z = new double[n];
        double[] p = new double[n], ap = new double[n];
        double r0 = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = ((k * ny + j) * nx + i);
                    r[q] = -(div[q] - mean);
                    r0 += r[q] * r[q] * g.dz[k];
                }
            }
        r0 = Math.sqrt(r0);
        precondition(g, r, z);
        double rz = 0.0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = ((k * ny + j) * nx + i);
                    p[q] = z[q];
                    rz += r[q] * z[q] * g.dz[k];
                }
            }
        int it = 0;
        for (; it < maxIter; it++) {
            applyL(g, p, ap);
            double pap = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) pap += p[((k * ny + j) * nx + i)] * ap[((k * ny + j) * nx + i)] * g.dz[k];
                }
            if (Math.abs(pap) < 1e-300) break;
            double alpha = rz / pap;
            double rn = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = ((k * ny + j) * nx + i);
                        phi[q] += alpha * p[q];
                        r[q] -= alpha * ap[q];
                        rn += r[q] * r[q] * g.dz[k];
                    }
                }
            rn = Math.sqrt(rn);
            LAST_RES = rn;
            if (rn < 1e-10 * r0) break;
            precondition(g, r, z);
            double rzNew = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = ((k * ny + j) * nx + i);
                        rzNew += r[q] * z[q] * g.dz[k];
                    }
                }
            double beta = rzNew / rz;
            rz = rzNew;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = ((k * ny + j) * nx + i);
                        p[q] = z[q] + beta * p[q];
                    }
                }
        }
        LAST_CG_ITER = it;
        return phi;
    }

    /** 从格心速度初始化面速度；投影后原地修改 uF/vF/wF。返回投影前 max|div|。 */
    public static double project(OceanGrid g, OceanState st, int maxIter) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n = nx * ny * nz;
        double dx = g.dx, dy = g.dy;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    st.uF[q] = g.isWet(i + 1, j) ? 0.5 * (st.u[q] + st.u[st.idx(i + 1, j, k)]) : 0.0;
                    st.vF[q] = g.isWet(i, j + 1) ? 0.5 * (st.v[q] + st.v[st.idx(i, j + 1, k)]) : 0.0;
                    st.wF[q] = (k + 1 < nz) ? 0.5 * (st.w[q] + st.w[st.idx(i, j, k + 1)]) : 0.0;
                }
            }
        double[] div = new double[n];
        double sum = 0.0, vol = 0.0;
        int cnt = 0;
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    double d = faceDiv(g, st, i, j, k);
                    div[q] = d;
                    sum += d * g.dz[k]; vol += g.dz[k];
                    cnt++;
                }
            }
        LAST_DIV_BEFORE = maxDiv(g, st);
        // Neumann 相容性：RHS 的体积加权积分必须为零
        double mean = vol > 0 ? sum / vol : 0.0;
        double[] phi = solvePoisson(g, st, div, mean, maxIter);
        for (int j = 1; j < ny - 1; j++)
            for (int i = 0; i < nx; i++) {
                if (!g.isWet(i, j)) continue;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    double c = phi[q];
                    double pe = g.isWet(i + 1, j) ? phi[st.idx(i + 1, j, k)] : c;
                    double pn = g.isWet(i, j + 1) ? phi[st.idx(i, j + 1, k)] : c;
                    double pb = (k + 1 < nz) ? phi[st.idx(i, j, k + 1)] : c;
                    double dzd = (k + 1 < nz) ? 0.5 * (g.dz[k] + g.dz[k + 1]) : g.dz[k];
                    st.uF[q] -= (pe - c) / dx;
                    st.vF[q] -= (pn - c) / dy;
                    st.wF[q] -= (c - pb) / dzd;
                }
            }
        LAST_DIV_AFTER = maxDiv(g, st);
        FACES_READY = true;
        return LAST_DIV_BEFORE;
    }
}
