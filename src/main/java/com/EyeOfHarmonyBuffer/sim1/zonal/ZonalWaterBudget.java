package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（4 的前置）：**纬向水汽收支**。
 *
 * <p>v_zm 由 **Held-Hou 解出的 w_zm** 经质量连续性导出（不是查表）：
 * V(φ) = −a·(∫₀^φ w_zm cosφ′dφ′ − wbar·sinφ)/cosφ，v_zm=V/H；
 * MFC = −(1/(2πR²cosφ))·d(2πR cosφ·v_zm·W)/dφ；稳态 P = E + MFC（dW/dt≈0）。
 */
public final class ZonalWaterBudget {

    private ZonalWaterBudget() {}

    /** wZm: 纬度（度）→ 纬向平均垂直速度 [m/s]（Hadley 解）。返回 19 点 P [mm/day]。 */
    public static double[] precipitation(double[] latDeg, double[] wKgM2, double[] eMmDay,
            java.util.function.DoubleUnaryOperator wZm) {
        int n = latDeg.length;
        double R = WorldContract.R_EFF;
        double H = 8000.0;
        double[] acc = new double[n];
        double prevW = wZm.applyAsDouble(0.0);
        for (int i = 1; i < n; i++) {
            double d2 = Math.abs(latDeg[i]);
            double curW = wZm.applyAsDouble(d2) * Math.cos(Math.toRadians(d2));
            acc[i] = acc[i - 1] + 0.5 * (prevW + curW) * Math.toRadians(Math.abs(latDeg[i] - latDeg[i - 1]));
            prevW = curW;
        }
        double wbar = acc[n - 1];
        double[] v = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double d2 = Math.abs(latDeg[i]);
            double c = Math.max(0.02, Math.cos(Math.toRadians(d2)));
            double V = -(R * (acc[i] - wbar * Math.sin(Math.toRadians(d2)))) / c;
            v[i] = V / H;
        }
        double[] flux = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double c = Math.max(0.02, Math.cos(Math.toRadians(latDeg[i])));
            flux[i] = 2.0 * Math.PI * R * c * v[i] * wKgM2[i];   // kg/s
        }
        double[] p = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double c = Math.max(0.02, Math.cos(Math.toRadians(latDeg[i])));
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double mfc = -(flux[i + 1] - flux[i - 1]) / (2.0 * dy) / (2.0 * Math.PI * R * c);  // kg/(m²s)
            p[i] = (eMmDay[i] / 86400.0 + mfc) * 86400.0;   // mm/day
        }
        return p;
    }
}