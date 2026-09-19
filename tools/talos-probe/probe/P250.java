package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.GyreRow;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P250：验证提升到 sim/ocean 的 {@link GyreRow}（解析基函数 + 4x4，精确 O(1)）。
 *
 * <pre>
 *   [1] 合成验证：与 P249 的稠密高斯解逐项对照（同参数）
 *   [2] 真实板块行：扫一片区域，量盆宽 / 成本 / 西边界流 / 符号（H 用真实深度 4000 m）
 *   [3] 成本统计
 * </pre>
 */
public class P250 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final int ZC = com.EyeOfHarmonyBuffer.sim.world.WorldContract.Z_CYCLE;

    static final Locale L = Locale.ROOT;
    static final File ROOT = new File("K:\\moder\\EyeOfHarmonyBuffer");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build\\eoh_probe\\mtn\\p250_report.txt"), "UTF-8");
        say("GyreRow（解析 1-D Munk）验证");

        // ---- [1] 合成验证：与 P249 同参数 ----
        say("");
        say("  [1] 合成验证 vs P249 稠密解（L=1200 km, beta=1e-11, A_H=400, rho0*H=5.125e6, curl=-1.851e-7）");
        GyreRow.Params p1 = new GyreRow.Params();
        p1.beta = 1.0e-11; p1.aH = 400.0; p1.rhoH = 1025.0 * 5000.0; p1.h = 2500.0;
        int n1 = (int) (1_200_000.0 / p1.h) + 1;
        double[] c1 = new double[n1];
        for (int i = 0; i < n1; i++) c1[i] = -1.851e-07;
        GyreRow.Row r1 = GyreRow.solveSpan(0, (int) ((n1 - 1) * p1.h), c1, p1);
        say(String.format(L, "      psi_max = %.0f   （P249 稠密解 5347；解析 Sverdrup 4334）", Math.abs(r1.psiMax)));
        say(String.format(L, "      |v|峰  = %.2f mm/s   （P249 稠密解 246.0）", Math.abs(r1.vPeak) * 1000));
        say(String.format(L, "      内区 v = %.4f mm/s   （Sverdrup 预测 -3.612）", r1.v[n1 / 2] * 1000));
        say(String.format(L, "      西带|v|均 = %.4f  东带 = %.4f  ⇒ 西/东 = %.2f   （P249 15.01）",
            r1.westBandMean, r1.eastBandMean, r1.westBandMean / Math.max(1e-15, r1.eastBandMean)));

        // ---- [2] 真实板块行 ----
        say("");
        say("  [2] 真实板块行（H=4000 m 真实深度；tau0=0.05 的三带风；h=5 km）");
        GyreRow.Params p2 = new GyreRow.Params();
        p2.h = 5000.0;
        GyreRow.WindCurl wind = new GyreRow.BandedWind(0.05, ZC);
        say(String.format(L, "      delta_M(赤道) = %.1f km   delta_M(45度) = %.1f km   rho0*H = %.3e",
            p2.deltaAt(0) / 1000, p2.deltaAt(250_000) / 1000, p2.rhoH));
        say(String.format(L, "      %-14s %10s %9s %12s %11s %9s %11s %9s",
            "查询点", "盆宽km", "用时us", "psi_max", "|v|峰mm/s", "西/东", "西带均mm/s", "西端符号"));
        long t0 = System.nanoTime();
        int nOk = 0, nLand = 0, nPos = 0;
        for (int iz = 0; iz < 8; iz++) {
            int z = iz * 250_000;
            for (int ix = 0; ix < 6; ix++) {
                int x = ix * 500_000;
                long s0 = System.nanoTime();
                GyreRow.Row r = GyreRow.solve(x, z, SD, CELL, wind, p2);
                long ms = (System.nanoTime() - s0) / 1000L;
                if (!r.valid) { nLand++; continue; }
                nOk++;
                double wb = 0; for (int i = 0; i < Math.min(10, r.n); i++) wb += r.v[i];
                wb /= 10;
                if (wb > 0) nPos++;
                if (iz < 4 && ix < 3) {
                    say(String.format(L, "      (%d,%d)km %10.0f %8d %12.0f %11.2f %9.2f %11.4f %9s",
                        x / 1000, z / 1000, (r.eastX - r.westX) / 1000.0, ms, Math.abs(r.psiMax),
                        Math.abs(r.vPeak) * 1000, r.westBandMean / Math.max(1e-15, r.eastBandMean),
                        r.westBandMean * 1000, wb > 0 ? "+" : "-"));
                }
            }
        }
        long tot = (System.nanoTime() - t0) / 1_000_000L;
        say(String.format(L, "      共 %d 次查询：有效 %d，陆格 %d，总耗时 %d ms（%.2f ms/次）",
            48, nOk, nLand, tot, tot / 48.0));
        say(String.format(L, "      西端 v 为正的次数 %d / %d（南半应多为 +，北半应多为 -）", nPos, nOk));
        rep.close();
    }

    static void say(String s) { System.out.println("[P250] " + s); rep.println("[P250] " + s); }
}
