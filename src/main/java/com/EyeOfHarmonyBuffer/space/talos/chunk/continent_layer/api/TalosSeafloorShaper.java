package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

public final class TalosSeafloorShaper {

    private TalosSeafloorShaper() {}

    private static final double W_SHALLOW_START = 0.90;
    private static final double W_SLOPE_START = 0.85;
    private static final double W_SHELF_START = 0.35;
    private static final double W_BLEND_START = 0.348;
    private static final double W_BLEND_END = 0.350;

    private static final int DEPTH_SHALLOW_NEAR = 1;
    private static final int DEPTH_SHALLOW_FAR = 4;
    private static final int DEPTH_SHELF = 16;

    /**
     * 根据 shelfWeight 对海底高度做多段式“大陆架”塑形：
     *
     *   - w <= W_BLEND_START:
     *       保持原始海底高度（只在海平面以下进行 clamp，不做额外加深处理）；
     *
     *   - W_BLEND_START < w <= W_SHELF_START:
     *       在“原始海底高度”和“大陆架平台高度（seaLevel - DEPTH_SHELF）”
     *       之间做平滑插值，使远洋海底逐渐过渡到统一的平台深度；
     *
     *   - W_SHELF_START < w <= W_SLOPE_START:
     *       维持恒定的平台深度（seaLevel - DEPTH_SHELF），形成宽阔的大陆架平台；
     *
     *   - W_SLOPE_START < w <= W_SHALLOW_START:
     *       在“平台深度（seaLevel - DEPTH_SHELF）”与
     *       “近岸较浅深度（seaLevel - DEPTH_SHALLOW_FAR）”之间做线性过渡，
     *       模拟大陆架边缘向近岸抬升的斜坡；
     *
     *   - w > W_SHALLOW_START:
     *       在“近岸最浅深度（seaLevel - DEPTH_SHALLOW_NEAR）”与
     *       “近岸较浅深度（seaLevel - DEPTH_SHALLOW_FAR）”之间做线性插值，
     *       形成紧邻海岸线的浅海带。
     *
     * 具体含义（可结合常量理解）：
     *
     *   - DEPTH_SHALLOW_NEAR : 靠岸最浅处的水深（例如 1，对应 seaLevel - 1）；
     *   - DEPTH_SHALLOW_FAR  : 离岸一些距离处的浅海水深（例如 4，对应 seaLevel - 4）；
     *   - DEPTH_SHELF        : 大陆架平台的标准水深（例如 16，对应 seaLevel - 16）。
     *
     * 注意：
     *   - 本塑形仅作用在“海洋区域”（isLand == false），
     *     且最终海底高度会被限制在 [1, seaLevel - 1] 之间；
     *   - 对陆地区域（isLand == true）不会抬高，只会在海平面以下做截断，
     *     保证陆地不会高于 seaLevel - 1 的水下高度。
     *
     * @param seaLevel         世界海平面 Y
     * @param isLand           是否为陆地（为 true 时不会做海底塑形，只做水下截断）
     * @param shelfWeight      海洋侧大陆架权重 [0,1]，从远洋向岸边递增
     * @param rawTerrainHeight 原始地形高度（未考虑海平面的噪声高度）
     * @param worldHeight      世界高度上限（用于 clamp）
     * @return 调整后的海底 / 地表 Y 值
     */
    public static int computeSeabedY(
        int seaLevel,
        boolean isLand,
        double shelfWeight,
        double rawTerrainHeight,
        int worldHeight
    ) {
        if (isLand) {
            int h = (int) Math.round(rawTerrainHeight);
            if (h < 1) h = 1;
            if (h > worldHeight - 2) h = worldHeight - 2;
            return Math.min(h, seaLevel - 1);
        }

        double w = shelfWeight;
        if (w < 0.0) w = 0.0;
        if (w > 1.0) w = 1.0;

        int originalSeabed = (int) Math.round(rawTerrainHeight);
        if (originalSeabed < 1) originalSeabed = 1;
        if (originalSeabed > seaLevel - 1) originalSeabed = seaLevel - 1;

        if (w < W_BLEND_START) {
            return originalSeabed;
        }

        int yShelf = seaLevel - DEPTH_SHELF;
        if (yShelf < 1) yShelf = 1;

        if (w >= W_SHALLOW_START) {
            double t = (1.0 - w) / (1.0 - W_SHALLOW_START);
            t = clamp01(t);

            double depthD = DEPTH_SHALLOW_NEAR
                + (DEPTH_SHALLOW_FAR - DEPTH_SHALLOW_NEAR) * t;
            int depth = (int) Math.round(depthD);

            int seabedY = seaLevel - depth;
            return clampSeabed(seabedY, seaLevel);
        }

        if (w >= W_SLOPE_START) {
            double t = (w - W_SLOPE_START) / (W_SHALLOW_START - W_SLOPE_START);
            t = clamp01(t);

            double depthD = DEPTH_SHELF
                + (DEPTH_SHALLOW_FAR - DEPTH_SHELF) * t;
            int depth = (int) Math.round(depthD);

            int seabedY = seaLevel - depth;
            return clampSeabed(seabedY, seaLevel);
        }

        if (w >= W_SHELF_START) {
            int seabedY = yShelf;
            if (seabedY > seaLevel - 1) seabedY = seaLevel - 1;
            return seabedY;
        }

        {
            double t = (w - W_BLEND_START) / (W_BLEND_END - W_BLEND_START);
            t = clamp01(t);

            double s = t * t * (3.0 - 2.0 * t);

            double blended = originalSeabed + (yShelf - originalSeabed) * s;
            int seabedY = (int) Math.round(blended);
            return clampSeabed(seabedY, seaLevel);
        }
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private static int clampSeabed(int y, int seaLevel) {
        if (y < 1) y = 1;
        if (y > seaLevel - 1) y = seaLevel - 1;
        return y;
    }
}
