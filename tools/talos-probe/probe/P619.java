package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P619 -- EDDY_MASK_OUTSIDE：掩码放在散度外面 vs 里面，哪个形状对？
public class P619 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P619] " + s); System.out.println("[P619] " + s); }

    static double toMmDay(double mfc) { return mfc * 86400.0; }

    static double corr(double th) {
        double mm = 0, mo = 0;
        for (double latd = 0; latd <= 80; latd += 2.5) {
            mm = Math.max(mm, Math.abs(toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th))));
            mo = Math.max(mo, Math.abs(ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th)));
        }
        if (mm <= 0 || mo <= 0) return 0;
        double sxy = 0, sxx = 0, syy = 0, sx = 0, sy = 0; int n = 0;
        for (double latd = 0; latd <= 80; latd += 2.5) {
            double a = toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th)) / mm;
            double b = ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th) / mo;
            sxy += a * b; sxx += a * a; syy += b * b; sx += a; sy += b; n++;
        }
        double cov = sxy / n - (sx / n) * (sy / n);
        double va = sxx / n - (sx / n) * (sx / n), vb = syy / n - (sy / n) * (sy / n);
        return (va > 0 && vb > 0) ? cov / Math.sqrt(va * vb) : 0;
    }

    static void dump(String tag, double th, String pass) {
        double mm = 0, mo = 0;
        for (double latd = 0; latd <= 80; latd += 2.5) {
            mm = Math.max(mm, Math.abs(toMmDay(PrecipField.eddyMfc(Math.toRadians(latd), th))));
            mo = Math.max(mo, Math.abs(ZonalTables.eddyMfcObsMonth(Math.toRadians(latd), th)));
        }
        say(String.format(LF, "    %s [%s] 峰值: 模型 %.3f  观测 %.3f  比 %.2f   corr %+.4f",
            tag, pass, mm, mo, mm / mo, corr(th)));
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p619_report.txt"), "UTF-8");
        say("P619: EDDY_MASK_OUTSIDE 的 A/B（形状相关与峰值比）");
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);

        PrecipField.EDDY_MASK_OUTSIDE = false;
        say("");
        say("  [A] EDDY_MASK_OUTSIDE = false（生产现值：掩码在微分【里面】）");
        dump("掩码在内", thS, "JJA");
        dump("掩码在内", thW, "DJF");
        say("      纬度   JJA(模型/观测)          DJF(模型/观测)");
        for (double latd = 0; latd <= 80; latd += 5) {
            double lat = Math.toRadians(latd);
            say(String.format(LF, "      %4.0f   %+9.3f / %+7.3f      %+9.3f / %+7.3f",
                latd, toMmDay(PrecipField.eddyMfc(lat, thS)), ZonalTables.eddyMfcObsMonth(lat, thS),
                toMmDay(PrecipField.eddyMfc(lat, thW)), ZonalTables.eddyMfcObsMonth(lat, thW)));
        }

        PrecipField.EDDY_MASK_OUTSIDE = true;
        say("");
        say("  [B] EDDY_MASK_OUTSIDE = true（掩码在微分【外面】，§269.5 工作项 14）");
        dump("掩码在外", thS, "JJA");
        dump("掩码在外", thW, "DJF");
        say("      纬度   JJA(模型/观测)          DJF(模型/观测)");
        for (double latd = 0; latd <= 80; latd += 5) {
            double lat = Math.toRadians(latd);
            say(String.format(LF, "      %4.0f   %+9.3f / %+7.3f      %+9.3f / %+7.3f",
                latd, toMmDay(PrecipField.eddyMfc(lat, thS)), ZonalTables.eddyMfcObsMonth(lat, thS),
                toMmDay(PrecipField.eddyMfc(lat, thW)), ZonalTables.eddyMfcObsMonth(lat, thW)));
        }
        PrecipField.EDDY_MASK_OUTSIDE = false;
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
