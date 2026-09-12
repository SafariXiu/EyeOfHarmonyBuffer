package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import java.io.File;
import java.io.PrintStream;

/**
 * P240：**极地地形 = 纯噪声**（最终定案后唯一的极地地形守卫）。
 *
 * <h3>它守什么</h3>
 * 2026-09 最终定案：极地系统**只做三件事** —— 虚拟墙（求解器掩码）、极地冷带（温度）、海冰（方块）。
 * **对地形的影响 = 0。**
 *
 * 这条守卫是几次反复之后留下的：极地先后试过"强制成海 + 强制成陆"（凿出护城河、造出假大陆）、
 * 又试过"挖一条水道"（窄了看不见、宽了就是护城河）。P240 的实测是那段历史的全部凭证：
 * <pre>
 *   bandD 0.88 自然 100.0% 陆 → 强制后 100.0%   （横贯 500km 的完整大陆）
 *   bandD 0.92 自然  98.5% 陆 → 强制后  27.8%   （从大陆上凿掉七成）
 *   bandD 0.99 自然  19.3% 陆 → 强制后 100.0%   （凭空造出横贯 500km 的陆地）
 * </pre>
 *
 * <h3>判据</h3>
 * <ol>
 *   <li><b>极地带的陆占比必须落在自然区间内</b>（15%~70%）。被强制过会贴到 ~0% 或 ~100%；</li>
 *   <li><b>极地带与全球的陆占比之差 &lt; 15pp</b>。极地不该在统计上区别于世界其他地方；</li>
 *   <li>逐行打印真实结构（记录用），让"极地海在哪"有据可查，而不是靠看图猜。</li>
 * </ol>
 * 源码级守卫在 P220 的 U13（地形链不得引用 PolarZone）。
 *
 * 用法：runprobe4.bat P240   输出：p240_report.txt；退出码 0/1
 */
public class P240 {

    static final int SEED = 1022228679;
    static final int CELL_X = 1250;
    static final int SPAN = 1_000_000;                 // x ∈ ±500km
    static final int NX = SPAN / CELL_X;
    /** 极地带陆占比的允许区间（自然实测 32.3%，外侧 29.3%）。 */
    static final double LAND_LO = 15.0, LAND_HI = 70.0;
    /** 极地与全球的陆占比最大允许差（pp）。 */
    static final double MAX_GAP_PP = 15.0;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p240_report.txt"), "UTF-8");
        boolean ok = true;

        say("===== P240：极地地形 = 纯噪声（最终定案后守卫）=====");
        say(String.format("  采样 x ∈ ±%dkm（%d 列 @%dm）；极地带 = bandD > %.2f",
            SPAN / 2000, NX, CELL_X, PolarZone.FLOE_BAND));
        say("  极地系统只做三件事：虚拟墙 / 极地冷带 / 海冰 —— 对地形的影响 = 0");
        say("");
        say("  bandD |   z    | 陆% | 最长连续洋面km | 最长连续陆地km");

        int polarLand = 0, polarN = 0, allLand = 0, allN = 0;
        for (double b = 0.78; b <= 1.0001; b += 0.01) {
            int z = (int) Math.round(b * 500_000);
            int land = 0, runSea = 0, bestSea = 0, runLand = 0, bestLand = 0;
            for (int c = 0; c < NX; c++) {
                int x = -SPAN / 2 + c * CELL_X;
                if (NoiseContinentGrid.isLand(x, z, SEED)) {
                    land++;
                    runLand++;
                    if (runLand > bestLand) bestLand = runLand;
                    runSea = 0;
                } else {
                    runSea++;
                    if (runSea > bestSea) bestSea = runSea;
                    runLand = 0;
                }
            }
            allLand += land;
            allN += NX;
            if (b > PolarZone.FLOE_BAND) {
                polarLand += land;
                polarN += NX;
            }
            say(String.format("  %.2f  | %6d | %3.0f%% | %15.0f | %14.0f",
                b, z, 100.0 * land / NX, bestSea * CELL_X / 1000.0, bestLand * CELL_X / 1000.0));
        }

        double polarPct = 100.0 * polarLand / polarN;
        double allPct = 100.0 * allLand / allN;
        say("");
        say(String.format("  [1] 极地带(bandD>%.2f) 陆占比 = %.1f%%（判据 %.0f%%~%.0f%%）",
            PolarZone.FLOE_BAND, polarPct, LAND_LO, LAND_HI));
        boolean ok1 = polarPct >= LAND_LO && polarPct <= LAND_HI;
        say("      判据: 既不是全陆也不是全海 —— 被强制过的话会贴到 ~0% 或 ~100% -> " + (ok1 ? "PASS" : "FAIL"));
        ok &= ok1;

        double gap = Math.abs(polarPct - allPct);
        say(String.format("  [2] 全球(0.78~1.00) 陆占比 = %.1f%%；极地与全球之差 = %.1fpp（判据 < %.0fpp）",
            allPct, gap, MAX_GAP_PP));
        boolean ok2 = gap < MAX_GAP_PP;
        say("      判据: 极地不该在统计上区别于世界其他地方（强制会把它拉成纬度函数） -> "
            + (ok2 ? "PASS" : "FAIL"));
        ok &= ok2;

        say("");
        say("POLAR_TERRAIN_STATUS=" + (ok ? "PASS" : "FAIL"));
        rep.flush();
        rep.close();
        System.exit(ok ? 0 : 1);
    }

    static void say(String s) {
        System.out.println("[P240] " + s);
        rep.println("[P240] " + s);
    }
}
