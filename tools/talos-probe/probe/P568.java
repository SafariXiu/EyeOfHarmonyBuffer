package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P568 -- S2 的【强迫场】可行性：非绝热加热 Q 有没有正确的亚洲/撒哈拉对比？
 *
 * §352：夏季副热带是【加热】压倒地形 => S2 必须用非绝热加热强迫，不是地形高度。
 * 本探针在地球掩膜上算 Q = H + LE（地表进入大气的感热+潜热），并把两者【分开报】：
 *   - LE（潜热）驱动【深对流】—— 这才是季风的引擎
 *   - H （感热）只驱动【浅热低压】—— 沙漠热但不深对流
 * 判据：若 LE(亚洲) >> LE(撒哈拉) => 强迫场是对的，S2 有正确的输入；
 *       若两者相近或反了 => S2 的强迫也是错的，问题更深。
 */
public class P568 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P568] " + s); System.out.println("[P568] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p568_report.txt"), "UTF-8");
        say("P568: S2 的强迫场 —— 非绝热加热 Q = H + LE 的逐框对比（地球掩膜，JJA）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        say("  dec = " + String.format(LF, "%.2f deg", Math.toDegrees(dec)));

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区"},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉"},
            {250.0, 285.0, 25.0, 35.0, "美国南部"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋"},
            {300.0, 350.0, 25.0, 35.0, "北大西洋"},
        };
        say("");
        say("  框            陆占比   吸收      Ta     Ts(皮温)   H(W/m2)   LE(W/m2)   Q=H+LE    LE/Q");
        double leAsia = -1, leSah = -1, qAsia = -1, qSah = -1;
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0, nLand = 0;
            double sAbs = 0, sTa = 0, sTs = 0, sH = 0, sLE = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                    double[] dg = new double[8];
                    P546.decompose(x, z, sd, cell, th, dg);
                    double qa = dg[1];
                    double ta = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    double sp = Math.hypot(u0[0], u0[1]);
                    double chv = Radiation.bulkCoeff(k, sp);
                    double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                    double absS = (1.0 - alb) * Radiation.insolation(lat, dec);
                    double ts = Radiation.skinTempLand(absS, ta, qa, chv);
                    double hh = chv * Radiation.CP * (ts - ta);
                    double le = chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
                    sAbs += absS; sTa += ta; sTs += ts; sH += hh; sLE += le;
                    if (P546.earthIsLand(x, z)) nLand++;
                    n++;
                }
            }
            double h = sH / n, le = sLE / n, q = h + le;
            if (bx[4].equals("亚洲季风区")) { leAsia = le; qAsia = q; }
            if (bx[4].equals("撒哈拉"))     { leSah = le; qSah = q; }
            say(String.format(LF, "  %-12s %5.1f%% %8.1f %8.1f %9.1f %9.1f %10.1f %9.1f %7.3f",
                bx[4], 100.0 * nLand / n, sAbs / n, sTa / n, sTs / n, h, le, q, q != 0 ? le / q : 0));
        }
        say("");
        say(String.format(LF, "  决定性对比："));
        say(String.format(LF, "    LE(亚洲) = %.1f   LE(撒哈拉) = %.1f   比 = %.3f   <== 潜热才是季风引擎", leAsia, leSah, leAsia / leSah));
        say(String.format(LF, "    Q (亚洲) = %.1f   Q (撒哈拉) = %.1f   比 = %.3f   <== 总加热", qAsia, qSah, qAsia / qSah));
        say("");
        say("  判据：LE 之比 >> 1 => 强迫场能分开季风与沙漠，S2 有正确输入；");
        say("        若 LE 之比 ~1 或 <1 => S2 的强迫也是错的（与 P548 的 38x 同源）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
