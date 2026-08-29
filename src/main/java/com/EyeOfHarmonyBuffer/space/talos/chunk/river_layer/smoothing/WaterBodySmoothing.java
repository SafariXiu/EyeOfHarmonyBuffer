package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

/**
 * 水体「平滑」策略：每种水体结构一个实现，
 * 负责计算轮廓内部的目标床面高度（y）。
 *
 * 岸滩 / 外坡 / 河道开口等公共逻辑仍由 TalosRiverProfile 统一处理，
 * 这里只封装各类型不同的湖盆形状、岸坡和河床起伏。
 */
public interface WaterBodySmoothing {

    /**
     * 计算轮廓内部某列的目标床面高度（世界 Y）。
     *
     * @param ctx 几何 / 水文上下文
     * @return 床面高度；调用方负责后续的浅水钳制、河道下切与最终 clamp
     */
    double interiorBedY(WaterBodySmoothingContext ctx);
}
