package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;

/**
 * ★ 2026-11-14（1b/2）：**带热容的季节柱**。
 *
 * <p>每个地表时间步：给定 Ts 把大气解到平衡（|dH|<tol），读同一步的地表净额 F，
 * 再 dTs/dt = F/C 推进一步。这样季节记忆留在 C 里，避免「瞬时 RCE」的 89 K 假振幅。
 * C 由调用方给（海/陆），物理量来自 Atmosphere 的平板模型。
 */
public final class SeasonalColumn {

    public static final double DT_ATM = 7200.0;
    public static final double TOL_ATM = 1.0;
    public static final int MAX_ATM = 4000;

    private SeasonalColumn() {}

    /** 返回最后一年每个地表步的 Ts（K）。stepsPerYear 个点；theta_j = 2π j/stepsPerYear。 */
    public static double[] run(double latDeg, double albedo, double heatCapacity, int years, int stepsPerYear) {
        return run(latDeg, albedo, heatCapacity, years, stepsPerYear, 0.0);
    }

    /** 带大气输运辐合 qAtm [W/m²]（整层均匀加热）的季节柱。 */
    public static double[] run(double latDeg, double albedo, double heatCapacity, int years, int stepsPerYear, double qAtm) {
        VerticalColumn c = new VerticalColumn();
        c.init(280.0, 0.010, 1.0, 6.0);
        c.qAtmExtraWm2 = qAtm;
        double ts = 280.0;
        double[] last = new double[stepsPerYear];
        double dtSeason = 365.0 * 86400.0 / stepsPerYear;
        double latRad = Math.toRadians(latDeg);
        double saveAlb = VerticalColumn.ALBEDO;
        VerticalColumn.ALBEDO = albedo;
        VerticalColumn.FIXED_TS = true;
        for (int y = 0; y < years; y++) {
            for (int j = 0; j < stepsPerYear; j++) {
                double theta = 2.0 * Math.PI * j / stepsPerYear;
                double dec = 0.409 * Math.cos(theta);
                double cz = Math.max(0.01, Radiation.insolation(latRad, dec) / VerticalColumn.S0);
                c.tSfc = ts;
                c.solveAtmosphere(DT_ATM, cz, 200.0, TOL_ATM, MAX_ATM);
                double f = c.lastSurfNet;
                ts += f * dtSeason / heatCapacity;
                if (y == years - 1) last[j] = ts;
            }
        }
        VerticalColumn.FIXED_TS = false;
        VerticalColumn.ALBEDO = saveAlb;
        return last;
    }
}