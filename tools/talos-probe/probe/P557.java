package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

/**
 * P557 -- P556 自证失败的【隔离诊断】。
 *
 * P556 事实：memo 关 vs memo 开，windAt/pressureAnomaly/surfaceTemp/mmPerDay 有 1e-6 相对差，
 * 而 kappaAt vs kappaMemo 逐位相同。反推 pressureAnomaly 的差 => elevationWithCell 差了 ~0.6 mm。
 *
 * 本探针问：哪一个原语在【相同入参】下给出不同结果？也就是【依赖调用历史】？
 * 这是比 memo 更根本的问题：若地形高度依赖调用顺序，整个模型的读数就依赖调用顺序。
 */
public class P557 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P557] " + s); System.out.println("[P557] " + s); }
    static final int SEED = 1022228679;

    static long bits(double d) { return Double.doubleToLongBits(d); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p557_report.txt"), "UTF-8");
        say("P557: 相同入参下的可重复性（谁依赖调用历史？）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("  seed=" + sd + "  PLATE_CELL=" + cell + "  WORLD_IS_TALOS=" + PlateField.WORLD_IS_TALOS);
        OceanField.install(SEED);

        // ---- 1) elevationWithCell 连续两次 ----
        int n = 40, bad1 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            double a = PlateField.elevationWithCell(x, z, sd, cell);
            double b = PlateField.elevationWithCell(x, z, sd, cell);
            if (bits(a) != bits(b)) { bad1++; if (bad1 <= 4) say(String.format(LF, "  1) elev 连续两次不同 @(%d,%d): %.9f vs %.9f  d=%.3e", x, z, a, b, b - a)); }
        }
        say(String.format(LF, "1) elevationWithCell 连续两次: %d/%d 不同", bad1, n));

        // ---- 2) landScoreWithCell 连续两次 ----
        int bad2 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            double a = PlateField.landScoreWithCell(x, z, sd, cell);
            double b = PlateField.landScoreWithCell(x, z, sd, cell);
            if (bits(a) != bits(b)) bad2++;
        }
        say(String.format(LF, "2) landScoreWithCell 连续两次: %d/%d 不同", bad2, n));

        // ---- 3) 中间插入别的点后再回来（调用历史）----
        int bad3 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            double a = PlateField.elevationWithCell(x, z, sd, cell);
            PlateField.elevationWithCell(x + 3_000_000, z + 1_000_000, sd, cell);
            PlateField.elevationWithCell(x - 2_000_000, z - 900_000, sd, cell);
            double b = PlateField.elevationWithCell(x, z, sd, cell);
            if (bits(a) != bits(b)) { bad3++; if (bad3 <= 4) say(String.format(LF, "  3) elev 扰动后不同 @(%d,%d): %.9f vs %.9f  d=%.3e", x, z, a, b, b - a)); }
        }
        say(String.format(LF, "3) elevationWithCell 中间插入他点后: %d/%d 不同", bad3, n));

        // ---- 4) memo 缓存的 elev vs 现算的 elev ----
        int bad4 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            boolean mc = Atmosphere.beginMemo();
            double[] ke = new double[]{Atmosphere.kappaMemo(x, z, sd, cell)};
            Atmosphere.endMemo(mc);
            double fresh = PlateField.elevationWithCell(x, z, sd, cell);
            double freshFirst = PlateField.elevationWithCell(x, z, sd, cell);
            if (bits(fresh) != bits(freshFirst)) bad4++;
        }
        say(String.format(LF, "4) (往返) elev 现算两次: %d/%d 不同", bad4, n));

        // ---- 5) sstAnom 连续两次 ----
        int bad5 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            double a = Atmosphere.sstAnom(x, z);
            double b = Atmosphere.sstAnom(x, z);
            if (bits(a) != bits(b)) bad5++;
        }
        say(String.format(LF, "5) sstAnom 连续两次: %d/%d 不同", bad5, n));

        // ---- 6) 决定性对照：整条 pressureAnomaly 在【同一 memo 状态】下连算两次 ----
        say("");
        say("6) 决定性对照 —— pressureAnomaly 在【memo 恒关】下连算两次（同一状态，只有顺序不同）:");
        double th = Atmosphere.theta(0.0);
        int bad6 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            double a = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double b = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            if (bits(a) != bits(b)) { bad6++; if (bad6 <= 4) say(String.format(LF, "  !! 同状态下两次不同 @(%d,%d): %.9f vs %.9f  d=%.3e", x, z, a, b, b - a)); }
        }
        say(String.format(LF, "   memo 恒关连算两次: %d/%d 不同", bad6, n));

        int bad7 = 0;
        for (int i = 0; i < n; i++) {
            int x = 137_000 + i * 211_000, z = 91_000 + i * 173_000;
            boolean mc = Atmosphere.beginMemo();
            double a = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            double b = Atmosphere.pressureAnomaly(x, z, sd, cell, th);
            Atmosphere.endMemo(mc);
            if (bits(a) != bits(b)) bad7++;
        }
        say(String.format(LF, "   memo 恒开连算两次: %d/%d 不同", bad7, n));

        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
