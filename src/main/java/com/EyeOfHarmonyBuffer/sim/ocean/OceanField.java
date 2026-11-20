package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.ocean.staticcore.StaticOceanProduction;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

/**
 * 3D 静态海洋的**唯一生产门面**。
 *
 * <p>2026-11-16：2.5D 行解（GyreRow/SeaSurfaceTemp/CoastalLayer/BasinFinder）整支删除。
 * 这里只保留 3D 解的生命周期、SST/冰 provider 与只读访问器；所有数值来自
 * {@link StaticOceanProduction}，不再有 2.5D fallback。
 */
public final class OceanField {

    private OceanField() {}

    /** 总开关；false 时卸掉 provider，回到 SST'=0 的旧口径（A/B 用）。 */
    public static boolean OCEAN_ON = true;
    /** 生产恒为 3D；保留字段是给指纹/探针读，不允许再切回 2.5D。 */
    public static final boolean STATIC3D_ON = true;
    /** 海冰开关（进 sstModeStamp）。 */
    public static boolean SEA_ICE_ON = true;
    /** 能量平衡 SST 开关（进 sstModeStamp；3D 解内部实际口径由 solveFlagsHash 决定）。 */
    public static boolean ENERGY_SST_ON = true;
    /** OHT 缩放（研究诊断；3D 的 OHT 由 BaroclinicFlow 内部算）。 */
    public static double OHT_SCALE = 1.0;
    /** 已退役：3D 生产不再使用 2D 表层温度缓存。 */
    public static final boolean OHT_T2D_ON = false;
    /** 已退役：3D provider 只给年平异常；保留字段供 SimClimate 指纹读取。 */
    public static final boolean PHASE_SEASONAL = false;

    /** 2.5D 行数（诊断/兼容字段；3D 不用）。 */
    public static final int ROWS = 128;
    /** 3D 解在 install() 内完成，视为已预热。 */
    public static volatile int warmRowsDone = ROWS;

    private static int installedSeed = Integer.MIN_VALUE;

    /**
     * 世界级安装：先清 provider，再同步建 3D 解，最后装 SST provider。
     * 3D 首解 ~650 s（含耦合），按用户决定暂不优化；磁盘缓存仍由
     * {@link StaticOceanProduction#CACHE_DIR} 控制。
     */
    public static synchronized void install(int worldSeedInt) {
        if (!OCEAN_ON) { uninstall(); return; }
        installedSeed = worldSeedInt;
        Atmosphere.SST_PROVIDER = null;          // 建解期间禁止递归回本 provider
        StaticOceanProduction.clearDerivedCaches();
        StaticOceanProduction.solution(worldSeedInt);
        StaticOceanProduction.REGION_ON = true;
        final int s = worldSeedInt;
        Atmosphere.SST_PROVIDER = new Atmosphere.SstProvider() {
            @Override public double anomalyAt(int x, int z) {
                return StaticOceanProduction.atAnomaly(x, z, s);
            }
            @Override public double anomalyAt(int x, int z, double theta) {
                // 3D 解是年平的；季节项由 Atmosphere 自己加。
                return StaticOceanProduction.atAnomaly(x, z, s);
            }
        };
        warmRowsDone = ROWS;
    }

    /**
     * 卸掉 provider 并清派生缓存（NTHC5 末位漂移的成因：装/卸都会改所有采样 memo 的口径）。
     */
    public static synchronized void uninstall() {
        StaticOceanProduction.REGION_ON = false;
        Atmosphere.SST_PROVIDER = null;
        StaticOceanProduction.clearDerivedCaches();
        installedSeed = Integer.MIN_VALUE;
    }

    /** 当前安装的世界种子；未安装返回 Integer.MIN_VALUE。 */
    public static synchronized int installedSeed() { return installedSeed; }

    /** 3D 解的海温异常（K）。 */
    public static double anomalyAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atAnomaly(x, z, worldSeedInt);
    }

    /** 同上；theta 只保留签名，3D 解为年平。 */
    public static double anomalyAt(int x, int z, int worldSeedInt, double theta) {
        return StaticOceanProduction.atAnomaly(x, z, worldSeedInt);
    }

    /** 别名，供 GlobalClimate/地图。 */
    public static double sstAnomalyAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atAnomaly(x, z, worldSeedInt);
    }

    public static double atT(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atT(x, z, worldSeedInt);
    }

    public static double atTSeason(int x, int z, int worldSeedInt, int si) {
        return StaticOceanProduction.atTSeason(x, z, worldSeedInt, si);
    }

    public static double atU(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atU(x, z, worldSeedInt);
    }

    public static double atV(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atV(x, z, worldSeedInt);
    }

    public static boolean wetAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.wetAt(x, z, worldSeedInt);
    }

    /** 3D 兼容别名（旧探针/地图用）。 */
    public static boolean wet3DAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.wetAt(x, z, worldSeedInt);
    }

    public static double waterT3DAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atT(x, z, worldSeedInt);
    }

    public static double waterT3DSeasonAt(int x, int z, int worldSeedInt, int si) {
        return StaticOceanProduction.atTSeason(x, z, worldSeedInt, si);
    }

    public static double u3DAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atU(x, z, worldSeedInt);
    }

    public static double v3DAt(int x, int z, int worldSeedInt) {
        return StaticOceanProduction.atV(x, z, worldSeedInt);
    }

    /** 预热入口：3D 解已在 install() 内完成；保留 no-op 供 OceanWiring/探针调用。 */
    public static int warmAll(int worldSeedInt) {
        warmRowsDone = ROWS;
        return 0;
    }

    /** 轻量 SST 模式指纹；给 SimClimate 用，避免 configStamp 循环依赖。 */
    public static long sstModeStamp() {
        long h = (SEA_ICE_ON ? 0x1CE0L : 0L) | (ENERGY_SST_ON ? 0xE2E2L : 0L);
        h = h * 31 + Double.doubleToLongBits(OHT_SCALE);
        h = h * 31 + (com.EyeOfHarmonyBuffer.sim.ocean.staticcore.BaroclinicFlow.OHT_ON ? 1 : 0);
        h = h * 31 + com.EyeOfHarmonyBuffer.sim.ocean.staticcore.StaticOceanProduction.solveFlagsHash();
        if (Atmosphere.ICE_PROVIDER != null) h = h * 31 + 0x7A173L;
        return h;
    }

    /** 3D 门面自身的配置戳（供探针/旧调用；生产瓦片用 sstModeStamp）。 */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + (OCEAN_ON ? 1 : 0);
        h = h * 31 + (SEA_ICE_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(OHT_SCALE);
        h = h * 31 + sstModeStamp();
        return h;
    }
}
