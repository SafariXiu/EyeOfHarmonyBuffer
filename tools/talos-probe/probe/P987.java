package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P987 (SS691 sec.6): localise the DJF tropical excess inside wEff.
 *  DIAG[4] = wBase (set inside wEff at :2253) ; DIAG[3] = wEff (set in mmPerDay at :2302).
 *  With SPLIT_ASCENT, wEff = max(0,wBase) + max(0,wLoc)  =>  DIAG[3] - max(0,DIAG[4]) is the
 *  positive part of wLoc.  Raw values, no unit conversion (state the units when known). */
public class P987 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p987_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        say(rep, "P987: wEff decomposition, tropical ocean (raw DIAG values, no unit conversion)");
        say(rep, "  SPLIT_ASCENT=" + PrecipField.SPLIT_ASCENT + "  WZM_FROM_TABLE=" + PrecipField.WZM_FROM_TABLE);
        String[] sn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "  === " + sn[s]);
            say(rep, String.format(LF, "    %-7s %6s %14s %14s %14s", "latN", "nPts", "DIAG3 wEff", "DIAG4 wBase", "wLoc+ (derived)"));
            double s3 = 0, s4 = 0, sL = 0; int nT = 0;
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = WorldContract.zOfLat(latDeg);
                double a3 = 0, a4 = 0, aL = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k >= 0.02) continue;
                    PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    double d3 = PrecipField.DIAG.get()[3];
                    double d4 = PrecipField.DIAG.get()[4];
                    a3 += d3; a4 += d4; aL += (d3 - Math.max(0.0, d4)); n++;
                }
                if (n == 0) continue;
                s3 += a3 / n; s4 += a4 / n; sL += aL / n; nT++;
                say(rep, String.format(LF, "    %-7.1f %6d %14.6e %14.6e %14.6e", latDeg, n, a3 / n, a4 / n, aL / n));
            }
            if (nT > 0) say(rep, String.format(LF, "    LAT-MEAN: wEff=%.6e  wBase=%.6e  wLoc+=%.6e", s3 / nT, s4 / nT, sL / nT));
        }
        say(rep, "");
        say(rep, "  Reading: if DIAG3 >> DIAG4 in DJF, the excess is in wLoc (divU / W_LOC_MAX).");
        say(rep, "  If DIAG4 itself is large in DJF but small in JJA, the excess is in wBase (wZm table / Hadley).");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P987] " + s); rep.println("[P987] " + s); }
}