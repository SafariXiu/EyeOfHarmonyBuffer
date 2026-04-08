package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private static final BiomeGenBase DEFAULT_BIOME = TalosBiomes.TALOS_PLAINS;

    private final World world;
    private final int worldSeedInt;

    private final Long2ObjectOpenHashMap<BiomeGenBase> biomeCache =
        new Long2ObjectOpenHashMap<>();

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = world;
        this.worldSeedInt = TalosMacroClimate.getWorldSeedInt(world);
    }

    private static long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 为某个世界坐标 (x,z) 选择群系。
     *
     * 现在的逻辑：
     *   - 使用 TalosMacroClimate（内部走 MacroRegionLayer + MacroPackageLayer）：
     *       * 先得到平滑后的宏群系 ID；
     *       * 再通过 MacroPackageDefs.pickDeterministicBiome 选具体 Biome；
     *   - 结果在 biomeCache 中缓存。
     *
     * 注意：
     *   - 坐标 x,z 均为世界方块坐标（与海陆 / 板块系统一致）；
     *   - 不再有 BIOME_SHIFT 降采样。
     */
    private BiomeGenBase pickBiomeFor(int x, int z) {
        long key = packXZ(x, z);

        BiomeGenBase cached = biomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        BiomeGenBase biome;
        try {
            biome = TalosMacroClimate.getBiome(x, z, worldSeedInt);
            if (biome == null) {
                biome = DEFAULT_BIOME;
            }
        } catch (Throwable t) {
            biome = DEFAULT_BIOME;
        }

        biomeCache.put(key, biome);
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
                int worldX = (x + dx) << 2;
                int worldZ = (z + dz) << 2;
                array[i++] = pickBiomeFor(worldX, worldZ);
            }
        }
        return array;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] array,
                                                 int x, int z,
                                                 int width, int depth) {
        if (array == null || array.length < width * depth) {
            array = new BiomeGenBase[width * depth];
        }

        int i = 0;
        for (int dz = 0; dz < depth; dz++) {
            for (int dx = 0; dx < width; dx++) {
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
