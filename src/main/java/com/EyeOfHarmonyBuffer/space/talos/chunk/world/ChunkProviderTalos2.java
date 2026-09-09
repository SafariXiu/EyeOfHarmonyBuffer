package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.Config.TalosConfig.V2TerrainConfigSection;
import com.EyeOfHarmonyBuffer.space.talos.BiomeDecoratorTalos2;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosBiomes;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceProfile;
import com.EyeOfHarmonyBuffer.space.talos.biome.TalosSurfaceRegistry;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.OrographyField;
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

    /** V2 地形轨（X1 阶段2，V2TerrainConfigSection.terrainV2Enabled；构造时固定一次）。 */
    private final boolean v2Track;

    public ChunkProviderTalos2(World world, long seed, boolean flag) {
        super(world, seed, flag);
        this.world = world;
        this.worldSeedInt = TalosLandMask.getWorldSeedInt(world);
        this.worldHeight = world.getActualHeight();
        this.v2Track = V2TerrainConfigSection.terrainV2Enabled;
    }

    // ================= 岩性场缓存（性能关键） =================
    //
    // 岩性变体 rockVariant3D 是 3D fBm（每石头方块一次，实测 ~59ns/方块），
    // 每列 ~95 个石头方块 → 每区块约 1.4ms，是全流程最大热点。
    // 该场的特征尺度是 240 格，因此每区块只按 8/8/16 格采样一次（3×3×17 = 153 次），
    // 其余方块三线性插值 → 每区块约 9µs（150× 减少），阈值判定仍在逐方块进行。

    private static final int RF_XZ_STEP = 8;
    private static final int RF_Y_STEP = 16;
    private static final int RF_NXZ = 3;
    private static final int RF_NY = 17;   // 0,16,...,256

    /** 岩性对（懒初始化：EFR 方块可能晚于本类加载）。 */
    private static volatile BlockMetaPair[] rockPairs;

    private static BlockMetaPair[] rockPairs() {
        BlockMetaPair[] p = rockPairs;
        if (p == null) {
            p = new BlockMetaPair[] {
                new BlockMetaPair(GregTechAPI.sBlockStones, (byte) 8),      // 0 玄武岩
                new BlockMetaPair(GregTechAPI.sBlockGranites, (byte) 0),    // 1 黑花岗岩
                new BlockMetaPair(GregTechAPI.sBlockGranites, (byte) 8),    // 2 红花岗岩
                new BlockMetaPair(GregTechAPI.sBlockStones, (byte) 0),      // 3 大理石
                efrRockPair(ModBlocks.DEEPSLATE),                           // 4 深板岩
                efrRockPair(ModBlocks.TUFF),                                // 5 凝灰岩
            };
            rockPairs = p;
        }
        return p;
    }

    /**
     * 每区块的岩性 3D 场（线程本地复用）。
     *
     * 两级缓存：
     *   1. 区块级 3×3×17 的 fBm 网格（每区块 153 次采样）；
     *   2. **逐列**把 y=0..maxY 的岩性一次算好存进 colVar（17 次 x/z 插值 + 逐格 y 插值 + 阈值），
     *      方块查询退化成一次数组读（~1ns）。
     */
    private static final class RockField {
        final double[] v = new double[RF_NXZ * RF_NXZ * RF_NY];
        final double[] node = new double[RF_NY];
        final byte[] colVar = new byte[256];
        int ox, oz;
        BlockMetaPair[] pairs;

        void build(int originX, int originZ, int worldSeed) {
            ox = originX;
            oz = originZ;
            pairs = rockPairs();
            for (int ix = 0; ix < RF_NXZ; ix++) {
                int wx = originX + ix * RF_XZ_STEP;
                for (int iz = 0; iz < RF_NXZ; iz++) {
                    int wz = originZ + iz * RF_XZ_STEP;
                    int base = (ix * RF_NXZ + iz) * RF_NY;
                    for (int iy = 0; iy < RF_NY; iy++) {
                        v[base + iy] = CaveMath.rockValue3D(wx, iy * RF_Y_STEP, wz, worldSeed);
                    }
                }
            }
        }

        /** 为当前列预计算 y=0..maxY 的岩性（每个区块每列一次）。 */
        void column(int wx, int wz, int maxY) {
            double fx = (wx - ox) / (double) RF_XZ_STEP;
            double fz = (wz - oz) / (double) RF_XZ_STEP;
            int ix = (int) fx, iz = (int) fz;
            if (ix < 0) ix = 0;
            if (iz < 0) iz = 0;
            if (ix > RF_NXZ - 2) ix = RF_NXZ - 2;
            if (iz > RF_NXZ - 2) iz = RF_NXZ - 2;
            double tx = fx - ix, tz = fz - iz;
            int b00 = (ix * RF_NXZ + iz) * RF_NY;
            int b10 = ((ix + 1) * RF_NXZ + iz) * RF_NY;
            int b01 = (ix * RF_NXZ + iz + 1) * RF_NY;
            int b11 = ((ix + 1) * RF_NXZ + iz + 1) * RF_NY;
            for (int iy = 0; iy < RF_NY; iy++) {
                double c00 = v[b00 + iy] + (v[b10 + iy] - v[b00 + iy]) * tx;
                double c01 = v[b01 + iy] + (v[b11 + iy] - v[b01 + iy]) * tx;
                node[iy] = c00 + (c01 - c00) * tz;
            }
            int top = maxY < 255 ? maxY : 255;
            for (int y = 0; y <= top; y++) {
                int iy = y / RF_Y_STEP;
                if (iy > RF_NY - 2) {
                    iy = RF_NY - 2;
                }
                double ty = (y - iy * RF_Y_STEP) / (double) RF_Y_STEP;
                double n = node[iy] + (node[iy + 1] - node[iy]) * ty;
                colVar[y] = CaveMath.rockVariantFromValue(n, y);
            }
        }

        /** 当前列的方块岩性（需先调用 {@link #column}）。 */
        BlockMetaPair pairAt(int wy) {
            return pairs[colVar[wy & 255] & 7];
        }
    }

    private static final ThreadLocal<RockField> RF = new ThreadLocal<RockField>() {
        @Override
        protected RockField initialValue() {
            return new RockField();
        }
    };

    /** base/plain 复用暂存（线程本地，避免逐列分配）。 */
    private static final ThreadLocal<double[]> BP = new ThreadLocal<double[]>() {
        @Override
        protected double[] initialValue() {
            return new double[2];
        }
    };

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

        if (v2Track) {
            // X1 阶段2：V2 轨 —— 新 L1 海陆 + L1b 骨架直接出方块（无宏包/河网/DLA/洞穴门控）
            generateTerrainV2(chunkX, chunkZ, blocks, meta);
            return;
        }

        TalosChunkContext ctx = TalosChunkContext.create(
            chunkX, chunkZ, worldSeedInt, getWaterLevel(), worldHeight
        );

        generateTerrainWithBaseHeightSimple(ctx, blocks, meta);
    }

    /**
     * V2 轨方块铺设（integration-worklist T1.3）：每列一次 OrographyField.sample →
     * 高度映射（V2TerrainGen，D30）→ 按 isLand 铺方块。
     * 海洋：海床由海残差深度映射；陆地：基岩 + 岩性变体 + 表层（群系 profile / 沙滩 / 雪）。
     * 无河网/湖（T3.4）、无洞穴（T3.2）、水面 = 海平面（T3.1 水场 v1）。
     */
    private void generateTerrainV2(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        final int seaLevel = getWaterLevel();
        final int worldX0 = chunkX * CHUNK_SIZE;
        final int worldZ0 = chunkZ * CHUNK_SIZE;
        final int seed = worldSeedInt;

        // 岩性 3D 场：每区块建一次（3×3×17 采样），逐方块三线性插值
        RockField rf = RF.get();
        rf.build(worldX0, worldZ0, seed);

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                final int worldX = worldX0 + localX;
                final int worldZ = worldZ0 + localZ;

                blocks[getIndex(localX, 0, localZ)] = Blocks.bedrock;
                meta[getIndex(localX, 0, localZ)] = 0;

                OrographyField.OroSample oro = OrographyField.sample(worldX, worldZ, seed);
                if (oro.isLand) {
                    fillLandColumnV2(blocks, meta, localX, localZ, worldX, worldZ, seed, seaLevel, oro, rf);
                } else {
                    fillSeaColumnV2(blocks, meta, localX, localZ, worldX, worldZ, seed, seaLevel, rf);
                }
            }
        }
    }

    /** V2 轨陆地列：高度 = V2TerrainGen 映射（单次成型，无宏包/河网子级）。 */
    private void fillLandColumnV2(Block[] blocks, byte[] meta,
                                  int localX, int localZ, int worldX, int worldZ,
                                  int seed, int seaLevel, OrographyField.OroSample oro,
                                  RockField rf) {
        // V2 高度链（D34/D38）：群系场（L1c）给出高度倾向 → 基础地形分解（plain + mtnComp）
        //   与 V2 山层按权威权重 w 仲裁
        //   h = plain + (1-w)*mtnComp + w*uplift   （w=1 山层全权接管，w=0 基础地形原样）
        // 群系 LUT 查询（1km 网格 + 平滑）：无分配、~20ns/列（本列已知是陆地 → 直接取陆地口径）
        V2BiomeField.Sample bs = V2BiomeField.sample(worldX, worldZ, seed, true);
        final double bBias = bs.bias, bScale = bs.scale;
        final V2BiomeSelect.Kind bKind = bs.kind;
        // base + plain 一次算（共享三层噪声）
        double[] bp = BP.get();
        V2TerrainGen.baseAndPlain(worldX, worldZ, seed, seaLevel, oro, bBias, bScale, bp);
        double base = bp[0];
        double plain = bp[1];
        double mtnComp = base > plain ? base - plain : 0.0;
        double w = MountainLayerV2.auth(worldX, worldZ, seed);
        double up = MountainLayerV2.uplift(worldX, worldZ, seed);
        double hD = plain + (1.0 - w) * mtnComp + w * up;
        // 块级细节：强度随山体强度，并按山层坡度调制（陡坡更多细节）
        double mtnAmt = Math.max(w, Math.min(1.0, mtnComp / 90.0));
        double slope01 = MountainLayerV2.slope01(worldX, worldZ, seed);
        hD += V2TerrainGen.mountainDetail(worldX, worldZ, seed,
            Math.min(1.0, mtnAmt * (1.00 + 0.80 * slope01)));
        // 软封顶（H=252, k=6）：接近世界高度上限时平滑压缩，不硬截出平台
        double H = 252.0, kk = 6.0;
        if (hD > H - 6.0 * kk) {
            hD = H - kk * Math.log1p(Math.exp((H - hD) / kk));
        }
        int h = (int) Math.round(hD);
        if (h < 1) {
            h = 1;
        } else if (h > worldHeight - 2) {
            h = worldHeight - 2;
        }

        final boolean snow = hD >= V2TerrainGen.snowLineY(worldZ);
        final boolean beach = !snow && V2TerrainGen.isBeachLand(oro, hD, seaLevel);

        BlockMetaPair surface;
        int surfaceDepth;
        BlockMetaPair filler;
        int fillerDepth;
        if (snow) {
            // 雪线以上：陡坡露岩、缓坡积雪（雪只挂在缓坡上 → 山体有纹理与明暗，不再一片白平）
            boolean steep = columnSlope(worldX, worldZ, seed, seaLevel) > 0.62;
            surface = steep
                ? new BlockMetaPair(Blocks.stone, (byte) 0)
                : new BlockMetaPair(Blocks.snow, (byte) 0);
            surfaceDepth = 1;
            filler = new BlockMetaPair(Blocks.stone, (byte) 0);
            fillerDepth = 3;
        } else if (beach) {
            surface = new BlockMetaPair(Blocks.sand, (byte) 0);
            surfaceDepth = 1;
            filler = new BlockMetaPair(Blocks.sand, (byte) 0);
            fillerDepth = 2;
        } else {
            TalosSurfaceProfile profile = TalosSurfaceRegistry.get(V2BiomePicker.biomeOf(bKind));
            surface = profile.surfaceBlock;
            surfaceDepth = profile.surfaceDepth;
            filler = profile.fillerBlock;
            fillerDepth = profile.fillerDepth;
        }

        final int surfaceStart = h - surfaceDepth + 1;
        final int fillerStart = surfaceStart - fillerDepth;

        // 逐列预计算岩性（y=1..h）→ 方块循环里只剩数组读
        rf.column(worldX, worldZ, h);

        for (int y = 1; y < h; y++) {
            BlockMetaPair pair;
            if (y < fillerStart) {
                pair = rf.pairAt(y);                        // 深层恒为石头 → 岩性变体
            } else if (y < surfaceStart) {
                pair = (filler.getBlock() == Blocks.stone && filler.getMetadata() == 0)
                    ? rf.pairAt(y) : filler;
            } else {
                pair = surface;
            }
            putBlock(blocks, meta, localX, y, localZ, pair);
        }
        putBlock(blocks, meta, localX, h, localZ, surface);

        // V2 水场 v1：陆地默认无水（无湖/河授权），水面只出现在海洋列（T3.1 后续接 basinMask）。
    }

    /** 列坡度估计（blocks/block）：用 ±4 格的合成高度差。仅雪线附近列调用（成本可控）。 */
    private double columnSlope(int worldX, int worldZ, int seed, int seaLevel) {
        final int d = 4;
        double hx = columnHeight(worldX + d, worldZ, seed, seaLevel)
            - columnHeight(worldX - d, worldZ, seed, seaLevel);
        double hz = columnHeight(worldX, worldZ + d, seed, seaLevel)
            - columnHeight(worldX, worldZ - d, seed, seaLevel);
        return Math.sqrt(hx * hx + hz * hz) / (2.0 * d);
    }

    /** 单列合成高度（不含雪/岩分支，供坡度估计复用同一口径）。 */
    private double columnHeight(int x, int z, int seed, int seaLevel) {
        OrographyField.OroSample o = OrographyField.sample(x, z, seed);
        if (!o.isLand) {
            return seaLevel - V2TerrainGen.seaDepthBlocks(x, z, seed);
        }
        V2BiomeField.Sample bs = V2BiomeField.sample(x, z, seed, true);
        double[] bp = BP.get();
        V2TerrainGen.baseAndPlain(x, z, seed, seaLevel, o, bs.bias, bs.scale, bp);
        double base = bp[0];
        double plain = bp[1];
        double mtnComp = base > plain ? base - plain : 0.0;
        double w = MountainLayerV2.auth(x, z, seed);
        double up = MountainLayerV2.uplift(x, z, seed);
        double h = plain + (1.0 - w) * mtnComp + w * up;
        double amt = Math.max(w, Math.min(1.0, mtnComp / 90.0));
        h += V2TerrainGen.mountainDetail(x, z, seed,
            Math.min(1.0, amt * (1.00 + 0.80 * MountainLayerV2.slope01(x, z, seed))));
        return h;
    }

    /** V2 轨海洋列：海床深度 = 海残差映射；浅海沙/砂砾底，深海直接岩性变体；水面 = 海平面。 */
    private void fillSeaColumnV2(Block[] blocks, byte[] meta,
                                 int localX, int localZ, int worldX, int worldZ,
                                 int seed, int seaLevel, RockField rf) {
        double depth = V2TerrainGen.seaDepthBlocks(worldX, worldZ, seed);
        int seabed = seaLevel - (int) Math.round(depth);
        if (seabed < 1) {
            seabed = 1;
        }
        if (seabed > seaLevel - 1) {
            seabed = seaLevel - 1;
        }

        rf.column(worldX, worldZ, seabed);

        // 海底表层：浅沙 → 砂砾 → 岩石变体（深层其余由岩性场铺）
        for (int y = 1; y <= seabed; y++) {
            BlockMetaPair pair = null;
            if (y == seabed) {
                if (depth <= V2TerrainGen.SAND_SEA_DEPTH) {
                    pair = SEAFLOOR_SAND;
                } else if (depth <= V2TerrainGen.GRAVEL_SEA_DEPTH) {
                    pair = SEAFLOOR_GRAVEL;
                }
            } else if (y == seabed - 1 && depth <= V2TerrainGen.SAND_SEA_DEPTH) {
                pair = SEAFLOOR_SAND;
            }
            if (pair != null) {
                putBlock(blocks, meta, localX, y, localZ, pair);
            } else {
                BlockMetaPair rp = rf.pairAt(y);
                int idx = getIndex(localX, y, localZ);
                blocks[idx] = rp.getBlock();
                meta[idx] = rp.getMetadata();
            }
        }

        for (int y = seabed + 1; y <= seaLevel; y++) {
            int idx = getIndex(localX, y, localZ);
            blocks[idx] = Blocks.water;
            meta[idx] = 0;
        }
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
