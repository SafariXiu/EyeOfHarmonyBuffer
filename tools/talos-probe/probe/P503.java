package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P503：**由观测的涡动 MFC 反解「需要的 K(y)」**，与模型的 K(y) 逐纬度对照（§254.5）。
 *
 * <p>思路（一步纯代数）：闭合式是 @@v'q' = -K * dW/dy@@，@@MFC = -d(v'q')/dy@@。
 * 观测给了 @@MFC_obs(y)@@（@@EDDY_MFC_OBS_MONTH@@，无量纲归一化，45~60 度年均 = 1）。
 * 把它沿 y **积分**得到 @@v'q'@@ 的**形状**（差一个常数，用"极地无通量"定），
 * 再除以模型自己的 @@dW/dy@@ ⇒ **需要的 K(y) 形状**。
 *
 * <p>这是**诊断**，不是处方：它不写进任何公式，只回答「模型的 K(y) 与观测要求的 K(y) 差在哪」。
 *
 * <p><b>自检</b>
 * <ol>
 *   <li>[1] 梯形积分 vs Simpson 积分：两者的差就是求积误差（**两个独立算法**，不是定义式，E86 纪律）</li>
 *   <li>[2] **反解出的 K 必须与生产自洽**：@@K_prod = eddyMfc/(curv*gate)@@（从生产量反推）
 *       必须等于 @@K_probe = gain*tau*sigma^2*L_d^2@@（独立重算），逐位</li>
 * </ol>
 */
public class P503 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P503] " + s); rep.flush(); System.out.println("[P503] " + s); System.out.flush(); }

    static final int N = 19;                       // 0..90 步长 5
    static final double D = Math.toRadians(5.0);
    static final double DY = D * WorldContract.R_EFF;

    static int failQuad = 0, failK = 0;
    static double worstK = 0.0;

    public static void main(String[] args) throws Exception {
        double th = args.length > 0 ? Double.parseDouble(args[0]) : 0.0;
        String tag = args.length > 1 ? args[1] : "SOL";
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p503_report_" + tag + ".txt"), "UTF-8");
        say("P503：由观测涡动 MFC 反解需要的 K(y)   theta=" + th + "   (" + (th == 0 ? "夏至" : "JJA") + ")");
        say("");

        double[] W = new double[N], Wp = new double[N], mfc = new double[N];
        double[] Kp = new double[N], curv = new double[N], gate = new double[N];
        for (int i = 0; i < N; i++) {
            double lat = Math.toRadians(i * 5.0);
            W[i] = PrecipField.columnWater(lat, th);
            mfc[i] = ZonalTables.eddyMfcObsMonth(lat, th);
            gate[i] = PrecipField.stormGate(lat, th);
            double c = Math.min(lat, Math.PI / 2.0 - D);
            curv[i] = (PrecipField.columnWater(c + D, th) - 2.0 * PrecipField.columnWater(c, th)
                     + PrecipField.columnWater(c - D, th)) / (DY * DY);
            Kp[i] = PrecipField.eddyMfc(lat, th);
        }
        for (int i = 1; i < N - 1; i++) Wp[i] = (W[i + 1] - W[i - 1]) / (2.0 * DY);
        Wp[0] = (W[1] - W[0]) / DY;
        Wp[N - 1] = (W[N - 1] - W[N - 2]) / DY;

        // ---- 通量形状：把 MFC 沿 y 积分（极地无通量）----
        double[] vqT = new double[N], vqS = new double[N];
        vqT[N - 1] = 0; vqS[N - 1] = 0;
        for (int i = N - 2; i >= 0; i--) vqT[i] = vqT[i + 1] + 0.5 * (mfc[i] + mfc[i + 1]) * DY;
        for (int i = N - 3; i >= 0; i--)                       // Simpson（端点用梯形）
            vqS[i] = vqS[i + 2] + (mfc[i] + 4.0 * mfc[i + 1] + mfc[i + 2]) / 3.0 * DY;
        vqS[N - 2] = vqS[N - 1] + 0.5 * (mfc[N - 2] + mfc[N - 1]) * DY;
        // E87 FIX: v1 把「梯形 vs Simpson 一致到 2%」写成硬断言。被积函数 MFC_obs 在 5 度网格上
        // 是**噪声的**（相邻节点正负跳变），求积对它是**病态**的 —— 2% 这个容差没有任何依据，
        // 报了 17 次假 FAIL。求积一致性是**测出来的不确定度**，不是 PASS/FAIL。
        // NEW RULE: 只有在被积函数于所用网格上光滑时，才允许把两种求积的一致性写成断言。
        double worstQ = 0.0, medQ = 0.0; int nq = 0;
        for (int i = 0; i < N - 2; i++) {
            double rel = Math.abs(vqT[i] - vqS[i]) / Math.max(1e-30, Math.abs(vqT[i]));
            worstQ = Math.max(worstQ, rel); medQ += rel; nq++;
        }
        medQ = nq > 0 ? medQ / nq : 0.0;
        // 需要的 K(y) = -vq / W'
        double[] Kt = new double[N];
        for (int i = 1; i < N - 1; i++) Kt[i] = -vqT[i] / Wp[i];

        // ---- 自检 [2]：生产反推的 K vs 独立重算的 K ----
        double[] Kprod = new double[N];
        for (int i = 0; i < N; i++) {
            double den = curv[i] * gate[i];
            if (Math.abs(den) < 1e-30) { Kprod[i] = Double.NaN; continue; }
            Kprod[i] = Kp[i] / den;
            double Kmodel = (PrecipField.EDDY_CLOSURE == 0)
                ? PrecipField.EDDY_MIX * PrecipField.eadyGrowth(Math.toRadians(i * 5.0), th)
                  * Math.pow(PrecipField.deformRadius(Math.toRadians(i * 5.0), th), 2)
                : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU
                  * Math.pow(PrecipField.eadyGrowth(Math.toRadians(i * 5.0), th), 2)
                  * Math.pow(PrecipField.deformRadius(Math.toRadians(i * 5.0), th), 2);
            double rel = Math.abs(Kmodel - Kprod[i]) / Math.max(1e-30, Math.abs(Kmodel));
            if (rel > 1e-9) failK++;
            worstK = Math.max(worstK, rel);
        }

        say(String.format(LF, "  %5s | %10s %10s | %11s %11s | %11s %11s | %9s",
                "latN", "W", "dW/dy(1e-6)", "MFC_obs", "vq_obs", "K_need(1e7)", "K_model(1e7)", "比"));
        for (int i = 0; i < N; i++) {
            double kt = Kt[i] / 1e7, km = Kprod[i] / 1e7;
            say(String.format(LF, "  %5d | %10.2f %10.2f | %11.3f %11.3e | %11.3f %11.3f | %9s",
                    i * 5, W[i], Wp[i] * 1e6, mfc[i], vqT[i], kt, km,
                    (Math.abs(kt) > 1e-9 && Math.abs(km) > 1e-9) ? String.format(LF, "%.3f", kt / km) : "-"));
        }
        say("");
        say("★ 读法（**形状**才是信号，绝对值不是 —— vq_obs 是无量纲归一化的）：");
        say("   把 vq 各自按 45 度归一，看它随纬度**塌得多快**：");
        double v45o = vqT[9], v45m = -Kprod[9] * Wp[9];
        for (int i = 7; i <= 15; i++)
            say(String.format(LF, "     %3d 度：观测 %.2f   模型 %.2f", i * 5, vqT[i] / v45o, (-Kprod[i] * Wp[i]) / v45m));
        say("");
        say("I. 模型自己的温度链 vs 模型**自带**的 ERA5 月表（零新数据的一致性检查）");
        say("   zonalSlTemp = zonalMeanSeaLevelK + seasonalAnomalyZonal   应当 ≈ tZmSlMonth（观测表）");
        say(String.format(LF, "  %5s | %11s %11s %11s | %11s %11s %11s",
                "latN", "zonalSlTemp", "tZmSlMonth", "差(K)", "W(前者)", "W(观测表)", "W比"));
        for (int i = 0; i < N; i++) {
            double lat = Math.toRadians(i * 5.0);
            double tModel = com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.zonalMeanSeaLevelK(lat)
                          + com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere.seasonalAnomalyZonal(lat, th);
            double tObs = ZonalTables.tZmSlMonth(lat, th);
            double wm = PrecipField.columnWater(lat, th);
            double wo = PrecipField.RH_SEA * PrecipField.qSat(tObs) * PrecipField.RHO_AIR * PrecipField.H_MOIST;
            say(String.format(LF, "  %5d | %11.2f %11.2f %11.2f | %11.2f %11.2f %11.3f",
                    i * 5, tModel, tObs, tModel - tObs, wm, wo, wm / wo));
        }
        say("");
        say("J. 候选 v*_alt = abs(dT_ocean/dy) * L_d （锚在 SST 锋上，L-12）");
        say("   K_alt = GAIN*tau*v*_alt^2（与现式同归一）—— 只看**形状**，量级由标定管");
        double[] TO = new double[N], dTO = new double[N], vAlt = new double[N], vCur = new double[N];
        for (int i = 0; i < N; i++) TO[i] = ZonalTables.tOceanK(Math.toRadians(i * 5.0));
        for (int i = 1; i < N - 1; i++) dTO[i] = (TO[i + 1] - TO[i - 1]) / (2.0 * DY);
        dTO[0] = (TO[1] - TO[0]) / DY; dTO[N - 1] = (TO[N - 1] - TO[N - 2]) / DY;
        double worstV = 0.0;
        for (int i = 0; i < N; i++) {
            double lat = Math.toRadians(i * 5.0);
            double ld = PrecipField.deformRadius(lat, th);
            vAlt[i] = Math.abs(dTO[i]) * ld;
            // 自检：由生产 K 反推 v*，必须等于 sigma*L_d
            double K0 = (PrecipField.EDDY_CLOSURE == 0)
                ? PrecipField.EDDY_MIX * PrecipField.eadyGrowth(lat, th) * ld * ld
                : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU
                  * Math.pow(PrecipField.eadyGrowth(lat, th), 2) * ld * ld;
            vCur[i] = Math.sqrt(K0 / (PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU));
            double direct = PrecipField.eadyGrowth(lat, th) * ld;
            worstV = Math.max(worstV, Math.abs(vCur[i] - direct) / Math.max(1e-30, direct));
        }
        double mxAlt = 0, mxCur = 0;
        for (int i = 5; i <= 16; i++) { mxAlt = Math.max(mxAlt, vAlt[i]); mxCur = Math.max(mxCur, vCur[i]); }
        say(String.format(LF, "  %5s | %12s %10s | %11s %11s | %11s %11s | %10s",
                "latN", "dT_o/dy(1e-6)", "Ld(km)", "v*_alt", "v*_alt/max", "v*(现)", "v*现/max", "K_alt/K现"));
        for (int i = 5; i <= 16; i++) {
            double ka = vAlt[i] * vAlt[i], kc = vCur[i] * vCur[i];
            say(String.format(LF, "  %5d | %12.3f %10.1f | %11.3f %11.3f | %11.3f %11.3f | %10.3f",
                    i * 5, dTO[i] * 1e6, PrecipField.deformRadius(Math.toRadians(i * 5.0), th) / 1000.0,
                    vAlt[i], vAlt[i] / mxAlt, vCur[i], vCur[i] / mxCur, ka / kc));
        }
        int pkA = 5, pkC = 5;
        for (int i = 5; i <= 16; i++) { if (vAlt[i] > vAlt[pkA]) pkA = i; if (vCur[i] > vCur[pkC]) pkC = i; }
        say(String.format(LF, "  ⇒ v*_alt 峰在 %d 度；v*(现) 峰在 %d 度。", pkA * 5, pkC * 5));
        say(String.format(LF, "  [3] v* 反推 vs sigma*L_d 直接算   最大相对差 = %.3e   %s", worstV, worstV < 1e-12 ? "PASS" : "FAIL"));
        say("");
        say("K. 通量饱和候选（S255.3 的 W-2）：vq_sat = F_sat*tanh(vq_raw/F_sat)，MFC = -d(vq)/dy");
        double FS = args.length > 2 ? Double.parseDouble(args[2]) : 0.7;
        double[] vqRaw = new double[N], vqSat = new double[N], mfcRaw = new double[N], mfcSat = new double[N];
        for (int i = 0; i < N; i++) vqRaw[i] = -Kprod[i] * Wp[i];     // v'q' = -K dW/dy（poleward > 0）
        double mx = 0; for (int i = 7; i <= 16; i++) mx = Math.max(mx, vqRaw[i]);
        double Fsat = FS * mx;
        for (int i = 0; i < N; i++) vqSat[i] = Fsat * Math.tanh(vqRaw[i] / Fsat);
        for (int i = 1; i < N - 1; i++) {
            mfcRaw[i] = -(vqRaw[i + 1] - vqRaw[i - 1]) / (2.0 * DY);
            mfcSat[i] = -(vqSat[i + 1] - vqSat[i - 1]) / (2.0 * DY);
        }
        // E90 FIX: v1 summed over i = 1..N-2, which includes latitudes where Kprod is NaN
        // (curv*gate == 0 -> guarded division -> NaN) and silently produced NaN residuals.
        // NEW RULE: a range-spanning check must first assert the range contains no NaN.
        int LO = 8, HI = 13;                       // 40..65 度：这一段 Kprod 全部有限
        double telRaw = 0, telSat = 0; boolean finite = true;
        for (int i = LO; i <= HI; i++) {
            if (!Double.isFinite(vqRaw[i - 1]) || !Double.isFinite(vqRaw[i]) || !Double.isFinite(vqRaw[i + 1])) finite = false;
            telRaw += mfcRaw[i] * DY; telSat += mfcSat[i] * DY;
        }
        double expRaw = -0.5 * (vqRaw[HI] + vqRaw[HI + 1] - vqRaw[LO - 1] - vqRaw[LO]);
        double expSat = -0.5 * (vqSat[HI] + vqSat[HI + 1] - vqSat[LO - 1] - vqSat[LO]);
        if (!finite) { expRaw = Double.NaN; }
        say(String.format(LF, "  FS = %.2f   F_sat = %.4e ( = FS * max(vq_raw) over 35..80 deg )", FS, Fsat));
        double mR = 0, mS = 0;
        for (int i = 8; i <= 13; i++) { mR = Math.max(mR, Math.abs(mfcRaw[i])); mS = Math.max(mS, Math.abs(mfcSat[i])); }
        say(String.format(LF, "  %5s | %11s %11s | %11s %11s | %10s | %9s",
                "latN", "vq_raw", "vq_sat", "MFC_raw", "MFC_sat", "MFC_sat/max", "obs MFC"));
        for (int i = 7; i <= 16; i++)
            say(String.format(LF, "  %5d | %11.3e %11.3e | %11.3e %11.3e | %10.3f | %9.3f",
                    i * 5, vqRaw[i], vqSat[i], mfcRaw[i], mfcSat[i], mfcSat[i] / mS, mfc[i]));
        say(String.format(LF, "  [4] 望远镜求和 = -(端点组合)，40~65 度   raw 残差 %.3e / sat 残差 %.3e   %s",
                Math.abs(telRaw - expRaw), Math.abs(telSat - expSat),
                (finite && Math.abs(telRaw - expRaw) < 1e-9 * Math.max(1e-30, Math.abs(expRaw))
                 && Math.abs(telSat - expSat) < 1e-9 * Math.max(1e-30, Math.abs(expSat))) ? "PASS" : "FAIL"));
        say("");
        say("H. 自检");
        say(String.format(LF, "  [1]（报告值，非断言）梯形 vs Simpson：中位相对差 = %.3e，最大 = %.3e", medQ, worstQ));
        say("      ⇒ 它衡量的是「5 度网格上积分噪声剖面」的不确定度，不是对错。");
        say(String.format(LF, "  [2] 生产反推的 K vs 独立重算的 K           最大相对差 = %.3e   %s", worstK, failK == 0 ? "PASS" : ("FAIL n=" + failK)));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
