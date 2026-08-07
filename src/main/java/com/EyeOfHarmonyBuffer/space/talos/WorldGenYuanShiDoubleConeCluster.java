package com.EyeOfHarmonyBuffer.space.talos;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.space.talos.chunk.terrain_layer.api.TalosTerrainHeights;
import net.minecraft.block.Block;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 源石双锥簇：以区域触发，区域内多个晶体簇；每个簇一根主轴 + 数根副晶，
 * 全部由 YuanShiBlock 组成。
 *
 * 跨区块完整性：簇的几何完全由 (worldSeed, 拥有区块) 确定，任意区块都能重建
 * 同一份簇数据，并只放置自己 16×16 范围内的切片。这样无论区块以什么顺序生成，
 * 每个与晶簇相交的区块都会补齐自己那份，不会在区块边界被截断。
 */
public class WorldGenYuanShiDoubleConeCluster {

    private static final int REGION_TRIGGER_DIVISOR = 800;

    private static final int MIN_CLUSTERS = 3;
    private static final int MAX_CLUSTERS = 6;

    /** 每个簇：1 根主轴 + 4~8 根副晶。 */
    private static final int MIN_CONES = 5;
    private static final int MAX_CONES = 9;

    private static final double CLUSTER_DIST_MIN = 24.0;
    private static final double CLUSTER_DIST_MAX = 60.0;

    private static final double MAIN_HALF_LENGTH = 40.0;
    private static final double MAIN_RADIUS = 7.0;
    private static final double MAIN_TILT_MIN_DEG = 25.0;
    private static final double MAIN_TILT_MAX_DEG = 60.0;
    private static final double MAIN_CENTER_ALONG = 6.0;

    private static final double SECONDARY_SCALE_MIN = 0.45;
    private static final double SECONDARY_SCALE_MAX = 0.70;
    private static final double SECONDARY_ALONG_MIN = -8.0;
    private static final double SECONDARY_ALONG_MAX = 30.0;
    private static final double SECONDARY_SIDE_MAX = 8.0;
    private static final double SECONDARY_LIFT_MAX = 4.0;
    private static final double SECONDARY_TILT_MIN_DEG = 20.0;
    private static final double SECONDARY_TILT_MAX_DEG = 40.0;

    /** 搜索“可能覆盖当前区块”的拥有区块半径（块）：晶体最远约伸到拥有区块外 3 块。 */
    private static final int OWNER_SEARCH_RADIUS_CHUNKS = 3;

    /** 同区域内簇落点的最小间距（blocks），避免新簇落在已有晶体上。 */
    private static final double MIN_CLUSTER_GAP = 36.0;

    private static final class Cone {
        final double cx;
        final double cy;
        final double cz;
        final double ax;
        final double ay;
        final double az;
        final double p1x;
        final double p1y;
        final double p1z;
        final double p2x;
        final double p2y;
        final double p2z;
        final double halfLength;
        final double radius;

        Cone(double cx, double cy, double cz,
             double ax, double ay, double az,
             double[] perp, double[] perp2,
             double halfLength, double radius) {
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
            this.ax = ax;
            this.ay = ay;
            this.az = az;
            this.p1x = perp[0];
            this.p1y = perp[1];
            this.p1z = perp[2];
            this.p2x = perp2[0];
            this.p2y = perp2[1];
            this.p2z = perp2[2];
            this.halfLength = halfLength;
            this.radius = radius;
        }
    }

    /** 拥有区块 -> 该区域内所有已接受簇的锥体列表（空列表 = 未触发）。 */
    private static final ConcurrentHashMap<Long, List<Cone>> CACHE =
        new ConcurrentHashMap<Long, List<Cone>>();

    public void generate(World world, Random rand, int chunkX, int chunkZ) {
        int seed = (int) (world.getSeed() & 0x7FFFFFFFL);

        for (int dz = -OWNER_SEARCH_RADIUS_CHUNKS;
             dz <= OWNER_SEARCH_RADIUS_CHUNKS; dz++) {
            for (int dx = -OWNER_SEARCH_RADIUS_CHUNKS;
                 dx <= OWNER_SEARCH_RADIUS_CHUNKS; dx++) {
                int ownerX = chunkX + dx;
                int ownerZ = chunkZ + dz;
                long key = ownerKey(seed, ownerX, ownerZ);

                List<Cone> cones = CACHE.get(key);
                if (cones == null) {
                    cones = ensureOwner(world, seed, ownerX, ownerZ, key);
                }
                if (cones != null && !cones.isEmpty()) {
                    placeSlice(world, chunkX, chunkZ, cones);
                }
            }
        }
    }

    private List<Cone> ensureOwner(World world, int seed,
                                   int ownerX, int ownerZ, long key) {
        Random r = new Random(key);
        if (r.nextInt(REGION_TRIGGER_DIVISOR) != 0) {
            CACHE.putIfAbsent(key, new ArrayList<Cone>(0));
            return CACHE.get(key);
        }

        List<Cone> built = buildOwnerClusters(seed, r, ownerX, ownerZ);
        List<Cone> prev = CACHE.putIfAbsent(key, built);
        return prev != null ? prev : built;
    }

    private List<Cone> buildOwnerClusters(int seed, Random r,
                                          int ownerX, int ownerZ) {
        int centerX = ownerX * 16 + 8;
        int centerZ = ownerZ * 16 + 8;
        int clusterCount = MIN_CLUSTERS
            + r.nextInt(MAX_CLUSTERS - MIN_CLUSTERS + 1);

        List<Cone> all = new ArrayList<Cone>();
        java.util.ArrayList<int[]> acceptedAnchors =
            new java.util.ArrayList<int[]>();
        for (int i = 0; i < clusterCount; i++) {
            double angle = r.nextDouble() * Math.PI * 2.0D;
            double dist = CLUSTER_DIST_MIN + r.nextDouble()
                * (CLUSTER_DIST_MAX - CLUSTER_DIST_MIN);
            int anchorX = centerX + (int) Math.round(Math.cos(angle) * dist);
            int anchorZ = centerZ + (int) Math.round(Math.sin(angle) * dist);

            // 确定性几何间距：不与同区域已接受的簇重叠
            if (tooCloseToAccepted(anchorX, anchorZ, acceptedAnchors)) {
                continue;
            }

            // 直接读地形高度场：不含树 / 装饰，任何区块复算结果一致
            int anchorY = terrainGroundY(seed, anchorX, anchorZ);
            if (anchorY <= 70 || !isFlatTerrain(seed, anchorX, anchorZ)) {
                continue;
            }
            acceptedAnchors.add(new int[]{anchorX, anchorZ});
            buildClusterCones(r, anchorX, anchorY, anchorZ, all);
        }
        return all;
    }

    private int terrainGroundY(int seed, int x, int z) {
        try {
            return (int) Math.round(
                TalosTerrainHeights.sample(x, z, seed, 64, 256).surfaceD);
        } catch (Throwable t) {
            return -1; // 极端情况（模板未加载等）：放弃该落点
        }
    }

    private boolean isFlatTerrain(int seed, int ax, int az) {
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                int y = terrainGroundY(seed, ax + dx, az + dz);
                if (y <= 0) {
                    return false;
                }
                if (y < minY) minY = y;
                if (y > maxY) maxY = y;
            }
        }
        return (maxY - minY) <= 4;
    }

    private boolean tooCloseToAccepted(int ax, int az,
                                       java.util.List<int[]> anchors) {
        for (int[] a : anchors) {
            double dx = ax - a[0];
            double dz = az - a[1];
            if (dx * dx + dz * dz < MIN_CLUSTER_GAP * MIN_CLUSTER_GAP) {
                return true;
            }
        }
        return false;
    }

    private void buildClusterCones(Random r,
                                   int anchorX, int anchorY, int anchorZ,
                                   List<Cone> out) {
        double mainTilt = Math.toRadians(MAIN_TILT_MIN_DEG
            + r.nextDouble() * (MAIN_TILT_MAX_DEG - MAIN_TILT_MIN_DEG));
        double phi = r.nextDouble() * Math.PI * 2.0D;
        double vx = Math.sin(mainTilt) * Math.cos(phi);
        double vy = Math.cos(mainTilt);
        double vz = Math.sin(mainTilt) * Math.sin(phi);

        double[] perp = perpendicular(vx, vy, vz);
        double[] perp2 = cross(vx, vy, vz, perp[0], perp[1], perp[2]);

        double baseX = anchorX + 0.5D;
        double baseY = anchorY;
        double baseZ = anchorZ + 0.5D;

        int coneCount = MIN_CONES
            + r.nextInt(MAX_CONES - MIN_CONES + 1);

        double mainCx = baseX + vx * MAIN_CENTER_ALONG;
        double mainCy = baseY + vy * MAIN_CENTER_ALONG;
        double mainCz = baseZ + vz * MAIN_CENTER_ALONG;
        out.add(new Cone(mainCx, mainCy, mainCz,
            vx, vy, vz, perp, perp2, MAIN_HALF_LENGTH, MAIN_RADIUS));

        for (int i = 1; i < coneCount; i++) {
            double along = SECONDARY_ALONG_MIN + r.nextDouble()
                * (SECONDARY_ALONG_MAX - SECONDARY_ALONG_MIN);
            double side = (r.nextDouble() - 0.5D) * SECONDARY_SIDE_MAX;
            double lift = (r.nextDouble() - 0.5D) * SECONDARY_LIFT_MAX;

            double cx = baseX + vx * along + perp[0] * side + perp2[0] * lift;
            double cy = baseY + vy * along + perp[1] * side + perp2[1] * lift;
            double cz = baseZ + vz * along + perp[2] * side + perp2[2] * lift;

            double scale = SECONDARY_SCALE_MIN + r.nextDouble()
                * (SECONDARY_SCALE_MAX - SECONDARY_SCALE_MIN);
            double halfLength = MAIN_HALF_LENGTH * scale;
            double radius = MAIN_RADIUS * scale;

            double tiltAngle = Math.toRadians(SECONDARY_TILT_MIN_DEG
                + r.nextDouble()
                    * (SECONDARY_TILT_MAX_DEG - SECONDARY_TILT_MIN_DEG));
            double rotPhi = r.nextDouble() * Math.PI * 2.0D;
            double ux = perp[0] * Math.cos(rotPhi) + perp2[0] * Math.sin(rotPhi);
            double uy = perp[1] * Math.cos(rotPhi) + perp2[1] * Math.sin(rotPhi);
            double uz = perp[2] * Math.cos(rotPhi) + perp2[2] * Math.sin(rotPhi);
            double[] s = rotateAround(vx, vy, vz, ux, uy, uz, tiltAngle);

            double[] sperp = perpendicular(s[0], s[1], s[2]);
            double[] sperp2 = cross(s[0], s[1], s[2],
                sperp[0], sperp[1], sperp[2]);
            out.add(new Cone(cx, cy, cz,
                s[0], s[1], s[2], sperp, sperp2, halfLength, radius));
        }
    }

    /** 只放置当前区块 16×16 范围内的切片。 */
    private void placeSlice(World world, int chunkX, int chunkZ,
                            List<Cone> cones) {
        Block yuanShi = GTCMItemList.YuanShiBlock.getBlock();
        if (yuanShi == null) {
            return;
        }

        final int chunkMinX = chunkX * 16;
        final int chunkMaxX = chunkX * 16 + 15;
        final int chunkMinZ = chunkZ * 16;
        final int chunkMaxZ = chunkZ * 16 + 15;
        final int worldMinY = 1;
        final int worldMaxY = world.getActualHeight() - 2;
        final int FACES = 6;
        final double halfAngle = Math.PI / FACES;

        for (Cone c : cones) {
            double spanX = Math.abs(c.ax) * c.halfLength + c.radius;
            double spanY = Math.abs(c.ay) * c.halfLength + c.radius;
            double spanZ = Math.abs(c.az) * c.halfLength + c.radius;

            int xMin = Math.max(chunkMinX,
                (int) Math.floor(c.cx - spanX));
            int xMax = Math.min(chunkMaxX,
                (int) Math.ceil(c.cx + spanX));
            int zMin = Math.max(chunkMinZ,
                (int) Math.floor(c.cz - spanZ));
            int zMax = Math.min(chunkMaxZ,
                (int) Math.ceil(c.cz + spanZ));
            int yMin = Math.max(worldMinY,
                (int) Math.floor(c.cy - spanY));
            int yMax = Math.min(worldMaxY,
                (int) Math.ceil(c.cy + spanY));
            if (xMin > xMax || zMin > zMax || yMin > yMax) {
                continue;
            }

            double denom = 1.0 - c.ay * c.ay;
            boolean useColumnReject = Math.abs(denom) > 1.0e-4;

            for (int x = xMin; x <= xMax; x++) {
                double dx = x + 0.5D - c.cx;
                for (int z = zMin; z <= zMax; z++) {
                    double dz = z + 0.5D - c.cz;
                    double B = dx * c.ax - c.cy * c.ay + dz * c.az;

                    if (useColumnReject) {
                        double uStar = (c.cy + c.ay * B) / denom;
                        double d2Min = dx * dx + dz * dz
                            + (uStar - c.cy) * (uStar - c.cy)
                            - (B + uStar * c.ay) * (B + uStar * c.ay);
                        if (d2Min > c.radius * c.radius) {
                            continue;
                        }
                    }

                    double uLo;
                    double uHi;
                    if (Math.abs(c.ay) < 1.0e-6) {
                        if (Math.abs(B) > c.halfLength) {
                            continue;
                        }
                        uLo = yMin - 0.5D;
                        uHi = yMax + 0.5D;
                    } else {
                        uLo = (-c.halfLength - B) / c.ay;
                        uHi = (c.halfLength - B) / c.ay;
                        if (uLo > uHi) {
                            double tmp = uLo;
                            uLo = uHi;
                            uHi = tmp;
                        }
                    }

                    int yStart = Math.max(yMin,
                        (int) Math.ceil(uLo - 0.5D));
                    int yEnd = Math.min(yMax,
                        (int) Math.floor(uHi - 0.5D));

                    for (int y = yStart; y <= yEnd; y++) {
                        double u = y + 0.5D;
                        double t = B + u * c.ay;
                        double du = t / c.halfLength;
                        double r = c.radius * (1.0 - Math.abs(du));
                        if (r <= 0.0) {
                            continue;
                        }

                        double dxp = dx - c.ax * t;
                        double dyp = u - c.cy - c.ay * t;
                        double dzp = dz - c.az * t;
                        double q1 = dxp * c.p1x + dyp * c.p1y + dzp * c.p1z;
                        double q2 = dxp * c.p2x + dyp * c.p2y + dzp * c.p2z;

                        double angle = Math.atan2(q2, q1);
                        double a = ((angle + halfAngle)
                            % (2.0 * halfAngle) + 2.0 * halfAngle)
                            % (2.0 * halfAngle) - halfAngle;
                        double polyScale = Math.cos(halfAngle) / Math.cos(a);

                        if (q1 * q1 + q2 * q2
                            <= (r * polyScale) * (r * polyScale)) {
                            world.setBlock(x, y, z, yuanShi, 0, 2);
                        }
                    }
                }
            }
        }
    }

    private static long ownerKey(int seed, int ownerX, int ownerZ) {
        long h = seed;
        h = h * 0x9E3779B97F4A7C15L ^ ownerX;
        h = h * 0x9E3779B97F4A7C15L ^ ownerZ;
        return h;
    }

    private static double[] perpendicular(double vx, double vy, double vz) {
        double rx = 0.0D;
        double rz = 1.0D;
        if (Math.abs(vy) > 0.9D) {
            rx = 1.0D;
            rz = 0.0D;
        }
        double px = vy * rz;
        double py = vz * rx - vx * rz;
        double pz = -vy * rx;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1.0e-6) {
            return new double[]{1.0D, 0.0D, 0.0D};
        }
        return new double[]{px / len, py / len, pz / len};
    }

    private static double[] cross(double ax, double ay, double az,
                                  double bx, double by, double bz) {
        double cx = ay * bz - az * by;
        double cy = az * bx - ax * bz;
        double cz = ax * by - ay * bx;
        double len = Math.sqrt(cx * cx + cy * cy + cz * cz);
        if (len < 1.0e-6) {
            return new double[]{0.0D, 1.0D, 0.0D};
        }
        return new double[]{cx / len, cy / len, cz / len};
    }

    private static double[] rotateAround(double vx, double vy, double vz,
                                         double ux, double uy, double uz,
                                         double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = vx * ux + vy * uy + vz * uz;

        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;

        double rx = vx * cos + cx * sin + ux * dot * (1.0D - cos);
        double ry = vy * cos + cy * sin + uy * dot * (1.0D - cos);
        double rz = vz * cos + cz * sin + uz * dot * (1.0D - cos);
        return new double[]{rx, ry, rz};
    }
}
