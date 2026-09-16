package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P477 **v2**：A4 / A5 的读数（舌头幅值与尺度）—— 判据①的**子集口径已收敛**。
 *
 * <h3>v1 -> v2 改了什么（§181.5 预登记的）</h3>
 * v1 的判据①用「副热带子集 |lat| 20~50」，但读表就能看出**它混进了副极地盆**：
 * 45N / 55N 的「暖峰」出现在「距西 = 全盆宽」处，也就是**紧贴东端** ——
 * 那是副极地环流的**东边界暖流**（阿拉斯加流那一路），**不是副热带西边界暖舌**。
 * 把两类盆混在一个「暖峰是否落在 +4~8 K」的判据里，口径是脏的。
 *
 * <p><b>v2 的收敛方式（跑之前写死）</b>：按**西端 T' 的符号**把海盆分成两类 ——
 * <ul>
 *   <li><b>暖西边界流盆（warmWBC）</b>：西端 T' &gt; 0 ⇒ 西边界流向极 ⇒ 有**副热带型暖舌**；
 *       <b>只有这类盆进判据①和③</b>；</li>
 *   <li><b>冷西边界流盆（coldWBC）</b>：西端 T' &lt; 0 ⇒ 副极地型（拉布拉多/亲潮）。
 *       它们**单独报告**：西端的冷峰幅值、以及**东端的暖峰**（阿拉斯加流那一路）。</li>
 * </ul>
 *
 * <p>⚠ 我**没有**追溯修改 v1 的结论：v1 的判据①「16/21 落在 +4~8」照实保留在 §181；
 * 本节是**新一代判据**在同一份数据上的读数。
 */
public class P477 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    /** 西边界流分类的**显著性门**（K）：|西端 T'| 小于它视为「该处没有边界流」。 */
    static final double WBC_MIN = 0.5;
    static PrintStream rep;

    static void say(String s) { rep.println("[P477] " + s); rep.flush(); System.out.println("[P477] " + s); System.out.flush(); }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }
    static double minOf(double[] v, int n) { double m = Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.min(m, v[i]); return n == 0 ? Double.NaN : m; }
    static double maxOf(double[] v, int n) { double m = -Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.max(m, v[i]); return n == 0 ? Double.NaN : m; }

    static List<int[]> basinsAt(int z) {
        LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
        for (int x = -WorldContract.MAX_D; x <= WorldContract.MAX_D; x += SCAN) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return new ArrayList<>(m.values());
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p477_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P477 v2：A4 / A5 —— 舌头幅值与尺度（判据①子集已按「西端 T' 的符号」收敛）");
        say(String.format(LF, "  接线：installedSeed=%d  ROW_H=%.0f m", OceanField.installedSeed(), OceanField.ROW_H));
        say("  观测锚（SeaSurfaceTemp 的 javadoc）：暖舌 **+4~8 K**、冷舌 **-4~5 K**");
        say("");
        int[] lats = {15, 25, 35, 45, 55, -15, -25, -35, -45};
        say(String.format(LF, "  %-6s %-9s %-8s %-8s %-9s %-7s %-9s %-9s %-7s %-9s %-7s %-9s",
            "lat", "盆西km", "宽km", "类", "暖峰K", "距西km", "暖FWHMkm", "冷峰K", "距东km", "冷FWHMkm", "内区K", "饱和"));
        double[] wWarm = new double[64]; int nWarm = 0;          // warmWBC 的暖峰（判据①）
        double[] fWarm = new double[64];                        // warmWBC 的暖舌 FWHM（判据③）
        double[] cWarm = new double[64];                        // warmWBC 的东端值（冷舌代理）
        double[] wCold = new double[64], eCold = new double[64]; int nCold = 0;   // coldWBC
        boolean coldPeakAtWest = true;   // 仪器自检（E44）
        int nExcluded = 0;               // 被显著性门排除的盆数
        long t0 = System.nanoTime();
        for (int latDeg : lats) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            for (int[] b : basinsAt(z)) {
                int wx = b[0], ex = b[1];
                int width = ex - wx;
                if (width < 200_000) continue;
                int step = Math.max(5_000, width / 3000);
                int m = width / step + 1;
                int[] xs = new int[m]; double[] tv = new double[m];
                for (int i = 0; i < m; i++) { xs[i] = wx + i * step; tv[i] = OceanField.anomalyAt(xs[i], z, SEED); }
                int lo = m / 4, hi = 3 * m / 4;
                double[] mid = Arrays.copyOfRange(tv, lo, hi); Arrays.sort(mid);
                double base = mid[mid.length / 2];
                int iW = 0, iC = 0;
                for (int i = 1; i < m; i++) { if (tv[i] > tv[iW]) iW = i; if (tv[i] < tv[iC]) iC = i; }
                double wPk = tv[iW], cPk = tv[iC];
                double wHalf = base + 0.5 * (wPk - base), cHalf = base + 0.5 * (cPk - base);
                double wW = 0, cW = 0;
                if (wPk > base) { int a = iW; while (a > 0 && tv[a - 1] > wHalf) a--; int c = iW; while (c < m - 1 && tv[c + 1] > wHalf) c++; wW = (c - a) * (double) step; }
                if (cPk < base) { int a = iC; while (a > 0 && tv[a - 1] < cHalf) a--; int c = iC; while (c < m - 1 && tv[c + 1] < cHalf) c++; cW = (c - a) * (double) step; }
                int nSat = 0;
                for (int i = 0; i < m; i++) if (Math.abs(tv[i]) >= 0.99 * com.EyeOfHarmonyBuffer.sim.ocean.SeaSurfaceTemp.ANOM_MAX) nSat++;
                double satPct = 100.0 * nSat / m;
                // ★ v2 的分类：西端 T' 的符号
                // ★★ v2 的分类（E44 之后的物理修正）：**必须带显著性门**。
                // 只按「符号」分类会把热带盆也算进来 —— 表里 -15 度那几个盆的西端 T' 是 -0.02 K 这种
                // **噪声级**的值，却被判成「冷西边界流盆」，而它们真正的冷特征是**东端**的
                // ⇒ 自检报「冷峰不在西端」。
                // 物理上：**边界流只在距平显著的地方才存在**（噪声级距平没有对应的流）。
                // 门限取 0.5 K：低于它的盆**两类都不进**（既不判暖舌也不判冷舌）。
                double tWest = tv[Math.min(1, m - 1)];
                boolean warmWBC = tWest >= WBC_MIN;
                boolean coldWBC = tWest <= -WBC_MIN;
                int a = Math.abs(latDeg);
                boolean sub = a >= 15 && a <= 45;
                if (warmWBC && sub) { wWarm[nWarm] = wPk; fWarm[nWarm] = wW / 1000.0; cWarm[nWarm] = tv[m - 1]; nWarm++; }
                if (coldWBC && sub) {
                    // ⚠⚠ E44：v2 第一版这里存的是 **wPk（暖峰）**，而判据②要的是**冷峰** ⇒
                    // 它把「东端的阿拉斯加流暖峰 +7.99」当成了冷舌，报出「冷峰中位 +7.50 ⇒ 不通过」。
                    // **那是仪器的错，不是产品的错。** 现在存 cPk（全局极小 = 西端冷舌）。
                    wCold[nCold] = cPk;
                    eCold[nCold] = tv[m - 1];
                    // 自检：冷WBC盆的冷峰必须在**西端**（距西 < 10% 盆宽），否则这个分类没意义
                    coldPeakAtWest = coldPeakAtWest && ((xs[iC] - wx) <= 0.10 * width);
                    nCold++;
                }
                if (!warmWBC && !coldWBC) nExcluded++;
                say(String.format(LF, "  %-6d %-9d %-8d %-8s %-9.2f %-7d %-9.0f %-9.2f %-7d %-9.0f %-7.2f %-9s",
                    latDeg, wx / 1000, width / 1000, warmWBC ? "暖WBC" : (coldWBC ? "冷WBC" : "(无)"), wPk, (xs[iW] - wx) / 1000, wW / 1000.0,
                    cPk, (ex - xs[iC]) / 1000, cW / 1000.0, base, String.format(LF, "%.1f%%", satPct)));
            }
        }
        say("");
        say(String.format(LF, "  ⇒ 耗时 %.1f s；被显著性门（|西端 T'| < %.1f K）排除 %d 个盆", (System.nanoTime() - t0) / 1e9, WBC_MIN, nExcluded));
        say(String.format(LF, "  **暖西边界流盆（西端 T' > 0，|lat| 15~45）：%d 个**", nWarm));
        say(String.format(LF, "     暖峰 K：中位 %+.2f（范围 %+.2f ~ %+.2f）", median(wWarm, nWarm), minOf(wWarm, nWarm), maxOf(wWarm, nWarm)));
        say(String.format(LF, "     暖舌 FWHM km：中位 %.0f（范围 %.0f ~ %.0f）", median(fWarm, nWarm), minOf(fWarm, nWarm), maxOf(fWarm, nWarm)));
        say(String.format(LF, "     东端 T'（冷舌代理）中位 %+.2f K", median(cWarm, nWarm)));
        say(String.format(LF, "  **冷西边界流盆（副极地型，|lat| 15~45）：%d 个**", nCold));
        say(String.format(LF, "     西端冷峰 K：中位 %+.2f（范围 %+.2f ~ %+.2f）", median(wCold, nCold), minOf(wCold, nCold), maxOf(wCold, nCold)));
        say(String.format(LF, "     东端暖峰 K：中位 %+.2f（范围 %+.2f ~ %+.2f）  <- 阿拉斯加流那一路", median(eCold, nCold), minOf(eCold, nCold), maxOf(eCold, nCold)));
        say(String.format(LF, "     [自检] 冷WBC盆的冷峰是否都落在**西端**（距西 <= 10%% 盆宽）：%s",
            coldPeakAtWest ? "是（分类自洽）" : "**否 —— 分类或取值有问题**"));
        say("");
        int inBand = 0; for (int i = 0; i < nWarm; i++) if (wWarm[i] >= 4.0 && wWarm[i] <= 8.0) inBand++;
        int wide = 0; for (int i = 0; i < nWarm; i++) if (fWarm[i] >= 50.0) wide++;
        double medWarm = median(wWarm, nWarm), medFw = median(fWarm, nWarm);
        say("  判定标准（v2，跑之前写死）：");
        say("   ① A5'-暖（在**暖西边界流盆**上）：暖峰中位数落在观测锚 +4~8 K 内，且 >=4 K 的盆占多数；");
        say("   ② A5'-冷（在**冷西边界流盆**上）：西端冷峰中位 <= -4.0 K（观测加州/亲潮 -4~5 K）；");
        say("   ③ A4'（尺度）：暖西边界流盆的暖舌 FWHM 中位 >= 50 km。");
        say("");
        say(String.format(LF, "  ① 暖峰中位 %+.2f K；落在 +4~8 K 的盆 %d/%d ⇒ %s",
            medWarm, inBand, nWarm, (medWarm >= 4.0 && medWarm <= 8.0) ? "通过" : "**不通过**"));
        say(String.format(LF, "  ② 冷峰中位 %+.2f K ⇒ %s", median(wCold, nCold), median(wCold, nCold) <= -4.0 ? "通过" : "**不通过**"));
        say(String.format(LF, "  ③ 暖舌 FWHM 中位 %.0f km；>=50 km 的盆 %d/%d ⇒ %s", medFw, wide, nWarm, medFw >= 50 ? "通过" : "**不通过**"));
        say("");
        say(String.format(LF, "  reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读 OceanField 的公开取值接口，未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
