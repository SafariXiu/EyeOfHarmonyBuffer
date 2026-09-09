package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * 群系气候坐标（L1c 的输入层，B1）：把环流层（M1–M6）的物理量收口成
 * **两个有物理含义的坐标 + 内陆度**，供 V2BiomeSelect 做气候带分类。
 *
 *   tempEff  = 纬度带 + 气团温度 + 气团签名 − 海拔直减
 *   moistEff = 空气湿度 + 海洋性 + 动力干湿 + 迎风抬升雨 − 背风焚风
 *              + 寒/暖流海岸 + 气团签名 − 大陆性干燥
 *   continent= 内陆度（海岸距离归一）
 *
 * 与 GlobalClimate 的 P1b 共用同一套算子口径（UPLIFT_SCALE / 风×海拔梯度），
 * 因此群系与 /talosmap、降水场不会口径漂移。**不读最终高度** → 无循环依赖。
 *
 * 三个物理项可单独开关（探针逐项对照用）：
 *   ENABLE_ORO（风带 + 地形雨） / ENABLE_SST（洋流·海温海岸） / ENABLE_AIRMASS（气团签名）
 */
public final class ClimateCoords {

    private ClimateCoords() {}

    // ===== 开关 =====
    public static boolean ENABLE_ORO = true;
    public static boolean ENABLE_SST = true;
    public static boolean ENABLE_AIRMASS = true;

    // ===== 系数（探针可扫参） =====
    /** 温度 = W_LAT·纬度带 + (1−W_LAT)·气团温度。 */
    public static double W_LAT = 0.70;
    /** 海拔直减（× elevation01，L1b 类型量）。 */
    public static double LAPSE = 0.55;
    /** 风×海拔梯度的归一标尺（与 GlobalClimate.UPLIFT_SCALE 一致）。 */
    public static double UPLIFT_SCALE = 1.8e-5;
    /** 迎风抬升 → 增湿 / 背风焚风 → 减湿。 */
    public static double ORO_UP_GAIN = 0.30, ORO_LEE_GAIN = 0.18;
    /** 湿度坐标零点偏移（只做整体平移，不改物理结构；用于把中位数落在 0.45 附近）。 */
    public static double MOIST_OFFSET = 0.25;
    /** 海温距平 → 增/减湿。 */
    public static double SST_GAIN = 0.90;
    /** 上风海点取样距离（blocks）。 */
    public static double SST_OFFSET = 60_000.0;
    /** 大陆性干燥。 */
    public static double INLAND_SCALE = 40_000.0, INLAND_DRY = 0.12;
    /** 梯度差分步长（blocks）。 */
    public static double GRAD_STEP = 6_000.0;
    /** 气团签名：Δ温度 / Δ湿度（0=mT 1=cT 2=mP 3=cP）。 */
    public static double[] AIR_DT = {0.05, 0.08, -0.05, -0.08};
    public static double[] AIR_DQ = {0.08, -0.06, 0.05, -0.05};

    /** 纬度带海温期望（实测 10 档均值，用于算洋流距平）。 */
    private static final double[] SST_BY_BAND = {
        0.281, 0.325, 0.365, 0.345, 0.190, 0.013, -0.120, -0.203, -0.317, -0.406
    };

    /** 一次采样的结果。 */
    public static final class Coords {
        public double temp, moist, continent;
        // ---- 诊断 / 出图 ----
        public double windX, windZ;
        public double up, lee, sstAnom;
        public double dry, q, mar, airT, bandD;
        /** 0=mT 1=cT 2=mP 3=cP。 */
        public int airMass;
        public boolean onshore;
    }

    /** 单点采样（陆地；海上只需 temp/moist 的话也可调用）。 */
    public static Coords sample(int x, int z, int worldSeedInt, OrographyField.OroSample oro) {
        Coords c = new Coords();

        // ---- 环流场（4 次查表） ----
        double p = RelaxedClimate.samplePressure(x, z, worldSeedInt);
        double airT = RelaxedClimate.sampleAirTemp(x, z, worldSeedInt);
        double q = RelaxedClimate.sampleHumidity(x, z, worldSeedInt);
        double mar = RelaxedClimate.sampleMaritime(x, z, worldSeedInt);
        double dry = clamp01(0.5 + p / 3.2);
        double bandD = GlobalCirculation.bandD(z);
        c.dry = dry; c.q = q; c.mar = mar; c.airT = airT; c.bandD = bandD;

        double temp = W_LAT * (1.0 - bandD) + (1.0 - W_LAT) * (0.5 + 0.5 * airT);
        double moist = MOIST_OFFSET + 0.55 * q + 0.25 * mar + 0.20 * (1.0 - dry);
        c.continent = clamp01(-oro.coastDist / INLAND_SCALE);

        // ---- 气团签名（M3 产物） ----
        boolean maritime = mar >= 0.5;
        boolean tropical = airT >= 0.0;
        c.airMass = maritime ? (tropical ? 0 : 2) : (tropical ? 1 : 3);
        if (ENABLE_AIRMASS) {
            temp += AIR_DT[c.airMass];
            moist += AIR_DQ[c.airMass];
        }

        // ---- 风带 + 地形雨（1 次风查表 + 4 次 elevation01 差分） ----
        double[] wind = RelaxedClimate.sampleWind(x, z, worldSeedInt);
        c.windX = wind[0];
        c.windZ = wind[1];
        double sp = Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1]);
        if (ENABLE_ORO && oro.isLand && sp > 1.0e-6) {
            double ux = wind[0] / sp, uz = wind[1] / sp;
            int s = (int) GRAD_STEP;
            double gx = (elev01(x + s, z, worldSeedInt) - elev01(x - s, z, worldSeedInt)) / (2.0 * s);
            double gz = (elev01(x, z + s, worldSeedInt) - elev01(x, z - s, worldSeedInt)) / (2.0 * s);
            double dot = ux * gx + uz * gz;
            c.up = clamp01(Math.max(0.0, dot) / UPLIFT_SCALE);
            c.lee = clamp01(Math.max(0.0, -dot) / UPLIFT_SCALE);
            moist += ORO_UP_GAIN * c.up * q - ORO_LEE_GAIN * c.lee * q;
        }

        // ---- 洋流 / 海温海岸（上风方向的海点距平） ----
        if (ENABLE_SST && oro.isLand && sp > 1.0e-6) {
            int px = x + (int) (wind[0] / sp * SST_OFFSET);
            int pz = z + (int) (wind[1] / sp * SST_OFFSET);
            if (NoiseContinentGrid.landResidual(px, pz, worldSeedInt) < 0.0) {
                double sst = RelaxedClimate.sampleSst(px, pz, worldSeedInt);
                if (!Double.isNaN(sst)) {
                    c.sstAnom = sst - sstExpectation(GlobalCirculation.bandD(pz));
                    c.onshore = true;
                    moist += SST_GAIN * c.sstAnom;
                }
            }
        }

        // ---- 大陆性干燥 ----
        moist -= INLAND_DRY * c.continent * (1.0 - 0.5 * mar);

        c.temp = clamp01(temp - LAPSE * oro.elevation01);
        c.moist = clamp01(moist);
        return c;
    }

    /** 纬度带海温期望（线性插值实测 10 档）。 */
    public static double sstExpectation(double bandD) {
        double t = clamp01(bandD) * (SST_BY_BAND.length - 1);
        int i = (int) t;
        if (i >= SST_BY_BAND.length - 1) {
            return SST_BY_BAND[SST_BY_BAND.length - 1];
        }
        double f = t - i;
        return SST_BY_BAND[i] * (1.0 - f) + SST_BY_BAND[i + 1] * f;
    }

    private static double elev01(int x, int z, int worldSeedInt) {
        return OrographyField.elevation01(NoiseContinentGrid.landResidual(x, z, worldSeedInt), worldSeedInt);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
