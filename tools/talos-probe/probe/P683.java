package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P683 -- §477【常驻物理门】：副热带/季风区降水季节循环的【相位 + 幅度】。
//   目标第 (4) 项：把 §403「副热带沙漠季节循环」判据 + §476 留出集相位判据接进验收套件。
//   为什么相位是最好的防拟合门：它由太阳直射纬度与海陆热力差决定，
//   任何「调一个量级常数」的做法都伪造不出来（§476）。
//   锚 = GPCP v2.3 LTM 1991-2020（refs/gen_holdout_anchors.py）。
public class P683 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P683] " + s); System.out.println("[P683] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }
    // {lon0,lon1,lat0,lat1, 观测 JJA, 观测 DJF, 角色}  —— 角色 0=in-sample 1=holdout
    static final double[][] BX = {
        {70,120,15,35,     7.667, 0.763, 0},
        {0,30,20,35,       0.105, 0.392, 0},
        {130,145,-20,-15,  0.120, 6.371, 1},
        {20,35,-20,-10,    0.076, 6.609, 1}
    };
    static final String[] NM = {"亚洲季风*","撒哈拉*","澳洲季风","非洲南部"};

    static double bm(long sd, int cell, double th, int b) {
        double s = 0; long n = 0;
        for (double latd = BX[b][2] + 2.5; latd <= BX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BX[b][0] || lon > BX[b][1]) continue;
                s += PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                n++;
            }
        return n == 0 ? Double.NaN : s / n;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p683_report.txt"), "UTF-8");
        say("P683 常驻门：降水季节循环的相位 + 幅度（§477）—— * = in-sample，其余为留出集");
        EarthRef.install();
        com.EyeOfHarmonyBuffer.sim.ocean.OceanField.install(SEED);
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("");
        say("     盒子        | 模型 JJA  DJF  | 观测 JJA  DJF  | 相位 | 幅度(模型/观测)");
        int pass = 0, tot = 0;
        int holdPass = 0, holdTot = 0;
        for (int b = 0; b < BX.length; b++) {
            double aj = bm(sd, cell, thS, b), aw = bm(sd, cell, thW, b);
            if (Double.isNaN(aj) || Double.isNaN(aw)) { say(String.format(LF, "     %-10s | NaN（该盒在模型世界里不是陆地）", NM[b])); continue; }
            boolean ph = (aj > aw) == (BX[b][4] > BX[b][5]);
            double ampM = Math.abs(aj - aw), ampO = Math.abs(BX[b][4] - BX[b][5]);
            tot++; if (ph) pass++;
            if (BX[b][6] > 0.5) { holdTot++; if (ph) holdPass++; }
            say(String.format(LF, "     %-10s | %8.3f %6.3f | %8.3f %6.3f | %s | %.3f / %.3f",
                NM[b], aj, aw, BX[b][4], BX[b][5], ph ? " OK " : "**反相**", ampM, ampO));
        }
        say("");
        say("     GATE_PHASE_ALL=" + pass + "/" + tot);
        say("     GATE_PHASE_HOLDOUT=" + holdPass + "/" + holdTot);
        say("     GATE_VERDICT=" + ((pass == tot && holdTot > 0 && holdPass == holdTot) ? "PASS" : "FAIL"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}