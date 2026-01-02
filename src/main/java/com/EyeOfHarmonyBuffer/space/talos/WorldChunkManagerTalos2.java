package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.Talos2BiomeResolver.Talos2BiomeResolver;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Map;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private final MacroBiomeField macroField;
    private final CoastlineAtlas coastlineAtlas;
    private final Talos2BiomeResolver biomeResolver;
    private final TalosMacroCellBuilder macroCellBuilder;
    private final Map<Long, ChunkProviderTalos2.ChunkShoreCache> shoreCache = new Long2ObjectOpenHashMap<>();
    private final World world;

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = world;

        Talos2Hooks.HookData hook = Talos2Hooks.resolveOrCreate(world);

        this.macroField = hook.macroField;
        this.coastlineAtlas = hook.coastlineAtlas;
        this.macroCellBuilder = hook.macroCellBuilder;
        this.biomeResolver = new Talos2BiomeResolver(world, this.macroField);

        System.out.println("[Talos2] WCM builder instance=" +
            System.identityHashCode(this.macroCellBuilder));
    }

    @Override
    public BiomeGenBase getBiome() {
        return TalosBiomes.TALOS_PLAINS;
    }

    private BiomeGenBase pickBiomeFor(int x, int z) {
        ChunkProviderTalos2.ChunkShoreCache.MacroCell cell = getCell(x, z);
        if (cell == null) {
            return TalosBiomes.TALOS_PLAINS;
        }

        BiomeGenBase biome;
        if (!cell.isLand) {
            int dist = ushort(cell.distToCoast);
            int shelf = ushort(cell.shelfWidth);
            biome = (dist <= shelf) ? TalosBiomes.TALOS_SHELF : TalosBiomes.TALOS_OCEAN;
        } else {
            int dist = ushort(cell.distToCoast);
            int beach = ushort(cell.beachWidth);
            if (dist <= beach) {
                biome = TalosBiomes.TALOS_BEACH;
            } else {
                biome = this.biomeResolver.resolve(x, z, cell);
                if (biome == null) {
                    biome = TalosBiomes.TALOS_PLAINS;
                }
            }
        }

        BiomeGenBase generated = TalosBiomeDebugHooks.getGeneratedBiome(x, z);
        if (generated != null && generated != biome) {
            System.out.println("[Talos2] BIOME MISMATCH @ (" + x + "," + z + ")" +
                " WCM=" + biome.biomeName + ", CP=" + generated.biomeName +
                " macro=" + cell.primary + "/" + cell.secondary +
                " tier=" + cell.tier +
                " plate=" + cell.plateId +
                " patch=" + cell.patchVariant +
                " blend=" + String.format("%.2f", cell.blendPrimary));
        }

        return biome;
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

        ChunkProviderTalos2.ChunkShoreCache cache = shoreCache.computeIfAbsent(
            key, k -> macroCellBuilder.build(chunkX, chunkZ)
        );

        int lx = gx - chunkX * 16;
        int lz = gz - chunkZ * 16;
        return cache.macroContext[lx][lz];
    }
}
