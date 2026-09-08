package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * V2 统一气候采样门面（docs/TerrainV2/design.md 二c：对外唯一 API）。
 *
 * 数据源 = M5/M6 RelaxedClimate（按种子离线松弛求解 + 双线性查表），叠加：
 *   - L1 海陆（NoiseContinentGrid，块级海岸距离）
 *   - L1b 地形算子（P1b）：迎风坡抬升 → 额外降水；背风坡下沉 → 焚风减雨；
 *     辐合（-∇·v）→ 对流性降水；气压异常 → 干湿（高压=干）
 *
 * 生产消费方（L5 群系 / 水体 / 地形增强）与 /talosmap 统一走本门面。
 */
public final class GlobalClimate {

    private GlobalClimate() {}

    // ---- P1b 地形算子参数（尺度归一，出图后微调） ----
    /** 风场差分/辐合采样步长（block）。 */
    private static final int DIFF_STEP = 6000;
    /** 辐合归一标尺（实测 |div| p50≈5e-5/p90≈1.6e-4 → 取 1.0e-4 使 p90≈饱和 1.0）。 */
    private static final double CONV_SCALE = 1.0e-4;
    /** 地形抬升坡度归一标尺（实测坡向点积 p90≈1.4e-5 → 取 1.8e-5）。 */
    private static final double UPLIFT_SCALE = 1.8e-5;
    /** 海拔梯度采样步长（block）。 */
    private static final int ELEV_STEP = 8000;

    /**
     * 单点完整气候采样（x,z 任意范围）。
     */
    public static ClimateSample sample(int x, int z, int worldSeedInt) {
        // ---- L1 海陆（一次采样，各场复用） ----
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        boolean isLand = r >= 0.0;
        double coastDist = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);

        // ---- M5/M6 松弛气候场（双线性查表，O(1)） ----
        double bandD = GlobalCirculation.bandD(z);
        double[] wind = RelaxedClimate.sampleWind(x, z, worldSeedInt);
        double p = RelaxedClimate.samplePressure(x, z, worldSeedInt);
        double airT = RelaxedClimate.sampleAirTemp(x, z, worldSeedInt);
        double q = RelaxedClimate.sampleHumidity(x, z, worldSeedInt);
        double mar = RelaxedClimate.sampleMaritime(x, z, worldSeedInt);
        double sst = isLand ? Double.NaN : RelaxedClimate.sampleSst(x, z, worldSeedInt);
        double[] cur = isLand ? null : RelaxedClimate.sampleCurrent(x, z, worldSeedInt);

        // ---- 干湿：高压=干（动力下沉骨架），低压=湿 ----
        double dry = clamp01(0.5 + p / 3.2);

        // ---- P1b 降水：湿度 ×（对流辐合 + 地形抬升 − 焚风） ----
        double conv = -divergence(x, z, worldSeedInt);
        double convN = clamp01(conv / CONV_SCALE);
        // 下沉抑制：副热带高压/极地高压下沉带削弱对流雨（动力干燥骨架）
        double subsidence = clamp01((p - 0.15) / 1.2);
        double convEff = convN * (1.0 - 0.85 * subsidence);
        double[] oro = slopeAlongWind(x, z, worldSeedInt, wind);
        double upliftN = clamp01(Math.max(0.0, oro[0]) / UPLIFT_SCALE);   // 迎风抬升
        double downN = clamp01(Math.max(0.0, -oro[0]) / UPLIFT_SCALE);    // 背风下沉
        double lee = downN * (isLand ? 1.0 : 0.0);                        // 陆上才有焚风减雨
        double rain = q * (0.30 + 1.6 * convEff) + 2.0 * upliftN * q * (isLand ? 1.0 : 0.0);
        rain = rain * (1.0 - 0.5 * lee);
        rain = clamp01(rain);

        // ---- 气团标签（溯源派生：海洋性 × 冷热） ----
        AirMassType type;
        boolean tropical = airT >= 0.0;
        if (mar >= 0.5) {
            type = tropical ? AirMassType.MARITIME_TROPICAL : AirMassType.MARITIME_POLAR;
        } else {
            type = tropical ? AirMassType.CONTINENTAL_TROPICAL : AirMassType.CONTINENTAL_POLAR;
        }

        double gyreBase = 0.5 - bandD;
        double gyre = gyreBase < -1 ? -1 : (gyreBase > 1 ? 1 : gyreBase);

        return new ClimateSample(
            isLand, coastDist,
            bandD, wind[0], wind[1],
            dry, rain, null, gyre,
            type, airT, q,
            cur != null ? cur[0] : 0.0, cur != null ? cur[1] : 0.0,
            sst, cur != null ? 0.5 : 0.0
        );
    }

    /** 风场散度 ∂u/∂x + ∂v/∂z（每 block）。 */
    private static double divergence(int x, int z, int worldSeedInt) {
        int e = DIFF_STEP;
        double[] wp = RelaxedClimate.sampleWind(x + e, z, worldSeedInt);
        double[] wm = RelaxedClimate.sampleWind(x - e, z, worldSeedInt);
        double[] wzp = RelaxedClimate.sampleWind(x, z + e, worldSeedInt);
        double[] wzm = RelaxedClimate.sampleWind(x, z - e, worldSeedInt);
        return (wp[0] - wm[0]) / (2.0 * e) + (wzp[1] - wzm[1]) / (2.0 * e);
    }

    /**
     * 风沿海拔梯度的投影 [dotSlope, relief01]：>0 迎风抬升、<0 背风下沉。
     */
    private static double[] slopeAlongWind(int x, int z, int worldSeedInt, double[] wind) {
        int e = ELEV_STEP;
        double sp = Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1]);
        if (sp < 1.0e-6) {
            return new double[] { 0.0, 0.0 };
        }
        double ux = wind[0] / sp, uz = wind[1] / sp;
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        double elev = OrographyField.elevation01(r, worldSeedInt);
        if (r < 0.0) {
            return new double[] { 0.0, 0.0 };   // 海上不适用地形项
        }
        double ep = elevOf(x + e, z, worldSeedInt);
        double em = elevOf(x - e, z, worldSeedInt);
        double ezp = elevOf(x, z + e, worldSeedInt);
        double ezm = elevOf(x, z - e, worldSeedInt);
        double gx = (ep - em) / (2.0 * e);
        double gz = (ezp - ezm) / (2.0 * e);
        return new double[] { ux * gx + uz * gz, 0.0 };
    }

    private static double elevOf(int x, int z, int worldSeedInt) {
        double rr = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        return OrographyField.elevation01(rr, worldSeedInt);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
