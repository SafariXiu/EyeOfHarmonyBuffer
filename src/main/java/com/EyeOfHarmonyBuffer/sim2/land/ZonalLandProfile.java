package com.EyeOfHarmonyBuffer.sim2.land;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * 本世界 V3 的**纬向陆地占比** kbar(phi)，供 Phase 1 温度骨架使用。
 *
 * <p><b>为什么需要它</b>：{@code Atmosphere.zmslkNew} 原来用固定的
 * {@code KAPPA_MEAN = 0.183} 与地球的 {@code ZF_EARTH(phi)} 做修正。0.183 是
 * coast-distance 口径的全球均值，不是陆地占比；Phase 1 必须先让纬向陆地占比
 * 来自本世界的 {@link LandV3}，才谈得上“改海陆分布，气候跟着变”。
 *
 * <p><b>采样口径</b>：X 无限，严格纬向平均不存在。这里固定一个**世界尺度的
 * 规范窗口** `4·MAX_D = 40,000 km`（与出图/验收的全球窗口一致），每纬度 2,001
 * 个等距样本（20 km），结果缓存到 seed 变化为止。它是**世界定义选择**，不是
 * 物理常数；换窗口会在文档里显式记账。
 *
 * <p><b>纯函数性</b>：给定 seed 与 LandV3 配置，结果确定；缓存只是加速。
 * 不写盘、不预计算全球瓦片（铁律④）。
 */
public final class ZonalLandProfile {

    /** 纬度节点数：-90..+90，每 5 度。 */
    public static final int N = 37;
    /** 节点间隔（度）。 */
    public static final double STEP_DEG = 5.0;
    /** 每个纬度的 X 样本数（40,000 km / 20 km + 1）。 */
    public static final int X_SAMPLES = 2_001;

    private static volatile long seed = Long.MIN_VALUE;
    private static volatile double[] profile = null;
    private static volatile double globalMean = Double.NaN;

    private ZonalLandProfile() {}

    /** 世界入口设置 seed；换 seed 自动清缓存。 */
    public static synchronized void setSeed(long ws) {
        if (ws != seed) {
            seed = ws;
            profile = null;
            globalMean = Double.NaN;
        }
    }

    /** 是否已经设置过 seed。 */
    public static boolean hasSeed() { return seed != Long.MIN_VALUE; }

    /** kbar(lat)，0..1；cos 纬度面积加权由调用方决定。 */
    public static double at(double latRad) {
        double[] p = ensure();
        double deg = Math.toDegrees(latRad);
        if (deg <= -90.0) return p[0];
        if (deg >= 90.0) return p[N - 1];
        double f = (deg + 90.0) / STEP_DEG;
        int i = (int) Math.floor(f);
        if (i >= N - 1) return p[N - 1];
        double t = f - i;
        return p[i] * (1.0 - t) + p[i + 1] * t;
    }

    /** 全球面积加权陆地占比（cos-lat 权重；两极权重为 0）。 */
    public static double globalMean() {
        ensure();
        return globalMean;
    }

    /** 37 节点副本（诊断/探针）。 */
    public static double[] profileCopy() {
        return ensure().clone();
    }

    /** 清缓存（LandV3 配置变化后由 LandV3.clearHeightCache 调用）。 */
    public static synchronized void clearCache() {
        profile = null;
        globalMean = Double.NaN;
    }

    /** 采样口径指纹（不含 seed；seed 由气候缓存键负责）。 */
    public static long configStamp() {
        long h = 0x4B415242L;   // "KBAR"
        h = h * 31 + N;
        h = h * 31 + Double.doubleToLongBits(STEP_DEG);
        h = h * 31 + X_SAMPLES;
        h = h * 31 + WorldContract.MAX_D;
        return h;
    }

    private static double[] ensure() {
        double[] p = profile;
        if (p != null) return p;
        synchronized (ZonalLandProfile.class) {
            if (profile != null) return profile;
            if (seed == Long.MIN_VALUE) {
                throw new IllegalStateException("ZonalLandProfile.seed 未设置（应由 OceanWiring/探针调用 setSeed）");
            }
            profile = compute(seed);
            return profile;
        }
    }

    private static double[] compute(long ws) {
        final double span = 4.0 * WorldContract.MAX_D;      // 40,000 km
        // Same canonical window as LandV3.landFracActual(): [0, 40,000 km).
        final double x0 = 0.0;
        final double dx = span / (X_SAMPLES - 1);
        double[] out = new double[N];
        double sw = 0.0, sk = 0.0;
        for (int i = 0; i < N; i++) {
            double deg = -90.0 + i * STEP_DEG;
            double z = WorldContract.zOfLat(deg);
            int land = 0;
            for (int j = 0; j < X_SAMPLES; j++) {
                if (LandV3.isLand(ws, x0 + j * dx, z)) land++;
            }
            out[i] = land / (double) X_SAMPLES;
            double w = Math.cos(Math.toRadians(deg));
            if (w > 0.0) { sw += w; sk += w * out[i]; }
        }
        globalMean = sw > 0.0 ? sk / sw : 0.0;
        return out;
    }
}
