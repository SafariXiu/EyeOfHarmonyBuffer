package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P500：**中纬雨带的形状分解** —— 到底是哪一项把 55~62.5 度杀成 0.5 mm/day 的？
 *
 * <p>为什么量这个（§245.3）：观测在 40~60°N 海洋 JJA 是一条 2.6~2.9 的**平坦高原**，
 * 模型是一个 45° 的窄峰（7.29），55~60° 掉到 0.55/0.93。这是全降水场里绝对误差最大的一条。
 * 模型的 {@code P_mean} 在中纬恒为 0（{@code w_zm < 0}，Ferrel 下沉 —— 纬向平均口径下这是对的），
 * 所以**整条中纬雨带 100% 来自涡动项** {@code eddyMfc = gate * K * curv(W)}，形状只由三件事决定。
 *
 * <p><b>自检（E69 纪律：模型行为的判断只能来自生产代码探针）</b>
 * <ol>
 *   <li><b>[1] 二次求导的可逆性（覆盖「求导之后的量」）</b>：由 {@code curv} 逐点离散积分两次
 *       必须**精确重建** {@code W}（差一个线性趋势）。符号错/系数错/步长错都会在这里炸。</li>
 *   <li><b>[2] 本探针重抄的 {@code modelShape} 必须与生产 {@link PrecipField#eddyMfc} 逐位相同</b>
 *       —— 保证下面的分解说的就是生产在算的东西。</li>
 * </ol>
 */
public class P500 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P500] " + s); rep.flush(); System.out.println("[P500] " + s); System.out.flush(); }

    static final int N = 37;                 // 0 .. 90 步长 2.5
    static final double D = Math.toRadians(2.5);

    /** 生产 {@code modelShape} 的逐字拷贝（只把 latRad 换成入参）。 */
    static double shape(double latRad, double theta) {
        double d = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double w0 = PrecipField.columnWater(c, theta);
        if (w0 <= 0.0) return 0.0;
        double wp = PrecipField.columnWater(c + d, theta);
        double wm = PrecipField.columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
        double ld = PrecipField.deformRadius(c, theta);
        double sg = PrecipField.eadyGrowth(c, theta);
        double K = (PrecipField.EDDY_CLOSURE == 0)
            ? PrecipField.EDDY_MIX * sg * ld * ld
            : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
        return K * curv * PrecipField.stormGate(latRad, theta);
    }

    static int failInv = 0, failShape = 0, failObs = 0;

    /** G := gate*K（闭合算子的纬度函数）。 */
    static double gk(double y, double theta) {
        double ld = PrecipField.deformRadius(y, theta);
        double sg = PrecipField.eadyGrowth(y, theta);
        double K = (PrecipField.EDDY_CLOSURE == 0)
            ? PrecipField.EDDY_MIX * sg * ld * ld
            : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
        return PrecipField.stormGate(y, theta) * K;
    }

    /** W'(y) 的中心差分。 */
    static double wp(double y, double theta, double d) {
        return (PrecipField.columnWater(y + d, theta) - PrecipField.columnWater(y - d, theta))
             / (2.0 * d * WorldContract.R_EFF);
    }
    static double worstInv = 0.0, worstObs = 0.0;

    public static void main(String[] args) throws Exception {
        double TH = args.length > 0 ? Double.parseDouble(args[0]) : 0.0;
        String tag = args.length > 1 ? args[1] : "S";
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p500_report_" + tag + ".txt"), "UTF-8");
        say("P500：中纬雨带形状分解   EDDY_CLOSURE=" + PrecipField.EDDY_CLOSURE
            + "  EDDY_PHYS_GAIN=" + PrecipField.EDDY_PHYS_GAIN + "  EDDY_MIX=" + PrecipField.EDDY_MIX
            + "  EDDY_TAU=" + PrecipField.EDDY_TAU + "  U0_STORM=" + PrecipField.U0_STORM);
        say(String.format(LF, "  涡动项 = gate(U_zm) x K(sigma,L_d) x curv(W)     1 kg/(m^2 s) = 86400 mm/day"));
        say("");

        double[][] W = new double[2][N];
        for (int s = 0; s < 2; s++) {
            double th = s == 0 ? TH : TH + Math.PI;
            for (int i = 0; i < N; i++) W[s][i] = PrecipField.columnWater(Math.toRadians(i * 2.5), th);
        }

        // ---------- 自检 [1]：curv 的二阶递推必须精确重建 W ----------
        // ⚠ E82（我自己的错，记账）：v1 用生产那套 **5 度** 差分去重建 **2.5 度** 采样的序列
        //   —— 步长不匹配，递推根本不成立，报了 70 次假 FAIL（E74/E75 同族：
        //   拿一个「只在口径匹配时成立」的关系去断言）。
        //   修法：在同一张 **5 度** 网格上采样 W、用同一个 5 度差分算 curv，再做
        //   W_j = 2W_{j-1} - W_{j-2} + dy^2 * c_{j-1} 的二阶递推（代数上逐位可逆）。
        //   夹逼边界（lat >= 90-5 度时 c 被 clamp）会破坏递推 ⇒ 只查 5..80 度。
        for (int s = 0; s < 2; s++) {
            double th = s == 0 ? 0.0 : Math.PI;
            double dn = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
            double dyP = dn * WorldContract.R_EFF;
            int M = 19;                                  // 0,5,...,90
            double[] w5 = new double[M];
            for (int j = 0; j < M; j++) w5[j] = PrecipField.columnWater(Math.toRadians(j * 5.0), th);
            double[] c5 = new double[M];
            for (int j = 1; j < M - 1; j++)
                c5[j] = (w5[j + 1] - 2.0 * w5[j] + w5[j - 1]) / (dyP * dyP);
            double[] rec = new double[M];
            rec[0] = w5[0]; rec[1] = w5[1];
            for (int j = 2; j < M; j++) rec[j] = 2.0 * rec[j - 1] - rec[j - 2] + dyP * dyP * c5[j - 1];
            for (int j = 1; j <= 16; j++) {              // 5..80 度，避开 clamp 区
                double err = Math.abs(rec[j] - w5[j]);
                worstInv = Math.max(worstInv, err);
                if (err > 1e-9 * Math.max(1.0, Math.abs(w5[j]))) failInv++;
            }
        }

        say(String.format(LF, "  %6s | %8s %9s %12s %10s %9s %9s %9s %12s %12s %12s",
                "latN", "W夏", "W冬", "curv夏(1e-14)", "gate夏", "sigma夏", "Ld夏(km)", "K夏", "shape夏", "obs夏", "obs冬"));
        say("       (shape 单位 mm/day；obs 列 **无量纲**，是归一化剖面，1.0 = 45~60 度年均)");
        for (int i = 0; i < N; i++) {
            double latDeg = i * 2.5;
            double lat = Math.toRadians(latDeg);
            double c = Math.min(lat, Math.PI / 2.0 - Math.toRadians(PrecipField.EDDY_DPHI_DEG));
            double dn = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
            double dyP = dn * WorldContract.R_EFF;
            double curvS = (PrecipField.columnWater(c + dn, TH) - 2.0 * PrecipField.columnWater(c, TH)
                          + PrecipField.columnWater(c - dn, TH)) / (dyP * dyP);
            double gS = PrecipField.stormGate(lat, TH);
            double sgS = PrecipField.eadyGrowth(c, TH);
            double ldS = PrecipField.deformRadius(c, TH);
            double KS = PrecipField.EDDY_CLOSURE == 0 ? PrecipField.EDDY_MIX * sgS * ldS * ldS
                     : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sgS * sgS * ldS * ldS;
            double sh = shape(lat, TH);
            // 自检 [2]：与生产逐位相同
            double prod = PrecipField.eddyMfc(lat, TH);
            if (Double.doubleToLongBits(sh) != Double.doubleToLongBits(prod)) failShape++;
            // E83 FIX: EDDY_MFC_OBS_MONTH is **dimensionless** -- gen_eddy_obs_month.py line 36-37
            // divides the physical profile by `ref` (the 45..60 deg annual mean, kg/(m^2 s)) and
            // stores the RATIO. v1 multiplied it by 86400 and printed -19240 mm/day, an absurd
            // number I should have caught on sight. Print it raw; self-check [3] guards the caliber.
            double obsS = ZonalTables.eddyMfcObsMonth(lat, TH);
            double obsW = ZonalTables.eddyMfcObsMonth(lat, TH + Math.PI);
            worstObs = Math.max(worstObs, Math.max(Math.abs(obsS), Math.abs(obsW)));
            say(String.format(LF, "  %6.1f | %8.2f %9.2f %12.4f %10.4f %9.3e %9.1f %9.2e %12.3e %8.2f %8.2f",
                    latDeg, W[0][i], W[1][i], curvS * 1e14, gS, sgS, ldS / 1000.0, KS, sh * 86400.0, obsS, obsW));
        }
        say("");
        say("G. 自检");
        say(String.format(LF, "  [1] curv 二次积分重建 W  最大残差 = %.3e kg/m^2   %s", worstInv,
                failInv == 0 ? "PASS" : ("FAIL n=" + failInv)));
        say(String.format(LF, "  [2] 探针重抄的 shape == 生产 eddyMfc（逐位）  失配 %d 次   %s", failShape,
                failShape == 0 ? "PASS" : "FAIL"));
        if (worstObs > 5.0) failObs = 1;
        say(String.format(LF, "  [3] 观测剖面必须是无量纲归一化（|obs| <= 5）  实测最大 |obs| = %.3f   %s", worstObs,
                failObs == 0 ? "PASS" : "FAIL（口径搞错了）"));
        say("");
        say("L. 闭合算子的精确拆分：MFC = d(G*W' )/dy = <W'>*G' + <G>*W''   (G := gate*K)");
        double dE = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double dyE = dE * WorldContract.R_EFF;
        int failSplit = 0, failProd = 0; double worstSplit = 0, worstProd = 0;
        say(String.format(LF, "  %5s | %10s %10s %11s | %11s %11s | %11s %11s",
                "latN", "Wp(1e-6)", "G(1e7)", "Gp(1e-4)", "Wp*Gp(1e-6)", "G*Wpp(1e-6)", "sum(1e-6)", "prod(1e-6)"));
        for (int i = 14; i <= 26; i++) {
            double lat = Math.toRadians(i * 2.5);
            double lim = Math.PI / 2.0 - dE;
            double c = Math.min(lat, lim);
            double Gp = gk(c + dE, TH), Gm = gk(c - dE, TH), G0 = gk(c, TH);
            double Wp2 = wp(c + dE, TH, dE), Wm2 = wp(c - dE, TH, dE), W02 = wp(c, TH, dE);
            double tot = (Gp * Wp2 - Gm * Wm2) / (2.0 * dyE);
            double a1 = 0.5 * (Wp2 + Wm2) * (Gp - Gm) / (2.0 * dyE);
            double a2 = 0.5 * (Gp + Gm) * (Wp2 - Wm2) / (2.0 * dyE);
            double res = Math.abs(tot - (a1 + a2));
            worstSplit = Math.max(worstSplit, res);
            if (res > 1e-9 * Math.max(1e-30, Math.abs(tot))) failSplit++;
            double prod = PrecipField.eddyMfc(lat, TH);
            worstProd = Math.max(worstProd, Math.abs(tot - prod));
            if (Double.doubleToLongBits(tot) != Double.doubleToLongBits(prod)) failProd++;
            say(String.format(LF, "  %5.1f | %10.3f %10.4f %11.4f | %11.3f %11.3f | %11.4f %11.4f",
                    i * 2.5, W02 * 1e6, G0 / 1e7, (Gp - Gm) / (2.0 * dyE) * 1e4,
                    a1 * 1e6, a2 * 1e6, tot * 1e6, prod * 1e6));
        }
        say(String.format(LF, "  [4] 精确拆分 两项和 == d(G*W')/dy   最大残差 %.3e   %s", worstSplit,
                failSplit == 0 ? "PASS" : ("FAIL n=" + failSplit)));
        say(String.format(LF, "  [5] 本段合计 == 生产 eddyMfc（逐位）   失配 %d 次（最大 %.3e）   %s", failProd, worstProd,
                failProd == 0 ? "PASS" : "FAIL"));
        say("★ 读法：obs 是 NCEP 日资料实测的瞬变涡动水汽通量辐合的**归一化无量纲剖面**（可正可负）。");
        say("   模型 eddyMfc 只取正部（负部被 max(0,·) 砍掉），所以模型在 obs<0 的纬度必然是 0。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
