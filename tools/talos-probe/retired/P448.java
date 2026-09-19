package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P448：**复核** PlateField.elevationFull 的断崖到底出在哪条线上（子代理线索，未对账 -> 本探针裁决）。
 *
 * <h3>为什么这样测（不重写站位搜索）</h3>
 * 子代理说跳变发生在「最近站点不变、**次近站点**换人」的那条线上，而不是真 Voronoi 棱上。
 * 我不能把 elevationFull 的 3x3 站点搜索抄一遍（P284 的教训：手抄物理 = 口径漂移）。
 * 但 {@link PlateField#edgeDistance} 是公开的，它返回的正是**同一次** 3x3 搜索里的 0.5*(d2-d1)：
 *   <ul>
 *     <li>真 Voronoi 棱上 d1 == d2 ⇒ edge == 0；</li>
 *     <li>「次近站点换人」线上 d1 不变、d2 跳 ⇒ **edge 跳而 edge > 0**。</li>
 *   </ul>
 * 于是：大跳变处如果 edge 明显 > 0 ⇒ 子代理的机理成立；如果大跳变**只**出现在 edge ≈ 0 处 ⇒ 证伪。
 *
 * <h3>对照</h3>
 * 同时统计 edge < 1 m 的真棱上的 |Δelev|（应远小于跳变）。
 */
public class P448 {

    static final int SEED = 1022228679;
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P448] " + s); System.out.println("[P448] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p448_report.txt"), "UTF-8");
        say("P448：elevationFull 断崖的定位（复核子代理线索）");
        say(String.format(LF, "  SEED=%d  PLATE_CELL=%d km  OROGEN_W=%.0f km  MARGIN_W=%.0f km",
            SEED, CELL / 1000, 30.0, 120.0));
        say("");

        // 扫描窗口：包含子代理报的那一处 (9,509,218 @ z=5,000,000)，外加几个对照窗
        int[][] wins = {
            {9_400_000, 9_600_000, 5_000_000},
            {1_000_000, 1_200_000, 3_000_000},
            {-4_000_000, -3_800_000, -6_000_000},
            {0, 200_000, 0},
        };
        int STEP = 2;                       // 2 m 步长
        int nBig = 0, nBigEdgePos = 0, nBigEdgeZero = 0, nT1 = 0;
        double maxJumpT1 = 0, maxJumpT1X = 0;
        double maxJump = 0, maxJumpX = 0, maxJumpZ = 0, maxJumpEdge = 0;
        double maxTrueEdgeJump = 0;
        int nTrueEdge = 0;
        say(String.format(LF, "  %-26s %10s %12s %12s %12s", "窗口 (x0..x1 @ z)", "最大跳变", "最大跳变处 x", "该处 edge", "真棱最大跳变"));
        for (int[] w : wins) {
            double prevE = PlateField.elevationWithCell(w[0], w[2], SD, CELL);
            double prevEdge = PlateField.edgeDistance(w[0], w[2], SD);
            double wMax = 0, wMaxX = 0, wMaxEdge = 0, wTrue = 0;
            for (int x = w[0] + STEP; x <= w[1]; x += STEP) {
                double e = PlateField.elevationWithCell(x, w[2], SD, CELL);
                double ed = PlateField.edgeDistance(x, w[2], SD);
                double j = Math.abs(e - prevE);
                if (j > wMax) { wMax = j; wMaxX = x; wMaxEdge = Math.min(prevEdge, ed); }
                // 子代理机理只可能在 t<1 的区间（离板块边界 < MARGIN_W=120 km）成立 —— 单独统计这一段
                if (Math.min(prevEdge, ed) < 120_000.0) { nT1++; if (j > maxJumpT1) { maxJumpT1 = j; maxJumpT1X = x; } }
                if (j > 500.0) {
                    nBig++;
                    if (Math.min(prevEdge, ed) > 1.0) nBigEdgePos++; else nBigEdgeZero++;
                    if (j > maxJump) { maxJump = j; maxJumpX = x; maxJumpZ = w[2]; maxJumpEdge = Math.min(prevEdge, ed); }
                }
                double eMin = Math.min(prevEdge, ed);
                if (eMin < 1.0) { nTrueEdge++; if (j > wTrue) wTrue = j; if (j > maxTrueEdgeJump) maxTrueEdgeJump = j; }
                prevE = e; prevEdge = ed;
            }
            say(String.format(LF, "  %-26s %10.1f %12.0f %12.3f %12.3f",
                String.format(LF, "%d..%d @ z=%d", w[0], w[1], w[2]), wMax, wMaxX, wMaxEdge, wTrue));
        }
        say("");
        say("A. 汇总");
        say(String.format(LF, "  全程 |Δelev| > 500 m 的跳变数 = %d", nBig));
        say(String.format(LF, "    其中「不是真棱」（edge 两侧都 > 1 m）= %d    「是真棱」（edge < 1 m）= %d", nBigEdgePos, nBigEdgeZero));
        say(String.format(LF, "  最大跳变 = %.1f m，位于 x=%.0f, z=%.0f，该处 edge = %.3f m", maxJump, maxJumpX, maxJumpZ, maxJumpEdge));
        say(String.format(LF, "  真棱样本数 (edge < 1 m) = %.0f，其上最大 |Δelev| = %.3f m", (double) nTrueEdge, maxTrueEdgeJump));
        say(String.format(LF, "  **t<1 区间（edge < MARGIN_W = 120 km）样本数 = %d，其上最大 |Δelev| = %.3f m（在 x=%.0f）**",
            nT1, maxJumpT1, maxJumpT1X));
        say("  （这一行才是对子代理机理的**直接检验**：t>0 时 feat 的权重才带上 f2，f2 换人才可能造出跳变）");
        say("");
        say("A2. **定点复核**：子代理报的坐标 (x=9,509,218, z=5,000,000) 附近，1 m 步长");
        say(String.format(LF, "  %12s %16s %16s %16s %16s", "x", "elevationWithCell", "elevationFull", "elevationWithCellRaw", "edgeDistance"));
        int tx = 9_509_218, tz = 5_000_000;
        double prevC = PlateField.elevationWithCell(tx - 8, tz, SD, CELL);
        double prevF = PlateField.elevationFull(tx - 8, tz, SD, CELL, 0.10);
        double maxC = 0, maxF = 0;
        for (int x = tx - 7; x <= tx + 7; x++) {
            double ec = PlateField.elevationWithCell(x, tz, SD, CELL);
            double ef = PlateField.elevationFull(x, tz, SD, CELL, 0.10);
            double er = PlateField.elevationWithCellRaw(x, tz, SD, CELL);
            double ed = PlateField.edgeDistance(x, tz, SD);
            if (Math.abs(ec - prevC) > maxC) maxC = Math.abs(ec - prevC);
            if (Math.abs(ef - prevF) > maxF) maxF = Math.abs(ef - prevF);
            say(String.format(LF, "  %12d %16.3f %16.3f %16.3f %16.1f", x, ec, ef, er, ed));
            prevC = ec; prevF = ef;
        }
        say(String.format(LF, "  ⇒ 1 m 步长下：elevationWithCell 最大 |Δ| = %.3f m ；elevationFull 最大 |Δ| = %.3f m", maxC, maxF));
        say(String.format(LF, "  作为对照：这三个量在子代理报的坐标附近的**绝对量级** = %.0f m 量级",
            Math.abs(PlateField.elevationWithCell(tx, tz, SD, CELL))));
        say("");
        say("B. 裁决");
        if (nBigEdgePos > 0 && maxJumpEdge > 1.0) {
            say(String.format(LF, "  ==> **子代理机理成立**：最大的断崖出现在「edge > 0」的线上（即次近站点换人），"));
            say(String.format(LF, "      不是真 Voronoi 棱。真棱上的最大跳变只有 %.3f m（相差 %.0f 倍）。",
                maxTrueEdgeJump, maxJump / Math.max(1e-9, maxTrueEdgeJump)));
            say("      ⇒ PlateField.java:29 的「边界处取均值保证连续」只覆盖真棱，不覆盖这条线。");
        } else {
            say("  ==> 子代理机理**不成立**：断崖都落在真棱（edge≈0）附近。");
        }
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
    static double maxTrueJump(double v) { return v == 0 ? 0.0 : v; }
}
