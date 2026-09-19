package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P578 -- Q_SCALE 标定扫描：把 div(V) 拉到与模型自己的 divU 同量级（~1e-6），
// 并看此时五个框的降水怎么动。
public class P578 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P578] " + s); System.out.println("[P578] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static final double[][] BOXES = {
        {70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}, {300, 350, 25, 35}
    };
    static final String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋", "北大西洋"};
    static final double[] TRUTH = {7.775, 0.105, 3.595, 2.402, 0.677};

    static double[] scan(long sd, int cell, double th) {
        double[] out = new double[BOXES.length];
        for (int b = 0; b < BOXES.length; b++) {
            long n = 0; double s = 0;
            for (double latd = BOXES[b][2] + 2.5; latd <= BOXES[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < BOXES[b][0] || lon > BOXES[b][1]) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    s += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    n++;
                }
            }
            out[b] = s / n;
        }
        return out;
    }

    /** divCache 的 RMS（通过抽样估）。 */
    static double divRms() {
        long n = 0; double s2 = 0;
        for (int latd = -80; latd <= 80; latd += 5)
            for (int lon = 0; lon < 360; lon += 10) {
                double d = StationaryWave.divAt((int) Math.round(lon / 360.0 * CIRC), WorldContract.zOfLat(latd));
                s2 += d * d; n++;
            }
        return Math.sqrt(s2 / n);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p578_report.txt"), "UTF-8");
        say("P578: Q_SCALE 标定扫描（目标：div(V) 的 RMS 与模型的 divU ~1e-6 同量级）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        StationaryWave.ENABLED = false;
        double[] off = scan(sd, cell, th);
        StationaryWave.ENABLED = true;

        double[] scales = {1e-5, 1e-3, 1e-2, 1e-1};
        say("");
        say("  Q_SCALE     div(V) RMS     亚洲      撒哈拉    亚洲/撒哈拉   solve ms");
        for (double qs : scales) {
            StationaryWave.Q_SCALE = qs;
            StationaryWave.invalidate();
            long t0 = System.nanoTime();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            double ms = (System.nanoTime() - t0) / 1.0e6;
            double rms = divRms();
            // 比例性检查：三个固定点在每个 Q_SCALE 下的 div(V)
            int zx = WorldContract.zOfLat(25);
            double p1 = StationaryWave.divAt((int) Math.round(95.0 / 360 * CIRC), zx);
            double p2 = StationaryWave.divAt((int) Math.round(15.0 / 360 * CIRC), zx);
            double p3 = StationaryWave.divAt((int) Math.round(180.0 / 360 * CIRC), zx);
            say(String.format(LF, "      [比例性] (95E,25N)=%+.4e  (15E,25N)=%+.4e  (180E,25N)=%+.4e", p1, p2, p3));
            double[] on = scan(sd, cell, th);
            say(String.format(LF, "  %.0e   %12.3e   %7.3f  %9.3f   %10.3f   %7.0f",
                qs, rms, on[0], on[1], on[0] / on[1], ms));
        }
        say("");
        say("  参考（开关 OFF，未加定常波）：");
        say(String.format(LF, "    亚洲 %.3f   撒哈拉 %.3f   比值 %.3f", off[0], off[1], off[0] / off[1]));
        say("  判据：比值应当【上升】；且 div(V) RMS 应落在 ~1e-6 量级（否则 tanh 不动或直接饱和）");
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
