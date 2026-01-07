package com.EyeOfHarmonyBuffer.space.talos.chunk.world;

import com.EyeOfHarmonyBuffer.space.talos.*;
import com.EyeOfHarmonyBuffer.space.talos.biome.*;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroBiomeSelector;
import com.EyeOfHarmonyBuffer.space.talos.chunk.biome.selector.MacroSelectionResult;
import com.EyeOfHarmonyBuffer.space.talos.chunk.field.diagnostics.FieldDiagnostics;
import com.EyeOfHarmonyBuffer.space.talos.chunk.hook.Talos2Hooks;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.data.MacroTag;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.IMacroCellProvider;
import com.EyeOfHarmonyBuffer.space.talos.chunk.macro.builder.TalosMacroCellBuilder;
import galaxyspace.core.dimension.ChunkProviderSpaceLakes;
import galaxyspace.core.world.GSBiomeGenBase;
import micdoodle8.mods.galacticraft.api.prefab.core.BlockMetaPair;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.BiomeDecoratorSpace;
import micdoodle8.mods.galacticraft.api.prefab.world.gen.MapGenBaseMeta;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class ChunkProviderTalos2 extends ChunkProviderSpaceLakes {

    private static final boolean DEBUG_SOFTEN = false;

    private final World world;
    private final IMacroCellProvider macroCellBuilder;
    private final FieldDiagnostics diagnostics;
    private final MacroBiomeSelector macroSelector;

    private static final BlockMetaPair SNOW_SURFACE = new BlockMetaPair(Blocks.snow, (byte) 0);
    private static final BlockMetaPair PACKED_ICE = new BlockMetaPair(Blocks.packed_ice, (byte) 0);
    private static final BlockMetaPair SANDSTONE_FILL = new BlockMetaPair(Blocks.sandstone, (byte) 0);
    private static final BlockMetaPair MYCELIUM_TOP = new BlockMetaPair(Blocks.mycelium, (byte) 0);

    private static final Logger LOGGER = LogManager.getLogger("EyeOfHarmonyBuffer");

    private static long now() { return System.nanoTime(); }
    private static long ms(long nanos) { return nanos / 1_000_000L; }

    private static final boolean TALOS_TIMING = false;
    private static final int SLOW_CHUNK_MS = 80;
    private static final int VERY_SLOW_CHUNK_MS = 300;

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

        this.macroSelector = hook.fieldContext().getMacroSelector();

        this.macroCellBuilder = hook.macroCellBuilder();
        this.diagnostics = hook.diagnostics();

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

        final int landHeight = Math.min(worldHeight - 4, waterLevel + 5);
        final int seaFloorHeight = Math.max(4, waterLevel - 12);

        int[][] heightMap = new int[SIZE][SIZE];

        for (int localX = 0; localX <= 16; localX++) {
            for (int localZ = 0; localZ <= 16; localZ++) {
                heightMap[localX][localZ] =
                    shore.isLand[localX][localZ] ? landHeight : seaFloorHeight;
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
                    baseBiome = pickBiomeFromMacro(cell.macroResult);
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

        float temp = biome.temperature;
        float humid = biome.rainfall;

        if (temp < 0.16F) {
            surface = SNOW_SURFACE;
            filler = PACKED_ICE;
        } else if (temp > 0.82F && humid < 0.35F) {
            surface = getSandBlock();
            filler = SANDSTONE_FILL;
        } else if (humid > 0.78F && temp > 0.55F) {
            surface = MYCELIUM_TOP;
            filler = this.getDirtBlock();
        }

        for (int y = 0; y <= groundHeight; y++) {
            int idx = columnBase + y;
            BlockMetaPair pair;

            if (y == groundHeight) {
                pair = surface;
            } else if (y >= groundHeight - 3) {
                pair = (filler != null) ? filler : surface;
            } else {
                pair = (stone != null) ? stone : this.getStoneBlock();
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
        final int GRID = 16;

        for (int localX = 0; localX <= GRID; localX++) {
            for (int localZ = 0; localZ <= GRID; localZ++) {

                int gx = chunkX * 16 + localX;
                int gz = chunkZ * 16 + localZ;

                ChunkShoreCache.MacroCell cell = shore.macroContext[localX][localZ];
                if (cell == null) {
                    continue;
                }

                MacroSelectionResult macro = macroSelector.select(gx, gz);
                cell.macroResult = macro;
                cell.macroTag = macro.macroTag();
                cell.macroPatchId = macro.patchId();
                cell.macroRare = macro.rare();
                cell.macroTemp = macro.temperature();
                cell.macroHumid = macro.humidity();
                cell.macroContinental = macro.continentalScore();
                cell.macroCoastWidth = macro.coastWidth();
                cell.macroShelfWidth = macro.shelfWidth();
                cell.macroVariant = macro.variant();

                BiomeGenBase biome = pickBiomeFromMacro(macro);
                cell.resolvedBiome = biome;
            }
        }
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

    private BiomeGenBase pickBiomeFromMacro(MacroSelectionResult macro) {
        MacroBiome.MacroBiomeVariant variant = macro.variant();
        if (variant != null && variant.biome != null) {
            return variant.biome;
        }

        MacroTag tag = macro.macroTag();

        if (tag.isOceanic()) {
            return macro.coastDistance() <= macro.shelfWidth()
                ? TalosBiomes.TALOS_SHELF
                : TalosBiomes.TALOS_OCEAN;
        }

        if (tag.isCoastal() || macro.coastDistance() <= macro.coastWidth()) {
            return TalosBiomes.TALOS_BEACH;
        }

        if (tag.isFrozen()) {
            return TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
        }

        return switch (tag) {
            case DESERT -> TalosBiomes.TALOS_DESERT;
            case SAVANNA -> TalosBiomes.TALOS_SAVANNA;
            case STEPPE -> TalosBiomes.TALOS_WARM_STEPPE;
            case COOL_FOREST -> TalosBiomes.TALOS_COOL_FOREST;
            case TROPICAL -> TalosBiomes.TALOS_TROPICAL_RAIN;
            case TUNDRA -> TalosBiomes.TALOS_SUBPOLAR_TUNDRA;
            case MOUNTAIN, ALPINE -> TalosBiomes.TALOS_MOUNTAINS;
            case BASIN -> TalosBiomes.TALOS_BASIN;
            default -> TalosBiomes.TALOS_PLAINS;
        };
    }

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
