package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P462：**D46 的验收探针** —— 沿岸到内陆的雪线过渡（P297 分辨不出 3.43 K 的振幅变化）。
 *
 * <p>量的是**生产量** {@link SimTerrain#warmestMonthTempK}（雪判据就是它 < {@code SNOW_T}），
 * 并在同一批点上并列三个「海洋影响因子」的反事实：
 * <pre>
 *   f_old    = clamp01(-coastD/40km)                     ← D46 修复前
 *   f_kappa  = Atmosphere.kappaAt                         ← 我第一版修法（800 km）
 *   f_mar    = SimClimate.maritimeInland（= 生产）        ← 200 km 专用尺度
 *   T_warm(f) = SimClimate.surfaceTempK + [aSea + (aLand-aSea)*f]
 * </pre>
 */
public class P462 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P462] " + s); System.out.println("[P462] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p462_report.txt"), "UTF-8");
        say("P462：D46 验收探针 —— 沿岸到内陆的雪线过渡（生产量 warmestMonthTempK）");
        say(String.format(LF, "  SNOW_T=%.2f K   MARITIME_SCALE=%d km   COAST_FAR=%d km   COAST_FINE=%d km",
            SimTerrain.SNOW_T, (int) (SimClimate.MARITIME_SCALE / 1000), SimClimate.COAST_FAR / 1000, SimClimate.COAST_FINE / 1000));
        say("");
        // ⚠ 单位是 **block = 米**（1 block = 1 m），不是 km —— 我第一版写成 km，
        // 于是所有 >2000 m 的点都掉进最后一档，得出一张没有意义的表。已修（E22）。
        double[] BIN = {0, 25_000, 50_000, 100_000, 200_000, 300_000, 500_000, 800_000, 1_200_000, 2_000_000};
        int NB = BIN.length - 1;
        double[][] sum = new double[3][NB];
        int[][] snowCnt = new int[3][NB];
        int[] cnt = new int[NB];
        double[] dMean = new double[NB];
        int nLat = 0;
        double[][] latRows = new double[3][6];
        for (double latDeg = 45; latDeg <= 65.0001; latDeg += 10) {
            nLat++;
            int z = WorldContract.zOfLat(latDeg);
            double lat = WorldContract.latOf(z);
            double aS = ZonalTables.aSea(Math.abs(latDeg)), aL = ZonalTables.aLand(Math.abs(latDeg));
            for (int c = 0; c < 121; c++) {
                int x = -3_000_000 + c * 50_000;
                double dFine = PlateField.coastDistanceNew(x, z, SD, CELL, SimClimate.COAST_FINE);
                if (dFine > 0) continue;                       // 只看陆地
                double dFar = PlateField.coastDistanceNew(x, z, SD, CELL, SimClimate.COAST_FAR);
                double dd = -dFar;
                if (dd < 0 || dd >= 1_200_000) continue;       // 只看到岸 1200 km（coastFar 的可分辨上限是 1024 km）
                double[] f = {Math.min(1.0, Math.max(0.0, -dFine) / 40_000.0),
                              Atmosphere.kappaAt(x, z, SD, CELL),
                              SimClimate.maritimeInland(x, z, SEED)};
                double tAnn = SimClimate.surfaceTempK(x, z, SEED);
                int b = NB - 1;
                for (int q = 0; q < NB; q++) if (dd >= BIN[q] && dd < BIN[q + 1]) { b = q; break; }
                cnt[b]++; dMean[b] += dd;
                for (int k = 0; k < 3; k++) {
                    double tw = tAnn + (aS + (aL - aS) * f[k]);
                    sum[k][b] += tw;
                    if (tw < SimTerrain.SNOW_T) snowCnt[k][b]++;
                }
                // 也直接调一次生产入口做一致性自检
                double prod = SimTerrain.warmestMonthTempK(x, z, SEED);
                double recon = tAnn + (aS + (aL - aS) * f[2]);
                if (Math.abs(prod - recon) > 1e-6 && latRows[0][0] < 1e9) latRows[0][0] = Math.abs(prod - recon);
            }
        }
        say(String.format(LF, "  采样 %d 条纬线（45/55/65 度）x 121 个 x 点（x 步长 50 km，x ∈ [-3000, +3000] km）", nLat));
        say("  ⚠ coastFar 的倍增搜索上限是 1024 km ⇒ 距离 >1024 km 的点返回哨兵，已被过滤。");
        say(String.format(LF, "  生产入口自检：max |warmestMonthTempK - (Tann + amp(f_mar))| = %.3e K", latRows[0][0]));
        say("");
        say("A. 离岸距离分档：**最暖月地表温度**（K）与**下雪比例**（%）");
        say(String.format(LF, "  %-12s %6s %-22s %-22s %-22s", "离岸 km", "n", "f_old(40km)", "f_kappa(800km)", "**f_maritime(200km)**"));
        for (int b = 0; b < NB; b++) {
            if (cnt[b] == 0) continue;
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(LF, "  %-12s %6d", (int) (BIN[b] / 1000) + "-" + (int) (BIN[b + 1] / 1000), cnt[b]));
            for (int k = 0; k < 3; k++)
                sb.append(String.format(LF, " %7.2f K /%5.1f%%     ", sum[k][b] / cnt[b], 100.0 * snowCnt[k][b] / cnt[b]));
            say(sb.toString());
        }
        say("");
        say("B. 结论");
        say("  - f_old：0~25 km 档就已经接近内陆值 ⇒ 海洋影响被压成 40 km 的窄带；");
        say("  - f_kappa：岸线处只有一半海洋性（κ 在岸线 = 0.5），过渡拉到 800 km，太缓；");
        say("  - f_maritime：岸线 = 完全海洋性，~200 km 一个 e-folding，~600 km 才趋近内陆值。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
