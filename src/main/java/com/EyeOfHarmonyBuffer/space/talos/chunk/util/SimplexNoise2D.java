package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

/**
 * 2D Simplex Noise (classic Stefan Gustavson implementation, public domain).
 *
 * Guaranteed continuous: the permutation table is 512 entries (the 256-entry
 * permutation duplicated), so any index 'ii + perm[jj]' with ii,jj in [0,255]
 * and perm values in [0,255] stays below 512 - no wrap, no discontinuity.
 * Output in roughly [-1, 1].
 */
public final class SimplexNoise2D {
    private static final int[][] GRAD3 = {
        {1,1,0}, {-1,1,0}, {1,-1,0}, {-1,-1,0},
        {1,0,1}, {-1,0,1}, {1,0,-1}, {-1,0,-1},
        {0,1,1}, {0,-1,1}, {0,1,-1}, {0,-1,-1}
    };

    private final int[] perm = new int[512];

    public SimplexNoise2D(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = s * 6364136223846793005L + 1442695040888963407L;
            int j = (int) (((s >>> 33) & 0x7FFFFFFFL) % (i + 1));
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static int fastfloor(double x) {
        int xi = (int) x;
        return (x < xi) ? xi - 1 : xi;
    }

    public double noise2(double xin, double yin) {
        double F2 = 0.5 * (Math.sqrt(3.0) - 1.0);
        double G2 = (3.0 - Math.sqrt(3.0)) / 6.0;
        double n0, n1, n2;

        double s = (xin + yin) * F2;
        int i = fastfloor(xin + s);
        int j = fastfloor(yin + s);
        double t = (i + j) * G2;
        double X0 = i - t;
        double Y0 = j - t;
        double x0 = xin - X0;
        double y0 = yin - Y0;

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }

        double x1 = x0 - i1 + G2;
        double y1 = y0 - j1 + G2;
        double x2 = x0 - 1.0 + 2.0 * G2;
        double y2 = y0 - 1.0 + 2.0 * G2;

        int ii = i & 255;
        int jj = j & 255;

        double t0 = 0.5 - x0 * x0 - y0 * y0;
        if (t0 < 0.0) n0 = 0.0;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(GRAD3[perm[ii + perm[jj]] % 12], x0, y0);
        }

        double t1 = 0.5 - x1 * x1 - y1 * y1;
        if (t1 < 0.0) n1 = 0.0;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(GRAD3[perm[ii + i1 + perm[jj + j1]] % 12], x1, y1);
        }

        double t2 = 0.5 - x2 * x2 - y2 * y2;
        if (t2 < 0.0) n2 = 0.0;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(GRAD3[perm[ii + 1 + perm[jj + 1]] % 12], x2, y2);
        }

        return 70.0 * (n0 + n1 + n2);
    }

    private static double dot(int[] g, double x, double y) {
        return g[0] * x + g[1] * y;
    }
}
