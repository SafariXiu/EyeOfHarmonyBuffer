package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.BasinFinder;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * P474：**D57 的验证** —— BasinFinder 在陆地查询上白算 161x161 陆地掩膜。
 *
 * <p>D57：{@code BasinFinder.find} 原来先把 25,921 格的陆地掩膜**全部算出来**，
 * 才检查中心格是不是陆地。而中心格恰好就是 `(x,z)` 本身 ⇒ 陆地查询白算 25,921 次 isLand。
 * 修法是把那个判断**提前**（等价性：掩膜构造无副作用、判断条件逐字相同）。
 *
 * <p>四段：A 单元代价（陆/海查询各多少次）；B 等价性逐点断言；C 早退自洽计数；
 * <p>D 按 P284 的形状重测「大范围 x」的代价（与 P472 A 段的前值对照）。
 */
public class P474 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P474] " + s); rep.flush(); System.out.println("[P474] " + s); System.out.flush(); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p474_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        say("P474：D57 验证（BasinFinder 陆地早退）");
        say(String.format(LF, "  SEED=%d  MAX_R=%d SAMPLE=%d ⇒ 掩膜 %d x %d = %d 格",
            SEED, BasinFinder.MAX_R, BasinFinder.SAMPLE,
            2 * BasinFinder.MAX_R + 1, 2 * BasinFinder.MAX_R + 1, (2 * BasinFinder.MAX_R + 1) * (2 * BasinFinder.MAX_R + 1)));
        say("");

        // ---- A. 收集陆地 / 海洋样本点 ----
        int z30 = (int) (30.0 / 90.0 * (ZC / 2));
        int[] landX = new int[400], seaX = new int[400];
        int nl = 0, ns = 0;
        for (int x = -8_000_000; x <= 8_000_000 && (nl < 400 || ns < 400); x += 40_000) {
            boolean isL = PlateField.isLandWithCell(x, z30, sd, cell);
            if (isL && nl < 400) landX[nl++] = x;
            if (!isL && ns < 400) seaX[ns++] = x;
        }
        say(String.format(LF, "A. 样本：陆地 %d 个、海洋 %d 个（z=lat30 那条线）", nl, ns));
        double usLand = timeFind(landX, nl, z30, sd, cell, 3);
        double usSea = timeFind(seaX, ns, z30, sd, cell, 3);
        double usIsLand = timeIsLand(landX, nl, z30, sd, cell, 20);
        say(String.format(LF, "   BasinFinder.find（陆地点，已早退）：%.1f us/次", usLand));
        say(String.format(LF, "   BasinFinder.find（海洋点，真解域）  ：%.1f us/次", usSea));
        say(String.format(LF, "   PlateField.isLandWithCell          ：%.2f us/次", usIsLand));
        say(String.format(LF, "   ⇒ 修前每个陆地查询要白算 %d 次 isLand = **%.1f ms**；修后 %.1f us（省 %.0f 倍）",
            25921, 25921 * usIsLand / 1000.0, usLand, (25921 * usIsLand / 1000.0) / Math.max(1e-9, usLand / 1000.0)));
        say("");

        // ---- B. 等价性逐点断言 ----
        say("B. 等价性：isLandWithCell(x,z) == true ⇒ find(x,z).valid 必须为 false（不许有例外）");
        int bad = 0, nTest = 0;
        for (int x = -9_000_000; x <= 9_000_000; x += 137_000) {
            for (int z = 100_000; z <= 6_000_000; z += 730_000) {
                boolean isL = PlateField.isLandWithCell(x, z, sd, cell);
                boolean ok = BasinFinder.find(x, z, sd, cell).valid;
                nTest++;
                if (isL && ok) bad++;
            }
        }
        say(String.format(LF, "   %d 个点：陆地却报 valid 的 %d 个 ⇒ %s", nTest, bad, bad == 0 ? "通过" : "**失败**"));
        say("");

        // ---- C. 早退自洽计数 ----
        say("C. 保留的自洽断言（掩膜算完后再查一次中心格）：");
        say(String.format(LF, "   earlyOutMismatch = %d  ⇒ %s", BasinFinder.earlyOutMismatch,
            BasinFinder.earlyOutMismatch == 0 ? "早退与掩膜判断**从未不一致**（等价性成立）" : "**不一致！等价性论证有洞**"));
        say("");

        // ---- D. P284 形状的代价重测 ----
        say("D. 按 P284 的形状重测大范围 x 的代价（与 P472 A 段前值对照）");
        long c0 = OceanField.solveCount, s0 = OceanField.solveNanos;
        long t0 = System.nanoTime();
        int nBasin = 0, nRow = 0;
        for (int z = 1_600_000; z <= 5_000_000; z += 200_000) {
            nRow++;
            LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
            for (int x = 0; x <= 40_000_000; x += 500_000) {
                int[] sp = OceanField.spanOf(x, z, SEED);
                if (sp == null) continue;
                long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
                if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
            }
            nBasin += m.size();
        }
        double dt = (System.nanoTime() - t0) / 1e9;
        say(String.format(LF, "   z 行 %d 条；新解海盆 %d 个；墙钟 **%.1f s**（前值 120.7 s，海盆数 279）",
            nRow, nBasin, dt));
        say(String.format(LF, "   纯解行 %.1f s；solveRow 调用 %d 次",
            (OceanField.solveNanos - s0) / 1e9, OceanField.solveCount - c0));
        say("");
        say(String.format(LF, "   ⇒ 最终 earlyOutMismatch = %d，reentryBlocked = %d（都必须 0）",
            BasinFinder.earlyOutMismatch, OceanField.reentryBlocked));
        say("⚠ 记账：本探针只测量，未改任何物理参数。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static double timeFind(int[] xs, int n, int z, long sd, int cell, int reps) {
        if (n == 0) return Double.NaN;
        long t = System.nanoTime(); int cnt = 0;
        for (int r = 0; r < reps; r++) for (int i = 0; i < n; i++) { BasinFinder.find(xs[i], z + (i % 7) * 1000, sd, cell); cnt++; }
        return (System.nanoTime() - t) / 1e3 / cnt;
    }

    static double timeIsLand(int[] xs, int n, int z, long sd, int cell, int reps) {
        if (n == 0) return Double.NaN;
        long t = System.nanoTime(); int cnt = 0;
        for (int r = 0; r < reps; r++) for (int i = 0; i < n; i++) { PlateField.isLandWithCell(xs[i], z, sd, cell); cnt++; }
        return (System.nanoTime() - t) / 1e3 / cnt;
    }
}
