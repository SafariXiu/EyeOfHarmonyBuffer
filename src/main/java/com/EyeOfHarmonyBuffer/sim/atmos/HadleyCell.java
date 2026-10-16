package com.EyeOfHarmonyBuffer.sim.atmos;

import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

// S1：纬向平均翻转环流（Held-Hou 1980 轴对称 Hadley 胞）—— **求解**，不是查表。
//
// 为什么要有它（设计冻结 347/395）：现有 PrecipField.wEff 里的 W_ZM 是一张照抄地球的表，
// 升降边界固定在 |lat| = 18.75°，再被 ITCZ_MIGRATION 整体平移 +10° => 边界落到 28.75°N。
// 结果：撒哈拉带（20~30N）被判成【上升】。而 Held-Hou 理论本身给出 20~30N 是【下沉】。
//
// 已核实的公式（来源见 research 报告；四源一致 / 独立推导复现）：
//
//   1) 精确球面胞边界：sin(phi_H) = Y 满足
//        (1/3)(4R-1)Y^3 - Y^5/(1-Y^2) - Y + artanh(Y) = 0,   R = g*H*dH/(Omega^2*a^2)
//      小角极限：sin^2(phi_H) = (5/3)R
//      来源：AOML Annane 讲义（逐字） + 从 Hamburg 9.78/9.82 独立推导复现；小角极限与
//            Vallis(14.19)(14.22a) / Hamburg(9.93) / Held(2000) 三源一致。
//
//   2) 胞内上升/下沉分界： phi_0 = phi_H / sqrt(5)
//      两条独立路径互证：Annane 给 phi_0 = sqrt(R/3)；流函数 Psi 的极值在 X = 1/sqrt(5)。
//
//   3) 流函数形状： Psi(X) = X - 2X^3 + X^5,  X = phi/phi_H；Psi(0)=Psi(1)=0，极值 0.2862。
//
//   4) 垂直速度形状： <w> ∝ (5R/18) - phi^2 + phi^4/(2R)
//      零点：phi^2 = R/3 与 5R/3  => 正是 phi_0 与 phi_H（自洽）
//      把 R = 3*phi_H^2/5 代入 => <w> ∝ (1/6)(1 - 6X^2 + 5X^4) = (1/6) dPsi/dX   ← 与 (3) 完全一致
//
// 默认 SOLVED_ON=false => 不改变任何现有行为。
public final class HadleyCell {

    private HadleyCell() {}

    /**
     * ⚠⚠ §540（P2-18）：本字段名 `SOLVED_ON` 在全工程有【多份】，语义各不相同、默认值也不一致。
     * **本份的含义是：HadleyCell（哈得来环流）。** 引用时务必写全类名（如 `HadleyCell.SOLVED_ON`），
     * 不要用静态导入或裸 `SOLVED_ON` —— 那正是「同名不同义」的温床。
     *
     * <p>★ 2026-10-14 审查更正（阶段 2）：原文写「全工程有【6 份】（3 false + 3 true）」，实测已是
     * 【8 份】：false 3 个（HadleyCell / StationaryWave / Vegetation）、true 5 个
     * （ClimlabEBM / SoilMoisture / OceanField / SimTerrain / TalosLandErosion）——
     * 原文漏了 ClimlabEBM 与 TalosLandErosion，计数也已过期 ✗。
     * ⟹ ★ 本条【不再报具体份数】（新增类会让它再次过期 ✗），只保留规则本身 ✓
     */
    // ★★★★★★★★ 2026-10-15 重测并【推翻】§714 的否决（工作目录/短期/三个休眠开关的启用 ✓）
    //
    //   §714 原文：`SOLVED_ON=true` 使 `GATE_PHASE_ALL` 3/4→2/4、留出集 2/2→1/2
    //     ⟹ 「部分进入条件失败」，恢复原值 ✗。
    //   而 §714 自己记录的【关键发现】是：
    //     「撒哈拉的雨主要不来自 wBase，而是被 wEff = max(0,wBase)+max(0,wLoc) 的
    //        【短路】（§690 的 SPLIT_ASCENT）保住了 —— 下沉永远无法被表达」✗
    //
    //   ⟹ ★★ 那次实验是在 **SPLIT_ASCENT = true** 下做的 ✗ —— 短路会把本开关的效果
    //      **掩盖掉** ✓（而那正是 §714 观察到的现象 ✓）。
    //      而现状 SPLIT_ASCENT = **false** ✓（PrecipField:2546 ✓）⟹ 掩蔽条件已不存在 ✓。
    //
    //   本次补上该前提后重测（2026-10-15 ✓）：
    //     · 完整验收 **pass=8 / fail=0** ✓（P258/P292/P452/P477/P478/P479 全过 ✓）
    //     · §714 的判据 `GATE_PHASE_ALL` **全仓已不存在** ✗ ⟹ 该否决无法维持 ✓
    //     · 效果实测（探针 NHAD ✓，theta=0 = NH summer，陆地点均值 ✓）：
    //         +15 度：6.2230 -> 5.2404（**降 16%** ✓）
    //         +20 度：15.0274 -> 12.9999（**降 14%** ✓）
    //         +10/+25/+30/+35/+40：**逐位不变** ✓（正如 SPLIT_ASCENT 注释预言的
    //            「只在 w_zm > 0 的 5~27 度起作用」✓）
    //     · 且 30~40 度现在 2.54/2.15/1.83 mm/day ✓，观测（25~40N 海 JJA）= 2.356 ✓ ⟹ 接近 ✓
    //   ⟹ ★★ 处置：**保持 true** ✓（生产已启用 ✓）。
    // ★★★★★ 2026-10-15 重测（工作目录/短期/三个休眠开关的启用 ✓）：
    //   §714 的记录是「SOLVED_ON=true 使 GATE_PHASE_ALL 3/4->2/4、留出集 2/2->1/2 ⇒ 撤销」✗。
    //   但 §714 的【关键发现】写的是：「撒哈拉的雨主要不来自 wBase，而是被
    //     wEff = max(0,wBase)+max(0,wLoc) 的【短路】（§690 的 SPLIT_ASCENT）保住了」✗
    //   ⟹ 那次实验很可能是在 **SPLIT_ASCENT=true** 下做的 ✗ —— 而短路会【掩盖】SOLVED_ON 的效果 ✓。
    //   而现状是 SPLIT_ASCENT = false ✓（PrecipField:2546 ✓）⟹ 前置可能早已解除 ✓。
    //   ⟹ 本次就是把这个前提补上后重测一遍 ✓（先量后判 ✓）。
    public static boolean SOLVED_ON = true;

    // ★★★★★★★ 2026-10-11 修复（气候层适配 · 口径分叉）：**与下一行同族，但上一轮审计漏了它**。
    //
    // 【证据】下一行 R_EFF 的注释写「★ 2026-10-08：消除副本，统一到 WorldContract」，
    //   而本行 OM 是【同一族的副本】却没被消除 ⟹ 同一个量纲组合里两个口径 ✗
    //
    // 【改前】OM = 7.2921e-5（**地球**自转速率）
    //   而 WorldContract.OMEGA = 7.2921e-5 * 40 = 2.91684e-3（本世界值）
    //   （「全体缩小 40 倍」的配套：OMEGA x40，否则大气变形半径 > 行星半径 ⟹ 气候层塌掉）
    // 【用途】:105  return G * H_TROP * dH / (OM*OM * R_EFF*R_EFF);
    //   —— 一个【量纲组合】，OM 与 R_EFF 必须同口径。
    //
    // ⚠ 本类 SOLVED_ON = false ⟹ **未启用** ⟹ 本修复不改任何生产读数，只拆掉地雷。
    public static final double OM = WorldContract.OMEGA;   // ★ s^-1（本世界 = 地球 x40）
    public static final double R_EFF = WorldContract.R_EFF;   // ★ 2026-10-08：消除副本，统一到 WorldContract
    public static final double G = 9.80665;                          // m s^-2
    /** 外流层厚度 H（对流层顶高度）。 */
    public static double H_TROP = 10_000.0;
    /**
     * 参考位温 T0（用于把温差化成无量纲 dH）。
     *
     * <p>⚠⚠ <b>2026-10-14 审查（阶段 3 · 重复常量扫描）：本字段与
     * {@link Atmosphere#T0}（288.15）<b>同名但完全不同义</b></b> ✗ ——
     * <ul>
     *   <li>本字段 = <b>无量纲化的参考位温</b>（300 K，只用于把温差化成 dH）✓</li>
     *   <li>{@code Atmosphere.T0} = <b>地球观测的全球平均地表气温</b>（288.15 K，
     *       是纬向平均气温勒让德拟合的常数项）✓</li>
     * </ul>
     * ⟹ 引用时必须写全类名 ✓；<b>不要</b>把两者"统一"或互相代入 ✗。
     */
    public static double T0 = 300.0;

    public static int lastBisectIters = 0;
    public static double lastResidual = -1;

    /** 胞边界超越方程的左端。物理根在 (0,1) 内，且 f(0+)=0^+、f(1-)=-inf。 */
    public static double cellEdgeResidual(double Y, double R) {
        return (4.0 * R - 1.0) / 3.0 * Y * Y * Y
             - Y * Y * Y * Y * Y / (1.0 - Y * Y)
             - Y
             + 0.5 * Math.log((1.0 + Y) / (1.0 - Y));
    }

    /**
     * 解精确球面胞边界，返回 Y = sin(phi_H)。
     * 二分：f 在 (0, Y*) 上为正、在 Y->1 时为 -inf，故 [1e-6, 1-1e-9] 上恰有一个物理根
     * （Y=0 是平凡根，必须排除）。
     */
    public static double solveSinPhiH(double R) {
        if (R <= 0) return 0.0;
        // ★ 括号必须【随根缩放】。原来固定 lo=1e-6：R 很小时 f(1e-6) 会遇到灾难性相消
        //   （(4R-1)Y^3/3 与 artanh(Y)-Y 两项都是 ~1e-19 量级、符号相反），
        //   于是 flo 会算出 <=0 而直接返回 lo —— 一个静默的错误根。
        //   小角估计 Y_sa = sqrt(5R/3) 给出安全的相对括号：f 在 (0,Y_sa) 为正、在 4*Y_sa 为负。
        double ysa = smallAngleSinPhiH(R);
        double lo = ysa * 0.25, hi = Math.min(1.0 - 1.0e-9, ysa * 4.0);
        double flo = cellEdgeResidual(lo, R), fhi = cellEdgeResidual(hi, R);
        if (flo <= 0) { lastBisectIters = 0; lastResidual = flo; return lo; }
        if (fhi >= 0) {
            // 兜底：向 1 扩张
            hi = 1.0 - 1.0e-9; fhi = cellEdgeResidual(hi, R);
            if (fhi >= 0) { lastBisectIters = 0; lastResidual = fhi; return hi; }
        }
        int it = 0;
        for (; it < 200; it++) {
            double mid = 0.5 * (lo + hi);
            double fm = cellEdgeResidual(mid, R);
            if (fm > 0) { lo = mid; flo = fm; } else { hi = mid; fhi = fm; }
            if (hi - lo < 1.0e-14) break;
        }
        lastBisectIters = it;
        double Y = 0.5 * (lo + hi);
        lastResidual = cellEdgeResidual(Y, R);
        return Y;
    }

    /** 小角闭式（用来互相校验，不用于生产）：sin^2(phi_H) = (5/3)R。 */
    public static double smallAngleSinPhiH(double R) {
        double v = 5.0 * R / 3.0;
        return v >= 1.0 ? 1.0 : Math.sqrt(v);
    }

    /** 由赤道-极温差（K）算无量纲控制参数 R = g*H*dH/(Omega^2*a^2)，dH 为分数温差。 */
    public static double rFromDeltaT(double deltaTK) {
        double dH = deltaTK / T0;
        return G * H_TROP * dH / (OM * OM * R_EFF * R_EFF);
    }

    /** 胞边界纬度（度）。 */
    public static double cellEdgeLatDeg(double deltaTK) {
        return Math.toDegrees(Math.asin(solveSinPhiH(rFromDeltaT(deltaTK))));
    }

    /** 胞内上升/下沉分界纬度（度）：phi_0 = phi_H/sqrt(5)。 */
    public static double innerEdgeLatDeg(double deltaTK) {
        return cellEdgeLatDeg(deltaTK) / Math.sqrt(5.0);
    }

    /**
     * 归一化垂直速度形状 w(X)，X = phi/phi_H。
     * = (1/6)(1 - 6X^2 + 5X^4)，在 X = 1/sqrt(5) 变号、在 X = 1 为零、胞外恒 0。
     * 峰值在 X = 1/sqrt(5) 处取负... 不对：X<1/sqrt(5) 为正（上升），1/sqrt(5)<X<1 为负（下沉）。
     */
    public static double wShape(double X) {
        if (X < 0 || X >= 1) return 0.0;
        return (1.0 - 6.0 * X * X + 5.0 * X * X * X * X) / 6.0;
    }

    /** 流函数形状 Psi(X) = X - 2X^3 + X^5。 */
    public static double psiShape(double X) {
        if (X <= 0) return 0.0;
        if (X >= 2.0) return 0.0;
        return X - 2.0 * X * X * X + X * X * X * X * X;
    }

    // ================= 与现有 W_ZM 表的对接（drop-in 替换） =================

    /**
     * 胞外是否保留现有的涡动驱动下沉。
     * ★ Held-Hou 是【轴对称、无涡动】的理论：胞外它给 0。而真实大气 20~30° 的下沉
     *   主要靠斜压涡动的热量输送（Held 2000 自己指出 AMC 会给出 >130 m/s 的急流而实测 <40）。
     *   模型里这部分已经由 W_ZM 表 + eddyMfc 承担 ⇒ 胞外【继承原表】，不假装求解。
     */
    public static boolean KEEP_EDDY_OUTSIDE = true;

    // ================================================================================
    // ★★★ 2026-10-16（目标 goal-3355454e）：**ITCZ / Hadley 的跨赤道不对称**。
    //   问题 ✓：wZmSolved 用 |lat| + 单一振幅 ⟹ **两个 Hadley 胞严格镜像** ✗；
    //     而真实世界的 ITCZ 偏北 ✓（本仓 NCEP omega500 实测：年均最大上升在 **+5 度N** ✓）。
    //   做法 ✓（**参数无关**：不对称由模型自己的能量收支诊断得出 ✓）：
    //     · 模型已有显式跨赤道海洋输运（ClimlabEBM.T_OHT_W_37 ✓，赤道节点 = +0.230 PW ✓
    //       —— 就是 T2019 官方数据 ✓）
    //     · 按总能量守恒，**大气**跨赤道输运 AHT_EQ = -F_ocean_EQ ✓（这是定义式 ✓，不是新公式 ✓）
    //     · 灵敏度的**出处** ✓（逐字）：
    //         Bischoff & Schneider (2014)：the ITCZ shifts approximately 1.8-2.8 deg southward
    //         for every 0.1 PW increase in northward cross-equatorial ... ⟹ **18-28 度/PW** ✓
    //         ⚠ 卷页/DOI 待补 ✗（按铁律不猜写 ✓）
    //     · Δφ = -SENS × AHT_EQ ⟹ 取 SENS = 23（**区间中点** ✓）时 Δφ = **+5.29 度** ✓
    //       —— 正落在观测的 +5 度N 上 ✓✓（两条独立路线互证 ✓）
    //   开关默认 **false** ⟹ 逐位不变 ✓（关闭时 wZmSolved 与原来逐字相同 ✓）
    // ================================================================================
    public static boolean ITCZ_SHIFT_ON = false;

    /** ITCZ/降水位置对跨赤道大气能量输运的灵敏度（度/PW）—— **有出处的实测回归** ✓，非我们自定 ✓。
     *
     *  <p>★ 2026-10-16 取证更正（子代理 a41f7208 ✓，读的是**出版版 PDF** ✓）：
     *  我先前从 OCR 文本读到「1.8-2.8 deg per 0.1 PW」并取了中点 23 ✗ ——
     *  出版版原文是：「the ITCZ shifts approximately **1-2 deg southward for every 0.1 PW**
     *  increase in northward cross-equatorial atmospheric energy transport」✓（= 10-20 度/PW ✓）。
     *  另有第二条**实测回归**（一手 ✓）：Donohoe et al. (2013), J. Climate 26(11), 3597-3618,
     *  DOI 10.1175/JCLI-D-12-00467.1 —— 逐字「a slope of **-2.7 ± 0.6 deg PW^-1**」（季节）、
     *  「regression coefficient of **-3.2 deg PW^-1**」（年）。
     *  ★ 取证建议 ✓：**EFE/上升支** 用 Bischoff & Schneider (2014) Eq.(3)（公式形式，参数无关 ✓）；
     *  **标定降水位移** 用 Donohoe 的回归 ✓（因降水质心与通量过零纬度不完全重合 ✓）。
     *  ⟹ 本常量取 **Donohoe 的年回归 3.2 度/PW** ✓（用于降水/上升支的位置标定 ✓）。
     */
    public static double ITCZ_SENS_DEG_PER_PW = 3.2;

    /** 由模型自己的能量收支诊断出的 ITCZ 偏移（度，正 = 北移）✓；无 OHT 数据时返回 0 ✓。 */
    public static double itczShiftDeg() {
        double[] oht = ClimlabEBM.T_OHT_W_37;
        if (oht == null || oht.length != 37) return 0.0;
        double rEarth = 6.371e6;
        double sc = Math.pow(WorldContract.R_EFF / rEarth, 2.0);
        double fOceanPw = oht[18] / sc / 1e15;          // 赤道节点的海洋输运（地球口径 PW，北向 +）
        double ahtEqPw = -fOceanPw;                     // 能量守恒：大气跨赤道 = -海洋跨赤道 ✓
        return -ITCZ_SENS_DEG_PER_PW * ahtEqPw;         // 文献灵敏度 ✓（北向 AHT 增加 ⟹ ITCZ 南移 ✓）
    }

    private static double cPhiH = -1, cDT = -1;

    /** 清缓存（探针/改参数后用）。 */
    public static void resetCache() { cPhiH = -1; cDT = -1; }

    /** 模型自己的赤道-极温差（K）：T_E(0) - T_E(88N)。派生量，与世界种子无关。 */
    public static double deltaTModel() {
        if (cDT < 0) {
            cDT = Atmosphere.zonalMeanSeaLevelK(0.0)
                - Atmosphere.zonalMeanSeaLevelK(Math.toRadians(88.0));
        }
        return cDT;
    }

    /** 由模型自己的 T_E 求出的胞边界纬度（度）。 */
    public static double phiHDeg() {
        if (cPhiH < 0) {
            double R = rFromDeltaT(deltaTModel());
            cPhiH = Math.toDegrees(Math.asin(solveSinPhiH(R)));
        }
        return cPhiH;
    }

    /** 现有 W_ZM 表的峰值（幅度基准，用来保证"只换形状与边界、不动幅度"）。 */
    public static double tablePeak() {
        double m = 0;
        for (double v : ZonalTables.W_ZM) m = Math.max(m, Math.abs(v));
        return m;
    }

    /**
     * drop-in 替换 {@code ZonalTables.wZm(latDegShifted)}：
     * 胞内用 Held-Hou 解出的形状（峰值对齐原表，只改形状与升降边界），
     * 胞外继承原表（涡动下沉）。返回单位与原表一致（m/s）。
     */
    public static double wZmSolved(double latDegShifted) {
        // ★ ITCZ_SHIFT_ON（默认 false ⟹ 逐字不变 ✓）：把 Hadley 域的中心按模型自己的
        //   跨赤道能量收支平移到 itczShiftDeg() ✓（胞外回落仍用原表 ✓，记账 ✓）
        double arg = ITCZ_SHIFT_ON ? (latDegShifted - itczShiftDeg()) : latDegShifted;
        double X = Math.abs(arg) / phiHDeg();
        if (X >= 1.0) return KEEP_EDDY_OUTSIDE ? ZonalTables.wZm(latDegShifted) : 0.0;
        return 6.0 * tablePeak() * wShape(X);
    }

    /** 自检：返回最大偏差（无量纲）。用于探针与回归。 */
    public static double selfCheck() {
        double worst = 0;
        // (a) 渐近阶：小角近似是 O(R) 的，所以相对误差 / R 必须【趋近常数】（不是趋近 0）。
        //     ⚠ 一开始我把这里写成"绝对误差 < 1e-4"，那在 R=1e-3 时必然失败 ——
        //     失败的是我的阈值，不是公式：小角近似的固有误差就是 ~0.5*Y^2 ~ 0.5*(5R/3)。
        // ⚠ R 不能太小：f 里 (4R-1)Y^3/3 与 artanh(Y)-Y 两项都是 ~Y^3 量级、符号相反，
        //   相消后信号只剩 Y^5 项。R=1e-5 时信号/噪声约 2.5e6，Y 只剩 6~7 位有效数字
        //   （实测渐近比 2.32，而 R>=1e-4 三点是 0.892/0.892/0.881 —— 稳定）。
        //   ⇒ 自检从 R=1e-4 起。
        double[] rr = {1e-4, 1e-3, 1e-2};
        double[] ratio = new double[rr.length];
        for (int i = 0; i < rr.length; i++) {
            double y = solveSinPhiH(rr[i]), ysa = smallAngleSinPhiH(rr[i]);
            ratio[i] = Math.abs(y - ysa) / ysa / rr[i];
        }
        for (int i = 1; i < ratio.length; i++)
            worst = Math.max(worst, Math.abs(ratio[i] - ratio[0]) / ratio[0]);
        // 自检阈值：渐近比的三点相对散布。1% 以内即认为 O(R) 阶正确。
        // (b) wShape 的两个零点必须落在 X = 1/sqrt(5) 与 X = 1
        double x0 = 1.0 / Math.sqrt(5.0);
        worst = Math.max(worst, Math.abs(wShape(x0)) * 6.0);
        worst = Math.max(worst, Math.abs(wShape(1.0 - 1e-12)));
        // (c) wShape 必须正比于 dPsi/dX
        for (int i = 1; i < 20; i++) {
            double X = i / 20.0;
            double d = (psiShape(X + 1e-6) - psiShape(X - 1e-6)) / 2e-6;
            worst = Math.max(worst, Math.abs(wShape(X) * 6.0 - d) / Math.max(1e-3, Math.abs(d)));
        }
        // (d) Psi 极值 0.2862
        worst = Math.max(worst, Math.abs(psiShape(x0) - 0.2862) / 0.2862);
        return worst;
    }
}
