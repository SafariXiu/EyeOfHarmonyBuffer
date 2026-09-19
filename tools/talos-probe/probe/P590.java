package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P590 -- 闭环轨迹（精简）+ HadleyCell bracketing 复验
public class P590 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P590] " + s); System.out.println("[P590] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};

    static double[] boxes(long sd, int cell, double th) {
        double[] out = new double[BOX.length];
        for (int b = 0; b < BOX.length; b++) {
            long n = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    out[b] += PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), z, sd, cell, th, GRAD);
                    n++;
                }
            }
            out[b] /= n;
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p590_report.txt"), "UTF-8");
        say("P590: 闭环轨迹（精简）+ HadleyCell bracketing 复验");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        double sc = HadleyCell.selfCheck();
        say("");
        say(String.format(LF, "  [A] HadleyCell.selfCheck() = %.3e  %s   phi_H=%.2f phi_0=%.2f dT=%.2fK",
            sc, sc < 1e-3 ? "通过 ✓" : "不通过 ✗", HadleyCell.phiHDeg(), HadleyCell.phiHDeg() / Math.sqrt(5), HadleyCell.deltaTModel()));
        say("      小角渐近比 err/(Y*R)（应趋近常数）：");
        for (double R : new double[]{1e-5, 1e-4, 1e-3, 1e-2}) {
            double y = HadleyCell.solveSinPhiH(R), ysa = HadleyCell.smallAngleSinPhiH(R);
            say(String.format(LF, "        R=%.0e  Y=%.8f  Y_sa=%.8f  比 = %.5f", R, y, ysa, Math.abs(y - ysa) / ysa / R));
        }

        StationaryWave.ENABLED = true;
        StationaryWave.CLOSED_LOOP = true;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.LOOP_RELAX = 1.0;
        say("");
        say("  [B] 闭环轨迹（LOOP_TOL=0 强制跑满，看增益与是否收敛）");
        say("      iter上限  亚洲    撒哈拉   比值    实际迭代  末轮变化   环路增益");
        for (int cap : new int[]{1, 2, 4, 8, 16}) {
            StationaryWave.LOOP_MAX_ITER = cap;
            StationaryWave.LOOP_TOL = 0.0;
            StationaryWave.invalidate();
            long t0 = System.nanoTime();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            long ms = (System.nanoTime() - t0) / 1_000_000;
            double[] r = boxes(sd, cell, th);
            say(String.format(LF, "      %4d      %6.3f  %6.3f  %6.3f    %3d     %.3e   %.4f   (%d ms)",
                cap, r[0], r[1], r[0] / r[1], StationaryWave.lastIterations,
                StationaryWave.lastLoopChange, StationaryWave.lastLoopGain, ms));
        }

        say("");
        say("  [C] 带收敛判据 + 下松弛（LOOP_TOL=1e-3, cap=20）");
        double[] rx = {1.0, 0.5, 0.3};
        for (double x : rx) {
            StationaryWave.LOOP_RELAX = x;
            StationaryWave.LOOP_TOL = 1e-3;
            StationaryWave.LOOP_MAX_ITER = 20;
            StationaryWave.invalidate();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            double[] r = boxes(sd, cell, th);
            say(String.format(LF, "      松弛 %.1f  亚洲 %6.3f  撒哈拉 %6.3f  比 %6.3f  迭代 %2d  末轮变化 %.3e  增益 %.4f  %s",
                x, r[0], r[1], r[0] / r[1], StationaryWave.lastIterations, StationaryWave.lastLoopChange,
                StationaryWave.lastLoopGain, StationaryWave.lastLoopChange < 1e-3 ? "已收敛" : "未收敛"));
        }
        StationaryWave.ENABLED = false;
        StationaryWave.CLOSED_LOOP = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
