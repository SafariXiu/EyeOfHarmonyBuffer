package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P582 -- E127 修复后：div(V) 的盒子符号到底对不对？Q_SCALE 该标到多少？
// 判据：divU<0 才是辐合。若 div(V)_亚洲 < div(V)_撒哈拉，则 divU += div(V) 方向正确。
public class P582 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P582] " + s); System.out.println("[P582] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲季风区", "撒哈拉", "美国南部", "北太平洋"};

    /** 每盒返回 {P均值, divU原始均值, div(V)均值, wEff均值}。 */
    static double[][] boxes(long sd, int cell, double th) {
        double[][] out = new double[BOX.length][4];
        for (int b = 0; b < BOX.length; b++) {
            long n = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    double p = PrecipField.mmPerDay((int) Math.round((c + 0.5) * CIRC / 72), z, sd, cell, th, GRAD);
                    double[] d = PrecipField.DIAG.get();
                    out[b][0] += p; out[b][1] += d[0]; out[b][2] += d[1]; out[b][3] += d[3];
                    n++;
                }
            }
            for (int q = 0; q < 4; q++) out[b][q] /= n;
        }
        return out;
    }

    static void line(String tag, double[][] r) {
        say(String.format(LF, "  %-16s", tag));
        for (int b = 0; b < BOX.length; b++)
            say(String.format(LF, "    %-6s P=%7.3f  divU.orig=%+.4e  div(V)=%+.4e  divU.new=%+.4e  wEff=%.6f",
                NM[b], r[b][0], r[b][1], r[b][2], r[b][1] + r[b][2], r[b][3]));
        say(String.format(LF, "    亚洲/撒哈拉 = %.3f   （div(V): 亚洲 %+.3e vs 撒哈拉 %+.3e => %s）",
            r[0][0] / r[1][0], r[0][2], r[1][2], r[0][2] < r[1][2] ? "方向正确（亚洲更辐合）" : "方向反了"));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p582_report.txt"), "UTF-8");
        say("P582: div(V) 盒子符号 + Q_SCALE 标定（目标 RMS 与 divU 同量级）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        StationaryWave.ENABLED = false;
        line("OFF（无定常波）", boxes(sd, cell, th));

        // 先量 divU 的自然量级（接线关闭时）
        StationaryWave.ENABLED = false;
        double s2 = 0; long n = 0;
        for (int latd = -80; latd <= 80; latd += 5)
            for (int lon = 0; lon < 360; lon += 10) {
                PrecipField.mmPerDay((int) Math.round(lon / 360.0 * CIRC), WorldContract.zOfLat(latd), sd, cell, th, GRAD);
                double d = PrecipField.DIAG.get()[0];
                s2 += d * d; n++;
            }
        double rmsNat = Math.sqrt(s2 / n);
        say("");
        say(String.format(LF, "  ★ divU 自然量级（OFF，400 点）：RMS = %.4e", rmsNat));
        say(String.format(LF, "     => 要让 div(V) 同量级，Q_SCALE 应使 div RMS ≈ %.2e", rmsNat));

        double[] scales = {1e-5, 3e-5, 1e-4, 3e-4, 1e-3};
        for (double qs : scales) {
            StationaryWave.ENABLED = true;
            StationaryWave.Q_SCALE = qs;
            StationaryWave.invalidate();
            StationaryWave.ensureSolved(sd, cell, th, GRAD);
            say("");
            say(String.format(LF, "Q_SCALE = %.0e", qs));
            line("接线 ON", boxes(sd, cell, th));
        }
        StationaryWave.ENABLED = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
