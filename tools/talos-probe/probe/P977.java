package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P977 (SS671 path B, first diagnostic): split the January OCEAN surface temperature into
 *   surfaceTemp = annualSeaLevelTemp(lat, kappa=0, sstAnom) + seasonalAnomaly - GAMMA*elev*kappa
 * and, for kappa = 0, annualSeaLevelTemp(lat, 0, 0) == oceanBaseK(lat).  So the three pieces are:
 *   oceanBaseK(lat)      (the observed annual SST profile)
 *   sstAnom(x,z,theta)   (the ocean-dynamics anomaly)
 *   seasonalAnomaly(lat, theta)   (the seasonal term)
 * Whichever is responsible for the 13-20 K cold bias will show up here.
 */
public class P977 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");

    static void row(PrintStream rep, String tag, double th, String season) {
        say(rep, "");
        say(rep, "=== " + tag + "  (ocean only, kappa<0.02) ===");
        say(rep, String.format(LF, "  %-6s %6s %11s %11s %11s %11s %11s",
            "latN", "nPts", "oceanBaseK", "sstAnom", "seasonal", "T_ocean", "realJanSST"));
        for (int latDeg = 5; latDeg <= 65; latDeg += 5) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            double lat = WorldContract.latOf(z);
            double sBase = 0, sAnom = 0, sSea = 0, sT = 0; int n = 0;
            for (int x = -23_000_000; x <= 23_000_000; x += 1_000_000) {
                int cell = PlateField.PLATE_CELL;
                double k = Atmosphere.kappaMemo(x, z, SD, cell);
                if (k >= 0.02) continue;
                double anom = Atmosphere.sstAnom(x, z, th);
                sBase += Atmosphere.annualSeaLevelTemp(lat, k, 0.0);
                sAnom += anom;
                sSea += Atmosphere.seasonalAnomaly(lat, k, th);
                sT += Atmosphere.surfaceTemp(x, z, SD, cell, th);
                n++;
            }
            if (n == 0) continue;
            say(rep, String.format(LF, "  %-6d %6d %11.2f %11.3f %11.2f %11.2f",
                latDeg, n, sBase / n, sAnom / n, sSea / n, sT / n));
        }
    }

    public static void main(String[] args) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p977_report.txt"), "UTF-8");
        say(rep, "P977: why is the model ocean 13-20 K too cold in January?  (split the terms)");
        row(rep, "JANUARY (theta=0)", Atmosphere.theta(0.0), "Jan");
        row(rep, "JULY (theta=DAYS/2)", Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0), "Jul");
        say(rep, "");
        say(rep, "  Reference real SST (degC): 5N~27 10N~26 20N~22 30N~19 40N~12 50N~6.5 60N~4 65N~2");
        rep.close();
    }

    static void say(PrintStream rep, String s) { System.out.println("[P977] " + s); rep.println("[P977] " + s); }
}