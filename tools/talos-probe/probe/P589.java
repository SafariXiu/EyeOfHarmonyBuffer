package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.HadleyCell;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P589 -- S1 端到端：把查表的 W_ZM 换成 Held-Hou 求解值，对降水的影响。
public class P589 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P589] " + s); System.out.println("[P589] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};

    static double[] boxes(long sd, int cell, double th, double[] wBaseOut) {
        double[] out = new double[BOX.length];
        long nw = 0;
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
        if (wBaseOut != null) {
            for (int latd = -85; latd <= 85; latd += 5) for (int c = 0; c < 72; c++) {
                PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), WorldContract.zOfLat(latd), sd, cell, th, GRAD);
                wBaseOut[0] += PrecipField.DIAG.get()[4]; nw++;
            }
            wBaseOut[0] /= nw;
        }
        return out;
    }

    static void row(String tag, double[] r) {
        say(String.format(LF, "  %-26s 亚洲 %6.3f  撒哈拉 %6.3f  美南 %6.3f  北太 %6.3f   亚洲/撒哈拉 %6.3f",
            tag, r[0], r[1], r[2], r[3], r[0] / r[1]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p589_report.txt"), "UTF-8");
        say("P589: S1 端到端（Held-Hou 求解的 W_ZM 替换查表）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        double sc = HadleyCell.selfCheck();
        say("");
        say(String.format(LF, "  [A] HadleyCell.selfCheck() = %.3e  %s", sc, sc < 1e-3 ? "通过 ✓" : "不通过 ✗"));
        say(String.format(LF, "      phi_H(模型T_E) = %.2f°   phi_0 = %.2f°   DeltaT = %.2f K",
            HadleyCell.phiHDeg(), HadleyCell.phiHDeg() / Math.sqrt(5), HadleyCell.deltaTModel()));
        say(String.format(LF, "      原表峰值 %.5f  => 求解形状峰值 %.5f（峰值对齐，只换形状与边界）",
            HadleyCell.tablePeak(), 6.0 * HadleyCell.tablePeak() * HadleyCell.wShape(0.0)));

        double[] wb = new double[1];
        StationaryWave.ENABLED = false;
        HadleyCell.ENABLED = false;
        say("");
        row("OFF（原表 W_ZM）", boxes(sd, cell, th, wb));
        say(String.format(LF, "      <wBase> = %+.6f", wb[0]));

        StationaryWave.ENABLED = false;
        HadleyCell.ENABLED = true;
        wb[0] = 0;
        row("S1 ON（Held-Hou）", boxes(sd, cell, th, wb));
        say(String.format(LF, "      <wBase> = %+.6f", wb[0]));

        StationaryWave.ENABLED = true;
        StationaryWave.CLOSED_LOOP = false;
        StationaryWave.Q_SCALE = 1e-3;
        StationaryWave.invalidate();
        row("S1 ON + S2 ON", boxes(sd, cell, th, null));

        say("");
        say("  [B] wBase 沿纬度剖面（现有表 vs 求解）：");
        say("       lat     原表        求解");
        HadleyCell.ENABLED = false;
        StationaryWave.ENABLED = false;
        for (int latd = 0; latd <= 40; latd += 2) {
            double a = ZonalTables.wZm(latd);
            double b = HadleyCell.wZmSolved(latd);
            say(String.format(LF, "      %4d   %+9.6f   %+9.6f", latd, a, b));
        }
        HadleyCell.ENABLED = false;
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
