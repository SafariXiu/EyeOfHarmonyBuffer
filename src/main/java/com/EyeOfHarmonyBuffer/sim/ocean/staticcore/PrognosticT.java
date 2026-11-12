package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;

/**
 * ★ 2026-11-03（stage 4）：T 的季节内 prognostic flux-form 输运。
 * <pre>
 *   dT/dt = -div(uT) + KH ∇²_h T + KV ∂²T/∂z² + Q_sw
 * </pre>
 * 表面 k=0 作为 Dirichlet 边界（surfaceT，由 ML/表面通量步给出）。
 * 面通量用已验证的 divFlux（Σ=0）；30×1 天显式步。
 */
public final class PrognosticT {
    public static double LAST_MAX_DT = 0.0;
    /** 本次实际用的子步数（CFL 自适应）。 */
    public static int LAST_STEPS = 0;
    /** 累计的冰点钳制温差（诊断，K·格点）。 */
    public static double LAST_DEFICIT = 0.0;

    /**
     * ★ 2026-11-04（A3）：内部冰点【记账】。默认 false ⟹ 与 2026-11-03 的纯钳制逐位相同。
     *
     * <p>钳制 T→Tf(S) 本身是"凭空加热"：真实海水靠**结冰释放的潜热**补上这份热量。
     * 打开后按
     * <pre>  dm = ρ0 cp (Tf − T) / L_f   [kg/m³]   ⟹   dhi = dm·dz/ρ_ice   [m]（按 1 m² 面积）</pre>
     * 把冰计入 {@code state.iceThickness}（frazil 上浮到表层），并累计潜热。
     * 于是「钳制升高的温度」与「生成冰的潜热」是同一笔账（{@link #LAST_LATENT_J}）。
     *
     * <p>常数（P0，外部出处）：
     * <ul>
     *   <li>{@link #L_FUSION} = 3.3355e5 J/kg —— 水在 0 °C 的熔化潜热
     *       （CRC Handbook of Chemistry and Physics, 94th ed., 6-1；IAPWS-95 同值）；</li>
     *   <li>{@link #RHO_ICE} = 916.8 kg/m³ —— 0 °C 冰的密度（CRC 94th, 6-2）。</li>
     * </ul>
     *
     * <p><b>明写的边界：不做盐析</b>（冰视为"留住自己的盐"）。盐析需要一个冰盐度参数，
     * 按铁律②（不自造常数）不造；生产路径的表面 brine 走 {@code OceanState.iceSaltFlux}，
     * 与本项是两条独立通道。
     */
    public static boolean FREEZE_LEDGER_ON = false;
    public static final double L_FUSION = 3.3355e5;
    public static final double RHO_ICE = 916.8;
    /** 累计内部结冰的等效厚度 [m]（对各格点求和，**不是**面积平均）。 */
    public static double LAST_ICE_MADE = 0.0;
    /** 累计释放的潜热 [J/m²·格点]。 */
    public static double LAST_LATENT_J = 0.0;
    /** 触发过内部结冰的格点次数。 */
    public static int LAST_ICE_CELLS = 0;

    private PrognosticT() {}

    /** 诊断：打印该格点第一次出现 NaN 时的各项（默认 -1 关）。 */
    public static int DBG_I = -1, DBG_J = -1, DBG_K = -1;

    public static double run(OceanGrid g, OceanState st, double[] surfaceT, double[] src,
                             double seasonSeconds, int substeps) {
        int nx = g.nx, ny = g.ny, nz = g.nz, n3 = nx * ny * nz;
        // ★ 2026-11-07（R3）：显式通量平流必须满足 CFL；w 来自连续方程时尤其关键。
        // ★ 只在 R3 路径上自适应：v1 fallback 必须逐位不变（v1 的 CFL 本来就在 0.2 以内，
        //   但 need=31>30 会把 30 子步抬到 31，改变 v1 的解 —— 不能用）。
        // 2026-11-11: LOCAL_V_ON produces a genuinely 3D flow whose vertical velocity can violate
        // the explicit CFL at the default substep count (measured 1.577 > 1). Adapt whenever the
        // LOCAL velocity is on; the default path stays bit-identical.
        int steps = (R3Momentum.ON || BaroclinicFlow.LOCAL_V_ON)
                ? AdvectionDiffusionSolver.cflSteps(g, st, seasonSeconds, substeps)
                : Math.max(1, substeps);
        LAST_STEPS = steps;
        double dt = seasonSeconds / steps;
        // ★ 2026-11-11（深层收支仪器）：记下本次的显式 CFL 数；>1 表示格式本身不稳定。
        if (DeepBudget.ON) {
            double cfl = AdvectionDiffusionSolver.cflRate(g, st) * dt;
            if (cfl > DeepBudget.LAST_CFL) {
                DeepBudget.LAST_CFL = cfl;
            }
        }
        double[] adv = new double[n3];
        double[] tn = new double[n3];
        // Redi isopycnal diffusion (computed once per call: operator split).
        double[] redi = new double[n3];
        if (RediDiffusion.ON) RediDiffusion.tendency(g, st, st.t, redi);
        double maxd = 0.0;
        for (int step = 0; step < steps; step++) {
            st.fillDryGhosts();
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        double d = AdvectionDiffusionSolver.divFlux(g, st, st.t, i, j, k);
                        if (AdvectionDiffusionSolver.ADVECTIVE_FORM) {
                            d -= st.t[q] * AdvectionDiffusionSolver.divU(g, st, i, j, k);
                        }
                        adv[q] = d;
                    }
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    int js = j * nx + i;
                    // ★ 2026-11-11（深层收支仪器）：每列算一次纬度带与面积权，默认关时零成本。
                    final int bandJ = DeepBudget.ON ? DeepBudget.band(g, j) : 0;
                    final double cosJ = DeepBudget.ON ? Math.max(0.05, g.cosLat(j)) : 1.0;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        if (k == 0) { tn[q] = surfaceT[js]; continue; }
                        double t = st.t[q], dz = g.dz[k];
                        double tw = g.isWet(i - 1, j, k) ? st.t[st.idx(i - 1, j, k)] : t;
                        double te = g.isWet(i + 1, j, k) ? st.t[st.idx(i + 1, j, k)] : t;
                        double ts = g.isWet(i, j - 1, k) ? st.t[st.idx(i, j - 1, k)] : t;
                        double tnn = g.isWet(i, j + 1, k) ? st.t[st.idx(i, j + 1, k)] : t;
                        double tup = st.t[st.idx(i, j, k - 1)];
                        // A3: below the seabed -> zero gradient (dry cells do not participate).
                        double tdn = (k + 1 < g.wetLayers[g.idx(i, j)]) ? st.t[st.idx(i, j, k + 1)] : t;
                        double lapH = AdvectionDiffusionSolver.khAt(g, k)
                                * ((tw + te - 2.0 * t) / (g.dx * g.dx)
                                 + (ts + tnn - 2.0 * t) / (g.dy * g.dy));
                        double cv = AdvectionDiffusionSolver.kvAt(g, st, i, j, k) / (dz * dz);
                        double lapZ = cv * (tup + tdn - 2.0 * t);
                        double v = t + dt * (-adv[q] + lapH + lapZ + (src == null ? 0.0 : src[q]) + redi[q]);
                        if (i == DBG_I && j == DBG_J && k == DBG_K && !Double.isFinite(v)) {
                            int q0 = st.idx(i, j, 0);
                            System.out.println(String.format(java.util.Locale.ROOT,
                                "DBG_T step=%d t=%.4f adv=%.3e lapH=%.3e lapZ=%.3e cv=%.3e src=%.3e redi=%.3e dt=%.3e s0=%.4f h=%.2f k0t=%.4f",
                                step, t, adv[q], lapH, lapZ, cv,
                                src == null ? 0.0 : src[q], redi[q], dt,
                                st.s[q0], st.mixedLayerDepth[j * g.nx + i], st.t[q0]));
                            System.out.println(String.format(java.util.Locale.ROOT,
                                "DBG_FACES ready=%s uF=%.3e uW=%.3e vF=%.3e vS=%.3e wF=%.3e wTop=%.3e",
                                VelocityProjection.FACES_READY,
                                st.uF[q], st.uF[st.idx(i - 1, j, k)],
                                st.vF[q], st.vF[st.idx(i, j - 1, k)],
                                st.wF[q], k > 0 ? st.wF[st.idx(i, j, k - 1)] : 0.0));
                        }
                        // ★ 2026-11-11（深层收支仪器）：逐项记账（只读，不改 v）。默认关。
                        //   只记【湿层】：海底以下的干格是 ghost，没有对应的体积权重，
                        //   记进去会让账本不闭合（实测极带虚高 +3 K/yr 就是这么来的）。
                        if (DeepBudget.ON && k < g.wetLayers[g.idx(i, j)]) {
                            double wv = dt * dz * cosJ;
                            DeepBudget.add(bandJ, DeepBudget.T_ADV, k, -adv[q] * wv);
                            DeepBudget.add(bandJ, DeepBudget.T_ADV_V, k,
                                    -AdvectionDiffusionSolver.advVerticalPart(g, st, st.t, i, j, k) * wv);
                            DeepBudget.add(bandJ, DeepBudget.T_DIFF_H, k, lapH * wv);
                            DeepBudget.add(bandJ, DeepBudget.T_DIFF_V, k, lapZ * wv);
                            DeepBudget.add(bandJ, DeepBudget.T_SRC, k, (src == null ? 0.0 : src[q]) * wv);
                            DeepBudget.add(bandJ, DeepBudget.T_REDI, k, redi[q] * wv);
                        }
                        // ★ 2026-11-09（阶段 2）：max|ΔT| 只统计平流/扩散变化，
                        //   不把冻结钳制算进去——否则物理约束会触发数值兜底（已删）。
                        double dAdv = Math.abs(v - t);
                        // ★ 2026-11-03（stage 3 接入 progn T）：T≥Tf(S)，子步内钳制。
                        // ★ 2026-11-04（A3）：钳制同时做【潜热记账】（{@link #FREEZE_LEDGER_ON}）。
                        double tf = FreezingPoint.kelvin(st.s[q]);
                        if (v < tf) {
                            double deficit = tf - v;
                            LAST_DEFICIT += deficit;
                            if (DeepBudget.ON && k < g.wetLayers[g.idx(i, j)])
                                DeepBudget.add(bandJ, DeepBudget.T_FRZ_LOOP, k, deficit * dz * cosJ);
                            if (FREEZE_LEDGER_ON) {
                                double massIce = AdvectionDiffusionSolver.RHO0
                                        * AdvectionDiffusionSolver.CP_SEA * deficit / L_FUSION;   // kg/m³
                                double dhi = massIce * dz / RHO_ICE;                              // m
                                st.iceThickness[js] += dhi;
                                LAST_ICE_MADE += dhi;
                                LAST_LATENT_J += AdvectionDiffusionSolver.RHO0
                                        * AdvectionDiffusionSolver.CP_SEA * deficit * dz;
                                LAST_ICE_CELLS++;
                            }
                            v = tf;
                        }
                        tn[q] = v;
                        maxd = Math.max(maxd, dAdv);
                    }
                }
            for (int j = 1; j < ny - 1; j++)
                for (int i = 0; i < nx; i++) {
                    if (!g.isWet(i, j)) continue;
                    for (int k = 0; k < nz; k++) {
                        int q = st.idx(i, j, k);
                        st.t[q] = tn[q];
                    }
                }
        }
        LAST_MAX_DT = maxd;
        return maxd;
    }
}
