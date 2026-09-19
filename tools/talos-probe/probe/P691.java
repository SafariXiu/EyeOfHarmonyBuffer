package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P691 -- §485: JJA 45~50N 涡动符号反了（§484）—— 分解 curv(W) / gate。
//   因为 eddyMfc 的符号 = curv(W) 的符号（K 与 gate 都 >= 0）。
public class P691 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P691] " + s); System.out.println("[P691] " + s); }

    static double curvW(double latd, double theta) {
        double d = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double c = Math.toRadians(latd);
        double w0 = PrecipField.columnWater(c, theta);
        double wp = PrecipField.columnWater(c + d, theta);
        double wm = PrecipField.columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        return (wp - 2.0 * w0 + wm) / (dy * dy);
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p691_report.txt"), "UTF-8");
        say("P691: 涡动符号的分解（§485）—— 路径 EDDY_GATE_MODE=" + PrecipField.EDDY_GATE_MODE
            + " EDDY_VAR=" + PrecipField.EDDY_VAR + " EDDY_GRAD=" + PrecipField.EDDY_GRAD
            + " PLACEMENT_FROM_OBS=" + PrecipField.EDDY_PLACEMENT_FROM_OBS);
        EarthRef.install();
        SimClimate.clearCache();
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thJ = Atmosphere.theta(0.0);
        String[] sn = {"JJA", "DJF"};
        double[] ss = {thJ, thD};
        for (int s = 0; s < 2; s++) {
            say("");
            say("  --- " + sn[s] + " ---");
            say("     纬度 |   W(kg/m2)  |  curv(W)      | stormGate | eddyMfc(kg/m2/s) | 观测符号");
            for (double latd = 35.0; latd <= 65.0; latd += 2.5) {
                double lr = Math.toRadians(latd);
                double w = PrecipField.columnWater(lr, ss[s]);
                double cv = curvW(latd, ss[s]);
                double sg = PrecipField.stormGate(lr, ss[s]);
                double m = PrecipField.eddyMfc(lr, ss[s]);
                double o = ZonalTables.eddyMfcObsMonth(lr, ss[s]);
                say(String.format(LF, "     %5.1f | %10.3f | %+11.3e | %9.4f | %+14.4e | %+6.2f %s",
                    latd, w, cv, sg, m, o, ((m > 0) == (o > 0)) ? "OK" : "**反号**"));
            }
        }
        say("");
        say("判据：模型 eddyMfc 的符号 = curv(W) 的符号 ⇒ 找 curv 穿零的纬度 vs 观测穿零的纬度。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}