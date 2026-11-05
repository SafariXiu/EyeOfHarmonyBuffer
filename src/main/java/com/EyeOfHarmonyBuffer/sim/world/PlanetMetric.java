package com.EyeOfHarmonyBuffer.sim.world;

/**
 * 行星度量 —— 全仓「口径」的唯一来源。
 *
 * <p>为什么要有这个类（2026-11-03 审计）：同一个世界出现了多个周长。
 * 大气/降水/陆地分析按 40,000 km；而 3D 海洋的 {@code OceanGrid.WIDTH_M} 是 80,000 km
 * （MITgcm 80k 实验遗留：{@code delX=144*555555.5556}），并且全域没有 cos(lat) 面积权重。
 * 本类把【半径 / 周长 / 面积 / 网格单元真实面积】收成一处，消费者只允许引用这里。
 *
 * <p>数值全部由 {@link WorldContract} 派生，<b>不新造任何常数</b>：
 * <pre>
 *   MAX_D       = 10,000,000 m        极到赤道
 *   R_EFF       = MAX_D / (pi/2)      = 6,366,197.7 m
 *   CIRC        = 4 * MAX_D           = 40,000,000 m   （= Z_CYCLE = 2*pi*R，浮点上逐位相等）
 *   AREA        = 4 * pi * R^2        = 5.092958179e14 m^2
 * </pre>
 *
 * <p>⚠ 关于「无限世界」：X 无限、无周期（见 {@link WorldContract}）。任何周期箱都是
 * <b>模型边界</b>，不参与本类的定义；跨了几圈要在使用处显式写出（例如
 * {@code OceanGrid.WIDTH_LAPS}），不得再用 80_000_000 这种字面量。
 *
 * <p>口径自检：{@link #circumferenceDrift()} 必须 ≈ 0；探针 {@code NMETRIC} 会检查
 * 各消费者（OceanGrid / LandContract / 大气的 2*pi*R 表达式）是否与本类一致。
 */
public final class PlanetMetric {

    private PlanetMetric() {}

    /** 有效行星半径（m）= MAX_D/(pi/2) = 6,366,197.7。 */
    public static final double RADIUS_M = WorldContract.R_EFF;

    /** 赤道周长（m）= 整条子午圈 = 4*MAX_D = 2*pi*R = 40,000,000。 */
    public static final double CIRCUMFERENCE_M = WorldContract.Z_CYCLE;

    /** 行星表面积（m^2）= 4*pi*R^2 = 5.092958179e14（约 5.09e8 km^2）。 */
    public static final double AREA_M2 = 4.0 * Math.PI * RADIUS_M * RADIUS_M;

    /** 纬度 latRad 处的一圈长度（m）= CIRC*cos(lat)；极点为 0。 */
    public static double circumferenceAt(double latRad) {
        return CIRCUMFERENCE_M * Math.cos(latRad);
    }

    /** 一个 dx*dy 网格单元在纬度 latRad 处的真实面积（m^2）。 */
    public static double cellAreaM2(double dx, double dy, double latRad) {
        return dx * dy * Math.cos(latRad);
    }

    /** 2*pi*R 与 CIRC 的相对差。定义正确时应 ≈ 0（浮点量级 1e-16）。 */
    public static double circumferenceDrift() {
        return Math.abs(2.0 * Math.PI * RADIUS_M - CIRCUMFERENCE_M) / CIRCUMFERENCE_M;
    }
}
