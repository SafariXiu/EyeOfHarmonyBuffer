package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Collections;
import java.util.List;

import ganymedes01.etfuturum.ModBlocks;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * 翡翠王座区块生成器：完全接管地形（无 GC / 原版地形参与）。
 * 高度公式与群系划分共用 {@link EmeraldThroneTerrain}：
 * y ≤ 64 = 死亡之海（海洋：深色海床 + 水），y &gt; 64 = 生之大陆（草皮大陆 / 裸岩高原）。
 * 大陆骨架为低频阶梯噪声——一大块一大块的连续大陆与深海，高度封顶 140。
 */
public class ChunkProviderEmeraldThrone extends ChunkProviderSpaceLakes {

    private static final int CHUNK_SIZE = 16;

    private final World world;
    private final int worldHeight;

    private final NoiseGeneratorPerlin continentNoise;
    private final NoiseGeneratorPerlin hillNoise;
    private final NoiseGeneratorPerlin rockNoise;
    private final NoiseGeneratorPerlin peakNoise;

    public ChunkProviderEmeraldThrone(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.world = world;
        this.worldHeight = world.getActualHeight();
        NoiseGeneratorPerlin[] noises = EmeraldThroneTerrain.createNoises(seed);
        this.continentNoise = noises[0];
        this.hillNoise = noises[1];
        this.rockNoise = noises[2];
        this.peakNoise = noises[3];
    }

    @Override
    public String makeString() {
        return "EmeraldThroneSource";
    }

    @Override
    protected BiomeDecoratorSpace getBiomeGenerator() {
        return new BiomeDecoratorEmeraldThrone();
    }

    @Override
    protected BiomeGenBase[] getBiomesForGeneration() {
        return new BiomeGenBase[0];
    }

    @Override
    protected BlockMetaPair getWaterBlock() {
        return new BlockMetaPair(Blocks.water, (byte) 0);
    }

    @Override
    protected BlockMetaPair getGrassBlock() {
        return new BlockMetaPair(Blocks.grass, (byte) 0);
    }

    @Override
    protected BlockMetaPair getDirtBlock() {
        return new BlockMetaPair(Blocks.dirt, (byte) 0);
    }

    @Override
    protected BlockMetaPair getStoneBlock() {
        return new BlockMetaPair(Blocks.stone, (byte) 0);
    }

    protected BlockMetaPair getSandBlock() {
        return new BlockMetaPair(Blocks.sand, (byte) 0);
    }

    @Override
    protected net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[] getMonsters() {
        return new net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[] getCreatures() {
        return new net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[] getWaterCreatures() {
        return new net.minecraft.world.biome.BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected List<MapGenBaseMeta> getWorldGenerators() {
        return Collections.emptyList();
    }

    @Override
    public double getHeightModifier() {
        return 15.0;
    }

    @Override
    public void onPopulate(IChunkProvider provider, int x, int z) {
        // 装饰阶段放置的方块走轻量光照路径，这里在区块发出前同步补一次光照，
        // 消除新地面 / 树木的暗块伪影（塔罗斯-2 同款做法）。
        net.minecraft.world.chunk.Chunk chunk = this.world.getChunkFromChunkCoords(x, z);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        chunk.generateSkylightMap();
        chunk.func_150809_p();
    }

    @Override
    public int getWaterLevel() {
        return EmeraldThroneTerrain.SEA_LEVEL;
    }

    @Override
    public boolean canGenerateWaterBlock() {
        // 死亡之海无任何水：禁止基类生成任何水体
        return false;
    }

    @Override
    public boolean canGenerateIceBlock() {
        return false;
    }

    @Override
    protected boolean enableBiomeGenBaseBlock() {
        // 地形完全自绘，不需要基类的 biome 方块替换
        return false;
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        clearChunkBlocks(blocks, meta);

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int lx = 0; lx < CHUNK_SIZE; lx++) {
            for (int lz = 0; lz < CHUNK_SIZE; lz++) {
                int wx = worldX0 + lx;
                int wz = worldZ0 + lz;

                int h = EmeraldThroneTerrain
                    .sampleHeight(this.continentNoise, this.hillNoise, this.rockNoise,
                        this.peakNoise, wx, wz, this.worldHeight - 2);

                putBlock(blocks, meta, lx, 0, lz, Blocks.bedrock, 0);

                // —— 死亡之海（y≤64）：ETF 深板岩（塔罗斯-2 同款获取，找不到时兜底石头）——
                // —— 生之大陆（y>64）：石头 ——
                Block fillBlock;
                if (h <= EmeraldThroneTerrain.SEA_LEVEL) {
                    Block deepslate = ModBlocks.DEEPSLATE.get();
                    fillBlock = deepslate != null ? deepslate : Blocks.stone;
                } else {
                    fillBlock = Blocks.stone;
                }
                for (int y = 1; y < h; y++) {
                    putBlock(blocks, meta, lx, y, lz, fillBlock, 0);
                }
            }
        }
    }

    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i] = 0;
        }
    }

    private int getIndex(int x, int y, int z) {
        return (x * CHUNK_SIZE + z) * worldHeight + y;
    }

    private void putBlock(Block[] blocks, byte[] meta, int x, int y, int z, Block block, int m) {
        int idx = getIndex(x, y, z);
        blocks[idx] = block;
        meta[idx] = (byte) m;
    }
}
