package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P689 -- §484: eddyMfc 的 DJF 量级（目标第 (3) 项）。
//   ⚠ §483 的第一版把 latRad 当 latDeg 传了（访问器签名是 (latRad, theta)）⇒ 那一列作废。
public class P689 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P689] " + s); System.out.println("[P689] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p689_report.txt"), "UTF-8");
        say("P689: eddyMfc 的 DJF/JJA 量级（目标第 (3) 项，§484）");
        EarthRef.install();
        SimClimate.clearCache();
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thJ = Atmosphere.theta(0.0);
        say("     访问器签名：eddyMfcObsMonth(latRad, theta)  —— §483 传成了 latDeg");
        say("");
        say("     纬度 | 模型DJF      模型JJA    | 观测DJF   观测JJA   | DJF 模型/观测 | JJA 模型/观测 | DJF 符号");
        double sD = 0, sJ = 0; int nD = 0, nJ = 0; int sgn = 0, sgnOK = 0;
        for (double latd = 25.0; latd <= 55.0; latd += 2.5) {
            double lr = Math.toRadians(latd);
            double mD = PrecipField.eddyMfc(lr, thD);
            double mJ = PrecipField.eddyMfc(lr, thJ);
            double oD = ZonalTables.eddyMfcObsMonth(lr, thD);
            double oJ = ZonalTables.eddyMfcObsMonth(lr, thJ);
            if (latd >= 30.0 && latd <= 50.0) {
                if (Math.abs(oD) > 1e-9) { sD += mD / oD; nD++; }
                if (Math.abs(oJ) > 1e-9) { sJ += mJ / oJ; nJ++; }
                sgn++; if ((mD > 0) == (oD > 0)) sgnOK++;
            }
            say(String.format(LF, "     %5.1f | %+11.4e %+11.4e | %+9.4f %+9.4f | %13s | %13s | %s",
                latd, mD, mJ, oD, oJ,
                Math.abs(oD) < 1e-9 ? "n/a" : String.format(LF, "%.2f", mD / oD),
                Math.abs(oJ) < 1e-9 ? "n/a" : String.format(LF, "%.2f", mJ / oJ),
                ((mD > 0) == (oD > 0)) ? "OK" : "**反号**"));
        }
        say("");
        say(String.format(LF, "     30~50N 平均 模型/观测：  DJF = %.2f   JJA = %.2f   符号正确 %d/%d",
            nD == 0 ? Double.NaN : sD / nD, nJ == 0 ? Double.NaN : sJ / nJ, sgnOK, sgn));
        say("     判据：§441 记 DJF 30~50N 偏大 3~5 倍。");
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}