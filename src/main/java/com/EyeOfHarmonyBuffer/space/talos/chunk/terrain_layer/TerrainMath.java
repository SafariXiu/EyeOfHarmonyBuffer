package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

public final class TerrainMath {

    private TerrainMath() {}

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public static double saturate(double v) {
        if (v < 0.0) return 0.0;
        if (v > 1.0) return 1.0;
        return v;
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** 固定 smoothstep 定义，和规划书保持一致 */
    public static double smoothstep(double edge0, double edge1, double x) {
        if (edge0 == edge1) {
            return (x < edge0) ? 0.0 : 1.0;
        }
        double t = saturate((x - edge0) / (edge1 - edge0));
        return t * t * (3.0 - 2.0 * t);
    }
}
