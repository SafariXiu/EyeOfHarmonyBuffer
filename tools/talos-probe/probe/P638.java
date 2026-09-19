package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P638 -- §428 涡动门三模式 x 两闭合：下边界（临界纬度）与过渡宽度（Rhines）。
// 目标（本轮目标第 3 项原文）：修 stormGate 的【下边界与过渡宽度】+ DJF 30~50N 量级偏大 3~5 倍。
public class P638 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P638] " + s); System.out.println("[P638] " + s); }
    static final double R = WorldContract.R_EFF, MMD = 86400.0;
    static final double D5 = Math.toRadians(5.0), H5 = D5 * R;
    static final int N = 19;
    static final String[] GM = {"门0阈值", "门1混合长", "门2临界纬度"};

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p638_report.txt"), "UTF-8");
        int CL = PrecipField.EDDY_CLOSURE, GM0 = PrecipField.EDDY_GATE_MODE;
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};
        say("P638: 涡动门三模式（下边界 + 过渡宽度），目标第 3 项原文的验收量");

        say("");
        say("=== 一、临界纬度 phi_c 与 Rhines 宽度 W（模型自己的 u_zm / sigma / L_d 给出）===");
        for (int q = 0; q < 2; q++) {
            double pc = PrecipField.criticalLatRad(ths[q], true);
            double pcS = PrecipField.criticalLatRad(ths[q], false);
            say(String.format(LF, "   %s  北半球 phi_c = %.2f N   南半球 phi_c = %.2f S", tn[q],
                Math.toDegrees(Math.abs(pc)), Math.toDegrees(Math.abs(pcS))));
        }
        say("      lat    W(度) JJA   W(度) DJF    L_R(km) JJA    L_lag(km) JJA    u_zm JJA   u_zm DJF");
        for (int k = 0; k < N; k++) {
            double lat = Math.toRadians(k * 5.0);
            say(String.format(LF, "     %4.0f    %9.2f    %9.2f    %11.1f    %12.1f    %8.2f   %8.2f",
                k * 5.0, Math.toDegrees(PrecipField.rhinesWidthRad(lat, ths[0])),
                Math.toDegrees(PrecipField.rhinesWidthRad(lat, ths[1])),
                PrecipField.rhinesWidthRad(lat, ths[0]) * R / 1000.0,
                PrecipField.mixingLength(lat, ths[0]) / 1000.0,
                ZonalTables.uZm(k * 5.0, ths[0]), ZonalTables.uZm(k * 5.0, ths[1])));
        }

        say("");
        say("=== 二、门剖面（0~70N 每 2.5 度）===");
        for (int q = 0; q < 2; q++) {
            say(String.format(LF, "   [%s]   lat     门0       门1       门2", tn[q]));
            for (double ld = 0; ld <= 70.0; ld += 2.5) {
                double lat = Math.toRadians(ld);
                say(String.format(LF, "        %5.1f  %8.4f  %8.4f  %8.4f", ld,
                    PrecipField.stormGate(lat, ths[q]), PrecipField.stormGateWide(lat, ths[q]),
                    PrecipField.stormGateCrit(lat, ths[q])));
            }
        }

        say("");
        say("=== 三、6 格：corr / 正瓣 / 季节比 / DJF 25~50N 的 eddy 降水贡献（= 正 MFC，mm/day）===");
        say("     配置           JJA corr全 带25-70 | DJF corr全 带25-70 符号 | JJA 正瓣@lat | DJF 正瓣@lat | 冬夏比 | DJF 30~50N 最大正贡献");
        for (int gm = 0; gm <= 2; gm++) {
            for (int cl = 0; cl <= 1; cl++) {
                PrecipField.EDDY_GATE_MODE = gm; PrecipField.EDDY_CLOSURE = cl;
                double[][] m = new double[2][N]; double[][] o = new double[2][N];
                for (int q = 0; q < 2; q++)
                    for (int k = 0; k < N; k++) {
                        m[q][k] = PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[q]);
                        o[q][k] = ZonalTables.eddyMfcObsMonth(Math.toRadians(k * 5.0), ths[q]);
                    }
                int same = 0; for (int k = 5; k <= 14; k++) if ((o[1][k] > 0) == (m[1][k] > 0)) same++;
                double pj = 0; int ij = 0, id = 0; double pd = 0;
                for (int k = 5; k <= 14; k++) { if (m[0][k] > pj) { pj = m[0][k]; ij = k; } if (m[1][k] > pd) { pd = m[1][k]; id = k; } }
                double d3050 = 0;
                for (double ld = 30.0; ld <= 50.0; ld += 1.0) {
                    double v = PrecipField.eddyMfc(Math.toRadians(ld), ths[1]);
                    if (v > d3050) d3050 = v;
                }
                say(String.format(LF, "     %s sig%d  %+.4f %+.4f | %+.4f %+.4f %2d/10 | %6.2f@%2.0fN | %6.2f@%2.0fN | %5.2f | %8.2f mm/day",
                    GM[gm], cl, corr(m[0], o[0], 0, 18), corr(m[0], o[0], 5, 14),
                    corr(m[1], o[1], 0, 18), corr(m[1], o[1], 5, 14), same,
                    pj * MMD, ij * 5.0, pd * MMD, id * 5.0, pd / Math.max(1e-30, pj), d3050 * MMD));
            }
        }
        PrecipField.EDDY_GATE_MODE = GM0; PrecipField.EDDY_CLOSURE = CL;

        say("");
        say("=== 四、三项分解（sig1，DJF）：dG/dy 项的占比应当显著下降 ===");
        for (int gm = 0; gm <= 2; gm++) {
            PrecipField.EDDY_GATE_MODE = gm; PrecipField.EDDY_CLOSURE = 0;
            double[] g = new double[N], kk = new double[N], xx = new double[N];
            for (int i = 0; i < N; i++) {
                double lat = Math.toRadians(i * 5.0);
                g[i] = PrecipField.gateOf(lat, ths[1]); kk[i] = kOf(lat, ths[1]);
                xx[i] = PrecipField.eddyDXdy(lat, ths[1], D5);
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(LF, "   %s |", GM[gm]));
            for (int i = 1; i < N - 1; i++) {
                if (i * 5.0 < 30.0 || i * 5.0 > 50.0) continue;
                double dG = (g[i + 1] - g[i - 1]) / (2 * H5), dK = (kk[i + 1] - kk[i - 1]) / (2 * H5);
                double dX = (xx[i + 1] - xx[i - 1]) / (2 * H5);
                double t1 = dG * kk[i] * xx[i], t2 = g[i] * dK * xx[i], t3 = g[i] * kk[i] * dX;
                double den = Math.abs(t1) + Math.abs(t2) + Math.abs(t3);
                sb.append(String.format(LF, "  %2.0fN: %+6.2f (%3.0f%%) MFC %+6.2f |", i * 5.0, t1 * MMD,
                    100.0 * Math.abs(t1) / Math.max(1e-30, den), (t1 + t2 + t3) * MMD));
            }
            say(sb.toString());
        }
        PrecipField.EDDY_GATE_MODE = GM0; PrecipField.EDDY_CLOSURE = CL;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
