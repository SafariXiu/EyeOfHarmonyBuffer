package com.EyeOfHarmonyBuffer.sim.zonal;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-11-14（1b/2）：**大气经向热输运的扩散闭合**（占位；最终由 3/5 自算）。
 *
 * <p>F(φ) = −(2πR cosφ)·(cp·p_sfc/g)·D·∂T/∂y，Q = −(1/(2πR cosφ))·dF/dy。
 * D = σ·L_d²（σ 取模型的 eadyGrowth，L_d 取变形半径 c/f）——零地球表；D 的量级会打印出来。
 */
public final class DiffusiveTransport {

    private DiffusiveTransport() {}

    /**
     * Self-consistent diffusivity from the model's own T at 850 hPa.
     * Replaces the old path that read ZonalTables.t850Month/zonalSlTemp (Earth tables).
     */
    public static double diffusivityFromT(double latDeg, double tSouth, double tLocal, double tNorth) {
        if (Math.abs(latDeg) < 20.0) return 0.0;
        double lat = Math.toRadians(latDeg);
        double f = 2.0 * WorldContract.OMEGA * Math.sin(lat);
        if (Math.abs(f) < 1.0e-8) return 0.0;
        double T = Math.max(200.0, tLocal);
        double dy = Math.toRadians(10.0) * WorldContract.R_EFF;
        double dTdy = (tNorth - tSouth) / (2.0 * dy);
        double N = Math.sqrt(Math.max(1.0e-8,
                (PrecipField.G_ACC / T) * (PrecipField.GAMMA_D - Atmosphere.GAMMA)));
        double dudz = -(PrecipField.G_ACC / (f * T)) * dTdy;
        // Miles-Howard / Richardson critical shear: Ri = N^2 / (du/dz)^2 >= 1/4.
        // The Eady sigma grows without bound as |du/dz| grows; cap the shear at 2N.
        double shear = Math.min(Math.abs(dudz), 2.0 * N);
        double sigma = PrecipField.EADY_COEF * Math.abs(f) * shear / N;
        double ld = Math.min(1.5e6, N * Atmosphere.H_EFF / Math.abs(f));
        // Grid-scale mixing limiter: an eddy cannot diffuse across more than
        // one grid cell per synoptic eddy lifetime (~3 days).  This replaces the
        // unsourced 1e6 cap with a resolution-derived bound.
        double dz = Math.toRadians(10.0) * WorldContract.R_EFF;
        double tauEddy = 3.0 * 86400.0;
        double dGrid = dz * dz / (4.0 * tauEddy);
        return Math.min(dGrid, sigma * ld * ld);
    }

    /** Plane geometry: F(z) W per unit x, q=-dF/dz; no 2*pi*R*cos(phi). */
    public static double[] qFromTModelZ(double[] latDeg, double[] t850) {
        int n = latDeg.length;
        double dz = Math.toRadians(10.0) * WorldContract.R_EFF;
        double cap = Radiation.CP * PrecipField.P_SURF / PrecipField.G_ACC;
        double[] flux = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double dTdz = (t850[i + 1] - t850[i - 1]) / (2.0 * dz);
            double d = diffusivityFromT(latDeg[i], t850[i - 1], t850[i], t850[i + 1]);
            flux[i] = -cap * d * dTdz;   // W per unit x
        }
        double[] q = new double[n];
        for (int i = 1; i < n - 1; i++) {
            q[i] = -(flux[i + 1] - flux[i - 1]) / (2.0 * dz);
        }
        // Polar lines have zero area in the infinite-X plane; no cell-average q there.
        q[0] = 0.0;
        q[n - 1] = 0.0;
        return q;
    }

    /** Self-consistent Q from the model's own 850-hPa T; no Earth tables. */
    public static double[] qFromTModel(double[] latDeg, double[] t850) {
        int n = latDeg.length;
        double R = WorldContract.R_EFF;
        double cap = Radiation.CP * PrecipField.P_SURF / PrecipField.G_ACC;
        double[] flux = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double phi = Math.toRadians(latDeg[i]);
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double dTdy = (t850[i + 1] - t850[i - 1]) / (2.0 * dy);
            double d = diffusivityFromT(latDeg[i], t850[i - 1], t850[i], t850[i + 1]);
            flux[i] = -(2.0 * Math.PI * R * Math.cos(phi)) * cap * d * dTdy;
        }
        double[] q = new double[n];
        for (int i = 1; i < n - 1; i++) {
            double phi = Math.toRadians(latDeg[i]);
            double dy = Math.toRadians(latDeg[i + 1] - latDeg[i - 1]) * R / 2.0;
            double c = Math.max(0.02, Math.cos(phi));
            q[i] = -(flux[i + 1] - flux[i - 1]) / (2.0 * dy) / (2.0 * Math.PI * R * c);
        }
        // Polar cap: the pointwise cos(phi)->0 formula is singular.  Use the
        // finite cap area and the flux crossing its 10-degree boundary.
        double capArea = 2.0 * Math.PI * R * R * (1.0 - Math.cos(Math.toRadians(10.0)));
        q[0] = -flux[1] / capArea;
        q[n - 1] = flux[n - 2] / capArea;
        return q;
    }
}