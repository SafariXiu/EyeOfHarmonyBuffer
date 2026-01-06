package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import com.EyeOfHarmonyBuffer.space.talos.TalosBiomeDebugHooks;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Map;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private final IMacroCellProvider macroCellBuilder;
    private final Map<Long, ChunkProviderTalos2.ChunkShoreCache> shoreCache = new Long2ObjectOpenHashMap<>();
    private final World world;

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = world;

        Talos2Hooks.HookData hook = Talos2Hooks.resolveOrCreate(world);
        this.macroCellBuilder = hook.macroCellBuilder();

        System.out.println("[Talos2] WCM builder instance=" +
            System.identityHashCode(this.macroCellBuilder));
    }

    @Override
    public BiomeGenBase getBiome() {
        return TalosBiomes.TALOS_PLAINS;
    }

    private BiomeGenBase pickBiomeFor(int x, int z) {
        BiomeGenBase generated = TalosBiomeDebugHooks.getGeneratedBiome(x, z);
        if (generated != null) {
            return generated;
        }

        ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = getCell(x, z);
        if (cell == null) {
            return TalosBiomes.TALOS_PLAINS;
        }

        boolean isLand = cell.isLand;
        int dist = ushort(cell.distToCoast);
        int beach = ushort(cell.beachWidth);
        int shelf = ushort(cell.shelfWidth);

        return pickBaseBiome(isLand, dist, beach, shelf);
    }

    private BiomeGenBase pickBaseBiome(boolean isLand, int distToCoast, int beachWidth, int shelfWidth) {
        if (!isLand) {
            return distToCoast <= shelfWidth ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
        }
        if (distToCoast <= beachWidth) {
            return TalosBiomes.TALOS_BEACH;
        }
        return TalosBiomes.TALOS_PLAINS;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return pickBiomeFor(x, z);
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                int gx = x + dx;
                int gz = z + dz;
                array[i++] = pickBiomeFor(gx, gz);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        return getBiomesForGeneration(array, x, z, width, depth);
    }

    private static int ushort(short v) {
        return v & 0xFFFF;
    }

    private ChunkProviderTalos2.ChunkShoreCache.MacroCell getCell(int gx, int gz) {
        int chunkX = Math.floorDiv(gx, 16);
        int chunkZ = Math.floorDiv(gz, 16);
        long key = (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);

        ChunkProviderTalos2.ChunkShoreCache cache =
            shoreCache.computeIfAbsent(key, k -> macroCellBuilder.build(chunkX, chunkZ));

        int lx = gx - chunkX * 16;
        int lz = gz - chunkZ * 16;
        return cache.macroContext[lx][lz];
    }
}
