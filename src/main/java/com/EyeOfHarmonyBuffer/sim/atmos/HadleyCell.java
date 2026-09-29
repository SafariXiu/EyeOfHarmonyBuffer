package com.EyeOfHarmonyBuffer.sim.atmos;

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
// 默认 ENABLED=false => 不改变任何现有行为。
public final class HadleyCell {

    private HadleyCell() {}

    /**
     * ⚠⚠ §540（P2-18）：本字段名 `ENABLED` 在全工程有【6 份】，语义各不相同、默认值也不一致
     * （3 个 false：HadleyCell/StationaryWave/Vegetation；3 个 true：OceanField/SimTerrain/SoilMoisture）。
     * **本份的含义是：HadleyCell（哈得来环流）。** 引用时务必写全类名（如 `HadleyCell.ENABLED`），
     * 不要用静态导入或裸 `ENABLED` —— 那正是「同名不同义」的温床。
     */
    // ★ §714：§713 的 A/B 实验已做完并撤销。`ENABLED=true` 使 `GATE_PHASE_ALL` 3/4→2/4、
    //   留出集 2/2→1/2，`GATE_VERDICT` 仍 FAIL ⇒ 部分进入条件失败，恢复原值。
    //   关键发现：撒哈拉 DJF 在开关前后逐位相同（都是 0.950），JJA 只降 12%
    //   ⇒ 撒哈拉的雨主要不来自 `wBase`，而是被 `wEff = max(0,wBase)+max(0,wLoc)` 的
    //   【短路】（§690的 SPLIT_ASCENT）保住了 —— 下沉永远无法被表达。下一个候选是 `SPLIT_ASCENT`。
    public static boolean ENABLED = false;

    public static final double OM = 7.2921e-5;                       // s^-1
    public static final double R_EFF = 10_000_000.0 / (Math.PI / 2.0); // m（2*pi*R = 40,000 km）
    public static final double G = 9.80665;                          // m s^-2
    /** 外流层厚度 H（对流层顶高度）。 */
    public static double H_TROP = 10_000.0;
    /** 参考位温 T0（用于把温差化成无量纲 dH）。 */
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
        double X = Math.abs(latDegShifted) / phiHDeg();
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
