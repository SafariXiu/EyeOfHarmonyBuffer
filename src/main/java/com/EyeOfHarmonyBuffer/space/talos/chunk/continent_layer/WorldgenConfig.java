package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：WorldgenConfig
 * 来源：Python 模块 worldgen_core 全局常量定义部分
 * 功能：
 *   - 提供世界生成算法中的基础参数常量；
 *   - 所有参数保持与 Python 版本一致；
 *   - 用于超级大陆生成、噪声扰动等基础逻辑。
 * =====================================================
 */

public class WorldgenConfig {

    public static final int SUPER_GRID_SIZE = 18000;
    public static final int SUPER_MIN_RADIUS = 4000;
    public static final int SUPER_MAX_RADIUS = 9000;
    public static final int SUPER_MAX_INFLUENCE = 40000;
    public static final double LATITUDE_CYCLE = 10000.0;

    // 噪声扰动参数
    public static final double BIG_AMP_RATIO = 0.6;
    public static final double SMALL_AMP_RATIO = 0.08;
    public static final double BIG_FREQ_SCALE = 0.002;
    public static final double SMALL_FREQ_SCALE = 0.0006;
}
