package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P456：**D7 的定量测量** —— 「kappa-blend 伪造的 du/dx」占 divU 的多少？
 *
 * <h3>缺陷的准确形态</h3>
 * Atmosphere.wind 里 u += ZonalTables.uZmBlend(latDeg, theta, kappa)。
 * uZmBlend 按**局地** κ 在两张表之间插值 ⇒ u_zm 获得了 du/dx —— 而它是**纬向平均**量，
 * 本不该有 x 梯度。它经 PrecipField.mmPerDay 的 divU = du/dx + dv/dz 变成**伪造散度**。
 *
 * <h3>精确分解（不需要改任何静态）</h3>
 * uBlend(x) = uZmSea(lat,th)*(1-k) + uZm(lat,th)*k,  k = kappa(x,z)
 * ⇒ duBlend/dx = (uZm(lat,th) - uZmSea(lat,th)) * dk/dx
 * 两项都能从公开 API 拿到 ⇒ 这是**精确**分解，不是估计。
 */
public class P456 {

    static final int SEED = 1022228679;
    static final int CELL = PlateField.PLATE_CELL;
    static final int GRAD = 500_000;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;

    static void say(String s) { rep.println("[P456] " + s); System.out.println("[P456] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p456_report.txt"), "UTF-8");
        say("P456：D7 定量 —— kappa-blend 伪造的 du/dx 占 divU 的多少");
        say(String.format(LF, "  SEA_ONLY_UZM=%s  GRAD=%d km  U_ZM 与 U_SEA 在 10 度处的差 = %.2f m/s",
            ZonalTables.SEA_ONLY_UZM, GRAD / 1000, ZonalTables.uZm(10.0) - ZonalTables.uZmSea(10.0, 0.0)));
        say("");

        double[] bandsLo = {10, 20, 30, 40, 50, 60};
        say(String.format(LF, "  %-8s %8s %14s %14s %14s %10s", "纬度带", "n", "|du/dx|_blend", "|divU| p50", "比值 p50", ">20% 占比"));
        long nAll = 0, overAll = 0; double sumRatio = 0;
        for (int bi = 0; bi < bandsLo.length; bi++) {
            double lo = bandsLo[bi], hi = lo + 10;
            java.util.ArrayList<Double> rb = new java.util.ArrayList<>();
            java.util.ArrayList<Double> dv = new java.util.ArrayList<>();
            int n = 0, over = 0;
            for (int sx = -10; sx <= 10; sx++) {
                for (int sz = 0; sz < 6; sz++) {
                    double latDeg = lo + (sz + 0.5) * (hi - lo) / 6.0;
                    int z = (int) (latDeg / 90.0 * (WorldContract.Z_CYCLE / 2));
                    int x = sx * 1_000_000;
                    double lat = WorldContract.latOf(z);
                    for (double th : PH4) {
                        double kP = Atmosphere.kappaAt(x + GRAD, z, SEED, CELL);
                        double kM = Atmosphere.kappaAt(x - GRAD, z, SEED, CELL);
                        double dkdx = (kP - kM) / (2.0 * GRAD);
                        double uZm = ZonalTables.uZm(latDeg, th);
                        double uSea = ZonalTables.uZmSea(latDeg, th);
                        double dUdxBlend = (uZm - uSea) * dkdx;
                        double[] uE = Atmosphere.windAt(x + GRAD, z, SEED, CELL, th, GRAD);
                        double[] uW = Atmosphere.windAt(x - GRAD, z, SEED, CELL, th, GRAD);
                        double[] vN = Atmosphere.windAt(x, z + GRAD, SEED, CELL, th, GRAD);
                        double[] vS = Atmosphere.windAt(x, z - GRAD, SEED, CELL, th, GRAD);
                        double divU = (uE[0] - uW[0]) / (2.0 * GRAD) + (vN[1] - vS[1]) / (2.0 * GRAD);
                        double a = Math.abs(dUdxBlend);
                        rb.add(a / Math.max(1e-12, Math.abs(divU)));
                        dv.add(Math.abs(divU));
                        n++; nAll++;
                        if (a > 0.2 * Math.abs(divU)) { over++; overAll++; }
                    }
                }
            }
            java.util.Collections.sort(rb); java.util.Collections.sort(dv);
            double r50 = rb.isEmpty() ? 0 : rb.get(rb.size() / 2);
            double d50 = dv.isEmpty() ? 0 : dv.get(dv.size() / 2);
            sumRatio += r50;
            say(String.format(LF, "  %-8s %8d %14.3e %14.3e %14.2f %9.0f%%",
                String.format(LF, "%.0f-%.0f", lo, hi), n, Math.abs(ZonalTables.uZm(lo, 0.0) - ZonalTables.uZmSea(lo, 0.0)) * 1e-6, d50, r50, 100.0 * over / Math.max(1, n)));
        }
        say("");
        say("A. 汇总");
        say(String.format(LF, "  样本 n = %d；|du/dx|_blend 超过 |divU| 的 20%% 的比例 = **%.1f%%**", nAll, 100.0 * overAll / Math.max(1, nAll)));
        say(String.format(LF, "  六个纬度带的「比值 p50」平均 = %.2f", sumRatio / bandsLo.length));
        say("");
        say("B. 裁决要点");
        say("  - 比值 p50 若 > 0.2 ⇒ 海岸带的 divU 有相当部分是这个伪造项，**必须修**；");
        say("  - 若 < 0.05 ⇒ 它是可忽略的次要点，**可以只记账**；");
        say("  - 注意：本项只在 κ 有 x 梯度的地方非零（海岸 800 km 内），内陆/深海应为 0。");
        rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
