package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;

public final class NoiseUtil {

    private NoiseUtil() {}

    public static double hash2(int ix, int iz, int seed) {
        long h = TectonicMath.hashInts(ix, iz, seed);
        return TectonicMath.randUnitDouble(h);
    }

    public static double coreNoise2D(double fx, double fz, int seed) {
        int x0 = fastFloor(fx);
        int z0 = fastFloor(fz);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double tx = fx - x0;
        double tz = fz - z0;

        double sx = smoothstep(tx);
        double sz = smoothstep(tz);

        double v00 = hash2(x0, z0, seed);
        double v10 = hash2(x1, z0, seed);
        double v01 = hash2(x0, z1, seed);
        double v11 = hash2(x1, z1, seed);

        double ix0 = lerp(v00, v10, sx);
        double ix1 = lerp(v01, v11, sx);

        double v = lerp(ix0, ix1, sz);

        if (v < 0.0) v = 0.0;
        if (v > 1.0) v = 1.0;
        return v;
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private static double smoothstep(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        return t * t * (3.0 - 2.0 * t);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
