package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

/**
 * 单个 16×16 区块的 block 级海陆掩码。
 * 每个方块 1 bit，总共 256 bit = 4 个 long。
 */

public final class LandMask16 {

    private final long[] words = new long[4];

    /**
     * 读取局部坐标 (localX, localZ) 对应方块是否为“陆地”。
     *
     * @param localX 0..15，chunk 内局部 X
     * @param localZ 0..15，chunk 内局部 Z
     * @return true 表示陆地，false 表示海洋
     */
    public boolean get(int localX, int localZ) {
        int index = (localZ << 4) | localX; // 0..255
        return (words[index >>> 6] & (1L << (index & 63))) != 0L;
    }

    /**
     * 将局部坐标 (localX, localZ) 标记为“陆地”。
     */
    public void set(int localX, int localZ) {
        int index = (localZ << 4) | localX;
        words[index >>> 6] |= 1L << (index & 63);
    }

    /**
     * 将整个 16×16 区块填充为陆地。
     */
    public void fillLand() {
        words[0] = -1L;
        words[1] = -1L;
        words[2] = -1L;
        words[3] = -1L;
    }

    /**
     * 将整个 16×16 区块填充为海洋。
     */
    public void fillOcean() {
        words[0] = 0L;
        words[1] = 0L;
        words[2] = 0L;
        words[3] = 0L;
    }

    /**
     * 返回内部使用的位数组。
     * 只给需要做自定义序列化 / 调试的代码用，一般不要直接修改。
     */
    public long[] rawWords() {
        return words;
    }
}
