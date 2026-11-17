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

    /** 海表风矢量 u（m/s）；默认 0。只给冰漂移参数化用。 */
    default double windU(int x, int z, double theta) { return 0.0; }

    /** 海表风矢量 v（m/s）；默认 0。 */
    default double windV(int x, int z, double theta) { return 0.0; }

    /** 净热通量（W/m2，正 = 进入海洋）。 */
    double netHeatFluxWm2(int x, int z, double theta, double sstK);

    /** 带冰覆盖率的最小扩展；默认退化为无冰。 */
    default double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction) {
        return netHeatFluxWm2(x, z, theta, sstK);
    }

    /** 带【冰厚】的扩展；默认退化为 5 参数（旧实现逐位不变）。 */
    default double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction, double iceThicknessM) {
        return netHeatFluxWm2(x, z, theta, sstK, iceFraction);
    }

    /** 短波（W/m2）；默认 0，由具体实现覆盖。 */
    default double shortwaveWm2(int x, int z, double theta, double sstK) { return 0.0; }

    /** 淡水通量（kg/(m2 s)，正 = 进入海洋）。 */
    double freshwaterFluxKgM2S(int x, int z, double theta, double sstK);

    /**
     * 降雪率（m/s 水当量，正 = 落到冰面/海面）。默认 0；Stage B 的生产实现
     * 用总降水 × 温度分配。降雪不再同时作为海洋淡水，需从 freshwaterFlux 中扣除。
     */
    default double snowfallMperS(int x, int z, double theta) { return 0.0; }
}
