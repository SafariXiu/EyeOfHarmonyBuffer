package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.geom;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.PlateId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.SupercontinentId;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.*;

/**
 * 单个“超级大陆”的几何与板块信息。
 *
 * 核心几何：
 *   - 以 (centerX, centerZ) 为圆心；
 *   - 海岸线由 r = R(theta) 给出（见 radiusAtAngle）；
 *   - 对任意点 (x,z)，定义 d = r - R(theta)：
 *       * d < 0 : 点在海岸线内侧（陆地）；
 *       * d > 0 : 点在海岸线外侧（海洋）。
 *
 * 所有“是否为陆地 / 离海岸多远”的含义，都统一基于这个 d。
 */

public final class Supercontinent {

    public final long worldSeed;
    public final SupercontinentId id;

    public final double centerX;
    public final double centerZ;

    public final double baseRadius;

    private double thetaLongAxis;
    private double ampAxis;
    private List<Lobe> midLobes;
    private int smallNoiseSegments;
    private double[] smallNoiseValues;
    private double smallNoiseAmp;

    private double[] coastX;
    private double[] coastZ;

    public final double innerSafeRadius;
    public final double outerSafeRadius;

    private int plateCount;
    private LogicalSeed[] plateSeeds;
    private double[] plateMotionX;
    private double[] plateMotionZ;

    private int boundarySampleCount;
    private double[] boundarySampleAngle;
    private double[] boundarySampleStrength;

    private static final class Lobe {
        final double theta;
        final double amp;
        final double width;

        Lobe(double theta, double amp, double width) {
            this.theta = theta;
            this.amp = amp;
            this.width = width;
        }
    }

    /**
     * “逻辑”板块种子：放在 [0,1]x[0,1] 空间里，
     * 真正距离计算时会映射到超大陆内部。
     */
    private static final class LogicalSeed {
        final double u;
        final double v;

        LogicalSeed(double u, double v) {
            this.u = u;
            this.v = v;
        }
    }

    public Supercontinent(long worldSeed, int cellX, int cellZ) {
        this.worldSeed = worldSeed;
        this.id = new SupercontinentId(cellX, cellZ);

        long seedCx = TectonicMath.hashInts((int) (worldSeed & 0xFFFFFFFFL), 0x10001, cellX, cellZ);
        long seedCz = TectonicMath.hashInts((int) (worldSeed & 0xFFFFFFFFL), 0x10002, cellX, cellZ);

        double baseCenterX = cellX * (double) TectonicConfig.SUPER_CELL_SIZE
            + TectonicConfig.SUPER_CELL_SIZE / 2.0;
        double baseCenterZ = cellZ * (double) TectonicConfig.SUPER_CELL_SIZE
            + TectonicConfig.SUPER_CELL_SIZE / 2.0;

        double dx = TectonicMath.randRange(seedCx, -TectonicConfig.CENTER_JITTER_MAX, TectonicConfig.CENTER_JITTER_MAX);
        double dz = TectonicMath.randRange(seedCz, -TectonicConfig.CENTER_JITTER_MAX, TectonicConfig.CENTER_JITTER_MAX);

        this.centerX = baseCenterX + dx;
        this.centerZ = baseCenterZ + dz;

        long seedRBase = TectonicMath.hashInts((int) (worldSeed & 0xFFFFFFFFL), 0x20001, cellX, cellZ);
        this.baseRadius = TectonicMath.randRange(seedRBase,
            TectonicConfig.BASE_RADIUS_MIN,
            TectonicConfig.BASE_RADIUS_MAX
        );

        initRadiusParams();

        initPlateSeeds();

        initPlateMotions();
        precomputeBoundaryRing();

        precomputeCoastVertices();

        double[] safe = computeSafeRadii();
        this.innerSafeRadius = safe[0];
        this.outerSafeRadius = safe[1];
    }

    private void initRadiusParams() {
        int cx = id.cellX;
        int cz = id.cellZ;
        long ws = worldSeed;

        long seedAxis = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x40001, cx, cz);
        thetaLongAxis = TectonicMath.randRange(seedAxis, 0.0, 2.0 * PI);

        long seedAxisAmp = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x40002, cx, cz);
        double ampAxis01 = TectonicMath.randRange(seedAxisAmp, -1.0, 1.0);
        ampAxis = ampAxis01 * 6000.0;

        long seedLobeCount = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x50000, cx, cz);
        double tCount = TectonicMath.randUnitDouble(seedLobeCount);
        int lobeCount = 10 + (int) (tCount * 7.0);

        midLobes = new ArrayList<Lobe>(lobeCount);
        final double AMP_MID_MAX = 2600.0;
        final double WIDTH_MIN = PI * 0.06;
        final double WIDTH_MAX = PI * 0.30;

        for (int i = 0; i < lobeCount; i++) {
            long seedAng = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x50001, cx, cz, i);
            double thetaI = TectonicMath.randRange(seedAng, 0.0, 2.0 * PI);

            long seedAmp = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x50002, cx, cz, i);
            double amp01 = TectonicMath.randRange(seedAmp, -1.0, 1.0);
            double ampI = amp01 * AMP_MID_MAX;

            long seedW = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x50003, cx, cz, i);
            double widthI = TectonicMath.randRange(seedW, WIDTH_MIN, WIDTH_MAX);

            midLobes.add(new Lobe(thetaI, ampI, widthI));
        }

        smallNoiseSegments = 256;
        smallNoiseValues = new double[smallNoiseSegments];
        for (int k = 0; k < smallNoiseSegments; k++) {
            long seedN = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x60001, cx, cz, k);
            smallNoiseValues[k] = TectonicMath.randRange(seedN, -1.0, 1.0);
        }
        smallNoiseAmp = 900.0;
    }

    double radiusAtAngle(double theta) {
        final double TWO_PI = 2.0 * PI;
        if (theta < 0.0 || theta >= TWO_PI) {
            theta = theta % TWO_PI;
            if (theta < 0.0) theta += TWO_PI;
        }

        double r = baseRadius;

        r += ampAxis * cos(2.0 * (theta - thetaLongAxis));

        for (Lobe l : midLobes) {
            double d = theta - l.theta;
            d = (d + PI) % (2.0 * PI) - PI;
            double ad = abs(d);

            if (ad < l.width) {
                double t = ad / l.width;
                double k = 0.5 * (1.0 + cos(PI * t));
                r += l.amp * k;
            }
        }

        int segCount = smallNoiseSegments;
        double pos = (theta / TWO_PI) * segCount;
        int idx0 = (int) floor(pos);
        double t = pos - idx0;
        int idx1 = idx0 + 1;
        if (idx1 >= segCount) idx1 = 0;

        double v0 = smallNoiseValues[idx0];
        double v1 = smallNoiseValues[idx1];
        double t2 = t * t;
        double smoothT = t2 * (3.0 - 2.0 * t);
        double noiseVal = v0 * (1.0 - smoothT) + v1 * smoothT;

        r += noiseVal * smallNoiseAmp;

        if (r < TectonicConfig.MIN_RADIUS) r = TectonicConfig.MIN_RADIUS;
        else if (r > TectonicConfig.MAX_RADIUS) r = TectonicConfig.MAX_RADIUS;

        return r;
    }

    /**
     * 基于极坐标的有符号海岸距离：
     *   d = r - R(theta)
     *   - 海洋：d > 0（点在海岸外，离海岸线多远）
     *   - 陆地：d < 0（点在海岸内，离海岸线多深）
     *
     * 这是 tectonic_v1 中关于“离海岸有多远”的几何基准。
     */
    public double signedCoastDistanceRadial(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;

        double r2 = dx * dx + dz * dz;
        if (r2 <= 0.0) {
            return -baseRadius;
        }

        double r = Math.sqrt(r2);
        double theta = Math.atan2(dz, dx);
        double rEdge = radiusAtAngle(theta);

        return r - rEdge;
    }

    private void precomputeCoastVertices() {
        int n = TectonicConfig.COAST_VERTEX_COUNT;
        coastX = new double[n];
        coastZ = new double[n];

        for (int i = 0; i < n; i++) {
            double theta = 2.0 * PI * i / n;
            double r = radiusAtAngle(theta);
            coastX[i] = centerX + r * cos(theta);
            coastZ[i] = centerZ + r * sin(theta);
        }
    }

    /**
     * 计算 innerSafeRadius / outerSafeRadius：
     *
     * outerSafeRadius = 所有海岸顶点到中心的最大距离；
     * innerSafeRadius = 所有海岸线段到中心的最小距离。
     */
    private double[] computeSafeRadii() {
        int n = coastX.length;
        double maxR = 0.0;
        double minDistEdge = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double dx = coastX[i] - centerX;
            double dz = coastZ[i] - centerZ;
            double r = hypot(dx, dz);
            if (r > maxR) {
                maxR = r;
            }
        }

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double ax = coastX[j];
            double az = coastZ[j];
            double bx = coastX[i];
            double bz = coastZ[i];

            double d = distanceToSegment(centerX, centerZ, ax, az, bx, bz);
            if (d < minDistEdge) {
                minDistEdge = d;
            }
        }

        if (!Double.isFinite(minDistEdge) || minDistEdge < 0.0) {
            minDistEdge = 0.0;
        }

        return new double[]{minDistEdge, maxR};
    }

    /**
     * 点是否在超大陆内部。
     * 等价于 signedCoastDistanceRadial(x,z) <= 0。
     */
    public boolean pointInside(double x, double z) {
        return signedCoastDistanceRadial(x, z) <= 0.0;
    }

    private static double distanceToSegment(double px, double pz,
                                            double ax, double az,
                                            double bx, double bz) {
        double vx = bx - ax;
        double vz = bz - az;
        double wx = px - ax;
        double wz = pz - az;

        double c1 = vx * wx + vz * wz;
        if (c1 <= 0.0) {
            return hypot(px - ax, pz - az);
        }

        double c2 = vx * vx + vz * vz;
        if (c2 <= c1) {
            return hypot(px - bx, pz - bz);
        }

        double t = c1 / c2;
        double cx = ax + t * vx;
        double cz = az + t * vz;
        return hypot(px - cx, pz - cz);
    }

    /**
     * 找离 (x,z) 最近的海岸线距离（总是非负标量）。
     */
    public double distanceToCoast(double x, double z) {
        return Math.abs(signedCoastDistanceRadial(x, z));
    }

    /**
     * 以超大陆中心为圆心的极坐标半径（不限制 inside/outside，只反映几何半径）。
     */
    public double radialDistance(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return hypot(dx, dz);
    }

    /**
     * 径向“向中心权重”：
     *   - 圆心附近 ≈ 1；
     *   - 靠近 R(theta) 外缘 ≈ 0；
     *   - 海外 clamp 为 0。
     */

    public double radialCenterward(double x, double z) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double r = hypot(dx, dz);
        double theta = atan2(dz, dx);
        double rEdge = radiusAtAngle(theta);

        if (r <= 0.0) {
            return 1.0;
        }

        double t = 1.0 - r / (rEdge + 1e-9);
        return TectonicMath.clamp(t, 0.0, 1.0);
    }

    private void initPlateSeeds() {
        int cx = id.cellX;
        int cz = id.cellZ;
        long ws = worldSeed;

        long seedCount = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x70000, cx, cz);
        int count = TectonicMath.randRangeInt(seedCount,
            TectonicConfig.MIN_PLATE_PER_SUPER,
            TectonicConfig.MAX_PLATE_PER_SUPER
        );

        plateCount = count;
        plateSeeds = new LogicalSeed[plateCount];

        for (int i = 0; i < plateCount; i++) {
            long sAng = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x70001, cx, cz, i);
            long sRad = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x70002, cx, cz, i);

            double ang = TectonicMath.randRange(sAng, 0.0, 2.0 * PI);
            double r01 = TectonicMath.randRange(sRad,
                TectonicConfig.PLATE_SEED_RING_MIN,
                TectonicConfig.PLATE_SEED_RING_MAX
            );

            double u = 0.5 + r01 * cos(ang) * 0.45;
            double v = 0.5 + r01 * sin(ang) * 0.45;

            u = TectonicMath.clamp(u, 0.05, 0.95);
            v = TectonicMath.clamp(v, 0.05, 0.95);

            plateSeeds[i] = new LogicalSeed(u, v);
        }
    }

    private void initPlateMotions() {
        int cx = id.cellX;
        int cz = id.cellZ;
        long ws = worldSeed;

        plateMotionX = new double[plateCount];
        plateMotionZ = new double[plateCount];

        for (int i = 0; i < plateCount; i++) {
            long sDir = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x71000, cx, cz, i);
            long sSpd = TectonicMath.hashInts((int) (ws & 0xFFFFFFFFL), 0x71001, cx, cz, i);

            double theta = TectonicMath.randRange(sDir, 0.0, 2.0 * PI);
            double spd = TectonicMath.randRange(sSpd, 0.3, 1.0);

            plateMotionX[i] = spd * cos(theta);
            plateMotionZ[i] = spd * sin(theta);
        }
    }

    /**
     * 把世界坐标映射为 [0,1]^2 的逻辑坐标，用于板块 Voronoi。
     * 内部会根据 R(theta) 做归一化，远海位置也会 clamp 到 [0,1]。
     */
    private void toLogical(double x, double z, double[] outUV) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double r = hypot(dx, dz);
        double theta = atan2(dz, dx);
        double rEdge = radiusAtAngle(theta);

        double u = 0.5 + (r / (rEdge + 1e-9)) * cos(theta) * 0.5;
        double v = 0.5 + (r / (rEdge + 1e-9)) * sin(theta) * 0.5;

        u = TectonicMath.clamp(u, 0.0, 1.0);
        v = TectonicMath.clamp(v, 0.0, 1.0);

        outUV[0] = u;
        outUV[1] = v;
    }

    /**
     * 找最近板块 + 第二近板块，返回“当前板块 index”以及到下一板块的逻辑距离差。
     */
    public int findNearestPlate(double x, double z, double[] outNearestDist, double[] outSecondNearestDist) {
        if (plateCount <= 0) {
            if (outNearestDist != null && outNearestDist.length > 0) {
                outNearestDist[0] = 1.0;
            }
            if (outSecondNearestDist != null && outSecondNearestDist.length > 0) {
                outSecondNearestDist[0] = 1.0;
            }
            return 0;
        }

        double[] uv = new double[2];
        toLogical(x, z, uv);
        double u = uv[0];
        double v = uv[1];

        int bestIdx = 0;
        double bestDist = Double.POSITIVE_INFINITY;
        double secondBest = Double.POSITIVE_INFINITY;

        for (int i = 0; i < plateCount; i++) {
            LogicalSeed s = plateSeeds[i];
            double du = u - s.u;
            double dv = v - s.v;
            double d2 = du * du + dv * dv;

            if (d2 < bestDist) {
                secondBest = bestDist;
                bestDist = d2;
                bestIdx = i;
            } else if (d2 < secondBest) {
                secondBest = d2;
            }
        }

        double d1 = sqrt(bestDist);
        double d2 = sqrt(secondBest);

        if (outNearestDist != null && outNearestDist.length > 0) {
            outNearestDist[0] = d1;
        }
        if (outSecondNearestDist != null && outSecondNearestDist.length > 0) {
            outSecondNearestDist[0] = d2;
        }

        return bestIdx;
    }

    /**
     * 返回板块 ID（如果点在外海，也会返回最近超级大陆的某个板块）。
     */
    public PlateId getPlateIdForPoint(double x, double z) {
        double[] d1 = new double[1];
        double[] d2 = new double[1];
        int idx = findNearestPlate(x, z, d1, d2);
        return new PlateId(id, idx);
    }

    /**
     * 预计算一圈角度样本的“板块边界强度”，用于快速近似板块边界带。
     */
    private void precomputeBoundaryRing() {
        boundarySampleCount = 512;
        boundarySampleAngle = new double[boundarySampleCount];
        boundarySampleStrength = new double[boundarySampleCount];

        double[] d1 = new double[1];
        double[] d2 = new double[1];

        for (int i = 0; i < boundarySampleCount; i++) {
            double theta = 2.0 * PI * i / boundarySampleCount;
            double r = baseRadius * 0.85;
            double x = centerX + r * cos(theta);
            double z = centerZ + r * sin(theta);

            findNearestPlate(x, z, d1, d2);

            double boundaryMetric = d2[0] - d1[0];
            double strength = 1.0 - boundaryMetric / TectonicConfig.PLATE_BOUNDARY_THRESHOLD;
            strength = TectonicMath.clamp(strength, 0.0, 1.0);

            boundarySampleAngle[i] = theta;
            boundarySampleStrength[i] = strength;
        }

        double[] tmp = new double[boundarySampleCount];
        for (int i = 0; i < boundarySampleCount; i++) {
            double sum = boundarySampleStrength[i];
            int count = 1;
            if (i > 0) {
                sum += boundarySampleStrength[i - 1];
                count++;
            } else {
                sum += boundarySampleStrength[boundarySampleCount - 1];
                count++;
            }
            if (i < boundarySampleCount - 1) {
                sum += boundarySampleStrength[i + 1];
                count++;
            } else {
                sum += boundarySampleStrength[0];
                count++;
            }
            tmp[i] = sum / count;
        }
        System.arraycopy(tmp, 0, boundarySampleStrength, 0, boundarySampleCount);
    }

    /**
     * 估算板块边界权重：0 = 板块内部，1 = 强边界。
     * 实现：取当前点的极角，在预计算的环上线性插值。
     */
    public double getPlateBoundaryWeight(double x, double z) {
        if (boundarySampleCount <= 0) return 0.0;

        double dx = x - centerX;
        double dz = z - centerZ;
        double theta = atan2(dz, dx);
        if (theta < 0.0) theta += 2.0 * PI;

        double pos = (theta / (2.0 * PI)) * boundarySampleCount;
        int idx0 = (int) floor(pos);
        double t = pos - idx0;
        int idx1 = idx0 + 1;
        if (idx1 >= boundarySampleCount) idx1 = 0;

        double v0 = boundarySampleStrength[idx0];
        double v1 = boundarySampleStrength[idx1];
        return v0 * (1.0 - t) + v1 * t;
    }
}
