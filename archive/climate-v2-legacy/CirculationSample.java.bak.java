package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

/**
 * L0 全球环流采样结果（风场/气压系统/降水）。
 *
 * 环面几何：X 周期 400k，Z 纬度循环 200k（热带中线 z=0/±200k，寒带 ±100k 冷接冷）。
 * 所有值都是确定性纯函数：f(worldX, worldZ, worldSeedInt)。
 *
 * 风场由**半固定气压系统**驱动（PressureSystemType 带标签），非平行纬向带。
 */
public final class CirculationSample {

    /** 纬度带 0=热带中线 1=寒带中线（≈ 归一化到最近热带距离 d/MAX_D）。 */
    public final double bandD;

    /** 盛行风向（单位向量，指向风流动方向）。 */
    public final double windX;
    public final double windZ;

    /** 洋流温湿（S2 阶段 = 纬度基准背景温度；S4 洋流再做）。 */
    public final double gyreWarmth;

    /** 气压干湿：0=极湿(ITCZ/副极地低压)，1=极干(副热带/极地高压)。 */
    public final double pressureDry;

    /** 潜在降水 [0,1]。 */
    public final double rainfallBase;

    /** 当前点主导的气压系统类型（带标签），无则 null。 */
    public final PressureSystemType pressureSystem;

    public CirculationSample(double bandD, double windX, double windZ,
                             double gyreWarmth, double pressureDry,
                             double rainfallBase, PressureSystemType pressureSystem) {
        this.bandD = bandD;
        this.windX = windX;
        this.windZ = windZ;
        this.gyreWarmth = gyreWarmth;
        this.pressureDry = pressureDry;
        this.rainfallBase = rainfallBase;
        this.pressureSystem = pressureSystem;
    }

    @Override
    public String toString() {
        return String.format(
            "Circulation[bandD=%.2f wind=(%.2f,%.2f) gyre=%.2f dry=%.2f rain=%.2f sys=%s]",
            bandD, windX, windZ, gyreWarmth, pressureDry, rainfallBase,
            pressureSystem == null ? "-" : pressureSystem.label);
    }
}
