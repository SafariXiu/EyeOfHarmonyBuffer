package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;

/**
 * 构造风格判定（唯一来源）。
 *
 * 规则与 TectonicStyleLayer.sampleGrid 完全一致；
 * 山地层条带识别也走这里，避免阈值规则两处维护导致漂移。
 */
public final class TalosTectonicStyles {

    private TalosTectonicStyles() {}

    /** 由海陆采样结果判定构造风格（含 RIFT / NONE）。 */
    public static TectonicStyle styleFromSample(TalosLandMask.Sample s) {
        if (s == null || !s.isLand) {
            return TectonicStyle.NONE;
        }
        // 分离带（DIVERGENT）不再接管地形/群系：完全按普通群系选择，
        // 平滑由气候层处理。仅挤压带（CONVERGENT）继续产生高山风格。
        if (s.plateBoundaryState != PlateBoundaryState.CONVERGENT) {
            return TectonicStyle.NONE;
        }
        double w = s.plateBoundaryWeight;
        if (w >= 0.7) {
            return TectonicStyle.PEAK;
        }
        if (w > 0.5) {
            return TectonicStyle.MOUNTAINS;
        }
        if (w >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return TectonicStyle.HIGHLAND;
        }
        return TectonicStyle.NONE;
    }

    /** 山地层级：0=非山地，1=HIGHLAND，2=MOUNTAINS，3=PEAK。 */
    public static int tierFromStyle(TectonicStyle style) {
        if (style == null) {
            return 0;
        }
        switch (style) {
            case PEAK:
                return 3;
            case MOUNTAINS:
                return 2;
            case HIGHLAND:
                return 1;
            default:
                return 0;
        }
    }

    /** 便捷版：按世界坐标采样一次并返回山地层级。 */
    public static int tierAt(int worldX, int worldZ, int worldSeedInt) {
        if (V2TerrainConfigSection.terrainV2Enabled) {
            return v2TierAt(worldX, worldZ, worldSeedInt);
        }
        TalosLandMask.Sample s = TalosLandMask.sampleFull(
            worldX, worldZ, worldSeedInt
        );
        return tierFromStyle(styleFromSample(s));
    }

    // ==================== V2 轨：山带场 ====================

    /**
     * V2 山带大区包络波长（blocks）与阈值（bandNoise 同域扭曲、独立盐）。
     * 包络把山带限定在若干大区（每大陆数片），避免 dev 走廊全球连通把整片大陆
     * 变成一个巨型 DLA 山带（探针：beltMask01≥0.55 占陆 51~62%，必须包络截断）。
     */
    private static final double V2_ENV_FREQ = 1.0 / 60_000.0;
    private static final double V2_ENV_MIN = 0.40;
    private static final long V2_ENV_SALT = 0x517CC1L;

    /**
     * V2 山带层级（预算 ≈ 陆地面积 8~10% 山地核 + 5~7% 低山缘 + ~1% 峰，探针标定）：
     *   存在域：陆地 && 包络带内（env ≥ 0.40）；
     *   tier3 PEAK：kind=PEAK && belt≥0.72 && elev≥0.30；
     *   tier2 MOUNTAINS：kind∈{MOUNTAIN,PEAK} && belt≥0.55 && elev≥0.30；
     *   tier1 HIGHLAND：kind∈{MOUNTAIN,PEAK} && belt≥0.45 && elev≥0.18。
     * 判定点 = 64 格中心（MountainWorldState 调用约定）。
     */
    private static int v2TierAt(int worldX, int worldZ, int worldSeedInt) {
        OrographyField.OroSample o = OrographyField.sample(worldX, worldZ, worldSeedInt);
        if (!o.isLand) {
            return 0;
        }
        int kind = o.kind;
        if (kind != OrographyField.KIND_MOUNTAIN && kind != OrographyField.KIND_PEAK) {
            return 0;
        }
        if (NoiseContinentGrid.bandNoise(
            worldX, worldZ, worldSeedInt, V2_ENV_SALT, V2_ENV_FREQ, 2) < V2_ENV_MIN) {
            return 0;
        }
        double belt = o.beltMask01;
        double e = o.elevation01;
        if (kind == OrographyField.KIND_PEAK && belt >= 0.72 && e >= 0.30) {
            return 3;
        }
        if (belt >= 0.55 && e >= 0.30) {
            return 2;
        }
        if (belt >= 0.45 && e >= 0.18) {
            return 1;
        }
        return 0;
    }
}
