package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * Talos 2 代世界群系管理：
 *   - 所有群系查询统一走 TalosMacroClimate.getBiome(...)：
 *       * 内部先用 MacroPackageLayer + SubPatch 划分宏观块状 Biome；
 *       * 再用 BiomeRegionLayer 做一次 tile 级平滑 / 小块吞并；
 *   - 这样可以同时避免 per-block hash 马赛克 & 海岸线小碎块。
 */

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private static final BiomeGenBase DEFAULT_BIOME = TalosBiomes.TALOS_PLAINS;

    private final World world;
    private final int worldSeedInt;

    /** 群系查询缓存（有界：超过上限整体清空，避免长会话下无限增长）。 */
    private static final int CACHE_LIMIT = 8192;
    private final Long2ObjectOpenHashMap<BiomeGenBase> biomeCache =
        new Long2ObjectOpenHashMap<>();

    public WorldChunkManagerTalos2(World world) {
        super();
        this.world = world;
        this.worldSeedInt = TalosSeed.of(world);   // 世界种子派生唯一入口
    }

    private static long packXZ(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    /**
     * 为某个世界坐标 (x,z) 选择群系。
     *
     * V2 轨：{@link V2BiomePicker#biomeAt} → {@link V2BiomeField}（1km LUT + 平滑），
     * 与地形生成同源；旧轨：TalosMacroClimate 宏群系。结果按坐标缓存（有上限）。
     */
    private BiomeGenBase pickBiomeFor(int x, int z) {
        long key = packXZ(x, z);

        BiomeGenBase cached = biomeCache.get(key);
        if (cached != null) {
            return cached;
        }

        // 群系 = L1/L1b 场直接映射（与 ChunkProviderTalos2 同源）。
        // 这里曾经按 terrainV2Enabled 二选一，另一支走 TalosMacroClimate（旧宏气候）——
        // 现在是**同一条链**，不可能再出现"世界群系管理器与地形用两套海陆/气候"。
        BiomeGenBase biome = V2BiomePicker.biomeAt(x, z, worldSeedInt);

        if (biomeCache.size() >= CACHE_LIMIT) {
            biomeCache.clear();
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
