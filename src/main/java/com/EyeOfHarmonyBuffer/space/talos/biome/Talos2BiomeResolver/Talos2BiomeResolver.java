package com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver;

import com.EyeOfHarmonyBuffer.space.talos.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiomeField;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.World;

import java.util.*;

public final class Talos2BiomeResolver {

    private final long worldSeed;
    private final Map<MacroBiome, TalosMacroPicker> pickerRegistry;

    public Talos2BiomeResolver(World world, MacroBiomeField macroField) {
        this.worldSeed = world.getSeed();
        this.pickerRegistry = buildPickerRegistry();
        System.out.println("[Talos2BiomeResolver] macro-only resolver ready");
    }

    @Deprecated
    public BiomeGenBase resolve(int x, int z) {
        throw new UnsupportedOperationException("Use resolve(x, z, MacroCell)");
    }

    public BiomeGenBase resolve(int x, int z, ChunkProviderTalos2.ChunkShoreCache.MacroCell cell) {
        if (cell == null) return TalosBiomes.TALOS_PLAINS;

        MacroBiome macro = cell.primary != null ? cell.primary : MacroBiome.PLAINS_TEMPERATE;
        TalosMacroPicker picker = pickerRegistry.get(macro);
        if (picker == null) return TalosBiomes.TALOS_PLAINS;

        BiomeGenBase biome = picker.pick(cell, worldSeed, x, z);
        BiomeGenBase result = (biome != null) ? biome : TalosBiomes.TALOS_PLAINS;

        /*if ((x & 0xF) == 0 && (z & 0xF) == 0) {
            System.out.println("[Talos2BiomeResolver] x=" + x + ", z=" + z +
                " macro=" + macro +
                " tier=" + cell.tier +
                " plate=" + cell.plateId +
                " patchVariant=" + (cell.patchVariant & 0xFF) +
                " patchEdge=" + String.format("%.2f", clamp01(cell.patchEdgeBlend)) +
                " single=" + cell.patchSingleBiome +
                " -> biome=" + result.biomeName);
        }*/

        return result;
    }

    private Map<MacroBiome, TalosMacroPicker> buildPickerRegistry() {
        Map<MacroBiome, TalosMacroPicker> map = new EnumMap<>(MacroBiome.class);

        for (MacroBiome macro : MacroBiome.values()) {
            map.put(macro, variantPicker(macro));
        }

        return map;
    }

    private TalosMacroPicker variantPicker(MacroBiome macro) {
        return new TalosMacroPicker() {
            @Override public String name() { return "macro-" + macro.name().toLowerCase(Locale.ROOT); }

            @Override
            public BiomeGenBase pick(ChunkProviderTalos2.ChunkShoreCache.MacroCell cell, long seed, int x, int z) {
                if (macro.variants == null || macro.variants.isEmpty()) {
                    return TalosBiomes.TALOS_PLAINS;
                }

                List<MacroBiome.MacroBiomeVariant> candidates = new ArrayList<>(macro.variants);

                switch (macro) {
                    case OCEANIC -> {
                        if (cell.distToCoast <= cell.shelfWidth + 4) {
                            return TalosBiomes.TALOS_SHELF;
                        }
                        return TalosBiomes.TALOS_OCEAN;
                    }
                    case COASTAL -> {
                        if (cell.distToCoast <= cell.beachWidth + 2) {
                            return TalosBiomes.TALOS_BEACH;
                        }
                        candidates.removeIf(v -> v.biome == TalosBiomes.TALOS_BEACH);
                    }
                    case LOWLAND_WET -> {
                        if (cell.macroBaseHeight < 68) {
                            return TalosBiomes.TALOS_BASIN;
                        }
                    }
                    case PLAINS_TEMPERATE -> {
                        if (cell.tier >= 2 || cell.macroBaseHeight >= 95) {
                            return TalosBiomes.TALOS_TEMPERATE_STEPPE;
                        }
                    }
                    case WARM_DRY -> {
                        if (cell.distToCoast < cell.beachWidth + 6) {
                            return TalosBiomes.TALOS_SAVANNA;
                        }
                    }
                    case TROPICAL_HUMID -> {
                        if (cell.tier >= 2 || cell.macroBaseHeight >= 96) {
                            candidates.add(new MacroBiome.MacroBiomeVariant(TalosBiomes.TALOS_PLATEAU, 2));
                        }
                    }
                    case COOL_FORESTED -> {
                        if (cell.macroBaseHeight >= 104 || cell.tier >= 3) {
                            return TalosBiomes.TALOS_ALPINE;
                        }
                    }
                    case SUBPOLAR -> {
                        if (cell.macroBaseHeight >= 110 || cell.tier >= 3) {
                            return TalosBiomes.TALOS_ALPINE;
                        }
                        if (cell.macroBaseHeight <= 78) {
                            return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
                        }
                    }
                    case MOUNTAINOUS -> {
                        if (cell.macroBaseHeight >= 120 || cell.tier >= 3) {
                            return TalosBiomes.TALOS_ALPINE;
                        }
                        if (cell.macroBaseHeight <= 98) {
                            return TalosBiomes.TALOS_COOL_FOREST;
                        }
                    }
                    default -> {
                    }
                }

                if (candidates.isEmpty()) {
                    return TalosBiomes.TALOS_PLAINS;
                }

                return pickVariantFromCandidates(candidates, cell, seed, x, z);
            }
        };
    }

    private BiomeGenBase pickVariantFromCandidates(List<MacroBiome.MacroBiomeVariant> candidates,
                                                   ChunkProviderTalos2.ChunkShoreCache.MacroCell cell,
                                                   long seed, int x, int z) {
        if (candidates.isEmpty()) {
            return TalosBiomes.TALOS_PLAINS;
        }

        int totalWeight = 0;
        for (MacroBiome.MacroBiomeVariant variant : candidates) {
            totalWeight += Math.max(1, variant.weight);
        }
        if (totalWeight <= 0) {
            return candidates.get(0).biome;
        }

        double selectorFromPatch = (cell.patchVariant & 0xFF) / 255.0D;
        double selectorFromHash = hash01(seed, x, z, cell.plateId, cell.tier);
        double edgeBlend = clamp01(cell.patchEdgeBlend);

        boolean lockedToPatch = cell.patchSingleBiome || edgeBlend <= 0.05D;

        double selector = lockedToPatch
            ? selectorFromPatch
            : lerp(selectorFromHash, selectorFromPatch, edgeBlend);

        double target = selector * totalWeight;
        int accum = 0;

        for (MacroBiome.MacroBiomeVariant variant : candidates) {
            accum += Math.max(1, variant.weight);
            if (target <= accum) {
                return variant.biome;
            }
        }
        return candidates.get(candidates.size() - 1).biome;
    }

    private static double hash01(long seed, int x, int z, byte plateId, byte tier) {
        long h = mix64(seed ^ ((long) x << 32) ^ z ^ ((long) plateId << 24) ^ tier);
        return ((h >>> 11) & 0x1FFFFFFFFFFFFFL) * 0x1.0p-53; // 53-bit -> [0,1)
    }

    private static double clamp01(double v) {
        return v < 0.0D ? 0.0D : (v > 1.0D ? 1.0D : v);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * clamp01(t);
    }

    private interface TalosMacroPicker {
        String name();
        BiomeGenBase pick(ChunkProviderTalos2.ChunkShoreCache.MacroCell cell, long seed, int x, int z);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
