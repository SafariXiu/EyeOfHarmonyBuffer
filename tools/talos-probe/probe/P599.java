package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P599 -- 仪器审计：不看全局平均，直接在地球上【有名的】那几个站看。
// 物理（与地球数值无关，只看"机制在不在对的地方"）：
//   副热带大陆西岸 = 洋东界 = 冷上升流（加那利/加利福尼亚/秘鲁/本格拉）=> 必须【干】
//   副热带大陆东岸 = 洋西界 = 暖流（湾流/黑潮/巴西/东澳）            => 必须【湿】
// 为什么必须这样测：P598 的全局分桶在地球上给了反号，而地球岸线远比随机行星破碎
//   （印尼/菲律宾/爱琴海），分桶很可能把半岛与岛屿误判成"西岸" => 先审计仪器本身。
public class P599 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P599] " + s); System.out.println("[P599] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    /** 站名, 纬度, 经度, 期望的 eastness 符号(+1 洋东界/大陆西岸, -1 洋西界/大陆东岸) */
    static final Object[][] SITES = {
        {22.0, -18.0, "加那利 (NW 非洲)", +1},
        {35.0, -123.0, "加利福尼亚", +1},
        {-12.0, -78.0, "秘鲁/洪堡", +1},
        {-25.0, 13.0, "本格拉 (纳米布)", +1},
        {28.0, -82.0, "佛罗里达/湾流", -1},
        {33.0, 138.0, "日本南岸/黑潮", -1},
        {-25.0, -45.0, "巴西 (桑托斯)", -1},
        {-28.0, 154.0, "东澳 (布里斯班)", -1},
    };

    /** ±1.5 度邻域平均。 */
    static double[] site(long sd, int cell, double th, double lat, double lon) {
        long n = 0, nL = 0; double sp = 0, se = 0, seAbs = 0;
        for (double dla = -1.5; dla <= 1.5; dla += 0.75)
            for (double dlo = -1.5; dlo <= 1.5; dlo += 0.75) {
                int x = xOfLon(lon + dlo), z = zOfLat(lat + dla);
                double e = Atmosphere.eastness(x, z, sd, cell);
                sp += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                se += e; seAbs += Math.abs(e);
                if (Atmosphere.kappaMemo(x, z, sd, cell) > 0.5) nL++;
                n++;
            }
        return new double[]{sp / n, se / n, seAbs / n, (double) nL / n};
    }

    static void group(String tag, long sd, int cell, double th, int want) {
        say("  " + tag);
        double sum = 0; int cnt = 0; int okSign = 0;
        for (Object[] s : SITES) {
            if ((int) s[3] != want) continue;
            double[] r = site(sd, cell, th, (double) s[0], (double) s[1]);
            boolean signOk = (want > 0) ? r[1] > 0 : r[1] < 0;
            if (signOk) okSign++;
            sum += r[0]; cnt++;
            say(String.format(LF, "    %-20s P=%6.3f  eastness=%+.3f  |e|均=%.3f  陆占比=%4.0f%%  %s",
                (String) s[2], r[0], r[1], r[2], r[3] * 100,
                signOk ? "eastness 符号对 ✓" : "eastness 符号反 ✗"));
        }
        say(String.format(LF, "    => 均值 P = %.3f    eastness 符号正确 %d/%d", sum / cnt, okSign, cnt));
        LAST = sum / cnt;
    }
    static double LAST = 0;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p599_report.txt"), "UTF-8");
        say("P599: 仪器审计 —— 在地球的四个上升流区与四个暖流区直接取样");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("");
        say("  物理要求（与地球数值无关，只看机制在不在对的地方）：");
        say("    副热带大陆西岸(洋东界, 冷上升流) 必须【干】");
        say("    副热带大陆东岸(洋西界, 暖流)     必须【湿】");
        say("");
        group("A. 冷上升流组（大陆西岸，期望 eastness > 0）", sd, cell, th, +1);
        double cold = LAST;
        say("");
        group("B. 暖流组（大陆东岸，期望 eastness < 0）", sd, cell, th, -1);
        double warm = LAST;
        say("");
        say(String.format(LF, "  ★ 判据：冷上升流组必须比暖流组干。实测 %.3f vs %.3f  比值 %.3f  => %s",
            cold, warm, cold / warm, cold < warm ? "通过 ✓" : "★ 违反物理 ★"));
        say("");
        say("  若此判据也失败，则 P598 的全局分桶反号就不是仪器假象，而是真缺陷。");
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
