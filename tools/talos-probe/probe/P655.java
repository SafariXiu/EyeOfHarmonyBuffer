package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P655 -- §443 水汽长波吸收项的 go/no-go。
//   (a) 基线：把 netColumnHeating 的三项拆开 + CWV，看现状的归因到底对不对；
//   (b) 扫 WVLW_K，看 Qnet 的经向对比走到哪（文献量级 ±80 W/m2，且亚洲应为正）。
public class P655 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P655] " + s); System.out.println("[P655] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** {qLat, qSens, qRad, Qnet, CWV, P} 的盒均值。 */
    static double[] box(long sd, int cell, double th, int b) {
        double a0=0,a1=0,a2=0,a3=0,a4=0,a5=0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double[] d = StationaryWave.DIAG_HEAT.get();
                a0 += d[0]; a1 += d[1]; a2 += d[2]; a3 += qn; a4 += d[3]; a5 += pm;
                n++;
            }
        return new double[]{a0/n, a1/n, a2/n, a3/n, a4/n, a5/n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p655_report.txt"), "UTF-8");
        say("P655: 水汽长波吸收项（§443）的 go/no-go");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        StationaryWave.DIAG_HEAT = ThreadLocal.withInitial(() -> new double[6]);
        StationaryWave.ENABLED = true; StationaryWave.Q_NET_HEATING = true;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] ks = {0.0, 0.02, 0.05};
        say("");
        say("      k     季节 盒子   |  qLat     qSens     qRad      Qnet  |   CWV(kg/m2)   P");
        for (double k : ks) {
            StationaryWave.WVLW_K = k;
            StationaryWave.invalidate(); SoilMoisture.invalidate();
            SimClimate.clearCache();
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] A = box(sd, cell, th, 0), B = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %5.3f  %s %s | %+8.1f %+9.1f %+9.1f %+9.2f | %10.2f  %7.3f",
                    k, t, NM[0], A[0], A[1], A[2], A[3], A[4], A[5]));
                say(String.format(LF, "     %5.3f  %s %s | %+8.1f %+9.1f %+9.1f %+9.2f | %10.2f  %7.3f",
                    k, t, NM[1], B[0], B[1], B[2], B[3], B[4], B[5]));
                say(String.format(LF, "     %5.3f  %s ---- 亚-撒 | %+8.1f %+9.1f %+9.1f %+9.2f | %+10.2f  %+7.3f  <== 差值",
                    k, t, A[0]-B[0], A[1]-B[1], A[2]-B[2], A[3]-B[3], A[4]-B[4], A[5]-B[5]));
            }
        }
        StationaryWave.WVLW_K = 0.0;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false; StationaryWave.invalidate();
        StationaryWave.DIAG_HEAT = null;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
