package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

/**
 * 纬度 / 气候带系统。
 *
 * 设计要点：
 * - 纬度循环长度固定为 100,000 blocks（LAT_CYCLE）。
 * - Z 轴上每隔 100,000 有一条“热带中线”（... -200k, -100k, 0, 100k, 200k ...）。
 * - 在两条相邻热带中线之间的中点（... -150k, -50k, 50k, 150k, ...）是“寒带中点”（最冷）。
 *
 * - 对任意 worldZ：
 *   1. 将其折叠到一个纬度周期内 [0, LAT_CYCLE)；
 *   2. 计算它到最近热带中线（0 或 LAT_CYCLE）的距离 d ∈ [0, LAT_CYCLE / 2]：
 *        d = min(zMod, LAT_CYCLE - zMod)
 *   3. 用 d 落在哪个区间，决定它属于哪一个气候带（热带 / 亚热带 / 温带 / 亚寒带 / 寒带）。
 *
 * 直观效果：
 * - 0、±100k、±200k ... 是热带中线（TROPIC 中点，d = 0）。
 * - ±50k、±150k、±250k ... 是寒带中点（POLAR 中点，d = 50,000）。
 * - 在一个周期内（例如 0..100k），沿 Z 方向看到的带序为：
 *   热带 → 亚热带 → 温带 → 亚寒带 → 寒带中心 → 亚寒带 → 温带 → 亚热带 → 热带。
 */

public final class ClimateLatitudes {

    private ClimateLatitudes() {
    }

    /**
     * 大气 / 纬度气候带类型。
     * - TROPIC    : 热带中心附近
     * - SUBTROPIC : 亚热带
     * - TEMPERATE : 温带（较宽）
     * - SUBPOLAR  : 亚寒带
     * - POLAR     : 寒带 / 极地
     */
    public enum Belt {
        TROPIC,
        SUBTROPIC,
        TEMPERATE,
        SUBPOLAR,
        POLAR
    }

    /** 纬度循环长度：一条热带中线到下一条热带中线的距离。 */
    public static final int LAT_CYCLE = 100_000;

    /** d 的最大值 = LAT_CYCLE / 2 = 50_000（离最近热带中线最远的位置，即寒带中点）。 */
    public static final int MAX_D = LAT_CYCLE / 2;

    /** 热带距离上限：0–8k 为热带中心区域。 */
    public static final int D_TROPIC_MAX = 8_000;

    /** 亚热带距离上限：8k–16k 为亚热带。 */
    public static final int D_SUBTROPIC_MAX = 16_000;

    /** 温带距离上限：16k–32k 为温带（较宽）。 */
    public static final int D_TEMPERATE_MAX = 32_000;

    /** 亚寒带距离上限：32k–42k 为亚寒带。 */
    public static final int D_SUBPOLAR_MAX = 42_000;

    /** 寒带距离上限：42k–50k 为寒带（靠近最冷的区域）。 */
    public static final int D_POLAR_MAX = MAX_D;

    /**
     * 根据 worldZ 返回当前所属的气候带。
     */
    public static Belt getBelt(int worldZ) {
        int d = distanceToCycleCenter(worldZ);
        return getBeltByDistance(d);
    }

    /**
     * 返回当前 worldZ 在所在气候带内部的插值参数 t ∈ [0, 1]。
     *
     * t = 0   : 靠近该带“内侧边界”（更接近热带一侧，d 较小）。
     * t = 1   : 靠近该带“外侧边界”（更接近寒带一侧，d 较大）。
     *
     * 注意：这里的“内侧/外侧”是相对于距离 d 的方向，并不区分南北。
     */
    public static double computeBeltT(int worldZ) {
        int d = distanceToCycleCenter(worldZ);
        return computeLocalTInBelt(d);
    }

    /**
     * 返回当前 worldZ 到最近热带中线的绝对距离 d ∈ [0, MAX_D]。
     * 这个值可以用来做更细的温度 / 湿度插值。
     */
    public static int getDistanceToCenter(int worldZ) {
        return distanceToCycleCenter(worldZ);
    }

    /**
     * 将 worldZ 折叠到一个纬度周期内 [0, LAT_CYCLE)。
     *
     * 例如（LAT_CYCLE = 100,000）：
     * - z =      0  → zMod = 0
     * - z =  50,000 → zMod = 50,000
     * - z = 100,000 → zMod = 0
     * - z = -50,000 → zMod = 50,000
     *
     * 即每个长度为 100,000 的区间 [n*100k, (n+1)*100k) 被折叠到同一个 0..100k 模式中。
     */
    private static int foldZToCycle(int worldZ) {
        int m = LAT_CYCLE;
        int zMod = worldZ % m;   // 可能为负
        if (zMod < 0) {
            zMod += m;           // 调整到 [0, m)
        }
        return zMod;
    }

    /**
     * 计算 worldZ 到“最近热带中线”的绝对距离 d ∈ [0, MAX_D]。
     *
     * 热带中线位于 z = n * LAT_CYCLE（..., -200k, -100k, 0, 100k, 200k, ...）。
     *
     * 对折叠后的 zMod ∈ [0, LAT_CYCLE)：
     * - 最近的热带中线可能是 0 或 LAT_CYCLE；
     * - 因此 d = min(zMod, LAT_CYCLE - zMod)。
     *
     * 举例（LAT_CYCLE = 100,000）：
     * - z =   0       → zMod = 0       → d = 0         （热带中线）
     * - z =  50,000   → zMod = 50,000  → d = 50,000    （寒带中点）
     * - z = 100,000   → zMod = 0       → d = 0         （下一条热带中线）
     * - z = -50,000   → zMod = 50,000  → d = 50,000    （寒带中点）
     */
    private static int distanceToCycleCenter(int worldZ) {
        int zMod = foldZToCycle(worldZ);  // 0..LAT_CYCLE
        int d = zMod;
        int other = LAT_CYCLE - zMod;
        if (other < d) {
            d = other;
        }
        if (d > MAX_D) {
            d = MAX_D;
        }
        return d;
    }

    /**
     * 根据距中线的距离 d 判断气候带。
     */
    private static Belt getBeltByDistance(int d) {
        if (d < D_TROPIC_MAX) {
            return Belt.TROPIC;
        } else if (d < D_SUBTROPIC_MAX) {
            return Belt.SUBTROPIC;
        } else if (d < D_TEMPERATE_MAX) {
            return Belt.TEMPERATE;
        } else if (d < D_SUBPOLAR_MAX) {
            return Belt.SUBPOLAR;
        } else {
            return Belt.POLAR;
        }
    }

    /**
     * 计算 d 在当前气候带中的局部插值 t ∈ [0, 1]。
     *
     * 这里按 d 的区间来反推当前带的起止 d，然后线性插值：
     *   t = (d - start) / (end - start)
     */
    private static double computeLocalTInBelt(int d) {
        int start;
        int end;

        if (d < D_TROPIC_MAX) {
            start = 0;
            end   = D_TROPIC_MAX;
        } else if (d < D_SUBTROPIC_MAX) {
            start = D_TROPIC_MAX;
            end   = D_SUBTROPIC_MAX;
        } else if (d < D_TEMPERATE_MAX) {
            start = D_SUBTROPIC_MAX;
            end   = D_TEMPERATE_MAX;
        } else if (d < D_SUBPOLAR_MAX) {
            start = D_TEMPERATE_MAX;
            end   = D_SUBPOLAR_MAX;
        } else {
            start = D_SUBPOLAR_MAX;
            end   = D_POLAR_MAX;
        }

        int span = end - start;
        if (span <= 0) return 0.0;

        double t = (double) (d - start) / (double) span;
        if (t < 0.0) t = 0.0;
        if (t > 1.0) t = 1.0;
        return t;
    }
}
