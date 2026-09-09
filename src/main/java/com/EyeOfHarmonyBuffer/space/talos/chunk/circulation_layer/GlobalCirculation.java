package com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;

/**
 * 纬度 / 环面工具（全层共用的小工具类）。
 *
 * 仅保留：环面折叠 foldX/foldZ、纬度带 bandD、周期常量。
 * 旧"三圈基底 + 半固定气压系统"模型（windDir/pressureDry/dominant/rainfall/sample
 * 及其 DTO 枚举）已随网格解 RelaxedClimate 下线——完整快照见
 * archive/climate-v2-legacy/（勿恢复编译）。
 */
public final class GlobalCirculation {

    /** X 方向周期（blocks）。 */
    public static final int X_CYCLE = 400_000;
    /** Z 方向纬度循环（blocks）。 */
    public static final int Z_CYCLE = ClimateLatitudes.LAT_CYCLE;

    private GlobalCirculation() {}

    public static int foldX(int x) {
        int m = x % X_CYCLE;
        return m < 0 ? m + X_CYCLE : m;
    }

    public static int foldZ(int z) {
        int m = z % Z_CYCLE;
        return m < 0 ? m + Z_CYCLE : m;
    }

    /** 纬度带 0=赤道 1=极地。 */
    public static double bandD(int worldZ) {
        int d = ClimateLatitudes.getDistanceToCenter(worldZ);
        return d / (double) ClimateLatitudes.MAX_D;
    }
}
