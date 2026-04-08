package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.*;

/**
 * =====================================================
 * 类名：WorldgenCore
 * 来源：Python 模块 worldgen_core
 * 功能：
 *   - 实现无限世界地形核心算法（第一层：海陆 + 超级大陆ID + 板块ID）；
 *   - 包含超级大陆中心生成、板块子核生成、陆地判定；
 *   - 完整复刻 Python 逻辑的同时，加入针对百万级大陆的高性能优化：
 *       * SuperPrecomputed 扁平 subCore 数组 + 平均半径缓存；
 *       * 每个超级大陆内部构建空间栅格索引（加速最近 subCore 查询）；
 *       * isSuperLandAt 中只对最终最小距离做 sqrt；
 *       * Chunk 级 LandContext 预计算。
 *   - 对外核心函数：
 *         isLandRaw(x, z, seed)
 * =====================================================
 */

public class WorldgenCore {

    private static final NoiseFamily LAND_NOISE =
        new NoiseFamily(1001, 1.0, 3, 2.0, 0.5);

    public static final class LandEval {
        public final boolean isLand;
        public final int bestContinentId;
        public final int bestSuperId;

        public LandEval(boolean isLand, int bestContinentId, int bestSuperId) {
            this.isLand = isLand;
            this.bestContinentId = bestContinentId;
            this.bestSuperId = bestSuperId;
        }
    }

    private static final class SuperPrecomputed {

        /**
         * 一个 subCore：
         *   - x, z: 世界坐标
         *   - r: 该 subCore 对应的半径
         *   - continentId: 所属板块/子大陆ID
         */
        static final class Core {
            final double x, z, r;
            final int continentId;

            Core(double x, double z, double r, int continentId) {
                this.x = x;
                this.z = z;
                this.r = r;
                this.continentId = continentId;
            }
        }

        final Core[] cores;

        final double avgR;

        final double gridOriginX;
        final double gridOriginZ;
        final double cellSize;
        final int gridWidth;
        final int gridHeight;
        final int[] cellOffsets;
        final int[] cellCounts;
        final int[] cellCoreIndices;

        SuperPrecomputed(Core[] cores,
                         double avgR,
                         double gridOriginX,
                         double gridOriginZ,
                         double cellSize,
                         int gridWidth,
                         int gridHeight,
                         int[] cellOffsets,
                         int[] cellCounts,
                         int[] cellCoreIndices) {
            this.cores = cores;
            this.avgR = avgR;
            this.gridOriginX = gridOriginX;
            this.gridOriginZ = gridOriginZ;
            this.cellSize = cellSize;
            this.gridWidth = gridWidth;
            this.gridHeight = gridHeight;
            this.cellOffsets = cellOffsets;
            this.cellCounts = cellCounts;
            this.cellCoreIndices = cellCoreIndices;
        }
    }

    private static final Int2ObjectOpenHashMap<SuperPrecomputed> SUPER_CACHE
        = new Int2ObjectOpenHashMap<>();

    public static class LandContext {
        /**
         * 当前矩形范围内可能影响海陆的所有超级大陆中心
         */
        public final List<SuperContinentCenter> centers;

        /**
         * 用于这一范围内的地形噪声（共用一份对象，避免重复 new）
         */
        public final NoiseFamily noise;

        public LandContext(List<SuperContinentCenter> centers, NoiseFamily noise) {
            this.centers = centers;
            this.noise = noise;
        }
    }

    private static long packSuperGridKey(int gx, int gz, int worldSeed) {
        long k = (((long) gx) & 0xffffffffL) << 32;
        k |= (((long) gz) & 0xffffL) << 16;
        k |= (worldSeed & 0xffff);
        return k;
    }

    private static final Long2ObjectOpenHashMap<SuperContinentCenter> SUPER_CENTER_CACHE
        = new Long2ObjectOpenHashMap<>();

    private static SuperContinentCenter getSuperCenterForGridCached(int gx, int gz, int worldSeed) {
        long key = packSuperGridKey(gx, gz, worldSeed);
        SuperContinentCenter c = SUPER_CENTER_CACHE.get(key);
        if (c != null) return c;

        c = getSuperCenterForGrid(gx, gz, worldSeed);
        if (c != null) {
            SUPER_CENTER_CACHE.put(key, c);
        }
        return c;
    }

    public static double latitudeWeight(int worldZ) {
        double zMod = ((worldZ % WorldgenConfig.LATITUDE_CYCLE) + WorldgenConfig.LATITUDE_CYCLE) % WorldgenConfig.LATITUDE_CYCLE;
        double t = (zMod / WorldgenConfig.LATITUDE_CYCLE) * 2.0 - 1.0;
        double poleWeight = Math.pow(Math.cos(t * Math.PI * 0.5), 2.0);
        double base = 0.05;
        return base + (1.0 - base) * poleWeight;
    }

    public static SuperContinentCenter getSuperCenterForGrid(int gx, int gz, int worldSeed) {
        double rnd = NoiseUtil.hash2(gx, gz, worldSeed);
        double gridCenterZ = gx * WorldgenConfig.SUPER_GRID_SIZE + WorldgenConfig.SUPER_GRID_SIZE / 2.0;
        double latW = latitudeWeight((int) gridCenterZ);
        double prob = (1.0 / 4.0) * latW;
        if (rnd > prob) return null;

        int seedLocal = (int) (NoiseUtil.hash2(gx, gz, worldSeed + 1) * 1_000_000_000);

        int offsetX = (int) ((NoiseUtil.hash2(gx, gz, seedLocal + 1) - 0.5)
            * WorldgenConfig.SUPER_GRID_SIZE * 0.6);
        int offsetZ = (int) ((NoiseUtil.hash2(gx, gz, seedLocal + 2) - 0.5)
            * WorldgenConfig.SUPER_GRID_SIZE * 0.6);

        int worldX = gx * WorldgenConfig.SUPER_GRID_SIZE + WorldgenConfig.SUPER_GRID_SIZE / 2 + offsetX;
        int worldZ = gz * WorldgenConfig.SUPER_GRID_SIZE + WorldgenConfig.SUPER_GRID_SIZE / 2 + offsetZ;

        int baseR = (int) (WorldgenConfig.SUPER_MIN_RADIUS +
            (WorldgenConfig.SUPER_MAX_RADIUS - WorldgenConfig.SUPER_MIN_RADIUS)
                * NoiseUtil.hash2(gx, gz, seedLocal + 3));

        double angle = NoiseUtil.hash2(gx, gz, seedLocal + 4) * 2.0 * Math.PI;
        double stretchMajor = 0.6 + 0.3 * NoiseUtil.hash2(gx, gz, seedLocal + 5);
        double stretchMinor = 1.0 + 0.4 * NoiseUtil.hash2(gx, gz, seedLocal + 6);
        int superId = WorldgenMath.makeSuperId(gx, gz, worldSeed);

        int smoothSideSign = NoiseUtil.hash2(gx, gz, seedLocal + 7) > 0.5 ? 1 : -1;
        double smoothStrength = 0.8 + 0.3 * NoiseUtil.hash2(gx, gz, seedLocal + 8);
        double smoothHalfAngle = Math.toRadians(60.0 + 20.0 * NoiseUtil.hash2(gx, gz, seedLocal + 9));

        return new SuperContinentCenter(
            worldX, worldZ, baseR, angle, stretchMajor, stretchMinor,
            seedLocal + 1000, superId, smoothSideSign, smoothStrength, smoothHalfAngle
        );
    }

    public static List<SuperContinentCenter> getCandidateCentersForRect(
        int xMin, int zMin, int xMax, int zMax, int worldSeed) {

        int xMinExt = xMin - WorldgenConfig.SUPER_MAX_INFLUENCE;
        int xMaxExt = xMax + WorldgenConfig.SUPER_MAX_INFLUENCE;
        int zMinExt = zMin - WorldgenConfig.SUPER_MAX_INFLUENCE;
        int zMaxExt = zMax + WorldgenConfig.SUPER_MAX_INFLUENCE;

        // 用整数 floorDiv，避免 double + Math.floor
        int gxMin = Math.floorDiv(xMinExt, WorldgenConfig.SUPER_GRID_SIZE);
        int gxMax = Math.floorDiv(xMaxExt, WorldgenConfig.SUPER_GRID_SIZE);
        int gzMin = Math.floorDiv(zMinExt, WorldgenConfig.SUPER_GRID_SIZE);
        int gzMax = Math.floorDiv(zMaxExt, WorldgenConfig.SUPER_GRID_SIZE);

        int gxCount = gxMax - gxMin + 1;
        int gzCount = gzMax - gzMin + 1;
        int estSize = gxCount * gzCount;

        List<SuperContinentCenter> result = new ArrayList<>(estSize);

        final int maxDistSq = WorldgenConfig.SUPER_MAX_INFLUENCE * WorldgenConfig.SUPER_MAX_INFLUENCE;

        for (int gx = gxMin; gx <= gxMax; gx++) {
            for (int gz = gzMin; gz <= gzMax; gz++) {
                SuperContinentCenter c = getSuperCenterForGridCached(gx, gz, worldSeed);
                if (c == null) continue;

                int dx = 0, dz = 0;
                if (c.worldX < xMin) dx = xMin - c.worldX;
                else if (c.worldX > xMax) dx = c.worldX - xMax;

                if (c.worldZ < zMin) dz = zMin - c.worldZ;
                else if (c.worldZ > zMax) dz = c.worldZ - zMax;

                int dist2 = dx * dx + dz * dz;
                if (dist2 <= maxDistSq) {
                    result.add(c);
                }
            }
        }
        return result;
    }

    public static List<PlateCenter> generatePlateCentersForSuper(SuperContinentCenter center) {
        List<PlateCenter> plates = new ArrayList<>();
        Random rng = new Random(center.subCoreSeed);

        int superId = center.superId;
        double superR = center.baseRadius;

        double mainOffsetR = superR * 0.1;
        double mainAngle = rng.nextDouble() * Math.PI * 2.0;
        double mainX = center.worldX + Math.cos(mainAngle) * mainOffsetR;
        double mainZ = center.worldZ + Math.sin(mainAngle) * mainOffsetR;
        double mainR = superR * 1.1;

        PlateCenter main = new PlateCenter(superId, center.mainContinentId, mainX, mainZ, mainR);
        plates.add(main);

        int nSub = 2 + rng.nextInt(3); // 2~4
        double minD = superR * 0.35;
        double maxD = superR * 0.75;
        double baseAngle = rng.nextDouble() * Math.PI * 2.0;

        for (int i = 0; i < nSub; i++) {
            double ang = baseAngle + (Math.PI * 2.0 * i / Math.max(1, nSub))
                + rng.nextDouble() * 0.8 - 0.4;
            double dist = minD + rng.nextDouble() * (maxD - minD);
            double px = center.worldX + Math.cos(ang) * dist;
            double pz = center.worldZ + Math.sin(ang) * dist;
            double pr = superR * (0.7 + rng.nextDouble() * 0.5);

            int localIndex = i + 1;
            int cid = WorldgenMath.makeSubContinentId(superId, localIndex);
            plates.add(new PlateCenter(superId, cid, px, pz, pr));
        }
        return plates;
    }

    public static List<double[]> generateSubCoresForPlate(PlateCenter plate, double baseRadius) {
        List<double[]> subCores = new ArrayList<>();

        long raw = (long) (plate.worldX * 374761393L)
            + (long) (plate.worldZ * 668265263L)
            + plate.continentId * 69069L;
        Random rng = new Random((int) (raw & 0x7FFFFFFFL));

        int segCount = 4 + rng.nextInt(5);
        double skeletonTotalLen = (2.0 + 0.8 * rng.nextDouble()) * plate.radius;
        double segLen = skeletonTotalLen / Math.max(1, segCount);

        double sx = plate.worldX;
        double sz = plate.worldZ;
        double baseDir = rng.nextDouble() * Math.PI * 2.0;

        List<double[]> nodes = new ArrayList<>();
        nodes.add(new double[]{sx, sz});
        double cx = sx, cz = sz;
        double curAngle = baseDir;

        for (int i = 0; i < segCount; i++) {
            double bend = (rng.nextDouble() - 0.5) * (Math.PI / 1.5);
            double t = i / Math.max(1.0, segCount - 1.0);
            curAngle = baseDir * (1.0 - 0.3 * t) + (baseDir + bend) * (0.3 * t);
            cx += Math.cos(curAngle) * segLen;
            cz += Math.sin(curAngle) * segLen;
            nodes.add(new double[]{cx, cz});
        }

        for (int i = 0; i < segCount; i++) {
            if (rng.nextDouble() > 0.55) continue;
            double[] a = nodes.get(i);
            double[] b = nodes.get(Math.min(i + 1, segCount));
            double dir = Math.atan2(b[1] - a[1], b[0] - a[0]);

            double branchAngle = dir + (rng.nextDouble() - 0.5) * (Math.PI * 0.7);
            double branchLen = skeletonTotalLen * (0.3 + rng.nextDouble() * 0.6);
            int branchSegCount = Math.max(2, (int) (branchLen / segLen));
            double bx = a[0], bz = a[1];
            double curBAngle = branchAngle;

            for (int j = 0; j < branchSegCount; j++) {
                double bendB = (rng.nextDouble() - 0.5) * (Math.PI / 2.0);
                double t = j / Math.max(1.0, branchSegCount - 1.0);
                curBAngle = branchAngle * (1.0 - 0.5 * t) + (branchAngle + bendB) * (0.5 * t);
                bx += Math.cos(curBAngle) * segLen;
                bz += Math.sin(curBAngle) * segLen;
                nodes.add(new double[]{bx, bz});
            }
        }

        int subCount = 6 + rng.nextInt(7);
        for (int i = 0; i < subCount; i++) {
            int nid = rng.nextInt(nodes.size());
            double[] p = nodes.get(nid);

            double sideOffset = (rng.nextDouble() - 0.5) * 1.2 * plate.radius;

            double dx = p[0] - plate.worldX;
            double dz = p[1] - plate.worldZ;
            double dist = Math.hypot(dx, dz) + 1e-6;
            double tx = dx / dist;
            double tz = dz / dist;
            double nx = -tz;
            double nz = tx;

            double scx = p[0] + nx * sideOffset;
            double scz = p[1] + nz * sideOffset;

            double scale = plate.radius / Math.max(1e-6, baseRadius);
            double subR = baseRadius * scale * (0.9 + (0.9 * Math.pow(rng.nextDouble(), 0.55)));

            subCores.add(new double[]{scx, scz, subR});
        }

        return subCores;
    }

    private static SuperPrecomputed buildSuperPrecomputed(SuperContinentCenter center) {
        List<PlateCenter> plates = generatePlateCentersForSuper(center);

        List<SuperPrecomputed.Core> coreList = new ArrayList<>();
        double sumR = 0.0;
        int count = 0;

        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for (PlateCenter p : plates) {
            List<double[]> subCores = generateSubCoresForPlate(p, center.baseRadius);
            for (double[] sc : subCores) {
                double x = sc[0];
                double z = sc[1];
                double r = sc[2];

                coreList.add(new SuperPrecomputed.Core(x, z, r, p.continentId));
                sumR += r;
                count++;

                if (x < minX) minX = x;
                if (x > maxX) maxX = x;
                if (z < minZ) minZ = z;
                if (z > maxZ) maxZ = z;
            }
        }

        SuperPrecomputed.Core[] cores =
            coreList.toArray(new SuperPrecomputed.Core[0]);

        if (count == 0) {
            return new SuperPrecomputed(
                cores,
                0.0,
                0.0, 0.0, 0.0,
                0, 0,
                null, null, null
            );
        }

        double avgR = sumR / count;

        double margin = center.baseRadius * 0.3;
        minX -= margin;
        maxX += margin;
        minZ -= margin;
        maxZ += margin;

        double width = Math.max(1.0, maxX - minX);
        double height = Math.max(1.0, maxZ - minZ);

        double cellSize = Math.max(8000.0, center.baseRadius / 4.0);

        int gridWidth = Math.max(1, (int) Math.ceil(width / cellSize));
        int gridHeight = Math.max(1, (int) Math.ceil(height / cellSize));
        int cellCount = gridWidth * gridHeight;

        int[] cellCounts = new int[cellCount];

        for (int i = 0; i < cores.length; i++) {
            SuperPrecomputed.Core c = cores[i];
            int gx = (int) Math.floor((c.x - minX) / cellSize);
            int gz = (int) Math.floor((c.z - minZ) / cellSize);
            if (gx < 0) gx = 0;
            if (gx >= gridWidth) gx = gridWidth - 1;
            if (gz < 0) gz = 0;
            if (gz >= gridHeight) gz = gridHeight - 1;
            int idx = gz * gridWidth + gx;
            cellCounts[idx]++;
        }

        int[] cellOffsets = new int[cellCount];
        int total = 0;
        for (int i = 0; i < cellCount; i++) {
            cellOffsets[i] = total;
            total += cellCounts[i];
        }

        int[] cellCoreIndices = new int[total];
        int[] writePos = cellOffsets.clone();

        for (int i = 0; i < cores.length; i++) {
            SuperPrecomputed.Core c = cores[i];
            int gx = (int) Math.floor((c.x - minX) / cellSize);
            int gz = (int) Math.floor((c.z - minZ) / cellSize);
            if (gx < 0) gx = 0;
            if (gx >= gridWidth) gx = gridWidth - 1;
            if (gz < 0) gz = 0;
            if (gz >= gridHeight) gz = gridHeight - 1;
            int idx = gz * gridWidth + gx;
            int pos = writePos[idx]++;
            cellCoreIndices[pos] = i;
        }

        return new SuperPrecomputed(
            cores,
            avgR,
            minX,
            minZ,
            cellSize,
            gridWidth,
            gridHeight,
            cellOffsets,
            cellCounts,
            cellCoreIndices
        );
    }

    private static SuperPrecomputed getPrecomputedForSuper(SuperContinentCenter center) {
        int superId = center.superId;
        SuperPrecomputed cached = SUPER_CACHE.get(superId);
        if (cached != null) return cached;

        SuperPrecomputed built = buildSuperPrecomputed(center);
        SuperPrecomputed prev = SUPER_CACHE.putIfAbsent(superId, built);
        return prev != null ? prev : built;
    }

    public static LandEval isSuperLandAt(
        double x, double z,
        List<SuperContinentCenter> centers,
        NoiseFamily noiseFamily) {

        double lowFreq = noiseFamily.get(x * WorldgenConfig.BIG_FREQ_SCALE, z * WorldgenConfig.BIG_FREQ_SCALE);
        double highFreq = noiseFamily.get(x * WorldgenConfig.SMALL_FREQ_SCALE, z * WorldgenConfig.SMALL_FREQ_SCALE);

        double bestScore = -1e9;
        int bestCont = 0;
        int bestSuper = 0;

        for (SuperContinentCenter c : centers) {
            double dx0 = x - c.worldX;
            double dz0 = z - c.worldZ;
            if (dx0 * dx0 + dz0 * dz0 > WorldgenConfig.SUPER_MAX_INFLUENCE * WorldgenConfig.SUPER_MAX_INFLUENCE) {
                continue;
            }

            int superId = c.superId;

            SuperPrecomputed pre = getPrecomputedForSuper(c);
            SuperPrecomputed.Core[] cores = pre.cores;
            if (cores.length == 0) continue;

            double dist2 = dx0 * dx0 + dz0 * dz0;
            double smoothFactor = 1.0;
            if (dist2 > 1.0) {
                double dist = Math.sqrt(dist2);
                double ux = dx0 / dist;
                double uz = dz0 / dist;

                double ax = c.cosAngle;
                double az = c.sinAngle;
                double nx1 = -az, nz1 = ax;
                double nx2 = az, nz2 = -ax;
                double sxDir = (c.smoothSideSign > 0 ? nx1 : nx2);
                double szDir = (c.smoothSideSign > 0 ? nz1 : nz2);

                double dot = ux * sxDir + uz * szDir;
                dot = Math.max(-1.0, Math.min(1.0, dot));
                double cosHalf = c.cosSmoothHalfAngle;
                if (dot >= cosHalf) {
                    double t = (cosHalf < 1.0) ? (cosHalf - dot) / (cosHalf - 1.0) : 1.0;
                    t = Math.max(0.0, Math.min(1.0, t));
                    smoothFactor = (1.0 - c.smoothStrength) * (1.0 - t) + t;
                }
            }

            double minDistSq = Double.POSITIVE_INFINITY;
            int minCont = 0;
            boolean anyVisited = false;

            if (pre.gridWidth > 0 && pre.gridHeight > 0 && pre.cellCoreIndices != null) {
                double gx = (x - pre.gridOriginX) / pre.cellSize;
                double gz = (z - pre.gridOriginZ) / pre.cellSize;
                int cx = (int) Math.floor(gx);
                int cz = (int) Math.floor(gz);

                final int searchRadius = 2;

                int gw = pre.gridWidth;
                int gh = pre.gridHeight;

                for (int dzCell = -searchRadius; dzCell <= searchRadius; dzCell++) {
                    int gzCell = cz + dzCell;
                    if (gzCell < 0 || gzCell >= gh) continue;

                    for (int dxCell = -searchRadius; dxCell <= searchRadius; dxCell++) {
                        int gxCell = cx + dxCell;
                        if (gxCell < 0 || gxCell >= gw) continue;

                        int cellIndex = gzCell * gw + gxCell;
                        int count = pre.cellCounts[cellIndex];
                        if (count <= 0) continue;

                        anyVisited = true;
                        int offset = pre.cellOffsets[cellIndex];
                        int end = offset + count;

                        for (int i = offset; i < end; i++) {
                            int coreIndex = pre.cellCoreIndices[i];
                            SuperPrecomputed.Core core = cores[coreIndex];

                            double dx = x - core.x;
                            double dz = z - core.z;
                            double d2 = dx * dx + dz * dz;
                            if (d2 < minDistSq) {
                                minDistSq = d2;
                                minCont = core.continentId;
                            }
                        }
                    }
                }
            }

            if (!anyVisited || minCont == 0) {
                for (SuperPrecomputed.Core core : cores) {
                    double dx = x - core.x;
                    double dz = z - core.z;
                    double d2 = dx * dx + dz * dz;
                    if (d2 < minDistSq) {
                        minDistSq = d2;
                        minCont = core.continentId;
                    }
                }
            }

            if (minCont == 0 || !Double.isFinite(minDistSq)) {
                continue;
            }

            double minDist = Math.sqrt(minDistSq);

            double baseR = pre.avgR * 0.85;

            double deltaBig = (lowFreq - 0.5) * baseR * WorldgenConfig.BIG_AMP_RATIO * smoothFactor;
            double deltaSmall = (highFreq - 0.5) * baseR * WorldgenConfig.SMALL_AMP_RATIO * smoothFactor;
            double angle = Math.atan2(dz0, dx0);
            double dirHash = Math.sin(angle * 2.5 + superId * 0.001);
            double deltaDir = dirHash * baseR * 0.12 * smoothFactor;

            double effR = Math.max(50.0, baseR + deltaBig + deltaSmall + deltaDir);
            double normDist = minDist / effR;
            double v = 1.0 - normDist;
            v = v > 0 ? v * v : v * 1.5;

            if (v > 0 && v > bestScore) {
                bestScore = v;
                bestCont = minCont;
                bestSuper = superId;
            }
        }

        boolean isLand = bestCont != 0;
        return new LandEval(isLand, bestCont, bestSuper);
    }

    public static LandContext prepareLandContextForRect(
        int xMin, int zMin, int xMax, int zMax, int worldSeed) {

        List<SuperContinentCenter> centers =
            getCandidateCentersForRect(xMin, zMin, xMax, zMax, worldSeed);

        if (centers.isEmpty()) {
            return new LandContext(Collections.emptyList(), LAND_NOISE);
        }
        return new LandContext(centers, LAND_NOISE);
    }

    public static LandResult isLandWithContext(int x, int z, LandContext ctx) {
        if (ctx.centers.isEmpty()) {
            return new LandResult(false, 0, 0);
        }
        LandEval eval = isSuperLandAt(x, z, ctx.centers, ctx.noise);
        if (!eval.isLand) {
            return new LandResult(false, 0, 0);
        }
        return new LandResult(true, eval.bestContinentId, eval.bestSuperId);
    }

    public static LandResult isLandRaw(int x, int z, int worldSeed) {
        LandContext ctx = prepareLandContextForRect(x, z, x, z, worldSeed);
        return isLandWithContext(x, z, ctx);
    }

    public static class LandResult {
        public boolean isLand;
        public int plateId;
        public int superId;

        public LandResult(boolean land, int plate, int sup) {
            this.isLand = land;
            this.plateId = plate;
            this.superId = sup;
        }
    }
}
