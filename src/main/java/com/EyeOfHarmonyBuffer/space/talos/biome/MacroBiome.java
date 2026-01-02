package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

public enum MacroBiome {

    OCEANIC(
        0,
        new ClimateProfile(0.40f, 0.90f, 0.20f),
        new MacroHeightProfile(48.0, 68.0, -1.20f, 0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_OCEAN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SHELF, 1)
        )
    ),

    COASTAL(
        1,
        new ClimateProfile(0.65f, 0.85f, 0.30f),
        new MacroHeightProfile(62.0, 70.0, -0.40f, 0.03f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BEACH, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 1)
        )
    ),


    LOWLAND_WET(
        2,
        new ClimateProfile(0.80f, 0.95f, 0.60f),
        new MacroHeightProfile(68.0, 82.0, -0.10f, 0.04f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BASIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 1)
        )
    ),

    PLAINS_TEMPERATE(
        3,
        new ClimateProfile(0.55f, 0.65f, 0.55f),
        new MacroHeightProfile(70.0, 92.0, 0.00f, 0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_STEPPE, 1)
        )
    ),

    WARM_DRY(
        4,
        new ClimateProfile(0.85f, 0.35f, 0.20f),
        new MacroHeightProfile(70.0, 92.0, 0.05f, 0.03f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_DESERT, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_WARM_STEPPE, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    TROPICAL_HUMID(
        5,
        new ClimateProfile(1.00f, 0.95f, 0.25f),
        new MacroHeightProfile(72.0, 92.0, 0.05f, 0.06f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    COOL_FORESTED(
        6,
        new ClimateProfile(0.35f, 0.75f, 0.50f),
        new MacroHeightProfile(80.0, 102.0, 0.20f, 0.07f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_COOL_FOREST, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 1)
        )
    ),

    SUBPOLAR(
        7,
        new ClimateProfile(0.20f, 0.40f, 0.30f),
        new MacroHeightProfile(70.0, 110.0, 0.10f, 0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_SUBPOLAR_TUNDRA, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_POLAR_DESERT, 1),
            new MacroBiomeVariant(TalosBiomes.TALOS_ALPINE, 1)
        )
    ),

    MOUNTAINOUS(
        8,
        new ClimateProfile(0.30f, 0.35f, 0.80f),
        new MacroHeightProfile(92.0, 140.0, 0.45f, 0.15f),
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

    public boolean isCoastal() {
        return this == COASTAL;
    }

    public boolean isLand() {
        return !isOceanic();
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
