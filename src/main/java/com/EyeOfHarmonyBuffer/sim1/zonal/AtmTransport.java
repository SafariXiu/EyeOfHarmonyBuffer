package com.EyeOfHarmonyBuffer.sim1.zonal;

/**
 * ★ 2026-11-14（1b 敏感性）：大气经向热输运的**占位接口**。
 *
 * <p>这里的 gaussian() 只是**敏感性实验**（给多少 W/m² 的柱加热才能把极地冬季抬到 ~250 K），
 * **不是物理**；最终必须由 3（风/环流）与 5（涡动）自算。
 */
public final class AtmTransport {

    private AtmTransport() {}

    /** 高斯型柱加热 [W/m²]：中心 |lat|=centerDeg、宽度 widthDeg、峰值 ampWm2（南北对称）。 */
    public static double[] gaussian(double ampWm2, double centerDeg, double widthDeg) {
        double[] q = new double[ZonalColumns.NLAT];
        for (int i = 0; i < ZonalColumns.NLAT; i++) {
            double lat = -90.0 + 10.0 * i;
            double d = (Math.abs(lat) - centerDeg) / widthDeg;
            q[i] = ampWm2 * Math.exp(-d * d);
        }
        return q;
    }
}