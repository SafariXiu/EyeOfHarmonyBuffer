package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P612 -- 种子来自 Hadley 下沉：wZm 不带 ITCZ 平移 + S3 + 干暖项
public class P612 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P612] " + s); System.out.println("[P612] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double[] scan(long sd, int cell, double th) {
        double[] o = new double[8];
        for (int b = 0; b < 4; b++) {
            double s = 0, be = 0; long n = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    int x = xOfLon(lon), z = zOfLat(latd);
                    s += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    be += PrecipField.DIAG.get()[13];
                    n++;
                }
            o[b] = s / n; o[4 + b] = be / n;
        }
        return o;
    }

    static void off() {
        HadleyCell.ENABLED = false; SoilMoisture.ENABLED = false;
        Atmosphere.PA_DRY_WARMTH = false; PrecipField.WZM_ITCZ_SHIFT = true;
        SoilMoisture.clearMemo(); SimClimate.clearCache();
    }

    static void run(String tag, long sd, int cell, double th) {
        SimClimate.clearCache(); SoilMoisture.clearMemo();
        double[] r = scan(sd, cell, th);
        say(String.format(LF, "      %-30s 亚洲 %6.3f(%5.3f) 撒哈拉 %6.3f(%5.3f) 美南 %6.3f 北太 %6.3f  比 %6.3f",
            tag, r[0], r[4], r[1], r[5], r[2], r[3], r[0] / r[1]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p612_report.txt"), "UTF-8");
        say("P612: 把「副热带下沉」当 beta 的种子（wZm 不带 ITCZ 平移）");
        say(String.format(LF, "  HadleyCell phi_H=%.2f phi_0=%.2f 度", HadleyCell.phiHDeg(), HadleyCell.phiHDeg() / Math.sqrt(5.0)));
        say("      （括号内是 beta）");
        EarthRef.install();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("");
        off(); run("基线", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              run("S3+干暖(带平移)", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              PrecipField.WZM_ITCZ_SHIFT = false;
              run("S3+干暖+无平移", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              PrecipField.WZM_ITCZ_SHIFT = false; HadleyCell.ENABLED = true;
              run("S3+干暖+无平移+HeldHou", sd, cell, th);
        off(); HadleyCell.ENABLED = true; PrecipField.WZM_ITCZ_SHIFT = false;
              run("无平移+HeldHou(无S3)", sd, cell, th);
        off();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}