package com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.api;

public final class TalosSeafloorShaper {

    private TalosSeafloorShaper() {}

    /**
     * 根据 shelfWeight 对海底高度做“大陆架”塑形。
     *
     * 规则（shelfWeight = w）大致为：
     *
     *   1) 1.0 >= w >= 0.8   → 近岸浅海：从 海平面-1 过渡到 海平面-4；
     *   2) 0.8 >  w >= 0.7   → 由 海平面-4 过渡到 海平面-16；
     *   3) 0.7 >  w >= 0.1   → 恒定 海平面-16（大陆架平台）；
     *   4) 0.1 >  w >= 0.05  → 在 海平面-16 与 原始地形 之间平滑插值；
     *   5) w <  0.05         → 保留原始地形（远洋不处理）。
     *
     * 对陆地 isLand=true 的位置，只做简单的“海平面以下截断”，不会抬高陆地。
     *
     * @param seaLevel         世界海平面 Y
     * @param isLand           是否为陆地（为 true 时不会做海底塑形）
     * @param shelfWeight      海洋侧大陆架权重 [0,1]
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

        if (w < 0.05) {
            return originalSeabed;
        }

        final int SHALLOW_NEAR_DEPTH = 1;
        final int SHALLOW_FAR_DEPTH = 4;
        final int SHELF_DEPTH = 16;

        int yShelf = seaLevel - SHELF_DEPTH;
        if (yShelf < 1) yShelf = 1;

        if (w >= 0.8) {
            double t = (1.0 - w) / (1.0 - 0.8);
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            double depthD = SHALLOW_NEAR_DEPTH
                + (SHALLOW_FAR_DEPTH - SHALLOW_NEAR_DEPTH) * t;
            int depth = (int) Math.round(depthD);

            int seabedY = seaLevel - depth;
            if (seabedY < 1) seabedY = 1;
            if (seabedY > seaLevel - 1) seabedY = seaLevel - 1;

            return seabedY;
        }

        if (w >= 0.7) {
            final double W0 = 0.7;
            final double W1 = 0.8;

            double t = (w - W0) / (W1 - W0);
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            int depthStart = SHELF_DEPTH; // 16
            int depthEnd   = SHALLOW_FAR_DEPTH; // 4

            double depthD = depthStart + (depthEnd - depthStart) * t;
            int depth = (int) Math.round(depthD);

            int seabedY = seaLevel - depth;
            if (seabedY < 1) seabedY = 1;
            if (seabedY > seaLevel - 1) seabedY = seaLevel - 1;

            return seabedY;
        }

        if (w >= 0.1) {
            int seabedY = yShelf;
            if (seabedY > seaLevel - 1) seabedY = seaLevel - 1;
            return seabedY;
        }

        if (w >= 0.05) {
            final double W0 = 0.05;
            final double W1 = 0.10;

            double t = (w - W0) / (W1 - W0);
            if (t < 0.0) t = 0.0;
            if (t > 1.0) t = 1.0;

            double s = t * t * (3.0 - 2.0 * t);

            double blended = originalSeabed + (yShelf - originalSeabed) * s;
            int seabedY = (int) Math.round(blended);

            if (seabedY < 1) seabedY = 1;
            if (seabedY > seaLevel - 1) seabedY = seaLevel - 1;

            return seabedY;
        }

        return originalSeabed;
    }
}
