package com.EyeOfHarmonyBuffer.space.talos;

public final class Talos2Continent {
    private Talos2Continent() {}

    public static final double CONTINENT_SCALE = 0.00007D;
    public static final int CONTINENT_OCTAVES = 2;
    public static final long CONTINENT_SALT = 0xC0FFEE1234ABCDEFL;

    public static final double C_LAND = 0.55D;

    public static double sampleC01(SimplexNoiseOctave continentNoise, int x, int z) {
        double raw = continentNoise.noise(x * CONTINENT_SCALE, z * CONTINENT_SCALE);

        double c = (raw + 1.0D) * 0.5D;

        c = clamp01(c);

        c = c * c * (3.0D - 2.0D * c);

        return c;
    }

    public static boolean isLand(SimplexNoiseOctave continentNoise, int x, int z) {
        return sampleC01(continentNoise, x, z) >= C_LAND;
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }
}

