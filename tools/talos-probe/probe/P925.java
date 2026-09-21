package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import java.io.*; import java.util.Locale;

// P925 -- A1 的经验半边：对每个【默认打开】的开关，翻成关闭，看生产量有没有任何变化。
//   零变化的开关 = 嫌疑「被架空」（§592 的缺陷类）。
//   ⚠ 局限（必须记账）：靶量只取 mmPerDay 与 surfaceTemp。只影响海洋/冰/植被的开关
//     在这里看不出变化，不代表它们被架空。
public class P925 {
    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(SEED);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p925_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        long sd = SimTerrain.seedOf(SEED); int cell = PlateField.PLATE_CELL, GR = Zonal.GRAD;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        // 采样点：跨纬度（z）+ 几个 x
        int[] zs = new int[10];
        for (int i = 0; i < 10; i++) zs[i] = WorldContract.zOfLat(-80 + i * 18);
        int[] xs = {40_000, 4_000_000, 8_000_000, 16_000_000, 24_000_000, 32_000_000, 36_000_000};
        sb.append("P925: A1 经验扫描 —— 默认【开】的开关逐个翻成【关】，看生产量是否变化\n");
        sb.append("  靶量：PrecipField.mmPerDay（10 纬 x 7 经 = 70 点，两季共 140 次）+ Atmosphere.surfaceTemp（同点）\n\n");

        double[] base = sample(sd, cell, GR, zs, xs, thS, thW);
        sb.append("  基点采样完成，样本数 = ").append(base.length).append("\n\n");
        sb.append("  %-26s %14s %14s  %s%n".replace("%-26s","  开关").replace("%14s","最大绝对差").replace("%14s","最大相对差").replace("%s","判定"));

        String[][] flags = {
            {"PZREF_IN_V","P"},{"KEEP_EDDY_OUTSIDE","H"},{"BLQ_THETA_E","P"},{"COL_WATER_FROM_TABLE","P"},
            {"EDDY_FULL_DIVERGENCE","P"},{"SHALLOW_FLOOR","P"},{"WZM_ITCZ_SHIFT","P"},{"ZONAL_SL_FROM_TABLE","P"},
            {"CW_SMOOTH","P"},{"EDDY_T_SMOOTH","P"},{"EDDY_SIGMA_T850","P"},{"SEA_ONLY_UZM","Z"},
            {"QRAD_ASR_MINUS_OLR","S"},{"SNOW_FROM_TEMP","T"},
        };
        String[] saved = new String[flags.length];
        for (int i = 0; i < flags.length; i++) {
            String f = flags[i][0];
            try { saved[i] = String.valueOf(getBool(f)); setBool(f, !getBool(f)); }
            catch (Throwable t) { saved[i] = null; }
            if (saved[i] == null) { sb.append(String.format(LF, "  %-24s %14s %14s  %s%n", f, "-", "-", "SKIP(不可翻)")); continue; }
            double[] v = sample(sd, cell, GR, zs, xs, thS, thW);
            double mx = 0, mrel = 0;
            for (int k = 0; k < base.length; k++) {
                double d = Math.abs(v[k] - base[k]);
                if (d > mx) mx = d;
                double den = Math.abs(base[k]);
                if (den > 1e-9) { double r = d / den; if (r > mrel) mrel = r; }
            }
            setBool(f, Boolean.parseBoolean(saved[i]));
            String verdict = (mx == 0.0) ? "** NO EFFECT **" : "有效";
            sb.append(String.format(LF, "  %-24s %14.6g %14.4g  %s%n", f, mx, mrel, verdict));
        }
        sb.append("\n  判读：\n");
        sb.append("   · 'NO EFFECT' 的开关 => 嫌疑被架空，逐个人工地走调用链（静态那一半已给出短路清单）。\n");
        sb.append("   · '有效' 只说明【对这两个靶量】有效；不影响它们的开关要另设靶量。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static double[] sample(long sd, int cell, int GR, int[] zs, int[] xs, double thS, double thW) {
        double[] o = new double[zs.length * xs.length * 2 + zs.length * xs.length];
        int p = 0;
        for (int si = 0; si < 2; si++) {
            double th = (si == 0) ? thS : thW;
            for (int z : zs) for (int x : xs) {
                try { o[p++] = PrecipField.mmPerDay(x, z, sd, cell, th, GR); } catch (Throwable t) { o[p++] = Double.NaN; }
            }
        }
        for (int si = 0; si < 1; si++) {
            for (int z : zs) for (int x : xs) {
                try { o[p++] = Atmosphere.surfaceTemp(x, z, sd, cell, thS); } catch (Throwable t) { o[p++] = Double.NaN; }
            }
        }
        return o;
    }
    static boolean getBool(String n) throws Exception { return PrecipField.class.getField(n).getBoolean(null); }
    static void setBool(String n, boolean v) throws Exception { PrecipField.class.getField(n).setBoolean(null, v); }
}
