package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.zonal.SeasonalZonal;
import com.EyeOfHarmonyBuffer.sim.zonal.ZonalEquilibrium;
import com.EyeOfHarmonyBuffer.sim2.land.LandV3;
import com.EyeOfHarmonyBuffer.sim2.land.ZonalLandProfile;

/**
 * ★★★★★ 2026-11-22（P2 收口）：**Δ(φ) = 陆面年均温度 − 海面年均温度**，
 * 由本世界自己的**全柱辐射—对流平衡（RCE）**解出。
 *
 * <p>它替换的是地球表 {@code ZonalTables.tLandK + Γ·landMeanElev}（Δ 的两个分量），
 * 也是 P2 最后一块地球表。
 *
 * <p><b>做法</b>（19 纬节点，每节点两柱）：
 * <pre>
 *   陆柱：VerticalColumn.solveRCE(cosZenith(φ), ALBEDO=ALB_LAND, beta=β̄_land(φ))
 *   海柱：同一条方程，ALBEDO=ALB_SEA，beta=1（饱和表面）
 *   Δ = tSfc(陆) − tSfc(海)
 * </pre>
 *
 * <p><b>为什么 RCE 能用来做 Δ，尽管它缺经向输运</b>：输运对陆、海两柱的作用**同向同量**，
 * 相减时大部分抵消 ⟹ Δ 比 T 本身稳健得多（第 10 轮实测已见雏形）。
 *
 * <p><b>β̄_land(φ)</b> 是该纬度陆地上 {@code SoilMoisture.betaAt} 的窗口平均
 * （4·MAX_D / 401 样本）—— 世界统计量，不是可调参数。
 *
 * <p><b>成本</b>：一次性（每 seed 一次），实测 **≈55 s**（含 EBM 自辐射态构建 ~10 s + 38 柱）。
 * 属 A0-② 的「离线解一次 → 缓存」，**不得**挂到瓦片路径上；接线后应在世界初始化时**预热**。
 *
 * <p>❌ <b>2026-11-22 实测结论：本类【未接线】。</b>完整 19 纬剖面（设 DEBUG=true 可复现）：
 * <pre>
 *   lat   +0    +5   +10   +15   +20   +25   +30   +35   +40   +45   +50   +55   +60   +75   +90
 *   Δ   +0.9  −2.1  −4.2  −2.9  −2.2  −2.3  −2.1  −2.6  −7.0 −15.2 −12.6  −9.4  −7.8  −6.3  −6.1
 * </pre>
 * 热带 +0.9 K ✓、高纬 −6 K ✓ 都对；**中纬（40–55°）负了 10 K 以上**（真实约 −2…−4 K）。
 * ⟹ 缺的是**海洋热输运**（把中纬海面暖回来）与**云/反照率耦合**，不是再调 β。
 *
 * <p><b>前置缺陷（已修）</b>：开工前必须先调 ClimlabEBM.buildSelfRadiationState() 固定
 * VerticalColumn.W_REF（它是 EBM 构建的副产物，K_W ∝ 1/W_REF）；否则解柱途中被改写
 * ⟹ 前后两半用两套光学（详见 工作目录/中期/AW-WREF隐式状态与Delta锯齿-2026-11-22.md）。
 */
public final class SurfaceDelta {

    /** 纬度节点（0..90，5°）。 */
    public static final int N = 19;
    /** β̄ 采样的 x 样本数（与 ZonalLandProfile 同窗口、少一个量级以控成本）。 */
    public static final int X_SAMPLES = 401;
    /** RCE 的时间步上限（1500↔3000 步之间 Δ 变化 ≤2.6 K）。 */
    public static final int RCE_STEPS = 3000;

    private SurfaceDelta() {}

    /** 调试打印（默认关）。 */
    public static boolean DEBUG = false;

    private static volatile double[] delta = null;
    private static volatile long builtSeed = Long.MIN_VALUE;

    public static synchronized void clearCache() { delta = null; builtSeed = Long.MIN_VALUE; }

    /** 配置身份（进 configStamp）。 */
    public static long stamp() {
        long h = 0x7A1AB2L;
        h = h * 31 + N;
        h = h * 31 + X_SAMPLES;
        h = h * 31 + RCE_STEPS;
        h = h * 31 + Double.doubleToLongBits(Radiation.ALB_LAND);
        h = h * 31 + Double.doubleToLongBits(Radiation.ALB_SEA);
        h = h * 31 + Double.doubleToLongBits(SeasonalZonal.DT_ATM);
        h = h * 31 + Double.doubleToLongBits(WorldContract.MAX_D);
        // W_REF 与由此导出的 K_W 直接决定柱的温度 ⟹ 必须进指纹。
        h = h * 31 + Double.doubleToLongBits(VerticalColumn.W_REF);
        return h;
    }

    /** Δ(φ)（K）：陆面年均 − 海面年均；|lat| 对称、线性插值。 */
    public static double delta(double latRad) {
        double[] d = ensure();
        double a = Math.min(90.0, Math.abs(Math.toDegrees(latRad)));
        double f = a / 5.0;
        int i = (int) Math.floor(f);
        if (i >= N - 1) return d[N - 1];
        double t = f - i;
        return d[i] * (1.0 - t) + d[i + 1] * t;
    }

    private static double[] ensure() {
        double[] d = delta;
        long ws = ZonalLandProfile.currentSeed();
        if (d != null && ws == builtSeed) return d;
        synchronized (SurfaceDelta.class) {
            ws = ZonalLandProfile.currentSeed();
            if (delta != null && ws == builtSeed) return delta;
            if (ws == Long.MIN_VALUE) throw new IllegalStateException("SurfaceDelta: 世界种子未设置");
            delta = build(ws);
            builtSeed = ws;
            return delta;
        }
    }

    private static double[] build(long ws) {
        // ★★ 2026-11-22（第 29 轮，根因见 工作目录/中期/AW-WREF隐式状态与Delta锯齿-2026-11-22.md）：
        //   必须先**显式固定** VerticalColumn.W_REF —— 它是 EBM 自辐射态构建的副产物
        //   （ClimlabEBM:421 把 50 改成世界柱水汽均值，再 setupOptics() 重导 K_W ∝ 1/W_REF）。
        //   若在解柱途中才被改写，前几个纬度用旧光学、后面用新光学 ⟹ Δ 出现 −15 K 的锯齿。
        com.EyeOfHarmonyBuffer.sim.atmos.ClimlabEBM.buildSelfRadiationState();
        double span = 4.0 * WorldContract.MAX_D;
        double dx = span / (X_SAMPLES - 1);
        // ★ 2026-11-22（第 28 轮）：**两遍** —— 先算全部 β̄（每个点都会触发土壤 spinup），再逐纬解柱。
        //   单遍（β̄ 与列求解交替）实测会把 ±45° 的 Δ 打到 −15 K（邻纬只有 −6~−7），
        //   而固定 β 的直算给出光滑的 −7.2…−6.0 ⟹ 事故是**交替执行的状态污染**，不是物理。
        double[] betas = new double[N];
        for (int i = 0; i < N; i++) {
            betas[i] = betaLand(ws, WorldContract.zOfLat(i * 5.0), dx);
        }
        double[] out = new double[N];
        for (int i = 0; i < N; i++) {
            double latDeg = i * 5.0;
            double tl = column(latDeg, Radiation.ALB_LAND, betas[i]);
            double tsea = column(latDeg, Radiation.ALB_SEA, 1.0);
            if (DEBUG) {
                System.out.println(String.format(java.util.Locale.ROOT,
                    "SD lat=%+5.1f beta=%.4f land=%.3f sea=%.3f d=%+.3f",
                    latDeg, betas[i], tl, tsea, tl - tsea));
            }
            out[i] = tl - tsea;
        }
        return out;
    }

    /** 该纬度陆地的平均土壤湿润度（世界统计量）。 */
    private static double betaLand(long ws, int z, double dx) {
        double sum = 0.0;
        int n = 0;
        for (int j = 0; j < X_SAMPLES; j++) {
            int x = (int) Math.round(j * dx);
            if (LandV3.isLand(ws, x, z)) {
                sum += SoilMoisture.betaAt(x, z, ws, SimClimate.CELL, 0.0, Atmosphere.SOIL_GRAD_STEP);
                n++;
            }
        }
        return n > 0 ? Math.max(0.05, Math.min(1.0, sum / n)) : 1.0;
    }

    /** 单柱 RCE（时间积分到近平衡）。 */
    private static double column(double latDeg, double alb, double beta) {
        boolean sF = VerticalColumn.FIXED_TS;
        double sA = VerticalColumn.ALBEDO;
        try {
            VerticalColumn.FIXED_TS = false;
            VerticalColumn.ALBEDO = alb;
            VerticalColumn c = new VerticalColumn();
            c.init(280.0, 0.010, beta, 6.0);
            c.solveRCE(SeasonalZonal.DT_ATM, ZonalEquilibrium.annualCosZenith(latDeg), 200.0, 1.0, RCE_STEPS);
            return c.tSfc;
        } finally {
            VerticalColumn.FIXED_TS = sF;
            VerticalColumn.ALBEDO = sA;
        }
    }
}