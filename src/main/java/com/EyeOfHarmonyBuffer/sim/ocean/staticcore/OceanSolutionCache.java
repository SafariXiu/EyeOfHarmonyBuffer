package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** F5 静态气候解缓存；键相同即同解。 */
public final class OceanSolutionCache {

    private final Map<OceanCacheKey, OceanSolution> cache =
        new ConcurrentHashMap<OceanCacheKey, OceanSolution>();

    public OceanSolution get(OceanCacheKey key) { return cache.get(key); }

    public OceanSolution put(OceanSolution solution) {
        cache.put(solution.key, solution);
        return solution;
    }

    public int size() { return cache.size(); }

    public void clear() { cache.clear(); }
}
