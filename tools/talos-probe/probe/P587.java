package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P587 -- 闭环：Q <- P(divU + div(V)) 的不动点迭代能否收敛？环路增益多少？
public class P587 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P587] " + s); System.out.println("[P587] " + s); }
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

    static void row(String tag, double[] r, long ms) {
        say(String.format(LF, "  %-22s 亚洲 %6.3f  撒哈拉 %6.3f  美南 %6.3f  北太 %6.3f  比 %6.3f  %s",
            tag, r[0], r[1], r[2], r[3], r[0] / r[1], ms >= 0 ? (ms + " ms") : ""));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p587_report.txt"), "UTF-8");
        say("P587: 闭环不动点迭代（Q <- P(divU + div(V))）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        StationaryWave.ENABLED = false;
        StationaryWave.CLOSED_LOOP = false;
        row("OFF（无定常波）", boxes(sd, cell, th), -1);

        say("");
        say("  [A] Q_SCALE = 1e-3，闭环迭代次数上限扫描（量收敛速度与环路增益）");
        int[] caps = {1, 2, 3, 5, 8, 12, 20, 40};
        for (int cap : caps) {
            StationaryWave.ENABLED = true;
            StationaryWave.CLOSED_LOOP = true;
            StationaryWave.Q_SCALE = 1e-3;
            StationaryWave.LOOP_MAX_ITER = cap;
            StationaryWave.LOOP_TOL = 0.0;        // 关掉提前退出 => 强制跑满 => 能看出轨迹
            StationaryWave.invalidate();
            long t0 = System.nanoTime();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            row("闭环 iter<=" + cap, boxes(sd, cell, th), ms);
            say(String.format(LF, "      实际迭代 %d  末轮相对变化 %.4e  残差 %.2e",
                StationaryWave.lastIterations, StationaryWave.lastLoopChange, StationaryWave.lastResidual));
        }

        say("");
        say("  [B] 收敛判据生效时的实际迭代次数（LOOP_TOL=1e-3）");
        double[] relax = {1.0, 0.7, 0.5, 0.3};
        for (double rx : relax) {
            StationaryWave.ENABLED = true;
            StationaryWave.CLOSED_LOOP = true;
            StationaryWave.Q_SCALE = 1e-3;
            StationaryWave.LOOP_MAX_ITER = 60;
            StationaryWave.LOOP_TOL = 1e-3;
            StationaryWave.LOOP_RELAX = rx;
            StationaryWave.invalidate();
            long t0 = System.nanoTime();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            row(String.format(LF, "松弛 %.1f", rx), boxes(sd, cell, th), ms);
            say(String.format(LF, "      迭代 %d  末轮变化 %.4e  %s",
                StationaryWave.lastIterations, StationaryWave.lastLoopChange,
                StationaryWave.lastLoopChange < 1e-3 ? "已收敛" : "未收敛（撞上限）"));
        }

        StationaryWave.ENABLED = false;
        StationaryWave.CLOSED_LOOP = false;
        StationaryWave.LOOP_TOL = 1e-3;
        StationaryWave.LOOP_RELAX = 1.0;
        StationaryWave.LOOP_MAX_ITER = 40;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
