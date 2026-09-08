package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

/**
 * @deprecated 解析链（M3）已被 RelaxedClimate 的空气场网格解取代（本类不再被运行时引用，保留作对照/调参参考）。
 * M3 气团输运（风场 v2 第三模块）。
 *
 * 真实机制复刻：某点的"平均空气" = 沿盛行风回溯的源地性质 + 沿途与下垫面交换后的状态。
 * 实现为**稳态上游采样**（无时间演化、确定性 O(N)）：
 *   从查询点沿 PressureField 风场逆风向走 N 步（步长 ~风速×DT），
 *   在端点初始化平衡态，再顺次向下游弛豫：
 *     - 温度 → 弛豫到 Teq（海/陆各自的平衡温；特征长度 L_T）；
 *     - 湿度 → 海上弛豫向"该海温的饱和湿度"(克劳修斯-克拉佩龙近似：qSat 随 Teq 升)，
 *              陆上弛豫向"干旱平衡"(特征长度 L_DRYOUT 长 → 大陆性可深入内陆)；
 *     - maritime01 → 海洋性记忆（0=典型大陆气团 1=典型海洋气团）。
 *
 * 产出效果（涌现，不预设）：
 *   - 迎风海岸的湿舌、背风侧干区（雨影前身）；
 *   - 冷洋流沿岸的稳定干空气（海岸沙漠的空气侧成因）；
 *   - 大陆内部干化路径感；暖池上的最湿空气。
 *
 * 纯函数 O(N)：N=5 步 × 每步 1 次 PressureField.windDir（≈5 次气压差分采样）。
 * 若嫌贵：N 可降到 3、DT 可加大；M6 查表化后本类作为"离线求解算子"复用。
 */
public final class AdvectedAirField {

    private AdvectedAirField() {}

    /** 回溯步数。 */
    private static final int STEPS = 7;
    /** 步长（block）；记忆范围 ≈ STEPS×DT ≈ 110k（大陆穿越级）。 */
    private static final double DT = 16_000.0;
    /** 温度弛豫特征长度（block）。 */
    private static final double L_TEMP = 40_000.0;
    /** 海上增湿特征长度（暖洋蒸发快）。 */
    private static final double L_MOIST = 22_000.0;
    /** 陆上干化特征长度（长 → 大陆度可深入内陆 ~100k）。 */
    private static final double L_DRYOUT = 70_000.0;
    /** 海洋性记忆特征长度（长 → 迎风岸走廊清晰）。 */
    private static final double L_MARITIME = 90_000.0;

    /** 采样结果。 */
    public static final class AirSample {
        /** 气团温度 [-1,1]（沿路径弛豫到 Teq 后）。 */
        public final double temperature;
        /** 空气湿度 [0,1]（海上→近饱和，陆上→干）。 */
        public final double humidity;
        /** 海洋性记忆 [0,1]：1=海洋气团 0=大陆气团。 */
        public final double maritime01;
        /** 回溯最远点的平衡温度（源地性质参考）。 */
        public final double sourceTemp;

        AirSample(double temperature, double humidity, double maritime01, double sourceTemp) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.maritime01 = maritime01;
            this.sourceTemp = sourceTemp;
        }

        @Override
        public String toString() {
            return String.format("Air[t=%.2f q=%.2f m=%.2f src=%.2f]",
                temperature, humidity, maritime01, sourceTemp);
        }
    }

    /** 单点采样（世界 block 坐标，任意范围；海陆皆可）。 */
    public static AirSample sample(int x, int z, int worldSeedInt) {
        // ---- 上游轨迹：从查询点逆风收集路径点 ----
        double px = x, pz = z;
        double[][] path = new double[STEPS][2];   // [0]=最近 ... [STEPS-1]=最远
        path[0][0] = px;
        path[0][1] = pz;
        for (int i = 1; i < STEPS; i++) {
            double[] w = PressureField.windDir((int) px, (int) pz, worldSeedInt);
            double sp = Math.sqrt(w[0] * w[0] + w[1] * w[1]);
            // 逆风移动：沿风向的反方向走固定距离 DT（方向为准）
            if (sp > 1.0e-6) {
                px -= (w[0] / sp) * DT;
                pz -= (w[1] / sp) * DT;
            }
            path[i][0] = px;
            path[i][1] = pz;
        }
        // ---- 端点初始化：源地为当地平衡态 ----
        int sx = (int) path[STEPS - 1][0];
        int sz = (int) path[STEPS - 1][1];
        double srcTeq = ThermalForcing.teq(sx, sz, worldSeedInt);
        double t = srcTeq;
        double q = equilibriumHumidity(sx, sz, worldSeedInt, srcTeq);
        double m = NoiseContinentGrid.landResidual(sx, sz, worldSeedInt) >= 0.0 ? 0.0 : 1.0;
        // ---- 顺流弛豫（从最远向查询点走回） ----
        for (int i = STEPS - 2; i >= 0; i--) {
            int cx = (int) path[i][0];
            int cz = (int) path[i][1];
            double teq = ThermalForcing.teq(cx, cz, worldSeedInt);
            boolean land = NoiseContinentGrid.landResidual(cx, cz, worldSeedInt) >= 0.0;
            double ft = 1.0 - Math.exp(-DT / L_TEMP);
            t += (teq - t) * ft;
            double qEq = equilibriumHumidity(cx, cz, worldSeedInt, teq);
            double Lq = land ? L_DRYOUT : L_MOIST;
            double fq = 1.0 - Math.exp(-DT / Lq);
            q += (qEq - q) * fq;
            double targetM = land ? 0.0 : 1.0;
            double fm = 1.0 - Math.exp(-DT / L_MARITIME);
            m += (targetM - m) * fm;
        }
        // 查询点本体也做一次交换（近地面最终平衡）
        double teq0 = ThermalForcing.teq(x, z, worldSeedInt);
        boolean land0 = NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0;
        t += (teq0 - t) * (1.0 - Math.exp(-DT / L_TEMP));
        double qEq0 = equilibriumHumidity(x, z, worldSeedInt, teq0);
        double Lq0 = land0 ? L_DRYOUT : L_MOIST;
        q += (qEq0 - q) * (1.0 - Math.exp(-DT / Lq0));
        m += ((land0 ? 0.0 : 1.0) - m) * (1.0 - Math.exp(-DT / L_MARITIME));
        return new AirSample(clamp(t, -1, 1), clamp01(q), clamp01(m), srcTeq);
    }


    /** 当地平衡湿度：海 = 饱和(随 Teq 升)，陆 = 干旱平衡。 */
    private static double equilibriumHumidity(int x, int z, int worldSeedInt, double teq) {
        boolean land = NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0;
        if (land) {
            double b = GlobalCirculation.bandD(z);
            return 0.05 + 0.20 * ThermalForcing.insolation01(b);
        }
        return 0.60 + 0.35 * teq;   // 饱和近似：暖海高、冷海低（Clausius-Clapeyron 线性化）
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
