package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P983 (SS684.5): reproduce CALIBERS item 11.  33.8..46.3N, JJA, ALL longitudes.
 *  Reports DIAG[8] (pre-eddy), DIAG[9] (post-eddy, pre-floor), DIAG[10] (floor) and the final,
 *  split land / ocean / combined.  Item 11 claims: main term 0.013..0.076, total 0.569..0.590. */
public class P983 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final double MMD = 86400.0 * 1000.0;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p983_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(2.0 * WorldContract.DAYS_PER_YEAR / 4.0);
        say(rep, "P983: reproduce CALIBERS item 11   (JJA, 33.8..46.3N, ALL longitudes)");
        say(rep, "  SHALLOW_FLOOR=" + PrecipField.SHALLOW_FLOOR + "  SHALLOW_CONDENSATE=" + PrecipField.SHALLOW_CONDENSATE);
        double[] lats = {33.8, 36.3, 38.8, 41.3, 43.8, 46.3};
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %-7s %6s %11s %11s %11s %11s", "latN", "side", "nPts", "DIAG8", "DIAG9", "DIAG10", "final"));
        double a8 = 0, a9 = 0, a10 = 0, af = 0; int an = 0;
        double o8 = 0, o9 = 0, o10 = 0, of = 0; int on = 0;
        double l8 = 0, l9 = 0, l10 = 0, lf = 0; int ln = 0;
        for (double latDeg : lats) {
            int z = WorldContract.zOfLat(latDeg);
            for (int side = 0; side < 3; side++) {
                double s8 = 0, s9 = 0, s10 = 0, sf = 0; int n = 0;
                for (int x = -23_000_000; x <= 23_000_000; x += 1_000_000) {
                    boolean land = PlateField.isLandWithCell(x, z, SD, cell);
                    if (side == 1 && !land) continue;
                    if (side == 2 && land) continue;
                    double fin = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                    s8 += PrecipField.DIAG.get()[8] * MMD;
                    s9 += PrecipField.DIAG.get()[9] * MMD;
                    s10 += PrecipField.DIAG.get()[10] * MMD;
                    sf += fin; n++;
                }
                if (n == 0) continue;
                String nm = side == 0 ? "ALL" : (side == 1 ? "LAND" : "OCEAN");
                say(rep, String.format(LF, "  %-6.1f %-7s %6d %11.4f %11.4f %11.4f %11.4f", latDeg, nm, n, s8 / n, s9 / n, s10 / n, sf / n));
                if (side == 0) { a8 += s8; a9 += s9; a10 += s10; af += sf; an += n; }
                if (side == 1) { l8 += s8; l9 += s9; l10 += s10; lf += sf; ln += n; }
                if (side == 2) { o8 += s8; o9 += s9; o10 += s10; of += sf; on += n; }
            }
        }
        say(rep, "");
        say(rep, String.format(LF, "  BAND MEAN  ALL  : DIAG8=%.4f  DIAG9=%.4f  DIAG10=%.4f  final=%.4f  (n=%d)", a8 / an, a9 / an, a10 / an, af / an, an));
        say(rep, String.format(LF, "  BAND MEAN  LAND : DIAG8=%.4f  DIAG9=%.4f  DIAG10=%.4f  final=%.4f  (n=%d)", l8 / Math.max(1, ln), l9 / Math.max(1, ln), l10 / Math.max(1, ln), lf / Math.max(1, ln), ln));
        say(rep, String.format(LF, "  BAND MEAN  OCEAN: DIAG8=%.4f  DIAG9=%.4f  DIAG10=%.4f  final=%.4f  (n=%d)", o8 / Math.max(1, on), o9 / Math.max(1, on), o10 / Math.max(1, on), of / Math.max(1, on), on));
        say(rep, "");
        say(rep, "  item 11 reference: main term 0.013..0.076 ; total 0.569..0.590");
        say(rep, String.format(LF, "  MAIN_TERM_IN_RANGE (DIAG8 ALL in [0.013,0.076]) = %s", (a8 / an >= 0.013 && a8 / an <= 0.076) ? "YES" : "NO"));
        say(rep, String.format(LF, "  TOTAL_IN_RANGE    (final ALL in [0.569,0.590]) = %s", (af / an >= 0.569 && af / an <= 0.590) ? "YES" : "NO"));
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P983] " + s); rep.println("[P983] " + s); }
}