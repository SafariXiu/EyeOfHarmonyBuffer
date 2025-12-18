package com.EyeOfHarmonyBuffer.space.talos;

import bartworks.util.NoiseUtil.SimplexNoise;

import java.util.Random;

public class SimplexNoiseOctave {

    private final int octaves;
    private final double[] frequencies;
    private final double[] amplitudes;

    public SimplexNoiseOctave(int octavesCount) {
        this.octaves = octavesCount;
        this.frequencies = new double[octavesCount];
        this.amplitudes = new double[octavesCount];
        for (int i = 0; i < octavesCount; i++) {
            this.frequencies[i] = Math.pow(2, i);
            this.amplitudes[i] = Math.pow(0.5, i);
        }
    }

    public double noise(double x, double z) {
        double total = 0;
        for (int i = 0; i < octaves; i++) {
            double nx = x * frequencies[i];
            double nz = z * frequencies[i];
            total += SimplexNoise.noise(nx, nz) * amplitudes[i];
        }
        return total;
    }
}
