package com.EyeOfHarmonyBuffer.sim.atmos;

/**
 * S1a —— **辐射闭合**（设计冻结 §358 / §360，实测见 P554）。
 *
 * <p>存在理由：{@code src} 里原本**没有**太阳辐射、没有反照率、没有 OLR
 * （§348 结论四：grep 0 命中）。后果是地表温度是**诊断**给的、没有蒸发冷却，
 * 于是 Manabe eq.19 的潜在蒸发 {@code rho*C_D*|V|*(q_sat(T_s)-q)} 虚高
 * （P554 实测亚洲 **17.03 mm/day**，而能量限制值应为 ~7），
 * 使陆地桶在季风区也会把地榨干（§357）。
 *
 * <p>闭合（陆地 {@code G = 0}，Manabe 1969 eq.16 的先例）：
 * <pre>
 *   (1 - alpha_s) * S  =  eps*sigma*T_s^4 + H + LE
 *   H  = rho_a * c_p * C_H * |V| * (T_s - T_a)
 *   LE = rho_a * L_v * C_E * |V| * max(0, q_sat(T_s) - q_a)
 * </pre>
 *
 * <p><b>系数取法（声明，不新造数）</b>：{@code C_H = C_E = Atmosphere.cdOf(kappa)}
 * —— §354 把 {@code C_H}/{@code C_E} 的具体值列为**第③层未核验**，
 * 所以复用模型自己的拖曳系数，而不是从文献里抄一组无法核验的数。
 *
 * <p><b>OLR 取 {@code eps*sigma*T^4} 而不是 {@code A + B*T}</b>：§354 已证各来源的 A/B
 * 不一致且依赖参考态；{@code eps*sigma*T^4} 只需**一个**有效发射率，
 * 且直接由 Trenberth et al. (2009) 的观测 OLR 238.5 W/m^2 @ 288.15 K **定标**。
 */
public final class Radiation {

    private Radiation() {}

    /** 太阳常数（W/m^2）。 */
    public static final double S0 = 1361.0;
    public static final double SIGMA = 5.670374419e-8;
    public static final double CP = 1004.0;
    public static final double LV = 2.45e6;
    /** 有效发射率：238.5 / (sigma * 288.15^4)。 */
    public static final double EPS = 238.5 / (SIGMA * Math.pow(288.15, 4));
    public static final double ALB_SEA = 0.06;
    public static final double ALB_LAND = 0.20;
    public static final double ALB_SNOW = 0.65;
    /** 雪面判据（与 SimTerrain 的雪线同值）。 */
    public static final double T_SNOW_K = 273.15;

    /**
     * ★ S1a 接线开关（§358/§360/§368/§385）：把 {@code SimClimate} 里【诊断】的陆地皮温
     * 换成【表面能量平衡解出】的皮温。**默认 false ⇒ 逐位不变。**
     *
     * <p>为什么需要它：{@code P568}（§385）实测陆地上感热 {@code H} 是【负】的
     * （亚洲 −74.8、撒哈拉 −29.7 W/m²）—— 根因是诊断温度 {@code T_a} 没有蒸发冷却、偏高。
     * 而 {@code H} 的符号是 S2 的强迫场 {@code Q} 的前提。
     *
     * <p>⚠ 本开关改变气候输出 ⇒ **必须折进 {@code SimClimate.configStamp()}**（已折）。
     */
    public static boolean SKIN_TEMP_FROM_ENERGY_BALANCE = false;

    /** 日平均 TOA 日照（W/m^2）。{@code phi} 与 {@code dec} 为弧度。 */
    public static double insolation(double phi, double dec) {
        double x = -Math.tan(phi) * Math.tan(dec);
        double h0 = (x <= -1.0) ? Math.PI : (x >= 1.0 ? 0.0 : Math.acos(x));
        return (S0 / Math.PI) * (h0 * Math.sin(phi) * Math.sin(dec)
               + Math.cos(phi) * Math.cos(dec) * Math.sin(h0));
    }

    /**
     * ★★★ **§441：陆地反照率的加性偏移**（默认 **0.0** ⇒ 逐位不变）。
     *
     * <p>这是**植被状态量将来要驱动的那个量**：裸地反照率高、植被低。
     * 先把它做成一个可扫描的旋钮，用来回答 V′ 的 go/no-go ——
     * **Charney (1975) 型「植被 ⇒ 反照率 ⇒ 加热 ⇒ 环流 ⇒ 降水」的正反馈，在模型里到底闭不闭得上。**
     *
     * <p>为什么必须走反照率这条路（而不是水分侧）：§440 已实测，任何**全局均匀**的
     * 水汽侧干预（表面阻力）都只放大「撒哈拉更湿」这个错误排序。
     * 而反照率影响的是 `netColumnHeating` 的**感热与辐射**两项 ⇒ 改变 **S2 定常波的强迫** ⇒
     * 而 §396 已经证明：**强迫取对时，求解器给出的正是「亚洲辐合 / 撒哈拉辐散」。**
     */
    public static double ALB_LAND_ADD = 0.0;

    /** 地表反照率。 */
    public static double albedo(boolean isLand, double tSurfK) {
        if (!isLand) return ALB_SEA;
        return tSurfK < T_SNOW_K ? ALB_SNOW : ALB_LAND + ALB_LAND_ADD;
    }

    /** 体块通量系数 {@code rho_a * C_D(kappa) * |V|}（W/(m^2 K) 或 kg/(m^2 s) 视用途）。 */
    public static double bulkCoeff(double kappa, double windSpeed) {
        return Atmosphere.RHO_AIR * Atmosphere.cdOf(kappa) * windSpeed;
    }

    /** 表面能量平衡残差（W/m^2），`beta = 1`（饱和表面）。**对 T_s 单调递减** ⇒ 二分法唯一根。 */
    public static double residual(double ts, double absSolar, double ta, double qa, double chv) {
        return residual(ts, absSolar, ta, qa, chv, 1.0);
    }

    /**
     * 表面能量平衡残差（W/m^2），带**地表湿润度 `beta`**（§387 定案）。
     *
     * <pre>
     *   E = beta * rho*C_D*|V| * (q_sat(Ts) - q_a)      <- Manabe：beta 乘在【通量】上
     * </pre>
     * **`beta` 才是「沙漠的干」的正确表达** —— 不是低 `q_a`。
     * （§387 的 `P569` 实测：降低 `q_a` 反而【增大】蒸发 ⇒ `H` 更负。因为干燥空气吹过湿表面蒸发更多。）
     * `beta -> 0` ⇒ 没有潜热冷却 ⇒ 皮温升高 ⇒ **`H` 翻正**（`P570` 实测在 `beta ~ 0.3` 处翻正）。
     */
    public static double residual(double ts, double absSolar, double ta, double qa, double chv, double beta) {
        double olr = EPS * SIGMA * ts * ts * ts * ts;
        double h = chv * CP * (ts - ta);
        double le = beta * chv * LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
        return absSolar - olr - h - le;
    }

    /** 解陆地皮温（K），`beta = 1`。二分，60 次足够 double 收敛。 */
    public static double skinTempLand(double absSolar, double ta, double qa, double chv) {
        return skinTempLand(absSolar, ta, qa, chv, 1.0);
    }

    /** 解陆地皮温（K），带地表湿润度 `beta`。上界抬到 360 K（干沙漠皮温会接近 320 K）。 */
    public static double skinTempLand(double absSolar, double ta, double qa, double chv, double beta) {
        double lo = 180.0, hi = 360.0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            if (residual(mid, absSolar, ta, qa, chv, beta) > 0.0) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    /**
     * ★ 桶开关（§387/§354）：用【稳态互补解】给出 `beta`，而不是全场 `beta = 1`。
     *
     * <pre>
     *   E_p  = beta=1 时的潜在蒸发（Manabe eq.19）
     *   E    = min(E_p, P)                <- 稳态互补（dW/dt = 0）
     *   W    = W_K * min(1, P/E_p)        <- W_K = 0.75 * W_FC  （§354 对教科书的更正）
     *   beta = E/E_p = min(1, P/E_p)
     * </pre>
     * **默认 false ⇒ 逐位不变。**
     */
    public static boolean BUCKET_BETA = false;

    /** Manabe eq.20 的 `W_K / W_FC`。教科书写 1.0 是**错的**（§354 从原文 PDF 核出）。 */
    public static final double WK_OVER_WFC = 0.75;

    /** 稳态互补解给出的地表湿润度 `beta = min(1, P/E_p)`（`E_p` 与 `P` 同单位即可）。 */
    public static double bucketBeta(double p, double ep) {
        if (ep <= 1.0e-12) return 1.0;
        double b = p / ep;
        return b < 0.0 ? 0.0 : (b > 1.0 ? 1.0 : b);
    }

    /** 潜在蒸发（mm/day）：Manabe eq.19 在【解出的皮温】上求值 —— 这就是「能量限制」。 */
    public static double potentialEvapMmDay(double ts, double qa, double chv) {
        return chv * Math.max(0.0, PrecipField.qSat(ts) - qa) / PrecipField.RHO_WATER * 86400.0 * 1000.0;
    }
}
