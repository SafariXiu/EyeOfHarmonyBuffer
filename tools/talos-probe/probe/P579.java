package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P579 -- 定案符号：div(V) 与强迫 Q 的相关性符号是什么？
// 方程是 eps*p + c^2*div(V) = -Q  =>  加热处 div(V) < 0（辐合）。
// 所以 corr(Q, div) 应当【为负】。若为正 => 符号链有问题。
public class P579 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P579] " + s); System.out.println("[P579] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p579_report.txt"), "UTF-8");
        say("P579: div(V) 与 Q 的相关性符号（方程 eps*p + c^2*div(V) = -Q）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        StationaryWave.ENABLED = true;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.ensureSolved(sd, cell, th, GRAD);
        say(String.format(LF, "  求解: solves=%d nodes=%d 残差=%.3e  Q_SCALE=%.1e",
            StationaryWave.solveCount, StationaryWave.nodeCount, StationaryWave.lastResidual, StationaryWave.Q_SCALE));

        double sq = 0, sdd = 0, sqd = 0, sq2 = 0, sd2 = 0; long n = 0;
        double qmin = 1e30, qmax = -1e30;
        for (int latd = -80; latd <= 80; latd += 5)
            for (int lon = 0; lon < 360; lon += 10) {
                int z = WorldContract.zOfLat(latd);
                int x = (int) Math.round(lon / 360.0 * CIRC);
                double q = 28.356 * PrecipField.mmPerDay(x, z, sd, cell, th, GRAD) * StationaryWave.Q_SCALE;
                double d = StationaryWave.divAt(x, z);
                sq += q; sdd += d; sqd += q * d; sq2 += q * q; sd2 += d * d; n++;
                qmin = Math.min(qmin, q); qmax = Math.max(qmax, q);
            }
        double mq = sq / n, md = sdd / n;
        double cov = sqd / n - mq * md;
        double vq = sq2 / n - mq * mq, vd = sd2 / n - md * md;
        double corr = (vq > 0 && vd > 0) ? cov / Math.sqrt(vq * vd) : 0;
        say("");
        say(String.format(LF, "  Q 范围 [%.3e, %.3e]   均值 %.3e", qmin, qmax, mq));
        say(String.format(LF, "  div(V) 均值 %.3e   RMS %.3e", md, Math.sqrt(vd)));
        say(String.format(LF, "  corr(Q, div) = %+.4f", corr));
        say("");
        say("  判据：方程是 eps*p + c^2*div(V) = -Q  =>  加热处 div 应为【负】=> corr 应为【负】");
        say("        若 corr 为正 => 符号链里有一处反了");
        say("");
        // 抽样：几个已知的强/弱加热点
        say("  抽样点（lat, lon）: Q  与  div(V)");
        int[][] pts = {{25, 95}, {25, 15}, {35, 140}, {25, 180}, {0, 0}, {45, 0}};
        for (int[] pt : pts) {
            int z = WorldContract.zOfLat(pt[0]);
            int x = (int) Math.round(pt[1] / 360.0 * CIRC);
            double q = 28.356 * PrecipField.mmPerDay(x, z, sd, cell, th, GRAD) * StationaryWave.Q_SCALE;
            double d = StationaryWave.divAt(x, z);
            say(String.format(LF, "    (%2dN, %3dE)  Q=%+.4e   div=%+.4e", pt[0], pt[1], q, d));
        }
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
