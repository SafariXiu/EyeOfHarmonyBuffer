package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P697 -- §494: 涡动符号不稳（§485）是不是【求导模板】造成的？
//   扫 EDDY_DPHI_DEG（curv 的二阶差分步长），数符号与观测一致的比例。
//   §485 的机理：curv(W) 是相消量（d2W/W ~ 0.01%~1%）⇒ 模板宽度应当显著影响符号。
public class P697 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P697] " + s); System.out.println("[P697] " + s); }

    static int[] count(double th) {
        int ok = 0, tot = 0;
        for (double latd = 25.0; latd <= 65.0; latd += 2.5) {
            double lr = Math.toRadians(latd);
            double m = PrecipField.eddyMfc(lr, th);
            double o = ZonalTables.eddyMfcObsMonth(lr, th);
            if (Math.abs(o) < 1e-9) continue;
            tot++; if ((m > 0) == (o > 0)) ok++;
        }
        return new int[]{ok, tot};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p697_report.txt"), "UTF-8");
        say("P697: 涡动求导模板宽度扫描（§494）");
        EarthRef.install();
        SimClimate.clearCache();
        double thJ = Atmosphere.theta(0.0);
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        say("     当前 EDDY_DPHI_DEG=" + PrecipField.EDDY_DPHI_DEG + "  EDDY_GATE_MODE=" + PrecipField.EDDY_GATE_MODE);
        say("");
        say("     DPHI(度) | JJA 符号正确 | DJF 符号正确 | 合计");
        double[] dd = {2.5, 5.0, 7.5, 10.0, 15.0};
        for (int k = 0; k < dd.length; k++) {
            PrecipField.EDDY_DPHI_DEG_V = dd[k];
            SimClimate.clearCache();
            int[] j = count(thJ), w = count(thD);
            say(String.format(LF, "     %9.1f | %6d/%-6d | %6d/%-6d | %d/%d",
                dd[k], j[0], j[1], w[0], w[1], j[0] + w[0], j[1] + w[1]));
        }
        PrecipField.EDDY_DPHI_DEG_V = -1.0;
        SimClimate.clearCache();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}