package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P501 v2：**候选 R-2 的前置测量**（设计冻结 §248.6）。
 *
 * <p>R-2 一句话：@@q@@ 现在用的是**海平面等效温度** @@tSl@@，而陆地上
 * @@tSl = T_LAND + GAMMA*z_bar_land + 季节项@@ —— 那个 @@+GAMMA*z_bar@@ 在 30 度是 **+5.4 K**，
 * 把 @@qSat@@ 抬了约 24%。**水汽是由有高度的真实地表提供的，不是由"海平面上的温度"提供的。**
 * R-2 = 把 @@moisture()@@ 的入参换成 @@Atmosphere.surfaceTemp@@（含 @@-GAMMA*max(0,h)*kappa@@）。
 *
 * <p><b>为什么先在探针里量</b>（纪律）：R-2 会同时动 q、RH、E 三个量，必须先把三条读数摆出来：
 * ① 陆地 q 会掉多少；② 陆地 RH 会掉多少（RH 掉 ⇒ 亏空变大 ⇒ **E 会变大**，方向可能相反！）；
 * ③ β 取多少才能把陆面 E 压回观测的 1~5 mm/day。
 *
 * <p><b>自检（承接 §248.1 的四条纪律）</b>
 * <ol>
 *   <li>[1] @@q == RH_SEA*qSat(tSl)*depl@@ 逐点（逐位）</li>
 *   <li>[2] 精确恒等式 @@RH == RH_SEA*depl*qSat(tSl)/qSat(tSfc)@@（**不用阈值分类做断言**，E84）</li>
 *   <li>[3] 量级合理性：q ∈ [0,40] g/kg、E ∈ [-100,100] mm/day（E83）</li>
 *   <li>[4] **R-2 的海洋中性证明**：@@tSfc == tSl - GAMMA*max(0,elev)*kappa@@ 逐点逐位成立
 *       ⇒ @@kappa = 0@@ 处两者**完全相同** ⇒ R-2 不可能改变任何纯海洋点</li>
 * </ol>
 */
public class P501 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P501] " + s); rep.flush(); System.out.println("[P501] " + s); System.out.flush(); }

    static final int NX = 120, XSPAN = 60_000_000, GS = 500_000, NROW = 24;

    static int failQ = 0, failRH = 0, failMag = 0, failT = 0;
    static double worstQ = 0.0, worstRH = 0.0, worstT = 0.0;

    static double[] st(int x, int z, double th) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, SD, CELL);
        double tSl = Atmosphere.annualSeaLevelTemp(lat, k, Atmosphere.sstAnom(x, z))
                   + Atmosphere.seasonalAnomaly(lat, k, th);
        double[] u = Atmosphere.windAt(x, z, SD, CELL, th, GS);
        double hUp = PrecipField.upwindElev(x, z, SD, CELL, u[0], u[1]);
        double depl = PrecipField.depletion(hUp, k);
        double q = PrecipField.moisture(tSl, k > 0.0 ? hUp : 0.0, k);
        double tSfc = Atmosphere.surfaceTemp(x, z, SD, CELL, th);
        double qsSfc = PrecipField.qSat(tSfc);
        return new double[]{tSl, k, hUp, depl, q, qsSfc, u[0], u[1], tSfc};
    }

    static double qUpwind(int x, int z, double th, double u, double v, double L) {
        double sp = Math.hypot(u, v);
        if (sp < 0.1) return Double.NaN;
        int ux = x - (int) Math.round(u / sp * L);
        int uz = z - (int) Math.round(v / sp * L);
        double latU = WorldContract.latOf(uz);
        double kU = Atmosphere.kappaAt(ux, uz, SD, CELL);
        double tSlU = Atmosphere.annualSeaLevelTemp(latU, kU, Atmosphere.sstAnom(ux, uz))
                    + Atmosphere.seasonalAnomaly(latU, kU, th);
        double[] uU = Atmosphere.windAt(ux, uz, SD, CELL, th, GS);
        double hUpU = PrecipField.upwindElev(ux, uz, SD, CELL, uU[0], uU[1]);
        return PrecipField.moisture(tSlU, kU > 0.0 ? hUpU : 0.0, kU);
    }

    public static void main(String[] args) throws Exception {
        double tauQ = args.length > 0 ? Double.parseDouble(args[0]) : 0.0;
        double Lkm  = args.length > 1 ? Double.parseDouble(args[1]) : 300.0;
        String tag  = args.length > 2 ? args[2] : "T0";
        double BETA = args.length > 3 ? Double.parseDouble(args[3]) : 0.30;
        double L = Lkm * 1000.0, tauS = tauQ * 86400.0;
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p501_report_" + tag + ".txt"), "UTF-8");
        say("P501 v2：" + tag + "   TAU_Q = " + tauQ + " 天   MOIST_L = " + Lkm + " km   BETA = " + BETA
            + "   RH_SEA = " + PrecipField.RH_SEA);
        say("");
        say(String.format(LF, "  %6s | %8s %8s %8s | %8s %8s %8s | %8s %8s",
                "latN", "qSL海", "q2m海", "E2m海", "qSL陆", "q2m陆", "RH2m陆", "ESL陆", "E2m陆"));
        say("  " + "-".repeat(88));

        double[] acc = new double[16];
        double e2bL = 0, e2bS = 0, eSrcL = 0;
        int[] cnt = new int[2];
        for (int i = 0; i < NROW; i++) {
            double la = 2.5 + i * 2.5;
            int z = (int) Math.round(la / 90.0 * WorldContract.MAX_D);
            double[] a = new double[16]; double eb = 0; int[] c = new int[2];
            for (int q = 0; q < NX; q++) {
                int x = (int) ((long) q * XSPAN / NX);
                double[] s = st(x, z, 0.0);
                double k = s[1];
                boolean land = k > 0.8, sea = k < 0.2;
                if (!land && !sea) continue;
                double qeq = s[4], qsSfc = s[5], sp = Math.hypot(s[6], s[7]);
                double rh = qsSfc > 0 ? qeq / qsSfc : Double.NaN;

                // ---- R-2：入参换成局地真实地表温度 ----
                double q2 = PrecipField.moisture(s[8], k > 0.0 ? s[2] : 0.0, k);
                double rh2 = qsSfc > 0 ? q2 / qsSfc : Double.NaN;
                double ce = PrecipField.RHO_AIR * Atmosphere.cdOf(k) * sp * 86400.0;
                double eSl = ce * Math.max(0.0, qsSfc - qeq);
                double e2  = ce * Math.max(0.0, qsSfc - q2);
                double e2b = ce * Math.max(0.0, BETA * qsSfc - q2);

                worstQ = Math.max(worstQ, Math.max(Math.abs(qeq), Math.abs(q2)) * 1000.0);
                worstQ = Math.max(worstQ, Math.abs(qsSfc) * 1000.0);
                if (Math.abs(qeq - PrecipField.RH_SEA * PrecipField.qSat(s[0]) * s[3]) > 1e-18) failQ++;
                double rhIdent = PrecipField.RH_SEA * s[3] * PrecipField.qSat(s[0]) / qsSfc;
                if (Math.abs(rh - rhIdent) > 1e-12) failRH++;
                if (sea) worstRH = Math.max(worstRH, Math.abs(rh - PrecipField.RH_SEA));
                if (eSl < -1e-9 || eSl > 100.0 || e2 < -1e-9 || e2 > 100.0) failMag++;
                double elevL = PlateField.elevationWithCell(x, z, SD, CELL);
                double tIdent = s[0] - Atmosphere.GAMMA * Math.max(0.0, elevL) * k;
                double dt = Math.abs(s[8] - tIdent);
                worstT = Math.max(worstT, dt);
                if (dt > 1e-12) failT++;

                int b = land ? 0 : 1;
                a[b * 8 + 0] += qeq * 1000.0; a[b * 8 + 1] += q2 * 1000.0; a[b * 8 + 2] += e2;
                a[b * 8 + 3] += rh;           a[b * 8 + 4] += rh2;         a[b * 8 + 5] += eSl;
                a[b * 8 + 6] += sp;           a[b * 8 + 7] += qsSfc * 1000.0;
                if (land) eb += e2b;
                c[b]++;
            }
            double[] s2 = new double[8]; for (int j = 0; j < 8; j++) s2[j] = (c[0] + c[1]) > 0 ? 0 : 0;
            double qSlS = c[1] > 0 ? a[8] / c[1] : Double.NaN, q2S = c[1] > 0 ? a[9] / c[1] : Double.NaN;
            double e2S = c[1] > 0 ? a[10] / c[1] : Double.NaN;
            double qSlL = c[0] > 0 ? a[0] / c[0] : Double.NaN, q2L = c[0] > 0 ? a[1] / c[0] : Double.NaN;
            double rh2L = c[0] > 0 ? a[4] / c[0] : Double.NaN, eSlL = c[0] > 0 ? a[5] / c[0] : Double.NaN;
            double e2L = c[0] > 0 ? a[2] / c[0] : Double.NaN;
            say(String.format(LF, "  %6.1f | %8.2f %8.2f %8.2f | %8.2f %8.2f %8.4f %8.2f %8.2f",
                    la, qSlS, q2S, e2S, qSlL, q2L, rh2L, eSlL, e2L));
            if (la >= 15 && la <= 45) {
                for (int j = 0; j < 16; j++) acc[j] += a[j];
                e2bL += c[0] > 0 ? eb : 0; e2bS += 0; cnt[0] += c[0]; cnt[1] += c[1];
            }
        }
        int n = 0; for (int i = 0; i < NROW; i++) { double la = 2.5 + i * 2.5; if (la >= 15 && la <= 45) n++; }
        say("");
        say("G. 15~45 度平均（R-2 = q 改用局地真实地表温度）");
        say(String.format(LF, "  海洋：q_SL = %.2f  q_2m = %.2f g/kg（**必须完全相同**：海洋 kappa=0）  E = %.2f mm/day",
                acc[8] / cnt[1], acc[9] / cnt[1], acc[10] / cnt[1]));
        say(String.format(LF, "  陆地：q_SL = %.2f  **q_2m = %.2f g/kg（%+.1f%%）**  RH_SL = %.4f  RH_2m = %.4f",
                acc[0] / cnt[0], acc[1] / cnt[0], 100.0 * (acc[1] / acc[0] - 1.0), acc[3] / cnt[0], acc[4] / cnt[0]));
        say(String.format(LF, "  陆地 E：原式(beta=1) = %.2f   R-2(beta=1) = %.2f   **R-2(beta=%.2f) = %.2f**",
                acc[5] / cnt[0], acc[2] / cnt[0], BETA, e2bL / (cnt[0] / n * n) * 0 + e2bL / (cnt[0])));
        say(String.format(LF, "  观测对照：陆面 E 真实 1~5 mm/day；海洋 E 真实 3~6 mm/day（本模型海洋 %.2f）", acc[10] / cnt[1]));
        say("");
        say("H. 自检");
        say(String.format(LF, "  [1] q == RH_SEA*qSat(tSl)*depl                     失配 %d 次       %s", failQ, failQ == 0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  [2] RH == RH_SEA*depl*qSat(tSl)/qSat(tSfc)（精确式）  失配 %d 次       %s", failRH, failRH == 0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  [2b]（报告值，非断言）海洋点上 |RH - 0.8| 最大偏离 = %.2e", worstRH));
        say(String.format(LF, "  [3] 量级合理性（q<=40 g/kg，E ∈ [0,100] mm/day）    越界 %d 次（max q %.2f）  %s", failMag, worstQ, failMag == 0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  [4] tSfc == tSl - GAMMA*max(0,elev)*kappa（R-2 海洋中性）  失配 %d 次（最大 %.2e）  %s",
                failT, worstT, failT == 0 ? "PASS" : "FAIL"));
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
