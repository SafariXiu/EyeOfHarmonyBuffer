package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P487：**A（单行剖析）的直接测量版** —— 把「行求解耗时」与「该海盆宽度」并排量出来。
 *
 * <p>§212.2 从源码读出：{@code GyreRow.solve} 第 166 行沿整条海盆**每 5 公里**求一次风，
 * 所以 {@code 行求解耗时 ≈ (盆宽 / 5 km) × 一次 wind.at 的价钱}。
 * §212.3 用「185.5 ms ÷ 68.1 µs = 2,724 格 ≈ 13,620 km」把这条式子核到了 3%。
 *
 * <p>本探针做的是**直接测量**：对冷行调用 {@code OceanField.spanOf}（它同时给出
 * {@code [zIdx, westX, eastX]} 并触发一次求解），把**盆宽**与**耗时**并排打出来，
 * 再用 {@code ms / (盆宽/5km)} 反推「每格多少钱」，看它是不是一个常数。
 *
 * <p>⚠ 只调 {@code OceanField.install}，**不启预热线程**（否则后台线程会污染 solveCount 与计时）。
 * 每个 JVM 冷启动 ⇒ 第一次命中某 (行, 海盆) 的调用就是冷解。
 */
public class P487 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P487] " + s); rep.flush(); System.out.println("[P487] " + s); System.out.flush(); }

    /** 对一批 (x,z) 量冷解耗时与盆宽；返回每格的单价（µs）。 */
    static void sweep(String tag, int[] xs, int[] zs) {
        say("== " + tag + " ==");
        say(String.format(LF, "   %9s %9s %7s %10s %11s %10s %10s %9s",
            "x", "z", "zIdx", "westX", "eastX", "盆宽 km", "ms", "us/格"));
        double sumUs = 0; int n = 0, nCold = 0;
        for (int z : zs) {
            for (int x : xs) {
                long s0 = OceanField.solveCount;
                long t0 = System.nanoTime();
                int[] sp = OceanField.spanOf(x, z, SEED);
                long dt = System.nanoTime() - t0;
                boolean cold = OceanField.solveCount > s0;
                if (sp == null) {
                    say(String.format(LF, "   %9d %9d %7s %10s %11s %10s %10.3f %9s  （解不出/无效）",
                        x, z, "-", "-", "-", "-", dt / 1e6, "-"));
                    continue;
                }
                double widthKm = (sp[2] - sp[1]) / 1000.0;
                double grids = (sp[2] - sp[1]) / OceanField.ROW_H + 1.0;
                double usPerGrid = dt / 1e3 / grids;
                if (cold) { nCold++; sumUs += usPerGrid; n++; }
                say(String.format(LF, "   %9d %9d %7d %10d %11d %10.1f %10.3f %9.2f%s",
                    x, z, sp[0], sp[1], sp[2], widthKm, dt / 1e6, usPerGrid, cold ? "" : "  (缓存)")); 
            }
        }
        say(String.format(LF, "   ⇒ 冷解 %d 次；平均 **%.2f µs / 5km 格**", nCold, n > 0 ? sumUs / n : 0.0));
        say("");
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p487_report.txt"), "UTF-8");
        say("P487：直接测量「行求解耗时 vs 海盆宽度」（A 的收口）");
        say(String.format(LF, "  ROW_H = %.0f m   ROWS = %d   Z_CYCLE = %d ⇒ 行粒度 %.0f km",
            OceanField.ROW_H, OceanField.ROWS, WorldContract.Z_CYCLE,
            WorldContract.Z_CYCLE / 1000.0 / OceanField.ROWS));
        say("  （只 install，不启预热线程；每支探针一个冷 JVM）");
        say("");
        OceanField.install(SEED);
        say(String.format(LF, "  installedSeed=%d  solveCount 起点=%d", OceanField.installedSeed(), OceanField.solveCount));
        say("");

        // P284 用的 18 条纬度行
        int[] zs = new int[18];
        for (int i = 0; i < 18; i++) zs[i] = 1_600_000 + i * 200_000;
        // 病态带（§210 的 1222..1333 列 ⇒ x = 24.44M..26.66M）与便宜带（0..332 列 ⇒ 0..6.64M）
        int[] xBad = {24_440_000, 25_000_000, 25_500_000, 26_000_000, 26_600_000};
        int[] xGood = {0, 1_000_000, 2_000_000, 4_000_000, 6_000_000};
        sweep("A. 病态带（§210 的 1222..1333 列）", xBad, zs);
        sweep("B. 便宜带（§210 的 0..332 列）", xGood, zs);

        say(String.format(LF, "C. 累计：OceanField.solveCount=%d  solveNanos=%.1f s  ⇒ %.1f ms/次（全局平均）",
            OceanField.solveCount, OceanField.solveNanos / 1e9,
            OceanField.solveNanos / 1e6 / Math.max(1, OceanField.solveCount)));
        say(String.format(LF, "   reentryBlocked=%d（必须 0）", OceanField.reentryBlocked));
        say("");
        say("⚠ 记账：本探针只测量，未改任何东西。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
