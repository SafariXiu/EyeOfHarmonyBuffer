package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P605 -- S3 土壤湿度桶（周期稳态解）首次实测。
public class P605 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P605] " + s); System.out.println("[P605] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    /** {P, beta, q} */
    static double[] box(long sd, int cell, double th, int b) {
        double[] a = new double[3]; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                a[0] += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                a[1] += d[13]; a[2] += d[5]; n++;
            }
        for (int i = 0; i < 3; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p605_report.txt"), "UTF-8");
        say("P605: S3 土壤湿度桶（dW/dt = P - E - R 的周期稳态解）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        SoilMoisture.ENABLED = false;
        say("");
        say("  [A] OFF（全球常数 RH = 0.80）");
        say("      盒子    JJA(P / beta / q)              DJF(P / beta / q)");
        for (int b = 0; b < BOX.length; b++) {
            double[] j = box(sd, cell, thS, b), w = box(sd, cell, thW, b);
            say(String.format(LF, "      %-6s  %7.3f / %.3f / %.6f    %7.3f / %.3f / %.6f   (观测JJA %.3f)",
                NM[b], j[0], j[1], j[2], w[0], w[1], w[2], OBS[b]));
        }

        SoilMoisture.ENABLED = true;
        SoilMoisture.clearMemo();
        long t0 = System.nanoTime();
        say("");
        say("  [B] ON（S3 桶，周期稳态）");
        say("      盒子    JJA(P / beta / q)              DJF(P / beta / q)");
        for (int b = 0; b < BOX.length; b++) {
            double[] j = box(sd, cell, thS, b), w = box(sd, cell, thW, b);
            say(String.format(LF, "      %-6s  %7.3f / %.3f / %.6f    %7.3f / %.3f / %.6f   (观测JJA %.3f)",
                NM[b], j[0], j[1], j[2], w[0], w[1], w[2], OBS[b]));
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        say("");
        say(String.format(LF, "      自旋：列数 %d  年数(末次) %.0f  末次残差 %.3e  耗时 %d ms  每次自旋均值 %.1f ms",
            SoilMoisture.spinupCount, SoilMoisture.lastSpinYears, SoilMoisture.lastSpinResid, ms,
            ms / Math.max(1.0, SoilMoisture.spinupCount)));
        say(String.format(LF, "      评估次数 %d   自旋总耗时 %d ms", SoilMoisture.evalCount, SoilMoisture.SPINUP_NANOS / 1_000_000));

        say("");
        say("  [B2] 环流侧也开（CELL_PHASE_FROM_TEMP）");
        Atmosphere.CELL_PHASE_FROM_TEMP = true;
        Atmosphere.CELL_MIG_SENS = 0.0;
        double mJ = Atmosphere.cellMigrationDegHardcoded(0.0);
        double mW = Atmosphere.cellMigrationDegHardcoded(Math.PI);
        double dT25J = Atmosphere.landSeaTempContrast(Math.toRadians(25.0), 0.0);
        double dT25W = Atmosphere.landSeaTempContrast(Math.toRadians(25.0), Math.PI);
        double sens = (Math.abs(dT25J) < 1e-6) ? 0.0 : mJ / dT25J;
        say(String.format(LF, "      25N 陆海温差：JJA %+.2f K   DJF %+.2f K", dT25J, dT25W));
        say(String.format(LF, "      迁移纬度  旧式：JJA %+.2f 度  DJF %+.2f 度", mJ, mW));
        say(String.format(LF, "      迁移纬度  新式：JJA %+.2f 度  DJF %+.2f 度  (自标定 sens=%.5f 度/K)",
            sens * dT25J, sens * dT25W, sens));
        SoilMoisture.clearMemo();
        for (int b = 0; b < BOX.length; b++) {
            double[] j = box(sd, cell, thS, b);
            say(String.format(LF, "      %-6s  JJA P=%7.3f  (观测 %.3f)", NM[b], j[0], OBS[b]));
        }
        Atmosphere.CELL_PHASE_FROM_TEMP = false;
        Atmosphere.CELL_MIG_SENS = 0.0;
        SoilMoisture.clearMemo();

        say("");
        say("  [B3] wZm 不再跟 ITCZ 平移（WZM_ITCZ_SHIFT=false）");
        PrecipField.WZM_ITCZ_SHIFT = false;
        SoilMoisture.clearMemo();
        for (int b = 0; b < BOX.length; b++) {
            double[] j = box(sd, cell, thS, b);
            say(String.format(LF, "      %-6s  JJA P=%7.3f  (观测 %.3f)", NM[b], j[0], OBS[b]));
        }
        say("");
        say("  [B4] B3 + 环流位相也由温度驱动");
        Atmosphere.CELL_PHASE_FROM_TEMP = true;
        Atmosphere.CELL_MIG_SENS = 0.0;
        SoilMoisture.clearMemo();
        for (int b = 0; b < BOX.length; b++) {
            double[] j = box(sd, cell, thS, b);
            say(String.format(LF, "      %-6s  JJA P=%7.3f  (观测 %.3f)  beta=%.3f", NM[b], j[0], OBS[b], j[1]));
        }
        Atmosphere.CELL_PHASE_FROM_TEMP = false;
        Atmosphere.CELL_MIG_SENS = 0.0;
        PrecipField.WZM_ITCZ_SHIFT = true;
        SoilMoisture.clearMemo();

        say("");
        say("  [C] 关键对照：S3 是否让撒哈拉变干、亚洲保持湿");
        SoilMoisture.ENABLED = false;
        double[] a0 = box(sd, cell, thS, 0), s0 = box(sd, cell, thS, 1);
        SoilMoisture.ENABLED = true; SoilMoisture.clearMemo();
        double[] a1 = box(sd, cell, thS, 0), s1 = box(sd, cell, thS, 1);
        say(String.format(LF, "      亚洲  JJA: %.3f -> %.3f  (观测 7.775)   beta %.3f -> %.3f",
            a0[0], a1[0], a0[1], a1[1]));
        say(String.format(LF, "      撒哈拉 JJA: %.3f -> %.3f  (观测 0.105)   beta %.3f -> %.3f",
            s0[0], s1[0], s0[1], s1[1]));
        say(String.format(LF, "      比值: OFF %.3f -> ON %.3f   (观测 74.0)",
            a0[0] / s0[0], a1[0] / s1[0]));
        SoilMoisture.ENABLED = false;
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
