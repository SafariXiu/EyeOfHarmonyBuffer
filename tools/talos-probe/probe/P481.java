package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * P481：**D29 的量化台阶到底有多大影响**（用户裁决 3-C：「先把台阶量出来再决定」）。
 *
 * <p>D29：`landScore` 的 `RING_W` 全为 1.0 ⇒ `wsum ≡ 193` ⇒ kappa 只有 **194 个离散值**
 * （级距 2/193 = 0.010363）。而 `Atmosphere.pressureAnomaly` 正是靠 **kappa 的差分**
 * 造跨岸气压梯度（`(1-k)*sstAnom`、`seasonalAnomaly(lat,k,theta)`、`cellPressure(lat,k,theta)`）
 * ⇒ 台阶会变成 p' 的台阶，再变成风的台阶。
 *
 * <p><b>本探针只测量，不改任何东西</b>，回答三个问题：
 * <ol>
 *   <li>跨一道岸，实际走过多少个 kappa 层级？（= 台阶的分辨率够不够）</li>
 *   <li>单个台阶造成的 p' 跳变有多大？（与跨岸总变化比）</li>
 *   <li>台阶会不会在**风**上留下可见的锯齿？（风是 p' 的导数，锯齿会被放大）</li>
 * </ol>
 */
public class P481 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P481] " + s); rep.flush(); System.out.println("[P481] " + s); System.out.flush(); }

    static double median(double[] v, int n) {
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p481_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        long sd = SimTerrain.seedOf(SEED);
        double th = Atmosphere.theta(0.0);
        say("P481：D29 的量化台阶影响（只测量，不改代码）");
        say(String.format(LF, "  SEED=%d  cell=%d  COAST_BLEND(半径)=%d  PLATE_CELL=%d",
            SEED, cell, PlateField.COAST_BLEND, cell));
        say(String.format(LF, "  理论级距 = 2/193 = %.6f（kappa 只有 194 个离散值）", 2.0/193.0));
        say("");

        // 找若干条跨越海岸的 x 扫描线
        int[] zs = {(int)(35.0/90.0*(ZC/2)), (int)(-35.0/90.0*(ZC/2)), (int)(55.0/90.0*(ZC/2))};
        for (int z : zs) {
            double lat = WorldContract.latOf(z, ZC) * 180.0 / Math.PI;
            // 找一条 |dkappa/dx| 大的位置：粗扫
            double bestX = 0, bestG = 0;
            for (int x = -8_000_000; x <= 8_000_000; x += 100_000) {
                double a = Atmosphere.kappaAt(x - 50_000, z, sd, cell);
                double b = Atmosphere.kappaAt(x + 50_000, z, sd, cell);
                double g = Math.abs(b - a);
                if (g > bestG) { bestG = g; bestX = x; }
            }
            int x0 = (int) bestX - 1_000_000, x1 = (int) bestX + 1_000_000, STEP = 5_000;
            int m = (x1 - x0) / STEP + 1;
            double[] kp = new double[m], pr = new double[m], wx = new double[m];
            LinkedHashSet<Long> levels = new LinkedHashSet<>();
            for (int i = 0; i < m; i++) {
                int x = x0 + i * STEP;
                kp[i] = Atmosphere.kappaAt(x, z, sd, cell);
                pr[i] = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
                wx[i] = Atmosphere.windStress(x, z, sd, cell, th, 500_000)[0];
                levels.add(Math.round(kp[i] * 193.0));
            }
            // 统计单步跳变
            double maxDp = 0, totDp = 0, maxDw = 0, totDw = 0;
            double[] steps = new double[m - 1]; int ns = 0;
            for (int i = 1; i < m; i++) {
                double dp = Math.abs(pr[i] - pr[i - 1]);
                double dw = Math.abs(wx[i] - wx[i - 1]);
                steps[ns++] = dp;
                if (dp > maxDp) maxDp = dp;
                if (dw > maxDw) maxDw = dw;
                totDp += dp; totDw += dw;
            }
            double medDp = median(steps, ns);
            say(String.format(LF, "lat %+.0f  z=%d  扫描 x=[%d, %d] km，步长 %d km",
                lat, z, x0 / 1000, x1 / 1000, STEP / 1000));
            say(String.format(LF, "  kappa 走过的层级数 = %d（这条线上 kappa 从 %.4f 到 %.4f）",
                levels.size(), minOf(kp, m), maxOf(kp, m)));
            say(String.format(LF, "  p' 单步 |Delta| ：中位 %.4f Pa，最大 %.4f Pa；跨岸总变化 %.4f Pa",
                medDp, maxDp, totDp));
            say(String.format(LF, "      最大单步 / 总变化 = %.4f%%", 100.0 * maxDp / Math.max(1e-9, totDp)));
            say(String.format(LF, "  风应力单步 |Delta|：最大 %.5f Pa；跨岸总变化 %.5f Pa", maxDw, totDw));
            say("");
        }
        say("  判读规则（跑之前写死）：");
        say("   ① 若「最大单步 / 总变化」< 1%，台阶在 p' 上可忽略；");
        say("   ② 若 1%~5%，台阶是噪声级但可见；");
        say("   ③ 若 > 5%，台阶会污染风的导数，D29 需要真修。");
        say("");
        say("⚠ 记账：本探针只测量（kappaAt / pressureAnomaly / windStress 都是纯函数），未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static double minOf(double[] v, int n) { double m = Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.min(m, v[i]); return n == 0 ? Double.NaN : m; }
    static double maxOf(double[] v, int n) { double m = -Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.max(m, v[i]); return n == 0 ? Double.NaN : m; }
}
