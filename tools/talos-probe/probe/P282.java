package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P282：B3（季风反转）的**仪器校准**。
 *
 * <p>记录里的一个不自洽：A3（东边界）用 GRAD=500 km 测，B3（反相比例）却是 P260 的 GRAD=10 km。
 * 同一个 p' 场，差分步长不同 = 不同的尺子。本探针把两者放到同一把尺子上，
 * 并做分项归因（热力距平梯度 vs cell 距平梯度 vs U_zm）。
 */
public class P282 {

    static final int SEED = 1022228679;
    static final int XSTEP = 20_000, ZSTEP = 200_000, NX = 551;
    static final int MD = WorldContract.MAX_D;
    static final int[][] BAND = new int[3][2];
    static final String[] BNAME = {"0~10度(赤道)", "20~40度(副热带)", "50~70度(中高纬)"};
    static final int[] GRADS = {10_000, 100_000, 250_000, 500_000, 1_000_000};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p282_report.txt"), "UTF-8");
        BAND[0][0] = 0;                    BAND[0][1] = (MD / 9) / ZSTEP;
        BAND[1][0] = (2 * MD / 9) / ZSTEP; BAND[1][1] = (4 * MD / 9) / ZSTEP;
        BAND[2][0] = (5 * MD / 9) / ZSTEP; BAND[2][1] = (7 * MD / 9) / ZSTEP;
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("P282：B3（季风反转）的仪器校准 —— 反相比例对差分步长 GRAD 的依赖");
        say(String.format(LF, "  PLATE_CELL=%d  网格 dx=%d km dz=%d km  x=0..%d km  行号 带1 %d~%d 带2 %d~%d 带3 %d~%d",
            cell, XSTEP / 1000, ZSTEP / 1000, (NX - 1) * XSTEP / 1000,
            BAND[0][0], BAND[0][1], BAND[1][0], BAND[1][1], BAND[2][0], BAND[2][1]));
        say("  P260 用 GRAD=10 km；A3 的生产测量用 GRAD=500 km。同一判据、两把尺子。");
        say("");

        int nP = 0;
        for (int b = 0; b < 3; b++) nP += (BAND[b][1] - BAND[b][0] + 1) * NX;
        int[] px_ = new int[nP], pz_ = new int[nP], pb_ = new int[nP];
        boolean[] land = new boolean[nP];
        double[] kap = new double[nP];
        int k = 0;
        for (int b = 0; b < 3; b++) {
            for (int r = BAND[b][0]; r <= BAND[b][1]; r++) {
                for (int c = 0; c < NX; c++) {
                    int x = c * XSTEP, z = r * ZSTEP;
                    px_[k] = x; pz_[k] = z; pb_[k] = b;
                    kap[k] = Atmosphere.kappaAt(x, z, SEED, cell);
                    land[k] = PlateField.isLandWithCell(x, z, SEED, cell);
                    k++;
                }
            }
        }
        int landTot = 0;
        for (int i = 0; i < nP; i++) if (land[i]) landTot++;
        say(String.format(LF, "  采样点 %d（陆地 %d = %.1f%%），kappa/cell 场已建好", nP, landTot, landTot * 100.0 / nP));
        say("");

        double[][] keepUs = null, keepVs = null, keepUw = null, keepVw = null;

        for (int gi = 0; gi < GRADS.length; gi++) {
            int G = GRADS[gi];
            long t0 = System.nanoTime();
            double[] us = new double[nP], vs = new double[nP], uw = new double[nP], vw = new double[nP];
            for (int i = 0; i < nP; i++) {
                double[] a = Atmosphere.windAt(px_[i], pz_[i], SEED, cell, thS, G);
                double[] b2 = Atmosphere.windAt(px_[i], pz_[i], SEED, cell, thW, G);
                us[i] = a[0]; vs[i] = a[1]; uw[i] = b2[0]; vw[i] = b2[1];
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            if (G == 500_000) { keepUs = new double[][]{us}; keepVs = new double[][]{vs}; keepUw = new double[][]{uw}; keepVw = new double[][]{vw}; }

            say(String.format(LF, "A%d. GRAD = %d km   （%d 点 x 2 季，%.0f ms）", gi + 1, G / 1000, nP, ms));
            say(String.format(LF, "    %-16s %6s %8s %8s %8s %10s %8s %7s %6s",
                "纬度带", "点数", "反相%", "u反相%", "v反相%", "平均|dU|", "撞上限%", "k均值", "海陆"));
            for (int b = 0; b < 3; b++) {
                for (int wl = 0; wl < 2; wl++) {
                    boolean wantLand = wl == 1;
                    int n = 0, rev = 0, revU = 0, revV = 0, cap = 0;
                    double dsum = 0, ksum = 0;
                    for (int i = 0; i < nP; i++) {
                        if (pb_[i] != b || land[i] != wantLand) continue;
                        double dot = us[i] * uw[i] + vs[i] * vw[i];
                        if (dot < 0) rev++;
                        if (us[i] * uw[i] < 0) revU++;
                        if (vs[i] * vw[i] < 0) revV++;
                        dsum += Math.hypot(us[i] - uw[i], vs[i] - vw[i]);
                        if (Math.hypot(us[i], vs[i]) > Atmosphere.U_MAX - 1e-9
                         || Math.hypot(uw[i], vw[i]) > Atmosphere.U_MAX - 1e-9) cap++;
                        ksum += kap[i];
                        n++;
                    }
                    if (n == 0) { say(String.format(LF, "    %-16s %6d %8s", BNAME[b], 0, "-")); continue; }
                    say(String.format(LF, "    %-16s %6d %7.1f%% %7.1f%% %7.1f%% %10.2f %7.1f%% %7.3f %6s",
                        BNAME[b], n, rev * 100.0 / n, revU * 100.0 / n, revV * 100.0 / n,
                        dsum / n, cap * 100.0 / n, ksum / n, wantLand ? "陆地" : "海洋"));
                }
            }
            say("");
        }

        // ---------------- B. 分项归因（GRAD = 500 km） ----------------
        say("B. 分项归因（GRAD=500 km）：把 p\u0027 拆成 热力距平 与 cell 距平，各自单独驱动风");
        say("   p_therm = -K_P*CHI*seasonalAnomaly ；p_cell = cellPressure ；两者之和 == pressureAnomaly");
        int G = 500_000;
        double[] usF = keepUs[0], vsF = keepVs[0], uwF = keepUw[0], vwF = keepVw[0];
        double[] usT = new double[nP], vsT = new double[nP], uwT = new double[nP], vwT = new double[nP];
        double[] usC = new double[nP], vsC = new double[nP], uwC = new double[nP], vwC = new double[nP];
        double[] usG = new double[nP], vsG = new double[nP], uwG = new double[nP], vwG = new double[nP];
        for (int i = 0; i < nP; i++) {
            int x = px_[i], z = pz_[i];
            double[] pt = diffTherm(x, z, SEED, cell, thS, G);
            double[] pw = diffTherm(x, z, SEED, cell, thW, G);
            double[] ct = diffCell(x, z, SEED, cell, thS, G);
            double[] cw = diffCell(x, z, SEED, cell, thW, G);
            double lat = WorldContract.latOf(z);
            double kk = kap[i];
            double[] a = Atmosphere.wind(pt[0], pt[1], kk, lat, thS);
            double[] b2 = Atmosphere.wind(pw[0], pw[1], kk, lat, thW);
            usT[i] = a[0]; vsT[i] = a[1]; uwT[i] = b2[0]; vwT[i] = b2[1];
            double[] c = Atmosphere.wind(ct[0], ct[1], kk, lat, thS);
            double[] d = Atmosphere.wind(cw[0], cw[1], kk, lat, thW);
            usC[i] = c[0]; vsC[i] = c[1]; uwC[i] = d[0]; vwC[i] = d[1];
            double sz = zmShift(thS), sw = zmShift(thW);
            double ld = Math.toDegrees(lat);
            usG[i] = a[0] - ZonalTables.uZm(ld - sz); vsG[i] = a[1];
            uwG[i] = b2[0] - ZonalTables.uZm(ld - sw); vwG[i] = b2[1];
        }
        say(String.format(LF, "    %-16s %6s %14s %14s %14s %14s", "纬度带", "海陆", "全量(含U_zm)", "只热力梯度", "只cell梯度", "热力无U_zm"));
        for (int b = 0; b < 3; b++) {
            for (int wl = 0; wl < 2; wl++) {
                boolean wantLand = wl == 1;
                int n = 0, r1 = 0, r2 = 0, r3 = 0, r4 = 0;
                for (int i = 0; i < nP; i++) {
                    if (pb_[i] != b || land[i] != wantLand) continue;
                    if (usF[i] * uwF[i] + vsF[i] * vwF[i] < 0) r1++;
                    if (usT[i] * uwT[i] + vsT[i] * vwT[i] < 0) r2++;
                    if (usC[i] * uwC[i] + vsC[i] * vwC[i] < 0) r3++;
                    if (usG[i] * uwG[i] + vsG[i] * vwG[i] < 0) r4++;
                    n++;
                }
                if (n == 0) continue;
                say(String.format(LF, "    %-16s %6s %13.1f%% %13.1f%% %13.1f%% %13.1f%%",
                    BNAME[b], wantLand ? "陆地" : "海洋", r1 * 100.0 / n, r2 * 100.0 / n, r3 * 100.0 / n, r4 * 100.0 / n));
            }
        }
        say("");

        // ---------------- C. 20~40 度陆地的风矢量结构 ----------------
        say("C. 20~40 度陆地的平均风矢量（GRAD=500 km，m/s）与 p\u0027 反号比例");
        double sUs = 0, sVs = 0, sUw = 0, sVw = 0, sTs = 0, sTw = 0, sCs = 0, sCw = 0;
        int n = 0, pRev = 0;
        for (int i = 0; i < nP; i++) {
            if (pb_[i] != 1 || !land[i]) continue;
            sUs += usF[i]; sVs += vsF[i]; sUw += uwF[i]; sVw += vwF[i];
            sTs += usT[i]; sTw += uwT[i]; sCs += usC[i]; sCw += uwC[i];
            double pS = Atmosphere.pressureAnomaly(px_[i], pz_[i], SEED, cell, thS);
            double pW = Atmosphere.pressureAnomaly(px_[i], pz_[i], SEED, cell, thW);
            if (pS * pW < 0) pRev++;
            n++;
        }
        if (n > 0) {
            say(String.format(LF, "   全量   夏 (%+.2f, %+.2f)   冬 (%+.2f, %+.2f)   n=%d", sUs / n, sVs / n, sUw / n, sVw / n, n));
            say(String.format(LF, "   只热力 夏 (%+.2f, ---)   冬 (%+.2f, ---)   p\u0027 夏冬反号 %.1f%%", sTs / n, sTw / n, pRev * 100.0 / n));
            say(String.format(LF, "   只cell 夏 (%+.2f, ---)   冬 (%+.2f, ---)", sCs / n, sCw / n));
        }
        say("");
        say("D. 成本：见各 GRAD 的毫秒数；单点单季 windAt 约 0.47 ms（P260 F 段实测）");
        rep.close();
    }

    static double zmShift(double theta) { return Math.toDegrees(Atmosphere.DELTA_PHI0) * Math.cos(theta); }

    static double pTherm(int x, int z, long seed, int cell, double theta) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, seed, cell);
        return -Atmosphere.K_P * Atmosphere.CHI * Atmosphere.seasonalAnomaly(lat, k, theta);
    }

    static double pCell(int x, int z, long seed, int cell, double theta) {
        double lat = WorldContract.latOf(z);
        double k = Atmosphere.kappaAt(x, z, seed, cell);
        return Atmosphere.cellPressure(lat, k, theta);
    }

    static double[] diffTherm(int x, int z, long seed, int cell, double theta, int g) {
        double dx = (pTherm(x + g, z, seed, cell, theta) - pTherm(x - g, z, seed, cell, theta)) / (2.0 * g);
        double dz = (pTherm(x, z + g, seed, cell, theta) - pTherm(x, z - g, seed, cell, theta)) / (2.0 * g);
        return new double[]{dx, dz};
    }

    static double[] diffCell(int x, int z, long seed, int cell, double theta, int g) {
        double dx = (pCell(x + g, z, seed, cell, theta) - pCell(x - g, z, seed, cell, theta)) / (2.0 * g);
        double dz = (pCell(x, z + g, seed, cell, theta) - pCell(x, z - g, seed, cell, theta)) / (2.0 * g);
        return new double[]{dx, dz};
    }

    static void say(String s) { System.out.println("[P282] " + s); rep.println("[P282] " + s); }
}
