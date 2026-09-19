package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P695 -- §490: 乙₁ 的【最省形式】先量：ADVB_MAX_EVAL（平流路径上的源汇采样点数）扫描。
//   判据：若 1.91 随采样密度上升并趋于饱和，则 2-D 场的收益有限（逐点法够用）；
//         若它远未饱和，则 1.91 确实是下界 ⇒ 需要真 2-D 求解。
public class P695 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P695] " + s); System.out.println("[P695] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70,120,15,35},{0,30,20,35}};

    static double bm(long sd, int cell, double th, int b) {
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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p695_report.txt"), "UTF-8");
        say("P695: 平流源汇采样密度扫描（§490，乙₁ 的最省形式）");
        say("     观测锚：JJA 亚洲 7.667 / 撒哈拉 0.105 = 72.76");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        PrecipField.Q_ADVECT_BUDGET = true;
        int[] ev = {2, 4, 8, 16, 32};
        say("");
        say("     采样点 | 亚洲 JJA  撒哈拉 JJA   比 | 亚洲 DJF  撒哈拉 DJF   比 | ms/点 | 求值点/次");
        for (int k = 0; k < ev.length; k++) {
            PrecipField.ADVB_MAX_EVAL = ev[k];
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            PrecipField.advCalls = 0; PrecipField.advEvalPoints = 0; PrecipField.advWalkSteps = 0;
            long t0 = System.nanoTime();
            double aj = bm(sd, cell, thS, 0), sj = bm(sd, cell, thS, 1);
            double aw = bm(sd, cell, thW, 0), sw = bm(sd, cell, thW, 1);
            double ms = (System.nanoTime() - t0) / 1.0e6;
            say(String.format(LF, "     %6d | %8.3f %8.3f %6.2f | %8.3f %8.3f %6.2f | %5.2f | %.2f",
                ev[k], aj, sj, aj / sj, aw, sw, aw / sw, ms / 320.0,
                PrecipField.advCalls == 0 ? 0.0 : (double) PrecipField.advEvalPoints / PrecipField.advCalls));
        }
        PrecipField.ADVB_MAX_EVAL = 4; PrecipField.Q_ADVECT_BUDGET = false;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}