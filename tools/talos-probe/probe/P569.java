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
 * P569 -- 决定 H 符号的到底是什么？量三种地表干湿度。
 *
 * P568 用 q_a = 0.80*q_sat(Ta)（= 湿表面）=> H 在陆地上为负。
 * 但真实沙漠是【干】的（LE≈0）=> 皮温升上去 => H 才翻正。
 * => H 的符号由【地表干湿】决定，不由 T_a 决定。这就是环上 S3 那一环。
 *
 * 本探针把 fa（近地面相对湿度因子）扫描 0.80 / 0.50 / 0.20 / 0.05，
 * 对每个框给出 Ts / H / LE，并给出 亚洲/撒哈拉 的 LE 之比。
 * => 这就是第 2 步（陆地桶）的【规格书】：桶必须把撒哈拉的 fa 压到哪个量级。
 */
public class P569 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P569] " + s); System.out.println("[P569] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[] FA = {0.80, 0.50, 0.20, 0.05};

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p569_report.txt"), "UTF-8");
        say("P569: 地表干湿度 fa 扫描 —— H 的符号由谁决定？（地球掩膜，JJA）");
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
        double[][] leByBox = new double[boxes.length][FA.length];
        double[][] tsByBox = new double[boxes.length][FA.length];
        double[][] hByBox  = new double[boxes.length][FA.length];

        say("");
        say("  框            陆占比   Ts_now   |  fa=0.80: Ts     H      LE   |  fa=0.20: Ts     H      LE   |  fa=0.05: Ts     H      LE");
        for (int b = 0; b < boxes.length; b++) {
            double lo = (double) boxes[b][0], hi = (double) boxes[b][1];
            double la = (double) boxes[b][2], hb = (double) boxes[b][3];
            long n = 0, nLand = 0;
            double sNow = 0;
            double[] sTs = new double[FA.length], sH = new double[FA.length], sLE = new double[FA.length];
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                    double ta = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    double sp = Math.hypot(u0[0], u0[1]);
                    double chv = Radiation.bulkCoeff(k, sp);
                    double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                    double absS = (1.0 - alb) * Radiation.insolation(lat, dec);
                    double qsTa = PrecipField.qSat(ta);
                    sNow += ta;
                    for (int f = 0; f < FA.length; f++) {
                        double qa = FA[f] * qsTa;
                        double ts = Radiation.skinTempLand(absS, ta, qa, chv);
                        double hh = chv * Radiation.CP * (ts - ta);
                        double le = chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
                        sTs[f] += ts; sH[f] += hh; sLE[f] += le;
                    }
                    if (P546.earthIsLand(x, z)) nLand++;
                    n++;
                }
            }
            for (int f = 0; f < FA.length; f++) {
                tsByBox[b][f] = sTs[f] / n; hByBox[b][f] = sH[f] / n; leByBox[b][f] = sLE[f] / n;
            }
            say(String.format(LF, "  %-12s %5.1f%% %7.1f  | %6.1f %7.1f %7.1f | %6.1f %7.1f %7.1f | %6.1f %7.1f %7.1f",
                boxes[b][4], 100.0 * nLand / n, sNow / n,
                tsByBox[b][0], hByBox[b][0], leByBox[b][0],
                tsByBox[b][2], hByBox[b][2], leByBox[b][2],
                tsByBox[b][3], hByBox[b][3], leByBox[b][3]));
        }
        say("");
        say("  === 决定性：亚洲/撒哈拉 的 LE 之比随 fa ===");
        say("  fa      亚洲 LE   撒哈拉 LE   比     亚洲 H   撒哈拉 H");
        for (int f = 0; f < FA.length; f++) {
            say(String.format(LF, "  %.2f  %8.1f  %9.1f  %6.3f   %7.1f  %8.1f",
                FA[f], leByBox[0][f], leByBox[1][f], leByBox[0][f] / leByBox[1][f], hByBox[0][f], hByBox[1][f]));
        }
        say("");
        say("  判据：要 H 在陆地上翻正、且 LE 之比 >> 1，撒哈拉的 fa 必须被压到很低（干）。");
        say("        当前模型是 fa = 0.80 全场强制 => 这就是必须由 S3 桶替换的那个常数。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
