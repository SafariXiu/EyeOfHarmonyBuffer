package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample;

/**
 * 板块边界类型（按相邻两板块运动向量在缝合线法向上的相对分量分类）。
 */
public enum PlateBoundaryState {
    /** 挤压：两板块相对运动在法向上相互靠近（汇聚）。 */
    CONVERGENT,
    /** 分离：两板块相对运动在法向上相互远离（离散）。 */
    DIVERGENT,
    /** 走滑：相对运动以切向（平行于缝合线）为主（转换断层）。 */
    TRANSFORM,
    /** 静止：两板块相对运动速度低于阈值（弱活动 / 未定）。 */
    INACTIVE;

    /** 挤压度轴映射：挤压 = +1，分离 = -1，走滑 / 静止 = 0。 */
    public double compressionAxis() {
        switch (this) {
            case CONVERGENT:
                return 1.0;
            case DIVERGENT:
                return -1.0;
            case TRANSFORM:
            case INACTIVE:
            default:
                return 0.0;
        }
    }
}
