package com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

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

    private static final Map<MacroBiome, MacroTag> CANONICAL_BY_BIOME;

    static {
        EnumMap<MacroBiome, MacroTag> map = new EnumMap<>(MacroBiome.class);
        map.put(MacroBiome.OCEANIC, OCEAN);
        map.put(MacroBiome.COASTAL, BEACH);
        map.put(MacroBiome.LOWLAND_WET, TROPICAL);
        map.put(MacroBiome.PLAINS_TEMPERATE, PLAINS);
        map.put(MacroBiome.WARM_DRY, DESERT);
        map.put(MacroBiome.COOL_FORESTED, COOL_FOREST);
        map.put(MacroBiome.SUBPOLAR, TUNDRA);
        map.put(MacroBiome.MOUNTAINOUS, MOUNTAIN);
        map.put(MacroBiome.TROPICAL_HUMID, TROPICAL);
        CANONICAL_BY_BIOME = Collections.unmodifiableMap(map);
    }

    public static MacroTag fromBiome(MacroBiome biome) {
        MacroTag tag = CANONICAL_BY_BIOME.get(biome);
        if (tag == null) {
            throw new IllegalArgumentException("No MacroTag mapping defined for biome: " + biome);
        }
        return tag;
    }

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
