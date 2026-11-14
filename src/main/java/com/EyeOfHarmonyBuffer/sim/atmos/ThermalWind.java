package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-10-20（u 阶段）：**用本世界自己的 T 场算纬向平均风**，替掉观测 U_ZM 表。
 *
 * <h3>物理链（全部有出处，无自造参数）</h3>
 * <pre>
 *   1) 热成风：∂u/∂z = -(g/(f T)) ∂T/∂y            （与 PrecipField.eadyGrowth 同一式）
 *      u_jet = dudz * H_LAYER，H_LAYER = 850-200 hPa 厚度 = 8000 m
 *   2) 对数廓线：u(z) = (u_star/kappa) ln(z/z0)      （von Karman kappa=0.41）
 *   3) 拖曳定律：G/u_star = (1/kappa) sqrt[(ln(u_star/(f z0)) - A)^2 + B^2]
 *      （Lettau 1959；Garratt 1992 "The Atmospheric Boundary Layer"）
 *   4) Charnock：z0 = alpha u_star^2 / g             （Charnock 1955；海面 alpha=0.0185）
 *   ⟹ 10 m 风 u10 = (u_star/kappa) ln(10/z0)，风应力 tau = rho_a u_star^2
 * </pre>
 *
 * <p>校验（NTDRAG）：G=12 m/s ⟹ u_star=0.308、u10=8.2、tau=0.116 Pa —— 与物理海面一致。
 * <p>注意：赤道 f→0 时热成风奇异；本类只负责中纬（|lat|>=15°），赤道段留给 Held–Hou。
 */
public final class ThermalWind {

    private ThermalWind() {}

    /** von Karman 常数。 */
    public static final double KAPPA = 0.41;
    /** Lettau 拖曳定律常数 A（Lettau 1959；Garratt 1992）。 */
    public static final double A_DRAG = 1.3;
    /** Lettau 拖曳定律常数 B。 */
    public static final double B_DRAG = 4.4;
    /** Charnock 系数（海面）。 */
    public static final double ALPHA_CHARNOCK = 0.0185;
    /** 850-200 hPa 厚度（m）。 */
    public static final double H_LAYER = 8000.0;

    /** 热成风急流（m/s，正=向东）；用 t850 的经向梯度。 */
    public static double jet(double latRad, double theta) {
        double f = WorldContract.coriolis(latRad);
        if (Math.abs(f) < 1e-6) return Double.NaN;
        double t850 = ZonalTables.t850Month(latRad, theta);
        double dTdy = PrecipField.t850Slope(latRad, theta);
        double dudz = -(PrecipField.G_ACC / (f * t850)) * dTdy;
        return dudz * H_LAYER;
    }

    /**
     * ★ 标定系数（**不是物理常数**）：涡动饱和效率。
     * <p>Vallis (2006, ch.2) 的 v'ψ ~ (L_e/L_d)·u 是“平均 APE 全部转成 EKE”的**上界**；
     * 真实大气的饱和效率是 O(0.01–0.1) 的标定值。本值按“中纬年平 τ≈0.1 Pa”标定，
     * 并用 NEDDYW 复核。禁用时逐位不变。
     */
    public static double EDDY_SAT = 0.05;

    /**
     * ★ 2026-10-20（EDDY_WIND）：天气尺度涡动地面风/风应力（含**显式登记的标定**）。
     * <pre>
     *   v'    = EDDY_SAT · σ · L_R / 0.31   （Vallis 2.51 + Eady σ=EADY_COEF·f·|du/dz|/N）
     *   EKE   = 0.5 v'^2                    （动能定义）
     *   [M_y] = 0.5 (p_s/g) EKE / L_R       （Schneider & Walker 2008 式10 的 0.5 × 质量加权积分）
     *   u*    = sqrt([M_y]/(ρ_a C_D))       （Held & Hoskins 1985 拖曳平衡）
     *   τ     = ρ_a u*^2；u10 由 Charnock + 对数廓线
     * </pre>
     * @return {u_star, u10, tau, M_y}（m/s, m/s, Pa, Pa）
     */
    public static double[] eddyWind(double latRad, double theta) {
        double sigma = PrecipField.eadyGrowth(latRad, theta);      // 1/s
        double ld = Math.max(1.0, PrecipField.deformRadius(latRad, theta)); // m
        double vPrime = EDDY_SAT * sigma * ld / PrecipField.EADY_COEF;      // m/s
        double eke = 0.5 * vPrime * vPrime;                        // m^2/s^2
        double mInt = 0.5 * (PrecipField.P_SURF / 9.81) * eke / ld; // Pa
        double tau = mInt;                                        // 拖曳平衡：τ = [M_y]
        double us = Math.sqrt(Math.max(0.0, tau) / Atmosphere.RHO_AIR); // τ = ρ u*^2
        double z0 = Math.max(1e-6, ALPHA_CHARNOCK * us * us / 9.81);
        double u10 = (us / KAPPA) * Math.log(10.0 / z0);
        return new double[]{us, u10, tau, mInt};
    }

    /**
     * 由自由大气风 G 解出 {u10, tau}（m/s, Pa）。迭代拖曳定律 + Charnock。
     * @param f 科氏参数（带符号，|f| 用于对数）
     */
    public static double[] u10Tau(double gWind, double f) {
        double af = Math.max(1e-6, Math.abs(f));
        double us = 0.03 * Math.abs(gWind);
        for (int it = 0; it < 100; it++) {
            double z0 = Math.max(1e-6, ALPHA_CHARNOCK * us * us / 9.81);
            double L = Math.log(us / (af * z0));
            us = KAPPA * Math.abs(gWind) / Math.sqrt((L - A_DRAG) * (L - A_DRAG) + B_DRAG * B_DRAG);
        }
        double z0 = Math.max(1e-6, ALPHA_CHARNOCK * us * us / 9.81);
        double u10 = (us / KAPPA) * Math.log(10.0 / z0);
        double tau = Atmosphere.RHO_AIR * us * us;
        return new double[]{u10, tau};
    }
}
