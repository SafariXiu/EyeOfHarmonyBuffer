package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosMacroClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.biome.BiomeGenBase;

/**
 * 真实群系（Biome）级别的小块吞并 / 平滑层（标准版）。
 *
 * 链条统一：
 *   - 原始 Biome 必须统一来自 TalosMacroClimate.getRawBiome(...)：
 *       * 内部：MacroPackageLayer（陆/海两套 Worley 场叠加 + TalosLandMask 精确裁剪）
 *       * 再用 MacroSitesSeparated.SubPatch 决定站点内部微结构。
 *   - 海陆标记统一来自 TalosLandMask.isLand(worldX, worldZ, worldSeedInt)；
 *   - 在 tile 的 16x16 网格上按 (Biome, isLand) 做连通分量、小块吞并：
 *       * 同 isLand 的 component 之间可以互相吞并；
 *       * 对“靠岸的海洋 component”使用更严格的吞并阈值；
 *   - 最终对 block 查询时，再用 block 级 isLand 校验：
 *       * 若当前 block 的 isLand 与该格子的 isLand[index] 不一致，则回退到原始 Biome。
 */

public final class BiomeRegionLayer {

    /** 每个 tile 覆盖的世界空间大小（以 block 为单位）。 */
    public static final int TILE_SIZE = 1024;

    /** 低分辨率采样步长（以 block 为单位）。 */
    public static final int SAMPLE_STEP = 64;

    /** 网格尺寸 = TILE_SIZE / SAMPLE_STEP。*/
    public static final int GRID_SIZE = TILE_SIZE / SAMPLE_STEP; // 1024/64 = 16

    /** 连通分量里“最小保留格子数”的基础阈值。*/
    public static final int MIN_LAND_COMPONENT_SIZE = 4; // 陆地
    public static final int MIN_OCEAN_COMPONENT_SIZE = 4; // 远离海岸的海洋
    public static final int MIN_COAST_OCEAN_COMPONENT_SIZE = 8; // 靠岸海洋分量更易被吞

    /** 是否使用 8 邻域（true）或 4 邻域（false）。一般 4 邻更规整。*/
    public static final boolean USE_8_NEIGHBOR = true;

    private final int worldSeedInt;

    private final Long2ObjectOpenHashMap<BiomeTile> tileCache = new Long2ObjectOpenHashMap<>();

    public BiomeRegionLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    /** 对外主接口：获取“平滑后的” Biome。*/
    public BiomeGenBase getSmoothedBiomeAt(int x, int z) {
        BiomeTile tile = getOrCreateTileFor(x, z);
        return tile.getSmoothedBiomeAt(x, z);
    }

    private static long packTile(int tx, int tz) {
        return (((long) tx) & 0xffffffffL) << 32
            | (((long) tz) & 0xffffffffL);
    }

    private BiomeTile getOrCreateTileFor(int x, int z) {
        int tx = worldToTileCoord(x);
        int tz = worldToTileCoord(z);
        long key = packTile(tx, tz);
        BiomeTile tile = tileCache.get(key);
        if (tile == null) {
            tile = new BiomeTile(tx, tz);
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

    private final class BiomeTile {

        final int tileX;
        final int tileZ;

        final BiomeGenBase[] rawBiome; // 原始 Biome（TalosMacroClimate.getRawBiome）
        final BiomeGenBase[] smoothedBiome; // 平滑后的 Biome
        final boolean[] isLand; // TalosLandMask 的海陆标记
        final int[] compId; // 连通分量 ID

        int compCount;
        Component[] components;

        BiomeTile(int tileX, int tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.rawBiome = new BiomeGenBase[GRID_SIZE * GRID_SIZE];
            this.smoothedBiome = new BiomeGenBase[GRID_SIZE * GRID_SIZE];
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

        private int idx(int gx, int gz) {
            return gz * GRID_SIZE + gx;
        }

        /** 采样阶段：填充 rawBiome / isLand。*/
        private void sampleRawGrid() {
            int baseWorldX = tileX * TILE_SIZE;
            int baseWorldZ = tileZ * TILE_SIZE;

            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int index = idx(gx, gz);

                    int worldX = baseWorldX + gx * SAMPLE_STEP + SAMPLE_STEP / 2;
                    int worldZ = baseWorldZ + gz * SAMPLE_STEP + SAMPLE_STEP / 2;

                    BiomeGenBase biome = TalosMacroClimate.getRawBiome(worldX, worldZ, worldSeedInt);
                    // 原来：boolean land = TalosLandMask.isLand(worldX, worldZ, worldSeedInt);
                    boolean land = TalosLandMask.isLandCheap(worldX, worldZ, worldSeedInt);

                    rawBiome[index] = biome;
                    smoothedBiome[index] = biome;
                    isLand[index] = land;
                    compId[index] = -1;
                }
            }
        }

        /** 连通分量数据结构。*/
        private final class Component {
            BiomeGenBase biome;
            boolean isLand;
            int size;
            boolean touchesCoast;
        }

        /** 在 rawBiome 上按 (biome, isLand) 做连通分量分析。*/
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

                    BiomeGenBase biome = rawBiome[startIndex];
                    boolean land = isLand[startIndex];

                    if (biome == null) {
                        compId[startIndex] = -2;
                        continue;
                    }

                    int thisComp = compCount;
                    Component comp = new Component();
                    comp.biome = biome;
                    comp.isLand = land;
                    comp.size = 0;
                    comp.touchesCoast = false;
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

                        if (!isLand[cur]) {
                            for (int[] d4 : OFFS_4) {
                                int nx4 = cx + d4[0];
                                int nz4 = cz + d4[1];
                                if (nx4 < 0 || nx4 >= GRID_SIZE || nz4 < 0 || nz4 >= GRID_SIZE) continue;
                                int ni4 = idx(nx4, nz4);
                                if (isLand[ni4]) {
                                    comp.touchesCoast = true;
                                    break;
                                }
                            }
                        }

                        for (int[] d : OFFS) {
                            int nx = cx + d[0];
                            int nz = cz + d[1];
                            if (nx < 0 || nx >= GRID_SIZE || nz < 0 || nz >= GRID_SIZE) continue;
                            int ni = idx(nx, nz);
                            if (compId[ni] != -1) continue;
                            if (rawBiome[ni] != biome) continue;
                            if (isLand[ni] != land) continue;
                            compId[ni] = thisComp;
                            queue[qTail++] = ni;
                        }
                    }
                }
            }
        }

        // mergeSmallComponents 保持不变，这里省略注释，只是原样拷贝
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

                int threshold;
                if (c.isLand) {
                    threshold = MIN_LAND_COMPONENT_SIZE;
                } else if (c.touchesCoast) {
                    threshold = MIN_COAST_OCEAN_COMPONENT_SIZE;
                } else {
                    threshold = MIN_OCEAN_COMPONENT_SIZE;
                }

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
                BiomeGenBase targetBiome = target.biome;

                for (int i = 0; i < smoothedBiome.length; i++) {
                    if (compId[i] == compIndex) {
                        smoothedBiome[i] = targetBiome;
                    }
                }
            }
        }

        /** 获取该 tile 中 (worldX,worldZ) 对应位置的平滑后 Biome（带 block 级海陆校验 + 多格插值）。*/
        BiomeGenBase getSmoothedBiomeAt(int worldX, int worldZ) {
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

            // 原来：boolean isLandHere = TalosLandMask.isLand(worldX, worldZ, worldSeedInt);
            boolean isLandHere = TalosLandMask.isLandCheap(worldX, worldZ, worldSeedInt);

            final int radius = 1;

            int baseWorldX = tileX * TILE_SIZE;
            int baseWorldZ = tileZ * TILE_SIZE;

            java.util.IdentityHashMap<BiomeGenBase, Double> weightMap = new java.util.IdentityHashMap<>();

            for (int dz = -radius; dz <= radius; dz++) {
                int gzN = gzCenter + dz;
                if (gzN < 0 || gzN >= GRID_SIZE) continue;

                for (int dx = -radius; dx <= radius; dx++) {
                    int gxN = gxCenter + dx;
                    if (gxN < 0 || gxN >= GRID_SIZE) continue;

                    int index = idx(gxN, gzN);

                    if (isLand[index] != isLandHere) continue;

                    BiomeGenBase b = smoothedBiome[index];
                    if (b == null) {
                        b = rawBiome[index];
                    }
                    if (b == null) continue;

                    int cellWorldX = baseWorldX + gxN * SAMPLE_STEP + SAMPLE_STEP / 2;
                    int cellWorldZ = baseWorldZ + gzN * SAMPLE_STEP + SAMPLE_STEP / 2;

                    double ddx = worldX - cellWorldX;
                    double ddz = worldZ - cellWorldZ;
                    double dist2 = ddx * ddx + ddz * ddz;

                    double w = 1.0 / (dist2 + 1.0);

                    Double old = weightMap.get(b);
                    weightMap.put(b, (old == null ? 0.0 : old) + w);
                }
            }

            if (weightMap.isEmpty()) {
                return TalosMacroClimate.getRawBiome(worldX, worldZ, worldSeedInt);
            }

            BiomeGenBase bestBiome = null;
            double bestW = -1.0;
            for (java.util.Map.Entry<BiomeGenBase, Double> e : weightMap.entrySet()) {
                double w = e.getValue();
                if (w > bestW) {
                    bestW = w;
                    bestBiome = e.getKey();
                }
            }

            if (bestBiome != null) {
                return bestBiome;
            }

            return TalosMacroClimate.getRawBiome(worldX, worldZ, worldSeedInt);
        }
    }
}
