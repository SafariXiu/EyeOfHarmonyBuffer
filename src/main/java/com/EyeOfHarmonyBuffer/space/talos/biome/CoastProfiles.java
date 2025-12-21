package com.EyeOfHarmonyBuffer.space.talos.biome;

public final class CoastProfiles {

    private CoastProfiles() {}

    public static CoastProfile forMacro(MacroBiome m) {
        switch (m) {
            case ARID:
                return new CoastProfile(60, 95, 25, 50);
            case COLD:
                return new CoastProfile(70, 100, 10, 25);
            case TEMPERATE:
            default:
                return new CoastProfile(50, 90, 15, 35);
        }
    }
}
