package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.SoilMoisture;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P620 -- 常驻物理门 v2：把「均值比较」换成【纬度配对】（一个异常点无法翻转判据）。
// beta 判据在 S3 关时标「不适用」而不是「不通过」。
public class P620 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P620] " + s); System.out.println("[P620] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static int nPass = 0, nTotal = 0, nNA = 0;
    static void gate(String name, Boolean ok, String detail) {
        if (ok == null) { nNA++; say(String.format(LF, "  [不适用] %-32s %s", name, detail)); return; }
        nTotal++; if (ok) nPass++;
        say(String.format(LF, "  [%s] %-32s %s", ok ? "通过" : "不通过", name, detail));
    }

    /** 纬度配对：{latW, lonW, nameW, latE, lonE, nameE}，西岸应当比东岸干（副热带）或湿（中纬）。 */
    static final Object[][] PAIRS_SUB = {
        {24.0, -14.0, "西撒哈拉", 25.0, -80.0, "迈阿密"},
        {-23.0, 15.0, "纳米布", -23.0, -43.0, "里约"},
        {-23.0, -70.0, "阿塔卡马", -25.0, 153.0, "弗雷泽岛"},
    };
    static final Object[][] PAIRS_MID = {
        {47.0, -124.0, "太平洋西北", 44.0, -70.0, "新英格兰"},
        {-45.0, -74.0, "智利南部", -45.0, -67.0, "阿根廷"},
        {52.0, -4.0, "不列颠", 43.0, 141.0, "日本北部"},
    };

    /** 返回 {西岸均值, 东岸均值, 西<东的配对数, 总配对数} */
    static double[] pairs(long sd, int cell, double th, Object[][] P) {
        double sw = 0, se = 0; int okW = 0, n = 0;
        StringBuilder sb = new StringBuilder();
        for (Object[] p : P) {
            double pw = PrecipField.mmPerDay(xOfLon((double) p[1]), zOfLat((double) p[0]), sd, cell, th, GRAD);
            double pe = PrecipField.mmPerDay(xOfLon((double) p[4]), zOfLat((double) p[3]), sd, cell, th, GRAD);
            sw += pw; se += pe; n++;
            if (pw < pe) okW++;
            sb.append(String.format(LF, "%s %.2f/%.2f  ", (String) p[2], pw, pe));
        }
        say("        " + sb.toString());
        return new double[]{sw / n, se / n, okW, n};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p620_report.txt"), "UTF-8");
        say("P620: 常驻物理门 v2（纬度配对 + beta 判据的适用性）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("");
        say("  --- 生产默认态 ---");
        say("  副热带配对（西岸应更干）：");
        double[] sub = pairs(sd, cell, th, PAIRS_SUB);
        gate("副热带 西岸 < 东岸（>=2/3 配对）", sub[2] >= 2.0,
             String.format(LF, "%d/%d 配对满足", (int) sub[2], (int) sub[3]));
        say("  中纬配对（西岸应更湿）：");
        double[] mid = pairs(sd, cell, th, PAIRS_MID);
        gate("中纬 西岸 > 东岸（>=2/3 配对）", (mid[3] - mid[2]) >= 2.0,
             String.format(LF, "%d/%d 配对满足", (int) (mid[3] - mid[2]), (int) mid[3]));

        // beta 判据：S3 关时标不适用
        double bDesert = betaBox(sd, cell, th, 0, 30, 20, 35);
        double bMonsoon = betaBox(sd, cell, th, 70, 120, 15, 35);
        gate("beta 沙漠 <= 0.1 且 季风 >= 0.8（§387）",
             SoilMoisture.ENABLED ? (bDesert <= 0.1 && bMonsoon >= 0.8) : null,
             String.format(LF, "沙漠 %.3f  季风 %.3f（S3 %s）", bDesert, bMonsoon, SoilMoisture.ENABLED ? "开" : "关"));
        say(String.format(LF, "  === 生产默认态：%d/%d 通过，%d 不适用 ===", nPass, nTotal, nNA));

        say("");
        say("  --- 全栈组合（S3 + 干暖 + 无平移）---");
        nPass = 0; nTotal = 0; nNA = 0;
        SoilMoisture.ENABLED = true; SoilMoisture.clearMemo();
        Atmosphere.PA_DRY_WARMTH = true;
        PrecipField.WZM_ITCZ_SHIFT = false;
        SimClimate.clearCache();
        say("  副热带配对（西岸应更干）：");
        double[] sub2 = pairs(sd, cell, th, PAIRS_SUB);
        gate("副热带 西岸 < 东岸（>=2/3 配对）", sub2[2] >= 2.0,
             String.format(LF, "%d/%d 配对满足", (int) sub2[2], (int) sub2[3]));
        say("  中纬配对（西岸应更湿）：");
        double[] mid2 = pairs(sd, cell, th, PAIRS_MID);
        gate("中纬 西岸 > 东岸（>=2/3 配对）", (mid2[3] - mid2[2]) >= 2.0,
             String.format(LF, "%d/%d 配对满足", (int) (mid2[3] - mid2[2]), (int) mid2[3]));
        double bd2 = betaBox(sd, cell, th, 0, 30, 20, 35), bm2 = betaBox(sd, cell, th, 70, 120, 15, 35);
        gate("beta 沙漠 <= 0.1 且 季风 >= 0.8（§387）", (bd2 <= 0.1 && bm2 >= 0.8),
             String.format(LF, "沙漠 %.3f  季风 %.3f", bd2, bm2));
        say(String.format(LF, "  === 全栈组合：%d/%d 通过，%d 不适用 ===", nPass, nTotal, nNA));

        SoilMoisture.ENABLED = false; Atmosphere.PA_DRY_WARMTH = false;
        PrecipField.WZM_ITCZ_SHIFT = true; SoilMoisture.clearMemo();
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }

    static double betaBox(long sd, int cell, double th, double lonLo, double lonHi, double latLo, double latHi) {
        double s = 0; long n = 0;
        for (double latd = latLo + 2.5; latd <= latHi; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < lonLo || lon > lonHi) continue;
                PrecipField.mmPerDay(xOfLon(lon), zOfLat(latd), sd, cell, th, GRAD);
                s += PrecipField.DIAG.get()[13]; n++;
            }
        return s / n;
    }
}
