package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.WorldgenAPI;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private static final BiomeGenBase DEFAULT_BIOME = TalosBiomes.TALOS_PLAINS;

    private final World world;
    private final int worldSeedInt;

    // 缓存：key = (x, z) 打包成 long，value = Biome
    private final Long2ObjectOpenHashMap<BiomeGenBase> biomeCache =
        new Long2ObjectOpenHashMap<>();

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = world;
        this.worldSeedInt = TalosLandMask.getWorldSeedInt(world);
    }

    private static long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 为某个世界坐标 (x,z) 选择群系。
     * 注意：这里不再做 BIOME_SHIFT 降采样，保证与方块级海陆一致。
     */
    private BiomeGenBase pickBiomeFor(int x, int z) {
        long key = packXZ(x, z);

        BiomeGenBase cached = biomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        WorldgenAPI.SampleResult result =
            TalosLandMask.sample(x, z, worldSeedInt);

        BiomeGenBase biome;
        if (result == null) {
            biome = DEFAULT_BIOME;
        } else {
            biome = result.isLand ? TalosBiomes.TALOS_PLAINS : TalosBiomes.TALOS_OCEAN;
        }

        biomeCache.put(key, biome);
        return biome;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        // 参数本身就是世界坐标
        return pickBiomeFor(x, z);
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] array, int x, int z, int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                // Galacticraft 这里传的是 4x 缩放坐标，继续保持：
                int worldX = (x + dx) << 2;
                int worldZ = (z + dz) << 2;
                array[i++] = pickBiomeFor(worldX, worldZ);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] array, int x, int z, int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
                // 这里本身就是逐方块坐标
                array[i++] = pickBiomeFor(x + dx, z + dz);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase getBiome() {
        return DEFAULT_BIOME;
    }
}
