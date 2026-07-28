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
        if (!isLand || coastWeight <= 0.0 || height <= seaLevel) {
            return height;
        }

        if (coastWeight <= 0.33) {
            return height;
        }

        double above = height - seaLevel;

        final double W0 = 0.33;
        final double W1 = 1.0;

        double t = (coastWeight - W0) / (W1 - W0);
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;

        double s = t * t * (3.0 - 2.0 * t);

        double k = 1.0 - s;

        double newHeight = seaLevel + above * k;

        if (coastWeight > 0.95 && newHeight > seaLevel) {
            newHeight = seaLevel;
        }

        return newHeight;
    }
}
