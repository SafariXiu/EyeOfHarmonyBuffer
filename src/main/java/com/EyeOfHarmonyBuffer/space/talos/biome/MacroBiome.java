package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

public enum MacroBiome {

    OCEANIC(
        0,
        new ClimateProfile(0.42f, 0.95f, 0.08f),
        new MacroHeightProfile(42.0, 60.0, -1.30f, 0.04f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_OCEAN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SHELF, 1)
        )
    ),

    COASTAL(
        1,
        new ClimateProfile(0.58f, 0.82f, 0.22f),
        new MacroHeightProfile(58.0, 72.0, -0.35f, 0.04f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BEACH, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 1)
        )
    ),

    LOWLAND_WET(
        2,
        new ClimateProfile(0.75f, 0.98f, 0.18f),
        new MacroHeightProfile(64.0, 78.0, -0.05f, 0.03f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BASIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 1)
        )
    ),

    PLAINS_TEMPERATE(
        3,
        new ClimateProfile(0.52f, 0.60f, 0.32f),
        new MacroHeightProfile(68.0, 96.0, 0.02f, 0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_STEPPE, 1)
        )
    ),

    WARM_DRY(
        4,
        new ClimateProfile(0.82f, 0.30f, 0.28f),
        new MacroHeightProfile(70.0, 100.0, 0.06f, 0.06f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_DESERT, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_WARM_STEPPE, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    TROPICAL_HUMID(
        5,
        new ClimateProfile(0.95f, 0.92f, 0.24f),
        new MacroHeightProfile(72.0, 90.0, 0.04f, 0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    COOL_FORESTED(
        6,
        new ClimateProfile(0.32f, 0.70f, 0.45f),
        new MacroHeightProfile(82.0, 116.0, 0.18f, 0.08f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_COOL_FOREST, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 1)
        )
    ),

    SUBPOLAR(
        7,
        new ClimateProfile(0.18f, 0.45f, 0.50f),
        new MacroHeightProfile(92.0, 132.0, 0.22f, 0.09f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_SUBPOLAR_TUNDRA, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_POLAR_DESERT, 1),
            new MacroBiomeVariant(TalosBiomes.TALOS_ALPINE, 1)
        )
    ),

    MOUNTAINOUS(
        8,
        new ClimateProfile(0.25f, 0.35f, 0.80f),
        new MacroHeightProfile(110.0, 180.0, 0.48f, 0.16f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_MOUNTAINS, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_PLATEAU, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_ALPINE, 1)
        )
    );

    public final int id;
    public final ClimateProfile climate;
    public final MacroHeightProfile height;
    public final List<MacroBiomeVariant> variants;

    MacroBiome(int id,
               ClimateProfile climate,
               MacroHeightProfile height,
               List<MacroBiomeVariant> variants) {
        this.id = id;
        this.climate = climate;
        this.height = height;
        this.variants = variants != null
            ? variants
            : java.util.Collections.emptyList();
    }

    public int getId() {
        return id;
    }

    public static final class ClimateProfile {
        public final float temperature;
        public final float humidity;
        public final float roughness;

        public ClimateProfile(float temperature, float humidity, float roughness) {
            this.temperature = temperature;
            this.humidity = humidity;
            this.roughness = roughness;
        }
    }

    public static final class MacroHeightProfile {
        public final double absoluteMin;
        public final double absoluteMax;
        public final float baseHeightOffset;
        public final float heightVariation;

        public MacroHeightProfile(double absoluteMin,
                                  double absoluteMax,
                                  float baseHeightOffset,
                                  float heightVariation) {
            this.absoluteMin = absoluteMin;
            this.absoluteMax = absoluteMax;
            this.baseHeightOffset = baseHeightOffset;
            this.heightVariation = heightVariation;
        }
    }

    public static final class MacroBiomeVariant {
        public final BiomeGenBase biome;
        public final int weight;

        public MacroBiomeVariant(BiomeGenBase biome, int weight) {
            this.biome = biome;
            this.weight = weight;
        }
    }

    private static List<MacroBiomeVariant> variants(MacroBiomeVariant... entries) {
        return java.util.Arrays.asList(entries);
    }

    public boolean isOceanic() {
        return this == OCEANIC;
    }

    public boolean isHardEdge() {
        switch (this) {
            case MOUNTAINOUS:
            case SUBPOLAR:
                return true;
            default:
                return false;
        }
    }
    public boolean isCoastal() {
        return this == COASTAL;
    }

    public boolean isLand() {
        return !isOceanic();
    }

    public float getPlateauAnchorWeight() {
        switch (this) {
            case OCEANIC:
            case COASTAL:
                return 0.0f;
            case LOWLAND_WET:
                return 0.05f;
            case PLAINS_TEMPERATE:
            case WARM_DRY:
            case TROPICAL_HUMID:
                return 0.10f;
            case COOL_FORESTED:
                return 0.20f;
            case SUBPOLAR:
                return 0.35f;
            case MOUNTAINOUS:
                return 1.0f;
            default:
                return 0.0f;
        }
    }

    private static int adjustBrightness(int rgb, float delta) {
        float factor = 1.0f + delta;
        int r = clamp((int) (((rgb >> 16) & 0xFF) * factor));
        int g = clamp((int) (((rgb >> 8) & 0xFF) * factor));
        int b = clamp((int) ((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }
}
