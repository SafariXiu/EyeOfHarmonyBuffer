package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.AirMassType;

/**
 * V2 统一气候采样结果（docs/TerrainV2/design.md 二c）。
 *
 * 一次编排采样：L1 海陆 + L0 环流（风/干湿/雨）+ L2 气团 + L4 洋流（仅海上有效）。
 * 所有值都是确定性纯函数：f(worldX, worldZ, worldSeedInt)。
 * 生产（L5 群系 / 水体 / 地形增强）消费方与 /talosmap 出图统一走 {@link GlobalClimate}，
 * 不要再直接调各层静态入口，避免口径漂移。
 */
public final class ClimateSample {

    // ---- L1 海陆（NoiseContinentGrid · V2 噪声场） ----
    /** 是否陆地。 */
    public final boolean isLand;
    /** 有符号海岸距离（block）：&lt;0 内陆 / ≈0 岸线 / &gt;0 海上。远场截断 ±200k。 */
    public final double coastDist;

    // ---- L0 纬度 / 环流（GlobalCirculation） ----
    /** 纬度带：0=热带中线，1=寒带中线。 */
    public final double bandD;
    /** 盛行风矢量（未归一化，幅 ≈1；基底主导 + 弱扰动）。 */
    public final double windX;
    public final double windZ;
    /** 气压干湿 [0,1]：0=湿（ITCZ/副极地低压），1=干（副热带/极地高压）。 */
    public final double pressureDry;
    /** 潜在降水 [0,1]。 */
    public final double rainfallBase;
    /** 主导气压系统（带标签），无则 null。 */
    public final PressureSystemType pressureSystem;
    /** 洋流温湿占位（0.5 - bandD）；S6.1 折射实现前仅用于 /talosmap gyre 出图。 */
    public final double gyreWarmth;

    // ---- L2 气团（AirMassField，海陆×纬度四分类） ----
    /** 气团类型（cP/mP/mT/cT，含海/陆信息）。 */
    public final AirMassType airType;
    /** 气温 [-1,1]（= 1 - 2·bandD，连续）。 */
    public final double airTemperature;
    /** 空气湿度 [0,1]（海洋湿、大陆干；热带偏湿）。 */
    public final double airHumidity;

    // ---- L4 洋流（OceanCurrentField，仅海上；陆上为 0/占位） ----
    /** 洋流方向（单位向量，海上）。 */
    public final double currentX;
    public final double currentZ;
    /** 海温 [-1,1]（纬度基准；折射修正 S6.1）。 */
    public final double seaTemperature;
    /** 流速 [0,1]（风应力）。 */
    public final double currentSpeed;

    public ClimateSample(boolean isLand, double coastDist,
                         double bandD, double windX, double windZ,
                         double pressureDry, double rainfallBase,
                         PressureSystemType pressureSystem, double gyreWarmth,
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
        this.pressureSystem = pressureSystem;
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
