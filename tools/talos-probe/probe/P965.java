package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P965: WHY does P292 accept only 4/48 rows? Count each rejection cause per latitude. */
public class P965 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final double TAU0 = 0.0685, H_T = 4000.0, A_H = 1.9e4, H = 5000.0, W = 100_000.0;
    static final int[] LATS = {10, 20, 30, 45, -10, -20, -30, -45};
    static final int NQ = 6;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p965_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        int cell = PlateField.PLATE_CELL;
        GyreRow.BandedWind band = new GyreRow.BandedWind(TAU0, ZC);
        say(rep, "P965: P292 row-qualification audit (why 4/48)");
        say(rep, "");
        say(rep, String.format(LF, "  %-6s %-4s %-7s %12s %12s %12s %12s %12s %s",
            "lat", "q", "valid", "maxRow", "eastX", "westX", "width", "2pi*delta", "verdict"));
        int[] cause = new int[5];
        for (int li = 0; li < LATS.length; li++) {
            int z = WorldContract.zOfLat(LATS[li]);
            for (int q = 0; q < NQ; q++) {
                GyreRow.Params p = new GyreRow.Params();
                p.h = H; p.rhoH = 1025.0 * H_T; p.aH = A_H; p.zCycle = ZC;
                GyreRow.Row row = GyreRow.solve((q * 3_100_000 + li * 811_000) % 9_000_000, z, SD, cell, band, p);
                String verdict;
                if (!row.valid) { verdict = "REJ:invalid"; cause[0]++; }
                else if (row.eastX >= p.maxRow - 20_000 || row.westX <= -p.maxRow + 20_000) { verdict = "REJ:edge"; cause[1]++; }
                else if ((row.eastX - row.westX) < 2 * Math.PI * p.deltaAt(z)) { verdict = "REJ:narrow"; cause[2]++; }
                else { verdict = "OK"; cause[3]++; }
                say(rep, String.format(LF, "  %-6d %-4d %-7s %12d %12d %12d %12d %12.1f %s",
                    LATS[li], q, String.valueOf(row.valid), p.maxRow, row.eastX, row.westX,
                    row.eastX - row.westX, 2 * Math.PI * p.deltaAt(z), verdict));
            }
            say(rep, "");
        }
        say(rep, String.format(LF, "  causes: invalid=%d edge=%d narrow=%d OK=%d total=%d",
            cause[0], cause[1], cause[2], cause[3], LATS.length * NQ));
        say(rep, "");
        say(rep, "  land fraction per sampled row (is the row mostly land?)");
        for (int li = 0; li < LATS.length; li++) {
            int z = WorldContract.zOfLat(LATS[li]);
            int nLand = 0, n = 0;
            for (int x = -ZC / 2; x < ZC / 2; x += 4000) { n++; if (PlateField.isLandWithCell(x, z, SD, cell)) nLand++; }
            say(rep, String.format(LF, "    lat %+4d : land %3d/%3d = %3.0f%%", LATS[li], nLand, n, 100.0 * nLand / n));
        }
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P965] " + s); rep.println("[P965] " + s); }
}