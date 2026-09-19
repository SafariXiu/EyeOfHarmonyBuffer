package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P576 -- 三种强迫的对照：Q 该取哪一种？
 *
 *  §392 查出：Q = H + L_v*E（蒸发型）把最大热源放在海洋上 => 辐合落到北太平洋。
 *  定常波的强迫应是【深对流加热】= 降水释放的潜热 L_v*P。
 *
 *  变体 A: Q = H + L_v*E                  （蒸发型，上一版）
 *  变体 B: Q = 28.356 * P_model           （模型自己的降水 mm/day -> W/m^2）
 *  变体 C: Q = 28.356 * P_real_proxy      （真实季风降水的平滑代理）
 *
 *  判据：C 应当把辐合放到亚洲季风区。若 C 成立 => 【机器 + Q∝P 足够】，剩下只有【种子】问题（§392）。
 */
public class P576 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P576] " + s); System.out.println("[P576] " + s); }

    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double OM = 7.2921e-5;
    static final double LV_W = 28.356;          // mm/day -> W/m^2 （1000*2.45e6/8.64e7）

    static int NX = 64, NY = 33;
    static double Lx = 40_000_000.0, Ly = 5_000_000.0, dy;
    static double eps = 1.0 / (1.5 * 86400.0), cc = 4900.0;
    static double[] fOf, Uy, curP;
    static double kk;

    static double pPrimeAt(int j) {
        if (j <= 0) return curP[1] / dy;
        if (j >= NY - 1) return -curP[NY - 2] / dy;
        return (curP[j + 1] - curP[j - 1]) / (2 * dy);
    }
    static double[] vAt(int j) {
        int jc = Math.min(Math.max(j, 0), NY - 1);
        double pj = (jc <= 0 || jc >= NY - 1) ? 0.0 : curP[jc];
        double pp = pPrimeAt(jc);
        double Akr = eps, Aki = kk * Uy[jc], fr = fOf[jc];
        double Dr = Akr * Akr - Aki * Aki + fr * fr, Di = 2 * Akr * Aki;
        double m2 = Dr * Dr + Di * Di;
        double nr = -Akr * pp, ni = -Aki * pp + kk * fr * pj;
        return new double[]{ (nr * Dr + ni * Di) / m2, (ni * Dr - nr * Di) / m2 };
    }
    static double[] resid(int j, double[] Qk) {
        double pj = curP[j], pp = pPrimeAt(j);
        double Akr = eps, Aki = kk * Uy[j], fr = fOf[j];
        double Dr = Akr * Akr - Aki * Aki + fr * fr, Di = 2 * Akr * Aki;
        double m2 = Dr * Dr + Di * Di;
        double nur = kk * pj * Aki - fr * pp, nui = -kk * pj * Akr;
        double ur = (nur * Dr + nui * Di) / m2, ui = (nui * Dr - nur * Di) / m2;
        double[] vm = vAt(j - 1), vp = vAt(j + 1);
        double vpr = (vp[0] - vm[0]) / (2 * dy), vpi = (vp[1] - vm[1]) / (2 * dy);
        double t1r = Akr * pj, t1i = Aki * pj;
        double t2r = cc * (0.0 - kk * ui + vpr), t2i = cc * (kk * ur + vpi);
        return new double[]{ t1r + t2r + Qk[j], t1i + t2i };
    }

    /** 给定 Q 场，解出 div(V) 场（实部）。 */
    static double[][] solveFor(double[][] Q) {
        double[][] div = new double[NY][NX];
        int n = NY - 2;
        curP = new double[NY];
        // DFT
        double[][][] Qk = new double[NX][NY][2];
        for (int m = 0; m < NX; m++)
            for (int j = 0; j < NY; j++) {
                double sr = 0, si = 0;
                for (int i = 0; i < NX; i++) { double a = -2 * Math.PI * m * i / NX;
                    sr += Q[j][i] * Math.cos(a); si += Q[j][i] * Math.sin(a); }
                Qk[m][j][0] = sr / NX; Qk[m][j][1] = si / NX;
            }
        Qk[0][0][0] = 0;                                  // 去平均（距平强迫）
        for (int j = 1; j < NY - 1; j++) Qk[0][j][0] = 0;
        for (int m = 0; m < NX; m++) {
            kk = 2 * Math.PI * m / Lx;
            double[] Fkr = new double[NY];
            for (int j = 0; j < NY; j++) Fkr[j] = Qk[m][j][0];
            double[][][] A = new double[n][n][2];
            double[][] bb = new double[n][2];
            for (int c = 0; c < n; c++) {
                for (int j = 0; j < NY; j++) curP[j] = 0.0;
                curP[c + 1] = 1.0;
                for (int r = 0; r < n; r++) { double[] rr = resid(r + 1, Fkr); A[r][c][0] = rr[0]; A[r][c][1] = rr[1]; }
            }
            for (int j = 0; j < NY; j++) curP[j] = 0.0;
            for (int r = 0; r < n; r++) { double[] rr = resid(r + 1, Fkr); bb[r][0] = -rr[0]; bb[r][1] = -rr[1]; }
            double[][] x = P574.solveDense(A, bb, n);
            for (int j = 1; j <= NY - 2; j++) curP[j] = x[j - 1][0];
            for (int j = 1; j < NY - 1; j++) {
                double pj = curP[j], pp = pPrimeAt(j);
                double Akr = eps, Aki = kk * Uy[j], fr = fOf[j];
                double Dr = Akr * Akr - Aki * Aki + fr * fr, Di = 2 * Akr * Aki;
                double m2 = Dr * Dr + Di * Di;
                double nur = kk * pj * Aki - fr * pp, nui = -kk * pj * Akr;
                double ui = (nui * Dr - nur * Di) / m2;
                double[] vm = vAt(j - 1), vp = vAt(j + 1);
                double vpr = (vp[0] - vm[0]) / (2 * dy);
                double divr = -kk * ui + vpr;
                for (int i = 0; i < NX; i++) div[j][i] += divr * Math.cos(2 * Math.PI * m * i / NX);
            }
        }
        return div;
    }

    static void report(String tag, double[][] Q, double[][] div, String[] names) {
        say("");
        say("  === " + tag + " ===");
        say("    框             Q均值      div(V)均值        判读");
        int[][] defs = {{70, 120, 15, 35}, {0, 30, 20, 35}, {150, 210, 25, 35}, {300, 350, 25, 35}};
        for (int b = 0; b < defs.length; b++) {
            long cnt = 0; double sq = 0, sd = 0;
            for (int j = 1; j < NY - 1; j++) {
                double latd = Math.toDegrees(WorldContract.latOf((int) Math.round(Ly * j / (NY - 1))));
                if (latd < defs[b][2] || latd > defs[b][3]) continue;
                for (int i = 0; i < NX; i++) {
                    double lon = 360.0 * i / NX;
                    if (lon < defs[b][0] || lon > defs[b][1]) continue;
                    sq += Q[j][i]; sd += div[j][i]; cnt++;
                }
            }
            if (cnt == 0) continue;
            double d = sd / cnt;
            say(String.format(LF, "    %-12s %9.2f %16.4e      %s", names[b], sq / cnt, d, d > 0 ? "【辐合】" : "【辐散】"));
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p576_report.txt"), "UTF-8");
        say("P576: 三种强迫的对照 —— Q 该取蒸发型还是降水型？");
        long sd0 = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        dy = Ly / (NY - 1);
        fOf = new double[NY]; Uy = new double[NY];
        for (int j = 0; j < NY; j++) {
            double lat = WorldContract.latOf((int) Math.round(Ly * j / (NY - 1)));
            fOf[j] = 2 * OM * Math.sin(lat); Uy[j] = 0.0;
        }

        double[][] Qev = new double[NY][NX], Qpm = new double[NY][NX], Qpr = new double[NY][NX];
        for (int j = 0; j < NY; j++) {
            int z = (int) Math.round(Ly * j / (NY - 1));
            double lat = WorldContract.latOf(z), latd = Math.toDegrees(lat);
            for (int i = 0; i < NX; i++) {
                int x = (int) Math.round(CIRC * i / NX);
                double lond = 360.0 * i / NX;
                double k = Atmosphere.kappaAt(x, z, sd0, cell);
                double[] u0 = Atmosphere.windAt(x, z, sd0, cell, th, GRAD);
                double ta = Atmosphere.surfaceTemp(x, z, sd0, cell, th);
                double[] dg = new double[8];
                P546.decompose(x, z, sd0, cell, th, dg);
                double qa = dg[1];
                double beta = (lond >= 70 && lond <= 120 && latd >= 15 && latd <= 35) ? 1.0
                            : ((lond >= 0 && lond <= 30 && latd >= 20 && latd <= 35) ? 0.05
                            : 1.0 - 0.95 * Atmosphere.clamp01(k));
                double chv = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
                double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                double absS = (1.0 - alb) * Radiation.insolation(lat, dec);
                double ts = Radiation.skinTempLand(absS, ta, qa, chv, beta);
                double hh = chv * Radiation.CP * (ts - ta);
                double le = beta * chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
                Qev[j][i] = hh + le;
                Qpm[j][i] = LV_W * PrecipField.mmPerDay(x, z, sd0, cell, th, GRAD);
                // 真实季风降水的平滑代理（mm/day）：亚洲主峰 + 西北太平洋 + 干底
                double pReal = 9.0 * Math.exp(-Math.pow((lond - 95) / 28.0, 2)) * Math.exp(-Math.pow((latd - 20) / 9.0, 2))
                             + 3.0 * Math.exp(-Math.pow((lond - 140) / 20.0, 2)) * Math.exp(-Math.pow((latd - 35) / 12.0, 2))
                             + 0.25;
                Qpr[j][i] = LV_W * pReal;
            }
        }
        say(String.format(LF, "  Q 场统计:  蒸发型[%.1f, %.1f]   模型降水型[%.1f, %.1f]   真实降水型[%.1f, %.1f]",
            min2(Qev), max2(Qev), min2(Qpm), max2(Qpm), min2(Qpr), max2(Qpr)));
        String[] nm = {"亚洲季风区", "撒哈拉", "北太平洋", "北大西洋"};
        report("A: Q = H + L_v*E（蒸发型）", Qev, solveFor(Qev), nm);
        report("B: Q = 28.356 * P_model（模型降水）", Qpm, solveFor(Qpm), nm);
        report("C: Q = 28.356 * P_real（真实季风降水代理）", Qpr, solveFor(Qpr), nm);
        say("");
        say("  判据：A -> 辐合落海洋；B -> 若辐合落撒哈拉则【放大反转】；C -> 辐合落亚洲则【机器+Q∝P 足够】");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
    static double min2(double[][] a) { double m = 1e30; for (double[] r : a) for (double v : r) m = Math.min(m, v); return m; }
    static double max2(double[][] a) { double m = -1e30; for (double[] r : a) for (double v : r) m = Math.max(m, v); return m; }
}
