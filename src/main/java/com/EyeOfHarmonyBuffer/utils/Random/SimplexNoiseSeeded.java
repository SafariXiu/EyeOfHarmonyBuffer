package com.EyeOfHarmonyBuffer.utils.Random;

import java.util.Random;

public final class SimplexNoiseSeeded {

    static final java.util.concurrent.atomic.LongAdder CALLS = new java.util.concurrent.atomic.LongAdder();
    private static final boolean DEBUG_CALLS = true;

    private static final int[][] GRAD3 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {1, 0}, {-1, 0},
        {0, 1}, {0, -1}, {0, 1}, {0, -1}
    };

    private static final double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
    private static final double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;

    private final short[] perm = new short[512];

    public SimplexNoiseSeeded(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) p[i] = i;

        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            short tmp = p[i];
            p[i] = p[j];
            p[j] = tmp;
        }

        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static double dot(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }

    public double noise(double xin, double yin) {

        if (DEBUG_CALLS) CALLS.increment();

        double s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);

        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;

        int gi0 = perm[ii + perm[jj]] % 12;
        int gi1 = perm[ii + i1 + perm[jj + j1]] % 12;
        int gi2 = perm[ii + 1 + perm[jj + 1]] % 12;

        double n0, n1, n2;

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(GRAD3[gi0], x0, y0);
        }

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(GRAD3[gi1], x1, y1);
        }

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0) n2 = 0.0;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(GRAD3[gi2], x2, y2);
        }

        return 70.0 * (n0 + n1 + n2);
    }
}
