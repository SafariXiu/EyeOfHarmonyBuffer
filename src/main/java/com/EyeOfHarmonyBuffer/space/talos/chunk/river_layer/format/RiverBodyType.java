package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

public enum RiverBodyType {
    THROUGH_LAKE,
    OXBOW_LAKE,
    LAKE,
    WETLAND;

    public static RiverBodyType fromCode(int code) {
        switch (code) {
            case 0: return THROUGH_LAKE;
            case 1: return OXBOW_LAKE;
            case 2: return LAKE;
            case 3: return WETLAND;
            default:
                throw new IllegalArgumentException("Unknown RiverBodyType code: " + code);
        }
    }

    /** 独立水体：不挂河，parentEdgeId 必须为 -1。 */
    public boolean isStandalone() {
        return this == LAKE || this == WETLAND;
    }
}
