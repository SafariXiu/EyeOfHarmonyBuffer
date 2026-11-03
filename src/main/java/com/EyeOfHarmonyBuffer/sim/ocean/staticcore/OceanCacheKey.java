package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/** F5 缓存键：同键必须同解。 */
public final class OceanCacheKey {

    public final int worldSeed;
    public final int x0, z0;
    public final int landVersion;
    public final int solverVersion;
    public final int season;

    public OceanCacheKey(int worldSeed, int x0, int z0,
                         int landVersion, int solverVersion, int season) {
        this.worldSeed = worldSeed;
        this.x0 = x0; this.z0 = z0;
        this.landVersion = landVersion;
        this.solverVersion = solverVersion;
        this.season = season;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof OceanCacheKey)) return false;
        OceanCacheKey k = (OceanCacheKey) o;
        return worldSeed == k.worldSeed && x0 == k.x0 && z0 == k.z0
            && landVersion == k.landVersion && solverVersion == k.solverVersion
            && season == k.season;
    }

    @Override public int hashCode() {
        int h = worldSeed;
        h = 31 * h + x0;
        h = 31 * h + z0;
        h = 31 * h + landVersion;
        h = 31 * h + solverVersion;
        h = 31 * h + season;
        return h;
    }

    @Override public String toString() {
        return "OceanCacheKey(seed=" + worldSeed + ",x0=" + x0 + ",z0=" + z0
            + ",land=" + landVersion + ",solver=" + solverVersion
            + ",season=" + season + ")";
    }
}
