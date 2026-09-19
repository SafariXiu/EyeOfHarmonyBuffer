package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P641 -- §432 50~62N「涡动供雨不足」的形状诊断。
// ⚠ 步长必须 = EDDY_DPHI_DEG（生产用的就是它），否则分解复现不了 MFC（我第一版用了 2.5°，作废）。
public class P641 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P641] " + s); System.out.println("[P641] " + s); }
    static final double R = WorldContract.R_EFF;
    static final double D5 = Math.toRadians(PrecipField.EDDY_DPHI_DEG), H5 = D5 * R;
    static double K(double lat, double th) { return PrecipField.EDDY_MIX * PrecipField.eadyGrowth(lat, th) * PrecipField.deformRadius(lat, th) * PrecipField.deformRadius(lat, th); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p641_report.txt"), "UTF-8");
        say(String.format(LF, "P641: MFC = K'X' + K X''（步长 = %.1f 度；CLOSURE=%d GATE=%d MIX=%.4f）",
            PrecipField.EDDY_DPHI_DEG, PrecipField.EDDY_CLOSURE, PrecipField.EDDY_GATE_MODE, PrecipField.EDDY_MIX));
        double[] ths = {Atmosphere.theta(0.0), Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0)};
        String[] tn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            say("");
            say(String.format(LF, "  === %s ===", tn[s]));
            say("      lat |  W     X'(1e-9)  sigma(1e-6)  L_d(km)   K(1e6) |  K'X'(1e-4)  K X''(1e-4)  和 | 和*86400  MFC mm/day |  观测");
            for (double ld = 30.0; ld <= 75.0; ld += 2.5) {
                double lat = Math.toRadians(ld), th = ths[s];
                double w = PrecipField.columnWater(lat, th);
                double x0 = PrecipField.eddyDXdy(lat, th, D5);
                double xp = PrecipField.eddyDXdy(lat + D5, th, D5), xm = PrecipField.eddyDXdy(lat - D5, th, D5);
                double k0 = K(lat, th), kp = K(lat + D5, th), km = K(lat - D5, th);
                double t1 = ((kp - km) / (2 * H5)) * x0;
                double t2 = k0 * ((xp - xm) / (2 * H5));
                double mfc = PrecipField.eddyMfc(lat, th);
                say(String.format(LF, "     %5.1f | %6.2f %9.1f  %10.3f  %8.1f %8.3f | %+11.2f %+12.2f %+7.2f | %+9.3f %+10.3f | %+6.2f",
                    ld, w, x0 * 1e9, PrecipField.eadyGrowth(lat, th) * 1e6, PrecipField.deformRadius(lat, th) / 1000.0, k0 / 1e6,
                    t1 * 1e4, t2 * 1e4, (t1 + t2) * 1e4, (t1 + t2) * 86400.0, mfc * 86400.0,
                    ZonalTables.eddyMfcObsMonth(lat, th)));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
