package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
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

    /** [ta, qa, chv, wind]；只依赖 (x,z,theta)。 */
    private double[] atmo(int x, int z, double theta) {
        long k = key(x, z, theta);
        double[] a = ATMO.get(k);
        if (a != null) return a;
        double ta = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
        double qa = PrecipField.RH_SEA * PrecipField.qSat(ta);
        double[] uv = Atmosphere.windAt(x, z, seed, cell, theta, gradStep);
        double wind = Math.hypot(uv[0], uv[1]);
        double chv = Radiation.bulkCoeff(0.0, wind);
        a = new double[]{ta, qa, chv, wind};
        if (ATMO.size() > 200000) ATMO.clear();
        ATMO.put(k, a);
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
        double lat = WorldContract.latOf(z);
        double dec = Atmosphere.subsolarLat(theta);
        double[] at = atmo(x, z, theta);
        double ta = at[0], qa = at[1], chv = at[2];
        // ★ 2026-11-09（阶段 3 / A1）：开阔水是 ALB_SEA；冰面才用冰反照率，
        //   按【实际】iceFraction 混合。旧式 aOcean 走 FDD 气候态冰，0–9 °C 水
        //   被当成 0.63→0.06 的反照率，是冰 runaway 的正反馈源。
        double aOcean = Radiation.ALB_SEA;
        double aIce = Radiation.albedo(true, sstK, lat);
        double alb = aOcean + iceFraction * (aIce - aOcean);
        double asr = Radiation.absSolarSurface(lat, dec, alb);
        return Radiation.residual(sstK, asr, ta, qa, chv, 1.0);
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
