package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P967: land-side T_a from the closed-form inversion of the model's own energy balance. */
public class P967 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p967_report.txt"), "UTF-8");
        say(rep, "P967: land T_a = T_s - (absSolar - OLR(T_s) - LE(T_s)) / (chv*CP)");
        say(rep, "");
        double ts = 300.0, kappa = 1.0, lat = Math.toRadians(30.0), v = 5.0;
        double chv = Atmosphere.cdOf(kappa) * v;
        double qa = 0.005;
        double qs = PrecipField.qSat(ts);
        say(rep, String.format(LF, "  T_s=%.1f K  qSat(T_s)=%.5f  q_air=%.5f  chv=%.5f  eps*sigma*T^4=%.1f W/m2",
            ts, qs, qa, chv, Radiation.EPS * Radiation.SIGMA * Math.pow(ts, 4)));
        say(rep, "");
        say(rep, String.format(LF, "  absSolar  beta    T_a K    T_s-T_a K   dT/dbeta   flux K m/s   w_* m/s"));
        double prev = Double.NaN, flipBeta = Double.NaN;
        double[] sol = {0.0, 100.0, 200.0, 300.0, 400.0};
        double[] bet = {0.0, 0.1, 0.2, 0.3, 0.5, 0.7, 1.0};
        int nUnstable = 0, nStable = 0, nTot = 0, nWPos = 0, nBuoyant = 0;
        for (double s : sol) {
            for (double b : bet) {
                double ta = PrecipField.airTempK(ts, s, qa, b, lat, kappa, v);
                double flux = PrecipField.surfaceBuoyancyFluxK(ts, qs, qa, s, b, lat, kappa, v);
                double w = PrecipField.wStarK(ts, qs, qa, s, b, lat, kappa, v);
                double d = ts - ta;
                double thvS = ts * (1.0 + 0.608 * qs), thvA = (ts - d) * (1.0 + 0.608 * qa);
                nTot++; if (d > 0) nUnstable++; else nStable++; if (w > 0) nWPos++; if (thvS > thvA) nBuoyant++;
                say(rep, String.format(LF, "  %7.0f  %5.2f  %8.3f  %+10.4f              %+11.4f  %9.4f",
                    s, b, ta, d, flux, w));
            }
            say(rep, "");
        }
        // beta flip point at strong sun
        double sStrong = 400.0, lo = 0.0, hi = 1.0;
        for (int i = 0; i < 60; i++) {
            double mid = 0.5 * (lo + hi);
            double d = ts - PrecipField.airTempK(ts, sStrong, qa, mid, lat, kappa, v);
            if (d > 0) lo = mid; else hi = mid;
        }
        double betaFlip = 0.5 * (lo + hi);
        say(rep, String.format(LF, "  beta flip point at absSolar=%.0f: beta = %.3f  (surface stops being warmer than air)", sStrong, betaFlip));
        say(rep, String.format(LF, "  reference: Radiation:100-102 records P570 measuring H flipping positive at beta ~ 0.3"));
        say(rep, "");
        say(rep, String.format(LF, "  samples=%d  T_s>T_a (thermally unstable) = %d   T_s<=T_a = %d   theta_v,s>theta_v,a = %d   w_*>0 = %d", nTot, nUnstable, nStable, nBuoyant, nWPos));
        say(rep, String.format(LF, "  GATE_LAND_DT_BOTH_SIGNS=%s   (the energy balance must produce BOTH signs)",
            (nUnstable > 0 && nStable > 0) ? "PASS" : "FAIL"));
        say(rep, String.format(LF, "  GATE_LAND_BETA_FLIP=%s      (betaFlip in [0.05, 0.6])",
            (betaFlip >= 0.05 && betaFlip <= 0.6) ? "PASS" : "FAIL"));
        say(rep, String.format(LF, "  GATE_LAND_WS_POS=%s         (w_* > 0 IFF theta_v(T_s,q_sat) > theta_v(T_a,q): %d/%d)",
            nWPos == nBuoyant ? "PASS" : "FAIL", nWPos, nBuoyant));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P967] " + s); rep.println("[P967] " + s); }
}