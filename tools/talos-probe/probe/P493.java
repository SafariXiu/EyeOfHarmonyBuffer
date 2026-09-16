package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/**
 * P493：**风暴轴涡动项沿纬度的细结构** —— B2.b（中纬雨带的季节迁移）到底在不在物理里？
 *
 * <p>背景：E63 修掉「分段线性表的二阶导是节点脉冲梳」之后，B2.b 由「迁移 10.8 度」变成「0 度」。
 * 问题：这是**物理结论**还是**残余数值伪像**？本探针把 {@code eddyMfc} 的每一个因子
 * 都按 0.25 度打印出来，直接看峰在哪里、为什么在那里。
 */
public class P493 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P493] " + s); rep.flush(); System.out.println("[P493] " + s); System.out.flush(); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p493_report.txt"), "UTF-8");
        say("P493：风暴轴涡动项的纬度细结构（theta=0 北半球夏至 / theta=pi 冬至）");
        say(String.format(LF, "  EDDY_DPHI_DEG=%.1f  EDDY_CLOSURE=%d  EDDY_PHYS_GAIN=%.3f  EDDY_TAU=%.0f  U0_STORM=%.3f",
                PrecipField.EDDY_DPHI_DEG, PrecipField.EDDY_CLOSURE, PrecipField.EDDY_PHYS_GAIN,
                PrecipField.EDDY_TAU, PrecipField.U0_STORM));
        double D = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        for (double th : new double[]{0.0, Math.PI}) {
            say("");
            say(String.format(LF, "=== theta = %.3f (%s) ===", th, th == 0.0 ? "北半球夏至" : "北半球冬至"));
            say(String.format(LF, "  %6s %11s %12s %12s %11s %12s", "lat", "zonalSlT", "d2T/dphi2", "moistW", "stormGate", "eddyMfc"));
            double best = -1e30, bestLat = 0, bestW = -1e30, bestWLat = 0;
            for (double la = 20.0; la <= 72.001; la += 0.5) {
                double lr = Math.toRadians(la);
                double t0 = PrecipField.zonalSlTemp(lr, th);
                double tN = PrecipField.zonalSlTemp(lr + D, th);
                double tS = PrecipField.zonalSlTemp(lr - D, th);
                double d2 = (tN - 2 * t0 + tS) / (D * D);
                double w = PrecipField.eddyWEquivalent(lr, th);
                double mfc = PrecipField.eddyMfc(lr, th);
                if (mfc > best) { best = mfc; bestLat = la; }
                if (w > bestW) { bestW = w; bestWLat = la; }
                if (((int) Math.round(la * 2)) % 8 == 0)
                    say(String.format(LF, "  %6.1f %11.3f %12.3f %12.4f %11.4f %12.4e",
                            la, t0, d2, w, PrecipField.stormGate(lr, th), mfc));
            }
            say(String.format(LF, "  ⇒ eddyMfc 峰值在 %.2f 度（20~72 度内）；eddyWEquivalent 峰值在 %.2f 度", bestLat, bestWLat));
        }
        say("");
        say("★ 判读：若两季的 eddyMfc 峰值纬度差 >= 10 度 ⇒ 迁移在物理里，B2.b 是被**别的**东西压掉的；");
        say("        若 < 3 度 ⇒ 峰位由温度剖面的形状钉死，模型里**没有**风暴轴季节迁移的机制。");
        rep.flush(); rep.close();
        System.out.println("JAVA_EXIT=0");
    }
}
