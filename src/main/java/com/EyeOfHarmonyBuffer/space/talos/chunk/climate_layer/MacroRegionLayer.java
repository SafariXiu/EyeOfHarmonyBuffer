package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 第二阶段：宏群系平滑 / 小块吞并层。
 *
 * 功能：
 *   - 在较大 tile 上对 MacroPackageLayer 的原始结果做低分辨率采样；
 *   - 在 tile 的网格上做连通分量分析；
 *   - 将面积过小的宏群系碎块合并到周围更大的块中；
 *   - 对外提供“平滑后的宏群系 ID”查询接口。
 *
 * 注意：
 *   - 不会修改 MacroSites / MacroPackageLayer 的内部逻辑；
 *   - 只是对 getMacroPackageIdAt(x,z) 的结果做一个“智能后处理”。
 */

public final class MacroRegionLayer {

    /** 每个 tile 覆盖的世界空间大小（以 block 为单位）。*/
    public static final int TILE_SIZE = 1024;

    /** 低分辨率采样步长（以 block 为单位）。*/
    public static final int SAMPLE_STEP = 64;

    /** 网格尺寸 = TILE_SIZE / SAMPLE_STEP。*/
    public static final int GRID_SIZE = TILE_SIZE / SAMPLE_STEP; // 1024/64 = 16

    /** 连通分量里“最小保留格子数”，小于该值的视作碎块，需要合并。*/
    public static final int MIN_COMPONENT_SIZE = 8;

    /** 是否使用 8 邻域（true）或 4 邻域（false）。一般 4 邻更规整。*/
    public static final boolean USE_8_NEIGHBOR = false;

    private final int worldSeedInt;
    private final MacroPackageLayer baseLayer;

    private final Long2ObjectOpenHashMap<MacroTile> tileCache = new Long2ObjectOpenHashMap<>();

    public MacroRegionLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
        this.baseLayer = new MacroPackageLayer(worldSeedInt);
    }

    /** 对外暴露：获取“原始”宏群系 ID（不做平滑），方便 debug 对比。*/
    public MacroPackageId getRawMacroPackageIdAt(int x, int z) {
        return baseLayer.getMacroPackageIdAt(x, z);
    }

    /** 对外主接口：获取“平滑后的”宏群系 ID。*/
    public MacroPackageId getSmoothedMacroPackageIdAt(int x, int z) {
        MacroTile tile = getOrCreateTileFor(x, z);
        return tile.getSmoothedPkgAt(x, z);
    }

    /** 对外主接口：获取“平滑后的”群系 ID。*/
    public BiomeGenBase getBiomeAt(int x, int z) {
        MacroPackageId id = getSmoothedMacroPackageIdAt(x, z);
        return MacroPackageDefs.pickDeterministicBiome(id, x, z, worldSeedInt);
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
        // 按理说 local ∈ [0, TILE_SIZE)，但为保险再 clamp 一下
        if (local < 0) local = 0;
        if (local >= TILE_SIZE) local = TILE_SIZE - 1;
        return local;
    }

    private final class MacroTile {

        /** 该 tile 在 tile 坐标系中的位置。*/
        final int tileX;
        final int tileZ;

        /**
         * 网格大小：GRID_SIZE x GRID_SIZE。
         * 使用一维数组按 row-major 存储，index = z * GRID_SIZE + x。
         */
        final MacroPackageId[] rawPkg;      // 原始 pkgId
        final MacroPackageId[] smoothedPkg; // 平滑后的 pkgId
        final boolean[] isLand;             // 海陆标记（方便后续合并时不跨海陆）
        final int[] compId;                 // 连通分量 ID

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

        /** 在构造时完成：采样 + 连通分量分析 + 小块合并。*/
        private void build() {
            sampleRawGrid();

            buildComponents();

            mergeSmallComponents();
        }

        /** 将 (gx, gz) 映射到一维索引。*/
        private int idx(int gx, int gz) {
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
                    boolean land = TalosLandMask.isLand(worldX, worldZ, worldSeedInt);

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
                            if (nx < 0 || nx >= GRID_SIZE || nz < 0 || nz >= GRID_SIZE) {
                                continue;
                            }
                            int ni = idx(nx, nz);
                            if (compId[ni] != -1) {
                                continue;
                            }
                            if (rawPkg[ni] != id) {
                                continue;
                            }
                            if (isLand[ni] != land) {
                                continue;
                            }
                            compId[ni] = thisComp;
                            queue[qTail++] = ni;
                        }
                    }
                }
            }
        }

        /**
         * 小分量合并：
         *   - 对 size < MIN_COMPONENT_SIZE 的分量；
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
                if (c.size >= MIN_COMPONENT_SIZE) {
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
                            if (nx < 0 || nx >= GRID_SIZE || nz < 0 || nz >= GRID_SIZE) {
                                continue;
                            }
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

        /** 获取该 tile 中 (worldX,worldZ) 对应位置的平滑后 pkgId。*/
        MacroPackageId getSmoothedPkgAt(int worldX, int worldZ) {
            int localX = worldToLocalInTile(worldX);
            int localZ = worldToLocalInTile(worldZ);

            int gx = localX / SAMPLE_STEP;
            int gz = localZ / SAMPLE_STEP;

            if (gx < 0) gx = 0;
            if (gz < 0) gz = 0;
            if (gx >= GRID_SIZE) gx = GRID_SIZE - 1;
            if (gz >= GRID_SIZE) gz = GRID_SIZE - 1;

            int index = idx(gx, gz);
            MacroPackageId id = smoothedPkg[index];
            if (id == null) {
                id = rawPkg[index];
                if (id == null) {
                    return MacroPackageId.TEMPERATE_LOWLAND;
                }
            }
            return id;
        }
    }
}
