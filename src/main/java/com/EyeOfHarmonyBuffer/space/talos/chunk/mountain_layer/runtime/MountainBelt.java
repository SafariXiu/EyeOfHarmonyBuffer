package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime;

/**
 * 不可变山带：DLA 结构 + 世界映射。
 *
 * 网格行 = 沿山脉走向（长轴），列 = 横截面（短轴）；
 * 查询时把世界坐标旋转到山带坐标系，双线性采样高度与单元格蒙版。
 * 全部字段 final，构建后不可变，可被多线程只读共享。
 */
public final class MountainBelt {

    /** 确定性条带 ID（由世界种子 + 分量最小格哈希得到）。 */
    public final long beltId;

    /** 单元格边长（blocks），当前 64。 */
    public final double cellBlocks;

    /** 网格尺寸：gridW = 横截面格数，gridH = 走向格数。 */
    public final int gridW;
    public final int gridH;

    /** 连续高程场 0~1（行主序，row = 沿走向；脊线=1，谷底≈0）。 */
    public final float[] elevation01;

    /** 单元格蒙版 0/1（同一布局；双线性采样得到软边界）。 */
    public final float[] cellMask;

    /** 世界映射：中心、主轴角度、半长/半宽（blocks）。 */
    public final double centerX;
    public final double centerZ;
    public final double angleRad;
    public final double halfLength;
    public final double halfWidth;

    /** 世界包围盒（快速拒绝）。 */
    public final double minX;
    public final double minZ;
    public final double maxX;
    public final double maxZ;

    /** 条带类型：0=HIGHLAND，1=MOUNTAINS，2=PEAK（群系归属用）。 */
    public final int kind;

    public MountainBelt(long beltId, double cellBlocks,
                        int gridW, int gridH,
                        float[] height01, float[] cellMask,
                        double centerX, double centerZ, double angleRad,
                        double halfLength, double halfWidth,
                        double minX, double minZ, double maxX, double maxZ) {
        this.beltId = beltId;
        this.cellBlocks = cellBlocks;
        this.gridW = gridW;
        this.gridH = gridH;
        this.elevation01 = height01;
        this.cellMask = cellMask;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.angleRad = angleRad;
        this.halfLength = halfLength;
        this.halfWidth = halfWidth;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.kind = 0;
    }

    public MountainBelt(long beltId, double cellBlocks,
                        int gridW, int gridH,
                        float[] elevation01, float[] cellMask,
                        double centerX, double centerZ, double angleRad,
                        double halfLength, double halfWidth,
                        double minX, double minZ, double maxX, double maxZ,
                        int kind) {
        this.beltId = beltId;
        this.cellBlocks = cellBlocks;
        this.gridW = gridW;
        this.gridH = gridH;
        this.elevation01 = elevation01;
        this.cellMask = cellMask;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.angleRad = angleRad;
        this.halfLength = halfLength;
        this.halfWidth = halfWidth;
        this.minX = minX;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxZ = maxZ;
        this.kind = kind;
    }

    /**
     * 查询 (worldX, worldZ) 处的结构高度 01（含单元格蒙版）。
     * 山带之外返回 0。
     */
    public double sample01(double worldX, double worldZ) {
        double elev = sampleElevation01(worldX, worldZ);
        if (elev <= 0.0) {
            return 0.0;
        }
        double mask = sampleMask01(worldX, worldZ);
        double result = elev * mask;
        return result > 0.0 ? result : 0.0;
    }

    /** 连续高程 01（不带蒙版；山带矩形外为 0）。 */
    public double sampleElevation01(double worldX, double worldZ) {
        if (worldX < minX || worldX > maxX
            || worldZ < minZ || worldZ > maxZ) {
            return 0.0;
        }

        double dx = worldX - centerX;
        double dz = worldZ - centerZ;
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double along = dx * cos + dz * sin;
        double cross = -dx * sin + dz * cos;

        double u = (cross + halfWidth) / (2.0 * halfWidth);
        double v = (along + halfLength) / (2.0 * halfLength);
        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
            return 0.0;
        }

        // 双线性采样
        double fx = u * (gridW - 1);
        double fy = v * (gridH - 1);
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int x1 = Math.min(x0 + 1, gridW - 1);
        int y1 = Math.min(y0 + 1, gridH - 1);
        double tx = fx - x0;
        double ty = fy - y0;

        int row0 = y0 * gridW;
        int row1 = y1 * gridW;
        double h00 = elevation01[row0 + x0];
        double h10 = elevation01[row0 + x1];
        double h01 = elevation01[row1 + x0];
        double h11 = elevation01[row1 + x1];
        double height = (h00 * (1.0 - tx) + h10 * tx) * (1.0 - ty)
            + (h01 * (1.0 - tx) + h11 * tx) * ty;

        if (height <= 0.0) {
            return 0.0;
        }
        // 边界衰减：高程同时受蒙版约束，山带外的"凸出"高地被消除
        double mask = sampleMask01(worldX, worldZ);
        double result = height * mask;
        return result > 0.0 ? result : 0.0;
    }

    /** 山带单元格蒙版 0~1（双线性，边界处软过渡；带外为 0）。 */
    public double sampleMask01(double worldX, double worldZ) {
        if (worldX < minX || worldX > maxX
            || worldZ < minZ || worldZ > maxZ) {
            return 0.0;
        }

        double dx = worldX - centerX;
        double dz = worldZ - centerZ;
        double cos = Math.cos(angleRad);
        double sin = Math.sin(angleRad);
        double along = dx * cos + dz * sin;
        double cross = -dx * sin + dz * cos;

        double u = (cross + halfWidth) / (2.0 * halfWidth);
        double v = (along + halfLength) / (2.0 * halfLength);
        if (u < 0.0 || u > 1.0 || v < 0.0 || v > 1.0) {
            return 0.0;
        }

        double fx = u * (gridW - 1);
        double fy = v * (gridH - 1);
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        int x1 = Math.min(x0 + 1, gridW - 1);
        int y1 = Math.min(y0 + 1, gridH - 1);
        double tx = fx - x0;
        double ty = fy - y0;

        int row0 = y0 * gridW;
        int row1 = y1 * gridW;
        double m00 = cellMask[row0 + x0];
        double m10 = cellMask[row0 + x1];
        double m01 = cellMask[row1 + x0];
        double m11 = cellMask[row1 + x1];
        double mask = (m00 * (1.0 - tx) + m10 * tx) * (1.0 - ty)
            + (m01 * (1.0 - tx) + m11 * tx) * ty;
        return mask;
    }
}
