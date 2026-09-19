package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;

import java.io.*;
import java.util.Locale;

// P616 -- modelShape 的因子分解：DJF/JJA 峰值比 2.9 倍到底来自哪个因子？
public class P616 {

    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static PrintStream rep;
    static void say(String s) { rep.println("[P616] " + s); System.out.println("[P616] " + s); }

    /** 复刻 modelShape 的因子分解（四个 helper 全是 public）。 */
    static double[] factors(double latRad, double theta) {
        double d = Math.toRadians(PrecipField.EDDY_DPHI_DEG);
        double lim = Math.PI / 2.0 - d;
        double c = latRad > lim ? lim : (latRad < -lim ? -lim : latRad);
        double w0 = PrecipField.columnWater(c, theta);
        double wp = PrecipField.columnWater(c + d, theta);
        double wm = PrecipField.columnWater(c - d, theta);
        double dy = d * WorldContract.R_EFF;
        double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
        double ld = PrecipField.deformRadius(c, theta);
        double sg = PrecipField.eadyGrowth(c, theta);
        double gate = PrecipField.stormGate(latRad, theta);
        double K1 = PrecipField.EDDY_MIX * sg * ld * ld;
        double K2 = PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
        return new double[]{w0, curv, ld, sg, gate, K1, K2, K2 * curv * gate, K1 * curv * gate};
    }

    public static void main(String[] args) throws Exception {
        rep = new PrintStream(new File(ROOT, "build/eoh_probe/mtn/p616_report.txt"), "UTF-8");
        say("P616: modelShape 因子分解");
        say(String.format(LF, "  EDDY_CLOSURE=%d  EDDY_PHYS_GAIN=%.4e  EDDY_TAU=%.4e  EDDY_MIX=%.2f",
            PrecipField.EDDY_CLOSURE, PrecipField.EDDY_PHYS_GAIN, PrecipField.EDDY_TAU, PrecipField.EDDY_MIX));
        double thS = Atmosphere.theta(0.0);
        double thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR / 2.0);
        for (int pass = 0; pass < 2; pass++) {
            double th = (pass == 0) ? thS : thW;
            say("");
            say(String.format(LF, "  [%s]  纬度   W(kg/m2)      curv(W)        L_d(m)    sigma(1/s)  gate    shape(closure1)  shape(closure0)",
                (pass == 0) ? "JJA" : "DJF"));
            for (double latd = 20; latd <= 65; latd += 5) {
                double[] f = factors(Math.toRadians(latd), th);
                say(String.format(LF, "        %4.0f  %9.2f  %+12.4e  %9.0f  %+10.4e  %.4f  %+12.4e  %+12.4e",
                    latd, f[0], f[1], f[2], f[3], f[4], f[7], f[8]));
            }
        }
        say("");
        say("  峰值比（取各自最大 |shape|）：");
        double pS1 = 0, pW1 = 0, pS0 = 0, pW0 = 0;
        for (double latd = 20; latd <= 80; latd += 1) {
            double[] a = factors(Math.toRadians(latd), thS), b = factors(Math.toRadians(latd), thW);
            pS1 = Math.max(pS1, Math.abs(a[7])); pW1 = Math.max(pW1, Math.abs(b[7]));
            pS0 = Math.max(pS0, Math.abs(a[8])); pW0 = Math.max(pW0, Math.abs(b[8]));
        }
        say(String.format(LF, "    closure1 (sigma^2, 生产):  JJA %.4e   DJF %.4e   比 %.2f", pS1, pW1, pW1 / pS1));
        say(String.format(LF, "    closure0 (sigma^1, EDDY_MIX): JJA %.4e   DJF %.4e   比 %.2f", pS0, pW0, pW0 / pS0));
        rep.flush();
        System.out.println("JAVA_EXIT=0");
    }
}
