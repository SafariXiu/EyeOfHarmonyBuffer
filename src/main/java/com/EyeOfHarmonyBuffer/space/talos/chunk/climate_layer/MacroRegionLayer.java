package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;


/**
 * 第二阶段：宏群系平滑 / 小块吞并层（标准版）。
 *
 * 链路统一：
 *   - 宏群系原始结果统一来自 MacroPackageLayer（内部是“陆/海两套 Worley 场叠加 + TalosLandMask 精确裁剪”）；
 *   - 海陆标记统一来自 TalosLandMask.isLand(worldX, worldZ, worldSeedInt)；
 *   - 在 tile 的 16x16 低分辨率网格上做连通分量、小块吞并；
 *   - 最终对单个 block 查询时，再用同一个 TalosLandMask 做 block 级精确校验：
 *       * 若当前 block 的 isLand 与该格子缓存的 isLand 不一致，则回退到底层 MacroPackageLayer 逐方块结果；
 *       * 否则使用平滑后的 smoothedPkg。
 */

public final class MacroRegionLayer {

    /** 每个 tile 覆盖的世界空间大小（以 block 为单位）。*/
    public static final int TILE_SIZE = 1024;

    /** 低分辨率采样步长（以 block 为单位）。*/
    public static final int SAMPLE_STEP = 64;

    /** 网格尺寸 = TILE_SIZE / SAMPLE_STEP。*/
    public static final int GRID_SIZE = TILE_SIZE / SAMPLE_STEP; // 1024/64 = 16

    /** 连通分量里“最小保留格子数”基础值。*/
    public static final int MIN_LAND_COMPONENT_SIZE  = 8;
    public static final int MIN_OCEAN_COMPONENT_SIZE = 16;

    /** 是否使用 8 邻域（true）或 4 邻域（false）。一般 4 邻更规整。*/
    public static final boolean USE_8_NEIGHBOR = false;

    private final int worldSeedInt;
    /** 宏群系原始层：陆/海两套 Worley 场叠加 + TalosLandMask 精确裁剪。 */
    private final MacroPackageLayer baseLayer;

    /** 宏群系种类总数，用于内部权重数组。 */
    private static final int MACRO_COUNT = MacroPackageId.values().length;

    private final Long2ObjectOpenHashMap<MacroTile> tileCache = new Long2ObjectOpenHashMap<>();

    public MacroRegionLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
        this.baseLayer = new MacroPackageLayer(worldSeedInt);
    }

    /** 对外主接口：获取“平滑后的”宏群系 ID。*/
    public MacroPackageId getSmoothedMacroPackageIdAt(int x, int z) {
        return getSmoothedMacroPackageIdAt(
            x, z, TalosLandMask.isLandCheap(x, z, worldSeedInt)
        );
    }

    /**
     * 已知该点 isLand 时的平滑宏群系查询。
     * 与上面的接口结果完全一致，只是省掉内部重复的 isLandCheap 计算
     * （调用方已经通过 chunk 级 LandSample 表拿到结果时使用）。
     */
    public MacroPackageId getSmoothedMacroPackageIdAt(int x, int z,
                                                      boolean isLandHere) {
        MacroTile tile = getOrCreateTileFor(x, z);
        return tile.getSmoothedPkgAt(x, z, isLandHere);
    }

    /** 对外主接口：获取“平滑后的”群系 ID（基于宏群系 + 确定性子 Biome 选择）。*/
    public BiomeGenBase getBiomeAt(int x, int z) {
        MacroPackageId id = getSmoothedMacroPackageIdAt(x, z);
        return MacroPackageDefs.pickDeterministicBiome(id, x, z, worldSeedInt);
    }

    /**
     * 在宏群系平滑网格上，对 (worldX,worldZ) 附近做一个小邻域空间加权统计，
     * 返回最多 maxEntries 个宏群系及其权重。
     *
     * 新实现：
     *   - 不再限制在“当前 tile 内”的 3x3；改为在世界坐标上采样；
     *   - 每个采样点通过 getSmoothedMacroPackageIdAt(...) 获取 pkgId，可自然跨 tile；
     *   - 权重仍然使用 1 / (dist^2 + 1) 的反平方衰减；
     *   - 按 pkg 聚合、归一化，再取权重最大的前 maxEntries 个。
     */
    public TalosMacroClimate.MacroBlendSample sampleBlendAt(int worldX, int worldZ,
                                                            int maxEntries) {
        return sampleBlendAtImpl(worldX, worldZ, maxEntries, null);
    }

    /**
     * 带共享采样缓存的版本：同一个 chunk 内，3x3 邻域采样点高度重复，
     * 通过外部传入的 cache 让每个唯一采样点只算一次。
     * 结果与不带缓存的版本完全一致（同一确定性函数，只是记忆化）。
     */
    public TalosMacroClimate.MacroBlendSample sampleBlendAt(
        int worldX, int worldZ, int maxEntries,
        Long2ObjectOpenHashMap<TalosMacroClimate.SmoothedPkgPoint> cache
    ) {
        return sampleBlendAtImpl(worldX, worldZ, maxEntries, cache);
    }

    private TalosMacroClimate.MacroBlendSample sampleBlendAtImpl(
        int worldX, int worldZ, int maxEntries,
        Long2ObjectOpenHashMap<TalosMacroClimate.SmoothedPkgPoint> cache
    ) {
        if (maxEntries <= 0) {
            return new TalosMacroClimate.MacroBlendSample(
                new TalosMacroClimate.MacroBlendEntry[0]
            );
        }

        final int radius = 1;

        MacroTile centerTile = getOrCreateTileFor(worldX, worldZ);

        int localX = worldToLocalInTile(worldX);
        int localZ = worldToLocalInTile(worldZ);

        int gxCenter = localX / SAMPLE_STEP;
        int gzCenter = localZ / SAMPLE_STEP;

        int baseWorldX = centerTile.tileX * TILE_SIZE;
        int baseWorldZ = centerTile.tileZ * TILE_SIZE;
        int centerCellWorldX = baseWorldX + gxCenter * SAMPLE_STEP + SAMPLE_STEP / 2;
        int centerCellWorldZ = baseWorldZ + gzCenter * SAMPLE_STEP + SAMPLE_STEP / 2;

        double[] accum = new double[MACRO_COUNT];

        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {

                int sampleWorldX = centerCellWorldX + dx * SAMPLE_STEP;
                int sampleWorldZ = centerCellWorldZ + dz * SAMPLE_STEP;

                MacroPackageId id;
                if (cache != null) {
                    id = TalosMacroClimate.getSmoothedPkgCached(
                        sampleWorldX, sampleWorldZ, worldSeedInt, cache
                    ).pkg;
                } else {
                    id = getSmoothedMacroPackageIdAt(sampleWorldX, sampleWorldZ);
                }
                if (id == null) continue;

                double ddx = worldX - sampleWorldX;
                double ddz = worldZ - sampleWorldZ;
                double dist2 = ddx * ddx + ddz * ddz;

                double w = 1.0 / (dist2 + 1.0);

                accum[id.ordinal()] += w;
            }
        }

        double sum = 0.0;
        for (double v : accum) {
            sum += v;
        }

        if (sum <= 0.0) {
            MacroPackageId id;
            if (cache != null) {
                id = TalosMacroClimate.getSmoothedPkgCached(
                    worldX, worldZ, worldSeedInt, cache
                ).pkg;
            } else {
                id = getSmoothedMacroPackageIdAt(worldX, worldZ);
            }
            TalosMacroClimate.MacroBlendEntry[] entries =
                new TalosMacroClimate.MacroBlendEntry[] {
                    new TalosMacroClimate.MacroBlendEntry(id, 1.0)
                };
            return new TalosMacroClimate.MacroBlendSample(entries);
        }

        MacroPackageId[] allIds = MacroPackageId.values();
        int countNonZero = 0;
        for (int i = 0; i < MACRO_COUNT; i++) {
            if (accum[i] > 1e-9) countNonZero++;
        }

        TalosMacroClimate.MacroBlendEntry[] allEntries =
            new TalosMacroClimate.MacroBlendEntry[countNonZero];

        int idxEntry = 0;
        for (int i = 0; i < MACRO_COUNT; i++) {
            double v = accum[i];
            if (v <= 1e-9) continue;
            double weight = v / sum;
            allEntries[idxEntry++] =
                new TalosMacroClimate.MacroBlendEntry(allIds[i], weight);
        }

        java.util.Arrays.sort(allEntries,
            (a, b) -> Double.compare(b.weight, a.weight));

        int outCount = Math.min(maxEntries, allEntries.length);
        TalosMacroClimate.MacroBlendEntry[] result =
            new TalosMacroClimate.MacroBlendEntry[outCount];
        System.arraycopy(allEntries, 0, result, 0, outCount);

        return new TalosMacroClimate.MacroBlendSample(result);
    }

    private static long packTile(int tx, int tz) {
        return (((long) tx) & 0xffffffffL) << 32
            | (((long) tz) & 0xffffffffL);
    }

    private MacroTile getOrCreateTileFor(int x, int z) {
        int tx = worldToTileCoord(x);
        int tz = worldToTileCoord(z);
        long key = packTile(tx, tz);
        MacroTile tile = tileCache.get(key);
        if (tile == null) {
            tile = new MacroTile(tx, tz);
            tileCache.put(key, tile);
        }
        return tile;
    }

    /** 世界坐标 -> tile 坐标（对负数稳定）。*/
    private static int worldToTileCoord(int coord) {
        return Math.floorDiv(coord, TILE_SIZE);
    }

    /** 世界坐标在其所在 tile 内的局部坐标 [0, TILE_SIZE)。*/
    private static int worldToLocalInTile(int coord) {
        int t = worldToTileCoord(coord);
        int base = t * TILE_SIZE;
        int local = coord - base;
        if (local < 0) local = 0;
        if (local >= TILE_SIZE) local = TILE_SIZE - 1;
        return local;
    }

    private final class MacroTile {

        /** 该 tile 在 tile 坐标系中的位置。*/
        final int tileX;
        final int tileZ;

        final MacroPackageId[] rawPkg;
        final MacroPackageId[] smoothedPkg;
        final boolean[]        isLand;
        final int[]            compId;

        int compCount;
        Component[] components;

        MacroTile(int tileX, int tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.rawPkg = new MacroPackageId[GRID_SIZE * GRID_SIZE];
            this.smoothedPkg = new MacroPackageId[GRID_SIZE * GRID_SIZE];
            this.isLand = new boolean[GRID_SIZE * GRID_SIZE];
            this.compId = new int[GRID_SIZE * GRID_SIZE];

            build();
        }

        /** 构造时完成：采样 + 连通分量分析 + 小块合并。*/
        private void build() {
            sampleRawGrid();
            buildComponents();
            mergeSmallComponents();
        }

        /** 将 (gx, gz) 映射到一维索引。*/
        int idx(int gx, int gz) {
            return gz * GRID_SIZE + gx;
        }

        /** 采样阶段：填充 rawPkg / isLand。*/
        private void sampleRawGrid() {
            int baseWorldX = tileX * TILE_SIZE;
            int baseWorldZ = tileZ * TILE_SIZE;

            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int index = idx(gx, gz);

                    int worldX = baseWorldX + gx * SAMPLE_STEP + SAMPLE_STEP / 2;
                    int worldZ = baseWorldZ + gz * SAMPLE_STEP + SAMPLE_STEP / 2;

                    MacroPackageId id = baseLayer.getMacroPackageIdAt(worldX, worldZ);
                    boolean land = TalosLandMask.isLandCheap(worldX, worldZ, worldSeedInt);

                    rawPkg[index] = id;
                    smoothedPkg[index] = id;
                    isLand[index] = land;
                    compId[index] = -1;
                }
            }
        }

        /** 连通分量数据结构。*/
        private final class Component {
            MacroPackageId pkgId;
            boolean isLand;
            int size;
        }

        /** 在 rawPkg 上按 (pkgId, isLand) 做连通分量分析。*/
        private void buildComponents() {
            components = new Component[GRID_SIZE * GRID_SIZE];
            compCount = 0;

            final int[][] OFFS_4 = {{1,0},{-1,0},{0,1},{0,-1}};
            final int[][] OFFS_8 = {
                {1,0},{-1,0},{0,1},{0,-1},
                {1,1},{1,-1},{-1,1},{-1,-1}
            };
            final int[][] OFFS = USE_8_NEIGHBOR ? OFFS_8 : OFFS_4;

            int[] queue = new int[GRID_SIZE * GRID_SIZE];

            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int startIndex = idx(gx, gz);
                    if (compId[startIndex] != -1) {
                        continue;
                    }

                    MacroPackageId id = rawPkg[startIndex];
                    boolean land = isLand[startIndex];

                    if (id == null) {
                        compId[startIndex] = -2;
                        continue;
                    }

                    int thisComp = compCount;
                    Component comp = new Component();
                    comp.pkgId = id;
                    comp.isLand = land;
                    comp.size = 0;
                    components[thisComp] = comp;
                    compCount++;

                    int qHead = 0, qTail = 0;
                    queue[qTail++] = startIndex;
                    compId[startIndex] = thisComp;

                    while (qHead < qTail) {
                        int cur = queue[qHead++];
                        comp.size++;

                        int cx = cur % GRID_SIZE;
                        int cz = cur / GRID_SIZE;

                        for (int[] d : OFFS) {
                            int nx = cx + d[0];
                            int nz = cz + d[1];
                            if (nx < 0 || nx >= GRID_SIZE || nz < 0 || nz >= GRID_SIZE) continue;
                            int ni = idx(nx, nz);
                            if (compId[ni] != -1) continue;
                            if (rawPkg[ni] != id) continue;
                            if (isLand[ni] != land) continue;
                            compId[ni] = thisComp;
                            queue[qTail++] = ni;
                        }
                    }
                }
            }
        }

        /**
         * 小分量合并：
         *   - 对 size < 阈值 的分量；
         *   - 找它们的邻居分量（同 isLand）；
         *   - 并入“size 最大的邻居分量”的 pkgId。
         */
        private void mergeSmallComponents() {
            if (compCount <= 0) return;

            int maxComp = compCount;

            final int[][] OFFS_4 = {{1,0},{-1,0},{0,1},{0,-1}};
            final int[][] OFFS_8 = {
                {1,0},{-1,0},{0,1},{0,-1},
                {1,1},{1,-1},{-1,1},{-1,-1}
            };
            final int[][] OFFS = USE_8_NEIGHBOR ? OFFS_8 : OFFS_4;

            for (int compIndex = 0; compIndex < maxComp; compIndex++) {
                Component c = components[compIndex];
                if (c == null) continue;

                int threshold = c.isLand ? MIN_LAND_COMPONENT_SIZE : MIN_OCEAN_COMPONENT_SIZE;
                if (c.size >= threshold) {
                    continue;
                }

                boolean[] neighborMark = new boolean[maxComp];

                for (int gz = 0; gz < GRID_SIZE; gz++) {
                    for (int gx = 0; gx < GRID_SIZE; gx++) {
                        int index = idx(gx, gz);
                        if (compId[index] != compIndex) continue;

                        int cx = gx;
                        int cz = gz;

                        for (int[] d : OFFS) {
                            int nx = cx + d[0];
                            int nz = cz + d[1];
                            if (nx < 0 || nx >= GRID_SIZE || nz < 0 || nz >= GRID_SIZE) continue;
                            int ni = idx(nx, nz);
                            int otherComp = compId[ni];
                            if (otherComp == -1 || otherComp == compIndex) continue;
                            Component oc = components[otherComp];
                            if (oc == null) continue;
                            if (oc.isLand != c.isLand) continue;
                            neighborMark[otherComp] = true;
                        }
                    }
                }

                int bestNeighbor = -1;
                int bestSize = -1;

                for (int ni = 0; ni < maxComp; ni++) {
                    if (!neighborMark[ni]) continue;
                    Component oc = components[ni];
                    if (oc == null) continue;
                    if (oc.size > bestSize) {
                        bestSize = oc.size;
                        bestNeighbor = ni;
                    }
                }

                if (bestNeighbor == -1) {
                    continue;
                }

                Component target = components[bestNeighbor];
                MacroPackageId targetId = target.pkgId;

                for (int i = 0; i < smoothedPkg.length; i++) {
                    if (compId[i] == compIndex) {
                        smoothedPkg[i] = targetId;
                    }
                }
            }
        }

        /** 优先使用平滑后的 pkgId，若为 null 则退回 rawPkg。*/
        private MacroPackageId getCellPkgSafe(int index) {
            MacroPackageId id = smoothedPkg[index];
            if (id == null) {
                id = rawPkg[index];
            }
            return id;
        }

        /**
         * 获取该 tile 中 (worldX,worldZ) 对应位置的平滑后 pkgId：
         *   - 先用 TalosLandMask 做 block 级海陆校验；
         *   - 然后在当前 tile 内的 3x3 采样格上做空间加权投票；
         *   - 投票只考虑与当前 block 海陆一致的格子；
         *   - 选权重最大的宏群系作为结果；
         *   - 若极端情况下没有任何格子参与投票，则退回到底层 baseLayer。
         */
        MacroPackageId getSmoothedPkgAt(int worldX, int worldZ,
                                        boolean isLandHere) {
            int localX = worldToLocalInTile(worldX);
            int localZ = worldToLocalInTile(worldZ);

            double gx = (double) localX / SAMPLE_STEP;
            double gz = (double) localZ / SAMPLE_STEP;

            int gxCenter = (int) Math.floor(gx);
            int gzCenter = (int) Math.floor(gz);

            if (gxCenter < 0) gxCenter = 0;
            if (gzCenter < 0) gzCenter = 0;
            if (gxCenter >= GRID_SIZE) gxCenter = GRID_SIZE - 1;
            if (gzCenter >= GRID_SIZE) gzCenter = GRID_SIZE - 1;

            final int radius = 1;

            double[] accum = new double[MACRO_COUNT];

            int baseWorldX = tileX * TILE_SIZE;
            int baseWorldZ = tileZ * TILE_SIZE;

            for (int dz = -radius; dz <= radius; dz++) {
                int gzN = gzCenter + dz;
                if (gzN < 0 || gzN >= GRID_SIZE) continue;

                for (int dx = -radius; dx <= radius; dx++) {
                    int gxN = gxCenter + dx;
                    if (gxN < 0 || gxN >= GRID_SIZE) continue;

                    int index = idx(gxN, gzN);

                    if (isLand[index] != isLandHere) continue;

                    MacroPackageId id = getCellPkgSafe(index);
                    if (id == null) continue;

                    int cellWorldX = baseWorldX + gxN * SAMPLE_STEP + SAMPLE_STEP / 2;
                    int cellWorldZ = baseWorldZ + gzN * SAMPLE_STEP + SAMPLE_STEP / 2;

                    double ddx = worldX - cellWorldX;
                    double ddz = worldZ - cellWorldZ;
                    double dist2 = ddx * ddx + ddz * ddz;

                    double w = 1.0 / (dist2 + 1.0);

                    accum[id.ordinal()] += w;
                }
            }

            int bestIdx = -1;
            double bestW = -1.0;
            for (int i = 0; i < MACRO_COUNT; i++) {
                double v = accum[i];
                if (v > bestW) {
                    bestW = v;
                    bestIdx = i;
                }
            }

            if (bestIdx < 0 || bestW <= 0.0) {
                return baseLayer.getMacroPackageIdAt(worldX, worldZ);
            }

            return MacroPackageId.values()[bestIdx];
        }
    }
}
