package com.EyeOfHarmonyBuffer.space.talos.chunk.noise;

public final class FractalNoise2D {

    private final Perlin2D baseNoise;
    private final double frequency;
    private final double lacunarity;
    private final double persistence;
    private final int octaves;

    public FractalNoise2D(long seed,
                          double frequency,
                          double lacunarity,
                          double persistence,
                          int octaves) {

        this.baseNoise = new Perlin2D(seed);
        this.frequency = frequency;
        this.lacunarity = lacunarity;
        this.persistence = persistence;
        this.octaves = Math.max(1, octaves);
    }

    public double sample(double x, double z) {
        double freq = frequency;
        double amp = 1.0;
        double sum = 0.0;
        double max = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += baseNoise.noise(x * freq, z * freq) * amp;
            max += amp;
            freq *= lacunarity;
            amp *= persistence;
        }
        return max == 0.0 ? 0.0 : sum / max;
    }
}
