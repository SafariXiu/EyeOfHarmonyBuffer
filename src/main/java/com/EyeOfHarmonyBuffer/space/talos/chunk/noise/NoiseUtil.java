package com.EyeOfHarmonyBuffer.space.talos.chunk.noise;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

import java.util.List;

public final class NoiseUtil {

    private NoiseUtil() {}

    public static double fractal(long seed, long salt, double x, double z,
                                 double frequency, int octaves, double lacunarity, double gain) {
        double amp = 0.5;
        double sum = 0.0;
        double totalAmp = 0.0;
        double fx = x * frequency;
        double fz = z * frequency;

        for (int i = 0; i < octaves; i++) {
            sum += valueNoise(seed, salt + i * 17L, fx, fz) * amp;
            totalAmp += amp;
            fx *= lacunarity;
            fz *= lacunarity;
            amp *= gain;
        }
        return totalAmp == 0.0 ? 0.0 : sum / totalAmp;
    }

    public static double valueNoise(long seed, long salt, double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        double fx = x - x0;
        double fz = z - z0;

        double v00 = hashToUnit(mix(seed, x0, z0, salt));
        double v10 = hashToUnit(mix(seed, x0 + 1, z0, salt));
        double v01 = hashToUnit(mix(seed, x0, z0 + 1, salt));
        double v11 = hashToUnit(mix(seed, x0 + 1, z0 + 1, salt));

        double sx = smooth(fx);
        double sz = smooth(fz);

        double ix0 = lerp(v00, v10, sx);
        double ix1 = lerp(v01, v11, sx);
        return lerp(ix0, ix1, sz);
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    public static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    public static int fastFloor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    public static long mix(long seed, int x, int z, long salt) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 13);
        h ^= z * 0xC2B2AE3D27D4EB4FL;
        h = Long.rotateLeft(h, 7);
        h ^= salt;
        h *= 0x94D049BB133111EBL;
        return h;
    }

    public static double hashToUnit(long hash) {
        return (hash >>> 11) * (1.0 / (1L << 53));
    }

    public static int hashToInt(long seed, int x, int z, long salt) {
        long mixed = mix(seed, x, z, salt);
        return (int) (mixed ^ (mixed >>> 32));
    }

    public static <T> int weightedIndex(long seed,
                                       long salt,
                                       int x,
                                       int z,
                                       List<T> elements,
                                       java.util.function.ToIntFunction<T> weightExtractor) {
        if (elements == null || elements.isEmpty()) {
            return -1;
        }

        int totalWeight = 0;
        int[] weights = new int[elements.size()];
        for (int i = 0; i < elements.size(); i++) {
            int w = Math.max(1, weightExtractor.applyAsInt(elements.get(i)));
            weights[i] = w;
            totalWeight += w;
        }

        if (totalWeight <= 0) {
            return 0;
        }

        long hash = mix(seed, x, z, salt);
        int pick = (int) Math.floorMod(hash, totalWeight);

        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (pick < cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }

    public static int weightedIndex(long seed,
                                    long salt,
                                    int x,
                                    int z,
                                    List<MacroBiome.MacroBiomeVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return -1;
        }

        int totalWeight = 0;
        int[] weights = new int[variants.size()];
        for (int i = 0; i < variants.size(); i++) {
            MacroBiome.MacroBiomeVariant variant = variants.get(i);
            int w = Math.max(1, variant.weight);
            weights[i] = w;
            totalWeight += w;
        }

        long hash = mix(seed, x, z, salt);
        int pick = (int) Math.floorMod(hash, totalWeight);

        int cumulative = 0;
        for (int i = 0; i < weights.length; i++) {
            cumulative += weights[i];
            if (pick < cumulative) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
