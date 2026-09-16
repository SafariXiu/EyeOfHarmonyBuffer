package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P464：**EDDY_PHYS_GAIN = 5.4 是从哪来的？** —— 两种闭合的逐纬度分解。
 *
 * <p>动机（设计冻结 §147.3 记账 2）：Caballero &amp; Hanley 的标度给出的 κ 只有标定值的 1/5.4。
 * 要么是 σ²/L_d² 的纬度结构不一样，要么是「mfc &gt; 0」的截断把一半抵消掉了。
 * 这里把每个因子逐纬度打出来，并算两种闭合在 45~55N 的 max(MFC,0) 带均值之比 ——
 * 那个比值就是「要让降水对上，gain 必须取多少」。
 */
public class P464 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};

    static void say(String s) { rep.println("[P464] " + s); System.out.println("[P464] " + s); }

    static double[] parts(double latRad, double theta) {
        double d = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double w0 = PrecipField.columnWater(c, theta);
        double wp = PrecipField.columnWater(c + d, theta);
        double wm = PrecipField.columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
        double ld = PrecipField.deformRadius(c, theta);
        double sg = PrecipField.eadyGrowth(c, theta);
        double gate = PrecipField.stormGate(latRad, theta);
        double kOld = PrecipField.EDDY_MIX * sg * ld * ld;
        double kNew1 = PrecipField.EDDY_TAU * sg * sg * ld * ld;
        return new double[]{sg, ld, curv, gate, kOld, kNew1, kOld * curv * gate, kNew1 * curv * gate};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p464_report.txt"), "UTF-8");
        say("P464：EDDY_PHYS_GAIN 的来源分解（closure 0 vs closure 1 @ gain=1）");
        say(String.format(LF, "  EDDY_MIX=%.3f  EDDY_TAU=%.0f s  H_EFF=%.0f m  EADY_COEF=%.3f",
            PrecipField.EDDY_MIX, PrecipField.EDDY_TAU, Atmosphere.H_EFF, PrecipField.EADY_COEF));
        say("");
        say("A. 年均（4 相位平均）逐纬度");
        say(String.format(LF, "  %-7s %10s %9s %12s %8s %11s %11s %9s", "lat", "sigma 1/s", "L_d km", "curv", "gate", "K_old", "K_new(g1)", "Knew/Kold"));
        for (double latDeg = -80; latDeg <= 80.0001; latDeg += 5) {
            double lat = Math.toRadians(latDeg);
            double s = 0, l = 0, cv = 0, g = 0, ko = 0, kn = 0;
            for (double th : PH4) {
                double[] p = parts(lat, th);
                s += p[0] / 4; l += p[1] / 4; cv += p[2] / 4; g += p[3] / 4; ko += p[4] / 4; kn += p[5] / 4;
            }
            say(String.format(LF, "  %-7.1f %10.3e %9.1f %12.3e %8.4f %11.3e %11.3e %9.3f",
                latDeg, s, l / 1000, cv, g, ko, kn, kn / Math.max(1e-30, ko)));
        }
        say("");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("B. 关键比值：45~55N 的 **max(MFC,0) 带均值**（驱动降水量的量）");
        say(String.format(LF, "  %-9s %15s %15s %15s %9s", "season", "mean max(old,0)", "mean max(new g1,0)", "mean max(new g5.4,0)", "needed gain"));
        for (int si = 0; si < 2; si++) {
            double th = si == 0 ? thS : thW;
            double so = 0, sn = 0, sg = 0; int n = 0;
            for (double latDeg = 45; latDeg <= 55.0001; latDeg += 0.5) {
                double[] p = parts(Math.toRadians(latDeg), th);
                so += Math.max(0, p[6]); sn += Math.max(0, p[7]); sg += Math.max(0, p[7] * 5.4); n++;
            }
            so /= n; sn /= n; sg /= n;
            say(String.format(LF, "  %-9s %15.4e %15.4e %15.4e %9.3f", si == 0 ? "夏(JJA)" : "冬(DJF)", so, sn, sg, so / Math.max(1e-30, sn)));
        }
        say("");
        say("C. 去掉 gate、保留符号（含辐散侧）");
        for (int si = 0; si < 2; si++) {
            double th = si == 0 ? thS : thW;
            double so = 0, sn = 0; int n = 0;
            for (double latDeg = 45; latDeg <= 55.0001; latDeg += 0.5) {
                double[] p = parts(Math.toRadians(latDeg), th);
                so += p[6]; sn += p[7]; n++;
            }
            say(String.format(LF, "  %-9s 带均值 MFC_old=%+.4e  MFC_new(g1)=%+.4e  比值 %.3f",
                si == 0 ? "夏(JJA)" : "冬(DJF)", so / n, sn / n, (so / n) / Math.max(1e-30, sn / n)));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
