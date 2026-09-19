package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

/**
 * P554 —— S1a（辐射）的可行性实测：**辐射闭合能不能把 E_p 压到判据线以下？**
 *
 * <p>§357 的定量判据：陆地桶能正确工作 iff `E_p < P(季风) = 7.775 mm/day`。
 * 而模型现在的 `E_p` 是**纯空气动力学**的（亚洲 17.03 mm/day，海洋量级）⇒ 必然失败。
 *
 * <p>本探针问的是：**若把陆地皮温 `T_s` 由【表面能量平衡】解出来**（Manabe 1969 eq.16 的先例，`G = 0`），
 * `E_p = rho*C_E*|V|*(q_sat(T_s) - q_a)` 会变成多少？
 *
 * <p><b>为什么这一步能压住 E_p</b>：蒸发是**能量项**。当前 `T_s` 是诊断的（无蒸发冷却），
 * 所以 `q_sat(T_s)` 偏高 ⇒ `E_p` 虚高。把 `T_s` 解出来后，蒸发自己会把 `T_s` 压下来，
 * `q_sat(T_s)` 随之下降 ⇒ `E_p` 自限。这就是「能量限制」。
 *
 * <p><b>闭合</b>（`src` 零改动；所有系数都取自模型自己或已核验文献）：
 * <pre>
 *   (1 - alpha_s) * S  =  eps*sigma*T_s^4 + H + LE        （陆地 G = 0）
 *   H  = rho_a * c_p * C_H * |V| * (T_s - T_a)
 *   LE = rho_a * L_v * C_E * |V| * max(0, q_sat(T_s) - q_a)
 *   OLR = eps * sigma * T_s^4,   eps 由 Trenberth(2009) 的观测 OLR 定标
 * </pre>
 * `C_H = C_E = Atmosphere.cdOf(kappa)`（**声明**：复用模型自己的拖曳系数，不新造数）。
 * `S` = 日平均 TOA 日照（标准 insolation 公式，`S0 = 1361 W/m^2`）。
 * `alpha_s` = 0.06 海 / 0.20 陆 / 0.65 雪（`T_s < 273.15`）。
 *
 * <p><b>求解</b>：残差对 `T_s` 单调递减（每一项都随 `T_s` 增）⇒ 二分法唯一根。
 */
public class P554 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P554] " + s); System.out.println("[P554] " + s); }

    static final double CIRC = 40_000_000.0;
    static final int GRAD = 500_000;
    static final int SEED = 1022228679;
    static final double S0 = 1361.0;              // 太阳常数 W/m^2
    static final double SIGMA = 5.670374419e-8;
    static final double CP = 1004.0;
    static final double LV = 2.45e6;
    /** 由 Trenberth et al. (2009) 的观测 OLR 239 W/m^2 @ 288.15 K 反解的有效发射率。 */
    static final double EPS = 238.5 / (SIGMA * Math.pow(288.15, 4));
    static final double ALB_SEA = 0.06, ALB_LAND = 0.20, ALB_SNOW = 0.65;

    /** 日平均 TOA 日照（W/m^2）：标准公式，`phi`/`dec` 为弧度。 */
    static double insol(double phi, double dec) {
        double x = -Math.tan(phi) * Math.tan(dec);
        double H0 = (x <= -1.0) ? Math.PI : (x >= 1.0 ? 0.0 : Math.acos(x));
        return (S0 / Math.PI) * (H0 * Math.sin(phi) * Math.sin(dec)
               + Math.cos(phi) * Math.cos(dec) * Math.sin(H0));
    }

    /** 表面能量平衡残差（W/m^2）。单调递减于 T_s ⇒ 可二分。 */
    static double resid(double Ts, double absSolar, double Ta, double qa, double chv) {
        double olr = EPS * SIGMA * Math.pow(Ts, 4);
        double h = 1.225 * CP * chv * (Ts - Ta);
        double le = 1.225 * LV * chv * Math.max(0.0, PrecipField.qSat(Ts) - qa);
        return absSolar - olr - h - le;
    }

    static double solveTs(double absSolar, double Ta, double qa, double chv) {
        double lo = 180.0, hi = 340.0;
        for (int i = 0; i < 200; i++) {
            double mid = 0.5 * (lo + hi);
            if (resid(mid, absSolar, Ta, qa, chv) > 0) lo = mid; else hi = mid;
        }
        return 0.5 * (lo + hi);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p554_report.txt"), "UTF-8");
        P546.loadMask();
        PlateField.MASK = new PlateField.LandMask() {
            public boolean isLand(int x, int z) { return P546.earthIsLand(x, z); }
        };
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        double dec = Atmosphere.subsolarLat(th);

        say("P554：辐射闭合 ⇒ 能量限制的 E_p（src 零改动）");
        say("  自证 SELF-PROOF：二分残差单调性 —— 检查 resid(T-1) > resid(T) > resid(T+1) 于 5 个温度点");
        double mono = 0;
        for (double t = 220; t <= 320; t += 25) {
            double r0 = resid(t - 1, 300, 295, 0.015, 0.02);
            double r1 = resid(t, 300, 295, 0.015, 0.02);
            double r2 = resid(t + 1, 300, 295, 0.015, 0.02);
            mono = Math.max(mono, Math.max(0, r1 - r0) + Math.max(0, r2 - r1));
        }
        say(String.format(LF, "  max 单调性违例 = %.3e （必须 0）", mono));
        say(String.format(LF, "  eps = %.4f （由 238.5 W/m^2 @ 288.15 K 定标）  S0 = %.0f", EPS, S0));
        say(String.format(LF, "  JJA 太阳赤纬 = %.2f deg", Math.toDegrees(dec)));
        say(String.format(LF, "  阈值（§357）：E_p 必须 < 7.775 mm/day（真实亚洲季风 P）"));

        Object[][] boxes = {
            {70.0, 120.0, 15.0, 35.0, "亚洲季风区", 7.775},
            {0.0,  30.0,  20.0, 35.0, "撒哈拉",     0.105},
            {250.0, 285.0, 25.0, 35.0, "美国南部",   3.595},
            {150.0, 210.0, 25.0, 35.0, "北太平洋",   2.402},
            {300.0, 350.0, 25.0, 35.0, "北大西洋",   0.677},
        };

        say("");
        say("=== 逐框：能量平衡解出的 T_s 与 E_p ===");
        say("  框          陆占比   S(W/m2)  alpha   吸收    当前Ts   解出Ts   OLR     H      LE    E_p(mm/d)  判据E_p<真值P?  当前E_p(空气动力学)");
        for (Object[] bx : boxes) {
            double lo = (double) bx[0], hi = (double) bx[1];
            double la = (double) bx[2], hb = (double) bx[3];
            long n = 0, nLand = 0;
            double sS = 0, sAlb = 0, sAbs = 0, sTsCur = 0, sTs = 0, sOlr = 0, sH = 0, sLe = 0, sEp = 0, sEpCur = 0;
            for (double latd = la + 2.5; latd <= hb; latd += 2.5) {
                int z = WorldContract.zOfLat(latd);
                for (int c = 0; c < 72; c++) {
                    double lon = (c + 0.5) * 360.0 / 72;
                    if (lon < lo || lon > hi) continue;
                    int x = (int) Math.round((c + 0.5) * CIRC / 72);
                    double lat = WorldContract.latOf(z);
                    double phi = lat;
                    boolean isLand = P546.earthIsLand(x, z);
                    double k = Atmosphere.kappaAt(x, z, sd, cell);
                    double[] u0 = Atmosphere.windAt(x, z, sd, cell, th, GRAD);
                    double[] dg = new double[8];
                    P546.decompose(x, z, sd, cell, th, dg);
                    double qa = dg[1];
                    double Ta = Atmosphere.surfaceTemp(x, z, sd, cell, th);
                    double chv = Atmosphere.cdOf(k) * Math.sqrt(u0[0] * u0[0] + u0[1] * u0[1]);
                    double S = insol(phi, dec);
                    double alb = isLand ? (Ta < 273.15 ? ALB_SNOW : ALB_LAND) : ALB_SEA;
                    double absS = (1.0 - alb) * S;
                    double Ts = solveTs(absS, Ta, qa, chv);
                    double olr = EPS * SIGMA * Math.pow(Ts, 4);
                    double hh = 1.225 * CP * chv * (Ts - Ta);
                    double le = 1.225 * LV * chv * Math.max(0.0, PrecipField.qSat(Ts) - qa);
                    double ep = le / (1000.0 * LV) * 86400.0 * 1000.0;      // mm/day
                    double epCur = 1.225 * Atmosphere.cdOf(k) * Math.sqrt(u0[0] * u0[0] + u0[1] * u0[1] + 16.0)
                                 * Math.max(0.0, PrecipField.qSat(Ta) - qa) / 1000.0 * 86400.0 * 1000.0;
                    sS += S; sAlb += alb; sAbs += absS; sTsCur += Ta; sTs += Ts;
                    sOlr += olr; sH += hh; sLe += le; sEp += ep; sEpCur += epCur;
                    if (isLand) nLand++;
                    n++;
                }
            }
            double truth = (double) bx[5];
            double ep = sEp / n;
            say(String.format(LF, "  %-11s %5.1f%% %8.1f %6.3f %7.1f %8.1f %8.1f %6.1f %6.1f %6.1f %10.3f     %-6s        %8.3f",
                bx[4], 100.0 * nLand / n, sS / n, sAlb / n, sAbs / n, sTsCur / n, sTs / n,
                sOlr / n, sH / n, sLe / n, ep, (ep < truth ? "通过" : "失败"), sEpCur / n));
        }

        say("");
        say("  判据：亚洲的 E_p 必须 < 7.775 mm/day（否则桶在季风区也会干 —— §357 的失败模式）");
        say("  同时要检查：解出的 T_s 不能离谱（陆地 JJA 应在 290~310 K），否则说明闭合里有系数错了");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
