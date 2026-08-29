package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

/**
 * 湿地：水陆马赛克沼泽。
 *
 * 群系基础高度比较统一，按高度压会把整片湿地压成同一种状态。
 * 这里改用自己的确定性噪声在湿地内生成「一半水、一半陆」的斑块：
 *   - 噪声低于阈值：水洼，床面压到海平面下 2 格（默认 y=62）；
 *   - 噪声高于阈值：干丘，床面抬到水面以上 0~2 格（y=64~66）；
 *   - 阈值附近用软过渡，避免水陆硬切。
 * 基础高度明显高于湿地带时逐渐淡出回原地形（防止高山上挖出大坑）。
 */
public final class WetlandSmoothing implements WaterBodySmoothing {

    /** 水洼床面：海平面下 2 格（默认 y=62）。 */
    private static final double WATER_BED_BELOW_SEA = 2.0;
    /** 干丘最高点：水面以上 2 格（默认 y=66）。 */
    private static final double HUMMOCK_ABOVE_SEA = 2.0;
    /** 水陆斑块的噪声尺度（blocks）。 */
    private static final double NOISE_SCALE = 28.0;
    /** 水陆分界阈值：0.5 ≈ 一半水一半陆。 */
    private static final double THRESHOLD = 0.5;
    /** 阈值附近的软过渡带宽（噪声 0~1 空间）。 */
    private static final double TRANSITION = 0.16;
    /** 基础高度高于该值（相对海平面）后开始淡出回原地形。 */
    private static final double HIGH_FADE_START = 6.0;
    private static final double HIGH_FADE_BAND = 12.0;

    @Override
    public double interiorBedY(WaterBodySmoothingContext ctx) {
        double n = valueNoise(
            ctx.worldX, ctx.worldZ, ctx.worldSeedInt,
            NOISE_SCALE, 0x57A3C1D2
        );

        double t = (n - THRESHOLD) / TRANSITION;
        double swamp = (ctx.seaLevel - WATER_BED_BELOW_SEA)
            + HUMMOCK_ABOVE_SEA * SmoothingMath.smoothstep01(t);

        // 高地形淡出：基础高度明显高于湿地带时逐渐回到原地形，
        // 避免在山上挖出深坑；常规湿地高度（海平面附近）不受影响。
        double fade = SmoothingMath.clamp01(
            (ctx.baseHeightD - (ctx.seaLevel + HIGH_FADE_START))
                / HIGH_FADE_BAND
        );
        return swamp + (ctx.baseHeightD - swamp) * fade;
    }

    /** 确定性 2D 值噪声，返回 [0,1]，用于水陆斑块。 */
    private static double valueNoise(int x, int z, int seed,
                                     double scale, int salt) {
        double sx = x / scale;
        double sz = z / scale;
        int x0 = (int) Math.floor(sx);
        int z0 = (int) Math.floor(sz);
        double fx = sx - x0;
        double fz = sz - z0;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fz * fz * (3.0 - 2.0 * fz);

        double a = hash01(x0, z0, seed, salt);
        double b = hash01(x0 + 1, z0, seed, salt);
        double c = hash01(x0, z0 + 1, seed, salt);
        double d = hash01(x0 + 1, z0 + 1, seed, salt);
        double e = a + (b - a) * u;
        double f = c + (d - c) * u;
        return e + (f - e) * v;
    }

    private static double hash01(int x, int z, int seed, int salt) {
        long h = 0x9E3779B97F4A7C15L;
        h ^= mix64(x + 0x9E3779B97F4A7C15L);
        h ^= mix64(z + 0xBF58476D1CE4E5B9L);
        h ^= mix64(seed + 0x94D049BB133111EBL);
        h ^= mix64(salt);
        h = mix64(h);
        return (h >>> 11) / (double) (1L << 53);
    }

    private static long mix64(long x) {
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL;
        return x ^ (x >>> 31);
    }
}
