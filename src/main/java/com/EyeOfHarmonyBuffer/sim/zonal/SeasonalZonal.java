package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（1b/2）：**同步时间步进的纬向季节模型**（19 纬一起推进）。
 *
 * <p>每步：各纬柱在固定 Ts 下解大气（|dH|<1）→ F_i；
 * 再用**隐式扩散**（三对角）把大气经向输运 Q 并入，更新 Ts。
 * 隐式 ⟹ 10 天步长也稳定（显式扩散数 D·dt/Δy² 会 >1）。
 */
public final class SeasonalZonal {

    public static final int NL = 19;
    public static final double DT_ATM = 7200.0;
    public static final int MAX_ATM = 3000;

    private SeasonalZonal() {}

    public static final class Result {
        public final double[] latDeg = new double[NL];
        public final double[][] ts = new double[NL][];
        public double[][] pMmDay = null;   // 降水 [mm/day]
        public double[][] wKgM2 = null;    // 柱水汽 [kg/m²]
        public double[][] tMean = null;    // 年均 T(p)（NL × N），最后一年平均
        public double[][] eMmDay = null;   // 蒸发 [mm/day]
        public double globalMeanK = 0.0;
        public double maxAmpK = 0.0;
        public long ms = 0;
    }

    public static Result run(double fixedLandFrac, int years, int stepsPerYear) {
        return run(fixedLandFrac, years, stepsPerYear, 0.0);
    }

    /** ohtScale: 观测 OHT 表的缩放（0=无；1600=地球尺度，因为表是按旧 159 km 世界缩的）。 */
    public static Result run(double fixedLandFrac, int years, int stepsPerYear, double ohtScale) {
        long t0 = System.currentTimeMillis();
        double[] qOht = null;
        if (ohtScale != 0.0) {
            double[] f37 = new double[com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.T_OHT_W_37.length];
            for (int k = 0; k < f37.length; k++) f37[k] = com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.T_OHT_W_37[k] * ohtScale;
            qOht = ZonalUtil.fluxConvergence(f37);
        }
        Result r = new Result();
        double[] ts = new double[NL];
        VerticalColumn[] cols = new VerticalColumn[NL];
        double[] cap = new double[NL], alb = new double[NL], heat = new double[NL];
        double[] d = new double[NL];
        for (int i = 0; i < NL; i++) {
            double lat = -90.0 + 10.0 * i;
            r.latDeg[i] = lat;
            double k = (fixedLandFrac >= 0.0) ? fixedLandFrac
                    : Math.max(0.0, Math.min(1.0, com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile.at(Math.toRadians(lat))));
            heat[i] = (1.0 - k) * Atmosphere.C_SEA + k * Atmosphere.C_LAND;
            alb[i] = Radiation.ALB_SEA + k * (Radiation.ALB_LAND - Radiation.ALB_SEA);
            d[i] = DiffusiveTransport.diffusivity(lat);
            if (Math.abs(lat) < 20.0) d[i] = 0.0;
            cols[i] = new VerticalColumn();
            cols[i].init(280.0, 0.010, 1.0, 6.0);
            ts[i] = 280.0;
            cap[i] = Radiation.CP * PrecipField.P_SURF / PrecipField.G_ACC;
        }
        double dtSeason = 365.0 * 86400.0 / stepsPerYear;
        double dy = Math.toRadians(10.0) * WorldContract.R_EFF;
        double R = WorldContract.R_EFF;
        double saveAlb = VerticalColumn.ALBEDO;
        VerticalColumn.FIXED_TS = true;
        double[] f = new double[NL];
        for (int y = 0; y < years; y++) {
            for (int j = 0; j < stepsPerYear; j++) {
                double theta = 2.0 * Math.PI * j / stepsPerYear;
                double dec = 0.409 * Math.cos(theta);
                for (int i = 0; i < NL; i++) {
                    VerticalColumn c = cols[i];
                    VerticalColumn.ALBEDO = alb[i];
                    c.tSfc = ts[i];
                    double latRad = Math.toRadians(r.latDeg[i]);
                    double cz = Math.max(0.01, Radiation.insolation(latRad, dec) / VerticalColumn.S0);
                    c.solveAtmosphere(DT_ATM, cz, 200.0, 1.0, MAX_ATM);
                    f[i] = c.lastSurfNet;
                    if (y == years - 1) {
                        if (r.pMmDay == null) {
                            r.pMmDay = new double[NL][stepsPerYear];
                            r.eMmDay = new double[NL][stepsPerYear];
                        }
                        r.pMmDay[i][j] = (c.precipConv + c.precipLs) / c.lastDt * 86400.0;
                        r.eMmDay[i][j] = c.lh / Radiation.LV * 86400.0;
                        if (r.wKgM2 == null) r.wKgM2 = new double[NL][stepsPerYear];
                        r.wKgM2[i][j] = c.cwv();
                        if (r.tMean == null) r.tMean = new double[NL][VerticalColumn.N];
                        for (int k = 0; k < VerticalColumn.N; k++) r.tMean[i][k] += c.t[k];
                    }
                }
                // 隐式扩散：T^{n+1} − (dt/C)·L(T^{n+1}) = T^n + (dt/C)·F
                double[] a = new double[NL], b = new double[NL], c2 = new double[NL], rhs = new double[NL];
                for (int i = 0; i < NL; i++) {
                    double phi = Math.toRadians(r.latDeg[i]);
                    double cosI = Math.max(0.02, Math.cos(phi));
                    double km = (i > 0) ? Math.cos(0.5 * (phi + Math.toRadians(r.latDeg[i - 1]))) * 0.5 * (d[i] + d[i - 1]) : 0.0;
                    double kp = (i < NL - 1) ? Math.cos(0.5 * (phi + Math.toRadians(r.latDeg[i + 1]))) * 0.5 * (d[i] + d[i + 1]) : 0.0;
                    double gm = cap[i] * km / (cosI * dy * dy);
                    double gp = cap[i] * kp / (cosI * dy * dy);
                    double dtc = dtSeason / heat[i];
                    a[i] = -dtc * gm;
                    c2[i] = -dtc * gp;
                    b[i] = 1.0 - a[i] - c2[i];
                    rhs[i] = ts[i] + dtc * (f[i] + (qOht == null ? 0.0 : qOht[i]));
                }
                double[] x = solveTri(a, b, c2, rhs);
                for (int i = 0; i < NL; i++) ts[i] = x[i];
                if (y == years - 1) {
                    if (r.ts[0] == null) for (int i = 0; i < NL; i++) r.ts[i] = new double[stepsPerYear];
                    for (int i = 0; i < NL; i++) r.ts[i][j] = ts[i];
                }
            }
        }
        VerticalColumn.FIXED_TS = false;
        VerticalColumn.ALBEDO = saveAlb;
        double wsum = 0.0, tsum = 0.0;
        for (int i = 0; i < NL; i++) {
            double cw = Math.cos(Math.toRadians(r.latDeg[i]));
            double mean = 0.0, mn = 1e9, mx = -1e9;
            for (double t : r.ts[i]) { mean += t; mn = Math.min(mn, t); mx = Math.max(mx, t); }
            mean /= r.ts[i].length;
            r.maxAmpK = Math.max(r.maxAmpK, mx - mn);
            tsum += mean * cw; wsum += cw;
        }
        if (r.tMean != null) for (int i = 0; i < NL; i++) for (int k = 0; k < VerticalColumn.N; k++) r.tMean[i][k] /= stepsPerYear;
        r.globalMeanK = tsum / wsum;
        r.ms = System.currentTimeMillis() - t0;
        return r;
    }

    private static double[] solveTri(double[] a, double[] b, double[] c, double[] d) {
        int n = b.length;
        double[] cp = new double[n], dp = new double[n], x = new double[n];
        cp[0] = c[0] / b[0]; dp[0] = d[0] / b[0];
        for (int i = 1; i < n; i++) {
            double m = b[i] - a[i] * cp[i - 1];
            cp[i] = (i < n - 1) ? c[i] / m : 0.0;
            dp[i] = (d[i] - a[i] * dp[i - 1]) / m;
        }
        x[n - 1] = dp[n - 1];
        for (int i = n - 2; i >= 0; i--) x[i] = dp[i] - cp[i] * x[i + 1];
        return x;
    }
}