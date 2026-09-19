package com.EyeOfHarmonyBuffer.sim.atmos;

import java.util.HashMap;

/**
 * ★★★★★★ §462：植被 / 干旱度状态量 V(x,z) —— 文献口径（Claussen et al. 2013 谱系）。
 * 默认 ENABLED=false ⇒ 逐位不变。
 *
 * 全部常数逐字取自 Groner, Claussen & Reick (2015, Clim. Past Discuss.) 对
 * Claussen et al. (2013) 概念模型的复述（refs/cp2015_veg_precip.pdf / cp2015_tables.txt），
 * 无一处自创：
 *   Veq(P) = 1                                 P >= P_C2
 *          = (P - P_C1)/(P_C2 - P_C1)          P_C2 > P > P_C1
 *          = 0                                 P <= P_C1
 *   P_C1 = 150/365 mm/day , P_C2 = 500/365 mm/day   Sahelian 草地（观测物种分布包络）
 *   dV_i/dt = (Veq_i - V_i)/TAU , TAU = 5 yr        (Liu et al. 2006b)
 *   P_eff = P_d + D_B * V_S , D_B = 140/365 mm/day  (Liu et al. 2006a; Claussen et al. 2013)
 *
 * 反照率方向：tropical leaves are darker than steppe grasses (White 1983) ⇒ ALB_DESERT > ALB_LAND ✓
 * 主通道是蒸发不是反照率（Hély et al. 2009：叶面积最多 3 倍差；Rachmayani et al. 2015）
 * ⇒ RS_BARE 是物理第一通道。§440 否证的是它作为全局均匀旋钮，状态依赖形式从未被否证。
 *
 * ★ 闭环增益的闭式解（性能的关键）：由 P_eff = P_d + D_B·V 与斜坡 Veq，不动点可闭式求出：
 *   g  = D_B / (P_C2 - P_C1) = 140/350 = 0.40        <- 文献常数直接给出，见 §461
 *   V* = 0                                        若 P_d <= P_C1
 *      = 1                                        若 P_d >= P_C2 - D_B
 *      = (P_d - P_C1) / ((P_C2-P_C1)*(1-g))       否则
 * g < 1 ⇒ 唯一稳定根 ⇒ 闭式即精确。当 g >= 1 时分母 <= 0 ⇒ 闭式失效本身就是双稳的信号。
 *
 * 性能设计（用户硬约束）：① 闭式解 ⇒ D_B 通道零次 mmPerDay 重算；
 * ② 外层只迭代 RS_BARE/反照率这些次级通道，VEG_ITER（默认 3）次封顶；
 * ③ 逐点 MEMO，键含全部开关与常数（D58 / §440 第 27 条的教训）；④ 防重入 + 代价计数器。
 */
public final class Vegetation {
    private Vegetation() {}

    /** 总开关。默认 false ⇒ vegAt 恒返回 1.0（无裸地）⇒ 反照率与旧行为逐位相同。 */
    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （4 个 false：HadleyCell/SoilMoisture/StationaryWave/Vegetation；2 个 true：OceanField/SimTerrain）。
     * **本份的含义是：Vegetation（植被—干旱度 §459）。** 引用时务必写全类名（如 `Vegetation.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    public static boolean ENABLED = false;

    // ---- 文献常数（全部逐字，见类头） ----
    /** 草地/疏林草地的下阈值（Sahelian 型，150 mm/yr）。 */
    public static final double P_C1 = 150.0 / 365.0;
    /** 上阈值（500 mm/yr）。 */
    public static final double P_C2 = 500.0 / 365.0;
    /** 降水反馈系数 D_B = 140 mm/yr（Liu et al. 2006a; Claussen et al. 2013）。 */
    public static final double D_B = 140.0 / 365.0;
    /** 记忆时间尺度 tau = 5 yr（Liu et al. 2006b）。**稳态世界里它只决定响应时间**。 */
    public static final double TAU = 5.0;

    /** 裸地（沙/裸岩）反照率。第二通道（方向：White 1983）。 */
    public static double ALB_DESERT = 0.35;
    /** 裸地表面阻力（s/m）。**第一通道**（Hély et al. 2009 / Rachmayani et al. 2015）。0 = 不接。 */
    public static double RS_BARE = 0.0;

    /** 外层（次级通道）最大迭代次数与收敛容差。 */
    public static int VEG_ITER = 3;
    public static double VEG_TOL = 5.0e-3;

    /**
     * ★ §466：`RS_BARE` 通道的 pass 数（**不是迭代**）。1 = 多算一遍 `A/e1/e2` 块，
     * `rsTot` 取第一遍收敛后的 `V̄`；0 = 不刷新（`RS_BARE` 无效）。
     * ⚠ §464 实测：一次桶自旋 4.378 ms/点 ⇒ **迭代方案被性能否证**，所以这里封顶为 pass。
     */
    public static int RS_PASS = 1;

    // ---- 代价与诊断（探针读数；正常路径只做加法） ----
    public static long solveCount = 0;
    public static long iterTotal = 0;
    public static long SOLVE_NANOS = 0;
    public static long reentryBlocked = 0;
    /** 最近一次的闭环增益（= D_B/(P_C2-P_C1)，常数；导出以便探针自证）。 */
    public static double lastGain = -1;
    /** 最近一次的外层迭代次数与残差。 */
    public static int lastIterations = 0;
    public static double lastResidual = -1;
    /** 闭式解是否失效过（= 分母 <= 0 ⇒ 双稳信号）。 */
    public static long bistableDetected = 0;

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();
    private static int depthGet() { Integer v = DEPTH.get(); return v == null ? 0 : v; }
    private static void depthSet(int v) { if (v <= 0) DEPTH.remove(); else DEPTH.set(v); }
    public static boolean isSolving() { return depthGet() > 0; }

    /** 解 V 期间的暂存值：内层查询读它，避免递归。 */
    private static final ThreadLocal<double[]> CUR = new ThreadLocal<>();

    /** 注入给 mmPerDay 的 V 覆盖：非 null 时降水用这个 V，从而不重入 vegAt。 */
    public static final ThreadLocal<double[]> V_OVERRIDE = new ThreadLocal<>();

    private static final HashMap<Long, Double> MEMO = new HashMap<>();
    private static long stamp = Long.MIN_VALUE;

    public static synchronized void invalidate() { MEMO.clear(); stamp = Long.MIN_VALUE; }

    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + (ENABLED ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(ALB_DESERT);
        h = h * 31 + Double.doubleToLongBits(RS_BARE);
        h = h * 31 + VEG_ITER;
        h = h * 31 + Double.doubleToLongBits(VEG_TOL);
        h = h * 31 + Double.doubleToLongBits(P_C1);
        h = h * 31 + Double.doubleToLongBits(P_C2);
        h = h * 31 + Double.doubleToLongBits(D_B);
        h = h * 31 + RS_PASS;
        return h;
    }

    /** 闭环增益 g = D_B/(P_C2-P_C1)。 */
    public static double gain() { return D_B / (P_C2 - P_C1); }

    /** 斜坡平衡盖度 Veq(P)（mm/day）。 */
    public static double veq(double pMmDay) {
        if (pMmDay <= P_C1) return 0.0;
        if (pMmDay >= P_C2) return 1.0;
        return (pMmDay - P_C1) / (P_C2 - P_C1);
    }

    /**
     * 给定【无植被反馈的】降水 P_d，闭式求不动点 V*。
     * ⚠ 分母 (1-g) <= 0 时闭式失效 ⇒ 记录 bistableDetected 并回落到斜坡值。
     */
    public static double vStarClosed(double pd) {
        double g = gain();
        lastGain = g;
        if (pd <= P_C1) return 0.0;
        if (pd >= P_C2 - D_B) return 1.0;
        double den = (P_C2 - P_C1) * (1.0 - g);
        if (den <= 1.0e-12) { bistableDetected++; return veq(pd); }
        double v = (pd - P_C1) / den;
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    /** 降水回调：给定 V，返回【不含 D_B·V 的】干降水 P_d。 */
    public interface PrecipFn { double at(double v); }

    private static long key(int x, int z, long seed, int cell) {
        long h = seed * 0x9E3779B97F4A7C15L;
        h ^= (long) x * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) z * 0x165667B19E3779F9L;
        h ^= (long) cell * 0x27D4EB2F165667C5L;
        h ^= configStamp();
        h ^= (h >>> 29);
        return h;
    }

    /** 该点的植被/干旱度状态 V ∈ [0,1]。未打开时恒为 1.0。 */
    public static double vegAt(int x, int z, long seed, int cell, PrecipFn pfn) {
        if (!ENABLED) return 1.0;
        if (depthGet() > 0) { reentryBlocked++; double[] c = CUR.get(); return c == null ? 1.0 : c[0]; }
        long k = key(x, z, seed, cell);
        synchronized (Vegetation.class) {
            long h = ((long) seed) * 31 + cell;
            if (h != stamp) { MEMO.clear(); stamp = h; }
            Double v = MEMO.get(k);
            if (v != null) return v;
        }
        long t0 = System.nanoTime();
        double[] cur = new double[]{1.0};
        CUR.set(cur);
        double[] ov = new double[]{1.0};
        V_OVERRIDE.set(ov);
        depthSet(depthGet() + 1);
        double v = 1.0;
        int it = 0;
        double res = -1;
        try {
            for (; it < VEG_ITER; it++) {
                ov[0] = v;
                double pd = pfn.at(v);       // 干降水（honour RS_BARE / albedo 通道）
                double nv = vStarClosed(pd);
                res = Math.abs(nv - v);
                v = nv;
                if (res < VEG_TOL) { it++; break; }
            }
        } finally {
            V_OVERRIDE.remove();
            CUR.remove();
            depthSet(depthGet() - 1);
        }
        lastIterations = it;
        lastResidual = res;
        synchronized (Vegetation.class) { MEMO.put(k, v); }
        solveCount++; iterTotal += it; SOLVE_NANOS += System.nanoTime() - t0;
        return v;
    }

    /** 反照率相对 ALB_LAND 的增量：(ALB_DESERT - ALB_LAND)*(1 - V)。 */
    public static double albedoAdd(double v) {
        if (!ENABLED) return 0.0;
        double a = (ALB_DESERT - Radiation.ALB_LAND) * (1.0 - v);
        return a < 0.0 ? 0.0 : a;
    }

    /** 表面阻力（s/m）：RS_BARE*(1 - V)。 */
    public static double rsOf(double v) {
        if (!ENABLED) return 0.0;
        return RS_BARE * (1.0 - v);
    }
}
