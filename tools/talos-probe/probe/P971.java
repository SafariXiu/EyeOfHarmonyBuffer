package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P971: WHY does the condensate form have a collapsed tropical seasonal cycle (arm B ratio 1.06
 * vs GPCP 1.61)?  Hypothesis: q_c = q - qSat(tTh) is a DIFFERENCE of two quantities that both
 * scale with Clausius-Clapeyron, so its RELATIVE seasonal swing is far smaller than q's.
 *
 * This is checked with the PRODUCTION qSat and the PRODUCTION tTh construction.
 */
public class P971 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p971_report.txt"), "UTF-8");
        say(rep, "P971: seasonal sensitivity of q vs q_c (condensate)");
        say(rep, "  tTh = T_s - GAMMA*H_BL with GAMMA=" + Atmosphere.GAMMA + "  H_BL=" + Atmosphere.H_BL);
        say(rep, "");
        say(rep, String.format(LF, "  %8s %12s %12s %12s %10s %10s", "T_s K", "q_sat(T_s)", "q=0.8*qsat", "qSat(tTh)", "q_c", "q_c/q"));
        double[] ts = {288.0, 290.0, 295.0, 300.0, 302.0, 304.0};
        double[] qa = new double[ts.length], qca = new double[ts.length];
        for (int i = 0; i < ts.length; i++) {
            double T = ts[i];
            double qs = PrecipField.qSat(T);
            double q = 0.8 * qs;
            double tTh = T - Atmosphere.GAMMA * Atmosphere.H_BL;
            double qc = Math.max(0.0, q - PrecipField.qSat(tTh));
            qa[i] = q; qca[i] = qc;
            say(rep, String.format(LF, "  %8.1f %12.5f %12.5f %12.5f %10.5f %10.4f", T, qs, q, PrecipField.qSat(tTh), qc, qc / q));
        }
        double dq = (qa[ts.length - 1] - qa[0]) / qa[0];
        double dqc = (qca[ts.length - 1] - qca[0]) / Math.max(1e-12, qca[0]);
        say(rep, "");
        say(rep, String.format(LF, "  over T_s = %.0f -> %.0f K:", ts[0], ts[ts.length - 1]));
        say(rep, String.format(LF, "    relative swing of q   = %+.4f", dq));
        say(rep, String.format(LF, "    relative swing of q_c = %+.4f", dqc));
        say(rep, String.format(LF, "    ratio q_c/q swing = %.4f   (>>1 would mean the condensate is as seasonal as q)", Math.abs(dqc) / Math.max(1e-12, Math.abs(dq))));
        say(rep, "");
        say(rep, String.format(LF, "  GATE_Qc_INSENSITIVE=%s   (the condensate's relative swing is < 25%% of q's)",
            Math.abs(dqc) < 0.25 * Math.abs(dq) ? "PASS" : "FAIL"));
        say(rep, "  Reading: if PASS, the collapsed seasonal ratio of arm B (1.06 vs GPCP 1.61) is a");
        say(rep, "  STRUCTURAL property of q_c, not a calibration problem.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P971] " + s); rep.println("[P971] " + s); }
}