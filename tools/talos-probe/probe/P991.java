package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P991: NEW ACCEPTANCE GATE candidate (SS680/SS681 coverage gap).  Tropical precipitation
 * vs the SAME-MASK GPCP anchor.  Unlike P975/P980 it does NOT re-derive a candidate form --
 * it measures PRODUCTION mmPerDay, so it judges whatever floor is active.
 *
 * Caliber (SS636 four elements), identical to refs/_eqpeak_masked.py:
 *   window    = OCEAN only (kappa < 0.02), band 0..25 N
 *   level     = surface precipitation rate
 *   phase     = JJA / DJF
 *   statistic = argmax of the zonal-mean profile (all longitudes within the mask)
 * Anchor (OCEAN mask): JJA 7.958 @8.75 N ; DJF 4.956 @6.25 N ; seasonal ratio 1.606
 *   (ETOPO1 land-fraction mask, resampled to the GPCP 2.5-deg grid)
 *
 * GATE_NTP_MAGNITUDE : JJA argmax / 7.958  in [0.5, 2.0]
 * GATE_NTP_SEASONAL  : (JJA/DJF) / 1.606    in [0.5, 2.0]
 * GATE_NTP_QC_POS    : argmax > 0 (a degenerate all-zero profile must not silently pass)
 */
public class P991 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final double A_JJA = 7.958, A_DJF = 4.956, A_SEA = 1.606;

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p991_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        int nBadRt = 0;
        for (int ld = 5; ld <= 85; ld += 5) { if (Math.abs(Math.toDegrees(WorldContract.latOf(WorldContract.zOfLat(ld))) - ld) > 1e-3) nBadRt++; }
        say(rep, "P991: tropical precipitation vs same-mask GPCP anchor (production mmPerDay)");
        say(rep, String.format(LF, "  COORD_ROUNDTRIP=%s  SHALLOW_FLOOR=%s  SHALLOW_CONDENSATE=%s",
            nBadRt == 0 ? "PASS" : "FAIL", PrecipField.SHALLOW_FLOOR, PrecipField.SHALLOW_CONDENSATE));
        String[] sn = {"JJA", "DJF"};
        double[] peak = new double[2]; double[] atLat = new double[2];
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "  === " + sn[s] + "  (mm/day, ocean kappa<0.02, all longitudes)");
            say(rep, String.format(LF, "    %-7s %6s %11s", "latN", "nPts", "P(mm/day)"));
            peak[s] = -1; atLat[s] = 0;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = WorldContract.zOfLat(latDeg);
                double sum = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k >= 0.02) continue;
                    sum += PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    n++;
                }
                if (n == 0) continue;
                double a = sum / n;
                if (a > peak[s]) { peak[s] = a; atLat[s] = latDeg; }
                say(rep, String.format(LF, "    %-7.1f %6d %11.3f", latDeg, n, a));
            }
            say(rep, String.format(LF, "    ARGMAX %.3f mm/day @ %.1f N", peak[s], atLat[s]));
        }
        say(rep, "");
        double rMag = peak[0] / A_JJA;
        double rSea = (peak[0] / Math.max(1e-12, peak[1])) / A_SEA;
        say(rep, String.format(LF, "  anchor(OCEAN): JJA %.3f  DJF %.3f  ratio %.3f", A_JJA, A_DJF, A_SEA));
        say(rep, String.format(LF, "  model        : JJA %.3f @%.1f  DJF %.3f @%.1f  ratio %.3f",
            peak[0], atLat[0], peak[1], atLat[1], peak[0] / Math.max(1e-12, peak[1])));
        say(rep, String.format(LF, "  GATE_NTP_MAGNITUDE=%s  ratio %.3f (must be in [0.5,2.0])", (rMag >= 0.5 && rMag <= 2.0) ? "PASS" : "FAIL", rMag));
        say(rep, String.format(LF, "  GATE_NTP_SEASONAL=%s   ratio %.3f (must be in [0.5,2.0])", (rSea >= 0.5 && rSea <= 2.0) ? "PASS" : "FAIL", rSea));
        say(rep, String.format(LF, "  GATE_NTP_QC_POS=%s     (JJA argmax %.3f > 0)", peak[0] > 0.0 ? "PASS" : "FAIL", peak[0]));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P991] " + s); rep.println("[P991] " + s); }
}