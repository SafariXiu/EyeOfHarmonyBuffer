package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;

import java.io.*;
import java.util.Locale;

// P584 -- 盒均值反号的归因：在【求解器自己的网格】上直接看 Q 与 div。
// 假设：Qk[0]=0 抹掉纬向平均 => div 逐纬度零纬向平均，而 P 的盒均值含纬向平均 => 口径不匹配。
public class P584 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P584] " + s); System.out.println("[P584] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p584_report.txt"), "UTF-8");
        say("P584: Q 与 div(V) 在求解器网格上的逐点对照");
        StationaryWave.ENABLED = true;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(SimTerrain.seedOf(1022228679), PlateField.PLATE_CELL, Atmosphere.theta(0.0), 500_000);
        int rows = StationaryWave.gridRows(), nx = StationaryWave.NX;
        double[] q = StationaryWave.qGridCopy(), dv = StationaryWave.divGridCopy();
        say(String.format(LF, "  rows=%d NX=%d  残差=%.3e", rows, nx, StationaryWave.lastResidual));

        say("");
        say("  [A] 逐行纬向平均（应当 ~0，因为 Qk[0]=0）与纬向 RMS：");
        for (int j = 1; j < rows - 1; j += 6) {
            double lat = -90.0 + 180.0 * j / (rows - 1);
            double mq = 0, mv = 0, vq = 0, vv = 0;
            for (int i = 0; i < nx; i++) { mq += q[j * nx + i]; mv += dv[j * nx + i]; }
            mq /= nx; mv /= nx;
            for (int i = 0; i < nx; i++) { vq += Math.pow(q[j * nx + i] - mq, 2); vv += Math.pow(dv[j * nx + i] - mv, 2); }
            say(String.format(LF, "    lat=%+6.1f   Q: 均值%+.3e RMS%.3e | div: 均值%+.3e RMS%.3e",
                lat, mq, Math.sqrt(vq / nx), mv, Math.sqrt(vv / nx)));
        }

        say("");
        say("  [B] Q 最大的 8 个点（若 div 与 Q 反号，则 div 应为负）：");
        int[] ord = new int[rows * nx];
        Integer[] idx = new Integer[rows * nx];
        for (int t = 0; t < rows * nx; t++) idx[t] = t;
        final double[] qf = q;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(qf[b], qf[a]));
        for (int t = 0; t < 8; t++) {
            int id = idx[t], j = id / nx, i = id % nx;
            double lat = -90.0 + 180.0 * j / (rows - 1);
            say(String.format(LF, "    lat=%+6.1f lon=%6.1f  Q=%+.4e  div=%+.4e  -Q/c^2=%+.4e  %s",
                lat, 360.0 * i / nx, q[j * nx + i], dv[j * nx + i], -q[j * nx + i] / 4900.0,
                dv[j * nx + i] < 0 ? "同号✓" : "反号✗"));
        }

        say("");
        say("  [C] 亚洲(15~35N,70~120E) / 撒哈拉(20~35N,0~30E) 在【求解器网格】上的逐点均值：");
        double[][] BB = {{15, 35, 70, 120}, {20, 35, 0, 30}};
        String[] NN = {"亚洲", "撒哈拉"};
        for (int b = 0; b < 2; b++) {
            long n = 0; double sq = 0, sv = 0, sqd = 0, sq2 = 0, sv2 = 0;
            for (int j = 1; j < rows - 1; j++) {
                double lat = -90.0 + 180.0 * j / (rows - 1);
                if (lat < BB[b][0] || lat > BB[b][1]) continue;
                for (int i = 0; i < nx; i++) {
                    double lon = 360.0 * i / nx;
                    if (lon < BB[b][2] || lon > BB[b][3]) continue;
                    double a = q[j * nx + i], c = dv[j * nx + i];
                    sq += a; sv += c; sqd += a * c; sq2 += a * a; sv2 += c * c; n++;
                }
            }
            double mq = sq / n, mv = sv / n;
            double cov = sqd / n - mq * mv;
            double vq = sq2 / n - mq * mq, vv = sv2 / n - mv * mv;
            double corr = (vq > 0 && vv > 0) ? cov / Math.sqrt(vq * vv) : 0;
            say(String.format(LF, "    %-6s n=%4d  Q均值%+.4e  div均值%+.4e  corr(Q,div)=%+.4f  %s",
                NN[b], n, mq, mv, corr, corr < -0.3 ? "负相关 ✓" : (corr > 0.3 ? "正相关 ✗" : "弱")));
        }
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
