package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.geom;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api.LandMask16;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.PlateId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.SupercontinentId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.LandType;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.TectonicLandSample;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理整个世界的超级大陆 / 板块查询。
 *
 * 作用：
 *   - 通过 (blockX, blockZ) 和 worldSeed 找到对应的 Supercontinent；
 *   - 计算：
 *       * 是否在超级大陆内部；
 *       * 到海岸的有符号距离；
 *       * 径向中心度 centerward；
 *       * 海岸带 / 大陆架带权重；
 *       * 板块 ID；
 *       * 板块边界粗权重。
 *
 * 注意：
 *   - 这里只做“点级别”的查询和 chunk 级 LandMask16；
 *   - 更大尺度 tile 缓存仍放在 WorldgenAPI / TalosLandMask 那一层做。
 */

public final class TectonicWorld {

    private final long worldSeed;

    /** (cellX,cellZ) → Supercontinent 的缓存。 */
    private final Map<Long, Supercontinent> continentCache =
        new HashMap<Long, Supercontinent>();

    public TectonicWorld(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    /**
     * 将 (cellX, cellZ) 打包成 long 作为 continentCache 的 key。
     */
    private static long packCellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /**
     * 获取指定超级单元格的 Supercontinent，不存在则构造一个并放入缓存。
     */
    private Supercontinent getSupercontinent(int cellX, int cellZ) {
        long key = packCellKey(cellX, cellZ);
        Supercontinent sc = continentCache.get(key);
        if (sc != null) return sc;

        sc = new Supercontinent(worldSeed, cellX, cellZ);
        continentCache.put(key, sc);
        return sc;
    }

    /**
     * 将世界 block 坐标转换为超级单元格坐标 (cellX, cellZ)。
     */
    private int[] getCellCoords(int blockX, int blockZ) {
        int cellX = TectonicMath.floorDiv(blockX, TectonicConfig.SUPER_CELL_SIZE);
        int cellZ = TectonicMath.floorDiv(blockZ, TectonicConfig.SUPER_CELL_SIZE);
        return new int[]{cellX, cellZ};
    }

    /**
     * 给定点所属的大致候选 cell 列表（自身 + 八邻居），用于搜索最近 Supercontinent。
     * 目前采用 3×3，若以后需要更大范围的“全局最近大陆”搜索，可以扩成 5×5。
     */
    private void candidateCellsForPoint(int blockX, int blockZ, int[][] outCells, int[] outCount) {
        int[] cell = getCellCoords(blockX, blockZ);
        int cx = cell[0];
        int cz = cell[1];

        int idx = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                outCells[idx][0] = cx + dx;
                outCells[idx][1] = cz + dz;
                idx++;
            }
        }
        outCount[0] = idx;
    }

    /**
     * 在附近若干超级单元中，找到“最近”的超级大陆，并返回该点相对该超大陆
     * 的有符号海岸距离 signedCoastDistance。
     *
     * 几何定义：
     *   - 对每个候选超级大陆，计算 d = r - R(theta)；
     *   - 选取 |d| 最小的那个作为“概念上最近”的超级大陆；
     *   - outSignedCoastDistance[0] = 该超级大陆下的 d。
     */
    private Supercontinent findNearestSupercontinent(int blockX, int blockZ, double[] outSignedCoastDistance) {
        int[][] cells = new int[9][2];
        int[] n = new int[1];
        candidateCellsForPoint(blockX, blockZ, cells, n);

        double bestAbsSigned = Double.POSITIVE_INFINITY;
        double bestSigned = 0.0;
        Supercontinent bestSc = null;

        for (int i = 0; i < n[0]; i++) {
            int cx = cells[i][0];
            int cz = cells[i][1];
            Supercontinent sc = getSupercontinent(cx, cz);

            double signed = sc.signedCoastDistanceRadial(blockX, blockZ);
            double absSigned = Math.abs(signed);

            if (absSigned < bestAbsSigned) {
                bestAbsSigned = absSigned;
                bestSigned = signed;
                bestSc = sc;
            }
        }

        if (bestSc == null) {
            if (outSignedCoastDistance != null && outSignedCoastDistance.length > 0) {
                outSignedCoastDistance[0] = Double.NEGATIVE_INFINITY;
            }
            return null;
        }

        if (outSignedCoastDistance != null && outSignedCoastDistance.length > 0) {
            outSignedCoastDistance[0] = bestSigned;
        }

        return bestSc;
    }

    /**
     * 对一个方块进行完整 tectonic_v1 语义采样。
     *
     * 返回的 TectonicLandSample 是“构造层”的结果，WorldgenAPI 再把它
     * 换算成 Minecraft 侧的 SampleResult。
     */
    public TectonicLandSample sampleBlock(int blockX, int blockZ) {
        double[] signedCoast = new double[1];
        Supercontinent sc = findNearestSupercontinent(blockX, blockZ, signedCoast);

        if (sc == null) {
            return new TectonicLandSample(
                blockX, blockZ,
                LandType.OCEAN,
                null,
                null,
                Double.NEGATIVE_INFINITY,
                0.0,
                0.0,
                0.0,
                0.0
            );
        }

        double coastSigned = signedCoast[0];
        boolean inside = (coastSigned <= 0.0);
        LandType landType = inside ? LandType.SUPERCONTINENT : LandType.OCEAN;

        double absCoastDist = Math.abs(coastSigned);

        double radial = inside ? sc.radialCenterward(blockX, blockZ) : 0.0;

        double coastBand;
        if (inside) {
            double t = 1.0 - absCoastDist / TectonicConfig.WCOAST_DEFAULT;
            coastBand = TectonicMath.clamp(t, 0.0, 1.0);
        } else {
            coastBand = 0.0;
        }

        double shelfBand;
        if (!inside) {
            double tShelf = 1.0 - absCoastDist / TectonicConfig.WSHELF_DEFAULT;
            shelfBand = TectonicMath.clamp(tShelf, 0.0, 1.0);
        } else {
            shelfBand = 0.0;
        }

        PlateId plateId = sc.getPlateIdForPoint(blockX, blockZ);
        double boundaryWeight = sc.getPlateBoundaryWeight(blockX, blockZ);

        SupercontinentId sid = sc.id;

        return new TectonicLandSample(
            blockX,
            blockZ,
            landType,
            sid,
            plateId,
            coastSigned,
            radial,
            coastBand,
            shelfBand,
            boundaryWeight
        );
    }

    /**
     * 针对某个 Supercontinent，对 chunk 做 FULL_OCEAN / FULL_LAND / MIXED 粗判。
     */
    private ChunkLandClass classifyChunkForSupercontinent(Supercontinent sc, int chunkX, int chunkZ) {
        double cx = sc.centerX;
        double cz = sc.centerZ;

        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;
        int x1 = x0 + 15;
        int z1 = z0 + 15;

        double nearestX = clamp(cx, x0, x1);
        double nearestZ = clamp(cz, z0, z1);
        double dxMin = nearestX - cx;
        double dzMin = nearestZ - cz;
        double minDist = Math.hypot(dxMin, dzMin);

        double dx0z0 = x0 - cx;
        double dz0z0 = z0 - cz;
        double dx1z0 = x1 - cx;
        double dz1z0 = z0 - cz;
        double dx0z1 = x0 - cx;
        double dz0z1 = z1 - cz;
        double dx1z1 = x1 - cx;
        double dz1z1 = z1 - cz;

        double d00 = Math.hypot(dx0z0, dz0z0);
        double d10 = Math.hypot(dx1z0, dz1z0);
        double d01 = Math.hypot(dx0z1, dz0z1);
        double d11 = Math.hypot(dx1z1, dz1z1);

        double maxDist = d00;
        if (d10 > maxDist) maxDist = d10;
        if (d01 > maxDist) maxDist = d01;
        if (d11 > maxDist) maxDist = d11;

        if (minDist > sc.outerSafeRadius) {
            return ChunkLandClass.FULL_OCEAN;
        }
        if (maxDist < sc.innerSafeRadius) {
            return ChunkLandClass.FULL_LAND;
        }

        return ChunkLandClass.MIXED;
    }

    private static double clamp(double v, double lo, double hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /**
     * 为某个 chunk 生成 block 级海陆掩码（16×16 LandMask16）。
     *
     * 逻辑：
     *   1) 用 chunk 中心点找到最近 Supercontinent；
     *   2) 用 inner/outerSafeRadius 对 chunk 做 FULL_LAND / FULL_OCEAN / MIXED 粗判；
     *   3) FULL_LAND 直接 fillLand，FULL_OCEAN 直接 fillOcean；
     *   4) MIXED 情况下，对 16×16 每个方块调用一次 pointInside。
     */
    public LandMask16 buildLandMaskForChunk(int chunkX, int chunkZ) {
        int blockCenterX = chunkX * 16 + 8;
        int blockCenterZ = chunkZ * 16 + 8;

        double[] signedCoast = new double[1];
        Supercontinent sc = findNearestSupercontinent(blockCenterX, blockCenterZ, signedCoast);
        LandMask16 mask = new LandMask16();

        if (sc == null) {
            mask.fillOcean();
            return mask;
        }

        ChunkLandClass cls = classifyChunkForSupercontinent(sc, chunkX, chunkZ);
        if (cls == ChunkLandClass.FULL_OCEAN) {
            mask.fillOcean();
            return mask;
        }
        if (cls == ChunkLandClass.FULL_LAND) {
            mask.fillLand();
            return mask;
        }

        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;

        for (int dz = 0; dz < 16; dz++) {
            int worldZ = z0 + dz;
            for (int dx = 0; dx < 16; dx++) {
                int worldX = x0 + dx;
                boolean inside = sc.pointInside(worldX, worldZ);
                if (inside) {
                    mask.set(dx, dz);
                }
            }
        }

        return mask;
    }

    /**
     * 通过 SupercontinentId 获取对应的 Supercontinent。
     * 主要给调试 / 工具层用。
     */
    public Supercontinent getSupercontinentById(SupercontinentId id) {
        if (id == null) return null;
        return getSupercontinent(id.cellX, id.cellZ);
    }

    /**
     * 返回某个超级大陆的中心整数 block 坐标 (x,z)。
     * 若 id 无效，返回 null。
     */
    public int[] getSuperCenterBlockPos(SupercontinentId id) {
        Supercontinent sc = getSupercontinentById(id);
        if (sc == null) return null;

        int cx = (int) Math.round(sc.centerX);
        int cz = (int) Math.round(sc.centerZ);
        return new int[]{cx, cz};
    }

    /**
     * 返回某个超级大陆的 baseRadius，id 无效时返回 0。
     */
    public double getSuperBaseRadius(SupercontinentId id) {
        Supercontinent sc = getSupercontinentById(id);
        if (sc == null) return 0.0;
        return sc.baseRadius;
    }

    /**
     * 仅做“是否为陆地”的快速判断：
     *   - 不计算海岸 / 大陆架权重；
     *   - 不计算板块 / 边界权重；
     *   - 只看点是否在最近超级大陆轮廓内部（signedCoastDistance <= 0）。
     */
    public boolean isLandFast(int blockX, int blockZ) {
        double[] signedCoast = new double[1];
        Supercontinent sc = findNearestSupercontinent(blockX, blockZ, signedCoast);
        if (sc == null) {
            return false;
        }
        return signedCoast[0] <= 0.0;
    }
}
