package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P268：**B4 雨影的正确测法**。
 *
 * <p>上一轮（P267）按**本地海拔**分档，测出来只是「高处雨少」——那不构成雨影证据，
 * 因为雨影是**同一本地海拔下、上风地形不同**的差。
 *
 * <p>本探针按 **(本地海拔, 上风海拔)** 二维分档，并且同时给出去掉损耗的反事实值，
 * 直接量「上风的山把下游抽干了多少」。
 */
public class P268 {

    static final int SEED = 1022228679;
    /**
     * ⚠⚠ 2026-09-19 修正（D 类「仪器口径」，**E5a 类错误**；模板 = P292:38、冻结 §559）：
     * {@code PlateField}/{@code Atmosphere}/{@code PrecipField} 的入口收的是 <b>派生后</b> 的长种子，
     * 不是裸世界种子。
     *
     * <p>生产路径：{@code OceanField:205 long seed = SimTerrain.seedOf(worldSeedInt)}，随后所有
     * 地形/风/降水调用都用它。本探针原来把裸世界种子 {@code SEED} 直接传给了 6 处收
     * {@code long seed} 的入口 —— {@code isLandWithCell}(:56)、{@code elevationWithCell}(:57)、
     * {@code windAt}(:59)、{@code upwindElev}(:60)、{@code mmPerDay}(:61)、{@code kappaAt}(:62)
     * ⇒ 陆地掩膜、海拔、上风地形、降水、κ 全不同 ⇒ **量的不是同一个世界**。
     * 同型缺陷本仓已修三次：E5a（冻结 :6208）、P539（:22955/:22962）、P292（§558）。
     *
     * <p>判据：**收 {@code long seed} 的入口必须传派生值 {@code SD}；收 {@code int worldSeedInt}
     * 的入口传裸值**。本文件的 {@code OceanWiring.onWorld(SEED)}（:38）按定义收 int 世界种子
     * ⇒ **保持裸 int**（生产同样如此）。
     * <p>本次改动**不动任何判据、判据域、阈值**：本门是 (c) 类「已淘汰、仅作诊断」的旧极端档
     * 读数（用户裁决 2026-09-13）；B4 的验收口径 = P452（质量加权），本文件的 1.8~4.1 锚带与
     * 分档边界（250/750/1500 m）一字未动。
     */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int GRAD = 20_000;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P268] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p268_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("P268：B4 雨影 —— 按 (本地海拔, 上风海拔) 二维分档");
        say(String.format(LF, "  H_MOIST=%.0f m  UPWIND_STEP=%.0f km  网格步长 250km", PrecipField.H_MOIST, PrecipField.UPWIND_STEP / 1000));
        say("");

        int NX = 44, NZ = 80;
        int[][] cnt = new int[4][4];
        double[][] sumP = new double[4][4], sumN = new double[4][4];
        int used = 0;
        for (int r = 0; r < NZ; r++) {
            int z = (int) ((r + 0.5) / NZ * ZC);
            for (int c = 0; c < NX; c++) {
                int x = (int) ((c + 0.5) / NX * 11_400_000);
                if (!PlateField.isLandWithCell(x, z, SD, cell)) continue;
                double e = PlateField.elevationWithCell(x, z, SD, cell);
                if (e <= 0) continue;
                double[] u = Atmosphere.windAt(x, z, SD, cell, th, GRAD);
                double hUp = PrecipField.upwindElev(x, z, SD, cell, u[0], u[1]);
                double p = PrecipField.mmPerDay(x, z, SD, cell, th, GRAD);
                double k = Atmosphere.kappaAt(x, z, SD, cell);
                double deplete = Math.exp(-Math.max(0.0, hUp) / PrecipField.H_MOIST * k);
                double pNoLoss = deplete > 1e-9 ? p / deplete : p;
                int bi = bin(e), bj = bin(hUp);
                cnt[bi][bj]++; sumP[bi][bj] += p; sumN[bi][bj] += pNoLoss;
                used++;
            }
        }
        String[] lab = {"<250m", "250-750", "750-1500", ">1500"};
        say("A. 平均降水（mm/day）—— 行 = 本地海拔，列 = **上风海拔**");
        say(String.format(LF, "   %-12s %12s %12s %12s %12s", "本地\\上风", lab[0], lab[1], lab[2], lab[3]));
        for (int i = 0; i < 4; i++) {
            StringBuilder sb = new StringBuilder(String.format(LF, "   %-12s", lab[i]));
            for (int j = 0; j < 4; j++) {
                sb.append(String.format(LF, "%12s", cnt[i][j] == 0 ? "-" : String.format(LF, "%.2f", sumP[i][j] / cnt[i][j])));
            }
            say(sb.toString());
        }
        say("");
        say("B. **去掉过山损耗的反事实值**（同一批点）");
        say(String.format(LF, "   %-12s %12s %12s %12s %12s", "本地\\上风", lab[0], lab[1], lab[2], lab[3]));
        for (int i = 0; i < 4; i++) {
            StringBuilder sb = new StringBuilder(String.format(LF, "   %-12s", lab[i]));
            for (int j = 0; j < 4; j++) {
                sb.append(String.format(LF, "%12s", cnt[i][j] == 0 ? "-" : String.format(LF, "%.2f", sumN[i][j] / cnt[i][j])));
            }
            say(sb.toString());
        }
        say("");
        say("C. 雨影比 = 实际 / 反事实（<1 就是被上风的山抽干了）");
        say(String.format(LF, "   %-12s %12s %12s %12s %12s", "本地\\上风", lab[0], lab[1], lab[2], lab[3]));
        for (int i = 0; i < 4; i++) {
            StringBuilder sb = new StringBuilder(String.format(LF, "   %-12s", lab[i]));
            for (int j = 0; j < 4; j++) {
                sb.append(String.format(LF, "%12s", cnt[i][j] == 0 ? "-"
                    : String.format(LF, "%.2f", sumP[i][j] / sumN[i][j])));
            }
            say(sb.toString());
        }
        say("");
        // ==================== ★ B4 判据（**观测锚化**，见设计冻结 §132.2） ====================
        //
        // ⚠ 2026 变更：原判据「>= 5 倍」**没有任何观测来源** —— §125.2 已查明它是照着模型自己
        //    早期的 5.55 定的（自指），所以它一路下滑而没人发现异常。现改锚到**真实观测**：
        //      P. J. Taylor (1980) "A Pedagogic Application of Multiple Regression Analysis:
        //      Precipitation in California", Geography 65(3) 203-212 —— 加州 30 个气象站，
        //      含 W(迎风)/L(背风) 朝向标记与海拔。
        //      同海拔 ANCOVA 比 = 2.68；原始均值比 2.75；自举 2000 次 95% 区间 1.82 ~ 4.08。
        //    ⇒ 判据取 **1.8 ~ 4.1**（中心 2.7）。
        //    ⚠ 局限（§132.4）：加州是**站点朝向分类**，本判据是**网格按上风地形高度分档**，
        //      不是同一个量（映射方向对，但别把它当成全球真值）。
        //
        // 定义：在「本地海拔 ∈ {250~750, 750~1500, >1500 m}」这三档里，
        //       分别取「上风海拔 < 250 m」与「上风海拔 > 1500 m」两组的**平均降水**，
        //       两组各按档取均值（空档跳过，不参与平均），再相除。
        //
        //   雨影比 = mean_i(P[本地_i, 上风<250m]) / mean_i(P[本地_i, 上风>1500m])
        //
        // 为什么是这个口径：雨影不是「高处雨少」（那只是高度效应），而是
        // **同一本地海拔下、上风地形不同**造成的差 —— 它把高度效应消掉了。
        // 本地_i 从 1 起（跳过 <250 m 那一档）是因为低地本来就少雨，会污染分子。
        final double B4_LO = 1.8, B4_HI = 4.1, B4_MID = 2.7;   // 观测锚带（§132.2）
        double w = 0, lee = 0; int nw = 0, nl = 0;
        StringBuilder det = new StringBuilder();
        for (int i = 1; i < 4; i++) {
            if (cnt[i][0] > 0) { double v = sumP[i][0] / cnt[i][0]; w += v; nw++; det.append(String.format(LF, " [%s|上风矮 n=%d %.2f]", lab[i], cnt[i][0], v)); }
            if (cnt[i][3] > 0) { double v = sumP[i][3] / cnt[i][3]; lee += v; nl++; det.append(String.format(LF, " [%s|上风高 n=%d %.2f]", lab[i], cnt[i][3], v)); }
        }
        double wm = nw > 0 ? w / nw : 0, lm = nl > 0 ? lee / nl : 0;
        double ratio = lm > 0 ? wm / lm : 0;
        say("");
        say("   ⚠ 本口径已**淘汰，仅作诊断**（用户裁决 2026-09-13）：它对网格密度敏感 34.3%，");
        say("     历史值 5.55→7.23→7.24→7.46→6.93→4.19→4.18→3.99→4.14→4.42 从来没有良定义。");
        say("     **B4 的验收口径 = P452 的质量加权版**。下面这个数只用于对照。");
        say("   ---（诊断）旧极端档，锚 = Taylor 1980 加州 W/L 同海拔比 1.8~4.1 ---");
        say(String.format(LF, "   雨影比 = mean_i(P[本地_i, 上风<250m]) / mean_i(P[本地_i, 上风>1500m])"));
        say(String.format(LF, "   分子 = %.2f mm/day（n=%d 档）   分母 = %.2f mm/day（n=%d 档）", wm, nw, lm, nl));
        say(String.format(LF, "   ⇒ **B4 = %.2f 倍**   观测锚带 %.1f~%.1f（中心 %.1f，95%% 区间）   %s   （相对中心 %.2fx）",
            ratio, B4_LO, B4_HI, B4_MID,
            (ratio >= B4_LO && ratio <= B4_HI) ? "落在锚带内 ✓"
                : (ratio > B4_HI ? "**高于锚带上沿**" : "**低于锚带下沿**"),
            ratio / B4_MID));
        say("   分档明细：" + det.toString());
        say(String.format(LF, "   （总共用了 %d 个陆地采样点；网格 44x80 dz=250km；seed=%d；Theta=0）", used, SEED));
        rep.close();
    }

    static int bin(double h) {
        if (h < 250) return 0;
        if (h < 750) return 1;
        if (h < 1500) return 2;
        return 3;
    }

    static void say(String s) { System.out.println("[P268] " + s); rep.println("[P268] " + s); }
}
