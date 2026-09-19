package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P644 -- §433 EDDY_SIGMA_T850 的 A/B：sigma/K/MFC 剖面、55~57.5N 的洞、与观测表的相关。
public class P644 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P644] " + s); System.out.println("[P644] " + s); }
    static double K(double lat, double th) { return PrecipField.EDDY_MIX * PrecipField.eadyGrowth(lat, th) * PrecipField.deformRadius(lat, th) * PrecipField.deformRadius(lat, th); }
    static double corr(double[] a, double[] b, int k0, int k1) {
        int n = k1 - k0 + 1; double sa = 0, sb = 0, sab = 0, sa2 = 0, sb2 = 0;
        for (int i = k0; i <= k1; i++) { sa += a[i]; sb += b[i]; sab += a[i] * b[i]; sa2 += a[i] * a[i]; sb2 += b[i] * b[i]; }
        double ca = sab / n - (sa / n) * (sb / n);
        double va = sa2 / n - (sa / n) * (sa / n), vb = sb2 / n - (sb / n) * (sb / n);
        return (va > 0 && vb > 0) ? ca / Math.sqrt(va * vb) : 0;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p644_report.txt"), "UTF-8");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] ths = {thS, thW};
        String[] tn = {"JJA", "DJF"};
        say("P644: EDDY_SIGMA_T850 A/B（CLOSURE=0 GATE=2 MIX=" + String.format(LF, "%.4f", PrecipField.EDDY_MIX) + "）");
        for (int s = 0; s < 2; s++) {
            say("");
            say("  === " + tn[s] + " ===   sigma(1e-6) / K(1e6) / MFC(mm/day)");
            say("      lat | sig_sl  sig_t850 |  K_sl    K_t850  | MFC_sl   MFC_t850 |  观测");
            for (double ld = 40.0; ld <= 75.0; ld += 2.5) {
                double lat = Math.toRadians(ld);
                PrecipField.EDDY_SIGMA_T850 = false;
                double s0 = PrecipField.eadyGrowth(lat, ths[s]) * 1e6, k0 = K(lat, ths[s]) / 1e6;
                double m0 = PrecipField.eddyMfc(lat, ths[s]) * 86400.0;
                PrecipField.EDDY_SIGMA_T850 = true;
                double s1 = PrecipField.eadyGrowth(lat, ths[s]) * 1e6, k1 = K(lat, ths[s]) / 1e6;
                double m1 = PrecipField.eddyMfc(lat, ths[s]) * 86400.0;
                say(String.format(LF, "     %5.1f | %7.3f %9.3f | %7.2f %8.2f | %+8.3f %+9.3f | %+6.2f",
                    ld, s0, s1, k0, k1, m0, m1, ZonalTables.eddyMfcObsMonth(lat, ths[s])));
            }
        }
        say("");
        say("  === 与观测表的相关（19 个 5 度节点 0~90N）===");
        for (int s = 0; s < 2; s++) {
            for (int q = 0; q <= 1; q++) {
                PrecipField.EDDY_SIGMA_T850 = (q == 1);
                double[] m = new double[19], o = new double[19];
                for (int k = 0; k < 19; k++) {
                    m[k] = PrecipField.eddyMfc(Math.toRadians(k * 5.0), ths[s]);
                    o[k] = ZonalTables.eddyMfcObsMonth(Math.toRadians(k * 5.0), ths[s]);
                }
                int same = 0; for (int k = 5; k <= 14; k++) if ((o[k] > 0) == (m[k] > 0)) same++;
                say(String.format(LF, "      %s  T850=%d   corr全 %+.4f   带25~70N %+.4f   符号 %2d/10",
                    tn[s], q, corr(m, o, 0, 18), corr(m, o, 5, 14), same));
            }
        }
        PrecipField.EDDY_SIGMA_T850 = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
