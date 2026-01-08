package com.EyeOfHarmonyBuffer.space.talos.chunk.biome;

import com.EyeOfHarmonyBuffer.space.talos.biome.MacroBiome;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ChunkProviderTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.World;

import java.util.*;

public final class Talos2BiomeResolver {

    private final IMacroCellProvider macroCellBuilder;
    private final Random random = new Random();

    public Talos2BiomeResolver(World world, IMacroCellProvider macroCellBuilder) {
        this.macroCellBuilder = macroCellBuilder;
        this.random.setSeed(world.getSeed());
    }

    public void resolveBiomes(int chunkX, int chunkZ, BiomeGenBase[] biomesOut) {
        ChunkProviderTalos2.ChunkShoreCache cache = macroCellBuilder.build(chunkX, chunkZ);

        int idx = 0;
        for (int lx = 0; lx < ChunkProviderTalos2.ChunkShoreCache.GRID_SIZE; lx++) {
            for (int lz = 0; lz < ChunkProviderTalos2.ChunkShoreCache.GRID_SIZE; lz++) {
                ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = cache.macroContext[lx][lz];
                MacroBiome primary = resolveBiome(cell.primary);
                MacroBiome secondary = resolveBiome(cell.secondary);

                BiomeGenBase picked = selectVariant(cache, cell, primary, secondary, lx, lz);
                biomesOut[idx++] = picked;
            }
        }
    }

    private BiomeGenBase selectVariant(ChunkProviderTalos2.ChunkShoreCache cache,
                                       ChunkProviderTalos2.ChunkShoreCache.MacroCell cell,
                                       MacroBiome primary,
                                       MacroBiome secondary,
                                       int lx,
                                       int lz) {

        double selector = cell.blendPrimary;
        if (secondary == primary) {
            selector = 0.0;
        } else {
            selector = Math.min(1.0, Math.max(0.0, selector));
        }

        MacroBiome target = selector < 0.5 ? primary : secondary;
        return pickBiomeVariant(target, lx, lz, cache, cell);
    }

    private BiomeGenBase pickBiomeVariant(MacroBiome macro,
                                          int lx,
                                          int lz,
                                          ChunkProviderTalos2.ChunkShoreCache cache,
                                          ChunkProviderTalos2.ChunkShoreCache.MacroCell cell) {

        if (macro.variants.isEmpty()) {
            return net.minecraft.world.biome.BiomeGenBase.plains;
        }

        int weightSum = 0;
        for (MacroBiome.MacroBiomeVariant variant : macro.variants) {
            weightSum += Math.max(1, variant.weight);
        }

        long seed = ((long) cell.plateId << 32) ^ ((long) cell.tier << 24)
            ^ ((long) lx << 12) ^ (long) lz;
        random.setSeed(seed);

        int roll = random.nextInt(weightSum);
        for (MacroBiome.MacroBiomeVariant variant : macro.variants) {
            roll -= Math.max(1, variant.weight);
            if (roll < 0) {
                return variant.biome;
            }
        }
        return macro.variants.get(0).biome;
    }

    private static MacroBiome resolveBiome(MacroTag tag) {
        return tag != null ? tag.toMacroBiome() : MacroBiome.PLAINS_TEMPERATE;
    }
}
