package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P670 -- §459: 植被状态量 V 的行为 + 【代价实测】（用户硬约束）。
public class P670 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P670] " + s); System.out.println("[P670] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        double[] a = new double[5]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double[] d = StationaryWave.DIAG_HEAT.get();
                double kk = Atmosphere.kappaMemo(x, z, sd, cell);
                double ins = Radiation.insolation(WorldContract.latOf(z), Atmosphere.subsolarLat(th));
                double alb = Radiation.albedo(kk > 0.5, d[4]) + Vegetation.albedoAdd(1.0);
                a[0] += qn; a[1] += d[2]; a[2] += ins * (1.0 - alb); a[3] += pm; a[4] += 1.0; n++;
            }
        for (int i = 0; i < 5; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p670_report.txt"), "UTF-8");
        say("P670: 植被状态量 V（§459）—— 行为 + 代价");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        StationaryWave.Q_NET_HEATING = true;
        StationaryWave.QRAD_ASR_MINUS_OLR = true;
        PrecipField.Q_FROM_SOURCE = true; PrecipField.SOURCE_SEASONAL_T = true;
        boolean[] veg = {false, true};
        String[] nm = {"V 关（逐位不变）", "V 开（ALB_DESERT=0.35, P_C=0.30）"};
        for (int i = 0; i < 2; i++) {
            Vegetation.ENABLED = veg[i];
            Vegetation.invalidate();
            SoilMoisture.ENABLED = true;
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            Vegetation.solveCount = 0; Vegetation.iterTotal = 0; Vegetation.SOLVE_NANOS = 0; Vegetation.reentryBlocked = 0;
            long t0 = System.nanoTime();
            say("");
            say("  --- " + nm[i] + " ---");
            say("     盒子 |    Qnet    qRad     ASR   |  P(mm/day)");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %+8.2f %+8.2f %+8.2f | %7.3f", NM[b], a[0], a[1], a[2], a[3]));
                if (veg[i]) {
                    int x = xOfLon(b == 0 ? 95.0 : 15.0), z = zOfLat(b == 0 ? 25.0 : 27.5);
                    final double pp = PrecipField.mmPerDay(x, z, sd, cell, thS, GRAD);
                    double vv = Vegetation.vegAt(x, z, sd, cell, v2 -> pp);
                    say(String.format(LF, "           盒心 V = %.4f  (P=%.3f, P_C=%.2f), albedoAdd = %.4f",
                        vv, pp, Vegetation.P_C2, Vegetation.albedoAdd(vv)));
                }
            }
            double ms = (System.nanoTime() - t0) / 1.0e6;
            say(String.format(LF, "     代价：解点数=%d  迭代总数=%d  平均迭代=%.2f  累计纳秒=%.1f ms  墙钟段=%.1f ms  重入拦截=%d",
                Vegetation.solveCount, Vegetation.iterTotal,
                Vegetation.solveCount == 0 ? 0.0 : (double) Vegetation.iterTotal / Vegetation.solveCount,
                Vegetation.SOLVE_NANOS / 1.0e6, ms, Vegetation.reentryBlocked));
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