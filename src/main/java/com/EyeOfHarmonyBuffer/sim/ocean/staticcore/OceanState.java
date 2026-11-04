package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** F5 静态气候解的 3D 状态。 */
public final class OceanState {

    public final OceanGrid grid;
    public final double[] t;
    public final double[] s;
    public final double[] u;
    public final double[] v;
    public final double[] w;
    /** 热成风斜压 v（不含正压风生流），供 OHT 积分。 */
    public final double[] vBaroclinic;
    public final double[] eta;
    /** 表面层厚度 h0（m），由局地 P-E+runoff 演化；h0=dz0+eta。 */
    public final double[] surfaceLayer;
    /** 热混合层厚度 h_T（m）。 */
    public final double[] mixedLayerDepth;
    public final double[] hTOld;
    public final double[] hTNew;
    /** 最小海冰占位：冰覆盖率与冰厚（provisional，不是真正海冰系统）。 */
    public final double[] iceFraction;
    public final double[] iceThickness;
    /**
     * 冰-海盐通量 [kg/(m2 s)]：结冰为负（析盐、浓缩表层），融冰为正。
     * 由 {@code AdvectionDiffusionSolver.updateMixedLayer} 写入，{@code PrognosticSurfaceSalinitySolver} 消费并清零。
     */
    public final double[] iceSaltFlux;
    /** 正压流函数 psi (m^2/s)，用于 u=-dpsi/dy, v=dpsi/dx。 */
    public double[] psi;
    /**
     * ★ 2026-11-03（C 网格）：面速度。uF=东面(i+1/2)、vF=北面(j+1/2)、wF=底面(k+1/2，正=向上)。
     * 投影/有限体积输运都读它，不再用格心速度直接做差分。
     */
    public final double[] uF, vF, wF;

    public OceanState(OceanGrid grid) {
        this.grid = grid;
        int n = grid.nx * grid.ny * grid.nz;
        t = new double[n];
        s = new double[n];
        u = new double[n];
        v = new double[n];
        w = new double[n];
        vBaroclinic = new double[n];
        uF = new double[n];
        vF = new double[n];
        wF = new double[n];
        eta = new double[grid.nx * grid.ny];
        surfaceLayer = new double[grid.nx * grid.ny];
        mixedLayerDepth = new double[grid.nx * grid.ny];
        hTOld = new double[grid.nx * grid.ny];
        hTNew = new double[grid.nx * grid.ny];
        iceFraction = new double[grid.nx * grid.ny];
        iceThickness = new double[grid.nx * grid.ny];
        iceSaltFlux = new double[grid.nx * grid.ny];
        for (int j = 0; j < grid.ny; j++) {
            for (int i = 0; i < grid.nx; i++) {
                surfaceLayer[j * grid.nx + i] = grid.dz[0];
                mixedLayerDepth[j * grid.nx + i] = grid.dz[0];
            }
        }
        psi = new double[grid.nx * grid.ny];
    }

    public int idx(int i, int j, int k) {
        return (k * grid.ny + j) * grid.nx + (i % grid.nx + grid.nx) % grid.nx;
    }

    /**
     * 阶段 3：海底以下的干格取上一层（wetLayers-1）的值，u/v/w 置 0。
     * <p>这样垂直扩散在海底自动零通量（零梯度），平流面由 wF=0 封住。
     */
    public void fillDryGhosts() {
        int nx = grid.nx, ny = grid.ny, nz = grid.nz;
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                int wl = grid.wetLayers[grid.idx(i, j)];
                if (wl <= 0) continue;
                int ql = idx(i, j, wl - 1);
                double tl = t[ql], sl = s[ql];
                for (int k = wl; k < nz; k++) {
                    int q = idx(i, j, k);
                    t[q] = tl; s[q] = sl;
                    u[q] = 0.0; v[q] = 0.0; w[q] = 0.0;
                }
            }
    }

    public double surfaceT(int i, int j) { return t[idx(i, j, 0)]; }
    public double surfaceS(int i, int j) { return s[idx(i, j, 0)]; }
}
