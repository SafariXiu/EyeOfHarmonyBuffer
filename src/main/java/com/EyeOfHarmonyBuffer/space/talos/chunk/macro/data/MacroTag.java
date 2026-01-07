package com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

public enum MacroTag {
    OCEAN,
    SHELF,
    BEACH,
    PLAINS,
    SAVANNA,
    DESERT,
    STEPPE,
    COOL_FOREST,
    TROPICAL,
    TUNDRA,
    MOUNTAIN,
    BASIN,
    POLAR_DESERT,
    ALPINE;

    public boolean isOceanic() {
        return switch (this) {
            case OCEAN, SHELF -> true;
            default -> false;
        };
    }

    public boolean isCoastal() {
        return switch (this) {
            case BEACH, SHELF -> true;
            default -> false;
        };
    }

    public boolean isFrozen() {
        return switch (this) {
            case TUNDRA, POLAR_DESERT, ALPINE -> true;
            default -> false;
        };
    }

    public boolean isArid() {
        return switch (this) {
            case DESERT, STEPPE, SAVANNA -> true;
            default -> false;
        };
    }

    public boolean isLand() {
        return !isOceanic();
    }

    public static MacroTag pick(double continental, double humidity, double temperature) {
        if (continental < -0.2) {
            return OCEAN;
        }
        if (continental < 0.0) {
            return SHELF;
        }
        if (continental < 0.05) {
            return BEACH;
        }

        if (temperature > 0.75) {
            return (humidity > 0.6) ? TROPICAL : DESERT;
        }
        if (temperature < 0.25) {
            return (humidity > 0.5) ? TUNDRA : POLAR_DESERT;
        }
        return PLAINS;
    }

    public MacroBiome toMacroBiome() {
        return switch (this) {
            case OCEAN, SHELF -> MacroBiome.OCEANIC;
            case BEACH -> MacroBiome.COASTAL;
            case BASIN, TROPICAL -> MacroBiome.LOWLAND_WET;
            case PLAINS -> MacroBiome.PLAINS_TEMPERATE;
            case SAVANNA, STEPPE, DESERT -> MacroBiome.WARM_DRY;
            case COOL_FOREST -> MacroBiome.COOL_FORESTED;
            case TUNDRA, POLAR_DESERT -> MacroBiome.SUBPOLAR;
            case ALPINE, MOUNTAIN -> MacroBiome.MOUNTAINOUS;
            default -> MacroBiome.PLAINS_TEMPERATE;
        };
    }

    public boolean isHumid() {
        return switch (this) {
            case BASIN, TROPICAL, SAVANNA, COOL_FOREST, TUNDRA -> true;
            default -> false;
        };
    }
}
