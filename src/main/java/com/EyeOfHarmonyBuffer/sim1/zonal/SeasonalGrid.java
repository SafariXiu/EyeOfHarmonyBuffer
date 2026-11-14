package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile;

/**
 * ★ 2026-11-14（1b/2）：**纬向季节柱网格**——19 纬，每纬一条带热容的季节柱。
 *
 * <p>陆/海按 sim2 的纬向陆地占比混合热容与反照率；输出最后一年的 Ts(lat, season)。
 */
public final class SeasonalGrid {

    public static final int NLAT = 19;

    public static final class Result {
        public final double[] latDeg = new double[NLAT];
        public final double[][] ts = new double[NLAT][];   // [lat][season]
        public double globalMeanK = 0.0;
        public double maxAmpK = 0.0;
        public long ms = 0;
    }

    private SeasonalGrid() {}

    public static double landFraction(double latRad) {
        double k = ZonalLandProfile.at(latRad);
        return Math.max(0.0, Math.min(1.0, k));
    }

    /** fixedK>=0 时用固定陆地占比（原型/无 seed 时用），否则读 sim2 的剖面。 */
    public static Result run(int years, int stepsPerYear, double fixedK) { return run(years, stepsPerYear, fixedK, null); }

    /** qAtm 为 19 点大气输运辐合 [W/m²]；null = 不加。 */
    public static Result run(int years, int stepsPerYear, double fixedK, double[] qAtm) {
        long t0 = System.currentTimeMillis();
        Result r = new Result();
        double wsum = 0.0, tsum = 0.0;
        for (int i = 0; i < NLAT; i++) {
            double lat = -90.0 + 10.0 * i;
            double latRad = Math.toRadians(lat);
            r.latDeg[i] = lat;
            double k = (fixedK >= 0.0) ? fixedK : landFraction(latRad);
            double c = (1.0 - k) * Atmosphere.C_SEA + k * Atmosphere.C_LAND;
            double alb = Radiation.ALB_SEA + k * (Radiation.ALB_LAND - Radiation.ALB_SEA);
            double[] ts = SeasonalColumn.run(lat, alb, c, years, stepsPerYear, qAtm == null ? 0.0 : qAtm[i]);
            r.ts[i] = ts;
            double tmin = 1e9, tmax = -1e9;
            for (double t : ts) { tmin = Math.min(tmin, t); tmax = Math.max(tmax, t); }
            r.maxAmpK = Math.max(r.maxAmpK, tmax - tmin);
            double cw = Math.cos(latRad);
            double mean = 0.0;
            for (double t : ts) mean += t;
            mean /= ts.length;
            tsum += mean * cw; wsum += cw;
        }
        r.globalMeanK = tsum / wsum;
        r.ms = System.currentTimeMillis() - t0;
        return r;
    }

    /** 默认原型：全海（避免依赖 OceanWiring 的 seed）。 */
    public static Result run(int years, int stepsPerYear) { return run(years, stepsPerYear, 0.0); }
}