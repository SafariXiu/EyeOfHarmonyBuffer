package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

public final class TalosCoastlineShaper {

    private TalosCoastlineShaper() {}

    /**
     * 根据 coastWeight 对高度做三段式海岸压制：
     *   - 0 < w <= 0.33: 保持原高度；
     *   - 0.33 < w <= 0.66: 在原高度和 seaLevel 之间平滑插值；
     *   - w > 0.66: 压到 seaLevel（只压低，不抬高）。
     *
     * 只作用在陆地且高于海平面的区域。
     */
    public static double applyCoastlineShaping(
        double height,
        int seaLevel,
        boolean isLand,
        double coastWeight
    ) {
        // 只处理：陆地 + 高于海平面 + 有海岸权重
        if (!isLand || coastWeight <= 0.0 || height <= seaLevel) {
            return height;
        }

        // 0 ~ 0.33：完全不动（沙子/内陆）
        if (coastWeight <= 0.33) {
            return height;
        }

        // 高于海平面的那一截高度
        double above = height - seaLevel;

        // 把 [0.33, 1.0] 压缩到 [0, 1]，表示“离真正海岸线的相对距离”
        final double W0 = 0.33;
        final double W1 = 1.0;

        double t = (coastWeight - W0) / (W1 - W0);
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        // 平滑 S 型曲线：0 -> 0, 1 -> 1，中间变缓
        double s = t * t * (3.0 - 2.0 * t);

        // 压缩系数：1 -> 0：越接近海岸，above 被压得越小
        double k = 1.0 - s;

        double newHeight = seaLevel + above * k;

        // 保险：极靠海时，确保不比海平面高（避免浮出一两格）
        if (coastWeight > 0.95 && newHeight > seaLevel) {
            newHeight = seaLevel;
        }

        return newHeight;
    }
}
