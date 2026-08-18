package com.EyeOfHarmonyBuffer.space.blackhole;

import java.util.Random;

import java.util.Collections;
import java.util.List;

import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * 翡翠王座区块生成器：完全接管地形（无 GC / 原版地形参与）。
 * 三层噪声：大陆形状（低频）→ 丘陵起伏（中频）→ 岩石细节（高频，决定山顶裸岩）。
 * 海平面 62：深海沙底、浅海沙岸、陆地草皮 / 泥土 / 石头。
 */
public class ChunkProviderEmeraldThrone extends ChunkProviderSpaceLakes {

    private static final int CHUNK_SIZE = 16;
    private static final int SEA_LEVEL = 62;

    private final World world;
    private final int worldHeight;

    private final NoiseGeneratorPerlin continentNoise;
    private final NoiseGeneratorPerlin hillNoise;
    private final NoiseGeneratorPerlin rockNoise;

    public ChunkProviderEmeraldThrone(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.world = world;
        this.worldHeight = world.getActualHeight();
        Random rand = new Random(seed);
        this.continentNoise = new NoiseGeneratorPerlin(rand, 4);
        this.hillNoise = new NoiseGeneratorPerlin(rand, 8);
        this.rockNoise = new NoiseGeneratorPerlin(rand, 3);
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
        return 64;
    }

    @Override
    public boolean canGenerateWaterBlock() {
        return true;
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

                double continent = this.continentNoise.func_151601_a(wx * 0.0035D, wz * 0.0035D);
                double hill = this.hillNoise.func_151601_a(wx * 0.02D, wz * 0.02D);
                double rock = this.rockNoise.func_151601_a(wx * 0.09D, wz * 0.09D);

                int h = (int) Math.round(63.0D + continent * 26.0D + hill * 9.0D);
                if (h < 45) {
                    h = 45;
                }
                if (h > this.worldHeight - 2) {
                    h = this.worldHeight - 2;
                }

                putBlock(blocks, meta, lx, 0, lz, Blocks.bedrock, 0);

                if (h <= SEA_LEVEL) {
                    int seabed = h - 3;
                    if (seabed < 1) {
                        seabed = 1;
                    }
                    for (int y = 1; y < seabed; y++) {
                        putBlock(blocks, meta, lx, y, lz, Blocks.stone, 0);
                    }
                    for (int y = seabed; y < h; y++) {
                        putBlock(blocks, meta, lx, y, lz, Blocks.sand, 0);
                    }
                    for (int y = h; y <= SEA_LEVEL; y++) {
                        putBlock(blocks, meta, lx, y, lz, Blocks.water, 0);
                    }
                } else {
                    int fillerTop = h - 4;
                    if (fillerTop < 1) {
                        fillerTop = 1;
                    }
                    for (int y = 1; y < fillerTop; y++) {
                        putBlock(blocks, meta, lx, y, lz, Blocks.stone, 0);
                    }
                    for (int y = fillerTop; y < h - 1; y++) {
                        putBlock(blocks, meta, lx, y, lz, Blocks.dirt, 0);
                    }
                    if (rock > 0.28D || h > 100) {
                        putBlock(blocks, meta, lx, h - 1, lz, Blocks.stone, 0);
                    } else {
                        putBlock(blocks, meta, lx, h - 1, lz, Blocks.grass, 0);
                    }
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
