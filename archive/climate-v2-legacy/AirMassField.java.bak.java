package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;

/**
 * 气团层（与海陆分布同层）。
 *
 * 气团 = 由源地上垫面（海陆）+ 纬度（冷热）决定的大范围均匀空气。
 * 判定：isLand（海/陆） × bandD（冷/热） → 4 类标准气团。
 *
 * 输出：
 *   - type        : cP / mP / mT / cT（带标签）
 *   - temperature : [-1,1]（热带暖 +1，极地冷 -1，连续）
 *   - humidity    : [0,1]（海洋湿，大陆干；热带更湿）
 *
 * 输入：NoiseContinentGrid.isLand（海陆，V2 噪声场）+ GlobalCirculation.bandD（纬度带）；
 *       与海陆层同层（S4 定稿：简单分类版；"气团种子→地转风"方案已废弃，
 *       见 docs/TerrainV2/design.md 决策 D21），输出供 L5 群系与洋流（S6.1）消费。
 */
public final class AirMassField {

    private AirMassField() {}

    /** 气团采样结果。 */
    public static final class AirMassSample {
        public final AirMassType type;
        public final double temperature;   // [-1,1]
        public final double humidity;      // [0,1]
        AirMassSample(AirMassType type, double temperature, double humidity) {
            this.type = type; this.temperature = temperature;
            this.humidity = humidity;
        }
        @Override public String toString() {
            return String.format("AirMass[%s %s t=%.2f h=%.2f]", type.label, type.code, temperature, humidity);
        }
    }

    /**
     * 采样某点的气团。
     *
     * @param x,z            世界 block 坐标
     * @param worldSeedInt   世界种子
     * @return 气团（type + 温度 + 湿度）
     */
    public static AirMassSample sample(int x, int z, int worldSeedInt) {
        return sample(x, z, worldSeedInt, NoiseContinentGrid.isLand(x, z, worldSeedInt));
    }

    /**
     * 采样某点的气团（调用方已算好 isLand 时用，省一次海陆采样）。
     */
    public static AirMassSample sample(int x, int z, int worldSeedInt, boolean isLand) {
        // 纬度带：0=热(赤道) 1=冷(极地)，用最近的纬度带插值温度
        double b = GlobalCirculation.bandD(z);
        // 温度：热带暖(+1) → 极地冷(-1)
        double temperature = 1.0 - 2.0 * b;
        // 湿度：海洋湿、大陆干；再乘纬度（热带偏湿）。陆地干一些，海洋比陆地湿。
        double latHumidity = 1.0 - b;    // 热更湿
        double humidity = isLand ? (0.35 * latHumidity) : (0.85 * latHumidity);
        if (humidity > 1.0) humidity = 1.0;
        if (humidity < 0.0) humidity = 0.0;

        // 类型：热/冷 × 海/陆。
        // 热/冷分界取 b<0.5（≈纬度 45°）：静态模型把中纬大陆按"冬季极地气团南侵"归 cP、
        // 低纬大陆归 cT（夏季地中海/中亚式 cT 南下由温度/湿度连续量表达，类型标签只是主导态）。
        boolean tropical = b < 0.5;
        boolean maritime = !isLand;
        AirMassType type;
        if (maritime && tropical) {
            type = AirMassType.MARITIME_TROPICAL;      // mT 热带海洋
        } else if (maritime) {
            type = AirMassType.MARITIME_POLAR;          // mP 极地海洋
        } else if (tropical) {
            type = AirMassType.CONTINENTAL_TROPICAL;    // cT 热带大陆
        } else {
            type = AirMassType.CONTINENTAL_POLAR;        // cP 极地大陆
        }
        return new AirMassSample(type, temperature, humidity);
    }

    /**
     * 仅类型（供快速分类 / 出图）。
     */
    public static AirMassType typeAt(int x, int z, int worldSeedInt) {
        return sample(x, z, worldSeedInt).type;
    }
}
