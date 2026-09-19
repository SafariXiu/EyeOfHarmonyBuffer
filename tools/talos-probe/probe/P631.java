package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import java.io.*; import java.util.Locale;

// P631 -- EDDY_CLOSURE 0(sigma^1) vs 1(sigma^2)：形状相关与峰值比。
public class P631 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P631] " + s); System.out.println("[P631] " + s); }

    static double toMmDay(double m) { return m * 86400.0; }

    static void stats(String tag) {
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(com.EyeOfHarmonyBuffer.sim.world.WorldContract.DAYS_PER_YEAR / 2.0);
        double[] mm = new double[2], mo = new double[2];
        for (int p = 0; p < 2; p++) {
            double th = (p == 0) ? thS : thW;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                double lat = Math.toRadians(latd);
                mm[p] = Math.max(mm[p], Math.abs(toMmDay(PrecipField.eddyMfc(lat, th))));
                mo[p] = Math.max(mo[p], Math.abs(ZonalTables.eddyMfcObsMonth(lat, th)));
            }
        }
        say(String.format(LF, "    %s", tag));
        for (int p = 0; p < 2; p++) {
            double th = (p == 0) ? thS : thW;
            double sxy = 0, sxx = 0, syy = 0, sx = 0, sy = 0; int n = 0;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                double a = toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th)) / mm[p];
                double b = ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th) / mo[p];
                sxy += a * b; sxx += a * a; syy += b * b; sx += a; sy += b; n++;
            }
            double cov = sxy / n - (sx / n) * (sy / n);
            double va = sxx / n - (sx / n) * (sx / n), vb = syy / n - (sy / n) * (sy / n);
            double corr = (va > 0 && vb > 0) ? cov / Math.sqrt(va * vb) : 0;
            say(String.format(LF, "      [%s] 峰值 模型 %.3f  观测 %.3f  比 %.2f   corr %+.4f",
                (p == 0) ? "JJA" : "DJF", mm[p], mo[p], mm[p] / mo[p], corr));
        }
        say(String.format(LF, "      DJF/JJA 峰值比：模型 %.2f  观测 %.2f", mm[1] / mm[0], mo[1] / mo[0]));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p631_report.txt"), "UTF-8");
        say("P631: EDDY_CLOSURE 0 vs 1（对照 eddyMfcObsMonth，规范化形状 + 峰值比）");
        say(String.format(LF, "  EDDY_MIX=%.2f  EDDY_PHYS_GAIN=%.2f  EDDY_TAU=%.0f", PrecipField.EDDY_MIX, PrecipField.EDDY_PHYS_GAIN, PrecipField.EDDY_TAU));
        say("");
        PrecipField.EDDY_CLOSURE = 1;
        say("  [A] EDDY_CLOSURE = 1（sigma^2，现状）");
        stats("");
        say("");
        PrecipField.EDDY_CLOSURE = 0;
        say("  [B] EDDY_CLOSURE = 0（sigma^1，混合长/Taylor 形式）");
        stats("");
        PrecipField.EDDY_CLOSURE = 1;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}