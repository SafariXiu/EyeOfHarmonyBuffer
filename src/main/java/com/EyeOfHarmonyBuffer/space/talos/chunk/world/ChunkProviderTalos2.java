package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.BiomeDecoratorTalos2;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceProfile;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverChannelShaper;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverTerrainModifier;
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
    private static final boolean USE_CHUNK_BLUR_BANK = true;

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
            chunkX, chunkZ, worldSeedInt, getWaterLevel()
        );

        generateTerrainWithBaseHeightSimple(ctx, blocks, meta);
    }

    /**
     * 使用 TalosBaseTerrain + TalosRiverSystem 生成基础陆地/海洋高度，并填充方块。
     *
     * 流程概述：
     *   1. 调用 TalosBaseTerrain.sampleBaseHeight(...) 计算「未考虑河流」的基础高度 baseHeight；
     *   2. 对陆地区域，使用 TalosRiverTerrainModifier.applyRiverBankShaping(...)：
     *        - 基于 TalosRiverSystem.getRiverMask(...) 的河流影响掩码；
     *        - 只在 baseHeight 高于河面（seaLevel）时，对靠近河流的高地做「河岸压低」；
     *        - 在 mask ∈ (0.7, 0.8) 区间内，将高度从原始地形平滑过渡到河面高度；
     *        - 在 mask ≥ 0.8 时，直接把高度压到河面高度（形成贴河的低缓河岸）；
     *        - 远离河流（mask ≤ 0.7）、低于河面的区域保持原始高度不变；
     *   3. 对最终高度 h 执行 clamp 到 [1, worldHeight-2]，避免越界；
     *   4. 按 isLand 决定填充：
     *        - 陆地：
     *            - y = 0 放置基岩；
     *            - [1, h) 填充石头，y = h 顶层放草；
     *            - 若 h < seaLevel，则 [h+1, seaLevel] 用水填充（形成内陆湖/洼地积水）；
     *        - 海洋：
     *            - y = 0 放置基岩；
     *            - [1, seabedY] 填充石头（海底），seabedY = min(h, seaLevel-1)；
     *            - [seabedY+1, seaLevel] 全部填充水；
     *   5. DEBUG_COASTLINE 为 true 时，在陆地顶层用不同方块标记海岸权重（调试用）。
     *
     * 注意：
     *   - 本方法只负责“标高 + 基础方块”的铺设；源头湖 / 暗河井等细节
     *     已并入高度场雕刻（TalosRiverProfile.computeChannelBedY）。
     *   - 河岸压低逻辑仅依赖世界坐标 (worldX, worldZ)、世界种子 int 和 seaLevel，
     *     不在这里直接操作方块数组，保证高度场是可重现、与方块填充解耦的。
     */
    private void generateTerrainWithBaseHeightSimple(TalosChunkContext ctx,
                                                     Block[] blocks, byte[] meta) {
        final int seaLevel = ctx.seaLevel;

        final int worldX0 = ctx.chunkX * CHUNK_SIZE;
        final int worldZ0 = ctx.chunkZ * CHUNK_SIZE;

        final LandMask16 landMask = ctx.landMask;

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                final int colIndex = localX * CHUNK_SIZE + localZ;
                final int worldX = worldX0 + localX;
                final int worldZ = worldZ0 + localZ;

                final boolean isLandFromMask =
                    (landMask != null && landMask.get(localX, localZ));

                TalosLandMask.Sample landSample = ctx.land[colIndex];

                final boolean isLand = isLandFromMask;
                final double coastWeight =
                    (landSample != null ? landSample.coastWeight : 0.0);
                final double shelfWeight =
                    (landSample != null ? landSample.shelfWeight : 0.0);

                double baseHeightD = ctx.baseHeight[colIndex];

                double bankIntensity;
                if (USE_CHUNK_BLUR_BANK) {
                    bankIntensity = ctx.bankIntensity[colIndex];
                } else {
                    bankIntensity = sampleSmoothedBankIntensity(worldX, worldZ);
                }

                var bankPreset = TalosRiverTerrainModifier.bankPreset(bankIntensity);

                // 与 TalosRiverSystem.getRiverMask 语义一致：
                // 非陆地（按逐点海陆采样判断）视为无河流影响。
                double riverMask =
                    (landSample != null && landSample.isLand)
                        ? ctx.hydro[colIndex].mask
                        : 0.0;

                double coastShapedHeightD = TalosCoastlineShaper.applyCoastlineShaping(
                    baseHeightD,
                    seaLevel,
                    isLand,
                    coastWeight
                );

                // 板块边界塑形：分离带 → 风格化裂谷悬崖（外崖面 + 崖缘 + 倒石堆 + 谷底）
                // 强度来自网格级构造风格层（与群系/宏包覆盖同源），
                // 外崖面 0.12~0.2 的下落与带边缘过渡都由平滑场驱动。
                TalosMacroClimate.TectonicStyleSample tectonic = TalosMacroClimate
                    .getTectonicStyleSample(worldX, worldZ, worldSeedInt);
                double riftStrength = tectonic.smoothedDivergence;
                PlateBoundaryState riftState = (riftStrength > 0.0)
                    ? PlateBoundaryState.DIVERGENT
                    : (landSample != null ? landSample.plateBoundaryState : null);
                double riftWeight =
                    (riftState == PlateBoundaryState.DIVERGENT)
                        ? riftStrength
                        : (landSample != null ? landSample.plateBoundaryWeight : 0.0);
                double riftShapedHeightD = TalosPlateBoundaryShaper.applyRiftShaping(
                    coastShapedHeightD,
                    seaLevel,
                    isLand,
                    riftState,
                    riftWeight,
                    worldX,
                    worldZ,
                    worldSeedInt
                );

                // 河岸塑形放在裂谷塑形之后：泛洪平原必须作用在最终地形上，
                // 否则裂谷压高会把它重新抬回谷底高度（裂谷里的河因此没有泛洪平原）。
                double riverShapedHeightD = TalosRiverTerrainModifier.applyRiverBankShaping(
                    worldX, worldZ,
                    worldSeedInt,
                    riftShapedHeightD,
                    seaLevel,
                    bankPreset,
                    isLand,
                    riverMask
                );

                // 河谷雕刻（高度场版）：在填充方块前把河谷做进高度场，
                // 河道两侧自然成坡。阶段 A 仅对陆地列生效，海洋一侧保持原样。
                double channelShapedHeightD;
                if (isLand) {
                    channelShapedHeightD = TalosRiverChannelShaper.applyRiverChannelShaping(
                        worldX, worldZ,
                        worldSeedInt,
                        riverShapedHeightD,
                        seaLevel,
                        ctx.hydro[colIndex],
                        ctx.macroPkg[colIndex]
                    );
                } else {
                    channelShapedHeightD = riverShapedHeightD;
                }

                boolean riverCarved = channelShapedHeightD < riverShapedHeightD - 0.01;

                int h = (int) Math.round(channelShapedHeightD);
                if (h < 1) {
                    h = 1;
                } else if (h > worldHeight - 2) {
                    h = worldHeight - 2;
                }

                int bedrockIndex = getIndex(localX, 0, localZ);
                blocks[bedrockIndex] = Blocks.bedrock;
                meta[bedrockIndex] = 0;

                if (isLand) {
                    TalosSurfaceProfile profile =
                        TalosSurfaceRegistry.get(ctx.biomes[colIndex]);

                    // 源头湖岸 / 滩涂：湖区干岸和浅水底换方块（宏群系预设）
                    int topSolidY = riverCarved ? h - 1 : h;
                    BlockMetaPair lakeMat = TalosRiverSystem.getLakeSurfaceMaterial(
                        topSolidY, seaLevel, worldX, worldZ,
                        ctx.hydro[colIndex], ctx.macroPkg[colIndex]
                    );

                    if (riverCarved) {
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

    /**
     * 在 (worldX, worldZ) 附近对 bankIntensity 做一个简单的空间平滑，
     * 以减弱宏包交界处的硬切感。
     *
     * 做法：
     *   - 以当前格子为中心，在一个固定采样半径内（例如 16 或 24 blocks），
     *     采样若干个点的宏包，并取它们的 riverBank().bankIntensity()；
     *   - 使用距离权重（越近权重越大）做加权平均；
     *   - 若周围都没有有效宏包，则回退到一个中性值 0.5。
     */
    private double sampleSmoothedBankIntensity(int worldX, int worldZ) {
        final int SAMPLE_STEP = 1;
        final int SAMPLE_RADIUS = 6;

        double weightedSum = 0.0;
        double weightSum = 0.0;

        for (int dz = -SAMPLE_RADIUS; dz <= SAMPLE_RADIUS; dz += SAMPLE_STEP) {
            for (int dx = -SAMPLE_RADIUS; dx <= SAMPLE_RADIUS; dx += SAMPLE_STEP) {
                int sx = worldX + dx;
                int sz = worldZ + dz;

                MacroPackageId macroId = TalosMacroClimate.getMacroPackageId(
                    sx, sz, worldSeedInt
                );
                if (macroId == MacroPackageId.OCEANIC) {
                    continue;
                }

                double k = TalosRiverTerrainModifier.bankIntensityFor(macroId);

                double distSq = (double) dx * dx + (double) dz * dz;
                double w = 1.0 / (1.0 + distSq * 0.01);

                weightedSum += k * w;
                weightSum   += w;
            }
        }

        if (weightSum <= 0.0) {
            return 0.5;
        }

        return weightedSum / weightSum;
    }

}
