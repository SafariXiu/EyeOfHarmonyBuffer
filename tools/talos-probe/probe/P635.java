package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P635 -- §427 用户裁决 A：换被涡动扩散的变量。三轴：变量(3) x 梯度构造(2) x 闭合(2)。
// 文献核实（子代理 96781b75）：标准闭合 D = sigma*L_d^2/0.31 = 3.23*sigma*L_d^2 (Green 1970 / Stone 1972);
//   被扩散变量优先取 750~1000 hPa 比湿，不要整层 W；Lu et al. 2022 实测 K 峰在 35N、1.85e6 m^2/s。
// 观测表 eddyMfcObsMonth 无量纲、45~60 度年均归一到 1 ==> 只有 corr 与符号结构可判。
public class P635 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P635] " + s); System.out.println("[P635] " + s); }
    static final double R = WorldContract.R_EFF;
    static final double D = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
    static final int N = 19;
    static final String[] VN = {"W柱水汽", "q近地比湿", "h湿静能"};
    static final String[] GN = {"node", "pchip"};
    static final String[] CN = {"sig1", "sig2"};

    static double corr(double[] a, double[] b) {
        int n = a.length; double sa = 0, sb = 0, sab = 0, sa2 = 0, sb2 = 0;
        for (int i = 0; i < n; i++) { sa += a[i]; sb += b[i]; sab += a[i] * b[i]; sa2 += a[i] * a[i]; sb2 += b[i] * b[i]; }
        double ca = sab / n - (sa / n) * (sb / n);
        double va = sa2 / n - (sa / n) * (sa / n), vb = sb2 / n - (sb / n) * (sb / n);
        return (va > 0 && vb > 0) ? ca / Math.sqrt(va * vb) : 0;
    }

    static double kOf(double lat, double th) {
        double ld = PrecipField.deformRadius(lat, th), sg = PrecipField.eadyGrowth(lat, th);
        return (PrecipField.EDDY_CLOSURE == 0) ? PrecipField.EDDY_MIX * sg * ld * ld
             : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
    }

    // 组合序号: c = ((v*2)+g)*2+cl
    static final int NC = 12;
    static int idx(int v, int g, int cl) { return ((v * 2) + g) * 2 + cl; }
    static void set(int v, int g, int cl) { PrecipField.EDDY_VAR = v; PrecipField.EDDY_GRAD = g; PrecipField.EDDY_CLOSURE = cl; }
    static String nm(int c) { return String.format(LF, "%-9s %-5s %s", VN[c / 4], GN[(c / 2) % 2], CN[c % 2]); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p635_report.txt"), "UTF-8");
        say("P635: §427 涡动扩散变量 A/B（变量 x 梯度构造 x 闭合 = 12 格）");
        say("  基线自检：W/node/sig2 这格必须等于生产现状 modelShapeFull");
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};
        int CL0 = PrecipField.EDDY_CLOSURE;

        double[][] obs = new double[2][N];
        for (int q = 0; q < 2; q++)
            for (int k = 0; k < N; k++) obs[q][k] = ZonalTables.eddyMfcObsMonth(Math.toRadians(k * 5.0), ths[q]);

        double[][] mv = new double[2][NC * N];
        say("");
        say("=== 一、corr(模型 MFC, 观测) / 符号一致率 / 最小二乘缩放后的相对 L2 残差 ===");
        for (int q = 0; q < 2; q++) {
            say(String.format(LF, "  [%s]", tn[q]));
            for (int c = 0; c < NC; c++) {
                set(c / 4, (c / 2) % 2, c % 2);
                double[] m = new double[N];
                for (int k = 0; k < N; k++) { m[k] = PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[q]); mv[q][c * N + k] = m[k]; }
                int same = 0, tot = 0;
                for (int k = 0; k < N; k++) if (Math.abs(obs[q][k]) > 0.05) { tot++; if ((obs[q][k] > 0) == (m[k] > 0)) same++; }
                double num = 0, den = 0;
                for (int k = 0; k < N; k++) { num += m[k] * obs[q][k]; den += m[k] * m[k]; }
                double s = den > 0 ? num / den : 0, e = 0, o = 0;
                for (int k = 0; k < N; k++) { double d = s * m[k] - obs[q][k]; e += d * d; o += obs[q][k] * obs[q][k]; }
                say(String.format(LF, "      %-22s corr %+.4f  符号 %2d/%2d  残差 %.4f", nm(c), corr(m, obs[q]), same, tot, Math.sqrt(e / o)));
            }
        }
        set(0, 0, CL0);

        say("");
        say("=== 二、扩散率 K 与文献锚（Lu et al. 2022 实测 K 峰 1.85e6 m^2/s @35N）===");
        say("      lat     L_d(km)   sigma(1/s)   K_sig1      K_sig2     K_文献@35N");
        for (int k = 0; k < N; k++) {
            double lat = Math.toRadians(k * 5.0);
            PrecipField.EDDY_CLOSURE = 0; double k1 = kOf(lat, ths[0]);
            PrecipField.EDDY_CLOSURE = 1; double k2 = kOf(lat, ths[0]);
            say(String.format(LF, "     %4.0f   %8.1f   %10.3e   %10.3e  %10.3e   %s",
                k * 5.0, PrecipField.deformRadius(lat, ths[0]) / 1000.0, PrecipField.eadyGrowth(lat, ths[0]), k1, k2,
                (k == 7 ? "1.850e6" : "")));
        }
        PrecipField.EDDY_CLOSURE = CL0;

        say("");
        say("=== 三、被扩散标量 X（kg/m^2）与梯度 X'（1e-9 kg/m^2/m），JJA ===");
        say("      lat    X_W      X_q      X_h   |  X'_W(node) X'_q(node) X'_h(node) |  X'_q(pchip) X'_h(pchip)");
        for (int k = 0; k < N; k++) {
            double lat = Math.toRadians(k * 5.0);
            double[] x = new double[3], gn = new double[3], gp = new double[3];
            for (int v = 0; v < 3; v++) {
                PrecipField.EDDY_VAR = v; PrecipField.EDDY_GRAD = 0;
                x[v] = PrecipField.eddyScalar(lat, ths[0]);
                gn[v] = PrecipField.eddyDXdy(lat, ths[0], D) * 1e9;
                PrecipField.EDDY_GRAD = 1;
                gp[v] = PrecipField.eddyDXdy(lat, ths[0], D) * 1e9;
            }
            say(String.format(LF, "     %4.0f  %7.3f  %7.3f  %8.2f   | %8.3f %8.3f %8.3f | %8.3f %8.3f",
                k * 5.0, x[0], x[1], x[2], gn[0], gn[1], gn[2], gp[1], gp[2]));
        }
        PrecipField.EDDY_VAR = 0; PrecipField.EDDY_GRAD = 0;

        say("");
        say("=== 四、|K*X'| 的单峰性（JJA，pchip；不乘 gate，只看通量形状）===");
        for (int cl = 0; cl < 2; cl++) {
            for (int v = 0; v < 3; v++) {
                PrecipField.EDDY_GRAD = 1; PrecipField.EDDY_VAR = v; PrecipField.EDDY_CLOSURE = cl;
                double best = -1; int bk = -1, sc = 0; double prev = 0;
                for (int k = 0; k < N; k++) {
                    double lat = Math.toRadians(k * 5.0);
                    double f = kOf(lat, ths[0]) * PrecipField.eddyDXdy(lat, ths[0], D);
                    if (Math.abs(f) > best) { best = Math.abs(f); bk = k; }
                    if (k > 0 && f * prev < 0) sc++;
                    prev = f;
                }
                say(String.format(LF, "      %-4s %-9s  峰位 %4.0fN   |K X'|max %.4e   符号翻转 %d 次", CN[cl], VN[v], bk * 5.0, best, sc));
            }
        }
        PrecipField.EDDY_VAR = 0; PrecipField.EDDY_GRAD = 0; PrecipField.EDDY_CLOSURE = CL0;

        say("");
        say("=== 五、MFC 剖面（各格按最小二乘缩放到观测，便于看形状）===");
        for (int q = 0; q < 2; q++) {
            say(String.format(LF, "   [%s]  lat    观测   |  W/node  W/pchip  q/node  q/pchip  h/node  h/pchip (sig1)", tn[q]));
            for (int k = 0; k < N; k++) {
                StringBuilder sb = new StringBuilder();
                sb.append(String.format(LF, "        %4.0f  %+7.3f  |", k * 5.0, obs[q][k]));
                for (int v = 0; v < 3; v++) for (int g = 0; g < 2; g++) {
                    int c = idx(v, g, 0);
                    double[] mm = new double[N]; double num = 0, den = 0;
                    for (int j = 0; j < N; j++) { mm[j] = mv[q][c * N + j]; num += mm[j] * obs[q][j]; den += mm[j] * mm[j]; }
                    double s = den > 0 ? num / den : 0;
                    sb.append(String.format(LF, " %+8.4f", s * mm[k]));
                }
                say(sb.toString());
            }
        }

        say("");
        say("=== 六、守恒型的分解自检：d(gate*K*X')/dy == gate*d(K X')/dy + (dgate/dy)*(K X') ===");
        say("      （用 q/pchip/sig1；三项都算出来并核对和式，误差应为 0）");
        set(1, 1, 0);
        double[] gv = new double[N], kv = new double[N], xv = new double[N];
        for (int k = 0; k < N; k++) {
            double lat = Math.toRadians(k * 5.0);
            gv[k] = PrecipField.stormGate(lat, ths[0]);
            kv[k] = kOf(lat, ths[0]);
            xv[k] = PrecipField.eddyDXdy(lat, ths[0], D);
        }
        say("      lat    d(gKX')/dy   gate*d(KX')/dy   (dg/dy)*KX'     和式-左式    占比");
        double h = D * R;
        for (int k = 1; k < N - 1; k++) {
            double lhs = (gv[k + 1] * kv[k + 1] * xv[k + 1] - gv[k - 1] * kv[k - 1] * xv[k - 1]) / (2 * h);
            double t1 = gv[k] * (kv[k + 1] * xv[k + 1] - kv[k - 1] * xv[k - 1]) / (2 * h);
            double t2 = ((gv[k + 1] - gv[k - 1]) / (2 * h)) * kv[k] * xv[k];
            say(String.format(LF, "     %4.0f  %+11.4e  %+14.4e  %+13.4e  %+11.3e   %+7.1f%%",
                k * 5.0, lhs, t1, t2, (t1 + t2) - lhs, 100.0 * Math.abs(t2) / Math.max(1e-30, Math.abs(t1))));
        }
        PrecipField.EDDY_VAR = 0; PrecipField.EDDY_GRAD = 0; PrecipField.EDDY_CLOSURE = CL0;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
