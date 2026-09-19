package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.atmos.StationaryWave;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P606 -- 净柱加热：撒哈拉与亚洲到底差多少？接进 Q 之后效果如何？
public class P606 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P606] " + s); System.out.println("[P606] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double[] box(long sd, int cell, double th, int b) {
        double[] a = new double[3]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                a[0] += p;
                a[1] += StationaryWave.netColumnHeating(x, z, sd, cell, th, GRAD, p);
                a[2] += PrecipField.DIAG.get()[13];
                n++;
            }
        for (int i = 0; i < 3; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p606_report.txt"), "UTF-8");
        say("P606: 净柱加热 = 潜热 + 感热 + 净辐射");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("");
        say(String.format(LF, "  单层灰体净辐射系数：1-2*EPS = %.4f   (EPS=%.4f)", 1.0 - 2.0 * com.EyeOfHarmonyBuffer.sim.atmos.Radiation.EPS, com.EyeOfHarmonyBuffer.sim.atmos.Radiation.EPS));
        say("");
        say("  [A0] 净柱加热 Q_net（W/m^2）—— beta=1（湿表面）");
        say("      盒子     JJA: P / Q_net / beta        DJF: P / Q_net / beta");
        for (int b = 0; b < BOX.length; b++) {
            double[] jj = box(sd, cell, thS, b), ww = box(sd, cell, thW, b);
            say(String.format(LF, "      %-6s  %7.3f / %+8.1f / %.3f    %7.3f / %+8.1f / %.3f   (观测JJA %.3f)",
                NM[b], jj[0], jj[1], jj[2], ww[0], ww[1], ww[2], OBS[b]));
        }
        say(String.format(LF, "      => 亚洲 - 撒哈拉的 Q_net 差 = %+.1f W/m^2（物理要求：亚洲 > 撒哈拉）",
            box(sd, cell, thS, 0)[1] - box(sd, cell, thS, 1)[1]));

        say("");
        say("  [A1] 同上，但 S3 桶开启（beta 逐点）—— 这才是 §387 说的「H 在 beta~0.3 翻正」的条件");
        SoilMoisture.ENABLED = true;
        SoilMoisture.clearMemo();
        say("      盒子     JJA: P / Q_net / beta        DJF: P / Q_net / beta");
        for (int b = 0; b < BOX.length; b++) {
            double[] jj = box(sd, cell, thS, b), ww = box(sd, cell, thW, b);
            say(String.format(LF, "      %-6s  %7.3f / %+8.1f / %.3f    %7.3f / %+8.1f / %.3f   (观测JJA %.3f)",
                NM[b], jj[0], jj[1], jj[2], ww[0], ww[1], ww[2], OBS[b]));
        }
        double qa1 = box(sd, cell, thS, 0)[1], qs1 = box(sd, cell, thS, 1)[1];
        say(String.format(LF, "      => 亚洲 - 撒哈拉 = %+.1f W/m^2   %s", qa1 - qs1,
            (qa1 - qs1) > 0 ? "符号正确 ✓（亚洲更暖）" : "★ 符号仍反 ★"));
        SoilMoisture.ENABLED = false;
        SoilMoisture.clearMemo();

        say("");
        say("  [B] 接进 Q（Q_NET_HEATING=true, Q_SCALE=1）后的降水 —— S3 也开");
        SoilMoisture.ENABLED = true;
        SoilMoisture.clearMemo();
        StationaryWave.ENABLED = true;
        StationaryWave.CLOSED_LOOP = false;
        StationaryWave.Q_SCALE = 1.0;
        StationaryWave.Q_NET_HEATING = false;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(sd, cell, thS, GRAD);
        for (int b = 0; b < BOX.length; b++)
            say(String.format(LF, "      潜热型  %-6s P=%7.3f  (观测 %.3f)", NM[b], box(sd, cell, thS, b)[0], OBS[b]));
        StationaryWave.Q_NET_HEATING = true;
        StationaryWave.invalidate();
        StationaryWave.ensureSolved(sd, cell, thS, GRAD);
        say(String.format(LF, "      残差 %.2e   forcing_max %.2e", StationaryWave.lastResidual, StationaryWave.lastForcing));
        for (int b = 0; b < BOX.length; b++)
            say(String.format(LF, "      净加热型 %-6s P=%7.3f  (观测 %.3f)", NM[b], box(sd, cell, thS, b)[0], OBS[b]));
        StationaryWave.Q_NET_HEATING = false;
        StationaryWave.ENABLED = false;
        SoilMoisture.ENABLED = false;
        SoilMoisture.clearMemo();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}