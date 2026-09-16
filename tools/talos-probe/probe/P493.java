package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P493：**风暴轴涡动项沿纬度的峰位** —— D79 的判读仪器（A/B：单一全年谐波 vs 观测年循环形状）。
 *
 * <p>§220 预登记：{@code eddyMfc} 峰位迁移 **>= 5 度** ⇒ 根因确认；**< 3 度** ⇒ 根因不是形状。
 * 靶子：真实 ERA5 纬向平均的 |dT/dy| 峰值冬相对夏向赤道迁移 **7.0 度**。
 *
 * <p>⚠ 分解类仪器纪律：本探针不只打印 {@code eddyMfc}，还把它的**每一个因子**
 * （{@code zonalSlTemp}、它的**二阶导** d²T/dφ²、{@code columnWater}、{@code stormGate}）一起打出来 ——
 * 否则峰位动了也不知道是哪一项动的。
 */
public class P493 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P493] " + s); rep.flush(); System.out.println("[P493] " + s); System.out.flush(); }

    static double peak(double theta) {
        double best = -1e30, bestLat = 0;
        for (double la = 20.0; la <= 72.001; la += 0.25) {
            double v = PrecipField.eddyMfc(Math.toRadians(la), theta);
            if (v > best) { best = v; bestLat = la; }
        }
        return bestLat;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p493_report.txt"), "UTF-8");
        say("P493：风暴轴涡动项的纬度峰位（D79 的判读仪器）");
        say(String.format(LF, "  EDDY_DPHI_DEG=%.1f  EDDY_CLOSURE=%d  EDDY_PHYS_GAIN=%.3f  EDDY_TAU=%.0f  U0_STORM=%.3f",
                PrecipField.EDDY_DPHI_DEG, PrecipField.EDDY_CLOSURE, PrecipField.EDDY_PHYS_GAIN,
                PrecipField.EDDY_TAU, PrecipField.U0_STORM));
        say("");

        double D = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double[][] peaks = new double[2][2];
        boolean[] modes = {false, true};
        for (int mi = 0; mi < 2; mi++) {
            Atmosphere.SEASON_SHAPE_FROM_OBS = modes[mi];
            say("=============== SEASON_SHAPE_FROM_OBS = " + modes[mi] + " ===============");
            for (int si = 0; si < 2; si++) {
                double th = si == 0 ? 0.0 : Math.PI;
                say(String.format(LF, "--- theta = %.3f (%s) ---", th, si == 0 ? "北半球夏至" : "北半球冬至"));
                say(String.format(LF, "  %6s %10s %11s %11s %10s %12s", "lat", "zonalSlT", "d2T/dphi2", "columnW", "gate", "eddyMfc"));
                for (double la = 24.0; la <= 72.001; la += 4.0) {
                    double lr = Math.toRadians(la);
                    double t0 = PrecipField.zonalSlTemp(lr, th);
                    double d2 = (PrecipField.zonalSlTemp(lr + D, th) - 2 * t0
                               + PrecipField.zonalSlTemp(lr - D, th)) / (D * D);
                    say(String.format(LF, "  %6.1f %10.3f %11.2f %11.5f %10.4f %12.4e",
                            la, t0, d2, PrecipField.columnWater(lr, th), PrecipField.stormGate(lr, th),
                            PrecipField.eddyMfc(lr, th)));
                }
                double pk = peak(th);
                peaks[mi][si] = pk;
                say(String.format(LF, "  ⇒ 20~72 度内 eddyMfc 峰值纬度 = %.2f 度", pk));
            }
            say(String.format(LF, "  ⇒ **迁移（冬 − 夏）= %+.2f 度**", peaks[mi][1] - peaks[mi][0]));
            say("");
        }
        Atmosphere.SEASON_SHAPE_FROM_OBS = false;
        say("=============== 汇总 ===============");
        say(String.format(LF, "  单一全年谐波（现状）: 夏 %.2f  冬 %.2f  迁移 %+.2f 度", peaks[0][0], peaks[0][1], peaks[0][1] - peaks[0][0]));
        say(String.format(LF, "  观测年循环形状      : 夏 %.2f  冬 %.2f  迁移 %+.2f 度", peaks[1][0], peaks[1][1], peaks[1][1] - peaks[1][0]));
        say("  靶子（ERA5 原始剖面 |dT/dy| 峰位）：夏 69.0  冬 62.0  迁移 -7.0 度");
        say("");
        say("★ 判读（§220 跑之前写死）：");
        say("   · 观测形状的迁移 >= 5 度 ⇒ 根因确认（可以进入「翻开关 + 全量验收」）；");
        say("   · < 3 度 ⇒ 根因不是形状，D79 要重新定位；");
        say("   · **不许**只用「B2.b 绿了」当理由 —— 必须同时看 P296 的独立 GPCP 三条带有没有变差。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
