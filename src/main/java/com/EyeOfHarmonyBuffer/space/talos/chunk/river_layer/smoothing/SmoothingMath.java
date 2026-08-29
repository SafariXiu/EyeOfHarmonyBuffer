package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

/** 平滑计算共用的小工具。 */
public final class SmoothingMath {

    private SmoothingMath() {}

    public static double clamp01(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    public static double smoothstep01(double t) {
        t = clamp01(t);
        return t * t * (3.0 - 2.0 * t);
    }
}
