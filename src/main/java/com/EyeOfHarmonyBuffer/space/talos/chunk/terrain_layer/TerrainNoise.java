package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;

/**
 * 第四层内部用的噪声封装。
 * 统一使用 NoiseUtil.coreNoise2D，并转换到 [-1,1]。
 */

public final class TerrainNoise {

    private TerrainNoise() {}

    /**
     * 统一 2D 噪声接口：
     *   - seed: 任意 long，内部会压成 int；
     *   - fx, fz: 世界坐标经过频率缩放以后的值；
     *   - 返回 [-1,1]。
     */
    public static double noise2(long seed, double fx, double fz) {
        int seedInt = (int)(seed & 0x7FFFFFFF);
        double n01 = NoiseUtil.coreNoise2D(fx, fz, seedInt); // [0,1)
        return n01 * 2.0 - 1.0; // [-1,1]
    }

    /**
     * 通用 FBM：分层 Value Noise，频率每层 x2，振幅每层 x0.5。
     */
    public static double fbm2D(long seed,
                               double x, double z,
                               double baseFreq, double baseAmp,
                               int octaves) {

        if (octaves <= 0 || baseAmp == 0.0 || baseFreq == 0.0) {
            return 0.0;
        }

        double sum  = 0.0;
        double freq = baseFreq;
        double amp  = baseAmp;

        for (int i = 0; i < octaves; i++) {
            double n = noise2(seed + i * 0x9E3779B97F4A7C15L, x * freq, z * freq);
            sum += n * amp;

            freq *= 2.0;
            amp  *= 0.5;
        }
        return sum;
    }
}
