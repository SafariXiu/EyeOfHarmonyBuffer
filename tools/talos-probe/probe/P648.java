package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P648 -- §435 选项 A 的 go/no-go：用【模型自己的风场与比湿场】算柱水汽通量辐合 -div(qV)，
// 看沙漠（应为辐散）与季风区（应为辐合）到底分不分得开、差多少倍。
// 全部量都取自模型自己的场（DIAG[5] 的 q 与 Atmosphere.windAt 的 u/v），不引入任何地球数据。
public class P648 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P648] " + s); System.out.println("[P648] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double COL = PrecipField.RHO_AIR * PrecipField.H_MOIST;   // 与 columnWater 同一口径
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    /** 该点的比湿（模型自己的 q）。 */
    static double qAt(int x, int z, long sd, int cell, double th) {
        PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
        return PrecipField.DIAG.get()[5];
    }

    static double[] box(long sd, int cell, double th, int b) {
        double sq = 0, sw = 0, sp = 0, sdv = 0, smfc = 0, ssp = 0, sabsv = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double p0 = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                double q0 = d[5];
                double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                // 四个邻居的 q 与 (u,v)
                double[] up = Atmosphere.windAt(x + GRAD, z, sd, cell, th, GRAD);
                double[] um = Atmosphere.windAt(x - GRAD, z, sd, cell, th, GRAD);
                double[] vp = Atmosphere.windAt(x, z + GRAD, sd, cell, th, GRAD);
                double[] vm = Atmosphere.windAt(x, z - GRAD, sd, cell, th, GRAD);
                double qp = qAt(x + GRAD, z, sd, cell, th), qm = qAt(x - GRAD, z, sd, cell, th);
                double qn = qAt(x, z + GRAD, sd, cell, th), qs = qAt(x, z - GRAD, sd, cell, th);
                // div(qV) 中心差分
                double divqV = ((qp * up[0]) - (qm * um[0])) / (2.0 * GRAD)
                            + ((qn * vp[1]) - (qs * vm[1])) / (2.0 * GRAD);
                double mfc = -divqV * COL * 86400.0;         // mm/day
                double spd = Math.hypot(u0[0], u0[1]);
                sq += q0; sw += q0 * COL; sp += p0; sdv += d[2]; smfc += mfc;
                ssp += spd; sabsv += Math.hypot(u0[0], u0[1]) * 1.0;
                n++;
            }
        return new double[]{sq / n, sw / n, sp / n, sdv / n, smfc / n, ssp / n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p648_report.txt"), "UTF-8");
        say("P648: 柱水汽通量辐合 -div(qV)（模型自己的风场与比湿场；盒子同 P601）");
        say(String.format(LF, "  口径：q 取 DIAG[5]，柱水 = q*rho*H_MOIST = q*%.1f；差分步长 = %d km", COL, GRAD / 1000));
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");
        say("     季节  盒子   |  q(kg/kg)   柱水(kg/m2)   P(mm/day)   divU(1/s)    -div(qV) mm/day   风速(m/s)");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            double[] a = box(sd, cell, th, 0), b = box(sd, cell, th, 1);
            say(String.format(LF, "     %s  亚洲   | %9.6f  %10.3f  %9.3f  %+.3e  %+13.3f   %8.3f",
                (s == 0 ? "JJA" : "DJF"), a[0], a[1], a[2], a[3], a[4], a[5]));
            say(String.format(LF, "     %s  撒哈拉 | %9.6f  %10.3f  %9.3f  %+.3e  %+13.3f   %8.3f",
                (s == 0 ? "JJA" : "DJF"), b[0], b[1], b[2], b[3], b[4], b[5]));
            say(String.format(LF, "     %s  ---- 亚-撒 差 = %+9.3f mm/day ； 比(亚/撒) = %s",
                (s == 0 ? "JJA" : "DJF"), a[4] - b[4], (Math.abs(b[4]) > 1e-9 ? String.format(LF, "%.2f", a[4] / b[4]) : "n/a")));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
