package probe;

import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.space.talos.chunk.world.ClimateCoords;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P458：**验收项 ④-A —— 气候坐标分布**（便宜层；设计与理由见设计冻结 §128）。
 *
 * <p>为什么需要这一层：群系 Kind 是气候坐标 (temp, moist, continent) 经 250 m LUT 的确定性函数。
 * 用 V2BiomeField.sample 取 Kind 必须解整个群系 LUT（81,204 格/瓦片）⇒ 每点 2~8 s、480 点要几十分钟。
 * 而 D18/D17/D4/D39 这类修复改的**正是气候坐标本身** ⇒ 只统计坐标分布就能抓到它们，
 * 代价只有一次气候瓦片求解（约 0.5 s）⇒ 480 点约 4 分钟。
 *
 * <p>口径：与 P455 的 ④-1 **同网格同种子**，两者可以对账（Kind 必须是坐标的函数）。
 */
public class P458 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final int NXB = 24, NZB = 20;

    static void say(String s) { rep.println("[P458] " + s); System.out.println("[P458] " + s); }

    static double q(double[] v, double p) {
        double[] c = v.clone(); Arrays.sort(c);
        int i = (int) Math.round(p * (c.length - 1));
        return c[Math.max(0, Math.min(c.length - 1, i))];
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p458_report.txt"), "UTF-8");
        say("P458：验收项 ④-A —— 气候坐标分布（便宜层，不碰群系 LUT）");
        say(String.format(LF, "  网格 %dx%d = %d 点（与 P455 的 ④-1 同网格同种子）  COAST_FINE=%d km",
            NXB, NZB, NXB * NZB, SimClimate.COAST_FINE / 1000));
        say("");

        int n = NXB * NZB;
        double[] temp = new double[n], moist = new double[n], cont = new double[n], mar = new double[n], airT = new double[n];
        int[] am = new int[4];
        int[] contHist = new int[10];
        int k = 0, nLand = 0, nGate = 0;
        for (int iz = 0; iz < NZB; iz++) {
            int z = (int) ((iz + 0.5) / NZB * 20_000_000);
            for (int ix = 0; ix < NXB; ix++) {
                int x = (int) ((ix + 0.5) / NXB * 24_000_000) - 12_000_000;
                ClimateCoords.Coords c = SimClimate.sample(x, z, SEED, null);
                temp[k] = c.temp; moist[k] = c.moist; cont[k] = c.continent; mar[k] = c.mar; airT[k] = c.airT;
                if (c.airMass >= 0 && c.airMass < 4) am[c.airMass]++;
                int b = (int) (c.continent * 10); if (b > 9) b = 9; if (b < 0) b = 0; contHist[b]++;
                if (PlateField.isLandWithCell(x, z, SD, CELL)) nLand++;
                // BASIN 门带：旧语义的 0.35~0.60（V2BiomeSelect.BASIN_INLAND_LO/HI）
                if (c.continent >= 0.35 && c.continent <= 0.60) nGate++;
                k++;
                if (k % 60 == 0) { say(String.format(LF, "    进度: %d / %d", k, n)); rep.flush(); }
            }
        }
        say(String.format(LF, "  采样 n=%d（陆地 %d = %.1f%%）", n, nLand, 100.0 * nLand / n));
        say("");
        say("A. 四个气候坐标的分布（分位数）");
        say(String.format(LF, "  %-12s %8s %8s %8s %8s %8s %8s", "坐标", "p05", "p25", "p50", "p75", "p95", "mean"));
        String[] nm = {"temp", "moist", "continent", "mar", "airT"};
        double[][] arr = {temp, moist, cont, mar, airT};
        for (int i = 0; i < nm.length; i++) {
            double m = 0; for (double v : arr[i]) m += v; m /= n;
            say(String.format(LF, "  %-12s %8.3f %8.3f %8.3f %8.3f %8.3f %8.3f", nm[i],
                q(arr[i], 0.05), q(arr[i], 0.25), q(arr[i], 0.50), q(arr[i], 0.75), q(arr[i], 0.95), m));
        }
        say("");
        say("B. continent 的直方图（0.1 一档）—— D18 改的就是这个量");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(String.format(LF, "  [%.1f-%.1f] %4d (%.1f%%)", i * 0.1, (i + 1) * 0.1, contHist[i], 100.0 * contHist[i] / n));
        say(sb.toString());
        say(String.format(LF, "  ⇒ **落在 BASIN 门带 [0.35, 0.60] 的点 = %d (%.1f%%)**（D18 之前用 800 km 的 κ，岸线处恒 0.5 ⇒ 这一带会被撑满）", nGate, 100.0 * nGate / n));
        say("");
        say("C. airMass 四个类别的占比（离散量，D8 的靶心）");
        for (int i = 0; i < 4; i++) say(String.format(LF, "  airMass=%d  %4d (%.1f%%)", i, am[i], 100.0 * am[i] / n));
        say("");
        say("  ⚠ 本层**不测群系 Kind**（那需要群系 LUT）。它与 P455 的 ④-B 是同一网格同种子 ⇒ 可对账。");
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
