package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.AtmosphereBoundaryLayer1D;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.ocean.staticcore.AdvectionDiffusionSolver;
import com.EyeOfHarmonyBuffer.sim.ocean.staticcore.OceanConstants;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.util.HashMap;

/**
 * F2 诊断版 L0 -> L1 通量（默认无消费者）。
 *
 * <p>只用现有 Atmosphere/Radiation/PrecipField 公式，不新增常数。
 * <p>风应力/热通量可用；淡水 E-P 尚未接线，调用会抛 UnsupportedOperationException，
 * 不返回假 0。
 */
public final class DiagnosticSeaSurfaceForcing implements SeaSurfaceForcing {

    private final long seed;
    private final int cell;
    private final int gradStep;
    private final HashMap<Long, double[]> ATMO = new HashMap<>();
    private final HashMap<Long, AtmosphereBoundaryLayer1D> ABL = new HashMap<>();
    /** 2026-11-22 (#4)：默认关；打开才建 per-cell 1D ABL。 */
    public static boolean ATMOS_ABL_1D_ON = false;
    public static long ABL_ATMO_CALLS = 0, ABL_ADV_CALLS = 0, ABL_STEP_CALLS = 0;
    public static double ABL_MIN_THETA = Double.POSITIVE_INFINITY;
    public static double ABL_MAX_THETA = Double.NEGATIVE_INFINITY;
    public static double ABL_LAST_BEFORE = Double.NaN, ABL_LAST_AFTER = Double.NaN;

    public DiagnosticSeaSurfaceForcing(long seed, int cell, int gradStep) {
        this.seed = seed;
        this.cell = cell;
        this.gradStep = gradStep;
    }

    private static long key(int x, int z, double theta) {
        long h = x * 1000003L + z;
        h = h * 31 + Double.doubleToLongBits(theta);
        return h;
    }

    private static long ablKey(int x, int z) { return x * 1000003L + z; }

    private AtmosphereBoundaryLayer1D ablFor(int x, int z, double theta) {
        long k = ablKey(x, z);
        AtmosphereBoundaryLayer1D c = ABL.get(k);
        if (c != null) return c;
        c = new AtmosphereBoundaryLayer1D(new double[]{2.0, 10.0, 30.0});
        double tclim = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
        if (!Double.isFinite(tclim)) return null;
        double qclim = PrecipField.RH_SEA * PrecipField.qSat(tclim);
        double[] uv = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        for (int n = 0; n < c.z.length; n++) {
            c.theta[n] = tclim;
            c.q[n] = qclim;
            c.u[n] = uv[0];
            c.v[n] = uv[1];
        }
        if (ABL.size() > 100000) ABL.clear();
        ABL.put(k, c);
        return c;
    }

    @Override public void advanceBoundaryLayer(int x, int z, double theta, double dt, double tsK, double qs) {
        if (!ATMOS_ABL_1D_ON) return;
        AtmosphereBoundaryLayer1D c = ABL.get(ablKey(x, z));
        if (c == null || !Double.isFinite(c.theta[0])) return;
        ABL_ADV_CALLS++;
        ABL_LAST_BEFORE = c.theta[0];
        double freeT = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
        double freeQ = PrecipField.RH_SEA * PrecipField.qSat(freeT);
        double[] uv = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        c.coupledSurfaceStep(dt, tsK, qs, freeT, freeQ, uv[0], uv[1],
                Atmosphere.RHO_AIR, PrecipField.CP_AIR, Radiation.LV,
                0.0, 0.0, 0.985);
        ABL_STEP_CALLS++;
        ABL_LAST_AFTER = c.theta[0];
    }

    /** [ta, qa, chv, wind]；只依赖 (x,z,theta)。 */
    private double[] atmo(int x, int z, double theta) {
        long k = key(x, z, theta);
        if (!Atmosphere.EQ_WALKER_ON && !AdvectionDiffusionSolver.D3_ICE_ATMOS_ON
                && !ATMOS_ABL_1D_ON) {
            // 2026-11-20 (D1b): 沃克开时缓存会挡住实时 SST 梯度 ⟹ 绕过
            // 2026-11-21 D3: 冰面温度每轮变化 ⟹ 缓存必须绕过
            double[] cached = ATMO.get(k);
            if (cached != null) return cached;
        }
        double ta = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
        if (AdvectionDiffusionSolver.D3_ICE_ATMOS_ON && Atmosphere.ICE_PROVIDER != null) {
            Atmosphere.IceProvider p = Atmosphere.ICE_PROVIDER;
            double fi = p.iceFraction(x, z);
            double ts = p.iceSurfaceTemp(x, z);
            if (fi > 0.0 && Double.isFinite(ts)) {
                if (fi > 1.0) fi = 1.0;
                ta += fi * (ts - ta);
            }
        }
        double qa = PrecipField.RH_SEA * PrecipField.qSat(ta);
        double[] uv = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double u = uv[0], v = uv[1];
        // 2026-11-20（D1b）：沃克项在 Atmosphere.windAt 里加（这里不再重复，避免双计）。
        double wind = Math.hypot(u, v);
        double chv = Radiation.bulkCoeff(0.0, wind);
        if (ATMOS_ABL_1D_ON) {
            AtmosphereBoundaryLayer1D c = ablFor(x, z, theta);
            if (c != null && Double.isFinite(c.theta[0])) {
                ABL_ATMO_CALLS++;
                ABL_MIN_THETA = Math.min(ABL_MIN_THETA, c.theta[0]);
                ABL_MAX_THETA = Math.max(ABL_MAX_THETA, c.theta[0]);
                ta = c.theta[0];
                qa = c.q[0];
                u = c.u[0];
                v = c.v[0];
                wind = Math.hypot(u, v);
                chv = Radiation.bulkCoeff(0.0, wind);
            }
        }
        // 2026-11-20（第③步）：云量由本世界降水给出（默认关 ⟹ cf=0 ⟹ 逐位不变）
        double cf = 0.0;
        if (Radiation.CLOUD_ON) {
            double p = com.EyeOfHarmonyBuffer.sim.atmos.PrecipField.mmPerDay(x, z, seed, cell, theta, 500_000);
            if (p > 0.0 && Double.isFinite(p)) {
                double pm = Radiation.precipMean();
                cf = p / (p + Math.max(1.0e-6, pm));
            }
        }
        double[] a = new double[]{ta, qa, chv, wind, u, v, cf};
        if (!Atmosphere.EQ_WALKER_ON && !AdvectionDiffusionSolver.D3_ICE_ATMOS_ON
                && !ATMOS_ABL_1D_ON) {
            if (ATMO.size() > 200000) ATMO.clear();
            ATMO.put(k, a);
        }
        return a;
    }

    private double[] tau(int x, int z, double theta) {
        return Atmosphere.windStress(x, z, seed, cell, theta, gradStep);
    }

    @Override public double windStressX(int x, int z, double theta) {
        return tau(x, z, theta)[0];
    }

    @Override public double windStressZ(int x, int z, double theta) {
        return tau(x, z, theta)[1];
    }

    @Override public double windU(int x, int z, double theta) {
        return atmo(x, z, theta)[4];
    }

    @Override public double windV(int x, int z, double theta) {
        return atmo(x, z, theta)[5];
    }

    public double shortwaveWm2(int x, int z, double theta, double sstK) {
        double lat = WorldContract.latOf(z);
        double dec = Atmosphere.subsolarLat(theta);
        double[] at = atmo(x, z, theta);
        // ★ 2026-11-09（阶段 3 / A1）：开阔水反照率用 ALB_SEA，不再用
        //   Radiation.albedo(false,...) 的 FDD 气候态海冰反照率（272 K 给 0.633）。
        double alb = Radiation.ALB_SEA;
        double asr = Radiation.absSolarSurface(lat, dec, alb);
        return Radiation.components(sstK, asr, at[0], at[1], at[2], 1.0)[0];
    }

    @Override public double netHeatFluxWm2(int x, int z, double theta, double sstK) {
        return netHeatFluxWm2(x, z, theta, sstK, 0.0);
    }

    @Override public double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction) {
        return netHeatFluxWm2(x, z, theta, sstK, iceFraction,
                Radiation.iceThicknessM(WorldContract.latOf(z)));
    }

    /**
     * ★ 2026-11-20（D3/选项 a）：**冰面温度恒温器** —— 有日照且被冰覆盖时，贴地气温不低于冰点。
     *
     * <p>物理：融化的冰面把边界层钉在 0 °C（海冰的经典「温度恒温器」），而模型的 {@code ta}
     * 来自气候函数（用了**海表**季节振幅 `A_SEA_K`，高纬只有 2–4 K）⟹ 极地夏季只有 259 K，
     * 冰永远不化。本开关只在**有日照**时生效（极夜不改），默认 false ⟹ 逐位不变。
     */
    public static boolean ICE_AIR_THERMOSTAT = false;
    /** 2026-11-21（#4 步骤④ D3）：雪反照率（用已有 snowBroadband 常数）。默认 false ⟹ 逐位不变。 */
    public static boolean D3_SNOW_ALBEDO_ON = false;
    /** 2026-11-20（b 诊断）：**全年**（不限日照）把冰上气温钉在冰点。默认 false ⟹ 逐位不变。 */
    public static boolean ICE_AIR_THERMOSTAT_ALL = false;

    @Override public double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction,
                                           double iceThicknessM) {
        return netHeatFluxWm2(x, z, theta, sstK, iceFraction, iceThicknessM, 0.0);
    }

    @Override public double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction,
                                           double iceThicknessM, double snowThicknessM) {
        return netHeatFluxWm2(x, z, theta, sstK, iceFraction, iceThicknessM, snowThicknessM, 0.0);
    }

    @Override public double netHeatFluxWm2(int x, int z, double theta, double sstK, double iceFraction,
                                           double iceThicknessM, double snowThicknessM,
                                           double pondFraction) {
        double lat = WorldContract.latOf(z);
        double dec = Atmosphere.subsolarLat(theta);
        double[] at = atmo(x, z, theta);
        double ta = at[0], qa = at[1], chv = at[2];
        if (ICE_AIR_THERMOSTAT_ALL && iceFraction > 0.5) {
            // 2026-11-20（b 诊断）：全年把冰上气温钉在冰点 ⟹ 若冰仍长，则增长来自海洋而非大气
            double tf = com.EyeOfHarmonyBuffer.sim.ocean.staticcore.OceanConstants.T_FREEZE;
            if (ta < tf) ta = tf;
        }
        if (ICE_AIR_THERMOSTAT && iceFraction > 0.5
                && Radiation.insolation(lat, dec) > 0.0) {
            double tf = com.EyeOfHarmonyBuffer.sim.ocean.staticcore.OceanConstants.T_FREEZE;
            if (ta < tf) ta = tf;
        }
        // ★ 2026-11-09（阶段 3 / A1）：开阔水是 ALB_SEA；冰面才用冰反照率，
        //   按【实际】iceFraction 混合。旧式 aOcean 走 FDD 气候态冰，0–9 °C 水
        //   被当成 0.63→0.06 的反照率，是冰 runaway 的正反馈源。
        double aOcean = Radiation.ALB_SEA;
        double aIce = (D3_SNOW_ALBEDO_ON && snowThicknessM > 1.0e-6)
                ? Radiation.snowBroadband(sstK)
                : Radiation.seaIceAlbedo(lat, sstK, iceThicknessM);
        if (AdvectionDiffusionSolver.MELT_POND_CESM_ON && pondFraction > 0.0) {
            double p = Math.max(0.0, Math.min(1.0, pondFraction));
            aIce = (1.0 - p) * aIce + p * Radiation.ALB_SEA;
        }
        double alb = aOcean + iceFraction * (aIce - aOcean);
        double asr = Radiation.absSolarSurface(lat, dec, alb);
        if (Radiation.CLOUD_ON) {
            asr *= (1.0 - at[6] * Radiation.CLOUD_DA);
            // 云的**下行长波**增量（正值=加热地表）
            return Radiation.residual(sstK, asr, ta, qa, chv, 1.0) + at[6] * Radiation.CLOUD_DLW;
        }
        return Radiation.residual(sstK, asr, ta, qa, chv, 1.0);
    }

    /**
     * ★ 2026-11-20（B 取证）：与 {@link #netHeatFluxWm2(int,int,double,double,double)} **同口径**的分项。
     * 冰厚用 5 参路径的同一取值（`Radiation.iceThicknessM(lat)`），所以 Qnet = Qsw−Qlw−Qsen−Qlat 与实测净通量一致。
     */
    @Override public double[] surfaceFluxComponents(int x, int z, double theta, double sstK,
                                                    double iceFraction) {
        double lat = WorldContract.latOf(z);
        double dec = Atmosphere.subsolarLat(theta);
        double[] at = atmo(x, z, theta);
        double ta = at[0], qa = at[1], chv = at[2];
        double aOcean = Radiation.ALB_SEA;
        double aIce = Radiation.seaIceAlbedo(lat, sstK, Radiation.iceThicknessM(lat));
        double alb = aOcean + iceFraction * (aIce - aOcean);
        double asr = Radiation.absSolarSurface(lat, dec, alb);
        return Radiation.components(sstK, asr, ta, qa, chv, 1.0);
    }

    @Override public double[] airState(int x, int z, double theta) {
        double[] at = atmo(x, z, theta);
        return new double[]{at[0], at[1], at[2]};
    }

    @Override public double snowfallMperS(int x, int z, double theta) {
        double pMmDay = PrecipField.mmPerDay(x, z, seed, cell, theta, gradStep);
        if (!(pMmDay > 0.0)) return 0.0;
        double ta = atmo(x, z, theta)[0];
        double f = (OceanConstants.T_RAIN - ta)
                / (OceanConstants.T_RAIN - OceanConstants.T_SNOW);
        if (f <= 0.0) return 0.0;
        if (f >= 1.0) f = 1.0;
        // mm/day -> kg/m2/s -> m/s water equivalent（1 mm = 1 kg/m2）。
        return pMmDay / (86400.0 * OceanConstants.RHO_FRESH) * f;
    }

    @Override public double freshwaterFluxKgM2S(int x, int z, double theta, double sstK) {
        double pMmDay = PrecipField.mmPerDay(x, z, seed, cell, theta, gradStep);
        double[] at = atmo(x, z, theta);
        double ta = at[0], qa = at[1], chv = at[2];
        double eMmDay = Radiation.potentialEvapMmDay(sstK, qa, chv);
        double pMinusE = (pMmDay - eMmDay) / 86400.0;
        double runoff = SoilMoisture.runoffKgM2SAt(x, z, seed, cell, gradStep);
        return pMinusE + runoff;
    }
}
