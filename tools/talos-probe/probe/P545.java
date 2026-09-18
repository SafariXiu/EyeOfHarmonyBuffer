package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/**
 * P545 —— **高纬度陆地占比 vs 地球**（同口径对照）。
 *
 * <p><b>口径（这是本节的全部要点）</b>：
 * <ul>
 *   <li>模型侧：{@code |lat|} 5 度带 × **整圈 40,000 km** × **16 世界系综**。</li>
 *   <li>地球侧：**由脚本从 ETOPO1 缓存算出并落盘**（{@code earth_land_band.tsv}，
 *       由 {@code earth_land_band.npz} 导出）—— 探针**不许手抄地球值**（M14）。</li>
 *   <li>用 {@code land%unif}（均匀纬度）那一栏比：模型的 z 行是均匀抽样的，
 *       与面积加权口径不同；两者在每条带内差 <0.4 pt，但口径必须说清。</li>
 * </ul>
 *
 * <p><b>为什么必须用 {@code |lat|} 而不是南北分列</b>：地球侧的表就是 {@code |lat|} 带
 * （南北合并）。D1 之后模型的一条 z 周期里，{@code |lat| ∈ [60,65)} 会出现在**四个分支**
 * （N 升 / N 降 / S 降 / S 升）⇒ 合并四条分支才与地球的南北合并同口径。
 */
public class P545 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File TSV = new File(ROOT, "build/eoh_probe/refs/earth_land_band.tsv");
    static PrintStream rep;
    static void say(String s) { rep.println("[P545] " + s); System.out.println("[P545] " + s); }

    static final int NX = 1000;       // 40,000 km / 1000 = 40 km 步长
    static final int NZ = 720;        // 40,000 km 周期 / 720 = 55.6 km 一行 = 0.5 度
    static final int NB = 18;         // |lat| 5 度带，0..89
    static final int NW = 16;         // 系综世界数

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p545_report.txt"), "UTF-8");
        say("P545：高纬度陆地占比 vs 地球（同口径：|lat| 5 度带 × 整圈 40,000 km × " + NW + " 世界系综）");
        say("  契约自证：Z_CYCLE=" + WorldContract.Z_CYCLE + "  MAX_D=" + WorldContract.MAX_D
            + "  TALOS_TERRAIN=" + PlateField.TALOS_TERRAIN);
        say(String.format(LF, "  采样：NX=%d（%.0f km 步长）  NZ=%d（%.1f 度一行）",
            NX, 40_000.0 / NX, NZ, 360.0 / NZ));
        say("");

        // ---- 地球参照（脚本落盘，不许手抄）----
        double[] eUnif = new double[NB];
        double[] eArea = new double[NB];
        for (String ln : Files.readAllLines(TSV.toPath(), StandardCharsets.UTF_8)) {
            if (ln.startsWith("#") || ln.trim().isEmpty()) continue;
            String[] q = ln.split("\t");
            int lo = Integer.parseInt(q[0].trim());
            int idx = lo / 5;
            if (idx < 0 || idx >= NB) continue;
            eArea[idx] = Double.parseDouble(q[2].trim());
            eUnif[idx] = Double.parseDouble(q[3].trim());
        }
        say("  ✔ 地球参照已从 " + TSV.getName() + " 读入（ETOPO1 0.25 度，land := h > 0 m）");
        say("");

        int cell = PlateField.PLATE_CELL;
        double[][] perWorld = new double[NW][NB];   // 每个世界的每条带陆地占比
        double[] glob = new double[NW];
        long[] bandN = new long[NB];

        double t0 = System.nanoTime();
        for (int wi = 0; wi < NW; wi++) {
            long sd = SimTerrain.seedOf(wi + 1);
            long[] lc = new long[NB];
            long[] tc = new long[NB];
            long gl = 0, gt = 0;
            for (int r = 0; r < NZ; r++) {
                int z = (int) ((r + 0.5) / NZ * WorldContract.Z_CYCLE);
                double absLat = Math.abs(Math.toDegrees(WorldContract.latOf(z)));
                int bi = (int) (absLat / 5.0);
                if (bi >= NB) bi = NB - 1;
                int nl = 0;
                for (int c = 0; c < NX; c++) {
                    int x = (int) Math.round((c + 0.5) * 40_000_000.0 / NX);
                    if (PlateField.isLandWithCell(x, z, sd, cell)) nl++;
                }
                lc[bi] += nl; tc[bi] += NX; gl += nl; gt += NX;
            }
            for (int b = 0; b < NB; b++) {
                perWorld[wi][b] = tc[b] > 0 ? 100.0 * lc[b] / tc[b] : 0.0;
                bandN[b] += tc[b];
            }
            glob[wi] = 100.0 * gl / gt;
        }
        say(String.format(LF, "  ✔ 采样完成：%d 世界 x %d 行 x %d 点 = %.1f M 次 isLand，耗时 %.1f s",
            NW, NZ, NX, NW * (double) NZ * NX / 1e6, (System.nanoTime() - t0) / 1e9));
        say("");

        // ---- 对照表 ----
        say("=== |lat| 5 度带：模型（" + NW + " 世界系综均值） vs 地球（ETOPO1）===");
        say("  带        模型均值   逐世界极差     地球(unif)   地球(area)     差(pt)   行数/带");
        double sumM = 0, sumE = 0; int nb = 0;
        for (int b = 0; b < NB; b++) {
            double mean = 0, mn = 1e9, mx = -1e9;
            for (int wi = 0; wi < NW; wi++) {
                mean += perWorld[wi][b];
                if (perWorld[wi][b] < mn) mn = perWorld[wi][b];
                if (perWorld[wi][b] > mx) mx = perWorld[wi][b];
            }
            mean /= NW;
            if (eUnif[b] <= 0 && b > 0) continue;
            say(String.format(LF, "  %2d-%2d    %8.2f%%   %8.2f pt   %8.2f%%   %8.2f%%   %+7.2f   %d",
                b * 5, b * 5 + 5, mean, mx - mn, eUnif[b], eArea[b], mean - eUnif[b], bandN[b] / NX));
            sumM += mean; sumE += eUnif[b]; nb++;
        }
        say(String.format(LF, "  ⇒ 全带平均：模型 %.2f%%   地球 %.2f%%   差 %+.2f pt", sumM / nb, sumE / nb, sumM / nb - sumE / nb));
        double gmn = 1e9, gmx = -1e9, gm = 0;
        for (int wi = 0; wi < NW; wi++) { gm += glob[wi]; if (glob[wi] < gmn) gmn = glob[wi]; if (glob[wi] > gmx) gmx = glob[wi]; }
        say(String.format(LF, "  ⇒ 全球陆地占比：模型系综均值 %.2f%%（逐世界 %.2f%% ~ %.2f%%）  地球 %.2f%%（area）",
            gm / NW, gmn, gmx, 28.992));
        say("");
        say("=== 关键分区（地球的「60 度以上陡升」我们有没有）===");
        double m06 = 0, e06 = 0, m60 = 0, e60 = 0; int n1 = 0, n2 = 0;
        for (int b = 0; b < 12; b++) { double mm = 0; for (int wi = 0; wi < NW; wi++) mm += perWorld[wi][b]; m06 += mm / NW; e06 += eUnif[b]; n1++; }
        for (int b = 12; b < NB; b++) { double mm = 0; for (int wi = 0; wi < NW; wi++) mm += perWorld[wi][b]; m60 += mm / NW; e60 += eUnif[b]; n2++; }
        say(String.format(LF, "  0~60 度：模型 %.2f%%   地球 %.2f%%   差 %+.2f pt", m06 / n1, e06 / n1, m06 / n1 - e06 / n1));
        say(String.format(LF, "  60~90 度：模型 %.2f%%   地球 %.2f%%   差 %+.2f pt", m60 / n2, e60 / n2, m60 / n2 - e60 / n2));
        say("  （⚠ 地球 60~90 度的陆地**主要是南极洲与格陵兰冰盖**；我们的世界没有冰盖，这一条解读时要带上）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
