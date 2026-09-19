package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P682 -- §476: 目标第 (5) 项 —— 多元锚 + 留出集。模型的六个盒子 vs GPCP。
//   留出集的判据是【季节相位】（南半球季风必须在 DJF 峰值），量级调参伪造不出来。
public class P682 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P682] " + s); System.out.println("[P682] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    // {lon0, lon1, lat0, lat1, 观测 ANN, 观测 JJA, 观测 DJF}
    static final double[][] BX = {
        {70,120,15,35,   3.560, 7.667, 0.763},
        {0,30,20,35,     0.236, 0.105, 0.392},
        {-112,-105,25,33,1.283, 2.749, 0.690},
        {-65,-50,-20,-10,4.291, 0.563, 8.332},
        {130,145,-20,-15,2.272, 0.120, 6.371},
        {20,35,-20,-10,  2.573, 0.076, 6.609}
    };
    static final String[] NM = {"亚洲季风*","撒哈拉*","北美季风","南美季风","澳洲季风","非洲南部"};

    static double bm(long sd, int cell, double th, int b) {
        double s = 0; long n = 0;
        for (double latd = BX[b][2] + 2.5; latd <= BX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BX[b][0] || lon > BX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                n++;
            }
        return n == 0 ? Double.NaN : s / n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p682_report.txt"), "UTF-8");
        say("P682: 多元锚 + 留出集（§476）—— * = in-sample，其余四个**从未被任何标定碰过**");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] cn = {"A §448 拟合参照", "B 平流 only（无拟合）"};
        Object[][] cfg = {{true,false},{false,true}};
        for (int k = 0; k < 2; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            PrecipField.Q_ADVECT_BUDGET = (Boolean) cfg[k][1];
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            say("");
            say("  --- " + cn[k] + " ---");
            say("     盒子        | 模型 ANN  JJA    DJF  | 观测 ANN  JJA    DJF  | 模型JJA/DJF 观测JJA/DJF | 相位");
            int ok = 0;
            for (int b = 0; b < 6; b++) {
                double aj = bm(sd, cell, thS, b), aw = bm(sd, cell, thW, b);
                double ann = 0.5 * (aj + aw);
                double mr = aw > 1e-9 ? aj / aw : Double.NaN;
                double or2 = BX[b][6] > 1e-9 ? BX[b][5] / BX[b][6] : Double.NaN;
                boolean ph = (mr > 1.0) == (or2 > 1.0);
                if (ph) ok++;
                say(String.format(LF, "     %-10s | %7.3f %6.3f %6.3f | %7.3f %6.3f %6.3f | %9.2f %10.2f | %s",
                    NM[b], ann, aj, aw, BX[b][4], BX[b][5], BX[b][6], mr, or2, ph ? "OK" : "**反相**"));
            }
            say("     相位正确 " + ok + "/6");
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        PrecipField.Q_ADVECT_BUDGET = false;
        SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}