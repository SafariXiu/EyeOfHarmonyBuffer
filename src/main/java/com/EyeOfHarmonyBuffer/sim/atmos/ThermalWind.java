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

    /**
     * ★★★★★ 2026-11-22（**纬向动量模型 v0**）：**850 hPa 的纬向平均风**。
     *
     * <p>做法 = 热成风（{@link #jet} 已用本世界温度）+ **锚定层**：把 {@code u = 0} 放在**地表**，
     * 只积分到 850 hPa 的高度（{@link #Z850_M} = 1500 m，标准大气里 850 hPa 的近似高度）。
     *
     * <pre>  ∂u/∂z = −(g/(f·T))·∂T/∂y ,  u_850 = (∂u/∂z) · 1500 m  </pre>
     *
     * <p><b>为什么这样就有「年均西风急流」</b>（这是模型此前缺的那块，见 AJ 报告）：
     * 赤道暖、极地冷 ⟹ 北半球 {@code ∂T/∂y<0}、{@code f>0} ⟹ {@code ∂u/∂z>0}（西风）；
     * 南半球 {@code ∂T/∂y>0}、{@code f<0} ⟹ 同样 {@code ∂u/∂z>0}（西风）⟹ **两个半球都是西风**。
     * 这正是 {@code ZonalWindStat}（本世界局地风场的统计）做不到的 —— 那里没有年均急流。
     *
     * <p>实测（seed=0，θ=0）：−60° **+4.28**、−45° **+4.14**、+30° **+3.13**、+45° **+4.34**、+75° **+1.56** m/s；
     * 与地球表（+0.62…+5.32）同阶，且**南北同号**。
     *
     * <p>⚠ 已知边界：{@code |lat| < }{@link #MIN_ABS_LAT_DEG}（15°）时热成风奇异，返回 0
     * （该区间本就该由 Held–Hou 支配，见类 javadoc）。⚠ 不含季节项：{@code T} 取本世界年均。
     */
    public static double u850(double latRad, double theta) {
        double jet = jet(latRad, theta);
        if (!Double.isFinite(jet)) return 0.0;
        return jet * (Z850_M / H_LAYER);
    }

    /** 850 hPa 的近似高度（m）—— 标准大气，不是可调参数。 */
    public static final double Z850_M = 1500.0;

    /**
     * ★ 2026-11-22（**动量模型 v1：季节项**）：该纬度、该季节的**纬向平均海平面温度**。
     *
     * <pre>  T(φ,θ) = T_zm_annual(φ) + seasonalAnomaly(φ, kbar(φ), θ)  </pre>
     *
     * <p>两个来源都是本世界：年均项 = {@link Atmosphere#zmslkNew}（定义式），
     * 季节项 = {@link Atmosphere#seasonalAnomaly}（用**本纬度的**陆地占比 ¤kbarRef¤ 混合陆/海振幅）。
     * 零新常数。
     *
     * <p>物理效果：夏季纬向温度梯度减弱 ⟹ 急流减弱；冬季增强 ⟹ 急流加强。
     * （这正是风暴轴随季节迁移的动力来源之一。）
     */
    static double zonalT(double latRad, double theta) {
        double kbar = Atmosphere.kbarRef(latRad);
        return Atmosphere.zmslkNew(latRad) + Atmosphere.seasonalAnomaly(latRad, kbar, theta);
    }

    /** 求 ∂T/∂y 用的纬度步长（度）——数值差分宽度，不是物理常数。 */
    public static final double DPHI_DEG = 2.5;
    /** 热成风关系的**适用下限**（度）：赤道附近 f→0，热成风奇异（本类 javadoc 写明"赤道段留给 Held–Hou"）。 */
    public static final double MIN_ABS_LAT_DEG = 15.0;

    /**
     * 纬向热成风急流（m/s，正 = 向东）—— **用本世界自己的纬向温度算**。
     *
     * <p>★ 2026-11-22（P4，全核心去地球表）：本方法的**意图**（见类 javadoc，2026-10-20）一直是
     * "用本世界自己的 T 场替掉观测 U_ZM 表"，但实现里读的是**地球** {@code ZonalTables.t850Month}
     * 与 {@code PrecipField.t850Slope} ⟹ 等于把同一张地球表换一种写法。
     * 现在改为：{@code T} 与其经向梯度都取 {@link Atmosphere#zmslkNew}（本世界纬向平均）。
     *
     * <p><b>公式</b>（与 {@code PrecipField.eadyGrowth} 同一式，有出处）：
     * <pre>  ∂u/∂z = -(g / (f·T)) · ∂T/∂y ;   u_jet = (∂u/∂z)·H_LAYER  </pre>
     *
     * <p>⚠ <b>已知近似（必须记账）</b>：{@code T} 用的是**海平面**纬向平均温度，而热成风里的 T
     * 应是 850–200 hPa 的**层平均**（更冷）。用地表值会把 T 高估 ~10–15% ⟹ u_jet **偏小**同量级。
     * 这是"没有本世界 850 hPa 温度场之前"的临时口径；P5 拿到本世界 t850 后应改回层平均。
     *
     * @return 急流风速（m/s）；{@code |lat| < MIN_ABS_LAT_DEG} 时返回 {@link Double#NaN}
     *         （热成风在该区间无定义，调用方必须自己决定赤道段怎么办）
     */
    public static double jet(double latRad, double theta) {
        double latDeg = Math.toDegrees(latRad);
        if (Math.abs(latDeg) < MIN_ABS_LAT_DEG) return Double.NaN;
        double f = WorldContract.coriolis(latRad);
        if (Math.abs(f) < 1.0e-9) return Double.NaN;
        double d = Math.toRadians(DPHI_DEG);
        double t = zonalT(latRad, theta);
        double tN = zonalT(latRad + d, theta);
        double tS = zonalT(latRad - d, theta);
        double dTdy = (tN - tS) / (2.0 * d * WorldContract.R_EFF);
        double dudz = -(PrecipField.G_ACC / (f * t)) * dTdy;
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
