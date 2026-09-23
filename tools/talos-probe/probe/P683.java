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

    /** ★ §719：单条纬线的盒均值（定位地板绑在哪个子带）。 */
    static double bmLat(long sd, int cell, double th, int b, double latd) {
        int z = zOfLat(latd);
        double s = 0; long n = 0;
        for (int c = 0; c < 72; c++) {
            double lon = (c + 0.5) * 5.0;
            if (lon < BX[b][0] || lon > BX[b][1]) continue;
            s += PrecipField.mmPerDay(xOfLon(lon), z, sd, cell, th, GRAD);
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
        // ★ §718 量测（纯新增，不进判据）：浅对流地板对每个盒子的分季贡献。
        //   做法：同一盒子在 SHALLOW_CONDENSATE=true/false 下各算一次，差即地板贡献。
        //   为什么不读 DIAG[10]：它只在诊断重载里写，而 mmPerDay(6 参) 不暴露地板前的 p。
        say("");
        say("D. §718 浅对流地板的分季贡献（同一盒子，地板开/关各算一次）");
        say(String.format(LF, "     %-10s | %8s %8s %8s | %8s %8s %8s",
            "盒子", "JJA开", "JJA关", "dJJA", "DJF开", "DJF关", "dDJF"));
        boolean savedSC = PrecipField.SHALLOW_CONDENSATE;
        for (int b = 0; b < BX.length; b++) {
            PrecipField.SHALLOW_CONDENSATE = true;  SimClimate.clearCache();
            double jOn = bm(sd, cell, thS, b), wOn = bm(sd, cell, thW, b);
            PrecipField.SHALLOW_CONDENSATE = false; SimClimate.clearCache();
            double jOff = bm(sd, cell, thS, b), wOff = bm(sd, cell, thW, b);
            say(String.format(LF, "     %-10s | %8.3f %8.3f %8.3f | %8.3f %8.3f %8.3f",
                NM[b], jOn, jOff, jOn - jOff, wOn, wOff, wOn - wOff));
        }
        PrecipField.SHALLOW_CONDENSATE = savedSC; SimClimate.clearCache();
        // ★ §719 纯新增：撒哈拉盒内逐纬度的地板贡献。
        say("");
        say("E. §719 撒哈拉盒 (0~30E,20~35N) 内【逐纬度】的地板贡献");
        say(String.format(LF, "     %-7s %10s %10s %10s | %10s %10s %10s", "latN", "JJA开", "JJA关", "dJJA", "DJF开", "DJF关", "dDJF"));
        double[] la = new double[8], j1 = new double[8], j0 = new double[8], w1 = new double[8], w0 = new double[8];
        int nL = 0;
        PrecipField.SHALLOW_CONDENSATE = true; SimClimate.clearCache();
        for (double latd = 20.0; latd <= 35.0; latd += 2.5) { la[nL] = latd; j1[nL] = bmLat(sd, cell, thS, 1, latd); w1[nL] = bmLat(sd, cell, thW, 1, latd); nL++; }
        PrecipField.SHALLOW_CONDENSATE = false; SimClimate.clearCache();
        for (int i = 0; i < nL; i++) { j0[i] = bmLat(sd, cell, thS, 1, la[i]); w0[i] = bmLat(sd, cell, thW, 1, la[i]); }
        PrecipField.SHALLOW_CONDENSATE = savedSC; SimClimate.clearCache();
        for (int i = 0; i < nL; i++)
            say(String.format(LF, "     %-7.1f %10.3f %10.3f %10.3f | %10.3f %10.3f %10.3f",
                la[i], j1[i], j0[i], j1[i] - j0[i], w1[i], w0[i], w1[i] - w0[i]));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}