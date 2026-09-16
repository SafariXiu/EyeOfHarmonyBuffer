package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P283：B3 的**反事实矩阵**。
 *
 * <p>P282 的结论：在生产的 GRAD=500 km 下，20~40 度陆地反相只有 46.8%（不是记录里的 75.6%，
 * 那是 GRAD=10 km 的产物），但**热力距平的气压梯度单独驱动时反相 100%**。
 * 所以问题不在"季风压力信号"，而在"它被谁盖住"。
 *
 * <p>本探针把可能的盖住源逐个关掉：
 * <pre>
 *   V_AMP_SIN   : A(phi) = Apk*sin(phi)（保持 45 度锚点），而不是 sin^2(phi)
 *   V_CELL_REF0 : cell 项的参考大陆度取 0（洋面上没有净 cell 项），而不是 KAPPA_MEAN
 *   V_NO_CELL   : 整个 cell 项关掉
 *   V_NO_UZM    : 外生纬向平均风 U_zm 关掉
 * </pre>
 */
public class P283 {

    static final int SEED = 1022228679;
    static final int XSTEP = 20_000, ZSTEP = 200_000, NX = 551;
    static final int MD = WorldContract.MAX_D;
    static final int GRAD = 500_000;
    static final int[][] BAND = new int[3][2];
    static final String[] BNAME = {"0~10度(赤道)", "20~40度(副热带)", "50~70度(中高纬)"};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static final int V_AMP_SIN = 1, V_CELL_REF0 = 2, V_NO_CELL = 4, V_NO_UZM = 8;
    static final int[] VARS = {0, V_NO_UZM, V_NO_CELL, V_NO_CELL | V_NO_UZM, V_CELL_REF0,
                               V_AMP_SIN, V_AMP_SIN | V_CELL_REF0, V_AMP_SIN | V_CELL_REF0 | V_NO_UZM};
    static final String[] VNAME = {"基线", "无U_zm", "无cell", "无cell无U_zm", "cell参考0",
                                   "A=sinφ", "A=sinφ+cell参考0", "A=sinφ+cell0+无U_zm"};

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p283_report.txt"), "UTF-8");
        BAND[0][0] = 0;                    BAND[0][1] = (MD / 9) / ZSTEP;
        BAND[1][0] = (2 * MD / 9) / ZSTEP; BAND[1][1] = (4 * MD / 9) / ZSTEP;
        BAND[2][0] = (5 * MD / 9) / ZSTEP; BAND[2][1] = (7 * MD / 9) / ZSTEP;
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        if (Atmosphere.PLATEAU_AMP != 1.0) throw new IllegalStateException("PLATEAU_AMP != 1，探针的热力式不再等价");

        say("P283：B3 反事实矩阵（GRAD=500 km，与 A3 生产测量同一把尺子）");
        say(String.format(LF, "  网格 dx=%d km dz=%d km x=0..%d km  格点 %d", XSTEP/1000, ZSTEP/1000, (NX-1)*XSTEP/1000,
            (BAND[0][1]-BAND[0][0]+1 + BAND[1][1]-BAND[1][0]+1 + BAND[2][1]-BAND[2][0]+1) * NX));
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

        for (int vi = 0; vi < VARS.length; vi++) {
            int var = VARS[vi];
            long t0 = System.nanoTime();
            double[] us = new double[nP], vs = new double[nP], uw = new double[nP], vw = new double[nP];
            double[] pS = new double[nP], pW = new double[nP];
            for (int i = 0; i < nP; i++) {
                int x = px_[i], z = pz_[i];
                double lat = WorldContract.latOf(z);
                double kk = kap[i];
                double[] a = grad(x, z, SEED, cell, thS, var);
                double[] b2 = grad(x, z, SEED, cell, thW, var);
                pS[i] = pAnom(x, z, SEED, cell, thS, var);
                pW[i] = pAnom(x, z, SEED, cell, thW, var);
                double[] w1 = Atmosphere.wind(a[0], a[1], kk, lat, thS);
                double[] w2 = Atmosphere.wind(b2[0], b2[1], kk, lat, thW);
                if ((var & V_NO_UZM) != 0) {
                    w1[0] -= ZonalTables.uZm(Math.toDegrees(lat) - zmShift(thS));
                    w2[0] -= ZonalTables.uZm(Math.toDegrees(lat) - zmShift(thW));
                }
                us[i] = w1[0]; vs[i] = w1[1]; uw[i] = w2[0]; vw[i] = w2[1];
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            say(String.format(LF, "V%d. %-22s  （%d 点 x 2 季，%.0f ms）", vi, VNAME[vi], nP, ms));
            say(String.format(LF, "    %-16s %6s %8s %8s %8s %10s %9s %9s",
                "纬度带", "点数", "反相%", "u反相%", "v反相%", "平均|dU|", "Δu>0比例", "p反号%"));
            for (int b = 0; b < 3; b++) {
                for (int wl = 0; wl < 2; wl++) {
                    boolean wantLand = wl == 1;
                    int n = 0, rev = 0, ru = 0, rv = 0, duPos = 0, pRev = 0;
                    double dsum = 0;
                    for (int i = 0; i < nP; i++) {
                        if (pb_[i] != b || land[i] != wantLand) continue;
                        if (us[i]*uw[i] + vs[i]*vw[i] < 0) rev++;
                        if (us[i]*uw[i] < 0) ru++;
                        if (vs[i]*vw[i] < 0) rv++;
                        if (us[i] - uw[i] > 0) duPos++;
                        if (pS[i]*pW[i] < 0) pRev++;
                        dsum += Math.hypot(us[i]-uw[i], vs[i]-vw[i]);
                        n++;
                    }
                    if (n == 0) continue;
                    say(String.format(LF, "    %-16s %6d %7.1f%% %7.1f%% %7.1f%% %10.2f %8.1f%% %8.1f%%",
                        BNAME[b], n, rev*100.0/n, ru*100.0/n, rv*100.0/n, dsum/n, duPos*100.0/n, pRev*100.0/n));
                }
            }
            say("");
        }

        // ---- 失败点的结构（基线） ----
        say("F. 20~40 度陆地按纬度细分（基线）：反相失败的来源");
        say(String.format(LF, "    %-8s %6s %8s %9s %9s %9s %9s %9s %9s",
            "纬度", "点数", "反相%", "u夏", "u冬", "v夏", "v冬", "u_zm夏", "u_zm冬"));
        int var = 0;
        for (int half = 0; half < 4; half++) {
            double lo = 20 + half * 5, hi = lo + 5;
            int n = 0, rev = 0;
            double suS = 0, suW = 0, svS = 0, svW = 0, szS = 0, szW = 0;
            for (int i = 0; i < nP; i++) {
                if (pb_[i] != 1 || !land[i]) continue;
                double ld = Math.toDegrees(WorldContract.latOf(pz_[i]));
                if (ld < lo || ld >= hi) continue;
                int x = px_[i], z = pz_[i];
                double lat = WorldContract.latOf(z);
                double[] a = grad(x, z, SEED, cell, thS, var);
                double[] b2 = grad(x, z, SEED, cell, thW, var);
                double[] w1 = Atmosphere.wind(a[0], a[1], kap[i], lat, thS);
                double[] w2 = Atmosphere.wind(b2[0], b2[1], kap[i], lat, thW);
                if (w1[0]*w2[0] + w1[1]*w2[1] < 0) rev++;
                suS += w1[0]; suW += w2[0]; svS += w1[1]; svW += w2[1];
                szS += ZonalTables.uZm(ld - zmShift(thS));
                szW += ZonalTables.uZm(ld - zmShift(thW));
                n++;
            }
            if (n == 0) continue;
            say(String.format(LF, "    %-8s %6d %7.1f%% %9.2f %9.2f %9.2f %9.2f %9.2f %9.2f",
                (int) lo + "~" + (int) hi, n, rev*100.0/n, suS/n, suW/n, svS/n, svW/n, szS/n, szW/n));
        }
        rep.close();
    }

    static double zmShift(double theta) { return Math.toDegrees(Atmosphere.DELTA_PHI0) * Math.cos(theta); }

    static double pAnom(int x, int z, long seed, int cell, double theta, int var) {
        double lat = WorldContract.latOf(z);
        double kv = Atmosphere.kappaAt(x, z, seed, cell);
        double k = Atmosphere.clamp01(kv);
        double s = Math.sin(lat);
        double psiDays = Atmosphere.PSI_SEA_DAYS + (Atmosphere.PSI_LAND_DAYS - Atmosphere.PSI_SEA_DAYS) * k;
        double psi = 2.0 * Math.PI * psiDays / WorldContract.DAYS_PER_YEAR;
        double hemi = lat >= 0.0 ? 0.0 : Math.PI;
        double latDeg = Math.toDegrees(lat);
        double aSea0 = ZonalTables.aSea(latDeg);
        double amp = aSea0 + (ZonalTables.aLand(latDeg) - aSea0) * k;
        double tAnom;
        if ((var & V_AMP_SIN) != 0) {
            // 历史对照：旧的 A = 10/26 * sin^2(phi)（已被 ERA5 否证，见 §80）
            tAnom = (10.0 + 16.0 * k) * s * s * Math.cos(theta - psi - hemi);
        } else {
            tAnom = amp * Math.cos(theta - psi - hemi);
        }
        double tZm = Atmosphere.tZonalMean(lat);
        double elev = PlateField.elevationWithCell(x, z, seed, cell);
        double tSfc = tZm + tAnom - Atmosphere.GAMMA * Math.max(0.0, elev) * k;
        double h = Math.max(0.0, elev) * Atmosphere.PLATEAU_AMP * k;
        double pTherm = -Atmosphere.K_P * Atmosphere.CHI * (tSfc + Atmosphere.GAMMA * h - tZm);
        if ((var & V_NO_CELL) != 0) return pTherm;
        double shifted = Math.toDegrees(lat - Atmosphere.CELL_MIGRATION * Math.cos(theta - Atmosphere.CELL_LAG));
        double ref = ((var & V_CELL_REF0) != 0) ? 0.0 : Atmosphere.KAPPA_MEAN;
        return pTherm + Atmosphere.CELL_GAIN * ZonalTables.carrier(shifted) * (ref - k);
    }

    static double[] grad(int x, int z, long seed, int cell, double theta, int var) {
        double dx = (pAnom(x + GRAD, z, seed, cell, theta, var) - pAnom(x - GRAD, z, seed, cell, theta, var)) / (2.0 * GRAD);
        double dz = (pAnom(x, z + GRAD, seed, cell, theta, var) - pAnom(x, z - GRAD, seed, cell, theta, var)) / (2.0 * GRAD);
        return new double[]{dx, dz};
    }

    static void say(String s) { System.out.println("[P283] " + s); rep.println("[P283] " + s); }
}
