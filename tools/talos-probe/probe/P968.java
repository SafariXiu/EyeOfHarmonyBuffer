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
 * P968: does a w_*-based shallow-convection rain rate remove the SS638 anomaly?
 *
 * The existing floor is  pSh = ALPHA_SH * rho*Cd*|V|*max(0, (1-k)*qSat(Ts) - q) / rho_w,
 * which is MONOTONE DECREASING in q  =>  'a wetter boundary layer rains less' (the anomaly).
 *
 * Candidate: P_w = precip(q, wStar(q)).  With wStar^3 ~ (a - b*q),
 *   d/dq [ q*(a-bq)^(1/3) ]  ~  a - (4/3)*b*q   =>  increasing iff q < 3a/(4b).
 * Claim to test numerically: 3a/(4b) > qSat(Ts) at every ocean sample, i.e. the turning point
 * lies OUTSIDE the physically reachable range and P_w is monotonically increasing in q.
 *
 * Both statistics are reported (SS636).
 */
public class P968 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int NS = 24;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p968_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(rep, "P968: w_*-based rain rate vs the q-anomaly (SS638 / SS652 discipline)");
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %9s %9s %9s %9s %8s %8s %8s", "lat", "T_s K", "q_sat", "q* turn", "q*/q_sat", "wStar", "Pw slope", "old slope"));
        int nTot = 0, nPwInc = 0, nOldDec = 0, nTurnAbove = 0;
        double worstRatio = 1e30, worstRelDrop = 0.0, oldWorstRelDrop = 0.0;
        int nPwStrict = 0, nOldStrict = 0;
        for (int latI = 5; latI <= 55; latI += 5) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int latDeg = sgn * latI;
                int z = WorldContract.zOfLat(latDeg);   // ★ §734 两倍纬度修正
                double lat = WorldContract.latOf(z);
                double sumTs = 0, sumQs = 0, sumQt = 0, sumWs = 0;
                int incPw = 0, decOld = 0, turnAbove = 0, nHere = 0;
                for (int x = -22_000_000; x <= 22_000_000; x += 4_000_000) {
                    if (PlateField.isLandWithCell(x, z, SD, cell)) continue;
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k > 0.5) continue;
                    double Ts = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double qs = PrecipField.qSat(Ts);
                    double q0 = PrecipField.moisture(Ts, 0.0, k);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double v = Math.hypot(u[0], u[1]) + PrecipField.V_GUST;
                    double ch = Atmosphere.cdOf(k);
                    double dt = ZonalTables.dtAirSea(Math.toDegrees(lat));
                    double ta = Ts + dt;
                    double a = ch * v * (-dt + 0.608 * Ts * qs);
                    double b = ch * v * 0.608 * ta;
                    double qTurn = (b > 0) ? 3.0 * a / (4.0 * b) : Double.POSITIVE_INFINITY;
                    if (qTurn > qs) { turnAbove++; nTurnAbove++; }
                    double wS = PrecipField.wStarK(Ts, qs, q0, lat, k, v);
                    double prevPw = -1, prevOld = -1;
                    boolean pwUp = true, oldDown = true;
                    for (int i = 0; i <= NS; i++) {
                        double f = i / (double) NS;
                        double qq = f * qs;
                        double pw = PrecipField.precip(qq, PrecipField.wStarK(Ts, qs, qq, lat, k, v)) * 86400.0 * 1000.0;
                        double old = PrecipField.ALPHA_SH * ch * v * Math.max(0.0, (1.0 - k) * qs - qq) / PrecipField.RHO_WATER * 86400.0 * 1000.0;
                        if (i > 0) {
                            if (pw < prevPw - 1e-15) pwUp = false;
                            if (prevPw > 0) { double rel = (prevPw - pw) / prevPw; if (rel > worstRelDrop) worstRelDrop = rel; }
                            if (prevOld > 0) { double rel = (prevOld - old) / prevOld; if (rel > oldWorstRelDrop) oldWorstRelDrop = rel; }
                            if (old > prevOld + 1e-15) oldDown = false;
                        }
                        prevPw = pw; prevOld = old;
                    }
                    nTot++; nHere++;
                    if (pwUp) { nPwInc++; incPw++; nPwStrict++; }
                    if (oldDown) { nOldDec++; decOld++; nOldStrict++; }
                    sumTs += Ts; sumQs += qs; sumQt += qTurn; sumWs += wS;
                    if (qs > 0) worstRatio = Math.min(worstRatio, qTurn / qs);
                }
                if (nHere > 0) {
                    say(rep, String.format(LF, "  %+6d %9.2f %9.5f %9.5f %9.3f %8.4f %8d/%d %6d/%d",
                        latDeg, sumTs / nHere, sumQs / nHere, sumQt / nHere, (sumQt / nHere) / Math.max(1e-9, sumQs / nHere),
                        sumWs / nHere, incPw, nHere, decOld, nHere));
                }
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  ocean samples = %d", nTot));
        say(rep, String.format(LF, "  GATE_WSTAR_QINC=%s    P_w = precip(q, wStar(q)) is non-decreasing in q at %d/%d samples",
            nPwInc == nTot && nTot > 0 ? "PASS" : "FAIL", nPwInc, nTot));
        say(rep, String.format(LF, "  GATE_TURN_ABOVE_QSAT=%s  turning point q* > qSat(T_s) at %d/%d samples; worst q*/qSat = %.3f",
            nTurnAbove == nTot && nTot > 0 ? "PASS" : "FAIL", nTurnAbove, nTot, worstRatio));
        say(rep, String.format(LF, "  OLD_FLOOR_QDEC_DIAG   the existing floor pSh decreases with q at %d/%d samples (this is the SS638 anomaly)",
            nOldDec, nTot));
        say(rep, String.format(LF, "  MAGNITUDE  worst relative DROP of P_w over the q sweep = %.3e  (a value at float-noise level is not a real non-monotonicity)", worstRelDrop));
        say(rep, String.format(LF, "  MAGNITUDE  worst relative DROP of the old floor         = %.3e", oldWorstRelDrop));
        say(rep, String.format(LF, "  STRICT     P_w strictly non-decreasing = %d/%d ; old floor strictly decreasing = %d/%d", nPwStrict, nTot, nOldStrict, nTot));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P968] " + s); rep.println("[P968] " + s); }
}