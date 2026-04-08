package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：WorldgenMath
 * 来源：Python 模块 worldgen_core 哈希/ID生成部分
 * 功能：
 *   - 用于生成 super_id, continent_id；
 *   - 提供基础哈希函数；
 * =====================================================
 */

public class WorldgenMath {

    /** 生成超级大陆ID */
    public static int makeSuperId(int gx, int gz, int worldSeed) {
        long n = gx * 374761393L + gz * 668265263L + worldSeed * 69069L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n = (n ^ (n >> 16)) & 0x7FFFFFFFL;
        return n != 0 ? (int) n : 1;
    }

    /** 生成子大陆/板块ID */
    public static int makeSubContinentId(int superId, int localIndex) {
        long n = superId * 374761393L + localIndex * 668265263L + 1234567L;
        n = (n ^ (n >> 13)) * 1274126177L;
        n = (n ^ (n >> 16)) & 0x7FFFFFFFL;
        return n != 0 ? (int) n : 1;
    }

    /** 简单随机权重 */
    public static double hash2(int ix, int iz, int seed) {
        return NoiseUtil.hash2(ix, iz, seed);
    }
}
