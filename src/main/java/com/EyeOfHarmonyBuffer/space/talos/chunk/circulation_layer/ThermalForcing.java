package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;

/**
 * M1 热力配方场（风场 v2 的"热力底座"，B 序第一模块；C 离线松弛与 M2 气压场复用本类）。
 *
 * 因果链第一环的真实表达：太阳辐射 → 赤道-极地温差 → 各处的"平衡表面温度"，
 * 海陆与地形只负责在平衡场上做调制（不直接产风/产雨，那是 M2/M3 的事）。
 *
 * 约定：温度沿用全项目 [-1,1]（+1=赤道暖池、-1=极寒），bandD∈[0,1]（0=赤道、1=极地）。
 *
 *   - insolation01(bandD)  太阳辐射曲线 [0,1]（赤道 1 → 极地 0）
 *   - zonalMeanSeaTeq(b)   纬向平均"海洋平衡温"：无洋流输运时的 SST 骨架基线
 *   - seaTeq(x,z)          SST 骨架 = 纬向平均 + 大尺度暖池/冷池摆动（洋流输运修正 = M4/M5）
 *   - landTeq(x,z)         陆地平衡温 = 同纬海温基 + 大陆性偏置(内陆强、近岸弱→等温线贴岸)
 *                           - 海拔直减(elevation01×直减率)
 *   - teq(x,z)             isLand 分流
 *
 * 状态说明：当前为**夏季态占位**（陆地偏置取正号：热带/中纬内陆比同纬海暖），
 * 季节相位（M7/D）只需把 CONTINENTAL_BIAS 变成 ± 相位函数即可，本类签名不变。
 * 纯函数 O(1)：约 1 次海陆残差 + 1 次带通摆动 + (陆地)1 次 coastDistBlocks。
 */
public final class ThermalForcing {

    private ThermalForcing() {}

    /** 大尺度摆动波长（block）：400k 域内 ~4 个暖池/冷池。 */
    private static final double WOBBLE_WAV = 100_000.0;
    /** 摆动盐（与海陆场去相关）。 */
    private static final long WOBBLE_SALT = 0x5EED_2026L;
    /** 摆动振幅（[-1,1] 温标；需大于纬向梯度才能形成闭合暖池/冷舌）。 */
    private static final double WOBBLE_AMP = 0.30;
    /** 大陆性尺度（block）：离岸超过它视为"深内陆"。 */
    private static final double LAND_CONTINENTAL_SCALE = 45_000.0;
    /** 大陆性偏置幅度（夏季态；季风驱动力 ∝ 这个 × 内陆度 × 太阳辐射）。 */
    private static final double CONTINENTAL_BIAS = 0.14;
    /** 海拔直减（作用在 elevation01 的平方上：普通内陆少降、深高原才显著）。 */
    private static final double ELEV_LAPSE = 0.24;

    /** 太阳辐射曲线 [0,1]：赤道 1 → 极地 0（cos 平滑，无硬带）。 */
    public static double insolation01(double bandD) {
        double b = bandD < 0.0 ? 0.0 : (bandD > 1.0 ? 1.0 : bandD);
        return Math.cos(b * Math.PI / 2.0);
    }

    /** 纬向平均海洋平衡温 [-1,1]（= 2·insolation - 1）。 */
    public static double zonalMeanSeaTeq(double bandD) {
        return insolation01(bandD) * 2.0 - 1.0;
    }

    /** 大尺度暖池/冷池摆动（长波，去相关盐）。 */
    private static double wobble(int x, int z, int worldSeedInt) {
        double n = NoiseContinentGrid.bandNoise(x, z, worldSeedInt, WOBBLE_SALT, 1.0 / WOBBLE_WAV, 2);
        return (n * 2.0 - 1.0) * WOBBLE_AMP;
    }

    /** SST 骨架 [-1,1]（海上平衡温；洋流输运修正见 M4/M5）。 */
    public static double seaTeq(int x, int z, int worldSeedInt) {
        double b = GlobalCirculation.bandD(z);
        double t = zonalMeanSeaTeq(b) + wobble(x, z, worldSeedInt);
        return clamp(t, -1.0, 1.0);
    }

    /** 陆地平衡温 [-1,1]（与海共用同一摆动基线 → 海陆差纯粹 = 大陆度 - 高原地形降温）。 */
    public static double landTeq(int x, int z, int worldSeedInt) {
        double b = GlobalCirculation.bandD(z);
        double base = zonalMeanSeaTeq(b) + wobble(x, z, worldSeedInt);
        // 内陆度：贴岸≈0（等温线贴岸弯曲）、深内陆≈1
        double d = NoiseContinentGrid.coastDistBlocks(x, z, worldSeedInt);   // 陆上 <0
        double inland = clamp01((-d) / LAND_CONTINENTAL_SCALE);
        // 大陆性偏置（夏季态）：辐射强处更热；内陆满幅、近岸消失
        double bias = CONTINENTAL_BIAS * inland * (0.4 + 0.6 * insolation01(b));
        // 地形降温：只惩罚"真高"（高原/山地 kind，用 elevation01 的平方）——
        // 平原内陆不该被冻（elevation01 是内陆深度代理，不是海拔）
        double r = NoiseContinentGrid.landResidual(x, z, worldSeedInt);
        double elev = OrographyField.elevation01(r, worldSeedInt);
        int kind = OrographyField.sample(x, z, worldSeedInt).kind;
        double terrain = 0.0;
        if (kind == OrographyField.KIND_PLATEAU) {
            terrain = ELEV_LAPSE * elev * elev;
        } else if (kind == OrographyField.KIND_MOUNTAIN || kind == OrographyField.KIND_PEAK) {
            terrain = ELEV_LAPSE * 0.5 * elev;
        }
        double t = base + bias - terrain;
        return clamp(t, -1.0, 1.0);
    }

    /** 平衡表面温度（海陆分流）。 */
    public static double teq(int x, int z, int worldSeedInt) {
        return NoiseContinentGrid.landResidual(x, z, worldSeedInt) >= 0.0
            ? landTeq(x, z, worldSeedInt)
            : seaTeq(x, z, worldSeedInt);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
