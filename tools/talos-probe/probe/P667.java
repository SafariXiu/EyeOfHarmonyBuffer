package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P667 -- §455: 柱净辐射 = ASR - OLR + LH + SH 的 A/B。
//   判据（文献）：热带柱 F_net 约 +100~+150（季风）/ 近 0（沙漠），且【对比为正】。
public class P667 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P667] " + s); System.out.println("[P667] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 盒均 {Qnet, qLat, qSens, qRad, cwv, ts, ASR, OLR, albedo} */
    static double[] box(long sd, int cell, double th, int b) {
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        double[] a = new double[9]; long n = 0;
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
                double alb = Radiation.albedo(kk > 0.5, d[4]);
                a[0] += qn; a[1] += d[0]; a[2] += d[1]; a[3] += d[2];
                a[4] += d[3]; a[5] += d[4]; a[6] += ins * (1.0 - alb);
                a[7] += StationaryWave.olrClear(d[4], d[3]); a[8] += alb; n++;
            }
        for (int i = 0; i < 9; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p667_report.txt"), "UTF-8");
        say("P667: 柱净辐射 = ASR - OLR + LH + SH（§455）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        say(String.format(LF, "     OLR 核对：CWV=10 -> %.1f W/m2（锚 340）；CWV=50 -> %.1f（锚 265）",
            StationaryWave.olrClear(295.0, 10.0), StationaryWave.olrClear(295.0, 50.0)));
        String[] cn = {"A 旧口径 qRad（sigma*Ts^4*(1-2EPS)）", "B 新口径 ASR-OLR，反照率不动", "C 新口径 + ALB_LAND_ADD=0.15"};
        Object[][] cfg = {{false, 0.0}, {true, 0.0}, {true, 0.15}};
        for (int k = 0; k < cn.length; k++) {
            StationaryWave.QRAD_ASR_MINUS_OLR = (Boolean) cfg[k][0];
            Radiation.ALB_LAND_ADD = (Double) cfg[k][1];
            PrecipField.Q_FROM_SOURCE = true;
            PrecipField.SOURCE_SEASONAL_T = true;
            SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
            say("");
            say("  --- " + cn[k] + " （Q源 + 源季节项 均开，B 步的口径）；JJA ---");
            say("     盒子 |    Qnet   qLat    qSens    qRad  |  CWV    ts(K)   ASR     OLR    albedo");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %+8.2f %+7.2f %+8.2f %+8.2f | %5.1f %7.2f %+7.2f %+7.2f  %.3f",
                    NM[b], a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8]));
            }
            double[] A = box(sd, cell, thS, 0), B = box(sd, cell, thS, 1);
            say(String.format(LF, "     经向对比（亚-撒）: Qnet %+8.2f  ASR %+7.2f  OLR %+7.2f  LH+SH %+7.2f",
                A[0] - B[0], A[6] - B[6], A[7] - B[7], (A[1] + A[2]) - (B[1] + B[2])));
        }
        StationaryWave.QRAD_ASR_MINUS_OLR = false; Radiation.ALB_LAND_ADD = 0.0;
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SimClimate.clearCache(); SoilMoisture.invalidate(); StationaryWave.invalidate();
        say("");
        say("文献判据：季风 F_net 约 +100~+150；沙漠近 0；对比为正。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}