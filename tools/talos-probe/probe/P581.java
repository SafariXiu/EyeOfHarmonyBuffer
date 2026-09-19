package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P581 -- E127 修复后的定案：在【求解器自己的网格】上做 corr(Q, div) 与盒比。
// 方程 eps*p + c^2*div(V) = -Q  =>  div ~ -Q/c^2  =>  corr 应接近 -1（修复前是 -0.067）。
public class P581 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P581] " + s); System.out.println("[P581] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋"};

    static double[] boxes(long sd, int cell, double th) {
        double[] out = new double[BOX.length];
        for (int b = 0; b < BOX.length; b++) {
            long n = 0; double s = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    s += PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), z, sd, cell, th, GRAD);
                    n++;
                }
            }
            out[b] = s / n;
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p581_report.txt"), "UTF-8");
        say("P581: E127（三处只取实部 + 错波数）修复后的复验");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        StationaryWave.ENABLED = false;
        double[] off = boxes(sd, cell, th);
        say("");
        say(String.format(LF, "参考 OFF（无定常波 wiring）: 亚洲 %.3f  撒哈拉 %.3f  比 %.3f",
            off[0], off[1], off[0] / off[1]));

        double[] scales = {1e-5, 1e-3, 1e-2};
        for (double qs : scales) {
            StationaryWave.ENABLED = true;
            StationaryWave.Q_SCALE = qs;
            StationaryWave.invalidate();
            long t0 = System.nanoTime();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            int rows = StationaryWave.gridRows();
            int nx = StationaryWave.NX;
            double[] q = StationaryWave.qGridCopy();
            double[] dv = StationaryWave.divGridCopy();

            double sq = 0, sd2 = 0, sqd = 0, sq2 = 0, sv2 = 0; long n = 0;
            double qmax = 0, dmax = 0;
            for (int j = 1; j < rows - 1; j++)
                for (int i = 0; i < nx; i++) {
                    double a = q[j * nx + i], b = dv[j * nx + i];
                    sq += a; sd2 += b; sqd += a * b; sq2 += a * a; sv2 += b * b; n++;
                    qmax = Math.max(qmax, Math.abs(a)); dmax = Math.max(dmax, Math.abs(b));
                }
            double mq = sq / n, md = sd2 / n;
            double cov = sqd / n - mq * md;
            double vq = sq2 / n - mq * mq, vd = sv2 / n - md * md;
            double corr = (vq > 0 && vd > 0) ? cov / Math.sqrt(vq * vd) : 0;
            double slope = vq > 0 ? cov / vq : 0;

            double[] bx = boxes(sd, cell, th);
            say("");
            say(String.format(LF, "Q_SCALE=%.0e  nodes=%d  lastResidual=%.3e  lastForcing=%.3e  解算 %d ms",
                qs, StationaryWave.nodeCount, StationaryWave.lastResidual, StationaryWave.lastForcing, ms));
            say(String.format(LF, "  网格 corr(Q,div) = %+.4f   （判据：应接近 -1；修复前是 -0.067）", corr));
            say(String.format(LF, "  最小二乘斜率 = %+.4e  （理论 div≈-Q/c^2 => -1/c^2 = %.4e）", slope, -1.0 / (70.0 * 70.0)));
            say(String.format(LF, "  max|Q|=%.3e  max|div|=%.3e  div RMS=%.3e", qmax, dmax, Math.sqrt(vd)));
            say(String.format(LF, "  盒子: 亚洲 %.3f  撒哈拉 %.3f  美南 %.3f  北太 %.3f   亚洲/撒哈拉 = %.3f",
                bx[0], bx[1], bx[2], bx[3], bx[0] / bx[1]));
        }
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
