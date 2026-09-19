package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import java.io.*; import java.util.Locale;

// P630 -- pzRef 进 v 的 A/B：赤道 divU 与降水锚。
public class P630 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P630] " + s); System.out.println("[P630] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static void divProfile(String tag, long sd, int cell, double th) {
        say("      " + tag);
        say("        lat   <divU>       RMS        |divU|/1e-6");
        for (double latd = 0; latd <= 50; latd += 5) {
            int z = zOfLat(latd);
            double s = 0, s2 = 0; long n = 0;
            for (int c = 0; c < 72; c++) {
                PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), z, sd, cell, th, 500_000);
                double d = PrecipField.DIAG.get()[0];
                s += d; s2 += d * d; n++;
            }
            double m = s / n;
            say(String.format(LF, "        %4.0f  %+11.4e  %10.3e   %.1f", latd, m, Math.sqrt(s2 / n - m * m), Math.abs(m) / 1e-6));
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p630_report.txt"), "UTF-8");
        say("P630: pzRef 进 v 的 A/B（地球掩膜 + ETOPO1 + SST）");
        EarthRef.install();
        OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("");
        Atmosphere.PZREF_IN_V = true;
        SimClimate.clearCache();
        divProfile("[A] PZREF_IN_V = true（现状）", sd, cell, th);
        say("");
        Atmosphere.PZREF_IN_V = false;
        SimClimate.clearCache();
        divProfile("[B] PZREF_IN_V = false（去掉重复计数）", sd, cell, th);
        Atmosphere.PZREF_IN_V = true;
        SimClimate.clearCache();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}