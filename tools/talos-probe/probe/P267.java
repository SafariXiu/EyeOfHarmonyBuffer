package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P267：**降水场** —— B2（三条带）与 B4（雨影）。 */
public class P267 {

    static final int SEED = 1022228679;
    /** ⚠ 口径修正（§559，模板 P442:47）：收 {@code long seed} 的入口传本值；收 {@code int worldSeedInt} 的入口仍传裸 {@code SEED}。本次未改任何判据/阈值/输出行。 */
    static final long SD = com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain.seedOf(SEED);
    static final int ZC = WorldContract.Z_CYCLE;
    static final int X0 = 0, Z0 = 0, DX = 40_000, DZ = 100_000;
    static final int NX = 276, NZ = 201;
    static final int GRAD = 20_000;

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File OUT = new File(ROOT, "build/eoh_probe/mtn/map");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p267_report.txt"), "UTF-8");
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("P267：降水场（B2 三条带 / B4 雨影）");
        say(String.format(LF, "  网格 %dx%d  dx=%dkm dz=%dkm  差分步长=%dkm", NX, NZ, DX / 1000, DZ / 1000, GRAD / 1000));
        double[] a = new double[NX * NZ], b = new double[NX * NZ];
        long t0 = System.nanoTime();
        for (int r = 0; r < NZ; r++) {
            int z = Z0 + r * DZ;
            for (int c = 0; c < NX; c++) {
                int x = X0 + c * DX;
                a[r * NX + c] = PrecipField.mmPerDay(x, z, SD, cell, thS, GRAD);
                b[r * NX + c] = PrecipField.mmPerDay(x, z, SD, cell, thW, GRAD);
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        MapWriter.writePng(new File(OUT, "p267_precip_summer.png"), NX, NZ, a, 0, 12, MapWriter.THERMAL);
        MapWriter.writePng(new File(OUT, "p267_precip_winter.png"), NX, NZ, b, 0, 12, MapWriter.THERMAL);
        say(String.format(LF, "  渲染 %.0f ms（%d 点 x 2 季）  夏至 %.2f~%.2f 均值 %.2f mm/day  冬至均值 %.2f",
            ms, NX * NZ, min(a), max(a), mean(a), mean(b)));
        say("");

        // ---- B2：纬向平均降水剖面 ----
        say("B2. 纬向平均降水（mm/day）随纬度 —— 应该看到三条带");
        say(String.format(LF, "   %-9s %12s %12s %10s", "纬度", "夏至", "冬至", "陆地占比"));
        for (int k = 0; k < 18; k++) {
            double bandD = (k + 0.5) / 18.0;
            int z = (int) (bandD * (ZC / 2));
            int r = z / DZ;
            double sa = 0, sb = 0, sl = 0; int n = 0;
            for (int c = 0; c < NX; c++) {
                sa += a[r * NX + c]; sb += b[r * NX + c];
                if (PlateField.isLandWithCell(X0 + c * DX, z, SD, cell)) sl++;
                n++;
            }
            say(String.format(LF, "   %-9.1f %12.2f %12.2f %9.0f%%", Math.toDegrees(WorldContract.latOf(z, ZC)),
                sa / n, sb / n, sl * 100.0 / n));
        }
        say("");
        say("B4. 雨影：迎风坡 vs 背风坡（按海拔分档看降水）");
        say(String.format(LF, "   %-14s %10s %12s %12s", "海拔档 m", "点数", "平均降水", "平均水汽"));
        int[] lo = {-99999, 0, 500, 1000, 1500, 2000, 2500};
        int[] hi = {0, 500, 1000, 1500, 2000, 2500, 99999};
        for (int k = 0; k < lo.length; k++) {
            double sp = 0, sq = 0; int n = 0;
            for (int r = 0; r < NZ; r += 2) {
                int z = Z0 + r * DZ;
                for (int c = 0; c < NX; c += 2) {
                    int x = X0 + c * DX;
                    if (!PlateField.isLandWithCell(x, z, SD, cell)) continue;
                    double e = PlateField.elevationWithCell(x, z, SD, cell);
                    if (e < lo[k] || e >= hi[k]) continue;
                    sp += a[r * NX + c];
                    double lat = WorldContract.latOf(z);
                    double kk = Atmosphere.kappaAt(x, z, SD, cell);
                    sq += PrecipField.moisture(Atmosphere.tZonalMean(lat)
                        + Atmosphere.seasonalAnomaly(lat, kk, thS), e, kk);
                    n++;
                }
            }
            if (n == 0) { say(String.format(LF, "   %-14s %10d", lo[k] + "~" + hi[k], 0)); continue; }
            say(String.format(LF, "   %-14s %10d %12.2f %12.5f", lo[k] + "~" + hi[k], n, sp / n, sq / n));
        }
        say("");
        say(String.format(LF, "   输出 p267_precip_summer.png / p267_precip_winter.png"));
        rep.close();
    }

    static double min(double[] v) { double m = v[0]; for (double x : v) if (x < m) m = x; return m; }
    static double max(double[] v) { double m = v[0]; for (double x : v) if (x > m) m = x; return m; }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }

    static void say(String s) { System.out.println("[P267] " + s); rep.println("[P267] " + s); }
}
