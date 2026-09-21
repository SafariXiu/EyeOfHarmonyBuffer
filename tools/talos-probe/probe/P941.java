package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.*;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P941 -- 冬季过冲：MFC = K'X' + K*X'' 里哪一项在冬季放大？
public class P941 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static double D, DY;
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p941_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        double thJ = Atmosphere.theta(0.0), thD = Atmosphere.theta(365.25 / 2.0);
        sb.append("P941: 冬/夏 的因子比（EDDY_MASK_OUTSIDE=").append(PrecipField.EDDY_MASK_OUTSIDE)
          .append("  PHASE_FROM_OBS=").append(Atmosphere.PHASE_FROM_OBS).append("）\n\n");
        sb.append("   lat |    K夏      K冬   K冬/夏 |   X夏       X冬    X冬/夏 |  K'X'夏   K'X'冬 比 | KX''夏  KX''冬 比 | MFC夏  MFC冬 比\n");
        for (double dg = 40; dg <= 66.0001; dg += 4) {
            double y = Math.toRadians(dg);
            double kJ = Kof(y, thJ), kD = Kof(y, thD);
            double xJ = PrecipField.eddyScalar(y, thJ), xD = PrecipField.eddyScalar(y, thD);
            double a1J = Kp(y, thJ) * PrecipField.eddyDXdy(y, thJ, D), a1D = Kp(y, thD) * PrecipField.eddyDXdy(y, thD, D);
            double a2J = kJ * Xpp(y, thJ), a2D = kD * Xpp(y, thD);
            double mJ = PrecipField.eddyMfc(y, thJ), mD = PrecipField.eddyMfc(y, thD);
            sb.append(String.format(LF, "  %5.0f | %9.3e %9.3e %6.2f | %7.3f %8.3f %5.2f | %8.3f %8.3f %5.2f | %6.3f %6.3f %5.2f | %6.3f %6.3f %5.2f%n",
                dg, kJ, kD, kD / kJ, xJ, xD, xD / xJ,
                a1J * 1e5, a1D * 1e5, (Math.abs(a1J) > 1e-30 ? a1D / a1J : Double.NaN),
                a2J * 1e5, a2D * 1e5, (Math.abs(a2J) > 1e-30 ? a2D / a2J : Double.NaN),
                mJ * 1e5, mD * 1e5, (Math.abs(mJ) > 1e-30 ? mD / mJ : Double.NaN)));
        }
        sb.append("\n  判读：看【K冬/夏】与【X冬/夏】；若 K 的比值远大于 X 的，则过冲来自扩散率而不是水汽。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static double Kof(double y, double th) {
        double ld = PrecipField.deformRadius(y, th), sg = PrecipField.eadyGrowth(y, th);
        return (PrecipField.EDDY_CLOSURE == 0) ? PrecipField.EDDY_MIX * sg * ld * ld
             : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
    }
    static double Kp(double y, double th) { return (Kof(y + D, th) - Kof(y - D, th)) / (2.0 * DY); }
    static double Xpp(double y, double th) { return (PrecipField.eddyDXdy(y + D, th, D) - PrecipField.eddyDXdy(y - D, th, D)) / (2.0 * DY); }
}