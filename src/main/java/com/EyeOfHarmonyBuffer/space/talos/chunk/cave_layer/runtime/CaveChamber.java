package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/** 洞穴大厅：旋转椭球空腔（不可变）。 */
public final class CaveChamber {

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
                       float rx, float ry, float rz) {
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
    }

    /** 归一化距离 < 1 表示在空腔内（叠加上洞壁噪声的小扰动）。 */
    public boolean inside(double px, double py, double pz,
                          double wallNoise) {
        if (px < minX || px > maxX || py < minY || py > maxY
            || pz < minZ || pz > maxZ) {
            return false;
        }
        double dx = (px - cx) / rx;
        double dy = (py - cy) / ry;
        double dz = (pz - cz) / rz;
        double d = dx * dx + dy * dy + dz * dz;
        return d < 1.0 - wallNoise * 0.25;
    }
}
