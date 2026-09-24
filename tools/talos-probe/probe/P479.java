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
 * P479：**A7（西边界强化比 西/东 >= 4）在生产口径下的第一个读数**。
 *
 * <p>为什么必须新写一支：旧探针 **P257** 是自己重新调 `GyreRow.solve` 并把
 * `GyreRow.BandedWind`（**合成带风**，tau0=0.0685）喂进去的 ——
 * 而那个驱动源**已经被 P466 否决**（带风 30N 的 vPeak = 0.0000，45N 符号相反）。
 * ⇒ **P257 的 A7 = 6/6 不是生产口径**，不能进验收表。
 *
 * <p>本探针只读 {@link OceanField#bandMeansAt} —— 那是**生产解自己**存下来的
 * 西带/东带深度平均速度（带宽 {@link OceanField#BAND_W} = 100 km，与 §23.2 的真实参照同口径）。
 * **不重解、不复制公式。**
 *
 * <p>判据（跑之前写死）：**副热带（|lat| 15~45）海盆的 |西/东| 中位数 >= 4，且通过率 >= 50%**。
 */
public class P479 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int SCAN = 500_000;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P479] " + s); rep.flush(); System.out.println("[P479] " + s); System.out.flush(); }

    static double minOf(double[] v, int n) { double m = Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.min(m, v[i]); return n == 0 ? Double.NaN : m; }
    static double maxOf(double[] v, int n) { double m = -Double.MAX_VALUE; for (int i = 0; i < n; i++) m = Math.max(m, v[i]); return n == 0 ? Double.NaN : m; }

    static double median(double[] v, int n) {
        if (n <= 0) return Double.NaN;
        double[] a = Arrays.copyOf(v, n); Arrays.sort(a);
        return (n % 2 == 1) ? a[n / 2] : 0.5 * (a[n / 2 - 1] + a[n / 2]);
    }

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
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p479_report.txt"), "UTF-8");
        OceanWiring.onWorld(SEED);
        say("P479：A7 西/东强化比（生产口径：模型自己的风驱动）");
        say(String.format(LF, "  接线：installedSeed=%d  BAND_W=%.0f km  ROW_H=%.0f m",
            OceanField.installedSeed(), OceanField.BAND_W / 1000, OceanField.ROW_H));
        say("  口径：西带/东带 = 该海盆两端各 100 km 内的**算术平均深度平均速度**（生产解本身）");
        say("  判据（事先写死）：副热带（|lat| 15~45）海盆的 |西/东| 中位数 >= 4，且通过率 >= 50%");
        say("");
        int[] lats = {15, 25, 35, 45, 55, -15, -25, -35, -45};
        say(String.format(LF, "  %-6s %-9s %-8s %-11s %-11s %-9s %-8s %-9s",
            "lat", "盆西km", "宽km", "西带mm/s", "东带mm/s", "|比|", ">=4?", "内区T'"));
        double[] rSub = new double[128]; int[] latSub = new int[128]; int ns = 0;
        double[] wTrop = new double[128], rTrop = new double[128]; int nTrop = 0;
        double[] wAll = new double[128], eAll = new double[128]; int nAll = 0;
        int nPassAll = 0, nTotAll = 0;
        long t0 = System.nanoTime();
        for (int latDeg : lats) {
            int z = WorldContract.zOfLat(latDeg);   // ★ §727 两倍纬度修正
            for (int[] b : basinsAt(z)) {
                int wx = b[0], ex = b[1];
                if (ex - wx < 200_000) continue;
                double[] bm = OceanField.bandMeansAt((wx + ex) / 2, z, SEED);
                if (bm == null) continue;
                double wmm = bm[0] * 1000, emm = bm[1] * 1000;   // m/s -> mm/s
                double ratio = Math.abs(emm) > 1e-12 ? Math.abs(wmm / emm) : Double.NaN;
                boolean pass = ratio >= 4.0;
                nTotAll++; if (pass) nPassAll++;
                say(String.format(LF, "  %-6d %-9d %-8d %-11.2f %-11.2f %-9.2f %-8s %-9.3f",
                    latDeg, wx / 1000, (ex - wx) / 1000, wmm, emm, ratio, pass ? "是" : "否",
                    OceanField.anomalyAt((wx + ex) / 2, z, SEED)));
                int a = Math.abs(latDeg);
                if (a >= 15 && a <= 45 && ns < 128) { rSub[ns] = ratio; latSub[ns] = latDeg; ns++; }
                // D63：再加一个**副热带proper**子集（|lat| 15~35），与 A3 的 D53 判据域同口径，
                // 因为 A2 的锚（湾流 30 Sv / 100 km x 4000 m = 75 mm/s）本身就是**副热带**的。
                if (a >= 15 && a <= 35 && nTrop < 128) { wTrop[nTrop] = Math.abs(wmm); rTrop[nTrop] = ratio; nTrop++; }
                if (nAll < 128) { wAll[nAll] = Math.abs(wmm); eAll[nAll] = Math.abs(emm); nAll++; }
            }
        }
        say("");
        say(String.format(LF, "  ⇒ %d 个海盆，耗时 %.1f s（BAND_W=%.0f km 口径）",
            nTotAll, (System.nanoTime() - t0) / 1e9, OceanField.BAND_W / 1000));
        int nSubPass = 0; for (int i = 0; i < ns; i++) if (rSub[i] >= 4.0) nSubPass++;
        say(String.format(LF, "  副热带子集（|lat| 15~45）：%d 个海盆", ns));
        say(String.format(LF, "     西带 |v| 中位 %.2f mm/s   东带 |v| 中位 %.2f mm/s", median(wAll, nAll), median(eAll, nAll)));
        say(String.format(LF, "     **|西/东| 中位 = %.2f**；|比| >= 4 的盆 %d / %d = %.0f%%",
            median(rSub, ns), nSubPass, ns, 100.0 * nSubPass / Math.max(1, ns)));
        int nTropPass = 0; for (int i = 0; i < nTrop; i++) if (rTrop[i] >= 4.0) nTropPass++;
        say(String.format(LF, "  **副热带 proper（|lat| 15~35，= A3/D53 的判据域）：%d 个海盆**", nTrop));
        say(String.format(LF, "     西带 |v| **中位 %.2f mm/s**（范围 %.2f ~ %.2f）  ⇒ A2 的锚是 75 mm/s",
            median(wTrop, nTrop), minOf(wTrop, nTrop), maxOf(wTrop, nTrop)));
        say(String.format(LF, "     |西/东| 中位 %.2f；>= 4 的盆 %d / %d = %.0f%%",
            median(rTrop, nTrop), nTropPass, nTrop, 100.0 * nTropPass / Math.max(1, nTrop)));
        say(String.format(LF, "  全部纬度（含 55 度）：|比| >= 4 的盆 %d / %d = %.0f%%",
            nPassAll, nTotAll, 100.0 * nPassAll / Math.max(1, nTotAll)));
        say("");
        double medR = median(rSub, ns);
        double passRate = 100.0 * nSubPass / Math.max(1, ns);
        say(String.format(LF, "  判据：中位 |比| %.2f >= 4  ⇒ %s", medR, medR >= 4.0 ? "通过" : "**不通过**"));
        say(String.format(LF, "        通过率 %.0f%% >= 50%%  ⇒ %s", passRate, passRate >= 50.0 ? "通过" : "**不通过**"));
        say(String.format(LF, "  GATE_A7_RATIO=%s", medR >= 4.0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  GATE_A7_PASSRATE=%s", passRate >= 50.0 ? "PASS" : "FAIL"));
        say(String.format(LF, "  reentryBlocked = %d（必须 0）", OceanField.reentryBlocked));
        say("⚠ 记账：本探针只读 OceanField 的公开接口（bandMeansAt / spanOf / anomalyAt），未改任何生产常量。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
