package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.geom;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicMath;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.SupercontinentPlacement;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.PlateId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.ids.SupercontinentId;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.PlateBoundaryInfluence;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.PlateBoundaryState;

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

    /** 海岸线半径钳制下限（主大陆 17000 / 次级 12000）。 */
    private final double minRadius;
    /** 海岸线半径钳制上限（主大陆 25000 / 次级 17000）。 */
    private final double maxRadius;

    /** 形状振幅等比缩放系数：baseRadius / MAIN_REFERENCE_RADIUS。 */
    private final double shapeScale;

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
        this(worldSeed, cellX, cellZ,
            SupercontinentPlacement.rawPlacement(
                cellX, cellZ, (int) (worldSeed & 0x7FFFFFFFL)
            )
        );
    }

    /**
     * 按布点规则构造超级大陆：中心、半径范围、主/次级特性全部来自 Placement。
     * 调用方必须保证 placement.exists == true（TectonicWorld 只对存在格调用）。
     */
    public Supercontinent(long worldSeed, int cellX, int cellZ,
                          SupercontinentPlacement.Placement placement) {
        this.worldSeed = worldSeed;
        this.id = new SupercontinentId(cellX, cellZ);
        this.minRadius = placement.clampMinRadius;
        this.maxRadius = placement.clampMaxRadius;
        this.centerX = placement.centerX;
        this.centerZ = placement.centerZ;

        long seedRBase = TectonicMath.hashInts((int) (worldSeed & 0xFFFFFFFFL), 0x20001, cellX, cellZ);
        this.baseRadius = TectonicMath.randRange(
            seedRBase,
            placement.baseRadiusMin,
            placement.baseRadiusMax
        );

        // 形状振幅按半径等比缩放：次级大陆观感与主大陆统一（同款 lobe / 噪声风格）
        this.shapeScale = baseRadius / TectonicConfig.MAIN_REFERENCE_RADIUS;

        initRadiusParams();

        initPlateSeeds();

        initPlateMotions();

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
        ampAxis = ampAxis01 * 6000.0 * shapeScale;

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
            double ampI = amp01 * AMP_MID_MAX * shapeScale;

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
        smallNoiseAmp = 900.0 * shapeScale;
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

        if (r < minRadius) r = minRadius;
        else if (r > maxRadius) r = maxRadius;

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

    /**
     * 从超大陆中心指向最近海岸点的方向（弧度，从 X 轴逆时针）。
     *
     * 先用预计算的海岸顶点找最小半径方向，再在附近细扫 radiusAtAngle
     * 消除离散采样误差。结果只依赖该超大陆自身的几何，完全确定性，
     * 与任何外部查询位置无关（河流系统用它作为固定的“向海流出方向”）。
     */
    public double nearestCoastAngle() {
        if (coastX == null || coastX.length == 0) {
            return 0.0;
        }

        int n = coastX.length;
        double bestTheta = 0.0;
        double bestR = Double.POSITIVE_INFINITY;

        for (int i = 0; i < n; i++) {
            double dx = coastX[i] - centerX;
            double dz = coastZ[i] - centerZ;
            double r = Math.hypot(dx, dz);
            if (r < bestR) {
                bestR = r;
                bestTheta = Math.atan2(dz, dx);
            }
        }

        // 在最佳顶点附近 ±半个顶点步长内细扫，取精确最小值
        final int REFINE_SAMPLES = 128;
        double refineSpan = 2.0 * Math.PI / n;
        for (int k = 0; k <= REFINE_SAMPLES; k++) {
            double theta = bestTheta - refineSpan / 2.0
                + refineSpan * k / REFINE_SAMPLES;
            double r = radiusAtAngle(theta);
            if (r < bestR) {
                bestR = r;
                bestTheta = theta;
            }
        }

        return bestTheta;
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
        return findNearestPlate(x, z, outNearestDist, outSecondNearestDist, null);
    }

    /**
     * 找最近板块 + 第二近板块，返回“当前板块 index”以及到下一板块的逻辑距离差。
     * 若需要第二个板块的索引（用于边界状态判定），传入 outSecondIdx。
     */
    public int findNearestPlate(double x, double z,
                                double[] outNearestDist, double[] outSecondNearestDist,
                                int[] outSecondIdx) {
        if (plateCount <= 0) {
            if (outNearestDist != null && outNearestDist.length > 0) {
                outNearestDist[0] = 1.0;
            }
            if (outSecondNearestDist != null && outSecondNearestDist.length > 0) {
                outSecondNearestDist[0] = 1.0;
            }
            if (outSecondIdx != null && outSecondIdx.length > 0) {
                outSecondIdx[0] = -1;
            }
            return 0;
        }

        double[] uv = new double[2];
        toLogical(x, z, uv);
        double u = uv[0];
        double v = uv[1];

        int bestIdx = 0;
        int secondIdx = -1;
        double bestDist = Double.POSITIVE_INFINITY;
        double secondBest = Double.POSITIVE_INFINITY;

        for (int i = 0; i < plateCount; i++) {
            LogicalSeed s = plateSeeds[i];
            double du = u - s.u;
            double dv = v - s.v;
            double d2 = du * du + dv * dv;

            if (d2 < bestDist) {
                secondIdx = bestIdx;
                secondBest = bestDist;
                bestDist = d2;
                bestIdx = i;
            } else if (d2 < secondBest) {
                secondIdx = i;
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
        if (outSecondIdx != null && outSecondIdx.length > 0) {
            outSecondIdx[0] = secondIdx;
        }

        return bestIdx;
    }

    /**
     * 找最近的 k 块板块（按逻辑距离升序），填充 outIdx / outDist 并返回实际数量。
     * k 会被 clamp 到 [1, plateCount]；plateCount <= 0 时返回 0。
     */
    public int findNearestPlates(double x, double z, int k,
                                 int[] outIdx, double[] outDist) {
        if (plateCount <= 0) {
            return 0;
        }
        if (k < 1) {
            k = 1;
        }
        if (k > plateCount) {
            k = plateCount;
        }

        double[] uv = new double[2];
        toLogical(x, z, uv);
        double u = uv[0];
        double v = uv[1];

        int filled = 0;
        for (int i = 0; i < plateCount; i++) {
            LogicalSeed s = plateSeeds[i];
            double du = u - s.u;
            double dv = v - s.v;
            double d = Math.sqrt(du * du + dv * dv);

            int pos = filled;
            if (pos >= k) {
                // 数组已满：不比当前最差的好则丢弃
                if (d >= outDist[k - 1]) {
                    continue;
                }
                pos = k - 1;
            }
            while (pos > 0 && outDist[pos - 1] > d) {
                outDist[pos] = outDist[pos - 1];
                outIdx[pos] = outIdx[pos - 1];
                pos--;
            }
            outDist[pos] = d;
            outIdx[pos] = i;
            if (filled < k) {
                filled++;
            }
        }
        return filled;
    }

    /**
     * 由已排序的最近板块表生成缝合线影响列表（多板块混合）：
     * 每条影响 = (最近板块, 第 i 近板块) 的状态 + 强度，只保留强度 > 0 的缝合线。
     */
    public PlateBoundaryInfluence[] plateBoundaryInfluences(int[] idx,
                                                            double[] dist,
                                                            int count) {
        if (count < 2 || idx == null || dist == null) {
            return new PlateBoundaryInfluence[0];
        }
        java.util.List<PlateBoundaryInfluence> out =
            new java.util.ArrayList<PlateBoundaryInfluence>(count - 1);
        for (int i = 1; i < count; i++) {
            double strength = plateBoundaryStrength(dist[0], dist[i]);
            if (strength <= 0.0) {
                continue;
            }
            out.add(new PlateBoundaryInfluence(
                getPlateBoundaryState(idx[0], idx[i]), strength
            ));
        }
        return out.toArray(new PlateBoundaryInfluence[out.size()]);
    }

    /**
     * 连续「挤压度」[-1,1]：各缝合线影响按强度加权混合
     * （挤压 +1 / 分离 -1 / 走滑、静止 0）；无有效缝合线时为 0。
     */
    public double plateCompression(PlateBoundaryInfluence[] influences) {
        double wSum = 0.0;
        double cSum = 0.0;
        if (influences != null) {
            for (PlateBoundaryInfluence inf : influences) {
                wSum += inf.strength;
                cSum += inf.strength * inf.state.compressionAxis();
            }
        }
        return (wSum > 0.0) ? cSum / wSum : 0.0;
    }

    /**
     * 返回板块 ID（如果点在外海，也会返回最近超级大陆的某个板块）。
     * 同时可带回该点的最近 / 次近板块距离（d1 / d2，归一化逻辑距离），
     * 供边界强度计算复用，避免重复查询。
     */
    public PlateId getPlateIdForPoint(double x, double z,
                                      double[] outD1, double[] outD2) {
        return getPlateIdForPoint(x, z, outD1, outD2, null);
    }

    /**
     * 返回板块 ID，同时带回最近 / 次近板块距离与次近板块索引
     * （供边界强度与边界状态判定复用，避免重复查询）。
     */
    public PlateId getPlateIdForPoint(double x, double z,
                                      double[] outD1, double[] outD2,
                                      int[] outSecondIdx) {
        int idx = findNearestPlate(x, z, outD1, outD2, outSecondIdx);
        return new PlateId(id, idx);
    }

    /**
     * 返回板块 ID（如果点在外海，也会返回最近超级大陆的某个板块）。
     */
    public PlateId getPlateIdForPoint(double x, double z) {
        return getPlateIdForPoint(x, z, new double[1], new double[1]);
    }

    /** 板块种子在逻辑空间的位置 → 世界坐标（用于缝合线法向）。 */
    private void seedWorldPosition(int idx, double[] out) {
        LogicalSeed s = plateSeeds[idx];
        double du = s.u - 0.5;
        double dv = s.v - 0.5;
        double theta = Math.atan2(dv, du);
        double ratio = 2.0 * Math.hypot(du, dv); // r / R(theta)
        double r = ratio * radiusAtAngle(theta);
        out[0] = centerX + r * Math.cos(theta);
        out[1] = centerZ + r * Math.sin(theta);
    }

    /**
     * 两个板块之间的边界状态：
     *   - 相对运动在缝合线法向（两板块种子连线）上相互靠近 → 挤压；
     *   - 相互远离 → 分离；
     *   - 切向分量主导 → 走滑；
     *   - 相对速度低于阈值 → 静止。
     */
    public PlateBoundaryState getPlateBoundaryState(int plateA, int plateB) {
        if (plateA < 0 || plateB < 0
            || plateA >= plateCount || plateB >= plateCount
            || plateA == plateB) {
            return PlateBoundaryState.INACTIVE;
        }

        double[] pa = new double[2];
        double[] pb = new double[2];
        seedWorldPosition(plateA, pa);
        seedWorldPosition(plateB, pb);

        double nx = pb[0] - pa[0];
        double nz = pb[1] - pa[1];
        double nl = Math.hypot(nx, nz);
        if (nl <= 1e-9) {
            return PlateBoundaryState.INACTIVE;
        }
        nx /= nl;
        nz /= nl;

        double vrx = plateMotionX[plateB] - plateMotionX[plateA];
        double vrz = plateMotionZ[plateB] - plateMotionZ[plateA];
        double vRel = Math.hypot(vrx, vrz);

        if (vRel < TectonicConfig.PLATE_INACTIVE_RELATIVE_SPEED) {
            return PlateBoundaryState.INACTIVE;
        }

        double normal = vrx * nx + vrz * nz;
        double tangent = Math.abs(vrx * nz - vrz * nx);

        if (tangent > TectonicConfig.PLATE_TRANSFORM_TANGENT_RATIO * Math.abs(normal)) {
            return PlateBoundaryState.TRANSFORM;
        }
        return (normal < 0.0)
            ? PlateBoundaryState.CONVERGENT
            : PlateBoundaryState.DIVERGENT;
    }

    /** 当前点所在缝合线的边界状态（取最近 / 次近板块对）。 */
    public PlateBoundaryState getPlateBoundaryStateAt(double x, double z) {
        int[] second = new int[1];
        double[] d1 = new double[1];
        double[] d2 = new double[1];
        int best = findNearestPlate(x, z, d1, d2, second);
        return getPlateBoundaryState(best, second[0]);
    }

    /**
     * 由最近 / 次近板块距离计算板块边界强度（距离级）：
     *   metric = d2 - d1（Voronoi 中该值即“到板块边界的有符号距离”的度量）
     *   strength = 1 - metric / PLATE_BOUNDARY_THRESHOLD
     * 0 = 板块内部，1 = 恰好位于缝合线上，随到边界的真实距离连续衰减。
     */
    public double plateBoundaryStrength(double d1, double d2) {
        double metric = d2 - d1;
        double strength = 1.0 - metric / TectonicConfig.PLATE_BOUNDARY_THRESHOLD;
        return TectonicMath.clamp(strength, 0.0, 1.0);
    }

    /**
     * 当前点的板块边界权重：0 = 板块内部，1 = 强缝合线。
     * 直接基于该点最近的板块 Voronoi 距离计算（距离级，随径向位置精确变化），
     * 不再是旧版“0.85 半径环上按角度插值”的近似。
     */
    public double getPlateBoundaryWeight(double x, double z) {
        double[] d1 = new double[1];
        double[] d2 = new double[1];
        findNearestPlate(x, z, d1, d2);
        return plateBoundaryStrength(d1[0], d2[0]);
    }
}
