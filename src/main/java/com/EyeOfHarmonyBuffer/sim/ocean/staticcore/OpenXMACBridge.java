package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** Bridge from OceanState faces to the X1 MAC projection and back. */
public final class OpenXMACBridge {
    private OpenXMACBridge() {}

    /** S0.1 诊断：最近一次桥接实际用到的有限值个数（西/东）。 */
    public static int LAST_W_FACES_CNT = 0, LAST_E_FACES_CNT = 0;

    public static double project(OceanGrid g, OceanState st, OceanParent parent,
                                 int maxIter, double tol) {
        int nx = g.nx, ny = g.ny, nz = g.nz;
        int n3 = nx * ny * nz;
        boolean[] wet = new boolean[n3];
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) wet[(k * ny + j) * nx + i] = g.isWet(i, j, k);

        double[] u = new double[(nx + 1) * ny * nz];
        double[] v = new double[nx * (ny + 1) * nz];
        double[] w = new double[nx * ny * (nz + 1)];

        // Reconstruct MAC faces from cell velocities (same as VelocityProjection step 1).
        for (int k = 0; k < nz; k++)
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j, k)) continue;
                    int q = (k * ny + j) * nx + i;
                    if (i + 1 < nx && g.isWet(i + 1, j, k))
                        u[(k * ny + j) * (nx + 1) + i + 1] = 0.5 * (st.u[q] + st.u[q + 1]);
                    if (j + 1 < ny && g.isWet(i, j + 1, k))
                        v[(k * (ny + 1) + j + 1) * nx + i] = 0.5 * (st.v[q] + st.v[q + nx]);
                    if (k + 1 < nz && g.isWet(i, j, k + 1))
                        w[k * nx * ny + j * nx + i] = 0.5 * (st.w[q] + st.w[q + nx * ny]);
                }

        // ★ 2026-11-24（S0.1）：父解在陆地（四角全干）返回 NaN。原先直接累加 ⟹ 一个 NaN
        //   污染 west/east，继而污染全部西/东 MAC 面与整个 3D 状态（实测 89% 格点 NaN）。
        //   只累加有限值、分别计数；一个都没有时取 0（与 parent==null 的旧行为一致）。
        double west = 0.0, east = 0.0;
        int cntW = 0, cntE = 0;
        for (int k = 0; k < nz; k++)
            for (int j = 1; j < ny - 1; j++) {
                if (parent != null) {
                    double uw = parent.uMs(g.xOf(0), g.zOf(j), k);
                    double ue = parent.uMs(g.xOf(nx - 1), g.zOf(j), k);
                    if (Double.isFinite(uw)) { west += uw; cntW++; }
                    if (Double.isFinite(ue)) { east += ue; cntE++; }
                }
            }
        west = cntW > 0 ? west / cntW : 0.0;
        east = cntE > 0 ? east / cntE : 0.0;
        LAST_W_FACES_CNT = cntW;
        LAST_E_FACES_CNT = cntE;
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++) {
                u[(k * ny + j) * (nx + 1)] = west;
                u[(k * ny + j) * (nx + 1) + nx] = east;
            }
        for (int k = 0; k < nz; k++)
            for (int i = 0; i < nx; i++) v[(k * (ny + 1) + ny) * nx + i] = v[k * (ny + 1) * nx + i];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) w[nz * nx * ny + j * nx + i] = w[j * nx + i];

        double max = OpenXMAC3DWet.project(nx, ny, nz, u, v, w, g.dx, g.dy, 1.0, wet,
                west, east, maxIter, tol);

        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    int q = (k * ny + j) * nx + i;
                    st.uF[q] = u[(k * ny + j) * (nx + 1) + i + 1];
                    st.vF[q] = v[(k * (ny + 1) + j + 1) * nx + i];
                    st.wF[q] = w[k * nx * ny + j * nx + i];
                }
        VelocityProjection.FACES_READY = true;
        VelocityProjection.LAST_DIV_AFTER = max;
        return max;
    }
}
