package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;

/**
 * 群系 / 宏群系边界的「域扭曲」（domain warp）。
 *
 * Worley / Voronoi 站点的边界天然是直线，三站点交汇还是尖角。
 * 在投票前把查询坐标用确定性低频噪声弯曲，直线边界就会变成有机曲线，
 * 直角 / 尖角也随之消失。结果是 g(warp(x, z))，仍然完全确定性。
 */
final class ClimateDomainWarp {

    private ClimateDomainWarp() {}

    /** 扭曲振幅（blocks）：越大边界越蜿蜒。 */
    private static final double WARP_AMPLITUDE = 200.0;

    /** 扭曲频率：波长 = 1 / 频率 = 1200 blocks。 */
    private static final double WARP_FREQUENCY = 1.0 / 1200.0;

    /** 返回扭曲后的世界坐标 {x, z}。 */
    static double[] warp(int worldX, int worldZ, int worldSeedInt) {
        long seed = worldSeedInt & 0xFFFFFFFFL;

        double fx = worldX * WARP_FREQUENCY;
        double fz = worldZ * WARP_FREQUENCY;

        double n1 = NoiseUtil.coreNoise2D(
            fx, fz, (int) (seed ^ 0xA1B2C3D4L)
        ) * 2.0 - 1.0;
        double n2 = NoiseUtil.coreNoise2D(
            fx + 317.0, fz + 541.0, (int) (seed ^ 0xD4E5F6A7L)
        ) * 2.0 - 1.0;

        return new double[] {
            worldX + n1 * WARP_AMPLITUDE,
            worldZ + n2 * WARP_AMPLITUDE
        };
    }
}
