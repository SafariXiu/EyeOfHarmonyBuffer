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

    /** (cellX,cellZ) → Supercontinent 的简单缓存 */
    private final Map<Long, Supercontinent> continentCache =
        new HashMap<Long, Supercontinent>();

    public TectonicWorld(long worldSeed) {
        this.worldSeed = worldSeed;
    }

    private static long packCellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    private Supercontinent getSupercontinent(int cellX, int cellZ) {
        long key = packCellKey(cellX, cellZ);
        Supercontinent sc = continentCache.get(key);
        if (sc != null) return sc;

        sc = new Supercontinent(worldSeed, cellX, cellZ);
        continentCache.put(key, sc);
        return sc;
    }

    private int[] getCellCoords(int blockX, int blockZ) {
        int cellX = TectonicMath.floorDiv(blockX, TectonicConfig.SUPER_CELL_SIZE);
        int cellZ = TectonicMath.floorDiv(blockZ, TectonicConfig.SUPER_CELL_SIZE);
        return new int[]{cellX, cellZ};
    }

    /**
     * 给定点所属的大致候选 cell 列表（自身 + 八邻居）。
     * 目前采用 3×3，后续如果要做“最近超级大陆”之类全局查询，可以扩成 5×5。
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
     * 在附近若干超级单元中，找到“海岸有符号距离绝对值最小”的那个超级大陆，
     * 并返回对应的 Supercontinent。
     *
     * signedCoastDistance:
     *   - >0：当前点在该超大陆内部，数值为到海岸折线的距离；
     *   - <0：当前点在该超大陆外部，数值为到海岸折线距离 + 负号；
     *   - 绝对值越小，离海岸越近。
     */
    private Supercontinent findNearestSupercontinent(int blockX, int blockZ, double[] outSignedCoastDistance) {
        int[][] cells = new int[9][2];
        int[] n = new int[1];
        candidateCellsForPoint(blockX, blockZ, cells, n);

        double bestAbsDist = Double.POSITIVE_INFINITY;
        boolean bestInside = false;
        Supercontinent bestSc = null;

        for (int i = 0; i < n[0]; i++) {
            int cx = cells[i][0];
            int cz = cells[i][1];
            Supercontinent sc = getSupercontinent(cx, cz);

            double dist = sc.distanceToCoast(blockX, blockZ); // 总是非负
            boolean inside = sc.pointInside(blockX, blockZ);

            double signed = inside ? dist : -dist;
            double absSigned = Math.abs(signed);

            if (absSigned < bestAbsDist) {
                bestAbsDist = absSigned;
                bestInside = inside;
                bestSc = sc;
                if (outSignedCoastDistance != null && outSignedCoastDistance.length > 0) {
                    outSignedCoastDistance[0] = signed;
                }
            }
        }

        if (bestSc == null) {
            if (outSignedCoastDistance != null && outSignedCoastDistance.length > 0) {
                outSignedCoastDistance[0] = Double.NEGATIVE_INFINITY;
            }
            return null;
        }

        // 重新写回“最佳”那一个的符号距离
        if (outSignedCoastDistance != null && outSignedCoastDistance.length > 0) {
            double sign = bestInside ? 1.0 : -1.0;
            outSignedCoastDistance[0] = sign * bestAbsDist;
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
            // 没有任何超级大陆（理论上不会发生），视为深海
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

        boolean inside = sc.pointInside(blockX, blockZ);
        LandType landType = inside ? LandType.SUPERCONTINENT : LandType.OCEAN;

        double coastSigned = signedCoast[0];
        double absCoastDist = Math.abs(coastSigned);

        // 径向中心度：只在陆地上有意义
        double radial = inside ? sc.radialCenterward(blockX, blockZ) : 0.0;

        // 海岸带权重（陆地侧）
        double coastBand;
        if (inside) {
            double t = 1.0 - absCoastDist / TectonicConfig.WCOAST_DEFAULT;
            coastBand = TectonicMath.clamp(t, 0.0, 1.0);
        } else {
            coastBand = 0.0;
        }

        // 大陆架带权重（海洋侧）
        double shelfBand;
        if (!inside) {
            double tShelf = 1.0 - absCoastDist / TectonicConfig.WSHELF_DEFAULT;
            shelfBand = TectonicMath.clamp(tShelf, 0.0, 1.0);
        } else {
            shelfBand = 0.0;
        }

        // 板块 ID + 边界粗权重（即 V1 文档里的“plateBoundaryProximity”的简化版）
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

        // 到 AABB 的最近点
        double nearestX = clamp(cx, x0, x1);
        double nearestZ = clamp(cz, z0, z1);
        double dxMin = nearestX - cx;
        double dzMin = nearestZ - cz;
        double minDist = Math.hypot(dxMin, dzMin);

        // 四角的最大距离
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

    public double getSuperBaseRadius(SupercontinentId id) {
        Supercontinent sc = getSupercontinentById(id);
        if (sc == null) return 0.0;
        return sc.baseRadius;
    }

    /**
     * 仅做“是否为陆地”的快速判断：
     *   - 不计算海岸 / 大陆架权重；
     *   - 不计算板块 / 边界权重；
     *   - 只看点是否在最近超级大陆的多边形内部。
     */
    public boolean isLandFast(int blockX, int blockZ) {
        double[] signedCoast = new double[1];
        Supercontinent sc = findNearestSupercontinent(blockX, blockZ, signedCoast);
        if (sc == null) {
            return false;
        }
        return sc.pointInside(blockX, blockZ);
    }
}
