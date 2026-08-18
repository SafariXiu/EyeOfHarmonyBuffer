package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * 翡翠王座地形共享逻辑：ChunkProviderEmeraldThrone 与 WorldChunkManagerEmeraldThrone
 * 共用同一套噪声参数与高度公式，保证「按 y=64 划分群系」与地形严格同步。
 *
 * <p>大陆骨架：极低频噪声（波长约 800 格）量化为阶梯 zone，每级 30 格高度差，
 * 形成一大块一大块的连续大陆 / 深海；细节起伏只有 ±6 格，高度封顶 140，
 * 不会再顶到世界顶被削平。
 */
public final class EmeraldThroneTerrain {

    /** 死亡之海 / 生之大陆分界（y ≤ 64 = 死亡之海，y &gt; 64 = 生之大陆）。 */
    public static final int SEA_LEVEL = 64;

    /** 高度下限：仅做越界兜底（深谷理论上限 ~10，正常不会触发）。 */
    private static final int MIN_HEIGHT = 10;

    private EmeraldThroneTerrain() {
    }

    /** 与 ChunkProviderEmeraldThrone 完全一致的噪声实例（顺序敏感：同 seed 同序列）。 */
    public static NoiseGeneratorPerlin[] createNoises(long seed) {
        Random rand = new Random(seed);
        return new NoiseGeneratorPerlin[] {
            new NoiseGeneratorPerlin(rand, 2), // continent：大块大陆（低频、少 octave，输出平滑）
            new NoiseGeneratorPerlin(rand, 4), // detail：大陆内部起伏
            new NoiseGeneratorPerlin(rand, 3), // rock：保留占位（全石头星球暂未使用）
            new NoiseGeneratorPerlin(rand, 2), // peak：高峰层（极低频，高尾平方放大出 200+ 巨峰）
        };
    }

    /**
     * 采样某列最终地表高度（连续平滑噪声，无阶梯、不削顶）。
     * <p>普通地形：64 + c*26 + d*7，极值约 168（MC simplex 输出会超理论 ±1，实测高尾 ~180）；
     * 高峰层 peak 只在 p &gt; 0.9 的高尾做平方放大并封顶（min(85, (p-0.9)² × 300)），
     * boost ≤ 85 数学上保证总高度 ≤ 253（h_base ≤ 168），不会削平；
     * 约 3~5% 区域越过 200（巨峰带）。
     * 想调山峰：阈值 0.9（越低越多）、系数 300（越大越高）、封顶 85（越高越险）、频率 0.0006（越小越庞大）。
     *
     * @param maxHeight 高度上限（世界高度 - 2）
     * @return 高度（列的最高实心方块 y）；y ≤ SEA_LEVEL 为死亡之海，否则为生之大陆
     */
    public static int sampleHeight(NoiseGeneratorPerlin continent, NoiseGeneratorPerlin detail,
        NoiseGeneratorPerlin rock, NoiseGeneratorPerlin peak, int wx, int wz, int maxHeight) {
        double c = continent.func_151601_a(wx * 0.0012D, wz * 0.0012D);
        double d = detail.func_151601_a(wx * 0.02D, wz * 0.02D);

        double boost = 0.0D;
        double p = peak.func_151601_a(wx * 0.0006D, wz * 0.0006D);
        if (p > 0.9D) {
            double t = p - 0.9D;
            boost = Math.min(85.0D, t * t * 300.0D);
        }

        int h = (int) Math.round(SEA_LEVEL + c * 26.0D + d * 7.0D + boost);
        if (h < MIN_HEIGHT) {
            h = MIN_HEIGHT;
        }
        if (h > maxHeight) {
            h = maxHeight;
        }
        return h;
    }
}
