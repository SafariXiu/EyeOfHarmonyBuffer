package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/** 纬向骨架的共用小工具（网格常量 + 经向热通量剖面的辐合）。 */
public final class ZonalUtil {

    /** 纬向网格点数（−90..+90，每 10°）。 */
    public static final int NLAT = 19;

    private ZonalUtil() {}

    /** 任意经向热通量剖面（37 点，W，正=向北）的辐合 [W/m²] → 19 纬。 */
    public static double[] fluxConvergence(double[] fluxW37) {
        double[] f = new double[NLAT];
        for (int i = 0; i < NLAT; i++) {
            double lat = -90.0 + 10.0 * i;
            double x = (lat + 90.0) / 5.0;          // 37 点：−90..90 每 5°
            int i0 = (int) Math.floor(x);
            if (i0 < 0) i0 = 0;
            if (i0 >= 36) { f[i] = fluxW37[36]; continue; }
            double tx = x - i0;
            f[i] = fluxW37[i0] * (1.0 - tx) + fluxW37[i0 + 1] * tx;
        }
        double[] q = new double[NLAT];
        double R = WorldContract.R_EFF;
        for (int i = 1; i < NLAT - 1; i++) {
            double phi = Math.toRadians(-90.0 + 10.0 * i);
            double c = Math.max(0.02, Math.cos(phi));
            // Q = −(dF/dφ)/(2πR²cosφ) = −(dF/dy)/(2πR cosφ)   ← 只除一个 R
            double dFdy = (f[i + 1] - f[i - 1]) / (2.0 * Math.toRadians(10.0) * R);
            q[i] = -dFdy / (2.0 * Math.PI * R * c);
        }
        return q;
    }
}