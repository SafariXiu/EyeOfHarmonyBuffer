package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * 气团类型（NOAA 标准 4 类，依附海陆 + 纬度）。
 *
 * 气团 = 大范围性质均匀的空气，由源地上垫面（海/陆、热/冷）决定：
 *   - C = Continental（大陆，干）
 *   - M = Maritime（海洋，湿）
 *   - P = Polar（极地，冷）
 *   - T = Tropical（热带，热）
 *
 * 组合：CP（极地大陆，干冷）/ MP（极地海洋，湿冷）/
 *       MT（热带海洋，暖湿）/ CT（热带大陆，干热）
 */
public enum AirMassType {

    /** 极地大陆气团：高纬陆地/冰盖，干冷。 */
    CONTINENTAL_POLAR("极地大陆气团", "cP", false, false),
    /** 极地海洋气团：高纬海洋，湿冷。 */
    MARITIME_POLAR("极地海洋气团", "mP", true, false),
    /** 热带海洋气团：低纬海洋，暖湿。 */
    MARITIME_TROPICAL("热带海洋气团", "mT", true, true),
    /** 热带大陆气团：低纬陆地，干热。 */
    CONTINENTAL_TROPICAL("热带大陆气团", "cT", false, true);

    /** 中文标签。 */
    public final String label;
    /** 标准缩写（cP/mP/mT/cT）。 */
    public final String code;
    /** 是否海洋气团（湿）。 */
    public final boolean maritime;
    /** 是否热带气团（热）。 */
    public final boolean tropical;

    AirMassType(String label, String code, boolean maritime, boolean tropical) {
        this.label = label;
        this.code = code;
        this.maritime = maritime;
        this.tropical = tropical;
    }
}
