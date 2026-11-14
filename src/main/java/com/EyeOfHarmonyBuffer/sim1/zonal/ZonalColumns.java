package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.VerticalColumn;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（1b+2 原型）：sim1 的**纬向单柱骨架**。
 *
 * <p>物理：每个 (纬度, 季节) 一根 RCE 柱（VerticalColumn），强迫 = 日平均太阳天顶角 + 地表反照率；
 * 纬向耦合先用**OHT 辐合 qExtra** 作为外加热源（NONE / 观测 reference / 3D 三档由调用方给）。
 *
 * <p>零新参数：cosZenith = insolation/S0；qExtra = −d(OHT)/(2πR²cosφ)；OHT 由调用方提供。
 */
public final class ZonalColumns {

    public static final int NLAT = 19;      // −90..+90，每 10°
    public static final int NSEA = 6;       // 6 个季节相位（60°）
    public static final double DT = 7200.0;
    public static final double TOL_TOA = 1.0;
    public static final int MAX_STEPS = 20000;

    public static final class Result {
        public final double[] latDeg = new double[NLAT];
        public final double[] tsK = new double[NLAT * NSEA];
        public final double[] toa = new double[NLAT * NSEA];   // asr − olr + qExtra
        public final double[] precip = new double[NLAT * NSEA];
        public int failed = 0;
        public double maxToaResid = 0.0;
    }

    private ZonalColumns() {}

    /** 任意经向热通量剖面的辐合 [W/m²]（37 点，W，正=向北）→ 19 纬。 */
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
            // Q = −(dF/dφ)/(2πR²cosφ) = −(dF/dy)/(2πR cosφ)   ← 只除一个 R（旧式多除了一个 R ⟹ Q 被压成 ~0）
            double dFdy = (f[i + 1] - f[i - 1]) / (2.0 * Math.toRadians(10.0) * R);
            q[i] = -dFdy / (2.0 * Math.PI * R * c);
        }
        return q;
    }

    /** 跑到稳态；两个剖面为 null 时对应项=0（只验闭合，气候会很极端）。 */
    public static Result solve(double[] ohtW37, double[] atmFluxW37) {
        double[] qOht = (ohtW37 == null) ? new double[NLAT] : fluxConvergence(ohtW37);
        double[] qAtm = (atmFluxW37 == null) ? new double[NLAT] : fluxConvergence(atmFluxW37);
        return solveQ(qOht, qAtm);
    }

    /** 直接给 19 点辐合 [W/m²]（敏感性实验/耦合迭代用）。 */
    public static Result solveQ(double[] qOht, double[] qAtm) {
        Result r = new Result();
        VerticalColumn prev = null;
        double saveAlb = VerticalColumn.ALBEDO;
        for (int i = 0; i < NLAT; i++) {
            double lat = -90.0 + 10.0 * i;
            double latRad = Math.toRadians(lat);
            r.latDeg[i] = lat;
            for (int s = 0; s < NSEA; s++) {
                double theta = 2.0 * Math.PI * s / NSEA;
                double dec = 0.409 * Math.cos(theta);
                double cz = Math.max(0.01, Radiation.insolation(latRad, dec) / VerticalColumn.S0);
                VerticalColumn c = new VerticalColumn();
                if (prev == null) c.init(288.0, 0.010, 1.0, 6.0);
                else {
                    c.init(prev.tSfc, prev.q[VerticalColumn.N - 1], 1.0, 6.0);
                    for (int k = 0; k < VerticalColumn.N; k++) { c.t[k] = prev.t[k]; c.q[k] = prev.q[k]; }
                }
                VerticalColumn.ALBEDO = 0.06;   // 原型：开阔海；陆/冰由调用方后续给
                c.qExtraWm2 = qOht[i];
                c.qAtmExtraWm2 = qAtm[i];
                int n = c.solveRCE(DT, cz, 200.0, TOL_TOA, MAX_STEPS);
                int idx = i * NSEA + s;
                r.tsK[idx] = c.tSfc;
                r.toa[idx] = c.asr - c.olr + c.qExtraWm2 + c.qAtmExtraWm2;
                r.precip[idx] = c.precipConv + c.precipLs;
                if (n < 0) r.failed++;
                double a = Math.abs(r.toa[idx]);
                if (a > r.maxToaResid) r.maxToaResid = a;
                prev = c;
            }
        }
        VerticalColumn.ALBEDO = saveAlb;
        return r;
    }
}