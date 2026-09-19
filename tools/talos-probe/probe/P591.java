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

// P591 -- 用【推导出来的】物理 Q（Q_PER_MMDAY=1e-3）重测。
// 关键判据修正：定常波的作用是造【经向图案】，不是造散度量级。
//   div(V) 逐纬度零纬向平均 => 正确的对照是"扰动散度 vs 扰动散度"，不是 RMS vs RMS。
public class P591 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P591] " + s); System.out.println("[P591] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};

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

    /** 25N 上的纬向平均与纬向扰动 RMS（divU 与 div(V)）。 */
    static double[] zonalAt25(long sd, int cell, double th) {
        double[] du = new double[36], dw = new double[36];
        int z = WorldContract.zOfLat(25);
        for (int c = 0; c < 36; c++) {
            PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 36), z, sd, cell, th, GRAD);
            double[] d = PrecipField.DIAG.get();
            du[c] = d[0]; dw[c] = d[1];
        }
        double[] out = new double[4];
        for (int c = 0; c < 36; c++) { out[0] += du[c]; out[1] += dw[c]; }
        out[0] /= 36; out[1] /= 36;
        for (int c = 0; c < 36; c++) { out[2] += Math.pow(du[c] - out[0], 2); out[3] += Math.pow(dw[c] - out[1], 2); }
        out[2] = Math.sqrt(out[2] / 36); out[3] = Math.sqrt(out[3] / 36);
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p591_report.txt"), "UTF-8");
        say("P591: 物理 Q（Q_PER_MMDAY=1e-3 m^2/s^3 per mm/day）下的重测");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("");
        say(String.format(LF, "  Q_PER_MMDAY = %.3e   Q_SCALE = %.2f  => 每 mm/day 的 Q = %.3e",
            StationaryWave.Q_PER_MMDAY, StationaryWave.Q_SCALE,
            StationaryWave.Q_PER_MMDAY * StationaryWave.Q_SCALE));
        say("  子代理核实：1 mm/day => Q_SW 约 1.0e-3，|div| 约 2.0e-7 s^-1（c=70）");

        StationaryWave.ENABLED = false;
        HadleyCell.ENABLED = false;
        double[] off = boxes(sd, cell, th);
        say("");
        say(String.format(LF, "  OFF  : 亚洲 %.3f  撒哈拉 %.3f  美南 %.3f  北太 %.3f  比 %.3f",
            off[0], off[1], off[2], off[3], off[0] / off[1]));
        double[] z0 = zonalAt25(sd, cell, th);
        say(String.format(LF, "  25N 纬向: <divU>=%+.4e  扰动RMS(divU)=%.4e   <div(V)>=%+.4e  扰动RMS(div(V))=%.4e",
            z0[0], z0[2], z0[1], z0[3]));

        for (double qs : new double[]{1.0, 3.0, 10.0}) {
            StationaryWave.ENABLED = true;
            StationaryWave.CLOSED_LOOP = false;
            StationaryWave.Q_SCALE = qs;
            StationaryWave.invalidate();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            double[] b = boxes(sd, cell, th);
            double[] z = zonalAt25(sd, cell, th);
            say("");
            say(String.format(LF, "  Q_SCALE=%.1f（= 物理值的 %.0f 倍）  残差 %.1e", qs, qs, StationaryWave.lastResidual));
            say(String.format(LF, "    亚洲 %.3f  撒哈拉 %.3f  美南 %.3f  北太 %.3f  比 %.3f", b[0], b[1], b[2], b[3], b[0] / b[1]));
            say(String.format(LF, "    25N: 扰动RMS(div(V))=%.4e  占扰动RMS(divU) 的 %.1f%%",
                z[3], 100.0 * z[3] / Math.max(1e-30, z[2])));
        }

        say("");
        say("  S1 + S2（物理 Q）：");
        StationaryWave.ENABLED = true;
        StationaryWave.Q_SCALE = 1.0;
        HadleyCell.ENABLED = true;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(sd, cell, th, GRAD);
        double[] b = boxes(sd, cell, th);
        say(String.format(LF, "    亚洲 %.3f  撒哈拉 %.3f  美南 %.3f  北太 %.3f  比 %.3f", b[0], b[1], b[2], b[3], b[0] / b[1]));

        StationaryWave.ENABLED = false;
        HadleyCell.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
