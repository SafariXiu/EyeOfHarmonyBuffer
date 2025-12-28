package com.EyeOfHarmonyBuffer.space.talos.biome;

public final class CoastProfiles {

    private CoastProfiles() {}

    private static final CoastProfile PROFILE_WARM_DRY     = new CoastProfile(60, 95, 25, 50);
    private static final CoastProfile PROFILE_TROPICAL     = new CoastProfile(55, 90, 18, 40);
    private static final CoastProfile PROFILE_TEMPERATE    = new CoastProfile(50, 90, 15, 35);
    private static final CoastProfile PROFILE_COOL         = new CoastProfile(65, 105, 12, 28);
    private static final CoastProfile PROFILE_SUBPOLAR     = new CoastProfile(70, 110, 10, 24);
    private static final CoastProfile PROFILE_MOUNTAINOUS  = new CoastProfile(45, 85, 12, 30);
    private static final CoastProfile PROFILE_DEFAULT      = PROFILE_TEMPERATE;

    public static CoastProfile forMacro(MacroBiome m) {
        if (m == null) {
            return PROFILE_DEFAULT;
        }

        if (m == MacroBiome.WARM_DRY || m == MacroBiome.LOWLAND_WET) {
            return PROFILE_WARM_DRY;
        }

        if (m == MacroBiome.TROPICAL_HUMID) {
            return PROFILE_TROPICAL;
        }

        if (m == MacroBiome.COOL_FORESTED) {
            return PROFILE_COOL;
        }

        if (m == MacroBiome.SUBPOLAR) {
            return PROFILE_SUBPOLAR;
        }

        if (m == MacroBiome.MOUNTAINOUS) {
            return PROFILE_MOUNTAINOUS;
        }

        // 其余（如 PLAINS_TEMPERATE、COASTAL 等）归入默认温带
        return PROFILE_TEMPERATE;
    }
}
