package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P692 -- §486【常驻物理门】：P600 的【指名站点】判据（目标第 (4) 项的后半）。
//   为什么用它取代 §399 的全局分桶：每个站点有明确地理身份，不需要全局分桶（§402）。
//   ⚠ 按 §476 的仪器纪律：必须先报每个站点在【本模型世界】里是不是陆地。
public class P692 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P692] " + s); System.out.println("[P692] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final Object[][] SITES = {
        {24.0, -14.0, "西撒哈拉 (加那利)", 0},
        {-23.0, 15.0, "纳米布 (本格拉)", 0},
        {-23.0, -70.0, "阿塔卡马 (秘鲁)", 0},
        {28.0, -114.0, "下加利福尼亚 (加州)", 0},
        {32.0, -83.0, "美国东南 (湾流)", 1},
        {-28.0, -49.0, "巴西南部", 1},
        {-28.0, 152.0, "东澳", 1},
        {34.0, 140.0, "日本", 1}
    };

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p692_report.txt"), "UTF-8");
        say("P692 常驻门：海岸指名站点（P600 判据，§486）—— 物理要求：沙漠组 P < 暖流组 P");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thN = Atmosphere.theta(0.0);                                            // NH 夏（JJA）
        double thS = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);              // SH 夏（DJF）
        // ★ §488 仪器修正：每个站点必须在【它自己的夏季】评。
        //   原版（承 P600）用单一 JJA 评全部 8 站 ⇒ 把 SH 站点的【冬季】当夏季比 ⇒ 季节混淆。
        //   这是仪器缺陷，不是模型缺陷 —— 必须先把仪器修对再判模型。
        say("");
        say("     站点                  | 类别 | 陆/海 | kappa | P(mm/day)");
        double dP = 0, wP = 0; int nd = 0, nw = 0; int landD = 0, landW = 0;
        for (Object[] s : SITES) {
            double lat = (Double) s[0], lon = (Double) s[1];
            int x = xOfLon(lon), z = zOfLat(lat);
            boolean land = PlateField.isLandWithCell(x, z, sd, cell);
            double k = Atmosphere.kappaMemo(x, z, sd, cell);
            double thOwn = (lat >= 0.0) ? thN : thS;                                   // §488 本半球夏季
            double p = PrecipField.mmPerDay(x, z, sd, cell, thOwn, GRAD);
            String cat = ((Integer) s[3]) == 0 ? "沙漠" : "暖流";
            if (((Integer) s[3]) == 0) { dP += p; nd++; if (land) landD++; }
            else { wP += p; nw++; if (land) landW++; }
            say(String.format(LF, "     %-20s | %s | %s | %.3f | %7.3f",
                (String) s[2], cat, land ? "陆" : "**海**", k, p));
        }
        double md = dP / nd, mw = wP / nw;
        say("");
        say(String.format(LF, "     沙漠组 %d 站（其中陆地 %d）：P 均值 %.3f", nd, landD, md));
        say(String.format(LF, "     暖流组 %d 站（其中陆地 %d）：P 均值 %.3f", nw, landW, mw));
        say(String.format(LF, "     GATE_COASTAL_RATIO=%.3f", md / mw));
        say(String.format(LF, "     GATE_COASTAL_LANDFRAC=%d/%d", landD + landW, nd + nw));
        say("     GATE_COASTAL_VERDICT=" + (md < mw ? "PASS" : "FAIL"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}