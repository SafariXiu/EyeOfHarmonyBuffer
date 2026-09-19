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
 * P570 -- 修正 P569 的错：扫【地表湿润度 beta】，不是扫空气湿度 fa。
 *
 * 正确的体块式（Manabe）：E = beta * rho*C_D*|V| * (q_sat(Ts) - q_a)
 *   beta = 1  饱和表面（湿地/海洋）
 *   beta -> 0 干表面（沙漠）=> 没有潜热冷却 => 皮温升上去 => H 翻正
 * q_a 保持模型的真值（0.80*q_sat(Ta)），它是【结果】不是【旋钮】。
 *
 * 能量平衡： (1-alb)S = eps*sigma*Ts^4 + chv*c_p*(Ts-Ta) + beta*chv*L_v*max(0, q_sat(Ts)-q_a)
 * 本探针内联实现带 beta 的残差（src 暂不改；验证后才把 beta 加进 Radiation.skinTempLand）。
 */
public class P570 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P570] " + s); System.out.println("[P570] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final double[] BETA = {1.00, 0.60, 0.30, 0.10, 0.02};

    static double resid(double ts, double absS, double ta, double qa, double chv, double beta) {
        double olr = Radiation.EPS * Radiation.SIGMA * ts * ts * ts * ts;
        double h = chv * Radiation.CP * (ts - ta);
        double le = beta * chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(ts) - qa);
        return absS - olr - h - le;
    }
    static double solveTs(double absS, double ta, double qa, double chv, double beta) {
        double lo = 180.0, hi = 360.0;
        for (int i = 0; i < 60; i++) { double m = 0.5 * (lo + hi); if (resid(m, absS, ta, qa, chv, beta) > 0) lo = m; else hi = m; }
        return 0.5 * (lo + hi);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p570_report.txt"), "UTF-8");
        say("P570: 地表湿润度 beta 扫描 —— H 的符号由【beta】决定（修正 P569）");
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);
        say("  dec = " + String.format(LF, "%.2f deg", Math.toDegrees(dec)) + "   q_a 固定为模型的 0.80*q_sat(Ta)");

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区"},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉"},
            {250.0, 285.0, 25.0, 35.0, "美国南部"},
            {150.0, 210.0, 25.0, 35.0, "北太平洋"},
            {300.0, 350.0, 25.0, 35.0, "北大西洋"},
        };
        double[][] ts = new double[boxes.length][BETA.length];
        double[][] hh = new double[boxes.length][BETA.length];
        double[][] le = new double[boxes.length][BETA.length];

        for (int b = 0; b < boxes.length; b++) {
            double lo = (double) boxes[b][0], hi = (double) boxes[b][1];
            double la = (double) boxes[b][2], hb = (double) boxes[b][3];
            long n = 0;
            double[] sTs = new double[BETA.length], sH = new double[BETA.length], sLE = new double[BETA.length];
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
                    double chv = Radiation.bulkCoeff(k, Math.hypot(u0[0], u0[1]));
                    double alb = Radiation.ALB_SEA + Atmosphere.clamp01(k) * (Radiation.ALB_LAND - Radiation.ALB_SEA);
                    double absS = (1.0 - alb) * Radiation.insolation(lat, dec);
                    double qa = PrecipField.RH_SEA * PrecipField.qSat(ta);
                    for (int f = 0; f < BETA.length; f++) {
                        double t = solveTs(absS, ta, qa, chv, BETA[f]);
                        sTs[f] += t;
                        sH[f] += chv * Radiation.CP * (t - ta);
                        sLE[f] += BETA[f] * chv * Radiation.LV * Math.max(0.0, PrecipField.qSat(t) - qa);
                    }
                    n++;
                }
            }
            for (int f = 0; f < BETA.length; f++) { ts[b][f] = sTs[f] / n; hh[b][f] = sH[f] / n; le[b][f] = sLE[f] / n; }
        }
        for (int f = 0; f < BETA.length; f++) {
            say("");
            say(String.format(LF, "  === beta = %.2f ===", BETA[f]));
            say("    框             Ts(K)     H(W/m2)   LE(W/m2)");
            for (int b = 0; b < boxes.length; b++) {
                say(String.format(LF, "    %-12s %7.1f %9.1f %9.1f", boxes[b][4], ts[b][f], hh[b][f], le[b][f]));
            }
            say(String.format(LF, "    亚洲/撒哈拉:  LE 比 = %.3f    H 差 = %.1f W/m2",
                le[0][f] / le[1][f], hh[0][f] - hh[1][f]));
        }
        say("");
        say("  判据：beta 降到多少时 (a) 陆地 H 翻正、(b) 亚洲/撒哈拉 LE 比 >> 1 ？");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
