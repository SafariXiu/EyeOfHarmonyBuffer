package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.integration;

/**
 * 山地高度配置：把「归一化高程 → 绝对高度」的换算与 Y 轴上限解耦。
 *
 * 现在 Minecraft 世界高度 256，峰顶沿用旧值（PEAK 252 等）；
 * 未来 GTNH 接入突破 Y 轴上限的模组后，把新的世界实际高度传进来，
 * 峰顶会按比例抬到接近新天花板：
 *   PEAK      ≈ 99% × (worldHeight - 2)
 *   MOUNTAINS ≈ 94% × (worldHeight - 2)
 *   HIGHLAND  ≈ 85% × (worldHeight - 2)
 * 谷底固定在海平面之上（PEAK 90 / MOUNTAINS 78 / HIGHLAND 68），
 * 高度越高落差越大——万米高山无需改生成算法。
 *
 * 注意：本类只负责山地层的换算；基础地形宏包预设（TerrainMacroPresetRegistry）
 * 仍按 256 高度标定，突破 Y 轴后如需整体抬升，那是地形层另一处改动。
 */
public final class MountainHeightProfile {

    /** 256 高度世界下的峰顶（保持旧观感，避免比例换算提前改变现状）。 */
    private static final double PEAK_LEGACY = 252.0;
    private static final double MOUNTAINS_LEGACY = 240.0;
    private static final double HIGHLAND_LEGACY = 216.0;

    private final double maxHeight;

    public MountainHeightProfile(double maxHeight) {
        this.maxHeight = maxHeight;
    }

    /** 由世界实际高度构造（自动留 2 格余量，与 ChunkProviderTalos2 钳制一致）。 */
    public static MountainHeightProfile ofWorldHeight(int worldHeight) {
        return new MountainHeightProfile(worldHeight - 2.0);
    }

    /** 可用高度上限（blocks）。 */
    public double maxHeight() {
        return maxHeight;
    }

    /** 山带类型的谷底高度（blocks，海平面 64 之上；不随世界高度变化）。 */
    public double valleyForKind(int kind) {
        switch (kind) {
            case 3: // PEAK
                return 90.0;
            case 2: // MOUNTAINS
                return 78.0;
            default: // 1 = HIGHLAND
                return 68.0;
        }
    }

    /** 山带类型的峰顶高度（blocks；随世界高度按比例抬升）。 */
    public double peakForKind(int kind) {
        switch (kind) {
            case 3:
                return Math.max(PEAK_LEGACY, maxHeight * 0.99);
            case 2:
                return Math.max(MOUNTAINS_LEGACY, maxHeight * 0.94);
            default:
                return Math.max(HIGHLAND_LEGACY, maxHeight * 0.85);
        }
    }
}
