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
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveGenerator;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime.CaveMegaHall;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import ganymedes01.etfuturum.ModBlocks;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import gregtech.api.GregTechAPI;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.EnumSkyBlock;
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
     *        - 陆地：基岩 + [1, h) 石头 + 顶层方块；水面由水场
     *          （TalosWaterField）授权，仅当「水面高于地表」时才灌水；
     *        - 海洋：基岩 + [1, seabedY] 石头 + [seabedY+1, 水面] 水（水面=海平面）。
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
                // 水面高度：水场权威输出（Double.NEGATIVE_INFINITY = 无水）。
                // 激进版规则：陆地默认无水，只有海洋 / 河道 / 水体 / 近海浅水带
                // 显式授权水面；干盆地等低于海平面的新地形天然不灌水。
                double waterLevel = ts.waterLevel;
                int waterSurfaceYInt = (int) Math.floor(waterLevel);

                // 只有真正低于水面的列才走“挖成河床/湖床”的填充；
                // 高于水面的岸滩、外坡和湿地干丘走正常地表（草/泥土），
                // 岸滩方块由 lakeMat 单独铺。
                boolean underwaterCarved = riverCarved && waterLevel > h;

                int bedrockIndex = getIndex(localX, 0, localZ);
                blocks[bedrockIndex] = Blocks.bedrock;
                meta[bedrockIndex] = 0;

                if (isLand) {
                    TalosSurfaceProfile profile =
                        TalosSurfaceRegistry.get(ctx.biomes[colIndex]);

                    // 源头湖岸 / 滩涂：湖区干岸和浅水底换方块（宏群系预设）。
                    // 水面传水场输出（带水位偏移的湖也用正确水面分类）；
                    // 无水列（干盆地等）不查滩涂方块，走正常地表。
                    int topSolidY = underwaterCarved ? h - 1 : h;
                    BlockMetaPair lakeMat =
                        (waterLevel != Double.NEGATIVE_INFINITY)
                            ? TalosRiverSystem.getLakeSurfaceMaterial(
                                topSolidY, waterSurfaceYInt, worldX, worldZ,
                                ctx.hydro[colIndex], ctx.macroPkg[colIndex])
                            : null;

                    if (underwaterCarved) {
                        // 河床：只露出深层（石头 / 砂岩…），不铺表层 / 填充层
                        for (int y = 1; y < h; y++) {
                            putBlock(blocks, meta, localX, y, localZ,
                                rockPair(profile.deepBlock,
                                    worldX, y, worldZ, worldSeedInt));
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
                                pair = rockPair(profile.deepBlock,
                                    worldX, y, worldZ, worldSeedInt);
                            } else if (y < surfaceStart) {
                                // 填充层也走岩性变体：rockPair 只对普通石头生效
                                // （泥土 / 沙岩等原样返回）。高原 / 高山等
                                // fillerBlock=STONE 的群系因此从地表下 1 格起
                                // 就是变体岩，避免河岸过渡带出现
                                // 「纯石头填充层 + 深层变体」的断层观感。
                                pair = rockPair(profile.fillerBlock,
                                    worldX, y, worldZ, worldSeedInt);
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

                    // 灌水：只有「水面高于地表」的列才灌（水场授权）。
                    // 干盆地 / 干裂谷等无水列 waterLevel = -inf，天然跳过。
                    if (waterLevel > h) {
                        int waterStart = riverCarved ? h : h + 1;

                        for (int y = waterStart; y <= waterSurfaceYInt; y++) {
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
                            topSolidY, waterSurfaceYInt, ts.riverMask, ts.body,
                            caveData, blocks, meta, worldHeight, worldSeedInt
                        );
                    }

                } else {
                    int seabedY = TalosSeafloorShaper.computeSeabedY(
                        seaLevel,
                        false,
                        shelfWeight,
                        coastShapedHeightD,
                        worldX,
                        worldZ,
                        worldSeedInt,
                        worldHeight
                    );

                    TalosSeafloorShaper.SeafloorFill sf =
                        TalosSeafloorShaper.computeSeafloorFill(
                            shelfWeight, worldX, worldZ, worldSeedInt);
                    TalosSeafloorShaper.SeafloorMaterial surfaceMat =
                        TalosSeafloorShaper.SeafloorMaterial.ROCK;
                    TalosSeafloorShaper.SeafloorMaterial fillerMat =
                        TalosSeafloorShaper.SeafloorMaterial.ROCK;
                    int surfaceDepth = 0;
                    int fillerDepth = 0;
                    if (sf != null) {
                        surfaceMat = sf.surface;
                        fillerMat = sf.filler;
                        surfaceDepth = Math.min(sf.surfaceDepth, seabedY);
                        fillerDepth = Math.min(
                            sf.fillerDepth, seabedY - surfaceDepth);
                    }
                    int fillerTop = seabedY - surfaceDepth;
                    int fillerBottom = fillerTop - fillerDepth + 1;

                    for (int y = 1; y <= seabedY; y++) {
                        int idx = getIndex(localX, y, localZ);
                        BlockMetaPair pair = null;
                        if (y > fillerTop) {
                            pair = seafloorPair(surfaceMat);
                        } else if (y >= fillerBottom) {
                            pair = seafloorPair(fillerMat);
                        }
                        if (pair != null) {
                            putBlock(blocks, meta, localX, y, localZ, pair);
                        } else {
                            putRock(blocks, meta, idx,
                                worldX, y, worldZ, worldSeedInt);
                        }
                    }

                    // 海洋列水面 = 海平面（水场对海洋恒授权），与陆地共用同一水面口径。
                    for (int y = seabedY + 1; y <= waterSurfaceYInt; y++) {
                        int idx = getIndex(localX, y, localZ);
                        blocks[idx] = Blocks.water;
                        meta[idx] = 0;
                    }

                    // 海床下方也雕刻洞穴（含近海平滑带）：
                    // 按海床高度 + 2 格缓冲，网络在海陆交界处连续且不挖穿海床。
                    if (caveData != null && seabedY > 1) {
                        CaveCarver.carveColumn(
                            worldX, worldZ, localX, localZ,
                            seabedY, waterSurfaceYInt, 1.0, null,
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

        // 洞厅是地下巨型空腔（cy≈34，ry≈29，顶约 60~63），洞厅上方覆盖层
        // 可能只剩几格石头。generateSkylightMap 在这种薄覆盖层下可能把
        // 洞厅内部的天光算成满值（15），客户端渲染成"整块像被太阳照到"，
        // 而实体/手持物品用的是实时光照数组（暗）→ 只有地形亮、物品暗。
        // 这里把洞厅覆盖列的天空光强制清零：洞厅在地下，本就不该有天空光；
        // 玩家若真的挖通到地表，实时光照传播会重新把光送进来（恢复正常）。
        zeroMegaHallSkyLight(chunk, x, z);
    }

    /**
     * 把洞厅覆盖列的天空光清零（洞厅 = 地下空腔，无天空光）。
     * 只对含洞厅的区块生效，洞厅占超级格约 0.5%，其余区块零开销。
     */
    private void zeroMegaHallSkyLight(Chunk chunk, int chunkX, int chunkZ) {
        // 只查本区块所在超级格（洞厅被限制在超级格内部）
        CaveMegaHall hall = CaveGenerator.megaHallAt(
            chunkX * 16 + 8, chunkZ * 16 + 8, worldSeedInt);
        if (hall == null) {
            return;
        }
        int y0 = Math.max(1, (int) Math.floor(hall.minY));
        int y1 = Math.min(worldHeight - 1, (int) Math.ceil(hall.maxY));
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                if (!hall.insideHorizontal(chunkX * 16 + lx + 0.5,
                    chunkZ * 16 + lz + 0.5)) {
                    continue;
                }
                for (int y = y0; y <= y1; y++) {
                    chunk.setLightValue(EnumSkyBlock.Sky, lx, y, lz, 0);
                }
            }
        }
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

    /** 深部石头换成 GT 原生岩性（黑/红花岗岩、大理石、玄武岩），保证矿脉变体矿石可生成。 */
    private static BlockMetaPair rockPair(BlockMetaPair pair,
                                          int wx, int wy, int wz, int seed) {
        if (pair != null && pair.getBlock() == Blocks.stone
            && pair.getMetadata() == 0) {
            return rockPairFor(wx, wy, wz, seed);
        }
        return pair;
    }

    private static void putRock(net.minecraft.block.Block[] blocks, byte[] meta,
                                int idx, int wx, int wy, int wz, int seed) {
        BlockMetaPair p = rockPairFor(wx, wy, wz, seed);
        blocks[idx] = p.getBlock();
        meta[idx] = p.getMetadata();
    }

    private static BlockMetaPair rockPairFor(int wx, int wy, int wz, int seed) {
        switch (CaveMath.rockVariant3D(wx, wy, wz, seed)) {
            case 4:
                return efrRockPair(ModBlocks.DEEPSLATE);
            case 5:
                return efrRockPair(ModBlocks.TUFF);
            case 1:
                // 黑花岗岩
                return new BlockMetaPair(GregTechAPI.sBlockGranites, (byte) 0);
            case 2:
                // 红花岗岩
                return new BlockMetaPair(GregTechAPI.sBlockGranites, (byte) 8);
            case 3:
                // 大理石
                return new BlockMetaPair(GregTechAPI.sBlockStones, (byte) 0);
            default:
                // 玄武岩
                return new BlockMetaPair(GregTechAPI.sBlockStones, (byte) 8);
        }
    }

    /** EFR 方块可能因配置未启用而为 null，此时回退普通石头。 */
    private static BlockMetaPair efrRockPair(ModBlocks mb) {
        Block b = mb.get();
        if (b == null) {
            b = Blocks.stone;
        }
        return new BlockMetaPair(b, (byte) 0);
    }

    private static final BlockMetaPair SEAFLOOR_SAND =
        new BlockMetaPair(Blocks.sand, (byte) 0);
    private static final BlockMetaPair SEAFLOOR_GRAVEL =
        new BlockMetaPair(Blocks.gravel, (byte) 0);
    private static final BlockMetaPair SEAFLOOR_CLAY =
        new BlockMetaPair(Blocks.clay, (byte) 0);

    /** 海床材质 -> 方块；ROCK 返回 null（保持深层岩石变体）。 */
    private static BlockMetaPair seafloorPair(
        TalosSeafloorShaper.SeafloorMaterial m) {
        switch (m) {
            case SAND:
                return SEAFLOOR_SAND;
            case GRAVEL:
                return SEAFLOOR_GRAVEL;
            case CLAY:
                return SEAFLOOR_CLAY;
            default:
                return null;
        }
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
