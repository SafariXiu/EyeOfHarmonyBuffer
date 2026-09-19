package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P577 -- S2 接线的 ON 效果：把定常波的 div(V) 叠进 divU 之后，五个框的降水怎么变？
// 同时报：求解成本、自证残差、div(V) 与原有 divU 的量级比。
public class P577 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P577] " + s); System.out.println("[P577] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static double[] scan() {
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区"},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉"},
            {250.0, 285.0, 25.0, 35.0, "美国南部"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋"},
            {300.0, 350.0, 25.0, 35.0, "北大西洋"},
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        double[] out = new double[boxes.length];
        for (int b = 0; b < boxes.length; b++) {
            double lo = (double) boxes[b][0], hi = (double) boxes[b][1];
            double la = (double) boxes[b][2], hb = (double) boxes[b][3];
            long n = 0; double s = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    s += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    n++;
                }
            }
            out[b] = s / n;
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p577_report.txt"), "UTF-8");
        say("P577: S2 接线 ON 效果（定常波 div(V) 叠进 divU）");
        say("  初值: StationaryWave.ENABLED=" + StationaryWave.ENABLED
            + "  Radiation.SKIN=" + Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE
            + "  Radiation.BUCKET=" + Radiation.BUCKET_BETA);

        StationaryWave.ENABLED = false;
        double[] off = scan();
        say("");
        say("  解一次定常波...");
        StationaryWave.ENABLED = true;
        long t0 = System.nanoTime();
        StationaryWave.ensureSolved(SimTerrain.seedOf(SEED), PlateField.PLATE_CELL, Atmosphere.theta(0.0), GRAD);
        double solveMs = (System.nanoTime() - t0) / 1.0e6;
        say(String.format(LF, "  求解: solves=%d  nodes=%d  耗时=%.0f ms  自证残差=%.3e",
            StationaryWave.solveCount, StationaryWave.nodeCount, solveMs, StationaryWave.lastResidual));
        say(String.format(LF, "  网格: NX=%d  NPHI=%d  Q_SCALE=%.2e", StationaryWave.NX, StationaryWave.NPHI, StationaryWave.Q_SCALE));
        say(String.format(LF, "  div(V) 抽样: (0,0)=%.3e  (95E,25N)=%.3e  (15E,25N)=%.3e",
            StationaryWave.divAt(0, 0),
            StationaryWave.divAt((int) Math.round(95.0 / 360 * CIRC), WorldContract.zOfLat(25)),
            StationaryWave.divAt((int) Math.round(15.0 / 360 * CIRC), WorldContract.zOfLat(25))));

        double[] on = scan();
        StationaryWave.ENABLED = false;
        say("");
        say("  框              OFF        ON(加定常波)    变化%     地球真值    OFF/真值   ON/真值");
        double[] truth = {7.775, 0.105, 3.595, 2.402, 0.677};
        String[] nm = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋", "北大西洋"};
        for (int b = 0; b < 5; b++) {
            say(String.format(LF, "  %-12s %8.3f %10.3f %12.2f%% %10.3f %10.2f %10.2f",
                nm[b], off[b], on[b], 100.0 * (on[b] - off[b]) / off[b], truth[b], off[b] / truth[b], on[b] / truth[b]));
        }
        say("");
        say(String.format(LF, "  亚洲/撒哈拉 比值（真实 74.0）:  OFF = %.3f   ON = %.3f", off[0] / off[1], on[0] / on[1]));
        say("  判据：ON 应当把比值【推上去】—— 393 的环第一次真正闭合");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
