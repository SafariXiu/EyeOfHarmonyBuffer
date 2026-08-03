package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

/**
 * 宏群系 ID：一组地形/气候非常接近的具体群系的集合。
 * 每个宏群系内 ≤ 3 个 Biome。
 *
 * 气候层公共 API 类型：被地形层 / 河流层等通过本 API 包引用，
 * 外部层不应直接使用气候层内部实现。
 */

public enum MacroPackageId {

    // 海洋
    OCEANIC,            // 深海 + 陆架

    // 热带 / 亚热带
    TROPICAL_HUMID,     // 热带雨林 + 湿热盆地
    TROPICAL_DRY,       // 热带草原 / 热带半干旱 / 热带沙漠

    // 温带
    TEMPERATE_LOWLAND,  // 温带平原 + 温带草原
    TEMPERATE_FORESTED, // 温带森林（落叶 + 过渡针叶）
    TEMPERATE_HIGHLAND, // 温带高原 + 中等山地

    // 凉爽 / 亚寒带 / 寒带
    COOL_FORESTED,      // 冷针叶林 / 过渡森林
    SUBPOLAR_TUNDRA,    // 亚极地冻原
    POLAR_HIGHLAND,     // 高寒山地 + 极地荒漠

    // 裂谷 / 峡谷（仅由板块分离带覆盖注入，不参与站点生成；按纬度分三种）
    RIFT_TROPICAL,      // 热带 / 亚热带峡谷
    RIFT_TEMPERATE,     // 温带峡谷
    RIFT_POLAR,         // 亚寒带 / 寒带峡谷

    // 最高峰（仅由挤压带核心覆盖注入，不参与站点生成）
    MOUNTAIN_PEAK       // 最高山峰：只含地形最高的群系
}
