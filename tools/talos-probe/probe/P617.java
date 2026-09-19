package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P617 -- curv(W) 的二阶差分步长敏感性：峰值位置与季节比是不是被数值噪声驱动的？
public class P617 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P617] " + s); System.out.println("[P617] " + s); }

    /** 用指定步长 dStepDeg 算 shape（其余同 modelShape）。 */
    static double shape(double latRad, double theta, double dStepDeg) {
        double d = Math.toRadians(dStepDeg);
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double w0 = PrecipField.columnWater(c, theta);
        double wp = PrecipField.columnWater(c + d, theta);
        double wm = PrecipField.columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
        double ld = PrecipField.deformRadius(c, theta);
        double sg = PrecipField.eadyGrowth(c, theta);
        double K = PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
        return K * curv * PrecipField.stormGate(latRad, theta);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p617_report.txt"), "UTF-8");
        say("P617: curv(W) 的二阶差分步长敏感性");
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double[] steps = {5.0, 7.5, 10.0, 12.5, 15.0};
        for (double d : steps) {
            say("");
            say(String.format(LF, "  step = %.1f 度", d));
            say("      纬度   JJA shape      DJF shape");
            int signFlipJ = 0, signFlipW = 0;
            double prevJ = 0, prevW = 0;
            double pJ = 0, pW = 0, latJ = 0, latW = 0;
            boolean first = true;
            for (double latd = 15; latd <= 70; latd += 2.5) {
                double a = shape(Math.toRadians(latd), thS, d);
                double b = shape(Math.toRadians(latd), thW, d);
                if (Math.abs(a) > Math.abs(pJ)) { pJ = Math.abs(a); latJ = latd; }
                if (Math.abs(b) > Math.abs(pW)) { pW = Math.abs(b); latW = latd; }
                if (!first) {
                    if (a * prevJ < 0) signFlipJ++;
                    if (b * prevW < 0) signFlipW++;
                }
                prevJ = a; prevW = b; first = false;
                if (Math.abs(latd % 5.0) < 1e-9)
                    say(String.format(LF, "      %4.0f   %+12.4e  %+12.4e", latd, a, b));
            }
            say(String.format(LF, "      => 峰值: JJA %.0f 度 (%.4e)   DJF %.0f 度 (%.4e)   比 %.2f",
                latJ, pJ, latW, pW, pW / pJ));
            say(String.format(LF, "      => 15~70 度内符号翻转次数: JJA %d   DJF %d", signFlipJ, signFlipW));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
