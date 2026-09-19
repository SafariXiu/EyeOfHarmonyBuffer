package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P642 -- §432 追 σ 在 57.5~60N 的尖峰：是温度表自己的节点尺度结构，还是求导引入的。
public class P642 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P642] " + s); System.out.println("[P642] " + s); }
    static final double R = WorldContract.R_EFF;
    static final double D5 = Math.toRadians(5.0), H5 = D5 * R;

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p642_report.txt"), "UTF-8");
        say("P642: 温度表的节点尺度结构 vs 求导（DJF 为主）");
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] ths = {thS, thW};
        String[] tn = {"JJA", "DJF"};
        for (int s = 0; s < 2; s++) {
            say("");
            say("  === " + tn[s] + " ===   （T 为纬向平均海平面温度 K；dTdy 用 ±5 度）");
            say("      lat |    T       T(+5)     T(-5)   |  dTdy(1e-6 K/m)   N(1e-2)  sigma(1e-6) |  节点斜率 左/右 (K/deg)");
            for (double ld = 45.0; ld <= 72.0; ld += 1.5) {
                double lat = Math.toRadians(ld), th = ths[s];
                double t = PrecipField.zonalSlTemp(lat, th);
                double tp = PrecipField.zonalSlTemp(lat + D5, th), tm = PrecipField.zonalSlTemp(lat - D5, th);
                double dTdy = (tp - tm) / (2 * H5);
                double nst = PrecipField.staticN(lat, th);
                // 左右相邻 5 度节点的割线斜率（K/度）——看表的节点尺度结构
                double d1 = (PrecipField.zonalSlTemp(lat + D5, th) - t) / 5.0;
                double d0 = (t - PrecipField.zonalSlTemp(lat - D5, th)) / 5.0;
                say(String.format(LF, "     %5.1f | %7.2f  %8.2f  %8.2f  | %+12.3f   %7.3f  %10.3f |  %+7.3f  %+7.3f",
                    ld, t, tp, tm, dTdy * 1e6, nst * 1e2, PrecipField.eadyGrowth(lat, th) * 1e6, d0, d1));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
