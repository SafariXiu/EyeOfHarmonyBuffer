package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P558 -- P556 差异的【决定性定位】。
 *
 * P556 事实：memo 关 vs 开，windAt/pressureAnomaly/surfaceTemp/mmPerDay 有 ~1e-6 相对差。
 * P557 事实：各原语在【它自己的测点】上完全可重复。
 * => 差异必在 P557 没测的地方：windAt/mmPerDay 会取 ±500 km 的模板点。
 *
 * 本探针做两件事：
 *   A) 在【P556 的确切测点与顺序】上，把 ±500 km 模板点的 elevationWithCell / sstAnom
 *      逐点做 memo 关 vs 开的逐位比较。
 *   B) 决定性对照：先把该纬度行的【海洋解预热】（这样 memo 开时不会发生嵌套海洋求解），
 *      再比较 windAt / pressureAnomaly。若 A 全同而 B 全同，则差异来自 memo 区域的【嵌套】。
 */
public class P558 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P558] " + s); System.out.println("[P558] " + s); }
    static final int SEED = 1022228679;
    static final int GS = SimClimate.GRAD_STEP;

    static long bits(double d) { return Double.doubleToLongBits(d); }
    static int badElev = 0, badSst = 0, badWind = 0, badPa = 0, n = 0;
    static void cmpElev(double a, double b) { if (bits(a) != bits(b)) badElev++; }
    static void cmpSst(double a, double b)  { if (bits(a) != bits(b)) badSst++; }
    static void cmpWind(double a, double b) { if (bits(a) != bits(b)) badWind++; }
    static void cmpPa(double a, double b)   { if (bits(a) != bits(b)) badPa++; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p558_report.txt"), "UTF-8");
        say("P558: P556 差异的决定性定位");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        OceanField.install(SEED);
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  GRAD_STEP=" + GS);
        say("  测点口径与 P556 完全一致: x = 100000 + i*137000, z = 50000 + i*91000");

        int N = 60;
        long h0 = Atmosphere.memoHits, m0 = Atmosphere.memoMisses;

        for (int i = 0; i < N; i++) {
            int x = 100_000 + i * 137_000;
            int z = 50_000 + i * 91_000;
            // ---- 排除嵌套：先把该纬度行的海洋解预热 ----
            Atmosphere.sstAnom(x, z);
            n++;

            int[] sx = {x + GS, x - GS, x, x};
            int[] sz = {z, z, z + GS, z - GS};
            double[] aE = new double[5], aS = new double[5];
            for (int t = 0; t < 4; t++) {
                aE[t] = PlateField.elevationWithCell(sx[t], sz[t], sd, cell);
                aS[t] = Atmosphere.sstAnom(sx[t], sz[t]);
            }
            aE[4] = PlateField.elevationWithCell(x, z, sd, cell);
            aS[4] = Atmosphere.sstAnom(x, z);
            double[] aW = Atmosphere.windAt(x, z, sd, cell, th, GS);
            double aPa = Atmosphere.pressureAnomaly(x, z, sd, cell, th);

            boolean mc = Atmosphere.beginMemo();
            double[] bE = new double[5], bS = new double[5];
            for (int t = 0; t < 4; t++) {
                bE[t] = PlateField.elevationWithCell(sx[t], sz[t], sd, cell);
                bS[t] = Atmosphere.sstAnom(sx[t], sz[t]);
            }
            bE[4] = PlateField.elevationWithCell(x, z, sd, cell);
            bS[4] = Atmosphere.sstAnom(x, z);
            double[] bW = Atmosphere.windAt(x, z, sd, cell, th, GS);
            double bPa = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            Atmosphere.endMemo(mc);

            for (int t = 0; t < 5; t++) { cmpElev(aE[t], bE[t]); cmpSst(aS[t], bS[t]); }
            cmpWind(aW[0], bW[0]); cmpWind(aW[1], bW[1]);
            cmpPa(aPa, bPa);

            if (i < 3) {
                say(String.format(LF, "  i=%d  windAt off=(%.12f,%.12f) on=(%.12f,%.12f)", i, aW[0], aW[1], bW[0], bW[1]));
                say(String.format(LF, "        Pa off=%.9f on=%.9f   d=%.3e", aPa, bPa, bPa - aPa));
            }
        }

        say("");
        say(String.format(LF, "A) 模板点逐位比较（%d 个测点 x (4 模板点 + 本点)）", n));
        say(String.format(LF, "   elevationWithCell 不符 = %d / %d", badElev, n * 5));
        say(String.format(LF, "   sstAnom           不符 = %d / %d", badSst, n * 5));
        say(String.format(LF, "B) 海洋行预热后的对照"));
        say(String.format(LF, "   windAt            不符 = %d / %d", badWind, n * 2));
        say(String.format(LF, "   pressureAnomaly   不符 = %d / %d", badPa, n));
        say(String.format(LF, "   memo 计数: hits=%d misses=%d", Atmosphere.memoHits - h0, Atmosphere.memoMisses - m0));
        say("");
        say("  判读：A 有非零 => 差异锁定在那个原语+点集；A 全零而 B 有非零 => 差异来自 memo 嵌套/覆盖");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
