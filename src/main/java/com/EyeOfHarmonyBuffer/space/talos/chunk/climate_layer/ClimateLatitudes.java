package com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer;

/**
 * 纬度 / 气候带系统。
 *
 * 设计要点（V2 重构版）：
 * - 纬度循环长度 = LAT_CYCLE = 1,000,000 blocks（**下文所有数字都从它派生**），沿 Z 方向循环。
 * - 热带中线在 z = n×LAT_CYCLE（... -1M, 0, 1M ...），Z=0 即热带中线。
 * - 寒带中点（最冷）在两条热带中线正中：z = ±MAX_D = ±500k（即 ±500k, ±1.5M ...）。
 *   → 周期边界落在寒带：跨周期时冷-冷相接（无热带跳变）。
 *
 * - 对任意 worldZ：
 *   1. 将其折叠到一个纬度周期内 [0, LAT_CYCLE)；
 *   2. 计算它到最近热带中线（0 或 LAT_CYCLE）的距离 d ∈ [0, LAT_CYCLE / 2]：
 *        d = min(zMod, LAT_CYCLE - zMod)
 *   3. 用 d 落在哪个区间，决定它属于哪一个气候带（热带 / 亚热带 / 温带 / 亚寒带 / 寒带）。
 *
 * 直观效果（数字按 LAT_CYCLE=1M、MAX_D=500k 现算）：
 * - 0、±1M、±2M ... 是热带中线（TROPIC 中点，d = 0）。
 * - ±500k、±1.5M ... 是寒带中点（POLAR 中点，d = MAX_D = 500,000）。
 * - 在一个周期内（0..1M），沿 Z 方向看到的带序为（括号内为 d 的边界，见 D_* 常量）：
 *   热带(0..80k) → 亚热带(80k..160k) → 温带(160k..320k) → 亚寒带(320k..420k) →
 *   寒带(420k..500k，极点在 500k) → 亚寒带 → 温带 → 亚热带 → 热带(1M)。
 *   ⇒ 赤道→极点 = MAX_D = **500 km**；最窄的带（热带）= D_TROPIC_MAX = **80 km**。
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

    /**
     * 纬度循环长度：一条热带中线到下一条热带中线的距离。
     *
     * 历史：曾按 200,000 使用（诊断 P115/P117 证明太小：赤道→极地只有 100 km，
     * 而气压纬向廓线的特征在 b 上换算过去只有 15~34 km 宽 → 气候带成了"8~17 km 宽的
     * 条纹"，1000 km 的图上必然看到 5 次完全相同的重复）。**现行值是 1,000,000。**
     *
     * 现值下的量纲（全部可由本常量导出）：
     *   赤道→极点 = MAX_D = 500 km；最窄气候带（热带）= D_TROPIC_MAX = 80 km；
     *   Z 方向每 1M 重复一次纬度（**只重复气候，不重复海陆/地形/洋流**，见 PeriodicNoise.INFINITE_Z）。
     *
     * 注：注释里曾出现"提到 4,000,000"的说法，与常量不符，已按现值改写；那段历史不再引用。
     */
    public static final int LAT_CYCLE = 1_000_000;

    /** d 的最大值 = LAT_CYCLE / 2（离最近热带中线最远的位置，即寒带中点）。 */
    public static final int MAX_D = LAT_CYCLE / 2;

    // 以下阈值按 MAX_D 的比例给出（原来是 16k/32k/64k/84k ÷ 100k 的固定 block 值，
    // 那样写死会让"热带中心"在周期变大后缩成一条细缝）。比例保持不变。
    /** 热带距离上限：0–16% 为热带中心区域。 */
    public static final int D_TROPIC_MAX = (int) (MAX_D * 0.16);

    /** 亚热带距离上限：16%–32% 为亚热带。 */
    public static final int D_SUBTROPIC_MAX = (int) (MAX_D * 0.32);

    /** 温带距离上限：32%–64% 为温带（较宽）。 */
    public static final int D_TEMPERATE_MAX = (int) (MAX_D * 0.64);

    /** 亚寒带距离上限：64%–84% 为亚寒带。 */
    public static final int D_SUBPOLAR_MAX = (int) (MAX_D * 0.84);

    /** 寒带距离上限：84%–100% × MAX_D（= 420k–500k）为寒带（靠近最冷的区域）。 */
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
     * t = 0   : 靠近该带"内侧边界"（更接近热带一侧，d 较小）。
     * t = 1   : 靠近该带"外侧边界"（更接近寒带一侧，d 较大）。
     *
     * 注意：这里的"内侧/外侧"是相对于距离 d 的方向，并不区分南北。
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
     * 例如（LAT_CYCLE = 1,000,000）：
     * - z =          0 → zMod = 0
     * - z =    500,000 → zMod = 500,000
     * - z =  1,000,000 → zMod = 0
     * - z =   -250,000 → zMod = 750,000
     *
     * 即每个长度为 LAT_CYCLE 的区间 [n*1M, (n+1)*1M) 被折叠到同一个 0..1M 模式中。
     */
    public static int foldZToCycle(int worldZ) {
        int m = LAT_CYCLE;
        int zMod = worldZ % m;   // 可能为负
        if (zMod < 0) {
            zMod += m;           // 调整到 [0, m)
        }
        return zMod;
    }

    /**
     * 计算 worldZ 到"最近热带中线"的绝对距离 d ∈ [0, MAX_D]。
     *
     * 热带中线位于 z = n * LAT_CYCLE（..., -1M, 0, 1M, ...）。
     *
     * 对折叠后的 zMod ∈ [0, LAT_CYCLE)：
     * - 最近的热带中线可能是 0 或 LAT_CYCLE；
     * - 因此 d = min(zMod, LAT_CYCLE - zMod)。
     *
     * 举例（LAT_CYCLE = 1,000,000，MAX_D = 500,000）：
     * - z =         0 → zMod =       0 → d = 0       （热带中线）
     * - z =   500,000 → zMod = 500,000 → d = 500,000 （寒带中点）
     * - z = 1,000,000 → zMod =       0 → d = 0       （下一条热带中线）
     * - z =  -500,000 → zMod = 500,000 → d = 500,000 （寒带中点）
     */
    private static int distanceToCycleCenter(int worldZ) {
        // **保持折返循环**：纬度沿 Z 循环（赤道→极→赤道）。
        // 极点交界（z = CYCLE/2）处原本"北极=南极"重合、科氏符号突跳，
        // 现在用一条**跨越交界的极地冰原带**在地理上把南北两极的海隔开
        // （见 NoiseContinentGrid.ICE_BAND / ICE_FORCE）—— 冰是"陆"，海不连通；
        // 玩家则可以直接踩着冰原走过去，纬度照常循环。
        int zMod = foldZToCycle(worldZ);
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
     * 半球符号：[0, CYCLE/2) 为北半球(+1)，[CYCLE/2, CYCLE) 为南半球(−1)。
     * 科氏参数 f = 2Ω·sin(纬度)，符号由此决定。
     */
    public static double hemisphereSign(int worldZ) {
        int zm = foldZToCycle(worldZ);
        return zm < LAT_CYCLE / 2 ? 1.0 : -1.0;
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
