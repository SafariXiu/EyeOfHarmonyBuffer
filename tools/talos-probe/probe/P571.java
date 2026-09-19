package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P571 -- 第 2 步的 ON 效果实测：S1a / S1a+S3 对瓦片皮温的影响。
 *
 * 读 SimClimate.surfaceTempK（= tSea - GAMMA*elev*kappa），三种配置各扫一遍：
 *   OFF     两个开关都关（= 旧行为）
 *   S1A     只开 SKIN_TEMP_FROM_ENERGY_BALANCE
 *   S1A+S3  两个都开（桶给 beta）
 *
 * 预期（若环未闭合）：桶让亚洲更干、撒哈拉更湿 —— 因为 beta = min(1, P/Ep) 的 P 仍反。
 * 实测确认，不许推断。
 */
public class P571 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P571] " + s); System.out.println("[P571] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static final Object[][] BOXES = {
        {70.0, 120.0, 15.0, 35.0, "亚洲季风区"},
        {0.0,  30.0,  20.0, 35.0, "撒哈拉"},
        {250.0, 285.0, 25.0, 35.0, "美国南部"},
        {150.0, 210.0, 25.0, 35.0, "北太平洋"},
        {300.0, 350.0, 25.0, 35.0, "北大西洋"},
    };

    /** 在某配置下扫五个框的瓦片皮温（K）。 */
    static double[] scan(String tag) {
        SimClimate.clearCache();
        double[] out = new double[BOXES.length];
        for (int b = 0; b < BOXES.length; b++) {
            double lo = (double) BOXES[b][0], hi = (double) BOXES[b][1];
            double la = (double) BOXES[b][2], hb = (double) BOXES[b][3];
            long n = 0; double s = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    s += SimClimate.surfaceTempK(x, z, SEED);
                    n++;
                }
            }
            out[b] = s / n;
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p571_report.txt"), "UTF-8");
        say("P571: 第 2 步 ON 效果 —— S1a / S1a+S3 对瓦片皮温的影响（地球掩膜）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        say("  开关初值: S1a=" + Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE
            + "  S3=" + Radiation.BUCKET_BETA + "   (都必须是 false)");

        Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE = false; Radiation.BUCKET_BETA = false;
        double[] off = scan("OFF");
        Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE = true;  Radiation.BUCKET_BETA = false;
        double[] s1a = scan("S1A");
        Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE = true;  Radiation.BUCKET_BETA = true;
        double[] both = scan("S1A+S3");
        // 复位
        Radiation.SKIN_TEMP_FROM_ENERGY_BALANCE = false; Radiation.BUCKET_BETA = false;

        say("");
        say("  框              OFF(旧)    S1a      S1a+S3    d(S1a-OFF)  d(S1aS3-OFF)");
        for (int b = 0; b < BOXES.length; b++) {
            say(String.format(LF, "  %-12s %8.2f %8.2f %9.2f %11.2f %13.2f",
                BOXES[b][4], off[b], s1a[b], both[b], s1a[b] - off[b], both[b] - off[b]));
        }
        say("");
        say(String.format(LF, "  亚洲-撒哈拉 温差:  OFF %+.2f   S1a %+.2f   S1a+S3 %+.2f",
            off[0] - off[1], s1a[0] - s1a[1], both[0] - both[1]));
        say("");
        say("  判据：若 S1a+S3 让【撒哈拉升得比亚洲多】=> 桶方向正确；若相反 => 环未闭合（预期，需 S2）。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
