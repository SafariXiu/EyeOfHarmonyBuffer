package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P669 -- §456 a'': 配对守卫的自检。
//   断言：错配（Q_NET_HEATING + S3 关）时 betaFallbackLandCalls > 0；
//         正配（两者都开）时 == 0。
public class P669 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P669] " + s); System.out.println("[P669] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p669_report.txt"), "UTF-8");
        say("P669: 配对守卫自检（§456 a''）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        StationaryWave.Q_NET_HEATING = true;
        StationaryWave.QRAD_ASR_MINUS_OLR = true;
        PrecipField.Q_FROM_SOURCE = true; PrecipField.SOURCE_SEASONAL_T = true;
        boolean[] s3 = {false, true};
        String[] nm = {"错配：Q_NET_HEATING=true, SoilMoisture.ENABLED=false", "正配：两者都开"};
        int fail = 0;
        for (int i = 0; i < 2; i++) {
            SoilMoisture.ENABLED = s3[i];
            StationaryWave.resetBetaGuard();
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            int n = 0;
            for (double latd = 17.5; latd <= 35.0; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    int x = xOfLon(lon), z = zOfLat(latd);
                    double pm = PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                    StationaryWave.netColumnHeating(x, z, sd, cell, thS, GRAD, pm);
                    n++;
                }
            long g = StationaryWave.betaFallbackLandCalls;
            boolean ok = s3[i] ? (g == 0) : (g > 0);
            if (!ok) fail++;
            say(String.format(LF, "     %-52s 采样=%d  守卫计数=%-7d  %s", nm[i], n, g, ok ? "OK" : "**FAIL**"));
        }
        SoilMoisture.ENABLED = false; StationaryWave.Q_NET_HEATING = false;
        StationaryWave.QRAD_ASR_MINUS_OLR = false;
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        StationaryWave.resetBetaGuard();
        SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        say("");
        say("GUARD_SELFTEST_FAILURES=" + fail);
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}