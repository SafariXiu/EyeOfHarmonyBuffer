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
 * P567 -- §357 的陆地桶用【S1a 的能量限制 E_p】重做一遍。
 *
 * P553 用【空气动力学】E_p（亚洲 17.03 mm/day）=> 桶把季风也榨干（比值 0.725 -> 0.629，变差）。
 * 但 S1a 的能量限制 E_p 只有 6.31 mm/day < 亚洲真实 P 7.775
 *   => beta = min(1, P/E_p) 在亚洲 >= 1 => 不被干；
 *   而沙漠 P 远小于其 E_p => 被干。
 *
 * 本探针只算代数（纯探针，src 零改动），问：比值 亚洲/撒哈拉 会不会【上升】？
 */
public class P567 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P567] " + s); System.out.println("[P567] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double MMD = 86400.0 * 1000.0;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p567_report.txt"), "UTF-8");
        say("P567: 陆地桶 x S1a 能量限制 E_p —— 比值会不会上升？");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        say("  dec = " + String.format(LF, "%.2f deg", Math.toDegrees(dec)) + "  EPS = " + String.format(LF, "%.4f", Radiation.EPS));

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区", 2.911, 7.775},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉",     4.013, 0.105},
            {250.0, 285.0, 25.0, 35.0, "美国南部",   1.181, 3.595},
            {150.0, 210.0, 25.0, 35.0, "北太平洋",   0.880, 2.402},
            {300.0, 350.0, 25.0, 35.0, "北大西洋",   0.885, 0.677},
        };
        say("");
        say("  框           P_old   Ep_能限  Ep_气动   beta_A   P_A    beta_B   P_B    地球真值");
        double pA0 = -1, pA1 = -1, pB0 = -1, pB1 = -1;
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0;
            double sP = 0, sEpE = 0, sEpA = 0, sPa = 0, sPb = 0, sBa = 0, sBb = 0;
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
                    double q = dg[1];
                    double tSfc = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    double sp = Math.hypot(u0[0], u0[1]);
                    double vEff = Math.sqrt(sp * sp + PrecipField.V_GUST * PrecipField.V_GUST);
                    double cd = Atmosphere.cdOf(k);
                    // 空气动力学 E_p（P553 用的那个）
                    double epA = PrecipField.RHO_AIR * cd * vEff
                               * Math.max(0.0, PrecipField.qSat(tSfc) - q) / PrecipField.RHO_WATER * MMD;
                    // S1a 能量限制 E_p
                    double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                    double absSolar = (1.0 - alb) * Radiation.insolation(lat, dec);
                    double chv = PrecipField.RHO_AIR * cd * vEff;
                    double tsSkin = Radiation.skinTempLand(absSolar, tSfc, q, chv);
                    double epE = Radiation.potentialEvapMmDay(tsSkin, q, chv);
                    double Pold = PrecipField.mmPerDay(x, z, sd, cell, th, GRAD);
                    double bA = epA > 1e-12 ? Math.min(1.0, Pold / epA) : 1.0;      // 空气动力学口径
                    double bB = epE > 1e-12 ? Math.min(1.0, Pold / epE) : 1.0;      // 能量限制口径
                    sP += Pold; sEpE += epE; sEpA += epA; sBa += bA; sBb += bB;
                    sPa += Pold * (1.0 - k + k * bA);
                    sPb += Pold * (1.0 - k + k * bB);
                    n++;
                }
            }
            double pOld = sP / n, pA = sPa / n, pB = sPb / n;
            if (bx[4].equals("亚洲季风区")) { pA0 = pA; pB0 = pB; }
            if (bx[4].equals("撒哈拉"))     { pA1 = pA; pB1 = pB; }
            say(String.format(LF, "  %-10s %7.3f %8.3f %8.3f %7.3f %7.3f %7.3f %7.3f %8.3f",
                bx[4], pOld, sEpE / n, sEpA / n, sBa / n, pA, sBb / n, pB, (double) bx[6]));
        }
        say("");
        say(String.format(LF, "  亚洲/撒哈拉 比值（真实 74.0）:"));
        say(String.format(LF, "    现状                 = %.3f", pA0 / pA1));
        say(String.format(LF, "    桶 x 空气动力学 E_p  = %.3f   （P553 的结论：变差）", pA0 / pA1));
        say(String.format(LF, "    桶 x 能量限制 E_p    = %.3f   <== 本轮要问的", pB0 / pB1));
        say("");
        say("  判据：若能量限制口径下比值【上升】=> S3 可以在 S2 之前落地（推翻 §357 的定序）");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
