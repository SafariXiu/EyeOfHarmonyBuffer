package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.*;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise.fbm2DS;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainNoise.warpedFbm2D;

/**
 * 第四层内部核心：根据 profile + worldSeedInt 计算 H_base(x,z)。
 */

public final class TerrainBaseHeight {

    private TerrainBaseHeight() {}

    /**
     * 共享大陆骨架（仅陆地宏群系）。
     *
     * 所有陆地 preset 都叠加这同一个低频场（同种子 / 同频率 / 同振幅），
     * 因此宏群系边界两侧的 h1 / h2 包含完全相同的分量，
     * 做 lerp 混合时这一部分原样穿过边界，最大尺度的起伏天然衔接，
     * 从根上消除"两侧大陆骨架不同导致的对不上"的接缝。
     *
     * 海洋 preset（oceanDepthMax > 0）不叠加，海底维持原样。
     */
    private static final double CONTINENTAL_FREQ = 1.0 / 8000.0;
    private static final double CONTINENTAL_AMP = 12.0;
    private static final int CONTINENTAL_OCTAVES = 2;

    /** 默认无群系偏移（bias=0.5）。 */
    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile) {
        return computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile, 0.5);
    }

    /**
     * 三层分解核心。
     *
     * @param biomeBias 群系级高度偏移 [0,1]，0.5=无偏移。作为 baseT 的
     *                  线性偏移整合进带内塑形（在 bandShape 之前），
     *                  取代旧系统在 TerrainEngine 里的事后 smoothstep 重映射
     *                  （后者会把平滑 bias 非线性放大成区域性抬升 / 硬切）。
     */
    public static double computeBaseHeightCore(int worldX, int worldZ,
                                               int worldSeedInt,
                                               BaseTerrainProfile profile,
                                               double biomeBias) {

        double x = worldX;
        double z = worldZ;
        long seed = (long) worldSeedInt;

        // ===== 三层分解（替代旧的"全频相加 + 一次归一化拉伸"） =====
        // 旧实现把低/中/高频揉成一个 t 再映射到整条高度带，低频偏置
        // 会把 t 钉在带内一段，导致地形失去大尺度高差、只剩窄带内抖动。
        // 新实现：
        //   base   = 低频 + 大陆骨架 -> bandShape 映射到高度带（绝对海拔，大尺度）
        //   relief = 中频（域扭曲）-> 有界起伏（丘陵/台地，不被带宽放大）
        //   detail = 高频（域扭曲）-> 有界细节（岩面/小起伏，振幅小）
        // 各层独立、有界，高原/平原/山地各自有序。

        // --- base：共享大陆骨架 + 低频，映射到高度带 ---
        double baseT = 0.5;
        if (profile.oceanDepthMax <= 0.0) {
            double lowRaw = warpedFbm2D(seed ^ 0x1234ABCDL,
                x, z,
                profile.lowFreq, profile.lowAmp, profile.lowOctaves,
                profile.lowFreq * 0.5, (1.0 / profile.lowFreq) * 0.5);
            // 大陆骨架（共享低频场）保证边界两侧基座连续
            double cont = fbm2DS(seed ^ 0xABCDEF01L,
                x, z,
                CONTINENTAL_FREQ, CONTINENTAL_AMP, CONTINENTAL_OCTAVES);
            double loAmp = octaveSum(profile.lowAmp, profile.lowOctaves);
            baseT = (lowRaw + cont) / (loAmp + CONTINENTAL_AMP) * 0.5 + 0.5;
            baseT = clamp(baseT, 0.0, 1.0);
        } else {
            // 海洋：只用低频（低频 base 映射带，海床由 applyOceanDepthLimit 处理）
            double lowRaw = warpedFbm2D(seed ^ 0x1234ABCDL,
                x, z,
                profile.lowFreq, profile.lowAmp, profile.lowOctaves,
                profile.lowFreq * 0.5, (1.0 / profile.lowFreq) * 0.5);
            double loAmp = octaveSum(profile.lowAmp, profile.lowOctaves);
            baseT = lowRaw / loAmp * 0.5 + 0.5;
            baseT = clamp(baseT, 0.0, 1.0);
        }

        // 台地 / 高原修饰（带内操作：把偏高部分向带中心拉拢，幅度减半防压扁）
        double s = profile.plateauStrength;
        if (s > 0.0) {
            double p = smoothstep(0.25, 0.75, baseT);
            baseT = lerp(baseT, 0.5, p * s * 0.5);
            baseT = clamp(baseT, 0.0, 1.0);
        }

        // 群系偏移：baseT 线性偏移（0.5=无偏移），在 bandShape 之前整合。
        // 强度 0.25：Basin(bias0.30) 下压、Alpine/Plateau(bias0.68) 上抬，
        // 但纯线性、无 smoothstep 二次放大，不会产生区域性抬升 / 硬切。
        if (biomeBias != 0.5) {
            baseT = clamp(baseT + (biomeBias - 0.5) * 0.25, 0.0, 1.0);
        }

        double lo = profile.minHeight;
        double hi = profile.maxHeight;
        if (hi <= lo) {
            hi = lo + 1.0;
        }

        // bandShape：分段 S 曲线，让高度铺满带（低地/丘陵/山地各有密度），
        // 取代旧的"一个 smoothstep 拉满整带"（后者放大带内噪声抖动）。
        double base = lo + (hi - lo) * bandShape(baseT);

        // --- relief：中频（域扭曲），有界 ---
        double reliefAmp = Math.min(18.0, profile.midAmp * 0.9);
        if (profile.midAmp > 0.0 && profile.midOctaves > 0) {
            double mid = warpedFbm2D(seed ^ 0x5678EF01L,
                x, z,
                profile.midFreq, profile.midAmp, profile.midOctaves,
                profile.midFreq * 0.5, (1.0 / profile.midFreq) * 0.35);
            double midAmp = octaveSum(profile.midAmp, profile.midOctaves);
            base += mid / midAmp * reliefAmp;
        }

        // --- detail：高频（域扭曲），有界小振幅 ---
        double detailAmp = Math.min(4.0, 1.0 + profile.highAmp * 0.6);
        if (profile.highAmp > 0.0 && profile.highOctaves > 0) {
            double hiN = warpedFbm2D(seed ^ 0x9ABCDEFFL,
                x, z,
                profile.highFreq, profile.highAmp, profile.highOctaves,
                profile.highFreq * 0.5, (1.0 / profile.highFreq) * 0.25);
            double hiAmp = octaveSum(profile.highAmp, profile.highOctaves);
            base += hiN / hiAmp * detailAmp;
        }

        return base;
    }

    /**
     * 分段高度带曲线：把归一化 t 映射到带内位置。
     * 低地段（t 小）平缓、丘陵段（t 中）起伏、山地带（t 大）陡峭，
     * 各段占比固定，避免旧的单 smoothstep 把带内噪声放大。
     */
    static double bandShape(double t) {
        if (t <= 0.0) return 0.0;
        if (t >= 1.0) return 1.0;
        double lo = smoothstep(0.0, 0.45, t);
        double mid = smoothstep(0.35, 0.80, t);
        double hi = smoothstep(0.72, 1.0, t);
        return clamp(0.26 * lo + 0.48 * mid + 0.26 * hi, 0.0, 1.0);
    }

    /** FBM 各层振幅的等比和（层间振幅折半）：amp * (2 - 0.5^(oct-1))。 */
    private static double octaveSum(double amp, int octaves) {
        if (octaves <= 0) {
            return 0.0;
        }
        return amp * (2.0 - Math.pow(0.5, octaves - 1));
    }

    /**
     * 仅对海洋 preset 生效的“深度下限收紧”。
     */
    public static double applyOceanDepthLimit(double h,
                                              BaseTerrainProfile profile,
                                              int seaLevel) {
        if (profile.oceanDepthMax <= 0.0) {
            return h;
        }
        double minY = seaLevel - profile.oceanDepthMax;
        if (h < minY) {
            double t = saturate((minY - h) / 16.0);
            h = lerp(h, minY, t);
        }
        return h;
    }
}
