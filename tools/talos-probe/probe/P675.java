package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P675 -- §468: 【缺失的那个数字】—— 全套开关一起开的主判据 + 逐项消融。
public class P675 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P675] " + s); System.out.println("[P675] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p675_report.txt"), "UTF-8");
        say("P675: 全套开关的主判据 + 消融（§468）");
        say("     观测锚：JJA 亚洲 7.667 / 撒哈拉 0.105 = 72.76；DJF 0.763 / 0.392 = 1.95；年 3.560 / 0.236 = 15.06");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] nm = {
            "1 基线（全 OFF）",
            "2 §448 最佳（Q源+源季节项）",
            "3 +S3+Qnet+ASR-OLR+闭环（无 V）",
            "4 全套（+V, RS_BARE=150, RS_PASS=2）",
            "5 全套 − V",
            "6 全套 − ASR-OLR（旧 qRad）",
            "7 全套 − PZREF_VZ_MODE"
        };
        // {src, seasT, s3, qnet, asrOlr, closed, veg, pzref}
        Object[][] cfg = {
            {false,false,false,false,false,false,false,0},
            {true, true, false,false,false,false,false,0},
            {true, true, true, true, true, true, false,0},
            {true, true, true, true, true, true, true, 1},
            {true, true, true, true, true, true, false,1},
            {true, true, true, true, false,true, true, 1},
            {true, true, true, true, true, true, true, 0}
        };
        say("");
        say("     配置                                |  JJA 亚洲  撒哈拉   比 |  DJF 亚洲  撒哈拉   比 |  年 亚洲  撒哈拉   比");
        for (int k = 0; k < nm.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][1];
            SoilMoisture.ENABLED = (Boolean) cfg[k][2];
            StationaryWave.Q_NET_HEATING = (Boolean) cfg[k][3];
            StationaryWave.QRAD_ASR_MINUS_OLR = (Boolean) cfg[k][4];
            StationaryWave.CLOSED_LOOP = (Boolean) cfg[k][5];
            StationaryWave.ENABLED = (Boolean) cfg[k][3];
            Vegetation.ENABLED = (Boolean) cfg[k][6];
            Vegetation.RS_BARE = cfg[k][6].equals(Boolean.TRUE) ? 150.0 : 0.0;
            Vegetation.RS_PASS = 2;
            Atmosphere.PZREF_VZ_MODE = (Integer) cfg[k][7];
            StationaryWave.invalidate(); SoilMoisture.invalidate(); Vegetation.invalidate(); SimClimate.clearCache();
            double aj = boxMean(sd, cell, thS, 0), sj = boxMean(sd, cell, thS, 1);
            double aw = boxMean(sd, cell, thW, 0), sw = boxMean(sd, cell, thW, 1);
            double aa = 0.5 * (aj + aw), sa = 0.5 * (sj + sw);
            say(String.format(LF, "     %-37s | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f | %7.3f %7.3f %6.2f",
                nm[k], aj, sj, aj / sj, aw, sw, aw / sw, aa, sa, aa / sa));
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.ENABLED = false; StationaryWave.Q_NET_HEATING = false;
        StationaryWave.QRAD_ASR_MINUS_OLR = false; StationaryWave.CLOSED_LOOP = false;
        StationaryWave.ENABLED = false; Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0;
        Atmosphere.PZREF_VZ_MODE = 0;
        StationaryWave.invalidate(); SoilMoisture.invalidate(); Vegetation.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}