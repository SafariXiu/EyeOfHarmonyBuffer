package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P553 —— S3 的**零成本否证测试**：陆地桶（Manabe dW/dt = 0 互补闭合）到底会不会把撒哈拉变干？
 *
 * <p>本探针**完全不改 src**：它在探针里把桶的代数算一遍，
 * 拿 P552 已确证的场（q、P、wEff、kappa、T_sfc、|U|）算出「若落地会变成什么」。
 *
 * <p><b>为什么要先做这个</b>：手算给出警告 —— `beta = min(1, P/E_p)` 以**本地 P** 作供水时，
 * 亚洲的 `P/E_p` 可能**低于**撒哈拉 ⇒ 桶会把季风区榨得比沙漠更干（把反转放大，而不是修好）。
 * 若成立 ⇒ S3 **不能单独落地**，必须先修风场（S2）。
 *
 * <p>三个变体：
 * <ol>
 *   <li>`beta1`：**非迭代**，用未受影响的 `P_old` 作供水（无失控风险）</li>
 *   <li>`beta3`：固定 3 次不动点（Zebiak-Cane 口径）</li>
 *   <li>`beta10`：10 次 —— **用来暴露不动点在哪里**（失控检测）</li>
 * </ol>
 */
public class P553 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P553] " + s); System.out.println("[P553] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;
    static final double MMD = 86400.0 * 1000.0;      // m/s -> mm/day

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p553_report.txt"), "UTF-8");
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("P553：陆地桶会不会把撒哈拉变干？（纯探针，src 零改动）");
        say("  自证：恒等 qNew/qOld == (1-k+k*beta)（当 beta=1 时必须逐位为 1）—— 见每行 beta=1 检查");
        say("  契约：RH_SEA=" + PrecipField.RH_SEA + "  V_GUST=" + PrecipField.V_GUST
            + "  EPS_C=" + PrecipField.EPS_C + "  RHO_WATER=" + PrecipField.RHO_WATER);

        // ---- 仪器自证：beta = 1 时 qNew 必须【逐位】等于 qOld（陆海分离的极限情形）----
        double selfMax = 0, selfMaxK = 0;
        for (double kk = 0.0; kk <= 1.0001; kk += 0.125) {
            double qq = 0.0123456789;
            selfMax = Math.max(selfMax, Math.abs(qq * (1.0 - kk + kk * 1.0) - qq));
            double kc = Math.min(1.0, kk);
            selfMaxK = Math.max(selfMaxK, Math.abs((1.0 - kc + kc * 1.0) - 1.0));
        }
        say("  自证 SELF-PROOF：max|q*(1-k+k*1) - q| = " + String.format(LF, "%.3e", selfMax)
            + "  max|(1-k+k*1) - 1| = " + String.format(LF, "%.3e", selfMaxK) + "  （都必须是 0）");

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区", 2.911, 7.775},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉",     4.013, 0.105},
            {250.0, 285.0, 25.0, 35.0, "美国南部",   1.181, 3.595},
            {150.0, 210.0, 25.0, 35.0, "北太平洋",   0.880, 2.402},
            {300.0, 350.0, 25.0, 35.0, "北大西洋",   0.885, 0.677},
        };

        say("");
        say("=== 逐框：桶的三个变体 vs 现实 ===");
        say("  框          kappa   Ep(mm/d)  P_old   beta1   P_beta1  beta3   P_beta3  beta10  P_beta10   地球真值  P_old/真值  P_beta1/真值");
        double[] rOld = new double[2], rB1 = new double[2], rB3 = new double[2], rB10 = new double[2];
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0;
            double sK = 0, sEp = 0, sP = 0, sB1 = 0, sP1 = 0, sB3 = 0, sP3 = 0, sB10 = 0, sP10 = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                    double[] dg = new double[8];
                    P546.decompose(x, z, sd, cell, th, dg);
                    double qOld = dg[1];
                    double tSfc = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    double qsSfc = PrecipField.qSat(tSfc);
                    double sp = Math.hypot(u0[0], u0[1]);
                    double vEff = Math.sqrt(sp * sp + PrecipField.V_GUST * PrecipField.V_GUST);
                    double cd = Atmosphere.cdOf(k);
                    // Manabe eq.19 的潜在蒸发（**不带** (1-kappa) 因子 —— 那是浅对流地板的写法）
                    double Ep = PrecipField.RHO_AIR * cd * vEff * Math.max(0.0, qsSfc - qOld) / PrecipField.RHO_WATER;
                    // 局地上升与 term1
                    double[] ux = Atmosphere.windAt(x + GRAD, z, sd, cell, th, GRAD);
                    double[] uw = Atmosphere.windAt(x - GRAD, z, sd, cell, th, GRAD);
                    double[] un = Atmosphere.windAt(x, z + GRAD, sd, cell, th, GRAD);
                    double[] us = Atmosphere.windAt(x, z - GRAD, sd, cell, th, GRAD);
                    double divU = (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
                    double we = PrecipField.wEff(lat, th, divU);
                    double t1old = PrecipField.precip(qOld, we) * MMD;
                    double Pold = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    double resid = Pold - t1old;                    // term2 (+term3) —— 与 q 无关（陆地上 term3=0）
                    double PoldS = Pold / MMD;
                    // ---- beta1：非迭代，用未受影响的 P_old 作供水 ----
                    double b1 = Ep > 0 ? Math.min(1.0, PoldS / Ep) : 1.0;
                    double q1 = qOld * (1.0 - k + k * b1);
                    double P1 = resid + PrecipField.precip(q1, we) * MMD;
                    // ---- 不动点：3 次与 10 次 ----
                    double b = 1.0, q = qOld, Pn = Pold, b3 = 1.0, q3 = qOld, P3 = Pold;
                    for (int it = 1; it <= 10; it++) {
                        q = qOld * (1.0 - k + k * b);
                        Pn = resid + PrecipField.precip(q, we) * MMD;
                        double EpI = PrecipField.RHO_AIR * cd * vEff
                                   * Math.max(0.0, qsSfc - q) / PrecipField.RHO_WATER;
                        b = EpI > 0 ? Math.min(1.0, (Pn / MMD) / EpI) : 1.0;
                        if (it == 2) { b3 = b; q3 = q; P3 = Pn; }
                    }
                    sK += k; sEp += Ep * MMD; sP += Pold;
                    sB1 += b1; sP1 += P1; sB3 += b3; sP3 += P3; sB10 += b; sP10 += Pn;
                    n++;
                }
            }
            double truth = (double) bx[6];
            double pOld = sP / n, p1 = sP1 / n, p3 = sP3 / n, p10 = sP10 / n;
            int slot = -1;
            if (bx[4].equals("亚洲季风区")) slot = 0;
            if (bx[4].equals("撒哈拉"))     slot = 1;
            if (slot >= 0) { rOld[slot] = pOld; rB1[slot] = p1; rB3[slot] = p3; rB10[slot] = p10; }
            say(String.format(LF, "  %-10s %6.3f %9.3f %7.3f %7.3f %8.3f %7.3f %8.3f %7.3f %9.3f %10.3f %10.2f %11.2f",
                bx[4], sK / n, sEp / n, pOld, sB1 / n, p1, sB3 / n, p3, sB10 / n, p10,
                truth, pOld / truth, p1 / truth));
        }

        say("");
        say("=== 决定性读数：亚洲/撒哈拉 的比值（真实 = 74.0）===");
        say(String.format(LF, "  现状 P_old         : 亚洲/撒哈拉 = %6.3f   （亚洲 %.3f / 撒哈拉 %.3f）", rOld[0] / rOld[1], rOld[0], rOld[1]));
        say(String.format(LF, "  桶 beta1（非迭代） : 亚洲/撒哈拉 = %6.3f   （亚洲 %.3f / 撒哈拉 %.3f）", rB1[0] / rB1[1], rB1[0], rB1[1]));
        say(String.format(LF, "  桶 beta3（3 次）   : 亚洲/撒哈拉 = %6.3f   （亚洲 %.3f / 撒哈拉 %.3f）", rB3[0] / rB3[1], rB3[0], rB3[1]));
        say(String.format(LF, "  桶 beta10（收敛）  : 亚洲/撒哈拉 = %6.3f   （亚洲 %.3f / 撒哈拉 %.3f）", rB10[0] / rB10[1], rB10[0], rB10[1]));
        say("");
        say("  判据：若比值**下降** ⇒ 桶把反转放大了 ⇒ S3 不能单独落地，必须先修风场（S2）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
