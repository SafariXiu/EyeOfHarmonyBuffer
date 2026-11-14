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

    /**
     * 缓存换代：v4 季节循环；v5 稳态短波源 + 海冰；v6 KT 卷吸 + 多年循环 + 分层 KV；
     * v7 prognostic S/T + C 网格投影；v8（2026-11-03）域宽 2 圈 -> 1 圈（40,000 km）+ 旋度配对修正。
     */
    public static final int SOLVER_VERSION = 9;   // ★ 2026-11-04（A6）：干格层判、MOC 口径、内部冰点、海气耦合

    /**
     * 季节采样数：4 = 季（默认）；12 = 月。
     * <p>⚠ 2026-11-02 实测：12 月 + 3D 盐度 + 冰盐析会累积到 S=215 psu / MOC=1272 Sv；
     * 根因是"整柱对流 → 单季结冰 3 m → 盐析通量过大"。在修好这个链之前，默认用 4 季。
     */
    public static int SEASONAL_N = 4;

    /**
     * ★ 2026-11-12（交接文档 §11.3 B1，用户批准）：收尾时把 {@code iceFraction} 与冰厚统一到年平口径。
     *
     * <p>问题：收尾循环把 {@code iceThickness} 换成年平均，但 {@code iceFraction} 停在最后一季
     * ⟹ 存出去的状态混两个时间口径，而世界（{@code Atmosphere.ICE_PROVIDER}）读的是 iceFraction。
     *
     * <p>实测（NICECAL，1 年、全湿格 6466）：只有 **2 格**不同 —— 冰厚普遍 > H_ICE_REF=0.5 m
     * ⟹ 分数早已饱和成 1，所以这是口径修复而不是改解。
     *
     * <p><b>默认 true</b>（用户 2026-11-12 批准改默认）；置 false 逐位回到旧口径。
     * 会改变世界输出 ⟹ 已折进 {@code solveFlagsHash()}（A3-⑤），旧缓存不会命中。
     */
    public static boolean ICE_FRACTION_FROM_MEAN = Boolean.parseBoolean(
            System.getProperty("eoh.icefrommean", "true"));
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
    /** ★ 2026-11-07（R3 诊断）：最近一次 solve 的年际 max|dT| 与实际跑了几年。 */
    public static double LAST_MAX_DT = 0.0;
    /** 年际 max|ΔT| 所在格点（q 索引；-1 = 未计算）。用于定位漂移源。 */
    public static int LAST_MAX_DT_Q = -1;
    /** 最近一年的逐格 |ΔT|（年际残差；诊断用）。 */
    public static double[] LAST_RESID = new double[0];
    public static int LAST_YEARS_RUN = 0;
    /** ★ 2026-11-09：年际 max|dS|。 */
    public static double LAST_MAX_DS = 0.0;

    /**
     * ★ 2026-11-09（按用户裁决）：把外层年循环当成**不动点迭代**来解，而不是"跑很多年"。
     *
     * <p>为什么要这个：本类的外层是
     * 稳态动量 -> 30 天示踪平流 -> 稳态动量 -> ...，本质是 Picard 迭代。
     * 我们只需要一个**稳定解**（算一次、缓存、世界直接用），所以判据应该是
     * "映射残差是否降到零"，而不是"跑了几年"。
     *
     * <p>FP_SOLVER_ON=true 时：每年把年平均 T/S 与上一年做**欠松弛**
     * (1-lambda)*prev + lambda*mean，lambda 自适应；并打印/记录残差序列。
     * 默认 false = 逐位不变。
     */
    public static boolean FP_SOLVER_ON = false;
    public static double FP_RELAX = 0.5;
    public static double FP_RELAX_MIN = 0.05;
    public static double LAST_RESID_T = 0.0, LAST_RESID_S = 0.0;
    /** 每年残差序列（T），长度 = 实际跑的年数。 */
    public static final java.util.ArrayList<Double> FP_RESID_T = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> FP_RESID_S = new java.util.ArrayList<>();
    /** 逐年年平均 T（气候平均门用；每项长度 n3）。 */
    public static final java.util.ArrayList<double[]> YEAR_T_MEAN = new java.util.ArrayList<>();
    /** 逐年全球热/盐/冰总量（面积加权；收支诊断用）。 */
    public static final java.util.ArrayList<Double> YEAR_HEAT = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_SALT = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_ICE = new java.util.ArrayList<>();
    /** 逐年上层（z<2000 m）与深层（z>=2000 m）的热量（面积加权；深层慢漂诊断）。 */
    public static final java.util.ArrayList<Double> YEAR_HEAT_UP = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_HEAT_DEEP = new java.util.ArrayList<>();
    /** 逐年 |ΔT| 的年际残差分位数（p50/p90/p99；气候定常的稳健判据）。 */
    public static final java.util.ArrayList<Double> YEAR_RESID_P50 = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_RESID_P90 = new java.util.ArrayList<>();
    public static final java.util.ArrayList<Double> YEAR_RESID_P99 = new java.util.ArrayList<>();

    /** 表面通量边界（A）；默认 true。 */
    public static boolean FLUX_BOUNDARY_ON = true;
    /** 诊断：每季末检查 T/S/u/v/w/h/ice 是否有限（默认关，零开销路径不变）。 */
    public static boolean DEBUG_FINITE = false;

    /**
     * ★ 2026-11-02（方案 B）：S 用季节内 prognostic flux-form（30 天 / 30 步），
     * 替代稳态 SOR+全局总盐 corr。默认关，A/B 用环境变量 NPROGS=1。
     */
    public static boolean PROGNOSTIC_S_ON = true;

    private StaticOceanSolver() {}

    /**
     * ★ 2026-11-07（R3）：动量来源二选一。
     * <p>R3 = 稳态 3D 动量平衡（Coriolis + 压力梯度 + A_V），给出局地斜压 u/v；
     * 关 = v1 的 Stommel + 行均匀热成风（逐位不变）。
     */
    static void applyMomentum(OceanGrid g, OceanState st) {
        if (R3Momentum.ON) R3Momentum.applyToState(g, st);
        else BaroclinicFlow.apply(g, st);
    }

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season) {
        return solve(worldSeed, cell, x0, z0, forcing, theta, landVersion, season, 50000);
    }

    public static OceanSolution solve(int worldSeed, int cell, int x0, int z0,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter) {
        return solve(OceanGrid.landV3(worldSeed, cell, x0, z0), worldSeed, forcing, theta,
            landVersion, season, advMaxIter);
    }

    /**
     * ★ 2026-11-03（阶段 2）：在【任意域】上求解 —— 区域 patch 的入口。
     *
     * <p>域由 {@code grid} 给出：绝对 x/z、真实 LandV3（不折叠）。默认路径
     * {@link #solve(int,int,int,int,SeaSurfaceForcing,double,int,int,int)} 只是用它构造
     * 40,000 km 全球箱并转调本方法，所以旧路径逐位不变。
     */
    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter) {
        return solve(grid, worldSeed, forcing, theta, landVersion, season, advMaxIter, null, 0);
    }

    /**
     * ★ 2026-11-03（阶段 2b）：带父状态 + sponge 的 patch 入口。
     *
     * <p>{@code parent == null} 时与旧路径逐位相同。sponge 只改外圈 {@code spongeCells}
     * 个湿格（y 两端；x 侧等阶段 2c 打开开边界后再生效），中心区完全由本域方程决定。
     */
    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter,
                                      OceanParent parent, int spongeCells) {
        return solve(grid, worldSeed, forcing, theta, landVersion, season, advMaxIter,
            parent, spongeCells, null);
    }

    /**
     * ★ 2026-11-04（阶段 3）：深层源水团初始化（两段式的第二段）。
     *
     * <p>物理依据：深水在高纬形成；深层 T/S 应由【源水团】决定，而不是由局地表面线性降到冰点。
     * {@code hHL} 用模型自己的高纬平均混合层深度（不是新常数）。
     */
    public static final class DeepInit {
        public final double tDeep, sDeep, hHL;
        public DeepInit(double tDeep, double sDeep, double hHL) {
            this.tDeep = tDeep;
            this.sDeep = sDeep;
            this.hHL = Math.max(1.0, hHL);
        }
    }

    public static OceanSolution solve(OceanGrid grid, int worldSeed,
                                      SeaSurfaceForcing forcing, double theta,
                                      int landVersion, int season, int advMaxIter,
                                      OceanParent parent, int spongeCells, DeepInit deepInit) {
        SeaSurfaceForcing periodic = new PeriodicSeaSurfaceForcing(forcing);
        int nx = grid.nx, ny = grid.ny, nz = grid.nz;
        int n3 = nx * ny * nz, n2 = nx * ny;

        double[] fallback = new double[n2];
        for (int j = 0; j < ny; j++) {
            double lat = grid.latAt(j);
            for (int i = 0; i < nx; i++) fallback[j * nx + i] = Atmosphere.oceanBaseK(lat);
        }
        double[] surfaceT = fallback.clone();

        OceanState st = new OceanState(grid);
        // ★ 2026-11-11（深层收支仪器）：清零并算体积权重；ON=false 时只是几个空数组。
        DeepBudget.begin(grid);
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
                            double latDeg = Math.toDegrees(grid.latAt(j));
                            double sSurf = sProfile(latDeg);
                            st.s[q] = sSurf + (34.9 - sSurf) * frac;
                        } else {
                            st.s[q] = 35.0;
                        }
                    }
                    dep += grid.dz[k];
                }
            }

        // ★ 2026-11-03（阶段 2b）：有父状态时用【父】热启动。
        //   区域模式的标准做法：边界与初值都取父，避免 patch 从解析剖面冷启动
        //   产生的 1 年瞬态污染 P2 收敛测试。不存在自造参数：只是换初值。
        if (parent != null) {
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    double x = grid.xOf(i), z = grid.zOf(j);
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double pt = parent.temperatureK(x, z, k);
                        if (Double.isFinite(pt)) st.t[q] = pt;
                        double ps = parent.salinityPsu(x, z, k);
                        if (Double.isFinite(ps)) st.s[q] = ps;
                        double pu = parent.uMs(x, z, k);
                        if (Double.isFinite(pu)) st.u[q] = pu;
                        double pv = parent.vMs(x, z, k);
                        if (Double.isFinite(pv)) st.v[q] = pv;
                    }
                }
        }

        // ★ 2026-11-04（阶段 3）：深层源水团初始化（在父热启动之后应用；只由调用方显式开启）。
        if (deepInit != null) {
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int wl = grid.wetLayers[grid.idx(i, j)];
                    double ts = st.t[st.idx(i, j, 0)];
                    double ss = st.s[st.idx(i, j, 0)];
                    double dep = 0.0;
                    for (int k = 0; k < wl; k++) {
                        double zc = dep + 0.5 * grid.dz[k];
                        double frac = Math.min(1.0, zc / deepInit.hHL);
                        int q = st.idx(i, j, k);
                        st.t[q] = ts + (deepInit.tDeep - ts) * frac;
                        st.s[q] = ss + (deepInit.sDeep - ss) * frac;
                        dep += grid.dz[k];
                    }
                }
            st.fillDryGhosts();
        }

        int nseas = Math.max(1, SEASONAL_N);
        int years = Math.max(1, SEASONAL_YEARS);
        // ★ 2026-11-09（阶段 3 / A5）：一季 = 一年/nseas，而不是固定 30 天。
        //   旧版 4×30=120 天/年，热/盐/冰年少积分 3.04×。
        AdvectionDiffusionSolver.SEASON_SECONDS =
                WorldContract.DAYS_PER_YEAR * 86400.0 / nseas;
        AdvectionDiffusionSolver.SUBSTEPS = AdvectionDiffusionSolver.substepsFor(nseas);
        FP_RESID_T.clear();
        FP_RESID_S.clear();
        YEAR_T_MEAN.clear();
        YEAR_HEAT.clear();
        YEAR_SALT.clear();
        YEAR_ICE.clear();
        YEAR_HEAT_UP.clear();
        YEAR_HEAT_DEEP.clear();
        YEAR_RESID_P50.clear();
        YEAR_RESID_P90.clear();
        YEAR_RESID_P99.clear();
        // ★★ 2026-11-06（用户裁决：开分季 SST 出口）：保留**最后一年**的逐季表层 T。
        //   为什么必需：P292 的自洽判据要问“上升流”，而上升流是**季节**现象；
        //   原来 OceanSolution 只存年均 SST'，季节信号被抹平（NUPWELL 四版实测 corr≈0）。
        //   只存字段、不参与任何计算 ⇒ 年均路径逐位不变。
        double[][] seasSurf = new double[nseas][];
        double[] tSum = new double[n3], sSum = new double[n3];
        double[] hSum = new double[n2], etaSum = new double[n2], iceSum = new double[n2];
        double[] tMean = new double[n3], sMean = new double[n3], prevT = new double[n3], prevS = new double[n3];
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
                baro = BarotropicSolver.update(grid, st, periodic, th, parent);
                if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE baro yr=" + yr + " si=" + si + " " + b); }
                // update() 覆盖了 u/v，但 vBaroclinic/w 仍是上一季的 ⟹ 先清掉。
                java.util.Arrays.fill(st.vBaroclinic, 0.0);
                java.util.Arrays.fill(st.w, 0.0);
                if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, true);
                st.fillDryGhosts();
                SteadyFreeSurfaceSolver.solve(grid, st, periodic, th);
                if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE freeSurf yr=" + yr + " si=" + si + " " + b); }
                int pics = (yr == 0 && si == 0) ? PIC_FIRST : PIC_WARM;
                for (int pic = 0; pic < pics; pic++) {
                    if (FLUX_BOUNDARY_ON) {
                        adv = AdvectionDiffusionSolver.solveFluxBoundary(grid, st, periodic, th,
                            advMaxIter, AdvectionDiffusionSolver.OUTER_MAX);
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
                    if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE T yr=" + yr + " si=" + si + " " + b); }
                    if (PROGNOSTIC_S_ON) {
                        double dSalt = PrognosticSalinity.run(grid, st, periodic, th,
                                AdvectionDiffusionSolver.SEASON_SECONDS, AdvectionDiffusionSolver.SUBSTEPS);
                        sR = new AdvectionDiffusionSolver.Result(0, Math.abs(dSalt));
                    } else {
                        sR = AdvectionDiffusionSolver.solveSalinity(grid, st,
                            periodic, th, Math.min(advMaxIter, 80));
                    }
                    sBudget = sR.residual;
                    if (DEBUG_FINITE) { String b = firstNonFinite(grid, st); if (b != null) System.out.println("NONFINITE_STAGE S yr=" + yr + " si=" + si + " " + b); }
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
                    applyMomentum(grid, st);
                    if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, true);
                    st.fillDryGhosts();
                }
                if (DEBUG_FINITE) {
                    String bad = firstNonFinite(grid, st);
                    if (bad != null) System.out.println("NONFINITE yr=" + yr + " si=" + si + " " + bad);
                }
                if (DEBUG_SEASON_H) {
                    double hh = 0.0;
                    int hn = 0;
                    for (int j = 1; j < ny - 1; j++) {
                        double lat = Math.toDegrees(grid.latAt(j));
                        if (Math.abs(lat) < 60.0) continue;
                        for (int i = 0; i < nx; i++) {
                            if (!grid.isWet(i, j)) continue;
                            hh += st.mixedLayerDepth[j * nx + i];
                            hn++;
                        }
                    }
                    System.out.println("SEASONH si=" + si + " hiLatH=" + (hn > 0 ? hh / hn : 0.0));
                }
                // ★ 2026-11-06：存下**本季**的表层 T（每年覆盖 ⇒ 最后年的值即收敛后的年循环）。
                {
                    double[] snap = new double[n2];
                    for (int j = 0; j < ny; j++)
                        for (int i = 0; i < nx; i++)
                            if (grid.isWet(i, j)) snap[j * nx + i] = st.t[st.idx(i, j, 0)];
                    if (AdvectionDiffusionSolver.ICE_TRACE) {
                        for (int jj = 1; jj < ny - 1; jj++) {
                            if (Math.abs(Math.toDegrees(grid.latAt(jj))
                                    - AdvectionDiffusionSolver.ICE_TRACE_LAT) >= 1.0) continue;
                            for (int ii = 0; ii < nx; ii++) {
                                if (!grid.isWet(ii, jj)) continue;
                                int js2 = jj * nx + ii;
                                if (st.iceThickness[js2] <= 0.0) continue;
                                System.out.println(String.format(java.util.Locale.ROOT,
                                    "ICESNAP si=%d yr=%d i=%d T0=%.3f ice=%.3f hML=%.1f",
                                    si, yr, ii, snap[js2], st.iceThickness[js2], st.mixedLayerDepth[js2]));
                            }
                        }
                    }
                    seasSurf[si] = snap;
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
            YEAR_T_MEAN.add(tMean.clone());
            double yearHeat = 0.0, yearSalt = 0.0, yearIce = 0.0;
            double yearHeatUp = 0.0, yearHeatDeep = 0.0;
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int js = j * nx + i;
                    double w = Math.max(0.05, Math.cos(grid.latAt(j)));
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double hq = tMean[q] * grid.dz[k] * w;
                        yearHeat += hq;
                        yearSalt += sMean[q] * grid.dz[k] * w;
                        if (grid.zCenter[k] < 2000.0) yearHeatUp += hq; else yearHeatDeep += hq;
                    }
                    yearIce += iceMean[js] * w;
                }
            YEAR_HEAT.add(yearHeat);
            YEAR_HEAT_UP.add(yearHeatUp);
            YEAR_HEAT_DEEP.add(yearHeatDeep);
            YEAR_SALT.add(yearSalt);
            YEAR_ICE.add(yearIce);
            maxDT = 0.0;
            double maxDS = 0.0;
            LAST_MAX_DT_Q = -1;
            if (yr > 0) {
                double[] dts = new double[n3];
                LAST_RESID = new double[n3];
                int m = 0;
                for (int j = 1; j < ny - 1; j++)
                    for (int i = 0; i < nx; i++) {
                        if (!grid.isWet(i, j)) continue;
                        for (int k = 0; k < nz; k++) {
                            int q = st.idx(i, j, k);
                            double dT = Math.abs(tMean[q] - prevT[q]);
                            dts[m++] = dT;
                            LAST_RESID[q] = dT;
                            if (dT > maxDT) { maxDT = dT; LAST_MAX_DT_Q = q; }
                            maxDS = Math.max(maxDS, Math.abs(sMean[q] - prevS[q]));
                        }
                    }
                java.util.Arrays.sort(dts, 0, m);
                YEAR_RESID_P50.add(m > 0 ? dts[(int) (0.50 * (m - 1))] : 0.0);
                YEAR_RESID_P90.add(m > 0 ? dts[(int) (0.90 * (m - 1))] : 0.0);
                YEAR_RESID_P99.add(m > 0 ? dts[(int) (0.99 * (m - 1))] : 0.0);
            }
            yearsRun = yr + 1;
            // ★ 2026-11-11：封存这一年的逐项热量账（默认关时是空操作）。
            //   必须在 yr==0 也执行，否则 1 年跑不出账本。
            DeepBudget.closeYear(grid, st);
            LAST_MAX_DT = maxDT;
            LAST_MAX_DS = maxDS;
            // ★ 2026-11-10（套件并入 DEEPGATE）：12 年 spin-up 中途不打印会被
            //   rerun_acceptance.ps1 的 1800 s 空闲看门狗判 TIMEOUT。逐年一行 ~4 分钟。
            if (SEASONAL_YEARS > 1) {
                System.out.println("YEAR_PROGRESS y=" + yearsRun
                        + " maxDT=" + maxDT + " maxDS=" + maxDS);
            }
            LAST_RESID_T = maxDT;
            LAST_RESID_S = maxDS;
            LAST_YEARS_RUN = yearsRun;
            if (yr > 0) { FP_RESID_T.add(maxDT); FP_RESID_S.add(maxDS); }
            if (FP_SOLVER_ON && yr > 0) {
                // damped fixed-point update on the annual mean
                for (int q = 0; q < n3; q++) {
                    st.t[q] = (1.0 - FP_RELAX) * prevT[q] + FP_RELAX * tMean[q];
                    st.s[q] = (1.0 - FP_RELAX) * prevS[q] + FP_RELAX * sMean[q];
                }
                System.arraycopy(st.t, 0, tMean, 0, n3);
                System.arraycopy(st.s, 0, sMean, 0, n3);
            }
            System.arraycopy(tMean, 0, prevT, 0, n3);
            System.arraycopy(sMean, 0, prevS, 0, n3);
            if (yr > 0 && maxDT < SEASONAL_CONV_K && maxDS < 0.05) break;
        }

        // 最后一年平均写入状态
        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++) {
                if (!grid.isWet(i, j)) continue;
                int js = j * nx + i;
                st.mixedLayerDepth[js] = hMean[js];
                st.eta[js] = etaMean[js];
                // ★ 2026-11-14（C1b 回滚）：状态 = 年平均（与 T/S 同口径），否则 iceFraction 与冰厚不自洽（P484 实测 FAIL）。
                //   年均不是「丢冰」：它是把耦合轨迹做年平均；真正的缺口是【融冰没记淡水 + 表面融冰没触发】（C1a/C0）。
                st.iceThickness[js] = iceMean[js];
                // ★ 2026-11-12（交接文档 §11.3 B1，用户批准）：冰分数与冰厚**统一到年平口径**。
                //   原来 :519 把 iceThickness 换成年平，iceFraction 却停在**最后一季**
                //   （它只在季节循环里被 min(1, iceThickness/H_ICE_REF) 覆写）⟹ 状态混两个时间口径，
                //   而 Atmosphere.ICE_PROVIDER 读的正是 iceFraction。
                //   实测影响（NICECAL，1 年，全湿格 6466）：只有 2 格不同（冰厚普遍 > H_ICE_REF=0.5 m
                //   ⟹ 分数早已饱和成 1）⟹ 这是口径诚实性修复，不改积分。
                if (ICE_FRACTION_FROM_MEAN) {
                    st.iceFraction[js] = Math.min(1.0, Math.max(0.0, iceMean[js])
                            / com.EyeOfHarmonyBuffer.sim.atmos.Radiation.H_ICE_REF);
                }
                for (int k = 0; k < nz; k++) {
                    int q = st.idx(i, j, k);
                    st.t[q] = tMean[q];
                    st.s[q] = sMean[q];
                }
            }
        // ★ 2026-11-02：h 用最终年平均 T/S 重诊（逐季平均的 h 与 T/S 不自洽）。
        // ★ 2026-11-11：收尾先做一次对流调整，再重诊 h（保证报出的状态静力稳定）。
        AdvectionDiffusionSolver.convectiveAdjust(grid, st);
        AdvectionDiffusionSolver.diagnoseMixedLayer(grid, st);
        // 用年平均 T/S 重算流场：先清零斜压场，再把 u/v 重置为正压基态
        java.util.Arrays.fill(st.vBaroclinic, 0.0);
        java.util.Arrays.fill(st.w, 0.0);
        baro = BarotropicSolver.update(grid, st, periodic, (nseas == 1) ? theta : 0.0, parent);
        if (AdvectionDiffusionSolver.FREEZE_SURFACE_ON) {
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!grid.isWet(i, j)) continue;
                    int q0 = st.idx(i, j, 0);
                    double tf = FreezingPoint.kelvin(st.s[q0]);
                    if (st.t[q0] < tf) st.t[q0] = tf;
                }
        }
        applyMomentum(grid, st);
        if (AdvectionDiffusionSolver.EKMAN_UPWELL_ON) {
            AdvectionDiffusionSolver.applyEkmanFlow(grid, st, periodic, 0.0);
        }
        if (parent != null) OceanSponge.apply(grid, st, parent, spongeCells, true);
        st.fillDryGhosts();

        // ★ 2026-11-04（A6）：内部冰点投影进生产路径。
        //   球面+地形生产配置实测有 19 个格点在年平均后仍低于 Tf（GATE_NOHT_T_GE_TF=FAIL）——
        //   与 A3 在稳态路径收掉的 4 格同类：PrognosticT 子步内钳制，但年平均/后续 S 更新会留下残差。
        //   投影只钉温度并记账（INTERIOR_FREEZE_ICE_ON 默认关：把亏损记成表面冰会失控，A3 已实测否决）。
        if (AdvectionDiffusionSolver.INTERIOR_FREEZE_ON) {
            AdvectionDiffusionSolver.applyInteriorFreezing(grid, st);
            st.fillDryGhosts();   // 投影改了海底格 ⟹ 干格拷贝同步，避免读到投影前的旧值
        }

        for (int j = 0; j < ny; j++)
            for (int i = 0; i < nx; i++)
                if (grid.isWet(i, j)) surfaceT[j * nx + i] = st.t[st.idx(i, j, 0)];

        // ★ 2026-11-04（A6）：键带网格身份（sphere/维度/bathyMask）——换网格不再命中旧解。
        OceanCacheKey key = OceanCacheKey.of(grid, worldSeed, landVersion, SOLVER_VERSION, season);
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
        // ★ 2026-11-04（A2）：宽度改 cos 加权 + 按【最宽盆地】取（BaroclinicFlow 里有完整说明）。
        // 同相上界见 LAST_MOC_ZONAL；盆地无关的 ψ 峰值见 LAST_PSI_MAX。
        double mocSv = R3Momentum.ON ? R3Momentum.LAST_MOC_SV : BaroclinicFlow.LAST_MOC;
        return new OceanSolution(key, st, surfaceT,
            baro.iterations, adv == null ? 0 : adv.iterations,
            baro.residual, adv == null ? 0.0 : adv.residual,
            psiMin, psiMax, mocSv,
            sBudget, sBudget, seasSurf);
    }

    /** 定位第一个非有限格（DEBUG_FINITE 用；默认路径零开销）。 */
    private static String firstNonFinite(OceanGrid g, OceanState st) {
        for (int j = 1; j < g.ny - 1; j++)
            for (int i = 0; i < g.nx; i++) {
                if (!g.isWet(i, j)) continue;
                int wl = g.wetLayers[g.idx(i, j)];
                for (int k = 0; k < wl; k++) {
                    int q = st.idx(i, j, k);
                    if (!Double.isFinite(st.t[q]) || !Double.isFinite(st.s[q])
                            || !Double.isFinite(st.u[q]) || !Double.isFinite(st.v[q])
                            || !Double.isFinite(st.w[q]))
                        return "i=" + i + " j=" + j + " k=" + k + " wl=" + wl
                                + " t=" + st.t[q] + " s=" + st.s[q]
                                + " u=" + st.u[q] + " v=" + st.v[q] + " w=" + st.w[q]
                                + " h=" + st.mixedLayerDepth[j * g.nx + i];
                }
                int js = j * g.nx + i;
                if (!Double.isFinite(st.mixedLayerDepth[js]) || !Double.isFinite(st.iceThickness[js])
                        || !Double.isFinite(st.eta[js]))
                    return "i=" + i + " j=" + j + " surface h=" + st.mixedLayerDepth[js]
                            + " ice=" + st.iceThickness[js] + " eta=" + st.eta[js];
            }
        return null;
    }
}
