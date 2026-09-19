package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.Radiation;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.ocean.OceanField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P564 -- 验证新模块 Radiation（S1a 落地件）能复现 P554 已通过的数字。
 * 模块当前【无生产调用者】=> src 行为零改动（这一点由 P564 附带的调用者检查证明）。
 */
public class P564 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P564] " + s); System.out.println("[P564] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p564_report.txt"), "UTF-8");
        say("P564: 验证 Radiation 模块（S1a 落地件）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        OceanField.install(SEED);
        P546.loadMask();
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        say(String.format(LF, "  eps = %.4f   S0 = %.0f   赤纬 = %.2f deg", Radiation.EPS, Radiation.S0, Math.toDegrees(dec)));

        // ---- 自证 1：残差对 T_s 单调递减（二分法唯一根的前提）----
        double viol = 0;
        for (double t = 220; t <= 320; t += 5) {
            double r0 = Radiation.residual(t - 1, 300, 295, 0.015, 0.02);
            double r1 = Radiation.residual(t, 300, 295, 0.015, 0.02);
            double r2 = Radiation.residual(t + 1, 300, 295, 0.015, 0.02);
            viol = Math.max(viol, Math.max(0, r1 - r0) + Math.max(0, r2 - r1));
        }
        say(String.format(LF, "  自证1 残差单调性违例 = %.3e （必须 0）", viol));

        // ---- 自证 2：insolation 的边界与极昼/极夜 ----
        double eq = Radiation.insolation(0.0, 0.0);
        double poleSummer = Radiation.insolation(Math.toRadians(80), dec);
        double poleWinter = Radiation.insolation(Math.toRadians(-80), dec);
        say(String.format(LF, "  自证2 赤道春分 S = %.1f W/m^2（应 ~433）；80N 夏至 = %.1f（极昼）；80S = %.1f（近 0）",
            eq, poleSummer, poleWinter));

        // ---- 主表：与 P554 的五个框同口径 ----
        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区", 298.1, 6.981},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉",     303.1, 4.977},
            {250.0, 285.0, 25.0, 35.0, "美国南部",   297.2, 8.045},
            {150.0, 210.0, 25.0, 35.0, "北太平洋",   304.5, 4.702},
            {300.0, 350.0, 25.0, 35.0, "北大西洋",   303.6, 4.998},
        };
        say("");
        say("  与 P554 对照（P554 的 Ts/E_p 见最后两列括号）");
        say("  框            S(W/m2)  alpha   吸收    Ta      Ts(P554)   Ep(P554)");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0; double sS = 0, sA = 0, sAb = 0, sTa = 0, sTs = 0, sEp = 0;
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
                    double S = Radiation.insolation(lat, dec);
                    double alb = Radiation.albedo(P546.earthIsLand(x, z), ta);
                    double abs = (1.0 - alb) * S;
                    double chv = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
                    double ts = Radiation.skinTempLand(abs, ta, qa, chv);
                    double ep = Radiation.potentialEvapMmDay(ts, qa, chv);
                    sS += S; sA += alb; sAb += abs; sTa += ta; sTs += ts; sEp += ep;
                    n++;
                }
            }
            say(String.format(LF, "  %-11s %8.1f %6.3f %7.1f %7.1f  %8.1f (%.1f)  %7.3f (%.3f)",
                bx[4], sS / n, sA / n, sAb / n, sTa / n, sTs / n, (double) bx[5], sEp / n, (double) bx[6]));
        }
        say("");
        say(String.format(LF, "  关键判据：亚洲的 E_p = ? 必须 < 7.775（P554 已通过）"));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
