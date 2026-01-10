package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.MacroDomain;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;
import java.util.Objects;

public enum MacroBiome {

    OCEANIC(
        MacroDomain.OCEAN,
        0,
        new ClimateProfile(0.42f, 0.95f),
        new MacroHeightProfile(42.0, 64.0, -1.30f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_OCEAN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SHELF, 1)
        )
    ),

    COASTAL(
        MacroDomain.LAND,
        1,
        new ClimateProfile(0.58f, 0.82f),
        new MacroHeightProfile(64.0, 72.0, -0.35f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BEACH, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 1)
        )
    ),

    LOWLAND_WET(
        MacroDomain.LAND,
        2,
        new ClimateProfile(0.75f, 0.98f),
        new MacroHeightProfile(64.0, 78.0, -0.05f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_BASIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 1)
        )
    ),

    PLAINS_TEMPERATE(
        MacroDomain.LAND,
        3,
        new ClimateProfile(0.52f, 0.60f),
        new MacroHeightProfile(68.0, 96.0, 0.02f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_PLAINS, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_STEPPE, 1)
        )
    ),

    WARM_DRY(
        MacroDomain.LAND,
        4,
        new ClimateProfile(0.82f, 0.30f),
        new MacroHeightProfile(70.0, 100.0, 0.06f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_DESERT, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_WARM_STEPPE, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    TROPICAL_HUMID(
        MacroDomain.LAND,
        5,
        new ClimateProfile(0.95f, 0.92f),
        new MacroHeightProfile(72.0, 90.0, 0.04f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_TROPICAL_RAIN, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_SAVANNA, 1)
        )
    ),

    COOL_FORESTED(
        MacroDomain.LAND,
        6,
        new ClimateProfile(0.32f, 0.70f),
        new MacroHeightProfile(82.0, 116.0, 0.18f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_COOL_FOREST, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_TEMPERATE_FOREST, 1)
        )
    ),

    SUBPOLAR(
        MacroDomain.LAND,
        7,
        new ClimateProfile(0.18f, 0.45f),
        new MacroHeightProfile(92.0, 132.0, 0.22f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_SUBPOLAR_TUNDRA, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_POLAR_DESERT, 1),
            new MacroBiomeVariant(TalosBiomes.TALOS_ALPINE, 1)
        )
    ),

    MOUNTAINOUS(
        MacroDomain.LAND,
        8,
        new ClimateProfile(0.25f, 0.35f),
        new MacroHeightProfile(110.0, 180.0, 0.48f),
        variants(
            new MacroBiomeVariant(TalosBiomes.TALOS_MOUNTAINS, 3),
            new MacroBiomeVariant(TalosBiomes.TALOS_PLATEAU, 2),
            new MacroBiomeVariant(TalosBiomes.TALOS_ALPINE, 1)
        )
    );

    public final MacroDomain domain;
    public final int id;
    public final ClimateProfile climate;
    public final MacroHeightProfile height;
    public final List<MacroBiomeVariant> variants;

    MacroBiome(MacroDomain domain,
               int id,
               ClimateProfile climate,
               MacroHeightProfile height,
               List<MacroBiomeVariant> variants) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.id = id;
        this.climate = Objects.requireNonNull(climate, "climate");
        this.height = Objects.requireNonNull(height, "height");
        this.variants = variants != null
            ? variants
            : java.util.Collections.emptyList();
    }

    public int getId() {
        return id;
    }

    public MacroDomain domain() {
        return domain;
    }

    public boolean matchesDomain(MacroDomain domain) {
        return this.domain == domain;
    }

    public boolean isOceanic() {
        return this.domain == MacroDomain.OCEAN;
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
        return this.domain == MacroDomain.LAND;
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

    public static final class ClimateProfile {
        public final float temperature;
        public final float humidity;

        public ClimateProfile(float temperature, float humidity) {
            this.temperature = temperature;
            this.humidity = humidity;
        }
    }

    public static final class MacroHeightProfile {
        public final double absoluteMin;
        public final double absoluteMax;
        public final float baseHeightOffset;

        public MacroHeightProfile(double absoluteMin,
                                  double absoluteMax,
                                  float baseHeightOffset) {
            this.absoluteMin = absoluteMin;
            this.absoluteMax = absoluteMax;
            this.baseHeightOffset = baseHeightOffset;
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
