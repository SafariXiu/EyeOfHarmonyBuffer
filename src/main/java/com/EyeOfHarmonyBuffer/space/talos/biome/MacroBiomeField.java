package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;

public final class MacroBiomeField {
    public static final long MACRO_SALT = 0x9E3779B97F4A7C15L;
    public static final double MACRO_SCALE = 0.00025D;

    private final SimplexNoiseOctave macroNoise;

    public MacroBiomeField(long worldSeed) {
        this.macroNoise = new SimplexNoiseOctave(worldSeed ^ MACRO_SALT, 2);
    }

    public MacroBiome pick(int gx, int gz) {
        double v = macroNoise.noise(gx * MACRO_SCALE, gz * MACRO_SCALE);
        if (v < -0.25) return MacroBiome.COLD;
        if (v >  0.25) return MacroBiome.ARID;
        return MacroBiome.TEMPERATE;
    }

    public static double clamp01(double x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }
}
