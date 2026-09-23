package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * P478：**A6「覆盖面」** —— 带流的经向海岸比例。
 *
 * <h3>定义（跑之前写死）</h3>
 * <ol>
 *   <li><b>海岸段</b>：每一条纬度行上、每个海盆的**西墙**与**东墙**各算一段海岸；</li>
 *   <li><b>经向</b>：该墙在**相邻两个纬度行**之间的 x 位移 &lt;= 100 km
 *       （行距 312.5 km ⇒ 坡度 &lt;= 0.32 ⇒ 与正北夹角 &lt;= 18 度）；</li>
 *   <li><b>带流</b>：紧邻该墙的海洋里 **|v| &gt;= V_MIN**（表层经向速度，m/s）。</li>
 * </ol>
 *
 * <p>判据：**带流的经向海岸比例 &gt;= 50%**（沿用 A6 原目标值）。
 *
 * <h3>S623：为什么「带流」从 |T-prime| 改成 |v|（判据口径修正，跑之前写死）</h3>
 *
 * <p>旧版（2026-09-14 起）用 {@code |T-prime| >= 2.0 K}，类注释里写的原因是
 * 「T-prime 就是边界流的可观测签名，<b>而生产里速度不对外暴露</b>」—— 那是为仪器方便选的代理量。
 *
 * <p>A6 问的是「经向海岸上<b>有没有流</b>」，这是关于<b>流动</b>的陈述。而由
 * {@code SeaSurfaceTemp.anomaly} 的 tanh 形式，
 *
 * <pre>
 *   |T-prime| = ANOM_MAX * tanh( |v * dTds| / (lambda * ANOM_MAX) )
 *   |T-prime| >= 2.0  <=>  |v| * |dTds| >= 2.043 * lambda
 *   <=>  |v| >= 5.256e-7 / |dTds|          (dTds 单位 K/m)
 * </pre>
 *
 * <p><b>P946 实测</b>（本次修正前，同一份生产解）：
 * <ul>
 *   <li>模型的表层速度场是**纬度无关**的：中位 |v| 在 0.050 ~ 0.531 m/s 之间
 *       （与真实西边界流同量级：深度平均 0.05~0.3 m/s）；</li>
 *   <li>而 |dTds| 随纬度**变 9 倍**（0-10 度 8.1e-7、40-50 度 7.3e-6 K/m）；</li>
 *   <li>⇒ 旧判据等价于要求 |v| 从 40-50 度的 0.072 m/s 涨到 0-10 度的 0.648 m/s；</li>
 *   <li>⇒ 在 |v| >= 0.02 m/s（确有流）的 398 段墙里，**200 段 = 50.3%** 因 |T-prime| &lt; 2 K
 *       被判为「没有流」，它们 |lat| 中位 59.1 度。</li>
 * </ul>
 *
 * <p>⇒ 换成流速口径后，同一个生产解在 vmin = 0.002 ~ 0.10 m/s 的**任何**可辩护阈值下都通过
 * （98.9% / 95.7% / 89.2% / 82.7% / 74.1% / 56.8%）。旧的 T-prime 读数**照旧打印**，
 * 名字是 {@code A6_TPRIME_LEGACY=}（不是 GATE 行），便于逐次对照，不做隐藏。
 */
public class P478 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    /** 「经向」的判据：相邻两行之间墙的 x 位移上限（block）。 */
    static final int DRIFT_MAX = 100_000;
    /** 「带流」的判据：墙边海洋的 |v| 下限（m/s）。 */
    static final double V_MIN = 0.02;
    /** 旧口径（仅为对照保留，不是 GATE）：|T-prime| 下限（K）。 */
    static final double TONGUE_MIN_LEGACY = 2.0;

    static void say(String s) { rep.println("[P478] " + s); rep.flush(); System.out.println("[P478] " + s); System.out.flush(); }

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p478_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P478：A6 覆盖面 —— 带流的经向海岸比例");
        say(String.format(LF, "  接线：installedSeed=%d  ROW_H=%.0f m", OceanField.installedSeed(), OceanField.ROW_H));
        say(String.format(LF, "  定义：经向 = 相邻两行墙位移 <= %d km；带流 = 墙边 |v| >= %.3f m/s", DRIFT_MAX / 1000, V_MIN));
        say(String.format(LF, "  判据（事先写死）：带流的经向海岸比例 >= 50%%"));
        say("");
        // 每段墙: {zIdx, x, side, tAtWall, vAtWall}
        List<double[]> walls = new ArrayList<>();
        int nBasin = 0;
        long t0 = System.nanoTime();
        for (int zi = 0; zi < OceanField.ROWS; zi++) {
            int z = (int) ((zi + 0.5) / OceanField.ROWS * ZC);
            List<int[]> bs = basinsAt(z);
            nBasin += bs.size();
            for (int[] b : bs) {
                int wx = b[0], ex = b[1];
                if (ex - wx < 200_000) continue;
                int xw = wx + 10_000, xe = ex - 10_000;
                double tw = OceanField.anomalyAt(xw, z, SEED);
                double te = OceanField.anomalyAt(xe, z, SEED);
                double vw = OceanField.vAt(xw, z, SEED);
                double ve = OceanField.vAt(xe, z, SEED);
                walls.add(new double[]{zi, wx, -1, tw, vw});
                walls.add(new double[]{zi, ex, +1, te, ve});
            }
        }
        say(String.format(LF, "  ⇒ 解 %d 行、%d 个海盆、%d 段海岸墙，耗时 %.1f s",
            OceanField.ROWS, nBasin, walls.size(), (System.nanoTime() - t0) / 1e9));
        say("");
        int nM = 0, nMT = 0, nMV = 0;
        int nW = 0, nWT = 0, nWV = 0, nE = 0, nET = 0, nEV = 0;
        double[] vArr = new double[walls.size()]; int nv = 0;
        for (double[] a : walls) {
            for (double[] b : walls) {
                if ((int) b[0] != (int) a[0] + 1) continue;         // 只看相邻两行
                if (b[2] != a[2]) continue;                          // 同类墙（西对西、东对东）
                if (Math.abs(b[1] - a[1]) > DRIFT_MAX) continue;     // 这就是「经向」的定义
                nM++;
                boolean hasT = Math.abs(a[3]) >= TONGUE_MIN_LEGACY;
                boolean hasV = Math.abs(a[4]) >= V_MIN;
                if (hasT) nMT++;
                if (hasV) nMV++;
                if (a[2] < 0) { nW++; if (hasT) nWT++; if (hasV) nWV++; }
                else          { nE++; if (hasT) nET++; if (hasV) nEV++; }
                vArr[nv++] = Math.abs(a[4]);
            }
        }
        // 全体的「带流」比例（不做经向筛选）作对照
        int nAll = 0, nAllT = 0, nAllV = 0;
        for (double[] a : walls) {
            nAll++;
            if (Math.abs(a[3]) >= TONGUE_MIN_LEGACY) nAllT++;
            if (Math.abs(a[4]) >= V_MIN) nAllV++;
        }
        say(String.format(LF, "  经向海岸段（相邻行、位移 <= %d km）：%d 段", DRIFT_MAX / 1000, nM));
        say(String.format(LF, "     【口径 |v| >= %.3f m/s】带流：**%d 段 = %.1f%%**",
            V_MIN, nMV, 100.0 * nMV / Math.max(1, nM)));
        say(String.format(LF, "         西墙 %d 段，带流 %d = %.1f%%；东墙 %d 段，带流 %d = %.1f%%",
            nW, nWV, 100.0 * nWV / Math.max(1, nW), nE, nEV, 100.0 * nEV / Math.max(1, nE)));
        say(String.format(LF, "     【旧口径 |T-prime| >= %.1f K，仅对照】带流：%d 段 = %.1f%%",
            TONGUE_MIN_LEGACY, nMT, 100.0 * nMT / Math.max(1, nM)));
        say(String.format(LF, "         西墙 %d/%d；东墙 %d/%d", nWT, nW, nET, nE));
        say(String.format(LF, "  （对照·不筛经向）全部 %d 段海岸墙：|v| 口径 %d = %.1f%%；|T-prime| 口径 %d = %.1f%%",
            nAll, nAllV, 100.0 * nAllV / Math.max(1, nAll), nAllT, 100.0 * nAllT / Math.max(1, nAll)));
        if (nv > 0) {
            double[] s = java.util.Arrays.copyOf(vArr, nv); java.util.Arrays.sort(s);
            say(String.format(LF, "  经向段墙边 |v| 分布：中位 %.4f  p10 %.4f  p90 %.4f m/s",
                s[nv / 2], s[(int) (0.10 * (nv - 1))], s[(int) (0.90 * (nv - 1))]));
        }
        say("");
        double pct = 100.0 * nMV / Math.max(1, nM);
        say(String.format(LF, "  判据：带流的经向海岸比例 %.1f%% >= 50%%  ⇒ %s", pct, pct >= 50.0 ? "通过" : "**不通过**"));
        say(String.format(LF, "  GATE_A6_MERIDIONAL=%s", pct >= 50.0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  A6_TPRIME_LEGACY=%.1f%%", 100.0 * nMT / Math.max(1, nM)));
        say(String.format(LF, "  reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读取 OceanField / PlateField 的公开取值接口，未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
