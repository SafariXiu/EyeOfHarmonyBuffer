package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.smoothing;

/**
 * 牛轭湖（恢复「观感强很多」的渲染版本）：
 * 抛物面湖盆（湖心最深到 y=50）；接口附近湖底只过渡到「河的深度」，
 * 不压到湖底最深；湖床起伏满幅生效（无岸坡、无软淡入）。
 */
public final class OxbowLakeSmoothing implements WaterBodySmoothing {

    private static final double LOWEST_Y_BELOW_SEA = 14.0;

    @Override
    public double interiorBedY(WaterBodySmoothingContext ctx) {
        double maxDepth = Math.max(
            ctx.body.getMaxDepthBlocks(),
            ctx.seaLevel - LOWEST_Y_BELOW_SEA
        );

        double u = 1.0 - Math.min(1.0, ctx.r);
        double depth = maxDepth * u * u;

        // 接口附近：只过渡到河的深度，不压到湖底最深
        if (ctx.hydro.mask > 0.0) {
            double riverDepth = Math.max(
                0.0, ctx.seaLevel - ctx.riverBedYd
            );
            double blendWidth = Math.max(1.0, ctx.cutWidth * 2.0);
            double riverBlend = 1.0 - SmoothingMath.clamp01(
                ctx.hydro.distance / blendWidth
            );
            riverBlend = SmoothingMath.smoothstep01(riverBlend);
            depth = Math.max(depth, riverDepth * riverBlend);
        }

        double bedY = ctx.waterLevel - depth;

        // 满幅河床起伏
        bedY += ctx.relief;
        return bedY;
    }
}
