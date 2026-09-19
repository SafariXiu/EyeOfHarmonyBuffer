package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P637 -- §427.3 涡动门过渡尺度：EDDY_GATE_MODE 0/1 x EDDY_CLOSURE 0/1。
// 文献锚：K_Q 2~8e6 m^2/s；MFC 峰 1~3、任何纬向上界 ~5 mm/day；冬夏比 3~5；过渡宽度 >= 混合长 6~9 度。
public class P637 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P637] " + s); System.out.println("[P637] " + s); }
    static final double R = WorldContract.R_EFF, MMD = 86400.0;
    static final double D5 = Math.toRadians(5.0), H5 = D5 * R;
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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p637_report.txt"), "UTF-8");
        int CL = PrecipField.EDDY_CLOSURE, GM = PrecipField.EDDY_GATE_MODE; double U0 = PrecipField.U0_STORM;
        say("P637: 涡动门过渡尺度 EDDY_GATE_MODE（0 = 阈值 smoothstep，1 = 混合长宽度 tanh）");
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};

        say("");
        say("=== 一、门与混合长的纬度结构 ===");
        say("      lat   |  gate_0 JJA  gate_1 JJA |  gate_0 DJF  gate_1 DJF |  l_L(km)  u_w(m/s)  dudy(1e-6/s)");
        for (int k = 0; k < N; k++) {
            double lat = Math.toRadians(k * 5.0);
            double l0j = PrecipField.stormGate(lat, ths[0]), l1j = PrecipField.stormGateWide(lat, ths[0]);
            double l0w = PrecipField.stormGate(lat, ths[1]), l1w = PrecipField.stormGateWide(lat, ths[1]);
            double ml = PrecipField.mixingLength(lat, ths[0]);
            double sh = PrecipField.uShearAbs(lat, ths[0]);
            say(String.format(LF, "     %4.0f   |   %7.4f    %7.4f  |   %7.4f    %7.4f  |  %7.1f  %8.3f  %11.3f",
                k * 5.0, l0j, l1j, l0w, l1w, ml / 1000.0, sh * ml, sh * 1e6));
        }

        say("");
        say("=== 二、四格 A/B：corr(全) / corr(25~70N) / 符号 / 正瓣峰值 / 冬夏比 ===");
        for (int cl = 1; cl >= 0; cl--) {
            for (int gm = 0; gm <= 1; gm++) {
                PrecipField.EDDY_CLOSURE = cl; PrecipField.EDDY_GATE_MODE = gm;
                StringBuilder line = new StringBuilder();
                line.append(String.format(LF, "   %s 门%d |", (cl == 0 ? "sig1" : "sig2"), gm));
                double[] posMax = new double[2];
                for (int q = 0; q < 2; q++) {
                    double[] m = new double[N], o = new double[N];
                    for (int k = 0; k < N; k++) {
                        m[k] = PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[q]);
                        o[k] = ZonalTables.eddyMfcObsMonth(Math.toRadians(k * 5.0), ths[q]);
                    }
                    int same = 0; for (int k = 5; k <= 14; k++) if ((o[k] > 0) == (m[k] > 0)) same++;
                    double pm = 0; int pk = 0;
                    for (int k = 5; k <= 14; k++) if (m[k] > pm) { pm = m[k]; pk = k; }
                    posMax[q] = pm;
                    line.append(String.format(LF, " %s 全%+.4f 带%+.4f 符%2d/10 正瓣%5.2f@%2.0fN |",
                        tn[q], corr(m, o, 0, 18), corr(m, o, 5, 14), same, pm * MMD, pk * 5.0));
                }
                line.append(String.format(LF, " 冬夏比 %.2f  负瓣 %.1f mm/day", posMax[1] / Math.max(1e-30, posMax[0]), 0.0));
                say(line.toString());
            }
        }
        PrecipField.EDDY_CLOSURE = CL; PrecipField.EDDY_GATE_MODE = GM;

        say("");
        say("=== 三、MFC 剖面（sig1，门 0 / 门 1），单位 mm/day ===");
        for (int q = 0; q < 2; q++) {
            say(String.format(LF, "   [%s]   lat    观测(无量纲)    门0 MFC      门1 MFC", tn[q]));
            for (int k = 0; k < N; k++) {
                double lat = Math.toRadians(k * 5.0);
                PrecipField.EDDY_CLOSURE = 0; PrecipField.EDDY_GATE_MODE = 0;
                double a = PrecipField.eddyMfc(lat, ths[q]);
                PrecipField.EDDY_GATE_MODE = 1;
                double b = PrecipField.eddyMfc(lat, ths[q]);
                say(String.format(LF, "        %4.0f   %+10.3f     %+9.3f    %+9.3f",
                    k * 5.0, ZonalTables.eddyMfcObsMonth(lat, ths[q]), a * MMD, b * MMD));
            }
        }
        PrecipField.EDDY_CLOSURE = CL; PrecipField.EDDY_GATE_MODE = GM;

        say("");
        say("=== 四、三项分解（sig1，JJA）门0 vs 门1：dG/dy 项应当不再是主导 ===");
        for (int gm = 0; gm <= 1; gm++) {
            PrecipField.EDDY_CLOSURE = 0; PrecipField.EDDY_GATE_MODE = gm;
            say(String.format(LF, "    --- 门 %d ---", gm));
            say("      lat    dG/dy项     dK/dy项    dX'/dy项     三项和     模型MFC    dG占比");
            double[] g = new double[N], kk = new double[N], xx = new double[N];
            for (int i = 0; i < N; i++) {
                double lat = Math.toRadians(i * 5.0);
                g[i] = PrecipField.gateOf(lat, ths[0]);
                kk[i] = kOf(lat, ths[0]);
                xx[i] = PrecipField.eddyDXdy(lat, ths[0], D5);
            }
            for (int i = 1; i < N - 1; i++) {
                double dG = (g[i + 1] - g[i - 1]) / (2 * H5), dK = (kk[i + 1] - kk[i - 1]) / (2 * H5);
                double dX = (xx[i + 1] - xx[i - 1]) / (2 * H5);
                double t1 = dG * kk[i] * xx[i], t2 = g[i] * dK * xx[i], t3 = g[i] * kk[i] * dX;
                double tot = PrecipField.eddyMfc(Math.toRadians(i * 5.0), ths[0]);
                double sum = t1 + t2 + t3;
                double frac = (Math.abs(t1) + Math.abs(t2) + Math.abs(t3)) > 0
                    ? 100.0 * Math.abs(t1) / (Math.abs(t1) + Math.abs(t2) + Math.abs(t3)) : 0;
                say(String.format(LF, "     %4.0f  %+10.3f  %+10.3f  %+10.3f  %+10.3f  %+10.3f   %+6.1f%%",
                    i * 5.0, t1 * MMD, t2 * MMD, t3 * MMD, sum * MMD, tot * MMD, frac));
            }
        }
        PrecipField.EDDY_CLOSURE = CL; PrecipField.EDDY_GATE_MODE = GM; PrecipField.U0_STORM = U0;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
