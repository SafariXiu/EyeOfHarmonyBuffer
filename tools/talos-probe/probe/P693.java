package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P693 -- §488: 海岸门唯一湿站（西撒哈拉 6.42）的机理 —— 近岸 kappa 剖面。
//   对比：西撒哈拉（湿）vs 纳米布（干），同一条经向剖面。
public class P693 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P693] " + s); System.out.println("[P693] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static void prof(long sd, int cell, double lat, double lon0, double lon1, String nm) {
        say("");
        say("  --- " + nm + "  (lat " + lat + ") ---");
        say("     经度  | 陆/海 | kappa | tSl(K)  | q(kg/kg) | P(mm/day)");
        double th = Atmosphere.theta(0.0);
        for (double lon = lon0; lon <= lon1; lon += 2.5) {
            int x = xOfLon(lon), z = zOfLat(lat);
            boolean land = PlateField.isLandWithCell(x, z, sd, cell);
            double k = Atmosphere.kappaMemo(x, z, sd, cell);
            double t = Atmosphere.annualSeaLevelTemp(WorldContract.latOf(z), k, Atmosphere.sstAnom(x, z, th))
                     + Atmosphere.seasonalAnomaly(WorldContract.latOf(z), k, th);
            double q = PrecipField.moisture(t, 0.0, k);
            double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
            say(String.format(LF, "     %6.1f | %s | %.3f | %7.2f | %8.5f | %7.3f",
                lon, land ? " 陆 " : " 海 ", k, t, q, p));
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p693_report.txt"), "UTF-8");
        say("P693: 海岸湿站的近岸 kappa 剖面（§488）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        prof(sd, cell, 24.0, -20.0, 5.0, "西撒哈拉（湿，P=6.42）");
        prof(sd, cell, -23.0, 10.0, 30.0, "纳米布（干，P=0.00）");
        prof(sd, cell, -23.0, -75.0, -60.0, "阿塔卡马（干，P=0.00）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}