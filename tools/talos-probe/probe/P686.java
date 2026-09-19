package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P686 -- §481: 那 27% 的 M <= 0 落在哪？（纬度带 × 海陆）
public class P686 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P686] " + s); System.out.println("[P686] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p686_report.txt"), "UTF-8");
        say("P686: M <= 0 的纬度/海陆分布（§481）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] nm = {"C 仅 WZM_FROM_QNET", "D 配对 + 收支口径"};
        boolean[] blb = {false, true};
        for (int k = 0; k < 2; k++) {
            PrecipField.WZM_FROM_QNET = true;
            PrecipField.Q_FROM_BLBUDGET = blb[k];
            SoilMoisture.ENABLED = true;
            SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            PrecipField.qnetNegM = 0; PrecipField.qnetCalls = 0;
            long[] lnd = new long[18], sea = new long[18];
            for (int q = 0; q < 18; q++) { PrecipField.qnetNegMBandLand[q] = 0; PrecipField.qnetNegMBandSea[q] = 0; }
            long nTot = 0;
            for (double latd = 0.0; latd <= 85.0; latd += 5.0)
                for (int c = 0; c < 36; c++) {
                    int x = xOfLon((c + 0.5) * 10.0), z = zOfLat(latd);
                    PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                    nTot++;
                    PrecipField.mmPerDay(x, z, sd, cell, thW, GRAD);
                    nTot++;
                }
            say("");
            say("  --- " + nm[k] + " ---  （采样 " + nTot + " 次，qnetCalls=" + PrecipField.qnetCalls + "）");
            say("     纬度带 | 陆地 M<=0 | 海洋 M<=0 | 合计");
            long tl = 0, ts = 0;
            for (int q = 0; q < 18; q++) {
                tl += PrecipField.qnetNegMBandLand[q]; ts += PrecipField.qnetNegMBandSea[q];
                if (PrecipField.qnetNegMBandLand[q] + PrecipField.qnetNegMBandSea[q] == 0) continue;
                say(String.format(LF, "     %2d-%2d  | %10d | %10d | %6d", q * 5, q * 5 + 5,
                    PrecipField.qnetNegMBandLand[q], PrecipField.qnetNegMBandSea[q],
                    PrecipField.qnetNegMBandLand[q] + PrecipField.qnetNegMBandSea[q]));
            }
            say(String.format(LF, "     合计   | %10d | %10d | %6d   （占调用 %.1f%%）",
                tl, ts, tl + ts, 100.0 * (tl + ts) / Math.max(1, PrecipField.qnetCalls)));
        }
        PrecipField.WZM_FROM_QNET = false; PrecipField.Q_FROM_BLBUDGET = false;
        SoilMoisture.ENABLED = false;
        SoilMoisture.invalidate(); Vegetation.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}