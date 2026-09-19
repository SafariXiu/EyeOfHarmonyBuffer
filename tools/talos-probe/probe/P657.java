package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P657 -- §444 配对实测：水汽源 x 水汽长波吸收。
//   量 Qnet 的经向对比是否翻成正号，以及 P、CWV 怎么变。
public class P657 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P657] " + s); System.out.println("[P657] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double sP=0, sQ=0, sW=0, sC=0, sD=0, sRad=0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double w = d[1], q = d[5];
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double[] h = StationaryWave.DIAG_HEAT.get();
                sP += pm; sW += w; sC += q; sQ += qn; sD += h[3]; sRad += h[2];
                n++;
            }
        return new double[]{sP/n, sW/n, sC/n, sQ/n, sD/n, sRad/n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p657_report.txt"), "UTF-8");
        say("P657: 水汽源 x 水汽长波吸收（配对实测）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        StationaryWave.ENABLED = true; StationaryWave.Q_NET_HEATING = true;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        boolean[] src = {false, true, false, true, true};
        double[] ks = {0.0, 0.0, 0.02, 0.02, 0.03};
        double[] Ls = {1.5e6, 1.5e6, 1.5e6, 1.5e6, 1.5e6};
        String[] cn = {"源关 k=0（基线）", "源开 k=0", "源关 k=0.02", "源开 k=0.02", "源开 k=0.03"};
        say("");
        say("     配置            季节 盒子   |  P(mm/day)     div(V)      CWV     Qnet(W/m2)   qRad(W/m2)");
        for (int i = 0; i < cn.length; i++) {
            PrecipField.Q_FROM_SOURCE = src[i];
            PrecipField.SOURCE_FETCH_L = Ls[i];
            StationaryWave.WVLW_K = ks[i];
            StationaryWave.invalidate(); SoilMoisture.invalidate();
            SimClimate.clearCache();
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] A = box(sd, cell, th, 0), B = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %-15s %s %s | %9.3f  %+.3e  %7.2f  %+10.2f  %+10.1f", cn[i], t, NM[0], A[0], A[1], A[4], A[3], A[5]));
                say(String.format(LF, "     %-15s %s %s | %9.3f  %+.3e  %7.2f  %+10.2f  %+10.1f", cn[i], t, NM[1], B[0], B[1], B[4], B[3], B[5]));
                say(String.format(LF, "     %-15s %s ---- 亚-撒| %+9.3f  %+.3e  %+7.2f  %+10.2f  %+10.1f   <== 差", cn[i], t,
                    A[0]-B[0], A[1]-B[1], A[4]-B[4], A[3]-B[3], A[5]-B[5]));
            }
        }
        PrecipField.Q_FROM_SOURCE = false; StationaryWave.WVLW_K = 0.0;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false; StationaryWave.invalidate();
        StationaryWave.DIAG_HEAT = null;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
