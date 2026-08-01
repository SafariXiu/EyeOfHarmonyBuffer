package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MacroClimateConfig {

    // 陆地宏群系候选列表
    private static final List<MacroPackageId> LAND_TROPIC;
    private static final List<MacroPackageId> LAND_SUBTROPIC;
    private static final List<MacroPackageId> LAND_TEMPERATE;
    private static final List<MacroPackageId> LAND_SUBPOLAR;
    private static final List<MacroPackageId> LAND_POLAR;

    // 海洋目前全纬度统一一套宏群系（OCEANIC）
    private static final List<MacroPackageId> OCEAN_ALL;

    static {
        LAND_TROPIC = Arrays.asList(
            MacroPackageId.TROPICAL_HUMID,
            MacroPackageId.TROPICAL_DRY
        );

        LAND_SUBTROPIC = Arrays.asList(
            MacroPackageId.TROPICAL_DRY,
            MacroPackageId.TEMPERATE_LOWLAND
        );

        LAND_TEMPERATE = Arrays.asList(
            MacroPackageId.TEMPERATE_LOWLAND,
            MacroPackageId.TEMPERATE_FORESTED,
            MacroPackageId.TEMPERATE_HIGHLAND
        );

        LAND_SUBPOLAR = Arrays.asList(
            MacroPackageId.COOL_FORESTED,
            MacroPackageId.TEMPERATE_HIGHLAND
        );

        LAND_POLAR = Arrays.asList(
            MacroPackageId.SUBPOLAR_TUNDRA,
            MacroPackageId.POLAR_HIGHLAND
        );

        OCEAN_ALL = Collections.singletonList(MacroPackageId.OCEANIC);
    }

    public static List<MacroPackageId> getPackagesFor(boolean isLand, ClimateLatitudes.Belt belt) {
        if (!isLand) {
            return OCEAN_ALL;
        }

        switch (belt) {
            case TROPIC:
                return LAND_TROPIC;
            case SUBTROPIC:
                return LAND_SUBTROPIC;
            case TEMPERATE:
                return LAND_TEMPERATE;
            case SUBPOLAR:
                return LAND_SUBPOLAR;
            case POLAR:
            default:
                return LAND_POLAR;
        }
    }
}
