package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P656 -- §443 的第二步 go/no-go：**水汽源**。沿风逆推到海，读那里的【海温】，
// 看「陆地水汽 = RH_SEA*qSat(上游海温) * exp(-fetch/L)」能不能把沙漠与季风分开。
public class P656 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P656] " + s); System.out.println("[P656] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double STEP = 50_000.0, MAXF = 4_000_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** 沿风逆推到海：返回 {fetch, 找到海?, 上游海温 K, 本点 q}。 */
    static double[] trace(int x0, int z0, long sd, int cell, double th) {
        double q0 = PrecipField.moisture(Atmosphere.zonalMeanSeaLevelK(WorldContract.latOf(z0))
                    + Atmosphere.seasonalAnomalyZonal(WorldContract.latOf(z0), th), 0.0, 0.0);
        double x = x0, z = z0;
        for (double d = 0; d < MAXF; d += STEP) {
            double[] uv = Atmosphere.windAt((int) Math.round(x), (int) Math.round(z), sd, cell, th, GRAD);
            double sp = Math.hypot(uv[0], uv[1]);
            if (sp < 1.0e-3) return new double[]{d, 0, Double.NaN, q0};
            x -= uv[0] / sp * STEP; z -= uv[1] / sp * STEP;
            z = Math.max(0, Math.min(WorldContract.MAX_D, z));
            int xi = (int) Math.round(x), zi = (int) Math.round(z);
            if (!PlateField.isLandWithCell(xi, zi, sd, cell)) {
                double lat = WorldContract.latOf(zi);
                double sst = Atmosphere.annualSeaLevelTemp(lat, 0.0, Atmosphere.sstAnom(xi, zi));
                return new double[]{d, 1, sst, q0};
            }
        }
        return new double[]{MAXF, 0, Double.NaN, q0};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p656_report.txt"), "UTF-8");
        say("P656: 水汽源 go/no-go —— 上游海温与 fetch");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");
        say("     季节 盒子   |  fetch(km)  上游SST(K)  q源=RH*qSat(SST)   本点 q    |  L=1000km  L=2000km  L=3000km");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            for (int b = 0; b < 2; b++) {
                double sf = 0, st = 0, sq = 0, sr = 0; long n = 0, nhit = 0;
                for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                    for (int c = 0; c < 72; c++) {
                        double lon = (c + 0.5) * 5.0;
                        if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                        double[] t = trace(xOfLon(lon), zOfLat(latd), sd, cell, th);
                        sf += t[0]; sq += t[3]; n++;
                        if (t[1] > 0.5) { st += t[2]; sr += PrecipField.RH_SEA * PrecipField.qSat(t[2]); nhit++; }
                    }
                double qs = (nhit > 0) ? sr / nhit : Double.NaN;
                say(String.format(LF, "     %s  %s | %9.0f  %10.2f  %14.6f  %9.6f  |  %.6f  %.6f  %.6f",
                    (s == 0 ? "JJA" : "DJF"), NM[b], sf / n / 1000.0, (nhit > 0 ? st / nhit : Double.NaN), qs, sq / n,
                    qs * Math.exp(-sf / n / 1.0e6), qs * Math.exp(-sf / n / 2.0e6), qs * Math.exp(-sf / n / 3.0e6)));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
