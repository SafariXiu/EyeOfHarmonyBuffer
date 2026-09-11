package probe;

import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.RelaxedClimate;
import com.EyeOfHarmonyBuffer.space.talos.chunk.continent_layer.NoiseContinentGrid;

import java.io.File;
import java.io.PrintStream;

/**
 * P205：isLandCell 修复的验收（重跑 P191 第一部分的同一张表）。
 *   A) 同一张表：z 行 x 400 列，查询判定（sampleSst 是否 NaN）vs 真值（landResidual>=0）→ 期望 0% 不一致
 *   B) 极区/曾经整行判错的那几行，现在给出什么值
 *   C) 口径说明：本探针**故意用 tile 主序**（先 tileX，再列，再 z），
 *      因为 P191 用的行主序会让工作集超过 CACHE_LIMIT=9 → 缓存抖动（那正是 P191 挂住的原因）。
 * 预算：约 2-3 分钟。
 */
public class P205 {

    static final int SEED = 1022228679;
    static final int TILE_X = 100_000, ZC = GlobalCirculation.Z_CYCLE;
    static final int STEP = 2_000, XMAX = 800_000;
    static final int ROWS = ZC / 5_000;

    static File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        RelaxedClimate.PREHEAT = false;
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p205_report.txt"), "UTF-8");
        long t0 = System.nanoTime();
        say("isLandCell 修复验收：口径与 P191 第一部分完全相同（z 每 5km 一行、x 每 2km 一点）");
        say("采样顺序改为 tile 主序（先 tileX → 该 tile 的列 → 行），避免缓存抖动；PREHEAT=false");
        say("");

        int[] bandTot = new int[5], bandMis = new int[5], bandTrueLand = new int[5], bandFalseLand = new int[5], bandFalseSea = new int[5];
        int worstRow = -1, worstRowMis = -1;
        int[][] mis = new int[ROWS][1];
        int totalMis = 0, totalPts = 0;

        say("===== A) 逐行不一致率（只打印每 20 行，与 P191 同口径）=====");
        say("z(k) | 采样点 | 不一致 | 不一致率 | 真值陆 | 查询判陆");
        // tile 主序
        int[] rowMis = new int[ROWS], rowTot = new int[ROWS], rowTrue = new int[ROWS], rowQL = new int[ROWS];
        for (int tx = 0; tx * TILE_X < XMAX; tx++) {
            for (int i = 0; i < XMAX / STEP; i++) {
                int x = i * STEP + 1_000;
                if (x < tx * TILE_X || x >= (tx + 1) * TILE_X) continue;
                for (int j = 0; j < ROWS; j++) {
                    int z = j * 5_000 + 2_500;
                    boolean truth = NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0;
                    boolean q = Double.isNaN(RelaxedClimate.sampleSst(x, z, SEED));
                    rowTot[j]++;
                    if (truth) rowTrue[j]++;
                    if (q) rowQL[j]++;
                    if (truth != q) {
                        rowMis[j]++;
                        totalMis++;
                        if (truth) bandFalseLand[band(z)]++; else bandFalseSea[band(z)]++;
                    }
                }
            }
        }
        totalPts = ROWS * (XMAX / STEP);
        for (int j = 0; j < ROWS; j++) {
            int z = j * 5_000 + 2_500;
            bandTot[band(z)] += rowTot[j];
            bandMis[band(z)] += rowMis[j];
            bandTrueLand[band(z)] += rowTrue[j];
            if (rowMis[j] > worstRowMis) { worstRowMis = rowMis[j]; worstRow = z; }
            if (j % 20 == 0) {
                say(String.format("%4d | %6d | %5d | %6.1f%% | %6d | %6d",
                    z / 1000, rowTot[j], rowMis[j], 100.0 * rowMis[j] / rowTot[j], rowTrue[j], rowQL[j]));
            }
        }
        say("最差行：z=" + (worstRow / 1000) + "km，不一致 " + worstRowMis);
        String[] bn = {"赤道附近 0.0-0.2", "副热带 0.2-0.4", "中纬 0.4-0.6", "副极地 0.6-0.8", "极地 0.8-1.0"};
        for (int b = 0; b < 5; b++) {
            say(String.format("%s ：不一致 %d/%d = %.3f%%（真值陆 %d；其中把海判成陆 %d、把陆判成海 %d）",
                bn[b], bandMis[b], bandTot[b], 100.0 * bandMis[b] / bandTot[b], bandTrueLand[b],
                bandFalseLand[b], bandFalseSea[b]));
        }
        say(String.format("总计：不一致 %d / %d = %.4f%%", totalMis, totalPts, 100.0 * totalMis / totalPts));
        say("（P191 修复前同表的数字：z=102km 72.8%、302km 68.3%、402km 84.3%、502km 100.0%、602km 32.5%、702km 62.5%、2km 35.3%）");
        say("");

        say("===== B) 曾经整行判错的那几行，现在给出什么 =====");
        String[] zs = {"2", "102", "302", "402", "502", "602", "702"};
        for (String s : zs) {
            int z = Integer.parseInt(s) * 1000 + 2_500;
            probeRow(z);
        }
        say("");
        say("===== C) 结论 =====");
        say("  修复前：z=502km 一行真值 100% 陆、旧代码全判成海（SST 把整片陆地当海面算）；");
        say("          z=402km 一行真值 0% 陆、旧代码判出 337/400 是陆（整片海面被丢掉 SST → NaN → ClimateCoords 丢项）。");
        say("  修复后：见 A) 与 B)。");
        say("总耗时 " + ((System.nanoTime() - t0) / 1_000_000_000) + "s");
        rep.close();
    }

    static int band(int z) {
        double lat = Math.min(z, ZC - z) / (ZC / 2.0);
        return Math.min(4, (int) (lat / 0.2));
    }

    static void say(String s) { System.out.println("[P205] " + s); rep.println("[P205] " + s); }

    /** 某一行：真值陆/海统计 + SST 值域（依然 tile 主序）。 */
    static void probeRow(int z) {
        int landN = 0, seaN = 0, falseLand = 0, falseSea = 0, nanSea = 0;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0;
        int n = 0;
        for (int tx = 0; tx * TILE_X < XMAX; tx++) {
            for (int i = 0; i < XMAX / STEP; i++) {
                int x = i * STEP + 1_000;
                if (x < tx * TILE_X || x >= (tx + 1) * TILE_X) continue;
                boolean truth = NoiseContinentGrid.landResidual(x, z, SEED) >= 0.0;
                double sst = RelaxedClimate.sampleSst(x, z, SEED);
                boolean q = Double.isNaN(sst);
                if (truth) {
                    landN++;
                    if (!q) falseLand++;
                } else {
                    seaN++;
                    if (q) { falseSea++; nanSea++; }
                    else { sum += sst; n++; if (sst < min) min = sst; if (sst > max) max = sst; }
                }
            }
        }
        say(String.format("  z=%4dkm：真值 陆 %3d / 海 %3d | 把海判成陆(NaN) %d | 把陆判成海(有值) %d | 真海格 SST：min=%s max=%s 均值=%s（n=%d）",
            z / 1000, landN, seaN, falseSea, falseLand,
            n > 0 ? String.format("%.4f", min) : "-", n > 0 ? String.format("%.4f", max) : "-",
            n > 0 ? String.format("%.4f", sum / n) : "-", n));
    }
}
