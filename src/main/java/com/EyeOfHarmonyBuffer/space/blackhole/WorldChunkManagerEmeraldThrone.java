package com.EyeOfHarmonyBuffer.space.blackhole;

import micdoodle8.mods.galacticraft.api.prefab.world.gen.WorldChunkManagerSpace;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * 翡翠王座群系管理：全部查询路径统一按「原始噪声高度」划分——
 * h ≤ 64 = 死亡之海，h &gt; 64 = 生之大陆（与 ChunkProviderEmeraldThrone 共用同一高度公式，
 * 单一噪声输出即唯一真相）。
 * <p>必须同时覆写区块生成 / 渲染数组版查询：否则 chunk 里存的 biome 全是默认值
 * （基类 getBiomesForGeneration 返回全 getBiome()），F3 / 渲染读出来全是同一个群系。
 */
public class WorldChunkManagerEmeraldThrone extends WorldChunkManagerSpace {

    private final NoiseGeneratorPerlin[] noises;
    private final int worldHeight;

    public WorldChunkManagerEmeraldThrone(World world) {
        super();
        this.noises = EmeraldThroneTerrain.createNoises(world.getWorldInfo().getSeed());
        this.worldHeight = world.getActualHeight();
    }

    private BiomeGenBase biomeAt(int x, int z) {
        int h = EmeraldThroneTerrain
            .sampleHeight(this.noises[0], this.noises[1], this.noises[2], this.noises[3], x, z,
                this.worldHeight - 2);
        return h <= EmeraldThroneTerrain.SEA_LEVEL
            ? BiomeGenEmeraldDeadSea.INSTANCE
            : BiomeGenEmeraldLivingLand.INSTANCE;
    }

    @Override
    public BiomeGenBase getBiome() {
        return BiomeGenEmeraldLivingLand.INSTANCE;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return biomeAt(x, z);
    }

    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] arr, int x, int z, int w, int h) {
        if (arr == null || arr.length < w * h) {
            arr = new BiomeGenBase[w * h];
        }
        for (int i = 0; i < w * h; i++) {
            arr[i] = biomeAt(x + (i % w), z + (i / w));
        }
        return arr;
    }

    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] arr, int x, int z, int w, int h, boolean cache) {
        if (arr == null || arr.length < w * h) {
            arr = new BiomeGenBase[w * h];
        }
        for (int i = 0; i < w * h; i++) {
            arr[i] = biomeAt(x + (i % w), z + (i / w));
        }
        return arr;
    }
}
