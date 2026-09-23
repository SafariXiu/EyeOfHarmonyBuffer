package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P293 v2: equator continuity of seasonalAnomaly -- criterion REWRITTEN (P964 proof).
 *
 * <h3>Why the old criterion was wrong (measured, not argued)</h3>
 * v1 asserted max |f(-e) - f(+e)| < 1e-9 K at the equator. P964 measured that statistic at
 * seven decades of e: it scales EXACTLY as e, with |f(-e)-f(+e)| / (2e) = 13.25527 constant to
 * seven significant figures (e = 1e-2 .. 1e-8 rad). The even part |f(-e)+f(+e)| is 1e-17..1e-23
 * (ULP noise of the amplitude), f(0) is bit-exactly 0, and the one-sided derivatives agree with
 * relative difference 0.000e+00.
 *
 * <p>f is ODD about the equator by construction: Atmosphere:608 flips the seasonal phase by PI
 * (hemi = latRad >= 0 ? 0 : PI) and ZonalTables.interp:144 takes Math.abs(latDeg), so the
 * amplitude is even. For an odd f, |f(-e)-f(+e)| = 2|f(e)| = 2*e*|f(0)| -- a sampling offset,
 * NOT a jump. v1's reading reconciles bit-for-bit: it used eps = toRadians(1e-4) = 1.745329e-6 rad
 * and 2 * 1.745329e-6 * 13.25527 = 4.6270e-5 K, the exact value v1 printed.
 *
 * <p>v1 therefore demanded |df/dlat| < 1e-9 / (2*1.745e-6) = 2.9e-4 K/rad = 5e-6 K/deg at the
 * equator, while the A(phi) table necessarily rises to 16.8 K by 90 deg. NO table with a nonzero
 * amplitude gradient can pass v1. It was not a continuity test.
 *
 * <h3>The replacement: a SCALING test (has teeth, proven in-probe)</h3>
 * A genuine offset jump J gives |f(-e)-f(+e)| ~ 2J + 2*e*|f'|, so S(e) = OLD(e)/e_rad blows up
 * as e shrinks (S ~ 2J/e). A continuous-but-sloped f gives S(e) = 2|f'| = const.
 * GATE_EQ_JUMP passes iff S does not grow as e shrinks over three decades.
 * GATE_EQ_SELFTEST feeds the same discriminator a synthetic 0.38 K jump and a synthetic
 * continuous control, and requires it to say FAIL / PASS respectively -- so the gate is shown to
 * still catch exactly what v1 caught.
 *
 * <p>GATE_EQ_AMP0 checks the invariant that ZonalTables:114 names as the hard constraint
 * (the equatorial annual harmonic must be zero). That invariant has teeth against the
 * historical defect (a table written with A(0)=0.5 fails it).
 *
 * <p><b>Accounting:</b> the frozen note ZonalTables:116 attributes the historical 0.38~0.87 K
 * reading to A(0)=0.5 alone. Under an even amplitude plus the odd phase flip, A(0)!=0 does NOT
 * produce a jump (it makes f(0)!=0 while f stays exactly odd). That attribution is therefore
 * NOT verified here and is recorded as OPEN, not adopted.
 */
public class P293 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    /** Sample offsets in DEGREES (v1 used 1e-4 deg; keep it first so the record stays comparable). */
    static final double[] EPS_DEG = {1e-4, 1e-5, 1e-6};
    static final double[] KS = {0.0, 0.15, 0.5, 1.0};
    static final int NTH = 8;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p293_report.txt"), "UTF-8");
        say("P293 v2: equator continuity (A(phi) observed table) -- criterion rewritten after P964");
        say("");

        // ---- 1. v1's diagnostic table, unchanged, so history stays comparable ----
        say("A. v1 statistic (kept as DIAGNOSTIC, no longer a criterion)  eps = 1e-4 deg");
        double epsDeg0 = EPS_DEG[0];
        double eps0 = Math.toRadians(epsDeg0);
        say(String.format(LF, "  %-8s %-8s %16s %16s %14s", "Theta", "kappa", "f(-eps)", "f(+eps)", "OLD jump K"));
        double worst0 = 0;
        for (int si = 0; si < NTH; si++) {
            double th = si * Math.PI / 4.0;
            for (double k : KS) {
                double a = Atmosphere.seasonalAnomaly(-eps0, k, th);
                double b = Atmosphere.seasonalAnomaly(+eps0, k, th);
                double jump = Math.abs(a - b);
                if (jump > worst0) worst0 = jump;
                say(String.format(LF, "  %-8.3f %-8.2f %16.9f %16.9f %14.9f", th, k, a, b, jump));
            }
        }
        say(String.format(LF, "  OLD_EQ_JUMP_DIAG=%.9f K   (v1 would call this FAIL because it demanded < 1e-9)", worst0));
        say("");

        // ---- 2. the SCALING test ----
        say("B. SCALING test: S(e) = OLD(e)/e_rad.  Jump => S ~ 2J/e (grows as e shrinks).  Slope => S = 2|f'| = const.");
        say(String.format(LF, "  %-12s %18s %18s", "eps(deg)", "OLD jump K", "S = OLD/e_rad"));
        double[] S = new double[EPS_DEG.length];
        for (int ei = 0; ei < EPS_DEG.length; ei++) {
            double er = Math.toRadians(EPS_DEG[ei]);
            double w = 0;
            for (int si = 0; si < NTH; si++) {
                double th = si * Math.PI / 4.0;
                for (double k : KS) {
                    double d = Math.abs(Atmosphere.seasonalAnomaly(-er, k, th) - Atmosphere.seasonalAnomaly(+er, k, th));
                    if (d > w) w = d;
                }
            }
            S[ei] = w / er;
            say(String.format(LF, "  %-12.0e %18.9f %18.6f", EPS_DEG[ei], w, S[ei]));
        }
        double grow = S[EPS_DEG.length - 1] / Math.max(1e-300, S[0]);
        say(String.format(LF, "  S(1e-6)/S(1e-4) = %.9f   (no jump => ~1.0 ; a jump J => ~100)", grow));
        boolean scalingOk = grow <= 1.0 + 1e-6;
        say(String.format(LF, "  GATE_EQ_JUMP=%s", scalingOk ? "PASS" : "FAIL"));
        say("");

        // ---- 3. even part / f(0) ----
        say("C. Even part (the true offset test) and f(0)");
        double wEven = 0, wF0 = 0;
        for (int si = 0; si < NTH; si++) {
            double th = si * Math.PI / 4.0;
            for (double k : KS) {
                double a = Atmosphere.seasonalAnomaly(-eps0, k, th);
                double b = Atmosphere.seasonalAnomaly(+eps0, k, th);
                if (Math.abs(a + b) > wEven) wEven = Math.abs(a + b);
                if (Math.abs(Atmosphere.seasonalAnomaly(0.0, k, th)) > wF0) wF0 = Math.abs(Atmosphere.seasonalAnomaly(0.0, k, th));
            }
        }
        say(String.format(LF, "  max |f(-eps)+f(+eps)| = %.3e K   (offset jump would be O(0.1 K))", wEven));
        say(String.format(LF, "  max |f(0)|              = %.3e K", wF0));
        say("");

        // ---- 4. the invariant ZonalTables:114 names: equatorial annual harmonic == 0 ----
        double aL0 = ZonalTables.aLand(0.0), aS0 = ZonalTables.aSea(0.0);
        say(String.format(LF, "D. Equatorial annual-harmonic amplitude (ZonalTables:114 hard constraint)"));
        say(String.format(LF, "  A_land(0) = %.12f   A_sea(0) = %.12f   (both must be exactly 0)", aL0, aS0));
        boolean amp0ok = Math.abs(aL0) < 1e-12 && Math.abs(aS0) < 1e-12;
        say(String.format(LF, "  GATE_EQ_AMP0=%s", amp0ok ? "PASS" : "FAIL"));
        say("");

        // ---- 5. instrument self-test: the discriminator must reject a real jump ----
        say("E. INSTRUMENT SELF-TEST: same discriminator on a synthetic jump and a synthetic control");
        double J0 = 0.38, slope = 13.25527;
        double[] Sj = new double[EPS_DEG.length], Sc = new double[EPS_DEG.length];
        for (int ei = 0; ei < EPS_DEG.length; ei++) {
            double er = Math.toRadians(EPS_DEG[ei]);
            Sj[ei] = Math.abs(jumpFn(-er, J0, slope) - jumpFn(+er, J0, slope)) / er;
            Sc[ei] = Math.abs(contFn(-er, slope) - contFn(+er, slope)) / er;
        }
        double gj = Sj[EPS_DEG.length - 1] / Sj[0];
        double gc = Sc[EPS_DEG.length - 1] / Sc[0];
        say(String.format(LF, "  synthetic JUMP  J=%.2f K : S goes %.3e -> %.3e  growth=%.1f  => discriminator says %s",
            J0, Sj[0], Sj[EPS_DEG.length - 1], gj, gj <= 1.0 + 1e-6 ? "PASS (BAD!)" : "FAIL (correct)"));
        say(String.format(LF, "  synthetic SLOPE (continuous) : S goes %.3e -> %.3e  growth=%.9f  => discriminator says %s",
            Sc[0], Sc[EPS_DEG.length - 1], gc, gc <= 1.0 + 1e-6 ? "PASS (correct)" : "FAIL (BAD!)"));
        boolean teeth = gj > 1.0 + 1e-6 && gc <= 1.0 + 1e-6;
        say(String.format(LF, "  GATE_EQ_SELFTEST=%s", teeth ? "PASS" : "FAIL"));
        say("");

        // ---- 6. one-sided derivative (unchanged) ----
        double dz = 1000.0;
        double th0 = 0.0;
        double dN = (Atmosphere.seasonalAnomaly(Math.toRadians(0.01), 0.5, th0) - Atmosphere.seasonalAnomaly(0.0, 0.5, th0)) / (0.01 / 90.0 * WorldContract.MAX_D);
        double dS = (Atmosphere.seasonalAnomaly(0.0, 0.5, th0) - Atmosphere.seasonalAnomaly(-Math.toRadians(0.01), 0.5, th0)) / (0.01 / 90.0 * WorldContract.MAX_D);
        say(String.format(LF, "  dT'/dz at equator: north %+.4e  south %+.4e K/m", dN, dS));
        say(String.format(LF, "  GATE_EQ_DERIV=%s", String.format(LF, "%+.4e", dN).equals(String.format(LF, "%+.4e", dS)) ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_EQ_DERIV_ULP=REVIEW  dN=%.17g  dS=%.17g  dN-dS=%.17g  判据：1 ULP = 浮点结合律噪声，非物理不连续；本门只做记录，不做判定", dN, dS, dN - dS));
        say("");

        say(String.format(LF, "  %-6s %10s %10s", "纬度", "A_land K", "A_sea K"));
        for (int lat = 0; lat <= 90; lat += 10) {
            say(String.format(LF, "  %-6d %10.2f %10.2f", lat, ZonalTables.aLand(lat), ZonalTables.aSea(lat)));
        }
        rep.close();
    }

    /** Synthetic field with a genuine offset jump J across x=0 (plus a continuous slope). */
    static double jumpFn(double x, double J, double slope) { return slope * x + 0.5 * J * Math.signum(x); }
    /** Synthetic continuous control: pure slope, no jump. */
    static double contFn(double x, double slope) { return slope * x; }

    static void say(String s) { System.out.println("[P293] " + s); rep.println("[P293] " + s); }
}