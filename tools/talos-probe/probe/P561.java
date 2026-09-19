package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P561 -- 修正 P560 的顺序缺陷后，测【唯一没测过的情形】：
 *
 *   mmPerDay 作为【第一个】触碰该纬度行的调用（海洋行尚未解），
 *   它的返回值与之后（海洋行已暖）的调用是否逐位相同？
 *
 * P560 的缺陷：它先调 sstAnom/surfaceTemp，那已经把行预热了 => #1 拿到的已经是暖值。
 * P556 的 OFF 分支做的正是"mmPerDay 第一个上" => 这就是那个未测的情形。
 *
 * 本探针不做任何 memo 操作。若 #1 != #2 => memo 无罪，差异来自这次冷启动。
 */
public class P561 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P561] " + s); System.out.println("[P561] " + s); }
    static final int SEED = 1022228679;
    static long bits(double d) { return Double.doubleToLongBits(d); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p561_report.txt"), "UTF-8");
        say("P561: mmPerDay【第一个上】（海洋行冷）vs 之后（暖）—— 无 memo 参与");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));

        int N = 14;
        int bad = 0, badSt = 0, badSst = 0;
        double maxD = 0;
        say("");
        say("  行   z             mm#1(冷)           mm#2(暖)           mm#3         d(#1-#2)      sst#1        sst#2");
        for (int i = 0; i < N; i++) {
            int x = 1_700_000 + i * 610_000;
            int z = 700_000 + i * 2_900_000;               // 互不相同的纬度行
            // ★ mmPerDay 必须是第一个碰这个纬度行的调用
            double m1 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double m2 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double m3 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double s1 = Atmosphere.sstAnom(x, z);
            double s2 = Atmosphere.sstAnom(x, z);
            double t1 = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            double t2 = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            if (bits(m1) != bits(m2)) { bad++; maxD = Math.max(maxD, Math.abs(m2 - m1)); }
            if (bits(s1) != bits(s2)) badSst++;
            if (bits(t1) != bits(t2)) badSt++;
            if (i < 7) {
                say(String.format(LF, "  %2d  %9d  %18.12f %18.12f %18.12f  %.3e  %11.7f %11.7f",
                    i, z, m1, m2, m3, m2 - m1, s1, s2));
            }
        }
        say("");
        say(String.format(LF, "  mmPerDay   #1(冷) vs #2(暖) 不符 = %d / %d   最大 |d| = %.3e mm/day", bad, N, maxD));
        say(String.format(LF, "  surfaceTemp #1 vs #2 不符 = %d / %d", badSt, N));
        say(String.format(LF, "  sstAnom     #1 vs #2 不符 = %d / %d", badSst, N));
        say("");
        say("  判据：若 mmPerDay #1 != #2 => 【memo 无罪】，P556 的差异来自这次冷启动；");
        say("        且这是一条先于 memo 就存在的真缺陷：验收读数依赖探针的遍历顺序。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
