package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * Zonal —— 「纬向平均降水 vs GPCP」这台仪器的**唯一实现**（M13）。
 *
 * <p><b>口径（不许再有第二份）</b>
 * <ul>
 *   <li>x 跨度 = <b>40,000 km</b>（= 地球纬圈周长）。理由见设计冻结 §327/§328：
 *       16,000 km 只覆盖 40%，而 V8 地形最大特征尺度 9,000~12,000 km
 *       ⇒ 一条纬线上只有约 1.3 个独立样本。实测同一世界、同一 gain，
 *       仅把跨度从 16,000 换成 40,000 km 就把中纬锚从 −5.0%/+1.5% 翻成 +36.4%/+71.5%。</li>
 *   <li>NX = 1000（步长 40 km，与旧口径同步长，只加长跨度）；NZ = 50（同旧）。</li>
 *   <li>band() 取 [lo, hi) 内各行的**等权**平均（均匀纬度口径）。
 *       要与 ETOPO1 的**面积加权**口径比时，用 refs/earth_land_band.py 给出的值，
 *       不要在这里偷偷加权。</li>
 * </ul>
 *
 * <p><b>判决 vs 检测（本来就不是一件事）</b>
 * <ul>
 *   <li>变化检测（run-to-run diff）：单世界即可，<b>必须固定 seed</b>。</li>
 *   <li>达标判决：<b>必须系综</b>（多世界均值），单世界没有判决资格。</li>
 * </ul>
 */
public final class Zonal {
    private Zonal() {}

    /** 40,000 km = 地球纬圈周长。 */
    public static final double XSPAN = 40_000_000.0;
    /**
     * ★ 2026-09-18：NX 1000 -> **500**（步长 40 km -> 80 km，跨度仍是 40,000 km）。
     *
     * <p><b>为什么可以降</b>：本仪器量的是**纬向平均**，其抽样误差由「相关长度」决定而非步长 ——
     * 地形相关长度约 2,222 km（L0 抖动格阵的 DCELL），40,000 km 跨度上无论 500 还是 1000 点
     * 都只有约 18 个独立样本；而地形最小特征尺度约 305 km（hf 的最高倍频），80 km 步长仍有
     * 约 3.8 个采样/特征。
     *
     * <p><b>为什么必须降</b>：`P296` 的 B 段是 2 x NX x NZ 次 `mmPerDay`，是验收套件里**唯一的
     * 瓶颈**（实测 A 段 0.4 s / B 段 1160 s）。NX 减半 ⇒ 套件从约 20 分钟回到约 10 分钟。
     *
     * <p>⚠ <b>代价</b>：所有 Zonal 口径的历史读数会有小幅位移（那是**抽样差**，不是模型差）。
     * 判据是「同一口径下的 A/B」，所以换口径必须**整体重跑基线**，不能跨口径比。
     */
    public static final int NX = 500, NZ = 50;
    public static final int GRAD = 500_000;
    public static final int CELL = PlateField.PLATE_CELL;

    /**
     * GPCP v2.3 LTM(1991-2020) 观测锚（mm/day）。
     * 由 build/eoh_probe/refs/verify_gpcp_anchors.py 复算（12 个锚 ±0.0004）——**不许手改**（M14）。
     */
    public static final double[] ANCHOR = {2.534, 2.432, 6.585, 3.580, 2.301, 2.391};
    public static final String[] ANAME = {"47-62夏", "47-62冬", "赤道夏", "赤道冬", "副热夏", "副热冬"};

    /** 第 r 行的纬度（度）。 */
    public static double latOfRow(int r) {
        int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
        return Math.toDegrees(WorldContract.latOf(z, WorldContract.Z_CYCLE));
    }

    /** 在 40,000 km 口径下取一条纬向剖面（x 取格心，避开 x=0 的格点原点）。 */
    public static double[] profile(long seed, double theta) {
        double[] p = new double[NZ];
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
            double a = 0;
            for (int c = 0; c < NX; c++) {
                int x = (int) Math.round((c + 0.5) * XSPAN / NX);
                a += PrecipField.mmPerDay(x, z, seed, CELL, theta, GRAD);
            }
            p[r] = a / NX;
        }
        return p;
    }

    /** [lo, hi) 内各行的等权平均。 */
    public static double band(double[] p, double lo, double hi) {
        double s = 0; int n = 0;
        for (int r = 0; r < NZ; r++) {
            double lat = latOfRow(r);
            if (lat >= lo && lat < hi) { s += p[r]; n++; }
        }
        return n > 0 ? s / n : 0;
    }

    /** 六个锚（夏/冬 × 中纬/赤道/副热），顺序同 ANCHOR。 */
    public static double[] anchors(double[] pS, double[] pW) {
        return new double[]{
            band(pS, 47.5, 62.5), band(pW, 47.5, 62.5),
            band(pS, 2.5, 12.5), band(pW, 2.5, 12.5),
            band(pS, 27.5, 37.5), band(pW, 27.5, 37.5)};
    }

    /** 六个锚对地球的**平均绝对相对偏差**。 */
    public static double err(double[] pS, double[] pW) {
        double[] a = anchors(pS, pW); double e = 0;
        for (int i = 0; i < 6; i++) e += Math.abs(a[i] / ANCHOR[i] - 1);
        return e / 6.0;
    }
}
