package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（4 的前置）：**纬向水汽收支**。
 *
 * <p>v_zm 由 **Held-Hou 解出的 w_zm** 经质量连续性导出（不是查表）：
 * V(φ) = −a·(∫₀^φ w_zm cosφ′dφ′ − wbar·sinφ)/cosφ，v_zm=V/H；
 * MFC = −(1/(2πR²cosφ))·d(2πR cosφ·v_zm·W)/dφ；稳态 P = E + MFC（dW/dt≈0）。
 */
public final class ZonalWaterBudget {

    /**
     * ★ 2026-11-18（AWBC M0）：散度改用**面通量有限体积**（默认 true）。
     *
     * <p>false = 2026-11-14 的格心中心差分（保留作 A/B），它**不守恒**：
     * 实测 ∫P/∫E = 0.963（`NAWBCM0`，残差 3.7%）。
     * <p>本类目前**不在生产路径上**（`SeasonalZonal.WATER_MFC_ON=false`、
     * `ZonalClimateProvider.ON=false`），所以翻默认不影响生产解（NIDEM 逐位不变已验）。
     */
    public static boolean FV_FACES_ON = true;

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
        double[] p = new double[n];
        if (FV_FACES_ON) {
            // ★ 2026-11-18（AWBC M0→M1）：**面通量有限体积**。
            //   旧实现用【格心】通量的中心差分，再除以 dy：离域积分会留下
            //   −(F[n−2] − F[1])/2 的账 ⟹ ∫MFC ≠ 0（实测 NAWBCM0 残差 3.7%）。
            //   有限体积把散度写成【面】通量之差 / 格面积，域积分**逐格抵消**；
            //   两端的面（2.5° 与 87.5°）显式置零：赤道侧 v=0（对称性）、
            //   极侧 cosφ→0（几何），这与本类"只覆盖 2.5°–87.5° 预算带"的口径一致。
            int nf = n + 1;                       // 面：i+1/2 → 下标 i
            double[] fFace = new double[nf];
            double[] vF = new double[nf];
            double[] wF = new double[nf];
            for (int i = 1; i < n - 1; i++) {     // 内部面（i 与 i+1 之间）
                vF[i + 1] = 0.5 * (v[i] + v[i + 1]);
                wF[i + 1] = 0.5 * (wKgM2[i] + wKgM2[i + 1]);
                double latFace = 0.5 * (latDeg[i] + latDeg[i + 1]);
                if (i + 1 >= 2 && i + 1 <= n - 2) {
                    fFace[i + 1] = 2.0 * Math.PI * R * Math.cos(Math.toRadians(latFace))
                            * vF[i + 1] * wF[i + 1];   // kg/s
                }
            }
            // 两端面显式置零（见上）
            for (int k = 0; k < nf; k++) {
                boolean interiorFace = (k >= 2 && k <= n - 2);
                if (!interiorFace) fFace[k] = 0.0;
            }
            for (int i = 1; i < n - 1; i++) {
                double c = Math.max(0.02, Math.cos(Math.toRadians(latDeg[i])));
                double dphi = Math.toRadians(Math.abs(latDeg[i + 1] - latDeg[i - 1])) / 2.0;
                double area = 2.0 * Math.PI * R * c * (R * dphi);      // m²（纬带格面积）
                double mfc = -(fFace[i + 1] - fFace[i]) / area;         // kg/(m² s)
                p[i] = (eMmDay[i] / 86400.0 + mfc) * 86400.0;           // mm/day
            }
            return p;
        }
        double[] flux = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double c = Math.max(0.02, Math.cos(Math.toRadians(latDeg[i])));
            flux[i] = 2.0 * Math.PI * R * c * v[i] * wKgM2[i];   // kg/s
        }
        for (int i = 1; i < n - 1; i++) {
            double c = Math.max(0.02, Math.cos(Math.toRadians(latDeg[i])));
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double mfc = -(flux[i + 1] - flux[i - 1]) / (2.0 * dy) / (2.0 * Math.PI * R * c);  // kg/(m²s)
            p[i] = (eMmDay[i] / 86400.0 + mfc) * 86400.0;   // mm/day
        }
        return p;
    }
}