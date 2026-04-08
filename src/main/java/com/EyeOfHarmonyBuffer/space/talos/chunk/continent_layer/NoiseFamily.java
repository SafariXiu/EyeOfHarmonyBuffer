package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：NoiseFamily
 * 来源：Python 模块 worldgen_core.NoiseFamily
 * 功能：
 *   - 分形噪声类（Fractal Brownian Noise）
 *   - 支持多个octave叠加；
 *   - 控制频率倍增 (lacunarity) 和振幅衰减 (gain)
 * =====================================================
 */

public class NoiseFamily {
    private final int seed;
    private final double baseFreq;
    private final int octaves;
    private final double lacunarity;
    private final double gain;

    public NoiseFamily(int seed, double baseFreq, int octaves, double lacunarity, double gain) {
        this.seed = seed;
        this.baseFreq = baseFreq;
        this.octaves = octaves;
        this.lacunarity = lacunarity;
        this.gain = gain;
    }

    public double get(double x, double z) {
        double freq = baseFreq;
        double amp = 1.0;
        double total = 0.0;
        double norm = 0.0;

        for (int i = 0; i < octaves; i++) {
            double n = NoiseUtil.coreNoise2D(x * freq, z * freq, seed + i);
            total += n * amp;
            norm += amp;
            freq *= lacunarity;
            amp *= gain;
        }

        return norm > 0.0 ? total / norm : 0.5;
    }
}
