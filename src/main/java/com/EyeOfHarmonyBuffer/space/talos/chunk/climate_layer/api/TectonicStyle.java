package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

/**
 * 构造风格（气候层对外 API）：
 * 由板块边界在网格级判定后输出的“区域风格”，
 * 群系覆盖与地形塑形统一使用，避免点级多状态竞争产生碎斑。
 */
public enum TectonicStyle {
    /** 无边界影响，保持自然宏群系。 */
    NONE,
    /** 挤压带外缘：高原 / 山脉混合。 */
    HIGHLAND,
    /** 挤压带核心：高山（山脉）。 */
    MOUNTAINS,
    /** 挤压带主峰：最高峰。 */
    PEAK
}
