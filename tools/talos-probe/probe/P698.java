package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P698 -- §496: BLQ 对流判据的实测（相位门四盒 + dh + 拦截率）。
public class P698 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P698] " + s); System.out.println("[P698] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BX = {{70,120,15,35},{0,30,20,35},{130,145,-20,-15},{20,35,-20,-10}};
    static final String[] NM = {"亚洲季风*","撒哈拉*","澳洲季风","非洲南部"};
    static final double[][] OB = {{7.667,0.763},{0.105,0.392},{0.120,6.371},{0.076,6.609}};

    static double[] bm(long sd, int cell, double th, int b) {
        double s = 0, sd2 = 0; long n = 0;
        for (double latd = BX[b][2] + 2.5; latd <= BX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BX[b][0] || lon > BX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                sd2 += PrecipField.blqLastDh; n++;
            }
        return new double[]{s / n, sd2 / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p698_report.txt"), "UTF-8");
        say("P698: BLQ 对流判据实测（§496）—— 文献 M_u = gamma*(s_bl - s*_th)，硬零阈值");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] cn = {"A BLQ 关（现状）", "B BLQ 开", "C BLQ 开 + 水汽源", "D BLQ 开 + 水汽源 + 源季节项"};
        Object[][] cfg = {{false,false,false},{true,false,false},{true,true,false},{true,true,true}};
        for (int k = 0; k < cn.length; k++) {
            PrecipField.BLQ_GATE = (Boolean) cfg[k][0];
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][1];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][2];
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            PrecipField.blqBlocked = 0; PrecipField.blqCalls = 0;
            say("");
            say("  --- " + cn[k] + " ---");
            int pass = 0;
            for (int b = 0; b < 4; b++) {
                double[] j = bm(sd, cell, thS, b), w = bm(sd, cell, thW, b);
                if (Double.isNaN(j[0])) continue;
                boolean ph = (j[0] > w[0]) == (OB[b][0] > OB[b][1]);
                if (ph) pass++;
                say(String.format(LF, "     %-10s | 模型 %7.3f %6.3f | 观测 %7.3f %6.3f | %s | dh(JJA)=%+9.0f",
                    NM[b], j[0], w[0], OB[b][0], OB[b][1], ph ? " OK " : "**反相**", j[1]));
            }
            say("     GATE_PHASE_ALL=" + pass + "/4   blqBlocked=" + PrecipField.blqBlocked
              + "/" + PrecipField.blqCalls + (PrecipField.blqCalls == 0 ? "" :
                String.format(LF, "  (%.1f%%)", 100.0 * PrecipField.blqBlocked / PrecipField.blqCalls)));
        }
        PrecipField.BLQ_GATE = false; PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}