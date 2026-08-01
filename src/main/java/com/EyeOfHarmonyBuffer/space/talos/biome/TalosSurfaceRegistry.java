package com.EyeOfHarmonyBuffer.space.talos.biome;

import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import net.minecraft.init.Blocks;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.HashMap;
import java.util.Map;

/**
 * 群系 → 地表规格注册表。
 *
 * 地形生成器按每列群系查表填充方块。未注册的群系（当前为海洋 / 陆架，
 * 后续扩展）使用 DEFAULT（草 / 泥土 / 石头）。
 */
public final class TalosSurfaceRegistry {

    private TalosSurfaceRegistry() {}

    private static final Map<BiomeGenBase, TalosSurfaceProfile> PROFILES =
        new HashMap<BiomeGenBase, TalosSurfaceProfile>();

    private static final BlockMetaPair GRASS = new BlockMetaPair(Blocks.grass, (byte) 0);
    private static final BlockMetaPair DIRT  = new BlockMetaPair(Blocks.dirt, (byte) 0);
    private static final BlockMetaPair STONE = new BlockMetaPair(Blocks.stone, (byte) 0);

    private static final TalosSurfaceProfile DEFAULT = new TalosSurfaceProfile(
        GRASS, 1,
        DIRT, 3,
        STONE,
        null, 0, 0.0
    );

    /** 在 TalosBiomes.init() 之后调用，为所有陆生群系注册地表规格。 */
    public static void init() {
        register(TalosBiomes.TALOS_PLAINS, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 3, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_TEMPERATE_STEPPE, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 3, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_TEMPERATE_FOREST, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 4, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_COOL_FOREST, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 4, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_SAVANNA, new TalosSurfaceProfile(
            new BlockMetaPair(Blocks.grass, (byte) 1), 1, DIRT, 3, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_WARM_STEPPE, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 3, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_TROPICAL_RAIN, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 4, STONE, null, 0, 0.0));
        register(TalosBiomes.TALOS_BASIN, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 4, STONE, null, 0, 0.0));

        // 沙漠：表层沙 → 砂岩 → 石头
        register(TalosBiomes.TALOS_DESERT, new TalosSurfaceProfile(
            new BlockMetaPair(Blocks.sand, (byte) 0), 3,
            new BlockMetaPair(Blocks.sandstone, (byte) 0), 4,
            STONE,
            null, 0, 0.0));

        // 高原：草皮下面直接是石头
        register(TalosBiomes.TALOS_PLATEAU, new TalosSurfaceProfile(
            GRASS, 1, STONE, 4, STONE, null, 0, 0.0));

        // 高山：表面大量石头 + 随机砾石袋
        register(TalosBiomes.TALOS_MOUNTAINS, new TalosSurfaceProfile(
            STONE, 2, STONE, 4, STONE,
            new BlockMetaPair(Blocks.gravel, (byte) 0), 3, 0.25));

        // 高山雪原：雪皮 + 石头 + 少量砾石
        register(TalosBiomes.TALOS_ALPINE, new TalosSurfaceProfile(
            new BlockMetaPair(Blocks.snow, (byte) 0), 1,
            STONE, 4, STONE,
            new BlockMetaPair(Blocks.gravel, (byte) 0), 3, 0.20));

        // 极地荒漠：雪 → 浮冰 → 石头
        register(TalosBiomes.TALOS_POLAR_DESERT, new TalosSurfaceProfile(
            new BlockMetaPair(Blocks.snow, (byte) 0), 1,
            new BlockMetaPair(Blocks.packed_ice, (byte) 0), 3,
            STONE,
            null, 0, 0.0));

        // 亚极地冻原
        register(TalosBiomes.TALOS_SUBPOLAR_TUNDRA, new TalosSurfaceProfile(
            GRASS, 1, DIRT, 3, STONE, null, 0, 0.0));
    }

    public static void register(BiomeGenBase biome, TalosSurfaceProfile profile) {
        if (biome != null && profile != null) {
            PROFILES.put(biome, profile);
        }
    }

    /** 未注册的群系返回默认配置（草 / 泥土 / 石头）。 */
    public static TalosSurfaceProfile get(BiomeGenBase biome) {
        TalosSurfaceProfile p = (biome == null) ? null : PROFILES.get(biome);
        return (p != null) ? p : DEFAULT;
    }
}
