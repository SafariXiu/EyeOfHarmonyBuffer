package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P982 (SS680 sec.4): old floor vs new floor, same points.  Why did P296 (35-70N) not move?
 *  DIAG[10] = the ACTIVE floor (SS677 form, switch true).  The OLD floor is recomputed inline.
 *  Units: BOTH in mm/day (SS680 rule: state the units of every compared pair). */
public class P982 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final double MMD = 86400.0 * 1000.0;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p982_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        say(rep, "P982: OLD floor vs NEW floor at the same ocean points (both mm/day)");
        say(rep, "  SHALLOW_FLOOR=" + PrecipField.SHALLOW_FLOOR + "  SHALLOW_CONDENSATE=" + PrecipField.SHALLOW_CONDENSATE);
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %6s %11s %11s %9s", "latN", "nPts", "OLD(mm/d)", "NEW(mm/d)", "NEW/OLD"));
        for (int latDeg = 5; latDeg <= 70; latDeg += 5) {
            int z = WorldContract.zOfLat(latDeg);
            double th = Atmosphere.theta(2.0 * WorldContract.DAYS_PER_YEAR / 4.0);
            int n = 0; double sOld = 0, sNew = 0;
            for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                double k = Atmosphere.kappaMemo(x, z, SD, cell);
                if (k >= 0.02) continue;
                PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                double q = PrecipField.DIAG.get()[5];
                double tSfc = Atmosphere.surfaceTemp(x, z, SD, cell, th);
                double qs = PrecipField.qSat(tSfc);
                double[] u = Atmosphere.windAt(x, z, SD, cell, th, 500_000);
                double sp = Math.hypot(u[0], u[1]);
                double vEff = Math.sqrt(sp * sp + PrecipField.V_GUST * PrecipField.V_GUST);
                double betaF = 1.0 - Atmosphere.clamp01(k);
                double eSh = Atmosphere.RHO_AIR * Atmosphere.cdOf(k) * vEff * Math.max(0.0, betaF * qs - q);
                double oldF = PrecipField.ALPHA_SH * eSh / PrecipField.RHO_WATER * MMD;   // mm/day
                double newF = PrecipField.DIAG.get()[10] * MMD;                          // mm/day
                sOld += oldF; sNew += newF; n++;
            }
            if (n == 0) continue;
            say(rep, String.format(LF, "  %-6d %6d %11.3f %11.3f %9.3f", latDeg, n, sOld / n, sNew / n, (sNew / n) / Math.max(1e-9, sOld / n)));
        }
        say(rep, "");
        say(rep, "  Reading: P296's domain is 35-70N.  If NEW/OLD ~ 1 there, its ratios cannot move");
        say(rep, "  even though the floor is active  ->  explains GATE_DIFFS=0 for P296.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P982] " + s); rep.println("[P982] " + s); }
}