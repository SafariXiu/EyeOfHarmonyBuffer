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

        // 大空间生成 1~2 根石柱（确定性）
        if (ry >= 7.0 && rx * rz >= 80.0) {
            int n = CaveMath.hash01((long) cx, (long) cy, (long) cz,
                seed, 0x51) < 0.5 ? 1 : 2;
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
