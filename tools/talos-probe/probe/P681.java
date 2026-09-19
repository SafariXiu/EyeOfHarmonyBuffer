package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P681 -- §475: 平流口径（无拟合）之上的完整消融，找【无拟合前提下的最好组合】。
public class P681 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P681] " + s); System.out.println("[P681] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};

    static double boxMean(long sd, int cell, double th, int b) {
        double s = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                n++;
            }
        return s / n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p681_report.txt"), "UTF-8");
        say("P681: 平流之上 的完整消融（§475）");
        say("     观测锚：JJA 7.667/0.105 = 72.76；DJF 0.763/0.392 = 1.95；年 3.560/0.236 = 15.06");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] nm = {
            "0 §448 拟合参照（无平流）",
            "1 平流 only",
            "2 平流 + V",
            "3 平流 + V + PZREF",
            "4 平流 + S3 + V + PZREF",
            "5 4 + S2(Qnet+ASR-OLR+闭环)",
            "6 平流 + S3 + S2"
        };
        // {src, adv, s3, veg, pzref, s2}
        Object[][] cfg = {
            {true, false,false,false,0,false},
            {false,true, false,false,0,false},
            {false,true, false,true, 0,false},
            {false,true, false,true, 1,false},
            {false,true, true, true, 1,false},
            {false,true, true, true, 1,true },
            {false,true, true, false,0,true }
        };
        say("");
        say("     配置                          |  JJA 亚洲  撒哈拉   比 |  DJF 亚洲  撒哈拉   比 |  年 比");
        for (int k = 0; k < nm.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            PrecipField.Q_ADVECT_BUDGET = (Boolean) cfg[k][1];
            SoilMoisture.ENABLED = (Boolean) cfg[k][2];
            Vegetation.ENABLED = (Boolean) cfg[k][3];
            Vegetation.RS_BARE = ((Boolean) cfg[k][3]) ? 150.0 : 0.0;
            Vegetation.RS_PASS = 2;
            Atmosphere.PZREF_VZ_MODE = (Integer) cfg[k][4];
            StationaryWave.ENABLED = (Boolean) cfg[k][5];
            StationaryWave.Q_NET_HEATING = (Boolean) cfg[k][5];
            StationaryWave.QRAD_ASR_MINUS_OLR = (Boolean) cfg[k][5];
            StationaryWave.CLOSED_LOOP = (Boolean) cfg[k][5];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            double aj = boxMean(sd, cell, thS, 0), sj = boxMean(sd, cell, thS, 1);
            double aw = boxMean(sd, cell, thW, 0), sw = boxMean(sd, cell, thW, 1);
            double aa = 0.5 * (aj + aw), sa = 0.5 * (sj + sw);
            say(String.format(LF, "     %-29s | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f | %6.2f",
                nm[k], aj, sj, aj / sj, aw, sw, aw / sw, aa / sa));
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        PrecipField.Q_ADVECT_BUDGET = false;
        SoilMoisture.ENABLED = false; Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0;
        Atmosphere.PZREF_VZ_MODE = 0;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false;
        StationaryWave.QRAD_ASR_MINUS_OLR = false; StationaryWave.CLOSED_LOOP = false;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}