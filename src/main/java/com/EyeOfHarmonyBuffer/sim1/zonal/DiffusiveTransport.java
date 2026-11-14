package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（1b/2）：**大气经向热输运的扩散闭合**（占位；最终由 3/5 自算）。
 *
 * <p>F(φ) = −(2πR cosφ)·(cp·p_sfc/g)·D·∂T/∂y，Q = −(1/(2πR cosφ))·dF/dy。
 * D = σ·L_d²（σ 取模型的 eadyGrowth，L_d 取变形半径 c/f）——零地球表；D 的量级会打印出来。
 */
public final class DiffusiveTransport {

    private DiffusiveTransport() {}

    public static double diffusivity(double latDeg) {
        double latRad = Math.toRadians(Math.abs(latDeg));
        // 四季平均的斜压增长率（年输运用）
        double sigma = 0.25 * (PrecipField.eadyGrowth(latRad, 0.0)
                + PrecipField.eadyGrowth(latRad, 0.5 * Math.PI)
                + PrecipField.eadyGrowth(latRad, Math.PI)
                + PrecipField.eadyGrowth(latRad, 1.5 * Math.PI));
        double f = Math.max(1.0e-5, 2.0 * WorldContract.OMEGA * Math.sin(latRad));
        // ★ 2026-11-14 修正：L_d = N·H/f（N=1e-2 s⁻¹、H=8 km ⟹ c=80 m/s），封顶 1.5e6 m。
        //   旧式 c=√(gH)=280 m/s 且不封顶 ⟹ 赤道 D 发散、Q 到 −4526 W/m²。
        double c = 0.01 * 8000.0;
        double ld = Math.min(1.5e6, c / f);
        return Math.min(5.0e6, sigma * ld * ld);
    }

    /** 给定 19 点年均 T（K），返回 19 点 Q [W/m²]（全球积分为 0）。 */
    public static double[] qFromT(double[] latDeg, double[] tK) {
        int n = latDeg.length;
        double R = WorldContract.R_EFF;
        double cap = Radiation.CP * PrecipField.P_SURF / PrecipField.G_ACC;   // J/(m²K)
        double[] f = new double[n];
        for (int i = 1; i < n - 1; i++) {
            // ★ 热带交给 Hadley（|lat|<20° 不扩散）
            if (Math.abs(latDeg[i]) < 20.0) { f[i] = 0.0; continue; }
            double phi = Math.toRadians(latDeg[i]);
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double dTdy = (tK[i + 1] - tK[i - 1]) / (2.0 * dy);
            double d = diffusivity(latDeg[i]);
            f[i] = -(2.0 * Math.PI * R * Math.cos(phi)) * cap * d * dTdy;
        }
        double[] q = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double phi = Math.toRadians(latDeg[i]);
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double c = Math.max(0.02, Math.cos(phi));
            q[i] = -(f[i + 1] - f[i - 1]) / (2.0 * dy) / (2.0 * Math.PI * R * c);
        }
        return q;
    }
}