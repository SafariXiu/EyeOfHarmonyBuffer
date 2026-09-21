package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P939 -- (a) 自证：新访问器 phiZonalMean 是否复现 P938 的约定（滞后应为 0 ~ -6 天）。
public class P939 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p939_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        sb.append("P939: phiZonalMean 的自证（对照 P938 的 thetaB 列）\n\n");
        sb.append("   lat | phi(度) | 滞后(天) | 期望(天, P938) | 判定\n");
        double[] expect = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.13, 2.90, -0.40, -4.84, -6.43, -1.15, 1.08, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, -5.84};
        boolean ok = true;
        for (int j = 0; j <= 18; j += 2) {
            double lat = j * 5.0;
            double phi = Atmosphere.phiZonalMean(lat);
            double lag = phi / (2.0 * Math.PI) * WorldContract.DAYS_PER_YEAR;
            double ex = expect[j];
            String v;
            if (Double.isNaN(ex)) { v = "(P938 未列)"; }
            else { boolean good = Math.abs(lag - ex) < 0.5; v = good ? "OK" : "** 不符 **"; if (!good) ok = false; }
            sb.append(String.format(LF, "  %5.0f | %7.2f | %8.2f | %14s | %s%n", lat, Math.toDegrees(phi), lag, Double.isNaN(ex) ? "-" : String.format(LF, "%.2f", ex), v));
        }
        sb.append("\n  模型旧相位滞后 = ").append(String.format(LF, "%.2f", (60.0 + (30.0 - 60.0) * Atmosphere.KAPPA_MEAN) )).append(" 天（对照）\n");
        sb.append(ok ? "VERDICT=CONVENTION_OK\n" : "VERDICT=**CONVENTION_MISMATCH**\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}