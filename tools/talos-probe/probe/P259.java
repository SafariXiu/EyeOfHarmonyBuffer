package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.export.MapWriter;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P259：**全球视野图**（x = 0..11,000 km）—— 给 D5（板块尺度）一个能直接看的证据。
 *
 * <p>P258 的窗口 x∈[0,2400 km] 在 PLATE_CELL=600 km 下只有 0.9% 陆地，
 * 看不出「连贯的经向海岸」，所以这里把 x 拉到 11,000 km，并把陆地占比按纬度带拆开。
 */
public class P259 {

    static final int SEED = 1022228679;
    static final int ZC = WorldContract.Z_CYCLE;
    static final int XSTEP = 20_000, ZSTEP = 200_000;   // z 覆盖一整个气候周期 0~20M
    static final int NX = 551, NZ = 101;
    static final int[] CELLS = {600_000, 2_400_000};
    static final int[] TAG = {600, 2400};

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static final File MAPDIR = new File(ROOT, "build/eoh_probe/mtn/map");
    static PrintStream rep;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p259_report.txt"), "UTF-8");
        say("P259：全球视野图 x=0..11,000 km, z=0..1000 km");
        say(String.format(LF, "  网格 %dx%d  dx=%d km  dz=%d km", NX, NZ, XSTEP / 1000, ZSTEP / 1000));
        say("");
        double th0 = Atmosphere.theta(0.0);
        double thHalf = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        for (int ci = 0; ci < CELLS.length; ci++) {
            int cell = CELLS[ci];
            double[] land = new double[NX * NZ];
            double[] tS = new double[NX * NZ];
            double[] tW = new double[NX * NZ];
            double[] pS = new double[NX * NZ];
            double[] elev = new double[NX * NZ];
            long t0 = System.nanoTime();
            for (int r = 0; r < NZ; r++) {
                int z = r * ZSTEP;
                for (int c = 0; c < NX; c++) {
                    int x = c * XSTEP;
                    int i = r * NX + c;
                    land[i] = PlateField.isLandWithCell(x, z, SEED, cell) ? 1.0 : 0.0;
                    elev[i] = PlateField.elevationWithCell(x, z, SEED, cell);
                    tS[i] = Atmosphere.surfaceTemp(x, z, SEED, cell, th0);
                    tW[i] = Atmosphere.surfaceTemp(x, z, SEED, cell, thHalf);
                    pS[i] = Atmosphere.pressureAnomaly(x, z, SEED, cell, th0);
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6;
            String tag = "c" + TAG[ci];
            MapWriter.writePng(new File(MAPDIR, "p259_" + tag + "_land.png"), NX, NZ, land, 0, 1, MapWriter.THERMAL);
            MapWriter.writePng(new File(MAPDIR, "p259_" + tag + "_elev.png"), NX, NZ, elev, -4500, 3200, MapWriter.DIVERGING);
            MapWriter.writePng(new File(MAPDIR, "p259_" + tag + "_temp_summer.png"), NX, NZ, tS, 230, 315, MapWriter.THERMAL);
            MapWriter.writePng(new File(MAPDIR, "p259_" + tag + "_temp_winter.png"), NX, NZ, tW, 230, 315, MapWriter.THERMAL);
            MapWriter.writePng(new File(MAPDIR, "p259_" + tag + "_p_summer.png"), NX, NZ, pS, -900, 900, MapWriter.DIVERGING);

            say(String.format(LF, "===== PLATE_CELL = %d =====", cell));
            say(String.format(LF, "  全球陆地占比 %.1f%%   海拔 min %.0f / max %.0f m   渲染 %.0f ms",
                mean(land) * 100, min(elev), max(elev), ms));
            say(String.format(LF, "  %-8s %-9s %8s %10s %12s", "z 段", "纬度", "陆地%", "海拔max", "海岸穿越数"));
            for (int k = 0; k < 10; k++) {
                int r0 = k * (NZ / 10), r1 = (k + 1) * (NZ / 10);
                double lf = 0; double em = -1e9; int n = 0, cross = 0;
                for (int r = r0; r < r1; r++) {
                    boolean prev = land[r * NX] > 0.5;
                    for (int c = 0; c < NX; c++) {
                        double v = land[r * NX + c];
                        lf += v; n++;
                        if (elev[r * NX + c] > em) em = elev[r * NX + c];
                        boolean cur = v > 0.5;
                        if (cur != prev) cross++;
                        prev = cur;
                    }
                }
                int zc = (int) ((r0 + r1) / 2.0 * ZSTEP);
                say(String.format(LF, "  %-8d %7.1f度 %7.1f%% %10.0f %12.1f",
                    zc, Math.toDegrees(WorldContract.latOf(zc)), lf / n * 100, em, cross / (double) (r1 - r0)));
            }
            say("");
        }
        say("输出：");
        for (String s : new String[]{"land", "elev", "temp_summer", "temp_winter", "p_summer"})
            say("  p259_c600_" + s + ".png / p259_c2400_" + s + ".png");
        rep.close();
    }

    static double min(double[] v) { double m = v[0]; for (double x : v) if (x < m) m = x; return m; }
    static double max(double[] v) { double m = v[0]; for (double x : v) if (x > m) m = x; return m; }
    static double mean(double[] v) { double s = 0; for (double x : v) s += x; return s / v.length; }

    static void say(String s) { System.out.println("[P259] " + s); rep.println("[P259] " + s); }
}
