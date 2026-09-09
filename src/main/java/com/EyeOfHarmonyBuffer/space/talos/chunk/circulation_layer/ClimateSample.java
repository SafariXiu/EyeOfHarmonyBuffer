package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType;

/**
 * V2 统一气候采样结果（docs/TerrainV2/design.md 二c / climate-layer-internals.md）。
 *
 * 一次编排：L1 海陆 + 耦合气候场（RelaxedClimate 查表）+ P1b 地形降水算子。
 * 所有值确定性纯函数。消费方（L5 群系 / 水体 / 地形增强）与出图统一走
 * {@link GlobalClimate}，不要散调内部场，避免口径漂移。
 */
public final class ClimateSample {

    // ---- L1 海陆（NoiseContinentGrid · 逐 block 解析） ----
    /** 是否陆地。 */
    public final boolean isLand;
    /** 有符号海岸距离（block）：&lt;0 内陆 / ≈0 岸线 / &gt;0 海上。远场截断 ±200k。 */
    public final double coastDist;

    // ---- 纬度带 ----
    /** 纬度带：0=赤道中线，1=寒带中线。 */
    public final double bandD;

    // ---- 耦合气候场（RelaxedClimate 网格解 · 双线性查表） ----
    /** 地表风矢量（幅 ≈ 风速单位）。 */
    public final double windX;
    public final double windZ;
    /** 气压干湿 [0,1]：1=干（副高/极地下沉带），0=湿。 */
    public final double pressureDry;
    /** 潜在降水 [0,1]（P1b：辐合对流 + 地形抬升 − 焚风，湿度供能）。 */
    public final double rainfallBase;
    /** 洋流温湿占位（0.5 - bandD）；待 L5 海洋群系定型后清理。 */
    public final double gyreWarmth;

    // ---- 气团（溯源派生标签 + 耦合空气场） ----
    /** 气团类型（cP/mP/mT/cT，海洋性 × 冷暖派生）。 */
    public final AirMassType airType;
    /** 空气温度 [-1,1]（沿流场输运弛豫后）。 */
    public final double airTemperature;
    /** 空气湿度 [0,1]（沿流场输运后；迎风岸/内陆有路径感）。 */
    public final double airHumidity;

    // ---- 洋流 / 海温（网格解；仅海上有效） ----
    /** 洋流方向（海上）。 */
    public final double currentX;
    public final double currentZ;
    /** 海温 [-1,1]（耦合输运后；陆上 NaN）。 */
    public final double seaTemperature;
    /** 流速 [0,1] 占位（=0.5，待 M5 输出流强后替换）。 */
    public final double currentSpeed;

    public ClimateSample(boolean isLand, double coastDist,
                         double bandD, double windX, double windZ,
                         double pressureDry, double rainfallBase,
                         double gyreWarmth,
                         AirMassType airType, double airTemperature, double airHumidity,
                         double currentX, double currentZ,
                         double seaTemperature, double currentSpeed) {
        this.isLand = isLand;
        this.coastDist = coastDist;
        this.bandD = bandD;
        this.windX = windX;
        this.windZ = windZ;
        this.pressureDry = pressureDry;
        this.rainfallBase = rainfallBase;
        this.gyreWarmth = gyreWarmth;
        this.airType = airType;
        this.airTemperature = airTemperature;
        this.airHumidity = airHumidity;
        this.currentX = currentX;
        this.currentZ = currentZ;
        this.seaTemperature = seaTemperature;
        this.currentSpeed = currentSpeed;
    }

    @Override
    public String toString() {
        return String.format(
            "Climate[%s d=%.0f band=%.2f wind=(%.2f,%.2f) dry=%.2f rain=%.2f %s t=%.2f h=%.2f cur=(%.2f,%.2f %.2f)",
            isLand ? "LAND" : "SEA", coastDist, bandD, windX, windZ,
            pressureDry, rainfallBase, airType == null ? "-" : airType.code,
            airTemperature, airHumidity, currentX, currentZ, seaTemperature);
    }
}
