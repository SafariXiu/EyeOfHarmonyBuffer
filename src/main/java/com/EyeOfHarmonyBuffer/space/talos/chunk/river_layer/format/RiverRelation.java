package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

public enum RiverRelation {
    ROOT,
    INTO_PARENT,
    FROM_PARENT;

    public static RiverRelation fromCode(int code) {
        switch (code) {
            case 0: return ROOT;
            case 1: return INTO_PARENT;
            case 2: return FROM_PARENT;
            default:
                throw new IllegalArgumentException("Unknown RiverRelation code: " + code);
        }
    }
}
