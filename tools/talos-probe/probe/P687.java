package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P687 -- §482: 两层 M 的【自由对流层高度】扫描。
//   判据：若 qnetNegM 随 zFT 剧烈变化，则两层口径下 M 的符号由一个建模选择控制
//         ⇒ M 不是良定义的物理量 ⇒ F_net/M 需要真垂直剖面。
public class P687 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P687] " + s); System.out.println("[P687] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BX = {{70,120,15,35},{0,30,20,35},{130,145,-20,-15},{20,35,-20,-10}};
    static final String[] NM = {"亚洲季风","撒哈拉","澳洲季风","非洲南部"};
    static final double[][] OB = {{7.667,0.763},{0.105,0.392},{0.120,6.371},{0.076,6.609}};

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p687_report.txt"), "UTF-8");
        say("P687: 两层 M 的自由对流层高度扫描（§482）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        PrecipField.WZM_FROM_QNET = true;
        SoilMoisture.ENABLED = true;
        double[] fr = {0.15, 0.20, 0.25, 0.3333, 0.40, 0.50};
        say("");
        say("     zFT/H_EFF | zFT(m) | q_BL 阈值 | qnetNegM | 相位 | 亚洲 JJA | 撒哈拉 JJA | JJA 比");
        for (int k = 0; k < fr.length; k++) {
            PrecipField.M_FT_FRAC = fr[k];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            double zFT = fr[k] * Atmosphere.H_EFF;
            double dry = 9.81 * zFT - 1004.0 * Atmosphere.GAMMA * zFT;
            double thr = dry / 2.45e6;
            PrecipField.qnetNegM = 0; PrecipField.qnetCalls = 0;
            long tot = 0;
            double[][] J = new double[4][2];
            int pass = 0;
            for (int b = 0; b < 4; b++) {
                J[b][0] = bm(sd, cell, thS, b); J[b][1] = bm(sd, cell, thW, b);
                if (Double.isNaN(J[b][0])) continue;
                if ((J[b][0] > J[b][1]) == (OB[b][0] > OB[b][1])) pass++;
            }
            double qn = PrecipField.qnetCalls == 0 ? 0.0 : 100.0 * PrecipField.qnetNegM / PrecipField.qnetCalls;
            say(String.format(LF, "     %9.4f | %6.0f | %10.5f | %6.1f%%  |  %d/4 | %8.3f | %10.3f | %6.2f",
                fr[k], zFT, thr, qn, pass, J[0][0], J[1][0], J[0][0] / J[1][0]));
        }
        PrecipField.WZM_FROM_QNET = false; PrecipField.M_FT_FRAC = 0.5;
        SoilMoisture.ENABLED = false;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}