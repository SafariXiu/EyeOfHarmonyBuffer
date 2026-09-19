package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P258：**M3 第一片纵向切片验收** —— 温度场（目标 B1）+ 气压异常 + 图像/数值导出（目标 4）。
 *
 * <p>地图覆盖**一个完整气候周期**：z = 0..1,000,000（赤道 -> 极点 -> 赤道），
 * x = 0..2,400,000。同时渲染 PLATE_CELL = 600 km 与 2400 km 两套地形（D5 的可视证据）。
 */
public class P258 {

    static final int SEED = 1022228679;
    /**
     * ⚠⚠ 2026-09-19 修正（D 类「仪器口径」，**E5a 类错误**；模板 = P292:38、冻结 §559）：
     * {@code Atmosphere}/{@code PlateField} 的入口收的是 <b>派生后</b> 的长种子，不是裸世界种子。
     *
     * <p>生产路径：{@code OceanField:205 long seed = SimTerrain.seedOf(worldSeedInt)}，随后
     * 温度/气压/掩膜全用它。本探针原来把裸世界种子 {@code SEED} 直接传给了 7 处收
     * {@code long seed} 的入口 —— {@code surfaceTemp}(:95/:96/:148/:158/:159)、
     * {@code pressureAnomaly}(:97)、{@code isLandWithCell}(:98)
     * ⇒ 渲染出来的温度场/气压场/陆地掩膜（含两张 PNG 与两份 TSV）全来自另一个世界。
     * 同型缺陷本仓已修三次：E5a（冻结 :6208）、P539（:22955/:22962）、P292（§558）。
     *
     * <p>判据：**收 {@code long seed} 的入口必须传派生值 {@code SD}；收 {@code int worldSeedInt}
     * 的入口传裸值**。本文件的 {@code OceanWiring.onWorld(SEED)}（:39）按定义收 int 世界种子
     * ⇒ **保持裸 int**（生产同样如此）。
     * <p>本次改动**不动任何判据、判据域、阈值**：仍是 T_zm 三节点 {@code < 0.005 K}、
     * 仍是 C1 {@code <= 20 s}、仍是两次同参数调用逐位相同。
     */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int X0 = 0, Z0 = 0, STEP = 5000;
    static final int NX = 481, NZ = 201;
    static final int[] CELLS = {600_000, 2_400_000};
    static final int[] CELL_TAG = {600, 2400};

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "build/eoh_probe/mtn/map");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {

        // ── 第三步接线（§162）：验收必须在**生产口径**下跑 ──
        // 生产由 WorldChunkManagerTalos2 -> OceanWiring.onWorld 装 SST'；这里调**同一个入口**，
        // 保证「验收测的」与「生产跑的」是同一段代码（否则就是口径偏移）。
        // 预热是后台线程，本探针不等待 —— 它自己在采样时按行懒解，代价一样。
        OceanWiring.onWorld(SEED);
        System.out.println("[P258] 接线口径：installedSeed=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.installedSeed()
            + "  ENABLED=" + com.EyeOfHarmonyBuffer.sim.ocean.OceanField.ENABLED);
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p258_report.txt"), "UTF-8");
        say("P258：M3 切片 1 —— 温度场 + 气压异常 + 导出");
        say(String.format(LF, "  R_eff=%.0f km  Z_CYCLE=%d  地图 x=[%d,%d] z=[%d,%d] step=%d  (%dx%d)",
            WorldContract.R_EFF / 1000, ZC, X0, X0 + (NX - 1) * STEP, Z0, Z0 + (NZ - 1) * STEP, STEP, NX, NZ));
        say("");

        // ---------------- 自检 ----------------
        say("A. 自检");
        // ⚠ 2026-09-16（§216.7）：T_zm 从三点勒让德拟合换成**观测表**，锚点随之换成表的节点值
        //   （ERA5 t2m 2014+2019 月平均，南北对称化；见 ZonalTables.T_ZM_K 与 refs/gen_zonal_tables.py）。
        //   非循环性说明：这三个数是 anchor 文本文件 ls_anchor_table.txt 里的独立列，
        //   P492 A 段会拿它逐点核对 **5 度表 + 线性插值**；这里只做「表节点没被改动」的快检。
        double[][] anchors = {{0.0, 299.41}, {Math.PI / 4, 282.66}, {Math.PI / 2, 244.27}};
        boolean ok = true;
        for (double[] a : anchors) {
            double got = Atmosphere.tZonalMean(a[0]);
            boolean good = Math.abs(got - a[1]) < 0.005;
            if (!good) ok = false;
            say(String.format(LF, "   T_zm(%5.1f 度) = %.2f K   表节点 %.2f K   %s",
                Math.toDegrees(a[0]), got, a[1], good ? "OK" : "错"));
        }
        say(String.format(LF, "   ⇒ 观测表三个节点：%s", ok ? "全部成立" : "**有错**"));
        say(String.format(LF, "  GATE_ZM_ANCHOR=%s", ok ? "PASS" : "FAIL"));
        say(String.format(LF, "   本世界的纬向平均（κ=⟨κ⟩）与地球 T_zm 的差：赤道 %+.2f K、45 度 %+.2f K、极 %+.2f K",
            Atmosphere.zonalMeanSeaLevelK(0.0) - Atmosphere.tZonalMean(0.0),
            Atmosphere.zonalMeanSeaLevelK(Math.PI / 4) - Atmosphere.tZonalMean(Math.PI / 4),
            Atmosphere.zonalMeanSeaLevelK(Math.PI / 2) - Atmosphere.tZonalMean(Math.PI / 2)));
        double th0 = Atmosphere.theta(0.0), thHalf = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double dtls0 = Atmosphere.seasonalAnomaly(Math.PI / 4, true, th0)
                     - Atmosphere.seasonalAnomaly(Math.PI / 4, false, th0);
        double dtlsH = Atmosphere.seasonalAnomaly(Math.PI / 4, true, thHalf)
                     - Atmosphere.seasonalAnomaly(Math.PI / 4, false, thHalf);
        say(String.format(LF, "   45 度海陆温差：夏至 %+.2f K   冬至 %+.2f K  （设计目标 +8.7 / -8.7，自动反相）",
            dtls0, dtlsH));
        say(String.format(LF, "   南半球 45 度同相位：夏至 %+.2f K（应与北半球反号）",
            Atmosphere.seasonalAnomaly(-Math.PI / 4, true, th0)));
        say("");

        // ---------------- 渲染 ----------------
        say("B. 渲染");
        say(String.format(LF, "   %-10s %-16s %10s %10s %10s %9s", "PLATE_CELL", "场", "min", "max", "mean", "耗时ms"));
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            double[] tempS = new double[NX * NZ];
            double[] tempW = new double[NX * NZ];
            double[] presS = new double[NX * NZ];
            double[] mask = new double[NX * NZ];
            long t0 = System.nanoTime();
            for (int r = 0; r < NZ; r++) {
                int z = Z0 + r * STEP;
                for (int c = 0; c < NX; c++) {
                    int x = X0 + c * STEP;
                    int i = r * NX + c;
                    tempS[i] = Atmosphere.surfaceTemp(x, z, SD, cell, th0);
                    tempW[i] = Atmosphere.surfaceTemp(x, z, SD, cell, thHalf);
                    presS[i] = Atmosphere.pressureAnomaly(x, z, SD, cell, th0);
                    mask[i] = PlateField.isLandWithCell(x, z, SD, cell) ? 1.0 : 0.0;
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            // 温度范围：用两季合并的固定色标，方便对比
            double lo = 230, hi = 315;
            String tag = "c" + CELL_TAG[ci];
            MapWriter.writePng(new File(MAPDIR, "p258_" + tag + "_temp_summer.png"), NX, NZ, tempS, lo, hi, MapWriter.THERMAL);
            MapWriter.writePng(new File(MAPDIR, "p258_" + tag + "_temp_winter.png"), NX, NZ, tempW, lo, hi, MapWriter.THERMAL);
            MapWriter.writePng(new File(MAPDIR, "p258_" + tag + "_p_summer.png"), NX, NZ, presS, -800, 800, MapWriter.DIVERGING);
            MapWriter.writePng(new File(MAPDIR, "p258_" + tag + "_land.png"), NX, NZ, mask, 0, 1, MapWriter.THERMAL);
            if (ci == 0) {
                MapWriter.writeTsv(new File(MAPDIR, "p258_temp_summer.tsv"), NX, NZ, tempS, X0, Z0, STEP, "T_sfc summer solstice (K)");
                MapWriter.writeTsv(new File(MAPDIR, "p258_land.tsv"), NX, NZ, mask, X0, Z0, STEP, "land mask 1=land");
            }
            // ⚠ 2026-09-18：原来是 `%9.0f` + 裸 ms 值 —— **日志里出现无单位的数字**，
            //   验收 diff 既不能把它当读数（它每趟都抖），也无法自动识别成计时
            //   ⇒ 变成永久假阳性。现在补上单位 `us`（ms 是 long，值仍是同一毫秒数），
            //   归一化规则 \d+\s*us 就能正确把它归零，而前三列读数照旧参与比较。
            say(String.format(LF, "   %-10d %-16s %10.1f %10.1f %10.1f %7.0f us", cell, "T_summer(K)", min(tempS), max(tempS), mean(tempS), ms * 1000.0));
            say(String.format(LF, "   %-10d %-16s %10.1f %10.1f %10.1f %9s", cell, "T_winter(K)", min(tempW), max(tempW), mean(tempW), ""));
            say(String.format(LF, "   %-10d %-16s %10.1f %10.1f %10.1f %9s", cell, "p'_summer(Pa)", min(presS), max(presS), mean(presS), ""));
            say(String.format(LF, "   %-10d %-16s %10.1f %10.1f %10.1f %9s", cell, "land 占比%", mean(mask) * 100, mean(mask) * 100, mean(mask) * 100, ""));

            // B1 量化：等温线的纬度梯度 vs 海陆横向对比
            double dTdz = 0, dTdxAu = 0, dTdxAuN = 0;
            int n = 0;
            for (int r = 0; r < NZ - 20; r++) {
                int z = Z0 + r * STEP;
                if (Math.abs(WorldContract.latOf(z)) > Math.toRadians(60)) continue;
                for (int c = 0; c < NX - 20; c += 20) {
                    int i = r * NX + c;
                    dTdz += Math.abs(tempS[i + 20 * NX] - tempS[i]) / (20.0 * STEP) * 100000.0;
                    dTdxAu += Math.abs(tempS[i + 20] - tempS[i]);
                    n++;
                }
            }
            say(String.format(LF, "   %-10d %-16s 纬向梯度 %.2f K/100km（z 方向）；横向 |dT/dx| 均值 %.2f K/5km*20=%.2f K/100km",
                cell, "B1 形态", dTdz / n, dTdxAu / n, dTdxAu / n));
            say("");
        }

        // ---------------- 成本 ----------------
        say("C. 成本（目标 C1：每个 100 km 瓦片 <= 20 s）");
        int W = 100_000 / 2000;
        long t1 = System.nanoTime();
        int reps = 3;
        for (int k = 0; k < reps; k++) {
            for (int r = 0; r < W; r++) {
                for (int c = 0; c < W; c++) {
                    Atmosphere.surfaceTemp(c * 2000, r * 2000, SD, PlateField.PLATE_CELL, th0);
                }
            }
        }
        double perTile = (System.nanoTime() - t1) / 1e6 / reps;
        say(String.format(LF, "   dT 单场：dx=2 km 的 100 km 瓦片（%d 点）= %.0f ms", W * W, perTile));
        say(String.format(LF, "   ⇒ 温度+气压两个场约 %.0f ms，离 20 s 预算有巨大余量", perTile * 2));
        say(String.format(LF, "  GATE_COST_C1=%s", perTile * 2 <= 20000.0 ? "PASS" : "FAIL"));
        say("");
        say("D. 确定性");
        double a = Atmosphere.surfaceTemp(123456, 234567, SD, PlateField.PLATE_CELL, 1.2345);
        double b = Atmosphere.surfaceTemp(123456, 234567, SD, PlateField.PLATE_CELL, 1.2345);
        say(String.format(LF, "   同参数两次调用：%.12f vs %.12f  %s", a, b, a == b ? "逐位相同 OK" : "**不同**"));
        say(String.format(LF, "  GATE_DETERMINISM=%s", a == b ? "PASS" : "FAIL"));
        say(String.format(LF, "   输出目录：%s", MAPDIR.getAbsolutePath()));
        rep.close();
    }

    static double min(double[] v) { double m = v[0]; for (double x : v) if (x < m) m = x; return m; }
    static double max(double[] v) { double m = v[0]; for (double x : v) if (x > m) m = x; return m; }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }

    static void say(String s) { System.out.println("[P258] " + s); rep.println("[P258] " + s); }
}
