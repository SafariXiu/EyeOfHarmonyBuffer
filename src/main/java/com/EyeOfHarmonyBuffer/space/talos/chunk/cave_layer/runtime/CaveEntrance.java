package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 地表入口（主入口 / 天坑）：竖井列位置 + 井底 Y + 半径。
 * 井身由雕刻 pass 在生成时按实际地表高度开凿（地表高度是地形链产物，
 * 不能在网络构建期确定）。
 */
public final class CaveEntrance {

    public final int x;
    public final int z;
    public final int y;
    public final int radius;
    public final boolean sinkhole;

    public CaveEntrance(int x, int z, int y, int radius, boolean sinkhole) {
        this.x = x;
        this.z = z;
        this.y = y;
        this.radius = radius;
        this.sinkhole = sinkhole;
    }
}
