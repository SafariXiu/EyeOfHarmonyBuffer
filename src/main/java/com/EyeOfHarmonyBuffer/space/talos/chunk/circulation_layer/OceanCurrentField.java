package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;

/**
 * 洋流层（S6 · 结构原型）。
 *
 * 权威因果链（docs/TerrainV2/design.md D16 / 十c）：洋流 = 风驱动 × 大陆折射。
 *   - 风应力：表层流方向 ≈ 风的方向（信风带往西、西风带往东）——已接 GlobalCirculation.windDir；
 *   - 科里奥利：表层流在风向右方偏转（北半球），占位用固定 14° 旋转近似；
 *   - 大陆折射 → 环流圈 / 东岸暖西岸冷：**S6.1 再做**（需要海岸方向场 / 大陆轮廓，
 *     当前海陆 = NoiseContinentGrid 噪声场，折射尚未实现）。
 *
 * 输出：
 *   - flowX/flowZ   : 洋流方向（单位向量）
 *   - temperature   : 海温 [-1,1]，当前 = 纬度基准（1 - 2·bandD）；折射带来的暖寒流色差 S6.1 接
 *   - speed         : [0,1] 流速（风应力强度 = 驱动风矢量模长，截断）
 *
 * 注意：本层只对海上点有物理意义；陆地点的采样值未定义（调用方先判 isLand，见
 * GlobalClimate / /talosmap）。旧版含一段"沿岸 ±0.1/-0.05 修正"，其窗口用的是
 * 噪声高度残差而非块距离（全图恒真），且无方向依据，已删除。
 */
public final class OceanCurrentField {

    private OceanCurrentField() {}

    /** 科里奥利偏转角（弧度 ≈ 14°）：表层流相对风向右偏（N）/ 左偏（S）。 */
    private static final double CORIOLIS_TURN = 0.24;

    /** 洋流采样结果。 */
    public static final class CurrentSample {
        public final double flowX;       // 洋流方向（单位）
        public final double flowZ;
        public final double temperature; // [-1,1] 海温（纬度基准，S6.1 接折射修正）
        public final double speed;       // [0,1] 流速（风应力强度）

        CurrentSample(double fx, double fz, double temp, double speed) {
            this.flowX = fx;
            this.flowZ = fz;
            this.temperature = temp;
            this.speed = speed;
        }

        @Override
        public String toString() {
            return String.format("Current[(%.2f,%.2f) t=%.2f v=%.2f]", flowX, flowZ, temperature, speed);
        }
    }

    /**
     * 采样某点洋流（仅海上点调用）。
     */
    public static CurrentSample sample(int x, int z, int worldSeedInt) {
        // 1) 驱动风（三圈基底 + 15° 斜向 + 弱扰动，见 GlobalCirculation.windDir）
        double[] wind = GlobalCirculation.windDir(x, z, worldSeedInt);
        double wlen = Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1]);

        // 2) 科里奥利偏转：风矢量旋转 ∓CORIOLIS_TURN（北半右偏 = 顺时针、南半左偏 = 逆时针）。
        //    注意这里是"表层流方向"的简化占位；真实为埃克曼螺线积分 + 大陆折射（S6.1）。
        double sign = (GlobalCirculation.foldZ(z) <= ClimateLatitudes.MAX_D) ? 1.0 : -1.0;
        double cosT = Math.cos(CORIOLIS_TURN), sinT = Math.sin(CORIOLIS_TURN);
        double fx = wind[0] * cosT + sign * wind[1] * sinT;
        double fz = -sign * wind[0] * sinT + wind[1] * cosT;
        double flen = Math.sqrt(fx * fx + fz * fz);
        if (flen > 1.0e-9) {
            fx /= flen;
            fz /= flen;
        } else {
            fx = wind[0];
            fz = wind[1];
        }

        // 3) 海温：纬度基准（热带暖 +1 → 寒带冷 -1）；沿岸暖寒流修正 S6.1（大陆折射后）
        double temperature = 1.0 - 2.0 * GlobalCirculation.bandD(z);
        if (temperature < -1) temperature = -1;
        if (temperature > 1) temperature = 1;

        // 4) 流速：驱动风模长截断 [0,1]（基底权重 1.0，通常 ≈0.8~1.3）
        double speed = wlen;
        if (speed < 0) speed = 0;
        if (speed > 1) speed = 1;

        return new CurrentSample(fx, fz, temperature, speed);
    }
}
