package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P446：**人造 divU 的影响范围**（按纬度带的 |divU| 分布 + wLoc 打满限幅的比例）。 */
public class P446 {

    static final int SEED = 1022228679;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P446] " + s); System.out.println("[P446] " + s); }

    static double divU(int x, int z, double th) {
        double[] ux = Atmosphere.windAt(x + GRAD, z, SEED, CELL, th, GRAD);
        double[] uw = Atmosphere.windAt(x - GRAD, z, SEED, CELL, th, GRAD);
        double[] un = Atmosphere.windAt(x, z + GRAD, SEED, CELL, th, GRAD);
        double[] us = Atmosphere.windAt(x, z - GRAD, SEED, CELL, th, GRAD);
        return (ux[0] - uw[0]) / (2.0 * GRAD) + (un[1] - us[1]) / (2.0 * GRAD);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p446_report.txt"), "UTF-8");
        say("P446：人造 divU 的影响范围");
        say(String.format(LF, "  W_LOC_MAX=%.2e m/s  H_BL=%.0f m  =>  限幅阈值 |divU| = %.3e 1/s",
            PrecipField.W_LOC_MAX, Atmosphere.H_BL, PrecipField.W_LOC_MAX / Atmosphere.H_BL));
        say("  物理参照：w_zm ~ 1e-3 m/s  =>  应有 |divU| ~ w_zm/H_BL ~ 1e-6 1/s");
        say("");
        say(String.format(LF, "  %-8s %8s %12s %12s %12s %10s %9s", "纬度带", "n", "|divU| p50", "|divU| p90", "|divU| max", "限幅比例", "中位 wZm"));
        for (int b = 0; b < 18; b++) {
            double lo = b * 5.0, hi = lo + 5.0;
            java.util.ArrayList<Double> vs = new java.util.ArrayList<>();
            int clamped = 0, n = 0;
            for (int xi = -6_000_000; xi <= 6_000_000; xi += 1_000_000) {
                for (int k = 0; k < 5; k++) {
                    double latDeg = lo + (k + 0.5) * (hi - lo) / 5.0;
                    int z = WorldContract.zOfLat(latDeg);
                    for (double th : PH4) {
                        double d = divU(xi, z, th);
                        vs.add(Math.abs(d)); n++;
                        if (Math.abs(-Atmosphere.H_BL * d) > PrecipField.W_LOC_MAX) clamped++;
                    }
                }
            }
            java.util.Collections.sort(vs);
            double p50 = vs.get(vs.size() / 2), p90 = vs.get((int) (vs.size() * 0.9));
            say(String.format(LF, "  %-8s %8d %12.3e %12.3e %12.3e %9.0f%% %9.2e",
                String.format(LF, "%.0f-%.0f", lo, hi), n, p50, p90, vs.get(vs.size() - 1),
                100.0 * clamped / n,
                ZonalTablesW(latDegOf(lo, hi))));
        }
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
    static double latDegOf(double lo, double hi) { return 0.5 * (lo + hi); }
    static double ZonalTablesW(double latDeg) { return com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.wZm(latDeg); }
}
