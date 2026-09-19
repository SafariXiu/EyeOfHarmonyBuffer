package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P609 -- 组合：S1(HadleyCell) + S3(beta) + 热容 + 净加热 + cell 位相 一起开
public class P609 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P609] " + s); System.out.println("[P609] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double[] boxes(long sd, int cell, double th) {
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
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = false;
        Atmosphere.CELL_PHASE_FROM_TEMP = false; Atmosphere.CELL_MIG_SENS = 0.0;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false;
        PrecipField.WZM_ITCZ_SHIFT = true;
        SoilMoisture.clearMemo(); SimClimate.clearCache();
    }

    static void run(String tag, long sd, int cell, double th) {
        SimClimate.clearCache(); SoilMoisture.clearMemo();
        if (StationaryWave.ENABLED) { StationaryWave.invalidate(); StationaryWave.ensureSolved(sd, cell, th, GRAD); }
        double[] r = boxes(sd, cell, th);
        say(String.format(LF, "      %-30s 亚洲 %6.3f  撒哈拉 %6.3f  美南 %6.3f  北太 %6.3f   比 %6.3f",
            tag, r[0], r[1], r[2], r[3], r[0] / r[1]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p609_report.txt"), "UTF-8");
        say("P609: 组合 —— S1 + S3 + 热容 + 净加热 + cell 位相");
        say(String.format(LF, "  HadleyCell.phi_H = %.2f 度   phi_0 = %.2f 度", HadleyCell.phiHDeg(), HadleyCell.phiHDeg() / Math.sqrt(5.0)));
        say(String.format(LF, "  观测：亚洲 %.3f  撒哈拉 %.3f  美南 %.3f  北太 %.3f   比 %.1f", OBS[0], OBS[1], OBS[2], OBS[3], OBS[0] / OBS[1]));
        EarthRef.install();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);

        say("");
        say("  [A] 逐个叠加（JJA）");
        off(); run("基线", sd, cell, thS);
        off(); SoilMoisture.ENABLED = true; run("S3", sd, cell, thS);
        off(); SoilMoisture.ENABLED = true; Atmosphere.SEASON_FROM_HEAT_CAPACITY = true; run("S3+热容", sd, cell, thS);
        off(); HadleyCell.ENABLED = true; run("S1", sd, cell, thS);
        off(); HadleyCell.ENABLED = true; SoilMoisture.ENABLED = true; run("S1+S3", sd, cell, thS);
        off(); HadleyCell.ENABLED = true; SoilMoisture.ENABLED = true;
              Atmosphere.SEASON_FROM_HEAT_CAPACITY = true;
              Atmosphere.CELL_PHASE_FROM_TEMP = true;
              StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1.0; StationaryWave.Q_NET_HEATING = true;
              run("S1+S3+热容+净加热+位相", sd, cell, thS);
        off(); SoilMoisture.ENABLED = true;
              StationaryWave.ENABLED = true; StationaryWave.Q_SCALE = 1.0; StationaryWave.Q_NET_HEATING = true;
              run("S3+净加热（无S1）", sd, cell, thS);
        say("");
        say("  [B] 最优组合的 DJF");
        off(); SoilMoisture.ENABLED = true; Atmosphere.SEASON_FROM_HEAT_CAPACITY = true;
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        run("S3+热容 DJF", sd, cell, thW);
        off();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}