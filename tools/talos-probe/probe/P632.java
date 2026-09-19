package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P632 -- columnWater 的光滑性：二阶差分是不是被【分段线性表的折角】支配？
public class P632 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P632] " + s); System.out.println("[P632] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p632_report.txt"), "UTF-8");
        say("P632: columnWater 的光滑性");
        double th = Atmosphere.theta(0.0);
        double R = WorldContract.R_EFF;
        say("");
        say("  细分辨率（0.5 度）的 W 与一/二阶差分，看折角是否落在 5 度的整数倍上");
        say("      lat    W(kg/m2)    dW/dy(1e-6)   d2W/dy2(1e-14)   lat%5");
        double[] w = new double[81];
        double[] lat = new double[81];
        for (int i = 0; i <= 80; i++) {
            lat[i] = 20.0 + i * 0.5;
            w[i] = PrecipField.columnWater(Math.toRadians(lat[i]), th);
        }
        double dl = Math.toRadians(0.5) * R;
        for (int i = 2; i <= 78; i++) {
            double d1 = (w[i + 1] - w[i - 1]) / (2 * dl);
            double d2 = (w[i + 1] - 2 * w[i] + w[i - 1]) / (dl * dl);
            if (i % 2 == 0 || Math.abs(lat[i] % 5.0) < 0.01)
                say(String.format(LF, "      %5.1f  %9.4f  %+12.3f  %+15.3f   %.1f",
                    lat[i], w[i], d1 * 1e6, d2 * 1e14, lat[i] % 5.0));
        }
        say("");
        say("  [B] 一阶差分在 5 度节点前后的变化（kink 检测）");
        say("      节点   左侧斜率      右侧斜率      比（1e-6 kg/m2/m）");
        for (double node = 25; node <= 50; node += 5) {
            double[] s = new double[80];
            for (int i = 1; i <= 79; i++) s[i] = (w[i + 1] - w[i]) / dl;
            int ic = (int) Math.round((node - 20.0) / 0.5);
            int iL = ic - 2, iR = ic + 2;
            if (iL < 1 || iR > 79) continue;
            say(String.format(LF, "      %4.0f  %+12.3f  %+12.3f    %.3f",
                node, s[iL] * 1e6, s[iR] * 1e6, s[iR] / s[iL]));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
