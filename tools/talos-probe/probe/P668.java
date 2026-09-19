package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P668 -- §456 步 a': qSens = -180 W/m2 的根因。
//   假设（待验）：净柱加热里 beta 取的是 SoilMoisture.ENABLED ? betaAt : 1.0，
//   而默认 S3 是关的 ⇒ beta = 1（饱和面）⇒ 潜热冷却过大 ⇒ 皮温被压到空气之下 ⇒ qSens 为负。
//   而 §387/P570 早已实测：H 在 beta ~ 0.3 处翻正。
public class P668 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P668] " + s); System.out.println("[P668] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 盒均 {Qnet, qLat, qSens, qRad, cwv, ts, ta, beta} */
    static double[] box(long sd, int cell, double th, int b) {
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        double[] a = new double[8]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double[] d = StationaryWave.DIAG_HEAT.get();
                double bet = SoilMoisture.ENABLED ? SoilMoisture.betaAt(x, z, sd, cell, th, GRAD) : 1.0;
                a[0] += qn; a[1] += d[0]; a[2] += d[1]; a[3] += d[2];
                a[4] += d[3]; a[5] += d[4]; a[6] += d[4] - d[5]; a[7] += bet; n++;
            }
        for (int i = 0; i < 8; i++) a[i] /= n;
        a[6] = a[5] - a[6];   // ta = ts - (ts-ta)
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p668_report.txt"), "UTF-8");
        say("P668: qSens = -180 W/m2 的根因（§456 步 a'）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        String[] cn = {"1 S3关 beta=1 + 旧qRad", "2 S3关 beta=1 + 新qRad", "3 S3开 + 新qRad"};
        Object[][] cfg = {{false, false}, {false, true}, {true, true}};
        for (int k = 0; k < cn.length; k++) {
            SoilMoisture.ENABLED = (Boolean) cfg[k][0];
            StationaryWave.QRAD_ASR_MINUS_OLR = (Boolean) cfg[k][1];
            PrecipField.Q_FROM_SOURCE = true; PrecipField.SOURCE_SEASONAL_T = true;
            SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
            say("");
            say("  --- " + cn[k] + " （Q源+源季节项 开，JJA） ---");
            say("     盒子 |    Qnet   qLat    qSens    qRad  |  CWV   ts(K)   ta(K)  ts-ta   beta");
            for (int b = 0; b < 2; b++) {
                double[] a = box(sd, cell, thS, b);
                say(String.format(LF, "     %-6s| %+8.2f %+7.2f %+8.2f %+8.2f | %5.1f %6.2f %6.2f %+6.2f  %.3f",
                    NM[b], a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[5] - a[6], a[7]));
            }
            double[] A = box(sd, cell, thS, 0), B = box(sd, cell, thS, 1);
            say(String.format(LF, "     经向对比（亚-撒）: Qnet %+8.2f  qSens %+8.2f  qLat %+7.2f  qRad %+8.2f",
                A[0] - B[0], A[2] - B[2], A[1] - B[1], A[3] - B[3]));
        }
        SoilMoisture.ENABLED = false; StationaryWave.QRAD_ASR_MINUS_OLR = false;
        PrecipField.Q_FROM_SOURCE = false; PrecipField.SOURCE_SEASONAL_T = false;
        SoilMoisture.invalidate(); StationaryWave.invalidate(); SimClimate.clearCache();
        say("");
        say("文献判据：季风 F_net 约 +100~+150；沙漠近 0；对比为正。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}