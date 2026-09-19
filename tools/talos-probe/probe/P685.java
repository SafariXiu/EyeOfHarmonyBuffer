package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P685 -- §480: WZM_FROM_QNET（+H_bl*F_net/M）与 Q_FROM_BLBUDGET 的配对实测。
public class P685 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P685] " + s); System.out.println("[P685] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BX = {{70,120,15,35},{0,30,20,35},{130,145,-20,-15},{20,35,-20,-10}};
    static final String[] NM = {"亚洲季风*","撒哈拉*","澳洲季风","非洲南部"};
    static final double[][] OB = {{7.667,0.763},{0.105,0.392},{0.120,6.371},{0.076,6.609}};
    static final int[] HOLD = {0,0,1,1};

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p685_report.txt"), "UTF-8");
        say("P685: WZM_FROM_QNET 与收支口径的配对（§480）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] nm = {
            "A 基线",
            "B §448 拟合参照",
            "C 仅 WZM_FROM_QNET（未配对）",
            "D 配对：Q_FROM_BLBUDGET + WZM_FROM_QNET",
            "E D + V + PZREF"
        };
        // {src, blb, qnet, vegpz}
        Object[][] cfg = {
            {false,false,false,0},
            {true, false,false,0},
            {false,false,true, 0},
            {false,true, true, 0},
            {false,true, true, 1}
        };
        for (int k = 0; k < nm.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][0];
            PrecipField.Q_FROM_BLBUDGET = (Boolean) cfg[k][1];
            PrecipField.WZM_FROM_QNET = (Boolean) cfg[k][2];
            Vegetation.ENABLED = ((Integer) cfg[k][3]) >= 1;
            Vegetation.RS_BARE = ((Integer) cfg[k][3]) >= 1 ? 150.0 : 0.0;
            Vegetation.RS_PASS = 2;
            Atmosphere.PZREF_VZ_MODE = (Integer) cfg[k][3];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            PrecipField.qnetNegM = 0; PrecipField.qnetCalls = 0;
            say("");
            say("  --- " + nm[k] + " ---");
            int pass = 0; int hp = 0; int ht = 0;
            for (int b = 0; b < 4; b++) {
                double aj = bm(sd, cell, thS, b), aw = bm(sd, cell, thW, b);
                if (Double.isNaN(aj)) continue;
                boolean ph = (aj > aw) == (OB[b][0] > OB[b][1]);
                if (ph) pass++;
                if (HOLD[b] == 1) { ht++; if (ph) hp++; }
                say(String.format(LF, "     %-10s | 模型 %7.3f %6.3f | 观测 %7.3f %6.3f | %s",
                    NM[b], aj, aw, OB[b][0], OB[b][1], ph ? " OK " : "**反相**"));
            }
            say("     GATE_PHASE_ALL=" + pass + "/4  GATE_PHASE_HOLDOUT=" + hp + "/" + ht
              + "  qnetCalls=" + PrecipField.qnetCalls + "  qnetNegM=" + PrecipField.qnetNegM);
        }
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        PrecipField.Q_FROM_BLBUDGET = false; PrecipField.WZM_FROM_QNET = false;
        Vegetation.ENABLED = false; Vegetation.RS_BARE = 0.0; Atmosphere.PZREF_VZ_MODE = 0;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}