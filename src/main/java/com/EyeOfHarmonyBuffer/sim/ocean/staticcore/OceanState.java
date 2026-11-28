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
    /** ★ 2026-11-07（R3）：斜压 u（v1 没有；由 R3 动量平衡给出）。 */
    public final double[] uBaroclinic;
    public final double[] eta;
    /** 表面层厚度 h0（m），由局地 P-E+runoff 演化；h0=dz0+eta。 */
    public final double[] surfaceLayer;
    /** 热混合层厚度 h_T（m）。 */
    public final double[] mixedLayerDepth;
    public final double[] hTOld;
    public final double[] hTNew;
    /**
     * 冰面积分数 aice（0..1）。★ 2026-11-17（海冰 Stage A）语义改为**预报量**：
     * 由冰形成/融化/侧向融化/输运更新；不再每步由 iceThickness/H_ICE_REF 反算。
     * 在 ICE_STATE_V2=false 时仍保持旧诊断口径（逐位不变）。
     */
    public final double[] iceFraction;
    /**
     * 格点平均冰体积 vice（m）。注意：不是冰面厚度；冰面厚度 h_i = vice / max(aice, eps)。
     * 旧代码把它直接当冰面厚度用，是 Stage A 要修正的口径。
     */
    public final double[] iceThickness;
    /**
     * 格点平均雪体积 vsnow（m 雪深，不是水当量）。★ Stage A 新增；Stage B 起参与热力。
     * 雪面厚度 h_s = vsnow / max(aice, eps)。
     */
    public final double[] snowThickness;
    /** 2026-11-22：CESM 融池面积（grid fraction，0..1）。 */
    public final double[] meltPondArea;
    /** 2026-11-22：CESM 融池深度 [m]。 */
    public final double[] meltPondDepth;
    /** 2026-11-21 D3：最近一次冰面能量平衡解出的冰面温度 [K]；NaN = 无冰/未算。 */
    public final double[] iceSurfaceTemp;
    /** Standard 5-category ice thickness distribution (Phase 2c; unused by default). */
    public final IceThicknessDistribution itd;
    /** Numerical floe-size distribution (area weights; Route B). */
    public final FloeSizeDistribution fsd;
    /** EVP 冰速度（2D 格心，m/s）；Stage D2a 只建状态，默认不参与计算。 */
    public final double[] iceU;
    public final double[] iceV;
    /** EVP 应力分量（2D，N/m）：sig1=s11+s22, sig2=s11-s22, sig12。 */
    public final double[] sig1;
    public final double[] sig2;
    public final double[] sig12;
    /**
     * 冰-海盐通量 [kg/(m2 s)]：结冰为负（析盐、浓缩表层），融冰为正。
     * 由 {@code AdvectionDiffusionSolver.updateMixedLayer} 写入，{@code PrognosticSurfaceSalinitySolver} 消费并清零。
     */
    public final double[] iceSaltFlux;
    /** 雪融水通量 kg/(m2 s)，由冰步写入、盐度步消费并清零（Stage B）。 */
    public final double[] snowMeltFlux;
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
        uBaroclinic = new double[n];
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
        snowThickness = new double[grid.nx * grid.ny];
        meltPondArea = new double[grid.nx * grid.ny];
        meltPondDepth = new double[grid.nx * grid.ny];
        iceSurfaceTemp = new double[grid.nx * grid.ny];
        java.util.Arrays.fill(iceSurfaceTemp, Double.NaN);
        itd = new IceThicknessDistribution(grid);
        fsd = new FloeSizeDistribution(grid);
        iceU = new double[grid.nx * grid.ny];
        iceV = new double[grid.nx * grid.ny];
        sig1 = new double[grid.nx * grid.ny];
        sig2 = new double[grid.nx * grid.ny];
        sig12 = new double[grid.nx * grid.ny];
        iceSaltFlux = new double[grid.nx * grid.ny];
        snowMeltFlux = new double[grid.nx * grid.ny];
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

    /** 冰面厚度 h_i（m）：iceThickness 是格点体积，必须除以冰面积。 */
    public double iceActualThickness(int i, int j) {
        int q = j * grid.nx + i;
        double a = iceFraction[q];
        return a > 1.0e-6 ? iceThickness[q] / a : 0.0;
    }

    /** 雪面厚度 h_s（m）：snowThickness 是格点雪体积。 */
    public double snowDepth(int i, int j) {
        int q = j * grid.nx + i;
        double a = iceFraction[q];
        return a > 1.0e-6 ? snowThickness[q] / a : 0.0;
    }
}
