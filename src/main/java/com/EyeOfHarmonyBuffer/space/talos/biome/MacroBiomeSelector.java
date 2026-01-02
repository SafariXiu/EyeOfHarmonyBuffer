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
        return pick(gx, gz, primary, secondary, blendPrimary,
            (byte) 0,
            false,
            0.0D
        );
    }

    public BiomeGenBase pick(int gx,
                             int gz,
                             MacroBiome primary,
                             MacroBiome secondary,
                             double blendPrimary,
                             byte patchVariant,
                             boolean patchSingleBiome,
                             double patchEdgeBlend) {

        if (primary == null && secondary == null) {
            return TalosBiomes.TALOS_PLAINS;
        }
        if (primary == null) primary = secondary;
        if (secondary == null) secondary = primary;

        List<MacroBiome.MacroBiomeVariant> primaryVariants = primary.variants;
        List<MacroBiome.MacroBiomeVariant> secondaryVariants = secondary.variants;

        boolean primaryEmpty = primaryVariants == null || primaryVariants.isEmpty();
        boolean secondaryEmpty = secondaryVariants == null || secondaryVariants.isEmpty();

        if (primaryEmpty && secondaryEmpty) {
            return TalosBiomes.TALOS_PLAINS;
        }

        double selectorFromPatch = (patchVariant & 0xFF) / 255.0D;
        double noise = sampleNoise01(gx, gz);
        double edgeStrength = clamp01(patchEdgeBlend);
        double selector01 = lerp(noise, selectorFromPatch, edgeStrength);

        double effectiveBlend = patchSingleBiome ? 1.0D : clamp01(blendPrimary);

        if (primaryEmpty)  return pickFromListWithSelector(secondaryVariants, selector01);
        if (secondaryEmpty) return pickFromListWithSelector(primaryVariants, selector01);

        if (effectiveBlend >= 0.999D) return pickFromListWithSelector(primaryVariants, selector01);
        if (effectiveBlend <= 0.001D) return pickFromListWithSelector(secondaryVariants, selector01);

        return pickFromBlendedListsWithSelector(primaryVariants, secondaryVariants, effectiveBlend, selector01);
    }

    private BiomeGenBase pickFromBlendedLists(int gx,
                                              int gz,
                                              List<MacroBiome.MacroBiomeVariant> primary,
                                              List<MacroBiome.MacroBiomeVariant> secondary,
                                              double blendPrimary) {
        double noise = sampleNoise01(gx, gz);
        return pickFromBlendedListsWithSelector(primary, secondary, blendPrimary, noise);
    }

    private BiomeGenBase pickFromListWithSelector(List<MacroBiome.MacroBiomeVariant> variants,
                                                  double selector01) {
        if (variants == null || variants.isEmpty()) {
            return TalosBiomes.TALOS_PLAINS;
        }

        int totalWeight = 0;
        for (MacroBiome.MacroBiomeVariant v : variants) {
            totalWeight += v.weight;
        }
        if (totalWeight <= 0) {
            return variants.get(0).biome;
        }

        double target = selector01 * totalWeight;
        int cumulative = 0;
        for (MacroBiome.MacroBiomeVariant v : variants) {
            cumulative += v.weight;
            if (target <= cumulative) {
                return v.biome;
            }
        }
        return variants.get(variants.size() - 1).biome;
    }

    private BiomeGenBase pickFromBlendedListsWithSelector(List<MacroBiome.MacroBiomeVariant> primary,
                                                          List<MacroBiome.MacroBiomeVariant> secondary,
                                                          double blendPrimary,
                                                          double selector01) {
        double weightPrimary = clamp01(blendPrimary);
        double weightSecondary = 1.0D - weightPrimary;

        double totalWeight = 0.0D;
        for (MacroBiome.MacroBiomeVariant v : primary)   totalWeight += v.weight * weightPrimary;
        for (MacroBiome.MacroBiomeVariant v : secondary) totalWeight += v.weight * weightSecondary;

        if (totalWeight <= 0.0D) {
            return TalosBiomes.TALOS_PLAINS;
        }

        double target = selector01 * totalWeight;
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

    private double sampleNoise01(int gx, int gz) {
        double noise = selectorNoise.noise(gx * 0.0025D, gz * 0.0025D);
        return (noise + 1.0D) * 0.5D;
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private static double clamp01(double v) {
        if (v < 0.0D) return 0.0D;
        if (v > 1.0D) return 1.0D;
        return v;
    }
}
