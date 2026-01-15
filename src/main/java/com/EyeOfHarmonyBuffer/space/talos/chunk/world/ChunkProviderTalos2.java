package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroBiomeSelector;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectionResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectorConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.context.FieldContext;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.TalosMacroCellBuilder;
import com.EyeOfHarmonyBuffer.space.talos.chunk.noise.NoiseUtil;
import com.EyeOfHarmonyBuffer.space.talos.chunk.util.TalosBiomeResolver;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * ChunkProviderTalos2
 *
 * 职责：
 * - Talos 世界的区块生成器（ChunkProvider）
 * - 使用 MacroSelectionResult 生成高度图（height map），并根据生物群系填充方块（地表/填充/石头/水等）
 * - 执行额外的清理/修饰：海底硬化、海岸向下延展、bedrock 层等
 *
 * 关键方法：
 * - onChunkProvider：生成一个 chunk 的整体流程（清空 -> 缓存 -> 高度图 -> 填充 -> 清理 -> 基岩）
 * - computeBaseHeightMap：对 17x17 采样点计算地表高度（供后续插值/列填充）
 * - computeGroundHeightFromMacro：MacroSelectionResult -> 最终地表高度 Y（核心高度合成）
 *
 * computeGroundHeightFromMacro 的基本逻辑：
 * - base：取 worldBaseHeight 或 continuousWorldBaseHeight 作为基准
 * - macroAmp/microAmp：宏/微起伏幅度（world-space）
 * - macroNoise/microNoise：噪声采样，叠加到 base 上
 * - riverCarveDepth：河谷侵蚀下切
 * - 最终 clamp 到世界高度范围
 *
 * 本次改动（接入 MacroBiome Height Profile 三参数）：
 * - 使用 MacroSelectionResult 中的 baseHeightOffset：
 *   - 对宏噪声做 bias（macroNoise + offset），控制更偏“隆起/盆地”的风格
 *   - 计算 ampMul 倍增 macroAmp/microAmp，使地形起伏强度随 biome 风格变化
 * - 使用 absoluteMinY/absoluteMaxY：
 *   - 对最终 finalHeight（包含噪声与河流侵蚀后）做 相对高度 clamp（双保险）
 *
 * 设计要点：
 * - Selector 阶段 clamp 的是“基准 worldBaseHeight”；ChunkProvider 阶段仍可能叠加噪声/侵蚀越界
 * - 因此最终高度处的 absoluteMin/Max clamp 是确保配置“必然生效”的关键一步
 */
public class ChunkProviderTalos2 extends ChunkProviderSpaceLakes {

    private static final boolean DEBUG_SOFTEN = false;
    private static final boolean USE_CONTINUOUS_BASE_Y = true;

    private final World world;
    private final IMacroCellProvider macroCellBuilder;
    private final FieldDiagnostics diagnostics;
    private final MacroBiomeSelector macroSelector;

    private static final BlockMetaPair SNOW_SURFACE = new BlockMetaPair(Blocks.snow, (byte) 0);
    private static final BlockMetaPair PACKED_ICE = new BlockMetaPair(Blocks.packed_ice, (byte) 0);
    private static final BlockMetaPair SANDSTONE_FILL = new BlockMetaPair(Blocks.sandstone, (byte) 0);
    private static final BlockMetaPair MYCELIUM_TOP = new BlockMetaPair(Blocks.mycelium, (byte) 0);

    private static final Logger LOGGER = LogManager.getLogger("EyeOfHarmonyBuffer");

    private static final long MACRO_HEIGHT_NOISE_SALT = 0xCBF29CE484222325L;
    private static final long MICRO_HEIGHT_NOISE_SALT = 0x9E3779B185EBCA87L;

    private static final double MACRO_HEIGHT_NOISE_FREQUENCY = 1.0 / 480.0;
    private static final double MICRO_HEIGHT_NOISE_FREQUENCY = 1.0 / 64.0;

    private static long now() { return System.nanoTime(); }
    private static long ms(long nanos) { return nanos / 1_000_000L; }

    private static final boolean TALOS_TIMING = false;
    private static final int SLOW_CHUNK_MS = 80;
    private static final int VERY_SLOW_CHUNK_MS = 300;

    private final MacroSelectorConfig.HeightProfile heightProfile;
    private final int worldFloorY;
    private final int worldCeilingY;
    private final double terrainFloorY;
    private final double terrainCeilingY;
    private final double heightRange;
    private final int configuredSeaLevel;
    private static final long RIVER_WARP_SALT = 0xA3C59AC3F1E2D4B1L;
    private static final long RIVER_FIELD_SALT = 0x19D3B6C8E5A7F02DL;

    private static void logChunkTiming(int chunkX, int chunkZ,
                                       long tTotal,
                                       long tClear, long tCache,
                                       long tBase, long tClamp,
                                       long tFill, long tClean, long tBedrock) {
        long totalMs = ms(tTotal);
        if (!TALOS_TIMING || totalMs < SLOW_CHUNK_MS) return;

        String msg = "[Talos2] chunk=(" + chunkX + "," + chunkZ + ") total=" + totalMs + "ms"
            + " clear=" + ms(tClear)
            + " shoreCache=" + ms(tCache)
            + " baseHM=" + ms(tBase)
            + " clamp=" + ms(tClamp)
            + " fill=" + ms(tFill)
            + " clean=" + ms(tClean)
            + " bedrock=" + ms(tBedrock);

        System.out.println(msg);

        if (totalMs >= VERY_SLOW_CHUNK_MS) {
            System.out.println("[Talos2] VERY SLOW chunk stacktrace:");
            for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
                System.out.println("    at " + e);
            }
        }
    }

    public ChunkProviderTalos2(World world, long seed, boolean mapFeaturesEnabled) {
        super(world, seed, mapFeaturesEnabled);
        this.world = world;

        Talos2Hooks.HookData hook = Talos2Hooks.resolveOrCreate(world);

        this.macroCellBuilder = hook.macroCellBuilder();
        this.diagnostics = hook.diagnostics();

        FieldContext fieldContext = hook.fieldContext();
        this.macroSelector = hook.fieldContext().getMacroSelector();

        this.heightProfile = fieldContext.heightProfile();

        this.worldFloorY = heightProfile.worldFloorY();
        this.worldCeilingY = heightProfile.worldCeilingY();
        this.terrainFloorY = heightProfile.terrainFloorY();
        this.terrainCeilingY = heightProfile.terrainCeilingY();
        this.heightRange = heightProfile.terrainRange();
        this.configuredSeaLevel = (int) Math.round(heightProfile.seaLevelY());

        System.out.println("[Talos2] CP builder instance=" +
            System.identityHashCode(this.macroCellBuilder));
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
    protected BiomeGenBase[] getBiomesForGeneration() {
        return new BiomeGenBase[]{
            TalosBiomes.TALOS_OCEAN,
            TalosBiomes.TALOS_SHELF,
            TalosBiomes.TALOS_BEACH,
            TalosBiomes.TALOS_PLAINS
        };
    }

    @Override
    public void onChunkProvider(int chunkX, int chunkZ, Block[] blocks, byte[] meta) {
        final long t0 = now();

        long tClear = 0, tCache = 0, tBase = 0, tFill = 0, tClean = 0, tBed = 0;
        long tClamp = 0;

        long a = now();
        clearChunkBlocks(blocks, meta);
        tClear = now() - a;

        a = now();
        ChunkShoreCache shore = macroCellBuilder.build(chunkX, chunkZ);
        tCache = now() - a;

        ChunkShoreCache east = macroCellBuilder.peekCached(chunkX + 1, chunkZ);
        if (east != null) {
        }

        resolveBiomesForCells(chunkX, chunkZ, shore);

        a = now();
        int[][] hm = computeBaseHeightMap(chunkX, chunkZ, shore);
        tBase = now() - a;

        a = now();
        fillBlocksFromHeightMap(blocks, meta, hm, chunkX, chunkZ, shore);
        tFill = now() - a;

        a = now();
        strongCleanOceanFloor(blocks, meta);
        extendLandEdgesDown(blocks, meta);
        tClean = now() - a;

        a = now();
        applyBedrockFloor(blocks, meta, chunkX, chunkZ);
        tBed = now() - a;

        long tTotal = now() - t0;
        logChunkTiming(chunkX, chunkZ, tTotal, tClear, tCache, tBase, tClamp, tFill, tClean, tBed);

        logMacroCacheStats("chunk=" + chunkX + "," + chunkZ);
    }

    private void clearChunkBlocks(Block[] blocks, byte[] meta) {
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = null;
            meta[i] = 0;
        }
    }

    private int[][] computeBaseHeightMap(int chunkX, int chunkZ, ChunkShoreCache shore) {
        final int SIZE = 17;
        final int worldHeight = 256;
        final int waterLevel = this.getWaterLevel();

        final int fallbackLand = Math.min(worldCeilingY - 4, configuredSeaLevel + 5);
        final int fallbackSea = Math.max(worldFloorY + 4, configuredSeaLevel - 12);

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {
                ChunkShoreCache.MacroCell cell = shore.macroContext[localX][localZ];
                MacroSelectionResult macro = cell != null ? cell.macroResult : null;

                int worldX = chunkX * 16 + localX;
                int worldZ = chunkZ * 16 + localZ;

                int groundHeight;
                if (macro != null) {
                    groundHeight = computeGroundHeightFromMacro(macro, worldX, worldZ);
                } else {
                    groundHeight = (cell != null && cell.isLand) ? fallbackLand : fallbackSea;
                }

                heightMap[localX][localZ] = groundHeight;
                if (cell != null) {
                    cell.macroBaseHeight = (short) groundHeight;
                }
            }
        }
        return heightMap;
    }

    private void fillBlocksFromHeightMap(
        Block[] blocks,
        byte[] meta,
        int[][] heightMap,
        int chunkX,
        int chunkZ,
        ChunkShoreCache shore) {

        final int worldHeight = 256;
        final int CHUNK_SIZE = 16;

        int waterLevel = this.getWaterLevel();

        for (int localX = 0; localX < CHUNK_SIZE; localX++) {
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                int columnBase = (localX * 16 + localZ) * worldHeight;
                int groundHeight = heightMap[localX][localZ];

                ChunkShoreCache.MacroCell cell = shore.macroContext[localX][localZ];
                if (cell == null) {
                    continue;
                }

                BiomeGenBase baseBiome = cell.resolvedBiome;
                if (baseBiome == null) {
                    baseBiome = TalosBiomeResolver.resolve(cell.macroResult);
                    cell.resolvedBiome = baseBiome;
                }

                if (baseBiome == TalosBiomes.TALOS_OCEAN) {
                    fillOceanColumn(blocks, meta, columnBase, groundHeight, waterLevel);
                } else if (baseBiome == TalosBiomes.TALOS_SHELF) {
                    fillShelfColumn(blocks, meta, columnBase, groundHeight, waterLevel,
                        (BiomeGenTalos2Shelf) baseBiome);
                } else if (baseBiome == TalosBiomes.TALOS_BEACH) {
                    fillBeachColumn(blocks, meta, columnBase, groundHeight, waterLevel,
                        (BiomeGenTalos2Beach) baseBiome);
                } else {
                    buildColumnForBiome(blocks, meta, columnBase, groundHeight, waterLevel, baseBiome);
                }

                TalosBiomeDebugHooks.recordGeneratedBiome(chunkX, chunkZ, localX, localZ, baseBiome);
            }
        }
    }

    private void strongCleanOceanFloor(Block[] blocks, byte[] meta) {
        final int H = 256;
        final int SEA = this.getWaterLevel();
        BlockMetaPair stone = this.getStoneBlock();

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int base = (lx * 16 + lz) * H;

                boolean seenWaterAbove = false;

                for (int y = SEA; y >= 0; y--) {
                    int idx = base + y;
                    Block b = blocks[idx];

                    if (b == null || b == Blocks.air) continue;

                    if (b == Blocks.water) {
                        seenWaterAbove = true;
                        continue;
                    }

                    if (!seenWaterAbove) continue;

                    if (b == Blocks.grass || b == Blocks.dirt || b == Blocks.gravel) {
                        blocks[idx] = stone.getBlock();
                        meta[idx] = stone.getMetadata();
                    }
                }
            }
        }
    }

    private void extendLandEdgesDown(Block[] blocks, byte[] meta) {
        final int worldHeight = 256;
        final int waterLevel = this.getWaterLevel();

        BlockMetaPair stone = this.getStoneBlock();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {

                int columnBase = (localX * 16 + localZ) * worldHeight;

                int topSolidBelowWater = -1;
                for (int y = waterLevel; y >= 1; y--) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b != null && b != Blocks.air && b != Blocks.water) {
                        topSolidBelowWater = y;
                        break;
                    }
                }
                if (topSolidBelowWater < 0) continue;

                boolean isCoastColumn = false;
                for (int y = waterLevel + 1; y <= waterLevel + 6 && y < worldHeight; y++) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b == Blocks.grass || b == Blocks.dirt || b == Blocks.stone || b == Blocks.sand) {
                        isCoastColumn = true;
                        break;
                    }
                }
                if (!isCoastColumn) continue;

                int targetDepth = Math.max(4, topSolidBelowWater - 10);
                for (int y = topSolidBelowWater - 1; y >= targetDepth; y--) {
                    int idx = columnBase + y;
                    Block b = blocks[idx];
                    if (b == null || b == Blocks.air || b == Blocks.water) {
                        blocks[idx] = stone.getBlock();
                        meta[idx] = stone.getMetadata();
                    } else {
                        break;
                    }
                }
            }
        }
    }

    private void applyBedrockFloor(Block[] blocks, byte[] meta, int chunkX, int chunkZ) {
        final int H = 256;
        final int LAYERS = 5;

        java.util.Random r = new java.util.Random(
            (long)chunkX * 341873128712L + (long)chunkZ * 132897987541L + this.world.getSeed()
        );

        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int base = (lx * 16 + lz) * H;

                for (int y = 0; y < LAYERS; y++) {
                    if (y == 0 || r.nextInt(LAYERS) > y) {
                        blocks[base + y] = Blocks.bedrock;
                        meta[base + y] = 0;
                    }
                }
            }
        }
    }

    public static final class ChunkShoreCache {

        public static final int GRID_SIZE = TalosMacroCellBuilder.GRID_SIZE;

        public final boolean[][] isLand = new boolean[GRID_SIZE][GRID_SIZE];
        public final short[][] dist = new short[GRID_SIZE][GRID_SIZE];
        public final short[][] beachW = new short[GRID_SIZE][GRID_SIZE];
        public final short[][] shelfW = new short[GRID_SIZE][GRID_SIZE];
        public final boolean[][] baselineLocked = new boolean[GRID_SIZE][GRID_SIZE];

        public final MacroTag[][] macroPrimary = new MacroTag[GRID_SIZE][GRID_SIZE];
        public final MacroTag[][] macroSecondary = new MacroTag[GRID_SIZE][GRID_SIZE];
        public final float[][] macroBlend = new float[GRID_SIZE][GRID_SIZE];

        public final float[][] macroWet = new float[GRID_SIZE][GRID_SIZE];
        public final float[][] macroCold = new float[GRID_SIZE][GRID_SIZE];
        public final float[][] macroCoast = new float[GRID_SIZE][GRID_SIZE];

        public final float[][] macroPlateau = new float[GRID_SIZE][GRID_SIZE];
        public final byte[][] macroTier = new byte[GRID_SIZE][GRID_SIZE];
        public final short[][] macroPlateId = new short[GRID_SIZE][GRID_SIZE];

        public final short[][] macroBaseHeight = new short[GRID_SIZE][GRID_SIZE];

        public final MacroCell[][] macroContext = new MacroCell[GRID_SIZE][GRID_SIZE];

        public final byte[][] macroPatchVariant = new byte[GRID_SIZE][GRID_SIZE];
        public final boolean[][] macroPatchSingle = new boolean[GRID_SIZE][GRID_SIZE];
        public final float[][] macroPatchEdge = new float[GRID_SIZE][GRID_SIZE];

        public final float[][] anchorWeight = new float[GRID_SIZE][GRID_SIZE];
        public final float[][] hardEdge = new float[GRID_SIZE][GRID_SIZE];

        public final boolean[][] boundaryTouched = new boolean[GRID_SIZE][GRID_SIZE];

        public ChunkShoreCache() {
            for (int x = 0; x < GRID_SIZE; x++) {
                for (int z = 0; z < GRID_SIZE; z++) {
                    macroContext[x][z] = new MacroCell();
                }
            }
        }

        public static final class MacroCell {
            public MacroTag primary;
            public MacroTag secondary;
            public double blendPrimary;
            public byte tier;
            public short plateId;
            public float plateauAnchor;
            public boolean isLand;
            public short distToCoast;
            public short beachWidth;
            public short shelfWidth;
            public short macroBaseHeight;
            public byte patchVariant;
            public boolean patchSingleBiome;
            public double patchEdgeBlend;
            public float anchorWeight;
            public float hardEdge;
            public BiomeGenBase resolvedBiome;
            public MacroSelectionResult macroResult;
            public MacroTag macroTag;
            public int macroPatchId;
            public boolean macroRare;
            public double macroTemp;
            public double macroHumid;
            public double macroContinental;
            public double macroCoastWidth;
            public double macroShelfWidth;
            public MacroBiome.MacroBiomeVariant macroVariant;
        }
    }

    private void buildColumnForBiome(
        Block[] blocks,
        byte[] meta,
        int columnBase,
        int groundHeight,
        int waterLevel,
        BiomeGenBase biome
    ) {
        BlockMetaPair surface = getSurfaceBlock(biome);
        BlockMetaPair filler = getFillerBlock(biome);
        BlockMetaPair stone = getStoneBlockForBiome(biome);
        BlockMetaPair water = this.getWaterBlock();

        BlockMetaPair defaultSurface = surface != null ? surface : this.getGrassBlock();
        BlockMetaPair defaultFiller = filler != null ? filler : this.getDirtBlock();
        BlockMetaPair defaultStone = stone != null ? stone : this.getStoneBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            BlockMetaPair pair;

            if (y == groundHeight) {
                pair = defaultSurface;
            } else if (y >= groundHeight - 3) {
                pair = defaultFiller;
            } else {
                pair = defaultStone;
            }

            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private BlockMetaPair getSurfaceBlock(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs) {
            Block top = gs.topBlock != null ? gs.topBlock : Blocks.grass;
            byte meta = gs.topMeta;
            return new BlockMetaPair(top, meta);
        }
        return this.getGrassBlock();
    }

    private BlockMetaPair getFillerBlock(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs) {
            Block filler = gs.fillerBlock != null ? gs.fillerBlock : Blocks.dirt;
            byte meta = gs.fillerMeta;
            return new BlockMetaPair(filler, meta);
        }
        return this.getDirtBlock();
    }

    private BlockMetaPair getStoneBlockForBiome(BiomeGenBase biome) {
        if (biome instanceof GSBiomeGenBase gs && gs.stoneBlock != null) {
            return new BlockMetaPair(gs.stoneBlock, gs.stoneMeta);
        }
        return this.getStoneBlock();
    }

    private void fillOceanColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel) {

        BiomeGenTalos2Ocean biome = TalosBiomes.TALOS_OCEAN;
        BlockMetaPair stone = biome.bottomBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            blocks[idx] = stone.getBlock();
            meta[idx] = stone.getMetadata();
        }
        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private void fillShelfColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel,
                                 BiomeGenTalos2Shelf biome) {

        BlockMetaPair top = biome.surfaceBlock;
        BlockMetaPair shelf = biome.shelfBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            BlockMetaPair pair = (y == groundHeight) ? top : shelf;
            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private void fillBeachColumn(Block[] blocks,
                                 byte[] meta,
                                 int columnBase,
                                 int groundHeight,
                                 int waterLevel,
                                 BiomeGenTalos2Beach biome) {

        BlockMetaPair sand = biome.surfaceBlock;
        BlockMetaPair dirt = biome.fillerBlock;
        BlockMetaPair stone = biome.stoneBlock;
        BlockMetaPair water = this.getWaterBlock();

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;

            BlockMetaPair pair;
            if (y == groundHeight || y == groundHeight - 1) {
                pair = sand;
            } else if (y >= groundHeight - 4) {
                pair = dirt;
            } else {
                pair = stone;
            }

            blocks[idx] = pair.getBlock();
            meta[idx] = pair.getMetadata();
        }

        if (this.canGenerateWaterBlock() && groundHeight < waterLevel - 2) {
            for (int y = groundHeight + 1; y <= waterLevel; y++) {
                int idx = columnBase + y;
                blocks[idx] = water.getBlock();
                meta[idx] = water.getMetadata();
            }
        }
    }

    private void resolveBiomesForCells(int chunkX, int chunkZ, ChunkShoreCache shore) {
        final int GRID = ChunkShoreCache.GRID_SIZE - 1;

        for (int localX = 0; localX <= GRID; localX++) {
            for (int localZ = 0; localZ <= GRID; localZ++) {
                ChunkShoreCache.MacroCell cell = shore.macroContext[localX][localZ];
                if (cell == null) continue;

                MacroSelectionResult macro = cell.macroResult;
                if (macro == null) {
                    int gx = chunkX * 16 + localX;
                    int gz = chunkZ * 16 + localZ;
                    macro = macroSelector.select(gx, gz);
                    cell.macroResult = macro;
                }

                cell.resolvedBiome = TalosBiomeResolver.resolve(cell.macroResult);
            }
        }
    }

    private double valleyMask01(int worldX, int worldZ) {
        double freq = 1.0 / 1800.0;

        double warpFreq = 1.0 / 900.0;
        double wx = n2(NoiseUtil.fractal(world.getSeed(), RIVER_WARP_SALT, worldX, worldZ, warpFreq, 2, 2.0, 0.5)) * 120.0;
        double wz = n2(NoiseUtil.fractal(world.getSeed(), RIVER_WARP_SALT ^ 0xB4L, worldX, worldZ, warpFreq, 2, 2.0, 0.5)) * 120.0;

        double nx = worldX + wx;
        double nz = worldZ + wz;

        double f = NoiseUtil.fractal(world.getSeed(), RIVER_FIELD_SALT, nx, nz, freq, 3, 2.0, 0.5);

        double d = Math.abs(f - 0.5);

        double riverHalfWidth = 0.07;
        double m = 1.0 - smoothstep(0.0, riverHalfWidth, d);

        return m;
    }

    private double riverCarveDepth(int worldX, int worldZ, MacroSelectionResult macro, double currentHeight) {
        double mask = valleyMask01(worldX, worldZ);

        double maxDepth = 42.0;

        double sea = this.configuredSeaLevel;

        double base = USE_CONTINUOUS_BASE_Y
            ? macro.continuousWorldBaseHeight()
            : macro.worldBaseHeight();

        double baseGate = smoothstep(sea + 2.0, sea + 18.0, base);

        double heightGate = smoothstep(sea - 8.0, sea + 10.0, currentHeight);
        //double heightGate = 1;

        return mask * maxDepth * baseGate * heightGate;
    }

    private void logMacroCacheStats(String reason) {
        if (diagnostics == null) return;

        FieldDiagnostics.MacroCacheSnapshot snapshot = diagnostics.snapshot().macroCache();
        LOGGER.info(
            "[MacroCache][{}] hits={} misses={} hitRate={} evictions={}",
            reason,
            snapshot.getHits(),
            snapshot.getMisses(),
            snapshot.hitRate(),
            snapshot.getEvictions()
        );
    }

    private int computeGroundHeightFromMacro(MacroSelectionResult macro,
                                             int worldX,
                                             int worldZ) {

        double base = USE_CONTINUOUS_BASE_Y
            ? macro.continuousWorldBaseHeight()
            : macro.worldBaseHeight();

        double macroAmp = macro.worldMacroVariance();
        double microAmp = macro.worldMicroVariance();

        double baseHeightOffset = macro.baseHeightOffset();
        double absMinY = macro.absoluteMinY();
        double absMaxY = macro.absoluteMaxY();

        double baseMin = worldFloorY;
        double baseMax = worldCeilingY;

        if (Double.isFinite(absMinY)) baseMin = Math.max(baseMin, absMinY);
        if (Double.isFinite(absMaxY)) baseMax = Math.min(baseMax, absMaxY);
        if (baseMin > baseMax) { double t = baseMin; baseMin = baseMax; baseMax = t; }

        base = MathHelper.clamp_double(base, baseMin, baseMax);

        double offClamped = MathHelper.clamp_double(baseHeightOffset, -1.5d, 1.5d);
        double ampMul = 1.0d + 0.35d * offClamped;
        ampMul = MathHelper.clamp_double(ampMul, 0.55d, 1.65d);

        macroAmp *= ampMul;
        microAmp *= ampMul;

        macroAmp = MathHelper.clamp_double(macroAmp, 0.0D, heightRange);
        microAmp = MathHelper.clamp_double(microAmp, 0.0D, heightRange);

        if (USE_CONTINUOUS_BASE_Y) {
            double detail = MathHelper.clamp_double(macro.continuousDetailAmpY(), 0.0d, heightRange);
            microAmp = MathHelper.clamp_double(microAmp * 0.5d + detail * 0.5d, 0.0d, heightRange);
        }

        double macroNoise = NoiseUtil.fractal(
            this.world.getSeed(),
            MACRO_HEIGHT_NOISE_SALT,
            worldX,
            worldZ,
            MACRO_HEIGHT_NOISE_FREQUENCY,
            3,
            2.0D,
            0.5D
        ) * 2.0D - 1.0D;

        double biasedMacroNoise = MathHelper.clamp_double(macroNoise + baseHeightOffset, -1.0d, 1.0d);

        double microNoise = NoiseUtil.fractal(
            this.world.getSeed(),
            MICRO_HEIGHT_NOISE_SALT,
            worldX,
            worldZ,
            MICRO_HEIGHT_NOISE_FREQUENCY,
            2,
            2.0D,
            0.65D
        ) * 2.0D - 1.0D;

        double finalHeight = base
            + biasedMacroNoise * macroAmp
            + microNoise * microAmp;

        finalHeight -= riverCarveDepth(worldX, worldZ, macro, finalHeight);

        if (Double.isFinite(absMinY) || Double.isFinite(absMaxY)) {
            double minY = absMinY;
            double maxY = absMaxY;
            if (Double.isFinite(minY) && Double.isFinite(maxY) && minY > maxY) {
                double t = minY; minY = maxY; maxY = t;
            }

            final double SOFT_BAND_Y = 24.0d;
            finalHeight = softClamp(finalHeight, minY, maxY, SOFT_BAND_Y);
        }

        int clamped = (int) MathHelper.clamp_double(
            finalHeight,
            worldFloorY + 1,
            worldCeilingY - 1
        );
        return clamped;
    }

    private static double saturate(double x) {
        return x < 0 ? 0 : (x > 1 ? 1 : x);
    }

    private static double smoothstep(double e0, double e1, double x) {
        double t = saturate((x - e0) / (e1 - e0));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double smoothstep01(double t) {
        t = t < 0 ? 0 : (t > 1 ? 1 : t);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double softClamp(double y, double minY, double maxY, double bandY) {
        if (!(Double.isFinite(minY) || Double.isFinite(maxY))) return y;

        if (Double.isFinite(minY) && Double.isFinite(maxY) && minY > maxY) {
            double t = minY; minY = maxY; maxY = t;
        }
        bandY = Math.max(0.0d, bandY);

        if (bandY <= 1.0e-9) {
            return MathHelper.clamp_double(y, minY, maxY);
        }

        if (Double.isFinite(maxY) && y > maxY) {
            double d = y - maxY;
            double t = d / bandY;
            double s = smoothstep01(t);
            return maxY + bandY * (1.0d - (1.0d - s) * (1.0d - t));
        }

        if (Double.isFinite(minY) && y < minY) {
            double d = minY - y;
            double t = d / bandY;
            double s = smoothstep01(t);
            return minY - bandY * (1.0d - (1.0d - s) * (1.0d - t));
        }

        return y;
    }

    private static double n2(double n01) { return n01 * 2.0 - 1.0; }

    @Override
    public void onPopulate(IChunkProvider provider, int x, int z) {

    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getMonsters() {
        return new BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getCreatures() {
        return new BiomeGenBase.SpawnListEntry[0];
    }

    @Override
    protected BiomeGenBase.SpawnListEntry[] getWaterCreatures() {
        return new BiomeGenBase.SpawnListEntry[0];
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
        return this.configuredSeaLevel;
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
