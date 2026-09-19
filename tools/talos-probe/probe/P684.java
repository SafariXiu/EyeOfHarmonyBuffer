package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P684 -- §478: 相位门唯一失败项（撒哈拉）的逐项分解。
//   §403 当年量到 DJF 撒哈拉 主项=0.314 而 加涡动=3.143（涡动 +2.83 = 主项的 9 倍）
//   ⇒ 怀疑「撒哈拉 DJF 湿」是【涡动项的伪影】。直接对应目标第 (3) 项。
public class P684 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P684] " + s); System.out.println("[P684] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BX = {{70,120,15,35},{0,30,20,35},{130,145,-20,-15},{20,35,-20,-10}};
    static final String[] NM = {"亚洲季风","撒哈拉","澳洲季风","非洲南部"};

    static double[] bm(long sd, int cell, double th, int b) {
        double[] a = new double[7]; long n = 0;
        for (double latd = BX[b][2] + 2.5; latd <= BX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BX[b][0] || lon > BX[b][1]) continue;
                double pm = PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                a[0] += d[8] * 86400.0 * 1000.0;
                a[1] += d[9] * 86400.0 * 1000.0;
                a[2] += d[10] * 86400.0 * 1000.0;
                a[3] += pm; a[4] += d[3]; a[5] += d[0];
                a[6] += Math.abs(PrecipField.eddyMfc(WorldContract.latOf(zOfLat(latd)), th));
                n++;
            }
        if (n == 0) { for (int i = 0; i < 7; i++) a[i] = Double.NaN; return a; }
        for (int i = 0; i < 7; i++) a[i] /= n;
        return a;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p684_report.txt"), "UTF-8");
        say("P684: 相位门失败项的逐项分解（§478）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");
        say("     盒子        | 季节 |   主项   加涡动   地板  |  终值 | wEff(m/s)  divU(1/s)   |mfc|     涡动增量");
        for (int b = 0; b < 4; b++) {
            double[] j = bm(sd, cell, thS, b), w = bm(sd, cell, thW, b);
            if (Double.isNaN(j[0])) { say(String.format(LF, "     %-10s | 该盒在模型世界里不是陆地", NM[b])); continue; }
            say(String.format(LF, "     %-10s | JJA  | %7.3f %8.3f %6.3f | %6.3f | %+9.2e %+10.2e %.3e %+8.3f",
                NM[b], j[0], j[1], j[2], j[3], j[4], j[5], j[6], j[1] - j[0]));
            say(String.format(LF, "     %-10s | DJF  | %7.3f %8.3f %6.3f | %6.3f | %+9.2e %+10.2e %.3e %+8.3f",
                NM[b], w[0], w[1], w[2], w[3], w[4], w[5], w[6], w[1] - w[0]));
        }
        say("");
        say("判据：若某盒的【涡动增量】远大于主项本身，则该盒的降水源自涡动项伪影（目标第 (3) 项）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}