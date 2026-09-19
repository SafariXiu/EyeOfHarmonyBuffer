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
 * P566 -- 决定性：把【该点 + 全部模板点】的海洋行都预热后，memo 关 vs 开还有差别吗？
 *
 * §367 的判读：P563 观测到的模式（surfaceTemp 一致而 pressureAnomaly 不同）在代数上不可能，
 * 所以优先按【探针缺陷】处理 -- 缺陷候选是：windAt/mmPerDay 会去取 (x±GRAD, z) 与 (x, z±GRAD)，
 * 后两者在【别的纬度】上，它们触发的海洋行求解会经 SST_SUPPRESS 让 sstAnom 返回 0；
 * OFF 分支先跑撞上窗口，ON 分支再跑时那些行已暖 => 差异与 memo 无关。
 *
 * 本探针把那些点【先全部预热】，再跑两支对照。
 * 判据：全零 => 探针缺陷确证，memo 无罪；仍有非零 => 真差异。
 */
public class P566 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P566] " + s); System.out.println("[P566] " + s); }
    static final int SEED = 1022228679;
    static final int GS = SimClimate.GRAD_STEP;
    static long bits(double d) { return Double.doubleToLongBits(d); }

    static final int NB = 5;
    static String[] nm = {"windAt[0]", "windAt[1]", "pressureAnomaly", "mmPerDay", "surfaceTemp"};
    static int[] cnt = new int[NB], firstI = new int[NB], lastI = new int[NB];
    static double[] md = new double[NB];

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p566_report.txt"), "UTF-8");
        say("P566: 全部模板点预热后，memo 关 vs 开（决定性）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        double th = Atmosphere.theta(0.0);
        for (int i = 0; i < NB; i++) { firstI[i] = -1; lastI[i] = -1; }
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  GRAD_STEP=" + GS + "  SST_PROVIDER=" + (Atmosphere.SST_PROVIDER != null));

        int N = 150;
        for (int i = 0; i < N; i++) {
            int x = 100_000 + i * 137_000;
            int z = 50_000 + i * 91_000;
            // ★ 预热该点【及其全部模板点】的海洋行
            Atmosphere.sstAnom(x, z);
            Atmosphere.sstAnom(x + GS, z); Atmosphere.sstAnom(x - GS, z);
            Atmosphere.sstAnom(x, z + GS); Atmosphere.sstAnom(x, z - GS);
            Atmosphere.sstAnom(x - 150_000, z);           // upwindElev 的取样点
            Atmosphere.sstAnom(x + 15_000, z);            // SLOPE 取样点

            double[] wO = Atmosphere.windAt(x, z, sd, cell, th, GS);
            double paO = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmO = PrecipField.mmPerDay(x, z, sd, cell, th, GS);
            double stO = Atmosphere.surfaceTemp(x, z, sd, cell, th);

            boolean mc = Atmosphere.beginMemo();
            double[] wN = Atmosphere.windAt(x, z, sd, cell, th, GS);
            double paN = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double pmN = PrecipField.mmPerDay(x, z, sd, cell, th, GS);
            double stN = Atmosphere.surfaceTemp(x, z, sd, cell, th);
            Atmosphere.endMemo(mc);

            double[] a = {wO[0], wO[1], paO, pmO, stO};
            double[] b = {wN[0], wN[1], paN, pmN, stN};
            for (int t = 0; t < NB; t++) {
                if (bits(a[t]) != bits(b[t])) {
                    cnt[t]++;
                    if (firstI[t] < 0) firstI[t] = i;
                    lastI[t] = i;
                    md[t] = Math.max(md[t], Math.abs(b[t] - a[t]));
                }
            }
        }
        say("");
        say("  量                不符 / " + N + "   首次 i   末次 i    最大 |d|");
        for (int t = 0; t < NB; t++) {
            say(String.format(LF, "  %-16s %6d / %d  %7d  %7d   %.3e", nm[t], cnt[t], N, firstI[t], lastI[t], md[t]));
        }
        say("");
        int tot = 0; for (int t = 0; t < NB; t++) tot += cnt[t];
        say("  合计不符 = " + tot + " / " + (N * NB));
        say("  判据：全零 => 探针缺陷确证、memo 无罪；非零 => 真差异，memo 不能上");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
