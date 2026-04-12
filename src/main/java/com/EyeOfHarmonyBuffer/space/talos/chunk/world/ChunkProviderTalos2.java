package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.BiomeDecoratorTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.WorldgenAPI;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.RiverDebugCarver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosBaseTerrain;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.*;

public class ChunkProviderTalos2 extends ChunkProviderSpaceLakes {

    private static final int CHUNK_SIZE = 16;
    private static final int WORLD_HEIGHT = 256;

    private final int worldSeedInt;

    public ChunkProviderTalos2(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.worldSeedInt = TalosLandMask.getWorldSeedInt(world);
    }

    @Override
    public String makeString() {
        return "Talos2Source_New";
    }

    @Override
    protected BiomeDecoratorSpace getBiomeGenerator() {
        return new BiomeDecoratorTalos2();
    }

    @Override
    protected net.minecraft.world.biome.BiomeGenBase[] getBiomesForGeneration() {
        return new net.minecraft.world.biome.BiomeGenBase[0];
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        clearChunkBlocks(blocks, meta);

        generateTerrainWithBaseHeightSimple(chunkX, chunkZ, blocks, meta);

        generateDebugRivers(chunkX, chunkZ, blocks, meta, worldSeedInt);
    }

    /**
     * 极简版：
     *  - 从 TalosBaseTerrain 取基础高度 baseHeight；
     *  - 仍然用 LandMask 的 isLand 决定陆地 / 海洋；
     *  - 陆地：基岩 0，下面全石头，最上面 1 层草；
     *  - 海洋：基岩 0，海底石头，上面全是水；
     *  - 只是在“表面 / 海底的高度”这一步用 baseHeight，而不是固定 seaLevel。
     */
    private void generateTerrainWithBaseHeightSimple(int chunkX, int chunkZ,
                                                     Block[] blocks, byte[] meta) {
        final int seaLevel = getWaterLevel();

        final int worldX0 = chunkX * CHUNK_SIZE;
        final int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                final int worldX = worldX0 + localX;
                final int worldZ = worldZ0 + localZ;

                WorldgenAPI.SampleResult landSample =
                    TalosLandMask.sample(worldX, worldZ, worldSeedInt);
                boolean isLand = (landSample != null && landSample.isLand);

                double baseHeightD = TalosBaseTerrain.sampleBaseHeight(
                    worldX, worldZ, worldSeedInt, seaLevel
                );

                int h = (int) Math.round(baseHeightD);
                if (h < 1) {
                    h = 1;
                } else if (h > WORLD_HEIGHT - 2) {
                    h = WORLD_HEIGHT - 2;
                }

                int bedrockIndex = getIndex(localX, 0, localZ);
                blocks[bedrockIndex] = Blocks.bedrock;
                meta[bedrockIndex] = 0;

                if (isLand) {
                    for (int y = 1; y < h; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.stone;
                        meta[idx] = 0;
                    }

                    int topIndex = getIndex(localX, h, localZ);
                    blocks[topIndex] = Blocks.grass;
                    meta[topIndex] = 0;

                    if (h < seaLevel) {
                        for (int y = h + 1; y <= seaLevel; y++) {
                            int idx = getIndex(localX, y, localZ);
                            blocks[idx] = Blocks.water;
                            meta[idx] = 0;
                        }
                    }

                } else {
                    int seabedY = Math.min(h, seaLevel - 1);
                    if (seabedY < 1) seabedY = 1;

                    for (int y = 1; y <= seabedY; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.stone;
                        meta[idx] = 0;
                    }

                    for (int y = seabedY + 1; y <= seaLevel; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.water;
                        meta[idx] = 0;
                    }
                }
            }
        }
    }

    /**
     * 老版的“平板”海陆渲染，只保留作调试用，不再从 onChunkProvider 调用。
     */
    @SuppressWarnings("unused")
    private void generateBasicLandWater(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        final int seaLevel = getWaterLevel();

        int worldX0 = chunkX * CHUNK_SIZE;
        int worldZ0 = chunkZ * CHUNK_SIZE;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                int worldX = worldX0 + localX;
                int worldZ = worldZ0 + localZ;

                WorldgenAPI.SampleResult result =
                    TalosLandMask.sample(worldX, worldZ, worldSeedInt);

                boolean isLand = (result != null && result.isLand);

                int bedrockIndex = getIndex(localX, 0, localZ);
                blocks[bedrockIndex] = Blocks.bedrock;
                meta[bedrockIndex] = 0;

                if (isLand) {
                    for (int y = 1; y < seaLevel; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.stone;
                        meta[idx] = 0;
                    }

                    int topIdx = getIndex(localX, seaLevel, localZ);
                    blocks[topIdx] = Blocks.grass;
                    meta[topIdx] = 0;
                } else {
                    for (int y = 1; y <= seaLevel; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.water;
                        meta[idx] = 0;
                    }
                }
            }
        }
    }

    private void generateDebugRivers(int chunkX, int chunkZ, Block[] blocks, byte[] meta, int worldSeedInt) {
        RiverDebugCarver.carveFlatChunk(
            chunkX, chunkZ,
            worldSeedInt,
            blocks, meta,
            getWaterLevel()
        );
    }

    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i] = 0;
        }
    }

    private static int getIndex(int x, int y, int z) {
        return (x * CHUNK_SIZE + z) * WORLD_HEIGHT + y;
    }

    // ====== 下面这些保持不变 ======

    @Override
    public void onPopulate(IChunkProvider provider, int x, int z) {
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
    protected boolean enableBiomeGenBaseBlock() {
        return false;
    }
}
