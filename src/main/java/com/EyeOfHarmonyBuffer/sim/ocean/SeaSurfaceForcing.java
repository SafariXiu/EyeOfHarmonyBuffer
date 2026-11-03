package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * F2 L0 -> L1 海表通量接口（新，默认无实现）。
 *
 * <p>ADR-001：z 无限；所有方法使用原始 z，不做 z 周期回卷。
 * <p>单位：
 * <ul>
 *   <li>windStressX/Z: Pa；</li>
 *   <li>netHeatFluxWm2: W/m2，正 = 进入海洋；</li>
 *   <li>freshwaterFluxKgM2S: kg/(m2 s)，正 = 进入海洋（降水 - 蒸发）。</li>
 * </ul>
 *
 * <p>本接口只定义契约，不提供数值；实现必须来自 L0 大气/辐射，
 * 不得在此处硬编码地球表或拟合系数。
 */
public interface SeaSurfaceForcing {

    /** 海表风应力 x 分量（Pa）。 */
    double windStressX(int x, int z, double theta);

    /** 海表风应力 z 分量（Pa）。 */
    double windStressZ(int x, int z, double theta);

    /** 净热通量（W/m2，正 = 进入海洋）。 */
    double netHeatFluxWm2(int x, int z, double theta, double sstK);

    /** 带冰覆盖率的最小扩展；默认退化为无冰。 */
    default double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction) {
        return netHeatFluxWm2(x, z, theta, sstK);
    }

    /** 短波（W/m2）；默认 0，由具体实现覆盖。 */
    default double shortwaveWm2(int x, int z, double theta, double sstK) { return 0.0; }

    /** 淡水通量（kg/(m2 s)，正 = 进入海洋）。 */
    double freshwaterFluxKgM2S(int x, int z, double theta, double sstK);
}
