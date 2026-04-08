package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：NoiseUtil
 * 来源：Python 模块 worldgen_core.core_noise2d / _hash_int_2d
 * 功能：
 *   - 实现2D值噪声（Value Noise）；
 *   - 提供哈希函数和Quintic插值；
 *   - 所有结果范围在 [0,1)。
 * =====================================================
 */

public class NoiseUtil {

    /** 简单2D整数哈希函数 -> [0,1) */
    public static double hashInt2D(int ix, int iz, int seed) {
        long n = ix * 374761393L + iz * 668265263L + seed * 69069L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n = (n ^ (n >> 16)) & 0xFFFFFFFFL;
        return n / 4294967295.0;
    }

    private static double quinticFade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    /** 基础2D噪声 */
    public static double coreNoise2D(double x, double z, int seed) {
        int ix = (int) Math.floor(x);
        int iz = (int) Math.floor(z);

        double fx = x - ix;
        double fz = z - iz;

        int ix1 = ix + 1;
        int iz1 = iz + 1;

        double v00 = hashInt2D(ix, iz, seed);
        double v10 = hashInt2D(ix1, iz, seed);
        double v01 = hashInt2D(ix, iz1, seed);
        double v11 = hashInt2D(ix1, iz1, seed);

        double u = quinticFade(fx);
        double v = quinticFade(fz);

        double nx0 = v00 + (v10 - v00) * u;
        double nx1 = v01 + (v11 - v01) * u;
        double n = nx0 + (nx1 - nx0) * v;

        return n;
    }

    /** 简单复用 hash 函数 */
    public static double hash2(int ix, int iz, int seed) {
        return hashInt2D(ix, iz, seed);
    }
}
