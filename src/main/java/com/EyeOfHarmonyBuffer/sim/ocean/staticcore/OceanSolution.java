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
