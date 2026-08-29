package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

public final class SupercontinentInfo {

    public final int superId;
    public final double centerX;
    public final double centerZ;
    public final double radius;
    /** 超级大陆“向外流”的固定方向（弧度）：从中心指向最近海岸，从 X 轴逆时针 */
    public final double angleRad;

    public SupercontinentInfo(int superId,
                              double centerX,
                              double centerZ,
                              double radius,
                              double angleRad) {
        this.superId  = superId;
        this.centerX  = centerX;
        this.centerZ  = centerZ;
        this.radius   = radius;
        this.angleRad = angleRad;
    }
}
