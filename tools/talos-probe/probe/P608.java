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

// P608 -- §407 陆海热容对比：振幅/相位对照 + 端到端效果
public class P608 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P608] " + s); System.out.println("[P608] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}, {250, 285, 25, 35}, {150, 210, 25, 35}};
    static final String[] NM = {"亚洲", "撒哈拉", "美南", "北太"};
    static final double[] OBS = {7.775, 0.105, 3.595, 2.402};

    static double box(long sd, int cell, double th, int b) {
        double s = 0; long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                n++;
            }
        return s / n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p608_report.txt"), "UTF-8");
        say("P608: §407 陆海热容对比（平板模型）");
        say(String.format(LF, "  omega = %.4e /s   C_SEA=%.3e   C_LAND=%.3e  beta_land_ref=%.2f",
            Atmosphere.slabPhaseRad(0.0, 0.0) != 0 ? 1.9924e-7 : 1.9924e-7, Atmosphere.C_SEA, Atmosphere.C_LAND, Atmosphere.BETA_LAND_REF));
        say("");
        say("  [A] 振幅与滞后：平板模型 vs 现有表（北半球）");
        say("      纬度 | 表aSea 表aLand 比 | 板aSea 板aLand 比 | 表psiSea 板psiSea  表psiLand 板psiLand");
        for (double latd = 5; latd <= 65; latd += 10) {
            double as = com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.aSea(latd);
            double al = com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables.aLand(latd);
            double bs = Atmosphere.slabAmpK(Math.toRadians(latd), 0.0);
            double bl = Atmosphere.slabAmpK(Math.toRadians(latd), 1.0);
            double w = 2.0 * Math.PI / (WorldContract.DAYS_PER_YEAR * 86400.0);
            double psSea = Atmosphere.PSI_SEA_DAYS * 360.0 / WorldContract.DAYS_PER_YEAR;
            double psLand = Atmosphere.PSI_LAND_DAYS * 360.0 / WorldContract.DAYS_PER_YEAR;
            double pbSea = Math.toDegrees(Atmosphere.slabPhaseRad(Math.toRadians(latd), 0.0));
            double pbLand = Math.toDegrees(Atmosphere.slabPhaseRad(Math.toRadians(latd), 1.0));
            say(String.format(LF, "      %4.0f | %6.2f %6.2f %5.2f | %6.2f %6.2f %5.2f | %7.1f %7.1f  %8.1f %8.1f",
                latd, as, al, al / as, bs, bl, bl / bs, psSea, pbSea, psLand, pbLand));
        }

        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0);
        say("");
        say("  [B] 端到端（地球掩膜 + 地形），JJA");
        say("      配置                        亚洲     撒哈拉   美南     北太     比");
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = false; SoilMoisture.ENABLED = false;
        double[] r0 = new double[4];
        for (int b = 0; b < 4; b++) r0[b] = box(sd, cell, thS, b);
        say(String.format(LF, "      基线                        %6.3f  %6.3f  %6.3f  %6.3f  %6.3f", r0[0], r0[1], r0[2], r0[3], r0[0] / r0[1]));
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = true; SimClimate.clearCache();
        double[] r1 = new double[4];
        for (int b = 0; b < 4; b++) r1[b] = box(sd, cell, thS, b);
        say(String.format(LF, "      +热容季节项                 %6.3f  %6.3f  %6.3f  %6.3f  %6.3f", r1[0], r1[1], r1[2], r1[3], r1[0] / r1[1]));
        SoilMoisture.ENABLED = true; SoilMoisture.clearMemo();
        double[] r2 = new double[4];
        for (int b = 0; b < 4; b++) r2[b] = box(sd, cell, thS, b);
        say(String.format(LF, "      +热容+S3                     %6.3f  %6.3f  %6.3f  %6.3f  %6.3f", r2[0], r2[1], r2[2], r2[3], r2[0] / r2[1]));
        say(String.format(LF, "      观测                        %6.3f  %6.3f  %6.3f  %6.3f  %6.1f", OBS[0], OBS[1], OBS[2], OBS[3], 74.0));
        Atmosphere.SEASON_FROM_HEAT_CAPACITY = false;
        SoilMoisture.ENABLED = false; SoilMoisture.clearMemo();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}