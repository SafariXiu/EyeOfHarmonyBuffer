package com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainBaseHeight.applyOceanDepthLimit;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainBaseHeight.computeBaseHeightCore;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.clamp;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.lerp;
import static com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.TerrainMath.smoothstep;

/**
 * 层4内部调度引擎：
 *   - 通过 TalosLandMask 拿海陆 + 权重；
 *   - 通过 TalosMacroClimate 拿宏群系 ID / 混合信息；
 *   - 通过 TerrainMacroPresetRegistry 拿地形 preset；
 *   - 通过 TerrainBaseHeight 计算基础高度；
 *   - 用 landWeight / coastWeight 做轻量约束和海岸平滑。
 */

public final class TerrainEngine {

    private TerrainEngine() {}

    public static double sampleBaseHeight(int worldX, int worldZ,
                                          int worldSeedInt,
                                          int seaLevel) {
        return sampleBaseHeight(
            worldX, worldZ, worldSeedInt, seaLevel,
            TalosLandMask.sampleFull(worldX, worldZ, worldSeedInt),
            0.5, 1.0
        );
    }

    /**
     * chunk 级上下文版本：复用调用方已经算好的 LandSample。
     * 结果与无缓存版本完全一致（同一确定性函数）。
     */
    public static double sampleBaseHeight(
        int worldX, int worldZ, int worldSeedInt, int seaLevel,
        TalosLandMask.Sample landSample
    ) {
        return sampleBaseHeight(
            worldX, worldZ, worldSeedInt, seaLevel, landSample,
            0.5, 1.0
        );
    }

    /**
     * 群系级高度调制：宏包带内做 bias/scale 微调。
     * 平滑后的参数由调用方（TalosChunkContext）传入。
     */
    public static double sampleBaseHeight(
        int worldX, int worldZ, int worldSeedInt, int seaLevel,
        TalosLandMask.Sample landSample,
        double biomeBias, double biomeScale
    ) {

        boolean isLand      = landSample != null && landSample.isLand;
        double  landWeight  = (landSample != null) ? landSample.landWeight  : 0.0;
        double  coastWeight = (landSample != null) ? landSample.coastWeight : 0.0;

        TalosMacroClimate.MacroBlendSample blend =
            TalosMacroClimate.sampleMacroBlend(
                worldX, worldZ, worldSeedInt, 2, isLand
            );

        MacroPackageId primaryId;
        MacroPackageId secondaryId;
        double w1, w2;

        if (blend != null && blend.entries != null && blend.entries.length > 0) {
            primaryId = blend.entries[0].id;
            w1        = blend.entries[0].weight;

            if (blend.entries.length > 1) {
                secondaryId = blend.entries[1].id;
                w2          = blend.entries[1].weight;
            } else {
                secondaryId = primaryId;
                w2          = 0.0;
            }
        } else {
            primaryId =
                TalosMacroClimate.getMacroPackageId(worldX, worldZ, worldSeedInt);
            secondaryId = primaryId;
            w1 = 1.0;
            w2 = 0.0;
        }

        if (!isLand) {
            primaryId   = MacroPackageId.OCEANIC;
            secondaryId = MacroPackageId.OCEANIC;
            w1 = 1.0;
            w2 = 0.0;
        } else {
            if (primaryId == MacroPackageId.OCEANIC &&
                secondaryId != MacroPackageId.OCEANIC) {

                primaryId = secondaryId;
                w1 = w1 + w2;
                w2 = 0.0;

            } else if (secondaryId == MacroPackageId.OCEANIC &&
                primaryId != MacroPackageId.OCEANIC) {

                w1 = 1.0;
                w2 = 0.0;

            } else if (primaryId == MacroPackageId.OCEANIC &&
                secondaryId == MacroPackageId.OCEANIC) {

                primaryId   = MacroPackageId.TEMPERATE_LOWLAND;
                secondaryId = primaryId;
                w1 = 1.0;
                w2 = 0.0;
            }
        }

        BaseTerrainPreset  preset1  = TerrainMacroPresetRegistry.get(primaryId);
        BaseTerrainPreset  preset2  = TerrainMacroPresetRegistry.get(secondaryId);

        BaseTerrainProfile profile1 = BaseTerrainProfile.fromPreset(preset1);
        BaseTerrainProfile profile2 = BaseTerrainProfile.fromPreset(preset2);

        double h1 = computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile1);
        double h2 = computeBaseHeightCore(worldX, worldZ, worldSeedInt, profile2);

        double t;
        double sumW = w1 + w2;
        if (sumW > 0.0) {
            t = w2 / sumW;
        } else {
            t = 0.0;
        }

        t = smoothstep(0.2, 0.8, t);

        double h = lerp(h1, h2, t);

        // 群系调制：在宏包混合带 [lo, hi] 内调整相对位置。
        // 宏包带始终是硬边界，调制不会越出带外。
        if (biomeBias != 0.5 || biomeScale != 1.0) {
            double lo = lerp(profile1.minHeight, profile2.minHeight, t);
            double hi = lerp(profile1.maxHeight, profile2.maxHeight, t);

            double tt = (hi > lo) ? (h - lo) / (hi - lo) : 0.5;
            tt = clamp(tt, 0.0, 1.0);
            tt = clamp(biomeBias + (tt - 0.5) * biomeScale, 0.0, 1.0);

            h = lo + (hi - lo) * smoothstep(0.0, 1.0, tt);
        }

        if (primaryId == MacroPackageId.OCEANIC) {
            h = applyOceanDepthLimit(h, profile1, seaLevel);
        }

        h = applyCoastSmooth(h, coastWeight, seaLevel, isLand);

        return h;
    }

    /**
     * 海岸平滑：保持你原来的逻辑，只做相对柔和的一点点调整。
     * 这里的前提是：经过 applyLandOceanShapingWithWeight 之后（如果启用），
     * 不会再出现把整条内陆边界压到 seaLevel 的那种极端情况。
     */
    private static double applyCoastSmooth(double h,
                                           double coastWeight,
                                           int seaLevel,
                                           boolean isLand) {
        if (coastWeight <= 0.0) {
            return h;
        }

        if (!isLand) {
            return h;
        }

        double blend = 0.4 * coastWeight;
        double target = seaLevel + (h - seaLevel) * 0.6;

        return lerp(h, target, blend);
    }
}
