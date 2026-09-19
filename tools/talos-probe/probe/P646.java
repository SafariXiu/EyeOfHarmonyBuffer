package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P646 -- 主判据现场：亚洲/撒哈拉 降水比（判据 >= 10），在目标第 (1)(2) 项的各开关组合下。
// 盒子与仪器与 P601 完全一致（亚洲 70-120E/15-35N，撒哈拉 0-30E/20-35N，EarthRef + OceanField）。
public class P646 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P646] " + s); System.out.println("[P646] " + s); }
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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p646_report.txt"), "UTF-8");
        say("P646: 主判据 亚洲/撒哈拉 降水比（判据 >= 10）—— 目标第 (1)(2) 项开关的干净 A/B");
        say("  观测锚：撒哈拉 0.105 mm/day（§404）⇒ 判据 >= 10 等价于亚洲 >= 1.05");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        boolean s3 = SoilMoisture.ENABLED, cpt = Atmosphere.CELL_PHASE_FROM_TEMP;
        boolean dw = Atmosphere.PA_DRY_WARMTH, itcz = PrecipField.WZM_ITCZ_SHIFT, hc = Atmosphere.SEASON_FROM_HEAT_CAPACITY;
        boolean wzm = PrecipField.WZM_FROM_TABLE;
        String[] nm = {
            "1 生产默认",
            "2 +S3 土壤湿度",
            "3 +位相由陆海温差驱动",
            "4 +S3 +位相",
            "5 +S3 +位相 +干暖",
            "6 +S3 +位相 +干暖 +ITCZ不平移",
            "7 +S3 +位相 +干暖 +热容季节",
            "8 仅 W_ZM 观测月表",
            "9 +S3 +位相 +干暖 +W_ZM观测表",
            "10 +q取地表温度",
            "11 +S3 +q取地表温度",
            "12 +S3 +位相 +干暖 +q取地表温度"
        };
        // 列： {S3, 位相, 干暖, ITCZ平移, 热容季节, q取地表温度}
        boolean[][] cfg = {
            {false, false, false, true,  false, false},
            {true,  false, false, true,  false, false},
            {false, true,  false, true,  false, false},
            {true,  true,  false, true,  false, false},
            {true,  true,  true,  true,  false, false},
            {true,  true,  true,  false, false, false},
            {true,  true,  true,  true,  true,  false},
            {false, false, false, true,  false, false},
            {true,  true,  true,  true,  false, false},
            {false, false, false, true,  false, true},
            {true,  false, false, true,  false, true},
            {true,  true,  true,  true,  false, true}
        };
        say("");
        say("     配置                        |  JJA 亚洲  撒哈拉   比 |  DJF 亚洲  撒哈拉   比 |  年 亚洲  撒哈拉   比");
        for (int k = 0; k < nm.length; k++) {
            SoilMoisture.ENABLED = cfg[k][0];
            Atmosphere.CELL_PHASE_FROM_TEMP = cfg[k][1];
            Atmosphere.PA_DRY_WARMTH = cfg[k][2];
            PrecipField.WZM_ITCZ_SHIFT = cfg[k][3];
            Atmosphere.SEASON_FROM_HEAT_CAPACITY = cfg[k][4];
            PrecipField.Q_AT_SURFACE_TEMP = cfg[k][5];
            PrecipField.WZM_FROM_TABLE = (k == 7 || k == 8);   // 第 8/9 格走观测月表
            SimClimate.clearCache();
            double aj = boxMean(sd, cell, thS, 0), sj = boxMean(sd, cell, thS, 1);
            double aw = boxMean(sd, cell, thW, 0), sw = boxMean(sd, cell, thW, 1);
            double aa = 0.5 * (aj + aw), sa = 0.5 * (sj + sw);
            say(String.format(LF, "     %-27s | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f | %7.3f %7.3f %6.2f",
                nm[k], aj, sj, aj / sj, aw, sw, aw / sw, aa, sa, aa / sa));
        }
        SoilMoisture.ENABLED = s3; Atmosphere.CELL_PHASE_FROM_TEMP = cpt;
        Atmosphere.PA_DRY_WARMTH = dw; PrecipField.WZM_ITCZ_SHIFT = itcz;
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = hc; PrecipField.WZM_FROM_TABLE = wzm;
        PrecipField.Q_AT_SURFACE_TEMP = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
