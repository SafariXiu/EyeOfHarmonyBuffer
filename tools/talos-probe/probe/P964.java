package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P964: re-examine the STATISTIC used by P293 GATE_EQ_JUMP.
 *
 * <p>P293 asserts max |f(-eps) - f(+eps)| < 1e-9 K at the equator, where f = seasonalAnomaly.
 * That statistic is the correct continuity test only if f is EVEN about the equator.
 * Atmosphere:608 sets hemi = (latRad >= 0 ? 0 : PI), i.e. the seasonal phase flips by PI
 * across the equator, so f is ODD by construction: f(-eps) = -f(+eps) exactly.
 * For an odd f, |f(-eps) - f(+eps)| = 2|f(+eps)| -> 0 LINEARLY in eps; it is a sampling
 * offset, not a jump. The genuine continuity test for an odd field is the EVEN PART
 * |f(+eps) + f(-eps)| (an offset jump) plus the one-sided derivatives.
 *
 * <p>This probe reports BOTH statistics at several eps, so the scaling can be inspected.
 */
public class P964 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p964_report.txt"), "UTF-8");
        say("P964: GATE_EQ_JUMP statistic audit (odd field vs even part)");
        say("");
        double[] eps = {1e-2, 1e-3, 1e-4, 1e-5, 1e-6, 1e-7, 1e-8};
        double[] ths = {0.0, Math.PI / 4.0, Math.PI / 2.0, Math.PI, 3.0 * Math.PI / 2.0};
        double[] ks = {0.0, 0.15, 0.5, 1.0};
        say(String.format(LF, "  %-10s %16s %16s %16s %14s", "eps(rad)", "OLD |f(-)-f(+)|", "EVEN |f(-)+f(+)|", "OLD/(2eps)", "|f(0)|"));
        for (double e : eps) {
            double wOld = 0, wEven = 0, wSlope = 0, wF0 = 0;
            for (double th : ths) {
                for (double k : ks) {
                    double fp = Atmosphere.seasonalAnomaly(+e, k, th);
                    double fm = Atmosphere.seasonalAnomaly(-e, k, th);
                    double f0 = Atmosphere.seasonalAnomaly(0.0, k, th);
                    double o = Math.abs(fm - fp), v = Math.abs(fm + fp);
                    if (o > wOld) wOld = o;
                    if (v > wEven) wEven = v;
                    if (Math.abs(o / (2.0 * e)) > wSlope) wSlope = Math.abs(o / (2.0 * e));
                    if (Math.abs(f0) > wF0) wF0 = Math.abs(f0);
                }
            }
            say(String.format(LF, "  %-10.0e %16.6e %16.6e %16.6e %14.3e", e, wOld, wEven, wSlope, wF0));
        }
        say("");
        say("  Reading: if EVEN is 0 and OLD/(2eps) is constant, then OLD is a linear sampling");
        say("  offset (2*eps*|f'(0)|), NOT a discontinuity. A real offset jump would keep OLD");
        say("  constant as eps -> 0 and would show up in EVEN.");
        say("");
        // one-sided derivatives, central-free, at several eps
        say(String.format(LF, "  %-10s %18s %18s %18s", "eps(rad)", "d/dlat f(0+)", "d/dlat f(0-)", "rel.diff"));
        for (double e : eps) {
            double dn = 0, ds = 0;
            for (double th : ths) {
                for (double k : ks) {
                    double a = Math.abs((Atmosphere.seasonalAnomaly(+e, k, th) - Atmosphere.seasonalAnomaly(0.0, k, th)) / e);
                    double b = Math.abs((Atmosphere.seasonalAnomaly(0.0, k, th) - Atmosphere.seasonalAnomaly(-e, k, th)) / e);
                    if (a > dn) dn = a;
                    if (b > ds) ds = b;
                }
            }
            double rd = Math.abs(dn - ds) / Math.max(1e-300, Math.max(dn, ds));
            say(String.format(LF, "  %-10.0e %18.6e %18.6e %18.3e", e, dn, ds, rd));
        }
        say("");
        say("  VERDICT fields:");
        double e0 = 1e-4;
        double wOld = 0, wEven = 0;
        for (double th : ths) for (double k : ks) {
            double fp = Atmosphere.seasonalAnomaly(+e0, k, th), fm = Atmosphere.seasonalAnomaly(-e0, k, th);
            if (Math.abs(fm - fp) > wOld) wOld = Math.abs(fm - fp);
            if (Math.abs(fm + fp) > wEven) wEven = Math.abs(fm + fp);
        }
        say(String.format(LF, "  at eps=1e-4:  OLD=%.8f  EVEN=%.3e  OLD/EVEN=%s", wOld, wEven, wEven == 0.0 ? "inf (exact zero)" : String.format(LF, "%.1f", wOld / wEven)));
        say(String.format(LF, "  GATE_EVEN_PART=%s", wEven < 1e-9 ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_F0_ZERO=%s", Math.abs(Atmosphere.seasonalAnomaly(0.0, 0.5, 0.0)) < 1e-9 ? "PASS" : "FAIL"));
        rep.close();
    }

    static void say(String s) { System.out.println("[P964] " + s); if (rep != null) rep.println("[P964] " + s); }
}