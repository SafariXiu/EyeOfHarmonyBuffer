package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectionResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.MacroSite;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import net.minecraft.world.biome.BiomeGenBase;

public final class TalosBiomeResolver {

    private static final BiomeGenBase DEFAULT_BIOME = TalosBiomes.TALOS_PLAINS;

    private TalosBiomeResolver() {}

    public static BiomeGenBase resolve(MacroSelectionResult macro) {
        if (macro == null) {
            return DEFAULT_BIOME;
        }

        MacroBiome.MacroBiomeVariant variant = macro.variant();
        if (variant != null && variant.biome != null) {
            return variant.biome;
        }

        BiomeGenBase primary = pickBaseBiomeForTag(macro.macroTag(), macro);
        if (primary == null) {
            primary = DEFAULT_BIOME;
        }

        MacroSite secondarySite = macro.secondarySite();
        if (secondarySite == null) {
            return primary;
        }

        double edge = macro.edgeFactor();
        if (edge >= 0.55d) {
            return primary;
        }
        if (edge <= 0.20d) {
            BiomeGenBase secondary = pickBaseBiomeForTag(secondarySite.macroTag(), macro);
            return secondary != null ? secondary : primary;
        }

        BiomeGenBase secondary = pickBaseBiomeForTag(secondarySite.macroTag(), macro);
        if (secondary == null || secondary == primary) {
            return primary;
        }

        return selectTransitionBiome(
            primary,
            secondary,
            edge,
            macro.macroSiteId(),
            macro.microSiteId()
        );
    }

    private static BiomeGenBase pickBaseBiomeForTag(MacroTag tag, MacroSelectionResult macro) {
        if (tag == null) {
            return DEFAULT_BIOME;
        }

        if (tag.isOceanic()) {
            return macro.coastDistance() <= macro.shelfWidth()
                ? TalosBiomes.TALOS_SHELF
                : TalosBiomes.TALOS_OCEAN;
        }

        if (tag.isCoastal() || macro.coastDistance() <= macro.coastWidth()) {
            return TalosBiomes.TALOS_BEACH;
        }

        if (tag.isFrozen()) {
            return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
        }

        return switch (tag) {
            case DESERT -> TalosBiomes.TALOS_DESERT;
            case SAVANNA -> TalosBiomes.TALOS_SAVANNA;
            case STEPPE -> TalosBiomes.TALOS_WARM_STEPPE;
            case COOL_FOREST -> TalosBiomes.TALOS_COOL_FOREST;
            case TROPICAL -> TalosBiomes.TALOS_TROPICAL_RAIN;
            case TUNDRA -> TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
            case MOUNTAIN, ALPINE -> TalosBiomes.TALOS_MOUNTAINS;
            case BASIN -> TalosBiomes.TALOS_BASIN;
            default -> DEFAULT_BIOME;
        };
    }

    private static BiomeGenBase selectTransitionBiome(BiomeGenBase primary,
                                                      BiomeGenBase secondary,
                                                      double edgeFactor,
                                                      long macroSiteId,
                                                      long microSiteId) {
        long mixed = macroSiteId
            ^ (microSiteId * 0x9E3779B97F4A7C15L)
            ^ Double.doubleToLongBits(edgeFactor);

        return (mixed & 1L) == 0L ? primary : secondary;
    }
}
