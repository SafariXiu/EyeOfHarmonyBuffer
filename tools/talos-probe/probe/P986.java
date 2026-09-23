package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P986 (SS688.7 step 1): who supplies the tropical peak in each season?
 *  DIAG[8] = p before eddy ; DIAG[9] = p after eddy, before floor ; DIAG[10] = floor ; final = mmPerDay.
 *  All in mm/day.  Ocean (kappa<0.02), 0..25N. */
public class P986 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final double MMD = 86400.0 * 1000.0;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p986_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        say(rep, "P986: who supplies the tropical peak?  (mm/day, ocean, kappa<0.02, 0..25N)");
        say(rep, "  SIGMA_UP=" + PrecipField.SIGMA_UP + "  Z_CT_COND=" + PrecipField.Z_CT_COND);
        String[] sn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            double th = Atmosphere.theta(s == 0 ? 2.0 * WorldContract.DAYS_PER_YEAR / 4.0 : 0.0);
            say(rep, "");
            say(rep, "  === " + sn[s]);
            say(rep, String.format(LF, "    %-7s %6s %9s %9s %9s %9s %8s", "latN", "nPts", "DIAG8", "DIAG9", "DIAG10", "final", "whoWins"));
            double best = -1, at = 0; String who = "";
            for (double latDeg = 2.5; latDeg <= 25.0; latDeg += 2.5) {
                int z = WorldContract.zOfLat(latDeg);
                double s8 = 0, s9 = 0, s10 = 0, sf = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                    double k = Atmosphere.kappaMemo(x, z, SD, cell);
                    if (k >= 0.02) continue;
                    sf += PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    s8 += PrecipField.DIAG.get()[8] * MMD;
                    s9 += PrecipField.DIAG.get()[9] * MMD;
                    s10 += PrecipField.DIAG.get()[10] * MMD; n++;
                }
                if (n == 0) continue;
                double a8 = s8 / n, a9 = s9 / n, a10 = s10 / n, af = sf / n;
                String w = (a10 >= a9) ? "FLOOR" : "EDDY";
                say(rep, String.format(LF, "    %-7.1f %6d %9.3f %9.3f %9.3f %9.3f %8s", latDeg, n, a8, a9, a10, af, w));
                if (af > best) { best = af; at = latDeg; who = w; }
            }
            say(rep, String.format(LF, "    PEAK %.3f mm/day @ %.1f N   supplied by %s", best, at, who));
        }
        say(rep, "");
        say(rep, "  Reading: if the DJF peak is EDDY while the JJA peak is FLOOR, then the seasonal");
        say(rep, "  gate's failure is an EDDY-line defect, not a floor defect.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P986] " + s); rep.println("[P986] " + s); }
}