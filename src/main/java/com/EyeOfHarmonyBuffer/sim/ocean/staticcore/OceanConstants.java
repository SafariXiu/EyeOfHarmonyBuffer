package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import com.EyeOfHarmonyBuffer.sim.ocean.FreezingPoint;

/**
 * 3D 海洋内核共享的物理常数与两参数函数。
 *
 * <p>从 legacy 2.5D 的 {@code ClimberOcean} 抽出：3D 静态解只依赖这些量，
 * 不再依赖 2.5D 盆地求解器。数值与原处逐字相同。
 */
public final class OceanConstants {

    private OceanConstants() {}

    /** 参考海水密度 kg/m^3（与 AdvectionDiffusionSolver.RHO0 同值）。 */
    public static final double RHO_REF = 1027.8;
    /** 海水定压比热 J/(kg K)（与 AdvectionDiffusionSolver.CP_SEA 同值）。 */
    public static final double C_OCEAN = 3985.0;
    /** 混合层厚度 m。 */
    public static final double H_MIXED = 50.0;

    /** 冰的热传导率 W/m/K（Semtner 1976）。 */
    public static final double K_ICE = 2.0;
    /** 冰密度 kg/m^3。 */
    public static final double RHO_ICE = 917.0;
    /** 熔化潜热 J/kg。 */
    public static final double L_FUSION = 3.34e5;
    /** 冰厚上限 m（仅 legacy SurfaceTemperatureSolver 用；3D 生产不加 cap）。 */
    public static final double ICE_MAX = 3.0;

    /** 雪密度 kg/m^3（Icepack icepack_parameters.F90 rhos=330）。 */
    public static final double RHO_SNOW = 330.0;
    /** 雪导热率 W/(m K)（Icepack icepack_parameters.F90 ksno=0.30）。 */
    public static final double K_SNOW = 0.30;
    /** 淡水密度 kg/m^3（SI 常用值；用于 snowfall 的 m <-> kg/m2 换算）。 */
    public static final double RHO_FRESH = 1000.0;
    /** 雨/雪分配：全雪温度 K（0 C）。 */
    public static final double T_SNOW = 273.15;
    /** 雨/雪分配：全雨温度 K（2 C，2 K 过渡带；模型参数化，不是物理常数）。 */
    public static final double T_RAIN = 275.15;

    /** 侧向融化 m1, m2（Icepack frzmlt_bottom_lateral；Maykut & Perovich 1987）。 */
    public static final double LAT_M1 = 1.6e-6;
    public static final double LAT_M2 = 1.36;
    /** 有效浮冰形状/直径（Icepack 默认 floeshape=0.66, floediam=300 m；Steele 1992）。 */
    public static final double FLOE_SHAPE = 0.66;
    public static final double FLOE_DIAM = 300.0;

    /** 0 °C 出现海冰。 */
    public static final double T_ICE0 = 273.15;
    /** 参考盐度冰点 K（FreezingPoint，S=35）。 */
    public static final double T_FREEZE = FreezingPoint.referenceKelvin();
    /** 冰反照率（Petoukhov et al. 2000 Table 1）。 */
    public static final double ALB_ICE = 0.62;

    /** Bryan-Lewis 垂直扩散（m^2/s），z 为深度（m，正=向下）。 */
    public static double blKv(double zM) {
        return (0.8 + (1.05 / Math.PI) * Math.atan(4.5e-5 * (zM * 100.0 - 2500.0e2))) * 1.0e-4;
    }

    /** Bryan-Lewis 水平扩散（m^2/s）。 */
    public static double blKh(double zM) {
        return 1000.0 + 4000.0 * Math.exp(-zM / 500.0);
    }

    /** 海冰密集度（0..1）：0 °C 出现、−1.8 °C 全覆盖，线性。 */
    public static double iceFraction(double t) {
        if (t >= T_ICE0) return 0.0;
        if (t <= T_FREEZE) return 1.0;
        return (T_ICE0 - t) / (T_ICE0 - T_FREEZE);
    }
}
