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
 * P966: does separating T_a from T_s fix the w_* defect found in SS639?
 *
 * SS639 established: with a single temperature (T_a == T_s) the surface virtual-potential-
 * temperature flux degenerates to C_H*|V|*0.608*theta*(q_sat(T_s) - q), which goes to ZERO as
 * q -> q_sat. That is physically backwards (a saturated marine boundary layer is the MOST
 * favourable case for convection) and it is the reason w_* inherited the SS638 anomaly.
 *
 * With T_a = T_s + DT (DT < 0 over ocean, SS645) the flux retains a thermal part
 * theta(T_s) - theta(T_a) = (T_s - T_a)*(1 + 0.608*q) which is independent of q, so at
 * saturation the flux is C_H*|V|*(T_s - T_a)*(1 + 0.608*q_sat) > 0.
 *
 * This probe measures both forms on the same ocean samples, BEFORE any production change.
 */
public class P966 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double G = 9.80665, EPS_V = 0.608;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p966_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(rep, "P966: T_a != T_s separation -- does it fix the SS639 w_* defect?");
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %7s %8s %8s %8s %9s %9s %9s %9s", "lat", "nOcean", "T_s K", "DT K", "q_s", "q_air", "w_OLD", "w_NEW", "w_SAT"));
        int nPts = 0, nDtNeg = 0, nWNewPos = 0, nWsatPos = 0, nWOldZeroAtSat = 0;
        double worstDt = 0, worstW = 1e30;
        for (int latI = 5; latI <= 55; latI += 5) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                int latDeg = sgn * latI;
                int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
                double lat = WorldContract.latOf(z);
                double dt = ZonalTables.dtAirSea(Math.toDegrees(lat));
                if (dt < 0) nDtNeg++; else worstDt = Math.max(worstDt, dt);
                int nOc = 0;
                double sTs = 0, sQ = 0, sQa = 0, sWo = 0, sWn = 0, sWs = 0;
                for (int x = -22_000_000; x <= 22_000_000; x += 4_000_000) {
                    if (PlateField.isLandWithCell(x, z, SD, cell)) continue;
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k > 0.5) continue;
                    double Ts = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double Ta = Ts + dt;
                    double qs = PrecipField.qSat(Ts);
                    double qo = PrecipField.moisture(Ts, 0.0, k);   // 648: q anchors on T_s (RH_SEA is the SEA-surface RH)
                    double qa = qo;
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double v = Math.hypot(u[0], u[1]) + PrecipField.V_GUST;
                    double ch = Atmosphere.cdOf(k);
                    double thvS = Ts * (1.0 + EPS_V * qs);
                    double fluxNew = PrecipField.surfaceBuoyancyFluxK(Ts, qs, qo, lat, k, v);
                    double fluxOld = ch * v * (thvS - Ts * (1.0 + EPS_V * qo));
                    double fluxSat = ch * v * (thvS - Ta * (1.0 + EPS_V * qs));
                    double fluxSatOld = ch * v * (thvS - Ts * (1.0 + EPS_V * qs));
                    double wNew = PrecipField.wStarK(Ts, qs, qo, lat, k, v);
                    double wOld = fluxOld > 0 ? Math.cbrt((G / Ts) * Atmosphere.H_BL * fluxOld) : Double.NaN;
                    double wSat = fluxSat > 0 ? Math.cbrt((G / Ts) * Atmosphere.H_BL * fluxSat) : Double.NaN;
                    nPts++; nOc++;
                    if (!Double.isNaN(wNew) && wNew > 0) nWNewPos++;
                    if (!Double.isNaN(wSat) && wSat > 0) nWsatPos++;
                    if (fluxSatOld <= 0) nWOldZeroAtSat++;
                    if (!Double.isNaN(wNew) && wNew < worstW) worstW = wNew;
                    sTs += Ts; sQ += qs; sQa += qa; sWo += wOld; sWn += wNew; sWs += wSat;
                }
                if (nOc > 0) {
                    say(rep, String.format(LF, "  %+6d %7d %8.2f %+8.3f %8.5f %9.5f %9.4f %9.4f %9.4f",
                        latDeg, nOc, sTs / nOc, dt, sQ / nOc, sQa / nOc, sWo / nOc, sWn / nOc, sWs / nOc));
                }
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  ocean samples = %d   latitudes with DT<0 = %d/22   worst(nonneg) DT = %.3e", nPts, nDtNeg, worstDt));
        say(rep, String.format(LF, "  smallest w_NEW = %.4f m/s", worstW));
        say(rep, String.format(LF, "  GATE_DT_SIGN=%s      (DT must be < 0 at every ocean latitude)", nDtNeg == 22 && worstDt == 0.0 ? "PASS" : "FAIL"));
        say(rep, String.format(LF, "  GATE_WS_POSITIVE=%s  (w_NEW > 0 at every ocean sample: %d/%d)", nWNewPos == nPts && nPts > 0 ? "PASS" : "FAIL", nWNewPos, nPts));
        say(rep, String.format(LF, "  GATE_WS_SAT_POS=%s   (w at the SATURATED limit > 0: %d/%d)", nWsatPos == nPts && nPts > 0 ? "PASS" : "FAIL", nWsatPos, nPts));
        say(rep, String.format(LF, "  OLD_FORM_AT_SAT_DIAG  the single-temperature form gives flux <= 0 at saturation in %d/%d samples (this is the SS639 defect)", nWOldZeroAtSat, nPts));
        say(rep, "");
        say(rep, "  Reading: w_NEW must be > 0 everywhere AND w_SAT must be > 0 (the SS639 fix).");
        say(rep, "  w_NEW still decreases with q -- that is correct physics (a moister BL has less surface");
        say(rep, "  buoyancy flux). What SS639 called wrong was w -> 0 at saturation, not the q-slope.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P966] " + s); rep.println("[P966] " + s); }
}