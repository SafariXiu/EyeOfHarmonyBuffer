package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ParcelLift;
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

    // 云长波研究臂的显式输入（仅 CLOUD_LW_ON=true 时用；默认关，旧路径零影响）。
    /** Research arm: prognostic column water W. Default false => old path bit-identical. */
    public static boolean W_STATE_ON = false;
    /** Research arm: use the existing ZonalWaterBudget MFC closure for W. Default false. */
    public static boolean WATER_MFC_ON = false;
    /** Retired with the 2.5D ocean removal (2026-11-16). Kept only so stale probes fail loudly. */
    @Deprecated public static final boolean OHT_FROM_OCEAN = false;
    /** Research arm: infinite-X plane geometry (q=-dF/dz, no 2*pi*R*cos). Default false. */
    public static boolean PLANE_GEOMETRY = false;

    /** 云量 RH 阈值；ECMWF IFS/Sundqvist 形式，原文精确式待核对。 */
    public static final double CLOUD_RH_C = 0.80;
    /** 降水质量 -> 云水路径的停留时标 [s]；Smith & Barstad (2004) 的云延迟时标。 */
    public static final double CLOUD_LWP_TAU = 1000.0;
    /** 长波质量吸收系数 [m²/kg]；0.1 m²/g 的 Stephens (1978) 量级，待核对原文/单位。 */
    public static final double CLOUD_KAPPA = 100.0;

    private SeasonalZonal() {}

    public static final class Result {
        public final double[] latDeg = new double[NL];
        public final double[][] ts = new double[NL][];
        public double[][] pMmDay = null;   // 降水 [mm/day]
        public double[][] wKgM2 = null;    // 柱水汽 [kg/m²]
        public double[][] wStateKgM2 = null; // prognostic W state [kg/m²]
        public double[][] mfcMmDay = null;   // mean-flow MFC [mm/day]
        public double[][] wBudgetMmDay = null; // q-budget residual [mm/day]
        public double wStartKgM2 = 0.0, wEndKgM2 = 0.0;
        public double epIntegralKgM2 = 0.0, mfcIntegralKgM2 = 0.0;
        public double[][] tMean = null;    // 年均 T(p)（NL × N），最后一年平均
        public double[][] eMmDay = null;   // 蒸发 [mm/day]
        public double globalMeanK = 0.0;
        public double maxAmpK = 0.0;
        public long ms = 0;
    }

    /** Build one cloud layer from the previous step's condensate/RH; LWP comes from the prognostic state. */
    private static void applyCloud(VerticalColumn c, double lwp) {
        if (!VerticalColumn.CLOUD_LW_ON && !VerticalColumn.CLOUD_SW_ON) return;
        int layer = -1;
        if (c.lastCloudTopPa > 0.0) {
            double best = 1.0e30;
            for (int k = 0; k < VerticalColumn.N; k++) {
                double d = Math.abs(VerticalColumn.PC[k] - c.lastCloudTopPa);
                if (d < best) { best = d; layer = k; }
            }
        } else {
            double best = -1.0;
            for (int k = 0; k < VerticalColumn.N; k++) {
                double rh = c.q[k] / Math.max(1.0e-12, ParcelLift.qs(c.t[k], VerticalColumn.PC[k]));
                if (rh > best) { best = rh; layer = k; }
            }
        }
        if (layer < 0) layer = VerticalColumn.N / 2;
        double rh = c.q[layer] / Math.max(1.0e-12, ParcelLift.qs(c.t[layer], VerticalColumn.PC[layer]));
        double frac = (rh - CLOUD_RH_C) / (1.0 - CLOUD_RH_C);
        frac = Math.max(0.0, Math.min(1.0, frac));
        c.cloudLayer = layer;
        c.cloudFrac = frac;
        c.cloudLwpKgM2 = Math.max(0.0, lwp);
        c.cloudKappaM2Kg = CLOUD_KAPPA;
    }

    public static Result run(double fixedLandFrac, int years, int stepsPerYear) {
        return run(fixedLandFrac, years, stepsPerYear, 0.0);
    }

    /**
     * ohtScale is retained only for signature compatibility and is IGNORED.
     * The 2026-11-15 audit removed the Earth T2019 OHT table from this path;
     * transport is now the model's own diffusive closure.
     */
    public static Result run(double fixedLandFrac, int years, int stepsPerYear, double ohtScale) {
        long t0 = System.currentTimeMillis();
        double[] qOht = null;   // 2.5D OHT bridge removed; model diffusion carries the transport
        Result r = new Result();
        double[] ts = new double[NL];
        VerticalColumn[] cols = new VerticalColumn[NL];
        double[] cap = new double[NL], alb = new double[NL], heat = new double[NL];
        double[] d = new double[NL];
        double[] lwpState = new double[NL];   // prognostic LWP [kg/m²], source=P, sink=LWP/tau
        double[] wState = new double[NL];     // prognostic column water [kg/m²], research arm
        for (int i = 0; i < NL; i++) {
            double lat = -90.0 + 10.0 * i;
            r.latDeg[i] = lat;
            double k = (fixedLandFrac >= 0.0) ? fixedLandFrac
                    : Math.max(0.0, Math.min(1.0, com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile.at(Math.toRadians(lat))));
            heat[i] = (1.0 - k) * Atmosphere.C_SEA + k * Atmosphere.C_LAND;
            alb[i] = Radiation.ALB_SEA + k * (Radiation.ALB_LAND - Radiation.ALB_SEA);
            d[i] = 0.0;   // filled from the model's own T each step; no Earth table
            cols[i] = new VerticalColumn();
            cols[i].init(280.0, 0.010, 1.0, 6.0);
            wState[i] = cols[i].cwv();
            ts[i] = 280.0;
            cap[i] = Radiation.CP * PrecipField.P_SURF / PrecipField.G_ACC;
        }
        double dtSeason = 365.0 * 86400.0 / stepsPerYear;
        double dy = Math.toRadians(10.0) * WorldContract.R_EFF;
        double R = WorldContract.R_EFF;
        double saveAlb = VerticalColumn.ALBEDO;
        VerticalColumn.FIXED_TS = true;
        double[] f = new double[NL];
        double[] mfcStep = new double[NL];
        double wgtSum = 0.0;
        for (int i = 0; i < NL; i++) wgtSum += Math.cos(Math.toRadians(r.latDeg[i]));
        int k850 = 0;
        double best850 = 1.0e30;
        for (int k = 0; k < VerticalColumn.N; k++) {
            double dd = Math.abs(VerticalColumn.PC[k] - 85000.0);
            if (dd < best850) { best850 = dd; k850 = k; }
        }
        for (int y = 0; y < years; y++) {
            for (int j = 0; j < stepsPerYear; j++) {
                double theta = 2.0 * Math.PI * j / stepsPerYear;
                double dec = 0.409 * Math.cos(theta);
                for (int i = 0; i < NL; i++) {
                    VerticalColumn c = cols[i];
                    VerticalColumn.ALBEDO = alb[i];
                    if (W_STATE_ON) c.setColumnWater(wState[i]);
                    c.tSfc = ts[i];
                    double latRad = Math.toRadians(r.latDeg[i]);
                    double cz = Math.max(0.01, Radiation.insolation(latRad, dec) / VerticalColumn.S0);
                    applyCloud(c, lwpState[i]);
                    c.solveAtmosphere(DT_ATM, cz, 200.0, 1.0, MAX_ATM);
                    f[i] = c.lastSurfNet;
                    // prognostic cloud water: source P, sink LWP/tau, implicit update
                    lwpState[i] = (lwpState[i] + Math.max(0.0, c.lastPFluxKgM2S) * dtSeason)
                            / (1.0 + dtSeason / CLOUD_LWP_TAU);
                    if (W_STATE_ON) {
                        // VerticalColumn.step already applied surface E, convection and condensation
                        // to q[].  The prognostic W must be the ACTUAL column water after that step;
                        // adding (E-P) here again double-counts the water tendency.
                        wState[i] = c.cwv();
                    }
                    if (y == years - 1) {
                        if (r.pMmDay == null) {
                            r.pMmDay = new double[NL][stepsPerYear];
                            r.eMmDay = new double[NL][stepsPerYear];
                        }
                        // 2026-11-15: use the mean over all internal solveAtmosphere steps,
                        // not the last step's instantaneous flux (NGRIDBAND P-E closure).
                        r.pMmDay[i][j] = c.lastPFluxKgM2S * 86400.0;
                        r.eMmDay[i][j] = c.lastEFluxKgM2S * 86400.0;
                        if (r.wBudgetMmDay == null) r.wBudgetMmDay = new double[NL][stepsPerYear];
                        r.wBudgetMmDay[i][j] = c.lastWBudgetResidualMean * 86400.0;
                        if (r.wKgM2 == null) r.wKgM2 = new double[NL][stepsPerYear];
                        r.wKgM2[i][j] = c.cwv();
                        if (r.wStateKgM2 == null) r.wStateKgM2 = new double[NL][stepsPerYear];
                        r.wStateKgM2[i][j] = wState[i];
                        if (r.tMean == null) r.tMean = new double[NL][VerticalColumn.N];
                        for (int k = 0; k < VerticalColumn.N; k++) r.tMean[i][k] += c.t[k];
                    }
                }
                // 2026-11-15: MFC is applied once per step, after all columns are solved.
                if (W_STATE_ON && WATER_MFC_ON) {
                    double[] eMm = new double[NL];
                    for (int q = 0; q < NL; q++) eMm[q] = Math.max(0.0, cols[q].lastEFluxKgM2S) * 86400.0;
                    double[] pEq = ZonalWaterBudget.precipitation(r.latDeg, wState, eMm,
                            latDeg -> com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell.wZmSolved(latDeg));
                    for (int i = 0; i < NL; i++) {
                        mfcStep[i] = (pEq[i] - eMm[i]) / 86400.0;
                        if (Double.isFinite(mfcStep[i])) {
                            wState[i] = Math.max(0.0, wState[i] + mfcStep[i] * dtSeason);
                        }
                    }
                    if (y == years - 1) {
                        if (r.mfcMmDay == null) r.mfcMmDay = new double[NL][stepsPerYear];
                        for (int i = 0; i < NL; i++) r.mfcMmDay[i][j] = mfcStep[i] * 86400.0;
                    }
                }
                if (W_STATE_ON) {
                    r.wEndKgM2 = 0.0;   // per-step accumulator, not annual accumulation
                    if (y == years - 1 && j == 0) {
                        for (int i = 0; i < NL; i++) r.wStartKgM2 += Math.cos(Math.toRadians(r.latDeg[i])) * wState[i];
                    }
                    for (int i = 0; i < NL; i++) {
                        double cw = Math.cos(Math.toRadians(r.latDeg[i]));
                        r.wEndKgM2 += cw * wState[i];
                        if (y == years - 1) {
                            r.epIntegralKgM2 += cw * (cols[i].lastEFluxKgM2S - cols[i].lastPFluxKgM2S) * dtSeason;
                            r.mfcIntegralKgM2 += cw * mfcStep[i] * dtSeason;
                        }
                    }
                }
                // 2026-11-15: diffusivity from the model's own 850-hPa T, not Earth tables.
                for (int i = 1; i < NL - 1; i++) {
                    d[i] = DiffusiveTransport.diffusivityFromT(r.latDeg[i],
                            cols[i - 1].t[k850], cols[i].t[k850], cols[i + 1].t[k850]);
                }
                d[0] = 0.0;
                d[NL - 1] = 0.0;
                // 隐式扩散：T^{n+1} − (dt/C)·L(T^{n+1}) = T^n + (dt/C)·F
                double[] a = new double[NL], b = new double[NL], c2 = new double[NL], rhs = new double[NL];
                for (int i = 0; i < NL; i++) {
                    double phi = Math.toRadians(r.latDeg[i]);
                    double cosI = Math.max(0.02, Math.cos(phi));
                    double km, kp, gm, gp;
                    if (PLANE_GEOMETRY) {
                        double dz = Math.toRadians(10.0) * WorldContract.R_EFF;
                        km = (i > 0) ? 0.5 * (d[i] + d[i - 1]) : 0.0;
                        kp = (i < NL - 1) ? 0.5 * (d[i] + d[i + 1]) : 0.0;
                        gm = cap[i] * km / (dz * dz);
                        gp = cap[i] * kp / (dz * dz);
                    } else {
                        km = (i > 0) ? Math.cos(0.5 * (phi + Math.toRadians(r.latDeg[i - 1]))) * 0.5 * (d[i] + d[i - 1]) : 0.0;
                        kp = (i < NL - 1) ? Math.cos(0.5 * (phi + Math.toRadians(r.latDeg[i + 1]))) * 0.5 * (d[i] + d[i + 1]) : 0.0;
                        gm = cap[i] * km / (cosI * dy * dy);
                        gp = cap[i] * kp / (cosI * dy * dy);
                    }
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
        if (wgtSum > 0.0) {
            r.wStartKgM2 /= wgtSum;
            r.wEndKgM2 /= wgtSum;
            r.epIntegralKgM2 /= wgtSum;
            r.mfcIntegralKgM2 /= wgtSum;
        }
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