package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.CoastalLayer;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P488：**A/B** —— 把「沿整条海盆每 5 公里求一次风」（生产口径）与
 * 「每 K 格求一次 + 线性插值」放在**同一批海盆**上比。
 *
 * <p>为什么：P487 直接量到 P284 的主项是
 * {@code GyreRow.solve} 第 166 行逐点求风，单价 ~360 µs/格，一个 13,510 km 的盆要 2,703 次 ⇒ 994 ms。
 * 而风场在几百公里尺度上才变化 ⇒ 5 公里采样过采样约 20~50 倍。
 *
 * <p>本探针只做**度量**：把 curl 数组换掉、用**同一个** {@code GyreRow.solveSpan} 求解，比较
 * {@code psiMax / vPeak / westBandMean / eastBandMean} 与整条 {@code v[]} 剖面的相对差。
 * **不改任何生产代码。**
 *
 * <p>口径：与生产 {@code OceanField.solveRow} 一致地 {@code suppressSst(true)}（求解期间不读 SST'），
 * 并用同一套 {@code Params}（h = ROW_H、rhoH = RHO*H_TOTAL、aH = A_H、beta 由该行纬度推导）。
 */
public class P488 {

    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P488] " + s); rep.flush(); System.out.println("[P488] " + s); System.out.flush(); }

    /** 逐字抄自 {@code OceanField.curlAtmos}（4 相位平均）。 */
    static double curlAt(int x, int z, long seed, int cell) {
        double c = 0;
        for (double th : OceanField.PH4) {
            double[] e = Atmosphere.windStress(x + OceanField.GRAD, z, seed, cell, th, OceanField.GRAD);
            double[] w = Atmosphere.windStress(x - OceanField.GRAD, z, seed, cell, th, OceanField.GRAD);
            double[] n = Atmosphere.windStress(x, z + OceanField.GRAD, seed, cell, th, OceanField.GRAD);
            double[] s = Atmosphere.windStress(x, z - OceanField.GRAD, seed, cell, th, OceanField.GRAD);
            c += ((e[1] - w[1]) - (n[0] - s[0])) / (2.0 * OceanField.GRAD) / OceanField.PH4.length;
        }
        return c;
    }

    static GyreRow.Params params() {
        GyreRow.Params p = new GyreRow.Params();
        p.h = OceanField.ROW_H; p.rhoH = CoastalLayer.RHO * OceanField.H_TOTAL; p.aH = OceanField.A_H;
        p.zCycle = WorldContract.Z_CYCLE;
        return p;
    }

    /** B：每 k 格求一次 curl，中间线性插值。返回调用次数（放在 c[1]）。 */
    static double[] buildCoarse(int xw, int z, long seed, int cell, int n, double h, int k, int[] calls) {
        double[] c = new double[n];
        int last = 0;
        for (int i = 0; i < n; i += k) { c[i] = curlAt(xw + (int) (i * h), z, seed, cell); last = i; calls[0]++; }
        if (last != n - 1) { c[n - 1] = curlAt(xw + (int) ((n - 1) * h), z, seed, cell); calls[0]++; last = n - 1; }
        for (int i = 0; i < n; i++) {
            int i0 = (i / k) * k; if (i0 > n - 1) i0 = n - 1;
            int i1 = Math.min(n - 1, i0 + k);
            if (i1 == i0) { c[i] = c[i0]; continue; }
            double t = (i - i0) / (double) (i1 - i0);
            c[i] = c[i0] * (1 - t) + c[i1] * t;
        }
        return c;
    }

    static double relDiff(double a, double b) { double d = Math.abs(a - b); double s = Math.max(Math.abs(a), Math.abs(b)); return s > 1e-30 ? d / s : 0.0; }

    static double[] profileDiff(double[] a, double[] b) {
        double maxAbs = 0, maxRef = 0, sum2 = 0, ref2 = 0;
        for (int i = 0; i < a.length; i++) {
            double d = Math.abs(a[i] - b[i]);
            if (d > maxAbs) maxAbs = d;
            if (Math.abs(a[i]) > maxRef) maxRef = Math.abs(a[i]);
            sum2 += d * d; ref2 += a[i] * a[i];
        }
        return new double[]{ maxAbs, maxRef > 0 ? maxAbs / maxRef : 0.0,
                             ref2 > 0 ? Math.sqrt(sum2 / ref2) : 0.0 };
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p488_report.txt"), "UTF-8");
        say("P488：A/B —— 逐点求风 vs 每 K 格求风+线性插值（同一个 solveSpan，只换力项）");
        say(String.format(LF, "  ROW_H=%.0f m  GRAD=%.0f km  A_H=%.3g  H_TOTAL=%.0f  PH4=%d 相位",
            OceanField.ROW_H, OceanField.GRAD / 1000.0, OceanField.A_H, OceanField.H_TOTAL, OceanField.PH4.length));
        final int[] KS = {5, 10, 20, 40};       // 25 / 50 / 100 / 200 km
        say(String.format(LF, "  K 取值 %s ⇒ 采样间距 %s km", java.util.Arrays.toString(KS),
            java.util.Arrays.toString(new int[]{25, 50, 100, 200})));
        say("");
        long seedL = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        Atmosphere.suppressSst(true);                 // 与生产 solveRow 内一致
        Atmosphere.beginMemo();
        say("  suppressSst(true) + beginMemo()（与生产 OceanField.solveRow 内同一口径）");
        say("");

        int[][] pts = {
            {24_440_000, 2_400_000}, {25_000_000, 2_400_000}, {26_000_000, 2_400_000},
            {25_500_000, 2_800_000}, {26_000_000, 3_000_000}, {25_000_000, 3_400_000},
            {26_600_000, 3_600_000}, {24_440_000, 4_000_000},
            {1_000_000, 2_400_000}, {4_000_000, 2_400_000}
        };
        say(String.format(LF, "  %9s %9s %8s %6s %10s %10s %9s %9s", "x", "z", "盆宽km", "n", "A:psiMax", "A:vPeak", "A:westBand", "A:eastBand"));
        int nBasin = 0;
        double[][] worst = new double[KS.length][3];
        int[] worstAt = new int[KS.length];
        for (int[] pt : pts) {
            int x = pt[0], z = pt[1];
            if (PlateField.isLandWithCell(x, z, seedL, cell)) { say(String.format(LF, "  %9d %9d    （陆地，跳过）", x, z)); continue; }
            GyreRow.Params p = params();
            if (!p.hasBeta()) p = p.withBeta(p.betaAt(z));
            int h = (int) p.h;
            int xw = x; while (xw > -p.maxRow && !PlateField.isLandWithCell(xw - h, z, seedL, cell)) xw -= h;
            int xe = x; while (xe < p.maxRow && !PlateField.isLandWithCell(xe + h, z, seedL, cell)) xe += h;
            int n = (xe - xw) / h + 1;
            if (n < 16) { say(String.format(LF, "  %9d %9d    （n=%d < 16，无效）", x, z, n)); continue; }
            nBasin++;

            int[] callsA = {0};
            long t0 = System.nanoTime();
            double[] cA = new double[n];
            for (int i = 0; i < n; i++) { cA[i] = curlAt(xw + (int) (i * h), z, seedL, cell); callsA[0]++; }
            long tA = System.nanoTime() - t0;
            GyreRow.Row rA = GyreRow.solveSpan(xw, xe, cA, p);
            double widthKm = (xe - xw) / 1000.0;
            say(String.format(LF, "  %9d %9d %8.1f %6d %10.4g %10.4g %9.4g %9.4g   [A: %d 次求风, %.0f ms]",
                x, z, widthKm, n, rA.psiMax, rA.vPeak, rA.westBandMean, rA.eastBandMean, callsA[0], tA / 1e6));

            for (int ki = 0; ki < KS.length; ki++) {
                int K = KS[ki];
                int[] callsB = {0};
                t0 = System.nanoTime();
                double[] cB = buildCoarse(xw, z, seedL, cell, n, h, K, callsB);
                long tB = System.nanoTime() - t0;
                GyreRow.Row rB = GyreRow.solveSpan(xw, xe, cB, p);
                double[] pd = profileDiff(rA.v, rB.v);
                double dPsi = relDiff(rA.psiMax, rB.psiMax);
                double dV = relDiff(rA.vPeak, rB.vPeak);
                double dW = relDiff(rA.westBandMean, rB.westBandMean);
                double dE = relDiff(rA.eastBandMean, rB.eastBandMean);
                if (pd[1] > worst[ki][1]) { worst[ki][1] = pd[1]; worstAt[ki] = x; }
                if (dV > worst[ki][0]) worst[ki][0] = dV;
                if (dPsi > worst[ki][2]) worst[ki][2] = dPsi;
                say(String.format(LF, "      K=%-3d(%3d km) %4d 次求风 %7.1f ms (%.1fx)  dpsiMax %7.3f%%  dvPeak %7.3f%%  dWest %7.3f%%  dEast %7.3f%%  |dv|max/|v|max %7.3f%%  rms %7.3f%%",
                    K, K * (int) h / 1000, callsB[0], tB / 1e6, (double) tA / Math.max(1, tB),
                    100 * dPsi, 100 * dV, 100 * dW, 100 * dE, 100 * pd[1], 100 * pd[2]));
            }
            say("");
        }
        Atmosphere.suppressSst(false);
        say(String.format(LF, "共剖 %d 个有效海盆", nBasin));
        say("");
        say("★★★ 汇总（每个 K 的最坏值）★★★");
        say(String.format(LF, "  %-14s %12s %12s %12s", "K", "最坏 dvPeak", "最坏 dpsiMax", "对应 x"));
        for (int ki = 0; ki < KS.length; ki++) {
            say(String.format(LF, "  K=%-3d(%3d km) %11.3f%% %11.3f%% %12d",
                KS[ki], KS[ki] * 5000 / 1000, 100 * worst[ki][0], 100 * worst[ki][2], worstAt[ki]));
        }
        say("");
        say("判读：A2 的判据是输运落在 [0.5,1.0]x（余量很大），A7 是比值 >= 4（实测 35，余量更大）。");
        say("      ⇒ 若最坏 dvPeak 在个位数百分比以内，则 K=10(50 km) 有资格进入下一轮裁决。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
