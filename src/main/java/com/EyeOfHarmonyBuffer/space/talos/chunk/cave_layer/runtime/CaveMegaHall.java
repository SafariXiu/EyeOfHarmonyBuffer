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

        this.pillarHalf = 5 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x91) * 4.0);
        int n = 3 + (int) (CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x92) * 4.0);
        this.pillarCount = n;
        this.pillarX = new double[n];
        this.pillarZ = new double[n];
        for (int i = 0; i < n; i++) {
            double dx;
            double dz;
            for (int attempt = 0; attempt < 8; attempt++) {
                dx = (CaveMath.hash01(
                    (long) cx, (long) cy, i * 2 + attempt,
                    seed, 0x93) - 0.5) * 2.0 * rx * 0.65;
                dz = (CaveMath.hash01(
                    (long) cx, (long) cz, i * 2 + attempt,
                    seed, 0x94) - 0.5) * 2.0 * rz * 0.65;
                if (dx * dx / (rx * rx) + dz * dz / (rz * rz) < 0.81) {
                    this.pillarX[i] = cx + dx;
                    this.pillarZ[i] = cz + dz;
                    break;
                }
                if (attempt == 7) {
                    this.pillarX[i] = cx;
                    this.pillarZ[i] = cz;
                }
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
        for (int i = 0; i < pillarCount; i++) {
            if (Math.abs(wx - pillarX[i]) <= pillarHalf
                && Math.abs(wz - pillarZ[i]) <= pillarHalf) {
                return true;
            }
        }
        return false;
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
            CaveMath.valueNoise3D(
                wx, 1, wz, seed, CEIL_NOISE_SCALE, CEIL_NOISE_SALT
            ) - 0.5) * 2.0;
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
            CaveMath.valueNoise3D(
                wx, 3, wz, seed, SHAPE_NOISE_SCALE, SHAPE_NOISE_SALT
            ) - 0.5) * 2.0;
    }

    private double shapeFactorZ(int wx, int wz) {
        return 1.0 + SHAPE_NOISE_AMP * (
            CaveMath.valueNoise3D(
                wx, 7, wz, seed, SHAPE_NOISE_SCALE, SHAPE_NOISE_SALT
            ) - 0.5) * 2.0;
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
