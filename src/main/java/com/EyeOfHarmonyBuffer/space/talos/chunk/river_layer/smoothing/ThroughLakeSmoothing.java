package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

/**
 * 穿河湖：
 * 湖心平底全深；整圈（含进出口）做统一平滑岸坡，
 * 不再在河口处垂直硬切。
 * 与牛轭湖/独立湖同一套河湖高度平滑：河道附近湖底过渡到「河的深度」，
 * 因此河仍然直接以自身深度进湖，湖岸却保持平滑。
 * 湖床起伏做软淡入。
 */
public final class ThroughLakeSmoothing implements WaterBodySmoothing {

    private static final double SIDE_BAND_FRACTION = 0.12;
    private static final double RELIEF_BAND_FRACTION = 0.10;
    private static final double RELIEF_MIN_SCALE = 0.25;

    @Override
    public double interiorBedY(WaterBodySmoothingContext ctx) {
        double sideBand = Math.max(8.0, ctx.rz * SIDE_BAND_FRACTION);
        double sideShape = SmoothingMath.smoothstep01(
            ctx.distToEdge / Math.max(2.0, sideBand)
        );

        // 整圈统一岸坡：轮廓处 1 格浅水，向内平滑到湖心平底全深
        double baseDepth = ctx.body.getMaxDepthBlocks() * sideShape;
        double depth = baseDepth;

        // 河湖高度平滑：离开岸坡带后，湖盆深度平滑过渡到「河的深度」。
        // 岸坡带内不向河深过渡——深水由保留的河道切口穿过沙圈，
        // 否则深水在岸线处直接顶到沙圈，形成交界立面。
        if (ctx.hydro.mask > 0.0) {
            double riverDepth = Math.max(
                0.0, ctx.seaLevel - ctx.riverBedYd
            );
            double blendWidth = Math.max(1.0, ctx.cutWidth * 2.0);
            double riverBlend = 1.0 - SmoothingMath.clamp01(
                ctx.hydro.distance / blendWidth
            );
            riverBlend = SmoothingMath.smoothstep01(riverBlend);
            if (riverDepth > baseDepth) {
                double interiorFactor = sideShape; // 0=岸线，1=湖心
                depth = baseDepth
                    + (riverDepth - baseDepth)
                        * riverBlend * interiorFactor;
            }
        }

        double bedY = ctx.waterLevel - depth;

        // 湖床起伏软淡入
        double reliefBand = Math.max(
            12.0, Math.min(ctx.rx, ctx.rz) * RELIEF_BAND_FRACTION
        );
        double edgeScale = SmoothingMath.smoothstep01(
            ctx.distToEdge / reliefBand
        );
        bedY += ctx.relief
            * (RELIEF_MIN_SCALE + (1.0 - RELIEF_MIN_SCALE) * edgeScale);
        return bedY;
    }
}
