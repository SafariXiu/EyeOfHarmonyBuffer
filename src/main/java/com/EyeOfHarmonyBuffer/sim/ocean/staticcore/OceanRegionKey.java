package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

/**
 * 区域解缓存键（无限世界架构 §2）。
 *
 * <p>同键必须同解：解算单元从 2.5D 的「纬度行」升级成「区域」，键里必须含
 * 所有会改变结果的量——世界种子、区域下标、海陆版本、求解器版本、质量档。
 * <p>与访问顺序无关：谁先解、解几次，同键都给同一份结果。
 */
public final class OceanRegionKey {

    /** 质量档：FAST = 游戏路径（outer 1 / substeps 15）；FULL = 验收/论文（outer 3 / substeps 30）。 */
    public static final int QUALITY_FAST = 0;
    public static final int QUALITY_FULL = 1;

    public final int worldSeed;
    public final int regionX, regionZ;
    public final int landVersion;
    public final int solverVersion;
    public final int quality;

    public OceanRegionKey(int worldSeed, int regionX, int regionZ,
                          int landVersion, int solverVersion, int quality) {
        this.worldSeed = worldSeed;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.landVersion = landVersion;
        this.solverVersion = solverVersion;
        this.quality = quality;
    }

    @Override public boolean equals(Object o) {
        if (!(o instanceof OceanRegionKey)) return false;
        OceanRegionKey k = (OceanRegionKey) o;
        return worldSeed == k.worldSeed && regionX == k.regionX && regionZ == k.regionZ
            && landVersion == k.landVersion && solverVersion == k.solverVersion
            && quality == k.quality;
    }

    @Override public int hashCode() {
        int h = worldSeed;
        h = 31 * h + regionX;
        h = 31 * h + regionZ;
        h = 31 * h + landVersion;
        h = 31 * h + solverVersion;
        h = 31 * h + quality;
        return h;
    }

    @Override public String toString() {
        return "OceanRegionKey(seed=" + worldSeed + ",r=" + regionX + "," + regionZ
            + ",land=" + landVersion + ",solver=" + solverVersion + ",q=" + quality + ")";
    }
}
