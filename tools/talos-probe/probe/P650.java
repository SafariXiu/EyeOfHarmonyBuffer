package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P650 -- §437 选项 A'':接 S2 定常波。量三件事：
//   (1) S2 给出的 div(V)（DIAG[1]）在亚洲/撒哈拉上是不是【亚洲辐合、撒哈拉辐散】；
//   (2) 它对总 divU 与 P 的贡献；
//   (3) 净柱加热的经向对比到底有多大（§412 记的是 +9.3 W/m2，需要约 +100）。
public class P650 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P650] " + s); System.out.println("[P650] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** {P, dWave, divU_tot, Qnet} 的盒均值。DIAG 是 ThreadLocal 引用，必须【立刻】取值。 */
    static double[] box(long sd, int cell, double th, int b) {
        double sP = 0, sW = 0, sD = 0, sQ = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double pm = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double a1 = d[1], a2 = d[2];
                double q = StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, pm);
                sP += pm; sW += a1; sD += a2; sQ += q; n++;
            }
        return new double[]{sP / n, sW / n, sD / n, sQ / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p650_report.txt"), "UTF-8");
        say("P650: S2 定常波接线实测（盒子同 P601）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        String[] cn = {"S2 关（基线）", "S2 开 · Q 取降水型", "S2 开 · Q 取净加热型"};
        for (int cfg = 0; cfg < 3; cfg++) {
            StationaryWave.ENABLED = (cfg > 0);
            StationaryWave.Q_NET_HEATING = (cfg == 2);
            StationaryWave.invalidate();
            SimClimate.clearCache();
            say("");
            say("  --- " + cn[cfg] + " ---");
            say("     季节  盒子   |      P(mm/day)      div(V)          divU(总)        Qnet(W/m2)");
            for (int s = 0; s < 2; s++) {
                double th = (s == 0) ? thS : thW;
                double[] a = box(sd, cell, th, 0), b = box(sd, cell, th, 1);
                String t = (s == 0) ? "JJA" : "DJF";
                say(String.format(LF, "     %s  %s | %12.3f   %+.3e   %+.3e   %+9.2f", t, NM[0], a[0], a[1], a[2], a[3]));
                say(String.format(LF, "     %s  %s | %12.3f   %+.3e   %+.3e   %+9.2f", t, NM[1], b[0], b[1], b[2], b[3]));
                say(String.format(LF, "     %s  ---- 亚-撒 | %12.3f   %+.3e   %+.3e   %+9.2f   <== 差值", t, a[0] - b[0], a[1] - b[1], a[2] - b[2], a[3] - b[3]));
            }
        }
        StationaryWave.ENABLED = false; StationaryWave.Q_NET_HEATING = false; StationaryWave.invalidate();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
