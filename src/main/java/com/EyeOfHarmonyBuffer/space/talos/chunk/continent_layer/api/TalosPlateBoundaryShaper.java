package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.TectonicConfig;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.sample.PlateBoundaryState;

/**
 * 板块边界地形塑形（海陆分布层 API）。
 *
 * 目前实现分离带的「裂谷压高」：地堑式剖面——
 *   - 强度 &lt; 阈值（0.2）：原样地形；
 *   - 强度 = 阈值：硬切到裂谷边缘平台（断崖式裂谷壁）；
 *   - 阈值 &lt; 强度 &lt; 1：从边缘平台软压缓坡下探到谷底；
 *   - 强度 → 1：谷底（最深处）。
 */
public final class TalosPlateBoundaryShaper {

    private TalosPlateBoundaryShaper() {}

    /**
     * 裂谷高度塑形。
     *
     * @param height   当前基础高度（海岸塑形之后）
     * @param seaLevel 海平面 Y
     * @param isLand   是否为陆地（海洋不塑形）
     * @param state    板块边界状态（仅 DIVERGENT 生效）
     * @param strength 板块边界强度 [0,1]
     */
    public static double applyRiftShaping(double height, int seaLevel,
                                          boolean isLand,
                                          PlateBoundaryState state,
                                          double strength) {
        if (!isLand || state != PlateBoundaryState.DIVERGENT) {
            return height;
        }

        double minStrength = TectonicConfig.PLATE_BOUNDARY_MIN_STRENGTH;
        if (strength < minStrength) {
            return height;
        }

        double u = smoothstep(minStrength, 1.0, strength);
        double edgeY = seaLevel + TectonicConfig.RIFT_EDGE_ABOVE_SEA;
        double floorY = seaLevel - TectonicConfig.RIFT_FLOOR_BELOW_SEA;
        double target = edgeY + (floorY - edgeY) * u;

        return height + (target - height) * TectonicConfig.RIFT_BLEND;
    }

    private static double smoothstep(double a, double b, double x) {
        double t = (x - a) / (b - a);
        if (t < 0.0) {
            t = 0.0;
        } else if (t > 1.0) {
            t = 1.0;
        }
        return t * t * (3.0 - 2.0 * t);
    }
}
