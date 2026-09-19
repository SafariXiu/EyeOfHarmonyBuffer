package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P647 -- §434：S3 的 beta 到底有没有空间对比？逐点读 DIAG[13]。
// 若 beta_Sahara/beta_Asia ~ 1，则桶被【模型自己的 P】喂饱 ⇒ 正是目标里点名的「循环」。
public class P647 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P647] " + s); System.out.println("[P647] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double P = 0, be = 0, q = 0, we = 0, ka = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                P += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                be += d[13]; q += d[5]; we += d[3]; ka += d[7]; n++;
            }
        return new double[]{P / n, be / n, q / n, we / n, ka / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p647_report.txt"), "UTF-8");
        say("P647: S3 的 beta 空间对比（盒子同 P601/P646）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        boolean[] on = {false, true};
        for (int k = 0; k < 2; k++) {
            SoilMoisture.ENABLED = on[k];
            SimClimate.clearCache();
            say("");
            say("  --- SoilMoisture.ENABLED = " + on[k] + " ---");
            say("     季节  盒子   |   P(mm/day)   beta_eff     q(kg/kg)    wEff(m/s)   kappa");
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] a = box(sd, cell, th, 0), b = box(sd, cell, th, 1);
                say(String.format(LF, "     %s  亚洲   | %9.3f   %8.4f   %10.6f   %+.3e   %.3f",
                    (s == 0 ? "JJA" : "DJF"), a[0], a[1], a[2], a[3], a[4]));
                say(String.format(LF, "     %s  撒哈拉 | %9.3f   %8.4f   %10.6f   %+.3e   %.3f",
                    (s == 0 ? "JJA" : "DJF"), b[0], b[1], b[2], b[3], b[4]));
                say(String.format(LF, "     %s  比值 亚/撒 | %9.3f   %8.4f   %10.6f",
                    (s == 0 ? "JJA" : "DJF"), a[0] / b[0], a[1] / b[1], a[2] / b[2]));
            }
        }
        SoilMoisture.ENABLED = false;
        say("");
        say(String.format(LF, "  SoilMoisture 参数：W_FC=%.1f mm  RH_DRY=%.3f  NTHETA=%d",
            SoilMoisture.W_FC, SoilMoisture.RH_DRY, SoilMoisture.NTHETA));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
