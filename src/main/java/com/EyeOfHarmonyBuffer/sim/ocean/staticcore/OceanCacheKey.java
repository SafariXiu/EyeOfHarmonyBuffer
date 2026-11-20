package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * F5 缓存键：同键必须同解。
 *
 * <p>★ 2026-11-04（A6）：**补上网格身份**。原来只有 (seed, x0, z0, landVersion, solverVersion, season)，
 * 于是把生产切到球面/地形网格时会命中旧键、读到旧网格的解——正是守则里的"缓存键漏了输入"
 * 那一类坑（N5）。现在键里含 sphere / nx / ny / nz / bathyMask 五项。
 *
 * <p>保留 6 参构造器只为兼容既有探针（它们只测缓存语义）；生产路径一律用
 * {@link #of(OceanGrid, int, int, int, int)}。
 */
public final class OceanCacheKey {

    public final int worldSeed;
    public final int x0, z0;
    public final int landVersion;
    public final int solverVersion;
    public final int season;
    /** 网格身份（2026-11-04）：球面？维度？是否启用地形掩膜？ */
    public final boolean sphere;
    public final int nx, ny, nz;
    public final boolean bathyMask;

    /** 兼容构造器（无网格身份）：只给只测缓存语义的探针用。 */
    public OceanCacheKey(int worldSeed, int x0, int z0,
                         int landVersion, int solverVersion, int season) {
        this(worldSeed, x0, z0, landVersion, solverVersion, season, false, 0, 0, 0, false);
    }

    public OceanCacheKey(int worldSeed, int x0, int z0,
                         int landVersion, int solverVersion, int season,
                         boolean sphere, int nx, int ny, int nz, boolean bathyMask) {
        this.worldSeed = worldSeed;
        this.x0 = x0; this.z0 = z0;
        this.landVersion = landVersion;
        this.solverVersion = solverVersion;
        this.season = season;
        this.sphere = sphere;
        this.nx = nx; this.ny = ny; this.nz = nz;
        this.bathyMask = bathyMask;
    }

    /** 从网格造键：网格身份与 BATHY_MASK 一起折入。 */
    public static OceanCacheKey of(OceanGrid g, int worldSeed, int landVersion, int solverVersion, int season) {
        return new OceanCacheKey(worldSeed, g.x0, g.z0, landVersion, solverVersion, season,
                g.sphere, g.nx, g.ny, g.nz, OceanGrid.BATHY_ON && OceanGrid.BATHY_MASK_ON);
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof OceanCacheKey)) return false;
        OceanCacheKey k = (OceanCacheKey) o;
        return worldSeed == k.worldSeed && x0 == k.x0 && z0 == k.z0
            && landVersion == k.landVersion && solverVersion == k.solverVersion
            && season == k.season
            && sphere == k.sphere && bathyMask == k.bathyMask
            && nx == k.nx && ny == k.ny && nz == k.nz;
    }

    @Override public int hashCode() {
        int h = worldSeed;
        h = 31 * h + x0;
        h = 31 * h + z0;
        h = 31 * h + landVersion;
        h = 31 * h + solverVersion;
        h = 31 * h + season;
        h = 31 * h + (sphere ? 1 : 0);
        h = 31 * h + nx;
        h = 31 * h + ny;
        h = 31 * h + nz;
        h = 31 * h + (bathyMask ? 1 : 0);
        return h;
    }

    @Override public String toString() {
        return "OceanCacheKey(seed=" + worldSeed + ",x0=" + x0 + ",z0=" + z0
            + ",land=" + landVersion + ",solver=" + solverVersion + ",season=" + season
            + ",sphere=" + sphere + "," + nx + "x" + ny + "x" + nz + ",bathyMask=" + bathyMask + ")";
    }
}
