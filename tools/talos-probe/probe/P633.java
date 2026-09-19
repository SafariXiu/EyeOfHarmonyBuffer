package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P633 -- CW_SMOOTH 的 A/B：唯一有效指标是 corr（观测表是无量纲归一化形状表）。
public class P633 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P633] " + s); System.out.println("[P633] " + s); }

    static void stats(String tag) {
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("    " + tag);
        for (int p = 0; p < 2; p++) {
            double th = (p == 0) ? thS : thW;
            double mm = 0, mo = 0;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                mm = Math.max(mm, Math.abs(PrecipField.eddyMfc(Math.toRadians(latd), th)));
                mo = Math.max(mo, Math.abs(ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th)));
            }
            double sxy = 0, sxx = 0, syy = 0, sx = 0, sy = 0; int n = 0;
            int nSignOk = 0, nTot = 0;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                double a = PrecipField.eddyMfc(Math.toRadians(latd), th) / mm;
                double b = ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th) / mo;
                sxy += a * b; sxx += a * a; syy += b * b; sx += a; sy += b; n++;
                nTot++;
                if (Math.abs(b) > 0.15 && a * b > 0) nSignOk++;
            }
            double cov = sxy / n - (sx / n) * (sy / n);
            double va = sxx / n - (sx / n) * (sx / n), vb = syy / n - (sy / n) * (sy / n);
            double corr = (va > 0 && vb > 0) ? cov / Math.sqrt(va * vb) : 0;
            say(String.format(LF, "      [%s] corr %+.4f   符号一致 %d/%d（|观测|>0.15 的点）",
                (p == 0) ? "JJA" : "DJF", corr, nSignOk, nTot));
        }
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p633_report.txt"), "UTF-8");
        say("P633: CW_SMOOTH 的 A/B（观测表无量纲，只看 corr 与符号一致率）");
        for (int cl : new int[]{1, 0}) {
            PrecipField.EDDY_CLOSURE = cl;
            for (boolean sm : new boolean[]{false, true}) {
                PrecipField.CW_SMOOTH = sm;
                say("");
                say(String.format(LF, "  EDDY_CLOSURE=%d  CW_SMOOTH=%s", cl, sm));
                stats("");
            }
        }
        PrecipField.EDDY_CLOSURE = 1; PrecipField.CW_SMOOTH = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}