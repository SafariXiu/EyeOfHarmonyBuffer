package com.EyeOfHarmonyBuffer.sim.atmos;

/**
 * M3 的**外生纬向平均表**（10 度间隔 + 线性插值）。
 *
 * <p><b>为什么必须外生</b>：世界契约里 X 是无限的 ⇒ 纬向平均**没有定义**
 * ⇒ 纬向平均态（温度/气压/风/上升速度）只能由外部给定。这是 M3 调研报告的第一条结论。
 *
 * <p>表对 |lat| 对称（两个半球的信风/西风/极地东风是对称的）；
 * 季节不对称性全部来自**整体随太阳直射点迁移**（见 Atmosphere 的 delta / Delta_phi）。
 */
public final class ZonalTables {

    private ZonalTables() {}

    /** 纬向平均海平面气压（Pa）。锚点：赤道槽 1008 / 副热带高压 1018@30 / 副极地低压 1005@60 / 极地高压 1015@90。 */
    public static final double[] P_REF_PA = {
        100800, 101200, 101600, 101800, 101400, 100800, 100500, 100800, 101200, 101500
    };

    /**
     * **纬向平均纬向风（m/s，正 = 西风）—— 1 月表**（观测，10 度间隔线性插值）。
     *
     * <p>来源：ERA5 月平均再分析，850 hPa 纬向平均纬向风 u，1991-2016 共 6 年、纬向每 5 度、经向 2.5 度 144 点，
     * 每一条取数 URL 见 `build/eoh_scratch_clim/REPORT.md` §3；NCEP/NCAR R1 独立交叉校验逐点吻合。
     *
     * <p>⚠ <b>0 度两项必须相等</b>（这里都取 1 月/7 月实测的算术平均 -3.5）：换表前模型是
     * `uZm(|lat|)` 的偶函数，赤道两侧对称；换成「1/7 月 + 半球反相」之后，
     * 赤道的年谐波振幅 = (JUL[0]-JAN[0])/2 —— 若不为零，`hemi` 在 lat=0 处翻转就会造成
     * <b>风速硬跳变</b>（§82 的教训：A(φ) 表的 0 度也是靠这一条保住的）。
     *
     * <p>⚠ 本表**对 |lat| 对称**（南北半球用同一条北半球剖面）—— 与 A_LAND_K / A_SEA_K 同一取舍：
     * 本世界的陆地分布是南北对称的，而真实地球南半球 50S 的 +13.3/+11.0 西风带
     * 是「南大洋无陆地阻断」造成的。真实南北不对称**已记账、未实现**。
     */
    public static final double[] U_ZM_JAN = {-3.5, -6.6, -2.4, 4.2, 5.6, 3.1, 2.3, 1.9, 1.5, 0.0};

    /** 同上的 **7 月表**（单位/来源/0 度约定同 U_ZM_JAN）。 */
    public static final double[] U_ZM_JUL = {-3.5, 0.6, -2.4, -0.5, 2.6, 3.7, 0.4, 1.1, 2.1, 0.0};

    /**
     * 纬向平均上升速度（m/s，正 = 上升）。
     *
     * <p>⚠ 50~60 度的正值是**瞬变斜压涡动水汽通量辐合的替身**：
     * 本世界的 Rossby 变形半径（约 818 km）大于极赤距离（500 km）⇒ 没有斜压不稳定、没有风暴轴，
     * 中纬第三条雨带在纬向平均意义上只能这样给。**这是全设计里"只能自己定"的第一名。**
     */
    public static final double[] W_ZM = {
        5.0e-3, 3.5e-3, -0.5e-3, -1.2e-3, 0.2e-3, 1.5e-3, 1.0e-3, -0.3e-3, -0.6e-3, -0.8e-3
    };

    /**
     * **地表气温年较差的一半 A(phi)（K）** —— 陆地。
     *
     * <p>`A = (最暖月平均 - 最冷月平均)/2`，ERA5 月平均再分析（1991-2018），北半球纬向平均。
     * 实测剖面（10/20/25/30/40/45/50/60/65/70/80 度）：
     * 2.1 / 6.6 / 8.2 / 10.1 / 13.8 / 15.4 / 16.5 / 18.4 / **19.5（峰在 65 度）** / 18.7 / 14.9。
     *
     * <p>⚠ <b>形状既不是 sin(phi) 也不是 sin^2(phi)</b>：真实剖面在 65 度达峰后**向极回落**，
     * 而 sin^2 单调升到 90 度。旧实现 `A = 26*sin^2(phi)` 在 20 度**偏低 2.2 倍**、30 度偏低 1.6 倍，
     * 而 70/80 度偏高 1.2/1.7 倍（§80）。所以改成**观测表**，与 p_ref/U_zm/w_zm 同一套做法。
     *
     * <p>⚠ <b>0 度必须精确为 0</b>（不是外推，是硬约束）：`seasonalAnomaly` 的半球相位
     * `hemi` 在 latRad=0 处翻转（北半球 cos(θ-ψ)、南半球取反），而两半球在同一天是**相反季节**
     * ⇒ 赤道的年谐波必须为零。P295 实测：表里写成 0.5 时会产生 **0.38~0.87 K 的赤道硬跳变**
     * （B2 子代理发现），污染 p'、风场与任何对温度场求经向导数的量。<b>换形状函数时不许丢掉它的零点。</b>
     * <p>90 度按 70->80 的斜率外推。
     * 表对 |lat| 对称 —— **本世界的陆地分布是南北对称的**，所以用北半球剖面是自洽的选择；
     * 真实地球南半球振幅只有北半球的 0.65~0.72 倍（A_land 30S=7.3、A_sea 35S=2.9），
     * 那是地球陆地分布不对称造成的，**已记账、未实现**。
     */
    public static final double[] A_LAND_K = {0.0, 2.1, 6.6, 10.1, 13.8, 16.5, 18.4, 18.7, 14.9, 11.0};

    /**
     * **A(phi)（K）** —— 海洋（SST）。ERA5 同口径：
     * 10/20/25/30/40/45/50/60/70/80 度 = 0.9 / 1.8 / 2.6 / 3.6 / 5.3 / **5.4（峰在 45 度）** / 4.0 / 4.2 / 2.8 / 0.4。
     *
     * <p>⚠ 旧实现 `A = 10*sin^2(phi)` 错得比陆地那条更厉害：20~40 度**偏低 1.3~1.5 倍**，
     * 而 60 度偏高 1.8 倍、70 度偏高 **3.2 倍**（§80）。
     * 80 度以后 SST 在海冰下被钉在冰点 ⇒ 资料伪像，本表只取到 70 度的实测值并按趋势收尾
     * （本世界没有海冰）。50 度取 5.0（ERA5 在 45~60 度之间非单调，做了轻微平滑）。
     * <b>0 度同样必须精确为 0</b>（半球相位翻转 ⇒ 赤道年谐波为零），理由见 A_LAND_K。
     */
    public static final double[] A_SEA_K = {0.0, 0.9, 1.8, 3.6, 5.3, 5.0, 4.2, 2.8, 1.5, 0.8};

    /** 陆地季节振幅（K）。 */
    public static double aLand(double latDeg) { return interp(A_LAND_K, latDeg); }
    /** 海洋季节振幅（K）。 */
    public static double aSea(double latDeg) { return interp(A_SEA_K, latDeg); }

    /** 按**带符号纬度**（度）线性插值；表对 |lat| 对称，超界取端点。 */
    public static double interp(double[] tab, double latDeg) {
        double a = Math.abs(latDeg);
        if (a >= 90.0) return tab[tab.length - 1];
        double f = a / 10.0;
        int i = (int) f;
        if (i >= tab.length - 1) return tab[tab.length - 1];
        double t = f - i;
        return tab[i] * (1.0 - t) + tab[i + 1] * t;
    }

    /** 参考气压（Pa）：把 p_ref 折算成「高压 / 低压异常」的基准。 */
    public static final double P_REF_BASE = 101300.0;

    /**
     * p_ref 相对基准的偏离（Pa）：副热带为正（高压）、副极地为负（低压）。
     *
     * <p>**这是副高 cell 项的振幅来源**（见 Atmosphere.cellPressure）：
     * 真实世界里这个 ±5~8 hPa 的异常**集中在洋盆上空**（北太平洋高压、冰岛低压），
     * 不是均匀铺在整个纬圈上。我们的 p_ref 是纬向平均 ⇒ 缺了那份「纬向不对称」，
     * 而那份不对称正是东边界沿岸风的**种子**（设计冻结 §34.4）。
     */
    public static double carrier(double latDeg) { return pRef(latDeg) - P_REF_BASE; }

    public static double pRef(double latDeg) { return interp(P_REF_PA, latDeg); }
    /** 纬向平均纬向风的**年平均**（仅供诊断对照；生产路径请用带 theta 的重载）。 */
    public static double uZm(double latDeg) { return 0.5 * (interp(U_ZM_JAN, latDeg) + interp(U_ZM_JUL, latDeg)); }

    /**
     * **纬向平均纬向风（m/s）**：1 月/7 月两组观测表 + 按 <code>cos(Theta)</code> 的季节插值。
     *
     * <pre>
     *   u_zm(phi, Theta) = U_ANN(|phi|) + U_DIF(|phi|) * cos(Theta) * hemi
     *   U_ANN = (JAN+JUL)/2,  U_DIF = (JUL-JAN)/2,  hemi = +1 北 / -1 南
     * </pre>
     *
     * <p><b>为什么不再用「整体平移 Δφ(θ)=6°·cosθ」</b>：真实季节变化**不是纯平移** ——
     * 10 度 u850 从 -6.6（1 月）变到 +0.6（7 月），跨 7.2 m/s，而 20 度**几乎不变**（Δ=0.0）；
     * 纯平移在 10 度最多给出 uZm(10-6)-uZm(10+6) = 2.4 m/s，**差 3 倍**，还会在 10 度留下
     * 一个全年为负（东风）的假象。改成「两表 + 季节插值」后每一度各按自己的振幅与相位变化。
     *
     * <p><b>相位约定</b>：<code>Theta = 0</code> 是北半球夏至（= 观测的 7 月，差 10 天）；
     * <code>Theta = pi</code> 是北半球冬至（= 观测的 1 月）。所以直接用 <code>cos(Theta)</code>，
     * **不引入任何新的相位/滞后参数**，与 <code>Atmosphere.subsolarLat = eps*cos(Theta)</code> 同相。
     *
     * <p><b>半球反相</b>：南半球的 7 月是北半球的 1 月，所以 <code>hemi = -1</code> 让整条季节谐波反号
     * （与 <code>seasonalAnomaly</code> 的 <code>hemi = 0/pi</code> 是同一个约定）。因为
     * <code>U_DIF(0) = 0</code>，lat=0 处**没有跳变**。
     */
    public static double uZm(double latDeg, double theta) {
        double uj = interp(U_ZM_JAN, latDeg);
        double ul = interp(U_ZM_JUL, latDeg);
        double hemi = latDeg >= 0.0 ? 1.0 : -1.0;
        return 0.5 * (uj + ul) + 0.5 * (ul - uj) * Math.cos(theta) * hemi;
    }
    public static double wZm(double latDeg) { return interp(W_ZM, latDeg); }

    // ================= 「洋盆尺度纬向风骨架」：洋面口径的纬向风两表 =================

    /**
     * **洋面 10 m 纬向平均纬向风（m/s，正 = 西风）—— 1 月表**，5 度间隔（0..90）。
     *
     * <p>来源与 A_LAND_K 同一批 ERA5 取数（只取海洋格点，海陆掩膜由 sst 缺测标记构造）的 10 m 纬向风。
     *
     * <p><b>为什么需要它</b>：U_ZM_JAN 是**全球（含陆地）850 hPa** 的纬向平均，
     * 而海洋的表面风应力是由**洋面上的 10 m 风**驱动的。两者差别很大：
     * 20 度 1 月 -2.4（全球 850）vs -4.56（洋面 10 m）；60 度 -0.10（洋面）vs +2.3（全球 850）。
     * 用全球 850 的纬向平均去驱动海洋，等于把「洋盆尺度的纬向风骨架」换成了「全球平均」。
     *
     * <p>0 度两项取算术平均 -2.47、90 度取 0：理由同 U_ZM_JAN ——
     * 半球相位在 lat=0 翻转，两表不等会在赤道造成风速硬跳变（§82 的教训）。
     */
    public static final double[] U_SEA_JAN = {
        -2.47, -3.64, -5.88, -5.86, -4.56, -1.50, 2.27, 4.13, 4.37, 3.44, 1.68, 0.24, -0.10, 0.06, 0.78, 0.40, 0.47, 0.69, 0.00
    };
    /** 同上的 **7 月表**（单位/来源/0 度与 90 度约定同 U_SEA_JAN）。 */
    public static final double[] U_SEA_JUL = {
        -2.47, 0.11, -0.35, -2.58, -3.51, -3.98, -2.15, 0.03, 1.08, 2.04, 2.73, 1.48, 0.47, -0.33, -0.33, 0.23, 0.66, 0.84, 0.00
    };

    /**
     * 开关：**是否用「洋面口径」的 u_zm**。
     * false = 原行为（全球 850 hPa 单表），逐位不变；true（默认）= 按大陆度 κ 在两套表之间**连续**插值。
     */
    public static boolean SEA_ONLY_UZM = true;

    /** 5 度间隔表的线性插值（|lat|，超界取端点）。 */
    public static double interp5(double[] tab, double latDeg) {
        double a = Math.abs(latDeg);
        if (a >= 90.0) return tab[tab.length - 1];
        double f = a / 5.0;
        int i = (int) f;
        if (i >= tab.length - 1) return tab[tab.length - 1];
        double t = f - i;
        return tab[i] * (1.0 - t) + tab[i + 1] * t;
    }

    /** 洋面口径的季节插值（与 uZm 同一相位约定）。 */
    public static double uZmSea(double latDeg, double theta) {
        double uj = interp5(U_SEA_JAN, latDeg);
        double ul = interp5(U_SEA_JUL, latDeg);
        double hemi = latDeg >= 0.0 ? 1.0 : -1.0;
        return 0.5 * (uj + ul) + 0.5 * (ul - uj) * Math.cos(theta) * hemi;
    }

    /**
     * **洋盆尺度纬向风骨架**：按大陆度 κ 在「洋面 10 m 表」与「全球 850 hPa 表」之间连续插值。
     * κ=0（深海）取洋面表、κ=1（内陆）取原表；COAST_BLEND 尺度上连续，无阈值。
     */
    public static double uZmBlend(double latDeg, double theta, double kappa) {
        if (!SEA_ONLY_UZM) return uZm(latDeg, theta);
        double c = kappa < 0 ? 0 : (kappa > 1 ? 1 : kappa);
        return uZmSea(latDeg, theta) * (1.0 - c) + uZm(latDeg, theta) * c;
    }


    /**
     * **d p_ref / d lat（Pa/弧度，带符号）** —— 分段线性表的斜率；|lat| >= 90 时返回 0（端点外推为常数）。
     *
     * <p>换算到 z：帐篷函数的**升支**上 d lat/dz = +1/R_EFF（两个半球都一样 —— z 增大时带符号纬度
     * 单调增大），而 p_ref 是 |lat| 的函数 ⇒ <code>d p_ref/dz = sign(lat)*slope(|lat|)/R_EFF</code>。
     * 本方法把 sign 直接乘进去，所以调用方写 <code>pRefSlopePerRad(latDeg)/R_EFF</code> 即可，
     * 不需要自己判半球。（⚠ 我第一版漏了这个 sign，南半球符号全反，A3 从 24/32 掉到 19/32。）
     *
     * <p>⚠⚠ <b>2026-09-13 修正（审计 D1）</b>：本方法原来返回**10 度分段的段内斜率**，
     * 于是它在每个节点上**跳变**（±10/20/…/80 度，最大 4583.7 Pa/rad），
     * 并且在赤道返回 <b>±2292</b> 而 p_ref 在赤道是极小值、真导数应为 <b>0</b>。
     * 该量经 {@link Atmosphere#wind} 的 pzRef 直接驱动 <b>v</b> ⇒ <b>v 是分段常数</b>：
     * P444 实测 1 m 跨度上阶跃 0.4~3.0 m/s、<b>赤道 20.35 m/s</b>；次生地让
     * {@code PrecipField.mmPerDay} 的 divU 在赤道被抬高约 10 倍，wLoc 在全世界
     * 60~89% 的采样点上打满 W_LOC_MAX（P446）。
     * 现改为<b>带符号纬度的中心差分</b>：段内部与旧实现数值相同，只在节点邻域变成连续，
     * 赤道处给出 0。极点的角点（|lat| >= 90 返回 0）与旧实现一致，未改动。
     */
    public static double pRefSlopePerRad(double latDeg) {
        if (Math.abs(latDeg) >= 90.0) return 0.0;
        final double h = 5.0;                       // 半窗 5 度 ⇒ 中心差分跨 10 度
        // ⚠ 必须传**带符号**纬度：pRef 是 |lat| 的偶函数，赤道两侧各取 5 度
        //   会得到 pRef(+5) - pRef(-5) = 0 ⇒ **赤道处自然给出 0**，不需要任何 if。
        //   （若先取 |lat| 再加减，下界被夹到 0 就退化成单侧差分，赤道仍会给 +2292 —— 这个坑写在这里。）
        double lp = Math.min(90.0, latDeg + h);
        double lm = Math.max(-90.0, latDeg - h);
        return (pRef(lp) - pRef(lm)) / Math.toRadians(lp - lm);
    }
}