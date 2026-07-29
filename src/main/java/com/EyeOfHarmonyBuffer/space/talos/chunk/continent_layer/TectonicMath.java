package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

public final class TectonicMath {

    private TectonicMath() {}

    public static int floorDiv(int value, int divisor) {
        int q = value / divisor;
        int r = value % divisor;
        return (r < 0) ? q - 1 : q;
    }

    public static long mix64(long x) {
        x &= 0xFFFFFFFFFFFFFFFFL;
        x = (x ^ (x >>> 30)) * 0xbf58476d1ce4e5b9L & 0xFFFFFFFFFFFFFFFFL;
        x = (x ^ (x >>> 27)) * 0x94d049bb133111ebL & 0xFFFFFFFFFFFFFFFFL;
        x = x ^ (x >>> 31);
        return x & 0xFFFFFFFFFFFFFFFFL;
    }

    public static long hashLongs(long... values) {
        long h = 0x9e3779b97f4a7c15L;
        for (long v : values) {
            h ^= mix64((v + 0x9e3779b97f4a7c15L) & 0xFFFFFFFFFFFFFFFFL);
            h = mix64(h);
        }
        return h;
    }

    public static long hashInts(int... values) {
        long[] tmp = new long[values.length];
        for (int i = 0; i < values.length; i++) {
            tmp[i] = values[i] & 0xFFFFFFFFL;
        }
        return hashLongs(tmp);
    }

    public static double randUnitDouble(long seed) {
        long s = seed & 0xFFFFFFFFFFFFFFFFL;
        long mantissa = s >>> (64 - 53);
        return mantissa / (double) (1L << 53);
    }

    public static double randRange(long seed, double lo, double hi) {
        double t = randUnitDouble(seed);
        return lo + (hi - lo) * t;
    }

    public static int randRangeInt(long seed, int loInclusive, int hiInclusive) {
        if (hiInclusive <= loInclusive) return loInclusive;
        long s = seed & 0x7FFFFFFFFFFFFFFFL;
        long span = (long) hiInclusive - (long) loInclusive + 1L;
        long v = s % span;
        return (int) (loInclusive + v);
    }

    public static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }
}
