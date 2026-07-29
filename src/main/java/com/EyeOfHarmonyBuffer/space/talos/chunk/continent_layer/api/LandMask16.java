package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

/**
 * 单个 16×16 区块的 block 级海陆掩码。
 * 每个方块 1 bit，总共 256 bit = 4 个 long。
 */

public final class LandMask16 {

    private final long[] words = new long[4];

    public boolean get(int localX, int localZ) {
        int index = (localZ << 4) | localX; // 0..255
        return (words[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    public void set(int localX, int localZ) {
        int index = (localZ << 4) | localX;
        words[index >>> 6] |= 1L << (index & 63);
    }

    public void fillLand() {
        words[0] = -1L;
        words[1] = -1L;
        words[2] = -1L;
        words[3] = -1L;
    }

    public void fillOcean() {
        words[0] = 0L;
        words[1] = 0L;
        words[2] = 0L;
        words[3] = 0L;
    }

    public long[] rawWords() {
        return words;
    }
}
