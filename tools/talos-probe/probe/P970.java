package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P970: MAGNITUDE check before wiring. ALPHA_SH = 0.40 was calibrated to the OLD floor form.
 *
 * Anchor (already established, SS251): GPCP + ETOPO1, ocean only, 20~62.5N, JJA,
 * band MINIMUM of the long-term mean = 1.84 mm/day.
 *
 * Four elements (SS636) written down:
 *   window    = ocean points, latitudes 20..62.5 N
 *   level     = surface precipitation rate
 *   phase     = JJA  (Atmosphere.theta(2 * DAYS_PER_YEAR / 4))
 *   statistic = band MINIMUM (per the recorded anchor)
 *
 * NOTE on hUp: samples are restricted to kappa < 0.02 and moisture() is called with elev = 0,
 * i.e. the PURE-OCEAN convention (depletion -> 1). BOTH forms use the same q, so the
 * comparison is internally consistent. This is recorded, not hidden.
 */
public class P970 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final double MMD = 86400.0 * 1000.0;

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p970_report.txt"), "UTF-8");
        double th = Atmosphere.theta(2.0 * WorldContract.DAYS_PER_YEAR / 4.0);
        say(rep, "P970: magnitude of the OLD floor vs the CONDENSATE form, 20~62.5N ocean, JJA");
        say(rep, String.format(LF, "  ALPHA_SH = %.3f   H_BL = %.1f m   GAMMA = %.6f K/m", PrecipField.ALPHA_SH, Atmosphere.H_BL, Atmosphere.GAMMA));
        say(rep, "");
        say(rep, String.format(LF, "  %-7s %7s %12s %12s %10s %10s", "lat", "nPts", "old pSh", "new P_c", "ratio", "min P_c"));
        double bandMinOld = 1e30, bandMinNew = 1e30, bandMinProd = 1e30;
        int nTot = 0;
        for (int latDeg = 20; latDeg <= 62; latDeg += 5) {
            int z = (int) ((double) latDeg / 90.0 * (WorldContract.Z_CYCLE / 2));
            double lat = WorldContract.latOf(z);
            double sOld = 0, sNew = 0; int n = 0; double minNew = 1e30;
            for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                double k = Atmosphere.kappaMemo(x, z, SD, PlateFieldCell(x, z));
                if (k > 0.02) continue;
                double Ts = Atmosphere.surfaceTemp(x, z, SD, PlateFieldCell(x, z), th);
                double qs = PrecipField.qSat(Ts);
                double q = PrecipField.moisture(Ts, 0.0, k);
                double[] u = Atmosphere.windAt(x, z, SD, PlateFieldCell(x, z), th, 500_000);
                double vEff = Math.sqrt(u[0] * u[0] + u[1] * u[1] + PrecipField.V_GUST * PrecipField.V_GUST);
                double chv = Atmosphere.cdOf(k) * vEff;
                double oldMm = PrecipField.ALPHA_SH * Atmosphere.RHO_AIR * chv * Math.max(0.0, (1.0 - k) * qs - q) / PrecipField.RHO_WATER * MMD;
                double tTh = Ts - Atmosphere.GAMMA * Atmosphere.H_BL;
                double qc = Math.max(0.0, q - PrecipField.qSat(tTh));
                double newMm = PrecipField.precip(qc, PrecipField.wStarK(Ts, qs, q, lat, k, vEff)) * MMD;
                double mmProd = PrecipField.mmPerDay(x, z, SD, PlateFieldCell(x, z), th, 500_000, true);
                if (mmProd < bandMinProd) bandMinProd = mmProd;
                sOld += oldMm; sNew += newMm; n++;
                if (newMm < minNew) minNew = newMm;
                if (oldMm < bandMinOld) bandMinOld = oldMm;
                if (newMm < bandMinNew) bandMinNew = newMm;
            }
            nTot += n;
            if (n > 0) {
                say(rep, String.format(LF, "  %-7d %7d %12.4f %12.4f %10.3f %10.4f", latDeg, n, sOld / n, sNew / n, (sNew / n) / Math.max(1e-12, sOld / n), minNew));
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  samples = %d", nTot));
        say(rep, String.format(LF, "  BAND MINIMUM  pSh alone = %.4f    FINAL P (mmPerDay) = %.4f    condensate P_c = %.4f   mm/day", bandMinOld, bandMinProd, bandMinNew));
        say(rep, String.format(LF, "  ANCHOR (GPCP+ETOPO1, same window/level/phase/statistic) = 1.84 mm/day"));
        say(rep, String.format(LF, "  ratio FINAL/anchor = %.3f    ratio condensate/anchor = %.3f    ratio pSh-only/anchor = %.3f", bandMinProd / 1.84, bandMinNew / 1.84, bandMinOld / 1.84));
        say(rep, "");
        say(rep, "  Reading: ALPHA_SH was fitted to the OLD form. If ratio-new differs from ratio-old,");
        say(rep, "  the new form needs its OWN calibration constant, and that constant must be stated");
        say(rep, "  as a calibration with this anchor -- not silently inherited.");
        rep.close();
    }

    static int PlateFieldCell(int x, int z) { return com.EyeOfHarmonyBuffer.sim.litho.PlateField.PLATE_CELL; }

    static void say(PrintStream rep, String s) { System.out.println("[P970] " + s); rep.println("[P970] " + s); }
}