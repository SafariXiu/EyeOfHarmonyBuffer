package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.BiomeDecoratorTalos2;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.MacroPackageId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.MacroPackageRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverCarver;
import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverTerrainModifier;
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
    private final int worldHeight;

    private final int worldSeedInt;

    private final TalosRiverCarver.MacroPackageResolver macroResolver;

    private static final boolean DEBUG_COASTLINE = true;
    private static final boolean USE_CHUNK_BLUR_BANK = true;

    private final EnumMap<MacroPackageId, Double> bankIntensityCache =
        new EnumMap<>(MacroPackageId.class);

    public ChunkProviderTalos2(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.worldSeedInt = TalosLandMask.getWorldSeedInt(world);
        this.macroResolver = createMacroResolver();
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

        generateTerrainWithBaseHeightSimple(chunkX, chunkZ, blocks, meta);

        //generateDebugRivers(chunkX, chunkZ, blocks, meta, worldSeedInt);

        TalosRiverCarver.carveChunkRivers(
            chunkX, chunkZ,
            worldSeedInt,
            blocks, meta,
            getWaterLevel(),
            worldHeight,
            macroResolver
        );
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
     *   - 本方法只负责“标高 + 基础方块”的铺设；具体的河槽切割、源头湖形状、
     *     暗河井等细节由 TalosRiverCarver.carveChunkRivers(...) 在后续单独处理。
     *   - 河岸压低逻辑仅依赖世界坐标 (worldX, worldZ)、世界种子 int 和 seaLevel，
     *     不在这里直接操作方块数组，保证高度场是可重现、与方块填充解耦的。
     */
    private void generateTerrainWithBaseHeightSimple(int chunkX, int chunkZ,
                                                     Block[] blocks, byte[] meta) {
        final int seaLevel = getWaterLevel();

        final int worldX0 = chunkX * CHUNK_SIZE;
        final int worldZ0 = chunkZ * CHUNK_SIZE;

        // ① 每个 chunk 生成一次 16×16 的 LandMask16
        final LandMask16 landMask = TalosLandMask.getLandMaskForChunk(chunkX, chunkZ, worldSeedInt);

        double[][] blurredBank = null;
        if (USE_CHUNK_BLUR_BANK) {
            blurredBank = new double[CHUNK_SIZE][CHUNK_SIZE];
            computeBlurredBankForChunk(chunkX, chunkZ, blurredBank);
        }

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                final int worldX = worldX0 + localX;
                final int worldZ = worldZ0 + localZ;

                final boolean isLandFromMask =
                    (landMask != null && landMask.get(localX, localZ));

                TalosLandMask.Sample landSample =
                    TalosLandMask.sampleFull(worldX, worldZ, worldSeedInt);

                final boolean isLand = isLandFromMask;
                final double coastWeight =
                    (landSample != null ? landSample.coastWeight : 0.0);
                final double shelfWeight =
                    (landSample != null ? landSample.shelfWeight : 0.0);

                // === 基础高度场 + 河岸 + 海岸线 ===

                double baseHeightD = TalosBaseTerrain.sampleBaseHeight(
                    worldX, worldZ, worldSeedInt, seaLevel
                );

                double bankIntensity;
                if (USE_CHUNK_BLUR_BANK && blurredBank != null) {
                    bankIntensity = blurredBank[localX][localZ];
                } else {
                    bankIntensity = sampleSmoothedBankIntensity(worldX, worldZ);
                }

                MacroPackageRegistry.RiverBankPreset bankPreset =
                    new MacroPackageRegistry.RiverBankPreset(bankIntensity);

                double riverShapedHeightD = TalosRiverTerrainModifier.applyRiverBankShaping(
                    worldX, worldZ,
                    worldSeedInt,
                    baseHeightD,
                    seaLevel,
                    bankPreset,
                    isLand
                );

                double coastShapedHeightD = TalosCoastlineShaper.applyCoastlineShaping(
                    riverShapedHeightD,
                    seaLevel,
                    isLand,
                    coastWeight
                );

                int h = (int) Math.round(coastShapedHeightD);
                if (h < 1) {
                    h = 1;
                } else if (h > worldHeight - 2) {
                    h = worldHeight - 2;
                }

                // === 方块填充 ===

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
                    int seabedY = TalosSeafloorShaper.computeSeabedY(
                        seaLevel,
                        false,              // isLand = false
                        shelfWeight,
                        coastShapedHeightD, // 传入河流+海岸线后的高度场
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

    private TalosRiverCarver.MacroPackageResolver createMacroResolver() {
        return new TalosRiverCarver.MacroPackageResolver() {
            @Override
            public MacroPackageId resolveMacroPackageId(int worldX, int worldZ) {
                MacroPackageId pkgId = TalosMacroClimate.getMacroPackageId(worldX, worldZ, worldSeedInt);

                if (pkgId == MacroPackageId.OCEANIC) {
                    return null;
                }

                return pkgId;
            }
        };
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

                MacroPackageId macroId = macroResolver.resolveMacroPackageId(sx, sz);
                if (macroId == null) {
                    continue;
                }

                double k = getBankIntensityForMacro(macroId);

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

    /**
     * 为当前 chunk 计算平滑后的 bankIntensity 图（带 halo，保证跨 chunk 连续）。
     *
     * 思路：
     *   - 在 chunk 四周各扩一圈半径 R 的「halo」，在这个 (16+2R) x (16+2R) 网格上采样原始 bankIntensity；
     *   - 在扩展网格上做一次 X 向 box blur，再做一次 Z 向 box blur；
     *   - 最后只把中间的 16x16（对应当前 chunk 的区域）拷贝到 outBlurred[localX][localZ]。
     *
     * 这样：
     *   - world 坐标相同的格子，在任何 chunk 中计算出来的模糊值都是一样的；
     *   - 邻接 chunk 的公共边界不会出现 bankIntensity 的硬切。
     */
    private void computeBlurredBankForChunk(int chunkX, int chunkZ,
                                            double[][] outBlurred) {
        final int R = 2;

        final int EXT_SIZE = CHUNK_SIZE + 2 * R;

        double[][] rawExt = new double[EXT_SIZE][EXT_SIZE];

        for (int extZ = 0; extZ < EXT_SIZE; extZ++) {
            int worldZ = chunkZ * CHUNK_SIZE + (extZ - R);
            for (int extX = 0; extX < EXT_SIZE; extX++) {
                int worldX = chunkX * CHUNK_SIZE + (extX - R);

                MacroPackageId macroId = macroResolver.resolveMacroPackageId(worldX, worldZ);
                double k = getBankIntensityForMacro(macroId);

                rawExt[extX][extZ] = k;
            }
        }

        double[][] tmpExt = new double[EXT_SIZE][EXT_SIZE];

        for (int z = 0; z < EXT_SIZE; z++) {
            for (int x = 0; x < EXT_SIZE; x++) {
                double sum = 0.0;
                int count = 0;

                for (int dx = -R; dx <= R; dx++) {
                    int sx = x + dx;
                    if (sx < 0 || sx >= EXT_SIZE) continue;

                    sum += rawExt[sx][z];
                    count++;
                }

                tmpExt[x][z] = sum / count;
            }
        }

        double[][] blurExt = new double[EXT_SIZE][EXT_SIZE];

        for (int x = 0; x < EXT_SIZE; x++) {
            for (int z = 0; z < EXT_SIZE; z++) {
                double sum = 0.0;
                int count = 0;

                for (int dz = -R; dz <= R; dz++) {
                    int sz = z + dz;
                    if (sz < 0 || sz >= EXT_SIZE) continue;

                    sum += tmpExt[x][sz];
                    count++;
                }

                blurExt[x][z] = sum / count;
            }
        }

        for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
            int extZ = localZ + R;
            for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                int extX = localX + R;

                outBlurred[localX][localZ] = blurExt[extX][extZ];
            }
        }
    }

    /**
     * 从 MacroPackageRegistry 中获取某个宏包的 bankIntensity，并做缓存。
     * null 宏包或缺省配置时使用中性值 0.5。
     */
    private double getBankIntensityForMacro(MacroPackageId macroId) {
        if (macroId == null) {
            return 0.5;
        }

        Double cached = bankIntensityCache.get(macroId);
        if (cached != null) {
            return cached;
        }

        MacroPackageRegistry.MacroPackageSpec spec = MacroPackageRegistry.get(macroId);
        MacroPackageRegistry.RiverBankPreset bank = spec.riverBank();

        double k = (bank != null) ? bank.bankIntensity() : 0.5;
        if (k < 0.0) k = 0.0;
        if (k > 1.0) k = 1.0;

        bankIntensityCache.put(macroId, k);
        return k;
    }
}
