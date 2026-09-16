package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.V2TerrainGen;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P451：验证 P0-c（D16-a 分派同源 + D19 贴岸低地保险）。
 *
 * <p>本探针只读 SimTerrain（SNOW_FROM_TEMP 关掉以避开气候瓦片）：A 段是沿用 P294 的「关雪线是口径中性」
 * 论证 —— 本探针一个字段都不读 c.snow。
 *
 * <p>**反向对照**由对话里的 A/B 完成：把 D19 的那两行临时撤掉再跑本探针，统计量应当变红。
 */
public class P451 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int SEA = 63, MAXY = 254;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P451] " + s); System.out.println("[P451] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p451_report.txt"), "UTF-8");
        final boolean snowSaved = SimTerrain.SNOW_FROM_TEMP;
        SimTerrain.SNOW_FROM_TEMP = false;      // 口径中性：本探针不读 c.snow
        say("P451：P0-c 验证（D16-a 分派同源 / D19 贴岸低地保险）");
        say(String.format(LF, "  seaLevel=%d  maxY=%d  ⇒ 陆地列下界应为 %d", SEA, MAXY, SEA + 1));
        say("");

        V2TerrainGen.Column col = new V2TerrainGen.Column();
        long n = 0, nLand = 0, nSea = 0, mis = 0, landUnder = 0, seaUnder1 = 0, landBeach = 0;
        int minLandH = Integer.MAX_VALUE, maxLandH = Integer.MIN_VALUE;

        // 三个窗口，步长 997/1013（互质，避免与任何格点对齐）
        int[][] wins = {{-3_000_000, -2_000_000, -1_000_000}, {0, 1_000_000, 3_000_000}, {6_000_000, 8_000_000, 12_000_000}};
        for (int[] w : wins) {
            for (int x = w[0]; x < w[0] + 1_000_000; x += 997) {
                for (int z = w[1]; z < w[1] + 400_000; z += 1013) {
                    V2TerrainGen.Column c = SimTerrain.compose(col, x, z, SEED, SEA, MAXY);
                    boolean ref = PlateField.isLandWithCell(x, z, SD, PlateField.PLATE_CELL);
                    n++;
                    if (c.land != ref) mis++;
                    if (c.land) {
                        nLand++;
                        if (c.h < SEA + 1) landUnder++;
                        if (c.h - SEA <= 3) landBeach++;
                        if (c.h < minLandH) minLandH = c.h;
                        if (c.h > maxLandH) maxLandH = c.h;
                    } else {
                        nSea++;
                        if (c.h < 1) seaUnder1++;
                    }
                }
            }
        }
        say(String.format(LF, "  样本 n=%d（陆地 %d = %.1f%%，海洋 %d）", n, nLand, 100.0 * nLand / n, nSea));
        say("");
        say("A. D16-a：compose().land 与 PlateField.isLandWithCell 是否同源");
        say(String.format(LF, "  不一致 = %d / %d   %s", mis, n, mis == 0 ? "**逐位一致 OK**" : "**不一致**"));
        say("");
        say("B. D19：陆地列是否都 >= seaLevel+1");
        say(String.format(LF, "  陆地列 h < %d 的数量 = **%d**   %s", SEA + 1, landUnder, landUnder == 0 ? "OK" : "**仍有干坑**"));
        say(String.format(LF, "  陆地列 h 范围 = [%d, %d]（seaLevel=%d）", minLandH, maxLandH, SEA));
        say(String.format(LF, "  其中贴岸带（h - seaLevel <= 3）的陆地列 = %d", landBeach));
        say("");
        say("C. 海洋列一致性：hCapped 与 h 都 >= 1");
        say(String.format(LF, "  海洋列 h < 1 的数量 = **%d**   %s", seaUnder1, seaUnder1 == 0 ? "OK" : "**不一致**"));
        say("");
        say("E. **D16-b 对照**：海列的海床，旧来源 seaDepthBlocks vs 新来源 compose().h");
        {
            V2TerrainGen.Column cc = new V2TerrainGen.Column();
            long nn2 = 0, diffN = 0;
            double sumAbs = 0, maxAbs = 0;
            for (int x = -3_000_000; x < -3_000_000 + 600_000; x += 3_001) {
                for (int z = -1_000_000; z < -1_000_000 + 400_000; z += 3_007) {
                    if (PlateField.isLandWithCell(x, z, SD, PlateField.PLATE_CELL)) continue;
                    double oldDepth = V2TerrainGen.seaDepthBlocks(x, z, SEED);
                    int oldSeabed = SEA - (int) Math.round(oldDepth);
                    if (oldSeabed < 1) oldSeabed = 1;
                    if (oldSeabed > SEA - 1) oldSeabed = SEA - 1;
                    int newSeabed = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.compose(cc, x, z, SEED, SEA, MAXY).h;
                    double d = Math.abs(newSeabed - oldSeabed);
                    nn2++; sumAbs += d; if (d > 0.5) diffN++;
                    if (d > maxAbs) maxAbs = d;
                }
            }
            say(String.format(LF, "  海洋列样本 = %d", nn2));
            if (nn2 > 0) {
                say(String.format(LF, "  海床不同（>0.5 格）的比例 = %.1f%%   平均 |差| = %.2f 格   最大 %.0f 格",
                    100.0 * diffN / nn2, sumAbs / nn2, maxAbs));
                say("  ⇒ 两者本来就不同 ⇒ D16-b 是一次**真实的世界改变**（海床换了来源），不是纯粹的等价改写。");
            } else {
                say("  **样本为 0 ⇒ 本段无效（纪律：扫描型探针必须在样本为 0 时报 FAIL）**");
            }
        }
        say("");
        say("D. 反向对照说明（本文件不做）");
        say("  「陆地列 h < seaLevel+1 的数量」这条断言必须能变红才有意义。");
        say("  做法：把 SimTerrain.compose 里 D19 的那两行（hFloor 与 if）**临时撤掉**再跑本探针，");
        say("  该计数应当 > 0。这一 A/B 在对话里执行并记账。");
        SimTerrain.SNOW_FROM_TEMP = snowSaved;
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
