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
 * P563 -- 把 P556 的对照修好，并记录【不符的迭代号分布】。
 *
 * 与 P556 的差别：
 *   1) 先预热该 (x,z) 的海洋行（与 P558 同口径）=> 排除"memo 区域跨越冷海洋行"这个交互
 *   2) 补上 mmPerDay（P558 漏了它）
 *   3) 记录每一条不符的【迭代号】，最后打印分布
 *
 * 判据（写死在文档 §365）：
 *   预热后全零             => P556 的差异来自海洋交互
 *   预热后仍有不符但只在 i<5 => 预热/JIT 假象 => memo 可上
 *   预热后仍有不符且散布全程  => 真差异 => memo 不能上
 */
public class P563 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P563] " + s); System.out.println("[P563] " + s); }
    static final int SEED = 1022228679;
    static long bits(double d) { return Double.doubleToLongBits(d); }

    static final int NB = 5;
    static String[] nm = {"windAt[0]", "windAt[1]", "pressureAnomaly", "mmPerDay", "surfaceTemp"};
    static int[] cnt = new int[NB];
    static int[] firstIdx = new int[NB];
    static int[] lastIdx = new int[NB];
    static double[] maxD = new double[NB];
    static int[] hist = new int[20];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p563_report.txt"), "UTF-8");
        say("P563: 修好对照 + 不符迭代号分布");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        for (int i = 0; i < NB; i++) { firstIdx[i] = -1; lastIdx[i] = -1; }
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));

        int N = 400;
        for (int i = 0; i < N; i++) {
            int x = 100_000 + i * 137_000;
            int z = 50_000 + i * 91_000;
            Atmosphere.sstAnom(x, z);                      // ★ 预热海洋行（P556 没做）

            double[] wO = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double paO = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmO = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double stO = Atmosphere.surfaceTemp(x, z, sd, cell, th);

            boolean mc = Atmosphere.beginMemo();
            double[] wN = Atmosphere.windAt(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double paN = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmN = PrecipField.mmPerDay(x, z, sd, cell, th, SimClimate.GRAD_STEP);
            double stN = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            Atmosphere.endMemo(mc);

            double[] a = {wO[0], wO[1], paO, pmO, stO};
            double[] b = {wN[0], wN[1], paN, pmN, stN};
            for (int t = 0; t < NB; t++) {
                if (bits(a[t]) != bits(b[t])) {
                    cnt[t]++;
                    if (firstIdx[t] < 0) firstIdx[t] = i;
                    lastIdx[t] = i;
                    maxD[t] = Math.max(maxD[t], Math.abs(b[t] - a[t]));
                    if (i < 20) hist[i]++;
                }
            }
        }
        say("");
        say("  量           不符 / " + N + "   首次 i    末次 i     最大 |d|");
        for (int t = 0; t < NB; t++) {
            say(String.format(LF, "  %-16s %5d / %d   %6d   %6d   %.3e", nm[t], cnt[t], N, firstIdx[t], lastIdx[t], maxD[t]));
        }
        say("");
        say("  不符的【迭代号】分布（只统计 i < 20；hist[i] = 该轮有多少个量不符）:");
        StringBuilder sb = new StringBuilder("    ");
        for (int i = 0; i < 20; i++) sb.append(i).append(":").append(hist[i]).append("  ");
        say(sb.toString());
        say("");
        say("  判读：预热后全零 => P556 的差异来自海洋交互；只在 i<5 => JIT 假象；散布全程 => 真差异");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
