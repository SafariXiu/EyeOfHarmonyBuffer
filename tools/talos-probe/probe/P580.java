package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P580 -- 一次定案两件事：
//  (1) 顺序依赖（373）：同一批点正序/逆序读，是否逐位一致？
//  (2) 符号：用【关闭接线】的种子 Q 去和已解出的 div(V) 求相关，符号应为负。
public class P580 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P580] " + s); System.out.println("[P580] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int NP = 400;
    static int[] PX = new int[NP], PZ = new int[NP];

    static void buildPoints() {
        int n = 0;
        for (int a = 0; a < 20; a++)
            for (int b = 0; b < 20; b++) {
                int latd = -76 + a * 8;
                double lon = (b + 0.5) * 18.0;
                PX[n] = (int) Math.round(lon / 360.0 * CIRC);
                PZ[n] = WorldContract.zOfLat(latd);
                n++;
            }
    }

    static double[] readSeq(long sd, int cell, double th, boolean forward) {
        double[] out = new double[NP];
        if (forward) { for (int i = 0; i < NP; i++) out[i] = PrecipField.mmPerDay(PX[i], PZ[i], sd, cell, th, GRAD); }
        else { for (int i = NP - 1; i >= 0; i--) out[i] = PrecipField.mmPerDay(PX[i], PZ[i], sd, cell, th, GRAD); }
        return out;
    }

    static void cmp(String tag, double[] a, double[] b) {
        int diff = 0; double mx = 0; int at = -1;
        for (int i = 0; i < NP; i++) {
            if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
                diff++;
                double d = Math.abs(a[i] - b[i]);
                if (d > mx) { mx = d; at = i; }
            }
        }
        say(String.format(LF, "  %s: 不同 %d/%d   最大差 %.6e%s", tag, diff, NP, mx,
            at >= 0 ? String.format(LF, "  @lat=%d lon=%.0f (%.6f vs %.6f)", ZL(at), XL(at), a[at], b[at]) : ""));
    }

    static int ZL(int i) { return -76 + (i / 20) * 8; }
    static double XL(int i) { return (i % 20 + 0.5) * 18.0; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p580_report.txt"), "UTF-8");
        buildPoints();
        say("P580: 顺序依赖 + 符号定案");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        StationaryWave.ENABLED = false;
        StationaryWave.Q_SCALE = 1e-3;

        say("");
        say("[A] 接线关闭（种子态）：正序 vs 逆序");
        double[] fwd = readSeq(sd, cell, th, true);
        double[] rev = readSeq(sd, cell, th, false);
        cmp("A 正序 vs 逆序", fwd, rev);

        say("");
        say("[B] 求解一次（Q_SCALE=1e-3），求解器内部仍走 NX=64 粗网格");
        StationaryWave.ENABLED = true;
        StationaryWave.invalidate();
        long t0 = System.nanoTime();
        StationaryWave.ensureSolved(sd, cell, th, GRAD);
        say(String.format(LF, "  solves=%d nodes=%d 残差=%.3e 耗时=%d ms", StationaryWave.solveCount,
            StationaryWave.nodeCount, StationaryWave.lastResidual, (System.nanoTime() - t0) / 1_000_000));

        // ★ 关键：把接线关掉，量到的才是求解器当初吃的那个种子 Q
        StationaryWave.ENABLED = false;
        say("");
        say("[C] 接线关闭后再读同一批点（= 求解器吃的种子 Q）");
        double[] seed = readSeq(sd, cell, th, true);
        double[] seedRev = readSeq(sd, cell, th, false);
        cmp("C 正序 vs 逆序", seed, seedRev);
        cmp("A(正序) vs C(正序)", fwd, seed);

        say("");
        say("[D] 符号定案：corr(种子 Q, div(V))，方程 eps*p + c^2*div(V) = -Q => 应为【负】");
        double sq = 0, sdd = 0, sqd = 0, sq2 = 0, sd2 = 0; long n = 0;
        for (int i = 0; i < NP; i++) {
            double q = seed[i];
            double d = StationaryWave.divAt(PX[i], PZ[i]);
            sq += q; sdd += d; sqd += q * d; sq2 += q * q; sd2 += d * d; n++;
        }
        double mq = sq / n, md = sdd / n;
        double cov = sqd / n - mq * md;
        double vq = sq2 / n - mq * mq, vd = sd2 / n - md * md;
        double corr = (vq > 0 && vd > 0) ? cov / Math.sqrt(vq * vd) : 0;
        say(String.format(LF, "  P 均值 %.4f   div 均值 %+.4e   div RMS %.4e", mq, md, Math.sqrt(vd)));
        say(String.format(LF, "  corr(P_seed, div) = %+.4f   %s", corr,
            corr < -0.3 ? "<<== 负、显著 => 符号正确" : (corr > 0.3 ? "<<== 正 => 符号反了" : "<<== 弱相关，需看下面的点")));

        say("");
        say("  单点对照（求解器在那里的种子 Q 与解出的 div）:");
        int[] show = {0, 57, 100, 143, 200, 257, 300, 343, 399};
        for (int i : show)
            say(String.format(LF, "    lat=%3d lon=%5.0f   P=%8.4f   Q=%.4e   div=%+.4e",
                ZL(i), XL(i), seed[i], 28.356 * seed[i] * 1e-3, StationaryWave.divAt(PX[i], PZ[i])));

        say("");
        say("[E] 盒子均值（种子态，与 P578 的 OFF 行同法）");
        double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
        String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋"};
        for (int b = 0; b < BOX.length; b++) {
            long m = 0; double s = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    s += PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), z, sd, cell, th, GRAD);
                    m++;
                }
            }
            say(String.format(LF, "    %-8s = %.4f mm/day", NM[b], s / m));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
