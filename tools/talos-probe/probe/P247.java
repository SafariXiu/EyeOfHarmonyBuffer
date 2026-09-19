package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.BasinFinder;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P247（M2）：**验证自适应求解域**（{@link BasinFinder}）—— 新架构海洋层的架构核心。
 *
 * <h3>三条要验的事</h3>
 * <ol>
 *   <li><b>普查</b>：随机查询点的 valid / truncated / 域尺度分布；</li>
 *   <li><b>不变性</b>：同一个海区从区内不同点查，包围盒必须**逐位相同**
 *       （否则「按海区缓存」这个设计不成立，玩家走到哪都会重算）；</li>
 *   <li><b>成本</b>：每个海区按 R5 规则算 DT 与步数，估出求解成本分布 ——
 *       这决定自适应域方案到底可不可行（设计冻结 §11.4 的「成本天花板」）。</li>
 * </ol>
 */
public class P247 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRID = 20;                 // 20x20 = 400 个查询点
    static final int GSTEP = 200_000;           // 200 km 间距 -> 4000 km 见方
    static final double AH = 1.9e4, HMIX = 100.0, BETA = 3.24e-10;
    static final double PSEUDO_T = 2.8e6;
    static final double SEC_PER_CELLSTEP = 7.0e-8;
    static final double MW = Math.PI * Math.cbrt(AH / BETA);   // Munk 宽
    static final double DX = MW / 6.0;                          // 分辨率：6 格/M_w

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p247_report.txt"), "UTF-8");
        say("自适应求解域验证（BasinFinder）");
        say(String.format(L, "参数：板块格 %d km  采样 %d km  搜索半径 %d 格 = %d km",
            CELL / 1000, BasinFinder.SAMPLE / 1000, BasinFinder.MAX_R, BasinFinder.MAX_R * BasinFinder.SAMPLE / 1000));
        say(String.format(L, "成本口径：M_w=%.0f km ⇒ DX=%.1f km；DT=safeDt(DX,β,span)；伪时间 %.1e s；%.1e s/(格·步)",
            MW / 1000, DX / 1000, PSEUDO_T, SEC_PER_CELLSTEP));

        int[] xs = new int[GRID], zs = new int[GRID];
        for (int i = 0; i < GRID; i++) { xs[i] = i * GSTEP; zs[i] = i * GSTEP; }

        long t0 = System.nanoTime();
        int nSea = 0, nLand = 0, nTrunc = 0;
        double[] spans = new double[GRID * GRID];
        double[] wruns = new double[GRID * GRID];
        int nw = 0;
        double[] costs = new double[GRID * GRID];
        double[] cells = new double[GRID * GRID];
        int ns = 0, nc = 0;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                BasinFinder.Basin b = BasinFinder.find(xs[ix], zs[iz], SD, CELL);
                if (!b.valid) { nLand++; continue; }
                nSea++;
                if (b.truncated) nTrunc++;
                spans[ns++] = b.spanKm();
                wruns[nw++] = b.westRunKm();
                cells[nc++] = b.seaCells;
                double spanM = b.spanKm() * 1000.0;
                double dt = BasinFinder.safeDt(DX, BETA, spanM);
                double nCell = Math.pow(Math.ceil(spanM / DX) + 4, 2);
                costs[nr(costs)] = nCell * (PSEUDO_T / dt) * SEC_PER_CELLSTEP;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        say("");
        say(String.format(L, "  [1] 普查：%d 个查询点，海格 %d，陆格 %d；用时 %d ms（%.1f ms/次）",
            GRID * GRID, nSea, nLand, ms, ms / (double) (GRID * GRID)));
        say(String.format(L, "      被搜索半径截断 %d / %d = %.1f%%（这些海区超出 1600 km，需要开边界回退）",
            nTrunc, nSea, 100.0 * nTrunc / Math.max(1, nSea)));
        stat("域尺度 span (km)", spans, ns, 1.0);
        stat("域内海格数 (粗)", cells, nc, 1.0);
        stat("西墙连续长度 westRun (km)", wruns, nw, 1.0);
        stat("求解成本估算 (s)", costs, nr(costs), 1.0);

        // [2] 不变性：从区内另一格重查，包围盒必须逐位相同
        say("");
        int trials = 0, same = 0, skipped = 0;
        for (int iz = 0; iz < GRID; iz++) {
            for (int ix = 0; ix < GRID; ix++) {
                BasinFinder.Basin b1 = BasinFinder.find(xs[ix], zs[iz], SD, CELL);
                if (!b1.valid || b1.truncated) { skipped++; continue; }
                // 区内另一个点：包围盒中心
                int mx = (b1.minX + b1.maxX) / 2, mz = (b1.minZ + b1.maxZ) / 2;
                BasinFinder.Basin b2 = BasinFinder.find(mx, mz, SD, CELL);
                if (!b2.valid) { skipped++; continue; }   // 包围盒中心可能落在陆上：跳过而不是判失败
                trials++;
                if (b2.minX == b1.minX && b2.maxX == b1.maxX && b2.minZ == b1.minZ && b2.maxZ == b1.maxZ) same++;
                else say(String.format(L, "      ✗ 不一致：查询(%d,%d) vs 中心(%d,%d)：%s | %s", xs[ix], zs[iz], mx, mz, b1, b2));
            }
        }
        say(String.format(L, "  [2] 不变性（非截断域，从包围盒中心重查）：有效重查 %d 次，逐位相同 %d 次，不一致 %d 次（跳过截断/陆格 %d）  %s",
            trials, same, trials - same, skipped, same == trials ? "PASS" : "FAIL"));
        rep.close();
    }

    static int nr(double[] a) { int k = 0; for (double v : a) if (v != 0) k++; return k; }

    static void say(String s) { System.out.println("[P247] " + s); rep.println("[P247] " + s); }

    static void stat(String name, double[] a, int n, double scale) {
        if (n == 0) { say("      " + name + "：空"); return; }
        double[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        say(String.format(L, "      %-18s 中位 %10.1f   P90 %10.1f   最大 %10.1f",
            name, c[n / 2] * scale, c[(int) (n * 0.9)] * scale, c[n - 1] * scale));
    }
}
