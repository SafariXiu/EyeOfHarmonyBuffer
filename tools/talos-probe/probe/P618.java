package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P618 -- modelShape vs 观测逐月剖面：规范化形状对比（单位无关）。
// 观测表 eddyMfcObsMonth 在【验证层】作参照，不进生产（EDDY_PLACEMENT_FROM_OBS 默认 false）。
public class P618 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P618] " + s); System.out.println("[P618] " + s); }

    /** kg/(m^2 s) -> mm/day */
    static double toMmDay(double mfc) { return mfc * 86400.0; }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p618_report.txt"), "UTF-8");
        say("P618: eddyMfc 的规范化形状对比（模型 vs 观测逐月剖面）");
        say("  模型量级换算：1e-4 kg/(m^2 s) = 8.64 mm/day");
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        for (int pass = 0; pass < 2; pass++) {
            double th = (pass == 0) ? thS : thW;
            say("");
            say(String.format(LF, "  [%s]  纬度   模型(mm/day)   观测(mm/day)   模型/观测", (pass == 0) ? "JJA" : "DJF"));
            double mm = 0, mo = 0;
            for (double latd = 0; latd <= 80; latd += 5) {
                double lat = Math.toRadians(latd);
                double m = toMmDay(PrecipField.eddyMfc(lat, th));
                double o = ZonalTables.eddyMfcObsMonth(lat, th);
                mm = Math.max(mm, Math.abs(m)); mo = Math.max(mo, Math.abs(o));
                say(String.format(LF, "        %4.0f   %+11.3f   %+11.3f   %s",
                    latd, m, o, Math.abs(o) < 1e-9 ? "  n/a" : String.format(LF, "%+7.2f", m / o)));
            }
            say(String.format(LF, "      => 峰值: 模型 %.3f   观测 %.3f   （峰值比 %.2f）", mm, mo, mm / mo));
        }
        say("");
        say("  逐纬度的【形状相关】（把两侧都归一化到峰值=1 后比）：");
        for (int pass = 0; pass < 2; pass++) {
            double th = (pass == 0) ? thS : thW;
            double mm = 0, mo = 0;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                mm = Math.max(mm, Math.abs(toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th))));
                mo = Math.max(mo, Math.abs(ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th)));
            }
            double sxy = 0, sxx = 0, syy = 0, sx = 0, sy = 0; int n = 0;
            for (double latd = 0; latd <= 80; latd += 2.5) {
                double a = toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th)) / mm;
                double b = ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th) / mo;
                sxy += a * b; sxx += a * a; syy += b * b; sx += a; sy += b; n++;
            }
            double cov = sxy / n - (sx / n) * (sy / n);
            double va = sxx / n - (sx / n) * (sx / n), vb = syy / n - (sy / n) * (sy / n);
            double corr = (va > 0 && vb > 0) ? cov / Math.sqrt(va * vb) : 0;
            say(String.format(LF, "    [%s] corr(模型, 观测) = %+.4f", (pass == 0) ? "JJA" : "DJF", corr));
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
