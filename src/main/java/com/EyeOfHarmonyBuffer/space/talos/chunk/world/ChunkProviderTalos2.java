package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.BiomeDecoratorTalos2;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceProfile;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.api.TalosCaveSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration.CaveCarver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.integration.CaveDecorator;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveChunkData;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

import java.util.*;

public class ChunkProviderTalos2 extends ChunkProviderSpaceLakes {

    private static final int CHUNK_SIZE = 16;
    private final int worldHeight;
    private final World world;

    private final int worldSeedInt;

    private static final boolean DEBUG_COASTLINE = true;

    public ChunkProviderTalos2(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.world = world;
        this.worldSeedInt = TalosLandMask.getWorldSeedInt(world);
        this.worldHeight = world.getActualHeight();
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

        TalosChunkContext ctx = TalosChunkContext.create(
            chunkX, chunkZ, worldSeedInt, getWaterLevel(), worldHeight
        );

        generateTerrainWithBaseHeightSimple(ctx, blocks, meta);
    }

    /**
     * 使用统一最终高度场（TalosTerrainHeights）生成基础陆地/海洋高度，并填充方块。
     *
     * 流程概述：
     *   1. 每列经 TalosTerrainHeights.sampleColumn(...) 取最终高度：
     *        基础高度 → 海岸塑形 → 裂谷塑形 → 山脉抬升 → 河岸/泛洪平原 → 河谷下切；
     *   2. 对最终高度 h 执行 clamp 到 [1, worldHeight-2]，避免越界；
     *   3. 按 isLand 决定填充：
     *        - 陆地：基岩 + [1, h) 石头 + 顶层方块；低于海平面的列灌水；
     *        - 海洋：基岩 + [1, seabedY] 石头 + [seabedY+1, seaLevel] 水。
     *
     * 注意：高度链实现只存在于 terrain_layer.api.TalosTerrainHeights，
     * 本方法不重复任何塑形逻辑，只做方块铺设。
     */
    private void generateTerrainWithBaseHeightSimple(TalosChunkContext ctx,
                                                     Block[] blocks, byte[] meta) {
        final int seaLevel = ctx.seaLevel;

        final int worldX0 = ctx.chunkX * CHUNK_SIZE;
        final int worldZ0 = ctx.chunkZ * CHUNK_SIZE;

        final LandMask16 landMask = ctx.landMask;

        // 洞穴数据：每区块取一次（系统未启用时返回 null，直接跳过雕刻）
        CaveChunkData caveData = TalosCaveSystem.dataForChunk(
            ctx.chunkX, ctx.chunkZ, worldSeedInt
        );

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                final int colIndex = localX * CHUNK_SIZE + localZ;
                final int worldX = worldX0 + localX;
                final int worldZ = worldZ0 + localZ;

                final boolean isLandFromMask =
                    (landMask != null && landMask.get(localX, localZ));

                TalosLandMask.Sample landSample = ctx.land[colIndex];

                final boolean isLand = isLandFromMask;
                final double shelfWeight =
                    (landSample != null ? landSample.shelfWeight : 0.0);

                // 最终高度场统一出口：基础 → 海岸 → 裂谷 → 山脉 → 河岸 → 河谷下切。
                TalosTerrainHeights.TerrainHeightSample ts =
                    TalosTerrainHeights.sampleColumn(ctx.terrainInputs(colIndex));
                double coastShapedHeightD = ts.coastD;
                double riverShapedHeightD = ts.preRiverD;
                double channelShapedHeightD = ts.surfaceD;

                boolean riverCarved = channelShapedHeightD < riverShapedHeightD - 0.01;

                int h = (int) Math.round(channelShapedHeightD);
                if (h < 1) {
                    h = 1;
                } else if (h > worldHeight - 2) {
                    h = worldHeight - 2;
                }
                // 只有真正低于水面的列才走“挖成河床/湖床”的填充；
                // 高于水面的岸滩、外坡和湿地干丘走正常地表（草/泥土），
                // 岸滩方块由 lakeMat 单独铺。
                boolean underwaterCarved = riverCarved && h < seaLevel;

                int bedrockIndex = getIndex(localX, 0, localZ);
                blocks[bedrockIndex] = Blocks.bedrock;
                meta[bedrockIndex] = 0;

                if (isLand) {
                    TalosSurfaceProfile profile =
                        TalosSurfaceRegistry.get(ctx.biomes[colIndex]);

                    // 源头湖岸 / 滩涂：湖区干岸和浅水底换方块（宏群系预设）
                    int topSolidY = underwaterCarved ? h - 1 : h;
                    BlockMetaPair lakeMat = TalosRiverSystem.getLakeSurfaceMaterial(
                        topSolidY, seaLevel, worldX, worldZ,
                        ctx.hydro[colIndex], ctx.macroPkg[colIndex]
                    );

                    if (underwaterCarved) {
                        // 河床：只露出深层（石头 / 砂岩…），不铺表层 / 填充层
                        for (int y = 1; y < h; y++) {
                            putBlock(blocks, meta, localX, y, localZ,
                                profile.deepBlock);
                        }
                        // 源头湖：湖床顶两格换成滩涂 / 干岸方块
                        if (lakeMat != null && h >= 2) {
                            putBlock(blocks, meta, localX, h - 1, localZ, lakeMat);
                            putBlock(blocks, meta, localX, h - 2, localZ, lakeMat);
                        } else if (h >= 2) {
                            // 河道：床顶铺斑块底料（砂砾 / 沙 / 黏土等，宏群系预设）
                            TalosRiverSystem.RiverbedMaterial rb =
                                TalosRiverSystem.getRiverbedMaterialAt(
                                    worldX, worldZ, worldSeedInt,
                                    ctx.macroPkg[colIndex]
                                );
                            if (rb != null) {
                                int top = h - 1;
                                int n = Math.min(rb.depth, top);
                                for (int i = 0; i < n; i++) {
                                    putBlock(blocks, meta, localX, top - i,
                                        localZ, rb.block);
                                }
                            }
                        }
                    } else {
                        int surfaceStart = h - profile.surfaceDepth + 1;
                        int fillerStart = surfaceStart - profile.fillerDepth;

                        for (int y = 1; y < h; y++) {
                            BlockMetaPair pair;
                            if (y < fillerStart) {
                                pair = profile.deepBlock;
                            } else if (y < surfaceStart) {
                                pair = profile.fillerBlock;
                            } else {
                                pair = profile.surfaceBlock;
                            }
                            putBlock(blocks, meta, localX, y, localZ, pair);
                        }

                        putBlock(blocks, meta, localX, h, localZ,
                            profile.surfaceBlock);
                        if (lakeMat != null) {
                            putBlock(blocks, meta, localX, h, localZ, lakeMat);
                            if (h >= 2) {
                                putBlock(blocks, meta, localX, h - 1, localZ, lakeMat);
                            }
                        }
                    }

                    if (h < seaLevel) {
                        int waterStart = riverCarved ? h : h + 1;

                        for (int y = waterStart; y <= seaLevel; y++) {
                            int idx = getIndex(localX, y, localZ);
                            blocks[idx] = Blocks.water;
                            meta[idx] = 0;
                        }
                    }

                    // 洞穴雕刻：方块填充完成后进行。
                    // 地表封层 / 入口竖井 / 水体避让规则都在 CaveCarver 内。
                    if (caveData != null) {
                        CaveCarver.carveColumn(
                            worldX, worldZ, localX, localZ,
                            topSolidY, seaLevel, ts.riverMask, ts.body,
                            caveData, blocks, meta, worldHeight, worldSeedInt
                        );
                    }

                } else {
                    int seabedY = TalosSeafloorShaper.computeSeabedY(
                        seaLevel,
                        false,
                        shelfWeight,
                        coastShapedHeightD,
                        worldHeight
                    );

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

                    // 海床下方也雕刻洞穴（含近海平滑带）：
                    // 按海床高度 + 2 格缓冲，网络在海陆交界处连续且不挖穿海床。
                    if (caveData != null && seabedY > 1) {
                        CaveCarver.carveColumn(
                            worldX, worldZ, localX, localZ,
                            seabedY, seaLevel, 1.0, null,
                            caveData, blocks, meta, worldHeight, worldSeedInt
                        );
                    }
                }
            }
        }

        // 洞穴风格化：雕刻完成后整块装饰（洞底铺层 / 钟乳石 / 塌方 / 入口碎石环）
        if (caveData != null) {
            CaveDecorator.decorateChunk(
                ctx.chunkX, ctx.chunkZ, worldSeedInt,
                blocks, meta, worldHeight, caveData
            );
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

    private void putBlock(Block[] blocks, byte[] meta, int x, int y, int z,
                          BlockMetaPair pair) {
        if (pair == null) {
            return;
        }
        int idx = getIndex(x, y, z);
        blocks[idx] = pair.getBlock();
        meta[idx] = pair.getMetadata();
    }

    @Override
    public void onPopulate(IChunkProvider provider, int x, int z) {
        // 装饰阶段放置了大量方块，但放置走的是轻量光照路径（不逐块重算光照），
        // 而 1.7.10 的延迟补光队列可能赶不上区块发送给客户端。
        // 这里在区块发出前同步补一次全区块天光 / 光照重算，消除新地面的伪影。
        Chunk chunk = this.world.getChunkFromChunkCoords(x, z);
        if (chunk == null || chunk.isEmpty()) {
            return;
        }

        BiomeGenBase biome = this.world.getBiomeGenForCoords(
            x * 16 + 8, z * 16 + 8
        );
        if (biome == TalosBiomes.TALOS_OCEAN || biome == TalosBiomes.TALOS_SHELF) {
            return; // 海洋 / 陆架没有装饰，不需要重算
        }

        chunk.generateSkylightMap();
        chunk.func_150809_p();
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
