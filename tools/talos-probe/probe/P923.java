package probe;

import com.EyeOfHarmonyBuffer.sim.atmos.Atmosphere;
import com.EyeOfHarmonyBuffer.sim.atmos.PrecipField;
import com.EyeOfHarmonyBuffer.sim.world.WorldContract;
import java.io.*; import java.util.Locale;

// P923 -- §591 归因：MFC = K * curv(W) * stormGate 里，高纬那个大尺度振荡出自哪一项？
//   全部是 public 标量函数 + 自己做同一个二阶差分 => 秒级。
//   对账：K_implied = MFC/(curv*gate) 必须等于 K_formula = EDDY_MIX*sigma*L_d^2。
public class P923 {
    static final Locale LF = Locale.ROOT;
    static final File ROOT = new File("K:" + File.separator + "moder" + File.separator + "EyeOfHarmonyBuffer");
    public static void main(String[] a) throws Exception {
        PrintStream rep = new PrintStream(new File(ROOT,"build/eoh_probe/mtn/p923_report.txt"),"UTF-8");
        StringBuilder sb = new StringBuilder();
        double thS = Atmosphere.theta(0.0), thW = Atmosphere.theta(WorldContract.DAYS_PER_YEAR/2.0);
        double d = Math.toRadians(PrecipField.dphiDeg());
        double dy = d * WorldContract.R_EFF;
        sb.append("P923: 涡动链因子归因。dphi=").append(PrecipField.dphiDeg()).append(" 度  dy=").append(String.format(LF,"%.1f",dy)).append(" m")
          .append("  EDDY_MIX=").append(String.format(LF,"%.5f",PrecipField.EDDY_MIX))
          .append("  EDDY_GATE_MODE=").append(PrecipField.EDDY_GATE_MODE)
          .append("  CW_SMOOTH=").append(PrecipField.CW_SMOOTH)
          .append("  EDDY_T_SMOOTH=").append(PrecipField.EDDY_T_SMOOTH)
          .append("  EDDY_SIGMA_T850=").append(PrecipField.EDDY_SIGMA_T850).append("\n\n");
        for (int season = 0; season < 2; season++) {
            double th = (season == 0) ? thS : thW;
            sb.append(season == 0 ? "=== JJA (theta=0) ===\n" : "=== DJF (theta=pi) ===\n");
            sb.append("   lat |    MFC(1e-5)   curv(1e-9)   gate    |  sigma(1e-5)  L_d(km)  K_form(1e-5)  K_impl(1e-5) |  W(kg/m2)\n");
            for (double deg = 35; deg <= 86; deg += 1.0) {
                double c = Math.toRadians(deg);
                double w0 = PrecipField.columnWater(c, th);
                double wp = PrecipField.columnWater(c + d, th);
                double wm = PrecipField.columnWater(c - d, th);
                double curv = (wp - 2.0 * w0 + wm) / (dy * dy);
                double gate = PrecipField.gateOf(c, th);   // §598 修正：分派器才是生效的那个（原来是遗留的 stormGate）
                double sig = PrecipField.eadyGrowth(c, th);
                double ld = PrecipField.deformRadius(c, th);
                double mfc = PrecipField.eddyMfc(c, th);
                double kF = PrecipField.EDDY_MIX * sig * ld * ld;
                double den = curv * gate;
                double kI = (Math.abs(den) > 1e-30) ? mfc / den : Double.NaN;
                sb.append(String.format(LF, "  %5.1f | %12.5f %12.5f %8.5f | %11.5f %8.1f %12.6f %12.6f | %9.4f%n",
                    deg, mfc * 1e5, curv * 1e9, gate, sig * 1e5, ld / 1000.0, kF * 1e5, kI * 1e5, w0));
            }
            sb.append("\n");
        }
        sb.append("  判读：比较 K_form 与 K_impl（应相等）；再看 MFC 的振荡与 curv / gate / sigma 哪一个同相。\n");
        sb.append("DONE\n");
        rep.print(sb); rep.flush(); System.out.print(sb);
        System.out.println("JAVA_EXIT=0");
    }
}
