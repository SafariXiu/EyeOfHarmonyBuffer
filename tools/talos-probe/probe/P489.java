package probe;

import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P489：**生产级的 A/B 验证** —— 把 P488 在「行求解器」那一层量到的结论，
 * 提到 {@code OceanField.anomalyAt}（= 生产真正吐出来的 {@code SST'}）这一层复验。
 *
 * <p>P488 比的是 {@code GyreRow.Row} 的 psiMax / vPeak（力项的扰动）；
 * 本探针比的是**最终产物 SST'**（还要过 SeaSurfaceTemp / CoastalLayer 那两层）。
 *
 * <p>做法：先 {@code CURL_STRIDE = 1}（= 改前口径）解一批点，再设成 10（= 现行）
 * —— {@code configStamp()} 含这个旋钮 ⇒ {@code resetIfStale} 会**自动清空海盆缓存** ⇒ 重解。
 * 于是两套读数来自**同一次进程、同一批点**，只有采样步长不同。
 */
public class P489 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P489] " + s); rep.flush(); System.out.println("[P489] " + s); System.out.flush(); }

    /** 对一批 (x,z) 采 SST'，并计时。 */
    static double[] run(int[][] pts, double[] out) {
        long t0 = System.nanoTime();
        for (int i = 0; i < pts.length; i++) out[i] = OceanField.anomalyAt(pts[i][0], pts[i][1], SEED);
        long dt = System.nanoTime() - t0;
        say(String.format(LF, "     解 %d 个点用了 %.2f s（solveCount 累计 %d，solveNanos %.2f s）",
            pts.length, dt / 1e9, OceanField.solveCount, OceanField.solveNanos / 1e9));
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p489_report.txt"), "UTF-8");
        say("P489：CURL_STRIDE 1（改前） vs 10（现行）—— 比**最终产物 SST'**");
        say("");

        // 病态带（宽海盆）为主 + 几个便宜带的点
        int[][] pts = {
            {24_440_000, 2_400_000}, {25_000_000, 2_400_000}, {26_000_000, 2_400_000},
            {25_500_000, 2_800_000}, {26_000_000, 3_000_000}, {25_000_000, 3_400_000},
            {26_600_000, 3_600_000}, {24_440_000, 4_000_000},
            {24_440_000, 2_200_000}, {24_440_000, 2_600_000},
            {1_000_000, 2_400_000}, {4_000_000, 3_000_000}, {8_000_000, 2_000_000},
            {0, 1_600_000}, {6_000_000, 4_400_000}
        };
        int n = pts.length;
        double[] a1 = new double[n], a10 = new double[n];

        OceanField.install(SEED);
        say("  --- A：CURL_STRIDE = 1（改前口径）---");
        GyreRow.CURL_STRIDE = 1;
        say(String.format(LF, "     configStamp=%016X", OceanField.configStamp()));
        run(pts, a1);
        say("");
        say("  --- B：CURL_STRIDE = 10（现行）---");
        GyreRow.CURL_STRIDE = 10;
        say(String.format(LF, "     configStamp=%016X  ← 与上面必须不同（否则缓存不会失效）", OceanField.configStamp()));
        run(pts, a10);
        say("");

        say("  --- 逐点 SST' 对照 ---");
        say(String.format(LF, "   %9s %9s %14s %14s %12s", "x", "z", "SST'(stride=1)", "SST'(stride=10)", "相对差"));
        double maxAbs = 0, maxRel = 0, sumAbs = 0; int k = 0;
        for (int i = 0; i < n; i++) {
            double d = Math.abs(a1[i] - a10[i]);
            double r = Math.abs(a1[i]) > 1e-12 ? d / Math.abs(a1[i]) : 0.0;
            if (d > maxAbs) maxAbs = d;
            if (r > maxRel) maxRel = r;
            sumAbs += d; k++;
            say(String.format(LF, "   %9d %9d %14.6f %14.6f %11.4f%%", pts[i][0], pts[i][1], a1[i], a10[i], 100 * r));
        }
        say("");
        say(String.format(LF, "  ⇒ 最大绝对差 %.6f K；最大相对差 %.4f%%；平均绝对差 %.6f K", maxAbs, 100 * maxRel, sumAbs / k));
        say("");
        say("  ★ 判读（跑之前写死）：");
        say("    · SST' 的最大绝对差 < 0.15 K ⇒ 与 P488 在求解器层的 1.65% 同量级，落地安全；");
        say("    · 若 > 0.5 K ⇒ 说明那两层（SeaSurfaceTemp/CoastalLayer）放大了扰动，必须重新评估。");
        GyreRow.CURL_STRIDE = 10;
        say("");
        say("⚠ 记账：本探针只测量；生产常量已由本轮裁决设为 10。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
