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
        double alb = Radiation.albedo(false, sstK, lat);
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
        double aOcean = Radiation.albedo(false, sstK, lat);
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
