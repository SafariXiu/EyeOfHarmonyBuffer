package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanWiring;
import java.io.*; import java.util.Locale;

// P927 -- A3：A1 找出的 8 个「隐藏依赖」是【真依赖】还是【又一个 orphaned repair】？
//   做法：先把依赖项打开，再看被依赖的开关翻转有没有影响。
//   若打开依赖后仍然零影响 => 那是第二个孤儿，不是依赖。
public class P927 {
    static final int SEED = 1022228679;
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static long sd; static int cell, GR;
    static int[] zs = new int[10];
    static int[] xs = {40_000, 4_000_000, 8_000_000, 16_000_000, 24_000_000, 32_000_000, 36_000_000};
    static double thS, thW;

    public static void main(String[] a) throws Exception {
        OceanWiring.onWorld(SEED);
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p927_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sd = SimTerrain.seedOf(SEED); cell = PlateField.PLATE_CELL; GR = Zonal.GRAD;
        thS = Atmosphere.theta(0.0); thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        for (int i = 0; i < 10; i++) zs[i] = WorldContract.zOfLat(-80 + i * 18);
        sb.append("P927: A3 —— 8 个隐藏依赖的复核（靶量 210 样本）\n\n");
        sb.append("  依赖项(先开)              被依赖开关        翻转   最大绝对差   判定\n");

        // 1) EDDY_SIGMA_T850 = false  => EDDY_T_SMOOTH 是否仍生效（A3 的核心）
        PrecipField.EDDY_SIGMA_T850 = false;
        chk(sb, "PrecipField.EDDY_SIGMA_T850=false", "PrecipField", "EDDY_T_SMOOTH", true);

        // 2) Q_FROM_SOURCE = true => SOURCE_SEASONAL_T
        PrecipField.Q_FROM_SOURCE = true; SimClimate.clearCache();
        chk(sb, "PrecipField.Q_FROM_SOURCE=true", "PrecipField", "SOURCE_SEASONAL_T", true);
        PrecipField.Q_FROM_SOURCE = false; SimClimate.clearCache();

        // 3) HadleyCell.ENABLED = true => KEEP_EDDY_OUTSIDE
        HadleyCell.ENABLED = true; SimClimate.clearCache();
        chk(sb, "HadleyCell.ENABLED=true", "HadleyCell", "KEEP_EDDY_OUTSIDE", false);
        HadleyCell.ENABLED = false; SimClimate.clearCache();

        // 4~7) StationaryWave.ENABLED = true => 它的四个开关
        StationaryWave.ENABLED = true; SimClimate.clearCache();
        chk(sb, "StationaryWave.ENABLED=true", "StationaryWave", "QRAD_ASR_MINUS_OLR", false);
        chk(sb, "StationaryWave.ENABLED=true", "StationaryWave", "Q_NET_HEATING", true);
        chk(sb, "StationaryWave.ENABLED=true", "StationaryWave", "ZERO_F", true);
        chk(sb, "StationaryWave.ENABLED=true", "StationaryWave", "CLOSED_LOOP", true);
        StationaryWave.ENABLED = false; SimClimate.clearCache();

        // 8) PZREF_VZ_MODE = 0 => PZREF_IN_V
        int savedMode = Atmosphere.PZREF_VZ_MODE;
        Atmosphere.PZREF_VZ_MODE = 0; SimClimate.clearCache();
        chk(sb, "Atmosphere.PZREF_VZ_MODE=0", "Atmosphere", "PZREF_IN_V", false);
        Atmosphere.PZREF_VZ_MODE = savedMode; SimClimate.clearCache();

        sb.append("\n  判读：\n");
        sb.append("   · 有影响 => 该依赖是【真的】，开关在依赖打开时生效。\n");
        sb.append("   · 仍零影响 => 【第二个孤儿】，必须走调用链另找原因。\n");
        sb.append("   ⚠ 靶量仍是 mmPerDay + surfaceTemp；只影响海洋/冰/植被的开关判不了。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }

    static void chk(StringBuilder sb, String dep, String cls, String fld, boolean orig) throws Exception {
        Class<?> c = Class.forName("com.EyeOfHarmonyBuffer.sim." + (cls.equals("PrecipField")||cls.equals("Atmosphere")||cls.equals("HadleyCell")||cls.equals("StationaryWave") ? "atmos." : "ocean.") + cls);
        java.lang.reflect.Field fl = c.getField(fld);
        boolean cur = fl.getBoolean(null);
        if (cur != orig) { fl.setBoolean(null, orig); SimClimate.clearCache(); }
        double[] base = sample();
        fl.setBoolean(null, !orig); SimClimate.clearCache();
        double[] v = sample();
        fl.setBoolean(null, orig); SimClimate.clearCache();
        double mx = 0;
        for (int k = 0; k < base.length; k++) { double d = Math.abs(v[k] - base[k]); if (d > mx) mx = d; }
        sb.append(String.format(LF, "  %-34s %-22s %5s -> %5s %13.6g  %s%n",
            dep, fld, orig, !orig, mx, mx == 0.0 ? "** 仍零影响（第二个孤儿?）**" : "真依赖（生效）"));
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
