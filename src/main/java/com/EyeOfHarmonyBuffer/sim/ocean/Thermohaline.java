package com.EyeOfHarmonyBuffer.sim.ocean;

import com.EyeOfHarmonyBuffer.sim.Frozen;

import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

/**
 * ★★★★★ 温盐环流（THC）—— **Stommel (1961) 的 2-box 模型**（纯函数，O(1)）。
 *
 * <h3>为什么是 2-box（我自行裁定的范围）</h3>
 * <p>409 审计结论：全仓 0 处 THC，且前置是【深层海洋结构】（本仓只有表层/行求解）。
 * 完整 OGCM（Bryan-Cox 类）远超范围。Stommel (1961) 的 2-box 模型是【原始文献本身】，
 * 且给出【闭式稳态解】=> O(1)、纯函数，与本仓架构天然兼容。
 *
 * <h3>★★★★★ 全部公式与常数的出处（逐字核对过，两份独立来源互证）</h3>
 * <pre>
 *   原始文献：**Stommel (1961)**, Tellus **13**, 224-230,
 *            DOI 10.1111/j.2153-3490.1961.tb00079.x
 *   复现来源（二者逐字一致）：
 *     (a) PIK Potsdam 教程 Klimageschichte Uebung 9 "the Stommel box model"
 *     (b) Tellus 79 (2026) Marotzke 回顾, DOI 10.16993/tellus.4175
 *
 *   流量律（PIK 方程 2-4）：
 *     q = k[rho1 - rho2] = k[alpha(T2-T1) - beta(S2-S1)] = k[alpha*DT - beta*DS]
 *   where k is a hydraulic constant。
 *
 *   状态方程（PIK 方程 1）：rho = rho0 - alpha(T-T0) + beta(S-S0)
 *   守恒方程（PIK 方程 5-8）：
 *     dT1/dt = -|q|*DT + gamma(T1*-T1)
 *     dT2/dt = +|q|*DT + gamma(T2*-T2)
 *     dS1/dt = +|q|*DS - H
 *     dS2/dt = -|q|*DS + H
 *   快速温度松弛极限（PIK 方程 9-10）：
 *     dDS/dt = -2|q|*DS + 2H
 *     dq/dt  = -k*beta*dDS/dt
 *   稳态解析解（PIK 方程 11）：
 *     q1,2 = A/2 +/- sqrt( (A/2)^2 - B )      （q > 0，热主导；q2 不稳定，舍弃）
 *     q3,4 = A/2 +/- sqrt( (A/2)^2 + B )      （q < 0，盐主导；q3 舍弃）
 *   分岔点：q1 = q2  =>  Hc = A^2 / (4B)
 *   其中 A = k*alpha*DT，B = k*beta*H。
 *
 *   系数：
 *     alpha = 1.67e-4 K^-1    —— Vallis (2017) Atmospheric and Oceanic Fluid Dynamics,
 *                               Table 1.2, page 33（经 Oceananigans.jl 源码引用）
 *     beta  = 7.80e-4 psu^-1  —— 同上
 *     S_REF = 35.16504 g/kg   —— TEOS-10 "Getting Started"（IOC/SCOR/IAPSO 国际标准）
 *                               原始文献 Millero et al. (2008)。逐字：
 *                               "Standard Ocean Reference Salinity S_SO = 35.165 04 g kg^-1
 *                                being exactly 35*u_PS"
 * </pre>
 *
 * <h3>★★★★ 本实现的两个诚实边界（不假装）</h3>
 * <ol>
 *   <li><b>A 完全确定</b>：<code>A = alpha*DT</code>，alpha 有出处、DT 来自模型自己的温度场。
 *       <b>现在就可用</b>。</li>
 *   <li>★ <b>B 的【绝对值】已标定</b>（2026-10-15）：Stommel 的 H 是"等效盐通量"(psu/s)，
 *       把 (E-P)(mm/day) 换成 psu/s 需要【混合层深度】h —— 该量现取自有出处的观测产品，
 *       见 {@link #H_MIXED_LAYER_M} 的来路（GOSML / Argo，本仓从原始 NetCDF 自算）。
 *       <p>⚠ 注意：原始设计文献 <b>de Boyer Montegut et al. (2004)</b> 给出的是
 *       <b>判据</b>（ΔT=0.2 °C ／ Δσ=0.03 kg m^-3，10 m 参考深度），
 *       而**没有**任何全球平均 MLD 数值（已逐字核实）⟹ 数值不可引它。</li>
 * </ol>
 * <p>=> 求解器（给定 A、B）**完全可验证**；B 的标定是一条独立、有出处的路径。
 * <p>★ 本类当前<b>零生产调用方</b>，且 {@link #configStamp()} <b>未被</b>
 * {@code SimClimate.configStamp()} 折入 ⟹ 改动本类的常数<b>不改变任何输出</b>。
 *
 * <h3>★ 性能</h3>
 * <p>闭式解：一个 sqrt。**无迭代、无缓存、无分配**（返回复用数组）。
 * O(1)，与 CoastalLayer / SeaSurfaceTemp 同级。纯函数 => 不破坏本仓「无状态」架构。
 */
@Frozen(reason = "重构冻结：热盐环流等待 3D 海洋替换", until = "refactor: 3D ocean")
public final class Thermohaline {

    private Thermohaline() {}

    /** 热膨胀系数 alpha（K^-1）。出处：Vallis (2017) Table 1.2 p.33。 */
    public static final double ALPHA = 1.67e-4;

    /** 盐收缩系数 beta（psu^-1）。出处：Vallis (2017) Table 1.2 p.33。 */
    public static final double BETA = 7.80e-4;

    /** 参考盐度 S_REF（g/kg）。出处：TEOS-10 / Millero et al. (2008)。 */
    public static final double S_REF = 35.16504;

    /**
     * ★ 开关。★★ **2026-10-15 用户裁决：打开（默认 true）** ✓
     * （此前为 false —— 按本仓「新物理一律带开关、默认关」的纪律；现由用户本人裁决打开 ✓）
     *
     * <p>★ 接线（待裁决/427）：游戏世界加载走
     * {@code WorldChunkManagerTalos2 → OceanWiring.onWorld → install()}；
     * 读数出口是 {@link #worldState(int)}（游戏内 {@code /talos_here} 打印一行 THC ✓）。
     *
     * <p>★★ **打开它不改变任何气候输出**（逐位不变 ✓）：本类是**纯诊断出口** ——
     * 没有任何生产模块读它、它也不反哺任何场；并且它**没有**被折入
     * {@code SimClimate.configStamp()} ⟹ 气候瓦片缓存身份不变 ✓。
     *
     * <p>★ 成本：{@link #worldState} 采样一次会建所需气候瓦片 + 按需解海洋行 ⟹
     * **每个世界首次约 20 s**（427 实测 22.0 s），此后走缓存（0.0 s）✓。
     * 而且它**只在有人来读时**才发生（游戏内 /talos_here、探针）——
     * 世界生成路径不读它 ⟹ 不拖慢区块生成 ✓。
     */
    public static boolean THERMOHALINE_ON = true;

    /**
     * 热驱动项 A = alpha * DT（Stommel 的 k*alpha*DT 去掉 k）。
     *
     * @param tLowK  低纬 box 的温度（K）
     * @param tHighK 高纬 box 的温度（K）
     * @return A（K^-1 * K = 无量纲）
     */
    public static double thermalDrive(double tLowK, double tHighK) {
        return ALPHA * (tLowK - tHighK);
    }

    /**
     * 盐驱动项 B = beta * H（Stommel 的 k*beta*H 去掉 k）。
     * <p>⚠ H 是【等效盐通量】(psu/s)，其标定见类注释的「诚实边界」第 2 条。
     */
    public static double halineDrive(double hEquivPsuPerSec) {
        return BETA * hEquivPsuPerSec;
    }

    /**
     * ★★★★★ Stommel 的稳态解（PIK 方程 11）。返回复用数组。
     *
     * @param a 热驱动 A
     * @param b 盐驱动 B（应 >= 0）
     * @return {disc, q1, q4, stateCode}
     *         disc  = (A/2)^2 - B
     *         q1    = A/2 + sqrt(disc)  （热主导，q>0；disc<0 时为 NaN）
     *         q4    = A/2 - sqrt((A/2)^2 + B)  （盐主导，q<0；恒有定义）
     *         stateCode： 1 = 仅热主导稳定（disc>0）
     *                     0 = 临界（disc==0）
     *                    -1 = 仅盐主导稳定（disc<0）
     */
    private static final double[] OUT = new double[4];

    public static double[] solve(double a, double b) {
        double half = a * 0.5;
        double aa = half * half;
        double disc = aa - b;
        double q1 = disc >= 0.0 ? half + Math.sqrt(disc) : Double.NaN;
        double q4 = half - Math.sqrt(aa + b);
        int st = disc > 0.0 ? 1 : (disc < 0.0 ? -1 : 0);
        synchronized (OUT) {
            OUT[0] = disc; OUT[1] = q1; OUT[2] = q4; OUT[3] = st;
            return OUT;
        }
    }

    /** 分岔点 Hc = A^2 / (4B)（PIK 问题 6）。B<=0 时返回 +Inf。 */
    public static double bifurcationHc(double a, double b) {
        return b > 0.0 ? (a * a) / (4.0 * b) : Double.POSITIVE_INFINITY;
    }

    /**
     * ★★★★★ **混合层深度 h（m）** —— 把淡水通量 (E-P) 换成 Stommel 的等效盐通量 H 用。
     *
     * <h4>换算的来路（这是【守恒律】，不是经验公式）</h4>
     * <pre>
     *   淡水通量 (E-P) [m/s] 加进厚度 h [m] 的混合层：
     *     dS/dt = -S*(E-P)/h
     *   => Stommel 的 H（等效盐通量，psu/s） ≡ S_REF*(E-P)/h
     *   于是 B = beta*H = beta*S_REF*(E-P)/h
     * </pre>
     *
     * <h4>★★★★★ 取值与出处（2026-10-15 标定；证据：Talos2 待裁决/425）</h4>
     * <pre>
     *   值 = **53.88 m**
     *   出处 = **GOSML: Global Ocean Surface Mixed Layer Statistical Monthly Climatology**
     *          Johnson, G. C., &amp; Lyman, J. M. (2022), J. Geophys. Res. Oceans **127**,
     *          e2021JC018219, DOI **10.1029/2021JC018219**（NOAA / PMEL；数据源 **Argo CTD**）
     *          判据 = **Holte &amp; Talley (2009)** 密度算法（J. Atmos. Oceanic Technol. 26(9),
     *                 1920-1939, DOI 10.1175/2009JTECHO543.1）—— 与 de Boyer Montegut (2004)
     *                 的 Δσ = 0.03 kg m^-3 一档**同族**。
     *   口径 = **全球 · 面积加权(cos lat) · 年均 · mean**
     *   取值方式 = ★ **本仓从原始 NetCDF 自己算**（不是转引）：
     *          https://www.pmel.noaa.gov/gosml/data_files/mixed_layer_properties_mean.nc
     *          变量 depth_mean(month=12, lat=180, lon=360)，海洋格点 34,469，
     *          先对 12 个月 nanmean，再按 cos(lat) 加权
     *   口径敏感性（同一产品，结论都不变）：
     *          全球 median（_50.nc）= **51.82 m**（差 4%）
     *          60-85N mean（深层水形成区）= **62.92 m**（差 17%）
     * </pre>
     * <p>⚠ <b>为什么不用 de Boyer Montegut et al. (2004) 的数值</b>（J. Geophys. Res.: Oceans
     * <b>109</b>, C12003, DOI <b>10.1029/2004JC002378</b>）：该文给的是<b>判据</b>与
     * 【逐 2° x 逐月 网格中位数】场 + 区域值，**全篇没有任何全球平均 MLD 数值**
     * （已逐字核实 ✓ 见 425 第 3.3 节）。它的「It is 40 m on average」指的是
     * <b>αdev（中位绝对偏差 = 离散度）</b>，**不是** MLD 本身 —— 引它是陷阱。
     *
     * <h4>★ 为什么这个值够用（对 h 不敏感）</h4>
     * <p>判别式翻转为盐主导需要 h &lt; h_crit ≈ <b>2.94e-5 m</b>
     * （{@link #criticalMixedLayerM}），而 53.88 m 是它的 <b>1.8e6 倍</b>；
     * h 取 10 ~ 200 m 的任意值，热驱动都压倒盐驱动 5.3 ~ 6.8 个数量级
     * ⟹ **THC 恒为热主导**（与 423 的零参数路径一致）。
     */
    public static double H_MIXED_LAYER_M = 53.88;

    /**
     * ★★★★★★ **临界混合层深度 h_crit（m）** —— 【零新参数】的判据。
     *
     * <pre>
     *   disc = (A/2)^2 - B,   B = beta*S_REF*(E-P)/h
     *   disc < 0  <=>  (A/2)^2 < beta*S_REF*(E-P)/h
     *   =>  **h < h_crit = 4*beta*S_REF*(E-P) / A^2**
     * </pre>
     *
     * <p><b>物理含义</b>：混合层越浅（或淡水强迫越强），越容易翻到【盐主导】。
     * <p>⟹ 它回答的正是 Stommel 的核心问题（THC 会不会翻转），
     * 而且**只需要有出处的量**（beta / S_REF）+ 模型自己的场（A / E-P）。
     *
     * @param a          热驱动 A = alpha*DT（{@link #thermalDrive}）
     * @param eminusPMmPerDay (E-P)，mm/day（正 = 净蒸发，使表层变咸）
     * @return h_crit（m）；A<=0 或 E-P<=0 时返回 +Inf（永不翻转）
     */
    public static double criticalMixedLayerM(double a, double eminusPMmPerDay) {
        if (!(a > 0.0) || !(eminusPMmPerDay > 0.0)) return Double.POSITIVE_INFINITY;
        // mm/day -> m/s
        double ep = eminusPMmPerDay / 86400.0 / 1000.0;
        return 4.0 * BETA * S_REF * ep / (a * a);
    }

    /**
     * 由 (E-P) 与 h 算等效盐通量 H（psu/s）。h 未标定（NaN 或 <=0）时返回 NaN。
     * <p>推导见 {@link #H_MIXED_LAYER_M}。
     */
    public static double equivalentSalinityFlux(double eminusPMmPerDay, double hMeters) {
        if (!(hMeters > 0.0)) return Double.NaN;
        return S_REF * (eminusPMmPerDay / 86400.0 / 1000.0) / hMeters;
    }

    // ================================================================================
    // ★★★ 2026-10-15（待裁决/427）：世界级接线 —— 生产入口 OceanWiring.onWorld -> install
    // ================================================================================

    /** 已装的世界种子（{@link Integer#MIN_VALUE} = 未装）。 */
    private static volatile int installedSeed = Integer.MIN_VALUE;

    /**
     * ★ **世界级接线**（生产：{@code OceanWiring.onWorld(worldSeedInt)}）。
     * 只做两件事：记种子 + 作废上一次的世界诊断缓存。可重复调用。
     * <p>★ 它**不改变任何输出**（不碰气候场），所以这一步不需要单独验收。
     */
    public static synchronized void install(int worldSeedInt) {
        installedSeed = worldSeedInt;
        synchronized (WOUT) { worldCached = false; }
    }

    /** 是否已装（诊断 / 探针用）。 */
    public static boolean installed() { return installedSeed != Integer.MIN_VALUE; }

    /** 已装的种子（未装 = {@link Integer#MIN_VALUE}）。 */
    public static int installedSeed() { return installedSeed; }

    // ---- worldState() 的下标与状态码 ★ 只增不改（探针按下标读） ----
    /** 热驱动 A = alpha*DT。 */
    public static final int W_A = 0;
    /** 低纬带的净淡水通量 (E-P)（mm/day）。 */
    public static final int W_EP_MM_PER_DAY = 1;
    /** 盐驱动 B = beta*S_REF*(E-P)/h。 */
    public static final int W_B = 2;
    /** 判别式 (A/2)^2 - B。 */
    public static final int W_DISC = 3;
    /** 热主导稳定解（disc<0 时 NaN）。 */
    public static final int W_Q1 = 4;
    /** 盐主导稳定解。 */
    public static final int W_Q4 = 5;
    /** 状态码（见下面的 STATE_*）。 */
    public static final int W_STATE = 6;
    /** 本次使用的混合层深度 h（m）。 */
    public static final int W_H = 7;

    /** 开关关（**未计算**）。 */
    public static final int STATE_OFF = -2;
    /** 盐主导（disc<0）。 */
    public static final int STATE_SALINE = -1;
    /** 临界（disc==0）。 */
    public static final int STATE_CRITICAL = 0;
    /** 热主导（disc>0）。 */
    public static final int STATE_THERMAL = 1;

    private static final double[] WOUT = new double[8];
    private static volatile boolean worldCached = false;
    private static volatile int worldSeed = Integer.MIN_VALUE;
    private static volatile long worldStamp = 0L;

    /**
     * ★★★★★ **世界级诊断**：把模型自己的场喂进 Stommel 2-box，返回世界状态（复用数组）。
     *
     * <pre>
     *   [W_A]  热驱动 A = alpha*DT，DT = T(0-20N) - T(60-85N)     <- 生产口径，见下
     *   [W_EP] 低纬带 (E-P) 均值（mm/day）
     *   [W_B]  盐驱动 B = beta*S_REF*(E-P)/h                       <- h = {@link #H_MIXED_LAYER_M}
     *   [W_DISC], [W_Q1], [W_Q4], [W_STATE], [W_H]
     * </pre>
     *
     * <h4>口径（★ 写死，勿漂）</h4>
     * <ul>
     *   <li><b>温度</b>：{@link SimClimate#surfaceTempK} 在纬度带上的算术均值 ——
     *       3 个经度（-4000 / 0 / +4000 km）x 每 5 度，与 425 的离线实测**同一口径**；</li>
     *   <li><b>淡水</b>：{@link SimClimate#annualPrecipMmPerYear} 与
     *       {@link SimClimate#lakeEvapMmPerYear}（年均，mm/yr）相减后 /365.25 ——
     *       低纬带取 10~35N（副热带蒸发带，即 Stommel 的盐源）；</li>
     *   <li>两者都走 SimClimate 的生产访问器（不触发新计算，只做瓦片双线性）。</li>
     * </ul>
     *
     * <h4>★ 性能与缓存（铁律 1）</h4>
     * <p>这是**世界级**量，不是逐点量：一次采样 ~30 次瓦片访问（**首次会建瓦片**）
     * ⟹ 结果**缓存**，键 = (种子, {@link SimClimate#configStamp()})；
     * {@link #install} 会作废它。开关为 false 时**直接返回 OFF 哨兵，不采样** ⟹ 成本 0。
     *
     * <h4>★ 它不反哺气候场</h4>
     * <p>本方法是**纯诊断出口**：打开开关也不改变任何气候输出（逐位不变）。
     */
    public static double[] worldState(int worldSeedInt) {
        synchronized (WOUT) {
            if (!THERMOHALINE_ON) {
                java.util.Arrays.fill(WOUT, Double.NaN);
                WOUT[W_STATE] = STATE_OFF;
                WOUT[W_H] = H_MIXED_LAYER_M;
                worldCached = false;          // ★ OFF 不缓存：打开开关必须重算
                return WOUT;
            }
            long st = SimClimate.configStamp();
            if (worldCached && worldSeed == worldSeedInt && worldStamp == st) return WOUT;
            double tLow = bandMeanTempK(worldSeedInt, 0, 20);
            double tHigh = bandMeanTempK(worldSeedInt, 60, 85);
            double a = thermalDrive(tLow, tHigh);
            double ep = beltEmPmmPerDay(worldSeedInt, 10, 35);
            double b = halineDrive(equivalentSalinityFlux(ep, H_MIXED_LAYER_M));
            double[] r = solve(a, b);
            WOUT[W_A] = a;
            WOUT[W_EP_MM_PER_DAY] = ep;
            WOUT[W_B] = b;
            WOUT[W_DISC] = r[0];
            WOUT[W_Q1] = r[1];
            WOUT[W_Q4] = r[2];
            WOUT[W_STATE] = (int) r[3];
            WOUT[W_H] = H_MIXED_LAYER_M;
            worldSeed = worldSeedInt;
            worldStamp = st;
            worldCached = true;
            return WOUT;
        }
    }

    /** 世界诊断是否已缓存（诊断 / 探针用）。 */
    public static boolean worldCached() { return worldCached; }

    /** 纬度带平均地表温度（K）—— 3 个经度 x 每 5 度，与 425 同一口径。 */
    static double bandMeanTempK(int worldSeedInt, int lat0, int lat1) {
        double s = 0.0;
        int n = 0;
        int[] xs = {-4_000_000, 0, 4_000_000};
        for (int lat = lat0; lat <= lat1; lat += 5) {
            for (int x : xs) {
                try {
                    double t = SimClimate.surfaceTempK(x, WorldContract.zOfLat(lat), worldSeedInt);
                    if (Double.isFinite(t)) { s += t; n++; }
                } catch (Throwable ignored) { }
            }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    /** 纬度带平均净淡水通量 (E-P)（mm/day）—— 生产年均访问器 / 365.25。 */
    static double beltEmPmmPerDay(int worldSeedInt, int lat0, int lat1) {
        double s = 0.0;
        int n = 0;
        for (int lat = lat0; lat <= lat1; lat += 5) {
            try {
                int z = WorldContract.zOfLat(lat);
                double p = SimClimate.annualPrecipMmPerYear(0, z, worldSeedInt) / WorldContract.DAYS_PER_YEAR;
                double e = SimClimate.lakeEvapMmPerYear(0, z, worldSeedInt) / WorldContract.DAYS_PER_YEAR;
                if (Double.isFinite(p) && Double.isFinite(e)) { s += (e - p); n++; }
            } catch (Throwable ignored) { }
        }
        return n == 0 ? Double.NaN : s / n;
    }

    /** 供 SimClimate.configStamp() 折入（本类改变结果时用）。 */
    public static long configStamp() {
        long h = 1125899906842597L;
        h = h * 31 + (THERMOHALINE_ON ? 1 : 0);
        h = h * 31 + Double.doubleToLongBits(ALPHA);
        h = h * 31 + Double.doubleToLongBits(BETA);
        h = h * 31 + Double.doubleToLongBits(S_REF);
        h = h * 31 + Double.doubleToLongBits(H_MIXED_LAYER_M);
        return h;
    }
}