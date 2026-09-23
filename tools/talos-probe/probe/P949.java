package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*; import java.util.*;

// P949 -- divU 的构成：cellPressure 的贡献 vs 其余, 陆/海分开.
public class P949 {
    static final int WORLD = 1022228679;
    static final int CELL  = PlateField.PLATE_CELL;
    static final int GRAD  = 500_000;
    static final int NW    = 12;                    // ★ 12 段【独立】40,000 km (P491 的做法)
    static final int SPAN  = 40_000_000;            // 每段 40,000 km
    static final int NPW   = 25;                    // 每段 25 点 => 每纬 300 点, 跨 480,000 km
    static final int NX    = NW * NPW;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P949] " + s); rep.flush(); System.out.println("[P949] " + s); System.out.flush(); }

    public static void main(String[] a) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p949_report.txt"), "UTF-8");
        long seed = SimTerrain.seedOf(WORLD);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thS = Atmosphere.theta(0.0);
        say("P949: divU 的构成  SHALLOW_FLOOR=" + PrecipField.SHALLOW_FLOOR
            + "  Q_FROM_BLBUDGET=" + PrecipField.Q_FROM_BLBUDGET
            + "  HadleyCell.ENABLED=" + HadleyCell.ENABLED
            + "  StationaryWave.ENABLED=" + StationaryWave.ENABLED);
        say("  DIAG 槽: [0]=divU(只 cellPressure) [1]=dWave [2]=divU(总) [3]=wEff [5]=q [14]=wDiag");
        say("  wBase: HadleyCell 关 => ZonalTables.wZm(wLat) = 【地球表】");
        say("");
        say("  ---- 冬至 (DJF for NH) ----");
        dump(seed, thW, "W");
        say("");
        say("  ---- 夏至 (JJA for NH) ----");
        dump(seed, thS, "S");
        say("DONE");
        rep.flush(); System.out.println("JAVA_EXIT=0");
    }

    static void dump(long seed, double theta, String tag) {
        say("   lat | sea% | --- 海洋 ---                       | --- 陆地 ---");
        say("       |      |  divU0     dWave    divU_tot   wEff  |  divU0     divU_tot   wEff   | q_sea  q_land");
        int H = WorldContract.MAX_D;
        for (int i = 1; i <= 17; i++) {
            double latDeg = i * 5.0;
            int z = (int) (latDeg / 90.0 * H);
            double[] s = new double[8], l = new double[8]; int ns = 0, nl = 0;
            for (int w = 0; w < NW; w++) {
              int x0 = -(NW / 2) * SPAN + w * SPAN;
              for (int k = 0; k < NPW; k++) {
                int x = x0 + (int) ((long) SPAN * (2 * k + 1) / (2 * NPW));
                double kap = Atmosphere.kappaMemo(x, z, seed, CELL);
                PrecipField.mmPerDay(x, z, seed, CELL, theta, GRAD);
                double[] d = PrecipField.DIAG.get();
                double[] acc = (kap <= 0.5) ? s : l;
                if (kap <= 0.5) ns++; else nl++;
                acc[0] += d[0]; acc[1] += d[1]; acc[2] += d[2]; acc[3] += d[3]; acc[5] += d[5];
              }
            }
            double n1 = Math.max(1, ns), n2 = Math.max(1, nl);
            say(String.format(LF, "   %3.0f | %4.0f | %+8.3e %+8.2e %+8.3e %+7.2e | %+8.3e %+8.3e %+7.2e | %6.4f %6.4f",
                latDeg, 100.0 * ns / (ns + nl),
                s[0] / n1, s[1] / n1, s[2] / n1, s[3] / n1,
                l[0] / n2, l[2] / n2, l[3] / n2, s[5] / n1, l[5] / n2));
        }
    }
}
