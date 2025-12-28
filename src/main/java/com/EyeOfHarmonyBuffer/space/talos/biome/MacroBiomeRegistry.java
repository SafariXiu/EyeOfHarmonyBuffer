package com.EyeOfHarmonyBuffer.space.talos.biome;

import net.minecraft.world.biome.BiomeGenBase;

import java.util.Map;
import java.util.HashMap;

public final class MacroBiomeRegistry {

    private static final Map<Integer, MacroBiome> REGISTRY = new HashMap<Integer, MacroBiome>();

    private MacroBiomeRegistry() {}

    public static void register(BiomeGenBase biome, MacroBiome macro) {
        if (biome == null || macro == null) {
            throw new IllegalArgumentException("Biome or MacroBiome cannot be null");
        }
        REGISTRY.put(biome.biomeID, macro);
    }

    public static MacroBiome getMacroBiome(BiomeGenBase biome) {
        return biome == null ? null : REGISTRY.get(biome.biomeID);
    }

    public static MacroBiome getMacroBiome(int biomeId) {
        return REGISTRY.get(biomeId);
    }
}
