package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TectonicStyle;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.api.TalosTectonicStyles;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.PlateBoundaryState;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.TalosLandMask;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * 构造风格层：把板块边界的“点级多状态决策”搬到网格级。
 *
 * 流程（与 BiomeRegionLayer 同构）：
 *   - 64 格网格采样：每格按现行规则算风格标签（分离 ≥0.2 → RIFT，
 *     挤压主导按 0.2/0.5/0.7 分档）+ 该格的 DIVERGENT 强度；
 *   - 连通分量分析 + 小块吞并：碎片风格并入相邻大块；
 *   - 查询时用 128 格核（R=2 格）对相邻格做权重混合：
 *       * 输出主导风格（群系覆盖用）；
 *       * 输出平滑后的 DIVERGENT 强度（裂谷塑形用，剖面几何与权重同源）。
 */
public final class TectonicStyleLayer {

    public static final int TILE_SIZE = 1024;
    public static final int SAMPLE_STEP = 64;
    public static final int GRID_SIZE = TILE_SIZE / SAMPLE_STEP;

    /** 混合核半径（64 格单元）：2 格 = 128 blocks。 */
    private static final int BLEND_RADIUS_CELLS = 2;
    private static final double BLEND_RADIUS_BLOCKS =
        BLEND_RADIUS_CELLS * SAMPLE_STEP;

    /** 小块吞并阈值（同风格陆地分量小于该格数并入邻居）。 */
    private static final int MIN_COMPONENT_SIZE = 4;

    private final int worldSeedInt;

    private final Long2ObjectOpenHashMap<Tile> tileCache =
        new Long2ObjectOpenHashMap<>();

    public TectonicStyleLayer(int worldSeedInt) {
        this.worldSeedInt = worldSeedInt;
    }

    /** 查询结果：主导风格 + 平滑后的 DIVERGENT 强度（0~1）。 */
    public static final class Sample {
        public final TectonicStyle style;
        public final double smoothedDivergence;

        public Sample(TectonicStyle style, double smoothedDivergence) {
            this.style = style;
            this.smoothedDivergence = smoothedDivergence;
        }
    }

    /** 对 (worldX, worldZ) 查询构造风格。 */
    public Sample sampleAt(int worldX, int worldZ) {
        boolean isLandHere = TalosLandMask.isLandCheap(
            worldX, worldZ, worldSeedInt);

        int cellX0 = (int) Math.floor(
            (worldX - SAMPLE_STEP / 2.0) / SAMPLE_STEP);
        int cellZ0 = (int) Math.floor(
            (worldZ - SAMPLE_STEP / 2.0) / SAMPLE_STEP);

        double[] weightByStyle = new double[TectonicStyle.values().length];
        double divSum = 0.0;
        double wSum = 0.0;

        for (int dz = -BLEND_RADIUS_CELLS; dz <= BLEND_RADIUS_CELLS; dz++) {
            int cellWorldZ = SAMPLE_STEP * (cellZ0 + dz) + SAMPLE_STEP / 2;
            double ddz = worldZ - cellWorldZ;
            for (int dx = -BLEND_RADIUS_CELLS; dx <= BLEND_RADIUS_CELLS; dx++) {
                int cellWorldX = SAMPLE_STEP * (cellX0 + dx) + SAMPLE_STEP / 2;
                double ddx = worldX - cellWorldX;

                double dist = Math.sqrt(ddx * ddx + ddz * ddz);
                double w = blendKernel(dist);
                if (w <= 0.0) {
                    continue;
                }

                Tile tile = getOrCreateTileFor(cellWorldX, cellWorldZ);
                int localX = worldToLocalInTile(cellWorldX);
                int localZ = worldToLocalInTile(cellWorldZ);
                int index = (localZ / SAMPLE_STEP) * GRID_SIZE
                    + (localX / SAMPLE_STEP);

                if (tile.isLand[index] != isLandHere) {
                    continue;
                }

                TectonicStyle style = tile.mergedStyle[index];
                if (style == null) {
                    style = tile.rawStyle[index];
                }
                if (style == null) {
                    continue;
                }

                weightByStyle[style.ordinal()] += w;
                divSum += w * tile.divStrength[index];
                wSum += w;
            }
        }

        if (wSum <= 0.0) {
            return new Sample(TectonicStyle.NONE, 0.0);
        }

        TectonicStyle best = TectonicStyle.NONE;
        double bestW = -1.0;
        for (TectonicStyle st : TectonicStyle.values()) {
            double v = weightByStyle[st.ordinal()];
            if (v > bestW) {
                bestW = v;
                best = st;
            }
        }

        return new Sample(best, divSum / wSum);
    }

    private static double blendKernel(double distBlocks) {
        if (distBlocks >= BLEND_RADIUS_BLOCKS) {
            return 0.0;
        }
        double t = 1.0 - distBlocks / BLEND_RADIUS_BLOCKS;
        return t * t;
    }

    private Tile getOrCreateTileFor(int x, int z) {
        int tx = Math.floorDiv(x, TILE_SIZE);
        int tz = Math.floorDiv(z, TILE_SIZE);
        long key = (((long) tx) & 0xffffffffL) << 32
            | (((long) tz) & 0xffffffffL);
        Tile tile = tileCache.get(key);
        if (tile == null) {
            tile = new Tile(tx, tz);
            tileCache.put(key, tile);
        }
        return tile;
    }

    private static int worldToLocalInTile(int coord) {
        int t = Math.floorDiv(coord, TILE_SIZE);
        int base = t * TILE_SIZE;
        int local = coord - base;
        if (local < 0) {
            local = 0;
        }
        if (local >= TILE_SIZE) {
            local = TILE_SIZE - 1;
        }
        return local;
    }

    private final class Tile {

        final int tileX;
        final int tileZ;

        final TectonicStyle[] rawStyle = new TectonicStyle[GRID_SIZE * GRID_SIZE];
        final TectonicStyle[] mergedStyle = new TectonicStyle[GRID_SIZE * GRID_SIZE];
        final double[] divStrength = new double[GRID_SIZE * GRID_SIZE];
        final boolean[] isLand = new boolean[GRID_SIZE * GRID_SIZE];
        final int[] compId = new int[GRID_SIZE * GRID_SIZE];

        int compCount;
        Component[] components;

        Tile(int tileX, int tileZ) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            build();
        }

        int idx(int gx, int gz) {
            return gz * GRID_SIZE + gx;
        }

        void build() {
            sampleGrid();
            buildComponents();
            mergeSmallComponents();
        }

        void sampleGrid() {
            int baseWorldX = tileX * TILE_SIZE;
            int baseWorldZ = tileZ * TILE_SIZE;
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int index = idx(gx, gz);
                    int worldX = baseWorldX + gx * SAMPLE_STEP + SAMPLE_STEP / 2;
                    int worldZ = baseWorldZ + gz * SAMPLE_STEP + SAMPLE_STEP / 2;

                    TalosLandMask.Sample s = TalosLandMask.sampleFull(
                        worldX, worldZ, worldSeedInt);

                    TectonicStyle style = TalosTectonicStyles.styleFromSample(s);
                    double div = 0.0;
                    boolean land = s != null && s.isLand;
                    if (land) {
                        div = TalosLandMask.maxBoundaryStrength(
                            PlateBoundaryState.DIVERGENT, s);
                    }

                    rawStyle[index] = style;
                    mergedStyle[index] = style;
                    divStrength[index] = div;
                    isLand[index] = land;
                    compId[index] = -1;
                }
            }
        }

        void buildComponents() {
            components = new Component[GRID_SIZE * GRID_SIZE];
            compCount = 0;

            final int[][] OFFS = {{1,0},{-1,0},{0,1},{0,-1}};
            int[] queue = new int[GRID_SIZE * GRID_SIZE];

            for (int gz = 0; gz < GRID_SIZE; gz++) {
                for (int gx = 0; gx < GRID_SIZE; gx++) {
                    int startIndex = idx(gx, gz);
                    if (compId[startIndex] != -1) {
                        continue;
                    }
                    TectonicStyle style = rawStyle[startIndex];
                    boolean land = isLand[startIndex];
                    if (style == null) {
                        compId[startIndex] = -2;
                        continue;
                    }

                    int thisComp = compCount;
                    Component comp = new Component();
                    comp.style = style;
                    comp.isLand = land;
                    components[thisComp] = comp;
                    compCount++;

                    int qHead = 0;
                    int qTail = 0;
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
                            if (nx < 0 || nx >= GRID_SIZE
                                || nz < 0 || nz >= GRID_SIZE) {
                                continue;
                            }
                            int ni = idx(nx, nz);
                            if (compId[ni] != -1) {
                                continue;
                            }
                            if (rawStyle[ni] != style || isLand[ni] != land) {
                                continue;
                            }
                            compId[ni] = thisComp;
                            queue[qTail++] = ni;
                        }
                    }
                }
            }
        }

        void mergeSmallComponents() {
            final int[][] OFFS = {{1,0},{-1,0},{0,1},{0,-1}};
            for (int compIndex = 0; compIndex < compCount; compIndex++) {
                Component c = components[compIndex];
                if (c == null || c.size >= MIN_COMPONENT_SIZE) {
                    continue;
                }

                boolean[] neighborMark = new boolean[compCount];
                for (int gz = 0; gz < GRID_SIZE; gz++) {
                    for (int gx = 0; gx < GRID_SIZE; gx++) {
                        int index = idx(gx, gz);
                        if (compId[index] != compIndex) {
                            continue;
                        }
                        for (int[] d : OFFS) {
                            int nx = gx + d[0];
                            int nz = gz + d[1];
                            if (nx < 0 || nx >= GRID_SIZE
                                || nz < 0 || nz >= GRID_SIZE) {
                                continue;
                            }
                            int otherComp = compId[idx(nx, nz)];
                            if (otherComp < 0 || otherComp == compIndex) {
                                continue;
                            }
                            Component oc = components[otherComp];
                            if (oc == null || oc.isLand != c.isLand) {
                                continue;
                            }
                            neighborMark[otherComp] = true;
                        }
                    }
                }

                int bestNeighbor = -1;
                int bestSize = -1;
                for (int ni = 0; ni < compCount; ni++) {
                    if (!neighborMark[ni]) {
                        continue;
                    }
                    Component oc = components[ni];
                    if (oc != null && oc.size > bestSize) {
                        bestSize = oc.size;
                        bestNeighbor = ni;
                    }
                }
                if (bestNeighbor < 0) {
                    continue;
                }
                TectonicStyle target = components[bestNeighbor].style;
                for (int i = 0; i < mergedStyle.length; i++) {
                    if (compId[i] == compIndex) {
                        mergedStyle[i] = target;
                    }
                }
            }
        }
    }

    private static final class Component {
        TectonicStyle style;
        boolean isLand;
        int size;
    }
}
