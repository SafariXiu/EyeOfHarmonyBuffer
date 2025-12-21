package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;

public final class CoastWidthField {

    public static final long WIDTH_SALT_SHELF = 0xA1B2C3D4E5F60718L;
    public static final long WIDTH_SALT_BEACH = 0x1A2B3C4D5E6F7081L;

    public static final double WIDTH_SCALE_SHELF = 0.0009D;
    public static final double WIDTH_SCALE_BEACH = 0.0013D;

    private final SimplexNoiseOctave shelfWidthNoise;
    private final SimplexNoiseOctave beachWidthNoise;

    public CoastWidthField(long worldSeed) {
        this.shelfWidthNoise = new SimplexNoiseOctave(worldSeed ^ WIDTH_SALT_SHELF, 1);
        this.beachWidthNoise = new SimplexNoiseOctave(worldSeed ^ WIDTH_SALT_BEACH, 1);
    }

    public int shelfWidthBlocks(int gx, int gz, CoastProfile p) {
        double w = (shelfWidthNoise.noise(gx * WIDTH_SCALE_SHELF, gz * WIDTH_SCALE_SHELF) + 1.0) * 0.5;
        w = smooth01(w);
        return lerpInt(p.shelfMin, p.shelfMax, w);
    }

    public int beachWidthBlocks(int gx, int gz, CoastProfile p) {
        double w = (beachWidthNoise.noise(gx * WIDTH_SCALE_BEACH, gz * WIDTH_SCALE_BEACH) + 1.0) * 0.5;
        w = smooth01(w);
        return lerpInt(p.beachMin, p.beachMax, w);
    }

    private static int lerpInt(int a, int b, double t) {
        if (t < 0) t = 0;
        if (t > 1) t = 1;
        return (int)Math.round(a + (b - a) * t);
    }

    private static double smooth01(double x) {
        if (x < 0) x = 0;
        if (x > 1) x = 1;
        return x * x * (3.0 - 2.0 * x);
    }
}
