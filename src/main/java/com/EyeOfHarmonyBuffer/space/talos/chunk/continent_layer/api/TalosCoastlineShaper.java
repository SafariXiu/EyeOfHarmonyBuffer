package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

public final class TalosCoastlineShaper {

    private TalosCoastlineShaper() {}

    private static final double W_KEEP_MAX = 0.50;
    private static final double W_BLEND_START = 0.55;
    private static final double W_BLEND_END = 0.90;

    /**
     * 根据 coastWeight 对陆地高度做三段式海岸压制，并对靠海区域的
     * 过深小湖 / 低洼区做“软抬升”处理：
     *
     *   - 0 < w <= W_KEEP_MAX:
     *       基本保持原始高度，相当于“远离海岸线的内陆”区域。
     *
     *   - W_KEEP_MAX < w <= W_BLEND_END（高于 seaLevel 的部分）:
     *       对高于 seaLevel 的陆地，在“原始高度（height）”与
     *       “海平面高度（seaLevel）”之间做平滑插值（smoothstep），
     *       使靠近海岸线的高地逐渐被削低接近海平面。
     *
     *   - w > W_BLEND_END（高于 seaLevel 的部分）:
     *       保持原逻辑，将高于 seaLevel 的部分强行压低到 seaLevel，
     *       形成清晰的海岸切线。
     *
     *   - 额外规则（软抬小湖，作用于 w > W_KEEP_MAX 且 height < seaLevel - 1）:
     *       在整个 w ∈ (W_KEEP_MAX, 1] 范围内，对所有低于 seaLevel - 1
     *       的小湖 / 低洼做“软抬升”：使用 smoothstep 按权重将高度
     *       从当前值平滑插值到 seaLevel - 1。
     *       w 越接近 1，被抬得越接近 seaLevel - 1；靠近 W_KEEP_MAX 时抬得很少。
     *
     * 行为约束与注意事项：
     *
     *   - 本塑形仅作用在「陆地区域」（isLand == true），对非陆地不做改动。
     *
     *   - 对高于 seaLevel 的部分：
     *       只会压低到 seaLevel，不会被抬高。
     *
     *   - 对 w > W_KEEP_MAX 且低于 seaLevel - 1 的部分：
     *       视为靠近海岸线的深洼 / 小湖，通过“软抬升”逐渐填高到
     *       seaLevel - 1（例如 63），避免贴海区域出现过深的坑。
     *
     * @param height      原始高度（陆地噪声生成后的高度）
     * @param seaLevel    世界海平面高度 Y
     * @param isLand      是否为陆地（为 false 时不做海岸压制）
     * @param coastWeight 海岸带权重 [0,1]，从内陆向海岸线递增
     * @return 经过海岸压制与近海软抬后的新高度
     */
    public static double applyCoastlineShaping(
        double height,
        int seaLevel,
        boolean isLand,
        double coastWeight
    ) {
        if (!isLand || coastWeight <= 0.0) {
            return height;
        }

        double w = coastWeight;
        if (w < 0.0) w = 0.0;
        if (w > 1.0) w = 1.0;

        double result = height;

        if (w > W_KEEP_MAX && height > seaLevel) {

            if (w <= W_BLEND_END) {
                double above = height - seaLevel;

                double t = (w - W_BLEND_START) / (W_BLEND_END - W_BLEND_START);
                t = clamp01(t);

                double s = t * t * (3.0 - 2.0 * t);
                double k = 1.0 - s;

                double newHeight = seaLevel + above * k;

                if (newHeight > height) {
                    newHeight = height;
                }

                result = newHeight;
            } else {
                result = seaLevel;
            }
        }

        int minY = seaLevel - 1;
        if (minY < 1) {
            minY = 1;
        }

        if (w > W_KEEP_MAX && result < minY) {
            double tLake = (w - W_KEEP_MAX) / (1.0 - W_KEEP_MAX);
            tLake = clamp01(tLake);

            double sLake = tLake * tLake * (3.0 - 2.0 * tLake);

            result = result + (minY - result) * sLake;
        }

        return result;
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
