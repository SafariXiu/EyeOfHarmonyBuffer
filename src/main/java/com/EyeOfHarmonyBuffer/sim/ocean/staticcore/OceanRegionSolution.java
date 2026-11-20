package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 区域解：区域键 + 载荷。
 *
 * <p>为什么不直接复用 {@link OceanSolution#key}：那个键是 2D 时代的
 * {@link OceanCacheKey}(seed,x0,z0,land,solver,season)，不含区域下标与质量档。
 * 区域缓存以 {@link OceanRegionKey} 为键、以本类为值，避免两套键互相冒充。
 */
public final class OceanRegionSolution {

    public final OceanRegionKey key;
    /** 阶段 2 由 patch solver 产生；阶段 1 允许为 null（仅测缓存骨架）。 */
    public final OceanSolution solution;

    public OceanRegionSolution(OceanRegionKey key, OceanSolution solution) {
        this.key = key;
        this.solution = solution;
    }
}
