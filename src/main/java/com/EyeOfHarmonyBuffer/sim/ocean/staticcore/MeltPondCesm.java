package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 2026-11-22 (#4 步骤②): CESM melt-pond tracer, sourced from Icepack.
 *
 * <p>Equations: Icepack columnphysics/icepack_meltpond_cesm.F90
 * (Bailey/Holland/Hunke). Defaults: CICE ponds_nml
 * pndaspect=0.8, rfracmax=0.85, frzpnd=cesm; Td=2 C, rexp=0.01, dpthhi=0.9
 * from the same source. No new physical constants are invented here.
 *
 * <p>This class is not wired into production; it is the tested building block.
 * It tracks grid-cell pond area fraction and pond depth; pond water is a
 * radiation tracer (CICE: not used in mass budgets).
 */
public final class MeltPondCesm {
    private MeltPondCesm() {}

    /** CICE ponds_nml defaults (sourced; not fitted). */
    public static final double PNDASPECT = 0.8;
    public static final double RFRAC = 0.85;
    public static final double TD = 2.0;
    public static final double REXP = 0.01;
    public static final double DPTHHI = 0.9;

    /**
     * @param apndIn pond grid-cell area fraction [0..1]
     * @param hpndIn pond depth [m]
     * @param meltIceMperS top ice melt rate [m/s]
     * @param meltSnowMperS snow melt rate [m/s]
     * @param rainKgM2S rainfall [kg/m2/s]
     * @return {new area fraction, new depth}
     */
    public static double[] update(double dt, double aice, double vice, double tsfcK,
                                  double meltIceMperS, double meltSnowMperS,
                                  double rainKgM2S, double apndIn, double hpndIn) {
        if (!(aice > 1.0e-12) || !(vice > 0.0)) return new double[]{0.0, 0.0};
        double hi = vice / aice;
        double apnd = Math.max(0.0, Math.min(1.0, apndIn));
        double hpnd = Math.max(0.0, hpndIn);
        double volpn = hpnd * apnd * aice;   // grid-cell mean pond volume [m]

        double meltt = Math.max(0.0, meltIceMperS);
        double melts = Math.max(0.0, meltSnowMperS);
        double frain = Math.max(0.0, rainKgM2S);
        volpn += RFRAC / OceanConstants.RHO_FRESH
                * (meltt * OceanConstants.RHO_ICE + melts * OceanConstants.RHO_SNOW
                + frain * dt) * aice;

        // CICE formula is in Celsius: Tp = 0 - Td = -TD; Tsfc is surface C.
        double tsfcC = tsfcK - OceanConstants.T_ICE0;
        double tpC = -TD;                              // pond freezing temperature [C]
        double dts = Math.max(tpC - tsfcC, 0.0);
        volpn *= Math.exp(REXP * dts / tpC);
        volpn = Math.max(0.0, volpn);

        double apondn = Math.min(Math.sqrt(volpn / (PNDASPECT * aice)), 1.0);
        double hpondn = PNDASPECT * apondn;
        hpondn = Math.min(hpondn, DPTHHI * hi);
        apondn = Math.min(1.0, hpondn / PNDASPECT);
        return new double[]{apondn * aice, hpondn};
    }
}
