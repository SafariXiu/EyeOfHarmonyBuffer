package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer;

/**
 * =====================================================
 * 类名：SuperContinentCenter
 * 来源：Python 模块 worldgen_core.SuperContinentCenter
 * 功能：
 *   - 表示一个超级大陆的中心信息；
 *   - 包含：坐标、半径、形变参数、随机种子；
 *   - 包含“单侧平直海岸”参数，用于塑造大陆边缘。
 * =====================================================
 */

public class SuperContinentCenter {
    public int worldX, worldZ;
    public int baseRadius;
    public double angle;
    public double stretchMajor, stretchMinor;
    public int subCoreSeed;
    public int superId;
    public int mainContinentId;

    public int smoothSideSign;
    public double smoothStrength;
    public double smoothHalfAngle;

    // 缓存
    public double cosAngle, sinAngle, cosSmoothHalfAngle;

    public SuperContinentCenter(int worldX, int worldZ, int baseRadius,
                                double angle, double stretchMajor, double stretchMinor,
                                int subCoreSeed, int superId,
                                int smoothSideSign, double smoothStrength, double smoothHalfAngle) {
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.baseRadius = baseRadius;
        this.angle = angle;
        this.stretchMajor = stretchMajor;
        this.stretchMinor = stretchMinor;
        this.subCoreSeed = subCoreSeed;
        this.superId = superId;

        this.mainContinentId = WorldgenMath.makeSubContinentId(superId, 0);

        this.smoothSideSign = smoothSideSign;
        this.smoothStrength = smoothStrength;
        this.smoothHalfAngle = smoothHalfAngle;

        this.cosAngle = Math.cos(angle);
        this.sinAngle = Math.sin(angle);
        this.cosSmoothHalfAngle = Math.cos(smoothHalfAngle);
    }
}
