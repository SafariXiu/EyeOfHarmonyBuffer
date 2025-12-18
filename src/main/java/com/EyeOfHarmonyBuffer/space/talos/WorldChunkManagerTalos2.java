package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.biome.BiomeGenBase;

import java.util.Random;

public class WorldChunkManagerTalos2 extends WorldChunkManagerSpace {

    private final SimplexNoiseOctave continentNoise;
    private final double continentScale = 0.0007D;

    public WorldChunkManagerTalos2() {
        super();
        // 你的 SimplexNoiseOctave 只需要倍频数量，不需要 seed
        this.continentNoise = new SimplexNoiseOctave(4);
    }

    /**
     * WorldChunkManagerSpace 传统接口：单一群系。
     * 为了兼容这里随便给一个默认的（比如平原），
     * 真正用的地方应该调用 getBiomeGenAt(x,z)。
     */
    @Override
    public BiomeGenBase getBiome() {
        return TalosBiomes.TALOS_PLAINS;
    }

    /** 根据坐标选择 Talos 的具体群系。 */
    private BiomeGenBase pickBiomeFor(int x, int z) {
        double cRaw = this.continentNoise.noise(x * continentScale, z * continentScale);
        double c = (cRaw + 1.0D) * 0.5D;
        // smoothstep 平滑
        c = c * c * (3.0D - 2.0D * c);

        if (c < 0.45D) {
            return TalosBiomes.TALOS_OCEAN;
        } else if (c < 0.55D) {
            return TalosBiomes.TALOS_BEACH;
        } else {
            return TalosBiomes.TALOS_PLAINS;
        }
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
}
