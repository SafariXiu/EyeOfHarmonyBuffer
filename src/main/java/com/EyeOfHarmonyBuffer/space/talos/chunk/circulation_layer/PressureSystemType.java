package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

/**
 * 半固定气压系统类型（真实大气行动中心）。
 * 每个系统沿纬度带分布，带标签；决定风场旋转方向（高压辐散/低压辐合）与干湿。
 */
public enum PressureSystemType {
    /** 赤道辐合带（低压，极湿，无/弱风）。 */
    ITCZ("赤道辐合带", -1.0, 0.10),
    /** 副热带高压（反气旋，顺时针辐散，干）。 */
    SUBTROPICAL_HIGH("副热带高压", +1.0, 0.85),
    /** 副极地低压（气旋，逆时针辐合，湿）。 */
    SUBPOLAR_LOW("副极地低压", -1.0, 0.20),
    /** 极地高压（反气旋，顺时针辐散，干）。 */
    POLAR_HIGH("极地高压", +1.0, 0.80);

    /** 中文标签（供 /talosmap 标注）。 */
    public final String label;
    /** 系统符号：+1 高压（顺时针辐散/干），-1 低压（逆时针辐合/湿）。 */
    public final double sign;
    /** 该类型的基准干湿度（高压=干 ~0.8，低压=湿 ~0.2）。 */
    public final double baseDry;

    PressureSystemType(String label, double sign, double baseDry) {
        this.label = label;
        this.sign = sign;
        this.baseDry = baseDry;
    }
}
