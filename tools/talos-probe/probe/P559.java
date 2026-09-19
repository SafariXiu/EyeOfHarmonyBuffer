package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P559 -- §364 的决定性测量：PlateField 的窗口缓存是否残留【顺序依赖】？
 *
 * P558 把 P556 的差异唯一收敛到 elev（~1 mm），且「背靠背两次」是可重复的。
 * 唯一自洽的解释：某次【更早、缓存状态不同】的调用给出的值与现算不同。
 *
 * 本探针：固定若干测点取基准，然后反复用【很远的一批点】去冲窗口缓存，
 * 每轮回采基准点并与 v0 逐位比较。
 */
public class P559 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P559] " + s); System.out.println("[P559] " + s); }
    static final int SEED = 1022228679;

    static long bits(double d) { return Double.doubleToLongBits(d); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p559_report.txt"), "UTF-8");
        say("P559: 窗口缓存的顺序依赖（elevationWithCell / landScoreWithCell / isLandWithCell）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  WORLD_IS_TALOS=" + PlateField.WORLD_IS_TALOS);

        final int NP = 8;
        int[] px = new int[NP], pz = new int[NP];
        double[] e0 = new double[NP], s0 = new double[NP];
        boolean[] l0 = new boolean[NP];
        for (int i = 0; i < NP; i++) {
            px[i] = 137_000 + i * 3_100_000;
            pz[i] = 91_000 + i * 2_700_000;
            e0[i] = PlateField.elevationWithCell(px[i], pz[i], sd, cell);
            s0[i] = PlateField.landScoreWithCell(px[i], pz[i], sd, cell);
            l0[i] = PlateField.isLandWithCell(px[i], pz[i], sd, cell);
        }
        say("  基准已取（8 个测点）");

        int N = 30, M = 400;
        long badE = 0, badS = 0, badL = 0, calls = 0;
        double maxD = 0; int firstE = -1;
        for (int it = 1; it <= N; it++) {
            for (int j = 0; j < M; j++) {
                int fx = 5_000_000 + j * 2_400_000 + it * 37_000;
                int fz = 3_000_000 + j * 1_900_000 + it * 53_000;
                PlateField.elevationWithCell(fx, fz, sd, cell);
                calls++;
            }
            for (int i = 0; i < NP; i++) {
                double e = PlateField.elevationWithCell(px[i], pz[i], sd, cell);
                double s = PlateField.landScoreWithCell(px[i], pz[i], sd, cell);
                boolean l = PlateField.isLandWithCell(px[i], pz[i], sd, cell);
                if (bits(e) != bits(e0[i])) {
                    badE++;
                    double d = Math.abs(e - e0[i]);
                    if (d > maxD) maxD = d;
                    if (firstE < 0) { firstE = it; say(String.format(LF, "  !! 首次 elev 不符: 迭代 %d 测点 %d  %.12f vs %.12f  d=%.3e", it, i, e, e0[i], e - e0[i])); }
                }
                if (bits(s) != bits(s0[i])) badS++;
                if (l != l0[i]) badL++;
            }
        }
        say("");
        say(String.format(LF, "  冲缓存调用 = %d 次（%d 轮 x %d 点）", calls, N, M));
        say(String.format(LF, "  elevationWithCell  不符 = %d / %d   首次在第 %d 轮   最大 |d| = %.3e m", badE, N * NP, firstE, maxD));
        say(String.format(LF, "  landScoreWithCell  不符 = %d / %d", badS, N * NP));
        say(String.format(LF, "  isLandWithCell     不符 = %d / %d", badL, N * NP));
        say("");
        say("  判据：任何不符 => 窗口缓存残留顺序依赖（必须先修的真缺陷）；全零 => 本假设也被否掉");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
