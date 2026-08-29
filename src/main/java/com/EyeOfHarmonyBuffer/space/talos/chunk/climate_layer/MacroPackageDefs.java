package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.NoiseUtil;
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
                // 寒带高原不再使用 Alpine：Alpine 只留给挤压带 PEAK（DLA 山带）。
                // 寒带高原是「高原 + 极地荒漠」——地形是 POLAR_HIGHLAND 宏包的高原带，
                // 观感统一，不会顶着"山峰"名字却呈现噪声高原。
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_PLATEAU,
                    TalosBiomes.TALOS_POLAR_DESERT
                };

            // ===== 最高峰（挤压带核心注入，只含地形最高的群系） =====
            case MOUNTAIN_PEAK:
                return new BiomeGenBase[] {
                    TalosBiomes.TALOS_ALPINE
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

    /**
     * 低频连贯的确定性 biome 选择：用于板块边界覆盖等"整带注入"场景。
     *
     * 与 pickDeterministicBiome 的区别：后者逐点哈希，会产生细碎斑块；
     * 这里使用 4096 格尺度的 Value Noise，让群系补丁大小与宏细胞 / 正常
     * 子块尺度一致（几千格一块），避免边界带内群系"普遍偏小"。
     */
    public static BiomeGenBase pickCoherentBiome(MacroPackageId id,
                                                 int x, int z,
                                                 int worldSeedInt) {
        BiomeGenBase[] list = getBiomes(id);
        if (list == null || list.length == 0) {
            return TalosBiomes.TALOS_PLAINS;
        }
        if (list.length == 1) {
            return list[0];
        }

        int seed = worldSeedInt ^ (id.ordinal() * 7347);
        double n = NoiseUtil.coreNoise2D(
            x / 4096.0, z / 4096.0, seed
        ); // [0,1)
        int idx = (int) (n * list.length);
        if (idx < 0) {
            idx = 0;
        }
        if (idx >= list.length) {
            idx = list.length - 1;
        }
        return list[idx];
    }
}
