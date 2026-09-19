package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P602 -- eddy 项的纬向/季节剖面：撒哈拉 DJF 的 +2.83 mm/day 从哪来。
public class P602 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P602] " + s); System.out.println("[P602] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p602_report.txt"), "UTF-8");
        say("P602: 涡动项与主项的纬向剖面（地球掩膜 + 地形）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        double[] lons = {10.0, 100.0, -100.0};
        String[] lnm = {"10E (撒哈拉经线)", "100E (亚洲经线)", "100W (北美经线)"};
        for (int L = 0; L < lons.length; L++) {
            say("");
            say(String.format(LF, "  === %s ===   纬度  kappa  divU        主项   涡动  地板   合计 (mm/day)", lnm[L]));
            for (int pass = 0; pass < 2; pass++) {
                double th = pass == 0 ? thS : thW;
                say(String.format(LF, "   [%s]", pass == 0 ? "JJA" : "DJF"));
                for (double latd = 5; latd <= 60; latd += 5) {
                    int x = xOfLon(lons[L]), z = zOfLat(latd);
                    double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    double[] d = PrecipField.DIAG.get();
                    double main = d[8] * 86400e3, withEddy = d[9] * 86400e3, floor = d[10] * 86400e3;
                    say(String.format(LF, "        %4.0f  %.2f  %+.2e  %6.3f %6.3f %6.3f  %6.3f",
                        latd, d[7], d[0], main, withEddy - main, floor, p));
                }
            }
        }
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
