package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * UNESCO 1983 (EOS-80) 海水密度：一个大气压下的多项式（Fofonoff &amp; Millard 1983）。
 *
 * <p>为什么用它：现在全仓的密度都是**线性** EOS（{@code Thermohaline.ALPHA/BETA}），
 * 在 0–30 °C、30–38 psu 范围内 alpha/beta 变化可达 2–3 倍。EOS-80 是标准式、
 * 系数全部来自文献，不是拟合参数。
 *
 * <p>为什么只做 p=0：R3 的热成风/地转只用**等 z 面的水平密度梯度**，
 * 压强项在水平差分里是二阶小量（同一深度上 p 近似相同）；用**位密度**是海洋模式的
 * 标准做法。等 TEOS-10 的 gsw 移植进来后，本类整体替换即可。
 *
 * <p>系数出处：UNESCO Technical Papers in Marine Science 44 (1983), 附录；
 * 也见 Fofonoff &amp; Millard, "Algorithms for computation of fundamental properties
 * of seawater", 1983, §2。
 *
 * <p>适用域：0 &lt;= S &lt;= 40 psu，0 &lt;= T &lt;= 40 °C。超出域仍返回有限值（多项式连续），
 * 但精度不保证 —— 生产 T/S 在域内。
 */
public final class Eos80 {
    private Eos80() {}

    /** 纯水项（S=0）系数，T 单位 °C。 */
    private static final double[] A = {
        999.842594, 6.793952e-2, -9.095290e-3, 1.001685e-4, -1.120083e-6, 6.536332e-9
    };
    /** 一次盐度项系数。 */
    private static final double[] B = {
        0.824493, -4.0899e-3, 7.6438e-5, -8.2467e-7, 5.3875e-9
    };
    /** S^(3/2) 项系数。 */
    private static final double[] C = {-5.72466e-3, 1.0227e-4, -1.6546e-6};
    /** S^2 项系数。 */
    private static final double D = 4.8314e-4;

    /**
     * 一个大气压下的密度 [kg/m3]。
     * @param s 实用盐度 [psu]
     * @param tC 温度 [°C]
     */
    public static double density0(double s, double tC) {
        double rho = A[0] + tC * (A[1] + tC * (A[2] + tC * (A[3] + tC * (A[4] + tC * A[5]))));
        double s32 = s * Math.sqrt(Math.max(0.0, s));
        rho += s * (B[0] + tC * (B[1] + tC * (B[2] + tC * (B[3] + tC * B[4]))));
        rho += s32 * (C[0] + tC * (C[1] + tC * C[2]));
        rho += D * s * s;
        return rho;
    }

    /** 位密度 [kg/m3]（T 用 K）。 */
    public static double density0K(double s, double tK) {
        return density0(s, tK - 273.15);
    }

    /** d(rho)/dT [kg/m3/K]，T 单位 °C（多项式解析导数）。 */
    public static double dRhoDT(double s, double tC) {
        double d = A[1] + tC * (2.0 * A[2] + tC * (3.0 * A[3] + tC * (4.0 * A[4] + tC * 5.0 * A[5])));
        d += s * (B[1] + tC * (2.0 * B[2] + tC * (3.0 * B[3] + tC * 4.0 * B[4])));
        d += s * Math.sqrt(Math.max(0.0, s)) * (C[1] + tC * 2.0 * C[2]);
        return d;
    }

    /** d(rho)/dS [kg/m3/psu]（多项式解析导数）。 */
    public static double dRhoDS(double s, double tC) {
        double d = B[0] + tC * (B[1] + tC * (B[2] + tC * (B[3] + tC * B[4])));
        double sq = Math.sqrt(Math.max(0.0, s));
        // d(S^1.5)/dS = 1.5*sqrt(S)
        d += 1.5 * sq * (C[0] + tC * (C[1] + tC * C[2]));
        d += 2.0 * D * s;
        return d;
    }
}
