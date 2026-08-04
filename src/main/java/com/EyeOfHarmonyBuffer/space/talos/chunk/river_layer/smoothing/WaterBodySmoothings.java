package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format.RiverBodyType;

/** 水体类型 → 平滑策略的工厂。 */
public final class WaterBodySmoothings {

    private static final WaterBodySmoothing LAKE = new LakeSmoothing();
    private static final WaterBodySmoothing THROUGH = new ThroughLakeSmoothing();
    private static final WaterBodySmoothing OXBOW = new OxbowLakeSmoothing();
    private static final WaterBodySmoothing WETLAND = new WetlandSmoothing();

    private WaterBodySmoothings() {}

    public static WaterBodySmoothing forType(RiverBodyType type) {
        switch (type) {
            case LAKE:
                return LAKE;
            case THROUGH_LAKE:
                return THROUGH;
            case OXBOW_LAKE:
                return OXBOW;
            case WETLAND:
                return WETLAND;
            default:
                throw new IllegalArgumentException(
                    "No smoothing for body type: " + type
                );
        }
    }
}
