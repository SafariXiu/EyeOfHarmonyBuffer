package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * V2 轨 · 群系查询入口。
 *
 * 判据全部落在 {@link V2BiomeSelect}（气候带 × 地貌，L1c）；本类只做 Kind → BiomeGenBase 映射。
 * 查询走 {@link V2BiomeField} 的 1km LUT（含空间平滑），与地形生成同源，
 * 因此群系不会与地形错位。
 */
public final class V2BiomePicker {

    private V2BiomePicker() {}

    /** 单点群系查询（WorldChunkManager / 装饰用）。 */
    public static BiomeGenBase biomeAt(int x, int z, int worldSeedInt) {
        return biomeOf(V2BiomeField.kind(x, z, worldSeedInt));
    }

    /** Kind → 群系对象。 */
    public static BiomeGenBase biomeOf(V2BiomeSelect.Kind k) {
        switch (k) {
            case OCEAN:            return TalosBiomes.TALOS_OCEAN;
            case SHELF:            return TalosBiomes.TALOS_SHELF;
            case BASIN:            return TalosBiomes.TALOS_BASIN;
            case PLATEAU:          return TalosBiomes.TALOS_PLATEAU;
            case MOUNTAINS:        return TalosBiomes.TALOS_MOUNTAINS;
            case ALPINE:           return TalosBiomes.TALOS_ALPINE;
            case DESERT:           return TalosBiomes.TALOS_DESERT;
            case SAVANNA:          return TalosBiomes.TALOS_SAVANNA;
            case TROPICAL_RAIN:    return TalosBiomes.TALOS_TROPICAL_RAIN;
            case WARM_STEPPE:      return TalosBiomes.TALOS_WARM_STEPPE;
            case TEMPERATE_FOREST: return TalosBiomes.TALOS_TEMPERATE_FOREST;
            case TEMPERATE_STEPPE: return TalosBiomes.TALOS_TEMPERATE_STEPPE;
            case COOL_FOREST:      return TalosBiomes.TALOS_COOL_FOREST;
            case SUBPOLAR_TUNDRA:  return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
            case POLAR_DESERT:     return TalosBiomes.TALOS_POLAR_DESERT;
            case PLAINS:
            default:               return TalosBiomes.TALOS_PLAINS;
        }
    }
}
