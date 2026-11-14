package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 区域 patch 的父状态 —— sponge 边界的目标值（无限世界架构 §4）。
 *
 * <p>patch 只能看到自己 10,000 km 的窗口；窗口之外的大尺度状态由父提供。
 * 物理上这是区域海洋模式的标准单向嵌套：边界被松弛到父状态，误差在 sponge
 * 与罗斯贝调整尺度内衰减，而不是把边界当墙。
 *
 * <p>坐标一律是【绝对】世界坐标 (x, z, k)；父自己负责周期/夹边/插值。
 * 不可用的点返回 {@link Double#NaN}（sponge 遇到 NaN 就跳过，绝不用假 0）。
 */
public interface OceanParent {

    /** 父是否覆盖该点（否 = 不可用）。 */
    boolean covers(double x, double z);

    /** 位温（K）。 */
    double temperatureK(double x, double z, int k);

    /** 盐度（psu）。 */
    double salinityPsu(double x, double z, int k);

    /** 东西流速（m/s，正 = 东）。 */
    double uMs(double x, double z, int k);

    /** 南北流速（m/s，正 = 北）。 */
    double vMs(double x, double z, int k);

    /**
     * 正压流函数 psi（m^2/s，u=-dpsi/dz, v=dpsi/dx）。
     *
     * <p>阶段 2c 的法向输运边界要用它：把 patch 边界的 psi 钉到父的值，
     * 等于规定【穿过该边界的水体输运】——这是 sponge（只传值、不传通量）缺的那一半。
     * 默认 NaN（父不提供）。
     */
    default double psi(double x, double z) { return Double.NaN; }

    /** 冰覆盖率 [0,1]；默认 0（无冰父）。 */
    default double iceFraction(double x, double z) { return 0.0; }
}
