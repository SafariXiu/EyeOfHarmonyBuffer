package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P659 -- §445：水汽源的【位置】。亚洲塌了 6.5 倍，查它的上游海面点在哪、多暖。
public class P659 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P659] " + s); System.out.println("[P659] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double STEP = 150_000.0, MAXF = 4_000_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** 返回 {fetch, 找到海?, 源纬度(deg), 源经度(deg), SST}。 */
    static double[] trace(int x0, int z0, long sd, int cell, double th) {
        double x = x0, z = z0;
        for (double d = 0; d < MAXF; d += STEP) {
            double[] uv = Atmosphere.windAt((int) Math.round(x), (int) Math.round(z), sd, cell, th, GRAD);
            double sp = Math.hypot(uv[0], uv[1]);
            if (sp < 1.0e-3) return new double[]{d, 0, 0, 0, Double.NaN};
            x -= uv[0] / sp * STEP; z -= uv[1] / sp * STEP;
            z = Math.max(0, Math.min(WorldContract.MAX_D, z));
            int xi = (int) Math.round(x), zi = (int) Math.round(z);
            if (!PlateField.isLandWithCell(xi, zi, sd, cell)) {
                double lat = WorldContract.latOf(zi);
                double sst = Atmosphere.annualSeaLevelTemp(lat, 0.0, Atmosphere.sstAnom(xi, zi));
                double lonDeg = xi / CIRC * 360.0;
                return new double[]{d, 1, Math.toDegrees(lat), lonDeg, sst};
            }
        }
        return new double[]{MAXF, 0, 0, 0, Double.NaN};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p659_report.txt"), "UTF-8");
        say("P659: 水汽源的位置（上溯到海面的那一点）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");
        say("     季节 盒子   |  fetch(km)  源纬度     源经度     SST(K)   理论 qSat(SST)   基准 20N 年表 tOceanK");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            for (int b = 0; b < 2; b++) {
                double sf=0, sl=0, so=0, st=0; long n=0, hit=0;
                for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                    for (int c = 0; c < 72; c++) {
                        double lon = (c + 0.5) * 5.0;
                        if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                        double[] t = trace(xOfLon(lon), zOfLat(latd), sd, cell, th);
                        sf += t[0]; n++;
                        if (t[1] > 0.5) { sl += t[2]; so += t[3]; st += t[4]; hit++; }
                    }
                double sst = (hit > 0) ? st / hit : Double.NaN;
                say(String.format(LF, "     %s  %s | %9.0f  %8.1f  %9.1f  %8.2f  %12.6f    (%.2f)",
                    (s == 0 ? "JJA" : "DJF"), NM[b], sf / n / 1000.0, (hit > 0 ? sl / hit : Double.NaN),
                    (hit > 0 ? so / hit : Double.NaN), sst, (hit > 0 ? PrecipField.qSat(sst) : Double.NaN),
                    ZonalTables.tOceanK(Math.toRadians(20.0))));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
