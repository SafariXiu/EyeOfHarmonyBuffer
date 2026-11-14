package com.EyeOfHarmonyBuffer.sim1.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（1b 修正）：把**平板季节热容**接进骨架。
 *
 * <p>不再让每个 (lat,season) 独立解瞬时 RCE（那会给出 89 K 的假振幅）；
 * 季节 Ts 由平板解给出：A = (F0/λ)/sqrt(1+(ωτ)²)，φ = 谐波相位 + atan(ωτ)。
 * 物理量（C_SEA/C_LAND、λ、τ）全部复用 Atmosphere 里已验证的平板实现，零新常数。
 */
public final class SlabSeasonal {

    private SlabSeasonal() {}

    public static double omega() {
        return 2.0 * Math.PI / (WorldContract.DAYS_PER_YEAR * 86400.0);
    }

    public static double ampK(double latRad, double kappa) {
        double[] ap = Atmosphere.slabAnnualHarmonic(latRad, kappa);
        double lam = Atmosphere.slabLambda(latRad, kappa);
        double tau = Atmosphere.slabTau(latRad, kappa);
        double w = omega();
        return (ap[0] / lam) / Math.sqrt(1.0 + w * tau * w * tau);
    }

    public static double phaseRad(double latRad, double kappa) {
        double[] ap = Atmosphere.slabAnnualHarmonic(latRad, kappa);
        double tau = Atmosphere.slabTau(latRad, kappa);
        return ap[1] + Math.atan(omega() * tau);
    }

    /** 给定年均 Ts，返回该季的平板 Ts。 */
    public static double tsK(double latRad, double theta, double kappa, double meanK) {
        return meanK + ampK(latRad, kappa) * Math.cos(theta - phaseRad(latRad, kappa));
    }
}