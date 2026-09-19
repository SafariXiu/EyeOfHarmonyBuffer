package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P611 -- §409 地表干暖项：把 beta 接进气压场之后，经向对比能不能出来？
public class P611 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P611] " + s); System.out.println("[P611] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double[] scan(long sd, int cell, double th, int b) {
        double[] a = new double[4]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                a[0] += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                a[1] += Atmosphere.pressureAnomaly(x, z, sd, cell, th);
                a[2] += PrecipField.DIAG.get()[0];
                a[3] += PrecipField.DIAG.get()[13];
                n++;
            }
        for (int i = 0; i < 4; i++) a[i] /= n;
        return a;
    }

    static void row(String tag, long sd, int cell, double th) {
        SimClimate.clearCache();
        double[] sum = new double[4];
        for (int b = 0; b < 4; b++) {
            double[] r = scan(sd, cell, th, b);
            say(String.format(LF, "      %-22s %-5s P=%7.3f (obs %6.3f)  p=%+8.1f Pa  divU=%+.3e  beta=%.3f",
                tag, NM[b], r[0], OBS[b], r[1], r[2], r[3]));
            sum[b] = r[0];
        }
        say(String.format(LF, "      => 亚洲/撒哈拉 = %.3f  (观测 74.0)", sum[0] / sum[1]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p611_report.txt"), "UTF-8");
        say("P611: 地表干暖项（把 beta 接进气压场）");
        EarthRef.install();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        SoilMoisture.ENABLED = true; SoilMoisture.clearMemo();
        Atmosphere.PA_DRY_WARMTH = false;
        say("");
        say("  [A] S3 开、干暖项关（基线）");
        row("基线", sd, cell, th);
        say("");
        say("  [B] S3 开 + 干暖项开");
        Atmosphere.PA_DRY_WARMTH = true;
        row("干暖项 ON", sd, cell, th);
        Atmosphere.PA_DRY_WARMTH = false;
        SoilMoisture.ENABLED = false; SoilMoisture.clearMemo();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}