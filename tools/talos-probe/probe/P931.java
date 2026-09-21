package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P931 -- B 的正确归因：gateOf 恒为 1 时，MFC = d(K*X')/dy 由哪一项主导？
//   全部量都是 public：gateOf / eadyGrowth / deformRadius / EDDY_MIX / eddyScalar / eddyDXdy / eddyMfc / dphiDeg
//   先【重建】MFC 与 eddyMfc 对账，再按乘积法则拆成三项。
public class P931 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    static double D, DY;
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p931_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        D = Math.toRadians(PrecipField.dphiDeg()); DY = D * WorldContract.R_EFF;
        sb.append("P931: 正确归因（EDDY_GATE_MODE=").append(PrecipField.EDDY_GATE_MODE)
          .append(", EDDY_CLOSURE=").append(PrecipField.EDDY_CLOSURE)
          .append(", EDDY_VAR=").append(PrecipField.EDDY_VAR)
          .append(", EDDY_GRAD=").append(PrecipField.EDDY_GRAD).append("）\n\n");
        for (int s = 0; s < 2; s++) {
            double th = (s == 0) ? thS : thW;
            sb.append(s == 0 ? "=== JJA ===\n" : "=== DJF ===\n");
            sb.append("   lat |   MFC(1e-5)  MFC_rebuilt  |    K(1e12)   X'(1e-3)  gate  |    K'X'(1e-5)  K*X''(1e-5)  gate'KX'(1e-5)\n");
            for (double dg = 30; dg <= 88.0001; dg += 2) {
                double y = Math.toRadians(dg);
                double mfc = PrecipField.eddyMfc(y, th);
                double rebuild = (F(y + D, th) - F(y - D, th)) / (2.0 * DY);
                double K = Kof(y, th);
                double Xp = PrecipField.eddyDXdy(y, th, D);
                double gate = PrecipField.gateOf(y, th);
                double Kp = (Kof(y + D, th) - Kof(y - D, th)) / (2.0 * DY);
                double Xpp = (PrecipField.eddyDXdy(y + D, th, D) - PrecipField.eddyDXdy(y - D, th, D)) / (2.0 * DY);
                double gp = (PrecipField.gateOf(y + D, th) - PrecipField.gateOf(y - D, th)) / (2.0 * DY);
                sb.append(String.format(LF, "  %5.1f | %11.5f %12.5f | %10.4e %10.5f %6.3f | %13.5f %12.5f %13.5f%n",
                    dg, mfc * 1e5, rebuild * 1e5, K, Xp * 1e3, gate,
                    (Kp * Xp) * 1e5, (K * Xpp) * 1e5, (gp * K * Xp) * 1e5));
            }
            sb.append("\n");
        }
        sb.append("  判读：三项中哪一项与 MFC 的符号/形状同相，就是主导项。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
    static double Kof(double y, double th) {
        double ld = PrecipField.deformRadius(y, th), sg = PrecipField.eadyGrowth(y, th);
        return (PrecipField.EDDY_CLOSURE == 0) ? PrecipField.EDDY_MIX * sg * ld * ld
             : PrecipField.EDDY_PHYS_GAIN * PrecipField.EDDY_TAU * sg * sg * ld * ld;
    }
    static double F(double y, double th) {
        if (PrecipField.eddyScalar(y, th) <= 0.0) return 0.0;
        return PrecipField.gateOf(y, th) * Kof(y, th) * PrecipField.eddyDXdy(y, th, D);
    }
}
