package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P654 -- §441 选项 V' 的 go/no-go：Charney 反照率环路的增益。
//   扫 Radiation.ALB_LAND_ADD，看 (a) Qnet 对比、(b) S2 的 div(V) 对比、(c) P 对比 怎么响应。
//   若「撒哈拉反照率↑ => 撒哈拉变冷 => 撒哈拉辐散 => 雨少」成立，则环路是正的且 V' 可行。
public class P654 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P654] " + s); System.out.println("[P654] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double[] box(long sd, int cell, double th, int b) {
        double sP = 0, sW = 0, sQ = 0, sT = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double w = d[1];
                double q = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                double ts = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                sP += pm; sW += w; sQ += q; sT += ts; n++;
            }
        return new double[]{sP / n, sW / n, sQ / n, sT / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p654_report.txt"), "UTF-8");
        say("P654: Charney 反照率环路的 go/no-go（S2 开 · Q 取净加热）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        StationaryWave.ENABLED = true; StationaryWave.Q_NET_HEATING = true;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] adds = {0.0, 0.10, 0.20};
        say("");
        say("      dALB  季节 盒子   |  P(mm/day)    div(V)        Qnet(W/m2)   地表T(K)");
        for (double a : adds) {
            Radiation.ALB_LAND_ADD = a;
            StationaryWave.invalidate();
            SoilMoisture.invalidate();
            SimClimate.clearCache();
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] A = box(sd, cell, th, 0), B = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %+5.2f  %s %s | %9.3f  %+.3e  %+9.2f  %8.2f", a, t, NM[0], A[0], A[1], A[2], A[3]));
                say(String.format(LF, "     %+5.2f  %s %s | %9.3f  %+.3e  %+9.2f  %8.2f", a, t, NM[1], B[0], B[1], B[2], B[3]));
                say(String.format(LF, "     %+5.2f  %s ---- 亚-撒 | %+9.3f  %+.3e  %+9.2f  %+8.2f   <== 差值", a, t,
                    A[0] - B[0], A[1] - B[1], A[2] - B[2], A[3] - B[3]));
            }
        }
        Radiation.ALB_LAND_ADD = 0.0;
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false; StationaryWave.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
