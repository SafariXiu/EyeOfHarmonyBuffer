package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** F5 一次静态气候解的结果。 */
public final class OceanSolution {

    public final OceanCacheKey key;
    public final OceanState state;
    public final double[] surfaceT;
    public final int baroIterations;
    public final int advIterations;
    public final double baroResidual;
    public final double advResidual;
    /** 正压流函数极值（m^2/s）与代理 MOC（Sv）。 */
    public final double psiMin, psiMax, mocSv;
    public final double salinityBudgetMax, salinityBudgetMean;

    /**
     * ★ 2026-11-06（用户裁决）：**逐季的表层 T**（长度 = 季节数，每个 nx*ny；可能为 null）。
     *
     * <p>为什么需要：上升流、季节性岸流、冬季混合层都是**季节**现象；
     * 只存年均的话，它们在年均场里会互相抵消（NUPWELL 四版实测 corr≈0 就是这个原因）。
     * <p>只存不算 ⇒ 年均路径逐位不变。
     */
    public final double[][] surfaceTBySeason;

    public OceanSolution(OceanCacheKey key, OceanState state, double[] surfaceT,
                         int baroIterations, int advIterations,
                         double baroResidual, double advResidual,
                         double psiMin, double psiMax, double mocSv) {
        this(key, state, surfaceT, baroIterations, advIterations, baroResidual, advResidual,
            psiMin, psiMax, mocSv, 0.0, 0.0);
    }

    public OceanSolution(OceanCacheKey key, OceanState state, double[] surfaceT,
                         int baroIterations, int advIterations,
                         double baroResidual, double advResidual,
                         double psiMin, double psiMax, double mocSv,
                         double salinityBudgetMax, double salinityBudgetMean) {
        this(key, state, surfaceT, baroIterations, advIterations, baroResidual, advResidual,
            psiMin, psiMax, mocSv, salinityBudgetMax, salinityBudgetMean, null);
    }

    public OceanSolution(OceanCacheKey key, OceanState state, double[] surfaceT,
                         int baroIterations, int advIterations,
                         double baroResidual, double advResidual,
                         double psiMin, double psiMax, double mocSv,
                         double salinityBudgetMax, double salinityBudgetMean,
                         double[][] surfaceTBySeason) {
        this.surfaceTBySeason = surfaceTBySeason;
        this.key = key;
        this.state = state;
        this.surfaceT = surfaceT;
        this.baroIterations = baroIterations;
        this.advIterations = advIterations;
        this.baroResidual = baroResidual;
        this.advResidual = advResidual;
        this.psiMin = psiMin;
        this.psiMax = psiMax;
        this.mocSv = mocSv;
        this.salinityBudgetMax = salinityBudgetMax;
        this.salinityBudgetMean = salinityBudgetMean;
    }
}
