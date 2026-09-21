package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import java.io.*; import java.util.Locale;

// P926 -- A1 经验扫描（修正版）：每个开关【两个方向都测】，靶量 mmPerDay + surfaceTemp。
//   P925 的 bug：用 PrecipField.class.getField() 找所有开关 => 住在别的类里的 5 个 SKIP。
//   本版按类解析。并对每次翻转清 SimClimate 缓存。
public class P926 {
    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static long sd; static int cell, GR; static int[] zs = new int[10]; static int[] xs = {40_000, 4_000_000, 8_000_000, 16_000_000, 24_000_000, 32_000_000, 36_000_000};
    static double thS, thW;

    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(SEED);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p926_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sd = SimTerrain.seedOf(SEED); cell = PlateField.PLATE_CELL; GR = Zonal.GRAD;
        thS = Atmosphere.theta(0.0); thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        for (int i = 0; i < 10; i++) zs[i] = WorldContract.zOfLat(-80 + i * 18);
        sb.append("P926: A1 经验扫描（每个开关两个方向）\n");
        sb.append("  靶量：mmPerDay 10 纬 x 7 经 x 2 季 = 140 + surfaceTemp 70 = 210 个样本\n\n");
        double[] base = sample();
        sb.append("  基点 210 样本完成。\n\n");
        sb.append("  开关                          原值  翻转  最大绝对差    最大相对差  判定\n");

        String[][] t = {
            {"PrecipField","BLQ_THETA_E"},{"PrecipField","COL_WATER_FROM_TABLE"},{"PrecipField","EDDY_FULL_DIVERGENCE"},
            {"PrecipField","SHALLOW_FLOOR"},{"PrecipField","WZM_ITCZ_SHIFT"},{"PrecipField","ZONAL_SL_FROM_TABLE"},
            {"PrecipField","CW_SMOOTH"},{"PrecipField","EDDY_T_SMOOTH"},{"PrecipField","EDDY_SIGMA_T850"},
            {"PrecipField","EDDY_MASK_OUTSIDE"},{"PrecipField","EDDY_PLACEMENT_FROM_OBS"},{"PrecipField","BLQ_GATE"},
            {"PrecipField","SPLIT_ASCENT"},{"PrecipField","Q_AT_SURFACE_TEMP"},{"PrecipField","Q_FROM_BLBUDGET"},
            {"PrecipField","Q_FROM_SOURCE"},{"PrecipField","SOURCE_SEASONAL_T"},{"PrecipField","Q_ADVECT_BUDGET"},
            {"PrecipField","WZM_FROM_QNET"},{"PrecipField","WZM_FROM_TABLE"},
            {"Atmosphere","PZREF_IN_V"},{"Atmosphere","COAST_WIND_ON"},{"Atmosphere","LANDS_ANNUAL_IN_PRESSURE"},
            {"Atmosphere","SEASON_SHAPE_FROM_OBS"},{"Atmosphere","SEASON_FROM_HEAT_CAPACITY"},{"Atmosphere","CELL_PHASE_FROM_TEMP"},
            {"Atmosphere","PA_DRY_WARMTH"},{"Atmosphere","PA_NO_CELL"},{"Atmosphere","PA_NO_THERMAL"},
            {"HadleyCell","ENABLED"},{"HadleyCell","KEEP_EDDY_OUTSIDE"},
            {"StationaryWave","ENABLED"},{"StationaryWave","QRAD_ASR_MINUS_OLR"},{"StationaryWave","Q_NET_HEATING"},
            {"StationaryWave","ZERO_F"},{"StationaryWave","CLOSED_LOOP"},
            {"SoilMoisture","ENABLED"},{"Vegetation","ENABLED"},
            {"VerticalColumn","FIXED_TS"},{"Radiation","SKIN_TEMP_FROM_ENERGY_BALANCE"},{"Radiation","BUCKET_BETA"},
            {"ZonalTables","SEA_ONLY_UZM"},{"SurfaceLayer","EKMAN_ENABLED"},{"OceanField","PHASE_SEASONAL"},
            {"SimClimate","AIRT_SEALEVEL"},{"SimTerrain","SNOW_FROM_TEMP"},
        };
        int noEffect = 0, live = 0, skip = 0;
        for (String[] p : t) {
            Class<?> c;
            try { c = Class.forName("com.EyeOfHarmonyBuffer.sim." + (p[0].equals("SimClimate")||p[0].equals("SimTerrain") ? "runtime." : (p[0].equals("ZonalTables")||p[0].equals("Atmosphere")||p[0].equals("HadleyCell")||p[0].equals("StationaryWave")||p[0].equals("SoilMoisture")||p[0].equals("Vegetation")||p[0].equals("VerticalColumn")||p[0].equals("Radiation")||p[0].equals("PrecipField") ? "atmos." : "ocean.")) + p[0]); }
            catch (Throwable e) { sb.append(String.format(LF, "  %-30s  %s%n", p[0]+"."+p[1], "SKIP(类不可达)")); skip++; continue; }
            java.lang.reflect.Field fl;
            try { fl = c.getField(p[1]); } catch (Throwable e) { sb.append(String.format(LF, "  %-30s  %s%n", p[0]+"."+p[1], "SKIP(字段不可达)")); skip++; continue; }
            boolean orig;
            try { orig = fl.getBoolean(null); } catch (Throwable e) { sb.append(String.format(LF, "  %-30s  %s%n", p[0]+"."+p[1], "SKIP(非布尔)")); skip++; continue; }
            double mx, mrel;
            try {
                fl.setBoolean(null, !orig);
                try { SimClimate.clearCache(); } catch (Throwable e) { }
                double[] v = sample();
                mx = 0; mrel = 0;
                for (int k = 0; k < base.length; k++) {
                    double d = Math.abs(v[k] - base[k]);
                    if (d > mx) mx = d;
                    double den = Math.abs(base[k]);
                    if (den > 1e-9) { double r = d / den; if (r > mrel) mrel = r; }
                }
            } finally { try { fl.setBoolean(null, orig); SimClimate.clearCache(); } catch (Throwable e) { } }
            String verdict;
            if (mx == 0.0) { verdict = "** NO EFFECT **"; noEffect++; } else { verdict = "live"; live++; }
            sb.append(String.format(LF, "  %-30s %5s -> %5s %13.6g %13.4g  %s%n", p[0]+"."+p[1], orig, !orig, mx, mrel, verdict));
        }
        sb.append("\n  汇总：live=").append(live).append("  NO_EFFECT=").append(noEffect).append("  SKIP=").append(skip).append("\n");
        sb.append("  判读：NO_EFFECT 的开关在【这两个靶量】上零影响 => 嫌疑被架空；\n");
        sb.append("        但只影响海洋/冰/植被/地形的开关本来就该是零影响 => 必须逐个走调用链区分。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static double[] sample() {
        double[] o = new double[zs.length * xs.length * 2 + zs.length * xs.length];
        int p = 0;
        for (int si = 0; si < 2; si++) {
            double th = (si == 0) ? thS : thW;
            for (int z : zs) for (int x : xs) { try { o[p++] = PrecipField.mmPerDay(x, z, sd, cell, th, GR); } catch (Throwable t) { o[p++] = Double.NaN; } }
        }
        for (int z : zs) for (int x : xs) { try { o[p++] = Atmosphere.surfaceTemp(x, z, sd, cell, thS); } catch (Throwable t) { o[p++] = Double.NaN; } }
        return o;
    }
}
