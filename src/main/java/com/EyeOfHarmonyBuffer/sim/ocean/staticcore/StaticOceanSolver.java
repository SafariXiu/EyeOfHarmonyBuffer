package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;
import com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceForcing;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * F5 静态气候解流水线：LandV3 -> Stommel -> T/S 平流扩散 -> 结果。
 *
 * <p>★ 2026-11-02（甲：季节循环）：不再只解一个相位。按 {@link #SEASONAL_N} 个相位
 * 依次求稳态（热启动：保留上一季的 T/S/h），最后输出**年平均场**。
 * <p>★ 2026-11-02（KT + 多年）：h 由 Kraus–Turner 卷吸方程时间积分（见
 * {@code AdvectionDiffusionSolver}），不再用"B&lt;0 就 h=hLimit"的瞬时整柱对流；
 * 并按年循环至 {@link #SEASONAL_CONV_K} 收敛（{@link #SEASONAL_YEARS} 为上限）。
 *
 * <p>生产默认不调用；只生成一次，后续走缓存。
 */
public final class StaticOceanSolver {

    /** ★ 缓存换代：v4 季节循环；v5 稳态短波源 + 海冰；v6 KT 卷吸 + 多年循环 + 分层 KV。 */
    public static final int SOLVER_VERSION = 7;

    /**
     * 季节采样数：4 = 季（默认）；12 = 月。
     * <p>⚠ 2026-11-02 实测：12 月 + 3D 盐度 + 冰盐析会累积到 S=215 psu / MOC=1272 Sv；
     * 根因是"整柱对流 → 单季结冰 3 m → 盐析通量过大"。在修好这个链之前，默认用 4 季。
     */
    public static int SEASONAL_N = 4;
    /** 冷启动第一季的 Picard 次数（eta <-> T/S 耦合）。 */
    public static int PIC_FIRST = 2;
    /** 之后每季的 Picard 次数（热启动）。 */
    public static int PIC_WARM = 1;

    /**
     * 多年循环的最大年数。
     * <p>★ 2026-11-02 实测（seed 0，4 季/年，KT + 分层 KV + 冰）：
     * 跑 2 年会进入**雪球态**——T=270.65..287.29 K、冰盖 6636/7946（83.5%）、
     * 平均冰厚 22.45 m、h 均值 1008 m、MOC=359.9 Sv。
     * 机理：深层仍可整柱不稳定 → KT 的 ΔT 被 0.05 下限放大 → h 仍会跳到 hLimit；
     *      冰反照率正反馈再把整个海洋冻住。这是**真发现**，但默认不能跑成雪球。
     * ⟹ 默认 1 年；2+ 年作为诊断开关（SEASONAL_YEARS=2/3）。
     */
    public static int SEASONAL_YEARS = 1;

    /**
     * ★ 2026-11-03：S 初值用物理纬向剖面（高纬淡、副热带咸）。
     * 默认关；A/B 用 NSINIT=1。这不是拟合：高纬表层淡（~34）是海洋气候学的基本事实，
     * 缺了它高纬盐度层结为零，冬季整柱对流（h=1739 m）。
     */
    public static boolean S_PROFILE_INIT_ON = true;
    /** 调试：逐季打印高纬（|lat|>60）平均 h。 */
    public static boolean DEBUG_SEASON_H = false;

    /** 表层盐度纬向剖面（psu）：热带 34.8 / 副热带 35.5 / 中纬 35.0 / 高纬 34.3–34.0。 */
    static double sProfile(double latDeg) {
        double a = Math.abs(latDeg);
        if (a < 15.0) return 34.8;
        if (a < 30.0) return 35.5;
        if (a < 45.0) return 35.0;
        if (a < 60.0) return 34.3;
        return 34.0;
    }
    /** 收敛判据：相邻两年年平均 T 的最大差 [K]。 */
    public static double SEASONAL_CONV_K = 0.05;

    /** 表面通量边界（A）；默认 true。 */
    public static boolean FLUX_BOUNDARY_ON = true;

    /**
     * ★ 2026-11-02（方案 B）：S 用季节内 prognostic flux-form（30 天 / 30 步），
     * 替代稳态 SOR+全局总盐 corr。默认关，A/B 用环境变量 NPROGS=1。
     */
    public static boolean PROGNOSTIC_S_ON = true;

    private StaticOceanSolver() {}

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season) {
        return solve(worldSeed, cell, x0, z0, forcing, theta, landVersion, season, 50000);
    }

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter) {
        OceanGrid grid = OceanGrid.landV3(worldSeed, cell, x0, z0);
        SeaSurfaceForcing periodic = new PeriodicSeaSurfaceForcing(forcing);
        int nx = grid.nx, ny = grid.ny, nz = grid.nz;
        int n3 = nx * ny * nz, n2 = nx * ny;

        double[] fallback = new double[n2];
        for (int j = 0; j < ny; j++) {
            double lat = WorldContract.latOf((int) Math.round(grid.zOf(j)));
            for (int i = 0; i < nx; i++) fallback[j * nx + i] = Atmosphere.oceanBaseK(lat);
        }
        double[] surfaceT = fallback.clone();

        OceanState st = new OceanState(grid);
        // 冷启动：★ 2026-11-02 修正输入 —— 深层不能也取表层气候值。
        // 旧版把 oceanBaseK 赋给所有深度 ⟹ 深层天生是暖的 ⟹ 高纬表层(冷)比深层密 ⟹
        // 密度判据永远找不到稳定界面 ⟹ h 顶到全深度；OHT 的深层暖支也把符号带反。
        // 这里用一条**无自由参数**的线性剖面：表层 = oceanBaseK，底层 = 海水冰点。
        // 冰点是物理量（FreezingPoint，UNESCO 1983），不是调参。
        double deepT = com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint.referenceKelvin();
        double hTotal = 0.0;
        for (int k = 0; k < nz; k++) hTotal += grid.dz[k];
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                double ts = fallback[j * nx + i];
                double dep = 0.0;
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    double frac = Math.min(1.0, (dep + 0.5 * grid.dz[k]) / hTotal);
                    st.t[q] = ts + (deepT - ts) * frac;
                    if (st.s[q] == 0.0) {
                        if (S_PROFILE_INIT_ON) {
                            double latDeg = Math.toDegrees(WorldContract.latOf((int) Math.round(grid.zOf(j))));
                            double sSurf = sProfile(latDeg);
                            st.s[q] = sSurf + (34.9 - sSurf) * frac;
                        } else {
                            st.s[q] = 35.0;
                        }
                    }
                    dep += grid.dz[k];
                }
            }

        int nseas = Math.max(1, SEASONAL_N);
        int years = Math.max(1, SEASONAL_YEARS);
        double[] tSum = new double[n3], sSum = new double[n3];
        double[] hSum = new double[n2], etaSum = new double[n2], iceSum = new double[n2];
        double[] tMean = new double[n3], sMean = new double[n3], prevT = new double[n3];
        double[] hMean = new double[n2], etaMean = new double[n2], iceMean = new double[n2];

        AdvectionDiffusionSolver.Result adv = null;
        double sBudget = 0.0;
        BarotropicSolver.Result baro = null;
        double maxDT = 0.0;
        int yearsRun = 0;

        // ★ 2026-11-02（KT + 多年）：h 现在是卷吸方程的时间积分，深层通风是多年过程。
        // 单跑一年到不了气候平衡；按年循环，直到相邻两年的年平均 T 收敛。
        for (int yr = 0; yr < years; yr++) {
            java.util.Arrays.fill(tSum, 0.0);
            java.util.Arrays.fill(sSum, 0.0);
            java.util.Arrays.fill(hSum, 0.0);
            java.util.Arrays.fill(etaSum, 0.0);
            java.util.Arrays.fill(iceSum, 0.0);
            for (int si = 0; si < nseas; si++) {
                double th = (nseas == 1) ? theta : (2.0 * Math.PI * si / nseas);
                baro = BarotropicSolver.update(grid, st, periodic, th);
                // update() 覆盖了 u/v，但 vBaroclinic/w 仍是上一季的 ⟹ 先清掉。
                java.util.Arrays.fill(st.vBaroclinic, 0.0);
                java.util.Arrays.fill(st.w, 0.0);
                SteadyFreeSurfaceSolver.solve(grid, st, periodic, th);
                int pics = (yr == 0 && si == 0) ? PIC_FIRST : PIC_WARM;
                for (int pic = 0; pic < pics; pic++) {
                    if (FLUX_BOUNDARY_ON) {
                        adv = AdvectionDiffusionSolver.solveFluxBoundary(grid, st, periodic, th,
                            advMaxIter, 3);
                        for (int j = 0; j < ny; j++)
                            for (int i = 0; i < nx; i++)
                                if (grid.isWet(i, j)) surfaceT[j * nx + i] = st.t[st.idx(i, j, 0)];
                    } else {
                        surfaceT = SurfaceTemperatureSolver.solve(grid, periodic, th, fallback, st);
                        AdvectionDiffusionSolver.init(grid, st, surfaceT);
                        double tRes = BiCGSTABT.solve(grid, st, surfaceT, advMaxIter, 1.0e-6);
                        if (!Double.isFinite(tRes) || tRes > 1.0) {
                            adv = AdvectionDiffusionSolver.solve(grid, st, surfaceT,
                                Math.max(advMaxIter, 5000));
                            tRes = adv.residual;
                        } else {
                            adv = new AdvectionDiffusionSolver.Result(0, tRes);
                        }
                    }
                    // 3D 盐度：默认稳态 SOR；PROGNOSTIC_S_ON 时走季节内 flux-form 时间步。
                    AdvectionDiffusionSolver.Result sR;
                    if (PROGNOSTIC_S_ON) {
                        double dSalt = PrognosticSalinity.run(grid, st, periodic, th,
                                30.0 * 86400.0, 30);
                        sR = new AdvectionDiffusionSolver.Result(0, Math.abs(dSalt));
                    } else {
                        sR = AdvectionDiffusionSolver.solveSalinity(grid, st,
                            periodic, th, Math.min(advMaxIter, 80));
                    }
                    sBudget = sR.residual;
                    // ★ 2026-11-03（stage 3 步序）：盐度改变了 S ⟹ 表层 Tf 变了，重钳。
                    if (AdvectionDiffusionSolver.FREEZE_SURFACE_ON) {
                        for (int j = 1; j < ny - 1; j++)
                            for (int i = 0; i < nx; i++) {
                                if (!grid.isWet(i, j)) continue;
                                int q0 = st.idx(i, j, 0);
                                double tf = FreezingPoint.kelvin(st.s[q0]);
                                if (st.t[q0] < tf) st.t[q0] = tf;
                            }
                    }
                    BaroclinicFlow.apply(grid, st);
                }
                if (DEBUG_SEASON_H) {
                    double hh = 0.0;
                    int hn = 0;
                    for (int j = 1; j < ny - 1; j++) {
                        double lat = Math.toDegrees(WorldContract.latOf((int) Math.round(grid.zOf(j))));
                        if (Math.abs(lat) < 60.0) continue;
                        for (int i = 0; i < nx; i++) {
                            if (!grid.isWet(i, j)) continue;
                            hh += st.mixedLayerDepth[j * nx + i];
                            hn++;
                        }
                    }
                    System.out.println("SEASONH si=" + si + " hiLatH=" + (hn > 0 ? hh / hn : 0.0));
                }
                // 年度累加
                for (int j = 0; j < ny; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!grid.isWet(i, j)) continue;
                        int js = j * nx + i;
                        hSum[js] += st.mixedLayerDepth[js];
                        etaSum[js] += st.eta[js];
                        iceSum[js] += st.iceThickness[js];
                        for (int k = 0; k < nz; k++) {
                            int q = st.idx(i, j, k);
                            tSum[q] += st.t[q];
                            sSum[q] += st.s[q];
                        }
                    }
            }
            // 年度平均
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int js = j * nx + i;
                    hMean[js] = hSum[js] / nseas;
                    etaMean[js] = etaSum[js] / nseas;
                    iceMean[js] = iceSum[js] / nseas;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        tMean[q] = tSum[q] / nseas;
                        sMean[q] = sSum[q] / nseas;
                    }
                }
            maxDT = 0.0;
            if (yr > 0)
                for (int q = 0; q < n3; q++) maxDT = Math.max(maxDT, Math.abs(tMean[q] - prevT[q]));
            System.arraycopy(tMean, 0, prevT, 0, n3);
            yearsRun = yr + 1;
            if (yr > 0 && maxDT < SEASONAL_CONV_K) break;
        }

        // 最后一年平均写入状态
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                int js = j * nx + i;
                st.mixedLayerDepth[js] = hMean[js];
                st.eta[js] = etaMean[js];
                st.iceThickness[js] = iceMean[js];
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    st.t[q] = tMean[q];
                    st.s[q] = sMean[q];
                }
            }
        // ★ 2026-11-02：h 用最终年平均 T/S 重诊（逐季平均的 h 与 T/S 不自洽）。
        AdvectionDiffusionSolver.diagnoseMixedLayer(grid, st);
        // 用年平均 T/S 重算流场：先清零斜压场，再把 u/v 重置为正压基态
        java.util.Arrays.fill(st.vBaroclinic, 0.0);
        java.util.Arrays.fill(st.w, 0.0);
        baro = BarotropicSolver.update(grid, st, periodic, (nseas == 1) ? theta : 0.0);
        if (AdvectionDiffusionSolver.FREEZE_SURFACE_ON) {
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0);
                    double tf = FreezingPoint.kelvin(st.s[q0]);
                    if (st.t[q0] < tf) st.t[q0] = tf;
                }
        }
        BaroclinicFlow.apply(grid, st);

        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (grid.isWet(i, j)) surfaceT[j * nx + i] = st.t[st.idx(i, j, 0)];

        OceanCacheKey key = new OceanCacheKey(worldSeed, x0, z0,
            landVersion, SOLVER_VERSION, season);
        double psiMin = 0.0, psiMax = 0.0;
        for (int j = 0; j < ny; j++) {
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                double p = st.psi[grid.idx(i, j)];
                if (p < psiMin) psiMin = p;
                if (p > psiMax) psiMax = p;
            }
        }
        // ★ 2026-11-03：口径统一——MOC = max|ψ(y,z)| × 湿段宽度（BaroclinicFlow.LAST_MOC）。
        // 旧式用【正压】2D ψ 的 (max−min)/1e6，漏了湿宽且混用了正压/斜压两套 ψ ⟹ 报告 0.1 Sv（实际 141）。
        double mocSv = BaroclinicFlow.LAST_MOC;
        return new OceanSolution(key, st, surfaceT,
            baro.iterations, adv == null ? 0 : adv.iterations,
            baro.residual, adv == null ? 0.0 : adv.residual,
            psiMin, psiMax, mocSv,
            sBudget, sBudget);
    }
}
