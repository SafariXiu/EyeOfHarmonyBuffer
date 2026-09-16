package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Locale;

/**
 * P486：D74 的落地前预测量（不改源码，只在探针里算「如果按设计改了会怎样」）。
 *
 * <p>设计（设计冻结 §204）：把海面基座从「气温纬向平均 T_zm」换成
 * T_zm(phi) + (1-kappa)*(DELTA(phi) + SST')，其中 DELTA = SST_zm - t2m_zm
 * （ERA5 实测，10 度一档，见 §201.1）。
 *
 * <p>量三件事：冰缘纬度会挪到哪儿；面积加权的冰覆盖率；同一批海点会暖多少。
 */
public class P486 {

    static final int SEED = 1022228679;
    static final long SD = SimTerrain.seedOf(SEED);
    static final int CELL = PlateField.PLATE_CELL;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    /** §201.1 的 ERA5 实测：SST_zm - t2m_zm（K），纬度 0,10,...,90。 */
    static final double[] DELTA = {1.60, 1.38, 1.07, 3.06, 3.21, 1.98, 2.75, 4.85, 9.69, 11.19, 11.24};
    static PrintStream rep;

    static void say(String s) { rep.println("[P486] " + s); rep.flush(); System.out.println("[P486] " + s); System.out.flush(); }

    /** 表按 |lat| 对称、10 度一档线性插值（与 ZonalTables.interp 同口径）。 */
    static double delta(double latDeg) {
        double a = Math.abs(latDeg);
        if (a >= 90.0) return DELTA[DELTA.length - 1];
        int i = (int) (a / 10.0);
        double f = (a - i * 10.0) / 10.0;
        return DELTA[i] * (1 - f) + DELTA[i + 1] * f;
    }

    static boolean isSea(int x, int z) { return !PlateField.isLandWithCell(x, z, SD, CELL); }

    /** 当前口径：SimClimate.surfaceTempK 的**最冷月**。 */
    static double tColdNow(int x, int z) { return SimTerrain.coldestMonthTempK(x, z, SEED); }

    /** 提议口径（D74）：年平 = T_zm + (1-k)*(DELTA + SST')，最冷月 = 年平 - A。 */
    static double tColdNew(int x, int z) {
        double latRad = WorldContract.latOf(z);
        double latDeg = Math.toDegrees(latRad);
        double k = SimClimate.maritimeInland(x, z, SEED);
        double tAnn = Atmosphere.tZonalMean(latRad) + (1.0 - k) * (delta(latDeg) + Atmosphere.sstAnom(x, z));
        return tAnn - SimTerrain.seasonalAmpK(x, z, SEED);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p486_report.txt"), "UTF-8");
        say("P486：D74 的落地前预测量（当前口径 vs 提议口径，同一批真海点）");
        say(String.format(LF, "  DELTA 表（SST_zm - t2m_zm, K）= %s", Arrays.toString(DELTA)));
        say("");
        OceanWiring.onWorld(SEED);
        say("");
        int x = -6_000_000;
        say("A. 沿 z 扫一个 20M 周期（步长 333 km，x = -6M），只看真海");
        say(String.format(LF, "   %9s %8s %10s %10s %6s %6s", "z", "lat", "最冷月(现)", "最冷月(D74)", "现冰", "新冰"));
        int nSea = 0, nIceNow = 0, nIceNew = 0;
        double sumNow = 0, sumNew = 0;
        for (int z = 0; z < WorldContract.Z_CYCLE; z += 333_333) {
            if (!isSea(x, z)) continue;
            nSea++;
            double a = tColdNow(x, z), b = tColdNew(x, z);
            boolean i0 = a < SimTerrain.SEA_ICE_T, i1 = b < SimTerrain.SEA_ICE_T;
            if (i0) nIceNow++;
            if (i1) nIceNew++;
            sumNow += a; sumNew += b;
            if (nSea % 5 == 0 || i0 != i1) {
                say(String.format(LF, "   %9d %+8.2f %10.2f %10.2f %6s %6s",
                    z, Math.toDegrees(WorldContract.latOf(z)), a, b, i0 ? "冰" : "-", i1 ? "冰" : "-"));
            }
        }
        say(String.format(LF, "   ⇒ 采样 %d 个海点：现在结冰 %d（%.0f%%），D74 之后 %d（%.0f%%）",
            nSea, nIceNow, 100.0 * nIceNow / nSea, nIceNew, 100.0 * nIceNew / nSea));
        say(String.format(LF, "   ⇒ 平均最冷月海温：现在 %.2f K（%.2f C） -> D74 %.2f K（%.2f C），暖了 %.2f K",
            sumNow / nSea, sumNow / nSea - 273.15, sumNew / nSea, sumNew / nSea - 273.15, (sumNew - sumNow) / nSea));
        say("");

        say("B. 冰缘纬度（最靠赤道的结冰点 / 最靠极的未结冰点）");
        double warmestIceNow = -1e9, warmestIceNew = -1e9;
        double coldestNoIceNow = 1e9, coldestNoIceNew = 1e9;
        double latWI0 = 0, latWI1 = 0, latCN0 = 0, latCN1 = 0;
        for (int z = 0; z < WorldContract.Z_CYCLE; z += 333_333) {
            if (!isSea(x, z)) continue;
            double lat = Math.toDegrees(WorldContract.latOf(z));
            double a = tColdNow(x, z), b = tColdNew(x, z);
            if (a < SimTerrain.SEA_ICE_T && a > warmestIceNow) { warmestIceNow = a; latWI0 = lat; }
            if (b < SimTerrain.SEA_ICE_T && b > warmestIceNew) { warmestIceNew = b; latWI1 = lat; }
            if (a >= SimTerrain.SEA_ICE_T && a < coldestNoIceNow) { coldestNoIceNow = a; latCN0 = lat; }
            if (b >= SimTerrain.SEA_ICE_T && b < coldestNoIceNew) { coldestNoIceNew = b; latCN1 = lat; }
        }
        say(String.format(LF, "   现在：最暖的结冰点 %+.2f 度（%.2f K）；最冷的未结冰点 %+.2f 度（%.2f K）",
            latWI0, warmestIceNow, latCN0, coldestNoIceNow));
        say(String.format(LF, "   D74 ：最暖的结冰点 %+.2f 度（%.2f K）；最冷的未结冰点 %+.2f 度（%.2f K）",
            latWI1, warmestIceNew, latCN1, coldestNoIceNew));
        say("");
        say("C. 面积加权的冰覆盖率（对 z 做 cos(lat) 加权；只算真海点；单条经线 x=-6M）");
        double totW = 0, iceW = 0;
        for (int z = 0; z < WorldContract.Z_CYCLE; z += 100_000) {
            if (!isSea(x, z)) continue;
            double w = Math.cos(WorldContract.latOf(z));
            totW += w;
            if (tColdNew(x, z) < SimTerrain.SEA_ICE_T) iceW += w;
        }
        say(String.format(LF, "   提议口径下这一列真海的冰覆盖率 = %.1f%%（真实地球约 5~6%%）", 100.0 * iceW / Math.max(1e-9, totW)));
        say("   ⚠ 单经线估计；全局值要在 D74 落地后用全量探针量。");
        say("");
        say("⚠ 记账：本探针**没有改任何源码**，只是把提议的公式在探针里算了一遍。");
        rep.flush();
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
