package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/** 洞穴大厅：旋转椭球空腔（不可变）。 */
public final class CaveChamber {

    /** 立柱（大空间保留的石柱）；无立柱时数量为 0。 */
    public final int pillarCount;
    public final float[] pillarX;
    public final float[] pillarZ;

    public final float cx;
    public final float cy;
    public final float cz;
    public final float rx;
    public final float ry;
    public final float rz;

    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;

    /** 大厅底部是否有地下湖。 */
    public final boolean hasLake;
    /** 湖面 Y（hasLake 时有效）：湖只填充大厅底部。 */
    public final float lakeSurfaceY;
    /** 湖床 Y（hasLake 时有效）：第一个水体方块层，其下方保留实体湖床。 */
    public final int lakeBedY;

    public CaveChamber(float cx, float cy, float cz,
                       float rx, float ry, float rz,
                       long seed) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.rx = Math.max(1.0f, rx);
        this.ry = Math.max(1.0f, ry);
        this.rz = Math.max(1.0f, rz);
        this.minX = cx - this.rx;
        this.maxX = cx + this.rx;
        this.minY = cy - this.ry;
        this.maxY = cy + this.ry;
        this.minZ = cz - this.rz;
        this.maxZ = cz + this.rz;

        // 大空间生成 2~4 根石柱（确定性）
        if (ry >= 7.0 && rx * rz >= 80.0) {
            int n = 2 + (int) (CaveMath.hash01(
                (long) cx, (long) cy, (long) cz, seed, 0x51) * 3.0);
            this.pillarCount = n;
            this.pillarX = new float[n];
            this.pillarZ = new float[n];
            for (int i = 0; i < n; i++) {
                this.pillarX[i] = cx + (float) (
                    (CaveMath.hash01((long) cx, (long) cy, i, seed, 0x52) - 0.5)
                        * 2.0 * rx * 0.55
                );
                this.pillarZ[i] = cz + (float) (
                    (CaveMath.hash01((long) cx, (long) cz, i, seed, 0x53) - 0.5)
                        * 2.0 * rz * 0.55
                );
            }
        } else {
            this.pillarCount = 0;
            this.pillarX = new float[0];
            this.pillarZ = new float[0];
        }

        // 地下湖：约 20% 的巨型大厅底部有湖，水深 6~10 格。
        this.hasLake = ry >= 10.0 && CaveMath.hash01(
            (long) cx, (long) cy, (long) cz, seed, 0x54) < 0.20;
        if (this.hasLake) {
            float depth = 6.0f + (float) (CaveMath.hash01(
                (long) cx, (long) cy, (long) cz, seed, 0x55) * 5.0);
            this.lakeSurfaceY = (float) (cy - ry) + depth;
            this.lakeBedY = (int) Math.floor(cy - ry) + 1;
        } else {
            this.lakeSurfaceY = -1.0f;
            this.lakeBedY = -1;
        }
    }

    /** 归一化距离 < 1 表示在空腔内（叠加上洞壁噪声的小扰动）。 */
    public boolean inside(double px, double py, double pz,
                          double wallNoise) {
        if (px < minX || px > maxX || py < minY || py > maxY
            || pz < minZ || pz > maxZ) {
            return false;
        }
        // 石柱列不挖空
        for (int i = 0; i < pillarCount; i++) {
            if (Math.abs(px - pillarX[i]) < 1.6
                && Math.abs(pz - pillarZ[i]) < 1.6) {
                return false;
            }
        }
        double dx = (px - cx) / rx;
        double dy = (py - cy) / ry;
        double dz = (pz - cz) / rz;
        double d = dx * dx + dy * dy + dz * dz;
        return d < 1.0 - wallNoise * 0.25;
    }
}
