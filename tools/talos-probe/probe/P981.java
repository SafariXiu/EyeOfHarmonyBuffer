package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P981 (SS679 hypothesis 2): does the floor EVER get selected?  p = max(p, pFloor).
 * DIAG[9] = p after eddy, BEFORE the floor;  DIAG[10] = pFloor.  Selection <=> DIAG[10] > DIAG[9].
 * Run with SHALLOW_CONDENSATE = true (the SS677 literature form is currently ON).
 */
public class P981 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final Locale LF = Locale.ROOT;
    static final double MMD = 86400.0 * 1000.0;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p981_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        int nBadRt = 0;
        for (int ld = 5; ld <= 85; ld += 5) { if (Math.abs(Math.toDegrees(WorldContract.latOf(WorldContract.zOfLat(ld))) - ld) > 1e-3) nBadRt++; }
        say(rep, "P981: floor selection rate (p = max(p, pFloor))");
        say(rep, String.format(LF, "  COORD_ROUNDTRIP=%s  SHALLOW_FLOOR=%s  SHALLOW_CONDENSATE=%s",
            nBadRt == 0 ? "PASS" : "FAIL", PrecipField.SHALLOW_FLOOR, PrecipField.SHALLOW_CONDENSATE));
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %6s %11s %11s %11s %9s %9s", "latN", "nPts", "mean p9", "mean p10", "mean final", "selCount", "selRate"));
        for (int latDeg = 5; latDeg <= 65; latDeg += 5) {
            int z = WorldContract.zOfLat(latDeg);
            double th = Atmosphere.theta(2.0 * WorldContract.DAYS_PER_YEAR / 4.0);
            int n = 0, nSel = 0; double s9 = 0, s10 = 0, sFin = 0;
            for (int x = -23_000_000; x <= 23_000_000; x += 2_000_000) {
                double k = Atmosphere.kappaMemo(x, z, SD, cell);
                if (k >= 0.02) continue;
                double mm = PrecipField.mmPerDay(x, z, SD, cell, th, 500_000, true);
                double p9 = PrecipField.DIAG.get()[9] * MMD;
                double p10 = PrecipField.DIAG.get()[10] * MMD;
                if (p10 > PrecipField.DIAG.get()[9]) nSel++;
                s9 += p9; s10 += p10; sFin += mm; n++;
            }
            if (n == 0) continue;
            say(rep, String.format(LF, "  %-6d %6d %11.3f %11.3f %11.3f %9d %8.0f%%",
                latDeg, n, s9 / n, s10 / n, sFin / n, nSel, 100.0 * nSel / n));
        }
        say(rep, "");
        say(rep, "  Reading: if selRate is 0 almost everywhere, the floor is INERT and no gate can move.");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P981] " + s); rep.println("[P981] " + s); }
}