package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.litho.PlateField;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.runtime.SimTerrain;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P625 -- 赤道 divU 的真实量级：与「热带低层散度 ~1e-6 1/s」对照。
public class P625 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P625] " + s); System.out.println("[P625] " + s); }
    static final int SEED = 1022228679;
    static final double CIRC = 40_000_000.0;
    static int xOfLon(double lon) { return (int) Math.round(lon / 360.0 * CIRC); }
    static int zOfLat(double lat) { return WorldContract.zOfLat(lat); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p625_report.txt"), "UTF-8");
        say("P625: divU 的纬度剖面与量级（地球掩膜 + ETOPO1，JJA）");
        say(String.format(LF, "  H_EFF=%.1f m   w 换算系数 H/pi=%.1f", Atmosphere.H_EFF, Atmosphere.H_EFF / Math.PI));
        EarthRef.install();
        SimClimate.clearCache();
        long sd = SimTerrain.seedOf(SEED);
        int cell = PlateField.PLATE_CELL;
        double th = Atmosphere.theta(0.0);
        say("");
        say("  纬度   <divU>       RMS(divU)    <wDiag>    |w| 参考（热带低层散度 ~1e-6）");
        for (double latd = 0; latd <= 70; latd += 5) {
            int z = zOfLat(latd);
            double s = 0, s2 = 0, sw = 0; long n = 0;
            for (int c = 0; c < 72; c++) {
                PrecipField.mmPerDay(xOfLon((c + 0.5) * 5.0), z, sd, cell, th, 500_000);
                double d = PrecipField.DIAG.get()[0];
                s += d; s2 += d * d; sw += PrecipField.DIAG.get()[14]; n++;
            }
            double md = s / n, rms = Math.sqrt(s2 / n - md * md);
            say(String.format(LF, "  %4.0f  %+11.4e  %11.4e  %+11.4e   （|divU|/1e-6 = %.1f）",
                latd, md, rms, sw / n, Math.abs(md) / 1e-6));
        }
        EarthRef.uninstall();
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
