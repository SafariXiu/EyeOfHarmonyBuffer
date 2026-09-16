package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.File;
import java.io.PrintStream;
import java.util.Locale;

/** P465：**D47 修复的验证** —— L_d 的两个端点自检 + 对 45~55N 标定 gain 的预期影响。 */
public class P465 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P465] " + s); System.out.println("[P465] " + s); }
    static final double[] PH4 = {0.0, Math.PI / 2, Math.PI, 3.0 * Math.PI / 2.0};

    static double oldLd(double latRad, double theta) {
        double f = Math.abs(WorldContract.coriolis(latRad));
        if (f < 1.0e-5) f = 1.0e-5;
        return PrecipField.staticN(latRad, theta) * Atmosphere.H_EFF / f;
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p465_report.txt"), "UTF-8");
        double beta = 2.0 * WorldContract.OMEGA / WorldContract.R_EFF;
        say("P465：D47 修复验证（deformRadius 换成赤道 beta 平面形式）");
        say(String.format(LF, "  Omega=%.6e  R_EFF=%.0f m  beta=%.4e 1/(m s)  H_EFF=%.0f m",
            WorldContract.OMEGA, WorldContract.R_EFF, beta, Atmosphere.H_EFF));
        say("");
        say(String.format(LF, "  %-7s %11s %11s %11s %11s %10s %9s", "lat", "c=N*H m/s", "f 1/s", "Ld new km", "Ld old km", "new/old", "Ld_new*|f|/c"));
        for (double la : new double[]{0, 2.5, 5, 10, 20, 30, 45, 50, 60, 70, 80, 90}) {
            double lat = Math.toRadians(la);
            double c = 0, ldn = 0, ldo = 0, f = Math.abs(WorldContract.coriolis(lat));
            for (double th : PH4) { c += PrecipField.staticN(lat, th) * Atmosphere.H_EFF / 4; ldn += PrecipField.deformRadius(lat, th) / 4; ldo += oldLd(lat, th) / 4; }
            say(String.format(LF, "  %-7.1f %11.2f %11.3e %11.1f %11.1f %10.3f %9.4f",
                la, c, f, ldn / 1000, ldo / 1000, ldn / ldo, ldn * f / c));
            if (la == 0) say(String.format(LF, "      ⇒ 赤道端点自检：L_d 应为 sqrt(c/beta) = %.1f km（实得 %.1f km）",
                Math.sqrt(c / beta) / 1000, ldn / 1000));
            if (la == 90) say(String.format(LF, "      ⇒ 极点端点自检：L_d 应为 c/|f| = %.1f km（实得 %.1f km）",
                c / f / 1000, ldn / 1000));
        }
        say("");
        say("B. 对 45~55N 标定 gain 的预期影响（kappa ∝ L_d^2）");
        double r = 0; int n = 0;
        for (double la = 45; la <= 55.0001; la += 0.5) {
            double lat = Math.toRadians(la);
            double a = 0, b = 0;
            for (double th : PH4) { a += PrecipField.deformRadius(lat, th) / 4; b += oldLd(lat, th) / 4; }
            r += (b * b) / (a * a); n++;
        }
        r /= n;
        say(String.format(LF, "  45~55N 的 <Ld_old^2 / Ld_new^2> = %.4f", r));
        say(String.format(LF, "  ⇒ 预期新 gain ≈ %.3f x %.4f = **%.2f**（当前 4.40）", PrecipField.EDDY_PHYS_GAIN, r, PrecipField.EDDY_PHYS_GAIN * r));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
