package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

/**
 * 超级大陆中心信息：
 *   - 对应 WorldgenCore.SuperContinentCenter 的精简只读视图；
 *   - 供板块 / 河网等高层系统使用。
 */

public final class SuperCenterInfo {

    /** 超级大陆 ID（WorldgenMath.makeSuperId 生成） */
    public final int superId;

    /** 超级大陆中心世界坐标（blocks） */
    public final int worldX;
    public final int worldZ;

    /** 超级大陆基础半径（blocks） */
    public final int baseRadius;

    public SuperCenterInfo(int superId, int worldX, int worldZ, int baseRadius) {
        this.superId = superId;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.baseRadius = baseRadius;
    }

    @Override
    public String toString() {
        return "SuperCenterInfo{" +
            "superId=" + superId +
            ", worldX=" + worldX +
            ", worldZ=" + worldZ +
            ", baseRadius=" + baseRadius +
            '}';
    }
}
