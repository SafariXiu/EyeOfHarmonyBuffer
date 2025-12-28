package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.utils.Random.SimplexNoiseSeeded;

public class SimplexNoiseOctave {

    private final int octaves;
    private final double[] frequencies;
    private final double[] amplitudes;

    private final SimplexNoiseSeeded[] simplex;

    public SimplexNoiseOctave(long seed, int octavesCount) {
        this.octaves = octavesCount;
        this.frequencies = new double[octavesCount];
        this.amplitudes = new double[octavesCount];
        this.simplex = new SimplexNoiseSeeded[octavesCount];

        for (int i = 0; i < octavesCount; i++) {
            this.frequencies[i] = Math.pow(2, i);
            this.amplitudes[i] = Math.pow(0.5, i);

            long octaveSeed = mixSeed(seed, i);
            this.simplex[i] = new SimplexNoiseSeeded(octaveSeed);
        }
    }

    public double noise(double x, double z) {
        double total = 0.0;
        for (int i = 0; i < octaves; i++) {
            double nx = x * frequencies[i];
            double nz = z * frequencies[i];
            total += simplex[i].noise(nx, nz) * amplitudes[i];
        }
        return total;
    }

    /** Small seed mixer (deterministic). */
    private static long mixSeed(long seed, int octave) {
        long x = seed + 0x9E3779B97F4A7C15L * (octave + 1);
        x ^= (x >>> 30);
        x *= 0xBF58476D1CE4E5B9L;
        x ^= (x >>> 27);
        x *= 0x94D049BB133111EBL;
        x ^= (x >>> 31);
        return x;
    }
}
