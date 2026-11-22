package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile;

/**
 * ★★★★★ 2026-11-22（P4 全核心去地球表）：**纬向平均风 = 本世界局地风场的收敛窗口平均**。
 *
 * <p>它替换的是地球表 {@code ZonalTables.uZm}（U_ZM_JAN/JUL、U_ANN_12…），消费点是
 * stormGate / stormGateWide / uShearAbs / 风暴轴搜索。
 *
 * <p><b>为什么这不是「又造一个常数」</b>：
 * <ul>
 *   <li>它是一个**世界统计量**——对模型**自己的** {@link Atmosphere#windAt} 沿 x 做窗口平均；
 *   <li>窗口与纬向陆地占比**同一把尺子**（4·MAX_D / 2,001 样本），已实测**收敛**：
 *       n=2001 与 n=8001 的差 ≤ 0.04 m/s（标准误 0.02~0.25）；
 *   <li>任意种子只换 seed；解一次后 O(1)，并进 {@code configStamp}。
 * </ul>
 *
 * <p>⚠ <b>重入守卫</b>：构建期调 {@code windAt → pressureAnomaly}；若将来干暖项（默认关）改用它，
 * 会形成回路 ⟹ 构建期内 {@link #uZm} 直接返回 0。
 *
 * <p>❌ <b>2026-11-22 实测结论：本类目前【未接线】。</b> 接线试过又回退了 —— 原因不是数值，而是物理：
 * 本世界风场（{@code windAt}，2026-11-20 删掉外生风表后）**没有年均西风急流**：
 * 年均 u 是**反对称**的（−45°: −4.0 / +45°: +4.0 m/s），而地球是双半球都西风（+4.26/+4.26）。
 * 风暴门（{@code stormGate}）本质需要一个**急流**，急流需要纬向动量收支 ⟹ 属于未完成的动力学。
 * 保留本类是因为：**收敛性已验证**（2001 vs 8001 差 ≤0.04 m/s），一旦模型有了急流，直接接线即可。
 * 详见 {@code 工作目录/中期/AJ-P4-uZm回退-2026-11-22.md}。
 */
public final class ZonalWindStat {

    /** 纬度节点间隔（度）。 */
    public static final int N_LAT = 19;
    /** 季节节点数（θ = 0, π/2, π, 3π/2）。 */
    public static final int N_SEAS = 4;
    /** 每纬度的 x 采样数 —— 与 {@code ZonalLandProfile.X_SAMPLES} 同口径。 */
    public static final int X_SAMPLES = 2001;

    private ZonalWindStat() {}

    private static volatile double[][] tab = null;
    private static volatile long builtSeed = Long.MIN_VALUE;
    private static volatile int builtCell = Integer.MIN_VALUE;
    private static final ThreadLocal<Boolean> BUILDING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** 清缓存（换种子/换 cell/探针）。 */
    public static synchronized void clearCache() { tab = null; builtSeed = Long.MIN_VALUE; builtCell = Integer.MIN_VALUE; }

    /** 配置身份（进 {@code SimClimate.configStamp()}；当前未接线，保留给接线时用）。 */
    public static long stamp() {
        long h = 0x7A1A8L;
        h = h * 31 + N_LAT;
        h = h * 31 + N_SEAS;
        h = h * 31 + X_SAMPLES;
        h = h * 31 + Double.doubleToLongBits(WorldContract.MAX_D);
        return h;
    }

    /** 纬向平均风（m/s，正 = 向东），按 19 纬 × 4 季线性插值；|lat| 对称。 */
    public static double uZm(double latDeg, double theta) {
        if (BUILDING.get()) return 0.0;
        double[][] t = ensure();
        double a = Math.min(90.0, Math.abs(latDeg));
        double f = a / 5.0;
        int i = (int) Math.floor(f);
        double tl = f - i;
        double se = theta / (Math.PI / 2.0);
        int s0 = (int) Math.floor(se);
        double ts = se - s0;
        s0 = ((s0 % N_SEAS) + N_SEAS) % N_SEAS;
        int s1 = (s0 + 1) % N_SEAS;
        if (i >= N_LAT - 1) i = N_LAT - 2;
        double u0 = t[i][s0] * (1 - ts) + t[i][s1] * ts;
        double u1 = t[i + 1][s0] * (1 - ts) + t[i + 1][s1] * ts;
        double u = u0 * (1 - tl) + u1 * tl;
        return latDeg >= 0.0 ? u : -u;
    }

    private static double[][] ensure() {
        double[][] t = tab;
        long ws = ZonalLandProfile.currentSeed();
        int cell = SimClimate.CELL;
        if (t != null && ws == builtSeed && cell == builtCell) return t;
        synchronized (ZonalWindStat.class) {
            ws = ZonalLandProfile.currentSeed();
            cell = SimClimate.CELL;
            if (tab != null && ws == builtSeed && cell == builtCell) return tab;
            if (ws == Long.MIN_VALUE) throw new IllegalStateException("ZonalWindStat: 世界种子未设置");
            tab = build(ws, cell);
            builtSeed = ws;
            builtCell = cell;
            return tab;
        }
    }

    private static double[][] build(long ws, int cell) {
        BUILDING.set(Boolean.TRUE);
        try {
            double span = 4.0 * WorldContract.MAX_D;
            double dx = span / (X_SAMPLES - 1);
            int grad = SimClimate.GRAD_STEP;
            double[][] out = new double[N_LAT][N_SEAS];
            for (int i = 0; i < N_LAT; i++) {
                int z = WorldContract.zOfLat(i * 5.0);
                for (int s = 0; s < N_SEAS; s++) {
                    double th = s * Math.PI / 2.0;
                    double sum = 0.0;
                    for (int j = 0; j < X_SAMPLES; j++) {
                        int x = (int) Math.round(j * dx);
                        sum += Atmosphere.windAt(x, z, ws, cell, th, grad)[0];
                    }
                    out[i][s] = sum / X_SAMPLES;
                }
            }
            return out;
        } finally {
            BUILDING.set(Boolean.FALSE);
        }
    }
}