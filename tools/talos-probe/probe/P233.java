package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.climate_layer.ClimateLatitudes;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.PolarZone;

import java.io.File;
import java.io.PrintStream;

/**
 * P233：**逐纬度带阻塞剖面** —— 把"走 1 要到什么地步"变成可验收的数字。
 *
 * <h3>判据的来源（P223~P232 已确认的部分）</h3>
 * · 环流圈需要一条**横跨该环流圈纬度带**的连续经向岸（Stommel 1948；地球的太平洋就是靠
 *   亚洲—日本—堪察加连续横跨 10°N–45°N，而它的"漏"（印尼贯穿流、澳洲以南）都在带外）；
 * · 盆的纬向宽度 **≥ 195 km ≈ 5.6 δ_M**（P230 扫 2 实测，唯一可信的盆宽数据）；
 * · 现状：99.8% 的海在窗口内跨接缝连通（P227/P229），
 *   **最长连续岸只有 320 km（P226）**，且加宽窗口到 2400 km 只把闭合区从 0.10% 提到 2.52%。
 *
 * <h3>本探针算什么（纯海陆掩码，不需要求解器）</h3>
 * 把 z ∈ [0, 1M)（一个完整纬度周期）切成 N 个带，在 **x ∈ [-1200, +1200] km** 的宽幅上采样海陆，
 * 然后对每个带问两个问题：
 * <ol>
 *   <li><b>有没有一条"贯通墙"</b>：存在某个 x 列，它在这个带的整个 z 范围上都是陆地
 *       ⇒ 该带里的水**无法沿 X 直接穿过**（X 无限 ⇒ 前面有陆地就必须绕到别的纬度）；</li>
 * </ol>
 *
 * <h3>2026-09 修订：极地带为什么变成"无墙"，以及虚拟墙为什么**不该**算进本表</h3>
 * 极地定案把"实心冰盖"从 bandD&gt;0.82 收到 &gt;0.96，并新增极地海盆 + ψ=0 的**虚拟墙**。
 * 于是极地两带从"有墙"变成"无墙"。这**不是**判据失效，而是设计变更：
 *   · 本表的"贯通墙"是**经向屏障**（一列在整个带上都是障碍 ⇒ 水不能沿 X 直穿）；
 *   · 虚拟墙是**纬向屏障**（一圈闭合的带 ⇒ 水不能沿 Z 穿进极地海盆），两者正交；
 *   · 把一个 20km 厚的纬向墙放进"整带都是障碍"的判据里，永远不可能成立，算进去只会得到 0。
 * 所以判定仍旧**只用陆地**；虚拟墙的隔绝性由 P235 的洪水填充负责（赤道侧走不进极地海盆）。
 * 新增的 {@code 陆+虚拟墙列} 一列只是把这件事显式记下来，不参与判定。
 *
 * 结论：极地改成"封闭极地海盆 + 环极流"之后，本表的极地两带**本来就该是无墙**——
 * 那是用户要的"极地环流"，不是退化。
 * <ol>
 *   <li><b>最宽的海盆有多宽</b>：把"贯通墙"所在的列当作盆的边界，取相邻两道墙之间的距离
 *       ⇒ 判据要求 ≥ 195 km。</li>
 * </ol>
 *
 * 用法：runprobe4.bat P233   输出：p233_report.txt。
 */
public class P233 {

    static final int SEED = 1022228679;
    static final int CELL_X = 1250, CELL_Z = 5000;
    static final int SPAN_X = 2_400_000;                 // 采样 ±1200 km
    static final int NX = SPAN_X / CELL_X;               // 1920 列
    static final int NROW = 200;                          // z ∈ [0, 1M) 每 5 km 一行
    static final int BANDS = 10;                          // 10 带 × 100 km
    static final int ROWS_PER_BAND = NROW / BANDS;        // 20 行 = 100 km
    /** 盆宽判据（P230 扫 2 实测的饱和值）。 */
    static final double MIN_BASIN_KM = 195.0;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p233_report.txt"), "UTF-8");
        say("===== P233：逐纬度带阻塞剖面 =====");
        say("  采样 x ∈ [" + (-SPAN_X / 2 / 1000) + ", " + (SPAN_X / 2 / 1000) + "] km（"
            + NX + " 列 @" + CELL_X + "m）；z ∈ [0, 1M]（" + NROW + " 行 @" + CELL_Z + "m）");
        say("  纬度周期 LAT_CYCLE=" + ClimateLatitudes.LAT_CYCLE
            + "；bandD 0=赤道、1=极点（半周期 " + (ClimateLatitudes.LAT_CYCLE / 2 / 1000) + " km = 90°）");
        say("  判据：带内需有【贯通墙】（某列在整个带上都是陆地）；盆宽需 ≥ " + MIN_BASIN_KM + " km");

        boolean[][] land = new boolean[NX][NROW];
        boolean[] wallRow = new boolean[NROW];
        long t0 = System.nanoTime();
        for (int r = 0; r < NROW; r++) {
            int z = r * CELL_Z;
            wallRow[r] = PolarZone.isWallCell(PolarZone.rawBand(z));
            for (int c = 0; c < NX; c++) {
                int x = -SPAN_X / 2 + c * CELL_X;
                land[c][r] = NoiseContinentGrid.isLand(x, z, SEED);
            }
        }
        say("  采样耗时 " + ((System.nanoTime() - t0) / 1_000_000) + " ms");

        say("");
        say("  带# |      z 范围(km) |  bandD 范围 | 陆墙列 | 陆+虚拟墙列 | 该带最宽海盆(km) | 判定");
        int withWall = 0, okBands = 0, withLandOnly = 0;
        for (int b = 0; b < BANDS; b++) {
            int r0 = b * ROWS_PER_BAND, r1 = r0 + ROWS_PER_BAND - 1;
            // 贯通墙列：该列在 [r0, r1] 全是"障碍"（陆地，或虚拟墙所在行）
            boolean[] wall = new boolean[NX];
            int nWall = 0, nLand = 0;
            for (int c = 0; c < NX; c++) {
                boolean all = true, allLand = true;
                for (int r = r0; r <= r1; r++) {
                    if (wallRow[r]) { allLand = false; continue; }   // 虚拟墙行：不是陆，但水过不去
                    if (!land[c][r]) { all = false; allLand = false; break; }
                }
                wall[c] = all;
                if (all) nWall++;
                if (allLand) nLand++;
            }
            if (nLand > 0) withLandOnly++;
            // 相邻两道"墙列连续段"之间的最大海面距离（列）
            int maxGap = 0;
            int lastWallEnd = -1;
            for (int c = 0; c < NX; c++) {
                if (!wall[c]) continue;
                int start = c;
                while (c + 1 < NX && wall[c + 1]) c++;
                if (lastWallEnd >= 0) {
                    int gap = start - lastWallEnd - 1;
                    if (gap > maxGap) maxGap = gap;
                }
                lastWallEnd = c;
            }
            double gapKm = maxGap * CELL_X / 1000.0;
            boolean wallOk = nLand > 0;   // 判定只用陆地：虚拟墙是纬向屏障，不属于本表的经向判据
            boolean basinOk = wallOk && gapKm >= MIN_BASIN_KM;
            if (wallOk) withWall++;
            if (basinOk) okBands++;
            int z0 = r0 * CELL_Z, z1 = r1 * CELL_Z;
            say(String.format("  %3d | %6d – %6d | %.2f – %.2f | %6d | %11d | %16.0f | %s%s",
                b, z0 / 1000, z1 / 1000,
                GlobalCirculation.bandD(z0), GlobalCirculation.bandD(z1),
                nLand, nWall, gapKm,
                wallOk ? "有墙" : "**无墙**",
                basinOk ? " + 盆够宽 ⇒ 可成环" : (wallOk ? "（盆太窄）" : "")));
        }

        say("");
        say("  [汇总] 有贯通墙的带（**只算陆地**）= " + withWall + "/" + BANDS
            + "（" + pct(withWall, BANDS) + "）");
        say("         历史对照：冰盖时代是 5/10 —— 极地那两带当年靠『bandD>0.82 全成陆』");
        say("         这块完美纬线圈撑着。定案把它收成 0.96 并换成极地海盆后，那两带**按设计**变成无墙。");
        say("         其中盆宽达标的 = " + okBands + "/" + BANDS + "（" + pct(okBands, BANDS) + "）");
        say("");
        say("  [地球参照]（手工列，用于对照同一把尺子）");
        say("     · 被大陆挡住的纬度带：约 91%（只有 45–62°S 这 ~17° 是真正贯通的 → 那里是 ACC）");
        say("     · 挡住的形态：美洲跨 110°、非洲-欧亚跨 105°、澳洲跨 35° —— **有限长条带，端点错开**");
        say("     · 所以地球不是「两块墙」也不是「一条通道」，而是**一叠环流圈 + 一条贯通带**");
        say("");
        say("  [目标] 若要让我们的世界达到地球那种「一叠环流圈」的构造，");
        say("         本表的『有贯通墙的带』应达到 ~85% 以上，并保留 1~2 条无墙的带作为贯通带。");
        say("");
        say("PROFILE_STATUS=DONE");
        rep.flush();
        rep.close();
        System.exit(0);
    }

    static String pct(int a, int b) {
        return String.format("%.0f%%", 100.0 * a / Math.max(1, b));
    }

    static void say(String s) {
        System.out.println("[P233] " + s);
        rep.println("[P233] " + s);
    }
}
