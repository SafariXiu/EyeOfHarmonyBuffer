package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseUtil;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Random;

public final class MacroPackageDefs {

    private MacroPackageDefs() {}

    public static BiomeGenBase[] getBiomes(MacroPackageId id) {
        switch (id) {
            // ===== 海洋 =====
            case OCEANIC:
                // 海洋宏群系：深海 + 陆架
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_OCEAN,
                    TalosBiomes.TALOS_SHELF
                };

            // ===== 热带 / 亚热带 =====
            case TROPICAL_HUMID:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_TROPICAL_RAIN,
                    TalosBiomes.TALOS_BASIN,    // 湿热盆地
                    TalosBiomes.TALOS_PLATEAU   // 热带高原（局部）
                };

            case TROPICAL_DRY:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_SAVANNA,
                    TalosBiomes.TALOS_WARM_STEPPE,
                    TalosBiomes.TALOS_DESERT
                };

            // ===== 温带 =====
            case TEMPERATE_LOWLAND:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_PLAINS,
                    TalosBiomes.TALOS_TEMPERATE_STEPPE
                };

            case TEMPERATE_FORESTED:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_TEMPERATE_FOREST,
                    TalosBiomes.TALOS_COOL_FOREST
                };

            case TEMPERATE_HIGHLAND:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_PLATEAU,
                    TalosBiomes.TALOS_MOUNTAINS
                };

            // ===== 凉爽 / 亚寒带 / 寒带 =====
            case COOL_FORESTED:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_COOL_FOREST,
                    TalosBiomes.TALOS_TEMPERATE_FOREST
                };

            case SUBPOLAR_TUNDRA:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_SUBPOLAR_TUNDRA
                };

            case POLAR_HIGHLAND:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_ALPINE,
                    TalosBiomes.TALOS_POLAR_DESERT
                };

            default:
                return new BiomeGenBase[] { TalosBiomes.TALOS_PLAINS };
        }
    }

    public static BiomeGenBase pickRandomBiome(MacroPackageId id, Random rand) {
        BiomeGenBase[] list = getBiomes(id);
        if (list == null || list.length == 0) {
            return TalosBiomes.TALOS_PLAINS;
        }
        return list[rand.nextInt(list.length)];
    }

    /**
     * 基于坐标 + worldSeedInt 的确定性 biome 选择：
     * - 相同 worldSeedInt + (x,z) 必然得到同一个 biome；
     * - 不依赖 Random 实例，也不受调用顺序影响。
     */
    public static BiomeGenBase pickDeterministicBiome(MacroPackageId id,
                                                      int x, int z,
                                                      int worldSeedInt) {
        BiomeGenBase[] list = getBiomes(id);
        if (list == null || list.length == 0) {
            return TalosBiomes.TALOS_PLAINS;
        }
        int seed = worldSeedInt ^ (id.ordinal() * 7347);
        double r = NoiseUtil.hash2(x, z, seed);
        int idx = (int) (r * list.length);
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= list.length) {
            idx = list.length - 1;
        }
        return list[idx];
    }
}
