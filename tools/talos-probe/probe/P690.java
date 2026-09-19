package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.runtime.SimClimate;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P690 -- §484: eddyMfc 的量级判据（目标第 (3) 项）—— 【两边都归一化】后再比。
//   观测表是无量纲的（45~60 度年均值为 1）；模型是 kg/(m2*s)。
//   ⇒ 必须先把模型按它自己的 45~60 度年均值归一化。
public class P690 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P690] " + s); System.out.println("[P690] " + s); }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p690_report.txt"), "UTF-8");
        say("P690: eddyMfc 归一化后的量级（§484，目标第 (3) 项）");
        EarthRef.install();
        SimClimate.clearCache();
        double thD = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        double thJ = Atmosphere.theta(0.0);
        // 归一化参考：模型在 45~60N 的年均（用 4 个相位平均）
        double ref = 0; int nr = 0;
        for (double latd = 45.0; latd <= 60.0; latd += 2.5) {
            double lr = Math.toRadians(latd);
            double a = 0;
            for (int q = 0; q < 4; q++) a += PrecipField.eddyMfc(lr, 2.0 * Math.PI * q / 4.0);
            ref += a / 4.0; nr++;
        }
        ref /= nr;
        say(String.format(LF, "     模型 45~60N 年均参考值 M_ref = %+.4e kg/(m2*s)", ref));
        say("");
        say("     纬度 | 模型/观测 DJF | 模型/观测 JJA | DJF 符号");
        double sD = 0, sJ = 0; int nD = 0, nJ = 0, sgnOK = 0, sgn = 0;
        for (double latd = 25.0; latd <= 55.0; latd += 2.5) {
            double lr = Math.toRadians(latd);
            double mD = PrecipField.eddyMfc(lr, thD) / ref;
            double mJ = PrecipField.eddyMfc(lr, thJ) / ref;
            double oD = ZonalTables.eddyMfcObsMonth(lr, thD);
            double oJ = ZonalTables.eddyMfcObsMonth(lr, thJ);
            String rD = Math.abs(oD) < 1e-9 ? "n/a" : String.format(LF, "%8.2f", mD / oD);
            String rJ = Math.abs(oJ) < 1e-9 ? "n/a" : String.format(LF, "%8.2f", mJ / oJ);
            if (latd >= 30.0 && latd <= 50.0) {
                if (Math.abs(oD) > 1e-9) { sD += mD / oD; nD++; }
                if (Math.abs(oJ) > 1e-9) { sJ += mJ / oJ; nJ++; }
                sgn++; if ((mD > 0) == (oD > 0)) sgnOK++;
            }
            say(String.format(LF, "     %5.1f | %14s | %14s | %s", latd, rD, rJ,
                ((mD > 0) == (oD > 0)) ? "OK" : "**反号**"));
        }
        say("");
        say(String.format(LF, "     DJF 30~50N 平均 模型/观测 = %.2f  （§441 记 3~5 倍）", sD / Math.max(1, nD)));
        say(String.format(LF, "     JJA 30~50N 平均 模型/观测 = %.2f", sJ / Math.max(1, nJ)));
        say(String.format(LF, "     DJF 符号正确 %d/%d", sgnOK, sgn));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}