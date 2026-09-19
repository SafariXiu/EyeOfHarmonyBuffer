package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.BarotropicGyre;
import com.EyeOfHarmonyBuffer.space.talos.chunk.circulation_layer.GlobalCirculation;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P248：**「解析 Sverdrup 诊断」可行性** —— M2 的第三条路，也是前两条被否决后的候选。
 *
 * <h3>为什么不再做椭圆求解</h3>
 * 前两轮连续否决了两个「求解域」方案：
 * <pre>
 *   连通海区   -> 域 = 整个世界（97.6% 截断），成本 850 s
 *   海岸定界   -> 域中位 2380 km，成本 262 s
 *   规律：cost ∝ L^4（格数 ∝ L^2，步数 ∝ 1/DT ∝ L^2，Rossby CFL）
 * </pre>
 * 而且泊松方程的解依赖**整个定义域**，在无限世界上本来就只能用窗口近似 ——
 * 窗口边缘就是接缝。这是**架构级**的困难，不是调参能解决的。
 *
 * <h3>本探针测什么</h3>
 * <b>解析 Sverdrup 诊断</b>：稳态内区的 Sverdrup 平衡 `βv = curl(τ)/(ρ0·H)` 可以直接积分：
 * <pre>
 *   psi_S(x, z) = -(1/(ρ0·H·β)) · ∫_x^{x_east(z)} curl(τ) dx'
 * </pre>
 * 它是一个**沿纬度行的一维积分**：
 * <ul>
 *   <li><b>没有椭圆求解</b> ⇒ 没有 CFL、没有时间步、没有求解域；</li>
 *   <li><b>天然连续</b>：psi_S 是积分的连续函数，只在水陆交界处换行；</li>
 *   <li><b>O(行长)</b>：一次查询只要沿行扫到东海墙，约百次采样。</li>
 * </ul>
 *
 * <h3>要量的四件事</h3>
 * <ol>
 *   <li><b>成本</b>：每次查询多少 ms、扫多少点（对比椭圆解的 16~850 s）；</li>
 *   <li><b>盆宽与输运</b>：西岸到东岸的宽度、|psi_S(西岸)|（= 西边界流要回流的输运）；</li>
 *   <li><b>西边界流速度</b>：U ≈ |psi_S(西岸)| / δ_M（Munk 层厚）；</li>
 *   <li><b>连续性</b>：沿一条纬线扫过去，psi_S 是否出现跳变。</li>
 * </ol>
 *
 * <h3>风源：解析风带，不是旧的窗口式气候场</h3>
 * 第一版拿旧的窗口式气候场当风源，结果跑了 20 分钟没出结果：
 * 400 个查询点跨 38 个 x 瓦片 x 4 个 z 瓦片，每瓦片建窗约 10 s。
 * 旧的窗口式气候场根本不能当风源 —— 这本身就是一条对 M3 的硬约束。
 *
 * 本版换成解析三带风 tau_x(bandD) = tau0 * sin(2*pi*bandD)，
 * 其 curl = -(2*pi*tau0/MAX_D) * cos(2*pi*bandD)：
 * bandD 约 0 处 curl<0（副热带）、约 0.5 处 curl>0（副极地）—— 教科书双涡旋。
 * tau0 取 0.05 N/m2 则 curl 峰 6.28e-7，与 P246 实测的中纬连贯 curl 同量级。
 * 这样测到的就是诊断本身的成本，不含任何建窗。
 */

public class P248 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int SAMPLE = 20_000;          // 沿行步长
    static final int DH = 10_000;               // 差分步长
    static final int MAXROW = 400;              // 8000 km 上限
    static final double BETA = 3.24e-10;
    static final double RHO_H = 1025.0 * 100.0;
    static final double AH = 1.9e4;
    static final double DM = Math.cbrt(AH / BETA);      // Munk 层厚 38.9 km
    static final int MAX_D = GlobalCirculation.Z_CYCLE / 2;
    static final double TAU0 = 0.05;                    // N/m^2

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p248_report.txt"), "UTF-8");
        say("解析 Sverdrup 诊断可行性（不做椭圆求解）");
        say(String.format(L, "δ_M = %.1f km；SAMPLE = %d km；行扫描上限 %d 点 = %d km",
            DM / 1000, SAMPLE / 1000, MAXROW, MAXROW * SAMPLE / 1000));

        int[] xs = new int[20], zs = new int[20];
        for (int i = 0; i < 20; i++) { xs[i] = i * 200_000; zs[i] = i * 200_000; }

        long t0 = System.nanoTime();
        int nSea = 0, nLand = 0, nRowClip = 0;
        double[] widths = new double[400], psiW = new double[400], uwbc = new double[400], steps = new double[400];
        int nw = 0, np = 0, nu = 0, nst = 0;
        for (int iz = 0; iz < 20; iz++) {
            for (int ix = 0; ix < 20; ix++) {
                int x = xs[ix], z = zs[iz];
                if (PlateField.isLandWithCell(x, z, SD, CELL)) { nLand++; continue; }
                nSea++;
                int[] span = rowSpan(x, z);
                int w = (span[2] - span[0]) / SAMPLE + 1;
                widths[nw++] = (span[1] - span[0]) / 1000.0;
                if (span[3] == 1) nRowClip++;
                int cnt = 0;
                double p = psiSverdrup(span[0], z, cnt);
                psiW[np++] = p;
                uwbc[nu++] = Math.abs(p) / DM;
                steps[nst++] = w;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000L;

        say("");
        say(String.format(L, "  [1] 成本：400 个查询点用 %d ms（%.2f ms/次；含陆格判定+行扫描+积分）",
            ms, ms / 400.0));
        say(String.format(L, "      海格 %d，陆格 %d；行扫描触到上限的次数 %d", nSea, nLand, nRowClip));
        stat("盆宽 (km)", widths, nw);
        stat("沿行扫描点数", steps, nst);
        stat("|psi_S(西岸)| (m^2/s)", psiW, np);
        stat("西边界流估算 |psi|/δ_M (m/s)", uwbc, nu);

        // [4] 连续性：沿一条纬线扫过去
        say("");
        say("  [4] 连续性：z=1200km 行，x 每 20 km 一点，打印 psi_S（m²/s）");
        double prev = Double.NaN;
        int jumps = 0, printed = 0;
        StringBuilder sb = new StringBuilder("      ");
        for (int x = 0; x <= 4_000_000; x += 200_000) {
            if (PlateField.isLandWithCell(x, 1_200_000, SD, CELL)) { sb.append("  [陆]  "); prev = Double.NaN; continue; }
            int cnt = 0;
            double p = psiSverdrup(x, 1_200_000, cnt);
            if (!Double.isNaN(prev) && Math.abs(p - prev) > 5e4) jumps++;
            sb.append(String.format(L, " %7.0f", p));
            prev = p; printed++;
        }
        say(sb.toString());
        say(String.format(L, "      有效点 %d，相邻点差 > 5e4 的次数 %d", printed, jumps));
        rep.close();
    }

    static void say(String s) { System.out.println("[P248] " + s); rep.println("[P248] " + s); }

    static void stat(String name, double[] a, int n) {
        if (n == 0) { say("      " + name + "：空"); return; }
        double[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        say(String.format(L, "      %-28s 中位 %12.4f   P90 %12.4f   最大 %12.4f",
            name, c[n / 2], c[(int) (n * 0.9)], c[n - 1]));
    }

    /** 返回 {西岸x, 东岸x, 西岸索引偏移, 是否触到行扫描上限}。 */
    static int[] rowSpan(int x, int z) {
        int w = x; int guard = 0;
        while (guard++ < MAXROW && !PlateField.isLandWithCell(w - SAMPLE, z, SD, CELL)) w -= SAMPLE;
        int e = x; guard = 0;
        while (guard++ < MAXROW && !PlateField.isLandWithCell(e + SAMPLE, z, SD, CELL)) e += SAMPLE;
        return new int[]{w, e, (x - w) / SAMPLE, guard >= MAXROW ? 1 : 0};
    }

    /** 解析三带风的 curl：与外生给定的 tau_x(bandD) 解析导数一致，O(1)，无建窗。 */
    static double curlAt(int x, int z) {
        double b = GlobalCirculation.bandD(z);
        return -(2.0 * Math.PI * TAU0 / MAX_D) * Math.cos(2.0 * Math.PI * b);
    }

    /** psi_S(x,z) = -(1/(ρ0·H·β)) · ∫_x^{东岸} curl dx'。cnt[0] 回传点数。 */
    static double psiSverdrup(int x, int z, int cnt) {
        double sum = 0; int n = 0, g = 0;
        for (int xx = x; g < MAXROW; xx += SAMPLE, g++) {
            if (PlateField.isLandWithCell(xx, z, SD, CELL)) break;
            sum += curlAt(xx, z) * SAMPLE;
            n++;
        }
        cnt = n;
        return -sum / (RHO_H * BETA);
    }
}
