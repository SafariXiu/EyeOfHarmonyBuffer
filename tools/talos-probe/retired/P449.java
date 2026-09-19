package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P449：**定位 P295 报的那些断崖，并判断它们落在真 Voronoi 棱还是「次近站点换人」线**。
 *
 * <h3>为什么必须有这一跑（我上一轮的错误）</h3>
 * P448 只扫了 4 个 200 km 窗口（160 万点）就宣布「断崖不存在」—— 而 P295 扫 8 条 x 400 万点
 * （3200 万点）测到 **151 blocks（= 6426 m）**的 1-block 跳变。**我扫的窗口恰好避开了它。**
 * 这一跑用**与 P295 相同的 8 条线**，把最大的若干跳变连同它们两侧的 edgeDistance 一起打出来：
 *   edge ≈ 0  ⇒ 真 Voronoi 棱（PlateField:29 承诺连续的地方）
 *   edge > 0  ⇒ 「次近站点换人」线（子代理主张的机理）
 */
public class P449 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int TOP = 12;

    static void say(String s) { rep.println("[P449] " + s); System.out.println("[P449] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p449_report.txt"), "UTF-8");
        say("P449：断崖定位 + edge 分类（与 P295 相同的 8 条线，1-block 步长）");
        say(String.format(LF, "  SEED=%d  LAND_GAIN=%.4f  ⇒ 1 block 跳变 ≙ %.1f m", SEED, SimTerrain.LAND_GAIN, 1.0 / SimTerrain.LAND_GAIN));
        say("");

        double[] bestJ = new double[TOP];
        int[] bestX = new int[TOP], bestZ = new int[TOP];
        double[] bestEL = new double[TOP], bestER = new double[TOP];
        double[] bestEdgeL = new double[TOP], bestEdgeR = new double[TOP];
        long nBig50 = 0, nBig100 = 0, nBig187 = 0, total = 0;
        long bigAtEdge = 0, bigOffEdge = 0;

        for (int li = 0; li < 8; li++) {
            int z = li * 1_000_000;
            double prevE = PlateField.elevationWithCell(-2_000_000, z, SD, CELL);
            double prevEdge = PlateField.edgeDistance(-2_000_000, z, SD);
            for (int x = -2_000_000 + 1; x < 2_000_000; x++) {
                double e = PlateField.elevationWithCell(x, z, SD, CELL);
                double ed = PlateField.edgeDistance(x, z, SD);
                double j = Math.abs(e - prevE);
                total++;
                if (j > 50.0) nBig50++;
                if (j > 100.0) nBig100++;
                if (j > 187.0 / SimTerrain.LAND_GAIN) nBig187++;
                if (j > 500.0) { if (Math.min(prevEdge, ed) < 1.0) bigAtEdge++; else bigOffEdge++; }
                for (int t = 0; t < TOP; t++) {
                    if (j > bestJ[t]) {
                        for (int s = TOP - 1; s > t; s--) {
                            bestJ[s] = bestJ[s-1]; bestX[s] = bestX[s-1]; bestZ[s] = bestZ[s-1];
                            bestEL[s] = bestEL[s-1]; bestER[s] = bestER[s-1];
                            bestEdgeL[s] = bestEdgeL[s-1]; bestEdgeR[s] = bestEdgeR[s-1];
                        }
                        bestJ[t] = j; bestX[t] = x; bestZ[t] = z;
                        bestEL[t] = prevE; bestER[t] = e;
                        bestEdgeL[t] = prevEdge; bestEdgeR[t] = ed;
                        break;
                    }
                }
                prevE = e; prevEdge = ed;
            }
        }
        say(String.format(LF, "  扫描 %d 对（8 条线 x 400 万点 @1-block）", total));
        say(String.format(LF, "  |Δelev| > 50 m : %d (%.5f%%)   > 100 m : %d (%.5f%%)   > 187 blocks(=%.0f m) : %d (%.6f%%)",
            nBig50, 100.0*nBig50/total, nBig100, 100.0*nBig100/total, 187.0/SimTerrain.LAND_GAIN, nBig187, 100.0*nBig187/total));
        say("");
        say("A. 最大的 12 个跳变（含两侧 edge）");
        say(String.format(LF, "  %-4s %12s %12s %12s %12s %14s %14s %-8s", "#", "x", "z", "elev(左)", "elev(右)", "edge(左)", "edge(右)", "分类"));
        for (int t = 0; t < TOP; t++) {
            if (bestJ[t] <= 0) continue;
            double em = Math.min(bestEdgeL[t], bestEdgeR[t]);
            String cls = em < 1.0 ? "真棱" : (em < 120_000.0 ? "t<1 区内" : "t=1 区内");
            say(String.format(LF, "  %-4d %12d %12d %12.2f %12.2f %14.1f %14.1f %-8s",
                t + 1, bestX[t], bestZ[t], bestEL[t], bestER[t], bestEdgeL[t], bestEdgeR[t], cls));
        }
        say("");
        say("B. 裁决");
        say(String.format(LF, "  |Δelev| > 500 m 的跳变里：落在真棱(edge<1 m)上的 %d 个，**不在真棱上的 %d 个**", bigAtEdge, bigOffEdge));
        if (bigOffEdge > 0) {
            say("  ==> **子代理的机理（次近站点换人）成立**：大断崖**不在** Voronoi 棱上。");
            say("      ⇒ PlateField.java:29 的「边界处取均值保证连续」只覆盖真棱，不覆盖这些线。");
        } else {
            say("  ==> 大断崖全在真棱附近 ⇒ 子代理机理不成立。");
        }
        say("");
        say("C. 与既有记账的对照");
        say("  PlateField.java:233-234 记的是「base 项…最高 ~1790 m 的跳变…最大 7970 m 就来自它」；");
        say("  本跑实测最大跳变的量级见上表 ⇒ 归因是 base 还是 feat，要看跳变处的 elev 分解（下一步）。");
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
