package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.atmos.ZonalTables;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P615 -- eddyMfc 的纬向/季节剖面：模型自己的 modelShape vs 观测放置表。
// 观测表在这里只作【验证参照】，不进生产路径（EDDY_PLACEMENT_FROM_OBS 默认 false）。
public class P615 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P615] " + s); System.out.println("[P615] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p615_report.txt"), "UTF-8");
        say("P615: eddyMfc 剖面（模型 modelShape vs 观测放置表）");
        say(String.format(LF, "  EDDY_PLACEMENT_FROM_OBS=%s (默认 false => 走 modelShape);  EDDY_MIX=%.2f  U0_STORM=%.1f",
            PrecipField.EDDY_PLACEMENT_FROM_OBS, PrecipField.EDDY_MIX, PrecipField.U0_STORM));
        say(String.format(LF, "  EDDY_CLOSURE=%d  EDDY_FULL_DIVERGENCE=%s  EDDY_MASK_OUTSIDE=%s",
            PrecipField.EDDY_CLOSURE, PrecipField.EDDY_FULL_DIVERGENCE, PrecipField.EDDY_MASK_OUTSIDE));
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        for (int pass = 0; pass < 2; pass++) {
            double th = (pass == 0) ? thS : thW;
            say("");
            say(String.format(LF, "  [%s]   纬度   stormGate     模型eddyMfc      观测表      模型/观测",
                (pass == 0) ? "JJA" : "DJF"));
            for (double latd = 0; latd <= 80; latd += 5) {
                double lat = Math.toRadians(latd);
                double g = PrecipField.stormGate(lat, th);
                double m = PrecipField.eddyMfc(lat, th);
                double o = ZonalTables.eddyMfcObsMonth(lat, th);
                String r = (Math.abs(o) < 1.0e-12) ? "   n/a" : String.format(LF, "%+7.2f", m / o);
                say(String.format(LF, "        %4.0f   %8.4f   %+12.4e   %+12.4e   %s", latd, g, m, o, r));
            }
        }
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
