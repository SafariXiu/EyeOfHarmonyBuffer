package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P665 -- §452: 柱水汽 W 的量级核对。B（柱水汽收支）的前提是 W 是错的；
//   如果 W 已经对了，缺的就不是 W，而是 wEff。
//   观测锚 = NCEP/NCAR R1 月平均 LTM 1991-2020 的 TCWV（refs/gen_tcwv_box.py）。
public class P665 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P665] " + s); System.out.println("[P665] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};
    static final double KC = PrecipField.RHO_AIR * PrecipField.H_MOIST;

    static double[] box(long sd, int cell, double th, int b) {
        double sW = 0, sP = 0, sE = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                SoilMoisture.invalidate();
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                sW += d[5] * KC; sP += pm; sE += d[3]; n++;
            }
        return new double[]{sW / n, sP / n, sE / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p665_report.txt"), "UTF-8");
        say("P665: 柱水汽 W 的量级核对（§452）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say(String.format(LF, "     q -> kg/m2 换算 = RHO_AIR*H_MOIST = %.1f", KC));
        say("     观测锚（NCEP/NCAR R1 LTM 1991-2020，TCWV kg/m2）：");
        say("       亚洲   JJA 23.48  年 27.62  DJF 31.94");
        say("       撒哈拉 JJA 16.68  年 23.86  DJF 31.56");
        say("       比     JJA  1.41  年  1.16  DJF  1.01");
        String[] cn = {"1 基线（全 OFF）", "2 §444 配对（Q源 + WVLW 0.02）", "3 §448 配对 + 源季节项"};
        Object[][] cfg = {{false, 0.0, false}, {true, 0.02, false}, {true, 0.02, true}};
        for (int k = 0; k < cn.length; k++) {
            PrecipField.Q_FROM_SOURCE = (Boolean) cfg[k][0];
            StationaryWave.WVLW_K = (Double) cfg[k][1];
            PrecipField.SOURCE_SEASONAL_T = (Boolean) cfg[k][2];
            StationaryWave.invalidate(); SoilMoisture.invalidate(); SimClimate.clearCache();
            say("");
            say("  --- " + cn[k] + " ---");
            say("     季节 | 亚洲 W  撒哈拉 W   W比 | 亚洲 P  撒哈拉 P   P比 | 亚洲 wEff 撒哈拉 wEff  w比");
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] a = box(sd, cell, th, 0), b = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %s  | %7.2f %9.2f %6.2f | %6.3f %8.3f %6.2f | %9.2e %10.2e %6.2f",
                    t, a[0], b[0], a[0] / b[0], a[1], b[1], a[1] / b[1], a[2], b[2], a[2] / b[2]));
            }
        }
        PrecipField.Q_FROM_SOURCE = false; StationaryWave.WVLW_K = 0.0;
        PrecipField.SOURCE_SEASONAL_T = false;
        StationaryWave.invalidate(); SoilMoisture.invalidate(); SimClimate.clearCache();
        say("");
        say("判读：若配对的 W 比已对上观测 1.41，而 P 比只有 3.5（观测 72.76）");
        say("      则【错的不是 W，是 wEff】=> B 不是缺的那一项。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}