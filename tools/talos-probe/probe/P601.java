package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P601 -- 撒哈拉「恒湿」的归因分解（§402 下一轮第 1 项）。
// 把降水拆成：kappa / tSl / q / depl / wZm(shifted) / 局地项 / wEff / divU / 三个降水项。
public class P601 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P601] " + s); System.out.println("[P601] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    static final double[][] BOX = {{70, 120, 15, 35}, {0, 30, 20, 35}};
    static final String[] NM = {"亚洲", "撒哈拉"};

    /** 分解：返回 13 个量的均值。 */
    static double[] decomp(long sd, int cell, double th, int b) {
        double[] acc = new double[14];
        long n = 0;
        for (double latd = BOX[b][2] + 2.5; latd <= BOX[b][3]; latd += 2.5)
            for (int c = 0; c < 72; c++) {
                double lon = (c + 0.5) * 5.0;
                if (lon < BOX[b][0] || lon > BOX[b][1]) continue;
                int x = xOfLon(lon), z = zOfLat(latd);
                double p = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                double[] d = PrecipField.DIAG.get();
                acc[0] += p;                    // 最终 mm/day
                acc[1] += d[7];                 // kappa
                acc[2] += d[6];                 // tSl
                acc[3] += d[5];                 // q
                acc[4] += d[11];                // depl
                acc[5] += d[4];                 // wBase = wZm(shifted)
                acc[6] += d[3];                 // wEff
                acc[7] += d[0];                 // divU 原始
                acc[8] += d[8] * 86400e3;       // precip 主项 mm/day
                acc[9] += d[9] * 86400e3;       // 加完涡动 mm/day
                acc[10] += d[10] * 86400e3;     // 浅对流地板 mm/day
                acc[11] += d[12];               // 地表温度
                // wZm(shifted) 的 shifted 本身
                double shifted = Math.toDegrees(WorldContract.latOf(z) - 0.0);
                acc[12] += shifted;
                double wLocal = d[3] - d[4];
                acc[13] += wLocal;
                n++;
            }
        for (int i = 0; i < acc.length; i++) acc[i] /= n;
        return acc;
    }

    static void show(String tag, double[] a) {
        say(String.format(LF, "  %s", tag));
        say(String.format(LF, "    kappa=%.3f  tSl=%.2fK  地表T=%.2fK  q=%.6f kg/kg  depl=%.3f",
            a[1], a[2], a[11], a[3], a[4]));
        say(String.format(LF, "    wBase(wZm)=%+.6f  wLocal=%+.6f  wEff=%+.6f m/s   divU=%+.4e",
            a[5], a[13], a[6], a[7]));
        say(String.format(LF, "    主项=%.3f  加涡动=%.3f  浅对流地板=%.3f  => 最终 %.3f mm/day",
            a[8], a[9], a[10], a[0]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p601_report.txt"), "UTF-8");
        say("P601: 撒哈拉「恒湿」归因分解（地球掩膜 + ETOPO1 地形）");
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        say("");
        say(String.format(LF, "  季节项：theta(JJA)=%.4f  theta(DJF)=%.4f", thS, thW));
        say(String.format(LF, "  ITCZ_MIGRATION=%.6f rad (%.1f 度)   W_LOC_MAX=%.4f m/s   H_BL=%.1f",
            PrecipField.ITCZ_MIGRATION, Math.toDegrees(PrecipField.ITCZ_MIGRATION),
            PrecipField.W_LOC_MAX, Atmosphere.H_BL));

        say("");
        say("  【JJA】");
        double[] aJ = decomp(sd, cell, thS, 0); show("亚洲", aJ);
        double[] sJ = decomp(sd, cell, thS, 1); show("撒哈拉", sJ);
        say("");
        say("  【DJF】");
        double[] aW = decomp(sd, cell, thW, 0); show("亚洲", aW);
        double[] sW = decomp(sd, cell, thW, 1); show("撒哈拉", sW);

        say("");
        say("  ★ 季节循环分解（JJA - DJF）");
        say(String.format(LF, "    亚洲  : P %+.3f   wBase %+.6f   wLocal %+.6f   q %+.6f   divU %+.3e",
            aJ[0] - aW[0], aJ[5] - aW[5], aJ[13] - aW[13], aJ[3] - aW[3], aJ[7] - aW[7]));
        say(String.format(LF, "    撒哈拉: P %+.3f   wBase %+.6f   wLocal %+.6f   q %+.6f   divU %+.3e",
            sJ[0] - sW[0], sJ[5] - sW[5], sJ[13] - sW[13], sJ[3] - sW[3], sJ[7] - sW[7]));

        say("");
        say("  ★ 撒哈拉为什么湿？逐项对照（撒哈拉 JJA vs 亚洲 JJA）");
        say(String.format(LF, "    q        : 撒 %.6f  亚 %.6f   比 %.3f   <- 若接近，说明水汽没有区分", sJ[3], aJ[3], sJ[3] / aJ[3]));
        say(String.format(LF, "    kappa    : 撒 %.3f    亚 %.3f", sJ[1], aJ[1]));
        say(String.format(LF, "    地表T    : 撒 %.2f   亚 %.2f", sJ[11], aJ[11]));
        say(String.format(LF, "    wEff     : 撒 %+.6f 亚 %+.6f", sJ[6], aJ[6]));
        say(String.format(LF, "    divU     : 撒 %+.3e 亚 %+.3e", sJ[7], aJ[7]));
        say(String.format(LF, "    地板项   : 撒 %.3f   亚 %.3f   <- SHALLOW_FLOOR 若主导则与 kappa 无关", sJ[10], aJ[10]));
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
