package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P972 (SS659 step 1): whose SPATIAL structure sets the tropical peak -- the floor, or the
 * circulation?  Per-latitude profiles in the 0..25N band, JJA and DJF, ocean-only (kappa<0.02).
 *
 * Columns, all mm/day:
 *   pPreFloor = DIAG[8] after a production mmPerDay() call (pre-eddy, pre-floor)
 *   pSh       = DIAG[10] after the same call (arm A floor, production value)
 *   pC        = arm B floor recomputed inline with ALPHA_COND = 0.195
 *   pFinal    = the mmPerDay() return value (arm A: max(pPreFloor, pSh) + eddy)
 *
 * The question: does the ARGMAX latitude of pSh (and of pC) move between JJA and DJF?
 * Arm A's PEAK moved 10.8 -> 3.6; arm B's did not move at all (SS658). If the floor's own
 * argmax does not move, the flat seasonal ratio of arm B is a floor-structure effect.
 */
public class P972 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final double MMD = 86400.0 * 1000.0;

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p972_report.txt"), "UTF-8");
        say(rep, "P972: tropical 0..25N per-latitude profiles -- floor structure vs circulation");
        String[] sn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "=== " + sn[s] + " ===");
            say(rep, String.format(LF, "  %-7s %6s %11s %11s %11s %11s", "latN", "nPts", "pPreFloor", "pSh(A)", "pC(B)", "pFinal(A)"));
            double bestSh = -1, bestC = -1, bestPre = -1, bestFin = -1;
            double atSh = 0, atC = 0, atPre = 0, atFin = 0;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = WorldContract.zOfLat(latDeg);   // ★ §734 两倍纬度修正
                double lat = WorldContract.latOf(z);
                double sPre = 0, sSh = 0, sC = 0, sFin = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    int cell = PlateField.PLATE_CELL;
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k > 0.02) continue;
                    double fin = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    double pSh = PrecipField.DIAG.get()[10] * MMD;
                    double pPre = PrecipField.DIAG.get()[8] * MMD;
                    double Ts = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double qs = PrecipField.qSat(Ts);
                    double q = PrecipField.moisture(Ts, 0.0, k);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                    double tTh = Ts - Atmosphere.GAMMA * Atmosphere.H_BL;
                    double qc = Math.max(0.0, q - PrecipField.qSat(tTh));
                    double pC = 0.195 * PrecipField.precip(qc, PrecipField.wStarK(Ts, qs, q, lat, k, vEff)) * MMD;
                    sPre += pPre; sSh += pSh; sC += pC; sFin += fin; n++;
                }
                if (n == 0) continue;
                double aPre = sPre / n, aSh = sSh / n, aC = sC / n, aFin = sFin / n;
                if (aSh > bestSh) { bestSh = aSh; atSh = latDeg; }
                if (aC > bestC) { bestC = aC; atC = latDeg; }
                if (aPre > bestPre) { bestPre = aPre; atPre = latDeg; }
                if (aFin > bestFin) { bestFin = aFin; atFin = latDeg; }
                say(rep, String.format(LF, "  %-7.1f %6d %11.3f %11.3f %11.3f %11.3f", latDeg, n, aPre, aSh, aC, aFin));
            }
            say(rep, String.format(LF, "  ARGMAX lat:  preFloor %.1f    pSh(A) %.1f    pC(B) %.1f    pFinal(A) %.1f", atPre, atSh, atC, atFin));
            say(rep, String.format(LF, "  ARGMAX val:  preFloor %.3f    pSh(A) %.3f    pC(B) %.3f    pFinal(A) %.3f", bestPre, bestSh, bestC, bestFin));
        }
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P972] " + s); rep.println("[P972] " + s); }
}