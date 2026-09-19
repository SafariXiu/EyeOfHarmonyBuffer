package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P680 -- §473: 稳态平流水汽收支的实测 + 主判据 + 代价。
public class P680 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P680] " + s); System.out.println("[P680] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};

    static double[] boxMean(long sd, int cell, double th, int b) {
        double s = 0, sq = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                sq += PrecipField.DIAG.get()[5]; n++;
            }
        return new double[]{s / n, sq / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p680_report.txt"), "UTF-8");
        say("P680: 稳态平流水汽收支（§473）");
        say("     观测锚：JJA 7.667/0.105 = 72.76；DJF 0.763/0.392 = 1.95；年 3.560/0.236 = 15.06");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] nm = {
            "A 基线（全 OFF）",
            "B §448 最佳",
            "C 仅【平流口径】",
            "D 平流 + S3 + V + PZREF",
            "E B + 平流 + S3 + V + PZREF"
        };
        // {src, adv, s3veg, pzref}
        Object[][] cfg = {
            {false,false,false,0},
            {true, false,false,0},
            {false,true, false,0},
            {false,true, true, 1},
            {true, true, true, 1}
        };
        say("");
        say("     配置                        | q 亚     q 撒    |  JJA 亚洲  撒哈拉   比 |  DJF 比 |  年 比 | ms/点");
        for (int k = 0; k < nm.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            PrecipField.Q_ADVECT_BUDGET = (Boolean) cfg[k][1];
            SoilMoisture.ENABLED = (Boolean) cfg[k][2];
            Vegetation.ENABLED = (Boolean) cfg[k][2];
            Vegetation.RS_BARE = ((Boolean) cfg[k][2]) ? 150.0 : 0.0;
            Vegetation.RS_PASS = 2;
            Atmosphere.PZREF_VZ_MODE = (Integer) cfg[k][3];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            PrecipField.advCalls = 0; PrecipField.advWalkSteps = 0; PrecipField.advEvalPoints = 0;
            long t0 = System.nanoTime();
            double[] aj = boxMean(sd, cell, thS, 0), sj = boxMean(sd, cell, thS, 1);
            double[] aw = boxMean(sd, cell, thW, 0), sw = boxMean(sd, cell, thW, 1);
            double ms = (System.nanoTime() - t0) / 1.0e6;
            double aa = 0.5 * (aj[0] + aw[0]), sa = 0.5 * (sj[0] + sw[0]);
            say(String.format(LF, "     %-27s | %.5f %.5f | %8.3f %8.3f %6.2f | %6.2f | %6.2f | %6.2f",
                nm[k], aj[1], sj[1], aj[0], sj[0], aj[0] / sj[0], aw[0] / sw[0], aa / sa, ms / 320.0));
            if ((Boolean) cfg[k][1]) say(String.format(LF,
                "          平流代价：调用=%d 路径步=%d 源汇求值点=%d（%.2f 点/次）",
                PrecipField.advCalls, PrecipField.advWalkSteps, PrecipField.advEvalPoints,
                PrecipField.advCalls == 0 ? 0.0 : (double) PrecipField.advEvalPoints / PrecipField.advCalls));
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        PrecipField.Q_ADVECT_BUDGET = false;
        SoilMoisture.ENABLED = false; Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0;
        Atmosphere.PZREF_VZ_MODE = 0;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}