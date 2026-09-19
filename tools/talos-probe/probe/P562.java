package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P562 -- 最后一个从未被测过的组合：
 *   pressureAnomaly / windAt 在【冷海洋行】上的第一次调用。
 *
 * 已有覆盖：
 *   P560/P561: sstAnom / surfaceTemp / mmPerDay 冷启动  -> 0 不符
 *   P558:      windAt / pressureAnomaly 【预热后】      -> 0 不符
 *   缺的正是： pressureAnomaly / windAt 【冷】
 *
 * pressureAnomaly 内部调 sstAnom(x,z)，而 sstAnom 在【正在解行】时经 SST_SUPPRESS 返回 0
 * (Atmosphere:598-601)。若第一次调用恰好撞上这个窗口，值就不同。
 * 本探针无 memo 参与。
 */
public class P562 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P562] " + s); System.out.println("[P562] " + s); }
    static final int SEED = 1022228679;
    static long bits(double d) { return Double.doubleToLongBits(d); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p562_report.txt"), "UTF-8");
        say("P562: pressureAnomaly / windAt 在【冷海洋行】上的第一次调用");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));

        int N = 14;
        int badPa = 0, badW = 0;
        double maxPa = 0, maxW = 0;
        say("");
        say("  行   z             Pa#1(冷)            Pa#2(暖)          d          w#1[0](冷)      w#2[0](暖)");
        for (int i = 0; i < N; i++) {
            int z = 400_000 + i * 3_100_000;                  // 互不相同的纬度行
            int xa = 2_300_000 + i * 530_000;
            double p1 = Atmosphere.pressureAnomaly(xa, z, sd, cell, th);   // 第一个上
            double p2 = Atmosphere.pressureAnomaly(xa, z, sd, cell, th);
            int xb = -1_900_000 - i * 470_000;
            double[] w1 = Atmosphere.windAt(xb, z, sd, cell, th, SimClimate.GRAD_STEP);  // 同一行、另一点
            double[] w2 = Atmosphere.windAt(xb, z, sd, cell, th, SimClimate.GRAD_STEP);
            if (bits(p1) != bits(p2)) { badPa++; maxPa = Math.max(maxPa, Math.abs(p2 - p1)); }
            if (bits(w1[0]) != bits(w2[0])) { badW++; maxW = Math.max(maxW, Math.abs(w2[0] - w1[0])); }
            if (i < 7) say(String.format(LF, "  %2d  %9d  %19.12f %19.12f  %.3e  %14.10f %14.10f",
                i, z, p1, p2, p2 - p1, w1[0], w2[0]));
        }
        say("");
        say(String.format(LF, "  pressureAnomaly #1(冷) vs #2(暖) 不符 = %d / %d   最大 |d| = %.3e Pa", badPa, N, maxPa));
        say(String.format(LF, "  windAt[0]       #1(冷) vs #2(暖) 不符 = %d / %d   最大 |d| = %.3e m/s", badW, N, maxW));
        say("");
        say("  判据：若非零 => P556 的差异就是【冷海洋行上的第一次 pressureAnomaly/windAt】=> memo 无罪");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
