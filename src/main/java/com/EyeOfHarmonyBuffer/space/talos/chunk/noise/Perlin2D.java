package com.EyeOfHarmonyBuffer.space.talos.chunk.noise;

import java.util.Random;

final class Perlin2D {

    private static final int SIZE = 512;
    private final int[] permutation = new int[SIZE];

    Perlin2D(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) {
            p[i] = i;
        }
        Random random = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }
        for (int i = 0; i < SIZE; i++) {
            permutation[i] = p[i & 255];
        }
    }

    double noise(double x, double z) {
        int xi = fastFloor(x) & 255;
        int zi = fastFloor(z) & 255;

        double xf = x - fastFloor(x);
        double zf = z - fastFloor(z);

        double u = fade(xf);
        double v = fade(zf);

        int aa = permutation[permutation[xi] + zi];
        int ab = permutation[permutation[xi] + zi + 1];
        int ba = permutation[permutation[xi + 1] + zi];
        int bb = permutation[permutation[xi + 1] + zi + 1];

        double x1 = lerp(grad(aa, xf, zf), grad(ba, xf - 1, zf), u);
        double x2 = lerp(grad(ab, xf, zf - 1), grad(bb, xf - 1, zf - 1), u);

        return lerp(x1, x2, v);
    }

    private static int fastFloor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double grad(int hash, double x, double z) {
        switch (hash & 0x3) {
            case 0:  return x + z;
            case 1:  return -x + z;
            case 2:  return x - z;
            case 3:
            default: return -x - z;
        }
    }
}
