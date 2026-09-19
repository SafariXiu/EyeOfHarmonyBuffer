package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P636 -- §427 续：gate 的过渡尺度 A/B + MFC 幅度对文献锚。
// 文献核实（子代理 96781b75，Held 讲义 / Lu et al. 2022 / Caballero & Hanley 2012 / Randel & Held 1991）：
//   ① gate 必须在导数内（守恒），但 op(gate)/oy 是【无物理意义】的伪项，它随过渡陡度任意变化；
//      过渡尺度必须 >= 混合长（Rhines/拉格朗日 ~700-1000 km ~ 6-9 度）。
//   ② 任何纬向平均 MFC 的绝对值必须 << 5 mm/day（全球平均降水 2.65 mm/day，Grotjahn）。
//   ③ K_Q 中纬度年均 2~8e6 m^2/s（Lu et al. 2022 的 cos(phi)K 峰 1.85e6 @35N；Caballero v*L ~6.3e6）。
//   ④ D = sigma*L_d^2/0.31 = 3.23 sigma L_d^2（Green 1970 / Stone 1972）==> EDDY_MIX 应为 3.23，现为 3.00。
public class P636 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P636] " + s); System.out.println("[P636] " + s); }
    static final double R = WorldContract.R_EFF, MMD = 86400.0;   // kg/(m^2 s) -> mm/day
    static final double D5 = Math.toRadians(5.0);
    static final double H5 = D5 * R;
    static final int N = 19;

    static double corr(double[] a, double[] b, int k0, int k1) {
        int n = k1 - k0 + 1; double sa = 0, sb = 0, sab = 0, sa2 = 0, sb2 = 0;
        for (int i = k0; i <= k1; i++) { sa += a[i]; sb += b[i]; sab += a[i] * b[i]; sa2 += a[i] * a[i]; sb2 += b[i] * b[i]; }
        double ca = sab / n - (sa / n) * (sb / n);
        double va = sa2 / n - (sa / n) * (sa / n), vb = sb2 / n - (sb / n) * (sb / n);
        return (va > 0 && vb > 0) ? ca / Math.sqrt(va * vb) : 0;
    }

    static double kOf(double lat, double th) {
        double ld = PrecipField.deformRadius(lat, th), sg = PrecipField.eadyGrowth(lat, th);
        return (PrecipField.EDDY_CLOSURE == 0) ? PrecipField.EDDY_MIX * sg * ld * ld
             : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p636_report.txt"), "UTF-8");
        double U0 = PrecipField.U0_STORM; int CL = PrecipField.EDDY_CLOSURE;
        say("P636: gate 过渡尺度 A/B（U0_STORM）+ MFC 幅度对文献锚");
        say(String.format(LF, "  生产配置：U0_STORM=%.1f  EDDY_CLOSURE=%d  EDDY_DPHI_DEG=%.1f", U0, CL, PrecipField.EDDY_DPHI_DEG));
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};

        say("");
        say("=== 一、gate 的纬度过渡宽度（gate 从 0.1 升到 0.9 跨多少度）===");
        for (double u0 : new double[]{5.0, 8.0, 12.0, 20.0}) {
            PrecipField.U0_STORM = u0;
            StringBuilder sb = new StringBuilder();
            for (int q = 0; q < 2; q++) {
                double l1 = Double.NaN, l9 = Double.NaN;
                for (double latd = 0; latd <= 90.0; latd += 0.25) {
                    double g = PrecipField.stormGate(Math.toRadians(latd), ths[q]);
                    if (Double.isNaN(l1) && g >= 0.1) l1 = latd;
                    if (Double.isNaN(l9) && g >= 0.9) l9 = latd;
                }
                sb.append(String.format(LF, "   %s 0.1@%.2f 0.9@%.2f 宽 %.2f 度", tn[q], l1, l9, l9 - l1));
            }
            say(String.format(LF, "   U0=%5.1f m/s  %s", u0, sb.toString()));
        }
        PrecipField.U0_STORM = U0;

        say("");
        say("=== 二、A/B：gate 宽度 x 闭合。corr(全 0~90) / corr(风暴轴 25~70N) / 符号 / max|MFC| ===");
        for (double u0 : new double[]{5.0, 8.0, 12.0, 20.0}) {
            for (int cl = 1; cl >= 0; cl--) {
                PrecipField.U0_STORM = u0; PrecipField.EDDY_CLOSURE = cl;
                for (int q = 0; q < 2; q++) {
                    double[] m = new double[N], o = new double[N];
                    for (int k = 0; k < N; k++) {
                        m[k] = PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[q]);
                        o[k] = ZonalTables.eddyMfcObsMonth(Math.toRadians(k * 5.0), ths[q]);
                    }
                    int same = 0, tot = 0;
                    for (int k = 5; k <= 14; k++) { tot++; if ((o[k] > 0) == (m[k] > 0)) same++; }
                    double mx = 0; int mk = 0;
                    for (int k = 5; k <= 14; k++) if (Math.abs(m[k]) > mx) { mx = Math.abs(m[k]); mk = k; }
                    say(String.format(LF, "   U0=%4.1f %s %s  corr全 %+.4f  风暴轴 %+.4f  符号 %2d/%2d  max|MFC| %6.2f mm/day @%2.0fN",
                        u0, (cl == 0 ? "sig1" : "sig2"), tn[q], corr(m, o, 0, 18), corr(m, o, 5, 14), same, tot, mx * MMD, mk * 5.0));
                }
            }
        }
        PrecipField.U0_STORM = U0; PrecipField.EDDY_CLOSURE = CL;

        say("");
        say("=== 三、三项分解（sig1, JJA）：MFC = (dG/dy)KX' + G(dK/dy)X' + G K (dX'/dy) ===");
        say("      单位 mm/day；残差 = 三项和 - 模型 MFC（中心差分的离散误差，应当小）");
        for (double u0 : new double[]{5.0, 12.0}) {
            PrecipField.U0_STORM = u0; PrecipField.EDDY_CLOSURE = 0;
            say(String.format(LF, "    --- U0 = %.1f m/s ---", u0));
            say("      lat     dG/dy项      dK/dy项      dX'/dy项     三项和      模型MFC     残差占比   dG项占比");
            double[] g = new double[N], kk = new double[N], xx = new double[N];
            for (int i = 0; i < N; i++) {
                double lat = Math.toRadians(i * 5.0);
                g[i] = PrecipField.stormGate(lat, ths[0]);
                kk[i] = kOf(lat, ths[0]);
                xx[i] = PrecipField.eddyDXdy(lat, ths[0], D5);
            }
            for (int i = 1; i < N - 1; i++) {
                double dG = (g[i + 1] - g[i - 1]) / (2 * H5);
                double dK = (kk[i + 1] - kk[i - 1]) / (2 * H5);
                double dX = (xx[i + 1] - xx[i - 1]) / (2 * H5);
                double t1 = dG * kk[i] * xx[i], t2 = g[i] * dK * xx[i], t3 = g[i] * kk[i] * dX;
                double tot = PrecipField.eddyMfc(Math.toRadians(i * 5.0), ths[0]);
                double sum = t1 + t2 + t3;
                say(String.format(LF, "     %4.0f  %+11.3f  %+11.3f  %+11.3f  %+11.3f  %+11.3f   %+7.1f%%    %+7.1f%%",
                    i * 5.0, t1 * MMD, t2 * MMD, t3 * MMD, sum * MMD, tot * MMD,
                    100.0 * Math.abs(sum - tot) / Math.max(1e-30, Math.abs(tot)), 100.0 * t1 / Math.max(1e-30, sum)));
            }
        }
        PrecipField.U0_STORM = U0; PrecipField.EDDY_CLOSURE = CL;

        say("");
        say("=== 四、幅度对文献锚 ===");
        for (int cl = 0; cl <= 1; cl++) {
            PrecipField.EDDY_CLOSURE = cl;
            double k35 = kOf(Math.toRadians(35.0), ths[0]);
            double mxJ = 0, mxD = 0; int iJ = 0, iD = 0;
            for (int k = 5; k <= 14; k++) {
                double a = Math.abs(PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[0]));
                double b = Math.abs(PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[1]));
                if (a > mxJ) { mxJ = a; iJ = k; }
                if (b > mxD) { mxD = b; iD = k; }
            }
            say(String.format(LF, "   %s  K(35N,JJA)=%.3e m^2/s  [文献 2~8e6]   max|MFC| JJA %.2f mm/day @%2.0fN  DJF %.2f @%2.0fN  [文献峰 1~3, 上界 5]  冬夏比 %.2f [文献 3~5]",
                (cl == 0 ? "sig1" : "sig2"), k35, mxJ * MMD, iJ * 5.0, mxD * MMD, iD * 5.0, mxD / Math.max(1e-30, mxJ)));
        }
        PrecipField.EDDY_CLOSURE = CL; PrecipField.U0_STORM = U0;
        say("");
        double c35 = PrecipField.staticN(Math.toRadians(35.0), ths[0]) * Atmosphere.H_EFF;
        say(String.format(LF, "   L_d 的 c = staticN*H_EFF = %.2f m/s @35N（内重力波速口径，非涡动相速）=> L_d = %.0f km；N*H/f = %.0f km",
            c35, PrecipField.deformRadius(Math.toRadians(35.0), ths[0]) / 1000.0,
            c35 / Math.abs(WorldContract.coriolis(Math.toRadians(35.0))) / 1000.0));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
