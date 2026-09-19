package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P651 -- §438 选项 D 的 go/no-go：地形坡度到底分不分得开沙漠与季风区，方向对不对。
// 物理预期（我的担忧）：撒哈拉平坦、喜马拉雅/印度陡峭；而「陡坡径流大 => 更干」
// 会把季风区判得更干 —— 与需要【相反】。本探针先量，不实现。
public class P651 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P651] " + s); System.out.println("[P651] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final double STEP = 50_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲  ", "撒哈拉"};

    static double elev(int x, int z, long sd, int cell) { return PlateField.elevationWithCell(x, z, sd, cell); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p651_report.txt"), "UTF-8");
        say("P651: 地形坡度 vs 沙漠/季风（模型自己的高程场）");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        say("");
        say("     盒子   |  平均高程(m)   平均坡度     坡度中位数   坡度>0.5% 占比   坡度>2% 占比   |  降水(mm/day) JJA/DJF");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        for (int b = 0; b < 2; b++) {
            double se = 0, ss = 0, sp = 0, n = 0;
            java.util.List<Double> sl = new java.util.ArrayList<Double>();
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    int x = xOfLon(lon), z = zOfLat(latd);
                    double h = elev(x, z, sd, cell);
                    double hx = (elev(x + (int) STEP, z, sd, cell) - elev(x - (int) STEP, z, sd, cell)) / (2 * STEP);
                    double hz = (elev(x, z + (int) STEP, sd, cell) - elev(x, z - (int) STEP, sd, cell)) / (2 * STEP);
                    double s = Math.hypot(hx, hz);
                    se += h; ss += s; sl.add(s); n++;
                }
            java.util.Collections.sort(sl);
            double med = sl.get(sl.size() / 2);
            int g5 = 0, g20 = 0;
            for (double v : sl) { if (v > 0.005) g5++; if (v > 0.02) g20++; }
            double pj = 0, pw = 0; long m = 0;
            for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 5.0;
                    if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                    int x = xOfLon(lon), z = zOfLat(latd);
                    pj += PrecipField.mmPerDay(x, z, sd, cell, thS, 500_000);
                    pw += PrecipField.mmPerDay(x, z, sd, cell, thW, 500_000);
                    m++;
                }
            say(String.format(LF, "     %s | %10.0f   %9.5f   %9.5f   %11.1f%%   %10.1f%%   |  %.3f / %.3f",
                NM[b], se / n, ss / n, med, 100.0 * g5 / n, 100.0 * g20 / n, pj / m, pw / m));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
