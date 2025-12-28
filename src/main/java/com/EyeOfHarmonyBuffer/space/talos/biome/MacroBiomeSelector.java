package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.SimplexNoiseOctave;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.List;

public final class MacroBiomeSelector {

    private final SimplexNoiseOctave selectorNoise;

    public MacroBiomeSelector(long seed) {
        this.selectorNoise = new SimplexNoiseOctave(seed ^ 0xA17F4E3DL, 2);
    }

    public BiomeGenBase pick(int gx, int gz, MacroBiome macro) {
        if (macro == null) {
            return TalosBiomes.TALOS_PLAINS;
        }
        return pick(gx, gz, macro, macro, 1.0D);
    }

    public BiomeGenBase pick(int gx,
                             int gz,
                             MacroBiome primary,
                             MacroBiome secondary,
                             double blendPrimary) {

        if (primary == null && secondary == null) {
            return TalosBiomes.TALOS_PLAINS;
        }
        if (primary == null) primary = secondary;
        if (secondary == null) secondary = primary;

        List<MacroBiome.MacroBiomeVariant> primaryVariants = primary.variants;
        List<MacroBiome.MacroBiomeVariant> secondaryVariants = secondary.variants;

        boolean primaryEmpty = primaryVariants == null || primaryVariants.isEmpty();
        boolean secondaryEmpty = secondaryVariants == null || secondaryVariants.isEmpty();

        double t = clamp01(blendPrimary);

        if (primaryEmpty && secondaryEmpty) {
            return TalosBiomes.TALOS_PLAINS;
        } else if (primaryEmpty) {
            return pickFromList(gx, gz, secondaryVariants);
        } else if (secondaryEmpty) {
            return pickFromList(gx, gz, primaryVariants);
        }

        if (t >= 0.75D) {
            return pickFromList(gx, gz, primaryVariants);
        }
        if (t <= 0.25D) {
            return pickFromList(gx, gz, secondaryVariants);
        }

        return pickFromBlendedLists(gx, gz, primaryVariants, secondaryVariants, t);
    }

    private BiomeGenBase pickFromList(int gx, int gz, List<MacroBiome.MacroBiomeVariant> variants) {
        if (variants == null || variants.isEmpty()) {
            return TalosBiomes.TALOS_PLAINS;
        }

        double noise = selectorNoise.noise(gx * 0.0025D, gz * 0.0025D);
        double value = (noise + 1.0D) * 0.5D;

        int totalWeight = 0;
        for (MacroBiome.MacroBiomeVariant v : variants) {
            totalWeight += v.weight;
        }

        double target = value * totalWeight;
        int cumulative = 0;

        for (MacroBiome.MacroBiomeVariant v : variants) {
            cumulative += v.weight;
            if (target <= cumulative) {
                return v.biome;
            }
        }

        return variants.get(variants.size() - 1).biome;
    }

    private BiomeGenBase pickFromBlendedLists(int gx,
                                              int gz,
                                              List<MacroBiome.MacroBiomeVariant> primary,
                                              List<MacroBiome.MacroBiomeVariant> secondary,
                                              double blendPrimary) {

        double weightPrimary = clamp01(blendPrimary);
        double weightSecondary = 1.0D - weightPrimary;

        double noise = selectorNoise.noise(gx * 0.0025D, gz * 0.0025D);
        double value = (noise + 1.0D) * 0.5D; // 0..1

        double totalWeight = 0.0D;
        for (MacroBiome.MacroBiomeVariant v : primary)   totalWeight += v.weight * weightPrimary;
        for (MacroBiome.MacroBiomeVariant v : secondary) totalWeight += v.weight * weightSecondary;

        if (totalWeight <= 0.0D) {
            return TalosBiomes.TALOS_PLAINS;
        }

        double target = value * totalWeight;
        double cumulative = 0.0D;

        for (MacroBiome.MacroBiomeVariant v : primary) {
            cumulative += v.weight * weightPrimary;
            if (target <= cumulative) {
                return v.biome;
            }
        }
        for (MacroBiome.MacroBiomeVariant v : secondary) {
            cumulative += v.weight * weightSecondary;
            if (target <= cumulative) {
                return v.biome;
            }
        }

        return primary.get(primary.size() - 1).biome;
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }
}
