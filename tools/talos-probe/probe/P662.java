package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P662 -- §448 水汽源温度的季节项：干净的 A/B。盒子与仪器与 P601/P646 完全一致。
//   判据一（物理）：源温度在 JJA 必须高于年薪；观测孟加拉湾 +1.13 K / 南海 +1.30 K。
//   判据二（现场）：主判据 亚洲/撒哈拉 JJA 降水比（观测 72.76）。
public class P662 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P662] " + s); System.out.println("[P662] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};
    static final double[] PH = {0.0, Math.PI/2, Math.PI, 3*Math.PI/2};

    static double boxMean(long sd, int cell, double th, int b) {
        double s = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                SoilMoisture.invalidate();
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                n++;
            }
        return s / n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p662_report.txt"), "UTF-8");
        say("P662: 水汽源温度的季节项（§448）—— 干净的 A/B");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = 0.0, thW = Math.PI;

        // ---- 段 1：源温度本身 ----
        say("");
        say("段 1  `upwindSea` 返回的源温度（K）—— 逆推起点是各区域中心");
        double[][] RG = {{88.5,19.3},{62.0,15.0},{115.0,15.0},{10.0,25.0},{50.0,30.0}};
        String[] RGN = {"孟加拉湾","阿拉伯海","南海","撒哈拉中心","阿拉伯半岛"};
        say("     区域        | 年平 SST'(K) | 源温度 OFF(K) ON(K)  差(K) | 该纬度季节项 JJA(K)");
        for (int r = 0; r < RG.length; r++) {
            int x = xOfLon(RG[r][0]), z = zOfLat(RG[r][1]);
            double latRad = WorldContract.latOf(z);
            double[] uv = Atmosphere.windAt(x, z, sd, cell, thS, GRAD);
            PrecipField.SOURCE_SEASONAL_T = false;
            double[] a = PrecipField.upwindSea(x, z, sd, cell, thS, GRAD, uv[0], uv[1]);
            PrecipField.SOURCE_SEASONAL_T = true;
            double[] b = PrecipField.upwindSea(x, z, sd, cell, thS, GRAD, uv[0], uv[1]);
            double zo = Atmosphere.seasonalAnomaly(latRad, 0.0, thS);
            say(String.format(LF, "     %-11s |  %+9.4f   | %8.3f %8.3f %+7.3f | %+9.4f%s",
                RGN[r], Atmosphere.sstAnom(x, z, thS), a[1], b[1], b[1]-a[1], zo,
                (a[0] < 0 ? "  (逆推失败→回落 tSfcSl)" : "")));
        }

        // ---- 段 2：主判据 A/B ----
        say("");
        say("段 2 主判据现场（观测锚：撒哈拉 JJA 0.105 ⇒ 判据 = 亚洲/撒哈拉 >= 10；观测比 72.76）");
        String[] nm = {
            "1 基线（全 OFF）",
            "2 §444 配对（Q源+WVLW 0.02）",
            "3 §448 配对+源季节项",
            "4 §447+§448（再开逐相位）",
            "5 仅源季节项（无 WVLW）"
        };
        // 列： {Q_FROM_SOURCE, WVLW_K, SOURCE_SEASONAL_T, PHASE_SEASONAL}
        Object[][] cfg = {
            {false, 0.0,   false, false},
            {true,  0.02,  false, false},
            {true,  0.02,  true,  false},
            {true,  0.02,  true,  true },
            {true,  0.0,   true,  false}
        };
        say("");
        say("     配置                        |  JJA 亚洲  撒哈拉   比 |  DJF 亚洲  撒哈拉   比 |  年 亚洲  撒哈拉   比");
        for (int k = 0; k < nm.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            StationaryWave.WVLW_K = (Double) cfg[k][1];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][2];
            com.EyeOfHarmonyBuffer.sim.ocean.OceanField.PHASE_SEASONAL = (Boolean) cfg[k][3];
            StationaryWave.invalidate();
            SoilMoisture.invalidate();
            SimClimate.clearCache();
            double aj = boxMean(sd, cell, thS, 0), sj = boxMean(sd, cell, thS, 1);
            double aw = boxMean(sd, cell, thW, 0), sw = boxMean(sd, cell, thW, 1);
            double aa = 0.5*(aj+aw), sa = 0.5*(sj+sw);
            say(String.format(LF, "     %-27s | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f | %7.3f %7.3f %6.2f",
                nm[k], aj, sj, aj/sj, aw, sw, aw/sw, aa, sa, aa/sa));
        }
        PrecipField.Q_FROM_SOURCE = false; StationaryWave.WVLW_K = 0.0;
        PrecipField.SOURCE_SEASONAL_T = false;
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.PHASE_SEASONAL = false;
        StationaryWave.invalidate(); SoilMoisture.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}