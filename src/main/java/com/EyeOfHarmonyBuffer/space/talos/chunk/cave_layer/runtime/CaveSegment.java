package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.runtime;

/**
 * 洞穴通道（不可变）：节点间的 3D 样条折线 + 逐顶点半径。
 * 雕刻时做「点到折线距离 < 插值半径 - 洞壁噪声」判断。
 */
public final class CaveSegment {

    /** 边 ID（去重用，由两端节点 ID 派生）。 */
    public final long edgeId;

    /** 塌方段：通道下半部会被碎石填充，只留顶部爬行缝隙。 */
    public final boolean collapsed;

    /** 是否为暗河段（含水网络）。 */
    public final boolean aquifer;
    /** 全淹没：整段通道都填水；否则半淹没（到 waterLevelY 为止）。 */
    public final boolean fullySubmerged;
    /** 半淹没时的水面高度（全淹没时也可用于对齐参考）。 */
    public final int waterLevelY;

    /** 含水-湖泊连接管：允许凿穿大厅湖外壳 / 洞厅湖底进入湖体。 */
    public final boolean piercesLakeShell;

    /** 折线顶点数。 */
    public final int n;
    public final float[] xs;
    public final float[] ys;
    public final float[] zs;
    public final float[] rs;

    /** 包围盒（已按最大半径 + 噪声余量外扩）。 */
    public final float minX;
    public final float minY;
    public final float minZ;
    public final float maxX;
    public final float maxY;
    public final float maxZ;

    public CaveSegment(long edgeId,
                       boolean collapsed,
                       float[] xs, float[] ys, float[] zs, float[] rs,
                       float minX, float minY, float minZ,
                       float maxX, float maxY, float maxZ,
                       boolean aquifer, boolean fullySubmerged,
                       int waterLevelY, boolean piercesLakeShell) {
        this.edgeId = edgeId;
        this.collapsed = collapsed;
        this.aquifer = aquifer;
        this.fullySubmerged = fullySubmerged;
        this.waterLevelY = waterLevelY;
        this.piercesLakeShell = piercesLakeShell;
        this.n = xs.length;
        this.xs = xs;
        this.ys = ys;
        this.zs = zs;
        this.rs = rs;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * 采样某方块相对本通道的「挖空余量」：> 0 表示应挖空。
     *
     * @param wallNoise 洞壁噪声（同方块对所有线段共用，提前算好）
     * @return radius(t) - dist - wallNoise
     */
    public double sampleExcess(double px, double py, double pz,
                               double wallNoise) {
        if (px < minX || px > maxX || py < minY || py > maxY
            || pz < minZ || pz > maxZ) {
            return Double.NEGATIVE_INFINITY;
        }

        double bestDistSq = Double.POSITIVE_INFINITY;
        double bestT = 0.0;
        int bestI = -1;
        double[] t = new double[1];
        for (int i = 0; i < n - 1; i++) {
            double d2 = CaveMath.closestDistSq(
                px, py, pz,
                xs[i], ys[i], zs[i],
                xs[i + 1], ys[i + 1], zs[i + 1],
                t
            );
            if (d2 < bestDistSq) {
                bestDistSq = d2;
                bestT = t[0];
                bestI = i;
            }
        }
        if (bestI < 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double r = rs[bestI] + (rs[bestI + 1] - rs[bestI]) * bestT;
        return r - Math.sqrt(bestDistSq) - wallNoise;
    }
}
