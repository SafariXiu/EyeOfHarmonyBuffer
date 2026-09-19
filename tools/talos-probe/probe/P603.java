package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P603 -- 「沙漠不干」到底有多少是水汽缺陷造成的？（有界实验，不需要新物理）
// moisture() = RH_SEA(0.80 全球常数) * qSat(T) * depletion，kappa 完全不调制地表干燥度。
// 用 public 的 precip(q, wEff) 直接把 q 按比例缩放，看降水怎么变。
public class P603 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P603] " + s); System.out.println("[P603] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};
    static final double[] OBS = {7.775, 0.105};

    /** 收集盒内每点的 (q, wEff, 观测需要)。 */
    static double[][] collect(long sd, int cell, double th, int b) {
        java.util.ArrayList<double[]> L = new java.util.ArrayList<>();
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                L.add(new double[]{d[5], d[3]});   // q, wEff
            }
        return L.toArray(new double[0][]);
    }

    static double boxMean(double[][] pts, double f) {
        double s = 0;
        for (double[] p : pts) s += PrecipField.precip(p[0] * f, p[1]) * 86400e3;
        return s / pts.length;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p603_report.txt"), "UTF-8");
        say("P603: 水汽缺陷能解释多少「沙漠不干」？");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("");
        say("  q 缩放因子 f  对降水的影响（f=1 即现状）：");
        say("    f        亚洲(观测 7.775)      撒哈拉(观测 0.105)");
        double[] fs = {1.0, 0.8, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1, 0.05};
        for (double f : fs) {
            double a = 0, s = 0;
            double[][] pa = collect(sd, cell, th, 0), ps = collect(sd, cell, th, 1);
            a = boxMean(pa, f); s = boxMean(ps, f);
            say(String.format(LF, "   %.2f      %8.3f (%.2fx)     %8.3f (%.2fx)",
                f, a, a / OBS[0], s, s / OBS[1]));
        }

        say("");
        say("  ★ 读法：");
        say("    - 撒哈拉要落到观测 0.105，需要 f 小到什么程度？看下表（插值）。");
        double[][] ps = collect(sd, cell, th, 1);
        double target = OBS[1];
        double lo = 0.001, hi = 1.0;
        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo + hi);
            if (boxMean(ps, mid) > target) hi = mid; else lo = mid;
        }
        say(String.format(LF, "      撒哈拉达到观测 0.105 需要 q 乘子 f = %.4f（现状 f=1，即需要把 q 压到 %.1f%%）",
            0.5 * (lo + hi), 50.0 * (lo + hi)));
        double[][] pa = collect(sd, cell, th, 0);
        double lo2 = 0.001, hi2 = 1.0;
        for (int it = 0; it < 60; it++) {
            double mid = 0.5 * (lo2 + hi2);
            if (boxMean(pa, mid) > OBS[0]) hi2 = mid; else lo2 = mid;
        }
        say(String.format(LF, "      亚洲达到观测 7.775 需要 q 乘子 f = %.3f（现状 f=1，即需要把 q 抬到 %.0f%%）",
            0.5 * (lo2 + hi2), 100.0 / (0.5 * (lo2 + hi2))));
        say("");
        say("    - 若两者所需的 f 差别很大 => 单靠一个全球乘子修不了，必须是【逐点的表面湿润度 beta】。");
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
