package com.EyeOfHarmonyBuffer.sim.world;

/**
 * 世界契约（决定 1 = B）的**唯一真相来源**。
 *
 * <pre>
 *   X      无限、无周期
 *   Z      无限
 *   气候   在 Z 上以 Z_CYCLE 周期重复
 *   地形   在 Z 上不重复
 * </pre>
 *
 * <p>纬度用**帐篷函数**：z=0 是赤道，z=MAX_D 是极点，z=Z_CYCLE 又回到赤道。
 * ⚠ 极点在 z=MAX_D 处是 N/S 焊死的折返点 —— 这就是裁决项 D1（极点分离）要改的东西。
 */
public final class WorldContract {

    private WorldContract() {}

    /**
     * 气候在 z 上的周期（block）。
     *
     * <p><b>裁决：1,000,000 -> 20,000,000</b>（设计冻结 §29）。理由：
     * <ul>
     *   <li>极到赤道 = MAX_D = 10,000 km ⇒ 行星半径 6,366 km，**与地球 6,371 km 差 0.1%**；</li>
     *   <li>东边界沿岸上升流层 T_E = tau*L_along/(2*rho*f) **正比于 L_along** ⇒ x20；</li>
     *   <li>风带宽度/L_R 从 0.20 升到 4.07 ⇒ **斜压不稳定（风暴轴）从「不存在」变成「与地球同级」**；</li>
     *   <li>**西边界流完全不受影响**：curl ∝ 1/MAX_D 且 beta ∝ 1/MAX_D ⇒ curl/beta 不变。</li>
     * </ul>
     * 唯一代价是玩家从赤道走到极点要 10,000 km（1 格 ≈ 1 米是锁死的）—— 这是玩法选择。
     */
    public static final int Z_CYCLE = 20_000_000;
    public static final int MAX_D = Z_CYCLE / 2;
    public static final double OMEGA = 7.2921e-5;
    /** 有效行星半径（m）：R_eff = MAX_D/(pi/2) = 6366 km —— 与地球 6371 km 差 0.1%。 */
    public static final double R_EFF = MAX_D / (Math.PI / 2.0);
    /** 一年多少「天」（季节相位的时间单位，纯约定）。 */
    public static final double DAYS_PER_YEAR = 365.25;

    /** 帐篷函数 bandD：0（赤道）-> 1（极点）-> 0。 */
    public static double bandD(int z, int zCycle) {
        int zz = ((z % zCycle) + zCycle) % zCycle;
        return Math.min(zz, zCycle - zz) / (zCycle / 2.0);
    }

    /** 半球符号：z 在 [0, zCycle/2) 为 +1（北），否则 -1（南）。 */
    public static double hemisphereSign(int z, int zCycle) {
        return (((z % zCycle) + zCycle) % zCycle) < zCycle / 2 ? 1.0 : -1.0;
    }

    /** **带符号**纬度（弧度）。 */
    public static double latOf(int z, int zCycle) {
        return hemisphereSign(z, zCycle) * bandD(z, zCycle) * Math.PI / 2.0;
    }

    /** beta(phi) = 2*Omega*pi/zCycle*cos(phi)。**绝不要再用常数 3.24e-10（那是 45 度的值）。** */
    public static double betaForLatitude(double latRad, int zCycle) {
        return 2.0 * OMEGA * Math.PI / zCycle * Math.cos(latRad);
    }

    /** 科氏参数 f = 2*Omega*sin(phi)。 */
    public static double coriolis(double latRad) {
        return 2.0 * OMEGA * Math.sin(latRad);
    }

    // ---- 默认契约的便捷重载 ----
    public static double bandD(int z) { return bandD(z, Z_CYCLE); }
    public static double latOf(int z) { return latOf(z, Z_CYCLE); }
    public static double betaForLatitude(double latRad) { return betaForLatitude(latRad, Z_CYCLE); }
}
