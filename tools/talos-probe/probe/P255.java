package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P255（v2）：**「带内均速」这个口径本身值不值得当验收标准** + 修正 beta 的仪器错误。
 *
 * <p>v1 的两个错误（本文件已修）：
 * <ol>
 *   <li>**beta 从未按纬度设置** —— Params 默认 3.24e-10 是 45 度的值，
 *       于是六个带全用了同一个 beta。正确值 beta(phi) = 2*Omega*pi/Z_CYCLE*cos(phi)；</li>
 *   <li>psi_max 用「累到东岸」求，而闭合行累到东岸必然是 0 ⇒ 打印的是离散残差。
 *       正确做法是取累计积分的**最大值**。</li>
 * </ol>
 * <p>带内均速 = psi_max / 带宽（W >> delta_M 时严格成立，因为 psi_max prop 1/beta）。
 */
public class P255 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685;
    static final double H_TOTAL = 4000.0;
    static final double H_THERMO = 800.0;
    static final double A_H = 1.9e4;
    static final double OMEGA = 7.2921e-5;
    static final double R_EARTH = 6.371e6;
    static final double[] BANDS = {0.15, 0.25, 0.40, 0.55, 0.70, 0.85};
    static final int NB = BANDS.length;
    static final double[] WS = {25, 50, 100, 122, 200, 300, 500};
    static final String[] WLAB = {"25", "50", "100", "122", "200", "300", "500"};
    static final double REAL_SV = 30.0;
    static final double REAL_W = 100_000.0;
    static final double REAL_H = 4000.0;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static double h = 5000.0;
    static GyreRow.BandedWind wind;
    static double[] psiMaxM = new double[NB], peakM = new double[NB], widthM = new double[NB];
    static double[] wEffM = new double[NB], deltaM = new double[NB], betaM = new double[NB];
    static double[][] bandMean = new double[NB][WS.length];
    static double[] bandMeanOwn = new double[NB];
    static int[] cntArr = new int[NB];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p255_report.txt"), "UTF-8");
        say("P255v2：带内均速这个口径 + 修正 beta（按纬度）");
        wind = new GyreRow.BandedWind(TAU0, ZC);
        say(String.format(LF, "  h=%.0f m  tau0=%.4f  A_H=%.3e  H=%.0f m  Z_CYCLE=%d",
            h, TAU0, A_H, H_TOTAL, ZC));
        say(String.format(LF, "  beta(phi) = 2*Omega*pi/Z_CYCLE*cos(phi) = %.4e*cos(phi)  （v1 误用常数 3.24e-10）",
            2 * OMEGA * Math.PI / ZC));
        say(String.format(LF, "  真实参照：湾流 %.0f Sv /(%.0f km x %.0f m) = %.1f mm/s（全深均口径）",
            REAL_SV, REAL_W / 1000, REAL_H, REAL_SV * 1e6 / (REAL_W * REAL_H) * 1000));
        say(String.format(LF, "  真实同口径传输量 = %.0f m^2/s（= Sv x 1e6 / H）", REAL_SV * 1e6 / REAL_H));
        say("");

        for (int bi = 0; bi < NB; bi++) {
            int z = (int) (BANDS[bi] * (ZC / 2));
            double latRad = BANDS[bi] * Math.PI / 2.0;
            double beta = 2.0 * OMEGA * Math.PI / ZC * Math.cos(latRad);
            betaM[bi] = beta;
            GyreRow.Params pb = new GyreRow.Params();
            pb.h = h; pb.rhoH = 1025.0 * H_TOTAL; pb.aH = A_H; pb.beta = beta;
            double dm = pb.delta();
            deltaM[bi] = dm;
            double mwGate = Math.PI * dm;
            GyreRow.Row[] tmp = new GyreRow.Row[8];
            int c = 0;
            for (int ix = 0; ix < 8; ix++) {
                GyreRow.Row r = GyreRow.solve(ix * 400_000, z, SD, CELL, wind, pb);
                if (!r.valid) continue;
                if ((r.eastX - r.westX) < 2 * mwGate) continue;
                tmp[c++] = r;
            }
            GyreRow.Row[] rows = Arrays.copyOf(tmp, c);
            cntArr[bi] = c;
            if (c == 0) { say(String.format(LF, "  bandD %.2f （无合格盆）", BANDS[bi])); continue; }
            double[] bm = new double[WS.length];
            double ownSum = 0;
            for (int k = 0; k < c; k++) {
                GyreRow.Row r = rows[k];
                int n = r.n;
                double L = (r.eastX - r.westX);
                // psi_max = 从西岸累计积分的最大绝对值
                double cum = 0, psiMax = 0, wOfMax = 0;
                double absInt = 0;
                for (int i = 0; i < n; i++) {
                    cum += r.v[i] * h;
                    absInt += Math.abs(r.v[i]) * h;
                    if (Math.abs(cum) > Math.abs(psiMax)) { psiMax = cum; wOfMax = i * h / 1000.0; }
                }
                // 峰值（跳过最外两格，避免边界格被 BC 放大）
                double pk = 0; int ipk = 1;
                for (int i = 1; i < n - 1; i++) if (Math.abs(r.v[i]) > Math.abs(pk)) { pk = r.v[i]; ipk = i; }
                psiMaxM[bi] += psiMax / c;
                peakM[bi] += Math.abs(pk) / c * 1000;
                widthM[bi] += L / 1000.0 / c;
                wEffM[bi] += (absInt / Math.abs(pk)) / 1000.0 / c;
                for (int wi = 0; wi < WS.length; wi++) {
                    int q = (int) (WS[wi] * 1000.0 / h);
                    if (q > n) q = n;
                    double s = 0;
                    for (int i = 0; i < q; i++) s += r.v[i];
                    bm[wi] += s / q / c * 1000;
                }
                int qo = Math.min(n, (int) (Math.PI * dm / h));
                double so = 0;
                for (int i = 0; i < qo; i++) so += r.v[i];
                ownSum += so / qo / c * 1000;
                if (k == 0) say(String.format(LF, "      row0: 盆宽=%.0f km n=%d psi_max=%.1f m^2/s 峰值=%.2f mm/s 峰值位置=%.1f km psi_max位置=%.1f km",
                    L / 1000.0, n, psiMax, Math.abs(pk) * 1000, ipk * h / 1000.0, wOfMax));
            }
            bandMean[bi] = bm;
            bandMeanOwn[bi] = ownSum;
            say(String.format(LF, "  --- bandD %.2f n_basin=%d beta=%.3e (地球 x%.1f) delta_M=%.1f km pi*delta_M=%.0f km",
                BANDS[bi], c, beta, beta / (2 * OMEGA * Math.cos(latRad) / R_EARTH), dm / 1000, Math.PI * dm / 1000));
            say(String.format(LF, "      盆宽=%.0f km psi_max=%.1f m^2/s (=%.1f Sv@4000m, =%.2f Sv实际传输) 峰值=%.2f mm/s 有效宽=%.0f km",
                widthM[bi], psiMaxM[bi], psiMaxM[bi] * H_TOTAL / 1e6, psiMaxM[bi] * H_TOTAL / 1e6,
                peakM[bi], wEffM[bi]));
            say(String.format(LF, "      带内均速(mm/s)  %s  | 自身带宽(%.0fkm)=%.2f",
                wsRow(bm), Math.PI * dm / 1000, ownSum));
            say("");
        }

        say("A. 带宽灵敏度（修正 beta 后）：西带均速（正压）随带宽怎么变 + 达标数（表层=x5）");
        say(String.format(LF, "  %8s %14s %14s %16s %16s", "带宽 km", "6带均值", "最好带", "对50达标(表层)", "对100达标(表层)"));
        for (int wi = 0; wi < WS.length; wi++) {
            double sum = 0, best = -1e9; int p50 = 0, p100 = 0, cc = 0;
            for (int bi = 0; bi < NB; bi++) {
                if (cntArr[bi] == 0) continue;
                double v = Math.abs(bandMean[bi][wi]);
                sum += v; cc++; if (v > best) best = v;
                if (v * 5.0 >= 50) p50++;
                if (v * 5.0 >= 100) p100++;
            }
            say(String.format(LF, "  %8s %11.2f mm/s %11.2f mm/s %13d/%d %15d/%d",
                WLAB[wi], sum / cc, best, p50, cc, p100, cc));
        }
        say("");

        say("B. 与带宽无关的口径：psi_max（= 边界层传输量, m^2/s）与换算成 Sv");
        double psiAll = 0; int cc = 0;
        for (int bi = 0; bi < NB; bi++) if (cntArr[bi] > 0) { psiAll += Math.abs(psiMaxM[bi]); cc++; }
        psiAll /= cc;
        double svAll = psiAll * H_TOTAL / 1e6;
        say(String.format(LF, "  6带平均 psi_max = %.1f m^2/s ⇒ %.2f Sv（H=%.0f m）", psiAll, svAll, H_TOTAL));
        say(String.format(LF, "  真实副热带涡 30 Sv ⇒ 差 %.1f 倍；真实同口径 psi_max = %.1f m^2/s",
            REAL_SV / svAll, REAL_SV * 1e6 / H_TOTAL));
        say(String.format(LF, "  用温跃层口径（H=%.0f m）折算带内均速(122km)：%.1f mm/s；真实同口径 %.1f mm/s ⇒ 差 %.1f 倍",
            H_THERMO, psiAll * H_THERMO / 122_000.0 * 1000, REAL_SV * 1e6 / (REAL_W * H_THERMO) * 1000,
            (REAL_SV * 1e6 / (REAL_W * H_THERMO)) / (psiAll * H_THERMO / 122_000.0)));
        say("");

        say(String.format(LF, "C. 要把 psi_max 补到真实量级（%.1f m^2/s）需要动什么（现状 %.1f，倍率 %.1f）",
            REAL_SV * 1e6 / H_TOTAL, psiAll, REAL_SV * 1e6 / H_TOTAL / psiAll));
        double need = REAL_SV * 1e6 / H_TOTAL / psiAll;
        say(String.format(LF, "  (a) 风 curl x%.1f ⇒ tau0 %.4f -> %.4f Pa（真实地球中纬 1e-7~3e-7，本世界已 1.3e-6）",
            need, TAU0, TAU0 * need));
        say(String.format(LF, "  (b) 海盆宽 x%.1f ⇒ 现在 %.0f km -> %.0f km（地球太平洋 10000 km，可调板块尺度）",
            need, avg(widthM, cntArr), avg(widthM, cntArr) * need));
        say(String.format(LF, "  (c) beta /%.1f ⇒ Z_CYCLE x%.1f（改世界契约，且与 D1 极点分离方向相反）", need, need));
        say(String.format(LF, "  (d) H：psi_max 与 H 无关（Sv 换算与 H 无关），只有**速度** prop 1/H"));
        say(String.format(LF, "      ⇒ 把 H 从 4000 换成温跃层 800：速度 x5（这就是 D4 的 x5 因子）"));
        rep.close();
    }

    static double avg(double[] a, int[] cnt) {
        double s = 0; int c = 0;
        for (int i = 0; i < a.length; i++) if (cnt[i] > 0) { s += a[i]; c++; }
        return c == 0 ? 0 : s / c;
    }

    static String wsRow(double[] bm) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < WS.length; i++) sb.append(String.format(LF, "%9.2f", bm[i]));
        return sb.toString();
    }

    static void say(String s) { System.out.println("[P255] " + s); rep.println("[P255] " + s); }
}
