package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P658 -- §445 闭环：把强迫-环流自洽迭代打开，量它把主判据推到哪。
//   CLOSED_LOOP=false 时 itMax=1（开环，现状）；true 时迭代到 LOOP_TOL 或 LOOP_MAX_ITER。
public class P658 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P658] " + s); System.out.println("[P658] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double sP=0, sW=0, sQ=0, sC=0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double w = d[1], q = d[5];
                double qn = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                sP += pm; sW += w; sC += q; sQ += qn; n++;
            }
        return new double[]{sP/n, sW/n, sC/n, sQ/n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p658_report.txt"), "UTF-8");
        say("P658: 闭环（CLOSED_LOOP）实测");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        StationaryWave.ENABLED = true; StationaryWave.Q_NET_HEATING = true;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        boolean[] src = {false, true, true, true};
        double[] ks = {0.0, 0.0, 0.02, 0.03};
        boolean[] loop = {false, false, true, true};
        String[] cn = {"基线（源关 k=0 开环）", "源开 k=0 开环", "源开 k=0.02 【闭环】", "源开 k=0.03 【闭环】"};
        for (int i = 0; i < cn.length; i++) {
            PrecipField.Q_FROM_SOURCE = src[i];
            StationaryWave.WVLW_K = ks[i];
            StationaryWave.CLOSED_LOOP = loop[i];
            StationaryWave.invalidate(); SoilMoisture.invalidate();
            SimClimate.clearCache();
            say("");
            say("  --- " + cn[i] + " ---");
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] A = box(sd, cell, th, 0);
                int it = StationaryWave.lastIterations;
                double ch = StationaryWave.lastLoopChange, gn = StationaryWave.lastLoopGain;
                double[] B = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %s 迭代=%2d 变化=%.4f 增益=%.3f", t, it, ch, gn));
                say(String.format(LF, "       %s | P %8.3f   div(V) %+.3e   CWV %7.2f   Qnet %+9.2f", NM[0], A[0], A[1], A[2], A[3]));
                say(String.format(LF, "       %s | P %8.3f   div(V) %+.3e   CWV %7.2f   Qnet %+9.2f", NM[1], B[0], B[1], B[2], B[3]));
                say(String.format(LF, "       ---- 亚-撒 | P %+8.3f   div(V) %+.3e   CWV %+7.2f   Qnet %+9.2f   => P 比 %.2f",
                    A[0]-B[0], A[1]-B[1], A[2]-B[2], A[3]-B[3], B[0] != 0 ? A[0]/B[0] : Double.NaN));
            }
        }
        PrecipField.Q_FROM_SOURCE = false; StationaryWave.WVLW_K = 0.0; StationaryWave.CLOSED_LOOP = false;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false; StationaryWave.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
