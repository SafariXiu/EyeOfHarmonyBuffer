package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P973 (SS660 step 1): how much tropical rain does the SHORT CIRCUIT suppress?
 *
 * precip(q, w) returns 0 whenever w <= 0.  P972 showed the circulation term (DIAG[8]) is
 * bit-exactly 0.000 over 7.5..25N in JJA.  This probe measures, at the same production points:
 *   fracNeg   = fraction of points where wEff <= 0 (the short circuit fires)
 *   pAbs      = precip(q, |wEff|)      -- what the downdraft branch would give if not zeroed
 *   pWstar    = precip(q, wStarK(...)) -- the convective velocity scale alternative
 *   pSh, pC   = the two floor forms, for scale
 * All mm/day, ocean-only (kappa<0.02), production mmPerDay() supplies wEff and q via DIAG.
 */
public class P973 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final double MMD = 86400.0 * 1000.0;

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p973_report.txt"), "UTF-8");
        say(rep, "P973: how much tropical rain does the short circuit (wEff<=0 => 0) suppress?");
        String[] sn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "=== " + sn[s] + " ===");
            say(rep, String.format(LF, "  %-7s %6s %9s %11s %11s %11s %11s %11s", "latN", "nPts", "fracNeg", "pAbs", "pWstar", "pSh(A)", "pC(B)", "pFinalA"));
            int totN = 0, totNeg = 0; double gAbs = 0, gWs = 0, gSh = 0, gC = 0, gFin = 0;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = (int) (latDeg / 90.0 * (ZC / 2));
                double lat = WorldContract.latOf(z);
                int n = 0, nNeg = 0; double sAbs = 0, sWs = 0, sSh = 0, sC = 0, sFin = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    int cell = PlateField.PLATE_CELL;
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k > 0.02) continue;
                    double fin = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    double wE = PrecipField.DIAG.get()[3];
                    double q = PrecipField.DIAG.get()[5];
                    double pSh = PrecipField.DIAG.get()[10] * MMD;
                    double Ts = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                    double qs = PrecipField.qSat(Ts);
                    double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                    double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                    double pAbs = PrecipField.precip(q, Math.abs(wE)) * MMD;
                    double pWs = PrecipField.precip(q, PrecipField.wStarK(Ts, qs, q, lat, k, vEff)) * MMD;
                    double tTh = Ts - Atmosphere.GAMMA * Atmosphere.H_BL;
                    double qc = Math.max(0.0, q - PrecipField.qSat(tTh));
                    double pC = 0.195 * PrecipField.precip(qc, PrecipField.wStarK(Ts, qs, q, lat, k, vEff)) * MMD;
                    if (wE <= 0.0) nNeg++;
                    sAbs += pAbs; sWs += pWs; sSh += pSh; sC += pC; sFin += fin; n++;
                }
                if (n == 0) continue;
                totN += n; totNeg += nNeg;
                gAbs += sAbs; gWs += sWs; gSh += sSh; gC += sC; gFin += sFin;
                say(rep, String.format(LF, "  %-7.1f %6d %9.2f %11.3f %11.3f %11.3f %11.3f %11.3f",
                    latDeg, n, (double) nNeg / n, sAbs / n, sWs / n, sSh / n, sC / n, sFin / n));
            }
            say(rep, String.format(LF, "  BAND: nPts=%d  frac(wEff<=0)=%.3f   means: pAbs=%.3f  pWstar=%.3f  pSh=%.3f  pC=%.3f  pFinalA=%.3f",
                totN, (double) totNeg / Math.max(1, totN), gAbs / Math.max(1, totN), gWs / Math.max(1, totN),
                gSh / Math.max(1, totN), gC / Math.max(1, totN), gFin / Math.max(1, totN)));
        }
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P973] " + s); rep.println("[P973] " + s); }
}