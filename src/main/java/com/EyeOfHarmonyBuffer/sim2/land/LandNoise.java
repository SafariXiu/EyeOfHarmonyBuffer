package com.EyeOfHarmonyBuffer.sim2.land;

/**
 * 哈希与带限噪声（splitmix64 + 双线性 value noise）。
 *
 * <p>为什么不用 Perlin/cnoise：这里只需要「同种子逐位确定、够快、C¹」的 2D 噪声，
 * 双线性 value noise 在 0.2 µs 量级，且没有梯度表的缓存问题。
 *
 * <p>⚠ 所有方法都是纯函数：同样的 (seed, x, y) 永远给同样的值。
 */
public final class LandNoise {

    private LandNoise() {}

    /** splitmix64 finalizer。 */
    public static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** 格点 (i,j) 的 [0,1) 均匀值。 */
    /** Public so the deprecated layer can be relocated to its own package. */
    public static double h01(long seed, long i, long j) {
        long h = mix(seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL));
        return (h >>> 11) * 0x1.0p-53;
    }

    /**
     * 双线性 value noise，[0,1)，格距 = 1。
     * <p>x、y 以「波长」为单位传入（例如 {@code px / WL}）。
     */
    /** 梯度噪声（Perlin）：海岸线用，比 value noise 平滑、没有栅格块感。 */
    public static double perlin(double x, double y, long seed) {
        long i = (long) Math.floor(x), j = (long) Math.floor(y);
        double fx = x - i, fy = y - j;
        double u = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
        double v = fy * fy * fy * (fy * (fy * 6.0 - 15.0) + 10.0);
        double n00 = grad(i, j, seed, fx, fy);
        double n10 = grad(i + 1, j, seed, fx - 1.0, fy);
        double n01 = grad(i, j + 1, seed, fx, fy - 1.0);
        double n11 = grad(i + 1, j + 1, seed, fx - 1.0, fy - 1.0);
        double a = n00 + u * (n10 - n00);
        double b = n01 + u * (n11 - n01);
        return (a + v * (b - a)) * 1.4;
    }

    private static double grad(long i, long j, long seed, double dx, double dy) {
        double ang = h01(seed, i, j) * 6.283185307179586;
        return Math.cos(ang) * dx + Math.sin(ang) * dy;
    }

    public static double value(double x, double y, long seed) {
        long i = (long) Math.floor(x), j = (long) Math.floor(y);
        double fx = x - i, fy = y - j;
        double u = fx * fx * (3.0 - 2.0 * fx);
        double v = fy * fy * (3.0 - 2.0 * fy);
        double a = h01(seed, i, j),     b = h01(seed, i + 1, j);
        double c = h01(seed, i, j + 1), d = h01(seed, i + 1, j + 1);
        return (a * (1.0 - u) + b * u) * (1.0 - v)
             + (c * (1.0 - u) + d * u) * v;
    }
}
