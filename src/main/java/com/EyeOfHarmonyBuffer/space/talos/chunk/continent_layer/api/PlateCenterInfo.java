package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

/**
 * 板块（子大陆）中心信息：
 *   - 对应 WorldgenCore.generatePlateCentersForSuper 里的 PlateCenter；
 *   - 供河网 / 气候 / 结构等系统使用。
 */

public final class PlateCenterInfo {

    /** 板块 / 子大陆 ID（等价于 WorldgenCore.LandResult.plateId） */
    public final int plateId;

    /** 所属超级大陆 ID */
    public final int superId;

    /** 板块中心世界坐标（blocks） */
    public final double centerX;
    public final double centerZ;

    /** 板块近似半径（blocks） */
    public final double radius;

    public PlateCenterInfo(int plateId,
                           int superId,
                           double centerX,
                           double centerZ,
                           double radius) {
        this.plateId = plateId;
        this.superId = superId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.radius = radius;
    }

    @Override
    public String toString() {
        return "PlateCenterInfo{" +
            "plateId=" + plateId +
            ", superId=" + superId +
            ", centerX=" + centerX +
            ", centerZ=" + centerZ +
            ", radius=" + radius +
            '}';
    }
}
