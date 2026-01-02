package com.EyeOfHarmonyBuffer.space.talos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;

import javax.annotation.Nullable;

public final class TalosBiomeDebugHooks {

    private static final Long2ObjectMap<int[]> GENERATED = new Long2ObjectOpenHashMap<>();

    public static void recordGeneratedBiome(int chunkX, int chunkZ, int localX, int localZ, BiomeGenBase biome) {
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        int[] arr = GENERATED.computeIfAbsent(key, k -> new int[16 * 16]);
        arr[(localZ << 4) | localX] = biome.biomeID;
    }

    @Nullable
    public static BiomeGenBase getGeneratedBiome(int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
        int[] arr = GENERATED.get(key);
        if (arr == null) return null;
        int localX = x & 15;
        int localZ = z & 15;
        int id = arr[(localZ << 4) | localX];
        return id == 0 ? null : BiomeGenBase.getBiome(id);
    }
}
