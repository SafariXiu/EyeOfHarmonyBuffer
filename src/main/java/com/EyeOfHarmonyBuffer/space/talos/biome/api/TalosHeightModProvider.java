package com.EyeOfHarmonyBuffer.space.talos.biome.api;

/**
 * 群系高度调制参数提供接口。
 *
 * 供气候层 / 高度场等通过 API 读取群系的高度调制配置，
 * 避免跨层直接依赖具体群系类。由 TalosBiomeBase 实现。
 */
public interface TalosHeightModProvider {

    /** 高度倾向（0 = 宏包带底，1 = 宏包带顶，0.5 = 居中）。 */
    double getHeightBias();

    /** 起伏强度（噪声围绕 bias 的散布比例，0 = 几乎平坦，1 = 用满整带）。 */
    double getHeightScale();
}
