package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P694 -- §489: 纳米布(23S/15E) 7.370 vs 阿塔卡马(23S/70W) 0.575 —— 同纬度同季节的两个东岸上升流沙漠。
//   同纬度 ⇒ w_zm 相同 ⇒ 差别只能来自 divU / sstAnom / depl 等【世界几何】量。
public class P694 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P694] " + s); System.out.println("[P694] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static void dump(long sd, int cell, double lat, double lon, String nm) {
        int x = xOfLon(lon), z = zOfLat(lat);
        double th = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);   // SH 夏（DJF）
        double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
        double[] d = PrecipField.DIAG.get();
        double[] u = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
        say("");
        say("  --- " + nm + "  (" + lat + ", " + lon + ") ---   陆=" + PlateField.isLandWithCell(x, z, sd, cell));
        say(String.format(LF, "     P=%.3f  kappa=%.3f  tSl=%.2f  tSfc=%.2f  q=%.5f  depl=%.4f",
            p, d[7], d[6], d[12], d[5], d[11]));
        say(String.format(LF, "     wEff=%+.4e  wBase(w_zm)=%+.4e  wLocal=%+.4e  divU=%+.4e  |V|=%.2f",
            d[3], d[4], d[3] - d[4], d[0], Math.hypot(u[0], u[1])));
        say(String.format(LF, "     主项=%.3f  加涡动=%.3f  地板=%.3f   地表->海平面温差=%.2f K",
            d[8] * 86400 * 1000, d[9] * 86400 * 1000, d[10] * 86400 * 1000, d[6] - d[12]));
        say(String.format(LF, "     sstAnom=%+.3f K   beta=%.3f   w_diag=%+.4e",
            Atmosphere.sstAnom(x, z, th), d[13], d[14]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p694_report.txt"), "UTF-8");
        say("P694: 纳米布 vs 阿塔卡马的逐项对比（§489）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        dump(sd, cell, -23.0, 15.0, "纳米布（本格拉）P=7.370");
        dump(sd, cell, -23.0, -70.0, "阿塔卡马（秘鲁）P=0.575");
        dump(sd, cell, -23.0, 20.0, "纳米布内陆 +5度");
        dump(sd, cell, -23.0, -65.0, "阿塔卡马内陆 +5度");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}