package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P969: is the CONDENSATE-based form structurally monotone in q (unlike P968's q*wStar)?
 *
 * P968: P_w = precip(q, wStar(q)) is non-decreasing at 150/152, worst relative drop 1.15%.
 * The residue exists because the supply factor f(q)=q has f'/f = 1/q, which is too small at
 * large q.  Physically the supply is the CONDENSATE q_c = q - qSat(T_throttling), not total q.
 *
 * The model already builds that level (BLQ_GATE):  tTh = tSfc - GAMMA*H_BL, and uses qSat(tTh).
 *
 * d/dq [ (q-qc0)*(a-bq)^(1/3) ] > 0  <=>  3(a-bq) > b(q-qc0)  <=>  q < q* + qc0/4
 * and f'/f = 1/(q-qc0) is larger than 1/q wherever qc0 > 0.  So the margin improves.
 * Claim to test: P_c is non-decreasing over the WHOLE reachable range [0, qSat(T_s)] at
 * every ocean sample, with the worst relative drop at float-noise level.
 */
public class P969 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int NS = 40;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p969_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(rep, "P969: condensate-based supply q_c = q - qSat(tThrottling) vs the q-anomaly");
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %9s %9s %9s %9s %9s %8s %8s", "lat", "T_s K", "q_sat", "tTh K", "qSat(tTh)", "qc0/q_s", "Pc drop", "Pw drop"));
        int nTot = 0, nPcInc = 0, nPwInc = 0;
        double worstPc = 0, worstPw = 0;
        for (int latI = 5; latI <= 55; latI += 5) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int latDeg = sgn * latI;
                int z = WorldContract.zOfLat(latDeg);   // ★ §734 两倍纬度修正
                double lat = WorldContract.latOf(z);
                double sTs = 0, sQs = 0, sTth = 0, sQc0 = 0;
                int incPc = 0, incPw = 0, nHere = 0;
                double herePc = 0, herePw = 0;
                for (int x = -22_000_000; x <= 22_000_000; x += 4_000_000) {
                    if (PlateField.isLandWithCell(x, z, SD, cell)) continue;
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k > 0.5) continue;
                    double Ts = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double qs = PrecipField.qSat(Ts);
                    double tTh = Ts - Atmosphere.GAMMA * Atmosphere.H_BL;
                    double qc0 = PrecipField.qSat(tTh);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double v = Math.hypot(u[0], u[1]) + PrecipField.V_GUST;
                    boolean pcUp = true, pwUp = true;
                    double prevPc = -1, prevPw = -1;
                    for (int i = 0; i <= NS; i++) {
                        double qq = (i / (double) NS) * qs;
                        double w = PrecipField.wStarK(Ts, qs, qq, lat, k, v);
                        double pc = PrecipField.precip(Math.max(0.0, qq - qc0), w) * 86400.0 * 1000.0;
                        double pw = PrecipField.precip(qq, w) * 86400.0 * 1000.0;
                        if (i > 0) {
                            if (prevPc > 0) { double r = (prevPc - pc) / prevPc; if (r > 0) pcUp = false; if (r > herePc) herePc = r; }
                            if (prevPw > 0) { double r = (prevPw - pw) / prevPw; if (r > 0) pwUp = false; if (r > herePw) herePw = r; }
                        }
                        prevPc = pc; prevPw = pw;
                    }
                    nTot++; nHere++;
                    if (pcUp) { nPcInc++; incPc++; }
                    if (pwUp) { nPwInc++; incPw++; }
                    if (herePc > worstPc) worstPc = herePc;
                    if (herePw > worstPw) worstPw = herePw;
                    sTs += Ts; sQs += qs; sTth += tTh; sQc0 += qc0;
                }
                if (nHere > 0) {
                    say(rep, String.format(LF, "  %+6d %9.2f %9.5f %9.2f %9.5f %9.3f %7d/%d %7d/%d",
                        latDeg, sTs / nHere, sQs / nHere, sTth / nHere, sQc0 / nHere, (sQc0 / nHere) / Math.max(1e-9, sQs / nHere),
                        incPc, nHere, incPw, nHere));
                }
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  ocean samples = %d", nTot));
        say(rep, String.format(LF, "  CONDENSATE  P_c strictly non-decreasing = %d/%d ; worst relative drop = %.3e", nPcInc, nTot, worstPc));
        say(rep, String.format(LF, "  TOTAL-q     P_w strictly non-decreasing = %d/%d ; worst relative drop = %.3e   (P968 comparison)", nPwInc, nTot, worstPw));
        say(rep, String.format(LF, "  GATE_COND_QINC=%s   (condensate form non-decreasing at 100%% of samples)",
            nPcInc == nTot && nTot > 0 ? "PASS" : "FAIL"));
        say(rep, String.format(LF, "  GATE_COND_MAG=%s    (worst relative drop at float-noise level, < 1e-12)",
            worstPc < 1e-12 ? "PASS" : "FAIL"));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P969] " + s); rep.println("[P969] " + s); }
}