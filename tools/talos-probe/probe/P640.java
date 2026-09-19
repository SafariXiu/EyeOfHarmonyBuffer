package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P640 -- §431 逐项分解：中纬海洋的降水由哪几项组成（涡动项是否与平均项相消）。
// 同时覆盖 P296 报出「变干」的 47.5~62.5N 带，两季都做。
// DIAG：[3]wEff [4]wBase [5]q [7]kappa [8]主项 [9]加涡动后 [10]浅对流 [13]beta [2]divU
public class P640 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P640] " + s); System.out.println("[P640] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0, C = 86400.0 * 1000.0;
    static final int GRAD = 500_000;
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[] LATS = {30.0, 33.8, 36.3, 38.8, 41.3, 43.8, 46.3, 48.8, 51.3, 53.8, 56.3, 58.8, 61.3};

    static void band(double latd, long sd, int cell, double th) {
        double n = 0, s8 = 0, s9 = 0, s10 = 0, ed = 0, q = 0, we = 0, wb = 0, dv = 0, be = 0;
        for (int c = 0; c < 144; c++) {
            int x = (int) Math.round((c + 0.5) * 2.5 / 360.0 * CIRC), z = zOfLat(latd);
            if (Atmosphere.kappaMemo(x, z, sd, cell) > 0.5) continue;
            PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
            double[] d = PrecipField.DIAG.get();
            double a = d[8] * C, b = d[9] * C;
            s8 += a; s9 += b; s10 += d[10] * C; ed += (b - a);
            q += d[5]; we += d[3]; wb += d[4]; dv += d[2]; be += d[13];
            n++;
        }
        if (n == 0) { say("      (无海洋点)"); return; }
        double lat = Math.toRadians(latd);
        double gate = PrecipField.gateOf(lat, th);
        double mfc = PrecipField.eddyMfc(lat, th) * C;
        say(String.format(LF, "      %5.1fN n=%3.0f | 主项 %6.3f  涡动 %+7.3f  合计 %6.3f | gate %.3f  MFC %+7.2f | q %.4f  wEff %+.2e  wBase %+.2e  divU %+.2e  beta %.3f",
            latd, n, s8 / n, ed / n, Math.max(s9 / n, s10 / n), gate, mfc, q / n, we / n, wb / n, dv / n, be / n));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p640_report.txt"), "UTF-8");
        say("P640: 逐项分解（中纬海洋，144 经度逐点；mm/day）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] ths = {thS, thW};
        String[] tn = {"JJA", "DJF"};
        String[] cn = {"旧生产 cl1/gm0/MIX3.00", "新标准 cl0/gm2/MIX=1/0.31"};
        for (int cfg = 0; cfg < 2; cfg++) {
            if (cfg == 0) { PrecipField.EDDY_CLOSURE = 1; PrecipField.EDDY_GATE_MODE = 0; PrecipField.EDDY_MIX = 3.00; }
            else { PrecipField.EDDY_CLOSURE = 0; PrecipField.EDDY_GATE_MODE = 2; PrecipField.EDDY_MIX = 1.0 / PrecipField.EADY_COEF; }
            SimClimate.clearCache();
            say("");
            say("  --- " + cn[cfg] + " ---");
            for (int s = 0; s < 2; s++) {
                say("      [" + tn[s] + "]");
                for (double la : LATS) band(la, sd, cell, ths[s]);
            }
        }
        PrecipField.EDDY_CLOSURE = 0; PrecipField.EDDY_GATE_MODE = 2; PrecipField.EDDY_MIX = 1.0 / PrecipField.EADY_COEF;
        say("");
        say(String.format(LF, "  开关态：SHALLOW_FLOOR=%s  EDDY_FULL_DIVERGENCE=%s  EDDY_MASK_OUTSIDE=%s  WZM_ITCZ_SHIFT=%s",
            PrecipField.SHALLOW_FLOOR, PrecipField.EDDY_FULL_DIVERGENCE, PrecipField.EDDY_MASK_OUTSIDE, PrecipField.WZM_ITCZ_SHIFT));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
