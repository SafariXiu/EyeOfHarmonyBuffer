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
 * P560 -- 四条假设全灭之后的最后一条，也是最小的一条：
 *
 *   mmPerDay / surfaceTemp 在【新纬度行】上的第一次调用，与之后的调用，是否逐位相同？
 *
 * P556 事实：memo 关（先跑）vs memo 开（后跑）有 ~1e-6 差。
 * P558 事实：把海洋行【预热】后，两者逐位相同。
 * P557/P559 事实：所有地形原语在任意调用历史下逐位可重复。
 *   => 唯一剩下的解释：第一次调用时海洋行正在被解，而这次调用【读到了不同的东西】。
 *
 * 本探针不做任何 memo 操作，只问「第一次 vs 第二次」。
 * 若它们不同 => 这是一条【先于 memo 就存在】的真缺陷，且它会让验收读数依赖探针的遍历顺序。
 */
public class P560 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P560] " + s); System.out.println("[P560] " + s); }
    static final int SEED = 1022228679;
    static long bits(double d) { return Double.doubleToLongBits(d); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p560_report.txt"), "UTF-8");
        say("P560: 新纬度行上的【第一次 vs 之后】—— 无 memo 参与");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));

        int N = 12;
        int badSst = 0, badMm = 0, badSt = 0;
        double maxMm = 0, maxSst = 0;
        say("");
        say("  行   z            sstAnom#1        sstAnom#2        mmPerDay#1        mmPerDay#2        mm#3        d(mm#1-#2)");
        for (int i = 0; i < N; i++) {
            int x = 1_000_000 + i * 700_000;
            int z = 300_000 + i * 3_300_000;          // 互不相同的纬度行
            double s1 = Atmosphere.sstAnom(x, z);
            double s2 = Atmosphere.sstAnom(x, z);
            double t1 = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            double t2 = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            double m1 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double m2 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double m3 = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            if (bits(s1) != bits(s2)) { badSst++; maxSst = Math.max(maxSst, Math.abs(s2 - s1)); }
            if (bits(t1) != bits(t2)) badSt++;
            if (bits(m1) != bits(m2)) { badMm++; maxMm = Math.max(maxMm, Math.abs(m2 - m1)); }
            if (i < 6) {
                say(String.format(LF, "  %2d  %9d  %16.12f %16.12f  %16.12f %16.12f %16.12f  %.3e",
                    i, z, s1, s2, m1, m2, m3, m2 - m1));
            }
        }
        say("");
        say(String.format(LF, "  sstAnom     #1 vs #2 不符 = %d / %d   最大 |d| = %.3e K", badSst, N, maxSst));
        say(String.format(LF, "  surfaceTemp #1 vs #2 不符 = %d / %d", badSt, N));
        say(String.format(LF, "  mmPerDay    #1 vs #2 不符 = %d / %d   最大 |d| = %.3e mm/day", badMm, N, maxMm));
        say("");
        say("  判据：若 mmPerDay #1 vs #2 不符 => 【第一次调用读到的是尚未解完的海洋状态】");
        say("        => 这是一条先于 memo 就存在的真缺陷，且会让验收读数依赖遍历顺序。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
