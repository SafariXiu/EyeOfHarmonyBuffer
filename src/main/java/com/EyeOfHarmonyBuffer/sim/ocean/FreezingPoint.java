package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * 海水冰点：由盐度计算，而不是固定 271.35 K。
 *
 * <p>UNESCO(1983) 常压冰点近似：
 * Tf(degC) = -0.0575*S + 1.710523e-3*S^1.5 - 2.154996e-4*S^2
 * 其中 S 为实用盐度 psu。压力项在表层取 0。
 *
 * <p>这是海水热力学关系，不是调参，也不是某个官方算例的常数。
 */
public final class FreezingPoint {

    /** 当前参考盐度（provisional；不是物理推导值）。 */
    public static final double S_REFERENCE = 35.0;

    private FreezingPoint() {}

    public static double celsius(double salinityPsu) {
        double s = Math.max(0.0, salinityPsu);
        return -0.0575 * s
             + 1.710523e-3 * Math.pow(s, 1.5)
             - 2.154996e-4 * s * s;
    }

    public static double kelvin(double salinityPsu) {
        return 273.15 + celsius(salinityPsu);
    }

    /** 当前参考盐度对应的冰点，仅用于兼容旧调用。 */
    public static double referenceKelvin() {
        return kelvin(S_REFERENCE);
    }
}
