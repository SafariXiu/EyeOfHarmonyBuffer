package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api;

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
        double div = TalosLandMask.maxBoundaryStrength(
            PlateBoundaryState.DIVERGENT, s
        );
        if (div >= TalosLandMask.PLATE_BOUNDARY_MIN_STRENGTH) {
            return TectonicStyle.RIFT;
        }
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
        TalosLandMask.Sample s = TalosLandMask.sampleFull(
            worldX, worldZ, worldSeedInt
        );
        return tierFromStyle(styleFromSample(s));
    }
}
