package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.format;

public enum RiverType {
    MAIN,
    BRANCH1,
    BRANCH2;

    public static RiverType fromCode(int code) {
        switch (code) {
            case 0: return MAIN;
            case 1: return BRANCH1;
            case 2: return BRANCH2;
            default:
                throw new IllegalArgumentException("Unknown RiverType code: " + code);
        }
    }
}
