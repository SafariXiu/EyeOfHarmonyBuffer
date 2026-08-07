package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyData;

/** 水体平滑计算所需的几何 / 水文上下文。 */
public final class WaterBodySmoothingContext {

    public final RiverBodyData body;
    /** 椭圆半轴（blocks）。 */
    public final double rx;
    public final double rz;
    /** 椭圆归一化半径（中心 0，轮廓 1）。 */
    public final double r;
    /** 世界坐标（block）。 */
    public final int worldX;
    public final int worldZ;
    public final int worldSeedInt;
    /** 到真实轮廓的最短距离（blocks，轮廓内部点）。 */
    public final double distToEdge;
    /** 水面高度（海平面 + 水位偏移）。 */
    public final double waterLevel;
    public final int seaLevel;
    /** 原始（河岸塑形后）地形高度。 */
    public final double baseHeightD;
    public final TalosRiverSystem.HydroSample hydro;
    /** 河道床面高度（含起伏，供接口过渡使用）。 */
    public final double riverBedYd;
    /** 河床微起伏噪声（±1.5 格）。 */
    public final double relief;
    /** 河道开口 / 过渡半径。 */
    public final double cutWidth;

    public WaterBodySmoothingContext(RiverBodyData body,
                                     double rx,
                                     double rz,
                                     double r,
                                     int worldX,
                                     int worldZ,
                                     int worldSeedInt,
                                     double distToEdge,
                                     double waterLevel,
                                     int seaLevel,
                                     double baseHeightD,
                                     TalosRiverSystem.HydroSample hydro,
                                     double riverBedYd,
                                     double relief,
                                     double cutWidth) {
        this.body = body;
        this.rx = rx;
        this.rz = rz;
        this.r = r;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.worldSeedInt = worldSeedInt;
        this.distToEdge = distToEdge;
        this.waterLevel = waterLevel;
        this.seaLevel = seaLevel;
        this.baseHeightD = baseHeightD;
        this.hydro = hydro;
        this.riverBedYd = riverBedYd;
        this.relief = relief;
        this.cutWidth = cutWidth;
    }
}
