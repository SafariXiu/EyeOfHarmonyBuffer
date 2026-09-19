package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P600 -- 决定性测量：海岸【陆地】侧的 kappa 与降水。
// 假设（P599 推出）：近岸 kappa 低（连续大陆度）=> moisture() 向海洋混合 => 海岸陆地类海洋 => 必然湿。
// 站点 = 四个有名的海岸沙漠（陆侧）vs 四个同纬度暖流区（陆侧）。
public class P600 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P600] " + s); System.out.println("[P600] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    /** 站名, 纬度, 经度（尽量取陆侧）, 期望类别 */
    static final Object[][] SITES = {
        {24.0, -14.0, "西撒哈拉 (加那利)", "沙漠"},
        {-23.0, 15.0, "纳米布 (本格拉)", "沙漠"},
        {-23.0, -70.0, "阿塔卡马 (秘鲁)", "沙漠"},
        {28.0, -114.0, "下加利福尼亚 (加州)", "沙漠"},
        {32.0, -83.0, "美国东南 (湾流)", "暖流"},
        {-28.0, -49.0, "巴西南部", "暖流"},
        {-28.0, 152.0, "东澳", "暖流"},
        {34.0, 140.0, "日本", "暖流"},
    };

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p600_report.txt"), "UTF-8");
        say("P600: 海岸【陆地】侧的 kappa 与降水（海岸沙漠 vs 暖流区）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);

        say("");
        say("  站点                    纬度    经度    kappa   eastness   P(mm/day)  类别");
        double dk = 0, dp = 0, dke = 0, dpe = 0; long nd = 0, nw = 0;
        for (Object[] s : SITES) {
            double lat = (double) s[0], lon = (double) s[1];
            int x = xOfLon(lon), z = zOfLat(lat);
            double k = Atmosphere.kappaMemo(x, z, sd, cell);
            double e = Atmosphere.eastness(x, z, sd, cell);
            double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
            say(String.format(LF, "  %-22s  %5.1f  %7.1f   %.3f   %+.3f    %7.3f   %s",
                (String) s[2], lat, lon, k, e, p, (String) s[3]));
            if ("沙漠".equals(s[3])) { dk += k; dp += p; dke += e; nd++; }
            else { dke += e; dpe += p; dk = dk; nw++; }
        }
        // 暖流组单独累计
        double wk = 0, wp = 0;
        for (Object[] s : SITES) {
            if (!"暖流".equals(s[3])) continue;
            int x = xOfLon((double) s[1]), z = zOfLat((double) s[0]);
            wk += Atmosphere.kappaMemo(x, z, sd, cell);
            wp += PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
        }
        say("");
        say(String.format(LF, "  沙漠组（4 站）：kappa 均值 %.3f   P 均值 %.3f   eastness 均值 %+.3f",
            dk / nd, dp / nd, dke / nd));
        say(String.format(LF, "  暖流组（4 站）：kappa 均值 %.3f   P 均值 %.3f   eastness 均值 %+.3f",
            wk / nw, wp / nw, dke / nw));
        say("");
        say(String.format(LF, "  ★ 物理要求：沙漠组 P < 暖流组 P。实测 %.3f vs %.3f  比值 %.3f  => %s",
            dp / nd, wp / nw, (dp / nd) / (wp / nw), (dp / nd) < (wp / nw) ? "通过 ✓" : "★ 违反物理 ★"));
        say(String.format(LF, "  ★ 假设检验：沙漠组的 kappa 是否偏低（近岸 => 类海洋 => 必然湿）？%.3f vs %.3f  => %s",
            dk / nd, wk / nw, (dk / nd) < (wk / nw) ? "沙漠组 kappa 更低（假设成立方向）" : "沙漠组 kappa 不低（假设不成立）"));
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
