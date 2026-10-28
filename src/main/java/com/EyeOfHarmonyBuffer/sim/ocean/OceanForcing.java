package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.Frozen;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.ThermalWind;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★ 2026-10-20：海洋表面热通量（诊断用，供 ClimberOcean 的 flux 边界）。
 *
 * <p>全部走模型自己的公式，不引观测：
 * <pre>
 *   Q_net = Radiation.residual(sst, asr, ta, qa, chv, beta=1)
 *   asr   = Radiation.absSolarSurface(lat, dec, albedo(false, sst, lat))   // 海表反照率
 *   ta    = Atmosphere.surfaceTemp(x, z, seed, cell, theta)               // 海表气温
 *   qa    = RH_SEA * qSat(ta)                                             // 海面比湿
 *   chv   = Radiation.bulkCoeff(kappa=0, |wind|)                          // 海面交换系数
 *   dec   = Atmosphere.subsolarLat(theta)
 * </pre>
 * 正 = 进入海洋（加热）。
 */
@Frozen(reason = "重构冻结：2.5D 海洋表面通量等待 3D 海洋替换", until = "refactor: 3D ocean")
public final class OceanForcing {

    private OceanForcing() {}

    /**
     * 某纬带的海洋平均净热通量（W/m²，正=进入海洋）。
     *
     * @param seed   世界种子（{@code SimTerrain.seedOf}）
     * @param cell   格点步长（与探针的 Zonal.CELL 一致）
     * @param grad   梯度步长（Zonal.GRAD）
     * @param xspan  采样经向跨度（Zonal.XSPAN）
     * @param band   0..17（纬度 -85..85，10°）
     * @param theta  季节相位
     * @param sstK   该带的当前海表温度（K）
     */
    public static double surfaceFluxWm2(long seed, int cell, int grad, double xspan,
                                        int band, double theta, double sstK) {
        return surfaceFluxWm2(seed, cell, grad, xspan, band, theta, sstK, 0.0);
    }

    /**
     * 同上，但按海冰密集度 {@code iceFrac} 混合反照率（冰 0.62，Stocker 1992 Table 1 的 AI）。
     * 冰密集度与 SST 的关系（Petoukhov et al. 2000 §5）：0 °C 出现冰，−1.8 °C 全覆盖，线性。
     */
    public static double surfaceFluxWm2(long seed, int cell, int grad, double xspan,
                                        int band, double theta, double sstK, double iceFrac) {
        double latDeg = -85.0 + band * 10.0;
        double lat = Math.toRadians(latDeg);
        int z = WorldContract.zOfLat(latDeg);
        double dec = Atmosphere.subsolarLat(theta);
        double f = Math.max(0.0, Math.min(1.0, iceFrac));
        double sum = 0.0;
        int n = 0;
        for (int c = 0; c < 4; c++) {
            int x = (int) Math.round((c + 0.5) * xspan / 4.0);
            if (PlateField.isLandWithCell(x, z, seed, cell)) continue;
            double ta = Atmosphere.surfaceTemp(x, z, seed, cell, theta);
            double qa = PrecipField.RH_SEA * PrecipField.qSat(ta);
            double[] uv = Atmosphere.windAt(x, z, seed, cell, theta, grad);
            double wind = Math.hypot(uv[0], uv[1]);
            // ★ 2026-10-20（EDDY_WIND）：只作用于海洋强迫；涡动地面风叠加到交换系数用的风速上
            if (Atmosphere.EDDY_WIND) wind += ThermalWind.eddyWind(lat, theta)[1];
            double chv = Radiation.bulkCoeff(0.0, wind);
            double albWater = Radiation.albedo(false, sstK, lat);
            double alb = (1.0 - f) * albWater + f * 0.62;   // 冰反照率 = Table 1 的 AI
            double asr = Radiation.absSolarSurface(lat, dec, alb);
            sum += Radiation.residual(sstK, asr, ta, qa, chv, 1.0);
            n++;
        }
        return n > 0 ? sum / n : 0.0;
    }
}
