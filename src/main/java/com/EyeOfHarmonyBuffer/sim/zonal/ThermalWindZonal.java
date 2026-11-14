package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（3 的前置）：**热成风闭合**——从自己的 T(p) 纬向梯度积分出 u(p)。
 *
 * <p>∂u/∂p = −(R_d/(f·p))·∂T/∂y；地表 u=0 向上积分。|lat|<15° 跳过（Hadley 区）。
 * 判据：急流位置应跟随**自身斜压性最大处**（不是地球）。
 */
public final class ThermalWindZonal {

    private ThermalWindZonal() {}

    /** u[lat][k]（m/s），k=0 柱顶、k=N-1 地表。 */
    public static double[][] zonalWind(double[] latDeg, double[][] tMean) {
        int n = latDeg.length, K = VerticalColumn.N;
        double R = WorldContract.R_EFF;
        double[][] u = new double[n][K];
        for (int i = 1; i < n - 1; i++) {
            double latRad = Math.toRadians(latDeg[i]);
            double f = 2.0 * WorldContract.OMEGA * Math.sin(latRad);
            if (Math.abs(f) < 1.0e-4) continue;
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            u[i][K - 1] = 0.0;
            for (int k = K - 2; k >= 0; k--) {
                double dTdy = (tMean[i + 1][k] - tMean[i - 1][k]) / dy;
                double p = VerticalColumn.PC[k];
                double dp = VerticalColumn.PC[k] - VerticalColumn.PC[k + 1];   // 向上为负
                u[i][k] = u[i][k + 1] - (ParcelLift.R_D / (f * p)) * dTdy * dp;
            }
        }
        return u;
    }

    /** 急流：{纬度, 最大 |u|}（逐纬取最大层）。 */
    public static double[] jet(double[] latDeg, double[][] u) {
        double best = 0.0, bestLat = 0.0;
        for (int i = 1; i < latDeg.length - 1; i++) {
            double m = 0.0;
            for (int k = 0; k < VerticalColumn.N; k++) m = Math.max(m, Math.abs(u[i][k]));
            if (m > best) { best = m; bestLat = latDeg[i]; }
        }
        return new double[]{bestLat, best};
    }
}