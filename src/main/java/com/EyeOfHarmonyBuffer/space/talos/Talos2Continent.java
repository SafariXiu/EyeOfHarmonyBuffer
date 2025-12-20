package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import net.minecraft.world.biome.BiomeGenBase;

public final class Talos2Continent {
    private Talos2Continent() {}

    public static final double CONTINENT_SCALE = 0.00007D;
    public static final int CONTINENT_OCTAVES = 2;
    public static final long CONTINENT_SALT = 0xC0FFEE1234ABCDEFL;

    public static final double C_SHELF_START = 0.30D;
    public static final double C_SHELF_END = 0.45D;
    public static final double C_BEACH_END = 0.55D;

    public static double sampleC01(SimplexNoiseOctave continentNoise, int x, int z) {
        double cRaw = continentNoise.noise(x * CONTINENT_SCALE, z * CONTINENT_SCALE);
        double c = (cRaw + 1.0D) * 0.5D;
        return c * c * (3.0D - 2.0D * c);
    }

    public static BiomeGenBase pickBiome(double c) {
        if (c < C_SHELF_END) {
            return TalosBiomes.TALOS_OCEAN;
        } else if (c < C_BEACH_END) {
            return TalosBiomes.TALOS_BEACH;
        } else {
            return TalosBiomes.TALOS_PLAINS;
        }
    }
}

