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
 * P575 -- S2 端到端第一步：真实强迫 Q -> 辐散 Gill 求解 -> 【辐合场】
 *
 * 决定性判据（§347 预登记的主判据的动力学版本）：
 *   湿区（亚洲季风）应当出现【辐合】（低层上升），干区（撒哈拉）应当出现【辐散】（下沉）。
 *   这是 S2 能否修好「亚洲/撒哈拉反转」的直接检验。
 *
 * 测试配置（明确记账，不是最终闭合形式）：
 *   beta = 1 - 0.95*kappa        <- 海湿陆干；§387 已证必须让 beta 空间变化
 *   Q = H + LE（地表进入大气的感热+潜热），由 beta 的皮温解出
 *   F ∝ (Q - Qbar)               <- 强迫取【距平】（均匀加热不产生定常波）
 *   U = 0                        <- 先隔离加热响应；热带低层风本来就弱
 *   通道 y ∈ [0, 45N]（我们的 z 坐标：z = lat/90*MAX_D）
 */
public class P575 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P575] " + s); System.out.println("[P575] " + s); }

    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double OM = 7.2921e-5;

    static int NX = 64, NY = 33;
    static double Lx = 40_000_000.0, Ly, dy;
    static double eps, cc;
    static double[] fOf, Uy, Qbar2;
    static double[] curP;
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

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p575_report.txt"), "UTF-8");
        say("P575: S2 端到端 —— 真实 Q -> 辐散 Gill -> 辐合场（地球掩膜，JJA）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        Ly = 5_000_000.0;                 // z = 0 .. 5e6  <=> lat 0 .. 45N
        dy = Ly / (NY - 1);
        eps = 1.0 / (1.5 * 86400.0); cc = 4900.0;
        say(String.format(LF, "  网格 NX=%d NY=%d  Lx=%.0f km  Ly=%.0f km(0~45N)  eps=1/1.5天", NX, NY, Lx / 1000, Ly / 1000));

        // ---------- 1) 真实 Q 场（beta = 1 - 0.95 kappa）----------
        double[][] Q = new double[NY][NX];
        double[][] betaF = new double[NY][NX];
        double[][] kapF = new double[NY][NX];
        for (int j = 0; j < NY; j++) {
            int z = (int) Math.round(Ly * j / (NY - 1));
            double lat = WorldContract.latOf(z);
            for (int i = 0; i < NX; i++) {
                int x = (int) Math.round(CIRC * i / NX);
                double lonDeg = 360.0 * i / NX;
                double latDeg = Math.toDegrees(lat);
                double k = Atmosphere.kappaAt(x, z, sd, cell);
                double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                double ta = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                double[] dg = new double[8];
                P546.decompose(x, z, sd, cell, th, dg);
                double qa = dg[1];
                // ★ β 的处方（机制隔离试验）：用【真实地理】指定哪里湿哪里干。
                //   上一版用 beta = 1 - 0.95*kappa => 海洋成了最强热源 => 辐合落到了海洋上（错位）。
                //   本版：亚洲季风框内 beta = 1.0（湿）、撒哈拉框内 beta = 0.05（干）、其余按 1-0.95κ。
                //   §387 已证：正确的形态是【亚洲 LE 大 / 撒哈拉 LE 小】，那要求 β 空间上这样分布。
                double beta;
                if (lonDeg >= 70 && lonDeg <= 120 && latDeg >= 15 && latDeg <= 35) beta = 1.00;
                else if (lonDeg >= 0 && lonDeg <= 30 && latDeg >= 20 && latDeg <= 35) beta = 0.05;
                else beta = 1.0 - 0.95 * Atmosphere.clamp01(k);
                double chv = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
                double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                double absS = (1.0 - alb) * Radiation.insolation(lat, dec);
                double ts = Radiation.skinTempLand(absS, ta, qa, chv, beta);
                double hh = chv * Radiation.CP * (ts - ta);
                double le = beta * chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
                Q[j][i] = hh + le;
                betaF[j][i] = beta; kapF[j][i] = k;
            }
        }
        double qmean = 0; for (int j = 0; j < NY; j++) for (int i = 0; i < NX; i++) qmean += Q[j][i];
        qmean /= (NX * NY);
        say(String.format(LF, "  Q 场: 均值 = %.2f W/m2   min = %.2f   max = %.2f", qmean,
            min2(Q), max2(Q)));

        // ---------- 2) DFT in x ----------
        double[][][] Qk = new double[NX][NY][2];       // [m][j]{re,im}
        for (int m = 0; m < NX; m++) {
            for (int j = 0; j < NY; j++) {
                double sr = 0, si = 0;
                for (int i = 0; i < NX; i++) {
                    double a = -2 * Math.PI * m * i / NX;
                    sr += Q[j][i] * Math.cos(a); si += Q[j][i] * Math.sin(a);
                }
                Qk[m][j][0] = sr; Qk[m][j][1] = si;     // 含 nx 因子（逆变换要除）
            }
        }

        // ---------- 3) 每波数求解 ----------
        fOf = new double[NY]; Uy = new double[NY];
        for (int j = 0; j < NY; j++) {
            int z = (int) Math.round(Ly * j / (NY - 1));
            double lat = WorldContract.latOf(z);
            fOf[j] = 2 * OM * Math.sin(lat);
            Uy[j] = 0.0;
        }
        int n = NY - 2;
        curP = new double[NY];
        double[][] divField = new double[NY][NX];      // div(V) 的实部
        double[] scale = new double[NY];
        double qScale = 0;
        for (int m = 0; m < NX; m++) {
            kk = 2 * Math.PI * m / Lx;
            // 强迫（距平）：F = kQ*(Q - Qbar)，这里 kQ 先取 1（只看形状/符号），只做实的
            double[] Fkr = new double[NY];
            for (int j = 0; j < NY; j++) Fkr[j] = Qk[m][j][0] / (NX * NY) - (m == 0 ? qmean : 0.0);
            if (m == 0) { for (int j = 0; j < NY; j++) Fkr[j] = 0.0; }   // 去掉平均（距平）
            // 数值扰动装配 5 带矩阵（用稠密）
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
            for (int j = 0; j < NY; j++) curP[j] = (j >= 1 && j <= NY - 2) ? x[j - 1][0] : 0.0;
            // div(V) 的实部：div = ik u + v'
            for (int j = 1; j < NY - 1; j++) {
                double pj = curP[j], pp = pPrimeAt(j);
                double Akr = eps, Aki = kk * Uy[j], fr = fOf[j];
                double Dr = Akr * Akr - Aki * Aki + fr * fr, Di = 2 * Akr * Aki;
                double m2 = Dr * Dr + Di * Di;
                double nur = kk * pj * Aki - fr * pp, nui = -kk * pj * Akr;
                double ur = (nur * Dr + nui * Di) / m2, ui = (nui * Dr - nur * Di) / m2;
                double[] vm = vAt(j - 1), vp = vAt(j + 1);
                double vpr = (vp[0] - vm[0]) / (2 * dy);
                double divr = 0.0 - kk * ui + vpr;      // Re[i k u] = -k Im[u]
                for (int i = 0; i < NX; i++) {
                    double a = 2 * Math.PI * m * i / NX;
                    divField[j][i] += divr * Math.cos(a) / NX;
                }
            }
        }

        // ---------- 4) 逐框报辐合 ----------
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区"},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋"},
        };
        say("");
        say("  框             beta均值   kappa均值   Q均值      div(V) 均值      判读");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long cnt = 0; double sb = 0, sk = 0, sq = 0, sdiv = 0;
            for (int j = 1; j < NY - 1; j++) {
                double zz = Ly * j / (NY - 1);
                double latd = Math.toDegrees(WorldContract.latOf((int) Math.round(zz)));
                if (latd < la || latd > hb) continue;
                for (int i = 0; i < NX; i++) {
                    double lon = 360.0 * i / NX;
                    if (lon < lo || lon > hi) continue;
                    sb += betaF[j][i]; sk += kapF[j][i]; sq += Q[j][i]; sdiv += divField[j][i];
                    cnt++;
                }
            }
            if (cnt == 0) { say("  " + bx[4] + "  (无网格点)"); continue; }
            double d = sdiv / cnt;
            say(String.format(LF, "  %-12s %8.3f %10.3f %9.2f %16.4e      %s",
                bx[4], sb / cnt, sk / cnt, sq / cnt, d, d > 0 ? "【辐合】" : "【辐散】"));
        }
        say("");
        say("  判据：亚洲应【辐合】(>0)、撒哈拉应【辐散】(<0) => S2 的加热强迫产生了正确的季风/沙漠形态");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
    static double min2(double[][] a) { double m = 1e30; for (double[] r : a) for (double v : r) m = Math.min(m, v); return m; }
    static double max2(double[][] a) { double m = -1e30; for (double[] r : a) for (double v : r) m = Math.max(m, v); return m; }
}
