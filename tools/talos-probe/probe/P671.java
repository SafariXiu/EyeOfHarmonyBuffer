package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P671 -- §462: 文献口径的植被状态 V + 【闭环增益】+ 代价。
public class P671 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P671] " + s); System.out.println("[P671] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double sP = 0, sV = 0, sQ = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                sP += pm; sQ += qn;
                if (Vegetation.ENABLED) sV += PrecipField.DIAG.get()[15];
                n++;
            }
        return new double[]{sP / n, sV / n, sQ / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p671_report.txt"), "UTF-8");
        say("P671: 文献口径的植被 V（§462）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        say(String.format(LF, "     文献常数：P_C1=%.4f  P_C2=%.4f  D_B=%.4f mm/day  TAU=%.1f yr  g=%.4f",
            Vegetation.P_C1, Vegetation.P_C2, Vegetation.D_B, Vegetation.TAU, Vegetation.gain()));
        StationaryWave.Q_NET_HEATING = true;
        StationaryWave.QRAD_ASR_MINUS_OLR = true;
        PrecipField.Q_FROM_SOURCE = true; PrecipField.SOURCE_SEASONAL_T = true;
        SoilMoisture.ENABLED = true;
        boolean[] vg = {false, true};
        String[] nm = {"V 关（逐位不变）", "V 开"};
        for (int i = 0; i < 2; i++) {
            Vegetation.ENABLED = vg[i];
            Vegetation.invalidate();
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            Vegetation.solveCount = 0; Vegetation.iterTotal = 0; Vegetation.SOLVE_NANOS = 0;
            Vegetation.reentryBlocked = 0; Vegetation.bistableDetected = 0;
            long t0 = System.nanoTime();
            say("");
            say("  --- " + nm[i] + " ---");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| P %7.3f mm/day   V %6.4f (%.0f mm/yr)   Qnet %+8.2f W/m2",
                    NM[b], a[0], a[1], a[1] * 0 + a[0] * 365.0, a[2]));
            }
            double ms = (System.nanoTime() - t0) / 1.0e6;
            say(String.format(LF, "     代价：解点数=%d 迭代总数=%d 平均=%.2f 累计=%.1f ms 墙钟段=%.1f ms 重入=%d 双稳信号=%d",
                Vegetation.solveCount, Vegetation.iterTotal,
                Vegetation.solveCount == 0 ? 0.0 : (double) Vegetation.iterTotal / Vegetation.solveCount,
                Vegetation.SOLVE_NANOS / 1.0e6, ms, Vegetation.reentryBlocked, Vegetation.bistableDetected));
        }
        Vegetation.ENABLED = false; Vegetation.invalidate();
        StationaryWave.Q_NET_HEATING = false; StationaryWave.QRAD_ASR_MINUS_OLR = false;
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.ENABLED = false;
        SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}