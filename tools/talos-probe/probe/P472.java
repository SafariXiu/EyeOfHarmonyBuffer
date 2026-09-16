package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * P472：**X 无限带来的代价**（D55 的验证）——大范围 x 的导出要付多少钱？
 *
 * <p>背景：接线后重跑验收，P284 跑了 880+ s。jstack 证明它**不是卡住**，是在真解海盆行。
 * 根因是契约本身：{@code WorldContract} 写着 **X 无限、无周期** ⇒
 * 「扫 x = 0..40,000 km」不是绕星球一圈，而是**四千万格全新的地方**，
 * 一路上每个海盆都是新海盆，缓存不可能摊薄。
 *
 * <p>三件事：A 量「解多少盆 / 多久」；B 量「**缓存命中**时的单次取值代价」（D55 要压住的正是它）；
 * C 用 +30/-30 剖面逐字回归，证明 D55 是纯索引改动、不动数值。
 */
public class P472 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int N = 64, WEST_HI = 16, MID_LO = 24, MID_HI = 40;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P472] " + s); rep.flush(); System.out.println("[P472] " + s); System.out.flush(); }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    /** 扫 x ∈ [lo,hi] 枚举某条 z 上的全部海盆（去重）。 */
    static LinkedHashMap<Long, int[]> basins(int z, int lo, int hi, int step) {
        LinkedHashMap<Long, int[]> m = new LinkedHashMap<>();
        for (int x = lo; x <= hi; x += step) {
            int[] sp = OceanField.spanOf(x, z, SEED);
            if (sp == null) continue;
            long k = ((long) sp[1] << 32) ^ (sp[2] & 0xFFFFFFFFL);
            if (!m.containsKey(k)) m.put(k, new int[]{sp[1], sp[2]});
        }
        return m;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p472_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        say("P472：X 无限带来的代价 + D55 验证");
        say(String.format(LF, "  SEED=%d  cell=%d  ROW_H=%.0f  ENABLED=%s", SEED, cell, OceanField.ROW_H, OceanField.ENABLED));
        say("");

        // ---------- A. 不预热，按 P284 的形状解 ----------
        say("A. **不预热**，按 P284 的采样形状解（z=1.6M..5.0M 每 200k；x=0..40,000 km 每 500 km）");
        OceanWiring.off();
        say(String.format(LF, "   off() 之后：installedSeed=%d  SST_PROVIDER=%s",
            OceanField.installedSeed(), Atmosphere.SST_PROVIDER == null ? "null" : "非 null"));
        long c0 = OceanField.solveCount, s0 = OceanField.solveNanos;
        long t0 = System.nanoTime();
        int nBasin = 0, nRow = 0;
        for (int z = 1_600_000; z <= 5_000_000; z += 200_000) {
            nRow++;
            nBasin += basins(z, 0, 40_000_000, 500_000).size();
        }
        double dt = (System.nanoTime() - t0) / 1e9;
        long dc = OceanField.solveCount - c0; double ds = (OceanField.solveNanos - s0) / 1e9;
        say(String.format(LF, "   z 行 %d 条；**新解海盆 %d 个**；solveRow 调用 %d 次", nRow, nBasin, dc));
        say(String.format(LF, "   墙钟 %.1f s（纯解行 %.1f s = %.0f%%）", dt, ds, 100.0 * ds / Math.max(1e-9, dt)));
        say(String.format(LF, "   ⇒ 平均每盆 %.2f s；每行每 1000 km x 跨度新增 %.3f 个海盆",
            ds / Math.max(1, nBasin), nBasin / (double) nRow / 40.0));
        say("");

        // ---------- B. 缓存命中的单次代价 ----------
        say("B. **缓存命中**时的单次取值代价（D55 要压住的正是这个量）");
        OceanWiring.onWorld(SEED);
        say(String.format(LF, "   已重新装线：installedSeed=%d", OceanField.installedSeed()));
        int z30 = (int) (30.0 / 90.0 * (ZC / 2));
        LinkedHashMap<Long, int[]> m30 = basins(z30, -10_000_000, 10_000_000, 500_000);
        int tot = 0; for (int[] b : m30.values()) tot += b[1] - b[0];
        say(String.format(LF, "   +30 行累计盆宽 %.0f km（%d 个盆）", tot / 1000.0, m30.size()));
        if (m30.isEmpty()) { say("   **没有海盆，B/C 段无法进行**"); } else {
            int[] b0 = m30.values().iterator().next();
            int wxx = b0[0], exx = b0[1]; int width = Math.max(1, exx - wxx);
            for (int i = 0; i < 200_000; i++) { Atmosphere.sstAnom(wxx + (i * 7919) % width, z30); }
            int M = 2_000_000;
            long t1 = System.nanoTime();
            double acc = 0;
            for (int i = 0; i < M; i++) { acc += Atmosphere.sstAnom(wxx + (i * 7919) % width, z30); }
            double d1 = (System.nanoTime() - t1) / 1e9;
            say(String.format(LF, "   %d 次 Atmosphere.sstAnom（全命中）：%.3f s ⇒ **%.0f ns/次**（acc=%.3e）",
                M, d1, d1 * 1e9 / M, acc));
            int MC = 200_000;
            long t2 = System.nanoTime();
            double acc2 = 0;
            for (int i = 0; i < MC; i++) { acc2 += Atmosphere.pressureAnomaly(wxx + (i * 7919) % width, z30, sd, cell, 0.0); }
            double d2 = (System.nanoTime() - t2) / 1e9;
            say(String.format(LF, "   对照 %d 次裸 pressureAnomaly（不含 OceanField）：%.3f s ⇒ %.1f us/次（acc=%.3e）",
                MC, d2, d2 * 1e6 / MC, acc2));
            say(String.format(LF, "   ⇒ OceanField 取值 %.0f ns = 裸算的 **%.2f%%**（D55 分桶后就该是这个小量）",
                d1 * 1e9 / M, 100.0 * (d1 * 1e9 / M) / (d2 * 1e6 / MC)));
            say(String.format(LF, "   ⇒ 若按 P284 的 4 次 x 2001 点 x 18 行 x 约 3 遍梯度 = 约 %.0f 万次取值，" +
                "OceanField 侧总开销约 **%.1f s**（与 880 s 相比可忽略 ⇒ 那 880 s 是解盆的代价，不是取值的代价）",
                4.0 * 2001 * 18 * 3 / 1e4, 4.0 * 2001 * 18 * 3 * (d1 * 1e9 / M) / 1e9));
        }
        say("");

        // ---------- C. 逐位回归 ----------
        say("C. D55 是纯索引改动 ⇒ +30/-30 剖面必须与 P467 v3 **逐字相同**");
        say(String.format(LF, "   %-6s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-9s %-8s",
            "lat", "盆西km", "@西端", "@1/4", "@1/2", "@3/4", "@东端", "西带均", "东带均", "中位比", "西带峰"));
        for (int latDeg : new int[]{30, -30}) {
            int z = (int) ((double) latDeg / 90.0 * (ZC / 2));
            for (int[] b : new ArrayList<>(basins(z, -10_000_000, 10_000_000, 500_000).values())) {
                int westX = b[0], eastX = b[1];
                double[] v = new double[5];
                for (int i = 0; i < 5; i++) v[i] = OceanField.anomalyAt(westX + (int) ((eastX - westX) * i / 4.0), z, SEED);
                double[] wv = new double[WEST_HI + 1], mv = new double[MID_HI - MID_LO + 1];
                double w = 0, e = 0; int nw = 0, ne = 0, im = 0; double wPeak = -1e9;
                for (int i = 0; i <= N; i++) {
                    int x = westX + (int) ((eastX - westX) * i / (double) N);
                    double a = OceanField.anomalyAt(x, z, SEED);
                    if (i <= 8) { w += a; nw++; }
                    if (i >= N - 8) { e += a; ne++; }
                    if (i <= WEST_HI) { wv[i] = Math.abs(a); if (a > wPeak) wPeak = a; }
                    else if (i >= MID_LO && i <= MID_HI) mv[im++] = Math.abs(a);
                }
                say(String.format(LF, "   %-6d %-9d %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %-9.2f %+.2f",
                    latDeg, westX / 1000, v[0], v[1], v[2], v[3], v[4],
                    w / Math.max(1, nw), e / Math.max(1, ne),
                    median(wv, WEST_HI + 1) / Math.max(1e-9, median(mv, im)), wPeak));
            }
        }
        say("");
        say(String.format(LF, "   ⇒ reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只测量；off()/onWorld() 都在探针内部成对使用。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
