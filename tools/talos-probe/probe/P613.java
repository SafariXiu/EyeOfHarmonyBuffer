package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P613 -- 全栈：副热带下沉种子 + S3 + 干暖 + 净柱加热的 S2（区域季风）
public class P613 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P613] " + s); System.out.println("[P613] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double[] scan(long sd, int cell, double th) {
        double[] o = new double[4];
        for (int b = 0; b < 4; b++) {
            double s = 0; long n = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                    n++;
                }
            o[b] = s / n;
        }
        return o;
    }

    static void off() {
        HadleyCell.ENABLED = false; SoilMoisture.ENABLED = false;
        Atmosphere.PA_DRY_WARMTH = false; PrecipField.WZM_ITCZ_SHIFT = true;
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = false; Atmosphere.CELL_PHASE_FROM_TEMP = false;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false;
        SoilMoisture.clearMemo(); SimClimate.clearCache();
    }

    static void run(String tag, long sd, int cell, double th) {
        SimClimate.clearCache(); SoilMoisture.clearMemo();
        if (StationaryWave.ENABLED) { StationaryWave.invalidate(); StationaryWave.ensureSolved(sd, cell, th, GRAD); }
        double[] r = scan(sd, cell, th);
        say(String.format(LF, "      %-34s 亚洲 %7.3f 撒哈拉 %7.3f 美南 %7.3f 北太 %6.3f  比 %7.3f",
            tag, r[0], r[1], r[2], r[3], r[0] / r[1]));
        say(String.format(LF, "      %-34s 与观测比 %6.3f        %6.3f        %6.3f       %6.3f",
            "", r[0] / OBS[0], r[1] / OBS[1], r[2] / OBS[2], r[3] / OBS[3]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p613_report.txt"), "UTF-8");
        say("P613: 全栈组合");
        EarthRef.install();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say(String.format(LF, "      观测                                 亚洲 %7.3f 撒哈拉 %7.3f 美南 %7.3f 北太 %6.3f  比 %7.1f",
            OBS[0], OBS[1], OBS[2], OBS[3], OBS[0] / OBS[1]));
        off(); run("基线", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              PrecipField.WZM_ITCZ_SHIFT = false;
              run("种子+干暖(无S2)", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              PrecipField.WZM_ITCZ_SHIFT = false;
              StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1.0; StationaryWave.Q_NET_HEATING = true;
              run("种子+干暖+S2净加热", sd, cell, th);
        off(); SoilMoisture.ENABLED = true; Atmosphere.PA_DRY_WARMTH = true;
              PrecipField.WZM_ITCZ_SHIFT = false;
              StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1.0; StationaryWave.Q_NET_HEATING = true;
              Atmosphere.SEASON_FROM_HEAT_CAPACITY = true; Atmosphere.CELL_PHASE_FROM_TEMP = true;
              run("全栈", sd, cell, th);
        off();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}