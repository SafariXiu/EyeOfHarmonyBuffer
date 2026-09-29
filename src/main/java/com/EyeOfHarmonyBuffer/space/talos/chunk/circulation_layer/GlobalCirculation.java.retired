package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;

/**
 * 纬度工具（全层共用的小工具类）。
 *
 * 仅保留：纬度带 bandD、纬度标量 Z_CYCLE。
 * 旧"三圈基底 + 半固定气压系统"模型（windDir/pressureDry/dominant/rainfall/sample
 * 及其 DTO 枚举）已随网格解 RelaxedClimate 下线——完整快照见
 * archive/climate-v2-legacy/（勿恢复编译）。
 */
public final class GlobalCirculation {

    /** Z 方向纬度循环（blocks）。 */
    public static final int Z_CYCLE = ClimateLatitudes.LAT_CYCLE;

    private GlobalCirculation() {}

    /** 纬度（弧度）：赤道为 0、两极为 ±π/2（沿 Z 折返循环）。 */
    public static double latRad(int worldZ) {
        return ClimateLatitudes.hemisphereSign(worldZ) * bandD(worldZ) * Math.PI / 2.0;
    }

    /** 纬度带 0=赤道 1=极地。 */
    public static double bandD(int worldZ) {
        int d = ClimateLatitudes.getDistanceToCenter(worldZ);
        return d / (double) ClimateLatitudes.MAX_D;
    }
}
