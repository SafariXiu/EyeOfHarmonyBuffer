package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 洞厅：宽长上千格的巨型空腔（不可变、确定性生成）。
 *
 * 形状用水平椭圆 + 垂直半高缩放；底部用中频噪声区分干地与湖泊。
 * 巨型石柱在雕刻时整列保留。
 */
public final class CaveMegaHall {

    /** 洞厅形状指数：2=椭球，4=圆角方厅（更接近长方体、不会中间鼓包）。 */
    public static final double SHAPE_P = 4.0;
    /** 水平边界噪声：尺度 / 幅度（半径 ±12%）/ 盐。 */
    private static final double SHAPE_NOISE_SCALE = 140.0;
    private static final double SHAPE_NOISE_AMP = 0.12;
    private static final int SHAPE_NOISE_SALT = 0xC1;
    /** 天花板噪声：尺度 / 幅度（±8 格）/ 盐。 */
    private static final double CEIL_NOISE_SCALE = 42.0;
    private static final double CEIL_NOISE_AMP = 8.0;
    private static final int CEIL_NOISE_SALT = 0xC2;
    /** 洞厅湖水面基准：低处灌水到该高度。 */
    public static final int LAKE_WATER_LEVEL = 15;
    /** 洞厅底部地形噪声：单层大尺度、大幅度，靠幅度自然拔高高区。 */
    private static final double FLOOR_NOISE_SCALE = 140.0;
    private static final double FLOOR_NOISE_AMP = 30.0;
    private static final int FLOOR_NOISE_SALT = 0xC3;
    /** 域扭曲：把噪声坐标揉弯，消除直线切线。 */
    private static final double FLOOR_WARP_AMP = 60.0;
    private static final double FLOOR_WARP_SCALE = 300.0;
    private static final int FLOOR_WARP_SALT = 0xD1;
    /** 平台阈值：噪声超过该值直接切高台（约覆盖 1/4 区域）。 */
    private static final double PLATEAU_THRESHOLD = 0.15;
    /** 平台过渡带宽：阈值前这段做陡坡，而不是硬切。 */
    private static final double PLATEAU_BLEND = 0.12;
    /** 噪声梯度超过该值时保留硬崖，不铺陡坡。 */
    private static final double PLATEAU_HARD_GRADIENT = 0.06;
    private static final int PLATEAU_GRADIENT_STEP = 4;
    /** 高平台基准高度与起伏。 */
    private static final int PLATEAU_BASE = 32;
    private static final double PLATEAU_AMP = 6.0;
    private static final double PLATEAU_SCALE = 600.0;
    private static final int PLATEAU_SALT = 0xC6;
    /** 底部高频风格化噪声：小幅、较缓起伏，只留自然粗糙感。 */
    private static final double FLOOR_DETAIL_SCALE = 32.0;
    private static final double FLOOR_DETAIL_AMP = 1.5;
    private static final int FLOOR_DETAIL_SALT = 0xC7;

    public final long seed;
    public final double cx;
    public final double cy;
    public final double cz;
    public final double rx;
    public final double ry;
    public final double rz;

    public final double minX;
    public final double maxX;
    public final double minY;
    public final double maxY;
    public final double minZ;
    public final double maxZ;
    public final int pillarCount;
    public final double[] pillarX;
    public final double[] pillarZ;
    public final int pillarHalf;

    public CaveMegaHall(double cx, double cy, double cz,
                        double rx, double ry, double rz,
                        long seed) {
        this.seed = seed;
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.rx = Math.max(1.0, rx);
        this.ry = Math.max(1.0, ry);
        this.rz = Math.max(1.0, rz);
        this.minX = cx - this.rx;
        this.maxX = cx + this.rx;
        this.minY = cy - this.ry;
        this.maxY = cy + this.ry;
        this.minZ = cz - this.rz;
        this.maxZ = cz + this.rz;

        // 基础半径 30~38（直径约 60~76）。
        this.pillarHalf = 30 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x91) * 9.0);
        int n = 12 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x92) * 5.0);
        this.pillarCount = n;
        this.pillarX = new double[n];
        this.pillarZ = new double[n];
        double minDist = pillarHalf * 3.0;
        double minDistSq = minDist * minDist;
        int placed = 0;
        for (int i = 0; i < n; i++) {
            double dx;
            double dz;
            boolean ok = false;
            for (int attempt = 0; attempt < 24 && !ok; attempt++) {
                dx = (CaveMath.hash01(
                    (long) cx, (long) cy, i * 2 + attempt,
                    seed, 0x93) - 0.5) * 2.0 * rx * 0.65;
                dz = (CaveMath.hash01(
                    (long) cx, (long) cz, i * 2 + attempt,
                    seed, 0x94) - 0.5) * 2.0 * rz * 0.65;
                if (dx * dx / (rx * rx) + dz * dz / (rz * rz) < 0.81) {
                    double px = cx + dx;
                    double pz = cz + dz;
                    boolean far = true;
                    for (int j = 0; j < placed; j++) {
                        double ddx = px - pillarX[j];
                        double ddz = pz - pillarZ[j];
                        if (ddx * ddx + ddz * ddz < minDistSq) {
                            far = false;
                            break;
                        }
                    }
                    if (far) {
                        this.pillarX[i] = px;
                        this.pillarZ[i] = pz;
                        placed++;
                        ok = true;
                    }
                }
            }
            if (!ok) {
                // 兜底：按角度放在 0.55 半径处，避免全部堆到中心。
                double a = i * (2.0 * Math.PI / n)
                    + CaveMath.hash01(
                        (long) cx, (long) cz, i, seed, 0x95) * 0.5;
                this.pillarX[i] = cx + Math.cos(a) * rx * 0.55;
                this.pillarZ[i] = cz + Math.sin(a) * rz * 0.55;
                placed++;
            }
        }
    }

    /** 水平投影是否在该洞厅内。 */
    public boolean insideHorizontal(double wx, double wz) {
        return noisyShapeH((int) Math.floor(wx), (int) Math.floor(wz)) < 1.0;
    }

    /** 是否在洞厅水平范围附近（含 margin 格的外扩带）。 */
    public boolean nearHorizontal(double wx, double wz, double margin) {
        int ix = (int) Math.floor(wx);
        int iz = (int) Math.floor(wz);
        double fx = shapeFactorX(ix, iz);
        double fz = shapeFactorZ(ix, iz);
        double ex = rx * fx + margin;
        double ez = rz * fz + margin;
        double dx = (wx - cx) / ex;
        double dz = (wz - cz) / ez;
        return shapeH(dx, dz) < 1.0;
    }

    /** 该列是否属于巨型石柱（整列不挖）。 */
    public boolean isPillarColumn(int wx, int wz) {
        return pillarIndex(wx, wz) >= 0;
    }

    /** 返回该列所属石柱的索引；不在任何柱内返回 -1。 */
    public int pillarIndex(int wx, int wz) {
        // 圆形判定：只处理可能落在柱体/外扩半径内的列，避免方形压地形。
        double reach = pillarHalf * 2.2;
        int best = -1;
        double bestD = Double.POSITIVE_INFINITY;
        for (int i = 0; i < pillarCount; i++) {
            double dx = wx + 0.5 - pillarX[i];
            double dz = wz + 0.5 - pillarZ[i];
            double d = dx * dx + dz * dz;
            if (d <= reach * reach && d < bestD) {
                best = i;
                bestD = d;
            }
        }
        return best;
    }

    /**
     * 计算该列在洞厅内的整数 Y 范围并写入 out[0..1]。
     * @return 是否有非空范围
     */
    public boolean verticalSpan(int wx, int wz, int maxY, int[] out) {
        double h = noisyShapeH(wx, wz);
        if (h >= 1.0) {
            return false;
        }
        double ryLocal = ry * Math.pow(
            Math.max(0.0, 1.0 - h), 1.0 / SHAPE_P);
        double a = cy - ryLocal;
        double b = cy + ryLocal;
        // 天花板噪声：让顶部起伏，地面保持原有中频噪声
        double ceilNoise = CEIL_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / CEIL_NOISE_SCALE, 0.1, wz / CEIL_NOISE_SCALE,
                seed, CEIL_NOISE_SALT
            ));
        b += ceilNoise;
        int yMin = (int) Math.floor(a - 0.5) + 1;
        int yMax = (int) Math.ceil(b - 0.5) - 1;
        if (yMin < 1) {
            yMin = 1;
        }
        if (yMax > maxY) {
            yMax = maxY;
        }
        if (yMin > yMax) {
            return false;
        }
        out[0] = yMin;
        out[1] = yMax;
        return true;
    }

    /**
     * 洞厅该列的地面高度（不含石柱、不做顶部钳制）。
     * 雕刻器用它生成底部地形；暗河接入点也用它在洞厅内找水下位置。
     */
    public int floorY(int worldX, int worldZ) {
        double wx2 = worldX + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.4, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT);
        double wz2 = worldZ + FLOOR_WARP_AMP * CaveMath.perlin3D(
            worldX / FLOOR_WARP_SCALE, 0.5, worldZ / FLOOR_WARP_SCALE,
            seed, FLOOR_WARP_SALT + 1);
        double n = CaveMath.fbm3D(
            wx2 / FLOOR_NOISE_SCALE, 0.1, wz2 / FLOOR_NOISE_SCALE,
            seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
        int offset = (int) Math.round(n * FLOOR_NOISE_AMP);
        int lowY = LAKE_WATER_LEVEL + offset;
        int floorY = lowY;
        // 阈值前做一段 smoothstep 陡坡，越过阈值后进入高平台。
        if (n >= PLATEAU_THRESHOLD - PLATEAU_BLEND) {
            double pn = CaveMath.perlin3D(
                worldX / PLATEAU_SCALE, 0.2, worldZ / PLATEAU_SCALE,
                seed, PLATEAU_SALT) * 2.0;
            int plateauY = PLATEAU_BASE + (int) Math.round(pn * PLATEAU_AMP);
            if (n >= PLATEAU_THRESHOLD) {
                floorY = plateauY;
            } else {
                double nRight = CaveMath.fbm3D(
                    (wx2 + PLATEAU_GRADIENT_STEP) / FLOOR_NOISE_SCALE,
                    0.1, wz2 / FLOOR_NOISE_SCALE,
                    seed, FLOOR_NOISE_SALT, 3, 2.0, 0.5) * 2.0;
                if (Math.abs(nRight - n) > PLATEAU_HARD_GRADIENT) {
                    // 噪声跳变剧烈：保留硬崖。
                    floorY = lowY;
                } else {
                    double t = (n - (PLATEAU_THRESHOLD - PLATEAU_BLEND))
                        / PLATEAU_BLEND;
                    double s = t * t * (3.0 - 2.0 * t);
                    floorY = (int) Math.round(lowY + (plateauY - lowY) * s);
                }
            }
        }
        // 高频风格化：小幅快速起伏，作用在整个底部（含湖盆与平台）。
        double dn = CaveMath.perlin3D(
            worldX / FLOOR_DETAIL_SCALE, 0.3, worldZ / FLOOR_DETAIL_SCALE,
            seed, FLOOR_DETAIL_SALT) * 2.0;
        floorY += (int) Math.round(dn * FLOOR_DETAIL_AMP);
        if (floorY < 2) {
            floorY = 2;
        }
        return floorY;
    }

    /** 带噪声扰动的水平形状值（<1 表示在该洞厅内）。 */
    private double noisyShapeH(int wx, int wz) {
        double fx = shapeFactorX(wx, wz);
        double fz = shapeFactorZ(wx, wz);
        double dx = (wx + 0.5 - cx) / (rx * fx);
        double dz = (wz + 0.5 - cz) / (rz * fz);
        return shapeH(dx, dz);
    }

    private double shapeFactorX(int wx, int wz) {
        return 1.0 + SHAPE_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / SHAPE_NOISE_SCALE, 0.05, wz / SHAPE_NOISE_SCALE,
                seed, SHAPE_NOISE_SALT
            ));
    }

    private double shapeFactorZ(int wx, int wz) {
        return 1.0 + SHAPE_NOISE_AMP * (
            CaveMath.perlin3D(
                wx / SHAPE_NOISE_SCALE, 0.07, wz / SHAPE_NOISE_SCALE,
                seed, SHAPE_NOISE_SALT + 1
            ));
    }

    private static double shapeH(double dx, double dz) {
        return Math.pow(Math.abs(dx), SHAPE_P)
            + Math.pow(Math.abs(dz), SHAPE_P);
    }

    /** 是否与某个区块相交（粗略水平包围盒判断）。 */
    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int x0 = chunkX * 16;
        int z0 = chunkZ * 16;
        return maxX >= x0 && minX <= x0 + 16
            && maxZ >= z0 && minZ <= z0 + 16;
    }
}
