package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P471：**kappa/elev 记忆化的等价性与加速比**（§162.16 的代价修复自检）。
 *
 * <p>记忆化只有在**逐位等价**的前提下才允许落地。本探针不靠推理：
 * 对一大批点分别用 memo 关 / memo 开 算同一个量，用 {@code Double.doubleToLongBits} 逐位比。
 *
 * <p>采样点**故意包含 curl 真正用到的三种偏移**：{@code x±GRAD}、{@code z±GRAD}、{@code ±2*GRAD}。
 * 只在「好看的网格」上等价比不上在真实调用图案上等价。
 */
public class P471 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int GRAD = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P471] " + s); rep.flush(); System.out.println("[P471] " + s); System.out.flush(); }

    static boolean sameBits(double a, double b) { return Double.doubleToLongBits(a) == Double.doubleToLongBits(b); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p471_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        say("P471：kappa/elev 记忆化的等价性与加速比");
        say(String.format(LF, "  memoOn()=%s（应为 false：只有解行线程才开）", Atmosphere.memoOn()));
        say("");

        // ---------- 采样点：真实调用图案 ----------
        int n = 0;
        int[] xs = new int[4000], zs = new int[4000];
        for (int latDeg = -80; latDeg <= 80 && n < 4000; latDeg += 7) {
            int z0 = WorldContract.zOfLat(latDeg);
            for (int i = 0; i < 400 && n < 4000; i++) {
                int x0 = -9_000_000 + i * 45_000;
                int[][] off = {{0, 0}, {GRAD, 0}, {-GRAD, 0}, {2 * GRAD, 0}, {-2 * GRAD, 0},
                               {0, GRAD}, {0, -GRAD}, {GRAD, GRAD}, {-GRAD, -GRAD}, {GRAD, -GRAD}};
                int[] o = off[i % off.length];
                xs[n] = x0 + o[0]; zs[n] = z0 + o[1]; n++;
            }
        }
        say(String.format(LF, "  采样点 %d 个（含 x±GRAD、z±GRAD、±2*GRAD 等真实偏移）", n));
        say("");

        // ---------- A. 逐位等价 ----------
        say("A. 逐位等价断言（memo 关 vs memo 开，Double.doubleToLongBits）");
        double[][] ref = new double[4][n];
        for (int i = 0; i < n; i++) {
            ref[0][i] = Atmosphere.pressureAnomaly(xs[i], zs[i], sd, cell, 0.0);
            ref[1][i] = Atmosphere.surfaceTemp(xs[i], zs[i], sd, cell, 0.0);
            ref[2][i] = Atmosphere.windAt(xs[i], zs[i], sd, cell, 0.0, GRAD)[1];
            ref[3][i] = Atmosphere.windStress(xs[i], zs[i], sd, cell, 0.0, GRAD)[0];
        }
        long h0 = Atmosphere.memoHits, m0 = Atmosphere.memoMisses;
        int[] bad = new int[4];
        boolean created = Atmosphere.beginMemo();
        for (int i = 0; i < n; i++) {
            if (!sameBits(ref[0][i], Atmosphere.pressureAnomaly(xs[i], zs[i], sd, cell, 0.0))) bad[0]++;
            if (!sameBits(ref[1][i], Atmosphere.surfaceTemp(xs[i], zs[i], sd, cell, 0.0))) bad[1]++;
            if (!sameBits(ref[2][i], Atmosphere.windAt(xs[i], zs[i], sd, cell, 0.0, GRAD)[1])) bad[2]++;
            if (!sameBits(ref[3][i], Atmosphere.windStress(xs[i], zs[i], sd, cell, 0.0, GRAD)[0])) bad[3]++;
        }
        long hits = Atmosphere.memoHits - h0, misses = Atmosphere.memoMisses - m0;
        Atmosphere.endMemo(created);
        say(String.format(LF, "   pressureAnomaly 逐位不等 %d / %d", bad[0], n));
        say(String.format(LF, "   surfaceTemp     逐位不等 %d / %d", bad[1], n));
        say(String.format(LF, "   windAt(v)       逐位不等 %d / %d", bad[2], n));
        say(String.format(LF, "   windStress(x)   逐位不等 %d / %d", bad[3], n));
        say(String.format(LF, "   ⇒ 记忆表：命中 %d，未命中 %d（命中率 %.1f%%）",
            hits, misses, 100.0 * hits / Math.max(1, hits + misses)));
        say(String.format(LF, "   ⇒ 关掉之后 memoOn()=%s（必须 false）", Atmosphere.memoOn()));
        say("");

        // ---------- B. 4 相位 curl 的加速比 ----------
        say("B. 4 相位 curl 图案的加速比（每点 4 相位 x 4 次 windStress，与 curlAtmos 同构）");
        double[] ph = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
        int NP = 300;
        // 预热 JIT
        for (int i = 0; i < 200; i++) { curl(xs[i], zs[i], sd, cell, ph, GRAD); }
        long t0 = System.nanoTime();
        double s0 = 0;
        for (int i = 0; i < NP; i++) s0 += curl(xs[i], zs[i], sd, cell, ph, GRAD);
        double dt0 = (System.nanoTime() - t0) / 1e9;
        h0 = Atmosphere.memoHits; m0 = Atmosphere.memoMisses;
        created = Atmosphere.beginMemo();
        t0 = System.nanoTime();
        double s1 = 0;
        for (int i = 0; i < NP; i++) s1 += curl(xs[i], zs[i], sd, cell, ph, GRAD);
        double dt1 = (System.nanoTime() - t0) / 1e9;
        hits = Atmosphere.memoHits - h0; misses = Atmosphere.memoMisses - m0;
        Atmosphere.endMemo(created);
        say(String.format(LF, "   memo 关：%.3f s（%.3f ms/点）  和=%.10e", dt0, dt0 * 1000 / NP, s0));
        say(String.format(LF, "   memo 开：%.3f s（%.3f ms/点）  和=%.10e", dt1, dt1 * 1000 / NP, s1));
        say(String.format(LF, "   ⇒ 加速比 **%.2fx**；命中 %d / 未命中 %d（命中率 %.1f%%）",
            dt0 / Math.max(1e-9, dt1), hits, misses, 100.0 * hits / Math.max(1, hits + misses)));
        say(String.format(LF, "   ⇒ 两次的和逐位相同：%s（%.10e vs %.10e）", sameBits(s0, s1) ? "是" : "**否**", s0, s1));
        say("");
        say(String.format(LF, "   ⇒ 按此加速比，P470 实测的预热 1489.8 s 外推为 **%.0f s = %.1f 分钟**",
            1489.8 * dt1 / Math.max(1e-9, dt0), 1489.8 * dt1 / Math.max(1e-9, dt0) / 60.0));
        say("");
        say("⚠ 记账：本探针只测量；memo 的开关由探针自己成对调用，退出前必已关闭。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    /** 与 OceanField.curlAtmos 同构的 curl（4 相位平均）。 */
    static double curl(int x, int z, long seed, int cell, double[] ph, int g) {
        double c = 0;
        for (double th : ph) {
            double[] e = Atmosphere.windStress(x + g, z, seed, cell, th, g);
            double[] w = Atmosphere.windStress(x - g, z, seed, cell, th, g);
            double[] nn = Atmosphere.windStress(x, z + g, seed, cell, th, g);
            double[] ss = Atmosphere.windStress(x, z - g, seed, cell, th, g);
            c += ((e[1] - w[1]) - (nn[0] - ss[0])) / (2.0 * g) / ph.length;
        }
        return c;
    }
}
