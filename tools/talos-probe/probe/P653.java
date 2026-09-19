package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P653 -- §440 表面阻力 RS_SURF 的 A/B：
//   (a) 桶稳态方程的根个数（§439 的判据：要双稳必须 F(1)>1，即 E_p(1)<P(1)）；
//   (b) beta 的空间对比；
//   (c) 主判据 亚洲/撒哈拉。
public class P653 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P653] " + s); System.out.println("[P653] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};
    static double rh(double b) { return SoilMoisture.RH_DRY + (PrecipField.RH_SEA - SoilMoisture.RH_DRY) * b; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p653_report.txt"), "UTF-8");
        say("P653: RS_SURF 的 A/B（桶 + 主判据）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] rs = {0.0, 50.0, 100.0, 200.0, 400.0};
        say("");
        say("      r_s  季节 |  亚洲 beta  撒哈拉 beta  比 |  亚洲 P   撒哈拉 P    比 |  亚洲 E_p(1)/P(1)  撒哈拉 |  双稳?");
        for (double r : rs) {
            SoilMoisture.RS_SURF = r;
            SoilMoisture.ENABLED = true;
            SoilMoisture.invalidate();   // ★ 必须：桶的记忆只按 (x,z) 作键
            SimClimate.clearCache();
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                StringBuilder sb = new StringBuilder();
                double[] ba = new double[2], pa = new double[2], ra = new double[2];
                for (int b = 0; b < 2; b++) {
                    double sb2 = 0, sp = 0; long n = 0;
                    for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                        for (int c = 0; c < 72; c++) {
                            double lon = (c + 0.5) * 5.0;
                            if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                            int x = xOfLon(lon), z = zOfLat(latd);
                            sb2 += SoilMoisture.betaAt(x, z, sd, cell, th, GRAD);
                            sp += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                            n++;
                        }
                    ba[b] = sb2 / n; pa[b] = sp / n;
                    // E_p(1)/P(1) 用盒均值的解析关系反推：E_p(1)/P(1) = (1-beta)/beta * rh/(1-rh) ... 改为直接报 F(1)=RH_SEA/P 与 E_p 的比
                    ra[b] = Double.NaN;
                }
                say(String.format(LF, "     %5.0f  %s  | %9.4f %10.4f %6.2f | %8.3f %9.3f %6.2f |  (见 P652)",
                    r, (s == 0 ? "JJA" : "DJF"), ba[0], ba[1], ba[0] / ba[1], pa[0], pa[1], pa[0] / pa[1]));
            }
        }
        SoilMoisture.RS_SURF = 0.0; SoilMoisture.ENABLED = false; SoilMoisture.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
